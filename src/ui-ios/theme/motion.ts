import { useMemo } from "react";
import { Easing as RNEasing, type EasingFunction as RNEasingFunction, LayoutAnimation } from "react-native";
import { cubicBezier, Easing, Keyframe, LinearTransition, ReduceMotion } from "react-native-reanimated";
import { useTheme } from "./ThemeContext";
import { CURVE, crossfadeSpec, type Curve, enterScale, motionSpec, type MotionRole, type MotionSpec } from "./motionSpec";

/**
 * The only place that turns motion roles into animations (DS §6). Screens and components ask for a
 * role, never a duration:
 *
 *   const m = useMotion();
 *   <Animated.View entering={m.entering({ from: "below" })} exiting={m.exiting()} layout={m.layout} />
 *   <Animated.View style={{ borderColor, ...m.colorTransition(["borderColor", "backgroundColor"]) }} />  // one object, not an array
 *   Animated.timing(v, { toValue: 1, ...m.timing("enter"), useNativeDriver: true })   // RN Animated
 *
 * Reduce motion is handled here (motionSpec), so every builder opts out of Reanimated's own skip
 * (ReduceMotion.Never): a reduced enter is a 90 ms fade, not a hard cut.
 */

/** Where an entering block comes from, or a leaving one goes. "end"/"start" follow the reading direction (LTR). */
export type Travel = "none" | "below" | "above" | "end" | "start";

const rnCurves: Record<Curve, RNEasingFunction> = {
  standard: RNEasing.bezier(...CURVE.standard),
  enter: RNEasing.bezier(...CURVE.enter),
  exit: RNEasing.bezier(...CURVE.exit),
};
const reCurves = {
  standard: Easing.bezier(...CURVE.standard),
  enter: Easing.bezier(...CURVE.enter),
  exit: Easing.bezier(...CURVE.exit),
};

function offset(travel: Travel, distance: number): { translateX: number; translateY: number } {
  switch (travel) {
    case "below":
      return { translateX: 0, translateY: distance };
    case "above":
      return { translateX: 0, translateY: -distance };
    case "end":
      return { translateX: distance, translateY: 0 };
    case "start":
      return { translateX: -distance, translateY: 0 };
    default:
      return { translateX: 0, translateY: 0 };
  }
}

function enterKeyframe(spec: MotionSpec, from: Travel, delay: number, scale = 1) {
  const o = offset(from, spec.travel);
  return new Keyframe({
    0: { opacity: 0, transform: [{ translateX: o.translateX }, { translateY: o.translateY }, { scale }] },
    100: { opacity: 1, transform: [{ translateX: 0 }, { translateY: 0 }, { scale: 1 }], easing: reCurves[spec.curve] },
  })
    .duration(spec.duration)
    .delay(delay)
    .reduceMotion(ReduceMotion.Never);
}

function exitKeyframe(spec: MotionSpec, to: Travel) {
  const o = offset(to, spec.travel);
  return new Keyframe({
    0: { opacity: 1, transform: [{ translateX: 0 }, { translateY: 0 }] },
    100: { opacity: 0, transform: [{ translateX: o.translateX }, { translateY: o.translateY }], easing: reCurves[spec.curve] },
  })
    .duration(spec.duration)
    .reduceMotion(ReduceMotion.Never);
}

export function buildMotion(reduceMotion: boolean) {
  const layoutSpec = motionSpec("layout", reduceMotion);
  const change = motionSpec("change", reduceMotion);
  const [x1, y1, x2, y2] = CURVE[change.curve];
  return {
    reduceMotion,
    spec: (role: MotionRole, opts?: { swap?: boolean }) => motionSpec(role, reduceMotion, opts),
    /** Duration + easing for RN `Animated.timing` (native driver: opacity/transform). */
    timing(role: MotionRole, opts?: { swap?: boolean }): { duration: number; easing: RNEasingFunction } {
      const s = motionSpec(role, reduceMotion, opts);
      return { duration: s.duration, easing: rnCurves[s.curve] };
    },
    /**
     * Reanimated `entering`: fade in over `enter`, travelling from `from` (dropped under reduce motion).
     * `pop`: also grows from POP_SCALE (a new item joining a live list); a fade only under reduce motion.
     */
    entering({ from = "none", delay = 0, pop = false }: { from?: Travel; delay?: number; pop?: boolean } = {}) {
      return enterKeyframe(motionSpec("enter", reduceMotion), from, delay, enterScale(pop, reduceMotion));
    },
    /** Reanimated `exiting`: fade out over `exit` (`swap`: the 90 ms half of a crossfade). */
    exiting({ to = "none", swap = false }: { to?: Travel; swap?: boolean } = {}) {
      return exitKeyframe(motionSpec("exit", reduceMotion, { swap }), to);
    },
    /**
     * A content swap on a keyed block: the old one leaves in 90 ms, the new one enters after it.
     *   <Animated.View key={step} entering={x.entering} exiting={x.exiting}>
     * `forward`: travel in the reading direction (next step) or against it (back).
     */
    crossfade(direction: "forward" | "back" | "none" = "none") {
      const x = crossfadeSpec(reduceMotion);
      const from: Travel = direction === "forward" ? "end" : direction === "back" ? "start" : "none";
      // The old block fades out in place: Reanimated runs the `exiting` it had at its last render,
      // which was built for the PREVIOUS change, so a direction there could point the wrong way.
      return { entering: enterKeyframe(x.in, from, x.in.delay), exiting: exitKeyframe(x.out, "none") };
    },
    /** Reanimated `layout`: size/position changes of siblings and containers. */
    layout: LinearTransition.duration(layoutSpec.duration).easing(reCurves[layoutSpec.curve]).reduceMotion(ReduceMotion.Never),
    /**
     * Call right before a state change that folds or unfolds content (Details, "Show more N", a model's
     * files): the whole next layout animates with `layout`, so the rows below slide instead of jumping
     * and the new content fades in (TR-6). RN LayoutAnimation, because it moves every sibling in the
     * commit, which per-view Reanimated `layout` cannot do without tagging each one. Its curve is the
     * closest built-in (easeInEaseOut); under reduce motion it does nothing (layout is instant).
     */
    animateNextLayout() {
      if (layoutSpec.duration === 0) return;
      const fade = { type: LayoutAnimation.Types.easeInEaseOut, property: LayoutAnimation.Properties.opacity };
      LayoutAnimation.configureNext({
        duration: layoutSpec.duration,
        create: fade,
        update: { type: LayoutAnimation.Types.easeInEaseOut },
        delete: fade,
      });
    },
    /** Reanimated 4 CSS transition for a box that moves or resizes (`layout`), e.g. a segment's sliding pill. */
    layoutProps(properties: ("left" | "top" | "width" | "height")[]) {
      const [lx1, ly1, lx2, ly2] = CURVE[layoutSpec.curve];
      return {
        transitionProperty: properties,
        transitionDuration: layoutSpec.duration,
        transitionTimingFunction: cubicBezier(lx1, ly1, lx2, ly2),
      };
    },
    /** Reanimated 4 CSS transition for in-place state (`change`): colours of a selection, a fill, a border. */
    colorTransition(properties: ("borderColor" | "backgroundColor" | "color" | "opacity")[]) {
      return {
        transitionProperty: properties,
        transitionDuration: change.duration,
        transitionTimingFunction: cubicBezier(x1, y1, x2, y2),
      };
    },
  };
}

export type Motion = ReturnType<typeof buildMotion>;

export function useMotion(): Motion {
  const { reduceMotion } = useTheme();
  return useMemo(() => buildMotion(reduceMotion), [reduceMotion]);
}
