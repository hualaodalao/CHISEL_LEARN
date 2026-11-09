from __future__ import annotations

import math
from dataclasses import dataclass
from typing import TypeAlias


_FORMAT_BITS = {"INT4": 4, "INT8": 8, "INT32": 32, "FP8": 8, "MXFP4": 4,
                "MXFP8": 8, "BF16": 16, "FP16": 16, "FP32": 32, "UINT8": 8}


def _positive_dimensions(shape: tuple[int, ...]) -> None:
    if not shape or any(type(n) is not int or n < 1 for n in shape):
        raise ValueError("tensor dimensions must be positive integers")


@dataclass(frozen=True)
class Tensor:
    id: str
    shape: tuple[int, ...]
    dtype: str
    memory_domain: str = "dram"
    base_id: str | None = None
    external: bool = False
    block_size: int = 32
    base_starts: tuple[int, ...] = ()
    base_stops: tuple[int, ...] = ()
    base_steps: tuple[int, ...] = ()
    scale_axis: int = -1

    def __post_init__(self) -> None:
        _positive_dimensions(self.shape)
        if not self.id or self.dtype not in _FORMAT_BITS:
            raise ValueError("tensor requires an ID and supported dtype")
        if self.dtype.startswith("MX"):
            if self.block_size not in (16, 32):
                raise ValueError("MX tensor block size must be 16 or 32")
            if type(self.scale_axis) is not int or not -len(self.shape) <= self.scale_axis < len(self.shape):
                raise ValueError("MX scale axis must reference a tensor dimension")

    @property
    def size_bytes(self) -> int:
        elements = math.prod(self.shape)
        data = math.ceil(elements * _FORMAT_BITS[self.dtype] / 8)
        if not self.dtype.startswith("MX"):
            return data
        axis = self.scale_axis % len(self.shape)
        scale_rows = math.prod(n for i, n in enumerate(self.shape) if i != axis)
        return data + scale_rows * math.ceil(self.shape[axis] / self.block_size)


@dataclass(frozen=True)
class TensorRef:
    tensor_id: str
    starts: tuple[int, ...] = ()
    stops: tuple[int, ...] = ()
    dtype: str | None = None

    def bounds(self, tensor: Tensor) -> tuple[tuple[int, ...], tuple[int, ...]]:
        starts = self.starts or (0,) * len(tensor.shape)
        stops = self.stops or tensor.shape
        if len(starts) != len(tensor.shape) or len(stops) != len(tensor.shape):
            raise ValueError("tensor slice rank mismatch")
        if any(type(a) is not int or type(b) is not int or not 0 <= a < b <= n
               for a, b, n in zip(starts, stops, tensor.shape)):
            raise ValueError("tensor slice is out of bounds")
        if self.dtype is not None and self.dtype != tensor.dtype:
            raise ValueError("tensor reference dtype mismatch; conversion must be explicit")
        return starts, stops


@dataclass(frozen=True)
class GemmGroup:
    m: int
    n: int
    k: int
    repeats: int = 1


@dataclass(frozen=True)
class LogicalOp:
    id: str
    layer: str
    name: str
    kind: str
    mac: int = 0
    elements: int = 0
    vectors: int = 0
    length: int = 0
    read_bytes: int = 0
    write_bytes: int = 0
    dram_read_bytes: int = 0
    dram_write_bytes: int = 0
    weight_bytes: float = 0
    gemms: tuple[GemmGroup, ...] = ()
    evidence: str = ""


@dataclass(frozen=True)
class GemmSpec:
    m: int
    n: int
    k: int
    a_fmt: str = "MXFP8"
    b_fmt: str = "MXFP8"
    c_dtype: str = "FP32"
    block_size: int = 32
    semantic_macs: int | None = None
    weight: bool = False
    evidence: str = ""
    a_storage_shape: tuple[int, ...] = ()
    b_storage_shape: tuple[int, ...] = ()

    def __post_init__(self) -> None:
        _positive_dimensions((self.m, self.n, self.k))
        for storage, logical in ((self.a_storage_shape, (self.m, self.k)),
                                 (self.b_storage_shape, (self.k, self.n))):
            if storage:
                _positive_dimensions(storage)
                if len(storage) != 2 or any(a < b for a, b in zip(storage, logical)):
                    raise ValueError("GEMM storage shape must contain its logical operand")
        if self.c_dtype not in ("FP32", "INT32"):
            raise ValueError("GEMM accumulation type must be FP32 or INT32")
        if (self.a_fmt.startswith("INT")) != (self.c_dtype == "INT32"):
            raise ValueError("integer GEMM requires INT32; floating GEMM requires FP32")
        if self.semantic_macs is not None and (type(self.semantic_macs) is not int or not 0 <= self.semantic_macs <= self.m * self.n * self.k):
            raise ValueError("semantic MACs must fit issued shape")


