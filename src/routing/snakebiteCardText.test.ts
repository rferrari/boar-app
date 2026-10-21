import { describe, it, expect } from "vitest";
import { snakebiteFirstAidCard } from "./firstAidCards";
import { conditionCardText } from "./conditionCards";
import { SNAKEBITE_EN, SNAKEBITE_SOURCES_EN, withReviewedSnakebiteText } from "./snakebiteCardText";

describe("SBt: reviewed EN snakebite text on cut 2's card (trigger and card code unchanged)", () => {
  it("the EN card (fast path and health-route condition card) gets the reviewed body and sources; the emergency line stays", () => {
    for (const card of [snakebiteFirstAidCard("snake bit my friend in Thailand, what do I do", false), conditionCardText("snakebite", "snake bit me", "en")!]) {
      const out = withReviewedSnakebiteText(card);
      expect(out.startsWith(`${SNAKEBITE_EN}\n\n${SNAKEBITE_SOURCES_EN}\n\n`)).toBe(true);
      expect(out.endsWith(card.slice(card.lastIndexOf("\n\n")))).toBe(true);
      expect(out).toMatch(/Call emergency services now/);
      expect(out).toMatch(/on their side/);
      expect(out).toMatch(/start CPR/);
      expect(out).not.toMatch(/\b(immobiliz\w*|heart|wash\w*)\b|tight tourniquet/i);
    }
  });
  it("PT, ES, other cards and null pass through unchanged", () => {
    const pt = snakebiteFirstAidCard("fui picado por uma cobra, o que faço?", true);
    const es = snakebiteFirstAidCard("me mordió una serpiente, qué hago", false, true);
    const burn = conditionCardText("burn", "I burned my hand on the stove", "en");
    expect(withReviewedSnakebiteText(pt)).toBe(pt);
    expect(withReviewedSnakebiteText(es)).toBe(es);
    expect(withReviewedSnakebiteText(burn)).toBe(burn);
    expect(withReviewedSnakebiteText(null)).toBeNull();
  });
});
