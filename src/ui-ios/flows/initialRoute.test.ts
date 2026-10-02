import { describe, expect, it } from "vitest";
import { decideInitialRoute } from "./initialRoute";

const GB = 1024 ** 3;
const DEFAULT = { id: "qwen3-4b-instruct-2507-q4km", answerTier: "default" as const, sizeBytes: 2.3 * GB };
const COMPACT = { id: "qwen2.5-1.5b-instruct-q4km", answerTier: "compact" as const, sizeBytes: 1.0 * GB };
const HF = { id: "hf-some-model", sizeBytes: 3 * GB };
const base = { totalRamBytes: 8 * GB, activeLlmId: null };

describe("decideInitialRoute", () => {
  it("first run with nothing on disk opens setup", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: false, installedLlms: [] })).toEqual({ route: "Setup" });
  });

  it("an answer model without the embedding model opens setup", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: false, installedLlms: [DEFAULT], activeLlmId: DEFAULT.id }).route).toBe("Setup");
  });

  it("no language model on disk opens setup, not a chat with a model error", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [] })).toEqual({ route: "Setup" });
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [], activeLlmId: DEFAULT.id })).toEqual({ route: "Setup" });
  });

  it("only the compact model, none chosen: opens the chat on the compact model, not the missing default (iOS shot on 4f0819f)", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [COMPACT] })).toEqual({ route: "Main", setActiveLlmId: COMPACT.id });
  });

  it("the chosen model was deleted: switches to an installed one", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [COMPACT], activeLlmId: DEFAULT.id })).toEqual({
      route: "Main",
      setActiveLlmId: COMPACT.id,
    });
  });

  it("keeps the user's choice when it is on disk, including a Hugging Face model", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [DEFAULT, HF], activeLlmId: HF.id })).toEqual({ route: "Main" });
  });

  describe("picks with Tusk's ranking when the saved model is absent", () => {
    it("the default tier on a phone with room for it", () => {
      expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [COMPACT, DEFAULT] }).setActiveLlmId).toBe(DEFAULT.id);
    });

    it("the compact tier on a low-RAM phone", () => {
      expect(decideInitialRoute({ ...base, totalRamBytes: 4 * GB, requiredPresent: true, installedLlms: [DEFAULT, COMPACT] }).setActiveLlmId).toBe(COMPACT.id);
    });

    it("the compact tier when the default was measured too slow here", () => {
      expect(
        decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [{ ...DEFAULT, tokPerSec: 3 }, { ...COMPACT, tokPerSec: 14 }] }).setActiveLlmId
      ).toBe(COMPACT.id);
    });

    it("the compact tier when the default will not fit", () => {
      expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [{ ...DEFAULT, fit: "insufficient" }, COMPACT] }).setActiveLlmId).toBe(COMPACT.id);
    });
  });


  it("low RAM with only the default 4B installed: opens the chat but saves no active model (CR-1)", () => {
    const r = decideInitialRoute({ ...base, totalRamBytes: 3.8 * GB, requiredPresent: true, installedLlms: [DEFAULT] });
    expect(r.route).toBe("Main");
    expect(r.setActiveLlmId).toBeUndefined();
  });

  it("a setup left mid-way resumes, even with the models on disk (Harbor 2bdd0d2)", () => {
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [DEFAULT], activeLlmId: DEFAULT.id, setupInProgress: true })).toEqual({
      route: "Setup",
    });
    expect(decideInitialRoute({ ...base, requiredPresent: true, installedLlms: [DEFAULT], activeLlmId: DEFAULT.id, setupInProgress: false }).route).toBe("Main");
  });
});
