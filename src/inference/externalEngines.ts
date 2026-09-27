/**
 * Experimental external inference engines, run as child processes by the native-engine module
 * (modules/native-engine) and driven over their own stdin/stdout protocols. LlamaEngine hands
 * off to one of these when the selected catalog model sets `engine`, so the chat screen, Stop,
 * the reasoning view and telemetry work unchanged. Build them with
 * scripts/build-native-engines.sh; place models with adb (docs/NATIVE_ENGINES.md).
 *
 *   colibri      https://github.com/JustVugg/colibri       OLMoE, experts streamed from storage
 *   BigMoeOnEdge https://github.com/Helldez/BigMoeOnEdge   GGUF MoE bigger than RAM
 */
import type { EventSubscription } from "expo-modules-core";
import { NativeEngine, type EngineOutput } from "native-engine";
import { getDeviceTotalRamBytes } from "ram-monitor";
import type { CatalogModel } from "../models/manifest";
import type { ChatMessageInput, GenerateOptions } from "./LlamaEngine";

export interface ExternalEngineStats {
  tokens: number;
  tokPerSec: number;
  prefillSeconds?: number;
  cacheHitPct?: number;
  readMiB?: number;
}

export interface ExternalEngine {
  readonly name: string;
  load(model: CatalogModel, opts: { nCtx: number; nThreads: number }): Promise<void>;
  generate(opts: GenerateOptions): Promise<string>;
  stop(): Promise<void>;
  unload(): Promise<void>;
  /** Stats of the last finished generation, as reported by the engine. */
  lastStats: ExternalEngineStats | null;
}

/** UTF-8 byte length, for protocols that frame by bytes. */
export function utf8Length(s: string): number {
  let n = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c < 0x80) n += 1;
    else if (c < 0x800) n += 2;
    else if (c >= 0xd800 && c <= 0xdbff) {
      n += 4;
      i++;
    } else n += 3;
  }
  return n;
}

/**
 * Chat messages as one plain prompt, for engines that take a single user turn and apply the
 * model's template themselves: the system text first, then earlier turns, then the question.
 */
export function flattenMessages(messages: ChatMessageInput[]): string {
  const system = messages.filter((m) => m.role === "system").map((m) => m.content.trim());
  const turns = messages.filter((m) => m.role !== "system");
  const last = turns.pop();
  const history = turns.map((m) => `${m.role === "assistant" ? "Assistant" : "User"}: ${m.content.trim()}`);
  return [...system, ...(history.length ? [`Conversation so far:\n${history.join("\n")}`] : []), last?.content.trim() ?? ""]
    .filter(Boolean)
    .join("\n\n");
}

/** OLMoE-Instruct's chat template, as colibri's own chat mode renders it (bos = eos = "|||IP_ADDRESS|||"). */
export function renderOlmoe(messages: ChatMessageInput[]): string {
  const EOS = "|||IP_ADDRESS|||";
  let out = EOS;
  for (const m of messages) {
    if (m.role === "system") out += `<|system|>\n${m.content}\n`;
    else if (m.role === "assistant") out += `<|assistant|>\n${m.content}${EOS}\n`;
    else out += `<|user|>\n${m.content}\n`;
  }
  return out + "<|assistant|>\n";
}

/**
 * Hex CPU mask of the `n` fastest cores (by highest frequency, lowest index first on ties), the
 * format BMOE_CPUMASK takes: "f0" is cpus 4-7. "" when frequencies are unknown.
 */
export function fastCoreMask(maxFreqKHz: number[], n: number): string {
  if (!maxFreqKHz.some((f) => f > 0)) return "";
  const picked = maxFreqKHz
    .map((f, cpu) => ({ f, cpu }))
    .sort((a, b) => b.f - a.f || a.cpu - b.cpu)
    .slice(0, Math.max(1, n));
  return picked.reduce((mask, { cpu }) => mask | (1n << BigInt(cpu)), 0n).toString(16);
}

/** Shared process plumbing: one engine process at a time, output lines routed to the active handler. */
abstract class ProcessEngine implements ExternalEngine {
  abstract readonly name: string;
  lastStats: ExternalEngineStats | null = null;
  protected loadedPath: string | null = null;
  private subs: EventSubscription[] = [];
  private onLine: ((e: EngineOutput) => void) | null = null;
  private exitWaiters: ((code: number) => void)[] = [];

  protected abstract binary: string;
  protected abstract args(model: CatalogModel, opts: { nCtx: number; nThreads: number }): string[];
  protected env(_model: CatalogModel, _opts: { nCtx: number; nThreads: number }): Record<string, string> {
    return {};
  }
  protected abstract isReady(e: EngineOutput): boolean;

