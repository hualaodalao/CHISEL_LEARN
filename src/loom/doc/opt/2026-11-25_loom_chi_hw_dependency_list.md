# Loom CHI 硬件依赖清单（2026-11-25）

> 背景：IR/GEN 适配任务（见 `2026-11-25_loom_ir_gen_chi_adaptation_plan.md`）范围裁定为**仅 IR + 生成逻辑，不改 loom/chi 硬件**。
> 本清单收录适配过程中发现的、**需要硬件侧（loom/chi RTL）修正或新增**的项，以及硬件疑问。
> 作者只做记录与 GEN 侧临时行为约定，不做 RTL 修正。每项闭合后回写 `ir_gen_spec.md` §9 对应 OQ 的裁定。
>
> 用户裁定（2026-11-25）：CDC 保留（HW-04）；ChiQosMap 硬编码入疑问清单（HQ-01）；banks 用途已答复（建议退役，待确认）。
> 硬件 owner 裁定（2026-11-25，详见 §4）：HW-01…HW-07 全部已确认（HW-01 HN-I/MN 都需要；HW-02 删约束；HW-03 ChiTop 退役；HW-04/06 硬件侧提供模块 GEN 例化；HW-05 NI 就地 NDERR；HW-07 恒双向+depth 可配）；HQ-01 已答复。
> 待 GEN 回答（详见 §5）：AQ-01/AQ-02 **已答复并经用户确认（2026-11-25，见 §5.1）**，S2/S3 对齐口径已冻结。

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

formal 10/10 PASS。网络级 async/NI/分区 CLI 集成为 GEN 消费点（IR cdc/partitions/addr 驱动）。

### 5.1 GEN 答复（2026-11-25，用户确认；同步 ir_gen_spec.md §9.2）

**AQ-01**：
1. 用现有 `nodes[].kind`（IR-14，六值集合已含 hn-i/mn），不新增 role/nodeKind 第二字段；v1 role 由升级器折算（MIG-02）。HW-01 闭合前 elaboration 报"暂不支持"，schema 不挡。
2. 同意 **RN→SN→HN→MN** 段序，段内按节点声明序确定性分配；显式 `nodeId` 覆盖优先（SEM-03），冲突/越界报 CHK；总节点数 ≤ 2^nodeIdWidth。
3. 同意 **SAM 仅 SN**（IR-24/CHK-17 已如此）；hn-i 一期端口+路由占位不进 SAM；MN 由 DVMOp TgtID 直达。增补：dvmEnable=true 时要求**恰好 1 个 mn**（CHK-19 变体），GEN 派生其 NodeID 作 elaboration 参数传 RN-D/MN，不走 SAM。

**AQ-02**：
1. CDG 按 D11 条件展开：**dateline 环上 link 展全 VC 顶点，非环 link 只展 half0**（不造不存在的 half1 路径）；通道类按生成配置纳入（snp 仅 dvmEnable）。依赖边：router 内同 (通道类,vc) 输入→输出；cut link 上 half0→half1 过渡边，half1 永不回 half0。
2. **半区由 GEN 拓扑派生、硬件消费**：复用 FabricGraph SCC/datelines/autoDatelines，cut 标志按 (link,方向,通道类) 写进 router 参数；硬件只在标记出口翻 0→1，不自行判半区。HW-07 恒双向后 cut 按两方向独立给（对标 v1 outCut/inCut）。
3. 权重/阈值**单一真源 = IR qos 块**（IR-25–28 mapping + classes[].weight，默认 8/4、4:2:1），GEN 装配传 ChiConfig，硬件吃 cfg——IR 是用户界面、ChiConfig 是硬件参数界面，非二选一。首版 CHK-34 锁默认表；硬件 S3 cfg 消费落地后次版本放开自定义（分段无洞无重叠、weight∈[1,64]）。

**VC 编号泛化（用户裁定，硬件 S3 必须遵守）**：`vc = classIndex*nHalf + half`，**nHalf 由拓扑路由规则派生，不得写死 ×2**——首版环 dateline nHalf=2，未来 mesh 特殊路由规则可能需要更多道数；`vcWidth = log2Ceil(nClasses×nHalf)` 随之派生。`ChiQosMap.vcOf` 现写死 `*2`/`vcWidth=3`，须参数化 nHalf/vcWidth（GEN 传入）。ir_gen_spec SEM-08/ENC-08 已按此改写。