import React from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { SHARE_STEPS, shareCanRetry, shareSucceeded, type ShareOutcome, type ShareStep } from "../eval/shareResults.pure";
import { Button, Icon, Progress, Screen, Section, Text, type IconName } from "./components";
import { useTokens } from "./theme";

export interface ShareLogLine {
  key: string;
  text: string;
}

interface Props {
  /** The step on screen while sending; null once the outcome is shown. */
  step: ShareStep | null;
  log: ShareLogLine[];
  outcome: ShareOutcome | null;
  /** "14:05" or "Tue 09:30", when the server said when sharing opens again. */
  retryAtText: string | null;
  onDone: () => void;
  onRetry: () => void;
  onExport: (format: "jsonl" | "csv") => void;
}

const RESULT_ICON: Partial<Record<ShareOutcome["result"], IconName>> = {
  shared: "check-circle",
  "shared-pending": "clock",
  "already-shared": "check-circle",
  "rate-limited": "clock",
  cooldown: "clock",
  "network-limited": "wifi",
  offline: "wifi-off",
  busy: "loader",
  "key-failed": "lock",
};

/**
 * The share after the user confirmed it: each step as it runs (a progress bar and a short log of
 * what the phone and the server did), then the outcome, why, and what to do. The run can always
 * be exported as JSONL or CSV from here, whatever happened.
 */
export function ShareProgress({ step, log, outcome, retryAtText, onDone, onRetry, onExport }: Props) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const sending = outcome === null;
  const index = step ? SHARE_STEPS.indexOf(step) : SHARE_STEPS.length;
  const value = sending ? (index + 0.5) / SHARE_STEPS.length : 1;
  const ok = outcome ? shareSucceeded(outcome.result) : false;
  const result = outcome?.result;
  const when = retryAtText && (result === "rate-limited" || result === "cooldown") ? "At" : "";

  return (
    <Screen
      edges={["top", "bottom", "left", "right"]}
      footer={
        sending ? undefined : (
          <View style={{ gap: tokens.space.sm }}>
            <View style={{ flexDirection: "row", gap: tokens.space.sm }}>
              {result && shareCanRetry(result) ? (
                <Button variant="secondary" label={t("evaluation.shareProgress.retry")} icon="refresh-cw" onPress={onRetry} style={{ flex: 1 }} />
              ) : null}
              <Button label={t("common.done")} onPress={onDone} style={{ flex: 1 }} />
            </View>
          </View>
        )
      }
    >
      <View style={{ gap: tokens.space.lg, paddingVertical: tokens.space.md }}>
        <View style={{ gap: tokens.space.xs }}>
          <Text variant="title1">
            {sending ? t("evaluation.shareProgress.sendingTitle") : t(`evaluation.shareProgress.title.${result}`)}
          </Text>
          {sending && step ? (
            <Text variant="callout" color="secondary">
              {t(`evaluation.shareProgress.step.${step}`)}
            </Text>
          ) : null}
        </View>

        <Progress
          value={value}
          label={t("evaluation.shareProgress.sendingTitle")}
          valueText={sending && step ? t(`evaluation.shareProgress.step.${step}`) : undefined}
          tone={!sending && !ok ? "danger" : "accent"}
        />

        {!sending && result ? (
          <View style={{ flexDirection: "row", gap: tokens.space.sm, alignItems: "flex-start" }}>
            {RESULT_ICON[result] ? (
              <Icon name={RESULT_ICON[result]!} size="md" color={ok ? tokens.color.status.success.solid : tokens.color.text.secondary} />
            ) : null}
            <Text variant="body" style={{ flex: 1 }}>
              {t(`evaluation.shareProgress.why.${result}${when}`, { time: retryAtText })}
            </Text>
          </View>
        ) : null}

        <Section title={t("evaluation.shareProgress.logTitle")}>
          <View style={{ padding: tokens.space.md, gap: tokens.space.xxs }} accessible accessibilityLabel={log.map((l) => l.text).join(". ")}>
            {log.map((l) => (
              <Text key={l.key} variant="mono" color="secondary">
                {l.text}
              </Text>
            ))}
            {outcome?.detail ? (
              <Text variant="mono" color={ok ? "secondary" : "danger"}>
                {t("evaluation.shareProgress.detail", { detail: outcome.detail })}
              </Text>
            ) : null}
          </View>
        </Section>

        {!sending ? (
          <Section title={t("evaluation.shareProgress.exportTitle")} footer={t("evaluation.shareProgress.exportFooter")}>
            <View style={{ flexDirection: "row", gap: tokens.space.sm, padding: tokens.space.md }}>
              <Button size="sm" variant="secondary" icon="download" label={t("evaluation.exportJsonl")} onPress={() => onExport("jsonl")} />
              <Button size="sm" variant="secondary" icon="download" label={t("evaluation.exportCsv")} onPress={() => onExport("csv")} />
            </View>
          </Section>
        ) : null}
      </View>
    </Screen>
  );
}
