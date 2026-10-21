/**
 * Bridge between the measured answer pipeline (src/routing/answerService.ts
 * answer(), the path the in-app evaluation and BOAR Bench measure) and the
 * Android chat screen's existing UI state: processing status, streamed text,
 * sources under the answer, stopped/timed-out badges and usage stats.
 *
 * Pure (no React Native imports): answer() is injected, so the event -> state
 * mapping is unit-tested (answerBridge.test.ts). The mapping follows
 * src/eval/answerEval.pure.ts (answerTextOf / buildAnswerEvalRow), the
 * reference consumer of the same event stream.
 */
import type { AnswerContext } from "../../routing/answer";
import type {
  AnswerEvent,
  AnswerEventHandler,
  AnswerHandle,
  AnswerOutcome,
  AnswerReceipt,
  AnswerRequest,
  AnswerResult,
  AnswerTier,
  Place,
  SourceChunk,
} from "../../routing/events";
import type { ConversationHistory, ConversationTurn } from "../../rag/pure";
import type { QueryStats } from "../../services/telemetry";
import type { TaskType } from "../../routing/types";
import { stripThinking } from "../../services/thinking";

/** Previous messages sent to the model verbatim; older ones live in the session summary. */
export const VERBATIM_MESSAGE_COUNT = 6;

/**
 * The history the chat sends with a question: the session summary plus the last
 * VERBATIM_MESSAGE_COUNT non-empty messages, assistant text without its reasoning.
 * The same window for the legacy path and answer().
 */
export function buildChatHistory(
  prior: { role: "user" | "assistant"; text: string }[],
  summary: string | null
): { history: ConversationHistory; priorMessages: { role: "user" | "assistant"; text: string }[] } {
  const priorMessages = prior.filter((m) => m.text.length > 0);
  const turns: ConversationTurn[] = priorMessages
    .slice(-VERBATIM_MESSAGE_COUNT)
    .map((m) => ({ role: m.role, text: m.role === "assistant" ? stripThinking(m.text) : m.text }));
  return { history: { summary, turns }, priorMessages };
}

export type ChatStatus = "retrieving" | "thinking" | "generating";

/** What the status line says, beyond the status itself; ChatScreen turns it into a translated label. */
export type ChatStatusLabel =
  | { kind: "loading_model" }
  | { kind: "research"; index: number; count: number }
  | { kind: "synthesizing" };

/** The chat screen's state setters, as the bridge drives them. */
export interface ChatAnswerSink {
  setStatus(status: ChatStatus, label?: ChatStatusLabel): void;
  /** Replaces the assistant message's text (instant snippet, final text). */
  setText(text: string): void;
  /** Appends a streamed piece to the assistant message's text. */
  appendText(piece: string): void;
  setLiveTokPerSec(tokPerSec: number): void;
}

export type AnswerFn = (req: AnswerRequest, onEvent: AnswerEventHandler, ctx: AnswerContext) => AnswerHandle;

export interface RunChatAnswerOptions {
  answer: AnswerFn;
  query: string;
  /**
   * The rest of the answer() request, for follow-up actions on an answer: answerAnyway ("Answer anyway"
   * after a decline), tier "deep" + reuseSources ("Deeper answer", as deepen() does), place (the city typed
   * after "which city?"). The chat sends none of these yet (no buttons on Android); plumbing only.
   */
  request?: Omit<AnswerRequest, "query">;
  ctx: AnswerContext;
  sink: ChatAnswerSink;
  now: () => number;
  /** The chat's Stop flag (stopRequestedRef): a stop before the handle exists is applied at once. */
  isStopRequested: () => boolean;
  /** Receives the handle as soon as answer() returns, so Stop / backgrounding can call handle.stop(). */
  onHandle?: (handle: AnswerHandle) => void;
}

