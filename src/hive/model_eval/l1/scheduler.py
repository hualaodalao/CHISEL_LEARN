"""Fixed-plan reservations keep unknown actual occupations open indefinitely."""
from __future__ import annotations

import math
from dataclasses import asdict, dataclass, field
from itertools import combinations

from ..common.opspec import PrimitiveTask, TaskGraph, TransferSpec
from ..hw.topology import EngineConfig, Topology
from .ledger import CATEGORIES, EngineCost
from .residency import BufferEndpoint, BufferPlan, align_bytes

_INF = math.inf


@dataclass(frozen=True)
class PhaseTiming:
    phase_id: str
    start_s: float | None
    end_s: float | None
    projection_start_s: float | None
    projection_end_s: float | None


@dataclass(frozen=True)
class Wait:
    reason: str
    resource: str | None
    start_s: float | None
    end_s: float | None
    projection_start_s: float | None
    projection_end_s: float | None
    blocking_task_ids: tuple[str, ...] = ()
    blocking_allocation_ids: tuple[str, ...] = ()


@dataclass(frozen=True)
class TaskTiming:
    task_id: str
    instance_id: str | None
    start_s: float | None
    end_s: float | None
    projection_start_s: float | None
    projection_end_s: float | None
    phases: tuple[PhaseTiming, ...] = ()
    waits: tuple[Wait, ...] = ()
    blocked_constraints: tuple[str, ...] = ()
    status: str = "scheduled"
    budget_instance_id: str | None = None


@dataclass(frozen=True)
class ScheduleResult:
    tasks: tuple[TaskTiming, ...]
    makespan_s: float | None
    projection_makespan_s: float | None
    attribution_s: dict[str, float | None]
    projection_attribution_s: dict[str, float | None]
    resource_busy_s: dict[str, float | None]
    projection_resource_busy_s: dict[str, float | None]
    memory_peak_bytes: dict[str, int | None]
    projection_memory_peak_bytes: dict[str, int | None]
    blocked_constraints: tuple[str, ...]
    policy: str
    projection_kind: str = "conditional_budget_nonactual"

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass(frozen=True)
class _Trace:
    time: float = 0.0
    path: tuple[float, ...] = (0.0,) * len(CATEGORIES)
    owner: str = ""

    def advance(self, time: float, category: str = "handoff", owner: str = "") -> _Trace:
        if not math.isfinite(time):
            return _Trace(time, self.path, owner or self.owner)
        path = list(self.path)
        path[CATEGORIES.index(category)] += max(0.0, time - self.time)
        return _Trace(time, tuple(path), owner or self.owner)


def _latest(traces) -> _Trace:
    return max(traces, key=lambda trace: (trace.time, trace.owner), default=_Trace())


@dataclass(frozen=True)
class _Reservation:
    resource: str
    start: float
    end: float
    amount: float
    task_id: str
    trace: _Trace


@dataclass(frozen=True)
class _Waiting:
    reason: str
    resource: str | None
    start: float
    end: float
    owners: tuple[str, ...] = ()


@dataclass
class _Attempt:
    task_id: str
    instance_id: str
    start: _Trace
    end: _Trace
    phases: dict[str, tuple[_Trace, _Trace]]
    reservations: list[_Reservation]
    waits: list[_Waiting]


@dataclass(frozen=True)
class _Lifetime:
    key: str
    domain: str
    size: int | None
    start: float
    end: _Trace
    offset: int | None = None
    region: str | None = None
    movable_release: bool = False


class _Blocked(Exception):
    pass


def _resolve(instance: EngineConfig, topology: Topology, name: str) -> str:
    if name in topology.resources:
        return name
    try:
        return instance.resolve(name)
    except KeyError as error:
        raise _Blocked(str(error)) from error


def _claims(cost: EngineCost, instance: EngineConfig, topology: Topology):
    leases = {_resolve(instance, topology, "engine")}
    leases.update(_resolve(instance, topology, name) for name in cost.lease_resources)
    claims = {}
    for phase in cost.phases:
        amounts = {}
        for claim in phase.claims:
            key = _resolve(instance, topology, claim.resource)
            amounts[key] = amounts.get(key, 0.0) + claim.amount
        # A phase uses its task's existing lease; it does not acquire it twice.
        amounts = {key: amount - (1.0 if key in leases else 0.0)
                   for key, amount in amounts.items()}
        claims[phase.id] = {key: amount for key, amount in amounts.items() if amount > 0}
    for key in leases:
        if topology.resources[key].capacity < 1:
            raise _Blocked(f"lease {key} exceeds physical capacity")
    for amounts in claims.values():
        for key, amount in amounts.items():
            if amount + (1 if key in leases else 0) > topology.resources[key].capacity * (1 + 1e-12):
                raise _Blocked(f"claim on {key} exceeds physical capacity")
    return leases, claims


