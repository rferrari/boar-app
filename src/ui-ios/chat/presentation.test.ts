import { describe, it, expect } from "vitest";
import type { AnswerState } from "./answerReducer";
import { answerReceiptShort, pillStep, snippetAutoCollapses, noticeShown, stepsCardShown, stepSpinnerRuns, phaseAnnouncement, previewText, receiptDetails, receiptLine, receiptShort, approxWords, stageLine, stageIcon, loadCrashMessage, generatingSteps, bootEntranceTiming, modelDisplayName, withDisplayNames, offersAskModel, receiptTagKey, noSourceNote, showsInstantSnippet, sourceLanguageLead, declineCopy, declineAfterSnippet, showsAnswerBody } from "./presentation";

// Echoes the key and options, so tests check which string is picked and with what.
const t = (key: string, opts?: Record<string, unknown>) => (opts ? `${key}${JSON.stringify(opts)}` : key);

const chunk = { chunkId: "c", docId: "d", title: "T", body: "b", score: 0, matchType: "hybrid" as const };
const receipt = { modelId: "q", modelLabel: "Qwen3 4B", tokens: 90, tokPerSec: 14.8, ttftMs: 2100, totalMs: 6200, reasonCodes: [] };

describe("stageLine", () => {
  it("says searching before anything happens", () => {
    expect(stageLine({ answerIds: ["a"], sources: [] }, t)).toBe("chat.stage.searching");
  });

  it("counts the articles being read, not their passages (LIVE_RESEARCH #1)", () => {
    const s: AnswerState = { answerIds: ["a"], sources: [chunk, { ...chunk, chunkId: "c2" }], fast: { text: "", stage: "prefill" } };
    expect(stageLine(s, t)).toBe('chat.stage.reading{"count":1}');
    const two: AnswerState = { ...s, sources: [chunk, { ...chunk, chunkId: "c2", docId: "d2" }] };
    expect(stageLine(two, t)).toBe('chat.stage.reading{"count":2}');
  });

  it("says which Deep Research part it is searching (engine: retrieving + detail)", () => {
    const s: AnswerState = {
      answerIds: ["a"],
      sources: [],
      fast: { text: "x", stage: null, outcome: "success" },
      deep: { text: "", stage: "retrieving", detail: { index: 1, count: 3 } },
    };
    expect(stageLine(s, t)).toBe('chat.stage.part{"index":2,"count":3}');
    expect(stageLine({ ...s, deep: { text: "", stage: "retrieving", detail: {} } }, t)).toBe("chat.stage.searching");
  });

  it("names the sub-question when a newer engine sends it (optional detail.subQuestion)", () => {
    const at = (subQuestion: unknown): AnswerState => ({
      answerIds: ["a"],
      sources: [],
      fast: { text: "x", stage: null, outcome: "success" },
      deep: { text: "", stage: "retrieving", detail: { index: 0, count: 2, subQuestion } as never },
    });
    expect(stageLine(at("  How does\nmethane trap heat? "), t)).toBe('chat.stage.partQuestion{"index":1,"count":2,"question":"How does methane trap heat?"}');
    expect(generatingSteps(at("What is CO2?"), t)![0].label).toBe('chat.stage.partQuestion{"index":1,"count":2,"question":"What is CO2?"}');
    // Missing, blank or not a string: the numbered part, as today's engine.
    for (const q of [undefined, "   ", 42]) expect(stageLine(at(q), t)).toBe('chat.stage.part{"index":1,"count":2}');
  });

  it("numbers the parts of a deep pass from 1", () => {
    const s: AnswerState = {
      answerIds: ["a"],
      sources: [],
      fast: { text: "x", stage: null, outcome: "success" },
      deep: { text: "", stage: "synthesizing", detail: { index: 0, count: 3 } },
    };
    expect(stageLine(s, t)).toBe('chat.stage.part{"index":1,"count":3}');
  });

  it("shows nothing once text streams or the answer is done", () => {
    expect(stageLine({ answerIds: ["a"], sources: [], fast: { text: "x", stage: "generating" } }, t)).toBeNull();
    expect(stageLine({ answerIds: ["a"], sources: [], fast: { text: "x", stage: null, outcome: "success" } }, t)).toBeNull();
  });
});

