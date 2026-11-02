from __future__ import annotations

import math
from dataclasses import dataclass, field

from ..common.opspec import OpSpec
from ..common.workload import WorkloadPoint
from ..frontend.spec import ModelSpec, NumFormat
from ..hw.geometry import ArrayGeometry, load_array_geometry
from ..hw.profiles import HwProfile
from ..l0.roofline import evaluate as evaluate_l0
from . import kernels
from .ledger import CATEGORIES, Ledger, fold
from .mapping import map_model
from .params import ModelParams


@dataclass(frozen=True)
class OpResult:
    op: OpSpec
    ledger: Ledger
    metrics: dict
    t_s: float
    attribution_s: dict[str, float]
    dominant_resources: tuple[str, ...]
    start_s: float = 0
    end_s: float = 0

    def to_dict(self, debug: bool = False) -> dict:
        result = {"id": self.op.id, "layer": self.op.layer, "name": self.op.name,
                  "engine": self.op.engine, "kind": self.op.kind,
                  "dependencies": list(self.op.dependencies), "evidence": self.op.evidence,
                  "metrics": self.metrics, "t_s": self.t_s, "start_s": self.start_s,
                  "end_s": self.end_s, "attribution_s": self.attribution_s,
                  "dominant_resources": list(self.dominant_resources),
                  "excluded_costs": sorted(set(self.ledger.excluded))}
        if debug:
            result["ledger"] = self.ledger.to_dict()
        return result


@dataclass(frozen=True)
class L1Result:
    model: str
    profile: str
    workload: WorkloadPoint
    fmt: str
    attention: str
    operations: tuple[OpResult, ...]
    t_s: float
    l0_t_s: float
    cross_level_comparable: bool
    attribution_s: dict[str, float]
    metrics: dict
    handoffs: int
    modeled_throughput: float | None
    excluded_costs: tuple[str, ...]
    performance_valid: bool = field(default=False, init=False)
    data_valid: bool = field(default=False, init=False)

    def to_dict(self, debug: bool = False) -> dict:
        layers = {}
        for result in self.operations:
            layer = layers.setdefault(result.op.layer, {"layer": result.op.layer, "t_s": 0,
                                                        "attribution_s": dict.fromkeys(CATEGORIES, 0.0),
                                                        "operators": 0})
            layer["t_s"] += result.t_s
            layer["operators"] += 1
            for category, seconds in result.attribution_s.items():
                layer["attribution_s"][category] += seconds
        return {"model": self.model, "profile": self.profile, "fmt": self.fmt,
                "phase": self.workload.phase.value, "seq_len": self.workload.seq_len,
                "batch": self.workload.batch, "attention": self.attention,
                "model_kind": "deterministic_analytic_ledger", "active_cores": 1,
                "t_modeled_s": self.t_s, "l0_time_lower_bound_s": self.l0_t_s,
                "cross_level_comparable": self.cross_level_comparable,
                "modeled_throughput_excluding_unknown_costs": self.modeled_throughput,
                "throughput_unit": self.workload.throughput_unit,
                "coverage_complete": False, "performance_valid": False, "data_valid": False,
                "handoffs": self.handoffs, "attribution_s": self.attribution_s,
                "metrics": self.metrics, "layers": list(layers.values()),
                "operators": [op.to_dict(debug) for op in self.operations],
                "excluded_costs": list(self.excluded_costs),
                "attribution_rule": "winner takes folded/overlapped interval; raw demands are not added"}


def compose(ledger: Ledger) -> tuple[float, dict[str, float], tuple[str, ...]]:
    occupied = {}
    for charge in ledger.charges:
        if charge.overlap_class == "serial":
            continue
        key = (charge.overlap_class, charge.resource, charge.instance)
        previous = occupied.get(key)
        if previous is not None and previous != charge.origin:
            raise ValueError("独立数据流不能在同一物理资源上以 max 假定并行")
        occupied[key] = charge.origin
    folded = fold(ledger)
    groups = {}
    selected = []
    for charge in folded:
        if charge.overlap_class == "serial":
            selected.append(charge)
        else:
            groups.setdefault(charge.overlap_class, []).append(charge)
    selected.extend(max(charges, key=lambda c: c.seconds) for charges in groups.values())
    attribution = dict.fromkeys(CATEGORIES, 0.0)
    for charge in selected:
        attribution[charge.category] += charge.seconds
    total = sum(attribution.values())
    return total, attribution, tuple(c.dominant_resource for c in selected)


def evaluate(model: ModelSpec, profile: HwProfile, fmt: NumFormat,
             point: WorkloadPoint, *, attention: str = "vpu",
             params: ModelParams | None = None,
             geometry: ArrayGeometry | None = None) -> L1Result:
    params = params or ModelParams()
    geometry = geometry or load_array_geometry()
    operations = map_model(model, fmt, point, attention)
    results = []
    attribution = dict.fromkeys(CATEGORIES, 0.0)
    totals = {}
    seen = set()
    excluded = {"Single-core serial operator schedule; multi-core partition/communication is not modeled",
                "Activation quantization, scale generation and layout conversion service are unmodeled",
                "CPU command submission/scheduler overhead is unmodeled"}
    if not params.sync_us:
        excluded.add("Inter-engine handoff/synchronization service is unknown (no numerical charge)")
    previous_engine = None
    handoffs = 0
    cursor = 0.0
    for op in operations:
        if op.id in seen or any(dep not in seen for dep in op.dependencies):
            raise ValueError("算子 DAG 必须为唯一 ID 的拓扑序")
        if previous_engine is not None and op.engine != previous_engine:
            handoffs += 1
            delay = params.sync_us * 1e-6
            cursor += delay
            attribution["handoff"] += delay
        ledger, metrics = kernels.cost(op, profile, fmt.name, geometry, params)
        seconds, shares, dominant = compose(ledger)
        results.append(OpResult(op, ledger, metrics, seconds, shares, dominant,
                                start_s=cursor, end_s=cursor + seconds))
        cursor += seconds
        for category, value in shares.items():
            attribution[category] += value
        for name, value in metrics.items():
            if isinstance(value, (int, float)) and name != "array_utilization":
                totals[name] = totals.get(name, 0) + value
        excluded.update(ledger.excluded)
        seen.add(op.id)
        previous_engine = op.engine
    totals["array_utilization"] = (totals.get("compute_cycles", 0) / totals["array_cycles"]
                                     if totals.get("array_cycles") else 0)
    total = sum(attribution.values())
    if not math.isclose(total, cursor, rel_tol=1e-12, abs_tol=1e-12):
        raise AssertionError("五类归因必须覆盖已计入的整个时间线")
    l0 = evaluate_l0(model, profile, fmt, point, use_doc_bytes=False)
    totals["l0_reference_macs"] = l0.mac
    totals["extra_semantic_macs_vs_l0"] = totals.get("semantic_macs", 0) - l0.mac
    comparable = profile.freq_hz is not None and profile.local_iface_bytes_per_s_per_core is not None
    if comparable and total + 1e-12 < l0.t_s:
        raise AssertionError(f"L1 {total} 低于同口径 L0 资源下界 {l0.t_s}")
    if not comparable:
        excluded.add("Missing profile service rates: partial L1 cannot be compared against L0 total bound")
    return L1Result(model.name, profile.name, point, fmt.name, attention, tuple(results),
                    total, l0.t_s, comparable, attribution, totals, handoffs,
                    point.tokens_produced / total if comparable and total else None,
                    tuple(sorted(excluded)))
