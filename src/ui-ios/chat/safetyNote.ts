/**
 * Whether an answer carries the "Not a substitute for emergency services"
 * line (Boar E-1, EQ-1): the engine marked it safety (done.safety), a passage
 * came from the Emergency and preparedness pack, or the question is health,
 * first aid, a disaster or another emergency. The classifier is the engine's
 * isSafetyQuery (Tusk 2e88300: his detector + the broad list that lived here),
 * so the note and the literal-passage answer always agree. Change the list there.
 */
import { isSafetyQuery } from "../../routing/context";

export const PREPAREDNESS_PACK_ID = "boar-preparedness";

/** Kept for callers and tests: the same classifier the engine uses. */
export function isHealthQuestion(question: string): boolean {
  return isSafetyQuery(question);
}

type Src = { chunkId: string; docId?: string };

export function usesPreparednessPack(sources: Src[]): boolean {
  const tag = `pack:${PREPAREDNESS_PACK_ID}:`;
  return sources.some((s) => s.chunkId.startsWith(tag) || !!s.docId?.startsWith(tag));
}

export function needsEmergencyNote(question: string, sources: Src[], engineSafety = false): boolean {
  return engineSafety || usesPreparednessPack(sources) || isSafetyQuery(question);
}

/**
 * The note shows for any answer classified as health/safety, whatever it is made of: the model's
 * text, or only the instant passage (EQ-1: health answers are the literal passage, no model). Not
 * for a places list, and not before anything is on screen.
 */
export function showsEmergencyNote(a: {
  question: string;
  sources: Src[];
  hasModelText: boolean;
  hasSnippet: boolean;
  placesOnly: boolean;
  /** done.safety from the engine. */
  safety?: boolean;
}): boolean {
  if (a.placesOnly || (!a.hasModelText && !a.hasSnippet)) return false;
  return needsEmergencyNote(a.question, a.sources, a.safety);
}

/** showsEmergencyNote for a chat answer: the model's text, the instant passage or the engine's literal excerpt (NB-1). */
export function answerShowsEmergencyNote(
  answer: { sources: Src[]; fast?: { text: string }; deep?: { text: string }; instant?: unknown; extract?: string; safety?: boolean },
  question: string,
  placesOnly: boolean
): boolean {
  return showsEmergencyNote({
    question,
    sources: answer.sources,
    hasModelText: !!(answer.fast?.text || answer.deep?.text),
    hasSnippet: !!answer.instant || !!answer.extract,
    placesOnly,
    safety: answer.safety,
  });
}
