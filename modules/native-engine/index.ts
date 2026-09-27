import { requireOptionalNativeModule, type EventSubscription } from "expo-modules-core";

export type EngineOutput =
  | { kind: "line"; text: string }
  | { kind: "stderr"; text: string }
  /** A colibri "DATA <id> <size>" frame, payload decoded as UTF-8. */
  | { kind: "data"; id: string; text: string };

interface NativeEngineModule {
  nativeLibraryDir(): string;
  pathSize(path: string): number;
  cpuInfo(): { features: string; maxFreqKHz: number[] };
  isRunning(): boolean;
  start(binary: string, args: string[], env: Record<string, string>): Promise<boolean>;
  write(text: string): void;
  stop(): Promise<void>;
  addListener(event: "onOutput", listener: (e: EngineOutput) => void): EventSubscription;
  addListener(event: "onExit", listener: (e: { code: number }) => void): EventSubscription;
}

// Optional: a JS bundle reloaded onto an older build without this module keeps working with
// llama.rn only; external engines report themselves unavailable.
const native = requireOptionalNativeModule<NativeEngineModule>("NativeEngine");

export const nativeEngineAvailable = native !== null;

function need(): NativeEngineModule {
  if (!native) throw new Error("This build has no native engine module; rebuild the app.");
  return native;
}

export const NativeEngine = {
  nativeLibraryDir: () => native?.nativeLibraryDir() ?? "",
  /** Bytes at an absolute path (a directory's files added up), or -1 when missing or unreadable. */
  pathSize: (path: string) => native?.pathSize(path) ?? -1,
  /** CPU feature flags and each core's highest frequency (kHz, 0 when unknown). */
  cpuInfo: () => native?.cpuInfo() ?? { features: "", maxFreqKHz: [] },
  isRunning: () => native?.isRunning() ?? false,
  start: (binary: string, args: string[], env: Record<string, string> = {}) => need().start(binary, args, env),
  write: (text: string) => need().write(text),
  stop: () => native?.stop() ?? Promise.resolve(),
  onOutput: (listener: (e: EngineOutput) => void) => need().addListener("onOutput", listener),
  onExit: (listener: (e: { code: number }) => void) => need().addListener("onExit", listener),
};
