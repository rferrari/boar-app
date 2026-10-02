import { describe, expect, it } from "vitest";
import {
  DRAIN_MS,
  FADE_MS,
  MAX_CPS,
  MAX_LAG_MS,
  MIN_CPS,
  REVEAL_FRAME_MS,
  fadeTail,
  incomingRate,
  nextShown,
  pruneMarks,
  revealRate,
  safeCut,
  toneTail,
  wordCut,
  isDraining,
  answerStillShowing,
  MAX_WORD,
  type RevealMark,
} from "./streamReveal";
import { STREAM_FLUSH_MS } from "./streamBatch";

describe("revealRate (SEND-MOTION v2 P1)", () => {
  it("follows the incoming rate, within a floor and a ceiling", () => {
    expect(revealRate(60, 5, false)).toBe(60);
    expect(revealRate(0, 1, false)).toBe(MIN_CPS);
    expect(revealRate(5000, 1, false)).toBe(MAX_CPS);
  });
  it("speeds up when the text trails by more than about a second", () => {
    // 60 cps coming in, 120 characters behind = 2 s: catch up at 120 cps.
    expect(revealRate(60, 120, false)).toBe((120 * 1000) / MAX_LAG_MS);
  });
  it("drains the rest quickly once the answer is done", () => {
    expect(revealRate(60, 90, true)).toBe((90 * 1000) / DRAIN_MS);
  });
});

describe("nextShown / incomingRate", () => {
  it("moves at least a character per step while behind and never past the text", () => {
    expect(nextShown(10, 40, REVEAL_FRAME_MS, 1)).toBe(11);
    expect(nextShown(10, 12, REVEAL_FRAME_MS, 600)).toBe(12);
    expect(nextShown(12, 12, REVEAL_FRAME_MS, 600)).toBe(12);
  });
  it("smooths the rate batch to batch", () => {
    expect(incomingRate(0, 6, 100)).toBe(60);
    expect(incomingRate(60, 12, 100)).toBeCloseTo(60 * 0.7 + 120 * 0.3);
    expect(incomingRate(60, 0, 100)).toBe(60);
  });
});

describe("the reveal against a model writing in batches", () => {
  // ~15 tokens/s ≈ 60 characters/s, flushed every STREAM_FLUSH_MS: 6-7 characters land at once.
  function simulate(totalChars: number, cps: number) {
    let received = 0;
    let shown = 0;
    let rate = 0;
    let lastBatch = 0;
    let maxStep = 0;
    let maxLagMs = 0;
    let t = 0;
    const steps: number[] = [];
    while (received < totalChars) {
      t += REVEAL_FRAME_MS;
      if (t - lastBatch >= STREAM_FLUSH_MS) {
        const grew = Math.min(totalChars - received, Math.round((cps * (t - lastBatch)) / 1000));
        rate = incomingRate(rate, grew, t - lastBatch);
        received += grew;
        lastBatch = t;
      }
      const before = shown;
      shown = nextShown(shown, received, REVEAL_FRAME_MS, revealRate(rate, received - shown, false));
      steps.push(shown - before);
      maxStep = Math.max(maxStep, shown - before);
      if (rate > 0) maxLagMs = Math.max(maxLagMs, ((received - shown) * 1000) / rate);
    }
    let drainMs = 0;
    while (shown < received) {
      drainMs += REVEAL_FRAME_MS;
      shown = nextShown(shown, received, REVEAL_FRAME_MS, revealRate(rate, received - shown, true));
    }
    return { maxStep, maxLagMs, drainMs, steps };
  }

  it("reveals a few characters per frame instead of a batch every 110 ms", () => {
    const r = simulate(1200, 60);
    // A batch is ~6-7 characters; the reveal never jumps by a whole batch in one frame.
    expect(r.maxStep).toBeLessThan(6);
    // Steady: after warm-up, nearly every frame moves.
    const moving = r.steps.slice(10).filter((s) => s > 0).length / r.steps.slice(10).length;
    expect(moving).toBeGreaterThan(0.9);
  });
  it("never trails the model by much more than a second, and drains fast at the end", () => {
    const r = simulate(1200, 60);
    expect(r.maxLagMs).toBeLessThanOrEqual(MAX_LAG_MS + STREAM_FLUSH_MS);
    expect(r.drainMs).toBeLessThanOrEqual(DRAIN_MS + 2 * REVEAL_FRAME_MS);
  });
  it("keeps up with a fast model (a burst reads, the lag stays bounded)", () => {
    const r = simulate(3000, 400);
    expect(r.maxLagMs).toBeLessThanOrEqual(MAX_LAG_MS + STREAM_FLUSH_MS);
  });
});

