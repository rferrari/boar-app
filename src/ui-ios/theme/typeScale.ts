/**
 * The type scale as data (pure: no react-native import, so tests can walk it). tokens.ts turns it
 * into styles.
 */
import type { FontScale } from "../../models/settings";
import type { FontFace, FontWeight } from "./fonts";

/** In-app text size preference, applied on top of the OS font scale. */
export const APP_FONT_SCALE: Record<FontScale, number> = {
  compact: 0.94,
  standard: 1,
  large: 1.12,
};

/** Informative text never renders below this, whatever the app scale. */
export const MIN_FONT_SIZE = 12;
/**
 * Uppercase letterspaced labels (overlines, seals, stepper, caps metadata) may go down to 11 pt
 * (iOS Caption 2): caps with tracking read larger than their size. The mockup's 9-10.5 px caps
 * render at 11 (Boar, fidelity sprint 27/09; reversible).
 */
export const MIN_CAPS_FONT_SIZE = 11;

export type TypeSpec = {
  face: FontFace;
  weight: FontWeight;
  size: number;
  /** Line height as a ratio of size, so it tracks every scale. */
  leading: number;
  tracking?: number;
  uppercase?: boolean;
  tabular?: boolean;
  /** Cap for OS font scaling. Undefined = unlimited (the default for content). */
  maxScale?: number;
};

/** Baloo 2 (display) for the wordmark, titles, buttons and big numbers; Lexend (text) for everything read. */
export const TYPE_SCALE = {
  /** The one figure a screen is about (download %, mockup 56). Capped: it is already large. */
  // Leading 1.2: Baloo's ascenders overflow a 1.0 line box and iOS clips them (Loom, b897ffd '51%').
  // Stat trims the extra leading back so the figure keeps the mockup's footprint.
  hero: { face: "display", weight: 800, size: 56, leading: 1.2, tracking: -1.2, maxScale: 1.2 },
  /** The chat's empty-state wordmark (mockup 40). */
  wordmark: { face: "display", weight: 800, size: 40, leading: 1, tracking: -0.8, maxScale: 1.3 },
  display: { face: "display", weight: 800, size: 34, leading: 1.1, tracking: -0.7, maxScale: 1.5 },
  // Screen titles stop growing at 1.6x: past that a one-word title ('Knowledge', 'Performance',
  // 'Conhecimento') is wider than a 375 pt screen and iOS breaks it mid-word (Harbor, AX-XL, 6eb9ca7).
  title1: { face: "display", weight: 800, size: 26, leading: 1.15, tracking: -0.5, maxScale: 1.6 },
  title2: { face: "display", weight: 800, size: 22, leading: 1.2, tracking: -0.4, maxScale: 1.6 },
  title3: { face: "display", weight: 700, size: 18, leading: 1.25, tracking: -0.2 },
  headline: { face: "display", weight: 700, size: 17, leading: 1.3, tracking: -0.2 },
  /** Title of a choice card (mockup OptionCard 16). */
  cardTitle: { face: "display", weight: 800, size: 16, leading: 1.1 },
  /** Bottom call to action (mockup 17). */
  buttonLg: { face: "display", weight: 800, size: 17, leading: 1.2 },
  /** The OFFLINE seal's label (mockup 13 / 800 / .04em, caps). */
  seal: { face: "display", weight: 800, size: 13, leading: 1.3, tracking: 0.5, uppercase: true },
  /** Button labels (mockup 15). */
  button: { face: "display", weight: 800, size: 15, leading: 1.25 },
  body: { face: "text", weight: 400, size: 16, leading: 1.5 },
  callout: { face: "text", weight: 400, size: 15, leading: 1.45 },
  subhead: { face: "text", weight: 500, size: 14, leading: 1.43 },
  footnote: { face: "text", weight: 400, size: 13, leading: 1.4 },
  caption: { face: "text", weight: 400, size: 12, leading: 1.35 },
  /** Uppercase letterspaced overline: section labels ("LANGUAGE", "LOCAL INDEX"). Mockup 10.5 / 500 / .12em. */
  label: { face: "text", weight: 500, size: 11, leading: 1.45, tracking: 1.3, uppercase: true },
  /** Status seals and chips ("RECOMMENDED", "ACTION REQUIRED", "STREAMING"). Mockup 9-10.5 / 600 / .1em. */
  badge: { face: "text", weight: 600, size: 11, leading: 1.3, tracking: 1, uppercase: true },
  /** Stepper labels. Mockup 9 / 400 / .06em. */
  step: { face: "text", weight: 400, size: 11, leading: 1.45, tracking: 0.6, uppercase: true },
  /** Caps metadata without tracking (model id in the chat header). Mockup 9.5; untracked caps keep the 12 pt floor. */
  capsMeta: { face: "text", weight: 400, size: 12, leading: 1.4, uppercase: true },
  /** Data readouts: sizes, speeds, model file names. Lexend with tabular figures (designer `--fm`). */
  mono: { face: "text", weight: 400, size: 13, leading: 1.45, tabular: true },
  /** Code blocks and raw hashes: system monospace. */
  code: { face: "code", weight: 400, size: 13, leading: 1.5 },
} satisfies Record<string, TypeSpec>;

/** Rendered line height for a size (the in-app scale already applied): whole points. */
export function lineHeightOf(fontSize: number, leading: number): number {
  return Math.round(fontSize * leading);
}

/** Rendered size for an in-app scale `k`: half points, never under the floor (caps may go to 11). */
export function fontSizeOf(spec: TypeSpec, k: number): number {
  const floor = spec.uppercase ? MIN_CAPS_FONT_SIZE : MIN_FONT_SIZE;
  return Math.max(floor, Math.round(spec.size * k * 2) / 2);
}
