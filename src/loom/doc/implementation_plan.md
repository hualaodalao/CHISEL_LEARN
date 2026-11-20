# Loom 实施计划 v1.0

> 依据：[architecture.md](architecture.md) v0.1 | 更新：2026-11-19 | 目标：40 周交付 v1.0
> 估算口径：人月（PM），含设计、编码、单测与文档，不含跨团队评审等待。
> 当前状态：**S1–S20 全部完成；M0/M1/M2/M3 已过门；v1.0.0 已发布**。v1.1 已实施完成（2026-11-20，见 [v1.1_plan.md](v1.1_plan.md) §7）：async 分区 + 多时钟 wrapper、AXI4 ID、SRAM block/面积拆分、G9 模型；回归 22 suite / **92 用例**全绿；`loom 1.1.0`。

## 0. 一页结论

- **v1.0 基线（S20 后）**：JSON IR + canonical hash + C0/C1/C2/G1/G4/G6/G7/G8/S1–S3；单/多 Router fabric（VC 队列、两级类权重、端到端信用）；CDC FIFO/信用/AsyncLink/宽度适配；Pipe 0–4 级；sync/async RouterLink；四类协议壳；分区切分、边界寄存、`ports.json` 与分区 hash；**wrapper 顶层 + 分区 stub（S4）**；P9 单分区 vs 两分区事务 trace 等价；`check/estimate/emit/formal/diff/serve/blocks`；**TB 骨架 + 断言清单 + signoff + manifest（含缓存判定）**；**零构建 GUI + `/api/evaluate`（表单/拓扑/门禁/估算，只读写 IR）**；**CI 矩阵（CG0/CG2/CG5/CG6 + 夜间 CG4）与 GitHub Actions**；**banked crossbar（输出分组 + 出口寄存，默认 flat 不变）**；**XY mesh 模板（坐标 + 转向模型 + XY 路由表）与树模板**；**P1–P3/P5–P8 有界证明（4 个 token 模型）**；**nangate45 面积校准与 area proxy/敏感项**；**perf 计数/粘滞错误、Verilator lint 门禁、签核清单**；**85 个用例全绿**；M2 评审通过；**`loom 1.0.0` 发布（见 release_notes_v1.0.md）**。
- **状态**：v1.0 已发布；已知限制与发布后项见 [release_notes_v1.0.md](release_notes_v1.0.md)。
- **工作量**：剩余 **0 PM**；累计约 26.2 PM（原 27 PM 基线内）。
- **形式化后端结论**：sby 0.71 + yosys 0.71 + z3 已安装，但 sby/z3 在 8×8 token 深度 40 上 10 分钟内不收敛；S8 门禁改用 yosys-sat 有界证明（8×8 安全 depth 12、8 路 RR 进度 depth 20），深证明/IC3 留 M2。
- **默认不做**：banked 自动分组、mesh 自适应路由、多播、一致性、UVM、物理设计与 STA。全部后置或显式开启。

---

## 1. 范围与 MVP 定义

**MVP（M1 出口，S8）**：配置 IR → RTL + TB + 断言 + formal harness + 报告；支持 flat 与任意**无环 Router 图**（多 Router、MNet/SNet、sync/async RouterLink、Pipe 0–4 级）；三类 QoS + VC 队列；端到端信用预留；CDG/G1；G1–G4/G5–G8 Safe 门（G9 随 S17 边界形式化）；P1–P3/P5 有界收敛（P4 待 `ordered` 语义）。

**v1.0（M3 出口，S20）**：在 MVP 上增加分区/多 top、协议壳（native 4 通道 / AXI4 / AxiLite4 / TL-UL 子集）、可选 banked、树与 XY mesh 模板、P6–P9、面积/延迟估算校准与签核模板。

**本计划内明确不做**：

- 缓存一致性（snoop/目录/MESI）。
- 物理设计、STA 签核、DFT。
- 完整 UVM 环境；生成 TB 以 Chisel/ScalaTest + ChiselSim 为主，附 SVA 供外部仿真。
- 任意自适应路由。
- 多播默认关闭，仅在 M3 以实验特性出现。

---

## 2. 假设、团队与工具

| 项 | 假设 | 不成立时的后果 |
|---|---|---|
| 团队 | 2 HW + 1 DV/工具，稳定投入；S13–S14 加 0.5 前端 | 2 人则 M1 后顺延约 8 周；1 人不可行 |
| 工具 | Chisel 7.13、Verilator、sbt、yosys 0.71、sby 0.71、z3 | 已安装；sby/z3 通过 Homebrew 安装 |
| 形式化 | yosys-sat 有界证明覆盖 P1–P3 + 路由安全与 P5（P5 允许先有界）；P4/P6–P9 与 sby/z3 深证明待后续 | 8×8 安全 depth 12、RR 进度 depth 20 均通过；更深的 k-归纳需 IC3/更强求解器 |
| 前端 | 静态页面 + JSON Schema 渲染，零构建 | 若引入框架，M2 +2 周 |
| 评审 | IR/协议语义在 M0 已冻结；后续变更按第 9 节处理 | 语义变更需 ADR 与全矩阵重跑 |

**形式化工具状态（S8）**：sby 0.71 + yosys 0.71 + z3 均已安装。实测 sby/z3 在 8×8 token 模型上深度 5 已达分钟级、深度 40 在 10 分钟内不收敛，因此门禁改用 yosys-sat：8×8 安全模型（P1–P3 + 路由安全）depth 12 约 3 分钟通过，8 路 RR 进度模型（P5）depth 20 秒级通过。深证明（k-归纳/IC3）与 P4 ordered 语义仍是 M2/后续 sprint 的开放项。

---

## 3. 里程碑与出口条件

| 里程碑 | 周次 | Sprint | 状态 | 交付 | 出口条件（全部满足才过门） |
|---|---|---|---|---|---|
| M0 底座 | W1–W6 | S1–S3 | **已过门（2026-11-19）** | 2×2/4×4 flat fabric、native 端口、定向 TB、C0/C1、确定性发射 | 一条命令产出 RTL+报告；两次发射逐文件哈希一致；定向/背压用例通过 |
| M1 MVP | W7–W16 | S4–S8 | **已过门（2026-11-19）** | 8×8、三类 QoS、VC 队列、信用预留、多 Router + async RouterLink + Pipe、CDG、P1–P3/P5 | G1/G2/G3(构造)/G5–G8 可判；多 Router 树可仿真；8×8 形式化有界收敛（P5 有界）；错误注入通过；有环图拒绝并打印环链。**G4 随 S9 协议壳、G9 随 S17 边界形式化补齐；P4 待 ordered** |
| M2 可用 | W17–W28 | S9–S14 | **已过门（2026-11-19）** | 协议壳、分区多 top、P9、CLI/TB 生成、GUI、CI 矩阵 | 新配置 ≤10 分钟；分区等价通过；GUI 往返 IR 不变；CI 全绿（证据见 m2_review.md） |
| M3 v1.0 | W29–W40 | S15–S20 | **已过门（2026-11-19）** | banked、树/XY mesh、P6–P8、面积校准、签核模板 | 代表元矩阵全绿；估算与仿真偏差在设定区间；签核模板可用；v1.0 已发布 |

---

## 4. WBS 与剩余工作量

