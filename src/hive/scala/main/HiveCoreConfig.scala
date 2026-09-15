/** HiveCore 控制模块配置与命令/状态 Bundle 定义。
  *
  * HiveCore 是 Hive 脉动阵列加速器的顶层控制单元，采用「寄存器配置 + 一键执行」架构：
  *   - REG_WRITE: 写入配置寄存器
  *   - EXECUTE: 触发自动 tiling GEMM 计算
  *   - REG_READ: 读取状态/配置寄存器
  *
  * 内部 FSM 自动完成 M×K @ K×N → M×N 的 GEMM，
  * 根据阵列能力 (totalN×totalN) 自动切块 tiling。
  */

import chisel3._
import chisel3.util._

// ============================================================================
// 配置
// ============================================================================

/** HiveCore 编译期配置。
  *
  * @param arrayN         单 cluster 内 PE 列数（≥4）
  * @param clusterM       cluster 数量（≥1）
  * @param aW             A 矩阵元素位宽
  * @param bW             B 矩阵元素位宽
  * @param cW             C 累加器位宽（0 = 自动推导）
  * @param supportedFmts  支持的数据格式集合
  * @param aBufferDepth   A 缓冲深度
  * @param bBufferDepth   B（权重）缓冲深度（独立 B buffer，宽度 totalN*bW，
  *                       由 RdOnly DMA 自主扫描整个 B 矩阵写入，Executor 按行 pop）
  * @param cBufferDepth   C 缓冲深度（仅存结果 + Partial sum，权重已改走独立 B buffer）
  *                       注意: cBufferDepth 应 >= max(curTileM)，
  *                       即能容纳 partial sum/结果数据，否则 DMA 在
  *                       sREQ_EXT 状态会等待空间释放（不会死锁但会降低吞吐）。
  * @param addrWidth      地址位宽
  * @param cDmaExtW       C 写回 DMA 外部总线位宽（P0 契约：默认 256，只允许 2 的 N 次幂；
  *                       废弃旧 0=640 满宽哨兵与 5B/元素 legacy 布局，废弃动作在 P2 落地）。
  *                       总线位宽 < 转换后行宽时行内拆多拍输出，运行期拍数由 cFmt 决定
  *                       （见 cBeatsPerRowMax 注释）；约束族：isPow2 /
  *                       totalN*32 整除 / [totalN*8, totalN*32]（见下方 require）。
  */
