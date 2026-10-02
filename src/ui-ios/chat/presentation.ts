import { CALCULATOR_MODEL_ID, EXTRACTIVE_MODEL_ID, GROUNDING_GUARD_MODEL_ID, type AnswerReceipt } from "./answerEvents";
import { answerPhase, noSourceKind, type AnswerPhase, type AnswerState } from "./answerReducer";
import { formatSeconds } from "./shareFormat";
import { numberFormat } from "./numberFormat";
import { placesEmptyTitle } from "./placesFormat";
import { chatModelName, chatModelNameById, type NameableModel } from "./modelName";
import { answerSourceSplit } from "./sourceLabel";
import { articleCount } from "./liveResearch";
import { formatCount, toWords } from "../flows/format";

type T = (key: string, opts?: Record<string, unknown>) => string;

/**
 * A Deep Research sub-question in progress ("part 2 of 3"): the engine sends it on the `retrieving`
 * stage (answer.ts, multipass onProgress), so the searching step says which part it is on. Newer
 * engines add the sub-question itself (detail.subQuestion, optional, written by the model): read
 * defensively, one line, and only when it says something.
 */
function subQuestion(state: AnswerState): { index: number; count: number; question?: string } | null {
  const d = (state.deep ?? state.fast)?.detail;
  if (d?.index == null || !d.count) return null;
  const raw = (d as { subQuestion?: unknown }).subQuestion;
  const question = typeof raw === "string" ? raw.replace(/\s+/g, " ").trim() : "";
  return question ? { index: d.index + 1, count: d.count, question } : { index: d.index + 1, count: d.count };
}

/** The searching step's label: "Researching 2/3: <sub-question>", "Researching part 2 of 3…", or plain searching. */
function searchLabel(state: AnswerState, t: T): string {
  const part = subQuestion(state);
  if (!part) return t("chat.stage.searching");
  return part.question ? t("chat.stage.partQuestion", part) : t("chat.stage.part", part);
}

/** The stage line under an answer that has no text yet (or a deep pass in progress). */
export function stageLine(state: AnswerState, t: T): string | null {
  const tier = state.deep ?? state.fast;
  const phase = answerPhase(state);
  switch (phase) {
    case "searching":
      return searchLabel(state, t);
    case "loading_model":
      return t("chat.stage.loadingModel");
    case "reading": {
      // Articles, not passages: three passages of one article are one source read (LIVE_RESEARCH audit #1).
      const n = articleCount(state.sources);
      return n > 0 ? t("chat.stage.reading", { count: n }) : t("chat.stage.thinking");
    }
    case "verifying":
      return t("chat.stage.verifying");
    case "synthesizing": {
      const d = tier?.detail;
      return d?.index != null && d.count
        ? t("chat.stage.part", { index: d.index + 1, count: d.count })
        : t("chat.stage.synthesizing");
    }
    default:
      return null;
  }
}

/**
 * What a screen reader hears when the answer moves to `phase`; null means
 * stay quiet (tokens are never announced). Errors are assertive.
 */
