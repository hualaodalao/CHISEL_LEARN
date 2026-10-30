# Deep Research: 加速器硬件 Scheduler 的业界做法与 Queen 架构选型

> 生成日期 2026-10-30 ｜ 深度：standard ｜ 来源：38 ｜ 被审对象：`spec.md` v1.1.0 第 5.1、5.6–5.8、8.3、14.2 节，`queenISA.md` 第 7–11、15 节，`queenHarewareInterface.md` §3.3、§9.1
> 本报告只提出选型建议，不改动上述文档。

## TL;DR

Queen Scheduler 的骨架——in-order 命令前端、每端点有限深度队列、完成计数——与 GPU/NPU 的公开做法同构，不需要推翻。但本次调研否掉了三个当前倾向：

1. **候选 A/B 之争不决定死锁。** 前端是顺序派发的标量 CPU，任一 FIFO 满整条派发都会停下，环在 CPU 层已经闭合，换 ready 口径消不掉。A 仍应保留，但理由改为"每 lane 只反映自己的端点、避免三路相与的额外耦合与组合路径"，不再声称 B 是唯一成环的边。
2. **软件 SIGNAL 救不了队列满死锁。** 在 in-order 标量 CPU 上，只要释放指令排在一条无法入队的更年轻指令之后，任何 ready 策略都无法让它进队 [2][36][37]。业界的做法是让**引擎写完成值**或**主机直接写寄存器**，而不是把释放做成队列条目 [3][4][20]。这正是 QN-F029 的真实价值：它不只是"多一个更新源"，而是**移除了绝大多数需要软件 SIGNAL 的场景**。
3. **完成不等于可见。** PDL 把"放行调度"与"数据可见"拆成两个 token [1]，Vulkan 把 signal/wait 定义为 release/acquire [25]，RVWMO 要求 I/O 次序用 fence、非一致 DMA 还要 cache 维护 [30]。`HW_SIGNAL_TARGET` 需要补一个"要求强 idle 才发布"的位。

另有两项落地建议：为可释放同步资源的命令预留专用 slot/credit；为 flush 引入 epoch，旧 epoch 的 completion 一律丢弃并计数。

## Executive Summary

本次调研以 3 个并行检索代理收集 38 个来源——A1 GPU 硬件调度与依赖解析、A4 反压与队列容量；A5 同步原语与完成通知、A2 NPU/DSA 厂商与专利；A3 加速器与体系结构理论、A6 抢占/故障/context 隔离——再由 1 个校验代理对 10 条关键引文逐条比对，结果为 6 SUPPORTED / 3 PARTIAL / 1 UNSUPPORTED。校验意见已全部收敛：删去无法抓取原文的 QDMA 中断聚合环论据，把专利结论收窄到摘要可支持的范围，软化 Gemmini 的"队列深度界定在途命令数"表述，PTX mbarrier 改用锚点链接。

逐领域结论：

**调度与反压。** GPU 前端是 pull/credit 模型而非 push 模型：命令放在系统内存的 pushbuffer 里，硬件 PBDMA 按需拉取，GigaThread 只在 SM 真有资源时发放 CTA [3][4][6]。因此"队列满"表现为生产者拿不到授权，已提交命令不被丢弃。三种流控的分工是清晰共识：valid/ready 最省面积但反压整条流水线；skid buffer 是 2 深弹性缓冲，用来切断组合 ready 路径；credit-based 是发送端主动停发、适合长延迟 [9][10][11]。AXI 靠"通道独立 + 多 outstanding + READY 反压"避免通道互等，但协议本身不提供端到端 credit，outstanding 上限由实现决定，这正是 SoC 级死锁的常见来源 [8]。Dally 的结论是判据而非建议：死锁 ⟺ channel dependency graph 存在环 [2]。

**依赖等待。** 厂商文档承认 GPU 支持"先提交 wait、后提交 signal"，即等待项在 signal 到达前就驻留在硬件等待表中，不阻塞后续命令入队 [5]；Vulkan 把这一性质上升为规范级的 forward-progress 要求 [14]。对 Queen 的直接含义是：**wait 应当是等待表项，而不是占用派发通道的队列槽位**。我们现有 ISA 的 wait 已经只阻塞本队列后续派发、不阻塞其它端点，方向正确；但 SCHED-endpoint 的 wait 会阻塞后续向 GEMM/DMA 的派发，这是比 GPU 更强的耦合，需要单独设防。

**调度许可与可见性。** PDL 把"可以 launch"与"数据可见"拆成两个控制点，并明确前者是 opportunistic、不保证 [1]。Vulkan 规范规定 signal 执行 availability（release）操作、wait 执行 visibility（acquire）操作 [25]。RVWMO 要求 I/O 与主存的次序使用带 PI/PO/SI/SO 的 fence，并提示非一致 DMA 平台可能还需 cache flush/invalidate [30]。这条证据直接决定了 `HW_SIGNAL_TARGET` 不能只有一个"completion 触发"。

**同步资源模型。** timeline semaphore 的状态是一个单调递增的 64-bit 整数，signal 是"设为某值"而不是"加一"，因此支持 wait-before-signal [14]。mbarrier 把 arrival count、phase parity、expect-tx 三件事合在一个对象里，且 expect-tx 只对当前 phase 有效、每轮必须重新 arm，否则 waiter 永久挂死 [15][16][26]。由此得到的判据是：单 bit 事件适合"每个生产者独占一位"的幂等 OR，计数器适合需要重数或区分轮次的完成计数，两者不能互相替代。

