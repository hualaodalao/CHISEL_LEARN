# Hive eval model 实施计划

2026-11-02 ｜ 首个算例 Qwen3-0.6B ｜ L0/L1 首版与离线网页已实现；161 条 unittest 回归通过；性能仍未校准

> **当前 L1 架构**见已批准的 [model_eval_l1_opt.md](model_eval_l1_opt.md)，实现 API/schema2 见 [model_eval_spec.md](model_eval_spec.md)。本文保留首版历史设计与验收设想（串行标量合成、严格跨级断言等），不作为当前接口或验证状态；文件表/旧路径已迁移到现行模块，L0/模型计量与历史记录保持原样。

**实施范围：L0 与 L1。** L2/L3 归 `src/workerbee/gem5/`，本计划只写它们是什么、L0/L1 为什么不能替代它们、以及接口与降级约定，不建目录、不排里程碑、不定验收。

---

## Context

Hive 需要回答：一个具体模型放到 G0 上端到端跑多快，瓶颈在哪个算子的哪种资源上。现在回答不了。

已有和缺的：

- `src/hive/doc/model_analysis/` 下 11 份逐层拆解（Qwen3 四档、Gemma4-E2B、Granite4.0-H-Tiny、LFM3-1.7B、MLA、YOLO11n/26n、模板），手算了参数量、每 token MAC、权重与 KV 流量。等于 L0 做过一遍，但每篇各写死一套硬件常数，没有共享 profile。
- `src/hive/model_eval/` 已包含共享根、L0 roofline、L1 命令/窗口与资源账本；两层均提供扫描 CLI 与离线交互网页。
- `src/workerbee/gem5/` 独立维护冻结硬件 profile、时序模型与 RTL 对拍。Hive eval 只读取几何与规格，不读取其时序结果或校准文件，也不以该工程的当前验收状态宣称本报告已校准。

**三个决定计划形状的发现：**

**一、口径分叉。** `model_analysis/` 全部按 **310 GB/s、25 TOPS** 算；`arch.md` 批准的 G0 是 **17.064 GB/s、1.024 TOPS**。带宽差约 18 倍。同一个 decode，310 口径 504.5 tok/s，G0 口径理论约 28 tok/s。两套都要保留成命名 profile，报告标明用哪一套；不改已有文档数字。

**二、decode 的 loadW 节奏。** 按 gem5 spec §6.1，WorkerBee 固定「连续装载 T=16 个 B 拍，再消费 M 个 A 拍」。decode 的 M=1，每窗口 17 拍只有 1 拍在算，阵列有效利用率 `M/(T+M)=1/17≈5.9%`。FLOPs 层面完全不可见。

**三、同源计账必须由数据路径决定。** 流式 B 的 DRAM 搬运、local 端口和 loadW 是同一路径上的不同约束，先按来源取 max，不直接相加。只有显式 staging/驻留使一次 DRAM 装载和后续多次本地重装成为不同阶段时，才分开计账。17.064 GB/s 高于单口 16 GB/s，但低于双核同时装载的 32 GB/s；不能写成连单核都喂不满。310 只提供带宽与声明算力，没有 SRAM/时钟/本地端口信息，不能推断驻留或资源独立，也不生成完整 L1 吞吐。

预期产出：一条命令，输入模型 config 与 profile 名，输出逐算子时间分解和首位瓶颈，数字可追溯到 profile 常数与手算锚点。

---

## 第一部分　宏观计划

### 1.1 四层是什么，各用什么建模方法

分级不是同一个模型的精度档位，是四种**建模范式**，各自回答不同问题、有各自的不可能。

| 层 | 名字 | 回答什么 | 建模范式 | 实现手段 | 看不见什么 | 本计划 |
|---|---|---|---|---|---|---|
| **L0** | 解析 roofline | 这个模型在这台机器上受带宽限制还是算力限制 | **闭式公式**。把模型压成 (总 MAC, 总字节) 两个标量，与 (峰值算力, 峰值带宽) 做比值，取 max | 几十行 Python 算术，无循环无搜索 | tile、补齐、搬运顺序、SRAM 容量、引擎切换。只给上界 | **实施** |
| **L1** | 算子/tile 代价 | 时间花在哪个算子的哪种资源上 | **资源分账的确定性累加**。按 (算子 → 命令 → 窗口) 三层枚举真实搬运与拍数，记进多条资源账，再按重叠规则合成时间线 | Python 嵌套循环 + `dataclass` 账本，确定性，无随机无事件队列 | 握手细节、FIFO 瞬时占用、gather 排空、retry。未经校准，不承诺误差倍数 | **实施** |
| **L2** | 周期近似 | 这串命令在硬件上真实多少拍 | **周期级状态机仿真**。每拍快照输入与拍前状态 → 组合求值 ready/valid/仲裁 → 一次性提交下一状态 | gem5 `ClockedObject` + C++ 状态类 + 六路 `RequestPort` | 数值。timing-only，`data_valid=false` | 不管 |
| **L3** | RTL 校准 | L2 的拍数是不是真的，L1 的结构参数能不能信 | **边沿对拍**。同一输入分别跑 RTL 与 L2，在时钟上升沿前统一采样，比较首个差异 | ChiselSim + Verilator 5.050，JSONL 逐拍采样 | 不是模型，是验收手段 | 不管 |

