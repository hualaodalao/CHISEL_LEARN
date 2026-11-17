import chisel3._
import chisel3.util._

/** 寄存器配置命令 op：一拍最多写两个寄存器，START 组装 shadow bank 入队。 */
object FbRegOp extends ChiselEnum {
  val WRITE1, WRITE2, READ, START = Value
}

class FbRegCmd extends Bundle {
  val op       = FbRegOp()
  val reg0Addr = UInt(7.W)
  val reg1Addr = UInt(7.W)
  val data0    = UInt(32.W)
  val data1    = UInt(32.W)
  val pc       = UInt(32.W)
  val tag      = UInt(8.W)
}

class FbRegResp extends Bundle {
  val data = UInt(32.W)
  val err  = Bool()
}

/** 寄存器槽位（7 bit 地址）：配置 0x00..0x22，0x23..0x3F 保留，状态 0x40+ 只读。 */
object FbRegAddr {
  val CTRL          = 0x00
  val PERM_VEC      = 0x01
  val AUX           = 0x02
  val SRC_ADDR_LO   = 0x03
  val DST_ADDR_LO   = 0x04
  val ADDR_HI       = 0x05
  val SG_LIST_LO    = 0x06
  val SCALE_LO      = 0x07
  val SHAPE_01      = 0x08
  val SHAPE_23      = 0x09
  val SHAPE4_START0 = 0x0A
  val STARTIDX_12   = 0x0B
  val STARTIDX_34   = 0x0C
  val SRC_STRIDE1_LO = 0x0D
  val SRC_STRIDE2_LO = 0x0E
  val SRC_STRIDE3_LO = 0x0F
  val SRC_STRIDE4_LO = 0x10
  val DST_STRIDE1_LO = 0x11
  val DST_STRIDE2_LO = 0x12
  val DST_STRIDE3_LO = 0x13
  val DST_STRIDE4_LO = 0x14
  val STRIDE_HI_12  = 0x15
  val STRIDE_HI_34  = 0x16
  val SRC_STRIDE0_LO = 0x17
  val DST_STRIDE0_LO = 0x18
  val PAD_VALUE     = 0x19
  val PAD_BEFORE_01 = 0x1A
  val PAD_AFTER_01  = 0x1B
  val I2C_IN_CH     = 0x1C
  val I2C_IN_W_KERN = 0x1D
  val I2C_STRIDE_PAD = 0x1E
  val I2C_DILATION  = 0x1F
  val T2L_COLS_ROWS = 0x20
  val T2L_TILE_NROW = 0x21
  val T2L_NTCOL     = 0x22
  val NumCfg        = 0x23

  val STATUS   = 0x40
  val DONE     = 0x41
  val DONE_CNT = 0x42
  val CREDIT   = 0x43
}

/** 双 bank 寄存器接口：写落 shadow，START 组装 shadow 为 FbCmd 快照入队并切换 bank。
  * credit 初值 numPorts，START 接受 -1、引擎 done.fire +1；credit==0 或命令队列满时 START 回 err。
  * credit 按完成而非派发返还：hazard 阻塞派发期间 credit 仍占用。
  */
class FbRegIf(cfg: ForagerBeeConfig) extends Module {
  require(cfg.addressWidth <= 40,
    s"FbRegIf: addressWidth(${cfg.addressWidth}) 须 <= 40（高 8 位拼槽寄存器 ABI 上限）")
  require(cfg.maxDims <= 5, s"FbRegIf: maxDims(${cfg.maxDims}) 须 <= 5")

  private val addrW = cfg.addressWidth
  private val creditW = log2Up(cfg.numPorts + 1)
  private val occW = log2Up(cfg.queueDepth + 1)

  val io = IO(new Bundle {
    val cmd       = slave(Stream(new FbRegCmd))
    val resp      = master(Stream(new FbRegResp))
    val fbCmd     = master(Stream(new FbCmd(cfg)))
    val done      = slave(Stream(new FbDone(cfg)))
    val busy      = Input(Bool())
    val idle      = Input(Bool())
    val occupancy = Input(UInt(occW.W))
  })

  private val banks  = RegInit(VecInit(Seq.fill(2)(VecInit(Seq.fill(FbRegAddr.NumCfg)(0.U(32.W))))))
  private val shadow = RegInit(false.B)
  private val credit = RegInit(cfg.numPorts.U(creditW.W))
  private val lastTag = RegInit(0.U(8.W))
  private val lastErr = RegInit(false.B)
  private val doneCnt = RegInit(0.U(32.W))

