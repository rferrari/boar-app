import { describe, it, expect } from "vitest";
import type { RetrievedChunk } from "../../rag/retrieve.types";
import type { AnswerEvent, AnswerReceipt as Receipt } from "./answerEvents";
import { answerSourceSplit, sourcesCardMode } from "./sourceLabel";
import { answerShowsEmergencyNote } from "./safetyNote";
import {
  answerPhase,
  answerReducer,
  attachAnswer,
  canDeepen,
  noSourceKind,
  initialAnswer,
  isAnswerActive,
  type AnswerState, isLocating, asInterrupted } from "./answerReducer";

const chunk = (id: string): RetrievedChunk => ({
  chunkId: id,
  docId: `doc-${id}`,
  title: `Title ${id}`,
  body: `Body ${id}`,
  score: 0.5,
  matchType: "hybrid",
});

const receipt = (over: Partial<Receipt> = {}): Receipt => ({
  modelId: "qwen3-4b",
  modelLabel: "Qwen3 4B",
  tokens: 120,
  tokPerSec: 14.8,
  ttftMs: 2100,
  totalMs: 6200,
  reasonCodes: [],
  ...over,
});

const run = (events: AnswerEvent[], id = "a1"): AnswerState => events.reduce(answerReducer, initialAnswer(id));

