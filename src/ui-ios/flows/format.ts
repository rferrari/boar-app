/** Locale-aware numbers for the flow screens ("1,2 GB" in PT, "1.2 GB" in EN). */

// Files and storage: the app-wide formatter (src/models/units.ts, Ledger), decimal
// like Android and iOS show them (Prism N-14), with the locale's separators.
import { formatBytes } from "../../models/units";
export { formatBytes, formatBytesParts } from "../../models/units";

// Memory (RAM) in binary units, as phones advertise it: a "16 GB" phone has 16 GiB.
const GIB = 1024 ** 3;
const MIB = 1024 ** 2;

function number(value: number, locale: string, digits: number): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: digits, minimumFractionDigits: 0 }).format(value);
}

/** Memory sizes (device RAM, working set), in the binary units phones are sold with. */
export function formatRam(bytes: number, locale: string): string {
  if (bytes >= 999.5 * MIB) return `${number(bytes / GIB, locale, 1)} GB`;
  return `${number(Math.max(bytes, 0) / MIB, locale, 0)} MB`;
}

export function formatCount(n: number, locale: string): string {
  return number(n, locale, 0);
}

export function formatSeconds(ms: number, locale: string): string {
  return `${number(ms / 1000, locale, ms < 10_000 ? 1 : 0)} s`;
}

/** A token is about three quarters of an English word: the screen shows words, never tokens (Prism UX-2). */
export const WORDS_PER_TOKEN = 0.75;

/** Tokens as an approximate word count (a length or a per-second rate). */
export function toWords(tokens: number): number {
  return tokens * WORDS_PER_TOKEN;
}

export function formatRate(tokPerSec: number, locale: string): string {
  return number(tokPerSec, locale, 1);
}

/**
 * A failure's technical detail made readable: byte counts formatted
 * ("783 MB of 2.5 GB") and the exception text in parentheses dropped (it goes
 * to the log, not the screen; Prism MD-1). The rest of the sentence stays.
 */
export function readableErrorDetail(message: string, locale: string): string {
  return message
    .replace(/(\d{4,}) of (\d{4,}) bytes/g, (_, a, b) => `${formatBytes(Number(a), locale)} of ${formatBytes(Number(b), locale)}`)
    .replace(/(\d{4,}) bytes/g, (_, a) => formatBytes(Number(a), locale))
    .replace(/expected (\d{4,})/g, (_, a) => `expected ${formatBytes(Number(a), locale)}`)
    .replace(/\s*\([^)]*\)/g, "")
    .trim();
}

/**
 * The failure lines of a row: the cause, then the detail. A download that
 * stopped part-way has a code and numbers (Ledger's DownloadErrorDetail),
 * translated here; anything else falls back to the readable English detail.
 */
export function failureLines(
  f: { errorKind: string; message: string; detail?: { code: "interrupted" | "paused"; bytesDone: number; bytesTotal: number; stallS?: number } },
  t: (key: string, opts?: Record<string, unknown>) => string,
  locale: string
): { cause: string; detail: string } {
  const d = f.detail;
  if (d) {
    return {
      // A stall is not a lost connection: say what happened (MD-4).
      cause: t(d.code === "paused" ? "flows.row.error.stalled" : `flows.row.error.${f.errorKind}`),
      detail: t(`flows.row.errorDetail.${d.code}`, {
        done: formatBytes(d.bytesDone, locale),
        total: formatBytes(d.bytesTotal, locale),
        seconds: d.stallS ?? 0,
      }),
    };
  }
  return { cause: t(`flows.row.error.${f.errorKind}`), detail: readableErrorDetail(f.message, locale) };
}

/** Minutes, rounded up, for time-left estimates; at least 1. */
export function minutesLeft(seconds: number): number {
  return Math.max(1, Math.ceil(seconds / 60));
}

/** Minutes, to the nearest, for an up-front estimate ("~3 min" vs "~4 min"); at least 1. */
export function minutesAbout(seconds: number): number {
  return Math.max(1, Math.round(seconds / 60));
}
