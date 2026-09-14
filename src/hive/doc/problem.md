1. 在16*16 fp16算力矩阵下，当前运行a/b的blocksize如果是16是不对的，因为一行理论是两个scale系数，但是当前仍然是只读一个，TB也认为是对的需要修改。 （暂定不支持，后续修改）
2. 目前仅在cdma的写侧加入的strb以及数据shift，后续需要继续修改adma/bdma，同时需要拓宽scale dma读写的位宽以支持一次读多个scale
3. fp8乘法优化，当前fp16乘法和两个fp8乘法全是独立的3个乘法器。 工作1: 其中一个fp8复用fp16乘法。 工作2: 另一个fp8乘法器不需要更高精度乘法。 之后支持fp4的话继续融合

---

## MXFP4/E2M1 打通（Phase 0-4 + 5a）交付说明（收尾修复 #50 追加）

### 【Fix-14】cfgMxFp4 的 fmax / 面积预算与 verilog_mx 不可比（时序/面积口径留痕）
- cfgMxFp4(hasMx4=true) 每 PE 的组合深度 = **4 级 HiveMacFpMx MAC 链**（fp4 quad-lane），
  而 fp8 dual-lane 仅 **2 级**。原因：`sumMx = Mux(isMx4(aFmt), quadResult, dualLaneMx())`
  的**双臂均 elaborate**（Mux 两臂都是实硬件），故即便在 cfgMxFp4 上**跑 fp8**，关键路径
  同样被 4 级 quad-lane 链钳住 → **fmax 被 4 级链决定，与 verilog_mx（2 级）不可比**。
- `HiveMacLazyDownstream` 是 **Scala object 内联（非 Module）** → firtool **不跨实例去重** →
  Phase 5a 对它的收敛属**纯源码 DRY，非面积优化**：每 PE 仍展开 **5 份 lazy-40 下游副本**。
- 结论：verilog_mxfp4 的 fmax/面积数据不得与 verilog_mx 直接对比；两者组合深度与副本数不同源。

### 【Fix-16】Phase 2 实现偏离登记（已裁定项，防后续审计误判为损失）
- 计划原定：参数化 `mxLanes` 派生单一 lane 循环，fp8/fp4 共用一份代码。
- 实际实现：**物理隔离双分支**——`dualLaneMx()` 抽为 private def（def 体逐字节保留原 fp8
  dual-lane 代码、全部 FIRRTL 信号名不变）+ `if(hasMx4){ quad-lane 独立分支 }`。
- 裁定理由（**已裁定，非损失**）：物理隔离是守住 normalized-diff=0 硬门槛的**必要手段**——
  hasMx4=false 时 quad-lane 分支根本不 elaborate，fp8 双 lane 硬件与命名前缀逐字节不变；
  参数化循环会改写 fp8 路径的信号名/结构 → 破坏基线。功能覆盖**等价且更优**（fp4 走独立
  4 级链、fp8 走原 2 级链，互不干扰）。

### 【Fix-13】已知交付缺口：fp4 波形未落地（根因 + 处置）
- 现象：`sim/mxfp4/` 目录已建但**零文件**，HiveCoreSimCaseMxFp4Spec 的 4（现 5，含 Fix-10
  errCode7 负向）用例均**无 trace.fst**；对照 HiveCoreSimCaseMxSpec 同 `HiveFstSim` trait 却
  在 `sim/mx/` 产出了 trace.fst。
- **根因**：`HiveFstSim` 的波形开启受 `-DemitFst=1` 门控（`enableWavesAtTimeZero`）。未带该
  flag 运行时 verilator 不 dump，`workdir-verilator/trace.fst` 不存在；而 `HiveSimCommon.copyTrace`
  对源文件缺失**只打印 NOTE、不 fail**（`HiveSimCommon.scala` copyTrace 的 else 分支），故缺口
  **静默**通过、从未暴露。`sim/mx/` 有波形说明 MxSpec 曾在带 `-DemitFst=1` 下跑过，MxFp4Spec 未。