export function phaseAnnouncement(
  phase: AnswerPhase,
  state: AnswerState,
  t: T,
  /** The library's indexing failed part-way: "not found" says so. */
  libraryIncomplete = false
): { message: string; assertive?: boolean } | null {
  switch (phase) {
    case "searching":
      return { message: t("chat.announce.searching") };
    case "locating":
      // Once, when the wait starts: what is happening and that the city can be typed.
      return { message: t("chat.announce.locating") };
    case "generating": {
      // Once per answer, with what it rests on (LIVE_RESEARCH a11y): the rows themselves are never announced.
      const n = state.places ? 0 : articleCount(state.sources);
      return { message: n > 0 ? t("chat.announce.answeringFrom", { count: n }) : t("chat.announce.answering") };
    }
    case "done":
      if (state.places) {
        if (state.places.coverage === "needs_place") return { message: t("chat.places.whichCity") };
        // Say what the screen says: the empty-state title, or how many places are listed.
        const empty = placesEmptyTitle(state.places, t);
        return { message: empty ?? t("chat.announce.placesFound", { count: state.places.places.length }) };
      }
      if (state.weakDeclined) {
        if (declineAfterSnippet(state)) return { message: t("chat.weak.heldAfterSnippet") };
        const c = declineCopy(state, libraryIncomplete);
        return { message: `${t(c.title)}. ${t(c.body)}` };
      }
      if (state.weakSources) return { message: t("chat.announce.readyNoSource") };
      {
        // CT-2: count what the card shows, the cited sources; none cited reads as no source.
        const cited = answerSourceSplit(state)?.cited.length ?? state.sources.length;
        if (cited === 0 && state.sources.length > 0) return { message: t("chat.announce.readyNoSource") };
        // No sources at all (a calculation, a fixed answer): just "ready", never "0 sources".
        if (cited === 0) return { message: t("chat.announce.readyPlain") };
        return { message: t("chat.announce.ready", { count: cited }) };
      }
    case "stopped":
      return { message: t("chat.announce.stopped") };
    case "error":
      return { message: t("chat.error.generic"), assertive: true };
    case "timeout":
    case "interrupted":
      return { message: t(`chat.notice.${phase}`) };
    default:
      return null;
  }
}

export const PLACES_MODEL_ID = "places";

/**
 * The one-time notice after a model load killed the app (Boar CR-2); null when there is nothing
 * to say. Without a previous model (first load of the session) it doesn't claim a switch back.
 */
/**
 * The display name for a model the crash marker names by id, label or file path (Prism CR-3: the
 * banner showed "models/qwen3-4b-instruct-2507-q4km.gguf"). Catalog (and discovered) label first;
 * otherwise the file's name without folder or ".gguf", never an internal path.
 */
export function modelDisplayName(
  ref: string,
  models: (NameableModel & { filename?: string })[],
  t: T
): string {
  const key = ref.trim();
  if (!key) return "";
  const found = models.find((m) => m.id === key || m.label === key || (!!m.filename && m.filename === key));
  if (found) return chatModelName(found, t);
  if (key.includes("/") || /\.gguf$/i.test(key)) return key.split("/").pop()!.replace(/\.gguf$/i, "");
  return key;
}

/** The crash with the chat's names for both models (CR-3; tier names, r4to). */
export function withDisplayNames<C extends { crashedLabel: string; fallbackLabel: string }>(
  crash: C | null,
  models: Parameters<typeof modelDisplayName>[1],
  t: T
): C | null {
  if (!crash) return null;
  return { ...crash, crashedLabel: modelDisplayName(crash.crashedLabel, models, t), fallbackLabel: modelDisplayName(crash.fallbackLabel, models, t) };
}

export function loadCrashMessage(crash: { crashedLabel: string; fallbackLabel: string } | null, t: T): string | null {
  if (!crash || !crash.crashedLabel.trim()) return null;
  const model = crash.crashedLabel.trim();
  const fallback = crash.fallbackLabel.trim();
  return fallback ? t("chat.loadCrash.message", { model, fallback }) : t("chat.loadCrash.messageNoFallback", { model });
}

/** A generation speed in tokens/s ("16", "8.4"): one decimal under 10. */
export function formatTokRate(tokPerSec: number, locale: string): string {
  return numberFormat(locale, 0, tokPerSec < 10 ? 1 : 0).format(tokPerSec);
}

/** Tokens as an approximate word count ("8"): whole words, one decimal under 1. The caller adds "~". */
export function approxWords(tokens: number, locale: string): string {
  const words = toWords(tokens);
  return numberFormat(locale, 0, words < 1 ? 1 : 0).format(words);
}

/**
 * Total time first, since that's what a person compares: "Answered in 6.2 s ·
 * Fast · 15 tok/s · started in 2.1 s · offline". The source-passage and
 * offline-map answers name their source instead of a model.
 */
