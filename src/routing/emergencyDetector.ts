import { Platform } from "react-native";
import * as FileSystem from "expo-file-system/legacy";
import { getBackendDevicesInfo, initLlama, type LlamaContext } from "llama.rn";
import { cpuDeviceNames, initWithCpuFallback } from "../inference/initFallback";
import { DETECTOR_THRESHOLD, detectorText, mmbertProbability, type DetectorHead, type MmbertHead } from "./emergencyHead";

/**
 * The learned emergency detector on the device (Forge device-detector arm; Sonar's frozen health13 config).
 * Desktop runs score through boar-eval's runner (llama-server + emergencyHead.ts); this is the same pipeline on
 * llama.rn, so a phone answers health questions with the detector the desktop numbers were measured with:
 *   - its own embedding context (mmBERT-small GGUF, encoder only): pooling "mean" (llama.rn's initLlama maps the string to the native int; an int would map to undefined = no pooling),
 *     n_parallel 1 (the 256k-vocab logits buffer is 1 MB per sequence), 512 ctx/batch/ubatch, 2 threads; resident;
 *   - input = detectorText(): 126 content tokens counted with tokenize() (no BOS/EOS; embedding() adds both);
 *   - embedding(text, { embd_normalize: -1 }): the RAW mean-pooled vector, then the JS head (mmbertProbability).
 * Files come with the kit (models/emergency-detector.gguf + .head.json, the frozen pair a86fe8b9… / 766434de…).
 * After init, a 2-string canary (vector cos, L2 and p vs the desktop reference) must pass, else the detector stays off.
 * Missing files → load() is false and answer() gets no detector (lexical predicate, health10 behaviour). Any error
 * while scoring throws; answer.ts turns it into null → lexical. Never "not emergency" by failure.
 */
export const DETECTOR_MODEL_FILE = "models/emergency-detector.gguf";
export const DETECTOR_HEAD_FILE = "models/emergency-detector.head.json";
/** Load-time canary (Sonar, detector-canary.v3b.json): 2 strings with the desktop b11010 reference vector, L2 and p. */
export const DETECTOR_CANARY_FILE = "models/emergency-detector.canary.json";

export type DetectorStatus = "absent" | "loading" | "ready" | "no-canary" | "canary-fail" | "load-failed";
/** Load outcomes that a retry in the same session would not change; load() does not re-init after them. */
const TERMINAL_STATUSES: ReadonlySet<DetectorStatus> = new Set(["canary-fail", "no-canary", "load-failed"]);
export interface CanaryItem { text: string; p: number; l2: number; vec: number[] }
export interface CanaryFile { model_sha256: string; head_sha256: string; thresholds: { cos_min: number; l2_rel_max: number; p_abs_max: number }; items: CanaryItem[] }

/**
 * One canary item vs the device's measurement. p alone is not enough (Sonar: last-token pooling also gives p 1/0 on
 * these strings); the vector must match too: cos >= cos_min, relative L2 within l2_rel_max, |dp| <= p_abs_max.
 */
export function canaryCheck(ref: CanaryItem, t: CanaryFile["thresholds"], vec: ArrayLike<number>, p: number) {
  let dot = 0, nv = 0, nr = 0;
  const n = Math.min(vec.length, ref.vec.length);
  for (let i = 0; i < n; i++) {
    dot += vec[i] * ref.vec[i];
    nv += vec[i] * vec[i];
    nr += ref.vec[i] * ref.vec[i];
  }
  const cos = vec.length === ref.vec.length && nv > 0 && nr > 0 ? dot / Math.sqrt(nv * nr) : 0;
  const l2Rel = Math.abs(Math.sqrt(nv) - ref.l2) / ref.l2;
  const dp = Math.abs(p - ref.p);
  return { ok: cos >= t.cos_min && l2Rel <= t.l2_rel_max && dp <= t.p_abs_max, cos, l2Rel, dp };
}

export class EmergencyDetector {
  private context: LlamaContext | null = null;
  private head: MmbertHead | null = null;
  private loading: Promise<boolean> | null = null;
  private queue: Promise<unknown> = Promise.resolve();
  /** Why the detector is (not) answering; BOAR Bench writes it into each run's sidecar. */
  status: DetectorStatus = "absent";
  canary: { text: string; cos: number; l2Rel: number; dp: number; ok: boolean }[] = [];

  /** Serializes llama.rn calls on the detector context ("Context is busy" otherwise). */
  private enqueue<T>(task: () => Promise<T>): Promise<T> {
    const run = this.queue.then(task, task);
    this.queue = run.catch(() => {});
    return run;
  }

