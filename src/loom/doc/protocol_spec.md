# Loom CHI 互连生成层硬件规格

## 版本控制

### 文档版本规则

本文以 `major.minor.patch` 标记。新增支持 feature 升 minor；接口/编码不兼容变更升 major；文案/勘误升 patch。Feature ID 一经分配不复用、不重排（见 3.1）。

### 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0.0 | 2026-11-25 | 首版：合并 chi_target_spec 与 protocol_spec，按完整硬件规格结构重写；S1–S12 落地，34 suite/143 test 绿，formal 9/9 PASS |
| 1.1.0 | 2026-11-25 | 第 6 章扩为逐子模块规格（功能/参数/接口/行为/用法/限制），覆盖全部 17 个 block |
| 1.2.0 | 2026-11-25 | HW-01…07 + HQ-01 落地：HN-I/MN elaboration + DVM 接入网络、SAM 1:1 约束删除、ChiTop 退役、depth 可配、QoS/VC 三级仲裁（nHalf 参数化）、NI 未命中 NDERR、ChiAsyncLink CDC、分区三件套；ChiRnPort 补 TX RSP/DAT |

## 1. 文档说明

### 1.1 目的

本文定义 Loom CHI 互连生成层（`loom.chi` 包）的协议层 feature、各 block 的接口/用法/功能、flit 数据表示、配置约束、时序复位、异常处理与实现限制。Loom 把 loom/v1 IR 描述的拓扑编译为 CHI 原生互连 RTL；本文是生成产物对外契约与内部 block 行为的权威依据。

### 1.2 范围

本文覆盖：

- CHI 节点角色（RN/SN/HN/MN）与四通道类（REQ/RSP/SNP/DAT）接口；
- 读/写/Atomic/DVM 事务行为与排序、三级流控；
- TgtID 路由、SAM、多 router 网络、QoS→VC；
- AXI4↔CHI 桥接；
- flit 字段编码、配置参数、形式化门禁。

本文不定义：用户侧 IR schema/GUI（另起任务）、旧 `loom.rtl` 协议（并存待退役）、系统物理地址映射、cache 一致性语义（仅预留接口）。

### 1.3 规范性用语

- "必须"：违反导致事务被拒绝、结果未定义或与本规格不一致。
- "应"：集成推荐约束。
- "默认配置"：未显式覆盖 `ChiConfig` 参数时的配置。
- "预留"：编码空间/端口形状存在但一期不实现，使用门禁拒绝。
- `datWidth`：DAT flit 位宽（默认 256）；`perFlit = datWidth/8` 字节；`nFlits = 64/perFlit`（64B 事务的 flit 数）。
- 地址均为字节地址；多字节数据低地址存低有效字节。
- opcode 十六进制对外编码在桥接层对齐 IHI0050；本文 `ChiOp` 为 Loom 内部符号编码。

## 2. 模块概述

Loom CHI 生成层产出 packet-based CHI 互连：节点按 TgtID 路由 flit，四通道类独立交换，写事务采用 DBID 拉模型，链路按 flit L-Credit 流控。基线 AMBA CHI IHI0050 Issue E。

与旧 `loom.rtl`（AXI 式 rreq/wdata/rdata/wresp、burst 信用、逐拍地址路由）的区别：CHI 按消息类别分通道、按 TgtID 路由、写先授权后发数、地址只在 REQ 出现一次。两者共享 `loom.ir/check/graph/report`，chi 复用 rtl 的 `LmRrArbiterN`/`LmWire` 通用件。

当前一期支持非一致性 IO 子集 + Atomic + DVM；缓存一致性仅留端口占位。

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

| 类别 | 编号格式 | 描述对象 |
|---|---|---|
| 功能 | `LM-Fxxx` | 协议事务、路由、流控、接口及外部行为 |
| 性能 | `LM-Pxxx` | 通路位宽/带宽、每拍接受/发射能力、并行度 |
| Debug | `LM-Dxxx` | 状态观测、形式化、仿真断言 |

ID 不是 opcode/地址/错误码，不重排不复用。性能条目只索引已存在能力，不列目标值。

### 3.2 功能清单

| Feature ID | 功能 | 状态 | 能力与主要约束 | 章节 |
|---|---|---|---|---|
| LM-F001 | CHI 四通道节点接口 | 已实现 | RN-I/RN-D/SN-F/SN-I 端口，每口 Node ID + REQ/RSP/SNP/DAT；SNP 按 DVM 条件实例化 | 4.2、6.3 |
| LM-F002 | TgtID 路由与 SAM | 已实现 | NI 一次性地址译码成 TgtID（ChiSamDecode）；fabric 按 TgtID 表选路，地址不再逐跳译码 | 5.7、6.4 |
| LM-F003 | 四类独立交换 | 已实现 | ChiRouter4 内 REQ/RSP/SNP/DAT 各一套 ChiChannelRouter，独立队列/仲裁，无跨类共享 | 4.2、5.7、6.7 |
| LM-F004 | 多 router 网络 | 已实现 | ChiNetworkParams BFS 路由表（四类共享 TgtID 表），ChiNetworkTop 本地端口+link 交叉接线 | 5.7、6.8–6.9 |
| LM-F005 | 非一致性读 | 已实现 | ReadNoSnp→CompData×nFlits（DataID 切片）；ChiReadSource 单事务 64B | 5.1、6.12 |
| LM-F006 | 非一致性写拉模型 | 已实现 | WriteNoSnp→DBIDResp→NonCopyBackWrData(TxnID=DBID)→Comp；DataID 重组 64B；DBID 池反压 | 5.2、6.12 |
| LM-F007 | Atomic | 已实现 | 名集 AtomicStore{8op}/AtomicLoad{8op}/AtomicSwap/AtomicCompare；8 ALU op 已实现，SN 执行 RMW 返回旧值；Device→NDERR 不改存储；可配 enable/entries | 5.3、6.14 |
| LM-F008 | DVM | 已实现 | DVMOp→DBIDResp→payload→SnpDVMOp 广播→SnpResp 收集→Comp；TLBI/BPI/VIC/SYNC 全族 | 5.4、6.15 |
| LM-F009 | 排序闭环 | 已实现 | Device RequestOrder+ReadReceipt；ExpCompAck 时 CompAck 才关闭追踪（Comp 单独不关闭）；OWO 预留 | 5.5、6.13 |
| LM-F010 | 链路 L-Credit + 激活 FSM | 已实现 | flit 信用≤15，四态 STOP/ACTIVATE/RUN/DEACTIVATE；ChiTxLink RUN+信用双门控 | 5.6、6.6 |
| LM-F011 | 事务 P-Credit | 已实现 | 满→RetryAck，释放→PCrdGrant，AllowRetry=0 重发 | 5.6、6.11 |
| LM-F012 | TxnID/DBID 池 | 已实现 | ChiIdPool 自由列表，耗尽反压+回收断言 | 5.6、6.11 |
| LM-F013 | AXI4 桥接 | 已实现 | ChiAxiReadBridge/ChiAxiWriteBridge，64B↔nFlits beat；portMode 可配 | 5.8、6.17 |
| LM-F014 | 一致性预留 + 使用门禁 | 预留 | ChiRnFPort/ChiHnFPort 端口占位；ChiReserved.checkOpcode 拒绝 reserved/unsupported | 6.16、14 |
| LM-F015 | QoS/VC 调度 | 已实现 | per-VC 队列 + 三级仲裁（input RR/half RR/class WRR）+ cut 翻半区；vc=class×nHalf+half，nHalf/vcWidth 参数化（GEN 传入）；阈值/权重 IR→cfg | 5.7、6.7、6.8、7.6 |
| LM-F016 | burst 事务化 | 已实现 | ChiBurstSplit 把字节 span 拆 ≤64B 事务 | 5.8、6.12 |
| LM-F017 | CDC 异步链路 | 已实现 | ChiAsyncLink 每通道类异步 FIFO + 保守跨域信用（复用 v1 LmAsyncLink）；formal token_chi_async_credit | 6.19 |
| LM-F018 | 分区发射 | 已实现 | ChiPartitionTop/Stub/Wrapper，边界口每通道 ≥1 pipe，async cut 插 ChiAsyncLink | 6.20 |
| LM-F019 | NI 未命中 NDERR | 已实现 | ChiSamDecode.withHit + ChiNiError 未命中就地 Comp NDERR 不进 fabric（网络级集成待 GEN） | 6.18 |
| LM-F020 | NodeID 段序装配 | 已实现 | ChiNodeIds.assign RN→SN→HN→MN 段序 + 显式覆盖优先 + 冲突/越界校验 | 6.4 |

### 3.3 性能清单

记 `perFlit=datWidth/8`，默认 datWidth=256 → perFlit=32B、nFlits=2。下表为接口峰值/结构上限，**非端到端实测吞吐**。

