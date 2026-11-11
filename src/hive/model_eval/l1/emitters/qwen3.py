"""Qwen3-family aggregate emitters.

The decoder block only wires child layers.  GQA attention is a family-specific
module: its QK/softmax/AV shapes, KV cache views and quantized per-head path are
not generic linear algebra, so the aggregate owns that lowering explicitly.
"""

from __future__ import annotations

from ...common.opspec import GemmGroup, GemmSpec, VpuSpec
from ...common.workload import Phase
from ...frontend.layers.qwen3 import GqaAttentionGeometry
from .base import EmitContext, emitter


@emitter("qwen3.decoder_block")
def emit_decoder_block(ctx, instance, inputs):
    x = inputs["hidden"]
    normalized = ctx.emit(ctx.child(instance, "input_norm"), {"hidden": x})
    attention = ctx.emit(ctx.child(instance, "attention"),
                         {"hidden": normalized["quantized"]})
    residual = ctx.emit(ctx.child(instance, "attention_residual"),
                        {"x": x, "y": attention["hidden"]})["hidden"]
    post = ctx.emit(ctx.child(instance, "post_norm"), {"hidden": residual})
    mlp = ctx.emit(ctx.child(instance, "mlp"), {"hidden": post["quantized"]})
    return ctx.emit(ctx.child(instance, "mlp_residual"),
                    {"x": residual, "y": mlp["hidden"]})


@emitter("qwen3.swiglu_mlp")
def emit_swiglu_mlp(ctx, instance, inputs):
    hidden = inputs["hidden"]
    gate = ctx.emit(ctx.child(instance, "gate_proj"), {"x": hidden})["out"]
    up = ctx.emit(ctx.child(instance, "up_proj"), {"x": hidden})["out"]
    gated = ctx.emit(ctx.child(instance, "silu_gate"),
                     {"gate": gate, "up": up})["hidden"]
    return {"hidden": ctx.emit(ctx.child(instance, "down_proj"),
                               {"x": gated})["out"]}


@emitter("qwen3.gqa_attention")
def emit_gqa_attention(ctx, instance, inputs):
    geometry = instance.geometry
    assert isinstance(geometry, GqaAttentionGeometry)
    hidden = inputs["hidden"]
    q = ctx.emit(ctx.child(instance, "q_proj"), {"x": hidden})["out"]
    k = ctx.emit(ctx.child(instance, "k_proj"), {"x": hidden})["out"]
    v = ctx.emit(ctx.child(instance, "v_proj"), {"x": hidden})["out"]
    if geometry.qk_norm:
        normalized = ctx.emit(ctx.child(instance, "qk_norm"), {"q": q, "k": k})
        q, k = normalized["q"], normalized["k"]
    if geometry.rope:
        rotated = ctx.emit(ctx.child(instance, "rope"), {"q": q, "k": k})
        q, k = rotated["q"], rotated["k"]
    cache = ctx.emit(ctx.child(instance, "kv_transfer"), {"k": k, "v": v})
    if ctx.point.seq_len:
        if ctx.variant == "vpu":
            scores = ctx.emit(ctx.child(instance, "qk"),
                              {"a": q, "b": cache["k_cache"]})["out"]
            probabilities = ctx.emit(ctx.child(instance, "softmax"),
                                     {"logits": scores})["probabilities"]
            context = ctx.emit(ctx.child(instance, "av"),
                               {"a": probabilities, "b": cache["v_cache"]})["out"]
        elif ctx.variant == "quantized_gemm":
            context = _emit_quantized_attention(ctx, instance, q, k, v, cache)
        else:
            raise ValueError(f"unknown attention variant: {ctx.variant}")
    else:
        width = ctx.builder.tensors[q].shape[-1]
        context = ctx.builder.tensor(f"{ctx.scope(instance)}:empty-attention-context",
                                     (ctx.rows, width), "FP32", external=True)
    return {"hidden": ctx.emit(ctx.child(instance, "o_proj"), {"x": context})["out"]}


