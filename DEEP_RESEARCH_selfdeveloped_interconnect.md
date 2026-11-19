# Deep Research: 自研片内总线（SoC Interconnect）该怎么做

> Generated 2026-11-18 | Depth: deep | Sources: 77（去重后实际引用 60+）| 读者：硬件架构团队
> 范围：Hive NPU SoC 的片内互连（GEMM 引擎 / DMA / LLC / CPU / 配置通路），协议允许全新定义，不以 AXI 兼容为目标。

## TL;DR

自研总线的成败不在 RTL，而在三件事：**先把事务语义定死（尤其"响应永不依赖新请求"这条死锁铁律）、先用性能模型算出 outstanding 与端口数、把验证（形式化死锁证明 + 协议断言包）当成一等公民**。对本项目而言，现成的 AXI/CHI 都不是好起点：AXI 把"返回路由"和"排序域"绑在同一个 ID 上，CHI 的复杂度对非一致加速器是纯负债；而仓库里已有一套可继承的 4 通道 Stream 语义（ForagerBee）。更关键的是，**当前 Hive 的瓶颈大概率不在 fabric 峰值带宽，而在 LLC 的 4 项 MSHR（算术下界约 1.7–2.6 GB/s）和 DMA 的单 burst 在途**——先修这两处，再谈总线。

---

## Executive Summary

本报告调研了 4 个域：协议谱系（AXI/ACE/CHI/TileLink）、加速器流量模型与拓扑、事务层机制（排序/死锁/QoS/原子/错误）、自研与买 IP 的工程代价，并对本仓库现有接口做了逐行核对。

**核心结论有五条。**

第一，**协议分层比协议选型重要**。端侧 NPU 需要的是三种语义而不是一种"万能总线"：低带宽但强序的控制面（配置/门铃）、高带宽可乱序的数据面（权重/激活/部分和）、以及仅在 CPU↔LLC 边界存在的一致性面。业界主流做法正是把加速器当作非一致主设备（ACE-Lite 的定位、CHI 的 RN-I 类节点），只在 CPU 簇边界付一致性成本 [7][8][9][72]。

第二，**AXI 的 ID 模型应当被拆开**。Arm 自己在 AXI5 中引入 Unique ID 指示符，允许主设备声明"不需要交织/排序跟踪"，以减少互连对 ID 的跟踪开销 [32]；AXI 的排序规则本身也只在同 ID 内成立，且对同地址仍有例外 [31]。全新协议应当把 SrcID（只用于响应路由）与排序属性（显式位）解耦。

第三，**死锁是协议级而非路由级的**。AXI 读写通道解耦会在端点形成环形等待，即使路由算法无死锁 [33]；真实的硅片 errata 也记录过写数据先于写地址到达导致的死锁 [34]。因此新协议必须硬编码：请求在发出时就要预留响应缓冲槽位，响应永远不需要新请求才能推进 [33]。

第四，**拓扑在端侧的正确解是分层 crossbar，而不是通用 mesh**。FlooNoC 在 12nm、8×4 mesh 下做到 645 Gbps/link、0.15 pJ/B/hop、每 tile 3.5% 面积开销 [16]，说明 NoC 本身的物理代价并不高；真正贵的是热点/多播、混合流量 QoS、CDC 与验证。MicroSwitch 早就指出通用 mesh 对空间型 NN 加速器是过度设计 [17]。单级 crossbar 在约 16×16 端口、512b 量级开始失去优势 [17][20]。

第五，**对本项目最可执行的结论是"先修端点，再修 fabric"**。按仓库参数做算术：LLC 的 4 项 MSHR × 64B = 256B 在途，若 miss 延迟 100–150 ns，则 LLC 未命中吞吐只有 **1.7–2.6 GB/s**，远低于 WorkerBee 的 A+B 端口 64 B/cycle（1 GHz 下 64 GB/s）；ForagerBee 每引擎仅 1 个读 burst 在途，大 burst（64 拍 × 32B = 2 KB）下约 13.6 GB/s，小传输则急剧退化。**总线做到 512 GB/s 聚合也救不了这两个端点。**

---

## 1. 现状：协议谱系与端侧互联的真实格局 [Confidence: High]

### 1.1 四条路线的本质差别不是"先进程度"，而是"把复杂度放在哪"

AXI 是一套**点对点、五通道、按 ID 划分排序域**的负载/存储协议：AW/W/B/AR/R 相互独立，AXI5 增加了原子交易和更丰富的 QoS/user 边带，但至今没有 snoop 或 shareability 语义，任何系统级顺序都要由 fabric 自己发明 [31][32]。它的优点是每个节点都能独立实现、工具链成熟、协议检查器几乎是免费附赠的 [36]；缺点是 ID 同时承担"响应回程路由"和"排序域"两个职责，互连必须为每个 ID 维护跟踪状态，Arm 在 AXI5 中专门加了 Unique ID 指示符来让主设备"声明我不需要交织"，从而让互连不再跟踪 [32]。

ACE/ACE-Lite 是在 AXI 上外挂一致性：增加 AC/CR/CD 通道与 snoop 流量。ACE-Lite 是"单向一致"的精简版——主设备可以一致地读，但不持有可被 snoop 的副本。按 Arm 的说明，ACE-Lite 的可缓存交易限定在 Non-shared、Non-cached、Cache Maintenance 三组，明确面向"DMA 引擎、网络接口、加速器"这类 IO 一致主设备 [7][8]。需要更正一个常见口径：这并不意味着 ACE-Lite 不能承载屏障或 DVM 消息——否则 TLB/地址翻译一致性就无从谈起；准确说法是**可缓存的数据访问被限制在这三组**。

CHI 是分层、packet/flit 化的四通道协议：REQ、RSP、DAT、SNP，分事务、信用流控，Home Node（HN-F）配合 snoop filter/目录做一致性，原子操作在近 home 节点执行，QoS 有显式钩子 [1][72]。它的演进史本身就是复杂度证据：Issue E 被形容为"迄今最大的一次更新，带来大量新交易"，2024 年的 Issue G 又加入 MEC、Limited Data Elision、RME-DA、DataSource Extensions、DataTarget、PrefetchTgtHint [3][4]。对一个不做一致性的 NPU 来说，这些交易类型是"至少不能违反"的负担。

TileLink 是轻量路线的代表：基础 A/D 通道做非一致读写，TL-C 只增加 B/C/E 三个通道做**权限管理**（Acquire/Probe/Release），是 probe 式而非强制目录式，块大小按参数配置而不是固定 64B [5][6]。它还有一个被低估的设计选择：读写共用 A 通道，从结构上消掉了 AXI 的 AW/W/AR/R 五通道分裂，也顺带消掉了一类端点死锁 [5][33]。

OCP 仍是第三种互操作选项，商用 fabric 通常同时桥接 AXI/ACE/CHI/OCP 多套协议 [75]——这意味着"自研一套协议"不等于"世界里只剩这一套协议"，边界桥接的工作始终有人要做。

一个必须诚实说明的空白：**CHI 与 AXI 的面积/延迟/线数定量对比没有公开可信数据**。检索到的唯一机制性论据是 CHI 用"四个统一通道"替代 ACE 的五个 AXI 派生通道，以"减少线数" [2]；所有具体数字都在厂商演讲与工具白皮书里，未公开。本报告因此不对二者做 PPA 排序，只做机制比较。

### 1.2 商业与自研的版图：混合是常态，不是例外

商用一致互连的事实标准是 Arm CMN 系列：CMN-700 是 crosspoint（XP）矩形网格，CHI agent（RN-F/RN-I/SN）挂在网格上，可扩展、非阻塞，支持 CCIX/CXL 多芯片，新一代是 CMN S3(AE) [10][11][15]。非一致侧则是 Arteris FlexNoC / Ncore 的主场 [46][47]，其 AI Package 明确提供数据多播/广播 [51][52]；大型系统厂商同时购买一致与非一致两套授权是常态，例如中兴 2017 年一次性授权 Ncore + FlexNoC [50]。

把互连当一阶变量并非自研阵营的偏见：TPU 把 ICI 与 HBM、MXU 吞吐并列为性能驱动因素 [23][24][25]；端侧的分析也反复指出瓶颈在数据搬运而非 MAC 数量 [29]。

自研阵营的共同点是"把互连当产品差异化"：Apple 的 UltraFusion 用硅中介层做 die 间 fabric，发布会强调的是"连接密度是现有技术的两倍" [65]；华为把超节点互联（UB/灵衢）作为 AI 基础设施的核心叙事 [63]（社区拆解称昇腾 950 采用多 die 合封 + UB 互联，属 Tier 3 旁证 [64]）；平头哥为真武 M890 配了自研 ICN 互联总线与 ICN Switch 1.0，吞吐 25.6 Tbps [62]；Tenstorrent 的 Tensix NoC 是软件可见的自研 fabric，每个 tile 1.5 MB SRAM [61]。但即便是 Tenstorrent，其一致侧仍采用了 Arteris Ncore——这是"自己造差异化的数据面、买标准合规的一致面"这一混合模式最清晰的公开样本 [49]，不过该来源是厂商案例研究，独立性有限。

