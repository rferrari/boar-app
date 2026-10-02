import React from "react";
import { View } from "react-native";
import Feather from "@expo/vector-icons/Feather";
import { useTokens } from "../theme";
import { iconA11yProps } from "./iconA11y";

/**
 * Single icon set for the whole app: Feather (stroke icons, consistent 2px
 * weight), bundled with the app so it works offline. No emoji as icons.
 * This is the only file allowed to import @expo/vector-icons (iconA11y.test.ts).
 */
export type IconName = React.ComponentProps<typeof Feather>["name"];

export interface IconProps {
  name: IconName;
  size?: "sm" | "md" | "lg" | number;
  color?: string;
  /**
   * Icons are decorative by default and removed from the accessibility tree.
   * Give a label only when the icon alone carries meaning outside a control
   * (a status glyph with no text). Icon-only buttons use IconButton, whose
   * `label` is required.
   */
  label?: string;
  /**
   * Trailing icon (chevron, check, x): pulls the glyph out by its empty side bearing so the visible
   * stroke, not the 24-grid box, lines up with the row's right edge. Icons at the end of rows share
   * one edge that way.
   */
  edge?: "end";
}

/**
 * Empty space right of the stroke, in 24-grid units (Feather paths + half the 2-unit stroke).
 * Only the glyphs we put at a trailing edge.
 */
const END_BEARING: Partial<Record<IconName, number>> = {
  "chevron-right": 8,
  "chevron-down": 5,
  "chevron-up": 5,
  "chevron-left": 8,
  check: 3,
  x: 5,
  "external-link": 2,
  "maximize-2": 2,
};

export function endBearing(name: IconName, px: number): number {
  return ((END_BEARING[name] ?? 0) / 24) * px;
}

export function Icon({ name, size = "md", color, label, edge }: IconProps) {
  const t = useTokens();
  const px = typeof size === "number" ? size : { sm: t.size.iconSm, md: t.size.icon, lg: t.size.iconLg }[size];
  const a11y = iconA11yProps(label);
  return (
    <View {...a11y.wrapper} pointerEvents="none" style={{ width: px, height: px, alignItems: "center", justifyContent: "center", marginRight: edge === "end" ? -endBearing(name, px) : undefined }}>
      <Feather name={name} size={px} color={color ?? t.color.text.secondary} {...a11y.glyph} />
    </View>
  );
}
