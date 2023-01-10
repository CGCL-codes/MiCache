package trace

import chisel3._
import chisel3.util._
import scala.language.reflectiveCalls

// object ElasticBuffer {
// 	def apply[T <: Data](in: DecoupledIO[T]) = {
// 		val m = Module(new ElasticBuffer(in.bits.cloneType))
// 		m.io.in <> in
// 		m.io.out
// 	}
// }

class ElasticBuffer2[T <: Data](gen: T) extends Module {
	val io = IO(new Bundle {
		val in = Flipped(DecoupledIO(gen))
		val out = DecoupledIO(gen)
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
}

class TraceEngine extends Module {
	val nParam = 2
	val nState = 3
	val nCfgReg = nParam + nState
	val cfgDataWidth = 32
	val cfgAddrWidth = log2Ceil(nCfgReg) + log2Ceil(cfgDataWidth / 8) // +1 for the status register
	val traceWidth = 32
	val dataWidth = 32
	val addressWidth = 33
	val idWidth = 0		// a reorder buffer is needed after axiOut
	val io = IO(new Bundle {
		// AXI-Lite signals
		val axiLiteCfg = new AXI4Lite(UInt(cfgDataWidth.W), cfgAddrWidth)
		// Trace input
		val axiStreamTrace = new AXI4Stream(UInt(traceWidth.W))
		// Trace execute port
		val axiOut = Flipped(new AXI4FullReadOnly(UInt(dataWidth.W), addressWidth, idWidth))
		val syncStart = Input(Bool()) // for all PE starting simultaneously
	})

	val sIdle :: sRunning :: Nil = Enum(2)
	val state = RegInit(sIdle)
	
	// AXI-Lite interface at 64-bit-wide cfgReg
	// 0x0			start/end
	// 0x8			cycles
	// 0x10			executed count
	// 0x18			trace count (param0)
	// 0x20			read element sum (param1)
	
	// 0x10 ~ 0x40	parameters
	val start      = RegInit(false.B)
	val cycles     = RegInit(0.U(cfgDataWidth.W))
	val execCount  = RegInit(0.U(cfgDataWidth.W))
	val traceCount = RegInit(0.U(cfgDataWidth.W))
	val paramRegs  = RegInit(Vec(Seq.fill(nParam)(0.U(cfgDataWidth.W))))

	// Read Channel
	val rdAddrEb = Module(new ElasticBuffer2(UInt(log2Ceil(nCfgReg).W))).io
	rdAddrEb.in.bits      := io.axiLiteCfg.ARADDR(cfgAddrWidth - 1, log2Ceil(cfgDataWidth / 8))
	rdAddrEb.in.valid     := io.axiLiteCfg.ARVALID
	io.axiLiteCfg.ARREADY := rdAddrEb.in.ready

	io.axiLiteCfg.RDATA  := MuxLookup(rdAddrEb.out.bits, ~state,
										Array(0.U -> ~state, 1.U -> cycles, 2.U -> execCount) ++
										(0 until nParam).zip(paramRegs).map((x) => (x._1 + nState).U -> x._2))
	io.axiLiteCfg.RVALID := rdAddrEb.out.valid
	rdAddrEb.out.ready   := io.axiLiteCfg.RREADY
	io.axiLiteCfg.RRESP := 0.U

	// Write Channel
	val wrAddrEb = Module(new ElasticBuffer2(UInt(log2Ceil(nCfgReg).W))).io
	val wrDataEb = Module(new ElasticBuffer2(io.axiLiteCfg.WDATA.cloneType)).io
	val wrStrbEb = Module(new ElasticBuffer2(io.axiLiteCfg.WSTRB.cloneType)).io
	wrAddrEb.in.bits      := io.axiLiteCfg.AWADDR(cfgAddrWidth - 1, log2Ceil(cfgDataWidth / 8))
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
		}
	}
	for (i <- 0 until nParam) {
		when (wrAddrDataAvailable && wrStrbEb.out.bits.andR) {
			when (wrAddrEb.out.bits === (i + nState).U) {
				paramRegs(i) := wrDataEb.out.bits
			}
		}
	}
	when (wrAddrDataAvailable) {
		bvalidReg := true.B
	} .elsewhen (bvalidReg & io.axiLiteCfg.BREADY) {
		bvalidReg := false.B
	}

	// Main logic
	switch (state) {
		is (sIdle) {
			when (start && io.syncStart) {
				state      := sRunning
				cycles     := 0.U
				execCount  := 0.U
				traceCount := paramRegs(0)
				start      := false.B
			}
		}
		is (sRunning) {
			cycles := cycles + 1.U
			when (io.axiOut.RREADY & io.axiOut.RVALID) {
				execCount    := execCount + 1.U
				paramRegs(1) := paramRegs(1) + io.axiOut.RDATA
				// when (io.axiStreamTrace.TLAST) {
				when (execCount === traceCount - 1.U) {
					state := sIdle
				}
			}
		}
	}

	io.axiOut.ARADDR  := Cat(io.axiStreamTrace.TDATA, 0.U(log2Ceil(dataWidth / 8).W))
	io.axiOut.ARVALID := io.axiStreamTrace.TVALID & (state === sRunning)
	io.axiStreamTrace.TREADY := io.axiOut.ARREADY & (state === sRunning)

	io.axiOut.ARLEN   := 0.U
	io.axiOut.ARSIZE  := log2Ceil(dataWidth / 8).U
	io.axiOut.ARBURST := 1.U
	io.axiOut.ARLOCK  := 0.U
	io.axiOut.ARCACHE := 0.U
	io.axiOut.ARPROT  := 0.U
	io.axiOut.ARID    := 0.U

	// Always ready
	io.axiOut.RREADY  := true.B

}

/**
 * To just generate the Verilog for the TraceEngine, run:
 * $ sbt "test:runMain trace.TraceEngine"
 * $ sbt "Test / runMain trace.TraceEngine"
 */
object TraceEngine extends App {
	chisel3.Driver.execute(args, () => new TraceEngine)
}
