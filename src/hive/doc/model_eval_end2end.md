# Qwen3-0.6B：model_eval L0/L1 端到端代码导读

本文以一次固定输入追踪 model_eval 的完整执行过程，说明模型如何变成 ModelPlan、LogicalOp、PrimitiveTask、EngineCost、ScheduleResult 和最终报告。重点不是操作步骤，而是**代码在哪里循环、每次调用改变什么数据结构、哪些数值在哪一层产生**。

> 模型结构由层注册表（`frontend/layers`）与 `ModelPlan` 表达，模型名通过中立 `frontend/registry` 解析；L1 展开走 `l1/compose.lower_plan()` 递归调用 `l1/emitters`。旧的 `ModelSpec`/`map_model()`/`lower_model()` 兼容路径已移除，本文所有代码引用均指向当前实现。plan 路径的接口与扩展方式见[建模规范 §2.3/§7](model_eval_spec.md)。

固定算例为：Qwen3-0.6B、G0、MXFP8、decode、`S=0`、batch=1、VPU attention、单 active core、每类引擎一个实例、stream/stream、serial。该算例用于展示代码路径，不表示实测硬件性能。

| 阅读目标 | 文档 |
|---|---|
| 第一次运行工具 | [入门教程](model_eval_tutorial.md) |
| 比较工作点、多实例、驻留或自定义图 | [操作指南](model_eval_howto.md) |
| 查询字段、公式和 CLI 参数 | [建模规范与接口参考](model_eval_spec.md) |
| 理解抽象分工与设计取舍 | [设计说明](model_eval_explanation.md) |
| 跟踪 Qwen 代码执行过程 | 本文 |

## 1. 全局调用关系

L0 和 L1 有独立 CLI 入口。相同模型配置会分别进入两条计算链，不存在“L0 完成后才能进入 L1”的调用关系。

```text
公共输入
  ├─ get_model("Qwen3-0.6B")        → ModelPlan（registry → models/qwen3_06b.build）
  ├─ get_profile("G0")              → HwProfile
  ├─ FORMATS["MXFP8"]              → NumFormat
  ├─ decode(0, batch=1)             → WorkloadPoint
  └─ load_array_geometry()           → ArrayGeometry

L0
  l0.run.main()
    → 每个 WorkloadPoint 调用 roofline.evaluate()
      → work_account()              → Account(MAC)
      → traffic_account()           → Account(bytes)
      → 两条 roofline 取 max         → L0Result
    → bottleneck.attribute()
    → manifest / 终端 / JSON / HTML

L1
  l1.run.main()
    → ModelParams / Topology / ExecutionPlan
    → 每个 WorkloadPoint × attention variant 调用 timeline.evaluate()
      → compose.lower_plan()           → ModelPlan 递归 emitter → TaskGraph
      → BufferPlan.from_graph()      → buffer 生命周期
      → estimate_graph()             → task × instance → EngineCost
      → _handoffs()                  → 跨引擎依赖前缀 Phase
      → dispatchers.dispatch()       → serial/list 或注入策略
        → _project()                 → 条件投影
        → _actual()                  → actual replay
      → 汇总 metrics / L0 参考        → L1Result
    → manifest / 终端 / JSON schema 2 / HTML
```

