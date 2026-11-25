# Loom → CHI 协议迁移计划（2026-11-25）

> 状态：**S1–S12 全部落地**（2026-11-25，全量回归 34 suite/143 test 全绿）| 作者：Qoder 会话整理
>
> 范围裁定（用户 2026-11-25）：
> 1. Loom 内部协议**直接按 CHI 要求重做**，不保留旧内部协议（burst 信用、逐拍地址路由、MNet/SNet 语义均退役）。
> 2. **DVM、Atomic 一期必须支持**。
> 3. **SNP/缓存一致性留接口**，一期不实现一致性语义。
> 4. **本次仅 RTL 级修改（底层代码生成能力）**；IR schema/GUI/示例迁移另起任务（§8）。
> 5. 实施完毕后新增 **protocol 层协议 spec**，写清底层所有支持 feature/协议/接口（S12）。

---

## 1. 目标与边界

把 Loom 的 **RTL 代码生成能力**从"自定义四通道运输 + AXI 语义"迁移为 **CHI 原生互连生成**：节点角色、通道类别、flit 格式、链路层流控、协议层事务全部按 AMBA CHI（IHI0050，Issue E 基线、字段向 F+ 留余量）。拓扑生成方法论（IR→门禁→RTL→形式化）保留；本次只改"生成出来的 RTL 跑什么协议"，用户侧 IR/GUI 迁移另起任务。

一期支持：
- 节点（**Loom 生成 RTL**，D1）：RN-I、RN-D（DVM 必须，D3）、SN-F/SN-I、MN（DVM）、HN-I；HN-F 仅端口占位。
- 事务：ReadNoSnp、WriteNoSnpFull/Ptl、Atomic{Store,Load,Swap,Compare}（SN 执行，D2）、DVMOp 全族（D3）。
- 机制：DBID 拉模型写、P-Credit/RetryAck 准入、Order/ReadReceipt/CompAck 排序、flit L-Credit、链路激活 FSM。

一期预留不实现（D4：**仅端口占位**）：
- 与 DVM 共享的 **SNP 通道保留**；一致性独占项（snoop filter、CopyBack 写、DCT/DMT、cache 状态机、Snp{Shared,Unique,...} 窥探逻辑）**不做占位实现**，仅预留 opcode 编码空间与端口形状。
- 暂不支持但记录（D8）：Exclusive、CMO、Stash、MPAM、MTE、DWT。

**关键推论：SNP 物理通道一期就要有。** DVM 的 SnpDVMOp 走 SNP；但按 D11，**若某配置未生成 DVM，则 SNP 不实例化、门禁也不证明它**——有什么才证明什么。

## 2. 名词对齐表（Loom 旧名 → CHI 名）

| Loom 旧名 | CHI 名 | 解释 |
|---|---|---|
| master / slave 端点 | RN-I / RN-D / SN-F / SN-I | 请求节点（IO / 带 DVM）/ 从节点（内存 / 外设） |
| MNet / SNet 附接口 | CHI interface（port） | 每口一个 Node ID + 全套 REQ/RSP/SNP/DAT |
| （新增） | HN-I / HN-F / MN | Home（IO / 一致性占位）/ 杂项节点（DVM 分发） |
| burst | transaction | CHI 单笔 ≤64B；AXI 长 burst 在桥接层拆分 |
| beat（拍） | flit | 链路层传输单位；DAT flit = width/8 字节 |
| (src, id) | (SrcID, TxnID) | 节点号 + 事务号（默认 8b，D12）；响应回显 TxnID 匹配 |
| src 回程路由 | ReturnNID / ReturnTxnID | REQ 中显式指定"数据回给谁" |
| 地址窗口逐跳译码 | SAM + TgtID | NI 一次性译码出目标 Node ID；fabric 只按 TgtID 路由 |
| rreq / wdata / rdata / wresp | REQ / DAT / RSP（+SNP） | 按消息类别分通道；读写数据共享 DAT |
| last | DataID | flit 在 64B 内的位置（2bit，标准档） |
| strb | BE | 逐字节使能 |
| err（1b） | RespErr（2b） | OK / EXOK / DERR / NDERR |
| vc（类×2+半区） | 实现私有 VC + QoS（4b） | CHI flit 只带 QoS；VC/dateline 是 fabric 内部实现自由 |
| 端到端 burst 信用 | P-Credit + DBID + L-Credit | 三级：事务准入 / 数据缓冲授权 / 逐跳 flit 流控 |
| shell（native/AXI4） | protocol bridge | AXI4 ↔ CHI 桥接，两种端口形态可配（D6） |

