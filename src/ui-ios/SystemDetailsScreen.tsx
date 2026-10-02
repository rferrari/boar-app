import React, { useCallback, useState } from "react";
import { useFocusEffect } from "@react-navigation/native";
import { useTranslation } from "react-i18next";
import * as FileSystem from "expo-file-system/legacy";
import { getDeviceTotalRamBytes, getMemoryInfo } from "ram-monitor";
import { llamaEngine } from "../inference/LlamaEngine";
import { availableRamFrom } from "../inference/memoryFit";
import { getAppPeakRssBytes } from "../services/telemetry";
import { shareDevice } from "../eval/shareResults";
import { describeChipset, describeCores, inferenceFeatures } from "../eval/shareResults.pure";
import { ListRow, Screen, Section } from "./components";
import { useTokens } from "./theme";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { formatBytes } from "./flows/format";
import { modelManager } from "./flows/useCatalog";

interface Details {
  rss: number;
  peak: number;
  total: number;
  models: number;
  knowledge: number;
  other: number;
  free: number;
}

/**
 * The original UI's usage stats, for benchmarking and bug reports: this phone's hardware (the same
 * readout a shared evaluation carries), BOAR's memory, its storage, and the loaded model. Performance ›
 * Advanced › System details; out of the way on purpose.
 */
export function SystemDetailsScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const lang = i18n.language;
  const [device] = useState(() => shareDevice());
  const [d, setD] = useState<Details | null>(null);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      (async () => {
        let rss = 0;
        try {
          rss = getMemoryInfo().rssBytes;
        } catch {
          rss = 0;
        }
        const [statuses, free] = await Promise.all([modelManager.statusAll().catch(() => []), FileSystem.getFreeDiskStorageAsync().catch(() => 0)]);
        const size = (kind: string) => statuses.filter((s) => s.present && s.asset.kind === kind).reduce((n, s) => n + s.sizeOnDiskBytes, 0);
        const models = size("llm");
        const knowledge = size("corpus");
        const other = statuses.filter((s) => s.present && s.asset.kind !== "llm" && s.asset.kind !== "corpus").reduce((n, s) => n + s.sizeOnDiskBytes, 0);
        if (!cancelled) setD({ rss, peak: getAppPeakRssBytes(), total: getDeviceTotalRamBytes(), models, knowledge, other, free });
      })();
      return () => {
        cancelled = true;
      };
    }, [])
  );

  const gb = (b: number | undefined) => (b && b > 0 ? formatBytes(b, lang) : "—");
  const features = inferenceFeatures(device.cpuFeatures);
  const yesNo = (v: boolean | undefined) => (v === undefined ? "—" : t(v ? "evaluation.preview.yes" : "evaluation.preview.no"));
  const model = llamaEngine.getModelInfo();

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.system.title")}</ScreenTitle>

      <Section title={t("evaluation.preview.phoneSection")}>
        <ListRow title={t("evaluation.preview.phone")} value={[device.brand, device.model].filter(Boolean).join(" ") || "—"} />
        <ListRow title={t("evaluation.preview.chipset")} value={describeChipset(device) ?? "—"} />
        <ListRow
          title={t("evaluation.preview.cpu")}
          value={[device.cpuCores ? t("evaluation.preview.cores", { count: device.cpuCores }) : null, describeCores(device.coreMaxFreqKHz)].filter(Boolean).join(" · ") || "—"}
        />
        <ListRow title="i8mm" value={yesNo(features?.i8mm)} />
        <ListRow title="dotprod" value={yesNo(features?.dotprod)} />
        <ListRow title={t("evaluation.preview.os")} value={device.osVersion ? `${device.platform === "ios" ? "iOS" : "Android"} ${device.osVersion}${device.apiLevel ? ` (API ${device.apiLevel})` : ""}` : "—"} />
      </Section>

      <Section title={t("flows.system.memory")} footer={t("flows.system.memoryFooter")}>
        <ListRow title={t("flows.system.ramTotal")} value={gb(d?.total)} />
        <ListRow title={t("flows.system.modelBudget")} value={d?.total ? gb(availableRamFrom({ totalBytes: d.total, rssBytes: 0 })) : "—"} />
        <ListRow title={t("flows.system.ramNow")} value={gb(d?.rss)} />
        <ListRow title={t("flows.system.ramPeak")} value={gb(d?.peak)} />
      </Section>

      <Section title={t("flows.system.storage")}>
        <ListRow title={t("flows.system.models")} value={gb(d?.models)} />
        <ListRow title={t("flows.system.knowledge")} value={gb(d?.knowledge)} />
        <ListRow title={t("flows.system.other")} value={gb(d?.other)} />
        <ListRow title={t("flows.system.free")} value={gb(d?.free)} />
      </Section>

      <Section title={t("flows.system.loaded")}>
        <ListRow title={t("flows.system.file")} value={model?.filename ?? t("flows.system.none")} />
        {model && <ListRow title={t("flows.system.context")} value={t("flows.system.contextValue", { n: model.nCtx })} />}
        {model && <ListRow title={t("flows.system.threads")} value={String(model.nThreads)} />}
      </Section>
    </Screen>
  );
}
