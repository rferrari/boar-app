import React from "react";
import { Pressable, StyleProp, View, ViewProps, ViewStyle } from "react-native";
import { useTokens } from "../theme";

export interface CardProps extends ViewProps {
  /** Elevation level. Dark mode draws levels with surface lightness, not shadows. */
  level?: 0 | 1 | 2;
  /** md 16 · compact 12/14 (mockup suggestion and steps cards) · sm 12 · none. */
  padding?: "none" | "sm" | "compact" | "md";
  /** card 18 (compact cards) · lg 20 (default) · hero 22 (download, model error). */
  radius?: "card" | "lg" | "hero";
  onPress?: () => void;
  /** Secondary action (e.g. edit). Also offer it another way: long-press is hard to discover. */
  onLongPress?: () => void;
  accessibilityHint?: string;
  accessibilityLabel?: string;
  style?: StyleProp<ViewStyle>;
}

/** Grouped content on a surface with a hairline. Hairline first, shadow second. */
export function Card({ level = 1, padding = "md", radius = "lg", onPress, onLongPress, style, children, ...rest }: CardProps) {
  const t = useTokens();
  const base: ViewStyle = {
    backgroundColor: level === 2 ? t.color.bg.raised : t.color.bg.surface,
    borderRadius: t.radius[radius],
    // Dark: planes read by lightness alone (mockup). Light: a hairline keeps cards on paper.
    borderWidth: t.scheme === "light" ? t.size.hairline : 0,
    borderColor: t.color.line.hairline,
    ...(padding === "compact"
      ? { paddingVertical: t.space.md, paddingHorizontal: t.space.md + t.space.xxs }
      : { padding: padding === "none" ? 0 : padding === "sm" ? t.space.md : t.space.base }),
    ...(t.elevation[level] as ViewStyle),
  };
  if (onPress || onLongPress) {
    return (
      <Pressable
        accessibilityRole="button"
        onPress={onPress}
        onLongPress={onLongPress}
        style={({ pressed }) => [base, pressed && { backgroundColor: t.color.bg.sunken }, style]}
        {...rest}
      >
        {children}
      </Pressable>
    );
  }
  return (
    <View style={[base, style]} {...rest}>
      {children}
    </View>
  );
}
