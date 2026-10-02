import { describe, expect, it } from "vitest";
import en from "../locales/en.json";
import pt from "../locales/pt.json";
import type { AnswerState } from "./answerReducer";
import { answerReducer, initialAnswer } from "./answerReducer";
import type { AnswerEvent, SourceChunk } from "./answerEvents";
import {
  articleCount,
  BADGE_TONES,
  badgeTone,
  deepSectionLabeled,
  deepSourcesFrom,
  emptyTimeline,
  foldTimeline,
  MAX_ARTICLES,
  partSignal,
  progressOf,
  researchPhase,
  researchView,
  rowGrows,
  SPLIT_PLACEHOLDERS,
  stackArticles,
  summaryItems,
  timelinePillStep,
  type Timeline,
} from "./liveResearch";
import { phaseAnnouncement } from "./presentation";

const t = (key: string, opts?: Record<string, unknown>) => (opts ? `${key}${JSON.stringify(opts)}` : key);
const chunk = (docId: string, n = 0, title = docId): SourceChunk => ({
  chunkId: `${docId}#${n}`,
  docId,
  title,
  body: `  ${title}   is the passage\n number ${n}.`,
  source: `Wikipedia — https://en.wikipedia.org/wiki/${title} (CC BY-SA 4.0)`,
  score: 0,
  matchType: "hybrid",
});
const part = (index: number, count: number, subQuestion?: string) => ({ index, count, ...(subQuestion ? { subQuestion } : {}) });

/** Replays a run: each step is what the answer shows at one render. */
function replay(steps: { sources: SourceChunk[]; detail?: unknown; searching?: boolean }[]): Timeline {
  return steps.reduce((tl, s) => foldTimeline(tl, { sources: s.sources, detail: s.detail, searching: s.searching ?? true }), emptyTimeline());
}

describe("foldTimeline: single pass (one implicit step)", () => {
  it("lists every article under the one step, first found first, passages folded", () => {
    const tl = replay([{ sources: [] }, { sources: [chunk("Greenhouse effect"), chunk("Climate change"), chunk("Greenhouse effect", 1)] }]);
    expect(tl.multi).toBe(false);
    expect(tl.parts).toHaveLength(1);
    expect(tl.parts[0].articles).toEqual(["Greenhouse effect", "Climate change"]);
    expect(tl.articles["Greenhouse effect"]).toMatchObject({ title: "Greenhouse effect", initial: "G", passage: "Greenhouse effect is the passage number 0." });
  });

  it("returns the same object when nothing new arrived (the panel's memo holds while tokens stream)", () => {
    const tl = replay([{ sources: [chunk("A")] }]);
    expect(foldTimeline(tl, { sources: [chunk("A")], searching: false })).toBe(tl);
  });

  it("one sources event or several give the same rows (today's engine and an incremental one)", () => {
    const all = [chunk("A"), chunk("B"), chunk("A", 1), chunk("C")];
    const once = replay([{ sources: all }]);
    let s = initialAnswer("a");
    const steps: { sources: SourceChunk[] }[] = [];
    for (const c of all) {
      s = answerReducer(s, { type: "sources", answerId: "a", tier: "fast", sources: [c] } as AnswerEvent);
      steps.push({ sources: s.sources });
    }
    expect(replay(steps)).toEqual(once);
  });
});

describe("foldTimeline: Deep Research parts", () => {
  it("puts each article under the part that was searching when it arrived; rows never move", () => {
    const tl = replay([
      { sources: [], detail: {} }, // decomposing: retrieving with an empty detail
      { sources: [], detail: part(0, 3, "How does the greenhouse effect trap heat?") },
      { sources: [chunk("Greenhouse effect"), chunk("Infrared")], detail: part(0, 3) },
      { sources: [chunk("Greenhouse effect"), chunk("Infrared")], detail: part(1, 3, "Which gases?") },
      { sources: [chunk("Greenhouse effect"), chunk("Infrared"), chunk("Carbon dioxide")], detail: part(1, 3) },
      { sources: [chunk("Greenhouse effect"), chunk("Infrared"), chunk("Carbon dioxide")], detail: part(2, 3) },
      { sources: [chunk("Greenhouse effect"), chunk("Infrared"), chunk("Carbon dioxide"), chunk("Global warming")], detail: part(2, 3) },
    ]);
    expect(tl.multi).toBe(true);
    expect(tl.parts.map((p) => p.articles)).toEqual([["Greenhouse effect", "Infrared"], ["Carbon dioxide"], ["Global warming"]]);
    expect(tl.parts.map((p) => p.question)).toEqual(["How does the greenhouse effect trap heat?", "Which gases?", undefined]);
    expect(tl.order).toEqual(["Greenhouse effect", "Infrared", "Carbon dioxide", "Global warming"]);
  });

  it("keeps a part's sub-question when a later stage for it comes without one", () => {
    const tl = replay([{ sources: [], detail: part(0, 2, "Q1") }, { sources: [], detail: part(0, 2) }]);
    expect(tl.parts[0].question).toBe("Q1");
  });

  it("a stage past the search (synthesizing, no detail) changes nothing", () => {
    const tl = replay([{ sources: [chunk("A")], detail: part(1, 2) }]);
    expect(foldTimeline(tl, { sources: [chunk("A")], detail: undefined, searching: false })).toBe(tl);
  });
});

