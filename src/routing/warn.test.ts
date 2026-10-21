import { describe, expect, it } from "vitest";
import {
  contentSentences,
  coverageOf,
  tierFromCoverage,
  warnLang,
  healthReasons,
  isHealthPackChunk,
  isModelGenerated,
  settleTier,
  streamStartTier,
  WARN_LEX,
  warnCopy,
  warnOf,
  warnTier,
  type WarnTier,
} from "./warn";
import goldens from "./testing/warn-goldens.json";

/**
 * Parity goldens: finished turns from the eval traces (64 probes, v0.1 60, the 180 published self-tests) with the tier the
 * reference rule gave each one. No answer text: each row carries the reference's coverage of its content sentences
 * (cov = [content sentences, uncited, first cited]); the sentence rules have their own cases below. kept = source hosts.
 * Traces run without HEALTHGAP carry its class as healthgap:<cls>, the code the layer emits. projected = the layer would take
 * the turn off the model (a dose, poisoning or CO question on the X2b runs): no WARN.
 */
interface Golden {
  g: string;
  id: string;
  q: string;
  codes: string[];
  modelId: string | null;
  outcome: string;
  nonEmpty: boolean;
  kept: string[];
  cited: number;
  cov: [number, number, boolean];
  projected: boolean;
  hgScan: string | null;
  expect: WarnTier | null;
}
const G = goldens as Golden[];
const turn = (x: Golden) => ({ query: x.q, codes: x.codes, modelId: x.modelId, outcome: x.outcome, text: x.nonEmpty ? "(model text)" : "", kept: x.kept.map((source) => ({ source })) });
const tierOf = (x: Golden): WarnTier | null => {
  if (x.projected || !isModelGenerated(turn(x)) || healthReasons(turn(x)).length === 0) return null;
  return tierFromCoverage({ kept: x.kept.length, cited: x.cited, count: x.cov[0], uncovered: x.cov[1], firstCited: x.cov[2] });
};
const ids = (g: string, tier?: WarnTier) => G.filter((x) => x.g === g && (tier ? tierOf(x) === tier : tierOf(x) !== null)).map((x) => x.id).sort();
const count = (g: string) => {
  const c: Record<string, number> = { n: 0, warn: 0 };
  for (const x of G.filter((y) => y.g === g)) {
    c.n++;
    const t = tierOf(x);
    if (t) {
      c.warn++;
      c[t] = (c[t] ?? 0) + 1;
    }
  }
  return c;
};

describe("warn goldens (reference rule, row by row)", () => {
  it("every row gets the reference tier", () => {
    const diff = G.filter((x) => tierOf(x) !== x.expect).map((x) => `${x.g} ${x.id}: ${tierOf(x)} != ${x.expect}`);
    expect(diff).toEqual([]);
    expect(G.length).toBe(676);
  });
  it("projected rows are dose, poisoning or CO questions the layer answers without a model", () => {
    for (const x of G.filter((y) => y.projected)) expect(["dose", "poison", "co"]).toContain(x.hgScan);
  });
});

