from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field, replace
from typing import Callable

from ..common.opspec import TaskGraph, VpuSpec
from ..common.workload import WorkloadPoint
from ..frontend.layers.base import LAYERS, LayerInstance
from ..frontend.plan import ModelPlan
from ..frontend.spec import ModelSpec, NumFormat
from ..hw.geometry import ArrayGeometry, load_array_geometry
from ..hw.profiles import HwProfile
from ..hw.topology import Topology, default_topology
from ..l0.roofline import evaluate as evaluate_l0
from . import kernels
from .compose import lower_plan
from .cost_view import build_cost_view
from .dispatchers import dispatch
from .ledger import EngineCost, Ledger, Phase, ResourceClaim
from .mapping import lower_model
from .params import ModelParams
from .residency import BufferPlan, ExecutionPlan
from .scheduler import ScheduleResult


def _sum_metrics(costs: list[EngineCost]) -> dict:
    totals = {}
    for cost in costs:
        for name, value in cost.metrics.items():
            if isinstance(value, (int, float)) and not isinstance(value, bool) and name != "array_utilization":
                totals[name] = totals.get(name, 0) + value
    totals["dram_bytes"] = sum(flow.logical_bytes for cost in costs for flow in cost.traffic
                               if flow.memory_domain == "dram")
    totals["norm_calls"] = 0
    totals["softmax_reductions"] = 0
    totals["array_utilization"] = (totals.get("compute_cycles", 0) / totals["array_cycles"]
                                    if totals.get("array_cycles") else 0)
    return totals


def _span(tasks: list[dict], start: str, end: str) -> float | None:
    if not tasks:
        return 0.0
    if any(task[start] is None or task[end] is None for task in tasks):
        return None
    return max(task[end] for task in tasks) - min(task[start] for task in tasks)


def _leaf_op_ids(layer: LayerInstance) -> tuple[str, ...]:
    definition = LAYERS.get(layer.type, layer.version)
    if definition.children is None:
        return (layer.op_name or layer.id,)
    ids: list[str] = []
    for child in definition.children(layer):
        ids.extend(_leaf_op_ids(child))
    return tuple(ids)


def estimate_graph(graph: TaskGraph, topology: Topology,
                   execution_plan: ExecutionPlan) -> dict[str, dict[str, EngineCost]]:
    graph.validate()
    costs = {}
    for task in graph.tasks:
        candidates = topology.candidates(task)
        if not candidates:
            raise ValueError(f"{task.id}: no candidate engine instances")
        estimates = {}
        failures = []
        for instance in candidates:
            try:
                topology.validate_access(graph, task, instance)
                estimates[instance.id] = kernels.estimate(task, instance, execution_plan)
            except ValueError as error:
                failures.append(f"{instance.id}: {error}")
        if not estimates:
            raise ValueError(f"{task.id}: no feasible engine configuration: {'; '.join(failures)}")
        costs[task.id] = estimates
    return costs


def _handoffs(graph: TaskGraph, costs: dict[str, dict[str, EngineCost]], topology: Topology):
    tasks = {task.id: task for task in graph.tasks}
    for task in graph.tasks:
        dependencies = [dep for dep in task.dependencies
                        if tasks[dep].engine_kind != task.engine_kind
                        and "DMA" not in (tasks[dep].engine_kind, task.engine_kind)]
        if not dependencies:
            continue
        for instance_id, cost in tuple(costs[task.id].items()):
            instance = topology.instances[instance_id]
            sync = instance.params.sync_us
            prefix = []
            previous = ()
            for index, dep in enumerate(sorted(dependencies)):
                id = task.id + f":handoff-{index}"
                prefix.append(Phase(id, None if sync is None else sync * 1e-6,
                                    "handoff", previous,
                                    (ResourceClaim(instance.resolve("engine")),),
                                    source="unknown" if sync is None else "scenario",
                                    evidence=f"dependency {dep} -> {task.id}; explicit inter-engine visibility service"))
                previous = (id,)
            phases = tuple(replace(phase, dependencies=previous) if not phase.dependencies else phase
                           for phase in cost.phases)
            excluded = cost.excluded_costs
            if sync is None:
                excluded = (*excluded, "Inter-engine dependency handoff service is unknown")
            costs[task.id][instance_id] = replace(cost, phases=(*prefix, *phases), excluded_costs=excluded)


