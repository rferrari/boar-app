/**
 * The small Markdown subset model answers use, parsed into blocks the chat
 * renders with design-system Text. Pure, so it is tested without React.
 */

export type Inline =
  | { type: "text"; text: string }
  | { type: "bold"; text: string }
  | { type: "italic"; text: string }
  | { type: "code"; text: string }
  /** "[n]" pointing at sources[n - 1]. */
  | { type: "cite"; n: number };

export type Block =
  | { type: "heading"; level: 1 | 2 | 3; inlines: Inline[] }
  | { type: "paragraph"; inlines: Inline[] }
  | { type: "bullet"; inlines: Inline[] }
  | { type: "ordered"; n: number; inlines: Inline[] }
  | { type: "code"; language: string; text: string }
  /** A table read as "Header: value" pairs per row, which fits a phone and reads linearly. */
  | { type: "table"; rows: { header: string; cells: Inline[] }[][] };

/** "[1]", "[1, 2]", "[1-3]" → the numbers, when all fall within the sources. */
function citationNumbers(body: string, sourceCount: number): number[] | null {
  const out: number[] = [];
  for (const part of body.split(/\s*,\s*/)) {
    const range = /^(\d+)\s*[-–]\s*(\d+)$/.exec(part);
    if (range) {
      const [a, b] = [Number(range[1]), Number(range[2])];
      if (b < a || b - a > 20) return null;
      for (let i = a; i <= b; i++) out.push(i);
    } else if (/^\d+$/.test(part)) {
      out.push(Number(part));
    } else {
      return null;
    }
  }
  return out.length > 0 && out.every((n) => n >= 1 && n <= sourceCount) ? out : null;
}

export function parseInline(text: string, sourceCount: number): Inline[] {
  const out: Inline[] = [];
  const push = (inline: Inline) => {
    const last = out[out.length - 1];
    if (inline.type === "text" && last?.type === "text") last.text += inline.text;
    else out.push(inline);
  };
  // Group 5: a literal "[n]" some models copy from the prompt's citation instruction; it is dropped.
  const re = /(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(\*[^*\s][^*\n]*\*|_[^_\s][^_\n]*_)|(\[[\d,\s\-–]+\])|([ \t]*\[n\])/gi;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) push({ type: "text", text: text.slice(last, m.index) });
    const [tok] = m;
    if (m[1]) push({ type: "code", text: tok.slice(1, -1) });
    else if (m[2]) push({ type: "bold", text: tok.slice(2, -2) });
    else if (m[3]) push({ type: "italic", text: tok.slice(1, -1) });
    else if (m[5]) {
      // skip
    } else {
      const nums = citationNumbers(tok.slice(1, -1), sourceCount);
      if (nums) nums.forEach((n) => push({ type: "cite", n }));
      else push({ type: "text", text: tok });
    }
    last = m.index + tok.length;
  }
  if (last < text.length) push({ type: "text", text: text.slice(last) });
  return out;
}

const isTableRow = (line: string) => /^\s*\|.*\|\s*$/.test(line);
const isTableRule = (line: string) => /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/.test(line);
const cellsOf = (line: string) =>
  line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((c) => c.trim());

/**
 * `sourceCount` bounds which "[n]" become citations; others stay as text.
 * An unclosed code fence (still streaming) is shown as plain text until it closes.
 */
export function parseMarkdown(text: string, sourceCount: number): Block[] {
  const blocks: Block[] = [];
  const lines = text.split("\n");
  let paragraph: string[] = [];
  const flush = () => {
    const joined = paragraph.join(" ").trim();
    if (joined) blocks.push({ type: "paragraph", inlines: parseInline(joined, sourceCount) });
    paragraph = [];
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    const fence = /^```\s*([\w-]*)\s*$/.exec(trimmed);
    if (fence) {
      const end = lines.findIndex((l, j) => j > i && l.trim().startsWith("```"));
      if (end !== -1) {
        flush();
        blocks.push({ type: "code", language: fence[1], text: lines.slice(i + 1, end).join("\n") });
        i = end;
        continue;
      }
    }

    if (isTableRow(line) && i + 1 < lines.length && isTableRule(lines[i + 1])) {
      flush();
      const headers = cellsOf(line);
      const rows: { header: string; cells: Inline[] }[][] = [];
      i += 2;
      while (i < lines.length && isTableRow(lines[i])) {
        const cells = cellsOf(lines[i]);
        rows.push(headers.map((header, c) => ({ header, cells: parseInline(cells[c] ?? "", sourceCount) })));
        i++;
      }
      i--;
      blocks.push({ type: "table", rows });
      continue;
    }

    const heading = /^(#{1,3})\s+(.*)$/.exec(trimmed);
    if (heading) {
      flush();
      blocks.push({ type: "heading", level: heading[1].length as 1 | 2 | 3, inlines: parseInline(heading[2], sourceCount) });
      continue;
    }
    const bullet = /^[-*•]\s+(.*)$/.exec(trimmed);
    if (bullet) {
      flush();
      blocks.push({ type: "bullet", inlines: parseInline(bullet[1], sourceCount) });
      continue;
    }
    const ordered = /^(\d+)[.)]\s+(.*)$/.exec(trimmed);
    if (ordered) {
      flush();
      blocks.push({ type: "ordered", n: Number(ordered[1]), inlines: parseInline(ordered[2], sourceCount) });
      continue;
    }
    if (!trimmed) {
      flush();
      continue;
    }
    paragraph.push(trimmed);
  }
  flush();
  return blocks;
}

/** Plain text of inlines, for accessibility labels. */
export function inlineText(inlines: Inline[]): string {
  return inlines.map((i) => (i.type === "cite" ? `[${i.n}]` : i.text)).join("");
}

/**
 * Same content, for the renderer's memo: parseMarkdown builds new objects on every streamed frame, and
 * only the block being written changes, so the finished blocks above it skip their re-render.
 */
export function sameBlock(a: Block, b: Block): boolean {
  return a === b || JSON.stringify(a) === JSON.stringify(b);
}
