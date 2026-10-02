import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import en from "./locales/en.json";
import pt from "./locales/pt.json";

// Screens outside the chat (onboarding, models, knowledge, settings,
// performance, about). Every key they use must exist in both locales.
const FLOW_FILES = [
  "SetupWizardScreen.tsx",
  "SettingsScreen.tsx",
  "SettingsSubscreens.tsx",
  "SettingsAssistantScreen.tsx",
  "ModelsScreen.tsx",
  "KnowledgeScreen.tsx",
  "PerformanceScreen.tsx",
  "EvaluationScreen.tsx",
  "AboutScreen.tsx",
  "flows/CatalogRow.tsx",
  "flows/ImportList.tsx",
  "flows/CitySearch.tsx",
  "flows/BootSplash.tsx",
  "navigation/RootNavigator.tsx",
].map((f) => join(__dirname, f));

type Tree = { [key: string]: string | Tree };

function lookup(tree: Tree, key: string): string | Tree | undefined {
  return key.split(".").reduce<string | Tree | undefined>(
    (node, part) => (node && typeof node === "object" ? node[part] : undefined),
    tree
  );
}

/** A plural key exists as `key_one`/`key_other` rather than `key`. */
function resolve(tree: Tree, key: string): string[] {
  const direct = lookup(tree, key);
  if (typeof direct === "string") return [direct];
  return ["_one", "_other"]
    .map((suffix) => lookup(tree, key + suffix))
    .filter((v): v is string => typeof v === "string");
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/{{\s*(\w+)\s*}}/g)].map((m) => m[1]).sort();
}

// Keys built from template strings in the flow screens, expanded by hand.
const expand = (prefix: string, parts: string[]) => parts.map((p) => `${prefix}${p}`);
const DYNAMIC_KEYS = [
  ...expand("flows.assistant.sub.", ["compact", "default"]),
  ...expand("flows.catalog.label.", ["corpus-standard", "corpus-full", "wiki-vital5"]),
  ...expand("flows.about.how", ["1", "2", "3"]),
  ...expand("flows.knowledge.stage.", ["reading", "chunking", "embedding"]),
  ...expand("flows.length.hint", ["256", "512", "1024", "2048"]),
  ...expand("flows.onboarding.package.", ["essential.name", "essential.body", "encyclopedia.name", "encyclopedia.body"]),
  ...expand("flows.onboarding.step", ["2Title", "3Title"]),
  ...expand("flows.onboarding.", ["doneAnswer", "doneKnowledge", "donePlaces", "doneIndex"]),
  ...expand("flows.onboarding.", ["doneTitle", "indexTitle", "importTitle", "step3Title", "doneBody", "indexSub", "importSub", "step3Sub"]),
  ...expand("flows.onboarding.stage.", ["start", "choose", "install", "index"]),
  ...expand("flows.onboarding.category.", ["answer", "search", "knowledge", "places"]),
  ...expand("flows.onboarding.categoryStatus.", ["failed", "moving", "importing", "ready", "queued", "import"]),
  "flows.onboarding.languageAnnounce",
  ...expand("flows.onboarding.answerTier.", ["default", "compact"]),
  ...expand("flows.places.reason.", ["timezone", "location", "manual"]),
  ...expand("flows.performance.band.", ["fast", "ok", "slow"]),
  ...expand("flows.performance.outcome.", ["success", "failure", "cancelled"]),
  ...expand("flows.row.error.", ["network", "storage", "hash-mismatch", "size-mismatch", "unknown-file", "empty-file", "unreadable-file", "too-large", "no-source", "offline-variant", "load", "unknown"]),
  ...expand("flows.row.fit.", ["streaming", "thrashing", "insufficient"]),
  ...expand("flows.row.fitShort.", ["streaming", "thrashing", "insufficient"]),
  ...expand("flows.row.kind.", ["llm", "embedding", "corpus"]),
  ...expand("flows.row.role.", ["answer", "deep", "search"]),
  ...expand("flows.row.fitWhy.", ["streaming", "thrashing", "insufficient"]),
  ...expand("flows.row.errorDetail.", ["interrupted", "paused"]),
  "flows.row.error.stalled",
  ...expand("flows.settings.", ["eraseModels", "eraseKnowledge", "eraseHistory", "eraseSettings"]),
  ...expand("flows.settings.answerMode.", ["quickModel", "quickComplete", "directModel", "directComplete"]),
];

function staticKeys(): string[] {
  const keys = new Set<string>();
  for (const file of FLOW_FILES) {
    const source = readFileSync(file, "utf8");
    for (const m of source.matchAll(/\b(?:t|tr)\(\s*"([\w.-]+)"/g)) keys.add(m[1]);
    // Any "flows.*" literal, including ones picked by a ternary inside t(...).
    for (const m of source.matchAll(/"(flows\.[\w.-]+[\w-])"/g)) keys.add(m[1]);
  }
  for (const k of DYNAMIC_KEYS) keys.add(k);
  return [...keys].sort();
}

describe("flow screen i18n keys", () => {
  const keys = staticKeys();

  it("finds keys to check", () => {
    expect(keys.length).toBeGreaterThan(50);
  });

  it.each(keys)("%s exists in en and pt with the same placeholders", (key) => {
    const enValues = resolve(en as Tree, key);
    const ptValues = resolve(pt as Tree, key);
    expect(enValues, `missing in en`).not.toHaveLength(0);
    expect(ptValues, `missing in pt`).not.toHaveLength(0);
    expect(placeholders(ptValues.join(" "))).toEqual(placeholders(enValues.join(" ")));
  });
});