def _slot(ready: _Trace, duration: float, claims: dict[str, float],
          calendar: list[_Reservation], topology: Topology):
    """Move only to conflicting release endpoints, never to a time tick."""
    start = ready
    waits = []
    if duration == 0 or not claims:
        return start, waits
    while True:
        end = start.time + duration
        conflict = None
        for key, amount in sorted(claims.items()):
            overlaps = [r for r in calendar if r.resource == key and
                        r.start < end and start.time < r.end]
            points = sorted({start.time, *(max(start.time, r.start) for r in overlaps)})
            for point in points:
                active = [r for r in overlaps if r.start <= point < r.end]
                if amount + sum(r.amount for r in active) > topology.resources[key].capacity * (1 + 1e-12):
                    release = min(r.end for r in active)
                    item = (point, key, release, active)
                    if conflict is None or item[:2] < conflict[:2]:
                        conflict = item
                    break
        if conflict is None:
            return start, waits
        _, key, release, active = conflict
        if not math.isfinite(release):
            raise _Blocked(f"{key}: unknown occupation has no known release")
        cause = _latest(r.trace for r in active if r.end == release)
        waits.append(_Waiting("resource", key, start.time, release,
                              tuple(sorted({r.task_id for r in active}))))
        start = cause.advance(release)


def _endpoint(endpoint: BufferEndpoint, assigned: dict[str, _Attempt], end: bool) -> _Trace | None:
    timing = assigned.get(endpoint.task_id)
    if timing is None:
        return None
    if endpoint.phase_id is None:
        return timing.end if end else timing.start
    phase = timing.phases.get(endpoint.phase_id)
    return None if phase is None else phase[1 if end else 0]


def _lifetimes(plan: BufferPlan, graph: TaskGraph, selected: dict[str, EngineCost],
               assigned: dict[str, _Attempt], candidate: str = "") -> list[_Lifetime]:
    tensors = {tensor.id: tensor for tensor in graph.tensors}
    base_allocations = {allocation.base_id or
                        (tensors[allocation.tensor_id].base_id or allocation.tensor_id): allocation
                        for allocation in plan.allocations if allocation.tensor_id is not None}
    allocations_by_id = {allocation.id: allocation for allocation in plan.allocations}
    augment: dict[str, tuple[int, int]] = {}
    requests = []
    for task_id, timing in assigned.items():
        for request in selected[task_id].buffers:
            allocation = allocations_by_id.get(request.id)
            if request.tensor_id is not None:
                tensor = tensors[request.tensor_id]
                base = tensor.base_id or tensor.id
                base_allocation = base_allocations[base]
                if request.memory_domain == base_allocation.memory_domain:
                    allocation = base_allocation
                # Otherwise this is a separate staged physical copy, unless an
                # explicit same-ID allocation in its destination domain owns it.
            start = _endpoint(BufferEndpoint(task_id, request.acquire_phase), assigned, False)
            end = _endpoint(BufferEndpoint(task_id, request.release_phase), assigned, True)
            if allocation is not None and allocation.memory_domain == request.memory_domain:
                size, alignment = augment.get(allocation.id,
                    (allocation.size_bytes or 0, allocation.alignment))
                augment[allocation.id] = (max(size, request.size_bytes),
                                          math.lcm(alignment, request.alignment))
                if start is not None and math.isfinite(start.time):
                    acquired = (_Trace() if allocation.source == "graph-entry" else
                                _endpoint(allocation.acquire, assigned, False))
                    released = [_endpoint(endpoint, assigned, True) for endpoint in allocation.releases]
                    if acquired is None or acquired.time > start.time:
                        raise _Blocked(f"allocation {allocation.id}: does not cover engine buffer acquisition")
                    release = _INF if any(item is None for item in released) else _latest(released).time
                    if release < (end.time if end is not None else _INF):
                        raise _Blocked(f"allocation {allocation.id}: does not cover engine buffer release")
                continue
            if start is not None and math.isfinite(start.time):
                requests.append(_Lifetime(f"{task_id}/{request.id}", request.memory_domain,
                    request.allocated_bytes, start.time, end or _Trace(_INF, owner=task_id),
                    movable_release=task_id == candidate))
    for allocation in plan.allocations:
        start = (_Trace() if allocation.source == "graph-entry" else
                 _endpoint(allocation.acquire, assigned, False))
        if start is None or not math.isfinite(start.time):
            continue
        ends = [_endpoint(endpoint, assigned, True) for endpoint in allocation.releases]
        end = (_Trace(_INF, owner=allocation.id) if any(item is None for item in ends)
               else _latest(ends))
        size = allocation.allocated_bytes
        if size is not None and allocation.id in augment:
            size = align_bytes(*augment[allocation.id])
        requests.append(_Lifetime(allocation.id, allocation.memory_domain, size,
            start.time, end, allocation.offset, allocation.region,
            any(endpoint.task_id == candidate for endpoint in allocation.releases)))
    return requests


