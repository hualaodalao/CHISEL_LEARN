# Hive NPU Core 总体架构

文档版本：`v0.7.0-draft`

修订日期：`2026-10-26`

> 本文定义 NPU core 的架构方案、系统边界和关键取舍，用于设计评审。所有未实现能力均为目标或待确认项，不构成 RTL 交付承诺。协议时序、寄存器编码和验证用例在后续硬件规格中细化。

## 版本控制

| 版本号 | 日期 | 修改内容 |
|---|---|---|
| v0.7.0-draft | 2026-10-26 | G0 工作负载基线收敛为 0.5B–1.5B W4 模型和 YOLO 等视觉模型；两核 1T 定义为 G0，后续四核 2T 定义为 G0 Pro |
| v0.6.0-draft | 2026-10-26 | 将 VPU 第 12.7 节改为首版性能、能力、接口与验收要求；设定每 core 256-bit、500 MHz、FP32 8 / FP16 16 GFLOPS 草案指标；产品对比、扩展档位、工作负载和带宽推导迁至独立性能分析文档 |
| v0.5.0-draft | 2026-10-26 | 补充 INT4/MXFP4 稠密峰值与 W4A8 区别；增加外部 FP32/FP16 VPU 的职责、公开产品配比参考、候选算力和算子/带宽约束，VPU 配置仍待外部实现确认 |
| v0.4.0-draft | 2026-10-26 | 首发目标调整为 2 cores × 500 MHz、LPDDR4x；定义 INT8/FP8 1T（两核）与 2T（四核）SKU，重算峰值算力、decode 带宽、在途数据和 prefill 理想时间；内存位宽、速率与持续性能仍待确认 |
| v0.3.0-draft | 2026-10-26 | 移除共享末级缓存及相关架构依赖；采用显式 SRAM 管理和 DMA 预取/双缓冲；系统互联直接连接 DRAM controller，原子同步独立实现，Host 一致性仍由平台或软件承担 |
| v0.2.0-draft | 2026-10-26 | 按总体架构、计算、存储、互联、执行模型、系统集成和非功能要求重组；正文使用通用功能名称；补充资源所有权、可见性、流控、可靠性和验证边界；将容量与性能推导集中到性能章 |
| v0.1.0-draft | 2026-10-26 | 首版系统架构、带宽预算、通信方式及集成前置项 |

## 1. 文档定位与阅读指南

### 1.1 目的与范围

本文面向架构、RTL、软件和系统集成人员，回答：

- 单个 NPU core 包含哪些组件，各自承担什么责任；
- 命令、数据和完成事件如何在组件之间流动；
- 计算、存储和互联之间有哪些带宽与容量约束；
- 多核及主控如何通信，谁承担顺序和一致性责任；
- 哪些能力已经存在，哪些需要新建、优化或确认。

范围以单 core 为主，多核 cluster、系统互联、同步单元和 DRAM 作为外部集成上下文。CPU/VPU 由外部仓库提供，本文不定义其微架构，也不重复 GEMM/DMA 的完整内部规格。

### 1.2 状态与规范层次

| 标记 | 含义 |
|---|---|
| **现状** | 依据当前源码或模块规格可核对的事实；不代表系统已完成集成验证 |
| **目标** | 本初稿建议的架构行为或资源配置，尚待评审确认 |
| **待确认** | 缺少决策或证据，不能作为保证值 |

正文描述目标架构，并显式标出当前实现差距；第 13 章汇总差距和待决策事项，第 14 章定义后续验证入口。本文不分配功能 Feature ID，不以架构草案替代模块规格。

### 1.3 术语与名称

除文档标题中的项目名外，正文、表格与框图统一使用功能名称：

| 术语 | 含义 |
|---|---|
| NPU core | 单个独立调度、计算和本地存储单元 |
| NPU cluster | 共享系统互联、同步单元和 DRAM 的多个 NPU core |
| GEMM | 矩阵计算单元，包括私有缓冲、阵列、输出处理和本地读写端口 |
| scheduler | 命令派发、依赖跟踪、完成和事件管理单元 |
| DMA | 通用多维数据搬运单元；与 GEMM 自带的本地读写端口区分 |
| local SRAM | core 内软件显式管理的共享 scratchpad，非硬件透明缓存 |
| local interconnect | 多发起方到 local SRAM banks 的互联 |
| system interconnect | core、peer window、同步单元、主控和 DRAM 之间的互联 |
| synchronization unit | 提供共享同步状态和原子操作的独立单元，不承载 tensor 数据 |
| Host / local CPU | Host 是系统任务提交方；local CPU 是 core 的本地控制处理器，两者角色不同 |
| MAC | 一次乘加贡献；换算为运算次数时按 2 operations/MAC，不与独立输出数量混用 |

实际源码文件名仅在附录 A 保留，以支持实现追溯；它们不是本文的架构组件名称。

### 1.4 文档组织

| 评审问题 | 阅读位置 |
|---|---|
| 做什么、为何这样做 | 第 2–3 章：目标、总体架构与边界 |
| 数据怎样计算和搬运 | 第 4–6 章：计算、存储和互联 |
| 命令如何执行并对软件可见 | 第 7–9 章：执行模型、地址与一致性、系统集成 |
| 生命周期和非功能要求 | 第 10–11 章：时钟电源、可靠性与观测 |
| 带宽容量是否闭环 | 第 12 章：性能模型 |
| 哪些尚未确定、怎样验收 | 第 13–14 章：取舍、实现差距与验证 |

组织方式参考 NVIDIA 官方 GPU 架构文章的由总览到子系统叙述，以及 NVDLA 架构/集成文档对接口和生命周期的分层。参考依据与不适用内容见附录 B。

## 2. 设计目标与系统假设

### 2.1 设计目标

**目标：** 面向端侧推理，建立 DMA 搬运、GEMM 计算、VPU 后处理和 scheduler 协同的执行闭环。数据驻留、搬运和复用由 runtime/scheduler 显式管理 local SRAM，通过 DMA 预取和双缓冲隐藏部分外部访问延迟，系统侧支持多核共享和异步命令提交。

显式管理避免透明缓存的命中/缺失、替换和回写路径，但不消除 bank 冲突、互联排队或 DRAM 延迟变化；这些仍由资源规划、流控和性能验证约束。

G0 容量与性能评估采用 0.5B–1.5B W4 模型、YOLO 等视觉模型及其典型 batch，至少分别评估：

- 语言模型 decode：M 很小，重点是持续权重带宽、搬运延迟和启动开销；
- 语言模型 prefill：M 较大，重点是权重复用、阵列利用率和 SRAM 容量；
- 视觉模型：覆盖 convolution/GEMM 主干、检测头和后处理，重点是多尺度 tensor、算子切换及端到端时延；
- attention/VPU：单独计入 KV、向量处理与中间 tensor 流量，不能用 GEMM 峰值替代。

上述类型定义 G0 的产品评估范围，不构成单一模型支持承诺；具体模型版本、输入尺寸、batch、量化布局和验收阈值仍需锁定。

### 2.2 首发配置与资源规划

G0 目标为 **2 cores × 500 MHz、1T SKU**；后续 G0 Pro 为 **4 cores × 500 MHz、2T SKU**。GEMM 因频率与功耗约束先采用 500 MHz，不把提高频率作为产品算力达标的前提；具体时序裕量、工作电压和功耗仍需物理实现验证。

| 项目 | 当前基线或方案 | 属性 |
|---|---|---|
| G0 core 数量 / GEMM clock | 2 cores × 500 MHz | 1T 产品目标，非实测结果 |
| G0 Pro core 数量 / GEMM clock | 4 cores × 500 MHz | 后续 2T 产品目标，集成与带宽需独立验证 |
| GEMM 阵列 | 每 core T=16，256 个 PE，16-bit 物理输入 lane | 现状默认 |
| 外部 VPU | 每 core 256-bit 等效执行、500 MHz；FP32 8 / FP16 16 GFLOPS | 首版草案目标，外部实现与 PPA 待确认；见 12.7 |
| 通用 DMA | 每 core 2 engines；当前 128-bit data、40-bit address | 现状默认 |
| DMA 目标数据宽度 | 每 engine 256 bit | 目标 |
| local SRAM | 每 core 8 banks × 256 bit；512 KiB 或 1 MiB/core | 目标组织，容量待确认 |
| 每 core 外部数据接口 | AXI4，256-bit data、40-bit physical address | 目标 |
| cluster 汇聚互联 | 按 G0 两核/G0 Pro 四核负载分别预算；W4 decode 条件化有效权重需求 16/32 GB/s，其他流量与余量另计 | 设计约束，见 12.4 |
| 外部内存 | LPDDR4x；总数据位宽、速率、容量和 controller 配置待确认 | 类型已确定，带宽未冻结 |
| 同步单元 | 独立同步状态与原子接口 | 目标，容量和协议待确认 |