| Feature ID | 通路/性能项 | 状态 | 位宽/带宽/率 | 并行能力与瓶颈 | 关联 |
|---|---|---|---|---|---|
| LM-P001 | DAT 数据通路 | 已实现 | 128/256/512b 可配，默认 256b=32B/flit；64B 事务=2 flit | 每端口每方向每拍 1 flit；读写共享 DAT | LM-F005/F006；7.1、12 |
| LM-P002 | REQ 事务率与混合带宽顶 | 已实现 | 每事务 1 REQ flit；混合全双工总带宽顶=REQ 口数×64B/拍 | 256b 单口混合恰配平（1REQ:2DAT=1口:2方向）；512b 单口混合被 REQ 封顶 64B/拍 | LM-F003；12.2 |
| LM-P003 | 四类通道并行 | 已实现 | REQ/RSP/SNP/DAT 独立线束/信用/仲裁 | 四类可同拍并行，无跨类阻塞 | LM-F003；5.7 |
| LM-P004 | 路由交换率 | 已实现 | 每输出端口每拍 1 flit（RR 仲裁） | 输入队列深度默认 2；多输入争用同输出按 RR | LM-F002/F003；6.7 |

### 3.4 Debug 清单

| Feature ID | Debug feature | 状态 | 观测能力与约束 | 章节 |
|---|---|---|---|---|
| LM-D001 | FSM 状态/路由观测 | 已实现 | ChiLinkFsm.state、ChiTop.tgtId、ChiIdPool.outstanding、ChiRetryCtrl.outstanding 输出 | 6、11.1 |
| LM-D002 | formal token 模型 | 已实现 | token_chi_lcredit/dbid/pcrd/dvm，yosys SAT 有界证明 | 11.2 |
| LM-D003 | 仿真断言 | 已实现 | ChiIdPool/ChiRetryCtrl/ChiTxCredit 溢出/下溢断言 | 10.4 |

### 3.5 支持边界

清单只索引已实现能力；预留/不支持见第 14 章。接口峰值不等价实测持续吞吐；单事务引擎的"单 outstanding"是当前实现限制（14.1）。

## 4. 架构说明

### 4.1 三层架构

| 层 | 职责 | 主要 block |
|---|---|---|
| Protocol | 事务语义：追踪/DBID/P-Credit/排序/Atomic/DVM | ChiTransaction、ChiProtocol、ChiOrder、ChiAtomic、ChiDvm |
| Network | TgtID 路由、SAM、四类交换、QoS→VC | ChiRouter、ChiNetwork、ChiNetworkTop、ChiParams |
| Link | flit L-Credit、链路激活 FSM | ChiLink |

### 4.2 子模块与数据通路

| 子模块 | 职责 | 规格 |
|---|---|---|
| ChiDefs | 节点角色/通道类/opcode 三清单/Size/RespErr/MemAttr/Order 编码/ChiOp 内部编码/ChiWidths | 6.1 |
| ChiConfig | 编译期参数 + require 校验 | 6.2、8 |
| ChiFlit | REQ/RSP/SNP/DAT flit bundle + ChiRnPort/ChiSnPort | 6.3 |
| ChiParams | master→RN-I/slave→SN-F、NodeID 分配、SAM 表、ChiSamDecode | 6.4 |
| ChiTop | 单 router 端口骨架 + 每 RN SAM 译码（tgtId 可观测），通道内部 tie-off | 6.5 |
| ChiLink | ChiTxCredit/ChiLinkFsm/ChiTxLink | 6.6 |
| ChiRouter | ChiChannelRouter（单类 TgtID 路由）/ChiRouterPort/ChiRouter4（四类） | 6.7 |
| ChiNetwork | ChiQosMap/ChiNetLink/ChiNetNode/ChiNetRouter/ChiNetworkParams（BFS） | 6.8 |
| ChiNetworkTop | 多 router 网络顶层（本地 RN/SN 端口 + link 交叉接线） | 6.9 |
| ChiGates | G1 按通道类条件证明（复用 FabricGraph CDG/dateline） | 6.10 |
| ChiProtocol | ChiIdPool（TxnID/DBID）/ChiRetryCtrl（P-Credit） | 6.11 |
| ChiTransaction | ChiWriteSink/ChiReadSource/ChiBurstSplit | 6.12 |
| ChiOrder | ChiOrderRule/ChiOrderCtrl | 6.13 |
| ChiAtomic | ChiAtomicOp（金锚）/ChiAtomicAlu/ChiAtomicEngine | 6.14 |
| ChiDvm | ChiDvmMsg/ChiDvmMn | 6.15 |
| ChiReserved | 使用门禁/Resp 编码/Snp* 空间/ChiRnFPort/ChiHnFPort | 6.16 |
| ChiAxiBridge | ChiAxiAddr/R/W/B bundle + ChiAxiReadBridge/ChiAxiWriteBridge | 6.17 |
| ChiNi | ChiSamDecode.withHit + ChiNiError（未命中 NDERR） | 6.18 |
| ChiAsyncLink | 每通道类 flit CDC（复用 LmAsyncLink） | 6.19 |
| ChiPartition | ChiPartitionParams/Top/Stub/WrapperTop | 6.20 |
| ChiNodeIds | NodeID 段序装配（RN→SN→HN→MN + 覆盖） | 6.4 |

## 5. 功能行为规范

### 5.1 非一致性读（LM-F005）

```text
RN --REQ(ReadNoSnp, TxnID=A, Size=6)--> SN
SN --DAT(CompData, TxnID=A, DataID=k*idStep)--> RN   // nFlits 个 flit
```

ChiReadSource 单事务：接受 REQ 锁存 TxnID/SrcID/64B 读字，逐拍发 CompData，`data = word >> (beat*datWidth)`，`DataID = beat*idStep`（idStep=datWidth/128），BE 全 1。nFlits 拍后回 idle。

### 5.2 非一致性写拉模型（LM-F006）

```text
RN --REQ(WriteNoSnp*, TxnID=A)--> SN
SN --RSP(DBIDResp, TxnID=A, DBID=B)--> RN
RN --DAT(NonCopyBackWrData, TxnID=B, DataID, BE)--> SN   // nFlits 个
SN --RSP(Comp, TxnID=A)--> RN
```

ChiWriteSink FSM：Idle→Grant→Data→Comp。Idle 收 REQ 且 DBID 池可分配→锁存 TxnID/SrcID/DBID；Grant 发 DBIDResp；Data 收 flit 按 `DataID*128` 偏移置入 64B 重组缓冲；Comp 发 Comp 且 wrDone 输出重组字，释放 DBID。**写数据必须凭 DBID 授权后发出**（拉模型）。

### 5.3 Atomic（LM-F007）

8 ALU op：Add/Clr/Eor/Set/Smax/Smin/Umax/Umin。Smax/Smin 按 width 位符号比较。ChiAtomicEngine 在接受拍组合算出结果、下一拍寄存输出旧值 mem（retValid 拉高拍有效）；Device 命令→RespErr=NDERR 且 mem 不变。引擎内部持单个 width 位存储字（非外接存储接口）。金锚 ChiAtomicOp.ref（位宽感知，符号 op 解释顶位）。

### 5.4 DVM（LM-F008）

```text
RN --REQ(DVMOp, TxnID=A)--> MN
MN --RSP(DBIDResp, DBID=B)--> RN
RN --DAT(payload)--> MN
MN --SNP(SnpDVMOp)--> 各 RN-D        // 广播
RN-D --RSP(SnpResp)--> MN           // 收集
MN --RSP(Comp, TxnID=A)--> RN
```

ChiDvmMn FSM：Idle→Grant→Payload→Broadcast→Collect→Comp。DBID 直接复用请求 TxnID（不另分配池）；payload 为单 DAT flit，窥探地址取其 data 低 addrWidth 位；SnpDVMOp.tgtId 按目标序号 p 直接给出。广播/收集用逐目标 mask（sent/collected）保部分进度；全收集后 Comp。消息族 TLBI/BPI/VIC/SYNC（TLBI 子类型 VMALL/VMALLS1/ASID/VA/VAA/IPAS2/IPA），SYNC 保证此前 DVM 完成。

### 5.5 排序（LM-F009）

ChiOrderRule：`Device→RequestOrder`，`Normal→NoOrder`。ChiOrderCtrl FSM：Idle→Receipt→WaitComp→WaitAck。收有序 REQ→发 ReadReceipt→等 Comp→等 CompAck；**Comp 单独不关闭追踪，仅 CompAck.fire 产生 done**。OWO 预留。

### 5.6 三级流控（LM-F010/F011/F012）