def _memory_check(lifetimes: list[_Lifetime], topology: Topology, candidate_start: float):
    """Return a releasing endpoint if a shift helps; otherwise report fixed-plan blocking."""
    for lifetime in lifetimes:
        if lifetime.domain not in topology.memory_domains:
            raise _Blocked(f"unregistered memory domain {lifetime.domain}")
        if lifetime.end.time < lifetime.start:
            raise _Blocked(f"allocation {lifetime.key}: release precedes acquisition")
        domain = topology.memory_domains[lifetime.domain]
        if lifetime.size is None:
            raise _Blocked(f"{lifetime.domain}: allocation {lifetime.key} size is unknown")
        if lifetime.size and domain.capacity_bytes is None and domain.id != "dram":
            raise _Blocked(f"{domain.id}: required SRAM capacity is unknown")
        if domain.capacity_bytes is not None and (
                lifetime.size > domain.capacity_bytes or
                (lifetime.offset is not None and lifetime.offset + lifetime.size > domain.capacity_bytes)):
            raise _Blocked(f"{domain.id}: allocation {lifetime.key} exceeds fixed capacity/region")
    for domain_id, domain in sorted(topology.memory_domains.items()):
        entries = [life for life in lifetimes if life.domain == domain_id and life.size and life.start < life.end.time]
        if domain.capacity_bytes is None and not any(life.offset is not None for life in entries):
            continue
        events = {}
        for index, life in enumerate(entries):
            events.setdefault(life.start, [[], []])[1].append(index)
            events.setdefault(life.end.time, [[], []])[0].append(index)
        active = {}
        total = 0
        for point, (ends, starts) in sorted(events.items()):
            for index in ends:
                total -= active.pop(index).size
            for index in starts:
                active[index] = entries[index]
                total += entries[index].size
            if not starts:
                continue
            live = [active[index] for index in sorted(active)]
            conflict = live if domain.capacity_bytes is not None and total > domain.capacity_bytes else []
            placed = [life for life in live if life.offset is not None]
            for left, right in combinations(placed, 2):
                if (left.region == right.region and left.offset < right.offset + right.size and
                        right.offset < left.offset + left.size):
                    conflict = [left, right]
                    break
            if conflict:
                releases = [life.end for life in conflict if not life.movable_release and
                            candidate_start < life.end.time < _INF]
                if not releases:
                    names = ", ".join(sorted(life.key for life in conflict))
                    raise _Blocked(f"{domain_id}: current fixed-plan live capacity/region conflict ({names}); "
                                   "release unknown or depends on this task")
                release = min(releases, key=lambda trace: (trace.time, trace.owner))
                return domain_id, release, tuple(sorted(life.key for life in conflict))
    return None


