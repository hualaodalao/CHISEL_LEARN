# model_eval L1 算子级 cost 评估优化计划（v2）

日期：2026-11-10

本文覆盖旧版《model_eval 层注册表与模型复用优化计划》。旧版 Phase 0–6 已落地，本版只描述下一阶段：把 L1 从“phase/ledger 引擎明细”收敛为“粗粒度算子的初步硬件 cost 评估”，并明确与 gem5 的分工。

范围：`src/hive/model_eval` 的 L1 成本估算、报告、CLI/Web 与扩展接口。

不在范围：L2/L3、RTL 周期模型、gem5 校准；不修改 `src/workerbee/gem5/`，不修改用户维护的 `src/hive/doc/performance_analysis.md`，不改 `src/hive/doc/model_analysis/` 下既有数值。

## 0. 结论

1. **L1 的定位是“初步算子 cost”，不是周期模型。** 每个粗粒度算子（linear/rms_norm/softmax/attention matmul/activation gate/DMA/sampling 等）给出运行时间和“大概率归属引擎”，周期级细节交给 gem5。
2. **不要压成单个标量时间。** 纯 `seconds + engine tag` 会在 weight stream/resident、1 核/2 核、VPU/GEMM attention、双缓冲、共享 DRAM 争用这些决策上给出错误结论。正确形态是“算子 cost 记录”：duration（known/unknown）+ DRAM/local 字节 + footprint + 引擎租约 + overlap 证据。
3. **`EngineCost` 保留为唯一事实源，`OperatorCost` 只做只读投影。** 直接删除 `EngineCost` 会破坏约 93/301 条测试和 4 条核心不变量；第一批只新增视图，不改调度器语义。
4. **先改报告，再改建模入口。** 当前默认报告的 `makespan` 恒为 None，`projection_makespan_s` 实际是“未知服务按 0 计算的串行求和”，不能当并发结论。先给每算子 cost 表、三个下界和 coverage，工具才可用于拆分/拓扑比较。
5. **未知成本不伪造。** conversion、FP32 accumulation 等仍以 unknown 传播；只有显式给出场景参数时才计费。
6. **新增算子只写一个公式，算子形态是第一抽象。** 引擎不再作为建模单元：`engine_kind` 只保留资源 identity、参数包和服务模板三种职责。新增算子通过 `(spec_kind, engine_kind) → cost_fn` 接入；GEMM 的命令规划和 DMA 的端点判定作为共享 helper 保留，不复制到每个算子。

当前基线（2026-11-10 实测，`--model Qwen3-0.6B --profile G0 --workload decode --seq 4096 --attention vpu --schedule serial`）：

| 指标 | 当前值 |
|---|---|
| 任务数 / 实际排上 / 仅投影 | 817 / 85 / 732 |
| makespan | None |
| known_service_budget | 248.758 ms |
| projection_makespan | 248.758 ms（串行已知和，不是并发下界） |
| L0 参考 | 63.551 ms |
| projection 归因 | DRAM 57.2 / GEMM 69.4 / VPU 122.2 ms |
| 未覆盖项 | 517 条（约 8 类根因） |
| SRAM 峰值（projection） | 672 B（只统计命令级 scratch） |
| DRAM 峰值（projection） | 161 MB |
| array_utilization | 5.9% |

### 0.1 实施状态（2026-11-10）

S1–S6 已落地：

- **S1 基线**：全量 301 项测试通过；记录默认 decode@4096 的 makespan/known/coverage 与 EngineCost 字段依赖（约 93/301 条测试引用）。
- **S2 cost view**：新增 `l1/cost_view.py`；`L1Result.to_dict()` 增加 `operator_costs`、`bounds`、`coverage`、`top_unknowns`；下界为 `max(最忙单引擎, DRAM, DAG 关键路径)` 并标注 `lower_bound`。
- **S3 报告降噪**：CLI 默认显示算子 cost、下界、覆盖和 Top unknown；unknown 按 phase 后缀/全局注记聚合，handoff 已合并；JSON/HTML 保留完整字段，CLI 用 `to_dict(detailed=False)` 的 compact 视图，`--detailed` 显示阶段/等待/层级明细。
- **S4 场景参数**：`ModelParams` 和 CLI 增加 `vpu_conversion_elements_per_cycle`、`vpu_fp32_accum_elements_per_cycle`；缺省仍 unknown，显式给值按 scenario 计费。
- **S5a/S5b 算子形态**：新增 `l1/costing/{registry,services,gemm_commands}.py` 和 `l1/costing/forms/*`；`engines.estimate()` 先查 `(spec_kind, engine_kind)`；`vpu_model/cpu_model/dma_model` 收缩为兼容包装；GEMM 命令规划保留共享入口，尚未物理搬出 `gemm_model.py`。
- **S6 文档/Web**：CLI `--detailed/--top-unknowns/--top-ops`；HTML 新增“00 / 算子 cost 与下界”区块；本文件与 `model_eval_spec/howto/tutorial/end2end` 已更新。

验证：全量 **317 项测试通过**；Qwen L0/L1 数值锚点未变。S7（把 phase 明细从默认 JSON 中移除）按计划保持可选、未执行。

