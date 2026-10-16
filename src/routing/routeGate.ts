/**
 * Route gate: the loaded model decides whether a message needs a search (Anvil chat-routing B4,
 * rnd/research/anvil/exp/chat-routing). One prefill of a fixed router prompt + the conversation, then the
 * next-token probabilities of "SEARCH" vs "ANS" (first token of "ANSWER"); no text is generated.
 * Compass chat-slice v0 on the 4B: 95% (false search 3/34, missed search 0/23); the 1.5B misses 8/23
 * searches, so it keeps the rules (ROUTE_GATE_MODEL_IDS).
 */
import { isRetrievalIrrelevant } from "./classify";
import type { TaskType } from "./types";
import type { ConversationHistory } from "../rag/pure";

/** Models the gate runs on. The 1.5B answered world questions from memory behind it. */
export const ROUTE_GATE_MODEL_IDS: ReadonlySet<string> = new Set(["qwen3-4b-instruct-2507-q4km"]);

/** Device probe switches (anvil/specs/probe-gate-prefix-cache.md): edit in place, never shipped on. */
export const ROUTE_GATE_CONFIG = { anyModel: false, off: false };

/**
 * What BOAR is, for questions about the app itself (B6: the router answers those; the no-source answer can reuse it).
 */
export const BOAR_SELF_DESCRIPTION =
  "an offline app on this phone; a small local AI model answers from a downloaded library (Wikipedia, Wikivoyage, " +
  "crypto, OpenStreetMap) and shows its sources; text only, no internet; nothing leaves the phone.";

/**
 * B6 = B5 + well-known facts always SEARCH (B5 answered "Which side won the American Civil War?" from memory in L2,
 * score 0.001) + a broader "question about BOAR itself" ANSWER line and the self-description AFTER the examples (B5's
 * weak kind on v1-fresh was meta; placed first, it made the gate answer lookups from memory) + "help with a real problem"
 * and "greeting followed by a question" clauses + 3 new examples. Dev sets: chat-slice v0, v0.1 (gate-only), own probes.
 * B5 = B4 without "arithmetic" in ANSWER and the "12 times 7" example (L1 vit-sci-003); B4 = search by default + examples.
 * Changing it invalidates the measurement.
 */
export const ROUTE_GATE_SYSTEM = [
  "You are BOAR's router. BOAR answers questions about the world only from its offline library, never from memory.",
  "SEARCH: the last message asks for any fact, explanation, advice, place, route, rule, health, safety or travel information. Well-known facts too (who won, when something happened, a capital, who someone was, how many): BOAR must show a source, so SEARCH even when the answer seems obvious. A call for help with a real problem (an injury, danger, getting somewhere) is SEARCH. A greeting followed by a question is judged by the question.",
  "ANSWER: the last message is a greeting, thanks, small talk, a request to rewrite or translate text already in the conversation, or a question about BOAR itself: what it is, how it works, where its answers come from, what it can or cannot do, what its library holds, how to use it, privacy.",
  "",
  "Examples:",
  "\"oi!\" -> ANSWER",
  "\"good evening\" -> ANSWER",
  "\"obrigado!\" -> ANSWER",
  "\"who made you?\" -> ANSWER",
  "\"make that shorter\" -> ANSWER",
  "\"what languages do you speak?\" -> ANSWER",
  "\"lol\" -> ANSWER",
  "\"capital da Austrália\" -> SEARCH",
  "\"how tall is the Eiffel Tower?\" -> SEARCH",
  "\"como chegar em Ouro Preto de ônibus?\" -> SEARCH",
  "\"is it safe to swim after eating?\" -> SEARCH",
  "\"help, I cut my hand badly\" -> SEARCH",
  "\"when was the printing press invented?\" -> SEARCH",
  "\"what is a rollup?\" -> SEARCH",
  "\"e quanto custa?\" (after a travel answer) -> SEARCH",
  "",
  `About BOAR (only for questions about the app itself): ${BOAR_SELF_DESCRIPTION}`,
  "",
  "Reply with exactly one word: SEARCH or ANSWER."
].join("\n");

/** Top-N next-token probabilities asked of the engine; both labels sat in the top 2 on the desktop (mass ≥ 0.93). */
export const ROUTE_GATE_N_PROBS = 20;
/** Label first tokens as llama.cpp prints them (Qwen2.5 and Qwen3 tokenizers: "SEARCH" = 1 token, "ANSWER" = "ANS"+"WER"). */
const SEARCH_PIECE = "SEARCH";
const ANSWER_PIECE = "ANS";
/** History cap: a follow-up pays a prefill for every new token, and the gate sits before the first visible text. */
const MAX_TURNS = 6;
const MAX_TURN_CHARS = 280;

export interface TokenProb {
  token: string;
  prob: number;
}

export interface GateMessage {
  role: "system" | "user";
  content: string;
}

const clip = (s: string) => (s.length > MAX_TURN_CHARS ? `${s.slice(0, MAX_TURN_CHARS).trimEnd()}…` : s);

/**
 * The gate prompt. History is folded into one user message: as separate chat turns the label mass fell to
 * ~0.001 on follow-ups. The system message is the cacheable prefix.
 */
export function routeGateMessages(query: string, history?: ConversationHistory | null): GateMessage[] {
  const turns = (history?.turns ?? []).slice(-MAX_TURNS);
  const user = turns.length
    ? `Conversation so far:\n${turns.map((t) => `${t.role}: ${clip(t.text)}`).join("\n")}\n\nLast message: ${query}`
    : query;
  return [
    { role: "system", content: ROUTE_GATE_SYSTEM },
    { role: "user", content: user },
  ];
}