## 3. 目标架构（按 CHI 三层）

- **Protocol layer**：事务引擎（TxnID 追踪、DBID 授权、P-Credit、排序闭环）、opcode 编解码、DVM 分发（MN）、Atomic 执行（SN）。
- **Network layer**：TgtID 路由、SAM、QoS→VC 映射、四通道类独立交换；XY/dateline 思想保留，门禁按实际生成的通道类证明（D11）。
- **Link layer**：每通道每方向 flit L-Credit（≤15）、LCRDV、链路激活 FSM（STOP/ACTIVATE/RUN/DEACTIVATE + SACTIVE）、Link flit。

位宽（D5 终裁）：DAT 默认 **256b**，可配 **128/512b**，**仅 CHI 标准档，不支持 64b**。DataID 2bit。REQ/RSP/SNP 窄 flit 独立线宽。

## 4. 实施波次（story 级，blocker 序，本次仅 RTL 生成层）

S1 为评审门禁，批准前不进 S2。IR schema/GUI 相关一律不在本次（§8）。

### S1 CHI 目标规格（评审门禁）
- 写 `src/loom/doc/chi_target_spec.md`：支持/预留/不支持 opcode 全清单、flit 字段编码、四通道消息方向表、读/写/Atomic/DVM 事务流、三级流控、排序档位、标准位宽声明。
- 名词对齐表定稿；旧 link_spec.md 标记废弃。
- 门禁影响分析：G1 顶点 `(link, 通道类, 方向, vc)`，按生成配置纳入通道类。
- **依赖**：无。**验收**：用户批准规格。

### S2 RTL 生成层：CHI 节点/通道模型 ✅（2026-11-25 落地，ChiSpec 7/7 绿）
- 新增 `loom.chi` 包：ChiDefs（节点/通道/opcode 三清单/编码）、ChiConfig（§9 参数+require）、ChiFlit（REQ/RSP/SNP/DAT bundle+RN/SN 端口）、ChiParams（master→RN-I/slave→SN-F/NodeID/SAM）、ChiTop（端口骨架+每 RN SAM 译码，通道内部 tie-off 待 S3/S4）。
- LoomMain 新增 `emit-chi` 命令；IR schema/GUI 未动（D7）。
- **依赖**：S1。**验收**：内部模型 check 通过，发射 CHI 端口骨架确定性。✔

### S3 链路层 ✅（2026-11-25 落地，ChiLinkSpec 3/3 绿）
- ChiLink.scala：ChiTxCredit（flit L-Credit≤15 + 溢出/下溢断言）、ChiLinkFsm（STOP/ACTIVATE/RUN/DEACTIVATE 四态 + SACTIVE）、ChiTxLink（RUN+信用双门控 TX）。
- Link flit 断链归还/ RX 信用授予与路由集成在 S4 细化。
- **依赖**：S2。**验收**：信用耗尽反压、断链/重激活仿真绿。✔

### S4 网络层：TgtID 路由 + 四通道类交换 ✅（2026-11-25 落地，ChiRouterSpec 3/3 + ChiNetworkSpec 4/4 绿）
- ChiRouter.scala：ChiChannelRouter（TgtID 表选路+输入队列+每输出 RR）、ChiRouterPort、ChiRouter4（四类独立交换，SNP 条件实例化）。
- ChiNetwork.scala：ChiQosMap（QoS→VC）、ChiNetworkParams（BFS 路由表，四类共享 TgtID 表）。ChiNetworkTop.scala：多 router 网络顶层（本地 RN/SN 端口 + link 交叉接线）。
- ChiGates.scala：G1 按通道类条件证明（复用 FabricGraph CDG/dateline，SNP 按 DVM 纳入，D11）。
- **依赖**：S2。**验收**：chain_4r 多跳 REQ 正向/DAT 反向仿真绿，G1 按配置通过。✔

