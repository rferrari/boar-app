import { describe, expect, it } from "vitest";
import { blind, bootstrapCI, cohenKappa, combineOrders, firstOrders, mapVerdict, meanScore, rng, summarize } from "./judge-core.mjs";
import { isRateLimited } from "./claude-cli.mjs";

const s = (c, m, u) => ({ correctness: c, completeness: m, usefulness: u });

describe("blind", () => {
  it("strips citations, links, bold and the trailing source list", () => {
    const text = "**Tidal locking** makes it [1]. See [NASA](https://nasa.gov) (https://x.y).\n\nSources:\nhttps://a.b\nhttps://c.d";
    expect(blind(text)).toBe("Tidal locking makes it. See NASA.");
  });
  it("drops <think> blocks", () => {
    expect(blind("<think>hmm</think>Paris.")).toBe("Paris.");
  });
});

describe("order and verdict mapping", () => {
  it("is deterministic for a seed and uses both orders", () => {
    const ids = Array.from({ length: 40 }, (_, i) => `q${i}`);
    expect(firstOrders(ids, 7)).toEqual(firstOrders(ids, 7));
    expect(new Set(Object.values(firstOrders(ids, 7)))).toEqual(new Set(["boarA", "boarB"]));
  });
  it("maps a positional winner back to systems", () => {
    const v = { scores_A: s(5, 5, 5), scores_B: s(1, 1, 1), winner: "A" };
    expect(mapVerdict(v, true)).toMatchObject({ winner: "boar", boar: s(5, 5, 5) });
    expect(mapVerdict(v, false)).toMatchObject({ winner: "ref", ref: s(5, 5, 5) });
    expect(mapVerdict({ ...v, winner: "tie" }, true).winner).toBe("tie");
  });
  it("turns disagreement between orders into an inconsistent tie", () => {
    const c = combineOrders({ winner: "boar", boar: s(4, 4, 4), ref: s(2, 2, 2) }, { winner: "ref", boar: s(2, 2, 2), ref: s(4, 4, 4) });
    expect(c).toMatchObject({ winner: "tie", consistent: false, boar: s(3, 3, 3), ref: s(3, 3, 3) });
  });
});

describe("stats", () => {
  it("summarizes win rates, ratio and position consistency", () => {
    const pairs = [
      { winner: "boar", consistent: true, boar: s(5, 5, 5), ref: s(4, 4, 4) },
      { winner: "ref", consistent: true, boar: s(2, 2, 2), ref: s(4, 4, 4) },
      { winner: "tie", consistent: false, boar: s(3, 3, 3), ref: s(4, 4, 4) },
      { winner: "ref", consistent: true, boar: s(2, 2, 2), ref: s(4, 4, 4) },
    ];
    const r = summarize(pairs);
    expect(r).toMatchObject({ n: 4, boarWin: 0.25, tie: 0.25, refWin: 0.5, winScore: 0.375, positionConsistency: 0.75 });
    expect(r.qualityRatio).toBeCloseTo(12 / 16);
    expect(r.winScoreCI[0]).toBeLessThanOrEqual(r.winScore);
    expect(r.winScoreCI[1]).toBeGreaterThanOrEqual(r.winScore);
    expect(meanScore(s(3, 4, 5))).toBe(4);
  });
  it("bootstrap CI is reproducible and collapses on constant data", () => {
    const f = (xs) => xs.reduce((a, b) => a + b, 0) / xs.length;
    expect(bootstrapCI([1, 0, 1, 1], f, { seed: 3 })).toEqual(bootstrapCI([1, 0, 1, 1], f, { seed: 3 }));
    expect(bootstrapCI([2, 2, 2], f)).toEqual([2, 2]);
  });
  it("rng stays in [0, 1)", () => {
    const r = rng(1);
    for (let i = 0; i < 1000; i++) { const x = r(); expect(x >= 0 && x < 1).toBe(true); }
  });
  it("cohen kappa: 1 on agreement, 0 at chance", () => {
    expect(cohenKappa(["a", "b", "a"], ["a", "b", "a"])).toBe(1);
    expect(cohenKappa(["a", "a", "b", "b"], ["a", "b", "a", "b"])).toBe(0);
  });
});

describe("rate limit detection", () => {
  it("flags usage limits and 429s only", () => {
    expect(isRateLimited({ error: "Claude usage limit reached" })).toBe(true);
    expect(isRateLimited({ stderr: "API Error: 429" })).toBe(true);
    expect(isRateLimited({ error: "exit 1: boom" })).toBe(false);
  });
});

import { deflects, matchVenues, matchable, normalize, scoreFoodAnswer } from "./venues.mjs";

describe("venue scoring", () => {
  const gold = {
    diet: "vegan",
    osm: { venues: [
      { osm: "node/1", name: "Café Vegan", vegan: "only" },
      { osm: "node/2", name: "Kopps", vegan: "only" },
      { osm: "node/3", name: "Brammibal's Donuts", vegan: "only" },
      { osm: "node/4", name: "Lucky Leek", vegan: "yes" },
      { osm: "node/5", name: "Tibet Haus", vegetarian: "yes" },
    ] },
  };
  it("normalizes accents, apostrophes and punctuation", () => {
    expect(normalize("Brammibal’s  Donuts!")).toBe("brammibals donuts");
    expect(normalize("São Paulo")).toBe("sao paulo");
  });
  it("ignores generic-only names", () => {
    expect(matchable("Café Vegan")).toBe(false);
    expect(matchable("Kopps")).toBe(true);
  });
  it("matches whole phrases only", () => {
    expect(matchVenues("Try Kopps and Brammibals Donuts.", gold.osm.venues).map((m) => m.osm)).toEqual(["node/2", "node/3"]);
    expect(matchVenues("Kopperstraße has nothing", gold.osm.venues)).toEqual([]);
  });
  it("passes with 3 verified diet venues and flags deflection", () => {
    const s = scoreFoodAnswer("1. **Kopps** 2. Lucky Leek 3. Brammibal's Donuts 4. Tibet Haus", gold);
    expect(s).toMatchObject({ verifiedVenues: 4, verifiedDietVenues: 3, pass: true, deflects: false });
    expect(scoreFoodAnswer("The context provided does not contain information about restaurants.", gold)).toMatchObject({ pass: false, deflects: true });
    expect(deflects("Check HappyCow for listings")).toBe(true);
  });
});