端侧 NPU 的公开数据很薄。能查到的硬事实只有几条：AMD Versal 的可编程 NoC 是"基于 AXI4 的互连网络" [68]，AI Engine 阵列可到 400 tile、1 GHz+ [69]；Intel Meteor Lake 的 NPU 把 MAC 阵列拆到两个 Neural Compute tile，中间走片上 fabric [28]；TriGen 给出一个端侧配置——每 NPU 32×32 MPA 配 32 GB/s DRAM，折合约 64 ops/byte，必须靠深 tiling 或 LLC 补 [26]。地平线、黑芝麻、Hexagon、三星 NPU 的总线宽度与拓扑均无公开数据。

### 1.3 本仓库现状：接口事实与冲突点

逐行核对的结果如下（这些事实决定了自研总线的形状）：

WorkerBee 的配置面是 4 路 `Stream(WorkerBeeCmd)`，命令字含 `reg0Addr/reg1Addr` 各 10 bit、`data0/data1` 各 32 bit、`pc` 32 bit、`tag` 8 bit，响应为 `Stream{data 32, err, laneId 2}`（`src/workerbee/scala/main/WbInterface.scala:7-31`）。数据面是 A/B/C 三条只读/只写 DMA 口，**请求只有 addr，没有 len、没有 ID**（`WbInterface.scala:43-66`），与 256b 的 A/B/C 缓冲行宽等宽直连。

ForagerBee 的 `FbStreamBus` 已经是一套"用 4 通道替代 AXI5 通道"的自研协议：写通道把 **addr+len+data+strb+sof/eof 合并在首拍**，读通道只有 `addr+len`，读数据按 `len+1` 拍返回并逐拍带 `err`（`src/foragerbee/scala/main/FbStreamBus.scala:24-76`）。写响应 `FbWriteResp` **没有 ID 字段**；在共享仲裁模式下，响应必须按 burst 发出顺序返回，否则需要未来给响应加 ID（同文件:33-43）。默认参数为 `dataWidth=128`、`addressWidth=40`、`maxBurstLen=64`、`queueDepth=8`、`writeOutstandingDepth=1`（`src/foragerbee/scala/main/ForagerBeeConfig.scala:23-39`），导出配置改成 256b / 4 outstanding。

LLC 是唯一真正用 AXI4 的模块：上行 slave、下行 master 各一套完整 AXI4（id 4 bit、len/burst/size/last/resp/strb 全开），数据 256b、地址 **32 bit**、line 64B、每 line 2 拍、**MSHR 4 项**（`src/llc/scala/main/LlcConfig.scala:5-25`）。控制口是 AxiLite4（addr 8 / data 32）。

三处硬冲突必须显式处理：

1. **地址位宽不一致**：DMA 与 Hive 架构是 40 bit，WorkerBee 与 LLC 是 32 bit（`LlcConfig.scala:8`、`WbInterface.scala:49`、`ForagerBeeConfig.scala:29`、`src/hive/doc/arch.md:129`）。总线若选 32b 会截断 DMA，选 40b 则 LLC 侧需要窗口检查或桥接。
2. **写响应无 ID**：FbStreamBus 的共享写端口靠"归属 FIFO + 保序"路由响应（`FbStreamBus.scala:38-42`），一旦 fabric 乱序返回，现有 DMA 就会错配。新协议要么保序，要么在响应里补 tag。
3. **全仓无 CDC**：没有任何异步 FIFO/同步器，而架构文档要求 NPU 区域独立时钟域与调频（`src/hive/doc/arch.md:156, 287`）。总线必须自己决定 CDC 边界放在哪里。

---

## 2. 趋势：2024–2026 年加速器互联的四个走向 [Confidence: Medium]

**走向一：非一致是默认，一致是边界。** 加速器调查文献把"绕过 cache 层次、直接 DMA 访问 DRAM"当作基线模型 [9]；Arm 把 ACE-Lite 明确定位给 DMA/网络接口/加速器 [7]；在 CHI 体系里对应 RN-I（I/O 一致请求节点）与 ReadNoSnp/WriteNoSnp 交易类。这意味着 CHI 可以只被当作**传输层**使用——packet 化、QoS、信用流控、原子能力照用，但不参与一致性。对 NPU 而言这是"吃 CHI 的传输红利、不吃一致性的验证债"的路径。

**走向二：数据多播从"可选优化"变成 AI fabric 的标配功能。** Arteris FlexNoC 4 的 AI Package 明确把写数据"尽可能靠近端点地广播"，以减少片外/DRAM 流量，并点名 Enflame 的 AI 芯片使用了该能力 [51][52]。学术界有树状多播路由用于 DNN NoC，目标是最小化包数 [66]；2024 年还有把多播与异步 NoC 结合的工作，报告在 SNN 流量下 42%–71% 的能耗节省 [67]；HPC 侧也有"交换机级多播优于点对点复制"的形式化分析 [71]。但要注意：这些收益主要是**能耗与 DRAM/LLC 带宽**，不是 fabric 线带宽；而且 Arm CMN 的公开文档里**未见 payload 级多播描述**（公开材料只描述 snoop/共享域与 DVM 广播）[10][12]——这是"据公开文档未见"，不是"硬件不支持"。

**走向三：CDC 被放进 fabric，而不是包在每个端口上。** CMN-700 支持"单一同步域或多个异步时钟域"，并可把网格划分为四个异步时钟域；CMN-650 的文档进一步规定"AMCS 必须桥接异步时钟域"，且桥接用的 AMCS 必须紧邻一到两个 MCS [13][14]。这给出一个可直接借鉴的结论：**让 crosspoint 成为 CDC 边界，一个时钟域就是网格的若干列**；而不是给每个 master/slave 端口套一个异步 FIFO。端点侧方案（握手/异步 FIFO/脉冲同步器）的代价在 Cummings 的经典文献里有完整清单 [70]。

**走向四：验证重心从"仿真覆盖率"转向"形式化 + 性能模型"。** DVCon 的层次化形式化方法配合 progress checking 才关得住 mesh 规模的死锁证明 [53]；GeNoC/ACL2 路线在路由层证明死锁避免 [54]；工程实践把"always eventually 能响应请求"当作死锁自由的标准形式 [55]。性能侧，gem5 明确"不是周期精确模型" [58]，SystemC/TLM 相对 RTL 可达约 100 倍加速 [59]，业内用 TLM 模型回答"带宽够不够、延迟堆在哪、竞争在哪" [60]。反面证据同样有力：2007 年一份基于硬件仿真器的互连性能验证案例，发现的三个"互连杀手 bug"每一个都能让整系统死锁 [57]。

另外一个不可忽视的趋势是**工具链税**：自研 fabric 会催生外部工具适配成本，例如学界要为 Tenstorrent 的 Tensix/NoC 单独写模拟器 [77]。

---

## 3. 关键设计决策：如果从零定义一套总线 [Confidence: Medium-High]

以下每条都给出"建议 + 理由 + 反方观点"，并尽量落到 Hive 的具体参数上。

### 3.1 先分层：三种语义，不要一条万能总线

**建议**：把互连切成三个平面，共享物理 fabric 但语义独立。

- **控制面（CTL）**：配置写、门铃、状态读。特征是小载荷（32–96 bit）、强序（同源到同目的必须保序）、延迟敏感。对应仓库里 Queen 的 4 lane × 96 bit、单 ready、`valid&&!ready` 时整 beat payload 保持的语义（`src/queen/doc/queenHarewareInterface.md:413-437`）。
- **数据面（BULK）**：权重、激活、部分和、DMA 搬运。256b beat、burst、split-transaction、可乱序、信用流控。
- **一致性面（COH）**：只存在于 CPU 簇 ↔ LLC 边界。NPU 不作为一致节点参与。

**理由**：AXI 的失败经验正是"用一套通道同时服务控制与批量"——一次大 burst 就能把 32B 的 descriptor 读头阻塞（head-of-line blocking），而 NPU 的调度器恰恰靠 descriptor 的延迟尾部决定利用率。把控制面单独成类，是成本最低的解法。业界也是这么做的：AMD NoC 的 VC 仲裁文档直接区分高/低优先级请求 [39]；BENoC 的立论就是"多跳 NoC 对延迟敏感信号太慢"，因此总线与 NoC 混合 [19]。

**反方**：平面越多，crossbar 端口越多、面积越大。对端口数 ≤8 的小系统，单平面 + 优先级可能更划算。这个边界要用 3.6 的端口账来判。

### 3.2 事务层：四个通道、SrcID 与排序属性解耦

**建议**：采用四通道——`REQ`（地址+长度+控制属性）、`WDATA`（写数据拍）、`RSP`（每 burst 一次确认/错误）、`RDATA`（读数据拍）。这与 ForagerBee 的 `FbStreamBus` 已经高度同构，可以最低成本继承并向上扩展。

