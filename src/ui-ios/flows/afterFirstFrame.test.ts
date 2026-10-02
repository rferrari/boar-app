import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it, vi } from "vitest";
import { afterFirstFrame, type FrameScheduler } from "./afterFirstFrame";

function fakeScheduler() {
  const frames: (() => void)[] = [];
  const interactions: (() => void)[] = [];
  const s: FrameScheduler = {
    nextFrame: (cb) => frames.push(cb) - 1,
    cancelFrame: (id) => {
      frames[id] = () => {};
    },
    afterInteractions: (task) => {
      const i = interactions.push(task) - 1;
      return { cancel: () => void (interactions[i] = () => {}) };
    },
  };
  return { s, frames, interactions };
}

describe("afterFirstFrame (boot: the tile catalog waits for the chat, Boar)", () => {
  it("runs the task after the next frame and the running interactions, never synchronously", () => {
    const { s, frames, interactions } = fakeScheduler();
    const task = vi.fn();
    afterFirstFrame(task, s);
    expect(task).not.toHaveBeenCalled();
    frames.forEach((f) => f());
    expect(task).not.toHaveBeenCalled();
    interactions.forEach((f) => f());
    expect(task).toHaveBeenCalledTimes(1);
  });

  it("an unmount before it ran cancels it", () => {
    const { s, frames, interactions } = fakeScheduler();
    const task = vi.fn();
    const cancel = afterFirstFrame(task, s);
    frames.forEach((f) => f());
    cancel();
    interactions.forEach((f) => f());
    expect(task).not.toHaveBeenCalled();
  });

  it("App.tsx calls loadTileCatalog only through afterFirstFrame, never in the mount effect", () => {
    const app = readFileSync(join(__dirname, "../App.tsx"), "utf8");
    const calls = app.match(/loadTileCatalog\(/g) ?? [];
    const deferred = app.match(/afterFirstFrame\(\s*\(\)\s*=>\s*(void\s+)?loadTileCatalog\(/g) ?? [];
    expect(calls.length).toBeGreaterThan(0);
    expect(deferred.length).toBe(calls.length);
  });
});
