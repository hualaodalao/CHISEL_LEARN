"""Physical resources, not throughput multipliers or automatic graph sharding."""
from __future__ import annotations

import math
from dataclasses import asdict, dataclass
from typing import Iterable

from ..common.opspec import PrimitiveTask, TaskGraph, TransferSpec
from ..l1.params import ModelParams
from .geometry import ArrayGeometry
from .profiles import HwProfile


@dataclass(frozen=True)
class Resource:
    id: str
    kind: str
    capacity: float
    source: str = "structural"
    evidence: str = ""

    def __post_init__(self) -> None:
        if not self.id or self.kind not in ("slot", "bandwidth"):
            raise ValueError("resource requires an ID and slot/bandwidth kind")
        if not math.isfinite(self.capacity) or self.capacity <= 0:
            raise ValueError("physical resource capacity must be finite and positive")


@dataclass(frozen=True)
class MemoryDomain:
    id: str
    core_id: str | None
    capacity_bytes: int | None
    source: str = "profile"
    evidence: str = ""

    def __post_init__(self) -> None:
        if not self.id or (self.capacity_bytes is not None and
                           (type(self.capacity_bytes) is not int or self.capacity_bytes < 0)):
            raise ValueError("invalid memory domain/capacity")

    def accessible_by(self, core_id: str) -> bool:
        return self.core_id is None or self.core_id == core_id


@dataclass(frozen=True)
class EngineConfig:
    id: str
    kind: str
    core_id: str
    profile: HwProfile
    geometry: ArrayGeometry
    params: ModelParams
    resources: dict[str, str]
    memory_domain: str
    source: str

    def resolve(self, name: str) -> str:
        """Return a registered physical ID; unknown names are never invented."""
        if name in self.resources:
            return self.resources[name]
        if name in self.resources.values():
            return name
        raise KeyError(f"{self.id}: unregistered resource {name!r}")


@dataclass(frozen=True)
class Topology:
    instances: dict[str, EngineConfig]
    resources: dict[str, Resource]
    memory_domains: dict[str, MemoryDomain]
    source: str = "structural"
    evidence: str = ""

    def __post_init__(self) -> None:
        for key, resource in self.resources.items():
            if key != resource.id:
                raise ValueError("resource registry key disagrees with ID")
        for key, domain in self.memory_domains.items():
            if key != domain.id:
                raise ValueError("memory domain registry key disagrees with ID")
        for key, instance in self.instances.items():
            if key != instance.id or instance.kind not in ("GEMM", "VPU", "CPU", "DMA"):
                raise ValueError("invalid engine instance registry")
            if instance.memory_domain not in self.memory_domains:
                raise ValueError("engine memory domain is unregistered")
            domain = self.memory_domains[instance.memory_domain]
            if not domain.accessible_by(instance.core_id):
                raise ValueError("engine cannot own another core's private memory")
            if any(value not in self.resources for value in instance.resources.values()):
                raise ValueError("engine alias references unregistered physical resource")
            try:
                engine_resource = self.resources[instance.resolve("engine")]
            except KeyError as error:
                raise ValueError("engine requires a registered engine lease") from error
            if engine_resource.kind != "slot" or engine_resource.capacity < 1:
                raise ValueError("engine lease must be a physical slot with capacity at least one")

    @property
    def active_cores(self) -> tuple[str, ...]:
        return tuple(sorted({instance.core_id for instance in self.instances.values()}))

    def candidates(self, task: PrimitiveTask) -> tuple[EngineConfig, ...]:
        unknown = set(task.allowed_instances) - self.instances.keys()
        if unknown:
            raise ValueError(f"{task.id}: unknown allowed instances {sorted(unknown)}")
        return tuple(instance for _, instance in sorted(self.instances.items())
                     if instance.kind == task.engine_kind and
                     (not task.allowed_instances or instance.id in task.allowed_instances))

    def validate_access(self, graph: TaskGraph, task: PrimitiveTask,
                        instance: EngineConfig) -> None:
        tensors = {tensor.id: tensor for tensor in graph.tensors}
        spec = task.spec
        if isinstance(spec, TransferSpec):
            if instance.kind != "DMA":
                raise ValueError("only an explicit DMA task can bridge domains")
            for name in (spec.source_domain, spec.destination_domain):
                if name not in self.memory_domains:
                    raise ValueError(f"{task.id}: unregistered DMA domain {name}")
            if spec.tensor is not None:
                if spec.tensor.tensor_id not in tensors:
                    raise ValueError("DMA tensor is unregistered")
                spec.tensor.bounds(tensors[spec.tensor.tensor_id])
                if tensors[spec.tensor.tensor_id].memory_domain != spec.source_domain:
                    raise ValueError("DMA tensor must belong to the declared source domain")
            for ref, expected in ((*((ref, spec.source_domain) for ref in task.inputs),
                                   *((ref, spec.destination_domain) for ref in task.outputs))):
                if tensors[ref.tensor_id].memory_domain != expected:
                    raise ValueError("DMA references disagree with explicit source/destination")
            return
        for ref in (*task.inputs, *task.outputs):
            name = tensors[ref.tensor_id].memory_domain
            if name not in self.memory_domains:
                raise ValueError(f"{task.id}: unregistered tensor domain {name}")
            if not self.memory_domains[name].accessible_by(instance.core_id):
                raise ValueError(f"{task.id}: private domain {name} requires an explicit DMA copy")

    def to_dict(self) -> dict:
        return {"source": self.source, "evidence": self.evidence,
                "active_cores": list(self.active_cores),
                "instances": {key: asdict(value) for key, value in sorted(self.instances.items())},
                "resources": {key: asdict(value) for key, value in sorted(self.resources.items())},
                "memory_domains": {key: asdict(value) for key, value in sorted(self.memory_domains.items())}}

    @classmethod
    def default(cls, profile: HwProfile, geometry: ArrayGeometry,
                params: ModelParams) -> Topology:
        return default_topology(profile, geometry, params)

    @classmethod
    def explicit(cls, profile: HwProfile, geometry: ArrayGeometry,
                 params: ModelParams, **scenario) -> Topology:
        return explicit_topology(profile, geometry, params, **scenario)


