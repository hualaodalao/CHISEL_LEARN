import json
import unittest
from dataclasses import is_dataclass, replace

from hive.model_eval.common.opspec import (CpuSpec, GemmSpec, LogicalOp, PrimitiveTask,
                                         TaskGraph, Tensor, TensorRef, TransferSpec, VpuSpec)
from hive.model_eval.hw.topology import MemoryDomain, Resource
from hive.model_eval.l1.ledger import BufferRequest, CATEGORIES, EngineCost, Phase, ResourceClaim
from hive.model_eval.l1.residency import BufferAllocation, BufferEndpoint, BufferPlan
from hive.model_eval.l1.scheduler import schedule
from hive.model_eval.tests.test_l1_topology import synthetic_topology


def task(key, kind="VPU", deps=(), **kwargs):
    spec = {"GEMM": GemmSpec(1, 1, 1), "VPU": VpuSpec("residual"), "CPU": CpuSpec()}[kind]
    return PrimitiveTask(key, "op", kind, spec, tuple(deps), **kwargs)


def graph(*tasks, tensors=()):
    return TaskGraph((LogicalOp("op", "layer", "synthetic", "custom"),), tuple(tasks), tuple(tensors))


def costs_for(graph, topology, durations, claims=None, buffers=None):
    costs = {}
    for primitive in graph.tasks:
        costs[primitive.id] = {}
        for instance in topology.candidates(primitive):
            seconds = durations[primitive.id]
            category = primitive.engine_kind.lower() if primitive.engine_kind != "DMA" else "dram"
            phases = (Phase(primitive.id + ":service", seconds, category,
                            claims=tuple((claims or {}).get(primitive.id, ()))),)
            costs[primitive.id][instance.id] = EngineCost(primitive.id, instance.id, phases,
                (instance.resolve("engine"),), buffers=tuple((buffers or {}).get(primitive.id, ())))
    return costs


def by_id(result):
    return {timing.task_id: timing for timing in result.tasks}


