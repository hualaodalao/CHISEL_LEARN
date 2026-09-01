/** HiveCoreDmaScaleWr — C 侧量化 scale 写回 DMA（P3b，dmaCScale 通道）。
  *
  * 职责：量化输出（outFmt 1xx）运行时，cDma（WrOnly）每写出一行量化密集行
  * 即同拍产出一行 E8M0 块 scale（cvt.io.scaleRow，宽 cScaleRowW = totalN/8 字节），
  * 经 Scratchpad cScaleFifo 缓冲后由本模块写到外部存储（基址 = regFile.cScaleAddr）。
  *
  * 遍历几何（与 cDma 的 C 数据写回同序，保证 scale 行与量化数据行逐行对应）：
  *   内层沿 M 方向（实际 M 行 = regFile.m，每行一拍、地址 + cScaleRowAddressOffset
  *   = ceil(n/blk) 字节），外层扫 nTile 个 N tile（轮间跳 cScaleColTileAddressOffset
  *   = totalN/blk 字节）。
  *
  * 【地址模型——「基址 + 轮偏移」线性模型（刻意不复制 cDma sNEXT_N 的三角累积）】
  *   cDma 数据通道的块间跳转用「curAddr += colAddr; colAddr += offset」三角累积，
  *   是已知遗留问题（禁动区）；本模块为新通道，直接采用线性模型：
  *   colAddr 寄存器只存「下一轮的累计列偏移」，轮间跳转时
  *     curAddr := baseAddr + colAddr;  colAddr := colAddr + colTileOffset
  *   每轮起始地址恒等于 baseAddr + nt*colTileOffset，无累积误差、易对账。
  *
  * 反压/在途记账（写通道简化版）：
  *   - req 仅在 sTRANSFER 且 bufPop 有数据时发出，且被在途上限（cScaleFifo 深度）
  *     门控，防 rsp 延迟堆积超出计数位宽；
  *   - ostCredit 记在途写拍数（req.fire +1 / rsp.fire −1），sDONE 等 credit==0
  *     后发 done 脉冲回 sIDLE（写通道无回读数据，完成判据只看在途排空）。
  *
  * start 语义：io.start 任意状态均可重新装载（与 RdOnly/WrOnly/ScaleRd 同构，
  * 异常路径强制重启契约对齐）：重载拍同周期锁存新遍历参数（下方 |io.start 锁存块）
  * 并复位计数/credit/错误。正常流程 start 仅出现在 sIDLE，其余状态重载分支为死路、
  * 行为不变；异常路径（Executor config-err 回 idle 后下次 EXECUTE）可强制重启。
  * 顶层 start 门控 = executePulse & cQuantRun（cQuantRun = outFmtEff∈{100,101,110} 枚举，
  * 与 WrOnly 量化判据同源），非量化运行本模块永不启动、不向外部发任何请求。
  *
  * 【config-err 场景行为契约（评审留痕）】量化配置违约（blk>totalN/不整除、mxfp4
  * 奇 n）时顶层 start 仍随 executePulse & cQuantRun 拉高（违约由 Executor 同拍置
  * errState 拦截，start 门不感知），本模块照常进入 sTRANSFER；但 Executor 不进计算、
  * cScaleFifo 恒空，本模块停留于 sTRANSFER（busy 恒高、done 不发），仅靠下次
  * EXECUTE 的 start 重载恢复（重载分支复位计数/credit 并重锁存参数）。功能无害：
  * FIFO 空时不发任何数据拍（负向用例已验证）。因此 io.done 不得在 config-err 场景
  * 接入完成链（顶层完成序只经 executor.io.dmaCBusy OR 门，busy 高电不阻断 err 上报）。
  *
  * @param cfg HiveCore 配置（行宽固定 = cfg.cScaleRowW，每 8 元素块 1 字节）
  */

import chisel3._
import chisel3.util._

