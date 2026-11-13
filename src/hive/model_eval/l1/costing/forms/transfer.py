"""DMA transfer operator-form formula with explicit endpoint domains."""
from __future__ import annotations

from ....common.opspec import PrimitiveTask, TransferSpec
from ... import dram
from ...ledger import EngineCost
from ..registry import register_operator_estimator
from ..services import ServiceBuilder as _ServiceBuilder


def _estimate_conversion(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    """DMA inline format conversion: read input bytes, convert, write output bytes.

    Read and write byte counts differ.  Transfer bandwidth reuses the profile
    memory interface (like a pure copy); the conversion rate is an exploration
    scenario (dma_conversion_elements_per_cycle), unknown when unset.  Phases
    stay serial (no fabricated pipelining), so the bound is conservative.
    """
    spec = task.spec
    builder = _ServiceBuilder(task, engine_config, execution_plan)
    params = engine_config.params
    read_bytes = spec.read_bytes if spec.read_bytes is not None else spec.byte_count
    write_bytes = spec.write_bytes if spec.write_bytes is not None else spec.byte_count
    elements = spec.elements
    source_is_dram = spec.source_domain == "dram"
    destination_is_dram = spec.destination_domain == "dram"
    inputs = spec.tensor or (task.inputs[0] if len(task.inputs) == 1 else None)
    outputs = task.outputs[0] if len(task.outputs) == 1 else inputs
    source_tensor = inputs.tensor_id if inputs else None
    destination_tensor = outputs.tensor_id if outputs else None

    def local_alias(domain):
        return "port.local" if domain == engine_config.memory_domain else f"port.local@{domain}"

    dependencies = ()
    source_phase = None
    if read_bytes:
        if source_is_dram:
            source_phase = builder.dram_phase("source_read", read_bytes)
        else:
            source_phase = builder.local_phase(
                "source_local_read", read_bytes, "handoff",
                resource=local_alias(spec.source_domain), evidence="declared private source endpoint")
        dependencies = (source_phase,)

    lanes = params.dma_conversion_elements_per_cycle
    if lanes is not None and elements:
        cycles = (elements + lanes - 1) // lanes
        seconds = dram.cycle_seconds(cycles, engine_config.profile)
        source = "scenario" if seconds is not None else "unknown"
        evidence = (f"exploration scenario: DMA inline conversion {spec.input_dtype}->"
                    f"{spec.output_dtype}; {elements} elements; "
                    f"dma_conversion_elements_per_cycle={lanes}; not measured service")
        if seconds is None:
            evidence += "; profile freq_hz is unknown"
    else:
        cycles, seconds, source = 0, None, "unknown"
        evidence = (f"DMA inline conversion {spec.input_dtype}->{spec.output_dtype}; "
                    "dma_conversion_elements_per_cycle is unset; conversion service unknown, not folded to zero")
    conversion = builder.add("conversion", seconds, "handoff", dependencies,
                             raw_resource="dma_conversion", cycles=cycles, ops=elements,
                             source=source, evidence=evidence)
    dependencies = (conversion,)

    if write_bytes:
        if destination_is_dram:
            dependencies = (builder.dram_phase("destination_write", write_bytes, dependencies),)
        else:
            dependencies = (builder.local_phase(
                "destination_local_write", write_bytes, "handoff", dependencies,
                resource=local_alias(spec.destination_domain), evidence="declared private destination endpoint"),)

    local_bytes = (0 if source_is_dram else read_bytes) + (0 if destination_is_dram else write_bytes)
    for domain, direction, byte_count, tensor_id in (
            (spec.source_domain, "read", read_bytes, source_tensor),
            (spec.destination_domain, "write", write_bytes, destination_tensor)):
        interface = engine_config.resolve("dram") if domain == "dram" else engine_config.resolve(local_alias(domain))
        builder.flow(spec.stream_id, interface, domain, direction, byte_count, tensor_id,
                     f"{spec.direction}:{direction}")
        if domain != "dram":
            builder.chunk(f"{direction}_chunk", domain, byte_count,
                          source_phase or conversion, dependencies[-1], tensor_id)

    return builder.finish({
        "stream_id": spec.stream_id, "direction": spec.direction,
        "source_domain": spec.source_domain, "destination_domain": spec.destination_domain,
        "input_dtype": spec.input_dtype, "output_dtype": spec.output_dtype,
        "physical_bytes": read_bytes + write_bytes,
        "read_bytes": read_bytes, "write_bytes": write_bytes,
        "local_bytes": local_bytes,
        "dram_read_bytes": read_bytes if source_is_dram else 0,
        "dram_write_bytes": write_bytes if destination_is_dram else 0,
        "elements": elements, "conversion_cycles": cycles,
        "evidence": spec.evidence,
    })


def estimate(task: PrimitiveTask, engine_config, execution_plan) -> EngineCost:
    """Charge actual DRAM endpoints, never a private-to-private copy as DRAM."""
    if task.engine_kind != "DMA" or not isinstance(task.spec, TransferSpec) or engine_config.kind != "DMA":
        raise ValueError("DMA estimate requires a DMA task/spec/config")
    if task.spec.input_dtype and task.spec.output_dtype:
        return _estimate_conversion(task, engine_config, execution_plan)
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


register_operator_estimator("transfer", "DMA", estimate, replace=True)
