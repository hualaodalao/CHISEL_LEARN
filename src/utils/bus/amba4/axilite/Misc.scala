/** SpinalHDL spinal.lib.bus.amba4.axilite.Misc（AxiLite4Utils）的 Chisel 移植版。
  *
  * 提供 Axi4 ↔ AxiLite4 的互转：
  *   - AxiLite4 → Axi4：toAxi() / fromAxi()（完整移植）
  *   - Axi4 → AxiLite4：toLite() / fromLite()（部分移植）
  *
  * toLite() 的限制（与原版路径的差异）：
  *   - Axi4IdRemover 未移植 → 要求 useId = false；
  *   - Axi4Unburster 未移植 → 要求 useLen = false；
  *   - burst 信号无法被 driveWeak 丢弃（与原版语义一致），useBurst 需为 false。
  */

import chisel3._
import chisel3.util._

object AxiLite4Utils {

  private def toLiteConfig(config: Axi4Config) = {
    AxiLite4Config(
      addressWidth = config.addressWidth,
      dataWidth = config.dataWidth,
      readIssuingCapability = config.readIssuingCapability,
      writeIssuingCapability = config.writeIssuingCapability,
      combinedIssuingCapability = config.combinedIssuingCapability,
      readDataReorderingDepth = config.readDataReorderingDepth
    )
  }

  private def toAxiConfig(config: AxiLite4Config) = {
    Axi4Config(
      addressWidth = config.addressWidth,
      dataWidth = config.dataWidth,
      useId = false,
      useRegion = false,
      useBurst = false,
      useLock = false,
      useCache = false,
      useSize = false,
      useQos = false,
      useLen = false,
      useResp = true,
      useProt = true,
      useStrb = true,
      readIssuingCapability = config.readIssuingCapability,
      writeIssuingCapability = config.writeIssuingCapability,
      combinedIssuingCapability = config.combinedIssuingCapability,
      readDataReorderingDepth = config.readDataReorderingDepth
    )
  }

  /** 双方同时存在的可选字段才连接 */
  private def zipConnect[T <: Data](to: Option[T], from: Option[T]): Unit =
    for (t <- to; f <- from) t := f

  // Axi ---> AxiLite
  implicit class Axi4Rich(axi: Axi4) {
    def toLite(): AxiLite4 = {
      val axiLite = Wire(AxiLite4(toLiteConfig(axi.config)))
      axiLite << axi.toReadOnly().toLite()
      axiLite << axi.toWriteOnly().toLite()
      axiLite
    }

    def fromLite(): AxiLite4 = {
      val axiLite = Wire(AxiLite4(toLiteConfig(axi.config)))
      axiLite.toAxi() >> axi
      axiLite
    }
  }

  implicit class Axi4ReadOnlyRich(axi: Axi4ReadOnly) {
    def toLite(): AxiLite4ReadOnly = {
      require(!axi.config.useId, "AxiLite4Utils.toLite: Axi4IdRemover 未移植，仅支持 useId=false 的配置")
      require(!axi.config.useLen, "AxiLite4Utils.toLite: Axi4Unburster 未移植，仅支持 useLen=false 的配置")

      // 先剥掉 size（AxiLite 固定全宽访问）
      val axiNoSize = if (axi.config.useSize) {
        val noSize = Wire(Axi4ReadOnly(axi.config.copy(useSize = false)))
        noSize.ar.arbitrationFrom(axi.ar)
        noSize.ar.payload.assignSomeByName(axi.ar.payload)

        axi.r.arbitrationFrom(noSize.r)
        axi.r.payload.data := noSize.r.payload.data
        zipConnect(axi.r.payload.id, noSize.r.payload.id)
        zipConnect(axi.r.payload.resp, noSize.r.payload.resp)
        zipConnect(axi.r.payload.last, noSize.r.payload.last)
        zipConnect(axi.r.payload.user, noSize.r.payload.user)
        noSize
      } else {
        axi
      }

      val axiLite = Wire(AxiLite4ReadOnly(toLiteConfig(axi.config)))
      val axiMinimal = Wire(Axi4ReadOnly(toAxiConfig(axiLite.config)))

      axiNoSize >> axiMinimal

      axiLite.ar.arbitrationFrom(axiMinimal.ar)
      axiLite.ar.payload.addr := axiMinimal.ar.payload.addr
      axiLite.ar.payload.prot := axiMinimal.ar.payload.prot.get

      axiMinimal.r.arbitrationFrom(axiLite.r)
      axiMinimal.r.payload.data := axiLite.r.payload.data
      axiMinimal.r.payload.resp.foreach(_ := axiLite.r.payload.resp)
      axiMinimal.r.payload.last.foreach(_ := true.B)

      axiLite
    }
  }

