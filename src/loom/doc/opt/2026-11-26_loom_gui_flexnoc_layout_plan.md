# Loom GUI → FlexNoC 风格布局迁移计划 — 2026-11-26

> 状态：**提案（未实施）**。本计划只规划 GUI **界面布局/导航范式**向 FlexNoC 风格的重构，**不是**已完成的 G1–G7 数据模型迁移（见 `2026-11-26_loom_gui_chi_adaptation_plan.md`，G1–G7 已全绿）。两者正交：G1–G7 解决"GUI 消费 v2 口径"，本计划解决"GUI 如何组织视图与交互"。
> 分工：本 agent 只改 GUI 资产；凡需 IR/GEN/RTL 能力支撑的特性，**提出上游改动需求（§6 UP-01/02/04/07/08）交其他 agent 实施**，GUI 侧列为条件特性 F7，待上游落地后消费。UP-03/05/06 已撤销（2026-11-26 用户裁定）。
>
> 调研基础（2026-11-26，公开资料；**像素级截图公开不可得**，下文严格区分"文档证实"与"推断"）：
> - Arteris 官方产品页（文档化特性）：intuitive GUI、topology overlaid on floorplan、mesh topology editor（XL option）、multi-clock/power/voltage domains、QoS bandwidth regulator/limiter、performance analysis、automated verification、NIU tiling。
> - FlexNoC 工具流（cnblogs/-9-8/5473278，经 CSDN ahr7882/101149663 转载）：**三阶段生成** + 两个命名编辑器（§1.2）。
> - Arteris 客户培训笔记（CSDN tiaozhanzhe1900/125772019）：topology（大 crossbar 拆小 switch 利于 floorplan、静态固定 routing）、QoS 三 generator、clock regime→manager→domain 层级、connectivity/memory map、probes/observability。
> - FlexNoC QoS 软件界面配置（CSDN weixin_42664351/142466208）：SocketQoS 的 saturation/bandwidth 在软件界面配置。
>
> 继承约束（持续生效，与 G1–G7 一致）：
> 1. **只改 GUI 资产** `src/loom/gui/{app.js,model.mjs,api.mjs,index.html,style.css}`（必要时 `scripts/gui.sh` 文案）。
> 2. **本 agent 不实施 IR/GEN/RTL/后端改动**（MIG-10 additive 已冻结一版）；唯一既有例外是 G7 已批准的 serve 静态白名单 + mjs MIME（LoomMain.scala L541），本计划若新增模块文件须沿用该白名单机制，不开放任意路径。**但可提出上游改动需求**（§6 UP-01/02/04/07/08）交由其他 agent 实施；GUI 侧依赖这些需求的特性列为**条件特性 F7**，待上游落地后消费，不在 F1–F6 内阻塞。
> 3. **不改 `ir_gen_spec.md` 契约与 v2 示例**；story 必须引用 IR/SEM/GEN/CHK/ENC/MIG 编号，无编号即未覆盖。
> 4. **零构建零框架**：原生 ES modules + `node:test`，无 bundler/npm/package.json。
> 5. **默认值纪律**：JS 不复制服务端默认表；派生量只读 API 快照；hash/canonical 以服务端为单一真源（GEN-03/06）。
> 6. **布局/物理坐标不进 IR**：拖拽位置、floorplan 坐标一律 localStorage 旁路（沿用 G5），不污染 hash/C0。
> 7. **验证纪律**：应用内浏览器实测；视口隐藏时用 take_snapshot + evaluate_script 做结构/功能级验证并如实标注级别；提案与已实现严格区分。
> 8. git 写需用户授权（当前代码均未提交）。

## 1. FlexNoC 布局调研结论

### 1.1 调研边界（如实声明）

FlexNoC 是 Arteris 专有 Eclipse 系工具，**真实 GUI 截图公开不可得**：Scribd FlexExplorer 培训文档 403、fpgalover 客户培训 PDF 加密（AESV2）无法提取文本、icspec 文章配图为 IoT 营销横幅（非 GUI）。因此本计划**不声称复刻 FlexNoC 的像素布局**，而是提取其**文档证实的视图/阶段组织范式**，再映射到 Loom 现有能力。凡涉及具体控件摆放、配色、图标均为**推断**，标注于 §3。

