import { describe, it, expect, vi, beforeEach } from "vitest";

type FakeContext = {
  model: string;
  released: boolean;
  release: () => Promise<void>;
  completion: (params: unknown, onToken: (d: { token: string }) => void) => Promise<{ text: string }>;
  stopCompletion: () => Promise<void> | undefined;
  releasedWhileGenerating: boolean;
};
/** llama.rn's JSI stopCompletion returns undefined instead of a promise (rc.4 and rc.6 alike). */
let stopReturnsNothing = false;
const created: FakeContext[] = [];
const initParams: Record<string, unknown>[] = [];
const ram = { total: 12 * 1024 ** 3, rss: 1024 ** 3, avail: 0 };
let fileSize = 1_000_000;
let header: Record<string, string> | null = null;
let inFlightInits = 0;
let maxConcurrentInits = 0;
/** Native messages the next initLlama calls reject with, in order. */
const initFailures: string[] = [];
const platform = { OS: "android" };

vi.mock("react-native", () => ({ Platform: { get OS() { return platform.OS; } } }));
vi.mock("llama.rn", () => ({
  getBackendDevicesInfo: async () => [
    { backend: "Metal", type: "gpu", deviceName: "MTL0", maxMemorySize: 0 },
    { backend: "CPU", type: "cpu", deviceName: "CPU", maxMemorySize: 0 },
  ],
  loadLlamaModelInfo: async () => {
    if (!header) throw new Error("no header");
    return header;
  },
  initLlama: async (params: { model: string }, onProgress?: (pct: number) => void) => {
    onProgress?.(0);
    onProgress?.(50);
    onProgress?.(100);
    const { model } = params;
    initParams.push(params);
    const failure = initFailures.shift();
    if (failure) throw new Error(failure);
    inFlightInits++;
    maxConcurrentInits = Math.max(maxConcurrentInits, inFlightInits);
    await new Promise((r) => setTimeout(r, 5));
    inFlightInits--;
    let finish: (() => void) | null = null;
    let generating = false;
    const ctx: FakeContext = {
      model,
      released: false,
      releasedWhileGenerating: false,
      release: async () => {
        if (generating) ctx.releasedWhileGenerating = true;
        ctx.released = true;
      },
      // Like llama.cpp: runs until stopped, and settles a moment after the stop (prompt still processing).
      completion: (_params, onToken) =>
        new Promise((resolve) => {
          generating = true;
          onToken({ token: "Hi" });
          finish = () =>
            setTimeout(() => {
              generating = false;
              resolve({ text: "Hi" });
            }, 20);
        }),
      stopCompletion: () => {
        finish?.();
        return stopReturnsNothing ? undefined : Promise.resolve();
      },
    };
    created.push(ctx);
    return ctx;
  },
}));
/** In-memory cache dir: like Android, a file can only be written into a directory that exists. */
const kvFs = { dirs: new Set<string>(), files: new Set<string>() };
vi.mock("expo-file-system/legacy", () => ({
  documentDirectory: "file:///docs/",
  cacheDirectory: "file:///cache/",
  getInfoAsync: async (uri: string) => (uri.startsWith("file:///cache/") ? { exists: kvFs.files.has(uri) || kvFs.dirs.has(uri) } : { exists: true, size: fileSize }),
  makeDirectoryAsync: async (uri: string) => {
    kvFs.dirs.add(uri);
  },
  deleteAsync: async (uri: string) => {
    kvFs.files.delete(uri);
  },
}));
vi.mock("ram-monitor", () => ({
  getDeviceTotalRamBytes: () => ram.total,
  getMemoryInfo: () => ({ rssBytes: ram.rss }),
  getAvailableRamBytes: () => ram.avail,
}));

import { LlamaEngine, PREFIX_WARM_IDLE_MS, defaultContextSize } from "./LlamaEngine";

const live = () => created.filter((c) => !c.released);

beforeEach(() => {
  created.length = 0;
  initParams.length = 0;
  Object.assign(ram, { total: 12 * 1024 ** 3, rss: 1024 ** 3, avail: 0 });
  fileSize = 1_000_000;
  header = null;
  inFlightInits = 0;
  maxConcurrentInits = 0;
  initFailures.length = 0;
  platform.OS = "android";
  stopReturnsNothing = false;
  kvFs.dirs.clear();
  kvFs.files.clear();
});

