import { describe, expect, it } from "vitest";
import { canAutoRetry, DownloadErrorKind, modelRowView, RowInput, mayCloseApp, withLiveProgress } from "./modelRowState";
import { COMPACT_ONLY_MAX_RAM_BYTES } from "../../routing/defaultModel";

const idle = { downloading: false, progress: 0, error: null };

function view(overrides: Partial<RowInput>) {
  return modelRowView({ present: false, roles: [], ...overrides });
}

describe("modelRowView", () => {
  it("offers download when the asset is not on disk", () => {
    const v = view({});
    expect(v.state).toEqual({ kind: "not-installed" });
    expect(v.primary).toBe("download");
  });

  it("shows download progress without an action", () => {
    const v = view({ download: { downloading: true, progress: 0.4, error: null } });
    expect(v.state).toEqual({ kind: "downloading", phase: "downloading", progress: 0.4 });
    expect(v.primary).toBe("none");
  });

  it("reports copying for an imported file", () => {
    const v = view({ download: { downloading: true, progress: 0.1, error: null, phase: "copying" } });
    expect(v.state).toMatchObject({ kind: "downloading", phase: "copying" });
  });

  it("reports hash progress while verifying", () => {
    const v = view({
      download: { downloading: true, progress: 1, error: null, phase: "verifying", verifyBytes: 25, verifyTotal: 100 },
    });
    expect(v.state).toEqual({ kind: "verifying", progress: 0.25 });
    expect(view({ download: { downloading: true, progress: 0.6, error: null, phase: "verifying" } }).state).toEqual({
      kind: "verifying",
      progress: 0.6,
    });
  });

  it("turns a download error into a retry with its kind", () => {
    const v = view({ download: { ...idle, error: "sha256 differs", errorKind: "hash-mismatch", permanent: true } });
    expect(v.state).toEqual({ kind: "failed", errorKind: "hash-mismatch", message: "sha256 differs", permanent: true });
    expect(v.primary).toBe("retry");
    expect(v.tone).toBe("danger");
  });

  it("treats an error without a kind as unknown and retryable", () => {
    const v = view({ download: { ...idle, error: "boom" } });
    expect(v.state).toMatchObject({ kind: "failed", errorKind: "unknown", permanent: false });
  });

  it("offers Use on an installed, unused model", () => {
    const v = view({ present: true, checksumOk: true });
    expect(v.state).toEqual({ kind: "installed", verified: true });
    expect(v.primary).toBe("use");
    expect(v.tone).toBe("neutral");
  });

  it("marks an unverified install as a warning, never as danger", () => {
    const v = view({ present: true, checksumOk: null });
    expect(v.state).toEqual({ kind: "installed", verified: false });
    expect(v.tone).toBe("warning");
  });

  it("blocks removal of a model in use and lists its roles", () => {
    const v = view({ present: true, checksumOk: true, roles: ["answer"] });
    expect(v.state).toEqual({ kind: "in-use", roles: ["answer"], verified: true });
    expect(v.removeBlocked).toBe(true);
    expect(v.primary).toBe("none");
  });

  it("shows loading over everything else", () => {
    expect(view({ present: true, loading: true, roles: ["answer"] }).state).toEqual({ kind: "loading" });
  });

  it("surfaces a failed load as a retryable failure", () => {
    const v = view({ present: true, loadError: "out of memory" });
    expect(v.state).toEqual({ kind: "failed", errorKind: "load", message: "out of memory", permanent: false });
  });

  it("warns about memory fit but never hides the row", () => {
    expect(view({ fit: "resident" }).fitWarning).toBeNull();
    expect(view({ fit: "streaming" }).fitWarning).toBe("streaming");
    const insufficient = view({ fit: "insufficient" });
    expect(insufficient.fitWarning).toBe("insufficient");
    expect(insufficient.state).toEqual({ kind: "not-installed" });
  });

  it("explains instead of downloading a model that cannot run here", () => {
    expect(view({ fit: "insufficient" }).primary).toBe("explain");
    expect(view({ fit: "thrashing" }).primary).toBe("download");
    expect(view({ fit: "streaming" }).primary).toBe("download");
  });
});

describe("canAutoRetry", () => {
  it("retries transient download failures only", () => {
    const failed = (errorKind: DownloadErrorKind, permanent: boolean) =>
      view({ download: { ...idle, error: "x", errorKind, permanent } }).state;
    expect(canAutoRetry(failed("network", false))).toBe(true);
    expect(canAutoRetry(failed("storage", true))).toBe(false);
    expect(canAutoRetry(view({ present: true, loadError: "oom" }).state)).toBe(false);
    expect(canAutoRetry(view({}).state)).toBe(false);
  });
});

