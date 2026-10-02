import React from "react";
import { Pressable, View } from "react-native";
import { icon as iconTokens, useTokens } from "../theme";
import type { IconName } from "./Icon";
import { IconSlot, useOpticalLine } from "./IconText";
import { Text } from "./Text";

export interface TextActionProps {
  label: string;
  onPress: () => void;
  /** Trailing glyph: "chevron-down"/"chevron-up" for an expander, "chevron-right" for navigation. */
  icon?: IconName;
  /** Leading glyph (e.g. "arrow-left" for Back). */
  leadingIcon?: IconName;
  /** Set for an expander: exposes accessibilityState.expanded. */
  expanded?: boolean;
  accessibilityHint?: string;
  accessibilityLabel?: string;
  disabled?: boolean;
  /**
   * `accent`: ember text for the one action that must read as tappable next to secondary text of the
   * same size (the chat's "Answer anyway" under the grey decline line). Counts as the screen's accent.
   */
  tone?: "neutral" | "accent";
}

/**
 * A quiet text action in text.secondary: secondary moves that must not add an accent to the screen
 * ("Show all", "Cancel", "Open settings", "Related in your library", "Back"). The glyph is small and
 * secondary; the touch target is raised to the platform minimum with hitSlop.
 */
export function TextAction({
  label,
  onPress,
  icon,
  leadingIcon,
  expanded,
  accessibilityHint,
  accessibilityLabel,
  disabled,
  tone = "neutral",
}: TextActionProps) {
  const t = useTokens();
  const ink = tone === "accent" ? t.color.accent.text : t.color.text.secondary;
  const line = useOpticalLine("footnote");
  const lineHeight = line.lineHeight;
  const slop = Math.max(0, (t.size.touch - lineHeight) / 2);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!disabled, ...(expanded === undefined ? null : { expanded }) }}
      disabled={disabled}
      onPress={onPress}
      hitSlop={{ top: slop, bottom: slop, left: t.space.sm, right: t.space.sm }}
      style={({ pressed }) => ({ alignSelf: "flex-start", opacity: disabled ? t.opacity.disabled : pressed ? t.opacity.pressed : 1 })}
    >
      <View style={{ flexDirection: "row", alignItems: "flex-start", gap: iconTokens.gap }}>
        {leadingIcon && <IconSlot name={leadingIcon} line={line} color={ink} />}
        <Text variant="footnote" color={tone === "accent" ? "accent" : "secondary"} style={{ flexShrink: 1 }}>
          {label}
        </Text>
        {/* Trailing: its stroke, not its box, meets a row's right edge (Prism CH-20). */}
        {icon && <IconSlot name={icon} line={line} color={ink} edge="end" />}
      </View>
    </Pressable>
  );
}
