# Deep Research: Hive 性能分析文档的业界方法论对标审查

> 生成日期 2026-10-30 ｜ 深度：deep ｜ 来源：55 ｜ 被审文档：`src/hive/doc/performance_analysis.md` v0.6.0（内部评审与本报告互补，报告不改动被审文档）

## TL;DR

被审文档的分析骨架（Q/w 接口预算、分层 roofline 下界、依赖图归因、L0–L4 分层验收、W/A 全量化分别建账、KV 精度单列）与业界方法论同构，多项做法（未冻结不签通过、逐算子独立量化参考、证据分级）达到或超过公开材料的显式程度。四个必须补齐的差距：**测量规程未条款化**（无采样数/warmup/EOS 冻结，分位数口径偏离 MLPerf 且无论证）、**签核后闭环缺失**（无回归门禁、无长稳判据）、**统计披露不足**（持续 fps 无温度/功耗/方差）、**质量门槛无外部锚定**（1pp/ppl≤5% 属自设，未覆盖下游任务组与安全类任务）。补齐均为条款级修订，不需要推翻结构。

## Executive Summary

本次调研以 4 个并行检索 agent、2 轮补检、1 轮引用抽查（7 SUPPORTED / 2 PARTIAL / 0 UNSUPPORTED）收集 55 个来源，覆盖服务指标与测量、性能建模与预算、验收与签核闭环、量化部署评估四个领域，逐项对照被审文档。

结论按领域：**服务指标**方向正确但距 MLPerf 级可复现标准差一档——MLPerf 把最短时长（600 s）、最少样本（Offline ≥24,576；单流 23,886 @90% 置信）、分位数（单流 90 分位、Server p99）与精度门槛（closed 99% of FP16）全部冻结成可复核数字 [1][47]，而文档只写"采样方案随 case 冻结"，属可复核性缺口而非行业常态 [53][54]；端侧持续性能必须披露热状态与方差（iPhone 16 Pro 两轮迭代吞吐近半 [4]），文档缺此披露。**建模预算**与业界同构：roofline 是下界且达界无保证 [9][10]、decode 带宽 bound（OI≈1 FLOP/B）/prefill 算力 bound [13]、Timeloop/MAESTRO 式每级流量容量账 [15][16]、动态事件依赖图归因 [18][19]，文档均有对应物；缺 stall attribution 计数器清单与仿真-实测对齐步骤 [17]，以及 w_i 分配的 owner。**验收签核**呈"前半强、后半空"：五层 23 case + 四类证据分级超出公开材料的显式程度，与证据金字塔精神一致 [20][23][24][25][30]；但业界标配的回归门禁（基线 artifact + 阈值取自实测方差 [26][27][28]）与长稳判据（8–72 h 趋势判据 [29]，量化阈值行业无公开标准、须自定并写明 [55]）缺失。**量化评估**框架正确（OCP MX block32 基线 [31][32]、perplexity+下游任务组 [33][36][37]、KV 单列 [43][44]、逐算子调试 [42] 与 Qualcomm Accuracy Debugger 同构）；但"≤1pp/ppl≤5%"未检索到公开先例，业界用相对保留率或"接近浮点基线"表述 [34]，裸 MXFP4 W/A RTN 掉点约 5.3 pp [33]，门槛应标注内部约定性质；量化可破坏安全对齐，任务组须含安全维度 [38][46]。

## 1. 现状对标 [Confidence: High（领域三为 Medium）]

### 1.1 服务指标与测量 [High]

LLM 推理服务质量评测的公开基准为测量口径提供了最严格的参照。MLPerf Inference server 分轨要求 TTFT 与 TPOT 满足 p99 约束（示例值 6 秒/175 毫秒），closed 分轨另设 99% 精度门槛 [1]；规则体系同时冻结最短 600 秒运行期、Offline 场景不少于 24,576 个样本、valid run 须同时通过性能与精度双门槛，并允许 early stopping [47]；单流场景要求至少 23,886 次查询以支撑 90% 置信度，官方明言数字"选得足够大以统计验证系统满足时延要求" [2][47]；面向端侧的 MLPerf Mobile 则为单流冻结了 90 分位、至少 1,024 个样本、最短 60 秒的采样方案 [54]。指标口径之外，Meta-Metrics 类方法论主张剔除 warmup 样本并以中位数报告，以抑制启动期扰动 [3]。

持续性能维度的公开证据更为严苛：端侧实测研究表明热管理是持续性能的第一约束，iPhone 16 Pro 在持续负载下两轮降频后吞吐约减半 [4]，因此持续 fps 类指标若不披露温度、功耗与方差，其数值不具备跨会话可比性；端侧 YOLO 基准研究同样把热节流起始点与时延确定性列为必报维度 [8]。CV 检测类工作负载应报告完整分布而非单点均值，且计费口径须包含预处理与后处理（NMS）开销 [6]。

被审文档以 p95 报告尾时延、warm/cold 分离、batch1 固定并单列多请求并发。p95 在工程实践中常用（APM 默认档位 [49][50][51]），但与 MLPerf 的 p99 不属同一统计档——p99 的样本成本达约 26 万次查询量级 [2]，采用 p95 须对样本量与置信度作专门论证而非默认等价。文档"TPOT 记相邻 token 间隔"的口径与 MLPerf 一致 [1]，跨文档对数前核对定义的做法也与 NVIDIA 官方告诫相符 [7]。缺口集中在条款化层面：无采样数规范、无 warmup 剔除与 EOS 冻结条款、持续 fps 无温度/功耗/方差披露。

### 1.2 性能建模与预算 [High]

roofline 作为分析工具的适用边界被反复申明：它不涵盖全部限制因素，达到边界亦无保证 [9][10]；分层 roofline 的价值在于逐级下钻、定位瓶颈所在存储层级 [11][12]。对 transformer 推理负载，decode 阶段算术强度约 1 FLOP/Byte、本质为带宽受限，prefill 阶段则为算力受限，两类阶段的预算逻辑须分开建立 [13][14]。分析模型的工程化依赖结构化输出：Timeloop/MAESTRO 类框架输出每级存储的流量（含 multicast/fan-out）、容量占用与活动计数，但属解析模型而非周期精确仿真 [15][16]。瓶颈归因可用动态事件依赖图加最长路径定位关键链路 [18][19]。NPU 语境下另有放大效应：实测框架研究显示仿真与实机的性能差距在 NPU 上可放大至 10 倍，须以逐接口计数器做对账 [17]。

