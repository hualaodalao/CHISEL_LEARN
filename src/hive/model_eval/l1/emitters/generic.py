"""Emitters for model-agnostic leaf layers.

These reproduce the legacy Qwen lowering rules exactly; the closed-form L0
accounting lives in ``frontend.layers.generic`` and is not recomputed here.
"""

from __future__ import annotations

import math

from ...common.opspec import CpuSpec, GemmGroup, GemmSpec, TransferSpec, VpuSpec
from ...common.workload import Phase
from ...frontend.layers.generic import (ActivationGateGeometry,
                                        AttentionMatmulGeometry,
                                        HeadRmsNormGeometry, KvAppendGeometry,
                                        LinearGeometry, ResidualGeometry,
                                        RmsNormGeometry, RopeGeometry,
                                        SamplingGeometry, SoftmaxGeometry)
from .base import EmitContext, emitter


def _bpp(ctx: EmitContext) -> float:
    return float(ctx.fmt.bytes_per_param_exact)


def _weight_gemm(ctx: EmitContext, op, a: str, m: int, n: int, k: int) -> str:
    a = ctx.builder.convert(op, a, ctx.fmt.name, "quantize-input")
    b = ctx.builder.tensor(op.id + ":weight", (k, n), ctx.fmt.name, external=True,
                           scale_axis=0)
    out = ctx.builder.gemm(op, a, b, GemmSpec(
        m, n, k, ctx.fmt.name, ctx.fmt.name, ctx.output_dtype, ctx.block_size,
        op.mac, True, op.evidence), ctx.geometry)
    return ctx.builder.convert(op, out, "FP32", "dequantize-output")


@emitter("generic.linear")
def emit_linear(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, LinearGeometry)
    rows = ctx.rows
    op = ctx.operation(instance, "GEMM", "weight_gemm",
                       mac=rows * geometry.out_features * geometry.in_features,
                       gemms=(GemmGroup(rows, geometry.out_features,
                                        geometry.in_features),))
    return {"out": _weight_gemm(ctx, op, inputs["x"], rows,
                                geometry.out_features, geometry.in_features)}


@emitter("generic.lm_head")
def emit_lm_head(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, LinearGeometry)
    batch = ctx.point.batch
    op = ctx.operation(instance, "GEMM", "weight_gemm",
                       mac=batch * geometry.out_features * geometry.in_features,
                       gemms=(GemmGroup(batch, geometry.out_features,
                                        geometry.in_features),))
    return {"logits": _weight_gemm(ctx, op, inputs["hidden"], batch,
                                   geometry.out_features, geometry.in_features)}


@emitter("generic.rms_norm")
def emit_rms_norm(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, RmsNormGeometry)
    rows = ctx.rows
    width = geometry.width
    op = ctx.operation(instance, "VPU", "norm",
                       mac=2 * width * rows, elements=width * rows, vectors=rows,
                       length=width, read_bytes=8 * width * rows,
                       write_bytes=4 * width * rows, weight_bytes=width * _bpp(ctx))
    coefficient_bytes = math.ceil(op.weight_bytes)
    dependencies = ()
    if coefficient_bytes:
        dependencies = (ctx.builder.dma(
            op, "execute-coefficients", op.id + ":coefficients", coefficient_bytes,
            direction="read", source_domain="dram", destination_domain="core0.sram",
            evidence="unique normalization coefficients"),)
    result = ctx.builder.vector(op, (inputs["hidden"],), (rows, width),
                                dependencies=dependencies)
    outputs = {"hidden": result}
    if geometry.quantize:
        outputs["quantized"] = ctx.builder.convert(op, result, ctx.fmt.name, "quantize")
    if geometry.last_position_only and ctx.point.phase is Phase.PREFILL:
        rows_total = rows
        outputs["hidden"] = ctx.builder.view(
            f"{op.layer}:last-position", result, (ctx.point.batch, width),
            starts=(ctx.point.seq_len - 1, 0), stops=(rows_total, width),
            steps=(ctx.point.seq_len, 1))
    return outputs


