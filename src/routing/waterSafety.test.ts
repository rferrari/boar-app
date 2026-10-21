import { describe, it, expect } from "vitest";
import { isWaterSafetyQuestion, waterSafetyAnswer } from "./waterSafety";
import { WATER_SAFETY_EN, WATER_SAFETY_SOURCES_EN } from "./waterSafety";

describe("emergency drinking-water card", () => {
  it("fires on making drinking water safe after a flood, storm, outage or boil notice", () => {
    for (const q of [
      "After a flood the tap water might be contaminated. How do I make water safe to drink?",
      "how long do i need to boil water after the flood to make it safe to drink",
      "how much bleach per gallon to disinfect drinking water? hurricane is coming",
      "We got a boil water notice from the city this morning. Is boiling enough?",
      "my private well was under water during the flood. how do i make it drinkable again",
      "power's been out 3 days and a water main broke. can i drink the tap water or not",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(true);
  });
  it("does not fire on lookalikes", () => {
    for (const q of [
      "how much water should I drink per day, 2 liters?",
      "is the sea safe to swim in after the storm, lots of runoff near the beach",
      "how long do i boil an egg for a soft yolk",
      "how much bleach to get red wine stains out of white laundry",
      "is it safe to drive through flood water if it's only a few inches deep",
      "how do I clean mold off drywall after a flood",
      "what should the free chlorine level in my pool be",
      "Is tap water safe to drink in Lisbon?",
      "Can I run my portable generator in the attached garage during the outage?",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(false);
  });
  it("the card is the shared text plus its sources line", () => {
    expect(waterSafetyAnswer()).toBe(`${WATER_SAFETY_EN}\n\n${WATER_SAFETY_SOURCES_EN}`);
    expect(waterSafetyAnswer()).toMatch(/rolling boil for 1 minute/);
    expect(waterSafetyAnswer()).toMatch(/8 drops per gallon/);
    expect(waterSafetyAnswer()).toMatch(/3 minutes if you are above 1,000 metres/);
    expect(waterSafetyAnswer()).toMatch(/for 1% bleach, add 10 drops per litre or 40 drops per gallon/);
    expect(waterSafetyAnswer()).toMatch(/smell slightly of chlorine/);
    expect(waterSafetyAnswer()).toMatch(/health department/);
    expect(waterSafetyAnswer()).toMatch(/Sources: CDC, How to Make Water Safe in an Emergency; US EPA, Emergency Disinfection of Drinking Water\.$/);
    // Not an acute-harm card: no emergency-numbers line.
    expect(waterSafetyAnswer()).not.toMatch(/emergency services|call 911|112/i);
  });
});

describe("emergency drinking-water card: faucets, rain barrels, wildfire, pool shock", () => {
  it("fires on a faucet / do-not-drink notice, rain barrel, wildfire contamination, coliform, broken pipes, pool shock", () => {
    for (const q of [
      "do not drink notice for our street. how do I treat what comes out of the faucet",
      "the rain barrel got storm debris in it, can I make that safe to drink",
      "wildfire damaged the town's system and there may be benzene, can boiling make the water drinkable?",
      "coliform bacteria found in the city tap water, how do we make it safe for the family to drink?",
      "stuck in a shelter after the earthquake, water from the broken pipes is cloudy, how do I make it safe for the kids?",
      "can I use pool shock to disinfect drinking water after the flood?",
      "camping and the only source is a creek after a flash flood upstream, how do i disinfect it before drinking",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(true);
  });
  it("still not on pools, hot tubs, ice machines, water bills or plumbing", () => {
    for (const q of [
      "what should the free chlorine level in my pool be after the storm",
      "how much bleach for the hot tub after the power outage",
      "is the ice machine water safe after the outage, how do I clean it",
      "my water bill doubled after the flood, is that normal",
      "leaky pipes after the earthquake, do I need a plumber before I can drink the tap water",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(false);
  });
});

describe("emergency drinking-water card: a present need only", () => {
  it("never history, how-it-works or routine backcountry filtering", () => {
    for (const q of [
      "when did cities start issuing boil water advisories after contamination?",
      "history of boil-water notices in the US, and why are they issued after floods",
      "how do municipal treatment plants remove bacteria so tap water is safe to drink",
      "how does a water utility disinfect contaminated water with chlorine",
      "best way to filter stream water when backpacking, is boiling enough?",
      "is creek water safe to drink on a hike if I boil it",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(false);
  });
  it("still fires on a present need, terse or imperative", () => {
    for (const q of [
      "purify stream water in an emergency, no filter, only a pot and a lighter",
      "hurricane knocked out our water treatment plant, how many drops of bleach per gallon to make tap water drinkable",
      "Camping when the hurricane hit, only have lake water. What's the safest way to get it drinkable?",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(true);
  });
});

describe("emergency drinking-water card: public bench and self-test phrasings", () => {
  it("fires on emergency water-treatment asks", () => {
    for (const q of [
      "The power is out after the storm and the city issued a boil water notice. How long do I boil tap water before drinking it?",
      "Our well flooded during the hurricane. Can we drink the water if we boil it?",
      "how many drops of bleach per litre to make flood water safe to drink",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(true);
  });
  it("does not fire on travel, how-much-to-drink, cooking, pools or other hazards", () => {
    for (const q of [
      "Is it safe to drink tap water in Mexico City?",
      "How much water should I carry for a 20 km desert hike?",
      "Water is rising in my street right now and starting to come into the yard. What do I do?",
      "My toddler just drank some cleaning product from under the sink. What do I do?",
      "My child spilled boiling water on their arm. What do I do?",
      "how long do I boil pasta at high altitude",
    ])
      expect(isWaterSafetyQuestion(q), q).toBe(false);
  });
  it("known miss: no make-safe word (boil, bleach, treat, safe to drink...) leaves the question on the health route", () => {
    expect(isWaterSafetyQuestion("After a flood the tap water might be contaminated. What should I do about drinking water?")).toBe(false);
  });
});
