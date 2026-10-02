/** The Hugging Face search's file-size filter (Models › Search). */
export type SizeFilter = "any" | "2" | "4" | "fits";

const GB = 1024 ** 3;

/**
 * Whether a GGUF file passes the size filter. "fits" leaves a fifth of the phone's model budget
 * (availableRamFrom) for the context and buffers the file doesn't count.
 */
export function passesSizeFilter(sizeBytes: number, filter: SizeFilter, budgetBytes: number): boolean {
  if (filter === "any" || !(sizeBytes > 0)) return true;
  if (filter === "fits") return budgetBytes <= 0 || sizeBytes <= budgetBytes * 0.8;
  return sizeBytes <= Number(filter) * GB;
}