describe("phaseAnnouncement", () => {
  const state: AnswerState = { answerIds: ["a"], sources: [chunk, chunk, chunk] };
  it("announces transitions, never tokens, and errors assertively", () => {
    expect(phaseAnnouncement("searching", state, t)).toEqual({ message: "chat.announce.searching" });
    expect(phaseAnnouncement("done", state, t)).toEqual({ message: 'chat.announce.ready{"count":3}' });
    // CT-2: the count follows the cited sources; nothing cited reads as no source.
    expect(phaseAnnouncement("done", { ...state, cited: [2] }, t)).toEqual({ message: 'chat.announce.ready{"count":1}' });
    expect(phaseAnnouncement("done", { ...state, cited: [] }, t)).toEqual({ message: "chat.announce.readyNoSource" });
    // A calculation or a fixed answer: no sources, never "0 sources" (Tusk R12).
    expect(phaseAnnouncement("done", { ...state, sources: [], cited: [] }, t)).toEqual({ message: "chat.announce.readyPlain" });
    expect(phaseAnnouncement("error", state, t)).toEqual({ message: "chat.error.generic", assertive: true });
    expect(phaseAnnouncement("reading", state, t)).toBeNull();
  });

  it("announces how many places were found, or asks for the city", () => {
    const places = (coverage: "ok" | "none" | "no_pack" | "needs_place", n: number): AnswerState => ({
      answerIds: ["a"],
      sources: [],
      places: {
        places: Array.from({ length: n }, (_, i) => ({ id: `p${i}`, name: `P${i}`, lat: 0, lon: 0, source: "osm" as const })),
        area: { kind: "city", label: "Lisboa" },
        criterion: "diet_match",
        coverage,
        attribution: [],
      },
    });
    expect(phaseAnnouncement("done", places("ok", 4), t)).toEqual({ message: 'chat.announce.placesFound{"count":4}' });
    expect(phaseAnnouncement("done", places("none", 0), t)).toEqual({
      message: 'chat.places.noneInCity{"city":"Lisboa","filter":null}',
    });
    expect(phaseAnnouncement("done", places("no_pack", 0), t)).toEqual({ message: "chat.places.noPackTitle" });
    expect(phaseAnnouncement("done", places("needs_place", 0), t)).toEqual({ message: "chat.places.whichCity" });
  });
});

describe("receiptLine", () => {
  it("lists model, speed in tok/s, time to start and total", () => {
    expect(receiptLine(receipt, "pt-BR", t)).toBe(
      'chat.receipt.answeredIn{"time":"6,2 s"} · Qwen3 4B · chat.receipt.speed{"rate":"15"} · chat.receipt.started{"time":"2,1 s"} · chat.receipt.offline'
    );
  });

  it("uses the source-passage form for extractive answers", () => {
    expect(receiptLine({ ...receipt, modelId: "extractive", tokens: 0, totalMs: 400 }, "en-US", t)).toBe(
      'chat.receipt.answeredIn{"time":"0.4 s"} · chat.receipt.sourcePassage · chat.receipt.offline'
    );
    expect(receiptLine({ ...receipt, modelId: "places", tokens: 0, totalMs: 300 }, "en-US", t)).toBe(
      'chat.receipt.answeredIn{"time":"0.3 s"} · chat.receipt.offlineMap · chat.receipt.offline'
    );
  });

  it("names the engine's no-source answer in the app's language (I18N-2), never its English label", () => {
    expect(receiptLine({ ...receipt, modelId: "grounding-guard", modelLabel: "No offline source", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 200 }, "pt-BR", t)).toBe(
      'chat.receipt.answeredIn{"time":"0,2 s"} · chat.receipt.noOfflineSource · chat.receipt.offline'
    );
  });

  it("names the exact conversion in the app's language (Tusk 7e9687a)", () => {
    expect(receiptLine({ ...receipt, modelId: "calculator", modelLabel: "Calculator", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 10 }, "pt-BR", t)).toBe(
      'chat.receipt.answeredIn{"time":"0 s"} · chat.receipt.calculator · chat.receipt.offline'
    );
  });

  it("omits speed when nothing was generated", () => {
    expect(receiptLine({ ...receipt, tokens: 0, tokPerSec: 0, ttftMs: 0 }, "en-US", t)).toBe(
      'chat.receipt.answeredIn{"time":"6.2 s"} · Qwen3 4B · chat.receipt.offline'
    );
  });
});