**厂商实践与公开度。** 公开材料一致指向"完成信号由引擎或 DMA 产生，软件只负责提交或记录"：Ascend 的 stream 是设备侧逻辑任务队列、Event 管片内跨 stream、Notify 管片间 [20]，片上 task scheduler 按序读 task buffer 再派发 [19]；层级硬件调度专利给出 coarse/fine 两级结构 [23]（摘要级证据）；NVIDIA 侧由 PBDMA 取 GPFIFO、引擎写 semaphore/fence [24]。TPU 的官方架构文档只覆盖拓扑，sequencer 与完成语义未公开 [17][18]；寒武纪公开材料停留在架构营销层 [22]。**队列满时到底 stall 还是 drop，在所检索的厂商文档与专利权利要求中普遍缺失**，没有可直接照抄的先例。

**故障与隔离。** Pascal 起 GPU 支持指令级粒度的 compute preemption 并保存/恢复执行上下文 [31][32]；RAS 要求把错误归因到可定位的 record 与 containment domain [33][34]；抢占与切换的单元是 context 而非单条命令 [38]。对我们的含义是：flush 必须有明确的顺序，旧 context 的异步完成必须按 epoch 丢弃。

## 1. 问题定义

### 1.1 现状

Queen 是单线程标量控制 CPU 的 DSA 派发前端加 Scheduler。当前已实现的只有派发：4 lane 合并、次拍取 `rs1/rs2`、按 `inst[31:30]` 分流到 GEMM/DMA/SCHED 三条整包队列（深度均为 8）、出口直连 `deq`。`QCTRL` 的全部语义、端点执行、同步资源、通知、fault、flush 都还是目标契约。

### 1.2 四个待决问题

| 编号 | 问题 | 出处 |
|---|---|---|
| Q1 | `inst.ready` 用候选 A（每端点计数预约）还是候选 B（三路 `enq.ready` 相与） | spec 5.1、14.2 |
| Q2 | wait 与有限深度队列如何耦合才不成环；软件 SIGNAL 是否需要额外保证 | spec 5.7、`queenISA` §8–§11 |
| Q3 | event 位图 + timeline 计数 + 软/硬双更新源这套模型是否需要改 | spec 5.8、QN-F023/F029 |
| Q4 | 硬件完成信号的发布点、可见性口径，以及 flush 后旧完成如何隔离 | spec 14.2、QN-F025/F027 |

### 1.3 选型判据

按优先级：① 不成环（形式化可判）；② 不丢完成（丢一次就不可恢复）；③ 可归因（fault 能定位到命令与 context）；④ 面积与时序代价可接受。

## 2. 业界证据

### 2.1 GPU 前端：pull 模型，队列满即反压

GPU 的命令不在硬件 FIFO 里，而在系统内存的 pushbuffer 中，由硬件 PBDMA 按需拉取；顶层 GigaThread 统一处理所有线程块，再按 SM 资源分发 [3][4][6]，CTA 级的分发是集中式、资源感知的 [7]。这套结构的后果是：

- "队列满"等价于生产者拿不到授权，**已提交命令不会被丢弃**；丢弃只在前端 fault 或抢占时以 replay 形式出现。
- 通道之间靠 semaphore/syncpoint 表达依赖，而不是靠共享同一个入队许可 [4]。
- runlist 以轮转方式调度，某个 context 的等待不阻止其它 context 前进 [13]。

对我们的映射很清楚：**每条 endpoint 队列应当有独立的接受条件**，用一条队列的满去关掉其它队列的入队许可，等于人为制造了一条跨队列依赖边。

### 2.2 依赖等待：等待表项，不是队列槽位

GPU 支持 wait-before-signal：先提交 wait、后提交 signal 是合法的，等待项在 signal 到达前就驻留在硬件等待表里 [5]；Vulkan 把这一性质写成 queue forward progress 的规范要求 [14]。这不是实现巧合，而是为了让阻塞 wait 不至于把提交路径锁死。

我们现有的 ISA 已经部分具备这一性质：GEMM/DMA 的 wait 只阻塞本队列中 wait 之后的命令，且"不阻止此前已经派发的任务继续运行"。**但 SCHED-endpoint 的 wait 会阻塞 Queen 后续向 GEMM/DMA 的派发**，这是一个 GPU 没有的强耦合——GPU 的通道 wait 只锁自己那条通道。这条边是全系统最需要单独设防的地方。

### 2.3 调度许可与数据可见性必须分开

PDL（CUTLASS 称之为 Grid Dependency Control [12]）的语义是：primary 的所有线程块 launch 完并执行 trigger 之后，driver 就可以放行 secondary 的 launch；但此时 primary 写的**数据可能仍不可见**，需要用 `cudaGridDependencySynchronize` 在读操作数前再 fence 一次；且这套提前放行被官方标注为 opportunistic、不保证带来并发 [1]。Vulkan 侧的定义更形式化：signal 执行 release 侧的 availability 操作，wait 执行 acquire 侧的 visibility 操作 [25]。RVWMO 则明确了边界——要约束 I/O 与主存的次序必须用带 PI/PO/SI/SO 的 fence，非一致 DMA 平台可能还需要额外的 cache flush 或 invalidate [30]。

结论：**"引擎完成"是一个调度事实，"数据可见"是一个内存序事实**。把两者压成一个 `done` 信号，在一致域内可以成立，跨一致域就不成立。