被审文档的 Q/w 接口预算、C_peak 容量峰值、分层 roofline 下界与"条件化效率"表述在结构上与上述方法论同构；GEMM 装载/计算不默认重叠、接口并列不证明重叠等告诫与 roofline 的适用边界声明一脉相承 [9][10]。缺口有二：其一，缺 stall attribution 计数器清单与仿真-实测对齐步骤，使下界无法转化为可执行的对账程序 [17]；其二，w_i 分配无 owner，无主字段在跨团队签核场景中必然悬空。

### 1.3 验收与签核闭环 [Medium]

性能验证的公开方法论以证据金字塔为共识骨架：分析/性能模型、RTL 仿真、emulation、FPGA 原型、硅后实测逐级收窄不确定性且互不替代 [20][23][24][25][30]；同一性能场景跨阶段复用（Portable Stimulus）是当前活跃研究方向 [20]。业界 datasheet 数字普遍呈"up to + 绑定条件"形态：Ethos-U85 标称 1 GHz 下 4 TOPS 并绑定 128–2048 MAC 配置 [21]；NPX6 的 250 TOPS 绑定 1.3 GHz、5 nm 与 worst-case 前提 [22]。被审文档"未冻结不签通过""待确认项不得签为通过"的条款与此精神同向，且证据四分级（理论/模型仿真/RTL 仿真/硬件实测）把金字塔写进了验收结构，属加分项。

签核之后的可持续性依赖回归门禁与长稳判据：回归门禁的标准构成为基线 artifact 固化、每次构建自动对比、阈值取自实测方差而非拍脑袋（"基准结果是一个分布，而非单一真值"）[26][27][28]，大规模 CI 用风险感知批处理控制成本 [27]；soak 测试成熟实践为 8–72 小时趋势判据 [29]。值得注意的是，长稳量化判据在公开行业标准中缺位，现有材料仅作定性描述 [55]；正因如此，自定量化判据并显式写明阈值成为可辩护签核的必要条件而非可选项。被审文档缺口集中于闭环后半段：无回归门禁、无长稳判据、case 无入口状态列。

### 1.4 量化部署评估 [High]

量化部署的格式基线由 OCP MX v1.0 确立：MXFP8/MXFP6/MXFP4/MXINT8 四种格式、block 32、E8M0 共享 scale [31][32]；gpt-oss 以原生 MXFP4 给出了已可端点部署的开源先例 [34][35]。评估方法上业界共识为联合评估：perplexity（如 WikiText2）加下游任务组（PIQA、WinoGrande、ARC 等）[33][36][37]，单凭 perplexity 不构成充分证据 [36][46]。

可接受掉点幅度方面，公开检索未发现"绝对下降≤1pp"的成文先例，业界惯用相对保留率或"接近浮点基线"的表述——gpt-oss 的 QAT 把 pass rate 自 16%/30% 恢复到 98% [34]。参照点：裸 MXFP4 对 W/A 做 RTN 的下游成绩约 57.3%、对比 FP16 约 62.6%，绝对差约 5.3 个百分点 [33]，显示 1pp 门槛在无 QAT 补偿时相当激进。任务维度的已知规律：检测类 PTQ 掉点大于分类、逐层误差调试是定位手段 [39][40][41][42]；KV 缓存精度应与权重/激活分开评估，FP8 KV 损失较小 [43][44][45]；量化可破坏安全对齐，评估须含安全/行为类任务 [38][46]。被审文档 W/A 分别建账、KV 单列、独立量化参考加逐算子误差预算（与 Qualcomm Accuracy Debugger 实践吻合 [42]）均为加分项；缺口在门槛性质未标注（自设 vs 行业先例）与安全类任务覆盖缺失。

## 2. 新兴动向（2025–2026）

- **原生 MXFP4 进入部署端点**：gpt-oss 权重原生 MXFP4、Day-0 可跑 [34][35]，MX 从"训练压缩"转为"端点交付格式"，使 W/A 全量化的部署评估成为端侧 NPU 的现实需求而非前瞻。
- **QAT 成为质量恢复标配**：掉点后用 QAT 补回"接近浮点基线"已是 NVIDIA 等厂商的标准动作 [34][39]，质量门槛应设计为与 QAT 路径兼容。
- **消费级端侧基准成型**：MLPerf Client v1.0（2025-07）把 LLM 测量带进消费 PC，v1.5/v2.0 持续演进 [5][48][52]，端侧 LLM 的报告规则有了可直接引用的先例。
- **KV 量化走向 agentic 场景**：FP8/FP4 KV cache 单独评估与分策略保留成为长上下文工程实践 [43][44]。
- **性能门禁工程化**：风险感知批测试 [27] 与"分布而非真值"的门禁设计 [26] 正从 Web CI 渗透到系统软件，硬件性能回归可借鉴其统计纪律。
- **跨阶段性能场景复用**：Portable Stimulus 让同一性能场景在 emulation 与硅后复用 [20]，提示被审文档的 case 定义应设计为跨证据类可执行。

## 3. 批判性评估 [Confidence: High]

**差距不是结构性的而是条款性的。** 被审文档的四领域骨架均与业界同构，没有任何一项需要推翻重来；全部缺口可归为三类：

1. **可复核性缺口**：业界把统计规程冻结成数字（600 s、24,576 样本、23,886 查询、90/99 分位）[2][47][54]，文档把采样"随 case 冻结"却没有给出冻结模板与最少字段。Kalibera & Jones 的方法学（pilot 实验定重复次数、算置信区间）[53] 与 MLPerf 的现成数字可直接搬用。
2. **闭环后半段缺口**：签核前的分层验收已经强于公开材料，签核后的回归门禁与长稳判据完全空缺 [26][27][28][29][55]。长稳阈值无行业标准恰是必须自定的理由，而非可以不写的理由。
3. **锚定缺口**：p95（vs MLPerf p99）、1pp 绝对下降（vs 业界相对保留率）两处口径偏离均未声明论证；质量门槛未覆盖下游任务组与安全类任务 [33][34][36][38]。