- **处置**：本任务（#50）铁律禁止跑 `sbt test`/仿真（属 #54 验证代理职责），故**无法在此补跑**
  产出波形。**明确登记为已知交付缺口**，交 #54 用带 `-DemitFst=1` 的
  `testOnly HiveCoreSimCaseMxFp4Spec` 补跑一次，令 5 个 .fst 落入 `sim/mxfp4/`（.fst 已 gitignore，
  属可再生产物、不入库）。copyTrace 的「静默 NOTE」是流程性弱点，非本任务逻辑缺陷。

### 【最终决策 · 用户 2026-09-12 裁定】MX4×MX8 混合(W4A8)：v1 保留 errCode 运行期可诊断守护，真支持列 v2

**① 混合 MX4×MX8 数学与规范均合法。** W4A8（MX4 权重 × MX8 激活）是 MX 的**主流合法用例**；
OCP MX 规范允许 A/B 采用**不同 MX 元素类型**，且共享同一 E8M0 block scale 语义。故**不**将其
视为"永久非法"，而是 v1 数据通路尚未实现、v2 立项补齐的**待支持合法组合**。

**② 当前 v1 会静默错算的四处系统性根因**（均因几何/选择只按 aFmt 单点决策，未考虑 A/B 异族）：
- **MAC lane 选择子只看 aFmt**：`HiveWorkUnit` 的 `Mux(isMx4(io.aFmt), quadResult, dualLaneMx())`
  按 aFmt 选 4-lane/2-lane，而被选支路内 b 侧解码器接 `io.bFmt` → lane 粒度与 b 解码格式矛盾。
- **K-per-pass / kTile 只按 aFmt**：`getKLoadNum` / Executor kTiles 以 aFmt 的 elemsPerLane 定
  一个 kTile 的 K 元素数；A/B 异族时两侧每拍元素数不同（fp4=4、fp8=2），单一 kTile 几何不成立。
- **DMA mxRunDma / mxRunB 只看 aFmt**：`HiveCoreDmaRdOnly` 与 Executor 的 MX 运行期门控
  （`mxRunB = isMx(aFmt)` 等）以 aFmt 代表整拍，b 侧 scale/数据供给未按 bFmt 独立成形。
- **scale 单广播曾是 quad-lane 专属假设**：fp4 v1 曾强制 aMxBlockSize=64（=1 kTile=1 pass=1
  scale 块，保 Executor scaleA 单广播不变式）；混配下 A/B block 语义不同，单广播假设被打破。
  **【#146 更新：该强制已解除】** `MXBlockSize` 已收窄为 2 级枚举 `{sel=0→16, sel=1→32}`，硬件
  errCode 7 的 `getBlockSize() =/= 64.U` 因此退化为**恒真式** —— fp4 无论 blk 取何值都被运行期
  **无条件静默拒绝**，而金锚 `hwErrCode`/`validate` 镜像了同一恒真式 ⟹ RTL 与金锚互相印证、
  测试永不红，fp4 呈"已覆盖"假象却完全不可用。现 errCode 7 整块删除，fp4 与 fp8 一样支持
  blk∈{16,32}；Executor 的逐 lane 字节选择 `idx=(lane*step)/blk` 已取代单广播假设（#145）。

**③ v1 处置：保留 errCode 运行期可诊断守护（非静默错算、非永久拒绝）。**
- 硬件：`HiveCoreCalcConfig.scala` errComb 裸块内 `if(cfg.hasMx4){ when(isMx4(aFmt)^isMx4(bFmt))
  errCodeComb:=3.U }`（Fix-4，暂复用 code 3="v1 不支持的格式组合"）。**【#146 真值序更新】**
  该 when 原置于 errCode7 之前，现 errCode7 分支已整块删除 ⟹ 它成为 when 链的**最后一个分支**，
  即 last-connect 的**最高优先级**。`hasMx4=true` 的完整真值序为：
  `3(MX4×MX8 混配) > 8(fp4 K 非 kPerBeat 整除) > 6 > 5 > 4 > 3(isAMx^isBMx) > 0`
  （原表述的最前项 `7(fp4 blk≠64)` 已不存在；码位 7 现为**空闲**）。
