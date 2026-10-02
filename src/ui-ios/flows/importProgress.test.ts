import { describe, expect, it } from "vitest";
import type { FileImport } from "./fileImport";
import type { RowState } from "./modelRowState";
import { importHeroFraction, withVerifiedImport } from "./importProgress";

const verified = (assetId: string): FileImport => ({ name: `${assetId}.bin`, status: "verified", progress: 1, assetId });
const importing = (name: string): FileImport => ({ name, status: "importing", progress: 0.4 });

describe("withVerifiedImport (Prism L3-3)", () => {
  it("a file verified in the running pick counts as installed before the catalog refreshes", () => {
    expect(withVerifiedImport({ kind: "not-installed" }, "qwen", [verified("qwen")])).toEqual({ kind: "installed", verified: true });
  });
  it("leaves other items and a file still being copied alone", () => {
    expect(withVerifiedImport({ kind: "not-installed" }, "bge", [verified("qwen"), importing("bge.bin")])).toEqual({ kind: "not-installed" });
  });
  it("never overrides a real state", () => {
    const inUse: RowState = { kind: "in-use", roles: ["answer"], verified: true };
    expect(withVerifiedImport(inUse, "qwen", [verified("qwen")])).toBe(inUse);
  });
  it("the file counter advances as files verify: 'File n of 4'", () => {
    const imports = [verified("a"), verified("b"), importing("c.bin")];
    const present = ["a", "b", "c", "d"].filter((id) => withVerifiedImport({ kind: "not-installed" }, id, imports).kind === "installed").length;
    expect(Math.min(present + 1, 4)).toBe(3);
  });
});

describe("importHeroFraction (Prism L3-4)", () => {
  it("counts the whole setup, so the bar never falls back to 0 at the next file", () => {
    // 3 of 10 GB in, the next 2 GB file 50% copied.
    expect(importHeroFraction(3, 10, { progress: 0.5, sizeBytes: 2 })).toBeCloseTo(0.4);
    expect(importHeroFraction(5, 10, { progress: 0, sizeBytes: 2 })).toBeCloseTo(0.5);
  });
  it("never passes 100%, even with a file outside the setup", () => {
    expect(importHeroFraction(10, 10, { progress: 1, sizeBytes: 4 })).toBe(1);
  });
  it("without sizes, falls back to the file's own progress", () => {
    expect(importHeroFraction(0, 0, { progress: 0.3 })).toBe(0.3);
  });
});
