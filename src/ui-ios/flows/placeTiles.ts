/**
 * Installed places areas (Bramble's 1° tiles) as Knowledge shows them:
 * grouped by the city they were downloaded for ("I'm going to Rome"), else
 * one per tile named by its corner ("41°N 12°E"). The tile id never shows.
 */
import type { CatalogModel } from "../../models/manifest";
import { tileBbox } from "../../rag/poiRegions";

export interface InstalledTile {
  tileId: string;
  entry: CatalogModel;
  /** On disk. */
  bytes: number;
  /** From the tile index; absent for a file the index doesn't list. */
  pois?: number;
  /** The index lists a newer file for this tile; the installed one keeps working. */
  updateAvailable?: boolean;
}

export interface PlaceArea {
  key: string;
  /** The city the tiles were downloaded for; absent for a tile that came in as a file. */
  city?: string;
  /** "41°N 12°E" when there is no city. */
  corner?: string;
  tiles: InstalledTile[];
  bytes: number;
  /** Sum of the tiles' places, when every tile's count is known. */
  pois?: number;
  updateAvailable: boolean;
}

/** "t-N41E012" from a tile's catalog entry (id "poi-t-N41E012"); null for anything else. */
export function tileIdOf(entry: Pick<CatalogModel, "id">): string | null {
  const id = entry.id.replace(/^poi-/, "");
  return tileBbox(id) ? id : null;
}

/** South-west corner of a tile, as a reader writes it: "41°N 12°E", "34°S 58°W". */
export function tileCorner(tileId: string): string {
  const b = tileBbox(tileId);
  if (!b) return tileId;
  const [lat, lon] = b;
  return `${Math.abs(lat)}°${lat >= 0 ? "N" : "S"} ${Math.abs(lon)}°${lon >= 0 ? "E" : "W"}`;
}

/** City areas first (by name), then unnamed tiles (north to south, west to east). */
export function groupPlaceAreas(tiles: InstalledTile[], names: Record<string, string>): PlaceArea[] {
  const byCity = new Map<string, InstalledTile[]>();
  const loose: InstalledTile[] = [];
  for (const tile of tiles) {
    const city = names[tile.tileId];
    if (city) byCity.set(city, [...(byCity.get(city) ?? []), tile]);
    else loose.push(tile);
  }
  const area = (key: string, group: InstalledTile[], name: { city?: string; corner?: string }): PlaceArea => ({
    key,
    ...name,
    tiles: group,
    bytes: group.reduce((n, t) => n + t.bytes, 0),
    pois: group.every((t) => t.pois != null) ? group.reduce((n, t) => n + (t.pois ?? 0), 0) : undefined,
    updateAvailable: group.some((t) => t.updateAvailable),
  });
  const cities = [...byCity.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([city, group]) => area(`city:${city}`, group, { city }));
  const corners = loose
    .map((t) => ({ t, b: tileBbox(t.tileId)! }))
    .sort((x, y) => y.b[0] - x.b[0] || x.b[1] - y.b[1])
    .map(({ t }) => area(t.tileId, [t], { corner: tileCorner(t.tileId) }));
  return [...cities, ...corners];
}