@dataclass(frozen=True)
class VpuSpec:
    kind: str
    mac: int = 0
    elements: int = 0
    vectors: int = 0
    length: int = 0
    read_bytes: int = 0
    write_bytes: int = 0
    dram_read_bytes: int = 0
    dram_write_bytes: int = 0
    input_dtype: str = "FP32"
    output_dtype: str = "FP32"
    evidence: str = ""

    def __post_init__(self) -> None:
        if any(type(n) is not int or n < 0 for n in (self.mac, self.elements, self.vectors,
                self.length, self.read_bytes, self.write_bytes, self.dram_read_bytes,
                self.dram_write_bytes)):
            raise ValueError("VPU workloads must be nonnegative integers")
        if self.input_dtype not in _FORMAT_BITS or self.output_dtype not in _FORMAT_BITS:
            raise ValueError("unsupported VPU tensor type")


@dataclass(frozen=True)
class CpuSpec:
    kind: str = "sampling"
    vectors: int = 1
    length: int = 1
    read_bytes: int = 0
    write_bytes: int = 0
    dram_read_bytes: int = 0
    dram_write_bytes: int = 0
    input_dtype: str = "FP32"
    evidence: str = ""

    def __post_init__(self) -> None:
        if self.kind != "sampling":
            raise ValueError("CPU model only supports explicit sampling comparison budgets")
        _positive_dimensions((self.vectors, self.length))
        if any(type(n) is not int or n < 0 for n in (self.read_bytes, self.write_bytes,
                self.dram_read_bytes, self.dram_write_bytes)) or self.input_dtype not in _FORMAT_BITS:
            raise ValueError("invalid CPU traffic or dtype")


@dataclass(frozen=True)
class TransferSpec:
    stream_id: str
    source_domain: str
    destination_domain: str
    byte_count: int
    tensor: TensorRef | None = None
    direction: str = "copy"
    evidence: str = ""

    def __post_init__(self) -> None:
        if not self.stream_id or type(self.byte_count) is not int or self.byte_count < 0:
            raise ValueError("transfer requires a unique stream and nonnegative byte count")
        if self.direction not in ("read", "write", "copy"):
            raise ValueError("unsupported transfer direction")


TaskSpec: TypeAlias = GemmSpec | VpuSpec | CpuSpec | TransferSpec


@dataclass(frozen=True)
class PrimitiveTask:
    id: str
    parent_op_id: str
    engine_kind: str
    spec: TaskSpec
    dependencies: tuple[str, ...] = ()
    inputs: tuple[TensorRef, ...] = ()
    outputs: tuple[TensorRef, ...] = ()
    allowed_instances: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        expected = {"GEMM": GemmSpec, "VPU": VpuSpec, "CPU": CpuSpec, "DMA": TransferSpec}
        if not self.id or self.engine_kind not in expected or not isinstance(self.spec, expected[self.engine_kind]):
            raise ValueError("task engine and specification disagree")


