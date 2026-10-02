/**
 * OKLCH -> sRGB hex conversion and WCAG contrast. Pure, no RN imports, so the
 * palette can be authored in OKLCH and verified in tests.
 * Math: Björn Ottosson's OKLab reference implementation.
 */

export type Oklch = readonly [l: number, c: number, h: number];

function clamp01(x: number): number {
  return Math.min(1, Math.max(0, x));
}

function linearToSrgb(x: number): number {
  return x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055;
}

function srgbToLinear(x: number): number {
  return x <= 0.04045 ? x / 12.92 : Math.pow((x + 0.055) / 1.055, 2.4);
}

/** Returns linear-light sRGB channels (may fall outside 0..1 when out of gamut). */
export function oklchToLinearRgb([l, c, h]: Oklch): [number, number, number] {
  const hr = (h * Math.PI) / 180;
  const a = c * Math.cos(hr);
  const b = c * Math.sin(hr);
  const l_ = l + 0.3963377774 * a + 0.2158037573 * b;
  const m_ = l - 0.1055613458 * a - 0.0638541728 * b;
  const s_ = l - 0.0894841775 * a - 1.291485548 * b;
  const L = l_ ** 3;
  const M = m_ ** 3;
  const S = s_ ** 3;
  return [
    4.0767416621 * L - 3.3077115913 * M + 0.2309699292 * S,
    -1.2684380046 * L + 2.6097574011 * M - 0.3413193965 * S,
    -0.0041960863 * L - 0.7034186147 * M + 1.707614701 * S,
  ];
}

export function inGamut(color: Oklch, epsilon = 0.0005): boolean {
  return oklchToLinearRgb(color).every((v) => v >= -epsilon && v <= 1 + epsilon);
}

export function oklchToHex(color: Oklch): string {
  return (
    "#" +
    oklchToLinearRgb(color)
      .map((v) => Math.round(clamp01(linearToSrgb(clamp01(v))) * 255))
      .map((v) => v.toString(16).padStart(2, "0"))
      .join("")
      .toUpperCase()
  );
}

export function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace("#", "");
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [number, number, number];
}

export function relativeLuminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((v) => srgbToLinear(v / 255));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrastRatio(fg: string, bg: string): number {
  const a = relativeLuminance(fg);
  const b = relativeLuminance(bg);
  const [hi, lo] = a > b ? [a, b] : [b, a];
  return (hi + 0.05) / (lo + 0.05);
}

/** sRGB hex -> OKLCH (inverse of oklchToHex). */
export function hexToOklch(hex: string): [number, number, number] {
  const [r, g, b] = hexToRgb(hex).map((v) => srgbToLinear(v / 255));
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
  const C = Math.sqrt(A * A + B * B);
  let H = (Math.atan2(B, A) * 180) / Math.PI;
  if (H < 0) H += 360;
  return [L, C, H];
}

/**
 * Returns `fg` unchanged if it already reaches `min` contrast against every
 * background; otherwise moves only its OKLCH lightness (hue and chroma kept)
 * away from the backgrounds, in 0.005 steps, until it does. Deterministic, so
 * the shipped palette is reproducible from the designer's values.
 */
export function ensureContrast(fg: string, backgrounds: string[], min: number): string {
  const worst = (hex: string) => Math.min(...backgrounds.map((bg) => contrastRatio(hex, bg)));
  if (worst(fg) >= min) return fg.toUpperCase();
  const [l, c, h] = hexToOklch(fg);
  const bgLum = backgrounds.reduce((sum, bg) => sum + relativeLuminance(bg), 0) / backgrounds.length;
  const direction = relativeLuminance(fg) > bgLum ? 1 : -1;
  for (let step = 1; step <= 200; step++) {
    const nl = Math.min(1, Math.max(0, l + direction * step * 0.005));
    const candidate = oklchToHex([nl, c, h]);
    if (worst(candidate) >= min) return candidate;
  }
  return direction > 0 ? "#FFFFFF" : "#000000";
}

/** Solid hex of `fg` at `alpha` over `bg` (to test and ship tinted fills as opaque colors). */
export function mixHex(fg: string, bg: string, alpha: number): string {
  const f = hexToRgb(fg);
  const b = hexToRgb(bg);
  return (
    "#" +
    f
      .map((v, i) => Math.round(v * alpha + b[i] * (1 - alpha)))
      .map((v) => v.toString(16).padStart(2, "0"))
      .join("")
      .toUpperCase()
  );
}

/** Nudges a color's OKLCH lightness by `delta` (for derived surfaces). */
export function shiftLightness(hex: string, delta: number): string {
  const [l, c, h] = hexToOklch(hex);
  return oklchToHex([Math.min(1, Math.max(0, l + delta)), c, h]);
}
