# Hive NPU 端到端性能分析

文档版本：`v0.6.0-draft`

修订日期：`2026-10-29`

> 本文从完整请求出发，依次分析软件调度、执行计划、数据生命周期和硬件服务，最后以验收 case 检查预算是否可实现。硬件规格与批准服务目标以[总体架构](arch.md)为准，产品演进见[架构路线图](arch_roadmap.md)，逐层算法与张量推导见各模型拆解。本文没有集成系统实测结果。

## 1. 分析目标与工作负载

### 1.1 分析路径与文档边界

本文要回答三个问题：完整任务需要多少计算、流量和活跃内存；这些需求如何落到各 core 和各阶段；实际系统能否在规定服务窗口内完成并保持数值质量。

分析采用以下顺序：

```text
服务目标与输入 → 请求阶段预算 → 软件分配与任务依赖
→ 算子映射与合法分块 → 数据流和生命周期 → 模块服务需求
→ 联合时间线 → 验收 case → 完整任务复核
```

预算自上而下分解，能力自下而上验证。单个模块达到峰值不是完整任务通过的充分条件；反过来，尚未规定完整服务目标时，也不能随意填写某条接口“必须达到”的 GB/s。

| 文档 | 承载内容 | 不承担的结论 |
|---|---|---|
| 总体架构 | 硬件能力、接口、容量和批准验收目标 | 某模型已经达到服务目标 |
| 本文 | 端到端预算、跨模型资源总览、case 矩阵与证据关系 | runtime/驱动/API 设计，逐个算子实现细节 |
| 模型拆解及后续专项分析 | 模型事实、逐层推导、具体分块/状态/量化方案和详细测试规程 | 未经执行验证的 Hive 服务承诺 |

### 1.2 模型／子图覆盖矩阵

下列九个完整模型按八组工作负载组织，另列 MLA 独立子图；均已有拆解文档。已有拆解不等于模型事实、量化部署或 Hive 执行计划已经验收。

| 序号 | 模型／子图与拆解入口 | 覆盖目的 | 重点算子与资源压力 | Hive 分析状态 |
|---|---|---|---|---|
| 1 | [Qwen3-0.6B](model_analysis/Qwen3-0.6B-逐层计算拆解.md) | 基础基线 | 标准 Attention、GQA、RoPE、SwiGLU | 已有拆解；待执行计划、物理流量与服务验证 |
| 2 | [Qwen3.5-0.8B](model_analysis/Qwen3.5-0.8B-逐层计算拆解.md) | 混合架构基线 | Delta-rule 状态更新、递归／分块归约、短卷积、门控 | 已有拆解；待状态方案与完整服务验证 |
| 3 | [Qwen3.5-2B](model_analysis/Qwen3.5-2B-逐层计算拆解.md) / [9B](model_analysis/Qwen3.5-9B-逐层计算拆解.md) | 规模压力，不新增架构类别 | 权重与状态容量、带宽及调度规模；两种规模独立建账 | 均已有拆解；本文保留 2B 条件化算例，部署与服务待验证 |
| 4 | [LFM3-1.7B](model_analysis/LFM3-1.7B-逐层计算拆解.md) | 卷积与 Attention 混合数据流 | 卷积、滑窗与全局 Attention；混合缓存与流式访问 | 已有拆解；待部署版本核验与执行验证 |
| 5 | [Granite 4.0-H-Tiny](model_analysis/Granite4.0-H-Tiny-逐层计算拆解.md) | Mamba2＋MoE | 状态扫描、专家路由、gather/scatter、小 GEMM | 已有拆解；待路由分布、状态与多核服务验证 |
| 6 | [Gemma 4 E2B](model_analysis/Gemma4-E2B-逐层计算拆解.md) | Per-Layer Embeddings | 逐层 embedding 查表、非连续读取与融合 | 已有拆解；待查表布局、共享复用与服务验证 |
| 7 | [YOLO11n](model_analysis/YOLO11n-逐层计算拆解.md) | 检测基线 | 普通／depthwise 卷积、C3k2、SPPF、多尺度融合、C2PSA 空间 Attention、DFL 解码＋NMS | 已有拆解；待冻结导出图、MX 映射与全流程验证 |
| 8 | [YOLO26n](model_analysis/YOLO26n-逐层计算拆解.md) | 较新的部署路径 | 移除 DFL 的 one-to-one NMS-free 检测头；框解码、top-k、gather、过滤 | 已有拆解；待冻结导出图、MX 映射与全流程验证 |
| 9 | [MLA](model_analysis/MLA-逐层计算拆解.md)（Multi-head Latent Attention，非完整模型） | 潜变量压缩 Attention 专项 | KV 压缩／重建、解耦 RoPE、latent cache、投影权重吸收方案对照 | 已有拆解；待冻结子图 profile、部署与可执行验证 |