关键字段决策：

- **SrcID 8 bit，只做响应路由，不承担排序语义。** 理由：AXI 把两件事绑在 ID 上，互连必须为每个 ID 维持跟踪状态，Arm 自己在 AXI5 里用 Unique ID 指示符来解耦 [32]。
- **排序属性显式化**：定义 `ordered` 位（同源到同目的的全序），默认乱序；同地址的 RAW/WAW 由硬件隐含保证，不占属性位。AXI 的规则是"同 ID 保序、异 ID 无约束"，但对同地址仍有例外 [31]；CHI 则改为按地址排序、不同地址可乱序 [76]。新协议应明确站在 CHI 一侧：**按地址排序 + 显式 ordered 位**，而不是按 ID 排序。
- **读写共用 REQ 通道**（TileLink 式）而非 AXI 的 AW/AR 分裂。理由：AXI 的读写通道独立会在端点形成环形等待，即使路由无死锁 [33]；Altera 的 Stratix 10 errata 就是写数据先于写地址到达把组件卡死 [34]。共用 REQ 通道从结构上消掉这一类问题，代价是写数据必须"头随数据"或紧跟头部，不能无限期后置。
- **写数据与写头的配对**：`WDATA` 每拍携带 `wtag`（或依赖"同源同序"），且**写头在目的端必须先成功预留一个写槽**才允许数据流动。这条规则等价于"响应不依赖新请求"的镜像。

**反方**：共用 REQ 通道会让读请求被写请求头阻塞，损失一点并发。缓解手段是给 REQ 内部再分两个虚拟队列（读/写），但共享同一物理链路与仲裁器。

### 3.3 流控与死锁：四条可以写进规范的铁律

死锁不是路由问题，是协议问题。必须硬编码以下规则：

1. **响应永远不需要新请求才能推进。** 请求在发出时必须预留响应缓冲槽（端到端信用），而不是等响应回来再申请。这是文献里对 AXI 端点死锁的标准解法 [33]。
2. **请求/响应使用不相交的虚拟通道集合，或物理分离。** Leone 等人的对比显示，预抢占 VC 相对 multiplane 可省最多 76% 的链路资源、仅增加约 3% 的路由器面积——注意限定词是"最多"、"链路资源"、"相对 multiplane" [33]。
3. **端到端信用用于返回路径，逐跳 ready/valid 只用于链路本身。** 用 ready/valid 做端到端背压会在多跳上形成依赖链，是经典死锁源。
4. **控制/小消息走 store-and-forward，批量数据走 wormhole/virtual-cut-through。** 前者便宜、无 HOL、易界定上界；后者需要 per-VC 缓冲。永远不要让一个请求占着"其排空依赖某个响应、而该响应又需要这个缓冲"的槽位。

这四条对应到 Hive：当前 `FbStreamBus` 的写响应无 ID、要求保序（`FbStreamBus.scala:38-43`），本身是"用一个隐式顺序换掉 ID 空间"的简化；如果新 fabric 要支持乱序返回，**必须在响应里加 tag，而不是让 fabric 去保序**——保序会把整个 fabric 变成一个巨大的重排缓冲。

### 3.4 QoS：三类流量、WRR + 老化、令牌桶限速

**建议**：只设三类——CTL（配置/门铃/descriptor）、LAT（小激活 gather、scalar 读）、BULK（权重/部分和/DMA 大块）。每类一个 VC。仲裁用加权轮询（WRR）+ 老化提升，而不是严格优先级。

**理由**：严格优先级在高负载下会让 BULK 饿死 CTL，而 NPU 的实时性由**延迟尾部（p99）**而不是平均值决定；AMD 的 NoC 文档确认商用实现用的是"高优先级请求总是优先，但需满足 eligibility 条件" [39]，而 WRR 在 QoS/公平性上优于严格优先级 [37]，代价是要按流配置权重。等优先级 VC 之间可用信用计数仲裁 [38]。带宽调节用 per-master 令牌桶，这同时是端侧热/功耗节流最自然的旋钮 [45]。

**反方**：三类已经接近端侧可承受的上限；每多一个 VC，路由器缓冲面积线性增长。若面积吃紧，退化为两类（CTL+LAT 合并），但绝不能把 CTL 并进 BULK。

### 3.5 一致性边界、原子与错误

**一致性**：NPU 作为非一致主设备（RN-I 等价物）。软件可见性靠"写数据 → fence → 门铃"的显式序列，门铃写标记为 `ordered`。若 CPU 侧需要硬件一致，则在 CPU↔LLC 之间保留 CHI 或 ACE-Lite 桥，而不是把一致性扩散到 NPU 端口 [7][8][9]。仓库当前 LLC 只有 AXI4 口、无 snoop（`LlcConfig.scala:31-46`），因此近期现实选择是**非一致 + 显式 cache maintenance 命令**。

**原子**：不要为 NPU 数据面提供 LL/SC。把 AMO（fetch-add、swap、CAS、min/max、bit-set/clear）放在 LLC 切片/近 home 节点执行，原子性几乎免费；如需 split-K 归约，加 AMO-accumulate 让 epilogue 不必往返。AXI exclusive 的语义是"slave 侧监视、可伪失败、软件重试"，在 NoC 上每次重试都是一个往返，不适合 [40]；若 RISC-V 核需要 LR/SC，用 PULP 的 `axi_riscv_atomics` 思路在 fabric 边缘终结，下游无需支持 exclusive [41]。

**错误**：两种机制都要，AXI 只有一种。Poison/DataCheck 标记"数据坏了但地址没错"，让错误传播到消费者；响应码（AXI SLVERR/DECERR、CHI DERR/NDERR）标记"这笔交易失败" [42]。设计规则：**每个请求必须被回答**（未回答的译码错误就是挂死）；每拍带 poison 位；fabric 为每个错误记录 master ID + 地址；错误必须可容纳、不阻塞无关流量。反面教材是 CVA6 的一个现网缺陷：cache refill 收到 RRESP=SLVERR/DECERR 后响应码在流水线里被丢弃，形成静默数据损坏 [43]。

### 3.6 拓扑与位宽：先算端口账，再选 crossbar

**端口账（按仓库现状）**：master 侧大致是 CPU/LLC 上行 1、ForagerBee 每实例 2 口（可 1–4）、WorkerBee 数据口 6–10（A/B/C + scaleA/B/C + bias/requant 取数）、Queen 配置 1、debug 1；slave 侧是 LLC 1、local SRAM 8 bank、DRAM 控制器 1、外设若干。总计约 14–20 主 × 10–12 从。

**结论**：这个规模正好落在单级 crossbar 的边界上。文献给出的经验交叉点是约 16×16 端口、512b 量级（256 个交叉点）之后不再划算，64×64 需要 4096 个交叉点 [17][20]；对存内/近存计算阵列的拓扑对比也显示，P2P、树、mesh 之间的选择对吞吐的影响与阵列本身同量级 [18]。因此建议**两级**：

- **计算簇 crossbar**：WorkerBee 的 A/B/C/scale 口 + local SRAM 8 bank + ForagerBee 本地口。这一级流量最大、模式最固定（行流式 + 32B 低位交织），适合宽而浅的单级 crossbar。
- **系统 crossbar**：LLC、DRAM、CPU 桥、DMA 大块搬运、配置通路。这一级端口少、协议异构，适合带 VC/QoS 的窄口。

**位宽**：256b（32B）是 Hive 的天然单位——WorkerBee A/B/C、LLC、导出 DMA 全是 256b，64B line 正好 2 拍。这不是巧合：NVDLA 的规范直接把读端口宽度定义为"每拍需要喂给阵列的元素数 × 元素字节数"，即端口宽度是阵列几何的函数，而不是 DRAM 宽度的函数 [21]；后处理单元同样按每拍元素数反推带宽（例如 16 × 16 bit = 32 B/cycle）[22]。不建议全网上 512b：线面积与每跳能耗翻倍，而收益只是把 line fill 从 2 拍降到 1 拍。可在 LLC↔DRAM 干线上单独用 512b，形成"端点 256b、主干 512b"的宽窄转换。

**带宽算术**（1 GHz 假设，仅用于量级判断）：

| 端点 | 每拍字节 | 1 GHz 下单向带宽 |
|---|---|---|
| WorkerBee A + B | 32 + 32 B | 64 GB/s |
| WorkerBee C | 32 B | 32 GB/s |
| DMA 单端口（读写各） | 16 B | 16 GB/s |
| 16 端口 crossbar 聚合 | 16 × 32 B | 512 GB/s（理论上限） |
| Hive DRAM（256b @500 MHz） | 32 B @0.5 GHz | 16 GB/s |

这张表说明两件事：第一，**片上链路带宽是 DRAM 的 2–32 倍，fabric 不是稀缺资源**；第二，**聚合带宽数字毫无意义**，因为真正的限制在端点。

**端点限制的定量证据（由仓库参数推算，非文献数据）**：

