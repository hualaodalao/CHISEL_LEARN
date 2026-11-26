# Loom IR / GEN 规格（loom/v2 用户口径 + CHI 生成管线）

文档版本：`v1.0.0`

修订日期：`2026-11-25`

> 状态：S0 评审门禁稿。本文是 `doc/opt/2026-11-25_loom_ir_gen_chi_adaptation_plan.md` S1–S6 的**唯一契约来源**：
> S1–S6 的字段、门禁、报告字段必须引用本文编号条目（IR-xx / SEM-xx / GEN-xx / CHK-xx / ENC-xx / MIG-xx），无编号即视为未覆盖。
>
> **协议语义不在本文定义**。CHI 通道/flit/opcode/事务流/排序/三级流控的协议行为以 `src/loom/doc/protocol_spec.md` 为单一权威（下称 **PS**），本文只定义"用户配置字段 + 生成管线契约"，逐处交叉引用。

## 版本控制

### 文档版本规则

文档采用 `v主版本.次版本.修订号`：

- 主版本：IR schema 或 GEN 对外契约发生不兼容变更（字段删除、语义改写、错误码含义变化）；
- 次版本：兼容新增字段、门禁、报告键或章节，不改变既有条目契约；
- 修订号：修正文案、排版或引用，不改变契约。

版本号仅标识本文档修订，不代表工具版本（`LoomMain.ToolVersion`）或 IR schema 版本（`loom/v2`）。每次修订必须同步更新首页版本/日期及下表，日期使用 `YYYY-MM-DD`；影响契约的修订必须列出受影响的编号条目。编号按 §2.2 规则保持稳定。

### 修订记录

| 版本号 | 日期 | 修改内容 | 相关条目 |
|---|---|---|---|
| v1.0.0 | 2026-11-25 | 建立首个编号基线（IR/GEN 迁移计划 S0）：定义 `loom/v2` 全字段表与 v1 映射、节点/NodeID/SAM/通道类/QoS/三级流控语义、GEN 单路径管线契约（parse→upgrade→C0/C1/C2→graph/G1→elaboration→emit→reports）、C/G/S 门禁全表（含错误码与消息模板、正/负例）、位宽编码表（交叉引用 protocol_spec）、v1→v2 升级与退役策略、现状代码与目标差异的待裁定清单 | IR-01–IR-69、SEM-01–SEM-16、GEN-01–GEN-20、CHK-01–CHK-48、ENC-01–ENC-10、MIG-01–MIG-13、OQ-01–OQ-09 |

## 1. 文档说明

### 1.1 目的

本文定义 Loom 互连生成套件的**用户可见配置契约**（`loom/v2` JSON IR）与**生成管线契约**（校验、图证明、参数装配、发射、报告、CLI、API），把用户口径从 `loom/v1`（MNet/SNet 四通道运输 + AXI 语义）切换为 CHI 原生口径（节点角色 + NodeID、SAM、REQ/RSP/DAT/SNP 通道类、QoS(4b)、三级流控参数）。拓扑方法论（IR→门禁→图→发射→形式化）保留。

### 1.2 范围

本文覆盖（且仅覆盖）以下代码路径的契约：

- `loom/ir`：schema、解析、Canonical/hash、默认值、v1 升级器、地址窗口数学（`AddressMap`）；
- `loom/check`：C0/C1/C2 与 G/S 门禁；
- `loom/graph`：FabricGraph / CDG / G1；
- `loom/report`：checks/estimate/ports/manifest/addressMap/formal/signoff 报告与 TbGen 骨架口径；
- `LoomMain` / `LoomApi`：CLI 命令、退出码、缓存、API JSON。

本文**不**定义：

- CHI 协议语义（flit 字段含义、opcode、事务流、排序、Atomic/DVM 行为）——见 PS §2–§8；
- `loom/rtl`、`loom/chi` 硬件实现（只被 GEN 调用，本次不改）；
- `loom/gui` 网页实现（API JSON 采用只增不删策略保证 GUI 不破，见 MIG-10）。

### 1.3 规范性用语

- **必须**：违反即被门禁拒绝（报错误码）或行为与本规格不一致，S1–S6 实现不得偏离。
- **应当**：推荐约束，偏离须在 story 评审中记录理由。
- **可以**：允许的实现自由，不构成契约。
- **CURRENT**：当前代码（main 分支，工具版本 1.1.0）已实现的事实，逐条以源码为证。
- **TARGET(v2)**：本规格规定的目标契约，S1–S6 落地后生效；与 CURRENT 冲突且计划未裁定之处列入 §9 待裁定（OQ-xx），**不得**在实现中擅自取边。
- 未加标注的条目为 TARGET(v2)。

### 1.4 记号

- `nodes[]`、`sam[]` 等表示 JSON 数组；`chi.datWidth` 表示对象路径。
- 地址/大小均为字节；JSON 中一律十进制整数（报告展示可用 `0x` 十六进制字符串，见 GEN-17）。
- `2^n` 写作 `1<<n`；`ceil(a/b)` 为向上取整。

## 2. 概述与 ID 规则

### 2.1 管线总览

```text
JSON 文本
  → Json.parse                     (JValue)
  → Upgrade.v1ToV2 [schema=loom/v1 时]  (JValue, warnings)     ← S1
  → C0 校验（schema/字段/范围/引用）      (errors)                ← S2
  → IrV2.fromJson                  (IrV2)                     ← S1
  → C1/C2/G/S 门禁 + graph/G1      (Gates.Result, FabricGraph) ← S2/S3
  → elaboration：ChiParams / ChiNetworkParams（唯一路径）        ← S4
  → emit：ChiselStage → ChiNetworkTop（+分区路径，见 OQ-08）     ← S4
  → reports：checks/estimate/graph/nodes/ports/manifest/signoff ← S5
```

### 2.2 编号族与稳定性规则

| 族 | 编号格式 | 描述对象 |
|---|---|---|
| IR | `IR-xx` | v2 schema 字段（含类型/取值域/默认/必填/语义/v1 映射） |
| SEM | `SEM-xx` | 用户口径语义（NodeID、SAM、通道类、QoS、流控、链路） |
| GEN | `GEN-xx` | 管线阶段、确定性、hash/缓存、CLI、API、报告契约 |
| CHK | `CHK-xx` | 校验门禁（判定条件 + 错误码 + 消息模板 + 正/负例） |
| ENC | `ENC-xx` | 位宽/编码档位（协议含义交叉引用 PS，不重复定义） |
| MIG | `MIG-xx` | v1→v2 升级、退役、兼容策略 |
| OQ | `OQ-xx` | 现状代码与目标冲突、计划未裁定项（非契约，评审必须闭合） |

ID 一经发布不得重排、复用；移除条目保留原 ID 并标注"已退役"（如 CHK 表中的 G7）。新增条目使用族内未分配编号。测试名、错误消息与 story 验收必须携带所覆盖的编号。

### 2.3 术语

节点角色（RN-I/RN-D/SN-F/SN-I/HN-I/MN/HN-F）、通道类（REQ/RSP/DAT/SNP）、flit、transaction、TxnID/SrcID/TgtID、SAM、DBID、P-Credit/L-Credit、QoS/VC、RespErr 等名词定义见 **PS §2**，本文直接使用。旧名词 MNet/SNet、burst/beat（用户口径）、(src,id)、rreq/wdata/rdata/wresp 自 v2 起退役（MIG-06）。

## 3. IR v2 Schema（`loom/v2`）

通用规则（适用于全部字段表）：

- 未声明的可选字段必须按"默认值"列补齐后再进入校验与 elaboration（GEN-03）。
- 所有 `id`/`name` 必须匹配 `[A-Za-z0-9_.-]+`（沿用 CURRENT `Checks.NamePattern`）。
- 未知字段：C0 必须报错（防止拼写错误静默丢失）；v1 升级产物除外（升级器负责删除退役键，MIG-05）。
- "v1 映射"列给出 `loom/v1`（`ir/Ir.scala`）来源字段；`（新增）`表示 v1 无对应。

### 3.1 顶层结构

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-01 | `schema` | string | `"loom/v2"`；`"loom/v1"` 仅作为升级入口被接受 | — | 是 | schema 判别；v1 输入自动升级并告警（MIG-01/07） | `schema`（值变更） |
| IR-02 | `meta` | object | §3.2 | — | 是 | 配置元信息 | `meta` |
| IR-03 | `clockDomains` | array | §3.3，≥1 项 | — | 是 | 时钟域声明 | `clockDomains` |
| IR-04 | `nodes` | array | §3.4，≥2 项（≥1 RN + ≥1 SN） | — | 是 | CHI 节点表，取代 endpoints | `endpoints`（MIG-02） |
| IR-05 | `sam` | array | §3.5 | 按 SN 声明序 4KB stride 自动派生 | 否 | 系统地址映射 | slave `base/size`（MIG-03） |
| IR-06 | `qos` | object | §3.6 | 全默认（见各字段） | 否 | QoS 优先级→类→VC 映射与调度参数 | `qos.classes`（MIG-04） |
| IR-07 | `chi` | object | §3.7 | 全默认（PS §9 档位） | 否 | CHI 配置块 | （新增；v1 部分隐含于 endpoints，MIG-08/09） |
| IR-08 | `fabric` | object | routers/links/timing，§3.8–§3.10 | — | 是 | 互连拓扑 | `fabric`（mnet/snet 键退役，MIG-05） |
| IR-09 | `partitions` | array | §3.11 | `[]` | 否 | 物理/时钟分区 | `partitions` |

退役顶层键：`axiIdWidth`（→ `chi.txnIdWidth`，MIG-08）、`fabric.mnet`、`fabric.snet`（MIG-05）。

### 3.2 meta

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-10 | `meta.name` | string | `[A-Za-z0-9_.-]+` | — | 是 | 网络名，用于顶层模块名与报告 `name` | `meta.name` |

### 3.3 clockDomains[]

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-11 | `id` | string | 名字规则；全表唯一 | — | 是 | 时钟域标识 | `clockDomains[].id` |
| IR-12 | `freqMHz` | number | > 0 | — | 是 | 频率，用于 estimate 容量换算（GEN-17） | `clockDomains[].freqMHz` |

### 3.4 nodes[]

每个 node 即一个 CHI 端口（PS §3）：一个 NodeID + REQ/RSP/DAT（+条件 SNP）。v1 的 endpoint + attach 网络二元组合并为单一 node。

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-13 | `id` | string | 名字规则；全 nodes 唯一 | — | 是 | 节点标识，SAM `tgtNode` 与 link 端点引用它 | `endpoints[].id` |
| IR-14 | `kind` | string | `rn-i` / `rn-d` / `sn-f` / `sn-i` / `hn-i` / `mn`；`hn-f` 仅占位（CHK-05 拒绝生成） | — | 是 | 节点角色（SEM-01） | `role=master→rn-i`、`role=slave→sn-f`（MIG-02） |
| IR-15 | `nodeId` | int | `[0, (1<<chi.nodeIdWidth)-1]`；全网唯一 | 自动分配（SEM-02） | 否 | 显式 NodeID 覆盖；显式优先（SEM-03） | （新增） |
| IR-16 | `clock` | string | 已声明 clockDomain id | — | 是 | 节点时钟域 | `endpoints[].clock` |
| IR-17 | `attach` | string | 已声明 router `name` | — | 是 | 挂接的 router 本地口（v1 attach 到 mnet/snet 改为直接 attach 到 router） | `endpoints[].attach`（经 mnet/snet 一跳折算，MIG-05） |
| IR-18 | `protocol` | string | `axi4` / `chi` | 由 `chi.portMode` 派生：`axi4→axi4`，`chi→chi`，`both→axi4` | 否 | 节点对外端口形态（D6；桥接能力匹配见 CHK-40） | `endpoints[].protocol`（`native/axilite/tlul` 退役，MIG-06） |
| IR-19 | `dataWidth` | int | {32, 64, 128, 256, 512, 1024} | `chi.datWidth` | 否 | AXI 侧数据宽（protocol=axi4 时生效）；与 DAT flit 宽的适配在桥接层（PS §5 F8），fabric DAT 恒为 `chi.datWidth` | `endpoints[].dataWidth`（v1 域 {32,64,128,512,1024}，v2 增补 256） |
| IR-20 | `outstanding` | int | `[1, 1<<chi.txnIdWidth]` | `chi.outstanding` | 否 | 该节点在途事务上限（TxnID 空间约束，CHK-42） | `endpoints[].outstanding`（v1 域 [1,256]） |
| IR-21 | `qos` | int | `[0, 15]`（4-bit 优先级） | `0` | 否 | 该节点发起流量的默认 QoS 优先级；→类→VC 见 SEM-08 | `endpoints[].qos`（类名 ctl/lat/bulk → 数值，MIG-04） |

