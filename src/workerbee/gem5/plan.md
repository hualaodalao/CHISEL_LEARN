# WorkerBee gem5 模型实施规划

版本：v0.2.1（L1 已实现）  
日期：2026-10-30  
状态：P0 idle 骨架、基线工具和 8 项 RTL 边沿单测通过；L1 粗粒度 GEMM timing 模型已实现并通过 INT8 与 MXFP8 形状扫描。RTL 逐拍对拍（L2）未开始，`edge_calibrated=false`、`performance_valid=false`
目标：先以粗粒度 GEMM timing 开展 kernel 和网络性能探测，再按需推进 RTL 逐拍校准。

本文定义目录、文件职责和实施顺序；行为与验收契约见 [模型规格](spec.md)。硬件契约引用 [WorkerBee 硬件规格](../doc/spec.md)，不在这里另建硬件寄存器或指令编码。

## 1. 实施范围与路线

采用 gem5 `ClockedObject` + C++ 时序状态模型 + 六路内存请求端口。不引入 CPU、Linux、MMIO 或尚未定义的 NPU ISA，使用 trace driver 驱动现有四路命令接口。

路线分两步，当前只承诺第一步：

1. **L1 粗粒度（已实现）**：保留 GEMM 的结构性时间——N→K 窗口顺序、每拍一个 A beat 与一个 B beat、B 预蓄、多 K pass 回灌、31 拍阵列流水延迟、C 写回排空、DMA 真实地址与字节数、gather 与 outstanding 限制——但不复现逐 PE 波前、skew/deskew 与输出转换内部拍级细节。它不是固定延迟公式：窗口障碍与 DMA 供数停顿由结构决定。
2. **L2 精确路线（后续）**：逐拍复现 FIFO、DMA credit、权重装载、波前、回灌与输出转换资源关系，并与同配置 RTL 对拍。在 L2 完成前，任何结果都不得称为周期准确。

L1 输出统一标记 `model_kind=timing_loose`、`phase=L1`、`data_valid=false`、`performance_valid=false`、`p0_accepted=false`。它不替代第 5 章的 RTL 校准，也不能作为 L2 验收证据。

第一版为 **timing-only**：支持当前默认输入格式与合法输出格式对应的时序、地址、流量及中断，不执行完整矩阵数值计算。具体限制见模型规格第 2、9、14 章。

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

L1 已创建的文件如下；标注「L2」的条目是精确路线的实施目标，不提前放空壳。

