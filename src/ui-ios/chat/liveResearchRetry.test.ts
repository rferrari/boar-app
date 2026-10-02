import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { answerPhase, answerReducer, attachAnswer, initialAnswer, type AnswerState } from "./answerReducer";
import type { AnswerEvent, SourceChunk } from "./answerEvents";
import { deepFromFor, foldAttempt, researchPhase, researchView, type AttemptTimeline } from "./liveResearch";

/**
 * Retry keeps the chat message, and so AssistantMessage and its refs, but runs a new answer() with a new
 * id (ChatScreen followUp: the answer is reset to { answerIds: [], sources: [] }, then the new id is
 * attached). These replays render the way AssistantMessage does: one foldAttempt per render, keyed by
 * the first answer() id, on while the answer runs.
 */

const chunk = (docId: string): SourceChunk => ({ chunkId: `${docId}#0`, docId, title: docId, body: `${docId} passage.`, score: 0, matchType: "hybrid" });
const stage = (answerId: string, tier: "fast" | "deep", s: "retrieving" | "prefill" | "generating" | "synthesizing", detail?: object): AnswerEvent =>
  ({ type: "stage", answerId, tier, stage: s, detail, at: 0 }) as AnswerEvent;
const sources = (answerId: string, tier: "fast" | "deep", list: SourceChunk[]): AnswerEvent => ({ type: "sources", answerId, tier, sources: list }) as AnswerEvent;
const done = (answerId: string, tier: "fast" | "deep"): AnswerEvent =>
  ({ type: "done", answerId, tier, outcome: "success", receipt: { modelId: "m", modelLabel: "M", tokens: 1, tokPerSec: 1, ttftMs: 1, totalMs: 1, reasonCodes: [] } }) as AnswerEvent;

/** One "render": what AssistantMessage folds for the answer's own tier. */
function render(prev: AttemptTimeline | null, s: AnswerState, running: boolean): AttemptTimeline | null {
  const tier = s.deep ?? s.fast;
  const phase = researchPhase(answerPhase(s));
  return foldAttempt(prev, s.answerIds[0] ?? "", running && !tier?.outcome, { sources: s.sources, detail: tier?.detail, searching: phase === "searching" });
}

function run(prev: AttemptTimeline | null, s: AnswerState, events: AnswerEvent[]): { tl: AttemptTimeline | null; s: AnswerState } {
  let tl = render(prev, s, true);
  for (const e of events) {
    s = answerReducer(s, e);
    tl = render(tl, s, !s.deep?.outcome && !s.fast?.outcome);
  }
  return { tl, s };
}

/** A deep-tier research: 3 parts, 2 articles in part 1. */
const deepAttempt = (id: string): AnswerEvent[] => [
  stage(id, "deep", "retrieving", {}),
  stage(id, "deep", "retrieving", { index: 0, count: 3, subQuestion: "How does it trap heat?" }),
  sources(id, "deep", [chunk("Greenhouse effect"), chunk("Infrared")]),
  stage(id, "deep", "retrieving", { index: 1, count: 3 }),
  stage(id, "deep", "retrieving", { index: 2, count: 3 }),
  stage(id, "deep", "synthesizing"),
  done(id, "deep"),
];

/** The Retry reset (ChatScreen followUp with {}), then the new attempt's id. */
const retry = (s: AnswerState, id: string): AnswerState => attachAnswer({ ...s, answerIds: [], sources: [], fast: undefined, deep: undefined }, id);

describe("Retry starts a fresh research timeline", () => {
  it("deep attempt → retry single pass: no parts, no articles, no 'part 3 of 3' from attempt 1", () => {
    const first = run(null, initialAnswer("a1"), deepAttempt("a1"));
    expect(first.tl!.timeline.multi).toBe(true);
    expect(first.tl!.timeline.order).toEqual(["Greenhouse effect", "Infrared"]);

    let s = retry(first.s, "a2");
    // Right after the reset, before any event of the new attempt: nothing of attempt 1.
    const blank = render(first.tl, { ...first.s, answerIds: [], sources: [], fast: undefined, deep: undefined }, true);
    expect(blank!.timeline.order).toEqual([]);
    expect(blank!.timeline.multi).toBe(false);

    const second = run(blank, s, [stage("a2", "fast", "retrieving"), sources("a2", "fast", [chunk("Carbon dioxide")]), stage("a2", "fast", "prefill")]);
    s = second.s;
    const tl = second.tl!.timeline;
    expect(second.tl!.key).toBe("a2");
    expect(tl.multi).toBe(false);
    expect(tl.parts).toHaveLength(1);
    expect(tl.order).toEqual(["Carbon dioxide"]);
    const v = researchView(tl, "reading");
    expect(v.header).toEqual({ key: "chat.research.headerReading", opts: { count: 1 } });
    expect(JSON.stringify(v)).not.toMatch(/headerPart|chat\.research\.part\b/);
  });

  it("deep attempt → retry deep: its own parts from zero, only its own articles", () => {
    const first = run(null, initialAnswer("a1"), deepAttempt("a1"));
    const s = retry(first.s, "a2");
    const second = run(first.tl, s, [
      stage("a2", "deep", "retrieving", {}),
      stage("a2", "deep", "retrieving", { index: 0, count: 2, subQuestion: "Which gases?" }),
    ]);
    const tl = second.tl!.timeline;
    expect(second.tl!.key).toBe("a2");
    expect(tl.parts).toHaveLength(2);
    expect(tl.parts.map((p) => p.question)).toEqual(["Which gases?", undefined]);
    expect(tl.order).toEqual([]);
    expect(researchView(tl, "searching").header).toEqual({ key: "chat.research.headerPart", opts: { index: 1, count: 2 } });
  });

  it("the same attempt keeps its timeline (and the same object while nothing changes)", () => {
    const first = run(null, initialAnswer("a1"), deepAttempt("a1"));
    expect(render(first.tl, first.s, false)).toBe(first.tl);
    expect(foldAttempt(first.tl, "a1", false, { sources: [], searching: false })).toBe(first.tl);
    // A new attempt that isn't running yet: nothing (never the old one).
    expect(foldAttempt(first.tl, "a2", false, { sources: [], searching: false })).toBeNull();
  });

  it("AssistantMessage keys its timelines, Deepen start and pill step by the attempt", () => {
    const am = readFileSync(join(__dirname, "AssistantMessage.tsx"), "utf8");
    expect(am).toMatch(/ref\.current = foldAttempt\(ref\.current, attempt, on,/);
    expect(am).toMatch(/const firstAttempt = answer\.answerIds\[0\] \?\? "";/);
    expect(am).toMatch(/useTimeline\(\s*firstAttempt,/);
    expect(am).toMatch(/useTimeline\(lastAttempt,/);
    expect(am).toMatch(/deepFromFor\(deepFrom\.current, lastAttempt,/);
    expect(am).toMatch(/if \(lastStep\.current\.key !== lastAttempt\)/);
  });

  it("a retry's Deepen measures its own sources from its own start", () => {
    const a = deepFromFor(null, "d1", true, 5);
    expect(a).toEqual({ key: "d1", from: 5 });
    expect(deepFromFor(a, "d1", true, 9).from).toBe(5);
    expect(deepFromFor(a, "d2", true, 2).from).toBe(2);
    expect(deepFromFor(a, "d2", false, 2).from).toBeNull();
  });
});
