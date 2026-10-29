# Hive NPU Core 总体架构

文档版本：`v0.19.0-draft`

修订日期：`2026-10-29`

> 本文规定 NPU core 的组成、系统接口和设计约束，用于设计评审。要求状态、实现状态和验收结论分别记录；协议时序、寄存器编码和验证用例由后续硬件规格定义。

## 版本控制

| 版本号 | 日期 | 修改内容 |
|---|---|---|
| v0.19.0-draft | 2026-10-29 | 硬件规格新增 SoC SRAM：多 core 与 main CPU 共享的显式管理片上 SRAM，经 system interconnect 按 window 访问；local SRAM 明确为 core 私有，共享容量/带宽独立核算；新增 C040/C041 并扩展 C011/S003 范围，有效 AR 71 条（C41/H2/S6/P22）；共享 tensor SRAM 不再列为非基线，容量/位宽/bank 及访问协议待冻结，模型服务与质量门槛不变 |
| v0.18.0-draft | 2026-10-28 | 首轮语言模型改为 Qwen3.5-0.8B、Qwen3.5-2B、DeepSeek-R1-Distill-Qwen-1.5B，保留 YOLO11n/s；部署基线统一为 MXFP8/MXFP4 W/A 全量化；精简2.1格式说明及2.2.1 SKU表，移除表内AR；同步容量、服务和验收范围，P008–P013服务数值待确认、P020改为MX数值验收，保留其余硬件/检测/质量及Pro扩展指标 |
| v0.17.0-draft | 2026-10-28 | 现状改指当前架构事实；软件任务管理统一称软件scheduler；编号规则移除过程说明；H001/H002明确4266 MT/s，撤销H003/H004且不约束器件密度与rank；撤销P019及P024–P029，P类聚焦硬件性能、LLM/检测服务与数值精度，清理对应正文约束；69条有效AR（C39/H2/S6/P22），保留其余指标与控制职责 |
| v0.16.2-draft | 2026-10-28 | 精简重复状态声明，将正文评审过程用语改为范围与状态说明，拆分模型规划及验收长段落；批准指标、78条有效AR、技术约束和评审状态不变 |
| v0.16.1-draft | 2026-10-28 | P1-1人工确认剔除，不在硬件arch细化高层调度软件与底层握手/资源释放流程；P1-4暂缓至后续性能仿真，不冻结分块、驻留或重叠方案；78条AR、资源规模、控制边界及原容量/服务验收要求不变 |
| v0.16.0-draft | 2026-10-28 | P1-5收口为整个NPU区域独立电源与时钟域，支持独立调频和受控电源关断；补充域边界、正常切换与重新初始化，C024扩展POWER_OFF，新增C038/C039；78条有效AR，500 MHz及原服务门槛不变，不默认retention、独立调压或逐core电源域；P1-5正文人工确认闭环，切换与物理细项仍待冻结，P1-7详细PMU定义及P0-4/P0-7保持暂缓，不表示实现或验收通过 |
| v0.15.0-draft | 2026-10-28 | P1-6编号/范围/追溯人工确认闭环，不提升目标AR状态，也不表示实现或验收通过；P1-3移除G0边缘端QoS有界服务，P1-2移除详细故障恢复/隔离架构要求，留实现阶段考虑；保留正常CDC/reset/clock-gating、反压响应正确性与基本前进性；76个AR及已批准服务指标不变，P0-4/P0-7保持暂缓，P1-5仅分析、待评审，不实施新clock/power表 |
| v0.14.0-draft | 2026-10-28 | S表收敛为六条硬件限制对应的固件软件callout，撤销17个S编号并清理正文追溯；新增Qwen3.5-2B模型身份，名义范围扩至0.5B–2B，不冻结执行格式或新指标；原四模型门槛不变，P0-4/P0-7保持暂缓，本轮表述等待人工审查 |
| v0.13.0-draft | 2026-10-27 | P1-6新增前置AR清单，C/H/S/P四表独立编号；正文与验收建立追溯，区分要求批准、实现和验收状态；P0-4/P0-7保持暂缓，不新增候选参数或PPA保证值 |
| v0.12.0-draft | 2026-10-27 | P0-5/P0-6 文档评审闭环；批准语言模型与双YOLO的服务/质量门槛及测量口径，规范值落入12.8；容量、算子与服务证据作为后续实施验收交付物，不宣称已实测通过；P0-7开始执行方案分析 |
| v0.11.0-draft | 2026-10-27 | 检测基线包含官方 YOLO11n/YOLO11s；区分 MX block scale 与不支持的 NVFP4 额外 tensor/global scale；local CPU/VPU 明确属于 NPU core，软件任务管理 Queen 连接主 CPU 与 local CPU，scheduler 仅派发 GEMM/DMA，VPU 由 local CPU 直接管理 |
| v0.10.0-draft | 2026-10-27 | 首轮性能 signoff 改用 Qwen3-0.6B 与 DeepSeek-R1-Distill-Qwen-1.5B 的 FP8 checkpoint；同源 BF16→MXFP8/MXFP4 先采用 A/B block32、C FP32，补充模型清单、数值与服务阶段门；吞吐候选及预算迁入性能分析，P0-4 保持暂缓 |
| v0.9.0-draft | 2026-10-27 | G0 Pro 内存基线提高为 8 GiB，16 GiB 为非基线扩容选项；G0 使用片内主控 CPU 术语，明确 scheduler 同步完成与 tracing 分离，硬件 ring 保持候选，错误协议暂缓 |
| v0.8.0-draft | 2026-10-27 | 落实批准的 B 平衡方案：G0 512 KiB/core、4 GiB LPDDR4x x32，后续 G0 Pro 1 MiB/core、4 GiB x64；统一 DMA、VPU、控制与非一致访问基线，区分 MUST、实现候选、非基线能力及冻结条件 |
| v0.7.0-draft | 2026-10-26 | G0 工作负载基线收敛为 0.5B–1.5B W4 模型和 YOLO 等视觉模型；两核 1T 定义为 G0，后续四核 2T 定义为 G0 Pro |
| v0.6.0-draft | 2026-10-26 | 将 VPU 第 12.7 节改为首版性能、能力、接口与验收要求；设定每 core 256-bit、500 MHz、FP32 8 / FP16 16 GFLOPS 草案指标；产品对比、扩展档位、工作负载和带宽推导迁至独立性能分析文档 |
| v0.5.0-draft | 2026-10-26 | 补充 INT4/MXFP4 稠密峰值与 W4A8 区别；增加外部 FP32/FP16 VPU 的职责、公开产品配比参考、候选算力和算子/带宽约束，VPU 配置仍待仓库外实现确认 |
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

范围以单 core 为主，多核 cluster、系统互联、同步单元和 DRAM 作为 core 之外的集成上下文。local CPU 与 VPU 均属于 NPU core；VPU 可视为挂接 local CPU 的 DSA，由 local CPU 直接管理。CPU/VPU 的“外部”仅指实现代码来自当前仓库之外，不表示它们在 NPU core 或 SoC 之外；本文不定义其微架构，也不重复 GEMM/DMA 的完整内部规格。

### 1.2 状态与规范层次

| 标记 | 含义 |
|---|---|
| **现状** | 当前架构已经明确的组成、能力和约束事实；源码依据架构要求实现或优化，代码进度不决定架构现状 |
| **批准基线（MUST）** | 已批准的产品配置或能力要求，实现必须满足；不代表已有 RTL 或验收结果 |
| **目标** | 未明确批准的架构行为，仍须评审确认 |
| **候选** | 用于实现评估的参数或组织形式；须经过指定阶段门才能成为保证值 |
| **待确认** | 缺少决策或证据，不能作为保证值 |
| **非基线** | 不属于 G0/G0 Pro 基础交付能力；引入需单独决策，不能作为基线依赖 |

模型身份、评估路线或验收门槛获批，只确定其适用范围和实现要求。实现完成及验收通过须分别提供证据；文档评审通过不改变这两项状态。

第 1.5 节列出架构需求 AR，第 2.2 节展开批准配置与候选冻结条件，第 13 章记录实现差距和待决策事项，第 14 章规定验收方法及逐 AR 证据。未批准的协议仍按目标或待确认标注。本文使用架构 AR，不分配模块 Feature ID；模块规格独立维护。

### 1.3 术语与名称

除文档标题中的项目名外，正文、表格与框图统一使用功能名称：

| 术语 | 含义 |
|---|---|
| NPU core | 单个独立调度、计算和本地存储单元 |
| NPU cluster | 共享系统互联、同步单元和 DRAM 的多个 NPU core |
| NPU区域 | 所有NPU cores及NPU侧互联、同步资源组成的电源/时钟管理区域；不包含main CPU、SoC system interconnect和DRAM子系统 |
| GEMM | 矩阵计算单元，包括私有缓冲、阵列、输出处理和本地读写端口 |
| 硬件 scheduler | core 内由 local CPU 使用的命令派发、依赖跟踪、完成和事件管理单元，仅管理 GEMM/DMA，不管理 VPU |
| 软件 scheduler | 主 CPU 与 NPU 间的软件任务管理器，负责任务编排、软件队列和任务完成管理；与 core 内硬件 scheduler 分层 |
| DMA | 通用多维数据搬运单元；与 GEMM 自带的本地读写端口区分 |
| local SRAM | 单个 NPU core 私有、由软件显式管理的 scratchpad，非硬件透明缓存 |
| SoC SRAM | 多个 NPU core 与 main CPU 共享的片上 SRAM，经 system interconnect 访问，同样为显式管理的 scratchpad |
| local interconnect | 多发起方到 local SRAM banks 的互联 |
| system interconnect | core、peer window、同步单元、主控和 DRAM 之间的互联 |
| synchronization unit | 提供共享同步状态和原子操作的独立单元，不承载 tensor 数据 |
| main CPU / local CPU | main CPU 是 G0 SoC 内的主控 CPU 和系统任务提交方；local CPU 是 NPU core 的本地控制处理器；G0 不存在外部 Host |
| MAC | 一次乘加贡献；换算为运算次数时按 2 operations/MAC，不与独立输出数量混用 |

本文未加“软件”前缀的 scheduler 指 core 内硬件 scheduler。实际源码文件名仅在附录 A 保留，以支持实现追溯；它们不是本文的架构组件名称。

### 1.4 文档组织

| 评审问题 | 阅读位置 |
|---|---|
| 必须满足哪些要求、由谁落实 | 第1.5节：C/H/S/P四类AR清单；第14.4节：逐项证据与责任映射 |
| 做什么、为何这样做 | 第 2–3 章：目标、总体架构与边界 |
| 数据怎样计算和搬运 | 第 4–6 章：计算、存储和互联 |
| 命令如何执行并对软件可见 | 第 7–9 章：执行模型、地址与一致性、系统集成 |
| 生命周期和非功能要求 | 第 10–11 章：时钟电源、可靠性与观测 |
| 带宽和容量是否满足工作负载 | 第 12 章：性能模型 |
| 哪些尚未确定、怎样验收 | 第 13–14 章：取舍、实现差距与验证 |

组织方式参考 NVIDIA 官方 GPU 架构文章的由总览到子系统叙述，以及 NVDLA 架构/集成文档对接口和生命周期的分层。参考依据与不适用内容见附录 B。

### 1.5 架构需求清单（AR）

#### 1.5.1 编号与追溯规则

AR（Architecture Requirement）使用 `AR-HIVE-Cxxx`、`AR-HIVE-Hxxx`、`AR-HIVE-Sxxx`、`AR-HIVE-Pxxx` 四个命名空间，分别在四张表内从001独立递增。C为芯片硬件设计，H为封装/板卡硬件设计，S为硬件限制对应的固件软件callout，P为性能与质量验收要求。C/H/S表示责任领域，P表示验收目标；P可由多个C/H/S共同满足，后续落实实现责任时不改P编号。

- 一个编号对应一个可独立核验的要求或配置项；G0/Pro对同一配置项的分档值可共用编号，分别验收。
- 编号不随章节调整而重排，撤销后保留记录且不复用；新要求在所属表末尾续号。实质改变既有要求须记录版本并重新评审。
- 清单是AR规范条目的入口，正文按同一编号展开范围、条件、原理与验收，不另建同义要求；重复展示的数值必须一致，发现冲突须审查修正。
- AR 的状态按第 1.2 节解释，编号不改变批准状态；接口与数值细项按各条目所列条件冻结。
- AR 与模块 Feature ID、P0/P1/P2 审查问题编号分别管理。
- 责任规格使用完整 AR ID 建立追溯；正文引用链接指向对应清单条目。逐项证据与验收结论的记录规则见第 14.4 节。

#### 1.5.2 C类：芯片硬件设计要求

