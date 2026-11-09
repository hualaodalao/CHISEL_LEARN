from __future__ import annotations

import math
from dataclasses import replace
from typing import Callable

from ..common.opspec import (CpuSpec, GemmGroup, GemmSpec, LogicalOp, PrimitiveTask,
                            TaskGraph, Tensor, TensorRef, TransferSpec, VpuSpec)
from ..common.workload import Phase, WorkloadPoint
from ..frontend.spec import ModelSpec, NumFormat

ATTENTION_VARIANTS = ("vpu", "quantized_gemm")


def map_model(model: ModelSpec, fmt: NumFormat, point: WorkloadPoint,
              attention: str = "vpu") -> tuple[LogicalOp, ...]:
    if attention not in ATTENTION_VARIANTS:
        raise ValueError(f"未知 attention 变体 {attention}")
    if point.phase is Phase.FRAME:
        raise ValueError("L1 Qwen frontend 只支持 decode/prefill，不代表视觉模型")
    rows = point.batch * (point.seq_len if point.phase is Phase.PREFILL else 1)
    operations = []

    def add(layer: str, name: str, engine: str, kind: str, **values) -> None:
        if engine in ("VPU", "CPU"):
            elements = values.get("elements", 0)
            values["dram_read_bytes"] = (4 * elements if kind in ("norm", "softmax")
                                        else values.get("read_bytes", 0))
            values["dram_write_bytes"] = values.get("write_bytes", 0)
        operations.append(LogicalOp(f"{layer}/{name}", layer, name, kind, **values))

    bpp = float(fmt.bytes_per_param_exact)
    index = 0
    for layer_type in model.layer_types:
        if layer_type.name != "qwen3_full_attention":
            raise ValueError(f"没有 L1 算子展开器：{layer_type.name}")
        tensors = {t.name: t for t in layer_type.tensors}
        head_dim = (layer_type.params_unique - sum(t.elements for t in tensors.values())
                    - 2 * model.hidden_size) // 2
        heads = tensors["q_proj"].shape[0] // head_dim
        kv_heads = tensors["k_proj"].shape[0] // head_dim
        hidden = model.hidden_size
        intermediate = tensors["gate_proj"].shape[0]
        for _ in range(layer_type.count):
            layer = f"block-{index:02d}"
            index += 1
            add(layer, "input_norm", "VPU", "norm", mac=2 * hidden * rows,
                elements=hidden * rows, vectors=rows, length=hidden,
                read_bytes=8 * hidden * rows, write_bytes=4 * hidden * rows,
                weight_bytes=hidden * bpp)
            for name in ("q_proj", "k_proj", "v_proj"):
                n, k = tensors[name].shape
                add(layer, name, "GEMM", "weight_gemm", mac=rows * n * k,
                    gemms=(GemmGroup(rows, n, k),))
            qk_elements = (heads + kv_heads) * head_dim * rows
            add(layer, "qk_norm", "VPU", "norm", mac=2 * qk_elements,
                elements=qk_elements, vectors=(heads + kv_heads) * rows, length=head_dim,
                read_bytes=8 * qk_elements, write_bytes=4 * qk_elements,
                weight_bytes=2 * head_dim * bpp)
            add(layer, "rope", "VPU", "rope", mac=2 * qk_elements,
                elements=qk_elements, read_bytes=8 * qk_elements, write_bytes=4 * qk_elements)
            kv_write = layer_type.kv_bytes_per_token * rows
            kv_read = (layer_type.kv_bytes_per_token * point.seq_len * point.batch
                       if point.phase is Phase.DECODE else 0)
            add(layer, "kv_transfer", "DMA", "kv_transfer", read_bytes=kv_read,
                write_bytes=kv_write, evidence="FP16 KV; explicit external read/write, no cache hits")
            s = point.seq_len
            if s:
                if point.phase is Phase.DECODE:
                    attention_mac = heads * head_dim * s * point.batch
                    soft_elements = heads * s * point.batch
                    qk_shape = GemmGroup(point.batch, s, head_dim, heads)
                    av_shape = GemmGroup(point.batch, head_dim, s, heads)
                else:
                    attention_mac = heads * head_dim * s * (s + 1) * point.batch // 2
                    soft_elements = heads * s * (s + 1) * point.batch // 2
                    qk_shape = GemmGroup(s, s, head_dim, heads * point.batch)
                    av_shape = GemmGroup(s, head_dim, s, heads * point.batch)
                engine = "GEMM" if attention == "quantized_gemm" else "VPU"
                kind = "attention_gemm" if engine == "GEMM" else "attention_vpu"
                attention_bytes = 2 * heads * s * head_dim * rows
                evidence = ("quantized QK/AV scenario; prefill issues dense masked matrices; "
                            "precision, packing and quantization service are unverified"
                            if engine == "GEMM" else "FP16 attention on VPU; no GEMM input substitution")
                add(layer, "qk", engine, kind, mac=attention_mac,
                    gemms=(qk_shape,) if engine == "GEMM" else (),
                    read_bytes=attention_bytes, write_bytes=4 * soft_elements, evidence=evidence)
                add(layer, "softmax", "VPU", "softmax", mac=soft_elements,
                    elements=soft_elements, vectors=heads * rows, length=s,
                    read_bytes=12 * soft_elements, write_bytes=4 * soft_elements,
                    evidence="three FP32 passes; prefill causal elements, not full masked matrix")
                add(layer, "av", engine, kind, mac=attention_mac,
                    gemms=(av_shape,) if engine == "GEMM" else (),
                    read_bytes=attention_bytes + 4 * soft_elements,
                    write_bytes=4 * heads * head_dim * rows, evidence=evidence)
            n, k = tensors["o_proj"].shape
            add(layer, "o_proj", "GEMM", "weight_gemm", mac=rows * n * k,
                gemms=(GemmGroup(rows, n, k),))
            add(layer, "attention_residual", "VPU", "residual", mac=hidden * rows,
                elements=hidden * rows, read_bytes=8 * hidden * rows, write_bytes=4 * hidden * rows)
            add(layer, "post_norm", "VPU", "norm", mac=2 * hidden * rows,
                elements=hidden * rows, vectors=rows, length=hidden,
                read_bytes=8 * hidden * rows, write_bytes=4 * hidden * rows,
                weight_bytes=hidden * bpp)
            for name in ("gate_proj", "up_proj"):
                n, k = tensors[name].shape
                add(layer, name, "GEMM", "weight_gemm", mac=rows * n * k,
                    gemms=(GemmGroup(rows, n, k),))
            add(layer, "silu_gate", "VPU", "silu_gate", mac=intermediate * rows,
                elements=intermediate * rows, read_bytes=12 * intermediate * rows,
                write_bytes=4 * intermediate * rows)
            n, k = tensors["down_proj"].shape
            add(layer, "down_proj", "GEMM", "weight_gemm", mac=rows * n * k,
                gemms=(GemmGroup(rows, n, k),))
            add(layer, "mlp_residual", "VPU", "residual", mac=hidden * rows,
                elements=hidden * rows, read_bytes=8 * hidden * rows, write_bytes=4 * hidden * rows)
    add("final-norm", "norm", "VPU", "norm", mac=model.final_norm_mac * rows,
        elements=model.hidden_size * rows, vectors=rows, length=model.hidden_size,
        read_bytes=8 * model.hidden_size * rows, write_bytes=4 * model.hidden_size * rows,
        weight_bytes=model.hidden_size * bpp)
    add("lm-head", "projection", "GEMM", "weight_gemm", mac=model.lm_head_mac * point.batch,
        gemms=(GemmGroup(point.batch, model.vocab_size, model.hidden_size),))
    add("sampling", "sampling", "CPU", "sampling", elements=model.vocab_size * point.batch,
        vectors=point.batch, length=model.vocab_size, read_bytes=4 * model.vocab_size * point.batch,
        evidence="full-sort comparison-budget scenario, not a selected production sampler")
    return tuple(operations)


