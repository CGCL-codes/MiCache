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

	val tagType = new UniTag(tagWidth)
	val cacheDataType = UInt(memDataWidth.W)
	val subentryAlignWidth = 8
	val subentryLineType = new SubentryLine(memDataWidth, offsetWidth, idWidth, numSubentriesPerRow, subentryAlignWidth)
	val numEntriesPerLine = subentryLineType.entriesPerLine
	val forwardingType = new UniForwarding(tagWidth, subentryLineType.lastValidIdxWidth)

	val hashTableAddrWidth = log2Ceil(numMSHRPerHashTable)
	val hashMultConstWidth = if (tagWidth > MSHR.maxMultConstWidth) MSHR.maxMultConstWidth else tagWidth
	val hbmChannelWidth = 28 - offsetWidth - log2Ceil(reqDataWidth / 8)
	val tagHashWidth = if (tagWidth > hbmChannelWidth) hbmChannelWidth else tagWidth
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

	/* Input logic */
	val inputArbiter = Module(new Arbiter(new AddrDataIdIO(addrWidth, memDataWidth, idWidth), 2))
	val stopAllocs = Wire(Bool())
	val stopDeallocs = Wire(Bool())
	val stallOnlyAllocs = Wire(Bool())

	inputArbiter.io.in(0).valid     := io.deallocIn.valid & ~stopDeallocs
	inputArbiter.io.in(0).bits.addr := io.deallocIn.bits.addr
	inputArbiter.io.in(0).bits.data := io.deallocIn.bits.data
	io.deallocIn.ready              := inputArbiter.io.in(0).ready & ~stopDeallocs

	inputArbiter.io.in(1).valid     := io.allocIn.valid & ~stopAllocs
	inputArbiter.io.in(1).bits.addr := io.allocIn.bits.addr
	inputArbiter.io.in(1).bits.id   := io.allocIn.bits.id
	io.allocIn.ready                := inputArbiter.io.in(1).ready & ~stopAllocs

	/* Arbiter between input and stash. Input has higher priority: we try to put back
	* entries in the tables "in the background". */
	val stashArbiter = Module(new Arbiter(new AddrDataIdAllocIO(addrWidth, memDataWidth, idWidth), 2))
	/* Queue containing entries that have been kicked out from the hash tables, and that we will try
	* to put back in one of their other possible locations. */
	val stash = Module(new InCacheMSHRStash(tagWidth, subentryLineType, assocMemorySize, log2Ceil(numHashTables)))
	stashArbiter.io.in(0).valid        := inputArbiter.io.out.valid
	stashArbiter.io.in(0).bits.addr    := inputArbiter.io.out.bits.addr
	stashArbiter.io.in(0).bits.data    := inputArbiter.io.out.bits.data
	stashArbiter.io.in(0).bits.id      := inputArbiter.io.out.bits.id
	stashArbiter.io.in(0).bits.isAlloc := inputArbiter.io.chosen === 1.U
	inputArbiter.io.out.ready          := stashArbiter.io.in(0).ready

	stashArbiter.io.in(1).valid        := stash.io.outToPipeline.valid
	stashArbiter.io.in(1).bits         := DontCare
	stashArbiter.io.in(1).bits.addr    := Cat(stash.io.outToPipeline.bits, 0.U(offsetWidth.W))
	stashArbiter.io.in(1).bits.isAlloc := true.B
	stash.io.outToPipeline.ready       := stashArbiter.io.in(1).ready

	stashArbiter.io.out.ready := pipelineReady

	/* Pipeline */
	/* stashArbiter.io.out -> register -> hash computation -> memory read address and register -> register -> data coming back from memory */
	val delayedRequest = Wire(Vec(InCacheMSHR.pplRdLen, ValidIO(stashArbiter.io.out.bits.cloneType)))
	/* One entry per pipeline stage; whether the entry in that pipeline stage is from stash or not
	* Entries from the stash behave like allocations but they do not generate a new read to memory
	* nor a new allocation to the load buffer if they do not hit. */
	val delayedIsFromStash = Wire(Vec(InCacheMSHR.pplRdLen, Bool()))
	val isDelayedFromStash = delayedIsFromStash.last
	val isDelayedValid     = delayedRequest.last.valid & ~(isDelayedFromStash & ~stash.io.hit)
	val isDelayedAlloc     = delayedRequest.last.bits.isAlloc & isDelayedValid
	val isDelayedDealloc   = ~delayedRequest.last.bits.isAlloc & delayedRequest.last.valid
	delayedRequest(0).bits  := RegEnable(stashArbiter.io.out.bits, enable=pipelineReady)
	delayedRequest(0).valid := RegEnable(stashArbiter.io.out.valid, enable=pipelineReady, init=false.B)
	delayedIsFromStash(0)   := RegEnable(stashArbiter.io.chosen === 1.U, enable=pipelineReady)
	for (i <- 1 until InCacheMSHR.pplRdLen) {
		delayedRequest(i).bits  := RegEnable(delayedRequest(i - 1).bits, enable=pipelineReady)
		delayedRequest(i).valid := RegEnable(delayedRequest(i - 1).valid, enable=pipelineReady, init=false.B)
		delayedIsFromStash(i)   := RegEnable(delayedIsFromStash(i - 1), enable=pipelineReady)
	}

	/* Address hashing */
	val r = new scala.util.Random(42)
	val a = (0 until numHashTables).map(_ => r.nextInt(1 << hashMultConstWidth))
	// val b = (0 until numHashTables).map(_ => r.nextInt(1 << hashTableAddrWidth))
	val hashedTags = (0 until numHashTables).map(i => if (sameHashFunction) hash(a(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), getTag(delayedRequest(0).bits.addr)))
	// val hashedTags = (0 until numHashTables).map(i => if(sameHashFunction) hash(a(0), b(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), b(i), getTag(delayedRequest(0).bits.addr)))
	//a.indices.foreach(i => println(s"a($i)=${a(i)}"))
	/* Uncomment to print out hashing parameters a and b */
	// a.indices.foreach(i => println(s"a($i)=${a(i)} b($i)=${b(i)}"))

	/* Memories instantiation and interconnection */
	/* Memories are initialized with all zeros, which is fine for us since all the valids will be false */
	val tagMems = Array.fill(numHashTables)(Module(new XilinxSimpleDualPortNoChangeBRAM(width=tagType.getWidth, depth=numMSHRPerHashTable)).io)
	val dataMems = Array.fill(numHashTables)(Module(new XilinxTrueDualPort1RdWr1RdBRAM(width=memDataWidth, depth=numMSHRPerHashTable, byteWriteWidth=subentryAlignWidth)).io)
	val storeToLoads = Array.fill(numHashTables)(Module(new StoreToLoadForwardingThreeStages(forwardingType, hashTableAddrWidth)).io)
	val dataMemOuts = Wire(Vec(numHashTables, cacheDataType))
	for (i <- 0 until numHashTables) {
		val rdAddri = RegEnable(hashedTags(i), enable=pipelineReady)
		tagMems(i).clock  := clock
		tagMems(i).reset  := reset
		tagMems(i).addrb  := rdAddri
		tagMems(i).enb    := pipelineReady
		tagMems(i).regceb := pipelineReady
		val tagMemOuti = tagMems(i).doutb.asTypeOf(tagType)

		dataMems(i).clock  := clock
		dataMems(i).reset  := reset
		dataMems(i).addrb  := rdAddri
		dataMems(i).enb    := pipelineReady
		dataMems(i).regceb := pipelineReady
		dataMemOuts(i)     := dataMems(i).doutb.asTypeOf(cacheDataType)

		storeToLoads(i).rdAddr                     := rdAddri
		storeToLoads(i).dataInFromMem.valid        := tagMemOuti.valid
		storeToLoads(i).dataInFromMem.isMSHR       := tagMemOuti.isMSHR
		storeToLoads(i).dataInFromMem.tag          := tagMemOuti.tag
		storeToLoads(i).dataInFromMem.lastValidIdx := dataMemOuts(i).asTypeOf(subentryLineType).lastValidIdx
		storeToLoads(i).pipelineReady              := pipelineReady
	}

	/* Searching stash in advance. The result comes back after a cycle. */
	val wtidx = InCacheMSHR.pplRdLen - 2 // waiting stage index
	stash.io.lookupTag.bits  := getTag(delayedRequest(wtidx).bits.addr)
	stash.io.lookupTag.valid := delayedRequest(wtidx).valid
	stash.io.pipelineReady   := pipelineReady
	stash.io.deallocMatching := ~delayedRequest(wtidx).bits.isAlloc | delayedIsFromStash(wtidx)

	/* Matching logic */
	val dataRead = storeToLoads.map(x => x.dataInFixed)
	val hashTableMatches = dataRead.map(x => x.valid & x.tag === getTag(delayedRequest.last.bits.addr))
	val allMatches = hashTableMatches ++ Array(stash.io.hit)
	val hit = Vec(allMatches).asUInt.orR

	val cacheMatches = hashTableMatches.zip(dataRead).map(x => x._1 & ~x._2.isMSHR)
	val cacheHit = Vec(cacheMatches).asUInt.orR
    val delayedOffset = getOffset(delayedRequest.last.bits.addr)
	val hitDataLine = Mux1H(cacheMatches, dataMemOuts)
    val hitData = MuxLookup(delayedOffset, hitDataLine(reqDataWidth-1, 0), (0 until memDataWidth by reqDataWidth).map(i => (i/reqDataWidth).U -> hitDataLine(i+reqDataWidth-1, i)))
	val respOutEb = Module(new ElasticBuffer(io.respOut.bits.cloneType))
	respOutEb.io.in.valid     := isDelayedValid & cacheHit
	respOutEb.io.in.bits.id   := delayedRequest.last.bits.id
	respOutEb.io.in.bits.data := hitData
	respOutEb.io.out          <> io.respOut

	val mshrMatches = hashTableMatches.zip(dataRead).map(x => x._1 & x._2.isMSHR) ++ Array(stash.io.hit)
	val mshrHit = Vec(mshrMatches).asUInt.orR
	val selectedData = Mux1H(mshrMatches, dataRead.map(x => x.lastValidIdx) ++ Array(stash.io.matchingSubentryLine.lastValidIdx))

	val cacheMiss = ~cacheHit & isDelayedAlloc & ~isDelayedFromStash
	val mshrAllocHit = mshrHit & isDelayedAlloc & ~isDelayedFromStash
	dontTouch(cacheMiss)
	dontTouch(mshrAllocHit)

	val allValid = Vec(dataRead.map(x => x.valid)).asUInt.andR
	val allIsMSHR = Vec(dataRead.map(x => x.isMSHR)).asUInt.andR
	val allFull = allValid & allIsMSHR
	/* When a tag appears for the first time, we allocate an entry in one of the hash tables (HT).
	* To better spread the entries among HTs, we want all HTs to have the same priority; however, we can only choose
	* a hash table for which the entry corresponding to the new tag is free. We use a RRArbiter to implement this functionality, where we
	* do not care about the value to arbitrate and we use ~entry.valid as valid signal for the arbiter. */
	val fakeRRArbiterForSelect = Module(new ResettableRRArbiter(Bool(), numHashTables))
	for (i <- 0 until numHashTables) {
		fakeRRArbiterForSelect.io.in(i).valid := ~dataRead(i).valid | (allValid & ~dataRead(i).isMSHR)
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
	stash.io.inVictim.bits.tag          := Mux1H(evictOH, dataRead.map(x => x.tag))
	stash.io.inVictim.bits.lastValidIdx := Mux1H(evictOH, dataRead.map(x => x.lastValidIdx))
	stash.io.inVictim.bits.lastTableIdx := evictTable
	stash.io.inVictim.valid             := ((isDelayedAlloc & !hit) | (isDelayedFromStash & stash.io.hit)) & allFull
	evictCounterEnable                  := stash.io.inVictim.valid & pipelineReady

	val newAllocDone = isDelayedAlloc & !hit & ~isDelayedFromStash & pipelineReady
	fakeRRArbiterForSelect.io.out.ready := newAllocDone

	/* Queue and interface to external memory arbiter */
	val externalMemoryQueue = Module(new BRAMQueue(tagWidth, numMSHRTotal))
	externalMemoryQueue.io.deq <> io.outMem
	externalMemoryQueue.io.enq.valid := newAllocDone
	externalMemoryQueue.io.enq.bits := getTag(delayedRequest.last.bits.addr)

	/* Update logic */
	val wrPipelineReady = Wire(Bool())

	val isAllocWrite = RegEnable(isDelayedAlloc, init=false.B, enable=wrPipelineReady)
	val isFromStashWrite = RegEnable(isDelayedFromStash, init=false.B, enable=wrPipelineReady)
	val updatedTag = Wire(tagType)
	updatedTag.valid  := true.B
	updatedTag.isMSHR := isAllocWrite
	updatedTag.tag    := RegEnable(getTag(delayedRequest.last.bits.addr), enable=wrPipelineReady)
	val updatedSubentryLine = Wire(subentryLineType.cloneType)
	updatedSubentryLine.lastValidIdx := Mux(mshrHit, selectedData + 1.U, 0.U)
	updatedSubentryLine.padding      := DontCare
	updatedSubentryLine.entries.map(x => {
		x.offset  := getOffset(delayedRequest.last.bits.addr)
		x.id      := delayedRequest.last.bits.id
		x.padding := DontCare
	})
	val subLineFromStash = Wire(subentryLineType.cloneType)
	subLineFromStash := stash.io.matchingSubentryLine.withPadding(subentryLineType)
	val updatedData = RegEnable(Mux(isDelayedDealloc, delayedRequest.last.bits.data,
								Mux(isDelayedFromStash, subLineFromStash, updatedSubentryLine).asTypeOf(cacheDataType)), enable=wrPipelineReady)
	val updateEntryOH = UIntToOH(RegEnable(updatedSubentryLine.lastValidIdx, enable=wrPipelineReady))
	val updateWrEn = Wire(Vec(memDataWidth / subentryAlignWidth, Bool()))
	for (i <- 0 until subentryLineType.lastValidIdxBytes) { // bits order depends on chisel
		updateWrEn(subentryLineType.entryBytes * subentryLineType.entriesPerLine + i) := true.B
	}
	for (i <- 0 until subentryLineType.entriesPerLine) {
		for (j <- 0 until subentryLineType.entryBytes) {
			updateWrEn(i * subentryLineType.entryBytes + j) := updateEntryOH(i) | ~isAllocWrite | isFromStashWrite
		}
	}

	// Subentry almost full stall logic
	val subentryAlmostFullStall = Module(new FullSubentryTagArray(tagWidth, InCacheMSHR.pplRdLen)).io
	subentryAlmostFullStall.in.valid           := (isDelayedAlloc & !cacheHit & (numEntriesPerLine.U - updatedSubentryLine.lastValidIdx <= InCacheMSHR.pplRdLen.U)) | isDelayedDealloc
	subentryAlmostFullStall.in.bits            := getTag(delayedRequest.last.bits.addr)
	subentryAlmostFullStall.deallocMatchingTag := isDelayedDealloc
	subentryAlmostFullStall.pipelineReady      := pipelineReady

	// Avoid alloc after dealloc of the same line, since no forwarding support.
	val allocWaitToUpdateMatch1 = Wire(Bool())
	val allocWaitToUpdateMatch2 = Wire(Bool())
	allocWaitToUpdateMatch1 := (getTag(io.allocIn.bits.addr) === getTag(delayedRequest(0).bits.addr)) & ~delayedRequest(0).bits.isAlloc & delayedRequest(0).valid
	allocWaitToUpdateMatch2 := (getTag(io.allocIn.bits.addr) === getTag(delayedRequest(1).bits.addr)) & ~delayedRequest(1).bits.isAlloc & delayedRequest(1).valid

	/* Memory write port */
	val isDelayedValid1CycAgo = RegEnable(isDelayedValid, init=false.B, enable=wrPipelineReady)
	val matchWrEn = RegEnable(Vec(mshrMatches).asUInt, enable=wrPipelineReady)
	val newWrEn = RegEnable(!hit | isDelayedFromStash, enable=wrPipelineReady)
	val emptyWrEn = RegEnable(Vec(hashTableToUpdate.map(x => x & ~allFull)).asUInt, enable=wrPipelineReady)
	val evictWrEn = RegEnable(Vec((0 until numHashTables).map(i => evictOH(i) & allFull)).asUInt, enable=wrPipelineReady)
	for (i <- 0 until numHashTables) {
		tagMems(i).addra    := storeToLoads(i).wrAddr
		tagMems(i).wea      := isDelayedValid1CycAgo & (matchWrEn(i) | (newWrEn & (emptyWrEn(i) | evictWrEn(i)))) & wrPipelineReady
		tagMems(i).dina     := updatedTag.asUInt

		dataMems(i).addra   := storeToLoads(i).wrAddr
		dataMems(i).ena     := tagMems(i).wea
		dataMems(i).regcea  := wrPipelineReady
		dataMems(i).wea     := updateWrEn.asUInt
		dataMems(i).dina    := updatedData.asUInt

		storeToLoads(i).wrEn                      := tagMems(i).wea
		storeToLoads(i).wrPipelineReady           := wrPipelineReady
		storeToLoads(i).dataOutToMem.valid        := updatedTag.valid
		storeToLoads(i).dataOutToMem.isMSHR       := updatedTag.isMSHR
		storeToLoads(i).dataOutToMem.tag          := updatedTag.tag
		storeToLoads(i).dataOutToMem.lastValidIdx := updatedData.asTypeOf(subentryLineType).lastValidIdx
	}
	stash.io.inNewSubentryWrEn               := updateEntryOH
	stash.io.inNewSubentry.valid             := RegEnable(isDelayedAlloc & ~isDelayedFromStash & stash.io.hit, init=false.B, enable=wrPipelineReady)
	stash.io.inNewSubentry.bits.lastValidIdx := updatedData.asTypeOf(subentryLineType).lastValidIdx
	stash.io.inNewSubentry.bits.entry        := updatedData.asTypeOf(subentryLineType).entries(0)
	stash.io.wrPipelineReady                 := wrPipelineReady

	// Write pipeline
	val delayedResp = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedRespData = Wire(Vec(InCacheMSHR.pplWrLen, cacheDataType))
	val delayedSubSel = Wire(Vec(InCacheMSHR.pplWrLen, UInt(numHashTables.W)))
	val delayedEvict = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	delayedResp(0)     := RegEnable(isDelayedDealloc & ~stash.io.hit, init=false.B, enable=wrPipelineReady)
	delayedRespData(0) := updatedData
	delayedSubSel(0)   := Vec((0 until numHashTables).map(i => matchWrEn(i) | (evictWrEn(i) & newWrEn))).asUInt
	delayedEvict(0)    := RegEnable(stash.io.inVictim.valid, init=false.B, enable=wrPipelineReady)
	for (i <- 1 until InCacheMSHR.pplWrLen) {
		delayedResp(i)     := RegEnable(delayedResp(i - 1), init=false.B, enable=wrPipelineReady)
		delayedRespData(i) := RegEnable(delayedRespData(i - 1), enable=wrPipelineReady)
		delayedSubSel(i)   := RegEnable(delayedSubSel(i - 1), enable=wrPipelineReady)
		delayedEvict(i)    := RegEnable(delayedEvict(i - 1), init=false.B, enable=wrPipelineReady)
	}

	val delayedSubLine = Mux1H(delayedSubSel.last, dataMems.map(x => {
		val subLine = Wire(new SubentryLineWithNoPadding(offsetWidth, idWidth, numEntriesPerLine))
		val raw = x.douta.asTypeOf(subentryLineType)
		subLine.lastValidIdx := raw.lastValidIdx
		subLine.entries.zip(raw.entries).map(x => {
			x._1.offset := x._2.offset
			x._1.id     := x._2.id
		})
		subLine
	}))
	stash.io.inVictimSubentryLine.valid := delayedEvict.last
	stash.io.inVictimSubentryLine.bits  := delayedSubLine.entries

	val deallocStashMatching = isDelayedDealloc & stash.io.hit
	io.respGenOut.valid             := delayedResp.last | deallocStashMatching
	io.respGenOut.bits.data         := Mux(deallocStashMatching, delayedRequest.last.bits.data, delayedRespData.last)
	io.respGenOut.bits.entries      := Mux(deallocStashMatching, stash.io.matchingSubentryLine.entries, delayedSubLine.entries)
	io.respGenOut.bits.lastValidIdx := Mux(deallocStashMatching, stash.io.matchingSubentryLine.lastValidIdx, delayedSubLine.lastValidIdx)

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

	stopAllocs := MSHRAlmostFull | stallOnlyAllocs | subentryAlmostFullStall.stopAllocs | allocWaitToUpdateMatch1 | allocWaitToUpdateMatch2
	stopDeallocs := false.B

	pipelineReady := MuxCase(true.B, Array(respOutEb.io.in.valid -> respOutEb.io.in.ready,
										deallocStashMatching -> io.respGenOut.ready,
										isDelayedValid -> (~stash.io.deallocNotReady & wrPipelineReady)))
	wrPipelineReady := Mux(delayedResp.last, io.respGenOut.ready & ~deallocStashMatching, true.B)

	/* Profiling interface */
	if(Profiling.enable) {
		/* The order by which registers are appended to profilingRegisters defines the register map */
		val profilingRegisters = scala.collection.mutable.ListBuffer[UInt]()
		val currentlyUsedMSHR = RegEnable(allocatedMSHRCounter, enable=io.axiProfiling.snapshot)
		profilingRegisters += currentlyUsedMSHR
		val maxUsedMSHR = ProfilingMax(allocatedMSHRCounter, io.axiProfiling)
		profilingRegisters += maxUsedMSHR
		val maxUsedSubentry = ProfilingMax(Mux(updatedTag.isMSHR, updatedSubentryLine.lastValidIdx, 0.U), io.axiProfiling)
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
class InCacheMSHRStash(tagWidth: Int, subentryType: SubentryLine, numStashEntries: Int, lastTableIdxWidth: Int) extends Module {
	val stashEntryType = new StashEntry(tagWidth, subentryType, lastTableIdxWidth)
	val io = IO(new Bundle {
		// query in
		val lookupTag       = Flipped(ValidIO(UInt(tagWidth.W)))
		val deallocMatching = Input(Bool())
		// query result out (with one cycle delay)
		val hit                  = Output(Bool())
		val matchingSubentryLine = Output(stashEntryType.sub.cloneType)
		val matchingLastTableIdx = Output(UInt(lastTableIdxWidth.W))
		// victim in
		val inVictim             = Flipped(ValidIO(new StashVictimInIO(tagWidth, stashEntryType.sub.lastValidIdxWidth, lastTableIdxWidth)))
		val inVictimSubentryLine = Flipped(ValidIO(stashEntryType.sub.entries.cloneType))
		val deallocNotReady      = Output(Bool())
		// hit on secondary info in
		val inNewSubentryWrEn = Input(UInt(stashEntryType.sub.entriesPerLine.W))
		val inNewSubentry     = Flipped(ValidIO(new StashNewSubentryIO(stashEntryType.sub.entries(0).cloneType, stashEntryType.sub.lastValidIdxWidth)))
		// re-insert
		val outToPipeline = DecoupledIO(UInt(tagWidth.W))
		// ready signal
		val pipelineReady = Input(Bool())
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
	val hit1CycAgo     = RegEnable(Vec(matches).asUInt.orR, init=false.B, enable=io.pipelineReady)
	io.matchingSubentryLine := Mux1H(matches1CycAgo, memory.map(x => x.sub))
	io.matchingLastTableIdx := Mux1H(matches1CycAgo, memory.map(x => x.lastTableIdx))
	io.hit                  := hit1CycAgo
	// update (2 cycles after querying the stash)
	val matches2CycAgo = RegEnable(matches1CycAgo, init=0.U, enable=io.wrPipelineReady)
	// delayed subentry line
	val delayedVictimOH = Wire(Vec(InCacheMSHR.pplWrLen, UInt(numStashEntries.W))) // mark the new coming entry for its later subentries
	delayedVictimOH(0) := RegEnable(Vec(emptyEntrySelect).asUInt, init=0.U, enable=io.wrPipelineReady)
	for (i <- 1 until InCacheMSHR.pplWrLen) {
		delayedVictimOH(i) := RegEnable(delayedVictimOH(i - 1), init=0.U, enable=io.wrPipelineReady)
	}
	val idxToBitmap0 = RegEnable(io.inVictim.bits.lastValidIdx, enable=io.wrPipelineReady)
	val idxToBitmap1 = RegEnable(idxToBitmap0 + 1.U((stashEntryType.sub.lastValidIdxWidth + 1).W), enable=io.wrPipelineReady)
	val idxToBitmap2 = RegEnable(UIntToOH(idxToBitmap1), enable=io.wrPipelineReady)
	val delayedSubWrEn = idxToBitmap2 - 1.U
	// dealloc stall
	val dealloc1CycAgo = RegEnable(io.deallocMatching, init=false.B, enable=io.pipelineReady)
	io.deallocNotReady := dealloc1CycAgo & Vec((0 until numStashEntries).map(i => memory(i).subTransit & matches1CycAgo(i))).asUInt.orR
	// re-insert
	val outArbiter = Module(new ResettableRRArbiter(io.outToPipeline.bits.cloneType, numStashEntries))

	for (i <- 0 until numStashEntries) {
		when (io.pipelineReady) {
			// dealloc lookup might deallocate the incoming victim
			when (matches(i) & io.deallocMatching) {
				memory(i).valid            := false.B
			} .elsewhen (io.inVictim.valid & emptyEntrySelect(i)) {
				memory(i).valid            := true.B
			}
			// victim
			when (io.inVictim.valid & emptyEntrySelect(i)) {
				memory(i).inPipeline       := false.B
				memory(i).subTransit       := true.B
				memory(i).tag              := io.inVictim.bits.tag
				memory(i).sub.lastValidIdx := io.inVictim.bits.lastValidIdx
				memory(i).lastTableIdx     := io.inVictim.bits.lastTableIdx
			}
			// re-insert
			when (outArbiter.io.in(i).valid & outArbiter.io.in(i).ready) { // won't re-insert twice
				memory(i).inPipeline := true.B
			}
		}
		when (io.wrPipelineReady) {
			// subentry update
			when (matches2CycAgo(i) & io.inNewSubentry.valid) {
				memory(i).sub.lastValidIdx := io.inNewSubentry.bits.lastValidIdx
			}
			for (j <- 0 until stashEntryType.sub.entriesPerLine) {
				when (matches2CycAgo(i) & io.inNewSubentry.valid & io.inNewSubentryWrEn(j)) {
					memory(i).sub.entries(j) := io.inNewSubentry.bits.entry
				}
			}
			// delayed subentry line
			when (io.inVictimSubentryLine.valid & delayedVictimOH.last(i)) {
				memory(i).subTransit := false.B
				for (j <- 0 until stashEntryType.sub.entriesPerLine) {
					when (delayedSubWrEn(j)) {
						memory(i).sub.entries(j) := io.inVictimSubentryLine.bits(j)
					}
				}
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