case class HiveCoreConfig(
    arrayN: Int = 8,
    clusterM: Int = 2,
    aW: Int = 16,
    bW: Int = 16,
    cW: Int = 0,
    // (#155 MJ1) MXE2M1 升格为【默认必选】：用户设计律「有 mxfp8 就有 mxfp4，是必选项」。
    //   默认集由 6 种（FP16/BF16/INT16/INT8/MXE4M3/MXE5M2）扩为 7 种（追加 MXE2M1）。
    //   后果：所有【不显式传 supportedFmts】的 cfg（cfgBase、3 个 SimCase、LoadWSpec、MxSpec、
    //   QuickSpec）hasMx4 由 false 翻 true ⟹ DUT 一律长出 fp4 quad-lane 硬件。这不是可选项：
    //   下方新增 require(!hasMx8 || hasMx4) 会在 elaborate 前把 fp8-only 配置直接拦死，
    //   故全仓每一个显式传 supportedFmts 的构造点都必须含 MXE2M1（对齐清单见 build/155 报告）。
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
    aBufferDepth: Int = 64, 
    bBufferDepth: Int = 64,
    cBufferDepth: Int = 64,
    addrWidth: Int = 32,
    scaleBufferDepth: Int = 64,
    cDmaExtW: Int = 256
) {
  /*
  //hive矩阵切分逻辑
  for(n <- 1 to numberOfN){
    for(M <- 1 to numberOfM ){
        kTile = nTile * mTile
    }
  }
  //
  //存在K维切分的时候buffer
  //limit m * n < nKTileInCBuffer if kTileNum > 1
  */
  
  // --- 派生量 ---

  /** PE 总列数 */
  val totalN: Int = arrayN * clusterM //fp16/int16 角度
  val totalNx2: Int = totalN * 2
  val totalNx4: Int = totalN * 4

  /** A 侧有效位宽 */
  val aEffW: Int = aW

  /** 是否包含浮点格式（用于编译期条件化生成规格化硬件） */
  val hasFp: Boolean = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)

  /** 是否包含整数格式（用于整数累加位宽约束校验） */
  val hasInt: Boolean = supportedFmts.contains(DataFormat.INT4) || supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)

  /** 是否包含 MX 格式（MXFP8 E4M3 / E5M2）——历史谓词，保留原义供既有外部引用
    * （HiveCore 顶层 dmaScaleA/dmaScaleB 端口与 scale DMA 子模块生成门控等）。 */
  val hasMx: Boolean = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2) || supportedFmts.contains(DataFormat.MXE2M1)

  /** Phase 1：MX 硬件生成谓词收敛。
    *   - hasMx8 = MXFP8（E4M3/E5M2），与 hasMx 定义逐字相同（= 现 hasMx 语义）；
    *   - hasMx4 = MXFP4（E2M1），与 HiveWorkUnit 内同名 private 谓词对齐。
    * 凡「生成 MX 硬件」的判据由 hasMx 改为 (hasMx8 || hasMx4)（新增并列，false 分支逐字保留）。
    * 【#155 MJ1：旧「恒等性铁律」契约已由用户主动作废】
    *   旧契约原文：「对任何不含 MXE2M1 的配置 hasMx4=false，故 hasMx8||hasMx4 == hasMx8 == hasMx，
    *   取值完全不变 ⟹ 门控解析出的硬件结构逐位不变（verilog/verilog_mx 两套基线
    *   normalized-diff=0 的根本保证）」。该契约的前提是「存在不含 MXE2M1 的出厂配置」，
    *   而本轮已把 MXE2M1 升格为默认必选、并用下方新增 require 强制「supportedFmts 含任一
    *   MX 格式 ⟹ 必含 MXE2M1」（字面上与上方既有 require 合成 hasMx4 ⟺ hasMx8，但那条既有
    *   require 实为恒真式，详见其下方注释），前提不再成立 ⟹ 契约作废。
    * 【新基线预期（供 #142 重发射时对照）】三套发射产物（verilog / verilog_mx / verilog_mxfp4）
    *   的 cfg 全部 hasMx4=true ⟹ fp4 支路一律 elaborate。预期非零 diff 的模块至少包含：
    *     · Hive_HiveOutCvt.sv —— q2Seq / packed4 / 次正规与深下溢新臂全部【无门控】
    *       （MJ1 取证：`grep 'supportedFmts|hasMx|isMx4|nGroups'` 在该文件只命中 :40/:92/:294/:297），
    *       故只要 hasMx4=true，E2M1 输出支路的门数必然进网表；
    *     · Hive_HiveWorkUnit.sv / Hive_HiveMacFpMx.sv —— fp4 quad-lane 通路
    *       （8×HiveCvtOpMx + 4 级 HiveMacFpMx 左结合级联）与运行期 Mux(isMx4, quad, dualLane)；
    *     · HiveCoreCalcConfig 的 elemsPerLane 由 2-bit 扩为 3-bit、errCode 8 / Fix-4 errCode 3
    *       两支 fp4 运行期守护进入网表。
    *   结论：三套产物的【结构】不再有 normalized-diff=0 关系；数值正确性改由 TB 侧金锚
    *   （OCP MX / IEEE-754 独立推导）+ 归一化 stdout 基线（打印数字逐字节不变）守护。 */
  val hasMx8: Boolean = hasMx  // 语义别名：MXFP8 生成判据，与历史 hasMx 同值，单一真源
  val hasMx4: Boolean = supportedFmts.contains(DataFormat.MXE2M1)

  // Phase 1 门控半收敛兜底(v1)：hasMx(=hasMx8) 仍是 scale DMA 端口/子模块、io.sum 路由、
  // HiveOutCvt fp32 支路的唯一主开关；fp4-only 会静默丢弃 quad-lane 结果且无 scale 供数。
  // 真·fp4-only 支持需全量迁移 hasMx→(hasMx8||hasMx4) 并解禁 io.sum 禁改区，属 v2 独立立项。
  require(!hasMx4 || hasMx8,
    s"HiveCoreConfig: MXFP4(E2M1) v1 必须与 MXFP8(E4M3/E5M2) 同时启用" +
    s"（hasMx 仍是 scale DMA 端口与 io.sum 路由主开关，fp4-only 会静默算错），当前 supportedFmts=$supportedFmts")

  // (#155 MJ1) 用户设计律编码为编译期不变式：「有 mxfp8 就有 mxfp4，是必选项」。
  //   本条与上方 require(!hasMx4 || hasMx8) 在字面上合起来等价于【hasMx4 ⟺ hasMx8】：
  //     · MX 族要么全在（fp8 与 fp4 同时启用），要么全不在；不存在「只带 fp8」的合法配置。
  //     · FP-only / INT-only / 无 MX 的配置：hasMx8=false 且 hasMx4=false ⟹ 两条 require
  //       的前件均为假 ⟹ 都不触发，仍然合法（#155 已用探针实测：{FP16,BF16} /
  //       {INT16,INT8,INT4} / 空集 三种非 MX 集全部构造成功，见 build/155 报告）。
  //   ★【#155 实测校正】上方那条 require(!hasMx4 || hasMx8) 其实是【恒真式、从未生效】：
  //     hasMx8 = hasMx = contains(MXE4M3) || contains(MXE5M2) || contains(MXE2M1)，
  //     而 hasMx4 = contains(MXE2M1) ⟹ hasMx4=true 必然推出 hasMx8=true，前件恒成立。
  //     探针实证：supportedFmts={MXE2M1}（fp4-only）构造【成功】、hasMx8=true、hasMx4=true，
  //     即上方那条拦不住 fp4-only（其注释所称「fp4-only 会静默算错」的兜底并未生效）。
  //     故本轮真正长牙的不变式是【本条】，其语义可精确改写为：
  //         supportedFmts 含任一 MX 格式  ⟹  必含 MXE2M1
  //     （比「有 mxfp8 就有 mxfp4」更强一档，且正是用户设计律的自然推广：MX 族不许残缺）。
  //     上方那条【逐字保留不动】——保留其意图留痕与历史可追溯性；是否要把它改成真正拦得住
  //     fp4-only 的判据（例如 require(!hasMx4 || (contains(MXE4M3) || contains(MXE5M2)))）
  //     属独立事项，需用户裁定，本轮不动。
  //     ⟹ 请勿因为「上方那条是恒真式」就顺手删掉本条：本条是唯一有牙的那条，删掉即回到
  //       MJ1 的缺陷面（E2M1 输出支路无门控、三套重发射基线不可控）。
  //   消息只用 ASCII（firtool 对非 ASCII 断言消息会发射失败）。
  require(!hasMx8 || hasMx4,
    "HiveCoreConfig: MXFP8(E4M3/E5M2) requires MXFP4(E2M1) -- per user design rule " +
    "\"if mxfp8 is supported then mxfp4 is mandatory\". Together with the require above " +
    "this pins hasMx4 <=> hasMx8 (the MX family is all-in or all-out; FP-only / INT-only / " +
    "no-MX configs keep both false and stay legal). Current supportedFmts=" + supportedFmts)

  /** P3a C 侧量化 scale 行位宽：每【minBlockSize=16】元素量化块一个 8-bit E8M0 scale 字节
    * （HiveOutCvt.scaleRow 输出宽度，P3b dmaCScale 通道行宽同源）。
    * (#155 MJ2) 订正本行注释的 2× 错：原文写「每 8 元素量化块一个字节」，与实际表达式
    *   `(totalN / minBlockSize) * 8` 相差 2 倍（minBlockSize=16，不是 8）。实际量纲：
    *     cScaleRowW = (totalN / 16) * 8  =  totalN/2  **bit**  =  totalN/16 **字节**。
    *   totalN=16 时 ⟹ 8 bit = 1 字节/行。同类 2× 错另有三处，已一并订正：
    *   HiveOutCvt.scala「cScaleRowW = nGroups*8」、HiveCoreElaborate.scala 与
    *   HiveCoreDmaWrOnly.scala 的「cScaleRowW = totalN/8 字节」。 */
  //DMA    
  val minBlockSize = 16

  // (#155 MJ2) 零 RTL 成本 / 零面积 / 零行为的编译期护栏：把 minBlockSize 与 MXBlockSize
  //   枚举的【最小档】绑死。MXBlockSize = {M16=0, M32=1} ⟹ 最小档 = 16 << 0 = 16。
  //   今天这条 require 是恒真式（16 == 16），故对 elaboration 结果零影响；它的价值在于
  //   日后有人收窄/放宽 MXBlockSize 枚举（历史上它曾是 4 档 8/16/32/64）或改 minBlockSize 时
  //   立刻在编译期炸，而不是让 cScaleRowW 静默变成 2× 错的端口宽度。
  // 【为什么必须绑死】cScaleRowW 用的是【编译期常量】minBlockSize，而运行期实际块大小
  //   cMxBlk 是寄存器字段（16<<blkSel，blkSel∈{0,1}）。二者只在 blkSel=0 时一致：
  //     · blkSel=1（cMxBlk=32）时，一行 totalN 个元素只该产 totalN/32 个 scale 字节，
  //       而端口物理宽度仍是 totalN/16 字节 ⟹ 契约前进量与端口宽度相差 2 倍。
  //     · 今天 totalN=16 时良性（HiveOutCvt 内 nGroups=totalN/8 ⟹ scaleWire 宽 totalN bit，
  //       :615 io.scaleOut.bits 目标宽 totalN/2 bit，截断掉的高位与低位同属 block16(0)、值相同，
  //       Ryan §E.2 已独立验证无害）。
  //     · 爆点：totalN >= 32 且 cMxBlk=32 ⟹ 2 倍重叠，后一拍首字节被前一拍次字节覆盖
  //       ⟹ C-scale 外部内存布局【静默损坏】，且顶层 dmaCScale.req.payload.data 端口宽度
  //       对下游 SoC 是 2× 错的。
  // ★cMxBlk=32 需要 cScaleRowW 随 blkSel 可变（运行期 Mux 或多套行宽）；totalN>=32 之前
  //   必须先解此耦合。此前唯一守护只在 TB 侧（HiveDebugModel 的 cMxBlk require），属测试期。
  require(minBlockSize == (16 << MXBlockSize.all.map(_.litValue.toInt).min),
    "HiveCoreConfig: minBlockSize (" + minBlockSize + ") must equal the smallest encodable " +
    "MXBlockSize level (16 << " + MXBlockSize.all.map(_.litValue.toInt).min + "), because " +
    "cScaleRowW = (totalN/minBlockSize)*8 is a COMPILE-TIME port width while the runtime " +
    "block size cMxBlk = 16<<blkSel is a register field. cMxBlk=32 needs cScaleRowW to vary " +
    "with blkSel; that coupling must be resolved before totalN>=32 (else the C-scale external " +
    "memory layout is silently corrupted by 2x overlap).")

  val cScaleRowW: Int = (totalN / minBlockSize) * 8
  val maxScalePerCyele = totalNx4 / minBlockSize
  val scaleAW: Int = 8 * maxScalePerCyele
  val scaleBW: Int = 8 * maxScalePerCyele
  

  /** C 累加器有效位宽（与 HiveComb 推导逻辑一致）。
    * cW==0 自动推导：含浮点或 MX 时至少 40（延迟规格化 {sign,exp8,mant31} 需要），
    * 纯整数时取 autoAccW（aEffW+bW+log2Up(totalN)，累加防溢出）。
    * Phase 1：MX 判据由 hasMx 收敛为 (hasMx8 || hasMx4)，使 fp4 quad-lane 复用同款
    * 40-bit 延迟规格化累加器的配置在 cW==0 时也自洽推导出 cEffW>=40；对不含 MXE2M1
    * 的配置 hasMx4=false，取值与 hasFp||hasMx 逐位相同（零 diff）。 */
  val cEffW: Int = {
    val autoAccW = aEffW + bW + log2Up(totalN)
    if (cW != 0) math.max(cW, bW)
    else if (hasFp || hasMx8 || hasMx4) math.max(40, autoAccW)
    else autoAccW
  }

  // --- 各 DMA 通道外部数据位宽：分别匹配各自 buffer 行宽（一拍一行，
  // ext 与 buffer 等宽直连，无位宽适配/拆包逻辑） ---

  /** A 通道外部 DMA 数据位宽（= A buffer 行宽 totalN*aEffW） */
  val aExtW: Int = totalN * aEffW

  /** B 通道外部 DMA 数据位宽（= B buffer 行宽 totalN*bW） */
  val bExtW: Int = totalN * bW

  /** C 通道外部 DMA 数据位宽（= C buffer 行宽 totalN*cEffW） */
  val cExtW: Int = totalN * cEffW

  /** C 写回每行最大拍数上界（编译期）：= totalN*32/cDmaExtW，按最密 32-bit 元素口径
    * 计算，仅供计数器/credit 位宽取上界（实际运行期拍数由 cFmt 决定，见下）。
    * 运行期拍数（256 默认 × totalN=16）：fp32/int32 = 2 拍/行；fp16/bf16/int16 = 1 拍/行；
    * mxfp8/int8 = 1 拍/行（半占用，高位零填充）；mxfp4 = 1 拍/行（四分之一占用，高位零填充）。
    * 本期禁止「双行合拍」，占用率契约见重构计划裁定 9。 */
  val cBeatsPerRowMax: Int = totalN * 32 / cDmaExtW

  // P3a：新增 regs(11) = cScaleAddr（C 侧量化 scale 写回基地址），寄存器组 11 → 12。
  // 旧软件只写 0x00..0x0A，新增寄存器默认 0 不影响既有行为。
  val registerNumRW: Int = 12
  val registerNum: Int = 12



  //MNK width
  val mnkWidth: Int = 16

  // --- elaboration-time require 校验 ---
  // cW == 0 表示自动推导（见 cEffW），此时跳过显式位宽比较
  require(cW == 0 || cW >= aW, s"HiveCoreConfig: cW($cW) 必须 >= aW($aW)")
  require(cW == 0 || cW >= bW, s"HiveCoreConfig: cW($cW) 必须 >= bW($bW)")
  // 延迟规格化架构约束：含浮点格式或 MX 格式时 cEffW 必须 >= 40，
  // 以容纳 40-bit 延迟规格化格式 {sign(1), exp(8), mantissa(31)}。
  // MX 复用延迟规格化 fp 累加器。
  // Phase 1：MX 判据 hasMx → (hasMx8 || hasMx4)；fp4 quad-lane 同样复用 40-bit 延迟规格化
  // 累加器。对不含 MXE2M1 的配置 hasMx4=false ⟹ 判据取值与 hasFp||hasMx 逐位相同（零 diff）。
  if (hasFp || hasMx8 || hasMx4) require(cEffW >= 40, s"HiveCoreConfig: 含浮点/MX 格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  // 整数累加约束：含整数格式时 cEffW 必须 >= aW+bW，以容纳整数积累加防溢出
  if (hasInt) require(cEffW >= aW + bW, s"HiveCoreConfig: 含整数格式时 cEffW($cEffW) 必须 >= aW+bW(${aW + bW})（整数累加防溢出需要）")
  require(arrayN >= 4, s"HiveCoreConfig: arrayN($arrayN) 必须 >= 4")
  require(clusterM >= 1, s"HiveCoreConfig: clusterM($clusterM) 必须 >= 1")
  require(aBufferDepth > 0, s"HiveCoreConfig: aBufferDepth($aBufferDepth) 必须 > 0")
  require(bBufferDepth > 0, s"HiveCoreConfig: bBufferDepth($bBufferDepth) 必须 > 0")
  require(cBufferDepth >= 2*totalN, s"HiveCoreConfig: cBufferDepth($cBufferDepth) 必须 > 2*totalN($totalN)")
  require(addrWidth > 0, s"HiveCoreConfig: addrWidth($addrWidth) 必须 > 0")
  require(addrWidth <= 32, s"HiveCoreConfig: addrWidth($addrWidth) 必须 <= 32")
  // --- C 写回总线（cDmaExtW）契约 require 族（P0 重构：默认 256、只允许 2 的 N 次幂，
  // 废弃旧 0=满宽哨兵与 5B/元素布局；窄总线拆拍与密集打包由 P2 落地，本阶段只立契约） ---
  // 位宽只允许 2 的 N 次幂（用户裁定；同时拦截负值/0 等非法值）
  require(isPow2(cDmaExtW), s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须是 2 的 N 次幂")
  // 行拍数上界（最密 32-bit 元素口径 totalN*32/cDmaExtW）必须为整数：
  // 计数器/credit 位宽按 cBeatsPerRowMax 取上界，非整除会使上界语义失效
  require(totalN * 32 % cDmaExtW == 0, s"HiveCoreConfig: totalN*32(${totalN * 32}) 必须能被 cDmaExtW($cDmaExtW) 整除（行拍数上界为整数）")
  // 取值区间 [totalN*8, totalN*32]：下限保证 8-bit 输出格式（mxfp8/int8）一行单拍可容，
  // 上限保证 32-bit 输出格式（fp32/int32）拍数 ≥ 1（不超过整行最密位宽）
  require(totalN * 8 <= cDmaExtW && cDmaExtW <= totalN * 32,
    s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须位于 [totalN*8(${totalN * 8}), totalN*32(${totalN * 32})]（8-bit 格式单拍可容、拍数上界整数）")
  // mxfp4 半字节几何前提（P0 保留、措辞适配新位宽族）：mxfp4（E2M1）输出为半字节/元素，
  // 窄总线使一行需拆多拍（totalN*4 > cDmaExtW）时，每拍元素数（cDmaExtW/4）必须为偶数，
  // 否则拍段在半字节中间切开导致内存布局错位；单拍整行（totalN*4 <= cDmaExtW，
  // 256 默认 × totalN=16 天然成立）天然豁免。量化判据门控在运行期，此处为编译期几何前提。
  // 【显式声明（评审裁定留痕）】本约束对 hasMx 配置的全部窄总线配置生效——含量化
  // （mxfp8/mxfp4）与非量化输出；下游配置窄总线时不得假设非量化输出可豁免本约束。
  // Phase 1：判据 hasMx → (hasMx8 || hasMx4)，覆盖仅含 mxfp4(E2M1) 的配置；对不含
  // MXE2M1 的配置 hasMx4=false ⟹ 取值与 hasMx 逐位相同（零 diff）。
  if (hasMx8 || hasMx4) require(totalN * 4 <= cDmaExtW || (cDmaExtW / 4) % 2 == 0,
    s"HiveCoreConfig: hasMx 窄总线配置要求 mxfp4 每拍元素数（cDmaExtW/4=${cDmaExtW / 4}）为偶数（拍内整字节打包），" +
    s"该约束覆盖 hasMx 配置的全部窄总线（含量化与非量化输出），当前 cDmaExtW=$cDmaExtW")
  require(scaleBufferDepth > 0, s"HiveCoreConfig: scaleBufferDepth($scaleBufferDepth) 必须 > 0（hasMx 时 scale FIFO 深度；非 MX 死路参数）")
  // MX 阵列几何硬约束：32-K scale 块 = 16 lane × 2 元素/拍 = 单 pass，该等价关系仅在
  // totalN==16 时成立（scaleBDma lineTarget=kTile、executor 每 sLOAD_B 窗口 pop scaleB
  // 均硬编码此假设）；totalN≠16 的 hasMx 配置会静默数值错或挂死，故在
  // elaboration 期直接拒绝。若未来支持其它 totalN，需同步重推上述两处节奏。
  // Phase 1：判据 hasMx → (hasMx8 || hasMx4)。fp4 quad-lane 几何自洽性确认（仅判据/注释层面，
  // 几何数值改动属 Phase 3）：totalN==16 时 fp4 每 lane 打包 4 个 4-bit 元素（elemBits=4），
  // 16 lane × 4 elem = 64 K/pass，恰与 mxfp4 的 64-元素 scale 块（block=64）单 pass 对齐，
  // 故 totalN==16 对 fp4 quad-lane 仍是自洽前提（与 fp8 dual-lane 的 16 lane×2 elem=32K/pass、
  // block=32 单 pass 同构）。对不含 MXE2M1 的配置 hasMx4=false ⟹ 取值与 hasMx 逐位相同（零 diff）。
  if (hasMx8 || hasMx4) require(totalN == 16, s"HiveCoreConfig: MX 要求 totalN==16（fp8：32-K 块=16 lane×2 元素；fp4：64-K 块=16 lane×4 元素，均单 pass），当前 totalN=$totalN")
  // MX SIMD 双元素/拍：16-bit lane 打包 2 个 8-bit 元素（低字节=偶 k、高字节=奇 k），
  // PE 内 4×HiveCvtOpMx + 2×HiveMacFp 级联，故 aW/bW 必须 >= 16。
  // Phase 1：判据 hasMx → (hasMx8 || hasMx4)。fp4 quad-lane 同样要求 16-bit lane（打包 4 个
  // 4-bit 元素：io.a(3,0)/(7,4)/(11,8)/(15,12)），故 aW/bW>=16 对 fp4 亦成立。对不含 MXE2M1
  // 的配置 hasMx4=false ⟹ 取值与 hasMx 逐位相同（零 diff）。
  if (hasMx8 || hasMx4) require(aW >= 16 && bW >= 16, s"HiveCoreConfig: MX SIMD 需要 aW/bW >= 16（fp8：16-bit lane 打包 2 元素；fp4：打包 4 元素），当前 aW=$aW bW=$bW")
}



