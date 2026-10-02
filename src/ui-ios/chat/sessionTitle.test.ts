import { describe, it, expect } from "vitest";
import { titleFromQuestion } from "./sessionTitle";

describe("titleFromQuestion (Prism TT-1)", () => {
  it("keeps a short question whole, spaces collapsed", () => {
    expect(titleFromQuestion("  What is a   monsoon? ")).toBe("What is a monsoon?");
  });
  it("cuts a long one at a word, with an ellipsis, never inventing text", () => {
    const t = titleFromQuestion("Por que temos estações do ano diferentes no hemisfério sul e no norte?");
    expect(t).toBe("Por que temos estações do ano diferentes…");
    expect(t.length).toBeLessThanOrEqual(43);
  });
  it("cuts a single long word hard", () => {
    expect(titleFromQuestion("a".repeat(60))).toBe(`${"a".repeat(42)}…`);
  });
});