describe("partSignal (defensive detail)", () => {
  it("reads index/count and the optional sub-question, one line", () => {
    expect(partSignal(part(1, 3, "  What is\nCO2?  "))).toEqual({ part: { index: 1, count: 3, question: "What is CO2?" }, splitting: false });
    expect(partSignal(part(0, 2))).toEqual({ part: { index: 0, count: 2 }, splitting: false });
  });
  it("empty detail = splitting; no detail = single pass; junk = nothing", () => {
    expect(partSignal({})).toEqual({ part: null, splitting: true });
    expect(partSignal(undefined)).toEqual({ part: null, splitting: false });
    expect(partSignal({ index: "1", count: 3 })).toEqual({ part: null, splitting: false });
    expect(partSignal({ index: 5, count: 3 }).part?.index).toBe(2);
    expect(partSignal({ index: 0, count: 2, subQuestion: 42 }).part).toEqual({ index: 0, count: 2 });
  });
});

describe("researchView", () => {
  const deep = replay([
    { sources: [], detail: part(0, 3) },
    { sources: ["A", "B", "C", "D", "E"].map((d) => chunk(d)), detail: part(0, 3) },
    { sources: ["A", "B", "C", "D", "E"].map((d) => chunk(d)), detail: part(1, 3) },
  ]);

  it("header, counter, progress and node states while a part searches", () => {
    const v = researchView(deep, "searching");
    expect(v.header).toEqual({ key: "chat.research.headerPart", opts: { index: 2, count: 3 } });
    expect(v.total).toBe(5);
    // Part 2 searching (no new article yet): a third into its share of the bar.
    expect(v.progress).toBeCloseTo((1 + 1 / 3) / 3);
    expect(v.parts[1].reading).toBe(false);
    expect(v.parts.map((p) => p.status)).toEqual(["done", "active", "pending"]);
    expect(v.parts[2]).toMatchObject({ labelKey: "chat.research.part", labelOpts: { index: 3, count: 3 } });
  });

  it("at most 3 articles per part, then +N", () => {
    const p0 = researchView(deep, "searching").parts[0];
    expect(p0.shown.map((a) => a.key)).toEqual(["A", "B", "C"]);
    expect(p0.shown).toHaveLength(MAX_ARTICLES);
    expect(p0.more).toBe(2);
  });

  it("after the search: every part done, reading N articles, full bar", () => {
    const v = researchView(deep, "reading");
    expect(v.parts.every((p) => p.status === "done")).toBe(true);
    expect(v.header).toEqual({ key: "chat.research.headerReading", opts: { count: 5 } });
    expect(v.progress).toBe(1);
  });

  it("single pass: one unnumbered step, no 'reading' row while searching (only the header says it after)", () => {
    const single = replay([{ sources: [chunk("A")] }]);
    const searching = researchView(single, "searching");
    expect(searching.parts).toHaveLength(1);
    expect(searching.parts[0]).toMatchObject({ status: "active", labelKey: "chat.research.searchLibrary" });
    expect(searching.header.key).toBe("chat.research.headerSearching");
    const reading = researchView(single, "reading");
    expect(reading.parts[0]).toMatchObject({ status: "done", labelKey: "chat.research.searchedLibrary" });
    expect(reading.header).toEqual({ key: "chat.research.headerReading", opts: { count: 1 } });
  });

  it("splitting before any part, and the other phases' headers", () => {
    const split = replay([{ sources: [], detail: {} }]);
    expect(researchView(split, "searching").header.key).toBe("chat.research.headerSplitting");
    expect(progressOf(split, "searching")).toBe(0);
    const none = emptyTimeline();
    expect(researchView(none, "reading").header.key).toBe("chat.stage.thinking");
    expect(researchView(none, "loading_model").header.key).toBe("chat.stage.loadingModel");
    expect(researchView(none, "synthesizing").header.key).toBe("chat.stage.synthesizing");
    expect(researchView(none, null).parts[0].status).toBe("done");
  });
});

