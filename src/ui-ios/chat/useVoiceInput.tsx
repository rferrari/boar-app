import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Sheet, Text, useAnnounce, useToast } from "../components";
import { cancelListening, getVoiceSupport, startListening, stopListening, type VoiceEvent } from "../../voice/VoiceInput";
import type { VoiceSupport } from "../../voice/voicePolicy";
import { VOICE_FINISH_GRACE_MS, voiceCaveatNeeded, voiceErrorKind, type VoicePhase } from "./composerSlots";

interface Options {
  /** Voice input is on in Settings. */
  enabled: boolean;
  /** Listening starts: the caller keeps what was typed, to add the transcript after it. */
  onStart: () => void;
  /** What was heard so far: each partial as it arrives, then the final text. */
  onText: (text: string) => void;
  /** The user cancelled: the caller puts back what was typed before listening. */
  onCancel: () => void;
}

export interface VoiceInputControls {
  /** On in Settings and usable on this phone under the voice policy. */
  available: boolean;
  phase: VoicePhase;
  /** Date.now() when listening started (the strip's elapsed time). */
  startedAt: number;
  start: () => void;
  /** Stop and keep what was heard (the recognizer then sends its final text). */
  finish: () => void;
  /** Stop and drop what was heard. */
  cancel: () => void;
  /** The first-use caveat sheet; render it once. */
  caveat: React.ReactNode;
}

// The system-service caveat is shown once per app run (no settings key for it yet).
let caveatShown = false;

/**
 * Voice input for the composer (src/voice/VoiceInput.ts). The composer draws the controls (mic in the pill,
 * the listening strip, done / cancel); this owns the session: one at a time, events of an ended or
 * cancelled session ignored, a grace period after "done" in case the recognizer never sends its final text.
 * The "may go online" caveat shows only for the system recognition service (voiceCaveatNeeded): on iPhone
 * and Android's on-device recognizer audio never leaves the phone.
 */
export function useVoiceInput({ enabled, onStart, onText, onCancel }: Options): VoiceInputControls {
  const { t } = useTranslation();
  const announce = useAnnounce();
  const toast = useToast();
  const [support, setSupport] = useState<VoiceSupport | null>(null);
  const [phase, setPhaseState] = useState<VoicePhase>("idle");
  const [startedAt, setStartedAt] = useState(0);
  const [caveatOpen, setCaveatOpen] = useState(false);
  const phaseRef = useRef<VoicePhase>("idle");
  // The live session's id; ending a session bumps it, so its late events are ignored.
  const session = useRef(0);
  const grace = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cb = useRef({ onStart, onText, onCancel });
  cb.current = { onStart, onText, onCancel };

  const setPhase = useCallback((p: VoicePhase) => {
    phaseRef.current = p;
    setPhaseState(p);
  }, []);

  // Support can change in Settings (voice on/off, the system-service consent): re-read when the switch flips.
  useEffect(() => {
    if (!enabled) {
      setSupport(null);
      return;
    }
    let alive = true;
    getVoiceSupport()
      .then((s) => alive && setSupport(s))
      .catch(() => alive && setSupport(null));
    return () => {
      alive = false;
    };
  }, [enabled]);

  /** Ends session `id` if it is still the live one. */
  const end = useCallback(
    (id: number): boolean => {
      if (session.current !== id || phaseRef.current === "idle") return false;
      session.current++;
      if (grace.current) clearTimeout(grace.current);
      grace.current = null;
      setPhase("idle");
      return true;
    },
    [setPhase]
  );

  const listen = useCallback(async () => {
    const id = ++session.current;
    setPhase("listening");
    setStartedAt(Date.now());
    announce(t("chat.announce.listening"));
    cb.current.onStart();
    const failure = { code: "" };
    let text: string | null = null;
    try {
      text = await startListening((event: VoiceEvent) => {
        if (session.current !== id) return;
        if (event.type === "partial") cb.current.onText(event.text);
        if (event.type === "error") failure.code = event.code;
      });
    } catch {
      failure.code = "E_START_FAILED";
    }
    if (session.current !== id) return; // cancelled, or the grace period already ended it
    if (text) cb.current.onText(text);
    end(id);
    announce(t("chat.announce.stoppedListening"));
    if (!text && failure.code) {
      const kind = voiceErrorKind(failure.code);
      if (kind !== "silent") toast({ message: t(`chat.voice.error.${kind}`), icon: "mic-off" });
    }
  }, [announce, end, setPhase, t, toast]);

  const start = useCallback(() => {
    if (!support?.usable || phaseRef.current !== "idle") return;
    if (voiceCaveatNeeded(support.reason) && !caveatShown) {
      caveatShown = true;
      setCaveatOpen(true);
      return;
    }
    listen();
  }, [listen, support]);

  const finish = useCallback(() => {
    if (phaseRef.current !== "listening") return;
    const id = session.current;
    setPhase("finishing");
    stopListening();
    // Keep what was heard if the final text never comes; drop the native session so the mic is off.
    grace.current = setTimeout(() => {
      if (!end(id)) return;
      cancelListening();
      announce(t("chat.announce.stoppedListening"));
    }, VOICE_FINISH_GRACE_MS);
  }, [announce, end, setPhase, t]);

  // Also while the native start is still pending (permission prompt): VoiceInput stops it once it starts.
  const cancel = useCallback(() => {
    if (!end(session.current)) return;
    cancelListening();
    cb.current.onCancel();
    announce(t("chat.announce.stoppedListening"));
  }, [announce, end, t]);

  // Leaving the chat mid-dictation: stop the recognizer, drop its late events.
  useEffect(
    () => () => {
      if (grace.current) clearTimeout(grace.current);
      if (phaseRef.current !== "idle") {
        session.current++;
        cancelListening();
      }
    },
    []
  );

  const caveat = (
    <Sheet
      visible={caveatOpen}
      onClose={() => setCaveatOpen(false)}
      title={t("chat.voice.caveatTitle")}
      footer={
        <Button
          label={t("chat.voice.caveatOk")}
          fullWidth
          onPress={() => {
            setCaveatOpen(false);
            listen();
          }}
        />
      }
    >
      <Text color="secondary">{t("chat.voice.caveatBody")}</Text>
    </Sheet>
  );

  return { available: !!support?.usable, phase, startedAt, start, finish, cancel, caveat };
}