def _validate_cost_buffers(cost: EngineCost, graph: TaskGraph, instance: EngineConfig,
                           topology: Topology, plan: BufferPlan):
    tensors = {tensor.id: tensor for tensor in graph.tensors}
    ids = [request.id for request in cost.buffers]
    if len(set(ids)) != len(ids):
        raise _Blocked("duplicate engine buffer ID")
    task = next(task for task in graph.tasks if task.id == cost.task_id)
    dma_domains = ({task.spec.source_domain, task.spec.destination_domain}
                   if isinstance(task.spec, TransferSpec) else set())
    for request in cost.buffers:
        domain = topology.memory_domains.get(request.memory_domain)
        if domain is None:
            raise _Blocked(f"unregistered buffer domain {request.memory_domain}")
        if not domain.accessible_by(instance.core_id) and request.memory_domain not in dma_domains:
            raise _Blocked(f"{domain.id}: private buffer requires an explicit DMA copy")
        matching = next((allocation for allocation in plan.allocations
                         if allocation.id == request.id and allocation.memory_domain == domain.id), None)
        if matching is not None and matching.offset is not None and matching.offset % request.alignment:
            raise _Blocked(f"allocation {matching.id}: fixed offset violates engine buffer alignment")
        if request.tensor_id is not None:
            if request.tensor_id not in tensors:
                raise _Blocked(f"unregistered buffer tensor {request.tensor_id}")
            tensor = tensors[request.tensor_id]
            source = topology.memory_domains.get(tensor.memory_domain)
            if source is None:
                raise _Blocked(f"unregistered tensor domain {tensor.memory_domain}")
            if not source.accessible_by(instance.core_id) and source.id not in dma_domains:
                raise _Blocked(f"{source.id}: private tensor source requires an explicit DMA copy")
            allocation = next((allocation for allocation in plan.allocations
                               if allocation.tensor_id is not None and
                               (tensors[allocation.tensor_id].base_id or allocation.tensor_id) ==
                               (tensor.base_id or tensor.id)), None)
            if allocation is None:
                raise _Blocked("engine tensor buffer has no global base allocation")
            if (allocation.memory_domain == domain.id and allocation.offset is not None and
                    allocation.offset % request.alignment):
                raise _Blocked(f"allocation {allocation.id}: fixed offset violates engine buffer alignment")
    phase_ids = {phase.id for phase in cost.phases}
    for allocation in plan.allocations:
        for endpoint in (allocation.acquire, *allocation.releases):
            if endpoint.task_id != task.id:
                continue
            domain = topology.memory_domains.get(allocation.memory_domain)
            if domain is None:
                raise _Blocked(f"unregistered allocation domain {allocation.memory_domain}")
            if not domain.accessible_by(instance.core_id) and domain.id not in dma_domains:
                raise _Blocked(f"{domain.id}: private allocation requires an explicit DMA copy")
            if endpoint.phase_id is not None and endpoint.phase_id not in phase_ids:
                raise _Blocked(f"allocation {allocation.id}: missing phase {endpoint.phase_id}")