```text
src/workerbee/gem5/
├── plan.md                         # 实施规划
├── spec.md                         # 模型行为、输入输出及验收契约
├── model/
│   ├── SConscript                  # 外部 gem5 模块构建入口
│   ├── WorkerBee.py                # Python SimObject 声明（L1 参数）
│   ├── workerbee.hh
│   ├── workerbee.cc                # 顶层 ClockedObject、拍推进、端口、trace 装载、JSON 结果
│   ├── types.hh                    # 命令、trace batch、格式枚举
│   ├── regfile.hh/.cc              # 四 lane 接收、寄存器、IRQ/ERROR_CODE、启动快照
│   ├── geometry.hh/.cc             # 配置检查与地址/执行几何
│   ├── stream.hh/.cc               # 读/写 DMA 流：credit、gather、retry、行写
│   ├── executor.hh/.cc             # 五状态窗口 FSM：B 预蓄 → A 消费 → 下一 tile → 排空
│   └── test_support/               # L2：命令驱动、确定性内存与错误注入
├── configs/
│   ├── run.py                      # P0 idle smoke（不执行 GEMM）
│   ├── gemm.py                     # L1 单任务 GEMM 入口：私有 bank 或共享总线
│   ├── hardware.py                 # 已验证硬件配置清单
│   └── memory.py                   # L2：存储/互连组合与仲裁
├── tools/
│   ├── baseline.py                 # source/RTL 清单与 SHA256
│   ├── build.py                    # 隔离 gem5 构建与二进制来源记录
│   ├── elaborate.py                # 安全发射与默认 profile 对账
│   ├── EmitP0.scala                # 独立 WorkerBeeConfig() RTL 发射助手
│   ├── timing_evidence.py          # 结构与 API 源码定位；不是边沿校准
│   ├── calibrate.py                # RTL 单测、逐拍采样与溯源检查
│   ├── command_trace.py            # 形状/格式 → 四 lane 命令轨迹、地址布局、JSONL
│   ├── scan.py                     # 形状扫描驱动与 scan.json/scan.md 汇总
│   ├── compare.py                  # L2：RTL/gem5 事件归一化与差异定位
│   └── report.py                   # L2：统计归一化与配置扫描汇总
└── tests/
    ├── test_baseline.py            # 哈希、来源隔离、CLI 错误
    ├── test_p0.py                  # profile、过期基线、范围守卫
    ├── test_calibrate.py           # 单测结果、RTL/来源完整性与验收守卫
    ├── test_command_trace.py       # 命令字、几何、布局、打包
    ├── test_scan.py                # 扫描参数、结构估计、汇总表格
    ├── RtlEdgeSuite.scala          # 统一采样、只读测试与双线程编译设置
    ├── WorkerBeeRtlStorageSpec.scala # FIFO、CalcConfig、cvt 单测
    ├── WorkerBeeRtlArraySpec.scala # 波前、skew/deskew、executor 窗口
    └── WorkerBeeRtlDmaSpec.scala   # 读 credit、gather、stretch 单测
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
| `workerbee.hh/.cc` | 持有子状态；逐拍推进；端口绑定、生命周期、trace 装载、stats、JSON 结果 | 用总延迟公式直接完成命令 |
| `types.hh` | 与硬件同编码的 DataFormat、op、command；trace batch | 新硬件指令或寄存器 |
| `regfile.hh/.cc` | 四 lane 接收、寄存器、START、读快照、RAW/MASK/CLEAR/ERROR_CODE、DMA 错误锁存 | GEMM 几何、内存调度 |
| `geometry.hh/.cc` | M/N/K 检查；格式族、block、stride、地址、beat 数 | 自动替硬件补齐 N 或拆分 M |
| `executor.hh/.cc` | 五状态窗口 FSM；B 预蓄；A 消费；回灌与写门；C 行延迟排队与排空 | 外部 kernel 调度、擅自跨命令保留权重 |
| `stream.hh/.cc` | 读/写 DMA 流：占用与 outstanding、gather、retry、行写字节选通 | 缓存/DRAM 延迟预测、数值矩阵计算 |

L1 把 FIFO、地址生成和端口适配合并在这些状态类里；L2 若需要独立的 FIFO/cvt 状态，再按实际调用边界拆分，不预先建空壳。

这些 C++ 状态类由一个 DUT 时钟对象统一推进，不把每个内部子模块注册成独立 SimObject。测试环境与外部存储才是独立 SimObject。初始不创建通用插件框架或多套等价执行引擎。

### 3.2 测试环境与工具

| 文件 | 输入 | 输出与职责 |
|---|---|---|
| `trace_driver.*` | L2：原始命令批次、响应 ready 时间表、任务标记 | 持稳输入直到 fire；匹配 ACK；外部理想提交器，不代表 CPU/ISA |
| `timing_memory.*` | L2：延迟、ready/服务窗口、响应顺序和故障注入描述 | 可复现的内存响应；支持同拍/后续拍边界验证 |
| `hardware.py` | 经同配置 RTL 验证的参数 | 唯一命名 profile，派生参数不能重复配置 |
| `memory.py` | L2：存储容量、端口宽度/共享关系、延迟、频率 | 明确的系统组合，不把六口都默认为独享无限带宽 |
| `gemm.py` | 形状/格式/block、时钟、存储与模型旋钮 | 生成命令轨迹、实例化 WorkerBee 与六个存储 bank、写 manifest/result |
| `command_trace.py` | GEMM 形状、逻辑 N、格式、地址与 block/epilogue | 四 lane 命令轨迹、地址布局、JSONL；不含模型时间表 |
| `scan.py` | 形状网格与运行参数 | 逐形状运行 gem5 并汇总 scan.json/scan.md |
| `compare.py` | L2：同输入的 RTL 与 gem5 事件 | 首个差异的 cycle/port/nt/kt/m；精确流量和状态检查 |
| `report.py` | L2：manifest、原始 stats、任务边界 | 周期、流量、利用率、stall、宿主速度和误差；不提供“校正系数” |

只在形成真实调用边界时继续拆文件；以上职责不要求一个函数对应一个文件。

## 4. 实现步骤与阶段门槛

P0、P1 已完成并被 L1 复用；P2 起的逐拍对拍属于 L2 路线，未开始。L1 的已完成范围见第 8 章。

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

### 7.1 RTL 边沿校准进度（2026-10-30）

- 已实现 3 个 suite、8 项默认配置子模块测试及统一拍前 JSONL 采样；设计 RTL 与原有测试未修改。
- Python 工具回归 54 项通过，包括 Scala 测试来源清单、基线 hash、逐用例采样覆盖/单调周期、RTL/XML 完整性与单测不得冒充 P0 验收的守卫。
- 首轮 `sim/workerbee/gem5/rtl-edges-20261028-storage/` 中 FIFO、CalcConfig 通过，cvt 启动失败；它不是完整通过的校准报告。
- 硬件升到规格 v4.1.0（BF16 默认输入）后，`p0-20261027` 的 RTL 基线已与源码不符，`hardware.py`/`EmitP0.scala` 同步补上 BF16；`sim/workerbee/gem5/rtl-baseline-20261030/` 按当前源码重新发射，派生参数未变。
- 完整集合 `sim/workerbee/gem5/rtl-edges-20261030/` 8 项全部通过（单次 sbt 运行即全绿，约 1 分钟），`calibration.json` 与 `--verify-only` 复核均通过。
- 之前的 `rtl-edges-20261028-units/` 8 项失败源于宿主启动拦截：新链接的仿真程序首次执行时由企业 EDR 做云端信誉判定，超过约 10 s 窗口即被 SIGKILL，进程未到 main，svsim 因缺少 `simulation-log.txt` 把该失败伪装成文件缺失。签名本身有效（`codesign --verify --strict` 通过、重新签名仍被杀）；swap 与签名有效性均已排除。诊断与证据在 `startup-diagnostics.json`，未修改任何签名或系统安全设置。
- 该宿主问题由工具自行消化：`calibrate.py` 检测到启动签名后，先对每个已编译仿真程序做一次 `D` 预热（最多 4 次），再在同一目录重跑 sbt，最多 3 轮；重跑验证过即使 Verilator 重链接也会通过，因此恢复靠重试而非二进制复用。
- 仍不能据此声称周期准确：本报告只有子模块边沿，`edge_calibrated=false`，顶层 GEMM/写响应排空、ScaleA owner、C 写域、ScaleC 服务域与 gem5 协议适配仍未验证。

```sh
python3 -B src/workerbee/gem5/tools/calibrate.py \
  --rtl-dir "$RUN/rtl" \
  --output-dir "$REPO/sim/workerbee/gem5/rtl-edges-new-run"
