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
- **scale block=64 单广播是 quad-lane 专属假设**：fp4 v1 强制 aMxBlockSize=64（=1 kTile=1 pass=1
  scale 块，保 Executor scaleA 单广播不变式）；混配下 A/B block 语义不同，单广播假设被打破。

**③ v1 处置：保留 errCode 运行期可诊断守护（非静默错算、非永久拒绝）。**
- 硬件：`HiveCoreCalcConfig.scala` errComb 裸块内 `if(cfg.hasMx4){ when(isMx4(aFmt)^isMx4(bFmt))
  errCodeComb:=3.U }`（Fix-4，暂复用 code 3="v1 不支持的格式组合"，置于 errCode7 之前）。
- 金锚：`HiveDebugModel.validate` 加 hasMx4 门控同族 require（Fix-8）+ `precheckConfigErr` 混配
  `Some(3)` + `HiveCoreQuickSpec.hwErrCode`/`errExplain` 镜像（Fix-7）。测试：`HiveFp4GeometrySpec`
  case5 MXE2M1×MXE4M3 → errCode3（Fix-11）。
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

**⑥ v2 可选增强登记（Nina 建议，本次不实现）：** 为 MX4×MX8 混配分配**独立 errCode 8**（而非
复用 code 3），提升可诊断性——软件可区分"A=MX×B=非MX 的非法混配(3)"与"MX4×MX8 的 v2 待支持
混配(8)"。errCode 字段为 STATUS(0x08) bits[6:3]（4-bit，当前占用 0/3/4/5/6/7），**8 可安全新分配**。
本次按用户裁定**暂复用 code 3**，errCode 8 列为 v2 可选增强。

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