@emitter("generic.head_rms_norm")
def emit_head_rms_norm(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, HeadRmsNormGeometry)
    rows = ctx.rows
    dim = geometry.head_dim
    vectors = (geometry.heads + geometry.kv_heads) * rows
    elements = vectors * dim
    op = ctx.operation(instance, "VPU", "norm",
                       mac=2 * elements, elements=elements, vectors=vectors,
                       length=dim, read_bytes=8 * elements, write_bytes=4 * elements,
                       weight_bytes=2 * dim * _bpp(ctx))
    coefficient = ctx.builder.dma(
        op, "coefficients", op.id + ":coefficients", math.ceil(op.weight_bytes),
        direction="read", source_domain="dram", destination_domain="core0.sram",
        evidence="shared Q/K normalization coefficients")
    outputs = {}
    for name in ("q", "k"):
        tensor = inputs[name]
        width = ctx.builder.tensors[tensor].shape[-1]
        count = rows * width
        spec = VpuSpec("norm", mac=2 * count, elements=count,
                       vectors=rows * width // dim, length=dim,
                       read_bytes=8 * count, write_bytes=4 * count,
                       dram_read_bytes=4 * count, dram_write_bytes=4 * count,
                       evidence="independent Q/K head normalization")
        outputs[name] = ctx.builder.vector(op, (tensor,), (rows, width), suffix=name,
                                           spec=spec, dependencies=(coefficient,))
    return outputs


@emitter("generic.rope")
def emit_rope(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, RopeGeometry)
    rows = ctx.rows
    elements = (geometry.heads + geometry.kv_heads) * geometry.head_dim * rows
    op = ctx.operation(instance, "VPU", "rope",
                       mac=2 * elements, elements=elements,
                       read_bytes=8 * elements, write_bytes=4 * elements)
    outputs = {}
    for name in ("q", "k"):
        tensor = inputs[name]
        width = ctx.builder.tensors[tensor].shape[-1]
        count = rows * width
        spec = VpuSpec("rope", mac=2 * count, elements=count,
                       read_bytes=8 * count, write_bytes=4 * count,
                       dram_read_bytes=8 * count, dram_write_bytes=4 * count)
        outputs[name] = ctx.builder.vector(op, (tensor,), (rows, width), suffix=name,
                                           spec=spec)
    return outputs


@emitter("generic.kv_append")
def emit_kv_append(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, KvAppendGeometry)
    rows = ctx.rows
    batch = ctx.point.batch
    seq = ctx.point.seq_len
    bytes_per_token = (2 * geometry.kv_heads * geometry.head_dim
                       * geometry.elem_bytes)
    read_bytes = bytes_per_token * seq * batch if ctx.point.phase is Phase.DECODE else 0
    write_bytes = bytes_per_token * rows
    op = ctx.operation(instance, "DMA", "kv_transfer",
                       read_bytes=read_bytes, write_bytes=write_bytes,
                       evidence="FP16 KV; explicit external read/write, no cache hits")
    packed_k = ctx.builder.convert(op, inputs["k"], "FP16", "pack-k")
    packed_v = ctx.builder.convert(op, inputs["v"], "FP16", "pack-v")
    outputs = {}
    cache_outputs = ()
    if seq:
        scope = ctx.scope(instance)
        k_cache = ctx.builder.tensor(f"{scope}:K-cache",
                                     (batch, geometry.kv_heads, geometry.head_dim, seq),
                                     "FP16", external=True)
        v_cache = ctx.builder.tensor(f"{scope}:V-cache",
                                     (batch, geometry.kv_heads, seq, geometry.head_dim),
                                     "FP16", external=True)
        cache_outputs = (k_cache, v_cache)
        outputs = {"k_cache": k_cache, "v_cache": v_cache}
    ctx.builder.dma(
        op, "append", op.id + ":append", op.write_bytes, direction="copy",
        inputs=(packed_k, packed_v), outputs=cache_outputs,
        evidence="explicit external FP16 staging to KV allocation; no implicit SRAM residency")
    return outputs


