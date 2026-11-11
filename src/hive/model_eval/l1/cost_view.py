"""Read-only operator cost view and conservative lower bounds.

The view never adds service time or traffic; it only regroups EngineCost and
schedule information for reports.  Bounds are labelled lower bounds because
unknown service and unmodelled hardware effects are not filled with zero.
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from ..common.opspec import TaskGraph
from ..hw.topology import Topology
from .ledger import EngineCost
from .scheduler import ScheduleResult


@dataclass(frozen=True)
class OperatorCost:
    task_id: str
    op_id: str
    form: str
    engine_kind: str
    instance_id: str | None
    candidate_instances: tuple[str, ...]
    lease: tuple[str, ...]
    seconds: float | None
    known_seconds: float
    dram_read_bytes: int
    dram_write_bytes: int
    local_bytes: int
    footprint_bytes: int | None
    overlap: bool
    overlap_evidence: str
    basis: str
    source: str
    unknown: tuple[str, ...]
    evidence: str


@dataclass(frozen=True)
class CostBounds:
    lower_bound_s: float | None
    engine_busy_lb_s: dict[str, float]
    engine_busy_complete: dict[str, bool]
    dram_lb_s: float | None
    critical_path_lb_s: float | None
    critical_path_complete: bool
    complete: bool
    achievability: str = "lower_bound"


@dataclass(frozen=True)
class Coverage:
    tasks_total: int
    tasks_known: int
    tasks_unknown: int
    unknown_ratio: float
    known_service_s: float


@dataclass(frozen=True)
class UnknownCause:
    cause: str
    category: str
    tasks: int
    source: str
    evidence: str


@dataclass(frozen=True)
class CostView:
    operators: tuple[OperatorCost, ...]
    bounds: CostBounds
    coverage: Coverage
    unknowns: tuple[UnknownCause, ...]


def _form(task) -> str:
    kind = getattr(task.spec, "kind", None)
    if kind:
        return str(kind)
    if task.engine_kind == "DMA":
        return "transfer"
    return task.engine_kind.lower()


def _basis(cost: EngineCost) -> str:
    if not cost.complete:
        return "unknown"
    sources = {phase.source for phase in cost.phases}
    for name in ("scenario", "architecture_peak", "fallback", "structural"):
        if name in sources:
            return name
    return next(iter(sources), "structural")


def _traffic_bytes(cost: EngineCost) -> tuple[int, int, int]:
    read = write = local = 0
    for flow in cost.traffic:
        value = int(flow.logical_bytes)
        if flow.memory_domain == "dram":
            if flow.direction == "read":
                read += value
            else:
                write += value
        else:
            local += value
    return read, write, local


def _footprint(cost: EngineCost, timing) -> int | None:
    if not cost.buffers:
        return 0
    phases = {phase.phase_id: phase for phase in timing.phases}
    events: dict[float, int] = {}
    for request in cost.buffers:
        if request.acquire_phase is not None:
            acquire = phases.get(request.acquire_phase)
            start = None if acquire is None else acquire.start_s
        else:
            start = timing.start_s
        if request.release_phase is not None:
            release = phases.get(request.release_phase)
            end = None if release is None else release.end_s
        else:
            end = timing.end_s
        if start is None or end is None or not math.isfinite(start) or not math.isfinite(end):
            return None
        size = request.allocated_bytes
        if not size:
            continue
        events[start] = events.get(start, 0) + size
        events[end] = events.get(end, 0) - size
    total = peak = 0
    for _, delta in sorted(events.items()):
        total += delta
        peak = max(peak, total)
    return peak


def _task_costs(graph: TaskGraph, costs: dict[str, dict[str, EngineCost]],
                schedule: ScheduleResult, topology: Topology):
    timings = {task.task_id: task for task in schedule.tasks}
    rows = []
    for task in graph.tasks:
        timing = timings.get(task.id)
        estimates = costs.get(task.id, {})
        if timing is None or timing.budget_instance_id is None:
            cost = next(iter(estimates.values()), None)
        else:
            cost = estimates.get(timing.budget_instance_id)
        if cost is None:
            continue
        instance = topology.instances.get(cost.instance_id)
        engine_kind = instance.kind if instance is not None else task.engine_kind
        read, write, local = _traffic_bytes(cost)
        overlap_charges = [charge for charge in cost.ledger.charges
                           if charge.overlap_class != "serial" and charge.evidence.strip()]
        unknown = tuple(sorted(set(cost.excluded_costs)))
        basis = _basis(cost)
        rows.append((task, timing, cost, OperatorCost(
            task_id=task.id,
            op_id=task.parent_op_id,
            form=_form(task),
            engine_kind=engine_kind,
            instance_id=cost.instance_id,
            candidate_instances=tuple(sorted(estimates)),
            lease=cost.lease_resources,
            seconds=cost.known_service_s if cost.complete else None,
            known_seconds=cost.known_service_s,
            dram_read_bytes=read,
            dram_write_bytes=write,
            local_bytes=local,
            footprint_bytes=None if timing is None else _footprint(cost, timing),
            overlap=bool(overlap_charges),
            overlap_evidence=overlap_charges[0].evidence if overlap_charges else "",
            basis=basis,
            source=basis,
            unknown=unknown,
            evidence=str(getattr(task.spec, "evidence", "")),
        )))
    return rows


def _bounds(rows, graph: TaskGraph, dram_effective_bytes_per_s: float | None) -> CostBounds:
    busy: dict[str, float] = {}
    complete: dict[str, bool] = {}
    by_task = {}
    for task, _timing, _cost, row in rows:
        by_task[task.id] = row
        key = row.instance_id or "unassigned"
        complete[key] = complete.get(key, True) and row.seconds is not None
        # known_seconds is a valid lower bound even when another phase is unknown.
        busy[key] = busy.get(key, 0.0) + row.known_seconds
    dram_bytes = sum(row.dram_read_bytes + row.dram_write_bytes for _, _, _, row in rows)
    dram_lb = (dram_bytes / dram_effective_bytes_per_s
               if dram_effective_bytes_per_s else None)
    order = graph.validate()
    tasks = {task.id: task for task in graph.tasks}
    path_sum: dict[str, float] = {}
    path_complete: dict[str, bool] = {}
    for key in order:
        task = tasks[key]
        row = by_task.get(key)
        own = 0.0 if row is None else row.known_seconds
        own_complete = row is not None and row.seconds is not None
        predecessor = max(task.dependencies, key=lambda dep: path_sum.get(dep, 0.0),
                          default=None)
        base = 0.0 if predecessor is None else path_sum.get(predecessor, 0.0)
        deps_complete = all(path_complete.get(dep, False) for dep in task.dependencies)
        path_sum[key] = base + own
        path_complete[key] = own_complete and deps_complete
    cp_lb = max(path_sum.values(), default=None)
    cp_complete = (path_complete.get(max(path_sum, key=path_sum.get), False)
                   if path_sum else False)
    candidates = [value for value in (*busy.values(), dram_lb, cp_lb) if value is not None]
    lower = max(candidates) if candidates else None
    return CostBounds(
        lower_bound_s=lower,
        engine_busy_lb_s=busy,
        engine_busy_complete=complete,
        dram_lb_s=dram_lb,
        critical_path_lb_s=cp_lb,
        critical_path_complete=cp_complete,
        complete=(all(complete.values()) and dram_lb is not None and cp_complete),
    )


def _unknowns(rows) -> tuple[UnknownCause, ...]:
    groups: dict[tuple[str, str], dict] = {}
    for task, _timing, cost, _row in rows:
        for phase in cost.phases:
            if phase.seconds is not None:
                continue
            cause = phase.id.rsplit(":", 1)[-1] or phase.category
            if cause.startswith("handoff"):
                cause = "handoff"
            key = (cause, phase.category)
            entry = groups.setdefault(key, {"tasks": set(), "source": phase.source,
                                            "evidence": phase.evidence})
            entry["tasks"].add(task.id)
        for text in cost.excluded_costs:
            if "unknown service:" in text:
                continue
            if text == "Inter-engine dependency handoff service is unknown":
                # The phase-level handoff cause already carries the affected tasks.
                continue
            entry = groups.setdefault((text, "global"), {"tasks": set(),
                                                         "source": "model-limit",
                                                         "evidence": text})
            entry["tasks"].add(task.id)
    rows_out = [UnknownCause(cause, category, len(value["tasks"]),
                             value["source"], value["evidence"])
                for (cause, category), value in groups.items()]
    return tuple(sorted(rows_out, key=lambda item: (-item.tasks, item.cause, item.category)))


def _coverage(rows) -> Coverage:
    total = len(rows)
    known = sum(1 for _, _, _, row in rows if row.seconds is not None)
    return Coverage(total, known, total - known,
                    (total - known) / total if total else 0.0,
                    sum(row.known_seconds for _, _, _, row in rows))


def build_cost_view(graph: TaskGraph, costs: dict[str, dict[str, EngineCost]],
                    schedule: ScheduleResult, topology: Topology,
                    dram_effective_bytes_per_s: float | None = None) -> CostView:
    rows = _task_costs(graph, costs, schedule, topology)
    return CostView(
        operators=tuple(row for _, _, _, row in rows),
        bounds=_bounds(rows, graph, dram_effective_bytes_per_s),
        coverage=_coverage(rows),
        unknowns=_unknowns(rows),
    )