@dataclass(frozen=True)
class TaskGraph:
    operations: tuple[LogicalOp, ...]
    tasks: tuple[PrimitiveTask, ...]
    tensors: tuple[Tensor, ...] = ()
    evidence: str = "user-defined lowering"

    def validate(self) -> tuple[str, ...]:
        ops = {op.id: op for op in self.operations}
        tasks = {task.id: task for task in self.tasks}
        tensors = {tensor.id: tensor for tensor in self.tensors}
        if len(ops) != len(self.operations) or len(tasks) != len(self.tasks) or len(tensors) != len(self.tensors):
            raise ValueError("duplicate operation/task/tensor ID")
        for tensor in self.tensors:
            if tensor.base_id is not None:
                if tensor.base_id not in tensors or tensors[tensor.base_id].base_id is not None:
                    raise ValueError("tensor view must reference a base allocation")
                base = tensors[tensor.base_id]
                if (tensor.dtype != base.dtype or tensor.size_bytes > base.size_bytes or
                        math.prod(tensor.shape) > math.prod(base.shape) or
                        tensor.memory_domain != base.memory_domain):
                    raise ValueError("view does not fit its base allocation/type/domain")
                if tensor.base_starts or tensor.base_stops or tensor.base_steps:
                    starts, stops = TensorRef(base.id, tensor.base_starts, tensor.base_stops).bounds(base)
                    steps = tensor.base_steps or (1,) * len(base.shape)
                    if len(steps) != len(base.shape) or any(type(n) is not int or n < 1 for n in steps):
                        raise ValueError("invalid tensor view strides")
                    selected = math.prod((b - a + step - 1) // step
                                         for a, b, step in zip(starts, stops, steps))
                    if selected != math.prod(tensor.shape):
                        raise ValueError("view shape does not match selected base elements")
                # Without a selection, a view reshapes the first prod(shape)
                # base elements, not the entire allocation.
        pending = {task.id: set(task.dependencies) for task in self.tasks}
        ordered: list[str] = []
        ancestors: dict[str, set[str]] = {}
        for task in self.tasks:
            if task.parent_op_id not in ops or any(dep not in tasks for dep in task.dependencies):
                raise ValueError("missing parent operation or task dependency")
            for ref in (*task.inputs, *task.outputs):
                if ref.tensor_id not in tensors:
                    raise ValueError("missing tensor reference")
                ref.bounds(tensors[ref.tensor_id])
            if isinstance(task.spec, GemmSpec) and task.inputs:
                if len(task.inputs) != 2:
                    raise ValueError("GEMM requires two operand references")
                spec = task.spec
                for ref, shape, dtype, scale_axis, storage in zip(
                        task.inputs, ((spec.m, spec.k), (spec.k, spec.n)),
                        (spec.a_fmt, spec.b_fmt), (1, 0),
                        (spec.a_storage_shape, spec.b_storage_shape)):
                    tensor = tensors[ref.tensor_id]
                    starts, stops = ref.bounds(tensor)
                    actual = tuple(b - a for a, b in zip(starts, stops))
                    if actual != shape or tensor.dtype != dtype:
                        raise ValueError("GEMM operand shape/type mismatch")
                    if dtype.startswith("MX") and (tensor.block_size != spec.block_size or
                            tensor.scale_axis % len(tensor.shape) != scale_axis):
                        raise ValueError("GEMM operand MX block/scale axis mismatch")
                    if storage:
                        base = tensors[tensor.base_id or tensor.id]
                        if base.shape != storage or starts != (0, 0):
                            raise ValueError("GEMM storage shape does not match its backing allocation")
                        if tensor.base_id is not None and (
                                (tensor.base_starts or (0, 0)) != (0, 0) or
                                (tensor.base_stops or base.shape) != shape or
                                (tensor.base_steps or (1, 1)) != (1, 1)):
                            raise ValueError("GEMM backing view requires an explicit layout conversion")
                if any(tensors[ref.tensor_id].dtype != spec.c_dtype for ref in task.outputs):
                    raise ValueError("GEMM output type mismatch")
        while pending:
            ready = sorted(key for key, dependencies in pending.items() if not dependencies)
            if not ready:
                raise ValueError("task graph contains a dependency cycle")
            for key in ready:
                ancestors[key] = set(tasks[key].dependencies)
                for dep in tasks[key].dependencies:
                    ancestors[key].update(ancestors[dep])
                ordered.append(key)
                del pending[key]
            for dependencies in pending.values():
                dependencies.difference_update(ready)
        regions = _ElementRegions(max((math.prod(t.shape) for t in self.tensors), default=1))
        selections: dict[str, int] = {}
        references: dict[TensorRef, int] = {}

        def region(ref: TensorRef) -> int:
            if ref not in references:
                tensor = tensors[ref.tensor_id]
                starts, stops = ref.bounds(tensor)
                elements = regions.box(tensor.shape, starts, stops)
                if tensor.base_id is not None and (tensor.base_starts or tensor.base_stops or tensor.base_steps):
                    if tensor.id not in selections:
                        base = tensors[tensor.base_id]
                        a, b = TensorRef(base.id, tensor.base_starts, tensor.base_stops).bounds(base)
                        selections[tensor.id] = regions.box(base.shape, a, b, tensor.base_steps)
                    elements = regions.select(selections[tensor.id], elements, regions.top)
                references[ref] = elements
            return references[ref]

        writers: dict[str, list[tuple[str, int]]] = {}
        for task in self.tasks:
            for ref in task.outputs:
                tensor = tensors[ref.tensor_id]
                writers.setdefault(tensor.base_id or tensor.id, []).append((task.id, region(ref)))
        for task in self.tasks:
            for index, ref in enumerate(task.inputs):
                tensor = tensors[ref.tensor_id]
                base = tensors[tensor.base_id or tensor.id]
                requested = region(ref)
                if isinstance(task.spec, GemmSpec) and (
                        task.spec.a_storage_shape if index == 0 else task.spec.b_storage_shape):
                    requested = region(TensorRef(base.id))
                matching = [(writer, output) for writer, output in writers.get(base.id, [])
                            if writer != task.id and regions.intersection(requested, output)]
                if any(writer not in ancestors[task.id] for writer, _ in matching):
                    raise ValueError("tensor producer is not a dependency ancestor")
                if not base.external:
                    if not matching and not any(writer != task.id for writer, _ in writers.get(base.id, [])):
                        raise ValueError("input has no producer and is not external")
                    covered = 0
                    for _, output in matching:
                        covered = regions.union(covered, output)
                    if regions.difference(requested, covered):
                        raise ValueError("input includes an unwritten tensor slice")
        for entries in writers.values():
            for i, (a, left) in enumerate(entries):
                for b, right in entries[i + 1:]:
                    if a != b and regions.intersection(left, right) and a not in ancestors[b] and b not in ancestors[a]:
                        raise ValueError("unordered overlapping writes to shared tensor allocation")
        streams = [task.spec.stream_id for task in self.tasks if isinstance(task.spec, TransferSpec)]
        if len(streams) != len(set(streams)):
            raise ValueError("a transfer stream must have exactly one owner")
        return tuple(ordered)


