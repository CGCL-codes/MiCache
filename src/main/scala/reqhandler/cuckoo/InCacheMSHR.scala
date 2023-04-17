package fpgamshr.reqhandler.cuckoo

import chisel3._
import chisel3.util._
import fpgamshr.interfaces._
import fpgamshr.util._
import fpgamshr.profiling._
import chisel3.core.dontTouch
import scala.language.reflectiveCalls

import java.io._ // To generate the BRAM initialization files

object InCacheMSHR {
	val dataQueueDepth = 3
	val pplRdLen = 4
	val pplWrLen = 3
}

class InCacheMSHR(
	addrWidth:            Int=MSHR.addrWidth,
	numMSHRPerHashTable:  Int=MSHR.numMSHRPerHashTable,
	numHashTables:        Int=MSHR.numHashTables,
	idWidth:              Int=MSHR.idWidth,
	memDataWidth:         Int=MSHR.memDataWidth,
	reqDataWidth:         Int=MSHR.reqDataWidth,
	numSubentriesPerRow:  Int=0,
	MSHRAlmostFullMargin: Int=MSHR.MSHRAlmostFullMargin,
	assocMemorySize:      Int=MSHR.assocMemorySize,
	sameHashFunction:     Boolean=false
) extends Module {
	require(isPow2(memDataWidth / reqDataWidth))
	require(isPow2(numMSHRPerHashTable))
	val offsetWidth = log2Ceil(memDataWidth / reqDataWidth)
	val tagWidth = addrWidth - offsetWidth
	val numMSHRTotal = numMSHRPerHashTable * numHashTables

	val cacheDataType = UInt(memDataWidth.W)
	val subentryAlignWidth = 8
	val subentryLineType = new SubentryLine(memDataWidth, offsetWidth, idWidth, numSubentriesPerRow, subentryAlignWidth)
	val numEntriesPerLine = subentryLineType.entriesPerLine
	val tagType = new UniTag(tagWidth, subentryLineType.lastValidIdxWidth)

	val hashTableAddrWidth = log2Ceil(numMSHRPerHashTable)
	val hashMultConstWidth = if (tagWidth > MSHR.maxMultConstWidth) MSHR.maxMultConstWidth else tagWidth
	val hbmChannelWidth = 28 - offsetWidth - log2Ceil(reqDataWidth / 8)
	val tagHashWidth = if (tagWidth > hbmChannelWidth) hbmChannelWidth else tagWidth

	val stashEntryPerTable = assocMemorySize / numHashTables
	val stashTableSelWidth = log2Ceil(stashEntryPerTable)
	/*
	* a = positive odd integer on addr.getWidth bits
	https://en.wikipedia.org/wiki/Universal_hashing#Avoiding_modular_arithmetic */
	// def hash(aExponent: Int, b: Int, tag: UInt): UInt = ((tag << aExponent) + tag + b.U)(tagWidth - 1, tagWidth - hashTableAddrWidth)=
	// println(s"tagWidth=$tagWidth, hashTableAddrWidth=$hashTableAddrWidth")
	//def hash(a: Int, b: Int, tag: UInt): UInt = (a.U(hashMultConstWidth.W) * tag + b.U((tagWidth-hashTableAddrWidth).W))(tagWidth - 1, tagWidth - hashTableAddrWidth)
	// def hash(a: Int, b: Int, tag: UInt): UInt = ((a.U(hashMultConstWidth.W) * tag(tagHashWidth - 1, 0))(tagWidth - 1, tagWidth - hashTableAddrWidth) + b.U((hashTableAddrWidth).W))
	/* The way the hash was computed, b was useless anyway, so we can remove it altogether. */
	def hash(a: Int, tag: UInt): UInt = (a.U(hashMultConstWidth.W) * tag(tagHashWidth - 1, 0))(tagWidth - 1, tagWidth - hashTableAddrWidth)
	//def hash2(a1: Int, a2: Int, tag: UInt): UInt = (tag + (tag << a1.U) + (tag << a2.U))(tagWidth - 1, tagWidth - hashTableAddrWidth)
	def getTag(addr: UInt): UInt = addr(addrWidth - 1, addrWidth - tagWidth)
	def getOffset(addr: UInt): UInt = addr(offsetWidth - 1, 0)
	def getStashBRAMAddr(idx: UInt): UInt = Cat(idx(log2Ceil(assocMemorySize) - 1, stashTableSelWidth), Fill(hashTableAddrWidth - stashTableSelWidth, 1.U), idx(stashTableSelWidth - 1, 0))

	val io = IO(new Bundle {
		val allocIn = Flipped(DecoupledIO(new AddrIdIO(addrWidth, idWidth)))
		val deallocIn = Flipped(DecoupledIO(new AddrDataIO(addrWidth, memDataWidth)))
		/* Interface to memory arbiter, with burst requests to be sent to DDR */
		val outMem = DecoupledIO(UInt(tagWidth.W))
		val respOut = DecoupledIO(new DataIdIO(reqDataWidth, idWidth))
		val respGenOut = DecoupledIO(new UniRespGenIO(memDataWidth, offsetWidth, idWidth, numEntriesPerLine))
		val axiProfiling = new AXI4LiteReadOnlyProfiling(Profiling.dataWidth, Profiling.regAddrWidth)
		/* MSHR will stop accepting allocations when we reach this number of MSHRs. By making this Value
		* configurable at runtime, we can quickly explore the impact of reducing the number of MSHRs without
		* recompiling the design. */
		val maxAllowedMSHRs = Input(UInt(log2Ceil(numMSHRTotal + 1).W))
	})

	val pipelineReady = Wire(Bool())
	val wrPipelineReady = Wire(Bool())

	/* Input logic */
	val inputArbiter = Module(new Arbiter(new AddrIdIO(addrWidth, idWidth), 2))
	val inputDataQueue = Module(new Queue(io.deallocIn.bits.data.cloneType, InCacheMSHR.dataQueueDepth))
	val stopAllocs = Wire(Bool())
	val stopDeallocs = Wire(Bool())
	val stallOnlyAllocs = Wire(Bool())

	inputDataQueue.io.enq.valid     := io.deallocIn.valid & inputArbiter.io.in(0).ready // the second cond is not necessary
	inputDataQueue.io.enq.bits      := io.deallocIn.bits.data
	inputArbiter.io.in(0).valid     := io.deallocIn.valid & ~stopDeallocs
	inputArbiter.io.in(0).bits.addr := io.deallocIn.bits.addr
	// inputArbiter.io.in(0).bits.data := io.deallocIn.bits.data
	io.deallocIn.ready              := inputArbiter.io.in(0).ready & ~stopDeallocs

	inputArbiter.io.in(1).valid     := io.allocIn.valid & ~stopAllocs
	inputArbiter.io.in(1).bits.addr := io.allocIn.bits.addr
	inputArbiter.io.in(1).bits.id   := io.allocIn.bits.id
	io.allocIn.ready                := inputArbiter.io.in(1).ready & ~stopAllocs

	/* Arbiter between input and stash. Input has higher priority: we try to put back
	* entries in the tables "in the background". */
	val stashArbiter = Module(new Arbiter(new AddrIdAllocIO(addrWidth, idWidth), 2))
	/* Queue containing entries that have been kicked out from the hash tables, and that we will try
	* to put back in one of their other possible locations. */
	val stash = Module(new InCacheMSHRStash(tagType, log2Ceil(numHashTables), assocMemorySize))
	stashArbiter.io.in(0).valid        := inputArbiter.io.out.valid
	stashArbiter.io.in(0).bits.addr    := inputArbiter.io.out.bits.addr
	// stashArbiter.io.in(0).bits.data    := inputArbiter.io.out.bits.data
	stashArbiter.io.in(0).bits.id      := inputArbiter.io.out.bits.id
	stashArbiter.io.in(0).bits.isAlloc := inputArbiter.io.chosen === 1.U
	inputArbiter.io.out.ready          := stashArbiter.io.in(0).ready

	val reinsertHazard1 = Wire(Bool())
	val reinsertHazard2 = Wire(Bool())
	stashArbiter.io.in(1).valid        := stash.io.outToPipeline.valid & ~reinsertHazard1 & ~reinsertHazard2
	// stashArbiter.io.in(1).bits         := DontCare
	stashArbiter.io.in(1).bits.addr    := Cat(stash.io.outToPipeline.bits, 0.U(offsetWidth.W))
	stashArbiter.io.in(1).bits.isAlloc := true.B
	stash.io.outToPipeline.ready       := stashArbiter.io.in(1).ready & ~reinsertHazard1 & ~reinsertHazard2

	stashArbiter.io.out.ready := pipelineReady

	/* Pipeline */
	val pipelineType = new PipelineIO(addrWidth, idWidth)
	// val delayedRequest = Wire(Vec(InCacheMSHR.pplRdLen, ValidIO(stashArbiter.io.out.bits.cloneType)))
	// val delayedIsFromStash = Wire(Vec(InCacheMSHR.pplRdLen, Bool()))
	val pplHashStage = Wire(pipelineType.cloneType)
	val pplReadStage = Wire(pipelineType.cloneType)
	val pplStashStage = Wire(pipelineType.cloneType)
	val pplMatchStage = Wire(pipelineType.cloneType)
	val pplWriteStage = Wire(pipelineType.cloneType)

	val isDelayedFromStash = pplMatchStage.isFromStash
	val isDelayedValid     = pplMatchStage.valid & ~(isDelayedFromStash & ~stash.io.hit)
	val isDelayedAlloc     = pplMatchStage.isAlloc & isDelayedValid
	val isDelayedDealloc   = ~pplMatchStage.isAlloc & pplMatchStage.valid

	pplHashStage.valid       := RegEnable(stashArbiter.io.out.valid, enable=pipelineReady, init=false.B)
	pplHashStage.addr        := RegEnable(stashArbiter.io.out.bits.addr, enable=pipelineReady)
	pplHashStage.id          := RegEnable(stashArbiter.io.out.bits.id, enable=pipelineReady)
	pplHashStage.isAlloc     := RegEnable(stashArbiter.io.out.bits.isAlloc, enable=pipelineReady)
	pplHashStage.isFromStash := RegEnable(stashArbiter.io.chosen === 1.U, enable=pipelineReady)
	pplReadStage  := RegEnable(pplHashStage, enable=pipelineReady, init=pipelineType.getInvalid())
	pplStashStage := RegEnable(pplReadStage, enable=pipelineReady, init=pipelineType.getInvalid())
	pplMatchStage := RegEnable(pplStashStage, enable=pipelineReady, init=pipelineType.getInvalid())
	pplWriteStage.valid       := RegEnable(isDelayedValid & pipelineReady, enable=wrPipelineReady, init=false.B)
	pplWriteStage.addr        := RegEnable(pplMatchStage.addr, enable=pipelineReady)
	pplWriteStage.id          := RegEnable(pplMatchStage.id, enable=pipelineReady)
	pplWriteStage.isAlloc     := RegEnable(pplMatchStage.isAlloc, enable=pipelineReady)
	pplWriteStage.isFromStash := RegEnable(pplMatchStage.isFromStash, enable=pipelineReady)

	/* When re-inserting a stash entry, a BRAM read is claimed in stash searching stage. We want no update on this entry,
	   that is, the previous two (aka BRAM delay) requests must not hit the re-inserting stash entry. */
	reinsertHazard1 := (stash.io.outToPipeline.bits === getTag(pplHashStage.addr)) & pplHashStage.valid
	reinsertHazard2 := (stash.io.outToPipeline.bits === getTag(pplReadStage.addr)) & pplReadStage.valid

	/* Pipeline hashing stage */
	val r = new scala.util.Random(42)
	val a = (0 until numHashTables).map(_ => r.nextInt(1 << hashMultConstWidth))
	// val b = (0 until numHashTables).map(_ => r.nextInt(1 << hashTableAddrWidth))
	val hashedTags = (0 until numHashTables).map(i => hash(a(i), getTag(pplHashStage.addr)))
	// val hashedTags = (0 until numHashTables).map(i => if(sameHashFunction) hash(a(0), b(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), b(i), getTag(delayedRequest(0).bits.addr)))
	//a.indices.foreach(i => println(s"a($i)=${a(i)}"))
	/* Uncomment to print out hashing parameters a and b */
	// a.indices.foreach(i => println(s"a($i)=${a(i)} b($i)=${b(i)}"))

	/* Memories instantiation and interconnection */
	/* Memories are initialized with all zeros, which is fine for us since all the valids will be false */
	val tagMems = Array.fill(numHashTables)(Module(new XilinxSimpleDualPortNoChangeBRAM(width=tagType.getWidth, depth=numMSHRPerHashTable)).io)
	val dataMem = Module(new XilinxTDPReadFirstByteWriteBRAM(width=memDataWidth, depth=numMSHRTotal, byteWriteWidth=subentryAlignWidth)).io
	val storeToLoads = Array.fill(numHashTables)(Module(new StoreToLoadForwardingThreeStages(tagType, hashTableAddrWidth)).io)

	/* Pipeline reading stage */
	for (i <- 0 until numHashTables) {
		val hashedi = RegEnable(hashedTags(i), enable=pipelineReady)
		val rdAddri = Mux(hashedi >= (numMSHRPerHashTable - stashEntryPerTable).U, hashedi & Fill(log2Ceil(stashEntryPerTable), 1.U(1.W)), hashedi)
		tagMems(i).clock  := clock
		tagMems(i).reset  := reset
		tagMems(i).addrb  := rdAddri
		tagMems(i).enb    := pipelineReady
		tagMems(i).regceb := pipelineReady
		storeToLoads(i).rdAddr        := rdAddri
		storeToLoads(i).dataInFromMem := tagMems(i).doutb.asTypeOf(tagType)
		storeToLoads(i).pipelineReady := pipelineReady
	}

	/* Pipeline stash looking up stage. Searching stash in advance. The result comes back after a cycle. */
	stash.io.lookupTag.bits  := getTag(pplStashStage.addr)
	stash.io.lookupTag.valid := pplStashStage.valid
	stash.io.pipelineReady   := pipelineReady
	stash.io.deallocMatching := ~pplStashStage.isAlloc | pplStashStage.isFromStash

	val isWritingStashBRAM = Wire(Bool())
	val stashVictimAddr = Wire(dataMem.addrb.cloneType)
	dataMem.clock := clock
	dataMem.reset := reset
	dataMem.addrb := Mux(isWritingStashBRAM, stashVictimAddr, getStashBRAMAddr(stash.io.matchingNoAhead.bits))
	dataMem.enb := (pipelineReady & stash.io.matchingNoAhead.valid) | (wrPipelineReady & isWritingStashBRAM)
	dataMem.regceb := RegNext(pipelineReady & stash.io.matchingNoAhead.valid) // delay one cycle to make sure reading result can be updated, even if a write interleaving

	/* Pipeline matching stage */
	val tagsRead = storeToLoads.map(x => x.dataInFixed)
	val hashTableMatches = tagsRead.map(x => x.valid & x.tag === getTag(pplMatchStage.addr))
	val allMatches = hashTableMatches ++ Array(stash.io.hit)
	val hit = Vec(allMatches).asUInt.orR

	val cacheMatches = hashTableMatches.zip(tagsRead).map(x => x._1 & ~x._2.isMSHR)
	val cacheHit = Vec(cacheMatches).asUInt.orR
	val mshrMatches = hashTableMatches.zip(tagsRead).map(x => x._1 & x._2.isMSHR) ++ Array(stash.io.hit)
	val mshrHit = Vec(mshrMatches).asUInt.orR
	val selectedLastValidIdx = Mux1H(mshrMatches, tagsRead.map(x => x.lastValidIdx) ++ Array(stash.io.matchingLastValidIdx))

	// For simulation
	val cacheMiss = ~cacheHit & isDelayedAlloc & ~isDelayedFromStash
	val mshrAllocHit = mshrHit & isDelayedAlloc & ~isDelayedFromStash
	dontTouch(cacheMiss)
	dontTouch(mshrAllocHit)

	val allValid = Vec(tagsRead.map(x => x.valid)).asUInt.andR
	val allIsMSHR = Vec(tagsRead.map(x => x.isMSHR)).asUInt.andR
	val allFull = allValid & allIsMSHR
	/* When a tag appears for the first time, we allocate an entry in one of the hash tables (HT).
	* To better spread the entries among HTs, we want all HTs to have the same priority; however, we can only choose
	* a hash table for which the entry corresponding to the new tag is free. We use a RRArbiter to implement this functionality, where we
	* do not care about the value to arbitrate and we use ~entry.valid as valid signal for the arbiter. */
	val fakeRRArbiterForSelect = Module(new ResettableRRArbiter(Bool(), numHashTables))
	for (i <- 0 until numHashTables) {
		fakeRRArbiterForSelect.io.in(i).valid := ~tagsRead(i).valid | (allValid & ~tagsRead(i).isMSHR)
	}
	val hashTableToUpdate = UIntToOH(fakeRRArbiterForSelect.io.chosen).toBools

	/* Eviction logic */
	/* If the entry has been kicked out from HT i, we will try put it in HT i+1 mod HT_count.
	* We use a round-robin policy also for the first eviction: the index of the last hash
	* table from which we evicted is stored in evictTableForFirstAttempt.
	* This round-robin policy is simpler and works better than using an LFSR16. */
	val evictCounterEnable = Wire(Bool())
	val evictTableForFirstAttempt = Counter(evictCounterEnable, numHashTables)
	/* To support non-power-of-two number of tables, we need to implement the wrapping logic manually. */
	val evictTableForEntryFromStash = Mux(stash.io.matchingLastTableIdx === (numHashTables - 1).U, 0.U, stash.io.matchingLastTableIdx + 1.U)
	val evictTable = Mux(isDelayedFromStash, evictTableForEntryFromStash, evictTableForFirstAttempt._1)
	val evictOH = UIntToOH(evictTable)
	stash.io.inVictim.bits.tag          := Mux1H(evictOH, tagsRead.map(x => x.tag))
	stash.io.inVictim.bits.lastValidIdx := Mux1H(evictOH, tagsRead.map(x => x.lastValidIdx))
	stash.io.inVictim.bits.lastTableIdx := evictTable
	stash.io.inVictim.valid             := ((isDelayedAlloc & !hit) | (isDelayedFromStash & stash.io.hit)) & allFull
	evictCounterEnable                  := stash.io.inVictim.valid & pipelineReady

	val newAllocDone = isDelayedAlloc & !hit & ~isDelayedFromStash & pipelineReady
	fakeRRArbiterForSelect.io.out.ready := newAllocDone

	/* Queue and interface to external memory arbiter */
	val externalMemoryQueue = Module(new BRAMQueue(tagWidth, numMSHRTotal))
	externalMemoryQueue.io.deq <> io.outMem
	externalMemoryQueue.io.enq.valid := newAllocDone
	externalMemoryQueue.io.enq.bits := getTag(pplMatchStage.addr)

	// Subentry almost full stall logic
	val subentryAlmostFullStall = Module(new FullSubentryTagArray(tagWidth, InCacheMSHR.pplRdLen)).io
	subentryAlmostFullStall.in.valid           := (isDelayedAlloc & mshrHit & (numEntriesPerLine.U - selectedLastValidIdx - 1.U <= InCacheMSHR.pplRdLen.U)) | isDelayedDealloc
	subentryAlmostFullStall.in.bits            := getTag(pplMatchStage.addr)
	subentryAlmostFullStall.deallocMatchingTag := isDelayedDealloc
	subentryAlmostFullStall.pipelineReady      := pipelineReady

	// processing pipeline
	val delayedCacheHit = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedOffset = Wire(Vec(InCacheMSHR.pplWrLen - 1, UInt(offsetWidth.W)))
	val delayedId = Wire(Vec(InCacheMSHR.pplWrLen - 1, UInt(idWidth.W)))
	val delayedResp = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedLastValidIdx = Wire(Vec(InCacheMSHR.pplWrLen, UInt(subentryLineType.lastValidIdxWidth.W)))
	val delayedEvict = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedStashVictimNo = Wire(Vec(InCacheMSHR.pplWrLen, UInt(log2Ceil(assocMemorySize).W)))
	delayedCacheHit(0) := RegEnable(cacheHit, init=false.B, enable=pipelineReady) & pplWriteStage.valid
	delayedOffset(0) := RegEnable(getOffset(pplWriteStage.addr), enable=wrPipelineReady)
	delayedOffset(1) := RegEnable(delayedOffset(0), enable=wrPipelineReady)
	delayedId(0) := RegEnable(pplWriteStage.id, enable=wrPipelineReady)
	delayedId(1) := RegEnable(delayedId(0), enable=wrPipelineReady)
	delayedResp(0) := RegEnable(isDelayedDealloc, init=false.B, enable=pipelineReady) & pplWriteStage.valid
	delayedLastValidIdx(0) := RegEnable(selectedLastValidIdx, enable=pipelineReady)
	delayedEvict(0) := RegEnable(stash.io.inVictim.valid, init=false.B, enable=pipelineReady) & pplWriteStage.valid
	delayedStashVictimNo(0) := RegEnable(stash.io.newVictimNo, enable=pipelineReady)
	for (i <- 1 until InCacheMSHR.pplWrLen) {
		delayedCacheHit(i) := RegEnable(delayedCacheHit(i - 1), init=false.B, enable=wrPipelineReady)
		delayedResp(i) := RegEnable(delayedResp(i - 1), init=false.B, enable=wrPipelineReady)
		delayedLastValidIdx(i) := RegEnable(delayedLastValidIdx(i - 1), enable=wrPipelineReady)
		delayedEvict(i) := RegEnable(delayedEvict(i - 1), init=false.B, enable=wrPipelineReady)
		delayedStashVictimNo(i) := RegEnable(delayedStashVictimNo(i - 1), enable=wrPipelineReady)
	}
	isWritingStashBRAM := delayedEvict.last

	/* Pipeline writing stage */
	val matchSel = RegEnable(Vec(hashTableMatches).asUInt, enable=pipelineReady)
	val tableSel = Wire(Vec(numHashTables, Bool()))
	val matchWrEn = RegEnable(Vec(mshrMatches).asUInt, enable=pipelineReady)
	val newWrEn = RegEnable(!hit, enable=pipelineReady) | pplWriteStage.isFromStash
	val emptyWrEn = RegEnable(Vec(hashTableToUpdate.map(x => x & ~allFull)).asUInt, enable=pipelineReady)
	val evictWrEn = RegEnable(Vec((0 until numHashTables).map(i => evictOH(i) & allFull)).asUInt, enable=pipelineReady)

	val updatedTag = Wire(tagType)
	updatedTag.valid        := true.B
	updatedTag.isMSHR       := pplWriteStage.isAlloc
	updatedTag.tag          := getTag(pplWriteStage.addr)
	updatedTag.lastValidIdx := Mux(matchWrEn.orR, delayedLastValidIdx(0) + ~pplWriteStage.isFromStash, 0.U)
	val updatedSubentryLine = Wire(subentryLineType.cloneType)
	updatedSubentryLine.entries.map(x => {
		x.offset  := getOffset(pplWriteStage.addr)
		x.id      := pplWriteStage.id
		x.padding := DontCare
	})
	val updateEntryOH = UIntToOH(updatedTag.lastValidIdx)
	val updateWrEn = Wire(Vec(memDataWidth / subentryAlignWidth, Bool()))
	for (i <- 0 until subentryLineType.entriesPerLine) {
		for (j <- 0 until subentryLineType.entryBytes) {
			updateWrEn(i * subentryLineType.entryBytes + j) := (updateEntryOH(i) | ~pplWriteStage.isAlloc | pplWriteStage.isFromStash) & ~delayedCacheHit(0)
		}
	}
	for (i <- subentryLineType.entriesPerLine * subentryLineType.entryBytes until memDataWidth / subentryAlignWidth) {
		updateWrEn(i) := ~pplWriteStage.isAlloc
	}

	/* Memory write port */
	/* Since the pipeline is divided into two parts, one is reading from hashing to matching stage, the other one is the left part.
	   We may require the first part controlled by `pipelineReady` to stall sometimes while keeping the second part controlled by
	   `wrPipelineReady` working. However, we need to forward the latest written data from writing stage to matching stage, and the
	   former might flow while the latter might stall. To make sure the matching stage keep seeing the writing stage's data, we left
	   bits in writing stage unchanged, such as `dina` and `wea`, but at the same time, avoid the real `wea` staying high to overwrite
	   the BRAM in consider of energy savings. So a write enable status before pipeline stall is required to hold. */
	val pplReady1CycAgo = RegNext(pipelineReady)
	for (i <- 0 until numHashTables) {
		tableSel(i) := (matchSel(i) | (newWrEn & (emptyWrEn(i) | evictWrEn(i))))
		tagMems(i).addra := storeToLoads(i).wrAddr
		tagMems(i).wea   := pplWriteStage.valid & (matchWrEn(i) | (newWrEn & (emptyWrEn(i) | evictWrEn(i)))) & wrPipelineReady
		tagMems(i).dina  := updatedTag.asUInt
		val weBeforeStall = RegEnable(Mux(pipelineReady, false.B, tagMems(i).wea), enable=pipelineReady ^ pplReady1CycAgo, init=false.B)
		storeToLoads(i).wrEn            := tagMems(i).wea | weBeforeStall
		// storeToLoads(i).wrPipelineReady := wrPipelineReady
		storeToLoads(i).dataOutToMem    := updatedTag
	}
	dataMem.addra  := Mux(matchWrEn(numHashTables) & ~pplWriteStage.isFromStash, getStashBRAMAddr(RegEnable(stash.io.matchingEntryNo, enable=pipelineReady)),
	 					Cat(OHToUInt(tableSel), Mux1H(tableSel, storeToLoads.map(x => x.wrAddr))))
	dataMem.dina   := Mux(pplWriteStage.isAlloc, Mux(pplWriteStage.isFromStash, dataMem.doutb, updatedSubentryLine.asTypeOf(cacheDataType)), inputDataQueue.io.deq.bits)
	dataMem.ena    := wrPipelineReady & pplWriteStage.valid
	dataMem.regcea := wrPipelineReady
	dataMem.wea    := updateWrEn.asUInt
	stash.io.inLastValidIdx.valid := pplWriteStage.valid & pplWriteStage.isAlloc & RegEnable(~isDelayedFromStash & stash.io.hit, init=false.B, enable=pipelineReady)
	stash.io.inLastValidIdx.bits  := updatedTag.lastValidIdx
	stash.io.wrPipelineReady      := wrPipelineReady

	val idxToBitmap0 = RegEnable(stash.io.inVictim.bits.lastValidIdx, enable=pipelineReady)
	val idxToBitmap1 = RegEnable(idxToBitmap0 +& 1.U, enable=wrPipelineReady)
	val idxToBitmap2 = RegEnable(UIntToOH(idxToBitmap1), enable=wrPipelineReady)
	val delayedSubWrEn = idxToBitmap2 - 1.U
	val victimSubWrEn = Wire(Vec(memDataWidth / subentryAlignWidth, Bool()))
	for (i <- 0 until subentryLineType.entriesPerLine) {
		for (j <- 0 until subentryLineType.entryBytes) {
			victimSubWrEn(i * subentryLineType.entryBytes + j) := delayedSubWrEn(i) & isWritingStashBRAM
		}
	}
	for (i <- subentryLineType.entriesPerLine * subentryLineType.entryBytes until memDataWidth / subentryAlignWidth) {
		victimSubWrEn(i) := false.B
	}
	dataMem.dinb := dataMem.douta
	dataMem.web  := victimSubWrEn.asUInt
	stashVictimAddr := getStashBRAMAddr(delayedStashVictimNo.last)
	stash.io.transitDoneNo.valid := delayedEvict.last
	stash.io.transitDoneNo.bits  := delayedStashVictimNo.last

	val hitData = MuxLookup(delayedOffset.last, dataMem.douta(reqDataWidth-1, 0), (0 until memDataWidth by reqDataWidth).map(i => (i/reqDataWidth).U -> dataMem.douta(i+reqDataWidth-1, i)))
	val respOutEb = Module(new ElasticBuffer(io.respOut.bits.cloneType))
	respOutEb.io.in.valid     := delayedCacheHit.last
	respOutEb.io.in.bits.id   := delayedId.last
	respOutEb.io.in.bits.data := hitData
	respOutEb.io.out          <> io.respOut

	io.respGenOut.valid             := delayedResp.last
	io.respGenOut.bits.data         := inputDataQueue.io.deq.bits
	io.respGenOut.bits.entries      := dataMem.douta.asTypeOf(subentryLineType).withNoPadding().entries
	io.respGenOut.bits.lastValidIdx := delayedLastValidIdx.last
	inputDataQueue.io.deq.ready     := io.respGenOut.ready & io.respGenOut.valid

	val allocatedMSHRCounter = SimultaneousUpDownSaturatingCounter(numMSHRTotal, increment=newAllocDone, decrement=isDelayedDealloc & pipelineReady)
	/* The number of allocations + kicked out entries in flight must be limited to the number of slots in the stash since, in the worst case,
	* all of them will give rise to a kick out and must be stored in the stash if the pipeline gets filled with deallocations. */
	// val allocsInFlight = Module(new SimultaneousUpDownSaturatingCounter(2* assocMemorySize, 0))
	/* One allocation ceases to be in flight if:
	- it can be put in the hash table without kicking out another entry (hit | ~allFull)
	- an entry from the stash can be put in an hash table without more kickouts
	- an allocation or a kicked out entry in flight is deallocated */
	val allocsInFlight = SimultaneousUpDownSaturatingCounter(
		assocMemorySize + 1,
		increment=inputArbiter.io.in(1).valid & inputArbiter.io.in(1).ready & pipelineReady,
		decrement=((isDelayedAlloc & (Vec(hashTableMatches).asUInt.orR | ~allFull)) | (((isDelayedAlloc & ~isDelayedFromStash) | isDelayedDealloc) & stash.io.hit)) & pipelineReady
	)
	stallOnlyAllocs := allocsInFlight >= assocMemorySize.U
	// val MSHRAlmostFull = allocatedMSHRCounter.io.currValue >= (io.maxAllowedMSHRs - MSHRAlmostFullMargin.U)
	val MSHRAlmostFull = allocatedMSHRCounter >= (io.maxAllowedMSHRs - MSHRAlmostFullMargin.U)

	stopAllocs := MSHRAlmostFull | stallOnlyAllocs | subentryAlmostFullStall.stopAllocs
	// stopDeallocs := false.B
	stopDeallocs := ~inputDataQueue.io.enq.ready

	pipelineReady := (~stash.io.deallocNotReady & ~(stash.io.matchingNoAhead.valid & isWritingStashBRAM) & wrPipelineReady)
	wrPipelineReady := MuxCase(true.B, Array(respOutEb.io.in.valid -> respOutEb.io.in.ready, io.respGenOut.valid -> io.respGenOut.ready))

	/* Profiling interface */
	if(Profiling.enable) {
		/* The order by which registers are appended to profilingRegisters defines the register map */
		val profilingRegisters = scala.collection.mutable.ListBuffer[UInt]()
		val currentlyUsedMSHR = RegEnable(allocatedMSHRCounter, enable=io.axiProfiling.snapshot)
		profilingRegisters += currentlyUsedMSHR
		val maxUsedMSHR = ProfilingMax(allocatedMSHRCounter, io.axiProfiling)
		profilingRegisters += maxUsedMSHR
		val maxUsedSubentry = ProfilingMax(Mux(updatedTag.isMSHR, updatedTag.lastValidIdx, 0.U), io.axiProfiling)
		profilingRegisters += maxUsedSubentry
		val collisionCount = ProfilingCounter(isDelayedAlloc & !hit & allFull & pipelineReady & ~isDelayedFromStash, io.axiProfiling)
		profilingRegisters += collisionCount
		val cyclesSpentResolvingCollisions = ProfilingCounter(isDelayedFromStash & isDelayedValid & pipelineReady, io.axiProfiling)
		profilingRegisters += cyclesSpentResolvingCollisions
		val stallTriggerCount = ProfilingCounter(stallOnlyAllocs & ~RegNext(stallOnlyAllocs), io.axiProfiling)
		profilingRegisters += stallTriggerCount
		val cyclesSpentStalling = ProfilingCounter(stallOnlyAllocs & io.allocIn.valid, io.axiProfiling)
		profilingRegisters += cyclesSpentStalling
		val acceptedAllocsCount = ProfilingCounter(isDelayedAlloc & pipelineReady, io.axiProfiling)
		profilingRegisters += acceptedAllocsCount
		val acceptedDeallocsCount = ProfilingCounter(isDelayedDealloc & pipelineReady, io.axiProfiling)
		profilingRegisters += acceptedDeallocsCount
		val cyclesAllocsStalled = ProfilingCounter(io.allocIn.valid & ~io.allocIn.ready, io.axiProfiling)
		profilingRegisters += cyclesAllocsStalled
		val cyclesDeallocsStalled = ProfilingCounter(io.deallocIn.valid & ~io.deallocIn.ready, io.axiProfiling)
		profilingRegisters += cyclesDeallocsStalled
		val enqueuedMemReqsCount = ProfilingCounter(externalMemoryQueue.io.enq.valid, io.axiProfiling)
		profilingRegisters += enqueuedMemReqsCount
		val cacheHitCount = ProfilingCounter(respOutEb.io.in.valid & respOutEb.io.in.ready, io.axiProfiling)
		profilingRegisters += cacheHitCount
		// val dequeuedMemReqsCount = ProfilingCounter(externalMemoryQueue.io.deq.valid, io.axiProfiling)
		// profilingRegisters += dequeuedMemReqsCount
		// val cyclesOutLdBufNotReady = ProfilingCounter(io.outLdBuf.valid & ~io.outLdBuf.ready, io.axiProfiling)
		// profilingRegisters += cyclesOutLdBufNotReady
		// This is a very ugly hack to prevent sign extension when converting currValue to signed int, since asSInt does not accept a width as a parameter
		val accumUsedMSHR = ProfilingArbitraryIncrementCounter(Array((true.B -> (allocatedMSHRCounter + 0.U((allocatedMSHRCounter.getWidth+1).W)).asSInt)), io.axiProfiling)
		profilingRegisters += accumUsedMSHR._1
		val cyclesStallAllocFromLdBuf = ProfilingCounter(io.allocIn.valid & subentryAlmostFullStall.stopAllocs, io.axiProfiling)
		profilingRegisters += cyclesStallAllocFromLdBuf
		// readLine.validCount, writeLine.validCount, wrEn
		if(Profiling.enableHistograms) {
		val currentlyUsedMSHRHistogram = (0 until log2Ceil(numMSHRTotal)).map(i => ProfilingCounter(allocatedMSHRCounter >= (1 << i).U, io.axiProfiling))
		profilingRegisters ++= currentlyUsedMSHRHistogram
		}
		require(Profiling.regAddrWidth >= log2Ceil(profilingRegisters.length))
		val profilingInterface = ProfilingInterface(io.axiProfiling.axi, Vec(profilingRegisters))
		io.axiProfiling.axi.RDATA := profilingInterface.bits
		io.axiProfiling.axi.RVALID := profilingInterface.valid
		profilingInterface.ready := io.axiProfiling.axi.RREADY
		io.axiProfiling.axi.RRESP := 0.U
	} else {
		io.axiProfiling.axi.ARREADY := false.B
		io.axiProfiling.axi.RVALID := false.B
		io.axiProfiling.axi.RDATA := DontCare
		io.axiProfiling.axi.RRESP := DontCare
	}

}

