import { describe, expect, it } from "vitest";
import { FileImport, importFor, likelyTarget } from "./fileImport";

const f = (name: string, status: FileImport["status"], extra: Partial<FileImport> = {}): FileImport => ({ name, status, progress: 0, ...extra });

describe("importFor (the row that asked for a file shows its result, Prism IM-1)", () => {
  it("shows a file still being checked", () => {
    expect(importFor([f("boar-crypto.sqlite", "importing", { forIds: ["boar-crypto"] })], "boar-crypto")?.status).toBe("importing");
  });

  it("shows a refusal with its reason", () => {
    const r = importFor([f("x.sqlite", "failed", { forIds: ["boar-crypto"], errorKind: "unknown-file" })], "boar-crypto");
    expect(r?.errorKind).toBe("unknown-file");
  });

  it("says nothing once a file matched the row", () => {
    expect(importFor([f("boar-crypto.sqlite", "verified", { forIds: ["boar-crypto"], assetId: "boar-crypto" })], "boar-crypto")).toBeUndefined();
  });

  it("a region picked with its gazetteer: the region row is done, not 'another item'", () => {
    const batch = [
      f("sao-paulo.sqlite", "verified", { forIds: ["poi-sao-paulo", "poi-world-places"], assetId: "poi-sao-paulo" }),
      f("world-places.sqlite", "verified", { forIds: ["poi-sao-paulo", "poi-world-places"], assetId: "poi-world-places" }),
    ];
    expect(importFor(batch, "poi-sao-paulo")).toBeUndefined();
    expect(importFor(batch, "poi-world-places")).toBeUndefined();
  });

  it("a file that turned out to be another item is reported on the row that asked", () => {
    expect(importFor([f("preparedness.sqlite", "verified", { forIds: ["boar-crypto"], assetId: "boar-preparedness" })], "boar-crypto")?.assetId).toBe(
      "boar-preparedness"
    );
  });

  it("ignores files picked from the general import list", () => {
    expect(importFor([f("any.gguf", "failed")], "boar-crypto")).toBeUndefined();
  });
});

describe("likelyTarget (the row a copy in progress belongs to)", () => {
  const items = [
    { id: "q4b", filename: "models/qwen3-4b-instruct-2507-q4km.gguf", sizeBytes: 2_497_281_120 },
    { id: "q15", filename: "models/qwen2.5-1.5b-instruct-q4km.gguf", sizeBytes: 986_048_768 },
  ];

  it("matches by exact size, whatever the file is called", () => {
    expect(likelyTarget({ name: "renamed.gguf", sizeBytes: 2_497_281_120 }, items)?.id).toBe("q4b");
  });

  it("matches the Hugging Face name to the catalog's canonical file name (Harbor 2e7a026)", () => {
    expect(likelyTarget({ name: "Qwen3-4B-Instruct-2507-Q4_K_M.gguf" }, items)?.id).toBe("q4b");
  });

  it("finds nothing for an unrelated file", () => {
    expect(likelyTarget({ name: "notes.pdf", sizeBytes: 12 }, items)).toBeUndefined();
  });
});