  private val op = io.cmd.payload.op
  private val a0 = io.cmd.payload.reg0Addr
  private val a1 = io.cmd.payload.reg1Addr
  private val isW1    = op === FbRegOp.WRITE1
  private val isW2    = op === FbRegOp.WRITE2
  private val isRd    = op === FbRegOp.READ
  private val isStart = op === FbRegOp.START

  private def isCfg(a: UInt): Bool = a < FbRegAddr.NumCfg.U
  private def isStatus(a: UInt): Bool =
    a === FbRegAddr.STATUS.U || a === FbRegAddr.DONE.U ||
    a === FbRegAddr.DONE_CNT.U || a === FbRegAddr.CREDIT.U

  private val w0Bad = !isCfg(a0)
  private val w1Bad = !isCfg(a1)
  private val writeErr = (isW1 && w0Bad) || (isW2 && (w0Bad || w1Bad || a0 === a1))
  private val readErr  = isRd && !isCfg(a0) && !isStatus(a0)
  private val startErr = isStart && (credit === 0.U || !io.fbCmd.ready)
  private val err = writeErr || readErr || startErr

  private val bank = banks(shadow)
  private def statusRead(a: UInt): UInt = MuxLookup(a, 0.U(32.W))(Seq(
    FbRegAddr.STATUS.U   -> Cat(0.U(14.W), io.occupancy.pad(16), io.idle, io.busy),
    FbRegAddr.DONE.U     -> Cat(0.U(23.W), lastErr, lastTag),
    FbRegAddr.DONE_CNT.U -> doneCnt,
    FbRegAddr.CREDIT.U   -> credit.pad(32)
  ))
  private val readData = Mux(isCfg(a0), bank(a0), statusRead(a0))

  // 组合回执：命令被接受当拍给出结果，resp.ready 即 cmd.ready。
  io.resp.valid := io.cmd.valid
  io.resp.payload.data := Mux(isRd, readData, 0.U)
  io.resp.payload.err := err
  io.cmd.ready := io.resp.ready

  private val accept = io.cmd.fire
  when(accept && isW1 && !writeErr) { bank(a0) := io.cmd.payload.data0 }
  when(accept && isW2 && !writeErr) {
    bank(a0) := io.cmd.payload.data0
    bank(a1) := io.cmd.payload.data1
  }

