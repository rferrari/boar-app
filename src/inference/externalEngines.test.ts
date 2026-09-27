import { describe, it, expect, vi } from "vitest";

vi.mock("native-engine", () => ({ NativeEngine: {}, nativeEngineAvailable: false }));

import { flattenMessages, renderOlmoe, utf8Length, externalEngineFor } from "./externalEngines";
import { MODEL_CATALOG } from "../models/manifest";

describe("utf8Length", () => {
  it("counts bytes, not UTF-16 code units", () => {
    expect(utf8Length("abc")).toBe(3);
    expect(utf8Length("ç")).toBe(2);
    expect(utf8Length("€")).toBe(3);
    expect(utf8Length("🐗")).toBe(4);
    expect(utf8Length("São Paulo 🐗")).toBe(Buffer.byteLength("São Paulo 🐗", "utf8"));
  });
});

describe("renderOlmoe", () => {
  it("renders OLMoE-Instruct's turns and ends ready for the answer", () => {
    const out = renderOlmoe([
      { role: "system", content: "Be brief." },
      { role: "user", content: "Hi" },
      { role: "assistant", content: "Hello!" },
      { role: "user", content: "Capital of Australia?" },
    ]);
    expect(out).toBe(
      "|||IP_ADDRESS|||<|system|>\nBe brief.\n<|user|>\nHi\n<|assistant|>\nHello!|||IP_ADDRESS|||\n<|user|>\nCapital of Australia?\n<|assistant|>\n"
    );
  });
});

describe("flattenMessages", () => {
  it("puts the system text first, earlier turns next and the question last", () => {
    const out = flattenMessages([
      { role: "system", content: "Use the context.\n\nContext:\n[1] Canberra" },
      { role: "user", content: "hi" },
      { role: "assistant", content: "hello" },
      { role: "user", content: "Capital of Australia?" },
    ]);
    expect(out).toBe("Use the context.\n\nContext:\n[1] Canberra\n\nConversation so far:\nUser: hi\nAssistant: hello\n\nCapital of Australia?");
  });

  it("is just the question without system text or history", () => {
    expect(flattenMessages([{ role: "user", content: " Why? " }])).toBe("Why?");
  });
});

describe("externalEngineFor", () => {
  it("maps each external catalog model to its engine and leaves llama.rn models alone", () => {
    for (const m of MODEL_CATALOG) {
      const engine = externalEngineFor(m);
      if (m.engine === "colibri") expect(engine?.name).toBe("colibri");
      else if (m.engine === "bmoe") expect(engine?.name).toBe("BigMoeOnEdge");
      else expect(engine).toBeNull();
    }
  });
});
