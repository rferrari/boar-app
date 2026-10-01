/** The preview's tip sheet: pick an amount of $BOAR, "send" it. No wallet, no tokens move. */
import React, { useState } from "react";
import { Modal, Pressable, StyleSheet, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Text } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";

const AMOUNTS = [5, 10, 25, 50];

interface Props {
  creator: string;
  onSend: (amount: number) => void;
  onCancel: () => void;
}

export function TipSheet({ creator, onSend, onCancel }: Props) {
  const { t } = useTranslation();
  const [amount, setAmount] = useState(10);
  return (
    <Modal visible transparent animationType="slide" onRequestClose={onCancel}>
      <Pressable style={styles.backdrop} onPress={onCancel}>
        <Pressable style={styles.sheet} onPress={() => {}}>
          <View style={styles.previewPill}>
            <Text style={styles.previewText}>{t("sanctuary.previewBar")}</Text>
          </View>
          <Text style={styles.title}>{t("sanctuary.tipSheet.title", { creator })}</Text>
          <View style={styles.amounts}>
            {AMOUNTS.map((a) => (
              <Pressable key={a} style={[styles.chip, a === amount && styles.chipOn]} onPress={() => setAmount(a)} accessibilityRole="button" accessibilityState={{ selected: a === amount }}>
                <Text style={[styles.chipText, a === amount && styles.chipTextOn]}>{a}</Text>
              </Pressable>
            ))}
          </View>
          <Pressable style={styles.send} onPress={() => onSend(amount)} accessibilityRole="button">
            <Text style={styles.sendText}>{t("sanctuary.tipSheet.send", { amount })}</Text>
          </Pressable>
          <Pressable style={styles.cancel} onPress={onCancel}>
            <Text style={styles.cancelText}>{t("sanctuary.tipSheet.cancel")}</Text>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.6)", justifyContent: "flex-end" },
  sheet: { backgroundColor: colors.bg.card, borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: spacing.lg, gap: spacing.md },
  previewPill: { alignSelf: "flex-start", borderRadius: radii.full, borderWidth: 1, borderColor: colors.amber.border, backgroundColor: colors.amber.bgSubtle, paddingHorizontal: spacing.sm, paddingVertical: 2 },
  previewText: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber },
  title: { ...typography.ui.title, color: colors.text.heading },
  amounts: { flexDirection: "row", gap: spacing.sm },
  chip: { flex: 1, minHeight: 48, borderRadius: radii.full, backgroundColor: colors.bg.cardHover, alignItems: "center", justifyContent: "center" },
  chipOn: { backgroundColor: colors.emerald.bgSubtle, borderWidth: 1, borderColor: colors.emerald[500] },
  chipText: { ...typography.ui.body, fontWeight: "600", color: colors.text.primary },
  chipTextOn: { color: colors.text.accentEmerald },
  send: { minHeight: 52, borderRadius: radii.full, backgroundColor: colors.emerald[600], alignItems: "center", justifyContent: "center" },
  sendText: { ...typography.ui.body, fontWeight: "700", color: colors.text.heading },
  cancel: { minHeight: 44, alignItems: "center", justifyContent: "center" },
  cancelText: { ...typography.ui.body, color: colors.text.muted },
});
