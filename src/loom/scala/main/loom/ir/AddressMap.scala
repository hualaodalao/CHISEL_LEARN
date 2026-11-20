package loom.ir

/** Effective slave address windows.
  *
  * Slaves either all declare `base` and `size`, or none do; the second case
  * keeps the historical layout of one 4 KB window per slave in endpoint
  * order. C0 rejects mixed declarations.
  */
object AddressMap {
  final case class Window(id: String, index: Int, base: Long, size: Long) {
    def end: Long = base + size - 1
  }

  val DefaultSize: Long = 1L << LoomDefaults.RouteShift

  def declared(slaves: Seq[Endpoint]): Boolean =
    slaves.exists(e => e.base >= 0 || e.size > 0)

  def windows(slaves: Seq[Endpoint]): Seq[Window] = {
    val explicit = declared(slaves)
    slaves.zipWithIndex.map { case (e, i) =>
      if (explicit) Window(e.id, i, e.base, e.size)
      else Window(e.id, i, i.toLong * DefaultSize, DefaultSize)
    }
  }

  def covers(addrWidth: Int, windows: Seq[Window]): Boolean =
    BigInt(1) << addrWidth >= BigInt(windows.length) * BigInt(DefaultSize)
}
