import { describe, expect, it } from "vitest";
import { checkProposalDenial, proposalIds } from "./proposal-denial.mjs";

const top = (id, title) => [`Ethereum EIPs/ERCs: ${id}: ${title}`, "Ethereum", "Ethereum"];
describe("proposal denial (Boar 2026-09-27)", () => {
  it("reads EIP/ERC/BIP identifiers", () => {
    expect(proposalIds("Qual a diferença entre tokens ERC-20 e ERC-721? E o BIP 32?")).toEqual([["ERC", "20"], ["ERC", "721"], ["BIP", "32"]]);
  });
  it("fails the dddd8a8 denials when the page is in the top-3", () => {
    const pt = { query: "Qual é o saldo efetivo máximo de um validador do Ethereum depois da EIP-7251?", rawRetrievedTitles: top("EIP-7251", "Increase the MAX_EFFECTIVE_BALANCE"),
      answer: "Não há informação disponível sobre um saldo máximo para validadores do Ethereum após a EIP-7251, pois essa EIP não existe ou não foi publicada oficialmente no conjunto de fontes." };
    expect(checkProposalDenial(pt).pass).toBe(false);
    const en = { query: "O que a EIP-7702 permite que uma conta comum do Ethereum (EOA) faça?", rawRetrievedTitles: top("EIP-7702", "Set Code for EOAs"),
      answer: "There is no known EIP-7702 in the Ethereum ecosystem. The referenced EIP-7702 does not exist in official Ethereum documentation." };
    expect(checkProposalDenial(en).pass).toBe(false);
  });
  it("passes a real answer, and a denial when the page is not in the top-3", () => {
    const ok = { query: "O que é a EIP-4844?", rawRetrievedTitles: top("EIP-4844", "Shard Blob Transactions"), answer: "A EIP-4844 introduz transações com blobs [1]. Não existe outra forma de postar blobs antes dela." };
    expect(checkProposalDenial(ok).pass).toBe(true);
    expect(checkProposalDenial({ ...ok, answer: "Essa EIP não existe." }).pass).toBe(false);
    expect(checkProposalDenial({ ...ok, rawRetrievedTitles: ["Ethereum"], answer: "A EIP-4844 não existe." }).pass).toBe(true);
    expect(checkProposalDenial({ query: "O que é proof of stake?", answer: "não existe" })).toBeNull();
  });
});
