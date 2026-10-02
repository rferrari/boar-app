import { describe, expect, it } from "vitest";
import { CROP, discImage, heroImage, OPAQUE, wholeImage } from "./mascotFrame";

describe("discImage", () => {
  it("matches the mockup window (192% at 72% 40%) with the cropped asset", () => {
    const { size, left, top } = discImage(100);
    expect(size).toBeCloseTo(138.4, 1);
    expect(left).toBeCloseTo(-44.9, 1);
    expect(top).toBeCloseTo(-12.1, 1);
  });

  it("scales linearly with the diameter", () => {
    const a = discImage(32);
    const b = discImage(64);
    expect(b.size).toBeCloseTo(a.size * 2, 5);
    expect(b.left).toBeCloseTo(a.left * 2, 5);
  });

  it("crops without losing any opaque pixel (outside the crop the disc shows its accent, as in the mockup)", () => {
    expect(CROP.x).toBeLessThanOrEqual(OPAQUE.x0);
    expect(CROP.y).toBeLessThanOrEqual(OPAQUE.y0);
    expect(CROP.x + CROP.side).toBeGreaterThanOrEqual(OPAQUE.x1);
    expect(CROP.y + CROP.side).toBeGreaterThanOrEqual(OPAQUE.y1);
  });
});

describe("wholeImage", () => {
  it("draws the crop at the size and place it has inside the original image", () => {
    const w = wholeImage(200);
    expect(w.size).toBeCloseTo(144.1, 1);
    expect(w.left).toBeCloseTo(22.3, 1);
    expect(w.top).toBeCloseTo(25.8, 1);
  });
});

describe("heroImage", () => {
  it("matches the mockup's rendered hero (original at 170 pt, (-15,-10) in the 170x150 box)", () => {
    const h = heroImage();
    expect(h.size).toBeCloseTo(122.5, 1);
    expect(h.left).toBeCloseTo(3.9, 1);
    expect(h.top).toBeCloseTo(11.9, 1);
  });
});