### 2.4 同步资源：计数与位图各有适用面

timeline semaphore 的状态是单调递增的 64-bit 整数，signal 是"设为某个值"而非"加一"，wait 条件是 `value >= N`；正因如此它才支持 wait-before-signal，并消除了 binary semaphore 必须"先提交 signal 再提交 wait"的顺序约束 [14]。

mbarrier 提供的是另一种能力：arrival count 记录"还差几次到达"，phase parity 区分轮次，expect-tx 记录"还差多少字节的异步事务"。expect-tx 只对当前 phase 有效，进入下一 phase 必须重新 arm，否则计数永远不会归零，waiter 会一直等下去 [15][16][26]。

对我们的直接判据：

| 资源 | 适合表达的语义 | 不适合的语义 |
|---|---|---|
| 32-bit event 位图（每组 32 bit） | 每个生产者独占一位的"是否发生过"，幂等 OR，多生产者天然安全 | 需要重数、需要区分轮次、多生产者共用一位 |
| timeline 计数 | 完成次数、顺序推进、跨轮次 | 需要"清除"的布尔状态 |

硬件与软件同时写同一位是这套模型的唯一风险点：位图只保存"是否发生过"，一次误清除就永久丢失一个完成事件。业界对此的处理是把写权限收敛——timeline 的场景里，Vulkan 明确规定 signal 的提交顺序语义，硬件只看值 [14]。

### 2.5 反压机制的代价梯度

| 机制 | 深度/代价 | 反压行为 | 适用 |
|---|---|---|---|
| valid/ready 直连 | 0 | 组合 ready 路径长，一拉低就停整条流水线 | 短距离、同频 |
| skid buffer | 2 深 | 切断组合 ready 路径，吸收下游撤 ready 的 1 拍 | 队列入口 |
| credit-based | 接收端 buffer 深度 | 发送端主动停发，不产生丢包语义 | 长延迟、跨 NoC |

skid buffer 的两条硬判据是双向都有 VALID/READY、下游撤 ready 后不丢数据 [9]；credit 的语义是"满 = 无 credit"，先天没有 drop 的歧义 [10][11]。AXI 用五通道独立 + 多 outstanding 避免通道互等，但 outstanding 上限不在协议里，是集成期死锁的常见来源 [8]。

### 2.6 NPU/DSA 厂商与专利

| 对象 | 公开到什么程度 | 与我们相关的结论 |
|---|---|---|
| Ascend | stream 是设备侧逻辑任务队列，Event 管片内跨 stream、Notify 管片间同步 [20]；片上 task scheduler 按序读 task buffer、选首个 task 派发给 AI CPU/AI Core [19] | 与我们"每端点有序队列 + scheduler 端点"同构；同步原语按作用域分层 |
| NVIDIA GPU | PBDMA 取 GPFIFO，引擎执行后写 semaphore/fence，host 只负责提交 [24] | 完成信号由引擎产生，不由队列里的命令产生 |
| 专利 US20220188155A1 | 摘要级：coarse 调度电路把任务分配到多个加速器、fine 调度电路切成子任务流 [23] | 层级调度是已有专利形态；但权利要求未覆盖队列满策略 |
| TPU | 官方架构文档只讲 slice/cube/ICI 拓扑，sequencer 与完成语义未公开 [17]；论文层面的同步主要由编译器插入 [18] | 没有可引用的硬件通知块设计 |
| Cambricon | 公开材料停留在 MLUarch 版本与产品系列 [22] | 不可作为设计依据 |

Ascend 侧另有第三方材料指出多 stream 任务由驱动下发、硬件按运算单元并行仲裁 [21]（论坛来源，仅作旁证，不用于推出时序结论）。

**共同空白**：队列满时 stall 还是 drop、满队列的超时或溢出策略、跨 queue 依赖的具体仲裁，这些在所检索的厂商文档、论文与专利权利要求中都没有明确描述。这意味着 Q1/Q2 不能靠"照抄业界"，只能靠判据自行推导。

### 2.7 故障、抢占与 context 隔离

- Pascal 起支持指令级粒度的 compute preemption，执行上下文被换出到 GPU DRAM 以便另一个应用换入 [31][32]；抢占与调度的单元是 context 而非单条命令 [38]。
- 错误上报的形态是"带作用域的 record + 恢复动作"：XID 把错误归到 GPU、engine 或 link，并映射到 reset/drain/kill context [34]；OCP 的 RAS 要求把错误可归因、可隔离 [33]。
- 加速器侧的命令队列是 decoupled access-execute 结构：load/store/execute 三条指令队列加 ROB，ROB 表项数决定依赖跟踪能力的上限 [27][28]；RoCC 的响应通道本身也是 valid/ready 握手，加速器必须容忍下游背压 [29]。
- 体系结构理论给出的死锁判据最锋利：Tomasulo 的 issue 是 in-order 的，只在 reservation station 与 ROB 有空位时发射，它能不死锁的关键不是"乱序发射"，而是**释放资源的永远是更老的指令**；一旦释放依赖一条更年轻、还没能进队的指令，就形成循环等待 [36][37]。数据流/任务图调度的做法则是用计数代替边沿：完成只做 counter increment，wait 可重入，因此不会丢 wakeup [35]。

## 3. 三角验证