| WP | 内容 | 状态（S8 后） | 剩余 PM |
|---|---|---|---|
| WP0 | 工程基建：sbt 模块、CI、产物缓存、formal spike | 完成（S14）：CI 矩阵、夜间 formal、CG6 校验、GitHub Actions | 0 |
| WP1 | IR：schema、规范化、C0/C1/C2/G1、错误码 | 完成；G9 限 CDC 构件并已文档化 | 0 |
| WP2 | 基础构件：VC 队列、仲裁、信用、响应池、端口、Pipe | 完成：RR/WRR/信用/响应池/Pipe/VC 队列（S8） | 0 |
| WP3 | Router 外壳、MNet/SNet、flat switch、banked | 完成（S15）：flat 默认；banked 输出分组 + 出口寄存 | 0 |
| WP4 | 链路：RouterLink、异步 FIFO、CDC 信用、宽窄转换 | 完成（S6/S7）；双口 SRAM 宏列为发布后项 | 0 |
| WP5 | 生成后端：IR→FabricGraph→Chisel 参数、确定性 | 完成；路由歧义告警列为发布后项 | 0 |
| WP6 | 验证闭环：TB 生成、断言包、formal harness、P1–P9 | 完成：P1–P3/P5–P8 有界；P4/深证明列为发布后项 | 0 |
| WP7 | 分区与多 top：切分、边界 Pipe/CDC、S1–S4、P9 | 完成（S10/S11）：切分、边界寄存、ports.json、S1–S4、wrapper 与 P9 | 0 |
| WP8 | CLI 与产物：check/estimate/emit/formal/serve、缓存 | 完成（S12）：六个子命令、manifest、缓存判定、signoff、TB/断言产物 | 0 |
| WP9 | GUI 与估算：表单、拓扑预览、报告面板 | 完成（S13）：零构建表单 + 拓扑 + 门禁/估算面板 + `/api/evaluate` | 0 |
| WP10 | 协议壳：native、AXI4、AxiLite4、TL-UL | 完成（S9）：四类壳 + burst 拆分/响应归并 + G4 | 0 |
| WP11 | 文档与签核：端口表、拓扑图、签核模板 | 完成（S19/S20）：signoff 清单 + v1.0 发布说明 | 0 |
| WP12 | 扩展：树/维序 mesh 模板、面积校准 | 完成（S16/S18）：树、XY mesh、面积校准与 area proxy | 0 |
| **合计** | | | **0** |

原 40 周基线为 27 PM；已完成 S1–S19 按实际交付重估约 26.0 PM，剩余 1.0 PM。

---

## 5. 剩余排期

| 阶段 | 周次 | Sprint | 关键演示 |
|---|---|---|---|
| M1 收口 | W15–W16 | S8 | 已完成：VC 队列/G6–G8；8×8 与 RR 有界证明；M1 评审 |
| M2 | W17–W28 | S9–S14 | **已过门（2026-11-19）**：协议壳 + 分区 + wrapper/P9 + CLI/产物闭环 + GUI + CI 矩阵；评审材料 [m2_review.md](m2_review.md) |
| M3 | W29–W40 | S15–S20 | **已过门（2026-11-19）**：banked、树/XY mesh、P6–P8、面积校准、加固与 v1.0 发布 |

v1.0 已发布；累计约 26.2 PM，处于 27 PM 基线内。任何阶段超支优先削减 M3 的 TL-UL 全功能与多播实验特性，不削减 M1/M2 的 Safe 门与形式化。

---

## 6. Sprint 任务清单（S1–S20）

| Sprint | 任务 | 依赖 | DoD | 状态 |
|---|---|---|---|---|
| S1 W1–2 | sbt 模块、目录、CI 骨架；IR schema v0.1；canonical JSON + hash；C0；参数 case class；2×2 native crossbar 冒烟 | — | `loom check/emit` 可跑；2×2 仿真通过；哈希稳定 | 完成 |
| S2 W3–4 | 4×4 flat Router（RR 仲裁）；定向 TB；`checks.json`/`estimate.json`；walking skeleton | S1 | 一条命令 IR→RTL→仿真→报告；两次发射逐文件一致 | 完成 |
| S3 W5–6 | 信用管理器 + 响应池 + P3 断言；C1；背压/信用边界用例；M0 评审 | S2 | 定向 + 背压用例通过；C1 错误定位到字段 | 完成 |
| S4 W7–8 | 三类 QoS、WRR + 老化、G2/G5；8×8 冒烟；类间隔离用例 | S3 | 三类流量无 HOL；G2/G5 可判 | 完成 |
| S5 W9–10 | CDG 构造 + 环检测（G1）；有环图拒绝并打印环链；P1 token spike | S4 | 3 合法图放行、3 有环图拒绝；P1 在 4×4 有界收敛 | 完成 |
| S6 W11–12 | 异步 FIFO、CDC 信用、WidthAdapter、AsyncLink；P3/P7；错误注入 | S5 | 跨域 1000 项无丢失/透支；256↔128 用例通过 | 完成 |
| **S7 W13–14** | **多 Router RTL**：NetworkParams 端口划分/路由表；LmNetRouter；LmMasterNi/LmSlaveNi 端到端信用；LmPipe 0–4 级；LmRouterLink sync/async；3 Router 树与 4 Router 链 | S5, S6 | 树/链可发射/仿真；async RouterLink 1000 次往返；Pipe 0–4 透明；CG2 通过 | **完成** |
| **S8 W15–16** | **VC 队列 + G6–G8**：每类 VC 队列、`maxPacketBeats`、ctl 单 beat；G6 信用深度/在途占用、G7、G8；8×8 token 模型做有界证明；错误注入；**M1 评审** | S7 | LmVcQueue + 单/多 Router VC 队列；G6–G8 进 `checks.json`；P1–P3 + 路由安全 depth 12、P5 depth 20 有界通过；负向注入通过 | **完成** |
| **S9 W17–18** | **协议壳**：native 4 通道（REQ/WDATA/RSP/RDATA）、AXI4 子集（读写 burst + 错误）、AxiLite4、TL-UL 子集；G4；宽度适配接入（每端口位宽随 S10） | S8 | native 写/读 burst、AXI4 INCR 写/读 + 错误、AxiLite 单拍写/读、TL-UL Put/Get；响应不产生请求 | **完成** |
| **S10 W19–20** | **分区（单 top/分区）**：IR partitions/tops；链路边界切分；边界 Pipe/CDC ≥1；`ports.json`；S1–S3 检查 | S8 | 两分区发射；边界全寄存；分区 hash 独立；跨分区请求/响应通过 | **完成** |
| **S11 W21–22** | **验证 top + P9**：wrapper/verify top、stub、S4 契约；分区组合与未分区事务级等价 | S10 | `ports.json` 前后一致；P9 事务 trace 等价通过 | **完成** |
| **S12 W23–24** | **CLI/产物闭环**：`loom formal/diff/serve`；缓存与 manifest；生成 TB 骨架、断言包、formal harness、签核报告 | S8, S11 | 一条命令产出 RTL+TB+断言+formal+报告；产物确定性；CG5 可用 | **完成** |
| **S13 W25–26** | **GUI**：Schema 驱动表单、拓扑预览、Gate 告警、估算面板；只读写 IR | S9, S12 | GUI 往返 IR 不变；新配置 ≤10 分钟；告警可见 | **完成** |
| **S14 W27–28** | **CI 矩阵 + 文档 + M2 评审**：代表元配置、夜间 formal、CG0–CG6、文档 | S9–S13 | CI 全绿；P9；GUI；M2 出口评审通过 | **完成** |
| **S15 W29–30** | **banked crossbar**：纯流水分级、无新增独立仲裁；时序代理与告警；用户选择 banks | S14 | banked 4×4 可发射/仿真；flat 默认不变；时序代理体现 +1 拍 | **完成** |
| **S16 W31–32** | **树/XY mesh 模板**：树模板；XY 维序 mesh 与 turn model，CDG 支持转向限制 | S14 | 模板通过 CDG；XY mesh 可发射；死锁检查可判 | **完成** |
| **S17 W33–34** | **P6–P8 + 错误/poison + CDC 形式化**：NO_LOSS、CDC_CREDIT、ERROR_TOTAL；k-归纳/sby | S15, S16 | P6–P8 在代表配置收敛；CDC 无反例 | **完成（G9 限 CDC 构件）** |
| **S18 W35–36** | **面积/延迟校准**：yosys/nangate45 预算、Router/VC/CDC/宽度系数、估算 vs 仿真 | S15–S17 | 估算落在目标区间；报告列出前三敏感参数 | **完成** |
| **S19 W37–38** | **v1.0 加固**：perf counter、错误日志、reset/CDC lint、文档与签核模板、代表元矩阵 | S18 | 签核模板可用；文档同步；无手工 RTL | **完成** |
| **S20 W39–40** | **v1.0 发布**：全量回归、CG6、IR/协议版本冻结、release notes、已知限制 | S19 | v1.0 打标；矩阵全绿；签核完成 | **完成** |

