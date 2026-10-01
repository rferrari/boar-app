/**
 * The Knowledge Sanctuary preview's state: which mock packs were "acquired", the user's votes,
 * subscriptions, tips, reports and publish drafts. In memory only: nothing is sent, no tokens move.
 * Pure: tested without React Native.
 */
import { PACKS, type SessionId } from "./catalog";

export interface Draft {
  title: string;
  session: SessionId;
  tags: string[];
  documents: string[];
  pricing: "free" | "once" | "subscription";
}

export interface SanctuaryState {
  acquired: Record<string, true>;
  votes: Record<string, 1 | -1>;
  subscribed: Record<string, true>;
  /** Preview tips per pack, in $BOAR (never sent). */
  tips: Record<string, number>;
  reported: Record<string, true>;
  drafts: Draft[];
}

export type SanctuaryAction =
  | { type: "acquire"; packId: string }
  | { type: "vote"; packId: string; vote: 1 | -1 }
  | { type: "toggleSubscribe"; packId: string }
  | { type: "tip"; packId: string; amount: number }
  | { type: "report"; packId: string }
  | { type: "saveDraft"; draft: Draft };

export const initialSanctuary: SanctuaryState = { acquired: {}, votes: {}, subscribed: {}, tips: {}, reported: {}, drafts: [] };

const known = (id: string) => PACKS.some((p) => p.id === id);

export function sanctuaryReducer(state: SanctuaryState, action: SanctuaryAction): SanctuaryState {
  if ("packId" in action && !known(action.packId)) return state;
  switch (action.type) {
    case "acquire":
      return state.acquired[action.packId] ? state : { ...state, acquired: { ...state.acquired, [action.packId]: true } };
    case "vote": {
      // The same vote again takes it back.
      const votes = { ...state.votes };
      if (votes[action.packId] === action.vote) delete votes[action.packId];
      else votes[action.packId] = action.vote;
      return { ...state, votes };
    }
    case "toggleSubscribe": {
      const subscribed = { ...state.subscribed };
      if (subscribed[action.packId]) delete subscribed[action.packId];
      else subscribed[action.packId] = true;
      return { ...state, subscribed };
    }
    case "tip":
      if (!(action.amount > 0) || !Number.isFinite(action.amount)) return state;
      return { ...state, tips: { ...state.tips, [action.packId]: (state.tips[action.packId] ?? 0) + action.amount } };
    case "report":
      return { ...state, reported: { ...state.reported, [action.packId]: true } };
    case "saveDraft":
      if (!action.draft.title.trim()) return state;
      return { ...state, drafts: [...state.drafts, { ...action.draft, title: action.draft.title.trim() }] };
  }
}

/** A session shows its boar once any of its packs is acquired. */
export function sessionAcquired(state: SanctuaryState, session: SessionId): boolean {
  return PACKS.some((p) => p.session === session && state.acquired[p.id]);
}

/** One of the n entrance scenes, at random (the art changes on each open). */
export function pickEntrance(n: number, random: () => number = Math.random): number {
  return Math.min(n - 1, Math.floor(random() * n));
}
