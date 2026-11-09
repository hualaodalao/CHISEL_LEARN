"""模型规格的共享数据结构。L0 与 L1 读同一份 ModelSpec。

层类型按 `count` 聚合而不假设同构，MAC 分 fixed / 线性 / 二次三项，KV 与
循环状态分开记——这四项是为了让 MoE、混合注意力（DeltaNet/Mamba2）和视觉模型
走同一套代码，而不是给 Qwen3 开后门。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from fractions import Fraction


@dataclass(frozen=True)
class NumFormat:
    """元素格式。scale 字节单列，因为 MX 族的 scale 是独立的流量与端口。"""

    name: str
    elem_bytes: Fraction
    scale_bytes_per_elem: Fraction
    mac_family: str
    doc_bytes_per_param: float | None = None

    @property
    def bytes_per_param_exact(self) -> Fraction:
        return self.elem_bytes + self.scale_bytes_per_elem

    def bytes_per_param(self, *, use_doc_value: bool) -> float:
        """文档口径只覆盖了一部分格式，缺的退回精确值而不是报错。

        用了哪一个由 `bytes_per_param_source` 如实报出来，不靠调用方的开关猜。
        """
        if use_doc_value and self.doc_bytes_per_param is not None:
            return self.doc_bytes_per_param
        return float(self.bytes_per_param_exact)

    def bytes_per_param_source(self, *, use_doc_value: bool) -> str:
        if use_doc_value and self.doc_bytes_per_param is not None:
            return f"doc({self.doc_bytes_per_param})"
        exact = self.bytes_per_param_exact
        return f"exact({exact.numerator}/{exact.denominator})"

BF16 = NumFormat(
    name="BF16",
    elem_bytes=Fraction(2),
    scale_bytes_per_elem=Fraction(0),
    mac_family="int8_fp8_mxfp8_w4a8",
    doc_bytes_per_param=2.0,
)

MXFP8 = NumFormat(
    name="MXFP8",
    elem_bytes=Fraction(1),
    scale_bytes_per_elem=Fraction(1, 32),
    mac_family="int8_fp8_mxfp8_w4a8",
    doc_bytes_per_param=1.031,
)

MXFP4 = NumFormat(
    name="MXFP4",
    elem_bytes=Fraction(1, 2),
    scale_bytes_per_elem=Fraction(1, 32),
    mac_family="int4_mxfp4",
    doc_bytes_per_param=None,
)

INT8 = NumFormat(
    name="INT8",
    elem_bytes=Fraction(1),
    scale_bytes_per_elem=Fraction(0),
    mac_family="int8_fp8_mxfp8_w4a8",
    doc_bytes_per_param=1.0,
)

FORMATS = {fmt.name: fmt for fmt in (MXFP8, MXFP4, INT8)}


@dataclass(frozen=True)
class TensorSpec:
    name: str
    shape: tuple[int, ...]

    @property
    def elements(self) -> int:
        count = 1
        for dim in self.shape:
            count *= dim
        return count


@dataclass(frozen=True)
class LayerTypeSpec:
    """一类层的规格。`count` 份完全相同的实例。

    mac_* 三项都是「每个实例、每个新 token」的量：
      - mac_fixed      与上下文无关
      - mac_s_coef     decode 时随上下文 S 线性增长
      - mac_s2_coef    prefill 时整段 prompt 的 S² 系数（因果 attention 已折半）
    """

    name: str
    count: int
    params_unique: int
    params_streamed_per_token: int
    mac_fixed: int
    mac_s_coef: int
    mac_s2_coef: int
    kv_bytes_per_token: int
    state_bytes: int
    tensors: tuple[TensorSpec, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class ModelSpec:
    name: str
    layer_types: tuple[LayerTypeSpec, ...]
    final_norm_mac: int
    lm_head_params: int
    lm_head_mac: int
    embedding_params: int
    params_unique: int
    params_checkpoint: int
    vocab_size: int
    hidden_size: int
    source: str

    @property
    def params_streamed_per_token(self) -> int:
        """每个 token 实际要流过的参数量。dense 模型等于 params_unique。"""
        streamed = sum(t.count * t.params_streamed_per_token for t in self.layer_types)
        return streamed + self.lm_head_params + self.hidden_size

    @property
    def kv_bytes_per_token(self) -> int:
        return sum(t.count * t.kv_bytes_per_token for t in self.layer_types)

    @property
    def state_bytes(self) -> int:
        return sum(t.count * t.state_bytes for t in self.layer_types)

    @property
    def layer_count(self) -> int:
        return sum(t.count for t in self.layer_types)

    def body_mac_fixed(self) -> int:
        return sum(t.count * t.mac_fixed for t in self.layer_types)

    def body_mac_s_coef(self) -> int:
        return sum(t.count * t.mac_s_coef for t in self.layer_types)

    def body_mac_s2_coef(self) -> int:
        return sum(t.count * t.mac_s2_coef for t in self.layer_types)
