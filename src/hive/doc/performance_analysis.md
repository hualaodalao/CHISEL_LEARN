# Hive NPU 性能分析

文档版本：`v0.4.0-draft`

修订日期：`2026-10-28`

> 本文记录非规范性的性能推导、敏感性分析和公开产品数据。架构能力、接口和验收要求以[总体架构](arch.md)为准；代际产品演进、竞品定位、成本和里程碑以[架构路线图](arch_roadmap.md)为准。本文中的候选配置和算例不构成 RTL、PPA 或产品承诺。

## 1. 文档边界

本文用于回答性能选型中的“为什么”和“如果怎样”，包括：

- 公开产品资源配比能否作为本项目参考；
- VPU 执行宽度变化对峰值、供数、面积和功耗的影响；
- 工作负载对普通向量、特殊函数、归约和 attention 的需求；
- 计算峰值与 local SRAM 流式带宽之间的 roofline 约束；
- P0-5/P0-6 两个 FP8 语言模型与同源 BF16→MXFP8/MXFP4 分支的数值、容量、流量和服务阶段门；
- YOLO11n/YOLO11s 双检测基线的完整流程、算子落点与数值/服务候选。

本文不定义 VPU ISA、接口协议、错误语义或产品代际。首版规范目标见[总体架构第 4.4 节](arch.md#44-vpu-职责与精度需求)和[第 12.7 节](arch.md#127-vpu-性能指标与接口要求)。

2026-10-28总体架构新增批准官方[Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B)模型身份，G0工作负载范围扩至名义0.5B–2B。本文件现有语言模型算例仍只覆盖Qwen3-0.6B与DeepSeek-R1-Distill-Qwen-1.5B，未完成Qwen3.5-2B的精度、算子、状态容量或流量分析；既有KV、MAC和服务/质量门槛不能直接套用。新模型的执行格式、量化及服务/质量标准须独立评审，身份获批不代表4 GiB容量或当前硬件已经足以部署。

## 2. VPU 配比、选型与工作负载分析

当前批准基线为每 core 256-bit 等效执行宽度、500 MHz、FP32 8 GFLOPS / FP16 16 GFLOPS，以及一个 256-bit local 数据请求口。以下扩展选项不属于首版要求，也不代表外部 VPU 已实现。

### 2.1 公开产品配比与比较边界

公开 NPU 产品常只披露整数 TOPS 或 MAC 数，未披露独立 FP32/FP16 向量峰值，不能据总 TOPS 反推出 VPU 配比。下表用可核验的矩阵与通用浮点能力作参考；GPU CUDA 是通用浮点吞吐的代理，**不是独立 VPU**，也不把 CPU、DLA 或其他引擎相加后作为 GEMM 分子。核验来源见附录 A。

| 产品/口径 | GPU 矩阵 INT8 稠密峰值 | 通用 FP32 峰值 | 通用 FP16 峰值 | INT8 / FP32 | INT8 / FP16 |
|---|---:|---:|---:|---:|---:|
| Jetson AGX Orin 64GB | 85 TOPS | 5.3 TFLOPS | 10.6 TFLOPS | 约 16.0:1 | 约 8.0:1 |
| Jetson Orin Nano 8GB（Super） | 33 TOPS | 2.1 TFLOPS | 4.2 TFLOPS | 约 15.7:1 | 约 7.9:1 |
| Jetson AGX Xavier GPU | 22 TOPS | 约 1.40 TFLOPS，推导值 | 未在本次引用中独立核验 | 约 15.7:1 | 不推算 |

Orin 两行取厂家同一型号表的 `GPU Tensor Core INT8 Performance` 稠密值与 `GPU CUDA Core Performance`，不使用 170/67 sparse TOPS，也不使用整机含 DLA 的 AI TOPS。Xavier 的 FP32 按厂家披露的 512 CUDA cores、1.37 GHz，以及每核每拍一个 FP32 FMA（2 operations）推导为 1.403 TFLOPS；厂家列出的 11 FP16 TFLOPS 是矩阵峰值，不能填入通用 FP16 列。型号、频率、功耗档和支持精度不同，公开峰值的比不保证同时持续实现。

Arm Ethos-U85 页面披露 128–2048 MAC 和最高 4 TOPS，但未给独立 FP32/FP16 向量 GFLOPS，故不计算 GEMM/VPU 比。以上不构成“行业统一 16:1”标准；本项目 FP8/INT8 与 FP32/FP16 的数值配比也不能当成同精度算法加速比。

### 2.2 执行宽度与扩展档位

以 VPU 500 MHz、每个 FP32 元素每拍一 FMA 为演算基准，单精度峰值为 `FP32_elements_per_cycle × 2 × f_VPU`。FP16 翻倍要求每拍处理两倍有效 FP16 FMA，不是仅支持 FP16 类型；FP16 输入/FP32 累加路径的吞吐单独统计，两种精度的峰值不同时相加。

| 档位 | 等效执行宽度 | FP32 / FP16 FMA/cycle/core | 每 core FP32 / FP16 | 两核聚合 FP32 / FP16 | 四核聚合 FP32 / FP16 | INT8/FP8 GEMM / VPU FP32、FP16 |
|---|---:|---:|---:|---:|---:|---|
| 首版批准基线 | 256 bit | 8 / 16 | 8 / 16 GFLOPS | 16 / 32 GFLOPS | 32 / 64 GFLOPS | 64:1、32:1 |
| 扩展选项 | 512 bit | 16 / 32 | 16 / 32 GFLOPS | 32 / 64 GFLOPS | 64 / 128 GFLOPS | 32:1、16:1 |
| 更高吞吐选项 | 1024 bit | 32 / 64 | 32 / 64 GFLOPS | 64 / 128 GFLOPS | 128 / 256 GFLOPS | 16:1、8:1 |

256-bit 是当前首版批准起点。512/1024-bit 只保留为性能评估选项，不因 Orin 的公开配比相近就升格为需求。同格式 INT4/MXFP4 的 GEMM 峰值翻倍也不自动要求 VPU 翻倍。

计算宽度不是访存带宽：每拍 32 个 FP32 元素的一条操作数流为 1024 bit，500 MHz 下是 64 GB/s，但 FMA 可能有多条输入流及输出流，也可能通过寄存器复用减少 SRAM 访问。是否扩宽执行能力须同时比较算子服务时间、寄存器/缓冲成本、供数、面积和功耗；当前没有 PPA 数据证明某个扩展档具有产品收益。

### 2.3 工作负载与时延敏感性

对每个精度分别统计普通运算量 `F_V`、允许的向量阶段时间 `t_V` 和可达利用率 `u_V`，所需峰值可按 `P_V_peak ≥ F_V/(t_V×u_V)` 估算。若允许 GEMM 与 VPU 流水重叠，还需检验 `P_V_peak×u_V ≥ rho×P_G_peak×u_G`，其中 `rho=F_V/F_G` 来自同一任务的实际算子统计；没有重叠时，GEMM/VPU 时间相加。

仅作敏感性演算：普通向量工作量若占矩阵 operations 的 2%，GEMM/VPU 可达利用率分别为 50%/25%，每 core 的 GEMM 峰值为 512 GOP/s，则所需向量峰值约 `0.02×0.50/0.25×512=20.48 GFLOPS`。这个假设下 8 GFLOPS 基线不能满足完全重叠条件，不能据此宣称首版无 VPU 瓶颈；2% 和利用率均非模型实测，也不能据此认定首版必须采用 1024-bit。exp、rsqrt、除法与归约串行延迟另行建模。

采用第 3.1 节两个已批准模型，记 `Q=query_heads×head_dim`、`E=kv_heads×head_dim`，context=2048。Qwen3 的 Q=2048 不等于 H=1024，RoPE 和 attention 不应按 H 代替 Q。

| 普通向量工作 | 每 token 的量级公式 | 边界 |
|---|---|---|
| 两次 RMSNorm/层 | `2×layers×(4H+2)` | 未含 final norm、Qwen3 Q/K norm 与全部调度开销 |
| Q/K RoPE | `3×layers×(Q+E)` | 每二元组四乘两加，旋转系数已备好 |
| SwiGLU | `4×layers×I` | exp 另计 |
| softmax 普通运算 | `4×layers×query_heads×context` | 临时预算，不等于实际指令数 |

两模型上述普通运算分别约 **4.50M / 4.25M operations/token**，SiLU/softmax 分别约 **1.004M / 0.939M exp/token**。未含 residual、转换、bias、量化辅助和 checkpoint scale 桥接；这不是完整 VPU 指令量。以第 3.6 节 G0 候选 6 / 2.5 token/s 演算，exp 平均需求约 6.02M / 2.35M 次/s；若只允许 20% token 时间给这一阶段，所需阶段能力增加五倍，且并非必然均分到两核。

decode 普通运算可能远低于 FMA 峰值，但特殊函数、归约、动态量化及访存仍可限制端到端性能；不能用 GFLOPS 替代完整 kernel 服务时间。

### 2.4 FP16 attention 回退的额外负担

若 FP16 VPU 承担完整 QKᵀ 与 probability×V，decode 约为 `4×layers×Q×context` FLOP/token，causal prefill 约为 `2×layers×Q×S×(S+1)` FLOP。以下仅以首版 VPU 满利用率计算下界，不含 softmax、供数、量化与调度。

| 模型 | context=2048 decode | S=512 prefill | G0 prefill 理想下界（32 GFLOPS） | Pro 下界（64 GFLOPS） |
|---|---:|---:|---:|---:|
| Qwen3-0.6B | 0.470 GFLOP/token | 30.123 GFLOP | 0.941 s | 0.471 s |
| DeepSeek-R1-Distill-Qwen-1.5B | 0.352 GFLOP/token | 22.593 GFLOP | 0.706 s | 0.353 s |

G0 decode 理想 attention 时间分别为 14.68 / 11.01 ms/token；实际 FP16 输入/FP32 归约吞吐、寄存器复用和 KV 重读会提高时间。若 attention 落到低精度 GEMM，必须另验 Q/K/V、概率量化、累加与精度；不能默认沿用 FP16 语义或同格式 MXFP4 峰值。GQA 的 KV heads 减少存储，不减少 query heads 对应的 attention 运算量。

### 2.5 访存 roofline 与选型触发条件

一个 256-bit local 请求口在 500 MHz 下每拍最多一个读或写时，读写合计峰值为 16 GB/s，而非读 16 + 写 16 GB/s。以寄存器内标量系数的 `y=a×x+b` 为例，每元素读 x、写 y，FP32 需 8 B、FP16 需 4 B，各贡献 2 FLOP；理想流式上限分别仅为 **4/8 GFLOPS**，未扣仲裁和 bank 冲突。因此 256-bit 执行的 8/16 GFLOPS 也不是单口流式保证。

| FP32 标量系数 FMA 的持续目标 | 最低有效读写合计带宽 | 比较条件 |
|---|---:|---|
| 8 GFLOPS | 32 GB/s | 不能由首版单口连续流式供数达成 |
| 16 GFLOPS | 64 GB/s | 执行扩到 512 bit 后仍需供数或复用 |
| 32 GFLOPS | 128 GB/s | 执行扩到 1024 bit 后仍需供数或复用 |

若 a、x、b 三个输入均为独立流式 FP32 向量，连同输出为 16 B/元素、2 FLOP，32 GFLOPS 则需 256 GB/s。流量模型必须按实际输入/输出和寄存器复用统计，不用单条 1024-bit 数据流的 64 GB/s 代替总带宽。

演进优先检查寄存器/局部缓冲复用和算子融合，减少中间结果落 SRAM；仅在冻结工作负载的向量服务时间不达标时，比较特殊函数加速、归约优化、执行扩宽与端口扩展。拓宽数据路径必须同步重算总体架构第 12.3 节的请求压力、GEMM/DMA 并发、bank 冲突和 PPA。GFLOPS、exp 启动间隔、归约延迟与完整 kernel 服务时间分别测量，不以更大 FMA 阵列作为所有瓶颈的答案。

## 3. P0-5/P0-6 双模型重评

### 3.1 模型身份、配置与版本

2026-10-27 已批准两个 FP8 signoff 模型与同源 BF16→MXFP8/MXFP4 评估路线；以下 revision 是本轮公开资料的核验快照，正式 manifest 仍须记录文件 hash、tokenizer、runtime 和生成 recipe。既有 checkpoint 的 FP8 发布格式不等于 GEMM 原生可直接运行。

| 模型 / artifact | 核验 revision | 权重文件字节 | 量化身份 |
|---|---|---:|---|
| `Qwen/Qwen3-0.6B-FP8` | `e5be08033360965ceca7b0ffd72d521a51331ce0` | 1,062,975,744 | 官方 FP8 E4M3；weight block128×128、dynamic activation，含 BF16 tensor |
| `RedHatAI/DeepSeek-R1-Distill-Qwen-1.5B-FP8-dynamic` | `b87f998fcda08540b81b50844bcdb811540db5e8` | 2,245,332,464 | 社区 FP8 E4M3；weight channel、activation token，LM head 未量化 |
| `Qwen/Qwen3-0.6B` | `c1899de289a04d12100db370d81485cdf75e47ca` | 1,503,300,328 | MX 自量化的原始 BF16 源，不是部署完成的 MX checkpoint |
| `deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B` | `ad9f0ae0864d7fbcd1cd905e3c6c5b069cc8b562` | 3,554,214,621 | 同上；不是 MLA/MoE 模型 |

| 配置 | Qwen3-0.6B | DeepSeek-R1-Distill-Qwen-1.5B |
|---|---:|---:|
| layers / hidden H / intermediate I | 28 / 1024 / 3072 | 28 / 1536 / 8960 |
| query heads / KV heads / head dimension | 16 / 8 / 128 | 12 / 2 / 128 |
| query width Q / KV width E | 2048 / 1024 | 1536 / 256 |
| vocab / embedding 与 LM head 绑定 | 151936 / 是 | 151936 / 否 |
| 主干 Linear 权重元素 P | 440,401,920 | 1,310,195,712 |
| LM head 权重元素 V×H | 155,582,464 | 233,373,696 |
| 主干＋LM head MAC/token | 595,984,384 | 1,543,569,408 |
| FP16 KV 字节/token | 114,688（112 KiB） | 28,672（28 KiB） |
| FP16 KV：2048 / 4096 tokens | 224 / 448 MiB | 56 / 112 MiB |

主干元素按 `P=layers×(2H×Q+2H×E+3H×I)`，LM head 另计 `vocab×H`。embedding 查表不扫描全表，但绑定的 LM head 仍执行完整投影；不因 tying 删除这一计算。Qwen BF16 文件含绑定权重的存储副本，能否去重须校验 tensor 等值和导出布局，不能以逻辑唯一参数量替代文件容量。模型名中的 0.6B/1.5B 不是严格存储上限。

### 3.2 FP8 scale 与同源 MX 执行分支

| 路径 | 必须完成的映射 | 当前判断 |
|---|---|---|
| Qwen 已有 FP8 | weight scale 随 K 的128元素块变化；activation 分组/scale 以冻结 runtime 为准 | 不可丢弃 scale 或只在完整 K 归约后乘一个标量；候选是按 scale 边界分 K 子命令，FP32 输出缩放后显式累加，增加启动、C 写回与 VPU 流量 |
| DeepSeek 已有 FP8 | 对主干 Linear 保留 per-token activation 与 per-output-channel weight scale | 数学上可在不跨 scale 域的归约后做缩放，仍需校验 bias、动态量化及实际 runtime；不是当前系统已实现的保证 |
| 两个 FP8 的 BF16 层 | embedding、norm、LM head 及其余未量化 tensor 单列 | GEMM 默认不接收 BF16；候选是 VPU/片内 CPU 的合法精度实现，全部计入时延、带宽、功耗；不得静默重新量化后沿用原 FP8 成绩 |
| 同源 BF16→MXFP8 | 主干 A/B 沿 K 每32元素一个 E8M0 scale；元素 E4M3/E5M2 候选，C FP32 | 已批准评估路线；先验证格式/布局/数值及 activation 运行时量化，再测性能 |
| 同源 BF16→MXFP4 | 同样 block32，元素 E2M1、C FP32；首轮保留 embedding/LM head 的 BF16 参考精度 | 离线权重量化与动态 activation 量化分别交付；A/B 同为 MXFP4 才适用翻倍峰值，A8/B4 不适用 |
| 后续本地 MX C | T=16 时 C block16；下一算子的 A 沿 K block32 | 两个独立 scale 的16元素块合成32元素块通常需重新量化，不能拼接冒充标准 MX；另验轴转换、误差和服务成本 |

本轮源码核验 A/B MX 支持 block16/32；T=16 时 C MX block32 超过输出几何，首阶段 C FP32 避开这一限制。标准 MX 的 block32 不要求扩大首阶段 GEMM 输入 block。NVFP4 包含每16元素的 E4M3 block scale，以及额外的 tensor/global scale；当前 Hive 不支持这一额外缩放级。MX 使用 E8M0 block scale，不依赖 NVFP4 第二级 scale，本地 E8M0 MX block16 也不等于 NVFP4。不能直接删除第二级 scale 或把 NVFP4 字节改名为 MX；若转换须另立 artifact、重新量化并验数值和服务。

建议每个 BF16 源各导出 MXFP8 与 MXFP4，共四个独立 artifact。recipe 必须明确权重/activation 分组轴、元素格式、E8M0 scale 选择、RNE/饱和、非有限值/下溢、padding、未量化层、校准语料与 hash、样本长度/数量、seed、工具版本；这些细项尚未批准。先作矩阵与完整模型数值检查，不在 G0 上常驻 BF16 源执行量化，不将已有 FP8→MX 的再量化混作同源 BF16 分支。

### 3.3 DRAM 流量、容量与在途服务

以 batch=1、每 token 主干权重读取一次、LM head 保持2 B/element、KV FP16 为条件；暂不计 scale、padding、重复访问、activation、中间结果和其他主设备。理想 MX 主干字节分别为 `P×(1+1/32)` 和 `P×(0.5+1/32)`，其中每32元素含一个1 B scale。

| 规划量 | Qwen3-0.6B | DeepSeek-R1-Distill-Qwen-1.5B |
|---|---:|---:|
| FP8 decode 权重流（主干＋BF16 LM head） | 0.752 GB/token | 1.777 GB/token |
| MXFP8 权重流，含理想 block32 scale | 0.765 GB/token | 1.818 GB/token |
| MXFP4 权重流，含理想 block32 scale | 0.545 GB/token | 1.163 GB/token |
| FP8 权重＋2048-token KV 读取 | 0.986 GB/token | 1.836 GB/token |
| NPU 可用8 GB/s下的上述流量上界 | 8.11 token/s | 4.36 token/s |
| NPU 可用16 GB/s下的上述流量上界 | 16.22 token/s | 8.72 token/s |

这里的 KV 是理想读取一次的下界；GQA head 复用不足、块访问、score 落内存、scale 桥接、写回和多核重复读都会增加流量。8/16 GB/s 是用于重评的 NPU 有效 payload 候选，不是纯权重带宽，也不是已批准服务保证。decode 的全文件字节不应计入整张 embedding 查表，但容量仍必须容纳实际部署副本。

| LPDDR4x 4266 MT/s 候选效率 | G0 x32 | Pro x64 |
|---|---:|---:|
| 理论峰值 | 17.064 GB/s | 34.128 GB/s |
| 60% payload | 10.238 GB/s | 20.477 GB/s |
| 70% payload | 11.945 GB/s | 23.890 GB/s |
| 80% payload | 13.651 GB/s | 27.302 GB/s |

建议以 NPU 持续 payload≥8/16 GB/s、同时其他 SoC payload=2/4 GB/s 的场景开始评估；60%效率只提供很小的总流量余量，不能预先保证并发通过。分别测 LPDDR controller、system fabric、DMA、NPU 消费链的瓶颈，不能把主干16/32 GB/s接口峰值当有效 payload。

容量按单模型常驻、batch1、最大活跃 context=2304、FP16 KV 规划：两 FP8 文件加 KV 分别约 **1.236 / 2.153 GiB**。若暂为 OS/runtime/其他 SoC 保留1 GiB、额外 activation/scratch/复制与暂存预算256 MiB，合计约 **2.486 / 3.403 GiB**，G0 4 GiB 在这一条件下可规划，余量分别约1.514/0.597 GiB；不是实际峰值验收，也不承诺双模型并发。候选预算一旦被实际生命周期超出，应调整数据计划或重新评审容量，不能删除开销。

同样保守保留 embedding/LM head 存储副本与 KV，MXFP8 两模型约1.249/2.189 GiB，MXFP4 约1.044/1.579 GiB，尚未包含导出 padding、元数据和量化临时区。DeepSeek BF16 源文件约3.310 GiB，不能在4 GiB设备同时容纳上述系统预留并执行离线量化；MX权重在工作站离线生成，运行时只保留部署 artifact。

对每个活跃 engine，最低在途返回容量随 `BW_required×latency` 增长。256-bit、64-beat burst=2 KiB，四满 burst=8 KiB/engine候选；以总NPU8 GB/s和256 ns举例，cluster延迟覆盖下界2 KiB，而1 μs则为约7.813 KiB，均未计排队、传输阶段与安全余量，也不能直接当每engine FIFO深度。候选1 μs并非实测，须按延迟分布、engine分摊、返回credit与内部服务节奏冻结。

### 3.4 SRAM 分块与生命周期峰值

对完整 K 驻留的保守计划，A 一份、B 两份、C FP32 一份，A/B沿 K block32，所有 K/N 已合法补齐：

```text
A_data       = M×K×a_bytes
B_double     = 2×K×N×b_bytes
MX_scale     = (M×K+2×K×N)/32
C_FP32       = 4×M×N
SRAM_peak    = A_data+B_double+MX_scale+C_FP32
             +previous_writeback+VPU_scratch+parameters+guard+other_live_regions
```

published FP8 的128×128或channel scale按真实布局另计，不套MX scale公式。N 是整个命令的输出分块宽度，不是单独一个 PE；增大N还会增加A重读。以下只列MX8/MX4的数据、scale及一份C，不含尾项，不能据此宣称完整双缓冲可用。

| 模型 down projection K=I | M / N | MXFP8 小计 | MXFP4 小计 | 含义 |
|---|---|---:|---:|---|
| Qwen3，K=3072 | 32 / 16 | 200 KiB | 104 KiB | G0有空间继续规划VPU/上一输出，仍需峰值验证 |
| Qwen3，K=3072 | 32 / 32 | 301 KiB | 157 KiB | 更大N减少命令数，但增加驻留与bank竞争 |
| DeepSeek，K=8960 | 32 / 16 | 579.5 KiB | 299.5 KiB | MX8保守计划已超G0 512 KiB；Pro可继续规划，G0须缩M、减预取或证实流式供给 |
| DeepSeek，K=8960 | 32 / 32 | 870.25 KiB | 450.25 KiB | Pro MX8及G0 MX4余量都小，不能直接使用满M32计划 |

DeepSeek MX8的M16/N16小计约434.125 KiB，仍只剩约77.875 KiB给G0其余活跃区。减M会降低权重复用及阵列有效拍占比，不能为通过容量而沿用M32性能。K流式DMA与完整K驻留是不同计划；没有外部partial sum输入不证明全部B必须先装满。跨K子命令的FP32外部累加和更多结果buffer须按实际生命周期计入。

C FP32每个16元素输出占64 B、两个256-bit写拍；它降低量化输出的不确定性，却增加SRAM流量和VPU重新量化工作。后续C MX block16是否值得引入，应比较这一成本与block16→32桥接成本及数值损失。

### 3.5 算子服务与计算上界

第3.1节的主矩阵MAC不含attention和向量运算，也不能全部套FP8 GEMM峰值：两个LM head在原FP8版本都保持BF16。若以FP16 VPU执行LM head，其理想运算时间在G0约9.72/14.59 ms/token，Pro约4.86/7.29 ms/token，还要加BF16→FP16数值适配、权重读取、归约与调度；FP32实现会有不同吞吐。若保留原BF16算术需要CPU或外部VPU能力，必须显式说明，不能假定已支持。

主干GEMM还受16拍权重装载、排空、M时间轴以及C输出反压制约；M1 decode不能要求与M32/M48 prefill同样的峰值利用率。以M32理想有效拍比例约49%作敏感性估算，512-token主干FP8 prefill在G0约0.895/2.661 s，再叠加第2.4节FP16 attention理想下界约0.941/0.706 s。这些不是实际TTFT：G0 DeepSeek的SRAM可能迫使部分算子用更小M，Qwen FP8 scale跨K桥接可增加命令及累加，完整时延必须重算；并行阶段只在有依赖/资源证据时取max，否则相加。

| 算子/通路 | 优先评估落点 | 必须另测的服务与缺口 |
|---|---|---|
| 主干Linear | FP8或MX GEMM | scale/bias桥接、M/N/K分块、A重读、装载/排空、C两写拍 |
| embedding/norm/RoPE/SiLU/residual | VPU或片内CPU | BF16/FP16/FP32语义、Qwen Q/K norm、特殊函数、归约、tail和访存 |
| QKᵀ / probability×V | FP16 VPU参考；低精度GEMM为独立候选 | KV复用、概率量化、softmax稳定性、完整attention服务，不默认量化KV |
| MX activation量化与FP32 C后处理 | VPU/合法转换实现 | 动态scale求取、两遍访问、axis/布局、MX4量化和后继桥接 |
| DRAM/local/peer搬运 | 通用DMA | 多读在途、有效字节、内部串行上限、转置/布局限制、bank争用 |

独立DMA参考规格的byte reader/writer收集与输出串行：目标256-bit时，简单流式端点的BB/2上限为16 B/cycle，即500 MHz下8 GB/s/engine，不是16 GB/s持续payload。两个engine的理论和也不保证整条端到端搬运链达到该值。多在途不会消除这个结构上限。

参考MX量化实现仅支持FP32/FP16/BF16→MXFP8，每engine量化阶段1element/cycle且收集/量化/排出不重叠；500 MHz的0.5G element/s只计量化阶段，不是完整转换吞吐。不支持的MXFP4动态量化与MX反量化不能作为已有DMA能力；必须由VPU/其他明确实现补齐再评估。以上是独立模块参考约束，不是已集成NPU系统成绩。

### 3.6 P0-5 验收基线与阶段门

**2026-10-27人工确认本轮P0-5/P0-6文档闭环；规范值以[架构12.8](arch.md#128-批准工作负载与持续验收门槛)为准，以下记录批准目标及推导，不是实测成绩。** 不沿用旧Qwen2.5/INT W4A8数字；FP8保留原artifact语义，MX不因压缩比例自动获得同比例速度承诺。

批准profile为：P512（batch1、512-token prompt、生成256 tokens、无prefix cache）；D2048（先建立2048-token FP16 KV，再测256个decode step，禁止EOS提前结束，context增长至2304）；C4096（容量/长上下文压力，吞吐独立报告）。固定token序列、template和thinking模式，warm与cold、模型加载、预处理和输出采样分开标时。相同输入和artifact用于G0/Pro比较，不用短回复或不同生成模式提高成绩。

| FP8指标 / profile | G0目标 | Pro目标 | 约束与原因 |
|---|---:|---:|---|
| Qwen3-0.6B D2048平均decode | ≥6 token/s | ≥10 token/s | 起始权重＋KV理想流约5.92/9.86 GB/s，保留额外流量余量；Qwen scale桥接尚未证明 |
| DeepSeek D2048平均decode | ≥2.5 token/s | ≥4 token/s | 起始理想流约4.59/7.34 GB/s；须加LM head、量化、attention与调度服务 |
| Qwen3-0.6B P512 warm TTFT p95 | ≤4 s | ≤3 s | 包括全部prompt计算和首token，不包括模型首次加载；cold单列 |
| DeepSeek P512 warm TTFT p95 | ≤8 s | ≤5 s | G0部分算子可能减M，不能按理想M32计算保证 |
| Qwen3-0.6B D2048 TPOT p95 | ≤250 ms | ≤150 ms | 平均吞吐与尾延迟必须同时通过 |
| DeepSeek D2048 TPOT p95 | ≤600 ms | ≤400 ms | 同上；不以一次瞬时峰值代替持续服务 |
| Pro单请求decode加速 | — | 同profile≥1.6×G0 | 实际比值比较，不能只对比两个最低门槛；扩展效率按加速比/2报告 |
| SRAM驻留GEMM长K基准 | ≥40%相应稠密峰值 | ≥40%相应稠密峰值 | 独立合法M32/M48 kernel，含装载/排空和C；不适用于M1，也不强制所有模型算子满足 |

D2048的流量随context增长，2304-token KV必须纳入整个运行窗口；表内初始带宽仅解释目标来源，不是准入证明。MXFP8/MXFP4首轮先作同profile质量/流量/服务实测，性能数值TBD；若需以A8/B4替代A4/B4，另立profile且不使用同格式4bit翻倍峰值。

检测基线为官方YOLO11n/YOLO11s、640×640、batch1，fps/时延/质量目标已批准，见[第4章](#4-yolo-双检测模型基线与评估)。部署量化、导出图与逐算子服务须在实施验收前冻结并验证，不把浮点参考当成已有FP8/MX部署。

批准质量门槛为：FP8移植通过原checkpoint参考runtime的逐算子误差预算与模型任务一致性；同源MX相对BF16的固定语料perplexity相对增幅≤5%，任务准确率绝对下降≤1个百分点；YOLO mAP绝对下降≤1个百分点。FP8相对BF16的质量差异同样记录，不把发布版本默认视为通过。语料/任务集/hash、低精度误差预算、量化recipe须在数值验收前冻结；MX4若需放宽门槛重新评审，只通过perplexity不足以关闭生成质量验收。

批准测量口径为至少30次warm P512请求报告TTFT分布，D2048至少30轮报告总有效token/总decode时间与TPOT分布；连续重复完整任务≥10分钟验证频率、温度和吞吐稳态。记录batch1单请求和独立并发吞吐，禁止丢弃慢轮次；功耗/能量上限、温度边界、供电和噪声条件仍TBD，不造数值。数值/输入冻结、完整算子覆盖、峰值容量、DMA/DRAM服务与观测能力是性能签核前置，不可用更多MAC掩盖缺项。

**P0-5本轮结论：** 2026-10-27批准的原四模型身份、MX路线、服务/质量门槛与测量阶段门已落入架构，文档评审已关闭；完整算子、量化和持续性能实测仍是正式签核前置。2026-10-28新增的Qwen3.5-2B仅模型身份获批，不包含在本章既有算例及指标闭环结论中。

**P0-6本轮结论：** 容量/流量规划、合法分块约束及实施交付要求已经批准，文档评审已关闭，不等于实际服务已通过。FP8 scale/BF16层执行、DeepSeek大K数据计划、MX4动态量化、FP32 C流量与DRAM/DMA竞争证据仍须按[架构12.2](arch.md#122-sram-容量与-tiling)及[14.3](arch.md#143-性能验收)提交；单模型条件化预算不保证多模型常驻。8/16 GB/s参考payload、4266 MT/s、DMA在途深度未随本轮关闭而冻结，P1-4/P1-7独立保留。主CPU软件Queen/local CPU控制边界不变，P0-4其余协议仍暂缓，本轮不改RTL。

## 4. YOLO 双检测模型基线与评估

### 4.1 模型选择与质量参考

根据用户要求，批准基线必须包含两个 YOLO 检测模型。本轮选定 Ultralytics 官方 COCO 检测预训练 **YOLO11n / YOLO11s**，分别覆盖轻量与较高精度档；两者同属一代、同检测流程，便于比较模型大小与服务压力。选择稳定的同族检测图，不因其他代际名称更新就静默替换基准。

| 模型 | 官方权重名 | 输入 / batch | 厂家参数量 | 厂家FLOPs | 厂家COCO mAP50–95 |
|---|---|---|---:|---:|---:|
| YOLO11n | `yolo11n.pt` | 640×640 / 1 | 2.6M | 6.5G | 39.5 |
| YOLO11s | `yolo11s.pt` | 640×640 / 1 | 9.4M | 21.6G | 47.0 |

来源为[Ultralytics YOLO11官方检测表](https://docs.ultralytics.com/models/yolo11/)，核验日期2026-10-27。参数量、FLOPs与mAP是厂家报告的参考值，不是本项目FP8/MX量化或NPU实测；不得移植厂家CPU/T4时延作为G0/Pro成绩。FLOPs列需与冻结导出图的2operations/MAC统计重新对账，不包含我方layout、量化、DMA和完整预后处理成本。本轮同时核验官方主分支`yolo11.yaml`：包含C3k2、SPPF、C2PSA、Upsample/Concat与P3/P4/P5 Detect；其注释FLOPs为6.6/21.7G，与官网表6.5/21.6G不同，说明版本/统计口径不能混用，正式manifest必须冻结图并重新计数。

官方浮点checkpoint作为质量参考，正式manifest冻结权重hash、Ultralytics/exporter版本、导出图hash/opset、实际tensor dtype及数据集版本。不得把官方`.pt`权重宣称为现成FP8/MX部署版本；FP8/MXFP8/MXFP4的权重和activation格式、校准、未量化算子、scale映射仍待数值评审，禁止依赖NVFP4两级缩放或未支持的转换路径。

### 4.2 检测流程与算子落点

固定COCO val2017评估、80类检测、官方浮点参考；主基准640×640、batch1，不采用seg/pose/obb模型。输入letterbox/归一化、图内运算、框解码、置信度过滤、NMS与坐标还原全部覆盖；阈值、max_det、class-aware/agnostic、动态shape及图内/图外后处理位置写入manifest。密集目标和空检测作为边界场景，不靠提高过滤阈值或删除NMS提高fps。

| 工作 | 优先评估路径 | 不可省略的服务 |
|---|---|---|
| Conv/可映射Linear | GEMM，DMA或软件执行合法布局转换 | stride/padding、张量tile、M≤48、weight复用、scale、FP32输出与格式桥接 |
| SiLU、pool、split/concat、resize及残差 | VPU/local CPU或明确DMA COPY路径 | 非矩阵算子不能只按GEMM FLOPs计时，转换能力逐项核验 |
| 图中的attention类模块及检测头 | 按实际导出图分别映射GEMM/VPU/local CPU | softmax、归约、reshape和检测输出语义；不假定YOLO只有卷积 |
| 框解码、过滤、NMS、坐标还原 | VPU/local CPU；主CPU处理时单列并计入完整任务 | 候选框数量、空/密集目标、读写流量及尾延迟 |

主CPU的软件Queen只负责系统任务管理；local CPU调用scheduler执行GEMM/DMA、直接管理VPU。软件Queen任务往返和任何主CPU回退均计入端到端时延/功耗，不能把VPU写成scheduler派发或把仓库外实现画成core外设备。

### 4.3 批准服务指标与验收条件

两个模型纳入功能/质量/性能验收，以下目标已于2026-10-27人工批准并落入[架构12.8](arch.md#128-批准工作负载与持续验收门槛)，不代表实现或持续性能已通过：

| 指标 | G0目标 | Pro目标 |
|---|---:|---:|
| YOLO11n持续batch1检测 | ≥30 fps | ≥48 fps |
| YOLO11n完整检测p95时延 | ≤50 ms | ≤35 ms |
| YOLO11s持续batch1检测 | ≥10 fps | ≥16 fps |
| YOLO11s完整检测p95时延 | ≤150 ms | ≤100 ms |
| 量化mAP50–95损失，相对同源浮点参考 | ≤1个百分点 | ≤1个百分点 |

此前YOLOv8数字不能当成YOLO11性能推导；上述目标保留轻量实时/较高精度两档产品定位，必须用YOLO11导出图、量化和完整服务预算验证。若实际图不通过，重评算子实现；变更批准目标须重新评审，不用同名模型的另一版本替代。G0/Pro以同权重、同图、同量化、同预后处理比较，报告实际Pro加速/扩展效率。

容量按真实部署权重、当前/下一tile、FP32中间结果、布局变换临时区、VPU scratch、候选框与NMS峰值核算；厂家参数量不能证明512 KiB/core可容纳全部activation。实际验收须提交算子覆盖、数值、SRAM生命周期与DMA/DRAM流量证据，按批准测量口径完成持续与空/密集目标压力测试。当前未下载权重、未导出量化图、未运行NPU测试；P0-5/P0-6关闭仅代表本轮要求与规划获批。

## 附录 A：公开资料与核验限制

| 资料 | 核验范围 | 使用限制 |
|---|---|---|
| [NVIDIA Jetson Orin 官方产品表](https://www.nvidia.com/en-us/autonomous-machines/embedded-systems/jetson-orin/) | 同型号 GPU Tensor Core 的 INT8 dense/sparse 与 GPU CUDA Core 的 FP32/FP16 峰值；AGX Orin 64GB 和 Orin Nano 8GB（Super） | 排除整机 DLA TOPS；GPU 通用浮点不是独立 VPU；不同型号和功耗档不拼接 |
| [NVIDIA Jetson AGX Xavier 架构介绍](https://developer.nvidia.com/blog/nvidia-jetson-agx-xavier-32-teraops-ai-robotics/) | 512 CUDA cores、64 Tensor Cores、1.37 GHz、GPU INT8 22 TOPS 与矩阵 FP16 11 TFLOPS | FP32 1.403 TFLOPS 为按一 FP32 FMA/core/cycle 的推导值；矩阵 FP16 不作为 VPU FP16 |
| [Arm Ethos-U85 官方产品页](https://www.arm.com/products/silicon-ip-cpu/ethos/ethos-u85) | 128–2048 MAC 与最高 4 TOPS；本页未披露独立 FP32/FP16 向量 GFLOPS | 不能据此反推 GEMM/VPU 比 |

产品配比核验日期为2026-10-26；下列模型配置与API文件元数据核验日期为2026-10-27，不下载完整权重。模型操作量、利用率、允许时延和带宽均为条件化演算，不属于厂商保证或本项目实测；规范以总体架构为准。

| 模型公开来源 | 核验范围 | 限制 |
|---|---|---|
| [Qwen3-0.6B-FP8](https://huggingface.co/Qwen/Qwen3-0.6B-FP8)、[同源 BF16](https://huggingface.co/Qwen/Qwen3-0.6B) | 本轮revision的config、API tensor dtype统计与文件字节；Q/heads/head_dim、量化与tying | 未运行参考模型、未逐tensor下载校验；正式manifest仍需hash和runtime语义 |
| [DeepSeek FP8社区版本](https://huggingface.co/RedHatAI/DeepSeek-R1-Distill-Qwen-1.5B-FP8-dynamic)、[同源 BF16](https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B) | config、compressed-tensors量化组、LM head排除、API文件字节与revision | 不宣称DeepSeek官方FP8发布，不将模型名义大小当文件大小 |
| [GEMM运行期几何](../../workerbee/scala/main/WorkerBeeCalcConfig.scala)、[执行器](../../workerbee/scala/main/WorkerBeeExecutor.scala) | A/B block16/32、T=16的C block16限制、M/K装载与排空 | 独立模块源码，不是cluster吞吐实测 |
| [DMA参考规格](../../foragerbee/doc/spec.md) | byte reader/writer BB/2、MX8量化1element/cycle、转换支持边界 | 不涉及修改/验证独立DMA项目，不能宣称已集成 |
| [YOLO11官方检测表](https://docs.ultralytics.com/models/yolo11/)、[官方检测图](https://github.com/ultralytics/ultralytics/blob/main/ultralytics/cfg/models/11/yolo11.yaml) | YOLO11n/s模型身份、640输入、参数/FLOPs/mAP参考，检测图模块组成 | 官网与当前主分支注释统计不同；未下载权重、未运行量化或NPU检测验收 |
