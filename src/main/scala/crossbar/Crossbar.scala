package fpgamshr.crossbar

import chisel3._
import chisel3.util.{DecoupledIO, log2Ceil, Cat, isPow2, Queue, LFSR16, ValidIO}
import fpgamshr.util.{ElasticBuffer, ResettableRRArbiter}
import fpgamshr.interfaces.{DecAddrIdDecDataIdIO, AddrIdIO, DataIdIO, AXI4FullReadOnly, BindIdIO}
import scala.math.{pow}
import scala.language.reflectiveCalls

object Crossbar {
	val addressWidth    = 30  /* Word address (word size=reqDataWidth) */
	val reqDataWidth    = 32
	val memDataWidth    = 512
	val idWidth         = 8
	val numberOfInputs  = 16
	val numberOfOutputs = 16
	val maxArbiterPorts = 64
	/** Number of bits that must be added to the ID field to identify the input port */
	val inputIdWidth    = log2Ceil(numberOfInputs)
	/** Width of the offset of the data inside the cacheline */
	val offsetWidth     = log2Ceil(memDataWidth / reqDataWidth)
	/** Number of bits required to identify an output port */
	val moduleAddrWidth = log2Ceil(numberOfOutputs)
	/** Width of the address sent to the request handlers */
	val outAddrWidth    = addressWidth - moduleAddrWidth
	val tagWidth        = addressWidth - moduleAddrWidth - offsetWidth
	val outIdWidth      = idWidth + inputIdWidth
}

class CrossbarBase(
		nInputs:      Int=Crossbar.numberOfInputs,
		nOutputs:     Int=Crossbar.numberOfOutputs,
		addrWidth:    Int=Crossbar.addressWidth,
		reqDataWidth: Int=Crossbar.reqDataWidth,
		memDataWidth: Int=Crossbar.memDataWidth,
		idWidth:      Int=Crossbar.idWidth,
		numCBsPerPC:  Int=2,
		inEb:         Boolean=true
) extends Module {
	require(isPow2(nInputs))
	require(isPow2(nOutputs))
	require(isPow2(reqDataWidth))
	require(isPow2(memDataWidth / reqDataWidth))
	/** Number of bits that must be added to the ID field to identify the input port */
	val inputIdWidth    = log2Ceil(nInputs)
	/** Number of bits required to identify an output port */
	val moduleAddrWidth = log2Ceil(nOutputs)
	/** Width of the address sent to the request handlers */
	val outIdWidth      = idWidth + inputIdWidth
	val outAddrWidth    = addrWidth - moduleAddrWidth
	val bitsPerByte     = 8
    val subWordOffWidth = log2Ceil(reqDataWidth / bitsPerByte)
	val offsetWidth     = log2Ceil(memDataWidth / bitsPerByte) - subWordOffWidth
	val hbmChannelWidth = 28 - subWordOffWidth // i.e. 256MB
	require(addrWidth >= moduleAddrWidth + offsetWidth)

	val cacheSelWidth = log2Ceil(numCBsPerPC)
	val channelSelWidth = moduleAddrWidth - cacheSelWidth
	require(channelSelWidth >= 0)
	require(addrWidth >= channelSelWidth + hbmChannelWidth)

	val io = IO(new Bundle {
		val ins = Vec(nInputs, new DecAddrIdDecDataIdIO(addrWidth, reqDataWidth, idWidth))
		val outs = Flipped(Vec(nOutputs, new DecAddrIdDecDataIdIO(outAddrWidth, reqDataWidth, outIdWidth)))
	})
}

