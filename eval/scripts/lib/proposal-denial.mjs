// Blocking since 2026-09-27 (Boar, after dddd8a8): an answer must not deny that an EIP/ERC/BIP exists when that
// proposal's page is in the search top-3 ("EIP-7251 não existe", "There is no known EIP-7702").

/** Proposal identifiers named in a question: [["EIP", "1559"], ...]. */
export const proposalIds = (query) => [...String(query ?? "").matchAll(/\b(EIP|ERC|BIP)[- ]?(\d+)\b/gi)].map((m) => [m[1].toUpperCase(), m[2]]);

const DENIAL = /(n[ãa]o existe|inexistente|n[ãa]o foi (publicad|oficializad|aprovad)|n[ãa]o h[áa] (nenhuma|registro|informa[çc][ãa]o)[^.]*\b(sobre|de) (a |uma )?(EIP|ERC|BIP|essa|esta)|does(n't| not) exist|no known (EIP|ERC|BIP)|there is no (known |such )?(EIP|ERC|BIP)|not (a |an )?(real|recognized|valid|official) (EIP|ERC|BIP|proposal)|n[ãa]o (é|foi) (uma )?(EIP|ERC|BIP|proposta) (real|reconhecid|v[áa]lid|oficial))/i;

/**
 * @param {{ query?: string, answer?: string, rawRetrievedTitles?: string[] }} row
 * @returns {null | { pass: boolean, ids: string[], inTop3: string[], denial?: string }}  null = the question names no proposal
 */
export function checkProposalDenial(row) {
  const ids = proposalIds(row.query);
  if (!ids.length) return null;
  const top3 = (row.rawRetrievedTitles ?? []).slice(0, 3);
  const inTop3 = ids.filter(([k, n]) => top3.some((t) => new RegExp(`\\b${k}-${n}:`).test(t))).map((x) => x.join("-"));
  if (!inTop3.length) return { pass: true, ids: ids.map((x) => x.join("-")), inTop3 };
  // The denying sentence must be about the proposal: it names it, or says "essa EIP" / "this EIP" / "a proposta".
  const about = new RegExp(`\\b(${ids.map(([k, n]) => `${k}[- ]?${n}`).join("|")})\\b|\\b(essa|esta|this|that|the|a) (EIP|ERC|BIP|proposta|proposal)\\b`, "i");
  const sentence = String(row.answer ?? "").split(/(?<=[.!?])\s+|\n+/).find((s) => DENIAL.test(s) && about.test(s));
  return sentence ? { pass: false, ids: ids.map((x) => x.join("-")), inTop3, denial: sentence.trim().slice(0, 200) } : { pass: true, ids: ids.map((x) => x.join("-")), inTop3 };
}
