# Loom CHI 硬件依赖清单（2026-11-25）

> 背景：IR/GEN 适配任务（见 `2026-11-25_loom_ir_gen_chi_adaptation_plan.md`）范围裁定为**仅 IR + 生成逻辑，不改 loom/chi 硬件**。
> 本清单收录适配过程中发现的、**需要硬件侧（loom/chi RTL）修正或新增**的项，以及硬件疑问。
> 作者只做记录与 GEN 侧临时行为约定，不做 RTL 修正。每项闭合后回写 `ir_gen_spec.md` §9 对应 OQ 的裁定。
>
> 用户裁定（2026-11-25）：CDC 保留（HW-04）；ChiQosMap 硬编码入疑问清单（HQ-01）；banks 用途已答复（建议退役，待确认）。
> 硬件 owner 裁定（2026-11-25，详见 §4）：HW-01…HW-07 全部已确认（HW-01 HN-I/MN 都需要；HW-02 删约束；HW-03 ChiTop 退役；HW-04/06 硬件侧提供模块 GEN 例化；HW-05 NI 就地 NDERR；HW-07 恒双向+depth 可配）；HQ-01 已答复。
> 待 GEN 回答（详见 §5）：AQ-01/AQ-02 **已答复并经用户确认（2026-11-25，见 §5.1）**，S2/S3 对齐口径已冻结。
>
> **硬件总体完成状态（2026-11-27，wave1–7 闭合）**：HW-01…HW-17（含 HW-11 NoC 间桥/多 outstanding、HW-15 子系统独立根时钟、HW-16 子系统宽度适配钩子、HW-17 outstanding 透传）+ HQ-01 + A1～A4 **全部硬件落地并验证**。loom.Chi* **23 suite/92 test 绿**，formal **12/12 PASS**，protocol_spec **v1.9.0**。明细：§11（HW-11）/§11.5（多 outstanding）/§13.1（HW-17）/§13.5（HW-15）/§17（HW-16）；wave1 §6、wave3 §6.1、wave4 §9。**GEN 待办：按 §17.4 接入 mkNet 工厂（需 Module() 包装）+ ports.json datWidth/protocol + 解 hierarchicalAdaptGuard + 联合回归**。已记录后续：axi4-in-subsystem、异 cfg 子系统、异宽多 outstanding（W17）、dvmForward=true。硬件不再阻塞。

## 1. 修正/新增项（HW）

| ID | 项 | 现状证据 | 需要的硬件动作 | 影响规格条目 | GEN 侧临时行为 |
|---|---|---|---|---|---|
| HW-01 | hn-i / mn 无网络级 elaboration 路径 | `chi/ChiParams.scala` 只装配 RN-I/SN-F；`chi/ChiNetwork.scala` 本地口只挂 RN/SN；`ChiDvmMn` 已存在但未接入网络顶层 | ChiParams/ChiNetworkParams 支持 hn-i/mn 节点：NodeID 编号段、本地口挂接、MN 与 SNP 通道接线 | SEM-01/02、IR-14、GEN-17（OQ-01） | v2 schema 解析合法；elaboration 遇 hn-i/mn 报"暂不支持"错误 |
| HW-02 | SAM 每 SN 恰一窗口 | `chi/ChiParams.scala` require `sam.length == snNodes.length` | 放开为任意窗口表（多窗口→同一 SN），或在参数装配层支持窗口合并 | IR-22–24、CHK-30（OQ-02） | 首版维持 1:1，多窗口报 CHK-30 变体错误。注：此项偏参数装配，硬件 owner 可裁定移交 GEN |
| HW-03 | 单 router CHI 顶层是 tie-off 骨架 | `chi/ChiTop.scala` 通道内部 tie-off（S2 骨架）；CURRENT emit-chi 单 router 走 ChiTop | 补全 ChiTop 数据通路，或确认退役 | GEN-01 阶段8、GEN-02（OQ-04） | v2 emit 单 router 统一走 ChiNetworkTop（支持 ≥1 router）绕开骨架；ChiTop 随 MIG-11 隔离 |
| HW-04 | CHI link 无 CDC（异步跨时钟）支持 | `ChiNetLink` 仅 pipe 字段；`ChiNetworkTop` 无异步 FIFO；v1 有 async 边界 FIFO 路径 | CHI flit 通道级异步 FIFO（REQ/RSP/SNP/DAT 各自跨域），L-Credit 跨异步域的授予/归还时序须重新证明 | IR-48–51、SEM-13、MIG-06（OQ-05，用户裁定：**CDC 保留**） | IR v2 保留 cdc/asyncFifoDepth 字段 + 校验 + 报告口径；async link 的 CHI 发射在 HW-04 闭合前报明确错误 |
| HW-05 | SAM 未命中译码到 NodeID 0 | `chi/ChiParams.scala` ChiSamDecode：unmapped → NodeID 0，无 NDERR 响应 | NI 包装或协议引擎对未命中地址返回 NDERR（Comp/DBIDResp 携带 RespErr=NDERR），不得静默路由到 0 号节点 | SEM-05、ENC-07（OQ-09） | 首版 SEM-05 标注"协议层预留"；校验保证配置不产生未命中（全覆盖窗口或显式 default 窗口） |
| HW-06 | CHI 分区发射顶层缺失 | v1 有 LmPartitionTop/LmWrapperTop/LmPartitionStub；`chi/` 无对应物 | ChiPartitionTop（边界 link 变登记过的 CHI 端口，每通道类 ≥1 pipe）、ChiWrapperTop（回接整网，async 边界依赖 HW-04）、ChiPartitionStub（对端占位） | SEM-16、GEN-01 阶段8、GEN-17 ports.json（OQ-08） | S4 交付分区门禁 + ports 报告；partitions 非空时发射报错指引，待 HW-06 闭合后接通 |
| HW-07 | fabric 参数不被 CHI 硬件消费 | `chi/ChiNetwork.scala` ChiNetworkParams 忽略 link.width/direction；`ChiRouter4` 队列 depth=2 固定 | 确认消费边界：width 应由 chi.datWidth 派生（GEN 可收敛）；direction=forward 的单向链路 CHI 路由是否支持须硬件表态；队列 depth 是否随 lcreditDepth/BDP 可配 | IR-48–51、IR-56、CHK-20/31（OQ-05 余项） | width 由 datWidth 派生校验；direction 保留（G1 需要）；depth 固定值写入报告口径 |
| HW-08 | 分区顶层无 MN/DVM 逻辑 | `chi/ChiPartition.scala` ChiPartitionTop 本地口仅 RN/SN，hn-i/mn 被丢弃（S4 勘查发现；ChiNetworkTop 已接 ChiDvmMn，分区侧未对标） | ChiPartitionTop 补 MN 实例化与 SNP 广播/收集接线（对标 ChiNetworkTop L70–89），HN-I 占位口对齐 | SEM-16、GEN-17（S4 发现） | GEN 守护：partitions 非空且含 rn-d/mn/hn-i 时 elaboration 显式报错（ChiAssemble.partitionParams），不静默发射 |
| HW-09 | portMode 不改变发射端口形态（GUI UP-04，高优先） | `ChiNetworkTop` io 恒为 CHI flit VC bundle；`ChiAxiBridge` 输出裸 flit，无 VC 适配；portMode 仅影响 node.protocol 默认与 CHK-40 | 硬件提供 AXI 边界网络顶层（或桥的 VC bundle 适配），按 portMode=axi4/both 在 RN/SN 边界实例化 ChiAxiReadBridge/WriteBridge；对标 HW-04/06"硬件出模块、GEN 例化" | IR-18/43、CHK-40、GEN-17 estimate（UP-04） | **硬件已闭合（wave2+wave3，§6/§6.1）**：ChiAxiNetworkTop per-node 混合边界（RN 读/写桥 + SN 反向桥 ChiAxiSnBridge）；GEN 做 portMode/protocol 装配分派 + estimate 桥接开销 |
| HW-10 | NIU 无 QoS 带宽调节（GUI UP-02） | IR-25–34 仅优先级+VC；NI 注入无 fixed/limiter/regulator 逻辑 | NIU 注入侧 per-node QoS generator（kind:fixed/limiter/regulator，bandwidth/saturation 参数化），GEN 按 IR qosGen 例化 | IR-25–34 扩展、CHK-34（UP-02） | **硬件已闭合（wave4，§9.2）**：ChiQosRegulator 令牌桶 + ChiNetworkParams.qosGen 自动串入 RN 入口；GEN 映射 qosGenParams 传参即可 |
| HW-11 | 无 NoC 间桥（GUI UP-07，低优先） | Loom 单 fabric；无层次子系统发射 | NoC 间桥 RTL（协议/时钟/地址域 crossing），GEN 分层发射例化 | 层次 IR（UP-07） | **硬件已闭合（wave5 W11/W12/W13，§11）**：ChiNocBridge 终结型代理（窗口转发+TxnID/NodeID 重映射+DBID 拉模型+未命中 NDERR）+ 跨时钟/异宽齿轮/store&fwd 流控隔离 + ChiDatWidth + ChiSystemTop；formal token_chi_noc_bridge 死锁自由 PASS；GEN 接 phase 2 分层发射。后续：多 outstanding、dvmForward=true |
| HW-12 | ChiNi 无速率适配（GUI UP-08，低优先） | node.dataWidth≠chi.datWidth 时 NIU 无 store&fwd 宽度/频率适配（ChiNi.scala 直通） | ChiNi 增宽度/频率失配 store&fwd 适配，参数化深度 | IR-19、ENC-01（UP-08） | **硬件已闭合（wave4，§9.3）**：ChiNiWidth 宽度 gearbox + ChiNiAdapt 频率 FIFO + ChiNetworkParams.adapt；GEN 在失配节点 DAT 边界例化接线 |
| HW-13 | 网络顶层无 SAM 译码（UP-01 勘查发现） | `ChiSamDecode` 仅被已退役的 ChiTop 消费；ChiNetworkTop 的 TgtID 由请求方驱动，ChiNi（含 HW-05 NDERR）未接入网络顶层 | ChiNetworkTop RN 本地口接入 ChiNi/ChiSamDecode（per-RN SAM 表参数化），发射 fabric 自含地址译码与未命中 NDERR | SEM-04/05、UP-01 forbid 语义 | **硬件已闭合（wave2 方案 A + wave3 A4，§6/§6.1）**：RN 入口 addr 驱动 SAM+NI 就地 NDERR；samByRn per-source 过滤/禁止对；GEN 装配 per-source routes/forbid |
| HW-14 | flat 配置 async link 无插入钩子（S7 勘查发现） | ChiNetworkTop/ChiNetworkParams 无 per-link async 字段/钩子；ChiAsyncLink 仅 ChiWrapperTop 分区边界消费；flat+cdc=async 发射 GEN 报明确错误 | ChiNetworkTop 内部 link 级 async 插入（ChiNetLink 增 asyncFifoDepth 消费 ChiAsyncLink），或提供 router-chunk 发射 API 供 GEN 边界例化 | IR-57/59、SEM-14（HW-04 余项） | **硬件已闭合（wave4 方案①，§9.1）**：ChiNetLink.asyncFifoDepth + routerDomain，ChiNetworkTop 按域 withClock + 跨域 link 自动插 ChiAsyncLink（首版节点挂 domain 0）；GEN 解除 flat+async 报错并装配 |

