# Hive NPU 架构演进与产品对标路线图

文档版本：`v0.7.0-draft`

修订日期：`2026-10-28`

> 本文是[总体架构](arch.md)的非规范性配套参考，只汇总产品调研、竞品比较、代际演进、产品分档、成本与阶段验收建议。模块级性能推导和敏感性分析记录在[性能分析](performance_analysis.md)；具体接口、寄存器、错误码和时序仍以正式架构与模块规格为准。本文中的远期指标是规划候选，不构成 RTL、PPA 或产品交付承诺。

2026-10-28同步G0批准模型范围至名义0.5B–2B：Qwen3.5-2B仅新增身份，精度、容量及服务/质量门槛待独立评审。G0/G0 Pro当前配置和原四模型验收以总体架构为准；本路线图历史W4、YOLOv8和扩展率候选不替代已批准基线，也不作为新增2B模型的保证。

G0/G0 Pro当前不将QoS有界服务及详细异常恢复/故障隔离列为架构评审项；正常前进性、反压响应正确性、初始化reset和正常clock-gating/drain仍需验证。后续G1/G2的确定性服务及故障包含仅为对应代际规划，不反向成为G0架构要求。

2026-10-28批准整个NPU区域具备独立电源与时钟域，支持独立调频及受控电源关断；500 MHz仍为既有服务验收基准，不因可调频提高算力口径。该能力不默认包含独立调压、逐core电源域或断电状态retention，域边界与正常切换细项以总体架构第10章为准。

## 1. 文档目的与路线原则

### 1.1 目的

本文回答以下问题：

- 当前架构怎样先落地为 1T/2T 产品定位的端侧完整 SoC，并冻结 T 的计数口径；
- 怎样演进为带独立本地内存、可通过高速 SerDes/D2D 扩展的家庭算力卡；
- 怎样进一步演进到高性能、强可靠、具备功能安全基础的具身智能平台；
- 每一代怎样划分标准版与 Pro 版，并用哪些工作负载和硬指标判断产品达标；
- 每一阶段参考哪些产品，其优势、劣势及不可直接照搬的部分是什么；
- 哪些接口和数据结构应从首版起保持可扩展，避免后续整体推倒重来。

### 1.2 产品边界

产品形态随代际演进：

- **G0 是完整端侧 SoC。** 单芯片集成应用 CPU、本地控制 CPU、NPU、VPU、内存控制器、系统互联及目标市场所需的基础外设，能够不依赖外部强主控完成启动、模型执行和设备控制；
- **G1/G2 是外挂算力卡。** 卡内仍保留管理 CPU、固件、调度、遥测和故障恢复能力，但不承担完整桌面/机器人主控职责；
- G1/G2 由外部性能更强的 Host CPU 负责操作系统、应用编排、模型切分和多卡拓扑管理；卡内管理 CPU 负责本卡自治，不替代 Host；
- 后续高速互联用于板内、卡间或封装间本地拓扑扩展，不引入 Ethernet/IP/RoCE 等网络语义；
- G2 的安全控制闭环不依赖普通 NPU 任务或外部 Host 及时完成，安全控制器与高性能计算域分离。

### 1.3 G0 架构原则

- local SRAM 由软件和 scheduler 显式管理，G0 不引入 shared LLC；
- 以 DMA 预取、显式驻留和双缓冲获得确定性，不依赖缓存命中；
- G0 不实现跨 core 硬件 cache coherence；
- 同步、CPU cache 维护和原子空间分别定义，不把 shared LLC 作为它们的隐含前提；
- 优先闭环单 core DMA→GEMM→VPU→DMA，再集成为 2/4-core SoC；
- NPU core 频率按 500 MHz 规划，不通过提高频率补足 SKU 算力。

## 2. 性能口径与比较限制

### 2.1 统一口径

本文采用以下报告口径：

- 原始吞吐先按 MAC/s 报告；需要换算 operations/s 时，明确采用 1 MAC = 1 次乘法 + 1 次加法 = 2 operations；该换算规则不等于 1T/2T SKU 命名口径已经冻结；
- TOPS 必须注明 INT8、INT4、FP8、FP4 或其他精度；
- dense 与 sparse 峰值分别报告，不把稀疏峰值当作任意模型可达值；
- 峰值算力、持续算力、完整模型吞吐和能效分别报告；
- GB/s 使用十进制，GiB/MiB/KiB 使用二进制；
- 厂商没有公开精度、稀疏或功耗条件时，表中明确标为“口径未公开”。

不同产品的 TOPS 不能脱离以下条件直接相除：

- 精度、累加位宽和舍入规则；
- 是否使用结构化稀疏；
- 是否包含矩阵、向量、CPU 或 GPU 的联合算力；
- 内存带宽、数据复用和算子回退；
- 热稳态功耗与允许的短时峰值。

### 2.2 当前计算模块理论值

当前默认几何为 T=16、256 个 PE，G0 NPU core 频率按 500 MHz 规划。硬件原始吞吐与按 2 operations/MAC 换算的宣传口径同时列出：

| 运行格式 | 单 core MAC/cycle | 500 MHz 单 core | 2 cores（G0 标准版） | 4 cores（G0 Pro） |
|---|---:|---:|---:|---:|
| INT8、FP8、MXFP8、合法 W4A8 | 512 | 0.256 TMAC/s；按 2 ops/MAC 为 0.512 TOPS | 0.512 TMAC/s；按 2 ops/MAC 为 1.024 TOPS | 1.024 TMAC/s；按 2 ops/MAC 为 2.048 TOPS |
| INT4、MXFP4 | 1024 | 0.512 TMAC/s；按 2 ops/MAC 为 1.024 TOPS | 1.024 TMAC/s；按 2 ops/MAC 为 2.048 TOPS | 2.048 TMAC/s；按 2 ops/MAC 为 4.096 TOPS |

这些是有效计算拍峰值，不包含权重装载、阵列排空、DMA 等待、bank conflict、VPU 和调度开销。G0 外部内存采用 LPDDR4x，但总位宽、速率、容量和有效效率仍待冻结，因此当前不能发布持续模型吞吐或虚构降频后的功耗收益。

G0 标准版固定为 2 cores×500 MHz，产品定位名为 1T SKU；G0 Pro 固定为 4 cores×500 MHz，产品定位名为 2T SKU。这里的 1T/2T 尚未冻结为 TMAC/s 或按 2 operations/MAC 换算的 TOPS，正式对外比较前必须先统一口径。Pro 通过增加计算与存储资源形成，不通过提高 NPU core 频率实现。

### 2.3 代际达标规则

每一代及其 Pro 版本都必须定义可通过或不通过的产品 KPI。峰值 TOPS 只用于确认资源规模，不能单独证明达到该代产品指标。

代际放行至少同时满足：

