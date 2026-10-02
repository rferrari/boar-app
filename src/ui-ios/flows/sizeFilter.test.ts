import { describe, it, expect } from "vitest";
import { passesSizeFilter } from "./sizeFilter";

const GB = 1024 ** 3;

describe("passesSizeFilter", () => {
  it("keeps files up to the chosen size", () => {
    expect(passesSizeFilter(1.9 * GB, "2", 0)).toBe(true);
    expect(passesSizeFilter(2.1 * GB, "2", 0)).toBe(false);
    expect(passesSizeFilter(3.9 * GB, "4", 0)).toBe(true);
  });

  it("'fits' leaves a fifth of the phone's model budget for context and buffers", () => {
    // A 12 GB phone's budget is 9 GB: files up to 7.2 GB fit.
    expect(passesSizeFilter(7 * GB, "fits", 9 * GB)).toBe(true);
    expect(passesSizeFilter(8 * GB, "fits", 9 * GB)).toBe(false);
  });

  it("shows everything when the size or the budget is unknown", () => {
    expect(passesSizeFilter(0, "2", 9 * GB)).toBe(true);
    expect(passesSizeFilter(50 * GB, "fits", 0)).toBe(true);
    expect(passesSizeFilter(50 * GB, "any", 9 * GB)).toBe(true);
  });
});