**怀疑者视角的反驳与回应**：其一，"文档是内部分析文档，不必对齐 MLPerf"——但文档第 1.3 节自称用于服务验收，验收数字若无统计条款则签核不可辩护；其二，"p95 是工程惯例"——成立 [49][50]，但须写明这是有意选择并给样本量支撑，否则与 MLPerf 数字并排时不可比 [7]；其三，"1pp 比 5.3 pp 的 RTN 掉点更严是好事"——严格本身无错，但无 QAT 路径配套的严格门槛等于隐性否决 MXFP4 部署，应写明配套恢复手段或标注为待评审的内部阈值。

## 4. 行动计划

以下条目按优先级排序，均针对 `performance_analysis.md` v0.6.0 的具体位置；带 * 者同时承接内部三 agent 评审（完整性/可实现性/落地性）的发现。

- [ ] §1.3 为每个 profile 增加统计规程冻结字段：最短时长、最少样本/查询数、warmup 剔除、EOS 与生成长度上限；直接采用 MLPerf 数字作模板（600 s / 24,576 / 23,886；端侧可用 MLPerf Mobile 的 1,024 样本 / 60 s / 90 分位）[47][54]
- [ ] §1.3 对 p95 口径作显式决策：声明为内部口径并给出样本量与置信度论证，或对齐 p99；引用 p99 的统计成本（约 26 万查询）说明取舍 [2][47][49][50]
- [ ] §1.3 持续 fps/吞吐的观测量增加温度、功耗与方差披露要求 [4][8]
- [ ] §9.6 新增长稳验收（扩展 C4-02 或新增 case）：自定并写明量化判据——时长（参照 8–72 h 惯例）、吞吐漂移阈值、内存/状态泄漏与错误率判据；因行业无公开标准，阈值须内部冻结 [29][55]
- [ ] §9 新增"性能回归门禁"小节：基线 artifact（known-good 版本+输入 hash）固化、每构建自动对比、门禁阈值取自实测方差；说明大规模场景可用风险感知批处理 [26][27][28]
- [ ] §9.1 case 矩阵增加"当前入口/可执行性"列，区分有真实入口与被未实现子系统阻塞的 case *
- [ ] 第 2 章 w_i/l_i 分配与 C0-01 冻结导出图指定 owner、产出物与登记位置，消除唯一可能死锁的门控起点 *
- [ ] §8.2 注明 F_decode 常数已含 lm_head 508.6M，避免与 F_lm_head 相加双计 27% 算力 *
- [ ] §1.3/第 9 章质量门槛：标注 1pp/ppl≤5% 为内部阈值；任务组从单一语料扩为"perplexity+下游任务组（如 MMLU/ARC/GSM8K 类）+安全类任务"；给出与相对保留率口径的对照 [33][34][36][38][46]
- [ ] 第 7 章归因表扩展为 stall attribution 计数器清单（每接口/引擎的等待与占用计数），并增加仿真-实测对齐步骤；NPU 上框架差距可放大 10× 的教训写入证据要求 [17][18][19]
- [ ] C2-07 拆为三个独立 case（MoE 路由/查表/检测后处理）；削减附录 B.2 与附录 C 中的重复内容 *

## 5. 未决问题与保留事项

- **MLPerf Client v1.5/v2.0 规则全文未取得** [52]，端侧消费级报告字段（热态要求、TTFT/TPOT 定义）未经一手核对。
- **MLPerf rules 中 warmup 具体条数与 EOS 处理条款原文未定位**；报告建议的"warmup 剔除"以 Meta-Metrics [3] 为据而非 MLPerf 条款。
- **半导体性能验收的样本量/置信度无公开统一标准**；本报告借软件性能回归实践 [26][53] 与 MLPerf 数字 [47] 作为最接近参照，此为领域三置信度仅 Medium 的主因。
- **长稳量化判据无行业标准** [55]；行动计划的"自定阈值"建议因此不可再用外部数字锚定，须内部冻结。
- **两条 [snippet only] 引用**：MLPerf Mobile 的 1,024/60 s/p90 数字（PDF 直抓超时，经索引快照核对，DVFS 归因以 [2] 为准而非 [54] 原文措辞）；Qualcomm Accuracy Debugger 的逐层比对描述（文档站 JS 渲染，引句截断）。
- **"无 ≤1pp 公开先例"是检索否定性结论**，置信度 Medium；不排除厂商内部文档存在类似门槛。
- 缺 Apple/联发科等端侧厂商一手测量白皮书（不可公开抓取），端侧热控规程的厂商实践仅由学术基准 [4] 与 Tier 3 材料 [8] 间接支撑。

## Methodology

- **深度**：deep。子代理共 8 个：4 个并行检索（Wave 1，按四领域分派，起始编号 [1]/[9]/[20]/[31] 防冲突）、2 个定向补检（Wave 2：MLPerf 规程条款、统计与长稳；其中统计补检首次因服务错误失败，换措辞重派成功）、1 个引用核查（Phase 3.1）、1 个综合起草（Phase 5）。主代理执行合并、三角验证、提纲修订、批判与成文。
- **来源**：合并去重后 55 个（Wave 1 产 46 个，Wave 2 补 9 个；重复的规则文档条目按 URL 去重，样本数条款并入 [47]）。分层：Tier 1 ×33、Tier 2 ×11、Tier 3 ×11。趋势类来源均 ≤18 个月；方法学奠基文献（roofline、MLPerf 论文、Timeloop/MAESTRO、OCP MX、ACL 量化综述、Kalibera & Jones）标注 [foundational] 不受年限限制。
- **引用抽查**：10 条最高影响主张，7 SUPPORTED、2 PARTIAL、0 UNSUPPORTED。修正两处：MXFP4 RTN 掉点由"8 pp vs 65% 基线"改为 **5.3 pp vs 62.6% 基线** [33]；Timeloop 表述改为"解析模型，报告各存储级流量与存储利用率" [15]。两条 [snippet only] 降级已在未决问题中声明。
- **提纲修订**：原计划按四领域平铺；证据显示"统计规程"是跨领域的第一缺口，故在现状对标中按领域展开、在批判评估与行动计划中按缺口类型重组（可复核性/闭环后半段/锚定），结构变化未超 50%。
- **内部评审衔接**：本报告与此前内部三 agent 评审（文档完整性/工程可实现性/硬件点落地）互补；行动计划中带 * 条目来自内部评审，其余来自本次外部对标。被审文档的硬件规格与验收目标以 `src/hive/doc/arch.md`（第 12.8 节服务门槛）为准，本报告未核验内部文档事实。