describe("LlamaEngine Metal fallback (iPhone 13)", () => {
  it("loads on CPU after the Metal init failure ('Failed to load model' in JS) and reports it", async () => {
    platform.OS = "ios";
    initFailures.push("Failed to load model"); // all llama.rn's JSI passes up; "MTL0" is only in the native log
    const engine = new LlamaEngine();
    const r = await engine.load("models/a.gguf");
    expect(initParams).toHaveLength(2);
    expect(initParams[0]).toMatchObject({ n_gpu_layers: 0, flash_attn_type: "off" });
    expect(initParams[1]).toMatchObject({ n_gpu_layers: 0, devices: ["CPU"], flash_attn_type: "off" });
    expect(r.backend).toEqual({ kind: "cpu-fallback", reason: "Failed to load model" });
    expect(engine.getModelInfo()?.backend?.kind).toBe("cpu-fallback");
  });

  it("still fails with the load message when the CPU retry fails too", async () => {
    initFailures.push("Failed to load model", "Failed to load model");
    await expect(new LlamaEngine().load("models/a.gguf")).rejects.toThrow(/Failed to load "models\/a.gguf": Failed to load model/);
  });
});

describe("LlamaEngine CR-2 marker and model swap", () => {
  it("writes the marker before initLlama with the previous model, clears it after, and releases the old model first", async () => {
    const log: string[] = [];
    const guard = {
      begin: async (meta: any, previous: any) =>
        void log.push(`begin ${meta.modelId} prev=${previous?.modelId ?? "-"} inits=${initParams.length} live=${created.filter((c) => !c.released).length}`),
      end: async (meta: any, ok: boolean) => void log.push(`end ${meta.modelId} ${ok} inits=${initParams.length}`),
      consume: async () => null,
      ensureChecked: async () => {},
    };
    const engine = new LlamaEngine(guard as any);
    await engine.load("models/q15.gguf", { meta: { modelId: "q15", label: "1.5B" } });
    const first = created[0];
    const origInit = initParams.length;
    await engine.load("models/q4.gguf", { meta: { modelId: "q4", label: "4B" } });
    // live=0 at the 4B's begin: the 1.5B was released before the 4B's initLlama.
    expect(log).toEqual(["begin q15 prev=- inits=0 live=0", "end q15 true inits=1", "begin q4 prev=q15 inits=1 live=0", "end q4 true inits=2"]);
    // The 1.5B is released before the 4B's context is created: never both resident.
    expect(first.released).toBe(true);
    expect(origInit).toBe(1);
  });

  it("a failed load still clears the marker (the app survived it)", async () => {
    const ends: boolean[] = [];
    const guard = { begin: async () => {}, end: async (_m: any, ok: boolean) => void ends.push(ok), consume: async () => null, ensureChecked: async () => {} };
    initFailures.push("model file not found");
    await expect(new LlamaEngine(guard as any).load("models/a.gguf")).rejects.toThrow();
    expect(ends).toEqual([false]);
  });
});

describe("LlamaEngine load progress", () => {
  it("reports llama.rn's 0-100 as 0..1", async () => {
    const seen: number[] = [];
    await new LlamaEngine(null).load("models/a.gguf", { onProgress: (f) => seen.push(f) });
    expect(seen).toEqual([0, 0.5, 1]);
  });
});

describe("LlamaEngine load/unload", () => {
  it("never leaves an orphaned context when loads overlap (quick model swaps)", async () => {
    const engine = new LlamaEngine();
    await Promise.all([engine.load("models/a.gguf"), engine.load("models/b.gguf"), engine.load("models/a.gguf")]);
    expect(maxConcurrentInits).toBe(1);
    expect(live()).toHaveLength(1);
    expect(live()[0].model).toBe("file:///docs/models/a.gguf");
    expect(engine.getModelInfo()?.filename).toBe("models/a.gguf");
  });

  it("skips a load of the model that is already loaded", async () => {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    await Promise.all([engine.load("models/a.gguf"), engine.load("models/a.gguf")]);
    expect(created).toHaveLength(1);
  });

  it("serializes unload with a pending load, leaving nothing loaded", async () => {
    const engine = new LlamaEngine();
    await Promise.all([engine.load("models/a.gguf"), engine.unload()]);
    expect(live()).toHaveLength(0);
    expect(engine.isLoaded).toBe(false);
  });

  it("keeps working after a failed load", async () => {
    const engine = new LlamaEngine();
    const fs = await import("expo-file-system/legacy");
    const spy = vi.spyOn(fs, "getInfoAsync").mockResolvedValueOnce({ exists: false } as any);
    await expect(engine.load("models/missing.gguf")).rejects.toThrow(/not found/);
    await engine.load("models/b.gguf");
    expect(engine.getModelInfo()?.filename).toBe("models/b.gguf");
    spy.mockRestore();
  });

  it("stops and waits for a running generation before releasing its model", async () => {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const reply = engine.generate({ prompt: "say hi" });
    await engine.load("models/b.gguf");
    await expect(reply).resolves.toBe("Hi");
    expect(created[0].released).toBe(true);
    expect(created[0].releasedWhileGenerating).toBe(false);
    expect(engine.getModelInfo()?.filename).toBe("models/b.gguf");
  });

  it("switches models twice mid-generation with a stopCompletion that returns no promise and keeps one context loaded", async () => {
    stopReturnsNothing = true;
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const first = engine.generate({ prompt: "say hi" });
    await engine.load("models/b.gguf");
    const second = engine.generate({ prompt: "say hi" });
    await engine.load("models/a.gguf");
    await expect(first).resolves.toBe("Hi");
    await expect(second).resolves.toBe("Hi");
    expect(created).toHaveLength(3);
    expect(live()).toHaveLength(1);
    expect(live()[0].model).toContain("models/a.gguf");
    expect(created.some((c) => c.releasedWhileGenerating)).toBe(false);
    expect(engine.getModelInfo()?.filename).toBe("models/a.gguf");
  });
});

