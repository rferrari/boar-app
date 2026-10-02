import { describe, expect, it } from "vitest";
import { META_SEPARATOR, metaItems } from "./metaItems";

describe("metaItems", () => {
  it("drops falsy and blank facts so callers can inline conditions", () => {
    expect(metaItems(["978 MB", false, null, undefined, "  ", "~4 min"])).toEqual(["978 MB", "~4 min"]);
  });

  it("joins with a middle dot", () => {
    expect(metaItems(["1.4 s", "16 tok/s"]).join(META_SEPARATOR)).toBe("1.4 s · 16 tok/s");
  });
});