| 级 | 机制 | 不变量 |
|---|---|---|
| 链路 L-Credit | ChiTxCredit：lcrdv 增、send 减，持信用才发 | cnt≤DEPTH（RTL 以 assert 守卫；token 模型以结构性门控证明） |
| 事务 P-Credit | ChiRetryCtrl：满→RetryAck+pend，释放→PCrdGrant | acc+retr==total；retr==gr+pend |
| 数据缓冲 DBID | ChiIdPool：alloc 弹自由 ID，release 归还 | granted-released==popcount(busy) |

链路激活 ChiLinkFsm 四态：STOP→(sactive)→ACTIVATE→(txAck&&rxReq)→RUN→(!sactive)→DEACTIVATE→(!txAck&&!rxReq)→STOP。ChiTxLink 仅在 RUN 且持信用时放行 flit。

### 5.7 路由与网络（LM-F002/F003/F004/F015）

- SAM：ChiSamDecode 按窗口 `base<=addr<=end` 译码出 TgtID（未命中暂译 0，错误响应待协议引擎完善）。
- ChiChannelRouter：每输入 Queue（破组合环），routeVec[TgtID]→输出端口，每输出 RR 仲裁（LmRrArbiterN）。
- ChiRouter4：四类各一个 ChiChannelRouter，独立队列/仲裁；SNP 按 snpEnable 条件实例化，否则 tie-off。
- ChiNetworkParams：BFS 求每 router 到各 NodeID 的首跳→路由表（四类共享）；本地端口=RN 后 SN（按 NodeID），link 端口按 incident link。
- ChiQosMap：QoS≥8→ctl(0)，≥4→lat(1)，否则 bulk(2)；vc=类×2+半区。

### 5.8 AXI4 桥接与事务化（LM-F013/F016）

ChiBurstSplit.split(addr,totalBytes)→≤64B 事务序列。ChiAxiReadBridge：AR(64B burst)→ReadNoSnp→收 nFlits CompData 重组→发 nFlits R beat（last 末拍）。ChiAxiWriteBridge：AW→收 W beat 重组→WriteNoSnp→DBIDResp→发 nFlits DAT(TxnID=DBID)→Comp→B。**W 通道在 AW 被接受后才 ready**。

## 6. 子模块规格

握手统一 Decoupled（valid/ready/bits），fire=valid&&ready。fabric 侧端口须 Flipped 镜像节点朝向；未用 rx 通道 tie-off（valid=0/bits=0），未用 tx 通道 ready=1。下文每节按 功能 / 参数 / 接口 / 行为规则 / 用法 / 限制 描述。

### 6.1 ChiDefs —— 编码与常量（纯 Scala object，无硬件）

**功能**：集中定义节点角色、通道类、opcode 名集三分类、Size/RespErr/MemAttr/Order 编码、Loom 内部 opcode 符号编码与位宽派生函数。是其余 block 的唯一编码真源。

**对象与成员**：

| 对象 | 成员 | 说明 |
|---|---|---|
| ChiNodeKind | RnI/RnD/RnF/SnF/SnI/HnI/HnF/Mn；isRequest/isSubordinate/isHome/hasSnpRx | 角色枚举；hasSnpRx 仅 RnD/RnF |
| ChiChannel | Req/Rsp/Snp/Dat | 四通道类 |
| ChiOpcode | atomicAlu(8)、reqSupported/rspSupported/datSupported/snpSupported、reserved、unsupported、isSupported(channel,name) | 名集三分类；reqSupported=ReadNoSnp/WriteNoSnpFull/WriteNoSnpPtl + AtomicStore{8}/AtomicLoad{8}/AtomicSwap/AtomicCompare + DVMOp |
| ChiSize | maxBytes=64、encode(bytes)、decode(code) | encode 要求 2 幂∈[1,64]，返回 log2 |
| ChiRespErr | OK=0/EXOK=1/DERR=2/NDERR=3 | 错误编码 |
| ChiMemAttr | device(m)/cacheable(m)、normalNonCacheable=0、deviceNRnE=2 | bit1=Device、bit2=Cacheable |
| ChiOrder | NoOrder=0/RequestOrder=2/EndpointOrder=3 | 排序编码 |
| ChiWidths | beWidth(dw)=dw/8、dataIdWidth=2、flitCount(bytes,dw) | flit 数=(bytes+perFlit-1)/perFlit |
| ChiOp | 见 7.7 | Loom 内部符号编码（非对外 CHI hex） |

**用法**：生成器与测试统一引用，禁止散落裸 Int 编码。`ChiOpcode.isSupported` 供 ChiReserved 门禁；`ChiWidths.flitCount` 供各引擎算 beat 数。

**限制**：ChiOp 为内部编码，对外 CHI 合规 hex 在桥接层对齐；reserved/unsupported 名集见 14.2/14.3。

### 6.2 ChiConfig —— 编译期配置

**功能**：以 case class 承载全部生成参数并 require 校验；`fromIr` 从 loom/v1 IR 派生配置（不改 IR schema）。

**参数**：见 8.1 表（datWidth/txnIdWidth/outstanding/nodeIdWidth/addrWidth/atomicEnable/atomicEntries/dvmEnable/portMode/pcrdTypes/lcreditDepth）。派生值 `beWidth=datWidth/8`、`maxNodes=1<<nodeIdWidth`。

**行为规则**：构造即校验，非法值抛 require（编译/elaborate 期失败，非运行期）。`fromIr`：addrWidth 取首 master（非标准档回落 48）；outstanding=max(64, IR 端点最大值)；datWidth 固定 256。

**用法**：`ChiConfig()` 取默认；`ChiConfig(datWidth=512, dvmEnable=false)` 覆盖；`ChiConfig.fromIr(ir)` 接 IR。

**限制**：datWidth 无 64b 档；fromIr 暂不随 IR link width 改 fabric 位宽（端点窄位宽适配待 NI 故事）。

### 6.3 ChiFlit —— flit 与节点端口

**功能**：定义四通道 flit bundle 与 RN/SN 节点端口 bundle。

#### 6.3.1 ChiReqFlit（REQ，opcode 7b）

| 字段 | 宽度 | 含义 |
|---|---:|---|
| qos | 4 | 优先级 |
| tgtId / srcId | nodeIdWidth | 目的/源 Node ID |
| txnId | txnIdWidth | 事务号 |
| opcode | 7 | REQ opcode |
| addr | addrWidth | 字节地址 |
| size | 3 | log2 字节数（0=1B…6=64B） |
| memAttr | 4 | {Allocate,Cacheable,Device,EWA} |
| order | 2 | 00 无/10 Request/11 Endpoint |
| allowRetry | 1 | 允许 retry |
| pcrdType | 4 | P-Credit 类型 |
| expCompAck | 1 | 期望 CompAck |
| returnNid / returnTxnId | nodeIdWidth/txnIdWidth | 数据回给谁（DMT 预留） |
| ns | 1 | 非安全 |

#### 6.3.2 ChiRspFlit（RSP，opcode 5b）

| 字段 | 宽度 | 含义 |
|---|---:|---|
| qos/tgtId/srcId/txnId | 同上 | 公共 |
| opcode | 5 | RSP opcode |
| dbid | txnIdWidth | 数据缓冲号 |
| respErr | 2 | OK/EXOK/DERR/NDERR |
| resp | 3 | 响应状态（cache 编码预留） |
| pcrdType | 4 | P-Credit 类型 |

#### 6.3.3 ChiDatFlit（DAT，opcode 4b）

| 字段 | 宽度 | 含义 |
|---|---:|---|
| qos/tgtId/srcId/txnId | 同上 | 公共 |
| opcode | 4 | DAT opcode |
| dataId | 2 | 64B 内 16B chunk 位置 |
| be | datWidth/8 | 字节使能 |
| data | datWidth | 数据 |
| respErr | 2 | 错误 |
| resp | 3 | 响应状态 |
| homeNid | nodeIdWidth | Home Node ID |

#### 6.3.4 ChiSnpFlit（SNP，opcode 5b）

| 字段 | 宽度 | 含义 |
|---|---:|---|
| qos/tgtId/srcId/txnId | 同上 | 公共 |
| opcode | 5 | SNP opcode（一期仅 SnpDVMOp） |
| addr | addrWidth | 地址 |
| fwdNid / fwdTxnId | nodeIdWidth/txnIdWidth | 转发目标（DCT 预留） |

#### 6.3.5 节点端口

| 端口 | 方向（节点视角） | 通道 |
|---|---|---|
| ChiRnPort | req=Decoupled(Req)；rsp/dat/snp=Flipped；txRsp/txDat=Decoupled | RN 发 REQ/RSP(CompAck/SnpResp)/DAT(写数据)，收 RSP/DAT/SNP |
| ChiSnPort | req=Flipped(Req)；rsp/dat=Decoupled | SN 收 REQ，发 RSP/DAT |

