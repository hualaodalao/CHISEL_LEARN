import chisel3._
import chisel3.util._

class WayPartitionIO(cfg: LlcConfig) extends Bundle {
  val partitionId     = Input(UInt(log2Ceil(cfg.wayPartitions).W))
  val plruVictim      = Input(UInt(log2Ceil(cfg.ways).W))
  val wayValids       = Input(Vec(cfg.ways, Bool()))
  val wayPartitionMap = Input(Vec(cfg.ways, UInt(log2Ceil(cfg.wayPartitions).W)))
  val victimWay       = Output(UInt(log2Ceil(cfg.ways).W))
  val hitMask         = Output(UInt(cfg.ways.W))
}

/** Way partitioning logic for QoS.
  *
  * - hitMask is always all-1s: hit lookup checks all ways regardless of partition.
  * - victimWay selection priority:
  *   1. Find an invalid (free) way within the request's partition
  *   2. Use PLRU victim if it belongs to the request's partition
  *   3. Fallback: first way that belongs to the partition
  */
class LlcWayPartition(cfg: LlcConfig) extends Module {
  val io = IO(new WayPartitionIO(cfg))

  // Hit check is unrestricted - always look at all ways
  io.hitMask := Fill(cfg.ways, 1.U(1.W))

  // Determine which ways belong to the current request's partition
  val inPartition = VecInit(Seq.tabulate(cfg.ways) { w =>
    io.wayPartitionMap(w) === io.partitionId
  })

  // Priority 1: Find an invalid (free) way within the partition
  val invalidInPartition = VecInit(Seq.tabulate(cfg.ways) { w =>
    inPartition(w) && !io.wayValids(w)
  })
  val hasInvalidInPartition = invalidInPartition.asUInt.orR
  val firstInvalidWay = PriorityEncoder(invalidInPartition.asUInt)

  // Priority 2: Check if PLRU victim is within the partition
  val plruInPartition = inPartition(io.plruVictim)

  // Priority 3: Fallback - first way that belongs to the partition
  val firstPartitionWay = PriorityEncoder(inPartition.asUInt)

  // victimWay selection with priority
  io.victimWay := Mux(hasInvalidInPartition, firstInvalidWay,
                  Mux(plruInPartition, io.plruVictim,
                      firstPartitionWay))
}