export function receiptLine(r: AnswerReceipt, locale: string, t: T): string {
  const total = t("chat.receipt.answeredIn", { time: formatSeconds(r.totalMs, locale) });
  if (r.modelId === EXTRACTIVE_MODEL_ID) return [total, t("chat.receipt.sourcePassage"), t("chat.receipt.offline")].join(" · ");
  if (r.modelId === PLACES_MODEL_ID) return [total, t("chat.receipt.offlineMap"), t("chat.receipt.offline")].join(" · ");
  // I18N-2: the engine's label is English; say it in the app's language.
  if (r.modelId === GROUNDING_GUARD_MODEL_ID) return [total, t("chat.receipt.noOfflineSource"), t("chat.receipt.offline")].join(" · ");
  if (r.modelId === CALCULATOR_MODEL_ID) return [total, t("chat.receipt.calculator"), t("chat.receipt.offline")].join(" · ");
  const parts = [total, (r.modelLabel && chatModelNameById(r.modelId, r.modelLabel, t)) || t("chat.receipt.localModel")];
  if (r.tokPerSec > 0) parts.push(t("chat.receipt.speed", { rate: formatTokRate(r.tokPerSec, locale) }));
  if (r.ttftMs > 0) parts.push(t("chat.receipt.started", { time: formatSeconds(r.ttftMs, locale) }));
  parts.push(t("chat.receipt.offline"));
  return parts.join(" · ");
}

/**
 * The receipt facts that sit next to the assistant's name (a MetaLine): total time, plus the
 * generation speed when a model wrote the answer ("9.1 s · 10.7 tok/s"). The spoken and
 * expanded forms use `receiptLine`.
 */
export function receiptShort(r: AnswerReceipt, locale: string, t: T): string[] {
  const parts = [formatSeconds(r.totalMs, locale)];
  if (r.modelId !== EXTRACTIVE_MODEL_ID && r.modelId !== PLACES_MODEL_ID && r.tokPerSec > 0) {
    parts.push(t("chat.receipt.speed", { rate: formatTokRate(r.tokPerSec, locale) }));
  }
  return parts;
}

/**
 * The collapsed receipt of an answer (Prism CX-9: a decline had none, against NOVO NORTE P2 "total time
 * visible"). A decline shows its time only: the speed would measure text that isn't shown.
 */
export function answerReceiptShort(declined: boolean, r: AnswerReceipt, locale: string, t: T): string[] {
  return declined ? [formatSeconds(r.totalMs, locale)] : receiptShort(r, locale, t);
}

/** The measured details shown when the receipt is expanded, as label/value rows. */
export function receiptDetails(r: AnswerReceipt, locale: string, t: T): { label: string; value: string }[] {
  const rows: { label: string; value: string }[] = [];
  const s = (ms: number) => formatSeconds(ms, locale);
  if (r.loadMs) rows.push({ label: t("chat.receipt.load"), value: s(r.loadMs) });
  if (r.retrievalMs != null) rows.push({ label: t("chat.receipt.search"), value: s(r.retrievalMs) });
  if (r.prefillMs != null) rows.push({ label: t("chat.receipt.prefill"), value: s(r.prefillMs) });
  if (r.ctxTokens != null) rows.push({ label: t("chat.receipt.context"), value: t("chat.receipt.tokenCount", { tokens: formatCount(r.ctxTokens, locale), words: approxWords(r.ctxTokens, locale) }) });
  rows.push({ label: t("chat.receipt.start"), value: s(r.ttftMs) });
  // The run's own numbers, for comparing models and phones: which model, and its speed both ways.
  const generated = r.tokens > 0 && r.modelId !== EXTRACTIVE_MODEL_ID && r.modelId !== PLACES_MODEL_ID;
  if (generated && r.modelLabel) rows.push({ label: t("chat.receipt.model"), value: r.modelLabel });
  if (generated && r.tokPerSec > 0) {
    rows.push({ label: t("chat.receipt.speedLabel"), value: t("chat.receipt.speedBoth", { rate: formatTokRate(r.tokPerSec, locale), words: approxWords(r.tokPerSec, locale) }) });
  }
  rows.push({ label: t("chat.receipt.words"), value: t("chat.receipt.tokenCount", { tokens: formatCount(r.tokens, locale), words: approxWords(r.tokens, locale) }) });
  rows.push({ label: t("chat.receipt.total"), value: s(r.totalMs) });
  if (r.verification) rows.push({ label: t("chat.receipt.verification"), value: t(`chat.receipt.verified.${r.verification}`) });
  return rows;
}

