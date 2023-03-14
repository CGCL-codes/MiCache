package fpgamshr.interfaces

import chisel3._
import chisel3.util._
import scala.language.reflectiveCalls

// Classes of unified storage type for both cachelines and MSHRs

class UniCacheLine(val tagWidth: Int, val dataWidth: Int) extends Bundle with HasIsMSHR with HasTag with HasData {
	override def cloneType = (new UniCacheLine(tagWidth, dataWidth)).asInstanceOf[this.type]
}

class UniCacheLineValid(val tagWidth: Int, val dataWidth: Int) extends Bundle with HasValid with HasIsMSHR with HasTag with HasData {
	override def cloneType = (new UniCacheLineValid(tagWidth, dataWidth)).asInstanceOf[this.type]
}

class Subentry(offsetWidth: Int, idWidth: Int) extends Bundle {
	val offset = UInt(offsetWidth.W)
	val id = UInt(idWidth.W)
	override def cloneType = (new Subentry(offsetWidth, idWidth)).asInstanceOf[this.type]
}

// Subentries are stored in the data field of a cache line, so lineWidth is cache data width.
class SubentryLine(lineWidth: Int, offsetWidth: Int, idWidth: Int, numSubentriesPerLine: Int) extends Bundle {
	val max = lineWidth / (offsetWidth + idWidth)
	val exceeded = max * (offsetWidth + idWidth) + log2Ceil(max) > lineWidth
	val maxEntriesPerLine = if (exceeded) max - 1 else max
	require(numSubentriesPerLine <= maxEntriesPerLine)

	val entriesPerLine = if (numSubentriesPerLine == 0) maxEntriesPerLine else numSubentriesPerLine
	val lastValidIdxWidth = log2Ceil(entriesPerLine)

	val lastValidIdx = UInt(lastValidIdxWidth.W)
	val entries = Vec(entriesPerLine, new Subentry(offsetWidth, idWidth))

	override def cloneType = (new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine)).asInstanceOf[this.type]
}

class UniMSHREntry(val tagWidth: Int, lineWidth: Int, offsetWidth: Int, idWidth: Int, numSubentriesPerLine: Int) extends Bundle with HasIsMSHR with HasTag {
	val sub = new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine)
	override def cloneType = (new UniMSHREntry(tagWidth, lineWidth, offsetWidth, idWidth, numSubentriesPerLine: Int)).asInstanceOf[this.type]
}

class UniMSHREntryValid(val tagWidth: Int, lineWidth: Int, offsetWidth: Int, idWidth: Int, numSubentriesPerLine: Int)
extends Bundle with HasValid with HasIsMSHR with HasTag {
	val sub = new SubentryLine(lineWidth, offsetWidth, idWidth, numSubentriesPerLine)
	override def cloneType = (new UniMSHREntryValid(tagWidth, lineWidth, offsetWidth, idWidth, numSubentriesPerLine)).asInstanceOf[this.type]
}

class StashEntryInIO(mshrGen: UniMSHREntryValid, val lastTableIdxWidth: Int) extends {
	val tagWidth = mshrGen.tagWidth
} with Bundle with HasTag with HasLastTableIdx {
	val sub = mshrGen.sub.cloneType
	override def cloneType = (new StashEntryInIO(mshrGen, lastTableIdxWidth)).asInstanceOf[this.type]
}

class StashEntry(mshrGen: UniMSHREntryValid, val lastTableIdxWidth: Int) extends {
	val tagWidth = mshrGen.tagWidth
} with Bundle with HasValid with HasTag with HasLastTableIdx {
	val inPipeline = Bool()
	val sub = mshrGen.sub.cloneType
	override def cloneType = (new StashEntry(mshrGen, lastTableIdxWidth)).asInstanceOf[this.type]
	def getInvalidEntry() = {
		val m = Wire(this)
		m.valid := false.B
		m.inPipeline := DontCare
		m.tag := DontCare
		m.sub := DontCare
		m.lastTableIdx := DontCare
		m
	}
}

class UniRespGenIO(val dataWidth: Int, offsetWidth: Int, idWidth: Int, entriesPerLine: Int)
extends SubentryLine((offsetWidth + idWidth) * entriesPerLine + log2Ceil(entriesPerLine), offsetWidth, idWidth, entriesPerLine) with HasData {
	override def cloneType = (new UniRespGenIO(dataWidth, offsetWidth, idWidth, entriesPerLine)).asInstanceOf[this.type]
}