### 6.1 每个 Sprint 的统一 DoD

- CG0（编译）与 CG1（schema/检查器单测）全绿。
- 新增功能有定向用例 + 至少一个负向/边界用例；全量回归不红。
- CG2 确定性通过；新增产物进入哈希清单。
- 文档同步：architecture.md 的契约变更 + 本计划进度。
- 发射物入库（`generated/loom/`），禁止手工修改 RTL。
- 新 Gate 必须在 `checks.json` 可见，失败能定位到字段或环链。

**并行安排**：S9（协议壳）与 S10（分区）可在 S8 后并行；S11 依赖 S10；S12 依赖 S8/S11；S13 依赖 S9/S12。只有 S7→S8 是严格串行（多 Router 与 VC 队列共用同一套参数/信用模型）。

---

## 7. 验收与 CI 门禁

| 门禁 | 内容 | 频率 | 失败处理 |
|---|---|---|---|
| CG0 | 编译（Scala + firtool） | 每次提交 | 阻断合并 |
| CG1 | schema/canonical/检查器单测 | 每次提交 | 阻断合并 |
| CG2 | 确定性：同 IR 两次发射逐文件哈希一致 | 每次提交 | 阻断合并 |
| CG3 | 冒烟仿真：2×2、4×4、8×8；S7 起加多 Router 树；S10 起加两分区 | 每次提交 | 阻断合并 |
| CG4 | 形式化 P1–P3 + 路由安全（8×8，depth 12）、P5（8 路 RR，depth 20）、P6/P7/P8（token 模型，depth 12），yosys-sat | 每夜 | 阻断发版 |
| CG5 | 报告基线 diff（checks/estimate/graph） | 每夜 | 人工评审 |
| CG6 | 生成物清单哈希 + 禁止手工修改扫描 | 发版前 | 阻断发版 |
| CG7 | 负向注入：P3/P7 断言、非法配置、环图 | 每次提交 | 阻断合并 |

落地方式（S14）：

- 每次提交：`sbt Test/compile` + `sbt "testOnly loom.*"`（21 suite / 85 用例，含负向注入与 lint）+ `./src/loom/scripts/ci_matrix.sh
./src/loom/scripts/area_calibration.sh
./src/loom/scripts/lint.sh`（CG0/CG2/CG5/CG6）。
- 每夜：`./src/loom/scripts/ci_matrix.sh
./src/loom/scripts/area_calibration.sh
./src/loom/scripts/lint.sh --formal`（CG4，约 3 分钟/配置的 yosys-sat 有界证明）。
- 发版前：`cg6_manifest.sh` 校验发射目录的 manifest 未被手工修改；`cg2_determinism.sh` 覆盖代表元配置。
- GitHub Actions：`.github/workflows/loom.yml`（Loom 路径触发；安装 verilator/yosys；编译、测试、CI 矩阵）。

验收项沿用架构规范 A1–A7，并追加：

- A8：任何 Safe mode 配置在 `loom emit` 前必须已有 `checks.json`，且 Gate 表完整。
- A9：生成的 `ports.json` 在分区前后一致；跨区链路全部寄存。
- A10：估算报告必须列出敏感参数前三名与区间，否则视为无效。
- A11：多 Router 配置必须给出确定性路由表；等代价路径必须告警。
- A12：CDC 链路必须跑过 P7 负向注入与 1000 次往返。

---

## 8. 风险与应对

| # | 风险 | 概率/影响 | 触发信号 | 应对 |
|---|---|---|---|---|
| 1 | 8×8 形式化深证明收敛超时 | 高/中 | sby/z3 深度 40 超 10 分钟 | S8 已用 yosys-sat 有界签核（安全 12 拍、RR 进度 20 拍）；M2 评估 IC3/更强求解器 |
| 2 | 多 Router 路由不确定/不可达 | 低/中 | 等代价路径、跨分支请求 | 已实现最短路径 + 排序 tie-break 与 XY 转向路由；等代价路径不做告警，按确定性 tie-break 取第一个 |
| 3 | VC 队列引入固定延迟与准入串行化 | 中/中 | 旧组合语义用例转红 | 已按缓存语义更新 4×4/信用用例；准入与出口用同一套类权重，避免权重被准入 RR 稀释 |
| 4 | CDC FIFO 寄存器阵列面积/时序 | 中/中 | 64b×深度综合过大 | S9/M2 替换双口 SRAM 宏；面积估算先行 |
| 5 | CDC 信用计数回绕 | 低/中 | 长同步延迟下 16-bit epoch 绕回 | 按 depth/延迟定宽；断言覆盖；形式化 P7 |
| 6 | GUI 范围蔓延 | 高/中 | 要求在页面里跑 elaboration | 架构禁止；GUI 只读写 IR |
| 7 | Chisel 展开时间/内存 | 中/中 | 8×8 多 Router >10 分钟或 OOM | 参数缓存；分区；MVP 端口上限 8 |
| 8 | 无 STA 却承诺时序 | 高/中 | 报告出现“时序收敛”字样 | 只输出代理指标；banked 由用户选择 |
| 9 | 协议语义蔓延 | 中/高 | 为单配置加特例 | 语义版本冻结；变更走 ADR + 全矩阵重跑 |
| 10 | 人力不足 | 高/高 | 连续两个 sprint 未达 DoD | 2 人则顺延；优先保 M1/M2 的 Safe 门 |

---

## 9. 变更控制

- **IR schema** 走 semver；破坏性变更必须提供迁移脚本与旧配置回归。
- **协议语义**（通道、字段、错误码、状态机）独立版本化；任何变更需 ADR，记录动机、影响配置集与重跑结果。
- **路由模型**（最短路径/turn model/显式路由表）视为语义的一部分；变更必须重跑 CDG 与 P 系列。
- **生成物** 不允许手工修改；发现补丁即视为配置缺陷，回写到 IR 或构件库。
- **范围变更** 只允许在里程碑边界发生；里程碑内只接受缺陷修复与文档修正。

---