说明：v1 `endpoints[].addrWidth` 退役为全局 `chi.addrWidth`（IR-38，MIG-09）；v1 slave `base/size` 退役进 `sam[]`（MIG-03）。`hn-i`/`mn` 的 elaboration 位置见 OQ-01。

### 3.5 sam[]

SAM 条目 = 地址窗口 → 目标节点。NI 一次译码（SEM-04）。窗口数学复用 `loom.ir.AddressMap`（S2 techGuidance）。

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-22 | `base` | int | ≥0；`base % size == 0`；`base+size ≤ 1<<chi.addrWidth` | 派生窗口按 SN 序 `index*4096` | 是（显式声明 sam 时） | 窗口基地址（字节） | slave `base`（-1 表示未声明） |
| IR-23 | `size` | int | 2 的幂；≥4096（`AddressMap.DefaultSize`） | 4096 | 是（显式声明 sam 时） | 窗口大小（字节） | slave `size` |
| IR-24 | `tgtNode` | string | 已声明 node `id`，且 kind ∈ {sn-f, sn-i} | 派生窗口按 SN 声明序一一对应 | 是（显式声明 sam 时） | 目标节点（elaboration 译为其 NodeID） | slave 身份隐式（窗口序=slave 序） |

规则：

- `sam` 整体省略时，按 SN 节点声明序派生每节点一个 4KB 窗口（与 CURRENT `AddressMap.windows` 的隐式布局等价，MIG-03）；显式声明与派生不得混用。
- 每个 SN 节点必须至少被一条 sam 条目覆盖（CHK-30）；一个 SN 允许多条窗口（多条时窗口互不重叠，CHK-29）。**CURRENT `ChiParams` require `sam.length == snNodes.length`（1:1），与多窗口目标冲突，见 OQ-02。**
- 窗口两两不重叠（CHK-29）。

### 3.6 qos

v2 的 qos 块 = **4-bit 优先级 → 类** 的映射表 + **类 → 实现私有 VC/调度** 参数表。QoS 字段本身上 CHI flit，VC 不上 flit（PS §2）。

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-25 | `qos.mapping` | array | §IR-26–28 条目；必须无洞、无重叠覆盖 [0,15]（CHK-32） | `[{8,15,ctl},{4,7,lat},{0,3,bulk}]` | 否 | 优先级分段→类 | （新增；对应 CURRENT `ChiQosMap.classOf` 阈值） |
| IR-26 | `mapping[].qosMin` | int | [0,15]，≤ qosMax | — | 是 | 分段下界（含） | （新增） |
| IR-27 | `mapping[].qosMax` | int | [0,15] | — | 是 | 分段上界（含） | （新增） |
| IR-28 | `mapping[].class` | string | `qos.classes[].name` 之一 | — | 是 | 目标类 | endpoint 类名语义上移 |
| IR-29 | `qos.classes` | array | 恰好 {ctl, lat, bulk} 三类、名字唯一（CHK-33） | `LoomDefaults.DefaultQos` 去掉 maxPacketBeats | 否 | 类表（顺序固定 ctl→lat→bulk，类索引 0/1/2） | `qos.classes` |
| IR-30 | `classes[].name` | string | ctl / lat / bulk | — | 是 | 类名，VC 编号按固定类序推导（SEM-08） | `classes[].name` |
| IR-31 | `classes[].vcs` | int | [1,8] | ctl=1, lat=1, bulk=2 | 否 | 类声明的 VC 队列数（fabric 内部；实际 VC 编号仍按 SEM-08 公式） | `classes[].vcs` |
| IR-32 | `classes[].depth` | int | [1,64] | ctl=4, lat=8, bulk=16 | 否 | 每 VC 输入队列深度（flit 数，fabric 内部） | `classes[].depth`（单位由 beat 改 flit） |
| IR-33 | `classes[].arbitration` | string | `wrr` / `rr`（CHK-43） | `wrr` | 否 | 类间/VC 仲裁策略 | `classes[].arbitration` |
| IR-34 | `classes[].weight` | int | [1,64] | ctl=4, lat=2, bulk=1 | 否 | WRR 权重 | `classes[].weight` |

退役：`classes[].maxPacketBeats`（CHI 单笔 ≤64B 是协议常量，AXI burst 由桥接拆分，PS §2；见 MIG-06）。映射阈值可配性受 CURRENT `ChiQosMap` 硬编码限制，见 OQ-06：v2 首版 mapping 必须等于默认表（CHK-34），自定义阈值待硬件裁定后以次版本放开。

### 3.7 chi 配置块

字段与 CURRENT `chi/ChiConfig.scala` 一一对应（默认值/取值域即其 require 集合）；协议含义见 PS §9。整块可省略（全默认）。

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义（配置口径） | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-35 | `chi.datWidth` | int | {128, 256, 512}（**无 64b**，D5） | 256 | 否 | DAT flit 位宽；BE/flit 数派生见 ENC-01 | （新增；v1 link.width/endpoint.dataWidth 不承担此义） |
| IR-36 | `chi.txnIdWidth` | int | {8, 12} | 8 | 否 | TxnID/DBID 位宽（D12） | `axiIdWidth`（[1,8]→档位 8，MIG-08） |
| IR-37 | `chi.nodeIdWidth` | int | {7, 11} | 7 | 否 | NodeID 位宽；maxNodes=1<<nodeIdWidth | （新增） |
| IR-38 | `chi.addrWidth` | int | {32, 40, 48} | 48 | 否 | 系统地址位宽（REQ.Addr / SAM 窗口容量） | 首 master `addrWidth`（16/64 档折算 48 并告警，MIG-09） |
| IR-39 | `chi.outstanding` | int | `[1, 1<<txnIdWidth]` | 64 | 否 | 全局默认在途事务上限（节点可覆盖，IR-20） | `max(64, max(endpoint.outstanding))`（CURRENT `ChiConfig.fromIr`），v2 改显式 |
| IR-40 | `chi.atomicEnable` | bool | true/false | true | 否 | 是否生成 Atomic 引擎（PS §5 F3，D2） | （新增） |
| IR-41 | `chi.atomicEntries` | int | ≥1 | 4 | 否 | Atomic 追踪表项数 | （新增） |
| IR-42 | `chi.dvmEnable` | bool | true/false | true | 否 | 是否生成 DVM/MN/SNP（D3/D11；SEM-07） | （新增） |
| IR-43 | `chi.portMode` | string | `axi4` / `chi` / `both` | `axi4` | 否 | 顶层端口形态（D6）；与 node.protocol 匹配见 CHK-40 | endpoint.protocol 集合收敛（MIG-06） |
| IR-44 | `chi.pcrdTypes` | int | ≥1（首版实现上限 16，PCrdType 4b，PS §4） | 1 | 否 | P-Credit 类型数（SEM-10） | （新增） |
| IR-45 | `chi.lcreditDepth` | int | [1,15] | 4 | 否 | 每通道每方向 L-Credit 深度默认值（SEM-12；link 可覆盖，IR-63） | （新增；取代端到端 burst 信用口径） |

缓冲深度按 BDP 的原则与默认深度留待 PPA 阶段（D10，PS §9）；本文只约束取值域。

### 3.8 fabric.routers[]

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-46 | `name` | string | 名字规则；与 nodes id 全网唯一 | — | 是 | router 标识 | `routers[].name` |
| IR-47 | `clock` | string | 已声明 clockDomain id | — | 是 | router 时钟域 | `routers[].clock` |
| IR-48 | `in` | int | [1,64]；若声明必须等于派生入端口数（CHK-31） | 派生：nLocal + 入向 link 数 | 否 | 入端口数声明（核对用；CHI 端口布局由 GEN 派生，GEN-10） | `routers[].in`（v1 必填、单 router 时须等于 mnet 数——该规则退役） |
| IR-49 | `out` | int | [1,64]；若声明必须等于派生出端口数（CHK-31） | 派生：nLocal + 出向 link 数 | 否 | 出端口数声明 | `routers[].out`（同上） |
| IR-50 | ~~`switch.kind`~~ | — | **退役**（MIG-06；CHI router 恒 flat，banked 无实现且 banks 退役后不可用） | — | — | v2 出现即 C0 报错；v1 升级丢弃+告警（MIG-07） | `switch.kind` |
| IR-51 | ~~`switch.banks`~~ | — | **退役**（MIG-06；用户确认 2026-11-25：v1 实际效果仅剩输出寄存级，CHI router 每输出已有队列+寄存级） | — | — | 同上 | `switch.banks` |
| IR-52 | `pos` | object | `{x:int, y:int}`；xy 路由必填 | 无 | routing=xy 时必填 | mesh 坐标 | `routers[].pos` |
| IR-53 | `routing` | string | `any` / `xy` | `any` | 否 | 路由/转弯模型（xy 用于 CDG 转弯限制，SEM-09） | `routers[].routing` |

### 3.9 fabric.links[]

link 是 fabric 内部运输边；v2 保留 v1 链路参数并新增通道类与 L-Credit 深度。CHI flit 语义见 PS §4/§5 F6。

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-54 | `from` | string | 已声明 node id 或 router name | — | 是 | 源端点；node 只允许出现在 from（RN 注入）或 to（SN 接收）之一，且必须与其 attach router 相邻（CHK-24/26） | `links[].from`（mnet 名折算为 node id） |
| IR-55 | `to` | string | 同上 | — | 是 | 目的端点 | `links[].to`（snet 名折算为 node id） |
| IR-56 | `width` | int | {128, 256, 512}；必须等于 `chi.datWidth`（CHK-20） | `chi.datWidth` | 否 | DAT 线宽（fabric 内部；REQ/RSP/SNP 窄 flit 线宽由 GEN 派生，PS §4）。v1 的 [8,1024] 2 幂域退役 | `links[].width`（域收敛，见 OQ-05） |
| IR-57 | `cdc` | string | `sync` / `async` | `sync` | 否 | 跨时钟方式；sync 两端必须同域（CHK-28），async 两端必须异域（CHK-46） | `links[].cdc` |
| IR-58 | `pipe` | int | [0,8] | 0 | 否 | 链路流水级数 | `links[].pipe` |
| IR-59 | `asyncFifoDepth` | int | async⇒≥2；sync⇒0 | async=2, sync=0 | 否 | 异步 FIFO 深度 | `links[].asyncFifoDepth` |
| IR-60 | ~~`direction`~~ | — | **退役**（MIG-06；HW-07 裁定恒双向，不支持单向链路） | — | — | v2 出现即 C0 报错；v1 升级丢弃+告警（MIG-07），CDG 恒展开两方向（SEM-13） | `links[].direction` |
| IR-61 | `dateline` | bool | true/false；true 仅允许 router↔router 且属于简单环（CHK-37） | false | 否 | 环切割点（VC 半区切换，SEM-09） | `links[].dateline` |
| IR-62 | `channels` | array | {`req`,`rsp`,`dat`,`snp`} 的非空子集，无重复；必须含 req/rsp/dat；含 snp 仅当 `chi.dvmEnable=true`（CHK-18/19） | `["req","rsp","dat"]` + dvmEnable 时追加 `"snp"` | 否 | 该 link 实例化的通道类集合（D11：有什么才生成/证明什么） | （新增；v1 四通道 rreq/wdata/rdata/wresp 恒全量） |
| IR-63 | `lcreditDepth` | int | [1,15] | `chi.lcreditDepth` | 否 | 该 link 每通道每方向 L-Credit 深度覆盖 | （新增） |

### 3.10 fabric.timing

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-64 | `timing.maxFanin` | int | [1,64] | 8 | 否 | 时序告警阈值（只产生 warning，不拒绝） | `fabric.timing.maxFanin` |
| IR-65 | `timing.maxCombDepth` | int | [1,256] | 12 | 否 | 组合深度预算（报告口径） | `fabric.timing.maxCombDepth` |

### 3.11 partitions[]

| ID | 字段 | 类型 | 取值域 | 默认 | 必填 | 语义 | v1 映射 |
|---|---|---|---|---|---|---|---|
| IR-66 | `name` | string | 名字规则；分区名唯一 | — | 是（声明分区时） | 分区标识 | `partitions[].name` |
| IR-67 | `clock` | string | 已声明 clockDomain id | — | 是 | 分区时钟 | `partitions[].clock` |
| IR-68 | `members` | array[string] | node id / router name；全网 fabric 对象必须被恰好一个分区覆盖（CHK-44） | — | 是 | 分区成员；node 必须与其 attach router 同分区（CHK-45） | `partitions[].members`（mnet/snet 成员折算为 node） |
| IR-69 | `boundaryPipe` | int | [1,8] | 1 | 否 | 分区边界寄存级数 | `partitions[].boundaryPipe` |

## 4. 语义规范（SEM）

### 4.1 节点与 NodeID