| # | 结论 | 支撑 | 置信度 | 分歧/空白 |
|---|---|---|---|---|
| K1 | GPU 前端是 pull/credit 模型，队列满反压生产者，不丢已提交命令 | [1][3][4][6][8][10] | High | 队列深度与满队列超时策略未公开 |
| K2 | 依赖等待是等待表项，支持 wait-before-signal，不占用承载 release 的通道 | [1][4][5][14] | High | GPU 的通道 wait 只锁本通道，没有与 Queen SCHED wait 对等的"全局阻塞" |
| K3 | 调度许可与数据可见性是两个控制点，完成不隐含可见 | [1][25][30] | High | 一致域内的具体边界取决于平台 |
| K4 | 死锁等价于依赖图成环；独立 credit / virtual channel 破环 | [2][8][10] | High | 无定量规则告诉你 depth 该多大 |
| K5 | 单调计数优于单 bit 布尔；单 bit 适合单生产者幂等 OR | [14][15][16][26] | High（phase 重新 arm 的细节为 Medium，论坛来源） | 无厂商文档直接讨论"32 组 32-bit 位图"这种规模 |
| K6 | 完成信号由引擎/DMA 产生，软件只提交或记录 | [3][19][20][23][24] | High（专利仅摘要级） | "谁产生"在 TPU/Cambricon 上不可考 |
| K7 | in-order 前端的死锁来源是"release 排在无法入队的年轻指令之后" | [2][27][28][36][37] | Medium | 无公开 NPU 参考设计；结论由 Tomasulo + Dally 推导 |
| K8 | context 是隔离与抢占单元，stale completion 必须按 epoch 丢弃 | [31][32][33][34][38] | Medium | 厂商文档描述机制但未给出可直接照抄的 epoch 协议 |
| K9 | 厂商对 queue-full 策略普遍沉默 | [17][22][23] | Medium（负面证据） | 沉默本身不构成反对，只说明无先例可抄 |
| K10 | 非一致 DMA 需要额外的 cache 维护才能谈"可见" | [30] | High | 是否适用取决于 Hive 的 LLC 一致性设计 |

## 4. 架构选型

### 4.1 D1 — `inst.ready` 口径：保留候选 A，但撤回死锁论证

**评审修正（2026-10-30）**：前端是顺序派发的标量 CPU，任一 FIFO 不能入队时整条派发都会停下。因此 A 与 B 在 CPU 接口上的可观测行为等价，"A 能让其它端点继续"这一优势在我们的系统里不存在。

**选项**：A = 每端点按 `count + enq.valid - deq.fire < depth` 独立生成；B = 三路 `enq.ready` 相与后统一给出。

**裁定：保留 A。** 理由改为三条实现层面的：

1. 一条 lane 的 ready 只应反映它自己的目的地；B 会让一个纯 SCHED 的 bundle 因 GEMM 队列占用而被否决，这是与被派发命令无关的耦合。
2. B 要把三个队列的就绪信号合成一条组合路径，而 depth 8 的计数比较本身已有时序压力。
3. A 是现有 RTL 的形态；若将来引入非阻塞派发，per-lane ready 是可用的起点，而 B 会把耦合写死。

**撤回的论点**：原报告称"B 是系统中唯一会成环的边"。在前端顺序派发的前提下这不成立——环在 CPU 层已经闭合（见 4.2），A 与 B 都消除不了它。B 的问题是冗余耦合与额外时序，不是死锁。

**结论**：ready 口径不是死锁问题的解，4.2 才是。附加约束保留两条：队列入口用有效/就绪握手且**永不 drop**（[9][10] 的语义）；用 watchdog 监测"某队列头部阻塞超过 N 周期"并上报 fault，作为诊断兜底 [34]。

### 4.2 D2 — 释放路径 wait-free：统一 ring + SCHED 直通

**评审提出的结构（2026-10-30）**：GEMM/DMA 命令进同一个 ring buffer，Scheduler 命令直通、不进 ring；Scheduler 自身不再有 wait；完成信号主要来自硬件，软件 SIGNAL 只保留通路。

**先给一个判据性结论**：软件 SIGNAL 只能释放程序序在它**之后**的 wait。在单线程顺序派发下，signal 指令一定比它所释放的 wait 更早被发射，因此它无法表达"等后面这批工作完成"。**所有"等完成"语义都必须由硬件产生。** 这从语义上确定了 QN-F029 不是可选增强，而是 wait 机制得以成立的前提。

**结构评估**：

- **SCHED 直通 + wait-free 是这套方案的正确内核。** Scheduler 命令永远能排空，释放路径不经过任何可能阻塞的队列；这正是 GPU 把 release 放在引擎侧、而不是放进命令队列的原因 [3][4][24]，也是 Tomasulo 里"释放者永远更老"的对应物 [36][37]。
- **单一 ring 有一个必须处理的风险：头部阻塞死锁。** 若 ring 只有一条读指针，队头是一条等待事件的 DMA 命令，而该事件由排在它后面的 GEMM 命令产生，则 GEMM 永远得不到派发。现状的三条 FIFO 没有这个问题，因为 DMA 队列阻塞不阻止 GEMM 队列出队。
- **解法是 per-consumer 游标。** GEMM 与 DMA 各持一个读游标，各自跳过不属于自己的条目；某槽位在两个游标都越过它之后才释放，即 `commit = min(cursors)`、`free = writePtr - commit`。这样跨引擎不互相阻塞，同时 CPU 侧只需一个写口和一条"ring 是否有空槽"的判断，比三路 ready 解码更简单。若不愿引入游标逻辑，则退回三条 FIFO，其余结论不变。

