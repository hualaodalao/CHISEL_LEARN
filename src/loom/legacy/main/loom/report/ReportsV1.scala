package loom.report

import loom.ir._
import loom.rtl.{NetLinkSpec, NetworkParams, PartitionParams}

/** MIG-11 (S6): the v1 four-channel partition ports report, isolated together
  * with `loom/rtl/LmPartition.scala` and `NetworkParams.scala`. Kept one
  * version for comparison (LmPartitionSpec asserts it), then deleted with the
  * legacy source set. The CHI caliber replacement is `Reports.portsJsonV2`.
  */
object ReportsV1 {

  /** Stable port contract for one partition. Names and ordering are derived
    * from the IR only, so the file is reproducible across emissions.
    */
  def portsJson(
      ir: Ir,
      net: NetworkParams,
      pp: PartitionParams,
      configHash: String
  ): JValue = {
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")

    def epObj(id: String, router: String, port: Int): JValue =
      JObj(Vector(
        "id" -> JStr(id),
        "router" -> JStr(router),
        "port" -> JNum(BigDecimal(port))
      ))

    def boundaryEntry(l: NetLinkSpec, dir: String): (Int, JValue) = {
      val local = if (dir == "out") l.fromRouter else l.toRouter
      val peer = if (dir == "out") l.toRouter else l.fromRouter
      val peerClock = ir.routers.find(_.name == net.routers(peer).name).map(_.clock).getOrElse("?")
      l.id -> JObj(Vector(
        "link" -> JNum(BigDecimal(l.id)),
        "direction" -> JStr(dir),
        "localRouter" -> JStr(net.routers(local).name),
        "peerRouter" -> JStr(net.routers(peer).name),
        "localPort" -> JNum(BigDecimal(if (dir == "out") l.fromPort else l.toPort)),
        "peerPort" -> JNum(BigDecimal(if (dir == "out") l.toPort else l.fromPort)),
        "width" -> JNum(BigDecimal(l.width)),
        "pipe" -> JNum(BigDecimal(l.pipe)),
        "localClock" -> JStr(pp.clock),
        "peerClock" -> JStr(peerClock),
        "async" -> JBool(l.async)
      ))
    }

    val boundaryObjs = (pp.outLinks.map(boundaryEntry(_, "out")) ++
      pp.inLinks.map(boundaryEntry(_, "in")))
      .sortBy(_._1)
      .map(_._2)

    JObj(Vector(
      "schema" -> JStr("loom/ports/v1"),
      "name" -> JStr(ir.meta.name),
      "partition" -> JStr(pp.name),
      "configHash" -> JStr(configHash),
      "partitionHash" -> JStr(pp.hash),
      "clock" -> JStr(pp.clock),
      "boundaryPipe" -> JNum(BigDecimal(pp.pipe)),
      "masters" -> JArr(pp.masters.map { i =>
        epObj(masters(i).id, net.routers(net.masterRouter(i)).name, net.masterPort(i))
      }.toVector),
      "slaves" -> JArr(pp.slaves.map { j =>
        epObj(slaves(j).id, net.routers(net.slaveRouter(j)).name, net.slavePort(j))
      }.toVector),
      "boundary" -> JArr(boundaryObjs.toVector)
    ))
  }
}