/* Since subentries are also stored in cacheline, we need to update MSHR entries including those lying in the stash.
   In the origin way, stash entries in pipeline also need to be updated, increasing complexity. In fact, we need to
   guarantee that the in-flight allocs can not exceed the number of stash slots (see allocsInFlight). Thus, it's OK
   to leave entries in the stash when re-inserting them into pipeline so that we can update them in spite of where
   they exactly are (stash or pipeline).
   By doing this, we can simplify the pipelne logic (removing redundant fields). But there is a tricky case where an
   in-pipeline stash entry is being deallocated and we can not invalidate it. So a request from stash is only valid
   when a matching entry is present in the stash.
   Without forwarding the whole data line, the data will arrive 3 cycle later than the corresponding tag. */
class InCacheMSHRStash(gen: UniTag, lastTableIdxWidth: Int, numStashEntries: Int) extends Module {
	val stashEntryType = new StashEntry(gen.tagWidth, gen.lastValidIdxWidth, lastTableIdxWidth)
	val io = IO(new Bundle {
		// query in
		val lookupTag       = Flipped(ValidIO(stashEntryType.tag.cloneType))
		val deallocMatching = Input(Bool())
		val matchingNoAhead = ValidIO(UInt(log2Ceil(numStashEntries).W)) // to get subentries in advance for write stage
		// query result out (with one cycle delay)
		val hit                  = Output(Bool())
		val matchingEntryNo      = Output(UInt(log2Ceil(numStashEntries).W)) // implicate the addr of subentries in BRAM
		val matchingLastValidIdx = Output(stashEntryType.lastValidIdx.cloneType)
		val matchingLastTableIdx = Output(stashEntryType.lastTableIdx.cloneType)
		// victim in
		val inVictim        = Flipped(ValidIO(new StashVictimInIO(gen.tagWidth, gen.lastValidIdxWidth, lastTableIdxWidth)))
		val newVictimNo     = Output(UInt(log2Ceil(numStashEntries).W)) // tell data BRAM where the victim's subentries should go
		val transitDoneNo   = Flipped(ValidIO(UInt(log2Ceil(numStashEntries).W))) // the OneHot code indicates whose subentry line's transaction is done
		val deallocNotReady = Output(Bool())
		// update in
		val inLastValidIdx = Flipped(ValidIO(stashEntryType.lastValidIdx.cloneType))
		// re-insert
		val outToPipeline = DecoupledIO(stashEntryType.tag.cloneType)
		// ready signal
		val pipelineReady   = Input(Bool())
		val wrPipelineReady = Input(Bool())
	})
	// memory init
	val invalidMemoryEntry = stashEntryType.getInvalidEntry()
	val memory = RegInit(Vec(Seq.fill(numStashEntries)(invalidMemoryEntry)))
	val emptyEntrySelect = PriorityEncoderOH(memory.map(x => ~x.valid))
	// match
	val matchIncoming  = io.inVictim.valid & (io.inVictim.bits.tag === io.lookupTag.bits) & io.lookupTag.valid
	val matchesNew     = emptyEntrySelect.map(x => x & matchIncoming) // where the new victim that hits will be stored
	val matchesOld     = memory.map(x => x.valid & (x.tag === io.lookupTag.bits) & io.lookupTag.valid)
	val matches        = matchesOld.zip(matchesNew).map(x => x._1 | x._2)
	val matches1CycAgo = RegEnable(Vec(matches).asUInt, init=0.U, enable=io.pipelineReady)
	val hit            = Vec(matches).asUInt.orR
	val hit1CycAgo     = RegEnable(hit, init=false.B, enable=io.pipelineReady)
	// update (2 cycles after querying the stash)
	val matches2CycAgo = RegEnable(matches1CycAgo, init=0.U, enable=io.wrPipelineReady)
	// forwarding latest idx for matching stage
	val successiveMatch = (matches1CycAgo & matches2CycAgo).orR & io.inLastValidIdx.valid
	// dealloc stall
	val dealloc1CycAgo = RegEnable(io.deallocMatching, init=false.B, enable=io.pipelineReady)
	// subentry line done transit
	val transitDoneOH = UIntToOH(io.transitDoneNo.bits)
	// re-insert
	val outArbiter = Module(new ResettableRRArbiter(io.outToPipeline.bits.cloneType, numStashEntries))