## Bibliography

[1] MLCommons — MLPerf Inference v5.0 Advances Language Model Performance — https://mlcommons.org/2025/04/llm-inference-v5/ — 2025-04-02 — Tier: 1
[2] Reddi, V.J. et al. — MLPerf Inference Benchmark — https://arxiv.org/pdf/1911.02549 — 2020 [foundational] — Tier: 1
[3] Meta-Metrics and Best Practices for System-Level LLM Benchmarking — https://arxiv.org/html/2508.10251v1 — 2025-08-14 — Tier: 1
[4] LLM Inference at the Edge: Mobile, NPU, and GPU — https://arxiv.org/html/2603.23640v1 — 2026-03-24 — Tier: 1
[5] MLCommons — MLPerf Client v1.0 发布 — https://mlcommons.org/2025/07/mlperf-client-v1-0/ — 2025-07-30 — Tier: 1
[6] Input Resolution Matters: Real-Time Object Detection Latency — https://arxiv.org/html/2609.12920v1 — 2026-09-11 — Tier: 1
[7] NVIDIA — NIM LLM Benchmarking Metrics — https://docs.nvidia.com/nim/benchmarking/llm/metrics — 访问 2026-10-30 — Tier: 2
[8] IJERT — Comparative Benchmarking of CPU/GPU/NPU for Real-Time YOLOv8n Inference — https://www.ijert.org/comparative-benchmarking-of-cpu-gpu-and-npu-architectures-for-real-time-yolov8n-inference-on-embedded-edge-platforms-ijertv15is030232 — 2026-03 — Tier: 3
[9] Williams, Waterman, Patterson — Roofline: An Insightful Visual Performance Model for Multicore Architectures — https://cacm.acm.org/research/roofline-an-insightful-visual-performance-model-for-multicore-architectures/ — [foundational] — Tier: 1
[10] ScienceDirect — Roofline Model overview（局限综述） — https://www.sciencedirect.com/topics/computer-science/roofline-model — 访问 2026-10-30 — Tier: 2
[11] Yang et al. — Hierarchical Roofline Performance Analysis for Deep Learning Applications (SC20 P3HPC) — https://arxiv.org/html/2009.05257v4 — [foundational] — Tier: 1
[12] NVIDIA — Accelerating HPC Applications with Nsight Compute Roofline Analysis — https://developer.nvidia.com/blog/accelerating-hpc-applications-with-nsight-compute-roofline-analysis/ — 访问 2026-10-30 — Tier: 1
[13] Bi et al. — A Benchmarking Framework for On-Device LLMs via Roofline Model — https://arxiv.org/html/2602.11506v4 — 2026-02 — Tier: 1
[14] Hebbia — The Hidden Economics of LLM Inference — https://www.hebbia.com/blog/the-hidden-economics-of-llm-inference — 访问 2026-10-30 — Tier: 2
[15] Parashar et al. — Timeloop: A Systematic Approach to DNN Accelerator Evaluation (ISCA 2019) — https://accelergy.mit.edu/timeloop.pdf — [foundational] — Tier: 1
[16] Kwon et al. — MAESTRO: A Data-Centric Approach (HPCA 2019) — https://arxiv.org/html/1805.02566v6 — [foundational] — Tier: 1
[17] Cai et al. — Is Your NPU Ready for LLMs? Dissecting the Hidden Performance Gaps — https://arxiv.org/html/2607.05475 — 2026-07 — Tier: 1
[18] Calipers: A Criticality-aware Framework for Modeling Accelerator-based ML Systems (HPCA 2022) — https://arxiv.org/html/2201.05884v1 — [foundational] — Tier: 1
[19] Bai et al. — ArchExplorer: Microarchitecture Exploration Via Bottleneck Analysis (MICRO 2023) — http://www.cse.cuhk.edu.hk/~byu/papers/C180-MICRO2023-ArchExplorer.pdf — [foundational] — Tier: 1
[20] Vasu et al. — Media Performance Validation in Emulation and Post-Silicon using Portable Stimulus Standard (DVCon) — https://dvcon-proceedings.org/wp-content/uploads/media-performance-validation-in-emulation-and-post-silicon-using-portable-stimulus-standard.pdf — 访问 2026-10-30 — Tier: 1
[21] Arm — Ethos-U85 产品页 — https://www.arm.com/products/silicon-ip-cpu/ethos/ethos-u85 — 访问 2026-10-30 — Tier: 1
[22] Synopsys — Introduces Industry's Highest Performance Neural Processor IP (ARC NPX6) — https://news.synopsys.com/2022-04-19-Synopsys-Introduces-Industrys-Highest-Performance-Neural-Processor-IP — 2022-04-19 — Tier: 1
[23] exostivlabs — 10 things you should know before SoC Validation — https://www.exostivlabs.com/10-things-you-should-know-soc-validation/ — 访问 2026-10-30 — Tier: 2
[24] techlabssemi — Post Silicon Validation in Chip Design — https://techlabssemi.com/blogs/post-silicon-validation-in-chip-design-techlabs-semiconductor/ — 访问 2026-10-30 — Tier: 3
[25] Tessolve — Post-Silicon Validation in the AI Era — https://www.tessolve.com/blogs/post-silicon-validation-in-the-ai-era-strategies-for-testing-complex-socs/ — 访问 2026-10-30（403，据摘要）— Tier: 3
[26] Marbach, D. — Keep the gains: performance regression testing without fooling yourself — https://www.planetgeek.ch/2026/07/20/keep-the-gains-performance-regression-testing-without-fooling-yourself/ — 2026-07-20 — Tier: 2
[27] Risk-Aware Batch Testing for Performance Regression Detection — https://arxiv.org/html/2604.00222v1 — 2026-04 — Tier: 1
[28] Evaluat — Performance Regression Testing in CI/CD — https://www.evaluat.com/blog/performance-regression-testing — 访问 2026-10-30 — Tier: 3
[29] Grafana Labs — Soak testing: A beginner's guide — https://grafana.com/blog/soak-testing/ — 访问 2026-10-30 — Tier: 2
[30] Sun et al. — A Reproducible FPGA-to-Silicon Verification Methodology — https://www.mdpi.com/2079-9292/15/10/2202 — 2026 — Tier: 1
[31] OCP — Microscaling Formats (MX) Specification v1.0 — https://www.opencompute.org/documents/ocp-microscaling-formats-mx-v1-0-spec-final-pdf — 2023-09 [foundational] — Tier: 1
[32] Yang, H. et al. — An Empirical Study of Microscaling Formats — https://aisystemcodesign.github.io/papers/FP4.pdf — [foundational] — Tier: 1
[33] Shao et al. — Block Rotation is All You Need for MXFP4 Quantization — https://arxiv.org/html/2511.04214v1 — 2025-11 — Tier: 1
[34] NVIDIA — Fine-Tuning gpt-oss for Accuracy and Performance with QAT — https://developer.nvidia.com/blog/fine-tuning-gpt-oss-for-accuracy-and-performance-with-quantization-aware-training/ — 2025-08-29 — Tier: 1
[35] Perplexity AI — GPT-OSS on Day 0 — https://www.perplexity.ai/hub/blog/gpt-oss-on-day-0 — 2025-08-08 — Tier: 2
[36] Jin et al. — A Comprehensive Evaluation of Quantization Strategies for LLMs (ACL Findings 2024) — https://aclanthology.org/2024.findings-acl.726.pdf — [foundational] — Tier: 1
[37] Red Hat — We ran over half a million evaluations on quantized LLMs — https://developers.redhat.com/articles/2024/10/17/we-ran-over-half-million-evaluations-quantized-llms — 2024-10-17 — Tier: 2
[38] Egashira et al. — Exploiting LLM Quantization — https://arxiv.org/abs/2402.18102 — 2024 — Tier: 1
[39] NVIDIA — Achieving FP32 Accuracy for INT8 Inference Using QAT with TensorRT — https://developer.nvidia.com/blog/achieving-fp32-accuracy-for-int8-inference-using-quantization-aware-training-with-tensorrt/ — 2021 [foundational] — Tier: 1
[40] NVIDIA/TensorRT GitHub Issue #1114 — Quantization Error Analysis on Object Detection — https://github.com/NVIDIA/TensorRT/issues/1114 — 2021 — Tier: 3
[41] Quantization Robustness to Input Degradations for Object Detection — https://arxiv.org/html/2508.19600v2 — 2025 — Tier: 1
[42] Qualcomm — SNPE Accuracy Debugger 文档 — https://docs.qualcomm.com/doc/80-63442-10/topic/debug_tools_snpe-accuracy-debugger.html — 2026-09 更新 — Tier: 1
[43] Hugging Face — Unlocking Longer Generation with KV Cache Quantization — https://huggingface.co/blog/kv-cache-quantization — 2024-05-16 — Tier: 2
[44] LMSYS — NVFP4 KV Cache for Long-Context and Agentic Inference — https://www.lmsys.org/blog/2026-09-16-nvfp4-kv-cache/ — 2026-09-16 — Tier: 2
[45] r/LocalLLaMA — KV cache quantization 讨论 — https://www.reddit.com/r/LocalLLaMA/ — 2025 — Tier: 3
[46] apxml — Metrics for Quantized LLM Evaluation — https://apxml.com/courses/practical-llm-quantization/chapter-6-evaluating-deploying-quantized-llms/evaluating-quantized-models — 2025 — Tier: 3
[47] MLCommons — MLPerf Inference Benchmark Rules (inference_rules.adoc) — https://github.com/mlcommons/inference_policies/blob/master/inference_rules.adoc — 访问 2026-10-30 — Tier: 1
[48] MLCommons — mlperf_client (GitHub README) — https://github.com/mlcommons/mlperf_client — 访问 2026-10-30 — Tier: 1
[49] Redis — P95 Latency: What It Is & Why It Matters — https://redis.io/blog/p95-latency/ — 访问 2026-10-30 — Tier: 2
[50] Aerospike — What Is P99 Latency? — https://aerospike.com/blog/what-is-p99-latency/ — 访问 2026-10-30 — Tier: 2
[51] Rajesh — Understanding the P95/P99 Latency Principle — https://medium.com/@rajesh.sgr/understanding-the-p95-p99-latency-principle-why-the-slowest-requests-matter-most-1bcabf3bf5e5 — 访问 2026-10-30 — Tier: 3
[52] MLCommons — MLPerf Client v1.5 / v2.0 发布页（仅定位未抓取） — https://mlcommons.org/2025/11/mlperf-client-1-5-release/ 、 https://mlcommons.org/2026/08/mlperf-client-v2-0/ — Tier: 1
[53] Kalibera, T., Jones, R. — Rigorous Benchmarking in Reasonable Time (ISMM 2013) — https://dl.acm.org/doi/10.1145/2491894.2464160 — [foundational] — Tier: 1
[54] Reddi, V.J. et al. — MLPerf Mobile Inference Benchmark (MLSys 2022) — https://proceedings.mlsys.org/paper_files/paper/2022/file/a2b2702ea7e682c5ea2c20e8f71efb0c-Paper.pdf — [foundational] — Tier: 1（[snippet only]）
[55] Drizz.dev — Soak Testing and Endurance Testing for Mobile Apps — https://www.drizz.dev/post/soak-testing-and-endurance-testing-for-mobile-apps — 访问 2026-10-30 — Tier: 3

