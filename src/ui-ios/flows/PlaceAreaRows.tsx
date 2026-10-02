import React, { useState } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { Button, ListRow, Sheet, Text, useToast } from "../components";
import { useTokens } from "../theme";
import { formatBytes, formatCount } from "./format";
import type { PlaceArea } from "./placeTiles";
import type { CatalogState } from "./useCatalog";

/**
 * Knowledge › Places: the city areas on this phone (downloaded from "I'm
 * travelling to…" or imported as files), each removable, and the way to
 * import a places file.
 */
export function PlaceAreaRows({ catalog }: { catalog: CatalogState }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const tokens = useTokens();
  const toast = useToast();
  const [toRemove, setToRemove] = useState<PlaceArea | null>(null);
  const [removingKey, setRemovingKey] = useState<string | null>(null);

  const nameOf = (a: PlaceArea) => (a.city ? t("flows.travel.areaOf", { city: a.city }) : t("flows.places.cornerArea", { corner: a.corner }));

  const remove = async (a: PlaceArea) => {
    setToRemove(null);
    setRemovingKey(a.key);
    try {
      for (const tile of a.tiles) await catalog.remove(tile.entry);
      toast({ message: t("flows.places.areaRemoved", { name: nameOf(a) }), tone: "success" });
    } catch (e: any) {
      toast({ message: t("flows.row.error.unknown"), tone: "danger" });
      console.warn("[places] remove failed:", e?.message ?? e);
    } finally {
      setRemovingKey(null);
    }
  };

  return (
    <>
      {catalog.placeAreas.map((a) => (
        <View key={a.key}>
          <ListRow
            icon="map-pin"
            title={nameOf(a)}
            value={formatBytes(a.bytes, lang)}
            subtitle={
              [a.pois != null && t("flows.places.count", { places: formatCount(a.pois, lang) }), a.updateAvailable && t("flows.places.updateAvailable")].filter(Boolean).join(" · ") ||
              undefined
            }
          />
          <View style={{ flexDirection: "row", paddingHorizontal: tokens.space.inset, paddingBottom: tokens.space.md }}>
            <Button
              size="sm"
              variant="ghost"
              tone="danger"
              label={t("flows.row.remove")}
              accessibilityLabel={t("flows.knowledge.removeA11y", { name: nameOf(a) })}
              loading={removingKey === a.key}
              onPress={() => setToRemove(a)}
            />
          </View>
        </View>
      ))}
      <View style={{ padding: tokens.space.base, gap: tokens.space.xs }}>
        <Button size="sm" variant="secondary" icon="file-plus" label={t("flows.places.import")} onPress={catalog.importFiles} />
        <Text variant="footnote" color="secondary">
          {t("flows.places.importHint")}
        </Text>
      </View>
      <Sheet
        visible={toRemove !== null}
        onClose={() => setToRemove(null)}
        title={t("flows.row.removeTitle", { name: toRemove ? nameOf(toRemove) : "" })}
        description={t("flows.row.removeBody", { size: formatBytes(toRemove?.bytes ?? 0, lang) })}
        footer={
          <>
            <Button label={t("common.cancel")} variant="secondary" fullWidth onPress={() => setToRemove(null)} />
            <Button label={t("flows.row.remove")} variant="destructive" fullWidth onPress={() => toRemove && remove(toRemove)} />
          </>
        }
      />
    </>
  );
}
