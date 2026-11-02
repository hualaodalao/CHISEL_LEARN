"""Hive L1 analytical GEMM geometry and logical window accounting."""

from .gemm_geom import Command, GemmShape, format_elem_bytes, format_is_mx, legalize
from .windows import Window, WindowCounts, count_windows, iter_windows

__all__ = [
    "Command", "GemmShape", "format_elem_bytes", "format_is_mx", "legalize",
    "Window", "WindowCounts", "count_windows", "iter_windows",
]