@emitter("generic.softmax")
def emit_softmax(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, SoftmaxGeometry)
    logits = inputs["logits"]
    shape = ctx.builder.tensors[logits].shape
    batch, heads, _, seq = shape
    if ctx.point.phase is Phase.DECODE:
        elements = heads * seq * batch
    else:
        elements = heads * seq * (seq + 1) * batch // 2
    op = ctx.operation(instance, "VPU", "softmax",
                       mac=elements, elements=elements,
                       vectors=heads * ctx.rows, length=seq,
                       read_bytes=12 * elements, write_bytes=4 * elements,
                       evidence="three FP32 passes; prefill causal elements, not full masked matrix")
    return {"probabilities": ctx.builder.vector(op, (logits,), shape)}


@emitter("generic.attention_matmul")
def emit_attention_matmul(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, AttentionMatmulGeometry)
    if ctx.variant != "vpu":
        raise ValueError("quantized attention is composed by qwen3.gqa_attention")
    rows = ctx.rows
    batch = ctx.point.batch
    if geometry.mode == "qk":
        scores = ctx.builder.tensors[inputs["a"]]
        s = ctx.builder.tensors[inputs["b"]].shape[-1]
        m = scores.shape[0] // batch
    else:
        probabilities = ctx.builder.tensors[inputs["a"]]
        m = probabilities.shape[2]
        s = probabilities.shape[-1]
        batch = probabilities.shape[0]
    heads = geometry.heads
    dim = geometry.head_dim
    if ctx.point.phase is Phase.DECODE:
        mac = heads * dim * s * batch
        soft_elements = heads * s * batch
    else:
        mac = heads * dim * s * (s + 1) * batch // 2
        soft_elements = heads * s * (s + 1) * batch // 2
    attention_bytes = 2 * heads * s * dim * rows
    evidence = "FP16 attention on VPU; no GEMM input substitution"
    if geometry.mode == "qk":
        op = ctx.operation(instance, "VPU", "attention_vpu", mac=mac,
                           read_bytes=attention_bytes, write_bytes=4 * soft_elements,
                           evidence=evidence)
        return {"out": ctx.builder.vector(op, (inputs["a"], inputs["b"]),
                                          (batch, heads, m, s))}
    op = ctx.operation(instance, "VPU", "attention_vpu", mac=mac,
                       read_bytes=attention_bytes + 4 * soft_elements,
                       write_bytes=4 * heads * dim * rows, evidence=evidence)
    return {"out": ctx.builder.vector(op, (inputs["a"], inputs["b"]),
                                      (rows, heads * dim))}


@emitter("generic.activation_gate")
def emit_activation_gate(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, ActivationGateGeometry)
    rows = ctx.rows
    width = geometry.intermediate
    shape = ctx.builder.tensors[inputs["gate"]].shape
    op = ctx.operation(instance, "VPU", "silu_gate",
                       mac=width * rows, elements=width * rows,
                       read_bytes=12 * width * rows, write_bytes=4 * width * rows)
    return {"hidden": ctx.builder.vector(op, (inputs["gate"], inputs["up"]), shape)}


@emitter("generic.residual")
def emit_residual(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, ResidualGeometry)
    rows = ctx.rows
    op = ctx.operation(instance, "VPU", "residual",
                       mac=geometry.width * rows, elements=geometry.width * rows,
                       read_bytes=8 * geometry.width * rows,
                       write_bytes=4 * geometry.width * rows)
    return {"hidden": ctx.builder.vector(op, (inputs["x"], inputs["y"]),
                                         (rows, geometry.width))}


@emitter("generic.sampling")
def emit_sampling(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, SamplingGeometry)
    batch = ctx.point.batch
    op = ctx.operation(instance, "CPU", "sampling",
                       elements=geometry.vocab_size * batch, vectors=batch,
                       length=geometry.vocab_size,
                       read_bytes=4 * geometry.vocab_size * batch,
                       evidence="full-sort comparison-budget scenario, not a selected production sampler")
    ctx.builder.task(op, "execute", "CPU", CpuSpec(
        vectors=batch, length=geometry.vocab_size, read_bytes=op.read_bytes,
        dram_read_bytes=op.dram_read_bytes, evidence=op.evidence),
        (inputs["logits"],), None)
    return {}
