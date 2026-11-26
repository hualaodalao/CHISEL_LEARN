import {
  KINDS, PROTOCOLS, ADDR_WIDTHS, DAT_WIDTHS, NODE_DATA_WIDTHS, CHANNELS,
  NODE_W, NODE_H, DEFAULT_QOS_CLASSES, DEFAULT_QOS_MAPPING,
  isRequest, isSubordinate, uniqueName, normalize as modelNormalize,
  parseAddress, pairKey, routerAdjacency as modelRouterAdjacency, hopCount,
  computeLayout as modelComputeLayout,
  renameObjectIn, removeNodeIn, removeRouterIn, kindAllowed as modelKindAllowed,
  samRows as modelSamRows
} from "./model.mjs";
import {
  postEvaluate, getExamples, getExample, getDraft, postDraft, getWorkspace, postWorkspace
} from "./api.mjs";

const $ = (id) => document.getElementById(id);

const DOMAIN_COLORS = ["#a4532c", "#2b6f63", "#7a5a1f", "#4a5470", "#8c3f56", "#3f6b8c"];

let ir = null;
let last = null;
let selection = null;
let upgradeWarnings = [];

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
/* model helpers (loom/v2) — pure core lives in model.js               */
/* ------------------------------------------------------------------ */

const routerNames = () => ir.fabric.routers.map((r) => r.name);
const isRouter = (name) => routerNames().includes(name);
const allObjectNames = () => [...routerNames(), ...ir.nodes.map((n) => n.id)];

function routerOf(name) {
  return ir.fabric.routers.find((r) => r.name === name) || null;
}

function nodeOf(id) {
  return ir.nodes.find((n) => n.id === id) || null;
}

function objectOf(name) {
  return routerOf(name) || nodeOf(name);
}

function clockOfObject(name) {
  const o = objectOf(name);
  return o ? o.clock : "";
}

function partitionOf(name) {
  return ir.partitions.find((p) => p.members.includes(name)) || null;
}

function normalize() {
  modelNormalize(ir);
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
    const data = await postEvaluate($("editor").value);
    if (seq !== evalSeq) return;
    last = data;
    $("hash").textContent = data.hash ? data.hash.slice(0, 12) : "";
    showGates(data);
    showEstimate(data);
    showSam(data);
    showNodesReport(data);
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
  const { ok: draftOk, text } = await getDraft();
  if (!draftOk) return false;
  try {
    JSON.parse(text);
  } catch {
    toast("draft in workspace is not valid JSON");
    return false;
  }
  $("editor").value = text;
  const ok = await parseEditor();
  if (ok) {
    lastSaved = $("editor").value;
    toast("restored draft from workspace");
  }
  return ok;
}

