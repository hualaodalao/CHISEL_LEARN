"use strict";

const $ = (id) => document.getElementById(id);

const ADDR_WIDTHS = [16, 32, 48, 64];
const DATA_WIDTHS = [32, 64, 128, 512, 1024];
const PROTOCOLS = ["native", "axi4", "axilite", "tlul"];
const QOS = ["ctl", "lat", "bulk"];
const DOMAIN_COLORS = ["#a4532c", "#2b6f63", "#7a5a1f", "#4a5470", "#8c3f56", "#3f6b8c"];

const DEFAULT_QOS = {
  classes: [
    { name: "ctl", vcs: 1, depth: 4, arbitration: "wrr", weight: 4, maxPacketBeats: 1 },
    { name: "lat", vcs: 1, depth: 8, arbitration: "wrr", weight: 2, maxPacketBeats: 2 },
    { name: "bulk", vcs: 2, depth: 16, arbitration: "wrr", weight: 1, maxPacketBeats: 4 }
  ]
};

let ir = null;
let last = null;
let selection = null;
let widths = { addr: 32, data: 128 };

/* ------------------------------------------------------------------ */
/* small helpers                                                       */
/* ------------------------------------------------------------------ */

function el(tag, cls, text) {
  const d = document.createElement(tag);
  if (cls) d.className = cls;
  if (text != null) d.textContent = text;
  return d;
}

function clear(node) {
  node.replaceChildren();
}

function uniqueName(prefix, taken) {
  const set = new Set(taken);
  let i = 0;
  while (set.has(prefix + i)) i += 1;
  return prefix + i;
}

let toastTimer = null;
function toast(msg) {
  const box = $("toast");
  box.textContent = msg;
  box.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => box.classList.remove("show"), 2400);
}

function dangerButton(label, onConfirm) {
  const b = el("button", "danger", label);
  b.type = "button";
  let armed = false;
  let timer = null;
  b.addEventListener("click", () => {
    if (!armed) {
      armed = true;
      b.textContent = "sure?";
      b.classList.add("armed");
      timer = setTimeout(() => {
        armed = false;
        b.textContent = label;
        b.classList.remove("armed");
      }, 2500);
      return;
    }
    clearTimeout(timer);
    onConfirm();
  });
  return b;
}

function selectInput(values, current, onChange, numeric = false) {
  const s = document.createElement("select");
  for (const v of values) {
    const o = document.createElement("option");
    o.value = String(v);
    o.textContent = String(v);
    s.appendChild(o);
  }
  if (current != null) s.value = String(current);
  if (onChange) {
    s.addEventListener("change", () => onChange(numeric ? Number(s.value) : s.value));
  }
  return s;
}

function textInput(value, onChange) {
  const i = document.createElement("input");
  i.type = "text";
  i.spellcheck = false;
  i.value = value == null ? "" : String(value);
  i.addEventListener("change", () => onChange(i.value.trim()));
  return i;
}

function numberInput(value, min, max, onChange) {
  const i = document.createElement("input");
  i.type = "number";
  i.min = String(min);
  i.max = String(max);
  i.value = value == null ? String(min) : String(value);
  i.addEventListener("change", () => {
    const n = Number(i.value);
    if (Number.isFinite(n)) onChange(Math.max(min, Math.min(max, n)));
  });
  return i;
}

function field(labelText, control) {
  const l = el("label");
  l.appendChild(el("span", null, labelText));
  l.appendChild(control);
  return l;
}

/* ------------------------------------------------------------------ */
/* model helpers                                                       */
/* ------------------------------------------------------------------ */

function ensureFabric() {
  ir.schema = ir.schema || "loom/v1";
  ir.meta = ir.meta || {};
  ir.meta.name = ir.meta.name || "loom";
  delete ir.meta.schema;
  ir.clockDomains = ir.clockDomains || [];
  if (!ir.qos || !Array.isArray(ir.qos.classes) || !ir.qos.classes.length) ir.qos = DEFAULT_QOS;
  ir.endpoints = ir.endpoints || [];
  ir.fabric = ir.fabric || {};
  ir.fabric.mnet = ir.fabric.mnet || [];
  ir.fabric.snet = ir.fabric.snet || [];
  ir.fabric.routers = ir.fabric.routers || [];
  ir.fabric.links = ir.fabric.links || [];
  ir.fabric.timing = ir.fabric.timing || { maxFanin: 8, maxCombDepth: 12 };
  ir.partitions = ir.partitions || [];
  if (!Number.isFinite(ir.axiIdWidth)) ir.axiIdWidth = 4;

  const fallbackClock = (ir.clockDomains[0] || {}).id || "core";
  for (const r of ir.fabric.routers) {
    r.switch = r.switch || {};
    r.switch.kind = r.switch.kind || "flat";
    r.switch.banks = Number(r.switch.banks) || 1;
    r.clock = r.clock || fallbackClock;
    r.in = Number.isFinite(r.in) ? r.in : 1;
    r.out = Number.isFinite(r.out) ? r.out : 1;
  }
  for (const n of [...ir.fabric.mnet, ...ir.fabric.snet]) n.clock = n.clock || fallbackClock;
  for (const p of ir.partitions) {
    p.members = p.members || [];
    p.boundaryPipe = Number.isFinite(p.boundaryPipe) ? p.boundaryPipe : 1;
    if (!ir.clockDomains.some((c) => c.id === p.clock)) p.clock = fallbackClock;
  }
}

const routerNames = () => ir.fabric.routers.map((r) => r.name);
const isRouter = (name) => routerNames().includes(name);
const allNodeNames = () => [
  ...routerNames(),
  ...ir.fabric.mnet.map((n) => n.name),
  ...ir.fabric.snet.map((n) => n.name)
];

function nodeOf(name) {
  return ir.fabric.mnet.find((n) => n.name === name)
    || ir.fabric.snet.find((n) => n.name === name)
    || ir.fabric.routers.find((n) => n.name === name)
    || null;
}

function clockOfNode(name) {
  const n = nodeOf(name);
  return n ? n.clock : "";
}

function partitionOf(name) {
  return ir.partitions.find((p) => p.members.includes(name)) || null;
}

/** mnet/snet -> owning router, taken from the attachment links. */
function ownership() {
  const owner = new Map();
  for (const l of ir.fabric.links) {
    if (isRouter(l.to) && !isRouter(l.from)) owner.set(l.from, l.to);
    if (isRouter(l.from) && !isRouter(l.to)) owner.set(l.to, l.from);
  }
  // A node that already sits in a single-router partition belongs to it.
  for (const p of ir.partitions) {
    const routers = ir.fabric.routers.filter((r) => p.members.includes(r.name));
    if (routers.length === 1) {
      for (const n of [...ir.fabric.mnet, ...ir.fabric.snet]) {
        if (p.members.includes(n.name) && !owner.has(n.name)) owner.set(n.name, routers[0].name);
      }
    }
  }
  return owner;
}

