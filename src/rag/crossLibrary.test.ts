/// <reference types="node" />
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { dropCrossLibraryCopies, fuseRetrievalResults, libraryOf } from "./pure";
import { containedShare, dedupeArticleCopies } from "./dedupe";
import type { RetrievedChunk } from "./retrieve.types";

// Real passages: the built-in corpus (assets/corpus) and the Vital Articles pack (wiki-vital5) hold different
// paragraphs of the same article, so #48's word-pair overlap (0.6) doesn't see them as copies, and the X6 Pro
// prompt had "Vaccine, Vaccine" (desktop check of #48, 2026-09-30).
type Raw = { chunkId: string; docId: string; title: string; body: string };
const fx: { pairs: Array<{ question: string; builtin: Raw; pack: Raw }>; sameLibrarySections: Raw[] } = JSON.parse(
  readFileSync(join(__dirname, "testing", "fixtures", "cross-library-copies.json"), "utf8")
);
const chunk = (r: Raw, score: number, similarity = 0.8): RetrievedChunk => ({ ...r, score, similarity, matchType: "lexical" });
const other = (title: string, score: number): RetrievedChunk => chunk({ chunkId: `pack:wiki-vital5:x-${title}`, docId: `pack:wiki-vital5:${title}`, title, body: `${title} text.` }, score);

describe("one library per article (follow-up to #48)", () => {
  it("the real pairs are different paragraphs: #48's overlap rule keeps both", () => {
    for (const { builtin, pack } of fx.pairs) {
      expect(Math.max(containedShare(builtin.body, pack.body), containedShare(pack.body, builtin.body)), pack.title).toBeLessThan(0.6);
      expect(dedupeArticleCopies([builtin, pack]), pack.title).toHaveLength(2);
    }
  });

  it("fusion keeps the better-ranked library's passage and refills the slot with the next distinct source", () => {
    for (const { builtin, pack } of fx.pairs) {
      const lexical = [chunk(builtin, 10), chunk(pack, 9), other("Next A", 8), other("Next B", 7), other("Next C", 6)];
      const titles = fuseRetrievalResults(lexical, [], 3).map((c) => c.title);
      expect(titles, pack.title).toEqual([builtin.title, "Next A", "Next B"]);
      // The pack ranked first: its passage is the one kept.
      const kept = fuseRetrievalResults([chunk(pack, 10), chunk(builtin, 9), other("Next A", 8)], [], 2);
      expect(kept.map((c) => c.chunkId), pack.title).toEqual([pack.chunkId, "pack:wiki-vital5:x-Next A"]);
    }
  });

  it("drops another library's copy after the named-article merge too, whatever the order", () => {
    for (const { builtin, pack } of fx.pairs) {
      expect(dropCrossLibraryCopies([pack, other("Next A", 1), builtin]).map((c) => c.chunkId), pack.title).toEqual([pack.chunkId, "pack:wiki-vital5:x-Next A"]);
      expect(dropCrossLibraryCopies([builtin, pack]).map((c) => c.chunkId), pack.title).toEqual([builtin.chunkId]);
    }
  });

  it("control: two sections of one article in one library both stay", () => {
    const [a, b] = fx.sameLibrarySections;
    expect(a.title).toBe(b.title);
    expect(dropCrossLibraryCopies([a, b])).toHaveLength(2);
    expect(fuseRetrievalResults([chunk(a, 10), chunk(b, 9), other("Next A", 8)], [], 3).map((c) => c.chunkId)).toEqual([a.chunkId, b.chunkId, "pack:wiki-vital5:x-Next A"]);
  });

  it("the user's own documents are never a copy of a library article; other sites' titles stay apart", () => {
    const { builtin } = fx.pairs[0];
    const mine = { chunkId: "doc-42-c0", docId: "doc-42", title: "Vaccine", body: "My notes on vaccines." };
    expect(libraryOf(mine)).toBeNull();
    expect(dropCrossLibraryCopies([builtin, mine])).toHaveLength(2);
    const voyage = { chunkId: "pack:boar-wikivoyage-en:7", docId: "pack:boar-wikivoyage-en:a7", title: "Wikivoyage: Paris", body: "Paris is the capital of France." };
    const wiki = { chunkId: "wiki-min-paris", docId: "wiki-min-paris", title: "Paris", body: "Paris is the capital of France." };
    expect(dropCrossLibraryCopies([voyage, wiki])).toHaveLength(2);
  });

  it("title matching ignores case and spacing only: 'Vaccines' is another title", () => {
    const { builtin, pack } = fx.pairs[0];
    expect(dropCrossLibraryCopies([builtin, { ...pack, title: "  vaccine " }])).toHaveLength(1);
    expect(dropCrossLibraryCopies([builtin, { ...pack, title: "Vaccines" }])).toHaveLength(2);
  });
});
