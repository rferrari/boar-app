import { beforeEach, describe, expect, it, vi } from "vitest";

// A 384-dim head that maps the pooled vector's first coordinate to the emergency logit, so tests control p.
const DIM = 384;
const zeros = () => new Array(DIM).fill(0);
const head = (() => {
  const dense = zeros().flatMap((_, i) => zeros().map((__, j) => (i === j ? 1 : 0)));
  const cls_w = [...zeros(), ...zeros()];
  cls_w[DIM] = 1; // logit[1] = z[0]
  return { kind: "mmbert-cls", dim: DIM, eps: 1e-5, dense, norm: new Array(DIM).fill(1), cls_w, cls_b: [5, 0] }; // bias: p ~0 unless z[0] is large
})();
const vecFor = (lead: number) => {
  const v = zeros().map((_, i) => ((i * 7919) % 13) / 13 - 0.4);
  v[0] = lead;
  return v;
};

const state = {
  files: {} as Record<string, string>,
  inits: [] as any[],
  embeds: [] as { text: string; params: any }[],
  released: 0,
  // what the fake context returns for a text (by default: the canary's own reference vector)
  vector: (text: string): number[] => vecFor(text.includes("choking") ? 30 : -30),
};

vi.mock("react-native", () => ({ Platform: { OS: "android" } }));
vi.mock("expo-file-system/legacy", () => ({
  documentDirectory: "file:///docs/",
  getInfoAsync: async (uri: string) => ({ exists: uri.replace("file:///docs/", "") in state.files }),
  readAsStringAsync: async (uri: string) => state.files[uri.replace("file:///docs/", "")],
}));
vi.mock("llama.rn", () => ({
  getBackendDevicesInfo: async () => [{ backend: "CPU", type: "cpu", deviceName: "CPU" }],
  initLlama: async (params: any) => {
    state.inits.push(params);
    return {
      tokenize: async (t: string) => ({ tokens: t.split(" ") }),
      embedding: async (text: string, params: any) => {
        state.embeds.push({ text, params });
        return { embedding: state.vector(text) };
      },
      release: async () => {
        state.released++;
      },
    };
  },
}));

import { mmbertProbability } from "./emergencyHead";
import { canaryCheck, deviceEmergencyScore, EmergencyDetector, DETECTOR_CANARY_FILE, DETECTOR_HEAD_FILE, DETECTOR_MODEL_FILE } from "./emergencyDetector";

const T = { cos_min: 0.99, l2_rel_max: 0.02, p_abs_max: 0.05 };
const l2 = (v: number[]) => Math.sqrt(v.reduce((s, x) => s + x * x, 0));
const canaryItem = (text: string, lead: number) => {
  const vec = vecFor(lead);
  return { text, vec, l2: l2(vec), p: mmbertProbability(head as any, vec) };
};
const canaryFile = () => ({
  model_sha256: "a86f", head_sha256: "7664", thresholds: T,
  items: [canaryItem("my child is choking", 30), canaryItem("capital of Japan", -30)],
});

beforeEach(() => {
  state.files = {
    [DETECTOR_MODEL_FILE]: "gguf",
    [DETECTOR_HEAD_FILE]: JSON.stringify(head),
    [DETECTOR_CANARY_FILE]: JSON.stringify(canaryFile()),
  };
  state.inits = [];
  state.embeds = [];
  state.released = 0;
  state.vector = (text: string) => vecFor(text.includes("choking") ? 30 : -30);
});

describe("canaryCheck", () => {
  const ref = canaryItem("x", 30);
  it("passes the reference itself", () => {
    expect(canaryCheck(ref, T, ref.vec, ref.p).ok).toBe(true);
  });
  it("fails a scaled vector with the same direction and p (the last-token pooling case: p passes, L2 doesn't)", () => {
    const r = canaryCheck(ref, T, ref.vec.map((x) => x * 1.35), ref.p);
    expect(r.cos).toBeCloseTo(1, 6);
    expect(r.ok).toBe(false);
  });
  it("fails another direction, and a p off by more than 0.05", () => {
    expect(canaryCheck(ref, T, vecFor(-30), ref.p).ok).toBe(false);
    expect(canaryCheck(ref, T, ref.vec, ref.p - 0.06).ok).toBe(false);
  });
  it("fails a vector of the wrong length", () => {
    expect(canaryCheck(ref, T, ref.vec.slice(0, 100), ref.p).ok).toBe(false);
  });
});

