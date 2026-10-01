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

export function pickLabel(random: () => number = Math.random): string {
  return UNLOCKED_LABELS[Math.min(UNLOCKED_LABELS.length - 1, Math.floor(random() * UNLOCKED_LABELS.length))];
}
