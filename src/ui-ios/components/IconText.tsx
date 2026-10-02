import React from "react";
import { Platform, StyleProp, TextStyle, useWindowDimensions, View, ViewStyle } from "react-native";
import { icon as iconTokens, opticalOffset, useTokens, variantShape } from "../theme";
import type { TextVariant } from "../theme";
import { endBearing, Icon, IconName } from "./Icon";
import { Text, TextColor, TextProps } from "./Text";

const TITLE_VARIANTS: TextVariant[] = ["hero", "wordmark", "display", "title1", "title2", "title3", "headline", "cardTitle", "buttonLg"];
const SEAL_VARIANTS: TextVariant[] = ["badge", "label", "step", "seal", "capsMeta"];

export type IconRole = "body" | "title" | "seal";

/** The icon size that goes with a text variant (tokens.icon): title 20, caps seal 13, everything else 16. */
export function iconRoleFor(variant: TextVariant): IconRole {
  if (TITLE_VARIANTS.includes(variant)) return "title";
  if (SEAL_VARIANTS.includes(variant)) return "seal";
  return "body";
}

export interface OpticalLine {
  /** Rendered line height of the first line (after the OS font scale). */
  lineHeight: number;
  /** Optical centre of that line relative to the middle of its line box (pt, + = below). */
  offset: number;
  /** Icon size for `role`, grown with the text. */
  iconSize: number;
}

/**
 * Geometry for placing something (icon, radio, dot) on the optical centre of a text's first line,
 * on this platform and at the current text size. See theme/opticalCenter.ts.
 */
export function useOpticalLine(variant: TextVariant, role: IconRole = iconRoleFor(variant), maxFontSizeMultiplier?: number): OpticalLine {
  const t = useTokens();
  const { fontScale } = useWindowDimensions();
  const shape = variantShape(variant);
  const cap = maxFontSizeMultiplier ?? shape.maxScale;
  // RN scales size and line height by the OS scale, capped by maxFontSizeMultiplier (>= 1 caps).
  const k = cap && cap >= 1 ? Math.min(fontScale, cap) : fontScale;
  const style = t.type[variant];
  const fontSize = (style.fontSize ?? 16) * k;
  const lineHeight = (style.lineHeight ?? fontSize) * k;
  // Includes Text's iOS inset (theme/opticalCenter.ts), so the icon follows the glyphs it sits beside.
  const offset = opticalOffset({ face: shape.face, uppercase: shape.uppercase, fontSize, lineHeight, platform: Platform.OS === "ios" ? "ios" : "android" });
  // The in-app size preference is already in the type tokens; body 16 at 1.0 tells us its factor.
  const appScale = (t.type.body.fontSize ?? 16) / 16;
  const base = role === "title" ? iconTokens.sizeTitle : role === "seal" ? iconTokens.sizeSeal : iconTokens.sizeBody;
  const iconSize = Math.round(base * appScale * Math.min(k, iconTokens.maxScale));
  return { lineHeight, offset, iconSize };
}

export interface LineSlotProps {
  line: OpticalLine;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

/**
 * A box one text line tall, nudged onto the line's optical centre, for anything that is not a glyph
 * (spinner, dot, radio, badge). Put it in a row with alignItems "flex-start".
 */
export function LineSlot({ line, children, style }: LineSlotProps) {
  return (
    <View style={[{ height: line.lineHeight, justifyContent: "center", transform: [{ translateY: line.offset }] }, style]}>{children}</View>
  );
}

export interface IconSlotProps {
  name: IconName;
  line: OpticalLine;
  color?: string;
  size?: number;
  /** Trailing icon: aligns its stroke, not its box, with the row's right edge (see Icon `edge`). */
  edge?: "end";
}

/** An icon in a box one text line tall, nudged onto the line's optical centre. For custom rows. */
export function IconSlot({ name, line, color, size, edge }: IconSlotProps) {
  const px = size ?? line.iconSize;
  return (
    <LineSlot line={line} style={{ width: px, marginRight: edge === "end" ? -endBearing(name, px) : undefined }}>
      <Icon name={name} size={px} color={color} />
    </LineSlot>
  );
}

export interface IconTextProps {
  icon?: IconName;
  children: React.ReactNode;
  variant?: TextVariant;
  color?: TextColor;
  /** Raw colour for text and icon (buttons, seals on a fill); wins over `color`. */
  tint?: string;
  iconColor?: string;
  /** `tight` (6) inside chips and seals; default 8. */
  gap?: "default" | "tight";
  iconPosition?: "start" | "end";
  /** With iconPosition "end" at a row's right edge: line up the glyph's stroke, not its box (CH-20). */
  edge?: "end";
  /** Overrides the size that goes with the variant (tokens.icon). */
  iconRole?: IconRole;
  /**
   * In a pill or button: moves icon and text together so the text's optical centre, not its box,
   * sits in the middle of the container (iOS draws Baloo up to 4 pt high).
   */
  centerOnBox?: boolean;
  numberOfLines?: number;
  align?: TextProps["align"];
  weight?: TextProps["weight"];
  numeric?: boolean;
  maxFontSizeMultiplier?: number;
  textStyle?: StyleProp<TextStyle>;
  style?: StyleProp<ViewStyle>;
}

/**
 * Icon + text on one row: the icon sits on the optical centre of the FIRST line (a wrapped label
 * keeps its icon beside line 1), one gap token, icon size from the variant and grown with the text.
 * The icon is decorative; the text is the accessible name.
 */
export function IconText({
  icon,
  children,
  variant = "body",
  color = "primary",
  tint,
  iconColor,
  gap = "default",
  iconPosition = "start",
  edge,
  iconRole,
  centerOnBox,
  numberOfLines,
  align,
  weight,
  numeric,
  maxFontSizeMultiplier,
  textStyle,
  style,
}: IconTextProps) {
  const line = useOpticalLine(variant, iconRole ?? iconRoleFor(variant), maxFontSizeMultiplier);
  return (
    <View
      style={[
        {
          flexDirection: iconPosition === "end" ? "row-reverse" : "row",
          alignItems: "flex-start",
          gap: gap === "tight" ? iconTokens.gapTight : iconTokens.gap,
          flexShrink: 1,
        },
        centerOnBox && { transform: [{ translateY: -line.offset }] },
        style,
      ]}
    >
      {icon && <IconSlot name={icon} line={line} color={iconColor ?? tint} edge={iconPosition === "end" ? edge : undefined} />}
      <Text
        variant={variant}
        color={color}
        numberOfLines={numberOfLines}
        align={align}
        weight={weight}
        numeric={numeric}
        maxFontSizeMultiplier={maxFontSizeMultiplier}
        style={[{ flexShrink: 1 }, tint ? { color: tint } : null, textStyle]}
      >
        {children}
      </Text>
    </View>
  );
}