- 金锚：`HiveDebugModel.validate` 加 hasMx4 门控同族 require（Fix-8）+ `precheckConfigErr` 混配
  `Some(3)` + `HiveCoreQuickSpec.hwErrCode`/`errExplain` 镜像（Fix-7）。测试：`HiveFp4GeometrySpec`
  case5 MXE2M1×MXE4M3 → errCode3（Fix-11）。**【#146 留痕】** `HiveFp4GeometrySpec` 与
  `HiveCoreSimCaseMxFp4Spec` 两个套件文件已不在仓库（当前 hive 测试目录仅 4 个 suite：
  `HiveCoreQuickSpec` / `HiveCoreSimCase` / `HiveCoreSimCaseLoadWSpec` / `HiveCoreSimCaseMxSpec`），
  故上述 fp4 负向用例目前**无执行者**；混配 code3 与 code8 两支仅靠金锚镜像保持同步。
- 价值：软件读到明确错误码而非静默错算；hasMx4=false 基线（verilog/verilog_mx）零变化（全部门控）。

**④ 真支持列 v2，与 per-half-pass/block=32 同源（Phase3+5 级重构）。候选方案：**
- **方案 B（完整双向混合几何）**：A/B 各自独立 kTile 几何 + 双向 lane 选择 + scale 供给解耦。
  代价：**触碰 scaleA 单广播不变式**（需升级为 per-half-pass 双广播），改动面最大、最通用。
- **方案 D（最小子集，推荐先行）**：仅支持**单向 A=MX8 × B=MX4**、block 同为 64、K%64==0，
  B 侧加 **2 拍打包 shim**（把 fp4 的 4-elem/lane 折算成与 fp8 对齐的供给节奏）。改动面最小、
  覆盖 W4A8 主场景，不动 scaleA 单广播。
- **方案 A（byte-slot 密度牺牲）已否决**：为兼容异族而降低 lane 打包密度，牺牲吞吐，不划算。
- **方案 C（都塞 quad-lane）不可行**：把 fp8 也走 4-lane 会破坏 fp8 dual-lane 基线 RTL（违 normalized-diff）。

**⑤ 将来解除路径（无需接口重构）：** 只需**删守护 when（Fix-4）+ 金锚 require/Some(3)（Fix-7/8）
+ 补 W4A8 数据通路（方案 B 或 D）**。`aFmt`/`bFmt` 已是**独立 4-bit 字段**，寄存器映射与软件
编程接口**无需重构**，**无需引入历史 mixFmtEn 之类的使能位**。

**⑥ v2 可选增强登记（Nina 建议，本次不实现）：** 为 MX4×MX8 混配分配**独立 errCode**（而非
复用 code 3），提升可诊断性——软件可区分"A=MX×B=非MX 的非法混配(3)"与"MX4×MX8 的 v2 待支持
混配"。**【占用变更 · P0-1】** 原此处预留的 **errCode 8 现已被 P0-1「fp4 K 非 kPerBeat 整除」守护
占用**（见下节），故 W4A8 混配的独立诊断码**顺延至 errCode 9**（仍未实现）。errCode 字段为
STATUS(0x08) bits[6:3]（4-bit，取值 0..15；**【#146】当前占用 0/3/4/5/6/8，码位 7 已空闲**），
**7 与 9 均可安全新分配**。既有分配裁定继续有效：**新校验从 7 起分配、绝不复用 1/2** —— errCode 1/2
因 a/b/cMxBlockSize 三违约被 `errMXLargerThanKLoadNum` 合并为单一 code 0 而**永不可达**，不要去恢复。
本次按用户裁定 MX4×MX8 混配**仍暂复用 code 3**，独立码 9 列为 v2 可选增强。

---

### 【P0-1 · errCode 8】fp4(E2M1) K 非 kPerBeat 整除的运行期守护（真 bug 修复 + 真值序更新）

