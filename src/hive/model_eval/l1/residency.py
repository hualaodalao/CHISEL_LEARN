"""Explicit allocation lifetimes. Capacity is private to a physical memory domain."""
from __future__ import annotations

from dataclasses import asdict, dataclass

from ..common.opspec import TaskGraph


def align_bytes(size: int, alignment: int) -> int:
    if type(size) is not int or size < 0 or type(alignment) is not int or alignment < 1:
        raise ValueError("size/alignment must be nonnegative/positive integers")
    return ((size + alignment - 1) // alignment) * alignment


@dataclass(frozen=True)
class ExecutionPlan:
    weight_policy: str = "stream"
    activation_policy: str = "stream"
    alignment: int = 32
    evidence: str = "explicit execution policy"

    def __post_init__(self) -> None:
        if self.weight_policy not in ("stream", "resident"):
            raise ValueError("weight_policy must be stream or resident")
        if self.activation_policy not in ("stream", "stage"):
            raise ValueError("activation_policy must be stream or stage")
        align_bytes(0, self.alignment)
        if not self.evidence.strip():
            raise ValueError("execution policy requires explicit evidence")

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass(frozen=True)
class BufferEndpoint:
    task_id: str
    phase_id: str | None = None

    def __post_init__(self) -> None:
        if not self.task_id:
            raise ValueError("buffer endpoint requires a task ID")


@dataclass(frozen=True)
class BufferAllocation:
    id: str
    memory_domain: str
    size_bytes: int | None
    acquire: BufferEndpoint
    releases: tuple[BufferEndpoint, ...]
    alignment: int = 32
    tensor_id: str | None = None
    base_id: str | None = None
    offset: int | None = None
    region: str | None = None
    source: str = "explicit"
    evidence: str = "explicit allocation lifecycle"

    def __post_init__(self) -> None:
        if not self.id or not self.memory_domain or not self.releases:
            raise ValueError("buffer requires ID, domain, acquire and release endpoints")
        align_bytes(0 if self.size_bytes is None else self.size_bytes, self.alignment)
        if self.offset is not None and (type(self.offset) is not int or self.offset < 0 or
                                        self.offset % self.alignment):
            raise ValueError("buffer offset must be nonnegative and aligned")
        if len(set(self.releases)) != len(self.releases):
            raise ValueError("duplicate buffer release endpoint")

    @property
    def allocated_bytes(self) -> int | None:
        return None if self.size_bytes is None else align_bytes(self.size_bytes, self.alignment)


@dataclass(frozen=True)
class BufferPlan:
    allocations: tuple[BufferAllocation, ...] = ()
    execution: ExecutionPlan = ExecutionPlan()
    source: str = "explicit"
    evidence: str = "explicit graph allocation lifecycles"

    def validate(self, graph: TaskGraph) -> None:
        order = graph.validate()
        tasks = {task.id: task for task in graph.tasks}
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        ancestors: dict[str, set[str]] = {}
        for key in order:
            ancestors[key] = set(tasks[key].dependencies)
            for dep in tasks[key].dependencies:
                ancestors[key].update(ancestors[dep])
        ids = set()
        bases = set()
        for allocation in self.allocations:
            if allocation.id in ids:
                raise ValueError("duplicate global allocation ID")
            ids.add(allocation.id)
            endpoints = (allocation.acquire, *allocation.releases)
            if any(endpoint.task_id not in tasks for endpoint in endpoints):
                raise ValueError("buffer lifecycle references missing task")
            if allocation.source == "graph-entry" and allocation.acquire.phase_id is not None:
                raise ValueError("graph-entry allocation cannot acquire at a task phase")
            for release in allocation.releases:
                if (allocation.source != "graph-entry" and
                        allocation.acquire.task_id != release.task_id and
                        allocation.acquire.task_id not in ancestors[release.task_id]):
                    raise ValueError("buffer release must follow acquisition in the task DAG")
            if allocation.tensor_id is None:
                if allocation.base_id is not None:
                    raise ValueError("base_id requires a graph tensor")
                continue
            if allocation.tensor_id not in tensors:
                raise ValueError("allocation references missing tensor")
            tensor = tensors[allocation.tensor_id]
            base = tensors[tensor.base_id or tensor.id]
            if allocation.base_id not in (None, base.id) or allocation.memory_domain != base.memory_domain:
                raise ValueError("allocation disagrees with tensor base/domain")
            if base.id in bases:
                raise ValueError("tensor base views must share one global allocation")
            bases.add(base.id)
            if allocation.size_bytes is not None and allocation.size_bytes < base.size_bytes:
                raise ValueError("allocation is smaller than tensor base")
            uses = {task.id for task in graph.tasks for ref in (*task.inputs, *task.outputs)
                    if (tensors[ref.tensor_id].base_id or ref.tensor_id) == base.id}
            for key in uses:
                if (allocation.source != "graph-entry" and key != allocation.acquire.task_id and
                        allocation.acquire.task_id not in ancestors[key]):
                    raise ValueError("allocation acquisition must precede every tensor use")
                if not any(key == end.task_id or key in ancestors[end.task_id] for end in allocation.releases):
                    raise ValueError("output buffer must be held through its last consumer")
                if any(end.task_id == key and end.phase_id is not None for end in allocation.releases):
                    raise ValueError("graph tensor release must cover the entire consumer task")
            if any(task.id == allocation.acquire.task_id and any(
                    (tensors[ref.tensor_id].base_id or ref.tensor_id) == base.id for ref in task.outputs)
                   for task in graph.tasks) and allocation.acquire.phase_id is not None:
                raise ValueError("producer output allocation must be acquired at task start")
        required = {tensor.base_id or tensor.id for tensor in graph.tensors
                    if any(ref.tensor_id == tensor.id for task in graph.tasks
                           for ref in (*task.inputs, *task.outputs))}
        if required - bases:
            raise ValueError(f"buffer plan omits graph tensor bases {sorted(required - bases)}")

    def to_dict(self) -> dict:
        return {"allocations": [asdict(allocation) for allocation in self.allocations],
                "execution": self.execution.to_dict(), "source": self.source, "evidence": self.evidence}

    @classmethod
    def from_graph(cls, graph: TaskGraph, execution: ExecutionPlan | None = None) -> BufferPlan:
        return default_buffer_plan(graph, execution)


def default_buffer_plan(graph: TaskGraph, execution: ExecutionPlan | None = None) -> BufferPlan:
    """One allocation per tensor base, from first write through all last consumers.

    Independent external readers have no unique first task. Acquire at graph entry
    is represented by a zero-time graph-wide lifetime in the scheduler instead of
    inventing a dependency: such allocations use the first stable reader and carry
    an explicit external-entry source marker.
    """
    order = graph.validate()
    rank = {key: index for index, key in enumerate(order)}
    tensors = {tensor.id: tensor for tensor in graph.tensors}
    policy = execution or ExecutionPlan()
    uses: dict[str, set[str]] = {}
    writers: dict[str, set[str]] = {}
    for task in graph.tasks:
        for ref in (*task.inputs, *task.outputs):
            base = tensors[ref.tensor_id].base_id or ref.tensor_id
            uses.setdefault(base, set()).add(task.id)
        for ref in task.outputs:
            base = tensors[ref.tensor_id].base_id or ref.tensor_id
            writers.setdefault(base, set()).add(task.id)
    ancestors: dict[str, set[str]] = {}
    task_map = {task.id: task for task in graph.tasks}
    for key in order:
        ancestors[key] = set(task_map[key].dependencies)
        for dep in task_map[key].dependencies:
            ancestors[key].update(ancestors[dep])
    allocations = []
    for base_id, consumers in sorted(uses.items()):
        base = tensors[base_id]
        candidates = writers.get(base_id, consumers)
        acquire = min(candidates, key=rank.__getitem__)
        entry = any(key != acquire and acquire not in ancestors[key] for key in consumers)
        if entry and writers.get(base_id):
            # Disjoint parallel writers still own one allocation at graph entry.
            source = "graph-entry"
        else:
            source = "graph-entry" if entry else "graph"
        releases = tuple(BufferEndpoint(key) for key in sorted(consumers)
                         if not any(key in ancestors[other] for other in consumers))
        allocations.append(BufferAllocation(
            base_id, base.memory_domain, base.size_bytes, BufferEndpoint(acquire), releases,
            policy.alignment, base_id, base_id, source=source,
            evidence="unique tensor base; acquired before first write; held through last consumers"))
    plan = BufferPlan(tuple(allocations), policy, "graph", graph.evidence)
    # Graph-entry allocations deliberately cover independent readers/writers.
    _validate_graph_entry_plan(plan, graph)
    return plan


def _validate_graph_entry_plan(plan: BufferPlan, graph: TaskGraph) -> None:
    """Validate entry lifetimes without pretending independent users are ordered."""
    # Validation's acquisition-order checks are waived only for explicit entry
    # allocations; all size/domain/last-consumer checks still apply.
    plan.validate(graph)
