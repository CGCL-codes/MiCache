package fpgamshr.extmemarbiter

import chisel3._
import chisel3.util._
import fpgamshr.util._
import fpgamshr.interfaces._
import scala.language.reflectiveCalls

object ExternalMemoryArbiter {
	val memAddrWidth   = 32
	val reqAddrWidth   = 32
	val memAddrOffset  = 0x00000000
	val memDataWidth   = 512
	val memIdWidth     = 5
	val numReqHandlers = 4
	val bramLatency    = 2
}

object InOrderExternalMemoryArbiter {
	val maxInFlightRequests = 128
}

class MemoryInterfaceManager(
		tagWidth:            Int,
		offsetWidth:         Int,
		memAddrWidth:        Int,
		memDataWidth:        Int,
		memIdWidth:          Int,
		maxInFlightRequests: Int,
		memAddrOffset:       Long
) extends Module {
	val io = IO(new Bundle {
		val enq        = Flipped(DecoupledIO(UInt(tagWidth.W)))
		val outMemAddr = DecoupledIO(UInt(memAddrWidth.W))
		val inMemData  = Flipped(DecoupledIO(UInt(memDataWidth.W)))
		val deq        = DecoupledIO(new AddrDataIO(tagWidth, memDataWidth))
	})

	val fullAddress       = Cat(io.enq.bits, 0.U(offsetWidth.W)) + memAddrOffset.U
	val inFlightAddresses = Module(new BRAMQueue(tagWidth, maxInFlightRequests))
	inFlightAddresses.io.enq.valid := io.enq.valid & io.outMemAddr.ready
	inFlightAddresses.io.enq.bits  := io.enq.bits
	io.enq.ready        := inFlightAddresses.io.enq.ready & io.outMemAddr.ready
	io.outMemAddr.valid := io.enq.valid & inFlightAddresses.io.enq.ready
	io.outMemAddr.bits  := fullAddress

	val rChannelEb = Module(new ElasticBuffer(UInt(memDataWidth.W)))
	rChannelEb.io.in.valid := io.inMemData.valid
	rChannelEb.io.in.bits  := io.inMemData.bits
	io.inMemData.ready     := rChannelEb.io.in.ready
	io.deq.valid     := rChannelEb.io.out.valid
	io.deq.bits.data := rChannelEb.io.out.bits
	io.deq.bits.addr := inFlightAddresses.io.deq.bits
	inFlightAddresses.io.deq.ready := rChannelEb.io.out.valid & io.deq.ready
	rChannelEb.io.out.ready        := io.deq.ready & inFlightAddresses.io.deq.valid
}

class ExternalMemoryArbiterBase(
		reqAddrWidth:   Int,
		memAddrWidth:   Int,
		memDataWidth:   Int,
		memIdWidth:     Int,
		numReqHandlers: Int,
		numMemoryPorts: Int
) extends Module {
	require(isPow2(memDataWidth))
	require(isPow2(numMemoryPorts))
	val bitsPerByte      = 8
	val hbmChannelWidth  = 28 // i.e. 256MB
	val offsetWidth      = log2Ceil(memDataWidth / bitsPerByte)
	val handlerAddrWidth = log2Ceil(numReqHandlers)
	val tag1Width        = reqAddrWidth - handlerAddrWidth - hbmChannelWidth
	val tag2Width        = hbmChannelWidth - offsetWidth
	val tagWidth         = tag1Width + tag2Width
	val fullTagWidth     = reqAddrWidth - offsetWidth
	val numIds           = 1 << memIdWidth
	val channelAddrWidth = log2Ceil(numMemoryPorts)

	val io = IO(new Bundle {
		val inReq   = Flipped(DecoupledIO(UInt(tagWidth.W)))
		val outMem  = Flipped(Vec(numMemoryPorts, new AXI4FullReadOnly(UInt(memDataWidth.W), memAddrWidth, memIdWidth)))
		val outResp = DecoupledIO(new AddrDataIO(tagWidth, memDataWidth))
	})

	io.outMem.foreach(x => {
		x.ARLEN   := 0.U
		x.ARSIZE  := log2Ceil(memDataWidth / bitsPerByte).U
		x.ARBURST := 1.U /* FIXED */
		x.ARLOCK  := 0.U  /* Normal (not exclusive/locked) access */
		x.ARCACHE := 0.U /* Non-modifiable */
		x.ARPROT  := 0.U  /* Unprivileged, secure, data
		* (default used by Vivado HLS) */
		x.ARID    := 0.U
	})
}

object InOrderExternalMultiPortedMemoryArbiter {
	val numMemoryPorts = 4
}