## 2. 硬件疑问（HQ）

| ID | 疑问 | 现状事实 | 待硬件 owner 回答 |
|---|---|---|---|
| HQ-01 | ChiQosMap 为什么硬编码 8/4？ | `chi/ChiNetwork.scala` L10–19：`classOf(qos) = qos≥8→ctl, qos≥4→lat, 否则 bulk`，`vc = class*2 + half`。CHI 的 QoS 是 4bit（0–15），**CHI 标准不定义类分段**——8/4 是迁移时为把 16 个 QoS 值塞进 Loom 既有 ctl/lat/bulk 三类而做的实现选择，不是协议要求；ChiConfig 没有对应参数，所以写死 | ① 分段阈值是否应进 ChiConfig 可配？② 三类×2 half 的 VC 方案是否保留为 CHI fabric 的正式裁定（还是改为纯 QoS 调度）？③ 若可配，IR 映射表（IR-25–28）与硬件参数如何对接？GEN 侧首版按 CHK-34 锁定默认表 8/4 |

## 3. 非硬件项（GEN 侧自理，不入本清单）

- OQ-03 CDG 顶点 `(link,通道类,方向,vc)` 展开与 per-class 依赖边：S3 的 GEN 工作量。
- OQ-07 hash 基准切换到规范化 v2：GEN 侧一次性基线失效，已建议接受。
- banks 字段：v1 实际效果仅剩输出寄存级（`LmRtl.scala` L361 注释 "No extra arbitration is introduced"），CHI router 每输出已有队列+寄存级，功能被覆盖 → 建议 v2 退役（升级器丢弃+固定警告），待用户确认。

## 4. 硬件 owner 裁定与答复（2026-11-25）

> "已确认"为用户拍板项；"待确认"为硬件 owner 建议，确认后回写 `ir_gen_spec.md` §9 对应 OQ。

| ID | 状态 | 裁定 / 建议 |
|---|---|---|
| HW-01 | 已确认 | **HN-I 与 MN 都需要网络级 elaboration 支持（用户裁定）。** ChiParams/ChiNetworkParams 增加 hn-i/mn 节点：NodeID 编号段、本地口挂接；MN 接入 `ChiDvmMn` 的 SNP 广播/收集接线（DVM 闭环）；HN-I 铺路 Home/一致性逻辑（一期可先端口+路由占位，逻辑预留）。 |
| HW-02 | 已确认 | **硬件侧删除** `ChiParams` 的 `require(sam.length == snNodes.length)`。`ChiSamDecode` 本就支持任意窗口表（多窗口→同一 tgtId 合法，last-hit 语义）。多窗口装配/校验移交 GEN；硬件仅去约束。 |
| HW-03 | 已确认 | **ChiTop 退役。** 单 router 统一走 `ChiNetworkTop`（`ChiNetworkParams.fromIr` 已支持 ≥1 router，功能等价且更完整）；ChiTop 骨架随 MIG-11 隔离。 |
| HW-04 | 已确认 | **CDC 归硬件侧（loom/chi），GEN 例化（用户裁定）。** 硬件提供每通道类（REQ/RSP/SNP/DAT）flit 级异步 FIFO + 跨域 L-Credit 模块（如 `ChiAsyncLink`），按 flit 位宽/深度参数化；GEN 仅按 IR `cdc/asyncFifoDepth` 例化接线，不手写粘合 RTL。L-Credit 跨域授予/归还须 formal 重证（对标 v1 `LmAsyncFifo/LmCdcCredit/LmAsyncLink`）。 |
| HW-05 | 已确认 | **NI 包装层对未命中地址就地回 NDERR，不进 fabric（用户裁定）。** SAM 译码增 hit 标志；未命中时读→Comp/CompData 携 `RespErr=NDERR`、写→DBIDResp/Comp 携 NDERR，不路由到 0 号节点。GEN 仍可校验全覆盖窗口。 |
| HW-06 | 已确认 | **分区顶层归硬件侧（loom/chi），GEN 例化（用户裁定）。** 硬件提供 `ChiPartitionTop`（边界 link 变登记过的 CHI 端口，每类 ≥1 pipe）/`ChiWrapperTop`（回接整网，async 边界依赖 HW-04）/`ChiPartitionStub`（对端占位），对标 v1 `LmPartition*`；GEN 按 IR partitions 例化。 |
| HW-07 | 已确认 | **恒双向，不支持单向链路**（`direction` 字段忽略/退役，`ChiNetworkTop` 恒双向交叉接线）；**队列 depth 改为可配**（随 `lcreditDepth`/BDP，去掉写死 2）；`link.width` 由 `chi.datWidth` 派生（GEN 收敛，硬件不消费）。 |
| HQ-01 | 硬件 owner 答复（已纳入实施 plan S3） | **`ChiQosMap` 当前是死代码**：全 main 仅定义、无消费者；`ChiRouter4` 纯 TgtID 路由 + 每输入单队列 + RR 仲裁，**无 per-VC 队列、无 QoS 加权仲裁**。8/4 是把 16 个 QoS 值塞进 v1 ctl/lat/bulk 三类的残留映射，非 CHI 协议要求（CHI QoS 4bit 不定义类分段）。**用户裁定恢复 QoS/VC 调度（恢复 v1 能力），纳入硬件实施 plan S3**：per-VC 队列 + 两级 WRR/RR 仲裁 + dateline 半区 + ChiQosMap 吃 cfg（阈值/权重可配）。与 GEN 的对齐见 §5 AQ-02。 |

## 5. 待 GEN agent 回答的对齐问题（AQ）

> 硬件实施 plan（`2026-11-25_loom_chi_hw_implementation_plan.md`）S2/S3 开工前需 GEN 侧确认以下口径，避免双方各做一套。GEN 回答后回写本表并同步 `ir_gen_spec.md` §9。

| ID | 关联 | 问题 | 硬件侧建议默认 | 状态 |
|---|---|---|---|---|
| AQ-01 | S2 / HW-01 / OQ-01 | **hn-i/mn 的 IR 表达与 NodeID 编号序**：① v2 schema 用端点 `role`（新增 hn-i/mn 值）还是独立 `nodeKind` 字段描述？② NodeID 编号段顺序硬件建议 **RN→SN→HN→MN**（各段内按端点序确定性分配），GEN 是否一致？③ SAM 窗口是否仅 SN 持有（hn-i/mn 不进 SAM，MN 按 DVMOp 目的 NodeID 直达）？ | RN→SN→HN→MN；SAM 仅 SN；MN 由 DVMOp TgtID 直达 | 已闭合（§5.1 答复，plan S2 已回写） |
| AQ-02 | S3 / HQ-01 / OQ-03 | **per-VC CDG 与 dateline 半区口径**：① VC 方案硬件建议 `vc = class×2 + half`（class∈ctl/lat/bulk，half∈{0,1}，nVc=6，vcWidth=3），GEN 的 CDG 顶点 `(link,通道类,方向,vc)` 展开是否按此 6 VC？② **dateline 半区分配由谁派生**——GEN 从拓扑（环 cut）算好写入路由/IR，还是硬件 router 按路由方向自行判半区？③ 类权重默认值（建议 ctl:lat:bulk = 4:2:1，对标 v1）与 QoS 阈值 8/4 放 IR 映射表（IR-25–28）还是 ChiConfig？ | 6 VC；半区由 GEN 拓扑派生（硬件消费）；权重 4:2:1；阈值进 ChiConfig 可配、IR 表锁默认 | 已闭合（§5.1 答复 + VC 泛化裁定，plan S3 已回写） |

## 6. 硬件落地状态（2026-11-25，实施 plan S1–S6）

| 项 | 落地 | 验证 |
|---|---|---|
| HW-02 删 SAM 1:1 | ChiParams require 删除 | ChiSpec 多窗口用例绿 |
| HW-03 ChiTop 退役 | emit-chi 统一 ChiNetworkTop | ChiNetworkSpec 单 router 绿 |
| HW-07 depth 可配/恒双向 | ChiNetworkParams.queueDepth；direction 不消费 | ChiNetworkSpec 绿 |
| HW-01 HN-I/MN | ChiNodeIds 段序 + ChiNetworkTop MN 接 ChiDvmMn（单端口序列化广播/按 SrcID 收集）+ HN-I 占位 + ChiRnPort TX RSP/DAT | ChiDvmNetworkSpec DVM 经网络端到端绿 |
| HQ-01 QoS/VC | ChiConfig QoS 参数 + ChiQosMap 参数化（nHalf）+ ChiChannelRouter 三级仲裁 + cut 翻半区 + ChiVcBundle sideband | ChiQosSpec 公平/翻半区/参数化绿 |
| HW-05 NI NDERR | ChiSamDecode.withHit + ChiNiError | ChiNiSpec 绿（网络级集成待 GEN） |
| HW-04 CDC | ChiAsyncLink（复用 LmAsyncLink）+ token_chi_async_credit | ChiLinkSpec 双时钟绿 + formal PASS（网络级插入待 GEN） |
| HW-06 分区 | ChiPartitionTop/Stub/WrapperTop | ChiPartitionSpec 跨分区回接 + stub 确定性绿（CLI 发射待 GEN） |
| HW-08 分区 MN/DVM | ChiPartitionTop 增 MN 分支（ChiDvmMn 广播/收集）+ HN-I 占位，对标 ChiNetworkTop | ChiPartitionSpec 含 MN 分区 DVM 端到端绿（2026-11-26 wave2） |
| HW-13 网络 SAM/NI | ChiNetworkParams.sam（fromIr 派生）；ChiNetworkTop RN 入口 sam 非空时 addr→ChiSamDecode.withHit→ChiNiError，命中填 TgtID 进 fabric、未命中就地 NDERR；sam 空退回 TgtID 直通 | ChiNetworkSpec 未命中 NDERR + 命中译码到 sn1 绿（2026-11-26 wave2，方案 A addr 驱动） |
| HW-09 AXI 边界顶层 | ChiAxiNetworkTop（内含 ChiNetworkTop，per-node 混合边界，见 §6.1 A2/A3） | ChiAxiNetworkSpec AXI 读/写 + 混合端到端绿（wave2 起，wave3 补 SN 反向桥/per-node/RX DAT）。**硬件侧余项已闭合**；GEN portMode/protocol 装配分派 + estimate 桥接开销待接（见 §7） |

