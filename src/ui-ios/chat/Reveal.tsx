import React, { ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { LayoutChangeEvent, View } from "react-native";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";
import { useTheme } from "../theme";
import { useMotion } from "../theme/motion";
import { CURVE, type Curve } from "../theme/motionSpec";
import { REST, boundsAfter, boundsAtStart, hideSteps, revealDeadline, revealMove, revealOnLayout, revealTiming, type RevealMove } from "./revealTiming";

// Every timing here passes ReduceMotion.Never: the DS applies reduce motion itself (motionSpec: instant
// layout, a 90 ms fade), as theme/motion.ts does. Reanimated's default (System) skips an animation to its end
// when the OS setting is on: on the iPhone with Reduce Motion every Reveal move ended within a frame, the 90 ms
// fade included, and the declined text vanished (probe cddf2a8, F2-2).
const curves: Record<Curve, ReturnType<typeof Easing.bezier>> = {
  standard: Easing.bezier(...CURVE.standard),
  enter: Easing.bezier(...CURVE.enter),
  exit: Easing.bezier(...CURVE.exit),
};

/**
 * A block of the answer that enters and leaves without a jump (SEND-MOTION D2). Its frame's height
 * animates on the UI thread, so the list below and the scroll anchored at the end follow it frame by
 * frame; Reanimated's `layout` only moves the drawing while the list's size changes at once. Durations
 * and curves are the DS roles (revealTiming → motionSpec).
 *
 * SAFETY (BUG-reveal-empty, iPhone 13 f7e8eb2): the frame never hides content that is shown. It moves
 * min/max height bounds, never `height`, and every move of a shown block ends at REST (no clamp), so
 * content that grows later (streamed text) is never cut, even if Fabric keeps the last animated value.
 * If a move or the first measurement isn't over by revealDeadline(), REST is forced.
 * At rest the block is its content's height: later changes (a fold opening) follow at once, animated by
 * the DS's animateNextLayout where the caller asks for it.
 *
 * - `shown`: false makes it leave (plan B, iPhone F2-2): its opacity fades while its height stays as it is
 *   (nothing is cut, no bound moves), then, invisible, it unmounts under the DS animateNextLayout, so only
 *   the blocks around it slide into the space. The last content stays while it fades.
 * - `appear`: grows from 0 on its first layout (asked in this run); false shows it in place (history).
 *   A block that mounts hidden and shows later always grows.
 * - `spaceBefore`: the gap above it, inside the moving frame (a parent's `gap` would outlive it).
 */
export function Reveal({
  shown = true,
  appear = true,
  spaceBefore = 0,
  children,
}: {
  shown?: boolean;
  appear?: boolean;
  spaceBefore?: number;
  children?: ReactNode;
}) {
  const { reduceMotion } = useTheme();
  const motion = useMotion();
  const [mounted, setMounted] = useState(shown);
  // Left (faded and unmounted) at least once: showing again grows from nothing.
  const hidden = useRef(false);
  const grows = useRef(appear || !shown).current;
  const natural = useRef<number | null>(null);
  const minHeight = useSharedValue(REST.minHeight);
  const maxHeight = useSharedValue(grows ? 0 : REST.maxHeight);
  const opacity = useSharedValue(grows ? 0 : 1);
  const moving = useRef(false);
  const last = useRef<ReactNode>(children);
  if (shown) last.current = children;
  const shownRef = useRef(shown);
  shownRef.current = shown;

  // The safety net: once over (or past the deadline) a shown block is at REST and opaque; a hidden one unmounts.
  const deadline = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Each move has an id; only the latest one may end the block's motion. A settle queued by an older move
  // (its animation finished on the UI thread just as a hide began) unmounted the hiding block at once:
  // the declined text and its sources "vanished in one frame" on the iPhone (v9, F2-2).
  const runId = useRef(0);
  const settle = useCallback((id?: number) => {
    if (id != null && id !== runId.current) return;
    moving.current = false;
    if (deadline.current) clearTimeout(deadline.current);
    deadline.current = null;
    if (shownRef.current) {
      minHeight.value = REST.minHeight;
      maxHeight.value = REST.maxHeight;
      opacity.value = 1;
    } else {
      // Invisible by now (or past the deadline): the space closes with the DS layout animation, the
      // neighbours slide, and no frame shows a cut line.
      motion.animateNextLayout();
      setMounted(false);
    }
  }, [minHeight, maxHeight, opacity, motion]);
  const arm = useCallback(
    (ms: number, id?: number) => {
      if (deadline.current) clearTimeout(deadline.current);
      deadline.current = setTimeout(() => settle(id), ms);
    },
    [settle]
  );
  useEffect(
    () => () => {
      if (deadline.current) clearTimeout(deadline.current);
    },
    []
  );

  const run = useCallback(
    (move: RevealMove, show: boolean) => {
      const timing = revealTiming(show, reduceMotion);
      const start = boundsAtStart(move);
      const end = boundsAfter(move);
      const bound = move.bound === "maxHeight" ? maxHeight : minHeight;
      const id = ++runId.current;
      if (!moving.current) {
        // A grow retargeted while it runs keeps going from where it is.
        minHeight.value = start.minHeight;
        maxHeight.value = start.maxHeight;
      }
      moving.current = true;
      bound.value = withTiming(move.to, { duration: timing.height.duration, easing: curves[timing.height.curve], reduceMotion: ReduceMotion.Never }, (finished) => {
        if (!finished) return;
        minHeight.value = end.minHeight;
        maxHeight.value = end.maxHeight;
        scheduleOnRN(settle, id);
      });
      opacity.value = withTiming(show ? 1 : 0, { duration: timing.opacity.duration, easing: curves[timing.opacity.curve], reduceMotion: ReduceMotion.Never });
      arm(revealDeadline(timing), id);
    },
    [reduceMotion, minHeight, maxHeight, opacity, arm, settle]
  );

  useEffect(() => {
    if (shown) {
      setMounted(true);
      // Shown again after it left: grow back from nothing to the last measure (the next layout retargets it).
      if (hidden.current && natural.current != null) {
        hidden.current = false;
        maxHeight.value = 0;
        run(revealMove("show", 0, natural.current)!, true);
      } else if (natural.current != null && maxHeight.value !== REST.maxHeight) run(revealMove("show", 0, natural.current)!, true);
      // Growing and not measured yet: the deadline shows it even if no layout ever comes.
      else if (grows && natural.current == null) arm(revealDeadline(revealTiming(true, reduceMotion)));
      return;
    }
    // Leaving (hideSteps): fade only, the height as it is; then settle() closes the space and unmounts.
    const id = ++runId.current;
    hidden.current = true;
    moving.current = true;
    const steps = hideSteps(reduceMotion);
    opacity.value = withTiming(0, { duration: steps.fadeMs, easing: curves[steps.curve], reduceMotion: ReduceMotion.Never }, (finished) => {
      if (finished) scheduleOnRN(settle, id);
    });
    arm(steps.deadlineMs, id);
    // Only on a change of `shown`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shown]);

  const onLayout = useCallback(
    (e: LayoutChangeEvent) => {
      const next = e.nativeEvent.layout.height;
      const step = revealOnLayout(natural.current, next, grows, spaceBefore);
      if (step === "wait") return;
      const previous = natural.current;
      natural.current = next;
      if (!shownRef.current) return;
      if (step === "grow") return run(revealMove("grow", null, next)!, true);
      // A grow still running follows its content (streamed text); at rest nothing clamps it.
      if (step === "resize" && moving.current && next > (previous ?? 0)) run(revealMove("grow", null, next)!, true);
    },
    [grows, run, spaceBefore]
  );

  const style = useAnimatedStyle(() => ({ minHeight: minHeight.value, maxHeight: maxHeight.value, opacity: opacity.value }));

  const content = useMemo(() => (shown ? children : last.current), [shown, children]);
  if (!mounted && !shown) return null;
  return (
    <Animated.View
      style={[{ overflow: "hidden" }, style]}
      pointerEvents={shown ? "auto" : "none"}
      importantForAccessibility={shown ? "auto" : "no-hide-descendants"}
      accessibilityElementsHidden={!shown}
    >
      {/* Its own natural height even inside a shorter frame: that's what the frame opens to. */}
      <View onLayout={onLayout} style={{ paddingTop: spaceBefore }}>
        {content}
      </View>
    </Animated.View>
  );
}
