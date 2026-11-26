import { test } from "node:test";
import assert from "node:assert/strict";
import {
  normalize, ensureV2, renameObjectIn, removeNodeIn, removeRouterIn,
  samRows, autoLayout, computeLayout, kindAllowed, parseAddress,
  routerAdjacency, hopCount, pairKey, isRequest, isSubordinate
} from "../model.mjs";

function baseIr() {
  return {
    schema: "loom/v2",
    meta: { name: "t" },
    clockDomains: [{ id: "core", freqMHz: 1000 }],
    chi: {},
    nodes: [
      { id: "m0", kind: "rn-i", clock: "core", attach: "r0" },
      { id: "s0", kind: "sn-f", clock: "core", attach: "r0" }
    ],
    fabric: {
      routers: [{ name: "r0", clock: "core" }],
      links: []
    },
    partitions: []
  };
}

test("normalize generates RN->router and SN->router attachment edges", () => {
  const ir = baseIr();
  normalize(ir);
  const keys = ir.fabric.links.map((l) => `${l.from}>${l.to}`).sort();
  assert.deepEqual(keys, ["m0>r0", "r0>s0"]);
  assert.ok(ir.fabric.links.every((l) => l.cdc === "sync"));
});

test("normalize exempts hn-i/mn from attachment edges (GUI-OQ-05)", () => {
  const ir = baseIr();
  ir.chi.dvmEnable = true;
  ir.nodes.push({ id: "mn0", kind: "mn", clock: "core", attach: "r0" });
  ir.nodes.push({ id: "hn0", kind: "hn-i", clock: "core", attach: "r0" });
  normalize(ir);
  const keys = ir.fabric.links.map((l) => `${l.from}>${l.to}`);
  assert.ok(!keys.some((k) => k.includes("mn0")));
  assert.ok(!keys.some((k) => k.includes("hn0")));
});

test("normalize is idempotent and dedups links", () => {
  const ir = baseIr();
  normalize(ir);
  const once = JSON.stringify(ir.fabric.links);
  normalize(ir);
  assert.equal(JSON.stringify(ir.fabric.links), once);
});

test("normalize cascades node clock from attach router", () => {
  const ir = baseIr();
  ir.fabric.routers[0].clock = "core";
  ir.nodes[0].clock = "stale";
  normalize(ir);
  assert.equal(ir.nodes[0].clock, "core");
});

test("renameObjectIn cascades across attach/links/sam/partitions", () => {
  const ir = baseIr();
  ir.sam = [{ base: 0, size: 4096, tgtNode: "s0" }];
  ir.partitions = [{ name: "p0", clock: "core", members: ["r0", "m0", "s0"] }];
  normalize(ir);
  renameObjectIn(ir, "r0", "rx");
  assert.equal(ir.fabric.routers[0].name, "rx");
  assert.ok(ir.nodes.every((n) => n.attach === "rx"));
  assert.ok(ir.fabric.links.every((l) => l.from !== "r0" && l.to !== "r0"));
  assert.deepEqual(ir.partitions[0].members, ["rx", "m0", "s0"]);
  renameObjectIn(ir, "s0", "sx");
  assert.equal(ir.sam[0].tgtNode, "sx");
  assert.ok(ir.nodes.some((n) => n.id === "sx"));
});

test("removeNodeIn drops node, its links, sam entries and membership", () => {
  const ir = baseIr();
  ir.sam = [{ base: 0, size: 4096, tgtNode: "s0" }];
  ir.partitions = [{ name: "p0", clock: "core", members: ["r0", "m0", "s0"] }];
  normalize(ir);
  removeNodeIn(ir, "s0");
  assert.ok(!ir.nodes.some((n) => n.id === "s0"));
  assert.ok(!ir.fabric.links.some((l) => l.from === "s0" || l.to === "s0"));
  assert.equal(ir.sam.length, 0);
  assert.deepEqual(ir.partitions[0].members, ["r0", "m0"]);
});

test("removeRouterIn cascades attached nodes", () => {
  const ir = baseIr();
  normalize(ir);
  removeRouterIn(ir, "r0");
  assert.equal(ir.fabric.routers.length, 0);
  assert.equal(ir.nodes.length, 0);
  assert.equal(ir.fabric.links.length, 0);
});