- LLC：4 项 MSHR × 64B = 256B 在途。若 miss 往返 100 ns，吞吐上界 2.56 GB/s；150 ns 则 1.71 GB/s。这比 WorkerBee 的单端口还低一个数量级。**把 MSHR 从 4 提到 16 或按 bank 复制，比把 fabric 加宽 4 倍更有效。**
- DMA：每引擎 1 个读 burst 在途（`src/foragerbee/scala/main/FbByteTransfer.scala:14,43-49`），但一个 burst 可含 64 拍 × 32B = 2 KB，150 ns 下约 13.6 GB/s；提到 4 个 burst 约 54.6 GB/s。因此"1 个在途"对大块传输尚可，对小块/离散访问则是灾难——这正是 Hive 架构要求读侧 4 个 burst 在途的原因（`src/hive/doc/arch.md:284-285`）。

要得到这类数字不该靠手算：SCALE-Sim 这类周期级模型能从阵列形状与 dataflow 直接给出每层的权重/激活/部分和字节数与带宽需求 [27]。另外不要忽略每跳能耗——互连能耗本质是"每比特每毫米"，跳数与线长和位宽同等重要 [30]。

### 3.7 CDC：把异步边界放在 crosspoint

**建议**：初期定义 2 个时钟域（SoC/CPU 域、NPU 核域），异步 FIFO 放在 crosspoint 的端口上，一个域对应网格的一片区域；不要在每个 master/slave 端口各套一个 CDC。

**理由**：这是 CMN 的做法——CMN-700 支持单同步域或多异步域，可划分四个异步时钟域；CMN-650 规定 AMCS 负责桥接且必须紧邻 MCS [13][14]。端点方案的代价清单（握手延迟、吞吐损失、格雷码指针、复位同步）在 Cummings 的经典文献里 [70]。仓库当前完全没有 CDC（`src/hive/doc/arch.md:156` 只写了需求），所以这一层是纯新增，必须在协议规范里定义**信用返回如何跨域**——信用计数器跨域是最容易出错的地方。

**反方**：crosspoint 内建 CDC 会让每个 XP 都变复杂，面积高于"只在两个域交界处放一个桥"。折中：**按域边界切分物理 fabric**，跨域的链路才放异步 FIFO，而不是每个 XP 都放。

### 3.8 多播：只在"多消费者共享同一权重块"时立项

**建议**：在写请求里加 `mcast_group`（3–4 bit），目的端集合由寄存器预配置；fabric 在分叉点复制数据，响应按"组内最慢者"聚合或由组内每个成员各回一次（前者省带宽、后者省状态）。默认关闭。

**理由**：多播在 AI fabric 里已被产品化——FlexNoC 4 AI Package 把数据"尽可能靠近端点地广播"，Enflame 的 AI 芯片在用 [51][52]；学术界用树状多播最小化 DNN NoC 的包数 [66]，并在异步 NoC 上报告 42%–71% 的能耗节省 [67]；HPC 侧也有"交换机级多播优于点对点复制"的分析框架 [71]。

**但要划清收益口径**：多播省的是 **DRAM/LLC 带宽与能耗**，不是 fabric 线带宽。在 Hive 里，只有当多个 WorkerBee 实例或多个 bank 消费同一权重 tile 时才划算；单实例场景下多播只是增加状态机与验证负担。另外，CMN 公开文档未见 payload 多播 [10][12]，说明这不是"买了 IP 就自动有"的能力。

---

## 4. 批判性评估：自研真正会死在哪里 [Confidence: Medium]

### 4.1 四类已记录在案的失败模式

**（1）解耦死锁。** AXI 读写通道独立 → 端点的读写流量互相等待 [33]；写数据先于写地址到达 → 组件卡死（Altera Stratix 10 errata）[34]。这不是理论风险，是量产出货的芯片上发生过的。

**（2）ID 空间耗尽与活锁。** ID 是每主设备的有限资源；一个主设备占满 ID 后在请求路径上停住，如果排空又需要它推进，就形成活锁。这也是"无死锁 ID 分配"至今仍是活跃研究方向的原因。

**（3）QoS 饿死与延迟尾部。** 严格优先级 + 饱和的 BULK 主设备 = 控制流量无界延迟；平均值好看、p99 崩掉，正好击中 NPU 调度器最敏感的地方 [39][37]。

**（4）错误被静默丢弃。** 响应码在流水线里丢掉（CVA6 #3731）[43]，或译码错误无人回答导致挂死。这类 bug 往往在 bring-up 阶段才暴露，且极难定位。

### 4.2 自研 vs 买 IP：公开可得的数据只有一半

**买 IP 的论点**：Arteris 的市场话术是"相比自研每项目省下数百人年" [48]——这是厂商营销数字，无审计口径，不能当作规划依据。更可信的是结构性事实：CHI 的验证生态已经复杂到支撑一个多厂商 VIP 市场，卖点直接写"支持 Issue A–G" [73]；自研意味着没有现成 VIP，协议检查器要自己写（AXI 侧 Arm 直接提供断言描述，等于免费 [36]，而新协议没有这个红利）。一致性协议这一侧的教训更重：TileLink 的 Acquire/Finish/Release 接口是靠机器辅助的模块化形式化才被真正验证的 [35]，说明"看起来简单"的协议同样需要形式化投入。

**自研的论点**：Apple、华为、平头哥、Tenstorrent 都在自研，且都把互连当作差异化 [61][62][63][65]。买 CMN/Ncore 会把 floorplan、功耗意图、调试接口绑到厂商生成器与路线图上，量产后还有 royalty——这正是大厂自研的动机。

**最诚实的结论是混合**：一致面买（协议标准、检查清单长、合规风险高），数据面自研（流量模式就是产品本身）。Tenstorrent 是最清晰的样本：Tensix NoC 自研，一致侧买 Ncore [49][61]——但需再次说明，买 Ncore 这条事实来自厂商案例研究，独立性有限。

**没有任何公开的 CMN/Ncore 价格**。授权是 NRE + royalty，流传的具体数字都属传闻级，本报告不采用。

### 4.3 工程量：一个可辩护的推算

公开数据不足以给出精确人月，但可以给出结构。业界常用口径是验证占 SoC 开发的一半以上（一手调查逐年给出 50%–70% 的区间，具体取决于统计人力工时还是项目周期；本报告检索到的单一硕士论文转引 [56] 只作为旁证，不作为精确依据）[44][56]。据此对 Hive 规模（14–20 主 × 10–12 从、256b、带 VC/QoS、2 时钟域、无一致性）做推算：

- RTL（crossbar + 仲裁 + VC + 信用 + 错误 + CDC 包装）：**8–14 人月**
- 验证（定向 + 随机 + 错误注入 + 断言包）：**16–28 人月**（按 2 倍 RTL 计）
- 形式化（死锁自由 + 信用守恒 + 地址译码全覆盖）：**4–8 人月**
- 性能模型（TLM/周期级，用于定端口数与 outstanding）：**3–5 人月**
- 合计：**31–55 人月**，不含一致性、不含多播、不含后端

这个数字的含义不是"贵"，而是**它和买 IP 的比较必须把验证与性能模型算进去**——只比 RTL 工作量会得出完全错误的结论。

### 4.4 反方最强的一击

"你们会重造一个更差的 CHI。" 这个批评在一致性面上完全成立：CHI 的 snoop filter、MESI/MOESI 状态机、Issue A→G 的交易演化，都是几十家公司多年踩坑的结果 [1][3][4]。但如果协议范围明确限定为**非一致数据搬运 fabric**，这个批评就打偏了——因为被砍掉的恰好是最贵的那部分。真正无法回避的批评是另一条：**"你们会低估验证。"** 所有证据都指向这一点 [53][55][57][73]。

---

## 5. Action Plan（针对 Hive）

