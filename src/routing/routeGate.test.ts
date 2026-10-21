import { describe, it, expect } from "vitest";
import { BOAR_SELF_DESCRIPTION, gatedTaskType, routeGateSkip, routeGateMessages, routeGateVerdict, ROUTE_GATE_SYSTEM } from "./routeGate";

/** Measured (cut 7 gate prompt B6, sha256 7708281b…); update only with a new gate measurement. */
const ROUTE_GATE_SYSTEM_B6_LENGTH = 1673;

describe("routeGateMessages", () => {
  it("sends a single message as is, after the B6 system prompt", () => {
    expect(routeGateMessages("boa noite 🙂")).toEqual([
      { role: "system", content: ROUTE_GATE_SYSTEM },
      { role: "user", content: "boa noite 🙂" },
    ]);
    expect(ROUTE_GATE_SYSTEM.length).toBe(ROUTE_GATE_SYSTEM_B6_LENGTH);
    expect(ROUTE_GATE_SYSTEM).not.toMatch(/arithmetic|12 times 7/);
    expect(ROUTE_GATE_SYSTEM).toContain("Well-known facts too");
    expect(ROUTE_GATE_SYSTEM).toContain(`About BOAR (only for questions about the app itself): ${BOAR_SELF_DESCRIPTION}`);
    expect(ROUTE_GATE_SYSTEM.endsWith("Reply with exactly one word: SEARCH or ANSWER.")).toBe(true);
  });

  it("folds history into one user message (the measured format)", () => {
    const m = routeGateMessages("e quanto custa?", { turns: [{ role: "user", text: "como ir a Pai?" }, { role: "assistant", text: "De ônibus." }] });
    expect(m[1].content).toBe("Conversation so far:\nuser: como ir a Pai?\nassistant: De ônibus.\n\nLast message: e quanto custa?");
  });

  it("caps history to the last 6 turns and 280 characters a turn", () => {
    const turns = Array.from({ length: 8 }, (_, i) => ({ role: (i % 2 ? "assistant" : "user") as "user" | "assistant", text: `t${i} ${"x".repeat(400)}` }));
    const user = routeGateMessages("ok?", { turns })[1].content;
    expect(user).not.toContain("t0 ");
    expect(user).not.toContain("t1 ");
    expect(user).toContain("t2 ");
    expect(user.split("\n").filter((l) => l.startsWith("user:") || l.startsWith("assistant:")).every((l) => l.length <= 292)).toBe(true);
  });
});

describe("routeGateVerdict", () => {
  it("scores p(SEARCH) / (p(SEARCH) + p(ANS)) at 0.5", () => {
    expect(routeGateVerdict([{ token: "ANS", prob: 0.9 }, { token: "SEARCH", prob: 0.05 }])).toMatchObject({ decision: "answer" });
    expect(routeGateVerdict([{ token: "SEARCH", prob: 0.6 }, { token: "ANS", prob: 0.3 }]).score).toBeCloseTo(2 / 3);
    expect(routeGateVerdict([{ token: "SEARCH", prob: 0.5 }, { token: "ANS", prob: 0.5 }]).decision).toBe("search");
  });

  it("reads llama.rn rc.4 one-hot / truncated probabilities the same way", () => {
    expect(routeGateVerdict([{ token: "ANS", prob: 1 }, { token: "SEARCH", prob: 0 }]).decision).toBe("answer");
    expect(routeGateVerdict([{ token: "SEARCH", prob: 1 }]).decision).toBe("search");
  });

  it("searches when neither label is in the top N", () => {
    expect(routeGateVerdict([{ token: "Hello", prob: 0.9 }])).toEqual({ decision: "search", score: null, labelMass: 0 });
    expect(routeGateVerdict([])).toEqual({ decision: "search", score: null, labelMass: 0 });
  });
});

describe("gatedTaskType", () => {
  it("answer: conversation, unless the rule type already skips retrieval", () => {
    expect(gatedTaskType("chat", "answer")).toBe("conversation");
    expect(gatedTaskType("lookup", "answer")).toBe("conversation");
    expect(gatedTaskType("translate", "answer")).toBe("translate");
    expect(gatedTaskType("greeting", "answer")).toBe("greeting");
  });
  it("search: the rule type, as chat when that type would skip retrieval", () => {
    expect(gatedTaskType("lookup", "search")).toBe("lookup");
    expect(gatedTaskType("compare", "search")).toBe("compare");
    expect(gatedTaskType("greeting", "search")).toBe("chat");
    expect(gatedTaskType("conversation", "search")).toBe("chat");
  });
});

describe("routeGateSkip (CUT5-SPEED)", () => {
  const base = { ruleTaskType: "lookup" as const, query: "What is the capital of Australia?", historyTurns: 0, detectorFired: false, emergencyLexical: false };
  it("skips when the rules decide: lookup, research, compare, no history, no detector fire, not about BOAR", () => {
    for (const t of ["lookup", "research", "compare"] as const) expect(routeGateSkip({ ...base, ruleTaskType: t })).toBe(true);
    expect(routeGateSkip({ ...base, ruleTaskType: "research", query: "Explain Mozi's views on universal love." })).toBe(true);
    expect(routeGateSkip({ ...base, query: "What is the capital of the U.S.?" })).toBe(true);
  });
  it("runs the gate for every other rule type", () => {
    for (const t of ["chat", "conversation", "greeting", "calculate", "translate", "code", "unknown"] as const) expect(routeGateSkip({ ...base, ruleTaskType: t })).toBe(false);
  });
  it("runs the gate for hazard wording the detector may miss (boil advisories, floods, bleach)", () => {
    for (const q of ["How long do I need to boil my tap water before I can drink it?", "Is it fine to use unscented bleach in well water?", "What to do after the hurricane floods the basement?"])
      expect(routeGateSkip({ ...base, query: q }), q).toBe(false);
  });
  it("runs the gate for one- and two-word messages", () => {
    for (const q of ["e aí?", "and you?", "thanks"]) expect(routeGateSkip({ ...base, query: q }), q).toBe(false);
  });
  it("runs the gate with history, a detector fire or an emergency", () => {
    expect(routeGateSkip({ ...base, historyTurns: 2 })).toBe(false);
    expect(routeGateSkip({ ...base, detectorFired: true })).toBe(false);
    expect(routeGateSkip({ ...base, emergencyLexical: true })).toBe(false);
  });
  it("runs the gate for questions about BOAR or addressed to it (EN, PT, ES)", () => {
    for (const q of [
      "What is BOAR?",
      "What can you do?",
      "Who are you?",
      "Where do your answers come from?",
      "Does this work without internet?",
      "What does the app know about Paris?",
      "Who made this assistant?",
      "How does it work?",
      "Is my data private?",
      "What is in your library?",
      "O que você sabe sobre Roma?",
      "Qual é a sua fonte?",
      "Quem é o seu criador?",
      "¿Qué eres tú?",
      "quem te fez?",
      "como eu apago o histórico da conversa?",
      "e aí, tudo bem?",
      "boa noite! o que causa as marés?",
      "Hi! What is the capital of Australia?",
      "thanks, and what is a rollup?",
      "how do I clear this chat?",
    ])
      expect(routeGateSkip({ ...base, query: q }), q).toBe(false);
  });
});
