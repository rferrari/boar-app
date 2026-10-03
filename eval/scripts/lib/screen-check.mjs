// CIT-2 on the chat screen (Prism, Boar 2026-09-27: "What causes the greenhouse effect?" showed "[1]. [1]"). The row's
// `screen` is the tree's own chat reducer state (runner): the instant passage with its "[n]" button, the extract and
// the fast/deep texts. The same [n] must not show twice around one sentence end, including a passage that ends with
// "[n]" right above its "[n]" button.
const DUP = /\[(\d+)\][ \t]*[.!?]?[ \t]*\[\1\]/;

/** @returns {null | { pass: boolean, where?: string, text?: string }} null = no screen recorded */
export function checkScreen(row) {
  const s = row.screen;
  if (!s) return null;
  const parts = [
    ["passage", s.snippet ? `${s.snippet.text.trimEnd()} ${s.snippet.button}` : ""],
    ["extract", s.extract ?? ""], ["fast", s.fast ?? ""], ["deep", s.deep ?? ""],
  ];
  for (const [where, text] of parts) {
    const m = text.match(DUP);
    if (m) return { pass: false, where, text: text.slice(Math.max(0, m.index - 60), m.index + m[0].length) };
  }
  return { pass: true };
}