四层的分工一句话：**L0 给上界，L1 给归因，L2 给拍数，L3 给可信度。**

只做 L0 看不见 1/17、补齐和重装放大，所以需要 L1。L0/L1 的解析交付不依赖外部周期模型或 RTL 校准就绪；是否接入外部校准另行决定，本报告始终明确未校准和未覆盖成本。

### 1.2 L2/L3 的边界与本计划的三条约定

L2/L3 的推进、里程碑、验收全在 `src/workerbee/gem5/plan.md`，本计划不重复、不接管。只定三条约定：

1. **L1 不得把 gem5 数字当输入**，也不得因 gem5 阻塞而无法运行。
2. **结构计数、回退假设和未知成本分开。** FIFO 深度、命令上限、装载/消费拍数来自冻结几何与规格。VPU 非 FMA 向量趟数以 `source="fallback"` 标记；特殊函数和 CPU 服务仅接受显式情景参数。命令切换、prologue/epilogue、流水排空未知时进 `excluded_costs`，不填猜测值；31 拍候选不进入任何总时间字段。
3. **报告如实声明未校准。** `performance_valid=false`，头部打印「未经 RTL 校准的工程估算」。部分单测通过不构成校准。这条由 `common/manifest.py` 单点实现，任何模块不得自行声明已校准。

将来 gem5 P0 通过后，接入工作另立计划。

### 1.3 目录布局：两个层目录 + 一个共享根

```
src/hive/model_eval/
├── hw/          ← 共享：profile 常数、阵列几何（从 gem5 读，不复制）
├── frontend/    ← 共享：模型 config → ModelSpec
├── common/      ← 共享：workload point、manifest、report、opspec
├── l0/          ← 闭式 roofline 评估工程
├── l1/          ← 资源分账评估工程
└── tests/       ← 分层 unittest 锚点
```

`l0/` 与 `l1/` 并列，各放本层评估源码。L1 仅在跨层校验中调用 L0 的公开 `roofline.evaluate`，不复用 L0 内部计账作为 L1 成本。

**共享根是明写的例外。** 两层都要读同一套硬件 profile、同一份模型 shape、同一个 manifest 格式。若把 `hw/` 塞进 `l0/`，L1 就得 `from l0.hw import ...`（层间穿透）或自己抄一份几何（违反单一真源，且几何一抄就会漂）。所以把三类无层次属性的设施提到根：

| 共享目录 | 为什么不属于任何一层 | 谁消费 |
|---|---|---|
| `hw/` | profile 常数与阵列几何是硬件事实，不是某层的建模产物 | L0 L1 |
| `frontend/` | 模型图与 shape 抽取对两层是同一件事 | L0 L1 |
| `common/` | workload point / manifest / 报告骨架 / 算子描述是公共契约 | L0 L1 |

L0 与 L1 的两个规划实例独立得出了同一结论，故采纳并在此明示。

### 1.4 另外三个决策

**语言：Python 3。** L0/L1 是账本和枚举，不是热循环；gem5 侧 `tools/` 也是 Python，将来对接不跨语言。Qwen3-0.6B decode 的窗口总数量级 1e5–1e7，纯 Python 秒级到十秒级完成。

**编译器：不要，要 mapper。** 需要引擎分配、tiling、格式选择、K-split 决策、带代价的调度表。指令发射和寄存器分配不要——eval model 不执行代码。

**PyTorch：做前端，不做时延预言机。** `torch.export`/FX 给图和 shape，`FlopCounterMode` 交叉校验 MAC，前向结果给数值金锚。它不知道 T=16 补齐、512 KiB 驻留、显式 SRAM 搬运，wall time 不能用来推 Hive 性能。「直接跑 pytorch 就出结果」的上限是图与形状自动抽取、工作量自动对表；时间模型必须自己写。

### 1.5 里程碑