	io.matchingNoAhead.valid := hit & io.deallocMatching
	io.matchingNoAhead.bits  := OHToUInt(matches)
	io.matchingLastValidIdx := Mux(successiveMatch, io.inLastValidIdx.bits, Mux1H(matches1CycAgo, memory.map(x => x.lastValidIdx)))
	io.matchingLastTableIdx := Mux1H(matches1CycAgo, memory.map(x => x.lastTableIdx))
	io.matchingEntryNo      := RegEnable(io.matchingNoAhead.bits, enable=io.pipelineReady)
	io.hit                  := hit1CycAgo
	// indicate victim idx
	io.newVictimNo := OHToUInt(emptyEntrySelect)
	io.deallocNotReady := dealloc1CycAgo & Vec((0 until numStashEntries).map(i => memory(i).subTransit & matches1CycAgo(i))).asUInt.orR

	for (i <- 0 until numStashEntries) {
		when (io.pipelineReady) {
			// dealloc lookup might deallocate the incoming victim
			when (matches(i) & io.deallocMatching) {
				memory(i).valid := false.B
			} .elsewhen (io.inVictim.valid & emptyEntrySelect(i)) {
				memory(i).valid := true.B
			}
			// victim
			when (io.inVictim.valid & emptyEntrySelect(i)) {
				memory(i).inPipeline   := false.B
				memory(i).subTransit   := true.B
				memory(i).tag          := io.inVictim.bits.tag
				memory(i).lastValidIdx := io.inVictim.bits.lastValidIdx
				memory(i).lastTableIdx := io.inVictim.bits.lastTableIdx
			}
			// re-insert
			when (outArbiter.io.in(i).valid & outArbiter.io.in(i).ready) { // won't re-insert twice
				memory(i).inPipeline := true.B
			}
		}
		when (io.wrPipelineReady) {
			// subentry update
			when (matches2CycAgo(i) & io.inLastValidIdx.valid) {
				memory(i).lastValidIdx := io.inLastValidIdx.bits
			}
			when (io.transitDoneNo.valid & transitDoneOH(i)) {
				memory(i).subTransit := false.B
			}
		}
		// re-insert
		outArbiter.io.in(i).valid := memory(i).valid & ~memory(i).inPipeline & ~(matchesOld(i) & io.deallocMatching)
		outArbiter.io.in(i).bits  := memory(i).tag
	}
	outArbiter.io.out <> io.outToPipeline
}

