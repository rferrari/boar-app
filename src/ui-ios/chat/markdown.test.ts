import { describe, it, expect } from "vitest";
import { parseInline, parseMarkdown, sameBlock } from "./markdown";

describe("parseInline", () => {
  it("parses bold, italic and code", () => {
    expect(parseInline("a **b** *c* `d`", 0)).toEqual([
      { type: "text", text: "a " },
      { type: "bold", text: "b" },
      { type: "text", text: " " },
      { type: "italic", text: "c" },
      { type: "text", text: " " },
      { type: "code", text: "d" },
    ]);
  });

  it("turns [n], [1, 2] and [1-3] into citations within the sources", () => {
    const cites = (s: string) => parseInline(s, 3).filter((i) => i.type === "cite").map((i) => (i as any).n);
    expect(cites("x [1][2].")).toEqual([1, 2]);
    expect(cites("x [1, 3].")).toEqual([1, 3]);
    expect(cites("x [1-3].")).toEqual([1, 2, 3]);
  });

  it("leaves out-of-range or unknown brackets as text", () => {
    expect(parseInline("see [4]", 3)).toEqual([{ type: "text", text: "see [4]" }]);
    expect(parseInline("see [1]", 0)).toEqual([{ type: "text", text: "see [1]" }]);
    expect(parseInline("array[0]", 3)).toEqual([{ type: "text", text: "array[0]" }]);
  });

  it("drops a literal [n] copied from the prompt, even while streaming", () => {
    expect(parseInline("Canberra is the capital [n].", 2)).toEqual([{ type: "text", text: "Canberra is the capital." }]);
    expect(parseInline("A [N] and [1]", 1)).toEqual([
      { type: "text", text: "A and " },
      { type: "cite", n: 1 },
    ]);
  });

  it("keeps an unclosed ** as text while streaming", () => {
    expect(parseInline("so **important", 0)).toEqual([{ type: "text", text: "so **important" }]);
  });
});

describe("parseMarkdown", () => {
  it("parses headings, bullets, ordered items and joins paragraph lines", () => {
    const blocks = parseMarkdown("## Title\nLine one\nline two\n\n- a\n* b\n1. first\n2) second", 0);
    expect(blocks.map((b) => b.type)).toEqual(["heading", "paragraph", "bullet", "bullet", "ordered", "ordered"]);
    expect(blocks[1]).toEqual({ type: "paragraph", inlines: [{ type: "text", text: "Line one line two" }] });
    expect(blocks[5]).toMatchObject({ type: "ordered", n: 2 });
  });

  it("parses a closed code fence", () => {
    expect(parseMarkdown("x\n```ts\nconst a = 1;\n```\ny", 0)).toEqual([
      { type: "paragraph", inlines: [{ type: "text", text: "x" }] },
      { type: "code", language: "ts", text: "const a = 1;" },
      { type: "paragraph", inlines: [{ type: "text", text: "y" }] },
    ]);
  });

  it("shows an unclosed fence as text while streaming", () => {
    const blocks = parseMarkdown("```py\nprint(1)", 0);
    expect(blocks.every((b) => b.type === "paragraph")).toBe(true);
  });

  it("reads a table as header: value pairs per row", () => {
    const blocks = parseMarkdown("| Algo | Leader |\n|---|:--:|\n| Raft | yes [1] |\n| Paxos | no |", 1);
    expect(blocks).toHaveLength(1);
    const table = blocks[0];
    if (table.type !== "table") throw new Error("expected table");
    expect(table.rows).toHaveLength(2);
    expect(table.rows[0][0]).toEqual({ header: "Algo", cells: [{ type: "text", text: "Raft" }] });
    expect(table.rows[0][1].cells).toEqual([
      { type: "text", text: "yes " },
      { type: "cite", n: 1 },
    ]);
  });
});

describe("sameBlock", () => {
  it("keeps the finished blocks equal while the last one streams", () => {
    const before = parseMarkdown("# Title\n\nFirst paragraph [1].\n\n- one\n- tw", 2);
    const after = parseMarkdown("# Title\n\nFirst paragraph [1].\n\n- one\n- two", 2);
    expect(before.length).toBe(after.length);
    const same = before.map((b, i) => sameBlock(b, after[i]));
    expect(same).toEqual([true, true, true, false]);
  });

  it("tells apart blocks that differ only in type or number", () => {
    const [a] = parseMarkdown("1. step", 0);
    const [b] = parseMarkdown("2. step", 0);
    const [c] = parseMarkdown("- step", 0);
    expect(sameBlock(a, b)).toBe(false);
    expect(sameBlock(a, c)).toBe(false);
  });

  it("sees a citation become valid once the source arrives", () => {
    const [a] = parseMarkdown("Text [2].", 1);
    const [b] = parseMarkdown("Text [2].", 2);
    expect(sameBlock(a, b)).toBe(false);
  });
});
