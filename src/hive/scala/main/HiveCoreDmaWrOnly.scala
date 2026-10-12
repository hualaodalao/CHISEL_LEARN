/** HiveCoreDmaScaleWr — C 侧量化 scale 写回 DMA（P3b，dmaCScale 通道）。
  *
  * 职责：量化输出（outFmt 1xx）运行时，cDma（WrOnly）每写出一行量化密集行
  * 即同拍产出一行 E8M0 块 scale（cvt.io.scaleOut，宽 cScaleRowW
  * = (totalN/minBlockSize)*8 = totalN/2 **bit** = totalN/16 **字节**；totalN=16 时 8 bit。
  * (#155 MJ2) 订正：原文写「cScaleRowW = totalN/8 字节」，与实际相差 2 倍
  * （minBlockSize=16 而非 8，且原式漏了 bit/字节 量纲）。同类 2× 错另见
  * HiveCoreElaborate 的 P3b 端口变更留痕与 HiveOutCvt 的 scaleRow 注释，均已一并订正），
  * 经顶层深 2 cScaleFifo（P2 裁定 5：唯一 scale 缓冲，已从 Scratchpad 上移）缓冲后
  * 由本模块写到外部存储（基址 = regFile.cScaleAddr）。
  *
  * 遍历几何（与 cDma 的 C 数据写回同序，保证 scale 行与量化数据行逐行对应）：
  *   内层沿 M 方向（实际 M 行 = regFile.m，每行一拍、地址 + cScaleRowAddressOffset
  *   = ceil(n/blk) 字节），外层扫 nTile 个 N tile（轮间跳 cScaleColTileAddressOffset
  *   = totalN/blk 字节）。
  *
  * 【地址模型——「基址 + 轮偏移」线性模型（与 cDma sNEXT_N 现已同构）】
  *   colAddr 寄存器只存「下一轮的累计列偏移」，轮间跳转时
  *     curAddr := baseAddr + colAddr;  colAddr := colAddr + colTileOffset
  *   每轮起始地址恒等于 baseAddr + nt*colTileOffset，无累积误差、易对账。
  *   【#133 撤销旧声明】本段原写「cDma 数据通道的块间跳转用 curAddr += colAddr
  *   三角累积，是已知遗留问题（禁动区）；本模块为新通道，刻意不复制」——该声明已
  *   过期：cDma（HiveCoreDmaWrOnly.sNEXT_N）已改为 `curAddr := io.regFile.cAddr +
  *   colAddr`，两个 C 侧写通道现同为「基址 + 轮偏移」线性模型，不存在「禁动区」的
  *   三角累积通道。历史教训留痕：当年正是「金锚按缺陷 RTL 反向拟合 + 头注释把它标成
  *   禁动区」两件事互相背书，使该缺陷在所有 nTiles≥2 用例上假绿潜伏。
  *
  * 反压/在途记账（写通道简化版）：
  *   - req 仅在 sTRANSFER 且 bufPop 有数据时发出，且被在途上限（顶层 cScaleFifo 深度 2）
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
  * 【config-err 场景行为契约（评审留痕，C1 修复后更新）】量化配置违约
  * （blk>totalN/不整除、mxfp4 奇 n）时顶层 start 仍随 executePulse & cQuantRun
  * 拉高（违约由 Executor 同拍置 errState 拦截），本模块照常进入 sTRANSFER；但
  * Executor 不进计算、cScaleFifo 恒空，本模块停留于 sTRANSFER（busy 恒高、done
  * 不发）。恢复路径（C1 修复）：下次 EXECUTE 时若为量化运行（cQuantRun），start
  * 重载恢复（既有路径）；若为非量化运行（!cQuantRun），顶层经 io.abort 强制回
  * sIDLE（abort = executePulse & !cQuantRun），busy 归低，dmaCBusy OR 门解锁，
  * 非量化运行正常进行。io.done 不得在 config-err 场景接入完成链。
  *
  * @param cfg HiveCore 配置（行宽固定 = cfg.cScaleRowW，每 8 元素块 1 字节）
  */

import chisel3._
import chisel3.util._

