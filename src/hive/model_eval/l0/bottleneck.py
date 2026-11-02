"""瓶颈归因：首位瓶颈 + 主导分项 + 翻转点。

L0 能回答的只有「受哪一侧限制」和「哪一类流量/工作量占大头」。具体花在哪个算子的
哪种资源上是 L1 的事，这里不越界猜。
"""

from __future__ import annotations

from dataclasses import dataclass

from ..frontend.spec import ModelSpec
from .roofline import L0Result


@dataclass(frozen=True)
class Attribution:
    bottleneck: str
    bottleneck_ratio: float
    arithmetic_intensity: float
    machine_balance: float
    dominant_traffic: str
    dominant_traffic_share: float
    dominant_work: str
    dominant_work_share: float
    headroom_note: str


def _dominant(breakdown: dict[str, float]) -> tuple[str, float]:
    if not breakdown:
        return ("none", 0.0)
    key = max(breakdown, key=lambda k: breakdown[k])
    total = sum(breakdown.values())
    return (key, breakdown[key] / total if total else 0.0)


def attribute(result: L0Result, machine_balance: float) -> Attribution:
    traffic_key, traffic_share = _dominant(result.traffic.breakdown)
    work_key, work_share = _dominant(result.work.breakdown)

    if result.bottleneck == "dram":
        note = (
            f"算术强度 {result.arithmetic_intensity:.3f} MAC/B 低于机器平衡点"
            f" {machine_balance:.3f}，带宽侧先饱和"
        )
    else:
        note = (
            f"算术强度 {result.arithmetic_intensity:.3f} MAC/B 高于机器平衡点"
            f" {machine_balance:.3f}，算力侧先饱和"
        )

    return Attribution(
        bottleneck=result.bottleneck,
        bottleneck_ratio=result.bottleneck_ratio,
        arithmetic_intensity=result.arithmetic_intensity,
        machine_balance=machine_balance,
        dominant_traffic=traffic_key,
        dominant_traffic_share=traffic_share,
        dominant_work=work_key,
        dominant_work_share=work_share,
        headroom_note=note,
    )


def kv_weight_crossover(model: ModelSpec, bytes_per_param: float) -> float:
    """KV 流量追平权重流量的上下文长度。超过这个点，加长上下文开始主导带宽。"""
    kv_per_token = model.kv_bytes_per_token
    if kv_per_token == 0:
        return float("inf")
    return model.params_unique * bytes_per_param / kv_per_token
