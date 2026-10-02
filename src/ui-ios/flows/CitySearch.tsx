import React, { useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { Button, ListRow, Text, TextField, useAnnounce } from "../components";
import { useTokens } from "../theme";
import type { CatalogModel } from "../../models/manifest";
import { cityAreaTiles, nameTilesAfter, placesInstall, poiRegions, searchCities, worldPlacesEntry } from "./adapters";
import { City, cityOptions, CityOption } from "./travel";
import { formatBytes, formatCount } from "./format";
import { canDownload, CatalogState } from "./useCatalog";

const AREA_RADIUS_KM = 15;

interface Props {
  catalog: CatalogState;
  /** Setup passes this to add the choice to its install list instead of installing now. */
  onChoose?: (choice: { label: string; assets: CatalogModel[] }) => void;
}

/** "I'm travelling to X": find a city in the offline gazetteer, then install its area or its whole region. */
export function CitySearch({ catalog, onChoose }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const announce = useAnnounce();
  const lang = i18n.language;
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<City[] | null>(null);
  const [city, setCity] = useState<City | null>(null);
  // The tiles around the chosen city; undefined while the tile index is read.
  const [areaTiles, setAreaTiles] = useState<CatalogModel[] | null | undefined>(undefined);
  const seq = useRef(0);
  const gazetteer = worldPlacesEntry();
  const gazetteerReady = !!catalog.statuses[gazetteer.id]?.present;

  // Search as the user types, after a short pause; ignore answers to older queries.
  useEffect(() => {
    if (!gazetteerReady) return;
    const q = query.trim();
    if (q.length < 2) {
      setResults(null);
      return;
    }
    const mine = ++seq.current;
    const timer = setTimeout(async () => {
      const found = await searchCities(q).catch(() => []);
      if (mine !== seq.current) return;
      setResults(found);
      announce(t("flows.travel.resultsAnnounce", { count: found.length }));
    }, 250);
    return () => clearTimeout(timer);
  }, [query, gazetteerReady, announce, t]);

  useEffect(() => {
    setAreaTiles(undefined);
    if (!city) return;
    let live = true;
    cityAreaTiles(city.lat, city.lon, AREA_RADIUS_KM).then((tiles) => live && setAreaTiles(tiles));
    return () => {
      live = false;
    };
  }, [city]);

  if (!gazetteerReady) {
    return (
      <View style={{ gap: tokens.space.sm }}>
        <Text variant="callout" color="secondary">
          {t("flows.travel.needGazetteer", { size: formatBytes(gazetteer.sizeBytes, lang) })}
        </Text>
        <Button
          size="sm"
          variant="secondary"
          icon={canDownload(gazetteer) ? "download" : "file-plus"}
          label={canDownload(gazetteer) ? t("flows.row.download", { size: formatBytes(gazetteer.sizeBytes, lang) }) : t("flows.row.importFile", { size: formatBytes(gazetteer.sizeBytes, lang) })}
          onPress={() => catalog.install([gazetteer])}
        />
      </View>
    );
  }

  // Both options at once, so the list doesn't reorder under a finger when the area arrives.
  const options: CityOption[] | null =
    city && areaTiles !== undefined
      ? cityOptions({
          city,
          regions: poiRegions(),
          areaTiles,
          radiusKm: AREA_RADIUS_KM,
          regionAssets: placesInstall,
          gazetteer,
        })
      : null;

  const choose = (option: CityOption) => {
    const label =
      option.kind === "area"
        ? t("flows.travel.areaOf", { city: city!.name })
        : t("flows.travel.regionOf", { region: lang.startsWith("pt") ? option.region.name.pt : option.region.name.en });
    // Knowledge names the tiles after the city they were chosen for.
    if (option.kind === "area") nameTilesAfter(city!.name, option.assets).catch(() => {});
    if (onChoose) onChoose({ label, assets: option.assets });
    else catalog.install(option.assets);
  };

  return (
    <View style={{ gap: tokens.space.sm }}>
      <TextField
        label={t("flows.travel.searchLabel")}
        placeholder={t("flows.travel.searchPlaceholder")}
        value={query}
        onChangeText={(q) => {
          setQuery(q);
          setCity(null);
        }}
        autoCorrect={false}
        returnKeyType="search"
      />
      {results && results.length === 0 && (
        <Text variant="footnote" color="secondary">
          {t("flows.travel.noCity")}
        </Text>
      )}
      {!city &&
        results?.map((c) => (
          <ListRow
            key={`${c.name}-${c.lat}-${c.lon}`}
            icon="map-pin"
            title={c.country ? `${c.name}, ${c.country}` : c.name}
            subtitle={c.population ? t("flows.travel.population", { count: c.population, value: formatCount(c.population, lang) }) : undefined}
            onPress={() => setCity(c)}
          />
        ))}
      {city && (
        <View style={{ gap: tokens.space.sm }}>
          <Text variant="subhead">{city.country ? `${city.name}, ${city.country}` : city.name}</Text>
          {options === null ? null : options.length === 0 ? (
            <Text variant="footnote" color="secondary">
              {t("flows.travel.noPack")}
            </Text>
          ) : (
            options.map((o) => {
              const title =
                o.kind === "area"
                  ? t("flows.travel.areaOf", { city: city.name })
                  : t("flows.travel.regionOf", { region: lang.startsWith("pt") ? o.region.name.pt : o.region.name.en });
              const installed = o.assets.every((a) => catalog.statuses[a.id]?.present);
              const importOnly = o.assets.some((a) => !canDownload(a));
              return (
                <ListRow
                  key={o.kind}
                  title={title}
                  value={formatBytes(o.bytes, lang)}
                  // The radius reads as detail under the name; the status only says what is true (installed, or file import only).
                  subtitle={[o.kind === "area" && t("flows.travel.areaKm", { km: o.radiusKm }), installed ? t("flows.travel.installed") : importOnly && t("flows.travel.byFile")].filter(Boolean).join(" · ") || undefined}
                  onPress={installed ? undefined : () => choose(o)}
                  accessibilityHint={onChoose ? t("flows.travel.addHint") : undefined}
                />
              );
            })
          )}
          <Button size="sm" variant="ghost" label={t("flows.travel.otherCity")} onPress={() => setCity(null)} />
        </View>
      )}
    </View>
  );
}
