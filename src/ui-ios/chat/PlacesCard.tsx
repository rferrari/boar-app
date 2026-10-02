import React, { memo, useEffect, useRef, useState } from "react";
import {
  AccessibilityInfo,
  ActivityIndicator,
  findNodeHandle,
  Linking,
  Pressable,
  Text as RNText,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import * as Clipboard from "expo-clipboard";
import { KeyboardController } from "react-native-keyboard-controller";
import { useTranslation } from "react-i18next";
import { Badge, Banner, Button, Card, Chip, EmptyState, Icon, IconText, LineSlot, Sheet, Text, TextAction, TextField, useOpticalLine, useToast } from "../components";
import { installedPoiCities } from "../flows/adapters";
import { icon, useTokens } from "../theme";
import { useMotion } from "../theme/motion";
import type { Place } from "./answerEvents";
import { CityMapOffer } from "../flows/CityMapOffer";
import type { AnswerState, PlacesResult } from "./answerReducer";
import { sameAnswerFields } from "./renderEquality";
import {
  coordinatesText,
  areaCityName,
  cuisineLabels,
  dietLabels,
  formatDistance,
  geoUri,
  filterName,
  deviceClockApplies,
  attributionCredit,
  attributionSpoken,
  showUseLocation,
  openStateAt,
  openStateLabel,
  placesEmptyTitle,
  citySuggestions,
  agoText,
  lastKnownOf,
  type LastKnownPlace,
  placeA11yLabel,
  sourceName,
} from "./placesFormat";

const VISIBLE = 5;

type T = (key: string, opts?: Record<string, unknown>) => string;

function cardTitle(r: PlacesResult, count: number, t: T): string {
  const filter = filterName(r.filters, t);
  return filter ? t("chat.places.titleFiltered", { filter, count }) : t("chat.places.title", { count });
}

function cardSubtitle(r: PlacesResult, locale: string, t: T): string {
  const parts: string[] = [];
  if (r.area.kind === "near") {
    parts.push(r.area.radiusM ? t("chat.places.within", { distance: formatDistance(r.area.radiusM, locale) }) : t("chat.places.nearYou"));
  } else {
    parts.push(t("chat.places.inCity", { city: areaCityName(r.area) }));
  }
  // "Best" is never popularity: say how the list is ordered.
  // A named city lists no distances (the search centres on the city): say what the distance is from (Prism OM-1).
  const city = r.area.kind === "city";
  parts.push(
    t(r.criterion === "distance" ? (city ? "chat.places.byDistanceCity" : "chat.places.byDistance") : city ? "chat.places.byDietCity" : "chat.places.byDiet")
  );
  return parts.join(" · ");
}

/** The device clock, refreshed each minute so open/closed doesn't go stale in a long session. */
/** Ticks every minute only where the list shows the device's local time (audit #31: city lists ticked for nothing). */
function useMinuteClock(enabled: boolean): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    if (!enabled) return;
    setNow(new Date());
    const id = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(id);
  }, [enabled]);
  return now;
}

/** A place row stacks its facts from here (later than LARGE_TEXT_SCALE: name and distance still fit one line). */
const PLACE_ROW_STACK_SCALE = 1.6;