- [ ] **先冻结语义，再写 RTL。** 产出 `src/hive/doc/interconnect.md`：四通道定义、SrcID 8b、40b 统一地址、256b 数据、`ordered` 位、三类 QoS、信用规则、错误码表、CDC 边界。没有这份文档就不开工。
- [ ] **把 LLC MSHR 从 4 提到 ≥16**（`src/llc/scala/main/LlcConfig.scala:11`），或按 bank 复制 MSHR。按当前参数，LLC 未命中吞吐的算术上界只有 1.7–2.6 GB/s，加宽 fabric 无意义。
- [ ] **统一地址位宽到 40 bit**，LLC 侧增加窗口检查或桥接（当前 32b，`LlcConfig.scala:8`；DMA/Hive 为 40b）。这是一次性迁移，越晚做越贵。
- [ ] **提升 DMA 的 burst 级 outstanding 到 4**，并给写响应加 tag（或在 fabric 侧保序）。当前共享写端口的响应依赖归属 FIFO 保序（`FbStreamBus.scala:38-43`），与乱序 fabric 不兼容。
- [ ] **先做 TLM/周期级性能模型**，用 WorkerBee/ForagerBee 的真实流量标定，回答"端口数、VC 数、outstanding"三个问题；不要用 gem5 当周期精确模型 [58]，SystemC/TLM 足够且快约 100 倍 [59][60]。
- [ ] **P1：8×8、256b、单时钟域 crossbar 原型**，直接对接 `FbStreamBus` 与 WorkerBee DMA 口，跑随机流量；同时交付断言包（信用守恒、请求必有响应、同址保序、译码全覆盖）[36]。
- [ ] **P1 出口条件**：形式化证明"always eventually 请求被响应" [53][55]，并用层次化路径分解控制状态爆炸 [53][74]。
- [ ] **P2：加 VC/QoS、错误注入、poison 传播**；错误注入用例必须覆盖"响应码被丢弃"这一 CVA6 式缺陷 [43]。
- [ ] **P3：加 crosspoint 级 CDC**，定义信用跨域规则；参考 CMN 的域划分方式 [13][14]，用 Cummings 的同步器清单自查 [70]。
- [ ] **P4：多播（条件立项）**。先量化"多个消费者共享同一权重 tile"的命中率；若收益只体现在 DRAM/LLC 带宽而非线带宽，则用软件复制或 DMA 重复更划算 [51][52][66]。
- [ ] **一致性边界**：NPU 保持非一致（RN-I 等价），CPU 侧保留 CHI/ACE-Lite 桥；驱动门铃用 `ordered` 写 + fence [7][8][9]。
- [ ] **验收指标**：每类流量下的实测 B/cycle、p99 延迟、死锁证明覆盖率、错误注入通过率、CDC 域切换下的功能回归。只报聚合带宽不算验收。

---

## 6. 开放问题与注意事项

1. **CHI vs AXI 的定量 PPA 无公开数据。** 本报告只能做机制比较，不能做面积/延迟排序。若要做立项级对比，需要拿到 Arm 的 CHI 迁移演讲或 Cadence SoC Designer 白皮书，属于付费/闭门材料。
2. **多播的带宽收益无公开量化。** 现有证据只覆盖能耗（42%–71%）与"减少片外访问"的定性描述 [51][67]，没有"权重驻留型加速器上多播省多少 DRAM 带宽"的数据。
3. **端侧 NPU 互联数据严重缺失。** 只有 Versal 有"AXI4-based NoC"这一条硬事实 [68]，地平线/黑芝麻/Hexagon/三星均无公开总线参数。
4. **IP 价格不可得。** 所有流传数字均为传闻，本报告未采用。
5. **Hive 的并发需求未定。** 若最终是单 WorkerBee 实例，多播与 16 端口 crossbar 都不必要；若多实例共享权重，结论反转。这是影响最大、也最容易拍脑袋的输入。
6. **LLC 的一致性语义未定。** 当前 LLC 是纯 AXI、无 snoop，NPU 与 CPU 的可见性完全依赖软件维护。这是架构级决策，不是 fabric 能补的。
7. **"CMN 无 payload 多播"是缺席证据。** 措辞必须限定为"据公开文档未见"，不能推断硬件不支持。

---

## Methodology

- **深度**：deep（4 路检索 + 1 路 gap-fill + 1 路引用核验 + 1 路仓库接口勘查）。
- **波次**：Wave 1 四路并行检索（协议谱系、流量模型与拓扑、事务层机制、build-vs-buy 与验证）；Wave 2 单路 gap-fill（CHI/AXI 量化、多播、端侧 NPU、CDC），其中 Gap 1/2/4 为 PARTIAL，Gap 3 除 Versal 外仍开放；Phase 3.1 单路引用核验抽查 10 条论断。
- **来源**：去重后 68 个，最终引用 60+；Tier 1 约 40 个，Tier 2 约 18 个，Tier 3 约 15 个（含厂商材料，均已标注）。
- **引用核验修正**（已写入正文口径）：
  1. AXI 排序不能写成"异 ID 完全无序"——同地址仍有例外；RAW 跨通道无保证是推论而非原文 [31]。
  2. ACE-Lite 的"三组交易"是**可缓存交易**的限制，不排除屏障/DVM 消息 [8]。
  3. CMN 多播改为"据公开文档未见 payload 多播"，不作否定式断言 [10][12]。
  4. "验证占 50–80%"降格为"多份业界调查给出 50%–70% 区间，口径差异大"，单一硕士论文仅作旁证 [56]。
  5. Tenstorrent 买 Ncore 改为显式归属厂商案例研究，不推断"评估并否决了自研" [49]。
  6. WRR 的吞吐对照对象是 RR 而非严格优先级，正文已区分 [37]。
- **综合方式**：deep 模式下通常由综合子代理预写分域摘要；本次因最终来源编号达 77 条、跨域引用密集，为避免编号漂移，综合由主代理直接完成。
- **未能完成**：CHI-vs-AXI 定量 PPA、IP 授权价格、多播带宽收益、中国端侧 NPU 总线参数。均已在开放问题中标注。

---

## Bibliography