def _attempt(task: PrimitiveTask, cost: EngineCost, topology: Topology, graph: TaskGraph,
             plan: BufferPlan, assigned: dict[str, _Attempt], selected: dict[str, EngineCost],
             calendar: list[_Reservation], ready: _Trace, projection: bool) -> _Attempt:
    instance = topology.instances[cost.instance_id]
    try:
        topology.validate_access(graph, task, instance)
    except ValueError as error:
        raise _Blocked(str(error)) from error
    _validate_cost_buffers(cost, graph, instance, topology, plan)
    leases, claims = _claims(cost, instance, topology)
    start = ready
    outer_waits = []
    while True:
        phases = {}
        reservations = []
        waits = []
        phase_failures = []
        pending = {phase.id: phase for phase in cost.phases}
        while pending:
            options = []
            for key, phase in sorted(pending.items()):
                if not all(dep in phases for dep in phase.dependencies):
                    continue
                phase_ready = _latest([start, *(phases[dep][1] for dep in phase.dependencies)])
                if not math.isfinite(phase_ready.time):
                    continue
                duration = phase.budget_s if projection else (phase.seconds if phase.seconds is not None else _INF)
                try:
                    # An unknown interval cannot assume it ends before an already
                    # reserved future competitor. Infinity is occupation, not service.
                    phase_start, phase_waits = _slot(phase_ready, duration, claims[key],
                                                     calendar + reservations, topology)
                except _Blocked as error:
                    if projection:
                        raise
                    phase_failures.append(str(error))
                    continue
                phase_end = phase_start.advance(phase_start.time + duration, phase.category, task.id + "/" + key)
                options.append((phase_end.time, phase_start.time, key, phase_start, phase_end, phase_waits))
            if not options:
                if projection:
                    raise _Blocked("phase DAG has no schedulable phase under current reservations")
                break
            _, _, key, phase_start, phase_end, phase_waits = min(options, key=lambda item: item[:3])
            phases[key] = phase_start, phase_end
            waits.extend(phase_waits)
            for resource, amount in claims[key].items():
                if phase_start.time < phase_end.time:
                    reservations.append(_Reservation(resource, phase_start.time, phase_end.time,
                                                     amount, task.id, phase_end))
            del pending[key]
        if pending and not phases:
            raise _Blocked("; ".join(sorted(set(phase_failures))) or "phase start is unknown")
        first = min((pair[0] for pair in phases.values()),
                    key=lambda trace: (trace.time, trace.owner), default=start)
        if first.time > start.time:
            # Acquire the command lease when its first phase can start, but hold
            # it continuously across every later phase and internal wait.
            outer_waits.extend(wait for wait in waits if wait.end <= first.time)
            start = first
            continue
        end = (_Trace(_INF, owner=task.id) if pending else
               _latest([start, *(pair[1] for pair in phases.values())]))
        lease_start, lease_waits = _slot(start, end.time - start.time,
                                        dict.fromkeys(leases, 1.0), calendar, topology)
        if lease_start.time > start.time:
            outer_waits.extend(lease_waits)
            start = lease_start
            continue
        timing = _Attempt(task.id, cost.instance_id, start, end, phases, reservations,
                          outer_waits + waits)
        augmented_assigned = {**assigned, task.id: timing}
        augmented_selected = {**selected, task.id: cost}
        conflict = _memory_check(_lifetimes(plan, graph, augmented_selected, augmented_assigned, task.id),
                                 topology, start.time)
        if conflict is not None:
            domain, release, owners = conflict
            outer_waits.append(_Waiting("memory", domain, start.time, release.time, owners))
            start = release
            continue
        for resource in sorted(leases):
            if start.time < end.time:
                timing.reservations.append(_Reservation(resource, start.time, end.time, 1.0, task.id, end))
        return timing


@dataclass
class _Run:
    assigned: dict[str, _Attempt] = field(default_factory=dict)
    selected: dict[str, EngineCost] = field(default_factory=dict)
    calendar: list[_Reservation] = field(default_factory=list)
    failures: dict[str, tuple[str, ...]] = field(default_factory=dict)
    order: list[str] = field(default_factory=list)

    def add(self, timing: _Attempt, cost: EngineCost):
        self.assigned[timing.task_id] = timing
        self.selected[timing.task_id] = cost
        self.calendar.extend(timing.reservations)
        self.order.append(timing.task_id)
        self.failures.pop(timing.task_id, None)


def _project(graph: TaskGraph, costs, topology: Topology, plan: BufferPlan, policy: str,
             ordered: tuple[str, ...]) -> _Run:
    run = _Run()
    tasks = {task.id: task for task in graph.tasks}
    pending = set(tasks)
    while pending:
        ready_ids = [key for key in ordered if key in pending and
                     all(dep in run.assigned for dep in tasks[key].dependencies)]
        if policy == "serial":
            ready_ids = [next(key for key in ordered if key in pending)]
        options = []
        for key in ready_ids:
            task = tasks[key]
            ready = _latest(run.assigned[dep].end for dep in task.dependencies)
            if policy == "serial" and run.order:
                ready = _latest([ready, run.assigned[run.order[-1]].end])
            failures = []
            for instance in topology.candidates(task):
                cost = costs.get(key, {}).get(instance.id)
                if cost is None:
                    continue
                try:
                    timing = _attempt(task, cost, topology, graph, plan, run.assigned,
                                      run.selected, run.calendar, ready, True)
                    if ready.time > 0:
                        serial_wait = (policy == "serial" and run.order and
                            run.assigned[run.order[-1]].end.time >
                            max((run.assigned[dep].end.time for dep in task.dependencies), default=0.0))
                        timing.waits.insert(0, _Waiting("serial" if serial_wait else "dependency", None,
                            0.0, ready.time, (run.order[-1],) if serial_wait else tuple(sorted(task.dependencies))))
                    options.append((timing.end.time, timing.start.time, key, instance.id, timing, cost))
                except _Blocked as error:
                    failures.append(f"{instance.id}: {error}")
            run.failures[key] = tuple(failures or ["no costed candidate engine instance"])
        if not options:
            break
        _, _, key, _, timing, cost = min(options, key=lambda item: item[:4])
        run.add(timing, cost)
        pending.remove(key)
    for key in sorted(pending):
        if key not in run.failures:
            missing = sorted(set(tasks[key].dependencies) - run.assigned.keys())
            run.failures[key] = (f"blocked dependencies {missing}" if missing else
                                 "serial predecessor is blocked",)
    return run


