"""Built-in operator forms.

Importing this package registers the built-in operator estimators.  Extension
code may register additional (spec kind, engine kind) entries without touching
the engine model modules.
"""
from __future__ import annotations

from . import gemm, sampling, transfer, vpu  # noqa: F401

__all__ = ["gemm", "sampling", "transfer", "vpu"]