### 1.2 文档证实的 FlexNoC 组织范式

**三阶段生成流（命名编辑器）**——来源 cnblogs/-9-8/5473278：

| 阶段 | 编辑器 | 定义内容 | Loom 对应物 |
|---|---|---|---|
| NoC **Specification** Phase | **FlexArtist Specification Editor** | socket、domain（power/clock）、mapping（Memory / Connectivity / User bits）；之后 Exploration（program/architecture model 仿真） | nodes（socket≈NIU 接入点）、clockDomains/partitions、sam + 连接矩阵 |
| NoC **Architecture** Phase | **FlexArtist Architecture Editor** | performance/area trade-off；之后 DC 综合 | topology 画布（routers/links/pipe）+ estimate |
| NoC **Structural** Phase | （生成器） | netlist + script；FlexVerifier 验证 | RTL 发射 + formal（GUI 不展示，§8） |

**文档证实的视图要素**（培训笔记 + 产品页）：
- **Specification 以 socket 为中心**：socket 是两端接入 transaction-level 的单元（≈Loom node/NIU）；NIU 协议类型必须在 specification 阶段定（≈Loom node.protocol 创建时定，IR-18）。
- **Topology 画布物理感知**：大 crossbar 拆成多个小 switch 以利后端 floorplan；topology 可叠加在 floorplan 上；静态固定 routing（≈Loom SAM 派生路由，SEM-06）；switch 间/switch-NIU 间可加任意级 pipeline（≈Loom link.pipe，IR-58）。
- **QoS 分层配置**：4 种仲裁（rotate/fixed/round-robin/fifo 默认）；initiator NIU 3 种 QoS generator（Fixed/Limiter/Regulator）；SocketQoS 的 saturation/bandwidth 在软件界面配置。
- **clock 层级**：clock regime → clock manager（每 power domain ≥1）→ clock domain（≈Loom clockDomains + partitions，IR-11/12）。
- **connectivity / memory map / memory interleaving** 独立视图（≈Loom SAM + RN×SN 矩阵）。
- **probes / observability**：probe/observer/observation network，3 种 probe（error/packet/transaction）——Loom 无对应物（§8）。

### 1.3 提炼出的布局范式（推断，作为本计划设计依据）

综合上述，FlexNoC 的界面范式可提炼为（**推断**，非截图证实）：

1. **阶段/工作流驱动的多视图**，而非单页平铺所有配置：Specification（定义"是什么"）→ Architecture/Topology（定义"怎么连"）→ 验证。
2. **持久 Specification 树/表**作为全局导航骨架（socket/domain/mapping 一览），选中驱动属性编辑。
3. **中央画布**承载拓扑，物理感知（floorplan 叠加、拖拽摆放、连线）。
4. **上下文属性 inspector** 随选中对象切换。
5. **QoS / Address / Connectivity** 作为可切换的专门视图，而非散落在长表单里。

Loom 当前 GUI 是**单页三栏平铺**（§2），与范式 1/2/5 差距最大；范式 3/4 已部分具备（G5 拖拽 + 现有 inspector）。本计划的核心即把"单页平铺"重组为"工作流多视图 + 持久 spec 树 + 中央画布 + 上下文 inspector"。

## 2. 现状 GUI 布局盘点

`index.html`（181 行）当前为**单页三栏**：

```
masthead: brand | Example/Load/Evaluate/workspace | hash-chip
main.workspace (3 列):
  aside.rail (左栏，纵向堆叠 8 个 block):
    Design / CHI / QoS / Clocks / Structure / Router links / Nodes / SAM
  section.stage (中栏):
    Topology(画布) → split[Gates | Estimate] → Matrix(details) → Nodes report(details) → IR JSON(details)
  aside.inspector (右栏): 随 selection 分发 clock/partition/router/node/link/sam
```

`app.js`（~1900 行，ES module）渲染函数已按面板切分：`renderDesign/renderChi/renderQos/renderClocks/renderStructure/renderLinks/renderNodes/renderSam/renderMatrix/renderInspector/renderSummary` + `showGates/showEstimate/showNodesReport/showTopology`。`model.mjs` 为 DOM-free 纯核心（autoLayout/computeLayout/级联改名删除/SAM 写回/parseAddress 等），`api.mjs` 为网络层。

