"""DMA endpoint ownership, serial service and allocation-only alignment."""

import unittest
from dataclasses import replace

from hive.model_eval.common.opspec import PrimitiveTask, TensorRef, TransferSpec, VpuSpec
from hive.model_eval.hw.geometry import load_array_geometry
from hive.model_eval.hw.profiles import G0, MODEL_ANALYSIS_310
from hive.model_eval.hw.topology import default_topology, explicit_topology
from hive.model_eval.l1 import dma_model, dram
from hive.model_eval.l1.ledger import fold
from hive.model_eval.l1.params import ModelParams
from hive.model_eval.l1.residency import ExecutionPlan


GEOMETRY = load_array_geometry()


def transfer(source, destination, size=65, *, direction="copy", tensor=None):
    spec = TransferSpec("test:stream", source, destination, size, tensor=tensor, direction=direction)
    return PrimitiveTask("test:dma", "test:op", "DMA", spec)


def estimate(task, profile=G0, *, alignment=32, params=None, config=None):
    config = config or default_topology(profile, GEOMETRY, params or ModelParams()).instances["dma0"]
    return dma_model.estimate(task, config, ExecutionPlan(activation_policy="stream", alignment=alignment))


class TestDmaEndpoints(unittest.TestCase):
    def test_endpoint_matrix_charges_only_actual_domain_reads_and_writes(self):
        for source, destination in (("dram", "core0.sram"), ("core0.sram", "dram"),
                                    ("dram", "dram"), ("core0.sram", "core0.sram")):
            with self.subTest(source=source, destination=destination):
                task = transfer(source, destination)
                cost = estimate(task)
                source_dram, dest_dram = source == "dram", destination == "dram"
                local_bytes = 65 * (int(not source_dram) + int(not dest_dram))
                self.assertEqual(cost.metrics["local_bytes"], local_bytes)
                self.assertEqual(cost.metrics["dram_read_bytes"], 65 if source_dram else 0)
                self.assertEqual(cost.metrics["dram_write_bytes"], 65 if dest_dram else 0)
                self.assertEqual(cost.metrics["physical_bytes"], 65)
                self.assertTrue(cost.complete)
                expected = dram.seconds(65 * (int(source_dram) + int(dest_dram)), G0)
                expected += dram.local_seconds(local_bytes, G0)
                self.assertAlmostEqual(cost.known_service_s, expected, delta=expected * 1e-12)
                records = {t.direction: t for t in cost.traffic}
                self.assertEqual(set(records), {"read", "write"})
                self.assertEqual(records["read"].memory_domain, source)
                self.assertEqual(records["write"].memory_domain, destination)
                for domain, record in ((source, records["read"]), (destination, records["write"])):
                    self.assertEqual(record.interface, "dram" if domain == "dram" else "core0.local")
                    self.assertEqual(record.logical_bytes, 65)
                    self.assertEqual(record.physical_bytes, 65)
                self.assertEqual(len(cost.buffers), int(not source_dram) + int(not dest_dram))

    def test_dram_to_dram_charges_both_serial_external_transactions(self):
        task = transfer("dram", "dram", size=129)
        cost = estimate(task)
        self.assertEqual([p.id for p in cost.phases], ["test:dma:source_read", "test:dma:destination_write"])
        self.assertEqual(cost.phases[0].dependencies, ())
        self.assertEqual(cost.phases[1].dependencies, (cost.phases[0].id,))
        self.assertTrue(all(p.category == "dram" for p in cost.phases))
        self.assertEqual(sum(c.bytes for c in cost.ledger.charges), 258)
        self.assertEqual(cost.known_service_s, 2 * dram.seconds(129, G0))
        self.assertEqual(sum(t.physical_bytes for t in cost.traffic), 258)
        self.assertEqual(len(fold(cost.ledger)), 2)
        self.assertFalse(cost.buffers)

    def test_private_to_private_combines_reads_and_writes_on_single_local_port(self):
        cost = estimate(transfer("core0.sram", "core0.sram", size=33))
        self.assertEqual(len(cost.phases), 1)
        self.assertEqual(cost.phases[0].category, "handoff")
        self.assertEqual(cost.phases[0].claims[0].resource, "core0.local")
        self.assertEqual(cost.ledger.charges[0].resource, "local_port")
        self.assertEqual(cost.ledger.charges[0].bytes, 66)
        self.assertEqual(cost.known_service_s, 66 / G0.local_iface_bytes_per_s_per_core)
        self.assertFalse(any(t.memory_domain == "dram" for t in cost.traffic))
        self.assertEqual(cost.metrics["dram_read_bytes"], 0)
        self.assertEqual(cost.metrics["dram_write_bytes"], 0)

    def test_ingress_before_local_and_local_before_egress_are_not_folded_max(self):
        for source, destination, names in (("dram", "core0.sram", ("source_read", "local_read_write")),
                                          ("core0.sram", "dram", ("local_read_write", "destination_write"))):
            with self.subTest(source=source, destination=destination):
                cost = estimate(transfer(source, destination))
                self.assertEqual([p.id.rsplit(":", 1)[-1] for p in cost.phases], list(names))
                self.assertEqual(cost.phases[1].dependencies, (cost.phases[0].id,))
                self.assertEqual(cost.known_service_s, sum(p.seconds for p in cost.phases))
                self.assertGreater(cost.known_service_s, max(p.seconds for p in cost.phases))
                self.assertEqual(len(fold(cost.ledger)), len(cost.phases))
                self.assertFalse(cost.ledger.coupling)

    def test_one_stream_owner_is_shared_across_interface_records_not_service_origins(self):
        cost = estimate(transfer("dram", "core0.sram"))
        self.assertEqual({t.stream_id for t in cost.traffic}, {"test:stream"})
        self.assertEqual({t.owner_task_id for t in cost.traffic}, {cost.task_id})
        self.assertEqual({t.interface for t in cost.traffic}, {"dram", "core0.local"})
        self.assertEqual(len({c.origin.key() for c in cost.ledger.charges}), 2)
        self.assertTrue(all(c.origin.producer == cost.task_id for c in cost.ledger.charges))
        self.assertTrue(all(c.overlap_class == "serial" for c in cost.ledger.charges))

    def test_declared_direction_does_not_suppress_actual_destination_or_source_traffic(self):
        for direction in ("read", "write", "copy"):
            with self.subTest(direction=direction):
                cost = estimate(transfer("dram", "dram", direction=direction))
                self.assertEqual(cost.metrics["dram_read_bytes"], 65)
                self.assertEqual(cost.metrics["dram_write_bytes"], 65)
                self.assertEqual({t.direction for t in cost.traffic}, {"read", "write"})
                self.assertEqual(cost.known_service_s, 2 * dram.seconds(65, G0))

    def test_tensor_identity_is_preserved_on_source_and_destination_endpoints(self):
        task = transfer("dram", "core0.sram", tensor=TensorRef("source"))
        task = replace(task, inputs=(TensorRef("source"),), outputs=(TensorRef("dest"),))
        cost = estimate(task)
        self.assertEqual({t.direction: t.tensor_id for t in cost.traffic}, {"read": "source", "write": "dest"})
        self.assertEqual(cost.buffers[0].tensor_id, "dest")

    def test_zero_bytes_is_explicit_noop_without_invented_traffic_or_scratch(self):
        for source, destination in (("dram", "dram"), ("dram", "core0.sram"),
                                    ("core0.sram", "core0.sram")):
            with self.subTest(source=source, destination=destination):
                cost = estimate(transfer(source, destination, size=0))
                self.assertTrue(cost.complete)
                self.assertEqual(cost.known_service_s, 0)
                self.assertEqual(len(cost.phases), 1)
                self.assertTrue(cost.phases[0].id.endswith(":empty_transfer"))
                self.assertFalse(cost.traffic)
                self.assertFalse(cost.buffers)

    def test_vpu_overlap_knob_cannot_overlap_dma_interfaces(self):
        params = ModelParams(overlap=True, overlap_evidence="VPU double-buffer scenario")
        base = estimate(transfer("dram", "core0.sram"))
        cost = estimate(transfer("dram", "core0.sram"), params=params)
        self.assertEqual(base, cost)

    def test_cross_core_copy_reserves_both_endpoint_ports_and_keeps_route_unknown(self):
        topology = explicit_topology(G0, GEOMETRY, ModelParams(), dma_instances=2,
                                     active_cores=2, evidence="synthetic cross-core DMA")
        for source, destination in (("core0.sram", "core1.sram"),
                                    ("core1.sram", "dram"), ("dram", "core1.sram")):
            with self.subTest(source=source, destination=destination):
                task = transfer(source, destination)
                cost = estimate(task, config=topology.instances["dma0"])
                ports = {claim.resource for phase in cost.phases for claim in phase.claims}
                local_domains = {d for d in (source, destination) if d != "dram"}
                self.assertTrue({d.replace(".sram", ".local") for d in local_domains} <= ports)
                self.assertFalse(cost.complete)
                route = next(p for p in cost.phases if p.id.endswith("cross_domain_route"))
                self.assertIsNone(route.seconds)
                self.assertEqual({t.interface for t in cost.traffic},
                                 {"dram" if d == "dram" else d.replace(".sram", ".local")
                                  for d in (source, destination)})
                self.assertEqual({t.stream_id for t in cost.traffic}, {task.spec.stream_id})
                self.assertEqual({b.memory_domain for b in cost.buffers}, local_domains)


