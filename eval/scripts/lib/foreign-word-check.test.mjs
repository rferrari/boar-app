import { describe, expect, it } from "vitest";
import { checkForeignWord } from "./foreign-word-check.mjs";

describe("trv-007 foreign word in the right script", () => {
  it("fails the Khmer answer of 39a2508 and passes Thai script or romanization", () => {
    expect(checkForeignWord({ queryId: "trv-007-pt", answer: 'Obrigado em tailandês é "សួស្តី" (sàa-ssàa), e não muda com o sexo.' }).pass).toBe(false);
    const ok = checkForeignWord({ queryId: "trv-007", answer: 'You say "ขอบคุณ" (khop khun). Men add khrap, women add kha.' });
    expect(ok.pass).toBe(true);
    expect(ok.warnings).toEqual([]);
    expect(checkForeignWord({ queryId: "trv-007", answer: '"ขอบคุณ" (khop khun) is the same for men and women.' }).warnings).toContain("no gender particle (khrap / kha)");
    expect(checkForeignWord({ queryId: "trv-001", answer: "x" })).toBeNull();
  });
});

import { checkAllergyTranslation } from "./foreign-word-check.mjs";
describe("lng-009 allergy sentence in French (blocking)", () => {
  it("fails aa0729a's 'au peanut', passes the refusal and the right sentence", () => {
    expect(checkAllergyTranslation({ queryId: "lng-009", answer: "This answer is not from an offline source on this phone; check it before relying on it.\n\nJe suis allergique au peanut." }).pass).toBe(false);
    expect(checkAllergyTranslation({ queryId: "lng-009", answer: "I don't have a reliable offline source on this, so I won't give health advice from memory. In an emergency, call 112." }).pass).toBe(true);
    expect(checkAllergyTranslation({ queryId: "lng-009", answer: "In French: « Je suis allergique aux arachides. » (or « aux cacahuètes »)." }).pass).toBe(true);
    expect(checkAllergyTranslation({ queryId: "lng-008", answer: "x" })).toBeNull();
  });
});
