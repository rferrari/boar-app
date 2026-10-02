import React from "react";
import { Platform, Switch as RNSwitch } from "react-native";
import { selection } from "../../services/haptics";
import { Tokens, useTokens } from "../theme";

export interface SwitchProps {
  value: boolean;
  onValueChange: (value: boolean) => void;
  /** The row text this switch controls (read as the switch's name, not "on/off"). */
  label: string;
  disabled?: boolean;
}

/**
 * Colours for any native switch (this one and ListRow's). Android paints the
 * thumb from thumbColor: a dark thumb read as a blob on the ember track, so it
 * is light when on and muted when off (Material); iOS keeps its white thumb.
 */
export function switchColors(t: Tokens, value: boolean) {
  return {
    trackColor: { false: t.color.line.strong, true: t.color.accent.solid },
    ios_backgroundColor: t.color.line.strong,
    thumbColor: Platform.OS === "android" ? (value ? t.color.text.primary : t.color.text.secondary) : undefined,
  };
}

/** Native switch with theme colors. Use only for settings that take effect immediately. */
export function Switch({ value, onValueChange, label, disabled }: SwitchProps) {
  const t = useTokens();
  return (
    <RNSwitch
      accessibilityLabel={label}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
      value={value}
      disabled={disabled}
      onValueChange={(next) => {
        selection();
        onValueChange(next);
      }}
      {...switchColors(t, value)}
    />
  );
}
