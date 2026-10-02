/**
 * The preview's "Publish a pack" wizard: pick documents (names from My Documents, read only),
 * name and tag it, publish → a draft kept in memory. Nothing is uploaded.
 */
import React, { useEffect, useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { Text, TextInput } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";
import { listCustomCollections } from "../../rag/db";
import { SESSIONS, type SessionId } from "./catalog";
import type { Draft } from "./sanctuary.pure";

interface Props {
  onPublish: (draft: Draft) => void;
  onClose: () => void;
}

export function PublishWizard({ onPublish, onClose }: Props) {
  const { t } = useTranslation();
  const [step, setStep] = useState(0);
  const [documents, setDocuments] = useState<string[] | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [title, setTitle] = useState("");
  const [tags, setTags] = useState("");
  const [session, setSession] = useState<SessionId>("code");

  useEffect(() => {
    listCustomCollections()
      .then((cs) => setDocuments(cs.map((c) => c.name)))
      .catch(() => setDocuments([]));
  }, []);

  const toggle = (name: string) => setChosen((c) => (c.includes(name) ? c.filter((x) => x !== name) : [...c, name]));
  const canNext = step === 1 ? title.trim().length > 0 : true;
  const publish = () =>
    onPublish({ title, session, pricing: "free", documents: chosen, tags: tags.split(",").map((s) => s.trim()).filter(Boolean) });

  const option = (selected: boolean, label: string, onPress: () => void) => (
    <Pressable key={label} style={[styles.option, selected && styles.optionOn]} onPress={onPress} accessibilityRole="button" accessibilityState={{ selected }}>
      <Text style={[styles.optionText, selected && styles.optionTextOn]}>{label}</Text>
    </Pressable>
  );

  return (
    <Modal visible animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.container} edges={["top", "bottom", "left", "right"]}>
        <View style={styles.header}>
          <Text style={styles.title}>{t("sanctuary.publish.title")}</Text>
          <Pressable onPress={onClose} hitSlop={8}>
            <Text style={styles.close}>{t("common.done")}</Text>
          </Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.body}>
          {step === 0 && (
            <>
              <Text style={styles.stepTitle}>{t("sanctuary.publish.step1")}</Text>
              {documents && documents.length === 0 && <Text style={styles.muted}>{t("sanctuary.publish.noDocs")}</Text>}
              {(documents ?? []).map((name) => option(chosen.includes(name), name, () => toggle(name)))}
            </>
          )}
          {step === 1 && (
            <>
              <Text style={styles.stepTitle}>{t("sanctuary.publish.step2")}</Text>
              <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder={t("sanctuary.publish.titlePlaceholder")} placeholderTextColor={colors.text.dim} />
              <TextInput style={styles.input} value={tags} onChangeText={setTags} placeholder={t("sanctuary.publish.tagsPlaceholder")} placeholderTextColor={colors.text.dim} />
              <Text style={styles.label}>{t("sanctuary.publish.category")}</Text>
              {SESSIONS.map((s) => option(session === s, t(`sanctuary.sessions.${s}.name`), () => setSession(s)))}
            </>
          )}
        </ScrollView>
        <View style={styles.footer}>
          {step > 0 && (
            <Pressable style={[styles.btn, styles.btnSecondary]} onPress={() => setStep((s) => s - 1)}>
              <Text style={styles.btnSecondaryText}>{t("sanctuary.publish.back")}</Text>
            </Pressable>
          )}
          <Pressable
            style={[styles.btn, styles.btnPrimary, !canNext && styles.disabled]}
            disabled={!canNext}
            onPress={() => (step < 1 ? setStep((s) => s + 1) : publish())}
          >
            <Text style={styles.btnPrimaryText}>{step < 1 ? t("sanctuary.publish.next") : t("sanctuary.publish.publish")}</Text>
          </Pressable>
        </View>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg.terminal },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", padding: spacing.base },
  title: { ...typography.ui.title, color: colors.text.heading, flex: 1 },
  close: { ...typography.ui.body, fontWeight: "600", color: colors.text.accentEmerald },
  body: { padding: spacing.base, gap: spacing.sm },
  stepTitle: { ...typography.ui.titleSm, color: colors.text.heading, marginBottom: spacing.xs },
  label: { ...typography.ui.caption, color: colors.text.secondary, marginTop: spacing.sm },
  muted: { ...typography.ui.body, color: colors.text.muted },
  option: { minHeight: 48, borderRadius: 14, backgroundColor: colors.bg.card, paddingHorizontal: spacing.base, justifyContent: "center" },
  optionOn: { backgroundColor: colors.emerald.bgSubtle, borderWidth: 1, borderColor: colors.emerald[500] },
  optionText: { ...typography.ui.body, color: colors.text.primary },
  optionTextOn: { color: colors.text.heading },
  input: { minHeight: 48, borderRadius: radii.full, backgroundColor: colors.bg.input, borderWidth: 1, borderColor: colors.border.default, paddingHorizontal: 18, color: colors.text.primary, ...typography.ui.body },
  footer: { flexDirection: "row", gap: spacing.sm, padding: spacing.base },
  btn: { flex: 1, minHeight: 52, borderRadius: radii.full, alignItems: "center", justifyContent: "center" },
  btnPrimary: { backgroundColor: colors.emerald[600] },
  btnPrimaryText: { ...typography.ui.body, fontWeight: "700", color: colors.text.heading },
  btnSecondary: { backgroundColor: colors.bg.cardHover },
  btnSecondaryText: { ...typography.ui.body, fontWeight: "600", color: colors.text.primary },
  disabled: { opacity: 0.4 },
});