function normalize() {
  ensureFabric();
  const owner = ownership();

  const explicit = ir.fabric.links.filter(
    (l) => isRouter(l.from) && isRouter(l.to) && l.from !== l.to
  );
  const auto = [];
  for (const r of ir.fabric.routers) {
    for (const m of ir.fabric.mnet) {
      if (owner.get(m.name) === r.name) {
        auto.push({ from: m.name, to: r.name, width: widths.data, cdc: "sync", pipe: 0 });
      }
    }
    for (const s of ir.fabric.snet) {
      if (owner.get(s.name) === r.name) {
        auto.push({ from: r.name, to: s.name, width: widths.data, cdc: "sync", pipe: 0 });
      }
    }
  }

  const seen = new Set();
  ir.fabric.links = [...auto, ...explicit]
    .map((l) => {
      const async = l.cdc === "async";
      const out = {
        from: l.from,
        to: l.to,
        width: Number(l.width) || widths.data,
        cdc: async ? "async" : "sync",
        pipe: Number.isFinite(l.pipe) ? Number(l.pipe) : (isRouter(l.from) && isRouter(l.to) ? 1 : 0)
      };
      if (async) out.asyncFifoDepth = Number(l.asyncFifoDepth) || 4;
      return out;
    })
    .filter((l) => {
      const key = l.from + ">" + l.to;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => a.from.localeCompare(b.from) || a.to.localeCompare(b.to));

  for (const p of ir.partitions) {
    const prev = new Set(p.members);
    const routers = ir.fabric.routers.filter((r) => prev.has(r.name)).map((r) => r.name);
    const routerSet = new Set(routers);
    const mnets = ir.fabric.mnet.filter((m) => routerSet.has(owner.get(m.name))).map((m) => m.name);
    const snets = ir.fabric.snet.filter((s) => routerSet.has(owner.get(s.name))).map((s) => s.name);
    p.members = [...routers, ...mnets, ...snets];
  }

  for (const e of ir.endpoints) {
    const clock = clockOfNode(e.attach);
    if (clock) e.clock = clock;
  }

  if (ir.fabric.routers.length === 1) {
    const r = ir.fabric.routers[0];
    r.in = Math.max(1, ir.fabric.mnet.length);
    r.out = Math.max(1, ir.fabric.snet.length);
  }
}

/* ------------------------------------------------------------------ */
/* commit / evaluate                                                   */
/* ------------------------------------------------------------------ */

function syncEditor() {
  $("editor").value = JSON.stringify(ir, null, 2);
}

function commit() {
  normalize();
  syncEditor();
  renderAll();
  scheduleEvaluate();
  scheduleDraftSave();
}

let evalTimerLocal = null;
function scheduleEvaluate() {
  clearTimeout(evalTimerLocal);
  evalTimerLocal = setTimeout(evaluate, 220);
}

let evalSeq = 0;
async function evaluate() {
  if (!ir) return;
  const seq = ++evalSeq;
  try {
    const res = await fetch("/api/evaluate", { method: "POST", body: $("editor").value });
    const data = await res.json();
    if (seq !== evalSeq) return;
    last = data;
    $("hash").textContent = data.hash ? data.hash.slice(0, 12) : "";
    showGates(data);
    showEstimate(data);
    showAddressMap(data);
    showTopology(data);
  } catch (e) {
    if (seq === evalSeq) showProblem("evaluate: " + e.message);
  }
}

function showProblem(text) {
  const box = $("problems");
  clear(box);
  box.appendChild(el("div", "err", text));
}

/* ------------------------------------------------------------------ */
/* workspace + draft autosave                                          */
/* ------------------------------------------------------------------ */

let workspace = null;
let lastSaved = null;
let draftTimer = null;

function renderWorkspace() {
  const b = $("workspace");
  if (!workspace) {
    b.textContent = "set workspace";
    b.title = "no workspace — drafts are not saved";
    b.classList.add("needs-workspace");
    return;
  }
  const name = workspace.split("/").filter(Boolean).pop() || workspace;
  b.textContent = name;
  b.title = `${workspace} — autosaved as loom_draft.json`;
  b.classList.remove("needs-workspace");
}

function openWsDialog() {
  $("ws-path").value = workspace || "";
  $("ws-dialog").showModal();
  $("ws-path").focus();
}

function closeWsDialog() {
  if ($("ws-dialog").open) $("ws-dialog").close();
}

async function adoptDraft() {
  const res = await fetch("/api/draft");
  if (!res.ok) return false;
  try {
    const text = await res.text();
    ir = JSON.parse(text);
    ensureFabric();
    adoptWidths();
    normalize();
    syncEditor();
    renderAll();
    lastSaved = $("editor").value;
    scheduleEvaluate();
    toast("restored draft from workspace");
    return true;
  } catch {
    toast("draft in workspace is not valid JSON");
    return false;
  }
}

async function setWorkspace(path) {
  const trimmed = String(path || "").trim();
  if (!trimmed) return;
  try {
    const res = await fetch("/api/workspace", { method: "POST", body: trimmed });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      toast(data.error || "workspace failed");
      return;
    }
    const previous = workspace;
    workspace = data.workspace;
    renderWorkspace();
    closeWsDialog();
    if (data.draft && workspace !== previous) {
      if (window.confirm("Load loom_draft.json from this folder? Cancel keeps the current design.")) {
        if (await adoptDraft()) return;
      }
    }
    lastSaved = null;
    saveDraft();
  } catch (e) {
    toast("workspace: " + e.message);
  }
}

function scheduleDraftSave() {
  if (!workspace) return;
  clearTimeout(draftTimer);
  draftTimer = setTimeout(saveDraft, 600);
}

async function saveDraft() {
  if (!workspace) return;
  const text = $("editor").value;
  if (!text || text === lastSaved) return;
  try {
    const res = await fetch("/api/draft", { method: "POST", body: text });
    if (!res.ok) return;
    lastSaved = text;
    const b = $("workspace");
    b.classList.add("saved");
    setTimeout(() => b.classList.remove("saved"), 900);
  } catch {
    /* server gone; the next edit retries */
  }
}

/* ------------------------------------------------------------------ */
/* rendering                                                           */
/* ------------------------------------------------------------------ */

function domainColor(clockId) {
  const ids = ir.clockDomains.map((c) => c.id).slice().sort();
  const i = ids.indexOf(clockId);
  return DOMAIN_COLORS[(i < 0 ? 0 : i) % DOMAIN_COLORS.length];
}

function renderAll() {
  if (!ir) return;
  renderDesign();
  renderClocks();
  renderStructure();
  renderLinks();
  renderEndpoints();
  renderMatrix();
  renderInspector();
  renderSummary();
}

function routerAdjacency() {
  const adj = new Map(routerNames().map((n) => [n, []]));
  for (const l of ir.fabric.links) {
    if (adj.has(l.from) && adj.has(l.to)) adj.get(l.from).push(l.to);
  }
  return adj;
}

