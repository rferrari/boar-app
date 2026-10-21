import { describe, expect, it } from "vitest";
import { attributeCitations } from "./citations";

const src = (body: string) => ({ chunkId: "c1", docId: "d1", title: "Sahara", body, score: 1, matchType: "lexical" as const });

describe("attributeCitations keeps a decimal number whole", () => {
  it("cites after the sentence, never inside 9.4", () => {
    const s = src("The Sahara is the largest hot desert in the world, covering about 9.4 million square kilometres of North Africa.");
    const out = attributeCitations("The Sahara is the largest hot desert in the world, covering about 9.4 million square kilometres.", [s]);
    expect(out.text).not.toMatch(/\d \[\d+\]\.\d/);
    expect(out.text).toBe("The Sahara is the largest hot desert in the world, covering about 9.4 million square kilometres [1].");
  });
  it("still treats a period before a new sentence as its end", () => {
    const s = src("The Sahara covers about 9.4 million square kilometres. It spans eleven countries of North Africa.");
    const out = attributeCitations("The Sahara covers about 9.4 million square kilometres. It spans eleven countries of North Africa.", [s]);
    expect(out.text).toBe("The Sahara covers about 9.4 million square kilometres [1]. It spans eleven countries of North Africa [1].");
  });
});
