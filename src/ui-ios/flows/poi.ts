/**
 * Offline places (P1): which region pack to suggest, and how to summarize
 * it. The region catalog and the point/time-zone lookups are Bramble's
 * (src/rag/poiRegions.ts); this file only decides what the UI shows.
 */

import { PoiRegion, regionForPoint, regionsForTimeZone } from "../../rag/poiRegions";

export type { PoiRegion };

export type SuggestionReason = "location" | "timezone";

export interface RegionSuggestion {
  region: PoiRegion;
  reason: SuggestionReason;
}

/**
 * A measured position beats the time zone. Among regions sharing the time
 * zone, the one with the most places is the likelier home.
 */
export function suggestRegion(
  regions: PoiRegion[],
  hints: { timeZone?: string; point?: { lat: number; lon: number } }
): RegionSuggestion | null {
  if (hints.point) {
    const byPoint = regionForPoint(hints.point.lat, hints.point.lon, regions);
    if (byPoint) return { region: byPoint, reason: "location" };
  }
  const byZone = (hints.timeZone ? regionsForTimeZone(hints.timeZone, regions) : []).sort((a, b) => b.poiCount - a.poiCount)[0];
  return byZone ? { region: byZone, reason: "timezone" } : null;
}

/** The first `n` of the region's listed places and how many more the pack lists. */
export function citySummary(region: Pick<PoiRegion, "cities">, n = 3): { names: string[]; more: number } {
  const names = region.cities.slice(0, n).map((c) => c.name);
  return { names, more: Math.max(0, region.cities.length - names.length) };
}

export function deviceTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return undefined;
  }
}

/** A city to offer as a chip, with the region pack it comes from. */
export interface PoiCity {
  name: string;
  /** The region pack's id (POI_REGIONS[].id). */
  region: string;
}

/** A place this close to a bigger one in the same pack is one of its districts (Kreuzberg in Berlin). */
export const DISTRICT_RADIUS_KM = 10;

function km(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLon = (b.lon - a.lon) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLon / 2) ** 2;
  return 12_742 * Math.asin(Math.sqrt(h));
}

/**
 * Cities to offer as chips from the installed region packs. Each pack lists
 * its places biggest first (GeoNames), so its first entry is the main city.
 * Districts (within DISTRICT_RADIUS_KM of a bigger place in the same pack) are
 * left out, and the slots go round-robin: every installed region's main city
 * first, then the next ones, so a dense region can't take them all (Prism
 * L-4: six Berlin districts and no Qujing). Each name once.
 */
export function topInstalledCities(regions: Pick<PoiRegion, "id" | "cities">[], installed: Set<string>, limit = 6): PoiCity[] {
  const perRegion = regions
    .filter((r) => installed.has(r.id))
    .map((r) => {
      const kept: PoiRegion["cities"] = [];
      for (const c of r.cities) if (!kept.some((k) => km(k, c) < DISTRICT_RADIUS_KM)) kept.push(c);
      return kept.map((c) => ({ name: c.name, region: r.id, pois: c.pois }));
    });
  const seen = new Set<string>();
  const out: PoiCity[] = [];
  for (let round = 0; out.length < limit && perRegion.some((l) => l.length > round); round++) {
    // Within a round, regions with more food places around that city go first.
    const picks = perRegion.map((l) => l[round]).filter(Boolean).sort((a, b) => b.pois - a.pois);
    for (const c of picks) {
      if (out.length >= limit) break;
      if (seen.has(c.name)) continue;
      seen.add(c.name);
      out.push({ name: c.name, region: c.region });
    }
  }
  return out;
}