describe("answerReducer", () => {
  it("walks a fast answer through searching, reading, generating and done", () => {
    const steps: AnswerEvent[] = [
      { answerId: "a1", type: "stage", stage: "retrieving", tier: "fast", at: 0 },
      { answerId: "a1", type: "sources", tier: "fast", sources: [chunk("x"), chunk("y")] },
      { answerId: "a1", type: "stage", stage: "prefill", tier: "fast", at: 1 },
      { answerId: "a1", type: "token", tier: "fast", text: "Raft " },
      { answerId: "a1", type: "token", tier: "fast", text: "elects [1]." },
      { answerId: "a1", type: "done", tier: "fast", outcome: "success", receipt: receipt() },
    ];
    const phases = steps.map((_, i) => answerPhase(run(steps.slice(0, i + 1))));
    expect(phases).toEqual(["searching", "searching", "reading", "generating", "generating", "done"]);

    const final = run(steps);
    expect(final.fast?.text).toBe("Raft elects [1].");
    expect(final.fast?.receipt?.tokPerSec).toBe(14.8);
    expect(final.sources.map((s) => s.chunkId)).toEqual(["x", "y"]);
    expect(isAnswerActive(final)).toBe(false);
  });

  it("ignores events from another answer", () => {
    const state = run([
      { answerId: "old", type: "token", tier: "fast", text: "stale" },
      { answerId: "old", type: "sources", tier: "fast", sources: [chunk("z")] },
    ]);
    expect(state).toEqual(initialAnswer("a1"));
  });

  it("keeps source numbering stable and deduplicated across tiers", () => {
    const state = run([
      { answerId: "a1", type: "sources", tier: "fast", sources: [chunk("x"), chunk("y")] },
      { answerId: "a1", type: "sources", tier: "deep", sources: [chunk("y"), chunk("z")] },
    ]);
    expect(state.sources.map((s) => s.chunkId)).toEqual(["x", "y", "z"]);
  });

  it("does not change a tier after its done", () => {
    const state = run([
      { answerId: "a1", type: "token", tier: "fast", text: "partial" },
      { answerId: "a1", type: "done", tier: "fast", outcome: "stopped", receipt: receipt() },
      { answerId: "a1", type: "token", tier: "fast", text: " late" },
      { answerId: "a1", type: "stage", stage: "generating", tier: "fast", at: 9 },
      { answerId: "a1", type: "done", tier: "fast", outcome: "success", receipt: receipt() },
    ]);
    expect(state.fast?.text).toBe("partial");
    expect(answerPhase(state)).toBe("stopped");
  });

  it("carries the error code for the error notice", () => {
    const state = run([
      {
        answerId: "a1",
        type: "done",
        tier: "fast",
        outcome: "error",
        receipt: receipt({ tokens: 0 }),
        error: { code: "oom", message: "out of memory" },
      },
    ]);
    expect(answerPhase(state)).toBe("error");
    expect(state.fast?.error?.code).toBe("oom");
  });

  it("treats an extractive instant answer as done without a model pass", () => {
    const state = run([
      { answerId: "a1", type: "sources", tier: "instant", sources: [chunk("x")] },
      { answerId: "a1", type: "instant", snippet: { text: "Canberra is the capital.", sourceIndex: 1 }, confidence: 0.92 },
      { answerId: "a1", type: "done", tier: "instant", outcome: "success", receipt: receipt({ modelId: "extractive", tokens: 0 }) },
    ]);
    expect(state.instant?.confidence).toBe(0.92);
    expect(state.instantDone?.receipt.modelId).toBe("extractive");
    expect(state.fast).toBeUndefined();
    expect(answerPhase(state)).toBe("done");
  });

  it("offers Deepen only after a successful fast pass, and hands the phase to the deep pass", () => {
    const fastDone: AnswerEvent[] = [
      { answerId: "a1", type: "token", tier: "fast", text: "Short answer." },
      { answerId: "a1", type: "done", tier: "fast", outcome: "success", receipt: receipt() },
    ];
    expect(canDeepen(run(fastDone))).toBe(false);

    const offered = run([...fastDone, { answerId: "a1", type: "deep_available", estSeconds: 120 }]);
    expect(canDeepen(offered)).toBe(true);

    const deepening = answerReducer(attachAnswer(offered, "a2"), {
      answerId: "a2",
      type: "stage",
      stage: "synthesizing",
      tier: "deep",
      at: 5,
      detail: { index: 1, count: 3 },
    });
    expect(canDeepen(deepening)).toBe(false);
    expect(answerPhase(deepening)).toBe("synthesizing");
    expect(deepening.deep?.detail).toEqual({ index: 1, count: 3 });
    expect(deepening.fast?.text).toBe("Short answer.");
  });

  it("keeps a places answer as sent, with the location status", () => {
    const place = { id: "osm:node/1", name: "Mão Verde", lat: 1, lon: 2, source: "osm" as const };
    const state = run([
      { answerId: "a1", type: "stage", stage: "retrieving", tier: "instant", at: 0 },
      { answerId: "a1", type: "location", status: "granted", accuracyM: 12 },
      {
        answerId: "a1",
        type: "places",
        tier: "instant",
        places: [place],
        area: { kind: "near", radiusM: 2000 },
        criterion: "diet_match",
        coverage: "ok",
        attribution: [{ source: "osm", date: "2026-08", license: "ODbL" }],
      },
      { answerId: "a1", type: "done", tier: "instant", outcome: "success", receipt: receipt({ modelId: "places", tokens: 0 }) },
    ]);
    expect(state.location).toEqual({ status: "granted", accuracyM: 12, ageS: undefined });
    expect(state.places?.places).toEqual([place]);
    expect(state.places?.criterion).toBe("diet_match");
    expect(answerPhase(state)).toBe("done");
  });

  it("reports an instant-tier error", () => {
    const state = run([
      { answerId: "a1", type: "done", tier: "instant", outcome: "error", receipt: receipt(), error: { code: "unknown", message: "x" } },
    ]);
    expect(answerPhase(state)).toBe("error");
  });

  it("flags a model whose weights stream from storage", () => {
    const state = run([
      { answerId: "a1", type: "warning", code: "model_streams_from_storage", message: "slow" },
    ]);
    expect(state.streamsFromStorage).toBe(true);
  });

  it("does not offer Deepen after a stopped fast pass", () => {
    const state = run([
      { answerId: "a1", type: "done", tier: "fast", outcome: "stopped", receipt: receipt() },
      { answerId: "a1", type: "deep_available" },
    ]);
    expect(canDeepen(state)).toBe(false);
  });
});