fabric 顶层 io.rn=Flipped(ChiRnPort)、io.sn=Flipped(ChiSnPort)（镜像节点朝向）。ChiRnFPort/ChiHnFPort 占位见 6.16。

**用法**：引擎/桥/路由统一以这些 bundle 连线；填 flit 时先 `0.U.asTypeOf` 清零再赋有效字段，避免未初始化。

### 6.4 ChiParams / ChiSamDecode —— 节点模型与 SAM

**功能**：把 IR 端点 elaborated 成 CHI 节点模型（角色/NodeID/时钟/SAM），并提供地址→TgtID 译码器。

**数据模型**：

| 类型 | 字段 | 说明 |
|---|---|---|
| ChiNode | id/kind/nodeId/clock | 一个节点 |
| ChiSamEntry | base/size/tgtId；end=base+size-1 | 一个地址窗口 |
| ChiParams | name/cfg/rnNodes/snNodes/sam | 生成模型；nRn/nSn |

**行为规则**：`fromIr` master→RnI、slave→SnF；NodeID 确定性分配——RN 按端点序 0..nRn-1，SN 接续 nRn..；SAM 由 `AddressMap.windows(slaves)` 生成，每窗口 tgtId=该 slave NodeID。require：RN/SN 非空、NodeID 唯一且∈[0,maxNodes)、SAM 每 SN 一项且 size≥1。

**ChiSamDecode**：`apply(addr:UInt, addrWidth, sam:Seq[ChiSamEntry], outWidth):UInt`。并行窗口比较 `base<=addr<=end`，命中赋 tgtId，未命中→0（后写覆盖，多窗口重叠时取最后一个）。

**用法**：`ChiParams.fromIr(ir,cfg)` 得模型；NI 侧 `ChiSamDecode(req.addr,...)` 一次性译码填 req.tgtId，fabric 此后只认 TgtID。

**限制**：未命中暂译 0，NDERR/DECERR 错误响应待协议引擎；窗口重叠语义为"最后命中"，IR 应保证不重叠。

### 6.5 ChiTop —— 单 router 端口骨架

**功能**：单 router 配置的 CHI fabric 顶层骨架，暴露 RN/SN 端口并做每 RN SAM 译码（可观测），通道内部安全 tie-off 以保证确定性发射。

**参数**：`p:ChiParams`。

**接口**：

| 端口 | 方向 | 含义 |
|---|---|---|
| rn | Vec(nRn, Flipped(ChiRnPort)) | RN 节点端口 |
| sn | Vec(nSn, Flipped(ChiSnPort)) | SN 节点端口 |
| tgtId | Output(Vec(nRn, UInt(nodeIdWidth))) | 每 RN 的 SAM 译码结果（观测） |

**行为规则**：`tgtId(i)=ChiSamDecode(rn(i).req.bits.addr,...)`；rn.req.ready=1，rn.rsp/dat/snp.valid=0；sn.req.valid=0，sn.rsp/dat.ready=1。即骨架不搬运 flit，仅暴露端口与译码。

**用法**：`loom emit-chi` 单 router 分支发射本模块；完整单 router 交换待集成 ChiRouter4（14.1）。

**限制**：当前为骨架，无真实通道传输。

### 6.6 ChiLink —— 链路流控与激活

#### 6.6.1 ChiTxCredit（发送侧 flit L-Credit 计数器）

**参数**：maxDepth∈[1,15]。

| 端口 | 方向 | 含义 |
|---|---|---|
| lcrdv | Input(Bool) | 接收侧授予 1 信用 |
| send | Input(Bool) | 本拍发出 1 flit |
| canSend | Output(Bool) | 持信用（cnt≠0） |
| count | Output(UInt) | 当前信用数 |

**行为**：lcrdv&&!send→cnt+1；send&&!lcrdv→cnt-1；同拍保持。assert 拒绝满时 grant（overflow）/空时 send（underflow）。

**用法**：send 接 deq.fire；canSend 门控发送。环境须保证不越界（assert 为守卫非钳位）。

#### 6.6.2 ChiLinkFsm（链路激活 FSM，TX/RX 合视图）

| 端口 | 方向 | 含义 |
|---|---|---|
| txSactive/rxSactive | Input(Bool) | 本/对端活动请求 |
| txAck/rxReq | Input(Bool) | 对端确认/请求 |
| txReq/rxAck | Output(Bool) | 本端请求/确认（ACTIVATE 或 RUN 拉高） |
| run | Output(Bool) | RUN 态 |
| state | Output(UInt(2)) | 0 STOP/1 ACTIVATE/2 RUN/3 DEACTIVATE |

**行为**：STOP→(txSactive‖rxSactive)→ACTIVATE→(txAck&&rxReq)→RUN→(!txSactive&&!rxSactive)→DEACTIVATE→(!txAck&&!rxReq)→STOP。

#### 6.6.3 ChiTxLink[T]（信用门控 TX 链路）

**参数**：gen（flit 类型）、depth（信用深度）。

| 端口 | 方向 | 含义 |
|---|---|---|
| enq | Flipped(Decoupled(gen)) | 上游 flit 入 |
| deq | Decoupled(gen) | 下游 flit 出 |
| lcrdv/txSactive/rxSactive/txAck/rxReq | Input | 接对端/信用 |
| txReq/rxAck/run | Output(Bool) | FSM 观测 |
| creditCount | Output(UInt) | 信用观测 |

**行为**：`ok=fsm.run && cred.canSend`；deq.valid=enq.valid&&ok；enq.ready=deq.ready&&ok；cred.send=deq.fire。即仅 RUN 且持信用才放行。

**用法**：每通道每 link 插一个；RX 侧信用授予与路由集成待网络层（14.1，当前 ChiNetworkTop 未插入）。

### 6.7 ChiRouter —— TgtID 路由交换

#### 6.7.1 ChiChannelRouter[T]（单通道类 VC 路由器）

**参数**：gen、nIn、nOut、routeTable(TgtID→输出端口)、depth（每 VC 队列）、tgtOf(T=>UInt)、qm:ChiQosMap、cutOut:Seq[Boolean]（每输出口是否 cut）。require nIn/nOut≥1、depth≥1、routeTable 非空、cutOut 长度=nOut。

| 端口 | 方向 | 含义 |
|---|---|---|
| in | Vec(nIn, Flipped(Decoupled(ChiVcBundle(gen)))) | 输入 flit+VC sideband |
| out | Vec(nOut, Decoupled(ChiVcBundle(gen))) | 输出 flit+VC sideband |

**行为**：每 (输入,VC) 一个 Queue(depth)，入队按 bits.vc 分流；`vc=class*nHalf+half`。**三级仲裁**每输出：① input RR（LmRrArbiterN，每 (out,vc) 跨输入）→ ② half RR（类内跨 nHalf 半区）→ ③ class WRR（LmWrrArbiter，类权重，含 MaxAge 防饥饿）。selVc=classGrant*nHalf+halfGrant。每输出一个 LmPipe(1) 寄存级解耦 ready 链。**cut 翻半区**：cutOut(j)=true 时出口 vc 翻为 class*nHalf+1（0→1，永不回）。每输出每拍至多 1 flit。

**用法**：四类各实例化一个（见 ChiRouter4）；routeTable 由 BFS 生成；cutOut 由 GEN 拓扑派生（ChiNetRouter.outCut）。ChiVcBundle 在节点边界经 ChiVcInject/Eject 与 pure flit 互转。

#### 6.7.2 ChiRouterPort（router 视角四通道端口）

rxReq/rxRsp/rxSnp/rxDat=Flipped(Decoupled)；txReq/txRsp/txSnp/txDat=Decoupled。RX 入 router、TX 出 router。

#### 6.7.3 ChiRouter4（四类独立路由器）

**参数**：c、nPort、routeReq/routeRsp/routeSnp/routeDat、depth、snpEnable。

**接口**：`port=Vec(nPort, ChiRouterPort)`。

**行为**：REQ/RSP/DAT 各一个 ChiChannelRouter 恒实例化；SNP 仅 snpEnable=true 实例化，否则 rxSnp.ready=1、txSnp.valid=0/bits=0。四类独立队列/仲裁，无跨类共享 → 无跨类阻塞。

**用法**：ChiNetworkTop 每 router 一个；单 router 完整交换亦用它（待集成进 ChiTop）。

### 6.8 ChiNetwork —— 网络参数与 QoS 映射

#### 6.8.1 ChiQosMap

`classOf(qos)`：≥8→0(ctl)，≥4→1(lat)，否则 2(bulk)；`vcOf(qos,half)=classOf*2+half`；vcWidth=3；classes=ctl/lat/bulk。

#### 6.8.2 数据模型