formal 10/10 PASS（v1 5 + CHI 5）。wave2 后全量 23 suite/253 test 绿。网络级 async/分区 CLI 集成为 GEN 消费点（IR cdc/partitions 驱动）；网络 SAM/NI 已硬件自含（HW-13），per-source 过滤待后续。

### 6.1 Wave3 落地（2026-11-26，用户裁定 A1～A4 硬件侧一起做）

| 项 | 落地 | 验证 |
|---|---|---|
| A1 SN 写数据投递 | ChiSnPort 增 rxDat（SN RX 写数据）；ChiNetworkTop/ChiPartitionTop SN 分支 eject port.txDat→sn.rxDat；Stub/ChiTop/Wrapper/AxiNetworkTop 接线；ChiAxiWriteBridge dat.tgtId 从 DBIDResp.srcId 捕获；ChiWriteSink 增 nodeId 填 rsp.srcId | ChiAxiNetworkSpec 写用例经 fabric 投递写数据到 SN sink 并重组校验绿 |
| A2 SN CHI→AXI 反向桥 | ChiAxiSnBridge(c,snNodeId)：ReadNoSnp→AR→R→CompData；WriteNoSnp→DBIDResp→收 rxDat→AW/W→B→Comp | ChiAxiSnBridgeSpec 读/写绿（配 AXI 从模型） |
| A3 per-node 混合协议 | ChiNetNode.protocol（fromIr 归一 native→chi）；ChiAxiNetworkTop 按 protocol 拆 rnAxi/rnChi/snAxi/snChi 端口向量，axi4 挂桥/chi 直通，只暴露活跃端口 | ChiAxiNetworkSpec 混合（RN0/SN0 axi4 + RN1/SN1 chi）AXI 主→fabric→AXI 从端到端读/写含数据完整性绿 |
| A4 per-source SAM 过滤 | ChiNetworkParams.samByRn（每 RN 一份表，require 长度==nRn）；ChiNetworkTop RN 入口按源取表，空回退共享 sam；禁止对（未映射）→NDERR | ChiNetworkSpec per-source 用例：同 addr 不同 RN 译码不同 SN + 禁止对 NDERR 绿 |

wave3 后全量 **24 suite/258 test 绿**，formal 10/10 PASS。GEN 消费点：节点 protocol IR 表达/装配分派、per-source routes/forbid IR、estimate 桥接开销、网络级 async/分区 CLI 发射。HW-10/12/11 仍为 GEN 阻塞/待立项条件项。

### 5.1 GEN 答复（2026-11-25，用户确认；同步 ir_gen_spec.md §9.2）

**AQ-01**：
1. 用现有 `nodes[].kind`（IR-14，六值集合已含 hn-i/mn），不新增 role/nodeKind 第二字段；v1 role 由升级器折算（MIG-02）。HW-01 闭合前 elaboration 报"暂不支持"，schema 不挡。
2. 同意 **RN→SN→HN→MN** 段序，段内按节点声明序确定性分配；显式 `nodeId` 覆盖优先（SEM-03），冲突/越界报 CHK；总节点数 ≤ 2^nodeIdWidth。
3. 同意 **SAM 仅 SN**（IR-24/CHK-17 已如此）；hn-i 一期端口+路由占位不进 SAM；MN 由 DVMOp TgtID 直达。增补：**存在 rn-d 时须恰好 1 个 mn，mn 恒 ≤1**（CHK-19 变体；dvmEnable=true 本身不强制 mn），GEN 派生其 NodeID 作 elaboration 参数传 RN-D/MN，不走 SAM。

**AQ-02**：
1. CDG 按 D11 条件展开：**dateline 环上 link 展全 VC 顶点，非环 link 只展 half0**（不造不存在的 half1 路径）；通道类按生成配置纳入（snp 仅 dvmEnable）。依赖边：router 内同 (通道类,vc) 输入→输出；cut link 上 half0→half1 过渡边，half1 永不回 half0。
2. **半区由 GEN 拓扑派生、硬件消费**：复用 FabricGraph SCC/datelines/autoDatelines，cut 标志按 (link,方向,通道类) 写进 router 参数；硬件只在标记出口翻 0→1，不自行判半区。HW-07 恒双向后 cut 按两方向独立给（对标 v1 outCut/inCut）。
3. 权重/阈值**单一真源 = IR qos 块**（IR-25–28 mapping + classes[].weight，默认 8/4、4:2:1），GEN 装配传 ChiConfig，硬件吃 cfg——IR 是用户界面、ChiConfig 是硬件参数界面，非二选一。首版 CHK-34 锁默认表；硬件 S3 cfg 消费落地后次版本放开自定义（分段无洞无重叠、weight∈[1,64]）。

**VC 编号泛化（用户裁定，硬件 S3 必须遵守）**：`vc = classIndex*nHalf + half`，**nHalf 由拓扑路由规则派生，不得写死 ×2**——首版环 dateline nHalf=2，未来 mesh 特殊路由规则可能需要更多道数；`vcWidth = log2Ceil(nClasses×nHalf)` 随之派生。`ChiQosMap.vcOf` 现写死 `*2`/`vcWidth=3`，须参数化 nHalf/vcWidth（GEN 传入）。ir_gen_spec SEM-08/ENC-08 已按此改写。

## 7. 致 IR/GEN agent：硬件交付通知（2026-11-26，wave1+2+3 闭合）

> 硬件 owner 通知：HW-01…09/13 + HQ-01 + A1～A4 已全部落地并验证（**24 suite/258 test 绿，formal 10/10 PASS**，规格 protocol_spec v1.4.0）。下列模块已可供 GEN 装配例化；**GEN/IR 侧行动项**列于 §7.2，硬件不再阻塞这些项的 GEN 消费。HW-10/12/11 仍待 GEN IR 参数/立项（§7.3）。

### 7.1 已交付、可例化的硬件模块（契约入口）

| 模块 | 构造签名 | GEN 例化要点 |
|---|---|---|
| ChiNetworkTop | `new ChiNetworkTop(p: ChiNetworkParams)` | 全网顶层（≥1 router）。`p.sam` 非空→RN 入口 addr 驱动 SAM+NDERR（HW-13）；`p.samByRn` 非空→per-source 过滤（A4，长度须==nRn）；两者空→TgtID 直通。`p.queueDepth` 随 lcreditDepth；`ChiNetRouter.outCut` 由 GEN 拓扑派生（dateline 翻半区）。 |
| ChiNetworkParams | `fromIr(ir, cfg, snpEnable)` 或手工 copy | 字段：rn/sn/hn/mn（ChiNetNode 含 **protocol**）、routers、links、snpEnable、queueDepth、**sam**、**samByRn**。`ChiNetNode.protocol`∈{chi,axi4}（fromIr 归一 native→chi）；GEN 按 IR node.protocol 装配。 |
| ChiAxiNetworkTop | `new ChiAxiNetworkTop(p)` | **per-node 混合协议边界**（A3）。按 p.rn/p.sn 各节点 protocol 自动拆 `io.rnAxi/rnChi/snAxi/snChi` 端口向量；`rnAxiIdx/rnChiIdx/snAxiIdx/snChiIdx` 给各端口对应全局节点序。axi4 RN 挂读/写桥（REQ 经 Arbiter 仲裁），axi4 SN 挂 ChiAxiSnBridge，chi 节点直通。**GEN 按 portMode/protocol 选择例化 ChiAxiNetworkTop（含 axi4 节点）或 ChiNetworkTop（全 chi）**，并连接活跃端口子集。 |
| ChiAxiSnBridge | `new ChiAxiSnBridge(c, snNodeId)` | SN CHI→AXI 反向桥（A2），已由 ChiAxiNetworkTop 内部例化；snNodeId 填 rsp/dat.srcId。 |
| ChiPartitionTop / Stub / WrapperTop | `new ChiPartitionTop(pp)` / `ChiPartitionStub(pp)` / `ChiWrapperTop(full, parts, asyncCuts)` | 分区发射（HW-06/08）。`ChiPartitionParams(name, full, members, pipe≥1)`；分区内 MN/DVM 已对标网络。asyncCuts 内 cut 每通道插 ChiAsyncLink。**LoomMain partitions CLI 发射 + ports.json 报告待 GEN 接 IR partitions**。 |
| ChiAsyncLink | `new ChiAsyncLink(gen, depth)` | 每通道类 flit CDC（HW-04），dstClk/dstReset Bool。**网络级 async 插入待 GEN 按 IR cdc/asyncFifoDepth 例化接线**。 |
| ChiNiError / ChiSamDecode.withHit | `new ChiNiError(c)` / `withHit(addr,aw,sam,outW):(UInt,Bool)` | 已集成进 ChiNetworkTop RN 入口；GEN 无需外套 NI wrapper（HW-13 方案 A 自含）。 |
| ChiNodeIds.assign | `assign(kinds, overrides, nodeIdWidth)` | RN→SN→HN→MN 段序 + 覆盖优先（AQ-01）。 |

端口形态变更（GEN 接线须对齐）：
- **ChiSnPort 增 `rxDat = Flipped(Decoupled(ChiDatFlit))`**（SN RX 写数据，A1）；`dat` 仍为 SN TX 读数据。ChiNetworkTop/PartitionTop/Stub/Wrapper 已接线，GEN 若外套 SN 逻辑须消费 rxDat。
- **ChiRnPort 含 `txRsp/txDat`**（wave1，RN TX CompAck/SnpResp/写数据）。
- ChiAxiWriteBridge 写数据 `dat.tgtId` 从 DBIDResp.srcId 捕获（A1）；SN 侧 DBIDResp/Comp 须填 `srcId=SN NodeID`（ChiWriteSink 已增 nodeId 参数）。

### 7.2 GEN/IR 侧行动项（硬件已就绪，待 GEN 消费）

> **GEN 落地状态（2026-11-26，S7，ir_gen_spec v1.2.0）**：项 1/2/4/5 已消费落地（24 suite/262 test 绿）——portMode/protocol 分派 ChiAxiNetworkTop/ChiNetworkTop + estimate bridges 代理计数；sam/samByRn 装配（forbid 升级执行型，CHK-51 退役，connectivity 禁止对记 unreachable）；分区守护解除（HW-08 parity，含 MN 分区发射用例绿）；nHalf 拓扑派生（dateline 有=2/无=1）。**项 3 flat async 阻塞于新立 HW-14**（ChiNetworkTop 无 link 级 async 钩子；分区 async 经 asyncCuts 已工作），GEN 维持 flat+async 显式报错，未手写粘合 RTL。

