import { englishNamesIn } from "../../rag/ptLexicon";
import { ptLexicon } from "../../rag/ptLexiconAsset";

/**
 * A long one-word "question" no Portuguese name matches: englishNamesIn falls back to the English titles and
 * builds that map (173k normalized titles). "fahrenheit" would not do it: it is a lexicon key, so it hits first.
 */
export const WARMUP_WORD = "boarwarmup";

/** Only where Portuguese questions are expected: the lexicon is several MB of heap nobody else needs (CR-1). */
export function shouldWarmPtLexicon(locale: string | undefined): boolean {
  return !!locale && locale.toLowerCase().startsWith("pt");
}

let warmed = false;

/**
 * Builds the Portuguese lexicon and its English-titles map ahead of the first question (audit #5: the first
 * Portuguese question froze the JS thread while both were built on the send path). Once per process.
 */
export function warmPtLexicon(lexicon = ptLexicon): void {
  if (warmed) return;
  warmed = true;
  englishNamesIn(WARMUP_WORD, lexicon());
}
