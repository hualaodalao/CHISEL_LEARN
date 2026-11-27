import chisel3._
import chisel3.util._

class WayPartitionIO(cfg: LlcConfig) extends Bundle {
  val partitionId     = Input(UInt(log2Ceil(cfg.wayPartitions).W))
  val plruVictim      = Input(UInt(log2Ceil(cfg.ways).W))
  val wayValids       = Input(Vec(cfg.ways, Bool()))
  val busyMask        = Input(UInt(cfg.ways.W))
  val wayPartitionMap = Input(Vec(cfg.ways, UInt(log2Ceil(cfg.wayPartitions).W)))
  val victimWay       = Output(UInt(log2Ceil(cfg.ways).W))
  // Falls back to ignoring busyMask when every way of the partition is busy
  val hitMask         = Output(UInt(cfg.ways.W))
}

/** Way partitioning logic for QoS.
  *
  * - hitMask is always all-1s: hit lookup checks all ways regardless of partition.
  * - victimWay selection priority, among the ways of the request's partition
  *   that are not in busyMask (being refilled by an MSHR entry):
  *   1. Find an invalid (free) way
  *   2. Use PLRU victim
  *   3. Fallback: first such way
  */
class LlcWayPartition(cfg: LlcConfig) extends Module {
  val io = IO(new WayPartitionIO(cfg))

  // Hit check is unrestricted - always look at all ways
  io.hitMask := Fill(cfg.ways, 1.U(1.W))

  // Determine which ways belong to the current request's partition
  val inPartition = VecInit(Seq.tabulate(cfg.ways) { w =>
    io.wayPartitionMap(w) === io.partitionId
  })

  val partitionMask = inPartition.asUInt
  val validMask     = io.wayValids.asUInt

  // Ways of the partition that no MSHR entry is refilling
  val availMask    = partitionMask & ~io.busyMask
  val invalidAvail = availMask & ~validMask
  val plruAvail    = availMask(io.plruVictim)

  val plainMask    = partitionMask & ~validMask

  val pick = Mux(invalidAvail.orR, PriorityEncoder(invalidAvail),
             Mux(plruAvail, io.plruVictim, PriorityEncoder(availMask)))
  val fallback = Mux(plainMask.orR, PriorityEncoder(plainMask), PriorityEncoder(partitionMask))

  io.victimWay := Mux(availMask.orR, pick, fallback)
}
