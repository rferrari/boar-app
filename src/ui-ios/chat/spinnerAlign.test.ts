import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, it, expect } from "vitest";

// Spinners beside text sit on the text's optical line (LineSlot), not centred on the wrapped block
// (Prism CH-15, CH-16), and loading is drawn in the accent, never amber (provenance).
const files = readdirSync(__dirname).filter((f) => f.endsWith(".tsx"));

describe("chat spinners", () => {
  it("every StepSpinner / ActivityIndicator in a row sits in a LineSlot", () => {
    const loose: string[] = [];
    for (const f of files) {
      const lines = readFileSync(join(__dirname, f), "utf8").split("\n");
      lines.forEach((line, i) => {
        if (!/<(StepSpinner|ActivityIndicator)\b/.test(line)) return;
        const before = lines.slice(Math.max(0, i - 3), i).join("\n");
        if (!/<LineSlot\b/.test(before)) loose.push(`${f}:${i + 1}`);
      });
    }
    expect(loose).toEqual([]);
  });

  it("no spinner in the field (amber) colour", () => {
    const amber = files.filter((f) => /<ActivityIndicator[^>]*field\.solid/s.test(readFileSync(join(__dirname, f), "utf8")));
    expect(amber).toEqual([]);
  });
});
