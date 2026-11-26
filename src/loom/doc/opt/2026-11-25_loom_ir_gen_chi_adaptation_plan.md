# Loom IR / 网络生成逻辑 → CHI 适配计划（2026-11-25）

> 状态：**S0–S6 全部落地**（2026-11-25，最终 22 suite/230 test 绿 + ci_matrix OK + formal 10/10；旧 rtl 隔离 legacy/ 待一版后删除）| 前置：`2026-11-25_loom_chi_migration_plan.md` S1–S12 已落地（RTL 生成层 CHI 化）
>
> 范围裁定（用户 2026-11-25）：
> 1. **只做 IR 与网络 GEN 逻辑**（`loom/ir`、`loom/check`、`loom/graph`、`loom/report`、`LoomMain`/`LoomApi` 的生成与校验路径）。
> 2. **不改底层 RTL**（`loom/rtl`、`loom/chi` 的硬件实现保持现状，只被 GEN 调用）。
> 3. **不改网页 GUI**（`loom/gui` 不动；API JSON 口径变化由 S5 单独承担，GUI 适配另起任务）。
> 4. 旧内部协议在 IR/GEN 层**退役**：MNet/SNet、burst/beat、(src,id)、rreq/wdata/rdata/wresp、逐拍地址路由不再作为用户口径。

## 1. 目标

把用户侧配置与生成管线从 `loom/v1`（四通道运输 + AXI 语义）切换到 CHI 原生口径：节点角色 + NodeID、SAM、REQ/RSP/DAT/SNP 通道类、QoS(4b)、三级流控参数、链路层参数。拓扑方法论（IR→门禁→图→发射→形式化）保留；`loom.chi` 已有 `ChiConfig/ChiParams/ChiNetworkParams/ChiGates` 作为 GEN 的内部模型，本计划把它们提升为**唯一** elaboration 路径，并让 IR/校验/报告/CLI 与之对齐。

## 2. 名词与字段对齐（IR v2）

| v1 | v2（CHI） |
|---|---|
| endpoints.master/slave | nodes：`rn-i` / `rn-d` / `sn-f` / `sn-i` / `hn-i` / `mn`（`hn-f` 仅占位） |
| attach + mnet/snet | node 自带 port（NodeID 由 GEN 确定性分配，IR 可显式指定） |
| slave base/size | `sam[]`：base/size/tgtNode（NI 一次译码） |
| qos ctl/lat/bulk + vcs | `qos`：4b 优先级 + 实现私有 VC 映射表 |
| link.width/pipe/cdc/direction/dateline | 保留为 fabric 内部参数；新增 `channels[]`（req/rsp/dat/snp）与 `lcreditDepth` |
| （无） | `chi` 配置块：datWidth(128/256/512)、txnIdWidth(8/12)、nodeIdWidth(7/11)、addrWidth(32/40/48)、outstanding、atomicEnable/atomicEntries、dvmEnable、portMode(axi4/chi/both)、pcrdTypes、lcreditDepth |
| partitions/clockDomains | 保留 |

退役字段：`mnets/snets`、`endpoints.protocol=native/axilite/tlul`（桥接只留 axi4/chi）、`qos.classes[].maxPacketBeats`（CHI 单笔 ≤64B，由桥接拆分）、`axiIdWidth`（被 txnIdWidth 取代）。

## 3. Story 波次（blocker 序，每个 fits-one-run）

