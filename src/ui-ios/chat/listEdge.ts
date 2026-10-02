import { hexToRgb } from "../theme/oklch";

/**
 * The veil under the chat header (Prism CX-3): text scrolling up used to be cut in half, through the
 * letters, at the header's edge. A short fade from the screen's own colour to the same colour at zero
 * alpha (never "transparent", which is black at zero alpha and draws a grey band on the light theme).
 * It is always there, and the list starts that much lower: at the top it only covers empty space, so no
 * veil shows until the text scrolls under it, with no scroll handler to lag behind.
 */
export function headerVeil(canvasHex: string): string {
  const [r, g, b] = hexToRgb(canvasHex);
  return `linear-gradient(to bottom, rgba(${r}, ${g}, ${b}, 1) 0%, rgba(${r}, ${g}, ${b}, 0) 100%)`;
}
