"""共享模型前端：config → ModelSpec。对 L0 与 L1 是同一件事。"""

from .qwen3_06b import MODELS, get_model
from .spec import (
    FORMATS,
    INT8,
    MXFP4,
    MXFP8,
    LayerTypeSpec,
    ModelSpec,
    NumFormat,
    TensorSpec,
)

__all__ = [
    "FORMATS",
    "INT8",
    "LayerTypeSpec",
    "MODELS",
    "MXFP4",
    "MXFP8",
    "ModelSpec",
    "NumFormat",
    "TensorSpec",
    "get_model",
]
