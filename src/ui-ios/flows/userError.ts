/**
 * What a failure means for the person, as an i18n key, instead of the raw exception text on
 * screen (DS: "say what happened, why, next action"; Prism FL-11). The raw text stays available
 * as a detail (EmptyState `detail`) and in the logs, never as the body or a toast.
 */
import { AssetIntegrityError } from "../../models/integrity";

/** Causes with a sentence under flows.row.error.* (EN + PT). */
const KNOWN = new Set(["network", "stalled", "storage", "hash-mismatch", "empty-file", "unreadable-file", "size-mismatch", "offline-variant", "load", "unknown-file", "too-large", "no-source", "corrupt"]);

const STORAGE = /ENOSPC|no space|not enough (free )?space|disk (is )?full|storage full/i;
const NETWORK = /network|timed? ?out|offline|ENOTFOUND|ECONN|EAI_AGAIN|internet|unreachable|connection/i;
/** A file that opened but isn't what it should be (Knowledge import: "Couldn't read … End-of-File", Prism NA-3). */
const CORRUPT = /end.of.file|\bEOF\b|corrupt|malformed|unexpected end|couldn.?t (read|parse)|failed to parse|parse error|invalid (pdf|file|format|header|xref|zip)|bad (xref|zip|header)/i;
const UNREADABLE = /EACCES|EPERM|permission|denied|couldn.?t be opened|not readable|no such file|ENOENT/i;

export function rawErrorText(e: unknown): string {
  if (typeof e === "string") return e;
  const message = (e as { message?: unknown } | null)?.message;
  return typeof message === "string" ? message : String(e);
}

/** The i18n key (flows.row.error.<cause>) for an error or its message. */
export function userErrorKey(e: unknown): string {
  if (e instanceof AssetIntegrityError && KNOWN.has(e.kind)) return `flows.row.error.${e.kind}`;
  const text = rawErrorText(e);
  if (STORAGE.test(text)) return "flows.row.error.storage";
  if (NETWORK.test(text)) return "flows.row.error.network";
  if (CORRUPT.test(text)) return "flows.row.error.corrupt";
  if (UNREADABLE.test(text)) return "flows.row.error.unreadable-file";
  return "flows.row.error.unknown";
}
