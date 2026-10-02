import { describe, expect, it } from "vitest";
import { baselineFromTop, FACE_METRICS, iosAscenderInset, opticalOffset, renderedBaseline } from "./opticalCenter";
import { APP_FONT_SCALE, fontSizeOf, lineHeightOf, TYPE_SCALE, type TypeSpec } from "./typeScale";

describe("opticalOffset", () => {
  it("the raw iOS layout matches the measured CTA: Baloo 17/20 caps centre ~4 pt above the box middle (ce4fe83 setup1, 'G' at -3.8, before Text's inset)", () => {
    const raw = baselineFromTop({ face: "display", fontSize: 17, lineHeight: 20, platform: "ios" }) - (FACE_METRICS.display.capHeight / 2) * 17 - 10;
    expect(raw).toBeGreaterThan(-4.3);
    expect(raw).toBeLessThan(-3.5);
  });

  it("with Text's inset the same iOS label is centred like Android's, so icons beside it stay on it", () => {
    const label = { face: "display" as const, uppercase: true, fontSize: 17, lineHeight: 20 };
    expect(opticalOffset({ ...label, platform: "ios" })).toBeCloseTo(opticalOffset({ ...label, platform: "android" }), 0);
  });

  it("Android centres Baloo with half-leading, so the same label sits within 0.5 pt of the middle", () => {
    expect(Math.abs(opticalOffset({ face: "display", fontSize: 17, lineHeight: 20, platform: "android" }))).toBeLessThan(0.5);
  });

  it("Lexend lines are taller than the font on both platforms: same geometry, icon ~1 pt low at body 16/24", () => {
    const ios = opticalOffset({ face: "text", fontSize: 16, lineHeight: 24, platform: "ios" });
    const android = opticalOffset({ face: "text", fontSize: 16, lineHeight: 24, platform: "android" });
    expect(ios).toBe(android);
    expect(ios).toBeCloseTo(1.1, 1);
  });

  it("iOS keeps the descent at the bottom only when the line is shorter than the font", () => {
    // Baloo 1.602 em box: a 1.7 leading is taller, so iOS centres it like Android.
    const tall = { face: "display" as const, fontSize: 10, lineHeight: 17 };
    expect(baselineFromTop({ ...tall, platform: "ios" })).toBeCloseTo(baselineFromTop({ ...tall, platform: "android" }));
  });

  it("scales linearly with the text (Dynamic Type)", () => {
    const one = opticalOffset({ face: "display", fontSize: 15, lineHeight: 19, platform: "ios" });
    const two = opticalOffset({ face: "display", fontSize: 30, lineHeight: 38, platform: "ios" });
    expect(two).toBeCloseTo(one * 2, 0);
  });
});

describe("iosAscenderInset", () => {
  // Glyph tops from the bundled TTFs (FACE_METRICS): 'b' 661/1000, 'Á'/'É'/'Ã' up to 849/1000 (Baloo 2 ExtraBold).
  const m = FACE_METRICS.display;
  const at = (fontSize: number, lineHeight: number) => ({ face: "display" as const, fontSize, lineHeight });

  it("the 40/40 wordmark clips its 'b' on iOS without it (7.4 pt above the box: 'Doar')", () => {
    expect(baselineFromTop({ ...at(40, 40), platform: "ios" }) - m.ascender * 40).toBeCloseTo(-7.4, 1);
    expect(baselineFromTop({ ...at(40, 40), platform: "android" }) - m.ascender * 40).toBeGreaterThan(0);
  });

  // Every Baloo variant of the real scale, at the three in-app sizes and at OS scale 1 and its cap
  // (2 for the uncapped ones: the largest standard Dynamic Type step is ~1.35, AX goes further).
  const display = (Object.entries(TYPE_SCALE) as [string, TypeSpec][]).filter(([, spec]) => spec.face === "display");
  const cases = display.flatMap(([name, spec]) =>
    Object.values(APP_FONT_SCALE).flatMap((k) =>
      [1, spec.maxScale ?? 2].map((os) => {
        const size = fontSizeOf(spec, k);
        return { name, fontSize: size * os, lineHeight: lineHeightOf(size, spec.leading) * os };
      })
    )
  );

  it("covers display, cardTitle, title1-3 and the rest of the Baloo scale", () => {
    const names = new Set(display.map(([name]) => name));
    for (const name of ["display", "cardTitle", "title1", "title2", "title3", "wordmark", "hero", "headline", "button", "buttonLg", "seal"]) {
      expect(names.has(name)).toBe(true);
    }
  });

  it.each(cases)("$name $fontSize/$lineHeight: on iOS 'b' and accented capitals fit inside the box", ({ fontSize, lineHeight }) => {
    const ios = { ...at(fontSize, lineHeight), platform: "ios" as const };
    const baseline = renderedBaseline(ios);
    expect(baseline - m.ascender * fontSize).toBeGreaterThanOrEqual(0);
    expect(baseline - m.accentTop * fontSize).toBeGreaterThanOrEqual(0);
    // The descent still ends at the bottom of the padded box (iOS keeps it at the line's bottom).
    expect(baseline + m.descent * fontSize).toBeLessThanOrEqual(lineHeight + iosAscenderInset(ios) + 0.01);
  });

  it.each(cases)("$name $fontSize/$lineHeight: iOS draws at Android's baseline, lower only where accents need it", ({ fontSize, lineHeight }) => {
    const ios = renderedBaseline({ ...at(fontSize, lineHeight), platform: "ios" });
    const android = baselineFromTop({ ...at(fontSize, lineHeight), platform: "android" });
    expect(ios).toBeGreaterThanOrEqual(android - 0.01);
    const needs = m.accentTop * fontSize;
    expect(ios).toBeLessThanOrEqual(Math.max(android, needs) + 0.1);
  });

  it.each(cases)("$name $fontSize/$lineHeight: Android gets no inset, so its layout does not move", ({ fontSize, lineHeight }) => {
    expect(iosAscenderInset({ ...at(fontSize, lineHeight), platform: "android" })).toBe(0);
  });

  it("Lexend and code lines are taller than their font: no inset on iOS either", () => {
    for (const spec of Object.values(TYPE_SCALE) as TypeSpec[]) {
      if (spec.face === "display") continue;
      for (const k of Object.values(APP_FONT_SCALE)) {
        const size = fontSizeOf(spec, k);
        expect(iosAscenderInset({ face: spec.face, fontSize: size, lineHeight: lineHeightOf(size, spec.leading), platform: "ios" })).toBe(0);
      }
    }
  });

  it("only the wordmark (3 pt) and display (1 pt) sit visibly below Android, to fit 'Ã'", () => {
    const below = (fontSize: number, lineHeight: number) =>
      renderedBaseline({ ...at(fontSize, lineHeight), platform: "ios" }) - baselineFromTop({ ...at(fontSize, lineHeight), platform: "android" });
    expect(below(40, 40)).toBeCloseTo(3, 0);
    expect(below(34, 37)).toBeCloseTo(1, 0);
    expect(below(26, 30)).toBeLessThan(0.2);
  });
});