1. **portMode/protocol 装配分派（HW-09/A3，高优先）**：`gen/ChiAssemble.scala` 按 IR chi.portMode + node.protocol 选择例化 ChiAxiNetworkTop 或 ChiNetworkTop，逐节点传 protocol；estimate 增桥接开销代理指标。硬件端口已按 protocol 拆分就绪。
2. **per-source SAM / routes / forbid（A4 / UP-01）**：硬件 `samByRn` 已支持每 RN 一份表 + 禁止对 NDERR；GEN 把 IR routes/forbid（ir_gen_spec v1.1.0 IR-70–74/CHK-49–51）装配成 samByRn 传入。当前 GUI UP-01 forbid 为校验型，per-source 过滤硬件已就绪可消费。
3. **网络级 async 插入（HW-04）**：GEN 按 IR cdc/asyncFifoDepth 在 link 例化 ChiAsyncLink（模块+formal 已绿）。
4. **分区 CLI 发射（HW-06/08）**：GEN 按 IR partitions 例化 ChiPartitionTop/Wrapper，发射 ports.json；含 rn-d/mn 分区守护可解除（硬件 MN/DVM 已 parity）。
5. **dateline cut / QoS cfg（HQ-01/AQ-02）**：GEN 拓扑派生 outCut 与 nHalf/vcWidth、qos 阈值/权重传 ChiConfig（硬件已参数化消费）。

### 7.3 仍待 GEN/立项的条件项（硬件不先行）

> **GEN 先行状态（2026-11-26，S8，ir_gen_spec v1.3.0）**：HW-10/HW-12 的 GEN 侧参数已落地——IR node.qosGen{kind,bandwidth,saturation}（IR-75–78/CHK-52）+ `ChiAssemble.qosGenParams`（bytesPerCycle 换算）；node.adaptFifoDepth（IR-79/CHK-53）+ `ChiAssemble.adaptParams`（失配判定）；estimate/API additive qosGen/adapt 键（声明口径，无 RTL 效果）。**硬件可开工 HW-10/HW-12**，消费上述参数。HW-11 仍待立项。

- **HW-10 NIU QoS regulator/limiter**（UP-02）：~~待 GEN 先落 IR qosGen~~ **GEN 参数已就绪（S8）**，硬件做注入调节，消费 QosGenParams。
- **HW-12 ChiNi 速率适配**（UP-08）：~~待 GEN 暴露适配参数~~ **GEN 参数已就绪（S8）**，硬件做 store&fwd，消费 AdaptParams。
- **HW-11 NoC 间桥**（UP-07）：**已立项（用户 2026-11-26）**，GEN phase 1 落地（v1.5.0），硬件行动见 §10。

> 对齐口径冻结见 §5.1（AQ-01/02）；硬件实施细节见 protocol_spec v1.4.0 §6.9/6.17/6.18/6.20/6.21。GEN 落地后请回写本表 §6/§7 对应项状态。

## 8. 致硬件 agent：行动通知（2026-11-26，GEN S7/S8 落地后）

> GEN/IR 侧已就绪（ir_gen_spec v1.3.0，commit 80f1e46，24 suite/279 test 绿）。下列三项硬件行动**不再被 GEN 阻塞**，按优先级开工；每项落地后回写 §6 状态并通知 GEN 接消费。HW-11（UP-07）仍待用户立项，不在本通知。

### 8.1 HW-10 NIU QoS 带宽调节（UP-02，中优先）

- **消费契约**：`loom.gen.ChiAssemble.QosGenParams(nodeId: Int, kind: String, bytesPerCycle: Double, saturationBytes: Int)`，由 `ChiAssemble.qosGenParams(v2)` 逐节点产出（仅声明 qosGen 的节点有条目）。
- **语义（SEM-18）**：kind ∈ fixed/limiter/regulator；`bytesPerCycle = bandwidth(MB/s) ÷ 节点时钟 freqMHz`（GEN 已换算，硬件直接用）；fixed=恒定速率上限（saturation 恒 0，CHK-52 保证）；limiter=峰值上限+突发；regulator=持续速率+突发（saturationBytes=突发 allowance）。
- **硬件动作**：NI 注入侧按参数做速率调节（令牌桶/信用类实现自定，须 formal 或仿真证明不丢 flit、速率收敛于声明值）；参数进 ChiConfig 或 per-node 端口由硬件定，**定好后通知 GEN 装配接线**。
- **验收**：仿真对拍声明速率（±突发 allowance）；未声明节点行为不变（现有 279 test 不回退）；protocol_spec 增对应 block 规格。
- **IR/校验已冻结**：IR-75–78、CHK-52；estimate/API 已 additive 暴露 `qosGen`（声明口径），硬件落地后 GEN 把 assumptions 的"待 HW-10"改为生效口径。

### 8.2 HW-12 ChiNi 速率适配（UP-08，低优先）

- **消费契约**：`loom.gen.ChiAssemble.AdaptParams(nodeId, dataWidth, datWidth, clock, routerClock, fifoDepth, mismatch: Boolean)`，由 `ChiAssemble.adaptParams(v2)` 产出（失配节点+显式深度冻结节点）。
- **语义（SEM-19）**：失配 = dataWidth≠datWidth（宽度）或 clock≠routerClock（频率）；fifoDepth ∈ [2,64] 默认 4（IR-79/CHK-53 已校验）。
- **硬件动作**：ChiNi 增 store&fwd 宽度/频率适配（深度=fifoDepth）；AXI 边界宽度适配仍归桥接层（PS §5 F8），不重复实现。
- **验收**：宽度/频率失配用例数据逐位一致、无丢 flit；匹配节点零开销直通不回退；protocol_spec 增规格。

### 8.3 HW-14 flat 配置 async link 钩子（S7 勘查退回，中优先）

- **现状**：ChiNetworkTop/ChiNetworkParams 无 link 级 async 字段；ChiAsyncLink 仅 ChiWrapperTop 分区边界消费。flat+cdc=async 配置 GEN 报明确错误（不写粘合 RTL，HW-04/06 分工裁定）。
- **硬件动作（二选一，定好通知 GEN）**：① ChiNetLink 增 asyncFifoDepth（0=sync），ChiNetworkTop 内部 link 级例化 ChiAsyncLink（跨域 L-Credit 复用 token_chi_async_credit 证明）；② 提供 router-chunk 发射 API，GEN 在 chunk 边界例化。
- **验收**：flat 双时钟 ring/chain 发射+仿真绿；formal 跨域信用 PASS；GEN 随后解除 flat+async 报错并接 IR cdc/asyncFifoDepth 装配。

> 通用纪律：硬件模块参数化可复用、GEN 只例化接线（HW-04/06 裁定）；新增 cfg 字段须同步 protocol_spec 与 ir_gen_spec ENC/IR 编号（GEN 负责 IR 侧回写）；每 story 仿真绿再进下一个。

## 9. 硬件答复：wave4 落地（2026-11-26，回应 §8）

> HW-14/10/12 已全部落地并验证（protocol_spec v1.5.0；全量 loom.* 回归绿 + formal 10/10）。GEN 可解除对应报错并接消费。参数接口如下（硬件不 import loom.gen，GEN 把 ChiAssemble 参数映射到 chi 侧 case class）。

### 9.1 HW-14 flat async（裁定方案①，已落地）
- `ChiNetLink` 增 `asyncFifoDepth: Int = 0`（0=sync 直连，≥2 且 2 幂=异步 FIFO 深度）。
- `ChiNetworkParams` 增 `routerDomain: Seq[Int]`（每 router 时钟域索引，0=顶层模块时钟；空=全 0）；派生 `domainOf/nDomain/hasAsync`。
- `ChiNetworkTop` 按域 `withClockAndReset` 例化 router；跨域 link 每方向每通道类例化 ChiAsyncLink（源域=TX router 域，dstClk=RX router 域）；新增 `io.domClk/domReset = Vec(nDomain-1, Input Bool)` 暴露非 0 域时钟（nDomain=1 时为空 Vec，既有接线不变）。
- **首版约束**：本地节点仅挂 domain 0 router（require 守卫）；非 0 域节点边界 CDC 待后续。flat 双时钟 chain（r1/r2 transit domain1）RN→SN 仿真绿。
- **GEN 行动**：解除 flat+async 报错；按 IR cdc/asyncFifoDepth 填 ChiNetLink.asyncFifoDepth + routerDomain，接 domClk/domReset 外部时钟。

### 9.2 HW-10 NIU QoS 调节（已落地）
- 新模块 `ChiQosRegulator(c, bytesPerCycle: Double, saturationBytes: Int)`：令牌桶，scale=2^10 定点累加，capacity=max(perFlit, saturationBytes)，反压不丢 flit，速率收敛声明值（fixed sat=0 严格速率；limiter/regulator 突发）。
- `chi.ChiQosGen(nodeId, kind, bytesPerCycle, saturationBytes)`（镜像 GEN QosGenParams）；`ChiNetworkParams.qosGen: Seq[ChiQosGen]`。ChiNetworkTop RN 入口按 nodeId 匹配自动串入（SAM/NI 后、inject 前），未声明直通。
- **GEN 行动**：把 `ChiAssemble.qosGenParams(v2)` 映射成 chi.ChiQosGen 传 p.qosGen；estimate assumptions「待 HW-10」改生效口径。

### 9.3 HW-12 NIU 速率适配（已落地，GEN 例化）
- 新模块 `ChiNiWidth(inW, outW)`：store&fwd 宽度 gearbox，任意整数比（2 幂），降宽低位先拆/升宽低位先拼，last 随末拍，逐位一致；inW==outW 零开销直通。
- `ChiNiAdapt(inW, outW, fifoDepth, async)`：ChiNiWidth + 可选 ChiAsyncLink(ChiNiBeat(outW), fifoDepth) 跨频；dstClk/dstReset。
- `chi.ChiAdapt(nodeId, dataWidth, datWidth, fifoDepth, mismatch)`（镜像 GEN AdaptParams）；`ChiNetworkParams.adapt: Seq[ChiAdapt]`。
- **分工**：硬件出参数化模块（HW-04/06 裁定）；**GEN 在失配节点 DAT 边界例化 ChiNiAdapt**，负责节点 DAT flit↔ChiNiBeat 映射与 dataId 对齐；网络顶层自动插入待 per-node 宽度端口（后续）。AXI 边界宽度适配仍归桥接层。
- **GEN 行动**：把 `ChiAssemble.adaptParams(v2)` 映射成 chi.ChiAdapt；在失配节点例化接线。

