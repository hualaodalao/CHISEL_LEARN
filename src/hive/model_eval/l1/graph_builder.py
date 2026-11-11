"""Public task-graph builder shared by all layer emitters.

This is the former private ``mapping._GraphBuilder`` with explicit operation
registration, view construction and dependency helpers.  Its task/tensor naming
and padding behaviour are unchanged so emitters can reproduce the legacy graph.
"""

from __future__ import annotations

import math
from dataclasses import replace

from ..common.opspec import (GemmSpec, LogicalOp, PrimitiveTask, Tensor, TensorRef,
                             TransferSpec, VpuSpec)
from .split_policies import split_commands


class GraphBuilder:
    def __init__(self, block_size: int, *, operations: tuple[LogicalOp, ...] = (),
                 split_policy: str = "default"):
        self.operations: list[LogicalOp] = list(operations)
        self.tasks: list[PrimitiveTask] = []
        self.tensors: dict[str, Tensor] = {}
        self.producers: dict[str, str] = {}
        self.padded_operands: dict[tuple[str, tuple[int, ...]], str] = {}
        self.block_size = block_size
        self.split_policy = split_policy

    def add_op(self, op: LogicalOp) -> LogicalOp:
        if any(existing.id == op.id for existing in self.operations):
            raise ValueError(f"duplicate logical op: {op.id}")
        self.operations.append(op)
        return op

    def op(self, op_id: str) -> LogicalOp:
        for operation in self.operations:
            if operation.id == op_id:
                return operation
        raise KeyError(f"unregistered logical op: {op_id}")

    def tensor(self, id: str, shape: tuple[int, ...], dtype: str, *, external=False,
               base_id: str | None = None, base_starts=(), base_stops=(), base_steps=(),
               scale_axis=-1, memory_domain: str = "dram") -> str:
        self.tensors[id] = Tensor(id, shape, dtype, memory_domain=memory_domain,
                                  base_id=base_id, external=external,
                                  block_size=self.block_size, base_starts=base_starts,
                                  base_stops=base_stops, base_steps=base_steps, scale_axis=scale_axis)
        return id

    def view(self, id: str, base: str, shape: tuple[int, ...], *, starts=(), stops=(),
             steps=(), dtype: str | None = None, scale_axis=-1) -> str:
        source = self.tensors[base]
        self.tensor(id, shape, dtype or source.dtype, base_id=base, base_starts=starts,
                    base_stops=stops, base_steps=steps, scale_axis=scale_axis,
                    memory_domain=source.memory_domain)
        if base in self.producers:
            self.producers[id] = self.producers[base]
        return id

    def task(self, op: LogicalOp, suffix: str, engine: str, spec, inputs: tuple[str, ...],
             shape: tuple[int, ...] | None, dtype="FP32", *, dependencies=(), scale_axis=-1,
             memory_domain: str = "dram") -> str | None:
        id = op.id + "/" + suffix
        deps = tuple(dict.fromkeys((*(self.producers[t] for t in inputs
                                     if t in self.producers), *dependencies)))
        outputs = ()
        result = None
        if shape is not None:
            result = self.tensor(id + ":out", shape, dtype, scale_axis=scale_axis,
                                 memory_domain=memory_domain)
            self.producers[result] = id
            outputs = (TensorRef(result),)
        self.tasks.append(PrimitiveTask(id, op.id, engine, spec, deps,
                                        tuple(TensorRef(t) for t in inputs), outputs))
        return result

    def convert(self, op: LogicalOp, tensor: str, dtype: str, suffix: str, *, scale_axis=-1) -> str:
        source = self.tensors[tensor]
        if source.dtype == dtype and (not dtype.startswith("MX") or
                source.scale_axis % len(source.shape) == scale_axis % len(source.shape)):
            return tensor
        target = Tensor("target", source.shape, dtype, block_size=self.block_size,
                        scale_axis=scale_axis, memory_domain=source.memory_domain)
        return self.task(op, suffix, "VPU", VpuSpec(
            "convert", elements=math.prod(source.shape), read_bytes=source.size_bytes,
            write_bytes=target.size_bytes, dram_read_bytes=source.size_bytes,
            dram_write_bytes=target.size_bytes, input_dtype=source.dtype, output_dtype=dtype,
            evidence="explicit dtype/layout/quantization conversion; service unknown"),
            (tensor,), source.shape, dtype, scale_axis=scale_axis,
            memory_domain=source.memory_domain)

    def vector(self, op: LogicalOp, inputs: tuple[str, ...], shape: tuple[int, ...], *,
               suffix="execute", spec: VpuSpec | None = None, dtype="FP32",
               dependencies=(), memory_domain: str = "dram") -> str:
        spec = spec or VpuSpec(op.kind, op.mac, op.elements, op.vectors, op.length,
                              op.read_bytes, op.write_bytes, op.dram_read_bytes,
                              op.dram_write_bytes, evidence=op.evidence)
        result = self.task(op, suffix, "VPU", spec, inputs, shape, dtype,
                           dependencies=dependencies, memory_domain=memory_domain)
        return result

    def gemm(self, op: LogicalOp, a: str, b: str, spec: GemmSpec, geometry,
             suffix="execute", split_policy: str | None = None) -> str:
        from .gemm_model import GemmShape
        policy = split_policy or self.split_policy
        a_domain, b_domain = self.tensors[a].memory_domain, self.tensors[b].memory_domain
        commands = split_commands(
            GemmShape(spec.m, spec.n, spec.k, spec.a_fmt, spec.b_fmt,
                      block_size=spec.block_size), geometry, policy)
        k_parts = len({command.k_offset for command in commands})
        padded_n = sum(command.n for command in commands if command.m_offset == 0 and command.k_offset == 0)
        padded_k = sum(command.k for command in commands if command.m_offset == 0 and command.n_offset == 0)
        operands = []
        for name, tensor_id, storage_shape, axis in (
                ("a", a, (spec.m, padded_k), 1), ("b", b, (padded_k, padded_n), 0)):
            source = self.tensors[tensor_id]
            key = (tensor_id, storage_shape)
            if key in self.padded_operands:
                operands.append(self.padded_operands[key])
                continue
            if source.shape != storage_shape:
                target = Tensor("target", storage_shape, source.dtype,
                                block_size=self.block_size, scale_axis=axis,
                                memory_domain=source.memory_domain)
                backing = self.task(op, suffix + "-pad-" + name, "VPU", VpuSpec(
                    "convert", elements=math.prod(storage_shape), read_bytes=source.size_bytes,
                    write_bytes=target.size_bytes, dram_read_bytes=source.size_bytes,
                    dram_write_bytes=target.size_bytes, input_dtype=source.dtype,
                    output_dtype=source.dtype,
                    evidence="explicit operand repack and zero padding, including MX scales; service unknown"),
                    (tensor_id,), storage_shape, source.dtype, scale_axis=axis)
                tensor_id = self.tensor(backing + ":logical", source.shape, source.dtype,
                                        base_id=backing, base_starts=(0, 0),
                                        base_stops=source.shape, scale_axis=axis,
                                        memory_domain=source.memory_domain)
                self.producers[tensor_id] = self.producers[backing]
                self.padded_operands[key] = tensor_id
            operands.append(tensor_id)
        spec = replace(spec, split_policy=policy,
                       a_storage_shape=(spec.m, padded_k),
                       b_storage_shape=(padded_k, padded_n),
                       a_domain=a_domain, b_domain=b_domain)
        shape = (spec.m, padded_n) if k_parts == 1 else (k_parts, spec.m, padded_n)
        partial = self.task(op, suffix, "GEMM", spec, tuple(operands), shape, spec.c_dtype,
                            memory_domain=spec.c_domain)
        result = partial
        if k_parts > 1:
            cells = sum(command.m * command.n for command in commands if command.k_offset)
            result = self.vector(op, (partial,), (spec.m, padded_n), suffix=suffix + "-k-merge",
                                 dtype=spec.c_dtype, memory_domain=spec.c_domain,
                                 spec=VpuSpec(
                                     "k_accum", mac=cells, elements=cells, read_bytes=8 * cells,
                                     write_bytes=4 * cells, dram_read_bytes=8 * cells,
                                     dram_write_bytes=4 * cells, input_dtype=spec.c_dtype,
                                     output_dtype=spec.c_dtype,
                                     evidence="explicit independent K-command partial-sum merge"))
        if padded_n == spec.n:
            return result
        view = self.tensor(result + ":logical", (spec.m, spec.n), spec.c_dtype,
                           base_id=result, base_starts=(0, 0), base_stops=(spec.m, spec.n),
                           memory_domain=spec.c_domain)
        self.producers[view] = self.producers[result]
        return view

    def dma(self, op: LogicalOp, suffix: str, stream_id: str, byte_count: int, *,
            direction: str, inputs: tuple[str, ...] = (), outputs: tuple[str, ...] = (),
            source_domain="dram", destination_domain="dram", evidence="") -> str:
        id = op.id + "/" + suffix
        deps = tuple(dict.fromkeys(self.producers[t] for t in inputs if t in self.producers))
        self.tasks.append(PrimitiveTask(
            id, op.id, "DMA",
            TransferSpec(stream_id, source_domain, destination_domain, byte_count,
                         direction=direction, evidence=evidence),
            deps, tuple(TensorRef(t) for t in inputs),
            tuple(TensorRef(t) for t in outputs)))
        for tensor_id in outputs:
            self.producers[tensor_id] = id
        return id