### S0 IR/GEN 详细规格书（评审门禁）
- tasks：写 `src/loom/doc/ir_gen_spec.md`，作为 S1–S6 的唯一契约来源，内容必须逐项可校验：
  1. **IR v2 schema 全字段表**：每个字段的类型、取值域、默认值、是否必填、所属层级（meta/clocks/nodes/sam/qos/chi/links/partitions），并给出与 `loom/v1` 的逐字段映射与退役清单。
  2. **语义定义**：NodeID 分配与显式指定规则、SAM 译码语义（NI 一次译码、未命中行为）、通道类集合与 SNP 条件、QoS(4b)→实现 VC 映射、三级流控参数（P-Credit/DBID/L-Credit）在 IR 中的表达与默认。
  3. **GEN 管线契约**：parse→upgrade→C0/C1/C2→graph/G1→elaboration(ChiParams/ChiNetworkParams)→emit→reports 的输入/输出类型、确定性排序规则、Canonical/hash 规则、缓存键组成、CLI 命令与退出码、API JSON 字段表（additive 策略）。
  4. **校验规则表**：每条 C/G 门禁的判定条件、错误码与消息模板、正/负例配置片段。
  5. **位宽与编码表**：DAT 128/256/512、DataID、BE、TxnID 8/12、NodeID 7/11、addr 32/40/48、RespErr 2b，与 `protocol_spec.md` 的交叉引用（不重复定义协议语义）。
  6. **兼容与退役**：v1 升级器的字段映射、deprecation warning 文案、旧 `loom/rtl` 隔离策略、GUI 不破的 JSON 策略。
- blockedBy：[]
- demoBoundary：规格书评审通过；S1 的 schema 与 S2 的门禁能逐条追溯到规格条目。
- techGuidance：协议语义以 `protocol_spec.md` 为准，本 spec 只定义"用户配置与生成管线"，不重写 CHI 协议；每个字段/规则带编号（IR-xx / GEN-xx / CHK-xx）供后续 story 引用。

### S1 IR v2 schema + 解析 + v1 升级器 ✅（2026-11-25 落地，IrSpec 34/34、全量 38 suite/172 test 绿）
- tasks：定义 `IrV2` case class 与 `loom/v2` schema；Json/Canonical 支持；`LoomDefaults` 增 CHI 默认；写 `Upgrade.v1ToV2`（master→rn-i、slave→sn-f、base/size→sam、qos 三类→4b 优先级映射、mnet/snet 删除）；v1 输入自动升级并给 deprecation warning；IrSpec 增 v2 正/负例与升级等价性用例。
- blockedBy：[S0]
- demoBoundary：`check --config v1示例` 与 `check --config v2示例` 都解析成功且 hash 稳定。
- techGuidance：NodeID 分配规则沿用 `ChiParams.fromIr`（RN 先、SN 后）；IR 显式 nodeId 优先且唯一性在 S2 校验。

### S2 校验面向 CHI 口径（C0/C1/C2）✅（2026-11-25 落地，CheckV2Spec 69 + IrSpec 35、全量 39 suite/242 test 绿；CHK-40 冲突按 IR-43 portMode 收敛修复，CHK-19 补 mn 变体）
- tasks：C0 按 `ChiConfig` require 校验配置块与位宽档；node 角色/NodeID 范围与唯一；SAM 对齐/2 幂/不重叠/容量；`channels` 合法且 SNP 仅当 dvmEnable；portMode 与桥接能力匹配；C1 图结构（link 端点必须是 node/port、重复 link、clock 交叉）；C2 QoS→VC 映射表完整；负向用例齐备。
- blockedBy：[S1]
- demoBoundary：非法 SAM/NodeID/通道组合被精确报错，合法 v2 配置零错误。
- techGuidance：复用 `AddressMap` 做窗口数学；旧 G6/G7（burst 拍数）删除，替换为"DAT flit 数 = ceil(64B/datWidth)"一致性检查。

### S3 图与 G1 按生成通道类证明 ✅（2026-11-25 落地，GraphSpec v2 11 例、全量 39 suite/253 test 绿；顶点四元组+双向双环双 cut+D11 条件 VC 展开）
- tasks：`FabricGraph` 顶点改 `(link, 通道类, 方向, vc)`；通道类集合由配置决定（D11：无 DVM 则无 SNP）；dateline/简单环判定保留；`ChiGates` 成为 G1 唯一入口；GraphSpec 迁移到 v2 并补"SNP 条件纳入/排除"用例。
- blockedBy：[S2]
- demoBoundary：ring/mesh v2 配置 G1 通过；关掉 dvmEnable 后 SNP 不参与证明且报告可见。
- techGuidance：不要重写 CDG 算法，只改顶点标签与通道过滤；旧四通道顶点定义删除。

