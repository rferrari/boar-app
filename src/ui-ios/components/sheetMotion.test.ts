import { describe, expect, it } from "vitest";
import { sheetAnimates, sheetDragCloses, sheetTravel } from "./sheetMotion";

describe("sheetAnimates (perf audit #12: a Sheet mounted closed)", () => {
  it("a sheet mounted closed does nothing: no native animation, no focus handed back", () => {
    expect(sheetAnimates(false, false)).toBe(false);
  });

  it("opening, and closing one that is on screen, animate", () => {
    expect(sheetAnimates(true, false)).toBe(true);
    expect(sheetAnimates(true, true)).toBe(true);
    expect(sheetAnimates(false, true)).toBe(true);
  });
});

describe("sheetTravel (TR-4: slide by the sheet's own height)", () => {
  it("slides by the measured height, so a tall sheet starts fully off screen", () => {
    expect(sheetTravel(620, 844, false)).toBe(620);
  });
  it("uses the window height before the first layout", () => {
    expect(sheetTravel(0, 844, false)).toBe(844);
  });
  it("does not slide under reduce motion", () => {
    expect(sheetTravel(620, 844, true)).toBe(0);
  });
});

describe("sheetDragCloses (TR-12: the grabber drags)", () => {
  it("closes past a third of the sheet", () => {
    expect(sheetDragCloses(210, 0, 600)).toBe(true);
    expect(sheetDragCloses(190, 0, 600)).toBe(false);
  });
  it("closes on a downward flick, not on a tap-sized move", () => {
    expect(sheetDragCloses(40, 1200, 600)).toBe(true);
    expect(sheetDragCloses(8, 1200, 600)).toBe(false);
  });
  it("never closes on an upward drag", () => {
    expect(sheetDragCloses(-300, -2000, 600)).toBe(false);
  });
});
