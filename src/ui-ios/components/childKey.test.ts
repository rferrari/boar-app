import React from "react";
import { describe, expect, it } from "vitest";
import { childKey } from "./childKey";

describe("childKey (perf audit #30: rows keyed by index remounted)", () => {
  it("keeps a row's key when the row before it leaves", () => {
    const row = (id: string) => React.createElement("row", { key: id });
    const before = React.Children.toArray([row("a"), row("b"), row("c")]).map(childKey);
    const after = React.Children.toArray([row("b"), row("c")]).map(childKey);
    expect(after).toEqual(before.slice(1));
  });

  it("falls back to the index for text", () => {
    expect(childKey("hi", 2)).toBe(2);
  });
});