> 验证：ChiQosRegulatorSpec（速率收敛/缩放/突发）、ChiNiAdaptSpec（256↔128、512↔128、匹配直通逐位一致）、ChiNetworkSpec flat 双时钟 chain 绿。HW-11（UP-07）仍待用户立项。

## 10. 致硬件 agent：HW-11 立项行动通知（2026-11-26，用户批准 UP-07）

> **UP-07 已立项（用户 2026-11-26）**。GEN phase 1 已落地（ir_gen_spec v1.5.0：IR-80–93 subsystems[]/nocLinks[]、SEM-20、GEN-25、CHK-55–58；subsystemParams/BridgeParams 装配、gatesBySubsystem、estimate/API additive；分层发射守护待 HW-11）。硬件交付 ChiNocBridge 后 GEN 接 phase 2 分层发射。

### 10.1 消费契约（GEN 已冻结）

`loom.gen.ChiAssemble.BridgeParams(fromSub: String, toSub: String, fromRouter: String, toRouter: String, windows: Seq[(Long, Long)], asyncFifoDepth: Int, dvmForward: Boolean, fromDatWidth: Int, toDatWidth: Int)`，由 `ChiAssemble.bridgeParams(v2)` 逐 nocLink 产出；每子系统网络参数由 `ChiAssemble.subsystemParams(v2): Seq[(String, ChiNetworkParams)]` 产出（独立 NodeID 空间/SAM/chi/时钟域）。

### 10.2 硬件动作（ChiNocBridge + 系统顶层）

1. **协议代理**：桥在 from 侧 NoC 表现为端点节点（收 REQ，挂 fromRouter 本地口），在 to 侧表现为 RN（重发 REQ，挂 toRouter 本地口）；TxnID/NodeID 两侧独立编号，桥内映射表追踪，响应合并回源。桥是**终结依赖的端点**（G1 按子系统独立证明，桥切断跨 NoC 环——SEM-20）。
2. **窗口转发**：按 windows 表把 from 侧非本地地址转发 to 侧（地址不重映射，窗口是 to 侧 SAM 区域的别名——CHK-57 保证覆盖）；同源转发窗口两两不重叠、不压 from 本地 SAM（CHK-56 保证）。
3. **跨时钟/宽度**：asyncFifoDepth≥2 时每通道类 ChiAsyncLink（复用 HW-04 证明）；fromDatWidth≠toDatWidth 时 DAT 宽度齿轮（复用 ChiNiWidth）。
4. **流控隔离**：L-Credit/P-Credit/DBID 两域独立终结，桥内 store&fwd 缓冲，一侧反压不拖死另一侧；**跨域无死锁须 formal token 证明**（对标 token_chi_async_credit 纪律：结构性门控）。
5. **DVM 策略**：dvmForward=false（默认）DVMOp 不跨桥，各 NoC 自己的 MN 自治；true 时转发 SnpDVMOp 并收集对侧 SnpResp。
6. **系统顶层**：硬件提供 `ChiSystemTop`（或等价）例化各子系统 ChiNetworkTop + 桥 + 顶层端口聚合；GEN 只例化接线（HW-04/06 分工裁定）。

### 10.3 验收

- 双子系统（各 ≥1 RN + ≥1 SN）经桥端到端读/写仿真绿（含数据逐位一致）；异宽（128↔256）与异频（async）用例绿。
- 跨域死锁 formal PASS；未声明桥的子系统行为不回退。
- protocol_spec 增桥 block 规格（逐子模块六要素）；落地后回写本清单 §6/§10 并通知 GEN 解除发射守护、接 phase 2。

> GEN phase 2（HW-11 闭合后）：LoomMain 分层发射（subsystem tops + 桥 + 系统顶层 + ports/manifest）、estimate 桥接开销、GUI F7 subsystem 层消费。

## 11. 硬件答复：HW-11 闭合（2026-11-26，wave5 W11/W12/W13 落地，回应 §10）

> **HW-11（UP-07）硬件全部落地并验证**。GEN 可解除分层发射守护、接 phase 2。protocol_spec **v1.6.0**（新增 LM-F028/F029/F030 + §6.24/6.25/6.26）。

### 11.1 已交付模块（契约入口，chi 侧镜像 GEN 参数）

- `chi.ChiNocBridge(c, windows: Seq[(Long,Long)], fromNodeId, toNodeId, asyncFifoDepth=0, toDatWidth=0, dvmForward=false, outstanding=1)`：from=ChiSnPort(c)、to=ChiRnPort(toCfg)、dstClk/dstReset。终结型代理：窗口转发（地址不重映射）+ TxnID/NodeID 重映射 + DBID 拉模型写 + 未命中就地 NDERR。asyncFifoDepth≥2 每 to 侧通道类 ChiAsyncLink（TX from 域跨出、RX dstClk 域跨回）；toDatWidth≠c.datWidth 时 DAT 路径 ChiDatWidth 齿轮；store&fwd 两域独立反压。**outstanding 参数化多事务**（W14/W15：ChiIdPool to 侧 TxnID 池 + 每事务上下文表 + REQ 流水接收 + 读 DAT 逐 flit 转发 + 写数据 {fromSrc,fromTxn} CAM + RR 完成仲裁 + 读/写释放队列；默认 1 向后兼容，>1 require 匹配 DAT 宽度；异宽多 outstanding W17 后续）。**dvmForward=false**（各 NoC MN 自治；require 拒绝 true，跨桥 DVM 转发为后续）。
- `chi.ChiDatWidth(inCfg, outCfg)`：DAT flit 宽度齿轮，store&fwd 64B 字重组 + DataID 重算，元数据逐字段保留，128/256/512 任意比逐位一致。
- `chi.ChiSystemTop(subsystems: Seq[ChiNetworkParams], bridges: Seq[ChiBridgeLink])`：聚合子系统 ChiNetworkTop + 桥 + 端口聚合（freeRn/freeSn 扁平 Vec + domClk/domReset）。`chi.ChiBridgeLink(fromSub: Int, fromSn: Int, toSub: Int, toRn: Int, windows, asyncFifoDepth=0, toDatWidth=0, dvmForward=false)` 镜像 GEN BridgeParams（GEN 用 String 子系统名/路由器名，硬件用序号索引；GEN 装配时映射）。桥消费 fromSub.sn[fromSn] 与 toSub.rn[toRn] 两槽，不外露。**v1 各子系统 uniform ChiConfig**（DAT 宽差由桥 toDatWidth 承载）。

### 11.2 GEN 行动项（phase 2 解守护）

1. **桥端点节点装配**：GEN 在子系统 params 增桥从/至端点节点（fromSub 增 SN 槽、toSub 增 RN 槽），分配 NodeID/localPort，接 ChiSystemTop.bridges 的 fromSn/toRn 序号（HW-04/06 分工：硬件出顶层模块，GEN 例化接线）。
2. **分层发射**：LoomMain 发射 subsystem tops + ChiNocBridge + ChiSystemTop + ports/manifest；estimate 桥接开销；GUI F7 subsystem 层消费。
3. **参数映射**：BridgeParams(String 名) → ChiBridgeLink(Int 序号)；subsystemParams 序与 bridges.fromSub/toSub 索引对齐。

### 11.3 验证

- ChiNocBridgeSpec 5 例（读代理/写 DBID 拉模型逐位一致/窗口未命中 NDERR/异宽 256→128 端到端/异频双时钟读）；ChiDatWidthSpec 4 例（128↔256、512↔128 逐位一致）；ChiSystemSpec 2 例（双子系统经桥端到端读 + 写）。
- formal **token_chi_noc_bridge.sv**：两域缓冲有界 + 响应路径独立于请求缓冲（无循环等待=死锁自由），yosys SAT PASS（已入 LoomMain formal 清单，11/11）。
- 未声明桥的子系统行为不回退（ChiSystemTop 仅消费声明槽，其余端口直通）。

### 11.4 后续（非阻塞，已记录 protocol_spec §14.1）

- dvmForward=true 跨桥 SnpDVMOp 转发 + 对侧 SnpResp 收集（需 from 侧 SNP 注入路径）；ChiSystemTop 异 cfg 子系统（per-subsystem DAT 宽端口）；异宽多 outstanding（W17，per-txn DAT 重组缓冲）。

### 11.5 wave6 桥多 outstanding 闭合（2026-11-26，W14/W15/W16）

> **桥多 outstanding 硬件落地并验证**（用户裁定：默认 1、{fromSrc,fromTxn} CAM、RR 仲裁、v1 限匹配宽度）。protocol_spec **v1.7.0**。GEN `BridgeParams` 增 `outstanding: Int` 字段（IR nocLinks[].outstanding，CHK ≤ 2^txnIdWidth），装配传入 ChiBridgeLink.outstanding。

- 验证：ChiNocBridgeSpec **9 例**（原 5 + 多读 3 并发恢复各源 TxnID + 池耗尽反压回收 + 多写 2 并发 CAM 路由完整性 + 读写混合并发共享释放仲裁）；ChiDatWidthSpec 4 + ChiSystemSpec 2 不回退。
- formal **token_chi_noc_bridge_mo.sv**：outstanding≤DEPTH + exact-once（relCnt≤allocCnt）+ 响应独立死锁自由，yosys SAT PASS（入 LoomMain 清单，**12/12**）。
- GEN 行动：BridgeParams.outstanding 传参 + IR/CHK 字段（硬件已就绪，默认 1 不改变现有装配行为）。

## 12. GEN 答复：HW-11 phase 2 落地 + HW-15/16/17 新立（2026-11-26，回应 §11/§11.5）

> **UP-07 GEN phase 2 落地**（ir_gen_spec **v1.6.0/v1.7.0**）：分层发射守护解除，同步桥系统经 `ChiSystemTop` 端到端发射并仿真验证。§11.2 三行动项全部完成；wave6 outstanding 按声明口径消费（系统层透传待 HW-17）。

### 12.1 已落地（GEN 侧）

1. **桥端点装配（SEM-20a）**：`ChiAssemble.systemProjections/systemParams`——每 nocLink 在 fromSub 增合成 sn-f 槽（挂 fromRouter）、toSub 增合成 rn-i 槽（挂 toRouter），保留前缀 `__noc_<from>_<to>_<i>_sn/rn`（声明名冲突报错）；NodeID 按段确定性续编；from SAM 增补转发窗口→桥 SN；槽序号按**最终 NodeID 排序向量**查名定位（不假设插入序）。
2. **分层发射（GEN-25a）**：LoomMain `emitSystem` 发射 ChiSystemTop.sv + 逐子系统 `subsystems/<name>/chi_nodes.json` + 系统 `ports.json`（freeRn/freeSn 排除桥槽）+ manifest/signoff/tb；estimate additive `bridges.noc`；CG2 逐文件确定性。
3. **门禁**：CHK-59（dvmForward=true / async 非 2 幂）、CHK-60（**装配后 ChiConfig 一致，含拓扑派生 nHalf**——ring/chain 子系统 chi 块相同但 nHalf 不同亦拒绝）、CHK-61（outstanding ∈ [1, 2^txnIdWidth]）。
4. **验证**：GEN 级 golden sim（IR→systemParams→ChiSystemTop）跨桥读 CompData 逐位一致 + DBID 拉模型写重组逐位一致（Verilator）；全量回归绿（计数见提交）。

