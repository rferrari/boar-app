/**
 * Shared catalog state for Models, Knowledge and Setup: what is on disk,
 * what is downloading, which model fills which role, and the device limits.
 * Each screen renders rows from this instead of keeping its own copy.
 */
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import * as FileSystem from "expo-file-system/legacy";
import { getDeviceTotalRamBytes } from "ram-monitor";
import { AssetStatus, ModelManager } from "../../models/ModelManager";
import { CatalogModel, MODEL_CATALOG } from "../../models/manifest";
import { getActiveModelId, setActiveModelId } from "../../models/settings";
import { listDiscoveredModels, removeDiscoveredModel } from "../../models/discoveredModels";
import { DownloadState, getDownloadState, importAssetFile, startDownload, subscribeDownloads } from "../../services/downloadManager";
import { importBatch } from "../../services/importBatch";
import * as DocumentPicker from "expo-document-picker";
import { AssetIntegrityError, isAbortError } from "../../models/integrity";
import { llamaEngine } from "../../inference/LlamaEngine";
import { networkAllowed } from "../../config/variant";
import { forgetTileNames, readRam, installedPlaceTiles, largeModelConfirmedIds, loadCrashedIds, missingRequirementsOf, placeTileNames, poiCatalogEntry, poiRegions, removePackIndex, topicPacks, worldPlacesEntry } from "./adapters";
import { groupPlaceAreas, tileIdOf, type PlaceArea } from "./placeTiles";
import type { MemoryFit } from "../../inference/memoryFit";
import { mayCloseApp, ModelRole, modelRowView, RowView } from "./modelRowState";
import { answerModelChoices } from "./packages";
import { fitForSnapshot, wontFitHere } from "./fit";
import { FileImport, importFor } from "./fileImport";
export type { FileImport };

export const modelManager = new ModelManager();

/** One picked file on its way in (offline build, or "import a file"). */

/** Downloadable only with network and a published URL; everything else comes in as a file. */
export function canDownload(model: Pick<CatalogModel, "sourceUrl">): boolean {
  return networkAllowed() && !!model.sourceUrl;
}

export interface CatalogState {
  loaded: boolean;
  discovered: CatalogModel[];
  /** Places tiles on the phone, grouped by the city they were downloaded for. */
  placeAreas: PlaceArea[];
  statuses: Record<string, AssetStatus>;
  activeLlmId?: string;
  activeEmbeddingId?: string;
  deviceRamBytes: number;
  freeBytes: number;
  usedBytes: number;
  /** Id of the model being loaded after "Use". */
  loadingId: string | null;
  loadErrors: Record<string, string>;
  view: (model: CatalogModel) => RowView;
  fit: (model: CatalogModel) => MemoryFit | undefined;
  refresh: () => Promise<void>;
  download: (model: CatalogModel) => Promise<void>;
  remove: (model: CatalogModel) => Promise<void>;
  use: (model: CatalogModel) => Promise<boolean>;
  imports: FileImport[];
  /** Opens the system file picker and imports each chosen file. A cancelled picker does nothing. */
  importFiles: () => Promise<void>;
  /** The file a row asked for, while it matters: being checked, refused, or matched to another item. */
  importFor: (id: string) => FileImport | undefined;
  /** Downloads what can be downloaded; opens the file picker when any item must be imported. */
  install: (models: CatalogModel[]) => Promise<void>;
  /** Stops the import in progress; the cancelled file leaves the list without an error. */
  cancelImports: () => void;
}

/** The compact answer model's size, for the "may close the app" check: the manifest is fixed. */
const COMPACT_SIZE_BYTES = answerModelChoices(MODEL_CATALOG).compact?.sizeBytes;

/** With no saved choice: the manifest's standard answer model, and the required search model. */
function defaultId(kind: "llm" | "embedding"): string | undefined {
  if (kind === "llm") return answerModelChoices(MODEL_CATALOG).default?.id;
  return MODEL_CATALOG.find((m) => m.kind === kind && m.required)?.id;
}

/**
 * One asset's live download state. Re-renders only the component that asks (a CatalogRow's bar),
 * on that asset's events, which downloadManager caps at 5 Hz.
 */
export function useLiveDownload(assetId: string): DownloadState | undefined {
  const subscribe = useCallback(
    (onChange: () => void) => subscribeDownloads((e) => (!e.assetId || e.assetId === assetId) && onChange()),
    [assetId]
  );
  return useSyncExternalStore(subscribe, () => getDownloadState(assetId));
}

export interface CatalogOptions {
  /**
   * Re-render the host on every progress event (5 Hz), not only on phase changes. Only a screen
   * that shows progress outside the rows needs it (the setup wizard's total and ETA); rows follow
   * their own bar through useLiveDownload (perf audit #2/#9).
   */
  liveProgress?: boolean;
}