class _GraphBuilder:
    def __init__(self, operations: tuple[LogicalOp, ...], block_size: int):
        self.operations = operations
        self.tasks: list[PrimitiveTask] = []
        self.tensors: dict[str, Tensor] = {}
        self.producers: dict[str, str] = {}
        self.padded_operands: dict[tuple[str, tuple[int, ...]], str] = {}
        self.block_size = block_size

    def tensor(self, id: str, shape: tuple[int, ...], dtype: str, *, external=False,
               base_id: str | None = None, base_starts=(), base_stops=(), base_steps=(),
               scale_axis=-1) -> str:
        self.tensors[id] = Tensor(id, shape, dtype, base_id=base_id, external=external,
                                  block_size=self.block_size, base_starts=base_starts,
                                  base_stops=base_stops, base_steps=base_steps, scale_axis=scale_axis)
        return id

    def task(self, op: LogicalOp, suffix: str, engine: str, spec, inputs: tuple[str, ...],
             shape: tuple[int, ...] | None, dtype="FP32", *, dependencies=(), scale_axis=-1) -> str | None:
        id = op.id + "/" + suffix
        deps = tuple(dict.fromkeys((*dependencies, *(self.producers[t] for t in inputs
                                                     if t in self.producers))))
        outputs = ()
        result = None
        if shape is not None:
            result = self.tensor(id + ":out", shape, dtype, scale_axis=scale_axis)
            self.producers[result] = id
            outputs = (TensorRef(result),)
        self.tasks.append(PrimitiveTask(id, op.id, engine, spec, deps,
                                        tuple(TensorRef(t) for t in inputs), outputs))
        return result

    def convert(self, op: LogicalOp, tensor: str, dtype: str, suffix: str, *, scale_axis=-1) -> str:
        source = self.tensors[tensor]
        if source.dtype == dtype and (not dtype.startswith("MX") or
                source.scale_axis % len(source.shape) == scale_axis % len(source.shape)):
            return tensor
        target = Tensor("target", source.shape, dtype, block_size=self.block_size, scale_axis=scale_axis)
        return self.task(op, suffix, "VPU", VpuSpec(
            "convert", elements=math.prod(source.shape), read_bytes=source.size_bytes,
            write_bytes=target.size_bytes, dram_read_bytes=source.size_bytes,
            dram_write_bytes=target.size_bytes, input_dtype=source.dtype, output_dtype=dtype,
            evidence="explicit dtype/layout/quantization conversion; service unknown"),
            (tensor,), source.shape, dtype, scale_axis=scale_axis)

    def vector(self, op: LogicalOp, inputs: tuple[str, ...], shape: tuple[int, ...], *,
               suffix="execute", spec: VpuSpec | None = None, dtype="FP32") -> str:
        spec = spec or VpuSpec(op.kind, op.mac, op.elements, op.vectors, op.length,
                              op.read_bytes, op.write_bytes, op.dram_read_bytes,
                              op.dram_write_bytes, evidence=op.evidence)
        result = self.task(op, suffix, "VPU", spec, inputs, shape, dtype)
        return result

    def gemm(self, op: LogicalOp, a: str, b: str, spec: GemmSpec, geometry,
             suffix="execute") -> str:
        from .gemm_model import GemmShape, legalize
        commands = legalize(GemmShape(spec.m, spec.n, spec.k, spec.a_fmt, spec.b_fmt,
                                     block_size=spec.block_size), geometry)
        k_parts = len({command.k_offset for command in commands})
        padded_n = sum(command.n for command in commands if command.m_offset == 0 and command.k_offset == 0)
        padded_k = sum(command.k for command in commands if command.m_offset == 0 and command.n_offset == 0)
        operands = []
        for name, tensor_id, storage_shape, axis in (
                ("a", a, (spec.m, padded_k), 1), ("b", b, (padded_k, padded_n), 0)):
            source = self.tensors[tensor_id]
            key = (tensor_id, storage_shape)
            if key in self.padded_operands:
                operands.append(self.padded_operands[key])
                continue
            if source.shape != storage_shape:
                target = Tensor("target", storage_shape, source.dtype,
                                block_size=self.block_size, scale_axis=axis)
                backing = self.task(op, suffix + "-pad-" + name, "VPU", VpuSpec(
                    "convert", elements=math.prod(storage_shape), read_bytes=source.size_bytes,
                    write_bytes=target.size_bytes, dram_read_bytes=source.size_bytes,
                    dram_write_bytes=target.size_bytes, input_dtype=source.dtype,
                    output_dtype=source.dtype,
                    evidence="explicit operand repack and zero padding, including MX scales; service unknown"),
                    (tensor_id,), storage_shape, source.dtype, scale_axis=axis)
                tensor_id = self.tensor(backing + ":logical", source.shape, source.dtype,
                                        base_id=backing, base_starts=(0, 0),
                                        base_stops=source.shape, scale_axis=axis)
                self.producers[tensor_id] = self.producers[backing]
                self.padded_operands[key] = tensor_id
            operands.append(tensor_id)
        spec = replace(spec, a_storage_shape=(spec.m, padded_k),
                       b_storage_shape=(padded_k, padded_n))
        shape = (spec.m, padded_n) if k_parts == 1 else (k_parts, spec.m, padded_n)
        partial = self.task(op, suffix, "GEMM", spec, tuple(operands), shape, spec.c_dtype)
        result = partial
        if k_parts > 1:
            cells = sum(command.m * command.n for command in commands if command.k_offset)
            result = self.vector(op, (partial,), (spec.m, padded_n), suffix=suffix + "-k-merge",
                                 dtype=spec.c_dtype, spec=VpuSpec(
                                     "k_accum", mac=cells, elements=cells, read_bytes=8 * cells,
                                     write_bytes=4 * cells, dram_read_bytes=8 * cells,
                                     dram_write_bytes=4 * cells, input_dtype=spec.c_dtype,
                                     output_dtype=spec.c_dtype,
                                     evidence="explicit independent K-command partial-sum merge"))
        if padded_n == spec.n:
            return result
        view = self.tensor(result + ":logical", (spec.m, spec.n), spec.c_dtype,
                           base_id=result, base_starts=(0, 0), base_stops=(spec.m, spec.n))
        self.producers[view] = self.producers[result]
        return view


