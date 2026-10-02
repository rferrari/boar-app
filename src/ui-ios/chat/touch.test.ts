import { describe, it, expect } from "vitest";
import { lineSlop } from "./touch";

describe("lineSlop (Prism CH-5/CH-6: text controls reach 44/48)", () => {
  it("a caption line (16) reaches 44 on iOS and 48 on Android", () => {
    const ios = lineSlop(44, 16, 8);
    expect(16 + ios.top + ios.bottom).toBe(44);
    const android = lineSlop(48, 16, 8);
    expect(16 + android.top + android.bottom).toBe(48);
    expect([android.left, android.right]).toEqual([8, 8]);
  });
  it("adds nothing when the line is already tall enough (large text)", () => {
    expect(lineSlop(48, 60, 8)).toEqual({ top: 0, bottom: 0, left: 8, right: 8 });
  });
});
