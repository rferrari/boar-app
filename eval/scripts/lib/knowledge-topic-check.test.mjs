import { describe, expect, it } from "vitest";
import { checkKnowledgeTopic } from "./knowledge-topic-check.mjs";

describe("checkKnowledgeTopic", () => {
  it("fails an off-topic cited source (gate a9f156c: 1513 Marash for the 1906 earthquake)", () => {
    const r = checkKnowledgeTopic({ queryId: "kt-001", citedTitles: ["1513 Marash earthquake"], retrievedTitles: ["1513 Marash earthquake"] });
    expect(r.pass).toBe(false);
    expect(r.target).toBe(true);
  });
  it("passes on-topic citations and only warns about uncited retrieved junk", () => {
    const r = checkKnowledgeTopic({ queryId: "kt-003", citedTitles: ["1970 FIFA World Cup"], retrievedTitles: ["1970 FIFA World Cup", "Andy Roberts (cricketer)"] });
    expect(r.pass).toBe(true);
    expect(r.warnings[0]).toMatch(/Andy Roberts/);
  });
  it("an answer that cites nothing passes (nothing off topic is shown)", () => {
    expect(checkKnowledgeTopic({ queryId: "kt-002", citedTitles: [], retrievedTitles: [] }).pass).toBe(true);
  });
});
