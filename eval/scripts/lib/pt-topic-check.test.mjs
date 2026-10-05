import { describe, expect, it } from "vitest";
import { checkPtTopic } from "./pt-topic-check.mjs";

describe("checkPtTopic", () => {
  it("fails the Walipini / hurricane matches", () => {
    expect(checkPtTopic({ queryId: "ptt-002", answer: "…", retrievedTitles: ["Appropedia: Walipini"] }).pass).toBe(false);
    expect(checkPtTopic({ queryId: "ptt-001", answer: "…", retrievedTitles: ["2005 Atlantic hurricane season"] }).pass).toBe(false);
  });
  it("passes an on-topic source or an honest refusal", () => {
    expect(checkPtTopic({ queryId: "ptt-001", answer: "As estações existem pela inclinação do eixo [1].", retrievedTitles: ["Season"], citedTitles: ["Season"] }).pass).toBe(true);
    expect(checkPtTopic({ queryId: "ptt-002", answer: "Não encontrei uma fonte offline confiável sobre isso.", retrievedTitles: [] }).pass).toBe(true);
  });
  it("fails a sourceless answer that does not say it has no source", () => {
    expect(checkPtTopic({ queryId: "ptt-002", answer: "Por causa da inclinação do eixo da Terra.", retrievedTitles: [] }).pass).toBe(false);
  });

  it("judges what the card shows (cited only): junk kept out of the card but no disclosure still fails (gate ea5978c)", () => {
    const r = checkPtTopic({ queryId: "ptt-001", answer: "As estações existem pela inclinação do eixo da Terra.", citedTitles: [], retrievedTitles: ["Estrela, Lisbon", "Manteigas"] });
    expect(r.pass).toBe(false);
    expect(r.failures[0]).toMatch(/answered from memory without saying so.*Estrela/);
    expect(checkPtTopic({ queryId: "ptt-001", answer: "Esta resposta não vem de uma fonte offline. As estações existem pela inclinação do eixo.", citedTitles: [], retrievedTitles: [] }).pass).toBe(true);
  });
});
