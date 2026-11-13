# model_eval L1 引擎派发外部化与 convert 多引擎探索计划

日期：2026-11-12

承接 [model_eval_opt_plan_1110.md](model_eval_opt_plan_1110.md)（算子级 cost 收敛）。该计划已把 cost 收敛为「一个算子一个公式、引擎末端自算」；本计划解决两件事：

1. **引擎派发归属**：把 `graph_builder` 内部写死的辅助引擎（convert/pad/k-merge）交回 emit 层，由 user 选择派发；builder 退化为纯 typed 构造库。
2. **convert 多引擎探索**：convert 可派 VPU / DMA / GEMM-epilogue，各自的灵活度、带宽、性能是**待探索量**，由仿真扫出来，落入硬件设计建议——不以现有硬件证据为前提。

范围：`src/hive/model_eval` 的 L1 lowering、graph 契约、cost form 与扩展接口。

不在范围：L2/L3、RTL 周期模型、gem5 校准；不修改 `src/workerbee/gem5/`，不修改用户维护的 `src/hive/doc/performance_analysis.md`，不改 `src/hive/doc/model_analysis/` 下既有数值；**不暴露 CLI**（派发只走 Python API）。

---

## 0. 设计裁定（本次讨论已定）

1. **引擎派发由 emit 层 user 选，builder 纯构造。** 现状是 emitter 选主引擎、但 `builder.convert()`/`gemm()` 内部把辅助任务写死成 VPU。改为：emit 函数把引擎显式传给 builder，builder 只按传入引擎构造对应 typed spec，不自己决定。
2. **拓扑是固定输入，派发改变 binding、不改拓扑。** 选引擎影响的是任务绑定→资源争用→schedule 与跨算子复用，不是硬件拓扑本身。
3. **模型限制 ≠ 硬件定律。** GEMM 输出当前限 INT32/FP32 是建模假设（写在 `GemmSpec.__post_init__` 与 `gemm_model.estimate` 两处），不是硬件边界；GEMM 已支持 DRAM 域 operand；DMA 可做格式变换且带宽更大——这些是可放开/可探索的，不是架构障碍。
4. **convert 的引擎归属是探索量，由仿真给结果。** VPU/DMA/GEMM 各自支持哪些格式对（灵活度）、带宽、转换吞吐，由 scenario 参数扫描得出，输出是硬件设计建议（例如「DMA cvt 吞吐需 ≥ X、支持格式对 Y 才优于 VPU 路」）。**不要求先有 ForagerBee/WorkerBee 实测证据**——硬件依据仿真结果调整。
5. **unknown 不伪造仍守。** 探索 cost 用显式 scenario 参数（沿用 `vpu_conversion_elements_per_cycle` 模式）：给参→按场景计费并标「探索场景，非实测」；缺参→unknown，不折 0、不补全。下界口径仍 `max(单引擎忙碌, DRAM, 关键路径)`，报告标 lower_bound 与 coverage。
6. **自由派发必须有 dtype 安全网。** 漏做/做错 convert 要被硬校验抓住，不能静默。GEMM 边界已有校验；本计划把 convert 任务的 dtype 契约校验补全到所有承接 convert 的引擎。

---

## 1. 现状与问题（已查证）

### 1.1 谁在选引擎

