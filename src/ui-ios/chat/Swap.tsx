import React, { ReactNode, useMemo } from "react";
import type { StyleProp, ViewStyle } from "react-native";
import Animated, { LayoutAnimationConfig } from "react-native-reanimated";
import { useMotion } from "../theme/motion";

/**
 * Content that changes kind in place (SEND-MOTION): the running pill becoming the receipt, "N passages"
 * becoming the source list. The DS crossfade (old one out in the swap half, new one in after it), keyed
 * by `swapKey`; the first content shows in place (restored history doesn't fade in).
 */
export function Swap({ swapKey, style, children }: { swapKey: string; style?: StyleProp<ViewStyle>; children: ReactNode }) {
  const m = useMotion();
  const x = useMemo(() => m.crossfade("none"), [m]);
  return (
    <LayoutAnimationConfig skipEntering>
      <Animated.View key={swapKey} entering={x.entering} exiting={x.exiting} style={style}>
        {children}
      </Animated.View>
    </LayoutAnimationConfig>
  );
}