| AR ID | 规范要求 | 适用SKU | 要求状态 | 责任规格 | 正文解释／验收 |
|---|---|---|---|---|---|
| <a id="ar-hive-c001"></a>**AR-HIVE-C001** | G0应作为含片内主控CPU的完整SoC，不依赖外部Host。 | G0／G0 Pro | 已批准 | SoC集成规格 | §1.1、§3.1／§14.1 |
| <a id="ar-hive-c002"></a>**AR-HIVE-C002** | 每个NPU core应集成local CPU、VPU、GEMM、scheduler、通用DMA及local SRAM；仓库外实现来源不改变物理归属。 | G0／G0 Pro | 已批准 | core集成规格 | §3.2–3.3／§14.1 |
| <a id="ar-hive-c003"></a>**AR-HIVE-C003** | NPU core数量应为G0两核、G0 Pro四核。 | G0／G0 Pro | 已批准 | SoC配置规格 | §2.2、§3.1／§14.2 |
| <a id="ar-hive-c004"></a>**AR-HIVE-C004** | GEMM应满足500 MHz产品目标，不以提高频率作为1T/2T达标前提。 | G0／G0 Pro | 已批准；物理签核待完成 | GEMM／时钟规格 | §2.2、§10.1／§14.3 |
| <a id="ar-hive-c005"></a>**AR-HIVE-C005** | 每core GEMM应采用T=16、256个PE、16-bit物理输入lane的配置。 | G0／G0 Pro | 已批准 | GEMM规格 | §2.2、§4.1／§14.3 |
| <a id="ar-hive-c006"></a>**AR-HIVE-C006** | GEMM应保留INT8、INT4、MXE4M3、MXE5M2、MXE2M1、FP8E4M3、FP8E5M2输入格式及模块规格定义的合法组合，不承诺任意输入/输出混配。 | G0／G0 Pro | 已批准 | GEMM规格 | §4.1、附录A／§14.3 |
| <a id="ar-hive-c007"></a>**AR-HIVE-C007** | GEMM结果buffer应采用cBufferDepth=64，T=16时单命令M≤48。 | G0／G0 Pro | 已批准 | GEMM规格 | §2.2、§12.2／§14.1 |
| <a id="ar-hive-c008"></a>**AR-HIVE-C008** | GEMM应通过32-bit local offset访问local SRAM，不直接访问DRAM，也不假定支持外部partial-sum输入。 | G0／G0 Pro | 已批准 | GEMM／本地桥规格 | §3.3、§8.1、§12.2／§14.1–14.2 |
| <a id="ar-hive-c009"></a>**AR-HIVE-C009** | 每core local SRAM可用数据容量应为G0 512 KiB、G0 Pro 1 MiB，不含ECC校验位及GEMM私有buffer。 | G0／G0 Pro | 已批准 | SRAM／core集成规格 | §2.2、§5.2／§14.2–14.3 |
| <a id="ar-hive-c010"></a>**AR-HIVE-C010** | 每core local SRAM应采用8×256-bit banks及32-byte低位交织，bank=addr[7:5]。 | G0／G0 Pro | 已批准；macro/端口待冻结 | SRAM／本地互联规格 | §5.2／§14.2 |
| <a id="ar-hive-c011"></a>**AR-HIVE-C011** | NPU数据存储应采用显式管理的scratchpad，不以NPU透明缓存作为基线依赖；共享存储层同样显式管理，不提供自动填充或回写。 | G0／G0 Pro | 已批准 | 存储／core集成规格 | §2.2.3、§5.1、§5.5／§14.1 |
| <a id="ar-hive-c012"></a>**AR-HIVE-C012** | 每core应配置两个通用DMA engines，各为256-bit data、40-bit address，最大64 beats/burst。 | G0／G0 Pro | 已批准 | DMA／桥接规格 | §2.2、§6.3／§14.1–14.3 |
| <a id="ar-hive-c013"></a>**AR-HIVE-C013** | DMA engine应支持多读在途，不能仅增加AXI adapter深度代替引擎级并发。 | G0／G0 Pro | 已批准；深度/返回协议待冻结 | DMA／桥接规格 | §6.3、§12.4／§14.2–14.3 |
| <a id="ar-hive-c014"></a>**AR-HIVE-C014** | scheduler应配置8 slots/core、单GEMM执行上下文，支持无依赖计算与搬运并行，不要求同拍多派发。 | G0／G0 Pro | 已批准 | scheduler规格 | §7.1／§14.1–14.2 |
| <a id="ar-hive-c015"></a>**AR-HIVE-C015** | scheduler应仅管理local CPU提交的GEMM/DMA命令、依赖及同步资源，不管理VPU或主CPU软件任务队列。 | G0／G0 Pro | 已批准 | scheduler／CPU接口规格 | §3.2–3.3、§7.2／§14.1 |
| <a id="ar-hive-c016"></a>**AR-HIVE-C016** | GEMM/DMA完成应经同步接口更新scheduler指定资源；完成关联不得以tracing tag为依据。 | G0／G0 Pro | 已批准边界；同步协议暂缓 | 引擎同步／scheduler规格 | §7.2、§7.4／§14.1–14.2 |
| <a id="ar-hive-c017"></a>**AR-HIVE-C017** | 每core system/peer数据接口应为独立AXI4入口、256-bit data、40-bit physical address。 | G0／G0 Pro | 已批准；AXI profile暂缓 | 系统互联／AXI桥规格 | §6.4、§9.1／§14.2 |
| <a id="ar-hive-c018"></a>**AR-HIVE-C018** | CSR接口应采用32-bit AXI4-Lite。 | G0／G0 Pro | 已批准；事务/寄存器待冻结 | CSR规格 | §9.1／§14.1 |
| <a id="ar-hive-c019"></a>**AR-HIVE-C019** | IRQ应采用local/main CPU分路level通知及粘滞状态、mask/clear，不依赖MSI。 | G0／G0 Pro | 已批准；路由细项待冻结 | CSR／IRQ规格 | §9.2／§14.1–14.2 |
| <a id="ar-hive-c020"></a>**AR-HIVE-C020** | cluster应设置独立同步单元，提供READ/WRITE/FETCH_ADD，不承载tensor数据。 | G0／G0 Pro | 已批准；容量/原子协议暂缓 | 同步单元规格 | §8.4／§14.2 |
| <a id="ar-hive-c021"></a>**AR-HIVE-C021** | main CPU与NPU应采用非一致AXI访问，不依赖硬件snoop。 | G0／G0 Pro | 已批准；一致性契约暂缓 | SoC／内存接口规格 | §8.3／§14.2 |
| <a id="ar-hive-c022"></a>**AR-HIVE-C022** | 每core应配套独立的256-bit等效VPU执行能力，目标500 MHz，支持FP32/FP16，由local CPU直接控制。 | G0／G0 Pro | 已批准；仓库外实现待确认 | local CPU／VPU规格 | §4.4、§12.7.1／§14.3 |
| <a id="ar-hive-c023"></a>**AR-HIVE-C023** | 每core VPU应具有独立1×256-bit local请求口，读写合计每拍最多一请求。 | G0／G0 Pro | 已批准；返回/在途细项待冻结 | VPU／本地adapter规格 | §9.1、§12.7.1／§12.7.4、§14.3 |
| <a id="ar-hive-c024"></a>**AR-HIVE-C024** | NPU区域应支持RUN、IDLE、CLOCK_GATED和POWER_OFF；POWER_OFF不保证内部状态保持，不依赖专用retention或独立调压。 | G0／G0 Pro | 已批准状态集合；切换细项待冻结 | 时钟／电源规格 | §10.3／§14.2 |
| <a id="ar-hive-c025"></a>**AR-HIVE-C025** | local interconnect应按bank路由与仲裁，允许不同banks的无冲突请求同拍推进。 | G0／G0 Pro | 目标 | 本地互联规格 | §6.1／§14.2 |
| <a id="ar-hive-c026"></a>**AR-HIVE-C026** | 桥接/互联应保持请求源及返回归属，按返回容量限制在途请求，反压时不丢失已接受事务的响应。 | G0／G0 Pro | 目标；接口协议暂缓 | DMA／互联／VPU adapter规格 | §6.2–6.3、§12.7.3／§14.2 |
| <a id="ar-hive-c027"></a>**AR-HIVE-C027** | peer SRAM访问应经受范围约束的window路由，并参加目标core的local bank仲裁。 | G0／G0 Pro | 目标；接口协议暂缓 | peer／本地互联规格 | §6.4、§9.3／§14.2 |
| <a id="ar-hive-c028"></a>**AR-HIVE-C028** | 仲裁与流控应保证有限背压下各主设备前进，不让响应排空依赖获取已耗尽的新请求credit。 | G0／G0 Pro | 目标；仅正常前进性 | 互联／流控规格 | §6.1、§6.5、§12.7.3／§14.2 |
| <a id="ar-hive-c029"></a>**AR-HIVE-C029** | 跨域接口应成套处理地址/数据/响应与复位，IRQ/event跨域不得丢失通知。 | G0／G0 Pro | 目标；CDC/reset组织待冻结 | CDC／reset规格 | §10.1／§14.2 |
| <a id="ar-hive-c030"></a>**AR-HIVE-C030** | 正常停钟、调频或断电前应停止新任务提交并完成既有工作，确认CPU/VPU阶段安全并排空GEMM/DMA、peer/AXI事务及返回队列；恢复供电/时钟并满足必要初始化后才重新接受访问。 | G0／G0 Pro | 已批准正常切换要求；协议细项待冻结 | 时钟／电源／core集成规格 | §10.2–10.3／§14.2 |
| <a id="ar-hive-c031"></a>**AR-HIVE-C031** | GEMM local offset桥接应进行窗口译码和范围检查，不截断40-bit系统地址伪装成本地合法地址。 | G0／G0 Pro | 目标；地址映射待冻结 | 地址窗口／桥接规格 | §8.1／§14.2 |
| <a id="ar-hive-c032"></a>**AR-HIVE-C032** | 系统应定义CSR/debug访问许可及地址window保护边界，不宣称已有多租户/IOMMU隔离。 | G0／G0 Pro | 目标；保护契约待冻结 | SoC保护／debug规格 | §11.2／§14.2 |
| <a id="ar-hive-c033"></a>**AR-HIVE-C033** | PMU应覆盖GEMM阶段、DMA字节/在途、bank冲突、AXI反压及依赖等待，并明确计数、溢出、清零和snapshot语义。 | G0／G0 Pro | 目标；P1-7暂缓，详细事件/时间基准未冻结 | PMU规格 | §11.3／§14.3 |
| <a id="ar-hive-c034"></a>**AR-HIVE-C034** | VPU应支持FP32/FP16 add、mul、FMA、比较与选择；FMA仅在最终结果舍入。 | G0／G0 Pro | 已批准；数值细项待冻结 | VPU算术规格 | §12.7.2／§12.7.4、§14.3 |
| <a id="ar-hive-c035"></a>**AR-HIVE-C035** | VPU应支持FP16↔FP32转换及FP16输入转换后以FP32累加/归约，不要求原生混合FMA。 | G0／G0 Pro | 已批准；转换细项待冻结 | VPU算术规格 | §12.7.2／§12.7.4、§14.3 |
| <a id="ar-hive-c036"></a>**AR-HIVE-C036** | VPU应支持sum/max及mask/tail，无效元素不得更新结果、参与归约或产生越界访问。 | G0／G0 Pro | 已批准；归约误差待冻结 | VPU算术／访存规格 | §12.7.2／§12.7.4、§14.3 |
| <a id="ar-hive-c037"></a>**AR-HIVE-C037** | VPU数值契约应明确舍入、NaN/Inf、subnormal、signed zero及异常可见性，不能仅以格式支持判定通过。 | G0／G0 Pro | 已批准要求；数值细项待冻结 | VPU数值规格 | §12.7.2／§12.7.4、§14.3 |
| <a id="ar-hive-c038"></a>**AR-HIVE-C038** | 整个NPU区域应具有独立于main CPU、系统互联及DRAM子系统的可关断电源域；断电控制与边界隔离须在域外可用，不以引擎clock-gating替代电源关断。 | G0／G0 Pro | 已批准能力；电气边界/时序待冻结 | SoC电源域／NPU集成规格 | §2.2、§10.1–10.3／§14.2 |
| <a id="ar-hive-c039"></a>**AR-HIVE-C039** | NPU区域应具有独立时钟域并支持运行频率调整，不要求主CPU、system或DRAM时钟随之改变；500 MHz仍为原SKU与服务验收基准，停钟不替代调频。 | G0／G0 Pro | 已批准能力；频点/切换细项待冻结 | SoC时钟／CDC规格 | §2.2、§10.1–10.2／§14.2–14.3 |
| <a id="ar-hive-c040"></a>**AR-HIVE-C040** | SoC应提供可由全部NPU core与main CPU共享访问的片上SoC SRAM，经system interconnect按受范围约束的window寻址，并作为显式管理的scratchpad，不提供自动填充、替换或回写。 | G0／G0 Pro | 已批准能力；容量/位宽/端口待冻结 | SoC存储／system互联规格 | §2.2、§5.5、§8.1／§14.2 |
| <a id="ar-hive-c041"></a>**AR-HIVE-C041** | SoC SRAM不计入每core local SRAM可用容量，也不替代其带宽；GEMM与VPU仅访问local SRAM，跨层数据交换应由DMA搬运或授权CPU访问完成。 | G0／G0 Pro | 已批准 | SoC存储／core集成规格 | §5.1、§5.5、§12.2／§14.2 |

#### 1.5.3 H类：封装／板卡硬件设计要求

本表规定外部存储的类型、容量、总数据位宽和速率，不约束器件密度或 rank 组织形式。

| AR ID | 规范要求 | 适用SKU | 要求状态 | 责任规格 | 正文解释／验收 |
|---|---|---|---|---|---|
| <a id="ar-hive-h001"></a>**AR-HIVE-H001** | G0外部存储应为4 GiB LPDDR4x，总数据位宽x32，速率4266 MT/s。 | G0 | 已批准 | 封装／板卡／内存配置规格 | §2.2、§5.4、§12.4／§14.2–14.3 |
| <a id="ar-hive-h002"></a>**AR-HIVE-H002** | G0 Pro外部存储应为8 GiB LPDDR4x，总数据位宽x64，速率4266 MT/s；16 GiB不属于基线。 | G0 Pro | 已批准 | 封装／板卡／内存配置规格 | §2.2、§5.4、§12.4／§14.2–14.3 |

*Note：H003/H004 在 v0.17.0-draft 撤销，不复用；容量、位宽与速率由 H001/H002 规定。*

#### 1.5.4 S类：硬件限制对应的固件软件 callout

本表只记录硬件无法承担、非基线能力或硬件代价过大时需要固件软件补齐的动作，不定义完整软件体系。每条须明确硬件限制及对应动作；控制归属归C类，工作负载、数值和服务验收留在第2.1、12.8及14.3节，不另列软件设计AR。

| AR ID | 硬件限制／callout原因 | 固件软件应承担的动作 | 适用SKU | 要求状态 | 责任规格 | 正文解释／验收 |
|---|---|---|---|---|---|---|
| <a id="ar-hive-s003"></a>**AR-HIVE-S003** | local SRAM与SoC SRAM都是显式scratchpad，没有自动填充、替换或脏数据回写。 | 应显式发起数据装载与必要写回；容量不足时分批搬运，不依赖透明缓存补给。 | G0／G0 Pro | 已批准边界；搬运生命周期待冻结 | 固件／DMA控制规格 | §5.1、§5.4–5.5／§14.1–14.2 |
| <a id="ar-hive-s004"></a>**AR-HIVE-S004** | cBufferDepth=64、T=16时，GEMM单命令M≤48；增加local SRAM不扩大此上限。 | 应将更大M拆为合法子命令，不能提交超限命令。 | G0／G0 Pro | 已批准 | 固件／GEMM控制规格 | §2.2、§12.2／§14.1–14.3 |
| <a id="ar-hive-s006"></a>**AR-HIVE-S006** | scheduler不管理VPU及跨消费者的SRAM所有权，DMA内部hazard检查不覆盖全部外部访问。 | 应在跨引擎消费或复用区域前确认所需阶段完成与结果可见性，不以估计周期或busy下降替代。 | G0／G0 Pro | 目标；所有权/可见性协议暂缓 | 固件／引擎同步规格 | §7.2–7.4、§8.2／§14.2 |
| <a id="ar-hive-s007"></a>**AR-HIVE-S007** | main CPU与NPU之间是非一致AXI，硬件不提供snoop一致性。 | 对可缓存共享数据，应在交接前后执行所需cache维护及提交/完成屏障，不把引擎同步事件当作cache维护。 | G0／G0 Pro | 已批准责任；平台契约暂缓 | 固件／非一致内存访问规格 | §8.3／§14.2 |
| <a id="ar-hive-s018"></a>**AR-HIVE-S018** | GEMM不支持外部partial-sum输入，独立K子命令不能自动续接前一命令的累加结果。 | 需要拆K为独立子命令时，应组织VPU或软件显式累加FP32输出；不得仅拼接命令假定完成完整K归约。 | G0／G0 Pro | 已批准约束；实施待验证 | 固件／FP32累加控制规格 | §12.2／§14.3 |
| <a id="ar-hive-s022"></a>**AR-HIVE-S022** | VPU基线不强制提供exp、reciprocal、rsqrt专用硬件，FMA峰值不能代表其服务能力。 | 未由硬件提供时，应以向量软件补齐所需函数，并满足冻结的输入域、误差和服务时间要求。 | G0／G0 Pro | 已批准要求；数值/服务细项待冻结 | 固件／VPU函数规格 | §4.4、§12.7.2、§12.7.4／§14.3 |

撤销记录（v0.14.0-draft）：S001–S002、S005、S008–S017、S019–S021、S023 共 17 个编号退出有效清单，不复用。原条目属于软件组织、工作负载/部署或验收事项，相关硬件控制与验收要求保留在正文及 C/P 类。S003/S004/S006/S007/S018/S022 保留原号，仅记录上述硬件 callout。

#### 1.5.5 P类：性能与质量验收要求

P 类规定硬件性能规格、LLM 端到端服务、完整检测服务及数值精度指标。具体采样规程、报告格式和 DMA/DRAM 服务取证不列为 P 类要求。功耗上限、MX 速度和 cold TTFT 待定。

