from __future__ import annotations

from dataclasses import replace

from ..common.opspec import GemmGroup, OpSpec
from ..common.workload import Phase, WorkloadPoint
from ..frontend.spec import ModelSpec, NumFormat

ATTENTION_VARIANTS = ("vpu", "quantized_gemm")


def map_model(model: ModelSpec, fmt: NumFormat, point: WorkloadPoint,
              attention: str = "vpu") -> tuple[OpSpec, ...]:
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
        op = OpSpec(f"{layer}/{name}", layer, name, engine, kind, **values)
        if operations:
            op = replace(op, dependencies=(operations[-1].id,))
        operations.append(op)

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