class TestScheduler(unittest.TestCase):
    def assert_attribution(self, result):
        self.assertEqual(set(result.attribution_s), set(CATEGORIES))
        self.assertAlmostEqual(sum(result.attribution_s.values()), result.makespan_s)
        self.assertAlmostEqual(sum(result.projection_attribution_s.values()), result.projection_makespan_s)

    def test_independent_overlap_and_serial(self):
        topology = synthetic_topology()
        workload = graph(task("g", "GEMM"), task("v", "VPU"))
        costs = costs_for(workload, topology, {"g": 0.010, "v": 0.008})
        result = schedule(workload, costs, topology)
        self.assertEqual(result.makespan_s, 0.010)
        self.assertEqual({row.start_s for row in result.tasks}, {0.0})
        self.assert_attribution(result)
        serial = schedule(workload, costs, topology, policy="serial")
        self.assertAlmostEqual(serial.makespan_s, 0.018)
        self.assert_attribution(serial)

    def test_dependency_chain(self):
        topology = synthetic_topology()
        workload = graph(task("g", "GEMM"), task("v", deps=("g",)), task("c", "CPU", ("v",)))
        result = schedule(workload, costs_for(workload, topology, {"g": 0.010, "v": 0.004, "c": 0.001}), topology)
        self.assertAlmostEqual(result.makespan_s, 0.015)
        self.assertAlmostEqual(by_id(result)["v"].start_s, 0.010)
        self.assertAlmostEqual(by_id(result)["c"].start_s, 0.014)
        self.assert_attribution(result)

    def test_approved_synthetic_32ms_and_24ms(self):
        workload = graph(task("G0", "GEMM"), task("G1", "GEMM"),
                         task("V0", deps=("G0", "G1")), task("G2", "GEMM", ("V0",)),
                         task("V1", deps=("G2",)), *(task("C" + str(i), "CPU", ("V1",)) for i in range(4)))
        durations = {"G0": .010, "G1": .008, "V0": .004, "G2": .006, "V1": .002,
                     **dict.fromkeys(("C" + str(i) for i in range(4)), .001)}
        for gemms, expected in ((1, .032), (2, .024)):
            topology = synthetic_topology(gemm_instances=gemms, cpu_instances=2)
            result = schedule(workload, costs_for(workload, topology, durations), topology)
            self.assertAlmostEqual(result.makespan_s, expected)
            self.assert_attribution(result)

    def test_shared_slot_serializes_and_waits_are_dataclasses(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("a"), task("b"))
        costs = costs_for(workload, topology, {"a": 3, "b": 3},
                          claims=dict.fromkeys(("a", "b"), (ResourceClaim("port.local"),)))
        result = schedule(workload, costs, topology)
        self.assertEqual(result.makespan_s, 6)
        self.assertEqual(result.resource_busy_s["core0.local"], 6)
        waiting = by_id(result)["b"].waits[0]
        self.assertTrue(is_dataclass(waiting))
        self.assertEqual(waiting.resource, "core0.local")
        self.assertEqual((waiting.start_s, waiting.end_s), (0, 3))
        self.assertTrue(is_dataclass(result.tasks[0].phases[0]))
        json.dumps(result.to_dict(), allow_nan=False)
        self.assert_attribution(result)

    def test_fixed_rate_bandwidth_capacity_not_whole_port(self):
        topology = synthetic_topology(vpu_instances=3)
        topology = replace(topology, resources={**topology.resources, "dram": Resource("dram", "bandwidth", 100)})
        workload = graph(task("a"), task("b"), task("c"))
        costs = costs_for(workload, topology, dict.fromkeys(("a", "b", "c"), 2),
                          claims=dict.fromkeys(("a", "b", "c"), (ResourceClaim("dram", 40),)))
        result = schedule(workload, costs, topology)
        self.assertEqual(result.makespan_s, 4)
        self.assertEqual(sorted(row.start_s for row in result.tasks), [0, 0, 2])
        self.assertEqual(result.resource_busy_s["dram"], 4)
        for point in sorted({phase.start_s for row in result.tasks for phase in row.phases}):
            rate = sum(40 for row in result.tasks for phase in row.phases if phase.start_s <= point < phase.end_s)
            self.assertLessEqual(rate, 100)
        self.assert_attribution(result)

    def test_duplicate_bandwidth_claims_are_summed(self):
        topology = synthetic_topology(vpu_instances=2)
        topology = replace(topology, resources={**topology.resources, "dram": Resource("dram", "bandwidth", 100)})
        workload = graph(task("a"), task("b"))
        claims = (ResourceClaim("dram", 40), ResourceClaim("dram", 40))
        result = schedule(workload, costs_for(workload, topology, {"a": 1, "b": 1},
                                              claims={"a": claims, "b": claims}), topology)
        self.assertEqual(result.makespan_s, 2)

    def test_engine_lease_covers_loadw_and_resource_waits(self):
        topology = synthetic_topology()
        workload = graph(task("a", "GEMM"), task("b", "GEMM"))
        costs = costs_for(workload, topology, {"a": 1, "b": 1})
        costs["a"]["gemm0"] = replace(costs["a"]["gemm0"], phases=(
            Phase("loadW", 3, "dram", claims=(ResourceClaim("port.local"),)),
            Phase("compute", 2, "gemm", ("loadW",), (ResourceClaim("engine"),))))
        result = schedule(workload, costs, topology)
        rows = sorted(result.tasks, key=lambda row: row.start_s)
        self.assertGreaterEqual(rows[1].start_s, rows[0].end_s)
        self.assertEqual(result.makespan_s, 6)
        self.assertEqual(result.resource_busy_s["core0.gemm0"], 6)
        self.assert_attribution(result)

    def test_phase_dag_independent_roots_overlap(self):
        topology = synthetic_topology()
        workload = graph(task("a", "GEMM"))
        cost = EngineCost("a", "gemm0", (Phase("x", 3, "dram"), Phase("y", 2, "gemm"),
                           Phase("join", 1, "handoff", ("x", "y"))))
        result = schedule(workload, {"a": {"gemm0": cost}}, topology)
        self.assertEqual(result.makespan_s, 4)
        phases = {phase.phase_id: phase for phase in result.tasks[0].phases}
        self.assertEqual(phases["x"].start_s, 0)
        self.assertEqual(phases["y"].start_s, 0)
        self.assertEqual(phases["join"].start_s, 3)
        self.assertEqual(result.attribution_s["dram"], 3)
        self.assertEqual(result.attribution_s["handoff"], 1)
        self.assert_attribution(result)

    def test_bind_earliest_finish_and_id_tie(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("a"))
        costs = costs_for(workload, topology, {"a": 3})
        costs["a"]["vpu1"] = replace(costs["a"]["vpu1"], phases=(Phase("fast", 1, "vpu"),))
        self.assertEqual(schedule(workload, costs, topology).tasks[0].instance_id, "vpu1")
        costs["a"]["vpu0"] = replace(costs["a"]["vpu0"], phases=(Phase("fast", 1, "vpu"),))
        self.assertEqual(schedule(workload, costs, topology).tasks[0].instance_id, "vpu0")

    def test_deterministic_under_input_permutation(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("z"), task("a"), task("c", "CPU", ("a", "z")))
        costs = costs_for(workload, topology, {"a": 2, "z": 2, "c": 1})
        result = schedule(workload, costs, topology)
        shuffled = replace(workload, tasks=tuple(reversed(workload.tasks)))
        reversed_costs = {key: dict(reversed(tuple(value.items()))) for key, value in reversed(tuple(costs.items()))}
        other = schedule(shuffled, reversed_costs, replace(topology, instances=dict(reversed(tuple(topology.instances.items())))))
        self.assertEqual(by_id(result), by_id(other))
        self.assertEqual(result.attribution_s, other.attribution_s)
        self.assert_attribution(result)

    def test_known_resource_prefix_before_unknown_service(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("a"), task("b", allowed_instances=("vpu1",)),
                         task("c", "CPU", ("a",)), task("independent", "GEMM"))
        costs = costs_for(workload, topology, {"a": None, "b": 2, "c": 1, "independent": 4},
                          claims={"b": (ResourceClaim("port.local"),)})
        costs["a"]["vpu0"] = EngineCost("a", "vpu0", (
            Phase("known", 1, "vpu"), Phase("unknown", None, "vpu", ("known",),
                (ResourceClaim("port.local"),), known_seconds=.5),
            Phase("after", 1, "vpu", ("unknown",))))
        costs["a"].pop("vpu1")
        result = schedule(workload, costs, topology)
        rows = by_id(result)
        self.assertIsNone(result.makespan_s)
        self.assertIsNotNone(result.projection_makespan_s)
        self.assertIsNone(rows["a"].end_s)
        self.assertIsNone(rows["c"].start_s)
        self.assertEqual(rows["independent"].end_s, 4)
        phases = {phase.phase_id: phase for phase in rows["a"].phases}
        self.assertEqual(phases["known"].end_s, 1)
        self.assertIsNone(phases["unknown"].start_s)
        self.assertIsNone(phases["unknown"].end_s)
        self.assertEqual(phases["unknown"].projection_end_s - phases["unknown"].projection_start_s, .5)
        self.assertIsNone(phases["after"].start_s)
        self.assertEqual(result.projection_kind, "conditional_budget_nonactual")
        self.assertTrue(all(value is None for value in result.attribution_s.values()))
        self.assertIsNone(result.resource_busy_s["core0.vpu0"])

    def test_unknown_occupation_blocks_non_descendant_competitor(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("a", allowed_instances=("vpu0",)), task("b", allowed_instances=("vpu1",)))
        costs = costs_for(workload, topology, {"a": None, "b": 2},
                          claims=dict.fromkeys(("a", "b"), (ResourceClaim("port.local"),)))
        result = schedule(workload, costs, topology)
        self.assertIsNone(by_id(result)["b"].start_s)
        self.assertEqual(by_id(result)["b"].projection_end_s, 2)
        self.assertIsNone(result.resource_busy_s["core0.local"])
        self.assertTrue(any("unknown occupation" in reason for reason in result.blocked_constraints))

    def test_unknown_whole_task_lease_blocks_sibling(self):
        topology = synthetic_topology()
        workload = graph(task("a"), task("b"))
        result = schedule(workload, costs_for(workload, topology, {"a": None, "b": 2}), topology)
        self.assertIsNone(by_id(result)["b"].start_s)
        self.assertEqual(by_id(result)["a"].projection_end_s, 0)
        self.assertIsNone(by_id(result)["a"].end_s)

    def test_temporal_private_sram_capacity_and_consumer_lifetime(self):
        topology = synthetic_topology(vpu_instances=2)
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 64)})
        value = Tensor("value", (9,), "FP32", "core0.sram")
        workload = graph(task("produce", outputs=(TensorRef("value"),)),
                         task("consume", "CPU", ("produce",), inputs=(TensorRef("value"),)),
                         task("other"), tensors=(value,))
        costs = costs_for(workload, topology, {"produce": 1, "consume": 3, "other": 2},
                          buffers={"other": (BufferRequest("scratch", "core0.sram", 1, 32),)})
        result = schedule(workload, costs, topology)
        rows = by_id(result)
        self.assertEqual(rows["produce"].start_s, 0)
        self.assertEqual(rows["consume"].end_s, 4)
        self.assertEqual(rows["other"].start_s, 4)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 64)
        self.assertEqual(result.makespan_s, 6)
        self.assert_attribution(result)

    def test_view_and_engine_tensor_buffer_deduplicate_base(self):
        topology = synthetic_topology()
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 64)})
        base = Tensor("base", (9,), "FP32", "core0.sram")
        view = Tensor("view", (4,), "FP32", "core0.sram", base_id="base")
        workload = graph(task("p", outputs=(TensorRef("base"),)),
                         task("c", "CPU", ("p",), inputs=(TensorRef("view"),)), tensors=(base, view))
        costs = costs_for(workload, topology, {"p": 1, "c": 1},
                          buffers={"p": (BufferRequest("output", "core0.sram", 36, 32, tensor_id="base"),),
                                   "c": (BufferRequest("input", "core0.sram", 16, 32, tensor_id="view"),)})
        result = schedule(workload, costs, topology)
        self.assertEqual(result.makespan_s, 2)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 64)

    def test_engine_phase_buffer_temporal_reuse(self):
        topology = synthetic_topology()
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 32)})
        workload = graph(task("a"))
        cost = EngineCost("a", "vpu0", (Phase("first", 1, "vpu"), Phase("second", 1, "vpu", ("first",))),
                          buffers=(BufferRequest("x", "core0.sram", 1, 32, "first", "first"),
                                   BufferRequest("y", "core0.sram", 1, 32, "second", "second")))
        result = schedule(workload, {"a": {"vpu0": cost}}, topology)
        self.assertEqual(result.makespan_s, 2)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 32)

    def test_fixed_regions_serialize_even_if_total_capacity_fits(self):
        topology = synthetic_topology(vpu_instances=2)
        workload = graph(task("a"), task("b"))
        plan = BufferPlan(tuple(BufferAllocation(key, "core0.sram", 32, BufferEndpoint(key),
                                (BufferEndpoint(key),), offset=0, region="scratch") for key in ("a", "b")))
        result = schedule(workload, costs_for(workload, topology, {"a": 2, "b": 2}), topology, plan)
        self.assertEqual(result.makespan_s, 4)
        self.assert_attribution(result)

    def test_unknown_lifetime_blocks_memory_competitor_not_graph_descendant(self):
        topology = synthetic_topology(vpu_instances=2)
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 32)})
        value = Tensor("value", (8,), "FP32", "core0.sram")
        workload = graph(task("a", outputs=(TensorRef("value"),)),
                         task("c", "CPU", ("a",), inputs=(TensorRef("value"),)), task("b"), tensors=(value,))
        costs = costs_for(workload, topology, {"a": 0, "c": None, "b": 2},
                          buffers={"b": (BufferRequest("scratch", "core0.sram", 32),)})
        result = schedule(workload, costs, topology)
        self.assertIsNotNone(result.projection_makespan_s)
        self.assertIsNone(by_id(result)["b"].start_s)
        self.assertIsNone(result.memory_peak_bytes["core0.sram"])
        self.assertEqual(result.projection_memory_peak_bytes["core0.sram"], 32)

    def test_unknown_required_sram_capacity_blocks_projection(self):
        topology = synthetic_topology()
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", None)})
        workload = graph(task("a"))
        result = schedule(workload, costs_for(workload, topology, {"a": 1},
            buffers={"a": (BufferRequest("scratch", "core0.sram", 32),)}), topology)
        self.assertIsNone(result.projection_makespan_s)
        self.assertIsNone(result.tasks[0].projection_start_s)
        self.assertIsNone(result.memory_peak_bytes["core0.sram"])
        self.assertIsNone(result.projection_memory_peak_bytes["core0.sram"])
        self.assertTrue(any("required SRAM capacity is unknown" in reason for reason in result.blocked_constraints))
        self.assertEqual(result.tasks[0].status, "blocked")

    def test_unspecified_dram_capacity_does_not_block_external_storage(self):
        topology = synthetic_topology()
        value = Tensor("external", (1000000,), "FP32", "dram", external=True)
        workload = graph(task("a", inputs=(TensorRef("external"),)), tensors=(value,))
        result = schedule(workload, costs_for(workload, topology, {"a": 1}), topology)
        self.assertEqual(result.makespan_s, 1)
        self.assertEqual(result.memory_peak_bytes["dram"], 4000000)

    def test_private_access_blocked_but_explicit_dma_copy_runs(self):
        topology = synthetic_topology(vpu_instances=2, active_cores=2)
        source = Tensor("source", (8,), "FP32", "core0.sram", external=True)
        dest = Tensor("dest", (8,), "FP32", "core1.sram")
        workload = graph(task("consume", inputs=(TensorRef("source"),), allowed_instances=("vpu1",)), tensors=(source,))
        result = schedule(workload, costs_for(workload, topology, {"consume": 1}), topology)
        self.assertIsNone(result.makespan_s)
        self.assertTrue(any("explicit DMA" in reason for reason in result.blocked_constraints))
        copy = PrimitiveTask("copy", "op", "DMA", TransferSpec("copy", "core0.sram", "core1.sram", 32),
                             inputs=(TensorRef("source"),), outputs=(TensorRef("dest"),))
        workload = graph(copy, task("consume", deps=("copy",), inputs=(TensorRef("dest"),),
                         allowed_instances=("vpu1",)), tensors=(source, dest))
        result = schedule(workload, costs_for(workload, topology, {"copy": 1, "consume": 2}), topology)
        self.assertEqual(result.makespan_s, 3)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 32)
        self.assertEqual(result.memory_peak_bytes["core1.sram"], 32)

    def test_private_engine_scratch_is_not_accessible_on_other_core(self):
        topology = synthetic_topology(vpu_instances=2, active_cores=2)
        workload = graph(task("a", allowed_instances=("vpu1",)))
        result = schedule(workload, costs_for(workload, topology, {"a": 1},
            buffers={"a": (BufferRequest("scratch", "core0.sram", 32),)}), topology)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("private buffer" in reason for reason in result.blocked_constraints))

    def test_oversized_buffer_and_dependent_task_return_without_loops(self):
        topology = synthetic_topology()
        workload = graph(task("a"), task("b", "CPU", ("a",)))
        costs = costs_for(workload, topology, {"a": 1, "b": 1},
                          buffers={"a": (BufferRequest("huge", "core0.sram", 10**9),)})
        result = schedule(workload, costs, topology)
        self.assertEqual(len(result.tasks), 2)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(all(row.projection_end_s is None for row in result.tasks))
        self.assertTrue(all("current fixed-plan" in reason for reason in result.blocked_constraints))

    def test_unknown_allocation_size_blocks_even_projection(self):
        topology = synthetic_topology()
        workload = graph(task("a"))
        plan = BufferPlan((BufferAllocation("unknown", "core0.sram", None, BufferEndpoint("a"),
                                           (BufferEndpoint("a"),)),))
        result = schedule(workload, costs_for(workload, topology, {"a": 1}), topology, plan)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("size is unknown" in reason for reason in result.blocked_constraints))

    def test_unassigned_task_kept_and_invalid_input_rejected(self):
        topology = synthetic_topology(cpu_instances=0)
        workload = graph(task("a", "CPU"), task("b", deps=("a",)))
        result = schedule(workload, {}, topology)
        self.assertEqual(len(result.tasks), 2)
        self.assertIsNone(result.tasks[0].instance_id)
        self.assertEqual(result.tasks[0].status, "unassigned")
        self.assertIsNone(result.makespan_s)
        with self.assertRaisesRegex(ValueError, "policy"):
            schedule(workload, {}, topology, policy="other")
        with self.assertRaisesRegex(ValueError, "registry keys"):
            schedule(graph(task("x")), {"x": {"vpu0": EngineCost("wrong", "vpu0", ())}}, topology)

    def test_external_tensor_staging_is_separate_private_allocation(self):
        topology = synthetic_topology()
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 32)})
        source = Tensor("source", (64,), "FP32", "dram", external=True)
        workload = graph(task("a", inputs=(TensorRef("source"),)), tensors=(source,))
        costs = costs_for(workload, topology, {"a": 2}, buffers={"a": (
            BufferRequest("stage-window", "core0.sram", 8, 32, tensor_id="source"),)})
        result = schedule(workload, costs, topology)
        self.assertEqual(result.makespan_s, 2)
        self.assertEqual(result.memory_peak_bytes["dram"], 256)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 32)

    def test_unknown_bandwidth_only_blocks_capacity_competitors(self):
        topology = synthetic_topology(vpu_instances=2)
        topology = replace(topology, resources={**topology.resources, "dram": Resource("dram", "bandwidth", 100)})
        workload = graph(task("a", allowed_instances=("vpu0",)), task("b", allowed_instances=("vpu1",)))
        costs = costs_for(workload, topology, {"a": None, "b": 2},
                          claims=dict.fromkeys(("a", "b"), (ResourceClaim("dram", 40),)))
        result = schedule(workload, costs, topology)
        self.assertIsNone(by_id(result)["a"].end_s)
        self.assertEqual(by_id(result)["b"].end_s, 2)
        self.assertIsNone(result.resource_busy_s["dram"])

    def test_future_conflict_advances_to_release_endpoint(self):
        topology = synthetic_topology()
        workload = graph(task("seed", "CPU"), task("a", deps=("seed",)), task("b", "GEMM"))
        costs = costs_for(workload, topology, {"seed": 3, "a": 2, "b": 10},
                          claims={"a": (ResourceClaim("port.local"),), "b": (ResourceClaim("port.local"),)})
        result = schedule(workload, costs, topology)
        self.assertEqual(by_id(result)["a"].start_s, 3)
        self.assertEqual(by_id(result)["b"].start_s, 5)
        self.assertEqual(result.makespan_s, 15)
        self.assertEqual(result.attribution_s["cpu"], 3)
        self.assertEqual(result.attribution_s["vpu"], 2)
        self.assertEqual(result.attribution_s["gemm"], 10)
        self.assert_attribution(result)

    def test_private_capacity_cannot_borrow_another_cores_sram(self):
        topology = synthetic_topology(vpu_instances=2, active_cores=2)
        domains = {**topology.memory_domains,
                   **{core + ".sram": MemoryDomain(core + ".sram", core, 32) for core in topology.active_cores}}
        topology = replace(topology, memory_domains=domains)
        workload = graph(task("a", allowed_instances=("vpu0",)))
        result = schedule(workload, costs_for(workload, topology, {"a": 1},
                          buffers={"a": (BufferRequest("scratch", "core0.sram", 48, 16),)}), topology)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("exceeds fixed capacity" in reason for reason in result.blocked_constraints))

    def test_non_tensor_global_allocation_cannot_cross_private_domains(self):
        topology = synthetic_topology(vpu_instances=2, active_cores=2)
        workload = graph(task("a", allowed_instances=("vpu1",)))
        plan = BufferPlan((BufferAllocation("scratch", "core0.sram", 32, BufferEndpoint("a"),
                                           (BufferEndpoint("a"),)),))
        result = schedule(workload, costs_for(workload, topology, {"a": 1}), topology, plan)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("private allocation" in reason for reason in result.blocked_constraints))

    def test_reversed_phase_buffer_lifetime_is_blocked(self):
        topology = synthetic_topology()
        workload = graph(task("a"))
        cost = EngineCost("a", "vpu0", (Phase("first", 1, "vpu"), Phase("second", 1, "vpu", ("first",))),
                          buffers=(BufferRequest("bad", "core0.sram", 32, 32, "second", "first"),))
        # Equal endpoints are zero-sized intervals, so use a third later phase.
        cost = replace(cost, phases=(*cost.phases, Phase("third", 1, "vpu", ("second",))),
                       buffers=(replace(cost.buffers[0], acquire_phase="third"),))
        result = schedule(workload, {"a": {"vpu0": cost}}, topology)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("release precedes acquisition" in reason for reason in result.blocked_constraints))

    def test_same_base_alignment_combines_all_engine_requirements(self):
        topology = synthetic_topology()
        value = Tensor("value", (9,), "FP32", "core0.sram")
        workload = graph(task("a", outputs=(TensorRef("value"),)), tensors=(value,))
        buffers = (BufferRequest("one", "core0.sram", 36, 3, tensor_id="value"),
                   BufferRequest("two", "core0.sram", 36, 5, tensor_id="value"))
        result = schedule(workload, costs_for(workload, topology, {"a": 1}, buffers={"a": buffers}), topology)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 480)  # lcm(32, 3, 5)
        self.assertEqual(result.makespan_s, 1)

    def test_fixed_base_offset_must_meet_engine_alignment(self):
        topology = synthetic_topology()
        value = Tensor("value", (8,), "FP32", "core0.sram")
        workload = graph(task("a", outputs=(TensorRef("value"),)), tensors=(value,))
        plan = BufferPlan((BufferAllocation("value", "core0.sram", 32, BufferEndpoint("a"),
                            (BufferEndpoint("a"),), tensor_id="value", offset=32),))
        costs = costs_for(workload, topology, {"a": 1},
                          buffers={"a": (BufferRequest("output", "core0.sram", 32, 64, tensor_id="value"),)})
        result = schedule(workload, costs, topology, plan)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("violates engine buffer alignment" in reason for reason in result.blocked_constraints))

    def test_global_engine_buffer_id_is_one_physical_allocation(self):
        topology = synthetic_topology()
        topology = replace(topology, memory_domains={**topology.memory_domains,
                           "core0.sram": MemoryDomain("core0.sram", "core0", 32)})
        workload = graph(task("a"))
        plan = BufferPlan((BufferAllocation("scratch", "core0.sram", 32, BufferEndpoint("a"),
                                           (BufferEndpoint("a"),)),))
        costs = costs_for(workload, topology, {"a": 1},
                          buffers={"a": (BufferRequest("scratch", "core0.sram", 32),)})
        result = schedule(workload, costs, topology, plan)
        self.assertEqual(result.makespan_s, 1)
        self.assertEqual(result.memory_peak_bytes["core0.sram"], 32)

    def test_global_allocation_must_cover_named_engine_lifecycle(self):
        topology = synthetic_topology()
        workload = graph(task("a"))
        cost = EngineCost("a", "vpu0", (Phase("first", 1, "vpu"), Phase("second", 1, "vpu", ("first",))),
                          buffers=(BufferRequest("scratch", "core0.sram", 32),))
        plan = BufferPlan((BufferAllocation("scratch", "core0.sram", 32, BufferEndpoint("a", "second"),
                                           (BufferEndpoint("a"),)),))
        result = schedule(workload, {"a": {"vpu0": cost}}, topology, plan)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("does not cover engine buffer acquisition" in reason for reason in result.blocked_constraints))

    def test_serial_blocked_task_retains_all_rows(self):
        topology = synthetic_topology()
        workload = graph(task("a"), task("b", "CPU"))
        costs = costs_for(workload, topology, {"a": 1, "b": 1},
                          buffers={"a": (BufferRequest("huge", "core0.sram", 10**9),)})
        result = schedule(workload, costs, topology, policy="serial")
        self.assertEqual(len(result.tasks), 2)
        self.assertTrue(all(row.projection_start_s is None for row in result.tasks))
        self.assertTrue(any("serial predecessor" in reason for reason in result.blocked_constraints))

    def test_overcapacity_claim_blocked_and_empty_graph_zero(self):
        topology = synthetic_topology()
        workload = graph(task("a"))
        result = schedule(workload, costs_for(workload, topology, {"a": 1},
                                              claims={"a": (ResourceClaim("port.local", 2),)}), topology)
        self.assertIsNone(result.projection_makespan_s)
        self.assertTrue(any("exceeds physical capacity" in reason for reason in result.blocked_constraints))
        empty = schedule(graph(), {}, topology)
        self.assertEqual(empty.makespan_s, 0)
        self.assert_attribution(empty)


if __name__ == "__main__":
    unittest.main()