| 里程碑 | 内容 | 出口判据 | 依赖 |
|---|---|---|---|
| M0 | 共享根收口 | `hw/` + `frontend/` + `common/` 可用；同一算例切 G0 / 310 各出一份数字，manifest 记 profile 名与来源 hash |无 |
| M1 | `l0/` 闭式层 | 复现 310 口径 504.5 / 285.9 / 70.9 tok/s，并输出 G0 同组结果 | M0 |
| M2 | `l1/` 几何账本 | 逐算子窗口数、逻辑拍数、物理字节、padding 浪费与 L1 对账表逐项一致 | M1 |
| M3 | `l1/` 时间线 | 已计入时间归因到 DRAM / GEMM / VPU / CPU / handoff；loadW/compute 原始计数保留且同源不重复计账 | M2 |
| M4 | 报告与归因 | 指出 Qwen3-0.6B 在 G0 上的首位瓶颈及数值依据，含 `excluded_costs` 与未校准声明 | M3 |
| M5 | 多模型铺开 | 其余 10 份 model_analysis 跑通同一套代码并与手算对表 | M4 |

M0–M4 的 Qwen3-0.6B 单核解析首版已跑通，报告明确保留未知成本；M5 多模型铺开尚未开展。

### 1.6 并行与分工

M0 两件事（`hw/` 硬件 profile、`frontend/` 模型前端）无依赖，可并发。M2 内部三条线互不阻塞——GEMM 几何、VPU 代价、报告骨架，适合分给三个执行实例。M3 等 GEMM 几何完成。

### 1.7 贯穿全局的方法论

优先于任何单层的实现选择。

**单一真源。** 阵列几何（`arrayN`/`arrayM`/`totalN`/`cEffW`/`supportedFmts`）只从 `src/workerbee/gem5/configs/hardware.py` 的 `DEFAULT_PROFILE` 读；模型参数量只认 `Qwen3-0.6B-逐层计算拆解.md` 附录 B 脚本；硬件常数只认 `arch.md` 与 `arch_roadmap.md`。

**回归锚点先行。** 每层先把已知答案写成测试断言，再写实现。L0 已采用标准库 unittest（本机未安装 pytest），不新增必需依赖。

**禁止拟合系数。** DRAM 效率因子是命名输入，必须出现在 manifest 里，不许按 shape 调。不给不同形状加补偿表。沿用 gem5 spec §3.2。

**禁止重复计账。** 同一物理约束在不同视角下出现时取 max，不相加。loadW 拍与权重 DMA 字节是首例，`arch.md` 的 local 接口 16 GB/s 与 SRAM 128 GB/s 也是一组。

**未覆盖算子列明，不记 0。** 沿用 gem5 spec §11.6。`excluded_costs` 非空是正常状态。

**双 profile 并列。** 任何吞吐数字必须带 profile 名，G0 与 310 并排出现，不取平均、不互相覆盖。

**未校准声明单点实现。** 见 §1.2 第三条。

---

## 第二部分　两个子项目

---

## L0　`src/hive/model_eval/l0/`　闭式 roofline

### 建模方法

把整个模型压成两个标量，与硬件两个峰值做比值。全部闭式，无循环、无搜索、无状态。

```
# 工作量（每 token，decode）
MAC(S)    = L × (fixed_mac + s_term × S) + final_norm_mac + V × H
fixed_mac = 15,750,144      # 单层固定项，附录 B
s_term    = 4,112           # 单层随上下文线性项
L, V, H   = 28, 151936, 1024
final_norm_mac = 2 × H     # block 外的末尾 RMSNorm，不可漏计

# 流量（每 token，decode）
W_bytes   = uniq_params × bytes_per_param   # 596,049,920 × 1.031 = 614,527,468 (MXFP8)
KV_bytes  = kv_tok × S                      # kv_tok = 2×n_kv×d_h×2×L = 114,688 B
traffic   = W_bytes + KV_bytes

# prefill：body 与末尾 norm 覆盖 S 个 token，lm_head 只算最后一个 token
MAC_prefill(S) = S × (L × fixed_mac + final_norm_mac) + 57,344 × S² + V × H
# 沿用文档的因果 attention 主项近似；未计 softmax 的 224×S²，列入 excluded_costs

# 两条 roofline
t_bw      = traffic / (BW_peak × η)
t_mac     = MAC / MAC_rate                  # MAC_rate = mac_per_cycle × f × n_core
t         = max(t_bw, t_mac)
AI        = MAC / traffic                   # Qwen3-0.6B decode ≈ 0.971
bottleneck= "dram" if t_bw > t_mac else "compute"
```

视觉模型走第三种相位：每帧工作量固定，输出 FPS 而非 tok/s。

### 模型层重复如何体现

