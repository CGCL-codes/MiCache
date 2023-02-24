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
	val MSHRType = new UniMSHREntryValid(tagWidth, ldBufRowAddrWidth)
	val MSHRLdBufType = MSHRType.ldBufPtr.cloneType
	val entryType = new MSHREntry(tagWidth, ldBufRowAddrWidth)
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
		/* FRQ = free (load buffer) row queue */
		val frqIn = Flipped(DecoupledIO(UInt(ldBufRowAddrWidth.W)))
		/* Output to the load buffer unit */
		val outLdBuf = DecoupledIO(new MSHRToLdBufIO(offsetWidth, idWidth, dataWidth=memDataWidth, rowAddrWidth=ldBufRowAddrWidth))
		/* Raised by the load buffer unit when the FRQ is empty and allocations
		* need to be stalled. */
		val stopAllocFromLdBuf = Input(Bool())
		/* Interface to memory arbiter, with burst requests to be sent to DDR */
		val outMem = DecoupledIO(UInt(tagWidth.W))
		val respOut = DecoupledIO(new DataIdIO(reqDataWidth, idWidth))
		val axiProfiling = new AXI4LiteReadOnlyProfiling(Profiling.dataWidth, Profiling.regAddrWidth)
		/* MSHR will stop accepting allocations when we reach this number of MSHRs. By making this Value
		* configurable at runtime, we can quickly explore the impact of reducing the number of MSHRs without
		* recompiling the design. */
		val maxAllowedMSHRs = Input(UInt(log2Ceil(numMSHRTotal + 1).W))
	})

	val pipelineReady = Wire(Bool())

	// 输入端口有两类输入，即分配与释放，释放的优先级更高
	/* Input logic */
	val inputArbiter = Module(new Arbiter(new AddrDataIdIO(addrWidth, memDataWidth, idWidth), 2))
	val stopAllocs = Wire(Bool())
	val stopDeallocs = Wire(Bool())
	val stallOnlyAllocs = Wire(Bool())

	// connectWithEnable(inputArbiter.io.in(0), io.deallocIn, ~stopDeallocs)
	// inputArbiter.io.in(0).connectWithEnable(io.deallocIn, ~stopDeallocs)

	inputArbiter.io.in(0).valid      := io.deallocIn.valid & ~stopDeallocs
	inputArbiter.io.in(0).bits.addr  := io.deallocIn.bits.addr
	inputArbiter.io.in(0).bits.data  := io.deallocIn.bits.data
	io.deallocIn.ready := inputArbiter.io.in(0).ready & ~stopDeallocs

	inputArbiter.io.in(1).valid      := io.allocIn.valid & ~stopAllocs
	inputArbiter.io.in(1).bits.addr  := io.allocIn.bits.addr
	inputArbiter.io.in(1).bits.id    := io.allocIn.bits.id
	io.allocIn.ready   := inputArbiter.io.in(1).ready & ~stopAllocs

	// 第二层仲裁器，优先级低的输入源即stash的输出，实现了闲时插回的效果
	/* Arbiter between input and stash. Input has higher priority: we try to put back
	* entries in the tables "in the background". */
	val stashArbiter = Module(new Arbiter(new AddrDataIdAllocLdBufPtrLastTableIO(addrWidth, memDataWidth, idWidth, ldBufRowAddrWidth, log2Ceil(numHashTables)), 2))
	/* Queue containing entries that have been kicked out from the hash tables, and that we will try
	* to put back in one of their other possible locations. */
	val stash = Module(new MSHRStash(tagWidth, ldBufRowAddrWidth, assocMemorySize, log2Ceil(numHashTables)))
	stashArbiter.io.in(0).valid := inputArbiter.io.out.valid
	stashArbiter.io.in(0).bits := DontCare
	stashArbiter.io.in(0).bits.addr := inputArbiter.io.out.bits.addr
	stashArbiter.io.in(0).bits.data := inputArbiter.io.out.bits.data
	stashArbiter.io.in(0).bits.id := inputArbiter.io.out.bits.id
	stashArbiter.io.in(0).bits.isAlloc := inputArbiter.io.chosen === 1.U
	// stashArbiter.io.in(0).bits.ldBufPtr := DontCare
	// stashArbiter.io.in(0).bits.lastTableIdx := DontCare
	//stashArbiter.io.in(0).bits.
	inputArbiter.io.out.ready := stashArbiter.io.in(0).ready
	stashArbiter.io.in(1).valid := stash.io.deq.valid
	stashArbiter.io.in(1).bits := DontCare
	stashArbiter.io.in(1).bits.addr := Cat(stash.io.deq.bits.tag, 0.U(offsetWidth.W))
	stashArbiter.io.in(1).bits.isAlloc := true.B
	stashArbiter.io.in(1).bits.ldBufPtr := stash.io.deq.bits.ldBufPtr
	stashArbiter.io.in(1).bits.lastTableIdx := stash.io.deq.bits.lastTableIdx
	stash.io.deq.ready := stashArbiter.io.in(1).ready
	stashArbiter.io.out.ready := pipelineReady

	// 4级流水线：哈希计算，地址读取，等待2周期得到结果
	/* Pipeline */
	/* stashArbiter.io.out -> register -> hash computation -> memory read address and register -> register -> data coming back from memory */
	val delayedRequest = Wire(Vec(MSHR.pipelineLatency, ValidIO(stashArbiter.io.out.bits.cloneType)))
	/* One entry per pipeline stage; whether the entry in that pipeline stage is from stash or not
	* Entries from the stash behave like allocations but they do not generate a new read to memory
	* nor a new allocation to the load buffer if they do not hit. */
	val delayedIsFromStash = Wire(Vec(MSHR.pipelineLatency, Bool()))
	/* One bool per pipeline stage, true if the entry in that pipeline stage comes from the stash
	* and delayedRequest is a corresponding deallocation for it. */
	val deallocPipelineEntries = delayedRequest.zip(delayedIsFromStash).dropRight(1).map(x => (getTag(x._1.bits.addr) === getTag(delayedRequest.last.bits.addr)) & delayedRequest.last.valid & ~delayedRequest.last.bits.isAlloc & x._2)
	val isDelayedAlloc = delayedRequest.last.bits.isAlloc & delayedRequest.last.valid
	val isDelayedDealloc = ~delayedRequest.last.bits.isAlloc & delayedRequest.last.valid
	val isDelayedFromStash = delayedIsFromStash.last
	delayedRequest(0).bits := RegEnable(stashArbiter.io.out.bits, enable=pipelineReady)
	delayedRequest(0).valid := RegEnable(stashArbiter.io.out.valid, enable=pipelineReady, init=false.B)
	delayedIsFromStash(0) := RegEnable(stashArbiter.io.chosen === 1.U, enable=pipelineReady)
	for(i <- 1 until MSHR.pipelineLatency) {
		delayedRequest(i).bits := RegEnable(delayedRequest(i-1).bits, enable=pipelineReady)
		/* Implement in-flight deallocation of entries from the stash if delayedRequest is the corresponding deallocation. */
		delayedRequest(i).valid := RegEnable(delayedRequest(i-1).valid & ~deallocPipelineEntries(i-1), enable=pipelineReady, init=false.B)
		delayedIsFromStash(i) := RegEnable(delayedIsFromStash(i-1), enable=pipelineReady)
	}

	// 根据cache line的tag进行哈希计算，得到MSHR阵列中的地址
	/* Address hashing */
	val r = new scala.util.Random(42)
	val a = (0 until numHashTables).map(_ => r.nextInt(1 << hashMultConstWidth))
	// val b = (0 until numHashTables).map(_ => r.nextInt(1 << hashTableAddrWidth))
	val hashedTags = (0 until numHashTables).map(i => if(sameHashFunction) hash(a(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), getTag(delayedRequest(0).bits.addr)))
	// val hashedTags = (0 until numHashTables).map(i => if(sameHashFunction) hash(a(0), b(0), getTag(delayedRequest(0).bits.addr)) else hash(a(i), b(i), getTag(delayedRequest(0).bits.addr)))
	//a.indices.foreach(i => println(s"a($i)=${a(i)}"))
	/* Uncomment to print out hashing parameters a and b */
	// a.indices.foreach(i => println(s"a($i)=${a(i)} b($i)=${b(i)}"))

	// MSHR读取逻辑
	/* Memories instantiation and interconnection */
	/* Memories are initialized with all zeros, which is fine for us since all the valids will be false */
	val memories = Array.fill(numHashTables)(Module(new XilinxSimpleDualPortNoChangeBRAM(width=memWidth, depth=numMSHRPerHashTable)).io)
	val storeToLoads = Array.fill(numHashTables)(Module(new StoreToLoadForwardingTwoStages(MSHRType, hashTableAddrWidth)).io)
	val memDataOut = Wire(Vec(numHashTables, memType))
	for (i <- 0 until numHashTables) {
		memories(i).clock := clock
		memories(i).reset := reset
		memories(i).addrb := RegEnable(hashedTags(i), enable=pipelineReady)
		storeToLoads(i).rdAddr := RegEnable(hashedTags(i), enable=pipelineReady)

		memories(i).enb := pipelineReady
		memories(i).regceb := pipelineReady
		storeToLoads(i).pipelineReady := pipelineReady
		// storeToLoads(i).dataInFromMem := memType.fromBits(memories(i).doutb)
		memDataOut(i) := memories(i).doutb.asTypeOf(memType)
		storeToLoads(i).dataInFromMem.valid    := memDataOut(i).valid
		storeToLoads(i).dataInFromMem.isMSHR   := memDataOut(i).isMSHR
		storeToLoads(i).dataInFromMem.tag      := memDataOut(i).tag
		storeToLoads(i).dataInFromMem.ldBufPtr := memDataOut(i).data.asTypeOf(MSHRLdBufType)
	}
	// 查找匹配项，包括读取出的BRAM中的项、流水线中的项、stash中的项
	val dataRead = storeToLoads.map(x => x.dataInFixed)
	/* Matching and stash deallocation logic */
	val hashTableMatches = dataRead.map(x => x.valid & x.tag === getTag(delayedRequest.last.bits.addr))
	val cacheMatches = hashTableMatches.zip(dataRead).map(x => x._1 & ~x._2.isMSHR)
	val MSHRMatches = hashTableMatches.zip(dataRead).map(x => x._1 & x._2.isMSHR)
	val pipelineMatches = delayedRequest.zip(delayedIsFromStash).dropRight(1).map(x => (getTag(x._1.bits.addr) === getTag(delayedRequest.last.bits.addr)) & x._1.valid & x._2)
	stash.io.lookupTag.bits := getTag(delayedRequest.last.bits.addr)
	stash.io.lookupTag.valid := delayedRequest.last.valid
	stash.io.deallocMatchingEntry := delayedRequest.last.valid & ~delayedRequest.last.bits.isAlloc
	stash.io.pipelineReady := pipelineReady
	val allMatches = MSHRMatches ++ pipelineMatches ++ Array(stash.io.matchingLdBufPtr.valid)
	val selectedLdBufPtr = Mux1H(allMatches, dataRead.map(x => x.data.ldBufPtr) ++ delayedRequest.dropRight(1).map(x => x.bits.ldBufPtr) ++ Array(stash.io.matchingLdBufPtr.bits))
	val mshrHit = Vec(allMatches).asUInt.orR
	val allValid = Vec(dataRead.map(x => x.valid)).asUInt.andR
	val allIsMSHR = Vec(dataRead.map(x => x.isMSHR)).asUInt.andR /* all = all hash tables */
	val allFull = allValid & allIsMSHR /* all = all hash tables */

	val cacheHit = Vec(cacheMatches).asUInt.orR
    val selectedLine = Mux1H(cacheMatches, memDataOut.map(_.data)) // since no cache line forwarding, just take data from memory port
    val delayedOffset = getOffset(delayedRequest.last.bits.addr)
    val selectedData = MuxLookup(delayedOffset, selectedLine.data(reqDataWidth-1, 0), (0 until memDataWidth by reqDataWidth).map(i => (i/reqDataWidth).U -> selectedLine.data(i+reqDataWidth-1, i)))

	val hit = mshrHit | cacheHit

	val respOutEb = Module(new ElasticBuffer(io.respOut.bits.cloneType))
	respOutEb.io.out <> io.respOut
	respOutEb.io.in.valid := delayedRequest.last.valid & cacheHit
	respOutEb.io.in.bits.id := delayedRequest.last.bits.id
	respOutEb.io.in.bits.data := selectedData

	/* Update logic */
	val updatedData = Wire(memType)
	updatedData.valid := delayedRequest.last.valid
	updatedData.isMSHR := isDelayedAlloc
	updatedData.tag := getTag(delayedRequest.last.bits.addr)
	// updatedData.ldBufPtr := Mux(isDelayedFromStash, delayedRequest.last.bits.ldBufPtr, io.frqIn.bits)
	updatedData.data := Mux(isDelayedDealloc, delayedRequest.last.bits.data, Mux(isDelayedFromStash, delayedRequest.last.bits.ldBufPtr, io.frqIn.bits).asTypeOf(cacheDataType))
	/* When a tag appears for the first time, we allocate an entry in one of the hash tables (HT).
	* To better spread the entries among HTs, we want all HTs to have the same priority; however, we can only choose
	* a hash table for which the entry corresponding to the new tag is free. We use a RRArbiter to implement this functionality, where we
	* do not care about the value to arbitrate and we use ~entry.valid as valid signal for the arbiter. */
	// 轮巡地选择哈希表进行存储（当一个键值在所有表中对应的位置未满时，据此选择存储位置）
	val fakeRRArbiterForSelect = Module(new ResettableRRArbiter(Bool(), numHashTables))
	for(i <- 0 until numHashTables) fakeRRArbiterForSelect.io.in(i).valid := ~dataRead(i).valid | (allValid & ~dataRead(i).isMSHR)
	val hashTableToUpdate = UIntToOH(fakeRRArbiterForSelect.io.chosen).toBools

	/* Eviction logic */
	/* If the entry has been kicked out from HT i, we will try put it in HT i+1 mod HT_count.
	* We use a round-robin policy also for the first eviction: the index of the last hash
	* table from which we evicted is stored in evictTableForFirstAttempt.
	* This round-robin policy is simpler and works better than using an LFSR16. */
	// 使用计数器来索引淘汰位，但若被插入项来自stash，则插入其所在的原表的下一个表中
	val evictCounterEnable = Wire(Bool())
	val evictTableForFirstAttempt = Counter(evictCounterEnable, numHashTables)
	/* To support non-power-of-two number of tables, we need to implement the wrapping logic manually. */
	val evictTableForEntryFromStash = Mux(delayedRequest.last.bits.lastTableIdx === (numHashTables - 1).U, 0.U, delayedRequest.last.bits.lastTableIdx + 1.U)
	val evictTable = Mux(isDelayedFromStash, evictTableForEntryFromStash, evictTableForFirstAttempt._1)
	val evictOH = UIntToOH(evictTable)
	stash.io.enq.bits.tag := Mux1H(evictOH, dataRead.map(x => x.tag))
	stash.io.enq.bits.ldBufPtr := Mux1H(evictOH, dataRead.map(x => x.data.asTypeOf(MSHRLdBufType)))
	stash.io.enq.bits.lastTableIdx := evictTable
	stash.io.enq.valid := isDelayedAlloc & !hit & allFull & pipelineReady
	evictCounterEnable := stash.io.enq.valid

	/* Memory write port */
	for (i <- 0 until numHashTables) {
		memories(i).addra := storeToLoads(i).wrAddr
		memories(i).wea := Mux(isDelayedFromStash,
							delayedRequest.last.valid & Mux(allFull, evictOH(i), hashTableToUpdate(i)),
							(isDelayedAlloc & !hit & Mux(allFull, evictOH(i), hashTableToUpdate(i))) | (isDelayedDealloc & hashTableMatches(i)))
		storeToLoads(i).wrEn := memories(i).wea
		memories(i).dina := updatedData.asUInt
		// storeToLoads(i).dataOutToMem := updatedData
		storeToLoads(i).dataOutToMem.valid    := updatedData.valid
		storeToLoads(i).dataOutToMem.isMSHR   := updatedData.isMSHR
		storeToLoads(i).dataOutToMem.tag      := updatedData.tag
		storeToLoads(i).dataOutToMem.ldBufPtr := updatedData.data.asTypeOf(MSHRLdBufType)
	}
	io.frqIn.ready := isDelayedAlloc & !hit & pipelineReady & ~isDelayedFromStash
	fakeRRArbiterForSelect.io.out.ready := io.frqIn.ready

	/* Another way to work around the cache line forwarding problem: stalling. The deallocation of an MSHR and the next request to the corresponding
	   cache line must set a gap of two cycles to make the updated data visible to the request. Thus we can insert some stalls into the pipeline, or
	   more specifically, the allocation part of the pipeline. So we can just reject the io.allocIn. */
	val allocWaitToUpdateMatch1 = Wire(Bool())
	val allocWaitToUpdateMatch2 = Wire(Bool())
	allocWaitToUpdateMatch1 := (getTag(io.allocIn.bits.addr) === getTag(delayedRequest(0).bits.addr)) & ~delayedRequest(0).bits.isAlloc & delayedRequest(0).valid
	allocWaitToUpdateMatch2 := (getTag(io.allocIn.bits.addr) === getTag(delayedRequest(1).bits.addr)) & ~delayedRequest(1).bits.isAlloc & delayedRequest(1).valid

	// val allocatedMSHRCounter = Module(new SimultaneousUpDownSaturatingCounter(numMSHRTotal, 0))
	// allocatedMSHRCounter.io.load := false.B
	// allocatedMSHRCounter.io.loadValue := DontCare
	// allocatedMSHRCounter.io.increment := io.frqIn.ready
	// allocatedMSHRCounter.io.decrement := isDelayedDealloc & pipelineReady
	val allocatedMSHRCounter = SimultaneousUpDownSaturatingCounter(numMSHRTotal,
		increment=io.frqIn.ready,
		decrement=isDelayedDealloc & pipelineReady)
	/* The number of allocations + kicked out entries in flight must be limited to the number of slots in the stash since, in the worst case,
	* all of them will give rise to a kick out and must be stored in the stash if the pipeline gets filled with deallocations. */
	// val allocsInFlight = Module(new SimultaneousUpDownSaturatingCounter(2* assocMemorySize, 0))
	/* One allocation ceases to be in flight if:
	- it can be put in the hash table without kicking out another entry (hit | ~allFull)
	- an entry from the stash can be put in an hash table without more kickouts
	- an allocation or a kicked out entry in flight is deallocated */
	val allocsInFlight = SimultaneousUpDownSaturatingCounter(assocMemorySize+1,
		increment=inputArbiter.io.in(1).valid & inputArbiter.io.in(1).ready & pipelineReady,
		decrement=(isDelayedAlloc & pipelineReady & (hit | ~allFull)) | (isDelayedDealloc & pipelineReady & (Vec(pipelineMatches).asUInt.orR | stash.io.matchingLdBufPtr.valid))
	)
	// allocsInFlight.io.load := false.B
	// allocsInFlight.io.loadValue := DontCare
	// allocsInFlight.io.increment := inputArbiter.io.in(1).valid & inputArbiter.io.in(1).ready & pipelineReady
	// allocsInFlight.io.decrement := (isDelayedAlloc & pipelineReady & (hit | ~allFull)) | (isDelayedDealloc & pipelineReady & (Vec(pipelineMatches).asUInt.orR | stash.io.matchingLdBufPtr.valid))
	stallOnlyAllocs := allocsInFlight >= assocMemorySize.U
	// val MSHRAlmostFull = allocatedMSHRCounter.io.currValue >= (io.maxAllowedMSHRs - MSHRAlmostFullMargin.U)
	val MSHRAlmostFull = allocatedMSHRCounter >= (io.maxAllowedMSHRs - MSHRAlmostFullMargin.U)

	stopAllocs := MSHRAlmostFull | io.stopAllocFromLdBuf | stallOnlyAllocs | allocWaitToUpdateMatch1 | allocWaitToUpdateMatch2
	// stopAllocs := io.stopAllocFromLdBuf | stallOnlyAllocs
	stopDeallocs := false.B

	/* outLdBuf */
	io.outLdBuf.bits.rowAddr := Mux(hit, selectedLdBufPtr, io.frqIn.bits)
	io.outLdBuf.bits.entry.offset := getOffset(delayedRequest.last.bits.addr)
	io.outLdBuf.bits.entry.id := delayedRequest.last.bits.id
	io.outLdBuf.bits.data := delayedRequest.last.bits.data
	io.outLdBuf.bits.opType.allocateRow := isDelayedAlloc & !hit & ~isDelayedFromStash
	io.outLdBuf.bits.opType.allocateEntry := isDelayedAlloc
	io.outLdBuf.valid := delayedRequest.last.valid & ~isDelayedFromStash & ~cacheHit

	/* Queue and interface to external memory arbiter */
	val externalMemoryQueue = Module(new BRAMQueue(tagWidth, numMSHRTotal))
	externalMemoryQueue.io.deq <> io.outMem
	externalMemoryQueue.io.enq.valid := io.frqIn.ready
	externalMemoryQueue.io.enq.bits := getTag(delayedRequest.last.bits.addr)

	// pipelineReady := io.outLdBuf.ready | ~io.outLdBuf.valid | isDelayedFromStash
	pipelineReady := MuxCase(true.B, Array(respOutEb.io.in.valid -> respOutEb.io.in.ready, io.outLdBuf.valid -> io.outLdBuf.ready)) | isDelayedFromStash

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
		val cyclesSpentResolvingCollisions = ProfilingCounter(isDelayedFromStash & delayedRequest.last.valid & pipelineReady, io.axiProfiling)
		profilingRegisters += cyclesSpentResolvingCollisions
		val stallTriggerCount = ProfilingCounter(stallOnlyAllocs & ~RegNext(stallOnlyAllocs), io.axiProfiling)
		profilingRegisters += stallTriggerCount
		val cyclesSpentStalling = ProfilingCounter(stallOnlyAllocs & io.allocIn.valid, io.axiProfiling)
		profilingRegisters += cyclesSpentStalling
		val acceptedAllocsCount = ProfilingCounter(io.outLdBuf.bits.opType.allocateEntry & io.outLdBuf.valid & io.outLdBuf.ready, io.axiProfiling)
		profilingRegisters += acceptedAllocsCount
		val acceptedDeallocsCount = ProfilingCounter(~io.outLdBuf.bits.opType.allocateEntry & io.outLdBuf.valid & io.outLdBuf.ready, io.axiProfiling)
		profilingRegisters += acceptedDeallocsCount
		val cyclesAllocsStalled = ProfilingCounter(io.allocIn.valid & ~io.allocIn.ready, io.axiProfiling)
		profilingRegisters += cyclesAllocsStalled
		val cyclesDeallocsStalled = ProfilingCounter(io.deallocIn.valid & ~io.deallocIn.ready, io.axiProfiling)
		profilingRegisters += cyclesDeallocsStalled
		val enqueuedMemReqsCount = ProfilingCounter(externalMemoryQueue.io.enq.valid, io.axiProfiling)
		profilingRegisters += enqueuedMemReqsCount
		// val dequeuedMemReqsCount = ProfilingCounter(externalMemoryQueue.io.deq.valid, io.axiProfiling)
		// profilingRegisters += dequeuedMemReqsCount
		val cyclesOutLdBufNotReady = ProfilingCounter(io.outLdBuf.valid & ~io.outLdBuf.ready, io.axiProfiling)
		profilingRegisters += cyclesOutLdBufNotReady
		// This is a very ugly hack to prevent sign extension when converting currValue to signed int, since asSInt does not accept a width as a parameter
		val accumUsedMSHR = ProfilingArbitraryIncrementCounter(Array((true.B -> (allocatedMSHRCounter + 0.U((allocatedMSHRCounter.getWidth+1).W)).asSInt)), io.axiProfiling)
		profilingRegisters += accumUsedMSHR._1
		val cyclesStallAllocFromLdBuf = ProfilingCounter(io.allocIn.valid & io.stopAllocFromLdBuf, io.axiProfiling)
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