const GiB = 1024 ** 3;
const QWEN3_MOE_HEADER = {
  "general.architecture": "qwen3moe",
  "qwen3moe.block_count": "48",
  "qwen3moe.embedding_length": "2048",
  "qwen3moe.attention.head_count": "32",
  "qwen3moe.attention.head_count_kv": "4",
  "qwen3moe.attention.key_length": "128",
  "qwen3moe.attention.value_length": "128",
  "qwen3moe.expert_count": "128",
  "qwen3moe.expert_used_count": "8",
  "qwen3moe.expert_feed_forward_length": "768",
  "qwen3moe.vocab_size": "151936",
};

describe("LlamaEngine pre-flight memory check (mmap-aware)", () => {
  it("loads a MoE file larger than free RAM with a streaming warning instead of throwing", async () => {
    fileSize = 11 * GiB;
    ram.avail = 7 * GiB;
    header = QWEN3_MOE_HEADER;
    const engine = new LlamaEngine();
    const result = await engine.load("models/qwen3-30b-a3b.gguf");
    expect(engine.isLoaded).toBe(true);
    expect(result.fit?.verdict).toBe("streaming");
    expect(result.warning).toMatch(/streams from storage/);
    expect(initParams[0]).toMatchObject({ use_mmap: true, use_mlock: false });
  });

  it("returns the same warning when the already-loaded model is re-requested", async () => {
    fileSize = 11 * GiB;
    ram.avail = 7 * GiB;
    header = QWEN3_MOE_HEADER;
    const engine = new LlamaEngine();
    await engine.load("models/m.gguf");
    const again = await engine.load("models/m.gguf");
    expect(created).toHaveLength(1);
    expect(again.fit?.verdict).toBe("streaming");
  });

  it("still refuses when KV + buffers alone cannot fit, without creating a context", async () => {
    header = QWEN3_MOE_HEADER;
    // A phone too small for it: 2.5 GB total leaves nothing after the system's reserve.
    ram.total = 2.5 * GiB;
    ram.avail = 200 * 1024 ** 2;
    const engine = new LlamaEngine();
    await expect(engine.load("models/m.gguf")).rejects.toThrow(/cannot be streamed/);
    expect(created).toHaveLength(0);
  });

  it("budgets total RAM minus the system's reserve when the native module has no available-RAM readout", async () => {
    // 12 GB − max(2.5 GB, 25%) = 9 GB available, 1MB dense file without header → resident.
    const engine = new LlamaEngine();
    const result = await engine.load("models/a.gguf");
    expect(result.fit?.availableBytes).toBe(9 * GiB);
    expect(result.fit?.fromMetadata).toBe(false);
    expect(result.warning).toBeNull();
  });

  it("skips the check when RAM readouts are unavailable", async () => {
    ram.total = 0;
    const engine = new LlamaEngine();
    const result = await engine.load("models/a.gguf");
    expect(result.fit).toBeNull();
    expect(engine.isLoaded).toBe(true);
  });

  it("estimates a downloaded model without loading it", async () => {
    fileSize = 11 * GiB;
    ram.avail = 7 * GiB;
    header = QWEN3_MOE_HEADER;
    const engine = new LlamaEngine();
    const fit = await engine.estimateFit("models/m.gguf");
    expect(fit?.verdict).toBe("streaming");
    expect(created).toHaveLength(0);
  });
});