## 1. 需求用例

| 编号 | 用例 | 验收信号 |
|---|---|---|
| UC1 | 给定一个拆分方案，按粗粒度算子输出初步硬件 cost 和引擎归属 | 每算子实例一行 cost 记录 |
| UC2 | 报告能直接比较拆分方案和硬件拓扑 | 每引擎忙碌下界、DRAM 下界、DAG 关键路径下界、coverage 同时可见 |
| UC3 | 未知成本不伪造；显式场景参数可闭合 | 默认 unknown；给全参数后 makespan 可确定 |
| UC4 | 新增算子只写一个 cost 公式，不碰引擎内部 | 扩展 demo 不改核心文件即可接入 |
| UC5 | 保持 Qwen 数值锚点和现有测试 | 301 项测试通过，默认数值不变 |
| UC6 | L1 与 gem5 边界清晰，phase 明细降为可选 | 默认报告不展示 phase 时刻/waits/buffer_plan；`--detailed` 仍可取 |

## 2. 目标架构

```text
算子形态（authoring，第一抽象）
  forms/rms_norm.py / softmax.py / attention.py / activation_gate.py /
  transfer.py / sampling.py / gemm.py ...
        ↓  registry: (spec_kind, engine_kind) → cost_fn
costing/services.py        公共 DRAM/local/boundary/traffic/buffer 构造
costing/gemm_commands.py   matmul 命令规划；被多个 GEMM 形态共用
        ↓
EngineCost（唯一事实源，保留现有 phase/lease/traffic/buffers）
        ↓  只读派生，不改语义
OperatorCost 视图 + 三个下界 + coverage + unknown 聚合
        ↓
CLI / JSON / HTML 报告
        ↓
调度器仍吃 EngineCost：引擎租约 + 共享 DRAM + DAG 关键路径
```

引擎类仍存在，但只承担三件事：资源 identity（engine lease/候选实例）、参数包（lanes/freq/带宽/几何）、服务模板（少数几类 phase 组合）。它不再决定公式放在哪个文件。

分层原则：

- **authoring 层**：新算子在 `costing/forms/` 只写一个 cost function，参数来自算子形态（shape/bytes）和引擎常数（lanes/freq/带宽）。
- **语义层**：`EngineCost` 继续表达引擎租约、DRAM 带宽占用、buffer 生命周期和 unknown；不在第一批删除。
- **展示层**：`OperatorCost` 是只读投影，供报告和比较使用；不参与调度。
- **调度层**：第一批不改 `scheduler.py`；避免同时改数值口径和调度语义。

### 2.1 L1 与 gem5 的分工

| L1 保留 | 交给 gem5 |
|---|---|
| 引擎租约、实例归属 | 端口仲裁、发射流水 |
| 共享 DRAM 字节与有效带宽 | DRAM burst/行冲突/首字节延迟 |
| 本地接口字节 | bank conflict |
| SRAM footprint 与容量准入 | 命令 prologue/epilogue 气泡 |
| DAG 顺序、同核/跨核依赖身份 | loadW/consume 交织、流水线级重叠 |
| 显式 overlap 证据 | scale gather 响应延迟、跨核路由延迟数值 |
| known/unknown 与 evidence | 周期级精确 service time |

## 3. 关键设计决策

### 3.1 EngineCost 与 OperatorCost

`EngineCost` 字段不变：`task_id, instance_id, phases, lease_resources, traffic, buffers, metrics, ledger, excluded_costs`。

新增只读视图 `OperatorCost`：

```text
task_id, op_id, form, engine_kind, instance_id, lease,
seconds: float | None, known_seconds: float,
dram_read_bytes, dram_write_bytes, local_bytes,
footprint_bytes, overlap: bool, overlap_evidence,
basis: structural | architecture_peak | fallback | scenario | unknown,
unknown: tuple[str, ...], source, evidence
```

派生规则：

| OperatorCost 字段 | 来源 |
|---|---|
| `seconds` | `EngineCost.complete ? known_service_s : None` |
| `known_seconds` | `EngineCost.known_service_s` |
| `dram_read_bytes/dram_write_bytes` | `traffic` 中 `memory_domain == "dram"` 的 logical bytes，按 direction 汇总 |
| `local_bytes` | `traffic` 中非 DRAM 域的 logical bytes |
| `footprint_bytes` | `buffers` 的 allocated bytes 区间并集 |
| `lease` | `lease_resources`（保持引擎租约语义） |
| `overlap` | 任一 phase 的 `overlap_class != "serial"` 且 evidence 非空 |
| `unknown` | `excluded_costs` 中与本任务相关的条目 |
| `basis` | phase `source` 的保守合并；有 unknown 时为 `unknown` |

约束：

- `OperatorCost` 不新增数值，只重排 `EngineCost`。
- `dram` 下界只从 `traffic` 汇总，避免 `metrics["dram_bytes"]` 与 traffic 双算。
- `unknown` 不折 0；`seconds` 为 None 时报告必须显示“未知”，不能按 0 排名。

### 3.2 三个下界

