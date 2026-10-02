import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { describe, it, expect } from "vitest";

// One accent per screen (Prism CH-13/CH-14): in the conversation the ember belongs to send. Secondary moves
// are TextAction (text.secondary), never ghost Buttons (ember text); the places prompts use secondary Buttons.
const dir = __dirname;
const files = readdirSync(dir).filter((f) => f.endsWith(".tsx"));
const src = (f: string) => readFileSync(join(dir, f), "utf8");
const openingTags = (s: string, tag: string) => s.match(new RegExp(`<${tag}\\b[^>]*?/?>`, "gs")) ?? [];

describe("one accent in the conversation", () => {
  it("no ghost Button in the chat (ember text on a secondary move)", () => {
    const hits = files.filter((f) => /<Button\b[^>]*variant="ghost"/s.test(src(f)));
    expect(hits).toEqual([]);
  });

  it("the places prompts in the conversation use secondary Buttons (the place sheet, a modal, keeps its primary)", () => {
    const inline = openingTags(src("PlacesCard.tsx"), "Button").filter((tag) => /chat\.places\.(useCity|search|chooseCity|useLocation)"/.test(tag));
    expect(inline.length).toBe(4);
    expect(inline.filter((tag) => !/variant="secondary"/.test(tag))).toEqual([]);
    expect(src("PlacesCard.tsx")).toMatch(/actionVariant="secondary"/);
  });
});
