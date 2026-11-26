# Loom GUI → CHI（loom/v2）适配计划 — 2026-11-26

> 状态：**G1–G7 全部落地**（2026-11-26；浏览器结构/功能级验证 + node:test 16/16 + LoomApiSpec 13/13 绿。视口隐藏，无视觉截图，已如实标注验证级别。代码未提交，git 写需授权）
> 裁定闭合（2026-11-26 用户确认）：GUI-OQ-01–05 按建议默认；追加 G7 轻量工程化；拓扑做稳定布局+手动拖拽。
>
> 上游前置（均已落地，2026-11-25）：
> - RTL 迁移 S1–S12（`2026-11-25_loom_chi_migration_plan.md`）+ 硬件补全 HW-01…08 / HQ-01（`2026-11-25_loom_chi_hw_dependency_list.md` §6，formal 10/10）；
> - IR/GEN 收口 S0–S6（`2026-11-25_loom_ir_gen_chi_adaptation_plan.md`，22 suite/230 test 绿 + ci_matrix OK）；
> - 契约单一来源：`src/loom/doc/ir_gen_spec.md`（IR/SEM/GEN/CHK/ENC/MIG 编号，v1.0.0）。
>
> 范围裁定（建议，评审确认）：
> 1. **只改 GUI 资产**：`src/loom/gui/{app.js,index.html,style.css}`，必要时 `src/loom/scripts/gui.sh` 文案。
> 2. **不改后端**：`LoomApi`/`LoomMain serve`/`Reports`/`ChecksV2`/`FabricGraph` 与 API 路由（`/api/evaluate|workspace|draft|examples|example`）保持现状（唯一例外见范围裁定 5）；GUI 是纯消费者。实施中若发现 API 字段缺口，**记录为 gap 并升级裁定**，不得顺手改后端（MIG-10 additive 契约已冻结一个版本）。
> 3. **不改 `ir_gen_spec.md` 契约与 v2 示例**；GUI 侧不新建规格书——本计划 §4 的"面板 ↔ 契约 crosswalk"即 GUI 侧契约，story 必须引用 IR/CHK/GEN/MIG 编号，无编号即视为未覆盖（沿用 IR/GEN 计划验收纪律）。
> 4. **保持零构建零框架**：不引入 bundler/框架/npm 依赖。G1–G6 期间单 `app.js` 内部按现有分节组织；G7 做轻量工程化（原生 ES modules 拆分 + Node 内置 `node:test` 纯函数单测，仍零构建）。验证以应用内浏览器实测为准（§6）。
> 5. **唯一后端例外（随 G7 批准）**：serve 静态资产白名单 `assets`（LoomMain.scala L541）扩展放行 `model.mjs/api.mjs`，`contentType` 增 `mjs` MIME；并适配 `LoomApiSpec` 的 GUI 资产断言（`/api/*` 字符串改断 api.mjs）。仅限 serve 静态服务与该资产测试，**不触碰 API 契约与报告口径**。

## 1. 目标

把 GUI 的数据模型、编辑面板与结果展示从 `loom/v1`（endpoints + MNet/SNet + AXI 语义）整体切换到 `loom/v2` CHI 原生口径（nodes + NodeID、SAM、chi 配置块、QoS(4b)→VC、REQ/RSP/DAT/SNP 通道类、三级流控参数），使 GUI 重新成为 v2 管线的可视化编辑入口。**当前 GUI 对 v2 示例已实质损坏**（§2），本任务是恢复性迁移而非增量增强。

交互基线继承（用户 2026-11-20 多轮实测确立，v2 下等价保留）：

- 创建时强制层次：clock 先行 → partition → router → node；引用只能选已存在对象；node 附接链路自动生成（v2 改为 node↔router 直连，IR-17/CHK-26）。
- 破坏性操作就地两次确认（dangerButton）；列表跟随选中对象过滤。
- 地址映射可编辑 + `auto 4K`（v2 即 SAM 编辑器，IR-22–24）；连接矩阵只读（v2 为 RN×SN 跳数）。
- 所有 router 有 `pos` 时拓扑按网格摆放；workspace 草稿自动保存/恢复（`loom_draft.json`）。
- 对标 FlexNoC 流程：Spec → 拓扑 → 地址映射 → 验证；**提案与已实现严格区分**。

## 2. 现状盘点与损坏证据