```text
engine_busy_lb[e] = Σ known_seconds(task)  （绑定到 e；unknown 阶段记 0，仍为下界）
dram_lb           = Σ traffic(dram).logical_bytes / dram_effective_bytes_per_s
cp_lb             = max over DAG paths Σ known_seconds(task)  （unknown 阶段记 0）
lower_bound_s     = max(max_e engine_busy_lb[e], dram_lb, cp_lb)
```

注意：

- **`max_e` 不是 `Σ_e`**。跨引擎忙碌时间不能相加；同一引擎内才求和。
- `lower_bound_s` 是**下界**，不是可达 makespan；报告必须标注 `achievability = lower_bound`。
- 不同拓扑的下界松紧不同，不能只按下界排名；必须同时看 coverage 和 unknown。
- 有 unknown 时仍可给下界，但要标 `coverage`：`tasks_total / tasks_known / tasks_unknown / unknown_ratio`。
- 现有 `projection_makespan_s` 保留，但人类可读输出标注为“串行已知和”，不再作为并发结论。

### 3.3 unknown 聚合

默认报告不展开 517 条重复条目，按根因聚合：

- 从 phase id 最后一个 `:` 后缀取稳定关键词（`conversion`、`fp32_accumulation`、`rsqrt`、`exp`、`reciprocal`、`handoff` 等），结合 `category` 分组；
- 全局模型限制（layout、chunk、GEMM command setup 等）按固定前缀归并，去重后只显示一次；
- 输出 `top_unknowns: [{cause, category, tasks, evidence, source}]`；
- 聚合只用于展示，不改变 `EngineCost.excluded_costs` 契约。

### 3.4 算子形态目录与注册表

算子形态是第一抽象。新增 `l1/costing/`：

```text
l1/costing/
  registry.py          # (spec_kind, engine_kind) -> cost_fn；simple_cost()
  services.py          # 公共 phase/traffic/buffer 构造（从 dma_model._ServiceBuilder 提升）
  gemm_commands.py     # matmul 命令规划/窗口计数（被多个 GEMM 形态共用）
  forms/
    vpu.py        # norm/rope/residual/silu_gate/softmax/attention_vpu/k_accum/convert 各一个函数
    transfer.py
    sampling.py
    gemm.py
```

职责划分：

| 层 | 职责 | 不负责 |
|---|---|---|
| `forms/*.py` | 一个算子形态一个 cost function；把工作量/字节换成 service terms | 不解析引擎配置、不组装报告 |
| `services.py` | DRAM/local/boundary phase、traffic、buffer 的公共构造 | 不决定算子语义 |
| `gemm_commands.py` | legalize/窗口计数/K 拆分；图构建与成本共用 | 不组装 EngineCost |
| `registry.py` | `(spec_kind, engine_kind) → cost_fn`；`simple_cost()` | 不参与调度 |
| 引擎类 | 资源 identity、参数包、服务模板 | 不再作为公式目录 |

注册与调度：

```text
OPERATOR_ESTIMATORS: dict[(spec_kind, engine_kind), cost_fn]
register_operator_estimator(spec_kind, engine_kind, fn, *, replace=False)
simple_cost(task, seconds, *, resource=None, dram_read_bytes=0,
            dram_write_bytes=0, local_bytes=0, footprint_bytes=0,
            unknown=(), evidence="", source="structural") -> EngineCost
```

- `engines.estimate()` 先查 `(spec_kind, engine_kind)`，未命中再回退现有 `ENGINE_ESTIMATORS[engine_kind]`；
- `simple_cost()` 生成 2–3 个 phase（引擎租约 + DRAM 读/写 + 可选 local），继续返回 `EngineCost`；
- 新算子不修改 `vpu_model._KINDS`；spec 类型仍由 `common.opspec.ENGINE_SPECS` 校验。

#### 3.4.1 为什么 GEMM/DMA 保留共享 helper

GEMM 与 DMA 不是“公式”，而是被多个算子复用的规划逻辑：

- GEMM：`legalize()` / `count_windows()` 决定命令数、窗口数、K 拆分；`GraphBuilder.gemm()` 与成本估算必须共用同一份命令序列，否则图结构和 cost 漂移。q_proj/o_proj/qk/av/down_proj/lm_head 都依赖它。
- DMA：transfer 的 service 取决于 source/destination 域、local alias、跨域路由 unknown、staging/buffer 生命周期；activation 搬入/搬出、KV append、weight load 都依赖它。

因此保留 `gemm_commands.py` 和 `services.py` 作为纯规划/构造库；对外仍然是一个算子形态一个 cost function。它们是共享机制，不是第二套引擎模型。

#### 3.4.2 迁移策略

- S5a 先建立 `costing/`、`services.py`、注册表和兼容回退，旧 estimator 全部可用；
- S5b 逐个把 `vpu_model` 的 `spec.kind` 分支、`dma_model` 的端点判定、`cpu_model` 的 sampling 分支搬到 `forms/`；
- 每迁一个形态跑一次现有测试与 Qwen 数值对比；`EngineCost` 契约不变；
- 四个引擎文件最终收缩为参数/兼容包装，不再承载“算子公式目录”的角色；第一批不删除，保留回退。

