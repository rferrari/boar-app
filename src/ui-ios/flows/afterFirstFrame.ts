/**
 * Runs boot work that the first screen doesn't need once that screen is up: after the next frame,
 * then after running interactions (navigation transitions, the splash cut). The tile catalog of
 * the final world gazetteer has 8,374 entries (Intl per entry, audit #35) and used to run in the
 * mount effect, next to the boot route. Pure: the scheduler is injected (App passes React Native's).
 */
export interface FrameScheduler {
  nextFrame(cb: () => void): number;
  cancelFrame(id: number): void;
  afterInteractions(task: () => void): { cancel(): void };
}

/** Schedules `task`; never calls it synchronously. Returns a cancel for an unmount before it ran. */
export function afterFirstFrame(task: () => void, s: FrameScheduler): () => void {
  let pending: { cancel(): void } | null = null;
  const frame = s.nextFrame(() => {
    pending = s.afterInteractions(task);
  });
  return () => {
    s.cancelFrame(frame);
    pending?.cancel();
  };
}