export function useCatalog({ liveProgress = false }: CatalogOptions = {}): CatalogState {
  const [loaded, setLoaded] = useState(false);
  const [discovered, setDiscovered] = useState<CatalogModel[]>([]);
  const [placeAreas, setPlaceAreas] = useState<PlaceArea[]>([]);
  const [statuses, setStatuses] = useState<Record<string, AssetStatus>>({});
  const [activeLlmId, setActiveLlmId] = useState<string>();
  const [activeEmbeddingId, setActiveEmbeddingId] = useState<string>();
  const [freeBytes, setFreeBytes] = useState(0);
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [loadErrors, setLoadErrors] = useState<Record<string, string>>({});
  const [crashedIds, setCrashedIds] = useState<string[]>([]);
  const [confirmedIds, setConfirmedIds] = useState<string[]>([]);
  const [tick, setTick] = useState(0);
  const deviceRamBytes = useMemo(() => {
    try {
      return getDeviceTotalRamBytes();
    } catch {
      return 0;
    }
  }, []);

  // Read everything first (in parallel), then set it all in one synchronous block: one render per
  // refresh instead of one per await (~7 on every open and after each download/remove/use, audit #15).
  const refresh = useCallback(async () => {
    const [found, tiles] = await Promise.all([listDiscoveredModels(), installedPlaceTiles()]);
    const extra = [...found, ...poiRegions().map(poiCatalogEntry), worldPlacesEntry(), ...topicPacks().map((p) => p.entry), ...tiles.map((t) => t.entry)];
    const [catalogStatuses, extraStatuses, tileNames, llmId, embeddingId, [crashed, confirmed], free] = await Promise.all([
      modelManager.statusAll(),
      Promise.all(extra.map((m) => modelManager.statusOf(m))),
      placeTileNames(),
      getActiveModelId("llm"),
      getActiveModelId("embedding"),
      // In order: reading the crashes runs a once-per-process migration that also withdraws confirmations.
      loadCrashedIds().then(async (c) => [c, await largeModelConfirmedIds()] as const),
      FileSystem.getFreeDiskStorageAsync().catch(() => 0),
    ]);
    const byId = Object.fromEntries([...catalogStatuses, ...extraStatuses].map((s) => [s.asset.id, s]));
    // A tile the index doesn't list (no sha256) has nothing newer to update to.
    const withUpdates = tiles.map((t) => ({ ...t, updateAvailable: !!t.entry.sha256 && !!byId[t.entry.id]?.updateAvailable }));
    setDiscovered(found);
    setPlaceAreas(groupPlaceAreas(withUpdates, tileNames));
    setStatuses(byId);
    setActiveLlmId(llmId ?? defaultId("llm"));
    setActiveEmbeddingId(embeddingId ?? defaultId("embedding"));
    setCrashedIds(crashed);
    setConfirmedIds(confirmed);
    setFreeBytes(free);
    setLoaded(true);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);
  useEffect(() => subscribeDownloads((e) => (liveProgress || !e.progressOnly) && setTick((n) => n + 1)), [liveProgress]);

  // One RAM snapshot when something that matters changed (a refresh, a download phase), not three
  // native calls per fit and several fits per row on every render (perf audit #3).
  const ram = useMemo(() => readRam(), [statuses, tick]);
  const fit = useMemo(() => {
    const cache = new Map<string, MemoryFit | undefined>();
    return (model: CatalogModel) => {
      if (!cache.has(model.id)) cache.set(model.id, fitForSnapshot(model, ram));
      return cache.get(model.id);
    };
  }, [ram]);

  const view = useMemo(() => {
    // One view per model until its inputs change: ModelsScreen asks 2-3 times per row while grouping.
    const cache = new Map<string, RowView>();
    return (model: CatalogModel) => {
      const hit = cache.get(model.id);
      if (hit) return hit;
      const status = statuses[model.id];
      const roles: ModelRole[] = [];
      if (model.id === activeLlmId) roles.push("answer");
      if (model.id === activeEmbeddingId) roles.push("search");
      const v = modelRowView({
        present: status?.present ?? false,
        checksumOk: status?.checksumOk,
        download: getDownloadState(model.id),
        roles: model.kind === "corpus" ? [] : roles,
        loading: loadingId === model.id,
        loadError: loadErrors[model.id] ?? null,
        fit: fit(model)?.verdict,
        mayCloseApp: mayCloseApp(model, COMPACT_SIZE_BYTES, deviceRamBytes),
        loadCrashed: crashedIds.includes(model.id),
        largeConfirmed: confirmedIds.includes(model.id),
        wontFit: wontFitHere(model, fit(model)),
      });
      cache.set(model.id, v);
      return v;
    };
    // getDownloadState reads module state: tick changes on each download phase change (and on each
    // progress event with liveProgress), so the cache never outlives the state it read.
  }, [statuses, activeLlmId, activeEmbeddingId, loadingId, loadErrors, deviceRamBytes, crashedIds, confirmedIds, fit, tick]);

  const download = useCallback(
    async (model: CatalogModel) => {
      setLoadErrors(({ [model.id]: _, ...rest }) => rest);
      await startDownload(model);
      await refresh();
    },
    [refresh]
  );

  const remove = useCallback(
    async (model: CatalogModel) => {
      await removePackIndex(model);
      await modelManager.deleteModel(model);
      if (model.id.startsWith("hf-")) await removeDiscoveredModel(model.id);
      const tileId = tileIdOf(model);
      if (tileId) await forgetTileNames([tileId]);
      await refresh();
    },
    [refresh]
  );

  /** Loads first and saves after: an OOM kill mid-load must not leave the model active. */
  const use = useCallback(
    async (model: CatalogModel) => {
      if (loadingId) return false;
      if (model.kind !== "llm") {
        await setActiveModelId(model.kind, model.id);
        await refresh();
        return true;
      }
      setLoadingId(model.id);
      try {
        await llamaEngine.load(model.filename);
        await setActiveModelId("llm", model.id);
        setLoadErrors(({ [model.id]: _, ...rest }) => rest);
        return true;
      } catch (e: any) {
        setLoadErrors((prev) => ({ ...prev, [model.id]: e?.message ?? String(e) }));
        return false;
      } finally {
        setLoadingId(null);
        await refresh();
      }
    },
    [loadingId, refresh]
  );

  const [imports, setImports] = useState<FileImport[]>([]);
  const importAbort = useRef<AbortController | null>(null);
  const cancelImports = useCallback(() => importAbort.current?.abort(), []);
  const pickAndImport = useCallback(async (forIds?: string[]) => {
    const picked = await DocumentPicker.getDocumentAsync({ multiple: true, copyToCacheDirectory: false, type: "*/*" });
    if (picked.canceled || picked.assets.length === 0) return;
    const files = picked.assets;
    const patch = (name: string, next: Partial<FileImport>) =>
      setImports((prev) => prev.map((f) => (f.name === name ? { ...f, ...next } : f)));
    setImports((prev) => [
      ...prev.filter((f) => !files.some((p) => p.name === f.name)),
      ...files.map((f) => ({ name: f.name, status: "importing" as const, progress: 0, forIds, sizeBytes: f.size ?? undefined })),
    ]);
    const controller = new AbortController();
    importAbort.current = controller;
    // One at a time (each file is hashed in full), the gazetteer first; a file refused as unknown is checked
    // again once another file of the pick installed (a tile before the gazetteer that lists it).
    await importBatch(
      files,
      (file) =>
        importAssetFile(
          file.uri,
          (done, total) => patch(file.name, { progress: total > 0 ? done / total : 0 }),
          controller.signal
        ),
      {
        isUnknown: (e) => e instanceof AssetIntegrityError && e.kind === "unknown-file",
        aborted: () => controller.signal.aborted,
        onResult: async (file, r) => {
          if (r.ok) {
            // A places pack imported without the city index still loads, but names can't resolve (Ledger PL-1).
            const missing = await missingRequirementsOf(r.asset);
            patch(file.name, { status: "verified", progress: 1, assetId: r.asset.id, missing: missing.map((m) => ({ label: m.label, sizeBytes: m.sizeBytes })) });
          } else if (isAbortError(r.error)) {
            // Cancelled by the user: the file just leaves the list.
            setImports((prev) => prev.filter((f) => f.name !== file.name));
          } else {
            const e: any = r.error;
            patch(file.name, {
              status: "failed",
              errorKind: e instanceof AssetIntegrityError ? e.kind : "unknown",
              message: e?.message ?? String(e),
            });
          }
        },
      }
    );
    // Cancelled: the files not reached leave the list.
    if (controller.signal.aborted) setImports((prev) => prev.filter((f) => !(files.some((p) => p.name === f.name) && f.status === "importing")));
    importAbort.current = null;
    await refresh();
  }, [refresh]);
  // Bound to buttons as onPress: never let the press event through as forIds.
  const importFiles = useCallback(() => pickAndImport(), [pickAndImport]);

  const install = useCallback(
    async (models: CatalogModel[]) => {
      const missing = models.filter((m) => !statuses[m.id]?.present);
      const toImport = missing.filter((m) => !canDownload(m));
      await Promise.all(missing.filter(canDownload).map((m) => download(m)));
      if (toImport.length > 0) await pickAndImport(toImport.map((m) => m.id));
    },
    [statuses, download, pickAndImport]
  );

  const usedBytes = Object.values(statuses).reduce((sum, s) => sum + (s.present ? s.sizeOnDiskBytes : 0), 0);

  return {
    loaded,
    discovered,
    placeAreas,
    statuses,
    activeLlmId,
    activeEmbeddingId,
    deviceRamBytes,
    freeBytes,
    usedBytes,
    loadingId,
    loadErrors,
    view,
    fit,
    refresh,
    download,
    remove,
    use,
    imports,
    importFiles,
    importFor: (id: string) => importFor(imports, id),
    install,
    cancelImports,
  };
}