### 3.5 VPU conversion / FP32 accumulation 场景参数

`ModelParams` 增加：

```text
vpu_conversion_elements_per_cycle: int | None = None
vpu_fp32_accum_elements_per_cycle: int | None = None
```

- 缺省 None：保持现有 unknown phase，不伪造；
- 显式给值：按 `elements / lanes / freq` 计费，`source="scenario"`，evidence 记录参数值；
- 0 或负数报错；
- CLI 增加 `--conversion-elements-per-cycle`、`--accum-elements-per-cycle`；
- manifest 的 `named_inputs` 记录这两个场景参数。

### 3.6 报告兼容策略

- 第一批**只增不删**：`L1Result.to_dict()` 增加 `operator_costs`、`bounds`、`coverage`、`top_unknowns`，旧字段保留；
- CLI/HTML 默认使用 compact 视图；`--detailed` 保留 phase 时刻、waits、buffer_plan、graph、topology、ledger；
- 默认人类可读输出不再把 `projection_makespan_s` 叫“投影跨度”，改为“串行已知和”；
- 旧 JSON 消费方可继续读旧字段；删除旧字段放到可选的 S7，等测试迁移完成后再做。

## 4. Stories

| ID | 标题 | 覆盖 | 依赖 | 规模 |
|---|---|---|---|---|
| S1 | 冻结 L1 基线与成本依赖清单 | UC5 | — | S |
| S2 | 新增 OperatorCost 只读投影与三个下界 | UC2 | S1 | M |
| S3 | 报告降噪：unknown 聚合与 compact 视图 | UC2, UC6 | S2 | M |
| S4 | 补齐 VPU conversion / FP32 accumulation 场景参数 | UC3 | S1 | S |
| S5a | 建立 costing 目录、注册表与共享服务层 | UC1, UC4 | S1 | M |
| S5b | 迁移现有算子形态；保留 GEMM/DMA 共享 planner | UC1, UC4 | S5a | M |
| S6 | CLI/Web/文档切换到新 cost 视图 | UC2, UC4, UC6 | S2–S4, S5a, S5b | M |
| S7 | 可选：phase 明细降级为 --detailed | UC6 | S6 | M |

### S1 冻结 L1 基线与成本依赖清单

任务：

1. 运行当前 301 项 model_eval 测试并记录结果。
2. 记录 Qwen3-0.6B decode@4096（`--attention vpu --schedule serial`）的 L0/L1、任务数、known/unknown、metrics。
3. 静态统计 `EngineCost` 字段的生产代码与测试依赖（约 93/301 条测试引用 `phases/traffic/buffers/ledger/known_service_s/buffer_plan/lease_resources`）。
4. 把基线命令、锚点和依赖清单写入本文件或测试注释。

验收：基线可复现；后续改动可逐项对比；本 Story 不改任何行为。

### S2 新增 OperatorCost 只读投影与三个下界

任务：

1. 新建 `l1/cost_view.py`：`OperatorCost`、`CostBounds`、`build_cost_view()`。
2. 按 3.1 从 `EngineCost` 派生字段，不新增数值。
3. 按 3.2 计算 `engine_busy_lb`、`dram_lb`、`cp_lb` 和 `lower_bound_s`，标注 `lower_bound`。
4. 计算 coverage：总任务/已知/未知/未知比例。
5. 在 `L1Result.to_dict()` 增加 `operator_costs`、`bounds`、`coverage`，旧字段保留。
6. 补测试：投影等价、DRAM 不双算、unknown 不折 0、下界取 max 而非跨引擎求和。

验收：默认 CLI/JSON 能看到每算子 cost 和数值下界；`makespan` 仍可为 None，但不再只有“未知”。

### S3 报告降噪：unknown 聚合与 compact 视图

任务：

1. 按 3.3 聚合 unknown，输出 top causes（含受影响任务数）。
2. 新增 compact 报告视图，CLI/HTML 默认使用；不展示 phase 时刻、waits、buffer_plan、topology、layer_tree 的 span。
3. 保留 `to_dict()` 默认兼容；详细内容走 `--detailed`。
4. CLI 把 `projection_makespan_s` 标注为“串行已知和”，不冒充并发下界。
5. 补 unknown 聚合与 compact 视图测试。

验收：默认报告只显示算子 cost、bounds、coverage、top unknown；`--detailed` 仍能拿到完整明细。

### S4 补齐 VPU conversion / FP32 accumulation 场景参数

任务：

1. `ModelParams` 增加两个字段与校验。
2. `vpu_model` 在参数存在时计费，`source="scenario"`；缺省保持 unknown。
3. CLI 增加两个 flag，manifest 记录。
4. 补测试：有参数时 phase 有 seconds；无参数时 unknown；默认数值不变。

验收：显式场景参数可减少 unknown；全参数齐备的合成图上 makespan 可确定；默认不伪造。

### S5a 建立 costing 目录、注册表与共享服务层

任务：

