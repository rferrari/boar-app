import { describe, it, expect } from "vitest";
import type { ChatMessageRecord } from "../../services/chatHistory";
import {
  historyTurns,
  itemsFromRecords,
  RESUME_WINDOW_MS,
  sessionToResume,
  updateAnswer,
  type ChatItem,
} from "./chatItems";

const rec = (id: string, role: "user" | "assistant", text: string, meta: string | null = null): ChatMessageRecord => ({
  id,
  sessionId: "s",
  role,
  text,
  createdAt: 0,
  feedback: null,
  meta,
});

describe("itemsFromRecords", () => {
  it("pairs each answer with the question before it", () => {
    const items = itemsFromRecords([rec("1", "user", "Q1"), rec("2", "assistant", "A1"), rec("3", "user", "Q2"), rec("4", "assistant", "A2")]);
    expect(items.map((i) => (i.kind === "assistant" ? i.question : null))).toEqual([null, "Q1", null, "Q2"]);
    const a = items[1];
    expect(a.kind === "assistant" && a.answer.fast?.text).toBe("A1");
  });
});

describe("historyTurns", () => {
  it("keeps the last turns, strips reasoning and drops empty answers", () => {
    const items: ChatItem[] = [
      { kind: "user", id: "1", text: "Q1" },
      {
        kind: "assistant",
        id: "2",
        question: "Q1",
        feedback: null,
        answer: { answerIds: ["2"], sources: [], fast: { text: "<think>hmm</think>A1", stage: null, outcome: "success" } },
      },
      { kind: "user", id: "3", text: "Q2" },
      { kind: "assistant", id: "4", question: "Q2", feedback: null, answer: { answerIds: ["4"], sources: [] } },
    ];
    expect(historyTurns(items, 6)).toEqual([
      { role: "user", text: "Q1" },
      { role: "assistant", text: "A1" },
      { role: "user", text: "Q2" },
    ]);
    expect(historyTurns(items, 1)).toEqual([{ role: "user", text: "Q2" }]);
  });
});

describe("updateAnswer", () => {
  it("changes only the matching answer", () => {
    const items: ChatItem[] = [
      { kind: "user", id: "1", text: "Q" },
      { kind: "assistant", id: "2", question: "Q", feedback: null, answer: { answerIds: ["2"], sources: [] } },
    ];
    const next = updateAnswer(items, "2", (a) => ({ ...a, streamsFromStorage: true }));
    expect(next[0]).toBe(items[0]);
    expect(next[1].kind === "assistant" && next[1].answer.streamsFromStorage).toBe(true);
  });
});

describe("sessionToResume", () => {
  const session = (id: string, updatedAt: number) => ({ id, title: id, summary: null, createdAt: 0, updatedAt });
  it("reopens the latest session only within the window", () => {
    const now = 10 * RESUME_WINDOW_MS;
    expect(sessionToResume([session("old", now - 2 * RESUME_WINDOW_MS), session("new", now - 1000)], now)?.id).toBe("new");
    expect(sessionToResume([session("stale", now - RESUME_WINDOW_MS - 1)], now)).toBeNull();
    expect(sessionToResume([], now)).toBeNull();
  });
});

