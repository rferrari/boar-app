/**
 * BOAR palettes: "Fogueira & Luar", the user's visual identity (Claude
 * Design, review/ui-ref). Fogueira = ember on charcoal (default), Luar =
 * amber on night blue. Dark is the default mode; light variants exist.
 *
 * `SOURCE_PALETTES` holds the designer's values verbatim. `resolvePalette`
 * turns them into the semantic palette the app ships. It only departs from a
 * designer value when a WCAG AA floor requires it, and then it moves nothing
 * but OKLCH lightness (hue and chroma kept), deterministically. The a11y
 * floors win over the mockup. palette.test.ts gates every pair and lists
 * every departure.
 */
import { ensureContrast, mixHex, oklchToHex, shiftLightness } from "./oklch";

export type PaletteId = "fogueira" | "luar";
export type Mode = "dark" | "light";

/** Designer tokens: bg, s1 (surface), s2 (surface 2), bd (border), mu (muted), tx (text), a (action), a2 (verified), ink (on action), glow (rgb). */
export interface SourcePalette {
  bg: string;
  s1: string;
  s2: string;
  bd: string;
  mu: string;
  tx: string;
  a: string;
  a2: string;
  ink: string;
  glow: readonly [number, number, number];
  ok: string;
  warn: string;
  err: string;
}

const STATUS_DARK = { ok: "#8FC27A", warn: "#F2C14E", err: "#F0674F" } as const;
const STATUS_LIGHT = { ok: "#3F7A2E", warn: "#8A6200", err: "#B8321F" } as const;

export const SOURCE_PALETTES: Record<PaletteId, Record<Mode, SourcePalette>> = {
  fogueira: {
    dark: { bg: "#17110D", s1: "#221913", s2: "#33271E", bd: "#4A3526", mu: "#B3A596", tx: "#F5E9DC", a: "#FF7A3D", a2: "#FFC15E", ink: "#17110D", glow: [255, 122, 61], ...STATUS_DARK },
    light: { bg: "#F7EFE4", s1: "#EADCC9", s2: "#E0CFB8", bd: "#D4BFA3", mu: "#6E5A48", tx: "#17110D", a: "#C4541C", a2: "#8A5A06", ink: "#F7EFE4", glow: [255, 122, 61], ...STATUS_LIGHT },
  },
  luar: {
    dark: { bg: "#0E1330", s1: "#171D42", s2: "#262E5C", bd: "#323B6E", mu: "#A9B0D0", tx: "#FFF8E6", a: "#FFB547", a2: "#F4E7B5", ink: "#0E1330", glow: [244, 231, 181], ...STATUS_DARK },
    light: { bg: "#F3F1EA", s1: "#E1E2EC", s2: "#D3D5E3", bd: "#C3C6D9", mu: "#4E5578", tx: "#0E1330", a: "#B7700A", a2: "#7A5E10", ink: "#F3F1EA", glow: [244, 231, 181], ...STATUS_LIGHT },
  },
};

export interface ResolvedPalette {
  canvas: string;
  surface: string;
  raised: string;
  sunken: string;
  hairline: string;
  lineStrong: string;
  textPrimary: string;
  textSecondary: string;
  textDisabled: string;
  /** Primary button fill; `onAccent` text on it is >= 4.5:1. */
  accent: string;
  accentPressed: string;
  accentText: string;
  accentSoft: string;
  onAccent: string;
  /** "verified" (a2): provenance, sources, the OFFLINE seal text. */
  fieldText: string;
  /** Designer's a2 as-is, for non-text marks (relevance bars, icons on dark). */
  fieldSolid: string;
  fieldSoft: string;
  success: string;
  successSoft: string;
  warning: string;
  warningSoft: string;
  danger: string;
  dangerSoft: string;
  /** Destructive button fill; `onAccent` text on it is >= 4.5:1. */
  dangerFill: string;
  info: string;
  infoSoft: string;
  glow: readonly [number, number, number];
  /** Designer's moon disc (Luar light pattern). */
  moon: string;
}

/** Opacity of tinted fills over the surface (designer: .16 dark / .12 light). */
const SOFT_ALPHA: Record<Mode, number> = { dark: 0.16, light: 0.12 };
const INFO: Record<Mode, string> = {
  dark: oklchToHex([0.8, 0.07, 250]),
  light: oklchToHex([0.47, 0.09, 250]),
};

export function resolvePalette(src: SourcePalette, mode: Mode): ResolvedPalette {
  const dark = mode === "dark";
  const canvas = src.bg;
  const surface = src.s1;
  // Dark: planes get lighter as they rise (s2 floats). Light: cards (s1) sit
  // darker than the page, so floating things take a lighter bg and wells take s2.
  const raised = dark ? src.s2 : shiftLightness(src.bg, 0.02);
  const sunken = dark ? shiftLightness(src.bg, -0.03) : src.s2;
  const surfaces = [canvas, surface, raised, sunken];
  const alpha = SOFT_ALPHA[mode];
  const soft = (hex: string) => mixHex(hex, surface, alpha);
  const text = (hex: string, extra: string[] = []) => ensureContrast(hex, [...surfaces, ...extra], 4.5);

  const accent = ensureContrast(src.a, [src.ink], 4.5);
  const accentPressed = ensureContrast(shiftLightness(accent, dark ? -0.06 : -0.05), [src.ink], 4.5);
  const accentSoft = soft(src.a);
  const fieldSoft = soft(src.a2);
  const successSoft = soft(src.ok);
  const warningSoft = soft(src.warn);
  const dangerSoft = soft(src.err);
  const infoSoft = soft(INFO[mode]);

  return {
    canvas,
    surface,
    raised,
    sunken,
    hairline: src.bd,
    lineStrong: ensureContrast(src.bd, surfaces, 3),
    textPrimary: text(src.tx),
    // Also on every soft fill: details in status cards and banners (Prism FL-13, Fogueira light was 4.37-4.47).
    textSecondary: text(src.mu, [accentSoft, fieldSoft, successSoft, warningSoft, dangerSoft, infoSoft]),
    textDisabled: mixHex(src.mu, src.bg, 0.5),
    accent,
    accentPressed,
    accentText: text(src.a, [accentSoft]),
    accentSoft,
    onAccent: src.ink,
    fieldText: text(src.a2, [fieldSoft]),
    fieldSolid: src.a2,
    fieldSoft,
    success: text(src.ok, [successSoft]),
    successSoft,
    warning: text(src.warn, [warningSoft]),
    warningSoft,
    danger: text(src.err, [dangerSoft]),
    dangerSoft,
    dangerFill: ensureContrast(src.err, [src.ink], 4.5),
    info: text(INFO[mode], [infoSoft]),
    infoSoft,
    glow: src.glow,
    moon: dark ? "#F4E7B5" : "#EED98E",
  };
}

const cache = new Map<string, ResolvedPalette>();

export function getPalette(id: PaletteId, mode: Mode): ResolvedPalette {
  const key = `${id}:${mode}`;
  let p = cache.get(key);
  if (!p) {
    p = resolvePalette(SOURCE_PALETTES[id][mode], mode);
    cache.set(key, p);
  }
  return p;
}

export const PALETTE_IDS: readonly PaletteId[] = ["fogueira", "luar"];