**布局层面的问题**（非数据损坏，G1–G7 已修数据）：
- L1 左栏 8 个 block 纵向堆叠，**所有配置同时可见**，长滚动；CHI/QoS/SAM/Structure 互相争抢垂直空间，无主次。
- L2 无"阶段"概念：定义（spec）与连线（topology）与验证（gates）混在同一视野，新手难以建立"先定义后连线再验证"的心智模型（FlexNoC 范式 1）。
- L3 拓扑画布只是中栏一个 panel，**不是工作中心**；无 floorplan 叠加、无拖拽连线（只有 G5 拖拽摆放）、无画布工具条（zoom/fit）。
- L4 导航靠滚动而非结构树：选对象要在左栏多个 block 间找（FlexNoC 范式 2 的持久 spec 树缺失）。
- L5 QoS/Address 散在左栏 block，无专门视图承载"per-socket QoS + 连接矩阵"的组合（FlexNoC 范式 5）。

## 3. FlexNoC 范式 → Loom 布局映射（设计提案）

> 本节为**推断性设计**，控件细节待 §7 裁定。原则：**重组现有渲染函数，不重写业务逻辑**；model.mjs 纯核心与 API 契约不动。

### 3.1 目标布局骨架

```
masthead: brand | Example/Load/Evaluate/workspace | hash-chip
phase-tabs: [Spec] [Topology] [Address] [QoS] [Verify]   ← 新增工作流导航
main.workspace (3 列，骨架保留):
  aside.rail = 持久 Specification 树（全局导航，跨 phase 常驻）:
    Design(meta/chi 摘要) → Clocks → Partitions → Routers → Nodes(kind 徽标)
    选中任意对象 → 右栏 inspector；右键/＋ 上下文创建
  section.stage = 当前 phase 视图（随 tab 切换）:
    Spec     : 全局 CHI 配置 + clock/partition 域表 + socket/node 表
    Topology : 中央画布（floorplan 网格叠加 + 拖拽摆放 + 拖拽连线 + 工具条）
    Address  : SAM 编辑器 + RN×SN 连接矩阵（同视图）
    QoS      : per-node QoS + class/VC 表 + mapping(只读)
    Verify   : Gates + Estimate + Nodes report + problems
  aside.inspector = 上下文属性编辑（保留现有分发，跨 phase 常驻）
dock(底部可折叠): IR JSON 编辑器 + Canonicalize/Export（跨 phase 可用）
```

### 3.2 范式映射表

| FlexNoC 范式（§1.3） | Loom 现状 | 提案 | 推断/证实 |
|---|---|---|---|
| 1 阶段多视图 | 单页平铺（L1/L2） | phase-tabs：Spec/Topology/Address/QoS/Verify | 范式证实（三阶段），五视图划分为推断 |
| 2 持久 spec 树 | 左栏 8 block 堆叠（L4） | 左栏改单一层次树 Design→Clocks→Partitions→Routers→Nodes | 范式证实（socket 中心 spec），树形为推断 |
| 3 中央画布物理感知 | 画布是中栏 panel（L3） | Topology 视图以画布为中心 + floorplan 网格 + 拖拽连线 + 工具条 | floorplan 叠加证实；网格/工具条为推断 |
| 4 上下文 inspector | 已有（右栏） | 保留，跨 phase 常驻 | 已有 |
| 5 QoS/Address 专门视图 | 散在左栏（L5） | Address/QoS 独立 phase 视图 | 范式证实（独立视图），组合为推断 |

### 3.3 与契约的边界（不可越界）

