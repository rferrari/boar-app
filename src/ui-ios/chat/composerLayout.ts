/**
 * The composer's pill as the text grows (r4to: "the input only fits one line"). Pure, tested.
 *
 * Cause of the bug: on iOS (Fabric) TextInput emits onContentSizeChange only from updateLayoutMetrics,
 * i.e. when its layout changes; the composer fixed the input's `height` from that event, so a second line
 * never changed the layout, the event never came, and the input stayed one line (Android emits it on
 * every text change, so it grew there). Now the input sizes itself (native measure, both platforms, up to
 * COMPOSER_MAX_LINES then it scrolls) and the pill animates to the height the input measures.
 *
 * The mic / send / stop buttons sit inside the pill at its trailing end (CHAT_COMPOSER.md): anchored to
 * the pill's bottom, so they stay on the last line's centre while the pill grows (vertical delta 0).
 */

export const COMPOSER_MAX_LINES = 5;

export interface ComposerLayout {
  /** Space above and below the text inside the pill. */
  padV: number;
  /** The input's own cap (then it scrolls inside). */
  inputMax: number;
  /** The pill's height for one line, and for COMPOSER_MAX_LINES. */
  pillMin: number;
  pillMax: number;
  /** Gap between the pill's edge and a button inside it, for one line (the button centred on the pill). */
  inset: number;
  /** The buttons' distance from the pill's bottom: their centre on the last line's centre (never below `inset`). */
  buttonBottom: number;
}

/**
 * The mockup's pill is `composer` tall around one line of `lineHeight`; the OS scales the text (and its
 * line) by `fontScale`, so the pill grows with it and keeps the same padding. `button` is the visual
 * size of the discs inside the pill.
 */
export function composerLayout(p: { lineHeight: number; fontScale: number; composer: number; button: number }): ComposerLayout {
  const padV = (p.composer - p.lineHeight) / 2;
  const line = p.lineHeight * p.fontScale;
  const pillMin = Math.max(p.composer, line + 2 * padV);
  const inset = Math.max(0, (p.composer - p.button) / 2);
  return {
    padV,
    inputMax: line * COMPOSER_MAX_LINES,
    pillMin,
    pillMax: line * COMPOSER_MAX_LINES + 2 * padV,
    inset,
    // The last line's centre sits padV + line/2 above the pill's bottom; the disc's centre button/2 above its own.
    buttonBottom: Math.max(inset, padV + line / 2 - p.button / 2),
  };
}

/** The pill's height for the height the input measures: its text plus padding, within one to five lines. */
export function composerPillHeight(inputHeight: number, l: ComposerLayout): number {
  return Math.min(l.pillMax, Math.max(l.pillMin, inputHeight + 2 * l.padV));
}

/** Room the text leaves at the pill's end for `buttons` discs of `button`, `gap` apart, plus `gap` before the text. */
export function composerPadEnd(l: ComposerLayout, p: { buttons: number; button: number; gap: number }): number {
  if (p.buttons <= 0) return l.inset;
  return l.inset + p.buttons * p.button + (p.buttons - 1) * p.gap + p.gap;
}