GUI 三文件：`index.html`（159 行，面板骨架）、`app.js`（1727 行，全部业务逻辑）、`style.css`（742 行）。后端 serve 路由与草稿机制无需改动（LoomMain.scala L587–650）。

| # | 损坏点 | 证据 | 后果 |
|---|---|---|---|
| B1 | `ensureFabric()` 向任何输入注入 v1 键：`fabric.mnet/snet`、`endpoints`、`axiIdWidth`、`qos=DEFAULT_QOS`（含 maxPacketBeats） | app.js L126–158 | v2 示例加载后被补成 v1/v2 混合体；C0 对未知键报错（ChecksV2.scala L301–306，IR §3 通用规则），**11 个 v2 示例全部 evaluate 被拒** |
| B2 | 常量表为 v1 域：`ADDR_WIDTHS=[16,32,48,64]`、`DATA_WIDTHS=[32,64,128,512,1024]`、`PROTOCOLS=[native,axi4,axilite,tlul]`、`QOS=[ctl,lat,bulk]` | app.js L5–8 | 与 IR-19/35/38、IR-18（MIG-06 退役协议）、IR-21（4b 数值）冲突 |
| B3 | `normalize()` 按 mnet/snet ownership 自动生成附接链路、展开 partitions 成员 | app.js L203–265 | v2 无 mnet/snet；附接边应为 node→router（RN）/router→node（SN），成员折算规则失效（MIG-05） |
| B4 | 地址映射编辑写回 `ir.endpoints[slave].base/size` | app.js L847–871 | v2 窗口在 `sam[]`（IR-22–24），写回落到退役键 → C0 拒绝 |
| B5 | `Canonicalize`/`Export` 采用 `last.canonical`（现为**规范化 v2**，GEN-06/15），随后 `parseEditor→ensureFabric` 又注入 v1 键 | app.js L1668–1682 + B1 | 规范化按钮产出即被自身破坏 |
| B6 | 结构树/inspector/矩阵围绕 mnet/snet/endpoint role 构建 | app.js L598–717、L1055–1123、L463–493 | v2 对象模型不存在这些实体 |
| B7 | 结果面板只读 v1 键：gates 无 G1 四键语义、estimate 无 CHI totals、graph.channels 未处理四元组展开（同一 from/to 现有 class×direction×half 多条边，会重复画线） | app.js L1389–1585；FabricGraph.scala L204–211 | 展示失真/冗余；`upgraded/irSchema/nodes/chi/channels/addressMap.sam` 新键完全未消费（GEN-15） |
| B8 | index.html 提示文案全是 MNet/SNet 口径 | index.html L56、L65、L126 | 用户口径残留（MIG-06） |

后端现状（GUI 可直接消费，无需改动）：`LoomApi.evaluate` 接受 v1（自动升级 + MIG-07 告警）与 v2；返回 `loom/api/v1`，旧键保留、新键 additive（LoomApi.scala L70–86）；`hash/canonical` 为规范化 v2 口径。示例目录已全量 v2（`src/loom/examples/`，v1 副本在 `testdata/v1/` 专供升级器测试）。

## 3. 数据模型切换要点（app.js 全局）

| v1 GUI 状态 | v2 目标 | 契约 |
|---|---|---|
| `ir.endpoints[]`（role/attach 到 net/protocol 四值/qos 类名/base/size） | `ir.nodes[]`：id/kind(rn-i,rn-d,sn-f,sn-i,hn-i,mn)/nodeId(可选)/clock/attach(router)/protocol(axi4,chi)/dataWidth/outstanding/qos(0–15) | IR-13–21 |
| `ir.fabric.mnet/snet` + ownership() | 删除；node 直连 router，附接边自动生成 | IR-17、MIG-05、CHK-24/26 |
| slave base/size + addressMap.slaves 编辑 | `ir.sam[]{base,size,tgtNode}`；读 `data.addressMap.sam`（含 tgtId）；`auto 4K` = 删除 `ir.sam` 走派生 | IR-22–24、SEM-06、GEN-15 |
| `widths={addr,data}` 全局选择器 | `chi.addrWidth∈{32,40,48}`、`chi.datWidth∈{128,256,512}`；node.dataWidth（AXI 侧）在 node inspector 单独编辑 | IR-19/35/38、ENC-01/06 |
| `ir.qos.classes`（含 maxPacketBeats） | `qos.mapping`（**只读**，CHK-34 锁默认表 8/4）+ `qos.classes`（vcs/depth/arbitration/weight 可编辑，maxPacketBeats 删除） | IR-25–34、CHK-32–34 |
| `ir.axiIdWidth` | `chi.txnIdWidth∈{8,12}` | IR-36、MIG-08 |
| link：width/cdc/pipe/asyncFifoDepth | 保留 + `channels[]`（req/rsp/dat 必选，snp 仅 dvmEnable）、`lcreditDepth[1,15]`、`dateline`（仅环上 router↔router）；width 恒等于 chi.datWidth（UI 不提供独立编辑） | IR-54–63、CHK-18/20/37 |
| router：switch.kind/banks/in/out 编辑 | switch/banks **退役删除**；in/out 改只读派生显示（读 estimate.routers 或本地派生）；routing/pos 保留 | IR-46–53、MIG-06、CHK-21/31 |
| selection.kind ∈ {clock,partition,router,mnet,snet,endpoint,link} | {clock,partition,router,**node**,link,**sam**}（+chi/qos 面板级编辑，不进 selection 亦可） | — |