function hopCount(adj, from, to) {
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

function renderMatrix() {
  const box = $("matrix");
  if (!box) return;
  clear(box);
  const masters = ir.endpoints.filter((e) => e.role === "master");
  const slaves = ir.endpoints.filter((e) => e.role === "slave");
  if (!masters.length || !slaves.length) {
    box.appendChild(el("tr")).appendChild(el("td", "na", "Needs at least one master and one slave."));
    return;
  }
  const owner = ownership();
  const adj = routerAdjacency();
  const head = el("tr");
  head.appendChild(el("th", "row-head", "initiator \\ target"));
  for (const s of slaves) head.appendChild(el("th", null, s.id));
  box.appendChild(head);
  for (const m of masters) {
    const tr = el("tr");
    tr.appendChild(el("th", "row-head", m.id));
    for (const s of slaves) {
      const hops = hopCount(adj, owner.get(m.attach), owner.get(s.attach));
      const cls = hops === null ? "na" : (hops === 0 ? "h0" : "hn");
      const td = el("td", cls, hops === null ? "—" : String(hops));
      td.title = hops === null
        ? `${m.id} cannot reach ${s.id}`
        : `${m.id} → ${s.id}: ${hops} router hop${hops === 1 ? "" : "s"}`;
      tr.appendChild(td);
    }
    box.appendChild(tr);
  }
}

function renderSummary() {
  const parts = [
    `${ir.clockDomains.length} clocks`,
    `${ir.partitions.length} partitions`,
    `${ir.fabric.routers.length} routers`,
    `${ir.endpoints.length} endpoints`
  ];
  $("summary").textContent = parts.join(" · ");
}

function renderDesign() {
  $("design-name").value = ir.meta.name || "";
  const addr = $("addr-width");
  if (!addr.options.length) {
    for (const w of ADDR_WIDTHS) addr.appendChild(new Option(String(w), String(w)));
  }
  const data = $("data-width");
  if (!data.options.length) {
    for (const w of DATA_WIDTHS) data.appendChild(new Option(String(w), String(w)));
  }
  addr.value = String(widths.addr);
  data.value = String(widths.data);
}

function renderClocks() {
  const box = $("clocks");
  clear(box);
  if (!ir.clockDomains.length) {
    box.appendChild(el("div", "empty", "No clocks yet. Create one before adding routers."));
    return;
  }
  const usage = new Map();
  for (const p of ir.partitions) usage.set(p.clock, (usage.get(p.clock) || 0) + 1);
  for (const n of [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]) {
    usage.set(n.clock, (usage.get(n.clock) || 0) + 1);
  }
  for (const c of ir.clockDomains) {
    const chip = el("span", "chip clock");
    chip.style.setProperty("--domain", domainColor(c.id));
    chip.appendChild(el("b", null, c.id));
    chip.appendChild(el("span", null, `${c.freqMHz} MHz`));
    if (selection && selection.kind === "clock" && selection.id === c.id) chip.classList.add("selected");
    chip.addEventListener("click", () => {
      selection = { kind: "clock", id: c.id };
      renderAll();
    });
    const x = el("button", null, "×");
    x.type = "button";
    x.title = "remove clock";
    x.addEventListener("click", (ev) => {
      ev.stopPropagation();
      removeClock(c.id);
    });
    chip.appendChild(x);
    box.appendChild(chip);
  }
}

function setPartitionClock(part, clockId) {
  part.clock = clockId;
  for (const name of part.members) {
    const n = nodeOf(name);
    if (n) n.clock = clockId;
  }
  for (const e of ir.endpoints) {
    if (part.members.includes(e.attach)) e.clock = clockId;
  }
}

function setLooseNodeClock(name, clockId) {
  const n = nodeOf(name);
  if (!n) return;
  n.clock = clockId;
  const owner = ownership();
  for (const m of [...ir.fabric.mnet, ...ir.fabric.snet]) {
    if (owner.get(m.name) === name) m.clock = clockId;
  }
  for (const e of ir.endpoints) {
    if (e.attach === name || owner.get(e.attach) === name) e.clock = clockId;
  }
}

function clockSelect(value, onChange) {
  return selectInput(ir.clockDomains.map((c) => c.id), value, onChange);
}

function memberEndpoints(netName) {
  return ir.endpoints.filter((e) => e.attach === netName);
}

function endpointsOnRouter(name) {
  const owner = ownership();
  return ir.endpoints.filter((e) => e.attach === name || owner.get(e.attach) === name);
}

function endpointContext() {
  if (!selection) return null;
  if (selection.kind === "router") return { label: selection.id, list: endpointsOnRouter(selection.id) };
  if (selection.kind === "mnet" || selection.kind === "snet")
    return { label: selection.id, list: memberEndpoints(selection.id) };
  return null;
}

function renderStructure() {
  const box = $("structure");
  clear(box);
  const owner = ownership();
  const flat = ir.fabric.routers.filter((r) => !partitionOf(r.name));

  if (!ir.partitions.length) {
    box.appendChild(el("div", "flat-note",
      "Flat fabric: no split regions yet. Create a partition to group routers by clock domain."));
  }

  for (const p of ir.partitions) {
    const card = el("div", "partition");
    card.style.setProperty("--domain", domainColor(p.clock));
    const head = el("div", "partition-head");
    const name = el("button", "name-btn", p.name);
    name.type = "button";
    name.addEventListener("click", () => {
      selection = { kind: "partition", id: p.name };
      renderAll();
    });
    head.appendChild(name);
    head.appendChild(clockSelect(p.clock, (v) => {
      setPartitionClock(p, v);
      commit();
    }));
    const addR = el("button", "ghost", "+ router");
    addR.type = "button";
    addR.addEventListener("click", () => addRouter(p));
    head.appendChild(el("span", "spacer"));
    head.appendChild(addR);
    head.appendChild(dangerButton("remove", () => removePartition(p.name)));
    card.appendChild(head);

    const routers = ir.fabric.routers.filter((r) => p.members.includes(r.name));
    if (!routers.length) card.appendChild(el("div", "hint", "No routers in this region yet."));
    for (const r of routers) card.appendChild(routerCard(r, p, owner));
    box.appendChild(card);
  }

  if (flat.length) {
    if (ir.partitions.length) {
      box.appendChild(el("div", "flat-note", "Unassigned routers (add them to a partition):"));
    }
    for (const r of flat) box.appendChild(routerCard(r, null, owner));
  }

  if (!ir.fabric.routers.length) {
    box.appendChild(el("div", "empty", "No routers yet. Create a partition, then add a router inside it."));
  }
}

function routerCard(r, part, owner) {
  const wrap = el("div", "router");

  const row = el("div", "node");
  if (selection && selection.kind === "router" && selection.id === r.name) row.classList.add("selected");
  const sw = el("span", "swatch");
  sw.style.setProperty("--domain", domainColor(r.clock));
  row.appendChild(sw);
  const name = el("button", "name-btn", r.name);
  name.type = "button";
  name.addEventListener("click", () => {
    selection = { kind: "router", id: r.name };
    renderAll();
  });
  row.appendChild(name);
  const clockChip = el("span", "chip clock", r.clock);
  clockChip.style.setProperty("--domain", domainColor(r.clock));
  row.appendChild(clockChip);
  const monets = ir.fabric.mnet.filter((m) => owner.get(m.name) === r.name);
  const sonets = ir.fabric.snet.filter((s) => owner.get(s.name) === r.name);
  row.appendChild(el("span", "counts", `${monets.length} mnet · ${sonets.length} snet`));

  const actions = el("span", "row-actions");
  const addM = el("button", "ghost", "+ mnet");
  addM.type = "button";
  addM.title = "create a master-side network on this router";
  addM.addEventListener("click", () => addNet("mnet", r));
  const addS = el("button", "ghost", "+ snet");
  addS.type = "button";
  addS.addEventListener("click", () => addNet("snet", r));
  actions.appendChild(addM);
  actions.appendChild(addS);
  actions.appendChild(dangerButton("remove", () => removeRouter(r.name)));
  row.appendChild(actions);
  wrap.appendChild(row);

  const nets = el("div");
  for (const m of monets) nets.appendChild(netRow("mnet", m, owner));
  for (const s of sonets) nets.appendChild(netRow("snet", s, owner));
  if (!monets.length && !sonets.length) nets.appendChild(el("div", "hint", "No MNet/SNet on this router."));
  wrap.appendChild(nets);
  return wrap;
}

function netRow(kind, net, owner) {
  const row = el("div", "node member");
  if (selection && selection.kind === kind && selection.id === net.name) row.classList.add("selected");
  const sw = el("span", "swatch");
  sw.style.setProperty("--domain", domainColor(net.clock));
  row.appendChild(sw);
  const name = el("button", "name-btn", net.name);
  name.type = "button";
  name.addEventListener("click", () => {
    selection = { kind, id: net.name };
    renderAll();
  });
  row.appendChild(name);
  const neps = memberEndpoints(net.name).length;
  row.appendChild(el("span", "counts",
    `${neps} endpoint${neps === 1 ? "" : "s"}${neps > 1 ? " · shared" : ""}`));
  const addEp = el("button", "ghost", "+ ep");
  addEp.type = "button";
  addEp.title = `attach an endpoint to ${net.name}`;
  addEp.addEventListener("click", () => addEndpoint(net.name));
  row.appendChild(addEp);
  row.appendChild(dangerButton("remove", () => removeNet(kind, net.name)));
  return row;
}

function renderLinks() {
  const box = $("links");
  clear(box);
  const explicit = ir.fabric.links.filter((l) => isRouter(l.from) && isRouter(l.to));
  if (!explicit.length) box.appendChild(el("div", "empty", "No router-to-router links."));

  for (const l of explicit) {
    const key = l.from + ">" + l.to;
    const row = el("div", "link-row");
    const main = el("div", "link-main");
    const route = el("button", "link-route", `${l.from} → ${l.to}`);
    route.type = "button";
    route.addEventListener("click", () => {
      selection = { kind: "link", id: key };
      renderAll();
    });
    main.appendChild(route);
    if (l.cdc === "async") {
      const chip = el("span", "chip clock", "async");
      chip.style.setProperty("--domain", "var(--copper)");
      main.appendChild(chip);
    }
    main.appendChild(el("span", "counts", `${l.width} bit · pipe ${l.pipe}`));
    row.appendChild(main);

    const controls = el("div", "link-controls");
    const cdc = selectInput(["sync", "async"], l.cdc, (v) => {
      l.cdc = v;
      if (v === "async") l.asyncFifoDepth = l.asyncFifoDepth || 4;
      commit();
    });
    controls.appendChild(cdc);
    controls.appendChild(dangerButton("remove", () => removeLink(l.from, l.to)));
    row.appendChild(controls);
    box.appendChild(row);
  }

  renderLinkForm();
}

function renderLinkForm() {
  const box = $("link-form");
  clear(box);
  const routers = routerNames();
  if (routers.length < 2) {
    box.appendChild(el("div", "hint", "Create at least two routers to add a router-to-router link."));
    return;
  }
  const from = selectInput(routers, routers[0], null);
  const to = selectInput(routers, routers[1], null);
  const cdc = selectInput(["sync", "async"], "sync", null);
  const add = el("button", "primary", "add link");
  add.type = "button";
  add.addEventListener("click", () => {
    if (from.value === to.value) {
      toast("A router link needs two different routers.");
      return;
    }
    ir.fabric.links.push({
      from: from.value, to: to.value, width: widths.data,
      cdc: cdc.value, pipe: 1, ...(cdc.value === "async" ? { asyncFifoDepth: 4 } : {})
    });
    selection = { kind: "link", id: from.value + ">" + to.value };
    commit();
  });
  box.appendChild(field("From router", from));
  box.appendChild(field("To router", to));
  box.appendChild(field("Crossing", cdc));
  box.appendChild(add);
  box.lastChild.classList.add("span-2");
  const hint = el("div", "hint span-2", "async requires the two routers to sit in different clock domains.");
  const hint2 = el("div", "hint span-2", "A closed router cycle with 'any' routing fails G1; use XY on a 2x2 mesh or leave a cut.");
  box.appendChild(hint);
  box.appendChild(hint2);
}

function renderEndpoints() {
  const box = $("endpoints");
  clear(box);
  const ctx = endpointContext();
  const list = ctx ? ctx.list : ir.endpoints;
  if (ctx) {
    const head = el("div", "ep-context");
    head.appendChild(el("span", "chip", `on ${ctx.label} · ${list.length}`));
    const all = el("button", "ghost", "show all");
    all.type = "button";
    all.addEventListener("click", () => {
      selection = null;
      renderAll();
    });
    head.appendChild(all);
    box.appendChild(head);
  }
  if (!list.length) {
    box.appendChild(el("div", "empty", ctx ? `No endpoints on ${ctx.label}.` : "No endpoints yet."));
    return;
  }
  const table = el("table", "ep-table");
  const head = el("tr");
  for (const h of ["id", "role", "attach", "qos", "outs"]) head.appendChild(el("th", null, h));
  table.appendChild(head);
  for (const e of list) {
    const tr = el("tr", "ep-row");
    if (selection && selection.kind === "endpoint" && selection.id === e.id) tr.classList.add("selected");
    tr.appendChild(el("td", null, e.id));
    const role = el("td", "role" + (e.role === "slave" ? " slave" : ""), e.role);
    tr.appendChild(role);
    tr.appendChild(el("td", null, e.attach));
    tr.appendChild(el("td", null, e.qos || "–"));
    tr.appendChild(el("td", null, String(e.outstanding || 1)));
    tr.addEventListener("click", () => {
      selection = { kind: "endpoint", id: e.id };
      renderAll();
    });
    table.appendChild(tr);
  }
  box.appendChild(table);
}

function parseAddress(s) {
  const t = String(s).trim();
  if (/^0x[0-9a-f]+$/i.test(t)) return parseInt(t, 16);
  if (/^\d+$/.test(t)) return parseInt(t, 10);
  const m = /^(\d+)([kKmM])$/.exec(t);
  if (m) return parseInt(m[1], 10) * (m[2].toLowerCase() === "k" ? 1024 : 1024 * 1024);
  return NaN;
}

function setWindow(index, key, value) {
  const map = last && last.addressMap;
  if (!map || !map.slaves[index]) return;
  const slaves = ir.endpoints.filter((e) => e.role === "slave");
  const wins = map.slaves.map((s, i) => ({
    base: parseInt(s.base, 16),
    size: Number(s.size)
  }));
  if (!wins[index]) return;
  wins[index][key] = value;
  slaves.forEach((e, i) => {
    e.base = wins[i].base;
    e.size = wins[i].size;
  });
  commit();
}

function autoWindows() {
  const slaves = ir.endpoints.filter((e) => e.role === "slave");
  for (const e of slaves) {
    delete e.base;
    delete e.size;
  }
  commit();
}

function showAddressMap(data) {
  const box = $("address-map");
  const note = $("addr-note");
  clear(box);
  const map = data.addressMap;
  if (!map) {
    note.textContent = "";
    box.appendChild(el("div", "empty", "not available"));
    return;
  }
  const table = el("table", "ep-table addr-table");
  const head = el("tr");
  for (const h of ["target", "base", "size", "end"]) head.appendChild(el("th", null, h));
  table.appendChild(head);
  map.slaves.forEach((s, idx) => {
    const tr = el("tr", "ep-row");
    tr.appendChild(el("td", null, s.id));
    const base = document.createElement("input");
    base.type = "text";
    base.spellcheck = false;
    base.value = s.base;
    base.addEventListener("change", () => {
      const v = parseAddress(base.value);
      if (!Number.isFinite(v)) {
        toast("base must be a number, hex, or e.g. 64K");
        renderAll();
        return;
      }
      setWindow(idx, "base", v);
    });
    const size = document.createElement("input");
    size.type = "text";
    size.spellcheck = false;
    size.value = String(s.size);
    size.addEventListener("change", () => {
      const v = parseAddress(size.value);
      if (!Number.isFinite(v)) {
        toast("size must be a number, hex, or e.g. 64K");
        renderAll();
        return;
      }
      setWindow(idx, "size", v);
    });
    const baseCell = el("td");
    baseCell.appendChild(base);
    tr.appendChild(baseCell);
    const sizeCell = el("td");
    sizeCell.appendChild(size);
    tr.appendChild(sizeCell);
    tr.appendChild(el("td", null, s.end));
    table.appendChild(tr);
  });
  box.appendChild(table);
  note.textContent = map.explicit
    ? "Explicit windows: RTL matches base <= addr <= end; windows must be aligned and must not overlap."
    : `Derived 4 KB windows in slave order. Edit a base or size to switch to an explicit map.`;
}

/* ------------------------------------------------------------------ */
/* inspector                                                           */
/* ------------------------------------------------------------------ */

function renderInspector() {
  const box = $("inspector");
  clear(box);
  if (!selection) {
    box.appendChild(el("h2", null, "Inspector"));
    box.appendChild(el("p", "hint",
      "Select a clock, partition, router, MNet/SNet, endpoint or link to edit it."));
    return;
  }
  const { kind, id } = selection;
  if (kind === "clock") inspectClock(box, id);
  else if (kind === "partition") inspectPartition(box, id);
  else if (kind === "router") inspectRouter(box, id);
  else if (kind === "mnet" || kind === "snet") inspectNet(box, kind, id);
  else if (kind === "endpoint") inspectEndpoint(box, id);
  else if (kind === "link") inspectLink(box, id);
}

function inspectHead(box, kind, id) {
  box.appendChild(el("h2", null, "Inspector"));
  box.appendChild(el("p", "kind", kind));
  box.appendChild(el("p", "id-line", id));
}

function inspectClock(box, id) {
  const c = ir.clockDomains.find((x) => x.id === id);
  if (!c) { selection = null; return renderInspector(); }
  inspectHead(box, "clock domain", c.id);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Id", textInput(c.id, (v) => renameClock(c.id, v))));
  grid.appendChild(field("Frequency (MHz)", numberInput(c.freqMHz, 1, 100000, (v) => {
    c.freqMHz = v;
    commit();
  })));
  box.appendChild(grid);
  const users = [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]
    .filter((n) => n.clock === c.id).length
    + ir.partitions.filter((p) => p.clock === c.id).length;
  box.appendChild(el("p", "meta-line", `Used by ${users} node(s)/partition(s).`));
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete clock", () => removeClock(c.id)));
  box.appendChild(zone);
}

function inspectPartition(box, id) {
  const p = ir.partitions.find((x) => x.name === id);
  if (!p) { selection = null; return renderInspector(); }
  inspectHead(box, "partition", p.name);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Name", textInput(p.name, (v) => renamePartition(p.name, v))));
  grid.appendChild(field("Clock", clockSelect(p.clock, (v) => {
    setPartitionClock(p, v);
    commit();
  })));
  grid.appendChild(field("Boundary pipe", numberInput(p.boundaryPipe, 1, 8, (v) => {
    p.boundaryPipe = v;
    commit();
  })));
  box.appendChild(grid);
  const routers = ir.fabric.routers.filter((r) => p.members.includes(r.name)).map((r) => r.name);
  box.appendChild(el("p", "meta-line", `Routers: ${routers.length ? routers.join(", ") : "none"}`));
  const clock = clockOfNode(routers[0]);
  if (clock && clock !== p.clock) {
    box.appendChild(el("div", "warn", `Member '${routers[0]}' runs on '${clock}', not '${p.clock}'.`));
  }
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete partition", () => removePartition(p.name)));
  box.appendChild(zone);
}

function inspectRouter(box, id) {
  const r = ir.fabric.routers.find((x) => x.name === id);
  if (!r) { selection = null; return renderInspector(); }
  inspectHead(box, "router", r.name);
  const part = partitionOf(r.name);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Name", textInput(r.name, (v) => renameNode(r.name, v))));
  grid.appendChild(field("Clock", clockSelect(r.clock, (v) => {
    if (part) setPartitionClock(part, v);
    else setLooseNodeClock(r.name, v);
    commit();
  })));
  grid.appendChild(field("Switch", selectInput(["flat", "banked"], r.switch.kind, (v) => {
    r.switch.kind = v;
    if (v === "banked" && r.switch.banks === 1) r.switch.banks = 2;
    if (v === "flat") r.switch.banks = 1;
    commit();
  })));
  grid.appendChild(field("Banks", selectInput([1, 2, 4, 8], r.switch.banks, (v) => {
    r.switch.banks = v;
    r.switch.kind = v > 1 ? "banked" : "flat";
    commit();
  }, true)));
  grid.appendChild(field("Routing", selectInput(["any", "xy"], r.routing || "any", (v) => {
    r.routing = v;
    if (v === "xy" && !r.pos) r.pos = { x: ir.fabric.routers.indexOf(r), y: 0 };
    commit();
  })));
  if ((r.routing || "any") === "xy" && r.pos) {
    grid.appendChild(field("X", numberInput(r.pos.x, 0, 31, (v) => {
      r.pos = { x: v, y: r.pos.y };
      commit();
    })));
    grid.appendChild(field("Y", numberInput(r.pos.y, 0, 31, (v) => {
      r.pos = { x: r.pos.x, y: v };
      commit();
    })));
  }
  box.appendChild(grid);
  box.appendChild(el("p", "meta-line",
    `Ports: in=${r.in} out=${r.out}. Attachment links are generated automatically.`));
  if ((r.routing || "any") === "xy") {
    box.appendChild(el("p", "meta-line",
      "XY turn model: X links may turn to Y, Y links stay in Y. Links must join Manhattan neighbours."));
  }
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete router", () => removeRouter(r.name)));
  box.appendChild(zone);
}

function inspectNet(box, kind, id) {
  const net = (kind === "mnet" ? ir.fabric.mnet : ir.fabric.snet).find((n) => n.name === id);
  if (!net) { selection = null; return renderInspector(); }
  const owner = ownership();
  const router = owner.get(net.name);
  const part = partitionOf(net.name);
  inspectHead(box, kind, net.name);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Name", textInput(net.name, (v) => renameNode(net.name, v))));
  grid.appendChild(field("Clock", clockSelect(net.clock, (v) => {
    if (part) setPartitionClock(part, v);
    else if (router) setLooseNodeClock(router, v);
    else net.clock = v;
    commit();
  })));
  box.appendChild(grid);
  box.appendChild(el("p", "meta-line",
    `Owner: ${router || "unassigned"} · endpoints: ${memberEndpoints(net.name).map((e) => e.id).join(", ") || "none"}`));
  if (!router) {
    box.appendChild(el("div", "warn", "This network is not attached to a router; select an owning router from a partition."));
  }
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton(`delete ${kind}`, () => removeNet(kind, net.name)));
  box.appendChild(zone);
}