### S5 协议引擎：追踪 + 两级流控 ✅（2026-11-25 落地，ChiProtocolSpec 2/2 绿）
- ChiProtocol.scala：ChiIdPool（TxnID/DBID 自由列表，耗尽反压+回收断言）、ChiRetryCtrl（P-Credit：满→RetryAck，释放→PCrdGrant）。
- **依赖**：S3、S4。**验收**：ID 耗尽反压/回收、retry→grant 仿真绿。✔

### S6 读写事务数据通路 ✅（2026-11-25 落地，ChiTransactionSpec 3/3 绿）
- ChiTransaction.scala：ChiWriteSink（REQ→DBIDResp→DAT(TxnID=DBID)→Comp，DataID 重组 64B，DBID 池反压）、ChiReadSource（ReadNoSnp→CompData 切片+DataID）、ChiBurstSplit（≤64B 事务化）。ChiOp 内部 opcode 编码。
- **依赖**：S5。**验收**：写拉模型往返逐位一致、读 flit 序列、burst 拆分绿。✔

### S7 排序闭环 ✅（2026-11-25 落地，ChiOrderSpec 2/2 绿）
- ChiOrder.scala：ChiOrderRule（Device→RequestOrder/Normal→NoOrder，OWO 预留）、ChiOrderCtrl（ReadReceipt→等 Comp→CompAck 才关闭追踪）。
- **依赖**：S6。**验收**：Device 排序规则、Comp 单独不关闭/CompAck 闭环绿。✔

### S8 Atomic（D2：SN 执行）✅（2026-11-25 落地，ChiAtomicSpec 3/3 绿）
- ChiAtomic.scala：ChiAtomicOp（8 op + 位宽感知纯参考金锚）、ChiAtomicAlu（组合 8op，含符号 Smax/Smin）、ChiAtomicEngine（SN RMW 返回旧值，Device→NDERR 不改存储）。
- **依赖**：S6。**验收**：金锚对拍、8op ALU、RMW 旧值返回、Device 拒绝绿。✔

### S9 DVM（D3：全族，RN-D 必须）✅（2026-11-25 落地，ChiDvmSpec 2/2 绿）
- ChiDvm.scala：ChiDvmMsg（TLBI/BPI/VIC/SYNC 全族+TLBI 子类型）、ChiDvmMn（DVMOp→DBIDResp→payload→SnpDVMOp 广播→SnpResp 收集→Comp，逐目标 mask）。
- MN（Loom 生成）：DVMOp→DBIDResp→payload→SnpDVMOp 广播→SnpResp 收集→Comp。
- RN-D 端口（SNP RX）；DVM 全族 TLBI/BPI/VIC/SYNC；SYNC 完成语义。
- DVM 可配是否生成；未生成则 SNP 不实例化（D11）。
- **依赖**：S4、S6。**验收**：广播-收集-完成闭环，SYNC 仿真绿。

### S10 一致性预留（D4：仅端口占位）✅（2026-11-25 落地，ChiReservedSpec 3/3 绿）
- ChiReserved.scala：使用门禁（拒绝 reserved/unsupported opcode）、cache 状态 Resp 编码 + Snp* 编码空间预留、ChiRnFPort/ChiHnFPort 端口占位（无 cache 逻辑）。
- **依赖**：S9。**验收**：门禁拒绝预留 opcode、占位端口发射确定性绿。✔

