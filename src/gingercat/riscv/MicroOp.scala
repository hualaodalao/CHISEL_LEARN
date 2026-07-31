
import chisel3.util.BitPat

// Will be used to build a data model of what a micro op needs / does.
abstract class MicroOp(val resources : scala.collection.Seq[Resource]) {
  def keys : Seq[BitPat]
  //def keysMasked = keys.map(Masked.apply)
}

// Define one to one instruction to micro op mapping.
// In RISC-V "all" instruction can be considered as one to one with micro op.
// This simplifies things.
// The keys argument specifies what is the RISC-V instruction leading to that micro op.
case class SingleDecoding(keys : Seq[BitPat], override val resources : scala.collection.Seq[Resource]) extends MicroOp(resources) with Nameable {
  override def toString = s"${getName("")} $keys"
}
object SingleDecoding{
  def apply(key : BitPat, resources : scala.collection.Seq[Resource]): SingleDecoding = {
    SingleDecoding(List(key), resources)
  }
}

// Here we will define the classes and object which can be used to specify what a micro op needs and produces
// Those will be used to build a data model which can later be processed by some hardware generator.
class Resource
case class RfResource(rf : RegfileSpec, access : RfAccess) extends Resource

class RfAccess 
class RfRead extends RfAccess
class RfWrite extends RfAccess

object RS1 extends RfRead 
object RS2 extends RfRead 
object RS3 extends RfRead 
object RD  extends RfWrite 
object PC_READ  extends Resource 
object INSTRUCTION_SIZE  extends Resource
object LQ  extends Resource 
object SQ  extends Resource 
object FPU extends Resource 
object RM  extends Resource 