function inspectEndpoint(box, id) {
  const e = ir.endpoints.find((x) => x.id === id);
  if (!e) { selection = null; return renderInspector(); }
  inspectHead(box, "endpoint", e.id);
  const nets = e.role === "master" ? ir.fabric.mnet : ir.fabric.snet;
  const grid = el("div", "field-grid");
  grid.appendChild(field("Id", textInput(e.id, (v) => renameEndpoint(e.id, v))));
  grid.appendChild(field("Role", selectInput(["master", "slave"], e.role, (v) => {
    const options = v === "master" ? ir.fabric.mnet : ir.fabric.snet;
    if (!options.length) {
      toast(`No ${v === "master" ? "MNet" : "SNet"} exists for this role.`);
      renderAll();
      return;
    }
    e.role = v;
    e.attach = options[0].name;
    const clock = clockOfNode(e.attach);
    if (clock) e.clock = clock;
    commit();
  })));
  grid.appendChild(field("Protocol", selectInput(PROTOCOLS, e.protocol, (v) => { e.protocol = v; commit(); })));
  grid.appendChild(field("QoS", selectInput(QOS, e.qos || "bulk", (v) => { e.qos = v; commit(); })));
  grid.appendChild(field("Outstanding", numberInput(e.outstanding || 1, 1, 256, (v) => {
    e.outstanding = v;
    commit();
  })));
  if (nets.length) {
    grid.appendChild(field("Attach", selectInput(nets.map((n) => n.name), e.attach, (v) => {
      e.attach = v;
      const clock = clockOfNode(v);
      if (clock) e.clock = clock;
      commit();
    })));
  } else {
    box.appendChild(el("div", "warn", `No ${e.role === "master" ? "MNet" : "SNet"} exists yet.`));
  }
  box.appendChild(grid);
  box.appendChild(el("p", "meta-line",
    `Clock: ${e.clock} (follows the attachment) · address ${e.addrWidth} · data ${e.dataWidth}`));
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete endpoint", () => removeEndpoint(e.id)));
  box.appendChild(zone);
}