describe("receiptShort", () => {
  it("is the total time and the speed in tok/s", () => {
    expect(receiptShort(receipt, "pt-BR", t)).toEqual(["6,2 s", 'chat.receipt.speed{"rate":"15"}']);
    // One decimal under 10 tok/s: 9.1 s at 8.4 tok/s reads "9,1 s · 8,4 tok/s".
    expect(receiptShort({ ...receipt, tokPerSec: 8.4, totalMs: 9100 }, "pt-BR", t)).toEqual(["9,1 s", 'chat.receipt.speed{"rate":"8,4"}']);
  });

  it("keeps only the time when no model generated the answer", () => {
    expect(receiptShort({ ...receipt, modelId: "extractive", totalMs: 400 }, "en-US", t)).toEqual(["0.4 s"]);
    expect(receiptShort({ ...receipt, modelId: "places", totalMs: 300 }, "en-US", t)).toEqual(["0.3 s"]);
    expect(receiptShort({ ...receipt, tokPerSec: 0 }, "en-US", t)).toEqual(["6.2 s"]);
  });
});

describe("receiptDetails", () => {
  it("includes measured prefill and context only when present", () => {
    const labels = (r: typeof receipt & Record<string, unknown>) => receiptDetails(r, "en-US", t).map((d) => d.label);
    expect(labels(receipt)).not.toContain("chat.receipt.prefill");
    expect(labels({ ...receipt, prefillMs: 900, ctxTokens: 1100 })).toEqual(
      expect.arrayContaining(["chat.receipt.prefill", "chat.receipt.context"])
    );
  });

  it("gives the run's model, its speed in tok/s and words/s, and the token counts", () => {
    const rows = receiptDetails({ ...receipt, ctxTokens: 1100 }, "pt-BR", t);
    const value = (label: string) => rows.find((d) => d.label === label)?.value;
    expect(value("chat.receipt.start")).toBe("2,1 s");
    expect(value("chat.receipt.model")).toBe(receipt.modelLabel);
    expect(value("chat.receipt.speedLabel")).toBe('chat.receipt.speedBoth{"rate":"15","words":"11"}');
    expect(value("chat.receipt.words")).toBe('chat.receipt.tokenCount{"tokens":"90","words":"68"}');
    expect(value("chat.receipt.context")).toBe('chat.receipt.tokenCount{"tokens":"1.100","words":"825"}');
  });

  it("leaves out model and speed when no model wrote the answer", () => {
    const labels = receiptDetails({ ...receipt, modelId: "extractive", tokens: 0, tokPerSec: 0 }, "en-US", t).map((d) => d.label);
    expect(labels).not.toContain("chat.receipt.model");
    expect(labels).not.toContain("chat.receipt.speedLabel");
  });
});

describe("approxWords", () => {
  it("rounds to whole words, with one decimal under a word", () => {
    expect(approxWords(10.7, "pt-BR")).toBe("8");
    expect(approxWords(2000, "en-US")).toBe("1,500");
    expect(approxWords(1, "pt-BR")).toBe("0,8");
  });
});

describe("previewText", () => {
  it("keeps short passages whole and cuts long ones at a word", () => {
    expect(previewText("Pinch the nose.")).toBe("Pinch the nose.");
    const long = "When attempting to stop a nosebleed at home, lean forward, pinch the soft part of the nose and keep the pressure for ten minutes without letting go.";
    const p = previewText(long, 60);
    expect(p.endsWith("…")).toBe(true);
    expect(p.length).toBeLessThanOrEqual(61);
    expect(long.startsWith(p.slice(0, -1))).toBe(true);
    expect(p).not.toMatch(/[ ,]…$/);
  });

  it("flattens line breaks", () => {
    expect(previewText("a\n\nb")).toBe("a b");
  });
});

describe("locating announcement", () => {
  it("says once that the app is finding the position and the city can be typed", () => {
    expect(phaseAnnouncement("locating", { answerIds: [], sources: [] } as AnswerState, t)).toEqual({ message: "chat.announce.locating" });
  });
});

describe("stageIcon", () => {
  it("gives each step the mockup's kind of icon", () => {
    expect(stageIcon("searching")).toBe("search");
    expect(stageIcon("generating")).toBe("zap");
    expect(stageIcon("locating")).toBe("map-pin");
    expect(stageIcon("done")).toBe("circle");
  });
});

describe("loadCrashMessage (Boar CR-2)", () => {
  it("names the model that closed the app and the one we went back to", () => {
    expect(loadCrashMessage({ crashedLabel: "Qwen3 4B", fallbackLabel: "Qwen2.5 1.5B" }, t)).toBe(
      'chat.loadCrash.message{"model":"Qwen3 4B","fallback":"Qwen2.5 1.5B"}'
    );
  });

  it("says nothing without a crash, and doesn't claim a switch back without a previous model", () => {
    expect(loadCrashMessage(null, t)).toBeNull();
    expect(loadCrashMessage({ crashedLabel: "Qwen3 4B", fallbackLabel: "" }, t)).toBe('chat.loadCrash.messageNoFallback{"model":"Qwen3 4B"}');
  });
});

