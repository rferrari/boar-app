import { describe, expect, it, vi } from "vitest";

vi.mock("react-native", () => ({ Platform: { select: (o: Record<string, string>) => o.default } }));

describe("fontFamilyFor", () => {
  it("maps each weight to a family we actually bundle", async () => {
    const { fontFamilyFor, BUNDLED_FAMILIES } = await import("./fonts");
    const bundled = new Set<string>(BUNDLED_FAMILIES);
    for (const face of ["display", "text"] as const) {
      for (const w of [400, 500, 600, 700, 800] as const) {
        expect(bundled.has(fontFamilyFor(face, w)), `${face} ${w}`).toBe(true);
      }
    }
    expect(fontFamilyFor("display", 800)).toBe("Baloo2_800ExtraBold");
    expect(fontFamilyFor("display", 600)).toBe("Baloo2_700Bold");
    expect(fontFamilyFor("text", 500)).toBe("Lexend_500Medium");
    expect(fontFamilyFor("code", 400)).toBe("monospace");
  });
});