**SEM-01 节点种类与能力**。kind 集合与 PS §3 节点表一致：

| kind | 类别 | 通道 RX | 生成条件 | 备注 |
|---|---|---|---|---|
| `rn-i` | 请求节点 | REQ TX / RSP RX / DAT 双向 | 恒可 | IO 主，无 cache |
| `rn-d` | 请求节点 | 同上 + SNP RX | 仅 `chi.dvmEnable=true`（CHK-19 联动） | DVM 目标（D3） |
| `sn-f` | 从节点 | REQ RX / RSP TX / DAT 双向 | 恒可 | 内存类；Atomic 执行点（D2） |
| `sn-i` | 从节点 | 同上 | 恒可 | 外设类 |
| `hn-i` | Home | 路由/串行化节点 | 恒可 | elaboration 位置见 OQ-01 |
| `mn` | 杂项 | DVM 分发/收集 | 仅 `chi.dvmEnable=true` | 同上 |
| `hn-f` | 占位 | — | **不可生成**（CHK-05） | 仅协议编码空间占位（D4，PS §7） |

**SEM-02 NodeID 默认分配**。未显式声明 `nodeId` 的节点由 GEN 确定性分配：请求节点组（rn-i/rn-d）按 `nodes[]` 声明序在前，从节点组（sn-f/sn-i）按声明序在后，编号从 0 连续递增——沿用 CURRENT `ChiParams.fromIr`（RN 先、SN 后）。hn-i/mn 的编号段位置 CURRENT 无实现，目标暂定排在 SN 组之后按声明序分配，**待 OQ-01 裁定**。分配结果必须写入 nodes 报告（GEN-17），可复现。

**SEM-03 显式覆盖**。显式 `nodeId` 优先于自动分配；自动分配必须跳过所有已被显式占用的编号；全部节点（含显式）NodeID 必须两两不同且 `< 1<<chi.nodeIdWidth`（CHK-06）。显式与自动混用合法。

### 4.2 SAM

**SEM-04 NI 一次译码**。请求发起侧 NI 用 `sam[]` 把 REQ.Addr 一次译码为 TgtID；fabric 逐跳只按 TgtID 查表路由，不再逐拍译码地址（PS §2 SAM、§5 F7）。sam 条目在 elaboration 中折算为 `{base, size, tgtId=NodeID(tgtNode)}`（CURRENT `ChiSamEntry`）。

**SEM-05 未命中行为**。地址不落入任何窗口时，TARGET 行为是由 NI 产生 NDERR 错误响应（RespErr=11，PS §2），事务不进入 fabric。CURRENT `ChiSamDecode` 骨架把未命中译码为 NodeID 0（源码注释明确错误响应后补）——差异见 OQ-09；v2 校验不因此放行重叠/未覆盖配置。

**SEM-06 派生窗口**。`sam` 省略时按 SN 声明序派生 `base=i*4096, size=4096`（`AddressMap.windows` 隐式布局）；派生结果必须与显式书写等价配置的 elaboration 完全一致（升级等价性测试，MIG-01）。

### 4.3 通道类与 QoS

**SEM-07 通道类集合**。req/rsp/dat 三类恒生成、恒证明；snp 类当且仅当 `chi.dvmEnable=true` 时生成并纳入 G1（D11）。`dvmEnable=false` 时：MN/SNP 不实例化、`rn-d`/`mn` 节点非法（CHK-19）、link.channels 不得含 snp（CHK-18）、G1-SNP 门禁记 trivially-true 并在报告标注 `snpEnable=false`（GEN-17）。

**SEM-08 QoS→VC 映射**。flit 携带 QoS[4]；fabric 按 `qos.mapping` 把优先级映射到类（固定类序 ctl=0/lat=1/bulk=2），实现 VC 编号 = `classIndex * nHalf + half`。**nHalf（每类 VC 道数）由拓扑路由规则派生，不得写死 ×2**（用户裁定 2026-11-25）：首版简单环 dateline ⇒ nHalf=2、half∈{0,1}；未来 mesh 特殊路由规则可能需要更多道数。`vcWidth = log2Ceil(nClasses × nHalf)`（当前 3）。硬件 `ChiQosMap` 须同步参数化 nHalf/vcWidth（HQ-01/AQ-02）。VC 是实现私有调度队列，不出现在 CHI flit（PS §2）。

**SEM-09 dateline 与转弯模型**。简单环（SCC 内每顶点恰一前驱一后继、全部为 router↔router 边）必须有一个 dateline 切割：显式 `dateline=true` 优先，否则 GEN 自动选环内字典序最小 link（CURRENT `FabricGraph` autoDatelines 规则保留）。跨 dateline 的 flit 进入 half=1 的 VC 半区，切割后 CDG 子图无环。非简单 SCC 必须用 xy 转弯模型消解，否则 G1 拒绝（CHK-38）。

### 4.4 三级流控在 IR 中的表达

协议行为见 PS §5 F6；此处只定义参数归属与默认：

| 级 | 协议机制（PS） | IR 表达 | 默认 | 条目 |
|---|---|---|---|---|
| 事务准入 P-Credit | RetryAck→PCrdGrant→AllowRetry=0 重发 | `chi.pcrdTypes`（类型数）+ `chi.outstanding` / node `outstanding`（在途上限） | 1 / 64 | **SEM-10** |
| 数据缓冲 DBID | 接收方授 DBID，写数据 TxnID=DBID | 深度不直接暴露（BDP 推导，D10）；ID 空间由 `chi.txnIdWidth` 决定 | 8b 空间 | **SEM-11** |
| 链路 L-Credit | 每通道每方向 flit 信用，LCRDV 发放 | `chi.lcreditDepth`（全局默认）+ link `lcreditDepth`（覆盖） | 4，域 [1,15] | **SEM-12** |

**SEM-10**：`pcrdTypes` 必须 ≥1 且 ≤16（PCrdType 4b，PS §4）；outstanding 与 TxnID 空间一致性由 CHK-42 保证。
**SEM-11**：DBID 与 TxnID 共用 `chi.txnIdWidth` 位宽档（CURRENT `ChiIdPool` 以 txnIdWidth 建池）；v2 不新增 dbidWidth 字段。
**SEM-12**：L-Credit 深度按 (link, 通道类, 方向) 独立生效；pipe/asyncFifoDepth 引入的在途 flit 由链路信用覆盖，不再有端到端 burst 信用（退役，MIG-06）。

### 4.5 链路与分区

**SEM-13 恒双向**（HW-07 裁定）。CHI link 恒实例化两个方向，CDG 必须为每个 link 的两个方向分别建顶点（TARGET；CURRENT FabricGraph 只建 from→to 单顶点，见 OQ-03）。`direction` 字段退役（MIG-06），v1 forward 配置升级后按双向处理并告警。node↔router 接入边必须覆盖该 node 的注入/接收方向。

**SEM-14 cdc**。sync 边两端必须同时钟域；async 边两端必须异域且 `asyncFifoDepth≥2`。分区边界即 async 切割点（S1/S3 门禁）。

**SEM-15 事务粒度**。CHI 单笔 ≤64B（协议常量，PS §2）；AXI 长 burst 在桥接层拆分为多笔（PS §5 F8）。用户口径不再配置 burst/beat；DAT flit 数 = `ceil(64 / (chi.datWidth/8))`（ENC-01，CHK-41 一致性门禁）。

**SEM-16 分区**。分区是单时钟物理切分：成员全覆盖且唯一、成员时钟等于分区时钟、node 与 attach router 同分区、跨分区边只能是 router↔router（CHK-44–48）。CHI 分区发射路径现状缺失，见 OQ-08。

## 5. GEN 管线契约（GEN）

### 5.1 阶段与输入/输出类型

**GEN-01 阶段表**。TARGET 管线各阶段的输入/输出类型如下（CURRENT 列以当前源码为证）：

| # | 阶段 | 输入 → 输出（TARGET） | CURRENT | 落地 story |
|---|---|---|---|---|
| 1 | parse | `String → JValue`（`Json.parse`，失败抛解析异常） | 相同 | S1 |
| 2 | upgrade | `JValue(loom/v1) → (JValue(loom/v2), List[warning])`（`Upgrade.v1ToV2`）；v2 输入直通 | 无此阶段 | S1 |
| 3 | C0 | `JValue → List[String]`（schema/字段/范围/引用；非空即 exit 3） | `Checks.run(root)` | S2 |
| 4 | IR 构建 | `JValue → IrV2`（`IrV2.fromJson`，默认值已补齐） | `Ir.fromJson → Ir` | S1 |
| 5 | 门禁 | `IrV2 → Gates.Result(gates: Vector[(String,Boolean)], errors: List[String])`（C1/C2/G1/G4/G6/G8/S1/S2/S3） | `Gates.run(ir)` | S2/S3 |
| 6 | graph | `IrV2 → FabricGraph`（顶点 (link, 通道类, 方向, vc)，§5.4） | `FabricGraph.fromIr`（顶点=有向 link） | S3 |
| 7 | elaboration | `IrV2 → ChiConfig → ChiParams → ChiNetworkParams`（**唯一路径**） | 双路径：`FabricParams/NetworkParams`（emit）与 `ChiConfig.fromIr/ChiParams.fromIr/ChiNetworkParams.fromIr`（emit-chi） | S4 |
| 8 | emit | `ChiNetworkParams → ChiNetworkTop` SystemVerilog（分区路径见 OQ-08） | emit→`LmTop/LmNetworkTop/LmWrapperTop`；emit-chi→`ChiTop`(单 router)/`ChiNetworkTop`(多 router) | S4 |
| 9 | reports | `(IrV2, hash, gates, params) → checks/estimate/graph/nodes/ports/manifest/signoff/tb` | `Reports.*`（v1 口径） | S5 |

**GEN-02 单路径**。S4 后 `LoomMain` 的 check/emit/diff/formal/estimate 必须全部走 `ChiParams/ChiNetworkParams`；`FabricParams/NetworkParams` 与 `LmTop/LmNetworkTop/LmWrapperTop` 的 GEN 调用必须删除（文件去留由 S6 按 MIG-11 裁定）。`loom.chi` 硬件源码不得修改（计划范围裁定 2）。

**GEN-03 默认值补齐**。升级与解析后必须得到"全字段显式"的规范化 v2 对象：所有可选字段按 §3 默认列填充（依赖型默认按求解顺序：chi 块 → nodes → sam → qos → links）。规范化对象是 hash 与 Canonical 报告的输入（GEN-06）。

### 5.2 确定性与 Canonical/hash

**GEN-04 Canonical 渲染**。沿用 CURRENT `ir/Canonical.scala`：对象键按字典序排序、无空白、数字去尾零（`-0`→`0`）、字符串最小转义；`pretty` 为键排序缩进 2 的稳定人类可读形。所有 JSON 报告必须以 `Canonical.pretty + "\n"` 落盘，禁止时间戳与绝对路径（CURRENT Reports 约定保留）。

**GEN-05 确定性排序**。elaboration 与报告必须使用以下全序（与 CURRENT `ChiNetworkParams.fromIr` 一致处标注）：

