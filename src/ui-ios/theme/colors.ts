import { ThemeId } from "../../models/settings";
import type { ColorTokens } from "./tokens";

/**
 * BOAR Design System — Curated Themes
 * 1. Midnight Slate (Default: OLED Black + Emerald Status)
 * 2. Amber Phosphor (Night Field Operations / Vintage CRT Amber Glow)
 * 3. Matrix (Neon Phosphor-Green Hacker Terminal)
 */

export const midnightTheme = {
  id: "midnight" as ThemeId,
  name: "Ocean",
  icon: "🌊",
  description: "OLED dark mode with emerald telemetry",
  bg: {
    black: "#000000",
    terminal: "#080C14",
    surface: "#0B0F19",
    card: "#0F172A",
    cardElevated: "#131D31",
    cardHover: "#1E293B",
    input: "#0A0E17",
    subtle: "rgba(255, 255, 255, 0.03)",
    overlay: "rgba(2, 6, 23, 0.78)",
    modalOverlay: "rgba(0, 0, 0, 0.85)",
  },
  border: {
    subtle: "rgba(255, 255, 255, 0.07)",
    default: "#1E293B",
    elevated: "#334155",
    focus: "#06B6D4",
    emerald: "rgba(16, 185, 129, 0.35)",
    cyan: "rgba(6, 182, 212, 0.35)",
    amber: "rgba(245, 158, 11, 0.4)",
    danger: "rgba(239, 68, 68, 0.4)",
    frontier: "rgba(139, 92, 246, 0.45)",
  },
  text: {
    primary: "#FFFFFF",
    heading: "#F8FAFC",
    secondary: "#CBD5E1",
    muted: "#94A3B8",
    dim: "#64748B",
    inverse: "#020617",
    accentEmerald: "#34D399",
    accentCyan: "#38BDF8",
    accentAmber: "#FBBF24",
    accentViolet: "#C084FC",
  },
  emerald: {
    50: "#ECFDF5",
    400: "#34D399",
    500: "#10B981",
    600: "#059669",
    900: "#064E3B",
    bgSubtle: "rgba(16, 185, 129, 0.12)",
    border: "rgba(16, 185, 129, 0.28)",
  },
  cyan: {
    400: "#22D3EE",
    500: "#06B6D4",
    600: "#0891B2",
    bgSubtle: "rgba(6, 182, 212, 0.12)",
    border: "rgba(6, 182, 212, 0.3)",
  },
  frontier: {
    glow: "#8B5CF6",
    glowCyan: "#06B6D4",
    badgeBg: "rgba(139, 92, 246, 0.18)",
    badgeBorder: "rgba(139, 92, 246, 0.45)",
    text: "#C4B5FD",
    gradientStart: "#1E1138",
    gradientEnd: "#081E2C",
  },
  amber: {
    400: "#FBBF24",
    500: "#F59E0B",
    600: "#D97706",
    bgSubtle: "rgba(245, 158, 11, 0.12)",
    border: "rgba(245, 158, 11, 0.35)",
  },
  crimson: {
    400: "#F87171",
    500: "#EF4444",
    600: "#DC2626",
    900: "#7F1D1D",
    bgSubtle: "rgba(239, 68, 68, 0.12)",
    border: "rgba(239, 68, 68, 0.35)",
  },
};