/** Memoized with the place bound inside, so a clock tick or an opened sheet re-renders no row whose time didn't change. */
const PlaceRow = memo(function PlaceRow({ place, now, locale, onOpen }: { place: Place; now: Date | null; locale: string; onOpen: (place: Place) => void }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const { fontScale } = useWindowDimensions();
  const stacked = fontScale >= PLACE_ROW_STACK_SCALE;
  const tags = [...dietLabels(place.diet, tr, place.dietFlag), ...cuisineLabels(place.cuisine, tr)].join(" · ");
  const state = openStateAt(place, now);
  const distance = place.distanceM != null ? formatDistance(place.distanceM, locale) : null;
  return (
    <Pressable
      onPress={() => onOpen(place)}
      accessibilityRole="button"
      accessibilityLabel={placeA11yLabel(place, now, locale, tr)}
      style={({ pressed }) => ({
        minHeight: t.size.touch + t.space.sm,
        // The sheet's compact card padding (12/14).
        paddingHorizontal: t.space.md + t.space.xxs,
        paddingVertical: t.space.sm,
        justifyContent: "center",
        backgroundColor: pressed ? t.color.bg.sunken : undefined,
      })}
    >
      <View style={{ flexDirection: stacked ? "column" : "row", gap: stacked ? t.space.xxs : t.space.md }}>
        <View style={{ flex: stacked ? undefined : 1, gap: t.space.xxs }}>
          <Text variant="cardTitle" numberOfLines={2}>
            {place.name}
          </Text>
          {/* One line of facts (Iris): tags · open/closed · the address when there is no distance to show. */}
          {(tags || state || (!distance && place.address)) && (
            <Text variant="footnote" color="secondary" numberOfLines={2}>
              {tags}
              {tags && state ? " · " : ""}
              {/* Closed stands out at a glance (geo-result-card G5); the words carry it too. */}
              {state && <Text variant="footnote" color={state.open ? "secondary" : "warning"}>{openStateLabel(state, tr)}</Text>}
              {!distance && place.address ? `${tags || state ? " · " : ""}${place.address}` : ""}
            </Text>
          )}
        </View>
        {distance && (
          <Text variant="mono" align={stacked ? "left" : "right"}>
            {distance}
          </Text>
        )}
      </View>
    </Pressable>
  );
});

function PlaceSheet({
  place,
  now,
  locale,
  onClose,
  onOpenSource,
}: {
  place: Place | null;
  now: Date | null;
  locale: string;
  onClose: () => void;
  onOpenSource: (index: number) => void;
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const toast = useToast();
  if (!place) return <Sheet visible={false} onClose={onClose} title="" />;
  const state = openStateAt(place, now);
  const coords = coordinatesText(place);
  const Row = ({ label, value }: { label: string; value: string }) => (
    <View style={{ gap: t.space.xxs }}>
      <Text variant="label" color="secondary">
        {label}
      </Text>
      <Text selectable>{value}</Text>
    </View>
  );
  return (
    <Sheet
      visible
      onClose={onClose}
      title={place.name}
      description={
        place.distanceM != null ? `${formatDistance(place.distanceM, locale)} · ${sourceName(place.source, tr)}` : sourceName(place.source, tr)
      }
      footer={
        <>
          <Button
            label={tr("chat.places.copyCoordinates")}
            variant="secondary"
            icon="copy"
            fullWidth
            onPress={async () => {
              await Clipboard.setStringAsync(coords);
              toast({ message: tr("chat.places.coordinatesCopied"), icon: "check" });
            }}
          />
          <Button
            label={tr("chat.places.openInMaps")}
            icon="map"
            fullWidth
            onPress={() =>
              Linking.openURL(geoUri(place)).catch(() =>
                toast({
                  message: tr("chat.places.noMapsApp"),
                  tone: "danger",
                  // No maps app (e.g. GrapheneOS): the coordinates still work anywhere.
                  actionLabel: tr("chat.places.copyShort"),
                  onAction: async () => {
                    await Clipboard.setStringAsync(coords);
                    toast({ message: tr("chat.places.coordinatesCopied"), icon: "check" });
                  },
                })
              )
            }
          />
        </>
      }
    >
      <View style={{ gap: t.space.base }}>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.xs }}>
          {dietLabels(place.diet, tr, place.dietFlag).map((d) => (
            <Badge key={d} label={d} tone={place.dietFlag === "verify" ? "warning" : "field"} caps={false} />
          ))}
          {cuisineLabels(place.cuisine, tr, 4).map((c) => (
            <Badge key={c} label={c} />
          ))}
        </View>
        {place.address && <Row label={tr("chat.places.address")} value={place.address} />}
        {place.openingHours && (
          <Row
            label={tr("chat.places.hours")}
            value={state ? `${openStateLabel(state, tr)}\n${place.openingHours}` : place.openingHours}
          />
        )}
        {place.phone && <Row label={tr("chat.places.phone")} value={place.phone} />}
        {place.website && <Row label={tr("chat.places.website")} value={place.website} />}
        {place.description && <Row label={tr("chat.places.description")} value={place.description} />}
        <Row label={tr("chat.places.coordinates")} value={coords} />
        {/* The raw id (node/123…) is noise on screen; screen readers still get it for reporting a wrong entry. */}
        <Text variant="caption" color="field" accessibilityLabel={`${sourceName(place.source, tr)}, ${place.id}`}>
          {sourceName(place.source, tr)}
        </Text>
        {place.sourceIndex != null && (
          <TextAction
            label={tr("chat.places.viewPassage")}
            leadingIcon="book"
            onPress={() => {
              onClose();
              onOpenSource(place.sourceIndex!);
            }}
          />
        )}
      </View>
    </Sheet>
  );
}

