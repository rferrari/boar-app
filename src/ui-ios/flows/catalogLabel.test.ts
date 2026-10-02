import { describe, expect, it } from "vitest";
import type { TFunction } from "i18next";
import { COMPACT_ANSWER_MODEL, DEFAULT_ANSWER_MODEL, MODEL_CATALOG } from "../../models/manifest";
import { catalogLabel, friendlyModelNameById, isAdvancedModel, technicalModelName, TRANSLATED_LABEL_IDS } from "./catalogLabel";

const t = ((k: string) => `T:${k}`) as unknown as TFunction;

describe("catalogLabel", () => {
  it("translates the knowledge entries by id", () => {
    expect(catalogLabel({ id: "corpus-standard", label: "Standard knowledge base (+1,000 topics)" }, t)).toBe("T:flows.catalog.label.corpus-standard");
  });
  it("names the two answer models by tier, never by the technical name", () => {
    expect(catalogLabel(COMPACT_ANSWER_MODEL, t)).toBe("T:flows.onboarding.answerTier.compact");
    expect(catalogLabel(DEFAULT_ANSWER_MODEL, t)).toBe("T:flows.onboarding.answerTier.default");
  });
  it("names the search model Search", () => {
    const emb = MODEL_CATALOG.find((m) => m.kind === "embedding")!;
    expect(catalogLabel(emb, t)).toBe("T:flows.assistant.search");
  });
  it("names other models by their short name and marks them advanced", () => {
    const other = MODEL_CATALOG.find((m) => m.kind === "llm" && !m.answerTier)!;
    expect(isAdvancedModel(other)).toBe(true);
    expect(isAdvancedModel(DEFAULT_ANSWER_MODEL)).toBe(false);
    expect(catalogLabel(other, t)).toBe(other.displayName ?? other.label);
  });
  it("keeps the technical label on request and in the detail line", () => {
    expect(catalogLabel(DEFAULT_ANSWER_MODEL, t, { technical: true })).toBe(DEFAULT_ANSWER_MODEL.label);
    expect(technicalModelName({ id: "x", label: "Qwen3-4B-Instruct-2507 (Q4_K_M)", displayName: "Qwen3 4B" })).toBe("Qwen3 4B · Qwen3-4B-Instruct-2507 (Q4_K_M)");
    expect(technicalModelName({ id: "hf", label: "repo/model.gguf" })).toBe("repo/model.gguf");
  });
  it("names engine ids, falling back to the label outside the catalog", () => {
    expect(friendlyModelNameById(COMPACT_ANSWER_MODEL.id, "whatever", t)).toBe("T:flows.onboarding.answerTier.compact");
    expect(friendlyModelNameById("hf-unknown", "repo/model.gguf", t)).toBe("repo/model.gguf");
  });
  it("only lists ids that exist in the catalog", () => {
    const ids = new Set(MODEL_CATALOG.map((m) => m.id));
    for (const id of TRANSLATED_LABEL_IDS) expect(ids.has(id)).toBe(true);
  });
});