export const amberTheme = {
  id: "amber" as ThemeId,
  name: "Amber",
  icon: "🐗",
  description: "Warm night-vision CRT with zero blue-light strain",
  bg: {
    black: "#050402",
    terminal: "#0C0A04",
    surface: "#141008",
    card: "#1D160A",
    cardElevated: "#281F0E",
    cardHover: "#352A14",
    input: "#0F0C06",
    subtle: "rgba(245, 158, 11, 0.04)",
    overlay: "rgba(12, 10, 4, 0.82)",
    modalOverlay: "rgba(0, 0, 0, 0.88)",
  },
  border: {
    subtle: "rgba(245, 158, 11, 0.12)",
    default: "#3D2E12",
    elevated: "#5A441B",
    focus: "#F59E0B",
    emerald: "rgba(245, 158, 11, 0.4)",
    cyan: "rgba(251, 191, 36, 0.4)",
    amber: "rgba(245, 158, 11, 0.5)",
    danger: "rgba(239, 68, 68, 0.45)",
    frontier: "rgba(217, 119, 6, 0.5)",
  },
  text: {
    primary: "#FFFBEB",
    heading: "#FEF3C7",
    secondary: "#FDE68A",
    muted: "#D97706",
    dim: "#92400E",
    inverse: "#141008",
    accentEmerald: "#FBBF24",
    accentCyan: "#F59E0B",
    accentAmber: "#F59E0B",
    accentViolet: "#FCD34D",
  },
  emerald: {
    50: "#FFFBEB",
    400: "#FBBF24",
    500: "#F59E0B",
    600: "#D97706",
    900: "#78350F",
    bgSubtle: "rgba(245, 158, 11, 0.14)",
    border: "rgba(245, 158, 11, 0.35)",
  },
  cyan: {
    400: "#FCD34D",
    500: "#FBBF24",
    600: "#F59E0B",
    bgSubtle: "rgba(251, 191, 36, 0.12)",
    border: "rgba(251, 191, 36, 0.3)",
  },
  frontier: {
    glow: "#F59E0B",
    glowCyan: "#FBBF24",
    badgeBg: "rgba(245, 158, 11, 0.2)",
    badgeBorder: "rgba(245, 158, 11, 0.5)",
    text: "#FEF3C7",
    gradientStart: "#281804",
    gradientEnd: "#120B02",
  },
  amber: {
    400: "#FCD34D",
    500: "#FBBF24",
    600: "#F59E0B",
    bgSubtle: "rgba(245, 158, 11, 0.16)",
    border: "rgba(245, 158, 11, 0.4)",
  },
  crimson: {
    400: "#F87171",
    500: "#EF4444",
    600: "#DC2626",
    900: "#7F1D1D",
    bgSubtle: "rgba(239, 68, 68, 0.14)",
    border: "rgba(239, 68, 68, 0.4)",
  },
};

export const frontierTheme = {
  id: "frontier" as ThemeId,
  name: "Matrix",
  icon: "🟢",
  description: "Neon phosphor-green hacker terminal — maximum contrast, zero blue light",
  bg: {
    black: "#000000",
    terminal: "#00110A",
    surface: "#001A0D",
    card: "#012613",
    cardElevated: "#023018",
    cardHover: "#034021",
    input: "#000D06",
    subtle: "rgba(0, 255, 127, 0.04)",
    overlay: "rgba(0, 15, 8, 0.82)",
    modalOverlay: "rgba(0, 0, 0, 0.88)",
  },
  border: {
    subtle: "rgba(0, 255, 127, 0.12)",
    default: "#0A4025",
    elevated: "#0F5C35",
    focus: "#00FF7F",
    emerald: "rgba(0, 255, 127, 0.4)",
    cyan: "rgba(57, 255, 158, 0.4)",
    amber: "rgba(255, 195, 0, 0.4)",
    danger: "rgba(239, 68, 68, 0.4)",
    frontier: "rgba(0, 255, 127, 0.45)",
  },
  text: {
    primary: "#E8FFF0",
    heading: "#F0FFF5",
    secondary: "#8CFFC4",
    muted: "#3ECF7A",
    dim: "#1F8C50",
    inverse: "#00110A",
    accentEmerald: "#00FF7F",
    accentCyan: "#39FF9E",
    accentAmber: "#FFD400",
    accentViolet: "#A8FFCB",
  },
  emerald: {
    50: "#E8FFF0",
    400: "#00FF7F",
    500: "#00E070",
    600: "#00B85C",
    900: "#003D1F",
    bgSubtle: "rgba(0, 255, 127, 0.14)",
    border: "rgba(0, 255, 127, 0.35)",
  },
  cyan: {
    400: "#39FF9E",
    500: "#00E68A",
    600: "#00B86E",
    bgSubtle: "rgba(0, 230, 138, 0.12)",
    border: "rgba(0, 230, 138, 0.3)",
  },
  frontier: {
    glow: "#00FF7F",
    glowCyan: "#39FF9E",
    badgeBg: "rgba(0, 255, 127, 0.2)",
    badgeBorder: "rgba(0, 255, 127, 0.5)",
    text: "#D6FFE8",
    gradientStart: "#012613",
    gradientEnd: "#00110A",
  },
  amber: {
    400: "#FFD400",
    500: "#FFC300",
    600: "#E0A800",
    bgSubtle: "rgba(255, 195, 0, 0.14)",
    border: "rgba(255, 195, 0, 0.4)",
  },
  crimson: {
    400: "#F87171",
    500: "#EF4444",
    600: "#DC2626",
    900: "#7F1D1D",
    bgSubtle: "rgba(239, 68, 68, 0.14)",
    border: "rgba(239, 68, 68, 0.35)",
  },
};