**① 真 bug（此前静默错算，测试假绿）：** Executor 的 `kTiles = K / (totalN×elemsPerLane)` 是**地板除**。
fp4 的 `kPerBeat = totalN×4 = 64`：
- `K=96`（非 64 整除）→ `kTiles = floor(96/64) = 1` → 只累加 64 个 K、**尾部 32 个 K 静默丢弃**、结果错
  却**无任何 errCode**（旧测试全绿假象）。
- `K<64`（如 K=32）→ `newKTiles = 0` → `kTiles := newKTiles - 1` **16-bit 回绕为 65535** → 65536 轮 pass
  跑飞（DMA 越界读）。
- 此前唯一守护只在**测试侧金锚** `HiveDebugModel.validate` 的 `require(k % kPerBeat == 0)`；**硬件侧零守护**
  （Executor 旧注释"K%32 已在 sIDLE 校验"**不成立**，本次已订正为"整除契约由 Executor 之外保证"）。

**② 修复：** `HiveCoreCalcConfig.scala` errComb 裸块内、`if(cfg.hasMx4)` 门控追加
`when(isMx4(aFmt) && (k(15,0) % (totalN×4) != 0)) { errComb:=true; errCodeComb:=8 }`。
分配**独立诊断码 8**，令软件精确定位 fp4 的"K 非整除"几何违约。**【#146 更新】** 原句"（而非复用
code7），令软件区分 blk≠64(7) 与 K 非整除(8) 两类 fp4 几何违约"已失效：errCode 7 分支已整块删除，
code 8 现为**唯一**的 fp4 几何守护，码位 7 空闲。
hasMx4=false 基线不 elaborate → verilog/verilog_mx normalized-diff=0。顶层负向用例原见
`HiveCoreSimCaseMxFp4Spec`（fp4 K=96 → STATUS.err=1/errCode=8/done=false/dmaC 零写回）。
**【#146 留痕】** 该套件文件已不在仓库，code8 目前**无执行者**；且原文的 `blk=64` 已不可编码
（`MXBlockSize` 仅 `{sel=0→16, sel=1→32}`），重建该用例时 blk 只能取 16 或 32。

**③ 放置与真值序（last-connect 后写覆盖前写，源码越靠后优先级越高）：** code8 的 when 置于
errCode6 之后、Fix-4 混配 code3 之前 ⟹ 混配 code3 覆盖 code8。
**【#146 最终真值序】** errCode7 分支已整块删除，混配 code3 成为 when 链尾（= 最高优先级）：
`3(MX4×MX8 混配) > 8(fp4 K 非整除) > 6 > 5 > 4 > 3(isAMx^isBMx) > 0`。
**无遮蔽论证**（取代原"既有 HiveFp4GeometrySpec case5/case2·6 用例 K 均为 64"的实证口径 —— 该套件
已不在仓库）：code8 要求 `isMx4(aFmt)`，混配 code3 要求 `isMx4(aFmt) ^ isMx4(bFmt)`；两者同时成立
⟺ `aFmt=fp4 且 bFmt=fp8`，而这一组合本就被混配分支以 code 3 拒绝（v1 不支持 W4A8）⟹ 被 code3
覆盖不会遮蔽任何**活配置**。`aFmt=bFmt=fp4` 时混配分支不成立、code8 独立生效。

**④ 四处镜像已同步：** 硬件 `HiveCoreCalcConfig.scala`；金锚 `HiveDebugModel.hwErrCode`（新单一真源）
+ `precheckConfigErr`（委托）+ `validate`（原 require(k%kPerBeat==0) 保留）；`HiveCoreQuickSpec.hwErrCode`
（委托 HiveDebugModel）+ `errExplain` case8；本 problem.md 真值序。

**【P0-2 去双源】** `HiveCoreQuickSpec.hwErrCode` 已提升为 `HiveDebugModel.hwErrCode` 公共 def，
`precheckConfigErr` 与 QuickSpec 体检均委托它 → 消灭"两份互不一致的 errCode 软件镜像"；同时把金锚
`Some(0)/Some(1)/Some(2)` 三分**合并为单一 Some(0)**（与 RTL 一致，errCode 1/2 永不可达），
QuantSpec「blk32 over totalN」期望由 2 改为 0（红1 转绿）。