1. 新建 `l1/costing/registry.py`：`OPERATOR_ESTIMATORS`、`register_operator_estimator()`、`simple_cost()`。
2. 新建 `l1/costing/services.py`：把 `dma_model._ServiceBuilder` 提升为公共 phase/traffic/buffer 构造。
3. `engines.estimate()` 先查 `(spec_kind, engine_kind)`，未命中回退现有 estimator。
4. 扩展 demo 用 `simple_cost()` 注册一个新算子 kind，走完整 `evaluate`。
5. 补测试：注册、重复注册、未知 kind 回退、租约/unknown 传播。

验收：新算子通过 `simple_cost()` 接入，现有 estimator 全部保持可用；算子公式尚未迁到 `forms/`。

### S5b 迁移现有算子形态；保留 GEMM/DMA 共享 planner

任务：

1. 新建 `l1/costing/forms/`：`vpu.py`（按 `spec.kind` 提供 norm/rope/residual/silu_gate/softmax/attention_vpu/k_accum/convert 函数）、`transfer.py`、`sampling.py`、`gemm.py`。
2. 把 `vpu_model` 的 `spec.kind` 分支逐个搬到 forms；`vpu_model` 收缩为兼容包装。
3. 把 `dma_model` 的端点判定搬到 `forms/transfer.py`；`_ServiceBuilder` 已在 `services.py`。
4. 把 GEMM 的命令规划保留为 `costing/gemm_commands.py`；`forms/gemm.py` 调用它，图构建与成本共用。
5. 把 `cpu_model` 的 sampling 分支搬到 `forms/sampling.py`。
6. 每迁一个形态跑现有测试与 Qwen 锚点等价对比。

验收：四个引擎文件不再承载“算子公式目录”；现有数值与测试不回退；GEMM/DMA helper 仍是被复用的共享规划器，而不是第二套引擎模型。

### S6 CLI/Web/文档切换到新 cost 视图

任务：

1. CLI 增加 `--detailed`、`--top-unknowns`，默认展示算子 cost 表、bounds、coverage。
2. Web 报告增加同名字段/区块，旧区块折叠保留。
3. 更新 `model_eval_spec.md`、`model_eval_howto.md`、`model_eval_tutorial.md`、`model_eval_end2end.md` 的调用链、字段和 L1/gem5 边界。
4. 更新本计划的实施状态。
5. 验证文档命令与 HTML smoke test。

验收：文档命令可直接跑出算子 cost 表、bounds、coverage；HTML 可读。

### S7 可选：phase 明细降级为 --detailed

任务：

1. 统计仍依赖默认 phase 字段的测试并迁移。
2. 默认 `to_dict()` 移除 phase 时刻、waits、buffer_plan、graph、topology；`--detailed` 保留。
3. 全量回归与 Qwen 锚点对比。

验收：默认 JSON 体积显著下降，`--detailed` 仍完整。只有 S2–S6 稳定后才执行。

## 5. 文件修改清单

### 5.1 新增

| 文件 | 用途 | Story |
|---|---|---|
| `src/hive/model_eval/l1/cost_view.py` | `OperatorCost`、`CostBounds`、coverage、unknown 聚合 | S2/S3 |
| `src/hive/model_eval/l1/costing/__init__.py` | 导出注册表与服务层 | S5a |
| `src/hive/model_eval/l1/costing/registry.py` | `(spec_kind, engine_kind) → cost_fn`、`simple_cost()` | S5a |
| `src/hive/model_eval/l1/costing/services.py` | 公共 phase/traffic/buffer 构造，从 `dma_model._ServiceBuilder` 提升 | S5a |
| `src/hive/model_eval/l1/costing/gemm_commands.py` | matmul 命令规划/窗口计数，图构建与成本共用 | S5b |
| `src/hive/model_eval/l1/costing/forms/__init__.py` | 加载内置 form 注册 | S5b |
| `src/hive/model_eval/l1/costing/forms/vpu.py` | VPU 各 `spec.kind` 的 cost function | S5b |
| `src/hive/model_eval/l1/costing/forms/transfer.py` | transfer 入口；调用 `services` 做端点判定 | S5b |
| `src/hive/model_eval/l1/costing/forms/sampling.py` | CPU sampling cost function | S5b |
| `src/hive/model_eval/l1/costing/forms/gemm.py` | GEMM 入口；调用 `gemm_commands` | S5b |
| `src/hive/model_eval/tests/test_operator_cost.py` | 投影等价、下界、coverage、unknown 聚合 | S2/S3 |
| `src/hive/model_eval/tests/test_operator_estimators.py` | 注册表、`simple_cost`、场景参数、兼容包装 | S4/S5a/S5b |

### 5.2 修改

