import { describe, expect, it } from "vitest";
import { greatCircleKm, lookupCity, travelDistanceAnswer } from "./travelDistance";

describe("travelDistanceAnswer (EN, estimate from the bundled city table)", () => {
  it("answers distance, round trip and walking time, always as an estimate", () => {
    const r = travelDistanceAnswer("How far is it to walk from Paris to Rome and back?")!;
    expect(r).not.toBeNull();
    expect(r.mode).toBe("walk");
    expect(r.roundTrip).toBe(true);
    expect(r.straightKm).toBeGreaterThan(1090);
    expect(r.straightKm).toBeLessThan(1120);
    expect(r.text).toMatch(/estimate/);
    expect(r.text).toMatch(/there and back/);
    expect(r.text).toMatch(/days each way/);
  });

  it("covers the usual shapes, any city pair in the table", () => {
    for (const q of [
      "how far is it from Lisbon to Madrid?",
      "How far is Kyoto from Tokyo?",
      "What's the distance between Madrid and Barcelona?",
      "distance from Oslo to Bergen",
      "how many km from Bangkok to Chiang Mai by car?",
      "How long would it take to walk from Berlin to Prague?",
      "how many days to bike from Vienna to Budapest and back",
      "How far is it to drive from Los Angeles to San Francisco?",
    ]) {
      const r = travelDistanceAnswer(q);
      expect(r, q).not.toBeNull();
      expect(r!.text, q).toMatch(/estimate/);
    }
  });

  it("a time question leads with the time", () => {
    expect(travelDistanceAnswer("How long would it take to walk from Berlin to Prague?")!.text).toMatch(/^About \d+ days each way/);
  });

  it("driving and cycling only state the factor, never a mapped road distance", () => {
    const d = travelDistanceAnswer("How far is it to drive from Los Angeles to San Francisco?")!;
    expect(d.text).toMatch(/usually about 25% longer/);
    expect(d.text).toMatch(/not a mapped route/);
  });

  it("falls back (null) when a place isn't a city in the table, or isn't a travel question", () => {
    for (const q of [
      "How far is the Moon from the Earth?",
      "how far is it from my hotel to the beach?",
      "distance from Narnia to Mordor",
      "How far can a person walk in a day?",
      "How far is it to walk from Paris to Paris?",
      "Why is Rome far from Paris culturally?",
    ]) expect(travelDistanceAnswer(q), q).toBeNull();
  });

  it("resolves the most populous city of a name, with or without a country", () => {
    expect(lookupCity("Paris")!.country).toBe("FR");
    expect(lookupCity("Paris, France")!.country).toBe("FR");
    expect(Math.round(greatCircleKm({ lat: 0, lon: 0 }, { lat: 0, lon: 1 }))).toBe(111);
  });
});
