/**
 * Where the chat learns that loading a model killed the app last time (Boar
 * CR-2): Tusk's load guard writes a mark before a load; on the next start it
 * returns the record once and clears it.
 */
export { consumeLoadCrash } from "../../inference/loadGuard";
export type { LoadCrash } from "../../inference/loadGuard";