### S4 GEN 单路径化 + CLI 收口 ✅（2026-11-25 落地，EmitV2Spec 8 + 分区门禁 9、全量 40 suite/270 test 绿；hn/mn 已装配（MN 接 ChiDvmMn），分区+DVM 守护待 HW-08；ToolVersion 2.0.0）
- tasks：`LoomMain` 的 check/emit/diff/formal/estimate 全部走 `ChiParams/ChiNetworkParams`；`emit-chi` 合并进 `emit`（保留别名一个版本）；删除 `LmTop/LmNetworkTop/LmWrapperTop` 发射分支与 `FabricParams/NetworkParams` 的 GEN 调用（文件保留给旧 RTL 测试与否在 S6 裁定）；configHash 纳入 chi 配置块；manifest/缓存键更新。
- blockedBy：[S3]
- demoBoundary：`emit` 对 v2 ring 配置产出 ChiNetworkTop 且 CG2 逐文件确定性绿。
- techGuidance：不改 `loom/chi` 硬件；只改调用与参数装配。旧 `loom/rtl` 若仍被测试引用，S6 决定退役或隔离。

### S5 报告 / API JSON 口径 ✅（2026-11-25 落地，ReportSpec+8/LoomApiSpec+7/TbGenSpec+4、全量 40 suite/290 test 绿；GUI 零改动 additive-only，addressMap=SAM，TbGen CHI 骨架）
- tasks：`Reports` 的 estimate/ports/manifest/addressMap 改 CHI 口径（节点表、SAM、flit 位宽、三级流控参数、通道类清单）；`LoomApi` evaluate 返回 v2 字段；TbGen 生成 CHI 激励骨架；ReportSpec/LoomApiSpec/TbGenSpec 迁移。
- blockedBy：[S4]
- demoBoundary：`evaluate` 返回的 addressMap 即 SAM，estimate 含 datWidth/lcreditDepth，GUI 不改也能读到新 JSON（字段 additive）。
- techGuidance：GUI 不动；JSON 只增不删旧键一个版本，避免网页 immediate break。

### S6 示例 / CI / 旧资产退役 + 全量回归 ✅（2026-11-25 落地，示例 v2 化+v1 副本 testdata/v1、旧 rtl+18 suite 隔离 legacy/、blocks 退役、CI 切 v2；最终 22 suite/230 test 绿 + ci_matrix OK + formal 10/10）
- tasks：minimal/ring/mesh/chain/tree 示例重写为 v2（保留 v1 副本供升级器测试）；CI 矩阵与 cg2/cg6 脚本切 v2；裁定 `loom/rtl` 旧四通道 RTL 与其测试的退役或隔离（建议隔离到 `legacy` source set，默认不编译）；全量 `testOnly loom.*` 绿；implementation_plan/architecture 同步，link_spec.md 标废弃指向 protocol_spec.md。
- blockedBy：[S5]
- demoBoundary：CI matrix OK；全量回归绿；文档无旧四通道用户口径残留。
- techGuidance：退役走"先隔离后删除"，避免一次性删测试造成回归盲区。

## 4. 验收

- S0 规格书是契约来源：S1–S6 的字段、门禁、报告字段必须引用 `ir_gen_spec.md` 的编号条目（IR-xx/GEN-xx/CHK-xx），无编号即视为未覆盖。
- 每个 story 自带仿真/解析边界；S6 全量绿 + CG2 确定性。
- 金锚由 CHI 事务语义推导（沿用 protocol_spec），不以 RTL 行为拟合。
- 用户口径不再出现 MNet/SNet、burst 信用、(src,id)。

## 5. 不在本次

- GUI CHI 编辑界面（另起任务）。
- `loom/chi` 硬件功能扩展（一致性语义、OWO、CMO 等仍按 D4/D8/D9 预留）。
- PPA/深度校准（D10 留待 PPA 阶段）。