class _ElementRegions:
    """Decision diagrams keep strided and reshaped element coverage exact without enumeration."""

    def __init__(self, size: int) -> None:
        self.top = max(1, (size - 1).bit_length()) - 1
        self.nodes = [(-1, 0, 0), (-1, 1, 1)]  # empty / full suffix
        self.unique: dict[tuple[int, int, int], int] = {}
        self.combinations: dict[tuple[bool, int, int], int] = {}
        self.complements = {0: 1, 1: 0}
        self.prefixes: dict[tuple[int, int], int] = {}
        self.rotations: dict[tuple[int, int, int], int] = {}
        self.counts: dict[tuple[int, int], int] = {}
        self.selections: dict[tuple[int, int, int], int] = {}

    def _node(self, bit: int, left: int, right: int) -> int:
        if left == right:
            return left
        key = (bit, left, right)
        if key not in self.unique:
            self.unique[key] = len(self.nodes)
            self.nodes.append(key)
        return self.unique[key]

    def _children(self, node: int, bit: int) -> tuple[int, int]:
        level, left, right = self.nodes[node]
        return (left, right) if level == bit else (node, node)

    def _combine(self, union: bool, a: int, b: int) -> int:
        a, b = sorted((a, b))
        if a == b:
            return a
        if a == 0:
            return b if union else 0
        if a == 1:
            return 1 if union else b
        key = (union, a, b)
        if key not in self.combinations:
            bit = max(self.nodes[a][0], self.nodes[b][0])
            a0, a1 = self._children(a, bit)
            b0, b1 = self._children(b, bit)
            self.combinations[key] = self._node(bit, self._combine(union, a0, b0),
                                               self._combine(union, a1, b1))
        return self.combinations[key]

    def union(self, a: int, b: int) -> int:
        return self._combine(True, a, b)

    def intersection(self, a: int, b: int) -> int:
        return self._combine(False, a, b)

    def _complement(self, node: int) -> int:
        if node not in self.complements:
            bit, left, right = self.nodes[node]
            self.complements[node] = self._node(bit, self._complement(left), self._complement(right))
        return self.complements[node]

    def difference(self, a: int, b: int) -> int:
        return self.intersection(a, self._complement(b))

    def _prefix(self, stop: int, bit: int) -> int:
        if stop <= 0:
            return 0
        if stop >= 1 << (bit + 1):
            return 1
        key = (stop, bit)
        if key not in self.prefixes:
            half = 1 << bit
            if stop <= half:
                node = self._node(bit, self._prefix(stop, bit - 1), 0)
            else:
                node = self._node(bit, 1, self._prefix(stop - half, bit - 1))
            self.prefixes[key] = node
        return self.prefixes[key]

    def interval(self, start: int, stop: int) -> int:
        return self.difference(self._prefix(stop, self.top), self._prefix(start, self.top))

    def _rotate(self, node: int, offset: int, bit: int) -> int:
        """Translate modulo a suffix's address space, using the carry boundary."""
        offset %= 1 << (bit + 1)
        if node <= 1 or not offset:
            return node
        key = (node, offset, bit)
        if key not in self.rotations:
            half = 1 << bit
            low, high = self._children(node, bit)
            low = self._rotate(low, offset % half, bit - 1)
            high = self._rotate(high, offset % half, bit - 1)
            if offset >= half:
                low, high = high, low
            boundary = self._prefix(offset % half, bit - 1)
            left = self.union(self.difference(low, boundary), self.intersection(high, boundary))
            right = self.union(self.difference(high, boundary), self.intersection(low, boundary))
            self.rotations[key] = self._node(bit, left, right)
        return self.rotations[key]

    def _shift(self, node: int, offset: int) -> int:
        if not offset or not node:
            return node
        size = 1 << (self.top + 1)
        if abs(offset) >= size:
            return 0
        translated = self._rotate(node, offset, self.top)
        return self.intersection(translated, self.interval(max(0, offset), min(size, size + offset)))

    def _repeat(self, node: int, count: int, stride: int) -> int:
        result, offset, span = 0, 0, stride
        while count:
            if count & 1:
                result = self.union(result, self._shift(node, offset))
                offset += span
            count >>= 1
            if count:
                node = self.union(node, self._shift(node, span))
                span *= 2
        return result

    def box(self, shape: tuple[int, ...], starts: tuple[int, ...],
            stops: tuple[int, ...], steps: tuple[int, ...] = ()) -> int:
        node, stride = self.interval(0, 1), 1
        for n, a, b, step in reversed(tuple(zip(shape, starts, stops, steps or (1,) * len(shape)))):
            if step == 1 and node == self.interval(0, stride):
                node = self.interval(a * stride, b * stride)
            else:
                node = self._shift(self._repeat(node, (b - a + step - 1) // step, step * stride), a * stride)
            stride *= n
        return node

    def _count(self, node: int, bit: int) -> int:
        if node <= 1:
            return node << (bit + 1)
        key = (node, bit)
        if key not in self.counts:
            level, left, right = self.nodes[node]
            self.counts[key] = (self._count(left, level - 1) + self._count(right, level - 1)) << (bit - level)
        return self.counts[key]

    def select(self, node: int, ordinals: int, bit: int) -> int:
        """Keep the selected base elements whose row-major ranks are in ordinals."""
        if not node or not ordinals:
            return 0
        key = (node, ordinals, bit)
        if key not in self.selections:
            total = self._count(node, bit)
            if not self.difference(self.interval(0, total), ordinals):
                result = node
            elif node == 1:
                # Ordinals are global addresses; here they become local suffixes.
                result = ordinals
                while self.nodes[result][0] > bit:
                    result = self.nodes[result][1]
            else:
                left, right = self._children(node, bit)
                split = self._count(left, bit - 1)
                a = self.intersection(ordinals, self.interval(0, split))
                b = self._shift(self.intersection(ordinals, self.interval(split, total)), -split)
                result = self._node(bit, self.select(left, a, bit - 1), self.select(right, b, bit - 1))
            self.selections[key] = result
        return self.selections[key]