describe("error kinds from the trust layer", () => {
  it("keeps an unreadable imported file as a permanent failure", () => {
    const v = view({ download: { downloading: false, progress: 0, error: "unreadable", errorKind: "unknown-file", permanent: true } });
    expect(v.state).toMatchObject({ kind: "failed", errorKind: "unknown-file", permanent: true });
    expect(canAutoRetry(v.state)).toBe(false);
  });
});

describe("mayCloseApp (CR-1: Qwen3-4B on 3.8 GB got the app killed)", () => {
  const GIB = 1024 ** 3;
  const compact = 1.0e9;
  const fourB = { kind: "llm", sizeBytes: 2.5e9 };

  it("warns at and under the setup's compact-only limit, for a model bigger than the compact one", () => {
    expect(mayCloseApp(fourB, compact, 3.8 * GIB)).toBe(true);
    expect(mayCloseApp(fourB, compact, COMPACT_ONLY_MAX_RAM_BYTES)).toBe(true);
  });

  it("does not warn just above the limit, on unknown RAM, or for the compact model itself", () => {
    expect(mayCloseApp(fourB, compact, COMPACT_ONLY_MAX_RAM_BYTES + 1)).toBe(false);
    expect(mayCloseApp(fourB, compact, 0)).toBe(false);
    expect(mayCloseApp({ kind: "llm", sizeBytes: compact }, compact, 3.8 * GIB)).toBe(false);
    expect(mayCloseApp({ kind: "embedding", sizeBytes: 3e9 }, compact, 3.8 * GIB)).toBe(false);
  });

  it("replaces 'May be slow' in the row view", () => {
    const v = modelRowView({ present: true, roles: [], fit: "thrashing", mayCloseApp: true });
    expect(v.mayCloseApp).toBe(true);
    expect(v.fitWarning).toBeNull();
    expect(modelRowView({ present: true, roles: [], fit: "thrashing" })).toMatchObject({ mayCloseApp: false, fitWarning: "thrashing" });
  });
});

describe("load crash and confirmation (CR-2)", () => {
  const row = (o: Partial<RowInput>) => modelRowView({ present: true, roles: [], ...o });

  it("a risky model asks before Use until the user confirms it", () => {
    expect(row({ mayCloseApp: true }).confirmUse).toBe(true);
    expect(row({ mayCloseApp: true, largeConfirmed: true }).confirmUse).toBe(false);
  });

  it("a model whose last load killed the app says 'Didn't open here' and asks again, even if confirmed", () => {
    const v = row({ loadCrashed: true, largeConfirmed: true, fit: "thrashing" });
    expect(v).toMatchObject({ didNotOpen: true, mayCloseApp: true, confirmUse: true, fitWarning: null });
  });

  it("an ordinary model never asks", () => {
    expect(row({})).toMatchObject({ didNotOpen: false, mayCloseApp: false, confirmUse: false });
  });
});

describe("won't fit on this phone (CR-1)", () => {
  it("offers no download and replaces the memory warnings", () => {
    const v = modelRowView({ present: false, roles: [], wontFit: true, fit: "thrashing", mayCloseApp: true });
    expect(v).toMatchObject({ wontFit: true, primary: "none", fitWarning: null, mayCloseApp: false });
  });

  it("an installed model is not hidden behind it (it can still be removed)", () => {
    expect(modelRowView({ present: true, roles: [], wontFit: true }).wontFit).toBe(false);
  });
});

describe("withLiveProgress (a row follows its own download, perf audit #2/#9)", () => {
  const dl = (over: object) => ({ downloading: true, progress: 0.5, error: null, phase: "downloading" as const, ...over });

  it("moves the bar of a downloading or verifying row, nothing else", () => {
    expect(withLiveProgress({ kind: "downloading", phase: "downloading", progress: 0.1 }, dl({}))).toEqual({ kind: "downloading", phase: "downloading", progress: 0.5 });
    expect(withLiveProgress({ kind: "verifying", progress: 0.1 }, dl({ phase: "verifying", verifyBytes: 3, verifyTotal: 4 }))).toEqual({ kind: "verifying", progress: 0.75 });
  });

  it("leaves phase changes and errors to the screen's view", () => {
    const downloading = { kind: "downloading" as const, phase: "downloading" as const, progress: 0.1 };
    expect(withLiveProgress(downloading, dl({ phase: "verifying" }))).toBe(downloading);
    expect(withLiveProgress(downloading, dl({ error: "network" }))).toBe(downloading);
    expect(withLiveProgress(downloading, undefined)).toBe(downloading);
    const installed = { kind: "installed" as const, verified: true };
    expect(withLiveProgress(installed, dl({}))).toBe(installed);
  });

  it("keeps the same object when nothing moved", () => {
    const s = { kind: "downloading" as const, phase: "downloading" as const, progress: 0.5 };
    expect(withLiveProgress(s, dl({}))).toBe(s);
  });
});
