/* Loom GUI pure model core (loom/v2). DOM-free so it can be unit-tested
 * with node:test. All functions take the IR (or explicit params) rather
 * than closing over browser globals. Contract: src/loom/doc/ir_gen_spec.md. */

export const KINDS = ["rn-i", "rn-d", "sn-f", "sn-i", "hn-i", "mn"];
export const REQUEST_KINDS = ["rn-i", "rn-d"];
export const SUBORDINATE_KINDS = ["sn-f", "sn-i"];
export const PROTOCOLS = ["axi4", "chi"];
export const ADDR_WIDTHS = [32, 40, 48];
export const DAT_WIDTHS = [128, 256, 512];
export const NODE_DATA_WIDTHS = [32, 64, 128, 256, 512, 1024];
export const CHANNELS = ["req", "rsp", "dat", "snp"];

export const NODE_W = 130;
export const NODE_H = 26;

export const DEFAULT_QOS_CLASSES = [
  { name: "ctl", vcs: 1, depth: 4, arbitration: "wrr", weight: 4 },
  { name: "lat", vcs: 1, depth: 8, arbitration: "wrr", weight: 2 },
  { name: "bulk", vcs: 2, depth: 16, arbitration: "wrr", weight: 1 }
];
export const DEFAULT_QOS_MAPPING = [
  { qosMin: 8, qosMax: 15, class: "ctl" },
  { qosMin: 4, qosMax: 7, class: "lat" },
  { qosMin: 0, qosMax: 3, class: "bulk" }
];

export function isRequest(n) { return REQUEST_KINDS.includes(n.kind); }
export function isSubordinate(n) { return SUBORDINATE_KINDS.includes(n.kind); }

export function uniqueName(prefix, taken) {
  const set = new Set(taken);
  let i = 0;
  while (set.has(prefix + i)) i += 1;
  return prefix + i;
}

export const routerNames = (ir) => ir.fabric.routers.map((r) => r.name);
export const allObjectNames = (ir) => [...routerNames(ir), ...ir.nodes.map((n) => n.id)];
export const nodesOnRouter = (ir, name) => ir.nodes.filter((n) => n.attach === name);
export const subordinateIds = (ir) => ir.nodes.filter(isSubordinate).map((n) => n.id);

const routerOf = (ir, name) => ir.fabric.routers.find((r) => r.name === name) || null;

/* Structural defaults only; scalar defaults stay server-side (GEN-03). */
export function ensureV2(ir) {
  ir.schema = "loom/v2";
  ir.meta = ir.meta || {};
  ir.meta.name = ir.meta.name || "loom";
  delete ir.meta.schema;
  ir.clockDomains = ir.clockDomains || [];
  ir.chi = ir.chi || {};
  ir.nodes = ir.nodes || [];
  ir.fabric = ir.fabric || {};
  ir.fabric.routers = ir.fabric.routers || [];
  ir.fabric.links = ir.fabric.links || [];
  ir.partitions = ir.partitions || [];

  const fallbackClock = (ir.clockDomains[0] || {}).id || "core";
  for (const r of ir.fabric.routers) r.clock = r.clock || fallbackClock;
  for (const n of ir.nodes) n.clock = n.clock || fallbackClock;
  for (const p of ir.partitions) {
    p.members = p.members || [];
    if (!ir.clockDomains.some((c) => c.id === p.clock)) p.clock = fallbackClock;
  }
  return ir;
}

/* Auto attachment edges: RN node->router, SN router->node (IR-17, CHK-26).
 * hn-i/mn get no attachment edge (GUI-OQ-05). Other links preserved. */
