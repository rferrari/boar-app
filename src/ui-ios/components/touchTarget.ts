/** Pure for vitest: how far a control's hit area reaches past its visual box to meet the platform minimum. */
export function touchSlop(visual: number, touch: number): number {
  return Math.max(0, (touch - visual) / 2);
}
