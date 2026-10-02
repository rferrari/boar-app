import React, { forwardRef } from "react";
import { PixelRatio, Platform, StyleSheet, Text as RNText, TextProps as RNTextProps, TextStyle } from "react-native";
import { fontFamilyFor, useTokens, variantFace } from "../theme";
import type { TextVariant, Tokens } from "../theme";
import { cappedScale, iosTextInset } from "./textInset";

export type TextColor = "primary" | "secondary" | "tertiary" | "accent" | "field" | "onAccent" | "danger" | "success" | "warning" | "info";

export interface TextProps extends RNTextProps {
  variant?: TextVariant;
  color?: TextColor;
  /** Tabular figures for values that update in place (tok/s, sizes, counters). */
  numeric?: boolean;
  weight?: "regular" | "medium" | "semibold" | "bold";
  align?: "left" | "center" | "right";
  /** Exposes the text as a heading to screen readers. Defaults on for display/title variants. */
  header?: boolean;
}

const WEIGHTS = { regular: 400, medium: 500, semibold: 600, bold: 700 } as const;
const HEADER_VARIANTS: TextVariant[] = ["display", "title1", "title2", "title3"];

/**
 * The only way to draw text. Respects the OS font scale and the in-app size preference.
 * Forwards its ref, e.g. to move screen-reader focus to a title.
 */
const TABULAR: TextStyle = { fontVariant: ["tabular-nums"] };

/** The colour of a role, without building a 10-entry map on every render (perf audit #24). */
function colorFor(t: Tokens, color: TextColor): string {
  switch (color) {
    case "primary":
      return t.color.text.primary;
    case "secondary":
      return t.color.text.secondary;
    case "tertiary":
      return t.color.text.tertiary;
    case "accent":
      return t.color.text.accent;
    case "field":
      return t.color.text.field;
    case "onAccent":
      return t.color.text.onAccent;
    case "danger":
      return t.color.status.danger.solid;
    case "success":
      return t.color.status.success.solid;
    case "warning":
      return t.color.status.warning.solid;
    case "info":
      return t.color.status.info.solid;
  }
}

export const Text = forwardRef<RNText, TextProps>(function Text({
  variant = "body",
  color = "primary",
  numeric,
  weight,
  align,
  header,
  style,
  ...rest
}, ref) {
  const t = useTokens();
  const { maxFontSizeMultiplier, ...typeStyle } = t.type[variant];
  const colorValue = colorFor(t, color);
  const isHeader = header ?? HEADER_VARIANTS.includes(variant);
  const cap = rest.maxFontSizeMultiplier ?? maxFontSizeMultiplier;
  const composed = [
    typeStyle,
    { color: colorValue },
    numeric && TABULAR,
    // Custom fonts: switch family per weight instead of setting fontWeight.
    weight && { fontFamily: fontFamilyFor(variantFace(variant), WEIGHTS[weight]) },
    align && { textAlign: align },
    style,
  ];
  // iOS clips Baloo's ascenders and PT accents when the line is shorter than the font ('Doar', 'Área'):
  // pad the glyphs down and give the space back below (theme/opticalCenter.ts). The OS scale is read
  // once per render: a font-scale change remounts the navigation tree (FS-1).
  const inset =
    Platform.OS === "ios" && variantFace(variant) === "display"
      ? iosTextInset("display", StyleSheet.flatten(composed), cappedScale(PixelRatio.getFontScale(), cap, rest.allowFontScaling))
      : null;
  return (
    <RNText
      // Spread first: an explicit `maxFontSizeMultiplier={undefined}` (IconText passes one) must not
      // erase the variant's cap, or text and its icon scale apart (Prism FD-5).
      {...rest}
      ref={ref}
      accessibilityRole={isHeader ? "header" : rest.accessibilityRole}
      maxFontSizeMultiplier={cap}
      style={inset ? [composed, inset] : composed}
    />
  );
});
