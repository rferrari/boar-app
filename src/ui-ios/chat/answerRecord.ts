import type { RetrievedChunk } from "../../rag/retrieve.types";
import type { AnswerOutcome, AnswerReceipt } from "./answerEvents";
import type { AnswerState, PlacesResult, TierState } from "./answerReducer";

/** What the chat keeps with an answer so a reopened session shows it as it was. */
interface StoredTier {
  text: string;
  outcome?: AnswerOutcome;
  receipt?: AnswerReceipt;
  errorCode?: string;
}

export interface StoredAnswer {
  v: 1;
  sources: (Pick<RetrievedChunk, "chunkId" | "docId" | "title" | "body" | "source" | "collectionId"> & { relevance?: number })[];
  instant?: { text: string; sourceIndex: number };
  /** NB-1: the literal excerpt answer (health/safety). */
  extract?: string;
  /** Older records stored only the receipt of an extractive answer. */
  extractiveReceipt?: AnswerReceipt;
  instantDone?: { outcome: AnswerOutcome; receipt: AnswerReceipt; errorCode?: string };
  places?: PlacesResult;
  fast?: StoredTier;
  deep?: StoredTier;
  /** CT-2: the [n] the final text kept; absent in older records (every source shown). */
  cited?: number[];
  /** No strong source (weak_sources), and whether the compact model declined: a reopened answer keeps its note or card. */
  weakSources?: boolean;
  weakDeclined?: boolean;
}

function storeTier(t: TierState | undefined): StoredTier | undefined {
  if (!t) return undefined;
  return { text: t.text, outcome: t.outcome, receipt: t.receipt, errorCode: t.error?.code };
}

function restoreTier(t: StoredTier | undefined): TierState | undefined {
  if (!t) return undefined;
  // An answer saved mid-flight (app killed) reads as interrupted, never as still running.
  return {
    text: t.text,
    stage: null,
    outcome: t.outcome ?? "interrupted",
    receipt: t.receipt,
    error: t.errorCode ? { code: t.errorCode as NonNullable<TierState["error"]>["code"], message: "" } : undefined,
  };
}

export function toStoredAnswer(state: AnswerState): string {
  const stored: StoredAnswer = {
    v: 1,
    // relevance kept so a reopened answer still shows its measured bars.
    sources: state.sources.map(({ chunkId, docId, title, body, source, collectionId, relevance }) => ({
      chunkId,
      docId,
      title,
      body,
      source,
      collectionId,
      ...(relevance != null ? { relevance } : {}),
    })),
    instant: state.instant ? { text: state.instant.text, sourceIndex: state.instant.sourceIndex } : undefined,
    ...(state.extract ? { extract: state.extract } : {}),
    instantDone: state.instantDone
      ? { outcome: state.instantDone.outcome, receipt: state.instantDone.receipt, errorCode: state.instantDone.error?.code }
      : undefined,
    places: state.places,
    fast: storeTier(state.fast),
    deep: storeTier(state.deep),
    ...(state.cited ? { cited: state.cited } : {}),
    ...(state.weakSources ? { weakSources: true } : {}),
    ...(state.weakDeclined ? { weakDeclined: true } : {}),
  };
  return JSON.stringify(stored);
}

/**
 * Rebuilds a finished answer. `text` is the message's stored text, used when
 * there is no meta (messages saved before sources were kept).
 */
export function fromStoredAnswer(id: string, text: string, meta: string | null): AnswerState {
  let stored: StoredAnswer | null = null;
  if (meta) {
    try {
      const parsed = JSON.parse(meta);
      if (parsed?.v === 1) stored = parsed;
    } catch {
      // Unreadable meta: fall back to the plain text below.
    }
  }
  if (!stored) {
    return { answerIds: [id], sources: [], fast: { text, stage: null, outcome: "success" } };
  }
  return {
    answerIds: [id],
    sources: stored.sources.map((s) => ({ ...s, score: 0, matchType: "hybrid" as const })),
    instant: stored.instant ? { ...stored.instant, confidence: 1 } : undefined,
    ...(typeof stored.extract === "string" ? { extract: stored.extract } : {}),
    instantDone: stored.instantDone
      ? {
          outcome: stored.instantDone.outcome,
          receipt: stored.instantDone.receipt,
          error: stored.instantDone.errorCode
            ? { code: stored.instantDone.errorCode as NonNullable<TierState["error"]>["code"], message: "" }
            : undefined,
        }
      : stored.extractiveReceipt
        ? { outcome: "success", receipt: stored.extractiveReceipt }
        : undefined,
    places: stored.places,
    fast: restoreTier(stored.fast),
    deep: restoreTier(stored.deep),
    ...(Array.isArray(stored.cited) ? { cited: stored.cited } : {}),
    ...(stored.weakSources ? { weakSources: true } : {}),
    ...(stored.weakDeclined ? { weakDeclined: true } : {}),
  };
}

/**
 * The text a later turn sees as this answer: the deepest finished pass, else
 * the places list (names and addresses as recorded), else the snippet.
 */
export function answerTextForHistory(state: AnswerState): string {
  if (state.deep?.text) return state.deep.text;
  if (state.fast?.text) return state.fast.text;
  if (state.places?.places.length) {
    return state.places.places.map((p, i) => `${i + 1}. ${p.name}${p.address ? ` (${p.address})` : ""}`).join("\n");
  }
  return state.extract ?? state.instant?.text ?? "";
}
