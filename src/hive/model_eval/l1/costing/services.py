"""Shared service bookkeeping for primitive cost functions.

This module owns phase/traffic/buffer construction only.  Operator semantics
stay in the operator form modules; engine microarchitecture is out of scope.
"""
from __future__ import annotations

from .. import dram
from ..ledger import BufferRequest, Charge, EngineCost, Ledger, Origin, Phase, ResourceClaim, Traffic

_LOCAL_REF = "arch.md:942-947: single local request port, read/write combined"
_LAYOUT_LIMITS = (
    "scratch/staged chunks only; complete tensor residency is not inferred",
    "layout, strides, bank conflicts and cross-domain routing latency are unmodeled",
)


class ServiceBuilder:
    """Shared bookkeeping, not an engine routing or service model."""

    def __init__(self, task, config, plan):
        self.task, self.config = task, config
        self.alignment = plan.alignment
        if type(self.alignment) is not int or self.alignment < 1:
            raise ValueError("execution plan alignment must be a positive integer")
        self.engine = config.resolve("engine")
        self.local = None
        self.phases = []
        self.traffic = []
        self.buffers = []
        self.ledger = Ledger()
        self.excluded = list(_LAYOUT_LIMITS)

    def local_resource(self):
        if self.local is None:
            self.local = self.config.resolve("port.local")
        return self.local

    def add(self, name, seconds, category, dependencies=(), *, resource="engine",
            raw_resource=None, byte_count=0, cycles=0, ops=0, source="structural",
            evidence="", overlap_class="serial"):
        physical = self.config.resolve(resource)
        amount = byte_count / seconds if resource == "dram" and seconds else 1.0
        phase_id = f"{self.task.id}:{name}"
        phase = Phase(phase_id, seconds, category, tuple(dependencies),
                      (ResourceClaim(physical, amount),), source=source, evidence=evidence)
        self.phases.append(phase)
        self.ledger.charges.append(Charge(
            raw_resource or resource, self.config.id, Origin(phase_id, self.task.id),
            category, seconds, bytes=byte_count or 0, cycles=cycles or 0, ops=ops or 0,
            overlap_class=overlap_class, evidence=evidence, source=source))
        if seconds is None:
            self.excluded.append(f"{phase_id}: unknown service: {evidence}")
        return phase_id

    def dram_phase(self, name, byte_count, dependencies=()):
        return self.add(name, dram.seconds(byte_count, self.config.profile), "dram",
                        dependencies, resource="dram", byte_count=byte_count,
                        evidence="explicit external bytes at profile effective DRAM bandwidth; serviced once")

    def local_phase(self, name, byte_count, category, dependencies=(), *, overlap_class="serial",
                    evidence="", resource="port.local"):
        value = dram.local_seconds(byte_count, self.config.profile)
        detail = _LOCAL_REF + ("; " + evidence if evidence else "")
        if value is None:
            detail += "; local_iface_bytes_per_s_per_core is unknown"
        return self.add(name, value, category, dependencies, resource=resource,
                        raw_resource="local_port", byte_count=byte_count,
                        source="architecture_peak", evidence=detail, overlap_class=overlap_class)

    def flow(self, stream, interface, domain, direction, byte_count, tensor_id=None, segment=""):
        if byte_count:
            self.traffic.append(Traffic(stream, self.task.id, tensor_id, interface, domain,
                                        direction, byte_count, byte_count, segment=segment,
                                        evidence="explicit physical bytes; alignment is allocation-only"))

    def chunk(self, name, domain, byte_count, acquire, release, tensor_id=None, *, chunk_bytes=32):
        if byte_count:
            # Alignment pads the allocation, not the physical staging chunk.
            self.buffers.append(BufferRequest(
                f"{self.task.id}:{name}", domain, min(byte_count, chunk_bytes),
                self.alignment, acquire, release, tensor_id))

    def finish(self, metrics=None):
        self.ledger.excluded = sorted(set(self.excluded))
        return EngineCost(self.task.id, self.config.id, tuple(self.phases),
                          (self.engine,), tuple(self.traffic), tuple(self.buffers),
                          metrics or {}, self.ledger, tuple(self.ledger.excluded))


def boundary_read(builder, spec):
    """A primitive's declared DRAM ingress precedes its local/compute stages."""
    if not spec.dram_read_bytes:
        return ()
    phase = builder.dram_phase("activation_stage_read", spec.dram_read_bytes)
    return (phase,)


def boundary_write(builder, spec, dependencies):
    if not spec.dram_write_bytes:
        return tuple(dependencies)
    phase = builder.dram_phase("activation_stage_write", spec.dram_write_bytes, dependencies)
    return (phase,)


def primitive_flow(builder, spec, start, local_phase, end, *, read_end=None,
                   chunk_bytes=32, double_buffer=False):
    """Cross-interface identities do not imply folding serial service phases."""
    domain = builder.config.memory_domain
    for direction, local_bytes, external_bytes, refs in (
            ("read", spec.read_bytes, spec.dram_read_bytes, builder.task.inputs),
            ("write", spec.write_bytes, spec.dram_write_bytes, builder.task.outputs)):
        stream = f"{builder.task.id}:{direction}"
        tensor_id = refs[0].tensor_id if len(refs) == 1 else None
        if external_bytes:
            builder.flow(stream, builder.config.resolve("dram"), "dram", direction,
                         external_bytes, tensor_id, "boundary")
        if local_bytes:
            builder.flow(stream, builder.local_resource(), domain, direction,
                         local_bytes, tensor_id, "compute/io")
        count = max(local_bytes, external_bytes)
        acquire = start if direction == "read" else local_phase
        release = (read_end or end) if direction == "read" else end
        builder.chunk(f"{direction}_chunk", domain, count, acquire, release,
                      tensor_id, chunk_bytes=chunk_bytes)
        if double_buffer:
            builder.chunk(f"{direction}_chunk_alternate", domain, count, acquire, release,
                          tensor_id, chunk_bytes=chunk_bytes)


# Compatibility names for existing imports.
_ServiceBuilder = ServiceBuilder
_boundary_read = boundary_read
_boundary_write = boundary_write
_primitive_flow = primitive_flow
