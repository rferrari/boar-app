/** A visibility change animates only when it opens, or closes a sheet that is on screen (perf audit #12). */
export function sheetAnimates(visible: boolean, mounted: boolean): boolean {
  return visible || mounted;
}

/**
 * How far the sheet slides: its own measured height, so the top is off screen on the first frame
 * whatever the sheet's size (a fixed 400 pt left the top of a taller sheet already showing, Iris
 * TR-4). Before the first layout, the window height. Reduce motion: no slide, the sheet fades.
 */
export function sheetTravel(measured: number, windowHeight: number, reduceMotion: boolean): number {
  if (reduceMotion) return 0;
  return measured > 0 ? measured : windowHeight;
}

/**
 * Drag on the grabber/header (Iris TR-12: the grabber promised a drag that did nothing). Let go past
 * a third of the sheet, or flick down, and it closes; otherwise it springs back.
 */
export function sheetDragCloses(dy: number, vy: number, height: number): boolean {
  if (dy <= 0) return false;
  return dy > height / 3 || (vy > 800 && dy > 16);
}
