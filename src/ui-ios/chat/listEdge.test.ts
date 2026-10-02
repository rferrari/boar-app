import { describe, expect, it } from "vitest";
import { headerVeil } from "./listEdge";

describe("headerVeil (Prism CX-3)", () => {
  it("fades the screen colour to the same colour at zero alpha, top to bottom", () => {
    expect(headerVeil("#1A1410")).toBe("linear-gradient(to bottom, rgba(26, 20, 16, 1) 0%, rgba(26, 20, 16, 0) 100%)");
  });
  it("never fades through black (no grey band on the light theme)", () => {
    const veil = headerVeil("#F7F1E8");
    expect(veil).not.toMatch(/transparent|rgba\(0, 0, 0/);
    expect(veil).toContain("rgba(247, 241, 232, 0)");
  });
});
