import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(join(__dirname, rel), "utf8");

describe("chat perf guards (LAYOUT-AUDIT)", () => {
  it("#31: the places minute clock runs only where local time shows; rows are memoized with the place bound", () => {
    const src = read("PlacesCard.tsx");
    expect(src).toMatch(/useMinuteClock\(deviceClockApplies\(r\.area\)\)/);
    expect(src).toMatch(/const PlaceRow = memo\(/);
    expect(src).toMatch(/onOpen=\{setOpenPlace\}/);
  });

  it("#36: initModels is stable (no [t, locale] deps) and never runs twice at once", () => {
    const src = read("../ChatScreen.tsx");
    const init = src.match(/const initModels = useCallback\(async \(\) => \{[\s\S]*?\n  \}, \[(.*?)\]\);/);
    expect(init).not.toBeNull();
    expect(init![1]).toBe("");
    expect(init![0]).toMatch(/if \(initInFlight\.current\) return;/);
    expect(init![0]).toMatch(/finally \{\s*initInFlight\.current = false;/);
  });

  it("GFXINFO: the chat's only loops are the library spinner and the research card's ambient, which stops once the text streams", () => {
    const files = [...readdirSync(__dirname).filter((f) => f.endsWith(".tsx")), "../ChatScreen.tsx", "../ChatHeader.tsx"];
    const loops = files.filter((f) => /Animated\.loop|withRepeat/.test(read(f)));
    expect(loops.sort()).toEqual(["AssistantMessage.tsx", "ResearchCard.tsx"]);
    const am = read("AssistantMessage.tsx");
    expect(am.match(/Animated\.loop/g)).toHaveLength(1);
    expect(am).toMatch(/const turning = !reduceMotion && !still;/);
    // LIVE_RESEARCH: one loop drives the shimmer and the pulse, only on the live card, never under reduce motion;
    // the live card goes (it folds into the pill) at the answer's first words (stepsCardShown).
    const rc = read("ResearchCard.tsx");
    expect(rc.match(/Animated\.loop/g)).toHaveLength(1);
    expect(rc).toMatch(/const ambient = useAmbient\(live && !reduceMotion\);/);
    expect(rc).toMatch(/duration: t\.motion\.loop\.sweep/);
    expect(am).toMatch(/const fastSteps = !isDeepen && stepsShown && !props\.waitingLibrary;/);
    expect(am).toMatch(/const deepLive = isDeepen && stepsShown;/);
    expect(am).toMatch(/live=\{fastSteps\}/);
    expect(am).toMatch(/live=\{deepLive\}/);
  });
});