function inspectLink(box, key) {
  const [from, to] = key.split(">");
  const l = ir.fabric.links.find((x) => x.from === from && x.to === to);
  if (!l) { selection = null; return renderInspector(); }
  inspectHead(box, "router link", key);
  const routers = routerNames();
  const grid = el("div", "field-grid");
  grid.appendChild(field("From", selectInput(routers, l.from, (v) => {
    l.from = v;
    selection = { kind: "link", id: v + ">" + l.to };
    commit();
  })));
  grid.appendChild(field("To", selectInput(routers, l.to, (v) => {
    l.to = v;
    selection = { kind: "link", id: l.from + ">" + v };
    commit();
  })));
  grid.appendChild(field("Crossing", selectInput(["sync", "async"], l.cdc, (v) => {
    l.cdc = v;
    if (v === "async") l.asyncFifoDepth = l.asyncFifoDepth || 4;
    commit();
  })));
  grid.appendChild(field("Pipe stages", numberInput(l.pipe, 0, 8, (v) => {
    l.pipe = v;
    commit();
  })));
  box.appendChild(grid);
  const a = clockOfNode(l.from);
  const b = clockOfNode(l.to);
  box.appendChild(el("p", "meta-line", `Clocks: ${a} → ${b}${a === b && l.cdc === "async" ? " (async on one domain is rejected by S1)" : ""}`));
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete link", () => removeLink(l.from, l.to)));
  box.appendChild(zone);
}