[1] Arm — AMBA 5（架构总览） — https://www.arm.com/architecture/system-architectures/amba/amba-5 — Accessed 2026-11-18 — Tier: 1
[2] Semiconductor Engineering — Moving From AMBA ACE to CHI For Coherency — https://semiengineering.com/moving-from-amba-ace-to-chi-for-coherency/ — Accessed 2026-11-18 — Tier: 2
[3] Cadence — How AMBA CHI Specification Has Evolved – CHI-E (r)evolutionary? — https://community.cadence.com/cadence_blogs_8/b/fv/posts/how-amba-chi-specification-has-evolved---chi-e-r-evolutionary — 2022-05-02 — Tier: 2
[4] Synopsys — Industry's First Verification IP for Arm AMBA CHI-G — https://www.synopsys.com/blogs/chip-design/amba-chi-g-verification-ip.html — 2024-03-06 — Tier: 2
[5] SiFive — TileLink Specification 1.7.1 — https://sifive-china.oss-cn-zhangjiakou.aliyuncs.com/TileLink/TileLink-TileLink%20Spec%201.7.1.pdf — Accessed 2026-11-18 — Tier: 1
[6] jia.je — TileLink 总线协议分析 — https://jia.je/hardware/2022/05/09/tilelink/ — 2022-05-09 — Tier: 3
[7] Arm Developer Community — Exploring How Cache Coherency Accelerates Heterogeneous Compute — https://developer.arm.com/community/arm-community-blogs/b/architectures-and-processors-blog/posts/exploring-how-cache-coherency-accelerates-heterogeneous-compute — 2016-05-29 — Tier: 2
[8] Design & Reuse — AMBA 4 ACE for Heterogeneous Multiprocessing SoCs — https://www.design-reuse.com/article/60056-amba-4-ace-for-heterogeneous-multiprocessing-socs/ — 2012-05-21 — Tier: 2
[9] B. Peccerillo et al. — A survey on hardware accelerators (Journal of Systems Architecture) — https://www.sciencedirect.com/science/article/pii/S1383762122001138 — 2022 — Tier: 1
[10] Arm — Neoverse CMN-700 — https://www.arm.com/products/silicon-ip-system/corelink-interconnect/cmn-700 — Accessed 2026-11-18 — Tier: 1
[11] Arm — About CMN-600 — https://developer.arm.com/documentation/100180/0200/Introduction/About-CMN-600 — Accessed 2026-11-18 — Tier: 1
[12] Arm — Optimization of DVMOps and DVMSyncs (CMN-700 TRM) — https://support.arm.com/documentation/102308/0307/Functional-description/Transaction-handling/DVM-messages/Optimization-of-DVMOps-and-DVMSyncs — Accessed 2026-11-18 — Tier: 1
[13] Arm — CMN-700 TRM: Clock domain configurations — https://developer.arm.com/documentation/102308/0307/Functional-description/Clocks-and-resets/Clock-domain-configurations — Accessed 2026-11-18 — Tier: 1
[14] Arm — CMN-650 TRM: Clock domain configurations — https://support.arm.com/documentation/101481/0200/Functional-description/Clocks-and-resets/Clock-domain-configurations — Accessed 2026-11-18 — Tier: 1
[15] Arm — About CMN S3(AE) — https://support.arm.com/documentation/107858/0205/About-CMN-S3-AE- — Accessed 2026-11-18 — Tier: 1
[16] T. Fischer et al. — FlooNoC: A 645 Gbps/link 0.15 pJ/B/hop Open-Source NoC — https://arxiv.org/html/2409.17606v1 — 2024-09-26 — Tier: 1
[17] H. Kwon et al. — Rethinking NoCs for Spatial Neural Network Accelerators (MicroSwitch) — https://anands09.github.io/papers/MicroSwitch_NOCS2017.pdf — NOCS 2017 — Tier: 1
[18] G. Krishnan et al. — Impact of On-Chip Interconnect on In-Memory Acceleration of DNNs — https://arxiv.org/pdf/2107.02358 — Accessed 2026-11-18 — Tier: 1
[19] BENoC — Best of both worlds: A bus enhanced NoC — https://www.researchgate.net/publication/220864667_Best_of_both_worlds_A_bus_enhanced_NoC_BENoC — Accessed 2026-11-18 — Tier: 1
[20] A. Tavakkol et al. — Mesh Connected Crossbars — https://tavakkol.ch/downloads/papers/A.Tavakkol-ISPA2008.pdf — ISPA 2008 — Tier: 1
[21] NVIDIA — NVDLA Hardware Architectural Specification — https://nvdla.org/hw/v1/hwarch.html — Accessed 2026-11-18 — Tier: 1
[22] NVIDIA — NVDLA Unit Description — https://nvdla.org/hw/v1/ias/unit_description.html — Accessed 2026-11-18 — Tier: 1
[23] N. Jouppi et al. — TPU v4 — https://arxiv.org/pdf/2304.01433 — 2023 — Tier: 1
[24] Google Cloud — TPU architecture — https://docs.cloud.google.com/tpu/docs/system-architecture-tpu-vm — Accessed 2026-11-18 — Tier: 1
[25] SemiAnalysis InferenceX — What Is Inter-Chip Interconnect (ICI)? — https://inferencex.semianalysis.com/glossary/ici — Accessed 2026-11-18 — Tier: 2
[26] TriGen — NPU Architecture for End-to-End Acceleration of LLMs — https://arxiv.org/html/2602.12962v1 — 2026-02-13 — Tier: 1
[27] A. Samajdar et al. — SCALE-Sim v3 — https://arxiv.org/html/2504.15377 — 2025-05-09 — Tier: 1
[28] Chips and Cheese — Intel Meteor Lake's NPU — https://chipsandcheese.com/p/intel-meteor-lakes-npu — 2024-04-22 — Tier: 2
[29] Quadric — Beyond MAC Counts: Why NPU Offload Strategies Fail for Edge AI — https://quadric.ai/blog/beyond-mac-counts-npu-offload-edge-ai — 2025-01-14 — Tier: 3
[30] V. Adhinarayanan et al. — Measuring and Modeling On-Chip Interconnect Power — https://www.computermachines.org/joe/publications/pdfs/iiswc2016_interconnect.pdf — IISWC 2016 — Tier: 1
[31] Arm — Transfer behavior and transaction ordering (Learn the architecture: AXI) — https://support.arm.com/documentation/102202/0300/Transfer-behavior-and-transaction-ordering — Accessed 2026-11-18 — Tier: 1
[32] F. Socal (Arm) — AMBA moves forward with major revisions to AXI and CHI — https://developer.arm.com/community/arm-community-blogs/b/architectures-and-processors-blog/posts/updates-to-amba-axi-and-chi-specifications — 2019-10-15 — Tier: 1
[33] Leone et al. — Physically-Aware Preemptive Virtual Channels for Deadlock-Free AXI NoCs — https://arxiv.org/html/2607.01430v1 — 2026 — Tier: 1
[34] Intel/Altera — Stratix 10 errata 2.3.2: Write Data Can Appear before the Write Address — https://docs.altera.com/r/docs/683399/current/stratix-10-sx-device-errata/write-data-can-appear-at-an-axi-interface-before-the-write-address-which-can-cause-a-deadlock-condition — Accessed 2026-11-18 — Tier: 1
[35] K. McMillan et al. — Modular specification and verification of a cache-coherent interface — https://dl.acm.org/doi/pdf/10.5555/3077629.3077651 — Accessed 2026-11-18 — Tier: 1
[36] Arm — Assertion Descriptions (AMBA AXI protocol checker) — https://support.arm.com/documentation/dui0305/c/Assertion-Descriptions — Accessed 2026-11-18 — Tier: 1
[37] S. K. Mandal et al. — Theoretical Analysis and Evaluation of NoCs with Weighted Round-Robin Arbitration — https://arxiv.org/pdf/2108.09534 — 2021 — Tier: 1
[38] N. Matveeva et al. — QoS Support in Embedded Networks and NoC (FRUCT) — https://www.fruct.org/files/publications/volume-16/fruct16/Mat.pdf — Accessed 2026-11-18 — Tier: 2
[39] AMD/Xilinx — Virtual Channel Arbitration (PG313 NoC) — https://docs.amd.com/r/en-US/pg313-network-on-chip/Virtual-Channel-Arbitration — Accessed 2026-11-18 — Tier: 1
[40] Synopsys — Understanding AMBA AXI Exclusive Access — https://www.synopsys.com/blogs/chip-design/understanding-amba-axi-exclusive-access.html — 2016-08-23 — Tier: 2
[41] PULP Platform — axi_riscv_atomics — https://github.com/pulp-platform/axi_riscv_atomics/blob/master/README.md — Accessed 2026-11-18 — Tier: 2
[42] 谷公子（aijishu）— CHI 里的 Error handling 机制 — https://community.aijishu.com/a/1060000000397536 — 2023-04-18 — Tier: 3
[43] OpenHW Group — cva6 issue #3731 — https://github.com/openhwfoundation/cva6/issues/3731 — 2026-11-09 — Tier: 3
[44] Semiconductor Engineering — The Future Of SoC Design Is Data Movement — https://semiengineering.com/the-future-of-soc-design-is-data-movement/ — 2025-09-25 — Tier: 2
[45] S. Ibarra-Delgado et al. — A Bandwidth Control Arbitration for SoC Interconnections (Micromachines) — https://www.mdpi.com/2072-666x/11/12/1063 — 2020 — Tier: 1
[46] Arteris — Ncore: Scalable Cache-Coherent (C-NoC) IP — https://www.arteris.com/products/coherent-noc-ip/ncore/ — Accessed 2026-11-18 — Tier: 3
[47] Arteris — FlexNoC Interconnect IP — https://www.arteris.com/products/non-coherent-interconnect-ip/flexnoc/ — Accessed 2026-11-18 — Tier: 3
[48] Arteris — Resources（"100's of person-years" 论断） — https://www.arteris.com/resources/ — Accessed 2026-11-18 — Tier: 3
[49] Arteris — Tenstorrent Case Study — https://www.arteris.com/resources/case-studies/tenstorrent/ — Accessed 2026-11-18 — Tier: 3
[50] Arteris — ZTE licenses Arteris Ncore and FlexNoC — https://www.arteris.com/press-releases/zte-licenses-arteris-ncore-and-arteris-flexnoc/ — 2017-03-14 — Tier: 3
[51] Arteris — FlexNoC 4 Interconnect IP with Artificial Intelligence Package — https://www.arteris.com/press-releases/flexnoc-ai-package-flexnoc-4/ — 2018-10-31 — Tier: 3
[52] Arteris — Enflame licenses FlexNoC for AI chip — https://www.arteris.com/press-releases/enflame-ai-chip-arteris-ip-flexnoc/ — Accessed 2026-11-18 — Tier: 3
[53] P. Roy et al. — Hierarchical Formal Verification and Progress Checking of NoC — https://dvcon-proceedings.org/wp-content/uploads/1025-2.pdf — Accessed 2026-11-18 — Tier: 1
[54] F. Verbeek group — Formal Verification of Deadlock Avoidance Mechanisms (SIREN) — https://www.cs.ru.nl/~freekver/SIREN-Poster-1.pdf — Accessed 2026-11-18 — Tier: 1
[55] L. Arditi (Codasip) — Formal verification best practices: investigating a deadlock — https://codasip.com/2023/09/26/formal-verification-best-practices-investigating-a-deadlock/ — 2023-09-26 — Tier: 2
[56] University of Oulu — SOC Regression Strategy Development (MSc thesis) — https://oulurepo.oulu.fi/bitstream/10024/42377/1/nbnfioulu-202306302798.pdf — 2023-06-30 — Tier: 2
[57] Design & Reuse — SoC interconnect performance verification based on hardware emulator — https://www.design-reuse.com/article/58857-soc-interconnect-performance-verification-methodology-based-on-hardware-emulator/ — 2007-07-09 — Tier: 2
[58] gem5 — Architectural Exploration with gem5 (ASPLOS 2017 tutorial) — https://www.gem5.org/assets/files/ASPLOS2017_gem5_tutorial.pdf — 2017-04-09 — Tier: 1
[59] ACM — A Systematic Mapping Study on SystemC/TLM Modeling — https://dl.acm.org/doi/10.1145/3735641 — Accessed 2026-11-18 — Tier: 1
[60] Semiconductor Engineering — Using SystemC TLM Modeling To Solve AI Data Movement Challenges — https://semiengineering.com/using-systemc-tlm-modeling-to-solve-ai-data-movement-challenges/ — 2026-05-28 — Tier: 2
[61] Tenstorrent — Memory from a kernel developer's perspective (TT-Metalium) — https://docs.tenstorrent.com/tt-metal/latest/tt-metalium/tt_metal/advanced_topics/memory_for_kernel_developers.html — Accessed 2026-11-18 — Tier: 1
[62] 平头哥（T-Head）— 真武 M890 AI 芯片亮相（ICN Switch 1.0） — https://www.t-head.cn/news/newsDetail?id=188 — Accessed 2026-11-18 — Tier: 2
[63] 华为 — 以开创的超节点互联技术，引领 AI 基础设施新范式 — https://www.huawei.com/cn/news/2025/9/hc-xu-keynote-speech — 2025-09-18 — Tier: 1
[64] 知乎专栏 — 昇腾 950 架构深度拆解：多 Die 合封 + UB 灵衢互联 — https://zhuanlan.zhihu.com/p/2052319171556521722 — Accessed 2026-11-18 — Tier: 3
[65] Semiconductor Digest — Apple Joins 3D-Fabric Portfolio with M1 Ultra? — https://www.semiconductor-digest.com/apple-joins-3d-fabric-portfolio-with-m1-ultra/ — 2022-03-23 — Tier: 3
[66] Y. Ouyang et al. — MMNNN: A tree-based Multicast Mechanism for NoC-based DNN accelerators — https://dl.acm.org/doi/abs/10.1016/j.micpro.2021.104242 — 2021 — Tier: 1
[67] Z. Su et al. — An Ultra-Low Cost and Multicast-Enabled Asynchronous NoC — https://www.zora.uzh.ch/server/api/core/bitstreams/581c4bfc-66b8-4adb-864c-f9e79b0a7ca4/content — 2024 — Tier: 1
[68] AMD — DS946: XQR Versal AI Core — NoC — https://docs.amd.com/r/en-US/ds946-xqr-versal-ai-core/NoC — Accessed 2026-11-18 — Tier: 1
[69] AMD/Xilinx — Introduction to the Versal ACAP AI Engine (Vitis 2022.2 workshop) — https://www.amd.com/content/dam/amd/en/documents/university/workshops/vitis/2022_2/introduction_to_the_versal_acap_ai_engine_and_to_its_programming_model_20230428.pdf — 2023-04-28 — Tier: 1
[70] C. E. Cummings — Clock Domain Crossing Design & Verification Techniques Using SystemVerilog (SNUG) — http://staff.ustc.edu.cn/~wyu0725/FPGA/snug_collection/1Sunburst%20Design/Clock%20Domain%20Crossing%20(CDC)%20Design%20&%20Verification%20Techniques%20Using%20SystemVerilog.pdf — Accessed 2026-11-18 — Tier: 2
[71] Network-Offloaded Bandwidth-Optimal Broadcast and Allgather — https://arxiv.org/html/2408.13356v2 — 2024-11-11 — Tier: 1
[72] 香山（XiangShan）— Bus Interface（用户手册） — https://docs.xiangshan.cc/projects/user-guide/en/latest/bus-interface/ — Accessed 2026-11-18 — Tier: 2
[73] 10xEngineers — A Comparison on Different AMBA 5 CHI Verification IPs — https://10xengineers.ai/a-comparison-on-different-amba-5-chi-verification-ips/ — 2025-10-09 — Tier: 3
[74] 10xEngineers — Formal Verification (NoC scaling) — https://10xengineers.ai/formal-verification/ — Accessed 2026-11-18 — Tier: 3
[75] Arteris — AMBA CHI, ACE, AXI, AHB, APB & OCP protocols in NoC design — https://www.arteris.com/learn/axi-protocol-arm-ace-ahb-apb/ — Accessed 2026-11-18 — Tier: 3
[76] Maven Silicon — Understanding the AMBA CHI Protocol — https://www.maven-silicon.com/blog/understanding-the-amba-chi-protocol-a-technical-overview/ — 2026-07-13 — Tier: 3
[77] arXiv — A RISC-V Simulator for the Tenstorrent Tensix Architecture (Wolfpine) — https://arxiv.org/html/2611.04740 — 2026-11-03 — Tier: 1