/**
 * A collapsed passage as text cut at a word ("When attempting to stop a nosebleed at…"),
 * instead of a one-line numberOfLines clamp, which on Android with the app's font drew a
 * sliver of the second line (Prism, 06f508b).
 */
export function previewText(text: string, max = 110): string {
  const s = text.replace(/\s+/g, " ").trim();
  if (s.length <= max) return s;
  const cut = s.slice(0, max);
  const space = cut.lastIndexOf(" ");
  return `${(space > max * 0.6 ? cut.slice(0, space) : cut).replace(/[\s,;:.\-–—]+$/u, "")}…`;
}

/** The icon at the left of a step in the generating card (mockup: search, reasoning spark, streaming bolt). */
export function stageIcon(phase: AnswerPhase): "search" | "cpu" | "book-open" | "zap" | "check-circle" | "layers" | "map-pin" | "circle" {
  switch (phase) {
    case "searching":
      return "search";
    case "loading_model":
      return "cpu";
    case "reading":
      return "book-open";
    case "generating":
      return "zap";
    case "verifying":
      return "check-circle";
    case "synthesizing":
      return "layers";
    case "locating":
      return "map-pin";
    default:
      return "circle";
  }
}

export type StepStatus = "done" | "active" | "pending";
export interface GeneratingStep {
  key: "search" | "read" | "write";
  label: string;
  /** Short form for the pill next to the name ("Reading…"). */
  short: string;
  icon: "search" | "cpu" | "book-open" | "zap" | "check-circle" | "layers";
  status: StepStatus;
}

/**
 * The generating card as the mockup: every step from the start, done / active / pending. The steps are
 * the answer's real phases: searching this phone's library, reading the sources (or loading the model),
 * writing the answer (or checking it / researching part n of m). Null outside a running answer.
 */
export function generatingSteps(state: AnswerState, t: T): GeneratingStep[] | null {
  const phase = answerPhase(state);
  const order: Record<string, number> = { searching: 0, loading_model: 1, reading: 1, generating: 2, verifying: 2, synthesizing: 2 };
  const at = order[phase];
  if (at == null) return null;
  const status = (i: number): StepStatus => (i < at ? "done" : i === at ? "active" : "pending");
  const n = articleCount(state.sources);
  const read =
    phase === "loading_model"
      ? { label: t("chat.stage.loadingModel"), icon: "cpu" as const }
      : n > 0
        ? { label: t("chat.stage.reading", { count: n }), icon: "book-open" as const }
        : { label: t("chat.stage.thinking"), icon: "book-open" as const };
  const write =
    phase === "verifying"
      ? { label: t("chat.stage.verifying"), icon: "check-circle" as const }
      : phase === "synthesizing"
        ? { label: stageLine(state, t) ?? t("chat.stage.synthesizing"), icon: "layers" as const }
        : { label: t("chat.stage.writing"), icon: "zap" as const };
  return [
    { key: "search", label: phase === "searching" ? searchLabel(state, t) : t("chat.stage.searching"), short: t("chat.stepShort.search"), icon: "search", status: status(0) },
    { key: "read", label: read.label, short: t(phase === "loading_model" ? "chat.stepShort.load" : "chat.stepShort.read"), icon: read.icon, status: status(1) },
    { key: "write", label: write.label, short: t("chat.stepShort.write"), icon: write.icon, status: status(2) },
  ];
}