**⑤【#146 · errCode 7 删除后的同步清单（共 6 处）】**
1. **硬件** `HiveCoreCalcConfig.scala`：删
   `if(cfg.hasMx4){ when((isMx4(aFmt) && aMxBlockSize =/= 64.U) || (isMx4(bFmt) && bMxBlockSize =/= 64.U)){ errComb:=true.B; errCodeComb:=7.U } }`
   整块，原位留 ASCII 根因说明；**when 链其余分支的相对顺序一字未扰动**。同步订正 code8 块与混配
   code3 块的两处优先级注释（原均以"7 >"为序首）。
2. **金锚** `HiveDebugModel.hwErrCode`：删
   `if (hasMx4 && ((aFmt==MXE2M1 && aMxBlk != 64) || (bFmt==MXE2M1 && bMxBlk != 64))) e = Some(7)`
   （原写在最后 = last-connect 最高优先级），doc 的真值序与 hasMx4 门控说明同步订正。
3. **金锚** `HiveDebugModel.validate`：`require(p.aMxBlk == 64)` / `require(p.bMxBlk == 64)` →
   `require(== 16 || == 32)`（消息 ASCII）。原式在 2 级枚举下**不可满足**，会在仿真前抛掉全部 fp4
   用例；新式镜像"寄存器字段实际可编码的取值集合"，由枚举语义推导、**非拟合 RTL**。
4. **`HiveCoreQuickSpec`**：删 `errExplain` 的 `case 7`（code7 现落入 `case _` "未知 errCode"臂，
   语义正确）；订正文件头 errCode 段与 `hwErrCode` doc 的真值序。
5. **`HiveCoreElaborate.scala`** cfgMxFp4 注释：删 errCode7 表述、订正 `mxBlockSize "=M64=3"`
   （该枚举值已不存在，现为 `{sel=0→16, sel=1→32}`）。
6. **本 problem.md**：【最终决策】②③⑥ + 【P0-1】②③ + 本清单。
> 影响面：`HiveCoreSimCase` / `HiveCoreSimCaseLoadWSpec` / `HiveCoreSimCaseMxSpec` 三个 suite 均不含
> fp4 用例、也不引用 errCode 7 / `hwErrCode` / `errExplain`，故本次删除对它们**零影响**；
> `hasMx4=false` 的两套发射基线（verilog / verilog_mx）本来就不 elaborate 该分支 ⟹ 结构逐位不变。

---

### 【回归口径留痕 · #54 收尾】Hive 门槛须用 `testOnly <14 hive suite>`，勿用 `sbt test`
- **正确口径**：Hive 回归门槛应跑
  `sbt -J-Xmx6G "testOnly <14 个 hive suite>"`（Hive 专属 14 个 suite 的显式白名单）。
- **`sbt test` 不可用于 Hive 门槛判定**：`sbt test` 会连带跑 ForagerBee 等**共 23 个 suite**，
  多 suite **并发** Verilator 编译/仿真时对共享 `build/chiselsim` workspace 产生**资源争用**，
  会把 `HiveCoreQuickSpec` 打成**假红**（典型症状：`FileNotFoundException: …compilation-log.txt`
  ——并发下 svsim workspace 的编译日志被另一进程清理/尚未落盘即被读取）。
- **判别**：该假红在 **Hive-only 隔离跑**（`testOnly` 白名单、无跨族并发）中**消失**，属环境级
  并发伪失败、非 Hive RTL/金锚逻辑缺陷。**门槛判定一律以 Hive-only 隔离跑为准**，`sbt test`
  的 QuickSpec 红不得计入 Hive 硬门槛。
- **【#146 留痕】"14 个 hive suite" 已过期**：当前 `src/hive/scala/test/` 只有 **4 个 suite**
  （`HiveCoreQuickSpec` / `HiveCoreSimCase` / `HiveCoreSimCaseLoadWSpec` / `HiveCoreSimCaseMxSpec`）
  加 2 个非 suite 支撑文件（`HiveDebugModel` / `HiveSimCommon`）。结论不变：门槛判定仍以
  `testOnly <白名单>` 的 Hive-only 隔离跑为准。

