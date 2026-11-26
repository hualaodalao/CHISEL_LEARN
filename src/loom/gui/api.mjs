/* Loom GUI network layer. Thin wrappers over the serve API so the
 * contract lives in one place; DOM-free (fetch only). */

export async function postEvaluate(text) {
  const res = await fetch("/api/evaluate", { method: "POST", body: text });
  return res.json();
}

export async function getExamples() {
  const res = await fetch("/api/examples");
  return res.json();
}

export async function getExample(name) {
  const res = await fetch("/api/example?name=" + encodeURIComponent(name));
  return res.text();
}

export async function getDraft() {
  const res = await fetch("/api/draft");
  if (!res.ok) return { ok: false, text: null };
  return { ok: true, text: await res.text() };
}

export async function postDraft(text) {
  const res = await fetch("/api/draft", { method: "POST", body: text });
  return res.ok;
}

export async function getWorkspace() {
  const res = await fetch("/api/workspace");
  return res.json();
}

export async function postWorkspace(path) {
  const res = await fetch("/api/workspace", { method: "POST", body: path });
  const data = await res.json().catch(() => ({}));
  return { ok: res.ok, data };
}