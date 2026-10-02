import { describe, expect, it } from "vitest";
import { DEFAULT_APPEARANCE, resolveScheme } from "./scheme";

describe("resolveScheme", () => {
  it("honors an explicit choice regardless of the OS", () => {
    expect(resolveScheme("light", "dark")).toBe("light");
    expect(resolveScheme("dark", "light")).toBe("dark");
  });

  it("follows the OS when set to system", () => {
    expect(resolveScheme("system", "light")).toBe("light");
    expect(resolveScheme("system", "dark")).toBe("dark");
  });

  it("falls back to dark when the OS reports nothing", () => {
    expect(resolveScheme("system", null)).toBe("dark");
    expect(resolveScheme("system", undefined)).toBe("dark");
  });

  it("opens dark by default, even on a light OS", () => {
    expect(DEFAULT_APPEARANCE).toBe("dark");
    expect(resolveScheme(DEFAULT_APPEARANCE, "light")).toBe("dark");
  });
});