**默认值纪律（techGuidance 全局）**：JS 侧**不复制 `LoomDefaults`/`ChiConfig` 默认值表**。`ensureV2()` 只补编辑所需的结构性空位（`nodes/sam/fabric.routers/fabric.links/partitions/clockDomains` 数组、meta.name），标量默认一律不落地到编辑文本；派生量（beWidth/flitCount64B/maxNodes/NodeID 分配/localPort）只从 API 的 `chi`/`nodes`/`estimate` 快照读取展示。理由：避免 JS/Scala 两份默认表漂移，且保证 `hash` 与 Canonical 口径始终由服务端单一真源决定（GEN-03/06）。

## 4. 面板 ↔ 契约 crosswalk（story 验收追溯表）

| 面板（index.html 锚点） | 现状 | v2 目标 | 引用条目 |
|---|---|---|---|
| Design（#design-name/#addr-width/#data-width） | name + v1 宽度档 | name + `chi.addrWidth`/`chi.datWidth` 档位选择（改档级联 link.width 与 node.dataWidth 提示） | IR-10/35/38/56、ENC-01/06 |
| Clocks（#clocks） | 不变 | 文案去 MNet/SNet；级联逻辑保留 | IR-11/12 |
| Structure（#structure） | partition→router→mnet/snet→ep | partition→router→**node**（kind 徽标 + NodeID 显示读 `data.nodes`）；`+ node` 按 kind 创建，附接边自动生成 | IR-13–17、SEM-01/02、CHK-19/23/26 |
| Router links（#links/#link-form） | width/cdc/pipe | + channels 多选、lcreditDepth、dateline 开关；width 只读=datWidth | IR-56–63、CHK-18/20/37 |
| Endpoints（#endpoints）→ **Nodes** | role/attach/qos 类名表 | id/kind/nodeId/attach/protocol/qos(0–15)/outstanding 表；inspector 全字段编辑 | IR-13–21、CHK-05–12 |
| Address map（#address-map）→ **SAM** | slaves 窗口编辑 | `sam[]` 编辑：tgtNode 下拉（仅 sn-f/sn-i）、base/size、增删行、`auto 4K`；显示 tgtId/end；多窗口合法（HW-02 已闭合） | IR-22–24、SEM-04/06、CHK-16/17/29/30 |
| **CHI（新增块）** | 无 | chi 12 字段编辑 + 派生只读（beWidth/flitCount64B/maxNodes，读 `data.chi`）；dvmEnable 开关联动 snp/rn-d/mn 合法性提示 | IR-35–45、ENC-01–10、CHK-13–15/19 |
| **QoS（新增块）** | 无（类表在 IR 文本里） | mapping 只读表（CHK-34 锁定，注明"自定义阈值待硬件次版本"）；classes 的 vcs/depth/arbitration/weight 编辑 | IR-25–34、CHK-32–34/43、SEM-08 |
| Gates（#gates/#problems） | v1 门禁键 | G1-REQ/RSP/DAT/SNP 四键 + `snpEnable=false` 标注；G7 退役键处置见 GUI-OQ-02；errors 含 CHK 编号直接展示；`upgraded=true` 时顶部 deprecation 横幅（MIG-07 文案） | GEN-11/15/17/18、CHK-35–39、MIG-07 |
| Estimate（#estimate） | totals 平铺 | totals 分组：规模（routers/links/crosspoints）/ CHI（datWidth/txnIdWidth/nodeIdWidth/addrWidth/flitCount64B/maxNodes）/ 代理指标（capacity/latency/area/sram）；assumptions 保留"v1 校准、CHI 待 PPA"提示 | GEN-17、ENC-01 |
| Topology（#topology/#cdg） | 单通道边；布局依赖 evaluate 结果，编辑即漂移 | channels 按 (from,to) **去重**画边；class/vc 聚合图例/tooltip；dateline（含 autoDatelines）与 async 标记；**布局从 IR 推导**（与 evaluate 解耦）+ 确定性排序 + 跨渲染位置缓存 + **手动拖拽**（位置存 localStorage，不进 IR）；网格布局保留 | GEN-10/17、SEM-09/13、FabricGraph.toJson additive 键 |
| Connection matrix（#matrix） | master×slave | **RN×SN**（rn-i/rn-d × sn-f/sn-i），跳数按 attach router 邻接 BFS（逻辑保留） | SEM-01、IR-17 |
| IR JSON（#editor/#apply/#canonical/#export） | v1 文本 | v2 文本；Canonicalize/Export 采用服务端 canonical 后**不再注入 v1 键**（B5 修复）；v1 输入走升级路径（G1） | GEN-04/06/15、MIG-01 |
| Inspector（#inspector） | 6 类实体 | clock/partition/router/node/link/sam 分发；router 去 switch/banks；node 全字段；link 增 channels/lcreditDepth/dateline | IR-46–63、CHK-21 |
| workspace/draft（#workspace/#ws-dialog） | 不变 | 机制不动；草稿可能为 v1 → 升级路径（G1）；最近目录记忆保留 | MIG-01/07 |

