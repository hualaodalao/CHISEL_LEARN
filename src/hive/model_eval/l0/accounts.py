"""把模型压成两个标量：总 MAC 与总字节。闭式，无循环枚举。

分项（breakdown）只为归因，合计才是 roofline 的输入。两者必须自洽——
`total == sum(breakdown.values())` 由测试守着。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ..common.workload import Phase, WorkloadPoint
from ..frontend.plan import ModelPlan
from ..frontend.spec import NumFormat


@dataclass(frozen=True)
class Account:
    total: float
    breakdown: dict[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        summed = sum(self.breakdown.values())
        if self.breakdown and abs(summed - self.total) > max(1e-6, abs(self.total) * 1e-12):
            raise ValueError(f"分项之和 {summed} 与合计 {self.total} 不符")

    def share(self, key: str) -> float:
        if self.total == 0:
            return 0.0
        return self.breakdown.get(key, 0.0) / self.total


def work_account(model: ModelPlan, point: WorkloadPoint) -> Account:
    body_fixed = model.body_mac_fixed()
    body_s = model.body_mac_s_coef()
    body_s2 = model.body_mac_s2_coef()
    S = point.seq_len
    batch = point.batch

    if point.phase is Phase.DECODE:
        parts = {
            "body_fixed": float(body_fixed * batch),
            "body_context_linear": float(body_s * S * batch),
            "final_norm": float(model.final_norm_mac * batch),
            "lm_head": float(model.lm_head_mac * batch),
        }
    elif point.phase is Phase.PREFILL:
        # 线性项按每 token (28L + 末层 norm) 算，与文档 §9.2 的 441.0 M 同口径。
        per_token_linear = body_fixed + model.final_norm_mac
        parts = {
            "linear": float(per_token_linear * S * batch),
            "quadratic": float(body_s2 * S * S * batch),
            "lm_head": float(model.lm_head_mac * batch),
        }
    else:
        # 视觉模型：每帧工作量固定，等价于无上下文项的 decode。
        parts = {
            "body_fixed": float(body_fixed * batch),
            "final_norm": float(model.final_norm_mac * batch),
            "head": float(model.lm_head_mac * batch),
        }

    parts = {k: v for k, v in parts.items() if v}
    return Account(total=sum(parts.values()), breakdown=parts)


def traffic_account(model: ModelPlan, fmt: NumFormat, point: WorkloadPoint, *, use_doc_bytes: bool) -> Account:
    bpp = fmt.bytes_per_param(use_doc_value=use_doc_bytes)
    S = point.seq_len
    batch = point.batch

    if point.phase is Phase.DECODE:
        # 权重一步只读一次，与 batch 无关；KV 每条序列各一份。
        parts = {
            "weights": model.params_streamed_per_token * bpp,
            "kv_read": float(model.kv_bytes_per_token * S * batch),
            "recurrent_state": float(model.state_bytes * batch),
        }
    elif point.phase is Phase.PREFILL:
        # 权重整段只读一次，与文档 §9.2 的「权重带宽时间（只读一次）」同口径。
        parts = {
            "weights": model.params_unique * bpp,
            "kv_write": float(model.kv_bytes_per_token * S * batch),
            "recurrent_state": float(model.state_bytes * batch),
        }
    else:
        parts = {
            "weights": model.params_streamed_per_token * bpp,
            "recurrent_state": float(model.state_bytes * batch),
        }

    parts = {k: v for k, v in parts.items() if v}
    return Account(total=sum(parts.values()), breakdown=parts)