- **连接矩阵本次保持只读派生**（RN×SN 跳数，SEM-01/IR-17）：FlexNoC connectivity 可编辑，但 Loom 路由由 SAM 派生（SEM-06）。可编辑连接/显式路由已立**上游需求 UP-01**；上游落地前 GUI 不开放编辑，落地后由 F7 消费。
- **QoS 视图本次诚实映射**：Loom QoS 是 node.qos(0–15) 优先级 + class(vcs/depth/arbitration/weight) + mapping(只读 CHK-34)；**无 bandwidth regulator/limiter 模型**（IR-25–34）。视图呈现 Loom 实际有的字段，**不伪造**带宽控件；带宽调节已立**上游需求 UP-02**，落地后由 F7 增子面板。
- **floorplan 坐标本次不进 IR**：网格叠加与物理坐标走 localStorage 旁路（沿用 G5 键策略），hash 不受影响（约束 6）。"物理设计意图坐标写回 IR"（原 UP-06）已撤销，floorplan 仅作视图叠加。
- **拖拽连线只生成合法 link**：复用现有 link 创建护栏（channels/lcreditDepth/dateline，IR-56–63/CHK-18/20/37），不绕过校验。

## 4. 视图/面板 ↔ 契约 crosswalk（story 验收追溯表）

| 视图/面板 | 现状锚点 | 提案 | 引用条目 |
|---|---|---|---|
| phase-tabs（新增） | 无 | Spec/Topology/Address/QoS/Verify 切换；只切视图，不改 ir/selection/commit 语义 | —（纯 UI） |
| Spec 树（左栏重构） | #structure/#clocks/#design-name | 单一层次树 Design→Clocks→Partitions→Routers→Nodes；kind 徽标 + NodeID（读 data.nodes）；选中驱动 inspector | IR-10–17、SEM-01/02、CHK-19/23/26 |
| Spec 视图 | #chi/#clocks/#nodes | 全局 CHI 12 字段 + 派生只读（data.chi）+ 域表 + socket/node 表 | IR-35–45、ENC-01–10、IR-11–21 |
| Topology 视图 | #topology/#cdg | 画布为中心；floorplan 网格叠加（旁路）+ 拖拽摆放（G5）+ 拖拽连线 + 工具条（zoom/fit/reset）；边按 (from,to) 去重 + class/dateline/async 图例 | GEN-10/17、SEM-09/13、IR-56–63 |
| Address 视图 | #address-map/#matrix | SAM 编辑器（tgtNode/base/size/auto 4K）+ RN×SN 矩阵同视图 | IR-22–24、SEM-04/06、CHK-16/17/29/30、SEM-01 |
| QoS 视图 | #qos | per-node qos(0–15) + class(vcs/depth/arbitration/weight) + mapping 只读（CHK-34 注明） | IR-25–34、CHK-32–34/43、SEM-08 |
| Verify 视图 | #gates/#problems/#estimate/#nodes-report | G1 四键 + snpEnable 标注 + upgraded 横幅；Estimate 分组；Nodes report | GEN-11/15/17/18、CHK-35–39、MIG-07 |
| Inspector（右栏） | #inspector | 保留 clock/partition/router/node/link/sam 分发，跨 phase 常驻 | IR-46–63、CHK-21 |
| IR dock（底部） | #editor/#apply/#canonical/#export | 可折叠 dock，跨 phase 可用；canonical/export 回路不变 | GEN-04/06/15、MIG-01 |
| workspace/draft | #workspace/#ws-dialog | 机制不动 | MIG-01/07 |

## 5. Story 波次（blocker 序，每个 fits-one-run）

### F1 工作流骨架（phase-tabs + spec 树 + inspector/dock 常驻）
- tasks：index.html 增 phase-tabs 与视图容器；app.js 增 `setPhase()`/视图切换（只切换显示，不动 ir/selection/commit/evaluate）；左栏 8 block 重组为单一 spec 树（复用 renderStructure/renderClocks 逻辑，合并为树形导航）；inspector 与 IR dock 改为跨 phase 常驻；现有 render* 函数按视图归组（行为保持重组，不重写）。
- blockedBy：[]
- demoBoundary：浏览器在 5 个 phase 间切换，ir/hash/selection 不变；spec 树选中 node → inspector 正确分发；IR dock 在任意 phase 可 Apply/Canonicalize；11 示例加载后各 phase 渲染无报错。
- techGuidance：视图切换为纯显示层（CSS hidden / 容器切换），**不得**在切换时触发 evaluate 或改写 ir；spec 树复用现有 mutation/级联，不新建数据路径；本 story 后各 phase 内容允许临时占位（F2–F6 接管），但导航与 inspector/dock 必须通。