class Crossbar(
		nInputs:      Int=Crossbar.numberOfInputs,
		nOutputs:     Int=Crossbar.numberOfOutputs,
		addrWidth:    Int=Crossbar.addressWidth,
		reqDataWidth: Int=Crossbar.reqDataWidth,
		memDataWidth: Int=Crossbar.memDataWidth,
		idWidth:      Int=Crossbar.idWidth,
		numCBsPerPC:  Int=2,
		inEb:         Boolean=true
) extends CrossbarBase(nInputs, nOutputs, addrWidth, reqDataWidth, memDataWidth, idWidth, numCBsPerPC, inEb) {

	/* Address routing system for HBM.
	 *
	 * Input address:
	 * --------------------------------------------------xxxxxx
	 * | tag1 | pcSel | tag2 | cbSel | cacheline offset | sub x
	 * --------------------------------------------------xxxxxx
	 *                |<--------width of HBM channels-------->|
	 * Output address:
	 * --------------------------------------
	 * | tag(tag1++tag2) | cacheline offset |
	 * --------------------------------------
	 */

	if (nInputs <= Crossbar.maxArbiterPorts) {
		val ebOnArbiterInput = true
		val addrMasks    = (0 until nOutputs)
		val addrArbiters = Array.fill(nOutputs)(Module(new ResettableRRArbiter(new AddrIdIO(outAddrWidth, outIdWidth), nInputs)).io)
		val addrRegsOut  = Array.fill(nOutputs)(Module(new ElasticBuffer(new AddrIdIO(outAddrWidth, outIdWidth))).io)

		if (inEb) {
			if (ebOnArbiterInput) {
				for (i <- 0 until nInputs) {
					val match_conditions =
						if (nOutputs > 1) {
							if (channelSelWidth > 0) {
								if (cacheSelWidth > 0) {
									(0 until nOutputs).map(j =>
										Cat(io.ins(i).addr.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
											io.ins(i).addr.bits.addr(cacheSelWidth + offsetWidth - 1, offsetWidth))
										=== addrMasks(j).asUInt(moduleAddrWidth.W))
								} else {
									(0 until nOutputs).map(j =>
										io.ins(i).addr.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth)
										=== addrMasks(j).asUInt(moduleAddrWidth.W))
								}
							} else {
								(0 until nOutputs).map(j =>
									io.ins(i).addr.bits.addr(cacheSelWidth + offsetWidth - 1, offsetWidth)
									=== addrMasks(j).asUInt(moduleAddrWidth.W))
							}
						} else {
							Vector(true.B)
						}
					val addrRegsIn = Array.fill(nOutputs)(Module(new ElasticBuffer(new AddrIdIO(outAddrWidth, outIdWidth))).io)
					for (j <- 0 until nOutputs) {
						if (addrWidth > moduleAddrWidth + offsetWidth) {
							if (channelSelWidth > 0) {
								if (addrWidth > channelSelWidth + hbmChannelWidth) {
									addrRegsIn(j).in.bits.addr := 
										Cat(io.ins(i).addr.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
											io.ins(i).addr.bits.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
											io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
								} else {	// in this case tag1's width is zero
									addrRegsIn(j).in.bits.addr :=
										Cat(io.ins(i).addr.bits.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
											io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
								}
							} else {
								addrRegsIn(j).in.bits.addr := 
									Cat(io.ins(i).addr.bits.addr(addrWidth - 1, cacheSelWidth + offsetWidth),
										io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
							}
						} else {
							addrRegsIn(j).in.bits.addr := io.ins(i).addr.bits.addr(offsetWidth - 1, 0)
						}
						addrRegsIn(j).in.bits.id   := Cat(i.U, io.ins(i).addr.bits.id)
						addrRegsIn(j).in.valid     := match_conditions(j) && io.ins(i).addr.valid
						addrArbiters(j).in(i) <> addrRegsIn(j).out
					}
					/* If m(i,j) is the match signal between port i and output j
					 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
					 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
					 * then the ready for port i is:
					 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
					 * This is what the obscure one-liner below should compute.
					 */
					io.ins(i).addr.ready := Vec(addrRegsIn.zip(match_conditions).map((x) => x._1.in.ready & x._2)).asUInt.orR
				}
			}/* else {
				val addrRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(new AddrIdIO(addrWidth, idWidth))).io)
				for (i <- 0 until nInputs) {
					addrRegsIn(i).in <> io.ins(i).addr
					val match_conditions =
						if (nOutputs > 1) {
							(0 until nOutputs).map(j =>
								Cat(addrRegsIn(i).out.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
									addrRegsIn(i).out.bits.addr(cacheSelWidth + offsetWidth - 1, offsetWidth)) 
								=== addrMasks(j).asUInt(moduleAddrWidth.W))
						} else {
							Vector(true.B)
						}
					for (j <- 0 until nOutputs) {
						if (addrWidth > moduleAddrWidth + offsetWidth) {
							addrArbiters(j).in(i).bits.addr := Cat(addrRegsIn(i).out.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
																addrRegsIn(i).out.bits.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
																addrRegsIn(i).out.bits.addr(offsetWidth - 1, 0))
						} else {
							addrArbiters(j).in(i).bits.addr := addrRegsIn(i).out.bits.addr(offsetWidth - 1, 0)
						}
						/* Append the index of the input port */
						addrArbiters(j).in(i).bits.id   := Cat(i.U, addrRegsIn(i).out.bits.id)
						addrArbiters(j).in(i).valid     := match_conditions(j) && addrRegsIn(i).out.valid
					}
					addrRegsIn(i).out.ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
				}
			}
		} else { /* no input elastic buffers */
			for (i <- 0 until nInputs) {
				val match_conditions =
					if (nOutputs > 1) {
						(0 until nOutputs).map(j =>
							Cat(io.ins(i).addr.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
								io.ins(i).addr.bits.addr(cacheSelWidth + offsetWidth - 1, offsetWidth)) 
							=== addrMasks(j).asUInt(moduleAddrWidth.W))
					} else {
						Vector(true.B)
					}
				for (j <- 0 until nOutputs) {
					if (addrWidth > moduleAddrWidth + offsetWidth) {
						addrArbiters(j).in(i).bits.addr := Cat(io.ins(i).addr.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
															io.ins(i).addr.bits.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
															io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
					} else {
						addrArbiters(j).in(i).bits.addr := io.ins(i).addr.bits.addr(offsetWidth - 1, 0)
					}
					/* Append the index of the input port */
					addrArbiters(j).in(i).bits.id   := Cat(i.U, io.ins(i).addr.bits.id)
					addrArbiters(j).in(i).valid     := match_conditions(j) && io.ins(i).addr.valid
				}
				io.ins(i).addr.ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
			}*/
		}

		for (j <- 0 until nOutputs) {
			addrRegsOut(j).in <> addrArbiters(j).out
			io.outs(j).addr <> addrRegsOut(j).out
		}

		/* Data routing system. Dual to the address routing system,
		 * with the following transformations:
		 * address              -> data
		 * -------------------------------------------
		 * outputs              -> inputs
		 * inputs               -> outputs
		 * address(output port) -> data.id(input port)
		 *
		 * Input ID:
		 * ---------------------------------
		 * |   INPUT PORT    | original id |
		 * ---------------------------------
		 * |<--log2(numIns)->|<--idWidth-->|
		 * Output ID:
		 * ---------------
		 * | original id |
		 * ---------------
		 * |<--idWidth-->|
		 *
		 */

		val dataMasks    = (0 until nInputs)
		val dataArbiters = Array.fill(nInputs)(Module(new ResettableRRArbiter(new DataIdIO(reqDataWidth, idWidth), nOutputs)).io)
		val dataRegsOut  = Array.fill(nInputs)(Module(new ElasticBuffer(new DataIdIO(reqDataWidth, idWidth))).io)
		if (inEb) {
			if (ebOnArbiterInput) {
				for (j <- 0 until nOutputs) {
					val match_conditions =
						if (nInputs > 1) {
							(0 until nInputs).map(i => io.outs(j).data.bits.id(outIdWidth - 1, idWidth) === dataMasks(i).asUInt(inputIdWidth.W))
						} else {
							Vector(true.B)
						}
					val dataRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(new DataIdIO(reqDataWidth, idWidth))).io)
					for (i <- 0 until nInputs) {
						dataRegsIn(i).in.bits.data := io.outs(j).data.bits.data
						dataRegsIn(i).in.bits.id   := io.outs(j).data.bits.id(idWidth - 1, 0)
						dataRegsIn(i).in.valid     := match_conditions(i) & io.outs(j).data.valid
						dataArbiters(i).in(j) <> dataRegsIn(i).out
					}
					io.outs(j).data.ready := Vec(dataRegsIn.zip(match_conditions).map((x) => x._1.in.ready & x._2)).asUInt.orR
				}
			} else {
				val dataRegsIn = Array.fill(nOutputs)(Module(new ElasticBuffer(new DataIdIO(reqDataWidth, outIdWidth))).io)
				for (j <- 0 until nOutputs) {
					dataRegsIn(j).in <> io.outs(j).data
					val match_conditions =
						if (nInputs > 1) {
							(0 until nInputs).map(i => dataRegsIn(j).out.bits.id(outIdWidth - 1, idWidth) === dataMasks(i).asUInt(inputIdWidth.W))
						} else {
							Vector(true.B)
						}
					for (i <- 0 until nInputs) {
						dataArbiters(i).in(j).bits.data := dataRegsIn(j).out.bits.data
						dataArbiters(i).in(j).bits.id   := dataRegsIn(j).out.bits.id(idWidth - 1, 0)
						dataArbiters(i).in(j).valid     := match_conditions(i) & dataRegsIn(j).out.valid
					}
					dataRegsIn(j).out.ready := Vec(dataArbiters.zip(match_conditions).map((x) => x._1.in(j).ready & x._2)).asUInt.orR
				}
			}
		} else { /* No input elastic buffers */
			for (j <- 0 until nOutputs) {
				val match_conditions =
					if (nInputs > 1) {
						(0 until nInputs).map(i => io.outs(j).data.bits.id(outIdWidth - 1, idWidth)	=== dataMasks(i).asUInt(inputIdWidth.W))
					} else {
						Vector(true.B)
					}
				for (i <- 0 until nInputs) {
					dataArbiters(i).in(j).bits.data := io.outs(j).data.bits.data
					dataArbiters(i).in(j).bits.id   := io.outs(j).data.bits.id(idWidth - 1, 0)
					dataArbiters(i).in(j).valid     := match_conditions(i) & io.outs(j).data.valid
				}
				io.outs(j).data.ready := Vec(dataArbiters.zip(match_conditions).map((x) => x._1.in(j).ready & x._2)).asUInt.orR
			}
		}

		for (i <- 0 until nInputs) {
			dataRegsOut(i).in <> dataArbiters(i).out
			io.ins(i).data <> dataRegsOut(i).out
		}
	} else {
		val maxNumPorts = math.max(nInputs, nOutputs)
		val numLayers = math.ceil(math.log10(maxNumPorts)/math.log10(Crossbar.maxArbiterPorts)).toInt
		/* There must be an iterative/recursive way to handle an arbitrary
		 * number of layers but I couldn't come up with it, so I have to resort
		 * to this ugly ad-hoc solution...
		 */
		require(numLayers <= 3)
		val numXBarsFirstLayer = math.max(1, nInputs / Crossbar.maxArbiterPorts) /* a */
		val numInputsFirstLayer = math.min(Crossbar.maxArbiterPorts, nInputs) /* b */
		val numOutputsFirstLayer = math.min(Crossbar.maxArbiterPorts, nOutputs) /* c */
		val firstXBarLayer = Array.fill(numXBarsFirstLayer)(Module(new Crossbar(nInputs=numInputsFirstLayer, nOutputs=numOutputsFirstLayer, addrWidth=addrWidth, idWidth=idWidth, inEb=true)).io)
		// println(s"numXBarsFirstLayer=$numXBarsFirstLayer")
		// println(s"numInputsFirstLayer=$numInputsFirstLayer, numOutputsFirstLayer=$numOutputsFirstLayer")
		for(iXBar <- 0 until numXBarsFirstLayer) {
			for(iPort <- 0 until Crossbar.maxArbiterPorts) {
				firstXBarLayer(iXBar).ins(iPort) <> io.ins(iXBar * Crossbar.maxArbiterPorts + iPort)
			}
		}
		val numXBarsPerAddrRangeSecondLayer = math.max(1, numXBarsFirstLayer / Crossbar.maxArbiterPorts)
		val numAddrRangesSecondLayer = numOutputsFirstLayer
		val numInputsSecondLayer = math.min(Crossbar.maxArbiterPorts, numXBarsFirstLayer)
		val numOutputsSecondLayer = math.min(Crossbar.maxArbiterPorts, math.max(1, nOutputs/Crossbar.maxArbiterPorts))
		val addrWidthSecondLayer = addrWidth - log2Ceil(numOutputsFirstLayer)
		val idWidthSecondLayer = idWidth + log2Ceil(numInputsFirstLayer)
		val secondXBarLayer = Array.fill(numXBarsPerAddrRangeSecondLayer)(Array.fill(numAddrRangesSecondLayer)(Module(new Crossbar(nInputs=numInputsSecondLayer, nOutputs=numOutputsSecondLayer, addrWidth=addrWidthSecondLayer, idWidth=idWidthSecondLayer, inEb=false)).io))
		// println(s"numXBarsPerAddrRangeSecondLayer=$numXBarsPerAddrRangeSecondLayer, numAddrRangesSecondLayer=$numAddrRangesSecondLayer")
		// println(s"numInputsSecondLayer=$numInputsSecondLayer, numOutputsSecondLayer=$numOutputsSecondLayer")
		// println(s"addrWidthSecondLayer=$addrWidthSecondLayer, idWidthSecondLayer=$idWidthSecondLayer")
		for(iAddrRange <- 0 until numAddrRangesSecondLayer) {
			for(iInputPort <- 0 until numXBarsFirstLayer) {
				//    println(s"iInputPort=$iInputPort, iAddrRange=$iAddrRange")
				val iCrossbarOfThisAddrRange = iInputPort / Crossbar.maxArbiterPorts
				val iOutputPort = iInputPort % Crossbar.maxArbiterPorts
				secondXBarLayer(iCrossbarOfThisAddrRange)(iAddrRange).ins(iOutputPort) <> firstXBarLayer(iInputPort).outs(iAddrRange)
			}
		}
		if(numLayers == 2) {
		   for(iXBar <- 0 until numAddrRangesSecondLayer) { /* c = num of second layer crossbars */
			   for(iPort <- 0 until numOutputsSecondLayer) { /* a */
				   /* If there are only two layers then numXBarsPerAddrRangeSecondLayer = 1 */
				//    println(s"iXBar=$iXBar, iPort=$iPort")
				//    println(s"io.outs($iXBar + $iPort * ${Crossbar.maxArbiterPorts}) <> secondXBarLayer(0)($iXBar).outs($iPort)")
				   io.outs(iXBar + iPort * Crossbar.maxArbiterPorts) <> secondXBarLayer(0)(iXBar).outs(iPort)
			   }
		   }
		} else if (numLayers == 3){
			/* numXBarsPerAddrRangeThirdLayer should always be 1 */
			val numAddrRangesThirdLayer = numOutputsFirstLayer * numOutputsSecondLayer
			val numInputsThirdLayer = math.max(1, numXBarsPerAddrRangeSecondLayer)
			val numOutputsThirdLayer = nOutputs / numAddrRangesThirdLayer
			val addrWidthThirdLayer = addrWidthSecondLayer - log2Ceil(numOutputsSecondLayer)
			val idWidthThirdLayer = idWidthSecondLayer + log2Ceil(numInputsSecondLayer)
			val thirdXBarLayer = Array.fill(numAddrRangesThirdLayer)(Module(new Crossbar(nInputs=numInputsThirdLayer, nOutputs=numOutputsThirdLayer, addrWidth=addrWidthThirdLayer, idWidth=idWidthThirdLayer, inEb=false)).io)
			//    println(s"numAddrRangesThirdLayer=$numAddrRangesThirdLayer")
			//    println(s"numInputsThirdLayer=$numInputsThirdLayer, numOutputsThirdLayer=$numOutputsThirdLayer")
			//    println(s"addrWidthThirdLayer=$addrWidthThirdLayer, idWidthThirdLayer=$idWidthThirdLayer")
			for(iAddrRange <- 0 until numAddrRangesThirdLayer) {
				for(iInputPort <- 0 until numXBarsPerAddrRangeSecondLayer) {
					//    println(s"iInputPort=$iInputPort, iAddrRange=$iAddrRange")
					//    println(s"thirdXBarLayer($iAddrRange).ins($iInputPort % ${Crossbar.maxArbiterPorts}) <> secondXBarLayer($iInputPort)($iAddrRange / $numOutputsSecondLayer).outs($iAddrRange % ${numOutputsSecondLayer})")
					thirdXBarLayer(iAddrRange).ins(iInputPort) <> secondXBarLayer(iInputPort)(iAddrRange / numOutputsSecondLayer).outs(iAddrRange % numOutputsSecondLayer)
				}
			}
			for(i <- 0 until nOutputs) {
				val iXBar = (i % numOutputsFirstLayer) * numOutputsSecondLayer + (i / numOutputsFirstLayer) % numOutputsSecondLayer
				val iPort = i / (numOutputsFirstLayer * numOutputsSecondLayer)
				//    println(s"io.outs($i) <> thirdXBarLayer($iXBar).outs($iPort)")
				io.outs(i) <> thirdXBarLayer(iXBar).outs(iPort)
			}
		} else {
			throw new RuntimeException("numLayers must be <= 3")
		}
	}
}