| 类型 | 字段 |
|---|---|
| ChiNetLink | id/fromRouter/toRouter/fromPort/toPort/pipe |
| ChiNetNode | nodeId/kind/router/localPort |
| ChiNetRouter | index/name/nLocal/nLink/route(Seq[Int])/linkOfPort；nPort=nLocal+nLink |
| ChiNetworkParams | name/cfg/rn/sn/routers/links/snpEnable；nRn/nSn |

#### 6.8.3 ChiNetworkParams.fromIr

**行为**：router 名排序定 index；master 经 mnet 出向 link 定挂接 router、slave 经 snet 入向 link 定挂接 router；本地端口按 NodeID 排序（RN 后 SN）；link 端口=nLocal+incident 序；BFS（双向邻接）求每 router 到各 NodeID 首跳→route 表（四类共享，class-independent）；不可达回落 0。

**用法**：`ChiNetworkParams.fromIr(ir,cfg,snpEnable)` 供 ChiNetworkTop。

**限制**：route 表 class-independent（四类同表）；pipe 字段当前被 ChiNetworkTop 忽略。

### 6.9 ChiNetworkTop —— 多 router 网络顶层

**功能**：把多 router 拓扑接成完整 CHI 网络：每 router 一个 ChiRouter4，RN/SN 接本地端口，router-router link 交叉接 TX→RX。

**参数**：`p:ChiNetworkParams`。

**接口**：`rn=Vec(nRn, Flipped(ChiRnPort))`、`sn=Vec(nSn, Flipped(ChiSnPort))`。

**行为规则**：
- 本地 RN 端口：rn.req→port.rxReq，port.txRsp/txDat/txSnp→rn.rsp/dat/snp；port.rxRsp/rxDat/rxSnp tie-off，port.txReq.ready=1。
- 本地 SN 端口：port.txReq→sn.req，sn.rsp/dat→port.rxRsp/rxDat；port.rxReq/rxSnp tie-off，port.txRsp/txDat/txSnp.ready=1。
- link：a.txReq→b.rxReq 且 b.txReq→a.rxReq（RSP/DAT/SNP 同理），双向交叉。

**用法**：`loom emit-chi` ≥2 router 分支发射；chain_4r.json 验证多跳 REQ 正向/DAT 反向。

**限制**：link 为直连导线，未插 ChiTxLink（无 L-Credit/FSM/pipe）；本地端口 tie-off 意味着 RN 只发 REQ、SN 只发 RSP/DAT 的简化视图。

### 6.10 ChiGates —— 静态门禁

**功能**：复用 FabricGraph 的拓扑 CDG（SCC+dateline cuts）做 G1 无死锁证明，按通道类输出。

**接口**：`run(ir:Ir, snpEnable:Boolean):Result`；`Result(gates:Vector[(String,Boolean)], errors:List[String])`。

**行为**：`g1ok=fg.findCycle.isEmpty && fg.errors.isEmpty`；G1-REQ/RSP/DAT 恒=g1ok；G1-SNP 仅 snpEnable 时=g1ok，否则=true（未生成即平凡满足，D11"有什么证什么"）。errors=fg.errors.toList。

**用法**：CLI/GUI 门禁聚合调用；与 loom.check.Gates 并列。

### 6.11 ChiProtocol —— ID 池与 P-Credit

#### 6.11.1 ChiIdPool（自由列表 ID 池，TxnID/DBID 复用）

**参数**：depth、idWidth；require depth≥1 且≤2^idWidth。

| 端口 | 方向 | 含义 |
|---|---|---|
| alloc | Input(Bool) | 弹一个自由 ID |
| release | Input(Bool) | 归还一个 ID |
| releaseId | Input(UInt(idWidth)) | 归还的 ID |
| canAlloc | Output(Bool) | 有自由 ID（freeCnt≠0） |
| allocId | Output(UInt(idWidth)) | 队头自由 ID |
| outstanding | Output(UInt) | depth-freeCnt |

**行为**：mem 初始 0..depth-1 全自由；head/tail 环形（wrap 取模 depth）；allocFire=alloc&&canAlloc 弹头；release 写尾。freeCnt=freeCnt-allocFire+release。assert：alloc 无自由 ID / release 满。

**用法**：DBID 分配（ChiWriteSink）或 TxnID 追踪；耗尽即反压点（canAlloc=0 挡 REQ）。

#### 6.11.2 ChiRetryCtrl（P-Credit 准入）

**参数**：depth≥1。

| 端口 | 方向 | 含义 |
|---|---|---|
| reqAllowRetry | Input(Bool) | 来了一个 AllowRetry=1 请求 |
| release | Input(Bool) | 释放一个事务槽 |
| accept | Output(Bool) | 准入（reqAllowRetry&&hasRoom） |
| retryAck | Output(Bool) | 拒绝（reqAllowRetry&&!hasRoom） |
| pcrdGrant | Output(Bool) | 授信用（release&&pend≠0） |
| outstanding | Output(UInt) | occ |

**行为**：hasRoom=occ<depth；occ+=accept-release；pend+=retryAck-pcrdGrant。assert：release 空。请求方收 RetryAck 后等 PCrdGrant，再 AllowRetry=0 重发。

**用法**：SN/Home 侧事务准入；与 ChiIdPool 配合（池管 ID，本块管槽位准入）。

### 6.12 ChiTransaction —— 读写引擎与事务化

#### 6.12.1 ChiWriteSink（SN 侧写宿，拉模型）

**参数**：c、depth（DBID 池深度）；nFlits=flitCount(64,datWidth)。

| 端口 | 方向 | 含义 |
|---|---|---|
| req | Flipped(Decoupled(ChiReqFlit)) | WriteNoSnp 请求 |
| rsp | Decoupled(ChiRspFlit) | DBIDResp/Comp |
| dat | Flipped(Decoupled(ChiDatFlit)) | NonCopyBackWrData |
| wrDone | Decoupled(UInt(512)) | 重组 64B 写字（到存储） |

**行为（FSM Idle/Grant/Data/Comp）**：req.ready=Idle&&pool.canAlloc；Idle 收 REQ 锁存 reqTxn/reqSrc/dbid(=pool.allocId)，清 dataBuf；Grant 发 DBIDResp(txnId=reqTxn,dbid,tgtId=reqSrc)；Data 收 flit `dataBuf |= data.pad(512) << (dataId*128)`，beat 计满 nFlits→Comp；Comp 发 Comp 且 wrDone.valid，rsp.fire&&wrDone.fire 同拍释放 DBID 回 Idle。

**用法**：SN 侧接存储模型 wrDone；DBID 池满即反压 REQ。

**限制**：单 outstanding、固定 64B；BE 未参与重组（按全字）。

#### 6.12.2 ChiReadSource（SN 侧读源）

**参数**：c；nFlits、idStep=datWidth/128。

| 端口 | 方向 | 含义 |
|---|---|---|
| req | Flipped(Decoupled(ChiReqFlit)) | ReadNoSnp 请求 |
| dat | Decoupled(ChiDatFlit) | CompData 输出 |
| rdData | Input(UInt(512)) | 64B 读字（来自存储模型） |

**行为（FSM Idle/Data）**：req.ready=Idle；Idle 收 REQ 锁存 reqTxn/reqSrc/word(=rdData)；Data 逐拍发 CompData，`data=(word>>(beat*datWidth))(datWidth-1,0)`，`dataId=beat*idStep`，BE 全 1，tgtId=reqSrc；beat 计满回 Idle。

**用法**：SN 侧 rdData 接存储读口；与 ChiAxiReadBridge.dat 对接构成读通路。

**限制**：单 outstanding、固定 64B。

#### 6.12.3 ChiBurstSplit（burst 事务化，纯函数）

`split(addr:Long,totalBytes:Int):Seq[Txn(addr,bytes)]`；require totalBytes≥1；贪心切 ≤64B 块，地址递增。供 AXI 多 burst→多 CHI 事务。

### 6.13 ChiOrder —— 排序规则与闭环

#### 6.13.1 ChiOrderRule

`orderFor(memAttr)`：Device→RequestOrder，否则 NoOrder。OWO 预留。

#### 6.13.2 ChiOrderCtrl（有序请求闭环）

**参数**：c。

| 端口 | 方向 | 含义 |
|---|---|---|
| req | Flipped(Decoupled(ChiReqFlit)) | 有序请求 |
| readReceipt | Decoupled(ChiRspFlit) | ReadReceipt 输出 |
| comp | Input(Bool) | 收到 Comp 指示 |
| compAck | Flipped(Decoupled(ChiRspFlit)) | CompAck 输入 |
| done | Output(Bool) | 闭环完成（WaitAck&&compAck.fire） |

