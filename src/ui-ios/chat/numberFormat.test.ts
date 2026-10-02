import { describe, it, expect } from "vitest";
import { numberFormat } from "./numberFormat";

describe("numberFormat (audit #23)", () => {
  it("reuses one formatter per locale and digits", () => {
    expect(numberFormat("pt-BR", 0, 1)).toBe(numberFormat("pt-BR", 0, 1));
    expect(numberFormat("pt-BR", 0, 1)).not.toBe(numberFormat("en-US", 0, 1));
    expect(numberFormat("pt-BR", 1, 1)).not.toBe(numberFormat("pt-BR", 0, 1));
  });
  it("formats as a fresh Intl.NumberFormat would", () => {
    expect(numberFormat("pt-BR", 0, 1).format(6.2)).toBe("6,2");
    expect(numberFormat("en-US", 1, 1).format(9)).toBe("9.0");
  });
});