1. **数值正确性：** 目标模型和算子回归全部通过，量化误差在批准范围内；
2. **算子覆盖率：** 目标模型落在 NPU/VPU 的执行时间比例达到门槛，Host CPU fallback 不超过上限；
3. **持续计算效率：** 对冻结形状的大矩阵和代表性算子给出持续性能/理论峰值；
4. **内存效率：** DMA、外部内存、本地 SRAM 和 D2D 分别给出有效带宽/理论带宽；
5. **端到端性能：** 用冻结模型报告 FPS、token/s、首 token 延迟和尾延迟；
6. **能效与热稳定：** 报告芯片/卡/整机功耗，长时间运行后不得靠不可接受的降频维持；
7. **稳定性与可靠性：** G0验证长稳、持续反压、初始化reset和正常drain；G1/G2的错误注入、故障恢复及包含边界按各自代际选择的能力评审；
8. **软件可用性：** 模型部署、驱动、runtime、profiler 和升级流程达到对应代际要求。

任一强制项未达标时，只能标记为工程样片或预览版本，不能以峰值 TOPS 宣称达到该代产品指标。

## 3. 产品代际总览

| 代际 | 产品定位 | 主要对标产品 | 推荐计算规模 | 存储与互联 | 主要工作负载 |
|---|---|---|---|---|---|
| G0：端侧完整 SoC | 单芯片完成设备控制与边缘推理 | RK3568、NXP i.MX 8M Plus、RK3399Pro | 标准版 1T、Pro 2T 产品定位；TMAC/s 与 TOPS 口径待冻结 | 显式 local SRAM；LPDDR4x；无 D2D | 视觉、语音、轻量 Transformer；当前批准语言模型扩至名义0.5B–2B，精确身份与格式见总体架构 |
| G1：家庭算力卡 | 外部 Host 控制、卡内管理 CPU 自治的本地 AI 加速卡 | Axelera Metis、Mac mini M4、DGX Spark | 建议评估 32–128 dense INT8 TOPS/卡，不作为当前承诺 | 16–64GB 卡载内存；Host PCIe；独立 SerDes/D2D；显式 P2P DMA | 7B/14B 模型、视觉生成、多任务并发、多卡模型切分 |
| G2：具身智能卡 | 外部强主控协调多卡，卡内管理 CPU 与安全域自治 | Jetson AGX Orin/Thor、TI TDA4VH-Q1 | 依据模型和安全等级冻结，性能参照 Orin–Thor 区间 | ECC 本地内存；冗余侧带；确定性 QoS；故障包含域 | 多传感器融合、VLM/VLA、规划推理、实时感知 |

G1/G2 的规模必须通过工作负载和热设计反推，不能简单按 G0 core 数量线性复制。

### 3.1 标准版与 Pro 分档

| 代际 | 标准版定位 | Pro 版增强方向 | Pro 版成立条件 |
|---|---|---|---|
| G0 | 2 NPU cores×500 MHz，1T SKU；基础 LPDDR4x 配置 | 4 NPU cores×500 MHz，2T SKU；同步提高内存容量或带宽，避免计算资源空转 | 冻结 1T/2T 的 TMAC/s/TOPS 口径；代表模型端到端性能显著提升，且热稳态、CPU/VPU 和内存不成为新瓶颈 |
| G1 | 单卡基础 compute tile 数、16–32GB 本地内存候选、基础 D2D 端口 | 增加 compute tiles、32–64GB 内存、更高内存带宽、更多 D2D 端口或更强卡内管理 CPU | 至少在算力、模型容量、多卡扩展或并发能力之一形成可测量提升，不以短时超频定义 Pro |
| G2 | 满足目标机器人的基础传感器并发、实时和安全需求 | 增加计算/内存、传感器并发、冗余 D2D、故障降级能力或更高安全可用性 | 在热稳态和故障场景下仍达到 Pro KPI，不只提高无故障峰值 TOPS |

标准版与 Pro 共用软件 ABI、descriptor 和基础编程模型。Pro 可以增加 capability，但不得让标准版软件静默产生不同语义。

## 4. G0：端侧 1T/2T SKU

### 4.1 产品目标

G0 目标是在单芯片内完成可验证、可综合、可运行的端侧 SoC 闭环：

- 标准版 2 NPU cores×500 MHz，产品定位为 1T SKU；原始 INT8/FP8 吞吐为 0.512 TMAC/s，按 2 ops/MAC 换算为 1.024 TOPS；
- Pro 版 4 NPU cores×500 MHz，产品定位为 2T SKU；原始 INT8/FP8 吞吐为 1.024 TMAC/s，按 2 ops/MAC 换算为 2.048 TOPS；
- 集成应用 CPU、本地控制 CPU、NPU、VPU、LPDDR4x 控制器、系统互联及目标市场所需基础外设；
- local SRAM 显式分区与双缓冲；
- 单一受信任任务上下文；
- local CPU/应用 CPU 可完成模型提交和设备控制，不依赖外部强 Host；异常恢复具体在实现阶段考虑，不在当前架构细化；
- 不实现 shared LLC、peer window、跨卡 D2D、抢占和多租户；
- VPU 至少覆盖目标模型所需的激活、归一化、格式转换和基础 reduction。

### 4.2 竞品规格比较

| 产品 | AI 算力 | 内存/接口 | 优势 | 劣势及比较限制 |
|---|---|---|---|---|
| Rockchip RK3568 | 官方资料出现 0.8 TOPS 与 1T 两种表述；精度口径未明确 | 32-bit LPDDR4/4X、DDR4 等；PCIe、USB、双 GbE 等 SoC 外设 | 成本和功耗导向明确，外设丰富，适合视觉与轻量边缘设备 | 算力精度口径不透明；是完整 SoC，不代表独立 NPU 卡接口；持续 NPU 性能和功耗缺少统一公开值 |
| NXP i.MX 8M Plus | 2.3 TOPS INT8 | 32-bit LPDDR4-4000/DDR4-3200；PCIe Gen3 x1；工业接口丰富 | INT8 口径明确，量产与长期供货体系成熟，eIQ 软件栈和工业 BSP 完整 | 算力和内存带宽有限；部分算子可能回退 CPU；不适合作为中大型 LLM 性能上界 |
| Rockchip RK3399Pro | 最高约 3 TOPS，支持 8/16-bit 推理 | 双通道 DDR/LPDDR；PCIe 2.1 x4 等 | 作为 G0 的上界护栏，已有成熟 RKNN 流程 | 产品和软件代际较早；公开资料对精度、持续性能和功耗的条件不充分 |
| G0 建议方案 | 标准版：0.512 TMAC/s 或按 2 ops/MAC 为 1.024 TOPS；Pro：1.024 TMAC/s 或 2.048 TOPS | 完整 SoC；LPDDR4x；512KiB 或 1MiB local SRAM/core 候选 | 支持 INT8/INT4/FP8/MX 等低精度演进；显式 SRAM 行为可预测；单芯片可独立完成设备控制与推理 | 1T/2T 对外计数口径尚未冻结；且无 SoC 顶层、SRAM 互联、完整 CPU/VPU 集成、软件栈、硅后功耗与 PPA，不能按峰值宣称产品性能 |