**ISA 后果（需同步修改）**：

1. `WAIT_EVENT`/`WAIT_TIMELINE`/`FENCE` 的 `endpoint=SCHED` 变为非法。"等 GEMM 齐了再发 DMA"改为把 wait 放在 DMA 队列头部——与 GPU 的 channel-local wait 一致 [5]。
2. SCHED 与 GEMM/DMA 队列之间不再保证顺序，只能通过 wait 建立单向可见性。
3. 软件 SIGNAL 的语义从"发布完成"降级为"通路自检 / 非完成类标记"；声明完成必须用硬件信号。

**容量与残留风险**：ring 满时 CPU 仍会停，"能否到达 SIGNAL"仍取决于容量。三条不变量共同保证不成环：(i) 所有完成类 wait 的释放由已派发的工作产生（硬件信号）；(ii) SCHED 路径 wait-free；(iii) 软件 SIGNAL 只用于程序序在 wait 之前的语义。满足三条时，任何 wait 的释放都不依赖尚未派发的命令。

**不做 wait 表项化。** 把 wait 从队列里拿出来做成独立等待表需要额外的依赖跟踪与排序逻辑；在单线程、无抢占、wait 已局限于本队列的前提下收益不足，列为 P2 备选。

### 4.3 D3 — 同步资源模型：保留 event + timeline 双资源，硬件为写主

**证据**：单 bit 事件在多生产者并发 signal 时退化为 OR，无法区分次数与轮次 [14][15][16]；计数型 barrier 才能跟踪异步事务完成 [15][26]；数据流调度用计数避免丢 wakeup [35]。

**裁定**：

- 保留 32 组 32-bit event 与 8 个 timeline；两者不可互相替代，分工按 2.4 的表。
- **event 位的写权限以硬件为主，冲突时硬件置位胜出。** 丢失一次完成不可恢复，丢失一次清除可由软件重发，因此同拍的"硬件置位 vs 软件清除"判为置位。14.2 的对应待确认项由此直接裁定，不必再引入单写者约束。
- **软件 SIGNAL 只作通路自检与非完成类标记。** 若某 bit 已有硬件 target，软件不得依赖对该 bit 的清除表达状态；声明"某批工作完成"必须走硬件信号。
- **timeline：软件显式赋值与硬件自增不得指向同一 timeline**（现有 ISA 已有此约束），软件自增可与硬件自增并存、同拍按次数累加。32-bit 宽度足够，前提是在途距离 `< 2^31` 的约束继续成立（已有）。

### 4.4 D4 — 硬件信号的发布点：拆成"完成"与"可见"两级

**证据**：PDL 的两个 token [1]、Vulkan 的 release/acquire [25]、RVWMO 的 fence 与 cache 维护要求 [30]。

**裁定**：`HW_SIGNAL_TARGET` 增加一个 `requireVisible` 位，语义为"该信号表示数据已可被下游读"。

| 位值 | 发布点 | 适用 |
|---|---|---|
| 0 | adapter 接收 native completion | 同一致域内、只做执行顺序依赖 |
| 1 | 端点强 idle（含写回完成） | 跨一致域、下游要把结果当数据读 |

这直接回答 14.2 的待确认项：**不是二选一，而是让软件选**。默认取 0（低延迟），需要数据可见时置 1。若 Hive 的 DSA 边界完全一致且写回路径可保证可见，则位 1 退化为与位 0 等价，实现上可以不增加状态。

### 4.5 D5 — fault 与 flush：精确归因 + epoch

**证据**：GPU 把错误归到 engine/context 并给出 reset/drain/kill 的恢复动作 [34]，RAS 要求可归因与可隔离 [33]；抢占单元是 context [31][32][38]。

**裁定**：

1. **fault 归因到最老的未退休命令**（精确异常），比它年轻的在途命令标记 poisoned 并丢弃结果；这与 QN-F025 现有的"保存首个 fault"一致，但需要补"poisoned"状态，否则失败命令之后的结果会污染下游。
2. **引入 context epoch**（或 generation 计数）。每条命令随包携带 epoch，completion/fault 回带同一 epoch；不匹配者**丢弃并计入 stale 计数**，绝不更新 event/timeline/通知计数。
3. **flush 顺序固定**：停止接受新命令 → 端点 quiesce/drain（不支持安全 abort 的 DSA 等自然结束）→ epoch++ → 清旧 epoch 的计数与队列 → 重新使能。顺序不可颠倒，否则旧 completion 会与清零竞争。
4. 状态输出只报当前 epoch 的计数，避免软件在恢复后读到混合快照。

### 4.6 D6 — 通知计数：保留 COUNT/ACK/PENDING/ENABLE，补溢出语义

**证据**：计数型通知的优点是"按周期累加"，不存在单 bit 的丢失窗口；事件位图的丢失风险已在 4.3 讨论。

**裁定**：保留现有四寄存器模型。补两条边界：COUNT 按位宽饱和（或回绕）时必须置一个 overflow 位，否则 `PENDING = COUNT != ACK` 在回绕后可能静默相等；中断必须保持到 ACK 覆盖，不能因新 pulse 到达而自动清除。这两条不改变软件接口，只补语义。

## 5. 对 Queen 的落地清单

