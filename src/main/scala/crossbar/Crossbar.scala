package fpgamshr.crossbar

import chisel3._
import chisel3.util.{DecoupledIO, log2Ceil, Cat, isPow2, Queue}
import fpgamshr.util.{ElasticBuffer, ResettableRRArbiter}
import fpgamshr.interfaces.{DecAddrIdDecDataIdIO, AddrIdIO, DataIdIO, AXI4FullReadOnly}
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

class Crossbar(
		nInputs:      Int=Crossbar.numberOfInputs,
		nOutputs:     Int=Crossbar.numberOfOutputs,
		addrWidth:    Int=Crossbar.addressWidth,
		reqDataWidth: Int=Crossbar.reqDataWidth,
		memDataWidth: Int=Crossbar.memDataWidth,
		idWidth:      Int=Crossbar.idWidth,
		stripTypePC:  Int=2,
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

	val stripSelWidth = log2Ceil(stripTypePC)
	val channelSelWidth = moduleAddrWidth - stripSelWidth
	require(channelSelWidth > 0)
	require(addrWidth >= channelSelWidth + hbmChannelWidth)

	val io = IO(new Bundle {
		val ins = Vec(nInputs, new DecAddrIdDecDataIdIO(addrWidth, reqDataWidth, idWidth))
		val outs = Flipped(Vec(nOutputs, new DecAddrIdDecDataIdIO(outAddrWidth, reqDataWidth, outIdWidth)))
	})

	/* Address routing system for HBM.
	 *
	 * Input address:
	 * ------------------------------------------------xxxxxx
	 * | tag1 | OUTPUT PORT | tag2 | cacheline offset | sub x
	 * ------------------------------------------------xxxxxx
	 *                      |<----width of HBM channels---->|
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
							(0 until nOutputs).map(j =>
								Cat(io.ins(i).addr.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
									io.ins(i).addr.bits.addr(stripSelWidth + offsetWidth - 1, offsetWidth)) 
								=== addrMasks(j).asUInt(moduleAddrWidth.W))
						} else {
							Vector(true.B)
						}
					val addrRegsIn = Array.fill(nOutputs)(Module(new ElasticBuffer(new AddrIdIO(outAddrWidth, outIdWidth))).io)
					for (j <- 0 until nOutputs) {
						if (addrWidth > moduleAddrWidth + offsetWidth) {
							addrRegsIn(j).in.bits.addr := Cat(io.ins(i).addr.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
																io.ins(i).addr.bits.addr(hbmChannelWidth - 1, stripSelWidth + offsetWidth),
																io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
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
			} else {
				val addrRegsIn = Array.fill(nInputs)(Module(new ElasticBuffer(new AddrIdIO(addrWidth, idWidth))).io)
				for (i <- 0 until nInputs) {
					addrRegsIn(i).in <> io.ins(i).addr
					val match_conditions =
						if (nOutputs > 1) {
							(0 until nOutputs).map(j =>
								Cat(addrRegsIn(i).out.bits.addr(channelSelWidth + hbmChannelWidth - 1, hbmChannelWidth),
									addrRegsIn(i).out.bits.addr(stripSelWidth + offsetWidth - 1, offsetWidth)) 
								=== addrMasks(j).asUInt(moduleAddrWidth.W))
						} else {
							Vector(true.B)
						}
					for (j <- 0 until nOutputs) {
						if (addrWidth > moduleAddrWidth + offsetWidth) {
							addrArbiters(j).in(i).bits.addr := Cat(addrRegsIn(i).out.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
																addrRegsIn(i).out.bits.addr(hbmChannelWidth - 1, stripSelWidth + offsetWidth),
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
								io.ins(i).addr.bits.addr(stripSelWidth + offsetWidth - 1, offsetWidth)) 
							=== addrMasks(j).asUInt(moduleAddrWidth.W))
					} else {
						Vector(true.B)
					}
				for (j <- 0 until nOutputs) {
					if (addrWidth > moduleAddrWidth + offsetWidth) {
						addrArbiters(j).in(i).bits.addr := Cat(io.ins(i).addr.bits.addr(addrWidth - 1, channelSelWidth + hbmChannelWidth),
															io.ins(i).addr.bits.addr(hbmChannelWidth - 1, stripSelWidth + offsetWidth),
															io.ins(i).addr.bits.addr(offsetWidth - 1, 0))
					} else {
						addrArbiters(j).in(i).bits.addr := io.ins(i).addr.bits.addr(offsetWidth - 1, 0)
					}
					/* Append the index of the input port */
					addrArbiters(j).in(i).bits.id   := Cat(i.U, io.ins(i).addr.bits.id)
					addrArbiters(j).in(i).valid     := match_conditions(j) && io.ins(i).addr.valid
				}
				io.ins(i).addr.ready := Vec(addrArbiters.zip(match_conditions).map((x) => x._1.in(i).ready & x._2)).asUInt.orR
			}
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
