"""Import this module to register every built-in layer and model."""

from .layers import generic as _generic  # noqa: F401  (registers generic.*)
from .layers import qwen3 as _qwen3  # noqa: F401  (registers qwen3.*)
from . import models as _models  # noqa: F401  (registers demo models)