L0 不执行张量前向，也不模拟逐层时序。前端把 Qwen3 的 28 个同构 Transformer block 表示为一个 `LayerTypeSpec(count=28)`；`ModelSpec.body_mac_fixed()` 等方法按 `sum(count × 单层代价)` 汇总，等价于遍历 28 个 block 后累加。各层形状相同，不意味着权重共享；权重参数与 KV 流量同样乘以 28。

CLI 的 `--seq 0,4096,32768` 是三个独立工作点扫描，不是自回归生成循环。每点先汇总 MAC 与字节，再计算 `max(总字节/带宽, 总MAC/算力)`。当前报告不保留 block 索引、算子顺序或逐层时间线；异构层可以按类型分别给 count，但执行顺序与逐层资源归因属于 L1。

### 文件与数据结构

| 路径 | 内容 |
|---|---|
| `hw/profiles.py` | `HwProfile` 冻结 dataclass；`G0` / `G0_PRO` / `MODEL_ANALYSIS_310` 三实例 |
| `hw/geometry.py` | `load_array_geometry()`：import gem5 `configs/hardware.py` 取 `DEFAULT_PROFILE` |
| `frontend/spec.py` | `TensorSpec` / `LayerTypeSpec` / `ModelSpec` |
| `frontend/qwen3_06b.py` | config.json → `ModelSpec` |
| `common/workload.py` | `WorkloadPoint`：三相位 decode / prefill / frame |
| `common/manifest.py` | profile 名、来源 SHA256、`arch.md` 引用行号、未校准声明 |
| `common/report.py` | 报告骨架 |
| `l0/accounts.py` | 工作量与流量两个标量的累加 |
| `l0/roofline.py` | 上面那组公式 |
| `l0/bottleneck.py` | 瓶颈归因 |
| `l0/excluded.py` | 未建模项清单 |
| `l0/torch_check.py` | 可选 `torch.export` + `FlopCounterMode` 交叉校验 |
| `l0/run.py` | CLI 入口 |

关键字段：`HwProfile(dram_peak_gbps, dram_eta, sram_peak_gbps, sram_bytes, local_iface_bytes_per_cycle, mac_per_cycle, freq_hz, n_core)`；`NumFormat(bytes_per_param)`；`L0Result(..., is_upper_bound=True)`。

### 实施步骤

1. `hw/profiles.py` 三个 profile，数字逐项标注 `arch.md` 行号。
2. `hw/geometry.py` 从 gem5 读几何，字段不一致即抛错。
3. `frontend/spec.py` + `frontend/qwen3_06b.py`，先让参数量锚点过。
4. `common/workload.py` 三相位。
5. `l0/accounts.py` 工作量/流量。
6. `l0/roofline.py` 公式 + `l0/bottleneck.py` 归因。
7. `l0/torch_check.py`：torch 缺失时跳过而非失败。
8. `l0/run.py` + manifest 落盘。

### 工具

Python 3 标准库（`dataclasses`、`hashlib`、`json`、`unittest`）；`torch` 仅校验分支可选。不引入 numpy——L0 是标量算术。

### 测试锚点

附录 B 逐位复现：`fixed=15,750,144`、`s_term=4,112`、`uniq=596,049,920`、`ckpt=751,632,384`、`wb=614,527,468`、`kv_tok=114,688`、`AI≈0.971`。310 profile 下 504.5 / 285.9 / 70.9 / 19.8 tok/s（S→0 / 4096 / 32768 / 131072）。G0 profile 下 614 MB/token ÷ 17.064 GB/s ≈ 28 tok/s 上限。切 profile 不改代码。跨模型锚点：Qwen3.5-0.8B、Gemma4-E2B、Granite4.0-H-Tiny、LFM3-1.7B 各一组。

### 通用化需要的四项能力

1. `ModelSpec` 带层类型列表与每类 `count`，不假设同构。
2. 每类型给 `mac_fixed` / `mac_s_coef` / `kv_bytes_per_token` + `state_bytes`；DeltaNet/Mamba2 类的 KV 项为 0，另记状态字节。
3. `params_streamed_per_token` 与 `params_unique` 分开，供 MoE 用。
4. 三相位 workload，YOLO 走 frame → FPS。

### 风险

- **口径误读。** 任何吞吐必须带 profile 名；report 头部打印 profile 与来源 hash。
- **`bytes_per_param=1.031` 是四舍五入值**，精确值 33/32=1.03125，相对文档值差约 0.0242%。锚点断言用文档值，内部计算两者都给，差异进报告脚注；文档未给口径的格式使用精确值并标明来源。
- **把上界当预测。** `L0Result.is_upper_bound=True` 硬编码，并加跨层断言 `t_L1 >= t_L0`，违反即失败。

