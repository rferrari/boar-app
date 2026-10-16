import { describe, it, expect } from "vitest";
import { definesName, isHealthFollowUp, isHealthQuestion, isPtDefinitional, riskyHealthInstruction } from "./context";

describe("Sonar exp-29: health detection gaps (knob 1)", () => {
  it("should-questions next to a described injury or a disaster, frostbitten, collapsed", () => {
    for (const q of [
      "I burned my hand on a hot pan. Should I put ice or butter on it?",
      "An earthquake just started and I'm indoors. Should I stand in a doorway or run outside?",
      "My toes look frostbitten after a cold hike. Should I rub them or warm them by the fire?",
      "My friend collapsed in the heat, is confused and very hot. What should I do right now?",
    ]) expect(isHealthQuestion(q), q).toBe(true);
  });
  it("not a trip being planned", () => {
    for (const q of [
      "I'm going to Chiang Mai in March. I heard about the burning season — should I bring masks?",
      "How common are earthquakes in Wellington? Should I worry?",
    ]) expect(isHealthQuestion(q), q).toBe(false);
  });
  it("a coastal quake is a health (tsunami) question now that the tsunami card exists", () => {
    expect(isHealthQuestion("We're on the beach in Kamakura and there was a really strong, long earthquake. Should we wait for a tsunami warning before leaving?")).toBe(true);
    expect(isHealthQuestion("dor no peito de saudade")).toBe(false);
  });
});

describe("Sonar exp-29: health follow-ups (knob 2)", () => {
  it("a bare what-to-do or how-serious turn after a health turn", () => {
    expect(isHealthFollowUp("what do I do now?", ["My son was stung by a scorpion"])).toBe(true);
    expect(isHealthFollowUp("e agora?", ["Minha filha engoliu uma bateria"])).toBe(true);
    expect(isHealthFollowUp("é grave?", ["Escorpião picou meu filho"])).toBe(true);
    expect(isHealthFollowUp("o que eu faço agora?", ["meu filho caiu e bateu a cabeça e está vomitando"])).toBe(true);
  });
  it("a new question, or no health turn before", () => {
    expect(isHealthFollowUp("thanks! what's the capital of Peru?", ["My son was stung by a scorpion"])).toBe(false);
    expect(isHealthFollowUp("Is any vegan restaurant near me open right now?", ["My son was stung by a scorpion"])).toBe(false);
    expect(isHealthFollowUp("what do I do now?", ["What is the capital of Peru?"])).toBe(false);
  });
});

describe("Sonar exp-29: PT definitional instants (knob 3)", () => {
  it("one-subject definition questions only", () => {
    expect(isPtDefinitional("O que é uma monção?")).toBe(true);
    expect(isPtDefinitional("Quem foi Oswaldo Cruz e por que aconteceu a Revolta da Vacina?")).toBe(false);
    expect(isPtDefinitional("qual a melhor epoca pra ir nos lencois maranhenses")).toBe(false);
  });
  it("the sentence must define the name", () => {
    expect(definesName(["Monsoon"], "A monsoon is traditionally a seasonal reversing wind.")).toBe(true);
    expect(definesName(["Vitalik Buterin"], "- Why Proof of Stake (Nov 2020) _Vitalik Buterin_")).toBe(false);
    expect(definesName(["India"], "Atheism is not taboo in India.")).toBe(false);
  });
});

describe("Sonar exp-29: chest pain and the hypothermia exertion excerpt (B)", () => {
  it("chest pain is a health question", () => {
    for (const q of ["my dad has chest pain", "Meu pai está com dor no peito e suando frio", "mi madre tiene dolor en el pecho"]) expect(isHealthQuestion(q), q).toBe(true);
    expect(isHealthQuestion("chest press machine workout")).toBe(false);
  });
  it("'keep walking to stay warm' is not first aid for hypothermia", () => {
    expect(riskyHealthInstruction("Keeping on walking even when tired is important, not only to reach shelter, but also to keep warm.")).toBe("hypo-exertion");
    expect(riskyHealthInstruction("Get them out of the wind and keep them warm with dry clothes.")).toBe(null);
  });
});
