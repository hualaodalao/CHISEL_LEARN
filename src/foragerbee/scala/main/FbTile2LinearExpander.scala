/** FbTile2LinearExpander —— TILE2LINEAR → 4D COPY 命令展开器（纯组合逻辑）。
  *
  * 当输入命令的 op === FbOp.TILE2LINEAR 时，将其展开为等价的 4D COPY 命令：
  *   - dim3 = tile-row index (Mt)
  *   - dim2 = tile-col index (Nt)
  *   - dim1 = intra-tile row (Tm)
  *   - dim0 = intra-tile col (Tn)
  *
  * 数学映射（2D M×N 行主序 → tile-row-major 线性）：
  *   srcStride = [e, N*e, Tn*e, Tm*N*e]
  *   dstStride = [e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
  * 其中 e = 1 << elemBytesLog2。
  *
  * 非法参数保护：Tn/Tm/Nt/Mt 任一为 0 时，置 dimCount := 0（触发引擎 cBad 报错）。
  *
  * @param cfg ForagerBee 配置
  */

import chisel3._
import chisel3.util._

class FbTile2LinearExpander(cfg: ForagerBeeConfig) extends Module {
  val io = IO(new Bundle {
    val in  = slave(Stream(new FbCmd(cfg)))
    val out = master(Stream(new FbCmd(cfg)))
  })

  // --- 纯组合握手（零延迟透传） ---
  io.out.valid := io.in.valid
  io.in.ready  := io.out.ready

  // --- 默认透传所有字段 ---
  io.out.payload := io.in.payload

  // --- TILE2LINEAR 展开逻辑 ---
  private val cmd = io.in.payload
  private val addrW = cfg.addressWidth

  when(cmd.op === FbOp.TILE2LINEAR) {
    // 提取参数（16-bit → addrW-bit 扩展，防溢出）
    val N  = cmd.t2lMatCols.pad(addrW)
    val Tm = cmd.t2lTileRows.pad(addrW)
    val Tn = cmd.t2lTileCols.pad(addrW)
    val Mt = cmd.t2lNumTileRows.pad(addrW)
    val Nt = cmd.t2lNumTileCols.pad(addrW)

    // e = 1 << elemBytesLog2（字节粒度）
    val e = (1.U(addrW.W)) << cmd.elemBytesLog2

    // 中间乘积
    val Ne    = N * e          // N * e
    val Tne   = Tn * e         // Tn * e
    val TmNe  = Tm * Ne        // Tm * N * e
    val TmTne = Tm * Tne       // Tm * Tn * e
    val NtTmTne = Nt * TmTne   // Nt * Tm * Tn * e

    // 改写操作码为 COPY
    io.out.payload.op       := FbOp.COPY
    io.out.payload.dimCount := 4.U

    // shape: [Tn, Tm, Nt, Mt]
    io.out.payload.shape(0) := cmd.t2lTileCols
    io.out.payload.shape(1) := cmd.t2lTileRows
    io.out.payload.shape(2) := cmd.t2lNumTileCols
    io.out.payload.shape(3) := cmd.t2lNumTileRows

    // srcStride: [e, N*e, Tn*e, Tm*N*e]
    io.out.payload.srcStride(0) := e
    io.out.payload.srcStride(1) := Ne
    io.out.payload.srcStride(2) := Tne
    io.out.payload.srcStride(3) := TmNe

    // dstStride: [e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
    io.out.payload.dstStride(0) := e
    io.out.payload.dstStride(1) := Tne
    io.out.payload.dstStride(2) := TmTne
    io.out.payload.dstStride(3) := NtTmTne

    // 非法参数保护：任一为 0 则置 dimCount=0 → 引擎报 err
    when(cmd.t2lMatCols === 0.U || cmd.t2lTileCols === 0.U || cmd.t2lTileRows === 0.U ||
         cmd.t2lNumTileCols === 0.U || cmd.t2lNumTileRows === 0.U) {
      io.out.payload.dimCount := 0.U
    }
  }
}
