import React from "react";
import { Pressable, View } from "react-native";
import { useTranslation } from "react-i18next";
import { findAsset } from "../../models/assetRegistry";
import { Card, Progress, Text } from "../components";
import { useTokens } from "../theme";
import { catalogLabel } from "./catalogLabel";
import { formatCount } from "./format";
import { LoadingLine } from "./LoadingLine";
import type { Activity } from "./useActivity";

interface Props {
  activity: Activity;
  /** Opens the screen that owns the work: Models for a download, Knowledge for the index. */
  onOpenDownloads?: () => void;
  onOpenIndex?: () => void;
}

/**
 * "In progress": every download and the search index being built, with a bar each, shown outside the
 * setup (the menu). Nothing is drawn when nothing runs.
 */
export function ActivityCard({ activity, onOpenDownloads, onOpenIndex }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const { downloads, index } = activity;
  if (downloads.length === 0 && !index) return null;
  const lang = i18n.language;
  return (
    <Card style={{ gap: tokens.space.sm }}>
      <Text variant="label" color="secondary" header>
        {t("flows.activity.title")}
      </Text>
      {downloads.map((d) => {
        const asset = findAsset(d.assetId);
        const name = asset ? catalogLabel(asset, t, { technical: true }) : d.assetId;
        const label = t(`flows.activity.${d.phase}`, { name });
        const pct = Math.floor(d.progress * 100);
        const queued = d.phase === "queued";
        return (
          <Pressable key={d.assetId} onPress={onOpenDownloads} accessibilityRole="button" style={{ gap: tokens.space.xxs }}>
            <View style={{ flexDirection: "row", justifyContent: "space-between", gap: tokens.space.sm }}>
              <Text variant="footnote" numberOfLines={1} style={{ flex: 1 }}>
                {label}
              </Text>
              {queued ? null : <Text variant="footnote" color="secondary">{`${pct}%`}</Text>}
            </View>
            {queued ? null : <Progress label={label} value={d.progress} />}
          </Pressable>
        );
      })}
      {index && (
        <Pressable onPress={onOpenIndex} accessibilityRole="button" style={{ gap: tokens.space.xxs }}>
          <View style={{ flexDirection: "row", justifyContent: "space-between", gap: tokens.space.sm }}>
            <Text variant="footnote" numberOfLines={1} style={{ flex: 1 }}>
              {t("flows.activity.indexing")}
            </Text>
            <Text variant="footnote" color="secondary">
              {t("flows.onboarding.indexCounter", { done: formatCount(index.done, lang), total: formatCount(index.total, lang) })}
            </Text>
          </View>
          <Progress label={t("flows.activity.indexing")} value={index.done / index.total} />
          {index.title ? (
            <Text variant="caption" color="secondary" numberOfLines={1}>
              {t("flows.onboarding.indexReading", { title: index.title })}
            </Text>
          ) : null}
        </Pressable>
      )}
      <LoadingLine />
    </Card>
  );
}