---

### 【#146 · 裁定 4】MX scale 32-bit 读的对齐契约（完整推导 + 判据 + 待裁项）

**① 背景（用户裁定原文）：** 「TB 假存储天然支持，改动零。但 SoC 集成侧必须确认 AXI/存储能做
非对齐 32-bit 读，否则硬件上板会出错。**要求软件配置一定对齐**。」本轮据此**只推导并写下契约**，
**不改** scale 张量布局、**不加**拼包状态机、**不改** scale DMA 遍历。

**② 地址生成的单一真源：** `HiveCoreCalcConfig` 的两个 scale 偏移（scaleA/scaleB 同式）
```
rowStride   = scaleXRowAddressOffset     = K / blk          [字节]
kTileStride = scaleXColTileAddressOffset = kLoadNum / blk   [字节]
blk        = {MXBlockSize.M16 -> 16, MXBlockSize.M32 -> 32}
kLoadNum   = getKLoadNum(fmt) = totalN * elemsPerLane(fmt) = kPerBeat
           = {MXFP8 -> 32, MXFP4 -> 64}   (totalN=16)
```
scale 张量**稠密**存放（每个 MX 块 1 字节 E8M0，无 padding）：
```
scaleA: SA_BASE + mm*rowStride + j ,  j in [0, K/blk)
scaleB: SB_BASE + nn*rowStride + j    (N-major，行步进同式)
```
DMA 每拍在该地址发**一次 32-bit 读**：`addr(row, kt) = BASE + row*rowStride + kt*kTileStride`，
传输覆盖 `addr .. addr+3` 四字节；而 Executor 的字节选择
`idx = (lane*elemsPerLane)/blk` 只会选中其中前 `numScalesPerBeat = kPerBeat/blk` 个，
其余为 don't-care（末 kTile 的末行甚至会越出张量结尾 —— TB 侧 `loadByte` 未写地址返回 0，定义良好）。

**③ 契约与充要判据：** 软件必须保证**每一次** scale 读地址 4 字节对齐。因 `row`/`kt` 各自从 0
起连续遍历，故对整个运行成立的充要条件为
```
(S0) BASE % 4 == 0                      (SA_BASE / SB_BASE 由软件给)
(S1) rowStride   % 4 == 0   OR 行范围只有 1 个值 (A 侧 M==1 / B 侧 N==1)
(S2) kTileStride % 4 == 0   OR kTiles == 1 (即 K == kPerBeat)
```
代入 `rowStride = K/blk`、`kTileStride = kPerBeat/blk`：
```
(S1) <=> K % (4*blk) == 0 :  blk=16 -> K % 64 == 0 ;  blk=32 -> K % 128 == 0
(S2) <=> (kPerBeat/blk) % 4 == 0 :
        MXFP8 (kPerBeat=32): blk=32 -> 1 ; blk=16 -> 2   ==> 恒不满足
        MXFP4 (kPerBeat=64): blk=32 -> 2 ; blk=16 -> 4   ==> 仅 blk=16 满足
```

**④ 结论（推导所得，非设计选择）：**
- **MXFP4 + blk=16 是唯一能在任意 M/N/K 下满足契约的非退化组合**：`kTileStride = 4`（S2 成立），
  且 errCode 8 已强制 `K % 64 == 0`，恰好就是 `K % (4*16) == 0`（S1 成立）。
- **MXFP8（blk=16 或 32）与 MXFP4 + blk=32 在 `kTiles > 1` 时无法满足 (S2)**，因其 `kTileStride`
  为 1 或 2 字节。它们的"全对齐子集"是退化的：`kTiles == 1`（`K == kPerBeat`）**且**行数为 1
  （A 侧 `M==1` / B 侧 `N==1`）。
