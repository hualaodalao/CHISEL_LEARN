"""Shared GEMM command planning.

This module is the costing-layer name for the matmul command planner.  The
implementation currently stays in ``gemm_model`` so graph construction and
cost estimation keep consuming the same sequence; it is re-exported here for
operator forms and future extraction.
"""
from __future__ import annotations

from ..gemm_model import (Command, GemmShape, Window, WindowCounts, count_windows,
                          iter_windows, legalize)

__all__ = ["Command", "GemmShape", "Window", "WindowCounts", "count_windows",
           "iter_windows", "legalize"]
