"""数值格式的共享数据结构。

模型结构由 ``frontend.plan.ModelPlan`` 与 ``frontend.layers`` 的层注册表表达；
本模块只保留 L0/L1 共用的 ``NumFormat``。
"""

from __future__ import annotations

from dataclasses import dataclass
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
