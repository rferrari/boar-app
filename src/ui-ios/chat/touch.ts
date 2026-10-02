/**
 * hitSlop that raises a one-line text control to the platform's touch minimum (44 iOS / 48 Android), as
 * TextAction does: half the missing height above and below, `side` left and right (Prism CH-5, CH-6).
 */
export function lineSlop(touch: number, lineHeight: number, side: number) {
  const v = Math.max(0, (touch - lineHeight) / 2);
  return { top: v, bottom: v, left: side, right: side };
}
