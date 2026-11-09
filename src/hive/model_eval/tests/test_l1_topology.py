import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import (LogicalOp, PrimitiveTask, TaskGraph, Tensor,
                                         TensorRef, TransferSpec, VpuSpec)
from hive.model_eval.hw.geometry import ArrayGeometry
from hive.model_eval.hw.profiles import G0
from hive.model_eval.hw.topology import (MemoryDomain, Resource, Topology,
                                       default_topology, explicit_topology)
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import BufferPlan, ExecutionPlan


# Deliberately synthetic geometry: these tests do not depend on active RTL writers.
GEOMETRY = ArrayGeometry("synthetic", "test", 16, 16, 16, 256, 256, 512, 512,
                         32, 32, 16, ("MXFP8",), "unittest", 128, 128, 128, 256)


def synthetic_topology(**kwargs):
    return explicit_topology(G0, GEOMETRY, ModelParams(), source="unittest",
                             evidence="explicit synthetic engine counts", **kwargs)


class TestTopology(unittest.TestCase):
    def test_default_is_one_active_core_not_profile_core_count(self):
        topology = default_topology(G0, GEOMETRY, ModelParams())
        self.assertEqual(G0.n_core, 2)
        self.assertEqual(topology.active_cores, ("core0",))
        self.assertEqual({kind: sum(i.kind == kind for i in topology.instances.values())
                          for kind in ("GEMM", "VPU", "CPU", "DMA")},
                         dict.fromkeys(("GEMM", "VPU", "CPU", "DMA"), 1))
        self.assertEqual(topology.instances["gemm0"].resolve("dram.a"), "dram")
        self.assertEqual(topology.instances["gemm0"].resolve("port.dma_a"),
                         topology.instances["vpu0"].resolve("vpu.local"))
        with self.assertRaises(KeyError):
            topology.instances["gemm0"].resolve("invented")

    def test_private_capacities_are_not_pooled(self):
        topology = synthetic_topology(gemm_instances=2, active_cores=2)
        self.assertEqual(topology.active_cores, ("core0", "core1"))
        for core in topology.active_cores:
            domain = topology.memory_domains[core + ".sram"]
            self.assertEqual(domain.capacity_bytes, G0.sram_bytes_per_core)
            self.assertTrue(domain.accessible_by(core))
            self.assertFalse(domain.accessible_by("other"))
        self.assertIsNone(topology.memory_domains["dram"].capacity_bytes)
        self.assertTrue(topology.memory_domains["dram"].accessible_by("other"))

    def test_multi_instance_requires_provenance(self):
        with self.assertRaisesRegex(ValueError, "source and evidence"):
            explicit_topology(G0, GEOMETRY, ModelParams(), gemm_instances=2)
        for count in (-1, 1.5, True):
            with self.assertRaises(ValueError):
                synthetic_topology(gemm_instances=count)
        with self.assertRaises(ValueError):
            synthetic_topology(active_cores=("same", "same"))
        with self.assertRaises(ValueError):
            synthetic_topology(gemm_instances=1, active_cores=2)

    def test_registry_and_engine_lease_validation(self):
        topology = synthetic_topology()
        with self.assertRaises(ValueError):
            replace(topology, resources={**topology.resources, "wrong": Resource("other", "slot", 1)})
        instance = topology.instances["gemm0"]
        bad = replace(instance, resources={**instance.resources, "engine": "dram"})
        with self.assertRaisesRegex(ValueError, "physical slot"):
            replace(topology, instances={**topology.instances, "gemm0": bad})
        with self.assertRaises(ValueError):
            MemoryDomain("bad", "core0", -1)
        for capacity in (0, -1, float("inf")):
            with self.assertRaises(ValueError):
                Resource("bad", "bandwidth", capacity)

    def test_candidates_are_sorted_and_respect_allowed_instances(self):
        topology = synthetic_topology(vpu_instances=2)
        task = PrimitiveTask("task", "op", "VPU", VpuSpec("residual"))
        self.assertEqual(tuple(i.id for i in topology.candidates(task)), ("vpu0", "vpu1"))
        self.assertEqual(tuple(i.id for i in topology.candidates(replace(task, allowed_instances=("vpu1",)))),
                         ("vpu1",))
        with self.assertRaisesRegex(ValueError, "unknown allowed"):
            topology.candidates(replace(task, allowed_instances=("missing",)))

    def test_private_access_requires_explicit_source_destination_copy(self):
        topology = synthetic_topology(vpu_instances=2, active_cores=2)
        op = LogicalOp("op", "layer", "copy", "custom")
        source = Tensor("source", (8,), "FP32", "core0.sram", external=True)
        dest = Tensor("dest", (8,), "FP32", "core1.sram")
        consume = PrimitiveTask("consume", "op", "VPU", VpuSpec("residual"),
                                inputs=(TensorRef("source"),))
        graph = TaskGraph((op,), (consume,), (source,))
        with self.assertRaisesRegex(ValueError, "explicit DMA"):
            topology.validate_access(graph, consume, topology.instances["vpu1"])
        copy = PrimitiveTask("copy", "op", "DMA", TransferSpec("copy-stream", "core0.sram",
                             "core1.sram", 32, TensorRef("source")),
                             inputs=(TensorRef("source"),), outputs=(TensorRef("dest"),))
        consume = replace(consume, dependencies=("copy",), inputs=(TensorRef("dest"),))
        graph = TaskGraph((op,), (copy, consume), (source, dest))
        graph.validate()
        topology.validate_access(graph, copy, topology.instances["dma0"])
        topology.validate_access(graph, consume, topology.instances["vpu1"])
        bad_copy = replace(copy, spec=replace(copy.spec, destination_domain="core0.sram"))
        with self.assertRaisesRegex(ValueError, "source/destination"):
            topology.validate_access(graph, bad_copy, topology.instances["dma0"])
        with self.assertRaisesRegex(ValueError, "unregistered DMA"):
            topology.validate_access(graph, replace(copy, spec=replace(copy.spec, destination_domain="missing")),
                                     topology.instances["dma0"])

    def test_execution_policy_exactly_matches_engine_contract(self):
        self.assertEqual(ExecutionPlan().activation_policy, "stream")
        ExecutionPlan(activation_policy="stream")
        ExecutionPlan(activation_policy="stage")
        with self.assertRaisesRegex(ValueError, "stream or stage"):
            ExecutionPlan(activation_policy="resident")
        with self.assertRaises(ValueError):
            ExecutionPlan(alignment=0)

    def test_views_have_one_aligned_global_allocation(self):
        op = LogicalOp("op", "layer", "views", "custom")
        base = Tensor("base", (9,), "FP32", "core0.sram")
        view = Tensor("view", (4,), "FP32", "core0.sram", base_id="base")
        producer = PrimitiveTask("producer", "op", "VPU", VpuSpec("residual"),
                                 outputs=(TensorRef("base"),))
        consumer = PrimitiveTask("consumer", "op", "VPU", VpuSpec("residual"), ("producer",),
                                 inputs=(TensorRef("view"),))
        plan = BufferPlan.from_graph(TaskGraph((op,), (producer, consumer), (base, view)))
        self.assertEqual(len(plan.allocations), 1)
        self.assertEqual(plan.allocations[0].allocated_bytes, 64)
        self.assertEqual(plan.allocations[0].acquire.task_id, "producer")
        self.assertEqual(tuple(end.task_id for end in plan.allocations[0].releases), ("consumer",))


if __name__ == "__main__":
    unittest.main()
