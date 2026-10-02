/** Setup import (offline build): what the install step shows while a pick of files is imported. No native imports. */
import type { FileImport } from "./fileImport";
import type { RowState } from "./modelRowState";

/**
 * A file verified in the running pick is on the phone, even though the catalog only refreshes after
 * the whole pick. Without this the counter stayed at "File 1 of 4" and a row went back to "To import"
 * after its file reached 100% (Prism L3-3).
 */
export function withVerifiedImport(state: RowState, assetId: string, imports: FileImport[]): RowState {
  if (state.kind !== "not-installed" && state.kind !== "failed") return state;
  return imports.some((f) => f.status === "verified" && f.assetId === assetId) ? { kind: "installed", verified: true } : state;
}

/**
 * The hero bar while importing: the whole setup's bytes (files already in + the one being copied), not
 * the current file's own progress, which fell from 100% to 0% at every file (Prism L3-4).
 */
export function importHeroFraction(doneBytes: number, totalBytes: number, active?: Pick<FileImport, "progress" | "sizeBytes">): number {
  if (totalBytes <= 0) return active?.progress ?? 0;
  const moving = active?.sizeBytes ? active.sizeBytes * active.progress : 0;
  return Math.min(1, (doneBytes + moving) / totalBytes);
}