## 10. 开放决策（需在对应周次前拍板）

| 决策 | 截止 | 选项 | 默认 |
|---|---|---|---|
| 形式化后端 | 已定（S8） | yosys-sat 有界 / sby+z3 / IC3 | **sat 有界门禁**：8×8 安全 depth 12、RR 进度 depth 20；sby+z3 深度 40 不收敛，M2 再评估 |
| 多 Router 路由 | 已定（S7） | 最短路径推导 / IR 显式路由表 | 最短路径 + 排序 tie-break；根主/叶从树/链已实现；通用图 S8 扩展 |
| YAML 前端 | S9 W17 | 手写解析 / 外部库 / 仅 JSON | JSON canonical；YAML 后置 |
| 协议壳保真度 | S9 W17 | 子集 / 完整 | 子集（burst + 错误） |
| 分区默认策略 | S10 W19 | 时钟域 / 综合规模 / 手工 | 时钟域 + 手工覆盖 |
| TB 形态 | 已定（S12） | Chisel / Chisel+SVA / UVM | Chisel 仿真 + 端口表驱动的 SV TB 骨架；断言沿用发射出的 SVA 清单 |
| 前端技术 | S13 W25 | 零构建静态页 / Vite+框架 | 零构建 |
| banked 默认 | S15 W29 | flat / 自动 / 用户选择 | 用户选择，默认 flat |

---

## 11. 当前进度

**S1（W1–W2）已完成，2026-11-18**：

- `build.sbt` 已接入 `src/loom/scala/main` 与 `src/loom/scala/test`。
- IR v0.1：自带 JSON 解析器（无新依赖）、canonical JSON、SHA-256 config hash、C0 检查（schema/必填/范围/引用完整性）。
- RTL：`LmRrArbiter`（旋转请求 + 优先级编码，无组合环）、`LmFlatRouter`（请求按地址路由、响应按 src 回程）、`LmTop`（master/slave 端口壳，MNet/SNet 零延迟分组）。
- CLI：`loom check` / `loom emit`；示例 `src/loom/examples/minimal_2x2.json`。
- 验证：`loom.IrSpec`、`loom.LmRouterSpec`、`loom.EmitSpec` 共 9 个用例全绿；同一 IR 两次发射的 `.sv` 逐文件一致。
- 端到端产物：`generated/loom/minimal_2x2/`（`LmTop.sv`、`LmFlatRouter.sv`、`LmRrArbiter.sv`、`filelist.f`）。
- config hash：`da1694c65e0dba858392c3682f26942db0047695334df7b507610e208e0fb600`。

**S1 明确不含**：VC/QoS、信用预留、异步链路、CDG 环检测、AXI4 壳、多 top、`checks.json`/`estimate.json` 落盘。

**S2 已完成，2026-11-18**：

- 4×4 示例 `src/loom/examples/minimal_4x4.json`（128b、4 主 4 从、flat 16 交叉点）。
- `LmRouter4x4Spec`：4 条定向路由 + 双主争用同一从端的 RR 轮转；合计 5 个场景。
- 报告落盘：`checks.json`（C0 结果、warnings、configHash）与 `estimate.json`（链路容量、交叉点、延迟代理、假设、`LM-W-TIMING` 告警）。无时间戳、无绝对路径，两次发射逐字节一致。
- CLI 新增 `loom estimate`；`loom emit` 自动写两份报告；`loom check --out` 可单独落盘。
- CG2 脚本 `src/loom/scripts/cg2_determinism.sh`：两次独立发射后 `diff -r`，已实测通过。
- 测试：5 个 suite、14 个用例全绿。
- 4×4 实测：`crosspoints=16`、每链路 16 GB/s @1 GHz/128b、`latencyProxyCycles=4`、无时序告警。

**S2 明确不含**：信用预留、VC、异步链路、C1 结构检查、CDG、AXI4、多 top。

**S3 已完成，2026-11-18**：

- `LmCreditMgr`：每主一个返回槽信用池，深度取 `endpoint.outstanding`；请求注入时分配，响应送达主设备时释放。
- `LmSlotPool`：每从一个目的端响应槽池，深度取 Σ 主 outstanding；请求注入时分配，响应离开从端时释放。
- 准入条件：下游 ready ∧ 主返回槽可分配 ∧ 从响应槽可分配。G3 的“预预留”与 G6 的信用守恒由此结构化落实。
- P3 断言：两个池各自断言 overflow/underflow；Router 额外断言“无返回槽时不得接受请求”。非请求响应会触发 underflow 断言，已用负向用例验证可捕获。
- C1 结构检查：Router 度数 vs 端点计数、2 的幂、重复链路、mnet 出边/snet 入边、Router 可达、sync 链路跨时钟域、地址位宽。
- `checks.json` 增加 `C0`/`C1` 双门；`perf.returnSlots` / `perf.respSlots` 可观测。
- 背压用例：从端未 ready、返回信用耗尽后恢复、响应槽耗尽、P3 负向。
- 测试：6 个 suite、20 个用例全绿；CG2 仍通过；4×4 `checks.json` 的 C0/C1 均 true。

**S3 明确不含**：VC/QoS 三类划分、异步链路、CDG、AXI4、多 top。

**S4 已完成，2026-11-19**：

- IR 增加可选 `qos.classes`（name/vcs/depth/arbitration/weight），缺省 ctl:4、lat:2、bulk:1；`FabricParams` 增加 `masterClass` 与 `classWeights`。
- 端点 QoS 是静态属性：`masterClass` 由 endpoint.qos 映射到 0/1/2，Router 不需要在请求 payload 里携带类字段。
- 请求仲裁两级：每个（输出端口 × 类）一个 RR 仲裁器，类内只看到属于该类的 master；类间用 `LmWrrArbiter` 按权重展开的静态交错调度选择，空闲类跳过。
- 老化：类连续请求 `MaxAge=8` 拍未获授权即强制授权；授权后 age 清零。
- 握手正确性：输入 `ready` 只由本拍被类间调度选中的类仲裁器产生。
- C2（G2/G5）：类名恰为 ctl/lat/bulk 且唯一，vcs/depth/weight 范围、arbitration 取值、每端点必须有类定义。
- 用例：权重比例（ctl:bulk=4:1）、老化上限、C2 负向；测试 7 个 suite、25 个用例全绿；发射物新增 `LmWrrArbiter.sv`。

**S4 明确不含**：异步链路与 CDC 信用、CDG、AXI4/TL-UL、多 top、banked crossbar。

**S5 已完成，2026-11-19**：

- `FabricGraph`：节点为 MNet/SNet/Router，通道为有向 link；每个 Router 上入通道可依赖除 U 转以外的任意出通道，构造 CDG。
- G1：确定性 DFS 环检测；有环返回 `LM-G1: CDG cycle: ...` 并打印通道链；`checks.json` 增加 `G1`。
- 自环计为真实环；普通双向链路不因 U 转误判；`graph.json` 随 `check --out`/`emit` 落盘。
- 用例：3 个合法图（单级、三 Router 树、四 Router 链）放行；3 个有环图（三环、双向环、自环）拒绝。
- 8×8 冒烟：`minimal_8x8.json` 一次路由 8 条与响应回程通过。
- P1 spike：`p1_token_4x4.sv` 4×4 token 模型，yosys SAT 深度 12 无 counterexample；`p1_spike.sh` 可复现。
- 测试：9 个 suite、33 个用例全绿。