  async load(model: CatalogModel, opts: { nCtx: number; nThreads: number }): Promise<void> {
    if (!model.externalPath) throw new Error(`${model.label} has no externalPath`);
    if (this.loadedPath === model.externalPath && NativeEngine.isRunning()) return;
    await this.unload();
    this.subs = [
      NativeEngine.onOutput((e) => this.onLine?.(e)),
      NativeEngine.onExit(({ code }) => {
        this.loadedPath = null;
        this.exitWaiters.splice(0).forEach((w) => w(code));
      }),
    ];
    const ready = new Promise<void>((resolve, reject) => {
      const tail: string[] = [];
      this.onLine = (e) => {
        if (e.kind === "stderr") {
          tail.push(e.text);
          if (tail.length > 8) tail.shift();
        }
        if (this.isReady(e)) resolve();
      };
      this.exitWaiters.push((code) => reject(new Error(`${this.name} exited while loading (code ${code}): ${tail.join(" | ")}`)));
    });
    await NativeEngine.start(this.binary, this.args(model, opts), this.env(model, opts));
    await ready;
    this.loadedPath = model.externalPath;
  }

  /** Runs one request: `send` writes it, `handle` sees each output event and returns the answer text when done. */
  protected run(send: () => void, handle: (e: EngineOutput) => string | undefined): Promise<string> {
    if (!this.loadedPath) return Promise.reject(new Error(`${this.name}: model not loaded`));
    return new Promise<string>((resolve, reject) => {
      const done = (fn: () => void) => {
        this.onLine = null;
        this.exitWaiters = this.exitWaiters.filter((w) => w !== onExit);
        fn();
      };
      const onExit = (code: number) => done(() => reject(new Error(`${this.name} stopped unexpectedly (code ${code})`)));
      this.exitWaiters.push(onExit);
      this.onLine = (e) => {
        try {
          const text = handle(e);
          if (text !== undefined) done(() => resolve(text));
        } catch (err) {
          done(() => reject(err));
        }
      };
      send();
    });
  }

  abstract generate(opts: GenerateOptions): Promise<string>;
  abstract stop(): Promise<void>;

  async unload(): Promise<void> {
    this.subs.forEach((s) => s.remove());
    this.subs = [];
    this.onLine = null;
    this.exitWaiters = [];
    this.loadedPath = null;
    await NativeEngine.stop();
  }
}

/** colibri's OLMoE engine in serve mode (SERVE=1): SUBMIT requests, DATA frames, DONE stats. */
export class ColibriEngine extends ProcessEngine {
  readonly name = "colibri";
  protected binary = "libcolibri_olmoe.so";
  private nextId = 1;
  private currentId: string | null = null;

  /** Experts kept in RAM per layer (of 64). 16 measured fastest on the test phone (~3 GB peak). */
  static cachePerLayer = 16;

  protected args(): string[] {
    return [String(ColibriEngine.cachePerLayer), "8"];
  }
  /** The model folder (SNAP) and context size travel in the environment. */
  protected env(model: CatalogModel, opts: { nCtx: number; nThreads: number }): Record<string, string> {
    return { SERVE: "1", SNAP: model.externalPath ?? "", CTX: String(opts.nCtx), OMP_NUM_THREADS: String(opts.nThreads) };
  }
  protected isReady(e: EngineOutput): boolean {
    return e.kind === "line" && e.text.includes("READY");
  }

  generate({ prompt, messages, nPredict = 512, temperature = 0.7, onToken }: GenerateOptions): Promise<string> {
    const payload = messages ? renderOlmoe(messages) : prompt!;
    const id = `r${this.nextId++}`;
    this.currentId = id;
    let full = "";
    return this.run(
      () => NativeEngine.write(`SUBMIT ${id} 0 ${utf8Length(payload)} ${nPredict} ${temperature} 0.95\n${payload}\n`),
      (e) => {
        if (e.kind === "data" && e.id === id) {
          full += e.text;
          onToken?.(e.text);
        } else if (e.kind === "line" && e.text.startsWith(`DONE ${id} STAT`)) {
          const [tok, tps, hit] = e.text.split(" ").slice(3).map(Number);
          this.lastStats = { tokens: tok, tokPerSec: tps, cacheHitPct: hit };
          this.currentId = null;
          return full;
        } else if (e.kind === "line" && e.text.startsWith(`ERR ${id}`)) {
          throw new Error(`colibri: ${e.text}`);
        }
        return undefined;
      }
    );
  }

  async stop(): Promise<void> {
    if (this.currentId) NativeEngine.write(`CANCEL ${this.currentId}\n`);
  }
}

/** BigMoeOnEdge's bmoe-cli in --session mode: JSON requests, BMOE_* line protocol. */
export class BmoeEngine extends ProcessEngine {
  readonly name = "BigMoeOnEdge";
  protected binary = "libbmoe_cli.so";
  private nextId = 1;
  private busy = false;
  /** Let reasoning models think before answering (slower); shown behind the 💭 toggle. */
  static think = false;

