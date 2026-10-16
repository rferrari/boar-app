import { describe, expect, it } from "vitest";
import { coHazardAnswer, isCoExposure, isCoHazardQuestion } from "./coHazard";
import { conversationCondition } from "./conditionCards";

describe("isCoHazardQuestion (EN)", () => {
  it("fires on a fuel-burning device in an enclosed or attached space", () => {
    for (const q of [
      "Can I run my portable generator in the attached garage if I keep the door open?",
      "is it ok to use a charcoal grill inside the tent if it's raining",
      "We're camping and it's freezing. Can we bring the propane heater into the camper overnight?",
      "Can I use my Mr. Heater in the basement while I work?",
      "Is it safe to use a camping stove inside the tent to cook?",
      "Power's out. Can I run the generator in the basement with a window cracked?",
      "Can I warm up my car in the garage with the door closed for a few minutes?",
      "Is it safe to use the gas oven to heat the apartment during the outage?",
      "Can I use a disposable bbq in my tent if I leave the flap open?",
      "Can we run a kerosene heater in our ice fishing shack?",
      "I have a generator. Can I keep it running in the shed during the storm?",
      "Can I put the generator in the carport?",
      "Running a pressure washer in an enclosed porch, is that ok?",
    ])
      expect(isCoHazardQuestion(q), q).toBe(true);
  });

  it("stays out of symptoms, alarms, electric devices, figurative and non-space mentions", () => {
    for (const q of [
      "We ran the generator in the garage and now we all have headaches",
      "The CO alarm is beeping in the basement, what do I do?",
      "Can I use an electric heater in the tent?",
      "Can I use my grill in the apartment?",
      "Write a random number generator inside a function in Python",
      "Best charcoal drawing techniques indoors",
      "How many watts does a generator need to run a fridge?",
      "What is a static site generator?",
      "Where can I buy activated charcoal inside the airport?",
      "Is it safe to run a portable generator in the rain?",
      "Garage sale tips: how do I price an old generator?",
      "How do I cook rice on a gas stove?",
      "Is it safe to sleep in a tent in bear country?",
    ])
      expect(isCoHazardQuestion(q), q).toBe(false);
  });
});

describe("reviewer C1/C2 (2026-10-03)", () => {
  it("installed or vented appliances and a propane kitchen range do not fire; a vented generator still does", () => {
    for (const q of [
      "Is it safe to have a gas water heater in the basement?",
      "Is it safe to have a gas water heater in the garage?",
      "Can I use my vented gas heater in the living room?",
      "Is it safe to cook on my propane stove in the kitchen?",
    ])
      expect(isCoHazardQuestion(q), q).toBe(false);
    expect(isCoHazardQuestion("Can I run the generator in the basement if it's vented?")).toBe(true);
  });

  it("never leads with No. on risk questions, alternatives or safe setups", () => {
    for (const q of [
      "Is it safe to run my generator outside 25 ft from the house?",
      "Is it safe to run my generator 6 m from the window?",
      "Can you die from running a generator in the garage?",
      "Can you get CO poisoning from a camping stove in a tent?",
      "Could we get poisoned if we run the generator in the basement?",
      "Is it dangerous to run a generator in the garage?",
      "Is it bad to run a generator in the garage?",
      "Should we move the generator out of the garage?",
      "Should I be concerned about running the generator in the garage overnight?",
      "Should I worry about running the generator in the garage?",
      "Can I keep the generator outside instead of in the garage?",
      "Should I run the generator outside or in the garage?",
      "Can I use my camping stove outside the tent?",
      "Is it ok to move the camping stove out of the tent?",
      "Is it safe to run my generator in the driveway, 25 feet from the garage?",
      "Can I run my generator 30 feet from the garage door?",
      "Is it safe to have a gas water heater in the basement?",
      "Can I use my vented gas heater in the living room?",
      "Is it safe to cook on my propane stove in the kitchen?",
    ])
      // Production property: the answer is only shown when the trigger fires; then it must not start with "No.".
      expect(isCoHazardQuestion(q) && /^No\./.test(coHazardAnswer(q)), q).toBe(false);
    expect(coHazardAnswer("Can I run my portable generator in the attached garage if I keep the door open?")).toMatch(/^No\. Carbon monoxide/);
  });
});

describe("follow-up: appliance names in passing, propane stoves outside the kitchen", () => {
  it("an installed appliance named in passing no longer vetoes; a kitchen range (home or RV) still does not fire", () => {
    for (const q of [
      "we're boondocking in an old class C, how do I safely use the propain catalytic heater while we sleep? the furnace eats the battery",
      "can I cook on my butane stove inside the van with the sliding door shut when its raining",
      "doin a crawfish boil and it's stormin, can we run the propane burner inside the shop with the roll up door cracked a couple feet",
      "Can I put a propane heater next to the furnace in the basement?",
    ])
      expect(isCoHazardQuestion(q), q).toBe(true);
    for (const q of ["Can I light the propane stove in my RV kitchen?", "Is it safe to use a direct-vent propane heater in the bedroom?"])
      expect(isCoHazardQuestion(q), q).toBe(false);
  });
});

