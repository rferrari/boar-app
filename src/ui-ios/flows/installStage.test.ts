import { describe, expect, it } from "vitest";
import { installStage, stageSwapAnimates } from "./installStage";

describe("install step stages (Prism L3-2)", () => {
  it("import/download -> index is a stage change, so the step crossfades", () => {
    expect(installStage("waiting")).toBe("transfer");
    expect(installStage("building")).not.toBe(installStage("waiting"));
  });
  it("a failed index and its retry stay on the same stage (no swap)", () => {
    expect(installStage("error")).toBe(installStage("building"));
  });
  it("a step that opens with everything in does not animate a swap it never showed", () => {
    expect(stageSwapAnimates(true)).toBe(false);
    expect(stageSwapAnimates(false)).toBe(true);
  });
});