  private def addrOf(lo: UInt, hiByte: UInt): UInt = Cat(hiByte, lo)(addrW - 1, 0)
  private def assemble(b: Vec[UInt]): FbCmd = {
    val c = Wire(new FbCmd(cfg))
    val ctrl = b(FbRegAddr.CTRL)
    val aux  = b(FbRegAddr.AUX)
    val hi   = b(FbRegAddr.ADDR_HI)
    val hi12 = b(FbRegAddr.STRIDE_HI_12)
    val hi34 = b(FbRegAddr.STRIDE_HI_34)
    c.op             := ctrl(2, 0).asTypeOf(FbOp())
    c.tag            := io.cmd.payload.tag
    c.dimCount       := ctrl(5, 3)
    c.srcFmt         := ctrl(9, 6).asTypeOf(FbDataFmt())
    c.dstFmt         := ctrl(13, 10).asTypeOf(FbDataFmt())
    c.scaleBlockSize := ctrl(15, 14)
    c.srcAddr        := addrOf(b(FbRegAddr.SRC_ADDR_LO), hi(7, 0))
    c.dstAddr        := addrOf(b(FbRegAddr.DST_ADDR_LO), hi(15, 8))
    c.sgListAddr     := addrOf(b(FbRegAddr.SG_LIST_LO), hi(23, 16))
    c.scaleAddr      := addrOf(b(FbRegAddr.SCALE_LO), hi(31, 24))
    c.sgEntryCount   := aux(15, 0)
    c.padValue       := b(FbRegAddr.PAD_VALUE)
    c.padBefore(0)   := b(FbRegAddr.PAD_BEFORE_01)(15, 0)
    c.padBefore(1)   := b(FbRegAddr.PAD_BEFORE_01)(31, 16)
    c.padAfter(0)    := b(FbRegAddr.PAD_AFTER_01)(15, 0)
    c.padAfter(1)    := b(FbRegAddr.PAD_AFTER_01)(31, 16)
    c.im2colInShape(0) := b(FbRegAddr.I2C_IN_CH)(15, 0)
    c.im2colInShape(1) := b(FbRegAddr.I2C_IN_CH)(31, 16)
    c.im2colInShape(2) := b(FbRegAddr.I2C_IN_W_KERN)(15, 0)
    c.im2colKernel(0)  := b(FbRegAddr.I2C_IN_W_KERN)(23, 16)
    c.im2colKernel(1)  := b(FbRegAddr.I2C_IN_W_KERN)(31, 24)
    c.im2colStride(0)  := b(FbRegAddr.I2C_STRIDE_PAD)(7, 0)
    c.im2colStride(1)  := b(FbRegAddr.I2C_STRIDE_PAD)(15, 8)
    c.im2colPad(0)     := b(FbRegAddr.I2C_STRIDE_PAD)(23, 16)
    c.im2colPad(1)     := b(FbRegAddr.I2C_STRIDE_PAD)(31, 24)
    c.im2colDilation(0) := b(FbRegAddr.I2C_DILATION)(7, 0)
    c.im2colDilation(1) := b(FbRegAddr.I2C_DILATION)(15, 8)
    c.t2lMatCols     := b(FbRegAddr.T2L_COLS_ROWS)(15, 0)
    c.t2lTileRows    := b(FbRegAddr.T2L_COLS_ROWS)(31, 16)
    c.t2lTileCols    := b(FbRegAddr.T2L_TILE_NROW)(15, 0)
    c.t2lNumTileRows := b(FbRegAddr.T2L_TILE_NROW)(31, 16)
    c.t2lNumTileCols := b(FbRegAddr.T2L_NTCOL)(15, 0)
    val shapes = Seq(b(FbRegAddr.SHAPE_01)(15, 0), b(FbRegAddr.SHAPE_01)(31, 16),
      b(FbRegAddr.SHAPE_23)(15, 0), b(FbRegAddr.SHAPE_23)(31, 16),
      b(FbRegAddr.SHAPE4_START0)(15, 0))
    val starts = Seq(b(FbRegAddr.SHAPE4_START0)(31, 16),
      b(FbRegAddr.STARTIDX_12)(15, 0), b(FbRegAddr.STARTIDX_12)(31, 16),
      b(FbRegAddr.STARTIDX_34)(15, 0), b(FbRegAddr.STARTIDX_34)(31, 16))
    val srcLo = Seq(b(FbRegAddr.SRC_STRIDE0_LO), b(FbRegAddr.SRC_STRIDE1_LO),
      b(FbRegAddr.SRC_STRIDE2_LO), b(FbRegAddr.SRC_STRIDE3_LO), b(FbRegAddr.SRC_STRIDE4_LO))
    val dstLo = Seq(b(FbRegAddr.DST_STRIDE0_LO), b(FbRegAddr.DST_STRIDE1_LO),
      b(FbRegAddr.DST_STRIDE2_LO), b(FbRegAddr.DST_STRIDE3_LO), b(FbRegAddr.DST_STRIDE4_LO))
    val srcHi = Seq(aux(23, 16), hi12(7, 0), hi12(15, 8), hi34(7, 0), hi34(15, 8))
    val dstHi = Seq(aux(31, 24), hi12(23, 16), hi12(31, 24), hi34(23, 16), hi34(31, 24))
    val pv = b(FbRegAddr.PERM_VEC)
    for (d <- 0 until cfg.maxDims) {
      c.shape(d)       := shapes(d)
      c.srcStartIdx(d) := starts(d)
      c.srcStride(d)   := addrOf(srcLo(d), srcHi(d))
      c.dstStride(d)   := addrOf(dstLo(d), dstHi(d))
      c.permVec(d)     := pv(3 * d + 2, 3 * d)
    }
    c
  }

  private val startFire = accept && isStart && !startErr
  io.fbCmd.valid := startFire
  io.fbCmd.payload := assemble(bank)
  when(startFire) { shadow := !shadow }

  io.done.ready := true.B
  when(io.done.fire) {
    lastTag := io.done.payload.tag
    lastErr := io.done.payload.err
    doneCnt := doneCnt + 1.U
  }
  credit := credit - startFire.asUInt + io.done.fire.asUInt
}