对应的编排入口分别是 [l0/run.py:59](../model_eval/l0/run.py#L59)、[l1/run.py:56](../model_eval/l1/run.py#L56) 和 [l1/timeline.py:241](../model_eval/l1/timeline.py#L241)。

## 2. 公共输入如何建立

### 2.1 CLI 将字符串变成具名对象

L0 在 [l0/run.py:59-79](../model_eval/l0/run.py#L59) 中完成输入构造：

```python
seqs = [int(s) for s in args.seq.split(",") if s.strip() != ""]
model = get_model(args.model)
profile = get_profile(args.profile)
fmt = FORMATS[args.fmt]
geometry = load_array_geometry()
points = _points(args.workload, seqs)
```

`--seq 0,32,4096` 因而不是一个循环生成过程，而是三个独立 WorkloadPoint。`_points()` 根据 workload 选择 `decode()`、`prefill()` 或 `frame()`；WorkloadPoint 构造器检查 S 和 batch 的基本合法性，见 [common/workload.py:15](../model_eval/common/workload.py#L15)。

L1 额外构造三类物理/场景输入，见 [l1/run.py:62-84](../model_eval/l1/run.py#L62)：

```python
params = ModelParams(...)
points = [point_factory(seq, args.batch) for seq in seqs]
topology = explicit_topology(...)
plan = ExecutionPlan(args.weight_policy, args.activation_policy)
variants = ATTENTION_VARIANTS if args.attention == "both" else (args.attention,)
results = [evaluate(...) for point in points for variant in variants]
```

最后一个列表推导是二维笛卡尔积：每个工作点分别评估每个 attention variant。`both` 不会把两个 variant 合并成一条执行流水线。

### 2.2 Qwen builder 将配置组合成 ModelPlan

Qwen 配置定义在 [frontend/models/qwen3_06b.py:18-26](../model_eval/frontend/models/qwen3_06b.py#L18)：

| 符号 | 值 | 含义 |
|---|---:|---|
| H | 1024 | hidden size |
| I | 3072 | SwiGLU intermediate size |
| V | 151936 | vocab size |
| L | 28 | decoder block 数 |
| n_h | 16 | query heads |
| n_kv | 8 | KV heads |
| d_h | 128 | head dim |

`build()` 在 [frontend/models/qwen3_06b.py:29-76](../model_eval/frontend/models/qwen3_06b.py#L29) 中**不写任何闭式公式**，只把已注册的层组合成一个 `ModelPlan`：

```python
block = Qwen3BlockGeometry(hidden=H, heads=n_h, kv_heads=n_kv,
                           head_dim=d_h, intermediate=I)
stack = tuple(LayerInstance(id=f"block-{i:02d}", type="qwen3.decoder_block",
                            geometry=block) for i in range(L))
head = (final-norm, lm-head, sampling)   # generic.rms_norm / lm_head / sampling
return ModelPlan(name="Qwen3-0.6B", layers=stack + head,
                 allocations=(WeightAllocation("embedding", (V, H),
                                               checkpoint_copies=2),), ...)
```

参数量与 MAC 系数不在模型文件里，而在层定义的 L0 闭式中（[frontend/layers/qwen3.py](../model_eval/frontend/layers/qwen3.py)、[frontend/layers/generic.py](../model_eval/frontend/layers/generic.py)）。`ModelPlan` 通过 `iter_leaves()` 遍历叶子层并求和，得到与附录 B 一致的聚合量：

| 量 | 计算 | 结果 |
|---|---|---:|
| q_proj 参数 | 1024×2048 | 2,097,152 |
| k/v_proj 参数合计 | 2×1024×1024 | 2,097,152 |
| o_proj 参数 | 1024×2048 | 2,097,152 |
| attention 部分含 norm 参数 | 上式合计 | 6,293,760 |
| MLP 参数 | 3×1024×3072 | 9,437,184 |
| 单 block 参数 | attn+mlp | 15,730,944 |
| embedding / lm_head | 151936×1024 | 155,582,464 |
| 全模型唯一参数 | 28×layer+emb+H | 596,049,920 |

`qwen3.decoder_block` 是聚合层：它本身不拥有权重，`children()` 展开为 attention（q/k/v/o_proj、qk_norm、rope、kv_transfer、qk/softmax/av）与 SwiGLU MLP（gate/up/down_proj、silu_gate）以及两个 residual。每个叶子层的 L0 给出：

- `mac_fixed=15,750,144`（单 block 合计），表示每 block、每 token 与上下文无关的 MAC 等价量；
- `mac_s_coef=4112`（单 block 合计），表示 decode 的 S 线性项；
- `mac_s2_coef=2048`（单 block 合计），表示 prefill 的 QK/AV 因果二次项；
- `kv_bytes_per_token=4096`（单 block），全模型因此为 28×4096=114,688 B/token。

七个投影权重形状由层 geometry 决定，直接决定后续 GEMM 形状：

| 权重 | 存储 `[N,K]` | 逻辑计算 |
|---|---|---|
| q_proj | `[2048,1024]` | `[M,1024] @ [1024,2048]` |
| k_proj | `[1024,1024]` | `[M,1024] @ [1024,1024]` |
| v_proj | `[1024,1024]` | `[M,1024] @ [1024,1024]` |
| o_proj | `[1024,2048]` | `[M,2048] @ [2048,1024]` |
| gate_proj | `[3072,1024]` | `[M,1024] @ [1024,3072]` |
| up_proj | `[3072,1024]` | `[M,1024] @ [1024,3072]` |
| down_proj | `[1024,3072]` | `[M,3072] @ [3072,1024]` |

`get_model()` 只是中立注册表查询并调用 builder，见 [frontend/registry.py:36-42](../model_eval/frontend/registry.py#L36)。lm_head 的权重通过 `allocation="embedding"` 与 embedding 共享同一份唯一参数，`checkpoint_copies=2` 表示 checkpoint 中存了两份。这里没有读取 checkpoint，也没有构造 PyTorch module。

### 2.3 NumFormat、HwProfile 与 ArrayGeometry 提供不同事实

三者不能互相替代：

- NumFormat 给出元素字节、scale 平均字节和 MAC family；MXFP8 精确参数字节为 `1+1/32=33/32`。
- HwProfile 给出 DRAM 有效带宽、MAC 峰值、频率、SRAM 容量和 local 接口速率；G0 的 DRAM 为 17.064 GB/s，频率为 500 MHz。
- ArrayGeometry 给出阵列 tile、命令维度限制、DMA 拍宽和 buffer 深度；当前 `tile_n=16`、`max_m=48`、A/B/C DMA 均为 32 B/beat。

profile 回答“速率是多少”，geometry 回答“命令和窗口怎样形成”，NumFormat 回答“每个元素占多少存储以及查哪一族峰值”。

## 3. L0：从 ModelPlan 到闭式 roofline

### 3.1 CLI 对每个工作点独立调用 evaluate

[l0/run.py:76-81](../model_eval/l0/run.py#L76) 的循环只有两层含义：

```python
results = [
    evaluate(model, profile, fmt, point, use_doc_bytes=not args.exact_bytes)
    for point in points
]
attributions = [bottleneck.attribute(r, balance) for r in results]
```

第一个列表推导逐个生成 L0Result；第二个列表推导只做瓶颈归因，不重新计算 MAC 或流量。为了与 L1 内部的精确字节参考一致，本算例对应 `--exact-bytes`。

### 3.2 work_account 按 phase 汇总 MAC

[l0/accounts.py:31-62](../model_eval/l0/accounts.py#L31) 从 ModelPlan 取出聚合系数（这些方法对叶子层 L0 求和）：

```python
body_fixed = model.body_mac_fixed()
body_s = model.body_mac_s_coef()
body_s2 = model.body_mac_s2_coef()
S = point.seq_len
batch = point.batch
```

decode 分支生成四个分项：

```python
parts = {
    "body_fixed": body_fixed * batch,
    "body_context_linear": body_s * S * batch,
    "final_norm": model.final_norm_mac * batch,
    "lm_head": model.lm_head_mac * batch,
}
```

当前 `S=0`、batch=1，所以线性项为零：

| 分项 | 数值 |
|---|---:|
| 28 block fixed | 441,004,032 |
| final norm | 2,048 |
| lm_head | 155,582,464 |
| 合计 | 596,588,544 MAC |

prefill 分支不会复用 decode 公式，而是计算 `(body_fixed+final_norm)×S + body_s2×S² + lm_head`；frame 又走独立固定工作量分支。这里的 branch 是工作点口径选择，不是模型逐层执行循环。

### 3.3 traffic_account 按 phase 汇总字节

[l0/accounts.py:65-91](../model_eval/l0/accounts.py#L65) 的 decode 分支为：

```python
parts = {
    "weights": model.params_streamed_per_token * bpp,
    "kv_read": model.kv_bytes_per_token * S * batch,
    "recurrent_state": model.state_bytes * batch,
}
```

当前 `S=0`、state=0，只剩权重：

```text
596,049,920 × 33/32 = 614,676,480 B
```

这里假设本步权重只读一次，batch 不放大权重流量。它不追踪 Q/K/V 是否共享量化 A，也不计算 tile 重装、local 端口或显式转换；这些属于 L1。

### 3.4 roofline.evaluate 只组合两个总量

[l0/roofline.py:46-92](../model_eval/l0/roofline.py#L46) 是 L0 的核心：

```python
work = work_account(model, point)
traffic = traffic_account(model, fmt, point, use_doc_bytes=use_doc_bytes)
bw = profile.dram_effective_bytes_per_s
mac_rate = profile.mac_per_second(fmt.mac_family)
t_bw = traffic.total / bw
t_mac = work.total / mac_rate
t = max(t_bw, t_mac)
```

G0 的 MXFP8 family 峰值为：

```text
512 MAC/cycle/core × 500 MHz × 2 cores = 512×10⁹ MAC/s
```

因此当前算例为：

| 输出 | 数值 | 含义 |
|---|---:|---|
| MAC | 596,588,544 | 全模型工作量 |
| traffic | 614,676,480 B | 全模型 DRAM 口径流量 |
| t_compute | 1.165212 ms | 计算理想时间 |
| t_bandwidth | 36.021828 ms | DRAM 理想时间 |
| t_s | 36.021828 ms | 两者 max，理想时间下界 |
| throughput | 27.7609 tok/s | 相应吞吐上界 |

`bottleneck.attribute()` 随后比较机器平衡点与算术强度。终端和 JSON 只是序列化同一个 L0Result，见 [l0/run.py:100-129](../model_eval/l0/run.py#L100) 和 [l0/run.py:205-227](../model_eval/l0/run.py#L205)。

## 4. L1 入口：先固定拓扑与执行计划

### 4.1 explicit_topology 注册物理实例

[l1/run.py:74-79](../model_eval/l1/run.py#L74) 在进入模型展开前创建 Topology。[hw/topology.py:157-201](../model_eval/hw/topology.py#L157) 的 `_build()` 完成以下工作：

1. 注册一个共享 DRAM bandwidth resource 和一个共享 DRAM domain；
2. 对每个 active core 注册一个 local slot 和一个私有 SRAM domain；
3. 对 GEMM/VPU/CPU/DMA 的实例计数逐层循环；
4. 为每个实例注册 engine slot，并建立资源别名；
5. 为 GEMM 实例注册独立 scale A/B port；
6. 返回 `Topology(instances, resources, domains, ...)`。

核心循环位于 [hw/topology.py:170-200](../model_eval/hw/topology.py#L170)：

```python
for kind, count in counts.items():
    for index in range(count):
        core = cores[index % len(cores)]
        instance_id = f"{kind.lower()}{index}"
        ...
        instances[instance_id] = EngineConfig(...)
```

这段循环只注册资源，不复制模型任务。当前算例得到 `gemm0`、`vpu0`、`cpu0`、`dma0`，全部属于 core0。

### 4.2 timeline.evaluate 是完整模型总编排

[l1/timeline.py:290-341](../model_eval/l1/timeline.py#L290) 按固定顺序执行：

```python
graph = lower_plan(model, fmt, point, attention=attention, ...)
buffer_plan = buffer_plan or BufferPlan.from_graph(graph, plan)
costs = estimate_graph(graph, topology, plan)
scheduled = evaluate_graph(graph, topology, ..., costs=costs)   # 内部 _handoffs + dispatch
...
l0 = evaluate_l0(model, profile, fmt, point, use_doc_bytes=False)
...
return L1Result(...)
```

关键顺序是：

- 图必须先于 buffer 生命周期建立；
- 每个任务的成本必须先于 handoff 注入和调度；
- L0 参考在 L1 调度完成后才计算，且只写入参考字段；
- 最终 L1Result 同时保存 graph、topology、plan、schedule 和 costs，报告不需要重新推导。

## 5. 第一次展开：ModelPlan → LogicalOp（emitter 递归）

### 5.1 lower_plan 与 EmitContext 派发

[l1/compose.py:25-45](../model_eval/l1/compose.py#L25) 的 `lower_plan()` 先 `plan.validate()`，再建立一个 `GraphBuilder` 和 `EmitContext`，然后顺序遍历顶层层：

```python
builder = GraphBuilder(block_size)
ctx = EmitContext(plan, point, fmt, geometry, attention, builder)
current = builder.tensor("input:hidden", (ctx.rows, _hidden_width(plan)), "FP32", external=True)
inputs = {"hidden": current}
for layer in plan.layers:
    outputs = ctx.emit(layer, inputs)
    inputs = {**inputs, **outputs}
```

`ctx.rows` 在 [l1/emitters/base.py:36-38](../model_eval/l1/emitters/base.py#L36) 定义为 `batch × (seq if prefill else 1)`；当前 decode batch=1，因此 rows=1。`ctx.emit()` 按 `(layer.type, layer.version)` 查 `EMITTERS` 注册表并调用对应 emitter；未注册则抛 `MissingL1Coverage`，不会静默回退。

emitter 分两类：

- **聚合 emitter**（如 `qwen3.decoder_block`、`qwen3.gqa_attention`、`qwen3.swiglu_mlp`）只负责把子层按数据流连起来，通过 `ctx.emit(ctx.child(instance, "q_proj"), {"x": hidden})` 递归派发；
- **叶子 emitter**（如 `generic.linear`、`generic.rms_norm`、`generic.softmax`）调用 `ctx.operation()` 生成一个 LogicalOp，再调用 `builder.vector/gemm/task` 生成任务。

`ctx.operation()` 在 [l1/emitters/base.py:55-66](../model_eval/l1/emitters/base.py#L55) 统一构造 LogicalOp，并为 VPU/CPU 补齐 DRAM 读写字段。此处的 `engine` 是后续任务分解的语义提示，不代表已经生成一个且仅一个物理任务。

### 5.2 28 个 block 实例生成什么

`plan.layers` 含 28 个 `qwen3.decoder_block` 实例（`block-00` 到 `block-27`）。[l1/emitters/qwen3.py:16-27](../model_eval/l1/emitters/qwen3.py#L16) 的 `emit_decoder_block()` 依次派发 input_norm → attention → attention_residual → post_norm → mlp → mlp_residual。每个 block 的逻辑算子顺序如下：

| 顺序 | LogicalOp | 当前 S=0 的主要形状或工作量 |
|---:|---|---|
| 1 | input_norm | 1×1024，一个向量 |
| 2 | q_proj | M=1,N=2048,K=1024 |
| 3 | k_proj | M=1,N=1024,K=1024 |
| 4 | v_proj | M=1,N=1024,K=1024 |
| 5 | qk_norm | 24 个长度 128 的 head 向量 |
| 6 | rope | Q/K 共 3072 元素 |
| 7 | kv_transfer | 当前 token 的 K/V 写入 |
| 8 | qk | 仅 S>0 生成 |
| 9 | softmax | 仅 S>0 生成 |
| 10 | av | 仅 S>0 生成 |
| 11 | o_proj | M=1,N=1024,K=2048 |
| 12 | attention_residual | 1024 元素 |
| 13 | post_norm | 1×1024，一个向量 |
| 14 | gate_proj | M=1,N=3072,K=1024 |
| 15 | up_proj | M=1,N=3072,K=1024 |
| 16 | silu_gate | 3072 元素 |
| 17 | down_proj | M=1,N=1024,K=3072 |
| 18 | mlp_residual | 1024 元素 |

S=0 时跳过 qk/softmax/av，因此每 block 为 15 个 LogicalOp；S>0 时为 18 个。attention variant 只改变 qk/av 的语义引擎和 GemmGroup，softmax 始终为 VPU。叶子 op ID 形如 `block-00/q_proj`、`block-00/silu_gate`（聚合层 scope 不进入叶子 op 名）。

### 5.3 模型尾部增加三个分组

顶层层在 28 个 block 之后还有 final-norm、lm-head、sampling，分别由 `generic.rms_norm`、`generic.lm_head`、`generic.sampling` emitter 展开：

```text
final-norm/norm       VPU
lm-head/projection    GEMM, M=batch,N=151936,K=1024
sampling/sampling     CPU, vectors=batch,length=151936
```

因此当前 LogicalOp 总数为：

```text
28 × 15 + 3 = 423
```

这些对象尚未包含 tensor 生产者、任务依赖、GEMM 命令或物理实例。

## 6. 第二次展开：LogicalOp → TaskGraph（GraphBuilder）

### 6.1 GraphBuilder 维护 producer 和 tensor 注册表

[l1/graph_builder.py:18-27](../model_eval/l1/graph_builder.py#L18) 的 `GraphBuilder` 保存四组状态：

```python
self.tasks = []
self.tensors = {}
self.producers = {}
self.padded_operands = {}
```

`tensor()` 注册 Tensor；`task()` 是依赖形成的核心：

```python
deps = tuple(dict.fromkeys((*(self.producers[t] for t in inputs
                             if t in self.producers), *dependencies)))
...
self.producers[result] = id
self.tasks.append(PrimitiveTask(...))
```

每次创建任务时发生两件事：

1. 输入 tensor 若已有 producer，该 producer 自动成为依赖祖先；
2. 输出 tensor 注册当前 task ID，供后续消费者继承依赖。

因此 DAG 主要来自 tensor 生产/消费关系，而不是 scheduler 猜测算子顺序。`dict.fromkeys()` 只负责去重并保持顺序。

### 6.2 convert、gemm、vector、dma 是 builder 方法

叶子 emitter 不直接拼 PrimitiveTask，而是调用 builder 的四个方法：

- `convert()`（[l1/graph_builder.py:77-90](../model_eval/l1/graph_builder.py#L77)）：dtype 已相同且 MX scale 轴一致时直接返回原 tensor，不生成任务；否则生成一个 VPU `convert` 任务，其服务保持未知，并注册新 tensor 和 producer。
- `vector()`（[l1/graph_builder.py:92-100](../model_eval/l1/graph_builder.py#L92)）：用 `VpuSpec` 创建一个 VPU 任务。
- `gemm()`（[l1/graph_builder.py:102-163](../model_eval/l1/graph_builder.py#L102)）：合法化、padding、GEMM 任务与可选 k_accum，见 6.4。
- `dma()`（[l1/graph_builder.py:165-178](../model_eval/l1/graph_builder.py#L165)）：创建 DMA transfer 任务并注册输出 producer。

权重 GEMM 的组合模式在 [l1/emitters/generic.py:26-33](../model_eval/l1/emitters/generic.py#L26) 的 `_weight_gemm()`：

```python
a = ctx.builder.convert(op, a, ctx.fmt.name, "quantize-input")
b = ctx.builder.tensor(op.id + ":weight", (k, n), ctx.fmt.name, external=True, scale_axis=0)
out = ctx.builder.gemm(op, a, b, GemmSpec(...), ctx.geometry)
return ctx.builder.convert(op, out, "FP32", "dequantize-output")
```

它描述“激活按需量化→external 权重→GEMM→按需转 FP32”。是否真正产生 convert 任务由输入输出 dtype 决定，不能仅从函数名推断任务数。norm emitter 在有系数时先创建 DMA coefficient transfer，再创建 VPU norm，并将 DMA 加入 norm 依赖。

### 6.3 一个 decoder block 的数据流

聚合 emitter 递归派发后，当前 block 的 tensor 流为：

```text
current
  → input_norm
  → 一次共享量化 A ─→ q_proj ─→ Q ─→ Q norm ─→ Q RoPE ─┐
                      k_proj ─→ K ─→ K norm ─→ K RoPE ─┤
                      v_proj ─→ V ──────────────────────┤
                                                        → FP16 pack / KV append
  → S=0 empty attention context（跳过 QK/softmax/AV）
  → o_proj
  → attention_residual(residual, projected)
  → post_norm
  → 一次共享量化 A ─→ gate_proj ─┐
                      up_proj ───┤→ silu_gate → down_proj
  → mlp_residual(residual2, down)
  → 新的 current
```

几个容易误读的细节：

- Q/K/V 的 `_weight_gemm()` 接收同一个量化 tensor，不会生成三份量化 A；
- gate/up 共享另一份量化 A；
- K/V 先显式转为 FP16，再由 DMA append 到 KV allocation；
- 当前 S=0 时创建 external empty-attention-context 以保留后续 o_proj 数据流，这只是结构基线，不是数值前向；
- `current` 在每个 block 末尾替换为新的 residual 输出，下一个 block 的 producer 链由此延续。

S>0 时，VPU attention 直接构造 QK→softmax→AV；quantized GEMM 路径还在 batch/head 双重循环中创建 Q/K/V views、逐 head QK/AV、概率量化和 head join，见 [l1/emitters/qwen3.py:75-176](../model_eval/l1/emitters/qwen3.py#L75)。

### 6.4 GEMM 在 builder 中先做合法化和 padding

`builder.gemm()` 在真正创建 GEMM 任务前调用共享命令规划：

```python
commands = split_commands(GemmShape(...), geometry, policy)
k_parts = len({command.k_offset for command in commands})
padded_n = ...
padded_k = ...
```

`split_commands()`（[l1/split_policies.py](../model_eval/l1/split_policies.py)）默认调用 `gemm_model.legalize()`，builder 与 estimator 共用同一入口，避免两处合法化漂移。随后：

1. 分别检查 A/B storage shape；
2. 若逻辑形状小于 padded storage，创建显式 VPU repack/zero-pad；
3. 为原逻辑区域建立指向 padded base 的 view；
4. 将 storage shape 写回 GemmSpec；
5. 创建一个 GEMM PrimitiveTask；
6. K 被拆成多个部分时，再创建 VPU `k_accum`；
7. N 有 padding 时，为逻辑输出建立 view。

因此“一个 weight LogicalOp”可能对应 convert、pad、GEMM、k_accum 等多个任务；对齐且 dtype 已匹配时也可能只剩一个 GEMM 任务。

### 6.5 模型尾部与图校验

顶层 emitter 依次创建 final norm、lm_head 和 CPU sampling。prefill 会先用带步长的 view 选择每个序列最后一个位置，再进入 lm_head；decode 不需要该 view。

`lower_plan()` 最后：

```python
graph = TaskGraph(tuple(builder.operations), tuple(builder.tasks),
                  tuple(builder.tensors.values()), ...)
graph.validate()
```

当前算例得到：

```text
423 LogicalOp
733 PrimitiveTask
845 Tensor
```

[common/opspec.py:219-345](../model_eval/common/opspec.py#L219) 的 `TaskGraph.validate()` 再执行结构检查：

- 字典构造与长度比较检查重复 ID；
- tensor view 检查 base、dtype、domain、切片和 stride；
- `while pending` 拓扑循环检查依赖环并建立 ancestors；
- 每个输入引用检查 shape/dtype 和生产者覆盖；
- writers 双重循环拒绝无依赖顺序的重叠写；
- transfer stream 检查唯一 owner。

只有校验通过的图才进入 BufferPlan 和成本估算。

## 7. 具体任务：q_proj 如何变成窗口和阶段预算

### 7.1 当前 q_proj 为什么只有一个原子任务

当前 `block-00/q_proj` 的逻辑形状为：

```text
M=1, N=2048, K=1024
A: [1,1024] MXFP8
B: [1024,2048] MXFP8
C: [1,2048] FP32
semantic MAC = 1×2048×1024 = 2,097,152
```

N/K 已对齐，C 已为 FP32，A 的共享量化由 `block-00/input_norm/quantize` 完成，所以 q_proj 自身只需要：

```text
block-00/q_proj/execute
  engine_kind = GEMM
  dependency  = block-00/input_norm/quantize
  input A     = block-00/input_norm/quantize:out
  input B     = block-00/q_proj:weight
  output C    = block-00/q_proj/execute:out
```

这说明 LogicalOp 与 PrimitiveTask 不是固定一对一，也不是固定一对多；实际数量由共享转换、dtype、padding 和 K 拆分共同决定。

### 7.2 legalize 的 M/N/K 三重循环

[l1/gemm_model.py:121-169](../model_eval/l1/gemm_model.py#L121) 先检查格式，再计算：

```python
p_a = int(Fraction(2) / format_elem_bytes(shape.a_fmt))
k_per_beat = geometry.tile_n * p_a
padded_n = _round_up(shape.n, geometry.tile_n)
padded_k = _round_up(shape.k, granule)
```

MXFP8 每元素 1 B，所以 `p_a=2`、`k_per_beat=16×2=32`。当前 N=2048 已是 16 的倍数，K=1024 已是 32 的倍数，无需 padding。

命令生成循环为：

```python
for m_offset in range(0, shape.m, max_m):
    for n_offset in range(0, padded_n, max_n):
        for k_offset in range(0, padded_k, max_k):
            commands.append(Command(...))
```

该循环按命令维度上限拆分，不是逐元素计算。当前 M/N/K 都未超过单命令上限，因此只有 `command-0`：

```text
m=1, n=2048, k=1024
n_tiles=2048/16=128
k_tiles=1024/32=32
```

### 7.3 count_windows 聚合计数，不逐窗口物化执行

[l1/gemm_model.py:201-228](../model_eval/l1/gemm_model.py#L201) 展示窗口结构：

```python
for index, command in enumerate(commands):
    for n_tile in range(command.n_tiles):
        for k_pass in range(command.k_tiles):
            yield Window(...)
```

`count_windows()` 不逐个创建 Window，而是在 command 循环中用乘法聚合：

```python
n_windows = command.n_tiles * command.k_tiles
window_count += n_windows
a_beats += n_windows * command.m
b_beats += n_windows * command.tile_n
```

当前结果为：

| 指标 | 计算 | 数值 |
|---|---|---:|
| commands | 单命令 | 1 |
| windows | 128×32 | 4,096 |
| A beats | 4096×1 | 4,096 |
| B beats | 4096×16 | 65,536 |
| array cycles | A+B | 69,632 |
| useful MAC | 1×2048×1024 | 2,097,152 |
| padded MAC | 无 padding | 0 |
| C rows | 128×1 | 128 |
| C beats | 每行 2 拍 | 256 |
| 结构利用率 | 4096/69632 | 1/17 |

`array_cycles` 是结构拍数，不等于 q_proj 的全部服务时间；local、scale、DRAM 和 C 写出还需分别计费。

### 7.4 estimate 将命令变成 EngineCost

[l1/gemm_model.py:252-272](../model_eval/l1/gemm_model.py#L252) 再次执行 `legalize()` 和 `count_windows()`，随后创建 phases、traffic、buffers 和 ledger。

内部 `emit()` 位于 [l1/gemm_model.py:352-383](../model_eval/l1/gemm_model.py#L352)，它把同一阶段的多个 Charge 组合成一个 Phase：

```python
known[key] = known.get(key, 0.0) + (item.seconds or 0.0)
unknown |= item.seconds is None
budget = max(known.values(), default=0.0)
...
phases.append(Phase(..., None if unknown else budget, ..., budget, ...))
```

这里有两条不同规则：

- 同一物理资源上的同源串行服务先求和；
- 同一阶段不同资源的已知服务取 max 作为阶段预算；
- 只要任一必需服务未知，`seconds=None`，但 `known_seconds=budget` 仍保留已知部分。

当前 q_proj 形成五个串行阶段：

| 阶段 | 已知预算 | 主要内容 |
|---|---:|---|
| internal loadW | 221.184 µs | 前 31 个 K pass 的 B/scale/local/array 服务 |
| internal consume | 13.824 µs | 前 31 个 K pass 的 A/scale/local/array 服务 |
| output loadW | 8.192 µs | 最后一个 K pass 的 B 装载 |
| output consume | 0.512 µs | 最后一个 K pass 的 A 消费 |
| output write | 0.512 µs | C 输出 |
| 合计 | 244.224 µs | 局部 Phase DAG 的已知服务预算 |

这些时间来自当前 G0 频率、接口和结构账，不包含命令设置、prologue/epilogue、drain/cvt/control bubbles、SRAM bank conflict 或 DRAM 首字节延迟；这些项目留在 excluded costs 中。

## 8. 所有任务如何获得独立引擎成本

### 8.1 estimate_graph 的任务×候选实例双重循环

[l1/timeline.py:45-64](../model_eval/l1/timeline.py#L45) 先校验图，再执行：

```python
for task in graph.tasks:
    candidates = topology.candidates(task)
    for instance in candidates:
        topology.validate_access(graph, task, instance)
        estimates[instance.id] = kernels.estimate(task, instance, execution_plan)
    costs[task.id] = estimates
```

每个任务可以对每个同类候选实例拥有不同 EngineCost。私有内存访问或实例能力不匹配的候选被记录为失败；所有候选均不可行时整个估算失败。

当前默认拓扑每类只有一个实例，因此多数任务只有一个候选，但数据结构仍保持 `task_id → instance_id → EngineCost`，不会在 lowering 中提前绑定实例。

### 8.2 engines 先查算子形态注册表

[l1/engines.py:31](../model_eval/l1/engines.py#L31) 不再按引擎模块直接分发，而是先查算子形态注册表：

```python
operator = lookup_operator_estimator(task)          # (spec_kind, engine_kind)
if operator is not None:
    return operator(task, engine_config, execution_plan)
return ENGINE_ESTIMATORS[task.engine_kind](...)    # 兼容回退
```

成本公式按算子形态放在 `l1/costing/forms/`：

| 模块 | 成本生成重点 |
|---|---|
| `forms/vpu.py` | arithmetic pass、local I/O、特殊函数、显式转换和混合精度未知项 |
| `forms/sampling.py` | 每 batch 向量的 full-sort comparison budget、local/DRAM 和 scratch |
| `forms/transfer.py` | 源/目的 domain、真实端点、DRAM/local 阶段和跨域未知 route |
| `forms/gemm.py` → `costing/gemm_commands.py` | 命令、窗口、A/B/C/scale 流量、array/local/DRAM 阶段和 buffer |

`vpu_model.py`、`cpu_model.py`、`dma_model.py` 现在只是兼容包装；`gemm_model.py` 仍保留 GEMM 实现，命令规划通过 `costing/gemm_commands.py` 共享。VPU/CPU/DMA 公共的 phase/traffic/buffer 构造在 `costing/services.py`。

例如 VPU norm 在 `forms/vpu.py` 中使用三遍 FP32 lane-rate 回退，随后追加 rsqrt 或 exp/reciprocal 阶段。特殊函数参数缺失时，该阶段 `seconds=None`，不会删除整个任务的已知 arithmetic/local 预算。conversion 和 FP32 accumulation 只有在 `ModelParams` 显式给出 elements/cycle 时才计费，否则保持 unknown。

CPU sampling 在 [l1/costing/forms/sampling.py](../model_eval/l1/costing/forms/sampling.py) 中计算：

```python
comparisons = vectors * ceil(length * log2(max(2, length)))
cycles = comparisons * cpu_cycles_per_comparison
seconds = cycles / cpu_freq_hz if not missing else None
```

它只表示显式比较预算，不是生产 sampler 的实现或测量。

### 8.3 EngineCost 内部还有 Phase DAG

[l1/ledger.py:208-217](../model_eval/l1/ledger.py#L208) 的 `known_service_s` 并不是简单把 phases 全部相加，而是遍历可完成阶段：

```python
ends[key] = max((ends[dep] for dep in phase.dependencies), default=0.0) \
            + phase.budget_s
return max(ends.values(), default=0.0)
```

因此一个任务内部可以存在并行阶段；`known_service_s` 是该局部 DAG 在已知预算下的完成时间。跨任务的资源竞争不在这里处理。

EngineCost 构造器还检查 phase ID、phase 依赖环、buffer phase 引用和 traffic owner，见 [l1/ledger.py:174-206](../model_eval/l1/ledger.py#L174)。

### 8.4 handoff 在成本估算后插入

[l1/timeline.py:67-93](../model_eval/l1/timeline.py#L67) 对每个任务检查依赖两端：

```python
dependencies = [dep for dep in task.dependencies
                if tasks[dep].engine_kind != task.engine_kind
                and "DMA" not in (tasks[dep].engine_kind, task.engine_kind)]
```

符合条件的依赖按排序后的顺序生成 handoff Phase 前缀。`sync_us=None` 时秒数未知；显式参数只作为场景服务。已有成本的首个阶段会被改接到 handoff 之后。

任意一端为 DMA 时不添加这条通用 handoff，避免与显式 DMA 服务重复计费；跨域 route 等未知项仍由 DMA 模型自己保留。

### 8.5 cost_view 如何生成报告下界

[l1/cost_view.py](../model_eval/l1/cost_view.py) 只读派生，不参与调度：

```python
engine_busy_lb[e] = Σ 绑定到 e 的 known_seconds（unknown 阶段记 0，仍为下界）
dram_lb           = Σ traffic(dram).logical_bytes / dram_effective_bytes_per_s
cp_lb             = max over DAG paths Σ known_seconds（unknown 阶段记 0）
lower_bound_s     = max(max_e engine_busy_lb[e], dram_lb, cp_lb)
```

跨引擎 busy 不能相加，只对同一引擎求和，再在引擎之间取 max。unknown 不折 0；`seconds=None` 的任务仍贡献已知预算，但不会把完整 makespan 变成确定值。未实际绑定的任务按 `budget_instance_id` 归属，没有预算绑定时取候选成本中的首个实例；该归属只用于计数，不表示任务已排程。`OperatorCost` 的 DRAM/local 字节仍来自 `EngineCost.traffic`，footprint 来自 buffer 生命周期，避免另建一套账。

CLI 默认用 `to_dict(detailed=False)` 打印算子 cost、下界、覆盖和 Top unknown；JSON/HTML 仍保留完整字段并新增 `operator_costs`、`bounds`、`coverage`、`top_unknowns`。

## 9. BufferPlan 如何进入调度

[l1/residency.py:151-198](../model_eval/l1/residency.py#L151) 的 `default_buffer_plan()` 先调用 `graph.validate()`，再为每个被引用 tensor base 统计：

- `uses`：哪些任务读写该 base；
- `writers`：哪些任务产生该 base；
- `ancestors`：任务之间的全部祖先关系。

主循环对每个 base 选择首次 writer 或 reader 作为 acquire，并只保留没有后续消费者的 release 端点：

```python
for base_id, consumers in sorted(uses.items()):
    candidates = writers.get(base_id, consumers)
    acquire = min(candidates, key=rank.__getitem__)
    releases = tuple(BufferEndpoint(key) for key in sorted(consumers)
                     if not any(key in ancestors[other] for other in consumers))
    allocations.append(BufferAllocation(...))
```

因此分配生命周期覆盖首次生产到所有最后消费者，而不是只在单个 GEMM 内存在。graph-entry 输入没有虚构生产者，使用显式入口生命周期。

调度时还会合并 EngineCost 的局部 scratch request：同域 tensor buffer 与全局 base 去重，跨域 staging 形成额外物理副本。容量峰值和冲突都基于这些生命周期计算。

## 10. scheduler 如何得到 projection 与 actual

### 10.1 schedule 先校验，再运行两套预约

[l1/scheduler.py:621-695](../model_eval/l1/scheduler.py#L621) 的顺序为：

```python
ordered = graph.validate()
plan.validate(graph)
# 校验 cost task/instance ID
projection = _project(..., ordered)
actual = _actual(..., projection, policy)
# 生成 TaskTiming / Wait / busy / peaks / attribution
```

条件投影先运行，actual 使用 projection 选择的实例与执行顺序重放。这样可以比较同一固定计划，而不是让 actual 和 projection 各自选择不同实例。

### 10.2 _project 的 pending/ready/candidate 循环

[l1/scheduler.py:475-519](../model_eval/l1/scheduler.py#L475) 维护尚未调度的任务集合：

```python
while pending:
    ready_ids = [key for key in ordered if key in pending and
                 all(dep in run.assigned for dep in tasks[key].dependencies)]
    if policy == "serial":
        ready_ids = [next(key for key in ordered if key in pending)]
    ...
```

list 策略允许所有依赖已完成的任务成为候选；serial 只取稳定拓扑序中的下一个任务，并额外等待前一个 serial 任务完成。

对于 ready 任务，代码继续遍历候选实例并调用 `_attempt(..., projection=True)`，再按以下键确定性选择：

```text
结束时间 → 开始时间 → task ID → instance ID
```

成功任务加入 assigned、selected、calendar 和 order；失败原因保存在 failures，未知不会被改写成零。

### 10.3 _attempt 内部的 Phase DAG 与资源等待

[l1/scheduler.py:373-456](../model_eval/l1/scheduler.py#L373) 是单个候选预约的核心。其内部 `while pending` 循环寻找依赖已完成的 phase：

```python
duration = phase.budget_s if projection else (
    phase.seconds if phase.seconds is not None else math.inf)
phase_start, phase_waits = _slot(...)
phase_end = phase_start.advance(...)
```

projection 使用已知预算；actual 对未知服务使用无限占用。无限占用表示“结束未知”，不是服务时间无穷大的性能测量。

`_slot()` 位于 [l1/scheduler.py:167-197](../model_eval/l1/scheduler.py#L167)。它不会逐周期递增，而是在资源冲突时移动到冲突预约的释放端点：

```python
while True:
    ...
    if conflict is None:
        return start, waits
    if not math.isfinite(release):
        raise _Blocked(...)
    start = cause.advance(release)
```

每次循环只处理资源日历中的冲突端点。任务级 engine lease 从首个阶段开始一直保持到任务完成，覆盖内部等待；内存检查还可能把任务推迟到某个 buffer 释放点。

### 10.4 _actual 重放并传播未知

[l1/scheduler.py:522-553](../model_eval/l1/scheduler.py#L522) 按 projection.order 遍历：

1. 依赖未完成或完成时间未知时，当前任务标记失败；
2. ready 取所有依赖结束的最晚值，serial 还取前一任务结束；
3. 使用 projection.selected 中同一个 cost 调用 `_attempt(..., projection=False)`；
4. 若结束未知，保留 assigned 中的占用，同时记录失败原因；
5. 后续依赖和 serial 任务继续收到未知传播。

最终 `_summary()` 只有在所有任务都已分配且结束时间有限时才返回 makespan，见 [l1/scheduler.py:614-618](../model_eval/l1/scheduler.py#L614)。

这就是当前报告中以下组合的来源：

| 字段 | 当前值 | 产生原因 |
|---|---:|---|
| known_service_budget_s | 70.382 ms | 选中候选的局部已知服务预算之和 |
| projection_makespan_s | 70.382 ms | 已知预算下，serial 固定计划可完成 |
| makespan_s | null | 多个任务仍含未知服务，actual 无法全部完成 |
| modeled_throughput | null | actual 不完整且 excluded 非空 |

当前 serial 算例中预算和与投影数值相同，但二者语义仍不同；list、多实例或资源竞争场景下不能据此假定它们相等。

## 11. L1Result 与报告如何生成

### 11.1 timeline 汇总结构指标和 L0 参考

[l1/timeline.py:257-277](../model_eval/l1/timeline.py#L257) 先根据 `budget_instance_id` 选择用于预算对账的成本，再聚合 metrics：

```python
totals = _sum_metrics(list(selected.values()))
totals["semantic_macs"] = sum(op.mac for op in graph.operations)
...
l0 = evaluate_l0(..., use_doc_bytes=False)
totals["l0_reference_macs"] = l0.mac
```

`_sum_metrics()` 位于 [l1/timeline.py:22-34](../model_eval/l1/timeline.py#L22)，它对数值 metrics 求和，但单独处理：

- DRAM bytes：只汇总 `memory_domain="dram"` 的 Traffic；
- norm_calls / softmax_reductions：先清零，之后按 typed spec 单独统计；
- array_utilization：用总 compute cycles 除以总 array cycles，不把各任务利用率直接相加。

L0 参考不参与 graph、costs 或 schedule。只有 actual 完整时，代码才检查 L1 自身实际 DRAM 流量的共享带宽下界。

### 11.2 to_dict 的 task→operator→layer 聚合

[l1/timeline.py:154-238](../model_eval/l1/timeline.py#L154) 分三层构造输出：

1. **task 循环**：合并 PrimitiveTask、TaskTiming、EngineCost、PhaseTiming、Wait、metrics 和 excluded；
2. **operator 循环**：按 `parent_op_id` 找出成员任务，汇总已知预算、actual/projection span 和 metrics；
3. **layer 循环**：按 LogicalOp.layer 聚合 operator，再汇总该层任务和 span。

任务状态由 scheduler 给出：

```text
scheduled   actual 完成时间已知
projected   只有条件投影
blocked     有候选成本但固定计划阻断
unassigned  无可选成本
```

`instance_id` 与 `budget_instance_id` 分开序列化；未绑定任务的候选预算不会伪装成已绑定实例。

### 11.3 CLI 输出与文件输出共享同一结果

[l1/run.py:113-143](../model_eval/l1/run.py#L113) 最后执行三类输出：

- 终端表格读取 L1Result 的时间、metrics 和 projection attribution；
- JSON 调用 `result.to_dict(args.debug_ledger)`，写入 `schema_version=2`、manifest、digest 和 results；
- HTML 调用 `report_payload(profile, results, manifest)` 后嵌入模板。

当前算例的报告规模为：

```text
423 LogicalOp
733 PrimitiveTask
845 Tensor
199 GEMM commands
349 条去重后的 excluded costs
```

`--debug-ledger` 只决定 JSON 是否保留 operator 聚合 raw ledger，不改变任务、成本或调度。HTML 始终包含 ledger，且只展示 Python 已计算的数据。

## 12. 整条链路的数据结构变化

将前述代码压缩成类型变化，可以看到每一层的职责边界：

```text
CONFIG dict
  → ModelPlan（LayerInstance 树 + WeightAllocation）
      层 geometry/权重角色；L0 闭式在 LayerDefinition 中

WorkloadPoint + NumFormat + HwProfile
  → L0 Account(MAC) + Account(bytes)
  → L0Result
      只含闭式 roofline 与瓶颈

ModelPlan + WorkloadPoint + NumFormat
  → emitter 递归 → tuple[LogicalOp]
      有语义形状，尚无生产者 DAG

LogicalOp + GraphBuilder + ArrayGeometry
  → TaskGraph(operations, tasks, tensors)
      有 typed spec、显式转换、padding、依赖和 base/view

TaskGraph + ExecutionPlan
  → BufferPlan
      有 base 分配与生命周期

TaskGraph + Topology + ExecutionPlan
  → dict[task_id][instance_id] = EngineCost
      有 Phase DAG、资源 claim、流量、buffer 和未知服务

TaskGraph + costs + Topology + BufferPlan
  → ScheduleResult
      有 projection/actual 时刻、等待、资源 busy、内存峰值和阻断

L1Result + manifest
  → JSON schema 2 / 离线 HTML
      只序列化与展示已有结果
```

这条链上没有任何一步执行 Qwen 权重前向，也没有任何一步把未知服务自动补零。L0 给出总量 roofline，L1 给出显式任务与固定计划资源预约；二者共享输入，但回答不同问题。
