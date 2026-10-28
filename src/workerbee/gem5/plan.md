# WorkerBee gem5 模型实施规划

版本：v0.1.2（P0 实施中）  
日期：2026-10-28  
状态：已实现 P0 idle 骨架、基线工具和 RTL 边沿单测；单测运行受宿主启动问题阻塞，P0 验收未通过，尚无 GEMM 执行模型  
目标：以 RTL 校准的 GEMM timing 模型开展 kernel 和网络性能探测。

本文定义目录、文件职责和实施顺序；行为与验收契约见 [模型规格](spec.md)。硬件契约引用 [WorkerBee 硬件规格](../doc/spec.md)，不在这里另建硬件寄存器或指令编码。

## 1. 实施范围与路线

采用 gem5 `ClockedObject` + C++ 时序状态模型 + 六路内存请求端口。第一版不引入 CPU、Linux、MMIO 或尚未定义的 NPU ISA，使用 trace driver 驱动现有四路命令接口。

先实现逐 GEMM 时钟拍推进的参考 timing 模型，保留 FIFO、DMA、权重装载、波前、回灌及输出转换的资源关系；通过 RTL 对拍后再压缩无外部交互的事件区间。不能先用固定 GEMM 延迟替代内部执行过程。

第一版为 **timing-only**：支持当前默认输入格式与合法输出格式对应的时序、地址、流量及中断，不执行完整矩阵数值计算。它可以完成命令轨迹的性能仿真，但不能验证网络输出或精度。具体限制见模型规格第 2、9 章。

```text
kernel 描述 / 编译器命令轨迹
             |
       trace driver（测试环境）
             |
       四路寄存器命令接口
             |
  WorkerBee timing 模型（单执行上下文）
       |               |
    六路 DMA         status / IRQ / stats
       |
  端口适配 + 明确的存储/互连环境
```

系统 scheduler、外部数据搬运、向量算子、CPU 与 DRAM 属于组合环境，不能伪装成 GEMM 内部能力。本文不修改或验证其他独立硬件模块。

## 2. 目录结构

P0 已创建顶层绑定、六端口 idle 骨架、默认 profile、基线/发射/构建工具及 smoke 入口；下列后续阶段文件仍为实施目标，不提前放空壳。当前不能提交命令或执行 GEMM。

