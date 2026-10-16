import { describe, it, expect } from "vitest";
import { conditionCardText, pickCondition, regexCondition } from "./conditionCards";
import { noHealthSourceAnswer } from "./context";

describe("first-aid condition cards v0.1 (Compass, FROZEN e0730060…)", () => {
  it("term regexes: one condition, several (MULTI), none; EN and PT", () => {
    expect(regexCondition("We think there is carbon monoxide in the cabin")).toBe("co");
    expect(regexCondition("Acho que tem monóxido de carbono no quarto")).toBe("co");
    expect(regexCondition("My son spilled boiling water on his leg")).toBe("burn");
    expect(regexCondition("Ela está com dor no peito e a boca torta")).toBe("MULTI");
    expect(regexCondition("What is the capital of Australia?")).toBeNull();
  });
  it("hypothermia needs cold exposure (a shaking, confused diabetic is not hypothermia)", () => {
    expect(regexCondition("He is shaking, sweaty and confused after skipping lunch")).toBeNull();
    expect(regexCondition("He is shivering and confused after falling in the icy lake")).toBe("hypothermia");
  });
  it("Unicode boundaries as in Python: 'co' alone or inside a word is not CO", () => {
    // Not CO; since Compass round 2 it is a head injury (a coconut falling on a head is one).
    expect(regexCondition("The coconut fell on my head")).not.toBe("co");
    expect(regexCondition("co poisoning from the stove?")).toBe("co");
  });
  it("trigger v0.1: the regex's unique condition only; MULTI or none → no specific card (no embedding fallback)", () => {
    expect(pickCondition("We think there is carbon monoxide in the cabin")).toBe("co");
    expect(pickCondition("Something bad happened in the kitchen")).toBeNull();
    expect(pickCondition("Ela está com dor no peito e a boca torta")).toBeNull();
  });
  it("v0.1 bluebottle: a bluebottle sting soaked in hot water is jellyfish + burn = MULTI, not the burn card", () => {
    expect(regexCondition("A bluebottle stung me, should I put hot water on it?")).toBe("MULTI");
    expect(regexCondition("I was stung by a blue bottle at the beach")).toBe("jellyfish");
  });
  it("card text: must-do first, sources, then the emergency line; snakebite = the WHO card", () => {
    const co = conditionCardText("co", "carbon monoxide at home", false)!;
    expect(co).toMatch(/^Possible carbon monoxide poisoning: get everyone out into fresh air now/);
    expect(co).toMatch(/\n\nSource: NHS, Carbon monoxide poisoning/);
    expect(co).toMatch(/emergency/i);
    expect(conditionCardText("co", "monóxido de carbono em casa", true)).toMatch(/^Possível intoxicação por monóxido de carbono/);
    expect(conditionCardText("snakebite", "snake bit me", false)).toMatch(/^Snakebite, first aid/);
    expect(conditionCardText("unknown", "x", false)).toBeNull();
    expect(conditionCardText("co", "x", false)).not.toBe(noHealthSourceAnswer(false));
  });
});
