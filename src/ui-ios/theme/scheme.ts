import type { Appearance } from "../../models/settings";
import type { ColorScheme } from "./tokens";

/** The identity is Fogueira at night: dark until the user picks otherwise. The OS setting is opt-in ("system"). */
export const DEFAULT_APPEARANCE: Appearance = "dark";

/** Applies the user's appearance choice to the OS scheme. Unknown OS value falls back to dark (the app's historical look). */
export function resolveScheme(appearance: Appearance, system: string | null | undefined): ColorScheme {
  if (appearance === "light" || appearance === "dark") return appearance;
  return system === "light" ? "light" : "dark";
}