### 12.2 新立硬件需求（GEN 不实施，维持明确 emit 守护）

| ID | 缺口（源码引用） | 需求 | GEN 现状 | 优先级 |
|---|---|---|---|---|
| HW-15 | `ChiSystemTop.scala` L65–71：async 桥 dstClk 硬接 `io.domClk(0)`——全单域子系统时 nDomExtra=0 向量为空（elaboration 失败），且 domClk(0) 一般不是 to 侧时钟 | 每桥独立 dstClk 选择（ChiBridgeLink 增时钟域索引或系统顶层专用桥时钟端口），并保证非空 | emit 守护：nocLinks 含 asyncFifoDepth≥2 ⇒ 报错待 HW-15；check/estimate 照常 | **硬件已闭合（2026-11-27 方案A，§13.5）**：根因是子系统无独立根时钟（§13.2）；subDomain + io.subClk/subReset，桥 fromSub 域例化/dstClk=toSub 根时钟，同步同域/async 异域 require；GEN 驱动新端口 + ports.json 时钟标注后可解守护 |
| HW-16 | `ChiSystemTop.scala` L44：直例化 `ChiNetworkTop`，无平铺路径 `ChiAdaptedNetworkTop`（GEN-24）等价包装层 ⇒ 子系统内宽度失配 chi 节点无齿轮 | ChiSystemTop 按子系统 adapt 需求集成 ChiNiAdapt（或暴露 GEN 包装钩子） | emit 守护：任一子系统 `ChiAdaptedNetworkTop.needed` ⇒ 报错待 HW-16；匹配宽度不受影响 | **硬件已闭合（2026-11-27 薄包装变体，§17）**：ChiNetworkIO/ChiNetworkLike/ChiNetworkUniform + mkNet 工厂 + free 槽 MixedVec 边界 + ChiNodeWidths；ChiNetworkTop/GEN 包装零改动；GEN 按 §17.4 接入（工厂需 Module() 包装）后解守护 |
| HW-17 | §11.5 契约声称 `ChiBridgeLink(..., outstanding=1)`，但 `ChiSystemTop.scala` 未更新——ChiBridgeLink 无 outstanding 字段、ChiNocBridge 例化未传参 | ChiBridgeLink 增 `outstanding: Int = 1` 并透传 ChiNocBridge（含 >1 匹配宽度 require 对齐） | IR-94/CHK-61/BridgeParams.outstanding 已就绪（声明口径）；systemParams 暂默认 1，字段落地后一行消费 | **硬件已闭合（2026-11-27，§13.1）**：字段+透传落地，ChiSystemSpec HW-17 用例绿；GEN systemParams 一行消费 |

### 12.3 边界与后续

- v1 分层发射：**同步桥 + uniform ChiConfig + 匹配宽度 chi 节点**；async/异宽/异 cfg 均有明确守护或 CHK，不静默发射错误 RTL。
- 桥多 outstanding 异宽（W17）、dvmForward=true、异 cfg 子系统仍为硬件后续（§11.4），GEN 不预判字段。
- GUI F7 可消费：subsystem 层 + 分层发射 + bridges.noc；HW-15/16/17 相关控件待上游落地（GUI 计划 §6 已同步）。

## 13. 硬件答复：HW-17 闭合 + HW-15/16 勘查与方案待裁定（2026-11-27，回应 §12.2）

### 13.1 HW-17 已闭合（wave6 收口遗漏，高优先）

> `ChiBridgeLink` 增 `outstanding: Int = 1` 并透传 `ChiNocBridge`（含 >1 匹配宽度 require 由桥侧统一校验）。**GEN 可解除 outstanding 系统层透传缺口，`systemParams` 一行消费 `BridgeParams.outstanding`**。

- 改动：`chi/ChiSystemTop.scala` ChiBridgeLink 增字段 + 桥例化传参。默认 1，不改变现有装配行为。
- 验证：新增 `ChiSystemSpec` "pass ChiBridgeLink.outstanding through to the bridge (HW-17)" —— 双子系统 depth-2 桥在首笔读未返回前即接收第二笔 REQ（系统边界可观测），两笔各 nFlits flit 重组逐位一致。ChiSystemSpec 3 + ChiNocBridgeSpec 9 = **12 例绿**。
- GEN 行动：`systemParams` 把 `BridgeParams.outstanding` 传入 `ChiBridgeLink.outstanding`（IR-94/CHK-61 已就绪）。

### 13.2 HW-15 勘查结论：根因不是 dstClk 索引，而是子系统无独立根时钟

**核实源码**：
- `ChiNetworkTop.scala` L32：`clkOf(0) = clock`（模块时钟），domain≥1 才用 `io.domClk`。
- `ChiSystemTop.scala` L44：所有子系统在**顶层同一时钟域**例化 ⇒ 各子系统 domain-0 都是同一顶层时钟。
- `ChiNetworkTop.scala` L22：HW-14 首版 require 本地节点挂 domain-0 router。

**推论**：桥 from/to 两端**当前必然同时钟**。仅把 `dstClk` 改接某 `io.domClk(k)` 不能解决问题——to 侧子系统逻辑仍跑顶层时钟，跨域 FIFO 两端时钟与被跨越逻辑不一致，属**语义错误**。§12.2 描述的"domClk(0) 一般不是 to 侧时钟"成立，但根因是子系统缺独立根时钟。

**方案（待用户裁定，详见 `doc/opt/2026-11-27_loom_chi_hw_wave7_plan.md` §1）**：
- **A（推荐）**：`ChiSystemTop` 增 `subClk/subReset`，子系统按各自根时钟 `withClockAndReset` 例化；桥在 fromSub 域例化、`dstClk := subClk(toSub)`。async 桥两端真实异钟。同步桥 require 两子系统同时钟。**GEN 影响**：emitSystem 需驱动新增系统级 subClk/subReset；同步桥系统可全接同一时钟保持现状；freeRn/freeSn 端口时钟归属需在 ports.json/spec 明确。
- **B（不推荐）**：仅暴露每桥 dstClk 端口——不解决根因，只把错误接线变为外部可控的错误接线。
- **C（低成本兜底）**：仅修 `nDomExtra=0` 空向量 elaboration 崩溃为明确 require 报错，async 跨子系统能力留后续；GEN 守护不变。

### 13.3 HW-16 方案（待用户裁定）

现状 `ChiSystemTop` 直例化 `ChiNetworkTop`，子系统内失配 chi 节点无齿轮。GEN 已有 `ChiAdaptedNetworkTop`（GEN-owned，MixedVec 按节点宽度暴露端口）；硬件不得 import loom.gen（既定边界）。

- **A（推荐）**：硬件定义共享端口 trait（rn/sn MixedVec + domClk/domReset），`ChiNetworkTop` 实现；`ChiSystemTop` 增可选工厂 `mkNet`（默认 ChiNetworkTop）。**需 GEN 让 `ChiAdaptedNetworkTop` 实现该 trait 并在 emitSystem 传工厂**（跨侧契约，GEN 一行接入）。端口 Vec→MixedVec 化会影响桥接线与 ports.json 形态，需同步。
- **B（不推荐）**：硬件在 ChiSystemTop 内自插 ChiNiAdapt——与 HW-12 裁定（GEN 在节点边界例化）冲突，两处适配逻辑重复易漂移。
- **C（体量大）**：ChiSystemTop 退化为仅桥聚合器，GEN 在外层例化各子系统顶层并接线；ownership 最干净但 emitSystem 需重写。

### 13.4 接下来的动作

- HW-17：**已闭合**，GEN 可消费。
- HW-15/16：属深改，已出 wave7 plan 待用户裁定方案（HW-15 A/B/C、HW-16 A/B/C、是否本轮做 HW-16、同步桥同时钟 require）。裁定后按 W18（HW-15）→ W19（HW-16）→ W20（收口）推进，每故事仿真绿再进下一个。
- GEN 守护建议维持现状直至对应方案落地；HW-15 方案 A 落地后 async 守护可解除（需 GEN 驱动 subClk/subReset），HW-16 方案 A 落地后适配守护可解除（需 GEN 实现 trait + 传工厂）。

### 13.5 HW-15 已闭合（2026-11-27，用户裁定方案 A，W18）

> **子系统独立根时钟落地**。`ChiSystemTop` 增 `subDomain: Seq[Int] = Seq.empty` + `io.subClk/subReset`；**GEN 可解除 async 桥 emit 守护**（需按下述契约驱动新端口）。protocol_spec **v1.8.0**。

**契约（GEN 消费）**：
- `ChiSystemTop(subsystems, bridges, subDomain: Seq[Int] = Seq.empty)`：subDomain 给每子系统指派系统级时钟域，**0 = 模块时钟**（沿用 routerDomain 惯例），长度 = subsystems 数；空则全 0（默认，无额外端口，行为与现有单时钟系统一致）。
- `io.subClk/subReset = Vec(nSubDom-1, Input(Bool()))`，nSubDom = subDomain.max+1。GEN 按域索引驱动（域 0 无需端口）。
- 各子系统网络在**自己的根时钟**下例化；子系统 domain-0 = 其根时钟，domain≥1 仍接 `io.domClk`（偏移不变）。
- 桥在 **fromSub 域**例化，`dstClk := toSub 根时钟` ⇒ async 桥真实跨异钟（修 §13.2 语义根因）。
- **require：同步桥（asyncFifoDepth=0）两端 subDomain 相同；async 桥（≥2）两端 subDomain 不同**。后者同时保证 subClk 向量非空（修空向量 elaboration 崩溃）。
- **端口时钟归属**：`io.rn/io.sn` 第 k 项属于其自由槽所属子系统的时钟域 ⇒ **GEN ports.json 需标注每端口时钟域**。

**GEN 行动项**：
1. IR/装配：子系统级时钟域声明（可复用 subsystem.clock 或新增 subDomain 派生），映射为 `subDomain` 序号向量；CHK 建议增"同步桥两端同域 / async 桥两端异域"静态校验（与硬件 require 对齐，提前报错）。
2. emitSystem：驱动 `subClk/subReset`（域 0 接顶层 clock/reset）；ports.json 增端口时钟域标注。
3. 解除 async 桥 emit 守护（`nocLinks.asyncFifoDepth≥2` 不再报错），改由上述 CHK 保证域指派正确。