**S5 明确不含**：P1 无界活性、异步链路、WidthAdapter、AXI4、多 top、banked。

**S6 已完成，2026-11-19**：

- `LmAsyncFifo`：双时钟 FIFO，gray 写/读指针，2 级同步器；写侧隐式时钟，读侧 `dstClk/dstReset`；存储为寄存器阵列（distributed-RAM spike）。
- `LmCdcCredit`：源域分配、目的域归还；归还计数 16-bit gray 回传 + 2FF 同步；源域保守信用；P7 断言禁止超发/在途超深度。
- `LmAsyncLink`：FIFO + 信用组合；push 受 FIFO ready 与信用约束，pop 归还信用，`creditUsed` 可观测。
- `LmWidthAdapter`：2:1 下拆 / 1:2 上拼；下拆低半 `last=0`、高半继承；上拼奇数 beat 触发断言；支持输出反压。
- 用例：2:1 时钟比 1000 项无丢失/乱序；CDC 信用 1000 次归还后归零；AsyncLink 1000 项不透支；强制超发触发 P7；256↔128 及反压通过。
- `loom blocks` 发射四个独立构件；测试 11 个 suite、40 个用例全绿。

**S6 明确不含**：多 Router 的 `LmRouterLink`/`LmTop` 集成、双口 SRAM 宏、异步复位同步器、AXI4/TL-UL、多 top。

**S7 已完成，2026-11-19**：

- 形式化工具解除阻塞：`brew install sby` 装好 sby 0.71，yosys 由 0.69 升到 0.71；`p1_spike.sh` 在新版本下重跑仍通过。
- `NetworkParams`：按 router 名排序建图，mnet 恰好一条出边、snet 恰好一条入边；端口布局为 `masters ++ inLinks` / `slaves ++ outLinks`；BFS 求最短路径，下一跳同名按序 tie-break，得到 `routeSlave` / `routeMaster` 路由表。只有“有 master 的 router 必须能到所有 slave”和“有 slave 的 router 必须能到所有 master”是硬约束，叶分支之间的不可达不报错。
- `LmNetRouter`：输入输出各带 req/rsp 通道；每（输出端口 × 类）RR + 类间 WRR，与单 Router 语义一致；响应按 `src` 查 `routeMaster` 回程；`qos` 越界断言。Router 内部不缓冲，信用全在 NI。
- `LmMasterNi` / `LmSlaveNi`：端到端信用与响应槽从单 Router 的 `LmTop` 上移到 NI，每个 master 一个返回槽池、每个 slave 一个响应槽池；master NI 注入 `qos` 并把 `src` 改写为本 master 编号，slave NI 去掉 `qos`。
- `LmPipe(stages)` 0–4 级：0 级直连，N 级为 N 深 `Queue`，逐拍透明，满时反压。
- `LmRouterLink`：sync 模式在 req（A→B）与 rsp（B→A）各插 `LmPipe`；async 模式两侧各一个 `LmAsyncLink`，bundle 展平成 `UInt` 跨域后 `asTypeOf` 还原，req 方向的 `creditUsed` 可观测。
- `LmNetworkTop`：拼 NI、Router、RouterLink 与 perf 输出。**当前显式拒绝 async 链路**——多时钟顶层还需处理复位与 `withClockAndReset` 的边界，留到 M2。
- 示例：`tree_3r.json`（4 master 在根，两个叶 Router 各挂 mnet/snet，链路 pipe=1）、`chain_4r.json`（2 master 在首 Router，4 级链，2 slave 在尾）。
- 用例：`LmNetworkSpec`（树根主→叶从、跨分支、4 级链）、`LmPipeSpec`（0–4 级与反压）、`LmRouterLinkSpec`（双时钟域 1000 请求/响应，`creditUsed` 归零）。
- 回归：14 个 suite、46 个用例全绿；树/链发射成功且 CG2 逐文件一致。

**S7 明确不含**：VC 队列、G6–G9、多时钟网络顶层、通用图等代价告警、banked。

**S8 已完成，2026-11-19**：

- IR：`qos.classes[].maxPacketBeats`（缺省 ctl=1、lat=2、bulk=4）；C2 增范围检查；新增 G6（类深度与 master outstanding ≥ maxPacket）、G7（ctl 必须 1）、G8（仲裁仅 wrr/rr）；`checks.json` 门禁变为 C0/C1/C2/G1/G6/G7/G8。
- `LmVcQueue`：每类 VC 队列，深度取类深度，满时反压。
- 单 Router：每输入口一个类 VC 队列；准入按目的端串行（每从端每拍一次，匹配响应槽的一次分配），准入与出口共用同一套类权重；返回槽与响应槽在准入时预留；出口从队列头做类内 RR + 类间 WRR。
- 多 Router：`LmNetRouter` 每（输入口 × 类）一个 VC 队列，链路输入可携带多类；出口按 `routeSlave` 选路。
- 运行期守护：`len+1 > maxPacketBeats` 触发 G6 断言；ctl 类 `len != 0` 触发 G7 断言；队列占用 ≤ 在途信用（G6 不变式）。
- 形式化：`token_8x8.sv`（P1/P2/P3/P4）depth 12、`token_rr8.sv`（P5 8 路 RR 进度）depth 20，均由 yosys-sat 有界通过；sby/z3 深度 40 不收敛，记录为 M2 风险。
- 用例：新增 `LmVcSpec`（队列深度/反压/顺序、包长与 ctl 负向）、IrSpec G6/G7/G8 负向；`LmCreditSpec` 与 `LmRouter4x4Spec` 改为缓存语义。**15 suite / 55 用例全绿**；4×4/tree CG2 通过。

**S8 明确不含**：多 Router 多时钟顶层、G4（协议壳）、G9（跨边界形式化）、深归纳证明、banked。

**S9 已完成，2026-11-19**：

- `LmShellCore`：协议无关的 burst 引擎。命令锁存后逐拍发到 fabric（写数据随请求走），响应按序归并；写 burst 聚合为一个 bresp，读 burst 逐拍输出 rdata；请求只在 SEND 状态产生，响应路径永不生成请求（G4）。
- `LmShellNative`：REQ/WDATA/RSP/RDATA 四通道，支持 `len` 拍 burst。
- `LmShellAxi4`：AW/W/B/AR/R 子集，INCR burst、`size` 必须等于总线拍宽、无 ID；读 `last` 与写 `resp` 正确传播。
- `LmShellAxiLite4`：AW+W / AR 单拍，`OKAY`/`SLVERR` 映射。
- `LmShellTlUl`：A/D 通道 Get/Put（PutFull/PutPartial），单拍，`source` 回显、`error` 传播。
- G4 进 `checks.json`：协议全覆盖为静态判据，响应隔离由 `LmShellSpec` 定向用例证明。
- 用例：`LmShellSpec` 7 例全绿；全量 **16 suite / 62 用例**；4×4 CG2 仍通过。

**S9 明确不含**：壳与 top 的自动实例化（随 S10/S12）、每端口位宽与宽度适配接线、AXI4 ID/outstanding 乱序、TL-UL 多拍 burst、AXI4 非 INCR burst。

**S10 已完成，2026-11-19**：