describe("weak sources announcement", () => {
  it("says the answer has no source from the library", () => {
    const st = { answerIds: [], sources: [], weakSources: true } as AnswerState;
    expect(phaseAnnouncement("done", st, t)).toEqual({ message: "chat.announce.readyNoSource" });
  });

  it("state A announces the card as the answer", () => {
    const st = { answerIds: [], sources: [], weakSources: true, weakDeclined: true } as AnswerState;
    expect(phaseAnnouncement("done", st, t)).toEqual({ message: "chat.weak.declinedTitle. chat.weak.declinedBody" });
  });
});

describe("generatingSteps (mockup: all steps from the start)", () => {
  const base = { answerIds: ["a"], sources: [] } as AnswerState;
  const tier = (stage: string) => ({ text: "", stage, outcome: undefined }) as never;
  it("starts with search active and the rest pending", () => {
    const st = generatingSteps(base, t)!;
    expect(st.map((s) => s.status)).toEqual(["active", "pending", "pending"]);
    expect(st[1].label).toBe("chat.stage.thinking");
  });

  it("reads N sources, then writes", () => {
    const withSources = { ...base, sources: [{ chunkId: "c" }] } as unknown as AnswerState;
    const reading = generatingSteps({ ...withSources, fast: tier("prefill") }, t)!;
    expect(reading.map((s) => s.status)).toEqual(["done", "active", "pending"]);
    expect(reading[1].label).toBe('chat.stage.reading{"count":1}');
    const writing = generatingSteps({ ...withSources, fast: tier("generating") }, t)!;
    expect(writing.map((s) => s.status)).toEqual(["done", "done", "active"]);
    expect(writing[2].label).toBe("chat.stage.writing");
  });

  it("is null once the answer is done", () => {
    expect(generatingSteps({ ...base, fast: { text: "x", stage: null, outcome: "success" } } as AnswerState, t)).toBeNull();
  });

  it("counts articles in the reading step and names the Deep Research part in the searching one", () => {
    const passages = { ...base, sources: [chunk, { ...chunk, chunkId: "c2" }, { ...chunk, chunkId: "c3", docId: "d2" }] } as AnswerState;
    expect(generatingSteps({ ...passages, fast: tier("prefill") }, t)![1].label).toBe('chat.stage.reading{"count":2}');
    const deep = { ...passages, fast: { text: "x", stage: null, outcome: "success" }, deep: { text: "", stage: "retrieving", detail: { index: 0, count: 3 } } } as AnswerState;
    const st = generatingSteps(deep, t)!;
    expect(st[0]).toMatchObject({ status: "active", label: 'chat.stage.part{"index":1,"count":3}', short: "chat.stepShort.search" });
    // Past the search the step is plain "searching" again: the part was the search's progress.
    const synth = generatingSteps({ ...deep, deep: { text: "", stage: "synthesizing" } } as AnswerState, t)!;
    expect(synth[0].label).toBe("chat.stage.searching");
  });
});

describe("bootEntranceTiming (splash → chat, Iris/Prism)", () => {
  const d = { slow: 320, base: 220 };
  it("waits 320 ms and fades 220 ms on the first mount after launch", () => {
    expect(bootEntranceTiming(true, false, d)).toEqual({ delay: 320, fade: 220 });
  });

  it("with reduce motion keeps the wait and drops the fade (the delay keeps the two boars apart)", () => {
    expect(bootEntranceTiming(true, true, d)).toEqual({ delay: 320, fade: 0 });
  });

  it("shows at once on later mounts", () => {
    expect(bootEntranceTiming(false, false, d)).toBeNull();
    expect(bootEntranceTiming(false, true, d)).toBeNull();
  });
});

describe("declined with an incomplete library (Prism HX-1 residual)", () => {
  it("never says a plain 'couldn't find' when indexing stopped part-way", () => {
    const st = { answerIds: [], sources: [], weakSources: true, weakDeclined: true } as AnswerState;
    expect(phaseAnnouncement("done", st, t, true)).toEqual({ message: "chat.weak.declinedTitleIncomplete. chat.weak.declinedBodyIncomplete" });
    expect(phaseAnnouncement("done", st, t)).toEqual({ message: "chat.weak.declinedTitle. chat.weak.declinedBody" });
  });
});

