import React from "react";
import { Pressable, View } from "react-native";
import { selection } from "../../services/haptics";
import { toneColors, Tone, useTokens } from "../theme";
import type { IconName } from "./Icon";
import { IconText } from "./IconText";

export interface ChipProps {
  label: string;
  icon?: IconName;
  tone?: Tone;
  /** Toggle chips expose `selected` to screen readers. */
  selected?: boolean;
  onPress?: () => void;
  /**
   * `inline` is the citation marker [n] inside running text: compact visual,
   * but still a 44/48 hit area through hitSlop when pressable.
   */
  size?: "md" | "sm" | "inline";
  accessibilityLabel?: string;
  accessibilityHint?: string;
}

export function Chip({ label, icon, tone = "neutral", selected, onPress, size = "md", accessibilityLabel, accessibilityHint }: ChipProps) {
  const t = useTokens();
  const tc = toneColors(t.color, selected ? "accent" : tone);
  const height = size === "inline" ? 20 : size === "sm" ? 30 : 36;
  const slop = Math.max(0, (t.size.touch - height) / 2);
  const body = (
    <IconText
      icon={icon}
      variant={size === "inline" ? "caption" : size === "sm" ? "footnote" : "subhead"}
      iconRole={size === "md" ? "body" : "seal"}
      gap="tight"
      weight={size === "inline" ? "semibold" : "medium"}
      numeric={size === "inline"}
      tint={tc.fg}
      maxFontSizeMultiplier={size === "inline" ? 1.5 : undefined}
      centerOnBox
    >
      {label}
    </IconText>
  );
  const style = {
    flexDirection: "row" as const,
    alignItems: "center" as const,
    alignSelf: "flex-start" as const,
    minHeight: height,
    paddingHorizontal: size === "inline" ? t.space.xs + 2 : t.space.md,
    borderRadius: size === "inline" ? t.radius.xs : t.radius.full,
    backgroundColor: tone === "neutral" && !selected ? t.color.bg.raised : tc.bg,
    borderWidth: selected ? t.size.border : 0,
    borderColor: t.color.accent.solid,
  };
  if (!onPress) {
    return <View style={style}>{body}</View>;
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityHint={accessibilityHint}
      accessibilityState={selected === undefined ? undefined : { selected }}
      hitSlop={{ top: slop, bottom: slop, left: size === "inline" ? 6 : slop, right: size === "inline" ? 6 : slop }}
      onPress={() => {
        selection();
        onPress();
      }}
      style={({ pressed }) => [style, pressed && { opacity: 0.7 }]}
    >
      {body}
    </Pressable>
  );
}