describe("warn counts (the reference rule's table)", () => {
  it("probes 64, 4B cut 7 + HEALTHGAP r2: exactly h21, n12, vit-hlt-003", () => {
    expect(count("4B|probes64")).toEqual({ n: 64, warn: 3, "no-source": 1, "beyond-source": 2 });
    expect(ids("4B|probes64")).toEqual(["h21", "n12", "vit-hlt-003"]);
    expect(ids("4B|probes64", "no-source")).toEqual(["n12"]);
  });
  it("probes 64, 2B + HEALTHGAP: 3, and the cited-but-wrong vit-hlt-003 stays beyond-source", () => {
    expect(count("2B+HG|probes64")).toEqual({ n: 64, warn: 3, "no-source": 1, "beyond-source": 2 });
    expect(tierOf(G.find((x) => x.g === "2B+HG|probes64" && x.id === "vit-hlt-003")!)).toBe("beyond-source");
    const v = G.find((x) => x.g === "2B-cut7|probes64" && x.id === "vit-hlt-003")!;
    expect(v.cited).toBeGreaterThan(0);
    expect(tierOf(v)).toBe("beyond-source");
  });
  it("probes 64, cut 7 without HEALTHGAP: 15 for each model, covering the 6 unsafe 2B answers", () => {
    expect(count("4B-cut7|probes64")).toEqual({ n: 64, warn: 15, "no-source": 8, "beyond-source": 7 });
    expect(count("2B-cut7|probes64")).toEqual({ n: 64, warn: 15, "no-source": 8, "beyond-source": 7 });
    const two = ids("2B-cut7|probes64");
    for (const id of ["h08", "h10", "h19", "h27", "n03", "vit-hlt-003"]) expect(two).toContain(id);
  });
  it("v0.1 60: lan-hlt-002, lan-hlt-003, vit-hlt-003, vit-sci-001", () => {
    expect(count("4B|v01")).toEqual({ n: 60, warn: 4, "no-source": 2, "beyond-source": 1, sourced: 1 });
    expect(ids("4B|v01")).toEqual(["lan-hlt-002", "lan-hlt-003", "vit-hlt-003", "vit-sci-001"]);
    expect(ids("4B|v01", "sourced")).toEqual(["lan-hlt-003"]);
    expect(ids("4B|v01", "beyond-source")).toEqual(["vit-hlt-003"]);
  });
  it("180 self-tests: 36 for 4B+X2b and 1.7B+X2b, per pack 13/15/4/3/1", () => {
    expect(count("4B+X2b|st180")).toEqual({ n: 180, warn: 36, "no-source": 1, "beyond-source": 18, sourced: 17 });
    expect(count("1.7B+X2b|st180")).toEqual({ n: 180, warn: 36, "no-source": 1, "beyond-source": 21, sourced: 14 });
    for (const g of ["4B+X2b|st180", "1.7B+X2b|st180"]) {
      const per: Record<string, number> = {};
      for (const id of ids(g)) {
        const pack = id.replace(/^st-/, "").replace(/-\d+$/, "");
        per[pack] = (per[pack] ?? 0) + 1;
      }
      expect(per).toEqual({ "boar-medlineplus-en": 13, "qa-health-se": 15, "qa-outdoors-se": 4, "boar-firstaid-en": 3, "boar-factbook": 1 });
    }
  });
  it("the 4 non-health WARNs on the 180 (safety or exposure, accepted in review)", () => {
    const fp = ["st-boar-factbook-014", "st-boar-firstaid-en-013", "st-boar-medlineplus-en-019", "st-qa-outdoors-se-010"];
    for (const g of ["4B+X2b|st180", "1.7B+X2b|st180"]) for (const id of fp) expect(ids(g)).toContain(id);
    const pigs = G.find((x) => x.g === "4B+X2b|st180" && x.id === "st-qa-outdoors-se-010")!;
    expect(healthReasons(turn(pigs))).toEqual(["detector:p>=0.05"]);
    const kit = G.find((x) => x.g === "4B+X2b|st180" && x.id === "st-boar-medlineplus-en-019")!;
    expect(healthReasons(turn(kit))).toEqual(["source:health-pack"]);
    for (const id of ["st-boar-factbook-014", "st-boar-firstaid-en-013"]) {
      expect(healthReasons(turn(G.find((x) => x.g === "4B+X2b|st180" && x.id === id)!))).toContain("lexicon:isSafetyQuery");
    }
  });
  it("no WARN on the bitcoin, ethereum, simplewiki or travel self-tests", () => {
    for (const g of ["4B+X2b|st180", "1.7B+X2b|st180"]) expect(ids(g).filter((id) => /bitcoin|ethereum|simplewiki|travel/.test(id))).toEqual([]);
  });
});

describe("M: a model wrote the text", () => {
  const base = { modelId: "qwen3-4b-instruct-2507-q4km", codes: ["task:research"], outcome: "success", text: "Some answer." };
  it("a model answer", () => expect(isModelGenerated(base)).toBe(true));
  it.each([
    ["a card", { modelId: "grounding-guard" }],
    ["an excerpt", { modelId: "extractive" }],
    ["small talk", { modelId: "small-talk" }],
    ["no model", { modelId: "none" }],
    ["FACTS", { codes: ["answer:facts-capital"] }],
    ["a compact decline", { codes: ["grounding:declined-compact"] }],
    ["an instant final", { codes: ["instant:final"] }],
    ["the source excerpt that replaced a risky model text", { codes: ["grounding:health-strict", "grounding:health-unsafe-blow-nose"] }],
    ["stopped", { outcome: "stopped" }],
    ["empty", { text: "  " }],
  ])("not %s", (_n, over) => expect(isModelGenerated({ ...base, ...over })).toBe(false));
});