- **引擎 kind 由 emitter 写死**：`emit_*` 调 `builder.gemm`/`vector`/`convert`/`dma` 即定 kind。`ctx.operation(instance, engine, kind)` 的 `engine` 参数甚至不存进 `LogicalOp`，只用于 VPU/CPU 的 dram 字节估算（[emitters/base.py:55-66](../model_eval/l1/emitters/base.py#L55)）。唯一运行时选择是 attention 的 `ctx.variant`（CLI flag），非 cost。
- **引擎 instance 由 scheduler 选**：`topology.candidates(task)` 按 `instance.kind == task.engine_kind` 过滤（[hw/topology.py:108](../model_eval/hw/topology.py#L108)），再按结束时间挑。
- **cost 各引擎末端自算**：`engines.estimate` 要求 `engine_config.kind == task.engine_kind`（[l1/engines.py:34](../model_eval/l1/engines.py#L34)），按 `(spec_kind, engine_kind)` 查 cost form。

### 1.2 builder 内部写死的辅助引擎（本计划要外部化的点）

| 位置 | 现状 | 问题 |
|---|---|---|
| `convert()` | 永远建 VPU `VpuSpec("convert")`（[graph_builder.py:84](../model_eval/l1/graph_builder.py#L84)） | user 在 emit 里选不了 DMA-cvt |
| `gemm()` pad task | 写死 VPU（[graph_builder.py:125](../model_eval/l1/graph_builder.py#L125)） | operand repack/pad 引擎不可选 |
| `gemm()` k-merge | 写死 VPU `k_accum`（[graph_builder.py:149](../model_eval/l1/graph_builder.py#L149)） | K 合并引擎不可选 |
| qwen3 join-heads | emitter 内显式 `"VPU"` task（[emitters/qwen3.py:171](../model_eval/l1/emitters/qwen3.py#L171)） | 已在 emit，但写死 |

`builder.task()` 已接收 engine 参数（[graph_builder.py:60](../model_eval/l1/graph_builder.py#L60)），是纯构造入口；形状推导（padded_n/padded_k/k_parts）是结构事实，留在 builder，不属派发。

### 1.3 dtype 安全网现状（回答「漏做 convert 能否查出」）

- **GEMM 边界已硬校验**：`TaskGraph.validate()` 检查输入 tensor dtype 必须等于 `spec.a_fmt/b_fmt`（[common/opspec.py:293](../model_eval/common/opspec.py#L293)），输出必须等于 `spec.c_dtype`（[:307](../model_eval/common/opspec.py#L307)），MX block/scale axis 也查（[:295](../model_eval/common/opspec.py#L295)）。**漏掉 GEMM 前的 quantize 或后的 dequantize → 直接报 "GEMM operand/output type mismatch"**。`TensorRef.dtype` 不符也报 "conversion must be explicit"（[:67](../model_eval/common/opspec.py#L67)）。
- **VPU/DMA/CPU 未交叉校验**：validate 只对 `GemmSpec` 分支查 dtype；`VpuSpec.input_dtype/output_dtype` 不与连接 tensor 比对；`TransferSpec` 根本没有 dtype 字段（[common/opspec.py:185](../model_eval/common/opspec.py#L185)）。
- **结论**：把 convert 派到 DMA 后，若不补 dtype 契约，确实可能漏检。且非 convert 的 VPU 任务（norm/attention）dtype 声明松散（多用默认 FP32，与实际 FP16 tensor 不符），**不能一刀切收紧所有 VPU dtype**——那会改动 conversion 判定从而动 cost。安全网只针对 **convert 任务**（其 input/output dtype 由 builder 精确设置，收紧是保锚的）。

### 1.4 GEMM 输出格式限制（两处）

- `GemmSpec.__post_init__`：`c_dtype ∈ {FP32, INT32}` 且整数族必 INT32、浮点族必 FP32（[common/opspec.py:126-129](../model_eval/common/opspec.py#L126)）。
- `gemm_model.estimate`：同样校验（[l1/gemm_model.py:259](../model_eval/l1/gemm_model.py#L259)）。
- emitter `output_dtype` 写死 INT32/FP32（[emitters/base.py:41-42](../model_eval/l1/emitters/base.py#L41)）。

放开 GEMM 输出格式（epilogue 融合 requant/dequant）需同时改这三处。

---

## 2. 目标架构

```text
ModelPlan
  → lower_plan(..., dispatch=DispatchPolicy)        # user 设派发策略
      → ctx.emit(layer)                             # emit 层：user 选引擎
          → builder.convert(..., engine=ctx.dispatch.convert)
          → builder.gemm(..., pad_engine=..., merge_engine=...)
          → builder.task(..., engine=...)           # 纯构造，按传入引擎建 spec
      → TaskGraph.validate()                        # 全引擎 convert dtype 契约（安全网）
  → estimate_graph                                  # 各引擎 cost form 末端自算
  → schedule                                        # 选 instance + 资源预约
```

- **DispatchPolicy**：user 在入口设置的派发选择，emit 函数 consulted 后传给 builder。
- **builder**：纯 typed 构造库；按传入引擎建对应 spec，不决定引擎。
- **cost form**：保持 per-binding 纯函数；探索引擎（DMA-cvt/GEMM-epilogue）用 scenario 参数。
- **validate**：convert 任务 dtype 契约补全，漏做/做错硬报错。
- 骨架（compose→emit→builder→estimate→schedule）不变；扩展 = 写/注册 emitter + 按需加 cost form。

---

## 3. Phase 1 — 派发外部化 + dtype 安全网（保锚，可独立交付）

默认全 VPU，行为与现状逐位一致；本阶段只交「选择权」与「安全网」，不引入非 VPU cost。

### 3.1 DispatchPolicy

新增于 [emitters/base.py](../model_eval/l1/emitters/base.py)：

```python
@dataclass(frozen=True)
class DispatchPolicy:
    convert: str = "VPU"     # quantize/dequantize/pack 等 dtype 转换
    pad: str = "VPU"         # operand repack / zero-pad
    k_merge: str = "VPU"     # K partial-sum 合并
    head_join: str = "VPU"   # qwen3 join-heads 拼接
```

v1 用单一 `convert` 引擎；是否按 quantize/dequantize/pack 细分见 §8 待裁定。

### 3.2 builder 改造

- `convert(op, tensor, dtype, suffix, *, engine="VPU", scale_axis=-1)`：`engine=="VPU"` 走现有 `VpuSpec("convert")`；其它引擎 Phase 1 先 `raise ValueError("<engine> conversion cost not modeled yet")`（接口就绪，cost 待 Phase 2）。
- `gemm(..., pad_engine="VPU", merge_engine="VPU")`：pad task 用 `pad_engine`、k-merge 用 `merge_engine`；VPU 默认不变，非 VPU 同样 Phase 1 先 raise。
- `task()` 不动（已纯构造）。形状推导留 builder。

### 3.3 emit 派发接线（choice 可见地落在 emit 层）

| 文件 | 函数 | 调用点 |
|---|---|---|
| generic.py | `_weight_gemm` | convert quantize-input(:27)、gemm(:30)、convert dequantize-output(:33) |
| generic.py | `emit_rms_norm` | convert quantize(:83) |
| generic.py | `emit_kv_append` | convert pack-k(:160)、pack-v(:161) |
| qwen3.py | `_emit_quantized_attention` | convert quantize-q/k/p/v(:138/142/159/162)、dequantize(:150/169)、gemm QK/AV(:147/166)、join-heads(:171) |

每处把 `engine=ctx.dispatch.<field>` 显式传入。

### 3.4 plumbing（仅 Python API，无 CLI）

- `EmitContext` 加 `dispatch: DispatchPolicy = DispatchPolicy()` 字段。
- `lower_plan(..., dispatch=None)`（[compose.py:25](../model_eval/l1/compose.py#L25)）→ 传入 ctx。
- `evaluate(..., dispatch=None)`（[timeline.py:290](../model_eval/l1/timeline.py#L290)）→ 透传 lower_plan。
- 不加 `--dispatch-*` flag。

### 3.5 dtype 安全网（保锚）

- 在 `TaskGraph.validate()` 增加 **convert 任务**交叉校验：`VpuSpec.kind=="convert"` 时，输入 tensor dtype 必须 == `spec.input_dtype`、输出 tensor dtype 必须 == `spec.output_dtype`。builder.convert/pad 已精确设置这两个字段（[graph_builder.py:87](../model_eval/l1/graph_builder.py#L87)、[:128](../model_eval/l1/graph_builder.py#L128)），故现有图全过。
- GEMM 边界校验保持不动（已覆盖 GEMM 前后漏 convert）。
- **不**收紧非 convert 的 VPU dtype 声明（norm/attention 等），避免动 conversion 判定与 cost。
- DMA convert 的 dtype 契约随 Phase 2 `TransferSpec` 加 dtype 一并落地。

### 3.6 Phase 1 锚/测试影响

- 默认 VPU → 现有 318 测试全过、图计数（423 op / 733 task / 845 tensor）不变、无数值漂移。
- 新增测试：
  - 默认 policy 与现状逐位等价（图 + cost + schedule）。
  - 非 VPU policy 在 Phase 1 抛清晰「未建模」错误。
  - convert dtype 安全网：构造一个漏做/错做 convert 的图，validate 必须报错。

---

## 4. Phase 2 — convert 多引擎探索 cost（scenario 参数化，不以硬件证据为前提）

目标：让 user 在 emit 里把 convert 派给 VPU / DMA / GEMM-epilogue，仿真扫出各引擎的灵活度/带宽/性能权衡，输出硬件设计建议。**这是设计空间探索，不是对既有硬件的复现**；cost 用 scenario 参数，缺参→unknown，证据标「探索场景」。

### 4.1 探索维度

| 引擎 | 灵活度（支持的格式对） | 带宽/吞吐 scenario 参数 | 图形态 |
|---|---|---|---|
| VPU convert | 任意 dtype↔dtype | `vpu_conversion_elements_per_cycle`（已有） | 独立 convert task |
| DMA cvt | **任意 dtype↔dtype**（user 裁定） | `dma_conversion_elements_per_cycle`（新增）；搬运带宽复用 profile 内存接口，不另设带宽参数 | 独立 transfer task，读字节≠写字节 |
| GEMM epilogue | **仅输出端**，输出集合 {MXFP8,MXFP4,FP32,FP16,BF16} | **GEMM 自身输出吞吐**（无独立参数，user 裁定） | **融合进 GEMM**，无独立 convert task，`c_dtype`=目标输出格式 |

**GEMM epilogue 能力（已纠正「只做量化」的误解）**：epilogue 在输出端，做累加器→目标输出的通用格式转换，不只是量化：

| 目标输出 | 来自 FP32 累加（MXFP8/MXFP4/BF16 输入） | 来自 INT32 累加（INT8/INT4 输入） | 性质 |
|---|---|---|---|
| FP32 | 直出（免费） | int→float | 非量化 |
| FP16 / BF16 | 精度 cast（舍入） | int→float + 舍入 | 非量化 |
| MXFP8 / MXFP4 | 求 block scale + 量化（requant） | int→float + 量化 | 量化 |

- 输入格式集合 {MXFP8,MXFP4,INT8,INT4,BF16}；累加器 INT32（整数族）/FP32（浮点族）。当前代码 `c_dtype` 只到累加器（INT32/FP32），上表输出集合是 Phase 2 目标。
- **输入端 quantize（FP32→fmt）在 GEMM 之前，epilogue 够不着，不可融合**，只能是独立 pre-GEMM op（VPU/DMA）。
- 「convert 派给 GEMM」= 让 GEMM 直接输出下游格式（省独立 convert task 与一次 DRAM 往返），涵盖 quant/cast/int→float，**不是只 quant**。因此融合用「GEMM 输出格式」驱动，不用 `fuse_quant`（名字偏窄、方向反）；若需开关名用 `fuse_epilogue`/`fuse_output_convert`。

### 4.2 契约与 cost 改动

- **TransferSpec 加 dtype**（[common/opspec.py:185](../model_eval/common/opspec.py#L185)）：`input_dtype/output_dtype` 与读/写字节（cvt 后字节可不等）；validate 对 DMA convert 交叉校验 dtype（接 §3.5 安全网）。
- **DMA-cvt cost form**（[costing/forms/transfer.py](../model_eval/l1/costing/forms/transfer.py)）：搬运带宽 + 转换服务；DMA 支持任意格式对（user 裁定），无格式对约束；缺吞吐参数→unknown。
- **GEMM 输出格式放开**：改 `GemmSpec.__post_init__`（[:126-129](../model_eval/common/opspec.py#L126)）+ `gemm_model.estimate`（[:259](../model_eval/l1/gemm_model.py#L259)）+ emitter `output_dtype`（[base.py:41](../model_eval/l1/emitters/base.py#L41)）；`c_dtype` 扩到输出集合 {MXFP8,MXFP4,FP32,FP16,BF16}。**epilogue 按 GEMM 自身输出吞吐计费，无独立吞吐参数**（user 裁定）：输出格式只改变 c 字节/beat（如 MXFP8 1B+scale vs FP32 4B），转换融进现有 write phase，不新增独立瓶颈。
- **ModelParams 加 scenario 参数**（[l1/params.py](../model_eval/l1/params.py)）：上表新增项，默认 None（unknown）。
- **emit 融合模式（输出格式驱动）**：`_weight_gemm`/`_emit_quantized_attention` 的输出转换可选「独立 convert（VPU/DMA）」或「GEMM 直接输出目标格式（epilogue 融合，省独立 task）」；由 GEMM 输出格式选择自然表达，不用 `fuse_quant`。输入 quantize 始终独立。

### 4.3 探索产出 = 硬件设计建议

- 对给定模型/工作点，扫描「convert 引擎归属 × 吞吐/带宽参数」，比较 makespan 下界、DRAM/local 字节、footprint。
- 输出形如「DMA cvt 吞吐需 ≥ X elem/cycle、支持格式对 Y，才在该工作点优于 VPU 路」「GEMM epilogue 融合省 Z 字节 DRAM 往返，但需阵列支持输出格式 W」——这些是硬件设计目标，不是实测复现。
- 守 unknown 纪律：未给 scenario 参数的路径仍 unknown，报告标 coverage 与 lower_bound，不伪造确定吞吐。

### 4.4 Phase 2 锚/测试影响

- 每条非默认路径（DMA-cvt、GEMM-epilogue）建自己的锚（给定 scenario 参数下的 cost/字节/下界）。
- 默认 VPU 路径锚不动。
- 新增「缺参→unknown」「格式对不支持→拒绝/unknown」「融合后图无独立 convert task 且 dtype 连续」测试。

---

## 5. 文件改动面

| 文件 | Phase 1 | Phase 2 |
|---|---|---|
| `common/opspec.py` | validate 加 convert dtype 交叉校验 | TransferSpec 加 dtype；GemmSpec c_dtype 放开 |
| `l1/graph_builder.py` | convert/gemm 加 engine 参数 | convert(engine="DMA") 构造 TransferSpec-cvt |
| `l1/emitters/base.py` | DispatchPolicy + EmitContext.dispatch | output_dtype 可配；融合模式 |
| `l1/emitters/generic.py` | 派发接线 | dequantize 融合选项 |
| `l1/emitters/qwen3.py` | 派发接线 | quantized attention 融合选项 |
| `l1/compose.py` | lower_plan dispatch 参数 | — |
| `l1/timeline.py` | evaluate dispatch 参数 | — |
| `l1/params.py` | — | DMA-cvt / GEMM-epilogue scenario 参数 |
| `l1/costing/forms/transfer.py` | — | DMA-cvt cost |
| `l1/costing/forms/gemm.py` + `l1/gemm_model.py` | — | epilogue/输出格式 cost + c_dtype 放开 |
| `tests/` | 默认等价 + 非 VPU 拒绝 + convert dtype 守卫 | 各探索路径锚 + unknown/格式对/融合测试 |

---

## 6. 数值锚与回归纪律

- Phase 1 默认 VPU：318 测试全过，图计数 423/733/845 不变，L0 596588544、array_cycles 19788544、b_logical_bytes 595984384、c_rows 31000、norm_calls 729、util 1/17 等锚不动。
- Phase 2 非默认路径单独建锚；默认路径锚始终不动。
- 每阶段跑全量 `PYTHONPATH=src python3 -m unittest discover -s src/hive/model_eval/tests -t src`，确认绿后再交付。
- 不改 `model_analysis/` 既有数值、不改 `performance_analysis.md`、不动 gem5。

---

## 7. 验收

- Phase 1：user 可在 `lower_plan`/`evaluate` 传 DispatchPolicy；emit 层可见派发选择；builder 不再写死辅助引擎；默认行为逐位等价；漏做/错做 convert 被 validate 硬抓；非 VPU 派发抛清晰未建模错误。
- Phase 2：convert 可派 VPU/DMA/GEMM-epilogue 并得到 scenario cost；缺参 unknown、不伪造；扫描产出可落入硬件设计建议；默认 VPU 锚不变。

---

## 8. 裁定记录

### 已裁定（2026-11-12）

1. **DispatchPolicy 粒度**：v1 用单一 `convert` 引擎字段；Phase 2 再按需细分。
2. **GEMM epilogue 能力纠正**：epilogue 不只做量化，而是输出端通用格式转换（→MX 量化 / →FP16/BF16 cast / →FP32 int→float），输出集合 {MXFP8,MXFP4,FP32,FP16,BF16}；输入端 quantize 不可融合（见 §4.1）。
3. **融合表达**：不用 `fuse_quant`（偏窄、方向反）；改为「GEMM 输出格式」驱动——emit 让 GEMM 直接输出下游格式即融合，省独立 convert task。若需开关名用 `fuse_epilogue`/`fuse_output_convert`。
4. **DMA cvt 灵活度**：支持任意格式转换（user 裁定），无格式对约束。
5. **派发只走 Python API，不暴露 CLI**。
6. **Phase 2 起步顺序**：先 GEMM-epilogue（省 DRAM 往返收益直观、复用现有 GEMM cost 骨架），DMA-cvt 随后。
7. **epilogue 吞吐 = GEMM 自身吞吐**（user 裁定）：无独立 `gemm_epilogue_elements_per_cycle` 参数；输出格式只改 c 字节/beat，转换融进现有 write phase。
8. **DMA-cvt 带宽复用 profile，唯一新增探索量是转换元素率**（2026-11-12 实施裁定）：读/写搬运按 profile 的 dram/local 接口带宽计费（与纯搬运同口径），不新增 `dma_cvt_effective_bytes_per_s`；转换服务用 `dma_conversion_elements_per_cycle`（缺参 unknown）。read→conversion→write 串行计费，不伪造流水重叠（与纯搬运串行纪律一致）；流水化留作后续 overlap scenario。

### 实施状态

- **Phase 1 已落地（2026-11-12）**：DispatchPolicy（convert/pad/k_merge/head_join，默认 VPU）+ EmitContext.dispatch；builder.convert 加 engine、gemm 加 pad_engine/merge_engine（非 VPU 抛未建模错误）；generic/qwen3 各派发点接 ctx.dispatch；lower_plan/evaluate 加 dispatch 参数（仅 Python API）；validate 补 convert 任务 dtype 契约。全量回归 325 测试绿（318 原有 + 7 新），默认 VPU 行为与数值锚逐位不变。
- **Phase 2a 已落地（2026-11-12）**：GEMM 非 MX 输出格式 {FP32,FP16,BF16} + epilogue 融合。`GemmSpec.c_dtype` 放开为输出格式（INT32 仅整数输入；FP32/FP16/BF16 两族皆可），opspec.post_init 与 gemm_model.estimate 两处校验同步；输出字节/beat 按 c_dtype 参数化（FP32/INT32=4B 不变，FP16/BF16=2B），epilogue 按 GEMM 自身输出吞吐计费（无新 phase/参数）；DispatchPolicy 加 `gemm_output` 旋钮，`_weight_gemm` 用它设 GEMM 输出格式再转 FP32（已 FP32 则 no-op，消除整数 dequant task）；compose 入口拒绝未建模输出（MX/INT32）。全量回归 329 测试绿（325 + 4 新 epilogue），默认锚逐位不变。
- **Phase 2b 已落地（2026-11-12）**：MX 输出 {MXFP8,MXFP4} requant。`c_dtype` 与 estimate/compose 校验放开 MX；输出字节 = data + scale（与 `Tensor.size_bytes` 同口径，scale 沿 N 轴 `c.m*ceil(c.n/block_size)`），输出 beat = data beat + scale beat，`c_beats` metric 用累计实际值覆盖以含 scale；epilogue 按 GEMM 自身输出吞吐计费（无新参数）；内部 C 窗口缓冲仍累加器 4B 不变。MX+k-split 的 k_accum 走 unknown（不伪造）。全量回归 332 测试绿（329 + 3 新 MX），默认锚逐位不变。
- **Phase 2c 已落地（2026-11-12）**：DMA-cvt（任意格式对，搬运中转换，读≠写字节）。`TransferSpec` 加 `input_dtype/output_dtype/read_bytes/write_bytes/elements`（默认空/None，纯搬运路径逐位不变）；`builder.convert(engine="DMA")` 构造 converting TransferSpec（同 memory_domain，stream_id=task id，read/write 字节取源/目标 `Tensor.size_bytes`）；`transfer.py` 隔离 `_estimate_conversion` 分支（input/output dtype 均非空时触发）：source_read→conversion→destination_write 串行（不伪造流水），读/写字节分别计费，转换吞吐用 scenario 参数 `dma_conversion_elements_per_cycle`（缺参→unknown，不折 0）；**搬运带宽复用 profile 内存接口（dram/local），不新增 `dma_cvt_effective_bytes_per_s`**（与 GEMM epilogue「复用自身吞吐」同口径，唯一新增探索量是转换元素率）；validate 补 DMA convert dtype 交叉校验；params 加 scenario 参数 + named_inputs。派发复用 Phase 1 接线（`engine=ctx.dispatch.convert`），无需改 emitter。全量回归 344 测试绿（332 + 12 新：cost form 5 + dispatch 5 + graph 契约 2），默认 VPU 与纯搬运锚逐位不变。探索验证：1 层 plan `convert="DMA"` + `dma_conversion_elements_per_cycle=16` 使 coverage tasks_known 7→22、unknown 24→9；FP32→MXFP8 quantize 读 4096B/写 1056B（读≠写）。
- **后续**：convert 引擎归属 × 吞吐/带宽参数扫描，产出硬件设计建议（§4.3）；pad/k-merge/head-join 的非 VPU 派发仍抛未建模错误（按需扩展）。