### 4.3 相对竞争力判断

潜在优势：

- 支持 INT4、FP8 和 MX 家族，可为后续模型压缩与混合精度保留空间；
- scratchpad 显式管理有利于确定性和细粒度调优；
- 通用 DMA 与 GEMM 解耦，便于实现布局变换和任务重叠；
- 同一架构可通过 2/4 cores×500 MHz 形成 1T/2T 两档 SKU，同时保留 MAC/s 与按 2 ops/MAC 换算值。

主要弱点：

- 缺少完整算子闭环，特别是 VPU、attention 周边和后处理；
- 缺少成熟编译器、量化、图划分、runtime、驱动和性能分析工具；
- 未完成 PPA，500 MHz、SRAM macro、bank 数和总线规模尚未证明；
- LPDDR4x 总位宽、速率、容量和有效效率未冻结，Pro 版是否能获得接近线性的收益尚不确定；
- 模块默认配置、发射配置和产品 SKU 配置尚未形成单一权威配置表。

### 4.4 G0 退出门槛

G0 只有满足以下条件后才进入 G1：

1. 单 core DMA→GEMM→VPU→DMA 功能闭环；
2. 代表性视觉和轻量 Transformer 端到端运行，无 CPU 大比例算子回退；
3. 给出 dense INT8/INT4 持续利用率、DDR 有效带宽、SRAM bank conflict 和 P99 延迟；
4. 锁定 SRAM macro、面积、频率、动态功耗和热稳态频率；
5. 形成稳定的任务提交、完成与能力查询契约；详细错误完成与恢复机制不作为当前G0架构退出前置；
6. 完成持续反压、正常前进性、初始化reset和正常clock-gating/drain验证，不要求QoS有界服务或故障隔离专项。

## 5. G1：家庭算力盒与外挂计算卡

### 5.1 产品目标

G1 的形态不是完整主控 SoC，而是由外部强 Host 管理、卡内管理 CPU 自治、带独立本地内存的计算卡：

```text
External Host CPU / OS
          |
       Host PCIe
          |
+---------v---------------------------------------+
| NPU card                                       |
| management CPU / firmware / command processor  |
|          |                                     |
| compute tile -- compute tile -- ...            |
|      |              |                          |
| local memory controllers / card memory         |
|      |                                         |
| local SerDes/D2D ports <--> adjacent cards     |
+------------------------------------------------+
```

外部 Host 负责应用、模型切分、资源策略和多卡全局编排；卡内管理 CPU 负责本卡启动、命令处理、内存管理、遥测、链路维护、错误恢复和固件升级。即使 Host 暂时不响应，卡也必须能完成已有事务的排空或确定失败，不能因管理 CPU 能力较弱而失去本卡自治。

核心要求：

- Host 接口与卡间 D2D 分离，避免所有卡间数据绕 Host root complex；
- 卡载内存用于权重、KV 和大 tensor 驻留；
- 首版跨卡采用显式 remote DMA、message/event 和 collective，不做硬件全局 cache coherence；
- 从“core 复制”演进为“compute tile 复制”，每个 tile 内部完成局部调度、SRAM 和 DMA 闭环；
- 支持能力发现、拓扑发现、P2P 地址窗口、故障隔离和单卡复位；
- 管理 CPU 性能按控制面、链路服务和故障恢复负载确定，不承担主要 tensor 计算。

### 5.2 竞品规格比较

| 产品 | 形态与规格 | 优势 | 劣势及不可照搬部分 |
|---|---|---|---|
| Apple Mac mini M4/M4 Pro | 完整主控 SoC 整机；M4 Neural Engine 官方 38 万亿次操作/秒但未公开精度；M4/M4 Pro 统一内存带宽约 120/273 GB/s | 统一内存降低拷贝开销，软硬件集成成熟，静音桌面体验和模型可装入性强 | 不是外挂卡；CPU/GPU/NPU 共封装；没有公开本地多卡统一内存；38 TOPS 不能与明确 INT8 的数据直接比较 |
| NVIDIA DGX Spark | GB10 桌面 AI 系统；最高 1 PFLOP FP4；128GB LPDDR5X、约 273 GB/s | 大容量统一内存、CUDA/TensorRT 生态完整，可运行较大模型，提供多设备软件编排参考 | 是完整桌面系统而非卡；FP4/稀疏口径不可与 dense INT8 直接比较；多机扩展采用网络技术，不符合本项目非网络 D2D 边界 |
| Axelera Metis Embedded 113m | M.2 2280 PCIe Gen3 x4 独立卡；214 TOPS INT8；2GB/8GB LPDDR4X；平均功耗上限约 11.55W | 形态最接近目标；INT8/W 突出；支持多设备枚举；证明高算力可下沉到 M.2 功耗级别 | 卡载容量较小；Host 链路相对算力偏窄；公开资料未证明跨卡 P2P、一致性或统一内存；不适合直接照搬为大模型多卡互联方案 |
| G1 建议方案 | 32–128 dense INT8 TOPS/卡候选；16–64GB 卡载内存候选；Host PCIe + 独立 D2D | 保持外挂卡边界；卡间可组成非网络本地拓扑；可按容量、算力和功耗组合不同卡型 | 当前 500 MHz 单 core 原始吞吐仅 0.256 TMAC/s（按 2 ops/MAC 为 0.512 TOPS），直接复制需要大量 core；需要新 compute tile、内存控制器、卡级 management CPU/command processor、SerDes 和完整软件栈 |

G1 建议范围用于方案探索，不是已经批准的产品指标。最终数值应由 7B/14B 模型、并发视觉负载、功耗和成本共同决定。

### 5.3 G1 对竞品的取舍

应借鉴：

- Mac mini 的模型容量、内存带宽、零拷贝体验和低噪声整机目标；
- DGX Spark 的本地模型开发体验、容器化软件栈、拓扑感知调度和多设备工具；
- Axelera 的独立卡边界、低功耗、高 INT8 密度和 Host 多设备管理方式；
- PCIe/UCIe 类协议的链路训练、CRC、replay、credit、错误隔离和可测试性方法。

不应照搬：

- 不把 Mac/DGX 的统一内存假设映射为离散卡之间的低成本 coherence；
- 不把 DGX 的 Ethernet/RoCE/QSFP 网络扩展当作 D2D；
- 不把 FP4 sparse PFLOPS 与 dense INT8 TOPS 混为一张排行榜；
- 不因 M.2 卡可枚举多个设备，就默认具备跨卡 P2P 和统一地址空间。

### 5.4 Compute tile 演进

当前 MX 数据通路与 T=16、totalN=16、scale block 节奏紧耦合。G1 不能仅扩大 `arrayN`，需要在保持单阵列几何可验证的前提下增加层级：

```text
compute tile
  ├── multiple GEMM arrays
  ├── tile-local SRAM
  ├── tile DMA / prefetch
  ├── local scheduler
  └── tile router
```