/** Biggest cities of the map packs installed on this phone (Loom's adapter); [] until read or without packs. */
function usePackCities(): { name: string }[] {
  const [cities, setCities] = useState<{ name: string }[]>([]);
  useEffect(() => {
    let alive = true;
    installedPoiCities(8)
      .then((c) => alive && setCities(c))
      .catch(() => undefined);
    return () => {
      alive = false;
    };
  }, []);
  return cities;
}

/** One-tap cities the offline map covers (Prism L-2 / P-5). Nothing when no pack is installed. */
function CityChips({ exclude, onPick }: { exclude?: string; onPick: (city: string) => void }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const names = citySuggestions(usePackCities(), exclude);
  if (names.length === 0) return null;
  return (
    <View style={{ gap: t.space.sm }}>
      <Text variant="label" color="secondary" header>
        {tr("chat.places.coveredCities")}
      </Text>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
        {names.map((name) => (
          <Chip
            key={name}
            label={name}
            icon="map-pin"
            accessibilityHint={tr("chat.places.searchCityHint", { city: name })}
            onPress={() => {
              KeyboardController.dismiss();
              onPick(name);
            }}
          />
        ))}
      </View>
    </View>
  );
}

/**
 * The phone's last position is too old to list "near me" (Boar): say where and
 * when it was, and let the user use that city or choose another. Never lists
 * the old city on its own.
 */
