import React, { useMemo, useState } from "react";
import { Modal, View } from "react-native";
import { useTranslation } from "react-i18next";
import type { EvalResultRow } from "../eval/evalHarness.pure";
import { describeChipset, describeCores, inferenceFeatures, type ShareDevice } from "../eval/shareResults.pure";
import { scoreRun, SCORE_VERSION, REFERENCE_TOK_PER_SEC, WEIGHTS } from "../eval/score.pure";
import { Button, ListRow, Screen, Section, Text } from "./components";
import { useTokens } from "./theme";

interface Props {
  visible: boolean;
  rows: EvalResultRow[];
  device: ShareDevice;
  appVersion: string;
  sending: boolean;
  onCancel: () => void;
  onShare: () => void;
  /** Once the user pressed Share: the progress screen, shown in place of the preview. */
  progress?: React.ReactElement | null;
}

const gb = (bytes: number | undefined) => (bytes && bytes > 0 ? `${(bytes / 1024 ** 3).toFixed(1)} GB` : "—");
const secs = (ms: number | undefined) => (ms == null ? "—" : `${(ms / 1000).toFixed(1)} s`);
const pct = (x: number | undefined) => (x == null ? "—" : `${Math.round(x * 100)}%`);

/**
 * Everything a shared run carries, shown before anything is sent: the phone and its CPU, each
 * model's score with the raw numbers behind it, and every question and answer. The score shown
 * here is the app's copy of the formula (src/eval/score.pure.ts); the server recomputes it.
 */
export function SharePreview({ visible, rows, device, appVersion, sending, onCancel, onShare, progress }: Props) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const [showAnswers, setShowAnswers] = useState(false);
  const scores = useMemo(() => scoreRun(rows), [rows]);
  const features = inferenceFeatures(device.cpuFeatures);
  const yesNo = (v: boolean | undefined) => (v === undefined ? "—" : v ? t("evaluation.preview.yes") : t("evaluation.preview.no"));
  const cpu = [device.cpuCores ? t("evaluation.preview.cores", { count: device.cpuCores }) : null, describeCores(device.coreMaxFreqKHz)]
    .filter(Boolean)
    .join(" · ");
  const os = device.osVersion
    ? `${device.platform === "ios" ? "iOS" : "Android"} ${device.osVersion}${device.apiLevel ? ` (API ${device.apiLevel})` : ""}`
    : "—";

  if (progress) {
    // Back does nothing while sending (the progress screen's own buttons end it).
    return (
      <Modal visible={visible} animationType="slide" onRequestClose={sending ? () => {} : onCancel}>
        {progress}
      </Modal>
    );
  }

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onCancel}>
      <Screen
        edges={["top", "bottom", "left", "right"]}
        footer={
          <View style={{ flexDirection: "row", gap: tokens.space.sm }}>
            <Button variant="secondary" label={t("common.cancel")} onPress={onCancel} disabled={sending} style={{ flex: 1 }} />
            <Button label={sending ? t("evaluation.sharing") : t("evaluation.shareConfirm")} icon="share" onPress={onShare} disabled={sending} style={{ flex: 1 }} />
          </View>
        }
      >
        <View style={{ gap: tokens.space.lg, paddingVertical: tokens.space.md }}>
          <View style={{ gap: tokens.space.xs }}>
            <Text variant="title1">{t("evaluation.preview.title")}</Text>
            <Text variant="callout" color="secondary">
              {t("evaluation.preview.subtitle")}
            </Text>
          </View>

          <Section title={t("evaluation.preview.phoneSection")} footer={t("evaluation.preview.keyNote")}>
            <ListRow title={t("evaluation.preview.phone")} value={[device.brand, device.model].filter(Boolean).join(" ") || "—"} />
            <ListRow title={t("evaluation.preview.chipset")} value={describeChipset(device) ?? "—"} />
            <ListRow title={t("evaluation.preview.cpu")} value={cpu || "—"} />
            <ListRow title="i8mm" value={yesNo(features?.i8mm)} />
            <ListRow title="dotprod" value={yesNo(features?.dotprod)} />
            <ListRow title={t("evaluation.preview.ram")} value={gb(device.ramBytes)} />
            <ListRow title={t("evaluation.preview.os")} value={os} />
            <ListRow title={t("evaluation.preview.app")} value={appVersion} />
          </Section>

          {scores.map((s) => (
            <Section
              key={s.configId}
              title={`${s.modelLabel ?? s.configId} · ${s.score}/100`}
              footer={t("evaluation.preview.parts", { speed: pct(s.speed), reliability: pct(s.reliability), retrieval: pct(s.retrieval) })}
            >
              <ListRow title={t("evaluation.preview.tokPerSec")} value={s.medianTokPerSec == null ? "—" : s.medianTokPerSec.toFixed(1)} />
              <ListRow title={t("evaluation.preview.ttft")} value={secs(s.medianTtftMs)} />
              <ListRow title={t("evaluation.preview.total")} value={secs(s.medianTotalMs)} />
              <ListRow title={t("evaluation.preview.peakRam")} value={gb(s.peakRssBytes)} />
              <ListRow title={t("evaluation.preview.completed")} value={`${s.completed}/${s.answers}`} />
              <ListRow
                title={t("evaluation.preview.sources")}
                value={s.retrievalQuestions > 0 ? `${s.retrievalHits}/${s.retrievalQuestions}` : "—"}
              />
            </Section>
          ))}
          <Text variant="footnote" color="secondary">
            {t("evaluation.preview.formula", {
              version: SCORE_VERSION,
              speed: WEIGHTS.speed * 100,
              reliability: WEIGHTS.reliability * 100,
              retrieval: WEIGHTS.retrieval * 100,
              reference: REFERENCE_TOK_PER_SEC,
            })}
          </Text>

          <Section>
            <ListRow
              title={t("evaluation.preview.answers", { count: rows.length })}
              expanded={showAnswers}
              chevron
              onPress={() => setShowAnswers((v) => !v)}
            />
          </Section>
          {showAnswers &&
            rows.map((r) => (
              <View key={`${r.configId}:${r.queryId}`} style={{ gap: tokens.space.xxs }}>
                <Text variant="callout">{r.query}</Text>
                <Text variant="footnote">{r.answer || "—"}</Text>
                <Text variant="caption" color="secondary">
                  {r.configLabel}
                </Text>
              </View>
            ))}

          <Section title={t("evaluation.preview.notSentSection")}>
            <View style={{ padding: tokens.space.md }}>
              <Text variant="footnote">{t("evaluation.preview.notSent")}</Text>
            </View>
          </Section>
        </View>
      </Screen>
    </Modal>
  );
}