### F2 Spec 视图（全局 CHI + 域表 + socket/node 表）
- tasks：Spec 视图整合 renderChi（12 字段 + 派生只读）+ clock/partition 域表 + node 表（kind/nodeId/protocol/dataWidth/outstanding/qos/attach）；spec 树与 Spec 视图双向联动（树选中 → 视图高亮 + inspector）；创建护栏保留（CHK-19 mn≤1、rn-d⇒恰 1 mn、dvmEnable 联动）。
- blockedBy：[F1]
- demoBoundary：Spec 视图改 datWidth 256→128 → flitCount64B 2→4 联动；创建第 2 个 mn 就地阻止；dvmEnable 关闭提示既有 snp/rn-d/mn 将非法；树选中与视图高亮一致。
- techGuidance：派生量只读 data.chi（不 JS 复刻）；node 表与 spec 树共享同一 selection 状态，避免两份选中态漂移。

### F3 Topology 画布增强（floorplan 叠加 + 拖拽连线 + 工具条）
- tasks：Topology 视图以画布为中心；增 floorplan 网格叠加层（坐标存 localStorage 旁路，键含 meta.name，**不进 IR**）；画布工具条（zoom/fit/reset layout）；**拖拽连线**：从 router/node 端口拖出 → 落点创建 link（复用现有 link 护栏 channels/lcreditDepth/dateline，IR-56–63）；保留 G5 拖拽摆放与位置缓存；边去重 + class/dateline/async 图例不变。
- blockedBy：[F1]（可与 F2 并行）
- demoBoundary：拖拽 r0→r1 生成合法 link 且 gates 全绿；非法连线（如缺 channels）就地阻止；floorplan 网格开关与坐标刷新页面保留、reset 复位；同一 IR 两次渲染逐坐标一致（G5 不回归）；mesh 网格布局不回归。
- techGuidance：floorplan/拖拽坐标一律 localStorage 旁路（约束 6），读不到静默回退；拖拽连线生成走现有 addLink/mutation 路径，不绕过 CHK；布局纯函数 autoLayout/computeLayout 不动，作为 node:test 锚点。

### F4 Address 视图（SAM + 连接矩阵同视图）
- tasks：Address 视图整合 renderSam（tgtNode 下拉/base/size/增删/auto 4K）+ renderMatrix（RN×SN 跳数，只读）；SAM 行以 tgtNode 为主键；多窗口合法（HW-02）；矩阵随 attach 邻接 BFS 刷新。
- blockedBy：[F1]
- demoBoundary：SAM 制造重叠 → CHK-29 就地报错；auto 4K 恢复派生（explicit=false）；同 SN 第二窗口合法；矩阵跳数与 mesh 拓扑一致；矩阵只读不可编辑（F-OQ-05 默认）。
- techGuidance：base/size 复用 parseAddress（0x/64K）；矩阵保持只读派生（SEM-01），不开放编辑（越界见 §8）。

### F5 QoS 视图（per-node + class/VC + mapping 只读）
- tasks：QoS 视图整合 per-node qos(0–15) 编辑 + class 表（vcs/depth/arbitration/weight）+ mapping 只读表（CHK-34 注明"自定义阈值待硬件次版本"）；**诚实呈现 Loom 字段**，不伪造 bandwidth/saturation 控件（F-OQ-04 默认）。
- blockedBy：[F1]
- demoBoundary：改 node.qos 数值写回 ir.nodes 且 evaluate 通过；class vcs/depth 编辑联动 estimate；mapping 只读不可改且注明原因；dvmEnable 与 QoS 视图无耦合错误。
- techGuidance：QoS 视图只暴露 IR-25–34 实际字段；FlexNoC regulator/limiter 为带宽模型，Loom 无对应 IR，**不引入**（§8）。

