/**
 * BOAR design tokens. One object per color scheme; everything a component
 * needs to draw itself comes from here (via `useTheme().tokens`), never from
 * literals. See docs/DESIGN_SYSTEM.md for usage rules.
 */
import { Easing, Platform, StyleSheet, TextStyle } from "react-native";
import type { FontScale } from "../../models/settings";
import { fontFamilyFor, FontFace } from "./fonts";
import { APP_FONT_SCALE, fontSizeOf, lineHeightOf, TYPE_SCALE, type TypeSpec } from "./typeScale";
import { getPalette, PaletteId, ResolvedPalette } from "./palette";
import { CURVE, DELAY, DURATION, LOOP, TRAVEL } from "./motionSpec";

export type ColorScheme = "light" | "dark";

// ---------------------------------------------------------------------------
// Color
// ---------------------------------------------------------------------------

function withAlpha(hex: string, alpha: number): string {
  const a = Math.round(alpha * 255)
    .toString(16)
    .padStart(2, "0")
    .toUpperCase();
  return `${hex}${a}`;
}

function buildColors(p: ResolvedPalette, scheme: ColorScheme) {
  const [r, g, b] = p.glow;
  return {
    bg: {
      /** Screen background (designer `bg`). */
      canvas: p.canvas,
      /** Cards, list groups, the composer (designer `s1`). */
      surface: p.surface,
      /** Sheets, toasts, secondary buttons: anything above a surface. */
      raised: p.raised,
      /** Wells inside cards (source rows, inputs in a card), pressed rows, skeletons. */
      sunken: p.sunken,
      /** Backdrop behind sheets and the drawer. */
      scrim: scheme === "dark" ? "rgba(0, 0, 0, 0.6)" : withAlpha(p.textPrimary, 0.36),
    },
    text: {
      primary: p.textPrimary,
      /** Designer `mu`. AA on every surface; also used for metadata and placeholders. */
      secondary: p.textSecondary,
      tertiary: p.textSecondary,
      /** Decorative / disabled only. Fails AA on purpose; never carry information with it. */
      disabled: p.textDisabled,
      accent: p.accentText,
      field: p.fieldText,
      onAccent: p.onAccent,
    },
    line: {
      /** Designer `bd`. Decorative separator: never the only affordance of a control. */
      hairline: p.hairline,
      /** Separator between rows inside a card (mockup: s2 on s1). Light mode keeps the hairline. */
      row: scheme === "dark" ? p.raised : p.hairline,
      /** Control borders that are the affordance (inputs, switch off, outline button): >= 3:1. */
      strong: p.lineStrong,
      focus: p.accent,
    },
    accent: {
      solid: p.accent,
      pressed: p.accentPressed,
      soft: p.accentSoft,
      text: p.accentText,
      on: p.onAccent,
    },
    /** Designer `a2` ("verified"): provenance, sources, the OFFLINE seal. */
    field: {
      solid: p.fieldSolid,
      soft: p.fieldSoft,
      text: p.fieldText,
    },
    status: {
      success: { solid: p.success, soft: p.successSoft },
      warning: { solid: p.warning, soft: p.warningSoft },
      danger: { solid: p.danger, soft: p.dangerSoft, fill: p.dangerFill },
      info: { solid: p.info, soft: p.infoSoft },
    },
    /** Ember/moon light as "r, g, b" for rgba() glows. */
    glow: `${r}, ${g}, ${b}`,
    moon: p.moon,
  };
}

export type ColorTokens = ReturnType<typeof buildColors>;
export type Tone = "neutral" | "accent" | "field" | "success" | "warning" | "danger" | "info";

/** Foreground / background pair for a semantic tone (badges, banners, chips). */
export function toneColors(c: ColorTokens, tone: Tone): { fg: string; bg: string; solid: string } {
  switch (tone) {
    case "neutral":
      return { fg: c.text.secondary, bg: c.bg.sunken, solid: c.text.secondary };
    case "accent":
      return { fg: c.accent.text, bg: c.accent.soft, solid: c.accent.solid };
    case "field":
      return { fg: c.field.text, bg: c.field.soft, solid: c.field.solid };
    default:
      return { fg: c.status[tone].solid, bg: c.status[tone].soft, solid: c.status[tone].solid };
  }
}