### 2.3 GEMM 各精度算力与 SKU 口径

每个 PE 对 INT8/FP8 做两组配对乘加，对同格式 INT4/MXFP4 做四组配对乘加；这扩展 K 维而非 N 维，不按 A/B lane 数量相乘生成交叉 MAC。每 MAC 计 2 operations、GEMM clock 为 500 MHz：

```text
MACs_per_cycle_8bit = 16 × 16 × 2 = 512
MACs_per_cycle_4bit = 16 × 16 × 4 = 1024
Peak_operations    = cores × MACs_per_cycle × 2 operations/MAC × 500 MHz
```

| 配置 | INT8/FP8 MAC 吞吐 | INT8 稠密峰值 | FP8 稠密峰值 | INT4/MXFP4 MAC 吞吐 | INT4 稠密峰值 | MXFP4 稠密峰值 | 产品定位 |
|---|---:|---:|---:|---:|---:|---:|---|
| 单 core × 500 MHz | 0.256 TMAC/s | 0.512 TOPS | 0.512 TFLOPS | 0.512 TMAC/s | 1.024 TOPS | 1.024 TFLOPS | 单核计算基准 |
| 2 cores × 500 MHz | 0.512 TMAC/s | 1.024 TOPS | 1.024 TFLOPS | 1.024 TMAC/s | 2.048 TOPS | 2.048 TFLOPS | G0，1T SKU |
| 4 cores × 500 MHz | 1.024 TMAC/s | 2.048 TOPS | 2.048 TFLOPS | 2.048 TMAC/s | 4.096 TOPS | 4.096 TFLOPS | 后续 G0 Pro，2T SKU |

本文 FP4 算力指当前支持的 **MXFP4 E2M1 × MXFP4 E2M1**，含 block scale 语义，不宣称任意非 MX FP4 通路；INT4 峰值对应 A/B 均为 INT4。INT/MX 的 W4A8 混配经 B 展开后仍跟随 8bit A，每拍 512 MAC，不能套用同格式 4bit 的翻倍峰值。scale 处理开销不另加到元素 MAC 峰值中。

G0/G0 Pro 的 1T/2T 均按 INT8/FP8 稠密 GEMM 峰值定义，不改用 INT4 或稀疏口径，也不包含 VPU。上述峰值要求各 core 同时处于有效计算阶段；装载、排空、数据搬运、bank 冲突与任务不均衡使完整任务持续算力低于峰值。4bit 格式的量化精度、scale/布局开销和有效拍占比须单独验证，峰值翻倍不承诺模型速度翻倍。未知工艺、面积、功耗和时序结果不填造数值。

## 3. 总体架构与系统边界

### 3.1 Cluster 级架构

```text
Host / SoC master -------- CSR / doorbell / completion / IRQ
          |
          v
+------------------------------------------------------------------+
|                      System interconnect                         |
|        independent core ingress + peer routing + QoS             |
+---------+----------------+-----------------+----------------+-----+
          |                |                 |                |
     +----v-----+     +----v-----+   +--------v-------+  +-----v-----+
     | NPU core0|     | NPU core1|   | Synchronization|  | DRAM      |
     |Local SRAM|     |Local SRAM|   | unit           |  | controller|
     +----------+     +----------+   +----------------+  +-----+-----+
                                                              |
                                                          LPDDR4x
```

图示为 G0 两核配置；后续 G0 Pro 四核 2T SKU 在相同系统互联增加 core2/core3 的独立入口，并重新验证聚合带宽、同步和任务划分。

**目标：** core 可独立执行，也可通过 peer SRAM window、共享 DRAM 数据和事件协同。系统互联直接路由到 DRAM controller 或 peer SRAM，不经过 NPU 内建透明缓存。原子同步由独立同步单元处理；Host 所在平台的 cache/coherence 仍属于外部集成责任。

### 3.2 单 core 控制与数据路径

```text
CONTROL
Local CPU request ----+                  VPU instructions
                      |                        |
Host ring -> fetcher -> scheduler              v
                      |                    External VPU
                      +--> GEMM adapter        ^
                      +--> DMA adapter         | dependency events
                      +------------------------+
                      ^
               done / error / events
                      |
scheduler -> completion writer / IRQ aggregator -> Host / local CPU

DATA
GEMM local read/write ---+
DMA local read/write ----+--> Local interconnect --> Local SRAM banks
VPU local load/store ----+
CPU / debug access ------+

DMA system read/write ------> AXI bridge ---> System interconnect
Command fetcher ------------> System read path
Completion writer ----------> System write path
Remote access to local SRAM -> Peer AXI bridge -> Local interconnect
```

local CPU/VPU 由外部实现提供，图中只规定控制、数据和事件集成接口，不定义其内部逻辑。local SRAM 是数据端点，不是 DRAM 转发节点；DMA 在两侧地址空间之间读写。command fetcher、completion writer 和 IRQ aggregator 均为目标组件，不表示现有 scheduler 已具备。

### 3.3 组件责任与外部责任

| 组件/外部对象 | 责任 | 不承担的责任 |
|---|---|---|
| GEMM | 权重驻留矩阵计算、私有 partial sum 和输出处理 | 不直接访问 DRAM；不管理 Host ring |
| DMA | DRAM/local/peer 数据搬运与布局变换 | 不替代 scheduler 的跨引擎依赖管理 |
| scheduler | 命令接受、依赖、派发、事件和完成汇总 | 不替代 CPU/VPU 指令译码或系统 cache coherence |
| local SRAM + local interconnect | 本地数据存放、路由、冲突仲裁与返回 | 不自动推断 tensor 生命周期 |
| system interconnect | DRAM/peer 访问、共享流量和 QoS | 不自动保证 Host 私有 cache 一致性 |
| synchronization unit | 共享同步状态和原子操作序列化 | 不存储 tensor，不代替数据写完成和 fence |
| local CPU / VPU | 控制指令、向量算子和规定的事件协议 | 微架构由外部仓库管理 |
| Host / runtime | 分块、描述符、缓冲区生命周期、cache 维护及提交 | 不依赖未定义的隐式 fence |
| DRAM controller / SoC | 内存时序、物理带宽、地址路由及系统保护边界 | 不保证所有负载都达到峰值带宽 |

## 4. 计算组织与端到端数据流

### 4.1 GEMM 计算组织

**现状：** GEMM 的 K/N 是空间轴，M 是时间轴；基本执行次序为 N tile → K pass → M 行。每个 `(N tile, K pass)` 先装载权重，再处理 M 行，不应描述成 M/N 空间二维 output-stationary 阵列。

- 默认 T×T=256 个 PE；
- INT8/MXFP8/FP8 和合法 W4A8 混配每有效计算拍贡献 `2T²=512 MAC`；
- 同格式 INT4/MXFP4 每有效计算拍贡献 `4T²=1024 MAC`；
- 上述数字是有效计算拍并行度，不是完整命令持续吞吐；
- 当前权重装载与 A 行计算阶段不重叠，DMA 预取不等于 PE 双 bank 执行。

输入格式、K 对齐、N 补齐、舍入和输出异常由 GEMM 模块规格定义。架构层不声明所有输入/输出格式可任意交叉组合。各精度峰值见第 2.3 节，FP4 指 MXFP4，不以 4bit 峰值代表 W4A8。

### 4.2 存储与搬运边界

**目标数据流：**