def evaluate_graph(graph: TaskGraph, topology: Topology, *,
                   execution_plan: ExecutionPlan | None = None,
                   buffer_plan: BufferPlan | None = None,
                   scheduling_policy: str = "list",
                   costs: dict[str, dict[str, EngineCost]] | None = None,
                   dispatcher: str | Callable[..., ScheduleResult] | None = None) -> ScheduleResult:
    execution_plan = execution_plan or ExecutionPlan(activation_policy="stream")
    estimates = costs if costs is not None else estimate_graph(graph, topology, execution_plan)
    _handoffs(graph, estimates, topology)
    if callable(dispatcher):
        return dispatcher(graph, estimates, topology, buffer_plan)
    return dispatch(dispatcher or scheduling_policy, graph, estimates, topology, buffer_plan)


@dataclass(frozen=True)
class L1Result:
    model: str
    profile: str
    workload: WorkloadPoint
    fmt: str
    attention: str
    graph: TaskGraph
    topology: Topology
    execution_plan: ExecutionPlan
    buffer_plan: BufferPlan
    schedule: ScheduleResult
    costs: dict[str, dict[str, EngineCost]]
    l0_t_s: float
    metrics: dict
    excluded_costs: tuple[str, ...]
    cross_level_comparable: bool = False
    comparison_reason: str = "L0 is a cluster roofline reference; L1 routes engines and explicit staged/stream traffic differently"
    plan: ModelPlan | None = None
    dram_effective_bytes_per_s: float | None = None
    performance_valid: bool = field(default=False, init=False)
    data_valid: bool = field(default=False, init=False)

    @property
    def makespan_s(self):
        return self.schedule.makespan_s

    @property
    def known_service_budget_s(self):
        return sum(cost.known_service_s for cost in self.selected_costs.values())

    @property
    def projection_makespan_s(self):
        return self.schedule.projection_makespan_s

    @property
    def projection_attribution_s(self):
        return self.schedule.projection_attribution_s if self.projection_makespan_s is not None else None

    @property
    def modeled_throughput(self):
        return None if self.makespan_s is None or self.excluded_costs or not self.makespan_s else self.workload.tokens_produced / self.makespan_s

    @property
    def selected_costs(self) -> dict[str, EngineCost]:
        return {task.task_id: self.costs[task.task_id][task.budget_instance_id]
                for task in self.schedule.tasks if task.budget_instance_id is not None}

    def to_dict(self, debug: bool = False, detailed: bool = True) -> dict:
        view = build_cost_view(self.graph, self.costs, self.schedule, self.topology,
                               self.dram_effective_bytes_per_s)
        scheduled = {task.task_id: task for task in self.schedule.tasks}
        selected = self.selected_costs
        tasks = []
        for task in self.graph.tasks:
            timing = scheduled[task.id]
            cost = selected[task.id]
            phase_times = {phase.phase_id: phase for phase in timing.phases}
            phases = []
            for phase in cost.phases:
                phase_time = phase_times[phase.id]
                phases.append({**asdict(phase), "start_s": phase_time.start_s,
                    "end_s": phase_time.end_s, "projection_start_s": phase_time.projection_start_s,
                    "projection_end_s": phase_time.projection_end_s})
            tasks.append({**asdict(task), "instance_id": timing.instance_id,
                "budget_instance_id": timing.budget_instance_id,
                "cost_basis": "bound_instance" if timing.instance_id else "unbound_candidate_budget",
                "status": timing.status, "blocked_constraints": list(timing.blocked_constraints),
                "start_s": timing.start_s, "end_s": timing.end_s,
                "projection_start_s": timing.projection_start_s,
                "projection_end_s": timing.projection_end_s,
                "known_service_budget_s": cost.known_service_s, "phases": phases,
                "waits": [asdict(wait) for wait in timing.waits], "metrics": cost.metrics,
                "excluded_costs": list(cost.excluded_costs)})
        operators = []
        layers = {}
        for op in self.graph.operations:
            members = [task for task in tasks if task["parent_op_id"] == op.id]
            op_costs = [selected[task["id"]] for task in members]
            metrics = _sum_metrics(op_costs)
            for task in self.graph.tasks:
                if task.parent_op_id == op.id and isinstance(task.spec, VpuSpec):
                    if task.spec.kind == "norm":
                        metrics["norm_calls"] += task.spec.vectors
                    if task.spec.kind == "softmax":
                        metrics["softmax_reductions"] += task.spec.vectors
            excluded = sorted({item for cost in op_costs for item in cost.excluded_costs})
            row = {**asdict(op), "tasks": [task["id"] for task in members],
                   "known_service_budget_s": sum(cost.known_service_s for cost in op_costs),
                   "span_s": _span(members, "start_s", "end_s"),
                   "projection_span_s": _span(members, "projection_start_s", "projection_end_s"),
                   "metrics": metrics, "excluded_costs": excluded}
            if debug:
                ledger = Ledger([charge for cost in op_costs for charge in cost.ledger.charges],
                                coupling={pair: relation for cost in op_costs
                                          for pair, relation in cost.ledger.coupling.items()},
                                excluded=excluded)
                row["ledger"] = ledger.to_dict()
            operators.append(row)
            layers.setdefault(op.layer, []).append(row)
        layer_rows = []
        for layer, members in layers.items():
            layer_tasks = [task for task in tasks if task["parent_op_id"] in {op["id"] for op in members}]
            layer_rows.append({"layer": layer, "operators": len(members), "tasks": len(layer_tasks),
                "known_service_budget_s": sum(op["known_service_budget_s"] for op in members),
                "span_s": _span(layer_tasks, "start_s", "end_s"),
                "projection_span_s": _span(layer_tasks, "projection_start_s", "projection_end_s")})
        layer_tree = None
        if self.plan is not None:
            layer_tree = []
            for layer in self.plan.iter_layers():
                definition = LAYERS.get(layer.type, layer.version)
                op_ids = set(_leaf_op_ids(layer))
                members = [task for task in tasks if task["parent_op_id"] in op_ids]
                costs = [selected[task["id"]] for task in members if task["id"] in selected]
                layer_tree.append({
                    "id": layer.id,
                    "type": LAYERS.canonical(layer.type, layer.version)[0],
                    "aggregate": definition.children is not None,
                    "operators": len(op_ids),
                    "tasks": len(members),
                    "known_service_budget_s": sum(cost.known_service_s for cost in costs),
                    "span_s": _span(members, "start_s", "end_s"),
                    "projection_span_s": _span(members, "projection_start_s", "projection_end_s"),
                })
        payload = {"schema_version": 2, "model": self.model, "profile": self.profile, "fmt": self.fmt,
                "phase": self.workload.phase.value, "seq_len": self.workload.seq_len,
                "batch": self.workload.batch, "attention": self.attention,
                "model_kind": "deterministic_resource_schedule", "active_cores": len(self.topology.active_cores),
                "scheduling_policy": self.schedule.policy, "makespan_s": self.makespan_s,
                "known_service_budget_s": self.known_service_budget_s,
                "budget_basis": "bound instances; unbound tasks retain explicitly labeled candidate estimates",
                "projection_makespan_s": self.projection_makespan_s,
                "projection_attribution_s": self.projection_attribution_s,
                "attribution_s": self.schedule.attribution_s if self.makespan_s is not None else None,
                "l0_time_lower_bound_s": self.l0_t_s,
                "cross_level_comparable": self.cross_level_comparable,
                "comparison_reason": self.comparison_reason, "modeled_throughput": self.modeled_throughput,
                "throughput_unit": self.workload.throughput_unit, "coverage_complete": False,
                "performance_valid": False, "data_valid": False, "metrics": self.metrics,
                "layers": layer_rows, "layer_tree": layer_tree,
                "operators": operators, "tasks": tasks,
                "traffic": [{**asdict(flow), "budget_instance_id": cost.instance_id,
                             "cost_basis": "bound_instance" if scheduled[cost.task_id].instance_id
                             else "unbound_candidate_budget"}
                            for cost in selected.values() for flow in cost.traffic],
                "resource_busy_s": self.schedule.resource_busy_s,
                "projection_resource_busy_s": self.schedule.projection_resource_busy_s,
                "memory_peak_bytes": self.schedule.memory_peak_bytes,
                "projection_memory_peak_bytes": self.schedule.projection_memory_peak_bytes,
                "topology": self.topology.to_dict(), "buffer_plan": self.buffer_plan.to_dict(),
                "graph": asdict(self.graph), "schedule": self.schedule.to_dict(),
                "operator_costs": [asdict(item) for item in view.operators],
                "bounds": asdict(view.bounds),
                "coverage": asdict(view.coverage),
                "top_unknowns": [asdict(item) for item in view.unknowns],
                "excluded_costs": list(self.excluded_costs)}
        if not detailed:
            for key in ("layer_tree", "operators", "tasks", "traffic", "resource_busy_s",
                        "projection_resource_busy_s", "memory_peak_bytes",
                        "projection_memory_peak_bytes", "topology", "buffer_plan",
                        "graph", "schedule"):
                payload.pop(key, None)
        return json.loads(json.dumps(payload, allow_nan=False))


