import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(join(__dirname, rel), "utf8");

describe("chat spoken names (Prism minors)", () => {
  it("CH-26: the reasoning toggle's visible label is its spoken name (no separate accessibilityLabel)", () => {
    const reasoning = read("AssistantMessage.tsx").match(/function Reasoning[\s\S]*?\n}\n/)![0];
    expect(reasoning).toMatch(/<TextAction label=\{label\}/);
    expect(reasoning).not.toMatch(/<TextAction[^>]*accessibilityLabel/);
  });
  it("CH-32: the loading strip's text is not read twice (Progress speaks it)", () => {
    // The text sits in a Swap for its crossfade (Prism L3-1); still hidden, the bar still speaks.
    expect(read("../ChatScreen.tsx")).toMatch(/importantForAccessibility="no" accessibilityElementsHidden>\s*\{loadStatus\.label\}\s*<\/Text>\s*(<\/Swap>\s*)?<Progress label=\{loadStatus\.label\}/);
    expect(read("../ChatScreen.tsx")).toMatch(/<Text variant="footnote" color="secondary" importantForAccessibility="no" accessibilityElementsHidden>\s*\{loadStatus\.label\}/);
  });
  it("CH-33: the drawer names the assistant as the header does, not a hardcoded caps word", () => {
    expect(read("../navigation/AppDrawerContent.tsx")).not.toMatch(/accessibilityLabel="BOAR"/);
  });
});
