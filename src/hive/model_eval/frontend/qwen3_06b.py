"""Qwen3-0.6B → ModelSpec。

推导式子逐行照抄 `src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md` 附录 B，
那是参数量与 MAC 的唯一真源。改这里的任何一行都要先改文档。
"""

from __future__ import annotations

from .spec import LayerTypeSpec, ModelSpec, TensorSpec

SOURCE = "src/hive/doc/model_analysis/Qwen3-0.6B-逐层计算拆解.md#附录B"

CONFIG = {
    "hidden_size": 1024,
    "intermediate_size": 3072,
    "vocab_size": 151936,
    "num_hidden_layers": 28,
    "num_attention_heads": 16,
    "num_key_value_heads": 8,
    "head_dim": 128,
    "kv_elem_bytes": 2,
}


def build(config: dict | None = None) -> ModelSpec:
    cfg = dict(CONFIG if config is None else config)
    H = cfg["hidden_size"]
    I = cfg["intermediate_size"]
    V = cfg["vocab_size"]
    L = cfg["num_hidden_layers"]
    n_h = cfg["num_attention_heads"]
    n_kv = cfg["num_key_value_heads"]
    d_h = cfg["head_dim"]
    kv_elem = cfg["kv_elem_bytes"]

    q = H * (n_h * d_h)
    kv = 2 * H * (n_kv * d_h)
    o = H * (n_h * d_h)
    attn = H + q + kv + o + 2 * d_h + H
    mlp = 3 * H * I
    layer = attn + mlp
    emb = V * H

    nrm = 2 * H
    qkn = 2 * d_h * (n_h + n_kv)
    rope = 4 * (d_h // 2) * (n_h + n_kv)
    mac_fixed = (
        nrm + q + kv + o + qkn + rope + H + nrm + 2 * H * I + I + H * I + H
    )
    mac_s_coef = 2 * n_h * d_h + n_h

    # prefill 的因果 attention 二次项：QK^T 与 AV 两个矩阵各 n_h*d_h*S²/2。
    # 文档 §9.2 的 57,344·S² 不含 softmax 的 n_h·S² 部分，见 l0/excluded.py。
    mac_s2_coef = n_h * d_h

    attention_layer = LayerTypeSpec(
        name="qwen3_full_attention",
        count=L,
        params_unique=layer,
        params_streamed_per_token=layer,
        mac_fixed=mac_fixed,
        mac_s_coef=mac_s_coef,
        mac_s2_coef=mac_s2_coef,
        kv_bytes_per_token=2 * n_kv * d_h * kv_elem,
        state_bytes=0,
        tensors=(
            TensorSpec("q_proj", (n_h * d_h, H)),
            TensorSpec("k_proj", (n_kv * d_h, H)),
            TensorSpec("v_proj", (n_kv * d_h, H)),
            TensorSpec("o_proj", (H, n_h * d_h)),
            TensorSpec("gate_proj", (I, H)),
            TensorSpec("up_proj", (I, H)),
            TensorSpec("down_proj", (H, I)),
        ),
    )

    return ModelSpec(
        name="Qwen3-0.6B",
        layer_types=(attention_layer,),
        final_norm_mac=nrm,
        lm_head_params=emb,
        lm_head_mac=V * H,
        embedding_params=emb,
        params_unique=L * layer + emb + H,
        params_checkpoint=L * layer + emb + H + emb,
        vocab_size=V,
        hidden_size=H,
        source=SOURCE,
    )


MODELS = {"Qwen3-0.6B": build}


def get_model(name: str) -> ModelSpec:
    try:
        builder = MODELS[name]
    except KeyError:
        raise KeyError(f"未知模型 {name!r}；可选 {sorted(MODELS)}") from None
    return builder()
