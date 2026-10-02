/**
 * Accessibility value for `Stepper`. Pure (no RN imports) for vitest.
 *
 * A numeric {min, max, now} makes iOS speak a percentage computed from the
 * range (Harbor measured 33%…133% for steps 1-4 with min 1). Steps are not a
 * percentage, so the value is text only; the label carries "Step n of N: name".
 */
export function stepperValue(stepCount: number, current: number): { text: string } {
  const now = Math.min(Math.max(current, 0), Math.max(stepCount - 1, 0)) + 1;
  return { text: `${now}/${stepCount}` };
}
