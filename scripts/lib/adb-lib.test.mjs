import { describe, it, expect } from "vitest";
import { isDebuggable, isPackageListed, parseAdbDevices, selectDevice } from "./adb-lib.mjs";

describe("device selection", () => {
  const out = [
    "List of devices attached",
    "SSYLAQFILNBEKBEQ\tdevice",
    "emulator-5554\tunauthorized",
    "R58M\toffline",
    "",
  ].join("\n");

  it("parses adb devices output", () => {
    expect(parseAdbDevices(out)).toEqual([
      { serial: "SSYLAQFILNBEKBEQ", state: "device" },
      { serial: "emulator-5554", state: "unauthorized" },
      { serial: "R58M", state: "offline" },
    ]);
  });

  it("uses the single authorized device", () => {
    expect(selectDevice(parseAdbDevices(out))).toEqual({ serial: "SSYLAQFILNBEKBEQ" });
  });

  it("explains unauthorized, offline, missing and ambiguous devices", () => {
    expect(selectDevice([{ serial: "A", state: "unauthorized" }]).error).toMatch(/Allow USB debugging/);
    expect(selectDevice([{ serial: "A", state: "offline" }]).error).toMatch(/offline/);
    expect(selectDevice([]).error).toMatch(/no Android device/);
    expect(selectDevice([{ serial: "A", state: "device" }, { serial: "B", state: "device" }]).error).toMatch(/--serial/);
    expect(selectDevice([{ serial: "A", state: "device" }, { serial: "B", state: "device" }], "B")).toEqual({ serial: "B" });
    expect(selectDevice([{ serial: "A", state: "device" }], "Z").error).toMatch(/not connected/);
  });
});

describe("helpers", () => {
  it("detects debuggable, installed packages", () => {
    expect(isDebuggable("    pkgFlags=[ DEBUGGABLE HAS_CODE ALLOW_BACKUP ]")).toBe(true);
    expect(isDebuggable("    pkgFlags=[ HAS_CODE ALLOW_BACKUP ]")).toBe(false);
    expect(isPackageListed("package:team.sopa.aoair\n", "team.sopa.aoair")).toBe(true);
    expect(isPackageListed("package:team.sopa.aoair.other\n", "team.sopa.aoair")).toBe(false);
  });
});
