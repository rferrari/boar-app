import { describe, expect, it } from "vitest";
import { contrastRatio, ensureContrast, hexToOklch, hexToRgb, oklchToHex } from "./oklch";
import { getPalette, Mode, PALETTE_IDS, PaletteId, ResolvedPalette, SOURCE_PALETTES } from "./palette";

const combos: [PaletteId, Mode][] = PALETTE_IDS.flatMap((id) => (["dark", "light"] as Mode[]).map((m) => [id, m] as [PaletteId, Mode]));

const SURFACES: (keyof ResolvedPalette)[] = ["canvas", "surface", "raised", "sunken"];
const TEXT: (keyof ResolvedPalette)[] = ["textPrimary", "textSecondary", "accentText", "fieldText", "success", "warning", "danger", "info"];

const c = (p: ResolvedPalette, fg: keyof ResolvedPalette, bg: keyof ResolvedPalette) =>
  contrastRatio(p[fg] as string, p[bg] as string);

describe("oklch helpers", () => {
  it("maps the OKLCH extremes to black and white", () => {
    expect(oklchToHex([0, 0, 0])).toBe("#000000");
    expect(oklchToHex([1, 0, 0])).toBe("#FFFFFF");
  });

  it("round-trips hex through OKLCH", () => {
    for (const hex of ["#FF7A3D", "#0E1330", "#F7EFE4", "#8A5A06"]) {
      expect(oklchToHex(hexToOklch(hex))).toBe(hex);
    }
  });

  it("computes the WCAG ratio of black on white as 21", () => {
    expect(contrastRatio("#000000", "#FFFFFF")).toBeCloseTo(21, 5);
  });

  it("leaves a passing color untouched and fixes a failing one by lightness only", () => {
    expect(ensureContrast("#FF7A3D", ["#17110D"], 4.5)).toBe("#FF7A3D");
    const fixed = ensureContrast("#C4541C", ["#F7EFE4"], 4.5);
    expect(contrastRatio(fixed, "#F7EFE4")).toBeGreaterThanOrEqual(4.5);
    const [, c0, h0] = hexToOklch("#C4541C");
    const [, c1, h1] = hexToOklch(fixed);
    expect(Math.abs(h1 - h0)).toBeLessThan(2);
    expect(Math.abs(c1 - c0)).toBeLessThan(0.01);
  });

  it("parses hex channels", () => {
    expect(hexToRgb("#FF7A3D")).toEqual([255, 122, 61]);
  });
});

describe.each(combos)("%s / %s", (id, mode) => {
  const p = getPalette(id, mode);
  const src = SOURCE_PALETTES[id][mode];

  it("keeps the designer's surfaces, text and ink", () => {
    expect(p.canvas).toBe(src.bg);
    expect(p.surface).toBe(src.s1);
    expect(p.hairline).toBe(src.bd);
    expect(p.onAccent).toBe(src.ink);
  });

  it.each(SURFACES)("gives every text token >= 4.5:1 on %s", (bg) => {
    for (const fg of TEXT) expect(c(p, fg, bg), `${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5);
  });

  it("gives tone text >= 4.5:1 on its own soft fill", () => {
    const pairs: [keyof ResolvedPalette, keyof ResolvedPalette][] = [
      ["accentText", "accentSoft"],
      ["fieldText", "fieldSoft"],
      ["success", "successSoft"],
      ["warning", "warningSoft"],
      ["danger", "dangerSoft"],
      ["info", "infoSoft"],
    ];
    for (const [fg, bg] of pairs) expect(c(p, fg, bg), `${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5);
  });

  it("keeps primary text >= 4.5:1 on every soft fill (Banner body)", () => {
    for (const bg of ["accentSoft", "fieldSoft", "successSoft", "warningSoft", "dangerSoft", "infoSoft"] as (keyof ResolvedPalette)[]) {
      expect(c(p, "textPrimary", bg), `textPrimary on ${bg}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  it("keeps secondary text >= 4.5:1 on every soft fill (details in status cards, Prism FL-13/UX-7)", () => {
    for (const bg of ["accentSoft", "fieldSoft", "successSoft", "warningSoft", "dangerSoft", "infoSoft"] as (keyof ResolvedPalette)[]) {
      expect(c(p, "textSecondary", bg), `textSecondary on ${bg}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  it("keeps primary and secondary text >= 4.5:1 on a selected option (accentSoft)", () => {
    expect(c(p, "textPrimary", "accentSoft")).toBeGreaterThanOrEqual(4.5);
    expect(c(p, "textSecondary", "accentSoft")).toBeGreaterThanOrEqual(4.5);
  });

  it("gives button labels >= 4.5:1 on accent, pressed and destructive fills", () => {
    expect(c(p, "onAccent", "accent")).toBeGreaterThanOrEqual(4.5);
    expect(c(p, "onAccent", "accentPressed")).toBeGreaterThanOrEqual(4.5);
    expect(c(p, "onAccent", "dangerFill")).toBeGreaterThanOrEqual(4.5);
  });

  it("gives control borders and the accent fill >= 3:1 against surfaces (WCAG 1.4.11)", () => {
    for (const bg of SURFACES) {
      expect(c(p, "lineStrong", bg), `lineStrong on ${bg}`).toBeGreaterThanOrEqual(3);
      expect(c(p, "accent", bg), `accent on ${bg}`).toBeGreaterThanOrEqual(3);
    }
  });
});

describe("departures from the designer's values", () => {
  // Every place the shipped color differs from the mockup, because an AA
  // floor required it. If this snapshot changes, update DESIGN_SYSTEM.md §2.
  it("are limited to this list", () => {
    const out: string[] = [];
    for (const [id, mode] of combos) {
      const p = getPalette(id, mode);
      const s = SOURCE_PALETTES[id][mode];
      const check: [string, string, string][] = [
        ["tx", s.tx, p.textPrimary],
        ["mu", s.mu, p.textSecondary],
        ["a (fill)", s.a, p.accent],
        ["a (text)", s.a, p.accentText],
        ["a2 (text)", s.a2, p.fieldText],
        ["ok", s.ok, p.success],
        ["warn", s.warn, p.warning],
        ["err", s.err, p.danger],
        ["err (fill)", s.err, p.dangerFill],
        ["bd (strong)", s.bd, p.lineStrong],
      ];
      for (const [name, from, to] of check) if (from.toUpperCase() !== to) out.push(`${id}/${mode} ${name}: ${from} -> ${to}`);
    }
    expect(out).toMatchSnapshot();
  });
});