export interface GateVerdict {
  decision: "search" | "answer";
  /** p(SEARCH) / (p(SEARCH) + p(ANS)); null when neither label is in the top N (then: search). */
  score: number | null;
  labelMass: number;
}

/**
 * Threshold 0.5. Also right on llama.rn rc.4's post-sampling probabilities: the ratio keeps the sign of the
 * logit gap at any temperature, and truncation can only drop the lower label (anvil llamarn-nprobs.md).
 */
export function routeGateVerdict(probs: readonly TokenProb[]): GateVerdict {
  const pS = probs.find((p) => p.token === SEARCH_PIECE)?.prob ?? 0;
  const pA = probs.find((p) => p.token === ANSWER_PIECE)?.prob ?? 0;
  const labelMass = pS + pA;
  if (!(labelMass > 0)) return { decision: "search", score: null, labelMass: 0 };
  const score = pS / labelMass;
  return { decision: score >= 0.5 ? "search" : "answer", score, labelMass };
}

/**
 * The gate decides only whether to search. "answer" keeps a rule type that already skips retrieval
 * (translate, code, …) and otherwise answers as a conversation; "search" retrieves, as "chat" when the rule
 * type would skip it.
 */
export function gatedTaskType(rule: TaskType, decision: GateVerdict["decision"]): TaskType {
  if (decision === "answer") return isRetrievalIrrelevant(rule) ? rule : "conversation";
  return isRetrievalIrrelevant(rule) ? "chat" : rule;
}

/**
 * CUT5-SPEED: when the rules already decide, the gate (one model forward pass, 3-5 s warm on the A15) is skipped.
 * Skipping is equivalent to a gate verdict of SEARCH (gatedTaskType(rule, "search") = rule for these types), so it only
 * ever removes the gate's chance to divert a message to conversation. It is therefore limited to rule types that are
 * questions about the world, and never taken for: a detector fire, a lexical emergency or safety/health message (isSafetyQuery), a hazard word (boil, flood, bleach, ...), any earlier turn (a follow-up like "and the price?" is judged with its history), or a message that may be about
 * BOAR itself or addressed to it ("you", "your", the app, the assistant, the library, ...). Otherwise the gate runs as before.
 */
export const GATE_SKIP_RULE_TYPES: ReadonlySet<TaskType> = new Set<TaskType>(["lookup", "research", "compare"]);

const ABOUT_BOAR_RE =
  /(?<![\p{L}])(boar|you|your|yours|yourself|you'?re|you'?ll|you'?ve|youre|app|apps|application|assistant|chatbot|chat bot|bot|ai|llm|model|models|offline|online|internet|library|libraries|knowledge pack|privacy|private|vo[cç]ês?|voce|vc|seu|sua|seus|suas|e a[ií]|tudo bem|beleza|te|teu|tua|teus|tuas|contigo|conversa|conversation|conversaci[oó]n|chat|hist[oó]rico|aplicativo|aplicação|assistente|biblioteca|t[uú]|usted|ustedes|eres|tu|tus|aplicaci[oó]n)(?![\p{L}])|\bhow (does|do|did) (it|this|that) work\b|\bwhat (can|does|do) (it|this) (do|know|have|hold)\b|\bwho (made|built|created|developed|wrote|owns|runs)\b|\bthis (thing|tool|program|software|system)\b/iu;

/** Hazard words beyond isSafetyQuery (context.ts): water-safety and disaster wording the learned detector may or may not flag. Never skipped. */
const HAZARD_RE =
  /(?<![\p{L}])(boil(ed|ing)?|flood(s|ed|ing|water)?|bleach|iodine|chlorin\w*|contaminat\w*|advisory|do not drink|don'?t drink|tap water|well water|safe to drink|potable|poison\w*|toxic|overdos\w*|bitten|bleed\w*|chok\w*|unconscious|smoke|carbon monoxide|earthquake|hurricane|tsunami|wildfire)(?![\p{L}])/iu;

/** A message that opens with a greeting ("boa noite! o que causa as marés?") is left to the gate, which judges it. */
const GREETING_LEAD_RE = /^\s*[¡¿]?(oi+|ol[aá]|opa|e a[ií]|bom dia|boa (tarde|noite|noitee)|hello|hi|hey|yo|good (morning|evening|afternoon|night)|hola|buen(os|as)|thanks|thank you|obrigad[oa]|valeu|gracias)(?![\p{L}])/iu;

export interface GateSkipInput {
  ruleTaskType: TaskType;
  query: string;
  /** Number of earlier turns in the conversation. */
  historyTurns: number;
  /** The learned emergency detector fired on this message. */
  detectorFired: boolean;
  /** Lexical emergency: a first-aid condition card applies, or isSafetyQuery (health, safety, disaster), whatever the detector said. */
  emergencyLexical: boolean;
}

export function routeGateSkip(i: GateSkipInput): boolean {
  if (!GATE_SKIP_RULE_TYPES.has(i.ruleTaskType)) return false;
  if (i.detectorFired || i.emergencyLexical) return false;
  if (i.historyTurns > 0) return false;
  // One or two words ("e aí?", "and you?") are conversation more often than a question about the world.
  if (i.query.trim().split(/\s+/).length < 3) return false;
  if (GREETING_LEAD_RE.test(i.query) || ABOUT_BOAR_RE.test(i.query) || HAZARD_RE.test(i.query)) return false;
  return true;
}
