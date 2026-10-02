import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { describe, expect, it } from "vitest";
import { iconA11yProps } from "./iconA11y";

describe("iconA11yProps", () => {
  it("removes a decorative icon from the tree on both platforms", () => {
    for (const label of [undefined, "", "   "]) {
      const { wrapper, glyph } = iconA11yProps(label);
      expect(wrapper.accessible).toBe(false);
      expect(wrapper.importantForAccessibility).toBe("no-hide-descendants"); // Android
      expect(wrapper.accessibilityElementsHidden).toBe(true); // iOS
      expect(wrapper.accessibilityLabel).toBeUndefined();
      expect(glyph.accessible).toBe(false);
    }
  });

  it("exposes a labeled icon as one image element, never the glyph text", () => {
    const { wrapper, glyph } = iconA11yProps(" Offline ");
    expect(wrapper).toMatchObject({
      accessible: true,
      accessibilityRole: "image",
      accessibilityLabel: "Offline",
      accessibilityElementsHidden: false,
      importantForAccessibility: "yes",
    });
    expect(glyph).toEqual({ accessible: false, accessibilityElementsHidden: true, importantForAccessibility: "no-hide-descendants" });
  });
});

describe("icon usage in src/ui", () => {
  const root = join(__dirname, "..");
  const files: string[] = [];
  const walk = (dir: string) => {
    for (const name of readdirSync(dir)) {
      const p = join(dir, name);
      if (statSync(p).isDirectory()) walk(p);
      else if (/\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name)) files.push(p);
    }
  };
  walk(root);

  it("imports @expo/vector-icons only in components/Icon.tsx (so every glyph gets the a11y wrapper)", () => {
    const offenders = files
      .filter((f) => /@expo\/vector-icons|react-native-vector-icons/.test(readFileSync(f, "utf8")))
      .map((f) => relative(root, f))
      .filter((f) => f !== join("components", "Icon.tsx"));
    expect(offenders).toEqual([]);
  });

  it("never renders an IconButton without a non-empty label", () => {
    const offenders: string[] = [];
    for (const f of files) {
      const src = readFileSync(f, "utf8");
      for (const m of src.matchAll(/<IconButton\b([\s\S]*?)\/>/g)) {
        if (!/\blabel=\{?["'`]?[^"'`}\s]/.test(m[1])) offenders.push(`${relative(root, f)}: ${m[0].slice(0, 60)}`);
      }
    }
    expect(offenders).toEqual([]);
  });
});