## 5. Story 波次（blocker 序，每个 fits-one-run）

### G1 数据模型切换 + v1 草稿升级路径 ✅（2026-11-26 落地：11 v2 示例全绿 13/13 gates、v1 升级+MIG-07 横幅、canonical 回路 hash 稳定、G7 隐藏；浏览器结构/功能级验证）
- tasks：重写常量为 v2 域（KINDS/PROTOCOLS/宽度档/QOS 数值）；`ensureFabric→ensureV2`（§3 默认值纪律）；`normalize()` 改 node↔router 附接边自动生成（RN：node→router；SN：router→node；**hn-i/mn 不生成附接边**——CHK-26 对其豁免（ChecksV2.scala L354），GEN 装配亦不消费，已裁定）、partitions 成员按 node 折算、删除 ownership/mnet/snet 全部代码；selection kind 切换；**v1 升级路径**：`parseEditor`/`adoptDraft` 检测 `schema=loom/v1` → POST `/api/evaluate`，成功则采纳 `data.canonical`（规范化 v2）+ 展示 `warnings` 横幅，失败则保留原文本 + 展示 errors（不在 JS 复刻 Upgrade 逻辑）；修复 Canonicalize/Export 回路（B5）。
- blockedBy：[]
- demoBoundary：浏览器加载全部 11 个 v2 示例，evaluate 零 C0 错误、hash chip 稳定；把 `testdata/v1/ring_4.json` 文本贴入编辑器 Apply → 自动升级为 v2 + deprecation 横幅；Canonicalize 后再 Apply 不产生新错误。
- techGuidance：升级只借服务端单一真源（MIG-01 等价性由 Scala 侧保证）；`ensureV2` 不得写入任何标量默认；本 story 完成后旧结构树/inspector 允许临时降级为只读占位（G2 接管），但 evaluate 回路必须通。

