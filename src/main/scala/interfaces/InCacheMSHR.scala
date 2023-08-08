package fpgamshr.interfaces

import chisel3._
import chisel3.util._
import scala.language.reflectiveCalls

// Classes of unified storage type for both cachelines and MSHRs

// class UniCacheLine(val tagWidth: Int, val dataWidth: Int) extends Bundle with HasIsMSHR with HasTag with HasData {
// 	override def cloneType = (new UniCacheLine(tagWidth, dataWidth)).asInstanceOf[this.type]
// }

// class UniCacheLineValid(val tagWidth: Int, val dataWidth: Int) extends Bundle with HasValid with HasIsMSHR with HasTag with HasData {
// 	override def cloneType = (new UniCacheLineValid(tagWidth, dataWidth)).asInstanceOf[this.type]
// }

class UniTag(val tagWidth: Int, val lastValidIdxWidth: Int) extends Bundle with HasValid with HasIsMSHR with HasTag {
	val lastValidIdx = UInt(lastValidIdxWidth.W)
	override def cloneType = (new UniTag(tagWidth, lastValidIdxWidth)).asInstanceOf[this.type]
}

class Subentry(offsetWidth: Int, idWidth: Int) extends Bundle {
	val offset = UInt(offsetWidth.W)
	val id = UInt(idWidth.W)
	override def cloneType = (new Subentry(offsetWidth, idWidth)).asInstanceOf[this.type]
}

class SubentryWithPadding(offsetWidth: Int, idWidth: Int, paddingWidth: Int) extends Subentry(offsetWidth, idWidth) {
	val padding = UInt(paddingWidth.W)
	override def cloneType = (new SubentryWithPadding(offsetWidth, idWidth, paddingWidth)).asInstanceOf[this.type]
}

// Subentries are stored in the data field of a cache line, so lineWidth is cache data width.
// Add padding for BRAM byte write enble.
class SubentryLine(lineWidth: Int, val offsetWidth: Int, val idWidth: Int, numSubentriesPerLine: Int, alignWidth: Int=8) extends Bundle {
	val entryBits = roundUp(offsetWidth + idWidth, alignWidth)
	val maxEntriesPerLine = lineWidth / entryBits
	require(numSubentriesPerLine <= maxEntriesPerLine)
	val entriesPerLine = if (numSubentriesPerLine == 0) maxEntriesPerLine else numSubentriesPerLine
	val lastValidIdxWidth = log2Ceil(entriesPerLine)
	val entryBytes = entryBits / alignWidth

	val entries = Vec(entriesPerLine, new SubentryWithPadding(offsetWidth, idWidth, entryBits - (offsetWidth + idWidth)))

	def roundUp(a: Int, alignment: Int) = a + (alignment - a % alignment) % alignment
	def withNoPadding(): SubentryLineWithNoPadding = {
		val out = Wire(new SubentryLineWithNoPadding(offsetWidth, idWidth, entriesPerLine))
		out.entries.zip(this.entries).map(x => {
			x._1.offset  := x._2.offset
			x._1.id      := x._2.id
		})
		out
	}
	override def cloneType = (new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine, alignWidth)).asInstanceOf[this.type]
}

object SubentryLineWithNoPadding {
	def addPadding(raw: Vec[Subentry], gen: SubentryLine): Vec[SubentryWithPadding] = {
		val out = Wire(gen.cloneType)
		// out.lastValidIdx := this.lastValidIdx
		// out.padding      := DontCare
		out.entries.zip(raw).map(x => {
			x._1.offset  := x._2.offset
			x._1.id      := x._2.id
			x._1.padding := DontCare
		})
		out.entries
	}

}

class SubentryLineWithNoPadding(val offsetWidth: Int, val idWidth: Int, val entriesPerLine: Int) extends Bundle {
	// val lastValidIdxWidth = log2Ceil(entriesPerLine)
	// val lastValidIdx = UInt(lastValidIdxWidth.W)
	val entries = Vec(entriesPerLine, new Subentry(offsetWidth, idWidth))
	override def cloneType = (new SubentryLineWithNoPadding(offsetWidth, idWidth, entriesPerLine)).asInstanceOf[this.type]
	def withPadding(gen: SubentryLine): SubentryLine = {
		val out = Wire(gen.cloneType)
		// out.lastValidIdx := this.lastValidIdx
		// out.padding      := DontCare
		out.entries.zip(this.entries).map(x => {
			x._1.offset  := x._2.offset
			x._1.id      := x._2.id
			x._1.padding := DontCare
		})
		out
	}
}