---

## Source Extracts

> 仅收录正文实际引用且有可摘录内容的关键来源；其余来源见 Bibliography。所有直引均来自本次检索的页面/摘要，PDF 二进制无法解析处已注明。

### [1] Arm — AMBA 5 总览
- **Summary:** Arm 对 AMBA 5 的官方定位页，把 CHI 定义为面向完全一致处理器与高性能互连的接口，并列出 Issue E 的可选特性。
- **Key quotes:** "fully coherent processors and high-performance interconnects"；Issue E 增加 "Memory Tagging (MTE), multiple interfaces, replicated channels"。
- **Source type:** docs | **Tier:** 1

### [2] Semiconductor Engineering — From ACE to CHI
- **Summary:** 解释 CHI 用四个统一通道替代 ACE 的五个 AXI 派生通道，以"减少线数"；是本次检索中唯一的机制性论据，无定量数字。
- **Key quotes:** CHI uses "Four unified channels" to "reduce the number of wires."
- **Source type:** trade press | **Tier:** 2

### [3] Cadence — CHI-E 演进
- **Summary:** 从验证工具视角追踪 CHI 各 Issue 的交易类型增长。
- **Key quotes:** "Issue E was by far the biggest update yet with a slew of new transactions"；"added new transaction types such as Write*Zero, SnpQuery, MakeReadUnique(Excel)"。
- **Source type:** vendor blog | **Tier:** 2

### [4] Synopsys — CHI-G VIP
- **Summary:** 汇总 CHI Issue G（2024）新增特性。
- **Key quotes:** CHI-G adds "Memory Encryption Contexts (MEC), Limited Data Elision, RME-DA, DataSource Extensions, DataTarget, and PrefetchTgtHint"。
- **Source type:** vendor blog | **Tier:** 2

### [5] SiFive — TileLink 1.7.1
- **Summary:** 规范性文档。基础 A/D 通道做数据搬运，最高一致性级别 TL-C 只增加权限管理通道。
- **Key quotes:** "The highest protocol conformance level (TL-C) adds three additional channels that provide the capability to manage permissions on cached blocks"。
- **Source type:** spec | **Tier:** 1

### [7] Arm — Cache Coherency 与异构计算
- **Summary:** Arm 自己对 IO 一致性的定位说明。
- **Key quotes:** "The AMBA 4 ACE-Lite interface is designed for IO (or one-way) coherent system masters like DMA engines, network interfaces and accelerators."
- **Source type:** vendor blog | **Tier:** 2

### [8] Design & Reuse — AMBA 4 ACE
- **Summary:** 定义 ACE-Lite 主设备的可缓存交易限制。
- **Key quotes:** "ACE-Lite masters can perform transactions only from the Non-shared, Non-cached and Cache Maintenance transaction groups."
- **注:** 该引文只覆盖**可缓存数据交易**；ACE-Lite 仍可承载屏障与 DVM 消息。
- **Source type:** trade press | **Tier:** 2

### [9] Peccerillo et al. — 加速器综述
- **Summary:** 同行评议综述，把非一致 DMA 直连 DRAM 当作加速器基线模型。
- **Key quotes:** "Non-coherent accelerators access directly the DRAM through DMA bypassing the cache hierarchy."
- **Source type:** journal | **Tier:** 1

### [10] Arm — CMN-700
- **Summary:** 可配置 XP 网格、原生 CHI、非阻塞数据搬运；公开材料未描述 payload 多播。
- **Key quotes:** "The scalable mesh network can be customized… The native AMBA 5 CHI network provides high-frequency, non-blocking data transfers."
- **Source type:** docs | **Tier:** 1

### [12] Arm — CMN-700 DVM 优化
- **Summary:** 说明 CMN 广播的是共享域 snoop/DVM 消息，而非把数据复制到多个目标。
- **Key quotes:** "The CMN-700 RN-F will broadcast all Inner-Shareable and Outer-Shareable forms on CHI."
- **Source type:** docs | **Tier:** 1

### [13] Arm — CMN-700 时钟域配置
- **Summary:** 生产级 mesh fabric 原生支持多异步域，且可把网格切成四个异步域。
- **Key quotes:** "CMN‑700 supports either one synchronous or multiple asynchronous clock domains"；"supports dividing the mesh into four asynchronous clock domains."
- **Source type:** docs | **Tier:** 1

### [14] Arm — CMN-650 时钟域配置
- **Summary:** 明确 CDC 由 crosspoint 承担并约束其位置。
- **Key quotes:** "AMCSs must bridge asynchronous clock domains"；"configure the AMCS that bridges the two clock domains next to one or two MCSs in a chain."
- **Source type:** docs | **Tier:** 1

### [16] FlooNoC
- **Summary:** 12nm、8×4 mesh 的开源宽链路 NoC，给出面积与能耗的当前最好锚点。
- **Key quotes:** 645 Gbps per link；"minimal area overhead of only 3.5% per compute tile"；"0.15 pJ/B/hop" @0.8V。
- **Source type:** arXiv | **Tier:** 1