export function normalize(ir) {
  ensureV2(ir);
  const routers = new Set(routerNames(ir));

  const isAutoAttach = (l) => ir.nodes.some((n) =>
    (isRequest(n) && l.from === n.id && l.to === n.attach) ||
    (isSubordinate(n) && l.to === n.id && l.from === n.attach));

  const explicit = ir.fabric.links.filter((l) => !isAutoAttach(l));
  const auto = [];
  for (const n of ir.nodes) {
    if (!routers.has(n.attach)) continue;
    if (isRequest(n)) auto.push({ from: n.id, to: n.attach, cdc: "sync" });
    else if (isSubordinate(n)) auto.push({ from: n.attach, to: n.id, cdc: "sync" });
    const r = routerOf(ir, n.attach);
    if (r) n.clock = r.clock;
  }

  const seen = new Set();
  ir.fabric.links = [...auto, ...explicit]
    .map((l) => {
      const async = l.cdc === "async";
      const out = { from: l.from, to: l.to, cdc: async ? "async" : "sync" };
      if (Number.isFinite(l.width)) out.width = Number(l.width);
      if (Number.isFinite(l.pipe)) out.pipe = Number(l.pipe);
      if (async) out.asyncFifoDepth = Number(l.asyncFifoDepth) || 2;
      if (Array.isArray(l.channels) && l.channels.length) out.channels = [...l.channels];
      if (Number.isFinite(l.lcreditDepth)) out.lcreditDepth = Number(l.lcreditDepth);
      if (l.dateline) out.dateline = true;
      return out;
    })
    .filter((l) => {
      const key = l.from + ">" + l.to;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => a.from.localeCompare(b.from) || a.to.localeCompare(b.to));

  const objects = new Set(allObjectNames(ir));
  for (const p of ir.partitions) {
    p.members = p.members.filter((m) => objects.has(m));
  }
  return ir;
}

export function parseAddress(s) {
  const t = String(s).trim();
  if (/^0x[0-9a-f]+$/i.test(t)) return parseInt(t, 16);
  if (/^\d+$/.test(t)) return parseInt(t, 10);
  const m = /^(\d+)([kKmMgG])$/.exec(t);
  if (m) {
    const unit = m[2].toLowerCase();
    const mult = unit === "k" ? 1024 : unit === "m" ? 1024 * 1024 : 1024 * 1024 * 1024;
    return parseInt(m[1], 10) * mult;
  }
  return NaN;
}

export function pairKey(a, b) {
  return a <= b ? a + ">" + b : b + ">" + a;
}

export function routerAdjacency(ir) {
  const adj = new Map(routerNames(ir).map((n) => [n, []]));
  for (const l of ir.fabric.links) {
    if (adj.has(l.from) && adj.has(l.to)) {
      adj.get(l.from).push(l.to);
      adj.get(l.to).push(l.from);
    }
  }
  return adj;
}

export function hopCount(adj, from, to) {
  if (!from || !to) return null;
  if (from === to) return 0;
  const seen = new Set([from]);
  const queue = [[from, 0]];
  while (queue.length) {
    const [node, d] = queue.shift();
    for (const next of adj.get(node) || []) {
      if (next === to) return d + 1;
      if (!seen.has(next)) {
        seen.add(next);
        queue.push([next, d + 1]);
      }
    }
  }
  return null;
}

/* Pure layout (IR -> coords). Grid mode when every router has pos;
 * otherwise deterministic BFS layers from the lex-smallest router. */
export function autoLayout(model) {
  const routers = model.fabric.routers;
  const nodes = model.nodes;
  const links = model.fabric.links;
  const pos = new Map();
  const cellW = 170, cellH = 92, x0 = 210;
  const names = routers.map((r) => r.name);
  const adj = new Map(names.map((n) => [n, []]));
  for (const l of links) {
    if (adj.has(l.from) && adj.has(l.to)) {
      adj.get(l.from).push(l.to);
      adj.get(l.to).push(l.from);
    }
  }
  const routerXY = new Map();
  const grid = routers.length > 0 && routers.every((r) => r.pos);
  if (grid) {
    const minX = Math.min(...routers.map((r) => r.pos.x));
    const minY = Math.min(...routers.map((r) => r.pos.y));
    for (const r of routers) {
      routerXY.set(r.name, { x: x0 + (r.pos.x - minX) * cellW, y: 40 + (r.pos.y - minY) * cellH });
    }
  } else {
    const layer = new Map();
    const sorted = [...names].sort();
    if (sorted.length) {
      layer.set(sorted[0], 0);
      const q = [sorted[0]];
      while (q.length) {
        const n = q.shift();
        for (const m of (adj.get(n) || []).slice().sort()) {
          if (!layer.has(m)) { layer.set(m, layer.get(n) + 1); q.push(m); }
        }
      }
    }
    for (const n of names) if (!layer.has(n)) layer.set(n, 0);
    const groups = new Map();
    for (const n of names) {
      const l = layer.get(n);
      if (!groups.has(l)) groups.set(l, []);
      groups.get(l).push(n);
    }
    for (const [l, list] of [...groups.entries()].sort((a, b) => a[0] - b[0])) {
      list.sort();
      list.forEach((n, i) => routerXY.set(n, { x: x0 + l * cellW, y: 40 + i * cellH }));
    }
  }
  for (const [n, p] of routerXY) pos.set(n, p);

  const byRouter = new Map(names.map((n) => [n, []]));
  for (const n of nodes) if (byRouter.has(n.attach)) byRouter.get(n.attach).push(n);
  const maxX = Math.max(0, ...[...routerXY.values()].map((p) => p.x));
  for (const r of names) {
    const rp = routerXY.get(r);
    const kids = byRouter.get(r).slice().sort((a, b) => a.id.localeCompare(b.id));
    const rn = kids.filter(isRequest);
    const sn = kids.filter(isSubordinate);
    const other = kids.filter((n) => !isRequest(n) && !isSubordinate(n));
    rn.forEach((n, i) => pos.set(n.id, { x: 30, y: rp.y + i * 34 }));
    sn.forEach((n, i) => pos.set(n.id, { x: maxX + NODE_W + 70, y: rp.y + i * 34 }));
    other.forEach((n, i) => pos.set(n.id, { x: rp.x, y: rp.y + 40 + i * 34 }));
  }
  return pos;
}

/* Surviving entities keep cached coordinates; new entities take auto slot.
 * Returns {pos, cache} so the caller owns the cache (no module global). */
export function computeLayout(model, cache) {
  const auto = autoLayout(model);
  const result = new Map();
  for (const [name, p] of auto) {
    result.set(name, cache && cache.has(name) ? cache.get(name) : p);
  }
  return { pos: result, cache: result };
}

/* Pure rename cascade across nodes/routers/links/sam/partitions. */
export function renameObjectIn(ir, oldName, newName) {
  const r = routerOf(ir, oldName);
  if (r) r.name = newName;
  const n = ir.nodes.find((x) => x.id === oldName);
  if (n) n.id = newName;
  for (const m of ir.nodes) if (m.attach === oldName) m.attach = newName;
  for (const l of ir.fabric.links) {
    if (l.from === oldName) l.from = newName;
    if (l.to === oldName) l.to = newName;
  }
  if (Array.isArray(ir.sam)) {
    for (const s of ir.sam) if (s.tgtNode === oldName) s.tgtNode = newName;
  }
  for (const p of ir.partitions) {
    p.members = p.members.map((m) => (m === oldName ? newName : m));
  }
  return ir;
}

export function removeNodeIn(ir, id) {
  ir.nodes = ir.nodes.filter((n) => n.id !== id);
  if (Array.isArray(ir.sam)) ir.sam = ir.sam.filter((s) => s.tgtNode !== id);
  ir.fabric.links = ir.fabric.links.filter((l) => l.from !== id && l.to !== id);
  for (const p of ir.partitions) p.members = p.members.filter((m) => m !== id);
  return ir;
}

export function removeRouterIn(ir, name) {
  for (const n of nodesOnRouter(ir, name).map((n) => n.id)) removeNodeIn(ir, n);
  ir.fabric.links = ir.fabric.links.filter((l) => l.from !== name && l.to !== name);
  ir.fabric.routers = ir.fabric.routers.filter((r) => r.name !== name);
  for (const p of ir.partitions) p.members = p.members.filter((m) => m !== name);
  return ir;
}

/* CHK-19 creation guardrail. dvm is the effective dvmEnable. */
export function kindAllowed(ir, kind, excludeId, dvm) {
  if ((kind === "rn-d" || kind === "mn") && !dvm) {
    return { allowed: false, reason: `${kind} requires chi.dvmEnable=true (CHK-19).` };
  }
  if (kind === "mn") {
    const mnCount = ir.nodes.filter((n) => n.kind === "mn" && n.id !== excludeId).length;
    if (mnCount >= 1) return { allowed: false, reason: "At most one mn node allowed (CHK-19)." };
  }
  return { allowed: true, reason: null };
}

/* Explicit ir.sam, else the derived preview from the API addressMap. */
export function samRows(ir, addressMap) {
  if (Array.isArray(ir.sam)) return ir.sam;
  if (addressMap && Array.isArray(addressMap.sam)) {
    return addressMap.sam.map((s) => ({ base: s.base, size: s.size, tgtNode: s.tgtNode }));
  }
  return [];
}