### G2 结构编辑面（rail + inspector）✅（2026-11-26 落地：从零建 2 时钟/2 分区/2 router/2RN+2SN/async 跨分区 link 全绿；改名级联无悬空、删节点附接边同删、第二 mn 就地阻止；node inspector 8 字段；浏览器结构/功能级验证）
- tasks：Design 面板宽度选择器改 chi 档（级联 link.width）；Structure 树改 partition→router→node（kind 徽标、`+ node` 创建菜单、附接边随创建/删除/改名级联）；node inspector 全字段（kind/nodeId/protocol/dataWidth/outstanding/qos/attach，创建时护栏：CHK-19 mn≤1、rn-d⇒恰 1 mn、dvmEnable 联动；CHK-23 至少 1 RN+1 SN 提示）；router inspector 删 switch/banks、in/out 只读派生；link 编辑增 channels 多选/lcreditDepth/dateline；全部 mutation/rename 级联改 v2 引用（sam.tgtNode、partitions.members、links.from/to、node.attach）；两次确认删除与选中过滤保留。
- blockedBy：[G1]
- demoBoundary：浏览器从零建"2 时钟域 + 2 分区 + 2 router + 2 RN + 2 SN + async 跨分区 link"网络，gates 全绿；改名 router 后 sam/partitions/links 引用无悬空；删除 node 附接边同删；创建第 2 个 mn 被就地阻止。
- techGuidance：护栏在创建时强制（不用事后 gate 报错替代）；hn-i/mn 全 kind 暴露 + CHK-19 创建时护栏（GUI-OQ-04 已裁定），且不生成附接边（GUI-OQ-05）；async link 创建仍要求两端异域（CHK-46），发射侧限制（HW-04 网络级集成状态）以 warnings 呈现，不在 GUI 禁止编辑。

### G3 CHI / QoS / SAM 面板 ✅（2026-11-26 落地：datWidth 256→128 联动 flit 2→4/容量 32→16；SAM 重叠 CHK-29 就地报错；auto 4K 复位派生；同 SN 多窗口合法（HW-02）；QoS 类编辑；dvmEnable→snp 通道；CHI 11 字段+派生只读；浏览器结构/功能级验证）
- tasks：rail 新增 CHI 块（12 字段编辑 + 派生只读区读 `data.chi`）与 QoS 块（mapping 只读 + classes 编辑）；Address map 面板改 SAM 编辑器（读 `data.addressMap.sam`，tgtNode 下拉、base/size 编辑写回 `ir.sam`、增删行、`auto 4K`=删除 sam 键）；datWidth 改档 → link.width 级联 + flitCount64B/estimate 联动刷新；dvmEnable 关闭时就地提示既有 snp channels/rn-d/mn 将非法（CHK-18/19）。
- blockedBy：[G1]（可与 G2 并行，面板互不依赖）
- demoBoundary：ring_4 上改 datWidth 256→128，flitCount64B 2→4 且 estimate 容量联动；SAM 手工制造重叠 → CHK-29 错误就地显示；`auto 4K` 恢复派生窗口（explicit=false 文案）；给同一 SN 加第二窗口合法通过（HW-02）。
- techGuidance：SAM 行编辑以 `tgtNode` 为主键（不再按 slave 序索引，多窗口下索引不稳定）；base/size 解析复用现有 parseAddress（支持 0x/64K）；QoS mapping 只读须注明原因（CHK-34/OQ-06），避免用户误以为可改。

### G4 结果面板（Gates / Estimate / Matrix / Nodes 视图）✅（2026-11-26 落地：dvmEnable 联动 channels+G1-SNP 标注；NodeID 显式覆盖 m0=10 与报告一致；mesh RN×SN 跳数矩阵；负例环+弦 G1-DAT/REQ/RSP 失败含 CHK-37/38/35 顶点链；Estimate Scale/CHI/Proxies 分组；Nodes CHI 报告 nodeId/localPort；浏览器结构/功能级验证）
- tasks：Gates 面板 G1 四键 + snpEnable 标注 + G7 隐藏（GUI-OQ-02 已裁定）+ upgraded 横幅；Estimate totals 分组（§4）；Matrix 改 RN×SN；新增 Nodes 报告视图（NodeID 分配/localPort/attach，读 `data.nodes`，可折叠面板或并入 Structure 徽标）；`data.channels` 清单展示（SNP 条件可见）。
- blockedBy：[G1]（G2/G3 完成后联调）
- demoBoundary：ring_4 `dvmEnable` true/false 切换 → channels 清单与 G1-SNP 标注联动；负例 N-G1-a（环+弦）错误链含 CHK 编号完整显示；NodeID 显式覆盖后 Nodes 视图与 SEM-03 一致。
- techGuidance：`data.nodes` 在 gate 失败时可能是 IR-only 回退渲染（无 localPort，Reports.scala L605–619），视图须容忍键缺失；G7 键隐藏但 errors/warnings 全量保留。

