/**
 * The Knowledge Sanctuary preview: a community library of knowledge packs you get online and use
 * offline. Everything here is a clickable mock (catalog.ts, sanctuary.pure.ts): no request is
 * sent, no wallet is touched, and money actions are marked PREVIEW · NOT LIVE.
 */
import React, { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { Image, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Text } from "../components/AppText";
import { colors } from "../theme/colors";
import { typography } from "../theme/typography";
import { radii, spacing } from "../theme/spacing";
import { Toast } from "../Toast";
import { getHideSanctuaryWelcome, setHideSanctuaryWelcome } from "../../models/settings";
import { ENTRANCES, SESSION_BOARS, SESSION_LOCKED } from "./art";
import { SESSIONS, packsOf, type MockPack, type SessionId } from "./catalog";
import { initialSanctuary, pickEntrance, sanctuaryReducer, sessionAcquired, statsOf } from "./sanctuary.pure";
import { WelcomeTeaser } from "./WelcomeTeaser";
import { AcquireModal } from "./AcquireModal";
import { TipSheet } from "./TipSheet";
import { PublishWizard } from "./PublishWizard";
import { ReportSheet } from "./ReportSheet";
import { PreviewSheet } from "./PreviewSheet";
import { MATRIX_GREEN } from "./style";

export function SanctuaryScreen({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const [state, dispatch] = useReducer(sanctuaryReducer, initialSanctuary);
  // A different entrance scene each time the Sanctuary opens.
  const entrance = useMemo(() => pickEntrance(ENTRANCES.length), []);
  const [welcome, setWelcome] = useState<boolean | null>(null);
  const [session, setSession] = useState<SessionId | null>(null);
  const [acquiring, setAcquiring] = useState<MockPack | null>(null);
  const [tipping, setTipping] = useState<MockPack | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [reporting, setReporting] = useState<MockPack | null>(null);
  const [previewing, setPreviewing] = useState<MockPack | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const hideToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    getHideSanctuaryWelcome()
      .then((hide) => setWelcome(!hide))
      .catch(() => setWelcome(true));
  }, []);

  const sessionName = (s: SessionId) => t(`sanctuary.sessions.${s}.name`);
  const price = (p: MockPack) =>
    p.price.kind === "free"
      ? t("sanctuary.price.free")
      : p.price.kind === "once"
        ? t("sanctuary.price.once", { boar: p.price.boar })
        : t("sanctuary.price.subscription", { boar: p.price.boarPerMonth });

  const packCard = (p: MockPack) => {
    const have = !!state.acquired[p.id];
    const vote = state.votes[p.id];
    const stats = statsOf(p, state);
    return (
      <View key={p.id} style={styles.pack}>
        <Text style={styles.packTitle}>{t(`sanctuary.packs.${p.id}`)}</Text>
        <Text style={styles.packMeta}>
          {t("sanctuary.by", { creator: p.creator })} · {t("sanctuary.size", { mb: p.sizeMb })} · {p.rating.toFixed(1)} / 5
        </Text>
        <View style={styles.priceRow}>
          <Text style={styles.price}>{price(p)}</Text>
          {p.price.kind !== "free" && <Text style={styles.previewTag}>{t("sanctuary.badge")}</Text>}
        </View>
        <View style={styles.stats}>
          <Text style={styles.stat}>▲ {stats.up}</Text>
          <Text style={styles.stat}>▼ {stats.down}</Text>
          <Text style={styles.stat}>{t("sanctuary.stats.reports", { count: stats.reports })}</Text>
          <Text style={styles.stat}>{t("sanctuary.stats.tipped", { boar: stats.tippedBoar })}</Text>
        </View>
        <Pressable style={styles.previewBtn} onPress={() => setPreviewing(p)} accessibilityRole="button">
          <Text style={styles.previewBtnText}>{t("sanctuary.previewSheet.open")}</Text>
        </Pressable>
        <View style={styles.actions}>
          <Pressable style={[styles.btn, have ? styles.btnDone : styles.btnPrimary]} disabled={have} onPress={() => setAcquiring(p)}>
            <Text style={[styles.btnText, !have && styles.btnTextPrimary]}>{have ? t("sanctuary.acquired") : t("sanctuary.get")}</Text>
          </Pressable>
          <Pressable style={styles.btn} onPress={() => setTipping(p)}>
            <Text style={styles.btnText}>{t("sanctuary.tip")}</Text>
          </Pressable>
          <Pressable style={[styles.btn, state.subscribed[p.id] && styles.btnOn]} onPress={() => dispatch({ type: "toggleSubscribe", packId: p.id })}>
            <Text style={styles.btnText}>{state.subscribed[p.id] ? t("sanctuary.subscribed") : t("sanctuary.subscribe")}</Text>
          </Pressable>
        </View>
        {have && (
          <View style={styles.actions}>
            <Pressable style={[styles.btnSmall, vote === 1 && styles.btnOn]} onPress={() => dispatch({ type: "vote", packId: p.id, vote: 1 })} accessibilityLabel="Good">
              <Text style={styles.btnText}>▲</Text>
            </Pressable>
            <Pressable style={[styles.btnSmall, vote === -1 && styles.btnOn]} onPress={() => dispatch({ type: "vote", packId: p.id, vote: -1 })} accessibilityLabel="Bad">
              <Text style={styles.btnText}>▼</Text>
            </Pressable>
            <Pressable
              style={styles.btn}
              disabled={!!state.reported[p.id]}
              onPress={() => setReporting(p)}
            >
              <Text style={[styles.btnText, !!state.reported[p.id] && styles.dim]}>{t("sanctuary.report")}</Text>
            </Pressable>
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={{ flex: 1 }}>
          <Text style={styles.title}>{t("sanctuary.title")}</Text>
          <Text style={styles.subtitle}>{t("sanctuary.subtitle")}</Text>
        </View>
        <Pressable onPress={session ? () => setSession(null) : onClose} hitSlop={8} style={styles.closeBtn}>
          <Text style={styles.closeText}>{session ? t("common.back") : t("common.done")}</Text>
        </Pressable>
      </View>
      <View style={styles.previewBar}>
        <Text style={styles.previewBarText}>{t("sanctuary.previewBar")}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.body}>
        {!session ? (
          <>
            <View style={styles.heroFrame}>
              <Image source={ENTRANCES[entrance]} style={styles.fill} resizeMode="cover" />
            </View>
            <View style={styles.modes}>
              <Text style={styles.mode}>{t("sanctuary.dualMode.online")}</Text>
              <Text style={styles.mode}>{t("sanctuary.dualMode.offline")}</Text>
            </View>
            <Text style={styles.section}>{t("sanctuary.sessionsTitle")}</Text>
            <View style={styles.grid}>
              {SESSIONS.map((s) => {
                const unlocked = sessionAcquired(state, s);
                return (
                  <Pressable key={s} style={[styles.session, unlocked && styles.sessionOn]} onPress={() => setSession(s)} accessibilityRole="button">
                    {/* A square frame that clips its image, so the art always stays inside the card. */}
                    <View style={styles.sessionArt}>
                      <Image source={unlocked ? SESSION_BOARS[s] : SESSION_LOCKED[s]} style={styles.fill} resizeMode="cover" />
                      {!unlocked && (
                        <View style={styles.lockedLabel}>
                          <Text style={styles.lockedText}>{t("sanctuary.locked")}</Text>
                        </View>
                      )}
                    </View>
                    <Text style={styles.sessionName}>{sessionName(s)}</Text>
                    <Text style={styles.sessionDesc} numberOfLines={2}>
                      {t(`sanctuary.sessions.${s}.desc`)}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </>
        ) : (
          <>
            <View style={styles.sessionHeader}>
              <View style={styles.sessionHeaderArt}>
                <Image source={sessionAcquired(state, session) ? SESSION_BOARS[session] : SESSION_LOCKED[session]} style={styles.fill} resizeMode="cover" />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.sessionTitle}>{sessionName(session)}</Text>
                <Text style={styles.sessionDesc}>{t(`sanctuary.sessions.${session}.desc`)}</Text>
              </View>
            </View>
            {packsOf(session).map(packCard)}
          </>
        )}
      </ScrollView>

      <Pressable style={styles.fab} onPress={() => setPublishing(true)} accessibilityRole="button">
        <Text style={styles.fabText}>+ {t("sanctuary.publish.button")}</Text>
      </Pressable>

      <Toast message={toast} onHide={hideToast} />

      {welcome && (
        <WelcomeTeaser
          entrance={entrance}
          onClose={onClose}
          onEnter={(dontShow) => {
            setWelcome(false);
            if (dontShow) setHideSanctuaryWelcome(true).catch(() => {});
          }}
        />
      )}
      {acquiring && (
        <AcquireModal
          pack={acquiring}
          sessionName={sessionName(acquiring.session)}
          onDone={() => {
            dispatch({ type: "acquire", packId: acquiring.id });
            setAcquiring(null);
          }}
        />
      )}
      {tipping && (
        <TipSheet
          creator={tipping.creator}
          onCancel={() => setTipping(null)}
          onSend={(amount) => {
            dispatch({ type: "tip", packId: tipping.id, amount });
            setTipping(null);
            setToast(t("sanctuary.tipSheet.sent"));
          }}
        />
      )}
      {reporting && (
        <ReportSheet
          packTitle={t(`sanctuary.packs.${reporting.id}`)}
          onClose={() => setReporting(null)}
          onSubmit={(reason) => dispatch({ type: "report", packId: reporting.id, reason })}
        />
      )}
      {previewing && (
        <PreviewSheet title={t(`sanctuary.packs.${previewing.id}`)} markdown={previewing.preview} onClose={() => setPreviewing(null)} />
      )}
      {publishing && (
        <PublishWizard
          onClose={() => setPublishing(false)}
          onPublish={(draft) => {
            dispatch({ type: "saveDraft", draft });
            setPublishing(false);
            setToast(t("sanctuary.publish.saved"));
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg.terminal },
  header: { flexDirection: "row", alignItems: "center", paddingHorizontal: spacing.base, paddingTop: spacing.sm, paddingBottom: spacing.sm, gap: spacing.sm },
  title: { ...typography.ui.title, color: colors.text.heading },
  subtitle: { ...typography.ui.subtext, color: colors.text.muted },
  closeBtn: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: radii.full, backgroundColor: colors.bg.cardHover },
  closeText: { ...typography.ui.body, fontWeight: "600", color: colors.text.accentEmerald },
  previewBar: { backgroundColor: colors.amber.bgSubtle, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.amber.border, paddingVertical: 4, alignItems: "center" },
  previewBarText: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber },
  body: { padding: spacing.base, gap: spacing.md, paddingBottom: 120 },
  // The entrance art is square: shown whole, not cropped.
  heroFrame: { width: "100%", aspectRatio: 1, borderRadius: 20, overflow: "hidden" },
  fill: { width: "100%", height: "100%" },
  modes: { gap: 4 },
  mode: { ...typography.ui.subtext, color: colors.text.secondary },
  section: { ...typography.ui.titleSm, color: colors.text.heading, marginTop: spacing.sm },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  session: { width: "48%", backgroundColor: colors.bg.card, borderRadius: 18, padding: spacing.sm, gap: 6 },
  sessionOn: { borderWidth: 1, borderColor: MATRIX_GREEN },
  sessionArt: { width: "100%", aspectRatio: 1, borderRadius: 14, overflow: "hidden", backgroundColor: colors.bg.cardHover },
  lockedLabel: { position: "absolute", left: 0, right: 0, bottom: 0, paddingVertical: 4, paddingHorizontal: 6, backgroundColor: "rgba(0,0,0,0.6)" },
  lockedText: { ...typography.ui.caption, color: colors.text.secondary, textAlign: "center" },
  sessionName: { ...typography.ui.body, fontWeight: "600", color: colors.text.heading },
  sessionDesc: { ...typography.ui.subtext, color: colors.text.muted },
  sessionHeader: { flexDirection: "row", gap: spacing.md, alignItems: "center" },
  sessionHeaderArt: { width: 72, height: 72, borderRadius: 14, overflow: "hidden", borderWidth: 1, borderColor: MATRIX_GREEN },
  sessionTitle: { ...typography.ui.titleLg, color: colors.text.heading },
  pack: { backgroundColor: colors.bg.card, borderRadius: 18, padding: spacing.md, gap: spacing.sm },
  packTitle: { ...typography.ui.title, color: colors.text.heading },
  packMeta: { ...typography.ui.subtext, color: colors.text.muted },
  priceRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  price: { ...typography.ui.body, fontWeight: "600", color: colors.text.accentEmerald },
  previewTag: { ...typography.ui.caption, fontWeight: "700", color: colors.text.accentAmber, borderWidth: 1, borderColor: colors.amber.border, borderRadius: radii.full, paddingHorizontal: 8 },
  actions: { flexDirection: "row", gap: spacing.sm, flexWrap: "wrap" },
  stats: { flexDirection: "row", flexWrap: "wrap", columnGap: spacing.md, rowGap: 2 },
  stat: { ...typography.ui.subtext, color: colors.text.secondary },
  previewBtn: { alignSelf: "flex-start", minHeight: 40, paddingHorizontal: spacing.base, borderRadius: radii.full, borderWidth: 1, borderColor: colors.border.elevated, justifyContent: "center" },
  previewBtnText: { ...typography.ui.body, fontWeight: "600", color: colors.text.primary },
  btn: { minHeight: 44, paddingHorizontal: spacing.base, borderRadius: radii.full, backgroundColor: colors.bg.cardHover, alignItems: "center", justifyContent: "center" },
  btnSmall: { minHeight: 44, minWidth: 44, borderRadius: radii.full, backgroundColor: colors.bg.cardHover, alignItems: "center", justifyContent: "center" },
  btnPrimary: { backgroundColor: colors.emerald[600] },
  btnDone: { backgroundColor: "rgba(34,197,94,0.14)", borderWidth: 1, borderColor: MATRIX_GREEN },
  btnOn: { backgroundColor: colors.emerald.bgSubtle, borderWidth: 1, borderColor: colors.emerald[500] },
  btnText: { ...typography.ui.body, fontWeight: "600", color: colors.text.primary },
  btnTextPrimary: { color: colors.text.heading },
  dim: { color: colors.text.dim },
  fab: { position: "absolute", right: spacing.base, bottom: spacing.xl, minHeight: 52, paddingHorizontal: spacing.lg, borderRadius: radii.full, backgroundColor: colors.emerald[600], alignItems: "center", justifyContent: "center", elevation: 4 },
  fabText: { ...typography.ui.body, fontWeight: "700", color: colors.text.heading },
});