**行为（FSM Idle/Receipt/WaitComp/WaitAck）**：req.ready=Idle；Idle 收 REQ 锁存 reqTxn/reqSrc→Receipt；Receipt 发 ReadReceipt→WaitComp；WaitComp 等 comp→WaitAck；WaitAck 等 compAck.fire→Idle。**Comp 单独不关闭，仅 CompAck 关闭**（ExpCompAck 语义）。

**用法**：Device RequestOrder 请求的追踪器；comp 由完成逻辑驱动，compAck 由请求方回。

**限制**：单 outstanding。

### 6.14 ChiAtomic —— Atomic 运算与执行

#### 6.14.1 ChiAtomicOp（金锚，纯函数）

ops=Add/Clr/Eor/Set/Smax/Smin/Umax/Umin；code(op)=index；`ref(op,old,operand,width)` 位宽感知：mask 截断、符号 op 解释顶位、结果再 mask。Add 回绕、Clr=o&~b、Eor/Set 位运算、Smax/Smin 符号比较、Umax/Umin 无符号比较。

#### 6.14.2 ChiAtomicAlu（组合 8-op ALU）

**参数**：width。op=Input(UInt(3))、a/b=Input(UInt(width))、y=Output(UInt(width))。MuxLookup 8 op；Add 用 `+&` 截 width；Smax/Smin 用 asSInt 比较。默认（op 越界）y=a。

#### 6.14.3 ChiAtomicEngine（SN 侧执行引擎）

**参数**：width。

| 端口 | 方向 | 含义 |
|---|---|---|
| cmd | Flipped(Decoupled({op:UInt3,operand:UInt(width),device:Bool})) | atomic 命令 |
| retValid | Output(Bool) | 结果有效（cmd.fire 后一拍） |
| retVal | Output(UInt(width)) | 旧值 |
| respErr | Output(UInt(2)) | OK/NDERR |
| memWord | Output(UInt(width)) | 更新后存储字 |

**行为**：内部 mem RegInit(0)；cmd.ready 恒 1；cmd.fire 拍组合算 alu(mem,operand)，下一拍寄存 retReg=旧 mem、vReg=1；device→errReg=NDERR 且 mem 不变，否则 errReg=OK、mem=alu.y；无 fire 拍 vReg=0。

**用法**：测试/集成逐拍限速喂 cmd（无反压）；retVal 为 Load/Swap/Compare 语义的旧值。

**限制**：单存储字、cmd 式（未接完整 REQ/DAT 拉流）、单 outstanding。

### 6.15 ChiDvm —— DVM 消息与 MN 引擎

#### 6.15.1 ChiDvmMsg

families=TLBI/BPI/VIC/SYNC；tlbiSubtypes=VMALL/VMALLS1/ASID/VA/VAA/IPAS2/IPA；isSync(f)=f=="SYNC"。

#### 6.15.2 ChiDvmMn（MN DVM 引擎）

**参数**：c、nRnD≥1。

| 端口 | 方向 | 含义 |
|---|---|---|
| req | Flipped(Decoupled(ChiReqFlit)) | DVMOp |
| rsp | Decoupled(ChiRspFlit) | DBIDResp/Comp |
| dat | Flipped(Decoupled(ChiDatFlit)) | payload |
| snp | Vec(nRnD, Decoupled(ChiSnpFlit)) | SnpDVMOp 广播 |
| snpResp | Vec(nRnD, Flipped(Decoupled(ChiRspFlit))) | SnpResp 收集 |

**行为（FSM Idle/Grant/Payload/Broadcast/Collect/Comp）**：Idle 收 REQ 锁存 reqTxn/reqSrc，dbid=reqTxn，清 sent/collected mask；Grant 发 DBIDResp；Payload 收单 flit，snpAddr=data 低 addrWidth 位；Broadcast 逐目标发 SnpDVMOp(opcode,tgtId=p,addr=snpAddr)，sent(p) 防重发，全 sent→Collect；Collect 逐目标收 SnpResp，collected(p) 防重收，全 collected→Comp；Comp 发 Comp 回 Idle。

**用法**：MN 节点；snp 接各 RN-D 的 SNP RX，snpResp 接其 SnpResp TX。

**限制**：单 DVM outstanding；payload 单 flit；DBID 复用 TxnID。

### 6.16 ChiReserved —— 一致性预留与门禁

**功能**：cache 状态 Resp 编码与 Snp* 一致性 opcode 空间预留（不实现）；一期使用门禁拒绝 reserved/unsupported；RN-F/HN-F 端口占位。

**成员**：RespI=0/RespSC=1/RespUC=2/RespUD=3/RespSD=4；snpReserved={SnpShared:0x01,SnpUnique:0x07,SnpCleanInvalid:0x09,SnpMakeInvalid:0x0a,SnpOnce:0x0b}。

**门禁**：`checkOpcode(channel,name):Option[String]`——支持→None；reserved→Some("CHI-GATE: ... reserved ...")；否则 Some("... unsupported ...")。

**端口占位**：

| 端口 | 方向 | 说明 |
|---|---|---|
| ChiRnFPort | req=Decoupled；rsp/dat/snp=Flipped | 同 ChiRnPort（含 SNP RX），无 cache 逻辑 |
| ChiHnFPort | req/rxRsp/rxDat=Flipped；rsp/dat/snp=Decoupled | 收 REQ/SnpResp/CopyBack，发 RSP/DAT/SNP；无序列化/snoop-filter |

**用法**：生成前对所用 opcode 调 checkOpcode 把关；未来一致性故事复用 SNP 通道（与 DVM 共享）与这些端口形状。

### 6.17 ChiAxiBridge —— AXI4↔CHI 桥

#### 6.17.1 AXI payload bundle

| bundle | 字段 |
|---|---|
| ChiAxiAddr | addr(addrWidth)/id(txnIdWidth)/len(8) |
| ChiAxiR | data(datWidth)/id/resp(2)/last |
| ChiAxiW | data(datWidth)/strb(beWidth)/last |
| ChiAxiB | id/resp(2) |

#### 6.17.2 ChiAxiReadBridge（RN 侧读桥）

**参数**：c；nFlits。

| 端口 | 方向 | 含义 |
|---|---|---|
| ar | Flipped(Decoupled(ChiAxiAddr)) | AXI 读地址 |
| r | Decoupled(ChiAxiR) | AXI 读数据 |
| req | Decoupled(ChiReqFlit) | CHI ReadNoSnp |
| dat | Flipped(Decoupled(ChiDatFlit)) | CHI CompData |

**行为（FSM Idle/Req/Data/Resp）**：ar.ready=Idle；Idle 收 AR 锁存 txn=id/addr；Req 发 ReadNoSnp(size=encode(64))；Data 收 flit `dataBuf |= data.pad(512)<<(dataId*128)`，计满 nFlits→Resp；Resp 逐拍发 R，`data=(dataBuf>>(beatIdx*datWidth))`，id=txn，resp=OK，last=末拍；末拍 fire 回 Idle。

#### 6.17.3 ChiAxiWriteBridge（RN 侧写桥）

**参数**：c；nFlits、idStep。

| 端口 | 方向 | 含义 |
|---|---|---|
| aw | Flipped(Decoupled(ChiAxiAddr)) | AXI 写地址 |
| w | Flipped(Decoupled(ChiAxiW)) | AXI 写数据 |
| b | Decoupled(ChiAxiB) | AXI 写响应 |
| req | Decoupled(ChiReqFlit) | CHI WriteNoSnpFull |
| rsp | Flipped(Decoupled(ChiRspFlit)) | CHI DBIDResp/Comp |
| dat | Decoupled(ChiDatFlit) | CHI NonCopyBackWrData |

**行为（FSM Idle/Wcollect/Req/Grant/Data/Comp/Resp）**：aw.ready=Idle，w.ready=Wcollect（**AW 接受后 W 才 ready**）；Idle 收 AW 锁存 txn/addr；Wcollect 收 W `dataBuf |= data.pad(512)<<(wCnt*datWidth)`，last→Req；Req 发 WriteNoSnpFull(size=64)；Grant 收 DBIDResp 锁存 dbid；Data 逐拍发 DAT(txnId=dbid,dataId=beatIdx*idStep,BE 全 1)，计满→Comp；Comp 收 Comp→Resp；Resp 发 B(id=txn,resp=OK) 回 Idle。

**用法**：AXI 主接 ar/aw/w/r/b；桥 CHI 侧接网络 RN 端口；SN 侧配 ChiReadSource/ChiWriteSink 成端到端通路（13.2）。

**限制**：单 burst、固定 64B；忽略 len/strb；resp 恒 OK（不透传 RespErr）；多 burst/非对齐/错误透传待完善。

### 6.18 ChiNi —— NI 错误门禁（HW-05）

**功能**：未命中 SAM 的请求就地回 Comp(RespErr=NDERR)，不进 fabric；命中透传。