def _actual(graph: TaskGraph, topology: Topology, plan: BufferPlan,
            projection: _Run, policy: str) -> _Run:
    run = _Run()
    tasks = {task.id: task for task in graph.tasks}
    for key in projection.order:
        task = tasks[key]
        if any(dep not in run.assigned or not math.isfinite(run.assigned[dep].end.time)
               for dep in task.dependencies):
            run.failures[key] = ("dependency completion is unknown",)
            continue
        ready = _latest(run.assigned[dep].end for dep in task.dependencies)
        if policy == "serial" and run.order:
            ready = _latest([ready, run.assigned[run.order[-1]].end])
        if not math.isfinite(ready.time):
            run.failures[key] = ("serial predecessor occupation is unknown",)
            continue
        cost = projection.selected[key]
        try:
            timing = _attempt(task, cost, topology, graph, plan, run.assigned,
                              run.selected, run.calendar, ready, False)
            if ready.time > 0:
                serial_wait = (policy == "serial" and run.order and
                            run.assigned[run.order[-1]].end.time >
                            max((run.assigned[dep].end.time for dep in task.dependencies), default=0.0))
                timing.waits.insert(0, _Waiting("serial" if serial_wait else "dependency", None,
                    0.0, ready.time, (run.order[-1],) if serial_wait else tuple(sorted(task.dependencies))))
            run.add(timing, cost)
            if not math.isfinite(timing.end.time):
                run.failures[key] = ("phase service or resource release is unknown",)
        except _Blocked as error:
            run.failures[key] = (str(error),)
    return run


def _number(value: float) -> float | None:
    return value if math.isfinite(value) else None


def _busy(run: _Run, topology: Topology, potential: dict[str, EngineCost]):
    busy = {}
    for key in sorted(topology.resources):
        intervals = sorted((r.start, r.end) for r in run.calendar if r.resource == key)
        if any(not math.isfinite(end) for _, end in intervals):
            busy[key] = None
            continue
        total, previous_end = 0.0, 0.0
        for start, end in intervals:
            total += max(0.0, end - max(start, previous_end))
            previous_end = max(previous_end, end)
        busy[key] = total
    for task_id, cost in potential.items():
        if task_id in run.assigned and math.isfinite(run.assigned[task_id].end.time):
            continue
        instance = topology.instances[cost.instance_id]
        resources = {instance.resolve("engine"), *cost.lease_resources,
                     *(claim.resource for phase in cost.phases for claim in phase.claims)}
        for resource in resources:
            try:
                busy[_resolve(instance, topology, resource)] = None
            except _Blocked:
                pass
    return busy


def _peaks(run: _Run, plan: BufferPlan, graph: TaskGraph, topology: Topology,
           potential: dict[str, EngineCost]):
    lifetimes = _lifetimes(plan, graph, run.selected, run.assigned)
    peaks = {}
    for key, domain in sorted(topology.memory_domains.items()):
        entries = [life for life in lifetimes if life.domain == key and life.size != 0]
        required = (any(allocation.memory_domain == key and allocation.size_bytes != 0
                        for allocation in plan.allocations) or
                    any(request.memory_domain == key and request.size_bytes > 0
                        for cost in potential.values() for request in cost.buffers))
        unknown_size = any(allocation.memory_domain == key and allocation.size_bytes is None
                           for allocation in plan.allocations)
        if unknown_size or any(life.size is None or not math.isfinite(life.end.time) for life in entries) or (
                required and domain.capacity_bytes is None and key != "dram"):
            peaks[key] = None
        else:
            events = {}
            for life in entries:
                events[life.start] = events.get(life.start, 0) + life.size
                events[life.end.time] = events.get(life.end.time, 0) - life.size
            total = peak = 0
            for _, delta in sorted(events.items()):
                total += delta
                peak = max(peak, total)
            peaks[key] = peak
    return peaks


def _summary(run: _Run, count: int):
    trace = _latest(timing.end for timing in run.assigned.values())
    if len(run.assigned) != count or not math.isfinite(trace.time):
        return None, dict.fromkeys(CATEGORIES, None)
    return trace.time, dict(zip(CATEGORIES, trace.path))


