"""Layer contracts and built-in definitions.

The base registry is model-agnostic.  Model-family definitions are imported by
``frontend.builtins`` (or an explicit extension module), never by this package
initializer.
"""

from .base import (LAYERS, LayerDefinition, LayerGeometry, LayerInstance,
                   LayerL0, LayerRegistry, WeightAllocation, WeightSpec,
                   layer_l0, register_layer, sum_l0)

__all__ = [
    "LAYERS",
    "LayerDefinition",
    "LayerGeometry",
    "LayerInstance",
    "LayerL0",
    "LayerRegistry",
    "WeightAllocation",
    "WeightSpec",
    "layer_l0",
    "register_layer",
    "sum_l0",
]
