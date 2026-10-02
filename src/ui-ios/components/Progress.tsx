import React, { useEffect, useMemo, useRef, useState } from "react";
import { Animated, Easing, View } from "react-native";
import { Tokens, useTheme } from "../theme";

export interface ProgressProps {
  /** 0..1. Omit for indeterminate. */
  value?: number;
  /** Spoken value, e.g. "340 of 1,020 MB". Defaults to the percentage. */
  valueText?: string;
  label: string;
  tone?: "accent" | "field" | "danger";
  height?: number;
  /** Draw with these tokens instead of the user's theme (the boot splash is always dark Fogueira). */
  tokens?: Tokens;
}

/** Linear progress. Determinate when `value` is set; otherwise an indeterminate sweep (static under reduce motion). */
export function Progress({ value, valueText, label, tone = "accent", height = 10, tokens }: ProgressProps) {
  const theme = useTheme();
  const t = tokens ?? theme.tokens;
  const reduceMotion = theme.reduceMotion;
  // The sweep travels the bar's own width, so any width shows it on most frames (a fixed -160..400
  // px left a 140 pt bar empty ~65% of the time; Loom).
  const [width, setWidth] = useState(0);
  const sweep = useRef(new Animated.Value(0)).current;
  // Built once per value (and width), not on every render: a new interpolation rewires the native animated graph (perf audit #11).
  const sweepX = useMemo(() => sweep.interpolate({ inputRange: [0, 1], outputRange: [-0.4 * width, width] }), [sweep, width]);
  const indeterminate = value === undefined;
  const fill = tone === "danger" ? t.color.status.danger.solid : tone === "field" ? t.color.field.solid : t.color.accent.solid;

  useEffect(() => {
    if (!indeterminate || reduceMotion) return;
    const loop = Animated.loop(
      Animated.timing(sweep, { toValue: 1, duration: t.motion.loop.sweep, easing: Easing.inOut(Easing.quad), useNativeDriver: true })
    );
    loop.start();
    return () => loop.stop();
  }, [indeterminate, reduceMotion, sweep, t.motion]);

  const pct = value === undefined ? 0 : Math.round(Math.min(1, Math.max(0, value)) * 100);
  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityState={{ busy: indeterminate }}
      accessibilityValue={indeterminate ? { text: valueText } : { min: 0, max: 100, now: pct, text: valueText ?? `${pct}%` }}
      onLayout={indeterminate ? (e) => setWidth(e.nativeEvent.layout.width) : undefined}
      style={{ height, borderRadius: height, backgroundColor: t.color.bg.raised, overflow: indeterminate ? "hidden" : "visible" }}
    >
      {indeterminate ? (
        <Animated.View
          style={{
            width: "40%",
            height: "100%",
            borderRadius: height,
            backgroundColor: fill,
            opacity: reduceMotion ? 0.5 : 1,
            ...(tone === "accent" ? { boxShadow: `0px 0px 14px rgba(${t.color.glow}, 0.6)` } : null),
            transform: [{ translateX: sweepX }],
          }}
        />
      ) : (
        <View
          style={{
            width: `${pct}%`,
            height: "100%",
            borderRadius: height,
            backgroundColor: fill,
            // Mockup: the ember fill glows (0 0 14 @.6); other tones stay flat.
            ...(tone === "accent" ? { boxShadow: `0px 0px 14px rgba(${t.color.glow}, 0.6)` } : null),
          }}
        />
      )}
    </View>
  );
}
