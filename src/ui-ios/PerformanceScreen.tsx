import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { Badge, Button, EmptyState, IconText, ListRow, Progress, Reveal, Screen, Section, Sheet, Skeleton, Text, useLateLoad, useToast } from "./components";
import type { Tone } from "./theme";
import { useTokens } from "./theme";
import { catalogLabel } from "./flows/catalogLabel";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { MODEL_CATALOG, RAM_BUDGET_BYTES, STORAGE_BUDGET_BYTES } from "../models/manifest";
import {
  clearExecutionTelemetry,
  ExecutionTelemetryRecord,
  exportExecutionTelemetry,
  listRecentExecutions,
} from "../services/executionTelemetry";
import { getAppPeakRssBytes } from "../services/telemetry";
import { PerfBand, PERF_BANDS_PROVISIONAL, recordTokPerSec, summarizeRecent, tokPerSecBand, ttftBand } from "./flows/perfBands";
import { useCatalog } from "./flows/useCatalog";
import { formatBytes, formatRam, formatRate, formatSeconds } from "./flows/format";

/** Rough English/Portuguese average; the screen shows words, not tokens (copy-wrap: no jargon). */
import type { RootStackParamList } from "./navigation/types";
import { userErrorKey } from "./flows/userError";

type Nav = NativeStackNavigationProp<RootStackParamList>;

const BAND_TONE: Record<PerfBand, Tone> = { fast: "success", ok: "neutral", slow: "warning" };

function useRecords() {
  const [records, setRecords] = useState<ExecutionTelemetryRecord[] | null>(null);
  const [error, setError] = useState(false);
  const load = useCallback(async () => {
    setError(false);
    try {
      setRecords(await listRecentExecutions(200));
    } catch {
      setError(true);
    }
  }, []);
  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );
  return { records, error, load, setRecords };
}

function Metric({ label, value, band }: { label: string; value: string; band?: PerfBand }) {
  const { t } = useTranslation();
  const tokens = useTokens();
  return (
    <View
      accessible
      accessibilityLabel={[label, value, band && t(`flows.performance.band.${band}`)].filter(Boolean).join(", ")}
      style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: tokens.space.sm, paddingVertical: tokens.space.xs }}
    >
      <Text variant="callout" color="secondary">
        {label}
      </Text>
      <View style={{ flexDirection: "row", alignItems: "center", gap: tokens.space.sm }}>
        {/* The value is Baloo: centred by its optical centre, or it rides ~4 pt above the label and seal on iOS (icon-align). */}
        <IconText variant="headline" numeric centerOnBox>
          {value}
        </IconText>
        {band && <Badge label={t(`flows.performance.band.${band}`)} tone={BAND_TONE[band]} />}
      </View>
    </View>
  );
}

function Meter({ label, used, total, text }: { label: string; used: number; total: number; text: string }) {
  const tokens = useTokens();
  return (
    <View style={{ gap: tokens.space.xs }}>
      <View style={{ flexDirection: "row", justifyContent: "space-between", flexWrap: "wrap", gap: tokens.space.sm }}>
        <Text variant="callout" color="secondary">
          {label}
        </Text>
        <Text variant="callout" numeric>
          {text}
        </Text>
      </View>
      <Progress label={label} value={Math.min(used / total, 1)} valueText={text} tone="field" />
    </View>
  );
}

