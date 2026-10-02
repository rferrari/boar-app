import React, { useEffect, useRef } from "react";
import { Animated, DimensionValue, Easing } from "react-native";
import { useTheme } from "../theme";
import { useMotion } from "../theme/motion";

export interface SkeletonProps {
  width?: DimensionValue;
  height?: number;
  radius?: number;
}

const PULSE_LOW = 0.55;

/**
 * Loading placeholder. Hidden from screen readers: the screen announces
 * "loading" once (useAnnounce) instead. It keeps its space from the first frame but only shows after
 * motion.delay.skeleton (150 ms), fading in (`enter`), so a fast load never blinks a skeleton (Iris
 * TR-5). Then it pulses gently; static under reduce motion.
 */
export function Skeleton({ width = "100%", height = 14, radius }: SkeletonProps) {
  const { tokens: t, reduceMotion } = useTheme();
  const m = useMotion();
  const pulse = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const half = { duration: t.motion.loop.pulse / 2, easing: Easing.inOut(Easing.sin), useNativeDriver: true };
    const appear = Animated.sequence([
      Animated.delay(t.motion.delay.skeleton),
      Animated.timing(pulse, { toValue: PULSE_LOW, ...m.timing("enter"), useNativeDriver: true }),
    ]);
    const anim = reduceMotion
      ? appear
      : Animated.sequence([
          appear,
          Animated.loop(Animated.sequence([Animated.timing(pulse, { toValue: 1, ...half }), Animated.timing(pulse, { toValue: PULSE_LOW, ...half })])),
        ]);
    anim.start();
    return () => anim.stop();
  }, [pulse, reduceMotion, t.motion, m]);
  return (
    <Animated.View
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={{
        width,
        height,
        borderRadius: radius ?? t.radius.xs,
        backgroundColor: t.color.bg.sunken,
        opacity: pulse,
      }}
    />
  );
}