```text
DRAM weights/activation
  -> DMA copy / layout transform
  -> local SRAM input regions
  -> GEMM local ports -> private buffers -> compute -> output processing
  -> local SRAM result region
  -> VPU consume or DMA writeback
  -> DRAM / peer SRAM
```

GEMM 的 A/B/C 物理数据口默认各 256 bit；scale/epilogue 参数采用独立小数据口。通用 DMA 与这些 GEMM 本地端口分别存在，不能因都称为搬运而合并成一个共享执行引擎。

### 4.3 缓冲与重叠

**目标：** 通用 DMA 预取下一任务的数据时，GEMM 消费当前任务区域；结果写回可与不冲突的下一次输入搬运重叠。

重叠须满足：

- 使用互不覆盖的 SRAM 区域，或明确的生产者/消费者所有权转移；
- 当前命令所需的全 K 数据保持可读，不能假定有外部 partial sum 输入；
- 片外预取、SRAM 双缓冲和 PE 权重双 bank 是三种不同机制；
- W4 子字缓存、PE 双 bank 均为需单独验证的计算供数优化，不是当前系统能力。

### 4.4 VPU 职责与精度需求

**目标：** 外部 VPU 支持 FP32 和 FP16 算术，承担 GEMM 之外的向量处理，而不是再建一个同规模矩阵阵列：

- FP16 用于向量输入/输出、逐元素加乘、激活和带宽敏感的中间 tensor；
- FP32 用于归一化、softmax 等需要较高动态范围或数值稳定性的归约/累加；要求支持 FP16 输入转换后以 FP32 累加及结果转换，原生混合精度指令与具体吞吐由外部接口规格确认；
- 典型算子包括 RMSNorm/LayerNorm、SiLU/GELU、softmax、RoPE、residual add、格式转换及量化辅助；GEMM 已融合的 bias/requant 等工作不重复计入 VPU；
- QKᵀ 和 attention probability×V 优先按矩阵运算规划，但当前 GEMM 默认输入不支持 FP16/FP32，必须验证量化后的格式与精度；若这些矩阵乘落到 FP16 VPU，单独增加 attention 算力预算，不能混入小型后处理假设。

基础需求包括 add/mul/FMA、比较、sum/max 归约、掩码/tail 和 FP16↔FP32 转换；exp、reciprocal、rsqrt 及可选 GELU 所需特殊函数可由硬件或向量软件实现，但应定义误差、启动间隔和端到端算子吞吐。FMA 的 GFLOPS 不代表每秒可完成同等数量的 exp/归约。

