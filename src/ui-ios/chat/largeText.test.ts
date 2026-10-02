import { describe, it, expect } from "vitest";
import { chatLargeText } from "./largeText";

describe("chatLargeText (Prism CH-11, CH-12, AN-1)", () => {
  it("normal text: suggestions clamp at 2 lines, error buttons side by side", () => {
    expect(chatLargeText(false)).toEqual({ suggestionLines: 2, errorButtonBasis: "40%", snippetOverlineLines: 1 });
  });
  it("large text: the whole suggestion and the whole snippet overline (no clamp), buttons stacked", () => {
    expect(chatLargeText(true)).toEqual({ suggestionLines: undefined, errorButtonBasis: "100%", snippetOverlineLines: undefined });
  });
});