```text
src/workerbee/gem5/
├── plan.md                         # 实施规划
├── spec.md                         # 模型行为、输入输出及验收契约
├── model/
│   ├── SConscript                  # 外部 gem5 模块构建入口
│   ├── WorkerBee.py                # Python SimObject 声明
│   ├── workerbee.hh
│   ├── workerbee.cc                # 顶层 ClockedObject、时钟推进、端口与统计
│   ├── types.hh                    # 命令、响应、格式、窗口/行 token
│   ├── regfile.hh
│   ├── regfile.cc                  # 四路共享 bank、仲裁、响应、IRQ
│   ├── geometry.hh
│   ├── geometry.cc                 # 配置检查与地址/执行几何
│   ├── executor.hh
│   ├── executor.cc                 # 执行 FSM 与波前/回灌协调
│   ├── dma.hh
│   ├── dma.cc                      # 读/写 DMA 状态与 gem5 请求适配
│   ├── datapath.hh
│   ├── datapath.cc                 # FIFO、有效 token 流水及单行输出转换
│   └── test_support/
│       ├── SConscript
│       ├── TestDevices.py
│       ├── trace_driver.hh
│       ├── trace_driver.cc         # 命令与响应反压驱动，不属于 DUT
│       ├── timing_memory.hh
│       └── timing_memory.cc        # 确定性内存、延迟/反压/错误注入
├── configs/
│   ├── run.py                      # 唯一运行入口，拼装 DUT 与测试环境
│   ├── hardware.py                 # 已验证硬件配置清单
│   └── memory.py                   # 确定性内存、local SRAM、互连/DRAM 配置
├── tools/
│   ├── baseline.py                 # P0 source/RTL 清单与 SHA256
│   ├── build.py                    # P0 隔离 gem5 构建与二进制来源记录
│   ├── elaborate.py                # P0 安全发射与默认 profile 对账
│   ├── EmitP0.scala                # P0 独立 WorkerBeeConfig() RTL 发射助手
│   ├── timing_evidence.py          # P0 结构与 API 源码定位；不是边沿校准
│   ├── calibrate.py                # P0 RTL 单测、逐拍采样与溯源检查
│   ├── trace.py                    # 后续 kernel 描述转合法命令轨迹
│   ├── compare.py                  # 后续 RTL/gem5 事件归一化与差异定位
│   └── report.py                   # 后续统计归一化、配置扫描汇总
└── tests/
    ├── test_baseline.py            # P0 哈希、来源隔离、CLI 错误
    ├── test_p0.py                  # P0 profile、过期基线、范围守卫
    ├── test_calibrate.py           # 单测结果、RTL/来源完整性与验收守卫
    ├── RtlEdgeSuite.scala          # 统一采样、只读测试与双线程编译设置
    ├── WorkerBeeRtlStorageSpec.scala # FIFO、CalcConfig、cvt 单测
    ├── WorkerBeeRtlArraySpec.scala # 波前、skew/deskew、executor 窗口
    ├── WorkerBeeRtlDmaSpec.scala   # 读 credit、gather、stretch 单测
    ├── cases.json                  # 手写形状、地址、格式与故障用例描述
    ├── test_contract.py            # 寄存器/IRQ/四路握手
    ├── test_dma.py                 # 地址、gather/stretch、credit、writeback
    ├── test_timing.py              # FIFO、流水、背压、RTL 对拍
    └── test_workload.py            # trace 转换、重复执行、性能报告
```

### 2.1 仓库与外部环境边界

- 不把完整 gem5 源码、Linux 镜像、PDK、权重或第三方工具复制进本目录。
- gem5 源码/构建放在独立工具工作区，通过外部模块构建机制接入 `model/`；在 P0 核实固定 gem5 版本的 `EXTRAS`/SConscript 行为。
- 不修改 gem5 上游源码作为初始集成方式，不修改本仓库 SBT、RTL 发射入口或现有配置文件。
- 波形、临时 trace、RTL 对拍结果及 gem5 运行产物放在仓库根 `sim/workerbee/gem5/<run-id>/`；全部可重新生成，不作为持久金锚。
- `tests/cases.json` 仅保存测试输入与语义断言，不保存从 RTL 拟合得到的 golden 周期表。对拍时即时运行同一输入的 RTL 与 gem5。
- 不恢复历史删除的锚文件；需要长期保存报告时另由用户指定归档位置。

### 2.2 命名隔离

`dma.hh/.cc`、`types.hh` 等文件名只描述模块内职责，不注册全局类型。所有 C++ 类型使用 `gem5::workerbee`；头文件统一限定为 `workerbee/gem5/model/...`，不用裸 `dma.hh`。外部 SConscript 添加本仓库 `src/` 到包含路径，`EXTRAS` 指向本模块 `model/`。Python 全局 SimObject 类型为 `WorkerBeeGemm`，绑定 `gem5::workerbee::WorkerBee`；类型重名和头文件路径冲突仍须显式检查，不能只依赖命名空间。

## 3. 文件结构与职责

### 3.1 模型主体