本表是分析覆盖清单，不替代[架构批准工作负载](arch.md#128-批准工作负载与持续验收门槛)。不把 Qwen3.5-2B 的预算套给 9B，也不把 YOLO11n 的目标套给 YOLO26n；MLA 只报告子图服务与资源，不报告完整模型的 TTFT、TPOT 或 token/s。MLA 资料包含经典潜向量与后续稀疏/分层压缩路径，各子图固定代际与算法，不混用配置或移植参考机器的速度结论。

MXFP8、MXFP4 的 W/A 全量化分别建账。KV、递推状态、累加、输出及非矩阵算子的精度单列；具体边界由量化图定义，不能由 W/A 精度直接推定。

### 1.3 输入 profile 与目标登记

每个实例冻结模型 revision、tensor/量化 artifact hash、输入数据与布局、采样方式、状态初值、SKU、频率和并发条件。不能只用模型名称标识测试。

| Profile | 输入与边界 | 主要服务量 |
|---|---|---|
| P512，文本 prefill 基线 | batch1、512-token prompt、生成 256 tokens；warm 模型已加载；prefix cache 条件固定 | 完整请求入口至首 token 的 TTFT、prefill 分段时间 |
| D2048，文本 decode 基线 | batch1、初始 2048-token 上下文、连续 256 个 decode step；持久状态实际构建 | 平均 decode token/s、逐步 TPOT 与 p95；记录上下文增长 |
| L/S 扫描 | 独立冻结 prompt 长度 L、初始上下文 S、生成长度 D 与 chunk；区分复用缓存/无缓存 | prefill 权重复用、KV/状态、尾块、容量峰值与服务变化 |
| 完整检测 | COCO val2017、80 类、640×640、batch1；输入预处理到坐标还原后的结果 | 持续 fps、完整帧时延与 p95、mAP50–95 |
| 多请求并发 | 请求到达序列、并发数、各请求 profile、共享缓存和其他 SoC 负载固定 | 聚合吞吐、单请求时延、排队与各 core 负载 |
| MLA 独立子图 | batch、query 长度、上下文、head 数、latent/RoPE 维度、精度、cache 初态；prefill/decode 分开 | 输入就绪至子图输出可用的时间、各接口 bytes、latent cache/workspace 峰值 |

固定长度生成明确 EOS 处理，warm 与首次加载分开。持续测试记录实际频率、温度、请求数、运行时长和其他 SoC 流量，不能由短时 kernel 峰值代替。

| 目标来源与适用范围 | 已批准值或待冻结项 |
|---|---|
| Qwen3.5-0.8B/2B，MXFP8/MXFP4，各 SKU | P512 TTFT p95、D2048 平均 decode 和 TPOT p95 均待确认；不继承旧 FP8 数字 |
| YOLO11n，MXFP8/MXFP4，完整检测 | G0 ≥30 fps、p95 ≤50 ms；Pro ≥48 fps、p95 ≤35 ms |
| 批准语言模型的同 artifact/profile 单请求 decode 扩展 | Pro/G0 实测速度比 ≥1.6；扩展效率为速度比/2 |
| 批准模型的数值与质量 | 独立 MX 参考逐算子预算待冻结；语言模型相对 BF16 perplexity 增幅 ≤5%、任务准确率下降 ≤1 个百分点；检测相对浮点参考 mAP50–95 下降 ≤1 个百分点 |
| 其余分析模型、YOLO26n、MLA | 服务与质量判据单独冻结；没有本文新增的批准数值 |

数值引用[架构第 12.8 节](arch.md#128-批准工作负载与持续验收门槛)。架构还包含不在本文当前覆盖清单中的 DeepSeek/YOLO11s，其指标不转移到其他模型。

### 1.4 硬件分析基线

以下为[批准配置](arch.md#22-批准配置与资源规划)摘要。MAC/FMA 计 2 operations；容量使用 KiB/MiB/GiB，带宽使用十进制 GB/s。

| 资源 | 每 core | G0，2 cores | G0 Pro，4 cores |
|---|---|---|---|
| GEMM | T=16、256 PE、500 MHz | 同一 core 基线 | 同一 core 基线 |
| 8-bit 有效计算拍 | 512 MAC/cycle；0.256 TMAC/s | 0.512 TMAC/s；1.024 TOPS/TFLOPS | 1.024 TMAC/s；2.048 TOPS/TFLOPS |
| 同格式 INT4/MXFP4 有效计算拍 | 1024 MAC/cycle；0.512 TMAC/s | 1.024 TMAC/s；2.048 TOPS/TFLOPS | 2.048 TMAC/s；4.096 TOPS/TFLOPS |
| GEMM 结果 buffer | cBufferDepth=64；单命令 M≤48 | 多核不扩大单命令 M | SRAM 扩容不扩大单命令 M |
| VPU，500 MHz | FP32 8 / FP16 16 GFLOPS，替代模式 | FP32 16 / FP16 32 GFLOPS | FP32 32 / FP16 64 GFLOPS |
| local SRAM | 8×256-bit banks、32-byte 低位交织 | 512 KiB/core；合计 1 MiB | 1 MiB/core；合计 4 MiB |
| DMA | 2 engines、256-bit、最大 64 beats/burst | 4 engines | 8 engines |
| DRAM，共用 | LPDDR4x，4266 MT/s | 4 GiB、x32；理论 17.064 GB/s | 8 GiB、x64；理论 34.128 GB/s |
| SoC 共享 SRAM | 经 system interconnect 显式访问，不是 GEMM/VPU 本地口 | 容量/位宽/banks 待冻结 | 独立核算，不计入 local SRAM |

不同精度的峰值不能相加。合法 W4A8 使用 8-bit 的 512 MAC/cycle，不使用同格式 4-bit 峰值；cluster 峰值要求实际可分摊的工作。接口持续服务、bank 竞争和内存效率见第 6 章。

## 2. 端到端执行全景

### 2.1 请求阶段与计时边界

| 阶段 | 文本任务 | 检测任务 | 计时与资源边界 |
|---|---|---|---|
| 加载与准备 | 权重/量化布局加载、状态初始化 | 权重与工作区准备 | cold 单列；warm 不重复计加载，但保留请求内初始化 |
| 输入处理 | tokenize、输入/位置与初始状态准备 | letterbox、归一化、输入布局转换 | 按声明入口计入，不因 CPU 执行而删除 |
| 提交与排队 | 主 CPU 提交、core 可用性、任务准备 | 同左 | 区分排队与执行；记录实际到达负载 |
| 模型主干 | prefill 或单步 decode 的各层 | 网络、特征融合与检测头 | GEMM、VPU、DMA、CPU 工作全部进入时间线 |
| 输出处理 | final norm、lm_head、采样、token 可见 | 框解码、top-k/过滤、NMS（适用时）、坐标还原 | 输出 tensor 可用不等于请求结果已返回 |
| 完成与回收 | token 返回、后续 step 状态交接 | 结果返回与缓冲复用 | 数据可见、通知与仍活跃的异步操作明确 |

TTFT 为请求入口至首 token 可用的时延，不是主干 GEMM 时间。TPOT 为相邻输出 token 的间隔，包含该 step 内输出头、采样及必要等待。检测时延为每帧入口至完整结果，持续 fps 为测量窗口内完成帧数/时间。时延与吞吐分别核验。

### 2.2 从服务目标分解阶段预算

为每个冻结 profile 登记目标 `T_first*`、平均 decode 目标 `R_decode*`、`T_step,p95*`，或 `R_frame*`、`T_frame,p95*`。星号表示经批准或在分析 case 中明确冻结的目标，不表示实测。

阶段与算子进一步分配服务窗口 `w_i`、允许首响应延迟 `l_i`、数值预算 `E_i*` 和资源预算 `Q_i* / C_i*`。这些值随执行计划绑定，不是独立硬件规格。

```text
平均 decode 周期预算 = 1/R_decode*
持续检测完成周期预算 = 1/R_frame*
接口需求 BW_req,j,i   = Q_j,i / w_j,i
计算需求 P_req,i      = F_mapped,i / w_compute,i
容量需求 C_req,r      = max_t(sum(size of live allocations in region r))
```

YOLO11n 的持续吞吐对应 G0 平均完成周期 ≤33.333 ms、Pro ≤20.833 ms；完整帧 p95 仍分别为 ≤50/35 ms。这两组数值约束不同，不把平均周期当作每帧时延上限。

预算闭合需同时满足：依赖图的关键路径在请求预算内；每个共享资源在该持续周期中的占用可被调度；排队条件与声明负载一致。各阶段 p95 不能直接相加得到请求 p95，尾时延最终由完整请求样本验证。

## 3. 软件调度与多核执行

### 3.1 控制层级与可观测成本

| 执行者 | 分工 | 性能账目 |
|---|---|---|
| 主 CPU 软件 scheduler | 请求/任务分配、core 选择、任务提交与回收 | 主 CPU 占用、排队、提交/完成往返、请求分配不均衡 |
| local CPU | 本地数据计划、GEMM/DMA 命令组织、直接控制 VPU、数据交接 | 命令准备、VPU 启动、同步、cache 维护及主 CPU 回退的暴露时间 |
| 硬件 scheduler | GEMM/DMA 命令及依赖完成 | 8 slots/core 的占用、可发命令等待、单 GEMM 上下文的串行限制 |
| GEMM/VPU/DMA | 按依赖消费数据与产出结果 | 输入等待、执行、输出反压和实际完成时间 |

VPU 不由硬件 scheduler 派发。命令完成、数据对下一消费者可见、完整任务完成分别记录；不能用某个引擎 IRQ 代替所有阶段结束。这里只分析控制成本，不定义软件 API 或驱动结构。

### 3.2 单请求分片与多请求并发

| 策略 | 可能收益 | 必须显式增加的成本 |
|---|---|---|
| 投影输出通道分片 | 分摊权重与 GEMM 工作 | 输入广播/复制、输出拼接、后继消费者访问与同步 |
| attention/state 按 head 分片 | 分摊 KV 或递推状态服务 | head 数不均、GQA 复用、状态所有权、输出投影前的交换 |
| prefill 按 token tile 组织 | 增加投影复用与并行工作 | 因果依赖、递推 chunk 先后关系、状态交接；不能把 chunk 任意独立化 |
| 多请求分配到不同 core | 增加聚合吞吐 | 权重/状态副本、DRAM 与共享 SRAM 竞争、请求排队 |
| 共享数据放 SoC SRAM | 减少部分重复 DRAM 读取或 CPU 交接 | 共享区填充、system 访问、各 core 的 local 搬入、所有权与仲裁 |

K 维跨核归约不是当前架构基线，不作为计划的必要能力。对照单请求分片与多请求并发时，分别报告单请求速度和聚合吞吐，不能相互替代。

### 3.3 命令数量、同步与调度预算

执行计划逐项统计 GEMM/DMA 命令数、VPU 调用数、跨核交换和同步点。仅对独立的矩形投影，命令数可用下式作为初始估算：

```text
N_cmd ≈ ceil(M_total/M_tile) * ceil(N_total/N_tile) * ceil(K_total/K_tile)
```

该式不覆盖融合、尾块、布局转换或跨 K partial sum 的额外累加。命令提交能否被执行覆盖，以实际时间线为准；`命令数×单次提交时间` 是 CPU 成本估算，不自动等于端到端暴露成本。

每个调度实例输出 core 分配图、命令顺序、数据就绪事件、同步点和可复用缓冲区。最繁忙 core 的工作、控制等待与通信预算分别核验，不能用 cluster 平均值掩盖单 core 瓶颈。

## 4. 从计算图到执行计划

### 4.1 算子任务记录

模型拆解给出数学工作；可执行计划还需补齐下列记录，才能计算物理需求。

| 项目 | 必须记录的内容 |
|---|---|
| 算法与位置 | 使用的算子算法；GEMM、VPU、local CPU 或主 CPU；未覆盖算子单列 |
| 格式与转换 | W/A、累加、输出、scale、KV/状态精度；每条量化/反量化/布局路径的实现 |
| 命令与分块 | 合法 M/N/K、padding、尾块、命令数、core 分片轴、实际循环次序 |
| 复用与融合 | 权重/A/KV 重读次数、寄存器复用、融合前后中间写入/读取变化 |
| 分配与依赖 | 地址、所属存储区、活跃份数、数据可见、状态更新与缓冲释放事件 |
| 预算关联 | 所属请求/阶段、`w_i`、各接口 `Q_j,i`、`C_i*`、数值预算与 case ID |

计算账把已映射 GEMM MAC、向量 FLOP、归约次数、特殊函数调用和量化元素数分开。拆解文档可能把逐元素与归约计入广义 MAC，这些数字不能全部除以 GEMM 峰值。

### 4.2 分块、循环次序与精度边界

GEMM 的 K/N 为空间轴，M 为时间轴；单命令 M≤48。64-token 算法 chunk 可按 32+32 等合法命令拆分，但算法依赖不因此消失。增加 SRAM 不自动增加单命令 M，也不证明权重装载与 A 计算能够重叠。

固定 K/N tile 后，M 外循环与 N 外循环会改变权重重读和 A 重读。对 prefill，不能默认整段 prompt 只读取一遍权重，也不能直接使用 `L×decode 权重流量`；对 depthwise/小 GEMM，padding 与实际有效 lane 占比需单列。

跨 K 子命令必须说明 partial sum 的保存、重新读取和显式累加。W/A 量化路径必须包含 scale 求取、编码、padding 与后继格式转换；采用 FP16 attention 或混合精度回退时，应作为独立对照，不能宣称已完成全 MX 部署。

### 4.3 Qwen3.5-2B：从层依赖到任务图

依据[关键超参](model_analysis/Qwen3.5-2B-逐层计算拆解.md#12-关键超参)与[参数核算](model_analysis/Qwen3.5-2B-逐层计算拆解.md#13-参数量核算)，文本模型有 24 层：18 个 DeltaNet、6 个 gated full attention；hidden=2048、MLP intermediate=6144，词表 248320，embedding/lm_head 共享。

```text
输入/状态就绪 → norm与前置投影 → DeltaNet更新或full attention
→ 输出投影/残差 → MLP → 下一层
→ final norm → lm_head → 采样 → token可见
```

| 路径 | 需要落实的执行选择 | 主要依赖与资源 |
|---|---|---|
| prefill 的 DeltaNet | 参考 chunk=64、子块=16；选择块算法、求解与扫描方案 | 块内临时矩阵、块间状态依赖、M 拆命令与数值精度 |
| decode 的 DeltaNet | 单 token 递推；state 按 head/子矩阵分块 | 状态读取、更新、回写可见，不能套 prefill 工作量 |
| full attention | QKᵀ、softmax、p·V 的量化与分块路径 | 8 query/2 KV heads、head_dim=256；GQA 复用与 score/probability 是否落存储 |
| MLP 与输出头 | gate/up/down 的量化边界；lm_head 实际保留位置数 | residual、MLP 活跃区、down K=6144；logits 与采样读写 |

块求逆与三角前代是不同方案，不能只比较 MAC 数就断定 Hive 上谁更快。文本推理的输出头通常保留最后位置，但实际行数按冻结导出图执行，不把某个源码默认参数的解释当作运行保证。

## 5. 数据流与内存生命周期

### 5.1 流量账与容量账

| 账目 | 统计方式 | 需要避免的省略 |
|---|---|---|
| 物理流量 | 每请求/step/帧，按接口、方向、core、阶段计实际 bytes | scale、padding、重读、转换、多遍向量访问、状态、输出与副本 |
| 活跃容量 | 按实际分配的创建、占用、释放事件求峰值 | 当前输入、下一预取、上一写回、scratch、staging 与异步未完成区域 |

```text
Q_j,i = Q_read,j,i + Q_write,j,i
C_peak,r = max_t(sum(size_a for allocation a live in region r at t))
C_DRAM_peak = C_loaded_weights + C_persistent_state(S)
            + max_t(C_live_activation + C_staging + C_copies)
            + C_runtime_and_other_SoC
```

共享读写资源合计流量；独立方向资源还分别核验读与写。同一 tensor 穿过 DRAM、DMA、SRAM 可以在各接口各记一次；同一物理分配不能重复记容量。DRAM 不把已独立分配在 SoC SRAM 的数据再计一份，若实际保留副本则分别计入对应存储区。

### 5.2 存储层级与数据移动

| 存储区 | 典型数据与访问 | 生命周期和服务约束 |
|---|---|---|
| DRAM | 权重、外置 KV/状态、大临时区、系统分配 | 容量包含实际加载的非执行权重与 workspace；带宽与其他 SoC 主设备共享 |
| SoC 共享 SRAM | 共享权重/activation tile、CPU 交接与共享中间结果 | 容量独立冻结；仍需 system 与 local 搬运；没有自动填充或一致性 |
| 每 core local SRAM | GEMM A/B/C/scale、VPU 向量/state tile、预取/写回区 | 私有容量；8-bank 分布、窄请求占槽与读写冲突分别核验 |
| GEMM 结果 buffer/寄存器及 DMA 内部缓冲 | 运算中间状态、返回与转换数据 | 与 local 分配分别计；未完成操作不能提前复用缓冲 |

共享 SRAM 命中不等于本地命中。采用共享层需给出“DRAM→共享区→各 core local”的完整流量，以及共享区 refilling、CPU 访问和多主竞争；只比较减少的 DRAM bytes 会高估收益。

### 5.3 Qwen3.5-2B：持久状态与临时区

依据[运行时状态清单](model_analysis/Qwen3.5-2B-逐层计算拆解.md#43-运行时状态不在权重里但决定-sram)，以 FP32 递推状态、FP16 conv/KV 为参考，不预设量化 KV。

| 数据 | 参考容量 | 执行计划必须补齐的内容 |
|---|---:|---|
| DeltaNet 递推状态 | 18×16×128×128×4 B =18 MiB；每层 1 MiB、每 head 64 KiB | 分片、驻留比例、更新读写次数与所有权 |
| conv 历史状态 | 18×6144×3×2 B =648 KiB | ring buffer 布局、每 step 实际更新量 |
| full-attention KV | 12,288×S B；S=2048/4096/32768 为 24/48/384 MiB | context 增长、KV head 复用、读写布局与分片 |
| 单位置 FP32 logits | 248320×4 B =993,280 B，约 0.947 MiB | 分 tile 输出、采样读取、归约和 CPU 数据交接 |
| activation/量化临时区 | 随 tile 与循环次序变化 | residual、gate/up/down、转换 staging 的活跃区峰值 |

全部 18 MiB 递推状态超过两个 SKU 的 local SRAM 总量。单层 1 MiB 超过 G0 单 core 容量，也会占满 Pro 单 core 可用数据容量；不能假定状态与投影、scratch 同时完整驻留。SoC SRAM 的容量未冻结，不能据此宣布状态已可放入共享层。

若 prefill 保存每个 chunk 的状态快照 `h:[ceil(L/64),16,128,128]`，L=8192 时每层有 33,554,432 元素：2 B/element 为 64 MiB，写后读为 128 MiB；FP32 则翻倍。这是该算法的条件化临时区，不是 Hive 固定要求。融合扫描与输出能否消除它，需通过真实依赖和生命周期证明。

纯文本不执行视觉塔，普通 decode 不计 MTP；若 loader 仍保留其权重，实际分配仍进入容量账。模型名义参数量不能证明 G0 的 4 GiB 足够。

### 5.4 local SRAM 分块算例与容量判据

完整 K 驻留、一份 A、两份 B、一份 FP32 C，A/B 均沿 K block32、每 block 一个 1 B scale，以补齐后的 `M×K×N` 计算：

```text
A_data    = M*K*a_bytes
B_double  = 2*K*N*b_bytes
MX_scale  = (M*K + 2*K*N)/32
C_FP32    = 4*M*N
C_tile    = A_data + B_double + MX_scale + C_FP32
C_live    = C_tile + state_tile + previous_writeback
          + VPU_scratch + conversion_staging + other_live_regions
```

Qwen3.5-2B down projection 取 K=6144、N_tile=16：

| M_tile | MXFP8 的 C_tile | G0 剩余容量 | MXFP4 的 C_tile | G0 剩余容量 |
|---|---:|---:|---:|---:|
| 32 | 398 KiB | 114 KiB | 206 KiB | 306 KiB |
| 48 | 498 KiB | 14 KiB | 258 KiB | 254 KiB |

这里的 N_tile 是所分配的 B/C tile 列数，不是整个投影输出 N，也不是对命令内部复用的默认保证。命令循环和 PE 覆盖范围需另行记录。

验收使用 `C_live≤512 KiB/core`（G0）或 `≤1 MiB/core`（Pro），并检查分配地址不重叠、对齐与实际布局。例如 M48 的 MXFP8 tile 单独合法且能放入，却只留 14 KiB；若另需一个 64 KiB state head，则组合已经超容量，必须修改计划。流式 K、单份 B、不同 C 格式等方案重新计算，不能沿用本表。

## 6. 硬件加速与模块服务

### 6.1 峰值、结构上限与实际服务

除 DRAM 外，byte/s 换算采用 500 MHz 分析条件；实际时钟组织仍按架构冻结。

| 通路 | 接口峰值或参考上限 | 完整服务需测量的内容 |
|---|---|---|
| GEMM A/B 读、C 写 | 各 32 B/cycle，即各 16 GB/s | 权重装载、A 消费、有效计算、排空与写回；接口独立不证明阶段重叠 |
| GEMM scale | A/B 读各 4 B/cycle；C scale 写 1 B/cycle | 窄访问的 bank 服务槽、数据/scale 同步与回写 |
| VPU local | 每拍一次 32 B 读或写，合计 16 GB/s/core | 输入/输出、归约、多遍扫描及特殊函数，不能按读写各 16 GB/s |
| DMA local | 每 engine 32 B/cycle 接口预算 | DRAM/local/peer 的完整搬运；local→local 的源读和目的写同时计入 |
| 每 core system/peer AXI | 256-bit；500 MHz 时单方向 16 GB/s | burst、汇聚、仲裁、CDC、首响应与返回服务 |
| local SRAM banks | 每 bank 每拍服务一次时合计 128 GB/s/core | 1RW 为 macro 候选；同 bank 读写、窄请求和 peer 访问竞争 |
| DRAM controller | G0/Pro 理论 17.064/34.128 GB/s | 实际读写混合、地址分布、其他 SoC 负载、持续 payload 与延迟 |
| SoC SRAM | 容量、位宽、bank 组织待冻结 | 多主共享、system 往返、refill 与各 core 消费窗口 |

独立 [DMA 参考规格](../../foragerbee/doc/spec.md)的简单 byte reader/writer 串行收集与输出，在 256-bit 下 BB/2=16 B/cycle，即 8 GB/s/engine；这是参考路径结构上限，不是 Hive 已达到的带宽。其 FP32/FP16/BF16→MXFP8 量化阶段为 1 element/cycle/engine，即 0.5G element/s；完整收集、量化、排出服务更慢。MXFP4 动态量化、MX 反量化需指定并验证实现，不能视为已交付 DMA 能力。

### 6.2 每个模块的服务预算

| 模块 | 计算/流量输入 | 服务时间记录 | 验收连接 |
|---|---|---|---|
| GEMM | 已映射 MAC、合法 tile、A/B/C/scale bytes | 命令接收→输出可见；分解装载、计算、排空和反压 | 有效拍峰值与真实 tile 完成窗口分别验证 |
| VPU/CPU 算子 | 向量操作、归约/特殊函数、state bytes | 输入就绪→结果可用；启动、访存、归约与回写 | 不用 FMA 峰值替代 exp/rsqrt 或状态 kernel 服务 |
| DMA/转换 | 每方向 bytes、元素数、scale/布局、返回缓冲 | 首请求→全部结果可消费；包括完整转换链 | 有效 payload、首响应、完成窗口、数值与实际 workspace |
| SRAM/system/peer/DRAM | 阶段内真实地址与并发请求 | 每口/方向吞吐、请求槽、延迟、竞争等待 | 在该访问组合下满足 `Q_j,i/w_j,i`，而非独占口峰值 |
| 控制与同步 | 命令/事件数、数据交接 | 提交、依赖就绪、完成可见与缓冲释放 | 暴露控制等待在分配窗口内，不能漏在端到端边界外 |

`F/P_effective` 与 `Q/BW_effective` 给出下界分量；取最大值只是理想 roofline 下界，不是有串行装载/转换阶段时的完整时间。模块完成窗口必须用实际全路径测试判定。

DMA 延迟覆盖可初估 `B_inflight≥BW_engine×latency`。64 beats×32 B 为 2 KiB，四满 burst 为 8 KiB/engine 候选；还要核验返回 credit、实际请求长度、延迟分布和消费者反压。多在途不消除内部串行阶段上限。

### 6.3 接口需求总账

每个条目保存请求/阶段、core/接口、地址轨迹、读写 bytes、允许窗口、所需 GB/s、可用服务和对应 case，形成可复核的预算行。

| 接口/资源 | 需求来源 | 容量或占用检查 |
|---|---|---|
| DRAM | 权重、外置状态/KV、临时区、副本、其他 SoC | 实际已加载权重＋持久状态＋活跃 workspace＋系统分配 |
| system fabric | DRAM、SoC SRAM、按实际路由经过 system 的 peer 请求 | 按汇聚口求和，不以各 core 接口峰值相加代替 |
| SoC SRAM | refill、各 core 共享读取、CPU 交接、共享输出 | 独立活跃峰值与多主 bank/端口竞争 |
| 每 core DMA | 本 core 的外部/local/peer 搬运、格式转换 | 返回缓冲、staging、当前与下一 tile |
| GEMM A/B/scale、C | 循环次序决定的读取/重读、输出和 scale | 实际 A/B 份数、result buffer 与 local C 分开计 |
| VPU local | residual、norm、conv、state、KV、量化的多遍读写 | 向量/state tile、归约与转换 scratch |
| local SRAM banks | 上述本地请求与 peer 请求的真实地址分布 | bytes 与每 bank 请求/cycle 同时核验 |
| peer/控制 | 输出拼接、广播/复制、状态交换、事件与任务控制 | 收发缓冲、副本、同步与往返延迟 |

窄 scale 请求即使 bytes 很少，也可能消耗完整 bank 槽。共享同一口的服务窗口必须包含同时到来的其他工作；不能对每个消费者都单独承诺整口带宽。

## 7. 联合时间线与瓶颈归因

### 7.1 依赖、重叠与暴露成本

每个 profile 建立主 CPU、local CPU、scheduler、DMA、GEMM、VPU、各存储接口的统一事件时间线。事件至少包括请求入口、命令可发、数据就绪、输入等待、有效计算、输出等待、结果可见、状态交接和请求完成；跨域记录需换算到可比较时间基准。

端到端时间沿实际依赖图计算：串行阶段相加，重叠区只计一次。预取被隐藏须同时证明无依赖冲突、缓冲足够、端口能服务和数据按时就绪；SRAM 双缓冲不等于 PE 双权重 bank，也不保证装载/计算重叠。

| 暴露段 | 归因证据 | 不能据此直接断定的结论 |
|---|---|---|
| GEMM 输入等待 | A/B/scale 可见时间、DMA/接口服务与 FIFO 占用 | 只看 DRAM 利用率低就断定 DMA 不忙 |
| state/VPU 串行段 | 每 head/tile 的读、运算、回写与下一消费者 | 用状态总 bytes 小证明可被隐藏 |
| SRAM 冲突/输出反压 | bank 请求槽、仲裁、C 写回、缓冲活跃区 | 接口总 bytes 低于 bank 峰值就不存在冲突 |
| 软件/同步等待 | 命令准备、slot/依赖、跨核事件与数据可见 | 将所有提交时间相加算作完整暴露时间 |
| 多核失衡/共享竞争 | 最繁忙 core、共享接口与同步到达时间 | core 数翻倍就应得到两倍单请求速度 |

同一等待不能在 GEMM stall、DMA stall 和 DRAM 服务中重复加入关键路径。不能以 `max(GEMM,VPU,DMA)` 代替完整请求时间，也不能把各模块独占实测直接拼成并发时间线。

### 7.2 敏感性与方案对照

| 变量 | 对照方式 | 判据 |
|---|---|---|
| L/S 与 chunk | 扫描 prompt/context、chunk 边界与尾块 | 算法工作、权重复用、KV/快照、容量和完整服务变化 |
| MXFP8→MXFP4 | 各自冻结量化图与实际部署 | bytes、有效拍、转换时间、质量共同比较，不只看压缩比 |
| M/N/K 与缓冲份数 | 合法 tile、循环顺序、单/双 B、K streaming | 命令数、重读、活跃峰值与模块完成时间 |
| state 驻留/分片 | 按 head/子矩阵、部分驻留、显式外置 | DRAM 流量减少是否挤出其他 tile，state 依赖窗口是否缩短 |
| KV/临时结果复用与融合 | baseline/candidate 完整数据计划 | 少落存储的收益与新增 scratch、依赖及数值影响 |
| 多核/共享 SRAM | 单请求分片、多请求、共享/私有数据方案 | 单请求/聚合指标、local 搬运、共享竞争与所有权成本 |
| DRAM payload 与其他 SoC | 固定地址/读写组合，扫描外部负载 | controller、fabric、DMA 与消费者链服务分别取证 |

LPDDR4x payload 效率只作条件化敏感性输入：

| 总 payload / 理论峰值 | G0 x32 | Pro x64 |
|---|---:|---:|
| 60% | 10.238 GB/s | 20.477 GB/s |
| 70% | 11.945 GB/s | 23.890 GB/s |
| 80% | 13.651 GB/s | 27.302 GB/s |

这是整个 controller 的条件化 payload，不是 NPU 独享带宽。其他 SoC 流量从可用预算扣除，并重测其调度影响。用理想权重字节除以这些值，只能给出特定假设下的吞吐上界。

## 8. 跨模型资源与服务总览

### 8.1 统一总表的字段

所有模型采用相同字段，但 prefill、decode、整帧与子图分别成行，不将不同单位的数据直接排名。

| 维度 | 汇总内容 | 当前证据要求 |
|---|---|---|
| 实例 | revision、artifact、profile、格式、SKU、并发条件 | 与输入/量化图/执行计划一一关联 |
| 计算 | GEMM MAC、向量 FLOP、归约/特殊函数、量化元素数 | 已映射工作与源算法工作区分 |
| 流量/带宽 | 每接口读写 bytes、服务窗口、所需/可用 GB/s | 包含重读、转换、共享层、peer 与副本 |
| 容量 | DRAM、SoC SRAM、最繁忙 core local 峰值 | 生命周期和地址分配支持，不用 cluster 总量替代 |
| 调度/服务 | 命令数、同步点、各模块完成、关键路径与请求服务 | 同一时钟/负载与完整计时边界 |
| 结论 | 主要暴露成本、瓶颈、可行方案、case 状态/证据 | 理论、模型仿真、RTL 仿真、硬件实测分别标注 |

当前没有九个模型的统一 Hive 部署和可比较实测。第 1.2 节记录拆解状态，本章只给已知算例与各模型应关闭的压力项，不填伪精确的性能排名。

### 8.2 Qwen3.5-2B decode：条件化预算

拆解文档的[算力账](model_analysis/Qwen3.5-2B-逐层计算拆解.md#81-算力账decodes-较短)给出模型级近似：

```text
F_decode_MAC(S) ≈ 1.896e9 + 24576*S
F_lm_head       = 248320*2048 = 508559360 MAC/step
```

上下文项为六个 full attention 层的 QKᵀ 与 p·V；常数含递推等工作，不全是可映射 GEMM MAC。norm、特殊函数、转换与控制还需分别计服务。

约 1,881.8M 文本权重元素统一套用理想 block32、每 block 一个 1 B scale，仅用于格式敏感性：

| 格式 | 理想表示字节 | 每 step 扫描一次的条件化读取 |
|---|---:|---:|
| MXFP8 | 约 1.941 GB | 约 1.941 GB/step |
| MXFP4 | 约 1.000 GB | 约 1.000 GB/step |

这里没有逐 tensor 核验非矩阵参数、保留精度、padding、布局副本、重复读取和常驻复用，不能作为部署容量或最终 DRAM 流量。共享 embedding 查一行与 lm_head 扫整表的访问也需分开计。

若 FP32 递推状态全外置且每 step 读写各一次，KV 为 FP16、每个历史 KV 元素只读一次，则：

```text
Q_decode_DRAM(S) = Q_weight_actual
                + 2*18874368
                + 12288*S_read + 12288
                + Q_conv_state + Q_activation + Q_temporary + Q_replication
BW_average      = R_decode* * Q_decode_DRAM(S)
```

`S_read` 是本步实际读取长度，是否包含当前 token 按执行计划定义。S_read=4096 时历史 KV 读取为 48 MiB/step，状态全外置读写为 37.75 MB/step；改变驻留、复用、精度或分片就要替换相应项。

这条式子只给平均流量。每层 state 更新的窗口、权重 tile 的装载窗口和量化窗口另行计算 `Q/w`。当前未冻结语言模型服务数值，所需带宽保持参数化；源文档的 310 GB/s、token/s 或“状态必须全驻 SRAM”建议不属于 Hive 条件。

### 8.3 各模型决定的压力项

| 工作负载 | 优先关闭的资源与服务问题 | 关联验证 |
|---|---|---|
| Qwen3-0.6B | 标准 Attention/GQA 的 KV 复用、RoPE/softmax 与小 M 投影 | attention 子图、调度、prefill/decode 完整任务 |
| Qwen3.5-0.8B/2B/9B | state 服务、chunk 临时区、权重扫描与规模扩展 | 状态/量化、容量、层/chunk 流水、独立模型服务 |
| LFM3-1.7B | conv 流式访问与滑窗/全局 Attention 的混合缓存 | 数据轨迹、算子映射与跨路径切换 |
| Granite 4.0-H-Tiny | Mamba2 状态、专家路由失衡、gather/scatter 与小 GEMM | 冻结路由分布、非连续访问与多核流水 |
| Gemma 4 E2B | 逐层 embedding 与投影并发、查表布局/共享副本 | 实际 lookup 地址、共享服务与层时间线 |
| YOLO11n | 多尺度活跃区、depthwise 利用率、DFL/NMS 完整后处理 | 帧流水、空/密集目标、完整 fps/p95 与 mAP |
| YOLO26n | NMS-free 头的 decode/top-k/gather/过滤与输出规模 | 独立后处理预算；不借用 YOLO11n 阈值 |
| MLA 子图 | latent cache 读写、展开/权重吸收、临时区与小 GEMM | 固定子图数值、服务和流量对照，不外推完整模型 |

逐层推导留在第 1.2 节的模型文档；复杂状态分块、attention/MLA 方案、共享 SRAM 数据计划、检测映射及详细验收规程可后续单独在 `model_analysis` 下展开。每份专项分析交付可执行计划、bytes/生命周期、服务预算与对应 case 证据，而不是再次罗列峰值。

## 9. 验收 case 与验证闭环

### 9.1 Case 定义、输入冻结与证据

每个 case 实例按以下字段登记，表中的 ID 是本文分析编号，不是新增架构 AR：

```text
来源需求/上层case → 推导公式与假设 → 固定输入/配置/执行计划
→ 前置case → 可运行入口和步骤 → 观测量 → 数值门槛
→ 判定与失败归因 → 原始证据/报告
```

下文为 case 定义矩阵，不是执行记录。所有 case 当前均未执行；服务窗口、逐算子数值预算、共享 SRAM 配置或运行入口未冻结的实例标为“待冻结”，不能签为通过。

| 参数/证据 | 冻结内容 |
|---|---|
| `w_i / l_i` | 对应请求、阶段、接口的完成窗口/首响应延迟上限；由第 2 章预算分解，测量前给出具体数值和单位 |
| `Q_j,i* / C_i*` | 执行计划推导的物理 bytes/分配峰值；包含 padding、重读、缓冲和副本，不含未经说明的误差容忍 |
| `E_i*` | 独立量化/数值参考、输入集合、误差指标及各项阈值；精确搬运按 bit/byte 一致判定 |
| 运行入口 | 实际构建版本、执行程序/仿真入口、输入、步骤、采样数与时长；未实现路径先判不可实施，不虚构命令 |
| 证据类别 | 理论推导用于预算；模型仿真用于可行性；RTL 仿真用于功能/周期；硬件实测用于实际系统服务。不同类别不相互冒充 |
| 原始证据 | tensor/输入 hash、命令与地址轨迹、分配生命周期、周期/时间戳、接口统计、数值输出、质量与服务原始样本 |

统计工具的回放、串行化、cache 或频率控制可能改变运行条件；取证运行与无扰动服务运行分开，并检查计数器/trace 的覆盖与自身开销。性能判据的统计窗口和尾时延采样方案随 case 冻结，不在本文任意指定采样次数。

### 9.2 L0：数据计划可实施

L0 不依赖模块速度已通过，但必须有真实实现路径。它先排除“算得出预算、系统却无法执行”的方案。

| Case / 来源需求 | 输入与执行步骤 | 观测量 | 判据与证据 |
|---|---|---|---|
| C0-01 图、格式与命令合法性；所有上层任务 | 对冻结导出图逐算子登记实现；生成命令与转换链，检查 M/N/K、scale、布局与尾块；含 M=1/32/48、超 M 上限的拆分、chunk 尾块 | 算子覆盖、合法命令数、数据格式和转换边界 | 100% 算子有可运行路径、0 条非法命令、0 条缺失转换；不能靠隐含 FP16 回退完成 MX 计划。证据：图/命令清单、格式参考与合法性检查 |
| C0-02 活跃容量与分配；请求/阶段容量预算 | 为每 core、DRAM、SoC SRAM 生成地址与活跃区间；回放创建/消费/异步完成/释放；覆盖峰值 S、预取与上一写回同时活跃 | 每区 C_peak、地址重叠、越界与复用时刻 | G0 local≤512 KiB/core、Pro≤1 MiB/core；DRAM 含系统分配≤4/8 GiB；SoC SRAM≤独立冻结容量；0 次活跃区覆盖/越界。证据：分配表与生命周期峰值 |
| C0-03 物理流量与预算闭合；第 2/5/6 章 | 从合法计划生成每接口/方向/core/阶段轨迹；计算 Q、w、P_req、C；沿 DAG 核对请求和共享资源预算，标出不满足的行 | bytes、命令/同步数、服务需求、预算缺口 | 0 个未记账路径；每项需求可追溯到完整任务；关键路径/持续资源预算可闭合。w 或上层目标未冻结时只交付参数化推导，不判性能通过。证据：预算行、DAG 与轨迹 |

C0-01～03 均针对同一计划独立检查，通过后才进入服务验证。第 5.4 节 M48 MXFP8＋64 KiB state head 的组合应由 C0-02 识别为超容量，而不是因为单命令合法就通过。

### 9.3 L1：基础资源服务

前置为 C0-01～03。使用上层计划的真实地址/并发轨迹，也保留独占基准以便归因；对 bank 冲突等压力输入，门槛来自该输入对应预算，不要求所有地址都达到理论峰值。

| Case / 上层需求 | 输入与可执行步骤 | 观测量 | 数值判据与证据 |
|---|---|---|---|
| C1-01 local SRAM bank 服务；算子/层供数 | 回放 GEMM A/B/scale/C、VPU、DMA、peer 组合；分别执行均匀 bank、同 bank、窄 scale、读写竞争与实际 tile 地址 | 有效 bytes/s、bank 请求/完成槽、首响应、完成时间、数据 | 每口 B_obs≥Q/w、首响应≤l、组合完成≤w，搬运数据精确一致；128 GB/s 仅为条件化结构上限。证据：bank 轨迹、仲裁/反压与计数 |
| C1-02 DMA 完整搬运；装载/写回窗口 | 用实际布局执行 DRAM↔local、peer↔local、local→local；覆盖短/满 burst、合法非对齐、consumer 反压和多在途；比对源/目的 bytes | 两侧流量、首响应/最终可见、返回缓冲峰值、payload | 搬运 0 byte mismatch；B_obs≥Q/w、首响应≤l、全链完成≤w；缓冲≤配置，未完成前不复用。证据：请求/响应、地址与完成轨迹 |
| C1-03 system/peer/SoC SRAM 服务；共享/分片阶段 | 在冻结拓扑上回放多 core 搬运、共享 refill/read、CPU 交接与 peer 路由；分别测独占及声明并发组合 | 各汇聚口/方向 bytes、共享峰值、往返、等待与所有权 | 各口和组合满足 Q/w、l、w；数据交接正确，C_shared≤冻结容量；不将 core 口相加视为聚合服务。证据：路由、共享分配与并发 trace |
| C1-04 DRAM 持续服务；权重/KV/state 阶段 | 在目标 SKU/时钟下回放实际权重、KV/state 读写组合；加入冻结的其他 SoC 负载，持续记录完成与返回 | controller payload、读写比例、延迟、fabric/DMA/consumer 各段服务 | NPU 可用服务≥实际 Q/w，首响应≤l、阶段完成≤w；若需求超过实测能力则预算失败，不以 17.064/34.128 GB/s 理论值替代。证据：多层接口计数与原始时间样本 |
| C1-05 GEMM 有效拍能力；架构 P001～P005 与投影计算预算 | 在合法、充分供数配置下测各精度有效计算拍；再执行真实 tile，分解 load/compute/drain；INT/MX、W4A8 单独运行 | 有效 MAC/cycle、有效拍/总拍、装载/排空、输出数值 | 8-bit/W4A8 为 512 MAC/cycle/core，同格式 INT4/MXFP4 为 1024；真实 tile 按自己的完成窗口核验，不要求全命令平均等于峰值。证据：有效拍定义、输出与阶段周期 |
| C1-06 VPU 基础能力；架构 P006/P007 与向量预算 | 按架构无访存反压条件执行 FP32/FP16 FMA；另执行标量 a/b 的 y=a*x+b 流式输入输出与实际向量轨迹 | FMA/cycle、输入输出 bytes、完整时间、数值 | 无反压稳态 FP32=8、FP16=16 FMA/cycle/core；流式服务按 Q/w 与数值预算，不要求达到寄存器峰值。证据：模式、输入/输出与周期/访存计数 |

峰值 case 与应用服务 case 必须同时保留：前者说明硬件计算能力，后者说明合法数据计划能否供数并按时完成。

### 9.4 L2：算子与独立子图

每个实例前置其实际使用的 L1 资源 case。性能从输入数据就绪计至完整输出可被后继消费，包括转换、装载、运算、排空和必要同步；数值逐项按 `E_i*` 核验。

| Case / 前置资源 | 固定输入与执行步骤 | 观测量 | 判据与证据 |
|---|---|---|---|
| C2-01 完整投影/卷积映射；C1-01/02/05 及实际外部通路 | 执行计划中的小 M、M 上限、N/K 尾块、down projection 和 depthwise 代表形状；按真实循环含 partial sum 与量化边界 | 输出、有效/补齐 MAC、命令数、各口 Q、C_peak、全时间 | 输出满足 E_i*；Q_obs≤计划预算、C_peak≤C_i*、全时间≤w_i；不只测已驻留 GEMM。证据：参考输出、命令/周期与分配 |
| C2-02 norm/conv/门控/归约/特殊函数；C1-01/06 与实际搬运 | 对冻结张量执行 RMSNorm、短卷积、RoPE、softmax 等实际 kernel；覆盖长归约、真实动态范围与数值边界 | 误差、特殊函数/归约工作、多遍读写、scratch、全时间 | 各误差项满足 E_i*，实际 bytes/workspace 在预算内且完成≤w_i；FMA 峰值不作为特殊函数服务判据。证据：kernel 输入/输出、多遍访问与周期 |
| C2-03 MX 量化与布局链；C1-01/02/06 及选定实现资源 | FP32/FP16/BF16 输入分别运行 MX8/MX4 实现；覆盖 block/scale 边界、动态范围、尾块和 padding；输出交给实际消费者 | scale/编码、数值误差、扫描次数、输入/输出 bytes、staging 与完成 | 按独立量化参考满足 E_i*；转换链全部可执行、Q/C 在预算内、完成≤w_i；只有量化阶段快不能通过。证据：参考编码、全链 trace |
| C2-04 递推状态/chunk；C1-01/02/06 及选定 GEMM/外部资源 | 固定 state 与输入，执行单 token 更新及 chunk 方案；按实际 head/子矩阵分片，测试 chunk 前后连续性和状态写后读 | state/输出误差、状态读写次数、快照、C_peak、串行服务 | state 和输出满足 E_i*，下一消费者读到本步状态，Q/C≤预算、更新/chunk 完成≤w_i。证据：独立递推参考、状态交接与生命周期 |
| C2-05 完整 Attention；实际使用的 C1-01～06 | 冻结 query/KV heads、S、精度与因果 mask；执行 QKᵀ→softmax→p·V，含量化、KV 复用和输出转换 | 数值、KV/score/probability 流量、临时区、全服务 | 输出满足 E_i*，Q/C≤预算，完整 attention≤w_i；GQA 逻辑 KV 小不证明只读一次。证据：子图参考与物理访问 |
| C2-06 MLA 方案对照；实际使用的 L1 case | 冻结 latent/RoPE 配置与相同 cache 初态，运行展开和权重吸收候选的 prefill/decode 子图；包含全部压缩/重建与布局转换 | 子图/缓存数值、实际权重/latent bytes、workspace、服务 | 各方案满足 E_i*、Q/C 与独立子图 w_i；与各自推导一致；缺配置/实现/阈值时待冻结。证据：等价性参考、两套计划与子图时间线 |
| C2-07 专家路由/查表/检测后处理；C1-01/02/03/06 及实际通路 | 分别冻结 MoE 路由分布、逐层 embedding 索引、空/密集检测输出；执行实际 gather/scatter、框解码、top-k、过滤与适用 NMS | 索引/结果正确性、非连续流量、失衡、输出规模、workspace、全时间 | 分别满足各 E_i*、Q/C 与 w_i；YOLO26n 不执行 NMS 也要完成其全部输出处理。证据：每类独立输入参考、访问与服务记录 |

C2-07 的三类实例不互相替代，分别绑定所属模型与执行路径。未使用某资源的子图无需依赖其测试；实际使用却未通过的资源不能省略。

### 9.5 L3：层、流水与调度

前置为 C0 和所用 C1/C2 case。以完整单层、chunk、检测帧阶段为单位，将独占模块实测放回真实并发条件。

| Case / 上层需求 | 执行步骤 | 观测量 | 判据与证据 |
|---|---|---|---|
| C3-01 单层/chunk/检测阶段流水；完整任务阶段预算 | 执行完整阶段 DAG，回放当前计算、下一预取、上一写回、VPU/state 同时活跃；baseline/candidate 使用相同输入 | 联合时间线、暴露 load/VPU/state、bank 竞争、C_peak、输出 | 数值正确；阶段完成≤w_i、所有活跃区/流量≤预算；重叠有数据就绪和端口证据，不能由 max 时间推定。证据：多引擎 trace 与生命周期 |
| C3-02 控制与数据交接；提交/同步窗口 | 从主 CPU 任务提交到 local CPU 执行，硬件 scheduler 跑 GEMM/DMA、local CPU 直控 VPU；覆盖 slot 等待与跨消费者交接 | 排队、命令准备/可发、同步、可见、任务完成与缓冲释放 | 0 次错误依赖/提前复用；暴露控制等待≤分配窗口；完整结果可见再记完成。证据：CPU/任务/引擎事件与输出 |
| C3-03 多核/共享数据方案；分片与持续资源预算 | 同输入比较单请求分片、私有/共享数据；另按冻结到达序列执行多请求；包含状态、输出拼接与共享 refill | 各 core 工作、peer/system/共享 Q、同步、C_peak、单请求与聚合服务 | 最繁忙 core 与共享资源均满足其预算，数值一致；只对匹配 profile 比较收益，不用聚合吞吐证明单请求扩展。证据：分片图、并发 trace 与服务样本 |

### 9.6 L4：完整任务与质量复核

前置为该任务所用的 C0～C3 case。服务目标已批准也不能省略输入、量化 recipe、运行入口与统计方案的冻结。

| Case / 目标来源 | 可执行步骤与 profile | 观测量 | 数值判据与证据 |
|---|---|---|---|
| C4-01 文本 prefill/decode；第 1.3 节与架构 P008/P010/P012 | MX8/MX4、G0/Pro 分别执行 P512 和 D2048；记录完整首 token 与 256 步输出，另扫 L/S；warm/cold、单请求/并发分开 | TTFT p95、平均 decode、TPOT p95、逐步 S、接口 Q/C、关键路径 | 批准模型按各自冻结 T_first*、R_decode*、T_step,p95* 判定；当前 LLM 服务数值待确认，不签性能通过。证据：完整原始请求/step 样本与关联 trace |
| C4-02 完整检测；YOLO11n 架构 P014/P015，YOLO26n 独立目标 | COCO val2017、640×640、batch1；连续运行预处理→网络→完整后处理→返回，纳入空/密集目标与声明系统负载 | 持续 fps、完整帧 p95、预/后处理、帧流水、Q/C | YOLO11n G0≥30 fps/p95≤50 ms，Pro≥48 fps/p95≤35 ms；YOLO26n 的数值先冻结，不套用。证据：帧入口/完成样本、结果与全链服务 |
| C4-03 数值/模型质量；架构 P020～P023，扩展模型独立判据 | 先用独立 MX 参考验逐算子，再运行同源 BF16 语言语料/任务及同源浮点检测；固定校准/量化 recipe、数据 hash、采样 | 逐算子误差、状态/任务一致性、perplexity、准确率或 mAP50–95 | 批准语言模型 perplexity 相对增幅≤5%、准确率绝对下降≤1 个百分点；检测 mAP50–95 绝对下降≤1 个百分点；E_i* 全部满足。其余模型另冻判据，不能用 perplexity 替代完整生成质量。证据：独立参考与逐项质量报告 |
| C4-04 SKU/优化收益复核；架构 P018 与方案收益声明 | 同 artifact/profile/输入分别执行 G0/Pro 的单请求 decode；对优化用同 SKU 的 baseline/candidate 再跑全部相关功能、质量、资源与服务 case | 实测速比、扩展效率、完整时延/吞吐、Q/C 与瓶颈变化 | 批准单请求 decode Pro/G0≥1.6、效率≥0.8；优化收益按预先声明的数值目标判定，质量/容量/相关服务不退回。证据：成对完整任务样本，不以模块加速比代替 |

只有该实例的服务、质量、数据计划和所用依赖 case 均通过，才关闭完整任务。MLA 的终点为 C2-06 的独立子图验收及必要 C3 组合验证，不伪装成 C4 完整模型通过。

### 9.7 依赖关系、具体预算例与关闭条件

```text
冻结请求目标和输入 → 分配阶段/接口窗口 → C0-01/02/03
→ 所用C1基础资源 → 所用C2算子/子图
→ 所用C3流水/控制/多核 → C4服务＋质量＋收益复核
```

这是验证依赖，不是要求等待硬件实测后才做理论预算。预算先定义需求，底层测试给出能力；不满足时修改计划或重新审定目标，再重跑受影响的上层 case。

以 Qwen3.5-2B down projection/state 服务为例，一条关闭链应包含下列结果：

| 推导/执行环节 | 具体结果或判据 | 对应 case |
|---|---|---|
| 投影分块容量 | M32/N16/K6144 的 MX8 tile 为 398 KiB；G0 其余同时活跃区合计必须≤114 KiB | C0-02、C2-01 |
| state head 容量与流量 | FP32 单 head=64 KiB；若每次读写各一次，合计 128 KiB；驻留/寄存器复用改变时重算 | C0-02/03、C2-04 |
| 层状态服务需求 | 16 heads 合计读写 2 MiB；若分配状态窗口为 w_state 秒，所用链需服务≥2,097,152/w_state B/s，并完成全部运算与交接≤w_state | C1 相关通路、C2-04 |
| 投影计算需求 | 此 M32 tile 为 32×6144×16=3,145,728 MAC；仅算术项在 0.256 TMAC/s 下的理想下界为 12.288 μs，不能替代装载/输出完成窗口 | C1-05、C2-01 |
| 组合是否可重叠 | 回放投影、state、预取与写回的实际 DAG，检查剩余容量、bank 槽和依赖；不预设同层两个阶段并行 | C3-01 |
| 对请求的影响 | 用同 artifact/profile 的完整样本核验 TTFT/TPOT；state 与投影的暴露时间可追溯到下层预算 | C4-01/03 |

`w_state` 和请求目标未冻结时，这条链只能证明具体 bytes/容量和参数化需求，不能给出虚构 GB/s 或 PASS。状态计算也可能先于带宽达到瓶颈，必须同时验完整运算窗口。

失败按“输入/参考不一致、计划非法或超容量、底层服务不足、数值/算子超预算、并发/调度暴露成本、完整服务/质量失败”分开归因。实测 Q 超过计划时先检查重读、转换和副本；实测时间与预测不符时回溯时间线，不通过修改理论公式来拟合遗漏。

关闭记录包含 case 实例、目标/预算版本、结果、原始证据、差异解释和受影响的上层复测范围。缺数值目标、缺运行入口或只有理论估算的 case 保持待冻结/未执行状态；底层通过不自动关闭 L4。

## 附录 A：分析方法与资料索引

| 来源 | 借鉴内容 | 使用边界 |
|---|---|---|
| [NVIDIA LLM inference benchmarking](https://developer.nvidia.com/blog/llm-inference-benchmarking-performance-tuning-with-tensorrt-llm/) | 输入/输出长度、并发、TTFT/TPOT、单请求与聚合吞吐 | 借鉴指标组织，不移植 GPU 实测或软件能力 |
| [NVIDIA Nsight Compute Profiling Guide](https://docs.nvidia.com/nsight-compute/ProfilingGuide/index.html) | 分层 roofline、有效/峰值吞吐、事务效率与停顿 | 借鉴取证方法，不把 CUDA 工具当作 Hive 已有运行入口 |
| [模型拆解目录](model_analysis/) | 第 1.2 节逐层算法、形状与模型账目 | 源文档的精度建议、参考机器速度与“必须驻 SRAM”不自动成为 Hive 要求 |
| [总体架构](arch.md) | 当前计算/接口/容量、共享 SRAM 和批准服务目标 | 规格、实现候选与实际测量分开 |
| [DMA 参考规格](../../foragerbee/doc/spec.md) | 简单搬运与量化的阶段结构限制 | 独立参考，不代表本轮已验证 Hive 集成 |

## 附录 B：VPU roofline 与公开产品配比

<a id="2-vpu-配比选型与工作负载分析"></a>

### B.1 单口流式 roofline

VPU 的 256-bit local 单口在 500 MHz 下读写合计为 16 GB/s。`y=a*x+b` 若 a/b 为寄存器标量，每元素读 x、写 y，FP32 为 8 B、FP16 为 4 B，各贡献 2 FLOP；理想流式上限只有 4/8 GFLOPS，低于寄存器内 FMA 峰值 8/16 GFLOPS。

若 a/x/b 都是独立向量，加输出共 16 B/FP32 元素，算术强度进一步下降。执行宽度不能直接换算总访存需求，也不能只算一条操作数流。

| VPU 500 MHz 档位 | FP32 / FP16 峰值，每 core | 状态 |
|---|---|---|
| 256-bit | 8 / 16 GFLOPS | 当前架构基线 |
| 512-bit | 16 / 32 GFLOPS | 扩宽敏感性选项，未批准 |
| 1024-bit | 32 / 64 GFLOPS | 扩宽敏感性选项，未批准 |

FP16 峰值要求双倍有效 FMA 元素吞吐，不是仅支持 FP16 类型。扩宽前先检查真实 kernel 的特殊函数、归约、寄存器复用与 local 供数，不能只用峰值比例选型。

### B.2 公开产品资源比例

保留此前公开资料核验快照（2026-10-26）。GPU 通用 CUDA 算力不是独立 VPU，整数矩阵与浮点向量峰值比也不是同精度加速比。

| 产品 | GPU INT8 稠密矩阵峰值 | 通用 FP32 | 通用 FP16 | INT8 / FP32、FP16 |
|---|---:|---:|---:|---|
| Jetson AGX Orin 64GB | 85 TOPS | 5.3 TFLOPS | 10.6 TFLOPS | 约 16:1、8:1 |
| Jetson Orin Nano 8GB（Super） | 33 TOPS | 2.1 TFLOPS | 4.2 TFLOPS | 约 15.7:1、7.9:1 |
| Jetson AGX Xavier GPU | 22 TOPS | 约 1.403 TFLOPS，推导值 | 未独立核验 | 约 15.7:1、不可计算 |

Orin 取[官方同型号表](https://www.nvidia.com/en-us/autonomous-machines/embedded-systems/jetson-orin/)的 GPU Tensor Core INT8 dense 与 GPU CUDA 峰值，不计 sparse 或 DLA。[Xavier 架构资料](https://developer.nvidia.com/blog/nvidia-jetson-agx-xavier-32-teraops-ai-robotics/)的 FP32 按 512 CUDA cores×1.37 GHz×2 推导；11 FP16 TFLOPS 为矩阵口径，不填通用 FP16 列。[Ethos-U85](https://www.arm.com/products/silicon-ip-cpu/ethos/ethos-u85)在此前核验中没有独立 FP32/FP16 向量峰值，因此不计算该比例。

这些比值只供资源比较，不构成行业统一配比或 Hive 扩宽依据。

## 附录 C：YOLO 双检测路径的分析入口

<a id="4-yolo-双检测模型基线与评估"></a>

[YOLO11n](model_analysis/YOLO11n-逐层计算拆解.md)与[YOLO26n](model_analysis/YOLO26n-逐层计算拆解.md)均已有模型拆解。Hive 分析需进一步核验冻结导出图、W/A 全 MX 的算子映射、实际分块/流量、活跃内存与完整服务。

| 模型 | 路径差异 | 需要独立验证的内容 |
|---|---|---|
| YOLO11n | DFL＋NMS 基线 | 多尺度 feature map、concat/融合活跃区、depthwise 利用率、DFL 与 NMS workspace |
| YOLO26n | 移除 DFL 的 one-to-one NMS-free 检测头 | 框解码、top-k、gather、过滤、输出规模；移除 NMS 不省略全部后处理 |

两者都计入 letterbox/归一化与坐标还原，空检测和密集目标单独覆盖。实际算子在图内还是图外按 exporter/runtime 冻结，但不改变完整检测的计时边界。

YOLO11 的公开入口为[官方检测表](https://docs.ultralytics.com/models/yolo11/)与[官方检测图](https://github.com/ultralytics/ultralytics/blob/main/ultralytics/cfg/models/11/yolo11.yaml)。此前核验（2026-10-27）发现官网与主分支注释 FLOPs 口径有差异，不能用厂家参数量推定 activation 峰值。YOLO26n 的版本化配置与导出证据按其拆解继续核验；官方浮点权重不作为现成 MX 部署。
