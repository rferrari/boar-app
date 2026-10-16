/**
 * The layered answer pipeline: classify → plan depth (depth.ts) → retrieve →
 * instant snippet → compressed-context generation (fast or deep) → optional
 * verification, all reported as AnswerEvents (events.ts).
 *
 * Dependencies are injected (createAnswerer) so the whole flow is testable
 * without native modules; answerService.ts wires the real ones.
 *
 * One answer at a time: starting a new answer stops the previous one and
 * waits for it to settle, and LlamaEngine itself serializes completions, so
 * a double send can never run two completions on one context.
 */
import { classifyTask } from "./classify";
import { gatedTaskType, routeGateSkip, routeGateMessages, routeGateVerdict, ROUTE_GATE_CONFIG, ROUTE_GATE_MODEL_IDS, ROUTE_GATE_N_PROBS, type GateMessage, type TokenProb } from "./routeGate";
import {
  compressContext,
  selectInstant,
  instantFinalBlock,
  INSTANT_FINAL_CONFIDENCE,
  HEALTH_GROUNDING_INSTRUCTION,
  isHealthQuestion,
  isSafetyQuery,
  isCurrentEventQuery,
  currentEventAnswer,
  mentionsNow,
  isPhraseQuestion,
  splitSentences,
  wrongScriptSentences,
  stripModelDisclaimer,
  stripModelReferences,
  withSeekCare,
  PT_ANSWER_LANGUAGE,
  PT_ANSWER_LANGUAGE_NO_SOURCES,
  situationNote,
  isPortugueseQuestion,
  healthSourceOrder,
  safeHealthExcerpt,
  noSafeStepsAnswer,
  withAfterPart,
  sentenceNamesSubject,
  falseQuantumClaims,
  isSubstantive,
  identifiersIn,
  titleHasIdentifier,
  sourceLanguageLead,
  isTodayInHistory,
  historyDate,
  todayLine,
  ACTION_INTENT,
  MIN_TERM_COVERAGE,
  NO_SOURCE_INSTRUCTION,
  uncitedPreface,
  noHealthSourceAnswer,
  isSpanishQuestion,
  isHealthFollowUp,
  isNoStepsAnswer,
  isPtDefinitional,
  definesName,
  onTopic,
  namedByLexicon,
  PT_QUESTION,
  healthExtract,
  healthSourceIndex,
  coreProcedure,
  excerptRules,
  emergencyLine,
  healthTopicTerms,
  onHealthTopic,
  riskyHealthInstruction,
  termCoverage,
} from "./context";
import { canonicalHealthTerms, englishSearchTerms } from "./ptQuery";
import { englishNamesIn } from "../rag/ptLexicon";
import { ptLexicon } from "../rag/ptLexiconAsset";
import { cleanPlaceName } from "../rag/poiPack";
import { attributeCitations, checkCitations } from "./citations";
import { calculate } from "./calculators";
import { travelDistanceAnswer } from "./travelDistance";
import { verdictGuard } from "./verdictGuard";
import { arithmeticAnswer } from "./arithmetic";
import { numericGuard } from "./numguard";
import { emergencyNumbersAnswer } from "./emergencyNumbers";
import { factsAnswer } from "../rag/facts";
import { coHazardAnswer, isCoHazardQuestion } from "./coHazard";
import { isSnakebiteFirstAid, snakebiteFirstAidCard as cut2SnakebiteCard } from "./firstAidCards";
import { DETECTOR_FLOOR } from "./emergencyHead";
import type { LoadFailureKind } from "../inference/loadError";
import { DepthModel, planAnswer, resolveDeepModel, AnswerPlan, deepAutoIneligibility } from "./depth";
import { isCompactModel, pickDefaultAnswerModel, tooBigForLowRam } from "./defaultModel";
import { buildVerificationInput, parseVerificationVerdict, VERIFICATION_INSTRUCTION } from "./verify";
import { taskRequest } from "../inference/format";
import {
  detectGeoIntent,
  distanceMeters,
  formatPlacesAnswer,
  GeoIntent,
  GeoProviders,
  needsPlaceAnswer,
  noDataAnswer,
  noMatchAnswer,
  noPackAnswer,
  staleLocationAnswer,
  toPlace,
  dedupPlaces,
  toSourceChunk,
} from "./geo";
import type {
  AnswerErrorCode,
  AnswerEvent,
  AnswerEventHandler,
  AnswerHandle,
  AnswerOutcome,
  AnswerReceipt,
  AnswerRequest,
  AnswerResult,
  AnswerStageName,
  AnswerTier,
  SourceChunk,
  StageDetail,
} from "./events";
import type { ModelRole } from "./types";
import type { RetrievedChunk } from "../rag/retrieve.types";
import type { ConversationHistory, ChatMessage } from "../rag/pure";
import type { GenerateOptions, GenerationTimings, LoadResult, PromptPrefix } from "../inference/LlamaEngine";
import type { MemoryFit } from "../inference/memoryFit";
import type { AnswerSettings } from "../models/settings";
import type { ResearchOptions, ResearchProgress, ResearchResult } from "../services/orchestrator";
import type { ExecutionOutcome, ExecutionTelemetryRecord, ModelResidency } from "../services/executionTelemetry.pure";
import { smallTalkReply } from "./smallTalk";
import { conditionCardText as cut2ConditionCardText, conversationCondition, isCardEmergency } from "./conditionCards";
import { withReviewedSnakebiteText } from "./snakebiteCardText";
import { classify as classifyHealthGap, doseExcerpt, healthGapCard, healthGapCodes, healthGapTitle, noDoseAnswer, type HealthGap } from "./healthGap";

// SBt (cut-3 final candidate): the snakebite card's EN text is the reviewed text (snakebiteCardText.ts); the trigger and
// the card code in firstAidCards.ts / conditionCards.ts are cut 2's, byte for byte.
const snakebiteFirstAidCard = (query: string, pt: boolean, es = false) => withReviewedSnakebiteText(cut2SnakebiteCard(query, pt, es));
const conditionCardText = (...args: Parameters<typeof cut2ConditionCardText>) => withReviewedSnakebiteText(cut2ConditionCardText(...args));

/**
 * The no-source health card (Sonar exp-29, r4to 2026-10-03: never answer an emergency from memory, recover with
 * first-aid cards): a question whose terms name exactly one condition (conditionCards.ts, Compass trigger v1) gets
 * that condition's card; several or none, or a Spanish question (no ES card texts yet) → the generic card.
 */
function noSourceHealthCard(query: string, pt: boolean, es: boolean, reasonCodes: string[], userTurns: string[] = [], gap: HealthGap | null = null): { text: string; label: string } {
  const { condition: r, fromHistory } = conversationCondition(query, userTurns);
  if (r === "MULTI") reasonCodes.push("grounding:fa-card-multi");
  const condition = r === "MULTI" ? null : r;
  const text = condition ? conditionCardText(condition, query, es ? "es" : pt ? "pt" : "en") : null;
  if (condition && text) {
    reasonCodes.push(`grounding:fa-card-${condition}`);
    if (fromHistory) reasonCodes.push("grounding:fa-card-from-history");
    return { text, label: "First aid card" };
  }
  // HEALTHGAP (rule v1.1 §2.2): no specific card -> the class's own: the poisoning or CO card, the dose line; survival keeps the generic line.
  const fallback = healthGapCard(gap);
  const card = fallback ? conditionCardText(fallback, query, es ? "es" : pt ? "pt" : "en") : null;
  if (fallback && card) {
    reasonCodes.push(`grounding:fa-card-${fallback}`);
    return { text: card, label: "First aid card" };
  }
  if (gap?.cls === "dose") {
    reasonCodes.push("grounding:health-no-dose");
    return { text: noDoseAnswer(pt, es), label: "No offline source" };
  }
  return { text: noHealthSourceAnswer(pt, es), label: "No offline source" };
}

/** Development builds log each answer's checks to Metro ([ANSWER] lines); never in a release build or a test. */
const DEV_LOG = typeof __DEV__ !== "undefined" && __DEV__ && process.env.NODE_ENV !== "test";

export interface InstalledLlm {
  id: string;
  label: string;
  filename: string;
  sizeBytes: number;
  roles: ModelRole[];
  /** The app's default model (fallback when no active model is set or it vanished). */
  isDefault?: boolean;
  /** Catalog tier for the device-dependent default (see defaultModel.ts). */
  answerTier?: "default" | "compact";
}

export interface AnswerEngine {
  load(filename: string, opts?: { meta?: { modelId?: string; label?: string } }): Promise<LoadResult>;
  generate(opts: GenerateOptions): Promise<string>;
  stop(): Promise<void>;
  getModelInfo(): { filename: string } | null;
  hasEmbeddedChatTemplate(): boolean;
  estimateFit(filename: string): Promise<MemoryFit | null>;
  /** Keeps this prompt start prefilled in the KV cache between answers (LlamaEngine.setAnswerPrefix). */
  setAnswerPrefix?(prefix: PromptPrefix | null): void;
  /**
   * Top-N probabilities of the next token after `messages` (chat template applied), on the model in `modelFilename`.
   * Null when that model isn't the loaded one (the route gate then keeps the rules).
   */
  nextTokenProbs?(messages: GateMessage[], opts: { modelFilename: string; nProbs: number }): Promise<TokenProb[] | null>;
}

export interface AnswerDeps {
  engine: AnswerEngine;
  retrieve(query: string, k: number): Promise<RetrievedChunk[]>;
  getSettings(): Promise<AnswerSettings>;
  listInstalledLlms(): Promise<InstalledLlm[]>;
  getActiveModelId(): Promise<string | null>;
  runMultipass(
    query: string,
    systemPrompt: string | undefined,
    history: ConversationHistory | undefined,
    maxTokens: number,
    onProgress: (p: ResearchProgress) => void,
    onToken: (piece: string) => void,
    shouldStop: () => boolean,
    options: ResearchOptions
  ): Promise<ResearchResult>;
  /** Prompt builders (src/rag/pure.ts), injected so tests can inspect what the model sees. */
  assemblePrompt(q: string, chunks: RetrievedChunk[], system?: string, history?: ConversationHistory, style?: string): string;
  assembleChatMessages(q: string, chunks: RetrievedChunk[], system?: string, history?: ConversationHistory, style?: string): ChatMessage[];
  /** The fixed start those builders give every answer with sources, for a tone (src/rag/pure.ts answerPromptPrefix). */
  answerPrefix?(system?: string): PromptPrefix;
  now(): number;
  /** The device's calendar date (tests pass a fixed one). */
  today?(): Date;
  /** Context window the model will be loaded with (LlamaEngine defaultContextSize). */
  contextSize?(): number;
  /** Total device RAM (0 = unknown), for the device-dependent default model. */
  deviceRamBytes?(): number;
  /** Median measured tok/s per model id on this device (execution telemetry), for rule D6. */
  getModelSpeeds?(): Promise<Map<string, number>>;
  /** Offline places (POI pack + device location); null when not installed/registered. */
  getGeoProviders?(): GeoProviders | null;
  /**
   * Resolves once the built-in knowledge base is indexed (the first boot after install indexes it
   * after the models load). A question asked meanwhile waits instead of searching an empty index.
   */
  knowledgeReady?(): Promise<void>;
  /**
   * Sonar exp-30 (health11): a learned multilingual emergency detector (mmBERT-small GGUF via llama.rn +
   * a JS head, src/routing/emergencyHead.ts). When present it replaces the lexical isHealthQuestion as the
   * health predicate (OR the card triggers and the health follow-up). null = unavailable -> lexical.
   */
  emergencyScore?(query: string, userTurns: string[]): Promise<{ p: number; fire: boolean } | null>;
  /**
   * HEALTHGAP (src/routing/healthGap.ts, rule v1.1): a dose, poisoning, carbon-monoxide-reading or survival-time question
   * counts as a detector fire (the health route) and gets its class's no-source answer (dose line, poisoning or CO card).
   * Codes keep the detector's own detector:p / detector:fire and add healthgap:<class>. Default off; true = on.
   */
  healthgap?: boolean;
  /** FACTS instant tier (src/rag/facts.ts); default on. false = off (model-path tests whose fixtures are fact questions). */
  facts?: boolean;
  /**
   * NUMGUARD (src/routing/numguard.ts): when the learned detector fires on an English, single-turn pure conversion or clock
   * calculation with no health or person word in any user turn, a deterministic calculator may answer it (codes keep detector:p,
   * detector:fire, numguard:fired). No calculator answers -> the detector's call stands (code numguard:no-calculator): a model never
   * answers a fired message. Default off; true = on.
   */
  numguard?: boolean;
  /**
   * English article names a Portuguese question mentions (Bramble's lexicon, src/rag/ptLexicon.ts
   * englishNamesIn): "Por que existem as estações do ano?" -> ["Season"]. The grounding guard matches
   * sources against them; without them a PT question never names an English title.
   */
  englishNames?(query: string): string[];
  /**
   * Persists one execution record (src/services/executionTelemetry.ts recordExecution) at the end of
   * every answer a model generated: the Performance screen, the Models speeds and rule D6
   * (getModelSpeeds) read them. Answers with no model (instant excerpt, places, cards) record nothing.
   */
  recordExecution?(record: Omit<ExecutionTelemetryRecord, "id" | "createdAt">): Promise<void>;
  /**
   * The app's memory right now, for the execution record's peak (peakRssBytes). iOS: phys_footprint, what
   * jetsam counts and what the "[BOAR mem] footprint_mb" log line shows; Android: VmRSS. 0 or a throw = unknown.
   */
  memoryBytes?(): number;
}