// class UniMSHREntry(val tagWidth: Int, lineWidth: Int, offsetWidth: Int, idWidth: Int, numSubentriesPerLine: Int) extends Bundle with HasIsMSHR with HasTag {
// 	val sub = new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine)
// 	override def cloneType = (new UniMSHREntry(tagWidth, lineWidth, offsetWidth, idWidth, numSubentriesPerLine)).asInstanceOf[this.type]
// }

// class UniMSHREntryValid(val tagWidth: Int, lineWidth: Int, offsetWidth: Int, idWidth: Int, numSubentriesPerLine: Int)
// extends Bundle with HasValid with HasIsMSHR with HasTag {
// 	val sub = new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine)
// 	override def cloneType = (new UniMSHREntryValid(tagWidth, lineWidth, offsetWidth, idWidth, numSubentriesPerLine)).asInstanceOf[this.type]
// }

// class UniForwarding(tagWidth: Int, lastValidIdxWidth: Int) extends UniTag(tagWidth) {
// 	val lastValidIdx = UInt(lastValidIdxWidth.W)
// 	override def cloneType = (new UniForwarding(tagWidth, lastValidIdxWidth)).asInstanceOf[this.type]
// }

class StashVictimInIO(val tagWidth: Int, val lastValidIdxWidth: Int, val lastTableIdxWidth: Int) extends Bundle with HasTag with HasLastTableIdx {
	val lastValidIdx = UInt(lastValidIdxWidth.W)
	override def cloneType = (new StashVictimInIO(tagWidth, lastValidIdxWidth, lastTableIdxWidth)).asInstanceOf[this.type]
}

// class StashNewSubentryIO(subGen: Subentry, lastValidIdxWidth: Int) extends Bundle {
// 	val entry = subGen.cloneType
// 	val lastValidIdx = UInt(lastValidIdxWidth.W)
// 	override def cloneType = (new StashNewSubentryIO(subGen, lastValidIdxWidth)).asInstanceOf[this.type]
// }

class StashEntry(val tagWidth: Int, val lastValidIdxWidth: Int, val lastTableIdxWidth: Int)
extends Bundle with HasValid with HasTag with HasLastTableIdx {
	val inPipeline = Bool()
	val subTransit = Bool()
	val lastValidIdx = UInt(lastValidIdxWidth.W)
	// val sub = new SubentryLineWithNoPadding(subGen.offsetWidth, subGen.idWidth, subGen.entriesPerLine)
	override def cloneType = (new StashEntry(tagWidth, lastValidIdxWidth, lastTableIdxWidth)).asInstanceOf[this.type]
	def getInvalidEntry() = {
		val m = Wire(this)
		m.valid := false.B
		m.inPipeline := DontCare
		m.subTransit := DontCare
		m.tag := DontCare
		m.lastValidIdx := DontCare
		m.lastTableIdx := DontCare
		m
	}
}

class UniRespGenIO(val dataWidth: Int, offsetWidth: Int, idWidth: Int, entriesPerLine: Int)
extends SubentryLineWithNoPadding(offsetWidth, idWidth, entriesPerLine) with HasData {
	val lastValidIdxWidth = log2Ceil(entriesPerLine)
	val lastValidIdx = UInt(lastValidIdxWidth.W)
	override def cloneType = (new UniRespGenIO(dataWidth, offsetWidth, idWidth, entriesPerLine)).asInstanceOf[this.type]
}

class AllocPipelineIO(val addrWidth: Int, val idWidth: Int) extends Bundle with HasValid with HasAddr with HasID {
	val isFromStash = Bool()
	override def cloneType = (new AllocPipelineIO(addrWidth, idWidth)).asInstanceOf[this.type]
	def getInvalid() = {
		val m = Wire(this)
		m.valid := false.B
		m.addr := DontCare
		m.id := DontCare
		m.isFromStash := DontCare
		m
	}
}

class DeallocPipelineIO(val addrWidth: Int) extends Bundle with HasValid with HasAddr {
	override def cloneType = (new DeallocPipelineIO(addrWidth)).asInstanceOf[this.type]
	def getInvalid() = {
		val m = Wire(this)
		m.valid := false.B
		m.addr := DontCare
		m
	}
}