| 文件 | 修改内容 | Story |
|---|---|---|
| `src/hive/model_eval/l1/engines.py` | 先查 `OPERATOR_ESTIMATORS`，未命中回退 | S5a |
| `src/hive/model_eval/l1/params.py` | 增加 conversion/accum 场景参数与校验 | S4 |
| `src/hive/model_eval/l1/vpu_model.py` | 使用新参数；S5b 收缩为兼容包装 | S4/S5b |
| `src/hive/model_eval/l1/dma_model.py` | `_ServiceBuilder` 迁到 `services.py`；transfer 逻辑迁到 `forms/transfer.py` | S5a/S5b |
| `src/hive/model_eval/l1/cpu_model.py` | sampling 分支迁到 `forms/sampling.py`，保留兼容包装 | S5b |
| `src/hive/model_eval/l1/gemm_model.py` | 命令规划迁到 `gemm_commands.py`，保留兼容包装 | S5b |
| `src/hive/model_eval/l1/timeline.py` | `to_dict()` 增加 cost_view/bounds/coverage/top_unknowns | S2/S3 |
| `src/hive/model_eval/l1/run.py` | 新 CLI 参数、compact 表格、串行已知和标注 | S3/S6 |
| `src/hive/model_eval/l1/web.py` | 新报告区块；旧区块折叠 | S6 |
| `src/hive/model_eval/l1/resources.html` | 新字段渲染（如需） | S6 |
| `src/hive/model_eval/tests/test_l1_integration.py` | 新字段断言；旧锚点保持不变 | S2/S3 |
| `src/hive/model_eval/tests/test_plan_integration.py` | 新字段断言；旧锚点保持不变 | S2/S3 |
| `src/hive/model_eval/tests/test_l1_web.py` | 新报告区块 smoke test | S6 |
| `src/hive/model_eval/tests/test_extension.py` | 扩展算子接入 | S5a |
| `src/hive/model_eval/tests/extension_demo.py` | 注册一个新算子 kind | S5a |
| `src/hive/doc/model_eval_spec.md` | 新契约、L1/gem5 边界 | S6 |
| `src/hive/doc/model_eval_howto.md` | 新 CLI 示例与判读 | S6 |
| `src/hive/doc/model_eval_tutorial.md` | 新人逐步路径 | S6 |
| `src/hive/doc/model_eval_end2end.md` | 调用链与报告说明 | S6 |
| `src/hive/doc/model_eval_opt_plan_1110.md` | 本计划与实施状态 | S6 |

### 5.3 第一批不改

| 文件 | 原因 |
|---|---|
| `src/hive/model_eval/l1/ledger.py` | `EngineCost` 契约保持不变 |
| `src/hive/model_eval/l1/scheduler.py` | 第一批不改调度语义；避免与报告改动同时引入数值漂移 |
| `src/hive/model_eval/l1/dispatchers.py` | 派发策略不在本阶段 |
| `src/hive/model_eval/l1/residency.py` | buffer 生命周期保持现状 |
| `src/hive/model_eval/l1/mapping.py` | legacy 展开路径不在本阶段 |
| `src/hive/model_eval/common/opspec.py` | 不新增引擎/spec 类型 |
| `src/hive/model_eval/hw/topology.py` | 候选引擎语义不在本阶段 |

引擎文件 `gemm_model/dma_model/cpu_model/vpu_model` 在 S5b 只做“搬家 + 兼容包装”，公式与数值锚点不变；它们不再作为新增算子的落点。
| `src/workerbee/gem5/**` | 明确不改 |
| `src/hive/doc/performance_analysis.md` | 用户维护，不改 |
| `src/hive/doc/model_analysis/**` | 既有数值不改 |

## 6. 测试与验收

### 6.1 必测项

1. 现有 301 项测试全部通过。
2. Qwen3-0.6B 锚点不变：L0 596,049,920；decode S=0 的 596,588,544 MAC；默认 decode@4096 的 `array_cycles=19,788,544`、`b_logical_bytes=595,984,384`、known_service_budget、projection 归因不变。
3. `OperatorCost` 派生与 `EngineCost` 等价：seconds/known/dram_bytes/lease/footprint/unknown 逐项对齐。
4. 下界口径：`max(max_e busy, dram, cp)`；跨引擎忙碌不相加；unknown 不折 0；coverage 正确。
5. unknown 聚合：根因数量、受影响任务数、去重后的全局注记。
6. 场景参数：缺省 unknown；给值后 `source="scenario"`；0/负数报错。
7. 算子注册表：新 kind 接入；重复注册报错；未知 kind 回退现有 estimator。
8. 报告：默认 compact 视图字段齐全；`--detailed` 仍有 phase/waits/buffer_plan。

### 6.2 命令

```bash
export PYTHONPATH="$PWD/src${PYTHONPATH:+:$PYTHONPATH}"
python3 -m unittest discover -s src/hive/model_eval/tests -p 'test_*.py'

python3 -m hive.model_eval.l1.run \
  --model Qwen3-0.6B --profile G0 --workload decode \
  --seq 4096 --attention vpu --schedule serial

python3 -m hive.model_eval.l1.run \
  --model Qwen3-0.6B --profile G0 --workload decode \
  --seq 4096 --attention vpu --schedule serial --detailed \
  --json /tmp/l1_detail.json
```

## 7. 风险与处理

