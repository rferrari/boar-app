/**
 * The welcome teaser's button: locked until the preview disclaimer is ticked, then one of these
 * film and book nods, picked at random each time the Sanctuary opens. Short phrases, no emojis.
 */
export const LOCKED_LABEL = "You shall not pass!";

export const UNLOCKED_LABELS = [
  "Down the rabbit hole…",
  "Speak, friend, and enter",
  "Take the red pill",
  "Jack in to the Sanctuary",
  "BOAR knows kung fu",
  "Where we're going, we don't need roads",
  "This is the way",
  "Punch it, Chewie!",
  "Don't panic, and enter",
  "Open the pod bay doors, BOAR",
] as const;

/** One of the labels at random; never `avoid` (the one on screen), so ticking again always shows a new one. */
export function pickLabel(random: () => number = Math.random, avoid?: string): string {
  const pool = avoid ? UNLOCKED_LABELS.filter((l) => l !== avoid) : [...UNLOCKED_LABELS];
  return pool[Math.min(pool.length - 1, Math.floor(random() * pool.length))];
}