卡级 command processor 只管理 tile 级任务和依赖，不直接仲裁每个 SRAM bank。这样可以避免几十个 core 共享单层 crossbar、单一 scheduler 和单一完成队列。

### 5.5 D2D/SerDes 最小契约

G1 的本地互联至少定义：

- 物理 lane 数、速率、误码监测、训练和降级；
- packet header、source/destination、transaction identity 和顺序域；
- credit 流控、CRC、replay、timeout 和 link reset；
- remote DMA、event/message、atomic 是否支持及其序列化位置；
- ring、mesh、直连或交换结构的最大 hop 与双截带宽；
- 单链路或单卡故障后的隔离、重路由或任务失败语义；
- Host PCIe reset 与 D2D reset 的独立性。

首版建议只支持显式 P2P DMA 和 event，不实现 remote cache line coherence。collective engine 是否加入，应由多卡模型切分的实际通信比例决定。

### 5.6 G1 退出门槛

1. 单卡 7B/14B 目标模型在卡载内存中完成推理；
2. Host→card、card memory、tile SRAM、计算四级带宽闭环；
3. 双卡 P2P 不经 Host 内存完成 tensor 传输；
4. 给出卡间延迟、单向/双向持续带宽、并发计算下带宽和 collective 效率；
5. 单卡复位、链路错误和卡间任务失败不会破坏其他卡或 Host；
6. 完成热稳态持续性能、墙插功耗、噪声和降频曲线测试。

## 6. G2：具身智能与安全增强

### 6.1 产品目标

G2 面向机器人和工业设备中的多传感器融合、VLM/VLA、规划与实时感知。整机由外部高性能 Host CPU 负责全局感知编排、规划应用和多卡资源管理；每张算力卡保留管理 CPU，并增加独立 safety controller 或 safety island。相比 G1，新增重点不是单纯提高 TOPS，而是：

- 持续性能和有界延迟；
- 外部 Host、卡内管理 CPU、高性能计算域和安全控制域职责分离；
- 高性能计算域与安全控制域隔离；
- 端到端数据完整性、故障诊断和可恢复性；
- 在过温、欠压、链路故障和计算超时下进入确定状态；
- 支持长期供货、诊断资料和系统级安全认证过程。

### 6.2 竞品规格比较

| 产品 | AI、内存与功耗 | 安全/实时能力 | 优势 | 劣势及比较限制 |
|---|---|---|---|---|
| NVIDIA Jetson AGX Orin 64GB | 最高 275 TOPS，主要为 INT8 sparse 口径；64GB LPDDR5、204.8 GB/s；15–60W | 丰富 CSI、PCIe、CAN 和机器人 I/O；Jetson 普通版不是 ASIL/SIL 安全部件 | CUDA/TensorRT、Isaac ROS/Sim、ROS 2 生态成熟；量产和机器人部署经验丰富 | 稀疏峰值不可与 dense 直接比较；是完整 SoM；普通 Jetson 不提供完整功能安全认证基础 |
| NVIDIA Jetson AGX Thor | 最高 2070 FP4 sparse TFLOPS，约 1035 dense FP4；128GB LPDDR5X、273 GB/s；40–130W | 多相机、CAN 和高速 I/O；Jetson T5000 本身不等同于 DRIVE/IGX 安全产品线 | 高性能 physical AI 与生成式机器人生态基线，模型容量大 | 功耗较高；FP4/FP8/INT8 口径复杂；安全能力不能从 DRIVE/IGX 外推到 Jetson |
| TI TDA4VH-Q1 | 最高 32 TOPS、8-bit 口径；LPDDR4-4266、inline ECC，理论最高约 68 GB/s | 隔离 MCU 域、安全岛、R5F lockstep、ECC 和诊断库；器件可用于最高 ASIL D/SIL 3 系统设计 | 功能安全资料、实时控制、确定性 I/O 和汽车量产体系完整 | AI 峰值远低于 Thor；软件和模型生态不以大模型为中心；“支持 ASIL D/SIL 3”不等于整机自动获得认证 |
| G2 建议方案 | 算力依据目标 VLM/VLA 与传感器数量冻结；卡载 ECC 内存；热设计覆盖持续负载 | 独立 safety controller、watchdog、端到端 ECC/parity、故障包含区、冗余侧带 | 保持 NPU 为外挂卡，可与不同 Host/安全控制器组合；计算域和安全域职责清晰 | 当前尚无安全流程、Safety Manual、FMEDA、诊断覆盖率、故障注入和认证证据；该阶段不能仅靠 RTL 功能增强实现 |

### 6.3 安全架构增量

G2 至少增加以下能力：

- 独立 safety controller 或 safety island，采用 lockstep 或等效失效检测；
- safety controller 拥有独立时钟监测、看门狗、复位和断电隔离能力；
- local SRAM、外部内存、descriptor、NoC/D2D 数据和关键状态采用 ECC/parity/CRC；
- DMA/IOMMU/window 对地址越界、权限和 transaction identity 做硬件检查；
- 启动和周期 MBIST、必要的 LBIST、ECC scrub 与错误注入；
- 温度、电源轨、时钟、链路和任务 deadline 监测；
- fatal 错误停止新任务并进入定义的安全状态，不依赖普通 Host 软件及时响应；
- 安全日志具备时间戳、错误源、影响范围和不可静默覆盖的首错信息；
- secure boot、签名固件、anti-rollback 和 debug 生命周期控制。

是否以 ISO 26262、IEC 61508 或其他标准为目标由产品场景决定。选择标准后，安全需求、诊断覆盖和开发流程必须先于 RTL 完成冻结。

### 6.4 G2 退出门槛

1. 热稳态持续推理与实时任务同时运行，满足 P99.999 延迟目标；
2. 单 bit、多 bit、链路 CRC、DMA timeout、时钟/电源异常均完成故障注入；
3. 给出故障检测时间、隔离时间、任务影响范围和安全状态到达时间；
4. 单 compute tile、单内存通道或单 D2D 链路故障不会导致全系统无界阻塞；
5. 建立 Safety Manual、FMEDA 输入、诊断库、traceability 和软件安全要求；
6. 性能模式、降级模式和安全停机模式均有端到端验证。

## 7. 跨代可延续性设计

### 7.1 必须从 G0 保持稳定的契约

| 契约 | G0 | G1/G2 延伸方式 |
|---|---|---|
| Descriptor version | 固定版本字段和长度 | 新增 opcode/字段时保持旧版本可拒绝、可识别，不静默误解 |
| Capability discovery | 报告格式、阵列、SRAM、DMA 能力 | 扩展 tile、memory、D2D、安全特性位图 |
| Command identity | core 内唯一 tag | 扩展为 card/tile/queue/tag 分层身份 |
| Address space | local SRAM offset 与 system address 分离 | 增加 card memory、peer memory 和受保护 window |
| Completion/error | 成功/失败、source/code/address | 增加 tile/card/link/safety 域信息 |
| Event visibility | 明确 producer/consumer 可见性 | 扩展为跨 tile、跨卡 event 和 fence |
| PMU | 计算、DMA、SRAM、延迟 | 增加 memory controller、D2D、热、电源和安全计数 |
| Reset/drain | 停止提交、排空、复位 | 扩展为 tile、card、link 和 safety domain 独立复位 |

