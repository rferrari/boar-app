import { describe, it, expect } from "vitest";
import en from "../locales/en.json";
import pt from "../locales/pt.json";

// Prism lot 2, chat copy (EN and PT): labels are verb + object in one line (<= 18), the explanation on a
// support line; an empty-state body is one sentence of at most 2 lines (~39 characters a line at 343 pt).
const TWO_LINES = 78;

describe.each([
  ["en", en],
  ["pt", pt],
])("chat copy, %s", (_lang, d) => {
  it("CH-9: Go deeper is a short action; the estimate is its own fact, spoken with the action", () => {
    expect(d.chat.actions.deepen.length).toBeLessThanOrEqual(18);
    expect(d.chat.actions.deepenEst).toBe("~{{time}}");
    expect(d.chat.actions.deepenEstSpoken.startsWith(d.chat.actions.deepen)).toBe(true);
  });
  it("CH-10: the add-knowledge card's action fits a line, the reason apart", () => {
    expect(d.chat.empty.addKnowledge.length).toBeLessThanOrEqual(18);
    expect(d.chat.empty.addKnowledgeWhy.length).toBeLessThanOrEqual(18);
  });
  it("CH-30/CH-31/CH-33: verb + object, one retry verb, short drawer items", () => {
    expect(d.chat.places.useLocation).toMatch(/^(Use|Usar) /);
    expect(d.chat.places.useLocation.length).toBeLessThanOrEqual(18);
    expect(d.chat.modelError.retry).toBe(d.chat.actions.retry);
    expect(d.nav.catalog.length).toBeLessThanOrEqual(18);
  });
  it("CH-18: the places empty states are one sentence of at most two lines", () => {
    for (const body of [d.chat.places.noneBody, d.chat.places.noPackBody]) {
      expect(body.length).toBeLessThanOrEqual(TWO_LINES);
      expect(body.replace(/\.$/, "")).not.toMatch(/[.:;]/);
    }
  });
});