describe("LlamaEngine generate queue (double-send race)", () => {
  it("never runs two completions on one context at once", async () => {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const ctx = created[0] as any;
    let running = 0;
    let maxRunning = 0;
    ctx.completion = (_p: unknown, onToken: (d: { token: string }) => void) => {
      running++;
      maxRunning = Math.max(maxRunning, running);
      onToken({ token: "x" });
      return new Promise((resolve) =>
        setTimeout(() => {
          running--;
          resolve({ text: "x", timings: { prompt_n: 12, prompt_ms: 30, predicted_n: 1, predicted_ms: 5 }, tokens_cached: 8 });
        }, 10)
      );
    };
    const timings: unknown[] = [];
    const [a, b] = await Promise.all([
      engine.generate({ prompt: "one", onTimings: (t) => timings.push(t) }),
      engine.generate({ prompt: "two" }),
    ]);
    expect([a, b]).toEqual(["x", "x"]);
    expect(maxRunning).toBe(1);
    expect(timings[0]).toEqual({ promptTokens: 12, promptMs: 30, predictedTokens: 1, predictedMs: 5, cachedTokens: 8 });
  });

  it("keeps serving after a failed generation", async () => {
    const engine = new LlamaEngine();
    await expect(engine.generate({ prompt: "x" })).rejects.toThrow(/not loaded/);
    await engine.load("models/a.gguf");
    const first = engine.generate({ prompt: "x" });
    await new Promise((r) => setTimeout(r, 0));
    const queued = engine.generate({ prompt: "y" });
    await engine.stop();
    // The running one ends with what it had; the queued one never starts.
    await expect(first).resolves.toBe("Hi");
    await expect(queued).resolves.toBe("");
  });
});

describe("default context size", () => {
  it("uses 2048 on 4GB devices and 4096 otherwise (or when RAM is unknown)", async () => {
    ram.total = 4 * 1024 ** 3;
    expect(defaultContextSize()).toBe(2048);
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    expect(initParams[0]).toMatchObject({ n_ctx: 2048 });
    ram.total = 12 * 1024 ** 3;
    expect(defaultContextSize()).toBe(4096);
    ram.total = 0;
    expect(defaultContextSize()).toBe(4096);
  });
});

describe("thinking budget", () => {
  it("caps reasoning and adds its budget on top of the answer's tokens (chat-template path only)", async () => {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const seen: any[] = [];
    (created[0] as any).completion = (p: any) => {
      seen.push(p);
      return Promise.resolve({ text: "ok" });
    };
    await engine.generate({ messages: [{ role: "user", content: "q" }], nPredict: 300, thinkingBudget: 256 });
    await engine.generate({ prompt: "q", nPredict: 300, thinkingBudget: 256 });
    // llama.rn ignores thinking_budget_tokens unless the end tag is given.
    expect(seen[0]).toMatchObject({ n_predict: 556, thinking_budget_tokens: 256, thinking_start_tag: "<think>", thinking_end_tag: "</think>" });
    expect(seen[0].thinking_budget_message).toBeTruthy();
    expect(seen[1].n_predict).toBe(300);
    expect(seen[1].thinking_budget_tokens).toBeUndefined();
    await engine.generate({ messages: [{ role: "user", content: "q" }], nPredict: 200, thinkingBudget: 256, enableThinking: false });
    expect(seen[2]).toMatchObject({ n_predict: 200, enable_thinking: false });
    expect(seen[2].thinking_budget_tokens).toBeUndefined();
  });
});

describe("thinkingTagsFor", () => {
  it("uses Gemma 4's channel markers and <think> for everyone else", async () => {
    const { thinkingTagsFor } = await import("./LlamaEngine");
    expect(thinkingTagsFor("gemma4")).toEqual({ start: "<|channel>thought", end: "<channel|>" });
    expect(thinkingTagsFor("lfm2moe")).toEqual({ start: "<think>", end: "</think>" });
    expect(thinkingTagsFor(null)).toEqual({ start: "<think>", end: "</think>" });
  });

  it("picks the tags from the loaded model's GGUF header", async () => {
    header = { "general.architecture": "gemma4", "gemma4.block_count": "30", "gemma4.embedding_length": "2560", "gemma4.attention.head_count": "8" } as any;
    const engine = new LlamaEngine();
    await engine.load("models/gemma.gguf");
    const seen: any[] = [];
    (created[0] as any).completion = (p: any) => {
      seen.push(p);
      return Promise.resolve({ text: "ok" });
    };
    await engine.generate({ messages: [{ role: "user", content: "q" }], nPredict: 100, thinkingBudget: 64 });
    expect(seen[0]).toMatchObject({ thinking_start_tag: "<|channel>thought", thinking_end_tag: "<channel|>" });
  });
});