class OneWayCrossbar(nInputs: Int, nOutputs: Int, addrWidth: Int, bankOffset: Int, inEb: Boolean=true) extends Module {
	//require(isPow2(nInputs))
	require(isPow2(nOutputs))
	val inType = UInt(addrWidth.W)
	val outSelAddrWidth = log2Ceil(nOutputs)
	val outAddrWidth = addrWidth - outSelAddrWidth
	val outType = UInt(outAddrWidth.W)
	val bankAddrWidth = log2Ceil(nOutputs)

	val io = IO(new Bundle {
		val ins = Flipped(Vec(nInputs, DecoupledIO(inType)))
		val outs = Vec(nOutputs, DecoupledIO(outType))
	})

	/* Address routing system. The central bits of the address select the output
	 * port:
	 * Input address:
	 * ------------------------------------------
	 * | tag |    OUTPUT PORT    | bank offset  |
	 * ------------------------------------------
	 *       |<-outSelAddrWidth->|<-bankOffset->|
	 * Output address:
	 * --------------------------
	 * | tag | cacheline offset |
	 * --------------------------
	 *       |<----clWidth----->|
	 *
	 * The outputs of the ResettableRRArbiters are registered with an elastic buffer.
	 * If inEb is set to true,
	 * elastic buffers are also placed on the inputs (either on the module
	 * inputs or on each ResettableRRArbiter input depending on ebOnArbiterInput).
	 */



