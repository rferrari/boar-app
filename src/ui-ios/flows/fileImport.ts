/** A file picked for import and what happened to it. No native imports. */
import type { IntegrityErrorKind } from "../../models/integrity";

export interface FileImport {
  name: string;
  status: "importing" | "verified" | "failed";
  /** 0..1 of the file hashed so far. */
  progress: number;
  /** The picked file's size, when the system picker reports it. */
  sizeBytes?: number;
  /** After a verified import: what the item still needs on the phone (e.g. the city index for a places pack). */
  missing?: { label: string; sizeBytes: number }[];
  assetId?: string;
  errorKind?: IntegrityErrorKind;
  message?: string;
  /** Catalog items whose row asked for this file, so the row shows the result where the user tapped (Prism IM-1). */
  forIds?: string[];
}

/**
 * What a row should say about the files it asked for: nothing once one of
 * them matched it; else a file still being checked, then a refusal, then a
 * file that turned out to be another item.
 */
export function importFor(imports: FileImport[], id: string): FileImport | undefined {
  const mine = imports.filter((f) => f.forIds?.includes(id));
  if (mine.some((f) => f.status === "verified" && f.assetId === id)) return undefined;
  return mine.find((f) => f.status === "importing") ?? mine.find((f) => f.status === "failed") ?? mine.find((f) => f.status === "verified");
}

const normal = (name: string) => name.toLowerCase().replace(/[^a-z0-9]/g, "");

/**
 * The catalog item a file being imported most likely is, before its hash
 * says so: same size (the picker reports it and the catalog has it exact),
 * else the same name once case and punctuation are dropped (the catalog
 * stores 'qwen3-4b-instruct-2507-q4km.gguf' for 'Qwen3-4B-Instruct-2507-Q4_K_M.gguf').
 * Only for showing progress on the right row; verification still decides.
 */
export function likelyTarget<T extends { id: string; filename: string; sizeBytes: number }>(
  file: Pick<FileImport, "name" | "sizeBytes">,
  items: T[]
): T | undefined {
  if (file.sizeBytes) {
    const bySize = items.filter((i) => i.sizeBytes === file.sizeBytes);
    if (bySize.length === 1) return bySize[0];
  }
  const name = normal(file.name);
  return items.find((i) => normal(i.filename.split("/").pop() ?? "") === name);
}