python3 -B src/workerbee/gem5/tools/calibrate.py \
  --output-dir "$REPO/sim/workerbee/gem5/rtl-edges-new-run" --verify-only
```

`--suite` 可重复指定上述单个 suite；输出目录须不存在。成功报告同时检查当前 hardware/model 来源、默认 RTL 基线、完整产物清单及 XML 用例结果。采样工具与 Scala 单测均计入 model source hash；任何来源变更后旧报告失效，不能沿用失败轮次或历史两项通过作为当前全量验收。

**未通过的门槛**：顶层 GEMM/写响应排空、ScaleA 参数 owner、C-write 计数合法域、ScaleC 服务域、G5-U01–U06、gem5 协议及 Linux 构建验收。8 项子模块边沿单测通过不等于上述门槛通过，`edge_calibrated=false`、`p0_accepted=false`、`performance_valid=false`、`data_valid=false` 保持不变，不能以单测或 idle smoke 声称 GEMM 已实现或周期准确。

## 8. L1 粗粒度模型（2026-10-30）

### 8.1 时间语义

- 一拍消费一个 A beat（M 行之一）与一个 B beat（T 行之一）；窗口顺序 N tile → K pass → (T 个 B beat, M 个 A beat)，与 RTL executor 的状态顺序一致。
- B 侧先预蓄满 T 个 beat 才进入窗口；窗口内 B 供不上时计 stall，不提前推进。
- 每个输出行在其末 K pass 的 A beat 之后排队，`array_pipeline_latency`（默认 31）拍后进入 C 写通道；通道每拍最多一个 beat。
- 峰值口径：阵列 16×16 PE、每 lane 每拍打包 `16/elemBits` 个元素，故峰值 = `T × kPerBeat` MAC/cycle（INT8=512、BF16=256、INT4=1024）。利用率 = 实测 MAC/cycle ÷ 该峰值。
- 不建模：逐 PE 波前、skew/deskew、FIFO 透传、cvt 内部保持、多实例竞争。

### 8.2 存储环境

默认六个 DMA 端口各接一个 `SimpleMemory` bank，地址区间互不相交（`command_trace.banked_layout`：A→0、B→1、C→2、ScaleA/bias/rqM→3、ScaleB→4、ScaleC→5）。这样拍数只反映 GEMM 结构与 DMA 流控，不掺入互连排队。`--shared-bus` 切回六口共享一个 `SystemXBar` 的竞争实验：同一 16×16×64 INT8 任务从 141 拍升到 778 拍，其中 CoherentXBar 每读往返约 10 个 DUT 拍、发射被限到每 2 拍一个请求，属互连而非 DUT 行为，故不作为默认口径。

### 8.3 首批结果（500 MHz，1 ns 私有 bank，`--array-latency 31`）

`sim/workerbee/gem5/scan-20261030/`：INT8 18 个形状（M∈{16,32,48}、N∈{16,64,128}、K∈{64,256}）。整表 18/18 成功，详细表格见 `scan.md`。

| 指标 | 观察 |
|---|---|
| 16×16×64 | 141 拍，结构性估计 127 拍，利用率 0.227 |
| 48×128×256 | 4383 拍，利用率 0.701（本批最高） |
| 趋势 | M 增大时利用率单调上升：每窗口固定付出 T 个 B beat，M 越大摊薄越彻底（N=128、K=256 时 M=16/32/48 分别为 0.445/0.613/0.701） |
| 流量 | A/C 请求与字节数等于理论值；B 在 INT8、N=16 时因行步进 16 B 小于 32 B 拍宽，一半 beat 触发 gather，物理字节为 1.5× |

以上数字只描述结构与流控。它们未与 RTL 对拍，不能用于性能承诺；`model_kind=timing_loose`、`data_valid=false`、`performance_valid=false`。

`sim/workerbee/gem5/scan-20261030-mx8/`：MXFP8（A/B/C 均 MXE4M3、`loadW=1`、blkA=blkB=32、blkC=16）8 个形状（M∈{16,32}、N∈{16,64}、K∈{64,128}），8/8 成功。

| 指标 | 观察 |
|---|---|
| 16×16×64 | 125 拍，结构性估计 111 拍，利用率 0.256 |
| 32×64×128 | 936 拍，利用率 0.547（本批最高） |
| 与 INT8 对比 | 同形状下 MX8 每窗口的 B beat 数不变（`kPerBeat` 相同），但 scale 读把 4 B 拍拆成两次非对齐读，`dma_bytes_per_cycle` 升至 24–38；峰值同为 `16 × 32 = 512` MAC/cycle |
| 缩放读流量 | ScaleA/ScaleB 每个逻辑 beat 的地址步进为 1 B，只有 1/4 落在 4 B 拍边界，故 32 个 beat 产生 56 次物理请求 |

MX8 首轮曾整批 20M 拍超时：最后一个逻辑 scale beat 非对齐时，gather 的 lo 读已把 `produced_` 推到 `count_`，`ReadStream::tick()` 先判 `produced_ == count_` 提前返回，hi 读永不补发，beat 永久滞留 `reserved_`，第二个 K 窗口的 `scaleB` 占用停在 15 < T。现已把 gather 分支移到该判断之前，并让 hi 读遵守 outstanding 上限；修复后两组扫描全部通过，模型输出本身未改变口径。

### 8.4 复现

```sh
GEM5="/path/to/gem5/build/WORKERBEE_P0/gem5.opt"
RUN="$REPO/sim/workerbee/gem5/l1-run"
"$GEM5" -d "$RUN" src/workerbee/gem5/configs/gemm.py --shape 16x16x64
"$GEM5" -d "$RUN-mx8" src/workerbee/gem5/configs/gemm.py --shape 16x16x64 \
  --a-fmt MXE4M3 --b-fmt MXE4M3 --c-fmt MXE4M3 --load-w 1 \
  --blk-a 32 --blk-b 32 --blk-c 16
