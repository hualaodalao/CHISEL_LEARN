# Loom M2 出口评审材料（S14）

> 日期：2026-11-19 | 范围：S9–S13 交付 | 结论：**通过（带开放项，转入 M3）**

## 1. 出口条件与证据

| 出口条件 | 证据 | 结论 |
|---|---|---|
| 新配置 ≤ 10 分钟 | 编辑 `examples/*.json` → `loom check` < 1 s（sbt 已热）；GUI 表单改动到门禁/估算返回 < 1 s；`loom emit` 单配置 1–3 s，产物 25–176 个文件 | 通过 |
| 分区等价（P9） | `LmWrapperSpec` 单分区 vs 两分区事务 trace 完全一致；`loom diff` 对 chain_4r 与 chain_split 输出 `MATCH network contract` | 通过 |
| GUI 往返 IR 不变 | `LoomApiSpec` 断言 canonical 重新求值哈希不变；浏览器验证示例加载、门禁、告警、估算 | 通过 |
| CI 全绿 | 19 suite / 79 用例；`ci_matrix.sh` 完成 CG0/CG2/CG5/CG6；CG4 由 `--formal` 夜间运行 | 通过 |

## 2. 交付清单

- S9 协议壳：native / AXI4 INCR 子集 / AxiLite4 / TL-UL 子集；G4 响应隔离。
- S10 分区：`partitions` IR、S1–S3、边界 ≥1 级 Pipe、`ports.json` 与独立分区 hash。
- S11 组合：`LmWrapperTop`、`LmPartitionStub`（每请求恰好一个错误响应）、P9。
- S12 产物闭环：`loom formal/diff/serve`、manifest + 缓存、TB 骨架、断言清单、`signoff.md`。
- S13 GUI：Schema 驱动表单、拓扑 SVG、门禁/估算面板、`/api/evaluate`，与 CLI 共用检查器。

## 3. 验证记录

- 单测与仿真：`sbt -J-Xmx6G -batch "testOnly loom.*"` → 19 suite / 79 tests 全绿。
- 确定性：minimal_4x4、tree_3r、chain_split 两次发射整目录 `diff -r` 一致（含 manifest/signoff/tb）。
- 报告基线：同目录/同配置 `loom diff --a --b` → `MATCH reports`。
- 产物完整性：`cg6_manifest.sh` 对 219 个文件校验通过；手工改动 `estimate.json` 后正确报 `modified`。
- 有界证明：token_8x8（depth 12）与 token_rr8（depth 20）均 PASS；单次约 3 分钟。
- 浏览器：加载 minimal_4x4 → 11 项门禁 pass、crosspoints=16；`maxFanin=3` → `LM-W-TIMING`；新增未接线 Router → C1 fail 并给出具体错误。

## 4. 开放项（转入 M3）

| 项 | 影响 | 计划 |
|---|---|---|
| async 分区边界、多时钟 wrapper | 跨时钟分区无法组合 | S17（G9 一并） |
| banked crossbar | 大端口 timing 只能告警不能分解 | S15 |
| P4 ordered、P6–P8 | 形式化覆盖不完整 | S17 |
| 面积校准 | 估算仍是解析代理 | S18 |
| GUI 分区视图、拖拽布线 | 配置效率受限 | M3 可选 |
| 深证明（IC3/k-归纳） | sby+z3 不收敛，只做有界 | M3 评估 |

## 5. 风险

- `manifest --cache` 依赖工具版本号：修改生成逻辑但未提升 `ToolVersion` 会错误命中缓存；发版前由 CG6 校验拦截。
- GUI 的自动布局按最长路径分层，环图时位置只保证可读；G1 已在上游拒绝环图。
- CI 依赖 verilator/yosys 版本；`ci_matrix.sh` 不锁定具体版本，发版记录环境清单。