| 文件 | 核心内容 | 明确不负责 |
|---|---|---|
| `WorkerBee.py` | C++ 类绑定、GEMM clock domain、真实硬件配置及 DMA 端口 | 寄存器执行语义、网络调度 |
| `workerbee.hh/.cc` | 持有子状态；拍前快照/组合评估/拍后提交；端口绑定、生命周期、stats、debug 输出 | 用总延迟公式直接完成命令 |
| `types.hh` | 与硬件同编码的 DataFormat、op、command/response；`nt/kt/m` token；硬件状态与仿真元数据分离 | 新硬件指令或寄存器 |
| `regfile.hh/.cc` | bank、4 slots/pending、lane 仲裁、START、读快照、RAW/MASK/CLEAR/ERROR_CODE | GEMM 几何、内存调度 |
| `geometry.hh/.cc` | M/N/K 检查；格式族、block、stride、地址、beat 数；CalcConfig 阶段 | 自动替硬件补齐 N 或拆分 M |
| `executor.hh/.cc` | 五状态执行 FSM；B/ScaleB 预蓄；A/ScaleA 锁步；回灌与写门；完成 | 外部 kernel 调度、擅自跨命令保留权重 |
| `dma.hh/.cc` | 自主扫描 FSM；gather/stretch；credit/在途记账；ScaleA owner；包、retry、响应顺序适配 | 缓存/DRAM 延迟预测、数值矩阵计算 |
| `datapath.hh/.cc` | A/B/C/scale FIFO；按实际 enable 推进的有效 token；C回灌；单行 cvt 保持及ScaleC FIFO | 每 PE 的浮点或整数算术 |

这些 C++ 状态类由一个 DUT 时钟对象统一推进，不把每个内部子模块注册成独立 SimObject。测试环境与外部存储才是独立 SimObject。初始不创建通用插件框架或多套等价执行引擎。

### 3.2 测试环境与工具

| 文件 | 输入 | 输出与职责 |
|---|---|---|
| `trace_driver.*` | 原始命令批次、响应 ready 时间表、任务标记 | 持稳输入直到 fire；匹配 ACK；提供外部理想提交器，明确不代表 CPU/ISA |
| `timing_memory.*` | 延迟、ready/服务窗口、响应顺序和故障注入描述 | 可复现的内存响应；支持源码所需同拍/后续拍边界验证 |
| `hardware.py` | 经同配置 RTL 验证的参数 | 唯一命名 profile，派生参数不能重复配置 |
| `memory.py` | 存储容量、端口宽度/共享关系、延迟、频率 | 明确的系统组合，不把六口都默认为独享无限带宽 |
| `run.py` | hardware profile、内存环境、trace、seed、输出目录 | 实例化、运行、生成 manifest 与结果；拒绝不受支持运行域 |
| `trace.py` | GEMM kernel 形状、逻辑 N、格式、地址、block/epilogue | M拆命令、N/K合法补齐、B布局/打包描述与真实寄存器操作；不包含模型时间表 |
| `compare.py` | 同输入的 RTL 与 gem5 事件 | 首个差异的 cycle/port/nt/kt/m；精确流量和状态检查 |
| `report.py` | manifest、原始 stats、任务边界 | 周期、流量、利用率、stall、宿主速度和误差；不提供“校正系数” |

只在形成真实调用边界时继续拆文件；以上职责不要求一个函数对应一个文件。

## 4. 实现步骤与阶段门槛

### P0：冻结基线和建模证据

1. 审阅本规划与模型规格，确认第一版只做 timing-only。
2. 固定 gem5 release/commit、Linux 构建环境、编译器和 Verilator 版本。
3. 固定 WorkerBee 默认编译配置，重新发射对应 RTL；记录源文件与发射产物 hash，不能只凭历史 verilog 目录名选择基线。
4. 建立 hardware profile 的结构时序表：CalcConfig 阶段、skew/deskew、valid 流水、FIFO透传行为、DMA FSM、参数取数及 cvt 释放点。每项记录源码符号、推导规则和测试证据。
5. 处理模型规格第 13 章的规格/源码歧义；不擅自修改硬件规格或 RTL。
6. 确认 byte-enable、响应顺序、retry 和同拍事件的 gem5 接入方式；先建外部模块 smoke 构建。

**交付/验收**：可复现基线 manifest、结构时序表、可加载的 SimObject、无未裁定的关键时序歧义。未通过不得冻结“周期准确”口径。