export const THEMES = [midnightTheme, amberTheme, frontierTheme] as const;

export function getThemeColors(id: ThemeId = "midnight") {
  switch (id) {
    case "amber":
      return amberTheme;
    case "frontier":
      return frontierTheme;
    case "midnight":
    default:
      return midnightTheme;
  }
}

// Default export matching standard tokens
export const colors = midnightTheme;
export type Colors = typeof midnightTheme;

/**
 * Bridge for screens not yet migrated to `useTheme().tokens`: maps the new
 * palette onto the legacy color shape so `useTheme().colors` follows the
 * user's light/dark appearance. Screens that import `colors` statically stay
 * on the old midnight values until their owner migrates them.
 */
export function legacyColorsFromTokens(c: ColorTokens): Colors {
  const accentTone = { bgSubtle: c.accent.soft, border: c.accent.solid };
  return {
    ...midnightTheme,
    bg: {
      black: c.bg.sunken,
      terminal: c.bg.canvas,
      surface: c.bg.canvas,
      card: c.bg.surface,
      cardElevated: c.bg.raised,
      cardHover: c.bg.sunken,
      input: c.bg.surface,
      subtle: c.bg.sunken,
      overlay: c.bg.scrim,
      modalOverlay: c.bg.scrim,
    },
    border: {
      subtle: c.line.hairline,
      default: c.line.hairline,
      elevated: c.line.strong,
      focus: c.line.focus,
      emerald: c.field.solid,
      cyan: c.accent.solid,
      amber: c.status.warning.solid,
      danger: c.status.danger.solid,
      frontier: c.accent.solid,
    },
    text: {
      primary: c.text.primary,
      heading: c.text.primary,
      secondary: c.text.secondary,
      muted: c.text.tertiary,
      dim: c.text.tertiary,
      inverse: c.text.onAccent,
      accentEmerald: c.field.text,
      accentCyan: c.accent.text,
      accentAmber: c.status.warning.solid,
      accentViolet: c.accent.text,
    },
    emerald: {
      50: c.field.soft,
      400: c.field.text,
      500: c.field.solid,
      600: c.field.solid,
      900: c.field.soft,
      bgSubtle: c.field.soft,
      border: c.field.solid,
    },
    cyan: { 400: c.accent.text, 500: c.accent.solid, 600: c.accent.pressed, ...accentTone },
    frontier: {
      glow: c.accent.solid,
      glowCyan: c.accent.solid,
      badgeBg: c.accent.soft,
      badgeBorder: c.accent.solid,
      text: c.accent.text,
      gradientStart: c.bg.canvas,
      gradientEnd: c.bg.canvas,
    },
    amber: {
      400: c.status.warning.solid,
      500: c.status.warning.solid,
      600: c.status.warning.solid,
      bgSubtle: c.status.warning.soft,
      border: c.status.warning.solid,
    },
    crimson: {
      400: c.status.danger.solid,
      500: c.status.danger.solid,
      600: c.status.danger.solid,
      900: c.status.danger.soft,
      bgSubtle: c.status.danger.soft,
      border: c.status.danger.solid,
    },
  };
}