### 7.2 不应固化为跨代约束的实现细节

- local SRAM 固定为 8 banks 或固定 bank 位选；
- 所有代际都使用 256-bit 数据口；
- 单一 scheduler 直接管理所有计算阵列；
- 所有格式都要求同一个阵列规模；
- peer 访问必须表现为普通 load/store；
- Host 与卡间必须硬件 cache coherent；
- D2D 必须使用某个尚未选定的标准协议。

接口应表达能力和语义，不把当前微架构数量包装成永久 ABI。

## 8. 软件栈路线

硬件能否成为产品主要取决于软件闭环。建议按以下顺序建设：

### G0

- 模型导入与量化；
- 算子支持矩阵和 CPU fallback 报告；
- tiling、local SRAM 分配和 DMA descriptor 生成；
- runtime、驱动、命令提交、completion 和性能计数；
- 独立数值金锚与端到端模型回归。

### G1

- 卡载内存 allocator；
- 多 tile 调度、权重/KV 驻留和模型切分；
- P2P DMA、拓扑发现和 collective library；
- 多卡容器、设备监控、固件升级和故障恢复；
- Host 与卡内 profiler 的统一时间线。

### G2

- 实时优先级、deadline 和降级策略；
- safety controller 固件与高性能 runtime 分离；
- 签名升级、anti-rollback、故障日志和诊断服务；
- 安全要求到测试结果的可追溯性。

## 9. 工作负载与验收矩阵

### 9.1 验收基线必须冻结的条件

每次正式验收必须记录：

- 模型名称、版本、输入尺寸、batch、序列长度和 KV 长度；
- 权重/激活/KV 精度、量化方案、稀疏规则和允许的精度损失；
- compiler、runtime、固件和 RTL/芯片版本；
- NPU、CPU、VPU、内存和 D2D 时钟；
- 内存类型、位宽、速率、容量和实测空载/并发带宽；
- 功耗模式、环境温度、散热器和测量边界；
- 性能测量的起止事件、预热次数、样本数和分位数。

条件未冻结的跑分只用于探索，不能作为 SKU 达标证据。

### 9.2 代际强制 KPI

表内每一类 KPI 都是产品定级必审项。当前数值是路线图候选阈值；模型绝对性能、功耗和 G1/G2 算力需在产品需求冻结时形成唯一批准值。批准值缺失或任一批准值未达标时，该 SKU 不得完成产品定级。

| KPI | G0 标准版 | G0 Pro | G1 标准版 / Pro | G2 标准版 / Pro |
|---|---|---|---|---|
| 峰值算力 | 2 cores×500 MHz：0.512 TMAC/s；按 2 ops/MAC 为 1.024 TOPS | 4 cores×500 MHz：1.024 TMAC/s；按 2 ops/MAC 为 2.048 TOPS | 候选 32–64 / 64–128 dense INT8 TOPS，冻结前不得宣传 | 由 VLM/VLA、传感器和功耗反推，分别报告 MAC/s、operations/s、dense/sparse 与精度 |
| 大 GEMM 持续效率 | 冻结对齐形状下不低于理论峰值 60% | 同标准版，且不因四核仲裁下降 | 建议不低于 65%，同时报告 tile 间负载均衡 | 在实时并发负载下报告，不以离线 GEMM 单项替代 |
| 外部内存持续效率 | LPDDR4x 长 burst DMA 有效带宽不低于理论峰值 70% | 同标准版，并证明带宽配置可支撑四核 | 卡载内存和 Host PCIe 分别不低于批准目标 | 正常与降级模式分别验收，并开启 ECC |
| 算子覆盖率 | 冻结模型中 NPU/VPU 执行时间占比候选≥90% | 不低于标准版 | 目标模型关键路径不得因 Host fallback 破坏多卡收益 | 实时关键路径不得依赖非确定 Host fallback |
| 视觉基线 | YOLOv8n 640×640 INT8、batch=1 候选≥30 FPS | 同条件候选≥60 FPS | 冻结多流/生成模型并发目标 | 按相机数量和感知 deadline 冻结 |
| Transformer/LLM | 名义0.5B–2B；原两FP8模型的批准指标以总体架构12.8为准，Qwen3.5-2B新增身份的精度、容量及服务目标待评审；本表历史W4/roofline候选不覆盖该新增模型 | 至少一个批准的端到端工作负载达到标准版的 1.3× | 7B/14B W4；decode 候选达到实测内存 roofline 的 65%，prefill 候选达到计算 roofline 的 50% | VLM/VLA 按感知到动作 deadline 和并发传感器负载验收 |
| D2D/P2P | 不适用 | 不适用 | 单/双向有效载荷建议≥物理 payload 上限 70%；计算并发时单独报告下降比例 | 增加链路故障、降级拓扑和冗余路径验收 |
| 尾延迟 | 报告 P50/P90/P99，并冻结单流绝对上限 | 多核并发下仍满足同类任务上限 | 报告 P50/P99 及首 token 延迟，多卡拥塞纳入测量 | 实时任务按 P99.999 和 deadline miss 验收 |
| 热稳定 | 连续目标负载 30 分钟后吞吐不低于稳定前 90% | 同标准版，且不得依靠超频定义 Pro | 卡级热稳态、墙插功耗、噪声和降频曲线全部验收 | 最坏环境温度、降级模式和安全停机同时验收 |
| 稳定性 | 24 小时目标负载无未恢复错误或死锁 | 同标准版 | 加入多卡、链路 reset、单卡故障和固件恢复 | 加入 ECC/CRC、时钟、电源、timeout 和安全状态故障注入 |

YOLO 和 roofline 比例是当前建议基线，正式立项时可调整，但必须在流片或产品冻结前形成唯一批准值。

### 9.3 Pro 版本定级规则

Pro 不是简单增加峰值 TOPS。除满足标准版全部正确性、软件和可靠性门槛外，还必须满足：

- **G0 Pro：** 计算受限基准相对标准版建议≥1.6×，至少一个批准的端到端工作负载建议≥1.3×；若 LPDDR4x 限制使主要负载几乎无提升，应先增强内存而不是发布 Pro；
- **G1 Pro：** 在算力、模型容量、多卡带宽或并发数至少一项形成明确档差，并让优先模型端到端指标建议≥1.3×；
- **G2 Pro：** 可以由性能、传感器并发、冗余、故障降级能力或安全可用性形成档差，但必须用可测 KPI 表达，不能只增加未被软件利用的硬件；
- Pro 在相同兼容模式下不得比标准版拥有更差的数值语义、错误可见性和软件兼容性。

### 9.4 工作负载集合