  /** Expert cache in MiB on phones with at least 11 GiB of RAM (AndroidLM's measured default). */
  static bigCacheMiB = 5000;

  async load(model: CatalogModel, opts: { nCtx: number; nThreads: number }): Promise<void> {
    // Built with i8mm (scripts/build-native-engines.sh): an older core would crash with SIGILL.
    if (!NativeEngine.cpuInfo().features.split(/\s+/).includes("i8mm")) {
      throw new Error("This BigMoeOnEdge build needs a CPU with i8mm (Cortex-A715 class or newer).");
    }
    return super.load(model, opts);
  }

  protected args(model: CatalogModel, opts: { nCtx: number; nThreads: number }): string[] {
    // Upstream's session settings, plus what AndroidLM measured on a Pixel 8 Pro
    // (notes/2026-09-24-speed-levers.md): a bigger expert cache, 2 read lanes overlapping compute,
    // and dense weights in memory the kernel won't reclaim. The micro-batch stays at 512 tokens:
    // without it the compute buffer was sized for the whole context (~4 GB at 4096).
    let ramBytes = 0;
    try {
      ramBytes = getDeviceTotalRamBytes();
    } catch {}
    const cache = ramBytes >= 11 * 1024 ** 3 ? String(BmoeEngine.bigCacheMiB) : "auto";
    return [
      "-m", model.externalPath!, "-t", String(opts.nThreads), "-c", String(opts.nCtx),
      "--ubatch", String(Math.min(512, opts.nCtx)), "--chatml", "--session",
      "--moe-stream", "--cache-mb", cache, "--io-threads", "2", "--overlap", "--dense-weights", "ahwb",
    ];
  }

  /** AndroidLM's engine patches (patches/bigmoeonedge): priority, fast-core pinning, repacked dense weights. */
  protected env(_model: CatalogModel, opts: { nCtx: number; nThreads: number }): Record<string, string> {
    const mask = fastCoreMask(NativeEngine.cpuInfo().maxFreqKHz, opts.nThreads);
    return { BMOE_NICE: "-16", BMOE_REPACK: "1", ...(mask ? { BMOE_CPUMASK: mask } : {}) };
  }
  protected isReady(e: EngineOutput): boolean {
    return e.kind === "line" && e.text.startsWith("BMOE_READY");
  }

  generate({ prompt, messages, nPredict = 512, onToken }: GenerateOptions): Promise<string> {
    const text = messages ? flattenMessages(messages) : prompt!;
    const id = this.nextId++;
    const req = JSON.stringify({ cmd: "generate", id, prompt: text, n_predict: nPredict, think: BmoeEngine.think, clear_kv: true });
    let inReasoning = false;
    let answer = "";
    this.busy = true;
    return this.run(
      () => NativeEngine.write(req + "\n"),
      (e) => {
        if (e.kind !== "line") return undefined;
        const sp = e.text.indexOf(" ");
        const tag = sp > 0 ? e.text.slice(0, sp) : e.text;
        if (tag === "BMOE_PROGRESS") {
          const p = JSON.parse(e.text.slice(sp + 1));
          if (p.reset) return undefined; // a retroactive re-parse; the final text arrives with DONE
          if (p.delta_reasoning) {
            onToken?.(inReasoning ? p.delta_reasoning : `<think>${p.delta_reasoning}`);
            inReasoning = true;
          }
          if (p.delta_text) {
            onToken?.(inReasoning ? `</think>${p.delta_text}` : p.delta_text);
            inReasoning = false;
            answer += p.delta_text;
          }
        } else if (tag === "BMOE_DONE") {
          const d = JSON.parse(e.text.slice(sp + 1));
          if (d.id !== id) return undefined;
          if (inReasoning) onToken?.("</think>");
          this.lastStats = {
            tokens: d.tokens,
            tokPerSec: d.tok_s,
            prefillSeconds: d.prefill_s,
            cacheHitPct: d.cache_hit_pct,
            readMiB: d.read_mib,
          };
          this.busy = false;
          return typeof d.text === "string" && d.text.length ? d.text : answer;
        } else if (tag === "BMOE_ERROR") {
          const d = JSON.parse(e.text.slice(sp + 1));
          this.busy = false;
          throw new Error(`BigMoeOnEdge: ${d.msg}`);
        }
        return undefined;
      }
    );
  }

  async stop(): Promise<void> {
    if (this.busy) NativeEngine.write(`{"cmd":"cancel"}\n`);
  }
}

export const colibriEngine = new ColibriEngine();
export const bmoeEngine = new BmoeEngine();

export function externalEngineFor(model: CatalogModel): ExternalEngine | null {
  if (model.engine === "colibri") return colibriEngine;
  if (model.engine === "bmoe") return bmoeEngine;
  return null;
}
