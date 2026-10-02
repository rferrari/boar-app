import { colors, getThemeColors, THEMES, midnightTheme, amberTheme, frontierTheme } from "./colors";
import { typography, fontFamilies, getTypography, FONT_SCALES, Typography } from "./typography";
import { spacing, radii, shadows } from "./spacing";
import { ThemeProvider, useTheme, useTokens } from "./ThemeContext";
export { buildTokens, toneColors, variantFace, variantShape, icon, space, radius, size, motion, opacity, MIN_TOUCH, MIN_FONT_SIZE, APP_FONT_SCALE } from "./tokens";
export { fontFamilyFor, BUNDLED_FAMILIES } from "./fonts";
export { opticalOffset, baselineFromTop, iosAscenderInset, renderedBaseline, FACE_METRICS } from "./opticalCenter";
export type { FontFace, FontWeight } from "./fonts";
export type { Tokens, ColorTokens, ColorScheme, TextVariant, Tone, TypeStyle } from "./tokens";
export { getPalette, PALETTE_IDS, SOURCE_PALETTES } from "./palette";
export type { PaletteId, ResolvedPalette } from "./palette";
export { resolveScheme } from "./scheme";

export {
  colors,
  getThemeColors,
  THEMES,
  midnightTheme,
  amberTheme,
  frontierTheme,
  typography,
  fontFamilies,
  getTypography,
  FONT_SCALES,
  spacing,
  radii,
  shadows,
  ThemeProvider,
  useTheme,
  useTokens,
};

export type { Typography };

export const theme = {
  colors,
  typography,
  fontFamilies,
  spacing,
  radii,
  shadows,
  badges: {
    emerald: {
      backgroundColor: colors.emerald.bgSubtle,
      borderColor: colors.emerald.border,
      borderWidth: 1,
      color: colors.text.accentEmerald,
    },
    cyan: {
      backgroundColor: colors.cyan.bgSubtle,
      borderColor: colors.cyan.border,
      borderWidth: 1,
      color: colors.text.accentCyan,
    },
    amber: {
      backgroundColor: colors.amber.bgSubtle,
      borderColor: colors.amber.border,
      borderWidth: 1,
      color: colors.text.accentAmber,
    },
    crimson: {
      backgroundColor: colors.crimson.bgSubtle,
      borderColor: colors.crimson.border,
      borderWidth: 1,
      color: colors.crimson[400],
    },
    frontier: {
      backgroundColor: colors.frontier.badgeBg,
      borderColor: colors.frontier.badgeBorder,
      borderWidth: 1,
      color: colors.frontier.text,
    },
  },
} as const;

export default theme;