### P1：命令、配置和统计骨架

1. 实现命令、格式、寄存器及 runtime 几何类型。
2. 完整实现四路拍前检查、读快照、逐地址仲裁、响应 pending 和 START 时序。
3. 实现 IRQ 与 ERROR_CODE，区分接口错误、配置错误和 DMA 错误。
4. 实现逐拍状态提交、事件日志与 trace driver。
5. 移植既有四路接口用例意图，不把 Python 接口调用次数当作硬件周期。

**验收**：四路接口、重复执行及负向用例的外部可见事件逐拍一致；读/write ACK与完成事件严格分离。

### P2：INT8 基础 GEMM 时序闭环

1. 实现 A/B/C FIFO和读 DMA 自主扫描；先对齐地址。
2. 实现 Executor、权重装载、token 波前和多 K pass 回灌。
3. 实现 C 单行转换保持、多拍写回和写响应排空。
4. 接入确定性内存，跑 `M=1/48、N=16/32、K=32/64` 等基本组合。
5. 对比 A/B消费拍、FIFO占用、状态迁移、C写事务与 done；不能只比较最终周期。

**验收**：INT8 mode0/mode1，单/多 N tile与单/多 K pass全部精确对拍；物理读写流量无误差，完成不早于写响应排空。

### P3：低位宽、MX/FP8、参数与非对齐

1. 加入 INT4/MXFP4、MXFP8、FP8和正向 INT/MX W4A8 的几何及时序。
2. 实现非对齐 gather 的排空、两读两响应、合并及 credit。
3. 实现 stretch 每逻辑半片一物理读；禁止未经 RTL 依据的相邻半片缓存。
4. 实现 ScaleA/ScaleB FIFO与消费锁步、ScaleA/Bias/RqM owner与串行参数阶段。
5. 实现不同 C 位宽、MX量化单行保持及独立 ScaleC 写通道。
6. 测试 request/response 背压、存储错误和不受支持集成条件。

**验收**：模型规格第 12 章矩阵覆盖；默认配置全部合法格式的接口时序/流量通过；ScaleC越界不输出有效性能结论。

### P4：kernel 性能探测与存储组合

1. 实现 kernel→命令转换，对 M拆分、N/K补齐、重复权重装载与格式约束显式记账。
2. 构建 local SRAM 环境；定义端口共享/仲裁关系，再接互连与 DRAM。
3. 扫描 M/N/K、格式、对齐、存储延迟/带宽、SRAM容量和并行实例数。
4. 报告每 task/kernel 的周期、真实事务/字节、stall、计算拍占比和宿主速度。
5. 多实例测试共享资源竞争；GEMM内部FIFO不冒充系统local SRAM容量。

**验收**：能指出瓶颈而非只输出 TOPS；与同条件 RTL 的精确校准集合保持一致；实际 DRAM结果标明存储假设，不能声称已与硅片校准。

### P5：事件压缩与网络性能轨迹（后续）

1. 在 P1–P4 验证通过后优化事件推进；没有证据前不跳拍。
2. 先接单层、多层命令轨迹，再接一次 YOLO 推理或语言模型 prefill/decode 区间。
3. 对非 GEMM 算子、外部数据搬运和调度给出独立模型或明确成本输入。
4. 验证压缩前后关键事件、物理事务、占用与全部统计逐项一致。

**验收**：全网性能轨迹闭环；没有真实数值计算时报告必须称“性能轨迹仿真”，不能称“推理结果正确”。宿主运行速度通过实测评估，不预设实时或固定加速倍数。

### 后续独立立项，不属于第一版

- 整数 bit-exact功能模型，随后再评估MX lazy归约的数值模型。
- 最终 NPU ISA译码、指令依赖和scheduler；仍产生本模型已有命令。
- CPU SE/Full System、MMIO/指令桥、Linux驱动、实际runtime。
- RTL/gem5在线联仿；第一版采用相同测试条件的离线事件对比。
- 执行中checkpoint/恢复、执行中reset撤销或详细功耗模型。