| 代际 | 功能工作负载 | 性能工作负载 | 必测系统指标 |
|---|---|---|---|
| G0 | 视觉、轻量语音、名义0.5B–2B Transformer；当前批准身份为原两FP8、Qwen3.5-2B及YOLO11n/s，新增2B模型的格式和门槛待评审 | 单流低延迟、持续视觉、多算子混合 | 持续 TOPS、阵列利用率、LPDDR4x 有效带宽、bank conflict、P99 latency、TOPS/W |
| G1 | 7B/14B LLM、视觉生成、多模型并发 | prefill/decode、KV 压力、双卡模型切分 | token/s、首 token 延迟、卡载内存驻留、P2P 带宽、collective 效率、热稳态功耗 |
| G2 | 多相机感知、VLM/VLA、规划与实时控制协同 | 计算拥塞、传感器突发、降级运行 | P99.999 latency、deadline miss、故障检测/隔离时间、ECC/CRC 注入、持续温度和降频 |

任何阶段都同时报告峰值、持续 kernel 性能和完整任务性能。不得用 GEMM 峰值替代模型端到端结果。

## 10. 成本分析

### 10.1 成本口径

成本比较采用以下四类口径，不允许互相替代：

- **公开售价/采购价：** 官方 MSRP、官方商店或授权分销商的公开价格，记录产品形态、数量档位、币种、税费和查询时间；
- **BOM：** 芯片、内存、存储、PMIC、PCB、连接器、散热和结构件等直接物料，不含研发摊销；
- **单位制造成本（COGS）：** BOM 加封装、测试、组装、制造损耗及其他直接生产成本；
- **完全摊销成本：** 单位制造成本加掩膜、IP、EDA、流片、验证、软件、安全认证等 NRE 按实际出货量摊销，并计入质保和供应链损耗。

竞品售价只能用于采购预算和市场定位，不能反推出竞品 BOM、晶圆成本、毛利率或单位制造成本。没有官方、授权分销或可审计拆解来源时统一写 `TBD`，不采用论坛传言、二手价格或按零售价乘固定比例的估算。

我方 G0/G1/G2 ASIC 成本模型统一以 **TSMC 12nm** 为基准。具体 12nm 工艺版本、晶圆尺寸、晶圆报价、MPW/量产条件、掩膜费用和良率曲线均为 `TBD`，需以 TSMC、封装厂和测试厂正式报价为准；路线图不隐含迁移先进工艺来满足性能或成本目标。

### 10.2 竞品公开价格与成本

| 代际 | 竞品 | 可公开比较的价格 | 公开制造成本/COGS | 比较边界 |
|---|---|---:|---:|---|
| G0 | Rockchip RK3568 | TBD | TBD | 未找到可核验的官方或授权分销价；需区分裸芯片、核心板、开发板和终端整机 |
| G0 | NXP i.MX 8M Plus | MIMX8ML8CVNKZAB：USD 57.82/片（1 片档）；8MPLUSLPD4-EVK：USD 842.04/套 | TBD | 2026-10-26 授权分销页面价格，税费、关税和运费另计；开发板价格不代表 SoC 成本 |
| G0 | Rockchip RK3399Pro | TBD | TBD | 官方产品页无报价；产品代际较早，库存和渠道波动大 |
| G1 | Apple Mac mini M4/M4 Pro | 美国发布 MSRP：M4 16GB USD 599；M4 Pro 24GB USD 1,399 | TBD | 2024 发布价；比较对象是完整零售整机，包含 CPU/GPU/NPU、内存、存储、机箱、软件与渠道 |
| G1 | NVIDIA DGX Spark | Founders Edition MSRP：USD 4,699 | TBD | NVIDIA 公告的 2026-02-23 调整价格；比较对象是完整桌面 AI 系统，不是裸芯片或单卡 |
| G1 | Axelera Metis Embedded 113m | TBD | TBD | 官方要求联系销售；需分别询价 2GB/8GB 版本，M.2 卡售价也不等于芯片制造成本 |
| G2 | NVIDIA Jetson AGX Orin 64GB | 模组 USD 2,999（1KU+）；开发套件 USD 3,499 | TBD | 2026-10-26 NVIDIA FAQ 公开价；模组和开发套件边界不同，公开售价不能推导模组 COGS |
| G2 | NVIDIA Jetson AGX Thor | T5000 模组 USD 4,999（1KU+）；开发套件 USD 5,499 | TBD | 2026-10-26 NVIDIA FAQ 公开价；安全版本能力不可从普通开发套件价格推断 |
| G2 | TI TDA4VH-Q1 | TDA4VH88：USD 199.289（1–99），USD 171.80（1,000+）；J784S4XEVM：USD 1,758.90 | TBD | 2026-10-26 TI 页面价格；需锁定料号、封装、温度等级和库存，功能安全与认证成本通常不含在芯片标价中 |

表中价格只在来源、型号和交易条件可复核时填写；所有竞品真实晶圆、封装、测试、良率、BOM 和 NRE 摊销若无公开证据则保持 `TBD`。

### 10.3 TSMC 12nm ASIC 成本模型

我方 ASIC 不在面积和良率未知时填写单颗成本。签核面积和正式报价到位后按同一模型计算：

```text
GrossDiesPerWafer ≈ π × (WaferDiameter / 2)² / DieArea
                    - π × WaferDiameter / sqrt(2 × DieArea)
GoodDiesPerWafer   = GrossDiesPerWafer × CompositeYield
BareDieCost        = WaferPrice / GoodDiesPerWafer
PackagedASICCost   = BareDieCost + PackageSubstrate + Assembly + FinalTest
ProductCOGS        = Σ(PackagedASIC) + Memory + Storage + PMIC + PCB
                     + Connectors + Cooling + Enclosure + ManufacturingTest
FullyLoadedCost    = ProductCOGS + NRE / QualifiedShipmentVolume
                     + WarrantyReserve + SupplyChainLoss
```

`CompositeYield` 至少拆分为晶圆良率、SRAM/逻辑良率、封装组装良率和最终测试良率。面积、晶圆边缘损失、冗余修复和已知良率模型必须由 P&R、SRAM macro 和供应商数据更新，不用理论 die 数替代量产 good die 数。

TSMC 12nm 成本输入表：

| 输入项 | G0 Standard / Pro | G1 Standard / Pro | G2 Standard / Pro | 当前状态 |
|---|---|---|---|---|
| 工艺与晶圆报价 | TSMC 12nm | TSMC 12nm | TSMC 12nm | 具体工艺版本、晶圆尺寸和价格 TBD |
| 签核 die area | 2/4-core SoC 全芯片面积 | compute ASIC 数量与单 die 面积 | compute ASIC、安全域及冗余面积 | TBD；综合面积不能替代 P&R 签核面积 |
| SRAM macro | local SRAM、CPU/VPU SRAM | tile SRAM、管理域 SRAM | ECC SRAM、安全域 SRAM | macro 类型、容量、冗余和报价 TBD |
| PHY/IP NRE | LPDDR4x、CPU、基础 I/O | PCIe、D2D/SerDes、内存 PHY、CPU | 安全 CPU、PCIe/D2D、内存 PHY、安全 IP | 授权、版税和验证费用 TBD |
| 良率 | 逻辑、SRAM、封装、测试 | 单 die 与多芯片组装良率 | 安全冗余和更严格测试后的良率 | TBD；按 Standard/Pro 分别建模 |
| 封装与测试 | SoC I/O、LPDDR4x 接口、量产测试 | 高速 I/O、卡载内存和散热约束 | ECC、高可靠筛选和故障诊断测试 | 封装形式、测试秒数和报价 TBD |
| 出货量 | Standard/Pro 各自生命周期数量 | 单卡与多卡配置数量 | 工业/机器人生命周期数量 | TBD；用于 NRE 摊销和阶梯报价 |