## Source Extracts

### [1] MLPerf Inference v5.0 发布
- **摘要**：定义 LLM 指标分场景报法及 server 场景 p99 约束值；closed 分轨精度门槛 99% FP16。
- **关键引句**："token throughput for the offline scenario and Time To First Token (TTFT) + Time Per Output Token (TPOT) for the server scenario"；"99th percentile TTFT of 6 seconds"；"accuracy threshold for closed division submissions is set to 99% of the FP16 reference"
- **来源类型**：官方规范发布 ｜ **Tier**：1

### [2] MLPerf Inference Benchmark（奠基论文）
- **摘要**：确立 closed/open 分轨、四场景、精度约束与可复现设计；解释最短时长为测功耗管理与 DVFS 平衡；p99 的 95% 置信样本成本约 26 万查询。
- **关键引句**："The minimum run time ensures we measure the equilibrium behavior of power-management systems and systems that support dynamic voltage and frequency scaling"
- **来源类型**：学术论文 ｜ **Tier**：1 [foundational]

### [3] Meta-Metrics and Best Practices for System-Level LLM Benchmarking
- **摘要**：梳理 TTFT/ITL/TPOT 定义与陷阱；主张剔除 warmup 后以中位数报告；警告 batch 吞吐与单请求时延的此消彼长。
- **关键引句**："Inter-token Latency (ITL): … the average time interval between consecutive token generations"；"using median iteration time after excluding warmup"
- **来源类型**：学术论文 ｜ **Tier**：1