def lower_model(model: ModelSpec, fmt: NumFormat, point: WorkloadPoint,
                attention: str = "vpu", *, geometry=None, block_size=32,
                lowering: Callable[..., TaskGraph] | None = None) -> TaskGraph:
    if lowering is not None:
        graph = lowering(model, fmt, point, attention=attention, geometry=geometry,
                         block_size=block_size)
        graph.validate()
        return graph
    from ..hw.geometry import load_array_geometry
    geometry = geometry or load_array_geometry()
    operations = map_model(model, fmt, point, attention)
    builder = _GraphBuilder(operations, block_size)
    rows = point.batch * (point.seq_len if point.phase is Phase.PREFILL else 1)
    current = builder.tensor("input:hidden", (rows, model.hidden_size), "FP32", external=True)
    by_layer: dict[str, dict[str, LogicalOp]] = {}
    for op in operations:
        by_layer.setdefault(op.layer, {})[op.name] = op
    output_dtype = "INT32" if fmt.name.startswith("INT") else "FP32"

    def weight(op, a):
        group = op.gemms[0]
        a = builder.convert(op, a, fmt.name, "quantize-input")
        b = builder.tensor(op.id + ":weight", (group.k, group.n), fmt.name, external=True, scale_axis=0)
        out = builder.gemm(op, a, b, GemmSpec(group.m, group.n, group.k, fmt.name, fmt.name,
                                            output_dtype, block_size, op.mac, True, op.evidence), geometry)
        return builder.convert(op, out, "FP32", "dequantize-output")

    def norm(op, inp, shape, suffix="execute", spec=None):
        coefficient_bytes = math.ceil(op.weight_bytes) if spec is None else 0
        if coefficient_bytes:
            builder.task(op, suffix + "-coefficients", "DMA", TransferSpec(
                op.id + ":coefficients", "dram", "core0.sram", coefficient_bytes,
                direction="read", evidence="unique normalization coefficients"), (), None)
            coefficients_dep = (builder.tasks[-1].id,)
        else:
            coefficients_dep = ()
        result = builder.vector(op, (inp,), shape, suffix=suffix, spec=spec)
        if coefficients_dep:
            builder.tasks[-1] = replace(builder.tasks[-1], dependencies=tuple(dict.fromkeys(
                (*builder.tasks[-1].dependencies, *coefficients_dep))))
        return result

    for layer, ops in by_layer.items():
        if not layer.startswith("block-"):
            continue
        residual = current
        hidden = model.hidden_size
        normalized = norm(ops["input_norm"], current, (rows, hidden))
        # All three projections consume one quantized allocation, not three copies.
        quantized = builder.convert(ops["input_norm"], normalized, fmt.name, "quantize")
        q, k, v = (weight(ops[name], quantized) for name in ("q_proj", "k_proj", "v_proj"))
        qwidth, kwidth = builder.tensors[q].shape[-1], builder.tensors[k].shape[-1]
        qknorm = ops["qk_norm"]
        dim = qknorm.length
        qheads, kvheads = qwidth // dim, kwidth // dim
        builder.task(qknorm, "coefficients", "DMA", TransferSpec(
            qknorm.id + ":coefficients", "dram", "core0.sram", math.ceil(qknorm.weight_bytes),
            direction="read", evidence="shared Q/K normalization coefficients"), (), None)
        coefficient_task = builder.tasks[-1].id
        normalized_parts = []
        for name, tensor, width in (("q", q, qwidth), ("k", k, kwidth)):
            count = rows * width
            spec = VpuSpec("norm", mac=2 * count, elements=count, vectors=rows * width // dim,
                           length=dim, read_bytes=8 * count, write_bytes=4 * count,
                           dram_read_bytes=4 * count, dram_write_bytes=4 * count,
                           evidence="independent Q/K head normalization")
            normalized_parts.append(norm(qknorm, tensor, (rows, width), name, spec))
            builder.tasks[-1] = replace(builder.tasks[-1], dependencies=tuple(dict.fromkeys(
                (*builder.tasks[-1].dependencies, coefficient_task))))
        rotated = []
        for name, tensor, width in zip(("q", "k"), normalized_parts, (qwidth, kwidth)):
            count = rows * width
            rotated.append(builder.vector(ops["rope"], (tensor,), (rows, width), suffix=name,
                           spec=VpuSpec("rope", mac=2 * count, elements=count,
                                        read_bytes=8 * count, write_bytes=4 * count,
                                        dram_read_bytes=8 * count, dram_write_bytes=4 * count)))
        q, k = rotated
        kvop = ops["kv_transfer"]
        fp16k = builder.convert(kvop, k, "FP16", "pack-k")
        fp16v = builder.convert(kvop, v, "FP16", "pack-v")
        builder.task(kvop, "append", "DMA", TransferSpec(kvop.id + ":append", "dram",
                     "dram", kvop.write_bytes, direction="copy",
                     evidence="explicit external FP16 staging to KV allocation; no implicit SRAM residency"),
                     (fp16k, fp16v), None)
        append_id = builder.tasks[-1].id
        s = point.seq_len
        if s:
            cache_k = builder.tensor(layer + ":K-cache", (point.batch, kvheads, dim, s),
                                     "FP16", external=True)
            cache_v = builder.tensor(layer + ":V-cache", (point.batch, kvheads, s, dim),
                                     "FP16", external=True)
            if attention == "vpu":
                qkop = ops["qk"]
                scores = builder.vector(qkop, (q, cache_k), (point.batch, qheads, rows // point.batch, s),
                                        dtype="FP32")
                builder.tasks[-1] = replace(builder.tasks[-1], dependencies=tuple(dict.fromkeys(
                    (*builder.tasks[-1].dependencies, append_id))))
                probabilities = builder.vector(ops["softmax"], (scores,), builder.tensors[scores].shape)
                context = builder.vector(ops["av"], (probabilities, cache_v), (rows, qwidth))
            else:
                contexts = []
                quantized_keys: dict[tuple[int, int], str] = {}
                quantized_values: dict[tuple[int, int], str] = {}
                m = 1 if point.phase is Phase.DECODE else s
                for batch_index in range(point.batch):
                    for head in range(qheads):
                        suffix = f"b{batch_index}-h{head}"
                        kvhead = head * kvheads // qheads
                        qview = builder.tensor(layer + ":Q:" + suffix, (m, dim), "FP32", base_id=q,
                            base_starts=(batch_index * m, head * dim),
                            base_stops=((batch_index + 1) * m, (head + 1) * dim))
                        builder.producers[qview] = builder.producers[q]
                        kview = builder.tensor(layer + ":K:" + suffix, (dim, s), "FP16", base_id=cache_k,
                            base_starts=(batch_index, kvhead, 0, 0),
                            base_stops=(batch_index + 1, kvhead + 1, dim, s))
                        vview = builder.tensor(layer + ":V:" + suffix, (s, dim), "FP16", base_id=cache_v,
                            base_starts=(batch_index, kvhead, 0, 0),
                            base_stops=(batch_index + 1, kvhead + 1, s, dim))
                        qa = builder.convert(ops["qk"], qview, fmt.name, suffix + "-quantize-q")
                        key = (batch_index, kvhead)
                        if key not in quantized_keys:
                            quantized_keys[key] = builder.convert(ops["qk"], kview, fmt.name,
                                                                 f"b{batch_index}-kv{kvhead}-quantize-k", scale_axis=0)
                            builder.tasks[-1] = replace(builder.tasks[-1], dependencies=tuple(dict.fromkeys(
                                (*builder.tasks[-1].dependencies, append_id))))
                        kb = quantized_keys[key]
                        mac = dim * s if m == 1 else dim * s * (s + 1) // 2
                        scores = builder.gemm(ops["qk"], qa, kb, GemmSpec(m, s, dim, fmt.name,
                            fmt.name, output_dtype, block_size, mac, evidence=ops["qk"].evidence), geometry, suffix)
                        scores = builder.convert(ops["qk"], scores, "FP32", suffix + "-dequantize")
                        soft = ops["softmax"]
                        elements = s if m == 1 else s * (s + 1) // 2
                        probabilities = builder.vector(soft, (scores,), (m, s), suffix=suffix,
                            spec=VpuSpec("softmax", mac=elements, elements=elements, vectors=m,
                                         length=s, read_bytes=12 * elements, write_bytes=4 * elements,
                                         dram_read_bytes=4 * elements, dram_write_bytes=4 * elements))
                        pa = builder.convert(ops["av"], probabilities, fmt.name, suffix + "-quantize-p")
                        if key not in quantized_values:
                            quantized_values[key] = builder.convert(ops["av"], vview, fmt.name,
                                                                    f"b{batch_index}-kv{kvhead}-quantize-v", scale_axis=0)
                            builder.tasks[-1] = replace(builder.tasks[-1], dependencies=tuple(dict.fromkeys(
                                (*builder.tasks[-1].dependencies, append_id))))
                        vb = quantized_values[key]
                        context = builder.gemm(ops["av"], pa, vb, GemmSpec(m, dim, s, fmt.name,
                            fmt.name, output_dtype, block_size, mac, evidence=ops["av"].evidence), geometry, suffix)
                        contexts.append(builder.convert(ops["av"], context, "FP32", suffix + "-dequantize"))
                context = builder.task(ops["av"], "join-heads", "VPU", VpuSpec(
                    "convert", elements=rows * qwidth, input_dtype="FP32", output_dtype="FP32",
                    read_bytes=4 * rows * qwidth, write_bytes=4 * rows * qwidth,
                    dram_read_bytes=4 * rows * qwidth, dram_write_bytes=4 * rows * qwidth,
                    evidence="head assembly/layout service is unknown"), tuple(contexts), (rows, qwidth))
        else:
            context = builder.tensor(layer + ":empty-attention-context", (rows, qwidth),
                                     "FP32", external=True)
        projected = weight(ops["o_proj"], context)
        residual2 = builder.vector(ops["attention_residual"], (residual, projected), (rows, hidden))
        normalized = norm(ops["post_norm"], residual2, (rows, hidden))
        quantized = builder.convert(ops["post_norm"], normalized, fmt.name, "quantize")
        gate, up = (weight(ops[name], quantized) for name in ("gate_proj", "up_proj"))
        gated = builder.vector(ops["silu_gate"], (gate, up), builder.tensors[gate].shape)
        down = weight(ops["down_proj"], gated)
        current = builder.vector(ops["mlp_residual"], (residual2, down), (rows, hidden))
    final = norm(by_layer["final-norm"]["norm"], current, (rows, model.hidden_size))
    if point.phase is Phase.PREFILL:
        last = builder.tensor("final-norm:last-position", (point.batch, model.hidden_size),
                              "FP32", base_id=final, base_starts=(point.seq_len - 1, 0),
                              base_stops=(rows, model.hidden_size), base_steps=(point.seq_len, 1))
        builder.producers[last] = builder.producers[final]
        final = last
    logits = weight(by_layer["lm-head"]["projection"], final)
    sampling = by_layer["sampling"]["sampling"]
    builder.task(sampling, "execute", "CPU", CpuSpec(vectors=point.batch, length=model.vocab_size,
                 read_bytes=sampling.read_bytes, dram_read_bytes=sampling.dram_read_bytes,
                 evidence=sampling.evidence), (logits,), None)
    graph = TaskGraph(operations, tuple(builder.tasks), tuple(builder.tensors.values()),
                      "Qwen producer/consumer lowering; external DRAM intermediates; no implicit SRAM cache")
    graph.validate()
    return graph
