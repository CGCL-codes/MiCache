package fpgamshr.reqhandler.cuckoo

import chisel3._
import chisel3.util._
import fpgamshr.interfaces._
import fpgamshr.util._
import fpgamshr.profiling._
import chisel3.core.dontTouch
import scala.language.reflectiveCalls

import java.io._ // To generate the BRAM initialization files

class InCacheMSHR(
	addrWidth:            Int=MSHR.addrWidth,
	numMSHRPerHashTable:  Int=MSHR.numMSHRPerHashTable,
	numHashTables:        Int=MSHR.numHashTables,
	idWidth:              Int=MSHR.idWidth,
	memDataWidth:         Int=MSHR.memDataWidth,
	reqDataWidth:         Int=MSHR.reqDataWidth,
	ldBufRowAddrWidth:    Int=MSHR.ldBufRowAddrWidth,
	MSHRAlmostFullMargin: Int=MSHR.MSHRAlmostFullMargin,
	assocMemorySize:      Int=MSHR.assocMemorySize,
	sameHashFunction:     Boolean=false
) extends Module {
	require(isPow2(memDataWidth / reqDataWidth))
	require(isPow2(numMSHRPerHashTable))
	require(memDataWidth >= ldBufRowAddrWidth)
	val offsetWidth = log2Ceil(memDataWidth / reqDataWidth)
	val tagWidth = addrWidth - offsetWidth
	val numMSHRTotal = numMSHRPerHashTable * numHashTables

	val memType = new UniCacheLineValid(tagWidth, memDataWidth)
	val memWidth = memType.getWidth
	val cacheDataType = memType.data.cloneType
	val mshrType = new UniMSHREntryValid(tagWidth, memDataWidth, offsetWidth, idWidth)
	val subentryLineType = mshrType.sub.cloneType
	val numEntriesPerLine = mshrType.sub.entriesPerLine

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
	val stash = Module(new InCacheMSHRStash(mshrType, assocMemorySize, log2Ceil(numHashTables)))
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
	val delayedRequest = Wire(Vec(MSHR.pipelineLatency, ValidIO(stashArbiter.io.out.bits.cloneType)))
	/* One entry per pipeline stage; whether the entry in that pipeline stage is from stash or not
	* Entries from the stash behave like allocations but they do not generate a new read to memory
	* nor a new allocation to the load buffer if they do not hit. */
	val delayedIsFromStash = Wire(Vec(MSHR.pipelineLatency, Bool()))
	val isDelayedFromStash = delayedIsFromStash.last
	val isDelayedValid     = delayedRequest.last.valid & ~(isDelayedFromStash & ~stash.io.hit)
	val isDelayedAlloc     = delayedRequest.last.bits.isAlloc & isDelayedValid
	val isDelayedDealloc   = ~delayedRequest.last.bits.isAlloc & isDelayedValid
	delayedRequest(0).bits  := RegEnable(stashArbiter.io.out.bits, enable=pipelineReady)
	delayedRequest(0).valid := RegEnable(stashArbiter.io.out.valid, enable=pipelineReady, init=false.B)
	delayedIsFromStash(0)   := RegEnable(stashArbiter.io.chosen === 1.U, enable=pipelineReady)
	for (i <- 1 until MSHR.pipelineLatency) {
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
	val memories = Array.fill(numHashTables)(Module(new XilinxSimpleDualPortNoChangeBRAM(width=memWidth, depth=numMSHRPerHashTable)).io)
	val storeToLoad = Module(new SharedStoreToLoadForwardingTwoStages(memType, hashTableAddrWidth, numHashTables)).io
	for (i <- 0 until numHashTables) {
		memories(i).clock := clock
		memories(i).reset := reset
		val rdAddri = RegEnable(hashedTags(i), enable=pipelineReady)
		memories(i).addrb             := rdAddri
		memories(i).enb               := pipelineReady
		memories(i).regceb            := pipelineReady
		storeToLoad.rdAddrs(i)        := rdAddri
		storeToLoad.dataInFromMems(i) := memories(i).doutb.asTypeOf(memType)
	}
	storeToLoad.pipelineReady := pipelineReady

	/* Matching logic */
	val dataRead = storeToLoad.dataInFixeds
	val hashTableMatches = dataRead.map(x => x.valid & x.tag === getTag(delayedRequest.last.bits.addr))
	stash.io.lookupTag.bits       := getTag(delayedRequest.last.bits.addr)
	stash.io.lookupTag.valid      := delayedRequest.last.valid
	stash.io.pipelineReady        := pipelineReady
	stash.io.deallocMatchingEntry := isDelayedDealloc | isDelayedFromStash
	val allMatches = hashTableMatches ++ Array(stash.io.hit)
	val selectedData = Mux1H(allMatches, dataRead.map(x => x.data) ++ Array(stash.io.matchingSubentryLine.asTypeOf(cacheDataType)))

	val cacheMatches = hashTableMatches.zip(dataRead).map(x => x._1 & ~x._2.isMSHR)
	val cacheHit = Vec(cacheMatches).asUInt.orR
    val delayedOffset = getOffset(delayedRequest.last.bits.addr)
    val hitData = MuxLookup(delayedOffset, selectedData(reqDataWidth-1, 0), (0 until memDataWidth by reqDataWidth).map(i => (i/reqDataWidth).U -> selectedData(i+reqDataWidth-1, i)))
	val respOutEb = Module(new ElasticBuffer(io.respOut.bits.cloneType))
	respOutEb.io.in.valid     := isDelayedValid & cacheHit
	respOutEb.io.in.bits.id   := delayedRequest.last.bits.id
	respOutEb.io.in.bits.data := hitData
	respOutEb.io.out          <> io.respOut

	val mshrMatches = hashTableMatches.zip(dataRead).map(x => x._1 & x._2.isMSHR) ++ Array(stash.io.hit)
	val mshrHit = Vec(mshrMatches).asUInt.orR
	val hit = mshrHit | cacheHit

// TODO: critical path too long!
	/* Update logic */
	val updatedData = Wire(memType)
	val updatedSubentryLine = Wire(subentryLineType.cloneType)
	val selectedSubentryLine = selectedData.asTypeOf(subentryLineType)
	updatedData.valid  := true.B
	updatedData.isMSHR := isDelayedAlloc
	updatedData.tag    := getTag(delayedRequest.last.bits.addr)
	updatedData.data   := Mux(isDelayedDealloc, delayedRequest.last.bits.data, Mux(isDelayedFromStash, stash.io.matchingSubentryLine, updatedSubentryLine).asTypeOf(cacheDataType))
	updatedSubentryLine.lastValidIdx := Mux(mshrHit, selectedSubentryLine.lastValidIdx + 1.U, 0.U)
	val updateEntryOH = UIntToOH(updatedSubentryLine.lastValidIdx)
	for (i <- 0 until numEntriesPerLine) {
		updatedSubentryLine.entries(i).offset := Mux(updateEntryOH(i), getOffset(delayedRequest.last.bits.addr), selectedSubentryLine.entries(i).offset)
		updatedSubentryLine.entries(i).id     := Mux(updateEntryOH(i), delayedRequest.last.bits.id, selectedSubentryLine.entries(i).id)
	}

	val subentryAlmostFullStall = Module(new FullSubentryTagArray(tagWidth, MSHR.pipelineLatency)).io
	subentryAlmostFullStall.in.valid           := (isDelayedAlloc & !cacheHit & (numEntriesPerLine.U - updatedSubentryLine.lastValidIdx <= MSHR.pipelineLatency.U)) | isDelayedDealloc
	subentryAlmostFullStall.in.bits            := getTag(delayedRequest.last.bits.addr)
	subentryAlmostFullStall.deallocMatchingTag := isDelayedDealloc
	subentryAlmostFullStall.pipelineReady      := pipelineReady

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
	stash.io.in.bits.tag          := Mux1H(evictOH, dataRead.map(x => x.tag))
	stash.io.in.bits.sub          := Mux(stash.io.hit & ~isDelayedFromStash, updatedSubentryLine, Mux1H(evictOH, dataRead.map(x => x.data.asTypeOf(subentryLineType))))
	stash.io.in.bits.lastTableIdx := evictTable
	stash.io.in.valid             := (((isDelayedAlloc & !hit) | (isDelayedFromStash & stash.io.hit)) & allFull) | (isDelayedAlloc & ~isDelayedFromStash & stash.io.hit)
	evictCounterEnable            := ((isDelayedAlloc & !hit) | (isDelayedFromStash & stash.io.hit)) & allFull & pipelineReady

	/* Memory write port */
	for (i <- 0 until numHashTables) {
		memories(i).addra    := storeToLoad.wrAddrs(i)
		memories(i).wea      := isDelayedValid & (mshrMatches(i) | ((!hit | isDelayedFromStash) & Mux(allFull, evictOH(i), hashTableToUpdate(i))))
		memories(i).dina     := updatedData.asUInt
		storeToLoad.wrEns(i) := memories(i).wea
	}
	storeToLoad.dataOutToMem := updatedData
	val newAllocDone = isDelayedAlloc & !hit & ~isDelayedFromStash & pipelineReady
	fakeRRArbiterForSelect.io.out.ready := newAllocDone

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
	stopDeallocs := false.B

	/* Queue and interface to external memory arbiter */
	val externalMemoryQueue = Module(new BRAMQueue(tagWidth, numMSHRTotal))
	externalMemoryQueue.io.deq <> io.outMem
	externalMemoryQueue.io.enq.valid := newAllocDone
	externalMemoryQueue.io.enq.bits := getTag(delayedRequest.last.bits.addr)

	io.respGenOut.valid             := isDelayedDealloc & mshrHit
	io.respGenOut.bits.data         := delayedRequest.last.bits.data
	io.respGenOut.bits.entries      := selectedSubentryLine.entries
	io.respGenOut.bits.lastValidIdx := selectedSubentryLine.lastValidIdx

	pipelineReady := MuxCase(true.B, Array(respOutEb.io.in.valid -> respOutEb.io.in.ready, io.respGenOut.valid -> io.respGenOut.ready)) | isDelayedFromStash

	/* Profiling interface */
	if(Profiling.enable) {
		/* The order by which registers are appended to profilingRegisters defines the register map */
		val profilingRegisters = scala.collection.mutable.ListBuffer[UInt]()
		val currentlyUsedMSHR = RegEnable(allocatedMSHRCounter, enable=io.axiProfiling.snapshot)
		profilingRegisters += currentlyUsedMSHR
		val maxUsedMSHR = ProfilingMax(allocatedMSHRCounter, io.axiProfiling)
		profilingRegisters += maxUsedMSHR
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
   */
class InCacheMSHRStash(entryType: UniMSHREntryValid, numStashEntries: Int, lastTableIdxWidth: Int) extends Module {
	val io = IO(new Bundle {
		val in = Flipped(ValidIO(new StashEntryInIO(entryType, lastTableIdxWidth)))
		val outToPipeline = DecoupledIO(entryType.tag.cloneType)
		val pipelineReady = Input(Bool())
		val lookupTag = Flipped(ValidIO(entryType.tag.cloneType))
		val hit = Output(Bool())
		val matchingSubentryLine = Output(entryType.sub.cloneType)
		val matchingLastTableIdx = Output(UInt(lastTableIdxWidth.W))
		val deallocMatchingEntry = Input(Bool())
	})

	val stashEntryType = new StashEntry(entryType, lastTableIdxWidth)
	val invalidMemoryEntry = stashEntryType.getInvalidEntry()
	val memory = RegInit(Vec(Seq.fill(numStashEntries)(invalidMemoryEntry)))

	val emptyEntrySelect = PriorityEncoderOH(memory.map(x => ~x.valid))
	val matches = memory.map(x => x.valid & (x.tag === io.lookupTag.bits) & io.lookupTag.valid)
	val hit = Vec(matches).asUInt.orR
	val outArbiter = Module(new ResettableRRArbiter(io.outToPipeline.bits.cloneType, numStashEntries))
	for (i <- 0 until numStashEntries) {
		when (io.pipelineReady) {
			when (io.in.valid) { // writing (updating, adding, or replacing on an alloc from stash that causing an eviction)
				when (matches(i) | (~hit & emptyEntrySelect(i))) { // updating or adding
					memory(i).sub := io.in.bits.sub
				}
				when ((matches(i) & io.deallocMatchingEntry) | (~hit & emptyEntrySelect(i))) { // replacing or adding
					memory(i).valid        := true.B
					memory(i).inPipeline   := false.B
					memory(i).tag          := io.in.bits.tag
					memory(i).lastTableIdx := io.in.bits.lastTableIdx
				}
			} .elsewhen (matches(i) & io.deallocMatchingEntry) { // dealloc or alloc from stash written into BRAM
				memory(i).valid := false.B
			}
			when (outArbiter.io.in(i).valid & outArbiter.io.in(i).ready) { // won't re-insert twice
				memory(i).inPipeline := true.B
			}
		}
		outArbiter.io.in(i).valid := memory(i).valid & ~memory(i).inPipeline & ~(matches(i) & io.deallocMatchingEntry)
		outArbiter.io.in(i).bits  := memory(i).tag
	}
	outArbiter.io.out <> io.outToPipeline
	io.matchingSubentryLine := Mux1H(matches, memory.map(x => x.sub))
	io.matchingLastTableIdx := Mux1H(matches, memory.map(x => x.lastTableIdx))
	io.hit := hit
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