	 if(nInputs <= Crossbar.maxArbiterPorts) {
		 val ebOnArbiterInput = true  /* Place an input elastic buffer at every
										 ResettableRRArbiter input instead of every module
										 input. Uses much more resources but
										 provides best critical path. */
		val addrMasks = (0 until nOutputs)
		val addrArbiters = Array.fill(nOutputs)(Module(new ResettableRRArbiter(outType, nInputs)).io)
		val addrRegsOut = Array.fill(nOutputs)(Module(new ElasticBuffer(outType)).io)

		if(inEb) {
			if(ebOnArbiterInput) {
				/* Works but uses a lot of resources... */
				for (i <- 0 until nInputs) {
					val match_conditions = if(nOutputs > 1) {
												(0 until nOutputs).map(j => io.ins(i).bits(outSelAddrWidth + bankOffset - 1, bankOffset) === addrMasks(j).asUInt(bankAddrWidth.W))
											} else {
												Vector(true.B)
											}
					val addrRegsIn = Array.fill(nOutputs)(Module(new ElasticBuffer(outType)).io)
					for (j <- 0 until nOutputs) {
						/* Trim away the module address bits (currently, the central ones) */
						addrRegsIn(j).in.bits := Cat(io.ins(i).bits(addrWidth - 1, outSelAddrWidth + bankOffset), io.ins(i).bits(bankOffset - 1, 0))
						/* Append the index of the input port */
						addrRegsIn(j).in.valid := match_conditions(j) && io.ins(i).valid
						addrArbiters(j).in(i) <> addrRegsIn(j).out
					}
					/* If m(i,j) is the match signal between port i and output j
					 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
					 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
					 * then the ready for port i is:
					 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
					 * This is what the obscure one-liner below should compute.
					 */
					io.ins(i).ready := Vec(addrRegsIn.zip(match_conditions).map((x) => x._1.in.ready & x._2)).asUInt.orR
				}
			} else {
				val addrRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(inType)).io)
				// val addrRegsIn = Array.fill(nInputs)(Module(new Queue(new PayloadIdIO(addrWidth, idWidth), 4)).io)
				for (i <- 0 until nInputs) {
					addrRegsIn(i).in <> io.ins(i)
					val match_conditions = if (nOutputs > 1) {
											(0 until nOutputs).map(j => addrRegsIn(i).out.bits(bankOffset - 1, 0) === addrMasks(j).asUInt(bankAddrWidth.W))
											} else {
												Vector(true.B)
											}
					for (j <- 0 until nOutputs) {
						/* Trim away the module address bits (currently, the central ones) */
						addrArbiters(j).in(i).bits := Cat(addrRegsIn(i).out.bits(addrWidth - 1, outSelAddrWidth + bankOffset), addrRegsIn(i).out.bits(bankOffset - 1, 0))
						addrArbiters(j).in(i).valid := match_conditions(j) && addrRegsIn(i).out.valid
					}
					/* If m(i,j) is the match signal between port i and output j
					 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
					 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
					 * then the ready for port i is:
					 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
					 * This is what the obscure one-liner below should compute.
					 */
					addrRegsIn(i).out.ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
				}
			}
		} else { /* no input elastic buffers */
			for (i <- 0 until nInputs) {
				val match_conditions = if(nOutputs > 1) {
											(0 until nOutputs).map(j => io.ins(i).bits(bankOffset - 1, 0) === addrMasks(j).asUInt(bankAddrWidth.W))
										} else {
											Vector(true.B)
										}
				for (j <- 0 until nOutputs) {
					/* Trim away the module address bits (currently, the central ones) */
					addrArbiters(j).in(i).bits := Cat(io.ins(i).bits(addrWidth - 1, outSelAddrWidth + bankOffset), io.ins(i).bits(bankOffset - 1, 0))
					addrArbiters(j).in(i).valid := match_conditions(j) && io.ins(i).valid
				}
				/* If m(i,j) is the match signal between port i and output j
				 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
				 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
				 * then the ready for port i is:
				 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
				 * This is what the obscure one-liner below should compute.
				 */
				io.ins(i).ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
			}
		}

		for (j <- 0 until nOutputs) {
			addrRegsOut(j).in <> addrArbiters(j).out
			io.outs(j) <> addrRegsOut(j).out
		}
	} else {
	   val maxNumPorts = math.max(nInputs, nOutputs)
	   val numLayers = math.ceil(math.log10(maxNumPorts)/math.log10(Crossbar.maxArbiterPorts)).toInt
	   /* There must be an iterative/recursive way to handle an arbitrary
		* number of layers but I couldn't come up with it, so I have to resort
		* to this ugly ad-hoc solution...
		*/
	   require(numLayers <= 3)
	   val numXBarsFirstLayer = math.max(1, nInputs / Crossbar.maxArbiterPorts) /* a */
	   val numInputsFirstLayer = math.min(Crossbar.maxArbiterPorts, nInputs) /* b */
	   val numOutputsFirstLayer = math.min(Crossbar.maxArbiterPorts, nOutputs) /* c */
	   val firstXBarLayer = Array.fill(numXBarsFirstLayer)(Module(new OneWayCrossbar(nInputs=numInputsFirstLayer, nOutputs=numOutputsFirstLayer, addrWidth=addrWidth, bankOffset=bankOffset, inEb=true)).io)
	//    println(s"numXBarsFirstLayer=$numXBarsFirstLayer")
	//    println(s"numInputsFirstLayer=$numInputsFirstLayer, numOutputsFirstLayer=$numOutputsFirstLayer")
	   for(iXBar <- 0 until numXBarsFirstLayer) {
		   for(iPort <- 0 until Crossbar.maxArbiterPorts) {
			   firstXBarLayer(iXBar).ins(iPort) <> io.ins(iXBar * Crossbar.maxArbiterPorts + iPort)
		   }
	   }
	   val numXBarsPerAddrRangeSecondLayer = math.max(1, numXBarsFirstLayer / Crossbar.maxArbiterPorts)
	   val numAddrRangesSecondLayer = numOutputsFirstLayer
	   val numInputsSecondLayer = math.min(Crossbar.maxArbiterPorts, numXBarsFirstLayer)
	   val numOutputsSecondLayer = math.min(Crossbar.maxArbiterPorts, math.max(1, nOutputs/Crossbar.maxArbiterPorts))
	   val addrWidthSecondLayer = addrWidth - log2Ceil(numOutputsFirstLayer)
	   val secondXBarLayer = Array.fill(numXBarsPerAddrRangeSecondLayer)(Array.fill(numAddrRangesSecondLayer)(Module(new OneWayCrossbar(nInputs=numInputsSecondLayer, nOutputs=numOutputsSecondLayer, addrWidth=addrWidthSecondLayer, bankOffset=bankOffset, inEb=false)).io))
	//    println(s"numXBarsPerAddrRangeSecondLayer=$numXBarsPerAddrRangeSecondLayer, numAddrRangesSecondLayer=$numAddrRangesSecondLayer")
	//    println(s"numInputsSecondLayer=$numInputsSecondLayer, numOutputsSecondLayer=$numOutputsSecondLayer")
	//    println(s"addrWidthSecondLayer=$addrWidthSecondLayer, idWidthSecondLayer=$idWidthSecondLayer")
	   for(iAddrRange <- 0 until numAddrRangesSecondLayer) {
		   for(iInputPort <- 0 until numXBarsFirstLayer) {
			//    println(s"iInputPort=$iInputPort, iAddrRange=$iAddrRange")
			   val iCrossbarOfThisAddrRange = iInputPort / Crossbar.maxArbiterPorts
			   val iOutputPort = iInputPort % Crossbar.maxArbiterPorts
			   secondXBarLayer(iCrossbarOfThisAddrRange)(iAddrRange).ins(iOutputPort) <> firstXBarLayer(iInputPort).outs(iAddrRange)
		   }
	   }
	   if(numLayers == 2) {
		   for(iXBar <- 0 until numAddrRangesSecondLayer) { /* c = num of second layer crossbars */
			   for(iPort <- 0 until numOutputsSecondLayer) { /* a */
				   /* If there are only two layers then numXBarsPerAddrRangeSecondLayer = 1 */
				//    println(s"iXBar=$iXBar, iPort=$iPort")
				//    println(s"io.outs($iXBar + $iPort * ${Crossbar.maxArbiterPorts}) <> secondXBarLayer(0)($iXBar).outs($iPort)")
				   io.outs(iXBar + iPort * Crossbar.maxArbiterPorts) <> secondXBarLayer(0)(iXBar).outs(iPort)
			   }
		   }
	   } else if (numLayers == 3){
		   /* numXBarsPerAddrRangeThirdLayer should always be 1 */
		   val numAddrRangesThirdLayer = numOutputsFirstLayer * numOutputsSecondLayer
		   val numInputsThirdLayer = math.max(1, numXBarsPerAddrRangeSecondLayer)
		   val numOutputsThirdLayer = nOutputs / numAddrRangesThirdLayer
		   val addrWidthThirdLayer = addrWidthSecondLayer - log2Ceil(numOutputsSecondLayer)
		   val thirdXBarLayer = Array.fill(numAddrRangesThirdLayer)(Module(new OneWayCrossbar(nInputs=numInputsThirdLayer, nOutputs=numOutputsThirdLayer, addrWidth=addrWidthThirdLayer, bankOffset=bankOffset, inEb=false)).io)
		//    println(s"numAddrRangesThirdLayer=$numAddrRangesThirdLayer")
		//    println(s"numInputsThirdLayer=$numInputsThirdLayer, numOutputsThirdLayer=$numOutputsThirdLayer")
		//    println(s"addrWidthThirdLayer=$addrWidthThirdLayer, idWidthThirdLayer=$idWidthThirdLayer")
		   for(iAddrRange <- 0 until numAddrRangesThirdLayer) {
			   for(iInputPort <- 0 until numXBarsPerAddrRangeSecondLayer) {
				//    println(s"iInputPort=$iInputPort, iAddrRange=$iAddrRange")
				//    println(s"thirdXBarLayer($iAddrRange).ins($iInputPort % ${Crossbar.maxArbiterPorts}) <> secondXBarLayer($iInputPort)($iAddrRange / $numOutputsSecondLayer).outs($iAddrRange % ${numOutputsSecondLayer})")
				   thirdXBarLayer(iAddrRange).ins(iInputPort) <> secondXBarLayer(iInputPort)(iAddrRange / numOutputsSecondLayer).outs(iAddrRange % numOutputsSecondLayer)
			   }
		   }
		   for(i <- 0 until nOutputs) {
			   val iXBar = (i % numOutputsFirstLayer) * numOutputsSecondLayer + (i / numOutputsFirstLayer) % numOutputsSecondLayer
			   val iPort = i / (numOutputsFirstLayer * numOutputsSecondLayer)
			//    println(s"io.outs($i) <> thirdXBarLayer($iXBar).outs($iPort)")
			   io.outs(i) <> thirdXBarLayer(iXBar).outs(iPort)
		   }
	   } else {
		   throw new RuntimeException("numLayers must be <= 3")
	   }
	}
}

