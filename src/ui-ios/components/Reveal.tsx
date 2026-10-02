import React, { useRef } from "react";
import { StyleProp, ViewStyle } from "react-native";
import Animated from "react-native-reanimated";
import { useMotion } from "../theme/motion";

/**
 * The content that replaces a loading state (a Skeleton, an empty well). It fades in with the
 * `enter` role when it arrives after the screen was already on screen, and draws at once when the
 * data was ready at the first render (the stack push already animates the screen) (Iris TR-5).
 *
 *   const lateLoad = useLateLoad(loaded);   // above any early return
 *   ...
 *   <Reveal animate={lateLoad} style={screenRhythm(tokens)}>...</Reveal>
 */
export function Reveal({ animate, style, children }: { animate: boolean; style?: StyleProp<ViewStyle>; children: React.ReactNode }) {
  const m = useMotion();
  return (
    <Animated.View style={style} entering={animate ? m.entering() : undefined}>
      {children}
    </Animated.View>
  );
}

/** True when the screen first rendered while still loading: its content should Reveal when it comes. */
export function useLateLoad(loaded: boolean): boolean {
  return useRef(!loaded).current;
}
