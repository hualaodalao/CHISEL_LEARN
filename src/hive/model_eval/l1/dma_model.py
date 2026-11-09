"""Typed transfer service, with explicit endpoint domains and serial interfaces."""

from __future__ import annotations

from ..common.opspec import PrimitiveTask, TransferSpec
from . import dram
from .ledger import (BufferRequest, Charge, EngineCost, Ledger, Origin, Phase,
                     ResourceClaim, Traffic)

_LOCAL_REF = "arch.md:942-947: single local request port, read/write combined"
_LAYOUT_LIMITS = (
    "scratch/staged chunks only; complete tensor residency is not inferred",
    "layout, strides, bank conflicts and cross-domain routing latency are unmodeled",
)


class _ServiceBuilder:
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
            category, seconds, bytes=byte_count, cycles=cycles, ops=ops,
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


def _boundary_read(builder, spec):
    """A primitive's declared DRAM ingress precedes its local/compute stages."""
    if not spec.dram_read_bytes:
        return ()
    phase = builder.dram_phase("activation_stage_read", spec.dram_read_bytes)
    return (phase,)


def _boundary_write(builder, spec, dependencies):
    if not spec.dram_write_bytes:
        return tuple(dependencies)
    phase = builder.dram_phase("activation_stage_write", spec.dram_write_bytes, dependencies)
    return (phase,)


def _primitive_flow(builder, spec, start, local_phase, end, *, read_end=None,
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


def estimate(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    """Charge actual DRAM endpoints, never a private-to-private copy as DRAM."""
    if task.engine_kind != "DMA" or not isinstance(task.spec, TransferSpec) or engine_config.kind != "DMA":
        raise ValueError("DMA estimate requires a DMA task/spec/config")
    spec = task.spec
    builder = _ServiceBuilder(task, engine_config, execution_plan)
    source_is_dram = spec.source_domain == "dram"
    destination_is_dram = spec.destination_domain == "dram"
    inputs = spec.tensor or (task.inputs[0] if len(task.inputs) == 1 else None)
    outputs = task.outputs[0] if len(task.outputs) == 1 else inputs
    source_tensor = inputs.tensor_id if inputs else None
    destination_tensor = outputs.tensor_id if outputs else None
    size = spec.byte_count
    dependencies = ()
    source_phase = None
    local_phase = None

    def local_alias(domain):
        return "port.local" if domain == engine_config.memory_domain else f"port.local@{domain}"

    if source_is_dram and size:
        source_phase = builder.dram_phase("source_read", size)
        dependencies = (source_phase,)
    local_bytes = size * (int(not source_is_dram) + int(not destination_is_dram))
    remote = any(domain not in ("dram", engine_config.memory_domain)
                 for domain in (spec.source_domain, spec.destination_domain))
    if size and remote:
        if not source_is_dram:
            source_phase = builder.local_phase(
                "source_local_read", size, "handoff", dependencies,
                resource=local_alias(spec.source_domain), evidence="declared private source endpoint")
            dependencies = (source_phase,)
        route = builder.add("cross_domain_route", None, "handoff", dependencies, source="unknown",
                            evidence="explicit cross-core DMA route/visibility service is unknown; not a free copy")
        dependencies = (route,)
        if not destination_is_dram:
            local_phase = builder.local_phase(
                "destination_local_write", size, "handoff", dependencies,
                resource=local_alias(spec.destination_domain), evidence="declared private destination endpoint")
            dependencies = (local_phase,)
    elif local_bytes:
        local_phase = builder.local_phase(
            "local_read_write", local_bytes, "handoff", dependencies,
            evidence=f"{spec.source_domain} -> {spec.destination_domain}; declared direction={spec.direction}")
        dependencies = (local_phase,)
    if destination_is_dram and size:
        dependencies = (builder.dram_phase("destination_write", size, dependencies),)
    if not builder.phases:
        dependencies = (builder.add("empty_transfer", 0.0, "handoff",
                                    evidence="explicit zero-byte transfer"),)

    for domain, direction, tensor_id in ((spec.source_domain, "read", source_tensor),
                                          (spec.destination_domain, "write", destination_tensor)):
        interface = engine_config.resolve("dram") if domain == "dram" else engine_config.resolve(local_alias(domain))
        builder.flow(spec.stream_id, interface, domain, direction, size, tensor_id,
                     f"{spec.direction}:{direction}")
        if domain != "dram":
            builder.chunk(f"{direction}_chunk", domain, size,
                          source_phase or local_phase or dependencies[0], dependencies[-1], tensor_id)

    return builder.finish({
        "stream_id": spec.stream_id, "direction": spec.direction,
        "source_domain": spec.source_domain, "destination_domain": spec.destination_domain,
        "physical_bytes": size, "local_bytes": local_bytes,
        "dram_read_bytes": size if source_is_dram else 0,
        "dram_write_bytes": size if destination_is_dram else 0,
        "evidence": spec.evidence,
    })
