# Loom CHI 硬件依赖清单（2026-11-25）

> 背景：IR/GEN 适配任务（见 `2026-11-25_loom_ir_gen_chi_adaptation_plan.md`）范围裁定为**仅 IR + 生成逻辑，不改 loom/chi 硬件**。
> 本清单收录适配过程中发现的、**需要硬件侧（loom/chi RTL）修正或新增**的项，以及硬件疑问。
> 作者只做记录与 GEN 侧临时行为约定，不做 RTL 修正。每项闭合后回写 `ir_gen_spec.md` §9 对应 OQ 的裁定。
>
> 用户裁定（2026-11-25）：CDC 保留（HW-04）；ChiQosMap 硬编码入疑问清单（HQ-01）；banks 用途已答复（建议退役，待确认）。
> 硬件 owner 裁定（2026-11-25，详见 §4）：HW-01…HW-07 全部已确认（HW-01 HN-I/MN 都需要；HW-02 删约束；HW-03 ChiTop 退役；HW-04/06 硬件侧提供模块 GEN 例化；HW-05 NI 就地 NDERR；HW-07 恒双向+depth 可配）；HQ-01 已答复。
> 待 GEN 回答（详见 §5）：AQ-01/AQ-02 **已答复并经用户确认（2026-11-25，见 §5.1）**，S2/S3 对齐口径已冻结。
>
> **硬件总体完成状态（2026-11-26，wave1+2+3+4+5 闭合）**：HW-01…HW-14 + HQ-01 + A1～A4 + **HW-11（NoC 间桥/UP-07）全部硬件落地并验证**。loom.Chi* **23 suite/85 test 绿**，formal **11/11 PASS**（含 token_chi_noc_bridge 死锁自由），protocol_spec **v1.6.0**。HW-11 落地明细见 §11（答复 GEN §10）；wave1 见 §6、wave3 见 §6.1、wave4 见 §9；可例化模块契约见 §7.1/§11.1，GEN phase 2 行动项见 §11.2。剩余均为 GEN 装配消费点（portMode/protocol 分派、per-source routes/forbid、qosGen/adapt 传参、flat async/分区 CLI 发射、分层发射/estimate）+ 已记录后续（桥多 outstanding、dvmForward=true、异 cfg 子系统），硬件不再阻塞。

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

- `chi.ChiNocBridge(c, windows: Seq[(Long,Long)], fromNodeId, toNodeId, asyncFifoDepth=0, toDatWidth=0, dvmForward=false)`：from=ChiSnPort(c)、to=ChiRnPort(toCfg)、dstClk/dstReset。终结型代理：窗口转发（地址不重映射）+ TxnID/NodeID 重映射 + DBID 拉模型写 + 未命中就地 NDERR。asyncFifoDepth≥2 每 to 侧通道类 ChiAsyncLink（TX from 域跨出、RX dstClk 域跨回）；toDatWidth≠c.datWidth 时 DAT 路径 ChiDatWidth 齿轮；store&fwd 两域独立反压。**单 outstanding**（多 outstanding ID 池为后续阶段，用户裁定分阶段）。**dvmForward=false**（各 NoC MN 自治；require 拒绝 true，跨桥 DVM 转发为后续）。
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

- 桥多 outstanding（ID 池深度参数化）；dvmForward=true 跨桥 SnpDVMOp 转发 + 对侧 SnpResp 收集（需 from 侧 SNP 注入路径）；ChiSystemTop 异 cfg 子系统（per-subsystem DAT 宽端口）。