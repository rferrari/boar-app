/**
 * What BOAR is doing in the background right now, for status outside the setup wizard: model and pack
 * downloads (services/downloadManager) and the search index being built (rag/seedCorpus).
 */
import { useEffect, useState } from "react";
import { listDownloadStates, subscribeDownloads } from "../../services/downloadManager";
import { onSeedProgress, type SeedProgress } from "../../rag/seedCorpus";

export interface RunningDownload {
  assetId: string;
  /** 0..1 within the phase. */
  progress: number;
  /** "queued": waiting its turn behind another big download. */
  phase: "queued" | "downloading" | "verifying";
}

export interface Activity {
  downloads: RunningDownload[];
  /** The index being built, or null when there's nothing to index. */
  index: SeedProgress | null;
}

/** The downloads with bytes moving or being checked, in start order. Pure, for tests. */
export function runningDownloads(states: ReturnType<typeof listDownloadStates>): RunningDownload[] {
  return states
    .filter(({ state }) => state.downloading)
    .map(({ assetId, state }) => ({
      assetId,
      progress: state.progress,
      phase: state.phase === "verifying" || state.phase === "queued" ? state.phase : "downloading",
    }));
}

export function useActivity(): Activity {
  const [downloads, setDownloads] = useState<RunningDownload[]>(() => runningDownloads(listDownloadStates()));
  const [index, setIndex] = useState<SeedProgress | null>(null);
  useEffect(() => subscribeDownloads(() => setDownloads(runningDownloads(listDownloadStates()))), []);
  useEffect(() => onSeedProgress((p) => setIndex(p.total > 0 && p.done < p.total ? p : null)), []);
  return { downloads, index };
}