describe("safeCut", () => {
  it("never shows half a citation, an opening marker or half a surrogate pair", () => {
    const text = "Canberra is the capital [12] and **Sydney** is not 🐗.";
    expect(text.slice(0, safeCut(text, text.indexOf("[12]") + 2))).toBe("Canberra is the capital ");
    expect(text.slice(0, safeCut(text, text.indexOf("**") + 1))).toBe("Canberra is the capital [12] and ");
    expect(text.slice(0, safeCut(text, text.indexOf("**") + 2))).toBe("Canberra is the capital [12] and ");
    const boar = text.indexOf("🐗");
    expect(safeCut(text, boar + 1)).toBe(boar);
    expect(safeCut(text, 5)).toBe(5);
    expect(safeCut(text, 999)).toBe(text.length);
  });
});

describe("fadeTail / pruneMarks", () => {
  const marks: RevealMark[] = [
    { end: 10, at: -Infinity },
    { end: 14, at: 1000 },
    { end: 18, at: 1000 + FADE_MS / 2 },
    { end: 22, at: 1000 + FADE_MS - 1 },
  ];
  it("newest third in tertiary, the next third in secondary, older ones primary", () => {
    const now = 1000 + FADE_MS;
    expect(fadeTail(marks, 22, now)).toEqual({ tertiary: 4, secondary: 4 });
    expect(fadeTail(marks, 22, now + FADE_MS)).toEqual({ tertiary: 0, secondary: 0 });
  });
  it("keeps the marks still fading plus the one where they start", () => {
    const now = 1000 + FADE_MS + 10;
    expect(pruneMarks(marks, now)).toEqual(marks.slice(1));
    expect(pruneMarks(marks, now + 10 * FADE_MS)).toEqual(marks.slice(-1));
  });
});

describe("toneTail", () => {
  it("splits the newest characters into the fading tones, across parts", () => {
    const parts = [
      { type: "text", text: "Canberra is " },
      { type: "bold", text: "the capital" },
    ];
    expect(toneTail(parts, { tertiary: 3, secondary: 5 })).toEqual([
      { part: { type: "text", text: "Canberra is " }, tone: null },
      { part: { type: "bold", text: "the" }, tone: null },
      { part: { type: "bold", text: " capi" }, tone: "secondary" },
      { part: { type: "bold", text: "tal" }, tone: "tertiary" },
    ]);
    expect(toneTail(parts, { tertiary: 14, secondary: 0 }).map((p) => [p.part.text, p.tone])).toEqual([
      ["Canberra ", null],
      ["is ", "tertiary"],
      ["the capital", "tertiary"],
    ]);
  });
  it("a citation is one piece; nothing fading leaves the parts as they are", () => {
    const parts = [{ type: "text", text: "Yes " }, { type: "cite", n: 1 } as { type: string; text?: string }];
    expect(toneTail(parts, { tertiary: 2, secondary: 0 })).toEqual([
      { part: parts[0], tone: null },
      { part: parts[1], tone: "tertiary" },
    ]);
    expect(toneTail(parts, { tertiary: 0, secondary: 0 }).every((p) => p.tone === null)).toBe(true);
  });
});

describe("wordCut (cost option 1: the screen moves a word at a time)", () => {
  const text = "Canberra is the capital of Australia.";
  it("cuts at the end of the last whole word", () => {
    expect(text.slice(0, wordCut(text, 11))).toBe("Canberra is");
    expect(text.slice(0, wordCut(text, 13))).toBe("Canberra is");
    expect(text.slice(0, wordCut(text, 15))).toBe("Canberra is the");
    expect(text.slice(0, wordCut(text, 3))).toBe("");
  });
  it("shows everything once reached, and a very long word by characters", () => {
    expect(wordCut(text, 999)).toBe(text.length);
    const url = "see " + "x".repeat(MAX_WORD + 6) + " end";
    expect(wordCut(url, 4 + MAX_WORD + 2)).toBe(4 + MAX_WORD + 2);
  });
  it("keeps safeCut's rules", () => {
    const cite = "It is Canberra [12] today";
    expect(cite.slice(0, wordCut(cite, cite.indexOf("[12]") + 2))).toBe("It is Canberra");
  });
});

describe("the end waits for the text (Prism CX-12)", () => {
  it("a block drains after its stream is over, until the reveal settles", () => {
    expect(isDraining(true, false)).toBe(false);
    expect(isDraining(false, false)).toBe(true);
    expect(isDraining(false, true)).toBe(false);
  });
  it("the answer counts as running while any of its blocks drains", () => {
    expect(answerStillShowing(false, {})).toBe(false);
    expect(answerStillShowing(false, { fast: true })).toBe(true);
    expect(answerStillShowing(false, { fast: false, deep: false })).toBe(false);
    expect(answerStillShowing(true, {})).toBe(true);
  });
});