| 风险 | 影响 | 处理 |
|---|---|---|
| 两条成本路径分叉 | `OperatorCost` 与 `EngineCost` 数值不一致 | 只做单向派生；加等价性测试；禁止独立计算 |
| 引擎 silo 复辟 | 又按 VPU/DMA 组织公式 | helper 只放纯规划/构造；form 是唯一入口；review 检查 |
| 迁移期双入口漂移 | 旧 estimator 与新 form 数值不一致 | 每迁一个形态跑等价测试；旧入口保留到全量对齐 |
| helper 变成第二套模型 | 维护成本回升 | helper 不组装报告、不参与调度，只输出命令/端点/terms |
| DRAM 双算 | 下界和流量账目不一致 | 下界只从 `traffic` 汇总；`metrics["dram_bytes"]` 仅作对照 |
| unknown 折 0 | makespan/吞吐被低估 | `seconds=None` 传播；coverage 显式；禁止按 0 排名 |
| 下界被当成 makespan | 拓扑比较结论错误 | 统一标注 `lower_bound`；人类输出不叫 makespan；同时看 coverage |
| 删除 phase 破坏测试 | 约 93/301 条测试失败 | 第一批只增不删；S7 再迁移 |
| 新参数被误用 | 伪造场景数值 | 默认 None；0/负数报错；manifest 记录；evidence 写明 |
| 注册表绕过 kind 校验 | 非法 spec 进入调度 | 先查注册表，但 spec 类型仍由 `ENGINE_SPECS` 校验 |
| 报告继续膨胀 | 用户仍抓不到重点 | compact 默认；详细内容折叠；unknown 聚合 |

## 8. 不做的事

- 不把 L1 改成图编译器；不实现 Phase 7 ports/edges/多分支。
- 不删除 `EngineCost`/`Phase`/`Traffic`/`BufferRequest`。
- 不在本阶段改 `scheduler.py` 的资源语义。
- 不继续给 VPU 增加微架构细节（bank、burst、仲裁、流水线气泡）。
- 不引入多引擎 kind 候选集；当前“算子大概率归某引擎”的假设不变。
- 不追求所有算子完全同构；GEMM/DMA 保留共享 planner，不复制到每个 form。
- 不在第一批删除四个引擎文件；先保留兼容回退，S5b 只做搬家。
- 不改 Qwen 数值锚点、L0 口径和 `performance_analysis.md`。

## 9. 完成定义

S1–S6 完成后：

1. 默认报告能给出每个算子实例的 cost、归属引擎、DRAM/local 字节、basis、unknown；
2. 顶层给出 `max(max_e engine_busy, dram, cp)` 下界、coverage 和 top unknown，且明确标注“下界，不是可达 makespan”；
3. 未知成本不被折 0；显式场景参数可闭合 conversion/FP32 accumulation；
4. 新增算子只需在 `costing/forms/` 加一个 cost function 并注册 `(spec_kind, engine_kind) → cost_fn`；引擎文件只剩参数/兼容包装，GEMM/DMA 的共享 planner 不复制到每个 form；
5. 算子形态目录是新增算子的唯一入口，引擎文件不再承载公式目录；
6. 301 项测试和 Qwen 数值锚点不变；
7. 文档明确 L1 与 gem5 的分工，phase 明细只在 `--detailed` 出现。

## 附录 A：机器可读 Stories

