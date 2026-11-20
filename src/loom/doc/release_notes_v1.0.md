# Loom v1.0 发布说明

> 日期：2026-11-19 | 工具版本：`loom 1.0.0` | IR schema：`loom/v1` | 状态：发布

## 1. 范围

Loom 是互联生成套件：输入一份 JSON IR，输出可综合 RTL、分区/多 top、TB 骨架、SVA 断言层、形式化结果、面积/延迟估算、签核报告与可校验的产物清单。它不生成总线协议语义本身，也不做物理设计。

## 2. v1.0 能力

- **拓扑**：单 Router（flat/banked）、任意 Router 图（CDG 无环检查）、树、2×2 XY mesh（坐标 + 转向模型 + XY 路由表）。
- **QoS**：ctl/lat/bulk 三类，VC 队列与两级类权重仲裁，包长上限与 ctl 单拍约束。
- **信用/响应**：端到端 return credit 与 slave response slot，预留原子性，背压全链路。
- **链路**：同步/异步 RouterLink、CDC FIFO/信用、1–4 级 Pipe、宽窄转换。
- **协议壳**：native 四通道、AXI4 INCR 子集、AxiLite4、TL-UL Get/Put 子集；响应路径不产生请求（G4）。
- **分区**：按成员切分、边界 ≥1 级寄存、`ports.json` 与独立分区 hash、wrapper 与 stub、P9 等价。
- **产物**：`checks.json`/`estimate.json`/`graph.json`/`manifest.json`/`signoff.md`/`tb/`/`formal/`；`--cache` 命中判定。
- **CLI**：`check/estimate/emit/formal/diff/serve/blocks/version`。
- **GUI**：零构建表单 + 拓扑 + 门禁/估算，`/api/evaluate` 与 CLI 共用检查器。
- **验证**：21 suite / 85 用例；P1–P3/P5–P8 有界证明；Verilator elaboration lint。

## 3. 版本冻结

| 项 | 冻结值 |
|---|---|
| IR schema | `loom/v1`（新增字段均可选：`partitions`、`pos`、`routing`、`banks`、`maxPacketBeats`） |
| 检查码 | `LM-C*`、`LM-G1/G4/G6–G8`、`LM-S1–S3`、`LM-W-*`、`LM-P3/P6–P8` |
| 生成物 schema | `loom/checks/v1`、`loom/estimate/v1`、`loom/ports/v1`、`loom/formal/v1`、`loom/manifest/v1`、`loom/calibration/v1` |
| 形式化门禁 | yosys-sat 有界：8×8 安全 depth 12、RR depth 20、response/CDC depth 12 |

同一 IR 两次发射逐文件一致（CG2）；`manifest.json` 记录相对路径与 SHA-256（CG6）。

## 4. 发布验证记录

- 回归：`sbt -J-Xmx10G -batch "testOnly loom.*"` → 21 suite / 85 tests 全绿。
- CG2：minimal_4x4、tree_3r、chain_split 整目录一致。
- CG5/CG6：同配置双发报告 diff 一致；219 个产物哈希校验通过，篡改可检出。
- CG7：Verilator lint 覆盖 4×4、8×8、mesh、wrapper、两个分区 top、`LmAsyncLink`。
- CG4：四个 token 模型全部 PASS（约 3 分钟）。
- 面积：nangate45 下 2×2=29,752.6、4×4=68,927.0、4×4 banked=73,217.0 µm²；8×8 代理 225,622.5 µm²。
- GUI：示例加载、字段改动自动求值、门禁告警与 C1 负例均可见。

## 5. 已知限制

- 分区顶层拒绝 async 链路；跨时钟分区与 G9 的跨边界部分留待后续。
- 无 AXI4 ID/乱序、TL-UL 多拍与 poison、非 INCR burst。
- 未实现 SNet 地址窗口译码与未映射地址错误应答；错误响应来源为协议壳/stub 的显式错误。
- P4（ordered）与深归纳/IC3 未实现；形式化均为有界证明。
- banked 为"输出分组 + 出口寄存"，交叉点不降；Clos 变体未建模。
- 面积/延迟为代理模型，无 STA、功耗、SRAM 宏与布线。
- GUI 不含拖拽布线、分区视图与浏览器内 emit。

## 6. 升级注意事项

- 旧 IR 不需要修改：新增字段全部可选，`flat`/`banks=1` 行为与之前逐位一致。
- `manifest.json` 的工具版本已升至 `1.0.0`；旧的 `generated/` 缓存会自动失效，重新发射即可。
- 若复制了 `signoff.md` 作为评审记录，请重新运行 `loom formal` 以获得 P6–P8 结果。