describe("modelDisplayName / withDisplayNames (Prism CR-3)", () => {
  const models = [
    { id: "qwen3-4b-instruct-2507-q4km", label: "Qwen3-4B-Instruct-2507 (Q4_K_M)", filename: "models/qwen3-4b-instruct-2507-q4km.gguf" },
    { id: "qwen2.5-1.5b-instruct-q4km", label: "Qwen2.5-1.5B-Instruct (Q4_K_M)", filename: "models/qwen2.5-1.5b-instruct-q4km.gguf" },
  ];
  it("names the models as the catalog does, from a file path, an id or a label", () => {
    const crash = { crashedLabel: "models/qwen3-4b-instruct-2507-q4km.gguf", fallbackLabel: "models/qwen2.5-1.5b-instruct-q4km.gguf", at: 1 };
    expect(withDisplayNames(crash, models, t)).toEqual({ crashedLabel: "Qwen3-4B-Instruct-2507 (Q4_K_M)", fallbackLabel: "Qwen2.5-1.5B-Instruct (Q4_K_M)", at: 1 });
    expect(modelDisplayName("qwen2.5-1.5b-instruct-q4km", models, t)).toBe("Qwen2.5-1.5B-Instruct (Q4_K_M)");
    expect(modelDisplayName("Qwen2.5-1.5B-Instruct (Q4_K_M)", models, t)).toBe("Qwen2.5-1.5B-Instruct (Q4_K_M)");
  });
  it("shows the short name when the catalog has one (Ledger displayName)", () => {
    const short = [{ ...models[0], displayName: "Qwen3 4B" }, { ...models[1], displayName: "Qwen2.5 1.5B" }];
    const crash = { crashedLabel: "models/qwen3-4b-instruct-2507-q4km.gguf", fallbackLabel: "qwen2.5-1.5b-instruct-q4km", at: 1 };
    expect(withDisplayNames(crash, short, t)).toEqual({ crashedLabel: "Qwen3 4B", fallbackLabel: "Qwen2.5 1.5B", at: 1 });
  });
  it("the receipt names a catalog answer model by its tier, never the technical label (r4to)", () => {
    const line = receiptLine({ ...receipt, modelId: "qwen3-4b-instruct-2507-q4km", modelLabel: "Qwen3-4B-Instruct-2507 (Q4_K_M)" }, "en-US", t);
    expect(line).toContain(" · flows.onboarding.answerTier.");
    expect(line).not.toContain("Q4_K_M");
  });
  it("the crash banner names catalog answer models by their tier", () => {
    const tiered = [{ ...models[0], kind: "llm" as const, answerTier: "default" as const }, { ...models[1], kind: "llm" as const, answerTier: "compact" as const }];
    const crash = { crashedLabel: "models/qwen3-4b-instruct-2507-q4km.gguf", fallbackLabel: "qwen2.5-1.5b-instruct-q4km", at: 1 };
    expect(withDisplayNames(crash, tiered, t)).toEqual({ crashedLabel: "flows.onboarding.answerTier.default", fallbackLabel: "flows.onboarding.answerTier.compact", at: 1 });
  });

  it("never shows an internal path for a model outside the catalog, and keeps an empty fallback empty", () => {
    expect(modelDisplayName("models/hf/SmolLM3-3B-Q4_K_M.gguf", models, t)).toBe("SmolLM3-3B-Q4_K_M");
    expect(modelDisplayName("", models, t)).toBe("");
    expect(withDisplayNames(null, models, t)).toBeNull();
  });
});

describe("offersAskModel (Prism CT-4)", () => {
  const r = (modelId: string) => ({ instantDone: { receipt: { ...receipt, modelId } } });
  it("after a source passage, yes; after the engine's fixed current-events answer, no", () => {
    expect(offersAskModel(r("extractive"))).toBe(true);
    expect(offersAskModel(r("grounding-guard"))).toBe(false);
    expect(offersAskModel(r("calculator"))).toBe(false);
  });
  it("never once the model answered, for places, or before the instant pass ends", () => {
    expect(offersAskModel({ ...r("extractive"), fast: {} })).toBe(false);
    expect(offersAskModel({ ...r("places"), places: {} })).toBe(false);
    expect(offersAskModel({})).toBe(false);
  });
});