def evaluate(model: ModelSpec | ModelPlan, profile: HwProfile, fmt: NumFormat,
             point: WorkloadPoint, *, attention: str = "vpu",
             params: ModelParams | None = None, geometry: ArrayGeometry | None = None,
             topology: Topology | None = None, execution_plan: ExecutionPlan | None = None,
             buffer_plan: BufferPlan | None = None, scheduling_policy: str = "serial",
             lowering: Callable[..., TaskGraph] | None = None,
             costs: dict[str, dict[str, EngineCost]] | None = None,
             dispatcher: str | Callable[..., ScheduleResult] | None = None) -> L1Result:
    params = params or ModelParams()
    geometry = geometry or load_array_geometry()
    topology = topology or default_topology(profile, geometry, params)
    plan = execution_plan or ExecutionPlan(params.weight_policy, "stream")
    if isinstance(model, ModelPlan):
        if lowering is not None:
            graph = lowering(model, fmt, point, attention=attention, geometry=geometry,
                             block_size=params.block_size)
            graph.validate()
        else:
            graph = lower_plan(model, fmt, point, attention=attention,
                               geometry=geometry, block_size=params.block_size)
    else:
        graph = lower_model(model, fmt, point, attention, geometry=geometry,
                            block_size=params.block_size, lowering=lowering)
    buffer_plan = buffer_plan or BufferPlan.from_graph(graph, plan)
    estimates = estimate_graph(graph, topology, plan) if costs is None else costs
    scheduled = evaluate_graph(graph, topology, execution_plan=plan,
                               buffer_plan=buffer_plan, scheduling_policy=scheduling_policy,
                               costs=estimates, dispatcher=dispatcher)
    costs = estimates
    selected = {task.task_id: costs[task.task_id][task.budget_instance_id]
                for task in scheduled.tasks if task.budget_instance_id is not None}
    totals = _sum_metrics(list(selected.values()))
    totals["semantic_macs"] = sum(op.mac for op in graph.operations)
    totals["norm_calls"] = sum(task.spec.vectors for task in graph.tasks
                               if isinstance(task.spec, VpuSpec) and task.spec.kind == "norm")
    totals["softmax_reductions"] = sum(task.spec.vectors for task in graph.tasks
                                       if isinstance(task.spec, VpuSpec) and task.spec.kind == "softmax")
    l0 = evaluate_l0(model, profile, fmt, point, use_doc_bytes=False)
    totals["l0_reference_macs"] = l0.mac
    totals["extra_semantic_macs_vs_l0"] = totals["semantic_macs"] - l0.mac
    excluded = {item for cost in selected.values() for item in cost.excluded_costs}
    excluded.update(("CPU command submission/scheduler overhead is unmodeled",
                     "conditional projection cannot establish actual overlap across unknown service",
                     "fixed-rate reservations and conservative local slots are analytical, not measured arbitration"))
    if scheduled.makespan_s is not None:
        dram_bound = totals["dram_bytes"] / profile.dram_effective_bytes_per_s
        if scheduled.makespan_s + 1e-12 < dram_bound:
            raise AssertionError("schedule violates its actual shared DRAM traffic lower bound")
    return L1Result(model.name, profile.name, point, fmt.name, attention, graph, topology, plan,
                    buffer_plan, scheduled, costs, l0.t_s, totals, tuple(sorted(excluded)),
                    plan=model if isinstance(model, ModelPlan) else None,
                    dram_effective_bytes_per_s=profile.dram_effective_bytes_per_s)