### G5 拓扑稳定布局 + 手动拖拽 ✅（2026-11-26 落地：布局从 IR 推导纯函数 autoLayout；同 IR 两次渲染逐坐标一致；增删 node/dvm 切换既有顶点不漂移；拖拽 r0 移动+localStorage 持久化、刷新保留、reset 复位；mesh 网格坐标正确；边按 (from,to) 去重+class/dateline/async 图例；浏览器结构/功能级验证）
- tasks：布局输入改从 **IR 推导**（routers/nodes/links），与 evaluate 结果解耦——门禁失败/通道类展开不再引起布局变化；channels 按 (from,to) 去重画边，class/vc 聚合进 tooltip 与图例；确定性分层（名字排序 + 固定 tie-break），全 router 有 `pos` 时走网格（保留现状）；跨渲染位置缓存（同名实体保留坐标，新实体自动落位）；dateline/autoDatelines/async 边标记；**手动拖拽**（pointer 事件微调坐标），位置持久化到 localStorage（键含 `meta.name`，**绝不写入 IR JSON**，避免污染 hash/C0）；提供"重置布局"按钮。
- blockedBy：[G1]（G2/G3/G4 完成后联调）
- demoBoundary：连续编辑（增删 node、dvmEnable 切换、evaluate 失败→恢复）既有顶点不跳位；拖拽后刷新页面位置保留；同一 IR 两次渲染布局逐坐标一致；mesh_2x2_xy 网格布局不回归；重置布局恢复自动排布。
- techGuidance：布局实现为**纯函数**（IR → 坐标映射），与 DOM 渲染分离，作为 G7 单测锚点；顶点名集 = nodes.id + routers.name（FabricGraph.fromV2 L167）；localStorage 只存坐标旁路数据，读不到时静默回退自动布局；拖拽不改变 selection/commit 语义（不触发 evaluate）。

### G6 文案 / 样式 / 全量回归 ✅（2026-11-26 落地：index.html 去 MNet/SNet 文案、SAM 标题、DOM id nodes/add-node；grep 无 v1 口径残留；11 示例×{加载/canonical 回路/hash 稳定}全绿；负例 CHK-06/16/19/29/38/40 精确命中；workspace 草稿保存+刷新恢复；git diff 仅 gui/ 三文件+plan，后端零改动；浏览器结构/功能级验证）
- tasks：index.html 全部 hint/标题去 MNet/SNet/burst 口径（B8），新增 CHI/QoS/SAM 块骨架；style.css 增补（kind 徽标、channel chips、SAM 表、横幅、拖拽光标），既有视觉语言不动；gui.sh 走查（默认 minimal_4x4.json 已是 v2，必要时仅文案）；全量浏览器回归：11 示例 × {加载/编辑/evaluate/canonical/export/workspace 往返} + 负例集（CHK-06/16/19/29/38/40 各一）+ 拓扑稳定性/拖拽持久化 + file: 协议提示；grep 确认 GUI 资产无 v1 用户口径残留（mnet/snet/maxPacketBeats/axiIdWidth/native/axilite/tlul）。
- blockedBy：[G2, G3, G4, G5]
- demoBoundary：回归清单全绿；`gui.sh` 一键启动到可用；crosswalk（§4）逐行核对无遗漏；后端零改动（git diff 仅 gui/ 与 scripts/gui.sh）。
- techGuidance：回归用应用内浏览器实测并留截图/快照证据；面板不可见时用 take_snapshot + evaluate_script 做结构/功能验证并如实标注验证级别；发现 API gap 记录升级，不夹带后端修改。

