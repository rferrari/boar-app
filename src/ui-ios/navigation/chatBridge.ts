/**
 * Seam between the chat screen (which owns sessions and the engine) and the
 * app shell (drawer, routes). ChatScreen publishes its current state and
 * handlers here; the drawer and route wrappers read them. Keeps the drawer out
 * of ChatScreen without moving session logic in this change.
 */
import { useSyncExternalStore } from "react";
import type { ChatSession } from "../../services/chatHistory";

export interface ChatBridgeState {
  sessions: ChatSession[];
  activeSessionId: string | null;
  /** True while a reply is being generated. */
  generating: boolean;
  newChat: () => void;
  selectSession: (id: string) => void;
  deleteSession: (id: string) => void;
  refreshSessions: () => void;
  openPromptIdeas: () => void;
}

const noop = () => {};

const EMPTY: ChatBridgeState = {
  sessions: [],
  activeSessionId: null,
  generating: false,
  newChat: noop,
  selectSession: noop,
  deleteSession: noop,
  refreshSessions: noop,
  openPromptIdeas: noop,
};

let state: ChatBridgeState = EMPTY;
const listeners = new Set<() => void>();

export function publishChatBridge(next: Partial<ChatBridgeState>): void {
  let changed = false;
  for (const key of Object.keys(next) as (keyof ChatBridgeState)[]) {
    if (state[key] !== next[key]) changed = true;
  }
  if (!changed) return;
  state = { ...state, ...next };
  listeners.forEach((l) => l());
}

export function resetChatBridge(): void {
  state = EMPTY;
  listeners.forEach((l) => l());
}

export function getChatBridge(): ChatBridgeState {
  return state;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useChatBridge(): ChatBridgeState {
  return useSyncExternalStore(subscribe, getChatBridge, getChatBridge);
}