**ChiSamDecode.withHit**：`withHit(addr,addrWidth,sam,outWidth):(UInt,Bool)` 返回 (tgtId, hit)；窗口内 hit=1，未命中 hit=0/tgtId=0。`apply` 仍返回 tgtId（兼容）。

**ChiNiError(c)**：

| 端口 | 方向 | 含义 |
|---|---|---|
| req | Flipped(Decoupled(ChiReqFlit)) |  incoming 请求 |
| hit | Input(Bool) | SAM 命中（来自 withHit） |
| fwd | Decoupled(ChiReqFlit) | 命中时透传 fabric |
| rsp | Decoupled(ChiRspFlit) | 未命中时 Comp NDERR |

**行为**：fwd.valid=req.valid&&hit；req.ready=Mux(hit, fwd.ready, st==Idle)。req.fire&&!hit→锁存 txnId/srcId→sComp；sComp 发 Comp(respErr=NDERR,txnId,tgtId=srcId)，fire→Idle。

**用法**：RN 注入点接 withHit(req.addr)→hit，ChiNiError 串在 req 与 fabric 之间。网络级集成待 GEN/NI 包装（14.1）。

### 6.19 ChiAsyncLink —— 每通道 CDC 链路（HW-04）

**功能**：CHI flit 跨时钟域传输，复用 v1 LmAsyncLink（格雷码异步 FIFO + 保守跨域信用池）。

**参数**：gen（flit 类型）、depth（2 幂≥2）。

| 端口 | 方向 | 含义 |
|---|---|---|
| dstClk / dstReset | Input(Bool) | 目的时钟域（Bool+内部 asClock） |
| in | Flipped(Decoupled(gen)) | 源域 flit 入 |
| out | Decoupled(gen) | 目的域 flit 出 |
| creditUsed | Output(UInt(16)) | 源域计在途信用 |

**行为**：flit.asUInt↔LmAsyncLink↔asTypeOf(gen)。信用返回经 2FF 格雷码同步延迟，源域 used=alloc-relSync≤depth 恒成立（结构性保守，不会过发）。每通道类（REQ/RSP/SNP/DAT）一个实例。

**形式化**：token_chi_async_credit.sv 证 used≤DEPTH、relSync≤relCnt≤allocCnt（yosys SAT PASS）。

**用法**：async link 每通道插一个；dstClk 接目的域时钟。网络级插入待 GEN IR cdc 映射（14.1）。**仿真注意**：复位期须 toggle dstClk 给 dst 域复位沿；输出只在 dst 上升沿拍采样。

### 6.20 ChiPartition —— 分区发射三件套（HW-06）

**ChiPartitionParams(name, full:ChiNetworkParams, members:Seq[Int], pipe:Int)**：从全网切出本地 routers/nodes/internalLinks；outLinks/inLinks 合成 boundary（按 id 排序）；boundaryPort(l) 给本地 (router,port)。require members 非空、pipe≥1。

**ChiPartitionTop(pp)**：

| 端口 | 方向 | 含义 |
|---|---|---|
| rn | Vec(本地 RN, Flipped(ChiRnPort)) | 本地请求节点 |
| sn | Vec(本地 SN, Flipped(ChiSnPort)) | 本地从节点 |
| boundary | Vec(boundary, ChiRouterPort) | 边界 CHI 口（VC sideband） |

**行为**：本地 router 各一个 ChiRouter4（路由表继承全网）；本地节点 inject/eject 接线；internalLinks 交叉；boundary 每通道经 LmPipe(≥pipe) 寄存到边界口。

**ChiPartitionStub(pp)**：同端口契约，节点口/边界口安全 tie-off（rx 驱 ready、tx 驱 valid=0），用于孤立 elaborate/仿真。

**ChiWrapperTop(full, parts, asyncCuts)**：例化各 ChiPartitionTop，本地节点口接全网 io.rn/sn；按全局 link id 匹配两分区边界口交叉回接；asyncCuts 内的 cut 每通道插 ChiAsyncLink，否则直连。

**用法**：分区发射 + 回接整网；LoomMain partitions 发射待 GEN 接入（14.1）。

## 7. 数据表示规范

### 7.1 位宽与 flit 数

datWidth∈{128,256,512}（无 64b）；perFlit=datWidth/8；nFlits=64/perFlit（128→4，256→2，512→1）；idStep=datWidth/128（DataID 增量）。

### 7.2 Size 编码

| code | 字节 | code | 字节 |
|---:|---:|---:|---:|
| 0 | 1 | 4 | 16 |
| 1 | 2 | 5 | 32 |
| 2 | 4 | 6 | 64 |
| 3 | 8 | | |

### 7.3 RespErr 编码

00 OK / 01 EXOK / 10 DERR（数据错可继续）/ 11 NDERR（事务错）。

### 7.4 MemAttr 编码

bit3 Allocate / bit2 Cacheable / bit1 Device / bit0 EWA。deviceNRnE=0x2，normalNonCacheable=0x0。

### 7.5 Order 编码

00 NoOrder / 10 RequestOrder / 11 EndpointOrder。

### 7.6 QoS→VC

QoS≥8→ctl(0)，≥4→lat(1)，否则 bulk(2)；vc=类×2+dateline 半区；vcWidth=3。

### 7.7 opcode（ChiOp 内部编码）

REQ：ReadNoSnp=0x04、WriteNoSnpFull=0x18、WriteNoSnpPtl=0x19、DVMOp=0x14、AtomicSwap=0x38、AtomicCompare=0x39。RSP：Comp=0x04、DBIDResp=0x06、CompDBIDResp=0x07、RetryAck=0x03、PCrdGrant=0x08、ReadReceipt=0x09、CompAck=0x0a、SnpResp=0x01。DAT：CompData=0x04、NonCopyBackWrData=0x02。SNP：SnpDVMOp=0x01。

## 8. 配置规范

### 8.1 默认编译期配置（ChiConfig）

| 参数 | 默认 | 可配 | 约束 |
|---|---|---|---|
| datWidth | 256 | 128/256/512 | 仅标准档 |
| txnIdWidth | 8 | 8/12 | |
| outstanding | 64 | ≥1 | |
| nodeIdWidth | 7 | 7/11 | |
| addrWidth | 48 | 32/40/48 | |
| atomicEnable | true | bool | |
| atomicEntries | 4 | ≥1 | |
| dvmEnable | true | bool | false→不生成 MN/SNP |
| portMode | axi4 | axi4/chi/both | |
| pcrdTypes | 1 | ≥1 | |
| lcreditDepth | 4 | 1–15 | |
| qosCtlThreshold | 8 | 0–15 | > qosLatThreshold（无洞无重叠） |
| qosLatThreshold | 4 | 0–15 | |
| qosWeights | Seq(4,2,1) | 3 项，各 1–64 | ctl/lat/bulk 权重 |
| nHalf | 2 | ≥1 | 拓扑派生（GEN 传入）；环 dateline=2 |

派生：`nClasses=3`、`nVc=nClasses*nHalf`、`vcWidth=log2Ceil(nVc)`。QoS 阈值/权重单一真源=IR qos 块，GEN 装配进 ChiConfig，硬件吃 cfg。

### 8.2 编译期约束

ChiConfig require 校验上表范围；ChiParams require RN/SN 非空、NodeID 唯一且在 [0,maxNodes)、SAM 每 SN 一项且 size≥1；ChiIdPool require depth≤2^idWidth；ChiChannelRouter require 输入/输出≥1、depth≥1、routeTable 非空；ChiDvmMn require nRnD≥1。缓冲深度按 BDP（带宽×RTT），默认待 PPA 阶段。

`ChiConfig.fromIr`：addrWidth 取首个 master 的 addrWidth（不在 {32,40,48} 时回落 48）；outstanding=max(64, IR 各端点 outstanding 最大值)；datWidth 固定 256（IR link width 暂不改 fabric 位宽，端点窄位宽适配待 NI 故事）。SAM 由 `AddressMap.windows(slaves)` 生成，每窗口 tgtId=该 slave 的 NodeID。

## 9. 时序与复位规范

### 9.1 复位

各 FSM RegInit 到 Idle/STOP；计数器/池零初值（formal 以零初值抽象复位）。

### 9.2 握手与反压

Decoupled fire=valid&&ready。输入队列（Queue）破组合 ready 链；路由输出 valid 依赖下游 ready（仲裁），输入侧队列隔离防组合环。

### 9.3 信用时序

L-Credit：lcrdv 与 send 同拍则计数保持；RTL 以 assert 拒绝越界（满时再 grant / 空时再 send 即断言失败），环境须遵守；formal token 模型改用结构性门控（grant 仅 cnt<DEPTH、send 仅 cnt>0）以便 SAT 证明。DBID：alloc 在 REQ 接受拍，release 在 Comp 与 wrDone 同拍 fire。

