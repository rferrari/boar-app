import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

// Selecting an OptionCard changes colours only (Iris TR-1/TR-2, Prism S1-2): no piece mounts on
// selection and the title never refits, so neither card moves or resizes.
const src = readFileSync(join(__dirname, "OptionCard.tsx"), "utf8");

describe("OptionCard keeps its layout across selection", () => {
  it("does not refit the title", () => {
    expect(src).not.toMatch(/adjustsFontSizeToFit(?!\))/);
  });
  it("does not mount the check or the radio dot on selection", () => {
    expect(src).not.toMatch(/\b(selected|on)\s*&&\s*[(<]/);
  });
  it("animates the selection cues with the `change` role", () => {
    expect(src).toMatch(/colorTransition\(\["borderColor", "backgroundColor"\]\)/);
  });
});
