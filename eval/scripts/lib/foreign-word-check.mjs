// Fixed case trv-007 (Boar 2026-09-27, after 39a2508): a foreign word in the right language AND script. "How do I say
// thank you in Thai" answered "សួស្តី" (Khmer script, and a greeting). Thai "thank you" is ขอบคุณ (khop khun); the
// polite particle changes with the speaker's gender (khrap for men, kha for women), so "it does not change" is wrong
// (reported as a warning). Reported, not blocking, until the engine fix (Boar).
const KHMER = /[ក-៿]/;
const THAI_THANKS = /ขอบคุณ|\bk(h)?o[pb] ?k(h)?(u|oo)n\b/i;
const PARTICLE = /ครับ|ค่ะ|\bk(h)?rap\b|\bk(h)?(a|ah|ha)\b/i;

/** @returns {null | { pass: boolean, failures: string[], warnings: string[] }} */
export function checkForeignWord(row) {
  if (!/^trv-007(-pt)?$/.test(row.queryId ?? "")) return null;
  const a = String(row.answer ?? "");
  if (row.declined || /n[ãa]o encontrei|did(n't| not) find/i.test(a)) return { pass: true, failures: [], warnings: ["declined"] };
  const failures = [], warnings = [];
  if (KHMER.test(a)) failures.push("Khmer script in an answer about Thai");
  if (!THAI_THANKS.test(a)) failures.push('no Thai "thank you" (ขอบคุณ / khop khun)');
  if (!PARTICLE.test(a)) warnings.push("no gender particle (khrap / kha)");
  return { pass: failures.length === 0, failures, warnings };
}

// Fixed case lng-009 (Boar 2026-09-27, gate aa0729a answered "Je suis allergique au peanut."): an allergy sentence
// translated to French must name the allergen in French. BLOCKING. Passes when the app refuses, or when the answer
// has arachide/cacahuète; fails with "peanut" (or any English word for it) in the French sentence.
const FR_PEANUT = /arachide|cacahu[èe]te/i;
const EN_IN_FR = /\bpeanuts?\b|\bground ?nuts?\b/i;
/** @returns {null | { pass: boolean, failures: string[] }} */
export function checkAllergyTranslation(row) {
  if (row.queryId !== "lng-009") return null;
  const a = String(row.answer ?? "");
  if (row.declined || /n[ãa]o encontrei|did(n't| not) find|won't give health advice|no reliable offline source|n[ãa]o tenho uma fonte/i.test(a)) return { pass: true, failures: [] };
  // The French sentence: lines/quotes with "je suis" or "allergique"; the English question words outside it don't count.
  const french = a.split(/\n|(?<=[.!?])\s+/).filter((s) => /je suis|allergique/i.test(s)).join(" ");
  const failures = [];
  if (!french) failures.push("no French sentence");
  if (EN_IN_FR.test(french)) failures.push(`English word in the French sentence: "${french.slice(0, 80)}"`);
  if (french && !FR_PEANUT.test(french)) failures.push("no arachide/cacahuète");
  return { pass: failures.length === 0, failures };
}
