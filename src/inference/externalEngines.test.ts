import { describe, it, expect, vi } from "vitest";

vi.mock("native-engine", () => ({ NativeEngine: {}, nativeEngineAvailable: false }));
vi.mock("ram-monitor", () => ({ getDeviceTotalRamBytes: () => 12 * 1024 ** 3 }));

import { fastCoreMask, flattenMessages, renderOlmoe, utf8Length, externalEngineFor } from "./externalEngines";
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

describe("fastCoreMask", () => {
  it("picks the fastest cores", () => {
    // Dimensity 8300: 4 x 2.2 GHz little, 3 x 3.2 GHz and 1 x 3.35 GHz big
    expect(fastCoreMask([2200000, 2200000, 2200000, 2200000, 3200000, 3200000, 3200000, 3350000], 4)).toBe("f0");
    // Pixel 8 Pro: cpus 4-8 are the fast ones; 5 threads
    expect(fastCoreMask([1700000, 1700000, 1700000, 1700000, 2370000, 2370000, 2370000, 2370000, 2910000], 5)).toBe("1f0");
  });

  it("breaks ties by the lowest cpu and is empty when frequencies are unknown", () => {
    expect(fastCoreMask([3000, 3000, 3000, 3000], 2)).toBe("3");
    expect(fastCoreMask([0, 0, 0, 0], 4)).toBe("");
  });
});
