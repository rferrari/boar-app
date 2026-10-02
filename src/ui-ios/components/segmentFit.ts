/**
 * Whether a row of segments fits, from the control's width and the longest label. Pure for vitest.
 * The label width is estimated (Lexend ~0.58 em per character) because measuring every label before
 * the first render would flash; the estimate errs wide, so a tight row stacks rather than breaking a word.
 */
export const EM_PER_CHAR = 0.58;

export function segmentsFit(opts: {
  width: number;
  count: number;
  longestLabel: number;
  /** Rendered font size of a label (already multiplied by the OS font scale). */
  fontSize: number;
  /** Horizontal chrome per segment: padding on both sides plus the check glyph and its gap. */
  chrome: number;
  gap: number;
}): boolean {
  const { width, count, longestLabel, fontSize, chrome, gap } = opts;
  if (width <= 0 || count <= 0) return true; // not measured yet: render the row, re-decide on layout
  const column = (width - gap * (count - 1)) / count;
  return longestLabel * EM_PER_CHAR * fontSize + chrome <= column;
}
