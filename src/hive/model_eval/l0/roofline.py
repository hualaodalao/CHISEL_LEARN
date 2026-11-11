"""两条 roofline，取 max。L0 的输出是**上界**，不是预测。

`is_upper_bound` 硬编码 True，并由跨层断言 `t_L1 >= t_L0` 守着：L1 枚举出来的
时间不可能短于 L0 的闭式下界，若短了说明有一层算错。
"""

from __future__ import annotations

from dataclasses import dataclass, field

from ..common.workload import Phase, WorkloadPoint
from ..frontend.plan import ModelPlan
from ..frontend.spec import NumFormat
from ..hw.profiles import HwProfile
from .accounts import Account, traffic_account, work_account


@dataclass(frozen=True)
class L0Result:
    model: str
    profile: str
    workload: WorkloadPoint
    fmt: str
    mac_family: str
    work: Account
    traffic: Account
    arithmetic_intensity: float
    t_bandwidth_s: float
    t_compute_s: float
    t_s: float
    bottleneck: str
    bottleneck_ratio: float
    throughput: float
    throughput_unit: str
    bytes_per_param: float
    is_upper_bound: bool = field(default=True, init=False)

    @property
    def mac(self) -> float:
        return self.work.total

    @property
    def traffic_bytes(self) -> float:
        return self.traffic.total


def evaluate(
    model: ModelPlan,
    profile: HwProfile,
    fmt: NumFormat,
    point: WorkloadPoint,
    *,
    use_doc_bytes: bool = True,
) -> L0Result:
    work = work_account(model, point)
    traffic = traffic_account(model, fmt, point, use_doc_bytes=use_doc_bytes)

    bw = profile.dram_effective_bytes_per_s
    mac_rate = profile.mac_per_second(fmt.mac_family)
    t_bw = traffic.total / bw
    t_mac = work.total / mac_rate
    t = max(t_bw, t_mac)

    if t_bw >= t_mac:
        bottleneck = "dram"
        ratio = t_bw / t_mac if t_mac else float("inf")
    else:
        bottleneck = "compute"
        ratio = t_mac / t_bw if t_bw else float("inf")

    if point.phase is Phase.PREFILL:
        throughput = point.seq_len * point.batch / t
    else:
        throughput = point.batch / t

    return L0Result(
        model=model.name,
        profile=profile.name,
        workload=point,
        fmt=fmt.name,
        mac_family=fmt.mac_family,
        work=work,
        traffic=traffic,
        arithmetic_intensity=work.total / traffic.total,
        t_bandwidth_s=t_bw,
        t_compute_s=t_mac,
        t_s=t,
        bottleneck=bottleneck,
        bottleneck_ratio=ratio,
        throughput=throughput,
        throughput_unit=point.throughput_unit,
        bytes_per_param=fmt.bytes_per_param(use_doc_value=use_doc_bytes),
    )