describe("locating (Boar GPS-1)", () => {
  const base = { answerIds: ["a"], sources: [] } as AnswerState;
  it("is its own phase while the engine waits for the GPS and nothing is listed", () => {
    const waiting = answerReducer(base, { type: "location", answerId: "a", status: "locating" });
    expect(isLocating(waiting)).toBe(true);
    expect(answerPhase(waiting)).toBe("locating");
  });

  it("ends when a fix or a list arrives", () => {
    const waiting = answerReducer(base, { type: "location", answerId: "a", status: "locating" });
    expect(isLocating(answerReducer(waiting, { type: "location", answerId: "a", status: "granted" }))).toBe(false);
    expect(isLocating({ ...waiting, places: { coverage: "ok", places: [], area: { kind: "near" } } as never })).toBe(false);
  });
});

describe("asInterrupted (FS-1)", () => {
  it("saves a stop caused by the screen going away as interrupted, and leaves finished tiers alone", () => {
    const st = {
      answerIds: ["a"],
      sources: [],
      fast: { text: "Half an ans", stage: null, outcome: "stopped" },
      deep: { text: "", stage: "generating", outcome: undefined },
    } as unknown as AnswerState;
    const out = asInterrupted(st);
    expect(out.fast?.outcome).toBe("interrupted");
    expect(out.fast?.text).toBe("Half an ans");
    expect(out.deep?.outcome).toBe("interrupted");
    const done = { answerIds: ["a"], sources: [], fast: { text: "Done.", stage: null, outcome: "success" } } as unknown as AnswerState;
    expect(asInterrupted(done).fast?.outcome).toBe("success");
  });
});

describe("weak sources (Tusk weak_sources, Iris spec)", () => {
  it("marks the answer as answered from general knowledge", () => {
    const base = { answerIds: ["a"], sources: [] } as AnswerState;
    const s = answerReducer(base, { type: "warning", answerId: "a", code: "weak_sources" as never, message: "No offline source covers this question." });
    expect(s.weakSources).toBe(true);
    expect(answerReducer(base, { type: "warning", answerId: "a", code: "model_streams_from_storage", message: "" }).weakSources).toBeUndefined();
  });

  it("state A: the compact model declined (no generation) until 'Answer anyway'", () => {
    const base = { answerIds: ["a"], sources: [] } as AnswerState;
    const declined = answerReducer(base, { type: "warning", answerId: "a", code: "weak_sources", declined: true, message: "" } as never);
    expect(declined.weakDeclined).toBe(true);
    expect(answerReducer(base, { type: "warning", answerId: "a", code: "weak_sources", message: "" } as never).weakDeclined).toBeUndefined();
  });
});

describe("CT-1 finalText", () => {
  it("replaces the streamed text when the engine dropped unsupported citations, and keeps it otherwise", () => {
    const base = { answerIds: ["a"], sources: [] } as AnswerState;
    const streamed = answerReducer(base, { type: "token", answerId: "a", tier: "fast", text: "Canberra [3]." } as never);
    const receipt = { modelId: "q", modelLabel: "Q", tokens: 3, tokPerSec: 10, ttftMs: 1, totalMs: 2, reasonCodes: [] };
    const cleaned = answerReducer(streamed, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt, finalText: "Canberra." } as never);
    expect(cleaned.fast?.text).toBe("Canberra.");
    const kept = answerReducer(streamed, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt } as never);
    expect(kept.fast?.text).toBe("Canberra [3].");
  });
});

describe("CT-2 cited", () => {
  const receipt = { modelId: "q", modelLabel: "Q", tokens: 3, tokPerSec: 10, ttftMs: 1, totalMs: 2, reasonCodes: [] };
  const base = { answerIds: ["a"], sources: [] } as AnswerState;
  it("stays undefined until a tier reports it (older engines: every source shows)", () => {
    const s = answerReducer(base, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt } as never);
    expect(s.cited).toBeUndefined();
  });
  it("keeps an empty list (no [n] left) and merges fast with deep", () => {
    const fast = answerReducer(base, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt, cited: [] } as never);
    expect(fast.cited).toEqual([]);
    const withDeep = { ...fast, deep: { text: "", stage: null } } as AnswerState;
    const deep = answerReducer(withDeep, { type: "done", answerId: "a", tier: "deep", outcome: "success", receipt, cited: [3, 1, 3] } as never);
    expect(deep.cited).toEqual([1, 3]);
  });
});

