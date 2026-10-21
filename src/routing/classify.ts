import { TaskType } from "./types";

/**
 * Deterministic, rule-based task classification — per the build plan,
 * explicitly NOT an LLM call ("do not initially ask an LLM to freely invent
 * a pipeline"). Heuristic and imperfect by nature (keyword/shape matching on
 * a short query string can't really "understand" intent), but deterministic
 * and testable, which is the actual requirement for Phase 3's router: same
 * input always produces the same plan.
 */
const PATTERNS: Array<{ type: TaskType; test: RegExp }> = [
  { type: "compare", test: /\b(compare|versus|vs\.?|difference between|which is better)\b/i },
  // Portuguese (gate ea5978c: "Por que existem as estações do ano?" was "chat", so a from-memory
  // answer got no "not from the library" line). \b doesn't see accented letters: unaccented stems.
  { type: "compare", test: /\bdiferen[çc]a entre\b|\bcompar(e|ar|a[çc][ãa]o)\b|\bqual [ée] melhor\b/i },
  { type: "summarize", test: /\b(summarize|summarise|summary of|tl;?dr)\b/i },
  { type: "translate", test: /\btranslate\b/i },
  { type: "code", test: /```|\b(write (a |some )?code|debug this|refactor|fix this function|regex for)\b/i },
  { type: "calculate", test: /\b(calculate|compute|how much is)\b|(?<!\d)\d+\s*[+\-*/×÷]\s*\d/i },
  { type: "extract", test: /\b(extract|list all|pull out|find every)\b/i },
  // Explanation/reasoning questions: never a one-sentence lookup, even when
  // short and opening with "what" ("What caused the French Revolution?").
  {
    type: "research",
    test: /^(why|how)\b|\b(explain|caused?|causes|effects? of|impacts?|relate[sd]?|relationship|contrast|implications?)\b/i,
  },
  { type: "research", test: /^(por ?qu[eê]|como)\s|\b(explique|explica|explicar|causou|causas?|efeitos? d[eao]s?|impactos?)\b/i },
];

// Matches ONE greeting phrase, trailing punctuation only — not the whole
// query. Anchored per-segment (see isGreeting below), not per-query: "hi,
// can you compare X and Y" must NOT classify as greeting overall, but a
// literal single phrase like "hi" must.
const GREETING_PHRASE_RE =
  /^(hi|hello|hey|hey there|yo|sup|wake up|good (morning|afternoon|evening|night)|how(?:'s| is| are) it going|how are you|what'?s up|thanks?( you)?|thank you|bye|goodbye|see ya|see you|ok(ay)?|cool|nice)[!.?~]*$/i;

/**
 * A query counts as a pure greeting if it's made up ENTIRELY of greeting
 * phrases — including a compound one like "hey, what's up?" (two phrases
 * joined by a comma), not just a single literal match. Splitting on common
 * connectors (comma/semicolon/"and") and requiring every resulting segment
 * to independently match GREETING_PHRASE_RE generalizes to any combination
 * of the known phrases without hardcoding each combination as its own
 * literal string — "hi, can you compare X and Y" still correctly fails,
 * since "can you compare X" isn't a greeting segment.
 */
function isGreeting(trimmed: string): boolean {
  const segments = trimmed
    .split(/[,;]|\band\b/i)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  return segments.length > 0 && segments.every((seg) => GREETING_PHRASE_RE.test(seg));
}

/**
 * Questions to or about the assistant and chit-chat, where the knowledge base can only add noise
 * (scripts/rag-calibrate.mjs: "whats your name?" retrieved Name 0.69, "tell me a joke" Joke 0.75,
 * "what time is it?" Noon 0.71). Anchored on the whole message, so "what is the name of the
 * capital of Australia" still retrieves.
 */
const CONVERSATION_RE =
  /^(?:what'?s?\s+(?:is\s+)?your\s+name|who\s+are\s+you|what\s+are\s+you|what\s+can\s+you\s+do|(?:how\s+)?can\s+you\s+help(?:\s+me)?|who\s+(?:made|built|created|trained)\s+you|are\s+you\s+(?:an?\s+)?(?:ai|bot|robot|human|real|person|chatgpt|online|offline)|how\s+do\s+you\s+work|(?:tell\s+me\s+)?(?:a\s+)?joke|what\s+time\s+is\s+it|what'?s?\s+(?:is\s+)?the\s+time|what\s+day\s+is\s+(?:it|today)|what'?s?\s+(?:is\s+)?today'?s\s+date)(?:\s+(?:please|again|now|today))?[\s!.?~]*$/i;

const YES_NO =
  /^(is|are|was|were|do|does|did|can|could|should|will|would|has|have|é|e|são|sao|era|foi|posso|pode|podem|devo|deve|precisa|preciso|tem|há|ha|existe|vale)(?![\p{L}])[^?]*\?\s*$/iu;

// CHATFAIL L2: intent for follow-ups that ask for code or an example. The base regex only knew "write (a|some) code", so
// "write it", "show me the code", "not even an example?" and "what does it look like?" (after a technical turn) fell to lookup/chat,
// with retrieval and the Succinct 2-3 sentence cap. Explicit code nouns need no context; the ambiguous words ("it", "an example",
// "look like") need a technical word in this message or an earlier user turn, so "an example of a supervolcano" and "what does the
// road look like in January" stay lookups.
const CODE_CONTEXT =
  /\b(smart contracts?|solidity|erc-?\d+|merkle|hash(?:es|ing)?|algorithms?|programs?|programming|code|coding|scripts?|software|functions?|apis?|compiler|sql|database|python|javascript|typescript|rust|numpy|neural networks?|backpropagation|simulations?|data structures?)\b/i;
const CODE_EXPLICIT = [
  // show/see/give/paste + (the|some|a) code|program|script|snippet
  /\b(?:show|see|view|give|get|have|paste|print|display|read)\b(?:\s+(?:me|us))?(?:\s+(?:the|its|that|this|some|a|an|your|full))?\s+(?:source\s+)?(?:code|program|script|snippet)\b/i,
  // "in code", "in Python", "as code"
  /\b(?:in|as|using|with)\s+(?:code|python|javascript|js|typescript|solidity|rust|c\+\+|numpy|sql|bash)\b/i,
];
const CODE_NEEDS_CONTEXT = [
  // write it / write one for me (not "write it down")
  /\bwrite\s+(?:me\s+)?(?:it|that|this|one|them)\b(?!\s+down)/i,
  // an example / not even an example (not "an example of ...")
  /\b(?:an?|one|some|another|any)\s+(?:(?:small|short|simple|quick|minimal|tiny|code)\s+)*examples?\b(?!\s+of\b)/i,
  // what/how does it look like
  /\b(?:what|how)\b[^?]*\blooks?\s+like\b/i,
];

/**
 * Like classifyTask, with the user's earlier turns (oldest first) as context for the code-intent rule. Only a lookup, research,
 * chat or unknown message can become code; every other type is returned as classifyTask gives it. The answer path never calls
 * this for a health or emergency message (answer.ts), so a first-aid chat keeps its health route.
 */
export function classifyTaskInContext(query: string, priorUserTurns: string[] = []): TaskType {
  const base = classifyTask(query);
  if (base !== "lookup" && base !== "research" && base !== "chat" && base !== "unknown") return base;
  const q = query.trim();
  if (CODE_EXPLICIT.some((re) => re.test(q))) return "code";
  if (CODE_NEEDS_CONTEXT.some((re) => re.test(q)) && (CODE_CONTEXT.test(q) || priorUserTurns.some((t) => CODE_CONTEXT.test(t)))) return "code";
  return base;
}

export function classifyTask(query: string): TaskType {
  const trimmed = query.trim();
  if (!trimmed) return "unknown";

  if (isGreeting(trimmed)) return "greeting";
  if (CONVERSATION_RE.test(trimmed)) return "conversation";

  for (const { type, test } of PATTERNS) {
    if (test.test(trimmed)) return type;
  }

  const wordCount = trimmed.split(/\s+/).length;
  if (/^(who|what|when|where|which|quem|o que|qual|quais|quando|onde|quantos?|quantas?)\s/i.test(`${trimmed} `) && wordCount <= 12) {
    return "lookup";
  }
  if (wordCount > 25 || /\b(research|analyze|analyse|investigate|explore|explain in depth)\b/i.test(trimmed)) {
    return "research";
  }
  // A yes/no question is a question about the world ("Is tipping expected in restaurants in Portugal?", "É esperado
  // dar gorjeta…?"): as "chat" the compact model answered it from memory with no source (Sextant trv-009: "tão ou
  // tãozinho, 10%"); as a lookup it declines when the library has nothing on topic. Unicode boundaries ("É").
  if (YES_NO.test(trimmed)) return "lookup";
  return "chat";
}

/**
 * Whether local-knowledge-base retrieval is genuinely irrelevant for this
 * task type — shared between the (unwired) router and the live chat path
 * (`ChatScreen.tsx`) so the rule lives in exactly one place. A translation,
 * calculation, code request, or pure greeting doesn't get better by
 * retrieving unrelated knowledge-base chunks; every other task type
 * (including the broad "chat" fallback, which also catches real
 * informational requests phrased as commands) still retrieves.
 */
export function isRetrievalIrrelevant(taskType: TaskType): boolean {
  return (
    taskType === "calculate" ||
    taskType === "translate" ||
    taskType === "code" ||
    taskType === "greeting" ||
    taskType === "conversation"
  );
}