### F6 Verify 视图 + 文案/样式 + 全量回归
- tasks：Verify 视图整合 showGates（G1 四键 + snpEnable + upgraded 横幅）+ showEstimate（分组）+ showNodesReport；index.html/style.css 增补 phase-tabs/spec 树/画布工具条/dock 样式（既有视觉语言不动）；全量浏览器回归：11 示例 × {5 phase 切换/编辑/evaluate/canonical/export/workspace 往返} + 负例集（CHK-06/16/19/29/38/40）+ 拓扑稳定性/拖拽/floorplan 持久化；grep 确认无 v1 口径残留；git diff 仅 gui/ 资产。
- blockedBy：[F2, F3, F4, F5]
- demoBoundary：回归清单全绿；crosswalk（§4）逐行核对无遗漏；后端零改动；node:test（model.mjs）全绿不回归；LoomApiSpec 抽查绿（确认 API 契约未触碰）。
- techGuidance：回归用应用内浏览器实测留快照证据；视口隐藏用 take_snapshot + evaluate_script 并如实标注级别；发现 API gap 记录升级，不夹带后端修改。

### F7 条件特性（依赖上游需求 UP-xx，上游落地后另行排期）
- tasks：按 §6 上游需求落地情况，逐项解锁 GUI 消费——UP-01→Address 矩阵可编辑 + Topology 路径高亮；UP-02→QoS 视图 per-node generator 子面板 + 带宽可视化；UP-04→Spec portMode 端到端生效 + Estimate 桥接指标；UP-07→spec 树 subsystem 层 + 分层画布；UP-08→node inspector 速率适配提示。
- blockedBy：[F6] + **对应 UP-xx 已由其他 agent 落地**（外部依赖，非本 agent 实施）。
- demoBoundary：每项以其 UP-xx 的 IR/GEN 契约验收为准 + GUI 消费冒烟；上游未落地前该特性**不渲染伪造控件**（占位注明"待上游 UP-xx"）。
- techGuidance：F7 各子项独立 fits-one-run，按 UP 落地顺序插入；GUI 只消费上游新增的 additive 键（MIG-10），不预判字段；上游未落地时保持 F1–F6 现状不回归。

## 6. 上游改动需求清单（IR/GEN/RTL，交其他 agent 实施）

> **本 agent 不实施下列任何改动**，仅提出需求与验收口径，由负责 IR/GEN/RTL 的 agent 另行立项实施。每条含：动机（FlexNoC 特性/GUI 需求）、当前缺口（源码/契约引用）、需求描述、GUI 消费方式、优先级。编号 UP-xx 供 F7 与 crosswalk 追溯。

| ID | 需求 | 当前缺口（引用） | 需求描述（IR/GEN/RTL） | GUI 消费（F7） | 优先级 |
|---|---|---|---|---|---|
| UP-01 | 可编辑 connectivity / 显式路由 | 路由仅 SAM 派生（SEM-06）；IR 无显式路由/连接约束；矩阵只读（SEM-01） | IR 增可选 `routes[]`/连接约束（RN→SN 路径或禁止对）；GEN 路由表显式约束优先于 SAM 派生；CHK 增一致性校验；RTL 按约束发射路由表 | Address 矩阵可编辑 + Topology 路径高亮 | 中 |
| UP-02 | QoS 带宽 regulator/limiter | IR-25–34 仅优先级+VC，无带宽/urgency/saturation；CHK-34 mapping 锁定 | IR 增 per-node `qosGen{kind:fixed/limiter/regulator, bandwidth, saturation}`；GEN/RTL NIU 注入调节逻辑；CHK 范围校验；mapping 放开或扩展 | QoS 视图 generator 子面板 + 带宽可视化 | 中 |
| UP-04 | portMode/AXI 桥网络级集成 | portMode 仅影响 node.protocol 默认（IrV2.scala:175）与 CHK-40 门禁（ChecksV2.scala:461-468）；发射顶层 `ChiNetworkTop` io 恒为 CHI flit VC bundle，`ChiAxiBridge` 未实例化 → **portMode 不改变实际 RTL 端口形态**（protocol §11、HW-04） | GEN 装配层按 portMode 在 RN/SN 边界实例化 ChiAxiReadBridge/WriteBridge（axi4/both）或保持原生 flit（chi）；RTL 顶层端口随 portMode；CHI/estimate 反映桥接开销 | Spec portMode 端到端生效 + Estimate 桥接代理指标 | 高 |
| UP-07 | multi-NoC / 层次子系统 | Loom 单 fabric；无层次 NoC/NoC 间桥 IR | IR 增层次 `subsystems[]`/NoC 间 link；GEN 分层发射+桥接；CHK 跨 NoC 一致性 | spec 树增 subsystem 层 + Topology 分层画布 | 低 |
| UP-08 | NIU 速率适配（rate adaptation） | node.dataWidth≠chi.datWidth 时 NIU 无 store&fwd 宽度/频率适配（`ChiNi.scala` 无适配逻辑）；FlexNoC 的 NIU tiling 为物理切片特性，Loom 单片发射无对应层（tiling 子项待裁定 F-OQ-08） | GEN/RTL NIU 增宽度/频率失配 store&fwd 适配；IR 暴露适配参数 | node inspector 速率适配提示 + Estimate 面积 | 低 |