describe("NB-1 health excerpt (instant-tier tokens)", () => {
  const receipt = { modelId: "extractive", modelLabel: "Source excerpt", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 500, reasonCodes: [] };
  it("keeps the engine's literal excerpt, its cited source, and ignores tokens after done", () => {
    const text = "Da fonte offline (em inglês):\nPinch the soft part of the nose. [1]\n\nEm uma emergência, ligue 192.";
    let s = { answerIds: ["a"], sources: [] } as AnswerState;
    s = answerReducer(s, { type: "token", answerId: "a", tier: "instant", text } as never);
    expect(s.extract).toBe(text);
    s = answerReducer(s, { type: "done", answerId: "a", tier: "instant", outcome: "success", receipt, cited: [1], safety: true } as never);
    expect(s.instantDone?.outcome).toBe("success");
    expect(s.cited).toEqual([1]);
    expect(s.safety).toBe(true);
    expect(answerReducer(s, { type: "token", answerId: "a", tier: "instant", text: "x" } as never).extract).toBe(text);
    expect(answerPhase(s)).toBe("done");
  });
});

describe("NB-1 PT (Prism, 7a79150): the health excerpt cites [4] of 5 sources", () => {
  it("with the engine's done.cited, only source 4 is cited and the note shows", () => {
    const receipt = { modelId: "extractive", modelLabel: "Source excerpt", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 400, reasonCodes: [] };
    const chunk = (id: string, relevance: number) => ({ chunkId: id, docId: id.split("#")[0], title: id, body: "b", score: 1, matchType: "hybrid" as const, relevance });
    let s = { answerIds: ["a"], sources: [] } as AnswerState;
    const sources = [chunk("Nosebleed#0", 1), chunk("Nosebleed#1", 1), chunk("Emergency bleeding control#0", 1), chunk("External Bleeding#0", 0.52), chunk("Bleeding#0", 0.52)];
    s = answerReducer(s, { type: "sources", answerId: "a", tier: "instant", sources } as never);
    s = answerReducer(s, { type: "token", answerId: "a", tier: "instant", text: "Da fonte offline (em inglês): … [4]\n\nEm uma emergência, ligue 192." } as never);
    s = answerReducer(s, { type: "done", answerId: "a", tier: "instant", outcome: "success", receipt, cited: [4], safety: true } as never);
    expect(answerSourceSplit(s)).toEqual({ cited: [3], related: [0, 1, 2, 4] });
    expect(sourcesCardMode(false, answerSourceSplit(s))).toBe("cited");
    expect(answerShowsEmergencyNote(s, "Meu nariz esta sangrando, o que eu faco?", false)).toBe(true);
    // Without cited (a build before c884d7a, as 7a79150): every source, by design.
    const legacy = answerReducer({ ...s, cited: undefined, instantDone: undefined }, { type: "done", answerId: "a", tier: "instant", outcome: "success", receipt } as never);
    expect(sourcesCardMode(false, answerSourceSplit(legacy))).toBe("all");
  });
});

describe("no-source answers (Tusk 4375d76 / a740a0b)", () => {
  const receipt = { modelId: "q", modelLabel: "Q", tokens: 3, tokPerSec: 10, ttftMs: 1, totalMs: 2, reasonCodes: [] };
  const streamed = answerReducer({ answerIds: ["a"], sources: [], deepAvailable: {} } as AnswerState, { type: "token", answerId: "a", tier: "fast", text: "Seasons happen because…" } as never);
  it("(a) 4B+: the final text with the not-from-the-library line replaces the stream; weak note, not declined", () => {
    let s = answerReducer(streamed, { type: "warning", answerId: "a", code: "weak_sources" } as never);
    const finalText = "Esta resposta não vem de uma fonte offline deste celular; confira antes de confiar nela.\n\nSeasons happen because…";
    s = answerReducer(s, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt, finalText, cited: [] } as never);
    expect(s.fast?.text).toBe(finalText);
    expect(s.weakSources).toBe(true);
    expect(s.weakDeclined).toBeUndefined();
  });
  it("(b) compact: declined after streaming, finalText '' empties the text (not 'absent'), no Deeper answer", () => {
    let s = answerReducer(streamed, { type: "warning", answerId: "a", code: "weak_sources", declined: true } as never);
    s = answerReducer(s, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt, finalText: "", cited: [] } as never);
    expect(s.fast?.text).toBe("");
    expect(s.weakDeclined).toBe(true);
    expect(canDeepen(s)).toBe(false);
    expect(answerPhase(s)).toBe("done");
  });
});

