import React from "react";
import { useWindowDimensions, View } from "react-native";
import { useTokens } from "../theme";
import { stepperValue } from "./stepperA11y";
import { Text } from "./Text";
import { LARGE_TEXT_SCALE } from "./ListRow";

export interface StepperProps {
  /** Short step names, already translated ("Hardware", "Model", "Install", "Index"). */
  steps: string[];
  /** 0-based index of the current step. Steps before it read as done. */
  current: number;
  /** Spoken summary, e.g. "Step 2 of 4: Model". The visual labels are hidden from screen readers. */
  accessibilityLabel: string;
}

/**
 * Segmented progress for a linear flow (mockup: 4 pt bars 6 pt apart, caps labels 6 pt below).
 * Labels are caps at 11 pt; a long translation shrinks to fit its column rather than wrapping.
 */
export function Stepper({ steps, current, accessibilityLabel }: StepperProps) {
  const t = useTokens();
  // Large text: the caps labels grow to 1.5x and nearly touched at 6 pt (Prism NA-2).
  const gap = useWindowDimensions().fontScale >= LARGE_TEXT_SCALE ? t.space.md : t.space.xs + t.space.xxs;
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel}
      accessibilityValue={stepperValue(steps.length, current)}
      style={{ flexDirection: "row", gap }}
    >
      {steps.map((step, i) => (
        <View key={step} style={{ flex: 1, gap: t.space.xs + t.space.xxs }} importantForAccessibility="no-hide-descendants">
          <View
            style={{
              height: t.space.xs,
              borderRadius: t.radius.full,
              backgroundColor: i <= current ? t.color.accent.solid : t.color.bg.raised,
            }}
          />
          <Text
            variant="step"
            color={i === current ? "primary" : "secondary"}
            numberOfLines={1}
            adjustsFontSizeToFit
            // The badge/chip cap (DS §3), not a lower one of its own (Prism FD-4); the spoken summary covers 2.0.
            maxFontSizeMultiplier={1.5}
          >
            {step}
          </Text>
        </View>
      ))}
    </View>
  );
}