class OneWayCrossbarGeneric[S <: Data, T <: Data](inType: S, outType: T, nInputs: Int, nOutputs: Int, getAddr: S => UInt, getOutput: S => T, inEb: Boolean=true) extends Module {
  //require(isPow2(nInputs))
  require(isPow2(nOutputs))
  val outSelAddrWidth = log2Ceil(nOutputs)

  val io = IO(new Bundle {
      val ins = Flipped(Vec(nInputs, DecoupledIO(inType)))
      val outs = Vec(nOutputs, DecoupledIO(outType))
  })

  /* OBSOLETE
   * TODO: update
   * Address routing system. The central bits of the address select the output
   * port:
   * Input address:
   * ------------------------------------------
   * | tag |    OUTPUT PORT    | bank offset  |
   * ------------------------------------------
   *       |<-outSelAddrWidth->|<-bankOffset->|
   * Output address:
   * --------------------------
   * | tag | cacheline offset |
   * --------------------------
   *       |<----clWidth----->|
   *
   * The outputs of the ResettableRRArbiters are registered with an elastic buffer.
   * If inEb is set to true,
   * elastic buffers are also placed on the inputs (either on the module
   * inputs or on each ResettableRRArbiter input depending on ebOnArbiterInput).
   */

    val ebOnArbiterInput = true  /* Place an input elastic buffer at every
                                     ResettableRRArbiter input instead of every module
                                     input. Uses much more resources but
                                     provides best critical path. */
    val addrMasks = (0 until nOutputs)
    val addrArbiters = Array.fill(nOutputs)(Module(new ResettableRRArbiter(outType, nInputs)).io)
    val addrRegsOut = Array.fill(nOutputs)(Module(new ElasticBuffer(outType)).io)

    if(inEb) {
        if(ebOnArbiterInput) {
            /* Works but uses a lot of resources... */
            for (i <- 0 until nInputs) {
                val match_conditions = if(nOutputs > 1) {
                                            (0 until nOutputs).map(j => getAddr(io.ins(i).bits) === addrMasks(j).asUInt(outSelAddrWidth.W))
                                        } else {
                                            Vector(true.B)
                                        }
                val addrRegsIn = Array.fill(nOutputs)(Module(new ElasticBuffer(outType)).io)
                for (j <- 0 until nOutputs) {
                    /* Trim away the module address bits (currently, the central ones) */
                    addrRegsIn(j).in.bits := getOutput(io.ins(i).bits)
                    /* Append the index of the input port */
                    addrRegsIn(j).in.valid := match_conditions(j) && io.ins(i).valid
                    addrArbiters(j).in(i) <> addrRegsIn(j).out
                }
                /* If m(i,j) is the match signal between port i and output j
                 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
                 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
                 * then the ready for port i is:
                 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
                 * This is what the obscure one-liner below should compute.
                 */
                io.ins(i).ready := Vec(addrRegsIn.zip(match_conditions).map((x) => x._1.in.ready & x._2)).asUInt.orR
            }
        } else {
            val addrRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(inType)).io)
            // val addrRegsIn = Array.fill(nInputs)(Module(new Queue(new PayloadIdIO(addrWidth, idWidth), 4)).io)
            for (i <- 0 until nInputs) {
                addrRegsIn(i).in <> io.ins(i)
                val match_conditions = if (nOutputs > 1) {
                                        (0 until nOutputs).map(j => getAddr(addrRegsIn(i).out.bits) === addrMasks(j).asUInt(outSelAddrWidth.W))
                                        } else {
                                            Vector(true.B)
                                        }
                for (j <- 0 until nOutputs) {
                    /* Trim away the module address bits (currently, the central ones) */
                    addrArbiters(j).in(i).bits := getOutput(addrRegsIn(i).out.bits)
                    addrArbiters(j).in(i).valid := match_conditions(j) && addrRegsIn(i).out.valid
                }
                /* If m(i,j) is the match signal between port i and output j
                 * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
                 * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
                 * then the ready for port i is:
                 * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
                 * This is what the obscure one-liner below should compute.
                 */
                addrRegsIn(i).out.ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
            }
        }
    } else { /* no input elastic buffers */
        for (i <- 0 until nInputs) {
            val match_conditions = if(nOutputs > 1) {
                                        (0 until nOutputs).map(j => getAddr(io.ins(i).bits) === addrMasks(j).asUInt(outSelAddrWidth.W))
                                    } else {
                                        Vector(true.B)
                                    }
            for (j <- 0 until nOutputs) {
                /* Trim away the module address bits (currently, the central ones) */
                addrArbiters(j).in(i).bits := getOutput(io.ins(i).bits)
                addrArbiters(j).in(i).valid := match_conditions(j) && io.ins(i).valid
            }
            /* If m(i,j) is the match signal between port i and output j
             * (match_conditions = m(i,:)) and arb_rdy(i,j) is the ready signal of
             * input port i of the arbiter for output j (addrArbiters(j).in(i).ready),
             * then the ready for port i is:
             * m(i,0) & arb_rdy(i,0) | m(i,1) & arb_rdy(i,1) | ... | m(i,nOutputs-1) & arb_rdy(i,nOutputs-1)
             * This is what the obscure one-liner below should compute.
             */
            io.ins(i).ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
        }
    }

    for (j <- 0 until nOutputs) {
        addrRegsOut(j).in <> addrArbiters(j).out
        io.outs(j) <> addrRegsOut(j).out
    }
}