## 5. RTL 校准方法

1. 由测试描述生成合法输入及一个存储服务计划，不由 RTL 输出倒推预期几何。
2. RTL testbench与gem5确定性内存按相同接受/响应边界运行。ready计划和响应排队规则必须相同，只有“平均延迟相同”不够。
3. 在时钟边沿统一采样：命令fire、响应fire、DMA请求/响应、A/B消费、FIFO占用、cvt握手、状态、IRQ及done。
4. 比较首个差异，而不是用总周期相近掩盖内部误差。
5. 将差异归类为模型缺陷、存储适配差异、规格歧义或未支持输入；修正机制，不给不同shape加拟合补偿。
6. 更新硬件版本/配置后重新校准；同配置对拍结果不能外推到未经验证配置。

P0 边沿单测独立放在 `gem5/tests/`，仅通过测试 wrapper 和只读 probe 观测实际子模块；不修改设计 RTL、原有 `scala/test` 或 SBT 配置。运行时临时选择 WorkerBee 与 utils 源集，三个 suite 串行运行，Verilator 编译并发固定为 2。采样发生在输入稳定后的上升沿前；日志中如包含 reset 拍则显式记录 reset。

这些是子模块时序测试，不是完整 GEMM 对拍；deskew 检查独立的 enable/delay 递推，但使用观测到的原始列数据，不提供 MAC 数值金锚。通过全部 8 项单测后仍保持 `edge_calibrated=false`、`p0_accepted=false`，直到顶层写响应排空、参数 owner、ScaleC 服务域、gem5 协议及关键歧义均完成验证。

## 6. 关键风险与工作量

| 风险 | 控制方式 |
|---|---|
| 规格描述比实际结构粗 | P0逐项冻结结构时序和歧义，不靠文字猜延迟 |
| gem5响应乱序/同tick回调与RTL同序时钟接口不同 | 显式端口适配，记录缓冲容量与额外延迟 |
| C写在途计数不是可配置发射credit | 按源码计数和合法运行域建模，不补限流器 |
| ScaleC现有限制不允许随意直连高延迟DRAM | 先用符合限制的local SRAM；缓冲系统另行定义 |
| timing-only数据被误当真实推理输出 | manifest强制标记data_valid=false，禁止数值/数据相关执行 |
| 六路端口误设独享无限带宽 | 环境显式定义连接、共享与仲裁 |
| 只看总周期而遗漏并行与资源错误 | 逐事务、逐占用、逐状态对拍 |

单人熟悉工程、默认配置、Linux工具链可用情况下，P0–P2约2–4人周，P3–P4再约2–4人周；合计4–8人周是范围估计，不是交付承诺。P5、数值模型、ISA与Full System另估，不纳入此范围。

## 7. P0 当前范围与复现

已实现：唯一 SimObject 绑定、`ClockedObject` idle 生命周期、六个命名请求端口、固定默认 profile、默认配置安全 RTL 发射、来源/产物 SHA256、结构时序源码定位、隔离构建与 idle smoke。P0 不发 DMA 请求；意外响应/retry 直接报错，不静默吞包。smoke 六口共享一个 SystemXBar 和 SimpleMemory，仅验证连接与实例化，不验证存储性能。

固定 gem5 v25.1.0.1 / `c8222cc67a399bfc01e8658dd14b30d5bfd634f9`。本机开发环境为 macOS arm64 / Apple clang 17 / Python 3.14.6 / SCons 4.10.1 / Verilator 5.050 / SBT 1.10.1 / Chisel 7.13.0；Linux 基线尚未验证。现有 WorkerBeeElaborate 使用非默认配置且清理既有输出，因此不得用它代替这里的独立助手。

从仓库根运行；工具可用绝对路径代替示例中的命令名，`RUN` 必须是新的临时运行目录。

