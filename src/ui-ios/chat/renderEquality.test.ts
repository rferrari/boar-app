import { describe, it, expect } from "vitest";
import type { RetrievedChunk } from "../../rag/retrieve.types";
import type { AnswerEvent } from "./answerEvents";
import { answerReducer, initialAnswer, type AnswerState } from "./answerReducer";
import type { GeneratingStep } from "./presentation";
import { sameAnswerFields, sameNumbers, sameSteps } from "./renderEquality";

const chunk = (id: string): RetrievedChunk => ({ chunkId: id, docId: `doc-${id}`, title: `T ${id}`, body: `B ${id}`, score: 0.5, matchType: "hybrid" });
const run = (events: AnswerEvent[], from: AnswerState = initialAnswer("a1")): AnswerState => events.reduce(answerReducer, from);

// The pieces the memo keeps still while the text streams (InstantSnippet, PlacesCard, SourceList).
const STILL = ["sources", "instant", "places", "location"] as const;

describe("sameAnswerFields on a streaming answer", () => {
  const before = run([
    { answerId: "a1", type: "sources", tier: "fast", sources: [chunk("x"), chunk("y")] },
    { answerId: "a1", type: "instant", snippet: { text: "Snippet.", sourceIndex: 0 }, confidence: 0.9 },
    { answerId: "a1", type: "location", status: "granted", accuracyM: 12 },
    { answerId: "a1", type: "token", tier: "fast", text: "Raft " },
  ]);

  it("a token keeps sources, snippet, places and location (the memo holds)", () => {
    const after = run([{ answerId: "a1", type: "token", tier: "fast", text: "elects." }], before);
    expect(after).not.toBe(before);
    expect(sameAnswerFields(before, after, STILL)).toBe(true);
    expect(sameAnswerFields(before, after, ["fast"])).toBe(false);
  });

  it("a deep token keeps the finished fast tier", () => {
    const fastDone = run(
      [
        { answerId: "a1", type: "done", tier: "fast", outcome: "success", receipt: { modelId: "m", modelLabel: "M", tokens: 1, tokPerSec: 1, ttftMs: 1, totalMs: 1, reasonCodes: [] } },
        { answerId: "a1", type: "token", tier: "deep", text: "More" },
      ],
      before
    );
    const after = run([{ answerId: "a1", type: "token", tier: "deep", text: " detail" }], fastDone);
    expect(sameAnswerFields(fastDone, after, [...STILL, "fast"])).toBe(true);
  });

  it("new sources break it (the card must show them)", () => {
    const after = run([{ answerId: "a1", type: "sources", tier: "fast", sources: [chunk("z")] }], before);
    expect(sameAnswerFields(before, after, ["sources"])).toBe(false);
  });
});

describe("sameNumbers", () => {
  it("compares by value", () => {
    expect(sameNumbers([1, 2], [1, 2])).toBe(true);
    expect(sameNumbers(undefined, undefined)).toBe(true);
    expect(sameNumbers([1, 2], [2, 1])).toBe(false);
    expect(sameNumbers([1], undefined)).toBe(false);
    expect(sameNumbers([], [1])).toBe(false);
  });
});

describe("sameSteps", () => {
  const step = (over: Partial<GeneratingStep> = {}): GeneratingStep => ({ key: "write", label: "Writing", short: "Write", icon: "zap", status: "active", ...over });
  it("equal content in new objects is the same card", () => {
    expect(sameSteps([step()], [step()])).toBe(true);
  });
  it("a status or label change is not", () => {
    expect(sameSteps([step()], [step({ status: "done" })])).toBe(false);
    expect(sameSteps([step()], [step({ label: "Reading 3 passages" })])).toBe(false);
    expect(sameSteps([step()], [step(), step({ key: "read" })])).toBe(false);
  });
});
