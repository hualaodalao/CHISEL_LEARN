package loom.ir

object LoomDefaults {
  /** Default 4 KB stride used when slaves do not declare base/size.
    * The same shift is kept as the alias decode for such configurations.
    */
  val RouteShift: Int = 12

  /** AXI4 transaction ID width carried end-to-end by the fabric. */
  val AxiIdWidth: Int = 4

  /** Fixed class order used by the router's WRR schedule. */
  val QosOrder: Seq[String] = Seq("ctl", "lat", "bulk")

  /** VC id = class * 2 + dateline half, so three classes need 3 bits. */
  val VcWidth: Int = 3
  val VcHalves: Int = 2

  def vcOf(classIndex: Int, half: Int = 0): Int = classIndex * VcHalves + half

  /** Used when the IR omits the qos block. */
  val DefaultQos: Seq[QosClass] = Seq(
    QosClass("ctl", vcs = 1, depth = 4, arbitration = "wrr", weight = 4, maxPacketBeats = 1),
    QosClass("lat", vcs = 1, depth = 8, arbitration = "wrr", weight = 2, maxPacketBeats = 2),
    QosClass("bulk", vcs = 2, depth = 16, arbitration = "wrr", weight = 1, maxPacketBeats = 4)
  )

  /** G7 fixes the control class at a single beat; latency and bulk classes
    * may carry a full packet. Used when a class omits maxPacketBeats.
    */
  def defaultMaxPacket(name: String): Int = name match {
    case "ctl" => 1
    case "lat" => 2
    case _     => 4
  }

  /** Aging threshold in cycles before a class is force-granted. */
  val MaxAge: Int = 8
}