| # | 动作 | 关联 | 优先级 | 验收方式 |
|---|---|---|---|---|
| L1 | 保留候选 A，撤回"B 是唯一成环边"；B 记为功能等价但冗余耦合 | QN-F005、spec 5.1 | P0 | 文档复审；ready 路径不新增组合级 |
| L2 | GEMM/DMA 合并为统一 ring（per-consumer 游标），SCHED 直通且 wait-free | QN-F002..F005、QN-F015..F019 | P0 | "DMA 队头等 GEMM 事件"用例中 GEMM 不得被阻塞 |
| L2b | `WAIT_*`/`FENCE` 的 `endpoint=SCHED` 改为非法；跨设备顺序改放消费者队列 | QN-F015..F017、`queenISA` §7–§9 | P0 | 非法编码用例；改写后的顺序用例 |
| L3 | `HW_SIGNAL_TARGET` 增加 `requireVisible`，定义 completion / strong-idle 两个发布点 | QN-F029、接口 §9.1 | P0 | 两种发布点的时序用例各一 |
| L4 | 硬件置位优先；软件 SIGNAL 仅通路，声明完成必须用硬件信号 | QN-F023、QN-F029、spec 5.8 | P0 | 同拍置位/清除与软硬竞争用例 |
| L5 | context epoch + stale completion 丢弃计数 | QN-F025、QN-F027、新增条目 | P0 | flush 后用旧 completion 打新 context 的用例必须无副作用 |
| L6 | fault 增加 poisoned 状态，失败命令之后的在途结果丢弃 | QN-F025 | P1 | 注错用例：fault 后不得出现成功 notify |
| L7 | COUNT 溢出位与中断保持语义 | QN-F024 | P1 | 连续 pulse 超位宽的回绕用例 |
| L8 | 队列头部阻塞 watchdog 上报 fault | 新增条目（可选） | P2 | 人为制造阻塞，检查超时上报 |
| L9 | wait 表项化（脱离队列） | 若将来引入非阻塞发射 | P2（暂不做） | — |

## 6. 批判性评估

**我们和 GPU 不一样，不能套用结论。** GPU 有 driver、有多 context、有抢占、有轮转调度，某个 context 卡住不必然影响整机 [13][38]；Queen 是单线程标量 CPU 的协处理器前端，**一次 in-order 停顿就是整机停顿**。因此 GPU 那条"别的通道还能跑"的保险丝我们没有。这使 D2 的容量不变量比在 GPU 上更关键，也使 watchdog 从"诊断增强"变成"可恢复性的必要条件"。

**与公开实践的三处偏离，需要自觉承担：**

1. **wait 放在端点队列里。** GPU 的等待项在硬件等待表，不占命令队列槽位 [5]；我们把它做成队列条目，换来的是顺序语义简单、无需额外依赖跟踪。代价是队列被 wait 占一格，且 wait 必须能被"更老的、已派发的"工作解除。在单线程、无抢占的前提下这是可接受的简化，但它正是 D2 残留死锁的根源。
2. **原设计让 SCHED wait 阻塞向 GEMM/DMA 的派发。** GPU 的通道 wait 只锁本通道，没有对等物；好处是能表达"等所有 GEMM 到某点再继续派发"，坏处是把一条强边加进依赖图。按 4.2 的裁定取消这一语义，代价是失去"一条指令同时等两个引擎"的表达，需要拆成两条分别放在消费者队列里的 wait。
3. **32-bit timeline。** 业界用 64-bit [14]；我们靠"在途距离 < 2^31"的约束把 32-bit 用满。约束是显式的、可检查的，可以接受，但必须在资源分配协议里强制执行，不能只写在文档里。

**对"加硬件完成源就够了"的反驳。** 不够。硬件源只能表达"引擎完成"这一类语义，表达不了"阶段标记""软件注入的依赖点""非引擎来源的完成"（比如 VPU）。因此双源是必要的；但双源意味着同一资源有两个写者，所以写优先序必须显式规定：硬件置位优先，软件清除可重发。

**对"统一 ring 会引入头部阻塞"的反驳。** 会，而且不加游标就是真死锁（4.2 的例子）。所以 ring 必须配 per-consumer 游标；若实现上不愿承担游标的比较与释放记账，就退回三条 FIFO——两者的同步结论相同，只是 CPU 接口从"一条空槽判断"变回"三路 ready"。选 ring 的理由是接口简化，不是消除死锁。

## 7. 未决问题

1. **Hive 的 DSA 边界是否一致**：若 GEMM/DMA 的写回对下游（LLC/VPU）天然可见，`requireVisible=1` 可以退化为无操作，D4 的实现成本为零；否则必须在强 idle 后才发布。
2. **VPU 是否也接入硬件完成源**：本项目前只覆盖 GEMM/DMA（QN-F029），VPU 的完成由谁发布、是否需要进 Scheduler 的 event/timeline，尚无结论。
3. **watchdog 的超时阈值**：没有业界公开值可抄（K9），只能按最坏任务长度自定，属于内部约定。
4. **event 组数量**：32 组 × 32 bit 的规模没有业界对标，是否需要分组回收协议取决于软件资源管理器的设计。
5. **wait 表项化的门槛**：只有将来引入非阻塞发射或多 context 时才值得做；当前不做。

## Methodology