---

## L1　`src/hive/model_eval/l1/`　资源分账（本计划主体）

### 建模方法

**三层确定性枚举 + 多账本累加 + 时间线合成。** 不是概率模型，不是事件驱动仿真，是把 RTL 的循环结构在 Python 里重走一遍，每一步往账本里记数，并给每笔记账打上来源标签。

**第一层：算子 → 命令。**

```
Nprog   = ceil(N / T) × T              # T 来自冻结几何，默认 16
pA      = 1(BF16) / 2(8bit) / 4(4bit)
kPerBeat= T × pA                       # W4A8 按 8bit A，每拍 32 个 K 元素
kGran   = 64(W4A8) / kPerBeat(同宽)    # MX 同时检查 block16/32
Kprog   = ceil(K / kGran) × kGran
Mmax    = cBufferDepth − T             # 默认 48，不在 eval 侧复制常数
Nmax    = floor((2^mnkWidth−1) / T) × T
Kmax    = floor((2^mnkWidth−1) / kGran) × kGran
commands= split(M,Mmax) × split(Nprog,Nmax) × split(Kprog,Kmax)
pad_macs= sum(cmd.M×cmd.N×cmd.K) − M×N×K
```

**第二层：命令 → 窗口。** 窗口是 (N tile, K pass) 的笛卡尔积，顺序固定不可重排；权重每窗口重装。

```
windows      = Σ_cmd nTiles_cmd × kTiles_cmd
B_beats      = windows × T             # 每窗口严格连续 T 拍装载
A_beats      = Σ_cmd nTiles_cmd × kTiles_cmd × M_cmd
C_rows       = Σ_cmd nTiles_cmd × M_cmd # 每命令只在最后内部 K pass 输出
array_cycles = B_beats + A_beats       # 已知装载/消费结构计数
array_util   = A_beats / array_cycles  # M=1 → 1/17；每命令 M=48 → 48/64
```

`prologue` / `epilogue` 不赋猜测值，进入 `excluded_costs`。独立 K 子命令各自输出 FP32 部分和，额外显式累加按 S018 计账；不能当作同一命令的内部 K pass。lm_head 的 N=151936 拆为 65520/65520/20896，窗口与 C 总数不变。

**第三层：窗口 → 账本，每笔带来源。**

```
Origin(stream, producer)               # 例：("weight", "dma_b")
Charge(resource, instance, origin, beats, bytes, cycles, ops, overlap_class, evidence)
Ledger(charges, coupling, excluded)
coupling[(resA, resB)] ∈ {"same_source", "independent"}   # 由 residency.py 生成
```

耦合来自明确的流来源与 staging 决策。默认 B 流式，DRAM/B 端口/loadW 同源；`--weight-policy resident` 仅在完整权重加活跃区容量通过时减少 DRAM 重装，并将首次 DRAM 装载和后续本地重装分为不同阶段。A 在每命令活跃区能容纳时显式 staging 一次，否则流式；C 显式写出，不假设跨算子缓存。310 容量未知，不自动驻留。

逻辑元素字节、逻辑 scale 字节、端口物理请求字节分列。ScaleA/B 每次逻辑步进由 `kPerBeat/block` 决定；非对齐 4-byte scale 读按规格分别计两次物理请求，相邻请求不复用。物理 DRAM burst 粒度与响应延迟仍未建模。

**时间线合成，三步。**

```
# ① 同源折叠：按 origin.key() 分组取 max
t_folded[k] = max(t_charge for charge in group k)
# ② 算子内：按 overlap_class 合成，默认全 "serial"
t_op        = Σ t_folded  (serial)  /  max t_folded  (overlapped, 需证据)
# ③ 跨算子：沿 DAG 累加 + 交接气泡
t_total     = Σ t_op + handoffs × sync_us        # sync_us 默认 0
```

默认串行算子链，交接数量由真实引擎切换计数，`--sync-us` 作为独立情景计入，不硬编码 392。重叠默认关闭；开启需要非空 `evidence` 与 SRAM 双缓冲容量校验。当前仅覆盖 VPU 算术/本地读写和 GEMM consume/C write 的重叠，loadW 与 consume 始终串行；不推测跨窗口预取时序。五类归因由折叠/重叠区间的主导资源接收，原始资源需求另存 debug ledger，不将重叠的需求重复计入总时间。

### 文件

