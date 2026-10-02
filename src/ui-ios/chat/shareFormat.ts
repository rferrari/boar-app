import type { RetrievedChunk } from "../../rag/retrieve.types";
import type { AnswerReceipt as Receipt } from "./answerEvents";
import { CALCULATOR_MODEL_ID, EXTRACTIVE_MODEL_ID, GROUNDING_GUARD_MODEL_ID } from "./answerEvents";
import { numberFormat } from "./numberFormat";

/** Labels come from i18n so the text reads in the app's language. */
export interface ShareLabels {
  sources: string;
  /** e.g. "Answered offline by BOAR" */
  answeredOffline: string;
  /** e.g. "Source passage" — shown instead of a model name for extractive answers. */
  sourcePassage: string;
  /** e.g. "My documents" — origin of a chunk from a user-imported collection. */
  myDocuments: string;
  /** e.g. "No offline source": the engine's fixed answer when nothing on the phone covers it. */
  noOfflineSource?: string;
  /** e.g. "Calculator": an exact conversion, no model. */
  calculator?: string;
  /** The chat's name for a model the receipt names by id + label (its tier). */
  modelName?: (id: string, label: string) => string;
}

function sourceLine(chunk: RetrievedChunk, n: number, labels: ShareLabels): string {
  const origin = chunk.collectionId ? labels.myDocuments : chunk.source;
  return origin ? `[${n}] ${chunk.title} — ${origin}` : `[${n}] ${chunk.title}`;
}

/**
 * Answer text plus the sources its "[n]" point to, for the clipboard. `only` (CT-2: the cited
 * indexes) leaves out sources the answer doesn't rest on, keeping each one's own number.
 */
export function formatForCopy(answer: string, sources: RetrievedChunk[], labels: ShareLabels, only?: number[]): string {
  const body = answer.trim();
  const keep = only ? new Set(only) : null;
  const lines = sources.flatMap((c, i) => (keep && !keep.has(i) ? [] : [sourceLine(c, i + 1, labels)]));
  if (lines.length === 0) return body;
  const list = lines.join("\n");
  return `${body}\n\n${labels.sources}:\n${list}`;
}

export function formatSeconds(ms: number, locale: string): string {
  const s = ms / 1000;
  return `${numberFormat(locale, 0, s < 10 ? 1 : 0).format(s)} s`;
}


/** Question, answer, sources and a one-line provenance note, for the system share sheet. */
export function formatForShare(
  question: string,
  answer: string,
  sources: RetrievedChunk[],
  receipt: Receipt | undefined,
  labels: ShareLabels,
  locale: string,
  only?: number[]
): string {
  const parts = [question.trim(), formatForCopy(answer, sources, labels, only)];
  if (receipt) {
    const who =
      receipt.modelId === EXTRACTIVE_MODEL_ID
        ? labels.sourcePassage
        : receipt.modelId === GROUNDING_GUARD_MODEL_ID
          ? labels.noOfflineSource ?? receipt.modelLabel
          : receipt.modelId === CALCULATOR_MODEL_ID
            ? labels.calculator ?? receipt.modelLabel
            : labels.modelName?.(receipt.modelId, receipt.modelLabel) ?? receipt.modelLabel;
    parts.push(`${labels.answeredOffline} · ${who} · ${formatSeconds(receipt.totalMs, locale)}`);
  }
  return parts.join("\n\n");
}
