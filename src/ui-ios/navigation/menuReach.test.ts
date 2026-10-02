import { describe, expect, it } from "vitest";
import { touchSlop } from "../components/touchTarget";

// ChatHeader geometry (tokens: headerDisc 42, item gap sm + xxs = 10). Kept literal:
// tokens.ts imports react-native, which vitest can't load; a drift here only weakens the check.
const DISC = 42;
const GAP = 10;

describe("chat header menu stays reachable at large text (Harbor: 'menu tap did not open the drawer' at AX)", () => {
  for (const touch of [44, 48]) {
    it(`hit area reaches ${touch} pt and never meets the next item`, () => {
      const slop = touchSlop(DISC, touch);
      expect(DISC + 2 * slop).toBeGreaterThanOrEqual(touch);
      // The menu is the row's first child with a fixed width; the next item starts GAP after it.
      expect(slop).toBeLessThan(GAP);
    });
  }
});