class InOrderExternalMultiPortedMemoryArbiter(
		reqAddrWidth:        Int=ExternalMemoryArbiter.reqAddrWidth,
		memAddrWidth:        Int=ExternalMemoryArbiter.memAddrWidth,
		memDataWidth:        Int=ExternalMemoryArbiter.memDataWidth,
		memIdWidth:          Int=ExternalMemoryArbiter.memIdWidth,
		numReqHandlers:      Int=ExternalMemoryArbiter.numReqHandlers,
		maxInFlightRequests: Int=InOrderExternalMemoryArbiter.maxInFlightRequests,
		memAddrOffset:       Long=ExternalMemoryArbiter.memAddrOffset,
		numMemoryPorts:      Int=InOrderExternalMultiPortedMemoryArbiter.numMemoryPorts,
		reqHandlerId:        Int
) extends ExternalMemoryArbiterBase(reqAddrWidth, memAddrWidth, memDataWidth, memIdWidth, numReqHandlers, numMemoryPorts) {
	val inReqWithFullAddr = Wire(DecoupledIO(UInt(fullTagWidth.W)))
	inReqWithFullAddr.valid := io.inReq.valid
	io.inReq.ready          := inReqWithFullAddr.ready
	if (numReqHandlers > 1) {
		if (tag1Width > 0) {
			inReqWithFullAddr.bits := Cat(io.inReq.bits(tagWidth - 1, tagWidth - tag1Width),
										reqHandlerId.U(handlerAddrWidth.W),
										io.inReq.bits(tag2Width - 1, 0))
		} else {
			inReqWithFullAddr.bits := Cat(reqHandlerId.U(handlerAddrWidth.W),
										io.inReq.bits(tagWidth - 1, 0))
		}
	} else {
		inReqWithFullAddr.bits := io.inReq.bits
	}

	val match_conditions =
		if (numMemoryPorts > 1) {
			(0 until numMemoryPorts).map(i =>
				inReqWithFullAddr.bits(channelAddrWidth + handlerAddrWidth + tag2Width - 1, handlerAddrWidth + tag2Width)
				=== i.U(channelAddrWidth.W))
		} else {
			Vector(true.B)
		}
	val addrRegsIn = Array.fill(numMemoryPorts)(Module(new ElasticBuffer(UInt(fullTagWidth.W))).io)
	val memInterfaceManagers =
		Array.fill(numMemoryPorts)(
			Module(new MemoryInterfaceManager(
				fullTagWidth,
				offsetWidth,
				memAddrWidth,
				memDataWidth,
				memIdWidth,
				maxInFlightRequests,
				memAddrOffset
			)).io
		)

	for (i <- 0 until numMemoryPorts) {
		addrRegsIn(i).in.bits  := inReqWithFullAddr.bits
		addrRegsIn(i).in.valid := match_conditions(i) && inReqWithFullAddr.valid
		memInterfaceManagers(i).enq <> addrRegsIn(i).out
	}
	inReqWithFullAddr.ready := Vec(addrRegsIn.zip(match_conditions).map((x) => x._1.in.ready & x._2)).asUInt.orR

	memInterfaceManagers.map(_.outMemAddr).zip(io.outMem).foreach {
		case(mgrPort, memPort) => {
			memPort.ARVALID := mgrPort.valid
			mgrPort.ready   := memPort.ARREADY
			memPort.ARADDR  := mgrPort.bits
		}
	}
	memInterfaceManagers.map(_.inMemData).zip(io.outMem).foreach {
		case(mgrPort, memPort) => {
			mgrPort.valid   := memPort.RVALID
			mgrPort.bits    := memPort.RDATA
			memPort.RREADY  := mgrPort.ready
		}
	}

	val outRespType = io.outResp.bits.cloneType
	val dataArbiter = Module(new ResettableRRArbiter(outRespType, numMemoryPorts)).io
	for (i <- 0 until numMemoryPorts) {
		dataArbiter.in(i).valid := memInterfaceManagers(i).deq.valid
		dataArbiter.in(i).bits.data := memInterfaceManagers(i).deq.bits.data
		if (numReqHandlers > 1) {
			if (tag1Width > 0) {
				dataArbiter.in(i).bits.addr := Cat(memInterfaceManagers(i).deq.bits.addr(fullTagWidth - 1, handlerAddrWidth + tag2Width),
												memInterfaceManagers(i).deq.bits.addr(tag2Width - 1, 0))
			} else {
				dataArbiter.in(i).bits.addr := memInterfaceManagers(i).deq.bits.addr(tag2Width - 1, 0)
			}
		} else {
			dataArbiter.in(i).bits.addr := memInterfaceManagers(i).deq.bits.addr
		}
		memInterfaceManagers(i).deq.ready := dataArbiter.in(i).ready
	}
	dataArbiter.out <> io.outResp

}