export function PerformanceScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const navigation = useNavigation<Nav>();
  const catalog = useCatalog();
  const { refresh } = catalog;
  const { records, error, load } = useRecords();
  const lang = i18n.language;
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  const lateLoad = useLateLoad(!!records && catalog.loaded);

  if (error) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("nav.performance")}</ScreenTitle>
        <EmptyState tone="error" title={t("flows.performance.loadFailed")} actionLabel={t("flows.row.retry")} onAction={load} />
      </Screen>
    );
  }
  if (!records || !catalog.loaded) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("nav.performance")}</ScreenTitle>
        <View accessible accessibilityLabel={t("flows.common.loading")} style={{ gap: tokens.space.md }}>
          <Skeleton height={tokens.size.skeletonCard} />
          <Skeleton height={tokens.size.skeletonCard} />
        </View>
      </Screen>
    );
  }

  const last = records.find((r) => r.outcome === "success");
  const lastRate = last ? recordTokPerSec(last) : undefined;
  const typical = summarizeRecent(records);
  const modelLabel = (id?: string) => { const m = MODEL_CATALOG.find((x) => x.id === id) ?? catalog.discovered.find((x) => x.id === id); return m ? catalogLabel(m, t) : id; };

  const peakRss = getAppPeakRssBytes();
  let models = 0;
  let knowledge = 0;
  for (const s of Object.values(catalog.statuses)) {
    if (!s.present) continue;
    if (s.asset.kind === "corpus") knowledge += s.sizeOnDiskBytes;
    else models += s.sizeOnDiskBytes;
  }

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("nav.performance")}</ScreenTitle>
      {/* Replaces the skeleton: fades in when the data came after the screen (TR-5). */}
      <Reveal animate={lateLoad} style={screenRhythm(tokens)}>
        {PERF_BANDS_PROVISIONAL && (
          <Text variant="footnote" color="secondary">
            {t("flows.performance.provisional")}
          </Text>
        )}

        <Section title={t("flows.performance.lastAnswer")}>
          <View style={{ padding: tokens.space.base, gap: tokens.space.xs }}>
            {last ? (
              <>
                {last.ttftMs != null && (
                  <Metric label={t("flows.performance.ttft")} value={formatSeconds(last.ttftMs, lang)} band={ttftBand(last.ttftMs)} />
                )}
                {lastRate != null && (
                  <Metric
                    label={t("flows.performance.speed")}
                    value={t("flows.performance.rate", { rate: formatRate(lastRate, lang) })}
                    band={tokPerSecBand(lastRate)}
                  />
                )}
                {last.totalLatencyMs != null && <Metric label={t("flows.performance.total")} value={formatSeconds(last.totalLatencyMs, lang)} />}
                <Text variant="footnote" color="secondary">
                  {[modelLabel(last.modelId), last.retrievalUsed ? t("flows.performance.usedSources") : t("flows.performance.noSources")]
                    .filter(Boolean)
                    .join(" · ")}
                </Text>
              </>
            ) : (
              <Text variant="callout" color="secondary">
                {t("flows.performance.empty")}
              </Text>
            )}
          </View>
        </Section>

        {typical.sampleSize > 1 && (
          <Section title={t("flows.performance.typical", { count: typical.sampleSize })} footer={t("flows.performance.typicalFooter")}>
            <View style={{ padding: tokens.space.base, gap: tokens.space.xs }}>
              {typical.ttftMs != null && (
                <Metric label={t("flows.performance.ttft")} value={formatSeconds(typical.ttftMs, lang)} band={ttftBand(typical.ttftMs)} />
              )}
              {typical.tokPerSec != null && (
                <Metric
                  label={t("flows.performance.speed")}
                  value={t("flows.performance.rate", { rate: formatRate(typical.tokPerSec, lang) })}
                  band={tokPerSecBand(typical.tokPerSec)}
                />
              )}
              {typical.totalLatencyMs != null && <Metric label={t("flows.performance.total")} value={formatSeconds(typical.totalLatencyMs, lang)} />}
            </View>
          </Section>
        )}

        <Section title={t("flows.performance.fits")} footer={t("flows.performance.fitsFooter", { ram: formatRam(RAM_BUDGET_BYTES, lang), storage: formatBytes(STORAGE_BUDGET_BYTES, lang) })}>
          <View style={{ padding: tokens.space.base, gap: tokens.space.base }}>
            {peakRss > 0 && (
              <Meter
                label={t("flows.performance.memory")}
                used={peakRss}
                total={RAM_BUDGET_BYTES}
                text={t("flows.performance.ofLimit", { used: formatRam(peakRss, lang), limit: formatRam(RAM_BUDGET_BYTES, lang) })}
              />
            )}
            <Meter
              label={t("flows.performance.storage")}
              used={models + knowledge}
              total={STORAGE_BUDGET_BYTES}
              text={t("flows.performance.ofLimit", { used: formatBytes(models + knowledge, lang), limit: formatBytes(STORAGE_BUDGET_BYTES, lang) })}
            />
            <Text variant="footnote" color="secondary">
              {t("flows.performance.storageSplit", { models: formatBytes(models, lang), knowledge: formatBytes(knowledge, lang) })}
            </Text>
            {catalog.deviceRamBytes > 0 && (
              <Text variant="footnote" color="secondary">
                {t("flows.performance.deviceRam", { ram: formatRam(catalog.deviceRamBytes, lang) })}
              </Text>
            )}
          </View>
        </Section>

        <Section title={t("flows.models.advanced")}>
          <ListRow icon="list" title={t("flows.performance.logsTitle")} value={String(records.length)} onPress={() => navigation.navigate("PerformanceLogs")} />
          {/* In every build: people run the evaluation on their own phone and can share it (Share results,
              docs/RESULTS_SCORE.md). Starting one from a connected computer (ChatScreen's device request)
              stays a development-build feature. */}
          <ListRow icon="check-square" title={t("flows.performance.evaluationTitle")} subtitle={t("flows.performance.evaluationSub")} onPress={() => navigation.navigate("Evaluation")} />
          <ListRow icon="cpu" title={t("flows.system.title")} subtitle={t("flows.system.sub")} onPress={() => navigation.navigate("SystemDetails")} />
        </Section>
      </Reveal>
    </Screen>
  );
}

