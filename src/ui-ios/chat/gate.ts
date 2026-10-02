/**
 * A one-shot gate: `open()` resolves `promise` once; `done` says whether it is open. Starts open when there
 * is nothing to wait for. The chat uses it so a question asked on a first boot waits for the offline
 * library to be indexed instead of searching an empty one (Prism HX-1).
 */
export interface Gate {
  done: boolean;
  promise: Promise<void>;
  open: () => void;
}

export function makeGate(open: boolean): Gate {
  let resolve!: () => void;
  const promise = new Promise<void>((r) => (resolve = r));
  const gate: Gate = {
    done: open,
    promise,
    open: () => {
      if (gate.done) return;
      gate.done = true;
      resolve();
    },
  };
  if (open) resolve();
  return gate;
}