/* ------------------------------------------------------------------ */
/* mutations                                                           */
/* ------------------------------------------------------------------ */

function addClock() {
  const id = uniqueName("clk", ir.clockDomains.map((c) => c.id));
  ir.clockDomains.push({ id, freqMHz: 1000 });
  selection = { kind: "clock", id };
  commit();
}

function addPartition() {
  if (!ir.clockDomains.length) ir.clockDomains.push({ id: "core", freqMHz: 1000 });
  const name = uniqueName("part", ir.partitions.map((p) => p.name));
  const p = { name, clock: ir.clockDomains[0].id, members: [], boundaryPipe: 1 };
  for (const r of ir.fabric.routers) {
    if (!partitionOf(r.name)) {
      p.members.push(r.name);
      r.clock = p.clock;
    }
  }
  ir.partitions.push(p);
  selection = { kind: "partition", id: name };
  commit();
}

function addRouter(part) {
  if (!ir.clockDomains.length) {
    toast("Create a clock domain first.");
    return;
  }
  const name = uniqueName("r", allNodeNames());
  ir.fabric.routers.push({
    name,
    in: 1,
    out: 1,
    clock: part ? part.clock : ir.clockDomains[0].id,
    switch: { kind: "flat", banks: 1 }
  });
  if (part) part.members.push(name);
  selection = { kind: "router", id: name };
  commit();
}

function addNet(kind, router) {
  const list = kind === "mnet" ? ir.fabric.mnet : ir.fabric.snet;
  const name = uniqueName(kind, allNodeNames());
  list.push({ name, clock: router.clock });
  if (kind === "mnet") {
    ir.fabric.links.push({ from: name, to: router.name, width: widths.data, cdc: "sync", pipe: 0 });
  } else {
    ir.fabric.links.push({ from: router.name, to: name, width: widths.data, cdc: "sync", pipe: 0 });
  }
  selection = { kind, id: name };
  commit();
}

function findNet(name) {
  return ir.fabric.mnet.find((n) => n.name === name) || ir.fabric.snet.find((n) => n.name === name) || null;
}

function addEndpoint(explicitNet) {
  let netName = explicitNet || null;
  if (!netName && selection) {
    if (selection.kind === "mnet" || selection.kind === "snet") {
      netName = selection.id;
    } else if (selection.kind === "router") {
      const owner = ownership();
      const m = ir.fabric.mnet.find((n) => owner.get(n.name) === selection.id);
      const s = ir.fabric.snet.find((n) => owner.get(n.name) === selection.id);
      if (m || s) netName = (m || s).name;
    }
  }
  const net = netName ? findNet(netName) : null;
  const role = net
    ? (ir.fabric.mnet.includes(net) ? "master" : "slave")
    : (ir.fabric.mnet.length ? "master" : "slave");
  const nets = role === "master" ? ir.fabric.mnet : ir.fabric.snet;
  if (!nets.length) {
    toast(`Create a ${role === "master" ? "MNet" : "SNet"} first.`);
    return;
  }
  const masters = ir.endpoints.filter((e) => e.role === "master").length;
  const id = uniqueName(role === "master" ? "m" : "s", ir.endpoints.map((e) => e.id));
  const attach = net
    ? net.name
    : (nets.find((n) => !ir.endpoints.some((e) => e.attach === n.name)) || nets[0]).name;
  const e = {
    id,
    role,
    protocol: "native",
    dataWidth: widths.data,
    addrWidth: widths.addr,
    outstanding: 4,
    clock: clockOfNode(attach),
    attach
  };
  if (role === "master") e.qos = QOS[Math.min(2, masters % QOS.length)];
  ir.endpoints.push(e);
  selection = { kind: "endpoint", id };
  commit();
}

function removeClock(id) {
  const inUse = [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]
    .some((n) => n.clock === id)
    || ir.partitions.some((p) => p.clock === id)
    || ir.endpoints.some((e) => e.clock === id);
  if (inUse) {
    toast("Clock is in use. Reassign its nodes first.");
    return;
  }
  ir.clockDomains = ir.clockDomains.filter((c) => c.id !== id);
  if (selection && selection.kind === "clock" && selection.id === id) selection = null;
  commit();
}

function removePartition(name) {
  ir.partitions = ir.partitions.filter((p) => p.name !== name);
  if (selection && selection.kind === "partition" && selection.id === name) selection = null;
  commit();
}

function removeRouter(name) {
  const owner = ownership();
  const nets = [...ir.fabric.mnet, ...ir.fabric.snet]
    .filter((n) => owner.get(n.name) === name).map((n) => n.name);
  ir.fabric.mnet = ir.fabric.mnet.filter((m) => !nets.includes(m.name));
  ir.fabric.snet = ir.fabric.snet.filter((s) => !nets.includes(s.name));
  ir.endpoints = ir.endpoints.filter((e) => !nets.includes(e.attach));
  ir.fabric.links = ir.fabric.links.filter(
    (l) => l.from !== name && l.to !== name && !nets.includes(l.from) && !nets.includes(l.to)
  );
  ir.fabric.routers = ir.fabric.routers.filter((r) => r.name !== name);
  for (const p of ir.partitions) p.members = p.members.filter((m) => m !== name && !nets.includes(m));
  if (selection && (selection.id === name || nets.includes(selection.id))) selection = null;
  commit();
}

