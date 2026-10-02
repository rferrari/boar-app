import { describe, it, expect, vi } from "vitest";

vi.mock("../../services/downloadManager", () => ({ listDownloadStates: () => [], subscribeDownloads: () => () => {} }));
vi.mock("../../rag/seedCorpus", () => ({ onSeedProgress: () => () => {} }));

import { runningDownloads } from "./useActivity";

describe("runningDownloads", () => {
  it("keeps only downloads with bytes moving or being checked", () => {
    expect(
      runningDownloads([
        { assetId: "a", state: { downloading: true, progress: 0.4, error: null, phase: "downloading" } },
        { assetId: "b", state: { downloading: false, progress: 1, error: null, phase: "verified" } },
        { assetId: "c", state: { downloading: true, progress: 0.9, error: null, phase: "verifying" } },
        { assetId: "d", state: { downloading: false, progress: 0.2, error: "network", phase: "error" } },
        { assetId: "e", state: { downloading: true, progress: 0, error: null, phase: "queued" } },
      ])
    ).toEqual([
      { assetId: "a", progress: 0.4, phase: "downloading" },
      { assetId: "c", progress: 0.9, phase: "verifying" },
      { assetId: "e", progress: 0, phase: "queued" },
    ]);
  });
});