/** A logged model id as its catalog name; a model not in the manifest (a Hugging Face file) keeps its id. */
function logModelLabel(id: string | undefined, t: TFunction): string | undefined {
  if (!id) return undefined;
  const m = MODEL_CATALOG.find((x) => x.id === id);
  return m ? catalogLabel(m, t) : id;
}

function residencyKey(r: ExecutionTelemetryRecord): string {
  return `flows.performance.residency.${r.modelResidency ?? "unknown"}`;
}

/** Log rows mounted per page. */
const LOGS_PAGE = 30;

export function PerformanceLogsScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const { records, error, load, setRecords } = useRecords();
  const [exporting, setExporting] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  // Up to 200 records: mount them a page at a time, not 200 rows in one JS task (perf audit #29).
  const [shown, setShown] = useState(LOGS_PAGE);
  const lang = i18n.language;

  const doExport = async (format: "json" | "csv") => {
    setExporting(true);
    try {
      await exportExecutionTelemetry(format);
    } catch (e: any) {
      toast({ message: t("flows.performance.exportFailed", { error: t(userErrorKey(e)) }), tone: "danger" });
    } finally {
      setExporting(false);
    }
  };

  const lateLoad = useLateLoad(!!records);

  if (error) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("flows.performance.logsTitle")}</ScreenTitle>
        <EmptyState tone="error" title={t("flows.performance.loadFailed")} actionLabel={t("flows.row.retry")} onAction={load} />
      </Screen>
    );
  }
  if (!records) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("flows.performance.logsTitle")}</ScreenTitle>
        <View style={{ gap: tokens.space.sm }}>
          <Skeleton height={tokens.size.row} />
          <Skeleton height={tokens.size.row} />
        </View>
      </Screen>
    );
  }
  if (records.length === 0) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("flows.performance.logsTitle")}</ScreenTitle>
        <EmptyState icon="activity" title={t("flows.performance.logsEmpty")} body={t("flows.performance.empty")} />
      </Screen>
    );
  }

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.performance.logsTitle")}</ScreenTitle>
      {/* Replaces the skeleton: fades in when the data came after the screen (TR-5). */}
      <Reveal animate={lateLoad} style={screenRhythm(tokens)}>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: tokens.space.sm }}>
          <Button size="sm" variant="secondary" icon="share" label={t("flows.performance.exportJson")} loading={exporting} onPress={() => doExport("json")} />
          <Button size="sm" variant="secondary" icon="share" label={t("flows.performance.exportCsv")} disabled={exporting} onPress={() => doExport("csv")} />
          <Button size="sm" variant="ghost" tone="danger" icon="trash-2" label={t("flows.performance.clear")} onPress={() => setClearOpen(true)} />
        </View>
        <Section>
          {records.slice(0, shown).map((r) => {
            const rate = recordTokPerSec(r);
            const parts = [
              r.ttftMs != null && `${t("flows.performance.startLabel")} ${formatSeconds(r.ttftMs, lang)}`,
              rate != null && t("flows.performance.rate", { rate: formatRate(rate, lang) }),
              r.totalLatencyMs != null && `${t("flows.performance.total")} ${formatSeconds(r.totalLatencyMs, lang)}`,
            ].filter(Boolean);
            // Copy-wrap: memory and load go on their own line so the first one stays short.
            const extra = [
              r.peakRssBytes != null && `${t("flows.performance.memoryLabel")} ${formatBytes(r.peakRssBytes, lang)}`,
              r.modelLoadMs != null && `${t("flows.performance.load")} ${formatSeconds(r.modelLoadMs, lang)}`,
            ].filter(Boolean);
            return (
              <ListRow
                key={r.id}
                // The model's name, not its engine id; the raw task enum stays in the export (Prism FL-19).
                title={logModelLabel(r.modelId, t) ?? t("flows.performance.noModel")}
                value={t(`flows.performance.outcome.${r.outcome ?? "success"}`)}
                subtitle={[parts.join(" · "), extra.join(" · "), [t(residencyKey(r)), new Date(r.createdAt).toLocaleString(lang)].filter(Boolean).join(" · ")].filter(Boolean).join("\n")}
              />
            );
          })}
        </Section>
        {records.length > shown && (
          <Button size="sm" variant="secondary" label={t("flows.performance.showMore")} onPress={() => setShown((n) => n + LOGS_PAGE)} />
        )}

        <Sheet
          visible={clearOpen}
          onClose={() => setClearOpen(false)}
          title={t("flows.performance.clearTitle")}
          description={t("flows.performance.clearBody")}
          footer={
            <>
              <Button label={t("common.cancel")} variant="secondary" fullWidth onPress={() => setClearOpen(false)} />
              <Button
                label={t("flows.performance.clear")}
                variant="destructive"
                fullWidth
                onPress={async () => {
                  await clearExecutionTelemetry();
                  setClearOpen(false);
                  setRecords([]);
                }}
              />
            </>
          }
        />
      </Reveal>
    </Screen>
  );
}
