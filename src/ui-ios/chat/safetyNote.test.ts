import { describe, it, expect } from "vitest";
import { answerShowsEmergencyNote, isHealthQuestion, needsEmergencyNote, showsEmergencyNote, usesPreparednessPack } from "./safetyNote";

const prep = { chunkId: "pack:boar-preparedness:123" };
const wiki = { chunkId: "pack:enwiki:9" };

describe("needsEmergencyNote (Boar E-1)", () => {
  it("shows the note when a passage comes from the Emergency and preparedness pack", () => {
    expect(usesPreparednessPack([wiki, prep])).toBe(true);
    expect(needsEmergencyNote("How do I purify water?", [prep])).toBe(true);
  });

  it("shows it for health and first-aid questions, in EN and PT, whatever the sources", () => {
    for (const q of [
      "How do I stop a nosebleed?",
      "What to do for a burn",
      "someone is choking",
      "Como estancar um sangramento nasal?",
      "o que fazer numa queimadura",
      "sintomas de infarto",
      "sharp pain in my arm",
      "dor de cabeça forte",
    ]) {
      expect(isHealthQuestion(q)).toBe(true);
      expect(needsEmergencyNote(q, [wiki])).toBe(true);
    }
  });

  it("stays off for everyday questions", () => {
    for (const q of ["Why do we have seasons on Earth?", "What is 30 °C in Fahrenheit?", "Compare Raft and Paxos", "vegan restaurants in Berlin", "vegan places in Spain", "a painting by Goya", "Doral is a city", "How does a firewall work?", "raio-x do pulmão é seguro?", "Lost in Translation plot", "Why do earthquakes happen near plate boundaries?", "Por que os terremotos acontecem perto das bordas das placas?", "What causes hurricanes?", "How to configure a firewall"]) {
      expect(needsEmergencyNote(q, [wiki])).toBe(false);
    }
  });
});

describe("showsEmergencyNote (EQ-1)", () => {
  const base = { question: "What should I do during an earthquake?", sources: [wiki], placesOnly: false };

  it("shows for a safety answer made only of the instant passage (no model text)", () => {
    expect(showsEmergencyNote({ ...base, hasModelText: false, hasSnippet: true })).toBe(true);
  });

  it("shows for a safety answer with model text", () => {
    expect(showsEmergencyNote({ ...base, hasModelText: true, hasSnippet: false })).toBe(true);
  });

  it("shows when a passage comes from the preparedness pack by article id", () => {
    expect(
      showsEmergencyNote({ question: "tell me about Walipini", sources: [{ chunkId: "c1", docId: "pack:boar-preparedness:a3" }], hasModelText: true, hasSnippet: false, placesOnly: false })
    ).toBe(true);
  });

  it("stays off before anything is on screen, for places lists, and for everyday questions", () => {
    expect(showsEmergencyNote({ ...base, hasModelText: false, hasSnippet: false })).toBe(false);
    expect(showsEmergencyNote({ ...base, hasModelText: true, hasSnippet: false, placesOnly: true })).toBe(false);
    expect(showsEmergencyNote({ question: "Compare Raft and Paxos", sources: [wiki], hasModelText: true, hasSnippet: true, placesOnly: false })).toBe(false);
  });

  it("recognises disasters in EN and PT", () => {
    for (const q of ["What should I do during an earthquake?", "how to evacuate a flood", "O que fazer num terremoto?", "como agir em uma enchente", "incêndio no prédio", "there is a fire in the kitchen"]) {
      expect(isHealthQuestion(q)).toBe(true);
    }
  });
});

describe("engine safety flag (Tusk 2e88300)", () => {
  it("shows the note when the engine marked the answer safety, whatever the wording", () => {
    expect(showsEmergencyNote({ question: "hmm", sources: [], hasModelText: false, hasSnippet: true, placesOnly: false, safety: true })).toBe(true);
  });
});

// The engine's isSafetyQuery has the action-cue rule since Tusk 66b73c6.
describe("disasters need an action cue (Tusk 66b73c6)", () => {
  it("stays off for science questions about disasters", () => {
    for (const q of ["Why do earthquakes happen near plate boundaries?", "Por que os terremotos acontecem perto das bordas das placas?", "What causes hurricanes?", "How to configure a firewall"]) {
      expect(needsEmergencyNote(q, [wiki])).toBe(false);
    }
  });
});

describe("answerShowsEmergencyNote (Prism NB-1)", () => {
  const src = [{ chunkId: "wiki:Nosebleed#0", docId: "wiki:Nosebleed" }];
  it("shows the note for the engine's literal health excerpt, with no model text and no snippet", () => {
    expect(answerShowsEmergencyNote({ sources: src, extract: "Pinch the nose. [1]", safety: true }, "Meu nariz esta sangrando, o que eu faco?", false)).toBe(true);
  });
  it("not before anything is on screen, and not for a places list", () => {
    expect(answerShowsEmergencyNote({ sources: src, safety: true }, "nosebleed", false)).toBe(false);
    expect(answerShowsEmergencyNote({ sources: src, extract: "x", safety: true }, "nosebleed", true)).toBe(false);
  });
});
