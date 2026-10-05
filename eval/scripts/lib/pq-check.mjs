// Fixed regression case for "Which signature algorithms are quantum resistant?" (Vitalik's prompt; Prism blocker
// 2026-09-26: the app said "Grøstl, Keccak and X25519 are quantum resistant" citing an off-topic physicist).
// Deterministic, no model calls. EN and PT patterns, because the app answers in the user's language.

import { stripOfflinePreface } from "./preface.mjs";

export const PQ_QUERY_ID = "crypto-named-001";

// Classical or non-signature primitives: calling any of them a quantum-resistant signature algorithm is false.
const CLASSICAL = /(?<![\w-])(RSA(-PSS)?|DSA|ECDSA|EdDSA|Ed25519|X25519|Curve25519|ECC|elliptic[- ]curves?|curvas? el[ií]pticas?|Diffie[- ]Hellman|Schnorr|BLS|secp256k1|Keccak|SHA-?3|SHA-?256|Gr(ø|o|oe)stl|BLAKE2?|AES)\b/i;
const RESISTANT = /quantum[- ]?(resistant|safe|secure|proof)|resist(ant|s)? (to )?quantum|secure against quantum|resistentes? (a|à|ao) (computa[çc][ãa]o |ataques? )?qu[âa]ntic|seguros? contra (computadores |ataques )?qu[âa]ntic|p[óo]s-qu[âa]ntic/i;
const NEGATION = /\bnot\b|n't|\bno longer\b|vulnerable|broken|\bbreak|insecure|\bweak|threat|unlike|replace|shor|\bn[ãa]o\b|vulner[áa]ve|quebr|amea[çc]|inseguro|diferente d/i;
// "No signature algorithm is quantum resistant": false since FIPS 204/205 (2024).
const DENIAL = /\b(no|none of|nenhum)\b[^.]*\b(signature|assinatura|algorithm|algoritmo)s?\b[^.]*(quantum[- ]resistant|resistentes? (a|à) qu[âa]ntic)/i;
// Titles that are on topic for [1]: cryptography, signatures, post-quantum families.
const ON_TOPIC = /cryptograph|criptograf|signature|assinatura|post-quantum|p[óo]s-qu[âa]ntic|quantum[- ]safe|lattice|hash-based|merkle|sphincs|falcon|dilithium|ml-dsa|slh-dsa|fn-dsa|xmss|\blms\b|nist|shor|public-key|digital signature|elliptic|rsa|ethereum eips|ethereum specs/i;
const HEDGE = /\b(proven|definitive|single|universally|fully|guarantee[ds]?|widely)\b|comprovad|definitiv|garantid|amplamente/i;
const REFUSAL = /(don't|do not) support this answer|n[ãa]o sustentam esta resposta|did(n't| not) find|no (reliable |good )?(offline )?source|not (in|from) the offline library|won't answer from memory|n[ãa]o encontrei|n[ãa]o tenho (uma )?fonte|n[ãa]o est[áa] no acervo/i;
const STANDARD = /ML-DSA|Dilithium|SLH-DSA|SPHINCS\+?|Falcon|FN-DSA|XMSS|\bLMS\b|Leighton-Micali/i;

const sentences = (text) => text.split(/(?<=[.!?;:])\s+|\n+/).map((s) => s.trim()).filter(Boolean);
const citedIndexes = (text) => [...new Set([...text.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1])))];

/**
 * @param {{ answer: string, retrievedTitles?: string[] }} row
 * @returns {{ pass: boolean, failures: string[], warnings: string[] }}
 */
export function checkQuantumAnswer({ answer, retrievedTitles = [] }) {
  const failures = [], warnings = [];
  answer = answer.replace(/[’‘]/g, "'");
  const cited = citedIndexes(answer);
  const namesStandard = STANDARD.test(answer);
  for (const s of sentences(stripOfflinePreface(answer))) {
    const m = s.match(CLASSICAL);
    if (m && RESISTANT.test(s) && !NEGATION.test(s)) {
      failures.push(`false claim${cited.length ? " in a cited answer" : ""}: "${m[0]}" called quantum resistant — "${s.slice(0, 160)}"`);
    }
    // "None is universally proven / no single definitive list" is a hedge, not a denial, when the answer names the schemes.
    if (DENIAL.test(s)) {
      if (namesStandard && HEDGE.test(s)) warnings.push(`hedged: "${s.slice(0, 120)}"`);
      else failures.push(`false claim: denies that standardized quantum-resistant signatures exist — "${s.slice(0, 160)}"`);
    }
  }
  if (retrievedTitles.length && !ON_TOPIC.test(retrievedTitles[0])) failures.push(`source [1] off topic: "${retrievedTitles[0]}"`);
  for (const i of cited) {
    const t = retrievedTitles[i - 1];
    if (t === undefined) failures.push(`cites [${i}] but only ${retrievedTitles.length} sources were given`);
    else if (!ON_TOPIC.test(t)) failures.push(`cites off-topic source [${i}]: "${t}"`);
  }
  if (/\[n\]/.test(answer)) warnings.push("literal [n] placeholder instead of a source number");
  // The Compacto refuses a sourceless knowledge question (engine-routing 6e5e9b7): honest, reported apart (Boar).
  if (!retrievedTitles.length && REFUSAL.test(answer) && !STANDARD.test(answer)) {
    warnings.push("honest refusal: no offline source");
    return { pass: failures.length === 0, failures, warnings };
  }
  if (!STANDARD.test(answer)) warnings.push("names none of ML-DSA/Dilithium, SLH-DSA/SPHINCS+, Falcon/FN-DSA, XMSS, LMS");
  return { pass: failures.length === 0, failures, warnings };
}
