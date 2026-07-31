

import chisel3.util.BitPat

trait RegfileSpec {
    def sizeArch : Int // How many words it has from a RISC-V perspective (not physically)
    def width : Int // How many bits per word
    def x0AlwaysZero : Boolean // used by the RISC-V integer register file to hardwire x0
    def getName() : String
    def initialValue : BigInt // For the FPU, it allows to encode NaN as an initial value

     def ->(access : RfAccess) = RfResource(this, access)
}

object IntRegFile extends RegfileSpec {
    def sizeArch = Riscv.rfDepth
    def width = Riscv.XLEN
    def x0AlwaysZero = true
    def getName() = "IntRegfile"
    def initialValue = 0

    def TypeR(key : BitPat) = SingleDecoding(key = key, resources = List(RS1, RS2, RD).map(this -> _))
    
     def TypeR3(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2, RS3, RD).map(this -> _)
  )
  def TypeI(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RD).map(this -> _)
  )
  def TypeI(keys: Seq[BitPat]) = SingleDecoding(
    keys = keys,
    resources = List(RS1, RD).map(this -> _)
  )

  def TypeJ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RD).map(this -> _)
  )
  def TypeB(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2).map(this -> _) :+ PC_READ :+ INSTRUCTION_SIZE
  )
  def TypeU(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RD).map(this -> _)
  )
  def TypeRS(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2).map(this -> _)
  )
  def TypeUPC(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RD).map(this -> _) :+ PC_READ
  )
  def TypeILQ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RD).map(this -> _) :+ LQ :+ PC_READ //PC_READ is used to reschedule a load which had some store hazard
  )
  def TypeRLQ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RD).map(this -> _) :+ LQ :+ PC_READ //PC_READ is used to reschedule a load which had some store hazard
  )
  def TypeRSQ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2).map(this -> _) :+ SQ
  )
  def TypeSSQ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2).map(this -> _) :+ SQ
  )
  def TypeASQ(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1, RS2, RD).map(this -> _) :+ SQ
  )
  def TypeIC(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RD).map(this -> _)
  )
  def TypeCmb(key : BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1).map(this -> _)
  )

  def TypeNone(key : BitPat) = SingleDecoding(
    key = key,
    resources = Nil
  )

  def TypeCmoPrefetch(key: BitPat) = SingleDecoding(
    key = key,
    resources = List(RS1).map(this -> _)
  )
}


object FloatRegFile extends RegfileSpec {
    def sizeArch = 32
    def width = 64
    def x0AlwaysZero = true
    def getName() = "FpuRegfile"
    def initialValue = 0
}
