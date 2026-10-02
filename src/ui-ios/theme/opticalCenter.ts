/**
 * Where a line of text looks centred, so an icon beside it can sit on that
 * point instead of the middle of the text box (r4to, icon-align round, 27/09).
 *
 * Pure (no react-native import) so it runs under vitest.
 *
 * Face metrics come from the bundled TTFs (hhea ascender/descender, OS/2
 * xHeight/capHeight, per 1 em). Both weights of each family share them.
 */
import type { FontFace } from "./fonts";

export interface FaceMetrics {
  ascent: number;
  descent: number;
  xHeight: number;
  capHeight: number;
  /** Top of the tallest lowercase ascender ('b', 'd', 'h', 'k', 'l'): glyf yMax per 1 em. */
  ascender: number;
  /** Top of the tallest accented capital in PT ('Á', 'É', 'Ã', 'Â'…): glyf yMax per 1 em, heaviest weight. */
  accentTop: number;
}

export const FACE_METRICS: Record<FontFace, FaceMetrics> = {
  // Baloo 2: a tall hhea box (1.602 em) around small caps (0.602); every Baloo leading is below 1.602.
  display: { ascent: 1.078, descent: 0.524, xHeight: 0.46, capHeight: 0.602, ascender: 0.661, accentTop: 0.849 },
  // Lexend: 1.25 em box; every Lexend leading is above it, so the text is centred on both platforms.
  text: { ascent: 1.0, descent: 0.25, xHeight: 0.525, capHeight: 0.7, ascender: 0.74, accentTop: 0.943 },
  // System monospace (Menlo / Droid Sans Mono): close to Lexend; code never sits beside an icon.
  code: { ascent: 0.928, descent: 0.236, xHeight: 0.547, capHeight: 0.729, ascender: 0.763, accentTop: 0.928 },
};

export interface OpticalInput {
  face: FontFace;
  /** Caps labels centre on the cap height; mixed case between x-height and cap height. */
  uppercase?: boolean;
  /** Rendered size and line height (after the OS font scale). */
  fontSize: number;
  lineHeight: number;
  platform: "ios" | "android";
}

/**
 * Baseline of the first line, from the top of its line box, as RN 0.86 lays it out:
 * - Android (CustomLineHeightSpan): CSS half-leading, i.e. the ascent+descent box is centred in
 *   the line, even when the leading is negative.
 * - iOS (RCTApplyBaselineOffset): centred only when the line is at least the font's height; below
 *   that no offset is applied and TextKit keeps the descent at the bottom, so the glyphs ride
 *   high (and ascenders clip: the '51%' hero, b897ffd). Measured on ce4fe83 setup1 CTA:
 *   'G' centre 3.8 pt above the pill centre, predicted 4.0.
 */
export function baselineFromTop({ face, fontSize, lineHeight, platform }: OpticalInput): number {
  const m = FACE_METRICS[face];
  const content = (m.ascent + m.descent) * fontSize;
  if (platform === "ios" && lineHeight < content) return lineHeight - m.descent * fontSize;
  return (lineHeight - content) / 2 + m.ascent * fontSize;
}

/**
 * Top padding that keeps every glyph of an iOS line inside its box (pt; 0 on Android and for any
 * line at least as tall as the font). Below the font's height iOS rides the glyphs high and clips
 * whatever pokes above the box: the 40 pt wordmark lost the stem of its 'b' and read 'Doar' (r4to,
 * iPhone 13, 28/09); 'Área', 'É' and accented PT titles lost their accents. The padding moves the
 * first baseline down to where Android draws it, or further when an accented capital still would not
 * fit (the wordmark 3 pt lower, display 1 pt, the rest under 0.2). Text pairs it with the
 * same negative bottom margin, so the footprint, and everything laid out after the text, stays put.
 */
export function iosAscenderInset(input: OpticalInput): number {
  if (input.platform !== "ios") return 0;
  const baseline = baselineFromTop(input);
  const toAndroid = baselineFromTop({ ...input, platform: "android" }) - baseline;
  const toAccents = FACE_METRICS[input.face].accentTop * input.fontSize - baseline;
  const inset = Math.max(0, toAndroid, toAccents);
  return Math.ceil(inset * 10) / 10;
}

/** Baseline of the first line as Text draws it: the platform's layout plus the iOS inset above. */
export function renderedBaseline(input: OpticalInput): number {
  return baselineFromTop(input) + iosAscenderInset(input);
}

/**
 * How far the optical centre of the first line sits below the middle of its line box (pt; negative
 * = above). An icon centred on the line box moves by this much to sit on the text. Uses the baseline
 * Text renders (inset included), so an icon follows its text down on iOS.
 */
export function opticalOffset(input: OpticalInput): number {
  const m = FACE_METRICS[input.face];
  const above = input.uppercase ? m.capHeight / 2 : (m.xHeight + m.capHeight) / 4;
  const centre = renderedBaseline(input) - above * input.fontSize;
  return Math.round((centre - input.lineHeight / 2) * 10) / 10;
}
