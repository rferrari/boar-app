import { describe, it, expect } from "vitest";
import { answerSourceSplit, bestBand, citedSplit, sourcesCardMode, groupSources, relevanceBands, sourceParts, sourceSeal } from "./sourceLabel";

describe("sourceParts", () => {
  it("splits the corpus 'Name — URL (license)' string (Prism S-2)", () => {
    expect(sourceParts("Wikipedia — https://en.wikipedia.org/wiki/Nosebleed (CC BY-SA 4.0)")).toEqual({
      name: "Wikipedia",
      url: "https://en.wikipedia.org/wiki/Nosebleed",
    });
    expect(sourceParts("Wikibooks (First Aid, Outdoor Survival) — https://en.wikibooks.org/wiki/First_Aid")).toEqual({
      name: "Wikibooks (First Aid, Outdoor Survival)",
      url: "https://en.wikibooks.org/wiki/First_Aid",
    });
  });

  it("keeps a URL with spaces whole (pack URLs carry the raw title, Prism SR-1)", () => {
    expect(sourceParts("Wikipedia — https://en.wikipedia.org/wiki/Quantum cryptography (CC BY-SA 4.0)")).toEqual({
      name: "Wikipedia",
      url: "https://en.wikipedia.org/wiki/Quantum cryptography",
    });
  });

  it("names a bare URL by its host, and keeps a plain name", () => {
    expect(sourceParts("https://www.wikem.org/wiki/Epistaxis")).toEqual({ name: "wikem.org", url: "https://www.wikem.org/wiki/Epistaxis" });
    expect(sourceParts("WikEM")).toEqual({ name: "WikEM", url: null });
    expect(sourceParts(undefined)).toEqual({ name: null, url: null });
  });
});

describe("groupSources", () => {
  it("makes one row per article, keeping citation numbers (Iris: three 'Nosebleed' rows)", () => {
    const g = groupSources([
      { docId: "pack:boar-preparedness:a7", title: "Nosebleed" },
      { docId: "pack:boar-preparedness:a7", title: "Nosebleed" },
      { docId: "pack:boar-preparedness:a9", title: "Emergency bleeding control" },
      { docId: "pack:boar-preparedness:a7", title: "Nosebleed" },
    ]);
    expect(g).toEqual([
      { key: "pack:boar-preparedness:a7", title: "Nosebleed", indexes: [0, 1, 3] },
      { key: "pack:boar-preparedness:a9", title: "Emergency bleeding control", indexes: [2] },
    ]);
  });

  it("never merges sources without an article id", () => {
    expect(groupSources([{ docId: "", title: "A" }, { docId: "", title: "A" }]).length).toBe(2);
  });
});

describe("relevanceBands (Tusk: the raw value's scale depends on the query)", () => {
  it("maps the engine's relevance to high/medium/low at 0.75 and 0.5", () => {
    expect(relevanceBands([{ relevance: 1 }, { relevance: 0.75 }, { relevance: 0.59 }, { relevance: 0.5 }, { relevance: 0.49 }])).toEqual(["high", "high", "medium", "medium", "low"]);
  });
  it("gives no band to a source without a measured value", () => {
    expect(relevanceBands([{}, { relevance: 0 }, { relevance: Number.NaN }])).toEqual([null, null, null]);
  });
  it("a group takes its best passage's band", () => {
    expect(bestBand([null, "low", "medium"])).toBe("medium");
    expect(bestBand([null])).toBeNull();
  });
});

describe("citedSplit / answerSourceSplit (Prism CT-2, Tusk done.cited)", () => {
  it("is null while the engine said nothing: every source shows as before", () => {
    expect(citedSplit(3, undefined)).toBeNull();
    expect(answerSourceSplit({ sources: [1, 2] })).toBeNull();
  });

  it("an answer with no [n] cites nothing: all related, no sources card", () => {
    expect(citedSplit(3, [])).toEqual({ cited: [], related: [0, 1, 2] });
  });

  it("maps [n] to indexes and drops numbers out of range", () => {
    expect(citedSplit(3, [3, 1, 9, 0, 1.5])).toEqual({ cited: [0, 2], related: [1] });
  });

  it("counts the shown instant passage as cited, not when weak", () => {
    expect(answerSourceSplit({ sources: [1, 2, 3], cited: [], instant: { sourceIndex: 1 } })).toEqual({ cited: [1], related: [0, 2] });
    expect(answerSourceSplit({ sources: [1, 2], cited: [], instant: { sourceIndex: 1 }, weakSources: true })).toEqual({ cited: [], related: [0, 1] });
  });
});

describe("groupSources with only", () => {
  it("keeps the original indexes of the chosen sources", () => {
    const g = groupSources([{ docId: "a", title: "A" }, { docId: "b", title: "B" }, { docId: "a", title: "A" }], [1, 2]);
    expect(g).toEqual([{ key: "b", title: "B", indexes: [1] }, { key: "a", title: "A", indexes: [2] }]);
  });
});

describe("sourcesCardMode (Prism: no shrinking card while streaming)", () => {
  it("only the count while writing, then cited or related; all without the engine's cited", () => {
    expect(sourcesCardMode(true, null)).toBe("found");
    expect(sourcesCardMode(false, null)).toBe("all");
    expect(sourcesCardMode(false, { cited: [] })).toBe("related");
    expect(sourcesCardMode(true, { cited: [0] })).toBe("cited"); // Deepen running: the fast pass's card stays
  });
});

describe("sourceSeal (Prism CH-17)", () => {
  const labels = { myDocuments: "My documents", corpus: "Offline library" };
  it("a pack source: its name in the badge, the URL apart without scheme or license", () => {
    expect(sourceSeal({ source: "Wikipedia — https://en.wikipedia.org/wiki/Nosebleed (CC BY-SA 4.0)" }, labels)).toEqual({
      label: "Wikipedia",
      url: "en.wikipedia.org/wiki/Nosebleed",
    });
  });
  it("the user's documents and unnamed sources", () => {
    expect(sourceSeal({ source: "notes.pdf", collectionId: "c1" }, labels)).toEqual({ label: "My documents", url: null });
    expect(sourceSeal({ source: "" }, labels)).toEqual({ label: "Offline library", url: null });
    expect(sourceSeal({ source: "WikEM" }, labels)).toEqual({ label: "WikEM", url: null });
  });
  it("never puts a URL in the badge", () => {
    expect(sourceSeal({ source: "https://www.wikem.org/wiki/Epistaxis" }, labels).label).toBe("wikem.org");
  });
});