- 深度：standard；3 个检索代理并行（A1/A4、A5/A2、A3/A6），每个 8 次 WebSearch + 5 次 WebFetch 上限；1 个校验代理，10 次 WebFetch 上限。
- 来源 38 个，按可信度分级：Tier 1（论文、官方规范/文档、专利、研究机构）24 个，Tier 2（公司工程博客、会议教程、知名从业者）8 个，Tier 3（个人博客、论坛、营销材料）6 个。
- 三角验证规则：`[High]` 需 3 个以上独立来源且至少一个 Tier 1/2；`[Medium]` 为 2 个来源或单一权威来源；`[Low]` 为单一来源。
- 引用抽查：10 对 claim–source，6 SUPPORTED、3 PARTIAL、1 UNSUPPORTED。处理：UNSUPPORTED 的 QDMA 中断聚合环论据整条删除；PARTIAL 的专利结论收窄到摘要可支持的范围、Gemmini 表述软化为"ROB 表项数决定依赖跟踪能力"、PTX 改用锚点链接并标注相位细节为论坛来源。
- 未检索到公开材料的领域（TPU sequencer、Cambricon 调度器、厂商 queue-full 策略）在正文中以"空白"显式标注，不以推测填充。

## Bibliography

**[1]** NVIDIA — CUDA Programming Guide, 4.5 Programmatic Dependent Launch and Synchronization — https://docs.nvidia.com/cuda/cuda-programming-guide/04-special-topics/programmatic-dependent-launch.html — accessed 2026-10 — Tier 1
**[2]** W. J. Dally, C. L. Seitz — Deadlock-Free Message Routing in Multiprocessor Interconnection Networks — IEEE Trans. Computers, 1987 — https://authors.library.caltech.edu/records/fd0yr-br438 — Tier 1
**[3]** J. Bakita et al. — Demystifying NVIDIA GPU Internals to Enable Reliable GPU Management — RTAS'24 — https://www.cs.unc.edu/~anderson/papers/rtas24.pdf — 2024 — Tier 1
**[4]** N. Capodieci et al. — Deadline-based Scheduling for GPU with Preemption Support — RTSS'18 — https://hipert.unimore.it/people/marko/papers/RTSS18.pdf — 2018 — Tier 1
**[5]** NVIDIA — DRIVE OS: Semaphore Wait-Before-Signal — https://developer.nvidia.com/docs/drive/drive-os/6.0.10/public/drive-os-linux-sdk/common/topics/graphics_content/Semaphore-Wait-Before-Signal-10.html — 2024 — Tier 1
**[6]** J. Passerat-Palmbach et al. — GPU/CPU scheduling survey — arXiv:1501.01405 — https://arxiv.org/pdf/1501.01405 — 2015 — Tier 1
**[7]** University of Rochester — How the Fermi Thread Block Scheduler Works — https://www.cs.rochester.edu/~sree/fermi-tbs/fermi-tbs.html — 2014 — Tier 3
**[8]** Arm — AMBA AXI Protocol Specification (IHI 0022) — https://developer.arm.com/documentation/ihi0022/e/ — accessed 2026-10 — Tier 1
**[9]** D. Gisselquist (ZipCPU) — Building a Skid Buffer for AXI — https://zipcpu.com/blog/2019/05/22/skidbuffer.html — 2019 — Tier 2
**[10]** University of Crete CS-534 — Credit-Based Flow Control — https://www.csd.uoc.gr/~hy534/16a/s61_creditFC_sl.pdf — 2016 — Tier 2
**[11]** verilog-meetup — Credit-Based Flow Control — https://verilog-meetup.com/2025/02/11/credit-based-flow-control/ — 2025 — Tier 3
**[12]** 知乎 — 使用 Programmatic Dependent Launch (PDL) 降低端到端延迟 — https://zhuanlan.zhihu.com/p/2052349834762380330 — 2026 — Tier 3
**[13]** Anonymous — Unleashing the Power of Preemptive Priority-based Scheduling for GPU — arXiv:2401.16529 — 2024 — Tier 1
**[14]** Khronos (J. Ekstrand) — Vulkan Timeline Semaphores — https://www.khronos.org/blog/vulkan-timeline-semaphores — 2020 — Tier 1
**[15]** Colfax Research — Mastering the NVIDIA Tensor Memory Accelerator (TMA) — https://research.colfax-intl.com/tutorial-hopper-tma/ — 2024 — Tier 2
**[16]** NVIDIA Developer Forums — Mbarrier's expect-tx: forever or current phase? — https://forums.developer.nvidia.com/t/mbarriers-expect-tx-is-set-forever-or-just-current-phase/304745 — 2024 — Tier 3
**[17]** Google Cloud — TPU architecture — https://docs.cloud.google.com/tpu/docs/system-architecture-tpu-vm — accessed 2026-10 — Tier 1
**[18]** N. Jouppi et al. — TPU v4 — ISCA'23, arXiv:2304.01433 — 2023 — Tier 1
**[19]** Ascend-CC authors — Ascend-CC: Confidential Computing on Heterogeneous Ascend NPU — arXiv, 2024 — Tier 2
**[20]** Huawei — CANN Runtime — https://www.hiascend.com/eng/cann/runtime — accessed 2026-10 — Tier 1
**[21]** Huawei Cloud Forum (MindIE) — 昇腾平台模型并发请求调度原理 — 2026 — Tier 3
**[22]** Cambricon — 官网（MLU370 / MLUarch03） — https://www.cambricon.com/ — accessed 2026-10 — Tier 3
**[23]** UT-Battelle LLC — Hierarchical task scheduling for accelerators — US20220188155A1 — https://patents.google.com/patent/US20220188155A1/en — filed 2021, granted 2025 — Tier 1（摘要级）
**[24]** arXiv — Revealing NVIDIA Closed-Source Driver Command Submission — https://arxiv.org/html/2604.26889v1 — 2026 — Tier 2
**[25]** Khronos — Vulkan Specification, Synchronization chapter — https://docs.vulkan.org/spec/latest/chapters/synchronization.html — accessed 2026-10 — Tier 1
**[26]** NVIDIA — PTX ISA, Parallel Synchronization: mbarrier — https://docs.nvidia.com/cuda/parallel-thread-execution/#parallel-synchronization-and-communication-instructions-mbarrier — accessed 2026-10 — Tier 1
**[27]** UC Berkeley Chipyard — Gemmini Generator documentation — https://chipyard.readthedocs.io/en/1.2.0/Generators/Gemmini.html — accessed 2026-10 — Tier 1
**[28]** H. Genc et al. — Gemmini: Enabling Systematic DNN Architecture Evaluation — DAC'21, arXiv:1911.09460 — Tier 1
**[29]** ecrionix.org — RoCC Interface Deep Dive — https://ecrionix.org/riscv-accelerator/day-03/ — 2026 — Tier 3
**[30]** RISC-V International — RVWMO Explanatory Material — https://docs.riscv.org/reference/isa/v20260120/unpriv/mm-eplan.html — accessed 2026-10 — Tier 1
**[31]** NVIDIA — Pascal Architecture Whitepaper — https://images.nvidia.com/content/pdf/tesla/whitepaper/pascal-architecture-whitepaper.pdf — 2016 — Tier 2
**[32]** NVIDIA — Pascal Tuning Guide, Compute Preemption — https://docs.nvidia.com/cuda/pascal-tuning-guide/index.html — accessed 2026-10 — Tier 2
**[33]** Open Compute Project — GPU & Accelerator RAS Requirements v1.7 — https://www.opencompute.org/documents/ocp-gpu-and-accelerators-ras-requirements-v1-7-10-23-2025-pdf — 2025 — Tier 1（摘要级）
**[34]** NVIDIA — XID Errors — https://docs.nvidia.com/deploy/xid-errors/archive/index.html — 2024 — Tier 2
**[35]** T. De Matteis et al. — Streaming Task Graph Scheduling for Dataflow Architectures — HPDC'23, arXiv:2306.02730 — Tier 1
**[36]** Nanjing University (S. Wang) — Speculation I — https://cs.nju.edu.cn/swang/CA_16S/Lec13_Speculation_I.pdf — Tier 2
**[37]** University of Cantabria OCW — Instruction Level Parallelism III: Dynamic Scheduling — https://ocw.unican.es/pluginfile.php/1827/course/section/1428/07_scheduling_part1.pdf — Tier 2
**[38]** Y. Wang et al. — GCAPS: GPU Context-Aware Preemptive Priority-based Scheduling — 2024 — Tier 1

