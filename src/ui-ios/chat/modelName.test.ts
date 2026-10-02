import { describe, it, expect } from "vitest";
import { MODEL_CATALOG } from "../../models/manifest";
import { chatModelName, chatModelNameById } from "./modelName";

const t = (k: string) => k;

describe("chat model names = tier (r4to via Boar, Loom catalogLabel)", () => {
  it("names the two answer models by their tier, never the technical label", () => {
    const compact = MODEL_CATALOG.find((m) => m.answerTier === "compact")!;
    const standard = MODEL_CATALOG.find((m) => m.answerTier === "default")!;
    expect(chatModelNameById(compact.id, compact.label, t)).toBe("flows.onboarding.answerTier.compact");
    expect(chatModelName(standard, t)).toBe("flows.onboarding.answerTier.default");
  });
  it("keeps the label for a model outside the catalog, or without an id", () => {
    expect(chatModelNameById("hf/SmolLM3-3B", "SmolLM3-3B-Q4_K_M", t)).toBe("SmolLM3-3B-Q4_K_M");
    expect(chatModelNameById(undefined, "Some model", t)).toBe("Some model");
  });
});