describe("iPhone d0a9c66: a part past its search, empty parts, splitting", () => {
  // The device run: 3 parts, part 1 found 2 articles, parts 2 and 3 found nothing new.
  const s2 = [chunk("Greenhouse effect"), chunk("Infrared")];
  const upTo = (n: number) =>
    replay(
      [
        { sources: [], detail: {} },
        { sources: [], detail: part(0, 3) },
        { sources: s2, detail: part(0, 3) }, // part 1's sources event: its sub-answer is being written now
        { sources: s2, detail: part(1, 3) }, // part 2: searches, nothing new, no sources event
        { sources: s2, detail: part(2, 3) },
        { sources: s2, detail: undefined, searching: false }, // synthesizing
      ].slice(0, n)
    );

  it("infers 'reading part i' from the part's sources arriving: header, calm node, bar, pill step", () => {
    const tl = upTo(3);
    const v = researchView(tl, "searching");
    expect(v.header).toEqual({ key: "chat.research.headerReadingPart", opts: { index: 1, count: 3 } });
    expect(v.parts[0]).toMatchObject({ status: "active", reading: true });
    expect(v.progress).toBeCloseTo(2 / 3 / 3);
    expect(timelinePillStep(tl, "searching")).toBe("chat.stepShort.read");
  });

  it("a part that found nothing new reads as searching (no engine signal) and, once done, says 'No new articles'", () => {
    const tl = upTo(4);
    const v = researchView(tl, "searching");
    expect(v.header).toEqual({ key: "chat.research.headerPart", opts: { index: 2, count: 3 } });
    expect(v.parts.map((p) => [p.status, p.reading, p.empty])).toEqual([
      ["done", false, false],
      ["active", false, false],
      ["pending", false, false],
    ]);
    expect(timelinePillStep(tl, "searching")).toBeNull();
    const done = researchView(upTo(6), "synthesizing");
    expect(done.parts.map((p) => p.empty)).toEqual([false, true, true]);
    // A single pass never says "no new articles" (it has its own "No article on this").
    expect(researchView(replay([{ sources: [] }]), "reading").parts[0].empty).toBe(false);
  });

  it("while splitting: placeholders, not a single-pass step; then the parts in their place", () => {
    const split = researchView(upTo(1), "searching");
    expect(split).toMatchObject({ splitting: true, parts: [] });
    expect(SPLIT_PLACEHOLDERS).toBe(3);
    const parts = researchView(upTo(2), "searching");
    expect(parts.splitting).toBe(false);
    expect(parts.parts).toHaveLength(3);
    expect(researchView(replay([{ sources: [] }]), "searching").splitting).toBe(false);
  });

  it("the pill keeps the stage's step outside a Deep Research part", () => {
    expect(timelinePillStep(null, "searching")).toBeNull();
    expect(timelinePillStep(replay([{ sources: [chunk("A")] }]), "searching")).toBeNull();
    expect(timelinePillStep(upTo(3), "synthesizing")).toBeNull();
  });
});

describe("summary pill", () => {
  it("'3 parts · N articles' for Deep Research, 'N articles' for one step, nothing to open without articles", () => {
    const deep = replay([{ sources: [chunk("A"), chunk("B")], detail: part(0, 3) }]);
    expect(summaryItems(deep)).toEqual([
      { key: "chat.research.parts", opts: { count: 3 } },
      { key: "chat.research.articles", opts: { count: 2 } },
    ]);
    expect(summaryItems(replay([{ sources: [chunk("A")] }]))).toEqual([{ key: "chat.research.articles", opts: { count: 1 } }]);
    expect(summaryItems(emptyTimeline())).toBeNull();
  });
  it("stacks the first 3 articles' badges", () => {
    const tl = replay([{ sources: ["A", "B", "C", "D"].map((d) => chunk(d)) }]);
    expect(stackArticles(tl).map((a) => a.key)).toEqual(["A", "B", "C"]);
  });
});

describe("badges, counts, motion helpers", () => {
  it("a stable tone per article, from the DS tones", () => {
    expect(badgeTone("Greenhouse effect")).toBe(badgeTone("Greenhouse effect"));
    const tones = new Set(["A", "B", "C", "D", "E", "F", "G", "H"].map(badgeTone));
    expect(tones.size).toBeGreaterThan(1);
    for (const tone of tones) expect(BADGE_TONES).toContain(tone);
  });
  it("counts articles, not passages", () => {
    expect(articleCount([chunk("A"), chunk("A", 1), chunk("B")])).toBe(2);
  });
  it("rows there at mount show in place; later ones pop in", () => {
    expect(rowGrows("A", new Set(["A"]))).toBe(false);
    expect(rowGrows("B", new Set(["A"]))).toBe(true);
  });
  it("research phases", () => {
    expect(researchPhase("searching")).toBe("searching");
    expect(researchPhase("done")).toBeNull();
    expect(researchPhase("locating")).toBeNull();
  });
});

