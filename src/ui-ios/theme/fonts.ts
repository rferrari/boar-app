/**
 * Brand fonts, bundled with the app (no network): Baloo 2 for display and
 * titles, Lexend for body and data. Both SIL Open Font License 1.1, from the
 * @expo-google-fonts packages. Code blocks use the system monospace.
 *
 * Custom fonts don't synthesize weights reliably on Android, so each weight is
 * its own family name and components never set `fontWeight` with them.
 */
import { Platform } from "react-native";

export type FontFace = "display" | "text" | "code";
export type FontWeight = 400 | 500 | 600 | 700 | 800;

/** Every family we bundle. fontFiles.ts must provide a file for each (checked by tsc). */
export const BUNDLED_FAMILIES = [
  "Baloo2_700Bold",
  "Baloo2_800ExtraBold",
  "Lexend_400Regular",
  "Lexend_500Medium",
  "Lexend_600SemiBold",
  "Lexend_700Bold",
] as const;
export type BundledFamily = (typeof BUNDLED_FAMILIES)[number];

const CODE_FAMILY = Platform.select({ ios: "Menlo", default: "monospace" });

/** Family name for a face and weight, snapping to the weights we ship. */
export function fontFamilyFor(face: FontFace, weight: FontWeight): BundledFamily | string {
  if (face === "code") return CODE_FAMILY;
  if (face === "display") return weight >= 800 ? "Baloo2_800ExtraBold" : "Baloo2_700Bold";
  if (weight >= 700) return "Lexend_700Bold";
  if (weight >= 600) return "Lexend_600SemiBold";
  if (weight >= 500) return "Lexend_500Medium";
  return "Lexend_400Regular";
}