| AR ID | 规范要求 | 适用SKU | 要求状态 | 责任规格 | 正文解释／验收 |
|---|---|---|---|---|---|
| <a id="ar-hive-p001"></a>**AR-HIVE-P001** | INT8稠密GEMM峰值应为G0 1.024 TOPS、G0 Pro 2.048 TOPS，按2 operations/MAC计数，不含VPU。 | G0／G0 Pro | 已批准 | GEMM／SKU性能规格 | §2.3／§14.3 |
| <a id="ar-hive-p002"></a>**AR-HIVE-P002** | FP8稠密GEMM峰值应为G0 1.024 TFLOPS、G0 Pro 2.048 TFLOPS，不以稀疏或4bit口径定义1T/2T。 | G0／G0 Pro | 已批准 | GEMM／SKU性能规格 | §2.3／§14.3 |
| <a id="ar-hive-p003"></a>**AR-HIVE-P003** | 同格式INT4稠密GEMM峰值应为G0 2.048 TOPS、G0 Pro 4.096 TOPS。 | G0／G0 Pro | 已批准 | GEMM性能规格 | §2.3／§14.3 |
| <a id="ar-hive-p004"></a>**AR-HIVE-P004** | 同格式MXFP4 E2M1稠密GEMM峰值应为G0 2.048 TFLOPS、G0 Pro 4.096 TFLOPS，保留block scale语义。 | G0／G0 Pro | 已批准 | GEMM性能规格 | §2.3／§14.3 |
| <a id="ar-hive-p005"></a>**AR-HIVE-P005** | 合法INT/MX W4A8混配应按每core每有效计算拍512 MAC核验，不套用同格式4bit的1024 MAC峰值。 | G0／G0 Pro | 已批准 | GEMM混配性能规格 | §2.3、§4.1／§14.3 |
| <a id="ar-hive-p006"></a>**AR-HIVE-P006** | 每core VPU FP32应达到8 FMA/cycle稳态吞吐，500 MHz时峰值8 GFLOPS，按§12.7.1无访存反压条件验收。 | G0／G0 Pro | 已批准 | VPU性能规格 | §12.7.1／§12.7.4、§14.3 |
| <a id="ar-hive-p007"></a>**AR-HIVE-P007** | 每core VPU FP16应达到16 FMA/cycle稳态吞吐，500 MHz时峰值16 GFLOPS，不能与FP32替代模式峰值相加。 | G0／G0 Pro | 已批准 | VPU性能规格 | §12.7.1／§12.7.4、§14.3 |
| <a id="ar-hive-p008"></a>**AR-HIVE-P008** | Qwen3.5-0.8B、Qwen3.5-2B的MXFP8/MXFP4 D2048平均decode应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p009"></a>**AR-HIVE-P009** | DeepSeek-R1-Distill-Qwen-1.5B的MXFP8/MXFP4 D2048平均decode应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p010"></a>**AR-HIVE-P010** | Qwen3.5-0.8B、Qwen3.5-2B的MXFP8/MXFP4 P512 warm TTFT p95应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p011"></a>**AR-HIVE-P011** | DeepSeek-R1-Distill-Qwen-1.5B的MXFP8/MXFP4 P512 warm TTFT p95应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p012"></a>**AR-HIVE-P012** | Qwen3.5-0.8B、Qwen3.5-2B的MXFP8/MXFP4 D2048 TPOT p95应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p013"></a>**AR-HIVE-P013** | DeepSeek-R1-Distill-Qwen-1.5B的MXFP8/MXFP4 D2048 TPOT p95应分别设定并核验门槛。 | G0／G0 Pro | 模型/格式已批准；数值待确认 | LLM服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p014"></a>**AR-HIVE-P014** | YOLO11n完整检测持续吞吐应为G0≥30 fps、G0 Pro≥48 fps。 | G0／G0 Pro | 已批准 | 检测服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p015"></a>**AR-HIVE-P015** | YOLO11n完整检测p95时延应为G0≤50 ms、G0 Pro≤35 ms。 | G0／G0 Pro | 已批准 | 检测服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p016"></a>**AR-HIVE-P016** | YOLO11s完整检测持续吞吐应为G0≥10 fps、G0 Pro≥16 fps。 | G0／G0 Pro | 已批准 | 检测服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p017"></a>**AR-HIVE-P017** | YOLO11s完整检测p95时延应为G0≤150 ms、G0 Pro≤100 ms。 | G0／G0 Pro | 已批准 | 检测服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p018"></a>**AR-HIVE-P018** | G0 Pro同artifact/profile/输入的单请求decode实测速度应≥1.6×G0实测值，扩展效率按加速比/2报告。 | G0 Pro；G0为比较基准 | 已批准 | 多核服务验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p020"></a>**AR-HIVE-P020** | MXFP8/MXFP4部署应通过独立量化参考的逐算子误差与模型任务一致性验收，数值预算须先冻结。 | G0／G0 Pro | 已批准门槛；误差预算待冻结 | MX数值验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p021"></a>**AR-HIVE-P021** | 同源MX相对BF16的固定语料perplexity相对增幅应≤5%，不能单独替代完整生成质量。 | G0／G0 Pro | 已批准；语料/hash待冻结 | MX数值验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p022"></a>**AR-HIVE-P022** | 同源MX相对BF16的任务准确率绝对下降应≤1个百分点；放宽MX4门槛须重新评审。 | G0／G0 Pro | 已批准；任务集待冻结 | MX质量验收规格 | §12.8／§14.3 |
| <a id="ar-hive-p023"></a>**AR-HIVE-P023** | 检测部署相对同源浮点参考的COCO mAP50–95绝对下降应≤1个百分点。 | G0／G0 Pro | 已批准；recipe待冻结 | 检测质量验收规格 | §12.8／§14.3 |

*Note：P019、P024–P029 在 v0.17.0-draft 撤销，不复用；对应的驻留 GEMM 持续门槛、详细测量规程、文档格式与 DRAM 服务取证要求不再作为架构验收约束。v0.18.0-draft 更新 P008–P013 的模型/格式范围，数值待确认；P020 改为 MX 部署数值验收，其余有效指标保留。*

## 2. 设计目标与系统假设

### 2.1 设计目标