/** A generation samples the app's memory every this many tokens (plus load, first token and end). */
export const MEMORY_SAMPLE_EVERY_TOKENS = 16;

/** GPS budget: the first useful information must appear in under a second. */
export const LOCATION_TIMEOUT_MS = 700;
/** "Near me" without a fix of 5 minutes or less: how long to wait for a new one (a cold GPS takes seconds). */
export const LOCATION_WAIT_MS = 10_000;
/** A fix that isn't in by then is not a cached one: tell the UI we're locating. */
export const LOCATING_SIGNAL_MS = 150;
/** City path: a fix that is not in by the end of the POI search is ignored for PlacesArea.deviceInside. */
export const DEVICE_INSIDE_TIMEOUT_MS = 150;
/** A named city's reach from its center for deviceInside (metro areas included). */
export const DEVICE_INSIDE_RADIUS_M = 25_000;
export const PLACES_LIMIT = 10;

/** Max <think> tokens before an answer (reasoning models only; see LlamaEngine thinkingBudget). */
export const FAST_THINKING_BUDGET = 256;
export const DEEP_THINKING_BUDGET = 1024;

/** Room kept for the system prompt, history and template tokens around the sources. */
const PROMPT_OVERHEAD_TOKENS = 512;

export interface AnswerContext {
  systemPrompt?: string;
  styleReminder?: string;
  history?: ConversationHistory;
  /** User's Max Output Tokens. */
  maxTokens: number;
}

let answerSeq = 0;
const newAnswerId = () => `ans-${Date.now().toString(36)}-${(answerSeq++).toString(36)}`;

function errorCodeOf(message: string, stage: "load" | "generate"): AnswerErrorCode {
  if (/cannot be streamed|out of memory|oom/i.test(message)) return "oom";
  return stage === "load" ? "load_failed" : "generation_failed";
}

/**
 * Which model answers, with the rules applied everywhere (the user's pick,
 * CR-1 low RAM, CR-2 crashed loads, the device default). Shared by answer()
 * and the chat header (effectiveAnswerModel), so they never disagree.
 */
async function selectAnswerModel(deps: AnswerDeps) {
  let lowRamNote: string | null = null;
  let lowRamBlocked = false;
  const [settings, allInstalled, activeId, speeds] = await Promise.all([
    deps.getSettings(),
    deps.listInstalledLlms(),
    deps.getActiveModelId(),
    deps.getModelSpeeds ? deps.getModelSpeeds().catch(() => new Map<string, number>()) : Promise.resolve(new Map<string, number>()),
  ]);
  let installed = allInstalled;
  // CR-1: on a low-RAM phone, a model above the compact size is never used unless the
  // user confirmed it: not as the saved pick, the default, a fallback, deep or verifier.
  const ram = deps.deviceRamBytes?.() ?? 0;
  const confirmed = new Set(settings.largeModelConfirmedIds ?? []);
  // CR-2: a model whose load killed the app is not loaded again on its own (crash loop);
  // the crash withdrew its confirmation, so only a new one lets it run.
  const crashed = new Set(settings.loadCrashedIds ?? []);
  const blocked = installed.filter((m) => (tooBigForLowRam(m, ram) || crashed.has(m.id)) && !confirmed.has(m.id));
  const blockedActive = activeId ? blocked.find((m) => m.id === activeId) : undefined;
  if (blocked.length) {
    installed = installed.filter((m) => !blocked.includes(m));
    lowRamBlocked = installed.length === 0;
    if (blockedActive) lowRamNote = crashed.has(blockedActive.id) ? `model:load-crashed-${blockedActive.id}` : `model:low-ram-unconfirmed-${blockedActive.id}`;
  }
  const byId = new Map(installed.map((m) => [m.id, m]));
  // The user's pick; without one (or if it was deleted), the device-dependent default:
  // Qwen3-4B where it stays resident, the compact 1.5B on 4 GB phones.
  let fastLlm = activeId ? byId.get(activeId) : undefined;
  if (!fastLlm && installed.length) {
    // Header reads only where the fit can change the pick: the default tier, and other models measured fast.
    const candidates = await Promise.all(
      installed.map(async (m) => {
        const tokPerSec = speeds.get(m.id);
        const needsFit = m.answerTier === "default" || (!m.answerTier && tokPerSec !== undefined);
        return {
          id: m.id,
          answerTier: m.answerTier,
          sizeBytes: m.sizeBytes,
          tokPerSec,
          fit: needsFit ? (await deps.engine.estimateFit(m.filename).catch(() => null))?.verdict : undefined,
        };
      })
    );
    const pick = pickDefaultAnswerModel(candidates, ram);
    fastLlm = pick ? byId.get(pick.id) : undefined;
    fastLlm ??= installed.find((m) => m.isDefault) ?? installed[0];
  }
  return { settings, installed, speeds, fastLlm, blockedActive, lowRamNote, lowRamBlocked };
}

/** What the chat header shows before any question: the model that will answer, and the saved one it replaces. */
export interface EffectiveAnswerModel {
  id: string;
  label: string;
  /** The user's saved model, not used here (low RAM without confirmation, or its load killed the app). */
  downgradedFrom?: { id: string; label: string; reason: "low-ram" | "load-crashed" };
}

/** The bundled PT->EN lexicon's names: every caller gets the PT topic guard (the eval runner passed no englishNames, gate ea5978c). */
const defaultEnglishNames = (query: string) => englishNamesIn(query, ptLexicon());

/** Multi-pass sources: sub-questions score on their own scales, so there is no comparable relevance. */
const withoutRelevance = (s: RetrievedChunk[]): RetrievedChunk[] => s.map(({ relevance: _r, ...c }: SourceChunk) => c);

/** Receipts of answers no model wrote (fixed answers, source excerpts). */
/** Crypto-pack sources (their source labels) and a crypto cue in the user's own words (PT/EN/ES). "gás/gas" is not a
 *  cue: a gas leak is an emergency. Sonar exp-30 (health11). */
const CRYPTO_SOURCE = /^(Ethereum EIPs\/ERCs|Ethereum specs|ethereum\.org|Bitcoin BIPs)\b/;
const CRYPTO_CUE =
  /(?<![\p{L}\p{N}])(ethereum|ether|eth|bitcoin|btc|blockchains?|cripto\p{L}*|crypto\p{L}*|tokens?|nfts?|erc-?\d*|eip-?\d*|bip-?\d*|carteiras?( digita\p{L}*| cripto\p{L}*)?|wallets?|billeteras?|staking|stake|validador\p{L}*|validator\p{L}*|rollups?|l2s?|layer ?2|camada ?2|capa ?2|defi|smart contracts?|contratos? inteligentes?|solidity|minera\p{L}*|mining|hash\p{L}*|merkle|zk\p{L}*|sharding|consenso|consensus|privacy pools?|tornado cash|stablecoins?|usdc|usdt|uniswap|airdrops?|chave privada|private keys?|seed phrase|frase semente|mempool|satoshis?|lightning network|proof of (stake|work)|prova de (participa\p{L}+|trabalho)|prueba de (participaci\p{L}+|trabajo)|vitalik|buterin|nakamoto|dapps?|web3)(?![\p{L}\p{N}])/iu;

const NOT_A_MODEL = new Set(["grounding-guard", "extractive", "none", "places", "calculator"]);

