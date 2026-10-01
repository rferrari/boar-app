import { describe, expect, it } from "vitest";
import { PACKS, SESSIONS, packsOf } from "./catalog";
import { initialSanctuary, pickEntrance, sanctuaryReducer as r, sessionAcquired, statsOf } from "./sanctuary.pure";

describe("Sanctuary preview state", () => {
  it("acquires a pack once and shows its session's boar", () => {
    const s1 = r(initialSanctuary, { type: "acquire", packId: "synth-design-101" });
    expect(sessionAcquired(s1, "music")).toBe(true);
    expect(sessionAcquired(s1, "combat")).toBe(false);
    expect(r(s1, { type: "acquire", packId: "synth-design-101" })).toBe(s1);
  });

  it("ignores unknown packs", () => {
    expect(r(initialSanctuary, { type: "acquire", packId: "nope" })).toBe(initialSanctuary);
    expect(r(initialSanctuary, { type: "tip", packId: "nope", amount: 10 })).toBe(initialSanctuary);
  });

  it("votes toggle, and the other vote replaces it", () => {
    let s = r(initialSanctuary, { type: "vote", packId: "solar-off-grid", vote: 1 });
    expect(s.votes["solar-off-grid"]).toBe(1);
    s = r(s, { type: "vote", packId: "solar-off-grid", vote: -1 });
    expect(s.votes["solar-off-grid"]).toBe(-1);
    s = r(s, { type: "vote", packId: "solar-off-grid", vote: -1 });
    expect(s.votes["solar-off-grid"]).toBeUndefined();
  });

  it("tips add up, and only positive amounts count", () => {
    let s = r(initialSanctuary, { type: "tip", packId: "llms-on-a-phone", amount: 10 });
    s = r(s, { type: "tip", packId: "llms-on-a-phone", amount: 5 });
    expect(s.tips["llms-on-a-phone"]).toBe(15);
    expect(r(s, { type: "tip", packId: "llms-on-a-phone", amount: 0 })).toBe(s);
    expect(r(s, { type: "tip", packId: "llms-on-a-phone", amount: -3 })).toBe(s);
  });

  it("subscribe toggles; report sticks; a draft needs a title", () => {
    let s = r(initialSanctuary, { type: "toggleSubscribe", packId: "chords-and-harmony" });
    expect(s.subscribed["chords-and-harmony"]).toBe(true);
    s = r(s, { type: "toggleSubscribe", packId: "chords-and-harmony" });
    expect(s.subscribed["chords-and-harmony"]).toBeUndefined();
    s = r(s, { type: "report", packId: "chords-and-harmony", reason: "outdated" });
    expect(s.reported["chords-and-harmony"]).toBe("outdated");
    expect(r(s, { type: "report", packId: "chords-and-harmony", reason: "spam" })).toBe(s);
    const draft = { title: "  My notes ", session: "code" as const, tags: [], documents: [], pricing: "free" as const };
    expect(r(s, { type: "saveDraft", draft: { ...draft, title: " " } })).toBe(s);
    expect(r(s, { type: "saveDraft", draft }).drafts[0].title).toBe("My notes");
  });

  it("every session has packs, and pack ids are unique", () => {
    for (const session of SESSIONS) expect(packsOf(session).length).toBeGreaterThan(0);
    expect(new Set(PACKS.map((p) => p.id)).size).toBe(PACKS.length);
  });

  it("picks an entrance in range", () => {
    expect(pickEntrance(4, () => 0)).toBe(0);
    expect(pickEntrance(4, () => 0.999)).toBe(3);
    expect(pickEntrance(4, () => 1)).toBe(3);
  });
});

describe("welcome button labels", () => {
  it("picks one of the unlocked labels, none with emojis", async () => {
    const { pickLabel, UNLOCKED_LABELS, LOCKED_LABEL } = await import("./quotes");
    expect(pickLabel(() => 0)).toBe(UNLOCKED_LABELS[0]);
    expect(pickLabel(() => 0.9999)).toBe(UNLOCKED_LABELS.at(-1));
    for (const label of [LOCKED_LABEL, ...UNLOCKED_LABELS]) expect(label).not.toMatch(/\p{Extended_Pictographic}/u);
    for (const current of UNLOCKED_LABELS) {
      for (const r of [0, 0.5, 0.9999]) expect(pickLabel(() => r, current)).not.toBe(current);
    }
  });
});

describe("pack stats", () => {
  it("adds the user's vote, report and tips to the community counts", () => {
    const pack = PACKS.find((p) => p.id === "synth-design-101")!;
    let s = r(initialSanctuary, { type: "vote", packId: pack.id, vote: 1 });
    s = r(s, { type: "tip", packId: pack.id, amount: 25 });
    s = r(s, { type: "report", packId: pack.id, reason: "wrong" });
    expect(statsOf(pack, s)).toEqual({
      up: pack.community.up + 1,
      down: pack.community.down,
      reports: pack.community.reports + 1,
      tippedBoar: pack.community.tippedBoar + 25,
    });
    expect(statsOf(pack, initialSanctuary)).toEqual(pack.community);
  });

  it("every pack has a Markdown preview with a heading", () => {
    for (const p of PACKS) expect(p.preview.startsWith("# ")).toBe(true);
  });
});
