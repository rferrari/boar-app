import { describe, it, expect } from "vitest";
import type { RetrievedChunk } from "../../rag/retrieve.types";
import { formatForCopy, formatForShare, formatSeconds, type ShareLabels } from "./shareFormat";

const labels: ShareLabels = {
  sources: "Sources",
  answeredOffline: "Answered offline by BOAR",
  sourcePassage: "Source passage",
  myDocuments: "My documents",
};

const chunk = (over: Partial<RetrievedChunk>): RetrievedChunk => ({
  chunkId: "c",
  docId: "d",
  title: "Raft (algorithm)",
  body: "…",
  score: 0.4,
  matchType: "lexical",
  ...over,
});

describe("formatForCopy", () => {
  it("returns just the answer when there are no sources", () => {
    expect(formatForCopy("  Hello.  ", [], labels)).toBe("Hello.");
  });

  it("appends numbered sources, naming user documents as such", () => {
    const text = formatForCopy(
      "Raft elects a leader [1]. See notes [2].",
      [chunk({ source: "Wikipedia" }), chunk({ title: "trip.pdf", collectionId: "col-1", source: "file" })],
      labels
    );
    expect(text).toBe(
      "Raft elects a leader [1]. See notes [2].\n\nSources:\n[1] Raft (algorithm) — Wikipedia\n[2] trip.pdf — My documents"
    );
  });

  it("omits the origin when the chunk has none", () => {
    expect(formatForCopy("A [1]", [chunk({})], labels)).toBe("A [1]\n\nSources:\n[1] Raft (algorithm)");
  });
});

describe("formatForShare", () => {
  const receipt = { modelId: "qwen3-4b", modelLabel: "Qwen3 4B", tokens: 90, tokPerSec: 14.8, ttftMs: 2100, totalMs: 6200, reasonCodes: [] };

  it("puts the question first and a provenance line last", () => {
    expect(formatForShare("What is Raft?", "A consensus algorithm.", [], receipt, labels, "en-US")).toBe(
      "What is Raft?\n\nA consensus algorithm.\n\nAnswered offline by BOAR · Qwen3 4B · 6.2 s"
    );
  });

  it("names the source passage instead of a model for extractive answers", () => {
    const out = formatForShare("Q", "A", [], { ...receipt, modelId: "extractive", totalMs: 400 }, labels, "en-US");
    expect(out.endsWith("Answered offline by BOAR · Source passage · 0.4 s")).toBe(true);
  });
});

describe("number formatting", () => {
  it("uses the locale's decimal separator", () => {
    expect(formatSeconds(6200, "pt-BR")).toBe("6,2 s");
  });

  it("drops the decimal from 10 seconds up", () => {
    expect(formatSeconds(83_400, "en-US")).toBe("83 s");
  });
});

describe("formatForCopy with the cited sources (CT-2)", () => {
  it("lists only the cited ones, each with its own number, and none when nothing is cited", () => {
    const srcs = [chunk({ title: "A" }), chunk({ title: "B" }), chunk({ title: "C" })];
    expect(formatForCopy("x [3]", srcs, labels, [2])).toBe("x [3]\n\nSources:\n[3] C");
    expect(formatForCopy("x", srcs, labels, [])).toBe("x");
  });
});