  get loaded(): boolean {
    return this.context !== null && this.head !== null;
  }

  /**
   * Loads once; true when the detector is ready, false when its files are not installed or loading failed.
   * A terminal failure (canary-fail, no-canary, load-failed) is remembered: later calls return false without
   * re-initialising the mmBERT model next to the resident chat model. "absent" stays re-checkable (the kit may be
   * installed later).
   */
  load(): Promise<boolean> {
    if (this.loaded) return Promise.resolve(true);
    if (TERMINAL_STATUSES.has(this.status)) return Promise.resolve(false);
    this.loading ??= this.loadNow().finally(() => (this.loading = null));
    return this.loading;
  }

  private async loadNow(): Promise<boolean> {
    const dir = FileSystem.documentDirectory ?? "";
    const [model, head] = await Promise.all([
      FileSystem.getInfoAsync(`${dir}${DETECTOR_MODEL_FILE}`),
      FileSystem.getInfoAsync(`${dir}${DETECTOR_HEAD_FILE}`),
    ]);
    if (!model.exists || !head.exists) {
      this.status = "absent";
      return false;
    }
    this.status = "loading";
    try {
      const parsed = JSON.parse(await FileSystem.readAsStringAsync(`${dir}${DETECTOR_HEAD_FILE}`)) as DetectorHead;
      if (parsed.kind !== "mmbert-cls") throw new Error(`detector head kind ${parsed.kind} is not supported on the device`);
      const loaded = await initWithCpuFallback(
        initLlama,
        { model: `${dir}${DETECTOR_MODEL_FILE}`, embedding: true, pooling_type: "mean", n_parallel: 1, n_ctx: 512, n_batch: 512, n_ubatch: 512, n_threads: 2 },
        { platform: Platform.OS, cpuDevices: () => cpuDeviceNames(getBackendDevicesInfo), log: (m) => console.warn(m) }
      );
      const context = loaded.context;
      // Canary before first use: a wrong pooling/normalization/tokenizer path disables the detector (lexical answers).
      const canaryInfo = await FileSystem.getInfoAsync(`${dir}${DETECTOR_CANARY_FILE}`);
      if (!canaryInfo.exists) {
        await context.release();
        this.status = "no-canary";
        console.warn("[detector] no canary file; detector off, the lexical predicate answers");
        return false;
      }
      const canary = JSON.parse(await FileSystem.readAsStringAsync(`${dir}${DETECTOR_CANARY_FILE}`)) as CanaryFile;
      const countTokens = async (t: string) => (await context.tokenize(t)).tokens.length;
      this.canary = [];
      for (const item of canary.items) {
        const { embedding } = await context.embedding(await detectorText([], item.text, countTokens), { embd_normalize: -1 });
        const p = embedding.length === parsed.dim ? mmbertProbability(parsed, embedding) : NaN;
        this.canary.push({ text: item.text, ...canaryCheck(item, canary.thresholds, embedding, p) });
      }
      if (!this.canary.length || this.canary.some((c) => !c.ok)) {
        await context.release();
        this.status = "canary-fail";
        console.warn("[detector] canary FAILED; detector off, the lexical predicate answers:", JSON.stringify(this.canary));
        return false;
      }
      this.context = context;
      this.head = parsed;
      this.status = "ready";
      return true;
    } catch (e: any) {
      console.warn("[detector] load failed; the lexical predicate answers:", e?.message ?? e);
      this.context = null;
      this.head = null;
      this.status = "load-failed";
      return false;
    }
  }

  /** P(emergency) for the message in its conversation; throws on any failure (the caller falls back to lexical). */
  score(query: string, userTurns: string[]): Promise<{ p: number; fire: boolean }> {
    return this.enqueue(async () => {
      const context = this.context, head = this.head;
      if (!context || !head) throw new Error("detector not loaded");
      const countTokens = async (t: string) => (await context.tokenize(t)).tokens.length;
      const text = await detectorText(userTurns, query, countTokens);
      const { embedding } = await context.embedding(text, { embd_normalize: -1 });
      const p = mmbertProbability(head, embedding);
      return { p, fire: p >= DETECTOR_THRESHOLD };
    });
  }

  release(): Promise<void> {
    return this.enqueue(async () => {
      await this.context?.release();
      this.context = null;
      this.head = null;
    });
  }
}

export const emergencyDetector = new EmergencyDetector();

/** answer()'s `emergencyScore` dependency: null when the detector isn't installed (lexical path). */
export async function deviceEmergencyScore(query: string, userTurns: string[]): Promise<{ p: number; fire: boolean } | null> {
  return (await emergencyDetector.load()) ? emergencyDetector.score(query, userTurns) : null;
}