class MuxGeneric[S <: Data, T <: Data](
		inType:     BindIdIO[S],
		rawOutType: T,
		nInputs:    Int,
		getOutput:  S => T,
		srcId:      Boolean=false,
		inEb:       Boolean=true
) extends Module {
	val addrSelWidth = log2Ceil(nInputs)
	val outIdWidth = if (srcId) inType.idWidth + addrSelWidth else 0
	val outType = new BindIdIO(rawOutType, outIdWidth)
	val io = IO(new Bundle {
		val ins = Flipped(Vec(nInputs, DecoupledIO(inType)))
		val out = DecoupledIO(outType)
	})

	val addrArbiter = Module(new ResettableRRArbiter(outType, nInputs)).io
	if (inEb) {
		val addrRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(inType)).io)
		for (i <- 0 until nInputs) {
			addrRegsIn(i).in <> io.ins(i)
			val srcInputId = if (srcId) Cat(i.U(addrSelWidth.W), addrRegsIn(i).out.bits.id) else DontCare
			addrArbiter.in(i).bits.id  := srcInputId
			addrArbiter.in(i).bits.raw := getOutput(addrRegsIn(i).out.bits.raw)
			addrArbiter.in(i).valid    := addrRegsIn(i).out.valid
			addrRegsIn(i).out.ready    := addrArbiter.in(i).ready
		}
	} else {
		for (i <- 0 until nInputs) {
			val srcInputId = if (srcId) Cat(i.U(addrSelWidth.W), io.ins(i).bits.id) else DontCare
			addrArbiter.in(i).bits.id  := srcInputId
			addrArbiter.in(i).bits.raw := getOutput(io.ins(i).bits.raw)
			addrArbiter.in(i).valid    := io.ins(i).valid
			io.ins(i).ready            := addrArbiter.in(i).ready
		}
	}
	val addrRegOut = Module(new ElasticBuffer(outType)).io
	addrRegOut.in <> addrArbiter.out
	io.out <> addrRegOut.out
}

class DemuxGeneric[S <: Data, T <: Data](
		inType:     BindIdIO[S],
		rawOutType: T,
		nOutputs:   Int,
		getAddr:    S => UInt,
		getOutput:  S => T,
		srcId:      Boolean=false,
		inEb:       Boolean=true
) extends Module {
	val addrSelWidth = log2Ceil(nOutputs)
	val outType = new BindIdIO(rawOutType, if (srcId) inType.idWidth else 0) // only one input, append no additional ID bits
	val io = IO(new Bundle {
		val in = Flipped(DecoupledIO(inType))
		val outs = Vec(nOutputs, DecoupledIO(outType))
	})

	val addrMasks = (0 until nOutputs)
	val addrRegsOut = Array.fill(nOutputs)(Module(new ElasticBuffer(outType)).io)

	if (inEb) {
		val addrRegIn = Module(new ElasticBuffer(inType)).io
		addrRegIn.in <> io.in
		val matchs = (0 until nOutputs).map((j) => getAddr(addrRegIn.out.bits.raw) === addrMasks(j).asUInt(addrSelWidth.W))
		for (i <- 0 until nOutputs) {
			val srcInputId = if (srcId) addrRegIn.out.bits.id else DontCare
			addrRegsOut(i).in.bits.id  := srcInputId
			addrRegsOut(i).in.bits.raw := getOutput(addrRegIn.out.bits.raw)
			addrRegsOut(i).in.valid    := matchs(i) && addrRegIn.out.valid
		}
		addrRegIn.out.ready := Vec(addrRegsOut.zip(matchs).map((x) => x._1.in.ready & x._2)).asUInt.orR
	} else {
		val matchs = (0 until nOutputs).map((j) => getAddr(io.in.bits.raw) === addrMasks(j).asUInt(addrSelWidth.W))
		for (i <- 0 until nOutputs) {
			val srcInputId = if (srcId) io.in.bits.id else DontCare
			addrRegsOut(i).in.bits.id  := srcInputId
			addrRegsOut(i).in.bits.raw := getOutput(io.in.bits.raw)
			addrRegsOut(i).in.valid    := matchs(i) && io.in.valid
		}
		io.in.ready := Vec(addrRegsOut.zip(matchs).map((x) => x._1.in.ready & x._2)).asUInt.orR
	}

	addrRegsOut.map(_.out).zip(io.outs).foreach {
		case(x, y) => x <> y
	}
}