describe("CO exposure with symptoms -> the health route's CO card (Forge shipcut1 gap)", () => {
  const probe = "Since we started the generator in the garage my head hurts and I feel dizzy and sick. What should I do?";
  it("symptoms or an alarm with the device context is exposure, not the planning answer", () => {
    for (const q of [
      probe,
      "We ran the generator in the garage and now we all have headaches",
      "the alarm next to the propane heater in our camper keeps beeping and my son threw up",
      "been using the charcoal grill in the tent for warmth and now im super dizzy and confused",
    ]) {
      expect(isCoExposure(q), q).toBe(true);
      expect(isCoHazardQuestion(q), q).toBe(false);
    }
    for (const q of ["my whole family has a headache and the heater is on, what now?", "the alarm thing next to the heater in our cabin keeps beeping and all 3 of us have headaches now, my son just threw up"])
      expect(isCoExposure(q), q).toBe(true);
    for (const q of [
      "I have a headache and feel dizzy after the flight",
      "Can I run my generator in the garage?",
      "headache after a long drive",
      "I got a headache in the garage while fixing the car",
      "I have a headache, is the heater bad for me?",
      "my garage heater is broken",
    ])
      expect(isCoExposure(q), q).toBe(false);
  });
  it("names the co condition, alone or from history, and CO's own symptoms don't make it MULTI", () => {
    expect(conversationCondition(probe).condition).toBe("co");
    // The device in the immediately previous user turn, symptoms now: co (Sonar exp-37 follow-up c).
    expect(conversationCondition("now my head is pounding and I feel sick, what do I do", ["we've had the generator running in the garage all evening"]).condition).toBe("co");
    expect(conversationCondition("we ran the generator in the garage, now my head is pounding and I feel sick", ["hi"]).condition).toBe("co");
    expect(conversationCondition("propane heater in the ice shack, my buddy has chest pain and is confused").condition).toBe("co");
  });
});

describe("CO exposure follow-ups (Sonar exp-37): CO alarm, engine exhaust, the previous turn", () => {
  it("a CO alarm going off is exposure; a chirp alone is not", () => {
    expect(conversationCondition("co detector beeping and we all have headaches").condition).toBe("co");
    expect(conversationCondition("my carbon monoxide alarm is going off in the basement").condition).toBe("co");
    expect(conversationCondition("my co detector keeps chirping every minute, how do I change the battery?").condition).not.toBe("co");
  });
  it("engine running or engine exhaust in a closed space with symptoms", () => {
    expect(conversationCondition("found my brother in his car in the closed garage with the engine running. hes breathing but barely waking up").condition).toBe("co");
    expect(conversationCondition("on the boat, my uncle was napping in the cabin near the engine exhaust, now hes dizzy and slurring").condition).toBe("co");
  });
  it("the device (or a CO alarm) in the immediately previous user turn, symptoms now", () => {
    expect(conversationCondition("now my kids are complaining of headaches and feel sick", ["power is out, we have the generator running in the basement"]).condition).toBe("co");
    expect(conversationCondition("we're outside now but my dad is still really confused and weak, what now?", ["the CO detector went off at 3am and we all got out"]).condition).toBe("co");
    // Only the immediately previous turn, and only with symptoms now.
    expect(conversationCondition("now my kids have headaches", ["we have the generator running in the basement", "ok thanks"]).condition).not.toBe("co");
    expect(conversationCondition("how long can it run on one tank?", ["we have the generator running in the basement"]).condition).not.toBe("co");
    // exposure2.1 (Sonar probes2): nothing running in the previous turn, or a planning question, never carries over.
    expect(conversationCondition("unrelated, but I have a headache from stress, what can I take?", ["what size generator do I need for my garage workshop?"]).condition).not.toBe("co");
    expect(conversationCondition("my daughter feels sick and has a fever, could be the flu?", ["we run the generator outside the house"]).condition).not.toBe("co");
    expect(conversationCondition("now I feel dizzy and sick", ["can we keep the generator running in the garage tonight?"]).condition).not.toBe("co");
    expect(conversationCondition("now I feel dizzy and sick", ["the generator has been running in the garage all night"]).condition).toBe("co");
    expect(conversationCondition("my son is barely waking up", ["my car engine was running in the closed garage for an hour"]).condition).toBe("co");
  });
});

describe("coHazardAnswer", () => {
  it("leads with No. on a yes/no planning question only, and ends with sources and the emergency line", () => {
    const yesNo = coHazardAnswer("Can I run my generator in the garage?");
    expect(yesNo).toMatch(/^No\. Carbon monoxide/);
    expect(yesNo).toMatch(/Sources: CDC/);
    expect(yesNo).toMatch(/emergency/i);
    expect(coHazardAnswer("Where should I put the generator, garage or outside?")).toMatch(/^Carbon monoxide/);
  });
});