- IR 新增可选 `partitions`（name/clock/members/boundaryPipe）；未声明时行为不变。
- S1–S3 进 `checks.json`（对齐架构 §9.5）：S1 成员全覆盖/唯一/时钟，且 mnet/snet 与所挂 Router 同分区（切分只落在链路边界）；S2 边界 `boundaryPipe ≥ 1` 且每个分区至少一个 Router（边界寄存）；S3 切分边必须是 Router-Router（信用/响应槽不跨边界分裂）。错误码 `LM-S1/2/3`。
- `PartitionParams`：局部 Router、局部 master/slave、内部链路、出/入边界链路；分区 hash 由名称/时钟/成员/端点/边界链路 canonical 计算，与全局 hash 独立。
- `LmPartitionTop`：复用全局 Router 端口布局与路由表，切分边改成边界端口；每条边界 req/rsp 通道插入 ≥1 级 Pipe；局部 master/slave 带 NI 与 perf 观测；暂拒绝 async 边界。
- `emit`：配置含 `partitions` 时，除全网络 top 外，为每个分区发射 `partitions/<name>/` 与 `ports.json`（含 `partitionHash`、master/slave 映射、边界端口方向/对端/位宽）。
- 示例 `chain_split.json`（front={mnet0,r0,r1}，back={r2,r3,snet0}，切 r1→r2）；`LmPartitionSpec` 用双分区 harness 完成跨边界写+响应回程；分区元数据/hash/ports.json 稳定性用例。
- 回归：**17 suite / 66 用例全绿**；chain_split 发射与 CG2 通过。

**S10 明确不含**：async 分区间链路、多时钟分区顶层、验证 top 与 P9（S11）、自动分区策略。

**S11 已完成，2026-11-19**：

- `LmWrapperTop`：按分区实例化全部子 top，按链路 id 连接边界 req/rsp；全局 master/slave/perf 编号保持不变；要求分区恰好覆盖全部 Router。
- `LmPartitionStub`：与真实分区共用同一 `LmPartitionIo` 端口契约；每个被接受的本地主请求/边界请求恰好返回一个 `err=1` 响应，保证上游信用与响应槽闭环（S4）。
- `LmPartitionIo`：分区顶层与 stub 的共享端口 Bundle，锁定 `ports.json` 的字段与顺序。
- `emit`：分区配置额外产出 `tops/LmWrapperTop.sv`、`partitions/<name>/` 与 `partitions/<name>/stub/`。
- P9 用例：同一事务序列分别跑“单分区 wrapper”和“front/back 两分区 wrapper”，slave 侧 (端口/地址/数据/src) 与 master 侧响应 trace 完全一致；S4 stub 两个定向用例验证错误响应与边界信用闭环。
- 回归：**18 suite / 69 用例全绿**；chain_split 发射与 CG2 通过。

**S11 明确不含**：async 分区边界、多时钟 wrapper、仿真 trace 自动比对工具（S12 `loom diff`）。

**S12 已完成，2026-11-19**：

- `loom emit` 产物闭环：`tb/<Top>_tb.sv`（从发射后的端口表反解方向与位宽，复位后全输入置零，Verilator lint 通过）、`tb/assertions.f`（排序后的 verification 清单）、`signoff.md`（门禁表、告警、产物计数、有界证明结果、局限）、`manifest.json`（相对路径 + 逐文件 SHA-256）。
- `--cache`：manifest 的 configHash、工具版本与全部文件哈希一致时跳过重发射并打印 `up to date`；`--formal` 在同一次 emit 内完成 P1–P3/P5。
- `loom formal`：yosys-sat 验证 8×8 安全模型与 8 路 RR 进度模型，写 `formal/formal.json` 与日志，并刷新已存在产物的 signoff/manifest。
- `loom diff --a --b`：输入为配置时比较端点、Router、链路的网络契约（分区差异仅提示，可在配置级对照 P9）；输入为发射目录时逐份比较 `checks.json`/`estimate.json`/`graph.json`，作为 CG5 报告基线 diff。
- `loom serve --dir --port`：JDK HttpServer 静态预览发射目录，拒绝路径逃逸，为 S13 GUI 提供托管面。
- TB 生成修正：同一端口声明的延续行继承方向与位宽（`output [2:0] a, b`），跨方向不继承；避免 `[2:0]` 端口在 TB 中退化为 1 位。
- 用例：新增 `TbGenSpec` 3 例、`ReportSpec` +2 例；全量 **19 suite / 74 用例全绿**；chain_split 产物清单与 signoff 可复现。

**S12 明确不含**：GUI 页面与 IR 编辑（S13）、CI 矩阵（S14）、TB 自动激励与断言绑定（M3 签核模板）、跨机器缓存后端。

**S13 已完成，2026-11-19**：

- 零构建 GUI（`src/loom/gui/`：`index.html` / `app.js` / `style.css` / `form-schema.json`）：左侧 Schema 驱动表单（标量 + endpoints/routers/links 数组增删改）、IR JSON 编辑器、Canonicalize/Export；右侧拓扑 SVG、门禁表、估算表。
- `loom serve` 同时托管 GUI 与发射目录，并提供 `POST /api/evaluate`（IR → 门禁/估算/拓扑/规范 JSON）、`GET /api/examples`、`GET /api/example?name=`。
- 浏览器只编辑 IR；门禁与估算由 `LoomApi` 调用与 CLI 相同的 `Checks`/`Gates`/`Reports`，不复制规则（I7）。表单改动 250 ms 去抖后自动求值。
- GUI 往返：`evaluate` 返回 canonical JSON，重新求值哈希不变；`LoomApiSpec` 断言 canonical 往返与错误路径。
- 浏览器验证：加载 minimal_4x4 显示 11 项门禁全绿、crosspoints=16；改 `maxFanin=3` 后出现 `LM-W-TIMING` 告警；新增未接线 Router 显示 C1 fail 与具体错误。
- 用例：新增 `LoomApiSpec` 5 例；全量 **19 suite / 79 用例全绿**。

**S13 图形化改造（2026-11-20）**：

- `form-schema.json` 删除，改为结构编辑器：`partition → router → mnet/snet` 层次建对象，附接链路自动生成，Router→Router 链路与端点挂接只在既有对象中选择。
- 时钟先行：先建时钟域，所有节点时钟只从已建时钟中选择，改分区时钟级联成员。
- 设计级宽度收为固定集合（`addrWidth ∈ {16,32,48,64}`、`dataWidth ∈ {32,64,128,512,1024}`），C0 同集合校验；端点与链路宽度随设计同步。
- 页面重设计（Space Grotesk / JetBrains Mono、三栏工作区、按域着色拓扑与图例、Inspector），仍为零构建静态资源，规则全部走 `LoomApi`。
- 拓扑布局：全部 Router 带 `pos` 时按网格映射坐标（MNet 左列、SNet 右列），否则回退最长路径分层。
- 工作目录：`loom serve --work <dir>` 或页面弹窗选择；`/api/workspace`、`/api/draft` 保存 `loom_draft.json`（临时文件 + 原子替换），刷新自动恢复，路径记忆在 `~/.loom/serve_workspace`。
- 地址映射视图：`/api/evaluate` 增 `addressMap`，GUI 展示 slave 的 base/end 与 `addr[12 +: bits]` 译码说明（基址仍按 IR 顺序固定）。

**v1.2 语义升级（2026-11-20）**：