export interface ChatAnswerOutcome {
  /** The text to show and persist. */
  text: string;
  /** Shown under the answer: the sources when the final text cites at least one, else none (contract CT-2). */
  citations: SourceChunk[];
  sources: SourceChunk[];
  cited: number[];
  outcome: AnswerOutcome;
  tier: AnswerTier | "unknown";
  stopped: boolean;
  timedOut: boolean;
  receipt?: AnswerReceipt;
  error?: { code: string; message: string; kind?: string };
  /** weak_sources warning codes and the like, in order. */
  warnings: string[];
  /** A weak_sources warning with declined: true (the compact model did not answer from memory). */
  declined: boolean;
  /** The engine's health/first-aid/disaster flag (done.safety). */
  safety: boolean;
  /** A deep_available event came after the fast answer. */
  deepAvailable: boolean;
  /** Token events received (streamed pieces). */
  streamedTokens: number;
}

function placesList(places: Place[]): string[] {
  return places.map((p) => `- ${p.name}${p.address ? ` (${p.address})` : ""}${p.cuisine?.length ? ` · ${p.cuisine.join(", ")}` : ""}`);
}

/**
 * Runs one question through answer() and drives the sink; resolves when the answer is settled.
 * Never leaves the message empty: an error with no text becomes "Error: <message>", as the legacy path does.
 */
export async function runChatAnswer(opts: RunChatAnswerOptions): Promise<ChatAnswerOutcome> {
  const { sink, now } = opts;
  let answerId: string | null = null;
  let settled = false;
  let sources: SourceChunk[] = [];
  let instantText: string | null = null;
  let streamed = "";
  let streamedTokens = 0;
  let firstTokenAt: number | null = null;
  let places: Extract<AnswerEvent, { type: "places" }> | null = null;
  let done: Extract<AnswerEvent, { type: "done" }> | null = null;
  const warnings: string[] = [];
  let declined = false;
  let deepAvailable = false;

  const onEvent: AnswerEventHandler = (e) => {
    // One answer per call: late events of another answer id, or after done, are dropped.
    answerId ??= e.answerId;
    if (e.answerId !== answerId) return;
    if (settled && e.type !== "deep_available") return;
    switch (e.type) {
      case "stage": {
        if (e.stage === "retrieving") {
          const { index, count } = e.detail ?? {};
          sink.setStatus("retrieving", count && index !== undefined ? { kind: "research", index: index + 1, count } : undefined);
        } else if (e.stage === "loading_model") sink.setStatus("thinking", { kind: "loading_model" });
        else if (e.stage === "synthesizing") sink.setStatus("thinking", { kind: "synthesizing" });
        else if (e.stage === "generating") sink.setStatus("generating");
        else sink.setStatus("thinking"); // prefill, verifying
        break;
      }
      case "sources":
        sources = e.sources;
        break;
      case "instant":
        // A source sentence shown before the model writes; replaced by the model's first token.
        if (streamedTokens === 0) {
          instantText = e.snippet.text;
          sink.setText(e.snippet.text);
          sink.setStatus("generating");
        }
        break;
      case "token": {
        if (streamedTokens === 0) {
          firstTokenAt = now();
          if (instantText !== null) sink.setText("");
          sink.setStatus("generating");
        } else if (firstTokenAt !== null) {
          const s = (now() - firstTokenAt) / 1000;
          if (s > 0) sink.setLiveTokPerSec((streamedTokens + 1) / s);
        }
        streamedTokens += 1;
        streamed += e.text;
        sink.appendText(e.text);
        break;
      }
      case "warning":
        warnings.push(e.code);
        if (e.declined) declined = true;
        break;
      case "places":
        places = e;
        break;
      case "deep_available":
        deepAvailable = true;
        break;
      case "done":
        done = e;
        settled = true;
        break;
      case "location":
        break;
    }
  };

  const handle = opts.answer({ ...opts.request, query: opts.query }, onEvent, opts.ctx);
  answerId ??= handle.answerId;
  opts.onHandle?.(handle);
  if (opts.isStopRequested()) await handle.stop().catch(() => {});
  const result: AnswerResult | null = await handle.done;

  const d = done as Extract<AnswerEvent, { type: "done" }> | null;
  const outcome: AnswerOutcome = d?.outcome ?? result?.outcome ?? "error";
  const finalSources: SourceChunk[] = sources.length ? sources : (result?.sources ?? []);
  const cited = d?.cited ?? result?.cited ?? [];
  const p = places as Extract<AnswerEvent, { type: "places" }> | null;
  // As answerTextOf (eval): the corrected final text, else the streamed tokens, else the result's
  // text (places), else the instant snippet when it was the whole answer.
  const primary = d?.finalText ?? (streamed.trim() ? streamed : (result?.text ?? ""));
  const base = primary.trim() ? primary : (instantText ?? "");
  // A places answer's text already lists the places (formatPlacesAnswer); the list is only added
  // when the engine gave no text (as the eval's answerTextOf, without its duplication).
  let text = base.trim() ? base : placesList(p?.places ?? []).join("\n");
  const error = d?.error ?? undefined;
  // An error ends the answer visibly, also after some streamed text (the legacy path's "Error: …" bubble).
  if (outcome === "error" && error) text = text.trim() ? `${text.trimEnd()}\n\nError: ${error.message}` : `Error: ${error.message}`;
  else if (!text.trim() && outcome === "error") text = "Error: the answer failed.";
  // The streamed text is on screen; replace it only when the final text differs (CT-1 citations, declines, places).
  const shown = instantText !== null && streamedTokens === 0 ? instantText : streamed;
  if (text !== shown) sink.setText(text);

  return {
    text,
    // Places answers cite their records ([n]) but carry no cited list: show them all.
    citations: cited.length > 0 || (d?.receipt ?? result?.receipt)?.modelId === "places" ? finalSources : [],
    sources: finalSources,
    cited,
    outcome,
    tier: d?.tier ?? result?.tier ?? "unknown",
    stopped: outcome === "stopped" || opts.isStopRequested(),
    timedOut: outcome === "timeout",
    receipt: d?.receipt ?? result?.receipt,
    error,
    warnings,
    declined,
    safety: d?.safety === true,
    deepAvailable,
    streamedTokens,
  };
}