describe("receiptTagKey (Iris CT-5)", () => {
  const src = [{ chunkId: "c", docId: "d", title: "T", body: "b", score: 1, matchType: "hybrid" as const }];
  const base = { answerIds: ["a"], sources: src, fast: { text: "x", stage: null, outcome: "success", receipt } } as AnswerState;
  it("general knowledge only when nothing covered the question; no source cited when found passages weren't cited", () => {
    expect(receiptTagKey({ ...base, sources: [], weakSources: true, cited: [] })).toBe("chat.weak.receipt");
    // a428bb6: the engine also flags the uncited case with weak_sources; the found passages decide.
    expect(receiptTagKey({ ...base, weakSources: true, cited: [] })).toBe("chat.weak.receiptUncited");
    expect(receiptTagKey({ ...base, cited: [] })).toBe("chat.weak.receiptUncited");
    expect(receiptTagKey({ ...base, cited: [1] })).toBeNull();
    expect(receiptTagKey(base)).toBeNull();
    // CALC-1: the exact conversion shows "Calculation" by the name.
    const calc = { ...receipt, modelId: "calculator", tokens: 0, tokPerSec: 0 };
    expect(receiptTagKey({ answerIds: ["a"], sources: [], cited: [], instantDone: { outcome: "success", receipt: calc } } as AnswerState)).toBe("chat.receipt.calculator");
  });
});

describe("tag and note together (Prism: the tag changes, the warning never goes)", () => {
  const src = [{ chunkId: "c", docId: "d", title: "T", body: "b", score: 1, matchType: "hybrid" as const }];
  const base = { answerIds: ["a"], sources: src, fast: { text: "x", stage: null, outcome: "success", receipt } } as AnswerState;
  it("cited = [] with passages → 'no source cited' + the CT-5 note", () => {
    const a = { ...base, cited: [] };
    expect([receiptTagKey(a), noSourceNote(a, false)]).toEqual(["chat.weak.receiptUncited", "uncited"]);
  });
  it("cited = [] with passages AND weak_sources (a428bb6) → still 'no source cited' + the CT-5 note", () => {
    const a = { ...base, cited: [], weakSources: true };
    expect([receiptTagKey(a), noSourceNote(a, false)]).toEqual(["chat.weak.receiptUncited", "uncited"]);
  });
  it("weak_sources with nothing on the topic → 'general knowledge' + note B", () => {
    const a = { ...base, sources: [], weakSources: true };
    expect([receiptTagKey(a), noSourceNote(a, false)]).toEqual(["chat.weak.receipt", "weak"]);
  });
  it("a cited answer has neither; a decline and a places list have their own cards", () => {
    expect([receiptTagKey({ ...base, cited: [1] }), noSourceNote({ ...base, cited: [1] }, false)]).toEqual([null, null]);
    expect(noSourceNote({ ...base, weakSources: true, weakDeclined: true }, false)).toBeNull();
    expect(noSourceNote({ ...base, cited: [] }, true)).toBeNull();
  });
});

describe("showsInstantSnippet (Prism DUP-1)", () => {
  const src = [{ chunkId: "c", docId: "d", title: "Appropedia: How to survive an earthquake", body: "b", score: 1, matchType: "hybrid" as const }];
  const instant = { text: "During an earthquake: Drop, cover, and hold on!…", sourceIndex: 0, confidence: 0.9 };
  it("one block: the engine's excerpt replaces the instant card", () => {
    expect(showsInstantSnippet({ answerIds: ["a"], sources: src, instant } as AnswerState)).toBe(true);
    expect(showsInstantSnippet({ answerIds: ["a"], sources: src, instant, extract: "From the offline source: … [1]" } as AnswerState)).toBe(false);
  });
  it("no card without a passage, nor when nothing was on the topic", () => {
    expect(showsInstantSnippet({ answerIds: ["a"], sources: src } as AnswerState)).toBe(false);
    expect(showsInstantSnippet({ answerIds: ["a"], sources: [], instant, weakSources: true } as AnswerState)).toBe(false);
  });
});

describe("sourceLanguageLead (Tusk 29d7e52, Sextant q8 PT)", () => {
  it("takes the engine's language lead off the passage, PT and EN", () => {
    expect(sourceLanguageLead("Da fonte offline (em inglês):\nA monsoon is a seasonal change…")).toEqual({ lang: "em inglês", body: "A monsoon is a seasonal change…" });
    expect(sourceLanguageLead("From the offline source (in Portuguese):\nA monção é…")).toEqual({ lang: "in Portuguese", body: "A monção é…" });
  });
  it("leaves any other text alone", () => {
    expect(sourceLanguageLead("A monsoon is a seasonal change…")).toEqual({ lang: null, body: "A monsoon is a seasonal change…" });
    expect(sourceLanguageLead("From the offline source:\nSteps…")).toEqual({ lang: null, body: "From the offline source:\nSteps…" });
  });
});

