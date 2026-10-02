/**
 * The mockup's vertical rhythm for every flow screen (FIDELITY phase 2): 14 pt
 * between blocks, as measured in the setup (the Screen default is 24).
 */
import type { buildTokens } from "../theme";

export function screenRhythm(t: ReturnType<typeof buildTokens>) {
  return { gap: t.space.md + t.space.xxs };
}
