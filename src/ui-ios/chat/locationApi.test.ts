import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { showUseLocation } from "./placesFormat";

// locationApi imports native modules, so this reads its source instead of importing it
// (same approach as components/iconA11y.test.ts).
const source = readFileSync(join(__dirname, "locationApi.ts"), "utf8");

describe("locationApi", () => {
  it("is wired to the device locator, not a null placeholder (Prism L-1)", () => {
    expect(source).toMatch(/export \{ locateForUser as locate \} from "\.\.\/\.\.\/services\/location";/);
    expect(source).not.toMatch(/locate[^=\n]*=\s*null/);
  });

  it("so 'near me' with the permission skipped in setup offers 'Use my location'", () => {
    expect(showUseLocation(true, "prompt")).toBe(true);
  });
});
