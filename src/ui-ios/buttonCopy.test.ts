import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";
import en from "./locales/en.json";
import pt from "./locales/pt.json";

// Copy fits its shape (DESIGN_SYSTEM §3, r4to): a button, action, chip or seal is one line of at most
// 18 characters in EN and PT; the explanation goes on a support line. An interpolation counts as 6
// characters (a size like "2,5 GB" or a count).
const MAX = 18;
const SLOT = "x".repeat(6);
const CONTROL = /<(Button|TextAction|Chip|Badge|EmptyState)\b/g;
const LABEL_PROP = /\b(label|actionLabel)=\{/g;

const UNBOUNDED = /\{\{\s*(city|name|region|model|label|filter|title)\s*\}\}/;
const GLYPH = /[\u2190-\u21FF\u2500-\u27BF\u2B00-\u2BFF]/;

type Dict = { [k: string]: string | Dict };

function lookup(dict: Dict, key: string): string[] {
  const walk = (k: string): string | Dict | undefined =>
    k.split(".").reduce<string | Dict | undefined>((d, p) => (d && typeof d === "object" ? d[p] : undefined), dict);
  const hit = walk(key);
  if (typeof hit === "string") return [hit];
  // Plurals: t("key", { count }) resolves key_one / key_other.
  return ["_one", "_other"].map((s) => walk(key + s)).filter((v): v is string => typeof v === "string");
}

/** The source of one `{...}` expression starting at `open` (the index of "{"). */
function braced(src: string, open: number): string {
  let depth = 0;
  for (let i = open; i < src.length; i++) {
    if (src[i] === "{") depth++;
    else if (src[i] === "}" && --depth === 0) return src.slice(open + 1, i);
  }
  return "";
}

/** The opening tag starting at `start`, brace-aware (a `>` inside `{...}` does not end it). */
function openingTag(src: string, start: number): string {
  let depth = 0;
  for (let i = start; i < src.length; i++) {
    const c = src[i];
    if (c === "{") depth++;
    else if (c === "}") depth--;
    else if (c === ">" && depth === 0) return src.slice(start, i + 1);
  }
  return src.slice(start);
}

function tsxFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === "dev" ? [] : tsxFiles(path);
    return name.endsWith(".tsx") ? [path] : [];
  });
}

describe("control labels fit one line", () => {
  it("every i18n label on a Button, TextAction, Chip, Badge or EmptyState action is <= 18 characters in EN and PT", () => {
    const root = join(__dirname);
    const over: string[] = [];
    let checked = 0;
    for (const file of tsxFiles(root)) {
      const src = readFileSync(file, "utf8");
      for (const m of src.matchAll(CONTROL)) {
        const tag = openingTag(src, m.index!);
        for (const p of tag.matchAll(LABEL_PROP)) {
          const expr = braced(tag, p.index! + p[0].length - 1);
          for (const [, key] of expr.matchAll(/\bt\(\s*["'`]([\w.]+)["'`]/g)) {
            for (const [lang, dict] of [["en", en], ["pt", pt]] as const) {
              for (const text of lookup(dict as Dict, key)) {
                checked++;
                const shown = text.replace(/\{\{\s*\w+\s*\}\}/g, SLOT);
                if (shown.length > MAX) over.push(`${relative(root, file)} ${m[1]} ${key} ${lang}: "${text}" (${shown.length})`);
                // A free-length name makes any label too long ("Download Ciudad Nezahualcoyotl"): it goes on the support line (FL-9).
                if (UNBOUNDED.test(text)) over.push(`${relative(root, file)} ${m[1]} ${key} ${lang}: "${text}" interpolates a name`);
                // Icons come from the icon prop, never a glyph in the string (FL-17).
                if (GLYPH.test(text)) over.push(`${relative(root, file)} ${m[1]} ${key} ${lang}: "${text}" has a glyph icon`);
              }
            }
          }
        }
      }
    }
    expect(checked).toBeGreaterThan(100);
    expect(over).toEqual([]);
  });
});