python3 -B src/workerbee/gem5/tools/scan.py --gem5 "$GEM5" \
  --output "$REPO/sim/workerbee/gem5/scan-new-run" --preset int8
python3 -B src/workerbee/gem5/tools/scan.py --gem5 "$GEM5" \
  --output "$REPO/sim/workerbee/gem5/scan-new-run-mx8" --preset mx8 \
  --a-fmt MXE4M3 --b-fmt MXE4M3 --c-fmt MXE4M3 --load-w 1 --blk-a 32 --blk-b 32 --blk-c 16
```

`gemm.py` 的输出目录必须不存在（模型会写 `model_result.json` 并退出仿真）；`scan.py` 默认删除成功形状的子目录，加 `--keep-runs` 保留。每次模型 C++/绑定变化后需重新构建 `WORKERBEE_P0`。

### 8.5 文档变更与校准记录

`baseline.py` 把 `gem5/plan.md`、`gem5/spec.md` 与模型源码一起计入 `model_source_hash`，因此本次文档与 L1 模型更新会使 `sim/workerbee/gem5/rtl-edges-20261030/calibration.json` 与 `rtl-edges-20261030-l1/` 的模型来源失效。已按当前来源重跑边沿校准到 `sim/workerbee/gem5/rtl-edges-20261030-l1-fix/`（8 项全绿，`--verify-only` 通过，仍为 `edge_calibrated=false`）；旧目录保留为历史证据，`--verify-only` 对其报 `Stale RTL calibration sources` 属预期，不能沿用其结论。

