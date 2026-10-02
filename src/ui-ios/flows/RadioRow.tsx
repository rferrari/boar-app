import React from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { IconSlot, Text, useOpticalLine } from "../components";
import { useTokens } from "../theme";
import { selection } from "../../services/haptics";

interface Props {
  title: string;
  subtitle?: string;
  selected: boolean;
  onPress: () => void;
}

/** One option of a single-choice list. Wrap a group in a View with accessibilityRole="radiogroup". */
export function RadioRow({ title, subtitle, selected, onPress }: Props) {
  const t = useTokens();
  const line = useOpticalLine("body");
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityLabel={subtitle ? `${title}, ${subtitle}` : title}
      accessibilityState={{ checked: selected }}
      onPress={() => {
        selection();
        onPress();
      }}
      style={({ pressed }) => [
        styles.row,
        { minHeight: t.size.row, paddingHorizontal: t.space.md + t.space.xxs, paddingVertical: t.space.md, gap: t.space.md },
        pressed && { backgroundColor: t.color.bg.sunken },
      ]}
    >
      <View style={[styles.body, { gap: t.space.xxs }]}>
        <Text variant="body">{title}</Text>
        {subtitle && (
          <Text variant="footnote" color="secondary">
            {subtitle}
          </Text>
        )}
      </View>
      {/* The check sits on the title's optical line, at the row's end edge (icon-align). */}
      {selected ? (
        <IconSlot name="check" line={line} color={t.color.accent.text} edge="end" />
      ) : (
        <View style={{ width: line.iconSize }} />
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "flex-start" },
  body: { flex: 1 },
});
