import React from "react";
import { Pressable, View, ViewStyle } from "react-native";
import Animated from "react-native-reanimated";
import { selection } from "../../services/haptics";
import { useTokens } from "../theme";
import { useMotion } from "../theme/motion";
import { IconSlot, LineSlot, useOpticalLine } from "./IconText";
import { MetaLine } from "./MetaLine";
import { Text } from "./Text";

export interface OptionCardProps {
  title: string;
  selected: boolean;
  onPress: () => void;
  /** Sits on the title's line, right after it (e.g. a RECOMMENDED `<Badge>`). */
  badge?: React.ReactNode;
  /** Right-aligned on the title's line: the figure that decides (size, speed). A string renders as a tabular headline. */
  trailing?: React.ReactNode;
  description?: string;
  /** One line of secondary facts (see `MetaLine`). */
  meta?: (string | false | null | undefined)[];
  /** Before the title, e.g. a language monogram. */
  leading?: React.ReactNode;
  /** `radio` (default) on the left for lists; `check` on the right for compact side-by-side cards; `none`. */
  indicator?: "radio" | "check" | "none";
  disabled?: boolean;
  accessibilityHint?: string;
  children?: React.ReactNode;
}

/**
 * A choice among siblings (install tier, model, language). Selection is carried
 * by three cues at once: accent border, raised surface, filled indicator;
 * never the border alone. The cues change in place (`change`, 150 ms): nothing
 * mounts, unmounts or resizes on selection, so neither card moves (Iris TR-1/TR-2).
 */
export function OptionCard({
  title,
  selected,
  onPress,
  badge,
  trailing,
  description,
  meta,
  leading,
  indicator = "radio",
  disabled,
  accessibilityHint,
  children,
}: OptionCardProps) {
  const t = useTokens();
  const m = useMotion();
  const restBorder = t.scheme === "light" ? t.color.line.hairline : "transparent";
  // Mockup: a title-only card (language) centres its row; with a description the radio sits at the top.
  const titleOnly = !description && !meta && !children;
  // Radio and check sit on the title's first line (iOS draws Baloo ~3.6 pt above its box middle).
  const line = useOpticalLine("cardTitle");
  const frame = (pressed: boolean): ViewStyle => ({
    flexDirection: "row",
    alignItems: titleOnly ? "center" : "flex-start",
    // Mockup: 10/12 padding, radius 18, 2 pt border, 10 pt from the radio to the text.
    gap: t.space.sm + t.space.xxs,
    minHeight: t.size.touch,
    paddingVertical: t.space.sm + t.space.xxs,
    paddingHorizontal: t.space.md,
    borderRadius: t.radius.card,
    borderWidth: t.size.focusRing,
    borderColor: selected ? t.color.accent.solid : restBorder,
    // Selected rises to `raised` (mockup): an accent.soft wash would swallow soft accent badges.
    // Pressed sinks like a ListRow/Card, so a touch never looks like a selection before the border comes.
    backgroundColor: selected ? t.color.bg.raised : pressed ? t.color.bg.sunken : t.color.bg.surface,
    opacity: disabled ? t.opacity.disabled : 1,
  });

  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ selected, checked: selected, disabled }}
      accessibilityHint={accessibilityHint}
      disabled={disabled}
      onPress={() => {
        if (!selected) selection();
        onPress();
      }}
    >
      {({ pressed }) => (
        <Animated.View style={{ ...frame(pressed), ...m.colorTransition(["borderColor", "backgroundColor"]) }}>
          {indicator === "radio" && (
            <LineSlot line={line}>
              <Radio on={selected} />
            </LineSlot>
          )}
          {/* A title-only card centres its row on the box; the title's glyphs ride on its optical line
              (iOS ~3.6 pt high), so a leading disc moves with them (Prism FL-21). */}
          {leading && titleOnly ? <View style={{ transform: [{ translateY: line.offset }] }}>{leading}</View> : leading}
          <View style={{ flex: 1, gap: t.space.xxs }}>
            {/* Title and badge wrap together; the deciding figure keeps its column on the right. */}
            <View style={{ flexDirection: "row", alignItems: "flex-start", gap: t.space.sm }}>
              {/* The badge sits on the title's optical line, not its box middle (Prism FD-2, icon-align §7). */}
              <View style={{ flex: 1, flexDirection: "row", alignItems: "flex-start", flexWrap: "wrap", columnGap: t.space.sm, rowGap: t.space.xs }}>
                {/* No font refit (adjustsFontSizeToFit): the title keeps one size whatever the state (Prism S1-2:
                    "Português" shrank 16 → 14.3 pt when its check came in). A long one wraps. */}
                <Text variant="cardTitle" style={{ flexShrink: 1 }}>
                  {title}
                </Text>
                {badge ? <LineSlot line={line}>{badge}</LineSlot> : null}
              </View>
              {trailing !== undefined && (
                <View style={{ flexShrink: 0 }}>
                  {typeof trailing === "string" ? (
                    // Mockup: the size sits small and quiet on the right (11 px mu → 12 pt floor).
                    <Text variant="caption" color="secondary" numeric>
                      {trailing}
                    </Text>
                  ) : (
                    trailing
                  )}
                </View>
              )}
            </View>
            {description ? (
              <Text variant="caption" color="secondary">
                {description}
              </Text>
            ) : null}
            {meta ? <MetaLine items={meta} /> : null}
            {children}
          </View>
          {/* The check's slot is always there and only fades: a check that mounted on selection took
              ~30 pt from the title, which re-wrapped or shrank (Iris TR-1). */}
          {indicator === "check" && (
            <Animated.View style={{ opacity: selected ? 1 : 0, ...m.colorTransition(["opacity"]) }}>
              <IconSlot name="check" line={line} color={t.color.accent.text} edge="end" />
            </Animated.View>
          )}
        </Animated.View>
      )}
    </Pressable>
  );
}

function Radio({ on }: { on: boolean }) {
  const t = useTokens();
  const m = useMotion();
  // Mockup: 18 pt ring, 2 pt border, 8 pt dot.
  const d = t.size.iconSm + t.space.xxs;
  return (
    <Animated.View
      style={{
        width: d,
        height: d,
        borderRadius: d / 2,
        borderWidth: t.size.focusRing,
        // Unselected ring keeps the 3:1 control border (the mockup's bd would fail WCAG 1.4.11).
        borderColor: on ? t.color.accent.solid : t.color.line.strong,
        alignItems: "center",
        justifyContent: "center",
        ...m.colorTransition(["borderColor"]),
      }}
    >
      {/* The dot is always there and fades with the ring (`change`), instead of mounting. */}
      <Animated.View
        style={{
          width: t.space.sm,
          height: t.space.sm,
          borderRadius: t.radius.full,
          backgroundColor: t.color.accent.solid,
          opacity: on ? 1 : 0,
          ...m.colorTransition(["opacity"]),
        }}
      />
    </Animated.View>
  );
}