### [17] MicroSwitch (NOCS 2017)
- **Summary:** 系统比较 mesh、多总线/多树与"理想单周期零竞争网络"，论证通用 mesh 对空间型 NN 加速器是过度设计，并提出面向 scatter/gather/multicast 的小 crossbar + bus 生成器。
- **Key quotes:** "We compare the performance of a mesh, and a multi-bus/multi-tree topology against an 'ideal' NoC"；"a novel NoC generator that generates a network tailored for the traffic flows within a neural network, namely scatters, gathers…"
- **Source type:** conference | **Tier:** 1

### [19] BENoC
- **Summary:** 提出总线增强 NoC，理由是多跳对延迟敏感信号太慢。
- **Key quotes:** "While NoCs are efficient in delivering high throughput point-to-point traffic, their multi-hop operation is too slow for latency sensitive signals."
- **Source type:** paper | **Tier:** 1

### [20] Tavakkol et al. — Mesh Connected Crossbars
- **Summary:** 用网格内嵌 crossbar 控制节点度，是"crossbar → 分层结构"交叉点的早期形式化。
- **Key quotes:** "we will replace the fully-connected sub-graphs with crossbar switches to implement the MCC, which leads to better node degree."
- **Source type:** conference | **Tier:** 1

### [21] NVDLA 架构规范
- **Summary:** 直接从阵列几何推导读端口带宽。
- **Key quotes:** "Convolution read bandwidth determines the width of the read port… (data_size*Atomic-C)…"
- **Source type:** docs | **Tier:** 1

### [23] TPU v4
- **Summary:** ICI 是与 HBM、MXU 同量级的一阶性能变量。
- **Key quotes:** "The ICI interconnection network (across chips) is also a significant performance driver."
- **Source type:** arXiv | **Tier:** 1

### [26] TriGen
- **Summary:** 端侧配置：每 NPU 32×32 MPA + 32 GB/s DRAM，折合约 64 ops/byte。
- **Key quotes:** "Each NPU is engaged with 32 × 32 MPA. DRAM bandwidth is set up to 32 GB/s…"
- **Source type:** arXiv | **Tier:** 1

### [31] Arm — AXI 传输行为与交易排序
- **Summary:** AXI 官方排序模型：同 ID 保序，异 ID 无约束（同地址仍有例外）。
- **Key quotes:** "all transactions with a given ID must be ordered. However, there is no restriction on the ordering of transactions" with a different ID.
- **Source type:** docs | **Tier:** 1

### [32] Arm — AXI/CHI 规范更新
- **Summary:** 承认 ID 排序模型给互连带来跟踪开销，AXI5 引入 Unique ID 指示符解耦。
- **Key quotes:** "AXI originally defined an ID-based ordering model… imposes additional requirements on interconnects to track IDs"；Unique ID "eliminates the need for interconnects and downstream components to track those IDs for ordering purposes."
- **Source type:** vendor blog | **Tier:** 1

### [33] Leone et al. — 预抢占 VC
- **Summary:** 证明 AXI 读写通道解耦可在端点造成环形等待，即使路由无死锁；给出四种流量类分离方案的成本对比。
- **Key quotes:** "protocol-level dependencies between read and write traffic can create circular waits at the network endpoints, even when the routing algorithm" is deadlock-free；预抢占 VC "save up to 76% of link resources … only 3% router area overhead relative to the multiplane design".
- **Source type:** arXiv/IEEE | **Tier:** 1

### [34] Altera Stratix 10 errata
- **Summary:** 真实硅片 errata：写数据先于写地址到达可导致死锁。
- **Key quotes:** "A write response must always follow the last write transfer in a write transaction. Read data must always follow the read address of the data."
- **Source type:** errata | **Tier:** 1

### [37] Mandal et al. — WRR
- **Summary:** WRR 的 QoS/公平性优于严格优先级；吞吐对照对象是 RR。
- **Key quotes:** "WRR delivers better quality of service than NoCs with strict priority-based arbitration."
- **Source type:** arXiv | **Tier:** 1

### [39] AMD PG313 — VC 仲裁
- **Summary:** 商用 NoC 的优先级策略与 eligibility 条件。
- **Key quotes:** "High priority requests always take precedence over low priority requests, however a virtual channel must meet the following conditions to be eligible…"
- **Source type:** docs | **Tier:** 1

### [42] CHI 错误处理
- **Summary:** Poison/DataCheck 与 DERR/NDERR 两套机制的区分。
- **Key quotes:** "RespErr==0b10时DERR，0b11是NDERR"；"Poison和DataCheck的设置是独立的"。
- **Source type:** community blog | **Tier:** 3

### [43] CVA6 #3731
- **Summary:** 现网缺陷：cache refill 的错误响应码被流水线丢弃，形成静默损坏。
- **Key quotes:** "The response code is dropped inside the" pipeline.
- **Source type:** issue tracker | **Tier:** 3

### [49] Arteris — Tenstorrent 案例
- **Summary:** 厂商案例研究，称 Tenstorrent 在一致 NoC 上采用 Arteris IP；未披露数字。
- **Key quotes:** "Tenstorrent chose Arteris for IP over building its own NoC."
- **注:** 厂商材料，无法证明"评估并否决了自研"。
- **Source type:** vendor case study | **Tier:** 3

### [51] Arteris — FlexNoC 4 AI Package
- **Summary:** 商业化数据多播能力的公开描述。
- **Key quotes:** Multicast "optimizes the usage of on-chip and off-chip bandwidth by broadcasting data as close to network" endpoints.
- **Source type:** press release | **Tier:** 3

### [52] Arteris — Enflame 案例
- **Summary:** 确认多播/广播在 AI 芯片中已量产使用。
- **Key quotes:** "Implements means to multicast and/or broadcast data on the interconnect, which can help reduce off-chip memory accesses."
- **Source type:** press release | **Tier:** 3

### [53] DVCon — 层次化形式化验证
- **Summary:** 平地形式化在 NoC 规模上关不住，需要层次化 + progress checking。
- **Key quotes:** "This paper presents a hierarchical formal verification methodology."
- **Source type:** conference | **Tier:** 1

### [55] Codasip — 死锁形式化
- **Summary:** 把死锁自由表述为 liveness 属性。
- **Key quotes:** "To ensure a design is deadlock free, one approach consists in verifying that it is 'always eventually' able to respond to a request."
- **Source type:** vendor blog | **Tier:** 2

### [57] 硬件仿真器互连性能验证
- **Summary:** 三个互连 bug 各自都能让整系统死锁，且都在仿真阶段才被发现。
- **Key quotes:** "Three interconnect killer bugs were found during the interconnect performance verification. Each of these bugs were dead locking the overall" system.
- **Source type:** trade press | **Tier:** 2

### [58] gem5 教程
- **Summary:** gem5 不是周期精确模型，fabric 模型必须标定。
- **Key quotes:** "gem5 is not a cycle-accurate microarchitecture model!"
- **Source type:** tutorial | **Tier:** 1

### [59] SystemC/TLM 系统映射研究
- **Summary:** TLM 相对 RTL 最高约 100 倍加速。
- **Key quotes:** "SystemC/TLM models significantly reduce simulation time, reaching a speed-up of up to 100x compared with" RTL.
- **Source type:** ACM | **Tier:** 1

### [66] MMNNN — 树状多播
- **Summary:** 面向 DNN NoC 的树状多播路由，目标是最小化包数。
- **Source type:** journal | **Tier:** 1

### [67] 多播异步 NoC
- **Summary:** 多播 + 异步 fabric，报告 42%–71% 能耗节省（口径是能耗，不是带宽）。
- **Key quotes:** "energy savings ranging from 42% to 71% over a state-of-the-art NoC."
- **Source type:** paper | **Tier:** 1

### [68] AMD Versal NoC
- **Summary:** 端侧可编程 NoC 是 AXI4 派生网络。
- **Key quotes:** "The programmable NoC is an AXI4 based network of interconnect within the Versal architecture."
- **Source type:** datasheet | **Tier:** 1

### [72] 香山 Bus Interface
- **Summary:** 独立实现者视角下的 CHI 四通道结构。
- **Key quotes:** "CHI uses different channels to transmit various messages, including: Data (DAT); Request (REQ); Response (RSP); Snoop (SNP)."
- **Source type:** docs | **Tier:** 2

### [73] CHI VIP 对比
- **Summary:** CHI VIP 市场的存在本身就是验证成本证据，卖点直接写"Issues A–G"。
- **Key quotes:** "Supports AMBA 5 CHI Issues A–G."
- **Source type:** vendor comparison | **Tier:** 3

### [76] Maven Silicon — CHI 概述
- **Summary:** CHI 的排序模型是按地址而非按 ID。
- **Key quotes:** "Within a single master, ordering must be preserved for the same address. Between different addresses, CHI allows out-of-order completion."
- **Source type:** training blog | **Tier:** 3

### [77] Wolfpine
- **Summary:** 为 Tenstorrent Tensix/NoC 单独写模拟器，说明自研 fabric 会外溢成工具链成本。
- **Key quotes:** "an open source simulator for AI accelerators and initially focussing on Tenstorrent's Tensix."
- **Source type:** arXiv | **Tier:** 1
