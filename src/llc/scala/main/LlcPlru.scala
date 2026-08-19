import chisel3._
import chisel3.util._

class PlruQueryPort(cfg: LlcConfig) extends Bundle {
  val set = Input(UInt(cfg.setBits.W))
}

class PlruQueryResp(cfg: LlcConfig) extends Bundle {
  val victimWay = Output(UInt(log2Ceil(cfg.ways).W))
}

class PlruUpdatePort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
  val way    = Input(UInt(log2Ceil(cfg.ways).W))
}

/** Tree-PLRU replacement policy for 8-way set-associative cache.
  *
  * 7-bit binary tree per set:
  *   - bit 0: root
  *   - bits 1,2: level-1 (left child = 2*i+1, right child = 2*i+2 mapped to bits 1,2)
  *   - bits 3,4,5,6: level-2 (leaf nodes)
  *
  * Tree node index layout (0-based):
  *   Level 0:          0
  *   Level 1:       1     2
  *   Level 2:     3   4  5   6
  *   Ways:       0 1 2 3 4 5 6 7
  *
  * A tree bit value of 0 means "go left next time" (left subtree was more recently used),
  * a value of 1 means "go right next time".
  */
class LlcPlru(cfg: LlcConfig) extends Module {
  require(cfg.ways == 8, "Tree-PLRU implementation currently supports 8-way only")

  val io = IO(new Bundle {
    val query     = new PlruQueryPort(cfg)
    val queryResp = new PlruQueryResp(cfg)
    val update    = new PlruUpdatePort(cfg)
  })

  val numTreeBits = cfg.ways - 1  // 7 for 8-way

  // PLRU state: one tree per set, stored in registers for combinational read
  val plruState = RegInit(VecInit(Seq.fill(cfg.sets)(0.U(numTreeBits.W))))

  // --- Query: find victim by traversing tree ---
  val queryTree = plruState(io.query.set)

  // Traverse the 3-level tree to find victim way
  // Index width is log2Ceil(numTreeBits) = 3 for 7 tree bits
  val idxW = log2Ceil(numTreeBits)

  // Level 0: root is bit 0
  val l0Bit = queryTree(0)
  // Level 1: left child index 1, right child index 2
  val l1Idx = Mux(l0Bit, 2.U(idxW.W), 1.U(idxW.W))
  val l1Bit = queryTree(l1Idx)
  // Level 2: children of node at l1Idx are at 2*l1Idx+1 and 2*l1Idx+2
  val l2Idx = Mux(l1Bit, l1Idx * 2.U + 2.U, l1Idx * 2.U + 1.U)(idxW - 1, 0)
  val l2Bit = queryTree(l2Idx)

  // Final victim way: 3-bit concatenation of traversal decisions
  // l0Bit selects upper/lower half, l1Bit selects quarter, l2Bit selects way within pair
  io.queryResp.victimWay := Cat(l0Bit, l1Bit, l2Bit)

  // --- Update: point tree bits AWAY from the accessed way ---
  when(io.update.enable) {
    val way = io.update.way
    val tree = plruState(io.update.set)
    val newTree = Wire(UInt(numTreeBits.W))
    val treeBits = Wire(Vec(numTreeBits, Bool()))

    for (i <- 0 until numTreeBits) {
      treeBits(i) := tree(i)
    }

    // Way bits: way(2) = l0 decision, way(1) = l1 decision, way(0) = l2 decision
    // Update root (bit 0): point away from accessed way's half
    // If way is in right half (way(2)=1), set root to 0 (point left next time)
    // If way is in left half (way(2)=0), set root to 1 (point right next time)
    treeBits(0) := ~way(2)

    // Update level-1 node:
    // If way(2)=0, update node 1 (left child of root); otherwise update node 2
    val l1NodeIdx = Mux(way(2), 2.U, 1.U)
    // Point away from the accessed sub-half
    when(l1NodeIdx === 1.U) {
      treeBits(1) := ~way(1)
    }.otherwise {
      treeBits(2) := ~way(1)
    }

    // Update level-2 node:
    // Node index = 2 * l1NodeIdx + 1 if way(1)=0, else 2 * l1NodeIdx + 2
    val l2NodeIdx = Mux(way(1), l1NodeIdx * 2.U + 2.U, l1NodeIdx * 2.U + 1.U)
    for (i <- 3 until numTreeBits) {
      when(l2NodeIdx === i.U) {
        treeBits(i) := ~way(0)
      }
    }

    newTree := treeBits.asUInt
    plruState(io.update.set) := newTree
  }
}
