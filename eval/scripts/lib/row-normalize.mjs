// Runs recorded before the runner captured warnings (d880fa0 and earlier) hold an empty answer for the Compacto's
// decline, which the app shows as a warning ("I didn't find this in this phone's library."). Graders read that message.
const DECLINE = { en: "I didn't find this in this phone's library.", pt: "Não encontrei isso no acervo deste celular." };
const PT = /[ãõçáéíóúâê]|\b(por que|como|qual|quem|o que|onde)\b/i;

export function normalizeRow(r) {
  if ((r.answer ?? "").trim()) return r;
  const codes = r.reasonCodes ?? [];
  if (!codes.some((c) => c === "grounding:declined-compact" || c === "grounding:uncited-declined-compact")) return r;
  return { ...r, answer: PT.test(r.query ?? "") ? DECLINE.pt : DECLINE.en, outcome: "success", declined: true, normalized: "compact decline" };
}
