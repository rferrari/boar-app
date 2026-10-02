/**
 * The iOS top inset of a Text, composed with the caller's own padding and margins (pure, for tests).
 * See theme/opticalCenter.ts iosAscenderInset: the padding moves the glyphs down inside the box and
 * the negative bottom margin gives the space back, so the text's footprint does not change.
 */
import { iosAscenderInset } from "../theme/opticalCenter";
import type { FontFace } from "../theme/fonts";

// Anything React Native accepts (numbers, "50%", "auto", animated nodes); only plain numbers compose.
type Side = unknown;

/** The subset of a flattened text style this needs. */
export interface InsetSource {
  fontSize?: number;
  lineHeight?: number;
  padding?: Side;
  paddingVertical?: Side;
  paddingTop?: Side;
  margin?: Side;
  marginVertical?: Side;
  marginBottom?: Side;
}

/** Most specific side wins, as in React Native; undefined when it is not a plain number (%, auto). */
function side(...values: Side[]): number | undefined {
  const v = values.find((x) => x !== undefined && x !== null);
  if (v === undefined) return 0;
  return typeof v === "number" ? v : undefined;
}

/**
 * `{ paddingTop, marginBottom }` to append to an iOS text's style, or null when nothing moves
 * (the line is as tall as the font, or a side is not a number). `scale` = the OS font scale after
 * the variant's cap, as React Native applies it to both size and line height.
 */
export function iosTextInset(face: FontFace, flat: InsetSource, scale: number): { paddingTop: number; marginBottom: number } | null {
  if (!flat.fontSize) return null;
  const fontSize = flat.fontSize * scale;
  const lineHeight = (flat.lineHeight ?? flat.fontSize) * scale;
  const inset = iosAscenderInset({ face, fontSize, lineHeight, platform: "ios" });
  if (inset === 0) return null;
  const top = side(flat.paddingTop, flat.paddingVertical, flat.padding);
  const bottom = side(flat.marginBottom, flat.marginVertical, flat.margin);
  if (top === undefined || bottom === undefined) return null;
  return { paddingTop: top + inset, marginBottom: bottom - inset };
}

/** React Native's scale for a text: the OS scale, capped by maxFontSizeMultiplier (>= 1 caps; 0 = none). */
export function cappedScale(fontScale: number, cap: number | undefined, allowFontScaling = true): number {
  if (!allowFontScaling) return 1;
  return cap && cap >= 1 ? Math.min(fontScale, cap) : fontScale;
}