关联AR：[AR-HIVE-P020](#ar-hive-p020)、[AR-HIVE-P021](#ar-hive-p021)、[AR-HIVE-P022](#ar-hive-p022)、[AR-HIVE-P023](#ar-hive-p023)、[AR-HIVE-C006](#ar-hive-c006)。本节规定模型身份、artifact 语义和验收输入。

**目标：** 面向端侧推理，由 local CPU 协调 DMA 输入搬运、GEMM 计算、VPU 后处理和结果写回。固件/软件规划 local SRAM 的驻留、搬运和复用，local CPU 显式执行这些安排；scheduler 仅管理 GEMM/DMA 命令及其依赖，不分配 VPU scratch 或管理跨消费者的数据区所有权。

DMA 预取和 SRAM 双缓冲用于隐藏部分外部访问延迟，系统侧支持多核共享和异步任务提交。

显式管理避免透明缓存的命中/缺失、替换和回写路径，但不消除 bank 冲突、互联排队或 DRAM 延迟变化；这些仍由资源规划、流控和性能验证约束。

G0 容量与性能评估面向下表语言模型与 YOLO 检测模型。部署量化基线为 MXFP8、MXFP4，权重 W 与激活 A 全量化。至少分别评估：

- 语言模型 decode：M 很小，重点是持续权重带宽、搬运延迟和启动开销；
- 语言模型 prefill：M 较大，重点是权重复用、阵列利用率和 SRAM 容量；
- 视觉模型：覆盖 convolution/GEMM 主干、检测头和后处理，重点是多尺度 tensor、算子切换及端到端时延；
- attention/VPU：单独计入 KV、向量处理与中间 tensor 流量，不能用 GEMM 峰值替代。

**批准基线（MUST）：** 首轮 signoff 包含三个语言模型及两个 YOLO 检测模型，各自部署 MXFP8、MXFP4 的 W/A 全量化版本。服务与质量要求见第 12.8 节。

| 用途 | 模型 / 来源 | 工作负载 |
|---|---|---|
| 语言模型 | 官方 `Qwen/Qwen3.5-0.8B` | prefill、decode |
| 语言模型 | 官方 post-trained（非 Base）`Qwen/Qwen3.5-2B` | prefill、decode |
| 语言模型 | 官方 `deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B` | prefill、decode |
| 轻量检测 | Ultralytics 官方 COCO 预训练 `yolo11n.pt` | 640×640、batch1；主干、检测头、框解码与 NMS |
| 较高精度检测 | Ultralytics 官方 COCO 预训练 `yolo11s.pt` | 同输入与完整检测流程，不采用 seg/pose/obb 变体 |

模型 revision、量化配方、算子映射及输入条件须在验收前冻结。语言模型以同源 BF16 为质量参考，YOLO 以同源官方浮点权重为质量参考；MXFP8、MXFP4 分别验收。W/A 全量化不改变累加精度要求，也不预设 KV 或其他持久状态的存储格式。

### 2.2 批准配置与资源规划

**批准基线（MUST）：** 采用 B 平衡方案，G0 为 **2 cores × 500 MHz、1T SKU**；后续 G0 Pro 为 **4 cores × 500 MHz、2T SKU**。不以提高 GEMM 频率作为产品算力达标前提；时序裕量、工作电压和功耗仍需物理实现验证。

#### 2.2.1 G0 / G0 Pro SKU

| 硬件规格 | G0 | G0 Pro |
|---|---|---|
| 产品算力档位 | 1T | 2T |
| NPU core 数量 | 2 | 4 |
| GEMM 时钟 | 500 MHz | 500 MHz |
| 每 core GEMM 阵列 | T=16，256 个 PE，16-bit 物理输入 lane | 同 G0 |
| GEMM 结果 buffer | `cBufferDepth=64`，单命令 M≤48 | 同 G0 |
| local SRAM 可用数据容量 | 512 KiB/core；cluster 合计 1 MiB | 1 MiB/core；cluster 合计 4 MiB |
| SoC SRAM | 片上共享，全部 NPU core 与 main CPU 可访问；容量、位宽与端口待冻结 | 同 G0，覆盖四核 |
| SRAM banks | 8 × 256 bit/core，32-byte 低位交织 | 同 G0，增加 bank 深度 |
| 通用 DMA | 2 engines/core，256-bit data、40-bit address，最大 64 beats/burst，多读在途 | 同 G0 |
| 硬件 scheduler | 8 slots/core，单 GEMM 执行上下文 | 同 G0 |
| VPU 执行能力 | 256-bit/core，500 MHz；FP32 8 / FP16 16 GFLOPS/core | 同 G0 |
| VPU 本地数据口 | 1 × 256 bit/core，每拍一个读或写请求 | 同 G0 |
| 每 core system／peer 数据口 | AXI4，256-bit data、40-bit physical address | 同 G0 |
| 外部内存 | LPDDR4x，4 GiB，x32，4266 MT/s | LPDDR4x，8 GiB，x64，4266 MT/s |
| main CPU coherence | 非一致 AXI，无硬件 snoop | 同 G0 |
| CSR／IRQ | AXI4-Lite 32-bit；local/main CPU 分路 level IRQ，粘滞状态及 mask/clear | 同 G0 |
| 同步单元 | cluster 独立共享，READ/WRITE/FETCH_ADD | 同 G0 |
| NPU 电源／时钟域 | 整个 NPU 区域独立可关断电源域、独立可调频时钟域 | 同 G0，覆盖四核 |
| 电源状态 | RUN、IDLE、CLOCK_GATED、POWER_OFF；POWER_OFF 不保证内部状态保持 | 同 G0 |

#### 2.2.2 实现候选与冻结阶段门

“批准评估候选”不等于批准其服务保证；变更 MUST 配置须重新评审，不得静默降配。

| 项目 | 批准评估候选 | 唯一值冻结条件 |
|---|---|---|
| SRAM macro/端口 | 优先 1RW；G0 每 bank 2048 × 256 bit，Pro 4096 × 256 bit 的逻辑组织 | 接口/库冻结前确认 macro 拼接、byte-write/ECC 路径；物理冻结前通过 500 MHz 时序与竞争测试 |
| SoC SRAM 容量/端口 | 共享访问能力是MUST；容量分档、数据位宽、bank 组织与并发主设备数量待定 | 接口冻结前定义 window、仲裁与访问许可；性能冻结前按跨 core 共享流量、DRAM 替代收益和竞争确定容量与位宽；物理冻结前评估 macro 与面积 |
| DMA read outstanding | 每 engine 四个 burst 起步；满 burst 合计 8 KiB 返回容量预留 | 接口冻结前定义返回关联、credit 与反压；性能冻结前按延迟分布、排队与并发流量确定深度和物理缓冲组织 |
| DMA write outstanding | 每 engine 四个 burst 起步 | 接口冻结前确认响应归属；性能冻结前测量同 logical transfer 内重叠与 transfer 边界停顿，不等同于四份完整写数据缓冲 |
| DRAM 方向汇聚 | G0 256-bit @ 500 MHz；Pro 512-bit @ 500 MHz 或匹配 controller 的两路 256-bit | 接口冻结前选定拓扑与 system clock；性能冻结前计入 peer、控制和其他 SoC 主设备，验证有效 payload |
| NPU内部时钟组织 | local CPU/VPU/GEMM/DMA/scheduler/互联/SRAM/同步资源优先共域或同源派生；500 MHz为验收基准 | 区域独立调频是MUST，内部组织、支持频点/范围及正常切换协议仍待冻结；物理阶段验证时序，system/PHY频率独立确定 |
| NPU电源切换实现 | 整体受控断电，域外电源/时钟/reset及边界管理保持可用 | 独立可关断域是MUST；接口/电源规格冻结控制握手、隔离边界及上电初始化，电气时序与单元选择由后续物理阶段验证 |
| 同步状态 | 64 × 32-bit 状态 | 接口冻结前由 runtime 的同时活跃 barrier/event 数确认容量、访问与回收规则 |
| SRAM ECC | 不承诺基线 ECC；是否采用 SECDED 及粒度单独决策 | 库/可靠性冻结前明确校验位、部分写 RMW、恢复与面积；未选择实现前不得发布完整性保证 |

RTL 集成前须冻结接口；性能要求冻结须有版本化工作负载、服务指标和流量验证；物理冻结须有 TSMC 12nm macro/IP、时序与 PPA 依据。候选峰值不能替代尚未冻结的服务指标。

工作负载要求及规划已完成文档评审，实际验收仍需证据。早期 PPA 规划暂缓，面积/功耗预算及实现取舍留后续迭代，不列为已批准 AR。

#### 2.2.3 非基线能力

- NPU LLC、HBM/3D DRAM、跨 core K 维 partial-sum 归约、多租户/抢占、专用 event link 不属于基础交付能力；不得形成基线依赖。已批准的 SoC SRAM 是显式管理的共享 scratchpad，不等于引入 NPU LLC 或透明缓存层。
- 16-bank/1R1W、扩大 GEMM buffer、cluster 共享 VPU、硬件 main CPU coherence、APB/MSI、SWAP/CAS、专用retention、独立调压/DVFS及逐core独立电源/调频域均需单独变更决策，不是可任意切换的已批准配置；不包含已批准的区域级独立调频与受控电源关断。
- 2 GiB G0、16 GiB Pro 属于非基线装配选项；本方案按 G0 4 GiB、Pro 8 GiB 配置。Pro 扩至 16 GiB 须另行评审；容量增加不提高 x64 数据位宽或持续带宽。

### 2.3 GEMM 各精度算力与 SKU 口径

关联AR：[AR-HIVE-P001](#ar-hive-p001)、[AR-HIVE-P002](#ar-hive-p002)、[AR-HIVE-P003](#ar-hive-p003)、[AR-HIVE-P004](#ar-hive-p004)、[AR-HIVE-P005](#ar-hive-p005)。峰值由[C003](#ar-hive-c003)、[C004](#ar-hive-c004)、[C005](#ar-hive-c005)、[C006](#ar-hive-c006)配置展开，不能代替持续任务验收。

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

G0/G0 Pro 的 1T/2T 按 INT8/FP8 稠密 GEMM 峰值定义，不含 INT4、稀疏或 VPU 算力。峰值要求各 core 同时处于有效计算阶段；装载、排空、数据搬运、bank 冲突和任务不均衡会降低完整任务吞吐。

4bit 格式的量化精度、scale/布局开销和有效拍占比须单独验证，峰值翻倍不代表模型速度翻倍。物理实现条件和面积、功耗、时序结果见第 12.6 节。

## 3. 总体架构与系统边界

### 3.1 Cluster 级架构

关联AR：[AR-HIVE-C001](#ar-hive-c001)、[AR-HIVE-C003](#ar-hive-c003)、[AR-HIVE-C040](#ar-hive-c040)。G0/Pro的主CPU均在SoC内，多core扩展须分别集成验收。

```text
main CPU / software scheduler --- task submission / completion / CSR / IRQ
          |
          v
+-----------------------------------------------------------------------+
|                          System interconnect                          |
|         independent core ingress + peer routing + arbitration         |
+------+-------------+-------------+-------------+-------------+--------+
       |             |             |             |             |
  +----v-----+  +----v-----+  +----v-----+  +----v-----+  +----v-----+
  |NPU core0 |  |NPU core1 |  |Sync unit |  | SoC SRAM |  |DRAM ctrl |
  |Local SRAM|  |Local SRAM|  |(atomics) |  | (shared) |  |  / PHY   |
  +----------+  +----------+  +----------+  +----------+  +----+-----+
                                                               |
                                                            LPDDR4x
```

图示为 G0 两核配置；后续 G0 Pro 四核 2T SKU 在相同系统互联增加 core2/core3 的独立入口，并重新验证聚合带宽、同步和任务划分。SoC SRAM 由全部 core 与 main CPU 共享，不随核数复制。

**目标：** core 可独立执行，也可通过 peer SRAM window、SoC SRAM、共享 DRAM 数据和事件协同。系统互联直接路由到 DRAM controller、SoC SRAM 或 peer SRAM，不经过 NPU 内建透明缓存。原子同步由独立同步单元处理；片内主控 CPU 的非一致 cache 维护由固件/软件按 SoC 平台契约承担，协议保持暂缓。

### 3.2 单 core 控制与数据路径

关联AR：[AR-HIVE-C002](#ar-hive-c002)、[AR-HIVE-C008](#ar-hive-c008)、[AR-HIVE-C015](#ar-hive-c015)、[AR-HIVE-C019](#ar-hive-c019)。框图表达物理归属、控制责任与 IRQ 分路；传输和完成协议待冻结。

```text
MAIN CPU
Runtime / software scheduler
              | task submission / task completion
              v
+------------------------------------------------------------------+
| NPU CORE CONTROL                                                 |
|                                                                  |
| local CPU: task execution and local control                       |
|     |                                                            |
|     +--> scheduler --> GEMM adapter --> GEMM                      |
|     |              +-> DMA adapter  --> DMA                       |
|     |                                                            |
|     +--> VPU direct control / instructions --> VPU (CPU DSA)      |
|                                                                  |
| GEMM/DMA --> scheduler sync resources --> local CPU               |
| VPU completion / result visibility ------> local CPU             |
| local CPU --> task completion --> main CPU software scheduler    |
+------------------------------------------------------------------+

NPU CORE DATA
GEMM local read/write ---+
DMA local read/write ----+--> Local interconnect --> Local SRAM banks
VPU local load/store ----+
local CPU / debug -------+

CORE EXTERNAL DATA PORTS
DMA system read/write ------> AXI bridge ---> System interconnect
Remote access to local SRAM -> Peer AXI bridge -> Local interconnect
```

主 CPU 通过软件 scheduler 向 local CPU 提交任务并回收完成，不直接管理 scheduler 的 slot、引擎命令或同步资源。local CPU 使用 scheduler 派发 GEMM/DMA，直接管理 VPU，并协调跨引擎次序；完成观察与结果可见性协议待冻结。

图中的边界表示 NPU core 的物理范围，local CPU/VPU 的仓库外实现来源不改变其归属。local SRAM 是数据端点，不转发 DRAM 请求；DMA 在两侧地址空间之间读写。

软件 scheduler 的队列布局不属于硬件架构定义，提交和完成的传输/通知细节由后续接口规格明确。硬件 ring、command fetcher 和 completion writer 均非必需。core 任务通知与引擎同步完成是两个层次，main CPU IRQ 不提供直接管理 scheduler 的通路。

### 3.3 组件责任与外部责任

| 组件/外部对象 | 责任 | 不承担的责任 |
|---|---|---|
| GEMM | 权重驻留矩阵计算、私有 partial sum 和输出处理，完成经同步接口更新 scheduler 指定资源 | 不直接访问 DRAM；不管理系统任务队列 |
| DMA | DRAM/local/peer 数据搬运与布局变换 | 不替代 scheduler 的跨引擎依赖管理 |
| scheduler | 接受 local CPU 的 GEMM/DMA 命令，管理依赖、派发、同步资源及引擎完成 | 不管理 VPU，不承担主 CPU 与 NPU 之间的任务队列 |
| local SRAM + local interconnect | 本地数据存放、路由、冲突仲裁与返回 | 不自动推断 tensor 生命周期 |
| system interconnect | DRAM/peer 访问与共享访问仲裁 | 不自动保证 main CPU 私有 cache 一致性 |
| synchronization unit | 共享同步状态和原子操作序列化 | 不存储 tensor，不代替数据写完成和 fence |
| local CPU | 执行软件 scheduler 提交的任务，控制 scheduler 的 GEMM/DMA 命令和同步，直接管理 VPU，协调阶段完成 | 不替代系统软件任务编排；微架构实现来自仓库之外 |
| VPU | core 内 CPU 挂接的向量 DSA，执行 local CPU 发出的向量指令/操作并向 local CPU 提供完成观察 | 不接受 scheduler 派发，不管理系统任务队列；实现来自仓库之外 |
| main CPU / 软件 scheduler | 系统任务编排、软件队列、core 选择、缓冲区生命周期、cache 维护及任务提交/回收 | 不直接管理 scheduler，不依赖未定义的隐式 fence |
| DRAM controller / SoC | 内存时序、物理带宽、地址路由及系统保护边界 | 不保证所有负载都达到峰值带宽 |

## 4. 计算组织与端到端数据流

### 4.1 GEMM 计算组织

关联AR：[AR-HIVE-C005](#ar-hive-c005)、[AR-HIVE-C006](#ar-hive-c006)、[AR-HIVE-P005](#ar-hive-p005)。格式合法域由GEMM责任规格细化，不能从物理lane数量推断任意格式组合。

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

关联AR：[AR-HIVE-C022](#ar-hive-c022)、[AR-HIVE-C035](#ar-hive-c035)、[AR-HIVE-C036](#ar-hive-c036)、[AR-HIVE-S022](#ar-hive-s022)。算术与转换/归约能力归硬件要求，特殊函数缺少专用硬件时由固件/向量软件补齐，不强制把全部功能放入VPU硬件。

**目标：** core 内 VPU 支持 FP32 和 FP16 算术，承担 GEMM 之外的向量处理，而不是再建一个同规模矩阵阵列：

- FP16 用于向量输入/输出、逐元素加乘、激活和带宽敏感的中间 tensor；
- FP32 用于归一化、softmax 等需要较高动态范围或数值稳定性的归约/累加；要求支持 FP16 输入转换后以 FP32 累加及结果转换，原生混合精度指令与具体吞吐由外部接口规格确认；
- 典型算子包括 RMSNorm/LayerNorm、SiLU/GELU、softmax、RoPE、residual add、格式转换及量化辅助；GEMM 已融合的 bias/requant 等工作不重复计入 VPU；
- QKᵀ 和 attention probability×V 优先按矩阵运算规划，但当前 GEMM 默认输入不支持 FP16/FP32，必须验证量化后的格式与精度；若这些矩阵乘落到 FP16 VPU，单独增加 attention 算力预算，不能混入小型后处理假设。

基础需求包括 add/mul/FMA、比较、sum/max 归约、掩码/tail 和 FP16↔FP32 转换；exp、reciprocal、rsqrt 及可选 GELU 所需特殊函数可由硬件或向量软件实现，但应定义误差、启动间隔和端到端算子吞吐。FMA 的 GFLOPS 不代表每秒可完成同等数量的 exp/归约。

VPU 的 ISA、流水级及内部模块由所属仓库定义，执行与完成由 local CPU 直接管理，不经过 scheduler 派发。每 core 独立 256-bit、FP32 8 / FP16 16 GFLOPS 的批准指标、接口和验收要求见第 12.7 节。cluster 共享 VPU 为非基线变更，须单独批准，并验证聚合吞吐、各 core 数据访问及公平服务。

产品配比、扩展档位和工作负载推导见[性能分析](performance_analysis.md#2-vpu-配比选型与工作负载分析)。

## 5. 存储子系统

### 5.1 存储层次

关联AR：[AR-HIVE-C011](#ar-hive-c011)、[AR-HIVE-C041](#ar-hive-c041)、[AR-HIVE-S003](#ar-hive-s003)。scratchpad与私有buffer、SoC SRAM、DRAM容量分别核算，不能重复计入共享可用容量。

| 层次 | 内容 | 管理方式 |
|---|---|---|
| GEMM 私有 buffer | 当前 pass、权重、partial sum、输出暂存 | 引擎内部管理，不能作为共享 tensor 内存 |
| local SRAM | activation、weight tile、output、VPU scratch、少量控制数据 | core 私有；固件/软件规划分配并显式装载与必要写回，local CPU 协调复用与跨引擎生命周期 |
| SoC SRAM | 跨 core 复用的权重/activation 分块、core 与 main CPU 之间交接的数据、共享中间结果 | 多 core 与 main CPU 共享；经 system interconnect 显式搬运或访问，所有权由软件分配，不自动补给 |
| DRAM | 模型权重、完整tensor、KV和软件 scheduler 任务队列/记录 | 系统容量层，通过显式搬运访问；软件队列不由硬件 scheduler 管理 |

local SRAM 是 scratchpad，不用“L1 cache 命中率”描述其性能。数据只有通过明确的生产者写入或 DMA 搬运才进入 SRAM，没有自动填充、替换或脏数据回写；完整模型权重与持久状态按 DRAM 容量核算，local SRAM 只保留实际执行所需的分块。SoC SRAM 遵循同一显式管理规则，是 local SRAM 与 DRAM 之间的共享层，不是 local SRAM 的扩展容量，也不是 DRAM 的缓存。

### 5.2 Local SRAM banks

关联AR：[AR-HIVE-C009](#ar-hive-c009)、[AR-HIVE-C010](#ar-hive-c010)。下述逻辑组织由容量/bank配置展开，1RW仍是候选而不是AR保证的macro形式。

**批准基线（MUST）：** 两 SKU 均为 8 banks、每 bank 256 bit、32-byte 低位交织。G0 为 512 KiB/core，逻辑上每 bank 64 KiB、2048 个 256-bit 字；后续 G0 Pro 为 1 MiB/core，每 bank 128 KiB、4096 个字。容量增加不提高 bank 数或每拍服务槽数量。

在“每 bank 每拍完成一个 32-byte 访问”的条件下，bank 总峰值为 256 B/cycle。8-bank 映射为 `bank=addr[7:5]`；物理 macro 拼接、byte-write 和 ECC 路径须在库/接口冻结时确认。

**候选：** 优先评估 1RW macro。不同 bank 可并行，同 bank 读写竞争同一服务槽；不能把 256 B/cycle 承诺为读写各自独立的同时峰值。16-bank 或 1R1W 不属于基线，只有竞争/PPA 证据支持并经变更批准后才能引入。

### 5.3 资源分配与小访问

- 输入、输出、下一任务预取及 VPU scratch 使用显式区域；
- 按各模块真实布局预留 N 补齐、scale、bias/requant 参数、对齐和 guard 空间；
- 推荐按 bank 错开活跃 A/B/C 区域；仅 byte-address 对齐不足以避免冲突；
- 4-byte scale 和 1-byte scale 写可能仍占一个 bank 请求拍；byte strobe 不等于减少仲裁占用；
- 是否为 scale/控制数据设置独立窄存储或请求合并缓冲，是待测优化，而非初版承诺。

容量和 tiling 算例集中在第 12.2 节。

### 5.4 显式驻留与 DRAM 访问

关联AR：[AR-HIVE-S003](#ar-hive-s003)、[AR-HIVE-H001](#ar-hive-h001)、[AR-HIVE-H002](#ar-hive-h002)。外部存储容量、位宽和速率按 H001/H002 配置；理论峰值不等于持续 payload。

**目标：** 固件/软件显式组织 tensor 分块、SRAM 分配与驻留、数据复用和搬运依赖；scheduler 执行已提交的 GEMM/DMA 依赖，local CPU 确认跨引擎阶段完成与结果可见后协调缓冲区复用，DMA 按明确地址搬运。已驻留 SRAM 的数据复用无需重复访问 DRAM，但容量不足时必须显式写回或重新装载。

- cluster 多主经 40-bit system interconnect 直接访问 DRAM controller；
- 权重按连续分块预取到 SRAM，双缓冲区域与当前计算区不重叠；
- 描述符可按任务需要显式取入本地命令 buffer，容量与预取策略待确认；
- KV 按实际消费块从 DRAM 取入 SRAM，更新后通过明确写回和完成依赖发布；
- 跨 core 复用的数据可按第 5.5 节放入 SoC SRAM，减少重复 DRAM 读取；核间直接交换仍可使用 peer SRAM window；
- DRAM controller 的入口/后端并行度、排队和持续有效带宽必须匹配第 12 章预算。

该方式要求固件/软件补齐显式装载、必要写回与分批搬运，不消除存储调度复杂度，也不定义完整软件设计。批准内存配置为 G0 4 GiB LPDDR4x x32、后续 G0 Pro 8 GiB LPDDR4x x64，速率均为 4266 MT/s；controller 频率、有效 payload、调度效率和 ECC 仍待确认。

### 5.5 SoC 共享 SRAM

关联AR：[AR-HIVE-C040](#ar-hive-c040)、[AR-HIVE-C041](#ar-hive-c041)、[AR-HIVE-S003](#ar-hive-s003)。共享访问能力属于批准基线，容量分档与访问协议仍待冻结。

**批准基线（MUST）：** SoC 提供片上共享 SRAM，全部 NPU core 与 main CPU 均可访问，经 system interconnect 按受范围约束的 window 寻址。该存储为显式管理的 scratchpad，不提供自动填充、替换或脏数据回写，也不作为 DRAM 的透明缓存层。

| 项目 | 规定 |
|---|---|
| 访问者 | 各 core 的 DMA（system 侧）、main CPU、local CPU 的授权访问 |
| 不直接访问 | GEMM 与 VPU 只访问 local SRAM，跨层数据交换由 DMA 搬运或 CPU 访问完成 |
| 典型用途 | 跨 core 复用的权重/activation 分块、core 与 main CPU 之间交接的数据、共享中间结果 |
| 容量核算 | 独立核算，不计入每 core local SRAM 可用容量，也不计入 DRAM 容量预算 |
| 原子操作 | 不默认支持；共享同步状态仍由第 8.4 节的独立同步单元承担 |
| 电源/时钟域 | 位于 NPU 可关断电源域之外，NPU 断电不要求其内容失效；不得据此声明 NPU 内部状态保持 |

**候选：** 容量分档、数据位宽、bank 组织和并发主设备数量待定，冻结条件见第 2.2.2 节。共享层不替代 local SRAM 带宽：命中 SoC SRAM 的访问仍经 system interconnect，延迟与竞争高于 core 内 bank 访问，必须按实际共享流量验证收益，不能按容量相加宣称可用 scratchpad 变大。

跨 core 消费 SoC SRAM 数据前，固件/软件须按第 8.2 节确认生产阶段完成与结果可见；共享区域的分配、所有权交接与复用由软件组织，硬件不提供自动一致性。main CPU 若以可缓存属性映射该区域，须按第 8.3 节执行 cache 维护。

## 6. 互联与流控

### 6.1 Local interconnect

关联AR：[AR-HIVE-C025](#ar-hive-c025)、[AR-HIVE-C028](#ar-hive-c028)。bank并行与基本正常前进性分别验证；本架构不定义QoS数值，不设最小服务率、配额或最大等待上界。

**目标：** 按 bank 路由、每 bank 仲裁；对不同 banks 的无冲突请求允许同拍推进。

架构请求含 byte address、read/write、data、byte enable 和 source/return routing 信息；内部序号和具体 Bundle 留给接口规格。GEMM 小数据口、CPU 窄口和 256-bit 主数据口通过 adapter 访问。

仲裁实现由互联规格选择；有限背压解除后，各主设备应正常推进，不因计算优先而阻断DMA供数。架构不冻结优先级、配额或等待上界。

### 6.2 DMA 两侧地址路由

DMA 的读请求和写请求分别译码：

| 操作 | 读侧 | 写侧 |
|---|---|---|
| DRAM → local SRAM | system AXI | local interconnect |
| local SRAM → DRAM | local interconnect | system AXI |
| peer SRAM → local SRAM | peer system path | local interconnect |
| local SRAM → peer SRAM | local interconnect | peer system path |
| SoC SRAM → local SRAM | system AXI | local interconnect |
| local SRAM → SoC SRAM | local interconnect | system AXI |
| local SRAM → local SRAM | local interconnect | local interconnect |

一个 burst 内目的端点固定；跨区域和 AXI 4 KiB 边界的拆分责任由桥接器与 DMA 接口规格明确。返回数据/写响应须回到原 engine，不允许丢失源归属。

### 6.3 在途事务与反压

关联AR：[AR-HIVE-C012](#ar-hive-c012)、[AR-HIVE-C013](#ar-hive-c013)、[AR-HIVE-C026](#ar-hive-c026)。多读在途是批准能力，返回归属/credit为待冻结协议，四burst深度仍是候选。

**现状：** 每 DMA engine 至多一个读请求在途；数据接口无读 transaction ID。不能只把 AXI adapter 的 outstanding 深度调大就宣称引擎可并行发读。

**目标：**

- DMA 支持多读在途，并在接口或内部顺序域中定义返回关联；
- GEMM 的本地顺序返回要求由桥接器保持；
- 按预留返回数据容量发 credit，响应缓冲不足时停止接受新请求；
- 合并多个 engine 到 AXI 时保留 source ID，跨目的端点响应允许的顺序必须明确；
- 写请求接受、写响应返回、数据对其他主设备可见是不同事件。

**批准基线（MUST）：** 每 core 两个 256-bit DMA engines、最大 64 beats/burst，并支持多读在途。**候选：** read/write outstanding 均从四个 burst/engine 起步评估；四个满宽读 burst 合计预留 8 KiB/engine 返回容量，但物理缓冲位置与实际深度须由 credit、延迟和流量验证确定。不能只扩大 adapter 参数或现有单 burst FIFO 就宣称具备引擎级并发。

当前写在途可通过配置增加，但重叠范围受 logical transfer 和响应路由限制；读在途需新增配套协议与实现。在途深度推导见 12.4，不把候选字段当作已实现能力。

### 6.4 System interconnect

关联AR：[AR-HIVE-C017](#ar-hive-c017)、[AR-HIVE-C027](#ar-hive-c027)、[AR-HIVE-C040](#ar-hive-c040)。core接口宽度不等于DRAM汇聚拓扑或持续带宽保证。

**批准基线（MUST）：** 每 core 独立 256-bit AXI4 ingress，40-bit 地址。DRAM、SoC SRAM、peer SRAM 和独立同步单元路由为目标；具体 AXI profile 与顺序域仍待接口冻结。

256-bit 接口在 500 MHz 下单向数据峰值为 16 GB/s；G0 两核/G0 Pro 四核独立入口峰值总和为 32/64 GB/s，不代表 DRAM 持续带宽。system clock 尚未冻结，不与 GEMM 频率自动绑定。

**DRAM 方向汇聚候选：** G0 为 `256-bit @ 500 MHz`，后续 G0 Pro 为 `512-bit @ 500 MHz` 或匹配 controller 的两路 256-bit，分别具有 16/32 GB/s 理论单向接口峰值。它们不保证第 12.4 节的模型有效 payload；还须计入权重/scale、KV、其他流量、仲裁损失和 LPDDR4x 实际服务能力。若已批准工作负载服务目标不通过，须重新评审主干或内存配置，不能以降低验收目标代替闭环。

上述候选只描述 DRAM 汇聚方向，不限制其他路由的拓扑。peer 与 SoC SRAM 流量占用 system path，并分别竞争目标 local banks 或共享 SRAM 端口，须单独验证其与 DRAM/本地访问的竞争，不把入口峰值总和指定为主干保证值。

### 6.5 前进性与死锁约束

关联AR：[AR-HIVE-C028](#ar-hive-c028)。本节规定正常运行的基本前进性和响应排空约束，不规定 QoS 有界服务。

**目标：**

- 响应排空不依赖提交新命令或获取同一被占满队列的 credit；
- 发起 burst 前可用 credit 与返回 buffer 容量相符；
- 正常运行中，计算暂停不阻塞已接受访问的响应；
- peer copy 不同时长期锁住两端 SRAM 资源。

系统级验证须证明有限背压解除后各主设备可继续推进，且响应排空不因 credit 耗尽而死锁；采用 AXI 协议本身不足以证明这些条件。

## 7. 执行与编程模型

### 7.1 命令入口与上下文

关联AR：[AR-HIVE-C002](#ar-hive-c002)、[AR-HIVE-C014](#ar-hive-c014)、[AR-HIVE-C015](#ar-hive-c015)。任务、slot 和执行上下文属于不同层次。

本文规定资源规模、控制归属和功能边界。高层调度软件组织及底层反压、命令握手、资源分配与释放流程由实现细化并保证正确性，不另列软件 scheduler 设计 AR。

**批准控制边界：** 主 CPU 与 NPU 之间采用软件 scheduler 管理任务；core 内由 local CPU 负责把任务展开为 GEMM/DMA 命令并提交硬件 scheduler。主 CPU 不作为硬件 scheduler 的直接命令入口，也不管理其 slot 或同步资源。

- 软件任务与引擎命令是两级对象，任务接收/回收不等于某一 GEMM/DMA 命令完成；
- local CPU 经 custom request 使用 scheduler，维护任务内执行次序和阶段依赖；
- VPU 由 local CPU 直接控制，不占 scheduler 的派发上下文；
- 软件队列可驻留 DRAM，具体传输与通知协议待确认，不要求硬件 ring/fetcher；
- 首版按单受信任执行上下文规划，不声明多进程隔离或抢占。

**批准基线（MUST）：** scheduler 规划 8 slots/core、单 GEMM 执行上下文，允许无依赖的计算与搬运并行；不要求同拍多派发。

**现状：** scheduler 仅有请求回显和译码骨架；`slotDepth=8` 不代表已有八命令并发。GEMM 的四命令 lane 可接受寄存器批次，但仍只有一个执行上下文；通用 DMA 双 engine 不等于 scheduler 同拍双派发。

### 7.2 Scheduler 与 VPU

关联AR：[AR-HIVE-C015](#ar-hive-c015)、[AR-HIVE-C016](#ar-hive-c016)、[AR-HIVE-S006](#ar-hive-s006)。scheduler 不管理 VPU 或跨消费者所有权；跨引擎消费与复用由固件/软件确认阶段完成和结果可见性，具体协议暂缓。

**目标 scheduler：** 管理 slot、依赖、adapter 派发及同步资源，允许互不依赖的计算与搬运跨周期并行。GEMM/DMA 完成后通过同步接口更新指定资源，再解除对应依赖；该完成模型不以当前模块是否携带命令 ID 为前提。

VPU 作为 local CPU 挂接的 DSA，直接接收其指令/控制。scheduler 不接受 VPU launch，也不管理 VPU 命令或完成关联。local CPU 须先观察 GEMM/DMA 所需同步完成，再启动依赖其结果的 VPU 操作；VPU 结果对后继消费者可见后，才能提交后继 GEMM/DMA 命令。同步观察与直接 VPU 接口细项待冻结，错误处理范围见第 11.1 节。

模块 tag 用于软件主动插入的 tracing/profiling 标记；trace 落到内存后由软件重建运行时 timeline。tracing tag、scheduler 同步资源和总线 transaction ID 分开管理，不以 tracing tag 作为完成或依赖匹配依据。

### 7.3 缓冲区所有权

关联AR：[AR-HIVE-S006](#ar-hive-s006)。scheduler 仅落实 GEMM/DMA 命令间的已提交依赖，不管理 VPU scratch 或跨 CPU/VPU/peer 消费者的所有权；DMA 内部 hazard 检查不能替代跨引擎保护。

固件/软件须在跨引擎消费或复用数据区前确认所需阶段完成与结果可见性，不得读取未就绪输入或覆盖仍在使用的数据。缓冲区状态机、分配/释放流程和软件组织形式由实现定义；跨接口完成与可见性契约暂缓。

### 7.4 完成、事件与依赖

区分命令被接受、被派发、引擎执行完成与结果对消费者可见。凡同步完成允许消费者读取结果，必须先满足该消费者所需的写响应与可见性条件，再经 GEMM/DMA 同步接口更新 scheduler 内指定资源；不能只按命令接受或 busy 下降发布完成。

scheduler 同步资源采用 sync event、sync timeline 等机制，具体类型、数量及更新/等待语义待接口规格确认；内部资源分配与回收机制由实现细化，不另列架构评审项。同步 timeline 表达执行依赖，不是软件根据 trace 重建的 profiling timeline；scheduler 内同步资源与第 8.4 节 cluster 共享原子同步空间也不能默认混为一体。

`WAIT_EVENT`、`SIGNAL_EVENT`、`POLL_EQ` 和 atomic RMW 为目标能力，具体指令编码待确认。event 只解除已定义的依赖，不能隐式完成 CPU cache 维护。软件任务完成由 local CPU 与主 CPU 上的软件 scheduler 协作管理，发布/观察协议见第 9.2 节；错误完成处理范围见第 11.1 节。

完整端到端任务示例见第 14.1 节。

## 8. 地址、顺序与一致性

### 8.1 地址视图

关联AR：[AR-HIVE-C008](#ar-hive-c008)、[AR-HIVE-C031](#ar-hive-c031)、[AR-HIVE-C040](#ar-hive-c040)。位宽、窗口合法性与CPU映射属性分开核验，数值基址/权限仍待冻结。

**目标：** 系统采用 40-bit 物理地址；GEMM 使用 32-bit local SRAM offset。通用 DMA 当前已支持 40-bit address，无需为这一目标重新扩大其地址宽度。

| 区域 | 访问者/用途 | 属性 |
|---|---|---|
| local CSR | main CPU 访问 core 配置/状态；local CPU 控制 scheduler | device、非缓存；main CPU 不通过该窗口管理引擎调度 |
| local SRAM window | GEMM/DMA/VPU/CPU | scratchpad；CPU 映射不得产生未维护的私有 cache 副本 |
| peer SRAM window[n] | DMA/CPU/外部主设备 | remote scratchpad，遵循相同映射约束 |
| SoC SRAM window | 各 core DMA/main CPU/local CPU | 共享 scratchpad；默认非缓存，可缓存映射须按 8.3 节维护 |
| synchronization window | scheduler/CPU 等授权主设备 | device、非缓存；原子与普通访问统一序列化 |
| shared DRAM | descriptors、completion、权重、KV、tensor | NPU 直接系统访问；main CPU cache 属性与维护由平台契约决定 |

数值基址、window size 和访问许可待确认。bridge 对 GEMM offset 做窗口译码和范围检查，不截断 40-bit 地址伪装成合法本地访问。编译期字段只表达真实可配置项，固定内部数量不包装为 Config 选项。

### 8.2 顺序与可见性

**目标：** 明确以下 producer/consumer 顺序：

1. 主控 CPU 上的软件 scheduler 准备输入与任务参数 → 执行所需 cache 维护和屏障 → 提交给 core 的 local CPU；
2. local CPU 提交 DMA 命令 → DMA 写 SRAM → 所需响应完成且本地可见 → 更新 scheduler 同步资源，解除对应 GEMM/DMA 依赖或供 local CPU 观察；
3. GEMM 写结果并发布同步完成 → local CPU 确认 VPU 输入可读 → 直接启动 VPU；VPU 完成且结果可见后，local CPU 才允许后继 GEMM/DMA 消费；
4. local CPU 确认任务全部阶段满足完成条件 → 向软件 scheduler 发布任务完成 → 主控 CPU 执行所需屏障与 cache 维护并读取结果。

AXI 不同 ID、不同接口以及数据写与同步通知间不默认有全局顺序；fence、drain 和响应含义需在系统接口规格中定义。

### 8.3 main CPU cache coherence

关联AR：[AR-HIVE-C021](#ar-hive-c021)、[AR-HIVE-S007](#ar-hive-s007)。本节规定非一致访问及固件/软件维护责任，不定义 runtime、驱动或 API 设计。

**批准基线（MUST）：** 非一致 AXI，不依赖硬件 snoop。对可缓存共享 DRAM 数据，固件/软件须在所有权交接前后执行所需 cache 维护及提交/完成屏障；引擎同步事件不能代替 cache 维护。平台映射属性、维护范围、所有权交接与屏障顺序的具体契约暂缓；硬件 coherence 不属于基础交付能力。

NPU 数据路径不设置透明缓存，不等于 main CPU/local CPU 没有 cache；任何可缓存的共享数据都必须遵循明确的所有权与 cache 维护规则。local/peer/SoC SRAM window 和同步区默认采用非缓存映射，避免同一物理区域存在未受控的脏副本。

### 8.4 原子与同步空间

关联AR：[AR-HIVE-C020](#ar-hive-c020)。操作集合是批准要求，状态容量、编码与线性化协议仍待冻结，不与scheduler内部同步资源默认合并。

**批准基线（MUST）：** cluster 设置独立同步单元，提供 READ/WRITE/FETCH_ADD。**候选：** 64 × 32-bit 状态，具体容量、操作编码和访问协议在 runtime/接口冻结时确认；SWAP/CAS 不属于基线。多核 barrier 的 atomic RMW 与 `POLL_EQ` 访问同一同步窗口，普通访问与原子操作共享序列化点。

同步单元不承载 tensor 数据，也不让数据写自动可见；发出同步更新前仍须满足第 8.2 节的数据排空规则。local/peer/SoC SRAM window 不默认支持原子；同步状态不能靠多个普通读写模拟不可分割 RMW。

## 9. 系统集成与通信接口

### 9.1 对外接口分类

关联AR：[AR-HIVE-C017](#ar-hive-c017)、[AR-HIVE-C018](#ar-hive-c018)、[AR-HIVE-C019](#ar-hive-c019)、[AR-HIVE-C023](#ar-hive-c023)。VPU数据/控制属于core内部集成接口，不因实现来自仓库外而改为core外部端口。

方向以 NPU core 为参照，端口名为架构占位，不表示现有 RTL 已具备。

| 接口 | 方向 | 目标形态 | 用途与状态 |
|---|---|---|---|
| system data | master | AXI4，256-bit data、40-bit address | DMA/系统访问，含 DRAM、SoC SRAM 与同步单元；目标 |
| peer SRAM access | slave | AXI4，256-bit data、40-bit address | 远端访问本地 SRAM；目标 |
| CSR/control | slave | AXI4-Lite 32-bit | 批准基线；配置、doorbell、PMU，寄存器/事务待确认；APB 非基线 |
| interrupt | output | local/main CPU 分路 level | 批准基线，路由细项待确认；MSI 非基线 |
| core event link | 双向 | event ID + delivery/ack | 非基线增强，未实现 |
| debug access | 双向 | 系统 debug bridge 或 JTAG 集成 | 暂停、状态、trace；待确认 |

local CPU/VPU 来自仓库之外，但以下均是 core 内部集成接口，不列为 NPU core 对外端口：

| 内部接口 | 连接 | 形态与状态 |
|---|---|---|
| 引擎命令与同步观察 | local CPU↔scheduler | custom request/response、GEMM/DMA同步资源；完整事务语义待实现 |
| VPU直接控制 | local CPU↔VPU | 指令/控制、背压、完成观察；不经过scheduler，细项待仓库外实现接口确认 |
| VPU本地数据 | VPU↔local interconnect | 1×256-bit local request，读写合计每拍最多一请求；返回顺序、在途与反压见12.7 |

AXI ID、最大 burst、在途数、CDC 与顺序域在接口规格中逐项冻结，采用 AXI 不代表支持其完整功能集。模块所需的基本 AXI 错误响应编码由接口实现规格定义；详细故障处理范围见第 11.1 节。

### 9.2 主控 CPU 任务提交与完成

关联AR：[AR-HIVE-C002](#ar-hive-c002)、[AR-HIVE-C015](#ar-hive-c015)、[AR-HIVE-C019](#ar-hive-c019)。控制归属与IRQ形式已确定，软件 scheduler 不另列设计AR；固定entry、通知和完成可见性协议仍待专题确认。

**批准边界：** G0 主 CPU 与 NPU 之间的任务管理采用软件 scheduler。主 CPU 运行系统软件任务管理，向选定 core 的 local CPU 提交任务并回收任务完成；不直接管理 core 内 scheduler。任务队列及记录由软件管理，不要求硬件 SQ/CQ ring。

| 层次 | 责任 | 不承担的责任 |
|---|---|---|
| 主CPU软件 scheduler | 任务队列、core选择、提交/回收、共享缓冲区及CPU cache维护 | 不直接派发GEMM/DMA/VPU，不分配scheduler slot或同步资源 |
| core内local CPU | 接收并执行任务，提交GEMM/DMA命令，直接控制VPU，观察各阶段完成并发布任务完成 | 不把VPU交给scheduler派发，不按单个引擎busy下降推断完整任务完成 |
| core内scheduler | 管理local CPU提交的GEMM/DMA命令、依赖和同步资源，汇总引擎完成 | 不取软件任务队列，不管理VPU或主CPU任务队列 |

软件 scheduler 的内部队列布局与任务调度流程不属于硬件架构 ABI；底层命令握手和资源分配/释放由实现细化并保证正确性。

主 CPU↔local CPU 的任务通知与完成可见性、local CPU↔scheduler 的命令接受及同步更新/等待须形成互操作契约。这些协议暂缓，SQ/CQ 编码、固定 entry 大小、fence/drain 和原子协议均未冻结。错误处理范围见第 11.1 节。硬件 ring/fetcher/completion writer 为非基线变更，须单独评审。

完成中断采用粘滞状态 + mask/clear，local CPU 与 main CPU 分别路由。level IRQ 为批准基线；MSI 非基线，不能作为完成通知依赖。

### 9.3 多核任务划分、数据交换与 barrier

**初评划分建议：** 对单请求 GEMM 优先按 N 维输出列分片，每 core 使用不同 B 权重片，共享 A 通过复制或系统搬运分发，输出按列拼接。G0 两核/G0 Pro 四核 SKU 的权重带宽估算均以不重复读取同一层完整权重为前提；activation 分发和结果聚合另计流量。

按 M 维分片可能让多个 core 重读权重；独立请求的并行提高的是聚合吞吐，不等同于单请求加速。K 维分片需要跨 core partial sum 归约，不因现有 GEMM 内部 K pass 累加就默认支持，初版暂不采用。

**基础数据交换：** DMA 经 system interconnect 做 local/peer SRAM copy。每 core 暴露受范围约束的 peer window，远端访问参加本地 bank 仲裁。需要多个 core 或 main CPU 复用同一份数据时，可按第 5.5 节经 SoC SRAM 交换，避免逐 core 重复读 DRAM；共享区域的所有权与可见性仍由软件按第 8.2 节确认。

barrier 使用共享同步空间中的 atomic RMW + poll，前提是 atomic 与 scheduler 支持都已实现。通知的可见性和顺序遵循第 8.2–8.4 节。

### 9.4 可选核间事件通路

专用窄 event link 用于降低控制通知开销，不传 tensor 数据，也不替代 system interconnect。需定义 set/clear/ack、重复事件合并、复位状态和多来源竞争；性能改善待测，不预先保证亚微秒延迟。

## 10. 时钟、复位与电源管理

### 10.1 时钟域与 CDC

关联AR：[AR-HIVE-C004](#ar-hive-c004)、[AR-HIVE-C029](#ar-hive-c029)、[AR-HIVE-C038](#ar-hive-c038)、[AR-HIVE-C039](#ar-hive-c039)。电源与时钟管理以整个 NPU 区域为单位，引擎 clock-gating 不替代区域独立调频或电源关断。

**批准基线（MUST）：** 整个NPU区域具有独立可关断电源域和独立可调频时钟域；NPU调频、停钟和断电不要求main CPU、SoC system interconnect或DRAM子系统执行相同切换。两SKU均按以下区域边界集成，不要求逐core设置独立电源或调频域。

| 区域 | 包含单元 | 电源／时钟边界 |
|---|---|---|
| NPU域内 | 所有cores的local CPU、VPU、GEMM、DMA、scheduler、local SRAM，以及NPU侧互联和共享同步资源 | 整体支持独立调频、停钟和受控断电；内部共域／派生时钟组织待冻结 |
| NPU域外 | main CPU、SoC system interconnect、SoC SRAM、DRAM controller/PHY及LPDDR4x存储 | 不随NPU断电而关断；各自时钟与供电由SoC定义 |
| 域外边界管理 | NPU电源/时钟控制、reset控制、访问入口控制及电源边界隔离/唤醒路径 | NPU停钟或断电时仍可用；不得依赖已停钟的local CPU或已断电的NPU CSR完成唤醒 |

**时钟要求：** NPU 运行频率可独立调整，仅提供 clock gate 不满足该要求。G0/G0 Pro 的 SKU 峰值与服务验收仍以 500 MHz 为基准，调频能力不改变算力口径或验收门槛。独立电压调节和 DVFS 不属于基线；支持频点/范围、时钟源及切换时间由时钟/接口和物理规格冻结，当前待定。

**候选组织：** local CPU、VPU、scheduler、GEMM、DMA、local interconnect、SRAM及共享同步资源优先采用共同NPU时钟或同源派生时钟，内部组织仍需集成与物理确认。system interconnect、DRAM controller与LPDDR4x PHY时钟独立确定；system 500 MHz候选不限制controller/PHY频率。

AXI bridge跨域需成套处理地址、数据、响应及在途归属；异步FIFO的reset与两侧状态必须一致，正常切换不得用flush丢弃已接受事务。IRQ/event使用不会丢失通知的跨域机制，不能直接采样单拍脉冲；频率变化、停钟和断电边界须同时覆盖控制与数据路径。

### 10.2 复位与任务排空

关联AR：[AR-HIVE-C029](#ar-hive-c029)、[AR-HIVE-C030](#ar-hive-c030)、[AR-HIVE-C038](#ar-hive-c038)、[AR-HIVE-C039](#ar-hive-c039)。本节只定义初始化与正常域切换的约束，具体握手及顺序协议仍须冻结。

**初始化：** 上电后先确认供电和时钟稳定，reset在对应时钟域同步释放；local CPU及控制/同步状态完成初始化、必要数据显式装载后才允许任务执行。NPU重新上电按重新初始化处理，不假定透明续跑断电前任务。

**正常静止点：** 先停止新任务提交及无关外部/peer 新访问，保留已有任务完成所需的引擎派发、数据访问和响应排空路径。已有工作完成后，再关闭新派发及访问入口。

停钟、调频或断电前须确认：各 local CPU 处于安全停止点，VPU 操作完成，GEMM/DMA 空闲，已接受的 peer/AXI 事务及返回队列排空。仅 scheduler 空闲不足以证明整个 NPU 区域可安全切换。断电前还须将必要结果和需跨断电保存的数据写入域外存储，并确认其可见性。

**正常调频：** 在上述静止点完成时钟源/分频切换，确认新时钟稳定且CDC边界就绪后才恢复访问与提交；基线不承诺在命令执行中无停顿切频。仅调频或clock-gating不以清空SRAM/控制状态为前提，具体时钟切换电路须保证状态和通知正确性。

**范围：** 带在途事务的强制局部复位、故障隔离/恢复由实现阶段考虑，不在本文细化。复位或断电不保证撤销外部已接受写或回滚部分结果；正常排空要求不能替代仍暂缓的系统互操作协议。

### 10.3 电源状态

关联AR：[AR-HIVE-C024](#ar-hive-c024)、[AR-HIVE-C030](#ar-hive-c030)、[AR-HIVE-C038](#ar-hive-c038)。**批准基线（MUST）：** RUN、IDLE、CLOCK_GATED 和 POWER_OFF，包含区域级受控电源关断。

| 状态 | 电源／时钟 | 内部状态与进入／退出边界 |
|---|---|---|
| RUN | NPU供电开启，时钟运行于批准支持的频点 | 可执行任务和接受访问；原性能验收仍按500 MHz |
| IDLE | NPU供电开启，时钟运行，无活跃工作 | 状态保持；可恢复执行，停钟/调频/断电前仍须确认第10.2节静止点 |
| CLOCK_GATED | NPU供电开启，区域时钟停止 | 状态通过持续供电保持，不要求专用retention；域外唤醒路径恢复稳定时钟及访问条件后才接受访问 |
| POWER_OFF | NPU区域电源关断，时钟停止，跨电源边界隔离生效 | SRAM、寄存器、scheduler、同步资源及CPU/VPU状态不保证保持；重新供电须完成初始化和必要数据装载后才恢复执行 |

**受控关断：** 按第10.2节停止新访问并排空，确认必要域外结果可见；域外控制建立跨电源边界隔离并停止NPU时钟，再关断区域电源。跨域bridge、请求入口、IRQ/event及debug边界须在断电期间保持域外可见信号合法，不得使用掉电域的不确定输出。

**重新上电：** 域外控制恢复供电和稳定时钟，保持必要reset与边界隔离，完成同步reset释放和内部初始化后再开放边界访问；任务执行还须满足数据装载与依赖就绪条件。边界管理须支持初始化所需的受控访问，不能形成“等待域内就绪才能供电/供钟”的循环依赖。

POWER_OFF 不保证保留任何域内状态，不依赖 SRAM 或寄存器专用 retention。独立调压、retention 及逐 core 独立电源/调频域均为非基线。电压值、电源开关/隔离单元、信号极性和上电延迟由 SoC 电源/时钟及集成规格冻结，当前待定。正常电源边界隔离属于受控断电的电气要求，与详细故障隔离不同。

## 11. 可靠性、安全边界与观测

### 11.1 异常处理

G0 的详细异常恢复和故障隔离由实现阶段考虑，不属于当前架构评审或冻结前置。错误传播、timeout 和局部故障恢复机制未冻结，事务回滚不属于保证能力。基本响应正确性和 VPU 浮点数值要求仍须满足。

### 11.2 ECC 与访问保护

关联AR：[AR-HIVE-C032](#ar-hive-c032)。地址/CSR/debug保护是待冻结目标，不等同于完整多租户安全能力；ECC选择仍未形成批准AR。

**待确认：** local SRAM 是否采用 SECDED ECC、保护粒度、错误注入和不可纠错恢复。未实现 ECC 前不宣称存储完整性保证。

首版暂按单受信任任务上下文，不承诺虚拟地址、IOMMU、多租户隔离或抢占。至少需要系统地址 window 范围和 CSR/debug 访问权限契约；若平台要求多进程隔离，必须另立上下文与保护设计。

### 11.3 PMU 与调试

关联AR：[AR-HIVE-C033](#ar-hive-c033)、[AR-HIVE-C016](#ar-hive-c016)。硬件计数、软件 trace 和同步完成分别管理；PMU 详细定义暂缓，scheduler 同步事件的功能要求不变。

**目标观测：** GEMM active/load/stall，DMA physical/useful bytes、burst/在途水位，SRAM bank conflict 与访问等待，预取就绪等待与双缓冲切换停顿，AXI latency/backpressure，同步操作等待，scheduler 依赖等待与命令延迟。

当前 GEMM `perf` 为观测电平，系统计数器尚待实现。采样事件及条件、编码、位宽、溢出、清零、snapshot 和跨模块时间基准的定义暂缓。性能验收仍须提供可核验的测量证据；测试平台统计不代表产品内建 PMU 或其验收结果。

debug 暂停必须区分停止新派发和冻结在途事务。tracing/profiling 由软件主动插入标记命令，模块 tag 服务于 trace 记录，记录落到内存后由软件组合运行时 timeline；它不参与 scheduler 的同步完成匹配。trace 时间基准、记录格式、落盘路径、反压与丢失标记待规格确认，不先行保证未知 debug 接口的无扰动能力。

## 12. 性能、容量与带宽模型

### 12.1 统计口径

本章使用以下单位与计算口径，区分理论模型和完整任务实测。

所有数字区分：接口峰值、有效计算拍并行度、条件化持续目标、完整任务实测。byte/s 使用对应时钟域实际频率；GB 为十进制，KiB/MiB 为二进制。INT8/INT4 使用 TOPS，FP8/MXFP4 使用 TFLOPS，VPU FP32/FP16 使用 GFLOPS，MAC/FMA 均按 2 operations 计数，SKU 的 T 不代表 TMAC/s。不同精度的峰值比只表示资源配比，不等于相同算法的加速比。

G0 两核/512 KiB/core/4 GiB LPDDR4x x32 与后续 G0 Pro 四核/1 MiB/core/8 GiB x64、GEMM 500 MHz、LPDDR4x 4266 MT/s 为批准基线。第 12.8 节检测、质量及 Pro 扩展门槛保留，语言模型 MX 服务数值待确认；非 GEMM 共域/system 时钟、在途深度、带宽效率和首拍延迟仍待确认。第 2.3 节给出稠密峰值，当前未提供 cluster 持续吞吐、PPA 或端到端性能实测。

### 12.2 SRAM 容量与 tiling

关联AR：[AR-HIVE-C007](#ar-hive-c007)、[AR-HIVE-C008](#ar-hive-c008)、[AR-HIVE-C009](#ar-hive-c009)、[AR-HIVE-C041](#ar-hive-c041)、[AR-HIVE-S003](#ar-hive-s003)、[AR-HIVE-S004](#ar-hive-s004)、[AR-HIVE-S006](#ar-hive-s006)、[AR-HIVE-S018](#ar-hive-s018)。本节展开合法容量边界与必要固件/软件补齐动作；模型规划交付属于性能验收证据，不以单个分块算例证明所有算子可部署。

MX 部署容量按 W/A 全量化输入、scale 和 FP32 累加/中间结果核算。容量必须覆盖实际补齐维度、当前 A/B/C、下一任务预取、上一任务写回、VPU scratch 和保留区的生命周期峰值。C 输出为 FP32 时，16 个输出元素占 64 B、需两个 256-bit 写拍，不能按一个写拍预算。

GEMM 无外部 partial sum 输入：每个命令必须能完成其声明的 K 归约，固件/软件须通过显式搬运和跨引擎完成/可见性确认，确保所需地址在消费期间可读且不被覆盖。采用完整 K 的 A/B 驻留与双缓冲 B 是保守规划方案，不是 DMA 必须先装满全部 B 的硬件事实。若使用 K 流式补给，须证明供数、区域所有权和前进性；若为 scale 桥接拆成多个独立 K 子命令，其 FP32 输出必须由 VPU/软件显式累加，不能假定 GEMM 可读回外部 partial sum。

双缓冲指相邻任务的 SRAM 区域，不表示 PE 已支持双 bank。每个基线模型须核验实际分块及 FP32 中间结果成本，单个算例不能代替所有算子的驻留与重叠验证。

**批准基线/现状默认：** `cBufferDepth=64,T=16`，单命令 `M≤48`。增加 SRAM 不扩大该上限；更大 M 由软件分块，扩大结果 buffer 需另行变更评审。

**容量分档：** G0 512 KiB/core，Pro 1 MiB/core。活跃区峰值、访存竞争和完整任务服务安排在后续性能仿真中联合评估；M/N 分块、完整 K 驻留、流式补给、预取及重叠方案尚未冻结。候选算例不能证明容量足够，更大 SRAM 也不保证完整双缓冲同时驻留或按容量比提升速度。

基线容量按单模型运行核算，不保证多个模型同时常驻。源模型的权重量化在离线环境完成，不作为 G0 运行期常驻负载。实际数据计划须合法、峰值内存不超容量，并满足第 12.8 节适用的服务目标；候选分块或搬运方案仅供分析。

SoC SRAM 容量独立核算，不计入每 core local SRAM 可用容量，也不放宽第 12.8 节的服务目标。共享层的容量分档未冻结，使用它的数据计划须说明共享区域峰值、跨 core 复用收益和访问延迟/竞争代价。

三个语言模型及两个检测模型须分别核验 W/A 全量化的算子映射、scale、激活量化、持久状态及容量，不共享未经验证的模型预算。

*Note：8/16 GB/s 参考 payload、DMA 在途深度及系统互操作协议仍待确认。数据规划不替代重叠容量/性能仿真或 PMU 定义与验收。*

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
- bank 映射、合并、错峰和竞争下的实际服务验证共同决定实际吞吐。

必须测试实际地址分布和负载组合，不能用 `216<256` 就得出无瓶颈结论。

### 12.4 Decode、DRAM 与在途数据

关联AR：[AR-HIVE-H001](#ar-hive-h001)、[AR-HIVE-H002](#ar-hive-h002)。本节分析外部存储理论带宽与模型流量，不另设 DRAM 服务验收指标。

**批准配置：** G0 为 4 GiB LPDDR4x x32，Pro 为 8 GiB x64，速率均为 4266 MT/s。按总并行数据位宽计算，理论峰值为 17.064/34.128 GB/s，不再次乘 DDR 系数。NPU 实际可用带宽还受 controller、system fabric 和 DMA 搬运效率约束；16 GiB 非基线扩容只增加容量，不提高 x64 带宽。

模型级预算分别覆盖第 2.1 节三个语言模型的 MXFP8、MXFP4 W/A 全量化版本。每 token 流量须包含实际执行的量化权重与激活、scale、KV 或其他持久状态的读写、中间结果、控制及其他 SoC 竞争；checkpoint 文件大小不等于 decode 权重流量。

N 维权重分片只减少每核持有的分片，不保证持久状态与向量归约均匀分摊。权重复制、重复读取、布局展开、scale 桥接和独立 K 子命令的 FP32 累加均须计入；不以压缩比或有效计算拍峰值推断持续吞吐。若数据计划用 SoC SRAM 承接跨 core 复用，须按实测说明减少的 DRAM 流量、增加的 system path 流量和共享端口竞争，不默认共享层消除重复读取。

在途与返回容量按所需带宽×延迟估算，并计入排队余量；四 burst/engine 是实现候选而非性能保证。

持续服务受最窄串行端点约束。多读在途隐藏延迟，不消除 DMA 内部收集/输出串行节奏或量化吞吐上限；不能只扩大 AXI outstanding 就宣称引擎级并发。SRAM 双缓冲不降低没有复用的一次性权重流量。

在 W/A 全量化具有合法执行计划、数值通过且完整任务流量已覆盖前，只能报告条件化上界，不发布模型 token/s 保证。

### 12.5 Prefill 与 KV 容量

G0/G0 Pro 的 prefill 理想计算时间按各语言模型 MXFP8/MXFP4 执行的实际主矩阵总 MAC 数除以第 2.3 节对应 SKU 的峰值 MAC 吞吐计算，该结果只建立计算下界。权重装载、阵列启动/排空、attention/VPU、调度、量化与数据搬运必须另行计入，不能用理想时间承诺端到端 prefill 时延或 Pro 实测加速比。MXFP4 W/A 全量化须独立通过质量验收，4bit 峰值不代表同等模型速度收益。

持久状态按各模型的实际层类型、维度、上下文长度和存储格式核算。对于传统 attention 层，KV 容量按各层求和：

```text
KV_bytes/token = sum_attention_layers(2 × kv_heads × head_dim × bytes/element)
KV_capacity    = KV_bytes/token × context_length + scale/page/padding overhead
```

不得用 query heads 或 hidden size 推断 KV 宽度。Qwen3.5-0.8B、Qwen3.5-2B 与 DeepSeek-R1-Distill-Qwen-1.5B 须分别核验 KV 及其他持久状态，不假定全层采用相同的 KV 结构或 FP16 存储。分页、padding、生成期间 context 增长及临时 attention 块须计入；不同状态格式和算子落点独立验收，公开最大 context 不等于 G0 的可保证长度。

### 12.6 PPA 与测量依据

500 MHz 是 GEMM 的产品目标，尚未完成时序签核。面积、功耗和时序实测尚未取得，不能按降频比例推算节能比例，也不能按容量或核数比例推算整芯片面积。

*Note：早期 PPA 规划暂缓，面积/功耗预算、macro 选型、测量条件和报告细项由后续物理实现及验收规格定义，目前没有相应数值 AR。*

### 12.7 VPU 性能指标与接口要求

**批准基线（MUST）：** 每个 NPU core 配套一份独立的 256-bit 等效向量执行能力，VPU 目标时钟为 500 MHz，支持 FP32 和 FP16。以下集成要求已纳入 B 方案，尚未完成仓库外实现确认或 PPA 验证；产品对比、宽度选型及工作负载推导归入[性能分析](performance_analysis.md#2-vpu-配比选型与工作负载分析)，不作为本节的替代指标。

#### 12.7.1 计算与数据接口指标

关联AR：[AR-HIVE-C022](#ar-hive-c022)、[AR-HIVE-C023](#ar-hive-c023)、[AR-HIVE-P006](#ar-hive-p006)、[AR-HIVE-P007](#ar-hive-p007)。执行/端口配置与FMA稳态峰值分别核验，聚合值要求每core同时满足测量条件。

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

FMA 按 2 operations 计数；FP32/FP16 是替代运行模式，峰值不得相加。FP16 16 GFLOPS 要求每拍处理 16 个有效 FP16 FMA，仓库外实现必须提供相应打包算术能力。等效执行宽度规定元素吞吐，不规定 ISA 向量长度、物理 lane 组织或 SRAM 数据口以外的内部端口宽度。

计算峰值的验收条件为操作数驻留寄存器或局部缓冲、无访存反压、足够独立 FMA 消除依赖停顿、全部元素有效，并排除流水线启动/排空。多核聚合峰值要求各 core 同时满足这些条件。local 数据接口峰值按连续满宽请求、无仲裁冲突和无返回反压计数；它不是独立读写各 16 GB/s，也不保证 SRAM 流式算子达到寄存器内 FMA 峰值。

#### 12.7.2 算术与数值能力要求

| 能力／关联AR | 首版要求展开 |
|---|---|
| 基础算术：[C034](#ar-hive-c034) | FP32/FP16 add、mul、FMA、比较与选择；FMA 仅在最终结果处舍入 |
| 混合精度：[C035](#ar-hive-c035) | FP16 输入转为 FP32 后执行累加/归约，支持结果转回 FP16；可由转换加 FP32 算术实现，不要求原生混合 FMA 或 FP16 双倍累加吞吐 |
| 归约：[C035](#ar-hive-c035)、[C036](#ar-hive-c036) | sum/max；归一化与 softmax 的关键累加使用 FP32；明确归约顺序与误差界限 |
| 数据转换：[C035](#ar-hive-c035) | FP16↔FP32；量化辅助涉及的转换、舍入和饱和语义必须在算子规格中定义 |
| 掩码与tail：[C036](#ar-hive-c036) | 支持非满向量长度；无效元素不得更新结果、参与归约或产生越界访问 |
| 特殊函数：[S022](#ar-hive-s022) | exp、reciprocal、rsqrt 不强制采用专用硬件；未由硬件提供时由固件/向量软件补齐所需函数，分别定义输入域、误差、启动间隔或软件服务时间 |
| 浮点边界：[C037](#ar-hive-c037) | 明确舍入模式、NaN/Inf、subnormal、signed zero 和异常可见性；不得用格式支持代替数值契约 |

仓库外实现须满足第 4.4 节的向量算子范围。完整 FP16 attention 矩阵运算不属于本节 FMA 峰值的端到端性能保证；算子落点必须在模型执行计划中明确，不得将当前 GEMM 不支持的输入格式视为已覆盖。

#### 12.7.3 集成与前进性要求

- VPU 经 local interconnect 访问 local SRAM，遵循第 7.3 节缓冲区所有权和第 8.2 节结果可见性规则；不得读取未就绪输入或提前发布完成。
- local 数据口支持 byte enable、返回归属和反压；adapter 保持双方约定的返回顺序，并按返回缓冲容量限制在途请求。
- 调度必须为 VPU 和 DMA 提供有限背压下的前进性；VPU 请求反压时，已接受事务的响应仍须排空。
- VPU 的控制/指令、背压与完成观察直接连接 local CPU，不与 scheduler 建立命令关联；local CPU 必须区分操作完成与写结果可见，跨引擎阶段同步另定接口规则。
- cluster 共享 VPU 为非基线变更；若另行批准，必须满足对应 SKU 聚合计算指标、各 core 本地数据访问及公平服务，不以共享单核资源替代上述要求。

#### 12.7.4 验收与规格冻结要求

| 验收项／关联AR | 必须提供的结果 |
|---|---|
| 峰值计算：[C022](#ar-hive-c022)、[P006](#ar-hive-p006)、[P007](#ar-hive-p007) | FP32 8、FP16 16 FMA/cycle/core 的稳态测量；记录频率、有效元素、依赖条件和测量窗口 |
| local数据口：[C023](#ar-hive-c023)、[C026](#ar-hive-c026) | 256-bit 满宽请求的读、写及混合序列；核对读写合计每拍最多一请求、数据正确性、反压及返回顺序 |
| 非FMA能力：[C034](#ar-hive-c034)、[C035](#ar-hive-c035)、[C036](#ar-hive-c036)、[S022](#ar-hive-s022) | add/mul、混合精度、转换、sum/max、exp/reciprocal/rsqrt 分项给出吞吐、延迟或启动间隔与误差；不得套用 FMA GFLOPS |
| 完整算子：[C035](#ar-hive-c035)、[C036](#ar-hive-c036)、[S022](#ar-hive-s022) | RMSNorm/LayerNorm、softmax、SiLU、RoPE、residual add、格式转换及量化辅助的数值与服务时间；GELU 仅在目标模型采用时验收；算术、转换/归约与必要特殊函数补齐分别核验，不另列模型执行设计AR |
| 系统并发：[C028](#ar-hive-c028)、[S006](#ar-hive-s006) | GEMM/DMA/VPU 同时运行时的 SRAM 有效字节、bank 冲突、等待与完成可见性；G0 两核和后续 G0 Pro 四核分别验证 |
| 边界与正常生命周期：[C029](#ar-hive-c029)、[C030](#ar-hive-c030)、[C037](#ar-hive-c037) | tail、非有限值、地址边界、持续反压、初始化reset与正常drain，不丢响应、不误报完成 |

local CPU↔VPU 直接控制接口与 VPU 本地数据接口冻结前，须明确以下批准值或协议：

- 非 FMA 指令吞吐、混合精度路径吞吐；
- 特殊函数数值误差、端到端算子允许服务时间；
- local CPU 的完成/可见性观察、跨引擎阶段次序；
- 在途深度、tracing 标记、初始化 reset 及正常 drain 协议。

这些细项当前待确认，未确定唯一值或协议前，VPU 集成不能判为验收完成。延迟、误差和流式吞吐尚无保证值；故障恢复不属于冻结前置。

### 12.8 批准工作负载与持续验收门槛

服务指标关联 P008–P018，质量门槛关联[AR-HIVE-P020](#ar-hive-p020)、[AR-HIVE-P021](#ar-hive-p021)、[AR-HIVE-P022](#ar-hive-p022)、[AR-HIVE-P023](#ar-hive-p023)。以下输入条件定义服务指标适用的工作负载，不单独分配性能 AR。

**批准基线（MUST）：** Qwen3.5-0.8B、Qwen3.5-2B、DeepSeek-R1-Distill-Qwen-1.5B 及 YOLO11n/YOLO11s 均采用 MXFP8、MXFP4 W/A 全量化部署。语言模型对照同源 BF16，检测对照同源官方浮点参考；量化与导出版本须在实际验收前冻结。

三个语言模型的 MX 服务数值待确认，不继承旧 FP8 模型阈值。Qwen3.5-0.8B/2B 在 P008/P010/P012 下分别核验，DeepSeek 在 P009/P011/P013 下核验；每个模型、格式及 SKU 均独立确定服务值。检测、质量和 Pro 扩展门槛如下。

- **P512：** batch1、512-token prompt、生成256 tokens；warm模型已加载且可执行，TTFT为完整prompt处理至首token的时延。
- **D2048：** batch1、初始2048-token上下文、连续256个decode step；持久状态按实际模型构建并冻结存储格式，TPOT为各步时延。
- **检测：** COCO val2017、80类、640×640、batch1；服务包含预处理、网络、框解码、过滤、NMS和坐标还原。

| 关联AR | 指标（MXFP8/MXFP4 分别核验） | G0 | G0 Pro |
|---|---|---:|---:|
| [AR-HIVE-P008](#ar-hive-p008) | Qwen3.5-0.8B、Qwen3.5-2B各自D2048平均decode | 待确认 | 待确认 |
| [AR-HIVE-P009](#ar-hive-p009) | DeepSeek-R1-Distill-Qwen-1.5B D2048平均decode | 待确认 | 待确认 |
| [AR-HIVE-P010](#ar-hive-p010) | Qwen3.5-0.8B、Qwen3.5-2B各自P512 warm TTFT p95 | 待确认 | 待确认 |
| [AR-HIVE-P011](#ar-hive-p011) | DeepSeek-R1-Distill-Qwen-1.5B P512 warm TTFT p95 | 待确认 | 待确认 |
| [AR-HIVE-P012](#ar-hive-p012) | Qwen3.5-0.8B、Qwen3.5-2B各自D2048 TPOT p95 | 待确认 | 待确认 |
| [AR-HIVE-P013](#ar-hive-p013) | DeepSeek-R1-Distill-Qwen-1.5B D2048 TPOT p95 | 待确认 | 待确认 |
| [AR-HIVE-P014](#ar-hive-p014) | YOLO11n持续检测 | ≥30 fps | ≥48 fps |
| [AR-HIVE-P015](#ar-hive-p015) | YOLO11n完整检测p95时延 | ≤50 ms | ≤35 ms |
| [AR-HIVE-P016](#ar-hive-p016) | YOLO11s持续检测 | ≥10 fps | ≥16 fps |
| [AR-HIVE-P017](#ar-hive-p017) | YOLO11s完整检测p95时延 | ≤150 ms | ≤100 ms |
| [AR-HIVE-P018](#ar-hive-p018) | 同artifact/profile单请求decode扩展 | 比较基准 | 实测≥1.6×G0 |

Pro 单请求 decode 扩展指标使用同 artifact、profile 与输入的 G0/Pro 实测速度比值，扩展效率为加速比/2。

**质量门槛：** 五个模型的 MXFP8/MXFP4 部署分别通过独立量化参考的逐算子误差与模型任务一致性验收。三个语言模型相对同源 BF16 的固定语料 perplexity 相对增幅≤5%、任务准确率绝对下降≤1个百分点；检测部署相对同源浮点参考的 COCO mAP50–95 绝对下降≤1个百分点。数据集/hash、逐算子误差预算、校准和量化 recipe 须在数值验收前冻结；MXFP4 若需放宽门槛须重新评审，perplexity 单项不能替代完整生成质量。

*Note：本节保留服务与质量指标及必要输入条件。采样次数、运行时长、测量分项和报告格式由后续验收规格定义，不作为独立架构要求。*

语言模型 MXFP8/MXFP4 服务数值、cold TTFT 和功耗上限待定，不能按压缩比推断速度。服务值获批后，按第 14.3 节完成正式签核；待确认项不得签为通过。

## 13. 架构取舍、实现差距与待确认项

### 13.1 现有能力到系统目标的差距

| 组件 | 差距 | 后续动作/条件 |
|---|---|---|
| core 顶层/本地互联/SRAM | 尚未实现 | 按 G0 512 KiB/core、Pro 1 MiB/core、均 8-bank 建立路由、adapter、窗口、仲裁与返回路径 |
| GEMM | 已有计算与本地端口，无系统任务闭环 | 保持 32-bit offset；接入 local SRAM，不直接扩大 DRAM 访问职责 |
| GEMM 持续供数 | 阶段重叠和 W4 子字复用待优化 | 仅在目标吞吐需要时引入 PE 双 bank/子字缓存；独立验证 |
| GEMM 结果 buffer | 默认 M≤48，批准基线保持 cBufferDepth=64 | 大 M 由软件分块；扩大 buffer 须单独变更，不能解决 SRAM 容量不足 |
| DMA | 128-bit 默认、每 engine 单读在途 | 集成 256-bit 并实现多读在途；读/写四 burst 候选须按延迟、transfer 边界和返回协议冻结 |
| scheduler/任务管理 | 硬件译码骨架，无软件scheduler与local CPU任务执行闭环 | 主CPU软件scheduler提交/回收任务；local CPU控制scheduler派发GEMM/DMA，并直接管理VPU；8 slots/core、tracing独立，硬件ring非基线 |
| 固件/软件显式数据搬运与复用 | scratchpad无自动补给，系统级驻留、预取和复用契约待定义 | 明确 SRAM 分配、tensor 生命周期、DMA 依赖及容量不足时的显式搬运，核验跨引擎可见性；模型数据计划仅作性能验收证据，不定义runtime设计AR |
| MX 算子映射 | 五个模型的 W/A 全量化、scale、状态与算子映射尚无系统闭环 | 分别验证 MXFP8/MXFP4 的量化、桥接与 FP32 累加，核验质量、容量及完整服务 |
| 系统互联/内存 | 尚未实现 cluster 系统访问，持续带宽未测 | 按批准 G0 4 GiB x32 / Pro 8 GiB x64、4266 MT/s LPDDR4x 配置集成 DRAM/peer/仲裁/CDC；汇聚拓扑仍为候选 |
| SoC 共享 SRAM | 尚未实现 | 按第 5.5 节建立共享 window、多主仲裁与访问许可；容量分档、位宽和 bank 组织按 2.2.2 冻结条件确定，并验证共享收益与竞争 |
| 独立同步单元 | 尚未实现 | 实现 READ/WRITE/FETCH_ADD；冻结状态容量、同步窗口、原子/普通访问序列化和任务关联 |
| core内CPU/VPU集成接口 | VPU 计算/端口已批准，仓库外实现与细项契约未确认 | 按 12.7 核对每 core 独立 256-bit、FP32 8 / FP16 16 GFLOPS，并冻结非 FMA、数值、反压/返回与事件规则 |

### 13.2 架构决策清单

| 决策 | 批准基线 / 候选 / 待确认 | 验证或冻结依据 |
|---|---|---|
| SKU 与 GEMM | MUST：G0 两核 1T、Pro 四核 2T，均 500 MHz、T=16、cBufferDepth=64 | INT8/FP8 稠密口径、软件分块、时序与 PPA |
| 模型与部署量化 | MUST：Qwen3.5-0.8B、Qwen3.5-2B、DeepSeek-R1-Distill-Qwen-1.5B、YOLO11n/s，部署 MXFP8/MXFP4，W/A 全量化 | 各模型/格式独立核验量化、算子、状态、容量与服务；语言模型服务数值待确认，检测/质量及 Pro 扩展门槛见 12.8；NVFP4 额外 tensor/global scale 不支持 |
| DRAM | MUST：G0 4 GiB LPDDR4x x32，Pro 8 GiB x64，均4266 MT/s；16 GiB 非基线扩容 | 按容量、位宽与速率实现配置；不约束器件密度或rank |
| NPU时钟与DRAM汇聚 | MUST：NPU区域独立时钟域及调频，500 MHz验收基准不变；候选内部共域/同源派生；DRAM汇聚候选G0 256-bit、Pro 512-bit或两路256-bit @ 500 MHz | 接口冻结时选定内部组织、频点/切换及拓扑/CDC，性能和物理阶段验证 |
| NPU电源域 | MUST：整个NPU区域独立受控关断，main CPU/system/DRAM及必要域外边界管理不随之关断 | 第10章域边界、正常排空、边界隔离及上电初始化；电气时序待冻结，不默认retention、独立调压或逐core域 |
| SRAM 容量/banks | MUST：G0 512 KiB/core、Pro 1 MiB/core，均 8 × 256 bit | P1-4重叠容量/性能评估待后续性能仿真，不冻结具体数据计划；正式容量/竞争服务验收保留，P0-7 PPA暂缓 |
| SRAM macro/端口 | 候选 1RW，byte-write/macro 拼接待确认；16-bank/1R1W 非基线 | 库/接口冻结，部分写与 500 MHz 时序 |
| SoC 共享 SRAM | MUST：多 core 与 main CPU 共享的显式管理片上 SRAM，经 system interconnect 访问；容量、位宽、bank 与访问协议待冻结 | 第 5.5 节共享规则、第 8.1 节 window 与 8.3 节映射维护；按共享流量、竞争与面积确定容量，不计入 local SRAM 容量或带宽 |
| DMA | MUST：2 × 256-bit/core、40-bit address、最大 64 beats、多读在途；候选读/写各四 burst/engine | 返回关联、credit、transfer 边界及实际延迟 |
| 软件任务与硬件调度 | MUST：主CPU软件scheduler↔local CPU管理任务；local CPU使用scheduler的8 slots/core、单GEMM上下文，直接管理VPU | scheduler仅管理GEMM/DMA同步完成；P0-4互操作/可见性协议暂缓，tracing独立；P1-1详细调度与握手/资源释放流程不属架构细化范围 |
| 独立同步单元 | MUST：READ/WRITE/FETCH_ADD；候选 64 × 32-bit，协议待确认 | 活跃 barrier/event 数、顺序与线性化点 |
| main CPU coherence | MUST：非一致 AXI＋固件/软件 cache 维护；映射/屏障协议暂缓 | 平台非一致维护契约、所有权和结果可见性，不定义具体 API |
| VPU | MUST：每 core 独立 256-bit、500 MHz、FP32 8 / FP16 16 GFLOPS；仓库外实现及细项待确认 | 12.7 的计算/接口/数值验收；共享 VPU 非基线 |
| control/IRQ | MUST：AXI4-Lite 32-bit、local/main CPU level；寄存器与路由细项待确认 | doorbell、通知、mask/clear |
| 生命周期/保护 | MUST：RUN/IDLE/CLOCK_GATED/POWER_OFF；初始化reset、正常drain、调频、断电边界隔离及基本访问保护，具体协议待冻结；ECC非基线 | 正常域切换、重新初始化、CDC及地址/CSR/debug访问边界；ECC不承诺为已实现；详细异常恢复/故障隔离留实现阶段考虑，不在当前架构细化 |
| 增强能力 | LLC、HBM/3D DRAM、K 维跨核归约、抢占、专用 event link 等非基线 | 不形成基础依赖；变更须独立评审。已批准的 SoC SRAM 不属于此列 |

B 方案的实际服务和物理实现须按第 2.2.2 节所列条件验证，候选值不能替代验收证据。

## 14. 系统验证与交付边界

### 14.1 单 core 功能闭环

首个里程碑：

1. 经已确认入口提交 DMA→GEMM→DMA 依赖链；
2. 从外部存储搬输入到 local SRAM，写完成后转移所有权；
3. GEMM 计算并写 SRAM，检查数据、scale、tail 和模块错误；
4. DMA 将结果写回外部存储，排空必要响应；
5. 验证同步完成可被 CPU 观察、软件完成记录正确，并验证 local/main CPU IRQ。

local CPU入口先验证GEMM/DMA引擎闭环及直接VPU控制；主控CPU软件scheduler再验证任务提交、local CPU执行和任务完成回收。硬件ring/fetcher/completion writer不是交付依赖。

### 14.2 多核与边界测试

先完成 G0 两核 1T 闭环，再独立验证后续 G0 Pro 四核 2T SKU 的入口扩展、任务分片、同步、聚合带宽与功耗；不能用 G0 通过推断 G0 Pro 已验收。

至少覆盖以下场景，任务完成与引擎完成分别核验：

- peer copy 与本地计算争 bank、多主同步与可见性；
- 多 core 与 main CPU 共享 SoC SRAM 的 window 范围、访问许可、并发竞争与所有权交接；
- 持续反压、GEMM/DMA 依赖与完成正确性；
- SRAM 访问不覆盖仍在使用的数据；
- main CPU cache 维护、窗口越界；
- 独立的 tracing 标记与内存记录核对。

硬件 ring 不是验收依赖。命令握手、资源回收和软件队列管理流程由实现验证，不作为独立架构细化项。重叠数据计划待性能仿真评估，双缓冲与预取策略尚未冻结；详细故障恢复/隔离不属于当前架构验收清单。

正常电源/时钟验收按[C024](#ar-hive-c024)、[C029](#ar-hive-c029)、[C030](#ar-hive-c030)、[C038](#ar-hive-c038)、[C039](#ar-hive-c039)及第 10 章冻结的责任规格，分别覆盖两 SKU 的整个 NPU 区域：

- 初始化、clock-gating 唤醒、支持频点切换、受控断电和重新上电；
- CPU/VPU 安全停止、所有已接受事务及响应排空、断电边界隔离和域外控制可用，不丢响应或提前恢复访问；
- NPU 独立调频/断电时，main CPU、system 及 DRAM 无需跟随切换且可继续工作；
- CLOCK_GATED 状态保持；POWER_OFF 后不依赖内部状态保留，完成重新初始化和显式数据装载后再执行任务。

电气/时序证据由后续物理阶段提交，功能仿真不能替代物理签核。

READ/WRITE/FETCH_ADD 与非一致 main CPU cache 维护属于批准基线，协议冻结并实现后必须纳入验收；ECC、event link、coherent main CPU 为非基线增强，只有另行选择实现后才进入对应验收，不提前宣称能力。

### 14.3 性能验收

在目标 500 MHz 下分别核对 G0 两核/G0 Pro 四核 INT8、FP8、INT4、MXFP4 及 W4A8 的每有效拍乘加贡献和第 2.3 节峰值指标。G0/G0 Pro 的 1T/2T SKU 定位不替代完整任务服务与物理时序验收。

独立调频不改变 500 MHz 服务门槛。warm P512 的前提是模型已加载且可执行，POWER_OFF 后须先完成供电/时钟恢复、初始化和数据重载。切频、上电延迟和功耗当前没有数值指标。

VPU 按第 12.7 节验收每 core 256-bit 等效执行、500 MHz 下 FP32 8 / FP16 16 GFLOPS，以及单个 256-bit local 请求口的读写合计峰值；混合精度归约、转换、exp/reciprocal/rsqrt 和完整算子按各自批准指标验收。记录有效元素、访存、特殊函数和归约停顿，覆盖 tail、误差、非有限值及跨 core 竞争。寄存器内 FMA 峰值与 SRAM 流式吞吐分别发布，attention 矩阵落点不同的任务分开验收。

硬件峰值、语言模型端到端服务和检测服务按各自指标验收。MXFP8、MXFP4 分别核验，不以一个格式的成绩替代另一个格式。

首轮 signoff 覆盖 Qwen3.5-0.8B、Qwen3.5-2B、DeepSeek-R1-Distill-Qwen-1.5B 及 YOLO11n/YOLO11s 的 W/A 全量化部署，按以下步骤验收：

1. **输入条件：** 按第 2.1 节核验模型版本、W/A 量化覆盖及执行格式，服务指标使用第 12.8 节输入条件。
2. **数值与功能：** 语言模型对照同源 BF16，检测对照同源浮点参考；执行结果对照独立量化金锚。覆盖全部算子、scale 桥接、激活量化与 FP32 累加/后处理，不以 RTL 行为拟合金锚。
3. **容量：** 核验实际执行计划的 DRAM/SRAM 峰值内存及 KV/其他持久状态不超批准容量；未经校验的周期/流量模型不替代服务验收。
4. **完整任务：** 语言模型服务值获批后，核验 warm TTFT p95、平均 decode 与 TPOT p95；检测按已批准的持续 fps、p95 时延和 COCO mAP 门槛验收。Pro 按同 artifact、profile 与输入的实测速度核验扩展指标。

每个模型的 MXFP8、MXFP4 分支按[第12.8节](#128-批准工作负载与持续验收门槛)独立验收。未确定的语言模型服务值不得以旧 FP8 指标或已有硬件峰值替代。

*Note：重叠容量评估和 PMU 定义分别处理，见第 12.2 节及第 11.3 节。*

当前参考基线为独立模块，尚无 core/cluster 集成 RTL 或系统性能验证结论。软件 scheduler 与 local CPU 的控制职责已确定，任务通知、完成可见性和 fence/drain 等系统互操作协议仍暂缓；协议确认后补充相应系统验收。正常电源/时钟域切换须满足第 10 章约束，错误处理范围见第 11.1 节。

### 14.4 逐AR责任、证据与结论

后续规格和测试报告须按第 1.5 节的完整 AR ID 逐项追溯，模块或章节的总体测试结果不能替代单项结论。当前有 71 条有效 AR（C41/H2/S6/P22）。各条目的批准及冻结状态见第 1.5 节，实现差距见第 13.1 节。

*Note：17 个 S 编号、H003/H004 及 P019/P024–P029 保留撤销记录，不复用；有效 AR 尚未登记为实测通过。*

每条证据记录须包含以下字段：

| 字段组 | 内容 |
|---|---|
| 要求与责任 | AR ID、适用 SKU、要求版本/批准状态、责任规格及条款 |
| 实现基线 | 实现基线及参数 |
| 验证依据 | 验证方法、用例或报告位置、测量条件 |
| 结果与审查 | 预期/实测结果、验收结论、审查记录 |

要求批准、实现状态与验证结论分列维护。目标未批准或细项未冻结时，按状态标为待冻结/待验证，不得签为通过。

各责任领域按以下方法取证：

| 类别／要求 | 验证依据 |
|---|---|
| C：配置与拓扑 | core 和 SoC 集成检查 |
| C：GEMM、DMA、scheduler、SRAM、CSR/IRQ、同步能力 | 第 14.1–14.2 节功能与边界场景 |
| C：CDC、流控、保护与生命周期 | 对应冻结规格及竞争/反压证据 |
| C：VPU | 第 12.7.4 节分项证据 |
| C：500 MHz | 后续物理时序证据 |
| H：[H001](#ar-hive-h001)、[H002](#ar-hive-h002) | G0/Pro 的 LPDDR4x 类型、容量、总位宽及4266 MT/s速率，不约束器件密度或rank形式 |
| S：第 1.5.4 节六条有效要求 | 按每条硬件限制及对应固件/软件动作逐项取证；暂缓协议须在契约批准后正式验收 |
| P：[P001](#ar-hive-p001)–[P007](#ar-hive-p007) | 硬件有效计算拍/FMA 稳态性能 |
| P：[P008](#ar-hive-p008)–[P018](#ar-hive-p018) | 语言模型端到端服务、完整检测服务及 Pro 扩展指标 |
| P：[P020](#ar-hive-p020)–[P023](#ar-hive-p023) | 冻结参考、数据集及数值预算下的精度与质量 |

[AR-HIVE-C038](#ar-hive-c038)、[AR-HIVE-C039](#ar-hive-c039)须分别提交区域独立供电/受控关断和独立调频证据，结合 C024/C029/C030 核对状态、边界隔离及重新初始化；clock-gating 不能替代这两项能力。

[AR-HIVE-C040](#ar-hive-c040)、[AR-HIVE-C041](#ar-hive-c041)须提交 SoC SRAM 的共享访问、window 范围/许可、多主竞争与显式管理证据，并核验其容量和带宽未被计入 local SRAM 预算；容量分档和访问协议冻结后再做对应服务验收。

S 类只验收硬件限制对应的固件/软件补齐动作，runtime、驱动、API 或模型部署设计不作为 S 类证据。P 类按第 12.8 节的模型与格式范围验收，每个模型、格式及适用 SKU 均需独立结论；服务数值待确认的条目保持待冻结。

P 类责任规格须记录由哪些 C/H/S 要求共同满足目标，实现分配不改变 P 编号。例如[AR-HIVE-P008](#ar-hive-p008)须核对 GEMM 计算、DMA/内存服务、SRAM 驻留，以及必要的固件/软件搬运和跨引擎可见性补齐。模型 manifest、执行落点、量化/数值及容量/流量计划属于 P 类验收输入，不另列模型或 runtime 设计 AR，也不作为 S 类设计证据。

资源组合是否足以达标仍需实际验证。派生硬件要求或必要固件 callout 须经变更评审；未冻结的实现方案不能作为批准要求。

## 附录 A：当前实现追溯

以下链接仅保留真实仓库路径；组件说明均使用通用名称，不改变源码模块命名。

| 组件 | 当前参考文件 | 用途 |
|---|---|---|
| GEMM | [模块规格](../../workerbee/doc/spec.md)、[默认配置](../../workerbee/scala/main/WorkerBeeConfig.scala)、[顶层接口](../../workerbee/scala/main/WorkerBee.scala)、[运行期几何](../../workerbee/scala/main/WorkerBeeCalcConfig.scala) | 阵列、合法域、端口、命令与缓冲上限；T=16 时 A/B MX block16/32、C MX block16 |
| DMA | [模块规格](../../foragerbee/doc/spec.md)、[默认配置](../../foragerbee/scala/main/ForagerBeeConfig.scala)、[顶层接口](../../foragerbee/scala/main/ForagerBee.scala)、[数据协议](../../foragerbee/scala/main/FbStreamBus.scala) | 数据宽度、地址和在途响应约束 |
| scheduler | [实现骨架](../../queen/scala/main/Queen.scala)、[配置](../../queen/scala/main/QueenConfig.scala)、[CPU接口](../../queen/scala/main/QueenInterface.scala) | 当前硬件译码骨架；文件名Queen不表示已经实现主CPU↔NPU的软件scheduler任务管理 |
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

VPU 产品配比、执行宽度扩展、工作负载、访存推导及公开来源核验见[性能分析](performance_analysis.md)。这些分析不替代架构要求，也不构成实现或产品性能保证。

*Note：性能分析中的旧语言模型 FP8 算例尚未按本版 MX 部署基线重算，不作为当前三个语言模型的容量或服务验收依据。*