export function createAnswerer(deps: AnswerDeps) {
  let current: AnswerHandle | null = null;

  function answer(req: AnswerRequest, onEvent: AnswerEventHandler, ctx: AnswerContext): AnswerHandle {
    const answerId = newAnswerId();
    // The tone of this chat is the one the next question will most likely use too: keep its prefix prefilled.
    if (deps.answerPrefix) deps.engine.setAnswerPrefix?.(deps.answerPrefix(ctx.systemPrompt));
    const previous = current;
    let stopRequested = false;
    // Resolves on stop(), so a wait (the GPS) ends at once instead of running out its timeout.
    let signalStop: () => void = () => {};
    const stopSignal = new Promise<"stopped">((r) => (signalStop = () => r("stopped")));
    // Health, first aid or a disaster: the chat shows the emergency-services line (one classifier: this one).
    const safety = isSafetyQuery(req.query) || isCoHazardQuestion(req.query);
    /** Whether the chat was already told that no strong source backs this answer. */
    let weakSourcesSent = false;
    const emit = (e: AnswerEvent) => {
      if (e.type === "warning" && e.code === "weak_sources") weakSourcesSent = true;
      try {
        onEvent(e.type === "done" && safety ? { ...e, safety: true } : e);
      } catch (err) {
        console.warn("[answer] onEvent threw", err);
      }
    };

    const done = (async (): Promise<AnswerResult> => {
      // Double send: the previous answer is stopped and fully settled before
      // this one touches the model.
      if (previous) {
        await previous.stop();
        await previous.done.catch(() => {});
      }
      try {
        return await run();
      } catch (e: any) {
        // The contract promises a final done event, whatever failed.
        const message = e?.message ?? String(e);
        const receipt: AnswerReceipt = { modelId: "none", modelLabel: "", tokens: 0, tokPerSec: 0, ttftMs: 0, totalMs: 0, reasonCodes: ["error:unexpected"] };
        emit({ type: "done", answerId, tier: "fast", outcome: "error", receipt, error: { code: "unknown", message } });
        return { answerId, tier: "fast", outcome: "error", text: "", sources: [], receipt };
      }
    })();

    const handle: AnswerHandle = {
      answerId,
      async stop() {
        stopRequested = true;
        signalStop();
        await deps.engine.stop();
      },
      done,
    };
    current = handle;
    done
      .finally(() => {
        if (current === handle) current = null;
      })
      .catch(() => {});
    return handle;

    async function runGeo(
      intent: GeoIntent,
      t0: number,
      markVisible: () => void,
      visibleAt: () => number | null
    ): Promise<AnswerResult> {
      const reasonCodes = ["task:places", `near:${intent.near.kind}`, ...intent.diet.map((d) => `diet:${d}`)];
      const receipt = (retrievalMs?: number): AnswerReceipt => ({
        modelId: "places",
        modelLabel: "Offline places",
        tokens: 0,
        tokPerSec: 0,
        ttftMs: (visibleAt() ?? deps.now()) - t0,
        totalMs: deps.now() - t0,
        retrievalMs,
        reasonCodes,
      });
      const filters = intent.diet.length ? intent.diet : undefined;
      const criterion = intent.diet.length ? ("diet_match" as const) : ("distance" as const);
      const finishPlaces = (
        coverage: "ok" | "none" | "no_pack" | "needs_place",
        text: string,
        area: Extract<AnswerEvent, { type: "places" }>["area"],
        retrievalMs?: number,
        empty?: "no_data" | "no_match"
      ): AnswerResult => {
        markVisible();
        emit({ type: "places", answerId, tier: "instant", places: [], area, filters, criterion, coverage, ...(empty ? { empty } : {}), attribution: [] });
        const r = receipt(retrievalMs);
        emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r });
        return { answerId, tier: "instant", outcome: "success", text, sources: [], receipt: r };
      };

      emit({ type: "stage", answerId, stage: "retrieving", tier: "instant", at: deps.now() });
      const geo = deps.getGeoProviders?.() ?? null;
      const pt = intent.lang === "pt";
      const packInstalled = geo ? await (geo.hasPlaces?.() ?? Promise.resolve(true)).catch(() => true) : false;
      if (!geo || !packInstalled) {
        reasonCodes.push("places:no-pack");
        return finishPlaces("no_pack", noPackAnswer(intent), { kind: intent.near.kind === "device" ? "near" : "city" });
      }

      // A city written in lower case or after "de" counts only when the gazetteer knows it.
      if (intent.near.kind === "device" && intent.placeCandidates?.length) {
        for (const c of intent.placeCandidates) {
          const hit = await geo.resolvePlace(c).catch(() => null);
          // An alternate-name match on a small town is too weak ("center" -> Ózd): a city, or the same name.
          const fold = (x: string) => x.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
          if (hit && (hit.kind === "city" || fold(hit.name) === fold(c))) {
            intent = { ...intent, near: { kind: "place", name: c } };
            reasonCodes.push("places:gazetteer-name");
            break;
          }
        }
      }

      let center: { lat: number; lon: number };
      let area: Extract<AnswerEvent, { type: "places" }>["area"];
      let deviceNow: { value: Awaited<ReturnType<GeoProviders["getLocation"]>> | null } | null = null;
      if (intent.near.kind === "place") {
        const place = await geo.resolvePlace(intent.near.name).catch(() => null);
        if (!place) {
          reasonCodes.push("places:unknown-place");
          return finishPlaces(
            "none",
            noDataAnswer(intent, intent.near.name),
            { kind: "city", label: intent.near.name, place: { name: intent.near.name } },
            undefined,
            "no_data"
          );
        }
        center = { lat: place.lat, lon: place.lon };
        // The point too: an empty answer can then offer the map covering the city (T2-8).
        const asked = cleanPlaceName(intent.near.name).name;
        area = { kind: "city", label: place.name, place: { name: place.name, ...(asked ? { asked } : {}), country: place.country, lat: place.lat, lon: place.lon } };
        // Runs alongside the POI search; never prompts (GeoProviders contract). Read only if it
        // has already answered when the search ends: the list never waits for it.
        const pending = { value: null as Awaited<ReturnType<GeoProviders["getLocation"]>> | null };
        deviceNow = pending;
        geo.getLocation({ timeoutMs: DEVICE_INSIDE_TIMEOUT_MS }).then((v) => (pending.value = v), () => {});
      } else {
        // A fix of 5 minutes or less comes back at once; otherwise wait for a new one (cold GPS),
        // telling the UI, which offers "Type the city" meanwhile. stop() ends the wait.
        const request = (
          geo.getLocationFix
            ? geo.getLocationFix({ timeoutMs: LOCATION_WAIT_MS })
            : geo.getLocation({ timeoutMs: LOCATION_TIMEOUT_MS })
        ).catch(() => ({ error: "unavailable" as const }));
        const quick = await Promise.race([request, new Promise<"pending">((r) => setTimeout(() => r("pending"), LOCATING_SIGNAL_MS)), stopSignal]);
        if (quick === "pending") {
          emit({ type: "location", answerId, status: "locating" });
          reasonCodes.push("location:locating");
        }
        const loc = quick === "pending" ? await Promise.race([request, stopSignal]) : quick;
        if (loc === "stopped") {
          reasonCodes.push("location:stopped");
          const r = receipt();
          emit({ type: "done", answerId, tier: "instant", outcome: "stopped", receipt: r });
          return { answerId, tier: "instant", outcome: "stopped", text: "", sources: [], receipt: r };
        }
        if ("error" in loc) {
          reasonCodes.push(`location:${loc.error}`);
          if (loc.error === "stale") {
            // Never list the old area by itself (a traveler would get the city they left): offer it.
            emit({ type: "location", answerId, status: "stale", ageS: loc.last.ageS });
            const city = await geo.nearestCity?.(loc.last).catch(() => null);
            const area: Extract<AnswerEvent, { type: "places" }>["area"] = { kind: "near", ...(city ? { lastKnown: { city: city.name, country: city.country, ageS: loc.last.ageS } } : {}) };
            return finishPlaces("needs_place", staleLocationAnswer(intent, city?.name ?? null, loc.last.ageS), area);
          }
          const status = loc.error === "timeout" ? "unavailable" : loc.error;
          emit({ type: "location", answerId, status });
          return finishPlaces("needs_place", needsPlaceAnswer(intent), { kind: "near" });
        }
        emit({ type: "location", answerId, status: "granted", accuracyM: loc.accuracyM, ageS: loc.ageS });
        center = { lat: loc.lat, lon: loc.lon };
        area = { kind: "near", label: pt ? "perto de você" : "near you", origin: { lat: loc.lat, lon: loc.lon, accuracyM: loc.accuracyM, ageS: loc.ageS } };
      }

      const rs = deps.now();
      const found = await geo
        .searchPois({ center, diet: intent.diet.length ? intent.diet : undefined, text: intent.text, limit: PLACES_LIMIT })
        .catch((e) => {
          console.warn("[answer] POI search failed:", e?.message ?? e);
          return null;
        });
      const retrievalMs = deps.now() - rs;
      if (!found || found.coverage === "none" || found.pois.length === 0) {
        reasonCodes.push(found ? `places:coverage-${found.coverage}` : "places:search-failed");
        // Near the device the area is "near you", never the pack or tile id ("t-N41E012").
        const city = area.kind === "city" ? area.label! : null;
        if (found && found.coverage !== "none") {
          // A map covers the area but nothing matches the filters: the data exists, so not "no data" (T2-6).
          reasonCodes.push("places:no-match");
          return finishPlaces("none", noMatchAnswer(intent, city, found.radiusUsedM), { ...area, radiusM: found.radiusUsedM }, retrievalMs, "no_match");
        }
        return finishPlaces("none", noDataAnswer(intent, city), area, retrievalMs, found ? "no_data" : undefined);
      }

      const byDistance = intent.near.kind === "device";
      area.radiusM = found.radiusUsedM;
      const pois = dedupPlaces(found.pois);
      if (pois.length < found.pois.length) reasonCodes.push(`places:dedup-${found.pois.length - pois.length}`);
      if (deviceNow) {
        const here = deviceNow.value;
        if (here && !("error" in here) && distanceMeters(here, center) <= DEVICE_INSIDE_RADIUS_M) {
          area.deviceInside = true;
          reasonCodes.push("places:device-inside");
        }
      }
      // Without a distance (city named), a place is only findable by its
      // address: exact places with an address come first, ranking kept
      // within each group; approximate guide listings stay last.
      const ranked = byDistance
        ? pois
        : [
            ...pois.filter((p) => !p.approx && p.dietFlag !== "verify" && p.address),
            ...pois.filter((p) => !p.approx && p.dietFlag !== "verify" && !p.address),
            // Doubtful diet tags stay after every trustworthy place, as the pack ranked them.
            ...pois.filter((p) => !p.approx && p.dietFlag === "verify"),
            ...pois.filter((p) => p.approx),
          ];
      const sources = ranked.map(toSourceChunk);
      const places = ranked.map((p, i) => toPlace(p, i, byDistance));
      const attribution: { source: "osm" | "wikivoyage"; date?: string; license: string }[] = [];
      if (ranked.some((p) => p.source.kind === "osm")) {
        attribution.push({ source: "osm", date: ranked.find((p) => p.osmDate)?.osmDate, license: "ODbL" });
      }
      if (ranked.some((p) => p.source.kind === "wikivoyage")) attribution.push({ source: "wikivoyage", license: "CC BY-SA" });

      markVisible();
      emit({ type: "sources", answerId, tier: "instant", sources });
      emit({
        type: "places",
        answerId,
        tier: "instant",
        places,
        area,
        filters,
        criterion,
        coverage: "ok",
        truncated: found.pois.length >= PLACES_LIMIT,
        attribution,
      });
      reasonCodes.push(`places:${places.length}`, `places:coverage-${found.coverage}`, ...(found.region ? [`places:region-${found.region}`] : []), ...(byDistance && found.nearPlace ? ["places:near-city"] : []));
      const text = formatPlacesAnswer({
        intent,
        places,
        areaLabel: area.label ?? "",
        byDistance,
        radiusM: found.radiusUsedM,
        osmDate: attribution.find((a) => a.source === "osm")?.date,
        nearCity: byDistance ? found.nearPlace ?? null : null,
      });
      const r = receipt(retrievalMs);
      emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r });
      return { answerId, tier: "instant", outcome: "success", text, sources, receipt: r };
    }

    async function run(): Promise<AnswerResult> {
      const t0 = deps.now();
      let firstVisibleAt: number | null = null;
      const markVisible = () => {
        if (firstVisibleAt === null) firstVisibleAt = deps.now();
      };
      const stage = (name: AnswerStageName, tier: AnswerTier, modelId?: string, detail?: StageDetail) =>
        emit({ type: "stage", answerId, stage: name, tier, modelId, detail, at: deps.now() });

      // Sonar exp-30: the learned emergency detector runs first, so an emergency never takes the small-talk or
      // calculator shortcut ("help", "e agora?"). Unavailable or failing -> null -> the lexical predicate.
      const userTurns = (ctx.history?.turns ?? []).filter((t) => t.role === "user").map((t) => t.text);
      let learned = deps.emergencyScore && !req.reuseSources ? await deps.emergencyScore(req.query, userTurns).catch(() => null) : null;
      // NUMGUARD: the window over earlier turns fired -> the conversation is the emergency, never overridden.
      const windowFire = userTurns.length > 0 && !!learned?.fire;
      // DETMAX: earlier user turns dilute the detector window ("Hi, quick question." + a dog bite: 0.554 -> 0.049). With history, the current turn
      // is also scored alone and the larger score wins (fire if either window fires). Same threshold and model; no history -> no extra call.
      if (learned && userTurns.length && deps.emergencyScore) {
        const alone = await deps.emergencyScore(req.query, []).catch(() => null);
        if (alone) learned = { p: Math.max(learned.p, alone.p), fire: learned.fire || alone.fire };
      }
      const detectorCodes: string[] = learned ? [`detector:p=${learned.p.toFixed(3)}`, ...(learned.fire ? ["detector:fire"] : [])] : [];
      // NUMGUARD: a pure conversion or clock calculation (EN, no health or person word in this or any earlier user turn, the history
      // window did not fire) may take a deterministic calculator answer when the detector fires: the calculator shortcuts below see
      // "not fired" (small talk and FACTS stay off), and if none answers the detector's call is restored before anything else runs.
      const numguard = deps.numguard === true && !!learned?.fire && !windowFire && numericGuard(req.query, isPortugueseQuestion(req.query), userTurns);
      if (numguard && learned) {
        learned = { p: learned.p, fire: false };
        detectorCodes.push("numguard:fired");
      }

      // HEALTHGAP (deps.healthgap): a dose, poisoning, carbon-monoxide-reading or survival-time question the detector scores low
      // counts as a detector fire from here on (no small talk, calculator, FACTS or places; the health route). The detector's
      // own codes stay as scored; healthgap:<class> is added after them.
      const healthGap = deps.healthgap === true && !req.reuseSources ? classifyHealthGap(req.query, userTurns) : null;
      if (healthGap) {
        learned = { p: learned?.p ?? 0, fire: true };
        detectorCodes.push(...healthGapCodes(healthGap));
      }
      // HEALTHGAP (rule v1.2 §2.5): a dose or survival turn keeps a source whose title names its medicine or condition.
      const gapTitle = healthGapTitle(healthGap, req.query, userTurns);
      const gapSource = (c: RetrievedChunk) => !!gapTitle && gapTitle.test(c.title);

      // Arithmetic (temperature, fuel economy, Naismith, currency at a given rate, battery Wh) is answered
      // exactly, before anything else: "Minha conta do jantar deu 2.450 baht…" is a sum, not a restaurant
      // search (Sextant 3ccf7c0, mth-003-pt went to task:places).
      if (!req.reuseSources && req.tier !== "deep") {
        // Snakebite first aid: a fixed card from the WHO text, never a pack passage (Boar, after 3ffd7e0).
        if (isSnakebiteFirstAid(req.query)) {
          // Spanish first: isPortugueseQuestion also fires on Spanish ("Me picó una culebra, que hago?"), and ES_ONLY
          // holds no words Portuguese shares, so a Portuguese question is not Spanish here.
          const esQ = isSpanishQuestion(req.query);
          const card = snakebiteFirstAidCard(req.query, !esQ && isPortugueseQuestion(req.query), esQ);
          markVisible();
          const r: AnswerReceipt = { modelId: "grounding-guard", modelLabel: "First aid (WHO)", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: ["answer:first-aid-card-snakebite"] };
          emit({ type: "token", answerId, tier: "instant", text: card });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: card, sources: [], receipt: r, cited: [] };
        }
        // CO hazard (EN): a fuel-burning device in a garage, tent or home is never a model answer (ship L3: "yes, with ventilation").
        if (!isPortugueseQuestion(req.query) && !isSpanishQuestion(req.query) && isCoHazardQuestion(req.query)) {
          const text = coHazardAnswer(req.query);
          markVisible();
          const r: AnswerReceipt = { modelId: "grounding-guard", modelLabel: "Safety facts (CDC)", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: [...detectorCodes, "answer:safety-fact-co-hazard"] };
          emit({ type: "token", answerId, tier: "instant", text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text, sources: [], receipt: r, cited: [] };
        }
        // A country's emergency numbers: a fixed table, never a guess (Sextant trv-001-pt).
        const numbers = emergencyNumbersAnswer(req.query, isPortugueseQuestion(req.query));
        if (numbers) {
          markVisible();
          const r: AnswerReceipt = { modelId: "grounding-guard", modelLabel: "Offline library", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: ["answer:emergency-numbers"] };
          emit({ type: "token", answerId, tier: "instant", text: numbers });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: numbers, sources: [], receipt: r, cited: [] };
        }
        // Small talk ("hi", "valeu", "who are you", "what time is it"): a fixed reply, no model load, no search.
        const chat = learned?.fire || numguard ? null : smallTalkReply(req.query, isPortugueseQuestion(req.query), new Date());
        if (chat) {
          markVisible();
          const r: AnswerReceipt = { modelId: "small-talk", modelLabel: "BOAR", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: [`answer:small-talk-${chat.kind}`] };
          emit({ type: "token", answerId, tier: "instant", text: chat.text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: chat.text, sources: [], receipt: r, cited: [] };
        }
        const calc = learned?.fire ? null : calculate(req.query, isPortugueseQuestion(req.query));
        if (calc) {
          markVisible();
          const codes = [...(numguard ? detectorCodes : []), `answer:${calc.kind === "temperature" ? "temperature-conversion" : `calculator-${calc.kind}`}`];
          const r: AnswerReceipt = { modelId: "calculator", modelLabel: "Calculator", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: codes };
          emit({ type: "token", answerId, tier: "instant", text: calc.text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: calc.text, sources: [], receipt: r, cited: [] };
        }
        // AR (cut 3 dev, coordinator 4B-LOSS-ANATOMY arm 3): rate x quantity -> time and ceil-division word problems,
        // exact, from the question's own numbers (EN). It also runs when the learned detector fired, because the message
        // has no health, injury or danger word (the numeric guard: vit-sci-003 "10 tokens per second… how long?" got
        // "I won't give health advice from memory" on every cut-2 seed).
        const arith = isPortugueseQuestion(req.query) || isSpanishQuestion(req.query) ? null : arithmeticAnswer(req.query);
        if (arith) {
          markVisible();
          const codes = [...detectorCodes, ...(learned?.fire || numguard ? ["detector:numeric-guard"] : []), `answer:calculator-${arith.kind}`];
          const r: AnswerReceipt = { modelId: "calculator", modelLabel: "Calculator", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: codes };
          emit({ type: "token", answerId, tier: "instant", text: arith.text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: arith.text, sources: [], receipt: r, cited: [] };
        }
        // Travel distance between two cities (EN): an estimate from the bundled city table, never a mapped route; a place
        // not in the table -> null -> the normal path (Anvil, V-exact Paris–Rome).
        const travel = learned?.fire || isPortugueseQuestion(req.query) || isSpanishQuestion(req.query) ? null : travelDistanceAnswer(req.query);
        if (travel) {
          markVisible();
          const r: AnswerReceipt = { modelId: "calculator", modelLabel: "Calculator", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: [...(numguard ? detectorCodes : []), "answer:calculator-travel-distance"] };
          emit({ type: "token", answerId, tier: "instant", text: travel.text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: travel.text, sources: [], receipt: r, cited: [] };
        }
        // FACTS (coordinator arm, criterion arms/facts 4328960e): a fact from the offline Wikidata table when the question names a
        // table entity and asks exactly one property (population, area, capital, height, length, depth, dates, plugs/voltage,
        // host city, award winner); comparisons get a computed verdict. Never on a health or emergency message (EN only).
        const fact =
          deps.facts === false || learned?.fire || numguard || isPortugueseQuestion(req.query) || isSpanishQuestion(req.query) || isHealthQuestion(req.query) || isCardEmergency(req.query)
            ? null
            : factsAnswer(req.query);
        if (fact) {
          markVisible();
          const r: AnswerReceipt = { modelId: "grounding-guard", modelLabel: "Offline facts (Wikidata)", tokens: 0, tokPerSec: 0, ttftMs: deps.now() - t0, totalMs: deps.now() - t0, reasonCodes: [`answer:facts-${fact.template}`] };
          emit({ type: "token", answerId, tier: "instant", text: fact.text });
          emit({ type: "done", answerId, tier: "instant", outcome: "success", receipt: r, cited: [] });
          return { answerId, tier: "instant", outcome: "success", text: fact.text, sources: [], receipt: r, cited: [] };
        }
      }

      // NUMGUARD: no calculator answered the guarded turn -> the detector's call stands; a model never answers a fired message.
      if (numguard && learned) {
        learned = { p: learned.p, fire: true };
        detectorCodes.splice(detectorCodes.indexOf("numguard:fired"), 1, "numguard:no-calculator");
      }

      // Places questions never touch a model: the answer is built from the
      // POI records alone, so no name can be invented and it lands in <1s.
      const geoIntent = req.place
        ? { ...(detectGeoIntent(req.query) ?? { diet: [], wantsBest: false, lang: "en" as const }), near: { kind: "place" as const, name: req.place } }
        : req.tier === "deep" || req.reuseSources
          ? null
          : detectGeoIntent(req.query);
      // health13 (blind hand read): "choking at a restaurant" got "the places pack is not installed". The places pack is
      // food/venue search only, so a health message never takes this path.
      const healthNow = learned ? learned.fire || (learned.p >= DETECTOR_FLOOR && isHealthQuestion(req.query)) : isHealthQuestion(req.query);
      if (geoIntent && !healthNow && !isCardEmergency(req.query)) return runGeo(geoIntent as GeoIntent, t0, markVisible, () => firstVisibleAt);
      if (geoIntent) detectorCodes.push("places:skipped-health");

      const { settings, installed, speeds, fastLlm, lowRamNote, lowRamBlocked } = await selectAnswerModel(deps);
      const byId = new Map(installed.map((m) => [m.id, m]));
      const toDepth = (m: InstalledLlm, fit?: MemoryFit | null): DepthModel => ({
        id: m.id,
        label: m.label,
        sizeBytes: m.sizeBytes,
        roles: m.roles,
        fit: fit?.verdict,
        tokPerSec: speeds.get(m.id),
      });

      // Only models that could be the deep tier are worth a header read.
      const deepCandidates = installed.filter(
        (m) => m.id !== fastLlm?.id && (m.id === settings.deepModelId || m.roles.includes("reasoning") || m.roles.includes("verifier"))
      );
      const fits = new Map<string, MemoryFit | null>();
      await Promise.all(
        deepCandidates.map(async (m) => fits.set(m.id, await deps.engine.estimateFit(m.filename).catch(() => null)))
      );
      const depthModels = deepCandidates.map((m) => toDepth(m, fits.get(m.id)));
      const deepModel = resolveDeepModel(depthModels, fastLlm?.id, settings.deepModelId);
      // Route gate (4B only): the loaded model decides whether to search; the rules stay the fallback.
      const ruleTaskType = classifyTask(req.query);
      const gateCodes: string[] = [];
      let gateLoadMs = 0;
      let taskType = ruleTaskType;
      let gateSkip = false;
      if (
        fastLlm &&
        deps.engine.nextTokenProbs &&
        !ROUTE_GATE_CONFIG.off &&
        (ROUTE_GATE_CONFIG.anyModel || ROUTE_GATE_MODEL_IDS.has(fastLlm.id)) &&
        (req.tier ?? "auto") === "auto" &&
        !req.reuseSources &&
        !req.answerAnyway &&
        // CUT5-SPEED: the rules decide (lookup/research/compare, no detector fire, no history, not about BOAR): no gate pass.
        !(gateSkip = routeGateSkip({
          ruleTaskType,
          query: req.query,
          historyTurns: ctx.history?.turns?.length ?? 0,
          detectorFired: !!learned?.fire,
          emergencyLexical: isCardEmergency(req.query) || isSafetyQuery(req.query),
        }))
      ) {
        // Cold start (the first message after launch): the gate runs only on the resident model, so load the fast model
        // now. The answer below would load the same file anyway (ensureLoaded then finds it resident); a failed load
        // leaves the rules, as before. The time is counted in the answer's loadMs.
        if (deps.engine.getModelInfo()?.filename !== fastLlm.filename) {
          stage("loading_model", "fast", fastLlm.id);
          const ls = deps.now();
          try {
            const r = await deps.engine.load(fastLlm.filename, { meta: { modelId: fastLlm.id, label: fastLlm.label } });
            gateCodes.push("route-gate:cold-load");
            // As ensureLoaded: the answer below finds the model resident, so this load's result is the only report.
            if (r?.warning) emit({ type: "warning", answerId, code: "model_streams_from_storage", message: r.warning });
            if (r?.backend?.kind === "cpu-fallback") gateCodes.push("backend:cpu-fallback");
          } catch (e: any) {
            console.warn("[answer] route gate cold load failed:", e?.message ?? e);
          }
          gateLoadMs = deps.now() - ls;
        }
        const probs = await deps.engine
          .nextTokenProbs(routeGateMessages(req.query, ctx.history), { modelFilename: fastLlm.filename, nProbs: ROUTE_GATE_N_PROBS })
          .catch((e) => {
            console.warn("[answer] route gate failed:", e?.message ?? e);
            return null;
          });
        if (!probs) gateCodes.push("route-gate:unavailable");
        else {
          const v = routeGateVerdict(probs);
          // A health question always searches: the gate may only skip a search where a wrong skip is harmless.
          const decision = v.decision === "answer" && isHealthQuestion(req.query) ? "search" : v.decision;
          taskType = gatedTaskType(ruleTaskType, decision);
          gateCodes.push(`route-gate:${decision}`, `route-gate:score-${v.score === null ? "none" : v.score.toFixed(3)}`);
          if (decision !== v.decision) gateCodes.push("route-gate:health-guard");
          if (taskType !== ruleTaskType) gateCodes.push(`route-gate:${ruleTaskType}->${taskType}`);
        }
      } else if (gateSkip) gateCodes.push("route-gate:skip-rules");
      const plan: AnswerPlan = planAnswer({
        taskType,
        requestedTier: req.tier ?? "auto",
        // "Answer anyway" asks for the model's answer: the excerpt is already on screen, and offering it again
        // (instant:final on a lookup) ended the redo on the same excerpt with no generation.
        quickFirst: settings.quickFirst && !req.answerAnyway,
        alwaysComplete: settings.alwaysComplete,
        fastModel: fastLlm ? toDepth(fastLlm) : null,
        deepModel,
        // Verification is automatic too, so the same speed rule (D6) applies: a 200-token verdict
        // at 2.7 tok/s is over a minute on top of the answer.
        verifiers: depthModels.filter((m) => m.roles.includes("verifier") && deepAutoIneligibility(m) === null),
        hasReusedSources: !!req.reuseSources?.length,
      });
      const reasonCodes = [`task:${taskType}`, ...gateCodes, ...plan.reasonCodes, ...(lowRamNote ? [lowRamNote] : []), ...detectorCodes];
      if (!deepModel && !settings.deepModelId) {
        for (const m of depthModels.filter((d) => d.roles.includes("reasoning"))) {
          const why = deepAutoIneligibility(m);
          if (why) reasonCodes.push(`deep-auto:skip-${m.id}-${why}${m.tokPerSec !== undefined ? `-${m.tokPerSec.toFixed(1)}tps` : ""}`);
        }
      }
      // Health never goes through multi-pass Deep Research: it would answer from memory past the grounding guard.
      const healthFollowUp = isHealthFollowUp(req.query, userTurns);
      // health11: the learned detector replaces the broad regex (15/57 hard-negative fires on Sonar's dev). The broad
      // regex still counts with weak detector evidence (DETECTOR_FLOOR), and the precise lexical parts stay: card-condition
      // triggers and the health follow-up.
      const lexicalHealth = learned ? learned.fire || (learned.p >= DETECTOR_FLOOR && isHealthQuestion(req.query)) : isHealthQuestion(req.query);
      const cardEmergency = !lexicalHealth && !healthFollowUp && isCardEmergency(req.query);
      const health = lexicalHealth || healthFollowUp || cardEmergency;
      if (cardEmergency) reasonCodes.push("grounding:health-card-condition");
      if (healthFollowUp) reasonCodes.push("grounding:health-follow-up");
      const gen =
        health && plan.generation?.mode === "multipass"
          ? { ...plan.generation, mode: "single" as const, retrieveK: Math.max(plan.generation.retrieveK, 6) }
          : plan.generation;
      if (gen !== plan.generation) reasonCodes.push("grounding:health-no-multipass");
      const genTier: AnswerTier = gen?.tier ?? "fast";
      const genLlm = gen ? byId.get(gen.modelId) : undefined;

      /** Set once the model is asked to generate: only then does the answer leave an execution record. */
      let generation: { modelId: string; residency: ModelResidency; firstTokenAt: number | null; decodeMs?: number; decodeTokens?: number } | null = null;
      /** Highest memoryBytes() seen from the model load to the end of the answer (0 = no readout). */
      let peakMemoryBytes = 0;
      const sampleMemory = () => {
        try {
          peakMemoryBytes = Math.max(peakMemoryBytes, deps.memoryBytes?.() || 0);
        } catch {
          // No readout on this build: the record keeps a null peak.
        }
      };
      const record = (outcome: AnswerOutcome, r: AnswerReceipt, error?: { message: string }) => {
        if (!generation || !deps.recordExecution) return;
        sampleMemory();
        const execOutcome: ExecutionOutcome = outcome === "success" ? "success" : outcome === "stopped" ? "cancelled" : "failure";
        deps
          .recordExecution({
            modelId: generation.modelId,
            taskType,
            adaptiveRoutingUsed: true,
            reasonCodes: [...r.reasonCodes],
            retrievalUsed: plan.retrieve,
            modelSwitches: generation.residency === "resident" ? 0 : 1,
            crossMessageModelSwitch: generation.residency === "switched",
            modelResidency: generation.residency,
            modelLoadMs: r.loadMs,
            ttftMs: generation.firstTokenAt !== null ? generation.firstTokenAt - t0 : undefined,
            generationLatencyMs: generation.decodeMs,
            totalLatencyMs: deps.now() - t0,
            tokensGenerated: generation.decodeTokens ?? r.tokens,
            tokPerSec: r.tokPerSec,
            outcome: execOutcome,
            ...(peakMemoryBytes > 0 ? { peakRssBytes: peakMemoryBytes } : {}),
            ...(outcome === "timeout" ? { errorMessage: "timeout" } : error ? { errorMessage: error.message } : {}),
          })
          .catch(() => {});
      };
      const receipt = (over: Partial<AnswerReceipt> = {}): AnswerReceipt => ({
        modelId: genLlm?.id ?? "none",
        modelLabel: genLlm?.label ?? "",
        tokens: 0,
        tokPerSec: 0,
        ttftMs: (firstVisibleAt ?? deps.now()) - t0,
        totalMs: deps.now() - t0,
        reasonCodes,
        ...over,
      });
      /** Set when the text differs from the streamed tokens (citations removed, CT-1). */
      let finalText: string | undefined;
      /** The instant answer is a source's own sentence without "[n]": it cites that source. */
      let citedOverride: number[] | undefined;
      const finish = (
        tier: AnswerTier,
        outcome: AnswerOutcome,
        text: string,
        sources: RetrievedChunk[],
        r: AnswerReceipt,
        error?: { code: AnswerErrorCode; message: string }
      ): AnswerResult => {
        // CT-2: which sources the final text really cites (after the CT-1 check); none -> the chat
        // shows no "Sources" card for this answer.
        const cited =
          citedOverride ?? [...new Set([...text.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))].filter((n) => n >= 1 && n <= sources.length);
        // One rule for the chat (Quill/Prism CT-5): a model's answer that cites nothing gets the
        // weak_sources warning (the "general knowledge" seal), on every path: deep, multipass, PT,
        // answer-anyway, and the 4B with an on-topic source that it didn't cite.
        if (!weakSourcesSent && outcome === "success" && text.trim() && cited.length === 0 && !NOT_A_MODEL.has(r.modelId)) {
          reasonCodes.push("grounding:uncited-warning");
          emit({ type: "warning", answerId, code: "weak_sources", message: "No offline source backs this answer." });
        }
        emit({ type: "done", answerId, tier, outcome, receipt: r, error, cited, ...(finalText !== undefined ? { finalText } : {}) });
        if (DEV_LOG) console.log(`[ANSWER] done ${JSON.stringify({ q: req.query.slice(0, 80), tier, outcome, codes: reasonCodes, text: text.slice(0, 200) })}`);
        record(outcome, r, error);
        return { answerId, tier, outcome, text, sources, receipt: r, cited };
      };

      // CT-3 (Prism/Piston, 34efdf8): "Who won the football match yesterday?" -> "Meath won... [1]"
      // with [1] a 2021 final. A current-events question is about what an offline snapshot can't
      // know: a fixed, honest answer, no model, no sources.
      if (isCurrentEventQuery(req.query)) {
        reasonCodes.push("grounding:current-event");
        const text = currentEventAnswer(isPortugueseQuestion(req.query));
        markVisible();
        emit({ type: "token", answerId, tier: "instant", text });
        return finish("instant", "success", text, [], receipt({ modelId: "grounding-guard", modelLabel: "Offline library" }));
      }

      // 1. Sources. A Portuguese question searches the (English) packs with English words when it has known terms.
      const pt = isPortugueseQuestion(req.query);
      // "Today in history": a question about the device's date ("September 27"); a source must name it (Boar R3).
      const history = isTodayInHistory(req.query) ? historyDate(deps.today?.() ?? new Date()) : null;
      if (history) reasonCodes.push("grounding:today-in-history");
      const english = history
        ? history.search
        : pt
          ? englishSearchTerms(req.query)
          : (learned ? health : isHealthQuestion(req.query)) // without the detector: unchanged
            ? canonicalHealthTerms(req.query)
            : null;
      if (english && !history) reasonCodes.push("retrieve:pt-en-terms");
      // Any other PT question: the English names it mentions, from the lexicon (PT-1, "estacoes do ano" -> Season).
      const names = pt && !english ? (deps.englishNames ?? defaultEnglishNames)(req.query) : [];
      if (names.length) reasonCodes.push("match:pt-en-names");
      /** What the sources are matched against: the English words for a translated PT question. */
      // Identifiers the question names ("EIP-7251") stay in what sources are matched against (Sextant dddd8a8:
      // with names ["Ethereum"] only, the EIP page lost to the Ethereum articles).
      const ids = identifiersIn(req.query);
      // And the acronyms it writes as they are ("ML-KEM", "SLH-DSA", "MEV"): with names ["Algorithm"] alone the
      // ML-KEM page lost to "Jump flooding algorithm" in compression (Sextant cry-018-pt, gate bc7db6d).
      const acronyms = [...new Set(req.query.match(/\b[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+\b|\b[A-Z]{2,}[0-9]*\b/g) ?? [])];
      const matchQuery = english ?? (names.length ? [...names, ...ids, ...acronyms].join(" ") : req.query);
      // Lexicon names are article titles: a source must be titled by one ("Season", not "Hurricane Season ...").
      // Sonar exp-30: a translated PT question's English terms can be crypto words too ("queimadura… crianças" ->
      // "burn child" matched ERC-7401/7891, which burn child tokens). Crypto-pack sources need a crypto cue in the question.
      const cryptoBlocked = (c: RetrievedChunk) => !!english && !history && CRYPTO_SOURCE.test(c.source ?? "") && !CRYPTO_CUE.test(req.query);
      const onSubject = (c: RetrievedChunk) =>
        cryptoBlocked(c) ? false : history
          ? history.isDateArticle(c.title)
          : names.length
            ? // Or the question's own words: identifiers and English terms a PT question uses as written
              // ("EIP-1559", "proof of stake"); with weak names (["Ethereum"]) the EIP sources were dropped.
              namedByLexicon(names, c) || onTopic(req.query, c)
            : onTopic(matchQuery, c);
      // The packs lift an article's Treatment/Management section only when the query asks what to
      // do (Bramble b03c959); the article name alone ("snakebite") brought back "Signs and symptoms".
      // Lexicon names aren't searched here: retrieve() already adds them to a PT question's search.
      const searchQuery = english ? (ACTION_INTENT.test(req.query) ? `${english} what to do` : english) : req.query;
      let raw: RetrievedChunk[] = req.reuseSources ?? [];
      /** Multi-pass only: the cited sources as retrieved (ResearchResult.fullCitations), for the citation checks. */
      let fullCited: RetrievedChunk[] = [];
      let retrievalMs: number | undefined;
      if (plan.retrieve && gen?.mode !== "multipass") {
        stage("retrieving", plan.instant !== "off" ? "instant" : genTier);
        const rs = deps.now();
        // First boot (Harbor, 5f7d9ca): asked during indexing, the search found an empty index
        // (15/300) and the answer was "I couldn't find this". Wait for the index; stop() ends the wait.
        if (deps.knowledgeReady) {
          const indexed = deps.knowledgeReady().catch(() => {});
          if ((await Promise.race([indexed, stopSignal])) === "stopped") return finish(genTier, "stopped", "", [], receipt({}));
        }
        raw = await deps.retrieve(searchQuery, gen?.retrieveK ?? 6).catch((e) => {
          console.warn("[answer] retrieval failed, answering without sources:", e?.message ?? e);
          return [] as RetrievedChunk[];
        });
        retrievalMs = deps.now() - rs;
      }
      if (stopRequested) return finish(genTier, "stopped", "", [], receipt({ retrievalMs }));

      // Never let sources + answer overflow the context window (2048 on 4GB phones).
      const ctxSize = deps.contextSize?.() ?? 4096;
      const budget = Math.max(
        256,
        Math.min(
          gen?.contextTokens ?? 1200,
          ctxSize -
            Math.min(ctx.maxTokens, gen?.maxTokens ?? Infinity) -
            PROMPT_OVERHEAD_TOKENS -
            (gen?.thinking === false ? 0 : genTier === "deep" ? DEEP_THINKING_BUDGET : FAST_THINKING_BUDGET)
        )
      );
      // Passages without content (an EIP's metadata header, a hex example, a copyright notice) never reach
      // the prompt (Sextant cry-012), and so are never pinned.
      const substantive = raw.filter(isSubstantive);
      if (substantive.length < raw.length) reasonCodes.push(`context:no-content-dropped-${raw.length - substantive.length}`);
      raw = substantive;
      // A retrieved page of an identifier the question names always gets a place, first (Boar, dddd8a8).
      const pinned = new Set(ids.length ? raw.filter((c) => titleHasIdentifier(c.title, ids)).map((c) => c.chunkId) : []);
      if (pinned.size) reasonCodes.push(`context:pinned-${pinned.size}`);
      const compressed = compressContext(matchQuery, raw, { tokenBudget: budget, pinned });
      let sources = compressed.chunks;
      reasonCodes.push(`context:${compressed.tokensBefore}->${compressed.tokensAfter}`);

      // Grounding: sources must be on topic in absolute terms, not just the best of what came back.
      // Always on (Quill, 014c054: the chat reads "passages found, none cited" from a non-empty sources
      // event, so no off-topic passage may reach it). A PT question without English words is matched
      // by its own words: English sources rarely name them, so its noise ("Estrela, Lisbon") is dropped.
      const guarded = gen?.mode !== "multipass";
      // Health has its own, stricter topic filter below (the condition, lay sources allowed).
      if (guarded && !health && sources.length) {
        const kept = sources.filter((c) => onSubject(c));
        if (kept.length < sources.length) reasonCodes.push(`grounding:off-topic-dropped-${sources.length - kept.length}`);
        if (!kept.length) reasonCodes.push("grounding:no-good-source");
        sources = kept;
      }
      if (health) {
        reasonCodes.push("grounding:health-strict");
        // Health: only sources whose title names the health topic (never a plant, a cave or a TV show).
        const topic = healthTopicTerms(req.query, english);
        const onTopicHealth = sources.filter((c) => onHealthTopic(topic, c) || gapSource(c));
        if (onTopicHealth.length < sources.length) reasonCodes.push(`grounding:health-off-topic-dropped-${sources.length - onTopicHealth.length}`);
        sources = onTopicHealth;
        // The best source to quote may have been cut by compression (relative relevance): a
        // first-aid book's section loses to the clinical article's wording. Bring it back.
        const pool = raw.filter((c) => onHealthTopic(topic, c) || gapSource(c));
        if (pool.length) {
          const best = pool[healthSourceIndex(pool, coreProcedure(topic))];
          if (!sources.some((c) => c.chunkId === best.chunkId)) {
            sources = [...sources, best];
            reasonCodes.push("grounding:health-best-restored");
          }
        }
      }
      if (sources.length && gen?.mode !== "multipass") {
        emit({ type: "sources", answerId, tier: plan.instant !== "off" ? "instant" : genTier, sources });
      }

      // 2. Instant snippet (no LLM): only a sentence that covers the question. Not for an extractive
      // health answer: its snippet is the excerpt below (EQ-2: the preview quoted "Do not run during
      // the quake!" by word overlap, before the Drop, Cover and Hold On excerpt).
      // HEALTHGAP (rule v1.2 §2.6): a survival-time question with a kept source titled by its condition ("Cold weather",
      // "Hypothermia") is answered by the model from that source under the health grounding, as the deep tier answers health.
      const survivalSourced = healthGap?.cls === "survival" && sources.some(gapSource);
      if (survivalSourced) reasonCodes.push("grounding:health-survival-sourced");
      const healthExtractive = health && sources.length > 0 && req.tier !== "deep" && !survivalSourced;
      // Never for health/danger (a single sentence is never a first-aid answer, and with no source the guard below
      // answers), and never when the sources were matched against a translation instead of the question (a PT
      // question's pt-en terms or lexicon names): coverage of "child" or "India" alone let an off-topic pack line
      // ("Reject child token 2.", "Atheism is not taboo in India") stand as the final answer (Sonar exp-29).
      const translated = matchQuery !== req.query;
      // A PT/ES definition question matched through lexicon names may still get its snippet, if the sentence defines the name.
      const definitional = translated && !health && names.length > 0 && isPtDefinitional(req.query);
      if (plan.instant !== "off" && raw.length && (health || (translated && !definitional))) reasonCodes.push(health ? "instant:off-health" : "instant:off-translated");
      if (plan.instant !== "off" && raw.length && !health && (!translated || definitional)) {
        // Lexicon names: the sentence comes from a source titled by one (a one-word name is in many titles).
        const pool = names.length ? raw.filter(onSubject) : raw;
        const snip = pool.length ? selectInstant(matchQuery, pool) : null;
        const sourceIndex = snip ? sources.findIndex((c) => c.chunkId === pool[snip.sourceIndex].chunkId) : -1;
        // "From the source" only from an on-topic source (the same onTopic as the sources), and a sentence that covers the question.
        const covers =
          !!snip &&
          onSubject(pool[snip.sourceIndex]) &&
          termCoverage(matchQuery, `${pool[snip.sourceIndex].title} ${snip.text}`) >= MIN_TERM_COVERAGE &&
          // The sentence itself names the subject, not only its page's title (Sextant q7, EN and PT).
          sentenceNamesSubject(matchQuery, pool[snip.sourceIndex].title, snip.text) &&
          (!definitional || definesName(names, snip.text));
        if (definitional) reasonCodes.push("instant:pt-definitional");
        if (snip && sourceIndex >= 0 && !covers) reasonCodes.push("instant:off-topic");
        if (snip && sourceIndex >= 0 && covers) {
          markVisible();
          // A source in another language than the question says so in the snippet itself (Quill, q8).
          const lead = sourceLanguageLead(pt, snip.text);
          const shown = lead ? `${lead}\n${snip.text}` : snip.text;
          if (lead) reasonCodes.push("instant:source-language-lead");
          emit({ type: "instant", answerId, snippet: { text: shown, sourceIndex }, confidence: snip.confidence });
          const block =
            plan.instant === "may-finish" && snip.confidence >= INSTANT_FINAL_CONFIDENCE ? instantFinalBlock(req.query, snip.text, matchQuery) : "low";
          if (block && block !== "low") reasonCodes.push(`instant:not-final-${block}`);
          if (plan.instant === "may-finish" && block === null) {
            reasonCodes.push("instant:final");
            citedOverride = [sourceIndex + 1];
            return finish(
              "instant",
              "success",
              shown,
              sources,
              receipt({ modelId: "extractive", modelLabel: "Source excerpt", retrievalMs })
            );
          }
        }
      }

      // No good source: never an answer from memory (health: point to emergency services).
      // Off-topic sources only = no source (Boar, RT-1): the model answers with the
      // "not from the offline library" instruction below, never quoting them.
      if (health && gen?.mode !== "multipass" && sources.length === 0) {
        reasonCodes.push("grounding:health-no-source");
        const { text, label } = noSourceHealthCard(req.query, pt, !pt && isSpanishQuestion(req.query), reasonCodes, (ctx.history?.turns ?? []).filter((t) => t.role === "user").map((t) => t.text), healthGap);
        markVisible();
        emit({ type: "token", answerId, tier: genTier, text });
        return finish(genTier, "success", text, [], receipt({ modelId: "grounding-guard", modelLabel: label, retrievalMs }));
      }
      // Health with a good source: the answer IS the source's text, not the model's
      // retelling (Sextant: the models misplaced the pinch point, iced snake bites,
      // put cream on burns). "Deeper answer" (tier deep) lets the model summarize it, strictly.
      // A coastal quake (tsunami card): the pack's earthquake excerpts say "take cover", the wrong action at the coast, so the
      // card goes first (Sonar exp-29, Compass round 2).
      if (healthExtractive && conversationCondition(req.query, userTurns).condition === "tsunami") {
        const card = conditionCardText("tsunami", req.query, !pt && isSpanishQuestion(req.query) ? "es" : pt ? "pt" : "en");
        if (card) {
          reasonCodes.push("grounding:fa-card-tsunami", "grounding:health-card-first");
          markVisible();
          emit({ type: "token", answerId, tier: genTier, text: card });
          return finish(genTier, "success", card, [], receipt({ modelId: "grounding-guard", modelLabel: "First aid card", retrievalMs }));
        }
      }
      // HEALTHGAP: a poisoning event (a deadly mushroom, or something swallowed or breathed in) gets its card before any excerpt:
      // the specific condition's card if there is one (button battery, chemical in the eye…), else the poisoning card.
      if (healthExtractive && healthGap?.event) {
        const { condition: named } = conversationCondition(req.query, userTurns);
        const condition = named && named !== "MULTI" ? named : "poisoning";
        const card = conditionCardText(condition, req.query, !pt && isSpanishQuestion(req.query) ? "es" : pt ? "pt" : "en");
        if (card) {
          reasonCodes.push(`grounding:fa-card-${condition}`, "grounding:health-card-first");
          markVisible();
          emit({ type: "token", answerId, tier: genTier, text: card });
          return finish(genTier, "success", card, [], receipt({ modelId: "grounding-guard", modelLabel: "First aid card", retrievalMs }));
        }
      }
      // HEALTHGAP: a dose question with a kept source gets the source's own amount, quoted and cited, or the dose line; never a
      // first-aid excerpt and never a model.
      if (healthExtractive && healthGap?.cls === "dose") {
        const es = !pt && isSpanishQuestion(req.query);
        const fullSources = sources.map((c) => raw.find((r) => r.chunkId === c.chunkId) ?? c);
        const quoted = doseExcerpt(fullSources, gapTitle, pt, es);
        reasonCodes.push(quoted ? "grounding:health-dose-excerpt" : "grounding:health-no-dose");
        const text = quoted ?? noDoseAnswer(pt, es);
        markVisible();
        emit({ type: "token", answerId, tier: "instant", text });
        return quoted
          ? finish("instant", "success", text, sources, receipt({ modelId: "extractive", modelLabel: "Source excerpt", retrievalMs }))
          : finish("instant", "success", text, [], receipt({ modelId: "grounding-guard", modelLabel: "No offline source", retrievalMs }));
      }
      if (healthExtractive) {
        reasonCodes.push("grounding:health-extractive");
        // Score on each source's full text: compression keeps the sentences matching the question
        // words, which can leave out a first-aid text's instructions.
        const fullSources = sources.map((c) => raw.find((r) => r.chunkId === c.chunkId) ?? c);
        const rules = excerptRules(req.query, healthTopicTerms(req.query, english));
        // The best source whose excerpt is safe: no disputed procedure, no dangerous instruction (Sextant
        // dng-001: "pressure immobilization … remains a point of controversy"). None: emergency + get care.
        let text = "";
        for (const k of healthSourceOrder(fullSources, rules.procedure ?? null)) {
          const first = healthExtract(fullSources[k], k + 1, pt, rules);
          if (!safeHealthExcerpt(first)) {
            reasonCodes.push(`grounding:health-unsafe-excerpt-${k + 1}`);
            continue;
          }
          // A question that also asks about afterwards gets that part too, or is told it isn't there (dng-004-pt).
          const withAfter = withSeekCare(withAfterPart(req.query, first, fullSources, k, pt, rules), fullSources, k, rules);
          text = safeHealthExcerpt(withAfter) ? withAfter : first;
          // A general passage for a specific situation says so (dng-005: a flood, and Wikivoyage's "Water › Buy").
          const note = situationNote(req.query, fullSources[k], pt);
          if (note) {
            // Before the source's label: the reader learns it's general advice before reading it (Boar).
            text = `${note}\n\n${text}`;
            reasonCodes.push("grounding:health-general-source");
          }
          break;
        }
        // No first-aid steps in the source: the condition's card when there is one (Sonar exp-29: a diabetic's low sugar).
        if (text && isNoStepsAnswer(text)) {
          const { condition: named } = conversationCondition(req.query, userTurns);
          const condition = named && named !== "MULTI" ? named : healthGapCard(healthGap); // HEALTHGAP: poisoning / CO card fallback
          const card = condition ? conditionCardText(condition, req.query, !pt && isSpanishQuestion(req.query) ? "es" : pt ? "pt" : "en") : null;
          if (card) {
            reasonCodes.push("grounding:health-no-steps", `grounding:fa-card-${condition}`);
            markVisible();
            emit({ type: "token", answerId, tier: "instant", text: card });
            return finish("instant", "success", card, [], receipt({ modelId: "grounding-guard", modelLabel: "First aid card", retrievalMs }));
          }
        }
        if (!text) {
          reasonCodes.push("grounding:health-no-safe-excerpt");
          // No safe excerpt: the condition's first-aid card when there is one (Sonar exp-29), else the fixed line.
          const { condition: named } = conversationCondition(req.query, userTurns);
          const condition = named && named !== "MULTI" ? named : healthGapCard(healthGap); // HEALTHGAP: poisoning / CO card fallback
          const card = condition ? conditionCardText(condition, req.query, !pt && isSpanishQuestion(req.query) ? "es" : pt ? "pt" : "en") : null;
          if (card) reasonCodes.push(`grounding:fa-card-${condition}`);
          const fixed = card ?? noSafeStepsAnswer(pt);
          markVisible();
          emit({ type: "token", answerId, tier: "instant", text: fixed });
          return finish("instant", "success", fixed, [], receipt({ modelId: "grounding-guard", modelLabel: "No safe source", retrievalMs }));
        }
        markVisible();
        // No separate instant event: the excerpt IS the answer (Quill/Prism DUP-1: the chat showed it twice).
        emit({ type: "token", answerId, tier: "instant", text });
        return finish("instant", "success", text, sources, receipt({ modelId: "extractive", modelLabel: "Source excerpt", retrievalMs }));
      }
      // A knowledge question with nothing retrieved: the model answers, but not as if it came from a source.
      const fromMemory =
        !health && plan.retrieve && gen?.mode !== "multipass" && sources.length === 0 && ["lookup", "research", "compare", "extract"].includes(taskType);
      // A phrase or translation question with no source: no model answers it from memory (trv-007).
      if (fromMemory && genLlm && !req.answerAnyway && isPhraseQuestion(req.query)) {
        reasonCodes.push("grounding:phrase-no-source-declined");
        markVisible();
        const message = pt ? "Não encontrei isso no acervo deste celular." : "I didn't find this in this phone's library.";
        emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
        finalText = message;
        return finish(genTier, "success", message, [], receipt({ retrievalMs }));
      }
      if (fromMemory && genLlm && isCompactModel(genLlm) && !req.answerAnyway) {
        // Product decision (Iris/Boar): the compact model doesn't answer from memory unless asked to.
        reasonCodes.push("grounding:declined-compact");
        markVisible();
        const message = pt ? "Não encontrei isso no acervo deste celular." : "I didn't find this in this phone's library.";
        emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
        // The decline is the answer's text too: a screen never shows an empty answer (Boar, gate 20e8c65).
        finalText = message;
        return finish(genTier, "success", message, [], receipt({ retrievalMs }));
      }
      if (fromMemory) {
        reasonCodes.push("grounding:no-source-memory");
        emit({ type: "warning", answerId, code: "weak_sources", message: "No offline source covers this question." });
      }
      const styleReminder =
        [
          ctx.styleReminder,
          health ? HEALTH_GROUNDING_INSTRUCTION : fromMemory ? NO_SOURCE_INSTRUCTION : undefined,
          // "today"/"hoje": the model gets the device's date instead of guessing one (Prism TD-1).
          mentionsNow(req.query) ? todayLine(deps.today?.() ?? new Date(), pt) : undefined,
          // A PT question, in Portuguese, next to it: with English sources the 4B answered 8 of 29 PT questions in
          // English (gate bc7db6d) despite "Reply in the question's language" after </sources>.
          pt ? (sources.length ? PT_ANSWER_LANGUAGE : PT_ANSWER_LANGUAGE_NO_SOURCES) : undefined,
        ]
          .filter(Boolean)
          .join("\n") || undefined;

      // 3. Generation.
      if (!gen || !genLlm) {
        return finish(genTier, "error", "", sources, receipt({ retrievalMs }), {
          code: "no_model",
          message: lowRamBlocked
            ? "The installed models are too large for this phone's memory. Install the Compact model, or confirm one in Models to run it anyway."
            : "No language model is installed.",
        });
      }

      let loadMs = gateLoadMs;
      /** ModelLoadError.kind of the last failed load (memory | engine | corrupt | missing). */
      let loadFailureKind: LoadFailureKind | undefined;
      const ensureLoaded = async (m: InstalledLlm, tier: AnswerTier): Promise<string | null> => {
        if (deps.engine.getModelInfo()?.filename === m.filename) return null;
        stage("loading_model", tier, m.id);
        const ls = deps.now();
        try {
          const r = await deps.engine.load(m.filename, { meta: { modelId: m.id, label: m.label } });
          loadMs += deps.now() - ls;
          if (r.warning) emit({ type: "warning", answerId, code: "model_streams_from_storage", message: r.warning });
          if (r.backend?.kind === "cpu-fallback" && !reasonCodes.includes("backend:cpu-fallback")) reasonCodes.push("backend:cpu-fallback");
          return null;
        } catch (e: any) {
          loadFailureKind = e?.kind;
          return e?.message ?? String(e);
        }
      };

      const residentBefore = deps.engine.getModelInfo()?.filename ?? null;
      const residency: ModelResidency = residentBefore === genLlm.filename ? "resident" : residentBefore === null ? "cold" : "switched";
      sampleMemory();
      const loadError = await ensureLoaded(genLlm, genTier);
      sampleMemory();
      if (loadError) {
        return finish(genTier, "error", "", sources, receipt({ retrievalMs }), {
          code: errorCodeOf(loadError, "load"),
          message: loadError,
          ...(loadFailureKind ? { kind: loadFailureKind } : {}),
        });
      }
      if (stopRequested) return finish(genTier, "stopped", "", sources, receipt({ retrievalMs, loadMs }));

      let text = "";
      let tokens = 0;
      let firstTokenAt: number | null = null;
      let timings: GenerationTimings | undefined;
      let timedOut = false;
      const onToken = (piece: string) => {
        if (firstTokenAt === null) {
          firstTokenAt = deps.now();
          if (generation) generation.firstTokenAt = firstTokenAt;
          markVisible();
          if (gen.mode === "single") stage("generating", genTier, genLlm.id);
        }
        if (tokens++ % MEMORY_SAMPLE_EVERY_TOKENS === 0) sampleMemory();
        emit({ type: "token", answerId, tier: genTier, text: piece });
      };

      generation = { modelId: genLlm.id, residency, firstTokenAt: null };
      // CT-A: the model's own reference list ("[1] Monsoon, Wikipedia, acessado em…") goes; the app's [n] cite.
      const withoutModelReferences = (t: string) => {
        const stripped = stripModelReferences(t);
        if (stripped === t.trimEnd()) return t;
        reasonCodes.push("grounding:model-references-stripped");
        finalText = stripped;
        return stripped;
      };
      try {
        if (gen.mode === "multipass") {
          const r = await deps.runMultipass(
            req.query,
            ctx.systemPrompt,
            ctx.history,
            ctx.maxTokens,
            (p) => {
              if (p.stage === "synthesizing") stage("synthesizing", genTier, genLlm.id);
              else
                stage("retrieving", genTier, genLlm.id, {
                  index: p.subQuestionIndex,
                  count: p.subQuestionCount,
                  ...(p.subQuestion ? { subQuestion: p.subQuestion } : {}),
                  ...(p.answering ? { answering: true } : {}),
                });
            },
            onToken,
            () => stopRequested,
            {
              retrieveK: gen.retrieveK,
              // A Portuguese question gets a Portuguese answer here too: every multi-pass prompt was English
              // and none said which language to answer in, so the synthesis followed the English sources.
              ...(pt ? { answerLanguage: PT_ANSWER_LANGUAGE } : {}),
              // Each sub-question's sources as soon as they are known (a prefix of the final list, same
              // numbers), instead of only after every sub-answer is written. The answer's sources are
              // still the final list below.
              onPartialSources: (s) => emit({ type: "sources", answerId, tier: genTier, sources: withoutRelevance(s) }),
              onSources: (s) => {
                sources = s;
                emit({ type: "sources", answerId, tier: genTier, sources: withoutRelevance(s) });
              },
            }
          );
          text = withoutModelReferences(r.answer);
          timedOut = !!r.timedOut;
          sources = r.citations;
          // P1: the sources in full, for CT-1 and attribution below (raw is empty here: retrieval ran inside
          // the orchestrator, and sources are the compressed sub-question bodies).
          if (r.fullCitations) fullCited = r.fullCitations;
        } else {
          stage("prefill", genTier, genLlm.id);
          const useTemplate = deps.engine.hasEmbeddedChatTemplate();
          const common = {
            nPredict: Math.min(ctx.maxTokens, gen.maxTokens ?? Infinity),
            // Reasoning models think before answering: bounded on the fast tier, off on the streaming MoE.
            thinkingBudget: gen.thinking ? (genTier === "deep" ? DEEP_THINKING_BUDGET : FAST_THINKING_BUDGET) : undefined,
            enableThinking: gen.thinking ? undefined : false,
            // Health: no sampling noise, and nothing reaches the screen before the safety check below.
            ...(health ? { temperature: 0 } : {}),
            onToken: health ? () => {} : onToken,
            onTimings: (t: GenerationTimings) => (timings = t),
          };
          text = withoutModelReferences(
            await deps.engine.generate(
              useTemplate
                ? {
                    ...common,
                    messages: deps.assembleChatMessages(req.query, sources, ctx.systemPrompt, ctx.history, styleReminder),
                    // CUT5-SPEED: with no sources and no conversation summary the system message is static -> its own KV session file.
                    ...(sources.length === 0 && !ctx.history?.summary?.trim() ? { staticSystem: true } : {}),
                  }
                : { ...common, prompt: deps.assemblePrompt(req.query, sources, ctx.systemPrompt, ctx.history, styleReminder) }
            )
          );
          if (health) {
            // A known-dangerous instruction the model added ("blow your nose", "tilt the head back"): show the source instead.
            const risky = riskyHealthInstruction(text);
            if (risky && sources.length) {
              reasonCodes.push(`grounding:health-unsafe-${risky}`);
              const fullSources = sources.map((c) => raw.find((r) => r.chunkId === c.chunkId) ?? c);
              const rules = excerptRules(req.query, healthTopicTerms(req.query, english));
              const safe = healthSourceOrder(fullSources, rules.procedure ?? null)
                .map((k) => healthExtract(fullSources[k], k + 1, pt, rules))
                .find(safeHealthExcerpt);
              text = safe ?? noSafeStepsAnswer(pt);
            } else if (!/emergency number|emerg[êe]ncia/i.test(text)) {
              // A model-written health answer ends with the emergency line too.
              text = `${text.trim()}\n\n${emergencyLine(pt)}`;
            }
            markVisible();
            emit({ type: "token", answerId, tier: genTier, text });
          }
        }
      } catch (e: any) {
        const message = e?.message ?? String(e);
        return finish(genTier, "error", text, sources, receipt({ retrievalMs, loadMs, tokens }), {
          code: errorCodeOf(message, "generate"),
          message,
        });
      }

      const genEnd = deps.now();
      const decodeMs = timings?.predictedMs ?? (firstTokenAt !== null ? genEnd - firstTokenAt : 0);
      const decodeTokens = timings?.predictedTokens ?? tokens;
      if (generation) Object.assign(generation, { decodeMs, decodeTokens });
      const baseReceipt = receipt({
        tokens,
        tokPerSec: decodeMs > 0 ? (decodeTokens / decodeMs) * 1000 : 0,
        retrievalMs,
        loadMs,
        prefillMs: timings?.promptMs,
        ctxTokens: timings?.promptTokens,
        cachedTokens: timings?.cachedTokens,
      });
      // Development builds only: what the model wrote, before the checks below can change or hold it back.
      if (DEV_LOG) console.log(`[ANSWER] model wrote ${JSON.stringify(text.slice(0, 600))} from ${JSON.stringify(sources.map((c) => c.title))}`);
      // A source in full: the orchestrator's (multi-pass), else the retrieved chunk (single pass), else as shown.
      // fullCited first: on a deeper answer, raw holds the earlier answer's shown (compressed) sources, which
      // would shadow the full chunk whenever the synthesis cites the same one again.
      const fullOf = (c: RetrievedChunk) =>
        fullCited.find((r) => r.chunkId === c.chunkId) ?? raw.find((r) => r.chunkId === c.chunkId) ?? c;
      // CT-1: a [n] stays only where source n supports its sentence.
      let allCitationsRemoved = false;
      if (/\[\d+\]/.test(text)) {
        const cited = sources.map((c) => fullOf(c));
        const checked = checkCitations(text, cited);
        if (checked.removed.length) {
          reasonCodes.push(`citations:removed-${checked.removed.join("-")}`);
          text = checked.text;
          finalText = text;
          // Boar (A), s32 672bc41: the compact model cited, and no cited source supported it: 4 of 5 such
          // answers were confident errors ("Great Famine" from "Great Recession in Africa"). It declines,
          // as with no source, unless asked to answer anyway. The 4B keeps its (corrected) answer.
          // Also across languages (a PT answer, English sources): the cross-language exception (5fa5d96) let
          // 7 new confident crypto errors of the 1.5B through in PT (gate 20e8c65: "32 ETH" for EIP-7251), so
          // it was reverted; the decline is right there.
          // The decline waits for the attribution below: a sentence a source supports at the higher bar gets
          // its [n] back ("Nuclear fusion involves two or more atomic nuclei combining… [1]" after the [1]
          // closing its fission sentence went), and then the answer stands.
          allCitationsRemoved = !/\[\d+\]/.test(text);
        }
      }
      // A word in the wrong script for the language asked about ("obrigado em tailandês" answered in Khmer,
      // trv-007-pt): the answer's core is wrong, and what's left would mislead ("it's the same for men and women"):
      // the decline, both models, unless asked to answer anyway.
      if (!health && gen.mode !== "multipass" && !req.answerAnyway && wrongScriptSentences(req.query, text).length) {
        reasonCodes.push("grounding:wrong-script-declined");
        const message = pt ? "Não encontrei isso no acervo deste celular." : "I didn't find this in this phone's library.";
        emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
        finalText = message;
        return finish(genTier, "success", message, [], baseReceipt);
      }
      // A known-false claim (classical public-key crypto called quantum resistant, gate 394bf31): the compact
      // model declines; the 4B loses the sentence. Deterministic, whatever the sampling seed.
      const falseClaims = !health && gen.mode !== "multipass" ? falseQuantumClaims(text) : [];
      if (falseClaims.length) {
        if (isCompactModel(genLlm) && !req.answerAnyway) {
          reasonCodes.push("grounding:false-claim-declined-compact");
          const message = sources.length
            ? pt ? "Os trechos encontrados não sustentam esta resposta." : "The passages found don't support this answer."
            : pt ? "Não encontrei isso no acervo deste celular." : "I didn't find this in this phone's library.";
          emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
          finalText = message;
          return finish(genTier, "success", message, [], baseReceipt);
        }
        reasonCodes.push(`grounding:false-claim-removed-${falseClaims.length}`);
        for (const claim of falseClaims) text = text.replace(claim, "");
        text = text.replace(/[ \t]{2,}/g, " ").trim();
        finalText = text;
      }
      // Its inverse (Boar, gate 9ef80f9): a sentence without [n] that an on-topic source supports, by
      // the same measure, gets that source's [n]. Never without support.
      // Health answers are the source's excerpt or checked for risky lines: not here.
      // P2: multi-pass answers too, now that the sources are checked in full (fullCitations): same 0.75 bar.
      if (!health && sources.length && text.trim() && !stopRequested) {
        const attributed = attributeCitations(text, sources.map((c) => fullOf(c)));
        if (attributed.added.length) {
          reasonCodes.push(`citations:added-${attributed.added.join("-")}`);
          text = attributed.text;
          finalText = text;
        }
      }
      // VG (cut 3 dev, coordinator 4B-LOSS-ANATOMY arm 1): a Yes/No or winner that the answer's own facts contradict
      // ("No, astronauts did not walk on the Moon before 1970… July 20, 1969") is rewritten from those facts. EN model
      // answers only: never health (cards, excerpts), PT or ES. Unflagged answers are untouched.
      if (!health && !pt && !isSpanishQuestion(req.query) && text.trim() && !stopRequested) {
        const vg = verdictGuard(req.query, text);
        if (vg) {
          reasonCodes.push(`verdict-guard:${vg.kind}`);
          text = vg.text;
          finalText = text;
        }
      }
      if (stopRequested) return finish(genTier, "stopped", text, sources, baseReceipt);
      if (allCitationsRemoved && !/\[\d+\]/.test(text) && !health && isCompactModel(genLlm) && !req.answerAnyway && gen.mode !== "multipass") {
        reasonCodes.push("grounding:all-citations-removed-declined-compact");
        // Passages were found (and shown): "didn't find this" would be false (Quill 892c049).
        const message = pt ? "Os trechos encontrados não sustentam esta resposta." : "The passages found don't support this answer.";
        emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
        finalText = message;
        return finish(genTier, "success", message, [], baseReceipt);
      }

      // Safety net (Boar, gates ea5978c and be817b3): a knowledge answer that cites nothing, when no
      // source was on topic, came from memory ("Estrela, Lisbon" for the seasons). The 4B says so up
      // front; the compact model declines unless asked to answer anyway. With an on-topic source it
      // answers as written (s32: the line cost the 4B 4 right answers per wrong one caught, and the
      // compact model declined 21 of 28 knowledge questions); the chat still gets weak_sources (finish).
      // Boar, gate 1724fd5: with the PT language line the models stopped writing [n]; without [n], CT-1 removed
      // nothing, (A) never fired and the compact model's PT confident errors went 5 -> 9. A compact answer with
      // sources in its prompt and no [n] at all (after attribution) is treated like "every citation removed":
      // the decline, unless asked to answer anyway. The 4B is unchanged.
      if (!health && gen.mode !== "multipass" && sources.length && text.trim() && !/\[\d+\]/.test(text) && isCompactModel(genLlm) && !req.answerAnyway) {
        reasonCodes.push("grounding:uncited-with-sources-declined-compact");
        const message = pt ? "Os trechos encontrados não sustentam esta resposta." : "The passages found don't support this answer.";
        emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
        finalText = message;
        return finish(genTier, "success", message, [], baseReceipt);
      }
      const knowledge = !health && plan.retrieve && gen.mode !== "multipass" && ["lookup", "research", "compare", "extract"].includes(taskType);
      // Also with no source at all. The app's line is the one notice: the model's own is stripped first (gate
      // cd1478a: the 4B's translated "Esta resposta não está em um banco de dados offline." came after the app's).
      if (knowledge && text.trim() && !/\[\d+\]/.test(text)) {
        const stripped = stripModelDisclaimer(text, pt);
        if (stripped.trim() && stripped !== text.trimStart()) {
          reasonCodes.push("grounding:model-disclaimer-stripped");
          text = stripped;
          finalText = text;
        }
        // Sources that passed the topic guard are on topic.
        const onTopicSources = guarded ? sources.length : 0;
        if (onTopicSources > 0) reasonCodes.push("grounding:uncited-on-topic");
        else if (sources.length && isCompactModel(genLlm) && !req.answerAnyway) {
          reasonCodes.push("grounding:uncited-declined-compact");
          const message = pt ? "Não encontrei isso no acervo deste celular." : "I didn't find this in this phone's library.";
          emit({ type: "warning", answerId, code: "weak_sources", declined: true, message });
          finalText = message;
          return finish(genTier, "success", message, [], baseReceipt);
        } else {
          reasonCodes.push("grounding:uncited-preface");
          // No source: the weak_sources warning already went out before generation.
          if (sources.length) emit({ type: "warning", answerId, code: "weak_sources", message: "No offline source covers this question." });
          text = `${uncitedPreface(pt)}\n\n${text.trim()}`;
          finalText = text;
        }
      }

      // 4. Verification (complete answers only, distinct verifier).
      if (plan.verify && text.trim() && sources.length) {
        const verifier = byId.get(plan.verify.modelId);
        if (verifier) {
          const vErr = await ensureLoaded(verifier, genTier);
          if (!vErr && !stopRequested) {
            stage("verifying", genTier, verifier.id);
            const verdictText = await deps.engine
              .generate({
                ...taskRequest(
                  VERIFICATION_INSTRUCTION,
                  buildVerificationInput(req.query, text, sources),
                  "Verdict:",
                  deps.engine.hasEmbeddedChatTemplate()
                ),
                nPredict: 200,
                thinkingBudget: 128,
                temperature: 0.2,
              })
              .catch(() => "");
            const v = parseVerificationVerdict(verdictText).status;
            if (v === "passed" || v === "failed" || v === "uncertain") baseReceipt.verification = v;
          } else if (vErr) {
            reasonCodes.push("verify:load-failed");
          }
          baseReceipt.totalMs = deps.now() - t0;
        }
      }

      const result = finish(genTier, timedOut ? "timeout" : "success", text, sources, baseReceipt);
      if (plan.offerDeep && !stopRequested) {
        emit({
          type: "deep_available",
          answerId,
          reason: deepModel ? `deep-model:${deepModel.id}` : "multipass",
        });
      }
      return result;
    }
  }

  /** "Deeper answer" on an earlier answer: same question, deep tier, same sources. */
  function deepen(
    query: string,
    sources: RetrievedChunk[],
    onEvent: AnswerEventHandler,
    ctx: AnswerContext
  ): AnswerHandle {
    return answer({ query, tier: "deep", reuseSources: sources }, onEvent, ctx);
  }

  /** The model the next answer will use (same rules as answer()); null when none can run. */
  async function effectiveModel(): Promise<EffectiveAnswerModel | null> {
    const { fastLlm, blockedActive, settings } = await selectAnswerModel(deps);
    if (!fastLlm) return null;
    return {
      id: fastLlm.id,
      label: fastLlm.label,
      ...(blockedActive && blockedActive.id !== fastLlm.id
        ? {
            downgradedFrom: {
              id: blockedActive.id,
              label: blockedActive.label,
              reason: (settings.loadCrashedIds ?? []).includes(blockedActive.id) ? ("load-crashed" as const) : ("low-ram" as const),
            },
          }
        : {}),
    };
  }

  return { answer, deepen, effectiveModel };
}
