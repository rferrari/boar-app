import React, { forwardRef, memo, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from "react";
import { useFocusEffect } from "@react-navigation/native";
import { LayoutChangeEvent, StyleSheet, TextInput, useWindowDimensions, View } from "react-native";
import Animated, { Easing, ReduceMotion, useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { Chip, IconButton, ListRow, Section, Sheet, Text, TextAction, useAnnounce } from "../components";
import { getAnswerSettings, setAnswerSettings } from "../../models/settings";
import { footerBottom } from "../components/Screen";
import { useTokens } from "../theme";
import { useMotion } from "../theme/motion";
import { CURVE, type Curve } from "../theme/motionSpec";
import { composerNotice, composerPlaceholderKey, type ModelStatus } from "./composerState";
import { answerModeIcon, answerModeKey, composerSlots, formatElapsed, type VoicePhase } from "./composerSlots";
import { Swap } from "./Swap";
import { composerLayout, composerPadEnd, composerPillHeight } from "./composerLayout";
import { useVoiceInput } from "./useVoiceInput";

const curves: Record<Curve, ReturnType<typeof Easing.bezier>> = {
  standard: Easing.bezier(...CURVE.standard),
  enter: Easing.bezier(...CURVE.enter),
  exit: Easing.bezier(...CURVE.exit),
};

interface Props {
  value: string;
  onChange: (text: string) => void;
  onSend: () => void;
  onStop: () => void;
  /** Sending needs a loaded model; typing never waits. */
  status: ModelStatus;
  generating: boolean;
  stopping: boolean;
  voiceEnabled: boolean;
  /** Asking works: the model loads have started (answer() waits for them) and there is no load error. */
  canSend?: boolean;
}

/**
 * Question field (docs/design/CHAT_COMPOSER.md). One pill: the text, and at its end one slot that is the
 * mic (empty field), send (text), done (listening) or stop (an answer streaming). Above it, one row: the
 * answer-mode chip, or while listening the listening strip. Stays editable while an answer streams so the
 * next question can be written; only sending waits.
 */
const ComposerView = forwardRef<TextInput, Props>(function Composer(
  { value, onChange, onSend, onStop, status, generating, stopping, voiceEnabled, canSend = status === "ready" },
  ref
) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const field = useRef<TextInput>(null);
  // The field's text when voice input started; each transcript is appended to it, not to the last partial.
  const voiceBase = useRef("");
  useImperativeHandle(ref, () => field.current as TextInput);
  const empty = value.trim().length === 0;
  const insets = useSafeAreaInsets();
  // The mockup ends the composer 30 pt above the screen's bottom (into the home-indicator inset by 4);
  // the screen leaves the bottom edge to the composer. Small insets (Android gestures) keep at least sm.
  // iOS: 4 pt into the home-indicator inset, as the mockup; Android: fully above the navigation bar (Iris AN-1).
  // The same with the keyboard up: the chat's KeyboardAvoidingView takes this gap back (keyboardVerticalOffset),
  // so the composer ends sm above the keyboard and follows it frame by frame. Switching the padding on the keyboard
  // state jumped: the state turns visible when the keyboard starts opening but hidden only once it has closed, so
  // the composer rode the closing keyboard down onto the home indicator or the navigation bar, then popped up.
  const bottom = footerBottom(insets.bottom, t.space.xs, t.space.sm);
  // Model error: the composer is dimmed and not editable; the card above is where to act (E-5, Prism).
  const blocked = status === "error";
  const keys = composerNotice(status);
  const line = keys.line ? tr(keys.line) : undefined;
  const hint = keys.hint ? tr(keys.hint) : undefined;
  const [focused, setFocused] = useState(false);

  const voice = useVoiceInput({
    enabled: voiceEnabled,
    onStart: () => {
      voiceBase.current = value;
    },
    // Partial results stream into the field after what was typed before listening.
    onText: (text) => onChange(voiceBase.current ? `${voiceBase.current} ${text}` : text),
    onCancel: () => onChange(voiceBase.current),
  });
  const slots = composerSlots({ generating, voice: voice.phase, voiceAvailable: voice.available, empty, canSend });
  const listening = voice.phase !== "idle";
  // An answer starting mid-dictation (Retry in the list): Stop takes the slot, so end the dictation the
  // way "done" does, keeping what was heard. One thing at a time: no hidden open mic behind a stream.
  const finishVoice = voice.finish;
  useEffect(() => {
    if (generating && voice.phase === "listening") finishVoice();
  }, [generating, voice.phase, finishVoice]);

  // 14 regular, as the mockup's placeholder and text.
  const text = { ...t.type.subhead, fontFamily: t.type.body.fontFamily, fontWeight: t.type.body.fontWeight };
  const lineHeight = text.lineHeight ?? t.size.composer / 2;
  // The input sizes itself (up to 5 lines, then scrolls) and the pill follows it animated (composerLayout.ts
  // has the iOS cause: a fixed height fed by onContentSizeChange never grew past one line).
  const { fontScale } = useWindowDimensions();
  const disc = t.size.controlSm;
  const layout = useMemo(
    () => composerLayout({ lineHeight, fontScale, composer: t.size.composer, button: disc }),
    [lineHeight, fontScale, t.size.composer, disc]
  );
  const padEnd = composerPadEnd(layout, { buttons: slots.secondaryMic ? 2 : 1, button: disc, gap: t.space.sm });
  const m = useMotion();
  const micMotion = useMemo(() => ({ entering: m.entering(), exiting: m.exiting({ swap: true }) }), [m]);
  const pill = useSharedValue(layout.pillMin);
  const onInputLayout = useCallback(
    (e: LayoutChangeEvent) => {
      const target = composerPillHeight(e.nativeEvent.layout.height, layout);
      // A line more or less: the DS `layout` role (reduce motion: at once, no slide).
      const s = m.spec("layout");
      pill.value = s.duration > 0 ? withTiming(target, { duration: s.duration, easing: curves[s.curve], reduceMotion: ReduceMotion.Never }) : target;
    },
    [layout, m, pill]
  );
  const pillStyle = useAnimatedStyle(() => ({ height: pill.value }));

  // Answer mode, as a labelled chip above the field; its sheet holds the same two switches as Settings › Answers.
  const announce = useAnnounce();
  const [answerMode, setAnswerMode] = useState<{ quickFirst: boolean; alwaysComplete: boolean } | null>(null);
  const [modeOpen, setModeOpen] = useState(false);
  useFocusEffect(
    useCallback(() => {
      getAnswerSettings()
        .then((a) => setAnswerMode({ quickFirst: a.quickFirst, alwaysComplete: a.alwaysComplete }))
        .catch(() => {});
    }, [])
  );
  const modeKey = answerMode ? answerModeKey(answerMode.quickFirst, answerMode.alwaysComplete) : null;
  const setMode = (key: "quickFirst" | "alwaysComplete", on: boolean) => {
    if (!answerMode) return;
    const next = { ...answerMode, [key]: on };
    setAnswerMode(next);
    setAnswerSettings({ [key]: on }).catch(() => {});
    announce(tr(`flows.settings.answerMode.${answerModeKey(next.quickFirst, next.alwaysComplete)}`));
  };

  const placeholder = listening
    ? tr("chat.voice.speakNow")
    : // Asking already works while loading (answer() waits), so the usual placeholder then.
      tr(canSend ? "chat.composer.placeholder" : composerPlaceholderKey(status));

  const primary = (() => {
    switch (slots.primary) {
      case "stop":
        // Stop, as the mockup (s2 disc, ember ring, ember square): the DS's IconButton "stop" (Prism CH-29),
        // busy while the stop lands.
        return (
          <IconButton
            icon="square"
            variant="stop"
            size="sm"
            label={stopping ? tr("chat.composer.stopping") : tr("chat.composer.stop")}
            busy={stopping}
            onPress={onStop}
          />
        );
      case "done":
        // Stop listening and keep the text; busy while the recognizer sends its final words.
        return (
          <IconButton
            icon="check"
            variant="filled"
            size="sm"
            label={tr("chat.voice.stop")}
            accessibilityHint={tr("chat.voice.stopHint")}
            busy={voice.phase === "finishing"}
            onPress={voice.finish}
          />
        );
      case "mic":
        return <IconButton icon="mic" size="sm" label={tr("chat.voice.start")} disabled={blocked} onPress={voice.start} />;
      default:
        // Ember only when it sends: an empty field or a model that can't answer yet mutes it (raised disc).
        return (
          <IconButton
            icon="arrow-up"
            variant="filled"
            size="sm"
            label={tr("chat.composer.send")}
            accessibilityHint={empty ? tr("chat.composer.focusHint") : !canSend ? hint : undefined}
            disabled={slots.sendDisabled}
            onPress={onSend}
          />
        );
    }
  })();

  return (
    <View
      style={{
        paddingHorizontal: t.space.gutterChat,
        paddingTop: t.space.sm,
        paddingBottom: bottom,
        gap: t.space.xs,
      }}
    >
      {line && (
        <Text variant="caption" color="secondary">
          {line}
        </Text>
      )}
      {/* One row above the pill, the same height in both states so the pill never jumps: the answer-mode chip,
          or while listening the listening strip. */}
      <View style={{ minHeight: disc, justifyContent: "center", opacity: blocked ? t.opacity.disabled : 1 }}>
        {listening ? (
          <ListeningStrip phase={voice.phase} startedAt={voice.startedAt} onCancel={voice.cancel} />
        ) : (
          modeKey && (
            <Chip
              size="sm"
              icon={answerModeIcon(modeKey)}
              label={tr(`chat.composer.mode.${modeKey}`)}
              accessibilityLabel={`${tr("flows.settings.answers")}: ${tr(`chat.composer.mode.${modeKey}`)}`}
              accessibilityHint={tr("chat.composer.modeHint")}
              onPress={() => setModeOpen(true)}
            />
          )
        )}
      </View>
      {/* In the model-error state the whole composer is dimmed, as the mockup: the card above is where to act (E-5).
          The mockup's question pill: 52 tall for a line, s1, hairline border (focus colour when focused or
          listening), 18 side padding; same radius and padding at any height. The text sits on its bottom (the
          caret's line stays in view while the pill catches up with a new line) and the pill's height animates to
          it. The buttons sit inside at its end, anchored to its bottom: they stay on the last line's centre. */}
      <Animated.View
        style={[
          {
            opacity: blocked ? t.opacity.disabled : 1,
            borderRadius: t.size.composer / 2,
            backgroundColor: t.color.bg.surface,
            borderWidth: t.size.border,
            borderColor: focused || listening ? t.color.line.focus : t.color.line.hairline,
          },
          pillStyle,
        ]}
      >
        <View
          style={{
            ...StyleSheet.absoluteFill,
            justifyContent: "flex-end",
            overflow: "hidden",
            borderRadius: t.size.composer / 2,
            paddingBottom: layout.padV,
            paddingLeft: t.space.md + t.space.xs + t.space.xxs,
            paddingRight: padEnd,
          }}
        >
          <TextInput
            ref={field}
            value={value}
            onChangeText={onChange}
            accessibilityLabel={tr("chat.composer.label")}
            placeholder={placeholder}
            placeholderTextColor={t.color.text.secondary}
            // Read-only while listening: the live transcript rewrites the field, so a typed edit would be lost.
            editable={!blocked && !listening}
            accessibilityState={{ disabled: blocked }}
            multiline
            submitBehavior="newline"
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            onLayout={onInputLayout}
            style={{
              ...text,
              color: t.color.text.primary,
              maxHeight: layout.inputMax,
              paddingVertical: 0,
              textAlignVertical: "top",
            }}
          />
        </View>
        <View style={{ position: "absolute", right: layout.inset, bottom: layout.buttonBottom, flexDirection: "row", gap: t.space.sm }}>
          {slots.secondaryMic && (
            <Animated.View entering={micMotion.entering} exiting={micMotion.exiting}>
              <IconButton icon="mic" size="sm" label={tr("chat.voice.start")} disabled={blocked} onPress={voice.start} />
            </Animated.View>
          )}
          {/* Mic, send, done and stop swap in place with the DS crossfade (Prism F2-8). */}
          <Swap swapKey={slots.primary}>{primary}</Swap>
        </View>
      </Animated.View>
      {voice.caveat}
      {answerMode && modeKey && (
        <Sheet
          visible={modeOpen}
          onClose={() => setModeOpen(false)}
          title={tr("flows.settings.answers")}
          description={tr(`flows.settings.answerMode.${modeKey}`)}
        >
          <Section>
            <ListRow
              icon="zap"
              title={tr("flows.settings.quickFirst")}
              subtitle={tr("flows.settings.quickFirstHint")}
              switch={{ value: answerMode.quickFirst, onValueChange: (v) => setMode("quickFirst", v) }}
            />
            <ListRow
              icon="layers"
              title={tr("flows.settings.alwaysComplete")}
              subtitle={tr("flows.settings.alwaysCompleteHint")}
              switch={{ value: answerMode.alwaysComplete, onValueChange: (v) => setMode("alwaysComplete", v) }}
            />
          </Section>
        </Sheet>
      )}
    </View>
  );
});

/**
 * While listening: a recording dot that breathes with the seconds (static under reduce motion), the elapsed
 * time, and Cancel. After "done", "Transcribing…" until the final words land.
 */
function ListeningStrip({ phase, startedAt, onCancel }: { phase: VoicePhase; startedAt: number; onCancel: () => void }) {
  const t = useTokens();
  const m = useMotion();
  const { t: tr } = useTranslation();
  const [now, setNow] = useState(() => Date.now());
  const live = phase === "listening";
  useEffect(() => {
    if (!live) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [live]);
  const elapsed = formatElapsed(now - startedAt);
  const second = Math.floor(Math.max(0, now - startedAt) / 1000);
  const dot = useSharedValue(1);
  useEffect(() => {
    // One fade per second, towards dim on odd seconds and back on even ones: no endless loop (chatPerf).
    const target = live && !m.reduceMotion && second % 2 === 1 ? t.opacity.disabled : 1;
    dot.value = withTiming(target, { duration: t.motion.loop.pulse / 2, easing: curves.standard, reduceMotion: ReduceMotion.Never });
  }, [second, live, m.reduceMotion, dot, t.opacity.disabled, t.motion.loop.pulse]);
  const dotStyle = useAnimatedStyle(() => ({ opacity: dot.value }));
  const label = live ? tr("chat.voice.listening") : tr("chat.voice.transcribing");
  return (
    <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm }}>
      <View
        accessible
        accessibilityRole="text"
        accessibilityLabel={live ? `${label}, ${elapsed}` : label}
        style={{ flex: 1, flexDirection: "row", alignItems: "center", gap: t.space.sm }}
      >
        <Animated.View
          style={[{ width: t.space.sm, height: t.space.sm, borderRadius: t.radius.full, backgroundColor: t.color.status.danger.solid }, dotStyle]}
        />
        <Text variant="footnote" color="primary" numberOfLines={1}>
          {label}
        </Text>
        {live && (
          <Text variant="footnote" color="secondary" numeric>
            {elapsed}
          </Text>
        )}
      </View>
      <TextAction label={tr("chat.voice.cancel")} leadingIcon="x" accessibilityHint={tr("chat.voice.cancelHint")} onPress={onCancel} />
    </View>
  );
}

// Memoized: the chat screen re-renders on every streamed frame; the composer only when its props change.
export const Composer = memo(ComposerView);
