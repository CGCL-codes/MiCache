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
	val dataQueueDepth = 5 + 1
	val pplRdLen = 4
	val pplWrLen = 3
	val respQueueDepth = 6
	val respGenQueueDepth = 32
	val subentryAlignWidth = 9
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
	sameHashFunction:     Boolean=false,
	sizeReductionWidth:   Int=0
) extends Module {
	require(isPow2(memDataWidth / reqDataWidth))
	require(isPow2(numMSHRPerHashTable))
	val offsetWidth = log2Ceil(memDataWidth / reqDataWidth)
	val tagWidth = addrWidth - offsetWidth
	val numMSHRTotal = numMSHRPerHashTable * numHashTables

	val bramPortWidthAlignment = InCacheMSHR.subentryAlignWidth * 2 // BRAM18 provides 2-byte-wide ports
	val bram18Count = (memDataWidth / bramPortWidthAlignment) + (if (memDataWidth % bramPortWidthAlignment == 0) 0 else 1)
	val bramPortWidth = bram18Count * bramPortWidthAlignment
	val bramPortType = UInt((bram18Count * bramPortWidthAlignment).W)

	val cacheDataType = UInt(memDataWidth.W)
	val subentryLineType = new SubentryLine(bramPortWidth, offsetWidth, idWidth, numSubentriesPerRow, InCacheMSHR.subentryAlignWidth)
	val subLineNoPaddingType = new SubentryLineWithNoPadding(offsetWidth, idWidth, subentryLineType.entriesPerLine)
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
		val log2SizeReduction = Input(UInt(sizeReductionWidth.W))
		val invalidate = Input(Bool())
	})

	val invalidating = Wire(Bool())
	val invalidationAddressEn = Wire(Bool())
	val invalidationAddress = Counter(invalidationAddressEn, numMSHRPerHashTable)

	val allocPplStashReady = Wire(Bool())	// from stash stage
	val allocPplMatchReady = Wire(Bool())	// from match stage
	val allocPplWriteReady = Wire(Bool())	// from write stage
	val allocPplRespReady = Wire(Bool())	// after write stage
	val deallocPplStashReady = Wire(Bool())
	val deallocPplMatchReady = Wire(Bool())
	val deallocPplWriteReady = Wire(Bool())
	val deallocPplRespReady = Wire(Bool())

	/* Input logic */
	val allocInArbiter = Module(new Arbiter(new AddrIdIO(addrWidth, idWidth), 2))
	val subFullDelayQueue = Module(new Queue(new AddrIdIO(addrWidth, idWidth), InCacheMSHR.pplRdLen + 1 + 1))
	val deallocInArbiter = Module(new Arbiter(io.deallocIn.bits.addr.cloneType, 2))
	val inputDataQueue = Module(new Queue(io.deallocIn.bits.data.cloneType, InCacheMSHR.dataQueueDepth, pipe=true))
	val deallocRetryQueue = Module(new Queue(deallocInArbiter.io.out.bits.cloneType, 2, flow=true))
	val deallocRetrying = Wire(Bool())
	val stopAllocs = Wire(Bool())
	// val stopDeallocs = Wire(Bool())
	// val stallOnlyAllocs = Wire(Bool())

	// val issueDelayedReq = inputDataQueue.io.count =/= 0.U
	val issueDelayedReq = Wire(Bool())
	allocInArbiter.io.in(0).valid     := subFullDelayQueue.io.deq.valid & issueDelayedReq
	allocInArbiter.io.in(0).bits.addr := subFullDelayQueue.io.deq.bits.addr
	allocInArbiter.io.in(0).bits.id   := subFullDelayQueue.io.deq.bits.id
	subFullDelayQueue.io.deq.ready    := allocInArbiter.io.in(0).ready & issueDelayedReq
	allocInArbiter.io.in(1).valid     := io.allocIn.valid & ~stopAllocs & ~invalidating
	allocInArbiter.io.in(1).bits.addr := io.allocIn.bits.addr
	allocInArbiter.io.in(1).bits.id   := io.allocIn.bits.id
	io.allocIn.ready                  := allocInArbiter.io.in(1).ready & ~stopAllocs & ~invalidating

	val betterQueueTiming = false
	if (betterQueueTiming) {
	val inputDataQueueReady = Wire(Bool())
	val inputDataReadyToFire = io.deallocIn.valid & ~deallocInArbiter.io.in(0).valid & inputDataQueueReady
	inputDataQueueReady := RegNext(deallocPplStashReady, init=true.B) // for better timing
	val deallocInFirstTemporarily = RegEnable(inputDataReadyToFire, enable=inputDataQueueReady ^ deallocPplStashReady, init=false.B)
	val isDataInOrReady = RegEnable(inputDataReadyToFire | ~inputDataQueueReady, enable=inputDataQueueReady ^ deallocPplStashReady, init=true.B)
	deallocInArbiter.io.in(0).valid := deallocRetryQueue.io.deq.valid & ~deallocInFirstTemporarily
	deallocInArbiter.io.in(0).bits  := deallocRetryQueue.io.deq.bits
	deallocRetryQueue.io.deq.ready  := deallocInArbiter.io.in(0).ready & ~deallocInFirstTemporarily
	deallocInArbiter.io.in(1).valid := io.deallocIn.valid & isDataInOrReady//& inputDataQueue.io.enq.ready
	deallocInArbiter.io.in(1).bits  := io.deallocIn.bits.addr
	io.deallocIn.ready              := deallocInArbiter.io.in(1).ready & isDataInOrReady//& inputDataQueue.io.enq.ready
	inputDataQueue.io.enq.valid     := inputDataReadyToFire | deallocRetrying
	inputDataQueue.io.enq.bits      := Mux(deallocRetrying, inputDataQueue.io.deq.bits, io.deallocIn.bits.data)
	deallocInArbiter.io.out.ready   := deallocPplStashReady
	} else {
	deallocInArbiter.io.in(0).valid := deallocRetryQueue.io.deq.valid
	deallocInArbiter.io.in(0).bits  := deallocRetryQueue.io.deq.bits
	deallocRetryQueue.io.deq.ready  := deallocInArbiter.io.in(0).ready
	deallocInArbiter.io.in(1).valid := io.deallocIn.valid & inputDataQueue.io.enq.ready
	deallocInArbiter.io.in(1).bits  := io.deallocIn.bits.addr
	io.deallocIn.ready              := deallocInArbiter.io.in(1).ready & inputDataQueue.io.enq.ready
	inputDataQueue.io.enq.valid     := (io.deallocIn.valid & ~deallocInArbiter.io.in(0).valid & deallocPplStashReady) | deallocRetrying
	inputDataQueue.io.enq.bits      := Mux(deallocRetrying, inputDataQueue.io.deq.bits, io.deallocIn.bits.data)
	deallocInArbiter.io.out.ready   := deallocPplStashReady
	}

	/* Arbiter between input and stash. Input has higher priority: we try to put back
	* entries in the tables "in the background". */
	val stashArbiter = Module(new Arbiter(new AddrIdIO(addrWidth, idWidth), 2))
	/* Queue containing entries that have been kicked out from the hash tables, and that we will try
	* to put back in one of their other possible locations. */
	val stash = Module(new InCacheMSHRStash(tagType, log2Ceil(numHashTables), subLineNoPaddingType, assocMemorySize))
	stashArbiter.io.in(0).valid        := allocInArbiter.io.out.valid
	stashArbiter.io.in(0).bits.addr    := allocInArbiter.io.out.bits.addr
	stashArbiter.io.in(0).bits.id      := allocInArbiter.io.out.bits.id
	allocInArbiter.io.out.ready        := stashArbiter.io.in(0).ready

	// val reinsertHazard1 = Wire(Bool())
	// val reinsertHazard2 = Wire(Bool())
	stashArbiter.io.in(1).valid        := stash.io.outToPipeline.valid //& ~reinsertHazard1 & ~reinsertHazard2
	stashArbiter.io.in(1).bits.addr    := Cat(stash.io.outToPipeline.bits, 0.U(offsetWidth.W))
	stash.io.outToPipeline.ready       := stashArbiter.io.in(1).ready //& ~reinsertHazard1 & ~reinsertHazard2

	stashArbiter.io.out.ready := allocPplStashReady

	/* Pipeline */
	val allocPipelineType = new AllocPipelineIO(addrWidth, idWidth)
	val pplAllocHash = Wire(allocPipelineType.cloneType)
	val pplAllocRead = Wire(allocPipelineType.cloneType)
	val pplAllocStash = Wire(allocPipelineType.cloneType)
	val pplAllocMatch = Wire(allocPipelineType.cloneType)
	val pplAllocWrite = Wire(allocPipelineType.cloneType)
	val subentryFull = Wire(Bool())
	pplAllocHash.valid       := RegEnable(stashArbiter.io.out.valid,      enable=allocPplStashReady, init=false.B)
	pplAllocHash.addr        := RegEnable(stashArbiter.io.out.bits.addr,  enable=allocPplStashReady)
	pplAllocHash.id          := RegEnable(stashArbiter.io.out.bits.id,    enable=allocPplStashReady)
	pplAllocHash.isFromStash := RegEnable(stashArbiter.io.out.valid & (stashArbiter.io.chosen === 1.U), enable=allocPplStashReady, init=false.B)
	pplAllocRead  := RegEnable(pplAllocHash, enable=allocPplStashReady, init=allocPipelineType.getInvalid())
	pplAllocStash := RegEnable(pplAllocRead, enable=allocPplStashReady, init=allocPipelineType.getInvalid())
	pplAllocMatch.valid       := RegEnable(pplAllocStash.valid & ~(pplAllocStash.isFromStash & ~stash.io.reinsertValid) & allocPplStashReady, enable=allocPplMatchReady, init=false.B)
	pplAllocMatch.addr        := RegEnable(pplAllocStash.addr,        enable=allocPplMatchReady)
	pplAllocMatch.id          := RegEnable(pplAllocStash.id,          enable=allocPplMatchReady)
	pplAllocMatch.isFromStash := RegEnable(stash.io.reinsertValid & allocPplStashReady, enable=allocPplMatchReady, init=false.B)
	pplAllocWrite.valid       := RegEnable(pplAllocMatch.valid & allocPplMatchReady & ~subentryFull, enable=allocPplWriteReady, init=false.B)
	pplAllocWrite.addr        := RegEnable(pplAllocMatch.addr,        enable=allocPplWriteReady)
	pplAllocWrite.id          := RegEnable(pplAllocMatch.id,          enable=allocPplWriteReady)
	pplAllocWrite.isFromStash := RegEnable(pplAllocMatch.isFromStash & allocPplMatchReady, enable=allocPplWriteReady, init=false.B)

	val deallocPipelineType = new DeallocPipelineIO(addrWidth)
	val pplDeallocHash = Wire(deallocPipelineType.cloneType)
	val pplDeallocRead = Wire(deallocPipelineType.cloneType)
	val pplDeallocStash = Wire(deallocPipelineType.cloneType)
	val pplDeallocMatch = Wire(deallocPipelineType.cloneType)
	val pplDeallocWrite = Wire(deallocPipelineType.cloneType)
	pplDeallocHash.valid := RegEnable(deallocInArbiter.io.out.valid, enable=deallocPplStashReady, init=false.B)
	pplDeallocHash.addr  := RegEnable(deallocInArbiter.io.out.bits, enable=deallocPplStashReady)
	pplDeallocRead  := RegEnable(pplDeallocHash, enable=deallocPplStashReady, init=deallocPipelineType.getInvalid())
	pplDeallocStash := RegEnable(pplDeallocRead, enable=deallocPplStashReady, init=deallocPipelineType.getInvalid())
	pplDeallocMatch.valid := RegEnable(pplDeallocStash.valid & deallocPplStashReady, enable=deallocPplMatchReady, init=false.B)
	pplDeallocMatch.addr  := RegEnable(pplDeallocStash.addr, enable=deallocPplMatchReady)
	pplDeallocWrite.valid := RegEnable(pplDeallocMatch.valid & deallocPplMatchReady, enable=deallocPplWriteReady, init=false.B)
	pplDeallocWrite.addr  := RegEnable(pplDeallocMatch.addr, enable=deallocPplWriteReady)
	issueDelayedReq := RegEnable(pplDeallocWrite.valid, enable=pplDeallocWrite.valid | subFullDelayQueue.io.enq.valid, init=false.B)

	/* When re-inserting a stash entry, a BRAM read is claimed in stash searching stage. We want no update on this entry,
	   that is, the previous two (aka BRAM delay) requests must not hit the re-inserting stash entry. */
	// reinsertHazard1 := (stash.io.outToPipeline.bits === getTag(pplAllocHash.addr)) & pplAllocHash.valid
	// reinsertHazard2 := (stash.io.outToPipeline.bits === getTag(pplAllocRead.addr)) & pplAllocRead.valid

	/* Pipeline hashing stage */
	val r = new scala.util.Random(42)
	val a = (0 until numHashTables).map(_ => r.nextInt(1 << hashMultConstWidth))
	// val b = (0 until numHashTables).map(_ => r.nextInt(1 << hashTableAddrWidth))
	val hashedAllocTags = (0 until numHashTables).map(i => hash(a(i), getTag(pplAllocHash.addr)))
	val hashedDeallocTags = (0 until numHashTables).map(i => hash(a(i), getTag(pplDeallocHash.addr)))
	// val hashedTags = (0 until numHashTables).map(i => if(sameHashFunction) hash(a(0), b(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), b(i), getTag(delayedRequest(0).bits.addr)))
	//a.indices.foreach(i => println(s"a($i)=${a(i)}"))
	/* Uncomment to print out hashing parameters a and b */
	// a.indices.foreach(i => println(s"a($i)=${a(i)} b($i)=${b(i)}"))

	/* Memories instantiation and interconnection */
	/* Memories are initialized with all zeros, which is fine for us since all the valids will be false */
	val tagMems = Array.fill(numHashTables)(Module(new XilinxTrueDualPortReadFirstBRAM(width=tagType.getWidth, depth=numMSHRPerHashTable)).io)
	val dataMem = Module(new XilinxTDPReadFirstByteWriteBRAM(width=bramPortWidth, depth=numMSHRTotal, byteWriteWidth=InCacheMSHR.subentryAlignWidth)).io
	val storeToLoads = Array.fill(numHashTables)(Module(new StoreToLoadForwardingDualPPL(tagType, hashTableAddrWidth)).io)

	/* Pipeline reading stage */
	val log2SizeReductionMask = MuxLookup(io.log2SizeReduction, Fill(hashTableAddrWidth, 1.U), (0 until (1 << io.log2SizeReduction.getWidth)).map(i => (i.U -> Fill(hashTableAddrWidth - i, 1.U))))
	val deallocReadAddrs = Wire(Vec(numHashTables, tagMems(0).addrb.cloneType))
	val deallocReadEns = Wire(Vec(numHashTables, Bool()))
	val invalidatedTag = Wire(UInt(tagType.getWidth.W))
	invalidatedTag := 0.U
	for (i <- 0 until numHashTables) {
		val rdAddrAi = RegEnable(hashedAllocTags(i), enable=allocPplStashReady) & log2SizeReductionMask
		deallocReadAddrs(i) := RegEnable(hashedDeallocTags(i), enable=deallocPplStashReady) & log2SizeReductionMask
		tagMems(i).clock  := clock
		tagMems(i).reset  := reset
		tagMems(i).addra  := Mux(invalidating, invalidationAddress._1, rdAddrAi)
		tagMems(i).ena    := allocPplStashReady
		tagMems(i).regcea := allocPplMatchReady
		tagMems(i).wea    := invalidating
		tagMems(i).dina   := invalidatedTag
		storeToLoads(i).rdAddrReadA := rdAddrAi
		storeToLoads(i).rdAddrReadD := deallocReadAddrs(i)
		storeToLoads(i).dataInFromMemA := tagMems(i).douta.asTypeOf(tagType)
		storeToLoads(i).pipelineReadyA  := allocPplStashReady
		storeToLoads(i).pipelineReady1A := allocPplMatchReady
		storeToLoads(i).pipelineReady2A := allocPplWriteReady
		storeToLoads(i).pipelineReadyD  := deallocPplStashReady
		storeToLoads(i).pipelineReady1D := deallocPplMatchReady
		storeToLoads(i).pipelineReady2D := deallocPplWriteReady
	}

	/* Pipeline stash looking up stage. Searching stash in advance. The result comes back after a cycle. */
	stash.io.lookupTagA.valid := pplAllocStash.valid
	stash.io.lookupTagA.bits  := getTag(pplAllocStash.addr)
	stash.io.isReInsertingA   := pplAllocStash.isFromStash
	stash.io.pipelineReadyA  := allocPplStashReady
	stash.io.pipelineReady1A := allocPplMatchReady
	stash.io.pipelineReady2A := allocPplWriteReady
	stash.io.pipelineReady3A := allocPplRespReady

	stash.io.lookupTagD.valid := pplDeallocStash.valid
	stash.io.lookupTagD.bits  := getTag(pplDeallocStash.addr)
	stash.io.pipelineReadyD  := deallocPplStashReady
	stash.io.pipelineReady1D := deallocPplMatchReady
	stash.io.pipelineReady2D := deallocPplWriteReady

	// val readStashEntryForReInserting = stash.io.matchingNoAheadA.valid & pplAllocStash.isFromStash & allocPplStashReady
	// val readStashEntryForReInserting = pplAllocStash.isFromStash & allocPplStashReady

	/* Pipeline matching stage */
	val tagsAllocRead = storeToLoads.map(x => x.dataInFixedA)
	val tableAllocMatches = tagsAllocRead.map(x => x.valid & x.tag === getTag(pplAllocMatch.addr))
	val allAllocMatches = tableAllocMatches ++ Array(stash.io.hitA)
	val allocHit = Vec(allAllocMatches).asUInt.orR

	val cacheMatches = tableAllocMatches.zip(tagsAllocRead).map(x => x._1 & ~x._2.isMSHR)
	val cacheHit = Vec(cacheMatches).asUInt.orR
	val mshrAllocMatches = tableAllocMatches.zip(tagsAllocRead).map(x => x._1 & x._2.isMSHR) ++ Array(stash.io.hitA)
	val allocLastValidIdx = Mux1H(mshrAllocMatches, tagsAllocRead.map(x => x.lastValidIdx) ++ Array(stash.io.matchingLastValidIdxA))

	subentryFull := Vec(mshrAllocMatches).asUInt.orR & (allocLastValidIdx === (numEntriesPerLine - 1).U) & ~pplAllocMatch.isFromStash

	// For simulation
	val cacheMiss = ~cacheHit & pplAllocMatch.valid & ~pplAllocMatch.isFromStash
	val mshrAllocHit = Vec(mshrAllocMatches).asUInt.orR & ~pplAllocMatch.isFromStash
	val stashAllocHit = stash.io.hitA & ~pplAllocMatch.isFromStash
	dontTouch(cacheMiss)
	dontTouch(mshrAllocHit)
	dontTouch(stashAllocHit)

	val allValid = Vec(tagsAllocRead.map(x => x.valid)).asUInt.andR
	val allIsMSHR = Vec(tagsAllocRead.map(x => x.isMSHR)).asUInt.andR
	val allFull = allValid & allIsMSHR
	/* When a tag appears for the first time, we allocate an entry in one of the hash tables (HT).
	* To better spread the entries among HTs, we want all HTs to have the same priority; however, we can only choose
	* a hash table for which the entry corresponding to the new tag is free. We use a RRArbiter to implement this functionality, where we
	* do not care about the value to arbitrate and we use ~entry.valid as valid signal for the arbiter. */
	val fakeRRArbiterForSelect = Module(new ResettableRRArbiter(Bool(), numHashTables))
	for (i <- 0 until numHashTables) {
		fakeRRArbiterForSelect.io.in(i).valid := ~tagsAllocRead(i).valid | (allValid & ~tagsAllocRead(i).isMSHR)
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
	val evictTableForEntryFromStash = Mux(stash.io.matchingLastTableIdxA === (numHashTables - 1).U, 0.U, stash.io.matchingLastTableIdxA + 1.U)
	val evictTable = Mux(pplAllocMatch.isFromStash, evictTableForEntryFromStash, evictTableForFirstAttempt._1)
	val evictOH = UIntToOH(evictTable)
	stash.io.inVictim.bits.tag          := Mux1H(evictOH, tagsAllocRead.map(x => x.tag))
	stash.io.inVictim.bits.lastValidIdx := Mux1H(evictOH, tagsAllocRead.map(x => x.lastValidIdx))
	stash.io.inVictim.bits.lastTableIdx := evictTable
	stash.io.inVictim.valid             := pplAllocMatch.valid & (~allocHit | pplAllocMatch.isFromStash) & allFull
	evictCounterEnable                  := stash.io.inVictim.valid & allocPplMatchReady

	val isPrimaryAlloc = pplAllocMatch.valid & ~allocHit & allocPplMatchReady
	fakeRRArbiterForSelect.io.out.ready := isPrimaryAlloc

	/* Queue and interface to external memory arbiter */
	val externalMemoryQueue = Module(new BRAMQueue(tagWidth, numMSHRTotal))
	externalMemoryQueue.io.deq <> io.outMem
	externalMemoryQueue.io.enq.valid := RegNext(isPrimaryAlloc, init=false.B)
	externalMemoryQueue.io.enq.bits := getTag(pplAllocWrite.addr)

	/* Deallocation match stage */
	val deallocReadValids = Wire(Vec(numHashTables, Bool()))
	val tagsDeallocRead = storeToLoads.map(x => x.dataInFixedD)
	val tableDeallocMatches = tagsDeallocRead.zip(deallocReadValids).map(x => x._1.valid & x._2 & x._1.tag === getTag(pplDeallocMatch.addr))
	val mshrDeallocMatches = tableDeallocMatches ++ Array(stash.io.hitD)
	val deallocHit = Vec(mshrDeallocMatches).asUInt.orR
	val deallocLastValidIdx = Mux1H(mshrDeallocMatches, tagsDeallocRead.map(x => x.lastValidIdx) ++ Array(stash.io.matchingLastValidIdxD))
	// if only one way is unread, and no hits claimed, then the unread one must be the target
	val deallocHitUnread = (PopCount(deallocReadValids) === (numHashTables - 1).U) & ~deallocHit
	val mshrDeallocActualHit = tableDeallocMatches.zip(deallocReadValids).map(x => x._1 | (~x._2 & deallocHitUnread)) ++ Array(stash.io.hitD)

	/* Processing pipeline */
	// alloc
	// val pplAllocWriteValid = Wire(Bool())
	val delayedCacheHit = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedOffset = Wire(Vec(InCacheMSHR.pplWrLen - 1, UInt(offsetWidth.W)))
	val delayedId = Wire(Vec(InCacheMSHR.pplWrLen - 1, UInt(idWidth.W)))
	val delayedEvict = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedStashVictimNo = Wire(Vec(InCacheMSHR.pplWrLen, UInt(log2Ceil(assocMemorySize).W)))
	delayedCacheHit(0) := RegEnable(cacheHit & pplAllocMatch.valid & allocPplMatchReady, init=false.B, enable=allocPplWriteReady)
	delayedOffset(0) := RegEnable(getOffset(pplAllocWrite.addr), enable=allocPplRespReady)
	delayedOffset(1) := RegEnable(delayedOffset(0), enable=allocPplRespReady)
	delayedId(0) := RegEnable(pplAllocWrite.id, enable=allocPplRespReady)
	delayedId(1) := RegEnable(delayedId(0), enable=allocPplRespReady)
	delayedEvict(0) := RegEnable(stash.io.inVictim.valid & pplAllocMatch.valid & allocPplMatchReady, init=false.B, enable=allocPplWriteReady)
	delayedEvict(1) := RegEnable(delayedEvict(0) & allocPplWriteReady, init=false.B, enable=allocPplRespReady)
	delayedEvict(2) := RegEnable(delayedEvict(1), init=false.B, enable=allocPplRespReady)
	delayedStashVictimNo(0) := RegEnable(stash.io.newVictimNo, enable=allocPplWriteReady)
	for (i <- 1 until InCacheMSHR.pplWrLen) {
		delayedCacheHit(i) := RegEnable(delayedCacheHit(i - 1), init=false.B, enable=allocPplRespReady)
		delayedStashVictimNo(i) := RegEnable(delayedStashVictimNo(i - 1), enable=allocPplRespReady)
	}

	/* Pipeline writing stage */
	// alloc
	val tableAllocSel = Wire(Vec(numHashTables, Bool()))	// to cal out addr in data BRAM
	val tableAllocMatchSel = RegEnable(Vec(tableAllocMatches).asUInt, enable=allocPplWriteReady)
	val matchAllocWrEn = RegEnable(Vec(mshrAllocMatches).asUInt, enable=allocPplWriteReady)
	val stashAllocWrEn = matchAllocWrEn(numHashTables)
	val newWrEn = RegEnable(~allocHit, enable=allocPplWriteReady) | pplAllocWrite.isFromStash
	val emptyWrEn = RegEnable(Vec(hashTableToUpdate.map(x => x & ~allFull)).asUInt, enable=allocPplWriteReady)
	val evictWrEn = RegEnable(Vec((0 until numHashTables).map(i => evictOH(i) & allFull)).asUInt, enable=allocPplWriteReady)
	val allocSubIdx = RegEnable(allocLastValidIdx, enable=allocPplWriteReady)

	val updatedAllocTag = Wire(tagType)
	updatedAllocTag.valid        := true.B
	updatedAllocTag.isMSHR       := true.B
	updatedAllocTag.tag          := getTag(pplAllocWrite.addr)
	updatedAllocTag.lastValidIdx := Mux(matchAllocWrEn.orR, allocSubIdx + ~pplAllocWrite.isFromStash, 0.U)
	subFullDelayQueue.io.enq.valid     := RegEnable(pplAllocMatch.valid & subentryFull & allocPplMatchReady, enable=allocPplWriteReady, init=false.B) & allocPplWriteReady
	subFullDelayQueue.io.enq.bits.addr := pplAllocWrite.addr
	subFullDelayQueue.io.enq.bits.id   := pplAllocWrite.id
	val updatedSubentryLine = Wire(subentryLineType.cloneType)
	updatedSubentryLine.entries.map(x => {
		x        := DontCare
		x.offset := getOffset(pplAllocWrite.addr)
		x.id     := pplAllocWrite.id
	})
	val updateEntryOH = UIntToOH(updatedAllocTag.lastValidIdx)
	val updateWrEnPortA = Wire(Vec(bramPortWidth / InCacheMSHR.subentryAlignWidth, Bool()))
	for (i <- 0 until subentryLineType.entriesPerLine) {
		for (j <- 0 until subentryLineType.entryBytes) {
			updateWrEnPortA(i * subentryLineType.entryBytes + j) := (updateEntryOH(i) | pplAllocWrite.isFromStash) & ~delayedCacheHit(0)
		}
	}
	for (i <- subentryLineType.entriesPerLine * subentryLineType.entryBytes until bramPortWidth / InCacheMSHR.subentryAlignWidth) {
		updateWrEnPortA(i) := false.B
	}
	stash.io.inSubline     := updatedSubentryLine.withNoPadding().entries(0)
	stash.io.inSublineMask := Vec((0 until numEntriesPerLine).map(i => updateEntryOH(i)))

	// dealloc
	val tableDeallocSel = Wire(Vec(numHashTables, Bool()))
	val matchDeallocWrEn = RegEnable(Vec(mshrDeallocActualHit).asUInt, enable=deallocPplWriteReady)
	val stashDeallocWrEn = matchDeallocWrEn(numHashTables)
	val bramDeallocWrEn = tableDeallocSel.asUInt.orR
	val deallocWrEn = matchDeallocWrEn.orR

	val delayedResp = Wire(Vec(InCacheMSHR.pplWrLen - 1, Bool()))
	val delayedLastValidIdx = Wire(Vec(InCacheMSHR.pplWrLen, UInt(subentryLineType.lastValidIdxWidth.W)))
	val delayedIsHitUnread = Wire(Vec(InCacheMSHR.pplWrLen, Bool()))
	val delayedDeallocOH = Wire(Vec(InCacheMSHR.pplWrLen - 1, UInt(numHashTables.W)))
	val delayedRespFromStash = Wire(Vec(InCacheMSHR.pplWrLen - 1, Bool()))
	delayedResp(0) := RegEnable(pplDeallocWrite.valid & deallocWrEn & deallocPplWriteReady, init=false.B, enable=deallocPplRespReady)
	delayedResp(1) := RegEnable(delayedResp(0), init=false.B, enable=deallocPplRespReady)
	delayedLastValidIdx(0) := RegEnable(deallocLastValidIdx, enable=deallocPplWriteReady)
	delayedIsHitUnread(0) := RegEnable(deallocHitUnread, init=false.B, enable=deallocPplWriteReady)
	delayedDeallocOH(0) := RegEnable(matchDeallocWrEn(numHashTables - 1, 0), enable=deallocPplRespReady)
	delayedDeallocOH(1) := RegEnable(delayedDeallocOH(0), enable=deallocPplRespReady)
	delayedRespFromStash(0) := RegEnable(stashDeallocWrEn, enable=deallocPplRespReady)
	delayedRespFromStash(1) := RegEnable(delayedRespFromStash(0), enable=deallocPplRespReady)
	for (i <- 1 until InCacheMSHR.pplWrLen) {
		delayedLastValidIdx(i) := RegEnable(delayedLastValidIdx(i - 1), enable=deallocPplRespReady)
		delayedIsHitUnread(i) := RegEnable(delayedIsHitUnread(i - 1), init=false.B, enable=deallocPplRespReady)
	}
	for (i <- 0 until numHashTables) {
		// deallocReadValids(i) := RegEnable(RegEnable(deallocReadEns(i), enable=deallocPplStashReady), enable=deallocPplMatchReady)
		val overwriteBramOutput = ~deallocPplStashReady & tagMems(i).web
		val deallocReadValidAtStashStage = RegEnable(deallocReadEns(i) & ~overwriteBramOutput, enable=deallocPplStashReady | overwriteBramOutput)
		val overwriteBramOutputReg = ~deallocPplMatchReady & (delayedResp(0) & delayedIsHitUnread(1) & delayedDeallocOH(0)(i))
		deallocReadValids(i) := RegEnable(deallocReadValidAtStashStage & ~overwriteBramOutputReg, enable=deallocPplMatchReady | overwriteBramOutputReg)
	}

	val updatedDeallocTag = Wire(tagType)
	updatedDeallocTag.valid        := true.B
	updatedDeallocTag.isMSHR       := false.B
	updatedDeallocTag.tag          := getTag(pplDeallocWrite.addr)
	updatedDeallocTag.lastValidIdx := 0.U

	deallocRetrying                := pplDeallocWrite.valid & ~deallocWrEn & deallocPplWriteReady
	deallocRetryQueue.io.enq.valid := deallocRetrying
	deallocRetryQueue.io.enq.bits  := pplDeallocWrite.addr
	inputDataQueue.io.deq.ready := pplDeallocWrite.valid & deallocPplWriteReady
	val respDataQueue = Module(new Queue(inputDataQueue.io.deq.bits.cloneType, 2, pipe=true))
	respDataQueue.io.enq.valid := pplDeallocWrite.valid & deallocWrEn & deallocPplWriteReady
	respDataQueue.io.enq.bits  := inputDataQueue.io.deq.bits

	/* Memory write port */
	val allocWrEns = Wire(Vec(numHashTables, Bool()))
	val allocWritings = Wire(Vec(numHashTables, Bool()))
	val deallocWritings = Wire(Vec(numHashTables, Bool()))
	for (i <- 0 until numHashTables) {
		tableAllocSel(i)   := (tableAllocMatchSel(i) | (newWrEn & (emptyWrEn(i) | evictWrEn(i))))
		tableDeallocSel(i) := matchDeallocWrEn(i)
		// allocWrEns(i)      := matchAllocWrEn(i) | (newWrEn & (emptyWrEn(i) | evictWrEn(i)))
		allocWrEns(i)      := RegEnable(mshrAllocMatches(i) | ((~allocHit | pplAllocMatch.isFromStash & allocPplMatchReady) & ((hashTableToUpdate(i) & ~allFull) | (evictOH(i) & allFull))), enable=allocPplWriteReady)
		allocWritings(i)   := pplAllocWrite.valid & allocWrEns(i) & allocPplWriteReady
		deallocWritings(i) := pplDeallocWrite.valid & matchDeallocWrEn(i) & deallocPplWriteReady
		tagMems(i).addrb  := MuxCase(deallocReadAddrs(i), Array(allocWritings(i) -> storeToLoads(i).wrAddrWriteA, deallocWritings(i) -> storeToLoads(i).wrAddrWriteD))
		tagMems(i).enb    := allocWritings(i) | deallocWritings(i) | (pplDeallocRead.valid & deallocPplStashReady)
		// tagMems(i).regceb := RegNext(deallocReadEns(i))
		tagMems(i).regceb := deallocPplMatchReady | (delayedResp(0) & delayedIsHitUnread(1) & delayedDeallocOH(0)(i))
		tagMems(i).web    := allocWritings(i) | deallocWritings(i)
		tagMems(i).dinb   := Mux(allocWritings(i), updatedAllocTag, updatedDeallocTag).asUInt
		storeToLoads(i).wrEn := tagMems(i).web
		storeToLoads(i).wrAddr := Mux(allocWritings(i), storeToLoads(i).wrAddrWriteA, storeToLoads(i).wrAddrWriteD)
		storeToLoads(i).dataInFromMemD := tagMems(i).doutb.asTypeOf(tagType)
		storeToLoads(i).dataOutToMem := tagMems(i).dinb.asTypeOf(tagType)
		deallocReadEns(i) := ~tagMems(i).web & pplDeallocRead.valid & deallocPplStashReady
	}
	dataMem.clock := clock
	dataMem.reset := reset
	// alloc
	dataMem.addra  := Cat(OHToUInt(tableAllocSel), Mux1H(tableAllocSel, storeToLoads.map(x => x.wrAddrWriteA)))
	dataMem.dina   := Mux(pplAllocWrite.isFromStash, SubentryLineWithNoPadding.addPadding(stash.io.matchingSubline, subentryLineType).asTypeOf(bramPortType), updatedSubentryLine.asTypeOf(bramPortType))
	dataMem.ena    := allocPplWriteReady & pplAllocWrite.valid & (~stashAllocWrEn | pplAllocWrite.isFromStash)
	dataMem.regcea := allocPplRespReady & (delayedEvict(1) | delayedCacheHit(1))
	dataMem.wea    := updateWrEnPortA.asUInt
	// stash.io.inLastValidIdx.valid := pplAllocWrite.valid & stashAllocWrEn & ~pplAllocWrite.isFromStash
	stash.io.inLastValidIdx.valid := RegEnable(pplAllocMatch.valid & allocPplMatchReady & ~subentryFull & stash.io.hitA & ~pplAllocMatch.isFromStash, enable=allocPplWriteReady, init=false.B)
	stash.io.inLastValidIdx.bits  := updatedAllocTag.lastValidIdx
	// dealloc, stash read, and eviction
	dataMem.addrb  := Cat(OHToUInt(tableDeallocSel), Mux1H(tableDeallocSel, storeToLoads.map(x => x.wrAddrWriteD)))
	dataMem.dinb   := inputDataQueue.io.deq.bits
	dataMem.enb    := deallocPplWriteReady
	dataMem.regceb := deallocPplRespReady
	val updateWrEnPortB = Wire(Vec(bramPortWidth / InCacheMSHR.subentryAlignWidth, Bool()))
	dataMem.web    := updateWrEnPortB.asUInt
	for (i <- 0 until subentryLineType.entriesPerLine) {
		for (j <- 0 until subentryLineType.entryBytes) {
			updateWrEnPortB(i * subentryLineType.entryBytes + j) := pplDeallocWrite.valid & bramDeallocWrEn & deallocPplWriteReady
		}
	}
	for (i <- subentryLineType.entriesPerLine * subentryLineType.entryBytes until bramPortWidth / InCacheMSHR.subentryAlignWidth) {
		updateWrEnPortB(i) := pplDeallocWrite.valid & bramDeallocWrEn & deallocPplWriteReady
	}

	// mem data or victim to BRAM
	val idxToBitmap0 = RegEnable(stash.io.inVictim.bits.lastValidIdx, enable=allocPplWriteReady)
	val idxToBitmap1 = RegEnable(idxToBitmap0 +& 1.U, enable=allocPplRespReady)
	val idxToBitmap2 = RegEnable(UIntToOH(idxToBitmap1), enable=allocPplRespReady)
	val delayedSubWrEn = idxToBitmap2 - 1.U
	stash.io.inVictimSubNo.valid := delayedEvict.last
	stash.io.inVictimSubNo.bits  := delayedStashVictimNo.last
	stash.io.inVictimSubline     := dataMem.douta.asTypeOf(subentryLineType).withNoPadding().entries
	stash.io.inVictimSubMask     := Vec((0 until numEntriesPerLine).map(i => delayedSubWrEn(i)))

	val hitData = MuxLookup(delayedOffset.last, dataMem.douta(reqDataWidth-1, 0), (0 until memDataWidth by reqDataWidth).map(i => (i/reqDataWidth).U -> dataMem.douta(i+reqDataWidth-1, i)))
	val respQueue = Module(new Queue(io.respOut.bits.cloneType, InCacheMSHR.respQueueDepth))
	// val respOutEb = Module(new ElasticBuffer(io.respOut.bits.cloneType))
	// respOutEb.io.in.valid     := delayedCacheHit.last
	// respOutEb.io.in.bits.id   := delayedId.last
	// respOutEb.io.in.bits.data := hitData
	// respOutEb.io.out          <> io.respOut
	respQueue.io.enq.valid     := delayedCacheHit.last
	respQueue.io.enq.bits.id   := delayedId.last
	respQueue.io.enq.bits.data := hitData
	respQueue.io.deq           <> io.respOut

	val respGenQueue = Module(new Queue(io.respGenOut.bits.cloneType, InCacheMSHR.respGenQueueDepth, pipe=true))
	respGenQueue.io.enq.valid             := delayedResp.last & respGenQueue.io.enq.ready
	respGenQueue.io.enq.bits.data         := respDataQueue.io.deq.bits
	respGenQueue.io.enq.bits.entries      := Mux(delayedRespFromStash.last, RegEnable(RegEnable(stash.io.matchingSubline, enable=deallocPplRespReady), enable=deallocPplRespReady), 
													dataMem.doutb.asTypeOf(subentryLineType).withNoPadding().entries)
	respGenQueue.io.enq.bits.lastValidIdx := Mux(delayedIsHitUnread.last, Mux1H(delayedDeallocOH.last, tagMems.map(x => x.doutb.asTypeOf(tagType).lastValidIdx)), delayedLastValidIdx.last)
	respDataQueue.io.deq.ready            := respGenQueue.io.enq.ready & respGenQueue.io.enq.valid
	io.respGenOut <> respGenQueue.io.deq

	val allocatedMSHRCounter = SimultaneousUpDownSaturatingCounter(numMSHRTotal, increment=isPrimaryAlloc, decrement=pplDeallocWrite.valid & deallocWrEn & deallocPplWriteReady)
	/* The number of allocations + kicked out entries in flight must be limited to the number of slots in the stash since, in the worst case,
	* all of them will give rise to a kick out and must be stored in the stash if the pipeline gets filled with deallocations. */
	// val allocsInFlight = Module(new SimultaneousUpDownSaturatingCounter(2* assocMemorySize, 0))
	/* One allocation ceases to be in flight if:
	- it can be put in the hash table without kicking out another entry (hit | ~allFull)
	- an entry from the stash can be put in an hash table without more kickouts
	- an allocation or a kicked out entry in flight is deallocated */
	// val allocsInFlight = SimultaneousUpDownSaturatingCounter(
	// 	assocMemorySize + 1,
	// 	increment=((allocInArbiter.io.in(0).valid & allocInArbiter.io.in(0).ready) | (allocInArbiter.io.in(1).valid & allocInArbiter.io.in(1).ready)) & allocPplReady,
	// 	decrement=(pplAllocMatch.valid & (Vec(tableAllocMatches).asUInt.orR | (~pplAllocMatch.isFromStash & stash.io.hitA) | ~allFull) & allocPplReady1) |
	// 				(pplDeallocMatch.valid & stash.io.hitD & deallocPplReady1)
	// )
	val allocsInPipeline = SimultaneousUpDownSaturatingCounter(
		InCacheMSHR.pplRdLen + 1,
		increment=allocInArbiter.io.out.valid & allocInArbiter.io.out.ready & allocPplStashReady,
		decrement=pplAllocMatch.valid & ~pplAllocMatch.isFromStash & allocPplMatchReady
	)
	val allocsInFlight = Wire(UInt(log2Ceil(assocMemorySize + InCacheMSHR.pplRdLen).W))
	allocsInFlight := allocsInPipeline + stash.io.count
	val stallAllocsStash = Wire(Bool())
	val stallAllocsSubFull = Wire(Bool())
	val stallMshrAlmostFull = Wire(Bool())
	stallAllocsStash    := allocsInFlight >= assocMemorySize.U
	stallAllocsSubFull  := allocsInPipeline + subFullDelayQueue.io.count + subFullDelayQueue.io.enq.valid >= subFullDelayQueue.entries.U
	stallMshrAlmostFull := allocatedMSHRCounter >= (io.maxAllowedMSHRs - MSHRAlmostFullMargin.U)
	stopAllocs := stallMshrAlmostFull | stallAllocsStash | stallAllocsSubFull

	/* Pipeline ready signal */
	val stallTagsBramPortBusy = Wire(Bool())
	stallTagsBramPortBusy := pplDeallocWrite.valid & pplAllocWrite.valid & (allocWrEns.asUInt & matchDeallocWrEn(numHashTables - 1, 0)).asUInt.orR
	// stallTagsBramPortBusy := pplAllocWrite.valid & Vec(deallocWritings.zip(allocWrEns).map(x => x._1 & x._2)).asUInt.orR
	val stallAtSameAddr = Wire(Bool())
	// stallAtSameAddr := Vec(storeToLoads.map(x => x.hazardMatchAMatchD)).asUInt.orR & ((allocPplMatchReady & pplAllocMatch.valid) | pplAllocMatch.isFromStash) & pplDeallocMatch.valid
	// stallAtSameAddr := Vec(storeToLoads.map(x => x.hazardMatchAMatchD)).asUInt.orR & pplDeallocMatch.valid & pplAllocMatch.valid & ~((pplAllocMatch.isFromStash & (getTag(pplDeallocMatch.addr) === getTag(pplAllocMatch.addr))) | stash.io.hazardMatchAMatchD)
	stallAtSameAddr := Vec(storeToLoads.map(x => x.hazardMatchAMatchD)).asUInt.orR & pplDeallocMatch.valid & pplAllocMatch.valid & ~stash.io.hazardMatchAMatchD
	// alloc
	val respCount = respQueue.io.count +& delayedCacheHit.last
	allocPplRespReady := RegNext((respCount < InCacheMSHR.respQueueDepth.U) | (((respCount === InCacheMSHR.respQueueDepth.U) | ~allocPplRespReady) & respQueue.io.deq.fire()), init=true.B)

	allocPplWriteReady := (allocPplRespReady | ~(delayedCacheHit(0) | delayedEvict(0) | delayedEvict(1) | delayedCacheHit(1))) & ~stash.io.hazardEvictAWriteA

	val stallForwardingFromDealloc = Wire(Bool())
	// val hazardForwardingFromDealloc = ((Vec(storeToLoads.map(x => x.hazardWriteDMatchA)).asUInt & matchDeallocWrEn(numHashTables - 1, 0)).orR | stash.io.hazardWriteDMatchA) & pplAllocMatch.valid & pplDeallocWrite.valid
	val hazardForwardingFromDealloc = (Vec(storeToLoads.map(x => x.hazardWriteDMatchA)).asUInt & matchDeallocWrEn(numHashTables - 1, 0)).orR & pplAllocMatch.valid & pplDeallocWrite.valid
	stallForwardingFromDealloc := hazardForwardingFromDealloc & ~deallocPplWriteReady
	allocPplMatchReady := allocPplWriteReady & ~stallForwardingFromDealloc & ~stallAtSameAddr

	allocPplStashReady := allocPplMatchReady & ~stash.io.hazardStashStallA

	// dealloc
	deallocPplRespReady := true.B

	val respGenCount = respGenQueue.io.count +& respDataQueue.io.count + (pplDeallocWrite.valid & deallocWrEn & deallocPplWriteReady)
	val respGenReady = RegNext((respGenCount < InCacheMSHR.respGenQueueDepth.U) | ((respGenCount >= InCacheMSHR.respGenQueueDepth.U) & respGenQueue.io.deq.fire()), init=true.B)
	deallocPplWriteReady := respGenReady & ~stallTagsBramPortBusy & ~stash.io.hazardWriteAWriteD

	val stallForwardingFromAlloc = Wire(Bool())
	// stallForwardingFromAlloc := (Vec(storeToLoads.zip(allocWrEns).map(x => x._1.hazardWriteAMatchD & x._2)).asUInt.orR | stash.io.hazardWriteAMatchD) & pplDeallocMatch.valid & pplAllocWrite.valid & ~allocPplWriteReady
	stallForwardingFromAlloc := Vec(storeToLoads.zip(allocWrEns).map(x => x._1.hazardWriteAMatchD & x._2)).asUInt.orR & pplDeallocMatch.valid & pplAllocWrite.valid & ~allocPplWriteReady
	// val stallReinsertDealloc = Wire(Bool())
	// stallReinsertDealloc := pplAllocMatch.isFromStash & pplDeallocMatch.valid & (getTag(pplDeallocMatch.addr) === getTag(pplAllocMatch.addr))
	deallocPplMatchReady := deallocPplWriteReady & ~stallForwardingFromAlloc & ~stash.io.hazardMatchAMatchD & ~stash.io.hazardMatchStallD //& ~stallReinsertDealloc

	deallocPplStashReady := deallocPplMatchReady

	// invalidate
	val sNormal :: sInvalidating :: Nil = Enum(2)
	val state = RegInit(init=sNormal)

	invalidating := false.B
	invalidationAddressEn := false.B
	switch (state) {
		is (sNormal) {
			when (io.invalidate) {
				state := sInvalidating
			}
		}
		is (sInvalidating) {
			invalidating := true.B
			invalidationAddressEn := true.B
			when (invalidationAddress._2) {
				state := sNormal
			}
		}
	}

	/* Profiling interface */
	if(Profiling.enable) {
		/* The order by which registers are appended to profilingRegisters defines the register map */
		val profilingRegisters = scala.collection.mutable.ListBuffer[UInt]()
		val currentlyUsedMSHR = RegEnable(allocatedMSHRCounter, enable=io.axiProfiling.snapshot)
		val maxUsedMSHR = ProfilingMax(allocatedMSHRCounter, io.axiProfiling)
		val maxUsedSubentry = ProfilingMax(Mux(pplAllocWrite.valid, updatedAllocTag.lastValidIdx, 0.U), io.axiProfiling)
		val collisionCount = ProfilingCounter(evictCounterEnable, io.axiProfiling) // 0
		val cyclesSpentResolvingCollisions = ProfilingCounter(pplAllocMatch.isFromStash & pplAllocMatch.valid & allocPplMatchReady, io.axiProfiling) // 1
		val stallTriggerCount = ProfilingCounter(~allocPplStashReady & ~RegNext(~allocPplStashReady), io.axiProfiling) // 2
		val cyclesSpentStalling = ProfilingCounter(~allocPplStashReady & io.allocIn.valid, io.axiProfiling) // 3
		val acceptedAllocsCount = ProfilingCounter(io.allocIn.valid & io.allocIn.ready, io.axiProfiling) // 4
		val acceptedDeallocsCount = ProfilingCounter(io.deallocIn.valid & io.deallocIn.ready, io.axiProfiling) // 5
		val cyclesAllocsStalled = ProfilingCounter(io.allocIn.valid & ~io.allocIn.ready, io.axiProfiling) // 6
		val cyclesDeallocsStalled = ProfilingCounter(io.deallocIn.valid & ~io.deallocIn.ready, io.axiProfiling) // 7
		val enqueuedMemReqsCount = ProfilingCounter(externalMemoryQueue.io.enq.valid, io.axiProfiling) // 8
		val cacheHitCount = ProfilingCounter(io.respOut.valid & io.respOut.ready, io.axiProfiling) // 9
		val subFullCount = ProfilingCounter(subFullDelayQueue.io.enq.valid, io.axiProfiling) // 9
		val cyclesStallSubFull = ProfilingCounter(io.allocIn.valid & stallAllocsSubFull, io.axiProfiling)
		val deallocsRetryCount = ProfilingCounter(deallocRetrying, io.axiProfiling)
		// This is a very ugly hack to prevent sign extension when converting currValue to signed int, since asSInt does not accept a width as a parameter
		val accumUsedMSHR = ProfilingArbitraryIncrementCounter(Array((true.B -> (allocatedMSHRCounter + 0.U((allocatedMSHRCounter.getWidth+1).W)).asSInt)), io.axiProfiling)
		val pplAndStall = Wire(Vec(16, Bool()))
		pplAndStall(0)  := allocPplStashReady
		pplAndStall(1)  := allocPplMatchReady
		pplAndStall(2)  := allocPplWriteReady
		pplAndStall(3)  := allocPplRespReady
		pplAndStall(4)  := stallAllocsStash
		pplAndStall(5)  := stallAllocsSubFull 
		pplAndStall(6)  := stallMshrAlmostFull
		pplAndStall(7)  := stallForwardingFromDealloc 
		pplAndStall(8)  := hazardForwardingFromDealloc
		pplAndStall(9)  := stallAtSameAddr
		pplAndStall(10) := deallocPplStashReady
		pplAndStall(11) := deallocPplMatchReady
		pplAndStall(12) := deallocPplWriteReady
		pplAndStall(13) := deallocPplRespReady
		pplAndStall(14) := stallTagsBramPortBusy
		pplAndStall(15) := stallForwardingFromAlloc
		// pplAndStall(16) := stallReinsertDealloc
		// pplAndStall(18) := stallHazardWithReinserting
		val pplAndStallSnapshot = RegEnable(pplAndStall.asUInt, enable=io.axiProfiling.snapshot)

		profilingRegisters += currentlyUsedMSHR
		profilingRegisters += maxUsedMSHR
		profilingRegisters += maxUsedSubentry
		profilingRegisters += collisionCount
		profilingRegisters += cyclesSpentResolvingCollisions
		profilingRegisters += stallTriggerCount
		profilingRegisters += cyclesSpentStalling
		profilingRegisters += acceptedAllocsCount
		profilingRegisters += acceptedDeallocsCount
		profilingRegisters += cyclesAllocsStalled
		profilingRegisters += cyclesDeallocsStalled
		profilingRegisters += enqueuedMemReqsCount
		profilingRegisters += cacheHitCount
		profilingRegisters += subFullCount
		profilingRegisters += accumUsedMSHR._1
		profilingRegisters += cyclesStallSubFull
		profilingRegisters += deallocsRetryCount
		profilingRegisters += pplAndStallSnapshot
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
class InCacheMSHRStash(genTag: UniTag, lastTableIdxWidth: Int, genSub: SubentryLineWithNoPadding, numStashEntries: Int) extends Module {
	val stashEntryType = new StashEntry(genTag.tagWidth, genTag.lastValidIdxWidth, lastTableIdxWidth)
	val io = IO(new Bundle {
		// alloc query in
		val lookupTagA       = Flipped(ValidIO(stashEntryType.tag.cloneType))
		val isReInsertingA   = Input(Bool())
		val reinsertValid    = Output(Bool())
		// alloc query result out (with one cycle delay)
		val hitA                  = Output(Bool())
		val matchingLastValidIdxA = Output(stashEntryType.lastValidIdx.cloneType)
		val matchingLastTableIdxA = Output(stashEntryType.lastTableIdx.cloneType)
		// alloc new subentry
		val inLastValidIdx = Flipped(ValidIO(stashEntryType.lastValidIdx.cloneType))
		val inSublineMask  = Input(Vec(genSub.entriesPerLine, Bool()))
		val inSubline      = Input(genSub.entries(0))
		// dealloc query in
		val lookupTagD       = Flipped(ValidIO(stashEntryType.tag.cloneType))
		// dealloc query result out (with one cycle delay)
		val hitD                  = Output(Bool())
		val matchingLastValidIdxD = Output(stashEntryType.lastValidIdx.cloneType)
		// matching subentry line for re-insertion and deallocation
		val matchingSubline       = Output(genSub.entries)
		// victim in
		val inVictim        = Flipped(ValidIO(new StashVictimInIO(genTag.tagWidth, genTag.lastValidIdxWidth, lastTableIdxWidth)))
		val newVictimNo     = Output(UInt(log2Ceil(numStashEntries).W)) // tell data BRAM where the victim's subentries should go
		val inVictimSubNo   = Flipped(ValidIO(UInt(log2Ceil(numStashEntries).W))) // the OneHot code indicates whose subentry line's transaction is done
		val inVictimSubline = Input(genSub.entries)
		val inVictimSubMask = Input(Vec(genSub.entriesPerLine, Bool()))
		// hazard
		// val hazardWriteDMatchA = Output(Bool())
		val hazardMatchStallD  = Output(Bool())
		val hazardStashStallA  = Output(Bool())
		val hazardMatchAMatchD = Output(Bool())
		// val hazardWriteAMatchD = Output(Bool())
		val hazardWriteAWriteD = Output(Bool())
		val hazardEvictAWriteA = Output(Bool())
		// entry counter
		val count = Output(UInt(log2Ceil(numStashEntries + 1).W))
		// re-insert
		val outToPipeline = DecoupledIO(stashEntryType.tag.cloneType)
		// ready signal
		val pipelineReadyA  = Input(Bool())
		val pipelineReady1A = Input(Bool())
		val pipelineReady2A = Input(Bool())
		val pipelineReady3A = Input(Bool())
		val pipelineReadyD  = Input(Bool())
		val pipelineReady1D = Input(Bool())
		val pipelineReady2D = Input(Bool())
	})
	// memory init
	val invalidMemoryEntry = stashEntryType.getInvalidEntry()
	val tags = RegInit(Vec(Seq.fill(numStashEntries)(invalidMemoryEntry)))
	val subentries = Array.fill(genSub.entriesPerLine)(Mem(numStashEntries, genSub.entries(0)))
	// select available slot
	val matches1CycAgoD = Wire(UInt(numStashEntries.W))
	val matches2CycAgoD = Wire(UInt(numStashEntries.W))
	val emptyEntryMap = (0 until numStashEntries).map(i => ~(tags(i).valid | matches1CycAgoD(i) | (matches2CycAgoD(i) & ~io.pipelineReady2D)))
	val emptyEntrySelect = PriorityEncoderOH(emptyEntryMap)
	// match for allocs
	val matchIncomingA  = io.inVictim.valid & (io.inVictim.bits.tag === io.lookupTagA.bits) & io.lookupTagA.valid
	val matchesNewA     = emptyEntrySelect.map(x => x & matchIncomingA) // where the new victim that hits will be stored
	val matchesOldA     = tags.map(x => x.valid & (x.tag === io.lookupTagA.bits) & io.lookupTagA.valid)
	val matchesA        = matchesOldA.zip(matchesNewA).map(x => x._1 | x._2)
	val matches1CycAgoA = RegEnable(Vec(matchesA.map(x => x & io.pipelineReadyA)).asUInt, init=0.U, enable=io.pipelineReady1A)
	// match for deallocs
	val matchIncomingD  = io.inVictim.valid & (io.inVictim.bits.tag === io.lookupTagD.bits) & io.lookupTagD.valid
	val matchesNewD     = emptyEntrySelect.map(x => x & matchIncomingD & io.pipelineReady1A)
	val matchesOldD     = tags.map(x => x.valid & (x.tag === io.lookupTagD.bits) & io.lookupTagD.valid)
	val matchesD        = matchesOldD.zip(matchesNewD).map(x => x._1 | x._2)
	matches1CycAgoD := RegEnable(Vec(matchesD.map(x => x & io.pipelineReadyD)).asUInt, init=0.U, enable=io.pipelineReady1D)
	matches2CycAgoD := RegEnable(matches1CycAgoD & Fill(numStashEntries, io.pipelineReady1D), init=0.U, enable=io.pipelineReady2D)
	val matchNo2CycAgoD = RegEnable(OHToUInt((0 until numStashEntries).map(i => matches1CycAgoD(i))), enable=io.pipelineReady2D)
	// update (2 cycles after querying the stash)
	val matches2CycAgoA = RegEnable(matches1CycAgoA & Fill(numStashEntries, io.pipelineReady1A), init=0.U, enable=io.pipelineReady2A)
	val matchNo2CycAgoA = RegEnable(OHToUInt((0 until numStashEntries).map(i => matches1CycAgoA(i))), enable=io.pipelineReady2A)
	// forwarding the latest idx for matching stage
	val successiveMatchA = (matches1CycAgoA & matches2CycAgoA).orR & io.inLastValidIdx.valid
	val successiveMatchD = (matches1CycAgoD & matches2CycAgoA).orR & io.inLastValidIdx.valid
	// subentry line done transit
	val inVictimSubOH = UIntToOH(io.inVictimSubNo.bits)
	// re-insert
	val outArbiter = Module(new ResettableRRArbiter(io.outToPipeline.bits.cloneType, numStashEntries))
	val reinsertMatchValid = RegEnable(io.reinsertValid & io.pipelineReadyA, enable=io.pipelineReady1A, init=false.B)
	val reinsertWriteValid = RegEnable(reinsertMatchValid & io.pipelineReady1A, enable=io.pipelineReady2A, init=false.B)
	val deallocAfterUndoneReinsert = (0 until numStashEntries).map(i => (matches1CycAgoA(i) & ~io.pipelineReady1A) & (tags(i).tag === io.lookupTagD.bits) & io.lookupTagD.valid)
	val validMap = (0 until numStashEntries).map(i => tags(i).valid | matches1CycAgoA(i) | matches1CycAgoD(i) | (matches2CycAgoD(i) & ~io.pipelineReady2D))
	val count =  PopCount(validMap)

	val forwardedInLastValidIndexA = RegEnable(io.inLastValidIdx.bits, enable=successiveMatchA)
	val forwardedInLastValidIndexD = RegEnable(io.inLastValidIdx.bits, enable=successiveMatchD)
	val forwardedToMatchA = RegEnable(successiveMatchA & ~io.pipelineReady1A, init=false.B, enable=io.pipelineReady1A ^ RegNext(io.pipelineReady1A))
	val forwardedToMatchD = RegEnable(successiveMatchD & ~io.pipelineReady1D, init=false.B, enable=io.pipelineReady1D ^ RegNext(io.pipelineReady1D))

	// for alloc stash stage
	io.reinsertValid := Vec((0 until numStashEntries).map(i => matchesOldA(i) & ~(matchesOldD(i) | matches1CycAgoD(i)))).asUInt.orR & io.isReInsertingA // for stash re-inserting, so if it is a hit, it must be an old entry.
	// for alloc matching stage
	io.matchingLastValidIdxA := MuxCase(Mux1H(matches1CycAgoA, tags.map(x => x.lastValidIdx)), Array(successiveMatchA -> io.inLastValidIdx.bits, forwardedToMatchA -> forwardedInLastValidIndexA))
	io.matchingLastTableIdxA := Mux1H(matches1CycAgoA, tags.map(x => x.lastTableIdx))
	// io.matchingEntryNoA      := OHToUInt((0 until numStashEntries).map(i => matches1CycAgoA(i)))
	io.hitA                  := matches1CycAgoA.orR
	// for dealloc matching stage
	io.matchingLastValidIdxD := MuxCase(Mux1H(matches1CycAgoD, tags.map(x => x.lastValidIdx)), Array(successiveMatchD -> io.inLastValidIdx.bits, forwardedToMatchD -> forwardedInLastValidIndexD))
	// io.matchingEntryNoD      := OHToUInt((0 until numStashEntries).map(i => matches1CycAgoD(i)))
	io.hitD                  := matches1CycAgoD.orR
	// hazard and stall
	io.hazardStashStallA := (Vec(matchesOldA).asUInt & matches1CycAgoD).orR & ~io.isReInsertingA & io.pipelineReady1D
	io.hazardMatchStallD := Vec((0 until numStashEntries).map(i => tags(i).subTransit & matches1CycAgoD(i))).asUInt.orR
	io.hazardMatchAMatchD := (RegEnable(Vec(matchesD.zip(deallocAfterUndoneReinsert).map(x => (x._1 | x._2) & io.pipelineReadyD)).asUInt, enable=io.pipelineReady1D) & matches1CycAgoA).orR
	// io.hazardWriteDMatchA := (matches1CycAgoA & matches2CycAgoD).asUInt.orR
	// io.hazardWriteAMatchD := (matches1CycAgoD & matches2CycAgoA).asUInt.orR
	io.hazardWriteAWriteD := reinsertWriteValid & RegEnable(io.hitD & io.pipelineReady1D, enable=io.pipelineReady2D, init=false.B)
	io.hazardEvictAWriteA := io.inVictimSubNo.valid & io.inLastValidIdx.valid
	// indicate victim idx
	io.newVictimNo := OHToUInt(emptyEntrySelect)
	// entry counter
	io.count := count

	// io.reinsertSublineA.zip(subentries).foreach { case(o, s) => o := s.read(matchNo2CycAgoA) }
	// io.matchingSubline.zip(subentries).foreach { case(o, s) => o := s.read(matchNo2CycAgoD) }
	io.matchingSubline.zip(subentries).foreach { case(o, s) => o := s(Mux(reinsertWriteValid & io.pipelineReady2A, matchNo2CycAgoA, matchNo2CycAgoD)) }

	io.outToPipeline.valid  := outArbiter.io.out.valid
	io.outToPipeline.bits   := outArbiter.io.out.bits
	outArbiter.io.out.ready := io.outToPipeline.ready

	for (i <- 0 until numStashEntries) {
		// dealloc lookup might deallocate the incoming victim
		when ((matchesOldA(i) & ~(matchesOldD(i) | matches1CycAgoD(i)) & io.isReInsertingA & io.pipelineReadyA) | (matches1CycAgoD(i) & io.pipelineReady1D)) {
			tags(i).valid := false.B
		} .elsewhen (io.inVictim.valid & emptyEntrySelect(i) & io.pipelineReady1A) {
			tags(i).valid := true.B
		}
		// re-insert
		when (io.pipelineReadyA & outArbiter.io.in(i).valid & outArbiter.io.in(i).ready) {
			tags(i).inPipeline := true.B // won't re-insert twice
		}
		when (io.pipelineReady1A) {
			// victim
			when (io.inVictim.valid & emptyEntrySelect(i)) {
				tags(i).inPipeline   := false.B
				tags(i).subTransit   := true.B
				tags(i).tag          := io.inVictim.bits.tag
				tags(i).lastValidIdx := io.inVictim.bits.lastValidIdx
				tags(i).lastTableIdx := io.inVictim.bits.lastTableIdx
			}
		}
		when (io.pipelineReady2A) {
			// subentry update
			when (matches2CycAgoA(i) & io.inLastValidIdx.valid & tags(i).valid) {
				tags(i).lastValidIdx := io.inLastValidIdx.bits
			}
		}
		when (io.pipelineReady3A) {
			when (io.inVictimSubNo.valid & inVictimSubOH(i)) {
				tags(i).subTransit := false.B
			}
		}
		// re-insert
		outArbiter.io.in(i).valid := tags(i).valid & ~tags(i).inPipeline & ~matchesOldD(i)
		outArbiter.io.in(i).bits  := tags(i).tag
	}

	for (i <- 0 until genSub.entriesPerLine) {
		val evictedSubIn = io.inVictimSubNo.valid & io.inVictimSubMask(i) & io.pipelineReady3A
		val hitNewSubIn = io.inLastValidIdx.valid & io.inSublineMask(i) & io.pipelineReady2A
		when (evictedSubIn | hitNewSubIn) {
			subentries(i)(Mux(evictedSubIn, io.inVictimSubNo.bits, matchNo2CycAgoA)) := Mux(evictedSubIn, io.inVictimSubline(i), io.inSubline)
		}
	}

}

class StoreToLoadForwardingDualPPL[T <: Data](gen: T = StoreToLoadForwarding.defaultType, addrWidth: Int = StoreToLoadForwarding.defaultAddrWidth) extends Module {
	val io = IO(new Bundle {
		val rdAddrReadA = Input(UInt(addrWidth.W))
		val rdAddrReadD = Input(UInt(addrWidth.W))
		val wrAddrWriteA = Output(UInt(addrWidth.W))
		val wrAddrWriteD = Output(UInt(addrWidth.W))
		val wrAddr = Input(UInt(addrWidth.W))
		val wrEn = Input(Bool())
		val pipelineReadyA = Input(Bool())
		val pipelineReady1A = Input(Bool())
		val pipelineReady2A = Input(Bool())
		val pipelineReadyD = Input(Bool())
		val pipelineReady1D = Input(Bool())
		val pipelineReady2D = Input(Bool())
		val dataInFixedA = Output(gen)
		val dataInFixedD = Output(gen)
		val dataInFromMemA = Input(gen)
		val dataInFromMemD = Input(gen)
		val dataOutToMem = Input(gen)
		val hazardWriteDStashA = Output(Bool())
		val hazardWriteDMatchA = Output(Bool())
		val hazardWriteAMatchD = Output(Bool())
		val hazardMatchAMatchD = Output(Bool())
		val hazardStashAMatchD = Output(Bool())
	})

	// writer's perspective
	val rdAddrStashA = RegEnable(io.rdAddrReadA, enable=io.pipelineReadyA, init=0.U)
	val rdAddrMatchA = RegEnable(rdAddrStashA, enable=io.pipelineReady1A, init=0.U)
	val wrAddrWriteA = RegEnable(rdAddrMatchA, enable=io.pipelineReady2A, init=0.U)
	io.wrAddrWriteA := wrAddrWriteA
	val rdAddrStashD = RegEnable(io.rdAddrReadD, enable=io.pipelineReadyD, init=0.U)
	val rdAddrMatchD = RegEnable(rdAddrStashD, enable=io.pipelineReady1D, init=0.U)
	val wrAddrWriteD = RegEnable(rdAddrMatchD, enable=io.pipelineReady2D, init=0.U)
	io.wrAddrWriteD := wrAddrWriteD

	val forwardToMatchA = (io.wrAddr === rdAddrMatchA) & io.wrEn
	val forwardToStashA = (io.wrAddr === rdAddrStashA) & io.wrEn
	val forwardToReadA  = (io.wrAddr === io.rdAddrReadA) & io.wrEn
	val forwardToMatchD = (io.wrAddr === rdAddrMatchD) & io.wrEn
	val forwardToStashD = (io.wrAddr === rdAddrStashD) & io.wrEn
	val forwardToReadD  = (io.wrAddr === io.rdAddrReadD) & io.wrEn

	// reader's perspective at the match stage
	val pipelineReady1CycDelayA = RegNext(io.pipelineReadyA)
	val pipelineReady1CycDelay1A = RegNext(io.pipelineReady1A)
	val takeForwardingLaterA = RegEnable(forwardToReadA, enable=pipelineReady1CycDelayA | forwardToReadA, init=false.B)
	val takeForwardingA      = pipelineReady1CycDelay1A & RegEnable(forwardToStashA | takeForwardingLaterA, enable=pipelineReady1CycDelayA | forwardToStashA, init=false.B)
	val takeFromWritingA     = forwardToMatchA
	val takeFromStallA       = ~pipelineReady1CycDelay1A & RegEnable(~io.pipelineReady1A & (takeForwardingA | forwardToMatchA), enable=pipelineReady1CycDelay1A | forwardToMatchA, init=false.B)
	val dataForwardedLaterA = RegEnable(io.dataOutToMem, enable=pipelineReady1CycDelayA | forwardToReadA)
	val dataForwardedA      = RegEnable(Mux(forwardToStashA, io.dataOutToMem, dataForwardedLaterA), enable=pipelineReady1CycDelayA | forwardToStashA)
	val dataForwardedStallA = RegEnable(Mux(forwardToMatchA, io.dataOutToMem, dataForwardedA), enable=pipelineReady1CycDelay1A | forwardToMatchA)

	val pipelineReady1CycDelayD = RegNext(io.pipelineReadyD)
	val pipelineReady1CycDelay1D = RegNext(io.pipelineReady1D)
	val takeForwardingLaterD = RegEnable(forwardToReadD, enable=pipelineReady1CycDelayD | forwardToReadD, init=false.B)
	val takeForwardingD      = pipelineReady1CycDelay1D & RegEnable(forwardToStashD | takeForwardingLaterD, enable=pipelineReady1CycDelayD | forwardToStashD, init=false.B)
	val takeFromWritingD     = forwardToMatchD
	val takeFromStallD       = ~pipelineReady1CycDelay1D & RegEnable(~io.pipelineReady1D & (takeForwardingD | forwardToMatchD), enable=pipelineReady1CycDelay1D | forwardToMatchD, init=false.B)
	val dataForwardedLaterD = RegEnable(io.dataOutToMem, enable=pipelineReady1CycDelayD | forwardToReadD)
	val dataForwardedD      = RegEnable(Mux(forwardToStashD, io.dataOutToMem, dataForwardedLaterD), enable=pipelineReady1CycDelayD | forwardToStashD)
	val dataForwardedStallD = RegEnable(Mux(forwardToMatchD, io.dataOutToMem, dataForwardedD), enable=pipelineReady1CycDelay1D | forwardToMatchD)

	io.dataInFixedA := MuxCase(io.dataInFromMemA, Array(takeFromWritingA -> io.dataOutToMem, takeFromStallA -> dataForwardedStallA, takeForwardingA -> dataForwardedA))
	io.dataInFixedD := MuxCase(io.dataInFromMemD, Array(takeFromWritingD -> io.dataOutToMem, takeFromStallD -> dataForwardedStallD, takeForwardingD -> dataForwardedD))

	// possible hazards detection
	io.hazardWriteDStashA := rdAddrStashA === wrAddrWriteD
	io.hazardWriteDMatchA := rdAddrMatchA === wrAddrWriteD
	io.hazardWriteAMatchD := rdAddrMatchD === wrAddrWriteA
	io.hazardMatchAMatchD := rdAddrMatchA === rdAddrMatchD
	io.hazardStashAMatchD := rdAddrStashA === rdAddrMatchD
}