def schedule(graph: TaskGraph, costs: dict[str, dict[str, EngineCost]], topology: Topology,
             buffer_plan: BufferPlan | None = None, policy: str = "list") -> ScheduleResult:
    """Reserve a fixed plan with stable earliest-finish binding and whole-task leases."""
    if policy not in ("list", "serial"):
        raise ValueError("policy must be list or serial")
    ordered = graph.validate()
    plan = buffer_plan if buffer_plan is not None else BufferPlan.from_graph(graph)
    plan.validate(graph)
    task_ids = {task.id for task in graph.tasks}
    if costs.keys() - task_ids:
        raise ValueError("costs contain unknown tasks")
    for task_id, estimates in costs.items():
        for instance_id, cost in estimates.items():
            if cost.task_id != task_id or cost.instance_id != instance_id:
                raise ValueError("cost registry keys disagree with task/instance IDs")
            if instance_id not in topology.instances:
                raise ValueError("cost references an unregistered engine instance")
    projection = _project(graph, costs, topology, plan, policy, ordered)
    actual = _actual(graph, topology, plan, projection, policy)
    rows = []
    constraints = []
    potential = {}
    for task in sorted(graph.tasks, key=lambda primitive: primitive.id):
        p = projection.assigned.get(task.id)
        a = actual.assigned.get(task.id)
        cost = projection.selected.get(task.id)
        if cost is None:
            candidates = [costs.get(task.id, {}).get(instance.id) for instance in topology.candidates(task)]
            candidates = [candidate for candidate in candidates if candidate is not None]
            cost = min(candidates, key=lambda candidate: (candidate.known_service_s, candidate.instance_id), default=None)
        if cost is not None:
            potential[task.id] = cost
        problems = (*projection.failures.get(task.id, ()), *actual.failures.get(task.id, ()))
        constraints.extend(f"{task.id}: current fixed-plan blocking: {problem}" for problem in problems)
        phases = []
        for phase in cost.phases if cost is not None else ():
            pp = p.phases.get(phase.id) if p else None
            aa = a.phases.get(phase.id) if a else None
            phases.append(PhaseTiming(phase.id,
                _number(aa[0].time) if aa and phase.seconds is not None else None,
                _number(aa[1].time) if aa and phase.seconds is not None else None,
                _number(pp[0].time) if pp else None, _number(pp[1].time) if pp else None))
        waits = []
        actual_waits = list(a.waits) if a else []
        projection_waits = p.waits if p else []
        pairs = []
        for pw in projection_waits:
            index = next((index for index, aw in enumerate(actual_waits)
                          if (aw.reason, aw.resource, aw.owners) == (pw.reason, pw.resource, pw.owners)), None)
            pairs.append((actual_waits.pop(index) if index is not None else None, pw))
        pairs.extend((aw, None) for aw in actual_waits)
        for aw, pw in pairs:
            wait = pw or aw
            waits.append(Wait(wait.reason, wait.resource,
                _number(aw.start) if aw else None, _number(aw.end) if aw else None,
                _number(pw.start) if pw else None, _number(pw.end) if pw else None,
                blocking_task_ids=wait.owners if wait.reason != "memory" else (),
                blocking_allocation_ids=wait.owners if wait.reason == "memory" else ()))
        if a and math.isfinite(a.end.time):
            status = "scheduled"
        elif p:
            status = "projected"
        else:
            status = "blocked" if cost is not None else "unassigned"
        rows.append(TaskTiming(task.id, p.instance_id if p else None,
            _number(a.start.time) if a else None, _number(a.end.time) if a else None,
            _number(p.start.time) if p else None, _number(p.end.time) if p else None,
            tuple(phases), tuple(waits), tuple(problems), status,
            cost.instance_id if cost else None))
    makespan, attribution = _summary(actual, len(graph.tasks))
    projection_makespan, projection_attribution = _summary(projection, len(graph.tasks))
    return ScheduleResult(tuple(rows), makespan, projection_makespan, attribution, projection_attribution,
        _busy(actual, topology, potential), _busy(projection, topology, potential),
        _peaks(actual, plan, graph, topology, potential), _peaks(projection, plan, graph, topology, potential),
        tuple(constraints), policy)