- **★ 该暴露是【既有的】，不是放开 blk=16 引入的**：长期在跑的 MXFP8 blk=32 基线
  `kTileStride = 1`、`rowStride = K/32`；例如现役 QuickSpec 主用例 `K=32, blk=32` 给
  `rowStride = 1` ⟹ 第 1 行的 scale 读地址就是**奇地址**。不支持非对齐 32-bit 读的 SoC
  **在今天全绿的 MXFP8 用例上就已经会出错**。
- 因此集成侧必须三选一：**(a)** SoC 存储/AXI 通路支持非对齐 32-bit 读；**(b)** scale 布局加
  padding 使两个 stride 都为 4 的倍数；**(c)** scale DMA 改发窄（8-bit）读。**(b)/(c) 本轮按裁定
  不实施**，硬件侧也**未加任何守护**。

**⑤ 本轮新增 blk=16 用例的 K 选择：** `HiveCoreQuickSpec` 的 #146 新增用例取
`K=64, aBlk=bBlk=16, MXE5M2×MXE5M2→MXE4M3, M=N=32`。
- 满足 **(S0)**（TB 的 `SA_BASE/SB_BASE` 为 4 的倍数）与 **(S1)**：`rowStride = K/blk = 64/16 = 4` ✓
  —— 即裁定给出的判据「blk=16 需 K 为 64 的倍数」，K=64 是最小合法值。
- **不满足 (S2)**：MXFP8 的 `kTileStride = 32/16 = 2`，`kTiles = K/kPerBeat = 2` ⟹ `kt=1` 的读地址
  余 2 mod 4。**这是 (S2) 在 MXFP8 下【无解】的直接后果**（见 ④），不是选 K 能规避的：K=32 可使
  `kTiles=1` 但 `rowStride` 退化为 2、反而连 (S1) 也破。故按"最大化满足"取 K=64，并把 (S2) 残差
  作为**待裁项**登记（见 ⑥）。

**⑥ 待裁项（只给选项与影响面，本轮一律不实现）：**
| 选项 | 做法 | 影响面 | 代价/风险 |
|---|---|---|---|
| G1 | 不加任何硬件守护，纯软件契约（**本轮现状**） | 零 RTL 改动、两套发射基线 normalized-diff=0 | 违约配置**静默**产出错误地址；上板才暴露 |
| G2 | `HiveCoreCalcConfig` 增运行期 errCode（从**空闲的 7** 起分配），判据 `((K/blk) % 4 != 0) \|\| ((kLoadNum/blk) % 4 != 0 && kTiles>1)` | 新增一个 when 分支（须放在链**最前**以免遮蔽既有码，或按裁定明确其优先级）+ 金锚 `hwErrCode`/`errExplain`/problem.md 四处镜像 | **会把现役全部 MXFP8 用例判为非法**（见 ④ 第三点）⟹ 必须同时改布局或改 DMA，否则等于自毁回归 |
| G3 | scale 布局加 padding：`rowStride`/`kTileStride` 各自向上取整到 4 的倍数 | 改 `scaleXRowAddressOffset`/`scaleXColTileAddressOffset` 两式 + 金锚 `saRowOff/saColOff/sbRowOff/sbColOff` + `buildMemory` 的 scale 落址 + 两条 `expScaleXAddrSeq` | 触碰"禁动区相邻"的金锚几何；scale 张量总字节数上升；软件侧需同步 padding 约定 |
| G4 | scale DMA 改窄读（8-bit/字节使能），或改"每 4 块一读 + 内部移位" | 改 `HiveCoreDmaRdOnly` 的 scale 通道 + FIFO 宽度语义（#145 刚把 8→32 拓宽，等于回退） | 与 #145 的拓宽决策冲突；改动面最大 |

> **推荐**：短期取 **G1 + 集成侧确认 (a)**；若 SoC 确认**不支持**非对齐读，则必须走 **G3**
> （布局 padding），因为它同时解决 `rowStride` 与 `kTileStride` 两个残差，且不回退 #145。
> **G2 单独实施不可行**（会把现役 MXFP8 全判非法），只能作为 G3 的**配套**守护。



