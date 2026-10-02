/**
 * One toast at a time. A toast that comes while another is up waits for it to leave (a short `exit`),
 * then enters; the latest one wins if several come (Iris TR-11: the old one used to vanish in 0 ms).
 */
export interface ToastQueue<T> {
  current: T | null;
  pending: T | null;
}

export function queueToast<T>(q: ToastQueue<T>, next: T): ToastQueue<T> {
  return q.current === null ? { current: next, pending: null } : { current: q.current, pending: next };
}

/** The current toast finished leaving: the pending one (if any) takes its place. */
export function toastLeft<T>(q: ToastQueue<T>): ToastQueue<T> {
  return { current: q.pending, pending: null };
}
