/**
 * Geometry of the mascot disc (header avatar, brand line), from the user's
 * identity file: the 1024px boar drawn at `background-size: 192%` and
 * `background-position: 72% 40%` inside an accent circle. Pure, for vitest.
 */

/** The identity file's source image, and the square we cropped from it for assets/mascot.png. */
export const SOURCE_PX = 1024;
export const CROP = { x: 114, y: 132, side: 738 } as const;
/** Bounding box of the opaque pixels in the source (the rest is transparent). */
export const OPAQUE = { x0: 138, y0: 198, x1: 828, y1: 804 } as const;
/** Mockup CSS for the disc. */
export const DISC_CSS = { sizePct: 192, posX: 72, posY: 40 } as const;

/**
 * Size and offset of assets/mascot.png inside a disc of diameter `d`, so the
 * disc shows the same window of the boar (the face under the hat) as the mockup.
 */
export function discImage(d: number): { size: number; left: number; top: number } {
  const k = DISC_CSS.sizePct / 100;
  // Window of the source image visible in the disc, in source pixels.
  const windowPx = SOURCE_PX / k;
  const x0 = ((k - 1) * DISC_CSS.posX) / 100 * windowPx;
  const y0 = ((k - 1) * DISC_CSS.posY) / 100 * windowPx;
  const scale = d / windowPx; // points per source pixel
  return {
    size: CROP.side * scale,
    left: (CROP.x - x0) * scale,
    top: (CROP.y - y0) * scale,
  };
}

/**
 * Where assets/mascot.png goes when the mockup draws the ORIGINAL 1024 px image in a box of
 * `n` pt (object-fit: contain). Our asset is a crop of it, so drawing it at `n` would show the
 * boar ~39% bigger than the mockup; this puts the crop where it sits inside the original.
 */
export function wholeImage(n: number): { size: number; left: number; top: number } {
  const k = n / SOURCE_PX;
  return { size: CROP.side * k, left: CROP.x * k, top: CROP.y * k };
}

/**
 * The chat empty state's hero, as the mockup RENDERS it (DOM: img 170×200 at page x96.5 y113,
 * object-fit contain → the original drawn at 170 pt), relative to the 170×150 layout box.
 * The CSS says 200×200 at (-15,-25), but the image is capped at 170 wide, so the rendered
 * boar is 170 pt at (-15, -10). Using the declared 200 made it 17% bigger and 14 pt to the right.
 */
export const HERO_ORIGINAL = { size: 170, left: -15, top: -10 } as const;

export function heroImage(): { size: number; left: number; top: number } {
  const w = wholeImage(HERO_ORIGINAL.size);
  return { size: w.size, left: HERO_ORIGINAL.left + w.left, top: HERO_ORIGINAL.top + w.top };
}