**已撤销（2026-11-26 用户裁定）**：UP-03（per-SN atomic）、UP-05（probes/observability）、UP-06（floorplan 物理感知自动 pipeline）。编号保留不复用，F7 与 crosswalk 不再引用。

**上游落地状态（2026-11-26）**：**UP-01 已落地**（ir_gen_spec v1.1.0：IR-70–74/SEM-17/GEN-21/CHK-49–51；LoomApi additive `connectivity`/`routes` 键可供 F7 消费；全量 22 suite/248 test 绿）。**UP-04 已落地**（v1.2.0 GEN-22：portMode/protocol 端到端生效，axi4 节点发射 ChiAxiNetworkTop 混合边界，estimate additive `bridges{rnAxi,snAxi}` 代理计数；F7 可消费）。**UP-01 forbid 升级执行型**（A4/HW-13：samByRn per-source 过滤+NDERR，CHK-51 退役，可达禁止对合法，connectivity 记 unreachable）。UP-02/07/08 硬件前置 HW-10/11/12 待 GEN IR 参数先行（§7.3）；flat async 待 HW-14。**UP-02/UP-08 GEN 先行已落地（v1.3.0，S8）**：node.qosGen/adaptFifoDepth IR+CHK-52/53+装配参数+estimate/API additive `qosGen`/`adapt` 键（声明口径，F7 可渲染声明值；注入/适配 RTL 待 HW-10/HW-12，硬件参数已就绪可开工）。UP-07 仍待立项。**UP-07 已立项（用户 2026-11-26）且 GEN phase 1 落地**（v1.5.0：subsystems[]/nocLinks[] schema+CHK-55–58+装配参数+gatesBySubsystem+estimate/API additive `subsystems`/`nocLinks` 键，F7 spec 树 subsystem 层可消费；分层发射待 HW-11 ChiNocBridge，硬件行动通知见依赖清单 §10）。**UP-07 GEN phase 2 已落地（v1.6.0/v1.7.0，HW-11 闭合消费）**：分层发射 ChiSystemTop 可用（同步桥；合成桥端点装配 SEM-20a、CHK-59–61、ports/manifest/estimate bridges.noc），F7 可消费分层发射与 subsystem 层；**async 桥待 HW-15、子系统内节点宽度适配待 HW-16、桥 outstanding 系统层透传待 HW-17**（依赖清单 §12，GUI 不渲染伪造控件）。

**需求纪律**：UP-xx 均为 additive 扩展（不破坏 MIG-10 冻结契约与现有 v2 示例）；实施 agent 须同步更新 `ir_gen_spec.md` 编号与 CHK 门禁，并在 `LoomApi` 快照 additive 暴露新键，GUI 方能消费。floorplan 仅作视图叠加，物理坐标不进 IR（约束 6，原 UP-06 已撤销）。

## 7. 验收