| 对象 | 排序键 | 现状 |
|---|---|---|
| routers | `name` 字典序 | CURRENT 一致 |
| RN/SN 节点表 | NodeID 升序 | CURRENT 一致 |
| router 本地口 | 先 RN 后 SN，按 NodeID 升序 | CURRENT 一致 |
| router–router links | `(from, to)` 字典序 | CURRENT 一致 |
| link 端口号 | nLocal + 关联 link 序号（出边先于入边、去重升序） | CURRENT 一致 |
| CDG 顶点/邻接 | 顶点 id 字典序；DFS/SCC 按排序序遍历 | CURRENT 一致（顶点标签扩展见 §5.4） |
| manifest 文件表 | 相对路径字典序（`\`→`/`） | CURRENT 一致 |
| 报告数组 | 各表按主键（id/name/base）升序 | CURRENT 一致 |

**GEN-06 configHash**。TARGET：`configHash = Canonical.sha256(Canonical.render(规范化v2对象))`，即升级+默认补齐后的 v2 JSON 的 SHA-256。由此：(a) chi 配置块必然纳入 hash（S4 要求）；(b) 语义等价的 v1/v2/省略默认写法 hash 相同。**CURRENT 差异**：hash 对原始 root JSON 计算（`LoomMain.validate` 首行 `Canonical.hash(root)`），未升级、未补默认——v1 基线 hash 将全部变化，缓存与 diff 基线随 ToolVersion 升级一次性失效（MIG-13）；hash 口径切换本身列入 OQ-07 评审确认。

**GEN-07 CG2 发射确定性**。同一规范化配置 + 同一 ToolVersion 两次 emit，产物逐文件字节一致（manifest.files 的 sha256 全等）；CI cg2 脚本按此判定。禁止手改发射物。

### 5.3 缓存与 manifest

**GEN-08 缓存键**。`emit --cache` 命中条件（沿用 CURRENT `manifestMatches` 结构）：`manifest.configHash == configHash` ∧ `manifest.version == ToolVersion` ∧ files 非空 ∧ 每个文件存在且 sha256 匹配。命中则打印 `up to date: <out> (hash=...)` 并跳过发射。TARGET 增补：manifest 必须新增 `irSchema: "loom/v2"` 键（additive，GEN-16）；ToolVersion 升为 `2.0.0`（GEN-12）。

**GEN-09 manifest 内容**。`manifest.json`：`schema=loom/manifest/v1`（保留）、`name`、`version`、`configHash`、`files[]{path,sha256}`；统计范围排除 `manifest.json` 自身与 `formal/`（CURRENT 一致）。新增 `irSchema`、`chi`（配置块快照）两键（additive）。

### 5.4 graph 与 G1

**GEN-10 CDG 顶点**。TARGET：顶点 = `(link, 通道类, 方向, vc半区)`；通道类集合由配置决定（SEM-07），方向按 SEM-13 展开，vc 半区按 SEM-09 切割。依赖边规则保留 CURRENT：router 处入边可等待任一非 U-turn 出边，xy 转弯模型限制照旧；dateline 切割删除离开该 link 的依赖边。**CURRENT 差异**：`FabricGraph.fromIr` 顶点仅为有向 link（无通道/方向/VC 维度），`ChiGates.run` 把同一 `g1ok` 复制到 G1-REQ/RSP/DAT/SNP 四键——PS §11 的顶点口径尚未在代码中兑现，S3 必须实做，见 OQ-03。算法（SCC、简单环判定、autoDateline、确定性 DFS）不得重写，只改顶点标签与通道过滤（S3 techGuidance）。

**GEN-11 G1 输出**。`ChiGates` 是 G1 唯一入口（S3）；输出键固定 `G1-REQ/G1-RSP/G1-DAT/G1-SNP`；`snpEnable=false` 时 G1-SNP=true 且报告注明未生成。错误消息含 offending 顶点链（沿用 `LM-G1: CDG cycle: ...` 格式，顶点标签升级为四元组）。

### 5.5 CLI

**GEN-12 命令与退出码**。TARGET 命令表（★=相对 CURRENT 的变化）：

| 命令 | 参数 | 行为 | 产物 |
|---|---|---|---|
| `check` | `--config <file> [--out <dir>]` | parse→upgrade→C0→门禁；★接受 v1/v2 | `checks.json`、`graph.json`（有 --out 时） |
| `estimate` | `--config <file> --out <dir>` | 同上 + 估算 | `estimate.json` |
| `emit` | `--config <file> --out <dir> [--cache] [--formal]` | ★唯一 CHI 发射路径（GEN-02） | RTL + checks/estimate/graph/nodes/ports/manifest/signoff/tb/sram |
| `emit-chi` | 同 emit | ★退役别名：转发 emit 并打印 deprecation warning（MIG-07），保留一个版本后删除 | 同 emit |
| `formal` | `--config <file> --out <dir>` | 10 个 token 模型 yosys SAT（5 旧 + 5 CHI，CURRENT `runFormalModels` 表保留） | `formal/formal.json`、`formal/*.log` |
| `diff` | `--a <file|dir> --b <file|dir>` | 目录：比较 checks/estimate/graph 三报告 Canonical 渲染；文件：★比较 nodes（kind:id:nodeId）/routers/links 契约摘要（v1 的 endpoints 摘要退役） | stdout |
| `blocks` | `--out <dir>` | 旧 CDC/width 积木发射；★已退役（S6 裁定 2026-11-25：随 MIG-11 隔离，命令删除；CDC 原语保留于 `rtl/LmCdc.scala` 供 ChiAsyncLink，width 积木在 `src/loom/legacy/`） | —（退役） |
| `serve` | `--dir <dir> [--port] [--examples] [--work]` | GUI + API（GEN-14–16），GUI 资产不改 | HTTP |
| `version` | — | ★打印 `loom 2.0.0 (schema loom/v2, loom/v1 auto-upgrade)` | stdout |

退出码（CURRENT 事实，TARGET 保留）：

| 码 | 含义 |
|---|---|
| 0 | 成功（含 --cache 命中、diff MATCH） |
| 2 | 用法错误（缺参数/未知命令，打印 usage） |
| 3 | 解析失败 / C0 或门禁错误 / elaboration require 失败 / diff 缺报告文件 |
| 4 | formal 任一模型 FAIL / diff 报告或契约不一致 |

**GEN-13 错误输出格式**。stderr/stdout 错误行必须以 `ERROR: ` 前缀 + 门禁前缀 + CHK 编号开头（TARGET 统一格式，见 §6 消息模板）；warning 行以 `WARN: ` 前缀。CURRENT 消息无 CHK 编号，S2 起补齐。

### 5.6 API JSON（GUI 不破策略）

**GEN-14 evaluate 契约**。`LoomApi.evaluate(text: String): JValue` 保持纯函数（无 IO）；`POST /api/evaluate` 返回 `Canonical.pretty` 文本。输入允许 v1 或 v2 文本；v1 自动升级并在 `warnings` 中携带 MIG-07 文案。

**GEN-15 API 字段表**。`loom/api/v1` 顶层键（★=新增，additive-only，MIG-10）：

| 键 | 类型 | 语义 | 现状 |
|---|---|---|---|
| `schema` | string | 恒 `"loom/api/v1"`（本版本不改值） | CURRENT |
| `ok` | bool | 全部错误为空 | CURRENT |
| `hash` | string | configHash（GEN-06 新口径） | CURRENT（口径变更） |
| `errors` / `warnings` | array[string] | 错误/告警（含升级告警） | CURRENT |
| `checks` | object | checks.json 同构（GEN-17） | CURRENT |
| `estimate` | object | estimate.json 同构 | CURRENT |
| `addressMap` | object | ★内容改为 SAM 视图，但旧键 `explicit/routeShift/stride/slaves[]{id,index,base,size,end}` 保留一个版本；新增 `sam[]{base,size,tgtNode,tgtId}` 键 | CURRENT 为 v1 窗口表 |
| `graph` | object | graph.json 同构（★顶点标签升级，旧 `nodes/channels/cdg/datelines/autoDatelines/acyclic` 键保留） | CURRENT |
| `canonical` | string | ★规范化 v2 的 pretty 文本（CURRENT 为原始 root 的 pretty） | CURRENT（口径变更，OQ-07） |
| ★`upgraded` | bool | 输入是否经 v1 升级 | 新增 |
| ★`irSchema` | string | `"loom/v2"` | 新增 |
| ★`nodes` | object | chi_nodes.json 同构（GEN-17） | 新增 |
| ★`chi` | object | chi 配置块快照（含派生 beWidth/flitCount/maxNodes） | 新增 |
| ★`channels` | array[string] | 实际生成的通道类清单（SNP 条件可见，S3 demoBoundary） | 新增 |

C0 失败时返回仅含 `schema/ok/hash/errors/warnings` 的失败对象（CURRENT 行为保留）。

**GEN-16 additive-only 策略**。S5 落地版起一个版本内：API 与全部报告 JSON 的既有键**只增不删不改类型**；语义升级通过"旧键保留旧口径或可推导口径 + 新键承载 CHI 口径"表达（如 addressMap）。删除旧键必须等下一主版本并在 MIG 表登记。GUI（`loom/gui`）与 `/api/workspace`、`/api/draft`、`/api/examples`、`/api/example` 端点本次不改。

### 5.7 报告

**GEN-17 报告字段表**。TARGET 各报告在 CURRENT 基础上 additive 演进：

| 文件 | schema 键 | 保留字段（CURRENT） | ★新增/口径变更 |
|---|---|---|---|
| `checks.json` | `loom/checks/v1` | name/configHash/ok/errors/warnings/gates | gates 新增 `G1-REQ/G1-RSP/G1-DAT/G1-SNP`；旧 `G7` 键保留恒 true 一个版本（已退役门禁，MIG-06）；`G6` 含义换为 CHK-41/42 |
| `estimate.json` | `loom/estimate/v1` | links/routers/totals/assumptions/areaSensitivity/warnings | links[] 增 `channels/lcreditDepth`；totals 增 `datWidth/txnIdWidth/nodeIdWidth/addrWidth/flitCount64B/maxNodes`；容量公式改按 DAT 位宽（`datWidth/8 * MHz/1000 GB/s`）；v1 面积拟合系数标注为旧四通道校准、CHI 校准待 PPA（D10） |
| `graph.json` | —（无 schema 键，保留） | nodes/channels/cdg/datelines/autoDatelines/acyclic | channels[] 增 `class/direction/vc` 标签字段；cdg 边标签同步四元组 |
| `chi_nodes.json`（沿用现名） | —（保留） | name/datWidth/rn[]{id,kind,nodeId,clock}/sn[]/sam[]{base,size,tgtId} | rn/sn 条目增 `attach/localPort`；sam 条目增 `tgtNode`；新增 `hn[]/mn[]`（OQ-01 裁定后）与 `snpEnable` |
| `ports.json`（分区） | `loom/ports/v1` | partition/configHash/partitionHash/clock/boundaryPipe/boundary[] | `masters/slaves` 键保留（内容映射 RN/SN），新增 `nodes[]{id,kind,nodeId,router,port}`；boundary[] 增 `channels/lcreditDepth`；分区发射路径待 OQ-08 |
| `manifest.json` | `loom/manifest/v1` | name/version/configHash/files | 增 `irSchema/chi`（GEN-09） |
| `formal/formal.json` | `loom/formal/v1` | name/configHash/engine/ok/checks/limitations | checks 含 4 个 CHI token 模型（CURRENT 已实现）；limitations 增"按生成通道类证明（D11）" |
| `signoff.md` | — | 结构保留 | `Schema: loom/v2`；门禁表含 G1 四键；节点/SAM 摘要节新增 |
| `tb/*_tb.sv` | — | TbGen 骨架机制保留 | 激励骨架改 CHI 端口口径（S5，TbGenSpec 迁移） |

**GEN-18 warning 通道**。warning 不改变退出码；来源限定：v1 升级 deprecation（MIG-07）、addrWidth 档位折算（MIG-09）、timing maxFanin 建议（CURRENT `LM-W-TIMING` 文案保留）、emit-chi 别名（MIG-07）。

**GEN-19 formal 契约**。`formal` 与 `emit --formal` 运行 CURRENT `runFormalModels` 的 10 模型表（token_8x8/rr8/response/cdc/cdc_buf + token_chi_lcredit/dbid/pcrd/dvm/async_credit），深度与判定串（`SAT proof finished - no model found: SUCCESS`）不变；模型与配置无关、结果跨网络复用的 CURRENT 注释口径保留，CHI 模型按配置条件化的深化不在本次（PS §11）。

**GEN-20 版本标识**。ToolVersion `1.1.0 → 2.0.0` 于 S4 切换；`version` 命令、manifest.version、缓存键同步（GEN-08）。S1–S3 期间 ToolVersion 不变，v2 解析能力以 schema 字段判别。

## 6. 校验规则表（CHK）

### 6.1 门禁总表与错误码约定

门禁集合与执行顺序（Gates 聚合，顺序即输出顺序）：`C0 → C1 → G1 → C2 → G4 → G6 → G8 → S1 → S2 → S3`（CURRENT `Gates.run` 顺序保留；G7 退役，键位保留恒 true，见 GEN-17）。C0 失败立即终止（exit 3），其余门禁全量收集后一次报告。

错误码 = CHK 编号；消息模板统一为：

```text
<门禁>: [<CHK-id>] <模板正文>
```

例：`C0: [CHK-06] node 'n3' nodeId 130 out of range [0,127]`。`<>` 为运行时插值。G/S 系沿用 `LM-` 前缀（`LM-G1: [CHK-35] ...`）。正/负例见 §6.3–§6.12，片段均省略无关字段（默认补齐后语义完整）。

### 6.2 规则表

#### C0：schema、字段、范围、引用（S2）

| ID | 判定条件（违反即报错） | 消息模板 | 例 |
|---|---|---|---|
| CHK-01 | `schema ∉ {loom/v2, loom/v1}` | `C0: [CHK-01] schema must be 'loom/v2' (or 'loom/v1' for upgrade), got '<s>'` | §6.3 a |
| CHK-02 | `meta.name` 不匹配名字规则 | `C0: [CHK-02] meta.name '<n>' must match [A-Za-z0-9_.-]+` | §6.3 a |
| CHK-03 | clockDomain id 重复或 `freqMHz ≤ 0` | `C0: [CHK-03] clockDomain '<id>' duplicate id / freqMHz must be > 0` | §6.3 a |
| CHK-04 | node id 重复、不合法或与 router name 冲突 | `C0: [CHK-04] duplicate node/router name '<n>'` | §6.3 b |
| CHK-05 | `kind` 不在六类集合；`kind=hn-f`（占位不可生成） | `C0: [CHK-05] node '<id>' kind '<k>' invalid; hn-f is a reserved placeholder` | §6.3 b |
| CHK-06 | NodeID（显式或分配后）越界 `[0, 1<<nodeIdWidth)` 或重复 | `C0: [CHK-06] node '<id>' nodeId <v> out of range [0,<max>] / duplicate nodeId <v>` | §6.3 b |
| CHK-07 | node.clock 未声明 | `C0: [CHK-07] node '<id>' clock '<c>' is not declared` | §6.3 b |
| CHK-08 | node.protocol ∉ {axi4, chi} | `C0: [CHK-08] node '<id>' protocol '<p>' must be axi4 or chi` | §6.3 c |
| CHK-09 | node.dataWidth ∉ {32,64,128,256,512,1024} | `C0: [CHK-09] node '<id>' dataWidth <w> must be one of 32,64,128,256,512,1024` | §6.3 c |
| CHK-10 | node.outstanding ∉ `[1, 1<<txnIdWidth]` | `C0: [CHK-10] node '<id>' outstanding <v> must be in [1,<cap>]` | §6.3 c |
| CHK-11 | node.qos ∉ [0,15] | `C0: [CHK-11] node '<id>' qos <v> must be in [0,15]` | §6.3 c |
| CHK-12 | node.attach 不是已声明 router | `C0: [CHK-12] node '<id>' attach '<r>' is not a declared router` | §6.3 b |
| CHK-13 | chi 位宽档非法：datWidth∉{128,256,512}、txnIdWidth∉{8,12}、nodeIdWidth∉{7,11}、addrWidth∉{32,40,48} | `C0: [CHK-13] chi.<f> must be one of <set>, got <v>` | §6.3 c |
| CHK-14 | chi 标量非法：outstanding<1、atomicEntries<1、pcrdTypes∉[1,16]、lcreditDepth∉[1,15] | `C0: [CHK-14] chi.<f> must be in <range>, got <v>` | §6.3 c |
| CHK-15 | chi.portMode ∉ {axi4, chi, both} | `C0: [CHK-15] chi.portMode '<p>' unknown` | §6.3 c |
| CHK-16 | sam 窗口数学：size 非 2 幂 / size<4096 / base 未按 size 对齐 / base+size 超 `1<<addrWidth` / 显式与派生混用 | `C0: [CHK-16] sam entry '<tgtNode>' <violation>` | §6.3 d |
| CHK-17 | sam.tgtNode 不存在或 kind ∉ {sn-f, sn-i} | `C0: [CHK-17] sam tgtNode '<t>' is not a subordinate node` | §6.3 d |
| CHK-18 | link.channels 含非法名 / 重复 / 缺 req/rsp/dat / 空集 | `C0: [CHK-18] link '<f>-><t>' channels <violation>` | §6.3 e |
| CHK-19 | `dvmEnable=false` 且（任一 link.channels 含 snp，或存在 kind∈{rn-d,mn} 节点）；mn 节点多于 1 个；存在 rn-d 而 mn 不恰好 1 个（AQ-01③） | `C0: [CHK-19] snp/rn-d/mn requires chi.dvmEnable=true` / `C0: [CHK-19] at most one mn node allowed` / `C0: [CHK-19] rn-d requires exactly one mn node` | §6.3 e |
| CHK-20 | link.width ∉ {128,256,512} 或 ≠ chi.datWidth；pipe∉[0,8]；cdc∉{sync,async}；`direction` 键出现（退役，MIG-06）；async 且 asyncFifoDepth<2；lcreditDepth∉[1,15] | `C0: [CHK-20] link '<f>-><t>' <field> <violation>` | §6.3 e |
| CHK-21 | router：in/out（若声明）∉[1,64]；`switch` 块出现（退役，MIG-06）；routing∉{any,xy}；xy 无 pos；xy 相邻 link 非曼哈顿邻接 | `C0: [CHK-21] router '<r>' <violation>` | §6.3 f |
| CHK-22 | timing.maxFanin∉[1,64] 或 maxCombDepth∉[1,256] | `C0: [CHK-22] fabric.timing.<f> must be in <range>` | §6.3 f |
| CHK-23 | 请求节点（rn-i/rn-d）数为 0 或从节点（sn-f/sn-i）数为 0 | `C0: [CHK-23] at least one request node and one subordinate node are required` | §6.3 b |

#### C1：图结构一致性（S2）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-24 | link.from/to 引用未声明对象；node 端点方向非法（RN 出现在 to、SN 出现在 from）；node 端点 ≠ 其 attach router 的对端 | `C1: [CHK-24] link '<f>-><t>' <violation>` | §6.4 a |
| CHK-25 | `(from,to)` 重复 link | `C1: [CHK-25] duplicate link '<f>' -> '<t>'` | §6.4 a |
| CHK-26 | node 无接入 link（RN 无出边 / SN 无入边），或接入边不落在 attach router 上 | `C1: [CHK-26] node '<id>' has no attachment link to router '<r>'` | §6.4 a |
| CHK-27 | router 不被任何 link 引用 | `C1: [CHK-27] router '<r>' is not referenced by any link` | §6.4 a |
| CHK-28 | sync link 两端时钟域不同 | `C1: [CHK-28] sync link '<f>'(<a>) -> '<t>'(<b>) crosses clock domains` | §6.4 b |
| CHK-29 | sam 窗口两两重叠（按 base 排序后 `a.base+a.size > b.base`，窗口数学复用 AddressMap） | `C1: [CHK-29] sam windows of '<t1>' and '<t2>' overlap` | §6.4 b |
| CHK-30 | 某 SN 节点无任何 sam 条目覆盖 | `C1: [CHK-30] subordinate node '<id>' has no SAM window` | §6.4 b |
| CHK-31 | 显式 router.in/out ≠ 派生端口数（nLocal+入/出向 link 数；bidirectional 计入两向） | `C1: [CHK-31] router '<r>' in=<v> does not match derived <d>` | §6.4 a |

#### C2：QoS 映射完整性（S2）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-32 | qos.mapping 分段有空洞 / 重叠 / 越界 [0,15] / qosMin>qosMax / class 未声明 | `C2: [CHK-32] qos.mapping <violation> at [<lo>,<hi>]` | §6.5 |
| CHK-33 | classes 不是恰好 {ctl,lat,bulk}、名字重复、vcs∉[1,8]、depth∉[1,64]、weight∉[1,64] | `C2: [CHK-33] qos class '<n>' <violation>` | §6.5 |
| CHK-34 | mapping ≠ 实现默认表 `{[8,15]→ctl,[4,7]→lat,[0,3]→bulk}`（首版实现约束，OQ-06） | `C2: [CHK-34] qos.mapping deviates from the implemented ChiQosMap thresholds (8/4); custom thresholds not yet supported` | §6.5 |

#### G1：CDG 死锁自由（S3；ChiGates 唯一入口）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-35 | 任一生成通道类的 CDG（dateline 切割后）存在环 | `LM-G1: [CHK-35] CDG cycle (<class>): <v1> -> <v2> -> ...` | §6.6 |
| CHK-36 | 自环 link（from==to） | `LM-G1: [CHK-36] self-link <label> is not allowed` | §6.6 |
| CHK-37 | `dateline=true` 的 link 不属于任何简单环，或不是 router↔router 边 | `LM-G1: [CHK-37] dateline link <label> is not part of a ring` | §6.6 |
| CHK-38 | SCC 非简单环（存在多前驱/后继或含非 router 边）且无转弯模型消解 | `LM-G1: [CHK-38] CDG cycle: <path> (not a ring; use a turn model or turn VCs)` | §6.6 |
| CHK-39 | SNP 纳入规则被违反：`dvmEnable=true` 而 G1-SNP 未证明，或 `dvmEnable=false` 而 SNP 顶点参与证明/生成 | `LM-G1: [CHK-39] SNP proof inclusion does not match dvmEnable=<b>` | §6.6 |

G1 通过判据：对每个生成通道类 c ∈ {req,rsp,dat} ∪ ({snp} 若 dvmEnable)，类内 CDG 无环且无结构错误；未生成类恒 true（D11，SEM-07）。

#### G4：端口形态与桥接能力（S2；替代旧 shell 覆盖门禁）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-40 | node.protocol=axi4 而 portMode=chi；或 node.protocol=chi 而 portMode=axi4（both 恒兼容）；v1 遗留协议 native/axilite/tlul 出现在 v2 输入 | `LM-G4: [CHK-40] node '<id>' protocol '<p>' incompatible with chi.portMode '<m>'` | §6.7 |

#### G6：CHI 容量一致性（S2；替代旧 burst 拍数门禁）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-41 | elaboration/报告中的 64B 事务 DAT flit 数 ≠ `ceil(64/(datWidth/8))`（即 128→4、256→2、512→1；`ChiWidths.flitCount` 金锚） | `LM-G6: [CHK-41] DAT flit count <v> inconsistent with datWidth=<w> (expected <e>)` | §6.8 |
| CHK-42 | `chi.outstanding > 1<<txnIdWidth`（全局；节点级由 CHK-10 覆盖） | `LM-G6: [CHK-42] chi.outstanding <v> exceeds TxnID space 2^<w>=<cap>` | §6.8 |

**G7 已退役**（ctl 单拍约束随 burst/beat 口径消亡，MIG-06）：ID 保留、不再产生错误；checks.json 的 `G7` 键保留恒 true 一个版本（GEN-17）。

#### G8：仲裁策略（保留）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-43 | classes[].arbitration ∉ {wrr, rr} | `LM-G8: [CHK-43] qos class '<n>' arbitration must be wrr or rr` | §6.9 |

#### S1/S2/S3：分区（保留，口径适配 v2）

| ID | 判定条件 | 消息模板 | 例 |
|---|---|---|---|
| CHK-44 | 分区名重复 / 成员为空 / clock 未声明 / 成员不是 fabric 对象 / 对象跨分区 / 对象未分配 | `LM-S1: [CHK-44] partition '<p>' <violation>` | §6.10 |
| CHK-45 | 成员时钟 ≠ 分区时钟；node 与 attach router 不同分区（替代 v1 attachment-link 规则） | `LM-S1: [CHK-45] node '<id>' runs on clock '<c>', expected '<pc>' / crosses partition boundary` | §6.10 |
| CHK-46 | async link 两端同时钟域 | `LM-S1: [CHK-46] async link '<f>' -> '<t>' joins clock domain '<a>'; use cdc "sync"` | §6.10 |
| CHK-47 | boundaryPipe∉[1,8]；分区无 router 成员 | `LM-S2: [CHK-47] partition '<p>' boundaryPipe must be in [1,8] / has no router` | §6.11 |
| CHK-48 | 跨分区 link 不是 router↔router | `LM-S3: [CHK-48] cut link '<f>' -> '<t>' is not router-to-router` | §6.12 |

### 6.3 C0 正/负例

**正例 P-C0**（覆盖 CHK-01–23 的合法侧；即附录完整配置的核心片段）：

```json
{ "schema": "loom/v2", "meta": { "name": "ok_c0" },
  "clockDomains": [ { "id": "core", "freqMHz": 1000 } ],
  "chi": { "datWidth": 256, "txnIdWidth": 8, "nodeIdWidth": 7, "addrWidth": 40,
           "outstanding": 64, "pcrdTypes": 1, "lcreditDepth": 4, "dvmEnable": false },
  "nodes": [
    { "id": "m0", "kind": "rn-i", "clock": "core", "attach": "r0", "qos": 4 },
    { "id": "s0", "kind": "sn-f", "clock": "core", "attach": "r0" } ],
  "sam": [ { "base": 0, "size": 4096, "tgtNode": "s0" } ],
  "fabric": { "routers": [ { "name": "r0", "clock": "core" } ],
    "links": [
      { "from": "m0", "to": "r0", "channels": ["req","rsp","dat"] },
      { "from": "r0", "to": "s0", "channels": ["req","rsp","dat"] } ] } }
```

**负例 N-C0-a**（CHK-01/02/03）：

```json
{ "schema": "loom/v3", "meta": { "name": "bad name!" },
  "clockDomains": [ { "id": "c", "freqMHz": 0 }, { "id": "c", "freqMHz": 100 } ] }
```

**负例 N-C0-b**（CHK-04/05/06/07/12/23）：

```json
{ "nodes": [
    { "id": "m0", "kind": "hn-f", "nodeId": 130, "clock": "ghost", "attach": "rX" },
    { "id": "m0", "kind": "rn-i", "nodeId": 130, "clock": "core", "attach": "r0" } ],
  "chi": { "nodeIdWidth": 7 } }
```

（无 sn-f/sn-i ⇒ CHK-23；kind=hn-f ⇒ CHK-05；130≥128 且重复 ⇒ CHK-06；ghost ⇒ CHK-07；rX ⇒ CHK-12；m0 重复 ⇒ CHK-04。）

**负例 N-C0-c**（CHK-08/09/10/11/13/14/15）：

```json
{ "chi": { "datWidth": 64, "txnIdWidth": 10, "nodeIdWidth": 9, "addrWidth": 64,
           "outstanding": 0, "atomicEntries": 0, "pcrdTypes": 17, "lcreditDepth": 16,
           "portMode": "tlul" },
  "nodes": [ { "id": "m0", "kind": "rn-i", "clock": "core", "attach": "r0",
               "protocol": "native", "dataWidth": 100, "outstanding": 300, "qos": 16 } ] }
```

**负例 N-C0-d**（CHK-16/17）：

```json
{ "sam": [ { "base": 2048, "size": 3000, "tgtNode": "s0" },
           { "base": 0, "size": 4096, "tgtNode": "m0" } ] }
```

（size 非 2 幂且 <4096、base 未对齐 ⇒ CHK-16；tgtNode 指向 rn-i ⇒ CHK-17。超容量负例：addrWidth=32 时 `base=4294963200, size=8192`。）

**负例 N-C0-e**（CHK-18/19/20）：

```json
{ "chi": { "dvmEnable": false, "datWidth": 256 },
  "nodes": [ { "id": "d0", "kind": "rn-d", "clock": "core", "attach": "r0" } ],
  "fabric": { "links": [
    { "from": "r0", "to": "r1", "channels": ["req","snp","snp"], "width": 128,
      "pipe": 9, "cdc": "async", "asyncFifoDepth": 1, "lcreditDepth": 0 } ] } }
```

（channels 重复/缺 rsp,dat ⇒ CHK-18；dvmEnable=false 含 snp 与 rn-d ⇒ CHK-19；width≠datWidth、pipe/cdc/fifo/credit 越域 ⇒ CHK-20。）

**负例 N-C0-f**（CHK-21/22）：

```json
{ "fabric": {
    "routers": [ { "name": "r0", "clock": "core", "in": 0,
                   "switch": { "kind": "flat", "banks": 1 }, "routing": "xy" } ],
    "timing": { "maxFanin": 65, "maxCombDepth": 0 } } }
```

（in 越域、switch 块出现（退役）、xy 无 pos ⇒ CHK-21；timing ⇒ CHK-22。）

### 6.4 C1 正/负例

**正例 P-C1**：P-C0 基础上，`sam` 覆盖全部 SN、link 端点均存在且 node 接入边落在 attach router、无重复边、sync 同域。多窗口正例：`sam=[{base:0,size:4096,tgtNode:"s0"},{base:65536,size:4096,tgtNode:"s0"}]`（同 SN 两窗口，不重叠——OQ-02 裁定后生效）。

**负例 N-C1-a**（CHK-24/25/26/27/31）：

```json
{ "nodes": [ { "id": "m0", "kind": "rn-i", "clock": "core", "attach": "r0" },
             { "id": "s0", "kind": "sn-f", "clock": "core", "attach": "r1" } ],
  "fabric": { "routers": [ { "name": "r0", "clock": "core", "in": 5 },
                           { "name": "r1", "clock": "core" },
                           { "name": "r2", "clock": "core" } ],
    "links": [ { "from": "r0", "to": "m0" },
               { "from": "r0", "to": "s0" }, { "from": "r0", "to": "s0" } ] } }
```

（RN 出现在 to ⇒ CHK-24；s0 接入边来自 r0 而 attach=r1 ⇒ CHK-24/26；m0 无出边 ⇒ CHK-26；重复边 ⇒ CHK-25；r2 无引用 ⇒ CHK-27；r0.in=5≠派生 ⇒ CHK-31。）

**负例 N-C1-b**（CHK-28/29/30）：

```json
{ "clockDomains": [ { "id": "a", "freqMHz": 500 }, { "id": "b", "freqMHz": 800 } ],
  "nodes": [ { "id": "s0", "kind": "sn-f", "clock": "a", "attach": "r0" },
             { "id": "s1", "kind": "sn-f", "clock": "b", "attach": "r1" } ],
  "sam": [ { "base": 0, "size": 8192, "tgtNode": "s0" },
           { "base": 4096, "size": 4096, "tgtNode": "s0" } ],
  "fabric": { "links": [ { "from": "r0", "to": "r1", "cdc": "sync" } ] } }
```

（sync 跨域 ⇒ CHK-28；窗口重叠 ⇒ CHK-29；s1 无窗口 ⇒ CHK-30。）

### 6.5 C2 正/负例

**正例 P-C2**：省略 qos 块（默认表）；或显式书写默认映射：

```json
{ "qos": { "mapping": [ { "qosMin": 8, "qosMax": 15, "class": "ctl" },
                        { "qosMin": 4, "qosMax": 7,  "class": "lat" },
                        { "qosMin": 0, "qosMax": 3,  "class": "bulk" } ],
           "classes": [ { "name": "ctl", "depth": 4, "weight": 4 },
                        { "name": "lat", "depth": 8, "weight": 2 },
                        { "name": "bulk", "vcs": 2, "depth": 16, "weight": 1 } ] } }
```

**负例 N-C2**（CHK-32/33/34）：

```json
{ "qos": { "mapping": [ { "qosMin": 9, "qosMax": 15, "class": "ctl" },
                        { "qosMin": 4, "qosMax": 8,  "class": "lat" } ],
           "classes": [ { "name": "ctl", "depth": 0 }, { "name": "ctl" },
                        { "name": "lat" } ] } }
```

（[0,3] 空洞、8 处重叠 ⇒ CHK-32；缺 bulk、ctl 重复、depth=0 ⇒ CHK-33；阈值 9/8≠实现 8/4 ⇒ CHK-34。）

### 6.6 G1 正/负例

**正例 P-G1**：附录 ring_4 v2（4 router 双向简单环 + 自动 dateline），`dvmEnable=true` 时 G1 四键全 PASS；同配置 `dvmEnable=false` 时 G1-SNP=true 且报告 `channels=["req","rsp","dat"]`（CHK-39 合法侧，S3 demoBoundary）。

**负例 N-G1-a**（CHK-35/38）：4 router 双向简单环加一条弦，SCC 内出现多后继顶点（非简单环）：

```json
{ "fabric": { "routers": [ { "name": "r0", "clock": "core" },
                           { "name": "r1", "clock": "core" },
                           { "name": "r2", "clock": "core" },
                           { "name": "r3", "clock": "core" } ],
    "links": [ { "from": "r0", "to": "r1" },
               { "from": "r1", "to": "r2" },
               { "from": "r2", "to": "r3" },
               { "from": "r3", "to": "r0" },
               { "from": "r0", "to": "r2" } ] } }
```

（环 + 弦 r0↔r2：SCC 内 r0 有多个后继（r1 与 r2）⇒ 非简单环，dateline 不可救 ⇒ CHK-38；若实现以 findCycle 首环报告则落 CHK-35。注意：两 router 互连双向对不构成负例——U-turn 排除后 CDG 无环。）

**负例 N-G1-b**（CHK-36）：`{ "from": "r0", "to": "r0" }`。

**负例 N-G1-c**（CHK-37）：链式拓扑 chain 上给中间边标 `"dateline": true`（非环成员）；或给 node 接入边标 dateline。

### 6.7 G4 正/负例

**正例 P-G4**：`chi.portMode="both"`，nodes 混合 `protocol:"axi4"` 与 `"chi"`。
**负例 N-G4**（CHK-40）：`chi.portMode="chi"` + node `"protocol":"axi4"`；或 v2 输入携带 `"protocol":"tlul"`。

### 6.8 G6 正/负例

**正例 P-G6**：`datWidth=256` ⇒ flitCount64B=2；`datWidth=128` ⇒ 4；`datWidth=512` ⇒ 1；`txnIdWidth=8, outstanding=64`。
**负例 N-G6**（CHK-41/42）：`chi={ "txnIdWidth": 8, "outstanding": 512 }`（512>256 ⇒ CHK-42）。CHK-41 是实现一致性门禁：负例由篡改 elaboration/报告（如 datWidth=256 而报告 flitCount=4）的单元测试注入构造，正常用户配置无法直接触发——测试必须以 `ChiWidths.flitCount(64, w)` 为金锚断言三档（S2 techGuidance 的 G6/G7 替代项）。

### 6.9 G8 正/负例

**正例 P-G8**：`classes[].arbitration="wrr"`（默认）或 `"rr"`。
**负例 N-G8**（CHK-43）：`{ "qos": { "classes": [ { "name": "ctl", "arbitration": "strict" }, ... ] } }`。

### 6.10 S1 正/负例

**正例 P-S1**：两分区 `{name:"p0",clock:"a",members:["m0","r0"]}`、`{name:"p1",clock:"b",members:["r1","s0"]}`，r0↔r1 之间 `cdc:"async"`，node 与 attach router 同分区。

**负例 N-S1**（CHK-44/45/46）：

```json
{ "partitions": [
    { "name": "p0", "clock": "a", "members": ["m0", "r0", "r1"] },
    { "name": "p0", "clock": "b", "members": ["s0"] } ] }
```

（p0 重复、r1 时钟 b≠分区 a、m0 与 attach 关系被拆散 ⇒ CHK-44/45；若在 a 域内放 `cdc:"async"` 边 ⇒ CHK-46。）

### 6.11 S2 正/负例

**正例 P-S2**：P-S1（boundaryPipe 默认 1，每分区含 router）。
**负例 N-S2**（CHK-47）：`{ "name": "p0", "clock": "a", "members": ["m0"], "boundaryPipe": 0 }`（无 router、pipe 越域）。

### 6.12 S3 正/负例

**正例 P-S3**：P-S1（切割边 r0→r1 为 router↔router）。
**负例 N-S3**（CHK-48）：node `s0` 划入 p1 而其 attach router r0 在 p0，切割边为 `r0→s0`（非 router↔router）。

## 7. 位宽与编码表（ENC）

**协议字段的语义、编码值与事务行为一律以 PS 为准**；本节只固定 IR 可选档位、派生量与交叉引用位置，供 GEN/报告/测试引用。

| ID | 项目 | IR 档位/派生公式 | 默认 | 协议交叉引用 |
|---|---|---|---|---|
| ENC-01 | DAT 位宽与 flit 数 | `chi.datWidth ∈ {128,256,512}`（**无 64b**，D5）；`flitCount64B = ceil(64/(datWidth/8))`：128→4、256→2、512→1；`beWidth = datWidth/8`：16/32/64 | 256 | PS §4（DAT flit）、§5 F1/F2；`ChiWidths.flitCount/beWidth` |
| ENC-02 | DataID | 2-bit，固定（不可配）；flit 在 64B 内的 16B chunk 位置 | 2b | PS §2（DataID）、§4 |
| ENC-03 | BE | 逐字节使能，位宽 = ENC-01 beWidth；=AXI strb 语义在桥接对齐 | 派生 | PS §2（BE）、§4 |
| ENC-04 | TxnID / DBID | `chi.txnIdWidth ∈ {8,12}`；ID 空间 `1<<txnIdWidth`（256/4096）；DBID 共用同档（SEM-11） | 8 | PS §2（TxnID/DBID）、D12 |
| ENC-05 | NodeID（SrcID/TgtID） | `chi.nodeIdWidth ∈ {7,11}`；maxNodes = 128/2048 | 7 | PS §2（Node ID）、§3 |
| ENC-06 | 地址 | `chi.addrWidth ∈ {32,40,48}`；SAM 窗口容量 `1<<addrWidth` | 48 | PS §4（REQ.Addr）、§9 |
| ENC-07 | RespErr | 2-bit 固定：00 OK / 01 EXOK / 10 DERR / 11 NDERR（值定义在 PS，本文不重复语义）；SAM 未命中→NDERR（SEM-05） | 2b | PS §2（RespErr）；`ChiRespErr` |
| ENC-08 | QoS 与实现 VC | QoS 4-bit [0,15]；VC = classIndex*nHalf+half（nHalf 拓扑派生，首版 2；位宽 log2Ceil(3×nHalf)，当前 3；实现私有，不上 flit） | — | PS §2（QoS/VC）、§5 F7；SEM-08 |
| ENC-09 | L-Credit 计数 | 深度 [1,15]（4-bit 计数上限），每 (link,通道类,方向) 独立 | 4 | PS §2（L-Credit）、§5 F6 |
| ENC-10 | Size / PCrdType | Size 3-bit（log2 字节数，≤6=64B，协议常量）；PCrdType 4-bit ⇒ pcrdTypes ≤16 | — | PS §2（Size）、§4（PCrdType）；`ChiSize` |

约束：ENC-01/04/05/06 的档位集合必须与 `ChiConfig` 的 require 集合逐字一致（CHK-13 的金锚）；任何档位扩充须先改 PS 与硬件，再以本文次版本跟进。

## 8. 兼容与退役（MIG）

### 8.1 v1→v2 升级器

**MIG-01 触发与等价性**。`schema=="loom/v1"` 时 `Upgrade.v1ToV2` 自动执行（S1）：输出必须是合法 v2 对象且通过 C0；升级必须满足**elaboration 等价**——对同一 v1 配置，升级前后 `ChiParams`（节点集合/NodeID/SAM 窗口）逐字段相等（IrSpec 升级等价性用例，S1 demoBoundary：v1/v2 `check` 均解析成功且 hash 稳定）。升级不修改输入文件，只作用于内存对象；`canonical` 输出升级后文本（GEN-15）。

**MIG-02 endpoint→node 映射**：

| v1 字段/条件 | v2 去向 |
|---|---|
| `role=master` | `kind=rn-i` |
| `role=slave` | `kind=sn-f` |
| `id/clock` | 原样保留 |
| `attach=mnetX/snetX` | 折算为该网络唯一相邻 router 的 `name`（v1 C1 保证 mnet 单出边/snet 单入边；多跳网络中 mnet/snet 直接连 router） |
| `protocol=native` | `protocol=chi`（native 即 CHI native 的前身；桥接语义按 D6 收敛） |
| `protocol=axi4` | `protocol=axi4` |
| `protocol=axilite/tlul` | **不可升级**：报错退出（MIG-06 退役协议，用户须手工改配置） |
| `dataWidth` | 原样（256 档新增合法） |
| `addrWidth` | 全局 `chi.addrWidth`（MIG-09） |
| `outstanding` | node.outstanding 原样；`chi.outstanding = max(64, max(all))`（保持 CURRENT `ChiConfig.fromIr` 行为） |
| `qos`（类名） | 数值：`ctl→8`、`lat→4`、`bulk→0`（落入默认映射对应分段，SEM-08） |
| `protocol` 集合 | `chi.portMode` 收敛（IR-43）：全 chi（含 native 折算）→`chi`，全 axi4→`axi4`，混合→`both` |
| slave `base/size` | `sam[]{base,size,tgtNode=slave id}`；全未声明则省略 sam 走派生（MIG-03） |

**MIG-03 base/size→sam**。全 slave 显式声明 ⇒ 逐条转 sam；全未声明 ⇒ v2 省略 `sam`（派生 4KB stride 与 v1 `AddressMap.windows` 逐位一致）；混合声明在 v1 已被 C0 拒绝，升级器无需处理。

**MIG-04 qos 升级**。v1 `qos.classes[]` 保留 name/vcs/depth/arbitration/weight，删除 `maxPacketBeats`；`qos.mapping` 生成默认表；v1 缺省 qos 块 ⇒ v2 亦省略（两侧默认一致）。v1 自定义 vcs/depth/weight 原样搬运并通过 CHK-33。

**MIG-05 mnet/snet 删除与拓扑改写**。`fabric.mnet[]/snet[]` 删除；每条 `mnetX→rK` link 改写为 `endpointId→rK`，`rK→snetY` 改写为 `rK→endpointId`（endpoint 即 attach 该网络的 node）；link 其余参数原样，`channels` 按默认生成。routers/links/timing/partitions 其余字段保留；partitions.members 中的 mnet/snet 名替换为对应 node id。

**MIG-06 退役清单**（v2 输入出现即 C0 报错；v1 输入按表处置）：

| 退役项 | 替代 | v1 升级处置 |
|---|---|---|
| `fabric.mnet[] / fabric.snet[]` | node.attach 直连 router | MIG-05 改写 |
| `endpoints[].protocol = native/axilite/tlul` | `axi4/chi`（D6） | native→chi；axilite/tlul 报错 |
| `qos.classes[].maxPacketBeats` | 协议常量 ≤64B + 桥接拆分（SEM-15） | 删除 |
| `axiIdWidth`（顶层 [1,8]） | `chi.txnIdWidth ∈ {8,12}` | MIG-08 |
| burst/beat 用户口径 | transaction/flit（PS §2） | 文档/报告口径替换 |
| `(src,id)` 响应匹配 | `(SrcID,TxnID)` 回显（PS §2） | 报告口径替换 |
| `rreq/wdata/rdata/wresp` 通道名 | REQ/RSP/DAT/SNP 通道类 | graph/estimate 报告键 additive 过渡（GEN-17） |
| 逐拍地址路由 | SAM + TgtID（SEM-04） | 无字段，语义替换 |
| 端到端 burst 信用（G6/G7 旧判据） | 三级流控（SEM-10–12）+ CHK-41/42 | 门禁替换，G7 键保留一版 |
| `endpoints[].addrWidth`（16/64 档） | `chi.addrWidth ∈ {32,40,48}` | MIG-09 |
| v1 link.width [8,1024] 任意 2 幂 | {128,256,512} 且 =datWidth（IR-56） | 不等于 datWidth 档的 v1 width：改写为 datWidth 并告警（OQ-05 评审确认） |
| `routers[].switch`（kind/banks） | CHI router 恒 flat + 每输出队列/寄存级（用户确认 2026-11-25） | 丢弃 + MIG-07 告警 |
| `links[].direction` | 恒双向（HW-07，SEM-13） | 丢弃 + MIG-07 告警；forward 配置升级后按双向处理 |

**MIG-07 deprecation warning 文案**（固定字符串，测试逐字断言）：

```text
WARN: LM-W-DEPRECATED: schema 'loom/v1' is deprecated and auto-upgraded to 'loom/v2'; MNet/SNet, burst/beat and native/axilite/tlul protocols are retired (see doc/ir_gen_spec.md MIG-06)
WARN: LM-W-DEPRECATED: command 'emit-chi' is deprecated; use 'emit' (alias removed next version)
WARN: LM-W-DEPRECATED: endpoint '<id>' addrWidth=<v> is not a CHI standard width; using chi.addrWidth=48
WARN: LM-W-DEPRECATED: link '<f>-><t>' width=<v> is not chi.datWidth=<w>; using <w>
WARN: LM-W-DEPRECATED: router '<r>' switch block is retired (CHI router is flat with registered outputs); discarded
WARN: LM-W-DEPRECATED: link '<f>-><t>' direction='<v>' is retired (CHI links are always bidirectional); discarded
```

**MIG-08 axiIdWidth→txnIdWidth**。v1 `axiIdWidth∈[1,8]` 一律升级为 `txnIdWidth=8`（CHI 最小档；v1 值 <8 时 ID 空间只增不减，语义安全）；v1 无 >8 值，12 档只能显式 v2 声明。

**MIG-09 addrWidth 折算**。取首 master `addrWidth`：∈{32,40,48} 原样（v1 无 40 档，实际 {32,48}）；16/64 折算 48 并输出 MIG-07 第三条告警。CURRENT `ChiConfig.fromIr` 静默折算——TARGET 必须补告警。各 endpoint addrWidth 不一致时取首 master 并告警（沿用 CURRENT 口径）；v2 中全局唯一，不再有歧义。

### 8.2 JSON 与旧资产策略

**MIG-10 additive-only**（承接 GEN-16）：S5 版本起一个版本内 API/报告旧键只增不删；下一主版本删除时须在 MIG-06 表登记删除项与替代键。GUI 不改即可继续工作（旧键口径可推导，新键渐进采纳）。

**MIG-11 旧 `loom/rtl` 隔离策略（先隔离后删除）**：S4 删除 GEN 对 `FabricParams/NetworkParams/LmTop/LmNetworkTop/LmWrapperTop` 的调用；S6 裁定文件去留——推荐把旧四通道 RTL 及其测试迁入 `legacy` source set（默认不编译、不进 CI 矩阵），保留一个版本供比对，随后删除。禁止一次性删测试造成回归盲区（S6 techGuidance）。`blocks` 命令与 `link_spec.md`（标废弃、指向 PS）随 S6 处置。
> **S6 裁定落地（2026-11-25）**：旧四通道 RTL（`LmRtl/LmNetwork/FabricParams/NetworkParams/LmPartition/LmShell/LmWidth/LmAddrDecode`）与 17 个 `Lm*Spec` + `EmitSpec` + v1 路由断言（`GraphV1Spec`）、v1 `portsJson`（`ReportsV1`）、`area_calibration.sh` 已隔离至 `src/loom/legacy/`（见其 README，含临时重启用步骤）。`loom.chi` 复用的原语（`LmWire/LmPipe/LmRrArbiterN/LmWrrArbiter` → `rtl/LmPrimitives.scala`；`LmAsyncLink` 等 → `rtl/LmCdc.scala`）留在编译路径。`blocks` 命令已删除；`link_spec.md` 已标废弃指向 PS/本文。v1 示例副本在 `src/loom/testdata/v1/`（MIG-12）。

**MIG-12 示例与 CI**。minimal/ring/mesh/chain/tree 示例 S6 重写为 v2；v1 副本保留于测试资源目录专供升级器等价性用例（MIG-01），不再作为用户示例。CI 门禁矩阵与 cg2/cg6 脚本切 v2 输入；`testOnly loom.*` 全量绿为 S6 验收。

**MIG-13 hash/缓存失效**。GEN-06 口径切换 + ToolVersion 2.0.0 使全部 v1 时代 manifest 缓存失配（预期行为，一次性重发射）；diff 的目录模式比较报告 Canonical 渲染，跨版本目录比较必须先各自重 emit。

## 9. 现状代码与目标差异（待裁定，OQ）

以下为**当前源码事实与本规格目标/计划文本的冲突或空白**。S0 评审必须逐条裁定；裁定前相关 TARGET 条目不得视为已冻结，实现不得擅自取边。

| ID | 冲突/空白 | 现状证据 | 影响条目 | 待裁定问题 |
|---|---|---|---|---|
| OQ-01 | hn-i/mn 节点无 elaboration 路径：`ChiParams.fromIr` 只生成 RnI/SnF；`ChiNetworkParams` 本地口只挂 RN/SN；`ChiDvmMn` 存在但未接入网络顶层 | chi/ChiParams.scala、chi/ChiNetwork.scala、chi/ChiDvm.scala | SEM-01/02、IR-14、GEN-17 | hn-i/mn 的 NodeID 编号段、本地口挂接与网络级实例化由谁承担？不改 loom/chi 硬件的前提下 v2 是否首版仅接受 rn-i/rn-d/sn-f/sn-i（hn-i/mn 解析合法但 elaboration 拒绝）？ |
| OQ-02 | SAM 多重性：计划口径 `sam[]` 支持任意窗口表，但 `ChiParams` require `sam.length == snNodes.length`（每 SN 恰一窗口） | chi/ChiParams.scala L30 | IR-22–24、CHK-30、§6.4 P-C1 | v2 首版是否维持 1:1（多窗口报 CHK-30 变体错误）？放开多窗口需要动 ChiParams require——是否属于"GEN 参数装配"而非硬件改动？ |
| OQ-03 | CDG 顶点口径未兑现：PS §11 与计划宣称顶点 `(link,通道类,方向,vc)`，但 `FabricGraph.fromIr` 顶点=有向 link，`direction=bidirectional` 不展开反向顶点，`ChiGates.run` 将同一 g1ok 复制到四类 | graph/FabricGraph.scala L94–99、chi/ChiGates.scala L15–21 | GEN-10/11、SEM-13、CHK-35–39 | S3 的"只改顶点标签与通道过滤"是否包含双向展开与 per-class 依赖边（算法输入变化）？§6.6 N-G1-a 的期望错误码以裁定结果为准 |
| OQ-04 | 单 router CHI 数据通路缺失：`ChiTop` 仍是 S2 端口骨架（通道 tie-off），而 CURRENT emit-chi 单 router 走 ChiTop | chi/ChiTop.scala、LoomMain.runEmitChi | GEN-01 阶段8、GEN-02 | v2 emit 单 router 是否统一走 `ChiNetworkTop`（ChiNetworkParams 支持 ≥1 router）从而绕开骨架？ChiTop 是否随 MIG-11 隔离？ |
| OQ-05 | fabric 内部参数不被 CHI 硬件消费：`ChiNetworkParams` 忽略 link.width/cdc/direction 与 router.in/out/switch.banks；`ChiRouter4` 队列 depth=2 固定，banked 无 CHI 实现；v1 link.width 域与 datWidth 档不一致的升级改写规则未定 | chi/ChiNetwork.scala L96–98、chi/ChiNetworkTop.scala L19 | IR-48–51、IR-56、CHK-20/31、MIG-06 末行 | 这些字段 v2 首版是"校验+报告口径"（发射不消费）还是收敛/退役？banked/async 在 CHI 路径的支持边界须明确写入 PS 或本文次版本 |
| OQ-06 | QoS 阈值硬编码：`ChiQosMap.classOf` 固定 8/4 分段，IR 映射表可配性与硬件冲突 | chi/ChiNetwork.scala L10–19 | IR-25–28、CHK-34 | 首版以 CHK-34 锁定默认表（本文现行写法）是否成立？自定义阈值是否推迟到允许改 ChiQosMap 的后续任务？ |
| OQ-07 | hash/canonical 口径切换：CURRENT hash 与 API canonical 均基于原始 root；TARGET 基于升级+默认补齐的规范化 v2（GEN-06），v1 基线 hash 全部变化 | LoomMain.validate L89、LoomApi.evaluate | GEN-06、GEN-15、MIG-13 | 确认接受一次性基线失效；或改为"hash(升级后未补默认的 v2)"折中（默认值变更靠 ToolVersion 兜底）？ |
| OQ-08 | CHI 分区发射路径缺失：v1 有 LmWrapperTop/LmPartitionTop/LmPartitionStub，loom.chi 无对应分区顶层；计划保留 partitions/clockDomains 但未给 CHI 分区发射方案 | LoomMain.runEmit L206–224、chi/ 目录清单 | SEM-16、GEN-01 阶段8、GEN-17 ports.json | S4 是否只交付"分区门禁+ports 报告"、发射遇 partitions 非空时报错指引？还是用 ChiNetworkTop 按分区裁剪发射（需新 GEN 装配逻辑）？ |
| OQ-09 | SAM 未命中行为：`ChiSamDecode` 骨架译码到 NodeID 0，NDERR 响应未实现；SEM-05 目标为 NI 返回 NDERR | chi/ChiParams.scala L60–75 | SEM-05、ENC-07 | NDERR 属协议引擎（loom/chi 硬件）还是 GEN 可装配的 NI 包装？首版是否把 SEM-05 标注为"协议层预留，校验保证配置不产生未命中"？ |

### 9.1 裁定记录（2026-11-25，S0 评审第一轮）

用户裁定：
1. **需修正硬件的项统一收录** `doc/opt/2026-11-25_loom_chi_hw_dependency_list.md`（HW-01–07 / HQ-01），本任务作者**不做 RTL 修正**；GEN 侧按该清单"临时行为"列执行。映射：OQ-01→HW-01、OQ-02→HW-02、OQ-04→HW-03、OQ-05(cdc)→HW-04、OQ-09→HW-05、OQ-08→HW-06、OQ-05(余项)→HW-07。
2. **CDC 保留**：IR-57/59、SEM-14、CHK-28/46 维持 TARGET 生效；CHI 路径的 async link 发射在 HW-04 闭合前报明确错误（校验与报告口径先行）。
3. **ChiQosMap 8/4 硬编码**入硬件疑问清单 HQ-01（8/4 是实现选择而非 CHI 协议要求，CHI 标准不定义 QoS 类分段）；GEN 首版按 CHK-34 锁定默认表。

待确认（已答复用途，裁定未落）：
- **banks/switch 退役（用户确认 2026-11-25）**：IR-50/51 已标退役，升级器丢弃+固定警告（MIG-06/07）。
- **direction 退役（HW-07）**：IR-60 已标退役，SEM-13 改恒双向；升级器丢弃+固定警告。
- **分区发射**（OQ-08）：用途=按物理/时钟边界切分 fabric、各分区独立顶层+边界登记端口+Wrapper 回接+Stub 占位（多时钟/电源域、分区独立综合、3D die 边界）。建议 GEN 交付门禁+ports 报告，CHI 分区顶层列 HW-06，闭合前 partitions 非空发射报错。

GEN 侧自理项（无硬件依赖，按建议默认执行，评审可推翻）：OQ-03 双向展开+per-class 依赖边为 S3 核心工作量；OQ-07 接受 hash 基线一次性失效。

### 9.2 AQ 对齐答复（2026-11-25，用户已确认；详见硬件依赖清单 §5）

- **AQ-01**：① 用现有 `nodes[].kind`（IR-14），不新增字段；② NodeID 段序 **RN→SN→HN→MN**，段内声明序，显式 nodeId 优先；③ SAM 仅 SN（IR-24/CHK-17 不变）；**存在 rn-d 时须恰好 1 个 mn，mn 恒 ≤1**（CHK-19 变体；dvmEnable=true 本身不强制 mn，否则无 DVM 用户的默认配置全非法），MN NodeID 由 GEN 派生为 elaboration 参数传 RN-D/MN，不走 SAM。
- **AQ-02**：① CDG 按 D11 条件展开——dateline 环上 link 展全 VC 顶点，非环 link 只展 half0；snp 仅 dvmEnable 纳入；② dateline 半区由 **GEN 拓扑派生**（FabricGraph SCC/autoDatelines），cut 标志按 (link,方向,通道类) 传硬件，硬件只在标记出口翻 0→1；③ 权重/阈值单一真源 = IR qos 块，GEN 装配传 ChiConfig；首版 CHK-34 锁默认表（8/4、4:2:1），硬件 cfg 消费落地后次版本放开。
- **VC 编号泛化（用户裁定）**：`vc = classIndex*nHalf + half`，nHalf 由拓扑路由规则派生（首版环 dateline=2），**不得写死 ×2**——未来 mesh 特殊路由可能需更多道数；vcWidth 随之派生。SEM-08/ENC-08 已按此改写；硬件 ChiQosMap 须参数化 nHalf/vcWidth。

## 10. 验收与追溯

### 10.1 Story → 条目覆盖矩阵（S0 验收：无编号即未覆盖）

| Story | 必须引用的条目 |
|---|---|
| S1 schema/解析/升级器 | IR-01–IR-69、SEM-02/03/06、GEN-03/04/06、MIG-01–MIG-09 |
| S2 C0/C1/C2 面向 CHI | CHK-01–CHK-34、CHK-40–CHK-43、ENC-01/04/05/06、GEN-13 |
| S3 图与 G1 | SEM-07/08/09/13、GEN-10/11、CHK-35–CHK-39、OQ-03 裁定 |
| S4 GEN 单路径 + CLI | GEN-01/02/05/07/08/09/12/19/20、MIG-11/13、OQ-01/04/05/08 裁定 |
| S5 报告/API 口径 | GEN-14–GEN-18、MIG-10、SEM-05（OQ-09 裁定） |
| S6 示例/CI/退役 | MIG-06/07/11/12、CHK 全表负例、CG2（GEN-07） |

### 10.2 可校验性约定

- 每条 IR-xx：schema 正/负例解析测试（类型/域/默认/必填四断言）。
- 每条 SEM-xx：elaboration 或图级断言（如 SEM-02 的 NodeID 序列快照、SEM-07 的 channels 清单）。
- 每条 GEN-xx：CLI/API/字节级测试（hash 稳定性、Canonical 快照、退出码、additive 键存在性）。
- 每条 CHK-xx：§6 对应正/负例片段各一，错误消息含编号逐字匹配。
- 每条 ENC-xx：与 `ChiConfig`/`ChiWidths` require/派生函数的金锚对拍。
- 每条 MIG-xx：升级等价性用例（MIG-01）或告警文案逐字断言（MIG-07）。

## 附录 A：完整 v2 示例（ring_4，S1/S3/S4 基准配置）

由 `examples/ring_4.json`（v1）按 MIG-02–05 升级并显式化 defaults 的等价 v2 书写（`dvmEnable=false` 展示 SNP 排除路径；置 true 即得四通道类版本）。v1 的 forward 环按 HW-07 升级为双向环（direction/switch 丢弃+告警）；MIG-01 等价性口径为 ChiParams 节点/NodeID/SAM，不含链路方向。

```json
{
  "schema": "loom/v2",
  "meta": { "name": "ring_4" },
  "clockDomains": [ { "id": "core", "freqMHz": 1000 } ],
  "chi": {
    "datWidth": 256, "txnIdWidth": 8, "nodeIdWidth": 7, "addrWidth": 32,
    "outstanding": 64, "atomicEnable": true, "atomicEntries": 4,
    "dvmEnable": false, "portMode": "chi", "pcrdTypes": 1, "lcreditDepth": 4
  },
  "nodes": [
    { "id": "m0", "kind": "rn-i", "clock": "core", "attach": "r0",
      "protocol": "chi", "dataWidth": 128, "outstanding": 4, "qos": 0 },
    { "id": "m1", "kind": "rn-i", "clock": "core", "attach": "r1",
      "protocol": "chi", "dataWidth": 128, "outstanding": 4, "qos": 4 },
    { "id": "s0", "kind": "sn-f", "clock": "core", "attach": "r2",
      "protocol": "chi", "dataWidth": 128 },
    { "id": "s1", "kind": "sn-f", "clock": "core", "attach": "r3",
      "protocol": "chi", "dataWidth": 128 }
  ],
  "sam": [
    { "base": 0,    "size": 4096, "tgtNode": "s0" },
    { "base": 4096, "size": 4096, "tgtNode": "s1" }
  ],
  "qos": {
    "mapping": [
      { "qosMin": 8, "qosMax": 15, "class": "ctl" },
      { "qosMin": 4, "qosMax": 7,  "class": "lat" },
      { "qosMin": 0, "qosMax": 3,  "class": "bulk" }
    ],
    "classes": [
      { "name": "ctl",  "vcs": 1, "depth": 4,  "arbitration": "wrr", "weight": 4 },
      { "name": "lat",  "vcs": 1, "depth": 8,  "arbitration": "wrr", "weight": 2 },
      { "name": "bulk", "vcs": 2, "depth": 16, "arbitration": "wrr", "weight": 1 }
    ]
  },
  "fabric": {
    "routers": [
      { "name": "r0", "clock": "core" },
      { "name": "r1", "clock": "core" },
      { "name": "r2", "clock": "core" },
      { "name": "r3", "clock": "core" }
    ],
    "links": [
      { "from": "m0", "to": "r0", "channels": ["req","rsp","dat"] },
      { "from": "m1", "to": "r1", "channels": ["req","rsp","dat"] },
      { "from": "r2", "to": "s0", "channels": ["req","rsp","dat"] },
      { "from": "r3", "to": "s1", "channels": ["req","rsp","dat"] },
      { "from": "r0", "to": "r1", "channels": ["req","rsp","dat"] },
      { "from": "r1", "to": "r2", "channels": ["req","rsp","dat"] },
      { "from": "r2", "to": "r3", "channels": ["req","rsp","dat"] },
      { "from": "r3", "to": "r0", "channels": ["req","rsp","dat"],
        "dateline": true }
    ],
    "timing": { "maxFanin": 8, "maxCombDepth": 12 }
  }
}
```

预期：C0/C1/C2/G4/G6/G8 零错误；G1-REQ/RSP/DAT PASS（r3→r0 显式 dateline 切割简单环），G1-SNP=true（未生成，报告 `channels=["req","rsp","dat"]`）；NodeID 分配 m0=0、m1=1、s0=2、s1=3（SEM-02）；SAM tgtId s0→2、s1→3；flitCount64B=2（ENC-01）。

## 附录 B：引用文件清单

| 文件 | 角色 |
|---|---|
| `src/loom/doc/protocol_spec.md` | CHI 协议单一权威（PS），本文全部协议语义交叉引用目标 |
| `src/loom/doc/opt/2026-11-25_loom_ir_gen_chi_adaptation_plan.md` | S0–S6 计划（本文上游） |
| `src/loom/doc/opt/2026-11-25_loom_chi_migration_plan.md` | D1–D12 裁定记录（RTL 层迁移，已完成） |
| `src/loom/scala/main/loom/ir/{Ir,Json,Canonical,LoomDefaults,AddressMap}.scala` | CURRENT v1 schema/解析/hash/默认/窗口数学 |
| `src/loom/scala/main/loom/check/{Checks,Gates}.scala` | CURRENT C0/C1/C2/G/S 门禁 |
| `src/loom/scala/main/loom/graph/FabricGraph.scala` | CURRENT CDG/SCC/dateline |
| `src/loom/scala/main/loom/chi/{ChiConfig,ChiParams,ChiNetwork,ChiGates,ChiDefs}.scala` | CURRENT CHI elaboration/门禁/编码（TARGET 唯一路径的内部模型） |
| `src/loom/scala/main/loom/{LoomMain,LoomApi}.scala` | CURRENT CLI/API/缓存/manifest |
| `src/loom/scala/main/loom/report/Reports.scala` | CURRENT 报告口径 |
| `src/workerbee/doc/spec.md` | 文档结构/严谨度范本（版本规则、ID 规则、表格式规范） |