### S11 桥接 + 门禁/形式化/回归收尾 ✅（2026-11-25 落地，全量回归 34 suite/143 test 全绿）
- ChiAxiBridge.scala：ChiAxiReadBridge/ChiAxiWriteBridge（AXI4↔CHI，64B↔nFlits beat），ChiAxiBridgeSpec RN↔SN 环回 2/2 绿。emit-chi 多 router 发射 ChiNetworkTop。
- 门禁：ChiGates 按通道类条件证明（复用 FabricGraph CDG/dateline）；CG2 发射确定性。
- **全量 loom 回归 34 suite/143 test 全绿**（既有 109 + Chi 34），无回退。
- CHI formal token 模型已补（token_chi_lcredit/dbid/pcrd/dvm），yosys SAT 全 PASS（9/9，含 5 旧模型）；边界须结构性门控（assume 非硬约束）。
- **依赖**：S7、S8、S9。**验收**：全量绿、发射确定性。✔

### S12 protocol 层协议 spec ✅（2026-11-25 落地，src/loom/doc/protocol_spec.md）
- 由已落地 `loom.chi` RTL 反向核对：节点/端口、四通道 flit、支持 opcode 全表、三级流控参数、排序、Atomic/DVM 能力、标准位宽、不支持/预留清单、对外接口（AXI4/native）、配置参数、门禁、模块清单、验证状态。
- **依赖**：S11。**验收**：spec 与 RTL 逐项一致。✔

关键路径：S1→S2→S4→S5→S6→S7/S8/S9→S11→S12。S3 与 S4 可并行。

## 5. 保留资产 vs 退役清单

保留：拓扑→门禁→RTL→形式化方法论、XY/BFS 路由、dateline 思想、CG2 确定性。
退役：burst 原子信用、逐拍地址路由、rreq/wdata/rdata/wresp bundle、MNet/SNet 语义、(src,id) 旧编码、link_spec.md v0.1（废弃）。

## 6. 裁定记录（已闭合）

| # | 裁定 |
|---|---|
| D1 | HN/MN 由 **Loom 生成**；HN-F 仅占位 |
| D2 | Atomic **SN 执行**，可配是否支持 + entry 数；**Device 禁止**（SN MemAttr 校验回 NDERR，CHI 合规） |
| D3 | DVM **全族**（TLBI/BPI/VIC/SYNC），**RN-D 必须** |
| D4 | 一致性**仅端口占位**；与 DVM 共享的 SNP 留下，独占项不做占位实现 |
| D5 | DAT 默认 256b，可配 128/512；**仅 CHI 标准档，不支持 64b**（终裁） |
| D6 | AXI4 桥与 CHI native **均可配** |
| D7 | 本次**仅 RTL 级**（底层代码生成）；IR/GUI 另起任务（§8） |
| D8 | Exclusive/CMO/Stash/MPAM/MTE/DWT 暂不支持，**记录** |
| D9 | Normal 无排序、Device RequestOrder、**OWO 预留未来支持**（终裁） |
| D10 | 缓冲/信用按 BDP 公式，默认深度待 PPA 阶段定 |
| D11 | **有什么才证明什么**：门禁/形式化按实际生成的通道类与流量证明；DVM 未生成则 SNP 不纳入 |
| D12 | TxnID 默认 **8b**，outstanding 默认 **64**，均可配 |

## 7. 验收与门禁

- G1：CDG 顶点 `(link, 通道类, 方向, vc)`，按生成配置证明死锁自由（D11）。
- CG2：逐文件发射确定性；禁手改发射物。
- 形式化：token 模型覆盖三级流控不丢/不重/前进性，按配置证明。
- 回归：每 story 自带仿真边界 + S11 全量；金锚由事务语义推导，不以 RTL 行为拟合。

## 8. 另起任务（不在本次）

- **IR v2 schema 迁移**：用户侧 JSON IR 节点/端口/NodeID/SAM 字段、MNet/SNet 退役、C 校验面向用户口径。
- **GUI 工作区**：CHI 节点编辑、地址表/SAM 视图、报告口径同步。
- **示例配置重写**：minimal/ring/mesh 等 loom/v1 示例迁移到 CHI IR。
- 本次 RTL 生成层可先用内部最小 IR 钩子驱动，用户侧迁移完成后对接。