def _emit_quantized_attention(ctx, instance, q, k, v, cache):
    geometry = instance.geometry
    assert isinstance(geometry, GqaAttentionGeometry)
    rows = ctx.rows
    batch = ctx.point.batch
    seq = ctx.point.seq_len
    heads = geometry.heads
    dim = geometry.head_dim
    qk_child = ctx.child(instance, "qk")
    softmax_child = ctx.child(instance, "softmax")
    av_child = ctx.child(instance, "av")
    if ctx.point.phase is Phase.DECODE:
        attention_mac = heads * dim * seq * batch
        soft_elements = heads * seq * batch
        qk_shape = GemmGroup(batch, seq, dim, heads)
        av_shape = GemmGroup(batch, dim, seq, heads)
    else:
        attention_mac = heads * dim * seq * (seq + 1) * batch // 2
        soft_elements = heads * seq * (seq + 1) * batch // 2
        qk_shape = GemmGroup(seq, seq, dim, heads * batch)
        av_shape = GemmGroup(seq, dim, seq, heads * batch)
    attention_bytes = 2 * heads * seq * dim * rows
    evidence = ("quantized QK/AV scenario; prefill issues dense masked matrices; "
                "precision, packing and quantization service are unverified")
    qk_op = ctx.operation(qk_child, "GEMM", "attention_gemm", mac=attention_mac,
                          gemms=(qk_shape,), read_bytes=attention_bytes,
                          write_bytes=4 * soft_elements, evidence=evidence)
    softmax_op = ctx.operation(softmax_child, "VPU", "softmax", mac=soft_elements,
                               elements=soft_elements, vectors=heads * rows,
                               length=seq, read_bytes=12 * soft_elements,
                               write_bytes=4 * soft_elements,
                               evidence="three FP32 passes; prefill causal elements, not full masked matrix")
    av_op = ctx.operation(av_child, "GEMM", "attention_gemm", mac=attention_mac,
                          gemms=(av_shape,),
                          read_bytes=attention_bytes + 4 * soft_elements,
                          write_bytes=4 * heads * dim * rows, evidence=evidence)

    q_width = ctx.builder.tensors[q].shape[-1]
    q_heads = q_width // dim
    k_cache = cache["k_cache"]
    v_cache = cache["v_cache"]
    kv_heads = ctx.builder.tensors[k_cache].shape[1]
    layer = ctx.scope(instance)
    m = 1 if ctx.point.phase is Phase.DECODE else seq
    contexts = []
    quantized_keys: dict[tuple[int, int], str] = {}
    quantized_values: dict[tuple[int, int], str] = {}
    for batch_index in range(batch):
        for head in range(q_heads):
            suffix = f"b{batch_index}-h{head}"
            kv_head = head * kv_heads // q_heads
            q_view = ctx.builder.view(
                f"{layer}:Q:{suffix}", q, (m, dim),
                starts=(batch_index * m, head * dim),
                stops=((batch_index + 1) * m, (head + 1) * dim))
            k_view = ctx.builder.view(
                f"{layer}:K:{suffix}", k_cache, (dim, seq),
                starts=(batch_index, kv_head, 0, 0),
                stops=(batch_index + 1, kv_head + 1, dim, seq))
            v_view = ctx.builder.view(
                f"{layer}:V:{suffix}", v_cache, (seq, dim),
                starts=(batch_index, kv_head, 0, 0),
                stops=(batch_index + 1, kv_head + 1, seq, dim))
            qa = ctx.builder.convert(qk_op, q_view, ctx.fmt.name,
                                     suffix + "-quantize-q")
            key = (batch_index, kv_head)
            if key not in quantized_keys:
                quantized_keys[key] = ctx.builder.convert(
                    qk_op, k_view, ctx.fmt.name,
                    f"b{batch_index}-kv{kv_head}-quantize-k", scale_axis=0)
            kb = quantized_keys[key]
            mac = dim * seq if m == 1 else dim * seq * (seq + 1) // 2
            scores = ctx.builder.gemm(qk_op, qa, kb, GemmSpec(
                m, seq, dim, ctx.fmt.name, ctx.fmt.name, ctx.output_dtype,
                ctx.block_size, mac, evidence=qk_op.evidence), ctx.geometry, suffix)
            scores = ctx.builder.convert(qk_op, scores, "FP32", suffix + "-dequantize")
            elements = seq if m == 1 else seq * (seq + 1) // 2
            probabilities = ctx.builder.vector(
                softmax_op, (scores,), (m, seq), suffix=suffix,
                spec=VpuSpec("softmax", mac=elements, elements=elements, vectors=m,
                             length=seq, read_bytes=12 * elements,
                             write_bytes=4 * elements,
                             dram_read_bytes=4 * elements,
                             dram_write_bytes=4 * elements))
            pa = ctx.builder.convert(av_op, probabilities, ctx.fmt.name,
                                     suffix + "-quantize-p")
            if key not in quantized_values:
                quantized_values[key] = ctx.builder.convert(
                    av_op, v_view, ctx.fmt.name,
                    f"b{batch_index}-kv{kv_head}-quantize-v", scale_axis=0)
            vb = quantized_values[key]
            context = ctx.builder.gemm(av_op, pa, vb, GemmSpec(
                m, dim, seq, ctx.fmt.name, ctx.fmt.name, ctx.output_dtype,
                ctx.block_size, mac, evidence=av_op.evidence), ctx.geometry, suffix)
            contexts.append(ctx.builder.convert(av_op, context, "FP32",
                                                suffix + "-dequantize"))
    return ctx.builder.task(av_op, "join-heads", "VPU", VpuSpec(
        "convert", elements=rows * q_width,
        read_bytes=4 * rows * q_width, write_bytes=4 * rows * q_width,
        dram_read_bytes=4 * rows * q_width, dram_write_bytes=4 * rows * q_width,
        evidence="head assembly/layout service is unknown"), tuple(contexts),
        (rows, q_width))
