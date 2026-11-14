// A blind read of two models' answers (t9 0443 item 4): for each question, the two answers side by side as A and B,
// which model is which drawn at random per question, and the key in a separate file. Pure: no files, no clock.
import { parseRows } from "./eval-report.mjs";

/** mulberry32: a small seeded generator, so a blind file can be made again from its key's seed. */
export function seeded(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** A row's pass, as BenchPresets.passKey: "deep" for Deep Research, else its style (the default style when it says none). */
export function passOf(row) {
  return row.path === "deep" ? "deep" : (row.style ?? row.personalityId ?? "detailed");
}

/** The one model id among [ids] that [selector] names: the id itself, else the only id holding it. */
export function pickModel(selector, ids) {
  if (ids.includes(selector)) return selector;
  const norm = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
  const hits = ids.filter((id) => norm(id).includes(norm(selector)));
  if (hits.length !== 1) throw new Error(`model "${selector}" ${hits.length ? `is ambiguous (${hits.join(", ")})` : `is in no row (${ids.join(", ")})`}`);
  return hits[0];
}

/**
 * The pairs: one per run, pass and question that both models answered in, in the run's order (files in the order
 * given, then question order). [models]: two selectors. [run], [pass]: only that run or pass.
 */
export function pairs(texts, models, { run, pass } = {}) {
  const rows = texts.flatMap((t) => parseRows(t)).filter((r) => r.modelId);
  const ids = [...new Set(rows.map((r) => r.modelId))];
  if (models.length !== 2) throw new Error("name exactly two models");
  const [m1, m2] = models.map((s) => pickModel(s, ids));
  if (m1 === m2) throw new Error(`both selectors name ${m1}`);
  const byKey = new Map();
  for (const r of rows) {
    if (r.modelId !== m1 && r.modelId !== m2) continue;
    const p = passOf(r);
    if (run && r.runId !== run) continue;
    if (pass && p !== pass) continue;
    const key = `${r.runId}|${p}|${r.queryId}`;
    if (!byKey.has(key)) byKey.set(key, { runId: r.runId, pass: p, queryId: r.queryId, query: r.query, category: r.category, topic: r.topic });
    byKey.get(key)[r.modelId === m1 ? "first" : "second"] = r; // the last row wins, as dedupeRows
  }
  return { models: [m1, m2], items: [...byKey.values()].filter((i) => i.first && i.second) };
}

/** Draws A and B for each pair with [random]; returns the blind markdown, the key and a marks sheet (CSV). */
export function blind({ models, items }, random, { seed = null, sources = [] } = {}) {
  const key = { about: "The key to blind.md: which model is A and which is B in each item. Open it only after marking.", seed, sources, models, items: [] };
  const out = [
    "# Blind read",
    "",
    `${items.length} questions, two answers each (A and B). Which model wrote which is drawn at random per question, in key.json:`,
    "don't open it before marking. Timings and model names are left out on purpose.",
    "",
    "For each question mark A better, same or B better, and any wrong fact. A wrong step in a health or safety answer is a",
    "no-go (t9 0443). The same marks go in marks.csv.",
    "",
  ];
  const csv = ["n,pass,queryId,better (A/B/same),wrong fact in A,wrong fact in B,notes"];
  const text = (r) => (r.outcome === "success" && r.answer ? r.answer.trim() : `(no answer: ${r.outcome}${r.errorMessage ? `, ${r.errorMessage}` : ""})`);
  items.forEach((it, i) => {
    const n = i + 1;
    const swap = random() < 0.5;
    const [a, b] = swap ? [it.second, it.first] : [it.first, it.second];
    key.items.push({ n, runId: it.runId, pass: it.pass, queryId: it.queryId, A: a.modelId, B: b.modelId });
    out.push(`## ${n}. ${it.queryId} (${it.pass}${it.topic ? `, ${it.topic}` : ""})`, "", `> ${it.query.replace(/\n/g, "\n> ")}`, "");
    out.push("### A", "", text(a), "", "### B", "", text(b), "");
    out.push("Mark: A better / same / B better. Wrong fact in A: … Wrong fact in B: …", "", "---", "");
    csv.push(`${n},${it.pass},${it.queryId},,,,`);
  });
  return { markdown: out.join("\n"), key, marksCsv: csv.join("\n") + "\n" };
}
