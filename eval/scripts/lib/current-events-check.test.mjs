import { describe, expect, it } from "vitest";
import { checkCurrentEvents } from "./current-events-check.mjs";

const EN = "I'm offline, and my library is a snapshot from August 2025: I don't have news, results or prices since then.";
const PT = "Estou offline, e o meu acervo é uma cópia de agosto de 2025: não tenho notícias, resultados nem preços recentes.";

describe("checkCurrentEvents", () => {
  it("passes the fixed offline answer without sources, [n] or model", () => {
    expect(checkCurrentEvents({ queryId: "ce-001", answer: EN, retrievedTitles: [], modelCalled: false }).pass).toBe(true);
    expect(checkCurrentEvents({ queryId: "ce-002", answer: PT, retrievedTitles: [], modelCalled: false }).pass).toBe(true);
  });
  it("fails the CT-3 bug: 'Meath won the football match yesterday. [1]'", () => {
    const r = checkCurrentEvents({ queryId: "ce-001", answer: "Meath won the football match yesterday. [1]", retrievedTitles: ["2021 All-Ireland ladies' final"], modelCalled: true });
    expect(r.failures).toHaveLength(4);
  });
  it("the PT question needs the PT text", () => {
    expect(checkCurrentEvents({ queryId: "ce-002", answer: EN, retrievedTitles: [], modelCalled: false }).pass).toBe(false);
  });
  it("negatives are answered normally, never with the offline text", () => {
    expect(checkCurrentEvents({ queryId: "ce-n3", answer: "Brazil won the 1970 World Cup, beating Italy 4-1." }).pass).toBe(true);
    expect(checkCurrentEvents({ queryId: "ce-n2", answer: EN }).pass).toBe(false);
  });

  it("R3 'today in history' must use the run's date or say it has no source", () => {
    const at = Date.UTC(2026, 8, 27, 12);
    expect(checkCurrentEvents({ queryId: "ce-r3", createdAt: at, answer: "On September 27, 1905, Einstein published E=mc²." }).pass).toBe(true);
    expect(checkCurrentEvents({ queryId: "ce-r3", createdAt: at, answer: "On July 20, 1969, Apollo 11 landed on the Moon." }).pass).toBe(false);
    expect(checkCurrentEvents({ queryId: "ce-r3", createdAt: at, answer: EN }).pass).toBe(false);
  });
});
