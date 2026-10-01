/**
 * The Sanctuary's welcome teaser: a random entrance scene, what the preview is, and a button that
 * stays locked ("You shall not pass!") until the preview disclaimer is ticked, then shows one of
 * the film and book nods in quotes.ts.
 */
import React, { useMemo, useState } from "react";
import { Image, Modal, Pressable, ScrollView, StyleSheet, Switch, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { Text } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";
import { ENTRANCES } from "./art";
import { LOCKED_LABEL, pickLabel } from "./quotes";
import { MATRIX_GREEN } from "./style";

interface Props {
  entrance: number;
  onEnter: (dontShowAgain: boolean) => void;
  onClose: () => void;
}

export function WelcomeTeaser({ entrance, onEnter, onClose }: Props) {
  const { t } = useTranslation();
  const [agreed, setAgreed] = useState(false);
  const [dontShow, setDontShow] = useState(false);
  // A new nod each time the Sanctuary opens (this component mounts with it).
  const label = useMemo(() => pickLabel(), []);

  return (
    <Modal visible animationType="fade" onRequestClose={onClose}>
      <SafeAreaView style={styles.container} edges={["top", "bottom", "left", "right"]}>
        <ScrollView contentContainerStyle={styles.body}>
          <Image source={ENTRANCES[entrance]} style={styles.art} resizeMode="cover" />
          <View style={styles.previewPill}>
            <Text style={styles.previewText}>{t("sanctuary.previewBar")}</Text>
          </View>
          <Text style={styles.title}>{t("sanctuary.welcome.title")}</Text>
          <Text style={styles.paragraph}>{t("sanctuary.welcome.body")}</Text>

          <Pressable style={styles.row} onPress={() => setAgreed((v) => !v)} accessibilityRole="checkbox" accessibilityState={{ checked: agreed }}>
            <View style={[styles.box, agreed && styles.boxOn]}>{agreed && <View style={styles.boxDot} />}</View>
            <Text style={styles.rowText}>{t("sanctuary.welcome.agree")}</Text>
          </Pressable>

          <Pressable
            style={[styles.enter, agreed ? styles.enterOn : styles.enterOff]}
            disabled={!agreed}
            onPress={() => onEnter(dontShow)}
            accessibilityRole="button"
            accessibilityState={{ disabled: !agreed }}
          >
            <Text style={[styles.enterText, agreed && styles.enterTextOn]}>{agreed ? label : LOCKED_LABEL}</Text>
          </Pressable>

          <View style={styles.dontShow}>
            <Text style={styles.dontShowText}>{t("sanctuary.welcome.dontShow")}</Text>
            <Switch value={dontShow} onValueChange={setDontShow} trackColor={{ false: colors.border.default, true: colors.emerald[500] }} />
          </View>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg.terminal },
  body: { padding: spacing.base, gap: spacing.md, paddingBottom: spacing.xxl },
  art: { width: "100%", aspectRatio: 1, borderRadius: 20 },
  previewPill: {
    alignSelf: "flex-start",
    borderRadius: radii.full,
    borderWidth: 1,
    borderColor: colors.amber.border,
    backgroundColor: colors.amber.bgSubtle,
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
  },
  previewText: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber },
  title: { ...typography.ui.headline, color: colors.text.heading },
  paragraph: { ...typography.ui.body, color: colors.text.secondary },
  row: { flexDirection: "row", alignItems: "center", gap: spacing.sm, paddingVertical: spacing.xs },
  box: { width: 22, height: 22, borderRadius: 6, borderWidth: 2, borderColor: colors.border.elevated, alignItems: "center", justifyContent: "center" },
  boxOn: { borderColor: MATRIX_GREEN },
  boxDot: { width: 10, height: 10, borderRadius: 3, backgroundColor: MATRIX_GREEN },
  rowText: { ...typography.ui.body, color: colors.text.primary, flex: 1 },
  enter: { minHeight: 52, borderRadius: radii.full, alignItems: "center", justifyContent: "center", paddingHorizontal: spacing.base, borderWidth: 1 },
  enterOff: { backgroundColor: colors.bg.card, borderColor: colors.border.default, opacity: 0.6 },
  enterOn: {
    backgroundColor: "rgba(34, 197, 94, 0.14)",
    borderColor: MATRIX_GREEN,
    shadowColor: MATRIX_GREEN,
    shadowOpacity: 0.6,
    shadowRadius: 12,
    elevation: 6,
  },
  enterText: { ...typography.ui.title, color: colors.text.muted, textAlign: "center" },
  enterTextOn: { color: colors.text.heading },
  dontShow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: spacing.sm },
  dontShowText: { ...typography.ui.subtext, color: colors.text.muted },
});