/**
 * The mascot's entrance right after boot (Iris, transition splash → chat): wait, then fade in, so it never
 * overlaps or jumps from the native splash's boar. Under reduce motion the wait stays (a delay is not
 * motion, and it is what keeps the two boars apart) and the fade goes: it appears at once. Null = show at
 * once: not the first mount after launch.
 */
export function bootEntranceTiming(
  firstAfterBoot: boolean,
  reduceMotion: boolean,
  duration: { slow: number; base: number }
): { delay: number; fade: number } | null {
  if (!firstAfterBoot) return null;
  return { delay: duration.slow, fade: reduceMotion ? 0 : duration.base };
}

/**
 * Whether an instant answer offers "Answer with the model": after a source passage, yes; not after
 * the engine's fixed answer (grounding-guard: a current-events question, Prism CT-4), where a model
 * would only guess what an offline snapshot can't know.
 */
export function offersAskModel(a: { instantDone?: { receipt: AnswerReceipt }; fast?: unknown; places?: unknown }): boolean {
  if (!a.instantDone || a.fast || a.places) return false;
  // Nor after the exact arithmetic (calculator): a model would only be less exact.
  return a.instantDone.receipt.modelId !== GROUNDING_GUARD_MODEL_ID && a.instantDone.receipt.modelId !== CALCULATOR_MODEL_ID;
}

/**
 * The tag after the receipt's numbers (Iris): "general knowledge" only when nothing on the phone
 * covered the question, where it is true; "no source cited" when passages on the topic were found
 * but the model cited none (it may have used them, so "general knowledge" could be false).
 */
export function receiptTagKey(a: AnswerState): "chat.weak.receipt" | "chat.weak.receiptUncited" | "chat.receipt.calculator" | null {
  // The exact conversion (Prism CALC-1): say by the name that no model wrote it, as the other tags do.
  if (!a.fast && a.instantDone?.receipt.modelId === CALCULATOR_MODEL_ID) return "chat.receipt.calculator";
  // A decline answered nothing: "general knowledge" would be false (its card says what happened).
  if (a.weakDeclined) return null;
  const kind = noSourceKind(a);
  return kind === "weak" ? "chat.weak.receipt" : kind === "uncited" ? "chat.weak.receiptUncited" : null;
}

/**
 * Which note sits in the sources' slot of a finished answer (the tag changes, the warning never
 * goes, Prism): "weak" (nothing on the topic: note B), "uncited" (passages found, none cited:
 * CT-5 note), or none. A declined answer has its own card, a places list its own card.
 */
export function noSourceNote(a: AnswerState, placesOnly: boolean): "weak" | "uncited" | null {
  if (placesOnly || a.weakDeclined) return null;
  return noSourceKind(a);
}

/**
 * The "From the source" card shows the instant passage unless the engine's literal excerpt answers
 * (Prism DUP-1: a health question got both, the same instruction twice, pushing the emergency note
 * off screen; the excerpt is the engine's final answer, with [n], language label and emergency line),
 * or nothing on the phone was on the topic.
 */
export function showsInstantSnippet(a: AnswerState): boolean {
  return !!a.instant && !a.extract && noSourceKind(a) !== "weak";
}

/**
 * The engine's language lead on an instant passage (Tusk 29d7e52): "Da fonte offline (em inglês):" or
 * "From the offline source (in Portuguese):" as the first line when the passage's language differs
 * from the question's. The card shows the language by its header ("From the source (in Portuguese) ·
 * Title") instead of saying "from the source" twice; copy and share keep the text as it came.
 */
export function sourceLanguageLead(text: string): { lang: string | null; body: string } {
  const m = /^(?:Da fonte offline|From the offline source) \(([^)]+)\):[ \t]*\n/.exec(text);
  return m ? { lang: m[1], body: text.slice(m[0].length) } : { lang: null, body: text };
}

/**
 * Why the compact model's answer was withheld (weak_sources declined), which the card and the
 * announcement must say truthfully: "none" = nothing on the topic in this phone's library;
 * "unsupported" = passages on the topic were found, but every citation the model made was removed
 * as unsupported (Tusk 237764a, grounding:all-citations-removed-declined-compact). Off-topic
 * passages never reach the chat (Tusk 404d688), so the sources tell the two apart.
 */
