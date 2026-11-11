"""共享模型前端：config → ModelSpec 或 ModelPlan。

原有 ``qwen3_06b.get_model`` 保留为旧路径；新代码使用中立 registry。
"""

from .layers import (LayerDefinition, LayerInstance, LayerL0, WeightAllocation,
                     WeightSpec, register_layer)
from .plan import ModelPlan
from .qwen3_06b import MODELS, get_model
from .registry import get_model as get_model_plan
from .registry import list_models, register_model
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
    "LayerDefinition",
    "LayerInstance",
    "LayerL0",
    "LayerTypeSpec",
    "MODELS",
    "MXFP4",
    "MXFP8",
    "ModelPlan",
    "ModelSpec",
    "NumFormat",
    "TensorSpec",
    "WeightAllocation",
    "WeightSpec",
    "get_model",
    "get_model_plan",
    "list_models",
    "register_layer",
    "register_model",
]
