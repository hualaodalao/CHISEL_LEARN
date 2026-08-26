/** FbTile2LinearExpander —— TILE2LINEAR / LINEAR2TILE → 4D COPY 命令展开器（纯组合逻辑）。
  *
  * 当输入命令的 op === FbOp.TILE2LINEAR 或 FbOp.LINEAR2TILE 时，将其展开为等价的 4D COPY 命令：
  *   - dim3 = tile-row index (Mt)
  *   - dim2 = tile-col index (Nt)
  *   - dim1 = intra-tile row (Tm)
  *   - dim0 = intra-tile col (Tn)
  *
  * 两个操作数学上完全对称，共用 shape=[Tn,Tm,Nt,Mt]，仅 src/dst stride 互换：
  *   tiled 大矩阵 stride = [e, N*e, Tn*e, Tm*N*e]
  *   线性连续     stride = [e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
  * 其中 e = 1 << elemBytesLog2。
  *   - TILE2LINEAR：src = tiled 大矩阵，dst = 线性（大矩阵按 tile 取出→线性连续）
  *   - LINEAR2TILE：src = 线性，dst = tiled 大矩阵（线性连续读出→按 tile 布局写回）
  *
  * 非法参数保护：Tn/Tm/Nt/Mt/N 任一为 0 时，置 dimCount := 0（触发引擎 cBad 报错）。
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

  // --- TILE2LINEAR / LINEAR2TILE 展开逻辑 ---
  private val cmd = io.in.payload
  private val addrW = cfg.addressWidth

  private val isT2L = cmd.op === FbOp.TILE2LINEAR
  private val isL2T = cmd.op === FbOp.LINEAR2TILE

  when(isT2L || isL2T) {
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

    // tiled 大矩阵 stride 组：[e, N*e, Tn*e, Tm*N*e]
    // linear 连续 stride 组：[e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
    // 两组乘法链长度不同 → Chisel 推断位宽不同（99-bit vs 131-bit）；
    // 统一截断到 addrW 位后再 VecInit，使 Mux 两侧位宽一致
    //（这些值最终连到 UInt(addrW.W) 的 srcStride/dstStride，本就截断到 addrW，语义一致）。
    def toAddrW(x: UInt): UInt = x(addrW - 1, 0)
    val tiledStride = VecInit(toAddrW(e), toAddrW(Ne), toAddrW(Tne), toAddrW(TmNe))
    val linearStride = VecInit(toAddrW(e), toAddrW(Tne), toAddrW(TmTne), toAddrW(NtTmTne))

    // 改写操作码为 COPY
    io.out.payload.op       := FbOp.COPY
    io.out.payload.dimCount := 4.U

    // shape: [Tn, Tm, Nt, Mt]
    io.out.payload.shape(0) := cmd.t2lTileCols
    io.out.payload.shape(1) := cmd.t2lTileRows
    io.out.payload.shape(2) := cmd.t2lNumTileCols
    io.out.payload.shape(3) := cmd.t2lNumTileRows

    // src/dst stride 按 op 选择：
    //   TILE2LINEAR：src = tiled 大矩阵，dst = 线性
    //   LINEAR2TILE：src = 线性，dst = tiled 大矩阵
    val srcSel = Mux(isT2L, tiledStride, linearStride)
    val dstSel = Mux(isT2L, linearStride, tiledStride)
    for (i <- 0 until 4) {
      io.out.payload.srcStride(i) := srcSel(i)
      io.out.payload.dstStride(i) := dstSel(i)
    }

    // 非法参数保护：任一为 0 则置 dimCount=0 → 引擎报 err（两个 op 共用）
    when(cmd.t2lMatCols === 0.U || cmd.t2lTileCols === 0.U || cmd.t2lTileRows === 0.U ||
         cmd.t2lNumTileCols === 0.U || cmd.t2lNumTileRows === 0.U) {
      io.out.payload.dimCount := 0.U
    }
  }
}

