import chisel3._
import chisel3.util._

class PlruQueryPort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
}

class PlruQueryResp(cfg: LlcConfig) extends Bundle {
  val victimWay = Output(UInt(log2Ceil(cfg.ways).W))
}

class PlruUpdatePort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
  val way    = Input(UInt(log2Ceil(cfg.ways).W))
}

/** Tree-PLRU replacement policy for a power-of-two-way set-associative cache.
  *
  * Per set a (ways - 1)-bit binary tree: node 0 is the root, the children of
  * node n are 2n+1 and 2n+2, and the leaves in order are the ways. A node bit
  * of 0 means "go left next time" (the left subtree was used more recently),
  * 1 means "go right next time". The victim is the leaf reached by following
  * the node bits from the root.
  *
  * An access to a way sets every node on its path to point away from it; the
  * other nodes of the set keep their value, so an update is a masked write and
  * does not read the tree.
  *
  * The state is a sync-read SRAM read in the cycle before the lookup. The read
  * data predates the update of the previous cycle and the update of the cycle
  * it is consumed in, both of which are applied on top of it.
  */
class LlcPlru(cfg: LlcConfig) extends Module {
  require(cfg.ways >= 2, s"Tree-PLRU needs at least 2 ways, got ${cfg.ways}")

  val io = IO(new Bundle {
    val query     = new PlruQueryPort(cfg)
    val queryResp = new PlruQueryResp(cfg)
    val update    = new PlruUpdatePort(cfg)
  })

  val depth       = log2Ceil(cfg.ways)
  val numTreeBits = cfg.ways - 1
  val idxW        = log2Ceil(numTreeBits).max(1)

  val plruState = SyncReadMem(cfg.sets, Vec(numTreeBits, Bool()))
  val rdTree = plruState.read(io.query.set, io.query.enable).asUInt
  val rdSet  = RegEnable(io.query.set, io.query.enable)

  def child(node: UInt, right: Bool): UInt =
    Mux(right, (node << 1) + 2.U, (node << 1) + 1.U)(idxW - 1, 0)

  // Nodes on the path of a way from the root down, each with the value an
  // access to that way writes into it
  def path(way: UInt): Seq[(UInt, Bool)] = {
    var node = 0.U(idxW.W)
    Seq.tabulate(depth) { k =>
      val bit  = way(depth - 1 - k)
      val here = node
      node = child(here, bit)
      (here, !bit)
    }
  }

  // The tree with every node on the path of way set to its new value
  def updated(tree: UInt, way: UInt): UInt =
    path(way).foldLeft(tree) { case (t, (node, value)) =>
      val oh = UIntToOH(node, numTreeBits)
      Mux(value, t | oh, t & (~oh).asUInt)
    }

  // The leaf reached by following the node bits from the root
  def victim(tree: UInt): UInt = {
    var node = 0.U(idxW.W)
    val bits = Seq.tabulate(depth) { _ =>
      val here = node
      val bit  = tree(here)
      node = child(here, bit)
      bit
    }
    Cat(bits)
  }

  // Update of the previous cycle: it is not in the read data when the read
  // was issued in the same cycle as that update
  val updQValid = RegNext(io.update.enable, false.B)
  val updQSet   = RegNext(io.update.set)
  val updQWay   = RegNext(io.update.way)
  val prevTree = Mux(updQValid && (updQSet === rdSet), updated(rdTree, updQWay), rdTree)
  val curTree  = Mux(io.update.enable && (io.update.set === rdSet),
                     updated(prevTree, io.update.way), prevTree)
  io.queryResp.victimWay := victim(curTree)

  when(io.update.enable) {
    val mask = path(io.update.way).map(n => UIntToOH(n._1, numTreeBits)).reduce(_ | _)
    plruState.write(io.update.set, VecInit(updated(0.U(numTreeBits.W), io.update.way).asBools),
      mask.asBools)
  }
}
