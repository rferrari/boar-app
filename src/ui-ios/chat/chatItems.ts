import type { ConversationTurn } from "../../rag/retrieve";
import type { ChatMessageRecord, ChatSession } from "../../services/chatHistory";
import { stripThinking } from "../../services/thinking";
import type { AnswerState } from "./answerReducer";
import { answerTextForHistory, fromStoredAnswer } from "./answerRecord";

export type ChatItem =
  | { kind: "user"; id: string; text: string }
  | {
      kind: "assistant";
      id: string;
      question: string;
      answer: AnswerState;
      feedback: "up" | "down" | null;
      interrupted?: boolean;
    };

/** Rebuilds the conversation from stored rows; each answer remembers the question before it. */
export function itemsFromRecords(records: ChatMessageRecord[]): ChatItem[] {
  let question = "";
  return records.map((r): ChatItem => {
    if (r.role === "user") {
      question = r.text;
      return { kind: "user", id: r.id, text: r.text };
    }
    return { kind: "assistant", id: r.id, question, answer: fromStoredAnswer(r.id, r.text, r.meta), feedback: r.feedback };
  });
}

/** The last `count` messages as prompt history: answers without their reasoning, empty turns dropped. */
export function historyTurns(items: ChatItem[], count: number): ConversationTurn[] {
  return items
    .map((m): ConversationTurn =>
      m.kind === "user" ? { role: "user", text: m.text } : { role: "assistant", text: stripThinking(answerTextForHistory(m.answer)) }
    )
    .filter((turn) => turn.text.trim().length > 0)
    .slice(-count);
}

/** Applies `update` to the answer of message `id`, leaving every other item untouched. */
export function updateAnswer(items: ChatItem[], id: string, update: (a: AnswerState) => AnswerState): ChatItem[] {
  return items.map((m) => (m.kind === "assistant" && m.id === id ? { ...m, answer: update(m.answer) } : m));
}

/** Reopening the app within this long picks up the last conversation (e.g. after the system killed it). */
export const RESUME_WINDOW_MS = 6 * 60 * 60 * 1000;

/** The most recently used session if it was used within the window, else null (start a new chat). */
export function sessionToResume(sessions: ChatSession[], now: number, windowMs = RESUME_WINDOW_MS): ChatSession | null {
  const latest = [...sessions].sort((a, b) => b.updatedAt - a.updatedAt)[0];
  return latest && now - latest.updatedAt <= windowMs ? latest : null;
}