  implicit class Axi4WriteOnlyRich(axi: Axi4WriteOnly) {
    def toLite(): AxiLite4WriteOnly = {
      require(!axi.config.useId, "AxiLite4Utils.toLite: Axi4IdRemover 未移植，仅支持 useId=false 的配置")
      require(!axi.config.useLen, "AxiLite4Utils.toLite: Axi4Unburster 未移植，仅支持 useLen=false 的配置")

      // 先剥掉 size（AxiLite 固定全宽访问）
      val axiNoSize = if (axi.config.useSize) {
        val noSize = Wire(Axi4WriteOnly(axi.config.copy(useSize = false)))
        noSize.aw.arbitrationFrom(axi.aw)
        noSize.aw.payload.assignSomeByName(axi.aw.payload)

        noSize.w.arbitrationFrom(axi.w)
        noSize.w.payload.data := axi.w.payload.data
        zipConnect(noSize.w.payload.strb, axi.w.payload.strb)
        zipConnect(noSize.w.payload.user, axi.w.payload.user)
        zipConnect(noSize.w.payload.last, axi.w.payload.last)
        zipConnect(noSize.w.payload.id, axi.w.payload.id)

        axi.b.arbitrationFrom(noSize.b)
        zipConnect(axi.b.payload.id, noSize.b.payload.id)
        zipConnect(axi.b.payload.resp, noSize.b.payload.resp)
        zipConnect(axi.b.payload.user, noSize.b.payload.user)
        noSize
      } else {
        axi
      }

      val axiLite = Wire(AxiLite4WriteOnly(toLiteConfig(axi.config)))
      val axiMinimal = Wire(Axi4WriteOnly(toAxiConfig(axiLite.config)))

      axiNoSize >> axiMinimal

      axiLite.aw.arbitrationFrom(axiMinimal.aw)
      axiLite.aw.payload.addr := axiMinimal.aw.payload.addr
      axiLite.aw.payload.prot := axiMinimal.aw.payload.prot.get

      axiLite.w.arbitrationFrom(axiMinimal.w)
      axiLite.w.payload.data := axiMinimal.w.payload.data
      axiLite.w.payload.strb := axiMinimal.w.payload.strb.get

      axiMinimal.b.arbitrationFrom(axiLite.b)
      axiMinimal.b.payload.resp.foreach(_ := axiLite.b.payload.resp)

      axiLite
    }
  }

  // AxiLite ---> Axi
  implicit class AxiLite4Rich(axiLite: AxiLite4) {
    def toAxi(): Axi4 = {
      val axi = Wire(Axi4(toAxiConfig(axiLite.config)))

      axi.aw.arbitrationFrom(axiLite.aw)
      axi.aw.payload.addr := axiLite.aw.payload.addr
      axi.aw.payload.prot.foreach(_ := axiLite.aw.payload.prot)
      axi.w.arbitrationFrom(axiLite.w)
      axi.w.payload.data := axiLite.w.payload.data
      axi.w.payload.strb.foreach(_ := axiLite.w.payload.strb)
      axi.w.payload.last.foreach(_ := true.B)
      axiLite.b.arbitrationFrom(axi.b)
      axiLite.b.payload.resp := axi.b.payload.resp.get

      axi.ar.arbitrationFrom(axiLite.ar)
      axi.ar.payload.addr := axiLite.ar.payload.addr
      axi.ar.payload.prot.foreach(_ := axiLite.ar.payload.prot)
      axiLite.r.arbitrationFrom(axi.r)
      axiLite.r.payload.data := axi.r.payload.data
      axiLite.r.payload.resp := axi.r.payload.resp.get

      axi
    }

    def fromAxi(): Axi4 = {
      val axi = Wire(Axi4(toAxiConfig(axiLite.config)))
      axi.toLite() >> axiLite
      axi
    }
  }

  implicit class AxiLite4ReadOnlyRich(axiLite: AxiLite4ReadOnly) {
    def toAxi(): Axi4ReadOnly = {
      val axi = Wire(Axi4ReadOnly(toAxiConfig(axiLite.config)))

      axi.ar.arbitrationFrom(axiLite.ar)
      axi.ar.payload.addr := axiLite.ar.payload.addr
      axi.ar.payload.prot.foreach(_ := axiLite.ar.payload.prot)

      axiLite.r.arbitrationFrom(axi.r)
      axiLite.r.payload.data := axi.r.payload.data
      axiLite.r.payload.resp := axi.r.payload.resp.get

      axi
    }
  }

  implicit class AxiLite4WriteOnlyRich(axiLite: AxiLite4WriteOnly) {
    def toAxi(): Axi4WriteOnly = {
      val axi = Wire(Axi4WriteOnly(toAxiConfig(axiLite.config)))

      axi.aw.arbitrationFrom(axiLite.aw)
      axi.aw.payload.addr := axiLite.aw.payload.addr
      axi.aw.payload.prot.foreach(_ := axiLite.aw.payload.prot)

      axi.w.arbitrationFrom(axiLite.w)
      axi.w.payload.data := axiLite.w.payload.data
      axi.w.payload.strb.foreach(_ := axiLite.w.payload.strb)
      axi.w.payload.last.foreach(_ := true.B)

      axiLite.b.arbitrationFrom(axi.b)
      axiLite.b.payload.resp := axi.b.payload.resp.get

      axi
    }
  }
}