```json
{
  "feature": "L1 operator-level cost evaluation",
  "source": "src/hive/doc/model_eval_opt_plan_1110.md",
  "codebase": "/Users/wangliutailong/Qoder_Proj/Chisel_Learn",
  "stories": [
    {
      "id": "S1",
      "title": "冻结 L1 基线与成本依赖清单",
      "covers": [5],
      "tasks": [
        "运行 301 项 model_eval 测试并记录结果",
        "记录 Qwen3-0.6B decode@4096 的 L0/L1、任务数、known/unknown、metrics",
        "静态统计 EngineCost 字段的生产代码与测试依赖",
        "把基线命令、锚点和依赖清单写入计划文档或测试注释"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "基线清单完成，后续改动可逐项对比且不改任何行为",
      "scopeNotes": "只记录，不改代码；不新增测试",
      "blockedBy": [],
      "techGuidance": "基线包含 301 项测试、Qwen L0/L1 锚点、默认 decode@4096 的 makespan/known/coverage。"
    },
    {
      "id": "S2",
      "title": "新增 OperatorCost 只读投影与三个下界",
      "covers": [2],
      "tasks": [
        "新建 l1/cost_view.py：OperatorCost、CostBounds、build_cost_view",
        "从 EngineCost 派生 seconds/known/dram_bytes/local_bytes/footprint/lease/unknown",
        "实现下界 max(max_e engine_busy, dram, cp)，标注 lower_bound",
        "在 L1Result.to_dict 增加 operator_costs/bounds/coverage，旧字段保留",
        "补等价性测试：投影字段一致、DRAM 不双算、unknown 不折 0"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "默认 CLI/JSON 能显示每算子 cost 和数值下界，同时 makespan 仍可为 None",
      "scopeNotes": "只读派生，不改调度器和 EngineCost 契约",
      "blockedBy": ["S1"],
      "techGuidance": "dram_bytes 只从 traffic 汇总；每引擎忙碌先求和再取 max，跨引擎不能相加；unknown 用 None 并计入 coverage。"
    },
    {
      "id": "S3",
      "title": "报告降噪：unknown 聚合与 compact 视图",
      "covers": [2, 6],
      "tasks": [
        "按 phase id 后缀/category 聚合 unknown，输出 top causes",
        "新增 compact 报告视图，CLI/HTML 默认使用",
        "保留 to_dict 默认兼容，详细内容走 --detailed",
        "把串行已知和改标注，不冒充 makespan",
        "补 unknown 聚合与 compact 视图测试"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "默认报告只显示算子 cost、bounds、coverage 和 top unknown；--detailed 仍完整",
      "scopeNotes": "先做展示层，不删字段",
      "blockedBy": ["S2"],
      "techGuidance": "聚合仅为显示，不改 EngineCost 契约；全局 layout 注记去重。"
    },
    {
      "id": "S4",
      "title": "补齐 VPU conversion / FP32 accumulation 场景参数",
      "covers": [3],
      "tasks": [
        "ModelParams 增加 vpu_conversion_elements_per_cycle 和 vpu_fp32_accum_elements_per_cycle",
        "vpu_model 在参数存在时计费，basis=scenario；缺省仍 unknown",
        "CLI 增加对应 flags，并在 manifest named_inputs 记录",
        "补测试：有参数时 known；无参数时 unknown；默认数值不变"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "显式场景参数可减少 unknown；全参数齐备的合成图上 makespan 可确定",
      "scopeNotes": "默认 None，不伪造；保持 no silent typecast",
      "blockedBy": ["S1"],
      "techGuidance": "conversion/accum 仍是显式 unknown phase，只有参数存在才给 seconds。"
    },
    {
      "id": "S5a",
      "title": "建立 costing 目录、注册表与共享服务层",
      "covers": [1, 4],
      "tasks": [
        "新建 l1/costing/registry.py：OPERATOR_ESTIMATORS、register_operator_estimator、simple_cost",
        "新建 l1/costing/services.py：从 dma_model._ServiceBuilder 提升公共 phase/traffic/buffer 构造",
        "engines.estimate 先查 (spec_kind, engine_kind)，未命中回退现有 estimator",
        "扩展 demo 用 simple_cost 注册一个新算子 kind",
        "补测试：注册、重复注册、未知 kind 回退、租约/unknown 传播"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "新算子通过 simple_cost 接入，旧 estimator 全部保持可用",
      "scopeNotes": "只建立共享层和兼容回退，不迁移现有公式",
      "blockedBy": ["S1"],
      "techGuidance": "helper 不组装报告、不参与调度；EngineCost 契约不变。"
    },
    {
      "id": "S5b",
      "title": "迁移现有算子形态；保留 GEMM/DMA 共享 planner",
      "covers": [1, 4],
      "tasks": [
        "新建 l1/costing/forms/*.py：vpu（按 spec.kind 分函数）、transfer、sampling、gemm",
        "把 vpu_model 的 spec.kind 分支逐个搬到 forms；vpu_model 收缩为兼容包装",
        "把 dma_model 的端点判定搬到 forms/transfer.py；_ServiceBuilder 已在 services.py",
        "把 gemm_model 的命令规划保留为 costing/gemm_commands.py；forms/gemm.py 调用它",
        "把 cpu_model 的 sampling 分支搬到 forms/sampling.py",
        "每迁一个形态跑现有测试与 Qwen 锚点等价对比"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "四个引擎文件不再承载算子公式目录；现有数值与测试不回退",
      "scopeNotes": "分批迁移，旧入口保留到全量对齐",
      "blockedBy": ["S5a"],
      "techGuidance": "GEMM/DMA helper 是共享规划器，不是第二套引擎模型；不要复制命令规划或端点判定到每个 form。"
    },
    {
      "id": "S6",
      "title": "CLI/Web/文档切换到新 cost 视图",
      "covers": [2, 4, 6],
      "tasks": [
        "CLI 增加 --detailed/--top-unknowns，默认展示算子 cost 表、bounds、coverage",
        "Web 报告增加同名字段/区块，旧区块折叠保留",
        "更新 spec/howto/tutorial/end2end 的调用链、字段和边界说明",
        "更新本计划文档的实施状态",
        "补充文档命令验证与 HTML smoke test"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "文档命令可直接跑出算子 cost 表、bounds 和 coverage；HTML 可读",
      "scopeNotes": "文档只描述 L1 初步 cost 与 gem5 边界",
      "blockedBy": ["S2", "S3", "S4", "S5a", "S5b"],
      "techGuidance": "不写周期级承诺；保留旧字段说明。"
    },
    {
      "id": "S7",
      "title": "可选：phase 明细降级为 --detailed",
      "covers": [6],
      "tasks": [
        "统计仍依赖默认 phase 字段的测试并迁移",
        "默认 to_dict 移除 phase 时刻/waits/buffer_plan/topology，--detailed 保留",
        "全量回归与锚点对比"
      ],
      "executionFit": "fits-one-run",
      "demoBoundary": "默认 JSON 体积显著下降，--detailed 仍可完整调试",
      "scopeNotes": "只有 S2–S6 稳定后执行",
      "blockedBy": ["S6"],
      "techGuidance": "与兼容测试冲突，必须最后做。"
    }
  ]
}
```
