"""L0 闭式 roofline：把模型压成 (MAC, 字节) 两个标量，与峰值做比值取 max。

输出是上界，不是预测。`L0Result.is_upper_bound` 不可被调用方改写。
"""

from .accounts import Account, traffic_account, work_account
from .bottleneck import Attribution, attribute, kv_weight_crossover
from .excluded import for_workload
from .roofline import L0Result, evaluate
from .torch_check import CrossCheck, cross_check

__all__ = [
    "Account",
    "Attribution",
    "CrossCheck",
    "L0Result",
    "attribute",
    "cross_check",
    "evaluate",
    "for_workload",
    "kv_weight_crossover",
    "traffic_account",
    "work_account",
]
