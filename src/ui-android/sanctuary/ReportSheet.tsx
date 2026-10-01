/**
 * "Report bad info" in the preview: pick why, add a note, send → a confirmation in the sheet.
 * Nothing is sent; the report only counts in this session's stats.
 */
import React, { useState } from "react";
import { Modal, Pressable, StyleSheet, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Text, TextInput } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";
import { REPORT_REASONS, type ReportReason } from "./sanctuary.pure";

interface Props {
  packTitle: string;
  onSubmit: (reason: ReportReason) => void;
  onClose: () => void;
}

export function ReportSheet({ packTitle, onSubmit, onClose }: Props) {
  const { t } = useTranslation();
  const [reason, setReason] = useState<ReportReason | null>(null);
  const [note, setNote] = useState("");
  const [sent, setSent] = useState(false);

  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={styles.sheet} onPress={() => {}}>
          <View style={styles.previewPill}>
            <Text style={styles.previewText}>{t("sanctuary.previewBar")}</Text>
          </View>
          {!sent ? (
            <>
              <Text style={styles.title}>{t("sanctuary.reportSheet.title")}</Text>
              <Text style={styles.subtitle}>{packTitle}</Text>
              {REPORT_REASONS.map((r) => (
                <Pressable
                  key={r}
                  style={[styles.option, reason === r && styles.optionOn]}
                  onPress={() => setReason(r)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: reason === r }}
                >
                  <Text style={[styles.optionText, reason === r && styles.optionTextOn]}>{t(`sanctuary.reportSheet.reasons.${r}`)}</Text>
                </Pressable>
              ))}
              <TextInput
                style={styles.input}
                value={note}
                onChangeText={setNote}
                placeholder={t("sanctuary.reportSheet.notePlaceholder")}
                placeholderTextColor={colors.text.dim}
                multiline
              />
              <Pressable
                style={[styles.send, !reason && styles.disabled]}
                disabled={!reason}
                onPress={() => {
                  if (!reason) return;
                  onSubmit(reason);
                  setSent(true);
                }}
              >
                <Text style={styles.sendText}>{t("sanctuary.reportSheet.send")}</Text>
              </Pressable>
            </>
          ) : (
            <>
              <Text style={styles.title}>{t("sanctuary.reportSheet.thanksTitle")}</Text>
              <Text style={styles.subtitle}>{t("sanctuary.reportSheet.thanksBody")}</Text>
              <Pressable style={styles.send} onPress={onClose}>
                <Text style={styles.sendText}>{t("common.done")}</Text>
              </Pressable>
            </>
          )}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.6)", justifyContent: "flex-end" },
  sheet: { backgroundColor: colors.bg.card, borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: spacing.lg, gap: spacing.sm },
  previewPill: { alignSelf: "flex-start", borderRadius: radii.full, borderWidth: 1, borderColor: colors.amber.border, backgroundColor: colors.amber.bgSubtle, paddingHorizontal: spacing.sm, paddingVertical: 2 },
  previewText: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber },
  title: { ...typography.ui.title, color: colors.text.heading },
  subtitle: { ...typography.ui.body, color: colors.text.muted, marginBottom: spacing.xs },
  option: { minHeight: 48, borderRadius: 14, backgroundColor: colors.bg.cardHover, paddingHorizontal: spacing.base, justifyContent: "center" },
  optionOn: { backgroundColor: colors.crimson.bgSubtle, borderWidth: 1, borderColor: colors.crimson.border },
  optionText: { ...typography.ui.body, color: colors.text.primary },
  optionTextOn: { color: colors.text.heading },
  input: { minHeight: 72, borderRadius: 14, backgroundColor: colors.bg.input, borderWidth: 1, borderColor: colors.border.default, padding: spacing.md, color: colors.text.primary, textAlignVertical: "top", ...typography.ui.body },
  send: { minHeight: 52, borderRadius: radii.full, backgroundColor: colors.emerald[600], alignItems: "center", justifyContent: "center", marginTop: spacing.xs },
  sendText: { ...typography.ui.body, fontWeight: "700", color: colors.text.heading },
  disabled: { opacity: 0.4 },
});