- §4 crosswalk 每行在 F1–F6 有归属 story，验收引用 IR/SEM/GEN/CHK/ENC/MIG 编号；无编号即未覆盖。
- 每个 story 的 demoBoundary 在应用内浏览器实测通过（golden + 负路径），非仅代码审查。
- 布局重组为**行为保持**：同一 IR 在重组前后 evaluate/hash/canonical 逐一致；model.mjs 纯函数 node:test 全绿不回归。
- 5 phase 切换不改 ir/selection/commit；spec 树/inspector/dock 跨 phase 一致。
- floorplan/拖拽坐标持久化且**不进 IR**（hash 不受布局影响，约束 6）。
- 连接矩阵只读、QoS 视图无伪造带宽控件（§3.3 边界）。
- 后端/API/报告/校验契约零改动（serve 白名单沿用 G7 机制，不新增任意路径）；git diff 仅 gui/ 资产。
- 上游需求 UP-01/02/04/07/08 仅为提案，本 agent 零 IR/GEN/RTL 改动；F7 条件特性在对应 UP 落地前不渲染伪造控件（占位注明"待上游"）。

## 8. 待裁定（建议默认，评审确认）

| ID | 问题 | 建议默认 | 备选 |
|---|---|---|---|
| F-OQ-01 | 导航范式 | **phase-tabs 五视图**（Spec/Topology/Address/QoS/Verify，§3.1） | 保留单页可折叠 section；左图标 rail |
| F-OQ-02 | 拓扑连线交互 | **拖拽连线 + 表单并存**（F3） | 仅表单（现状） |
| F-OQ-03 | floorplan 叠加 | **网格叠加 + 物理坐标 localStorage 旁路**（F3，不进 IR） | 无叠加 |
| F-OQ-04 | QoS 视图深度 | **per-node 优先级 + class/VC 表**（匹配 IR-25–34） | 仿 FlexNoC bandwidth regulator/limiter → 已立 UP-02，落地后 F7 消费 |
| F-OQ-05 | 连接矩阵可编辑性 | **只读派生**（SEM-01/SEM-06） | 可编辑 connectivity → 已立 UP-01，落地后 F7 消费 |
| F-OQ-06 | 单页兼容视图 | **不保留**（phase-tabs 为唯一范式，减维护面） | 保留单页作为可切换兼容模式 |
| F-OQ-07 | spec 树创建入口 | **树内右键/＋ 上下文菜单**（沿用创建护栏） | 保留独立 + 按钮区 |
| F-OQ-08 | UP-08 的 tiling 子项 | **不立项**（物理后端切片，Loom 单片发射无对应层；UP-08 仅保留速率适配） | 立项 tiling（需物理实现层 IR/GEN，超出 Loom 范围） |

## 9. 不在本次（GUI 侧不实施；部分已立上游需求）

- 本 agent 不实施 IR/GEN/RTL/后端任何改动（仅提需求 §6 UP-xx）；`ir_gen_spec.md` 契约修订与示例重写由实施 agent 随 UP 落地同步。
- 连接矩阵可编辑、QoS bandwidth regulator/limiter、portMode 网络级集成、multi-NoC、NIU 速率适配：**本次 GUI 不实现**，已分别立 UP-01/02/04/07/08，待上游落地后由 F7 消费。
- per-SN atomic、probes/observability、floorplan 物理感知自动 pipeline、NIU tiling：**不立项**（UP-03/05/06 已撤销；tiling 为物理后端切片特性，Loom 单片发射无对应层，见 F-OQ-08）。
- per-class 拓扑分层/过滤视图、性能/PPA 可视化深化、emit/formal 结果在 GUI 内展示。
- 框架/打包器/npm 依赖等重工程化；像素级复刻 FlexNoC 配色/图标（公开截图不可得，§1.1）。
- `src/loom/legacy/` 旧资产删除（MIG-11 一版后另行处置）。
- 已完成的 G1–G7 数据模型迁移（本计划与其正交，不重复）。

## 10. 参考来源

- Arteris FlexNoC 产品页：https://www.arteris.com/products/non-coherent-interconnect-ip/flexnoc/
- FlexNoC 三阶段工具流（FlexArtist Specification/Architecture Editor）：https://www.cnblogs.com/-9-8/p/5473278.html （转载 https://blog.csdn.net/ahr7882/article/details/101149663）
- Arteris 客户培训笔记（topology/QoS/clock/connectivity/probes）：https://blog.csdn.net/tiaozhanzhe1900/article/details/125772019
- FlexNoC QoS 软件界面配置：https://blog.csdn.net/weixin_42664351/article/details/142466208
- 学术佐证（FlexNoC 自动化设计环境）：https://aijcst.org/index.php/aijcst/article/view/323