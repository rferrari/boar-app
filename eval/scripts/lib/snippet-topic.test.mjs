import { describe, expect, it } from "vitest";
import { checkSnippetTopic } from "./snippet-topic.mjs";

const q7 = { expect: ["Greenhouse effect", "Climate change"] };
describe("instant passage on topic (suggestions)", () => {
  it("fails the PT q7 passage about surface heating", () => {
    const r = checkSnippetTopic({ suggestion: q7, screen: { snippet: { title: "Greenhouse effect", text: "Da fonte offline (em inglês):\nSurface heating can happen from an internal heat source or come from an external source, such as a host star.", button: "[1]" } } });
    expect(r.pass).toBe(false);
  });
  it("passes the monsoon lead and a greenhouse passage; nothing to check without a passage", () => {
    expect(checkSnippetTopic({ suggestion: { expect: ["Monsoon"] }, screen: { snippet: { text: "Da fonte offline (em inglês):\nA monsoon is traditionally a seasonal reversing wind", button: "[1]" } } }).pass).toBe(true);
    expect(checkSnippetTopic({ suggestion: q7, screen: { snippet: { text: "Greenhouse gases trap heat in the atmosphere.", button: "[1]" } } }).pass).toBe(true);
    expect(checkSnippetTopic({ suggestion: q7, screen: { fast: "x" } })).toBeNull();
  });
});
