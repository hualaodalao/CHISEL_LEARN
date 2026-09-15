
import chisel3._
import chisel3.util._

class HiveCoreCalcConfig(cfg: HiveCoreConfig) extends Module {

    val io = IO(new Bundle {
        val startIn = Input(Bool())
        val startOut = Output(Bool())
        val err = Output(Bool())
        val errPluse = Output(Bool())
        val errCode = Output(UInt(4.W))
        val calcConfig = Output(HiveCoreExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
        val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
  })

    val err = RegInit(false.B)
    val errNext = RegNext(err)
    val errCode = Reg(UInt(4.W))
    val calcConfig = Reg(HiveCoreExePreCalcConfig(cfg)) 
    val errComb = Wire(Bool())
    val errCodeComb = Wire(UInt(4.W))

    val sIDLE      = 0.U(2.W)
    val sC0    = 1.U(2.W)
    val sC1    = 2.U(2.W)   // pop buffer 驱动 writeData、推进地址  
    val sDONE      = 3.U(2.W)
    val state = RegInit(sIDLE)

io.err := err
io.errCode := errCode
io.calcConfig := calcConfig
io.errPluse := err & !errNext


def getBlockSize(t: MXBlockSize.Type) : UInt = {
    //MXBlockSize
    val ret = Wire(UInt(8.W))
    when(t === MXBlockSize.M32){
        ret := 32.U
    }.otherwise{
        ret :=  16.U
    }
    ret
}
def getKLoadNum(t: DataFormat.Type) : UInt = {
    // 【Phase 3 INT 生态同步】aKLoadNum/bKLoadNum = 一个 kTile（单 pass）推进的 K 元素数
    //   = kPerBeat = totalN × elemsPerLane(t)。判据由 isMx8 泛化为 is1B（={INT8,MXE4M3,MXE5M2}）：
    //     · 单字节族（INT8 / MXFP8 E4M3/E5M2）：dual-lane，每拍 totalN×2 K 元素 → totalN×2。
    //     · 其余（含半字节族 INT4/MXE2M1 quad-lane，及非打包格式）：落 else → totalN×4。
    //   ▸ INT4/MXE2M1 quad-lane：落 else 得 totalN×4 = kPerBeat（totalN × elemsPerLane=4）✓。
    //   ▸ 非打包格式（FP16/BF16/INT32）落 else 得 totalN×4，但其 kTile 在下游走 ceil(k/totalN)
    //     路径（CalcConfig.kTile / Executor.newKTiles 的 otherwise 支），本函数返回值对这些格式
    //     不参与 kTile 几何，故 else=totalN×4 属无害兜底。
    // 【编译期恒等性自证（零 RTL，纯 Scala require，不发射任何硬件）】
    //   本函数的 is1B 二分把 {INT8,MX8}→totalN×2、其余(含 INT4/MXE2M1)→totalN×4，其正确性
    //   【依赖】elemsPerLaneLit 单一真源取值 INT4/MXE2M1==4、INT8/MX8==2。一旦该表变动而本函数
    //   未同步，elaboration 期即炸，强制修复。门控 if(cfg.hasMx4 || cfg.hasInt4)：无亚字节格式
    //   的配置完全不评估，零影响。
    if (cfg.hasMx4 || cfg.hasInt4) {
      require(DataFormat.elemsPerLaneLit(DataFormat.MXE2M1) == 4 &&
              DataFormat.elemsPerLaneLit(DataFormat.INT4) == 4 &&
              DataFormat.elemsPerLaneLit(DataFormat.MXE4M3) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.MXE5M2) == 2 &&
              DataFormat.elemsPerLaneLit(DataFormat.INT8) == 2,
        "getKLoadNum is1B split relies on elemsPerLane: INT4/MXE2M1==4 and INT8/MX8==2; " +
        "if the elemsPerLaneLit single-source table changes, this function must be updated too")
    }
    val ret = Wire(UInt(8.W))
    when(DataFormat.is1B(t)){
        ret := cfg.totalN.U  * 2.U
    }.otherwise{
        ret := cfg.totalN.U  * 4.U
    }
    assert(cfg.totalN > 0, "totalN must be greater than 0")
    assert(cfg.totalN < 64, "totalN must be less than 64, otherwise ret overflow")
    ret
}


/** 外部内存每元素字节数（运行期解码）。
  * 表与优先级逐字镜像金锚 HiveDebugModel.memElemBytes：
  *   FP32/INT32→4、FP16/BF16/INT16→2（注意 INT16 归 2B 档）、
  *   INT8/MXE4M3/MXE5M2→1、MXE2M1/INT4（isHalfB 半字节族）→0。
  * 分支序沿用本文件 C 侧既有链（半字节 → 1B → 2B(含 INT16) → otherwise 4B），
  *   otherwise 兜住 FP32/INT32 与 reserved 10..15（DataFormat 枚举无成员）。
  * 半字节族返回 0 是【既有约定】而非本次新裁：金锚同值同注释「不作为输入格式
  *   支持」，对应 colTile 偏移退化为 0，与金锚 kPerBeat×memElemBytes=0 一致。 */
def memElemBytesOf(t: DataFormat.Type) : UInt = {
    val ret = Wire(UInt(3.W))
    when(DataFormat.isHalfB(t)){
        ret := 0.U
    }.elsewhen(DataFormat.is1B(t)){
        ret := 1.U
    }.elsewhen(DataFormat.is2B(t) || t === DataFormat.INT16){
        ret := 2.U
    }.otherwise{
        ret := 4.U
    }
    ret
}

/** 每 16-bit lane 打包的元素数（MXFP8 dual-lane=2、MXFP4 quad-lane=4、其余=1）。
  * 与金锚 HiveDebugModel.elemsPerLane 同源；kPerBeat = totalN × 本量。
  * 【Phase 3】委托到 DataFormat.elemsPerLane 单一真源（Executor kTile 分母共用），
  *   编译期门控 cfg.hasMx4：
  *   · hasMx4=false（cfgBase/cfgMx 基线）：helper 走 else 支，逐字复刻原内联实现
  *     「Wire(UInt(2.W)); isMx8?2:1」，重发射 normalized-diff=0；MXE2M1 不生成硬件。
  *   · hasMx4=true（fp4 配置）：helper 走 3-bit 支，追加 isMx4→4。
  * 判据仍锚 isMx8：MXE2M1 走独立 isMx4 分支（quad-lane=4），不混入 MX8 dual-lane。 */
def elemsPerLaneOf(t: DataFormat.Type) : UInt = DataFormat.elemsPerLane(t, cfg.hasMx4)

/** 外部内存每元素【位宽】（运行期解码，Phase 3 新增）。
  * 与 memElemBytesOf 互补：字节对齐族 memElemBits = memElemBytes×8（恒等），
  * 亚字节族 memElemBytes=0 但 memElemBits=4（MXE2M1/INT4）。
  * colTile 偏移的 fp4 分支用 bits 口径 totalN×elemsPerLane×memElemBits/8 得到
  * 正确的 16×4×4/8=32（bytes 口径因 memElemBytes=0 会退化到 0）。
  * ★本函数仅在 if(cfg.hasMx4) 门控块内被调用；hasMx4=false 基线【从不 elaborate】
  *   本 def（Scala 方法未被调用即不生成硬件），故对 normalized-diff=0 零影响。
  * 分支序与 memElemBytesOf 逐字对齐（半字节→1B→2B(含INT16)→otherwise 4B）。 */
def memElemBitsOf(t: DataFormat.Type) : UInt = {
    val ret = Wire(UInt(6.W))
    when(DataFormat.isHalfB(t)){
        ret := 4.U
    }.elsewhen(DataFormat.is1B(t)){
        ret := 8.U
    }.elsewhen(DataFormat.is2B(t) || t === DataFormat.INT16){
        ret := 16.U
    }.otherwise{
        ret := 32.U
    }
    ret
}

//如果是mx类型的，K维至少大于编码block
val aMxBlockSize = WireInit(0.U(8.W))
val aKLoadNum = WireInit(0.U(8.W))
val bMxBlockSize = WireInit(0.U(8.W))
val bKLoadNum = WireInit(0.U(8.W))
val cMxBlockSize = WireInit(0.U(8.W))
val cNWriteNum = WireInit(0.U(8.W))
aMxBlockSize := getBlockSize(io.regFile.aMxBlockSize)
aKLoadNum := getKLoadNum(io.regFile.aFmt)
bMxBlockSize := getBlockSize(io.regFile.bMxBlockSize)
bKLoadNum := getKLoadNum(io.regFile.bFmt)
cMxBlockSize := getBlockSize(io.regFile.cMxBlockSize)
cNWriteNum := cfg.totalN.U

// 【#124 ①】colTile 偏移的两个运行期因子（A/B 各自解码，勿共用：aFmt/bFmt 可异构）。
// 与本文件上方 aMxBlockSize/aKLoadNum 同一放置层（模块级无条件赋值的 Wire），
// 使 sC0 分支内只做一次乘法，不在 when 里生成解码逻辑。
// 【Phase 3】laneElems 线宽随 cfg.hasMx4 门控：false→2-bit（与改动前逐字同宽，
//   elemsPerLaneOf 亦返回 2-bit，normalized-diff=0）；true→3-bit（容纳 fp4 的 4）。
// 【Phase 3 INT 生态同步】laneElems 线宽门控扩展：任一亚字节格式（MXE2M1 或 INT4）
//   在场即需 3-bit 容纳打包数 4；否则 2-bit 足够（打包数 ≤2）。
val laneElemsW = if(cfg.hasMx4 || cfg.hasInt4) 3 else 2
val aLaneElems = WireInit(0.U(laneElemsW.W))
val aElemBytes = WireInit(0.U(3.W))
val bLaneElems = WireInit(0.U(laneElemsW.W))
val bElemBytes = WireInit(0.U(3.W))
aLaneElems := elemsPerLaneOf(io.regFile.aFmt)
aElemBytes := memElemBytesOf(io.regFile.aFmt)
bLaneElems := elemsPerLaneOf(io.regFile.bFmt)
bElemBytes := memElemBytesOf(io.regFile.bFmt)

    
//Error config check
{
    errComb := false.B
    errCodeComb := 0.U
    val isAMx = DataFormat.isMx(io.regFile.aFmt)
    val isBMx = DataFormat.isMx(io.regFile.bFmt)
    val isMx = isAMx || isBMx
    
    val errMXLargerThanKLoadNum = ((aMxBlockSize > aKLoadNum) & DataFormat.isMx(io.regFile.aFmt))|
    ((bMxBlockSize > bKLoadNum) & DataFormat.isMx(io.regFile.bFmt))|
    ((cMxBlockSize > cNWriteNum) & DataFormat.isMx(io.regFile.cFmt))
    when(errMXLargerThanKLoadNum){
        errComb := true.B
        errCodeComb := 0.U
    }
     
    when(isAMx ^ isBMx){
        errComb := true.B
        errCodeComb := 3.U
    }
    //mx的时候必须loadWMode为1，代表B为转置矩阵，k维切分
    when(isMx & io.regFile.loadWMode === 0.U){
        errComb := true.B
        errCodeComb := 4.U
    }
    when(io.regFile.aFmt === DataFormat.FP32 | io.regFile.bFmt === DataFormat.FP32){
        errComb := true.B
        errCodeComb := 5.U
    }
    //when(io.regFile.cFmt === DataFormat.MXE2M1 && io.regFile.n(0) === 1.U){
    //    errComb := true.B
    //    errCodeComb := 6.U
    //}
    //容量不够
    when(io.regFile.m > (cfg.cBufferDepth - cfg.totalN).U){
        errComb := true.B
        errCodeComb := 7.U
    }

    // ==========================================================================
    // 【P0-1 errCode 8（fp4 K 整除运行期守护）】fp4(E2M1) 输入要求 K 被 kPerBeat 整除。
    // --------------------------------------------------------------------------
    // ▸ 真 bug 根因：Executor 的 kTiles = K / (totalN×elemsPerLane) 是【地板除】。fp4
    //   kPerBeat = totalN×4 = 64，K 非 64 整除时（如 K=96）只累加 floor(96/64)=1 个 kTile
    //   = 64 个 K，尾部 32 个 K【静默丢弃】、结果错但此前无任何 errCode（测试全绿假象）；
    //   K<64 时 newKTiles=0 → kTiles:=newKTiles-1 回绕为 65535 → 65536 轮 pass 跑飞
    //   （DMA 越界读）。此前唯一守护只在【测试侧金锚】HiveDebugModel.validate 的
    //   require(k%kPerBeat==0)，硬件侧零守护（Executor 旧注释"已在 sIDLE 校验"不成立）。
    // ▸ Decision: a dedicated code 8 was allocated so software can name the fp4
    //   geometry violation precisely. (#146) The original wording here said
    //   "code 8 instead of reusing code 7" -- that half of the rationale is now
    //   obsolete, because errCode 7 (fp4 forced blk=64) has been DELETED (see
    //   the removal note below). Code 8 is therefore the ONLY fp4 geometry guard
    //   left, and code 7 is free again for future allocation.
    //   errCode 8 itself was previously unused (problem.md item 6 had "reserved"
    //   8 for the optional W4A8 mix diagnostic; that one was pushed to code 9).
    // ▸ 判据：isMx4(aFmt) && (K % kPerBeat != 0)，kPerBeat = totalN×4（编译期常数，
    //   totalN=16 时 = 64）。与金锚 HiveDebugModel.hwErrCode / kPerBeat(MXE2M1,totalN) 同源。
    // ▸ 门控：整块包在 if(cfg.hasMx4) 内——cfgBase/cfgMx 基线 hasMx4=false 完全不 elaborate，
    //   现有 errCode 0/3/4/5/6 逐字不变，normalized-diff=0。
    // ▸ Priority (last-connect wins, later source line = higher priority): this
    //   block sits AFTER errCode6 and BEFORE the Fix-4 MX4xMX8 mix code3 block,
    //   so the mix code3 overrides this code8. (Both predicates can only fire
    //   together for a mixed fp4/fp8 pair, which is rejected by the mix branch
    //   anyway, so no live configuration is shadowed by this ordering.)
    //   (#146) The former errCode7 block that used to sit last has been DELETED,
    //   so nothing overrides this block from below any more. Final truth order:
    //     3 (MX4xMX8 mix) > 8 (fp4 K not divisible) > 6 > 5 > 4
    //       > 3 (isAMx ^ isBMx) > 0
    //   Code 7 is now FREE (see the removal note below).
    if(cfg.hasMx){
        when(DataFormat.isMx8(io.regFile.aFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 2).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }    
    if(cfg.hasMx4){
        when(DataFormat.isMx4(io.regFile.aFmt) &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 4).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }
    // ==========================================================================
    // 【Phase 3 INT 生态同步 · errCode 8（INT8/INT4 K 整除运行期守护）】与上方 MX 两块同构：
    //   INT8 dual-lane kPerBeat=totalN×2、INT4 quad-lane kPerBeat=totalN×4；K 非整除时
    //   Executor kTiles 地板除静默丢尾（或 K<kPerBeat 时 16-bit 回绕跑飞），故运行期拒绝。
    //   门控 if(cfg.hasInt)：不含整数格式的配置完全不 elaborate。位于 errCode6 之后、
    //   Fix-4 mix code3 之前，保持 last-connect 优先级序（3 mix > 8 K整除 > 6 > 5 > 4 > 3 > 0）。
    if(cfg.hasInt){
        when(io.regFile.aFmt === DataFormat.INT8 &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 2).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
        when(io.regFile.aFmt === DataFormat.INT4 &&
             ((io.regFile.k(15, 0) % (cfg.totalN * 4).U =/= 0.U) ||
              (io.regFile.k(15, 0) === 0.U))){
            errComb := true.B
            errCodeComb := 8.U
        }
    }
    // ==========================================================================
    // 【Fix-4 errCode 3（MX4×MX8 混配运行期可诊断守护）】aFmt/bFmt 分属 fp4(E2M1) 与
    //   fp8(E4M3/E5M2) 时，HiveWorkUnit 的运行期选择子 Mux(isMx4(io.aFmt), quadResult,
    //   dualLaneMx()) 只看 aFmt，而被选支路内 b 侧解码器接 io.bFmt → lane 粒度与解码格式
    //   矛盾、v1 数据通路会静默算错。现有 errCode3（isAMx ^ isBMx，见上方裸块）因 isMx 已含
    //   MXE2M1，MXE2M1×MXE4M3 两侧同 true → XOR=0 漏检。本 when 用 isMx4 异或【加强、不替换】
    //   原 errCode3，暂复用 code 3（语义="v1 不支持的格式组合"）。
    // ▸ 定位（用户 2026-09-12 最终裁定）：MX4×MX8 混合(W4A8)数学与 OCP 规范均【合法】，但真
    //   数据通路支持列 v2 后续立项（与 per-half-pass/block=32 同源）。本守护是【运行期可诊断】
    //   的 interim 状态——软件可读错误码、非静默错算、亦非"永久拒绝"：将来支持只需删本 when +
    //   补 W4A8 数据通路（aFmt/bFmt 已是独立 4-bit 字段，寄存器/软件接口无需重构）。
    // ▸ 门控：整块包在 if(cfg.hasMx4) 内——cfgBase/cfgMx 基线 hasMx4=false 完全不 elaborate，
    //   现有 errCode 0/3/4/5/6 逐字不变，normalized-diff=0。
    // ▸ Priority (last-connect wins, later source line = higher priority): this
    //   block sits AFTER code8(K divisibility), so the mix code3 overrides
    //   8/6/5/4. (#146) It used to sit before an errCode7 block; that block has
    //   been DELETED, so this is now the LAST branch of the chain and therefore
    //   the highest-priority one. Final truth order:
    //     3 (this block, MX4xMX8 mix) > 8 (fp4 K not divisible) > 6 > 5 > 4
    //       > 3 (isAMx ^ isBMx) > 0
    //   The golden model (HiveDebugModel.hwErrCode / precheckConfigErr, and
    //   HiveCoreQuickSpec.hwErrCode which delegates to it) plus
    //   HiveCoreQuickSpec.errExplain and src/hive/doc/problem.md must mirror
    //   exactly this order.
    if(cfg.hasMx4){
        when(DataFormat.isMx4(io.regFile.aFmt) ^ DataFormat.isMx4(io.regFile.bFmt)){
            errComb := true.B
            errCodeComb := 3.U
        }
    }

    // ==========================================================================
    // 【#146 errCode 7 REMOVED】fp4(E2M1) no longer forces mxBlockSize = 64.
    // --------------------------------------------------------------------------
    // ▸ Why the block had to go: the blocksize field was narrowed to a 2-level
    //   enum {sel=0 -> 16 bytes, sel=1 -> 32 bytes}, so getBlockSize() can only
    //   ever return 16 or 32. The removed predicate compared it against 64.U,
    //   i.e. `aMxBlockSize =/= 64.U` was a TAUTOLOGY. Combined with the isMx4
    //   term this meant: any fp4 run was rejected at runtime, unconditionally,
    //   and completely silently -- the golden model mirrored the same tautology,
    //   so RTL and anchor agreed and no test ever went red. fp4 was therefore
    //   permanently unusable while looking "covered".
    // ▸ What replaces it: fp4 now supports blk in {16, 32} like fp8. errCode 8
    //   (K not divisible by kPerBeat) stays as the ONLY fp4 geometry guard, and
    //   it is a real guard: it can be satisfied, and violating it is detected.
    // ▸ Code 7 is now FREE. The standing ruling on code allocation still holds:
    //   any new check is assigned from 7 upwards, and codes 1/2 are never
    //   revived (the three blocksize violations are merged into a single
    //   errCode 0 by errMXLargerThanKLoadNum above, so 1/2 are unreachable).
    // ▸ Truth order after the removal (last-connect wins, later source line =
    //   higher priority). Only the relative order of the SURVIVING branches
    //   matters and none of them was moved:
    //     3 (MX4xMX8 mix, Fix-4) > 8 (fp4 K not divisible) > 6 > 5 > 4
    //       > 3 (isAMx ^ isBMx) > 0
    //   The golden model (HiveDebugModel.hwErrCode / precheckConfigErr, and
    //   HiveCoreQuickSpec.hwErrCode which delegates to it) plus
    //   HiveCoreQuickSpec.errExplain and src/hive/doc/problem.md must mirror
    //   exactly this order.
    // ▸ Gating note: the removed block lived inside `if(cfg.hasMx4)`, so for the
    //   cfgBase/cfgMx baselines (hasMx4=false) it never elaborated at all and
    //   their errCode 0/3/4/5/6/8 paths are literally unchanged.

}



switch(state) {

    // --- sIDLE: 等待 start ---
    // 【B3/B2】idle 期无条件清 err/errCode：使 io.err 成为「本次 EXECUTE 的配置判决」
    // 而非「idle 期当前配置是否合法」。上方 Error config check 裸块（L74-90）每拍评估
    // blocksize 违约并置 err/errCode；若不在 sIDLE 覆盖清除，则装配阶段（多次 REG_WRITE、
    // 尚未 EXECUTE）写坏 blocksize 即会置 io.err → 顶层 errFlag 直连（B2）在 EXECUTE 前
    // 误报。last-connect 序：本 switch 在裸块之后，sIDLE 分支覆盖裸块的置位。errCode 亦
    // 在此清零（errCode Reg 无 RegInit，上电首拍 state=sIDLE 即被本分支初始化为 0）。
    is(sIDLE) { 
      err := false.B
      errCode := 0.U
      when(io.startIn) {
        state := sC0
      }
    }

    is(sC0){
      
      
      when(errComb){
        err := errComb
        errCode := errCodeComb
      }
      
      state := sC1
      calcConfig.mTile := (io.regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
      calcConfig.nTile := (io.regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
    
      calcConfig.isAX2 := DataFormat.is1B(io.regFile.aFmt)
      calcConfig.isBX2 := DataFormat.is1B(io.regFile.bFmt)
      calcConfig.isAX4 := DataFormat.isHalfB(io.regFile.aFmt)
      calcConfig.isBX4 := DataFormat.isHalfB(io.regFile.bFmt)

    // 【Phase 3 INT 生态同步】kTile 分母判据由 isMx 泛化为「打包格式」= is1B || isHalfB
    //   （={INT8,MXE4M3,MXE5M2} ∪ {INT4,MXE2M1}，已完整覆盖全部 MX）。打包格式每拍推进
    //   aKLoadNum=kPerBeat 个 K 元素，故 kTile = k / aKLoadNum（地板除，与 Executor newKTiles 同源）；
    //   非打包格式（FP16/BF16/INT32 等）每拍 totalN 个 K，走 ceil(k/totalN) 旧路径不变。
    when(DataFormat.is1B(io.regFile.aFmt) || DataFormat.isHalfB(io.regFile.aFmt)){
        calcConfig.kTile := (io.regFile.k / aKLoadNum)
    }.
    otherwise{
        calcConfig.kTile := (io.regFile.k  + cfg.totalN.U - 1.U) / cfg.totalN.U
    }



  
  // ==========================================================================
  // A矩阵row major 偏移计算
  // 【裁定3 修复：A 行步进偏移链补齐 INT16/INT32 档（refactor loss，同 Issue A 类）】
  // 权威语义三源印证：(a) git 旧码 aRow=Mux(mxRun,k*1,k*(aW/8))（配置位宽 + MX 打包）；
  //   (b) 已修 C 侧同结构链；(c) 金锚 HiveDebugModel.memElemBytes（测试对账基准）：
  //   FP32/INT32→4、FP16/BF16/**INT16**→2、INT8/MXE4M3/MXE5M2→1、MXE2M1/INT4→半字节
  //   「不作为输入格式支持」。原链 is2B→*2 / is1B→*1 / otherwise→/2 丢失 INT16(应*2)
  //   与 INT32(应*4)——二者均落 otherwise→/2（4× 偏差），直接致 DebugCase INT 组
  //   (aFmt=bFmt=INT16) A/B DMA 地址全错、1023/1024 全元素失配。
  // 按 memElemBytes 优先级重排：4B→2B(含 INT16)→1B→otherwise(半字节/reserved，
  //   不支持输入，保留旧 /2，无测试覆盖)。FP32 作输入已被 errCode=5 拦死，is4B 实达 INT32。
  when(DataFormat.is4B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.aFmt) || io.regFile.aFmt === DataFormat.INT16){
    calcConfig.aRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.aFmt)){
    calcConfig.aRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.aRowAddressOffset := io.regFile.k / 2.U
  }
  // ==========================================================================
  // 【#124 ① 修复：aColTileAddressOffset 由 elaboration 期常数改为运行期量】
  // 旧式（672fb37 退化，与下方 B 侧孪生同源）：cfg.totalN.U * (cfg.aW / 8).U
  //   —— aW 是【编译期】配置位宽（出厂 16），故整式恒 = 32，与运行期 aFmt 无关。
  // 新式：totalN × elemsPerLane(aFmt) × memElemBytes(aFmt) = aKpb × aEb。
  //
  // ▸ 语义依据（本模块消费者 = HiveCoreDmaRdOnly isA=true 通道）：RdOnly 头注释
  //   A 侧闭式「块首址(nt, kt) = base + kt*C」，C 即本偏移，是【一个 kTile 在外部
  //   内存中跨过的字节数】。A 行主序 M×K、每元素 aEb 字节、一个 kTile 推进
  //   kPerBeat(aFmt) = totalN × elemsPerLane 个 K 元素 ⇒ C = kPerBeat × aEb。
  // ▸ 金锚印证（HiveDebugModel.Plan）：aColOff = aKpb.toLong * aEb，其中
  //   aKpb = kPerBeat(aFmt,totalN)、aEb = memElemBytes(aFmt) —— 逐字同式。
  // ▸ 结构对称印证（同文件内部一致性）：本文件 A 侧 scale 的 kTile 步进
  //   scaleAColTileAddressOffset = aKLoadNum / blk、B 侧 scaleBColTileAddressOffset
  //   = bKLoadNum / blk，两处 kTile 步进一律用「每拍 K 元素数」而非 totalN；
  //   数据侧唯独退化成 totalN×(aW/8)，丢了 elemsPerLane 与 memElemBytes 两个因子。
  // ▸ 恒等性证明（不得反向破坏已绿区间）：aW=16 ⇒ 旧值恒 32。新值
  //     MX8(MXE4M3/MXE5M2) : 16 × 2 × 1 = 32  ✓ 恒等
  //     FP16/BF16/INT16    : 16 × 1 × 2 = 32  ✓ 恒等
  //     INT8               : 16 × 1 × 1 = 16  ✗ 旧值错（多 +16）
  //     INT32              : 16 × 1 × 4 = 64  ✗ 旧值错（少 −32）
  //     半字节族(MXE2M1/INT4): 16 × 1 × 0 = 0（memElemBytes=0 的既有约定，
  //        金锚同值；该族不支持作输入，validate 已在仿真前拦截）
  //   ⇒ 现役全绿套件（MxSpec/QuantSpec/LoadWSpec/SimCase/DebugCase/ParamDebugSpec
  //     的 MX8/FP16/BF16/INT16 区间）逐格式数值【零变化】，改动只在 INT8/INT32
  //     这两个此前零覆盖的破损区间生效。
  // ▸ 归零证明（A 侧）：RdOnly A 闭式与 nt 无关，唯一误差项 = kt × (C − aKpb×aEb)，
  //   代入新式后【恒为 0】（全格式、全 K）。#132 记录的 A 侧孪生缺陷
  //   （aFmt=INT8 时 hw 比语义值多 +16、INT32 时少 −32）由此消除。
  // ==========================================================================
  // 【Phase 3 fp4 additive（现有格式零改动）】上方 bytes 口径对 MXE2M1 会退化到 0
  //   （memElemBytes(halfB)=0）。仅在 if(cfg.hasMx4) 门控下改用 bits 口径：
  //     aColTile = totalN × elemsPerLane(aFmt) × memElemBits(aFmt) / 8
  //   fp4: 16 × 4 × 4 / 8 = 32（一个 kTile = 64 个 4-bit 元素 = 32 字节）✓。
  //   恒等性：字节对齐族 memElemBits/8 == memElemBytes，故 bits 式与原 bytes 式
  //   【全现有格式逐值恒等】（MX8=32/FP16=32/INT8=16/INT32=64）；差异仅在亚字节族
  //   （MXE2M1 0→32、INT4 0→8），而这些格式在 hasMx4=false 基线【从不进入本支】。
  //   else 支保留原 bytes 表达式【逐字不变】→ cfgBase/cfgMx normalized-diff=0。
  //   ▸ INT8/INT32「破损区间」说明：本仓 colTile 自 #124 ① 起已是运行期 bytes 口径，
  //     INT8=16/INT32=64 早已正确（见上恒等性证明），当前【无】INT8/INT32 破损需修；
  //     计划正文所述「统一公式修复 INT8/INT32」为历史陈述，本 Phase 不触碰其数值。
  if(cfg.hasMx4 || cfg.hasInt4){
    calcConfig.aColTileAddressOffset := cfg.totalN.U * (aLaneElems * memElemBitsOf(io.regFile.aFmt)) / 8.U
  } else {
    calcConfig.aColTileAddressOffset := cfg.totalN.U * (aLaneElems * aElemBytes)
  }

  
// B矩阵非转置 row major偏移计算
// 【裁定3 修复：B 行步进偏移链——(1) loadWMode=0 用 n 而非 m；(2) 补齐 INT16/INT32 档】
// (1) m→n 依据：GEMM C[M,N]=A[M,K]×B[K,N]，loadWMode=0 时 B 为 K×N 行主序、行长 = N，
//   行步进按 N 才自洽。git 旧码 bRow(loadWMode=0)=n*(bW/8)、金锚 HiveDebugModel
//   bRowOff=(if loadWMode then k else **n**)*bEb 双双用 n；新码误用 regFile.m，
//   M=N=32 巧合掩盖（既有全测试 M=N=32），M≠N 时 B 地址错位。
// (2) INT16/INT32 补齐依据同 A 链（memElemBytes：INT16→2、INT32→4）。
// loadWMode=1（MX 转置，errCode=4 强制 MX 必走此模式）：B 按 N×K 存储，行步进 = K*bEb。
when(io.regFile.loadWMode === 0.U){
  when(DataFormat.is4B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.n * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.bFmt) || io.regFile.bFmt === DataFormat.INT16){
    calcConfig.bRowAddressOffset := io.regFile.n * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.n * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.n / 2.U
  }
}.otherwise{
  //B矩阵col major转置 偏移计算
  when(DataFormat.is4B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 4.U
  }.elsewhen(DataFormat.is2B(io.regFile.bFmt) || io.regFile.bFmt === DataFormat.INT16){
    calcConfig.bRowAddressOffset := io.regFile.k * 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.bFmt)){
    calcConfig.bRowAddressOffset := io.regFile.k * 1.U
  }.otherwise{
    calcConfig.bRowAddressOffset := io.regFile.k / 2.U
  }
}
// ==========================================================================
// 【#124 ① 修复：bColTileAddressOffset 由 elaboration 期常数改为运行期量】
// 旧式（672fb37 退化，与上方 A 侧孪生同源）：cfg.totalN.U * (cfg.bW / 8).U
//   —— bW 是【编译期】配置位宽（出厂 16），故整式恒 = 32，与运行期 bFmt 无关。
// 新式：totalN × elemsPerLane(bFmt) × memElemBytes(bFmt) = bKpb × bEb。
//
// ▸ 双语义同时满足（B 侧比 A 侧多一层：本偏移在两种 loadWMode 下语义不同）
//   · loadWMode=0（B 为 K×N 行主序）：C 是【n-tile 步进】= 跨过 totalN 个 N 列
//     所需的字节数 = totalN × bEb。金锚 HiveDebugModel：
//     bNTileOff = if(loadWMode) totalN*bRowOff else **totalN*bEb** —— 与 C 同式
//     （非 MX 下 elemsPerLane=1，bKpb=totalN，故新式 = totalN×bEb ✓）。
//   · loadWMode=1（B 为 N×K 转置，MX 强制走此模式）：C 是【k-tile 步进】=
//     一个 kTile 推进 bKpb = totalN × elemsPerLane 个 K 元素所需的字节数
//     = bKpb × bEb。金锚 HiveDebugModel：bKTileOff = if(loadWMode) **bKpb*bEb**
//     （#121 修正值，禁改）。MX8 下 bKpb = 2×totalN = 32、bEb=1 ⇒ 新式 = 32 ✓。
//   ⇒ 同一个式子 totalN×elemsPerLane×memElemBytes 精确覆盖两支：非 MX 支
//     elemsPerLane=1 自动退化成 totalN×bEb（n-tile 语义），MX 支 elemsPerLane=2
//     自动放大成 2×totalN×bEb（k-tile 语义）。这不是巧合——loadW=0 恒非 MX
//     （errCode4 拦 MX+loadW=0），故两支持有的 elemsPerLane 天然互斥。
// ▸ 恒等性证明（不得反向破坏已绿区间）：bW=16 ⇒ 旧值恒 32。新值
//     MX8(MXE4M3/MXE5M2) : 16 × 2 × 1 = 32  ✓ 恒等（MX 区间新旧同值，MxSpec/
//        QuantSpec dualMX K=64 blk16 的 B 通道 64 拍逐拍全绿不受影响）
//     FP16/BF16/INT16    : 16 × 1 × 2 = 32  ✓ 恒等（LoadWSpec/SimCase/DebugCase
//        全绿区间数值零变化）
//     INT8               : 16 × 1 × 1 = 16  ✗ 旧值错（多 +16）
//     INT32              : 16 × 1 × 4 = 64  ✗ 旧值错（少 −32）
//     半字节族(MXE2M1/INT4): 16 × 1 × 0 = 0（memElemBytes=0 的既有约定，金锚同值）
// ▸ 误差解析（#132 推导式，用于界定本次改动的收敛范围）：dmaB(loadW=1) 的
//     Δ(nt,kt) = nt×(kTiles×colTileOff − rowOff) + kt×(colTileOff − bKpb×bEb)
//   · kt 维分量：代入新式后【恒为 0】（全格式、全 K）—— 这是本次改动消除的部分，
//     即 Section E【E2】「INT8/INT32 + loadW=1 + kTiles>1 → kt≥1 的 B 地址全错」。
//   · nt 维分量：属 HiveCoreDmaRdOnly lastBTileStartAddr 累加模型的【结构性残余】
//     （累加式与金锚闭式在 K 非 bKpb 整数倍时不同源），改前改后【同样存在】、
//     非本次引入，已呈报并由 #139 承接。本任务禁动 RdOnly 功能行，故此处不处理。
// ▸ 反面警示（勿照搬）：旧 HiveCore.scala 曾用 Mux(loadWMode, totalN×k×(bW/8), …)
//   这类「n-tile 周期语义」式子，与 lastBTileStartAddr FSM 的累加口径不兼容，
//   严禁回退到那种写法；colTileOffset 必须保持单一运行期量、由消费者按语义解释。
// ==========================================================================
// 【Phase 3 fp4 additive（现有格式零改动），与上方 A 侧孪生同批】B 侧 bytes 口径对
//   MXE2M1 同样退化到 0；仅在 if(cfg.hasMx4) 门控下改 bits 口径：
//     bColTile = totalN × elemsPerLane(bFmt) × memElemBits(bFmt) / 8
//   fp4(loadW=1，MX 强制): 16 × 4 × 4 / 8 = 32（一个 kTile = 64 个 4-bit = 32 字节）✓。
//   恒等性同 A 侧：字节对齐族 memElemBits/8 == memElemBytes → 全现有格式逐值恒等
//   （MX8=32/FP16=32/INT8=16/INT32=64）；else 支原 bytes 式逐字不变 → normalized-diff=0。
if(cfg.hasMx4 || cfg.hasInt4){
  calcConfig.bColTileAddressOffset := cfg.totalN.U * (bLaneElems * memElemBitsOf(io.regFile.bFmt)) / 8.U
} else {
  calcConfig.bColTileAddressOffset := cfg.totalN.U * (bLaneElems * bElemBytes)
}

//C矩阵Row Major偏移计算
// 【IssueA 修复：还原旧 HiveCore.scala 全函数语义（refactor loss，同 B4/B5 类）】
// 旧码（672fb37^ HiveCore.scala L160/L202-203）用 cOutElemBytes=MuxCase(默认4B) +
//   cIsFmt4 半字节专用分支（n>>1 / totalN>>1），注释原文：「reserved 回落：非量化/
//   非16/非8编码（含 fp32 与 1011..1111）自然落 4B 档」。拆分重构时被改成
//   is4B/is2B/is1B 三分支 partial-chain（无 otherwise），丢失三处：
//     (1) is1B 分支双赋值 cRowAddressOffset（n*1 后又 n/2，last-connect 生效 n/2 错），
//         且从不赋 cColTileAddressOffset；
//     (2) 整个半字节档（isHalfB=INT4/MXE2M1=mxfp4）缺失 → 无赋值；
//     (3) INT16 未被 is2B 覆盖（is2B 仅 FP16|BF16）+ reserved 无 otherwise → 无赋值，
//         cRowAddressOffset/cColTileAddressOffset 保留 last-connect 垃圾值
//         （直接导致 fallback reserved(11/15) 用例 C 地址 beat#1 = 0x82605645 垃圾）。
// 修复：按旧语义重排为「半字节 → 1B → 2B(含 INT16) → otherwise(4B, 覆盖 is4B+reserved)」，
//   otherwise 兜底 4B 精确复刻旧 MuxCase 默认档，reserved 自然落 4B。
when(DataFormat.isHalfB(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n >> 1
  calcConfig.cColTileAddressOffset := cfg.totalN.U >> 1
}.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
  calcConfig.cRowAddressOffset := io.regFile.n * 1.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 1.U
}.elsewhen(DataFormat.is2B(io.regFile.cFmt) || io.regFile.cFmt === DataFormat.INT16){
  calcConfig.cRowAddressOffset := io.regFile.n * 2.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 2.U
}.otherwise{
  // is4B(FP32/INT32) + reserved(含 1011..1111) → 4B 档（旧 MuxCase 默认）
  calcConfig.cRowAddressOffset := io.regFile.n * 4.U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * 4.U
}

// ==========================================================================
// (#146) MX SCALE READ ALIGNMENT CONTRACT -- full derivation (ASCII only).
// --------------------------------------------------------------------------
// This block is the SINGLE SOURCE of the two scale address strides consumed by
// HiveCoreDmaRdOnly (scaleA tied isA=true, scaleB tied isA=false):
//   rowStride   = scaleXRowAddressOffset     = K / blk          [bytes]
//   kTileStride = scaleXColTileAddressOffset = kLoadNum / blk   [bytes]
// with blk = 32 for MXBlockSize.M32, 16 for MXBlockSize.M16, and
// kLoadNum = getKLoadNum(fmt) = totalN * elemsPerLane(fmt) = kPerBeat
// (32 for MXFP8, 64 for MXFP4 at totalN=16).
// The scale tensors are stored DENSELY, one E8M0 byte per MX block:
//   scaleA at SA_BASE + mm*rowStride + j,  j in [0, K/blk)
//   scaleB at SB_BASE + nn*rowStride + j   (N-major, same row stride)
// and the DMA issues ONE 32-BIT read per beat at
//   addr(row, kt) = BASE + row*rowStride + kt*kTileStride
// That transfer touches bytes addr .. addr+3, while only the first
// numScalesPerBeat = kPerBeat/blk of them are ever selected by the Executor byte
// mux idx = (lane*elemsPerLane)/blk; the remaining bytes are don't-care (they may
// even run past the end of the row / tensor for the last kTile of the last row).
//
// CONTRACT -- software must guarantee it, the hardware does NOT check it:
// every scale read address must be 4-byte aligned. Since row and kt each run over
// a contiguous range starting at 0, this holds for the WHOLE run exactly when
//   (S0) BASE % 4 == 0                  (SA_BASE / SB_BASE come from software)
//   (S1) rowStride   % 4 == 0   OR the row range is a single value (M==1 / N==1)
//   (S2) kTileStride % 4 == 0   OR kTiles == 1  (i.e. K == kPerBeat)
// Substituting rowStride = K/blk and kTileStride = kPerBeat/blk:
//   (S1) <=> K % (4*blk) == 0 :  blk=16 -> K % 64 == 0 ; blk=32 -> K % 128 == 0
//   (S2) <=> (kPerBeat/blk) % 4 == 0 :
//          MXFP8 (kPerBeat=32): blk=32 -> 1 ; blk=16 -> 2  ==> NEVER 0 mod 4
//          MXFP4 (kPerBeat=64): blk=32 -> 2 ; blk=16 -> 4  ==> only blk=16
//
// CONSEQUENCE (this is a derived finding, not a design choice):
//   * MXFP4 + blk=16 is the ONLY non-degenerate combination satisfying the
//     contract for arbitrary M/N/K: kTileStride = 4 (S2 ok), and errCode 8
//     already forces K % 64 == 0, which is exactly K % (4*16) == 0 (S1 ok).
//   * MXFP8 (blk=16 or blk=32) and MXFP4 + blk=32 CANNOT satisfy (S2) once
//     kTiles > 1, because their kTileStride is 1 or 2 bytes. Their aligned subset
//     is degenerate: kTiles == 1 (K == kPerBeat) AND a single row (M==1 / N==1).
//   * This exposure is PRE-EXISTING, not introduced by enabling blk=16: the
//     long-standing MXFP8 blk=32 baseline has kTileStride = 1 and
//     rowStride = K/32, so e.g. K=32 gives rowStride = 1 and row 1 is read at an
//     ODD address. A SoC that cannot perform unaligned 32-bit reads already
//     breaks on today's green MXFP8 cases.
//   * Hence one of three things must be true on the integration side: (a) the
//     SoC memory / AXI path supports unaligned 32-bit reads; (b) the scale layout
//     gains padding so both strides are multiples of 4; (c) the scale DMA issues
//     narrow (8-bit) reads instead. (b) and (c) are OUT OF SCOPE by the #146
//     ruling (do not change the scale tensor layout, do not add a packing state
//     machine, do not change the scale DMA traversal), so NO hardware guard is
//     added here. Registered as an open SoC-integration item in
//     src/hive/doc/problem.md together with the guard options and their cost.
// ==========================================================================
//A矩阵 scale 偏移计算
when(MXBlockSize.M32 === io.regFile.aMxBlockSize){
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 32.U
}.otherwise{
    calcConfig.scaleARowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleAColTileAddressOffset := aKLoadNum / 16.U
}
//B矩阵 scale 偏移计算
when(MXBlockSize.M32 === io.regFile.bMxBlockSize){
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 32.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 32.U
}.
otherwise{
    calcConfig.scaleBRowAddressOffset     := io.regFile.k / 16.U
    calcConfig.scaleBColTileAddressOffset := bKLoadNum / 16.U
}
//C矩阵 scale 偏移计算
// ==========================================================================
when(MXBlockSize.M32 === io.regFile.cMxBlockSize){
    calcConfig.scaleCRowAddressOffset     := (io.regFile.n +& 31.U) / 32.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 32.U
}.
otherwise{
    calcConfig.scaleCRowAddressOffset     := (io.regFile.n +& 15.U) / 16.U
    calcConfig.scaleCColTileAddressOffset := cfg.totalN.U / 16.U
}


  val cWriteBytes = Wire(UInt((8.W)))
  val cDmaBytes = Wire(UInt((8.W)))
  cDmaBytes := cfg.cDmaExtW.U / 8.U
  
  // 【IssueA 同源修复：cWriteBytes partial-chain 同样丢失 reserved/INT16 档】
  // 旧结构 otherwise=totalN/2 意在半字节档（isHalfB=INT4/MXE2M1=mxfp4），但它同时
  //   兜住了 reserved（应回落 fp32=4*totalN）与 INT16（应 2*totalN）→ 二者被误算成
  //   totalN/2，致 fallback(reserved) 用例 cDmaBufBeatTar=0（单拍）而非 fp32 的
  //   多拍，beat#1 走行步进(+n*4=128)而非拍步进(+32)。按 IssueA 同序重排：
  //   半字节 → 1B → 2B(含 INT16) → otherwise(4B, 覆盖 is4B+reserved)。
  when(DataFormat.isHalfB(io.regFile.cFmt)){
    cWriteBytes := cfg.totalN.U / 2.U
  }.elsewhen(DataFormat.is1B(io.regFile.cFmt)){
    cWriteBytes := 1.U * cfg.totalN.U
  }.elsewhen(DataFormat.is2B(io.regFile.cFmt) || io.regFile.cFmt === DataFormat.INT16){
    cWriteBytes := 2.U * cfg.totalN.U
  }.otherwise{
    cWriteBytes := 4.U * cfg.totalN.U
  }

  when(cWriteBytes >  cDmaBytes){
    calcConfig.cBeatAddrStep := cDmaBytes
    calcConfig.cDmaBufBeatTar := cWriteBytes / cDmaBytes - 1.U
    calcConfig.cDmaDmaBeatTar := 0.U
  }.otherwise{
    calcConfig.cBeatAddrStep := cWriteBytes
    calcConfig.cDmaBufBeatTar := 0.U
    calcConfig.cDmaDmaBeatTar := cDmaBytes / cWriteBytes - 1.U
  }

   
}

    is(sC1){
        state := sDONE
    }

    // 【B3】sDONE → 响应新的 startIn 重新进入计算。原 switch 缺本分支：state 到
    // sDONE(=3) 后永久停留，后续 EXECUTE 的 startIn 因 state≠sIDLE 被忽略 →
    // io.startOut 永不再拉高、重复执行用例（QuantSpec 的 legal re-EXECUTE 与
    // C1 recovery）失效。进入 sC0 前清除上一轮 err/errCode 残留（sticky 语义：
    // err 保持到下一次 start 才清，与顶层 errFlag 的 startPulse 清除对齐）。
    is(sDONE){
        when(io.startIn) {
            err := false.B
            errCode := 0.U
            state := sC0
        }
    }

}

io.startOut := state === sC1 && !err
  
  
}