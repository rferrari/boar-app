/**
 * "Get" in the preview: a matrix-rain overlay steps through fetching, checking and ready offline,
 * then the session's boar appears ("BOAR knows music now!"). Nothing is downloaded. Only
 * opacity/translate animations on the native driver, so it stays light on Android.
 */
import React, { useEffect, useMemo, useRef, useState } from "react";
import { Animated, Easing, Image, Modal, Pressable, StyleSheet, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Text } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";
import type { MockPack } from "./catalog";
import { SESSION_BOARS } from "./art";
import { MATRIX_GREEN, MATRIX_GREEN_SOFT } from "./style";

const STEP_MS = 1200;
const COLUMNS = 12;
const GLYPHS = "01アイウエオカキクケコサシスセソ";

/** One falling column of characters, looping. */
function RainColumn({ index }: { index: number }) {
  const fall = useRef(new Animated.Value(0)).current;
  const text = useMemo(
    () => Array.from({ length: 14 }, (_, i) => GLYPHS[(index * 7 + i * 3) % GLYPHS.length]).join("\n"),
    [index]
  );
  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(fall, { toValue: 1, duration: 1800 + (index % 5) * 350, easing: Easing.linear, useNativeDriver: true })
    );
    loop.start();
    return () => loop.stop();
  }, [fall, index]);
  const translateY = fall.interpolate({ inputRange: [0, 1], outputRange: [-260, 260] });
  return (
    <Animated.Text style={[styles.rain, { left: `${(index / COLUMNS) * 100}%`, transform: [{ translateY }], opacity: 0.35 + (index % 3) * 0.2 }]}>
      {text}
    </Animated.Text>
  );
}

interface Props {
  pack: MockPack;
  sessionName: string;
  onDone: () => void;
}

export function AcquireModal({ pack, sessionName, onDone }: Props) {
  const { t } = useTranslation();
  const steps = t("sanctuary.acquire.steps", { returnObjects: true }) as string[];
  const [step, setStep] = useState(0);
  const finished = step >= steps.length;
  const reveal = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (finished) {
      Animated.spring(reveal, { toValue: 1, useNativeDriver: true, friction: 6 }).start();
      return;
    }
    const timer = setTimeout(() => setStep((s) => s + 1), STEP_MS);
    return () => clearTimeout(timer);
  }, [step, finished, reveal]);

  return (
    <Modal visible transparent animationType="fade" onRequestClose={finished ? onDone : () => {}}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <View style={styles.rainBox} pointerEvents="none">
            {Array.from({ length: COLUMNS }, (_, i) => (
              <RainColumn key={i} index={i} />
            ))}
          </View>

          {!finished ? (
            <View style={styles.steps}>
              <Text style={styles.mode}>{t("sanctuary.acquire.online")}</Text>
              {steps.map((label, i) => (
                <View key={label} style={styles.stepRow}>
                  <View style={[styles.dot, i < step && styles.dotDone, i === step && styles.dotActive]} />
                  <Text style={[styles.stepText, i <= step && styles.stepTextOn]}>{label}</Text>
                </View>
              ))}
              <Text style={styles.note}>{t("sanctuary.acquire.preview")}</Text>
            </View>
          ) : (
            <Animated.View style={[styles.done, { opacity: reveal, transform: [{ scale: reveal.interpolate({ inputRange: [0, 1], outputRange: [0.85, 1] }) }] }]}>
              <Text style={styles.mode}>{t("sanctuary.acquire.offlineReady")}</Text>
              <Image source={SESSION_BOARS[pack.session]} style={styles.boar} resizeMode="cover" />
              <Text style={styles.knows}>{t("sanctuary.acquire.knows", { session: sessionName.toLowerCase() })}</Text>
              <Text style={styles.saved}>{t("sanctuary.acquire.saved")}</Text>
              <Pressable style={styles.doneBtn} onPress={onDone} accessibilityRole="button">
                <Text style={styles.doneText}>{t("sanctuary.acquire.done")}</Text>
              </Pressable>
            </Animated.View>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.82)", justifyContent: "center", padding: spacing.base },
  card: { borderRadius: 24, overflow: "hidden", backgroundColor: "#07110B", borderWidth: 1, borderColor: MATRIX_GREEN, minHeight: 420 },
  rainBox: { position: "absolute", top: 0, left: 0, right: 0, bottom: 0, overflow: "hidden" },
  rain: { position: "absolute", top: 0, color: MATRIX_GREEN, fontSize: 13, lineHeight: 18, fontFamily: "monospace" },
  steps: { flex: 1, justifyContent: "center", padding: spacing.lg, gap: spacing.md, backgroundColor: "rgba(7,17,11,0.55)" },
  mode: { ...typography.ui.caption, fontWeight: "700", color: MATRIX_GREEN, letterSpacing: 1, textAlign: "center" },
  stepRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  dot: { width: 12, height: 12, borderRadius: 6, borderWidth: 2, borderColor: "rgba(34,197,94,0.4)" },
  dotActive: { borderColor: MATRIX_GREEN, backgroundColor: MATRIX_GREEN_SOFT },
  dotDone: { borderColor: MATRIX_GREEN, backgroundColor: MATRIX_GREEN },
  stepText: { ...typography.ui.body, color: "rgba(220,255,230,0.45)", flex: 1 },
  stepTextOn: { color: "#E7FFEE" },
  note: { ...typography.ui.subtext, color: "rgba(220,255,230,0.6)", textAlign: "center", marginTop: spacing.sm },
  done: { alignItems: "center", paddingHorizontal: spacing.lg, paddingVertical: spacing.xxl, gap: spacing.md, backgroundColor: "rgba(7,17,11,0.4)", flex: 1, justifyContent: "center" },
  boar: { width: 220, height: 220, borderRadius: 20, borderWidth: 1, borderColor: MATRIX_GREEN },
  knows: { ...typography.ui.titleLg, color: "#E7FFEE", textAlign: "center" },
  saved: { ...typography.ui.body, color: "rgba(220,255,230,0.75)", textAlign: "center" },
  doneBtn: { marginTop: spacing.sm, minHeight: 48, paddingHorizontal: spacing.xl, borderRadius: radii.full, backgroundColor: MATRIX_GREEN, alignItems: "center", justifyContent: "center" },
  doneText: { ...typography.ui.body, fontWeight: "700", color: colors.text.inverse },
});