### 10.4 各代产品成本构成

| 代际 | 单位成本对象 | 主要直接成本 | Pro 增量 | 主要成本风险 |
|---|---|---|---|---|
| G0 完整 SoC | 芯片或含基础内存/存储的最小系统，二者必须分开报价 | 12nm SoC die、封装测试、LPDDR4x、存储、PMIC、PCB、基础外设与散热 | 4-core die 面积、可能更高容量/位宽 LPDDR4x、更高规格封装和供电 | Pro die 变大导致 good dies/wafer 和良率下降；内存未增强时增加计算成本却无端到端收益 |
| G1 算力卡 | 可被 Host 枚举并独立运行固件的单张卡 | 一个或多个 12nm compute ASIC、卡载内存、管理 CPU、PCIe、D2D/SerDes、PCB、连接器和主动/被动散热 | 更多 compute ASIC/tile、更大内存、更宽 D2D、更强供电与散热 | 12nm 下大规模单 die 的面积和功耗可能迫使多芯片方案，增加封装、板级互联和良率组合成本 |
| G2 具身智能卡 | 含安全域、可靠内存和诊断能力的单张卡 | G1 基础成本加 safety controller、ECC 内存、冗余链路、电源/时钟监测、高可靠器件和扩展测试 | 更高冗余、传感器并发、故障降级能力和环境等级 | 安全流程、诊断覆盖、故障注入、长周期供货和认证 NRE 可能高于单纯增加 TOPS 的成本 |

G0 Standard 与 Pro、G1/G2 Standard 与 Pro 必须分别维护 costed BOM，不能用核数或 TOPS 比例线性缩放成本。内存、封装、PCB、散热和测试均可能形成非线性台阶。

### 10.5 成本定级指标

| 指标 | G0 | G1 | G2 |
|---|---|---|---|
| 单位制造成本上限 | Standard/Pro 分别 TBD | 单卡 Standard/Pro 分别 TBD | 单卡 Standard/Pro 分别 TBD |
| 完全摊销成本上限 | 按批准生命周期出货量 TBD | 按卡数量和多卡配置销量 TBD | 加入安全与长期供货 NRE 后 TBD |
| 成本效率 | 元/持续 INT8 TOPS、元/FPS、元/token/s | 元/持续 TOPS、元/GB 可驻留模型、元/token/s | 元/并发传感器、元/满足 deadline 的任务流 |
| 内存成本 | LPDDR4x 容量与带宽的增量 BOM | 卡载内存容量、带宽和 ECC 增量 | ECC、冗余与高可靠等级增量 |
| 量产敏感性 | 晶圆价、die area、良率、封装和数量档位 | ASIC 数量、内存、PCB 层数、SerDes 和散热 | G1 项加诊断测试时间、认证和质保储备 |

每个 SKU 在立项、流片、工程样片和量产前各更新一次 best/base/worst 三档模型。产品定级必须同时满足性能、功耗和成本批准值；低于性能门槛的低成本配置或超过成本门槛的高性能配置都不能自动放行。

### 10.6 TSMC 12nm 对路线图的约束

- G0 的 CPU、NPU、VPU、SRAM、LPDDR4x PHY 和基础 I/O 需在同一 12nm SoC 面积、封装引脚、功耗和成本预算内闭环；
- G1/G2 不假定依靠工艺缩小获得密度和能效，优先用 compute tile、多 die/多 ASIC、卡载内存和本地 D2D 扩展；
- 多 die/多 ASIC 只有在良率收益高于封装、SerDes、PCB、供电和散热增量时才具备成本优势；
- Standard/Pro 的 12nm die 是否共用同一掩膜版，需比较熔丝/屏蔽 SKU 的面积浪费与两套掩膜、验证和库存成本；
- G1/G2 候选算力若无法在 12nm 的卡级功耗、散热和成本上闭环，应下调单卡目标或增加卡数，不在成本表中隐含先进工艺替代方案。

## 11. 实施优先级与里程碑

### M0：G0 架构冻结

- 将 2 cores×500 MHz 和 4 cores×500 MHz 定义为标准版/Pro；
- 冻结 1T/2T 的 TMAC/s 与 operations/s 对外计数口径；
- 冻结 LPDDR4x 位宽、速率、容量和效率目标；
- 删除 G0 shared LLC 依赖；
- 冻结应用 CPU、本地控制 CPU 和最小 VPU 能力；
- 统一产品配置、模块默认配置和 RTL 发射配置；
- 冻结 command/completion/capability 基础格式；
- 冻结 TSMC 12nm 具体工艺候选、die area 预算、封装候选、目标出货量和 Standard/Pro 成本上限。

### M1：单 core 计算子系统闭环

- core 顶层、local SRAM、local interconnect 和 adapter；
- DMA→GEMM→VPU→DMA；
- 错误、IRQ、PMU、reset/drain；
- 功能和压力回归；
- 用 SRAM macro 与预综合/P&R 早期结果更新 die area、功耗和 TSMC 12nm 成本敏感性模型。

### M2：G0 完整 SoC 与产品验证

- 集成应用 CPU、本地控制 CPU、LPDDR4x 控制器、基础外设和启动链；
- 2/4-core 性能、PPA 和热功耗；
- 标准版/Pro 的代表模型端到端与 KPI 定级；
- 软件安装、模型部署和 profiler；
- 取得晶圆、封装、测试、LPDDR4x、PCB 和量产数量档位报价，形成 Standard/Pro costed BOM；
- 确认是否进入 ASIC、FPGA 或授权 IP 路线。

### M3：G1 compute tile 与算力卡

- 多阵列 tile、本地 scheduler 和 tile router；
- 卡内管理 CPU、固件和 command processor；
- 卡载内存控制器；
- Host PCIe card endpoint；
- 7B/14B 单卡闭环；
- 比较 12nm 单大 die、多 die 与多 ASIC 卡方案的良率、封装、板级互联、散热和单位成本。

### M4：G1 D2D

- 双卡直连、P2P DMA、event 和错误恢复；
- 拓扑感知 runtime；
- 多卡模型切分和 collective 实测。

### M5：G2 安全架构

- safety controller、故障包含域和独立复位；
- ECC/parity/CRC 与诊断；
- deadline/QoS 和安全状态；
- 安全流程、文档和故障注入；
- 将安全 IP、冗余硬件、扩展测试、认证和长期供货计入 G2 Standard/Pro 完全摊销成本。

