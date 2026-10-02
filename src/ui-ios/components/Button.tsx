import React, { forwardRef } from "react";
import { ActivityIndicator, Pressable, PressableProps, StyleProp, StyleSheet, View, ViewStyle } from "react-native";
import { impact, ImpactFeedbackStyle } from "../../services/haptics";
import { space, useTokens } from "../theme";
import type { IconName } from "./Icon";
import { IconText } from "./IconText";

export type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "destructive";

export interface ButtonProps extends Omit<PressableProps, "children" | "style"> {
  label: string;
  variant?: ButtonVariant;
  /** lg = bottom call to action (54, mockup); md = 46 visual on iOS (48 Android); sm = compact row action. */
  size?: "lg" | "md" | "sm";
  icon?: IconName;
  iconPosition?: "start" | "end";
  loading?: boolean;
  fullWidth?: boolean;
  /** `danger` recolours `ghost` and `outline` for a destructive entry point (Remove, Delete) whose confirmation comes next. */
  tone?: "default" | "danger";
  style?: StyleProp<ViewStyle>;
}

/**
 * Text button. Height is always >= the platform touch minimum; `sm` only
 * reduces padding and type, the hit area stays 44/48 via hitSlop.
 */
export const Button = forwardRef<View, ButtonProps>(function Button({
  label,
  variant = "primary",
  size = "md",
  icon,
  iconPosition = "start",
  loading,
  disabled,
  fullWidth,
  tone = "default",
  style,
  onPress,
  ...rest
}, ref) {
  const t = useTokens();
  const c = t.color;
  const inactive = disabled || loading;
  const palette = {
    primary: { bg: c.accent.solid, bgPressed: c.accent.pressed, fg: c.accent.on, border: "transparent" },
    secondary: { bg: c.bg.raised, bgPressed: c.bg.sunken, fg: c.text.primary, border: "transparent" },
    outline: { bg: "transparent", bgPressed: c.bg.sunken, fg: c.text.primary, border: c.line.strong },
    ghost: { bg: "transparent", bgPressed: c.bg.sunken, fg: c.accent.text, border: "transparent" },
    destructive: { bg: c.status.danger.fill, bgPressed: c.status.danger.fill, fg: c.accent.on, border: "transparent" },
  }[variant];
  const danger = tone === "danger" && (variant === "ghost" || variant === "outline");
  if (danger) {
    palette.fg = c.status.danger.solid;
    if (variant === "outline") palette.border = c.status.danger.solid;
  }
  const height = size === "sm" ? t.size.controlSm : size === "lg" ? t.size.buttonLg : Math.max(t.size.touch, t.size.button);
  const slop = Math.max(0, (t.size.touch - height) / 2);

  return (
    <Pressable
      ref={ref}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      disabled={inactive}
      hitSlop={slop}
      onPress={(e) => {
        impact(variant === "destructive" ? ImpactFeedbackStyle.Medium : ImpactFeedbackStyle.Light);
        onPress?.(e);
      }}
      style={({ pressed }) => [
        styles.base,
        {
          minHeight: height,
          paddingHorizontal: size === "sm" ? t.space.md : t.space.lg,
          borderRadius: t.radius.full,
          backgroundColor: pressed ? palette.bgPressed : palette.bg,
          borderColor: palette.border,
          borderWidth: palette.border === "transparent" ? 0 : t.size.border,
          opacity: inactive && !loading ? t.opacity.disabled : pressed && variant === "destructive" ? 0.85 : 1,
          // The ember glow marks the one primary action on a screen.
          ...(variant === "primary" && !inactive ? (t.elevation.glow as object) : null),
        },
        fullWidth && styles.fullWidth,
        style,
      ]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={palette.fg} />
      ) : (
        // Icon and label centred as one group on the label's optical centre (icon-align round):
        // the icon sits on line 1 and never pushes the label off the button's middle.
        <IconText
          icon={icon}
          iconPosition={iconPosition}
          variant={size === "sm" ? "subhead" : size === "lg" ? "buttonLg" : "button"}
          tint={palette.fg}
          numberOfLines={2}
          align="center"
          centerOnBox
        >
          {label}
        </IconText>
      )}
    </Pressable>
  );
});

const styles = StyleSheet.create({
  base: { alignItems: "center", justifyContent: "center", paddingVertical: space.sm },
  fullWidth: { alignSelf: "stretch" },
});