describe("noSourceKind (Prism CT-5, Tusk a428bb6: weak_sources on every uncited model answer)", () => {
  const model = { modelId: "qwen3-4b", modelLabel: "Q", tokens: 30, tokPerSec: 10, ttftMs: 1, totalMs: 2, reasonCodes: [] };
  const src = [{ chunkId: "c", docId: "d", title: "RMS Titanic", body: "b", score: 1, matchType: "hybrid" as const }];
  const answered = (over: Partial<AnswerState>) =>
    ({ answerIds: ["a"], sources: src, fast: { text: "Today in history…", stage: null, outcome: "success", receipt: model }, ...over }) as AnswerState;
  it("passages on the topic, none cited → uncited, with or without weak_sources", () => {
    expect(noSourceKind(answered({ cited: [], weakSources: true }))).toBe("uncited");
    expect(noSourceKind(answered({ cited: [] }))).toBe("uncited");
  });
  it("nothing on the topic (no sources) → weak", () => {
    expect(noSourceKind(answered({ sources: [], cited: [], weakSources: true }))).toBe("weak");
    expect(noSourceKind(answered({ sources: [], cited: [] }))).toBe("weak");
  });
  it("none when it cites, while cited is unknown, or for answers no model wrote", () => {
    expect(noSourceKind(answered({ cited: [1] }))).toBeNull();
    expect(noSourceKind(answered({}))).toBeNull();
    expect(noSourceKind(answered({ cited: [], fast: { text: "x", stage: "generating" } }))).toBeNull();
    expect(noSourceKind(answered({ cited: [], fast: { text: "fixed", stage: null, outcome: "success", receipt: { ...model, modelId: "grounding-guard" } } }))).toBeNull();
    expect(noSourceKind(answered({ cited: [2], deep: { text: "Deeper [2]", stage: null, outcome: "success", receipt: model } }))).toBeNull();
  });
});

describe("canDeepen on the engine's fixed answer (Tusk CT-4)", () => {
  it("never offers Deeper on grounding-guard", () => {
    const r = { modelId: "grounding-guard", modelLabel: "No offline source", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 1, reasonCodes: [] };
    const s = { answerIds: ["a"], sources: [], deepAvailable: {}, fast: { text: "fixed", stage: null, outcome: "success", receipt: r } } as AnswerState;
    expect(canDeepen(s)).toBe(false);
    expect(canDeepen({ ...s, fast: { ...s.fast!, receipt: { ...r, modelId: "qwen" } } })).toBe(true);
  });
});

describe("decline sentence in done.finalText (Tusk 05d1e6b)", () => {
  it("keeps the engine's sentence as the tier text, for history and share", () => {
    const receipt = { modelId: "q", modelLabel: "Q", tokens: 3, tokPerSec: 10, ttftMs: 1, totalMs: 2, reasonCodes: [] };
    let s = { answerIds: ["a"], sources: [] } as AnswerState;
    s = answerReducer(s, { type: "warning", answerId: "a", code: "weak_sources", declined: true } as never);
    s = answerReducer(s, { type: "done", answerId: "a", tier: "fast", outcome: "success", receipt, finalText: "Não encontrei isso no acervo deste celular.", cited: [] } as never);
    expect(s.weakDeclined).toBe(true);
    expect(s.fast?.text).toBe("Não encontrei isso no acervo deste celular.");
  });
});