async function setWorkspace(path) {
  const trimmed = String(path || "").trim();
  if (!trimmed) return;
  try {
    const { ok, data } = await postWorkspace(trimmed);
    if (!ok) {
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
    const ok = await postDraft(text);
    if (!ok) return;
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
  renderChi();
  renderQos();
  renderClocks();
  renderStructure();
  renderLinks();
  renderNodes();
  renderSam();
  renderMatrix();
  renderInspector();
  renderSummary();
}

function renderSummary() {
  const parts = [
    `${ir.clockDomains.length} clocks`,
    `${ir.partitions.length} partitions`,
    `${ir.fabric.routers.length} routers`,
    `${ir.nodes.length} nodes`
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
    for (const w of DAT_WIDTHS) data.appendChild(new Option(String(w), String(w)));
  }
  const snapshot = (last && last.chi) || {};
  addr.value = String(ir.chi.addrWidth != null ? ir.chi.addrWidth : (snapshot.addrWidth || 48));
  data.value = String(ir.chi.datWidth != null ? ir.chi.datWidth : (snapshot.datWidth || 256));
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
  for (const o of [...ir.fabric.routers, ...ir.nodes]) {
    usage.set(o.clock, (usage.get(o.clock) || 0) + 1);
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

function clockSelect(value, onChange) {
  return selectInput(ir.clockDomains.map((c) => c.id), value, onChange);
}

/* ------------------------------------------------------------------ */
/* structure rendering (partition -> router -> node)                   */
/* ------------------------------------------------------------------ */

function nodesOnRouter(name) {
  return ir.nodes.filter((n) => n.attach === name);
}

function assignedNodeId(id) {
  const rep = last && last.nodes;
  if (!rep) return null;
  for (const grp of ["rn", "sn", "hn", "mn"]) {
    const hit = (rep[grp] || []).find((x) => x.id === id);
    if (hit) return hit.nodeId;
  }
  return null;
}

function derivedPorts(name) {
  const rep = last && last.estimate && last.estimate.routers;
  const hit = rep && rep.find((r) => r.name === name);
  if (hit) return { in: hit.in, out: hit.out };
  const nLocal = nodesOnRouter(name).length;
  const links = ir.fabric.links.filter((l) => isRouter(l.from) && isRouter(l.to));
  return {
    in: nLocal + links.filter((l) => l.to === name).length,
    out: nLocal + links.filter((l) => l.from === name).length
  };
}

function renderStructure() {
  const box = $("structure");
  clear(box);
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
    for (const r of routers) card.appendChild(routerCard(r));
    box.appendChild(card);
  }

  if (flat.length) {
    if (ir.partitions.length) {
      box.appendChild(el("div", "flat-note", "Unassigned routers (add them to a partition):"));
    }
    for (const r of flat) box.appendChild(routerCard(r));
  }

  if (!ir.fabric.routers.length) {
    box.appendChild(el("div", "empty", "No routers yet. Create a partition, then add a router inside it."));
  }
}

function routerCard(r) {
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
  const attached = nodesOnRouter(r.name);
  const rn = attached.filter(isRequest).length;
  const sn = attached.filter(isSubordinate).length;
  row.appendChild(el("span", "counts", `${rn} RN · ${sn} SN`));

  const actions = el("span", "row-actions");
  const kindSel = selectInput(KINDS, "rn-i", null);
  kindSel.title = "node kind to create on this router";
  const addN = el("button", "ghost", "+ node");
  addN.type = "button";
  addN.addEventListener("click", () => addNode(kindSel.value, r.name));
  actions.appendChild(kindSel);
  actions.appendChild(addN);
  actions.appendChild(dangerButton("remove", () => removeRouter(r.name)));
  row.appendChild(actions);
  wrap.appendChild(row);

  const kids = el("div");
  for (const n of attached) kids.appendChild(nodeRow(n));
  if (!attached.length) kids.appendChild(el("div", "hint", "No nodes on this router."));
  wrap.appendChild(kids);
  return wrap;
}

function nodeRow(n) {
  const row = el("div", "node member");
  if (selection && selection.kind === "node" && selection.id === n.id) row.classList.add("selected");
  const sw = el("span", "swatch");
  sw.style.setProperty("--domain", domainColor(n.clock));
  row.appendChild(sw);
  const name = el("button", "name-btn", n.id);
  name.type = "button";
  name.addEventListener("click", () => {
    selection = { kind: "node", id: n.id };
    renderAll();
  });
  row.appendChild(name);
  row.appendChild(el("span", "chip", n.kind));
  const nid = assignedNodeId(n.id);
  row.appendChild(el("span", "counts", nid == null ? "nodeId —" : `nodeId ${nid}`));
  row.appendChild(dangerButton("remove", () => removeNode(n.id)));
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
    if (l.dateline) main.appendChild(el("span", "chip", "dateline"));
    const ch = (l.channels || ["req", "rsp", "dat"]).join(",");
    main.appendChild(el("span", "counts", `pipe ${l.pipe != null ? l.pipe : 0} · [${ch}]`));
    row.appendChild(main);

    const controls = el("div", "link-controls");
    controls.appendChild(selectInput(["sync", "async"], l.cdc || "sync", (v) => {
      l.cdc = v;
      if (v === "async" && l.asyncFifoDepth == null) l.asyncFifoDepth = 2;
      commit();
    }));
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
    addLink(from.value, to.value, cdc.value);
  });
  box.appendChild(field("From router", from));
  box.appendChild(field("To router", to));
  box.appendChild(field("Crossing", cdc));
  box.appendChild(add);
  box.lastChild.classList.add("span-2");
  box.appendChild(el("div", "hint span-2", "async requires the two routers to sit in different clock domains."));
  box.appendChild(el("div", "hint span-2", "A closed router cycle needs a dateline cut (simple ring) or XY turn model, else G1 rejects."));
}

function renderNodes() {
  const box = $("nodes");
  clear(box);
  let list = ir.nodes;
  let ctx = null;
  if (selection && selection.kind === "router") {
    ctx = selection.id;
    list = nodesOnRouter(ctx);
  }
  if (ctx) {
    const head = el("div", "ep-context");
    head.appendChild(el("span", "chip", `on ${ctx} · ${list.length}`));
    const all = el("button", "ghost", "show all");
    all.type = "button";
    all.addEventListener("click", () => { selection = null; renderAll(); });
    head.appendChild(all);
    box.appendChild(head);
  }
  if (!list.length) {
    box.appendChild(el("div", "empty", ctx ? `No nodes on ${ctx}.` : "No nodes yet."));
    return;
  }
  const table = el("table", "ep-table");
  const head = el("tr");
  for (const h of ["id", "kind", "nodeId", "attach", "qos", "outs"]) head.appendChild(el("th", null, h));
  table.appendChild(head);
  for (const n of list) {
    const tr = el("tr", "ep-row");
    if (selection && selection.kind === "node" && selection.id === n.id) tr.classList.add("selected");
    tr.appendChild(el("td", null, n.id));
    tr.appendChild(el("td", "role" + (isSubordinate(n) ? " slave" : ""), n.kind));
    const nid = assignedNodeId(n.id);
    tr.appendChild(el("td", null, nid == null ? "—" : String(nid)));
    tr.appendChild(el("td", null, n.attach));
    tr.appendChild(el("td", null, n.qos != null ? String(n.qos) : "0"));
    tr.appendChild(el("td", null, String(n.outstanding != null ? n.outstanding : ((last && last.chi && last.chi.outstanding) || 64))));
    tr.addEventListener("click", () => {
      selection = { kind: "node", id: n.id };
      renderAll();
    });
    table.appendChild(tr);
  }
  box.appendChild(table);
}

function routerAdjacency() {
  return modelRouterAdjacency(ir);
}

function renderMatrix() {
  const box = $("matrix");
  clear(box);
  const rns = ir.nodes.filter(isRequest);
  const sns = ir.nodes.filter(isSubordinate);
  if (!rns.length || !sns.length) {
    box.appendChild(el("tr")).appendChild(el("td", "na", "Needs at least one RN and one SN."));
    return;
  }
  const adj = routerAdjacency();
  const head = el("tr");
  head.appendChild(el("th", "row-head", "RN \\ SN"));
  for (const s of sns) head.appendChild(el("th", null, s.id));
  box.appendChild(head);
  for (const m of rns) {
    const tr = el("tr");
    tr.appendChild(el("th", "row-head", m.id));
    for (const s of sns) {
      const hops = hopCount(adj, m.attach, s.attach);
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

/* ------------------------------------------------------------------ */
/* inspector                                                           */
/* ------------------------------------------------------------------ */

function renderInspector() {
  const box = $("inspector");
  clear(box);
  if (!selection) {
    box.appendChild(el("h2", null, "Inspector"));
    box.appendChild(el("p", "hint",
      "Select a clock, partition, router, node or link to edit it."));
    return;
  }
  const { kind, id } = selection;
  if (kind === "clock") inspectClock(box, id);
  else if (kind === "partition") inspectPartition(box, id);
  else if (kind === "router") inspectRouter(box, id);
  else if (kind === "node") inspectNode(box, id);
  else if (kind === "link") inspectLink(box, id);
  else {
    box.appendChild(el("h2", null, "Inspector"));
    box.appendChild(el("p", "hint", "SAM editing arrives with G3."));
  }
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
  const users = [...ir.fabric.routers, ...ir.nodes].filter((o) => o.clock === c.id).length
    + ir.partitions.filter((p) => p.clock === c.id).length;
  box.appendChild(el("p", "meta-line", `Used by ${users} object(s)/partition(s).`));
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
  grid.appendChild(field("Boundary pipe", numberInput(p.boundaryPipe != null ? p.boundaryPipe : 1, 1, 8, (v) => {
    p.boundaryPipe = v;
    commit();
  })));
  box.appendChild(grid);
  const routers = ir.fabric.routers.filter((r) => p.members.includes(r.name)).map((r) => r.name);
  box.appendChild(el("p", "meta-line", `Routers: ${routers.length ? routers.join(", ") : "none"}`));
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete partition", () => removePartition(p.name)));
  box.appendChild(zone);
}

function inspectRouter(box, id) {
  const r = routerOf(id);
  if (!r) { selection = null; return renderInspector(); }
  inspectHead(box, "router", r.name);
  const part = partitionOf(r.name);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Name", textInput(r.name, (v) => renameObject(r.name, v))));
  grid.appendChild(field("Clock", clockSelect(r.clock, (v) => {
    if (part) setPartitionClock(part, v);
    else setLooseObjectClock(r.name, v);
    commit();
  })));
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
  const ports = derivedPorts(r.name);
  box.appendChild(el("p", "meta-line",
    `Derived ports: in=${ports.in} out=${ports.out} (CHK-31). CHI router is flat; attachment links are generated automatically.`));
  if ((r.routing || "any") === "xy") {
    box.appendChild(el("p", "meta-line",
      "XY turn model: X links may turn to Y, Y links stay in Y. Links must join Manhattan neighbours."));
  }
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete router", () => removeRouter(r.name)));
  box.appendChild(zone);
}

function inspectNode(box, id) {
  const n = nodeOf(id);
  if (!n) { selection = null; return renderInspector(); }
  inspectHead(box, "node", n.id);
  const grid = el("div", "field-grid");
  grid.appendChild(field("Id", textInput(n.id, (v) => renameObject(n.id, v))));
  grid.appendChild(field("Kind", selectInput(KINDS, n.kind, (v) => {
    if (!kindAllowed(v, n.id)) { renderAll(); return; }
    n.kind = v;
    commit();
  })));
  grid.appendChild(field("NodeID", textInput(n.nodeId != null ? String(n.nodeId) : "", (v) => {
    if (v === "") delete n.nodeId;
    else {
      const num = Number(v);
      if (Number.isInteger(num) && num >= 0) n.nodeId = num;
    }
    commit();
  })));
  grid.appendChild(field("Attach", selectInput(routerNames(), n.attach, (v) => {
    n.attach = v;
    const r = routerOf(v);
    if (r) n.clock = r.clock;
    moveNodeToRouterPartition(n);
    commit();
  })));
  const chiSnap = (last && last.chi) || {};
  grid.appendChild(field("Protocol", selectInput(PROTOCOLS, n.protocol || (chiSnap.portMode === "chi" ? "chi" : "axi4"), (v) => {
    n.protocol = v;
    commit();
  })));
  grid.appendChild(field("Data width (AXI)", selectInput(NODE_DATA_WIDTHS, n.dataWidth != null ? n.dataWidth : (chiSnap.datWidth || 256), (v) => {
    n.dataWidth = v;
    commit();
  }, true)));
  grid.appendChild(field("Outstanding", numberInput(n.outstanding != null ? n.outstanding : (chiSnap.outstanding || 64), 1, 1 << (chiSnap.txnIdWidth || 8), (v) => {
    n.outstanding = v;
    commit();
  })));
  grid.appendChild(field("QoS (0–15)", numberInput(n.qos != null ? n.qos : 0, 0, 15, (v) => {
    n.qos = v;
    commit();
  })));
  box.appendChild(grid);
  const nid = assignedNodeId(n.id);
  box.appendChild(el("p", "meta-line",
    `Clock: ${n.clock} (follows attach router) · assigned NodeID: ${nid == null ? "—" : nid}`));
  if (n.kind === "rn-d") {
    box.appendChild(el("div", "warn", "rn-d requires exactly one mn node and chi.dvmEnable=true (CHK-19)."));
  }
  const zone = el("div", "danger-zone");
  zone.appendChild(dangerButton("delete node", () => removeNode(n.id)));
  box.appendChild(zone);
}

function inspectLink(box, key) {
  const [from, to] = key.split(">");
  const l = ir.fabric.links.find((x) => x.from === from && x.to === to);
  if (!l) { selection = null; return renderInspector(); }
  inspectHead(box, "router link", key);
  const routers = routerNames();
  const chiSnap = (last && last.chi) || {};
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
  grid.appendChild(field("Crossing", selectInput(["sync", "async"], l.cdc || "sync", (v) => {
    l.cdc = v;
    if (v === "async" && l.asyncFifoDepth == null) l.asyncFifoDepth = 2;
    commit();
  })));
  grid.appendChild(field("Pipe stages", numberInput(l.pipe != null ? l.pipe : 0, 0, 8, (v) => {
    l.pipe = v;
    commit();
  })));
  if ((l.cdc || "sync") === "async") {
    grid.appendChild(field("Async FIFO depth", numberInput(l.asyncFifoDepth != null ? l.asyncFifoDepth : 2, 2, 64, (v) => {
      l.asyncFifoDepth = v;
      commit();
    })));
  }
  grid.appendChild(field("L-Credit depth", numberInput(l.lcreditDepth != null ? l.lcreditDepth : (chiSnap.lcreditDepth || 4), 1, 15, (v) => {
    l.lcreditDepth = v;
    commit();
  })));
  box.appendChild(grid);

  const chBox = el("div", "field-grid");
  const current = new Set(l.channels || ["req", "rsp", "dat"]);
  const dvm = ir.chi.dvmEnable != null ? ir.chi.dvmEnable : (chiSnap.dvmEnable !== false);
  for (const c of CHANNELS) {
    const lab = el("label");
    const cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = current.has(c);
    if (c !== "snp") cb.disabled = true;
    if (c === "snp" && !dvm) cb.disabled = true;
    cb.addEventListener("change", () => {
      const set = new Set(l.channels || ["req", "rsp", "dat"]);
      if (cb.checked) set.add(c); else set.delete(c);
      l.channels = CHANNELS.filter((x) => set.has(x));
      commit();
    });
    lab.appendChild(cb);
    lab.appendChild(el("span", null, c + (c !== "snp" ? " (required)" : (dvm ? "" : " (dvm off)"))));
    chBox.appendChild(lab);
  }
  box.appendChild(el("p", "meta-line", "Channels (req/rsp/dat required; snp only when dvmEnable):"));
  box.appendChild(chBox);

  const dl = el("label");
  const dlcb = document.createElement("input");
  dlcb.type = "checkbox";
  dlcb.checked = !!l.dateline;
  dlcb.addEventListener("change", () => {
    if (dlcb.checked) l.dateline = true; else delete l.dateline;
    commit();
  });
  dl.appendChild(dlcb);
  dl.appendChild(el("span", null, "dateline (ring cut; must be a router↔router ring member, CHK-37)"));
  box.appendChild(dl);

  const a = clockOfObject(l.from);
  const b = clockOfObject(l.to);
  box.appendChild(el("p", "meta-line",
    `Clocks: ${a} → ${b} · width = chi.datWidth (${chiSnap.datWidth || 256})`));
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

function removeClock(id) {
  const inUse = [...ir.fabric.routers, ...ir.nodes].some((o) => o.clock === id)
    || ir.partitions.some((p) => p.clock === id);
  if (inUse) {
    toast("Clock is in use. Reassign its objects first.");
    return;
  }
  ir.clockDomains = ir.clockDomains.filter((c) => c.id !== id);
  if (selection && selection.kind === "clock" && selection.id === id) selection = null;
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
  for (const o of [...ir.fabric.routers, ...ir.nodes]) {
    if (o.clock === oldId) o.clock = newId;
  }
  selection = { kind: "clock", id: newId };
  commit();
}

function setPartitionClock(part, clockId) {
  part.clock = clockId;
  for (const name of part.members) {
    const o = objectOf(name);
    if (o) o.clock = clockId;
  }
}

function setLooseObjectClock(name, clockId) {
  const o = objectOf(name);
  if (!o) return;
  o.clock = clockId;
  if (isRouter(name)) {
    for (const n of nodesOnRouter(name)) n.clock = clockId;
  }
}

function addPartition() {
  if (!ir.clockDomains.length) ir.clockDomains.push({ id: "core", freqMHz: 1000 });
  const name = uniqueName("part", ir.partitions.map((p) => p.name));
  const p = { name, clock: ir.clockDomains[0].id, members: [] };
  for (const r of ir.fabric.routers) {
    if (!partitionOf(r.name)) {
      p.members.push(r.name);
      r.clock = p.clock;
      for (const n of nodesOnRouter(r.name)) {
        p.members.push(n.id);
        n.clock = p.clock;
      }
    }
  }
  ir.partitions.push(p);
  selection = { kind: "partition", id: name };
  commit();
}

function removePartition(name) {
  ir.partitions = ir.partitions.filter((p) => p.name !== name);
  if (selection && selection.kind === "partition" && selection.id === name) selection = null;
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

function addRouter(part) {
  if (!ir.clockDomains.length) {
    toast("Create a clock domain first.");
    return;
  }
  const name = uniqueName("r", allObjectNames());
  ir.fabric.routers.push({
    name,
    clock: part ? part.clock : ir.clockDomains[0].id
  });
  if (part) part.members.push(name);
  selection = { kind: "router", id: name };
  commit();
}

function removeRouter(name) {
  removeRouterIn(ir, name);
  if (selection && selection.id === name) selection = null;
  commit();
}

function kindAllowed(kind, excludeId) {
  const chiSnap = (last && last.chi) || {};
  const dvm = ir.chi.dvmEnable != null ? ir.chi.dvmEnable : (chiSnap.dvmEnable !== false);
  const res = modelKindAllowed(ir, kind, excludeId, dvm);
  if (!res.allowed) toast(res.reason);
  return res.allowed;
}

function addNode(kind, routerName) {
  const r = routerOf(routerName);
  if (!r) { toast("Select a router first."); return; }
  if (!kindAllowed(kind, null)) return;
  const prefix = kind.split("-")[0];
  const id = uniqueName(prefix, ir.nodes.map((n) => n.id));
  const n = { id, kind, clock: r.clock, attach: r.name };
  ir.nodes.push(n);
  const part = partitionOf(r.name);
  if (part) part.members.push(id);
  if (kind === "rn-d" && !ir.nodes.some((x) => x.kind === "mn")) {
    toast("rn-d requires exactly one mn node; add an mn (CHK-19).");
  }
  selection = { kind: "node", id };
  commit();
}

function moveNodeToRouterPartition(n) {
  for (const p of ir.partitions) p.members = p.members.filter((m) => m !== n.id);
  const part = partitionOf(n.attach);
  if (part) part.members.push(n.id);
}

function removeNode(id) {
  removeNodeIn(ir, id);
  if (selection && selection.kind === "node" && selection.id === id) selection = null;
  commit();
}

function renameObject(oldName, newName) {
  if (!newName || newName === oldName) return;
  if (allObjectNames().includes(newName)) {
    toast("Name already exists.");
    renderAll();
    return;
  }
  renameObjectIn(ir, oldName, newName);
  if (selection && selection.id === oldName) selection = { ...selection, id: newName };
  commit();
}

function addLink(from, to, cdc) {
  const l = { from, to, cdc: cdc === "async" ? "async" : "sync", pipe: 1 };
  if (l.cdc === "async") l.asyncFifoDepth = 2;
  ir.fabric.links.push(l);
  selection = { kind: "link", id: from + ">" + to };
  commit();
}

function removeLink(from, to) {
  ir.fabric.links = ir.fabric.links.filter((l) => !(l.from === from && l.to === to));
  if (selection && selection.kind === "link" && selection.id === from + ">" + to) selection = null;
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

  if (data.upgraded || upgradeWarnings.length) {
    problems.appendChild(el("div", "warn",
      "loom/v1 input was auto-upgraded to loom/v2 (MIG-01/07); details below."));
    upgradeWarnings.forEach((w) => problems.appendChild(el("div", "warn", w)));
  }
  if (!data.checks) {
    summary.textContent = "rejected";
    box.appendChild(el("div", "empty", "Configuration rejected before gates."));
    (data.errors || []).forEach((e) => problems.appendChild(el("div", "err", e)));
    (data.warnings || []).forEach((w) => problems.appendChild(el("div", "warn", w)));
    return;
  }
  let passed = 0;
  let total = 0;
  for (const [g, ok] of Object.entries(data.checks.gates)) {
    if (g === "G7") continue; /* retired gate, hidden (GUI-OQ-02) */
    total += 1;
    if (ok) passed += 1;
    const chip = el("div", "gate " + (ok ? "pass" : "fail"), g);
    if (g === "G1-SNP") {
      const snp = data.nodes && data.nodes.snpEnable;
      chip.title = snp ? "SNP channel proved" : "trivially true — SNP not generated (dvmEnable=false)";
    }
    box.appendChild(chip);
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
  const totals = data.estimate.totals || {};
  const groups = [
    ["Scale", ["routers", "links", "crosspoints"]],
    ["CHI", ["datWidth", "txnIdWidth", "nodeIdWidth", "addrWidth", "flitCount64B", "maxNodes"]],
    ["Proxies", ["maxLinkCapacityGBps", "latencyProxyCycles", "areaProxyUm2", "sramBits", "sramBlocks", "sramAreaProxyUm2", "totalAreaProxyUm2"]]
  ];
  const shown = new Set();
  const table = el("table");
  for (const [title, keys] of groups) {
    const hr = el("tr");
    const th = el("td", "group-head", title);
    th.colSpan = 2;
    hr.appendChild(th);
    table.appendChild(hr);
    for (const k of keys) {
      if (!(k in totals)) continue;
      shown.add(k);
      const tr = el("tr");
      tr.appendChild(el("td", null, k));
      tr.appendChild(el("td", null, String(totals[k])));
      table.appendChild(tr);
    }
  }
  for (const [k, v] of Object.entries(totals)) {
    if (shown.has(k)) continue;
    const tr = el("tr");
    tr.appendChild(el("td", null, k));
    tr.appendChild(el("td", null, String(v)));
    table.appendChild(tr);
  }
  box.appendChild(table);
  const notes = (data.estimate.assumptions || []).slice(0, 3);
  box.appendChild(el("p", "hint", notes.join(" · ")));
}

/* CHI node/SAM report (GEN-17 chi_nodes.json caliber). localPort is
 * elaboration-owned and absent in the IR-only fallback (gate failures). */
function showNodesReport(data) {
  const box = $("nodes-report");
  clear(box);
  const rep = data.nodes;
  if (!rep) {
    box.appendChild(el("div", "empty", "not available"));
    return;
  }
  const chLine = el("p", "meta-line",
    `Channels: ${(data.channels || []).join(", ") || "—"} · SNP ${rep.snpEnable ? "generated" : "not generated (dvmEnable=false)"}`);
  box.appendChild(chLine);
  for (const grp of ["rn", "sn", "hn", "mn"]) {
    const arr = rep[grp] || [];
    if (!arr.length) continue;
    box.appendChild(el("p", "kind", grp.toUpperCase()));
    const table = el("table", "ep-table");
    const head = el("tr");
    for (const h of ["id", "kind", "nodeId", "attach", "localPort", "clock"]) head.appendChild(el("th", null, h));
    table.appendChild(head);
    for (const n of arr) {
      const tr = el("tr");
      tr.appendChild(el("td", null, n.id));
      tr.appendChild(el("td", null, n.kind));
      tr.appendChild(el("td", null, String(n.nodeId)));
      tr.appendChild(el("td", null, n.attach != null ? n.attach : "—"));
      tr.appendChild(el("td", null, n.localPort != null ? String(n.localPort) : "—"));
      tr.appendChild(el("td", null, n.clock != null ? n.clock : "—"));
      table.appendChild(tr);
    }
    box.appendChild(table);
  }
  if (Array.isArray(rep.sam) && rep.sam.length) {
    box.appendChild(el("p", "kind", "SAM"));
    const table = el("table", "ep-table");
    const head = el("tr");
    for (const h of ["base", "size", "tgtNode", "tgtId"]) head.appendChild(el("th", null, h));
    table.appendChild(head);
    for (const s of rep.sam) {
      const tr = el("tr");
      tr.appendChild(el("td", null, "0x" + Number(s.base).toString(16)));
      tr.appendChild(el("td", null, String(s.size)));
      tr.appendChild(el("td", null, s.tgtNode));
      tr.appendChild(el("td", null, String(s.tgtId)));
      table.appendChild(tr);
    }
    box.appendChild(table);
  }
}

/* SAM editor (IR-22–24). Derived mode previews the server 4 KB layout;
 * Customize materializes ir.sam for editing; auto 4K drops back to derived. */
function showSam(data) {
  renderSam();
}

function subordinateIds() {
  return ir.nodes.filter(isSubordinate).map((n) => n.id);
}

function samRows() {
  return modelSamRows(ir, last && last.addressMap);
}

function renderSam() {
  const box = $("address-map");
  const note = $("addr-note");
  clear(box);
  const explicit = Array.isArray(ir.sam);
  const sns = subordinateIds();
  if (!sns.length) {
    note.textContent = "";
    box.appendChild(el("div", "empty", "Add an SN node (sn-f/sn-i) first."));
    return;
  }
  const rows = samRows();
  const table = el("table", "ep-table addr-table");
  const head = el("tr");
  for (const h of ["tgtNode", "base", "size", "end", ""]) head.appendChild(el("th", null, h));
  table.appendChild(head);
  rows.forEach((w, idx) => {
    const tr = el("tr", "ep-row");
    const tgt = el("td");
    if (explicit) {
      tgt.appendChild(selectInput(sns, w.tgtNode, (v) => { w.tgtNode = v; commit(); }));
    } else {
      tgt.textContent = w.tgtNode;
    }
    tr.appendChild(tgt);
    const baseCell = el("td");
    const sizeCell = el("td");
    if (explicit) {
      baseCell.appendChild(textInput("0x" + Number(w.base).toString(16), (v) => {
        const num = parseAddress(v);
        if (!Number.isFinite(num)) { toast("base must be a number, hex, or e.g. 64K"); renderAll(); return; }
        w.base = num;
        commit();
      }));
      sizeCell.appendChild(textInput(String(w.size), (v) => {
        const num = parseAddress(v);
        if (!Number.isFinite(num)) { toast("size must be a number, hex, or e.g. 64K"); renderAll(); return; }
        w.size = num;
        commit();
      }));
    } else {
      baseCell.textContent = "0x" + Number(w.base).toString(16);
      sizeCell.textContent = String(w.size);
    }
    tr.appendChild(baseCell);
    tr.appendChild(sizeCell);
    tr.appendChild(el("td", null, "0x" + (Number(w.base) + Number(w.size) - 1).toString(16)));
    const act = el("td");
    if (explicit) act.appendChild(dangerButton("×", () => removeSam(idx)));
    tr.appendChild(act);
    table.appendChild(tr);
  });
  box.appendChild(table);

  const tools = el("div", "row");
  if (explicit) {
    const add = el("button", "ghost", "+ window");
    add.type = "button";
    add.addEventListener("click", addSam);
    tools.appendChild(add);
    note.textContent = "Explicit SAM windows: aligned, non-overlapping, every SN covered (CHK-16/29/30).";
  } else {
    const customize = el("button", "ghost", "customize");
    customize.type = "button";
    customize.addEventListener("click", customizeSam);
    tools.appendChild(customize);
    note.textContent = "Derived 4 KB windows in SN order. Customize to edit, or auto 4K to reset.";
  }
  box.appendChild(tools);
}

function customizeSam() {
  ir.sam = samRows().map((w) => ({ base: Number(w.base), size: Number(w.size), tgtNode: w.tgtNode }));
  commit();
}

function addSam() {
  if (!Array.isArray(ir.sam)) customizeSam();
  const sns = subordinateIds();
  const rows = ir.sam;
  const maxEnd = rows.reduce((m, w) => Math.max(m, Number(w.base) + Number(w.size)), 0);
  const base = Math.ceil(maxEnd / 4096) * 4096;
  const covered = new Set(rows.map((w) => w.tgtNode));
  const tgt = sns.find((id) => !covered.has(id)) || sns[0];
  rows.push({ base, size: 4096, tgtNode: tgt });
  commit();
}

function removeSam(idx) {
  ir.sam.splice(idx, 1);
  commit();
}

function autoWindows() {
  delete ir.sam;
  commit();
}

/* ------------------------------------------------------------------ */
/* CHI config block (IR-35–45)                                         */
/* ------------------------------------------------------------------ */

function checkboxInput(checked, onChange) {
  const i = document.createElement("input");
  i.type = "checkbox";
  i.checked = !!checked;
  i.addEventListener("change", () => onChange(i.checked));
  return i;
}

function chiValue(key, fallback) {
  if (ir.chi[key] != null) return ir.chi[key];
  const snap = (last && last.chi) || {};
  return snap[key] != null ? snap[key] : fallback;
}

function setChi(key, value) {
  ir.chi[key] = value;
  commit();
}

function renderChi() {
  const box = $("chi");
  clear(box);
  const snap = (last && last.chi) || {};
  $("chi-derived").textContent =
    `BE ${snap.beWidth != null ? snap.beWidth : "—"} · flit/64B ${snap.flitCount64B != null ? snap.flitCount64B : "—"} · maxNodes ${snap.maxNodes != null ? snap.maxNodes : "—"}`;

  box.appendChild(field("DAT width", selectInput(DAT_WIDTHS, chiValue("datWidth", 256), (v) => {
    setChi("datWidth", v);
    for (const l of ir.fabric.links) if (l.width != null) l.width = v;
  }, true)));
  box.appendChild(field("TxnID width", selectInput([8, 12], chiValue("txnIdWidth", 8), (v) => setChi("txnIdWidth", v), true)));
  box.appendChild(field("NodeID width", selectInput([7, 11], chiValue("nodeIdWidth", 7), (v) => setChi("nodeIdWidth", v), true)));
  box.appendChild(field("Addr width", selectInput(ADDR_WIDTHS, chiValue("addrWidth", 48), (v) => setChi("addrWidth", v), true)));
  box.appendChild(field("Outstanding", numberInput(chiValue("outstanding", 64), 1, 1 << chiValue("txnIdWidth", 8), (v) => setChi("outstanding", v))));
  box.appendChild(field("Atomic enable", checkboxInput(chiValue("atomicEnable", true), (v) => setChi("atomicEnable", v))));
  box.appendChild(field("Atomic entries", numberInput(chiValue("atomicEntries", 4), 1, 256, (v) => setChi("atomicEntries", v))));
  box.appendChild(field("DVM enable", checkboxInput(chiValue("dvmEnable", true), (v) => {
    if (!v) {
      const snpLinks = ir.fabric.links.filter((l) => (l.channels || []).includes("snp")).length;
      const dvmNodes = ir.nodes.filter((n) => n.kind === "rn-d" || n.kind === "mn").length;
      if (snpLinks || dvmNodes) {
        toast(`dvmEnable=false will reject ${snpLinks} snp link(s) and ${dvmNodes} rn-d/mn node(s) (CHK-18/19).`);
      }
    }
    setChi("dvmEnable", v);
  })));
  box.appendChild(field("Port mode", selectInput(["axi4", "chi", "both"], chiValue("portMode", "axi4"), (v) => setChi("portMode", v))));
  box.appendChild(field("P-Credit types", numberInput(chiValue("pcrdTypes", 1), 1, 16, (v) => setChi("pcrdTypes", v))));
  box.appendChild(field("L-Credit depth", numberInput(chiValue("lcreditDepth", 4), 1, 15, (v) => setChi("lcreditDepth", v))));
}

/* ------------------------------------------------------------------ */
/* QoS block (IR-25–34): mapping read-only, classes editable           */
/* ------------------------------------------------------------------ */

function effectiveQosClasses() {
  return (ir.qos && Array.isArray(ir.qos.classes) && ir.qos.classes.length)
    ? ir.qos.classes
    : DEFAULT_QOS_CLASSES;
}

function editQosClass(name, key, value) {
  if (!ir.qos) ir.qos = {};
  if (!Array.isArray(ir.qos.classes) || !ir.qos.classes.length) {
    ir.qos.classes = DEFAULT_QOS_CLASSES.map((c) => ({ ...c }));
  }
  const c = ir.qos.classes.find((x) => x.name === name);
  if (c) c[key] = value;
  commit();
}

function renderQos() {
  const box = $("qos");
  clear(box);
  const mapping = (ir.qos && ir.qos.mapping) || DEFAULT_QOS_MAPPING;
  const mtable = el("table", "ep-table");
  const mh = el("tr");
  for (const h of ["QoS range", "class"]) mh.appendChild(el("th", null, h));
  mtable.appendChild(mh);
  for (const m of mapping) {
    const tr = el("tr");
    tr.appendChild(el("td", null, `[${m.qosMin}, ${m.qosMax}]`));
    tr.appendChild(el("td", null, m.class));
    mtable.appendChild(tr);
  }
  box.appendChild(mtable);
  box.appendChild(el("p", "hint", "Mapping locked to the implemented 8/4 thresholds (CHK-34); custom thresholds await a hardware minor version."));

  const ctable = el("table", "ep-table");
  const ch = el("tr");
  for (const h of ["class", "vcs", "depth", "arb", "weight"]) ch.appendChild(el("th", null, h));
  ctable.appendChild(ch);
  for (const c of effectiveQosClasses()) {
    const tr = el("tr");
    tr.appendChild(el("td", null, c.name));
    const vcs = el("td"); vcs.appendChild(numberInput(c.vcs != null ? c.vcs : 1, 1, 8, (v) => editQosClass(c.name, "vcs", v))); tr.appendChild(vcs);
    const depth = el("td"); depth.appendChild(numberInput(c.depth != null ? c.depth : 4, 1, 64, (v) => editQosClass(c.name, "depth", v))); tr.appendChild(depth);
    const arb = el("td"); arb.appendChild(selectInput(["wrr", "rr"], c.arbitration || "wrr", (v) => editQosClass(c.name, "arbitration", v))); tr.appendChild(arb);
    const weight = el("td"); weight.appendChild(numberInput(c.weight != null ? c.weight : 1, 1, 64, (v) => editQosClass(c.name, "weight", v))); tr.appendChild(weight);
    ctable.appendChild(tr);
  }
  box.appendChild(ctable);
}

/* ------------------------------------------------------------------ */
/* topology: IR-derived stable layout + manual drag (G5)               */
/* ------------------------------------------------------------------ */

let posCache = new Map();

/* Surviving entities keep their cached coordinates (no drift on edits);
 * new entities take their auto slot. Drag overrides live in posCache. */
function computeLayout(model) {
  const res = modelComputeLayout(model, posCache);
  posCache = res.cache;
  return res.pos;
}

function layoutKey() {
  return "loom-layout:" + ((ir.meta || {}).name || "loom");
}

function loadLayoutCache() {
  posCache = new Map();
  try {
    const raw = localStorage.getItem(layoutKey());
    if (raw) {
      const o = JSON.parse(raw);
      for (const k of Object.keys(o)) posCache.set(k, o[k]);
    }
  } catch { /* private mode / quota: fall back to auto layout */ }
}

function saveLayout() {
  try {
    const o = {};
    for (const [k, v] of posCache) o[k] = v;
    localStorage.setItem(layoutKey(), JSON.stringify(o));
  } catch { /* ignore */ }
}

function resetLayout() {
  posCache = new Map();
  try { localStorage.removeItem(layoutKey()); } catch { /* ignore */ }
  showTopology(last || {});
}

function showTopology(data) {
  const box = $("topology");
  const legend = $("topology-legend");
  clear(box);
  clear(legend);
  $("cdg").textContent = data.graph
    ? (data.graph.acyclic ? "CDG acyclic — constructively deadlock free" : "CDG has a cycle — rejected by G1")
    : "";
  if (!ir.fabric.routers.length && !ir.nodes.length) {
    box.appendChild(el("div", "empty", "Add routers and nodes to see the topology."));
    return;
  }

  const pos = computeLayout(ir);
  const routers = new Set(routerNames());
  const clockOf = new Map();
  for (const o of [...ir.fabric.routers, ...ir.nodes]) clockOf.set(o.name || o.id, o.clock);

  /* aggregate channel classes per undirected pair from the graph report */
  const classOfPair = new Map();
  for (const c of (data.graph && data.graph.channels) || []) {
    const k = pairKey(c.from, c.to);
    if (!classOfPair.has(k)) classOfPair.set(k, new Set());
    if (c.class) classOfPair.get(k).add(c.class);
  }
  const datelinePairs = new Set();
  for (const label of [...((data.graph && data.graph.datelines) || []), ...((data.graph && data.graph.autoDatelines) || [])]) {
    const [a, b] = label.split("->");
    if (a && b) datelinePairs.add(pairKey(a, b));
  }

  /* unique undirected edges from IR links */
  const edges = new Map();
  for (const l of ir.fabric.links) {
    const k = pairKey(l.from, l.to);
    if (!edges.has(k)) edges.set(k, { from: l.from, to: l.to, async: false, dateline: false });
    const e = edges.get(k);
    if (l.cdc === "async") e.async = true;
    if (l.dateline) e.dateline = true;
  }
  for (const [k, e] of edges) {
    if (datelinePairs.has(k)) e.dateline = true;
    e.classes = classOfPair.has(k) ? [...classOfPair.get(k)].sort() : (ir.fabric.links.find((l) => pairKey(l.from, l.to) === k)?.channels || ["req", "rsp", "dat"]);
  }

  const maxX = Math.max(320, ...[...pos.values()].map((p) => p.x + NODE_W + 30));
  const maxY = Math.max(120, ...[...pos.values()].map((p) => p.y + NODE_H + 20));
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("viewBox", `0 0 ${maxX} ${maxY}`);
  svg.style.width = "100%";
  const defs = document.createElementNS(ns, "defs");
  defs.innerHTML =
    '<marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">' +
    '<path d="M0,0 L10,5 L0,10 z" fill="#8e8676"/></marker>' +
    '<marker id="arrow-async" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">' +
    '<path d="M0,0 L10,5 L0,10 z" fill="#a4532c"/></marker>';
  svg.appendChild(defs);

  const lineEls = [];
  for (const e of edges.values()) {
    const a = pos.get(e.from);
    const b = pos.get(e.to);
    if (!a || !b) continue;
    const line = document.createElementNS(ns, "line");
    line.setAttribute("class", "edge" + (e.async ? " async" : "") + (e.dateline ? " dateline" : ""));
    line.setAttribute("x1", String(a.x + NODE_W));
    line.setAttribute("y1", String(a.y + NODE_H / 2));
    line.setAttribute("x2", String(b.x - 2));
    line.setAttribute("y2", String(b.y + NODE_H / 2));
    line.setAttribute("marker-end", e.async ? "url(#arrow-async)" : "url(#arrow)");
    const title = document.createElementNS(ns, "title");
    title.textContent = `${e.from} ↔ ${e.to} · [${e.classes.join(",")}]${e.async ? " · async" : ""}${e.dateline ? " · dateline" : ""}`;
    line.appendChild(title);
    svg.appendChild(line);
    lineEls.push({ el: line, from: e.from, to: e.to });
  }

  const nodeG = new Map();
  for (const [name, p] of pos) {
    const g = document.createElementNS(ns, "g");
    g.setAttribute("transform", `translate(${p.x},${p.y})`);
    g.style.cursor = "grab";
    const rect = document.createElementNS(ns, "rect");
    rect.setAttribute("class", "node-shape" + (routers.has(name) ? " router" : ""));
    rect.setAttribute("width", String(NODE_W));
    rect.setAttribute("height", String(NODE_H));
    rect.setAttribute("rx", "4");
    rect.style.stroke = domainColor(clockOf.get(name));
    g.appendChild(rect);
    const label = document.createElementNS(ns, "text");
    label.setAttribute("class", "node-label");
    label.setAttribute("x", String(NODE_W / 2));
    label.setAttribute("y", String(NODE_H / 2));
    label.textContent = name;
    g.appendChild(label);
    svg.appendChild(g);
    nodeG.set(name, g);
  }
  box.appendChild(svg);

  /* manual drag: positions persist to localStorage, never into the IR */
  let dragging = null;
  const scale = () => {
    const rect = svg.getBoundingClientRect();
    return rect.width > 0 ? maxX / rect.width : 1;
  };
  const updateLines = (name) => {
    const p = pos.get(name);
    for (const le of lineEls) {
      if (le.from === name) {
        le.el.setAttribute("x1", String(p.x + NODE_W));
        le.el.setAttribute("y1", String(p.y + NODE_H / 2));
      }
      if (le.to === name) {
        le.el.setAttribute("x2", String(p.x - 2));
        le.el.setAttribute("y2", String(p.y + NODE_H / 2));
      }
    }
  };
  for (const [name, g] of nodeG) {
    g.addEventListener("pointerdown", (e) => {
      e.preventDefault();
      try { g.setPointerCapture(e.pointerId); } catch { /* synthetic events */ }
      const orig = { ...pos.get(name) };
      dragging = { name, sx: e.clientX, sy: e.clientY, orig };
      g.style.cursor = "grabbing";
    });
    g.addEventListener("pointermove", (e) => {
      if (!dragging || dragging.name !== name) return;
      const s = scale();
      const np = { x: dragging.orig.x + (e.clientX - dragging.sx) * s, y: dragging.orig.y + (e.clientY - dragging.sy) * s };
      pos.set(name, np);
      posCache.set(name, np);
      g.setAttribute("transform", `translate(${np.x},${np.y})`);
      updateLines(name);
    });
    const end = () => {
      if (dragging && dragging.name === name) {
        dragging = null;
        g.style.cursor = "grab";
        saveLayout();
      }
    };
    g.addEventListener("pointerup", end);
    g.addEventListener("pointercancel", end);
  }

  /* legend */
  const domains = [...new Set(ir.clockDomains.map((c) => c.id))].sort();
  for (const d of domains) {
    const key = el("span", "key");
    const dot = el("span", "dot");
    dot.style.background = domainColor(d);
    key.appendChild(dot);
    key.appendChild(el("span", null, d));
    legend.appendChild(key);
  }
  if ([...edges.values()].some((e) => e.async)) {
    const key = el("span", "key");
    key.appendChild(el("span", "dash"));
    key.appendChild(el("span", null, "async"));
    legend.appendChild(key);
  }
  if ([...edges.values()].some((e) => e.dateline)) {
    legend.appendChild(el("span", "key", "dateline cut"));
  }
  const allClasses = [...new Set([...edges.values()].flatMap((e) => e.classes))].sort();
  if (allClasses.length) legend.appendChild(el("span", "key", "channels: " + allClasses.join(",")));
  const reset = el("button", "ghost", "reset layout");
  reset.type = "button";
  reset.addEventListener("click", resetLayout);
  legend.appendChild(reset);
}

/* ------------------------------------------------------------------ */
/* editor / examples / v1 upgrade                                      */
/* ------------------------------------------------------------------ */

/* Returns true when the editor text was adopted into the model.
 * loom/v1 input is upgraded server-side (MIG-01); the normalized v2
 * canonical is adopted verbatim — no JS re-implementation of Upgrade. */
async function parseEditor() {
  const prev = ir;
  const text = $("editor").value;
  let obj;
  try {
    obj = JSON.parse(text);
  } catch (e) {
    ir = prev;
    showProblem("JSON: " + e.message);
    return false;
  }
  if (obj && obj.schema === "loom/v1") {
    try {
      const data = await postEvaluate(text);
      if (data.upgraded && data.canonical) {
        ir = JSON.parse(data.canonical);
        upgradeWarnings = data.warnings || [];
        toast("v1 input auto-upgraded to loom/v2");
      } else {
        ir = prev;
        upgradeWarnings = [];
        clear($("problems"));
        (data.errors || ["upgrade rejected"]).forEach((e) =>
          $("problems").appendChild(el("div", "err", e)));
        return false;
      }
    } catch (e) {
      ir = prev;
      showProblem("upgrade: " + e.message);
      return false;
    }
  } else {
    ir = obj;
    upgradeWarnings = [];
  }
  loadLayoutCache();
  normalize();
  syncEditor();
  renderAll();
  evaluate();
  scheduleDraftSave();
  return true;
}

async function loadExamples() {
  const list = await getExamples();
  const sel = $("examples");
  sel.replaceChildren();
  for (const name of list) sel.appendChild(new Option(name.replace(/\.json$/, ""), name));
  if (list.includes("minimal_4x4.json")) sel.value = "minimal_4x4.json";
}

async function loadExample() {
  const name = $("examples").value;
  $("editor").value = await getExample(name);
  await parseEditor();
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
  $("add-node").addEventListener("click", () => {
    const rname = (selection && selection.kind === "router") ? selection.id : routerNames()[0];
    if (!rname) { toast("Create a router first."); return; }
    addNode("rn-i", rname);
  });
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
    ir.chi.addrWidth = Number($("addr-width").value);
    commit();
  });
  $("data-width").addEventListener("change", () => {
    const w = Number($("data-width").value);
    ir.chi.datWidth = w;
    for (const l of ir.fabric.links) {
      if (l.width != null) l.width = w;
    }
    commit();
  });

  await loadExamples();
  let wsInfo = null;
  try {
    wsInfo = await getWorkspace();
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
      await parseEditor();
    }
  }
  if (!workspace) openWsDialog();
  requestAnimationFrame(() => document.body.classList.add("ready"));
}

main().catch((e) => showProblem("startup: " + e.message));