/** HiveCoreDmaWrOnly — 只写 DMA 引擎（多拍发射器，P2 两段式架构）。
  *
  * 职责：消费顶层 HiveOutCvt 输出的转换后密集行（行粒度握手，fire 即锁存进本模块
  * 行寄存器），按 tile 自动地址生成、以 cDmaExtW 位宽逐拍发往外部存储。
  * 拍拆分归本模块（计划裁定 4：cvt = 转换+行寄存器，cDma = 多拍发射器）。
  *
  * 搬运对象固定为结果矩阵 C[mTile, nTile]。C 写回固定「内 M 外 N」遍历：
  * C buffer 为严格 FIFO（上游顺序 = Executor push 顺序），写回顺序与 loopMode 无关。
  *   - 内层沿 M 方向（实际 M 行 regFile.m，边界感知不含 padding，
  *     每行地址 + cRowAddressOffset），外层扫 nTile 个 N tile
  *     （块间跳 cColTileAddressOffset）
  *
  * 地址模型（字节地址；只引用本设计实际存在的信号）：
  *   第 nt 轮块首址 = io.regFile.cAddr + nt*calcConfig.cColTileAddressOffset
  *   块内行首址     = 块首址 + lineCounter*calcConfig.cRowAddressOffset
  *   拍内地址       = curAddr + beatCnt × calcConfig.cBeatAddrStep（顶层单源运行期步进）
  * 块间跳转 = 「基址 + 轮偏移」线性模型（与同文件 HiveCoreDmaScaleWr 同构）：
  *   sNEXT_N 拍执行 curAddr := io.regFile.cAddr + colAddr; colAddr := colAddr +
  *   cColTileAddressOffset，其中 colAddr 锁存初值 = 1 轮偏移（见 startReload），
  *   故第 nt 轮起始地址恒 = cAddr + nt*cColTileAddressOffset，无累积误差、易对账。
  * 【#133 订正两条伪权威表述，勿恢复】
  *   (1) 旧注「块间跳转采用『跳回起点再前移一个块』的三角累积增量式计算（金锚背书，
  *       行级不动）：jump = blockOffset - (innerRows - 1) * rowStep」是更早提交的化石
  *       公式、与本模块代码不符；「金锚背书」亦非权威——当时金锚
  *       （HiveSimCommon.cAddrSeqDense）本身就是按缺陷 RTL 反向拟合出的三角累积闭式
  *       base + nt*(m-1)*rowOff + nt*(nt+1)/2*colOff，与「curAddr := curAddr + colAddr」
  *       逐字同源，两者互相背书使所有 nTiles≥2 用例的 dmaC 逐拍地址硬断言退化为
  *       「RTL 与其镜像自比」而恒真假绿（FP32/N=32 下 nt=1 首址实得 base+0xFC0、
  *       正确值 base+0x40）。金锚现已改回张量语义绝对式，RTL 与金锚独立推导后相等。
  *   (2) 旧注「与 HiveCoreExecutor 侧的『C 写回地址别名』约定一致」——该别名在 main
  *       源码中无任何定义（死引用，全仓仅两处注释命中），已删除；C 侧地址语义的唯一
  *       实际来源是 regFile.cAddr 与 calcConfig 的 cRow/cColTile/cBeatAddrStep 三偏移。
  *       【#136-RW1 采纳选项 (b)】本条留痕改用【描述性表述】、不复述原名裸 token，使
  *       grep 式死引用审计在 src/hive/scala/main/ 下对该名字 0 命中，无需读上下文区分
  *       「活引用」与「删除留痕」（同款处理见 HiveCore.scala 与 HiveCoreDmaRdOnly.scala
  *       的 #133 死引用清理段）。
  *
  * 【#136-RW3 提交纪律留痕：#133 在 main 侧的触碰清单（实测 5 段注释 + 0 功能行）】
  *   背景：本文件与 HiveCore.scala / HiveCoreDmaRdOnly.scala 在工作树里【同时承载他任务的
  *   功能行】，故「#133 仅注释级」这一结论【无法由 git diff 自证】、只能靠任务标注推断。
  *   为让后续能按任务拆 commit，此处逐段登记 #133 的 main 侧触碰点。下列行号是 #136 全部
  *   编辑落地后的实测值；定位请以【文本锚点】为准，行号会随注释改动漂移（本会话已发生四次
  *   锚点漂移，例如 #118 的 W6 锚点从 L446-449 漂到 L566-567）。
  *   (1) HiveCoreDmaWrOnly.scala L17-22 ——【#133 撤销旧声明】撤销「cDma sNEXT_N 三角累积
  *       属禁动区」的过期声明，改述为两个 C 侧写通道同为「基址 + 轮偏移」线性模型。
  *       #136 未再改动此段。
  *   (2) HiveCoreDmaWrOnly.scala L72-87 ——【#133 订正两条伪权威表述】其一撤销化石公式
  *       jump = blockOffset - (innerRows-1)*rowStep 并说明金锚反向拟合的机理；其二删除
  *       Executor 侧「C 写回地址别名」死引用。其中 L84-87 由 #136-RW1 追加（去裸 token）。
  *   (3) HiveCore.scala L155-161 ——【#133 死引用清理】calcConfig 生产者注释里「与 Executor
  *       侧三个 tile 首址别名保持一致」的伪约定删除留痕。其中 L159-161 由 #136-RW1 追加。
  *   (4) HiveCoreDmaRdOnly.scala L70-78 ——【#133 死引用清理】A/B tile 首址别名死引用的删除
  *       留痕。#133 当时只订正命名、未校验同块的化石公式，遗留问题由 #136-KW3 修：KW3 重写
  *       其上方 L26-68（六条跳转分通道描述 + 两支闭式 + 化石公式撤销），L70-75 由 #136-RW1
  *       追加去裸 token，L76-78 由 #136-KW3 追加信号名对照。
  *   (5) HiveCoreElaborate.scala L67-71 ——【#133 撤销】把「cDma sNEXT_N 三角累积地址模型」
  *       从「禁动区典型项」清单移除 + 教训留痕。#136 未再改动此段。
  *   ★ 功能行归属：#133 在 main 侧的功能行改动数 = 【0】。本文件 sNEXT_N 分支的
  *     `curAddr := io.regFile.cAddr + colAddr`（文本锚点定位）是【用户手工修复】、不属
  *     #133；#133 只把金锚与注释改到与该修复一致。
  *   注：#136 任务描述写「4 段注释」，实测为 5 段（漏计 HiveCoreElaborate.scala 那段），
  *     差异已在 #136 报告第 (10) 项「与任务前提矛盾之处」明说。
  *
  * 【#136-RW3 拆 commit 建议：main 侧功能行按任务归属（注释级改动可单独成一 commit）】
  *   HiveCore.scala：
  *     · respForTracing 端口声明 + tracing FSM（tracingSt/tracingPcReg/tracingTagReg 与
  *       io.respForTracing.valid/payload 全部驱动）        → tracing 任务
  *     · dmaCScale 位宽由字面 8 还原为 cfg.cScaleRowW      → 裁定2
  *     · 两处 `when(cmd.regXAddr === 0x07.U) { when(cmd.dataX(0)) { doneFlag := false.B } }`
  *       的删除（clear_done 移除）                        → B1
  *     · `when(calcConfig.io.err && !startPulse) { errFlag := true.B }` 直连 → B2
  *     · blkLatchCnt/blkLatchFull 新增 + scratchpad.io.cPop.ready 与 cvt.io.in.valid 各加
  *       `&& !blkLatchFull`（同源双门）                    → #122
  *     · scaleB lockstep 注释（该段无功能行）              → #118
  *   HiveCoreDmaRdOnly.scala：
  *     · 删除 bNextKTileAddressOffset 死寄存器             → B5
  *     · sIDLE 锁存与轮内切 k-tile 两处 B 地址改写（降序起点项 (totalN-1)*rowOffset +
  *       lastBTileStartAddr 累加模型）                     → B5
  *   HiveCoreDmaWrOnly.scala：
  *     · sNEXT_N 的 `curAddr := curAddr + colAddr` → `curAddr := io.regFile.cAddr + colAddr`
  *       （本文件唯一一对功能行改动）                      → 用户手工修复
  *   HiveCoreElaborate.scala：非注释功能行 = 0
  *
  * 【#136-RW3 结论：回归数字不可归因于单一任务】#136 收尾实测（9 套件全量）：
  *   Total 80 / Suites: completed 9, aborted 0 / succeeded 77, failed 3, canceled 0,
  *   ignored 0。任务书给的基线是 76/73/3；差的 +4 全部来自【并行任务 #137】在 #136
  *   会话期间给 HiveOutCvtSpec 追加的 4 个 INT4 用例（该套件 18→22，4 例全绿），
  *   #136 自身新增用例数 = 【0】。
  *   这恰好是本段结论最强的证据：该数字是【整棵未提交工作树的联合属性】—— 它同时
  *   包含 tracing / 裁定2 / B1 / B2 / B5 / #122 / #118 / 用户 dmaC 修正 / #133 / #136 /
  *   #137 的改动，甚至会在【你跑回归的过程中】被并行任务改掉。故【不可】把任何一组
  *   数字归因于单一任务落地；按上述建议拆 commit 后，每个 commit 都必须重跑全量回归
  *   重新取数，不得复用此处或任务书里的任何基线。
  *
  * 【#136-RW3 交接：既存红的判别指纹（勿再误归因于金锚族）】
  *   #127 的两条红：下列三条【同时成立】⇒ 属 #127（量化数据路 / dmaCScale 反压），不属金锚：
  *     (a) 失败信息是 `DMA-C store beat #0 data mismatch`（data 类，非 addr 类）；
  *     (b) 同次运行 dmaC 逐拍地址对账【全绿】。QuantSpec 内的取证形式：地址硬对账
  *         （`reqAddr should be(expCAddrSeq(...))`）与数据硬断言在同一拍内【先后相邻】，
  *         失败落在后者 ⇒ 前者已通过；DebugCase 家族的取证形式则是打印
  *         `地址对账不一致拍数=0`。两种形式等价，都表明缺陷不在地址生成侧。
  *     (c) 失配值与 pre-fix baseline 同值（#133 改金锚前后不变）。
  *   #118 的回归已给出这三项动态证据确证：仍恰为 2 条红、仍为 data 类、地址对账仍为 0。
  *   另一条既存红 `errCode should be 2`（0 was not equal to 2）属 #124（CalcConfig 退化）。
  *   三条红【不得】靠金锚侧修改消除；#133/#136 均未触碰它们，也不得让它们增减或变形。
  *
  * 运行期拍数（裁定 6）：beatsPerRowEff = ceil(totalN×elemBits/cDmaExtW)，由 io.outFmt
  *（顶层唯一源 cOutFmtEff）解码：256 默认 × totalN=16 下 fp32/int32=2 拍、
  * fp16/bf16/int16/mxfp8/int8/mxfp4=1 拍（半/四分之一占用高位零填充，禁止双行合拍）。
  * ★(#169-S2) 本句「占用高位零填充」描述的是【内部 wire dmaExtWrIF.req.payload.data】
  *   （见下方 `dmaExtWrIF.req.payload.data := (io.bufPop.payload >> ...)` 那一行，该层确实
  *   仍是「数据在低位、高位零填充」，#166 未改它）；【对外端口 io.dmaExtWrIF.req.payload.data】
  *   自 #166 起已按 addrShiftBytes 同源左移到它在总线字内应在的 byte lane，对外【不再是低位口径】。
  * 末拍推进单一判据：req.fire && beatCnt===beatsPerRowEff-1 → 行推进（行级结构不动，
  * 仅叠拍内偏移）；行/块跳转自动对齐、免维护拍地址寄存器。
  *
  * 反压策略（【#136-KS6 订正】原文以已死的行寄存 lineValid/lineData 为口径，与代码不符）：
  *   - 实际口径（唯一权威 = 本文件下方 `io.bufPop.ready := ...` 那一行）：
  *       bufPop.ready = (state === sTRANSFER_M) & dmaExtWrIF.req.ready & lastBeat
  *     即【组合直通 + 末拍弹出】：拍数据 = io.bufPop.payload >> (beatCnt×cDmaExtW) 取低
  *     cDmaExtW 位（★#169-S2：内部 wire 口径，对外端口的 lane 移位见下方 addrShiftBytes
  *     那一行），本模块【不自行锁存整行】；一行在全部 beatsPerRowEff 拍期间由上游
  *     （顶层 cvt 的行寄存 → C FIFO）保持稳定不弹，只有末拍（lastBeat = beatCnt ===
  *     calcConfig.cDmaBufBeatTar）且外部 req.ready 为高的那一拍才拉高 bufPop.ready 弹出
  *     该行 ⇒ 仍是「行粒度握手、每行只收一次」，但收行时刻在【末拍】而非首拍。
  *   - 原文「bufPop.ready = sTRANSFER_M 且行锁存空」描述的是已下线的 lineValid 二态锁存
  *     模型（旧 HiveCoreDma.scala 时代）。拆分重构后行寄存职责已归顶层 cvt（全设计唯一
  *     一份，见 HiveCore.scala 的 #122「禁动区声明：不恢复 lineData/lineValid」段），
  *     本模块原有的 lineData/lineValid 死寄存器已由 #125 删除（清理段见下方 beatCnt 声明处）。
  *   - 「与 RdOnly 对称」亦不成立：RdOnly 的信用计数 ostCredit 【参与 req.valid 门控】
  *     （`req.valid := ostCredit =/= 0.U & state === sTRANSFER`），即由 buffer 余量决定
  *     发不发 cmd；本模块的 req.valid = (state === sTRANSFER_M) & bufPop.valid、
  *     bufPop.ready = (state === sTRANSFER_M) & req.ready & lastBeat，【两者都不含
  *     ostCredit】——本模块的 ostCredit 只在 sDONE 用作「在途写拍已排空」的放行判据
  *     （`.elsewhen(ostCredit === 0.U)` 才发 done）。同文件的 HiveCoreDmaScaleWr 又是
  *     第三种口径（req.valid 含 `ostCredit =/= depth`），故三者互不对称，勿互相套用。
  *   - 数据在外部 writeData.ready（= dmaExtWrIF.req.ready）为高时才发拍，天然支持逐拍
  *     暂停（此条原文正确，保留）。
  *
  * 门控语义：storeGate 门控位于上游（顶层 cPop mux ready 源 + cvt.in.valid 门），
  * 本模块由 peekBlock/doneBlock 块协议与 Executor 同步；非末 K pass 期间不会收到行。
  * 内层行数锁存实际 M 行（regFile.m）：边界 M 下 Executor 只 push 实际
  * M 行，若期望 mTile*totalN 行会永久等待挂死。
  *
  * 运行模式：启动后完全自主运行——逐 tile 块连续写回，块间不做任何
  * 上游握手，直到所有 tile 块搬完后发一拍 done 脉冲回 IDLE。
  *
  * 【外部写通道契约】写回拍须按地址顺序提交（半占用拍零填充高位与密集行步进的正确性依赖保序写）；外部存储侧若乱序受理写拍，行内/行间布局无法保证。
  *   ★(#166/#169-S2) 上述「半占用拍零填充高位」是【内部 wire dmaExtWrIF.req.payload.data】
  *     口径（下方 `(io.bufPop.payload >> (beatCnt * cfg.cDmaExtW.U))(cfg.cDmaExtW - 1, 0)`
  *     那一行）；【对外端口 io.dmaExtWrIF.req.payload.data】已在 addrShiftBytes 那一行按本拍
  *     地址的 byte lane 偏移【同源左移】就位，与 strb 行逐拍同源 ⟹ 对外【不再是低位口径】。
  *     下游若忽略 wstrb、按「行数据在总线字低位」整字落盘，将在 off≠0 的拍上错位（#168-S3）。
  *
  * 【P2 两段式分工（裁定 4/6）】精度转换与行寄存器归顶层 HiveOutCvt（握手模块），
  * 本模块只消费转换后密集行（bufPop，宽 totalN×32，按 outFmt 选段、高位已由 cvt 填零）
  * 并拆拍发出；格式族判据单源化：io.outFmt ← 顶层唯一源 cOutFmtEff（废除旧「整数强制透传 000」判据）。
  *
  * start 语义：io.start 在任意状态均可重新装载（复位计数/错误、重新锁存遍历参数），
  * 用于异常路径后的强制重启（同 RdOnly 说明）。
  * 安全性：脱离 sTRANSFER_M 时 bufPop.ready/writeData.valid 自然拉低，无协议违约。
  *
  * @param cfg      HiveCore 配置
  * @param bufWidth P2 签名保留参数（语义已死：bufPop 位宽固定 = totalN×32 密集行）
  * @param hasFp    P2 签名保留参数（语义已死：规格化硬件随 cvt 上移顶层）
  * @param hasMx    P2 签名保留参数（语义已死：量化支路随 cvt 上移顶层；
  *                 cScalePush 端口删除，scale 改由顶层 cvt.scaleOut → 深 2 cScaleFifo 提交）
  */
class HiveCoreDmaWrOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, hasFp: Boolean = false, hasMx: Boolean = false) extends Module {
  // 总线侧写回位宽：P0 契约直取 cfg.cDmaExtW（默认 256）。
  val extW = cfg.cDmaExtW
  // bufPop 侧位宽：消费顶层 cvt.out 转换后密集行（行粒度，全设计唯一行寄存器在 cvt 内，
  // 本模块行锁存仅作发拍暂存——拍拆分必须切自锁存值，防发拍期间上游前移错位）。
  val popW = cfg.totalN * 32
  // 拍计数器位宽：按编译期最大拍数上界 cBeatsPerRowMax = totalN*32/cDmaExtW
  private val beatCntW = math.max(1, log2Up(cfg.cBeatsPerRowMax))

  val io = IO(new Bundle {
    // 控制/状态（块协议：peekBlock/doneBlock 与 Executor 同步）
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val peekBlock  = Input(Bool())                   // 写出一个M维Block控制脉冲
    val doneBlock = Output(Bool())                   // 完成一个M维Block控制脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new HiveCoreDMAExtWriteOnlyIF(cfg, extW)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
    val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
    val bufPop = slave(Stream(UInt(popW.W)))         // 消费 cvt.out 转换后整行（行粒度，fire 即锁存）
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))  // P2 无本模块内消费者（保顶层接线稳定）
    val nCntOut = Output(UInt(cfg.mnkWidth.W))       // 回供当前 N tile 序号，顶层算 cvt.nEff
  })

  //
  

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE      = 0.U(3.W)
  val sFIRST_PEEK = 1.U(3.W)
  val sTRANSFER_M  = 2.U(3.W)   // pop buffer 驱动 writeData、推进地址  
  val sNEXT_N = 3.U(3.W)
  val sDONE      = 4.U(3.W)

  val state = RegInit(sIDLE)

  // Internal IF
  val dmaExtWrIF = Wire(new HiveCoreDMAExtWriteOnlyIF(cfg, extW))
  val dmaCurStrb = Wire(UInt((cfg.cDmaExtW / 8).W))
  val dmaMaxStrb = Wire(UInt((cfg.cDmaExtW / 8).W))
  val dma2Strb = Wire(UInt((cfg.cDmaExtW / 8 / 2).W))
  val dma4Strb = Wire(UInt((cfg.cDmaExtW / 8 / 4).W))
  dmaMaxStrb := ((1L << (cfg.cDmaExtW / 8)) - 1L).U
  dma2Strb := ((1L << (cfg.cDmaExtW / 8 / 2)) - 1L).U
  dma4Strb := ((1L << (cfg.cDmaExtW / 8 / 4)) - 1L).U
  io.dmaExtWrIF.req.valid := dmaExtWrIF.req.valid
  io.dmaExtWrIF.req.payload.addr := dmaExtWrIF.req.payload.addr
  dmaExtWrIF.req.ready := io.dmaExtWrIF.req.ready

  // 【#166 契约描述（订正原「DMA 数据需要移位，目前全部把数据放到低位」自陈缺陷注释）】
  // AXI byte lane 契约：wstrb 第 i 位选通 wdata[8i+7:8i]，故 data 与 strb 必须做【同源】
  //   移位——两者共用下面同一个 addrShiftBytes 表达式（= addr mod dmaBytes，即本拍数据在
  //   总线字内的起始 byte lane 偏移），data 侧再 ×8 由 byte 单位换成 bit 单位。
  // 旧代码只移 strb（下方 strb 行一直是正确的）、data 直连总线字低位，导致选通位与实际
  //   数据字节错位：strb 命中的 lane 上取到的是别的列的数据，未命中的 lane 上反而压着
  //   有效数据。已按 #165 裁定的方案 A 修复。
  // 位宽验算（勿删）：addrShiftBytes = addr(log2Up(dmaBytes)-1, 0) = addr(4,0)（dmaBytes=32）
  //   是【显式 5-bit 切片】，值域 0..dmaBytes-1 = 0..31 由【类型系统】保证，【无需取模】；
  //   `addrShiftBytes << 3` 是 UInt << Int 静态移位 → 8-bit（最大值 31×8=248）；
  //   `data << 8-bit UInt` 是动态移位 → Chisel 层结果展宽到 cDmaExtW + (2^8 - 1) = 511 bit，
  //   故【必须】显式 (cDmaExtW-1, 0) 截回总线位宽。
  //   ★实测取证（#169 重发射后 src/hive/verilog*/Hive_HiveCoreDmaWrOnly.sv:78-80）：
  //     firtool 【并未】对该移位做已知位收敛，网表中间量就是 `wire [510:0]`（511-bit），
  //     移位量以 `{503'h0, addr[4:0], 3'h0}` 的 511-bit 拼接形式给出。评审 build/168-S4
  //     预期的「≈287-bit = 256+31」【不成立】——那是「firtool 会利用低 3 位恒 0」的推测，
  //     实际未发生（本注释旧版也拄了该推测，已订正）。真正的位宽收益在【S1 前后对比】：
  //       · strb 中间 wire：改前 `wire [94:0]`（= 32 + 2^6-1，addrShiftBytes 为 6-bit）
  //                       → 改后 `wire [62:0]`（= 32 + 2^5-1，5-bit）——【网表实测】；
  //       · data 移位中间 wire：改前【无】（data 裸截取）→ 改后 511-bit；若走「只删
  //         取模不改切片」的陷阱路径则会到 256 + 2^9-1 = 【767-bit】（511 vs 767 才是 S1 收益）。
  //
  // 【#169-S1 改写留痕 —— 订正 #166 写入此处的错误论断（评审 build/168 W1/S1）】
  //   本行历史上曾写作：
  //       addr(log2Up(cfg.cDmaExtW/8), 0) % dmaBytes.U          // = addr(5,0) % 32
  //   该切片是【6-bit】= addr mod 64、值域【0..63】，其中【取模是承重的】：
  //   addr(5,0) % 32 == addr(4,0)，取模的唯一作用就是【丢掉 bit 5】。
  //   #166 的注释却把它写成「addr(5,0) 已覆盖全域（值域 0..31），% dmaBytes.U 为冗余但无害，
  //   保留以维持与 strb 侧逐字同源」——「值域 0..31」这个结论恰恰是取模造成的，却被反过来
  //   当成「取模冗余」的论据，自相矛盾（build/165:145-148、build/166:82-83 与 §7.2-3 同错，
  //   三处均已追加更正段；#167 独立复核未抓出）。
  //   ★若照那条注释【只删取模、不改切片】，addrShiftBytes ∈ [0,63]，则 off>=32 时：
  //       · strb：dmaCurStrb(32-bit) << off 被 32-bit sink 截 ⟹ strb = 0；
  //       · data：off*8 >= 256 ⟹ (cDmaExtW-1,0) 窗口内全 0；
  //     即 strb 与 data【双双归零】⟹ 外部内存一个字节都不写、且不报 err（静默全丢）。
  //   机器级证据：陈旧产物 src/hive/verilog_mx/Hive_HiveCoreDmaWrOnly.sv:82 的 firtool 输出
  //     是 `<< _dmaExtWrIF_req_payload_addr_T_1[4:0]`（而非 [5:0]）——综合器把 %32 收敛成
  //     「丢掉 bit 5」，这正是「取模在做实际工作」的证据（网表看不到 % 不代表源码可删）。
  //   现改写为显式 5-bit 切片，陷阱从根上消除：既不需要取模，也不再需要「勿删」警示。
  //   恒等性依据：require(isPow2(cDmaExtW)) 已由 HiveCoreConfig.scala:291 强制（#169 追加注释前
  //     该行在 :265，评审 build/168 引用的就是旧行号）⟹ dmaBytes
  //     恒为 2 的幂 ⟹ addr mod dmaBytes ≡ addr(log2Up(dmaBytes)-1, 0)，二者【逐值恒等】。
  //   下标安全性：totalN >= 4（arrayN>=4、clusterM>=1）且 require(totalN*8 <= cDmaExtW)
  //     ⟹ dmaBytes >= 4 ⟹ log2Up(dmaBytes)-1 >= 1；addrWidth 默认 32 >= log2Up(dmaBytes)，
  //     切片不越界（地址空间小于一个总线字的退化配置不在支持范围）。
  //   （build/165_wrdma_strb_wdata_analysis.md:328 的实施风险表原本已建议「用 .tail(1) 或
  //     明确 addr(4,0)」，#166 未采纳反而改写成「冗余」——本轮才落地该建议。）
  //
  // 【#169-W2 订正：(cDmaExtW-1,0) 截回所丢弃的高位，其可达性是【两条独立路径】】
  //   丢弃条件 = laneOffset + lanes > dmaBytes（lanes 由 cDmaDmaBeatTar 三档决定 ∈ {8,16,32}）。
  //   #166 的注释只写了 (b) 并断言「当前不可达」，漏了 (a)：
  //   (a) cAddr % dmaBytes ≠ 0 —— off = addr mod dmaBytes 直接来自【运行期寄存器 cAddr】，
  //       与 N/totalN/cFmt【无关】。fp32/int32 输出下 cWriteBytes = totalN*32/8 = 64 >
  //       dmaBytes = 32 ⟹ cBeatAddrStep = 32、2 拍/行、cDmaDmaBeatTar = 0 ⟹ lanes = 32
  //       （满档），此时【任何 off > 0】都触发截断：每拍静默丢 off 字节、STATUS.err 不置位
  //       （例：基址余 8 的 64 字节行会少写 16 字节，外部内存保留旧值）。
  //       ★本路径现由【errCode 10】显式报错（HiveCoreCalcConfig 的「C write-back address
  //         alignment guard」分支），不再静默；软件对齐契约 W0/W1/W2 见 HiveCoreCalcConfig
  //         的 C WRITE-BACK ALIGNMENT CONTRACT 段（#169-W2）。
  //   (b) N 非 totalN 整数倍 ⟹ rowOff 不是 cWriteBytes 整数倍，属既有「strb 档位不按 N 尾数
  //       细化」缺陷族，#166/#169 范围外，未在此处掩盖；仿真期由 TB 侧 STRB-OVERFLOW 判据
  //       （HiveSimCommon.auditCBeatLanes）可见，硬件侧【仍无 errCode】。
  // 时序：addrShiftBytes 是 req.payload.addr 的纯组合函数，本行是纯组合桶形移位
  //   （byte 粒度 5 级 mux2），无新增时序元件、无 latch；与 strb 行共用同一源表达式 ⟹ 天然同源。
  //
  // ==========================================================================
  // 【#169-W10 跨文件隐式契约（勿破）】本模块对同一条总线字有【两套互相独立】的
  //   地址→数据映射，二者必须保持【互补】，否则 data 与 strb 会一起对准错误的字节
  //   （「同源」这层不会破——两行共用下面同一个 addrShiftBytes；破的是它们共同对准的位置）：
  //   (1) 拍内分段，用【编译期常量】cDmaExtW：
  //         dmaExtWrIF.req.payload.data := (io.bufPop.payload >> (beatCnt * cfg.cDmaExtW.U))(...)
  //   (2) lane 偏移，用【运行期寄存器】：
  //         addr = curAddr + beatCnt * io.calcConfig.cBeatAddrStep
  //       （见下方 dmaExtWrIF.req.payload.addr 那一行），cBeatAddrStep 由
  //       HiveCoreCalcConfig.scala 的 cWriteBytes/cBeatAddrStep 派生段运行期算出。
  //   今天的互补性由该派生式保证：
  //     cWriteBytes >  cDmaBytes ⟹ cBeatAddrStep = cDmaBytes (= cDmaExtW/8) 且多拍；
  //     cWriteBytes <= cDmaBytes ⟹ cDmaBufBeatTar = 0 ⟹ beatCnt ≡ 0，(1) 恒为 0 偏移。
  //   ★二者【无 require / 断言绑定】。改 cBeatAddrStep 公式（含「双行合拍」一类提案）
  //     必须同步复核 (1) 与 (2)。反向交叉引用见 HiveCoreCalcConfig 的
  //     「#169 W10 REVERSE CROSS-REFERENCE」段。TB 侧若要机器可检，须用【纯张量语义】
  //     不变式（同行相邻两拍 addr 差 = cDmaExtW/8），不得从同一条派生链取值，否则共模失效。
  // ==========================================================================
  val dmaBytes = cfg.cDmaExtW / 8
  // #169-S1：显式切片（值域 0..dmaBytes-1 由类型系统保证；详见上方改写留痕）
  val addrShiftBytes = dmaExtWrIF.req.payload.addr(log2Up(dmaBytes) - 1, 0)
  io.dmaExtWrIF.req.payload.data := (dmaExtWrIF.req.payload.data << (addrShiftBytes << 3))(cfg.cDmaExtW - 1, 0)
  when(io.calcConfig.cDmaDmaBeatTar === 0.U) { //full
    dmaCurStrb := dmaMaxStrb
  }.elsewhen(io.calcConfig.cDmaDmaBeatTar === 1.U) { // 1/2
    dmaCurStrb := dma2Strb
  }.otherwise { // 1/4
    dmaCurStrb := dma4Strb
  }
  io.dmaExtWrIF.req.payload.strb := dmaCurStrb << (addrShiftBytes)

  //Unused tie
  dmaExtWrIF.rsp.valid := false.B
  dmaExtWrIF.rsp.ready := false.B
  dmaExtWrIF.rsp.payload.err := false.B  
  dmaExtWrIF.req.payload.strb := 0.U

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写列地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val lineCounter = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val errReg = RegInit(false.B)
  // credit 记账口径 = 「拍数」（修复留痕）：窄总线下每行只 pop 一次但拆多拍写出，
  // 最大在途拍数 = cBufferDepth × 每行拍数；旧口径 log2Up(cBufferDepth+1)（行数）
  // 在多拍下发时可回绕，致 sDONE 误判 ostCredit===0 提前放行，故按拍数口径拓宽。
  // 【W-Paul 修复】+2 行余量：拍推进、行推进与外部 rsp 延迟可产生瞬态峰值，
  // 用 (cBufferDepth+2)*cBeatsPerRowMax 吸收。
  val ostCredit = RegInit(0.U(log2Up((cfg.cBufferDepth + 2) * cfg.cBeatsPerRowMax + 1).W))

  // ==========================================================================
  // 【#136-KS6 死寄存器清理（#125 已执行删除）】
  // 本段原标题为「发拍行锁存（bufPop fire 拍锁存 cvt.out 整行；发拍段必须切自锁存值——
  // 若切自组合直通，发送拍期间上游已前移到下一行，总线数据会整行错位）」，所描述的机制
  // 【已不在代码中】。原登记的两个死寄存器已由 #125 删除（不可达性论证：全文件均无任何
  // 读取点，删除前后 elaboration 产物逐字一致）：
  //   · lineData  = Reg(UInt(popW.W))  —— 曾【从未被赋值、从未被读取】，纯死寄存器，已删；
  //   · lineValid = RegInit(false.B)   —— 曾仅在 startReload 内被写 false.B、【从未被读取】，
  //                                       纯死寄存器，已删（连同 startReload 内那次死写入）；
  //   · beatCnt   = RegInit(0.U(...))  —— 【活，保留】：参与 lastBeat 判据与拍内地址/数据偏移。
  // 现行实际机制是【组合直通 + 末拍弹出】：发拍段直接切自 io.bufPop.payload（上游 =
  // 顶层 cvt 行寄存 → C FIFO）；因为 bufPop.ready 只在 lastBeat 那一拍拉高，一行在其余
  // beatsPerRowEff-1 拍期间由上游 FIFO 保持稳定不前移 —— 原标题担心的「整行错位」是由
  // 【上游持稳】避免的，不是由本模块锁存避免的。
  // 佐证：HiveCore.scala 的 #122「禁动区声明」明确写「不恢复 lineData/lineValid
  // （行寄存职责已归 cvt，全设计唯一一份）」。
  // ★ 请勿把 lineData/lineValid 接回数据路：那会造出第二份行锁存，重新引入
  //   #122 主修复所消除的同拍竞态（cvt.in.ready 组合直通 ∧ cStoreGate 寄存 ∧ FIFO 未空）。
  // ==========================================================================
  val beatCnt   = RegInit(0.U(beatCntW.W))

  // ==========================================================================
  // 运行期拍数解码（裁定 6；唯一判据源 io.outFmt ← 顶层 cOutFmtEff）：
  // 元素位宽档 = fp32/int32/reserved 回落→32、fp16/bf16/int16→16、
  // mxfp8/int8→8、mxfp4→4（半字节）；beatsPerRowEff = ceil(totalN×elemBits/cDmaExtW)，
  // 256 默认下 ∈ {1,2}（mxfp4 行 64bit → 1 拍；禁止双行合拍，裁定 9）。
  // reserved 编码（3/7/1011..1111）回落 32 档，与 cvt 选段/顶层布局同源。
  // ==========================================================================
  val lastBeat: Bool = beatCnt === io.calcConfig.cDmaBufBeatTar 

  io.nCntOut := nCnt  // 回供顶层算 cvt.nEff（原 WrOnly 内 nEff 组合逻辑已迁出）

  // P2：内嵌 HiveOutCvt 例化已删除（裁定 4）——cvt 上移 HiveCore 顶层（握手模块：
  // in ← cPop mux、out → 本模块 bufPop、scaleOut → 顶层深 2 cScaleFifo），
  // 原 TODO(P2) 临时接法（8+3 处）随本重构全部清除。

  // P2：ActiveRowSplit 已删除（裁定 4/6）——行寄存器职责归顶层 cvt（全设计唯一一份），
  // 【#136-KS6 订正 / #125】本模块【不再保留任何行锁存】：原有 lineData/lineValid 两个死
  // 寄存器已由 #125 删除（见其声明处的 #136-KS6 清理段），此处只保留活的 beatCnt；拍段切分
  // 是【组合直通】自 io.bufPop.payload 按总线位宽右移取段，行数据由上游 FIFO 在该行全部拍期间持稳。
  // P2：popDataFpNorm（40→32 延迟规格化）已删除——规格化下沉到 cvt 数值核（fpNormalize40to32），
  // 本模块收到的已是转换后密集行。

  // ==========================================================================
  // 默认输出驱动（P2：isFloat/normSel/outFmtEff 判据族已随两段式重构删除，
  // 格式判据唯一源 = io.outFmt ← 顶层 cOutFmtEff，见上方拍数解码块）
  // ==========================================================================
  io.dmaExtWrIF.rsp.ready        := true.B


  io.bufPop.ready := (state === sTRANSFER_M) & io.dmaExtWrIF.req.ready & lastBeat
  dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
  dmaExtWrIF.req.payload.addr := curAddr + beatCnt * io.calcConfig.cBeatAddrStep
  dmaExtWrIF.req.payload.data := (io.bufPop.payload >> (beatCnt * cfg.cDmaExtW.U))(cfg.cDmaExtW - 1, 0)

  // 拍推进：末拍回零拍计数（行推进动作在下方 switch sTRANSFER_M 分支；本模块无行锁存可清）
  when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire) {
    when(lastBeat) {
      beatCnt   := 0.U
    }.otherwise {
      beatCnt := beatCnt + 1.U
    }
  }

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg
  io.doneBlock := false.B

  when(state === sIDLE){
    ostCredit := 0.U
  }.otherwise{
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.dmaExtWrIF.req.fire){
      ostCredit := ostCredit + 1.U
    }
  }

  when(io.dmaExtWrIF.rsp.fire){
    when(io.dmaExtWrIF.rsp.payload.err){
      errReg := true.B
    }
  }
  

  // ==========================================================================
  // start 重载公共动作（任意状态生效，引用同文件 RdOnly 的 sTRANSFER/sDONE
  // 重载同构实现）：回 sFIRST_PEEK、清 errReg/lineCounter/nCnt、重载
  // curAddr/rowStep/colAddr、清 beatCnt。
  // 【#136-KS6 订正 / #125】原文「active 另清 lineValid/beatCnt（防异常路径残留半行状态）」
  //   两处不准：
  //   (1)「active」是已删除的编译期双分支（legacy/active）术语；P2 两段式重构后
  //       startReload 是【唯一】重载路径，不存在「active 另清」这一分支之别。
  //   (2) 原文所指 lineValid 曾是【死寄存器】（全文件从未被读取），其在本函数内的
  //       `:= false.B` 死写入无任何可观测效果，已由 #125 连同声明一并删除；本函数真正
  //       有防护意义的清零只有 beatCnt := 0.U —— 它参与 lastBeat 判据与拍内地址/数据偏移，
  //       残留会让重启后首行拍序错位（进而使收行时刻错拍）。
  // 正常流程 start 仅出现在 sIDLE，其余状态的重载分支为死路、
  // 行为不变；异常路径（Executor 回 idle 后下次 EXECUTE）可强制重启卡死状态。
  // ==========================================================================
  private def startReload(): Unit = {
    state       := sFIRST_PEEK
    errReg      := false.B
    lineCounter := 0.U
    // C 矩阵：固定「内 M 外 N」遍历（C buffer 为 FIFO，写回顺序 =
    // Executor push 顺序，与 loopMode 无关）
    curAddr := io.regFile.cAddr
    // 内层沿实际 M 行（regFile.m，边界感知不含 padding），外层扫 nTile 个 N tile
    rowStep := io.calcConfig.cRowAddressOffset
    colAddr := io.calcConfig.cColTileAddressOffset
    nCnt    := 0.U
    // 强制重启时清拍计数（防异常路径残留半行状态；正常流程回 sIDLE 前 beatCnt 已自然归零）。
    // 【#136-KS6 / #125】此处原有 `lineValid := false.B` 一行【死寄存器的死写入】（全文件
    //   从未被读取），仅为与 672fb37 重构前的语句保持一一对应而留存，已由 #125 连同
    //   lineValid 声明一并删除；勿据原注释「清行锁存」字样推断本模块仍有行锁存 ——
    //   现行机制是【组合直通 + 末拍弹出】，行数据由上游（顶层 cvt 行寄存 → C FIFO）持稳，
    //   见文件头「反压策略」段与 beatCnt 声明处的死寄存器清理段。
    beatCnt   := 0.U
  }

  switch(state) {

    // --- sIDLE: 等待 start ---
    is(sIDLE) { 
      // ==========================================================================
      // start 重新装载（任意状态生效，语义与安全性说明见 RdOnly 同构实现）：
      // Executor 异常回 idle 后下次 EXECUTE 必须能强制重启卡死的本 DMA
      // ==========================================================================
      when(io.start) {
        startReload()
      }


    }
    
    is(sFIRST_PEEK) {
      // start 重载（契约对齐，同 RdOnly）：等 peek 拍收到 start → 重载回起点；
      // 正常流程 start 不出现在本状态，分支死路、行为不变
      when(io.peekBlock) {
        state := sTRANSFER_M
      }
    }

    is(sTRANSFER_M){
        // 末拍（行内最后一拍）才做行推进（末拍推进单一判据，裁定 6；
        // 取代原按格式的 legacy/active/quant 多分支）；
        // 拍计数/行锁存清除在上方拍推进 when 块处理。
        when(dmaExtWrIF.req.fire && lastBeat) {
          when(lineCounter === io.regFile.m - 1.U) {
            lineCounter := 0.U
            state := sNEXT_N
            io.doneBlock := true.B
          }.otherwise {
            lineCounter := lineCounter + 1.U
            curAddr := curAddr + rowStep
          }
        }
    }


    is(sNEXT_N) {
      when(nCnt === io.calcConfig.nTile - 1.U){
        state := sDONE
      }.otherwise{
        when(io.peekBlock){
          nCnt := nCnt + 1.U
          curAddr := io.regFile.cAddr + colAddr
          colAddr := colAddr + io.calcConfig.cColTileAddressOffset
          state := sTRANSFER_M
        }
      }
      
    }

 
    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      // start 重载（契约对齐，同 RdOnly sDONE）：绕过 sIDLE 时 credit 归零
      // 不执行，故重载另需 ostCredit := 0.U（last-connect 覆盖上方
      // state=/=sIDLE 分支的同拍增减）；重启后以新写回拍重新累计在途。
      when(io.start) {
        startReload()
        ostCredit := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }
}