## 12. 关键风险与待决策项

| 风险/决策 | 影响 | 建议决策时点 |
|---|---|---|
| VPU 算子和吞吐未冻结 | 端到端模型可能大量回退 Host | G0 架构冻结前 |
| 500 MHz 与 SRAM 组织无 PPA | 峰值算力、带宽和功耗假设可能同时失效 | M1 前完成 macro 和预综合评估 |
| TSMC 12nm 工艺版本、面积、晶圆价和良率未冻结 | G0 单颗成本及 G1/G2 单卡功耗、芯片数量和封装方案无法闭环 | M0 建立预算，M1 用 PPA 更新，M2 取得供应商报价 |
| 当前 core 线性复制扩展性差 | G1 面积、功耗和互联不可控 | M2 后、M3 前冻结 compute tile |
| 卡载内存类型未选 | 7B/14B 容量、带宽和功耗无法闭环 | M3 前 |
| D2D 语义过重 | coherence 和协议验证拖慢产品 | G1 首版限定为 P2P DMA+event |
| D2D 语义过轻 | 多卡 tensor 并行被 Host 路径限制 | M4 前以实际模型通信量校验 |
| 功能安全后补 | reset、错误、CDC、trace 需大规模返工 | G0 预留错误和独立复位，G2 正式立项前冻结标准 |
| 软件栈投入不足 | 硬件峰值无法转化为模型性能 | 与 M1 同步建设，不等待 RTL 完成 |

## 13. 总结

- G0 是完整端侧 SoC，标准版为 2 cores×500 MHz 的 1T SKU，Pro 为 4 cores×500 MHz 的 2T SKU；
- 1T/2T 的 TMAC/s 与按 2 ops/MAC 换算 TOPS 口径尚待冻结，对外比较必须同时给出原始 MAC/s；
- G0 使用显式 SRAM 和 LPDDR4x，是否达到代际目标由端到端 KPI、热稳定和软件覆盖率共同决定；
- Qwen2.5-7B 应从 G0 首个基线移动到 G1 家庭算力卡阶段；
- G1/G2 是带管理 CPU 的外挂算力卡，由外部强 Host 负责多卡全局编排；
- G1 的核心不是继续复制 core，而是建立 compute tile、卡载内存、Host 卡边界和非网络 D2D；
- G2 的核心不是单纯提高 TOPS，而是独立安全域、确定性、端到端完整性和故障可控；
- 我方三代 ASIC 均以 TSMC 12nm 建模，晶圆、良率、封装、测试、BOM 和 NRE 无正式来源时保持 TBD；
- 数据格式、descriptor、身份、地址空间、completion、PMU 和 reset 域需要从 G0 起按可扩展契约设计。

## 附录 A：公开资料来源

以下资料仅用于产品对标，访问时间为 2026-10。厂商规格可能更新，产品决策前需重新核验。

### A.1 端侧产品

- [Rockchip RK3568 安防方案](https://www.rock-chips.com/a/cn/news/rockchip/2021/0114/1371.html)
- [Rockchip RK3568 Brief Datasheet](https://www.rock-chips.com/uploads/pdf/2022.8.26/192/RK3568%20Brief%20Datasheet.pdf)
- [Rockchip RK3399Pro 产品页](https://www.rock-chips.com/a/en/products/RK33_Series/2018/0130/874.html)
- [NXP i.MX 8M Plus 产品页](https://www.nxp.com/products/i.MX8MPLUS)
- [NXP i.MX 8M Plus Datasheet](https://www.nxp.com/docs/en/preview/PREVIEW_IMX8MPIEC.pdf)
- [NXP eIQ 培训资料](https://www.nxp.com/docs/en/training-reference-material/TP-EVB_eIQ_presentation.pdf)

### A.2 家庭算力盒与独立加速卡

- [Apple M4 芯片](https://www.apple.com/newsroom/2024/05/apple-introduces-m4-chip/)
- [Apple Mac mini M4 发布](https://www.apple.com/newsroom/2024/10/apples-new-mac-mini-is-more-mighty-more-mini-and-built-for-apple-intelligence/)
- [NVIDIA DGX Spark 产品页](https://www.nvidia.com/en-us/products/workstations/dgx-spark/)
- [NVIDIA DGX Spark 硬件指南](https://docs.nvidia.com/dgx/dgx-spark/hardware.html)
- [Axelera Metis Embedded 113m Datasheet](https://docs.axelera.ai/docs/hardware/embedded-113m/datasheet/)
- [Axelera Voyager SDK](https://docs.axelera.ai/sdk/)
- [UCIe 3.0 规格发布说明](https://www.uciexpress.org/post/ucie-3-0-specification-redefining-chiplet-interconnects)

### A.3 具身智能与功能安全

- [NVIDIA Jetson AGX Orin 产品规格](https://www.nvidia.com/en-us/autonomous-machines/embedded-systems/jetson-orin/)
- [NVIDIA Jetson Thor 发布与规格](https://developer.nvidia.com/blog/introducing-nvidia-jetson-thor-the-ultimate-platform-for-physical-ai/)
- [NVIDIA Jetson 产品生命周期](https://developer.nvidia.com/embedded/lifecycle)
- [TI TDA4VH-Q1 产品页](https://www.ti.com/product/TDA4VH-Q1)
- [TI TDA4VH-Q1 系列 Datasheet](https://www.ti.com/lit/ds/symlink/tda4ah-q1.pdf)
- [TI J784S4 Safety Diagnostic Library](https://software-dl.ti.com/jacinto7/esd/processor-sdk-rtos-j784s4/11_00_00_06/exports/docs/sdl/sdl_docs/userguide/j784s4/diagnostic_mappings.html)

### A.4 公开价格来源

- [NXP MIMX8ML8CVNKZAB，Mouser](https://www.mouser.com/en/ProductDetail/NXP-Semiconductors/MIMX8ML8CVNKZAB)
- [NXP 8MPLUSLPD4-EVK，DigiKey](https://www.digikey.com/en/products/detail/nxp-usa-inc/8MPLUSLPD4-EVK/13897867)
- [Apple Mac mini M4/M4 Pro 发布价格](https://www.apple.com/newsroom/2024/10/apples-new-mac-mini-is-more-mighty-more-mini-and-built-for-apple-intelligence/)
- [NVIDIA DGX Spark 2026 价格调整公告](https://forums.developer.nvidia.com/t/2-23-2026-price-change-announcement/361713)
- [Axelera AI accelerators 销售入口](https://axelera.ai/ai-accelerators)
- [NVIDIA Jetson 模组与开发套件价格 FAQ](https://developer.nvidia.com/embedded/faq)
- [TI TDA4VH88TGAALYRQ1 价格](https://www.ti.com/product/TDA4VH-Q1/part-details/TDA4VH88TGAALYRQ1)
- [TI J784S4XEVM 价格](https://www.ti.com/tool/J784S4XEVM)

