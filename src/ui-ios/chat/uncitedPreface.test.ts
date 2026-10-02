import { describe, it, expect } from "vitest";
import { uncitedPreface } from "../../routing/context";
import { withoutUncitedPreface } from "./uncitedPreface";

// Compared with the engine's own sentence: a mismatch would show the warning twice again (Prism CX-5).
describe("withoutUncitedPreface (one warning, the note in the app's language)", () => {
  it("drops the engine's line, PT and EN, and the space after it", () => {
    expect(withoutUncitedPreface(`${uncitedPreface(true)}\n\nAs estações acontecem porque…`)).toBe("As estações acontecem porque…");
    expect(withoutUncitedPreface(`  ${uncitedPreface(false)}\n\nSeasons happen because…`)).toBe("Seasons happen because…");
  });
  it("while the line streams in, shows nothing rather than half of it", () => {
    expect(withoutUncitedPreface("This answer is not fr")).toBe("");
    expect(withoutUncitedPreface("Esta resposta")).toBe("");
  });
  it("leaves any other text as it is (the line only counts at the start)", () => {
    expect(withoutUncitedPreface("Seasons happen because the Earth is tilted.")).toBe("Seasons happen because the Earth is tilted.");
    expect(withoutUncitedPreface(`Seasons… ${uncitedPreface(false)}`)).toBe(`Seasons… ${uncitedPreface(false)}`);
    expect(withoutUncitedPreface("This is Canberra.")).toBe("This is Canberra.");
    expect(withoutUncitedPreface("")).toBe("");
  });
});
