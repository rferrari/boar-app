/**
 * Chat layout at large text (fontScale >= LARGE_TEXT_SCALE):
 * - a suggestion shows the whole question (Prism CH-11: at 2.0 a 58-character question needs 3 lines, and
 *   tapping sent a question nobody could read in full); 2 lines at normal sizes keep the cards even;
 * - the model error's two buttons stack (Prism CH-12: side by side, "Configurar"/"Recarregar" broke mid-word);
 * - the snippet's overline has no clamp (Prism AN-1: at 200% "Da fonte (em inglês) · Monso…" lost the source's
 *   name; 2 lines weren't enough either: "… · Intertropical Convergence Zone" takes 3, 3-4 at iOS AX5).
 */
export function chatLargeText(large: boolean): { suggestionLines: number | undefined; errorButtonBasis: "100%" | "40%"; snippetOverlineLines: number | undefined } {
  return large
    ? { suggestionLines: undefined, errorButtonBasis: "100%", snippetOverlineLines: undefined }
    : { suggestionLines: 2, errorButtonBasis: "40%", snippetOverlineLines: 1 };
}
