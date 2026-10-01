/** A look inside a pack before getting it: a sample of its text, rendered as Markdown like a chat answer. */
import React from "react";
import { Modal, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { Text } from "../components/AppText";
import { MarkdownMessage } from "../components/MarkdownMessage";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";

interface Props {
  title: string;
  markdown: string;
  onClose: () => void;
}

export function PreviewSheet({ title, markdown, onClose }: Props) {
  const { t } = useTranslation();
  return (
    <Modal visible animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.container} edges={["top", "bottom", "left", "right"]}>
        <View style={styles.header}>
          <View style={{ flex: 1 }}>
            <Text style={styles.label}>{t("sanctuary.previewSheet.label")}</Text>
            <Text style={styles.title}>{title}</Text>
          </View>
          <Pressable onPress={onClose} hitSlop={8} style={styles.close}>
            <Text style={styles.closeText}>{t("common.done")}</Text>
          </Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.body}>
          <View style={styles.page}>
            <MarkdownMessage content={markdown} />
          </View>
          <Text style={styles.note}>{t("sanctuary.previewSheet.note")}</Text>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg.terminal },
  header: { flexDirection: "row", alignItems: "center", padding: spacing.base, gap: spacing.sm },
  label: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber },
  title: { ...typography.ui.title, color: colors.text.heading },
  close: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.full, backgroundColor: colors.bg.cardHover },
  closeText: { ...typography.ui.body, fontWeight: "600", color: colors.text.accentEmerald },
  body: { padding: spacing.base, gap: spacing.md },
  page: { backgroundColor: colors.bg.card, borderRadius: 18, padding: spacing.base },
  note: { ...typography.ui.subtext, color: colors.text.dim, textAlign: "center" },
});
