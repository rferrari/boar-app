import React, { ReactNode, useEffect, useRef } from "react";
import Animated from "react-native-reanimated";
import { useMotion } from "../theme/motion";

/**
 * A row sent in this run enters once (SEND-MOTION S1): the DS `enter` from below. Decided at mount and
 * reported through `onEntered`, so a row the list mounts again after scrolling shows in place, and
 * history rows get no wrapper at all.
 * `afterSwap`: the first question of a conversation replaces the empty state; it enters once that state has
 * left (the DS crossfade: exit in the swap half, then enter), never drawn over it (Prism F2-4b).
 */
export function EnterOnce({
  enter,
  onEntered,
  children,
}: {
  enter: false | "now" | "afterSwap";
  onEntered: () => void;
  children: ReactNode;
}) {
  const m = useMotion();
  // After the swap: wait out the empty state's exit (the swap half), as the DS crossfade does.
  const swapOut = m.spec("exit", { swap: true }).duration;
  const entering = useRef(
    enter === "afterSwap" ? m.entering({ from: "below", delay: swapOut }) : enter ? m.entering({ from: "below" }) : null
  ).current;
  useEffect(() => {
    if (entering) onEntered();
    // Once, at mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  if (!entering) return <>{children}</>;
  return <Animated.View entering={entering}>{children}</Animated.View>;
}