### [4] LLM Inference at the Edge: Mobile, NPU, and GPU
- **摘要**：独立跨平台端侧持续性能基准，20 次热态迭代；揭示峰值数字不可持续。
- **关键引句**："thermal management supersedes peak compute as the primary constraint"；"iPhone 16 Pro loses nearly half its throughput within two iterations"
- **来源类型**：学术论文 ｜ **Tier**：1

### [5] MLPerf Client v1.0
- **摘要**：消费级 PC 端 LLM 基准首发，Llama 2 7B Chat / Phi 4 14B 走 ONNX Runtime。
- **关键引句**："a benchmark that sets a new standard for measuring … performance of large language models (LLMs) … on personal computers"
- **来源类型**：官方规范发布 ｜ **Tier**：1

### [6] Input Resolution Matters: Real-Time Object Detection Latency
- **摘要**：检测时延须报分布而非均值；测量边界含预处理+推理+后处理；预处理时延随分辨率显著变化。
- **关键引句**："scalar metrics are only representative statistics and provide limited insight"
- **来源类型**：学术论文 ｜ **Tier**：1

### [7] NVIDIA NIM LLM Benchmarking Metrics
- **摘要**：官方指标定义页；工具间定义差异导致数字不可直接比较。
- **关键引句**："Tool implementations vary, so compare results only when definitions align"
- **来源类型**：官方文档 ｜ **Tier**：2

### [8] IJERT YOLOv8n 跨平台基准
- **摘要**：端侧 YOLO 基准补充热节流起始点、时延确定性与 host CPU 余量维度。
- **关键引句**："we assess critical deployment factors including thermal throttling onset, latency determinism"
- **来源类型**：期刊（较弱的编辑流程） ｜ **Tier**：3

### [9] Roofline（CACM）
- **摘要**：用 peak FLOPs 与 peak BW 定义机器 balance point；四种多核机器验证。
- **关键引句**："we propose one such model we call Roofline, demonstrating it on four diverse multicore computers."
- **来源类型**：学术论文 ｜ **Tier**：1 [foundational]

### [10] Roofline Model overview（ScienceDirect）
- **摘要**：汇总文献对 roofline 局限的表述。
- **关键引句**："a roofline model does not consider all factors that limit performance, there is no guarantee that an application can reach such levels."
- **来源类型**：文献聚合 ｜ **Tier**：2

### [11] Hierarchical Roofline (SC20 P3HPC)
- **摘要**：ERT 表征机器 + Nsight 采集应用，对 L1/L2/HBM 各级以该级字节重算 AI，定位瓶颈存储级。
- **关键引句**："arithmetic intensity (AI), the reuse of data once it is being loaded from memory"
- **来源类型**：学术论文 ｜ **Tier**：1

### [12] NVIDIA Nsight Compute Roofline
- **摘要**：官方分层 roofline 用途定义。
- **关键引句**："Hierarchical Roofline gives you a more detailed understanding of which level of the memory hierarchy could be the bottleneck."
- **来源类型**：官方文档 ｜ **Tier**：1

### [13] On-Device LLM Roofline Framework
- **摘要**：端侧 LLM 统一 roofline：由 GEMM/KV 推 OI；decode OI≈2/b_prec（带宽 bound），prefill 算力 bound。
- **关键引句**："the decoding phase generates tokens autoregressively... shifts the execution bottleneck toward memory bandwidth"
- **来源类型**：学术论文 ｜ **Tier**：1

### [14] The Hidden Economics of LLM Inference
- **摘要**：从业者解析 decode 每步读全部 KV cache，带宽成本随上下文线性放大。
- **关键引句**："each step must read the full KV cache for every attention layer."
- **来源类型**：公司博客 ｜ **Tier**：2

### [15] Timeloop (ISCA 2019)
- **摘要**：输入 workload+架构+映射，输出每存储级流量（含 multicast/fan-out）、存储利用率、延迟与能耗；解析模型而非周期精确。核查评级 PARTIAL（"读/写明细与时序理想化"以该措辞复核，PDF 为二进制 [snippet only]）。
- **关键引句**："Network activity includes traffic between levels (including fan-out and multi-cast traffic)…"；解析模型输出 "memory traffic, storage utilization, execution time, energy"
- **来源类型**：学术论文+官方工具文档 ｜ **Tier**：1 [foundational]

### [16] MAESTRO (HPCA 2019)
- **摘要**：datacenter 解析 cost model，输出 buffer 访问、网络流量、运算数等 20+ 活动计数；局限含均匀稀疏与理想化流水线。
- **关键引句**："MAESTRO performs cost-benefit analysis based on an analytic model... reports activity counts (buffer access, arithmetic ops)."
- **来源类型**：学术论文 ｜ **Tier**：1 [foundational]

### [17] Is Your NPU Ready for LLMs?
- **摘要**：NPU 实测归因：框架引起的性能差距在 NPU 上放大至 10×；实测 NPU DDR 39.7 GB/s（CPU 57–60）。
- **关键引句**："Framework-induced performance gaps are substantially amplified on NPUs."
- **来源类型**：学术论文 ｜ **Tier**：1

### [18] Calipers (HPCA 2022)
- **摘要**：构建数据流事件依赖图后用最长路径算法算总执行时间与瓶颈。
- **关键引句**："performance and bottlenecks can be analyzed by applying graph algorithms (e.g., longest-path search)."
- **来源类型**：学术论文 ｜ **Tier**：1

### [19] ArchExplorer (MICRO 2023)
- **摘要**：critical path 分析建立在动态事件依赖图（DEG）上，自动化微架构瓶颈归因。
- **关键引句**："The critical path analysis is a methodology based on the dynamic event-dependence graph (DEG)."
- **来源类型**：学术论文 ｜ **Tier**：1