describe("declineCopy (Tusk 237764a: the compact model's cited answer withheld)", () => {
  const src = [{ chunkId: "c", docId: "d", title: "Monsoon", body: "b", score: 1, matchType: "hybrid" as const }];
  it("passages found but every citation removed → says the passages don't back it, never 'couldn't find'", () => {
    const a = { answerIds: ["a"], sources: src, weakSources: true, weakDeclined: true } as AnswerState;
    expect(declineCopy(a)).toEqual({ title: "chat.weak.unsupportedTitle", body: "chat.weak.unsupportedBody" });
    expect(declineCopy(a, true).title).toBe("chat.weak.unsupportedTitle");
    expect(phaseAnnouncement("done", { ...a, fast: { text: "", stage: null, outcome: "success" } }, t)?.message).toBe("chat.weak.unsupportedTitle. chat.weak.unsupportedBody");
  });
  it("nothing on the topic → 'couldn't find', or the incomplete library", () => {
    const a = { answerIds: ["a"], sources: [], weakSources: true, weakDeclined: true } as AnswerState;
    expect(declineCopy(a).title).toBe("chat.weak.declinedTitle");
    expect(declineCopy(a, true).title).toBe("chat.weak.declinedTitleIncomplete");
  });
});

describe("declineAfterSnippet (Boar, Piston ecb83d3: PT question, EN passage, citations removed)", () => {
  const src = [{ chunkId: "c", docId: "d", title: "Monsoon", body: "b", score: 1, matchType: "hybrid" as const }];
  const fast = { text: "", stage: null, outcome: "success" as const };
  const held = { answerIds: ["a"], sources: src, weakSources: true, weakDeclined: true, instant: { text: "Da fonte offline (em inglês):\nA monsoon is…", sourceIndex: 0, confidence: 0.8 }, fast } as AnswerState;
  it("under the instant passage → a quiet line, and the announcement says the same line, not the error card", () => {
    expect(declineAfterSnippet(held)).toBe(true);
    expect(phaseAnnouncement("done", held, t)?.message).toBe("chat.weak.heldAfterSnippet");
  });
  it("no passage shown → the card stays (nothing found, or the engine's excerpt answers)", () => {
    expect(declineAfterSnippet({ ...held, instant: undefined })).toBe(false);
    expect(declineAfterSnippet({ ...held, extract: "x" } as AnswerState)).toBe(false);
    expect(declineAfterSnippet({ ...held, sources: [] })).toBe(false);
    expect(phaseAnnouncement("done", { ...held, instant: undefined }, t)?.message).toBe("chat.weak.unsupportedTitle. chat.weak.unsupportedBody");
  });
  it("not a decline → no line", () => {
    expect(declineAfterSnippet({ ...held, weakDeclined: undefined })).toBe(false);
  });
});

describe("showsAnswerBody (Tusk 05d1e6b: the decline's sentence in done.finalText)", () => {
  it("hides the body on a decline, so the card's sentence isn't shown twice; the text stays in the state", () => {
    const declined = { answerIds: ["a"], sources: [], weakSources: true, weakDeclined: true, fast: { text: "Não encontrei isso no acervo deste celular.", stage: null, outcome: "success" } } as AnswerState;
    expect(showsAnswerBody(declined)).toBe(false);
    expect(declined.fast?.text).toBe("Não encontrei isso no acervo deste celular.");
  });
  it("shows it otherwise, weak answers included", () => {
    expect(showsAnswerBody({ answerIds: ["a"], sources: [], weakSources: true, fast: { text: "x", stage: null, outcome: "success" } } as AnswerState)).toBe(true);
  });
});

