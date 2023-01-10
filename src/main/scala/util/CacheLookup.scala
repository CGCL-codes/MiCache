
class CacheLineNoValid(val tagWidth: Int, val dataWidth: Int) extends Bundle {
	val tag = UInt(tagWidth.W)
	val data = UInt(dataWidth.W)
	override def cloneType = (new CacheLineNoValid(tagWidth, dataWidth)).asInstanceOf[this.type]
}

class Cache(
		addrWidth: Int=Cache.addrWidth,
		idWidth: Int=Cache.idWidth,
		reqDataWidth: Int=Cache.reqDataWidth,
		memDataWidth: Int=Cache.memDataWidth,
		numWays: Int=Cache.numWays,
		sizeBytes: Int=Cache.sizeBytes,
) extends Module {

	val numSets = sizeBytes / (memDataWidth / 8) / numWays
	val offsetWidth = log2Ceil(memDataWidth / reqDataWidth)
	val setWidth = log2Ceil(numSets)
	val tagWidth = addrWidth - offsetWidth - setWidth
	val cacheLineType = new CacheLineNoValid(tagWidth, memDataWidth)
	val memWidth = cacheLineType.getWidth
	def getSet(input: UInt, setWidth: Int): UInt = input(offsetWidth + setWidth - 1, offsetWidth)
	def getTag(input: UInt, tagWidth: Int=tagWidth, addrWidth: Int=addrWidth): UInt = input(addrWidth - 1, addrWidth - tagWidth)
	def getOffset(input: UInt, offsetWidth: Int=offsetWidth): UInt = input(offsetWidth - 1, 0)

	val io = IO(new Bundle {
		val inReq = Flipped(DecoupledIO(new AddrIdIO(addrWidth, idWidth)))
		val outMisses = DecoupledIO(new AddrIdIO(addrWidth, idWidth))
		val outData = DecoupledIO(new DataIdIO(reqDataWidth, idWidth))
		val inData = Flipped(DecoupledIO(new AddrDataIO(addrWidth, memDataWidth)))
		val invalidate = Input(Bool())
		val enabled = Input(Bool())
	})

	val invalidating = Wire(Bool()) /* Enabled while the cache is being invalidated */

	// 输入流水线（3级），BRAM读取延迟设置为2周期
	val pipelineLevel = 3
	val inReqPipeline = Wire(Vec(pipelineLevel, ValidIO(io.inReq.bits.cloneType)))
	val inReqPipelineReady = Wire(Bool())
	inReqPipeline(0).bits  := RegEnable(io.inReq.bits, enable=inReqPipelineReady)
	inReqPipiline(0).valid := RegEnable(io.inReq.valid & ~invalidating, enable=inReqPipelineReady, init=false.B)
	for (i <- 1 until pipelineLevel) {
		inReqPipeline(i).bits  := RegEnable(inReqPipeline(i - 1).bits, enable=inReqPipelineReady)
		inReqPipeline(i).valid := ReaEnable(inReqPipeline(i - 1).valid, enable=inReqPipelineReady, init=false.B)
	}
	io.inReq.ready := inReqPipelineReady & ~invalidating

	// BRAM as memories
	val dataMemories = Array.fill(numWays)(Module(new XilinxSimpleDualPortNoChangeBRAM(width=memWidth, depth=numSets)).io)
	val validMemories = Array.fill(numWays)(Module(new XilinxTrueDualPortReadFirstBRAM(width=1, depth=numSets)).io)
	// 读取的多路cacah line数据和有效位
	val cacheLines = Wire(Vec(numWays, cacheLineType))
	val valids = Wire(Vec(numWays, Bool()))
	/* b channel of memories: serve requests (read-only) */
	for(i <- 0 until numWays) {
		dataMemories(i).clock := clock
		dataMemories(i).reset := reset
		dataMemories(i).addrb := getSet(io.inReq.bits.addr)
		dataMemories(i).enb := inReqPipelineReady
		dataMemories(i).regceb := inReqPipelineReady
		cacheLines(i) := cacheLineType.fromBits(dataMemories(i).doutb)

		validMemories(i).clock := clock
		validMemories(i).reset := reset
		validMemories(i).addrb := getSet(io.inReq.bits.addr)
		validMemories(i).regceb := inReqPipelineReady
		validMemories(i).enb := inReqPipelineReady
		valids(i) := validMemories(i).doutb === 1.U
	}
	// 在第二级流水比较各个tag
	val hits = (0 until numWays).map(
		i => valids(i) & (cacheLines(i).tag === getTag(inReqPipeline(1).bits.addr))
	)

	// 在第三级流水进行命中判断，并选择cacheline
	val hit = RegEnable(Vec(hits).asUInt.orR, enable=inReqPipelineReady, init=false.B) & io.enabled
	val selectedLine = RegEnable(Mux1H(hits, cacheLines), enable=inReqPipelineReady)
	val delayedOffset = getOffset(inReqPipeline(2).bits.addr)
	val selectedData = MuxLookup(
		delayedOffset,
		selectedLine.data(reqDataWidth - 1, 0),
		(0 until memDataWidth by reqDataWidth).map(
			i => (i / reqDataWidth).U -> selectedLine.data(i + reqDataWidth - 1, i)
		)
	)

	// 命中数据出口，在流水线第三级处理
	val outDataEb = Module(new ElasticBuffer(io.outData.bits.cloneType)).io
	outDataEb.out <> io.outData
	outDataEb.in.valid := inReqPipeline(2).valid & hit
	outDataEb.in.bits.id := inReqPipeline(2).bits.id
	outDataEb.in.bits.data := selectedData
	// 未命中信息出口
	val outMissesEb = Module(new ElasticBuffer(io.outMisses.bits.cloneType)).io
	outMissesEb.out <> io.outMisses
	outMissesEb.in.valid := inReqPipeline(2).valid & ~hit
	outMissesEb.in.bits := inReqPipeline(2).bits

	inReqPipelineReady := MuxCase(true.B, Array(outDataEb.in.valid -> outDataEb.in.ready, outMissesEb.in.valid -> outMissesEb.in.ready))


	// cache更新逻辑
	/* inData (cache update) delay network */
	/* First, we read all the sets to figure out which ones are free. Then, we select one that is free. If they are all full, choose (pseudo)randomly. */
	val delayedData = Wire(ValidIO(io.inData.bits.cloneType))
	delayedData.bits := RegNext(RegNext(io.inData.bits))
	delayedData.valid := RegNext(RegNext(io.inData.valid & io.inData.ready, init=false.B), init=false.B)
	/* a channels of data memory, d channel of valid memories: cache update (read/write) and invalidation */
	/* By writing on the d channel, all read channels still see the old data when reading
	 * and writing the same address in the same cycle. */
	val newCacheLine = Wire(cacheLineType)
	newCacheLine.tag := getTag(delayedData.bits.addr, maxTagWidth)
	newCacheLine.data := delayedData.bits.data
	val availableWaySelectionArbiter = Module(new Arbiter(Bool(), numWays))
	val wayToUpdateSelect = Wire(Vec(numWays, Bool()))
	val invalidationAddressEn = Wire(Bool())
	val invalidationAddress = Counter(invalidationAddressEn, numSets)
	for(i <- 0 until numWays) {

		validMemories(i).addra := Mux(invalidating, invalidationAddress._1, getSet(Mux(delayedData.valid, delayedData.bits.addr, io.inData.bits.addr)))
		validMemories(i).regcea := true.B
		validMemories(i).ena := true.B
		validMemories(i).wea := (delayedData.valid & wayToUpdateSelect(i)) | invalidating
		validMemories(i).dina := ~invalidating
		dataMemories(i).addra := getSet(delayedData.bits.addr)
		dataMemories(i).dina := newCacheLine.asUInt
		dataMemories(i).wea := validMemories(i).wea
		availableWaySelectionArbiter.io.in(i).valid := validMemories(i).douta === 0.U
	}
	// 随机选择存储槽位
	val lfsr = LFSR16(delayedData.valid)
	if (numWays > 1)
		wayToUpdateSelect := UIntToOH(Mux(availableWaySelectionArbiter.io.out.valid, availableWaySelectionArbiter.io.chosen, lfsr(log2Ceil(numWays)-1, 0))).toBools
	else
		wayToUpdateSelect(0) := true.B
	// io.inData.ready := true.B // ~delayedData.valid
	io.inData.ready := ~delayedData.valid

	/* Invalidation FSM */
	val sNormal :: sInvalidating :: Nil = Enum(2)
	val state = RegInit(init=sNormal)

	invalidating := false.B
	invalidationAddressEn := false.B
	switch (state) {
		is (sNormal) {
			when(io.invalidate) {
				state := sInvalidating
			}
		}
		is (sInvalidating) {
			invalidating := true.B
			invalidationAddressEn := true.B
			when(invalidationAddress._2) {
				state := sNormal
			}
		}
	}


}
