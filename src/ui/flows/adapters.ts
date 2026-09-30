/**
 * The seams where the flow screens meet work still on other branches.
 * Each function has one interim body built on what main has today; swap
 * the body when the branch is integrated, and no screen changes.
 *
 * - Memory fit and pack removal are wired (estimateMemoryFit, removeCorpusPackIndex).
 * - Places: POI_REGIONS, poiCatalogEntries, worldPlacesEntry and
 *   searchPlaces are wired, and so are the 1° tiles (tilesFor, loadTileCatalog). The preparedness pack is wired.
 * - Topic packs (preparedness, crypto) are wired.
 * - Position: modules/offline-location (GPS only, no Google Play Services) is wired.
 */
import type { CatalogModel } from "../../models/manifest";
// Topic packs live in their own module (no native imports) so they can be tested; re-exported for existing callers.
export { topicPacks, packName, type PackSource, type TopicPack } from "./topicPacks";
import { confirmLargeModel as confirmLargeModelSetting, getLargeModelConfirmedIds, getLoadCrashedIds, getPlaceTileNames, setPlaceTileNames } from "../../models/settings";
import { loadGuard } from "../../inference/loadGuard";
import * as Downloads from "../../services/downloadManager";
import * as FileSystem from "expo-file-system/legacy";
import { getAvailableRamBytes, getDeviceTotalRamBytes, getMemoryInfo } from "ram-monitor";
import { availableRamFrom, MemoryFit } from "../../inference/memoryFit";
import { removeCorpusPackIndex } from "../../rag/seedCorpus";
import { fitForSnapshot, type RamSnapshot } from "./fit";
import { topInstalledCities } from "./poi";
import type { PoiCity, PoiRegion } from "./poi";
import type { City } from "./travel";
import { closePoiPack, loadTileCatalog, POI_DIR, searchPlaces, tilesFor } from "../../rag/pois";
import { POI_REGIONS, poiCatalogEntries, tileBbox, tileEntry, worldPlacesEntry, type PoiTile } from "../../rag/poiRegions";
import { tileIdOf, type InstalledTile } from "./placeTiles";
import type { NativePosition } from "../../services/location.pure";
import * as OfflineLocation from "offline-location";
// Importing the module also registers the pack with the asset registry.

/**
 * Total and available RAM now: three synchronous native calls (two are Binder IPCs on Android),
 * so take one snapshot per render and pass it to fitForSnapshot (perf audit #3).
 */
export function readRam(): RamSnapshot | undefined {
  try {
    const totalBytes = getDeviceTotalRamBytes();
    return { totalBytes, availableBytes: availableRamFrom({ totalBytes, rssBytes: getMemoryInfo().rssBytes, availBytes: getAvailableRamBytes() }) };
  } catch {
    return undefined;
  }
}

/** Tusk's estimate against the RAM the OS says is available right now. For several models, readRam once. */
export function fitFor(model: CatalogModel): MemoryFit | undefined {
  return fitForSnapshot(model, readRam());
}

/** Deletes a JSON pack's indexed chunks or closes a sqlite pack, before the file goes. */
export async function removePackIndex(model: CatalogModel): Promise<void> {
  if (model.format === "poi-pack") {
    // Places packs have no chunks in the index; stop reading the file before it goes.
    await closePoiPack(model.filename);
    return;
  }
  await removeCorpusPackIndex(model);
}

export function poiRegions(): PoiRegion[] {
  return POI_REGIONS;
}

/** A region as a catalog entry, so downloads, verification and removal reuse the model rows. */
export function poiCatalogEntry(region: PoiRegion): CatalogModel {
  return poiCatalogEntries([region])[0];
}

/** The world gazetteer every places pack needs to resolve place names. */
export { worldPlacesEntry };

/** A region pack plus the gazetteer it needs, as one install. */
export function placesInstall(region: PoiRegion): CatalogModel[] {
  return [poiCatalogEntry(region), worldPlacesEntry()];
}

export interface DeviceLocationModule {
  getPermissionStatus(): Promise<"granted" | "denied" | "undetermined">;
  requestPermission(): Promise<"granted" | "denied">;
  getLastKnownPosition?(): Promise<NativePosition | null>;
  getCurrentPosition(opts: { timeoutMs?: number; maxAgeMs?: number }): Promise<NativePosition>;
}

/** The GPS-only native module (modules/offline-location), or null in a build without it. */
export function deviceLocation(): DeviceLocationModule | null {
  return OfflineLocation.isOfflineLocationSupported() ? OfflineLocation : null;
}

