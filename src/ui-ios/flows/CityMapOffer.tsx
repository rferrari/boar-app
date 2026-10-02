import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { Button, Progress, Text, useAnnounce } from "../components";
import { useTokens } from "../theme";
import type { CatalogModel } from "../../models/manifest";
import { getDownloadState, startDownload, subscribeDownloads } from "../../services/downloadManager";
import { cityAreaTiles, nameTilesAfter } from "./adapters";
import { formatBytes } from "./format";
import { canDownload } from "./useCatalog";

const AREA_RADIUS_KM = 15;

interface Props {
  city: { name: string; lat: number; lon: number };
  /** Opens Knowledge, where a places file is imported (offline build, or tiles not published yet). */
  onGetMap?: () => void;
}

/**
 * A places answer with no map for the city (empty "no_data", Tusk a9e1a3c):
 * the map covering it, one tap away, "Download Rome (12 MB)". Nothing when
 * the tile index lists no tile there.
 */
export function CityMapOffer({ city, onGetMap }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const announce = useAnnounce();
  const [tiles, setTiles] = useState<CatalogModel[] | null>(null);
  const [started, setStarted] = useState(false);
  const [, setTick] = useState(0);

  useEffect(() => {
    let live = true;
    cityAreaTiles(city.lat, city.lon, AREA_RADIUS_KM).then((found) => live && setTiles(found));
    return () => {
      live = false;
    };
  }, [city.lat, city.lon]);
  useEffect(() => subscribeDownloads(() => setTick((n) => n + 1)), []);

  const states = (tiles ?? []).map((tile) => getDownloadState(tile.id));
  const done = started && states.length > 0 && states.every((s) => s?.phase === "verified");
  const failed = started && states.some((s) => s?.phase === "error");
  useEffect(() => {
    if (done) announce(t("flows.places.cityReady", { city: city.name }));
  }, [done, announce, t, city.name]);

  if (!tiles || tiles.length === 0) return null;
  const bytes = tiles.reduce((n, tile) => n + tile.sizeBytes, 0);

  if (!tiles.every(canDownload)) {
    return onGetMap ? <Button size="sm" variant="secondary" icon="file-plus" label={t("flows.places.import")} onPress={onGetMap} /> : null;
  }

  const download = () => {
    setStarted(true);
    nameTilesAfter(city.name, tiles).catch(() => {});
    // A retry only fetches what didn't arrive.
    for (const tile of tiles) if (getDownloadState(tile.id)?.phase !== "verified") startDownload(tile).catch(() => {});
  };

  if (done) {
    return (
      <Text variant="footnote" color="secondary">
        {t("flows.places.cityReady", { city: city.name })}
      </Text>
    );
  }
  if (started && !failed) {
    // Progress is per phase: a tile being hashed has all its bytes.
    const arrived = (s: (typeof states)[number]) => (s?.phase === "verified" || s?.phase === "verifying" ? 1 : s?.progress ?? 0);
    const written = states.reduce((n, s, i) => n + arrived(s) * tiles[i].sizeBytes, 0);
    const pct = Math.round((written / bytes) * 100);
    return (
      <Progress label={t("flows.places.gettingCity", { city: city.name })} value={written / bytes} valueText={`${pct}%`} />
    );
  }
  return (
    <View style={{ gap: tokens.space.xs }}>
      {failed && (
        <Text variant="footnote" color="danger">
          {t("flows.row.error.unknown")}
        </Text>
      )}
      {/* Secondary: a primary here would be a second ember beside the composer (Prism FL-9); the city goes on the support line. */}
      <Button size="sm" variant="secondary" icon="download" label={t("flows.places.getCity")} onPress={download} />
      <Text variant="footnote" color="secondary">
        {t("flows.places.getCityHint", { city: city.name, size: formatBytes(bytes, i18n.language) })}
      </Text>
    </View>
  );
}