class UnitSwitch[S <: Data, T <: Data](
		inType:     BindIdIO[S],
		rawOutType: T,
		numPorts:   Int,
		getAddr:    S => UInt,
		getOutput:  S => T,
		srcId:      Boolean=false,
		inEb:       Boolean=true
) extends Module {
	require(numPorts > 1)	// otherwise a switch makes no sense
	val addrSelWidth = log2Ceil(numPorts)
	val outIdWidth = if (srcId) inType.idWidth + addrSelWidth else 0
	val outType = new BindIdIO(rawOutType, outIdWidth)
	val io = IO(new Bundle {
		val ins = Flipped(Vec(numPorts, DecoupledIO(inType)))
		val outs = Vec(numPorts, DecoupledIO(outType))
	})

	// here addr means port number
	val addrMasks = (0 until numPorts)
	val addrArbiters = Array.fill(numPorts)(Module(new ResettableRRArbiter(outType, numPorts)).io)
	val ebOnArbiterInput = false

	if (inEb) {
		if (ebOnArbiterInput) {
			for (i <- 0 until numPorts) {
				val matchs = (0 until numPorts).map((j) => getAddr(io.ins(i).bits.raw) === addrMasks(j).asUInt(addrSelWidth.W))
				val addrRegsIn = Array.fill(numPorts)(Module(new ElasticBuffer(outType)).io)
				for (j <- 0 until numPorts) {
					val srcInputId = if (srcId) Cat(i.U(addrSelWidth.W), io.ins(i).bits.id) else DontCare
					addrRegsIn(j).in.bits.id  := srcInputId
					addrRegsIn(j).in.bits.raw := getOutput(io.ins(i).bits.raw)
					addrRegsIn(j).in.valid    := matchs(j) && io.ins(i).valid
					addrArbiters(j).in(i) <> addrRegsIn(j).out
				}
				io.ins(i).ready := Vec(addrRegsIn.zip(matchs).map((x) => x._1.in.ready & x._2)).asUInt.orR
			}
		} else {
			val addrRegsIn = Array.fill(numPorts)(Module(new ElasticBuffer(inType)).io)
			for (i <- 0 until numPorts) {
				addrRegsIn(i).in <> io.ins(i)
				val matchs = (0 until numPorts).map((j) => getAddr(addrRegsIn(i).out.bits.raw) === addrMasks(j).asUInt(addrSelWidth.W))
				for (j <- 0 until numPorts) {
					val srcInputId = if (srcId) Cat(i.U(addrSelWidth.W), addrRegsIn(i).out.bits.id) else DontCare
					addrArbiters(j).in(i).bits.id  := srcInputId
					addrArbiters(j).in(i).bits.raw := getOutput(addrRegsIn(i).out.bits.raw)
					addrArbiters(j).in(i).valid    := matchs(j) && addrRegsIn(i).out.valid
				}
				addrRegsIn(i).out.ready := Vec(addrArbiters.zip(matchs).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
			}
		}
	} else {
		for (i <- 0 until numPorts) {
			val matchs = (0 until numPorts).map((j) => getAddr(io.ins(i).bits.raw) === addrMasks(j).asUInt(addrSelWidth.W))
			for (j <- 0 until numPorts) {
				val srcInputId = if (srcId) Cat(i.U(addrSelWidth.W), io.ins(i).bits.id) else DontCare
				addrArbiters(j).in(i).bits.id  := srcInputId
				addrArbiters(j).in(i).bits.raw := getOutput(io.ins(i).bits.raw)
				addrArbiters(j).in(i).valid    := matchs(j) && io.ins(i).valid
			}
			io.ins(i).ready := Vec(addrArbiters.zip(matchs).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
		}
	}

	val addrRegsOut = Array.fill(numPorts)(Module(new ElasticBuffer(outType)).io)
	for (j <- 0 until numPorts) {
		addrRegsOut(j).in <> addrArbiters(j).out
		io.outs(j)        <> addrRegsOut(j).out
	}
}

// Symmetric (i.e. nInputs == nOutputs) switch network
class MultilayerSwitch[S <: Data, T <: Data](
		inType:     BindIdIO[S],
		rawOutType: T,
		numPorts:   Int,
		getAddr:    S => UInt,
		getOutput:  S => T,
		srcId:      Boolean=false,
		inEb:       Boolean=true,
		interInEb:  Boolean=false
) extends Module {
	require(isPow2(numPorts))
	val addrSelWidth = log2Ceil(numPorts)
	val outIdWidth = if (srcId) inType.idWidth + addrSelWidth else 0
	val outType = new BindIdIO(rawOutType, outIdWidth)
	val io = IO(new Bundle {
		val ins = Flipped(Vec(numPorts, DecoupledIO(inType)))
		val outs = Vec(numPorts, DecoupledIO(outType))
	})

	if (numPorts > 1) {
		val numSwitchPorts = if ((log2Ceil(numPorts) % 2) == 0) 4 else 2
		val portSelWidth = log2Ceil(numSwitchPorts)
		val numSwitchPerLayer = numPorts / numSwitchPorts
		val numLayers = math.ceil(math.log10(numPorts)/math.log10(numSwitchPorts)).toInt
		require(numLayers > 0)

		val rawInType = inType.raw.cloneType
		val ioTypes = Array.ofDim[BindIdIO[S]](numLayers) // for convenience
		ioTypes(0) = inType.cloneType
		for (i <- 1 until numLayers) {
			ioTypes(i) = new BindIdIO(rawInType, if (srcId) ioTypes(i - 1).idWidth + portSelWidth else 0)
		}
		val switchsLastLayer = Array.fill(numSwitchPerLayer)( // pick it out because we need to convert the IO types here
			Module(new UnitSwitch(
				ioTypes(numLayers - 1), rawOutType, numSwitchPorts,
				(in: S) => getAddr(in)(numLayers * portSelWidth - 1, (numLayers - 1) * portSelWidth),
				getOutput,
				srcId,
				if (numLayers > 1) interInEb else inEb // place no buffer if not the first layer
			))
		)
		for (i <- 0 until numPorts) { // output
			io.outs(i) <> switchsLastLayer(i % numSwitchPerLayer).io.outs(i / numSwitchPerLayer)
		}
		if (numLayers > 1) {
			val switchs = Array.ofDim[UnitSwitch[S, S]](numLayers - 1, numSwitchPerLayer)
			for (i <- 0 until numLayers - 1) {
				for (j <- 0 until numSwitchPerLayer) {
					switchs(i)(j) = Module(new UnitSwitch(
						ioTypes(i), rawInType, numSwitchPorts,
						(in: S) => getAddr(in)((i + 1) * portSelWidth - 1, i * portSelWidth),
						(out: S) => out,
						srcId,
						if (i == 0) inEb else interInEb // place no buffer if not the first layer
					))
				}
			}
			// inter-layer
			for (layer <- 0 until numLayers - 1) {
				for (i <- 0 until numSwitchPerLayer) {
					val pow1 = pow(numSwitchPorts, layer).toInt
					val pow2 = pow(numSwitchPorts, layer + 1).toInt
					for (p <- 0 until numSwitchPorts) {
						val destSwitch = (i % pow1) + (i - i % pow2) + (p * pow1)
						val destPort = i / pow1 % numSwitchPorts
						if (layer == numLayers - 1 - 1) {
							switchs(layer)(i).io.outs(p) <> switchsLastLayer(destSwitch).io.ins(destPort)
						} else {
							switchs(layer)(i).io.outs(p) <> switchs(layer + 1)(destSwitch).io.ins(destPort)
						}
					}
				}
			}
			for (i <- 0 until numPorts) { // input
				io.ins(i) <> switchs(0)(i / numSwitchPorts).io.ins(i % numSwitchPorts)
			}
		} else {
			for (i <- 0 until numPorts) { // input
				io.ins(i) <> switchsLastLayer(i / numSwitchPorts).io.ins(i % numSwitchPorts)
			}
		}
	} else {
		val srcInputId = if (srcId) io.ins(0).bits.id else DontCare
		io.outs(0).bits.id  := srcInputId
		io.outs(0).bits.raw := getOutput(io.ins(0).bits.raw)
		io.outs(0).valid    := io.ins(0).valid
		io.ins(0).ready     := io.outs(0).ready		
	}
}

/*
Multi-layer crossbar with generic IO types. Routing via a symmetric network
formed by a switch array, and an array of mux/demux (single in/out crossbar)
to handle asymmetric situation, the mlcxb attempts to take up less resources.
Three cases: #in > #out (A), #in < #out (B), #in == #out (C)

A:  X X X   B: \ | | /  C: X X X
   / | | \      X X X      X X X

Customed address matching and input/output convertion are supported. Input
source ID tracing (for routing backward) is also available. Note that the
output type is wrapped in BindIdIO[T].
*/
class MultilayerCrossbarGeneric[S <: Data, T <: Data](
		rawInType:  S,
		rawOutType: T,
		nInputs:    Int,
		nOutputs:   Int,
		getAddr:    S => UInt,
		getOutput:  S => T,
		srcId:      Boolean=false,
		inEb:       Boolean=true,
		interInEb:  Boolean=false
) extends Module {
	val inputIdWidth = log2Ceil(nInputs)
	val inType = new BindIdIO(rawInType, 0)
	val outType = new BindIdIO(rawOutType, if (srcId) inputIdWidth else 0)
	val io = IO(new Bundle {
		val ins = Flipped(Vec(nInputs, DecoupledIO(rawInType)))
		val outs = Vec(nOutputs, DecoupledIO(outType))
	})

	if (nInputs < nOutputs) {
		val numExtendedPort = nOutputs / nInputs
		val extendedSelWidth = log2Ceil(numExtendedPort)
		val outSelWidth = log2Ceil(nOutputs)
		val multiSwitch = Module(new MultilayerSwitch(
			inType, rawInType, nInputs,
			(in: S) => getAddr(in)(outSelWidth - 1, extendedSelWidth),
			(out: S) => out,
			srcId, inEb, interInEb
		))
		val extendedDemuxes = Array.fill(nInputs)(Module(new DemuxGeneric(
			multiSwitch.io.outs(0).bits.cloneType, rawOutType, numExtendedPort,
			(in: S) => getAddr(in)(extendedSelWidth - 1, 0),
			getOutput,
			srcId, interInEb
		)).io)
		multiSwitch.io.ins.zip(io.ins).foreach { case(swIn, ioIn) => {
			swIn.bits.raw := ioIn.bits
			swIn.bits.id  := DontCare
			swIn.valid    := ioIn.valid
			ioIn.ready    := swIn.ready
		}}
		multiSwitch.io.outs.zip(extendedDemuxes.map(_.in)).foreach { case(x, y) => x <> y }
		for (i <- 0 until nOutputs) {
			io.outs(i) <> extendedDemuxes(i / numExtendedPort).outs(i % numExtendedPort)
		}
	} else if (nInputs > nOutputs) {
		val numExtendedPort = nInputs / nOutputs
		val extendedMuxes = Array.fill(nOutputs)(Module(new MuxGeneric(inType, rawInType, numExtendedPort, (out: S) => out, srcId, inEb)).io)
		val multiSwitch = Module(new MultilayerSwitch(extendedMuxes(0).out.bits.cloneType, rawOutType, nOutputs, getAddr, getOutput, srcId, interInEb, interInEb))

		for (i <- 0 until nInputs) {
			val muxId = i / numExtendedPort
			val portId = i % numExtendedPort
			extendedMuxes(muxId).ins(portId).bits.raw := io.ins(i).bits
			extendedMuxes(muxId).ins(portId).bits.id  := DontCare
			extendedMuxes(muxId).ins(portId).valid    := io.ins(i).valid
			io.ins(i).ready                           := extendedMuxes(muxId).ins(portId).ready
		}
		multiSwitch.io.ins.zip(extendedMuxes.map(_.out)).foreach { case(x, y) => x <> y }
		multiSwitch.io.outs.zip(io.outs).foreach { case(x, y) => x <> y }
	} else {
		val multiSwitch = Module(new MultilayerSwitch(inType, rawOutType, nInputs, getAddr, getOutput, srcId, inEb, interInEb))
		for (i <- 0 until nInputs) {
			multiSwitch.io.ins(i).bits.raw := io.ins(i).bits
			multiSwitch.io.ins(i).bits.id  := DontCare
			multiSwitch.io.ins(i).valid    := io.ins(i).valid
			io.ins(i).ready                := multiSwitch.io.ins(i).ready
			io.outs(i) <> multiSwitch.io.outs(i)
		}
	}
}

class MultilayerCrossbar(
		nInputs:      Int=Crossbar.numberOfInputs,
		nOutputs:     Int=Crossbar.numberOfOutputs,
		addrWidth:    Int=Crossbar.addressWidth,
		reqDataWidth: Int=Crossbar.reqDataWidth,
		memDataWidth: Int=Crossbar.memDataWidth,
		idWidth:      Int=Crossbar.idWidth,
		numCBsPerPC:  Int=2,
		inEb:         Boolean=true
) extends CrossbarBase(nInputs, nOutputs, addrWidth, reqDataWidth, memDataWidth, idWidth, numCBsPerPC, inEb) {

	val interInEb = false

	// Request route: address & ID
	val addrCrossbarInputType = io.ins(0).addr.bits.cloneType
	val addrCrossbarOutputType = new AddrIdIO(outAddrWidth, idWidth) // attach the input ID after coming out of the crossbar
	val addrCrossbar = Module(new MultilayerCrossbarGeneric(
		addrCrossbarInputType,
		addrCrossbarOutputType,
		nInputs,
		nOutputs,
		(input: AddrIdIO) => {
			val outAddr = Wire(UInt(moduleAddrWidth.W))
			if (channelSelWidth > 0) {
				if (cacheSelWidth > 0) {
					outAddr := Cat(input.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
									input.addr(cacheSelWidth + offsetWidth - 1, offsetWidth))
				} else {
					outAddr := input.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth)
				}
			} else {
				if (cacheSelWidth > 0) {
					outAddr := input.addr(cacheSelWidth + offsetWidth - 1, offsetWidth)
				} else {
					outAddr := DontCare
				}
			}
			outAddr
		},
		(input: AddrIdIO) => {
			val output = Wire(addrCrossbarOutputType)
			if (addrWidth > moduleAddrWidth + offsetWidth) {
				if (channelSelWidth > 0) {
					if (addrWidth > channelSelWidth + hbmChannelWidth) {
						output.addr := Cat(input.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
											input.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
											input.addr(offsetWidth - 1, 0))
					} else {	// in this case tag1's width is zero
						output.addr := Cat(input.addr(hbmChannelWidth - 1, cacheSelWidth + offsetWidth),
											input.addr(offsetWidth - 1, 0))
					}
				} else {
					output.addr := Cat(input.addr(addrWidth - 1, cacheSelWidth + offsetWidth),
										input.addr(offsetWidth - 1, 0))
				}
			} else {
				output.addr := input.addr(offsetWidth - 1, 0)
			}
			output.id := input.id
			output
		},
		srcId=true,
		inEb,
		interInEb
	)).io

	addrCrossbar.ins.zip(io.ins.map(_.addr)).foreach { case(x, y) => x <> y }
	addrCrossbar.outs.zip(io.outs.map(_.addr)).foreach {
		case(x, y) => {
			y.bits.addr := x.bits.raw.addr
			y.bits.id   := Cat(x.bits.id, x.bits.raw.id) // binding input port ID and AXI ID
			y.valid     := x.valid
			x.ready     := y.ready
		}
	}

	// Response route: data & ID
	val dataCrossbarInputType = io.outs(0).data.bits.cloneType
	val dataCrossbarOutputType = io.ins(0).data.bits.cloneType
	val dataCrossbar = Module(new MultilayerCrossbarGeneric(
		dataCrossbarInputType,
		dataCrossbarOutputType,
		nOutputs,
		nInputs,
		(input: DataIdIO) => input.id(outIdWidth - 1, idWidth), // extract the input source ID
		(input: DataIdIO) => {
			val output = Wire(dataCrossbarOutputType)
			output.data := input.data
			output.id   := input.id(idWidth - 1, 0) // extrace the AXI ID
			output
		},
		srcId=false,
		inEb,
		interInEb
	)).io

	dataCrossbar.ins.zip(io.outs.map(_.data)).foreach { case(x, y) => x <> y }
	dataCrossbar.outs.zip(io.ins.map(_.data)).foreach {
		case(x, y) => {
			y.bits.data := x.bits.raw.data
			y.bits.id   := x.bits.raw.id
			y.valid     := x.valid
			x.ready     := y.ready
		}
	}
}

// Down below is testbench
class RouteEngine(addrWidth: Int, idWidth: Int) extends Module {
	val io = IO(new Bundle{
		val in = Input(UInt(idWidth.W))
		val out = DecoupledIO(new AddrIdIO(addrWidth, idWidth))
	})

	io.out.valid     := true.B
	io.out.bits.id   := io.in
	if (addrWidth > 0) {
		val lfsr = LFSR16(io.out.fire())
		io.out.bits.addr := lfsr(addrWidth - 1, 0)
	} else {
		io.out.bits.addr := DontCare
	}
}

class MultilayerCrossbarTestbench extends Module {
	val numInputs = 1
	val numOutputs = 8
	val inSelWidth = log2Ceil(numInputs)
	val outSelWidth = log2Ceil(numOutputs)
	val ioType = new AddrIdIO(outSelWidth, inSelWidth)
	val io = IO(new Bundle{
		val outs = Vec(numOutputs, ValidIO(new BindIdIO(ioType, numInputs)))
	})

	val res = Array.fill(numInputs)(Module(new RouteEngine(outSelWidth, inSelWidth)))
	val crossbar = Module(new MultilayerCrossbarGeneric(
		ioType,
		ioType,
		numInputs,
		numOutputs,
		(in: AddrIdIO) => in.addr,
		(in: AddrIdIO) => in,
		srcId=true,
		inEb=true,
		interInEb=false
	))

	(0 until numInputs).map((i) => res(i).io.in := i.U)
	res.zip(crossbar.io.ins).foreach {
		case(x, y) => x.io.out <> y
	}
	(0 until numOutputs).map((i) => crossbar.io.outs(i).ready := LFSR16()(i % 16))
	io.outs.zip(crossbar.io.outs).foreach {
		case(x, y) => {
			x.bits.raw.addr := y.bits.raw.addr
			x.bits.raw.id   := y.bits.raw.id
			x.bits.id       := y.bits.id
			// x.bits.id       := DontCare
			x.valid         := y.valid
		}
	}
}

object MultilayerCrossbarTestbenchGen extends App {
	chisel3.Driver.execute(
		args,
		() => new MultilayerCrossbarTestbench()
	)
}
