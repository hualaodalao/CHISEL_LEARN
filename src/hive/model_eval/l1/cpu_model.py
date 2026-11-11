"""Compatibility wrapper; CPU sampling lives in costing.forms.sampling."""

from __future__ import annotations

from .costing.forms.sampling import estimate

__all__ = ["estimate"]