**验证**：ChiSystemSpec **4 例绿**——同步桥端到端读/写、outstanding 透传（HW-17）、**两子系统异根时钟（subDomain=Seq(0,1)）经 asyncFifoDepth=4 桥端到端读逐位一致**（远端存储例化在 subClk 域，复位期翻转远端时钟）。默认全 0 路径不回退。

### 13.6 HW-16 待 GEN 对齐（用户裁定方案 A，本轮不做）

硬件侧方案已定：定义共享端口 trait（rn/sn MixedVec + domClk/domReset），`ChiNetworkTop` 实现；`ChiSystemTop` 增可选工厂 `mkNet`（默认 ChiNetworkTop）。**需 GEN 让 `ChiAdaptedNetworkTop` 实现该 trait 并在 emitSystem 传工厂**。因端口 Vec→MixedVec 化会影响桥接线与 ports.json 形态，且需 GEN 侧同步接入，用户裁定**待 GEN 对齐后开工**（W19）。GEN 适配 emit 守护维持现状。

## 14. GEN 答复：HW-16 方案 A 对齐契约（2026-11-27，回应 §13.6）+ HW-15/17 消费

### 14.1 HW-16 方案 A：GEN 对齐契约（同意方案 A，W19 可开工）

GEN 确认方案 A 并给出跨侧契约细节（硬件定 trait/工厂，GEN 实现接入；硬件不 import loom.gen 边界不变）：

1. **trait 归属与形态**：`loom.chi.ChiNetworkIO extends Bundle`，`rn/sn: MixedVec[Data]` + `domClk/domReset: Vec[Bool]`。**ChiNetworkTop 的 Vec 须 MixedVec 化**（Vec[ChiRnPort] 不是 MixedVec[Data] 子类型，无法直接 override）；元素具体类型 = `Flipped(new ChiRnPort(cfgOf(node)))`（chi 节点，逐节点宽度）或 `ChiAxiRnPort(c)`（axi4 节点，非 Flipped）——与 GEN `ChiAdaptedNetworkTop` 现有 io 形态逐元素一致（见 ChiAdaptedNetworkTop.scala io 定义），uniform ChiNetworkTop 即全 fabric 宽特例。
2. **工厂签名**：建议 `abstract class ChiNetworkLike(val p: ChiNetworkParams) extends Module { val io: ChiNetworkIO }`；`ChiSystemTop(..., mkNet: ChiNetworkParams => ChiNetworkLike = new ChiNetworkTop(_))`。默认工厂保持现有行为不回退。GEN 侧 `ChiAdaptedNetworkTop extends ChiNetworkLike`，emitSystem 在任一子系统 `ChiAdaptedNetworkTop.needed` 时传 `p => new ChiAdaptedNetworkTop(p)`（可统一全子系统传工厂，matched 子系统行为等价）。
3. **桥槽不变式（GEN 保证）**：SEM-20a 合成桥端点恒为 fabric 宽原生 chi（无 adapt 行）⇒ 桥消费槽在两种顶层下端口形态相同，`nets(fromSub).io.sn(fromSn) <> br.io.from` 接线不变；适配只影响 free 槽。
4. **系统边界 MixedVec 化**：io.rn/io.sn 须按 free 槽逐端口类型构造 MixedVec（失配节点宽度/axi4 协议外溢到系统边界）。**ports.json 同步**：GEN 逐 free 端口增 `datWidth`/`protocol` additive 键 + HW-15 `clockDomain` 标注；GUI F7 后续消费。
5. **W19 范围建议**：本轮仅 chi 宽度适配；**axi4-in-subsystem 留后续**（ChiAxiNetworkTop 无 domClk/domReset 暴露，ChiAdaptedNetworkTop 现有 require nDomain==1 约束沿用），不扩大本轮面。
6. **落地序**：硬件 W19 先落 trait + ChiNetworkLike + mkNet + ChiNetworkTop MixedVec 化（默认工厂行为保持，硬件仿真绿）→ GEN 接入（ChiAdaptedNetworkTop extends ChiNetworkLike + emitSystem 传工厂 + ports.json 键 + 解除 hierarchicalAdaptGuard）→ 联合回归。GEN 在 trait 落地前不预写实现（无法编译 against 不存在类型），本对齐即开工依据。

### 14.2 HW-15/17 消费（S12，ir_gen_spec v1.8.0）

**已落地（2026-11-27，S12）**：

- **HW-17**：`ChiAssemble.systemParams` 传 `ChiBridgeLink.outstanding = nocLinks[].outstanding`（IR-94/CHK-61 既有，一行消费）。
- **HW-15/W18**：`ChiAssemble.subDomains(v2)` 按子系统根时钟 `(id, freqMHz)` 键首次出现序派生域向量（SEM-20b，0 = 模块时钟）；`emitSystem` 传入 `ChiSystemTop(nets, bridges, subDomain)`。
- **CHK-62**（新增 C0）：同步桥两端根域不同 / async 桥两端根域相同 ⇒ 报错（与 ChiSystemTop require 对齐）；**CHK-59 扩**：cdc 与 asyncFifoDepth 一致性（async⇔≥2）。
- **守护解除**：`hierarchicalEmitGuard`（async）删除，域正确性由 CHK-62 静态保证；`hierarchicalAdaptGuard`（HW-16）维持。
- **报告**：ports.json 逐 free 端口 additive `clockDomain`/`rootClock`/`rootClockMHz`；多根域系统 clocking 字段描述 subClk/subReset（nSubDom-1，集成方供给）；estimate assumptions 去除"sync bridges only/HW-15 pending"，标注 outstanding 透传与域规则（HW-16 注记保留）。
- **验证**：CheckV2Spec CHK-59/62 正负例、EmitV2Spec subDomains 派生/outstanding 透传/async 发射+端口标注、ChiSystemGenSpec IR 驱动 async 跨根时钟 golden（subDomain=Seq(0,1)、远端存储在 subClk 域、ReadNoSnp 逐位一致）；loom 全量回归绿。

## 15. 硬件答复：HW-15/17 消费确认 + HW-16 项1 实现形式请 GEN 确认（2026-11-27，回应 §14）

### 15.1 HW-15/17 消费确认

GEN S12（§14.2）已消费 HW-15/17：`subDomains(v2)` 派生 + `emitSystem` 传 subDomain、`systemParams` 传 outstanding、CHK-62 与硬件 require 对齐、async 守护解除、ports.json 时钟标注。**与硬件 §13.1/§13.5 契约一致，硬件侧无追加动作**。HW-15/17 视为双侧闭合。

### 15.2 HW-16 项1（ChiNetworkTop io MixedVec 化）：请 GEN 确认实现形式

硬件同意方案 A 的**跨侧接口**（`ChiNetworkIO` / `ChiNetworkLike` / `mkNet` 工厂，GEN `ChiAdaptedNetworkTop extends ChiNetworkLike` + emitSystem 传工厂）。但 §14.1 **项1 要求"ChiNetworkTop 的 Vec 须 MixedVec 化"**，勘查后发现其代价与越界风险显著，提出等价变体请 GEN 确认。

**量化证据（MixedVec 元素擦除为 Data，所有 `.req/.rsp` 访问需 asInstanceOf，参照 GEN 自己的 ChiAdaptedNetworkSpec L37/39 写法）**：

| 形式 | 波及范围 |
|---|---|
| **严格按项1**（MixedVec 化 `ChiNetworkTop.io`） | `ChiNetworkTop` 内部 16 处 + `ChiAxiNetworkTop` 4 处 + **`gen/ChiAdaptedNetworkTop` 6 处** + `ChiNetworkSpec` 77 处 + `ChiDvmNetworkSpec` 36 处 ≈ **139 处** cast 改动；且**必须修改 GEN-owned `ChiAdaptedNetworkTop`**（其 inner ChiNetworkTop 接线依赖 Vec 形态）——违反硬件不改 GEN 文件的既定边界，并与 GEN 在建工作碰撞 |
| **变体：薄包装层**（推荐） | 新增 `loom.chi.ChiNetworkIO`（rn/sn MixedVec + domClk/domReset）+ `abstract class ChiNetworkLike(val p) extends Module { val io: ChiNetworkIO }` + uniform 包装 `ChiNetworkUniform extends ChiNetworkLike`（内含 `ChiNetworkTop`，做 MixedVec↔Vec 转接）。**`ChiNetworkTop` 及其全部测试、GEN `ChiAdaptedNetworkTop` 全部不动**；仅 `ChiSystemTop` 边界按 free 槽 MixedVec 化 → `ChiSystemSpec` 20 处（硬件自有文件） |

**变体对 GEN 的影响（接口不变）**：
- `mkNet: ChiNetworkParams => ChiNetworkLike` 签名不变；默认工厂改为 `p => new ChiNetworkUniform(p)`（行为等价，仅多一层包装 hierarchy，综合会展平）。
- GEN 侧接入完全照 §14.1 项2/项6 执行：`ChiAdaptedNetworkTop extends ChiNetworkLike`（其 io 已是 MixedVec，只需声明为 `ChiNetworkIO` 类型）+ emitSystem 传工厂 + ports.json 键 + 解 `hierarchicalAdaptGuard`。**GEN 无需改 inner ChiNetworkTop 接线**。
- §14.1 项3（桥槽恒 fabric 宽原生 chi）、项4（系统边界 MixedVec + ports.json datWidth/protocol/clockDomain）、项5（本轮仅 chi 宽度适配，axi4-in-subsystem 后续）**全部沿用不变**。
- 唯一差异：发射 hierarchy 中默认子系统顶层名从 `ChiNetworkTop` 变为 `ChiNetworkUniform`（内含 ChiNetworkTop）⇒ **CG2 确定性基线需 GEN 重取**；若 GEN 介意顶层名，可将包装命名保持为 GEN 期望名。

**请 GEN 确认**：接受变体（推荐，省 ~119 处改动且不越界改 GEN 文件），还是坚持项1 原形（则需 GEN 自行承接 `ChiAdaptedNetworkTop` 的 6 处 cast 改动，硬件只改 hardware-owned 部分，两侧分别提交后联合回归）。

**用户裁定**：先跟 GEN 确认实现形式再开工 W19。硬件在收到确认前不动 RTL；`hierarchicalAdaptGuard` 维持。

## 16. GEN 答复：接受 HW-16 项1 薄包装变体，W19 可开工（2026-11-27，回应 §15.2）

### 16.1 裁定：接受变体（ChiNetworkUniform 薄包装）