| 路径 | 内容 |
|---|---|
| [l1/mapping.py](../model_eval/l1/mapping.py) | LogicalOp→TaskGraph，显式 producer/consumer、转换/补齐与 K merge |
| [l1/gemm_model.py](../model_eval/l1/gemm_model.py) | 唯一 GEMM 文件：命令拆分、窗口计数、流量与阶段预算；原两拆分模块已删除，无兼容重导出 |
| [l1/residency.py](../model_eval/l1/residency.py) | ExecutionPlan、BufferPlan 与 base allocation 生命周期 |
| [l1/ledger.py](../model_eval/l1/ledger.py) | EngineCost/Phase、资源/流量/缓冲需求与原始账目 |
| [l1/dram.py](../model_eval/l1/dram.py) | 服务换算，缺速率保持未知 |
| [l1/vpu_model.py](../model_eval/l1/vpu_model.py)、[cpu_model.py](../model_eval/l1/cpu_model.py)、[dma_model.py](../model_eval/l1/dma_model.py) | 独立 typed 引擎模型；转换/整数 merge/跨 core route 未知阶段显式保留 |
| [l1/kernels.py](../model_eval/l1/kernels.py) | 薄 estimate 分发，不持有成本算法 |
| [l1/params.py](../model_eval/l1/params.py) | 结构/场景参数及来源 |
| [hw/topology.py](../model_eval/hw/topology.py) | 多实例、私有内存/端口、共享 DRAM，不自动分片 |
| [l1/scheduler.py](../model_eval/l1/scheduler.py) | 固定计划 serial/list 预约，actual 与条件 projection 分离 |
| [l1/timeline.py](../model_eval/l1/timeline.py) | 引擎估算/调度编排与 schema2 报告 |
| [l1/web.py](../model_eval/l1/web.py) + [resources.html](../model_eval/l1/resources.html) | 任务/阶段、预算/跨度、流量/等待/容量与原始账目 |
| [l1/run.py](../model_eval/l1/run.py) | CLI、Topology/ExecutionPlan/调度输入，JSON/HTML |

### 首版实施步骤（历史设计，非当前接口）

1. `l1/mapping.py` 引擎映射与合法化。
2. `l1/gemm_model.py` 命令拆分（历史拆分模块已合并）。
3. `l1/gemm_model.py` 窗口枚举——先写下面的对账表断言再写实现。
4. `l1/residency.py` 驻留判定与 `coupling` 生成（历史职责；现行职责见上表）。
5. `l1/ledger.py` 账本与同源折叠。
6. `l1/dram.py` 时间换算。
7. `l1/vpu_model.py` / `cpu_model.py` / `dma_model.py` 非 GEMM 代价（历史共用入口已拆分）。
8. `l1/params.py` 回退值，逐项带引用。
9. `l1/kernels.py` 注册表接线。
10. `common/report.py` 五类归因表（可与 1、2 并行）。
11. `l1/timeline.py` 合成。
12. 跨层断言 `t_L1 >= t_L0`（已退役的首版设想，现行比较见顶部说明）。
13. `l1/run.py` + prefill/decode 扫描。

第 1、2、10 步可并发。

### 工具

Python 3、`dataclasses`、标准库 `unittest`，无新增必需依赖。窗口可逐项懒枚举，扫描通过每条命令的精确笛卡尔积计数累加，避免物化数百万窗口。**不引入仿真框架**——L1 是解析累加，不维护事件队列或逐拍 FIFO 状态。

### 方法论要点

- **窗口级累加，不用效率系数。** 每个窗口的装载与消费拍数单独记再求和。这是捕捉 1/17 的唯一正确方式；写成 `peak × 0.059` 会在 M>1 的 prefill 上立刻失真。
- **资源分账而非单一瓶颈取 max。** 多本账各自到底，最后才合成。否则无法回答「时间花在哪」。
- **同源折叠由数据决定。** 耦合表来自驻留判定，不是硬编码的 profile 分支。
- **显式搬运，不假设命中。** `arch.md` 规定首版显式 SRAM 管理、无透明 NPU cache。
- **可降级。** 没有依据的项标为未建模并进 `excluded_costs`，不填猜测值。
- **不得把 gem5 数字当输入。** gem5 阻塞时 L1 必须照常可运行。

### 测试锚点：Qwen3-0.6B decode（M=1，MXFP8，单核）

| 量 | 单层 mixer+MLP | lm_head | 校验 |
|---|---|---|---|
| windows | 30,720 | 303,872 | — |
| A beats | 30,720 | 303,872 | = windows（M=1） |
| B beats | 491,520 | 4,861,952 | ×32 B = 15,728,640 / 155,582,464 |
| C rows | 768 | 9,496 | — |
| array_cycles | 522,240 | 5,165,824 | = windows × 17 |