export function declineCopy(a: AnswerState, libraryIncomplete = false): { title: string; body: string } {
  if (a.sources.length > 0) return { title: "chat.weak.unsupportedTitle", body: "chat.weak.unsupportedBody" };
  return libraryIncomplete
    ? { title: "chat.weak.declinedTitleIncomplete", body: "chat.weak.declinedBodyIncomplete" }
    : { title: "chat.weak.declinedTitle", body: "chat.weak.declinedBody" };
}

/**
 * A compact-model decline right under the library's own passage on the topic (Boar, Piston ecb83d3):
 * a Portuguese question over English passages loses its citations in the check almost every time, so
 * an error card there reads as a failure right after a hit. The decline becomes one quiet line under
 * the passage instead; with no passage shown, the card stays. Presentation only, the engine is unchanged.
 */
export function declineAfterSnippet(a: AnswerState): boolean {
  return !!a.weakDeclined && showsInstantSnippet(a);
}

/**
 * Whether the model tier's text shows as the answer body. Not on a decline (weak_sources declined):
 * the engine now puts the decline's own sentence in done.finalText (Tusk 05d1e6b, "never an empty
 * screen") for readers that don't know the card (history, share, screen runners); on screen the
 * decline card already says it, so the body would repeat it.
 */
export function showsAnswerBody(a: AnswerState): boolean {
  return !a.weakDeclined;
}

/**
 * Whether the step card's ring turns (GFXINFO 28/09: its native-driven loop invalidated the view on every
 * vsync for the whole generation, one traversal per animation callback). Once the answer's text is on
 * screen, the text and its caret show the progress: the ring stands still (as under reduce motion).
 */
/**
 * Whether a tier ends with a notice under its text (stopped, interrupted, timed out, failed): the block
 * exists only then, so an answer's animated blocks never keep a gap for nothing (SEND-MOTION).
 */
export function noticeShown(tier: { outcome?: string } | undefined, interrupted?: boolean): boolean {
  if (!tier?.outcome) return false;
  return !!interrupted || ["interrupted", "stopped", "timeout", "error"].includes(tier.outcome);
}

/**
 * The step the running pill shows (iPhone v9): the current one, or the last one once the model's steps are
 * over, so the pill doesn't crossfade "Writing · 6 s" into "6 s" in place (two texts of different widths
 * over each other) right before it crossfades into the receipt. One swap only: pill → receipt.
 */
export function pillStep(current: string | undefined, last: string | undefined): string | undefined {
  return current ?? last;
}

/**
 * Whether the instant snippet folds to its preview on its own: once the model's answer is done, unless the
 * snippet is the final answer, or the model's answer was declined (Prism F2-10): then "the passage above is
 * what the library says" and the passage is the answer, so it stays whole (folding it also shrank a long
 * answer under the screen and made the list jump).
 */
export function snippetAutoCollapses(a: { fast?: { outcome?: string }; weakDeclined?: boolean }, isFinal: boolean): boolean {
  return a.fast?.outcome === "success" && !isFinal && !a.weakDeclined;
}

/**
 * SEND-MOTION D3 (Boar: option A): the steps card stays until the answer's own text starts, then shrinks
 * while the text takes its place; the progress goes on in the header pill (time + step) and the caret.
 * So at the end nothing leaves ABOVE the text (the card "vanishing out of nowhere" r4to saw). Option B
 * (the mockup's order, card above the text until done) would be `!!steps` alone.
 */
export function stepsCardShown(state: AnswerState, steps: readonly unknown[] | null): boolean {
  return !!steps && stepSpinnerRuns(state);
}

export function stepSpinnerRuns(state: AnswerState): boolean {
  // A Deepen shows its own steps: only the deep text counts there.
  const written = state.deep ? state.deep.text : state.fast?.text || state.extract || "";
  return !written.trim();
}
