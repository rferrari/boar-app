import React from "react";
import { View } from "react-native";
import { useTokens } from "../theme";
import { Text } from "./Text";
import type { TextColor } from "./Text";

export interface StatProps {
  /** The number, already formatted ("978", "62", "1.4"). */
  value: string;
  /** Unit drawn smaller after the number ("MB", "%", "tok/s"). */
  unit?: string;
  /** Overline above the number ("DOWNLOAD", "DOWNLOADING WEIGHTS"). */
  label?: string;
  /** xl = the screen's one figure (56, download %), lg = 34, md = card lead (26), sm = inline trailing figure (17). */
  size?: "xl" | "lg" | "md" | "sm";
  align?: "left" | "right";
  color?: TextColor;
}

/**
 * Trim of the hero's extra leading (measured on Android against the mockup, Prism A3-1 34efdf8). One
 * value for both platforms: Text's iOS inset now draws the digits at Android's baseline (28/09); the
 * old iOS trim (+0.09/-0.325) compensated digits riding 11 pt higher in the box.
 */
function xlTrim(size: number) {
  return { marginTop: -size * 0.125, marginBottom: -size * 0.13 };
}

const VALUE_VARIANT = { xl: "hero", lg: "display", md: "title1", sm: "headline" } as const;
const UNIT_VARIANT = { xl: "title2", lg: "title3", md: "headline", sm: "footnote" } as const;

/** A number that leads: big, tabular, with its unit and an optional overline. One per card at most. */
export function Stat({ value, unit, label, size = "md", align = "left", color = "primary" }: StatProps) {
  const t = useTokens();
  // Mockup: a percentage is one run ("62%" at the figure's size); other units ("MB", "tok/s") sit smaller.
  const joined = unit === "%";
  const shown = joined ? `${value}%` : value;
  return (
    <View
      accessible
      accessibilityLabel={[label, unit ? `${value} ${unit}` : value].filter(Boolean).join(": ")}
      style={{ alignItems: align === "right" ? "flex-end" : "flex-start", gap: t.space.sm + t.space.xxs }}
    >
      {label ? <Text variant="label" color="field">{label}</Text> : null}
      <View style={{ flexDirection: "row", alignItems: "baseline", gap: t.space.xxs }}>
        <Text
          variant={VALUE_VARIANT[size]}
          color={color}
          numeric
          header={false}
          // xl: `hero` keeps a 1.2 line box; the digits sit centred in it on both platforms (Android's
          // half-leading, iOS through Text's inset), so the trim is the same symmetric one.
          style={size === "xl" ? xlTrim(t.type.hero.fontSize ?? 0) : undefined}
        >
          {shown}
        </Text>
        {unit && !joined ? (
          <Text variant={UNIT_VARIANT[size]} color="secondary" numeric>
            {unit}
          </Text>
        ) : null}
      </View>
    </View>
  );
}