### [20] DVCon Portable Stimulus 媒体性能验证
- **摘要**：基于多款 SoC 性能验证瓶颈，提出 Portable Stimulus 标准让同一场景在 emulation 与 post-silicon 复用。（全文抓取失败，据检索摘要）
- **关键引句**："we propose enhanced validation methodology using Portable Stimulus Standard."
- **来源类型**：会议论文 ｜ **Tier**：1

### [21] Arm Ethos-U85
- **摘要**：官方产品页性能全部绑定配置；完整条件在 datasheet。
- **关键引句**："scales from 128 to 2048 MACs, providing up to 4 TOPs of performance at 1 GHz."
- **来源类型**：官方产品页 ｜ **Tier**：1

### [22] Synopsys ARC NPX6
- **摘要**：headline 数字显式绑定频率、工艺与 worst-case 条件。
- **关键引句**："up to 250 tera operations per second (TOPS) at 1.3 GHz on 5nm processes in worst-case conditions"
- **来源类型**：官方新闻稿 ｜ **Tier**：1

### [23] exostivlabs SoC Validation
- **摘要**：区分 verification 与 validation；emulator 与 FPGA 原型角色不同不可互替；存在只有硅后才能暴露的 bug。（抓取失败，据摘要）
- **关键引句**："Emulators do not replace prototypes (and the other way round)."
- **来源类型**：厂商工程博客 ｜ **Tier**：2

### [24] techlabssemi Post Silicon Validation
- **摘要**：pre-silicon 给纸面信心，硅后确认实际性能与可靠性。
- **关键引句**："Pre-silicon verification gives confidence in the design on paper, and post-silicon validation confirms the chip's performance and reliability in practice."
- **来源类型**：公司博客 ｜ **Tier**：3

### [25] Tessolve Post-Silicon in AI Era
- **摘要**：硅后在真实工作条件覆盖功能、性能、可靠性。（403，据摘要）
- **关键引句**："Post-silicon validation verifies fabricated chips under real operating conditions to ensure functionality, performance, reliability."
- **来源类型**：公司博客 ｜ **Tier**：3

### [26] Keep the gains（性能回归测试）
- **摘要**：基线 artifact 固化、先测方差再设门禁、阈值来自噪声；要求稳定硬件环境。
- **关键引句**："A benchmark result is a distribution, not a single truth."；"Choose the threshold based on observed variance … not because the number looks tidy"
- **来源类型**：从业者博客 ｜ **Tier**：2

### [27] Risk-Aware Batch Testing
- **摘要**：大规模 CI 不可能每 commit 全量跑性能套件；风险感知批处理策略。
- **关键引句**："Performance regression testing is essential in large-scale continuous-integration (CI) systems, yet executing full performance suites for every [commit]…"
- **来源类型**：学术论文 ｜ **Tier**：1

### [28] Evaluat 性能回归 CI/CD
- **摘要**：每 build 对比 known-good baseline，可测量劣化即 fail build。
- **关键引句**："compares every new build against a known-good baseline and fails the build when a change makes pages measurably [slower]"
- **来源类型**：公司博客 ｜ **Tier**：3

### [29] Grafana Soak Testing
- **摘要**：soak 常规 8–72 小时持续负载，监测趋势漂移与资源泄漏，判据为长期稳定性。
- **关键引句**："This test verifies the system stability and reliability over extended periods of use."
- **来源类型**：厂商博客 ｜ **Tier**：2

### [30] MDPI FPGA-to-Silicon Verification
- **摘要**：2026 同行评审方法学，FPGA 原型到硅片建立 HW/SW 等价性证据链。
- **关键引句**：（方法学框架性引用）
- **来源类型**：期刊论文 ｜ **Tier**：1

### [31] OCP MX Specification v1.0
- **摘要**：定义 MXFP8/MXFP6/MXFP4/MXINT8、block32、E8M0 共享 scale。（原 PDF 抓取损坏，细节经 [32] 交叉确认）
- **关键引句**："MX-compliant data formats are expected to improve the energy efficiency of AI at datacenter scale as well as on many AI endpoints"
- **来源类型**：官方规范 ｜ **Tier**：1 [foundational]

### [32] An Empirical Study of Microscaling Formats
- **摘要**：对 OCP MX 的学术复述与低精度实证。
- **关键引句**："MXFP8, which supports both E5M2 and E4M3 data types"
- **来源类型**：学术论文 ｜ **Tier**：1 [foundational]

### [33] Block Rotation is All You Need for MXFP4 Quantization
- **摘要**：评估口径 WikiText2+5 个 zero-shot 任务；MXFP4 RTN 下游 57.26% vs FP16 62.59%（约 5.3 pp，经引用核查修正）；需旋转/Hadamard 预处理。
- **关键引句**："methods nearer the top-left are closer to the FP16 baseline, indicating better"
- **来源类型**：学术论文 ｜ **Tier**：1

### [34] NVIDIA gpt-oss QAT
- **摘要**：原生 MXFP4 精度不稳，先上转 BF16 SFT 再 QAT；pass rate 16%/30%→98%；NVFP4 验证损失再优 2–3%。
- **关键引句**："its native MXFP4 precision hasn't yet proven stable accuracy"
- **来源类型**：官方博客 ｜ **Tier**：1

### [35] Perplexity GPT-OSS on Day 0
- **摘要**：证明 MXFP4 已是可部署端点格式。
- **关键引句**："The open-weight models are shipped using MXFP4 quantization, which helps them fit in the memory on consumer-grade hardware"
- **来源类型**：公司博客 ｜ **Tier**：2

### [36] Comprehensive Evaluation of Quantization Strategies (ACL 2024)
- **摘要**：大规模对比 GPTQ/AWQ 等；结论 perplexity 对多数基准可靠但需下游任务验证补足。
- **关键引句**："perplexity serves as a reliable performance indicator for quantized LLMs across the majority of the benchmarks"
- **来源类型**：会议论文 ｜ **Tier**：1 [foundational]

### [37] Red Hat 50 万次量化评测
- **摘要**：50 万+ 次评测：小模型量化损失更大，需按模型规模分档评估。
- **关键引句**："Quantized LLMs achieve near-full accuracy with minimal trade-offs after 500K+ evaluations"
- **来源类型**：公司工程文章 ｜ **Tier**：2