function removeNet(kind, name) {
  const list = kind === "mnet" ? ir.fabric.mnet : ir.fabric.snet;
  const keep = list.filter((n) => n.name !== name);
  if (kind === "mnet") ir.fabric.mnet = keep; else ir.fabric.snet = keep;
  ir.endpoints = ir.endpoints.filter((e) => e.attach !== name);
  ir.fabric.links = ir.fabric.links.filter((l) => l.from !== name && l.to !== name);
  for (const p of ir.partitions) p.members = p.members.filter((m) => m !== name);
  if (selection && selection.kind === kind && selection.id === name) selection = null;
  commit();
}

function removeEndpoint(id) {
  ir.endpoints = ir.endpoints.filter((e) => e.id !== id);
  if (selection && selection.kind === "endpoint" && selection.id === id) selection = null;
  commit();
}

function removeLink(from, to) {
  ir.fabric.links = ir.fabric.links.filter((l) => !(l.from === from && l.to === to));
  if (selection && selection.kind === "link" && selection.id === from + ">" + to) selection = null;
  commit();
}

function renameClock(oldId, newId) {
  if (!newId || newId === oldId) return;
  if (ir.clockDomains.some((c) => c.id === newId)) {
    toast("Clock id already exists.");
    renderAll();
    return;
  }
  for (const c of ir.clockDomains) if (c.id === oldId) c.id = newId;
  for (const p of ir.partitions) if (p.clock === oldId) p.clock = newId;
  for (const n of [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]) {
    if (n.clock === oldId) n.clock = newId;
  }
  for (const e of ir.endpoints) if (e.clock === oldId) e.clock = newId;
  selection = { kind: "clock", id: newId };
  commit();
}

function renamePartition(oldName, newName) {
  if (!newName || newName === oldName) return;
  if (ir.partitions.some((p) => p.name === newName)) {
    toast("Partition name already exists.");
    renderAll();
    return;
  }
  for (const p of ir.partitions) if (p.name === oldName) p.name = newName;
  selection = { kind: "partition", id: newName };
  commit();
}

function renameNode(oldName, newName) {
  if (!newName || newName === oldName) return;
  if (allNodeNames().some((n) => n === newName)) {
    toast("Node name already exists.");
    renderAll();
    return;
  }
  for (const n of [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]) {
    if (n.name === oldName) n.name = newName;
  }
  for (const l of ir.fabric.links) {
    if (l.from === oldName) l.from = newName;
    if (l.to === oldName) l.to = newName;
  }
  for (const e of ir.endpoints) if (e.attach === oldName) e.attach = newName;
  for (const p of ir.partitions) {
    p.members = p.members.map((m) => (m === oldName ? newName : m));
  }
  if (selection && selection.id === oldName) selection = { ...selection, id: newName };
  commit();
}

function renameEndpoint(oldId, newId) {
  if (!newId || newId === oldId) return;
  if (ir.endpoints.some((e) => e.id === newId)) {
    toast("Endpoint id already exists.");
    renderAll();
    return;
  }
  for (const e of ir.endpoints) if (e.id === oldId) e.id = newId;
  selection = { kind: "endpoint", id: newId };
  commit();
}

/* ------------------------------------------------------------------ */
/* result panels                                                       */
/* ------------------------------------------------------------------ */

function showGates(data) {
  const box = $("gates");
  clear(box);
  const problems = $("problems");
  clear(problems);
  const summary = $("gate-summary");
  summary.textContent = "";

  if (!data.checks) {
    summary.textContent = "rejected";
    box.appendChild(el("div", "empty", "Configuration rejected before gates."));
    (data.errors || []).forEach((e) => problems.appendChild(el("div", "err", e)));
    return;
  }
  let passed = 0;
  let total = 0;
  for (const [g, ok] of Object.entries(data.checks.gates)) {
    total += 1;
    if (ok) passed += 1;
    box.appendChild(el("div", "gate " + (ok ? "pass" : "fail"), g));
  }
  summary.textContent = `${passed}/${total} pass`;
  (data.errors || []).forEach((e) => problems.appendChild(el("div", "err", e)));
  (data.warnings || []).forEach((w) => problems.appendChild(el("div", "warn", w)));
}

function showEstimate(data) {
  const box = $("estimate");
  clear(box);
  if (!data.estimate) {
    box.appendChild(el("div", "empty", "not available"));
    return;
  }
  const table = el("table");
  for (const [k, v] of Object.entries(data.estimate.totals)) {
    const tr = el("tr");
    tr.appendChild(el("td", null, k));
    tr.appendChild(el("td", null, String(v)));
    table.appendChild(tr);
  }
  box.appendChild(table);
  const notes = (data.estimate.assumptions || []).slice(0, 3);
  box.appendChild(el("p", "hint", notes.join(" · ")));
}