/** Cities matching what the user typed (prefix, alternate names, most populous first), from the offline gazetteer. */
export async function searchCities(query: string, limit = 8): Promise<City[]> {
  const q = query.trim();
  if (!q) return [];
  return searchPlaces(q, limit);
}

/**
 * The places tiles covering a city (Bramble's tilesFor): [] when the tile
 * index lists none there, null when the index can't be read (the city then
 * offers its region only).
 */
export async function cityAreaTiles(lat: number, lon: number, radiusKm: number): Promise<CatalogModel[] | null> {
  try {
    return await tilesFor(lat, lon, radiusKm);
  } catch (e: any) {
    console.warn("[places] tile index unreadable:", e?.message ?? e);
    return null;
  }
}

/**
 * Tiles on this phone (poi/t-*.sqlite), with their index entry when the index
 * lists them. A tile the index doesn't list (an older file, or no gazetteer)
 * still shows, sized as it is on disk, so it can be removed.
 */
export async function installedPlaceTiles(): Promise<InstalledTile[]> {
  const dir = `${FileSystem.documentDirectory}${POI_DIR}`;
  const files = await FileSystem.readDirectoryAsync(dir).catch(() => [] as string[]);
  const ids = files.filter((f) => f.endsWith(".sqlite")).map((f) => f.slice(0, -".sqlite".length)).filter((id) => tileBbox(id));
  if (ids.length === 0) return [];
  const index = await loadTileCatalog().catch(() => new Map<string, PoiTile>());
  return Promise.all(
    ids.map(async (id) => {
      const info = await FileSystem.getInfoAsync(`${dir}${id}.sqlite`).catch(() => null);
      const bytes = info?.exists ? info.size ?? 0 : 0;
      const known = index.get(id);
      const entry = tileEntry(known ?? { id, sizeBytes: bytes, sha256: "", pois: 0, vegan: 0, osmDate: "" });
      return { tileId: id, entry, bytes, pois: known?.pois };
    })
  );
}

/** Which city each installed tile was downloaded for. */
export const placeTileNames = getPlaceTileNames;

/** Remembers the city a set of tiles is for, to name them in Knowledge (the gazetteer and other assets are skipped). */
export async function nameTilesAfter(city: string, assets: CatalogModel[]): Promise<void> {
  const ids = assets.map(tileIdOf).filter((id): id is string => !!id);
  if (ids.length > 0) await setPlaceTileNames(Object.fromEntries(ids.map((id) => [id, city])));
}

/** Forgets the names of removed tiles. */
export async function forgetTileNames(tileIds: string[]): Promise<void> {
  if (tileIds.length > 0) await setPlaceTileNames(Object.fromEntries(tileIds.map((id) => [id, null])));
}

/**
 * The biggest cities of the region packs installed on this phone, for the
 * chat's "Which city?" chips. A pack counts as installed when its catalog
 * file is on disk; no pack, or no readable storage, gives [].
 */
export async function installedPoiCities(limit = 6): Promise<PoiCity[]> {
  const installed = new Set<string>();
  await Promise.all(
    POI_REGIONS.map(async (r) => {
      const info = await FileSystem.getInfoAsync(`${FileSystem.documentDirectory}${r.filename}`).catch(() => null);
      if (info?.exists) installed.add(r.id);
    })
  );
  return topInstalledCities(POI_REGIONS, installed, limit);
}

/** Models whose last load killed the app on this phone ("Didn't open here"; Tusk, CR-2). */
export async function loadCrashedIds(): Promise<string[]> {
  // A crash is recorded when the load guard first checks its marker (the chat's banner or the next load).
  // Models may read before either, and would see nothing (Prism CR-4): check first, it runs once per process.
  await loadGuard.ensureChecked().catch(() => {});
  return getLoadCrashedIds();
}

/** Models the user chose to run anyway on a low-RAM phone. */
export async function largeModelConfirmedIds(): Promise<string[]> {
  return getLargeModelConfirmedIds();
}

/** "Use anyway": lets the engine load this model on a low-RAM phone. */
export async function confirmLargeModel(id: string): Promise<void> {
  await confirmLargeModelSetting(id);
}

/**
 * What an asset still needs on the phone (Ledger's missingRequirements, feat/trust-offline 8dfca20:
 * a places pack requires the world-places city index). Feature-detected, like the low-RAM settings:
 * until that commit is integrated it reports nothing missing.
 */
type RequirementsApi = { missingRequirements?: (asset: CatalogModel) => Promise<CatalogModel[]> };
export async function missingRequirementsOf(asset: CatalogModel): Promise<CatalogModel[]> {
  const api = Downloads as unknown as RequirementsApi;
  return (await api.missingRequirements?.(asset).catch(() => [])) ?? [];
}