两个独立交叉校验：`491,520 × 32 = 15,728,640` 等于单层 mixer+MLP 权重元素数；`4,861,952 × 32 = 155,582,464` 等于 lm_head 参数量。单 token 单核 `array_cycles = 19,788,544`，B 拍字节 `595,984,384`。

其余断言：`array_util` decode = 1/17、prefill M=48 = 48/64；关闭全部重叠时总时间 = 各段之和、开启后 ≥ 最长单段；五类归因之和 = 总时间；同源合成等于 max 而非和（构造两者相差 2 倍的用例断言 ≠ 和）；关掉 debug 不改数值；同输入可复现。

VPU 侧锚点：decode batch1 每 token 729 次 norm 调用、448 次 softmax 归约；S=4096 softmax 三趟回退量为 688128 cycles = 1.376256 ms。exp 默认未计入；显式 1.25 cycle/元素 情景增加 2293760 cycles = 4.58752 ms，不包含 SiLU 的额外 exp。S018 每次独立 K 部分和累加搬运 `3×4×M×N` bytes，另计 FP32 add 回退量；整数输出需转换后再供 VPU，转换服务未计入。采样默认未计入，只有同时输入 CPU 时钟与每比较周期时才发布 full-sort 比较预算情景。

L1 prefill 的 QK/AV 有效量按 `S(S+1)/2`，包含因果对角线；softmax 也采用精确因果元素数。L0 仍保留原文 `S²/2` 主项近似与 softmax 缺项，不改原模型分析文档。量化 GEMM attention 明确发射完整带 mask 矩阵，额外 mask/padding 工作单列；VPU 变体采用 FP16 FMA 峰值预算，FP32 累加/转换服务仍未覆盖。VPU/CPU 算子边界的张量 DRAM staging 与输出 spill 显式计入，不以 GEMM 输出已落盘却仍免费留在 SRAM 的假设省略重载。

### 风险

- **scale 账本口径。** 不得用 4 B 端口宽度替代逻辑 scale 步进（gem5 spec §7.6）；ScaleC 仅 local SRAM（§7.7）。
- **命令级开销未知。** setup、prologue/epilogue、排空等列入 `excluded_costs`，不猜，也不以外部工程的验收状态替代本报告校准证据。
- **prefill 权重重装放大。** `ceil(S/48)` 个命令各自重装权重，S 大时这项会主导；需单独成列而非混进 DRAM。
- **QKᵀ/AV 归属未定。** arch §4.4 说当前 GEMM 默认输入不支持 FP16/FP32，两个变体并列输出，不替用户选。
- **回退值被当实测。** `l1/params.py` 每项带 `source="fallback"`，报告中单列；31 拍候选结构延迟不出现在任何总延迟字段。

---

## 关键文件

**新建**

| 路径 | 内容 |
|---|---|
| `src/hive/model_eval/hw/{profiles,geometry}.py` | 三个命名 profile；从 gem5 读阵列几何 |
| `src/hive/model_eval/frontend/{spec,qwen3_06b}.py` | config → `ModelSpec` |
| `src/hive/model_eval/common/{workload,manifest,report,opspec}.py` | 公共契约，含未校准声明单点 |
| `src/hive/model_eval/l0/*.py` | 7 文件，见 L0 节 |
| `src/hive/model_eval/l1/*.py` | 11 文件，见 L1 节 |
| `src/hive/model_eval/tests/` | 分层 unittest 锚点 |
| `src/hive/doc/model_eval_plan.md` | 本文件记录实施状态、分层边界与口径 |

**报告**：CLI 支持 `--json` 保留 manifest、逐层与逐算子明细，`--debug-ledger` 附原始资源需求，`--layer-detail` 在终端展开逐层归因。`--html` 导出可直接打开的离线交互网页，始终包含原始账本，不依赖 `--debug-ledger`。网页按 Python 时间线展示五类归因；逐层时间合计不包含跨引擎交接，交接时间单列。原始资源需求不直接相加，未知时间仍标为未知。

**只读引用，不修改**

| 路径 | 用途 |
|---|---|
| `src/hive/doc/arch.md` | 硬件常数真源（第 590 / 869 / 895 / 943 行）、S018 / S022 / C041 / §4.4 |
| `src/hive/doc/arch_roadmap.md` | 算力真源（第 74 / 75 行） |
| `src/hive/doc/model_analysis/*.md` | shape 与手算锚点，11 份；Qwen3-0.6B 附录 B 为参数量唯一真源 |
| `src/workerbee/gem5/configs/hardware.py` | 冻结阵列几何 `DEFAULT_PROFILE` |
| `src/workerbee/gem5/spec.md` | §6.1 几何、§6.4 结构延迟、§7 DMA、§11 边界、§13 歧义 |