describe("stepSpinnerRuns (GFXINFO: a ring turning for the whole generation asked for a frame every vsync)", () => {
  const base: AnswerState = { answerIds: ["a"], sources: [] };
  it("turns while nothing is written yet: searching, reading, first token pending", () => {
    expect(stepSpinnerRuns(base)).toBe(true);
    expect(stepSpinnerRuns({ ...base, fast: { text: "", stage: "prefill" } })).toBe(true);
    expect(stepSpinnerRuns({ ...base, fast: { text: "", stage: "generating" } })).toBe(true);
  });
  it("stands still once the text streams (the text and caret show progress)", () => {
    expect(stepSpinnerRuns({ ...base, fast: { text: "Raft elects", stage: "generating" } })).toBe(false);
    expect(stepSpinnerRuns({ ...base, extract: "Pinch the nose" })).toBe(false);
  });
  it("a Deepen: turns until the deep text starts, even though the fast text is on screen", () => {
    const fastDone = { text: "Short answer.", stage: null, outcome: "success" as const };
    expect(stepSpinnerRuns({ ...base, fast: fastDone, deep: { text: "", stage: "prefill" } })).toBe(true);
    expect(stepSpinnerRuns({ ...base, fast: fastDone, deep: { text: "More", stage: "generating" } })).toBe(false);
  });
});

describe("noticeShown (SEND-MOTION: no animated block without content)", () => {
  it("only for a tier that ended without success", () => {
    expect(noticeShown(undefined)).toBe(false);
    expect(noticeShown({})).toBe(false);
    expect(noticeShown({ outcome: "success" })).toBe(false);
    for (const outcome of ["stopped", "interrupted", "timeout", "error"]) expect(noticeShown({ outcome })).toBe(true);
  });
  it("an interrupted run shows it even over a finished tier", () => {
    expect(noticeShown({ outcome: "success" }, true)).toBe(true);
    expect(noticeShown(undefined, true)).toBe(false);
  });
});

describe("stepsCardShown (SEND-MOTION D3, option A)", () => {
  const steps = [{}];
  it("shows the steps until the answer's own text starts", () => {
    expect(stepsCardShown({ sources: [], answerIds: [] } as unknown as AnswerState, steps)).toBe(true);
    expect(stepsCardShown({ sources: [], answerIds: [], fast: { text: "", stage: "generating" } } as unknown as AnswerState, steps)).toBe(true);
  });
  it("shrinks at the first words, of the model or of the extract", () => {
    expect(stepsCardShown({ sources: [], answerIds: [], fast: { text: "Boil", stage: null } } as unknown as AnswerState, steps)).toBe(false);
    expect(stepsCardShown({ sources: [], answerIds: [], extract: "Apply pressure" } as unknown as AnswerState, steps)).toBe(false);
  });
  it("a Deepen shows its own steps until the deep text starts", () => {
    const deep = { sources: [], answerIds: [], fast: { text: "Short.", stage: null, outcome: "success" }, deep: { text: "", stage: "generating" } };
    expect(stepsCardShown(deep as unknown as AnswerState, steps)).toBe(true);
    expect(stepsCardShown({ ...deep, deep: { text: "Longer", stage: null } } as unknown as AnswerState, steps)).toBe(false);
  });
  it("no steps (done, stopping): hidden", () => {
    expect(stepsCardShown({ sources: [], answerIds: [] } as unknown as AnswerState, null)).toBe(false);
  });
});

describe("answerReceiptShort / receiptTagKey for a decline (Prism CX-9)", () => {
  it("a decline shows its receipt, time only, no 'general knowledge' tag", () => {
    expect(answerReceiptShort(true, receipt, "en", t)).toEqual([answerReceiptShort(false, receipt, "en", t)[0]]);
    expect(answerReceiptShort(false, receipt, "en", t).length).toBe(2);
    const declined = { sources: [], answerIds: [], weakSources: true, weakDeclined: true, fast: { text: "", stage: null, outcome: "success", receipt } };
    expect(receiptTagKey(declined as unknown as AnswerState)).toBeNull();
  });
});

describe("snippetAutoCollapses (Prism F2-10)", () => {
  it("folds once the model's answer is done, not when the snippet is the final answer", () => {
    expect(snippetAutoCollapses({ fast: { outcome: "success" } }, false)).toBe(true);
    expect(snippetAutoCollapses({ fast: { outcome: "success" } }, true)).toBe(false);
    expect(snippetAutoCollapses({ fast: {} }, false)).toBe(false);
  });
  it("stays whole when the model's answer was declined: the passage is the answer", () => {
    expect(snippetAutoCollapses({ fast: { outcome: "success" }, weakDeclined: true }, false)).toBe(false);
  });
});

describe("pillStep (iPhone v9: 'Writing · 6 s' over '6 s' in the header)", () => {
  it("shows the current step, and keeps the last one once the steps are over", () => {
    expect(pillStep("Writing", "Reading")).toBe("Writing");
    expect(pillStep(undefined, "Writing")).toBe("Writing");
    expect(pillStep(undefined, undefined)).toBeUndefined();
  });
});
