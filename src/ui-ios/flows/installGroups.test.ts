import { describe, expect, it } from "vitest";
import { InstallItem, installCategories } from "./installGroups";

const item = (id: string, kind: string, state: InstallItem["state"], sizeBytes = 100, importOnly = false): InstallItem => ({ id, kind, sizeBytes, state, importOnly });
const ready = { kind: "installed", verified: true } as const;
const queued = { kind: "not-installed" } as const;

describe("installCategories (setup 3, one row per category)", () => {
  it("groups by what the files are, in the mockup's order, and drops empty categories", () => {
    const rows = installCategories([
      item("kb1", "corpus", ready),
      item("qwen", "llm", queued),
      item("bge", "embedding", ready),
      item("poi-rome", "corpus", queued),
    ]);
    expect(rows.map((r) => r.category)).toEqual(["answer", "search", "knowledge", "places"]);
    expect(installCategories([item("qwen", "llm", queued)]).map((r) => r.category)).toEqual(["answer"]);
  });

  it("a failure anywhere makes the row a failure, even with others ready or downloading (Prism, honest)", () => {
    const [kb] = installCategories([
      item("kb1", "corpus", ready),
      item("kb2", "corpus", { kind: "downloading", phase: "downloading", progress: 0.5 }),
      item("kb3", "corpus", { kind: "failed", errorKind: "network", message: "", permanent: false }),
    ]);
    expect(kb.status).toBe("failed");
  });

  it("moving while any file's bytes arrive; ready only when all are on the phone; queued before", () => {
    expect(installCategories([item("a", "llm", { kind: "downloading", phase: "downloading", progress: 0.2 }), item("b", "llm", queued)])[0].status).toBe("moving");
    expect(installCategories([item("a", "llm", ready), item("b", "llm", ready)])[0].status).toBe("ready");
    expect(installCategories([item("a", "llm", queued)])[0].status).toBe("queued");
    expect(installCategories([item("a", "llm", { kind: "downloading", phase: "downloading", progress: 0 })])[0].status).toBe("queued");
    expect(installCategories([item("a", "llm", queued, 100, true)])[0].status).toBe("import");
  });

  it("counts files and the bytes on the phone for the spoken sentence ('2 of 3 files, 64%')", () => {
    const [kb] = installCategories([
      item("kb1", "corpus", ready, 100),
      item("kb2", "corpus", ready, 100),
      item("kb3", "corpus", { kind: "downloading", phase: "downloading", progress: 0.2 }, 300),
    ]);
    expect([kb.done, kb.total]).toEqual([2, 3]);
    expect(kb.fraction).toBeCloseTo((100 + 100 + 60) / 500);
  });
});