/* Logic to raise stalls of allocs when any MSHR's subentry slots are getting full. By using cache line data field to store subentries,
 * it may be rare to fill all the slots of an MSHR, thus we don't bother to optimize these cases but just stall. That is, when an MSHR whose
 * empty slots are less than the pipeline length is being appended another subentry to, an alloc stall will raise. The rest allocs in the
 * pipeline are also possible to cause this case (getting full). Thus, we keep tracking those MSHRs' tag, after all of them are deallocated
 * can we accept allocs again. */
class FullSubentryTagArray(tagWidth: Int, pipelineLength: Int) extends Module {
	val io = IO(new Bundle {
		val in = Flipped(ValidIO(UInt(tagWidth.W)))
		val deallocMatchingTag = Input(Bool())
		val pipelineReady = Input(Bool())
		val stopAllocs = Output(Bool())
	})

	val valids = RegInit(Vec(Seq.fill(pipelineLength)(false.B)))
	val tags = Reg(Vec(pipelineLength, UInt(tagWidth.W)))
	val emptySelect = PriorityEncoderOH(valids.map(x => ~x))
	for (i <- 0 until pipelineLength) {
		when (io.pipelineReady & io.in.valid) {
			when (~io.deallocMatchingTag & emptySelect(i)) {
				valids(i) := true.B
				tags(i)   := io.in.bits
			} .elsewhen (io.deallocMatchingTag & io.in.bits === tags(i)) {
				valids(i) := false.B
			}
		}
	}
	io.stopAllocs := (io.in.valid & ~io.deallocMatchingTag) | valids.asUInt.orR
}