describe("EmergencyDetector", () => {
  it("loads with pooling 'mean' (a string: llama.rn maps it), n_parallel 1, and passes the canary", async () => {
    const d = new EmergencyDetector();
    expect(await d.load()).toBe(true);
    expect(d.status).toBe("ready");
    expect(state.inits[0]).toMatchObject({ embedding: true, pooling_type: "mean", n_parallel: 1, n_ctx: 512, n_ubatch: 512, n_threads: 2 });
    expect(d.canary.map((c) => c.ok)).toEqual([true, true]);
  });

  it("scores with the RAW vector (embd_normalize -1) and fires at the frozen threshold", async () => {
    const d = new EmergencyDetector();
    await d.load();
    const hit = await d.score("my child is choking on a grape", ["we are at a restaurant"]);
    expect(hit.fire).toBe(true);
    expect(hit.p).toBeGreaterThan(0.99);
    const last = state.embeds[state.embeds.length - 1];
    expect(last.params).toEqual({ embd_normalize: -1 });
    expect(last.text).toBe("we are at a restaurant | my child is choking on a grape");
    expect((await d.score("what is the capital of Japan", [])).fire).toBe(false);
  });

  it("stays off (lexical fallback) when the canary fails, e.g. a wrongly pooled vector of the same direction", async () => {
    state.vector = (text: string) => vecFor(text.includes("choking") ? 30 : -30).map((x) => x * 1.4);
    const d = new EmergencyDetector();
    expect(await d.load()).toBe(false);
    expect(d.status).toBe("canary-fail");
    expect(state.released).toBe(1);
    await expect(d.score("anything", [])).rejects.toThrow("not loaded");
  });

  it("stays off without a canary file, and reports absent without the model", async () => {
    delete state.files[DETECTOR_CANARY_FILE];
    const a = new EmergencyDetector();
    expect(await a.load()).toBe(false);
    expect(a.status).toBe("no-canary");
    delete state.files[DETECTOR_MODEL_FILE];
    const b = new EmergencyDetector();
    expect(await b.load()).toBe(false);
    expect(b.status).toBe("absent");
    expect(state.inits).toHaveLength(1);
  });

  it("remembers a terminal load status: a second load() does not re-initialise the model; 'absent' is re-checked", async () => {
    state.vector = (text: string) => vecFor(text.includes("choking") ? 30 : -30).map((x) => x * 1.4);
    const failed = new EmergencyDetector();
    expect(await failed.load()).toBe(false);
    expect(failed.status).toBe("canary-fail");
    expect(await failed.load()).toBe(false);
    expect(failed.status).toBe("canary-fail");
    expect(state.inits).toHaveLength(1);

    delete state.files[DETECTOR_CANARY_FILE];
    const noCanary = new EmergencyDetector();
    expect(await noCanary.load()).toBe(false);
    expect(await noCanary.load()).toBe(false);
    expect(noCanary.status).toBe("no-canary");
    expect(state.inits).toHaveLength(2);

    state.files[DETECTOR_HEAD_FILE] = "{not json";
    state.files[DETECTOR_CANARY_FILE] = JSON.stringify(canaryFile());
    const broken = new EmergencyDetector();
    expect(await broken.load()).toBe(false);
    expect(broken.status).toBe("load-failed");
    state.files[DETECTOR_HEAD_FILE] = JSON.stringify(head);
    expect(await broken.load()).toBe(false);
    expect(state.inits).toHaveLength(2);

    state.vector = (text: string) => vecFor(text.includes("choking") ? 30 : -30);
    delete state.files[DETECTOR_MODEL_FILE];
    const absent = new EmergencyDetector();
    expect(await absent.load()).toBe(false);
    expect(absent.status).toBe("absent");
    state.files[DETECTOR_MODEL_FILE] = "gguf";
    expect(await absent.load()).toBe(true);
    expect(absent.status).toBe("ready");
    expect(state.inits).toHaveLength(3);
  });

  it("deviceEmergencyScore is null when the detector is not installed (answer() then uses the lexical predicate)", async () => {
    state.files = {};
    expect(await deviceEmergencyScore("my child is choking", [])).toBeNull();
  });
});