// ---------------------------------------------------------------------------
// Typography
// ---------------------------------------------------------------------------

export { APP_FONT_SCALE, MIN_FONT_SIZE, MIN_CAPS_FONT_SIZE } from "./typeScale";

export type TextVariant = keyof typeof TYPE_SCALE;

export type TypeStyle = TextStyle & { maxFontSizeMultiplier?: number };

export function variantFace(variant: TextVariant): FontFace {
  return TYPE_SCALE[variant].face;
}

/** What the icon alignment needs from a variant: its face, whether it is caps, and its OS-scale cap. */
export function variantShape(variant: TextVariant): { face: FontFace; uppercase: boolean; maxScale?: number } {
  const spec: TypeSpec = TYPE_SCALE[variant];
  return { face: spec.face, uppercase: !!spec.uppercase, maxScale: spec.maxScale };
}

function buildType(fontScale: FontScale): Record<TextVariant, TypeStyle> {
  const k = APP_FONT_SCALE[fontScale];
  const out = {} as Record<TextVariant, TypeStyle>;
  for (const [name, spec] of Object.entries(TYPE_SCALE) as [TextVariant, TypeSpec][]) {
    const fontSize = fontSizeOf(spec, k);
    out[name] = {
      fontFamily: fontFamilyFor(spec.face, spec.weight),
      fontSize,
      lineHeight: lineHeightOf(fontSize, spec.leading),
      letterSpacing: spec.tracking ?? 0,
      ...(spec.face === "code" ? { fontWeight: "400" as const } : null),
      ...(spec.uppercase ? { textTransform: "uppercase" as const } : null),
      ...(spec.tabular ? { fontVariant: ["tabular-nums" as const] } : null),
      ...(spec.maxScale ? { maxFontSizeMultiplier: spec.maxScale } : null),
    };
  }
  return out;
}

// ---------------------------------------------------------------------------
// Space, radius, size
// ---------------------------------------------------------------------------

/** 4pt grid. Names kept compatible with the legacy `spacing` export. */
export const space = {
  none: 0,
  xxs: 2,
  xs: 4,
  sm: 8,
  md: 12,
  base: 16,
  lg: 20,
  xl: 24,
  xxl: 32,
  xxxl: 40,
  huge: 48,
  giant: 64,
  /** Screen side margin (mockup: 20). Use this, not `base`, for screen-level horizontal padding. */
  gutter: 20,
  /** Side margin of the conversation (chat header, list, composer): the mockup uses 16 there. */
  gutterChat: 16,
  /** Gap between suggestion cards in the chat (mockup 14). */
  cardGap: 14,
  /** Left/right inset of rows and blocks inside a Section (ListRow, Section title, catalog rows): 14. */
  inset: 14,
} as const;

/** From the mockup: pills for actions and inputs, soft cards. */
export const radius = {
  xs: 4,
  /** Relevance bars, small tags. */
  sm: 8,
  /** Rows inside cards (source items), toasts, segments. */
  md: 14,
  /** Compact cards: suggestions, choices, the steps card (mockup 18). */
  card: 18,
  /** Cards. */
  lg: 20,
  /** Hero cards: download, model error (mockup 22). */
  hero: 22,
  /** Sheets (top corners). */
  xl: 28,
  /** Buttons, inputs, badges, the OFFLINE seal. */
  full: 999,
} as const;

/**
 * Icon beside text (icon-align round, 27/09). One set (Feather), one stroke: Feather draws 2 on a
 * 24 grid, so the stroke scales with the icon. Sizes are at 1.0 and grow with the text (IconText).
 */
export const icon = {
  /** Icon to text: every row, button, banner, toast. */
  gap: 8,
  /** Chips and seals. */
  gapTight: 6,
  /** Beside body, callout, footnote and button text. */
  sizeBody: 16,
  /** Beside titles (headline and up, bottom CTA) and as the leading glyph of a list row. */
  sizeTitle: 20,
  /** Inside caps seals and chips (badge 11, seal 13). */
  sizeSeal: 13,
  /** Feather's stroke on its 24 grid; informational (the glyph font can't change it). */
  stroke: 2,
  /** Icons stop growing with the OS text size here, like the capped text variants. */
  maxScale: 2,
} as const;

