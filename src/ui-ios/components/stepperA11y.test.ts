import { describe, expect, it } from "vitest";
import { stepperValue } from "./stepperA11y";

describe("stepperValue", () => {
  it("is text, never a numeric range (iOS would speak a percentage)", () => {
    const v = stepperValue(4, 1);
    expect(v).toEqual({ text: "2/4" });
    expect(v).not.toHaveProperty("min");
    expect(v).not.toHaveProperty("now");
  });

  it("clamps an out-of-range index", () => {
    expect(stepperValue(4, 7)).toEqual({ text: "4/4" });
    expect(stepperValue(4, -1)).toEqual({ text: "1/4" });
  });
});