### G7 轻量工程化（ES modules + node:test，零构建零框架）✅（2026-11-26 落地：app.js 拆为 model.mjs 纯函数核心 + api.mjs 网络层 + app.js DOM 模块；index.html type=module；serve 白名单+mjs MIME（唯一后端例外）；gui/test/model.test.mjs 16 例 node:test 全绿 + scripts/gui_test.sh；LoomApiSpec GUI 资产断言适配模块拆分后 13/13 绿；浏览器冒烟 11 示例 13/13 且 hash 与拆分前逐一致、升级横幅/拖拽持久化正常；无 package.json/node_modules、零 v1 残留）
- tasks：`app.js` 按职责拆为原生 ES modules——`model.js`（纯函数：ensureV2/normalize/级联改名删除/SAM 写回/parseAddress/布局）、`api.js`（fetch 封装与升级采纳）、`ui.js`（渲染/inspector/事件 wiring）、`main.js`（入口）；index.html 改 `<script type="module">`；**扩展 serve assets 白名单**（LoomMain.scala L541，唯一后端例外，范围裁定 5）；`gui/test/` 下用 Node 内置 `node:test` 对 model 纯函数写单测：附接边生成（RN/SN/hn-mn 豁免）、rename/delete 级联无悬空、SAM 写回与 auto 4K、布局确定性与位置缓存、v1 升级采纳（mock API 响应）；加 `scripts/gui_test.sh`（`node --test`，无 npm 依赖）。
- blockedBy：[G6]
- demoBoundary：`node --test` 全绿；拆分后浏览器冒烟（G6 回归清单抽查）行为零变化；仓库无 package.json/node_modules；serve 白名单外文件仍 404（不开放任意路径）。
- techGuidance：拆分为**行为保持重构**，每批拆分后跑一次浏览器冒烟再继续；纯函数模块不得引用 document/window（DOM 依赖留在 ui.js），否则 Node 侧无法导入；白名单扩展只列实际模块文件名，不做通配放行。

## 6. 验收

- §4 crosswalk 每行在 G1–G7 中有归属 story，验收引用 IR/CHK/GEN/MIG 编号；无编号即未覆盖。
- 每个 story 的 demoBoundary 在应用内浏览器实测通过（golden path + 负路径），不是仅代码审查；G7 另以 `node --test` 全绿为门禁。
- 11 个 v2 示例加载即全绿；v1 文本/草稿经服务端升级可用且告警可见（MIG-01/07）。
- GUI 资产无 v1 用户口径残留（MIG-06）；后端改动仅限 G7 的 serve 静态白名单一行（范围裁定 5），API/报告/校验契约零改动。
- 拓扑：同一 IR 布局逐坐标确定；编辑/门禁状态变化不引起既有顶点漂移；拖拽位置持久化且不进入 IR（hash 不受布局影响）。
- Scala 侧回归不受影响（`testOnly loom.*` 无需重跑作为门禁，但 G6 抽查 `LoomApiSpec` 绿以确认 API 契约未被意外触碰；G7 白名单改动后重跑该 suite）。

## 7. 裁定记录（2026-11-26，用户确认按建议默认闭合）

| ID | 问题 | 裁定 |
|---|---|---|
| GUI-OQ-01 | v1 草稿/文本升级策略 | **服务端升级 + 采纳 canonical**（G1），JS 零复刻 Upgrade 逻辑 |
| GUI-OQ-02 | checks.gates 退役键 G7 | **隐藏不显示**（errors/warnings 保留） |
| GUI-OQ-03 | 拓扑通道类渲染深度 | **去重边 + class 聚合图例/tooltip**；per-class 分层视图留待后续 |
| GUI-OQ-04 | hn-i/mn 创建暴露 | **全 kind 集合暴露 + CHK-19 创建时护栏**（HW-01 已闭合） |
| GUI-OQ-05 | hn-i/mn 附接边方向 | **不自动生成附接边**：CHK-26 对 hn-i/mn 豁免（ChecksV2.scala L354 else true），GEN 装配只消费 router↔router link（ChiAssemble.scala L77–82/L107–121），附接边无消费者 |
| 追加① | 轻量工程化 | **纳入本计划 G7**：ES modules + node:test，零构建零框架；唯一后端例外为 serve assets 白名单扩展（LoomMain.scala L541） |
| 追加② | 拓扑稳定性 | **稳定布局 + 手动拖拽**（G5）：IR 推导布局 + 位置缓存 + localStorage 旁路持久化（不进 IR） |

## 8. 不在本次

- 后端/API/报告/校验任何改动（G7 白名单一行除外）——发现新 gap 走记录升级。
- `ir_gen_spec.md` 契约修订、示例重写、QoS 自定义阈值放开（CHK-34/OQ-06 待硬件次版本）。
- 框架/打包器/npm 依赖等重工程化（React/Vite 之类）；轻量模块化与 node:test 已纳入 G7。
- per-class 拓扑分层/过滤视图、性能/PPA 可视化深化（D10）、emit/formal 结果在 GUI 内的展示（当前 GUI 无此面板，不新增）。
- `src/loom/legacy/` 旧资产删除（MIG-11 一版后另行处置）。