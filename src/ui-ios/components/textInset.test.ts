import { describe, expect, it } from "vitest";
import { cappedScale, iosTextInset } from "./textInset";

describe("iosTextInset", () => {
  it("pads the 40/40 wordmark down and gives the space back below", () => {
    const inset = iosTextInset("display", { fontSize: 40, lineHeight: 40 }, 1);
    expect(inset).not.toBeNull();
    expect(inset!.paddingTop).toBeGreaterThan(0);
    expect(inset!.marginBottom).toBe(-inset!.paddingTop);
  });

  it("adds to the caller's own padding and margin, most specific side first", () => {
    const bare = iosTextInset("display", { fontSize: 40, lineHeight: 40 }, 1)!;
    const own = iosTextInset("display", { fontSize: 40, lineHeight: 40, padding: 2, paddingTop: 4, margin: 1, marginBottom: -7 }, 1)!;
    expect(own.paddingTop).toBeCloseTo(4 + bare.paddingTop);
    expect(own.marginBottom).toBeCloseTo(-7 - bare.paddingTop);
    const vertical = iosTextInset("display", { fontSize: 40, lineHeight: 40, paddingVertical: 3, marginVertical: 5 }, 1)!;
    expect(vertical.paddingTop).toBeCloseTo(3 + bare.paddingTop);
    expect(vertical.marginBottom).toBeCloseTo(5 - bare.paddingTop);
  });

  it("scales with the OS text size, like React Native scales size and line height", () => {
    const one = iosTextInset("display", { fontSize: 40, lineHeight: 40 }, 1)!;
    const big = iosTextInset("display", { fontSize: 40, lineHeight: 40 }, 1.3)!;
    expect(big.paddingTop).toBeCloseTo(one.paddingTop * 1.3, 0);
  });

  it("leaves text alone when nothing clips or a side is not a number", () => {
    expect(iosTextInset("text", { fontSize: 16, lineHeight: 24 }, 1)).toBeNull();
    expect(iosTextInset("display", { fontSize: 40, lineHeight: 40, paddingTop: "10%" }, 1)).toBeNull();
    expect(iosTextInset("display", { lineHeight: 40 }, 1)).toBeNull();
  });
});

describe("cappedScale", () => {
  it("caps at maxFontSizeMultiplier >= 1, ignores 0, and is 1 without font scaling", () => {
    expect(cappedScale(2, 1.3)).toBe(1.3);
    expect(cappedScale(2, 0)).toBe(2);
    expect(cappedScale(2, undefined)).toBe(2);
    expect(cappedScale(2, 1.3, false)).toBe(1);
  });
});
