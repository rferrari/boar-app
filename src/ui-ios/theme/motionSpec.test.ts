import { describe, expect, it } from "vitest";
import { crossfadeSpec, DELAY, DURATION, enterScale, motionSpec, POP_SCALE, TRAVEL } from "./motionSpec";

describe("motionSpec (DS §6)", () => {
  it("enter is base 220 decelerate with travel; exit is fast 150 accelerate", () => {
    expect(motionSpec("enter", false)).toEqual({ duration: 220, curve: "enter", travel: TRAVEL });
    expect(motionSpec("exit", false)).toEqual({ duration: 150, curve: "exit", travel: TRAVEL });
  });

  it("change is fast 150 standard and never travels; layout is base 220 standard", () => {
    expect(motionSpec("change", false)).toEqual({ duration: 150, curve: "standard", travel: 0 });
    expect(motionSpec("layout", false)).toEqual({ duration: 220, curve: "standard", travel: 0 });
  });

  it("reduce motion: enter/exit become a 90 ms fade with no travel, change stays, layout is instant", () => {
    expect(motionSpec("enter", true)).toMatchObject({ duration: DURATION.instant, travel: 0 });
    expect(motionSpec("exit", true)).toMatchObject({ duration: DURATION.instant, travel: 0 });
    expect(motionSpec("change", true)).toEqual(motionSpec("change", false));
    expect(motionSpec("layout", true).duration).toBe(0);
  });

  it("a crossfade is 90 out, then 220 in after it", () => {
    const x = crossfadeSpec(false);
    expect(x.out.duration).toBe(90);
    expect(x.in).toMatchObject({ duration: 220, delay: 90 });
    expect(crossfadeSpec(true).in).toMatchObject({ duration: 90, delay: 90, travel: 0 });
  });

  it("a pop enter starts slightly small, and is a plain fade under reduce motion", () => {
    expect(POP_SCALE).toBeGreaterThan(0.8);
    expect(POP_SCALE).toBeLessThan(1);
    expect(enterScale(true, false)).toBe(POP_SCALE);
    expect(enterScale(true, true)).toBe(1);
    expect(enterScale(false, false)).toBe(1);
  });

  it("a skeleton waits 150 ms before it shows", () => {
    expect(DELAY.skeleton).toBe(150);
  });
});