- **MNet/SNet 为物理共享端口**：每个网络每拍每方向最多一个事务。单 Router、多 Router 与分区 top 均插入请求/响应令牌仲裁；多 Router 的 SNet 请求经一级 skid 打破 token↔ready 组合环。Router 端口数与估算按网络数计（`r.in = MNet 数`、`r.out = SNet 数`）。
- **可编辑地址映射**：slave 端点新增可选 `base`/`size`；C0 校验对齐/幂次/容量，C1 校验不重叠；RTL 显式映射按窗口比较译码，未声明时保留 `addr >> 12` 别名。GUI 地址表可直接编辑，`auto 4K` 清除字段回到派生映射。
- **示例调整**：`minimal_2x2/4x4/4x4_banked/8x8` 改为每端点一个 MNet/SNet，保持满端口并行与 4×4 crosspoints 基线；共享端口示例仍由 `mesh_2x2_xy` 等覆盖。
- **非 2 的幂端点**：移除 C1/FabricParams/NetworkParams 的主从数量 2 的幂限制，通用 RR 仲裁器改为任意基数；窗口译码不再依赖移位截断。3×3 配置 `check` 与 `emit` 均通过。
- **连接矩阵**：GUI 新增只读矩阵（master × slave，显示最小 Router 跳数/不可达）。
- **用例**：新增 `LmShareSpec`（共享端口准入）与 3×3 译码用例；全量 **23 suite / 99 用例全绿**。

**S13 明确不含**：图形编辑分区/拓扑拖拽、IR 语法高亮与自动补全、浏览器内 emit、CI 矩阵。

**S14 已完成，2026-11-19**：

- `ci_matrix.sh`：6 个代表元配置的静态门（CG0）、minimal_4x4/tree_3r/chain_split 的 CG2、chain_split 双发报告基线（CG5）、manifest 完整性（CG6）；`--formal` 追加 CG4。
- `cg6_manifest.sh`：逐文件重算 SHA-256，缺失/修改均报错；实测 219 个产物通过，篡改 `estimate.json` 后正确失败。
- `.github/workflows/loom.yml`：Loom 路径触发，安装 verilator/yosys，编译、`testOnly loom.*`、CI 矩阵。
- 文档：CI 门禁落地方式、[m2_review.md](m2_review.md)（出口条件、证据、开放项与风险）。
- M2 出口评审：新配置 ≤10 分钟、P9 等价、GUI 往返不变、CI 全绿，全部满足；开放项转入 M3。

**S14 明确不含**：远程 CI 缓存、跨平台矩阵（Windows/macOS）、发版流水线（S20）。

**S15 已完成，2026-11-19**：

- IR/参数：`banked` 允许 `banks = 1..8`；C0 校验 bank 数为 2 的幂且整除 `out`；`FabricParams` / `NetworkParams` 同步校验，flat 仍要求 `banks = 1`。
- RTL：`LmFlatRouter` 与 `LmNetRouter` 的出口加 1 级寄存（`LmPipe(1)`）；仲裁的 ready 改由该级给出，不新增仲裁、不改变路由；flat（banks=1）路径与 S14 逐位一致。
- 估算：`routers[].banks` 可见；延迟代理对每个 banked Router +1 拍，并写入 assumptions。
- 示例 `minimal_4x4_banked.json`（banks=2）可发射；定向用例验证两笔请求分别落到 bank 0/1 且响应回程正确。
- 架构 §3.3 对齐实现：v1.0 的 banked 是"输出分组 + 出口寄存"，交叉点不变；Clos 式两级列为后续选项。
- 用例：新增 `LmBankedSpec` 2 例、`IrSpec` +1 例；全量 **21 suite / 82 用例全绿**。

**S15 明确不含**：Clos 式中间级、按输入分组、bank 间负载均衡、多级交织的时序模型。

**S16 已完成，2026-11-19**：

- IR：Router 新增可选 `pos {x,y}` 与 `routing: "any"|"xy"`（默认 any，旧配置行为不变）。
- C0：`routing` 取值校验；`xy` 必须有坐标；相邻 Router 链路必须 Manhattan 距离 1。
- CDG：`xy` Router 按转向模型裁剪依赖——X 入可转 X/Y，Y 入只能继续 Y；禁止 180° 回转；非 Router 对端（mnet/snet）不限制。因此 2×2 双向 mesh 在 `xy` 下无环，改为 `any` 立即检出环。
- 路由表：`xy` Router 请求按 X 先 Y 后、响应按 Y 先 X 后取确定性下一跳，不再用 BFS tie-break，保证 RTL 路径与 CDG 假设一致。
- 示例 `mesh_2x2_xy.json`：4 Router 双向 mesh，mnet 挂 r00、snet 挂在 r11；`check`/`emit` 通过，定向仿真完成跨角请求与响应回程。
- 树模板沿用 `tree_3r`（CDG 无环、可发射、已有定向用例）。
- 顺带修正：Router 参数读取按名称映射，而非排序后的下标（此前 flat 全为 banks=1 未暴露）。
- 用例：`GraphSpec` +2、`LmNetworkSpec` +1；全量 **21 suite / 85 用例全绿**。

**S16 明确不含**：XY 以外的 turn model（West-first/North-last 等）、非 Manhattan 链路、mesh 的 VC 升级与自适应路由。

**S17 已完成，2026-11-19**：

- `token_response.sv`（P6/P8）：请求只在空闲槽接收，响应恰好一次；断言 `accepted − responded = 在途数` 与"响应只对应活跃槽"，depth 12 通过。
- `token_cdc.sv`（P7）：跨域信用计数，发送侧按信用契约约束，断言 `used ≤ depth`，depth 12 通过。
- `loom formal` 一次跑 4 个模型（8×8 安全、RR8 进度、response、CDC），约 3 分钟；`formal.json` 与 `signoff.md` 同步 4 行结果。
- 新增 `p6_p8.sh` 可直接复现 P6–P8；CI `--formal` 覆盖全部四个模型。
- P8 的 RTL 侧由 `LmPartitionStub`（每请求恰好一个错误响应）与协议壳错误传播承担；TL-UL poison 不在当前子集内。
- **G9 范围**：分区顶层仍拒绝 async 链路，因此 G9 只覆盖 CDC 构件（P7 token 模型 + `LmCdcSpec` 双时钟用例）；async 分区边界的跨域形式化转为发布后项。
- 用例数不变（85）；新增模型与脚本不改 RTL 行为。

**S17 明确不含**：async 分区边界、TL-UL poison、深归纳/IC3、P4 ordered。

**S18 已完成，2026-11-19**：

- `area_calibration.sh`：yosys 0.71 + nangate45 流程（`synth` → `dfflibmap` → `abc` → `stat -liberty`）。为绕过 yosys 对 firtool `automatic` 局部变量与 `'{...}` 赋值模式的不支持，脚本在临时副本上做两处机械改写（hoist 为 `wire`、`'{` → `{`），只影响测量副本。
- 实测（µm²）：2×2 = 29,752.6；4×4 = 68,927.0；4×4 banked = 73,217.0。拟合 `area = 16,694.5 + 3,264.5×crosspoints + 4,290×bankedRouters`，写入 `generated/loom/calibration/area.json`。
- `estimate.json` 新增 `totals.areaProxyUm2`、`areaSensitivity`（按贡献排序的前三项）与 nangate45 假设；8×8 代理 225,622.5 µm²（crosspoints 项 208,928 占主导）。
- 数据口径：面积含 VC 队列与 NI，不含 SRAM 宏、时钟树与布线；用于配置间对比，不作硅面积签核。
- 未新增用例（脚本+报告字段），回归仍 85 例。

**S18 明确不含**：时序（无 STA）、功耗、SRAM 宏与物理布线；banked 的 Clos 变体未建模。

