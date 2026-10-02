/**
 * How the answer's sources are named and grouped in the source card.
 *
 * The corpus stores `source` as "Wikipedia — https://en.wikipedia.org/wiki/X (CC BY-SA 4.0)":
 * name, URL and license in one string. The card shows the name as the overline and the URL
 * apart, in normal case (Prism S-2). Passages of the same article (same docId) are one row
 * with its passages inside (Iris: "Nosebleed", "Nosebleed", "Nosebleed" read as a bug).
 */
export interface SourceParts {
  name: string | null;
  url: string | null;
}

const URL_RE = /\bhttps?:\/\/[^\s)]+/i;

export function sourceParts(source: string | undefined): SourceParts {
  const s = source?.trim();
  if (!s) return { name: null, url: null };
  const [head, ...tail] = s.split(/\s+[—–]\s+/);
  if (tail.length > 0 && !URL_RE.test(head)) {
    // After the dash: the URL, which may hold spaces (pack URLs carry the raw title, Prism SR-1),
    // then an optional " (license)".
    const rest = tail.join(" — ").replace(/\s+\([^()]*\)\s*$/, "").trim();
    return { name: head.trim() || null, url: /^https?:\/\//i.test(rest) ? rest : rest.match(URL_RE)?.[0] ?? null };
  }
  const url = s.match(URL_RE)?.[0] ?? null;
  if (!url) return { name: s, url: null };
  try {
    return { name: new URL(url).hostname.replace(/^www\./, ""), url };
  } catch {
    return { name: null, url };
  }
}

type Chunk = { docId: string; title: string };

export interface SourceGroup {
  /** The article (docId); passages of one article share it. */
  key: string;
  title: string;
  /** Indexes into the answer's sources, in citation order ([n] = index + 1). */
  indexes: number[];
}

/** One group per article, in the order the article is first cited; `only` limits it to those indexes. */
export function groupSources(sources: Chunk[], only?: number[]): SourceGroup[] {
  const groups: SourceGroup[] = [];
  const byKey = new Map<string, SourceGroup>();
  const keep = only ? new Set(only) : null;
  sources.forEach((s, i) => {
    if (keep && !keep.has(i)) return;
    const key = s.docId || `#${i}`;
    let g = byKey.get(key);
    if (!g) {
      g = { key, title: s.title, indexes: [] };
      byKey.set(key, g);
      groups.push(g);
    }
    g.indexes.push(i);
  });
  return groups;
}

export type RelevanceBand = "high" | "medium" | "low";

/** Thresholds on the engine's relevance (0..1), suggested by Tusk. */
export const RELEVANCE_BANDS = { high: 0.75, medium: 0.5 } as const;

/**
 * The relevance of each source as a band, not a percentage (Tusk, CT-2 follow-up): the engine's
 * relevance is the share of the match query's terms the best sentence covers, so its scale depends
 * on the query's length (a PT question through the dictionary becomes 3 canonical terms and tends
 * to 1.0; the same question in EN reads 0.59). A band says what the number can support.
 * A source without a positive measured value gets none (null).
 */
export function relevanceBands(sources: object[]): (RelevanceBand | null)[] {
  return sources.map((s) => bandOf((s as { relevance?: unknown }).relevance));
}

export function bandOf(v: unknown): RelevanceBand | null {
  if (typeof v !== "number" || !Number.isFinite(v) || v <= 0) return null;
  return v >= RELEVANCE_BANDS.high ? "high" : v >= RELEVANCE_BANDS.medium ? "medium" : "low";
}

/** The best band among a group's passages. */
export function bestBand(bands: (RelevanceBand | null)[]): RelevanceBand | null {
  return bands.includes("high") ? "high" : bands.includes("medium") ? "medium" : bands.includes("low") ? "low" : null;
}

/** How much of the bar a band fills (3 steps). */
export const BAND_FILL: Record<RelevanceBand, number> = { high: 1, medium: 2 / 3, low: 1 / 3 };

/**
 * Which sources the answer actually rests on (Prism CT-2, Tusk done.cited): `cited` are the
 * indexes whose [n] stayed in the final text, plus the instant passage when it is shown; the
 * rest are only "related". null when the engine said nothing about it (older answers, a tier
 * still running): every source is shown as before.
 */
export function citedSplit(count: number, cited: number[] | undefined, instantIndex?: number): { cited: number[]; related: number[] } | null {
  if (!cited) return null;
  const set = new Set(cited.filter((n) => Number.isInteger(n) && n >= 1 && n <= count).map((n) => n - 1));
  if (instantIndex != null && instantIndex >= 0 && instantIndex < count) set.add(instantIndex);
  const all = Array.from({ length: count }, (_, i) => i);
  return { cited: all.filter((i) => set.has(i)), related: all.filter((i) => !set.has(i)) };
}

/** citedSplit for an answer: the instant passage counts as cited when it is shown. */
export function answerSourceSplit(a: {
  sources: unknown[];
  cited?: number[];
  instant?: { sourceIndex: number };
  weakSources?: boolean;
}): { cited: number[]; related: number[] } | null {
  return citedSplit(a.sources.length, a.cited, a.instant && !a.weakSources ? a.instant.sourceIndex : undefined);
}

/**
 * What the sources slot shows (Prism CT-2): while the answer is written and the engine hasn't said
 * which [n] stayed, only the count; then the cited sources, or only "Related" when none is cited.
 * "all" = an engine or a record without cited: every source, as before.
 */
export function sourcesCardMode(active: boolean, split: { cited: number[] } | null): "found" | "related" | "cited" | "all" {
  if (!split) return active ? "found" : "all";
  return split.cited.length === 0 ? "related" : "cited";
}

/**
 * The source sheet's seal and link (Prism CH-17): the source's name in the caps badge ("Wikipedia"), never
 * the raw "Name — https://… (license)" string (an upper-cased URL in a pill), and the URL apart, without
 * its scheme, as a caption. The user's own documents say so; an unnamed source is the offline library.
 */
export function sourceSeal(
  chunk: { source?: string; collectionId?: string | null },
  labels: { myDocuments: string; corpus: string }
): { label: string; url: string | null } {
  if (chunk.collectionId) return { label: labels.myDocuments, url: null };
  const parts = sourceParts(chunk.source);
  return { label: parts.name ?? labels.corpus, url: parts.url ? parts.url.replace(/^https?:\/\/(www\.)?/, "") : null };
}