**接受 §15.2 变体，放弃项1 原形**。理由：
1. **边界**：项1 原形迫使硬件改 GEN-owned `ChiAdaptedNetworkTop`（6 处 cast），违反既定 ownership 边界；变体把全部 MixedVec↔Vec cast 收口在硬件自有 `ChiNetworkUniform` 包装内，GEN 文件零被动改动。
2. **代价**：~139 处擦除 cast（含 ChiNetworkSpec 77 + ChiDvmNetworkSpec 36 测试波及）vs 包装内 ~20 处；Data 擦除后 `.req/.rsp` 全量 asInstanceOf 是长期维护负担，变体保留 ChiNetworkTop 强类型 io。
3. **行为等价**：包装仅转接，综合展平；默认工厂 `p => new ChiNetworkUniform(p)` 与现行为逐拍一致（硬件以默认路径仿真绿为准）。

### 16.2 GEN 侧确认事项

- **接入范围照 §14.1 项2/6 不变**：`ChiAdaptedNetworkTop extends ChiNetworkLike`（io 已是 MixedVec，改声明为 `ChiNetworkIO` 类型）+ emitSystem 按需传 `mkNet` + ports.json `datWidth`/`protocol` additive 键（`clockDomain` 已随 S12 落地）+ 解 `hierarchicalAdaptGuard`。项3/4/5（桥槽不变式/系统边界 MixedVec/本轮仅 chi 宽度）全部沿用。
- **trait 实例化形态**：请保证 `ChiNetworkIO extends Bundle` 以抽象 val 声明（`val rn: MixedVec[Data]` 等），使 GEN 可用 `IO(new ChiNetworkIO { val rn = MixedVec(...); ... })` 匿名实例化直接承接现有 io 构造，无需重构逐端口逻辑。
- **顶层名**：接受 `ChiNetworkUniform`，无改名要求；ports.json `top` 仍为 `ChiSystemTop`。
- **CG2 基线澄清（无需重取）**：GEN 侧 CG2 为**同配置双发射逐字节自洽**断言（EmitV2Spec L771），非固定 golden 文件；SV 内容断言仅 `module ChiSystemTop`/`ChiNocBridge`（变体下均保留）。包装层级变化后双发射自洽自动适配，**平铺发射（ChiNetworkTop.sv）完全不受影响**。硬件担心的"基线重取"不构成 GEN 工作量；W19 落地后 GEN 跑全量回归即完成确认。

### 16.3 落地序（不变）

硬件 W19：`ChiNetworkIO` + `ChiNetworkLike` + `ChiNetworkUniform` + `ChiSystemTop` mkNet 工厂 + 系统边界 free 槽 MixedVec 化（默认工厂行为保持，硬件仿真绿，含 ChiSystemSpec 适配）→ 通知 GEN → GEN 接入（§16.2）+ 解适配守护 + 失配宽度子系统 golden sim → 联合全量回归。GEN 在 trait 落地前不预写实现。

## 17. 硬件答复：HW-16/W19 落地，GEN 可接入（2026-11-27，回应 §16）

> **薄包装变体已落地并验证**（按 §16.1 裁定）。`ChiNetworkTop` 及其全部测试、GEN `ChiAdaptedNetworkTop` **零改动**。GEN 可按 §16.2 接入并解 `hierarchicalAdaptGuard`。

### 17.1 已交付契约（`loom/chi/ChiNetworkIO.scala`，新文件）

```scala
abstract class ChiNetworkIO extends Bundle {
  val rn: MixedVec[Data]; val sn: MixedVec[Data]
  val domClk: Vec[Bool];  val domReset: Vec[Bool]
}
abstract class ChiNetworkLike(val p: ChiNetworkParams) extends Module { val io: ChiNetworkIO }
class ChiNetworkUniform(p: ChiNetworkParams) extends ChiNetworkLike(p)   // 默认：内含未改动的 ChiNetworkTop
object ChiNodeWidths {
  val Supported: Set[Int] = Set(128, 256, 512)
  def nodeWidth(p: ChiNetworkParams, n: ChiNetNode): Int
  def cfgOf(p: ChiNetworkParams, n: ChiNetNode): ChiConfig
}
```

- **抽象 val 形态照 §16.2 要求**：GEN 可 `IO(new ChiNetworkIO { val rn = MixedVec(...); ... })` 匿名实例化，沿用现有逐端口 io 构造，无需重构。
- **`ChiNodeWidths` 为共享节点宽度规则**（chi 协议 + mismatch + 宽度 ∈ {128,256,512} 且 ≠ fabric 宽才适配），与 GEN `ChiAdaptedNetworkTop.nodeWidth` 现有判定一致。**建议 GEN 改用此 helper** 以免两侧规则漂移（系统边界端口宽度由它决定，必须与包装内部一致）。

### 17.2 ChiSystemTop 变更

- 新增第 4 参数 `mkNet: ChiNetworkParams => ChiNetworkLike = p => Module(new ChiNetworkUniform(p))`。
- **⚠ 契约细节（与 §14.1 项2 示例的差异）**：Chisel 要求模块例化包在 `Module()` 内，故**工厂自身负责 `Module()` 包装**（§14.1 示例 `new ChiNetworkTop(_)` 会抛 "attempted to instantiate a Module without wrapping it in Module()"）。GEN 接入须写 **`p => Module(new ChiAdaptedNetworkTop(p))`**。
- `io.rn/io.sn` 改为 **MixedVec**，逐 free 槽类型 = `Flipped(new ChiRnPort/ChiSnPort(ChiNodeWidths.cfgOf(sub, node)))` ⇒ 失配节点宽度外溢到系统边界（§14.1 项4）。**GEN ports.json 逐 free 端口 `datWidth`/`protocol` additive 键照此 helper 取值**。
- 新增 require：**桥从/至槽必须 fabric 宽原生 chi**（把 §14.1 项3 的 GEN 不变式在硬件侧也守住，违反给明确报错）。
- 桥接线不变（`nets(fromSub).io.sn(fromSn) <> br.io.from`，Data `<>` Bundle 结构化连接）；HW-15 subDomain/时钟域逻辑不变。

### 17.3 验证

- `ChiSystemSpec` **5 例绿**：同步桥端到端读/写、outstanding 透传（HW-17）、异根时钟 async 桥（HW-15）、**自定义 mkNet 工厂 + 子系统内 128b 适配 SN 节点（fabric 256b）经桥端到端读逐位一致（HW-16）**。
  - HW-16 用例形态：测试侧 `AdaptedSnNet extends ChiNetworkLike`（匿名 `ChiNetworkIO`，sn 元素 `ChiSnPort(128)`，节点边界插 `ChiDatWidth` 双向齿轮），`sub("b").copy(adapt = Seq(ChiAdapt(1, 128, 256, 0, mismatch = true)))`，`mkNet` 按 `p.adapt.nonEmpty` 分派 ⇒ 证明工厂钩子 + 边界宽度派生 + 子系统内适配三者贯通。**GEN 接入后应以 IR 驱动等价 golden 复现**。
- 默认工厂路径（全 fabric 宽）行为不回退；`ChiNetworkTop`/`ChiAxiNetworkTop`/`ChiAdaptedNetworkTop` 源码未改。

### 17.4 GEN 行动项（解适配守护）

1. `ChiAdaptedNetworkTop extends ChiNetworkLike(p)`，io 声明为 `ChiNetworkIO` 匿名实例化（现有 MixedVec 构造可直接沿用）。
2. `emitSystem` 在任一子系统 `ChiAdaptedNetworkTop.needed` 时传 `mkNet = p => Module(new ChiAdaptedNetworkTop(p))`（可统一全子系统传，matched 子系统行为等价）。
3. ports.json 逐 free 端口 `datWidth`/`protocol` 取 `ChiNodeWidths.cfgOf`；`clockDomain` 已随 S12 落地。
4. 解除 `hierarchicalAdaptGuard`；建议 CHK 增"桥槽不得为适配节点"与硬件 require 对齐。
5. 联合全量回归 + 失配宽度子系统 IR golden sim。

### 17.5 本轮边界（沿用 §14.1 项5）

仅 chi 宽度适配；**axi4-in-subsystem 仍后续**（`ChiAxiNetworkTop` 未暴露 domClk/domReset，`ChiAdaptedNetworkTop` 现有 `nDomain==1` 约束沿用）；异 cfg 子系统、异宽多 outstanding（W17）、dvmForward=true 仍为已记录后续。

## 18. GEN 答复：HW-16/W19 消费落地，HW-16 双侧闭合（2026-11-27，S13，回应 §17）

> **§17.4 五项行动全部完成**（ir_gen_spec **v1.9.0**，GEN-26）。HW-16 视为双侧闭合；分层发射至此支持**同步/async 桥 + 多 outstanding + 子系统内 chi 宽度适配 + 多根时钟域**。

- **接入**：`ChiAdaptedNetworkTop extends ChiNetworkLike(p)`，io 以 `IO(new ChiNetworkIO {...})` 匿名实例化（逐端口构造原样沿用）；nodeWidth/cfgOf/needed 全部委托 `ChiNodeWidths` 共享规则（两侧不漂移）。
- **工厂**：emitSystem 传 `mkNet = p => Module(if (needed(p)) new ChiAdaptedNetworkTop(p) else new ChiNetworkUniform(p))`（Module() 包装在工厂侧，照 §17.2 契约）；matched 子系统走 Uniform，**既有 matched 分层发射 CG2 逐字节不变**。
- **守护解除**：`hierarchicalAdaptGuard` 删除；桥槽不变式由 GEN 构造 + 硬件 require 双守（不另立 CHK）。
- **CHK-63**（新增 C0）：子系统含 axi4 节点 ⇒ 拒绝（axi4-in-subsystem 后续，§17.5 边界静态守住）。
- **ports.json**：逐 free 端口 additive `datWidth`（ChiNodeWidths.cfgOf）/`protocol`；estimate assumptions 去除 HW-16-pending。
- **验证**：IR 驱动失配宽度 golden（B 子系统 sB=128b / fabric 256b，sync 桥，远端 ChiReadSource 128b）跨桥读在 A 的 RN 累积 2×256b CompData 逐位一致；平铺 ChiAdaptedNetworkSpec 与全量回归绿（31 suite/361 test，含硬件 ChiSystemSpec 5 例）。
- **提交说明**：硬件 W19 文件（ChiNetworkIO.scala/ChiSystemTop.scala/ChiSystemSpec.scala）为 GEN 代码编译前置件，随 GEN commit 纳入；protocol_spec/wave7 plan/本清单留硬件批次。
- **剩余后续（均非阻塞，已记录）**：axi4-in-subsystem（CHK-63 守护）、异 cfg 子系统、异宽多 outstanding（W17）、dvmForward=true、节点频率失配 CDC、32/64/1024 节点宽度档。