`src/hive/doc/performance_analysis.md` 归用户，不动。`src/workerbee/gem5/` 下任何文件本计划都不改。

---

## 验证

**单元回归**（`PYTHONPATH=src python3 -m unittest discover -s src/hive/model_eval/tests -t src`）

1. 参数量：`fixed=15,750,144`、`s_term=4,112`、`uniq=596,049,920`、`ckpt=751,632,384`、`wb=614,527,468` 逐位相同。
2. 几何：eval 侧读到的 `arrayN`/`arrayM`/`totalN`/`cEffW`/`supportedFmts` 与 gem5 `DEFAULT_PROFILE` 逐字段相同，不一致即失败。
3. 常数：三个 profile 的 DRAM / 算力数字与 `arch.md`、`arch_roadmap.md` 一致。
4. L0 吞吐：310 profile 下 504.5 / 285.9 / 70.9 / 19.8 tok/s。
5. L1 几何：对账表十项逐位一致（含 `491,520×32 = 15,728,640` 与 `4,861,952×32 = 155,582,464` 两个交叉校验）。
6. L1 利用率：decode `array_util = 1/17`，prefill M=48 时 `48/64`。
7. 同源取 max：loadW 与 dma_b 合成结果等于两者 max；构造相差 2 倍的用例断言 ≠ 和。
8. 耦合由显式数据路径生成：流式 B 为 `same_source`；容量通过且明确驻留时首次 DRAM 装载与本地重装分为独立阶段，310 不推断驻留。
9. 重叠边界：关闭时总时间为折叠后各段之和；证据与容量通过后只重叠许可区间，同一物理资源上的独立流不得通过 max 丢失需求。
10. 归因自洽：五类分量之和 = 总时间。
11. 跨层：服务率齐全的同输入、同精确字节口径下断言 `t_L1 >= t_L0`；310 仅输出已知部分时间，明确不可比较，不以 max(L1,L0) 补造时间。
12. 未校准声明：`performance_valid=false`，报告头部有未校准字样；回退值全部带 `source="fallback"`。
13. gem5 独立性：除 `hw/geometry.py` 读 `DEFAULT_PROFILE` 外不 import gem5 任何模块；gem5 结果缺失时全部用例通过。
14. 确定性：同输入可复现；关闭 debug 不改数值。

**端到端**

```sh
export PYTHONPATH=src
python3 -m unittest discover -s src/hive/model_eval/tests -t src
python3 -m hive.model_eval.l0.run --model Qwen3-0.6B --profile G0  --workload decode  --seq 0,4096,32768
python3 -m hive.model_eval.l0.run --model Qwen3-0.6B --profile 310 --workload decode  --seq 0,4096,32768
# L1 默认同时输出 VPU 与量化 GEMM attention 变体；单核已计入成本，不代表实测
python3 -m hive.model_eval.l1.run --model Qwen3-0.6B --profile G0  --workload decode  --seq 1,4096,32768
python3 -m hive.model_eval.l1.run --model Qwen3-0.6B --profile 310 --workload decode  --seq 1,4096,32768
python3 -m hive.model_eval.l1.run --model Qwen3-0.6B --profile G0  --workload prefill --seq 1024,32768
# 离线网页包含两种 attention 的逐层/逐算子明细与原始账本
python3 -m hive.model_eval.l1.run --profile G0 --seq 0,4096 --html sim/model_eval/g0-l1-decode.html
```

人工检查：G0 报告明确单核计划、310 明确服务率缺失且不输出吞吐；decode 权重 GEMM 的结构利用率为 1/17；loadW 与同源 DRAM 不重复相加；逐算子逻辑/物理字节、驻留依据、mask/padding 与未知成本均可追溯；归因表示已计入时间而非原始需求之和；报告头部明确未校准。

**M5 铺开判据**：其余 10 份 model_analysis 跑通同一套代码，每份输出与文档手算对表，差异要么解释要么修代码，不改文档数字。

---

## 不做的事

- 不改 `model_analysis/` 已有文档的数字。它们按 310 口径自洽。
- 不在 eval 侧重定义阵列几何。
- 不做数值计算。eval model 输出时间和流量，不输出张量；精度验证另立。
- 不做指令发射、寄存器分配、runtime、驱动。
- 不把效率因子当拟合参数，不加按 shape 的补偿表。
- **不实施 L2/L3，不建 `l2/`/`l3/` 目录，不排它们的里程碑，不改 `src/workerbee/gem5/` 任何文件。**
- 不让 L1 依赖 gem5 可用才能跑通。
- 不因部分单测通过而声明已校准。