describe("H: health-shaped", () => {
  const t = (query: string, codes: string[] = [], kept: { chunkId?: string; source?: string }[] = []) => healthReasons({ query, codes, kept });
  it("detector fire and p >= 0.05 (as the receipt rounds it)", () => {
    expect(t("x", ["detector:p=0.250", "detector:fire"])).toEqual(["detector:fire", "detector:p>=0.05"]);
    expect(t("x", ["detector:p=0.050"])).toEqual(["detector:p>=0.05"]);
    expect(t("x", ["detector:p=0.049"])).toEqual([]);
  });
  it("healthgap and grounding:health codes", () => {
    expect(t("x", ["healthgap:survival"])).toEqual(["healthgap"]);
    expect(t("x", ["grounding:health-strict"])).toEqual(["grounding:health-*"]);
  });
  it("a kept chunk from a health pack, by pack id or by source", () => {
    expect(isHealthPackChunk({ chunkId: "pack:boar-medlineplus-en:12" })).toBe(true);
    expect(isHealthPackChunk({ chunkId: "pack:qa-travel-se:3", source: "Stack Exchange — https://travel.stackexchange.com/q/1" })).toBe(false);
    expect(isHealthPackChunk({ source: "https://archive.org/details/army-fm-4-25-11" })).toBe(true);
    expect(t("x", [], [{ chunkId: "pack:qa-health-se:9" }])).toEqual(["source:health-pack"]);
  });
  it("WARN_LEX: Unicode word boundaries, the red-flag line, PT/ES words", () => {
    for (const q of ["I lost 6 kg in 2 weeks", "losing weight without trying", "Is dipirona safe?", "tenho febre", "¿Qué síntomas tiene el dengue?", "my LDL is 190", "bitten by a dog"]) expect(WARN_LEX.test(q)).toBe(true);
    // A word inside a longer word is not a hit: "flu" in "fluent", "pain" in "Spain", "dor" in "door"/"dorm", "heart" in "hearth".
    for (const q of ["I am fluent in French", "trains in Spain", "the door of the dormitory", "a hearth fire", "Ethereum gas fees"]) expect(WARN_LEX.test(q)).toBe(false);
    // PT/ES gaps closed: diarreia, "me duele el pecho", vômito, sangre, coração, queimadura, picada, mordida, infecção.
    for (const q of ["Estou com diarreia há 3 dias", "Me duele el pecho y el brazo izquierdo", "vômito com sangue", "sangre en la orina", "dor no coração", "queimadura de sol", "picada de abelha", "mordida de cachorro", "infecção urinária"]) expect(WARN_LEX.test(q)).toBe(true);
    // The weight line needs a person as the subject; an object's weight is no hit.
    for (const q of ["We lost 6 kg in the bike frame redesign", "The airline dropped 20 kg over the allowance", "lost 6 kg of luggage weight"]) expect(WARN_LEX.test(q)).toBe(false);
    for (const q of ["I lost 6 kg in two weeks without dieting", "My son has lost 3 kg in a month", "Dropped 6 kg in 2 weeks without trying"]) expect(WARN_LEX.test(q)).toBe(true);
    // Non-ASCII letters are word characters too: "dor" inside "dorée"/"adorável" is no hit.
    expect(WARN_LEX.test("a pomme dorée")).toBe(false);
    expect(WARN_LEX.test("um cão adorável")).toBe(false);
  });
});

describe("NFC and the copy's language", () => {
  it("a decomposed accent (NFD) still matches", () => {
    const nfd = "doença de pele".normalize("NFD");
    expect(nfd).not.toBe("doença de pele");
    expect(healthReasons({ query: nfd, codes: [], kept: [] })).toContain("lexicon:warn");
  });
  it("Spanish first, as the snakebite card: 'Me picó una culebra' gets the ES copy", () => {
    expect(warnLang("Me picó una culebra, que hago?")).toBe("es");
    expect(warnLang("Fui picado por uma abelha, o que faço?")).toBe("pt");
    expect(warnLang("What helps a bee sting?")).toBe("en");
  });
});

