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

class UniMSHREntry(val tagWidth: Int, val ldBufPtrWidth: Int) extends Bundle with HasIsMSHR with HasTag {
	val ldBufPtr = UInt(ldBufPtrWidth.W)
	override def cloneType = (new UniMSHREntry(tagWidth, ldBufPtrWidth)).asInstanceOf[this.type]
}

class UniMSHREntryValid(val tagWidth: Int, val ldBufPtrWidth: Int) extends Bundle with HasValid with HasIsMSHR with HasTag {
	val ldBufPtr = UInt(ldBufPtrWidth.W)
	override def cloneType = (new UniMSHREntryValid(tagWidth, ldBufPtrWidth)).asInstanceOf[this.type]
}