function showTopology(data) {
  const box = $("topology");
  const legend = $("topology-legend");
  clear(box);
  clear(legend);
  if (!data.graph) {
    box.appendChild(el("div", "empty", "not available"));
    $("cdg").textContent = "";
    return;
  }
  const nodes = data.graph.nodes || [];
  const channels = data.graph.channels || [];
  const routers = new Set(routerNames());
  const asyncPairs = new Set(
    ir.fabric.links.filter((l) => l.cdc === "async").map((l) => `${l.from}>${l.to}`)
  );
  const clockOf = new Map();
  for (const n of [...ir.fabric.routers, ...ir.fabric.mnet, ...ir.fabric.snet]) {
    clockOf.set(n.name, n.clock);
  }
  const domains = [...new Set(ir.clockDomains.map((c) => c.id))].sort();

  const w = 130;
  const h = 26;
  const pos = new Map();
  const routerList = ir.fabric.routers;
  const useGrid = routerList.length > 0 && routerList.every((r) => r.pos);

  if (useGrid) {
    const owner = ownership();
    const minX = Math.min(...routerList.map((r) => r.pos.x));
    const minY = Math.min(...routerList.map((r) => r.pos.y));
    const maxX = Math.max(...routerList.map((r) => r.pos.x));
    const cellW = 170;
    const cellH = 92;
    const routerX0 = 210;
    for (const r of routerList) {
      pos.set(r.name, {
        x: routerX0 + (r.pos.x - minX) * cellW,
        y: 40 + (r.pos.y - minY) * cellH
      });
    }
    const rowY = (name) => {
      const rn = owner.get(name);
      const rp = rn ? pos.get(rn) : null;
      return rp ? rp.y : 40;
    };
    const placeColumn = (nets, x0) => {
      const byRow = new Map();
      for (const n of nets) {
        const y = rowY(n.name);
        if (!byRow.has(y)) byRow.set(y, []);
        byRow.get(y).push(n);
      }
      for (const [y0, list] of byRow) {
        list.sort((a, b) => a.name.localeCompare(b.name));
        list.forEach((n, i) => pos.set(n.name, { x: x0, y: y0 + i * (h + 8) }));
      }
    };
    placeColumn(ir.fabric.mnet, 30);
    placeColumn(ir.fabric.snet, routerX0 + (maxX - minX) * cellW + w + 70);
  } else {
    const layer = new Map(nodes.map((n) => [n, 0]));
    for (let it = 0; it < nodes.length; it += 1) {
      let changed = false;
      for (const c of channels) {
        if (!layer.has(c.from) || !layer.has(c.to)) continue;
        if (layer.get(c.to) < layer.get(c.from) + 1) {
          layer.set(c.to, layer.get(c.from) + 1);
          changed = true;
        }
      }
      if (!changed) break;
    }
    const groups = new Map();
    for (const n of nodes) {
      const l = layer.get(n) || 0;
      if (!groups.has(l)) groups.set(l, []);
      groups.get(l).push(n);
    }
    for (const [l, list] of [...groups.entries()].sort((a, b) => a[0] - b[0])) {
      list.sort();
      list.forEach((n, i) => pos.set(n, { x: 70 + l * 170, y: 40 + i * 46 }));
    }
  }
  const maxX = Math.max(320, ...[...pos.values()].map((p) => p.x + w + 30));
  const maxY = Math.max(120, ...[...pos.values()].map((p) => p.y + h + 20));
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", `0 0 ${maxX} ${maxY}`);
  const defs = document.createElementNS(ns, "defs");
  defs.innerHTML =
    '<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">' +
    '<path d="M0,0 L10,5 L0,10 z" fill="#8e8676"/></marker>' +
    '<marker id="arrow-async" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">' +
    '<path d="M0,0 L10,5 L0,10 z" fill="#a4532c"/></marker>';
  svg.appendChild(defs);

  for (const c of channels) {
    const a = pos.get(c.from);
    const b = pos.get(c.to);
    if (!a || !b) continue;
    const line = document.createElementNS(ns, "line");
    const async = asyncPairs.has(`${c.from}>${c.to}`);
    line.setAttribute("class", "edge" + (async ? " async" : ""));
    line.setAttribute("x1", String(a.x + w));
    line.setAttribute("y1", String(a.y + h / 2));
    line.setAttribute("x2", String(b.x - 2));
    line.setAttribute("y2", String(b.y + h / 2));
    line.setAttribute("marker-end", async ? "url(#arrow-async)" : "url(#arrow)");
    svg.appendChild(line);
  }
  for (const [name, p] of pos) {
    const rect = document.createElementNS(ns, "rect");
    rect.setAttribute("class", "node-shape" + (routers.has(name) ? " router" : ""));
    rect.setAttribute("x", String(p.x));
    rect.setAttribute("y", String(p.y));
    rect.setAttribute("width", String(w));
    rect.setAttribute("height", String(h));
    rect.setAttribute("rx", "4");
    rect.style.stroke = domainColor(clockOf.get(name));
    svg.appendChild(rect);
    const label = document.createElementNS(ns, "text");
    label.setAttribute("class", "node-label");
    label.setAttribute("x", String(p.x + w / 2));
    label.setAttribute("y", String(p.y + h / 2));
    label.textContent = name;
    svg.appendChild(label);
  }
  box.appendChild(svg);

  for (const d of domains) {
    const key = el("span", "key");
    const dot = el("span", "dot");
    dot.style.background = domainColor(d);
    key.appendChild(dot);
    key.appendChild(el("span", null, d));
    legend.appendChild(key);
  }
  if (asyncPairs.size) {
    const key = el("span", "key");
    key.appendChild(el("span", "dash"));
    key.appendChild(el("span", null, "async"));
    legend.appendChild(key);
  }
  if (ir.partitions.length) {
    legend.appendChild(el("span", "key", ir.partitions.map((p) => `${p.name}@${p.clock}`).join(" · ")));
  }
  $("cdg").textContent = data.graph.acyclic
    ? "CDG acyclic — constructively deadlock free"
    : "CDG has a cycle — rejected by G1";
}

/* ------------------------------------------------------------------ */
/* editor / examples                                                   */
/* ------------------------------------------------------------------ */

function adoptWidths() {
  const list = (ir && ir.endpoints) || [];
  const first = list.find((e) => e.role === "master") || list[0];
  if (first) {
    if (ADDR_WIDTHS.includes(first.addrWidth)) widths.addr = first.addrWidth;
    if (DATA_WIDTHS.includes(first.dataWidth)) widths.data = first.dataWidth;
  }
}

function parseEditor() {
  const prev = ir;
  try {
    ir = JSON.parse($("editor").value);
    ensureFabric();
    adoptWidths();
    normalize();
    syncEditor();
    renderAll();
    evaluate();
    scheduleDraftSave();
  } catch (e) {
    ir = prev;
    showProblem("JSON: " + e.message);
  }
}

async function loadExamples() {
  const res = await fetch("/api/examples");
  const list = await res.json();
  const sel = $("examples");
  sel.replaceChildren();
  for (const name of list) sel.appendChild(new Option(name.replace(/\.json$/, ""), name));
  if (list.includes("minimal_4x4.json")) sel.value = "minimal_4x4.json";
}

async function loadExample() {
  const name = $("examples").value;
  const res = await fetch("/api/example?name=" + encodeURIComponent(name));
  $("editor").value = await res.text();
  parseEditor();
}

function fileModeNotice() {
  const box = $("structure");
  clear(box);
  box.appendChild(el("div", "err",
    "This page must be served by Loom; opening index.html from disk disables the configuration API."));
  box.appendChild(el("div", "hint",
    "Run: ./src/loom/scripts/gui.sh   then open http://localhost:8000/"));
}

/* ------------------------------------------------------------------ */
/* wiring                                                              */
/* ------------------------------------------------------------------ */

async function main() {
  if (location.protocol === "file:") {
    fileModeNotice();
    return;
  }

  $("add-clock").addEventListener("click", addClock);
  $("add-partition").addEventListener("click", addPartition);
  $("add-endpoint").addEventListener("click", () => addEndpoint());
  $("addr-auto").addEventListener("click", autoWindows);
  $("evaluate").addEventListener("click", evaluate);
  $("load").addEventListener("click", loadExample);
  $("workspace").addEventListener("click", openWsDialog);
  $("ws-cancel").addEventListener("click", closeWsDialog);
  $("ws-apply").addEventListener("click", () => setWorkspace($("ws-path").value));
  $("ws-path").addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      setWorkspace($("ws-path").value);
    }
  });
  $("apply").addEventListener("click", parseEditor);
  $("canonical").addEventListener("click", () => {
    if (last && last.canonical) {
      $("editor").value = last.canonical;
      parseEditor();
    }
  });
  $("export").addEventListener("click", () => {
    const text = (last && last.canonical) || $("editor").value;
    const blob = new Blob([text], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = ((ir.meta || {}).name || "loom") + ".json";
    a.click();
    URL.revokeObjectURL(a.href);
  });
  $("design-name").addEventListener("change", () => {
    const v = $("design-name").value.trim();
    if (!v) {
      $("design-name").value = ir.meta.name;
      return;
    }
    ir.meta.name = v;
    commit();
  });
  $("addr-width").addEventListener("change", () => {
    widths.addr = Number($("addr-width").value);
    for (const e of ir.endpoints) e.addrWidth = widths.addr;
    commit();
  });
  $("data-width").addEventListener("change", () => {
    widths.data = Number($("data-width").value);
    for (const e of ir.endpoints) e.dataWidth = widths.data;
    for (const l of ir.fabric.links) l.width = widths.data;
    commit();
  });

  await loadExamples();
  let wsInfo = null;
  try {
    wsInfo = await (await fetch("/api/workspace")).json();
  } catch {
    wsInfo = null;
  }
  workspace = (wsInfo && wsInfo.workspace) || null;
  renderWorkspace();
  let restored = false;
  if (workspace && wsInfo.draft) restored = await adoptDraft();
  if (!restored) {
    if ($("examples").value) {
      await loadExample();
    } else {
      $("editor").value = "{}";
      parseEditor();
    }
  }
  if (!workspace) openWsDialog();
  requestAnimationFrame(() => document.body.classList.add("ready"));
}

main().catch((e) => showProblem("startup: " + e.message));