```sh
REPO="$PWD"
GEM5_ROOT="/path/to/gem5"
RUN="$REPO/sim/workerbee/gem5/p0-new-run"
mkdir -p "$RUN"
python3 -B src/workerbee/gem5/tools/build.py --gem5-root "$GEM5_ROOT" --scons scons
python3 -B src/workerbee/gem5/tools/elaborate.py --sbt sbt --output-dir "$RUN/rtl"
"$GEM5_ROOT/build/WORKERBEE_P0/gem5.opt" --outdir="$RUN/smoke" \
  src/workerbee/gem5/configs/run.py --gem5-root "$GEM5_ROOT" --rtl-dir "$RUN/rtl"
python3 -B -m unittest discover -s src/workerbee/gem5/tests -p 'test_*.py'
```

发射目录必须不存在；构建只用新建 `WORKERBEE_P0` variant，不覆盖既有 RISCV/X86 二进制；所有步骤不得修改源文件来绕过检查。RTL 目录保存 `hardware.json`、`baseline.json` 与日志；smoke 保存 `manifest.json`、`result.json` 和 gem5 原生统计。硬件源码或 RTL 改变后必须重新发射；模型 C++/绑定改变后必须重新构建。

### 7.1 RTL 边沿校准进度（2026-10-28）

- 已实现 3 个 suite、8 项默认配置子模块测试及统一拍前 JSONL 采样；设计 RTL 与原有测试未修改。
- Python 工具回归 50 项通过，包括 Scala 测试来源清单、基线 hash、逐用例采样覆盖/单调周期、RTL/XML 完整性与单测不得冒充 P0 验收的守卫。
- 首轮 `sim/workerbee/gem5/rtl-edges-20261028-storage/` 中 FIFO、CalcConfig 通过，cvt 启动失败；它不是完整通过的校准报告。
- 完整集合 `sim/workerbee/gem5/rtl-edges-20261028-units/` 的 8 个设计均通过编译/发射并生成仿真程序，实际 Makefile 为 `-j 2`；8 项均因缺少 `simulation-log.txt` 失败，无测试体采样，不记为时序通过。
- `startup-diagnostics.json` 保存宿主启动证据及关联日志/XML/调用栈 hash。同一程序由 Python 直接启动可完成 ready/done 协议；Java ProcessBuilder 在 JDK 17/26 下均阻塞，采样停在 `_dyld_start`。系统日志记录生成程序的 AMFI `Unrecoverable CT signature issue`；当前探测不支持 swap 耗尽归因。没有修改签名或系统安全设置。
- 宿主启动问题解决后，先重跑 cvt/FIFO/CalcConfig，再运行 array/executor 和 DMA，最后在源码稳定且全新的目录执行完整集合。只有真实测试通过才生成校准报告；本次没有 `calibration.json`。

```sh
python3 -B src/workerbee/gem5/tools/calibrate.py \
  --rtl-dir "$RUN/rtl" \
  --output-dir "$REPO/sim/workerbee/gem5/rtl-edges-new-run"
python3 -B src/workerbee/gem5/tools/calibrate.py \
  --output-dir "$REPO/sim/workerbee/gem5/rtl-edges-new-run" --verify-only
```

`--suite` 可重复指定上述单个 suite；输出目录须不存在。成功报告同时检查当前 hardware/model 来源、默认 RTL 基线、完整产物清单及 XML 用例结果。采样工具与 Scala 单测均计入 model source hash；任何来源变更后旧报告失效，不能沿用失败轮次或历史两项通过作为当前全量验收。

**未通过的门槛**：完整 RTL 单测、顶层 GEMM/写响应排空、ScaleA 参数 owner、C-write 计数合法域、ScaleC 服务域、G5-U01–U06、gem5 协议及 Linux 构建验收。`edge_calibrated=false`、`p0_accepted=false`、`performance_valid=false`、`data_valid=false` 保持不变，不能以单测或 idle smoke 声称 GEMM 已实现或周期准确。