class HiveCoreDmaScaleWr(cfg: HiveCoreConfig) extends Module {
  private val rowW = cfg.cScaleRowW
  // P2（裁定 5）：唯一 scale 缓冲 = 顶层深 2 cScaleFifo（原 Scratchpad 内深度
  // scaleBufferDepth 已删）；在途上限/credit 位宽随之收窄（log2Up(2+1)=2 bit）。
  private val depth = 2

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
  val ostCredit = RegInit(0.U(log2Up(depth + 1).W))  // 在途写拍数（上限 = 顶层 FIFO 深度 2）
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
  // ==========================================================================
  // 【#169-W3 登记 → 已修复（本次，补括号）：下一行 strb 常量曾是【Scala 运算符优先级缺陷】
  //   原状：`(1 << (rowW / 8) - 1).U`。Scala 中运算符优先级由【首字符】决定，`+ -`
  //   的优先级【高于】`< >`，故它解析为 `1 << ((rowW / 8) - 1)`，【不是】
  //   `(1 << (rowW / 8)) - 1`。配合 rowW = cfg.cScaleRowW = (totalN/minBlockSize)*8
  //   = totalN/2 **bit**：
  //     | totalN | rowW | rowW/8 (字节) | 应为满档 strb | 实得 1<<(rowW/8-1) | 结论 |
  //     |  16（当前全部配置） |  8 | 1 | 0b1 = 1 | 1<<0 = 1 | 巧合正确 |
  //     |  32                | 16 | 2 | 0b11 = 3 | 1<<1 = 2 = 0b10 | 【错】lane0 不写 |
  //     |  64                | 32 | 4 | 0b1111 = 15 | 1<<3 = 8 = 0b00001000 | 【错】只写 lane3 |
  //   ★#169 当时只登记不修（修它属功能变更，需单独裁定）；用户已裁定，本次已修复：
  //     下一行现为 `((1 << (rowW / 8)) - 1).U`。当前配置 totalN=16 ⟹ rowW/8=1，
  //     新旧公式都算 1 ⟹ 纯编译期字面量求值、网表级 no-op ⟹ 重发射三套 verilog
  //     产物逐字节零 diff（取证见 build/w3_strb_precedence_fix.md）。
  //     交叉引用见 HiveCoreConfig.scala 的 minBlockSize/cScaleRowW 解耦清单
  //     （require(minBlockSize == ...) 上方那段）。
  //   ★同时订正【scaleC 豁免于 #166 lane 契约的理由】：依据是【端口宽度 = 1 字节
  //     （rowW = 8 bit @ totalN=16）⟹ lane 窗口退化为单 lane、移位恒为 0】，
  //     【不是】「其 strb 为常量满档」——后者仅 rowW=8 时巧合成立（见上表）。
  //     旧理由已扩散到 HiveSimCommon.scala:604、HiveCoreSimCaseIntSpec.scala:134 与
  //     build/166 §7.2-6（test 侧两处由 #170 订正，build/166 已追加更正段）。
  //     ⟹ 本行括号已修；totalN 提到 32 时剩余待办 = 把 scaleC 纳入 lane 审计
  //     + Config 侧 cScaleRowW 解耦。
  // ==========================================================================
  io.dmaExtWrIF.req.payload.strb  := ((1 << (rowW / 8)) - 1).U
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
        colAddr := colAddr + io.calcConfig.scaleCColTileAddressOffset
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
    colAddr    := io.calcConfig.scaleCColTileAddressOffset
    rowStep    := io.calcConfig.scaleCRowAddressOffset
    lineTarget := io.regFile.m
    nCnt       := 0.U
  }

}
