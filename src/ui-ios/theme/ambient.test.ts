import { describe, expect, it } from "vitest";
import { AMBIENT_LIGHT_STRENGTH, EMBER_PEAK_ALPHA, emberGradient, MASCOT_GLOW_ALPHA, mascotGlow, MOON_ALPHA } from "./ambient";
import { contrastRatio, mixHex } from "./oklch";
import { getPalette, Mode, PALETTE_IDS } from "./palette";

const rgbHex = (rgb: readonly number[]) => "#" + rgb.map((v) => v.toString(16).padStart(2, "0")).join("").toUpperCase();

describe("ember glow", () => {
  it("never peaks above 0.25 alpha", () => {
    expect(EMBER_PEAK_ALPHA).toBeLessThanOrEqual(0.25);
    expect(emberGradient("255, 122, 61")).toContain("rgba(255, 122, 61, 0.25) 0%");
  });

  it.each(PALETTE_IDS.flatMap((id) => (["dark", "light"] as Mode[]).map((m) => [id, m] as const)))(
    "keeps every text token AA over the light pattern at its peak (%s / %s)",
    (id, mode) => {
      const p = getPalette(id, mode);
      // Fogueira draws the ember; Luar draws the moon disc (see Ambient.tsx).
      const under =
        id === "luar"
          ? mixHex(p.moon, p.canvas, MOON_ALPHA)
          : mixHex(rgbHex(p.glow), p.canvas, EMBER_PEAK_ALPHA * (mode === "light" ? AMBIENT_LIGHT_STRENGTH : 1));
      for (const fg of [p.textPrimary, p.textSecondary, p.accentText, p.fieldText]) {
        expect(contrastRatio(fg, under), `${fg} over ${under}`).toBeGreaterThanOrEqual(4.5);
      }
    }
  );
});

describe("hero mascot glow", () => {
  it("fades to zero inside its box (no hard edge)", () => {
    const g = mascotGlow("255, 122, 61");
    expect(g).toContain("closest-side");
    expect(g).toMatch(/rgba\(255, 122, 61, 0\) 100%\)$/);
  });

  it.each(PALETTE_IDS.flatMap((id) => (["dark", "light"] as Mode[]).map((m) => [id, m] as const)))(
    "keeps primary text AA over the glow peak (%s / %s)",
    (id, mode) => {
      const p = getPalette(id, mode);
      const under = mixHex(rgbHex(p.glow), p.canvas, MASCOT_GLOW_ALPHA * (mode === "light" ? AMBIENT_LIGHT_STRENGTH : 1));
      expect(contrastRatio(p.textPrimary, under), `${p.textPrimary} over ${under}`).toBeGreaterThanOrEqual(4.5);
    }
  );
});
