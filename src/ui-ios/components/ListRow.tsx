import React from "react";
import { Pressable, StyleSheet, Switch as RNSwitch, useWindowDimensions, View } from "react-native";
import { impact, ImpactFeedbackStyle, selection } from "../../services/haptics";
import { icon as iconTokens, space, useTokens } from "../theme";
import { Icon, IconName } from "./Icon";
import { IconSlot, useOpticalLine } from "./IconText";
import { switchColors } from "./Switch";
import { Text } from "./Text";

/** From this OS text scale, rows stack their value under the title (and the drawer scrolls its destinations). */
export const LARGE_TEXT_SCALE = 1.2;

export interface ListRowProps {
  title: string;
  subtitle?: string;
  /** Current value, shown trailing (e.g. "Qwen2.5 1.5B"). */
  value?: string;
  icon?: IconName;
  /**
   * Makes the whole row a switch: one focus stop with role "switch" and
   * `checked`, toggled by tapping anywhere. Prefer this over a Switch in
   * `trailing`, which screen readers can't reach inside a non-pressable row.
   */
  switch?: { value: boolean; onValueChange: (value: boolean) => void };
  /** Custom non-interactive trailing element (Badge, text). Replaces value + chevron. */
  trailing?: React.ReactNode;
  onPress?: () => void;
  /** Shows a chevron; defaults to true when the row navigates (`onPress` without `trailing`). */
  chevron?: boolean;
  /**
   * Disclosure row that folds content below it (Details, larger models): announces expanded/collapsed
   * and swaps the chevron for one pointing down (collapsed) or up (expanded).
   */
  expanded?: boolean;
  destructive?: boolean;
  disabled?: boolean;
  /** Overrides the composed "title, value, subtitle" label. */
  accessibilityLabel?: string;
  accessibilityHint?: string;
}

/**
 * One focusable unit: title + value + subtitle are read as one phrase.
 * Title and value stack vertically when they don't fit (long PT strings, 200% text).
 */
export function ListRow({
  title,
  subtitle,
  value,
  icon,
  trailing,
  onPress,
  chevron,
  expanded,
  destructive,
  disabled,
  accessibilityLabel,
  accessibilityHint,
  switch: toggle,
}: ListRowProps) {
  const t = useTokens();
  // At large text, the value goes under the title (left-aligned) instead of beside it: beside it, a long
  // single word in the title was broken mid-word ('Conversati/on history', Harbor 51f9863 at 1.3).
  const { fontScale } = useWindowDimensions();
  const stacked = fontScale >= LARGE_TEXT_SCALE;
  // Leading icons form one column (title size, grown with the text) on the title's first line.
  const line = useOpticalLine("body", "title");
  if (toggle) {
    trailing = (
      <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
        <RNSwitch
          value={toggle.value}
          disabled={disabled}
          {...switchColors(t, toggle.value)}
        />
      </View>
    );
  }
  const disclosure = expanded !== undefined;
  const showChevron = disclosure || (chevron ?? (!!onPress && !trailing));
  const chevronName = disclosure ? (expanded ? "chevron-up" : "chevron-down") : "chevron-right";
  const label = accessibilityLabel ?? [title, value, subtitle].filter(Boolean).join(", ");
  const content = (
    <>
      <View style={[styles.lead, { gap: iconTokens.gap }]}>
        {icon && <IconSlot name={icon} line={line} color={destructive ? t.color.status.danger.solid : t.color.text.secondary} />}
        <View style={styles.body}>
          <View style={stacked ? styles.titleStack : styles.titleLine}>
            {/* flex: 1 only beside the value: in the stacked column it collapses the title to 0 height (Prism AX-4). */}
            <Text variant="body" color={destructive ? "danger" : "primary"} style={stacked ? undefined : styles.title}>
              {title}
            </Text>
            {value && !trailing && (
              <Text variant="callout" color="secondary" style={stacked ? undefined : styles.value} numberOfLines={stacked ? undefined : 2}>
                {value}
              </Text>
            )}
          </View>
          {subtitle && (
            <Text variant="footnote" color="secondary">
              {subtitle}
            </Text>
          )}
        </View>
      </View>
      {trailing}
      {showChevron && <Icon name={chevronName} size={iconTokens.sizeBody} color={t.color.text.tertiary} edge="end" />}
    </>
  );
  const rowStyle = {
    minHeight: t.size.row,
    // Mockup lists (setup 3 rows inside a 14-pt card): 14 horizontal.
    paddingHorizontal: t.space.md + t.space.xxs,
    paddingVertical: t.space.md,
    gap: t.space.md,
  };
  if (toggle) {
    return (
      <Pressable
        accessibilityRole="switch"
        accessibilityLabel={accessibilityLabel ?? [title, subtitle].filter(Boolean).join(", ")}
        accessibilityHint={accessibilityHint}
        accessibilityState={{ checked: toggle.value, disabled: !!disabled }}
        disabled={disabled}
        onPress={() => {
          selection();
          toggle.onValueChange(!toggle.value);
        }}
        style={({ pressed }) => [styles.row, rowStyle, pressed && { backgroundColor: t.color.bg.sunken }, disabled && { opacity: t.opacity.disabled }]}
      >
        {content}
      </Pressable>
    );
  }
  if (!onPress) {
    return (
      <View accessible accessibilityLabel={label} style={[styles.row, rowStyle]}>
        {content}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!disabled, ...(disclosure ? { expanded } : null) }}
      disabled={disabled}
      onPress={() => {
        impact(ImpactFeedbackStyle.Light);
        onPress();
      }}
      style={({ pressed }) => [
        styles.row,
        rowStyle,
        pressed && { backgroundColor: t.color.bg.sunken },
        disabled && { opacity: t.opacity.disabled },
      ]}
    >
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center" },
  // Icon column + text: the icon stays on line 1 while the row centres the whole block.
  lead: { flex: 1, flexDirection: "row", alignItems: "flex-start" },
  body: { flex: 1, gap: space.xxs },
  // Title and value share one line (value right, at most half the width, wrapping in place) instead of
  // the value dropping to a second line on its own (Prism LT-2, 'Keep the last 10').
  titleLine: { flexDirection: "row", alignItems: "baseline", columnGap: space.sm },
  titleStack: { gap: space.xxs },
  title: { flex: 1, flexShrink: 1 },
  value: { flexShrink: 1, maxWidth: "50%", textAlign: "right" },
});
