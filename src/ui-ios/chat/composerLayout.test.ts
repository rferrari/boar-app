import { describe, expect, it } from "vitest";
import { COMPOSER_MAX_LINES, composerLayout, composerPadEnd, composerPillHeight } from "./composerLayout";

// The DS numbers: pill 52 around a 20 pt line (subhead 14 × 1.43); the discs inside it are controlSm 36.
const base = { lineHeight: 20, composer: 52, button: 36 };

describe("composerLayout (r4to: the composer only fit one line)", () => {
  it("one line is the mockup's 52 pill, the disc centred on it", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    expect(l.padV).toBe(16);
    expect(composerPillHeight(20, l)).toBe(52);
    expect(l.inset).toBe(8);
    // Centred: 8 above and 8 below the 36 disc.
    expect(l.buttonBottom).toBe(8);
  });
  it("grows a line at a time, same padding (Prism: 3 lines 92, 5 lines 132)", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    expect(composerPillHeight(40, l)).toBe(72);
    expect(composerPillHeight(60, l)).toBe(92);
    expect(composerPillHeight(100, l)).toBe(132);
  });
  it("the disc keeps its place while the pill grows (bottom-anchored: vertical delta 0)", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    // buttonBottom doesn't depend on the pill's height; the last line's centre is 26 above the bottom, as the disc's.
    expect(l.buttonBottom + base.button / 2).toBe(l.padV + base.lineHeight / 2);
  });
  it("stops at five lines; the input scrolls past that (a long paste)", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    expect(l.inputMax).toBe(20 * COMPOSER_MAX_LINES);
    expect(composerPillHeight(20 * COMPOSER_MAX_LINES, l)).toBe(l.pillMax);
    expect(composerPillHeight(20 * 12, l)).toBe(l.pillMax);
  });
  it("an empty field (or a tiny first measure) is never below one line", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    expect(composerPillHeight(0, l)).toBe(52);
  });
  it("at text size 1.3 the line, the pill and the cap scale; the disc rises to the last line's centre", () => {
    const l = composerLayout({ ...base, fontScale: 1.3 });
    expect(composerPillHeight(26, l)).toBeCloseTo(58);
    expect(l.inputMax).toBeCloseTo(26 * COMPOSER_MAX_LINES);
    expect(l.pillMax).toBeCloseTo(26 * COMPOSER_MAX_LINES + 32);
    // Last line centre 16 + 13 = 29 above the bottom; disc centre 18: 11 from the bottom.
    expect(l.buttonBottom).toBeCloseTo(11);
  });
  it("the text leaves room for the buttons at the pill's end", () => {
    const l = composerLayout({ ...base, fontScale: 1 });
    // One disc: 8 inset + 36 + 8 before the text.
    expect(composerPadEnd(l, { buttons: 1, button: 36, gap: 8 })).toBe(52);
    // Mic + send: 8 + 36 + 8 + 36 + 8.
    expect(composerPadEnd(l, { buttons: 2, button: 36, gap: 8 })).toBe(96);
  });
});
