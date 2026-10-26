# Hive NPU 性能分析

文档版本：`v0.1.0-draft`

修订日期：`2026-10-26`

> 本文记录非规范性的性能推导、敏感性分析和公开产品数据。架构能力、接口和验收要求以[总体架构](arch.md)为准；代际产品演进、竞品定位、成本和里程碑以[架构路线图](arch_roadmap.md)为准。本文中的候选配置和算例不构成 RTL、PPA 或产品承诺。

## 1. 文档边界

本文用于回答性能选型中的“为什么”和“如果怎样”，包括：

- 公开产品资源配比能否作为本项目参考；
- VPU 执行宽度变化对峰值、供数、面积和功耗的影响；
- 工作负载对普通向量、特殊函数、归约和 attention 的需求；
- 计算峰值与 local SRAM 流式带宽之间的 roofline 约束。

本文不定义 VPU ISA、接口协议、错误语义或产品代际。首版规范目标见[总体架构第 4.4 节](arch.md#44-vpu-职责与精度需求)和[第 12.7 节](arch.md#127-vpu-性能指标与接口要求)。

## 2. VPU 配比、选型与工作负载分析

当前首版草案指标为每 core 256-bit 等效执行宽度、500 MHz、FP32 8 GFLOPS / FP16 16 GFLOPS，以及一个 256-bit local 数据请求口。以下扩展选项不属于首版要求，也不代表外部 VPU 已实现。

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
| 首版草案基线 | 256 bit | 8 / 16 | 8 / 16 GFLOPS | 16 / 32 GFLOPS | 32 / 64 GFLOPS | 64:1、32:1 |
| 扩展选项 | 512 bit | 16 / 32 | 16 / 32 GFLOPS | 32 / 64 GFLOPS | 64 / 128 GFLOPS | 32:1、16:1 |
| 更高吞吐选项 | 1024 bit | 32 / 64 | 32 / 64 GFLOPS | 64 / 128 GFLOPS | 128 / 256 GFLOPS | 16:1、8:1 |

256-bit 是当前首版草案起点。512/1024-bit 只保留为性能评估选项，不因 Orin 的公开配比相近就升格为需求。同格式 INT4/MXFP4 的 GEMM 峰值翻倍也不自动要求 VPU 翻倍。

计算宽度不是访存带宽：每拍 32 个 FP32 元素的一条操作数流为 1024 bit，500 MHz 下是 64 GB/s，但 FMA 可能有多条输入流及输出流，也可能通过寄存器复用减少 SRAM 访问。是否扩宽执行能力须同时比较算子服务时间、寄存器/缓冲成本、供数、面积和功耗；当前没有 PPA 数据证明某个扩展档具有产品收益。

### 2.3 工作负载与时延敏感性

对每个精度分别统计普通运算量 `F_V`、允许的向量阶段时间 `t_V` 和可达利用率 `u_V`，所需峰值可按 `P_V_peak ≥ F_V/(t_V×u_V)` 估算。若允许 GEMM 与 VPU 流水重叠，还需检验 `P_V_peak×u_V ≥ rho×P_G_peak×u_G`，其中 `rho=F_V/F_G` 来自同一任务的实际算子统计；没有重叠时，GEMM/VPU 时间相加。

仅作敏感性演算：普通向量工作量若占矩阵 operations 的 2%，GEMM/VPU 可达利用率分别为 50%/25%，每 core 的 GEMM 峰值为 512 GOP/s，则所需向量峰值约 `0.02×0.50/0.25×512=20.48 GFLOPS`。这个假设下 8 GFLOPS 基线不能满足完全重叠条件，不能据此宣称首版无 VPU 瓶颈；2% 和利用率均非模型实测，也不能据此认定首版必须采用 1024-bit。exp、rsqrt、除法与归约串行延迟另行建模。

以下采用 **7B 量级条件化算例**，参数为 `layers=28,H=3584,I=18944,query_heads=28,kv_heads=4,head_dim=128,context=4096`；精确模型及量化布局尚未锁定。该算例不构成 G0 的强制模型基线。

| 普通向量工作 | 每 token 的量级公式 | 算例条件 |
|---|---|---|
| 两次 RMSNorm/层 | `2×layers×(4H+2)` | 未含所有归约调度开销 |
| Q/K RoPE | `3×layers×(H+kv_heads×head_dim)` | 每二元组四乘两加，旋转系数已备好 |
| SwiGLU | `4×layers×I` | exp 另计 |
| softmax 普通运算 | `4×layers×query_heads×context` | 临时预算，不等于实际指令数 |

合计约 **16.11M operations/token**，尚未包含所有 residual、转换、比较和量化辅助。SiLU/softmax 约需 `layers×(I+query_heads×context)=3.74M` 次 exp/token。

沿用总体架构第 12.4 节的条件化 8.9 token/s 场景，普通运算约 0.143 GOP/s，exp 约 33.3M 次/s，均为整个请求的聚合值，而不是 token/s 或特殊函数保证。decode 普通浮点量可能远小于 FMA 峰值，瓶颈仍可能在特殊函数、归约和访存；向量阶段若只占部分 token 时间，瞬时服务能力须按该时间窗口上调。多核 N 分片不保证归约与共享 activation 的工作均匀分摊。

### 2.4 FP16 attention 回退的额外负担

若 FP16 VPU 承担完整 QKᵀ 与 probability×V，同一算例在 context=4096 时 decode 额外约为 `4×layers×H×context=1.644G FLOP/token`；1024-token causal prefill 约为 `2×layers×H×S×(S+1)=210.66G FLOP`（S=1024），忽略尾块无效计算。

| 每 core VPU 档位 | 两核聚合 FP16 | 两核 prefill attention 理想时间 | 四核聚合 FP16 | 四核 prefill attention 理想时间 |
|---|---:|---:|---:|---:|
| 256-bit 基线 | 32 GFLOPS | 6.58 s | 64 GFLOPS | 3.29 s |
| 512-bit 选项 | 64 GFLOPS | 3.29 s | 128 GFLOPS | 1.65 s |
| 1024-bit 选项 | 128 GFLOPS | 1.65 s | 256 GFLOPS | 0.82 s |

上述时间只按运算量除以满利用率峰值，未含 softmax、KV 搬运、低利用率与跨核调度，必须另加到完整任务模型。当前 GEMM 默认不支持 FP16/FP32 输入，attention 若要落到低精度 GEMM，必须验证格式合法性和量化精度，不能假定已经完成。

### 2.5 访存 roofline 与选型触发条件

一个 256-bit local 请求口在 500 MHz 下每拍最多一个读或写时，读写合计峰值为 16 GB/s，而非读 16 + 写 16 GB/s。以寄存器内标量系数的 `y=a×x+b` 为例，每元素读 x、写 y，FP32 需 8 B、FP16 需 4 B，各贡献 2 FLOP；理想流式上限分别仅为 **4/8 GFLOPS**，未扣仲裁和 bank 冲突。因此 256-bit 执行的 8/16 GFLOPS 也不是单口流式保证。

| FP32 标量系数 FMA 的持续目标 | 最低有效读写合计带宽 | 比较条件 |
|---|---:|---|
| 8 GFLOPS | 32 GB/s | 不能由首版单口连续流式供数达成 |
| 16 GFLOPS | 64 GB/s | 执行扩到 512 bit 后仍需供数或复用 |
| 32 GFLOPS | 128 GB/s | 执行扩到 1024 bit 后仍需供数或复用 |

若 a、x、b 三个输入均为独立流式 FP32 向量，连同输出为 16 B/元素、2 FLOP，32 GFLOPS 则需 256 GB/s。流量模型必须按实际输入/输出和寄存器复用统计，不用单条 1024-bit 数据流的 64 GB/s 代替总带宽。

演进优先检查寄存器/局部缓冲复用和算子融合，减少中间结果落 SRAM；仅在冻结工作负载的向量服务时间不达标时，比较特殊函数加速、归约优化、执行扩宽与端口扩展。拓宽数据路径必须同步重算总体架构第 12.3 节的请求压力、GEMM/DMA 并发、bank 冲突和 PPA。GFLOPS、exp 启动间隔、归约延迟与完整 kernel 服务时间分别测量，不以更大 FMA 阵列作为所有瓶颈的答案。

## 附录 A：公开资料与核验限制

| 资料 | 核验范围 | 使用限制 |
|---|---|---|
| [NVIDIA Jetson Orin 官方产品表](https://www.nvidia.com/en-us/autonomous-machines/embedded-systems/jetson-orin/) | 同型号 GPU Tensor Core 的 INT8 dense/sparse 与 GPU CUDA Core 的 FP32/FP16 峰值；AGX Orin 64GB 和 Orin Nano 8GB（Super） | 排除整机 DLA TOPS；GPU 通用浮点不是独立 VPU；不同型号和功耗档不拼接 |
| [NVIDIA Jetson AGX Xavier 架构介绍](https://developer.nvidia.com/blog/nvidia-jetson-agx-xavier-32-teraops-ai-robotics/) | 512 CUDA cores、64 Tensor Cores、1.37 GHz、GPU INT8 22 TOPS 与矩阵 FP16 11 TFLOPS | FP32 1.403 TFLOPS 为按一 FP32 FMA/core/cycle 的推导值；矩阵 FP16 不作为 VPU FP16 |
| [Arm Ethos-U85 官方产品页](https://www.arm.com/products/silicon-ip-cpu/ethos/ethos-u85) | 128–2048 MAC 与最高 4 TOPS；本页未披露独立 FP32/FP16 向量 GFLOPS | 不能据此反推 GEMM/VPU 比 |

核验日期为 2026-10-26。模型操作量、利用率、允许时延和带宽均为条件化演算，不属于厂商保证或本项目实测；首版要求以总体架构第 12.7 节为准。