describe("Deepen", () => {
  it("lists only what its own search appended (full list so far, new items at the end)", () => {
    let s = answerReducer(initialAnswer("a"), { type: "sources", answerId: "a", tier: "fast", sources: [chunk("A"), chunk("B")] } as AnswerEvent);
    const from = deepSourcesFrom(null, true, s.sources.length)!;
    s = answerReducer(s, { type: "sources", answerId: "a", tier: "deep", sources: [chunk("A"), chunk("C")] } as AnswerEvent);
    s = answerReducer(s, { type: "sources", answerId: "a", tier: "deep", sources: [chunk("A"), chunk("C"), chunk("D")] } as AnswerEvent);
    const tl = replay([{ sources: s.sources.slice(from), detail: part(0, 2) }]);
    expect(tl.order).toEqual(["C", "D"]);
  });
  it("deepSourcesFrom holds the count seen when the deep pass shows up", () => {
    expect(deepSourcesFrom(null, false, 4)).toBeNull();
    expect(deepSourcesFrom(null, true, 4)).toBe(4);
    expect(deepSourcesFrom(4, true, 9)).toBe(4);
  });
  it("'Deeper answer' only for a Deepen, not for a question routed to the deep tier", () => {
    expect(deepSectionLabeled({ fast: {}, deep: {} })).toBe(true);
    expect(deepSectionLabeled({ deep: {} })).toBe(false);
    expect(deepSectionLabeled({ fast: {} })).toBe(false);
  });
});

describe("the 'answering' announcement: one summary, never per row", () => {
  const writing = (sources: SourceChunk[], extra: Partial<AnswerState> = {}): AnswerState => ({ answerIds: ["a"], sources, fast: { text: "x", stage: "generating" }, ...extra });
  it("with the article count, or plain without sources", () => {
    expect(phaseAnnouncement("generating", writing([chunk("A"), chunk("A", 1), chunk("B")]), t)).toEqual({ message: 'chat.announce.answeringFrom{"count":2}' });
    expect(phaseAnnouncement("generating", writing([]), t)).toEqual({ message: "chat.announce.answering" });
  });
  it("a places answer's sources are places, not articles", () => {
    const places = { places: [], coverage: "ok" } as unknown as AnswerState["places"];
    expect(phaseAnnouncement("generating", writing([chunk("A")], { places }), t)).toEqual({ message: "chat.announce.answering" });
  });
});

describe("live research copy (en/pt in sync)", () => {
  it("both locales have the same keys, with what they interpolate", () => {
    expect(Object.keys(pt.chat.research).sort()).toEqual(Object.keys(en.chat.research).sort());
    for (const d of [en, pt]) {
      const r = d.chat.research;
      expect(r.headerPart).toMatch(/\{\{index\}\}[\s\S]*\{\{count\}\}/);
      expect(r.part).toMatch(/\{\{index\}\}[\s\S]*\{\{count\}\}/);
      for (const k of ["headerReading_other", "articles_other", "parts_other", "more_other"] as const) expect(r[k]).toContain("{{count}}");
      expect(r.summarySpoken).toContain("{{summary}}");
      expect(d.chat.announce.answeringFrom_other).toContain("{{count}}");
    }
    expect(pt.chat.research.headerSplitting).toBe("Dividindo a pergunta…");
    expect(pt.chat.research.headerPart).toBe("Pesquisando parte {{index}} de {{count}}");
    expect(pt.chat.research.headerReading_other).toBe("Lendo {{count}} artigos…");
    expect(pt.chat.research.headerReadingPart).toBe("Lendo a parte {{index}} de {{count}}…");
    expect(en.chat.research.headerReadingPart).toBe("Reading part {{index}} of {{count}}…");
    expect(pt.chat.research.noneNew).toBe("Nenhum artigo novo");
    expect(en.chat.research.noneNew).toBe("No new articles");
  });
});

describe("foldTimeline: the engine's answering signal", () => {
  it("marks a part as read when its sub-answer starts, even with no new article", () => {
    const tl = replay([
      { sources: [], detail: part(0, 2, "First?") },
      { sources: [], detail: { ...part(0, 2, "First?"), answering: true } },
    ]);
    expect(tl.parts[0].read).toBe(true);
    expect(tl.parts[0].articles).toEqual([]);
    expect(tl.parts[1].read).toBeFalsy();
  });

  it("says 'no articles' in words instead of '0 articles' in the pill", () => {
    const tl = replay([{ sources: [], detail: part(0, 3, "First?") }]);
    expect(summaryItems(tl)).toEqual([
      { key: "chat.research.parts", opts: { count: 3 } },
      { key: "chat.research.noArticles", opts: {} },
    ]);
  });
});
