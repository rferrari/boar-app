/**
 * What the composer shows, from its state (docs/design/CHAT_COMPOSER.md). Pure, tested.
 *
 * One trailing slot inside the pill, as current chat apps: stop while an answer streams, done while
 * listening, the mic while the field is empty (when voice is on), send once there is text. With text,
 * the mic stays as a quiet second button before send, so dictation can add to what was typed.
 */
import type { IconName } from "../components";
import type { VoiceSupportReason } from "../../voice/voicePolicy";

export type VoicePhase = "idle" | "listening" | "finishing";
export type PrimarySlot = "stop" | "done" | "mic" | "send";

export interface ComposerSlots {
  primary: PrimarySlot;
  /** The quiet mic before send (text in the field, voice on). */
  secondaryMic: boolean;
  /** Send is shown but can't send: empty field, or the model can't take a question yet. */
  sendDisabled: boolean;
}

export function composerSlots(s: {
  generating: boolean;
  voice: VoicePhase;
  /** Voice input is on in Settings and usable on this phone. */
  voiceAvailable: boolean;
  empty: boolean;
  canSend: boolean;
}): ComposerSlots {
  // An answer streaming always owns the slot with Stop, even mid-dictation (an answer started by Retry while
  // listening must stay stoppable); the composer then ends the dictation, keeping its text.
  if (s.generating) return { primary: "stop", secondaryMic: false, sendDisabled: false };
  if (s.voice !== "idle") return { primary: "done", secondaryMic: false, sendDisabled: false };
  if (s.empty && s.voiceAvailable) return { primary: "mic", secondaryMic: false, sendDisabled: false };
  return { primary: "send", secondaryMic: s.voiceAvailable && !s.empty, sendDisabled: s.empty || !s.canSend };
}

/** The answer mode the two switches make (Settings › Answers footer uses the same four). */
export type AnswerModeKey = "quickModel" | "quickComplete" | "directModel" | "directComplete";

export function answerModeKey(quickFirst: boolean, alwaysComplete: boolean): AnswerModeKey {
  return `${quickFirst ? "quick" : "direct"}${alwaysComplete ? "Complete" : "Model"}`;
}

/** The mode chip's glyph: zap when a quick passage comes first, layers for a full answer, else the plain chat glyph. */
export function answerModeIcon(key: AnswerModeKey): IconName {
  if (key.startsWith("quick")) return "zap";
  return key === "directComplete" ? "layers" : "message-square";
}

/** Elapsed listening time as m:ss (tabular in the strip). */
export function formatElapsed(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s < 10 ? "0" : ""}${s}`;
}

/**
 * The "audio may go to a provider" caveat is only true for the phone's system recognition service
 * (Android, after the user accepted it). iPhone and Android's on-device recognizer never send audio
 * anywhere, so they never see it.
 */
export function voiceCaveatNeeded(reason: VoiceSupportReason | null | undefined): boolean {
  return reason === "system-accepted";
}

/**
 * How a recognizer error is told to the user. Codes: iOS "no_speech" and NSError numbers, Android
 * SpeechRecognizer ERROR_* numbers (5 client, 6 speech timeout, 7 no match, 9 insufficient
 * permissions), and the native start rejections (E_PERMISSION_DENIED, E_START_FAILED, ...).
 * "silent": the recognizer's own reply to our stop, nothing to say.
 */
export type VoiceErrorKind = "permission" | "noSpeech" | "silent" | "other";

export function voiceErrorKind(code: string): VoiceErrorKind {
  if (code === "E_PERMISSION_DENIED" || code === "9") return "permission";
  if (code === "no_speech" || code === "6" || code === "7" || code === "1110") return "noSpeech";
  if (code === "5") return "silent";
  return "other";
}

/** After "done", how long to wait for the recognizer's final text before keeping what was heard. */
export const VOICE_FINISH_GRACE_MS = 4000;