class TestDmaBuffersAndUnknowns(unittest.TestCase):
    def test_padding_changes_allocated_bytes_only_not_chunks_or_endpoint_service(self):
        for source, destination in (("dram", "core0.sram"), ("core0.sram", "dram"),
                                    ("core0.sram", "core0.sram")):
            with self.subTest(source=source, destination=destination):
                task = transfer(source, destination, size=129)
                base, padded = estimate(task), estimate(task, alignment=128)
                self.assertEqual(base.traffic, padded.traffic)
                self.assertEqual(base.phases, padded.phases)
                self.assertEqual(base.ledger.to_dict(), padded.ledger.to_dict())
                self.assertEqual([b.size_bytes for b in base.buffers], [b.size_bytes for b in padded.buffers])
                self.assertTrue(all(b.size_bytes == 32 and b.allocated_bytes == 128 for b in padded.buffers))
                self.assertTrue(all(b.allocated_bytes == 32 for b in base.buffers))
                self.assertEqual(base.metrics, padded.metrics)

    def test_sub_chunk_transfer_keeps_exact_byte_size_and_allocation_padding(self):
        cost = estimate(transfer("dram", "core0.sram", size=17), alignment=64)
        self.assertEqual(cost.buffers[0].size_bytes, 17)
        self.assertEqual(cost.buffers[0].allocated_bytes, 64)
        self.assertTrue(all(t.physical_bytes == 17 for t in cost.traffic))
        self.assertTrue(all(c.bytes == 17 for c in cost.ledger.charges))

    def test_staging_chunks_have_private_domains_and_explicit_acquire_release_ranges(self):
        for source, destination in (("dram", "core0.sram"), ("core0.sram", "dram"),
                                    ("core0.sram", "core0.sram")):
            with self.subTest(source=source, destination=destination):
                cost = estimate(transfer(source, destination))
                ids = {p.id for p in cost.phases}
                for buffer in cost.buffers:
                    self.assertEqual(buffer.memory_domain, "core0.sram")
                    self.assertIn(buffer.acquire_phase, ids)
                    self.assertIn(buffer.release_phase, ids)
                    self.assertEqual(buffer.acquire_phase, cost.phases[0].id)
                    self.assertEqual(buffer.release_phase, cost.phases[-1].id)
                    self.assertEqual(buffer.size_bytes, 32)
                self.assertEqual(len({b.id for b in cost.buffers}), len(cost.buffers))
                self.assertEqual(cost.lease_resources, ("core0.dma0",))
                self.assertTrue(any("complete tensor residency is not inferred" in item for item in cost.excluded_costs))

    def test_unknown_local_service_is_none_and_does_not_erase_known_external_service(self):
        for source, destination in (("dram", "core0.sram"), ("core0.sram", "dram")):
            with self.subTest(source=source, destination=destination):
                cost = estimate(transfer(source, destination), MODEL_ANALYSIS_310)
                local = next(p for p in cost.phases if p.category == "handoff")
                self.assertIsNone(local.seconds)
                self.assertFalse(cost.complete)
                self.assertEqual(cost.known_service_s, 65 / 310e9)
                self.assertEqual(len(cost.traffic), 2)
                self.assertTrue(any("local_iface_bytes_per_s_per_core is unknown" in item
                                    for item in cost.excluded_costs))
                self.assertTrue(any(c.seconds is None for c in fold(cost.ledger)))

    def test_all_unknown_private_copy_is_not_reported_complete_zero_cost(self):
        cost = estimate(transfer("core0.sram", "core0.sram"), MODEL_ANALYSIS_310)
        self.assertIsNone(cost.phases[0].seconds)
        self.assertIsNone(cost.ledger.charges[0].seconds)
        self.assertFalse(cost.complete)
        self.assertEqual(cost.ledger.charges[0].bytes, 130)
        self.assertTrue(any("unknown service" in item for item in cost.excluded_costs))

    def test_missing_unused_local_alias_does_not_block_external_to_external_copy(self):
        config = default_topology(G0, GEOMETRY, ModelParams()).instances["dma0"]
        config = replace(config, resources={k: v for k, v in config.resources.items() if k != "port.local"})
        cost = estimate(transfer("dram", "dram"), config=config)
        self.assertTrue(cost.complete)
        with self.assertRaises(KeyError):
            estimate(transfer("dram", "core0.sram"), config=config)

    def test_dma_requires_typed_dma_task_and_dma_instance(self):
        topology = default_topology(G0, GEOMETRY, ModelParams())
        plan = ExecutionPlan(activation_policy="stream")
        with self.assertRaises(ValueError):
            dma_model.estimate(transfer("dram", "dram"), topology.instances["vpu0"], plan)
        with self.assertRaises(ValueError):
            dma_model.estimate(PrimitiveTask("vpu", "op", "VPU", VpuSpec("residual")),
                               topology.instances["dma0"], plan)


if __name__ == "__main__":
    unittest.main()
