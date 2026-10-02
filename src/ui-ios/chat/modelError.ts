/**
 * Why the model didn't load, from the engine's error text, and which action
 * leads. Setup (reinstalling) only helps when the file itself is the problem
 * (missing or damaged); for everything else the file is fine, so the card
 * leads with Try again and never sends the user back to setup.
 *
 * Order matters: file problems first; then explicit out-of-memory; then
 * engine/backend start failures (Metal, GPU, context), which also say
 * "failed to allocate context" and must not read as memory; then the
 * weaker memory words.
 */
export type ModelErrorKind = "memory" | "corrupt" | "missing" | "engine" | "general";

/**
 * The engine appends a device hint to load errors ("(this device has ~16GB RAM, ~xGB free; …likely the
 * cause if those are close)", LlamaEngine). It mentions RAM whatever the cause, so it is not evidence.
 */
function withoutDeviceHint(error: string): string {
  return error.replace(/\(\s*this device has[^)]*\)/gi, " ");
}

export function modelErrorKind(error: string): ModelErrorKind {
  const e = withoutDeviceHint(error).toLowerCase();
  if (/size mismatch|verification|hash|corrupt|truncat|invalid (gguf|magic)|bad magic/.test(e)) return "corrupt";
  if (/not found|no such file|enoent|missing/.test(e)) return "missing";
  // A named engine/backend failure wins over any mention of memory (Iris: Metal on a 16 GB phone is not "not enough memory").
  if (/metal|\bmtl\d*\b|backend|\bgpu\b|vulkan|opencl|xpc_/.test(e)) return "engine";
  if (/out of memory|\boom\b|insufficient memory|not enough memory|\bram\b/.test(e)) return "memory";
  // llama.rn surfaces only "Failed to load model" to JS for a Metal/backend start failure (the
  // details stay in the native log); a damaged GGUF says the same, so the card claims only that
  // the file is present (a missing file arrives earlier as "Model not found at …").
  if (/failed to load model|metal|\bmtl\d*\b|backend|\bgpu\b|vulkan|opencl|xpc_|context/.test(e)) return "engine";
  if (/memory|allocate/.test(e)) return "memory";
  return "general";
}

export function modelErrorPrimary(kind: ModelErrorKind): "setup" | "retry" {
  return kind === "missing" || kind === "corrupt" ? "setup" : "retry";
}

/** The engine's own words are worth showing when the cause isn't a plain file or memory problem. */
export function showsRawError(kind: ModelErrorKind): boolean {
  return kind === "engine" || kind === "general";
}