class HiveCoreDmaScaleWr(cfg: HiveCoreConfig) extends Module {
  private val rowW = cfg.cScaleRowW
  private val depth = cfg.scaleBufferDepth

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：cScaleFifo 解耦，启动后自主搬完所有行）
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志（rsp.err 锁存）
    val dmaExtWrIF = HiveCoreDMAExtWriteOnlyIF(cfg, rowW)  // 外部写通道（行宽 = cScaleRowW）
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // nTile + cScale 行/列步进
    val baseAddr = Input(UInt(cfg.addrWidth.W))      // scale 写回基址（← regFile.cScaleAddr）
    val regFile = Input(HiveCoreRegister(cfg))       // 仅使用 m（内层行数）
    val bufPop = slave(Stream(UInt(rowW.W)))         // 从 cScaleFifo pop 数据
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sTRANSFER = 1.U(2.W)   // pop FIFO 发写拍、推进地址
  val sNEXT_N   = 2.U(2.W)   // N tile 轮间跳转（线性模型）
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址（行首址）
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 「下一轮」累计列偏移（线性模型）
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每行地址步进
  val lineTarget  = RegInit(0.U(16.W))         // 内层行数 = regFile.m
  val lineCounter = RegInit(0.U(16.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val ostCredit = RegInit(0.U(log2Up(depth + 1).W))  // 在途写拍数
  val errReg = RegInit(false.B)

  // ==========================================================================
  // 在途 credit 记账 + rsp.err 锁存（与 WrOnly 数据通道同模式）
  // ==========================================================================
  when(state === sIDLE) {
    ostCredit := 0.U
    errReg := false.B
  }.otherwise {
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.req.fire) {
      ostCredit := ostCredit + 1.U
    }.elsewhen(io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit - 1.U
    }
    when(io.dmaExtWrIF.rsp.fire) {
      when(io.dmaExtWrIF.rsp.payload.err) {
        errReg := true.B
      }
    }
  }

  // ==========================================================================
  // 默认输出驱动
  //   req 被在途上限（FIFO 深度）门控：防外部 rsp 长延迟堆积超出计数位宽
  // ==========================================================================
  io.dmaExtWrIF.req.valid         := (state === sTRANSFER) & io.bufPop.valid & (ostCredit =/= depth.U)
  io.dmaExtWrIF.req.payload.addr  := curAddr
  io.dmaExtWrIF.req.payload.data  := io.bufPop.payload
  io.bufPop.ready                 := (state === sTRANSFER) & io.dmaExtWrIF.req.ready
  io.dmaExtWrIF.rsp.ready         := true.B

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg

  switch(state) {
    // --- sIDLE: 等待 start（锁存逻辑在下方 state===sIDLE|start 块统一处理）---
    is(sIDLE) {
      errReg := false.B
      lineCounter := 0.U
      // 防重复 start 竞态：重载拍（非 sIDLE 收到 start）已直达 sTRANSFER，
      // 仅当本拍确在 sIDLE 时才允许 start 推进（同 RdOnly 留痕）
      when(io.start && state === sIDLE) {
        state := sTRANSFER
      }
    }

    // --- sTRANSFER: 每行一拍，pop FIFO 发写拍并推进地址 ---
    is(sTRANSFER) {
      // start 重载（契约对齐，同 RdOnly sTRANSFER）：途中强制重启 →
      // 同拍重载参数（下方 |io.start 锁存块）并复位计数/credit；
      // 正常流程 start 只在 sIDLE 出现，本分支死路、行为不变
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(io.dmaExtWrIF.req.fire) {
        when(lineCounter === lineTarget - 1.U) {
          lineCounter := 0.U
          state := sNEXT_N
        }.otherwise {
          lineCounter := lineCounter + 1.U
          curAddr := curAddr + rowStep
        }
      }
    }

    // --- sNEXT_N: N tile 轮间跳转（线性模型：基址 + 轮偏移）---
    is(sNEXT_N) {
      // start 重载（收紧契约）：sNEXT_N 为单拍过渡态，重载拍直接回 sTRANSFER
      // 起跑（|io.start 锁存块已把 curAddr/colAddr/计数复位回首轮参数）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(nCnt === io.calcConfig.nTile - 1.U) {
        state := sDONE
      }.otherwise {
        // 线性模型：下一轮起始 = base + (nt+1)*colTileOffset；
        // colAddr 存累计轮偏移（锁存初值 = 1 轮偏移），无三角累积
        nCnt := nCnt + 1.U
        curAddr := io.baseAddr + colAddr
        colAddr := colAddr + io.calcConfig.cScaleColTileAddressOffset
        state := sTRANSFER
      }
    }

    // --- sDONE: 等在途写拍排空后发完成脉冲，回 IDLE ---
    is(sDONE) {
      // start 重载（契约对齐，同 RdOnly sDONE）：绕过 sIDLE 时 credit 归零
      // 不执行，故重载另需 ostCredit := 0.U（last-connect 覆盖上方
      // state=/=sIDLE 分支的同拍增减）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }

  // ==========================================================================
  // 遍历参数锁存（switch 外公共位置，与 RdOnly/ScaleRd 同模式）：
  // sIDLE 每拍刷新；重载拍（任意状态收到 start）同周期锁存——
  // start 为单拍 executePulse，不能经 sIDLE 中转。正常流程 |io.start 项恒假，
  // 锁存拍与行为逐字不变。
  // ==========================================================================
  when(state === sIDLE || io.start) {
    curAddr    := io.baseAddr
    colAddr    := io.calcConfig.cScaleColTileAddressOffset
    rowStep    := io.calcConfig.cScaleRowAddressOffset
    lineTarget := io.regFile.m
    nCnt       := 0.U
  }
}