### [38] Exploiting LLM Quantization
- **摘要**：量化可破坏安全对齐；评估需含安全/行为类任务。
- **关键引句**："benign behavior in full precision but becomes malicious when quantized"
- **来源类型**：学术论文 ｜ **Tier**：1

### [39] NVIDIA TensorRT INT8 QAT
- **摘要**：INT8 vs FP32 精度对比报告口径；PTQ 掉点用 QAT 补回；检测掉点大于分类。
- **关键引句**：（官方口径方法学引用）
- **来源类型**：官方博客 ｜ **Tier**：1 [foundational]

### [40] TensorRT Issue #1114
- **摘要**：从业者实测 INT8 PTQ 检测/分割掉点比分类明显，逐算子误差分析定位。
- **关键引句**：（实测报告）
- **来源类型**：社区讨论 ｜ **Tier**：3

### [41] Quantization Robustness to Input Degradations
- **摘要**：degradation-aware 校准；评估维度扩展到退化输入下的 mAP。
- **关键引句**："degradation-aware calibration strategy for Static INT8 PTQ, where the TensorRT calibration process is exposed to..."
- **来源类型**：学术论文 ｜ **Tier**：1

### [42] Qualcomm Accuracy Debugger
- **摘要**：要求代表性输入集计算量化范围；golden（框架浮点）vs 端上量化逐层比对定位误差层。（[snippet only]，引句截断）
- **关键引句**："finds inaccuracies in a neural-network at the layer level. The tool compares the golden outputs produced by running a model through a specific ML framework ..."
- **来源类型**：官方文档 ｜ **Tier**：1

### [43] HF KV Cache Quantization
- **摘要**：FP8/INT4 KV 单独评估、per-layer 可配置；FP8 损失小、INT4 需选择性保留敏感层。
- **关键引句**："KV Cache Quantization reduces memory usage for long-context text generation in LLMs with minimal impact on quality"
- **来源类型**：官方博客 ｜ **Tier**：2

### [44] LMSYS NVFP4 KV Cache
- **摘要**：FP8 KV 约减半带宽；FP4 KV 用于 agentic 长上下文，需精度策略防劣化。
- **关键引句**："Storing K/V values in FP8 instead of BF16 roughly halves the data footprint"
- **来源类型**：团队博客 ｜ **Tier**：2

### [45] r/LocalLLaMA KV 量化讨论
- **摘要**：社区批判：KV 量化在精确抽取/边缘案例失效——KV 评估需含对抗性/精确性任务。
- **关键引句**："It breaks in edge cases, and it breaks when you need something exact"
- **来源类型**：社区讨论 ｜ **Tier**：3

### [46] apxml 量化 LLM 评估指标
- **摘要**：perplexity 与个别下游任务可能背离。
- **关键引句**："Perplexity doesn't always correlate perfectly with performance on specific downstream tasks"
- **来源类型**：课程材料 ｜ **Tier**：3

### [47] MLPerf Inference Rules（inference_rules.adoc）
- **摘要**：全场景最短 600 s；Offline ≥24,576 样本；单流最少查询 23,886（90% 置信）/Server 270,336（99% 置信）；分位数固定 90%/99%-ile；valid run 双门槛；early stopping；逐模型 TTFT/TPOT 上限（例 Llama3.1-8B Server 2000 ms/100 ms）与 max_new_tokens。
- **关键引句**："The numbers were selected to be sufficiently large to statistically verify that the system meets the latency requirements."；valid runs "must meet latency requirement and quality requirement"
- **来源类型**：官方规则文档 ｜ **Tier**：1

### [48] mlperf_client README
- **摘要**：运行参数 Iterations（示例 1000）、WarmUp（默认 1）、Delay；结果经期望 token 序列与 SHA256 双重校验。
- **关键引句**：`"Iterations": 1000, "WarmUp": 1, "Delay": 0`（llama2 场景示例 5 秒）
- **来源类型**：官方仓库 ｜ **Tier**：1

### [49] Redis P95 Latency
- **摘要**：p95 为实用默认档位；尖峰成因与测量方式。
- **关键引句**："P95 latency is the threshold below which 95% of your requests complete."
- **来源类型**：厂商博客 ｜ **Tier**：2

### [50] Aerospike P99 Latency
- **摘要**：p99 与 p95 层级关系及高分位对尾部的捕捉。
- **关键引句**："p95 latency is the level under which 95% of requests finish, where the slowest 5% exceed it."
- **来源类型**：厂商博客 ｜ **Tier**：2

### [51] Medium P95/P99 原理
- **摘要**：社区通行取舍：两档并用各司其职。
- **关键引句**："P95 ensures the service is generally good. P99 ensures it is exceptionally reliable by hunting down..."
- **来源类型**：个人博客 ｜ **Tier**：3

### [52] MLPerf Client v1.5/v2.0 发布页
- **摘要**：已定位未抓取；rules 全文与热态/报告字段要求留待后续核对。
- **关键引句**：无
- **来源类型**：官方发布页 ｜ **Tier**：1

### [53] Rigorous Benchmarking in Reasonable Time (ISMM 2013)
- **摘要**：基准实验设计 cookbook：pilot 实验→确定重复次数→置信区间。
- **关键引句**："We present our methodology as a cookbook that guides researchers on the number of repetitions they should run to obtain reliable results."
- **来源类型**：学术论文 ｜ **Tier**：1 [foundational]

### [54] MLPerf Mobile Inference Benchmark (MLSys 2022)
- **摘要**：面向手机 SoC（含 NPU）的同行评审基准，为每场景冻结采样方案。核查 [snippet only]。
- **关键引句**："Single-stream mode measures the 90th-percentile latency over at least 1,024 samples for a minimum run time of 60 seconds to achieve a stable performance result."
- **来源类型**：会议论文 ｜ **Tier**：1

### [55] Drizz.dev Soak/Endurance Testing
- **摘要**：典型泛 QA 指南，仅定性建议；作为"长稳量化判据无公开标准"的存在性证据。
- **关键引句**："A 30-minute load test can pass clean and still miss a memory leak that crashes app after 14 hours of real-world use."
- **来源类型**：个人博客 ｜ **Tier**：3
