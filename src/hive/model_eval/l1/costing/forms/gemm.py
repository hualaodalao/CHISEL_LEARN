"""GEMM operator-form entry; command planning stays in gemm_commands."""
from __future__ import annotations

from ...gemm_model import estimate
from ..registry import register_operator_estimator

register_operator_estimator("gemm", "GEMM", estimate, replace=True)