describe("sentence rules (coverage)", () => {
  it.each([
    ["two citations after the full stop", "Rinse the eye with clean water for fifteen minutes. [1][2] Then see a doctor about it soon.", { count: 2, uncovered: 1, firstCited: true }],
    ["first sentence uncited", "Most stings are harmless for healthy adults. Remove the stinger by scraping it out [1].", { count: 2, uncovered: 1, firstCited: false }],
    ["an abbreviation is not a sentence end", "Dr. Smith says to rest the ankle for two days [1].", { count: 1, uncovered: 0, firstCited: true }],
    ["the emergency line is not content", "Cool the burn with running water for twenty minutes [1].\n\nIn an emergency, call your local emergency number (911 in the US, 112 in Europe).", { count: 1, uncovered: 0, firstCited: true }],
    ["PT cited", "Lave a ferida com água e sabão por alguns minutos [1]. Cubra com um curativo limpo e seco depois [1].", { count: 2, uncovered: 0, firstCited: true }],
    ["a short answer is no content sentence", "Yes [1].", { count: 0, uncovered: 0, firstCited: false }],
  ])("%s", (_n, text, cov) => expect(coverageOf(text as string)).toEqual(cov));
});

describe("tier", () => {
  it("no source kept -> no-source", () => expect(warnTier({ kept: 0, cited: 0, text: "Anything at all here." })).toBe("no-source"));
  it("kept but nothing cited -> beyond-source", () => expect(warnTier({ kept: 2, cited: 0, text: "Drink water slowly every few minutes. Rest in the shade for an hour." })).toBe("beyond-source"));
  it("first sentence uncited -> beyond-source, even when most are cited", () => {
    const text = "Take two tablets every four hours at most. The label lists the maximum daily dose [1]. Children need their own dose by weight [1].";
    expect(warnTier({ kept: 1, cited: 1, text })).toBe("beyond-source");
  });
  it("first cited and >= 50 % cited -> sourced; a citation after the full stop counts", () => {
    expect(warnTier({ kept: 1, cited: 1, text: "Cool the burn under running water for twenty minutes. [1] Do not put butter or ice on it." })).toBe("sourced");
  });
  it("first cited but under half cited -> beyond-source", () => {
    const text = "Cool the burn under running water for twenty minutes [1]. Then wrap it loosely in cling film. Take a painkiller if it hurts a lot.";
    expect(warnTier({ kept: 1, cited: 1, text })).toBe("beyond-source");
  });
  it("the app's preface and the emergency line are not content sentences", () => {
    const text = "This answer is not from an offline source on this phone; check it before relying on it.\n\nRest and drink plenty of fluids for a few days.\n\nIn an emergency, call your local emergency number (911 in the US, 112 in Europe).";
    expect(contentSentences(text)).toEqual(["Rest and drink plenty of fluids for a few days."]);
  });
});

describe("stream start and done", () => {
  it("stream start: no-source with 0 kept, else beyond-source", () => {
    expect(streamStartTier(0)).toBe("no-source");
    expect(streamStartTier(3)).toBe("beyond-source");
  });
  it("done may only move beyond-source -> sourced", () => {
    expect(settleTier("beyond-source", "sourced")).toBe("sourced");
    expect(settleTier("no-source", "sourced")).toBe("no-source");
    expect(settleTier("no-source", "beyond-source")).toBe("no-source");
    expect(settleTier("beyond-source", "no-source")).toBe("beyond-source");
    expect(settleTier("beyond-source", null)).toBeNull();
    expect(settleTier(null, "sourced")).toBe("sourced");
  });
});

describe("copy (reviewed EN / PT-BR / ES)", () => {
  it("nine strings, each at most 25 words, all naming the AI and the missing clinical review", () => {
    for (const tier of ["no-source", "beyond-source", "sourced"] as const)
      for (const lang of ["en", "pt", "es"] as const) {
        const s = warnCopy(tier, lang);
        expect(s.split(/\s+/).length).toBeLessThanOrEqual(25);
        expect(s).toMatch(/\b(AI|IA)\b/);
        expect(s).toMatch(/clinically reviewed|revisão clínica|revisión clínica/);
      }
  });
  it("pinned EN lines", () => {
    expect(warnCopy("no-source", "en")).toBe("Health caution: no offline source covers this. The AI answered from memory; not clinically reviewed, and it may be wrong. Ask a health professional.");
    expect(warnCopy("sourced", "pt")).toBe("Aviso de saúde: resposta da IA com base em fonte offline, sem revisão clínica; ainda pode estar errada. Para decisões de saúde, consulte um profissional.");
  });
});
