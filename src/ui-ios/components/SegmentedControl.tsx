import React, { useState } from "react";
import { LayoutRectangle, Pressable, useWindowDimensions, View } from "react-native";
import Animated from "react-native-reanimated";
import { selection } from "../../services/haptics";
import { icon as iconTokens, useTokens } from "../theme";
import { useMotion } from "../theme/motion";
import type { IconName } from "./Icon";
import { IconText } from "./IconText";
import { segmentsFit } from "./segmentFit";

export interface SegmentOption<T extends string> {
  value: T;
  label: string;
  icon?: IconName;
}

export interface SegmentedControlProps<T extends string> {
  options: readonly SegmentOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** Name of the group for screen readers, e.g. "Appearance". */
  label: string;
  /** `compact`: a slimmer track for forms (segments keep a full touch target via hitSlop). */
  size?: "regular" | "compact";
}

/** Compact segments are this much shorter than the touch minimum; hitSlop gives it back. */
const COMPACT_INSET = 8;

/**
 * 2-4 mutually exclusive options. Exposed as a radio group. Falls back to a
 * vertical list whenever the longest label would not fit its column, so labels never break.
 *
 * The selection is one pill (raised fill, hairline border, shadow) that slides to the chosen segment
 * (`layout`); labels change colour and weight in place. No check comes in with the selection: it
 * made the label wider and moved it (Iris TR-16).
 */
export function SegmentedControl<T extends string>({ options, value, onChange, label, size = "regular" }: SegmentedControlProps<T>) {
  const t = useTokens();
  const { fontScale } = useWindowDimensions();
  const [width, setWidth] = useState(0);
  const m = useMotion();
  // Each segment's box in the track, for the sliding pill. Until all are measured, the selected
  // segment draws its own fill (first frame).
  const [boxes, setBoxes] = useState<Record<string, LayoutRectangle>>({});
  const compact = size === "compact";
  const inset = compact ? 2 : 3;
  // Stack when a label would not fit its column (Prism SEG-2: Android's 1.3 is under a fixed 1.35
  // threshold and 'Standard' broke inside its pill). Label size = subhead at the OS font scale.
  const vertical = !segmentsFit({
    width: width - inset * 2,
    count: options.length,
    longestLabel: Math.max(...options.map((o) => o.label.length)),
    fontSize: (t.type.subhead.fontSize ?? 14) * fontScale,
    chrome: t.space.sm * 2 + t.size.iconSm + iconTokens.gapTight,
    gap: inset,
  });
  const slop = compact ? COMPACT_INSET / 2 : 0;
  const pillStyle = {
    borderRadius: vertical ? t.radius.md : t.radius.full,
    backgroundColor: t.color.bg.raised,
    borderWidth: t.size.border,
    borderColor: t.color.line.strong,
    ...(t.elevation[1] as object),
  };
  const measured = options.every((o) => boxes[o.value]);
  const pill = measured ? boxes[value] : undefined;
  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={label}
      onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
      style={{
        flexDirection: vertical ? "column" : "row",
        padding: inset,
        gap: inset,
        // Stacked at large text: a pill radius on a tall box bent the options (Prism SEG-1).
        borderRadius: vertical ? t.radius.card : t.radius.full,
        backgroundColor: t.color.bg.sunken,
      }}
    >
      {pill && (
        <Animated.View
          pointerEvents="none"
          style={{
            position: "absolute",
            left: pill.x,
            top: pill.y,
            width: pill.width,
            height: pill.height,
            ...pillStyle,
            ...m.layoutProps(["left", "top", "width", "height"]),
          }}
        />
      )}
      {options.map((opt) => {
        const selected = opt.value === value;
        return (
          <Pressable
            key={opt.value}
            accessibilityRole="radio"
            accessibilityLabel={opt.label}
            accessibilityState={{ checked: selected, selected }}
            hitSlop={{ top: slop, bottom: slop }}
            onLayout={(e) => {
              const box = e.nativeEvent.layout;
              setBoxes((b) => (sameBox(b[opt.value], box) ? b : { ...b, [opt.value]: box }));
            }}
            onPress={() => {
              if (selected) return;
              selection();
              onChange(opt.value);
            }}
            style={({ pressed }) => [
              {
                flex: vertical ? undefined : 1,
                minHeight: compact ? t.size.touch - COMPACT_INSET : t.size.touch,
                flexDirection: "row",
                alignItems: "center",
                justifyContent: vertical ? "flex-start" : "center",
                paddingHorizontal: t.space.sm,
                // Selection is marked by the pill (fill + border + shadow) and the label's weight, not by fill alone.
                // The border is always there (transparent when not selected), so selecting moves nothing by 1 pt.
                ...(selected && !pill ? pillStyle : { borderRadius: pillStyle.borderRadius, borderWidth: t.size.border, borderColor: "transparent" }),
              },
              pressed && !selected && { opacity: t.opacity.pressed },
            ]}
          >
            {/* IconText: the chip gap (6) and the glyph on the label's optical line (Prism FD-3). */}
            <IconText
              icon={opt.icon}
              variant="subhead"
              gap="tight"
              color={selected ? "primary" : "secondary"}
              iconColor={selected ? t.color.text.primary : t.color.text.secondary}
              weight={selected ? "semibold" : "medium"}
            >
              {opt.label}
            </IconText>
          </Pressable>
        );
      })}
    </View>
  );
}

function sameBox(a: LayoutRectangle | undefined, b: LayoutRectangle): boolean {
  return !!a && a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height;
}
