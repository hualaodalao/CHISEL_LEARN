"""共享模型前端：config → ModelPlan。

模型结构由层注册表（``frontend.layers``）与 ``ModelPlan`` 表达；模型名通过中立
``frontend.registry`` 解析。数值格式见 ``frontend.spec``。
"""

from .layers import (LayerDefinition, LayerInstance, LayerL0, WeightAllocation,
                     WeightSpec, register_layer)
from .plan import ModelPlan
from .registry import get_model, list_models, register_model
from .spec import FORMATS, INT8, MXFP4, MXFP8, NumFormat

__all__ = [
    "FORMATS",
    "INT8",
    "LayerDefinition",
    "LayerInstance",
    "LayerL0",
    "MXFP4",
    "MXFP8",
    "ModelPlan",
    "NumFormat",
    "WeightAllocation",
    "WeightSpec",
    "get_model",
    "list_models",
    "register_layer",
    "register_model",
]