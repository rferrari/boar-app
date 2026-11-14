import { describe, it, expect } from "vitest";
import { blind, pairs, passOf, pickModel, seeded } from "./blind-pair.mjs";

const row = (runId, modelId, queryId, style, answer, extra = {}) =>
  JSON.stringify({ runId, configId: `model:${modelId}`, modelId, queryId, query: `q ${queryId}?`, style, answer, outcome: "success", tokPerSec: 30, ...extra });

const small = "qwen3-0.6b-q4km";
const compact = "qwen3-1.7b-q4km";
const run1 = [
  row("r1", small, "factual-1", "succinct", "Canberra (small)"),
  row("r1", small, "factual-1", "detailed", "Canberra is the capital (small)"),
  row("r1", compact, "factual-1", "succinct", "Canberra (compact)"),
  row("r1", compact, "factual-1", "detailed", "Canberra is the capital (compact)"),
  row("r1", small, "health-dose", "succinct", "", { outcome: "failure", errorMessage: "model failed to load" }),
  row("r1", compact, "health-dose", "succinct", "No more than 4 g a day (compact)"),
  row("r1", compact, "only-one", "succinct", "only the compact answered"),
].join("\n");
const run2 = [row("r2", compact, "factual-1", "succinct", "Canberra again (compact)"), row("r2", small, "factual-1", "succinct", "Canberra again (small)")].join("\n");

describe("blind pairs", () => {
  it("pairs each run, pass and question both models answered, by a part of their ids", () => {
    const p = pairs([run1, run2], ["qwen3-0.6b", "qwen3-1.7b"]);
    expect(p.models).toEqual([small, compact]);
    expect(p.items.map((i) => `${i.runId} ${i.pass} ${i.queryId}`)).toEqual([
      "r1 succinct factual-1", "r1 detailed factual-1", "r1 succinct health-dose", "r2 succinct factual-1",
    ]);
    expect(pairs([run1, run2], ["qwen3-0.6b", "qwen3-1.7b"], { pass: "detailed" }).items).toHaveLength(1);
    expect(pairs([run1, run2], ["qwen3-0.6b", "qwen3-1.7b"], { run: "r2" }).items).toHaveLength(1);
  });

  it("refuses a model that is in no row or is ambiguous", () => {
    expect(() => pickModel("qwen3-4b", [small, compact])).toThrow(/in no row/);
    expect(() => pickModel("qwen3", [small, compact])).toThrow(/ambiguous/);
    expect(passOf({ path: "deep", style: "detailed" })).toBe("deep");
  });

  it("hides the model: no id, label or timing in the blind file; the key says who is A and B, the same again from its seed", () => {
    const p = pairs([run1, run2], ["qwen3-0.6b", "qwen3-1.7b"]);
    const a = blind(p, seeded(7), { seed: 7 });
    for (const s of [small, compact, "0.6", "1.7", "tokPerSec", "30"]) expect(a.markdown).not.toContain(s);
    expect(a.key.seed).toBe(7);
    expect(a.key.items).toHaveLength(4);
    for (const k of a.key.items) expect(new Set([k.A, k.B])).toEqual(new Set([small, compact]));
    // Each answer sits under the letter the key gives its model.
    const sections = a.markdown.split(/^## /m).slice(1);
    a.key.items.forEach((k, i) => {
      const [aPart, bPart] = sections[i].split("### B");
      const own = (id) => (id === small ? "(small)" : "(compact)");
      if (!sections[i].includes("no answer")) {
        expect(aPart).toContain(own(k.A));
        expect(bPart).toContain(own(k.B));
      }
    });
    expect(blind(p, seeded(7), { seed: 7 })).toEqual(a);
    expect(a.markdown).toContain("(no answer: failure, model failed to load)");
    expect(a.marksCsv.trim().split("\n")).toHaveLength(5);
  });

  it("draws both sides over many questions", () => {
    const many = Array.from({ length: 40 }, (_, i) => [row("r", small, `q${i}`, "succinct", "x"), row("r", compact, `q${i}`, "succinct", "y")]).flat().join("\n");
    const k = blind(pairs([many], [small, compact]), seeded(1)).key.items;
    const smallIsA = k.filter((x) => x.A === small).length;
    expect(smallIsA).toBeGreaterThan(10);
    expect(smallIsA).toBeLessThan(30);
  });
});