test("samRows prefers explicit ir.sam, else derived preview", () => {
  const ir = baseIr();
  const derived = { sam: [{ base: 0, size: 4096, tgtNode: "s0" }] };
  assert.deepEqual(samRows(ir, derived), [{ base: 0, size: 4096, tgtNode: "s0" }]);
  ir.sam = [{ base: 8192, size: 4096, tgtNode: "s0" }];
  assert.equal(samRows(ir, derived)[0].base, 8192);
  assert.deepEqual(samRows(ir, null), ir.sam);
});

test("kindAllowed enforces CHK-19 (dvm + single mn)", () => {
  const ir = baseIr();
  assert.equal(kindAllowed(ir, "rn-d", null, false).allowed, false);
  assert.equal(kindAllowed(ir, "mn", null, false).allowed, false);
  assert.equal(kindAllowed(ir, "rn-i", null, false).allowed, true);
  assert.equal(kindAllowed(ir, "mn", null, true).allowed, true);
  ir.nodes.push({ id: "mn0", kind: "mn", clock: "core", attach: "r0" });
  assert.equal(kindAllowed(ir, "mn", null, true).allowed, false);
  assert.equal(kindAllowed(ir, "mn", "mn0", true).allowed, true);
});

test("parseAddress handles decimal/hex/K/M/G", () => {
  assert.equal(parseAddress("4096"), 4096);
  assert.equal(parseAddress("0x1000"), 4096);
  assert.equal(parseAddress("64K"), 65536);
  assert.equal(parseAddress("2M"), 2097152);
  assert.equal(parseAddress("1G"), 1073741824);
  assert.ok(Number.isNaN(parseAddress("abc")));
});

test("autoLayout is deterministic for the same IR", () => {
  const ir = baseIr();
  normalize(ir);
  const a = autoLayout(ir);
  const b = autoLayout(ir);
  assert.deepEqual([...a.entries()], [...b.entries()]);
});

test("autoLayout uses grid when every router has pos", () => {
  const ir = baseIr();
  ir.fabric.routers = [
    { name: "r00", clock: "core", pos: { x: 0, y: 0 } },
    { name: "r10", clock: "core", pos: { x: 1, y: 0 } }
  ];
  ir.nodes = [];
  ir.fabric.links = [];
  const pos = autoLayout(ir);
  assert.equal(pos.get("r10").x - pos.get("r00").x, 170);
  assert.equal(pos.get("r00").y, pos.get("r10").y);
});

test("computeLayout keeps cached coordinates for surviving entities", () => {
  const ir = baseIr();
  normalize(ir);
  const first = computeLayout(ir, new Map());
  const dragged = { x: 999, y: 999 };
  first.cache.set("r0", dragged);
  ir.nodes.push({ id: "m1", kind: "rn-i", clock: "core", attach: "r0" });
  normalize(ir);
  const second = computeLayout(ir, first.cache);
  assert.deepEqual(second.pos.get("r0"), dragged);
  assert.ok(second.pos.has("m1"));
});

test("hopCount over bidirectional router adjacency", () => {
  const ir = baseIr();
  ir.fabric.routers.push({ name: "r1", clock: "core" });
  ir.fabric.links.push({ from: "r0", to: "r1", cdc: "sync" });
  const adj = routerAdjacency(ir);
  assert.equal(hopCount(adj, "r0", "r1"), 1);
  assert.equal(hopCount(adj, "r1", "r0"), 1);
  assert.equal(hopCount(adj, "r0", "r0"), 0);
  assert.equal(hopCount(adj, "r0", "ghost"), null);
});

test("pairKey is order-independent; kind predicates", () => {
  assert.equal(pairKey("a", "b"), pairKey("b", "a"));
  assert.ok(isRequest({ kind: "rn-d" }));
  assert.ok(isSubordinate({ kind: "sn-i" }));
  assert.ok(!isRequest({ kind: "sn-f" }));
});

test("ensureV2 fills structural defaults only", () => {
  const ir = {};
  ensureV2(ir);
  assert.equal(ir.schema, "loom/v2");
  assert.deepEqual(ir.nodes, []);
  assert.deepEqual(ir.fabric.links, []);
  assert.equal(ir.chi.datWidth, undefined);
});