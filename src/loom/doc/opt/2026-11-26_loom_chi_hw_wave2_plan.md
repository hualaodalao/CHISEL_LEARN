# Loom CHI 硬件 Wave2 实施计划（2026-11-26）

> 依据：`2026-11-25_loom_chi_hw_dependency_list.md` §1 新增 HW-08…HW-13（HW-01…07 已于 wave1 落地，formal 10/10、38 suite/161 test 绿）。
> 范围：**仅 loom/chi 硬件 + 测试 + 规格**；IR schema/装配分派/estimate 归 GEN（`gen/ChiAssemble.scala`）。
> 纪律：RTL 深改先 plan 待确认；禁手改发射物；每故事仿真绿再进下一个；sbt 单进程后台等通知；git 写需授权。

## 1. 项→故事映射与开工条件

| ID | 内容 | 故事 | 开工条件 |
|---|---|---|---|
| HW-08 | ChiPartitionTop 补 MN/DVM 接线 + HN-I 占位（对标 ChiNetworkTop） | W1 | 无外部依赖（GEN 已守护报错） |
| HW-13 | ChiNetworkTop RN 口接 ChiNi/ChiSamDecode，fabric 自含地址译码+未命中 NDERR | W2 | **契约取舍待确认**（addr 驱动 vs TgtID 驱动） |
| HW-09 | portMode AXI 边界网络顶层（桥例化 + 顶层端口形态） | W3 | 与 GEN 对齐 portMode 装配分派 |
| HW-10 | NIU QoS regulator/limiter | 条件 | 阻塞 GEN 先落 IR qosGen+CHK+传参 |
| HW-12 | ChiNi 宽度/频率 store&fwd 速率适配 | 条件 | 阻塞 GEN 暴露适配参数 |
| HW-11 | NoC 间桥 | 待立项 | IR subsystems schema 先行 |

## 2. 故事

### W1 分区 MN/DVM parity（HW-08）

**目标**：ChiPartitionTop 本地口支持 MN/HN-I，对标 ChiNetworkTop（当前非 RN/SN 节点被 tie-off 丢弃）。

**任务**：
1. `chi/ChiPartition.scala` ChiPartitionTop：本地节点循环增 MN 分支（targets=本地 RN-D NodeID，例化 ChiDvmMn，eject REQ/DAT、inject RSP/SNP、eject SnpResp，对标 ChiNetworkTop MN 接线）+ HN-I 占位分支。
2. ChiPartitionStub：MN/HN-I 节点口 tie-off 对齐（若 stub 暴露）。
3. 测试：ChiPartitionSpec 增含 MN 分区用例（DVM 经分区边界广播→收集→Comp）；GEN 守护解除后 partitions+rn-d/mn 可发射。

**验证门禁**：ChiPartitionSpec 含 MN 用例绿；既有分区/网络 suite 无回归。
**依赖**：无。**体量**：小。

### W2 网络级 SAM/NI 集成（HW-13，契约待确认）

**目标**：ChiNetworkTop RN 注入点自含地址译码 + 未命中 NDERR，闭合 HW-05 网络侧。

**契约取舍（待用户确认）**：
- **方案 A（addr 驱动，建议）**：RN 口给 addr，fabric 内 per-RN SAM 译码填 TgtID + hit，未命中 ChiNiError 就地 NDERR。符合 CHI NI 本职 + HW-13"自含"。**代价**：RN 注入契约变更，既有网络测试改 poke addr（落 SAM 窗口）。
- **方案 B（TgtID 驱动 + 边界 wrapper）**：ChiNetworkTop 保持 TgtID 驱动，SAM/NI 做独立 wrapper 由 GEN 外套。fabric 不自含译码，违背 HW-13 字面但兼容现有测试。

**任务（方案 A）**：
1. `chi/ChiNetwork.scala` ChiNetworkParams 增 per-RN SAM 表（从 ChiParams.sam 派生，每 RN 一份全表或按源过滤）。
2. `chi/ChiNetworkTop.scala` RN 分支：req.addr→ChiSamDecode.withHit→ChiNiError；命中 fwd 填 tgtId 进 inject，未命中 rsp 回 rn.rsp（NDERR）。
3. 测试：网络测试改 addr 驱动；增未命中→NDERR 网络用例。

**验证门禁**：网络 suite（addr 驱动）绿 + 未命中 NDERR 用例绿。
**依赖**：W1（可并行）。**体量**：中。**阻塞**：契约取舍确认。

### W3 portMode AXI 边界网络顶层（HW-09，高优先）

**目标**：按 portMode=axi4/both 在 RN/SN 边界例化桥，顶层端口形态随 portMode；chi 模式保持原生 flit。

**现状**：ChiAxiReadBridge/WriteBridge 已存在（RN 侧，AXI 主→CHI 裸 flit）；ChiNetworkTop io 恒 CHI flit VC bundle；桥未例化。

**任务**：
1. 新 `chi/ChiAxiNetworkTop.scala`（或边界 wrapper）：内含 ChiNetworkTop；RN 边界按 portMode 例化 ChiAxiReadBridge/WriteBridge（AXI 主侧 ar/aw/w/r/b 暴露为顶层口，CHI 侧裸 flit 接 ChiNetworkTop io.rn）；chi 模式直通 flit。
2. **SN 侧方向评估**：现有桥为 RN 侧（AXI 主→CHI）；SN axi4 需 CHI→AXI 反向桥（CHI req→AXI ar/aw，AXI r/b→CHI rsp/dat）。若需，新增 ChiAxiSnBridge（子任务，可能独立 story）。
3. 桥 VC 适配：桥 CHI 侧裸 flit 接 io.rn（pure flit，VC 由 fabric 内 inject），无需桥内 VC；确认接口对齐。
4. 与 GEN 对齐：`ChiAssemble` 按 portMode 选择例化 ChiAxiNetworkTop（axi4/both）或 ChiNetworkTop（chi）；estimate 桥接开销归 GEN。
5. 测试：新 ChiAxiNetworkSpec（AXI 主→桥→fabric→SN 端到端读/写）；portMode=chi 直通回归。

**验证门禁**：AXI 边界端到端读/写绿；chi 模式无回归；发射确定性。
**依赖**：W2（RN 口契约稳定后接桥；若 W2 方案 B 则依赖弱）。**体量**：中大。**协作**：GEN portMode 装配分派。

## 3. 依赖图

```text
W1 ─┬─ W2 ── W3 ──（GEN 装配消费）
    └────────┘
HW-10/12 条件（GEN 参数）；HW-11 待立项
```

## 4. 风险与约束

- **W2 契约变更**：addr 驱动改既有网络测试；须用户确认方案 A/B。
- **W3 SN 反向桥**：可能需新模块，体量上浮；必要时拆独立 story。
- **GEN 协作**：W3 portMode 装配分派 + estimate 归 GEN；HW-10/12 阻塞 GEN IR 参数。
- **确定性**：改 Scala 生成器，禁手改 .sv。

## 5. 验收

- HW-08/13/09 硬件落地，对应仿真绿；全量 loom.* 回归绿 + formal 全 PASS。
- protocol_spec 更新（分区 MN、网络 SAM/NI、AXI 边界顶层）；依赖清单 §6 回写落地状态。
- HW-10/12/11 条件项不阻塞本 wave。