// Gate item "current-events" (Boar, CT-3, engine-routing 93a7f3f): a current-event question ("Who won the football
// match yesterday?") gets the fixed offline answer (library snapshot date) without the model, sources or [n]; the
// negatives (history, a timeless "today", "latest theory") are answered normally, not with the offline text.
const OFFLINE = { en: /I'm offline, and my library is a snapshot/i, pt: /Estou offline, e o meu acervo/i };
const POSITIVE = new Set(["ce-001", "ce-002"]);

/** @returns {{ pass: boolean, failures: string[], warnings: string[] } | null} */
export function checkCurrentEvents(row) {
  if (!row.queryId?.startsWith("ce-")) return null;
  const answer = row.answer ?? "";
  const failures = [];
  const offline = OFFLINE.en.test(answer) || OFFLINE.pt.test(answer);
  if (POSITIVE.has(row.queryId)) {
    const pt = row.queryId === "ce-002";
    if (!(pt ? OFFLINE.pt : OFFLINE.en).test(answer)) failures.push(`not the fixed offline answer in ${pt ? "PT" : "EN"}: "${answer.slice(0, 120)}"`);
    if (/\[\d+\]/.test(answer)) failures.push("cites [n]");
    if ((row.retrievedTitles ?? []).length) failures.push(`shows sources: ${row.retrievedTitles.join(", ")}`);
    if (row.modelCalled) failures.push("called the model");
  } else {
    if (offline) failures.push("answered with the current-events offline text (false positive)");
    if (!answer.trim()) failures.push("empty answer");
    // R3 "What happened today in history?": uses the run's date (TD-1 puts it in the prompt) or refuses honestly.
    if (row.queryId === "ce-r3" && !offline && answer.trim()) {
      const d = new Date(row.createdAt ?? Date.now());
      const month = d.toLocaleString("en-US", { month: "long", timeZone: "UTC" });
      const day = d.getUTCDate();
      const dated = new RegExp(`${month}\\s+${day}\\b|\\b${day}(st|nd|rd|th)?\\s+(of\\s+)?${month}`, "i").test(answer);
      const honest = /did(n't| not) find|(no|don't have a) (reliable |good )?(offline )?source|not from an offline source|n[ãa]o encontrei/i.test(answer);
      if (!dated && !honest) failures.push(`does not use today's date (${month} ${day}) and does not say it has no source`);
    }
  }
  return { pass: failures.length === 0, failures, warnings: [] };
}
