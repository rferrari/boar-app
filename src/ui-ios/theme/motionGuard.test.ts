import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";

// No duration or delay written by hand (DS §6): components ask theme/motion.ts for a role
// (enter/exit/change/layout), or read tokens.motion.{duration,loop,delay}. The numbers live in
// motionSpec.ts only.
const ROOT = join(__dirname, "..", "..");
const ALLOWED = new Set(["ui-ios/theme/motionSpec.ts"]);
// The Android UI (src/ui-android) has its own motion conventions; this design system rule is the iOS UI's.
const OTHER_UI = "ui-android/";
const LITERAL = [
  /\b(duration|transitionDuration|animationDuration|delay)\s*:\s*\d/,
  /\.(duration|delay)\(\s*\d/,
  /\b(Animated\.delay|withDelay)\(\s*\d/,
];

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : [];
  });
}

describe("motion guard", () => {
  it("no literal animation duration or delay outside motionSpec.ts", () => {
    const hits: string[] = [];
    for (const file of sources(ROOT)) {
      const rel = relative(ROOT, file);
      if (ALLOWED.has(rel) || rel.startsWith(OTHER_UI)) continue;
      readFileSync(file, "utf8")
        .split("\n")
        .forEach((line, i) => {
          if (LITERAL.some((re) => re.test(line))) hits.push(`${rel}:${i + 1}: ${line.trim()}`);
        });
    }
    expect(hits).toEqual([]);
  });
});
