package axilitecfg

import chisel3._
import chisel3.util._

// object ElasticBuffer {
// 	def apply[T <: Data](in: DecoupledIO[T]) = {
// 		val m = Module(new ElasticBuffer(in.bits.cloneType))
// 		m.io.in <> in
// 		m.io.out
// 	}
// }

class ElasticBuffer[T <: Data](gen: T) extends Module {
	val io = IO(new Bundle {
		val in = Flipped(DecoupledIO(gen))
		val out = DecoupledIO(gen)
	})

	val fullBuffer = Module(new ElasticBufferRegExport(gen))
	fullBuffer.io.in <> io.in
	io.out <> fullBuffer.io.out
}

class ElasticBufferRegExport[T <: Data](gen: T) extends Module {
	val io = IO(new Bundle {
		val in = Flipped(DecoupledIO(gen))
		val out = DecoupledIO(gen)
		val regs = Vec(2, ValidIO(gen))
		val readyReg = Output(Bool())
	})

	val outerRegData = Reg(gen)
	val outerRegValid = RegInit(Bool(), false.B)
	val innerRegData = Reg(gen)
	val innerRegValid = RegInit(Bool(), false.B)
	val readyReg = Reg(Bool())

	when (readyReg === true.B)
	{
		outerRegData := io.in.bits
		innerRegData := outerRegData
		outerRegValid := io.in.valid
		innerRegValid := outerRegValid & ~(io.out.ready | ~io.out.valid)
	}
	io.out.bits := Mux(readyReg === true.B, outerRegData, innerRegData)
	io.out.valid := Mux(readyReg === true.B, outerRegValid, innerRegValid)
	readyReg := io.out.ready | ~io.out.valid
	io.in.ready := readyReg
	io.regs(0).bits := outerRegData
	io.regs(0).valid := outerRegValid
	io.regs(1).bits := innerRegData
	io.regs(1).valid := innerRegValid
	io.readyReg := readyReg
}

class AXILiteConfig extends Module {
	val numRegs = 8 + 1
	val dataWidth = 32
	val addrWidth = log2Ceil(numRegs) + log2Ceil(dataWidth / 8) // +1 for the status register
	val io = IO(new Bundle {
		// AXI-Lite signals
		val axiLiteCfg = new AXI4Lite(UInt(dataWidth.W), addrWidth)
		// User signals
		val traceNum = Output(UInt(dataWidth.W))
		val cycleCnt = Input(UInt(dataWidth.W))
		// State signals
		val start = Output(Bool())
		val end   = Input(Bool())
	})

	// val sIdle :: sRunning :: Nil = Enum(2)
	val start = RegInit(false.B)
	io.start := start
	val traceNumReg = RegInit(UInt(dataWidth.W), 0.U)

	// AXI-Lite interface
	// 0x0  start/end
	// 0x10 trace number
	// 0x20 cycle

	// Read Channel
	val rdAddrEb = Module(new ElasticBuffer(UInt(log2Ceil(numRegs).W))).io
	rdAddrEb.in.bits      := io.axiLiteCfg.ARADDR(addrWidth - 1, log2Ceil(dataWidth / 8))
	rdAddrEb.in.valid     := io.axiLiteCfg.ARVALID
	io.axiLiteCfg.ARREADY := rdAddrEb.in.ready

	io.axiLiteCfg.RDATA  := MuxLookup(rdAddrEb.out.bits, io.end, Array(4.U -> traceNumReg, 8.U -> io.cycleCnt))
	io.axiLiteCfg.RVALID := rdAddrEb.out.valid
	rdAddrEb.out.ready   := io.axiLiteCfg.RREADY

	io.axiLiteCfg.RRESP := 0.U

	// Write Channel
	val wrAddrEb = Module(new ElasticBuffer(UInt(log2Ceil(numRegs).W))).io
	val wrDataEb = Module(new ElasticBuffer(io.axiLiteCfg.WDATA.cloneType)).io
	val wrStrbEb = Module(new ElasticBuffer(io.axiLiteCfg.WSTRB.cloneType)).io
	
	wrAddrEb.in.bits      := io.axiLiteCfg.AWADDR(addrWidth - 1, log2Ceil(dataWidth / 8))
	wrAddrEb.in.valid     := io.axiLiteCfg.AWVALID
	io.axiLiteCfg.AWREADY := wrAddrEb.in.ready
	
	wrDataEb.in.bits     := io.axiLiteCfg.WDATA
	wrDataEb.in.valid    := io.axiLiteCfg.WVALID
	wrStrbEb.in.bits     := io.axiLiteCfg.WSTRB
	wrStrbEb.in.valid    := io.axiLiteCfg.WVALID
	io.axiLiteCfg.WREADY := wrDataEb.in.ready & wrStrbEb.in.ready

	wrAddrEb.out.ready := wrDataEb.out.valid & ~io.axiLiteCfg.BVALID
	wrDataEb.out.ready := wrAddrEb.out.valid & ~io.axiLiteCfg.BVALID
	wrStrbEb.out.ready := wrAddrEb.out.valid & ~io.axiLiteCfg.BVALID

	val wrAddrDataAvailable = wrAddrEb.out.valid & wrDataEb.out.valid & wrStrbEb.out.valid & ~io.axiLiteCfg.BVALID

	val bvalidReg = RegInit(false.B)
	io.axiLiteCfg.BRESP  := 0.U
	io.axiLiteCfg.BVALID := bvalidReg

	when (wrAddrDataAvailable && wrStrbEb.out.bits.andR) {
		when (wrAddrEb.out.bits === 0.U) {
			start := wrDataEb.out.bits(0)
		}.elsewhen (wrAddrEb.out.bits === 4.U) {
			traceNumReg := wrDataEb.out.bits
		}
	}
	when (wrAddrDataAvailable) {
		bvalidReg := true.B
	} .elsewhen (bvalidReg & io.axiLiteCfg.BREADY) {
		bvalidReg := false.B
	}

	io.traceNum := traceNumReg
}

/**
 * To just generate the Verilog for the AXILiteConfig, run:
 * $ sbt "test:runMain axislenstream.AXILiteConfig"
 */
object AXILiteConfig extends App {
	chisel3.Driver.execute(args, () => new AXILiteConfig)
}
