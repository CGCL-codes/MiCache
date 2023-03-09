package fpgamshr.util

import chisel3._
import chisel3.util.{MuxCase, RegEnable}
import scala.language.reflectiveCalls

object StoreToLoadForwarding {
    val defaultType = UInt(32.W)
    val defaultAddrWidth = 16
}

class StoreToLoadForwardingTwoStages[T <: Data](gen: T = StoreToLoadForwarding.defaultType, addrWidth: Int = StoreToLoadForwarding.defaultAddrWidth) extends Module {
    /* Note: fixed depth of two slots (like BRAM latency) */
    val io = IO(new Bundle {
        val rdAddr = Input(UInt(addrWidth.W))
        val wrAddr = Output(UInt(addrWidth.W))
        val wrEn = Input(Bool())
        val pipelineReady = Input(Bool())
        val dataInFromMem = Input(gen)
        val dataInFixed = Output(gen)
        val dataOutToMem = Input(gen)
    })

    /* Store-to-load forwarding logic: we keep a FIFO with the 2 most recently
     * written lines and take those instead of the value fetched from memory,
     * which may be wrong. It's the same logic that is present in the update part
     * of the BRAM LevelComparator */
    val oneRdAddrAgo = RegEnable(io.rdAddr, enable=io.pipelineReady)
    val twoRdAddrAgo = RegEnable(oneRdAddrAgo, enable=io.pipelineReady)
    io.wrAddr := twoRdAddrAgo
    val oneOutLineAgo = RegEnable(io.dataOutToMem, enable=io.pipelineReady)
    val twoOutLinesAgo = RegEnable(oneOutLineAgo, enable=io.pipelineReady)
    val takeOneLineAgo = RegEnable((twoRdAddrAgo === oneRdAddrAgo) & io.wrEn, false.B, enable=io.pipelineReady)
    val takeTwoLinesAgo = RegEnable(RegEnable((twoRdAddrAgo === io.rdAddr) & io.wrEn, false.B, enable=io.pipelineReady), false.B, enable=io.pipelineReady)
    io.dataInFixed := MuxCase(io.dataInFromMem, Array(takeOneLineAgo -> oneOutLineAgo, takeTwoLinesAgo -> twoOutLinesAgo))
}

/* With multiple BRAMs and only one pipeline, there is no need to perform forwarding for all of them. */
class SharedStoreToLoadForwardingTwoStages[T <: Data](gen: T, addrWidth: Int, numMemBlocks: Int) extends Module {
	val io = IO(new Bundle {
		val rdAddrs = Vec(numMemBlocks, Input(UInt(addrWidth.W)))
		val wrAddrs = Vec(numMemBlocks, Output(UInt(addrWidth.W)))
		val wrEns = Vec(numMemBlocks, Input(Bool()))
		val pipelineReady = Input(Bool())
		val dataInFromMems = Vec(numMemBlocks, Input(gen))
		val dataInFixeds = Vec(numMemBlocks, Output(gen))
		val dataOutToMem = Input(gen)
	})

	val rdAddrs1CycAgo = Wire(Vec(numMemBlocks, UInt(addrWidth.W)))
	val rdAddrs2CycAgo = Wire(Vec(numMemBlocks, UInt(addrWidth.W)))
	val takes1CycAgo = Wire(Vec(numMemBlocks, Bool()))
	val takes2CycAgo = Wire(Vec(numMemBlocks, Bool()))
	val outData1CycAgo = RegEnable(io.dataOutToMem, enable=io.pipelineReady)
	val outData2CycAgo = RegEnable(outData1CycAgo, enable=io.pipelineReady)
	for (i <- 0 until numMemBlocks) {
		rdAddrs1CycAgo(i)  := RegEnable(io.rdAddrs(i), enable=io.pipelineReady)
		rdAddrs2CycAgo(i)  := RegEnable(rdAddrs1CycAgo(i), enable=io.pipelineReady)
		io.wrAddrs(i)      := rdAddrs2CycAgo(i)
		takes1CycAgo(i)    := RegEnable((rdAddrs2CycAgo(i) === rdAddrs1CycAgo(i)) & io.wrEns(i), false.B, enable=io.pipelineReady)
		takes2CycAgo(i)    := RegEnable(RegEnable((rdAddrs2CycAgo(i) === io.rdAddrs(i)) & io.wrEns(i), false.B, enable=io.pipelineReady), false.B, enable=io.pipelineReady)
		io.dataInFixeds(i) := MuxCase(io.dataInFromMems(i), Array(takes1CycAgo(i) -> outData1CycAgo, takes2CycAgo(i) -> outData2CycAgo))
	}
}

class StoreToLoadForwardingThreeStages[T <: Data](gen: T = StoreToLoadForwarding.defaultType, addrWidth: Int = StoreToLoadForwarding.defaultAddrWidth) extends Module {
    /* Note: fixed depth of three slots (BRAM latency + 1) but data expected after two cycles */
    val io = IO(new Bundle {
        val rdAddr = Input(UInt(addrWidth.W))
        val wrAddr = Output(UInt(addrWidth.W))
        val wrEn = Input(Bool())
        val pipelineReady = Input(Bool())
        val dataInFromMem = Input(gen)
        val dataInFixed = Output(gen)
        val dataOutToMem = Input(gen)
    })

    /* Store-to-load forwarding logic: we keep a FIFO with the 2 most recently
     * written lines and take those instead of the value fetched from memory,
     * which may be wrong. It's the same logic that is present in the update part
     * of the BRAM LevelComparator */
    val oneRdAddrAgo = RegEnable(io.rdAddr, enable=io.pipelineReady)
    val twoRdAddrAgo = RegEnable(oneRdAddrAgo, enable=io.pipelineReady)
    val threeRdAddrAgo = RegEnable(twoRdAddrAgo, enable=io.pipelineReady)
    io.wrAddr := threeRdAddrAgo
    val oneOutLineAgo = RegEnable(io.dataOutToMem, enable=io.pipelineReady)
    val twoOutLinesAgo = RegEnable(oneOutLineAgo, enable=io.pipelineReady)
    val takeOneLineAgo = (threeRdAddrAgo === twoRdAddrAgo) & io.wrEn
    val takeTwoLinesAgo = RegEnable((threeRdAddrAgo === oneRdAddrAgo) & io.wrEn, false.B, enable=io.pipelineReady)
    val takeThreeLinesAgo = RegEnable(RegEnable((threeRdAddrAgo === io.rdAddr) & io.wrEn, false.B, enable=io.pipelineReady), false.B, enable=io.pipelineReady)
    io.dataInFixed := MuxCase(io.dataInFromMem, Array(takeOneLineAgo -> io.dataOutToMem, takeTwoLinesAgo -> oneOutLineAgo, takeThreeLinesAgo -> twoOutLinesAgo))
}
