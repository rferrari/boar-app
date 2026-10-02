import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import * as FileSystem from "expo-file-system/legacy";
import { getDeviceTotalRamBytes, getMemoryInfo } from "ram-monitor";
import { listRecentExecutions } from "../../services/executionTelemetry";
import { recordTokPerSec } from "./perfBands";
import { Progress, Text } from "../components";
import { useTokens } from "../theme";
import { formatBytes, formatRate } from "./format";
import { modelManager } from "./useCatalog";

/** Refresh rate while the menu is open; nothing is read while it is closed. */
const POLL_MS = 5000;

/**
 * The menu's live stats, as the original UI's footer: small bars for the RAM BOAR uses (of the phone's)
 * and what it stores (of what it could), and the last answer's speed. Turned off in Settings.
 */
export function LiveStats({ active }: { active: boolean }) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const [stats, setStats] = useState<{ rss?: number; ram?: number; storage?: number; free?: number; rate?: number }>({});

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    const poll = async () => {
      let rss: number | undefined;
      try {
        rss = getMemoryInfo().rssBytes || undefined;
      } catch {
        rss = undefined;
      }
      const [storage, free, records] = await Promise.all([
        modelManager.currentStorageUsageBytes().catch(() => undefined),
        FileSystem.getFreeDiskStorageAsync().catch(() => undefined),
        listRecentExecutions(5).catch(() => []),
      ]);
      const last = records.find((r) => r.outcome === "success" && recordTokPerSec(r) !== undefined);
      let ram: number | undefined;
      try {
        ram = getDeviceTotalRamBytes() || undefined;
      } catch {
        ram = undefined;
      }
      if (!cancelled) setStats({ rss, ram, storage, free, rate: last ? recordTokPerSec(last) : undefined });
    };
    poll();
    const id = setInterval(poll, POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [active]);

  const lang = i18n.language;
  const bars = [
    stats.rss != null && stats.ram
      ? { key: "ram", label: t("nav.liveStats.ramLabel"), value: formatBytes(stats.rss, lang), fraction: stats.rss / stats.ram }
      : null,
    stats.storage != null && stats.free != null
      ? { key: "storage", label: t("nav.liveStats.storageLabel"), value: formatBytes(stats.storage, lang), fraction: stats.storage / (stats.storage + stats.free) }
      : null,
  ].filter((b): b is { key: string; label: string; value: string; fraction: number } => !!b);
  if (bars.length === 0 && stats.rate == null) return null;
  const row = { flexDirection: "row" as const, alignItems: "center" as const, gap: tokens.space.sm };
  return (
    <View style={{ paddingHorizontal: tokens.space.base, paddingVertical: tokens.space.sm, gap: tokens.space.xs }}>
      {bars.map((b) => (
        <View key={b.key} style={row}>
          <Text variant="caption" color="tertiary" style={{ width: 56 }}>
            {b.label}
          </Text>
          <View style={{ flex: 1 }}>
            <Progress label={b.label} value={Math.min(1, b.fraction)} valueText={b.value} height={4} />
          </View>
          <Text variant="caption" color="tertiary" numeric>
            {b.value}
          </Text>
        </View>
      ))}
      {stats.rate != null && (
        <View style={row}>
          <Text variant="caption" color="tertiary" style={{ width: 56 }}>
            {t("nav.liveStats.speedLabel")}
          </Text>
          <Text variant="caption" color="tertiary" numeric>
            {t("flows.performance.rate", { rate: formatRate(stats.rate, lang) })}
          </Text>
        </View>
      )}
    </View>
  );
}