/** Minimum touch target per platform guideline (Apple HIG 44pt, Material 48dp). */
export const MIN_TOUCH = Platform.OS === "ios" ? 44 : 48;

export const size = {
  touch: MIN_TOUCH,
  /** Minimum height of a list row (ListRow, RadioRow, checkbox rows, toast): the touch minimum plus 4. */
  row: MIN_TOUCH + 4,
  /** Loading placeholder for a card of metrics or a list block. */
  skeletonCard: 120,
  iconSm: 16,
  icon: 20,
  iconLg: 24,
  controlSm: 36,
  /** The icon well of an empty or error state. */
  emptyWell: 56,
  control: 48,
  /** Round controls in the chat header (menu, avatar): visual 42, touch via hitSlop. */
  headerDisc: 42,
  /** Assistant avatar in the chat header / a message row. */
  avatar: 42,
  avatarSm: 26,
  /** Mascot in a horizontal brand line (setup). */
  mascotSm: 48,
  /** Mascot above a status (model loading/error) and in the download hero. */
  mascotMd: 120,
  /** Mascot as the hero of the chat's empty state: a 170×150 layout box (image placement in mascotFrame.ts). */
  mascot: 170,
  mascotBoxHeight: 150,
  /** Chat composer pill and send button. */
  composer: 52,
  /** Regular button (mockup 46; raised to the touch minimum where that is larger). */
  button: 46,
  /** Bottom call-to-action button. */
  buttonLg: 54,
  hairline: StyleSheet.hairlineWidth,
  border: 1,
  focusRing: 2,
} as const;

// ---------------------------------------------------------------------------
// Elevation
// ---------------------------------------------------------------------------

/**
 * Dark mode (the default) separates planes by surface lightness, never by
 * shadow; the only "shadow" is the ember glow on the primary action and the
 * OFFLINE seal (`glow`). Light mode adds soft warm shadows to floating planes.
 */
function buildElevation(scheme: ColorScheme, glow: string) {
  const glowShadow = { boxShadow: `0px 0px 22px rgba(${glow}, ${scheme === "dark" ? 0.45 : 0.3})` };
  if (scheme === "dark") {
    return { 0: {}, 1: {}, 2: {}, 3: { boxShadow: "0px 16px 40px rgba(0, 0, 0, 0.45)" }, glow: glowShadow } as const;
  }
  return {
    0: {},
    1: { boxShadow: "0px 1px 2px rgba(40, 24, 12, 0.08)" },
    2: { boxShadow: "0px 4px 16px rgba(40, 24, 12, 0.12), 0px 1px 3px rgba(40, 24, 12, 0.08)" },
    3: { boxShadow: "0px 12px 32px rgba(40, 24, 12, 0.18), 0px 2px 6px rgba(40, 24, 12, 0.08)" },
    glow: glowShadow,
  } as const;
}

// ---------------------------------------------------------------------------
// Motion
// ---------------------------------------------------------------------------

/** Numbers live in motionSpec.ts; roles (enter/exit/change/layout) and the Reanimated builders in motion.ts. */
export const motion = {
  duration: DURATION,
  /** Material 3 "standard" / "emphasized decelerate" / "emphasized accelerate" curves. */
  easing: {
    standard: Easing.bezier(...CURVE.standard),
    enter: Easing.bezier(...CURVE.enter),
    exit: Easing.bezier(...CURVE.exit),
  },
  loop: LOOP,
  delay: DELAY,
  travel: TRAVEL,
  spring: { damping: 22, stiffness: 240, mass: 1 },
} as const;

// ---------------------------------------------------------------------------
// Assembly
// ---------------------------------------------------------------------------

/** Opacity of a control's states: disabled, pressed (text actions, segments), busy (a stop in progress), dimmed art. */
export const opacity = {
  disabled: 0.45,
  pressed: 0.6,
  busy: 0.6,
  dim: 0.7,
} as const;

export function buildTokens(scheme: ColorScheme, fontScale: FontScale = "standard", palette: PaletteId = "fogueira") {
  const color = buildColors(getPalette(palette, scheme), scheme);
  return {
    scheme,
    palette,
    color,
    type: buildType(fontScale),
    space,
    radius,
    size,
    icon,
    elevation: buildElevation(scheme, color.glow),
    motion,
    opacity,
  };
}

export type Tokens = ReturnType<typeof buildTokens>;