### 9.4 FSM 状态

ChiWriteSink：Idle/Grant/Data/Comp。ChiReadSource：Idle/Data。ChiOrderCtrl：Idle/Receipt/WaitComp/WaitAck。ChiDvmMn：Idle/Grant/Payload/Broadcast/Collect/Comp。ChiLinkFsm：STOP/ACTIVATE/RUN/DEACTIVATE。ChiAxiReadBridge：Idle/Req/Data/Resp。ChiAxiWriteBridge：Idle/Wcollect/Req/Grant/Data/Comp/Resp。

## 10. 异常处理规范

### 10.1 错误码（RespErr）

DERR 数据错（可继续返回完整 flit）；NDERR 事务错（Device Atomic、非法地址）。

### 10.2 Device Atomic 拒绝

ChiAtomicEngine 对 device=1 命令回 NDERR 且 mem 不变（CHI 合规：Atomic 仅 targeting Normal）。

### 10.3 Retry

ChiRetryCtrl 资源满→RetryAck（pend++），释放→PCrdGrant（pend--）；请求方持 credit 后 AllowRetry=0 重发。

### 10.4 仿真断言

ChiIdPool：alloc 无自由 ID / release 满 → 断言失败。ChiRetryCtrl：release 空 → 断言失败。ChiTxCredit：满时 grant（overflow）/ 空时 send（underflow）→ 断言失败。formal token 模型边界须结构性门控（assume 非 yosys 硬约束）。

## 11. 状态与观测规范

### 11.1 状态输出

ChiLinkFsm.state、ChiTop.tgtId、ChiIdPool.outstanding、ChiRetryCtrl.outstanding、ChiAtomicEngine.memWord/respErr 可观测。

### 11.2 形式化

src/loom/formal/ token_chi_lcredit（L-Credit 守恒）/token_chi_dbid（DBID 恰好一次）/token_chi_pcrd（retry 不丢）/token_chi_dvm（收集完整性），yosys `sat -set-init-zero -prove-asserts`，depth 12，全 PASS。ChiGates G1 按生成配置条件证明（SNP 仅 DVM 时纳入）。

## 12. 性能与资源规格

### 12.1 带宽常数

单笔事务≤64B；REQ 每事务 1 flit；DAT 每 flit perFlit 字节。256b 单口：纯读/写 REQ:DAT=1:2，混合全双工恰配平（REQ 100%/DAT 各 100%）。512b 单口：单向 1:1 打平，混合被 REQ 封顶 64B/拍。突破混合顶靠多端口/多 NodeID（CHI 无 2REQ:1DAT 格式）。

### 12.2 实测边界

端到端延迟/吞吐/频率/面积/功耗实测待补；当前为接口峰值与结构上限。

## 13. 集成与使用规范

### 13.1 发射

`loom emit-chi --config <ir.json> --out <dir>`：先跑 IR 校验（有 error 即打印并 exit 3），再按 router 数分流——单 router→ChiTop，≥2 router→ChiNetworkTop（snpEnable=cfg.dvmEnable）。产物含 RTL、`checks.json`、`chi_nodes.json`（name/datWidth/rn/sn/sam）。复用 loom/v1 IR（master/slave/attach/routers/links），IR schema 不变。

### 13.2 桥接连接

AXI 主→ChiAxiReadBridge/ChiAxiWriteBridge 的 ar/aw/w/r/b；桥 CHI 侧 req/rsp/dat 接网络 RN 端口。读桥 dat 接 ChiReadSource.dat（SN 侧），写桥 req/rsp/dat 接 ChiWriteSink（SN 侧）构成 RN↔SN 通路。

### 13.3 示例

chain_4r.json（4 router 链，RN@r0/SN@r3）验证多跳 REQ 正向/DAT 反向；minimal_4x4.json 验证单 router 端口骨架。

## 14. 约束与实现限制

### 14.1 当前实现限制

- ChiReadSource/ChiWriteSink/ChiAtomicEngine/ChiDvmMn/ChiOrderCtrl/ChiAxiBridge 为**单 outstanding/单事务**引擎；多 outstanding 流水化待后续。
- 读写引擎固定 64B 事务（Size 恒 encode(64)）；变长 Size 待完善。
- ChiAxiBridge 忽略 `ar.len`/`aw.len`（按 64B 处理）、忽略 `w.strb`（BE 恒全 1）、`r.resp`/`b.resp` 恒 OK（不透传 RespErr）；多 burst/非对齐/错误透传待完善。
- ChiNetworkTop 同步 link 为直连导线（VC sideband 贯穿）；**每 link ChiTxLink（L-Credit/FSM）与 async link 的 ChiAsyncLink 插入待 GEN 提供 IR cdc/pipe/clock 映射**（模块已验证）。IR link `pipe` 字段当前被忽略。
- ChiNiError（未命中 NDERR）已验证为独立模块；**网络级 RN 注入点集成（addr 驱动 SAM + 就地错误响应）待 GEN/NI 包装**，届时网络测试须改由 addr 驱动。
- ChiPartitionTop/Stub/Wrapper 已验证；**LoomMain partitions 发射 + ports 报告待 GEN IR partitions 接入**。
- ChiAtomicEngine 为 cmd 式单字 RMW（内部存储字、cmd.ready 恒 1），未接完整 REQ/DAT 拉流。
- ChiTop 已退役（emit-chi 单/多 router 统一 ChiNetworkTop）；源码保留作 building-block 单测。
- `LoomMain.usage()` 尚未列出 `emit-chi` 子命令（命令本身可用）。
- QoS/VC 三级仲裁已落地（per-VC 队列 + input RR/half RR/class WRR + cut 翻半区，nHalf 参数化）；dateline 半区 cut 标志由 GEN 拓扑派生（ChiNetRouter.outCut），首版默认空（无 cut）。

### 14.2 预留（编码空间存在，不实现，门禁拒绝）

按 `ChiOpcode.reserved`：SnpShared/SnpUnique/SnpCleanInvalid/SnpMakeInvalid/SnpOnce；ReadOnce/ReadShared/ReadUnique/ReadNotSharedDirty/ReadClean；WriteBack/WriteClean/WriteEvictFull/WriteUniqueFull/WriteUniquePtl；DataSepResp/RespSepData/SnpRespData/CopyBackWrData；MakeReadUnique/CleanUnique/StashOnceShared/StashOnceUnique/CleanShared/CleanInvalid/MakeInvalid/CleanSharedPersist。另：OWO、RN-F/HN-F 逻辑、Resp cache 状态语义（RespI/SC/UC/UD/SD 编码占位）。

### 14.3 不支持（记录，门禁拒绝）

按 `ChiOpcode.unsupported`：Exclusive、CMO、Stash、MPAM、MTE、DWT、DCT、DMT。（CMO/Stash 的具体 opcode 名同时出现在 14.2 预留名集中，feature 层面统一按不支持处理。）

## 附录 A：名词术语表

| 名词 | 含义 |
|---|---|
| RN/SN/HN/MN | 请求/从/Home/杂项节点 |
| RN-I/RN-D/RN-F | IO 主/带 DVM 主/全一致性主（预留） |
| REQ/RSP/SNP/DAT | 请求/响应/窥探/数据通道 |
| flit / transaction | 链路传输单位 / ≤64B 事务 |
| TxnID / DBID | 事务号 / 数据缓冲号 |
| SrcID / TgtID | 源/目的 Node ID |
| ReturnNID / ReturnTxnId | 数据回给谁（DMT 预留） |
| SAM | 系统地址映射（地址→TgtID） |
| DataID / BE | 64B 内 chunk 位置 / 字节使能 |
| L-Credit / P-Credit | 链路 flit 信用 / 事务准入信用 |
| QoS / VC | 优先级 / 内部虚拟通道 |
| Order / ReadReceipt / CompAck | 排序字段 / 受理确认 / 完成确认 |
| OWO | 写按序可观测（预留） |
| RespErr / Resp / MemAttr / Size | 错误/响应状态/内存属性/大小编码 |
| CopyBack | cache 驱逐写回（预留） |
| DCT/DMT/DWT | cache/内存/写直传优化（预留） |
| Stash / CMO | 塞数据进 cache / 缓存维护（预留） |
| MPAM / MTE | 资源分区监控 / 内存标签安全（不支持） |

## 附录 A.2：关键公式

```text
perFlit = datWidth / 8
nFlits  = 64 / perFlit
idStep  = datWidth / 128
DataID  = beat * idStep
flit 数据偏移 = DataID * 128 (bit)
混合带宽顶 = REQ 口数 * 64B/拍
BDP 缓冲深度 ≥ 带宽(flit/拍) * RTT(拍)
vc = QoS类 * 2 + dateline半区
```