const TASK_TYPES: readonly TaskType[] = [
  "greeting",
  "conversation",
  "chat",
  "lookup",
  "research",
  "summarize",
  "compare",
  "calculate",
  "extract",
  "translate",
  "code",
  "unknown",
];

/** The engine's task type, from its "task:<type>" reason code (places and other non-task codes: none). */
export function taskTypeOf(reasonCodes: string[] | undefined): TaskType | undefined {
  const code = reasonCodes?.find((c) => c.startsWith("task:"))?.slice(5);
  return TASK_TYPES.find((t) => t === code);
}

/**
 * The chat's in-memory usage stats (telemetry.recordQueryStats) from the answer's receipt.
 * answer() itself persists the execution record (recordExecution) when a model generated,
 * so the chat must not record a second one for this path. adaptiveRoutingUsed stays unset: that
 * flag is the legacy runAdaptiveChat path's.
 */
export function queryStatsFromAnswer(
  o: ChatAnswerOutcome,
  t: { durationMs: number; peakRssBytes: number; timestamp: number; fallbackModelId?: string }
): QueryStats {
  const r = o.receipt;
  return {
    tokensGenerated: r?.tokens ?? o.streamedTokens,
    durationMs: t.durationMs,
    ttftMs: r?.ttftMs ?? 0,
    tokPerSec: r?.tokPerSec ?? 0,
    peakRssBytes: t.peakRssBytes,
    timestamp: t.timestamp,
    totalLatencyMs: r?.totalMs ?? t.durationMs,
    modelId: r && r.modelId !== "none" ? r.modelId : t.fallbackModelId,
    ...(taskTypeOf(r?.reasonCodes) ? { taskType: taskTypeOf(r?.reasonCodes) } : {}),
    reasonCodes: r?.reasonCodes ?? [],
    retrievalUsed: o.sources.length > 0,
    ...(r?.loadMs !== undefined ? { modelLoadMs: r.loadMs } : {}),
    outcome: o.outcome === "success" ? "success" : o.outcome === "stopped" ? "cancelled" : "failure",
  };
}