本文只定义能力与吞吐契约，不指定外部 VPU 的 ISA、流水级或内部模块。首版目标为每 NPU core 配套 256-bit 等效执行能力、FP32 8 / FP16 16 GFLOPS；若采用 cluster 共享 VPU，必须覆盖对应 SKU 的聚合吞吐、各 core 数据访问和公平调度，不得只保留单核容量。性能、接口与验收要求见第 12.7 节；产品配比、扩展档位和工作负载推导见[性能分析](performance_analysis.md#2-vpu-配比选型与工作负载分析)。

## 5. 存储子系统

### 5.1 存储层次

| 层次 | 内容 | 管理方式 |
|---|---|---|
| GEMM 私有 buffer | 当前 pass、权重、partial sum、输出暂存 | 引擎内部管理，不能作为共享 tensor 内存 |
| local SRAM | activation、weight tile、output、VPU scratch、少量控制数据 | runtime/scheduler 显式分配与复用 |
| DRAM | 模型权重、完整 tensor、KV 和命令/完成 ring | 系统容量层，通过显式搬运访问 |

local SRAM 是 scratchpad，不用“L1 cache 命中率”描述其性能。数据只有通过明确的生产者写入或 DMA 搬运才进入 SRAM，没有自动填充、替换或脏数据回写；KV 主体和完整模型仍驻留 DRAM。

### 5.2 Local SRAM banks

**目标组织：** 8 banks、每 bank 256 bit、32-byte 低位交织；容量候选 512 KiB/core 或 1 MiB/core。

在“每 bank 每拍完成一个 32-byte 访问”的假设下，bank 总峰值为 256 B/cycle。采用 32-byte 交织时，8-bank 示例为 `bank=addr[7:5]`；最终位选、深度和 ECC 粒度需结合 SRAM macro 确认。

**待确认：** 1RW、1R1W 或其他 macro 端口形式。未选 macro 前，不能把 256 B/cycle 承诺为读写各自独立的同时峰值。

### 5.3 资源分配与小访问

- 输入、输出、下一任务预取及 VPU scratch 使用显式区域；
- 按各模块真实布局预留 N 补齐、scale、bias/requant 参数、对齐和 guard 空间；
- 推荐按 bank 错开活跃 A/B/C 区域；仅 byte-address 对齐不足以避免冲突；
- 4-byte scale 和 1-byte scale 写可能仍占一个 bank 请求拍；byte strobe 不等于减少仲裁占用；
- 是否为 scale/控制数据设置独立窄存储或请求合并缓冲，是待测优化，而非初版承诺。

容量和 tiling 算例集中在第 12.2 节。

### 5.4 显式驻留与 DRAM 访问

**目标：** runtime 规划 tensor 分块、SRAM 分配、数据复用和搬运依赖；scheduler 执行依赖并约束缓冲区复用，DMA 按明确地址搬运。已驻留 SRAM 的数据复用无需重复访问 DRAM，但容量不足时由任务计划显式写回或重新装载。

- cluster 多主经 40-bit system interconnect 直接访问 DRAM controller；
- 权重按连续分块预取到 SRAM，双缓冲区域与当前计算区不重叠；
- 描述符可按任务需要显式取入本地命令 buffer，容量与预取策略待确认；
- KV 按实际消费块从 DRAM 取入 SRAM，更新后通过明确写回和完成依赖发布；
- 首版不增加 cluster 共享 tensor SRAM；核间共享优先复用 peer SRAM window，独立共享 SRAM 只有需求明确后才评估；
- DRAM controller 的入口/后端并行度、排队和持续有效带宽必须匹配第 12 章预算。

该方式将数据驻留和复用责任交给编译器/runtime，而不是消除存储调度复杂度。外部内存类型确定为 LPDDR4x；数据位宽、速率、容量、controller 频率、效率和 ECC 仍属于系统选型项。

## 6. 互联与流控

### 6.1 Local interconnect

**目标：** 按 bank 路由、每 bank 仲裁；对不同 banks 的无冲突请求允许同拍推进。

架构请求含 byte address、read/write、data、byte enable 和 source/return routing 信息；内部序号和具体 Bundle 留给接口规格。GEMM 小数据口、CPU 窄口和 256-bit 主数据口通过 adapter 访问。

仲裁建议：`GEMM > VPU > DMA > CPU/debug`，但 DMA 进度决定下一批输入是否到达，因此必须加入 age boost 或最小配额，避免计算优先策略使搬运永久饥饿。

### 6.2 DMA 两侧地址路由

DMA 的读请求和写请求分别译码：

| 操作 | 读侧 | 写侧 |
|---|---|---|
| DRAM → local SRAM | system AXI | local interconnect |
| local SRAM → DRAM | local interconnect | system AXI |
| peer SRAM → local SRAM | peer system path | local interconnect |
| local SRAM → peer SRAM | local interconnect | peer system path |
| local SRAM → local SRAM | local interconnect | local interconnect |

一个 burst 内目的端点固定；跨区域和 AXI 4 KiB 边界的拆分责任由桥接器与 DMA 接口规格明确。返回数据/写响应须回到原 engine，不允许丢失源归属。

### 6.3 在途事务与反压

**现状：** 每 DMA engine 至多一个读请求在途；数据接口无读 transaction ID。不能只把 AXI adapter 的 outstanding 深度调大就宣称引擎可并行发读。

**目标：**

- DMA 支持多读在途，并在接口或内部顺序域中定义返回关联；
- GEMM 的本地顺序返回要求由桥接器保持；
- 按预留返回数据容量发 credit，响应缓冲不足时停止接受新请求；
- 合并多个 engine 到 AXI 时保留 source ID，跨目的端点响应允许的顺序必须明确；
- 写请求接受、写响应返回、数据对其他主设备可见是不同事件。

在途深度推导见 12.4；不能把默认配置字段当作当前已实现并发能力。

### 6.4 System interconnect

**目标：** 每 core 独立 256-bit AXI4 ingress，40-bit 地址；支持 DRAM、peer SRAM 和独立同步单元路由，以及权重/KV/控制流量 QoS。

256-bit 接口若运行于 500 MHz，单向数据峰值为 16 GB/s；G0 两核/G0 Pro 四核独立入口的峰值总和为 32/64 GB/s，不代表 DRAM 可提供同等持续带宽。system clock 尚未冻结，不与 GEMM 降频自动绑定。

cluster 若汇聚为单路 `256-bit @ 500 MHz`，仅有 16 GB/s：对 G0 两核 W4 decode 的条件化权重需求已无其他流量余量，对 G0 Pro 四核 32 GB/s 需求则不足。更宽主干、多并行通路和独立 system clock 均是实现选项，按第 12 章负载和 LPDDR4x 实际可用带宽确定，不把入口峰值总和直接指定为主干保证值。

remote SRAM 访问也竞争 local banks，必须计入 local 仲裁，不能在带宽表中隐去。

### 6.5 前进性与死锁约束

**目标：**

- 响应排空不依赖提交新命令或获取同一被占满队列的 credit；
- 发起 burst 前可用 credit 与返回 buffer 容量相符；
- 地址接受后即使计算暂停也能返回响应或上报错误；
- peer copy 不同时长期锁住两端 SRAM 资源；
- 仲裁公平性和有限背压下的前进性需要系统级验证，不以“标准 AXI”替代证明。

## 7. 执行与编程模型

### 7.1 命令入口与上下文

**目标：** 支持 local CPU custom request 与 Host 异步 descriptor ring 两类入口，通过统一的命令接受与调度规则管理。

- local CPU 请求适合低延迟控制；
- Host ring 需要 command fetcher，当前尚未实现；
- 两类入口由 scheduler 仲裁，不能绕过依赖直接同时修改同一 GEMM 执行上下文；
- 首版按单受信任执行上下文规划，不声明多进程隔离或抢占。

**现状：** scheduler 仅有请求回显和译码骨架；`slotDepth=8` 不代表已有八命令并发。GEMM 的四命令 lane 可接受寄存器批次，但仍只有一个执行上下文；通用 DMA 双 engine 不等于 scheduler 同拍双派发。

### 7.2 Scheduler 与 VPU

**目标 scheduler：** 管理 slot、依赖、adapter 派发、tag 映射、完成和异常；允许互不依赖的计算与搬运跨周期并行。

VPU 指令仍可由 local CPU decoder 直接送往外部 VPU。scheduler 只管理必要的跨单元依赖和事件；VPU 的 launch/done、背压和错误协议待外部接口确认。

GEMM/DMA/VPU 的 tag 位宽和生命周期可能不同，adapter 必须定义关联规则，不默认各模块裸 tag 可直接作为全系统唯一 ID。

### 7.3 缓冲区所有权

**目标：** runtime/scheduler 按以下生命周期复用数据区，具体寄存器实现待定：

```text
FREE -> PRODUCER_WRITING -> READY -> CONSUMER_READING -> FREE
```

- 输入区 READY 前，消费者不得读；
- GEMM 完成读之前，DMA 不得覆盖输入；
- 输出写入可见并完成错误检查后，VPU/peer/Host 才能消费；
- buffer 双缓冲切换以完成依赖为依据，不按估计周期或仅 busy=0 推断；
- 不假定 DMA 内部 hazard 检查覆盖 CPU/VPU/GEMM/peer 的外部访问。

### 7.4 完成、事件与依赖

区分四个事件：命令被接受、被派发、引擎执行完成、结果对消费者可见。系统 completion 只有在所需写响应/可见性条件满足后发布，并同时携带成功或失败状态。

`WAIT_EVENT`、`SIGNAL_EVENT`、`POLL_EQ` 和 atomic RMW 为 scheduler 目标能力，不是现状；event 只解除已定义的依赖，不能隐式完成 cache 维护。

完整端到端任务示例见第 14.1 节。

## 8. 地址、顺序与一致性

### 8.1 地址视图

**目标：** 系统采用 40-bit 物理地址；GEMM 使用 32-bit local SRAM offset。通用 DMA 当前已支持 40-bit address，无需为这一目标重新扩大其地址宽度。

| 区域 | 访问者/用途 | 属性 |
|---|---|---|
| local CSR | Host/local CPU、scheduler 控制 | device、非缓存 |
| local SRAM window | GEMM/DMA/VPU/CPU | scratchpad；CPU 映射不得产生未维护的私有 cache 副本 |
| peer SRAM window[n] | DMA/CPU/外部主设备 | remote scratchpad，遵循相同映射约束 |
| synchronization window | scheduler/CPU 等授权主设备 | device、非缓存；原子与普通访问统一序列化 |
| shared DRAM | descriptors、completion、权重、KV、tensor | NPU 直接系统访问；Host cache 属性与维护由平台契约决定 |

数值基址、window size 和访问许可待确认。bridge 对 GEMM offset 做窗口译码和范围检查，不截断 40-bit 地址伪装成合法本地访问。编译期字段只表达真实可配置项，固定内部数量不包装为 Config 选项。

### 8.2 顺序与可见性

**目标：** 明确以下 producer/consumer 顺序：

1. Host 写 descriptor → 系统可见 → 更新 ring tail → doorbell；
2. DMA 写 SRAM → 所需响应完成且本地可见 → 发布输入 READY；
3. GEMM/VPU 写结果 → 结果可见 → peer event 或 completion；
4. Host 观察 completion → 规定的 cache 维护 → 读取结果。

AXI 不同 ID、不同接口以及数据写与同步通知间不默认有全局顺序；fence、drain 和响应含义需在系统接口规格中定义。

### 8.3 Host cache coherence

**待确认，二类方案择一：**

- 系统硬件 coherence：通过 SoC 提供的一致性接口处理 Host 私有 cache 与 NPU 的共享 DRAM 访问；是否采用相应桥接器由平台能力决定；
- 非一致系统：runtime 对 descriptor/input 做 clean，对 completion/output 做必要的 invalidate，提交和读取前后配合内存屏障。

NPU 数据路径不设置透明缓存，不等于 Host/local CPU 没有 cache；任何可缓存的共享数据都必须遵循明确的所有权与 cache 维护规则。local/peer SRAM window 和同步区默认采用非缓存映射，避免同一物理区域存在未受控的脏副本。

### 8.4 原子与同步空间

**目标：** cluster 设置独立同步单元，提供同步状态、atomic RMW 和读取接口；具体状态容量、操作编码和访问协议待确认。多核 barrier 的 atomic RMW 与 `POLL_EQ` 访问同一同步窗口，普通访问与原子操作共享序列化点。

同步单元不承载 tensor 数据，也不让数据写自动可见；发出同步更新前仍须满足第 8.2 节的数据排空规则。local/peer SRAM window 不默认支持原子；同步状态不能靠多个普通读写模拟不可分割 RMW。

## 9. 系统集成与通信接口

### 9.1 对外接口分类

方向以 NPU core 为参照，端口名为架构占位，不表示现有 RTL 已具备。

| 接口 | 方向 | 目标形态 | 用途与状态 |
|---|---|---|---|
| system data | master | AXI4，256-bit data、40-bit address | DMA/系统访问；目标 |
| peer SRAM access | slave | AXI4，256-bit data、40-bit address | 远端访问本地 SRAM；目标 |
| CSR/control | slave | AXI4-Lite 32-bit 或 APB | 配置、doorbell、PMU；协议待确认 |
| local CPU request/response | slave/master | Decoupled custom request | 已有 scheduler 骨架，完整事务语义待实现 |
| VPU dependency event | 双向 | launch/dependency/done/error | 外部接口待确认，不替代 VPU 指令口 |
| VPU local data | slave to VPU master | 1 × 256-bit local request，每拍最多一个读或写 | 首版目标；返回顺序、在途深度与服务配额须在外部接口规格冻结，见 12.7 |
| interrupt | output | local/Host 分路 level；MSI 为可选 | 路由待确认 |
| core event link | 双向 | event ID + delivery/ack | 可选增强，未实现 |
| debug access | 双向 | 系统 debug bridge 或 JTAG 集成 | 暂停、状态、trace；待确认 |

AXI ID、最大 burst、在途数、QoS、错误响应、CDC 与顺序域在接口规格中逐项冻结，不因采用标准协议就默认支持完整 AXI 功能集。

### 9.2 Host 任务提交与完成

**目标：** Host 在 DRAM ring 写 descriptor，完成可见性处理后写 tail/doorbell；fetcher 将 descriptor 提交 scheduler；执行结束后写 completion，再通知 Host。

ring 的 entry 编码、head/tail、满空、回绕、fetch 失败和恢复尚未定义；这些是正式接口的待办，不用一幅提交流程图代替契约。

完成中断采用粘滞状态 + mask/clear，local CPU 与 Host 分别路由。Host level IRQ 为首版建议；MSI 若采用，必须由平台提供送达和失败语义。

### 9.3 多核任务划分、数据交换与 barrier

**初评划分建议：** 对单请求 GEMM 优先按 N 维输出列分片，每 core 使用不同 B 权重片，共享 A 通过复制或系统搬运分发，输出按列拼接。G0 两核/G0 Pro 四核 SKU 的权重带宽估算均以不重复读取同一层完整权重为前提；activation 分发和结果聚合另计流量。

按 M 维分片可能让多个 core 重读权重；独立请求的并行提高的是聚合吞吐，不等同于单请求加速。K 维分片需要跨 core partial sum 归约，不因现有 GEMM 内部 K pass 累加就默认支持，初版暂不采用。

**基础数据交换：** DMA 经 system interconnect 做 local/peer SRAM copy。每 core 暴露受范围约束的 peer window，远端访问参加本地 bank 仲裁。

barrier 使用共享同步空间中的 atomic RMW + poll，前提是 atomic 与 scheduler 支持都已实现。通知的可见性和顺序遵循第 8.2–8.4 节。

### 9.4 可选核间事件通路

专用窄 event link 用于降低控制通知开销，不传 tensor 数据，也不替代 system interconnect。需定义 set/clear/ack、重复事件合并、复位状态和多来源竞争；性能改善待测，不预先保证亚微秒延迟。

## 10. 时钟、复位与电源管理

### 10.1 时钟域与 CDC

GEMM 的 G0 两核/G0 Pro 四核 SKU 均以 500 MHz 为目标。**首版组织建议：** scheduler、GEMM、DMA、local interconnect 和 SRAM 处于同一 500 MHz core clock；其中非 GEMM 单元的共域方案仍需集成确认。system interconnect、DRAM controller 与 LPDDR4x PHY 可使用独立时钟，频率和比例由系统选型决定。

AXI bridge 跨域需成套处理地址、数据、响应及在途归属；异步 FIFO 必须配合 reset/flush 策略。IRQ/event 使用不会丢失通知的跨域机制，不能直接采样单拍脉冲。

### 10.2 复位与任务排空

**目标：** reset 同步释放，初始化完成前停止接受新任务。正常停止先停止新提交，再 drain 在途访问，最后关闭 clock。

**待确认：** core 局部复位时已经被系统接受的事务怎样完成或标错；peer 正在读写 SRAM 时如何隔离。复位不承诺撤销外部已接受写或回滚部分结果。

### 10.3 电源状态

首版建议区分 RUN、IDLE 和 CLOCK_GATED；是否 power gate、SRAM retention、DVFS 尚未决定。

进入 clock gate 不仅需要 scheduler 空闲，还需 GEMM/DMA、command fetch、peer/AXI 事务及返回队列排空。所有外部访问和 event 的唤醒路径必须先于访问接收建立。

## 11. 可靠性、安全边界与观测

### 11.1 异常处理

**目标：** 对模块错误、AXI 错误和可选 SRAM ECC 汇总 source/code、有效 address、command identity；当前模块原始错误口不一定具有所有字段，由 adapter 关联而不是假定直接提供。

- completion 明确区分成功/失败；
- 首错粘滞保存，后续事件可计数；
- fatal 错误停止新派发，已接受事务按恢复协议 drain 或标错；
- 不支持事务回滚，部分写入可能仍对外可见；
- timeout 的定义、门限、受害命令与恢复范围待确认，不能用无限等待掩盖系统死锁。

### 11.2 ECC 与访问保护

**待确认：** local SRAM 是否采用 SECDED ECC、保护粒度、错误注入和不可纠错恢复。未实现 ECC 前不宣称存储完整性保证。

首版暂按单受信任任务上下文，不承诺虚拟地址、IOMMU、多租户隔离或抢占。至少需要系统地址 window 范围和 CSR/debug 访问权限契约；若平台要求多进程隔离，必须另立上下文与保护设计。

### 11.3 PMU 与调试

**目标观测：** GEMM active/load/stall，DMA physical/useful bytes、burst/在途水位，SRAM bank conflict 与服务配额，预取就绪等待与双缓冲切换停顿，AXI latency/backpressure，同步操作等待，scheduler 依赖等待与命令延迟。

当前 GEMM `perf` 为观测电平，系统计数器尚待实现。计数需明确采样事件、位宽、溢出、清零和 snapshot，不把测试平台统计当作产品内建 PMU。

debug 暂停必须区分停止新派发和冻结在途事务；trace 需要 command identity 配对及丢失标记。不先行保证未知 debug 接口的无扰动能力。

## 12. 性能、容量与带宽模型

### 12.1 统计口径

所有数字区分：接口峰值、有效计算拍并行度、条件化持续目标、完整任务实测。byte/s 使用对应时钟域实际频率；GB 为十进制，KiB/MiB 为二进制。INT8/INT4 使用 TOPS，FP8/MXFP4 使用 TFLOPS，VPU FP32/FP16 使用 GFLOPS，MAC/FMA 均按 2 operations 计数，SKU 的 T 不代表 TMAC/s。不同精度的峰值比只表示资源配比，不等于相同算法的加速比。

G0 两核与后续 G0 Pro 四核、GEMM 500 MHz 和 LPDDR4x 类型是产品目标；模型布局、非 GEMM 时钟、内存数据位宽/速率、效率与首拍延迟仍未冻结。第 2.3 节给出稠密峰值，当前未提供 cluster 持续吞吐、PPA 或端到端性能实测。

### 12.2 SRAM 容量与 tiling

GEMM 无外部 partial sum 输入，因此当前命令的 A 和完整 K 对应 B 切片须在执行期间保留。对 A8/W4、双缓冲 B：

```text
A_bytes        = M × K
B_double_bytes = 2 × K × N × 0.5 = K × N
C_bytes        = M × N × output_bytes
Required_SRAM  = A + B_double + C + scale/parameters + scratch/reserve
```

这里的双缓冲为相邻任务 B 区域，不表示 PE 已支持双 bank；N 必须采用模块实际编程/补齐宽度。公式还需按具体量化方案计入 scale 和 padding。容量评估必须使用第 2.1 节已批准的 G0 模型与视觉输入，并同时计入当前任务输入/输出、下一任务预取、上一任务写回、VPU scratch 和保留区；在模型及布局冻结前不以单个算例承诺 512 KiB 或 1 MiB 可容纳的 M/K/N 范围。

**现状：** GEMM 默认 `cBufferDepth=64,T=16` 时单命令 `M≤48`。增加结果 buffer 到 256 只在目标 M 超出现有上限等场景才必要，不能作为所有 prefill 的必选前置。

**取舍：** 512 KiB 偏面积/decode；1 MiB 有利于较大 K 的 prefill、视觉中间 tensor 和 VPU 共存，但收益需通过完整任务利用率而非容量比直接推断。

### 12.3 Local SRAM 带宽与请求占用

若采用 500 MHz 共域方案，8 banks × 32 B/cycle 的 SRAM 总峰值为 **128 GB/s/core**；256-bit 主数据口为 16 GB/s/口，两个 256-bit DMA engine 本地一侧合计 32 GB/s/core。以下仍按 B/cycle 列出请求压力，改变时钟不改变 bank 服务槽占用与冲突关系。

| 通路 | 物理接口峰值 | 条件 |
|---|---:|---|
| GEMM A/B read | 各 32 B/cycle | 独立接口峰值，消费阶段不因此变成重叠 |
| GEMM C write | 32 B/cycle | 小格式部分 byte lane 有效，仍占物理拍 |
| GEMM scale A/B read | 各 4 B/cycle | 参数访问可能共用 scale A 口 |
| GEMM C scale write | 1 B/cycle | 低占空比假设，不作为免仲裁理由 |
| DMA 两 engine 本地一侧 | 合计 64 B/cycle | 目标 256-bit/engine，DRAM↔local 或 peer↔local |
| VPU local | 32 B/cycle | 首版单个 256-bit 请求口，读写合计峰值；不保证 VPU 持续 FMA 峰值，见 12.7 |
| CPU/debug | 16 B/cycle | 低优先级预算，非已实现接口 |

上述压力预算不计 C-scale 为 216 B/cycle，计入为 217 B/cycle；它是接口字节总和，不是持续带宽证明：

- 多个窄请求可能各占 32-byte bank 服务槽，同拍总请求数可超过八个；
- 每 bank 1RW 时，同 bank 读写不可假定并行；
- 两 DMA engine 若都做 local→local copy，双侧访问预算增加 64 B/cycle，总和达 280/281 B/cycle；
- remote peer 请求另行竞争 bank；
- bank 映射、合并、错峰和 QoS 验证共同决定实际吞吐。

必须测试实际地址分布和负载组合，不能用 `216<256` 就得出无瓶颈结论。

### 12.4 Decode、DRAM 与在途数据

**条件化权重需求：** 若每 core 在 500 MHz 下达到平均 32 MAC/cycle、decode 每个权重贡献一次 MAC，则：

```text
W4: 32 MAC/cycle × 0.5 B/weight × 500 MHz =  8 GB/s/core
W8: 32 MAC/cycle × 1.0 B/weight × 500 MHz = 16 GB/s/core
```

| SKU | W4 有效权重需求 | W8 有效权重需求 |
|---|---:|---:|
| G0，1T，两核 × 500 MHz | 16 GB/s | 32 GB/s |
| 后续 G0 Pro，2T，四核 × 500 MHz | 32 GB/s | 64 GB/s |

32 MAC/cycle 是完整负载的评估假设，不由 512 MAC/有效计算拍直接保证，也不是 SKU 的峰值定义。W4A8 是首个模型带宽评估基线，不能用其半字节权重流量代表 INT8/FP8 的 W8 流量。权重装载、PE 切换、子字重复读、scale 和结果流量需另测。G0 两核/G0 Pro 四核服务单请求时均采用第 9.3 节的 N 维权重分片假设；若权重复制或任务分配不均，该带宽预算不能直接换算成单请求速度。

**LPDDR4x 与系统带宽：** 内存总数据位宽和传输速率尚未冻结，按所有并行通道的总数据位宽计算，不再次乘 DDR 系数：

```text
BW_theoretical_GBps = rate_MTps × total_data_bits / 8 / 1000
BW_payload         = efficiency × BW_theoretical
BW_weight_available = BW_payload - BW_KV_activation_scale_writeback_control
```

其中效率包括刷新、读写切换、访问布局和 controller 调度损失，其他 SoC 主设备竞争也需计入。仅作选型敏感性示例：若速率为 4266 MT/s，x32/x64 总位宽的理论峰值分别为 17.064/34.128 GB/s；它们并非已选内存配置。16/32 GB/s 的 W4 权重需求均已占对应示例约 94% 理论峰值，不能据此保证持续 decode 目标。

每个 SKU 应分别确认实际可用权重带宽；system fabric 的持续有效载荷预算至少覆盖该 SKU 的权重与其他流量，并为仲裁/反压保留余量。内部峰值由总流量、效率和拓扑推导，不沿用固定的聚合带宽承诺。GEMM 稠密峰值可通过权重复用负载评估，不要求 decode 的一次性 DRAM 权重流达到同等算力。

**在途数据与延迟：** 首拍延迟先以时间记录，再按相应 DMA 时钟换算，不因 GEMM 降频而假定 DRAM 的绝对延迟同步变化。令 `L=ceil(latency_seconds × f_DMA)`、burst 有 D 拍、每拍 b 字节，单读在途且数据连续返回的简化平均带宽为 `D×b/(L+D)` B/DMA-cycle；覆盖首拍延迟的在途数据下界为 `BW_required × latency_seconds`，未含 burst 传输、排队和安全余量。

当前 DMA 128-bit、64-beat burst 为 1 KiB。仅作演算，若 DMA 也运行于 500 MHz，首拍延迟假设为 256 ns，则 L=128 cycles：

```text
single read/engine: 1024 B / (128 + 64) cycles ≈ 5.33 B/cycle ≈ 2.67 GB/s
W4 latency coverage/core: 8 GB/s × 256 ns = 2048 B = 2 KiB
W4 latency coverage/cluster: two cores = 4 KiB; four cores = 8 KiB
```

256 ns 并非 LPDDR4x 实测值；上述 2 KiB/core 为分摊到活跃读 engine 的总下界，不能直接等同于每 engine 的 FIFO 深度。目标 256-bit 时 64-beat burst 为 2 KiB，但一个 burst 覆盖字节下界不等于持续吞吐达标。可从每活跃 engine 四个读 burst 的实现候选评估，再按实际延迟、burst 布局、credit、返回容量和 DRAM 排队冻结深度；W8 在相同吞吐假设下需两倍覆盖字节。

持续目标受最窄串行端点限制。提高 DMA 位宽而不解决 read outstanding、系统互联和 DRAM controller 后端并行度，无法闭环；SRAM 预取/双缓冲也不能降低没有复用的一次性权重流量。

模型级 decode 的纯权重上限按 `BW_weight_available / weight_bytes_per_token` 计算；`weight_bytes_per_token` 必须来自第 2.1 节已批准的 G0 模型、量化格式和实际布局。该上限不是端到端性能保证，attention/VPU、KV、调度和其他 SoC 主设备仍会限制速度。

### 12.5 Prefill 与 KV 容量

G0/G0 Pro 的 prefill 理想计算时间按已批准模型的主矩阵总 MAC 数除以第 2.3 节对应 SKU 的峰值 MAC 吞吐计算，但该结果只用于建立计算下界。权重装载、阵列启动/排空、attention/VPU、调度、数据搬运和实际利用率必须另行计入，不能用理想时间承诺端到端 prefill 时延或 G0 Pro 相对 G0 的实测加速比。若采用 A4/W4，还必须证明 activation 量化格式合法且精度满足模型验收要求，不能把同格式 4bit 峰值作为 W4A8 的默认收益。

KV 必须按已批准 G0 模型的 KV heads、层数、head dimension、上下文长度、存储格式和量化附加项推导：

```text
KV_bytes/token = layers × 2(K,V) × kv_heads × head_dim × bytes/element
KV_capacity    = KV_bytes/token × context_length + scale/page/padding overhead
```

不得用 attention query heads 替代 KV heads。模型配置未锁定前不发布精确容量承诺；MX/block scale、分页和 padding 还可能增加占用。

### 12.6 PPA 与测量依据

500 MHz 是针对 GEMM 频率/功耗约束的产品目标，不等于已完成时序签核，也不据降频比例推算节能比例。尚无可用于本文定量预算的面积、功耗和时序实测。

后续报告至少锁定 RTL 基线、SKU/core 数量、参数、SRAM macro、工艺、电压、工具、各时钟域频率、LPDDR4x 配置、工作负载和测量起止事件；G0 两核/G0 Pro 四核、8-bank/16-bank、512 KiB/1 MiB 需同时比较 PPA 与持续任务吞吐。

### 12.7 VPU 性能指标与接口要求

**目标：** 首版每个 NPU core 配套一份 256-bit 等效向量执行能力，VPU 目标时钟为 500 MHz，支持 FP32 和 FP16。以下是本架构草案的集成要求，尚未完成外部实现确认或 PPA 验证；产品对比、宽度选型及工作负载推导归入[性能分析](performance_analysis.md#2-vpu-配比选型与工作负载分析)，不作为本节的替代指标。

#### 12.7.1 计算与数据接口指标

| 指标 | 每 NPU core | G0 两核 1T SKU 聚合 | 后续 G0 Pro 四核 2T SKU 聚合 |
|---|---:|---:|---:|
| VPU 目标时钟 | 500 MHz | 500 MHz | 500 MHz |
| 等效向量执行宽度 | 256 bit：8 个 FP32 或 16 个 FP16 元素 | 每 core 同配置 | 每 core 同配置 |
| FP32 FMA 稳态吞吐 | 8 FMA/cycle | 16 FMA/cycle | 32 FMA/cycle |
| FP32 FMA 峰值 | 8 GFLOPS | 16 GFLOPS | 32 GFLOPS |
| FP16 FMA 稳态吞吐 | 16 FMA/cycle | 32 FMA/cycle | 64 FMA/cycle |
| FP16 FMA 峰值 | 16 GFLOPS | 32 GFLOPS | 64 GFLOPS |
| local 数据请求端口 | 1 × 256 bit，每拍最多一个读或写请求 | 每 core 独立端口 | 每 core 独立端口 |
| local 数据接口峰值 | 32 B/cycle；读写合计 16 GB/s | 合计 32 GB/s | 合计 64 GB/s |

FMA 按 2 operations 计数；FP32/FP16 是替代运行模式，峰值不得相加。FP16 16 GFLOPS 要求每拍处理 16 个有效 FP16 FMA，外部实现必须提供相应打包算术能力。等效执行宽度规定元素吞吐，不规定 ISA 向量长度、物理 lane 组织或 SRAM 数据口以外的内部端口宽度。

计算峰值的验收条件为操作数驻留寄存器或局部缓冲、无访存反压、足够独立 FMA 消除依赖停顿、全部元素有效，并排除流水线启动/排空。多核聚合峰值要求各 core 同时满足这些条件。local 数据接口峰值按连续满宽请求、无仲裁冲突和无返回反压计数；它不是独立读写各 16 GB/s，也不保证 SRAM 流式算子达到寄存器内 FMA 峰值。

#### 12.7.2 算术与数值能力要求

| 能力 | 首版要求 |
|---|---|
| 基础算术 | FP32/FP16 add、mul、FMA、比较与选择；FMA 仅在最终结果处舍入 |
| 混合精度 | FP16 输入转为 FP32 后执行累加/归约，支持结果转回 FP16；可由转换加 FP32 算术实现，不要求原生混合 FMA 或 FP16 双倍累加吞吐 |
| 归约 | sum/max；归一化与 softmax 的关键累加使用 FP32；明确归约顺序与误差界限 |
| 数据转换 | FP16↔FP32；量化辅助涉及的转换、舍入和饱和语义必须在算子规格中定义 |
| 掩码与 tail | 支持非满向量长度；无效元素不得更新结果、参与归约或产生越界访问 |
| 特殊函数 | exp、reciprocal、rsqrt 提供可调用实现，可为硬件或向量软件；分别定义输入域、误差、启动间隔或软件服务时间 |
| 浮点边界 | 明确舍入模式、NaN/Inf、subnormal、signed zero 和异常可见性；不得用格式支持代替数值契约 |

外部实现须满足第 4.4 节的向量算子范围。完整 FP16 attention 矩阵运算不属于本节 FMA 峰值的端到端性能保证；算子落点必须在模型执行计划中明确，不得将当前 GEMM 不支持的输入格式视为已覆盖。

#### 12.7.3 集成与前进性要求

- VPU 经 local interconnect 访问 local SRAM，遵循第 7.3 节缓冲区所有权和第 8.2 节结果可见性规则；不得绕过 READY 依赖读取输入或提前发布完成。
- local 数据口支持 byte enable、返回归属、反压和错误传递；adapter 保持双方约定的返回顺序，并按返回缓冲容量限制在途请求。
- 调度必须为 VPU 和 DMA 提供有限背压下的前进性；VPU 请求反压时，已接受事务的响应仍须排空。
- VPU 的 launch/dependency/done/error 与 scheduler 建立命令关联；done 与写结果可见分别定义，异常不得被报告为成功。
- 若改为 cluster 共享 VPU，必须满足对应 SKU 的聚合计算指标，保留各 core 的本地数据访问能力及公平服务，不以共享单核资源替代上述要求。

#### 12.7.4 验收与规格冻结要求

| 验收项 | 必须提供的结果 |
|---|---|
| 峰值计算 | FP32 8、FP16 16 FMA/cycle/core 的稳态测量；记录频率、有效元素、依赖条件和测量窗口 |
| local 数据口 | 256-bit 满宽请求的读、写及混合序列；核对读写合计每拍最多一请求、数据正确性、反压及返回顺序 |
| 非 FMA 能力 | add/mul、混合精度、转换、sum/max、exp/reciprocal/rsqrt 分项给出吞吐、延迟或启动间隔与误差；不得套用 FMA GFLOPS |
| 完整算子 | RMSNorm/LayerNorm、softmax、SiLU、RoPE、residual add、格式转换及量化辅助的数值与服务时间；GELU 仅在目标模型采用时验收 |
| 系统并发 | GEMM/DMA/VPU 同时运行时的 SRAM 有效字节、bank 冲突、等待与完成可见性；G0 两核和后续 G0 Pro 四核分别验证 |
| 边界与恢复 | tail、非有限值、地址边界、持续反压、异常和 reset/drain，不丢响应、不误报完成 |

**规格冻结前置：** 外部 VPU 接口规格必须填入非 FMA 指令吞吐、混合精度路径吞吐、特殊函数数值误差、端到端算子允许服务时间，以及 tag/在途深度、事件与 reset/drain 协议的批准值。上述细项目前待确认，未填入唯一值前不得判定 VPU 集成验收完成；本节不虚构延迟、误差或流式吞吐保证。

## 13. 架构取舍、实现差距与待确认项

### 13.1 现有能力到系统目标的差距

| 组件 | 差距 | 后续动作/条件 |
|---|---|---|
| core 顶层/本地互联/SRAM | 尚未实现 | 建立路由、adapter、窗口、仲裁和返回路径 |
| GEMM | 已有计算与本地端口，无系统任务闭环 | 保持 32-bit offset；接入 local SRAM，不直接扩大 DRAM 访问职责 |
| GEMM 持续供数 | 阶段重叠和 W4 子字复用待优化 | 仅在目标吞吐需要时引入 PE 双 bank/子字缓存；独立验证 |
| GEMM 结果 buffer | 默认 M≤48 | 仅为更大 M 调整配置；SRAM 容量不足不能靠增 buffer 解决 |
| DMA | 128-bit 默认、每 engine 单读在途 | 256-bit + 多读在途是当前带宽目标的重要前置，返回协议需配套设计 |
| scheduler/fetcher | 译码骨架，无任务执行与 ring fetch | 实现依赖、tag、adapter、完成和异常；ring 可按里程碑引入 |
| compiler/runtime 数据计划 | 系统级驻留、预取和复用契约待定义 | 明确 SRAM 分配、tensor 生命周期、DMA 依赖和容量不足时的搬运策略 |
| 系统互联 | 尚未实现 cluster 系统访问 | 实现 40-bit 路由、DRAM/peer 聚合并行度、QoS 及 CDC |
| 独立同步单元 | 尚未实现 | 定义同步窗口、原子/普通访问序列化和任务关联 |
| CPU/VPU 外部接口 | VPU 首版指标已在草案定义，外部实现与细项契约未确认 | 按 12.7 核对 256-bit、FP32 8 / FP16 16 GFLOPS，并冻结特殊函数/归约、local 数据服务配额、事件与可见性规则 |

### 13.2 架构决策清单

| 决策 | 已确定目标 / 待确认参数 | 决策依据 |
|---|---|---|
| SKU 与 GEMM 频率 | G0 为两核 1T，后续 G0 Pro 为四核 2T，均为 500 MHz；物理签核待完成 | INT8/FP8 稠密峰值、频率/功耗约束和任务划分效率 |
| DRAM | LPDDR4x 已确定；总位宽、速率、容量与 controller 配置待确认 | 按 SKU 验证有效持续带宽、功耗和平台约束 |
| 时钟与系统主干 | 非 GEMM 共域方案、system clock、聚合宽度与并行通路待确认 | DMA 延迟覆盖、CDC、G0 两核/G0 Pro 四核总流量和内存可用带宽 |
| SRAM 容量 | 512 KiB / 1 MiB | 大 K tiling、VPU 共存与 SRAM 面积 |
| SRAM bank/端口 | 8 / 16 banks；macro 类型 | 请求槽冲突、peer 流量和持续带宽 |
| 数据管理 | 驻留/预取粒度、双缓冲和复用计划 | 容量、任务依赖、搬运量与计算停顿 |
| 独立同步单元 | 状态容量、原子操作集和协议 | 多核 barrier、顺序和访问延迟 |
| Host coherence | coherent / software maintenance | SoC fabric 能力、驱动责任 |
| VPU | 首版草案每 core 256-bit、500 MHz、FP32 8 / FP16 16 GFLOPS；外部实现、共享组织与细项协议待确认 | 按 12.7 验收计算与接口；非 FMA、数值误差及算子服务时间须形成批准值，扩展选型归入性能分析 |
| control/IRQ | AXI-Lite/APB，level/MSI | SoC 接口与通知要求 |
| 生命周期/保护 | reset/drain、timeout、ECC、隔离 | 平台可靠性和安全要求 |
| event link | 共享内存通知 / 专用窄链路 | 实测同步开销，不按昵称决定设计 |

建议先锁定算力与 DRAM→SRAM→计算的带宽闭环，再决定控制增强。除项目标识外，内部工作昵称不属于架构决策项。

## 14. 系统验证与交付边界

### 14.1 单 core 功能闭环

首个里程碑：

1. 经已确认入口提交 DMA→GEMM→DMA 依赖链；
2. 从外部存储搬输入到 local SRAM，写完成后转移所有权；
3. GEMM 计算并写 SRAM，检查数据、scale、tail 和模块错误；
4. DMA 将结果写回外部存储，排空必要响应；
5. 发布带成功/失败的 completion，验证 local/Host IRQ。

local CPU 入口可先验证引擎闭环；Host ring/fetcher 完成后再验系统任务入口，二者交付状态分别记录。

### 14.2 多核与边界测试

先完成 G0 两核 1T 闭环，再独立验证后续 G0 Pro 四核 2T SKU 的入口扩展、任务分片、同步、聚合带宽与功耗；不能用 G0 通过推断 G0 Pro 已验收。

至少覆盖：peer copy 与本地计算争 bank、多主同步与可见性、持续反压、队列耗尽、tag 复用、SRAM 预取与消费重叠时的覆盖冲突、双缓冲切换、Host cache 维护、窗口越界、ring 回绕，以及任务进行中的局部 reset/错误恢复。

atomic barrier、ECC、event link 和 coherent Host 仅在相应能力实现并选择后进入验收，不提前承诺。

### 14.3 性能验收

在目标 500 MHz 下分别核对 G0 两核/G0 Pro 四核 INT8、FP8、INT4、MXFP4 及 W4A8 的每有效拍乘加贡献和第 2.3 节峰值口径，再记录完整命令的装载、计算、排空占比；G0/G0 Pro 的 1T/2T SKU 定位不替代持续算力和 PPA 验证。

VPU 按第 12.7 节验收每 core 256-bit 等效执行、500 MHz 下 FP32 8 / FP16 16 GFLOPS，以及单个 256-bit local 请求口的读写合计峰值；混合精度归约、转换、exp/reciprocal/rsqrt 和完整算子按各自批准指标验收。记录有效元素、访存、特殊函数和归约停顿，覆盖 tail、误差、非有限值及跨 core 竞争。寄存器内 FMA 峰值与 SRAM 流式吞吐分别发布，attention 矩阵落点不同的任务分开验收。

将接口峰值、持续物理字节、有效 tensor 字节、GEMM 有效拍利用率和完整任务延迟分别记录。decode/prefill 必须锁定 LPDDR4x 实际配置，含权重/scale、KV、VPU 与 Host 开销，并量化最窄端点、bank 冲突和 G0 Pro 相对 G0 的扩展效率；W4 与 W8 流量分别验收。

当前参考基线为独立模块；尚无 core/cluster 集成 RTL 或系统性能验证结论。方案确认后再细化正式接口、寄存器、配置、异常码和逐项验证计划。

## 附录 A：当前实现追溯

以下链接仅保留真实仓库路径；组件说明均使用通用名称，不改变源码模块命名。

| 组件 | 当前参考文件 | 用途 |
|---|---|---|
| GEMM | [模块规格](../../workerbee/doc/spec.md)、[默认配置](../../workerbee/scala/main/WorkerBeeConfig.scala)、[顶层接口](../../workerbee/scala/main/WorkerBee.scala) | 阵列、合法域、端口、命令与缓冲上限 |
| DMA | [模块规格](../../foragerbee/doc/spec.md)、[默认配置](../../foragerbee/scala/main/ForagerBeeConfig.scala)、[顶层接口](../../foragerbee/scala/main/ForagerBee.scala)、[数据协议](../../foragerbee/scala/main/FbStreamBus.scala) | 数据宽度、地址和在途响应约束 |
| scheduler | [实现骨架](../../queen/scala/main/Queen.scala)、[配置](../../queen/scala/main/QueenConfig.scala)、[CPU 接口](../../queen/scala/main/QueenInterface.scala) | 区分译码现状与目标执行能力 |
| 硬件规格模板 | [通用模板](../../doc/spec_template.md) | 后续正式规格的组织规则，不强制架构文档采用相同章节 |

## 附录 B：参考资料与适配依据

### B.1 NVIDIA 官方资料

| 资料 | 本次参考范围 | 借鉴方式 |
|---|---|---|
| [NVIDIA Ampere Architecture In-Depth](https://developer.nvidia.com/blog/nvidia-ampere-architecture-in-depth/) | 官方架构正文：总体组织、SM/Tensor Core、存储、异步复制/同步和系统能力 | 先总体后子系统，精度/并行度与性能成立条件分开 |
| [NVIDIA Hopper Architecture In-Depth](https://developer.nvidia.com/blog/nvidia-hopper-architecture-in-depth/) | 官方架构正文：计算层级、数据搬运/同步、存储和互连 | 将执行/同步模型与计算资源分开；明确层级边界 |
| [NVDLA Hardware Architectural Specification](https://nvdla.org/hw/v1/hwarch.html) | 公开硬件架构正文：功能、硬件参数、外部和寄存器接口 | 按功能、存储、控制及接口职责建立可追溯架构 |
| [NVDLA Integrator’s Manual](https://nvdla.org/hw/v2/integration_guide.html) | 公开集成正文：接口、时钟复位、实现/库约束和性能模型 | 独立描述集成责任、生命周期及物理实现假设 |

上述 GPU 架构文章不是完整工程规格；NVDLA v1 架构和 v2 集成资料仅用来参考组织，不能混作同一硬件版本的功能依据。GPU 白皮书 PDF 本次未完整解析，不据其未核验目录作结论。

### B.2 适配原则

- 采用“目标与总览→计算→存储/互联→执行模型→系统接口→非功能要求→性能/约束”的阅读顺序；
- 不照搬 GPU 的 warp/SM、CUDA、MIG 或 NVLink 章节，它们不是本设计已有能力；
- 将本地 scratchpad 与硬件透明 cache 区分，将单 core 与 cluster 带宽区分；
- 工作负载算例为架构决策提供依据，不与接口定义或实现状态混写；
- 路线与验证集中在文尾，不把未来优化列作当前架构支持承诺。

### B.3 非规范性性能分析

VPU 产品配比、执行宽度扩展、工作负载、访存推导及公开来源核验见[性能分析](performance_analysis.md)。这些分析不替代第 12.7 节的架构要求，也不构成实现或产品性能保证。