def _build(profile: HwProfile, geometry: ArrayGeometry, params: ModelParams,
           counts: dict[str, int], cores: tuple[str, ...], source: str, evidence: str) -> Topology:
    resources = {"dram": Resource("dram", "bandwidth", profile.dram_effective_bytes_per_s,
                                  "profile", f"{profile.name}: shared effective DRAM bandwidth")}
    domains = {"dram": MemoryDomain("dram", None, None, "profile", "shared external memory; not private SRAM")}
    instances = {}
    for core in cores:
        local = f"{core}.local"
        resources[local] = Resource(local, "slot", 1, "conservative",
                                   "all local data interfaces share one read/write slot")
        domains[f"{core}.sram"] = MemoryDomain(
            f"{core}.sram", core, profile.sram_bytes_per_core, "profile",
            f"{profile.name}: per-core capacity, never pooled")
    for kind, count in counts.items():
        for index in range(count):
            core = cores[index % len(cores)]
            instance_id = f"{kind.lower()}{index}"
            slot = f"{core}.{instance_id}"
            resources[slot] = Resource(slot, "slot", 1, source, evidence)
            aliases = {"engine": slot, "slot": slot, kind.lower(): slot,
                       "dram": "dram", "dram.read": "dram", "dram.write": "dram",
                       "local": f"{core}.local", "port.local": f"{core}.local"}
            for name in ("dram.a", "dram.b", "dram.c", "dram.kv"):
                aliases[name] = "dram"
            for name in ("port.dma_a", "port.dma_b", "port.dma_c", "port.kv",
                         "vpu.k_accum_port", "vpu.local", "cpu.local", "dma.local"):
                aliases[name] = f"{core}.local"
            for name in ("array.loadW", "array.consume", "vpu.k_accum_add", "compute"):
                aliases[name] = slot
            if kind == "DMA":
                for domain, memory in domains.items():
                    if memory.core_id is not None:
                        aliases[f"port.local@{domain}"] = f"{memory.core_id}.local"
            if kind == "GEMM":
                for side, width in (("a", geometry.scale_a_w), ("b", geometry.scale_b_w)):
                    if width > 0:
                        physical = f"{core}.{instance_id}.scale_{side}"
                        resources[physical] = Resource(
                            physical, "slot", 1, geometry.source_path,
                            f"scale{side.upper()} width {width} bits; one hardware port")
                        aliases[f"port.scale_{side}"] = physical
                        aliases[f"scale_{side}"] = physical
            instances[instance_id] = EngineConfig(instance_id, kind, core, profile, geometry,
                                                   params, aliases, f"{core}.sram", source)
    return Topology(instances, resources, domains, source, evidence)


def default_topology(profile: HwProfile, geometry: ArrayGeometry,
                     params: ModelParams) -> Topology:
    return _build(profile, geometry, params, dict.fromkeys(("GEMM", "VPU", "CPU", "DMA"), 1),
                  ("core0",), "structural", "single active core0; no automatic sharding from profile.n_core")


def explicit_topology(profile: HwProfile, geometry: ArrayGeometry, params: ModelParams, *,
                      gemm_instances: int = 1, vpu_instances: int = 1,
                      cpu_instances: int = 1, dma_instances: int = 1,
                      active_cores: int | Iterable[str] = 1,
                      source: str = "scenario", evidence: str = "") -> Topology:
    counts = dict(zip(("GEMM", "VPU", "CPU", "DMA"),
                      (gemm_instances, vpu_instances, cpu_instances, dma_instances)))
    if any(type(count) is not int or count < 0 for count in counts.values()) or not any(counts.values()):
        raise ValueError("instance counts must be nonnegative integers with at least one engine")
    if isinstance(active_cores, int):
        if type(active_cores) is not int or active_cores < 1:
            raise ValueError("active_cores must be positive")
        cores = tuple(f"core{i}" for i in range(active_cores))
    else:
        cores = tuple(active_cores)
    if not cores or len(cores) != len(set(cores)) or any(not isinstance(c, str) or not c for c in cores):
        raise ValueError("active cores require distinct nonempty IDs")
    if max(counts.values()) < len(cores):
        raise ValueError("every active core must have at least one explicitly counted instance")
    if (any(count > 1 for count in counts.values()) or len(cores) > 1) and (
            not source.strip() or not evidence.strip()):
        raise ValueError("multi-instance topology is a scenario requiring source and evidence")
    return _build(profile, geometry, params, counts, cores, source, evidence)