## Source Extracts

**[1]** "The CUDA driver can launch the secondary kernel when all primary thread blocks have launched and executed `cudaTriggerProgrammaticLaunchCompletion`."；"the secondary thread blocks might launch before data written by the primary kernel is visible."；"this behavior is opportunistic and not guaranteed to lead to concurrent kernel execution."

**[2]** "A necessary and sufficient condition for deadlock-free routing is the absence of cycles in the channel dependency graph."；"cycles … can be removed by splitting physical channels into groups of virtual channels."

**[3]** "Once the GPU HW scheduler identifies a channel with pending commands, it leverages a hardware Pushbuffer DMA (PBDMA) unit to pull commands from CPU to GPU."（检索摘要级引用）

**[4]** "It is simply a list of channels, each one pointing to a Command Push Buffer that contains the list of pending commands for that channel."；"Synchronization within a group of commands in the same channel or between different channels is implemented by means of semaphores and syncpoints…"

**[5]** "Using wait-before-signal can reduce synchronization latency by removing CPU host operations from the critical path…"

**[14]** "a synchronization primitive whose state consists of a monotonically increasing 64-bit integer value."；"wait-before-signal submission order."

**[15]** "since a single thread will initiate the TMA load, we should set the arrival count to 1."

**[16]** 讨论确认 expect-tx 的期望值只作用于当前 phase，进入下一 phase 需重新设置；未重新 arm 时 barrier 无法归零。

**[20]** "logical task execution queues on devices"；"intra-device synchronization between streams"；"cross-device task synchronization."

**[23]** 摘要：coarse scheduling circuit 把 task-set 中的任务分配到多个加速器电路，fine scheduling circuit 把工作切成子任务流。

**[25]** 规范规定 semaphore signal 执行 availability（release）操作、wait 执行 visibility（acquire）操作；"操作完成"不等于"数据可见"。

**[26]** `mbarrier.arrive` 具 release 语义、`mbarrier.test_wait/try_wait` 具 acquire 语义；phase parity 区分轮次，tx-count 跟踪异步事务完成。

**[30]** "To enforce ordering between I/O operations and main memory operations, code must use a FENCE with PI, PO, SI, and/or SO."；"Platforms or devices with non-coherent DMA may need additional synchronization (such as cache flush or invalidate mechanisms)…"

**[32]** "Compute Preemption allows compute tasks running on the GPU to be interrupted at instruction-level granularity."，上下文被换出到 GPU DRAM 以便另一个应用换入。

**[36]** "Issue – get instruction from FP Op Queue. – If reservation station and reorder buffer slot free, issue."

**[37]** "Scoreboard: no register renaming, limited out-of-order."；"Tomasulo: copy-based register renaming, full out-of-order."