**S19 已完成，2026-11-19**：

- `LmTop` / `LmNetworkTop` 的 `perf` 新增 `requests`、`responses`、`errors`、`errSticky`：请求/响应按 master 口 fire 计数，错误为响应 `err=1` 的计数与粘滞位；原有 returnSlots/respSlots 不变。
- `lint.sh`：Verilator 对 4×4、8×8、XY mesh、chain_split wrapper、两个分区 top 与 `LmAsyncLink` 做 elaboration lint；只把 error 视为失败。CI 矩阵新增 `[CG7]`。
- 修正 `loom blocks`：四次发射会互相覆盖 `filelist.f`，现在统一重写为全部模块的排序清单。
- `signoff.md` 增加 Signoff checklist（门禁、manifest、TB/断言、formal、面积、集成签核）。
- 代表元矩阵：8 个配置（含 banked 与 mesh）在 CI 中做 check/CG2/CG6/lint；formal 由夜间 `--formal` 覆盖。
- 用例：在既有 router 用例中断言 perf 计数（85 例不变）；lint 与 CI 脚本可复现。

**S19 明确不含**：带宽/延迟运行时统计、错误地址日志（响应无地址字段）、DFT/复位域 lint 工具链。

**S20 已完成，2026-11-19**：

- 工具版本升至 `1.0.0`；新增 `loom version`（打印版本与 schema），manifest 的 version 同步更新（旧缓存自动失效）。
- 版本冻结：IR `loom/v1`（新增字段均可选）、检查码、生成物 schema 与形式化门禁深度（见发布说明第 3 节）。
- 发布说明 [release_notes_v1.0.md](release_notes_v1.0.md)：能力清单、验证记录、已知限制与升级注意事项。
- 发布验证：全量回归 21 suite / 85 用例；CG2 三配置；CG5 报告 diff；CG6 manifest；CG7 lint；CG4 四模型全部 PASS。
- 未打 git tag / 未提交：按项目约定，git 写操作需用户明确授权。

**v1.0 已发布。**

### 11.1 当前基线（v1.0）

- **已交付**：IR + 全部门禁；flat/banked fabric；树与 XY mesh；四类协议壳；分区/P9；CLI 全命令；TB/断言/signoff/manifest；GUI；CI（check/CG2/CG5/CG6/CG7/夜间 CG4）；P1–P3/P5–P8 有界证明；面积校准；perf 计数与粘滞错误；85 个用例；`loom 1.0.0`。
- **发布后项**：async 分区边界与 G9 跨边界、P4 ordered、深归纳/IC3、AXI4 ID/乱序、TL-UL 多拍/poison、双口 SRAM 宏、Clos banked、GUI 分区视图、STA/功耗/布线。
- **维护口径**：任何 IR 或 RTL 变更都需重跑 CG1–CG7；修改生成逻辑须提升 `ToolVersion`，否则缓存可能误命中。

**复现命令**：

```bash
sbt -J-Xmx6G -batch "testOnly loom.IrSpec loom.GraphSpec loom.LmRouterSpec loom.LmRouter4x4Spec loom.LmRouter8x8Spec loom.ReportSpec loom.TbGenSpec loom.LoomApiSpec loom.EmitSpec loom.LmCreditSpec loom.LmQosSpec loom.LmCdcSpec loom.LmWidthSpec loom.LmNetworkSpec loom.LmPipeSpec loom.LmRouterLinkSpec loom.LmVcSpec loom.LmShellSpec loom.LmPartitionSpec loom.LmWrapperSpec"
sbt -J-Xmx6G -batch "runMain loom.LoomMain check    --config src/loom/examples/minimal_4x4.json --out generated/loom/minimal_4x4"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/minimal_4x4.json --out generated/loom/minimal_4x4"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/minimal_8x8.json --out generated/loom/minimal_8x8"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/tree_3r.json   --out generated/loom/tree_3r"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/chain_4r.json  --out generated/loom/chain_4r"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/chain_split.json --out generated/loom/chain_split --cache"
sbt -J-Xmx6G -batch "runMain loom.LoomMain check    --config src/loom/examples/mesh_2x2_xy.json"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/mesh_2x2_xy.json --out generated/loom/mesh_2x2_xy"
sbt -J-Xmx6G -batch "runMain loom.LoomMain emit     --config src/loom/examples/minimal_4x4_banked.json --out generated/loom/minimal_4x4_banked"
sbt -J-Xmx6G -batch "runMain loom.LoomMain formal   --config src/loom/examples/chain_split.json --out generated/loom/chain_split"
sbt -J-Xmx6G -batch "runMain loom.LoomMain diff     --a src/loom/examples/chain_4r.json --b src/loom/examples/chain_split.json"
sbt -J-Xmx6G -batch "runMain loom.LoomMain serve    --dir generated/loom/chain_split --port 8000 --examples src/loom/examples"
sbt -J-Xmx6G -batch "runMain loom.LoomMain blocks   --out generated/loom/blocks"
./src/loom/scripts/cg2_determinism.sh src/loom/examples/minimal_4x4.json
./src/loom/scripts/cg2_determinism.sh src/loom/examples/tree_3r.json
./src/loom/scripts/cg2_determinism.sh src/loom/examples/chain_split.json
./src/loom/scripts/cg6_manifest.sh generated/loom/chain_split
./src/loom/scripts/ci_matrix.sh
./src/loom/scripts/area_calibration.sh
./src/loom/scripts/lint.sh
./src/loom/scripts/p1_spike.sh
./src/loom/scripts/p1_p5_8x8.sh
./src/loom/scripts/p6_p8.sh
```


### 11.2 当前基线（2026-11-20，link spec 实施中）

- **已完成**：link `direction`/`dateline` 入 IR 与 C0；G1 改为 SCC 环判定（简单 router 环由 dateline 放行，显式优先、否则自动最小 link id；复杂 SCC、自环、游离切点仍拒绝）；`vc = 类 × 2 + 半区` 随拍携带；路由器每输入 6 个 VC 队列与三级仲裁；切点出端口半区 0→1；路由器新增 `outRspFwd/inRspFwd` 与按方向 BFS 的响应路由表（`routeMaster` 反向入端口、`routeMasterFwd` 前向出端口），分区边界新增前向响应端口与 async CDC；出端口固定一级寄存器、response 每跳至少一级 pipe 以打断环上组合环；新增 `ring_4.json`（单向，RTL 用例）与 `ring_3_bidir.json`。
- **验证**：23 suite / 105 用例全绿；`ring_4` 断言请求 2 跳后数据与 vc 4→5，响应沿环前向返回；`ring_3_bidir` 断言 bulk vc 4→5、非切点 lat 保持 2。
- **fabric 内部四通道拆分**：`LmRdReq/LmWrData/LmRdData/LmWrResp` 独立 bundle 与交换机；链路/分区每通道独立 pipe/CDC；顶层保留 `LmReq/LmRsp` 适配层，NI 完成拆分与合并，`LmReq` 增加 `strb`；写响应经 `wresp` 不再携带数据（测试期望改为 0 或改读）。
- **未实现**：响应通道的 dateline 半区翻转；顶层四通道端口（第二刀）；多拍 burst 的 `wresp` 聚合与 `rdata` 计数；全局位宽与窄端点 adapter 接 NI。
- **顺序**：顶层四通道端口 → 响应通道 dateline → 多拍 burst → 位宽/窄端点。