function StaleLocationPrompt({
  last,
  onCity,
  onChoose,
  focus,
}: {
  last: LastKnownPlace;
  onCity: (city: string) => void;
  onChoose: () => void;
  focus?: boolean;
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const titleRef = useRef<RNText>(null);
  useEffect(() => {
    if (!focus) return;
    const id = setTimeout(() => {
      const tag = findNodeHandle(titleRef.current);
      if (tag) AccessibilityInfo.setAccessibilityFocus(tag);
    }, 300);
    return () => clearTimeout(id);
    // Once, when the prompt appears.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return (
    <Card radius="card" padding="compact" style={{ gap: t.space.md }}>
      <View style={{ gap: t.space.xs }}>
        <Text ref={titleRef} variant="cardTitle" header>
          {tr("chat.places.staleTitle", { city: last.city, ago: agoText(last.ageS, tr) })}
        </Text>
        <Text variant="footnote" color="secondary">
          {tr("chat.places.staleBody")}
        </Text>
      </View>
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
        {/* One accent per screen: send keeps the ember; the prompt's own actions are secondary (Prism CH-13). */}
        <Button label={tr("chat.places.useCity", { city: last.city })} variant="secondary" icon="map-pin" onPress={() => onCity(last.city)} />
        <Button label={tr("chat.places.chooseCity")} variant="secondary" icon="search" onPress={onChoose} />
      </View>
    </Card>
  );
}

/** Stale position first; "Choose a city" turns it into the city prompt. */
function NeedsPlace({
  last,
  locationStatus,
  onCity,
  onUseLocation,
  focus,
}: {
  last: LastKnownPlace | null;
  locationStatus?: string;
  onCity: (city: string) => void;
  onUseLocation?: () => void;
  focus?: boolean;
}) {
  const [choosing, setChoosing] = useState(false);
  if (last && !choosing) return <StaleLocationPrompt last={last} onCity={onCity} onChoose={() => setChoosing(true)} focus={focus} />;
  return <CityPrompt locationStatus={locationStatus} onCity={onCity} onUseLocation={onUseLocation} focus={focus || choosing} />;
}

/** "Which city?" when there's no position and no city in the question. */
function CityPrompt({
  locationStatus,
  onCity,
  onUseLocation,
  focus,
  locating,
}: {
  locationStatus?: string;
  onCity: (city: string) => void;
  onUseLocation?: () => void;
  /** Still waiting for the GPS: say so with a spinner, keep the city field ready (no keyboard pops up). */
  locating?: boolean;
  /** Just asked: move the keyboard to the city field (so the city isn't typed into the composer as a new question) and the reader to the title. */
  focus?: boolean;
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [city, setCity] = useState("");
  const inputRef = useRef<TextInput>(null);
  const titleRef = useRef<RNText>(null);
  // The spinner on the title's optical line (Baloo rides high on iOS; Prism CH-16).
  const titleLine = useOpticalLine("cardTitle");
  useEffect(() => {
    if (!focus) return;
    inputRef.current?.focus();
    // After the field takes focus, so the reader starts at the question, not the empty field.
    const id = setTimeout(() => {
      const tag = findNodeHandle(titleRef.current);
      if (tag) AccessibilityInfo.setAccessibilityFocus(tag);
    }, 300);
    return () => clearTimeout(id);
    // Once, when the prompt appears.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const submit = () => {
    if (!city.trim()) return;
    // Close the keyboard before this field unmounts (the results replace the prompt): a focused
    // input removed with the keyboard open is a likely trigger of Prism's K-2 (composer left floating).
    KeyboardController.dismiss();
    onCity(city.trim());
  };
  return (
    <Card radius="card" padding="compact" style={{ gap: t.space.md }}>
      {/* A permanent "no" on Android can only be undone in the system settings; offer the way there. */}
      {locationStatus === "denied" && (
        <View style={{ gap: t.space.xs }}>
          <Banner tone="info" icon="map-pin" message={tr("chat.places.locationDenied")} />
          {/* One accent per screen (Iris): the field's focus is the ember here, so this link is secondary. */}
          <TextAction label={tr("chat.places.openSettings")} icon="chevron-right" onPress={() => Linking.openSettings()} />
        </View>
      )}
      {locationStatus === "unavailable" && <Banner tone="info" icon="map-pin" message={tr("chat.places.locationUnavailable")} />}
      {locating ? (
        <View style={{ gap: t.space.xs }}>
          {/* Loading is the accent, not amber (amber is provenance); on the title's first line (Prism CH-16). */}
          <View style={{ flexDirection: "row", alignItems: "flex-start", gap: icon.gap }}>
            <LineSlot line={titleLine}>
              <ActivityIndicator size="small" color={t.color.accent.solid} />
            </LineSlot>
            <Text ref={titleRef} variant="cardTitle" header style={{ flex: 1 }}>
              {tr("chat.places.locating")}
            </Text>
          </View>
          <Text variant="footnote" color="secondary">
            {tr("chat.places.locatingBody")}
          </Text>
        </View>
      ) : (
        <Text ref={titleRef} variant="cardTitle" header>
          {tr("chat.places.whichCity")}
        </Text>
      )}
      {/* The title names the field; no second visible "City" label. */}
      <TextField
        ref={inputRef}
        accessibilityLabel={tr(locating ? "chat.places.typeCity" : "chat.places.whichCity")}
        placeholder={tr("chat.places.cityPlaceholder")}
        value={city}
        onChangeText={setCity}
        onSubmitEditing={submit}
        returnKeyType="search"
        autoCapitalize="words"
      />
      <View style={{ flexDirection: "row", flexWrap: "wrap", gap: t.space.sm }}>
        <Button
          label={tr("chat.places.search")}
          variant="secondary"
          icon="search"
          disabled={!city.trim()}
          accessibilityHint={city.trim() ? undefined : tr("chat.places.searchHint")}
          onPress={submit}
        />
        {!locating && onUseLocation && showUseLocation(true, locationStatus) && (
          <Button label={tr("chat.places.useLocation")} variant="secondary" icon="navigation" onPress={onUseLocation} />
        )}
      </View>
      <CityChips onPick={onCity} />
    </Card>
  );
}

export interface PlacesCardProps {
  answer: AnswerState;
  locale: string;
  onOpenSource: (index: number) => void;
  onCity: (city: string) => void;
  onUseLocation?: () => void;
  onGetMap?: () => void;
  /** The answer was just asked: the city prompt takes focus. */
  focusCity?: boolean;
}

/**
 * The answer to "vegan restaurants in <city> / near me": the places exactly
 * as recorded in the offline map, one dense row each, ordered by the engine
 * with the ordering stated, attribution once at the bottom. No model writes
 * any of it, so no place can be invented.
 */
function PlacesCardView({ answer, locale, onOpenSource, onCity, onUseLocation, onGetMap, focusCity }: PlacesCardProps) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [expanded, setExpanded] = useState(false);
  const m = useMotion();
  const [openPlace, setOpenPlace] = useState<Place | null>(null);
  const [showLicense, setShowLicense] = useState(false);
  const r = answer.places!;
  const clock = useMinuteClock(deviceClockApplies(r.area));
  const credit = attributionCredit(r.attribution, locale, tr);
  // Open/closed needs the place's local time; only a "near" list shares the device's clock.
  const now = deviceClockApplies(r.area) ? clock : null;

  if (r.coverage === "needs_place") {
    return (
      <NeedsPlace
        last={lastKnownOf(r.area)}
        locationStatus={answer.location?.status}
        onCity={onCity}
        onUseLocation={onUseLocation}
        focus={focusCity}
      />
    );
  }
  const emptyTitle = placesEmptyTitle(r, tr);
  if (r.coverage === "no_pack") {
    return (
      <EmptyState
        icon="map"
        title={emptyTitle!}
        body={tr("chat.places.noPackBody")}
        actionLabel={onGetMap ? tr("chat.places.getMap") : undefined}
        onAction={onGetMap}
        // Inside the conversation: send keeps the screen's accent (Prism CH-13).
        actionVariant="secondary"
      />
    );
  }
  if (emptyTitle) {
    return (
      <View style={{ gap: t.space.md }}>
        <EmptyState icon="map" title={emptyTitle} body={tr("chat.places.noneBody")} />
        {/* No map covers the named city: offer the one that does (Tusk a9e1a3c; no_match keeps the list's filters as the reason). */}
        {r.empty === "no_data" && r.area.place?.lat != null && r.area.place.lon != null && (
          <CityMapOffer city={{ name: r.area.place.name, lat: r.area.place.lat, lon: r.area.place.lon }} onGetMap={onGetMap} />
        )}
        {/* What the map does cover, one tap away (Prism P-5). */}
        <CityChips exclude={r.area.place?.name ?? r.area.label} onPick={onCity} />
      </View>
    );
  }

  const shown = expanded ? r.places : r.places.slice(0, VISIBLE);
  const hidden = r.places.length - shown.length;
  const stale = r.area.origin?.ageS != null && r.area.origin.ageS > 600;

  return (
    <View style={{ gap: t.space.sm }}>
      <Card padding="none" style={{ overflow: "hidden" }}>
        <View style={{ paddingHorizontal: t.space.md + t.space.xxs, paddingTop: t.space.md, paddingBottom: t.space.sm, gap: t.space.xxs }}>
          <Text variant="label" color="secondary" header numeric>
            {cardTitle(r, r.places.length, tr)}
          </Text>
          <Text variant="footnote" color="secondary">
            {cardSubtitle(r, locale, tr)}
          </Text>
          {stale && (
            <Text variant="caption" color="secondary">
              {tr("chat.places.staleLocation", { minutes: Math.round(r.area.origin!.ageS! / 60) })}
            </Text>
          )}
        </View>
        <View accessibilityRole="list">
          {shown.map((p) => (
            // Rows inside a card are separated by line.row (the sheet's s2 hairline).
            <View key={p.id} style={{ borderTopWidth: t.size.hairline, borderTopColor: t.color.line.row }}>
              <PlaceRow place={p} now={now} locale={locale} onOpen={setOpenPlace} />
            </View>
          ))}
        </View>
        <View
          style={{
            paddingHorizontal: t.space.md + t.space.xxs,
            paddingVertical: t.space.sm,
            gap: t.space.xs,
            borderTopWidth: t.size.hairline,
            borderTopColor: t.color.line.row,
          }}
        >
          {hidden > 0 && (
            <TextAction
              label={tr("chat.places.showMore", { count: hidden })}
              icon="chevron-down"
              expanded={false}
              // The fold opens in height (DS layout), not in one frame.
              onPress={() => {
                m.animateNextLayout();
                setExpanded(true);
              }}
            />
          )}
          {r.truncated && expanded && (
            <Text variant="caption" color="secondary">
              {tr("chat.places.truncated")}
            </Text>
          )}
          <Pressable
            onPress={() => setShowLicense((s) => !s)}
            accessibilityRole="button"
            accessibilityState={{ expanded: showLicense }}
            accessibilityLabel={attributionSpoken(credit, tr)}
            style={{ minHeight: t.size.touch, justifyContent: "center" }}
          >
            {/* Icon-align: the map icon on the credit's first line (it wraps in PT). */}
            <IconText icon="map" variant="caption" color="field" iconColor={t.color.text.field}>
              {credit}
            </IconText>
          </Pressable>
          {showLicense && (
            <Text variant="caption" color="secondary">
              {r.attribution
                .map((a) => tr("chat.places.licenseLine", { source: sourceName(a.source, tr), license: a.license }))
                .join("\n")}
            </Text>
          )}
        </View>
      </Card>
      <PlaceSheet place={openPlace} now={now} locale={locale} onClose={() => setOpenPlace(null)} onOpenSource={onOpenSource} />
    </View>
  );
}

/**
 * Reads only answer.places and answer.location: memoized on those, the list stays still while a model
 * pass streams under it. Reading another answer field here means adding it to the comparison.
 */
export const PlacesCard = memo(
  PlacesCardView,
  (a, b) =>
    sameAnswerFields(a.answer, b.answer, ["places", "location"]) &&
    a.locale === b.locale &&
    a.onOpenSource === b.onOpenSource &&
    a.onCity === b.onCity &&
    a.onUseLocation === b.onUseLocation &&
    a.onGetMap === b.onGetMap &&
    a.focusCity === b.focusCity
);

/**
 * "Finding your location…" while the engine waits for the GPS (up to ~10 s, Boar GPS-1):
 * the city field, Search and the offline map's areas are usable from the start, so
 * nobody has to wait. The list replaces this as soon as a fix arrives.
 */
export function LocatingPrompt({ onCity }: { onCity: (city: string) => void }) {
  return <CityPrompt locating onCity={onCity} />;
}