describe("answer prefix kept in the KV cache (iPhone 13: 4-5 s to the first token)", () => {
  const PREFIX = { system: "You are Boar. Use the context below. You are BOAR, an offline app.", prompt: "You are Boar. Use the context below. You are BOAR, an offline app.\n\n" };
  const answerMsgs = [{ role: "system", content: `${PREFIX.system}\n\nContext:\n[1] Monsoon\nA monsoon is a seasonal wind.` }, { role: "user", content: "what's a monsoon?" }];

  async function loadedEngine() {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const seen: any[] = [];
    Object.assign(created[0] as any, {
      isJinjaSupported: () => true,
      completion: (p: any) => {
        seen.push(p);
        return Promise.resolve({ text: p.n_predict === 0 ? "" : "ok", timings: { prompt_n: 60, prompt_ms: 600 } });
      },
    });
    vi.useFakeTimers();
    return { engine, seen };
  }
  const warms = (seen: any[]) => seen.filter((p) => p.n_predict === 0);

  it("prefills the tone's prefix after the load, with no token generated, in the answer's chat format", async () => {
    try {
      const { engine, seen } = await loadedEngine();
      engine.setAnswerPrefix(PREFIX);
      await vi.advanceTimersByTimeAsync(20);
      expect(warms(seen)).toHaveLength(1);
      expect(warms(seen)[0]).toMatchObject({ jinja: true, messages: [{ role: "system", content: PREFIX.system }, { role: "user", content: "" }] });
      // The answer that follows starts with the same system text: nothing to refill after it.
      await engine.generate({ messages: answerMsgs });
      await vi.advanceTimersByTimeAsync(5000);
      expect(warms(seen)).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it("refills it once the session title (another prompt) is done, after an idle moment", async () => {
    try {
      const { engine, seen } = await loadedEngine();
      engine.setAnswerPrefix(PREFIX);
      await vi.advanceTimersByTimeAsync(20);
      await engine.generate({ messages: answerMsgs });
      await engine.generate({ messages: [{ role: "system", content: "Generate a short 3-5 word title" }, { role: "user", content: "q" }], nPredict: 16 });
      await vi.advanceTimersByTimeAsync(PREFIX_WARM_IDLE_MS - 50);
      expect(warms(seen)).toHaveLength(1);
      await vi.advanceTimersByTimeAsync(100);
      expect(warms(seen)).toHaveLength(2);
      // Plain-prompt models get the plain prefix.
      (created[0] as any).isJinjaSupported = () => false;
      await engine.generate({ prompt: "Title: q" });
      await vi.advanceTimersByTimeAsync(PREFIX_WARM_IDLE_MS + 20);
      expect(warms(seen)[2]).toEqual({ prompt: PREFIX.prompt, n_predict: 0, n_probs: 0 });
    } finally {
      vi.useRealTimers();
    }
  });

  it("never puts a refill in front of a question already waiting, and skips between back-to-back prompts", async () => {
    try {
      const { engine, seen } = await loadedEngine();
      engine.setAnswerPrefix(PREFIX);
      // A question arrives before the post-load refill starts: it goes first, and it prefills the prefix itself.
      const q = engine.generate({ messages: answerMsgs });
      await vi.advanceTimersByTimeAsync(20);
      await q;
      expect(warms(seen)).toHaveLength(0);
      // Deep Research stages one after another: no refill between them.
      await engine.generate({ prompt: "stage 1" });
      await vi.advanceTimersByTimeAsync(100);
      await engine.generate({ prompt: "stage 2" });
      await vi.advanceTimersByTimeAsync(100);
      expect(warms(seen)).toHaveLength(0);
      await vi.advanceTimersByTimeAsync(PREFIX_WARM_IDLE_MS);
      expect(warms(seen)).toHaveLength(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it("does nothing without a prefix, and a new tone prefills again", async () => {
    try {
      const { engine, seen } = await loadedEngine();
      await engine.generate({ prompt: "Title: q" });
      await vi.advanceTimersByTimeAsync(PREFIX_WARM_IDLE_MS + 20);
      expect(warms(seen)).toHaveLength(0);
      engine.setAnswerPrefix(PREFIX);
      engine.setAnswerPrefix({ ...PREFIX });
      await vi.advanceTimersByTimeAsync(20);
      expect(warms(seen)).toHaveLength(1);
      engine.setAnswerPrefix({ system: "Thorough.", prompt: "Thorough.\n\n" });
      await vi.advanceTimersByTimeAsync(20);
      expect(warms(seen)).toHaveLength(2);
      expect(warms(seen)[1].messages[0].content).toBe("Thorough.");
    } finally {
      vi.useRealTimers();
    }
  });

  it("route gate: reads the last probabilities entry (rc.4 never clears them) and resets n_probs for the answer", async () => {
    try {
      const { engine, seen } = await loadedEngine();
      Object.assign(created[0] as any, {
        completion: (p: any) => {
          seen.push(p);
          // rc.4: completion_probabilities keeps every earlier gate's token; this call's is the last one.
          const stale = { probs: [{ tok_str: "SEARCH", prob: 1 }] };
          const fresh = { probs: [{ tok_str: "ANS", prob: 0.8 }, { tok_str: "SEARCH", prob: 0.2 }] };
          return Promise.resolve({ text: "", completion_probabilities: p.n_probs ? [stale, fresh] : undefined, timings: { prompt_n: 9 } });
        },
        saveSession: vi.fn(async () => 1),
        loadSession: vi.fn(async () => ({})),
      });
      const gateMsgs = [{ role: "system", content: "router" }, { role: "user", content: "oi" }];
      const probs = await engine.nextTokenProbs(gateMsgs, { modelFilename: "models/a.gguf", nProbs: 20 });
      expect(probs).toEqual([{ token: "ANS", prob: 0.8 }, { token: "SEARCH", prob: 0.2 }]);
      expect(seen.at(-1)).toEqual({ messages: gateMsgs, jinja: true, n_predict: 1, n_probs: 20, temperature: 1 });
      // Another model than the loaded one: no gate (the answer path keeps the rules).
      expect(await engine.nextTokenProbs(gateMsgs, { modelFilename: "models/other.gguf", nProbs: 20 })).toBeNull();
      await engine.generate({ messages: answerMsgs });
      expect(seen.at(-1)).toMatchObject({ n_probs: 0, temperature: 0.7 });
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("KV session files (A15 smoke: models/ in the key left saveSession without a directory)", () => {
  const PREFIX = { system: "You are Boar. Use the context below.", prompt: "You are Boar. Use the context below.\n\n" };
  const gateMsgs = [{ role: "system", content: "router" }, { role: "user", content: "oi" }];

  /** A context that records warm-ups and session calls, and fails a save when its directory is missing (ENOENT). */
  async function engineWithSessions(model = "models/Qwen3-4B-Q4_K_M.gguf") {
    const engine = new LlamaEngine();
    await engine.load(model);
    const ctx = created.at(-1) as any;
    const log = { saved: [] as string[], loaded: [] as string[], warms: 0 };
    Object.assign(ctx, {
      isJinjaSupported: () => true,
      completion: (p: any) => {
        if (p.n_predict === 0) log.warms++;
        return Promise.resolve({
          text: p.n_predict === 0 ? "" : "ok",
          completion_probabilities: p.n_probs ? [{ probs: [{ tok_str: "SEARCH", prob: 1 }] }] : undefined,
          timings: { prompt_n: 60, prompt_ms: 600 },
        });
      },
      saveSession: async (path: string) => {
        const dir = "file://" + path.slice(0, path.lastIndexOf("/") + 1);
        if (!kvFs.dirs.has(dir)) throw new Error("Failed to save session");
        kvFs.files.add("file://" + path);
        log.saved.push(path);
        return 1;
      },
      loadSession: async (path: string) => {
        if (!kvFs.files.has("file://" + path)) throw new Error("Failed to load session");
        log.loaded.push(path);
        return {};
      },
    });
    return { engine, log, model };
  }

  it("a key with '/' (the model path) is flattened into cache/kv/ and round-trips save -> load", async () => {
    const { engine, log, model } = await engineWithSessions();
    await engine.nextTokenProbs(gateMsgs, { modelFilename: model, nProbs: 20 });
    expect(log.warms).toBe(1);
    expect(log.saved).toHaveLength(1);
    expect(log.saved[0]).toMatch(/^\/cache\/kv\/[^/]+\.bin$/);
    expect(log.saved[0]).toContain("Qwen3-4B-Q4_K_M.gguf");
    // A new process (fresh engine, same cache dir) restores the file instead of prefilling again.
    const again = await engineWithSessions();
    await again.engine.nextTokenProbs(gateMsgs, { modelFilename: again.model, nProbs: 20 });
    expect(again.log.loaded).toEqual(log.saved);
    expect(again.log.warms).toBe(0);
    expect(again.log.saved).toHaveLength(0);
  });

  it("the gate prefix is saved once and reused on the next questions", async () => {
    const { engine, log, model } = await engineWithSessions();
    for (let i = 0; i < 3; i++) await engine.nextTokenProbs(gateMsgs, { modelFilename: model, nProbs: 20 });
    expect(log.warms).toBe(1);
    expect(log.saved).toHaveLength(1);
  });

  it("the answer prefix is saved once, and a later engine restores it instead of prefilling", async () => {
    const a = await engineWithSessions();
    vi.useFakeTimers();
    try {
      a.engine.setAnswerPrefix(PREFIX);
      await vi.advanceTimersByTimeAsync(20);
    } finally {
      vi.useRealTimers();
    }
    expect(a.log.warms).toBe(1);
    expect(a.log.saved).toHaveLength(1);
    const b = await engineWithSessions();
    b.engine.setAnswerPrefix(PREFIX);
    await b.engine.generate({ messages: [{ role: "system", content: `${PREFIX.system}\n\nContext:\nx` }, { role: "user", content: "q" }] });
    expect(b.log.loaded).toEqual(a.log.saved);
    expect(b.log.warms).toBe(0);
  });

  it("gate and answer files differ and neither contains a path separator beyond cache/kv/", async () => {
    const { engine, log, model } = await engineWithSessions();
    await engine.nextTokenProbs(gateMsgs, { modelFilename: model, nProbs: 20 });
    vi.useFakeTimers();
    try {
      engine.setAnswerPrefix(PREFIX);
      await vi.advanceTimersByTimeAsync(20);
    } finally {
      vi.useRealTimers();
    }
    expect(new Set(log.saved).size).toBe(2);
    for (const p of log.saved) expect(p.replace("/cache/kv/", "")).not.toContain("/");
  });
  it("CUT5-SPEED: the no-source system message gets its own flattened .nosrc session file, saved once and restored by a later engine", async () => {
    const SYS = "You are Boar. Answer from your library only.";
    const msgs = [{ role: "system", content: SYS }, { role: "user", content: "q" }];
    const a = await engineWithSessions();
    await a.engine.generate({ messages: msgs, staticSystem: true });
    await a.engine.generate({ messages: msgs, staticSystem: true });
    expect(a.log.warms).toBe(1); // prefilled once, the second question finds it in the KV cache
    expect(a.log.saved).toHaveLength(1);
    expect(a.log.saved[0]).toMatch(/^\/cache\/kv\/[^/]+\.nosrc\.bin$/);
    expect(a.log.saved[0]).toContain("Qwen3-4B-Q4_K_M.gguf");
    expect(kvFs.dirs.has("file:///cache/kv/")).toBe(true); // mkdir before the save (ENOENT otherwise)
    const key = a.engine.noSourceKvKey(SYS);
    expect(key).toMatch(/\.nosrc$/);
    expect(a.engine.noSourceKvKey(SYS + "x")).not.toBe(key);
    const b = await engineWithSessions();
    await b.engine.generate({ messages: msgs, staticSystem: true });
    expect(b.log.loaded).toEqual(a.log.saved);
    expect(b.log.warms).toBe(0);
    expect(b.log.saved).toHaveLength(0);
  });

  it("CUT5-SPEED: without staticSystem nothing is saved or restored, and the generation prompt is unchanged", async () => {
    const { engine, log } = await engineWithSessions();
    const seen: any[] = [];
    const ctx = created.at(-1) as any;
    const orig = ctx.completion;
    ctx.completion = (p: any) => (seen.push(p), orig(p));
    const msgs = [{ role: "system", content: "sys" }, { role: "user", content: "q" }];
    await engine.generate({ messages: msgs });
    expect(log.saved).toHaveLength(0);
    expect(log.warms).toBe(0);
    await engine.generate({ messages: msgs, staticSystem: true });
    const gens = seen.filter((p) => p.n_predict !== 0);
    expect(gens).toHaveLength(2);
    expect(gens[1].messages).toEqual(gens[0].messages); // token-identical prompt
  });

  it("CUT5-SPEED: the no-source prefix is restored again after the gate took the KV cache", async () => {
    const { engine, log, model } = await engineWithSessions();
    const msgs = [{ role: "system", content: "sys" }, { role: "user", content: "q" }];
    await engine.generate({ messages: msgs, staticSystem: true });
    await engine.nextTokenProbs(gateMsgs, { modelFilename: model, nProbs: 20 });
    await engine.generate({ messages: msgs, staticSystem: true });
    expect(log.loaded.filter((p) => p.endsWith(".nosrc.bin"))).toHaveLength(1);
  });
});

describe("unload during KV session I/O (CodeRabbit: queue and genQueue are independent)", () => {
  const PREFIX = { system: "You are Boar. Use the context below.", prompt: "You are Boar. Use the context below.\n\n" };
  const gateMsgs = [{ role: "system", content: "router" }, { role: "user", content: "oi" }];

  /** A loaded engine whose session calls wait for `resolveIo()`; records native calls made after release. */
  async function slowSessions() {
    const engine = new LlamaEngine();
    await engine.load("models/a.gguf");
    const ctx = created.at(-1) as any;
    const log = { afterRelease: [] as string[], completions: 0, loads: 0, saves: 0 };
    let pending: (() => void)[] = [];
    const io = (name: string) => {
      if (ctx.released) log.afterRelease.push(name);
      return new Promise<number>((r) => pending.push(() => r(1)));
    };
    Object.assign(ctx, {
      isJinjaSupported: () => true,
      completion: (p: any) => {
        if (ctx.released) log.afterRelease.push("completion");
        log.completions++;
        return Promise.resolve({ text: "ok", completion_probabilities: p.n_probs ? [{ probs: [{ tok_str: "SEARCH", prob: 1 }] }] : undefined });
      },
      loadSession: (path: string) => (log.loads++, kvFs.files.add("file://" + path), io("loadSession")),
      saveSession: (path: string) => (log.saves++, kvFs.files.add("file://" + path), io("saveSession")),
    });
    const resolveIo = () => {
      const p = pending;
      pending = [];
      p.forEach((f) => f());
    };
    const ioPending = () => pending.length;
    return { engine, ctx, log, resolveIo, ioPending };
  }
  const tick = () => new Promise((r) => setTimeout(r, 1));

  it("an answer restoring its prefix: unload waits for loadSession, then the answer fails instead of running on the released context", async () => {
    const { engine, ctx, log, resolveIo, ioPending } = await slowSessions();
    engine.setAnswerPrefix(PREFIX);
    // The saved prefix file exists, so generate() restores it with loadSession before its completion.
    const key = (engine as any).answerKvKey(PREFIX) as string;
    kvFs.files.add((engine as any).kvPath(key).uri);
    const answer = engine.generate({ messages: [{ role: "system", content: `${PREFIX.system}\n\nContext:\nx` }, { role: "user", content: "q" }] });
    while (!ioPending()) await tick();
    let unloaded = false;
    const unload = engine.unload().then(() => (unloaded = true));
    await tick();
    expect(unloaded).toBe(false); // waits for the session load instead of releasing under it
    expect(ctx.released).toBe(false);
    resolveIo();
    await unload;
    await expect(answer).rejects.toThrow("model unloaded");
    expect(ctx.released).toBe(true);
    expect(log.afterRelease).toEqual([]);
    expect(log.completions).toBe(0);
  });

  it("an answer that fails with 'model unloaded' leaves no timeout armed (a stale timer would stop a later answer)", async () => {
    const { engine, resolveIo, ioPending } = await slowSessions();
    engine.setAnswerPrefix(PREFIX);
    const key = (engine as any).answerKvKey(PREFIX) as string;
    kvFs.files.add((engine as any).kvPath(key).uri);
    const onTimeout = vi.fn();
    const answer = engine.generate({ messages: [{ role: "system", content: `${PREFIX.system}\n\nContext:\nx` }, { role: "user", content: "q" }], timeoutMs: 30, onTimeout });
    while (!ioPending()) await tick();
    const unload = engine.unload();
    await tick(); // unloadNow is waiting on the session load
    resolveIo();
    await unload;
    await expect(answer).rejects.toThrow("model unloaded");
    await new Promise((r) => setTimeout(r, 60));
    expect(onTimeout).not.toHaveBeenCalled();
  });

  it("the route gate's first prefill: unload during saveSession waits for it, and the gate returns null without a completion", async () => {
    const { engine, ctx, log, resolveIo, ioPending } = await slowSessions();
    const probs = engine.nextTokenProbs(gateMsgs, { modelFilename: "models/a.gguf", nProbs: 20 });
    while (!ioPending()) await tick(); // the prefill ran; saveSession is pending
    expect(log.saves).toBe(1);
    const unload = engine.unload();
    await tick();
    expect(ctx.released).toBe(false);
    resolveIo();
    await unload;
    expect(await probs).toBeNull();
    expect(ctx.released).toBe(true);
    expect(log.afterRelease).toEqual([]);
    expect(log.completions).toBe(1); // the prefill only, no gate completion after the unload
  });

  it("no session call starts after an unload that ran during the directory check", async () => {
    const { engine, ctx, log } = await slowSessions();
    engine.setAnswerPrefix(PREFIX);
    const key = (engine as any).answerKvKey(PREFIX) as string;
    kvFs.files.add((engine as any).kvPath(key).uri);
    const answer = engine.generate({ messages: [{ role: "system", content: `${PREFIX.system}\n\nContext:\nx` }, { role: "user", content: "q" }] });
    await engine.unload(); // runs while generateNow awaits getInfoAsync, before loadSession
    await expect(answer).rejects.toThrow(/model unloaded|not loaded/);
    expect(ctx.released).toBe(true);
    expect(log.afterRelease).toEqual([]);
    expect(log.loads).toBe(0);
    expect(kvFs.files.has((engine as any).kvPath(key).uri)).toBe(true); // the saved prefix is not deleted
  });
});
