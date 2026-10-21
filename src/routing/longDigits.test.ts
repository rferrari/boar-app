import { describe, it, expect } from "vitest";
import { calculate } from "./calculators";
import { classifyTask } from "./classify";
import { temperatureConversion } from "./context";
import { numericGuard, numericIntent } from "./numguard";

// A pasted run of digits made four unanchored number patterns restart at every digit: quadratic time,
// about 0.7 s at 8,000 digits on a desktop and minutes on a phone. A number now cannot start inside
// another number. The bound is loose on purpose (a busy CI runner); the quadratic version took seconds at this length.
describe("long digit runs stay linear", () => {
  const run = "1".repeat(20000);
  const queries = [`what is ${run} + 2`, `${run} °C in fahrenheit`, `how far is ${run} km and back by car`];
  const fns: Array<[string, (q: string) => unknown]> = [
    ["calculate", (q) => calculate(q, false)],
    ["temperatureConversion", (q) => temperatureConversion(q, false)],
    ["classifyTask", (q) => classifyTask(q)],
    ["numericIntent", (q) => numericIntent(q)],
    ["numericGuard", (q) => numericGuard(q, false)],
  ];
  for (const [name, fn] of fns) {
    it(`${name} answers a 20,000-digit question in under 1,000 ms`, () => {
      for (const q of queries) {
        const t = performance.now();
        fn(q);
        expect(performance.now() - t).toBeLessThan(1000);
      }
    });
  }
  it("still reads the numbers it read before", () => {
    expect(temperatureConversion("What is 30 °C in Fahrenheit?", false)).toMatch(/30 °C = 86 °F/);
    expect(classifyTask("what is 12 * 7")).toBe("calculate");
    expect(numericIntent("2.2 bar in psi?")).toBe(true);
  });
});
