import { describe, expect, it } from "vitest";
import { segmentsFit } from "./segmentFit";

const base = { width: 353, count: 3, chrome: 16 + 22, gap: 3 };

describe("segmentsFit", () => {
  it("keeps 'Smaller | Standard | Larger' in a row at 1.0", () => {
    expect(segmentsFit({ ...base, longestLabel: 8, fontSize: 14 })).toBe(true);
  });

  it("stacks them at Android's 1.3 before 'Standard' can break", () => {
    expect(segmentsFit({ ...base, longestLabel: 8, fontSize: 14 * 1.3 })).toBe(false);
  });

  it("renders a row until the width is known", () => {
    expect(segmentsFit({ ...base, width: 0, longestLabel: 8, fontSize: 30 })).toBe(true);
  });
});
