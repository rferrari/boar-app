import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  answerModeIcon,
  answerModeKey,
  composerSlots,
  formatElapsed,
  voiceCaveatNeeded,
  voiceErrorKind,
} from "./composerSlots";

const base = { generating: false, voice: "idle" as const, voiceAvailable: true, empty: true, canSend: true };

describe("composerSlots (one trailing slot)", () => {
  it("empty field with voice on: the mic, no send", () => {
    expect(composerSlots(base)).toEqual({ primary: "mic", secondaryMic: false, sendDisabled: false });
  });
  it("text: send, with the quiet mic before it so dictation can add to it", () => {
    expect(composerSlots({ ...base, empty: false })).toEqual({ primary: "send", secondaryMic: true, sendDisabled: false });
  });
  it("voice off or unusable: send only, off while the field is empty", () => {
    expect(composerSlots({ ...base, voiceAvailable: false })).toEqual({ primary: "send", secondaryMic: false, sendDisabled: true });
    expect(composerSlots({ ...base, voiceAvailable: false, empty: false })).toEqual({ primary: "send", secondaryMic: false, sendDisabled: false });
  });
  it("text but the model can't take it yet (before the loads start, or a model error): send stays, muted", () => {
    expect(composerSlots({ ...base, empty: false, canSend: false }).sendDisabled).toBe(true);
  });
  it("sends as soon as asking works, not only once the model is ready (Tusk boot P1)", () => {
    // canSend is true while the model still loads (answer() waits for it); the composer never re-gates on "ready".
    expect(composerSlots({ ...base, voiceAvailable: false, empty: false, canSend: true })).toEqual({ primary: "send", secondaryMic: false, sendDisabled: false });
  });
  it("an answer streaming: Stop owns the slot, no mic (typing still works)", () => {
    expect(composerSlots({ ...base, generating: true })).toEqual({ primary: "stop", secondaryMic: false, sendDisabled: false });
    expect(composerSlots({ ...base, generating: true, empty: false }).primary).toBe("stop");
  });
  it("listening or finishing: done, whatever the field holds", () => {
    expect(composerSlots({ ...base, voice: "listening" }).primary).toBe("done");
    expect(composerSlots({ ...base, voice: "finishing", empty: false }).primary).toBe("done");
    expect(composerSlots({ ...base, voice: "listening", empty: false }).secondaryMic).toBe(false);
  });
  it("an answer streaming while listening (Retry mid-dictation): Stop, never done (PR #24)", () => {
    for (const voice of ["listening", "finishing"] as const) {
      expect(composerSlots({ ...base, generating: true, voice })).toEqual({ primary: "stop", secondaryMic: false, sendDisabled: false });
      expect(composerSlots({ ...base, generating: true, voice, empty: false }).primary).toBe("stop");
    }
  });
});

describe("answer mode chip", () => {
  it("names the same four modes as Settings › Answers", () => {
    expect(answerModeKey(true, false)).toBe("quickModel");
    expect(answerModeKey(true, true)).toBe("quickComplete");
    expect(answerModeKey(false, true)).toBe("directComplete");
    expect(answerModeKey(false, false)).toBe("directModel");
  });
  it("keeps the glyphs the switches already use", () => {
    expect(answerModeIcon("quickModel")).toBe("zap");
    expect(answerModeIcon("quickComplete")).toBe("zap");
    expect(answerModeIcon("directComplete")).toBe("layers");
  });
});

describe("listening strip", () => {
  it("shows m:ss", () => {
    expect(formatElapsed(0)).toBe("0:00");
    expect(formatElapsed(7400)).toBe("0:07");
    expect(formatElapsed(65_000)).toBe("1:05");
    expect(formatElapsed(-5)).toBe("0:00");
  });
});

describe("voice caveat (r4to, iPhone: audio never leaves the phone)", () => {
  it("only for the system recognition service", () => {
    expect(voiceCaveatNeeded("system-accepted")).toBe(true);
    expect(voiceCaveatNeeded("on-device")).toBe(false);
    expect(voiceCaveatNeeded(null)).toBe(false);
  });
});

describe("voiceErrorKind", () => {
  it("tells a denied permission, silence, our own stop and the rest apart", () => {
    expect(voiceErrorKind("E_PERMISSION_DENIED")).toBe("permission");
    expect(voiceErrorKind("9")).toBe("permission");
    expect(voiceErrorKind("no_speech")).toBe("noSpeech");
    expect(voiceErrorKind("7")).toBe("noSpeech");
    expect(voiceErrorKind("5")).toBe("silent");
    expect(voiceErrorKind("E_START_FAILED")).toBe("other");
  });
});

describe("wiring guards", () => {
  const read = (f: string) => readFileSync(join(__dirname, f), "utf8");
  it("the caveat sheet opens only through voiceCaveatNeeded", () => {
    const src = read("useVoiceInput.tsx");
    expect(src.match(/setCaveatOpen\(true\)/g)).toHaveLength(1);
    expect(src).toMatch(/if \(voiceCaveatNeeded\(support\.reason\) && !caveatShown\) \{\s*caveatShown = true;\s*setCaveatOpen\(true\);/);
  });
  it("the composer draws its buttons from composerSlots, inside the pill", () => {
    const src = read("Composer.tsx");
    expect(src).toMatch(/const slots = composerSlots\(/);
    expect(src).toMatch(/<Swap swapKey=\{slots\.primary\}>/);
  });
});
