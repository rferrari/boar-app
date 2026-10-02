import React, { createContext, memo, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { Animated, Easing, Pressable, useWindowDimensions, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Badge, Banner, Button, Card, Icon, IconButton, IconSlot, IconText, LARGE_TEXT_SCALE, LineSlot, Mascot, MetaLine, Text, TextAction, useOpticalLine, type IconName } from "../components";
import { MarkdownMessage } from "../components/MarkdownMessage";
import { icon, useTheme, useTokens } from "../theme";
import { META_SEPARATOR, metaItems } from "../components/metaItems";
import { splitThinking } from "../../services/thinking";
import { cleanCitations } from "../../services/citations";
import { splitInlineBullets } from "../../services/answerFormat";
import { answerPhase, canDeepen, isLocating, noSourceKind, type AnswerState, type TierState } from "./answerReducer";
import { declineAfterSnippet, declineCopy, answerReceiptShort, pillStep, snippetAutoCollapses, generatingSteps, noticeShown, stepsCardShown, showsAnswerBody, noSourceNote, offersAskModel, receiptTagKey, showsInstantSnippet, sourceLanguageLead, previewText, receiptDetails, receiptLine } from "./presentation";
import { answerSourceSplit, groupSources, sourcesCardMode, relevanceBands, bestBand, BAND_FILL, sourceParts, type RelevanceBand } from "./sourceLabel";
import { answerShowsEmergencyNote } from "./safetyNote";
import { withoutUncitedPreface } from "./uncitedPreface";
import { formatSeconds } from "./shareFormat";
import { LocatingPrompt, PlacesCard } from "./PlacesCard";
import type { AnswerReceipt } from "./answerEvents";
import { sameAnswerFields, sameNumbers } from "./renderEquality";
import { lineSlop } from "./touch";
import { chatLargeText } from "./largeText";
import Reanimated, { LayoutAnimationConfig } from "react-native-reanimated";
import { Reveal } from "./Reveal";
import { useSmoothText } from "./useSmoothText";
import { answerStillShowing, isDraining } from "./streamReveal";
import { Swap } from "./Swap";
import { useMotion } from "../theme/motion";
import { deepFromFor, deepSectionLabeled, foldAttempt, researchPhase, summaryItems, timelinePillStep, type AttemptTimeline, type Timeline } from "./liveResearch";
import { ResearchPanel } from "./ResearchCard";

export interface AssistantMessageProps {
  answer: AnswerState;
  /** This answer is the one running now. */
  active: boolean;
  /** Asked in this run (not restored from history): it may take focus, e.g. the city prompt. */
  fresh?: boolean;
  /** The question this answers (health questions get the emergency note). */
  question?: string;
  /** Waiting for the offline library to be ready before searching (first boot); the current status line. */
  waitingLibrary?: string;
  /** The library's indexing failed part-way: "not found" must say the library is incomplete (Prism). */
  libraryIncomplete?: boolean;
  stopping: boolean;
  /** Stopped because the app went to the background. */
  interrupted?: boolean;
  feedback?: "up" | "down" | null;
  locale: string;
  onOpenSource: (index: number) => void;
  onDeepen: () => void;
  onAskModel: () => void;
  onRetry: () => void;
  onRate: (rating: "up" | "down") => void;
  onCopy: () => void;
  onShare: () => void;
  onCopyReceipt: (text: string) => void;
  /** The model is done but the text is still showing its last words (Prism CX-12). */
  onRevealing?: (revealing: boolean) => void;
  /** Places answers: re-ask for a typed city, or with the device position. */
  onCity: (city: string) => void;
  /** Weak-sources state A: generate anyway for the same question. */
  onAnswerAnyway?: () => void;
  onUseLocation?: () => void;
  onGetMap?: () => void;
}

/**
 * One tier's research timeline (LIVE_RESEARCH): while `on` (the tier runs), each render folds what the
 * answer shows into it (the events keep no history: which part found which article is only known here);
 * after, it stays as it was, for the pill. Null for answers that never researched in this mount (history).
 * `foldTimeline` returns the same object when nothing changed, so the panel's memo holds while text streams.
 */
function useTimeline(attempt: string, on: boolean, sources: AnswerState["sources"], from: number, detail: unknown, searching: boolean): Timeline | null {
  // Keyed by the attempt: Retry reuses this message (and this ref) for a new answer() id (foldAttempt).
  const ref = useRef<AttemptTimeline | null>(null);
  ref.current = foldAttempt(ref.current, attempt, on, { sources: from > 0 ? sources.slice(from) : sources, detail, searching });
  return ref.current?.timeline ?? null;
}

function useElapsedSeconds(running: boolean): number {
  const started = useRef(Date.now());
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setSeconds(Math.floor((Date.now() - started.current) / 1000)), 1000);
    return () => clearInterval(id);
  }, [running]);
  return seconds;
}

/**
 * The mockup's step indicator: an ember ring turning (static under reduce motion). Memoized, with the
 * interpolation built once (audit #11): a new one per render rebuilt the native animated graph per flush.
 */
const StepSpinner = memo(function StepSpinner({ still = false }: { still?: boolean }) {
  const t = useTokens();
  const { reduceMotion } = useTheme();
  const spin = useRef(new Animated.Value(0)).current;
  // `still`: the answer's text is streaming and shows the progress; a loop running the whole generation
  // asked for a frame on every vsync (GFXINFO 28/09). Same static ring as under reduce motion.
  const turning = !reduceMotion && !still;
  useEffect(() => {
    if (!turning) {
      spin.setValue(0);
      return;
    }
    const loop = Animated.loop(Animated.timing(spin, { toValue: 1, duration: t.motion.loop.spin, easing: Easing.linear, useNativeDriver: true }));
    loop.start();
    return () => loop.stop();
  }, [turning, spin, t.motion]);
  const rotate = useMemo(() => spin.interpolate({ inputRange: [0, 1], outputRange: ["0deg", "360deg"] }), [spin]);
  const side = t.size.iconSm - t.space.xxs;
  return (
    <Animated.View
      style={{
        width: side,
        height: side,
        borderRadius: t.radius.full,
        borderWidth: t.size.focusRing,
        borderColor: t.color.accent.solid,
        borderTopColor: "transparent",
        transform: [{ rotate }],
      }}
    />
  );
});

/** Seconds since the answer started, as the mockup's pill at the right of the name (the receipt takes its place when done). */
function Elapsed({ locale, step }: { locale: string; step?: string }) {
  const t = useTokens();
  const seconds = useElapsedSeconds(true);
  return (
    <View
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
      style={{
        marginLeft: "auto",
        justifyContent: "center",
        paddingHorizontal: t.space.sm,
        paddingVertical: t.space.xs,
        borderRadius: t.radius.full,
        backgroundColor: t.color.bg.surface,
      }}
    >
      {/* A pill (Iris icon-align): seal-sized icon, tight gap, the pair on the pill's optical middle. */}
      {/* Searching → Reading → Writing crossfade in the pill (Prism F2-8); the seconds tick inside. */}
      <Swap swapKey={step ?? ""}>
        <IconText icon="loader" variant="caption" color="secondary" iconColor={t.color.text.secondary} iconRole="seal" gap="tight" centerOnBox numeric>
          {metaItems([step, formatSeconds(seconds * 1000, locale)]).join(META_SEPARATOR)}
        </IconText>
      </Swap>
    </View>
  );
}

function Reasoning({ thinking, inProgress, streaming }: { thinking: string; inProgress: boolean; streaming: boolean }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [open, setOpen] = useState(false);
  const m = useMotion();
  const counting = streaming && inProgress;
  const seconds = useElapsedSeconds(counting);
  const label = open ? tr("chat.reasoning.hide") : tr("chat.reasoning.show");
  // The fold opens in height (DS layout), not in one frame.
  const toggle = () => {
    m.animateNextLayout();
    setOpen((o) => !o);
  };
  return (
    <View style={{ gap: t.space.xs }}>
      {/* The label stays what it does (Prism CH-26: the spoken name is the visible one, and the pill no longer
          shifts every second); the counter is a caption beside it, silent (readers hear the stages). */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: icon.gap }}>
        {/* A secondary move in text.secondary, not ember (Prism CH-14). */}
        <TextAction label={label} leadingIcon="message-circle" onPress={toggle} expanded={open} />
        {counting && (
          <View importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
            <MetaLine items={[tr("chat.reasoning.thinking", { seconds })]} variant="caption" />
          </View>
        )}
      </View>
      {open && (
        <Text
          variant="footnote"
          color="secondary"
          style={{ paddingLeft: t.space.md, borderLeftWidth: t.size.focusRing, borderLeftColor: t.color.line.hairline }}
        >
          {thinking}
        </Text>
      )}
    </View>
  );
}

const TierBody = memo(function TierBody({
  tier,
  streaming,
  sourceTitles,
  onOpenSource,
  drainKey,
  onDraining,
}: {
  tier: TierState;
  streaming: boolean;
  sourceTitles: string[];
  onOpenSource: (index: number) => void;
  /** Reports while this block still shows its end after the stream (Prism CX-12). */
  drainKey?: string;
  onDraining?: (key: string, draining: boolean) => void;
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const { reduceMotion } = useTheme();
  const split = splitThinking(tier.text);
  // One warning (Prism CX-5): the engine's "not from an offline source" line is the weak-source note's job.
  const answerText = withoutUncitedPreface(split.answer);
  // v2 P1: the text flows at a steady pace, newest characters fading in; reduce motion shows it as it comes.
  const smooth = useSmoothText(answerText, streaming, !reduceMotion);
  const live = streaming || !smooth.settled;
  const draining = isDraining(streaming, smooth.settled);
  useEffect(() => {
    if (!drainKey || !onDraining) return;
    onDraining(drainKey, draining);
    return () => onDraining(drainKey, false);
  }, [drainKey, onDraining, draining]);
  // Invented citations are cleaned only once the answer is done (sources are final then) and on screen.
  const shown = live ? smooth.text : splitInlineBullets(cleanCitations(answerText, sourceTitles.length));
  const onCitationPress = useCallback((n: number) => onOpenSource(n - 1), [onOpenSource]);
  return (
    <View style={{ gap: t.space.sm }}>
      {split.thinking || split.thinkingInProgress ? (
        <Reasoning thinking={split.thinking ?? ""} inProgress={split.thinkingInProgress && !split.answer} streaming={streaming} />
      ) : null}
      {!streaming && split.thinkingInProgress && !split.answer ? (
        <Text variant="footnote" color="secondary">
          {tr("chat.reasoning.unfinished")}
        </Text>
      ) : null}
      {shown.length > 0 && (
        <MarkdownMessage
          content={shown}
          sourceTitles={sourceTitles}
          onCitationPress={onCitationPress}
          isStreaming={live}
          tail={live ? smooth.tail : undefined}
        />
      )}
    </View>
  );
});

/**
 * The measured receipt: a short line ("9.1 s · ~8 words/s") that
 * sits by the name and opens the full measurement below the header row.
 */
function useReceipt(receipt: AnswerReceipt | undefined, locale: string, tagKey: string | null = null, declined = false) {
  const { t: tr } = useTranslation();
  const [open, setOpen] = useState(false);
  // The strings only change with the receipt: not rebuilt on every streamed frame of a Deepen (audit #23).
  const text = useMemo(
    () =>
      receipt && {
        // "general knowledge" / "no source cited" after the numbers (weak-sources spec, Iris CT-5).
        short: tagKey ? [...answerReceiptShort(declined, receipt, locale, tr), tr(tagKey)] : answerReceiptShort(declined, receipt, locale, tr),
        line: receiptLine(receipt, locale, tr),
        details: receiptDetails(receipt, locale, tr),
      },
    [receipt, locale, tagKey, declined, tr]
  );
  if (!text) return null;
  return { open, toggle: () => setOpen((o) => !o), ...text };
}

/** Beside the name it sits at the row's right edge like the running Elapsed pill (Prism CH-27): its Swap puts it there. */
function ReceiptToggle({ r, hidden }: { r: NonNullable<ReturnType<typeof useReceipt>>; hidden: boolean }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  // A caption line: the touch area comes up to the platform minimum (Prism CH-6).
  const line = useOpticalLine("caption");
  return (
    <Pressable
      onPress={r.toggle}
      accessibilityRole="button"
      accessibilityLabel={`${tr("chat.receipt.details")}: ${r.line}`}
      accessibilityState={{ expanded: r.open }}
      importantForAccessibility={hidden ? "no-hide-descendants" : "auto"}
      accessibilityElementsHidden={hidden}
      hitSlop={lineSlop(t.size.touch, line.lineHeight, t.space.sm)}
    >
      <MetaLine items={r.short} variant="caption" numberOfLines={1} />
    </Pressable>
  );
}

function ReceiptDetails({ r, onCopy }: { r: NonNullable<ReturnType<typeof useReceipt>>; onCopy: (text: string) => void }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const rows = [r.line, ...r.details.map((d) => `${d.label}: ${d.value}`)];
  return (
    <View style={{ gap: t.space.xxs, padding: t.space.md, borderRadius: t.radius.md, backgroundColor: t.color.bg.surface }}>
      {rows.map((row) => (
        <Text key={row} variant="mono" color="secondary">
          {row}
        </Text>
      ))}
      <TextAction label={tr("chat.receipt.copy")} leadingIcon="copy" onPress={() => onCopy(rows.join("\n"))} />
    </View>
  );
}

/** A small receipt line for the deep tier, which has its own section. */
function Receipt({
  receipt,
  locale,
  hidden,
  onCopy,
}: {
  receipt: AnswerReceipt;
  locale: string;
  hidden: boolean;
  onCopy: (text: string) => void;
}) {
  const t = useTokens();
  const r = useReceipt(receipt, locale)!;
  return (
    <View>
      <ReceiptToggle r={r} hidden={hidden} />
      <Reveal shown={r.open} spaceBefore={t.space.xs}>
        <ReceiptDetails r={r} onCopy={onCopy} />
      </Reveal>
    </View>
  );
}

const BANDS: RelevanceBand[] = ["high", "medium", "low"];

/**
 * The mockup's relevance bar, in three steps with the band's name; nothing without a measured value.
 * Iris: the label column is as wide as the widest band in this language (all three share one cell,
 * the others invisible at zero height), so bars line up across rows and at any font size; it never
 * shrinks (the title gives way); "Low" is secondary, amber reads as strong provenance.
 */
function RelevanceBar({ band }: { band: RelevanceBand | null }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  if (band == null) return null;
  const track = t.space.xxl + t.space.xs;
  return (
    <View
      style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm, flexShrink: 0 }}
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
    >
      <View style={{ width: track, height: t.space.xs, borderRadius: t.radius.full, backgroundColor: t.color.bg.raised, overflow: "hidden" }}>
        <View style={{ width: track * BAND_FILL[band], height: "100%", borderRadius: t.radius.full, backgroundColor: t.color.field.solid }} />
      </View>
      <View>
        {BANDS.map((b) => (
          <Text
            key={b}
            variant="caption"
            weight="semibold"
            // Two tones per card (Prism CH-24): amber stays the single provenance mark (the origin overline).
            color="secondary"
            numberOfLines={1}
            style={b === band ? undefined : { height: 0, opacity: 0 }}
            // Sizing only: never read (the row's label already says "relevance high").
            accessibilityElementsHidden={b !== band}
            importantForAccessibility={b === band ? "auto" : "no-hide-descendants"}
          >
            {tr(`chat.sources.band.${b}`)}
          </Text>
        ))}
      </View>
    </View>
  );
}

/**
 * The answer's sources as the mockup's card: a header with the count, then one
 * row per article (passages of the same article are grouped, Iris) that
 * expands in place into a well: the source's name as the overline, its URL in
 * normal case (Prism S-2), and each passage with its citation number and first
 * lines. The full passage opens in the source sheet. Memoized on the fields it reads: during a Deepen
 * the card stays still while the deep text streams.
 */
const SourceList = memo(function SourceList({
  answer,
  onOpenSource,
  only,
  related,
}: {
  answer: AnswerState;
  onOpenSource: (i: number) => void;
  /** CT-2: only the sources the final text cites; undefined shows every source. */
  only?: number[];
  related?: number[];
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [expanded, setExpanded] = useState<string | null>(null);
  const m = useMotion();
  const groups = groupSources(answer.sources, only);
  const labelLine = useOpticalLine("label");
  // Prism AX-2/AX-3: at large text the row stacks (number + title on the full width, up to 2 lines;
  // band and chevron below) and the title loses the number's indent, so no word breaks mid-way.
  const { fontScale } = useWindowDimensions();
  const stacked = fontScale >= LARGE_TEXT_SCALE;
  // Measured relevance only (Boar), as a band (Tusk: the raw value's scale depends on the query); none without it.
  const bands = relevanceBands(answer.sources);
  return (
    <Card padding="sm" style={{ gap: t.space.xs }}>
      <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm, paddingHorizontal: t.space.xs }}>
        {/* Icon on the label's optical line; the count badge centres on the row. */}
        <View style={{ flex: 1, flexDirection: "row", alignItems: "flex-start", gap: icon.gap }}>
          <IconSlot name="book-open" line={labelLine} color={t.color.text.field} />
          <Text variant="label" header style={{ flex: 1 }}>
            {tr("chat.sources.heading")}
          </Text>
        </View>
        {/* A plain count: soft, solid is for ACTIVE or a recommendation (Prism CH-23). */}
        <Badge label={String(only?.length ?? answer.sources.length)} tone="field" emphasis="soft" />
      </View>
      {groups.map((g) => {
        const open = expanded === g.key;
        const first = answer.sources[g.indexes[0]];
        const parts = sourceParts(first.source);
        const origin = first.collectionId ? tr("chat.sources.myDocuments") : parts.name ?? tr("chat.sources.corpus");
        const numbers = g.indexes.map((i) => i + 1).join(", ");
        const passages = g.indexes.length;
        const groupBand = bestBand(g.indexes.map((i) => bands[i]));
        return (
          <View
            key={g.key}
            style={{
              borderRadius: t.radius.md,
              borderWidth: t.size.border,
              borderColor: open ? t.color.field.solid : "transparent",
              // The mockup's source rows sit in canvas wells inside the card.
              backgroundColor: t.color.bg.canvas,
            }}
          >
            <Pressable
              onPress={() => {
                m.animateNextLayout();
                setExpanded(open ? null : g.key);
              }}
              accessibilityRole="button"
              accessibilityLabel={
                tr("chat.sources.groupLabel", { numbers, title: g.title, origin, count: passages }) +
                (groupBand ? `, ${tr("chat.sources.relevance", { band: tr(`chat.sources.band.${groupBand}`) })}` : "")
              }
              accessibilityHint={tr("chat.sources.expandHint")}
              accessibilityState={{ expanded: open }}
              style={({ pressed }) => ({
                flexDirection: stacked ? "column" : "row",
                alignItems: stacked ? "stretch" : "center",
                gap: t.space.sm,
                minHeight: t.size.controlSm,
                paddingHorizontal: t.space.sm,
                paddingVertical: t.space.xs,
                borderRadius: t.radius.md,
                backgroundColor: pressed ? t.color.bg.sunken : undefined,
              })}
              hitSlop={{ top: (t.size.touch - t.size.controlSm) / 2, bottom: (t.size.touch - t.size.controlSm) / 2 }}
            >
              {stacked ? (
                <>
                  <Text variant="footnote" numberOfLines={open ? undefined : 2}>
                    <Text variant="footnote" weight="semibold" numeric>{`${g.indexes[0] + 1}  `}</Text>
                    {g.title}
                  </Text>
                  {passages > 1 && <MetaLine items={[tr("chat.sources.passages", { count: passages })]} variant="caption" />}
                  <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm }}>
                    <RelevanceBar band={groupBand} />
                    <View style={{ flex: 1 }} />
                    <Icon name={open ? "chevron-up" : "chevron-down"} size="sm" color={t.color.text.secondary} edge="end" />
                  </View>
                </>
              ) : (
                <>
                <View
                  style={{
                    minWidth: t.size.iconLg,
                    minHeight: t.size.iconLg,
                    paddingHorizontal: t.space.xs,
                    borderRadius: t.radius.full,
                    alignItems: "center",
                    justifyContent: "center",
                    backgroundColor: t.color.bg.raised,
                  }}
                >
                  <Text variant="caption" weight="semibold" numeric maxFontSizeMultiplier={1.5}>
                    {g.indexes[0] + 1}
                  </Text>
                </View>
                <View style={{ flex: 1 }}>
                  <Text variant="footnote" numberOfLines={open ? undefined : 1}>
                    {g.title}
                  </Text>
                  {passages > 1 && <MetaLine items={[tr("chat.sources.passages", { count: passages })]} variant="caption" numberOfLines={1} />}
                </View>
                <RelevanceBar band={groupBand} />
                <Icon name={open ? "chevron-up" : "chevron-down"} size="sm" color={t.color.text.secondary} edge="end" />
                </>
              )}
            </Pressable>
            {open && (
              <View style={{ gap: t.space.sm, paddingHorizontal: t.space.md, paddingBottom: t.space.md }}>
                {/* The mockup's overline line: where it comes from on the left, its path on the right. */}
                {/* The row ends on the chevron's edge above (sm, not the well's md), expand icon by its stroke (Prism UX-5). */}
                <View style={{ flexDirection: "row", alignItems: "flex-start", gap: icon.gap, marginRight: t.space.sm - t.space.md }}>
                  {/* Iris: the caps origin shrinks before it touches the icon; one gap token. */}
                  <Text variant="label" color="field" numberOfLines={1} style={{ flexShrink: 1 }}>
                    {origin}
                  </Text>
                  {parts.url && (
                    <Text variant="caption" color="secondary" numberOfLines={1} ellipsizeMode="middle" selectable style={{ flex: 1, textAlign: "right" }}>
                      {parts.url.replace(/^https?:\/\/(www\.)?/, "")}
                    </Text>
                  )}
                  {/* Says the passage below opens the full source (Iris). */}
                  {!parts.url && <View style={{ flex: 1 }} />}
                  <IconSlot name="maximize-2" line={labelLine} color={t.color.text.secondary} edge="end" />
                </View>
                {/* The passage itself opens the full source (no extra "Full passage" line, as in the mockup). */}
                {g.indexes.map((i) => (
                  <Pressable
                    key={answer.sources[i].chunkId}
                    onPress={() => onOpenSource(i)}
                    accessibilityRole="button"
                    accessibilityLabel={answer.sources[i].body}
                    accessibilityHint={tr("chat.sources.openHint")}
                  >
                    <Text variant="footnote" numberOfLines={4}>
                      {passages > 1 ? `[${i + 1}] ${answer.sources[i].body}` : answer.sources[i].body}
                    </Text>
                  </Pressable>
                ))}
              </View>
            )}
          </View>
        );
      })}
      {related && related.length > 0 && <RelatedSources answer={answer} indexes={related} />}
    </Card>
  );
}, (a, b) =>
  a.onOpenSource === b.onOpenSource && sameAnswerFields(a.answer, b.answer, ["sources"]) && sameNumbers(a.only, b.only) && sameNumbers(a.related, b.related)
);

/**
 * Retrieved but not cited (Prism CT-2): collapsed under "Related in your library", no number
 * (they are not citations), no percentage, no amber. Inside the sources card, or alone in its
 * slot when the answer cites nothing.
 */
function RelatedSources({ answer, indexes }: { answer: AnswerState; indexes: number[] }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [open, setOpen] = useState(false);
  const m = useMotion();
  const groups = groupSources(answer.sources, indexes);
  return (
    <View style={{ gap: t.space.sm }}>
      {/* Neutral text action, no ember inside the amber card (Iris). */}
      <View style={{ paddingHorizontal: t.space.sm }}>
        <TextAction
          label={tr(open ? "chat.sources.hideRelated" : "chat.sources.related", { count: groups.length })}
          icon={open ? "chevron-up" : "chevron-down"}
          expanded={open}
          onPress={() => {
            m.animateNextLayout();
            setOpen((o) => !o);
          }}
        />
      </View>
      {open &&
        groups.map((g) => (
          <View key={g.key} style={{ gap: t.space.xxs, paddingHorizontal: t.space.sm }}>
            <Text variant="footnote" numberOfLines={2}>
              {g.title}
            </Text>
            <MetaLine items={[sourceParts(answer.sources[g.indexes[0]].source).name ?? tr("chat.sources.corpus")]} variant="caption" />
          </View>
        ))}
    </View>
  );
}

/**
 * No strong source on this phone (Iris, specs/weak-sources.md): in the source card's slot, a
 * neutral note (no amber: amber means provenance, and there is none), one focus for readers.
 * "Show closest passages" only when the engine still returned some, marked as weak, unnumbered.
 */
function WeakSourceNote({ answer, incomplete, uncited }: { answer: AnswerState; incomplete?: boolean; uncited?: boolean }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [open, setOpen] = useState(false);
  const m = useMotion();
  const groups = groupSources(answer.sources);
  // CT-5: passages on the topic were found but the model cited none: say that, not "nothing matched".
  const title = tr(uncited ? "chat.weak.titleUncited" : "chat.weak.title");
  const body = tr(uncited ? "chat.weak.bodyUncited" : incomplete ? "chat.weak.bodyIncomplete" : "chat.weak.body");
  return (
    // A status, not a card (Prism CX-7): a card with a caps title read as an empty sources card or a button.
    <View style={{ gap: t.space.sm }}>
      {/* The marker is read, never decorative (Iris/Prism): "No strong source on this phone". */}
      <View accessible accessibilityRole="text" accessibilityLabel={`${title}. ${body}`} style={{ gap: t.space.sm }}>
        <IconText icon="book" variant="footnote" weight="semibold" color="secondary" iconColor={t.color.text.secondary}>
          {title}
        </IconText>
        {/* The one warning of the answer, in the app's language (the text no longer repeats it, CX-5). */}
        <Text variant="footnote" color="secondary">
          {body}
        </Text>
      </View>
      {groups.length > 0 && (
        <TextAction
          // CT-5 (Iris): these are the passages found, not the closest of a weak search.
          label={
            uncited
              ? tr(open ? "chat.weak.hideFound" : "chat.weak.showFound", { count: groups.length })
              : tr(open ? "chat.weak.hideClosest" : "chat.weak.showClosest")
          }
          icon={open ? "chevron-up" : "chevron-down"}
          expanded={open}
          onPress={() => {
            m.animateNextLayout();
            setOpen((o) => !o);
          }}
        />
      )}
      {open && (
        <View style={{ gap: t.space.sm }}>
          <Text variant="label" color="secondary" header>
            {tr(uncited ? "chat.weak.foundTitle" : "chat.weak.closestTitle")}
          </Text>
          {groups.map((g) => (
            <View key={g.key} style={{ gap: t.space.xxs }}>
              <Text variant="footnote" numberOfLines={2}>
                {g.title}
              </Text>
              <MetaLine
                items={uncited ? [sourceParts(answer.sources[g.indexes[0]].source).name] : [sourceParts(answer.sources[g.indexes[0]].source).name, tr("chat.weak.weakMatch")]}
                variant="caption"
              />
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

/**
 * The decline under the library's own passage (declineAfterSnippet): one quiet line, no card, no
 * error icon. The passage above is the answer; "Answer anyway" is a link in the passage's own link
 * style ("Show more": ghost, ember text), so it reads as tappable next to the grey line (Boar, prints v1.1).
 */
function HeldAfterSnippet({ onAnswerAnyway }: { onAnswerAnyway?: () => void }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  return (
    <View style={{ alignItems: "flex-start" }}>
      <Text variant="footnote" color="secondary">
        {tr("chat.weak.heldAfterSnippet")}
      </Text>
      {onAnswerAnyway && (
        // Ember, like the v1.1 decision: a neutral TextAction read as more grey text under the grey line (Piston 4b25507).
        <TextAction tone="accent" label={tr("chat.weak.answerAnyway")} accessibilityHint={tr("chat.weak.answerAnywayHint")} onPress={onAnswerAnyway} />
      )}
    </View>
  );
}

/**
 * Weak-sources state A (Iris spec, Boar's decision): the compact model found nothing in this phone's
 * library and didn't guess. The card is the answer; "Answer anyway (may be wrong)" generates for the
 * same question (state B). No primary ember, no amber; the receipt shows its time only (Prism CX-9).
 */
function DeclinedNoSource({ answer, onAnswerAnyway, incomplete }: { answer: AnswerState; onAnswerAnyway?: () => void; incomplete?: boolean }) {
  // Found but unsupported (Tusk 237764a) or nothing found: the card says which.
  const { title, body } = declineCopy(answer, incomplete);
  const found = answer.sources.length > 0;
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [open, setOpen] = useState(false);
  const m = useMotion();
  const groups = groupSources(answer.sources);
  return (
    <Card radius="card" padding="compact" style={{ gap: t.space.sm }}>
      <View accessible accessibilityLabel={`${tr(title)}. ${tr(body)}`} style={{ gap: t.space.sm }}>
        <IconText icon="search" variant="cardTitle" iconColor={t.color.text.secondary}>
          {tr(title)}
        </IconText>
        <Text variant="footnote" color="secondary">
          {tr(body)}
        </Text>
      </View>
      {/* The risk before the action, seen and heard (Prism CX-8; still not inside the label, Boar copy rule):
          the reader hears "May be wrong." then the button, so the button carries no hint repeating it. */}
      {onAnswerAnyway && (
        <Text variant="caption" color="secondary">
          {tr("chat.weak.answerAnywayHint")}
        </Text>
      )}
      <View style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: t.space.md }}>
        {onAnswerAnyway && (
          <Button label={tr("chat.weak.answerAnyway")} variant="secondary" size="sm" onPress={onAnswerAnyway} />
        )}
        {groups.length > 0 && (
          <TextAction
            label={found ? tr(open ? "chat.weak.hideFound" : "chat.weak.showFound", { count: groups.length }) : tr(open ? "chat.weak.hideClosest" : "chat.weak.showClosest")}
            icon={open ? "chevron-up" : "chevron-down"}
            expanded={open}
            onPress={() => {
              m.animateNextLayout();
              setOpen((o) => !o);
            }}
          />
        )}
      </View>
      {open && (
        <View style={{ gap: t.space.sm }}>
          <Text variant="label" color="secondary" header>
            {tr(found ? "chat.weak.foundTitle" : "chat.weak.closestTitle")}
          </Text>
          {groups.map((g) => (
            <View key={g.key} style={{ gap: t.space.xxs }}>
              <Text variant="footnote" numberOfLines={2}>
                {g.title}
              </Text>
              <MetaLine
                items={found ? [sourceParts(answer.sources[g.indexes[0]].source).name] : [sourceParts(answer.sources[g.indexes[0]].source).name, tr("chat.weak.weakMatch")]}
                variant="caption"
              />
            </View>
          ))}
        </View>
      )}
    </Card>
  );
}

/** "Not a substitute for emergency services": under health and preparedness answers (Boar E-1). */
function EmergencyNote() {
  const t = useTokens();
  const { t: tr } = useTranslation();
  return (
    <View
      accessible
      accessibilityLabel={tr("chat.safety.emergencyNote")}
      style={{ paddingHorizontal: t.space.xs }}
    >
      <IconText icon="alert-circle" variant="footnote" color="secondary" iconColor={t.color.text.secondary}>
        {tr("chat.safety.emergencyNote")}
      </IconText>
    </View>
  );
}

function Notice({ tier, snippetShown, interrupted, onRetry }: { tier?: TierState; snippetShown: boolean; interrupted?: boolean; onRetry: () => void }) {
  const { t: tr } = useTranslation();
  if (!tier || !noticeShown(tier, interrupted)) return null;
  if (interrupted || tier.outcome === "interrupted") {
    return <Banner tone="warning" message={tr("chat.notice.interrupted")} actionLabel={tr("chat.actions.retry")} onAction={onRetry} />;
  }
  switch (tier.outcome) {
    case "stopped":
      return <Banner tone="info" icon="square" message={tr(snippetShown && !tier.text ? "chat.notice.stoppedSnippet" : "chat.notice.stopped")} />;
    case "timeout":
      return <Banner tone="warning" message={tr("chat.notice.timeout")} actionLabel={tr("chat.actions.retry")} onAction={onRetry} />;
    case "error":
      return (
        <Banner
          tone="danger"
          message={tr(`chat.error.${tier.error?.code ?? "generic"}`)}
          actionLabel={tr("chat.actions.retry")}
          onAction={onRetry}
        />
      );
    default:
      return null;
  }
}

/** Memo on the fields it reads (the snippet, the sources, whether the model's pass is done): still while text streams below. */
const InstantSnippet = memo(function InstantSnippet({
  answer,
  isFinal,
  onOpenSource,
}: {
  answer: AnswerState;
  isFinal: boolean;
  onOpenSource: (i: number) => void;
}) {
  const t = useTokens();
  // At large text the overline wraps as far as it needs, so the source's name stays (Prism AN-1).
  const overlineLines = chatLargeText(useWindowDimensions().fontScale >= LARGE_TEXT_SCALE).snippetOverlineLines;
  const { t: tr } = useTranslation();
  const [userExpanded, setUserExpanded] = useState<boolean | null>(null);
  const m = useMotion();
  const snippet = answer.instant!;
  // The engine's "(em inglês)" lead moves to the header; the body is the passage itself.
  const { lang, body } = sourceLanguageLead(snippet.text);
  // 0-based, as the engine sends it ("[n]" = sourceIndex + 1).
  const source = answer.sources[snippet.sourceIndex];
  // Collapses to a short preview once the model's answer is done, unless the user chose otherwise.
  const autoCollapsed = snippetAutoCollapses(answer, isFinal);
  const expanded = userExpanded ?? !autoCollapsed;
  return (
    <Card padding="sm" style={{ gap: t.space.xs }}>
      <Text variant="caption" color="field" weight="semibold" numberOfLines={overlineLines}>
        {[lang ? `${tr("chat.snippet.fromSource")} (${lang})` : tr("chat.snippet.fromSource"), source?.title].filter(Boolean).join(" · ")}
      </Text>
      <Text variant={isFinal ? "body" : "callout"} selectable>
        {/* FMT-1: a pack's list flattened to " - " reads as a list again. */}
        {expanded ? splitInlineBullets(body) : previewText(body)}
      </Text>
      {/* Secondary moves in text.secondary (Prism CH-14), apart enough for their touch areas. */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.base }}>
        {!isFinal && (
          <TextAction
            label={expanded ? tr("chat.snippet.showLess") : tr("chat.snippet.showMore")}
            icon={expanded ? "chevron-up" : "chevron-down"}
            expanded={expanded}
            onPress={() => {
              m.animateNextLayout();
              setUserExpanded(!expanded);
            }}
          />
        )}
        {source && (
          <TextAction
            label={`[${snippet.sourceIndex + 1}]`}
            leadingIcon="book"
            accessibilityLabel={tr("chat.snippet.openSource", { n: snippet.sourceIndex + 1, title: source.title })}
            onPress={() => onOpenSource(snippet.sourceIndex)}
          />
        )}
      </View>
    </Card>
  );
}, (a, b) =>
  a.isFinal === b.isFinal &&
  a.onOpenSource === b.onOpenSource &&
  sameAnswerFields(a.answer, b.answer, ["instant", "sources"]) &&
  a.answer.fast?.outcome === b.answer.fast?.outcome &&
  a.answer.weakDeclined === b.answer.weakDeclined
);

/**
 * The mockup's "Copy response" pill (s1, caption in secondary, 36 tall, full radius), shared by the action
 * on the right of the actions row: Copy, or Deepen when a deeper pass is on offer (Prism CX-10). `meta`: a
 * support fact beside the label (Deepen's estimate, Prism CH-9), silent (readers hear it in the name).
 */
function ActionPill({
  icon: iconName,
  label,
  accessibilityLabel,
  meta,
  largeText,
  onPress,
}: {
  icon: IconName;
  label: string;
  accessibilityLabel?: string;
  meta?: string;
  largeText: boolean;
  onPress: () => void;
}) {
  const t = useTokens();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      hitSlop={{ top: (t.size.touch - t.size.controlSm) / 2, bottom: (t.size.touch - t.size.controlSm) / 2 }}
      style={({ pressed }) => ({
        minHeight: t.size.controlSm,
        paddingVertical: largeText ? t.space.xs : 0,
        flexDirection: "row",
        alignItems: "center",
        gap: icon.gap,
        paddingHorizontal: t.space.md,
        borderRadius: t.radius.full,
        backgroundColor: pressed ? t.color.bg.raised : t.color.bg.surface,
      })}
    >
      {/* In a pill: icon + text move together onto the pill's middle (iOS draws the label high). */}
      <IconText icon={iconName} variant="caption" color="secondary" iconColor={t.color.text.secondary} centerOnBox>
        {label}
      </IconText>
      {meta ? (
        <View importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          <MetaLine items={[meta]} variant="caption" />
        </View>
      ) : null}
    </Pressable>
  );
}

/** How an answer's blocks move: grow in when asked in this run, in place when restored; the gap above each. */
const BlockMotion = createContext<{ appear: boolean; gap: number }>({ appear: false, gap: 0 });

/**
 * One block of the answer under its header (SEND-MOTION): it enters and leaves in height (Reveal),
 * carrying its own gap, so nothing appears, vanishes or jumps in a single frame. Always rendered in its
 * place (stable position); `shown` says whether it has content.
 * A restored answer doesn't move: plain views, no animated values for every block of the history (its
 * folds open with the DS's animateNextLayout, as in a fresh answer at rest).
 */
function Block({ shown, gap, fade, children }: { shown: boolean; gap?: number; fade?: boolean; children?: ReactNode }) {
  const m = useContext(BlockMotion);
  const motion = useMotion();
  if (!m.appear) return shown ? <View style={{ paddingTop: gap ?? m.gap }}>{children}</View> : null;
  // `fade`: an end note that comes in whole with the DS enter (a fade, 220 ms; 90 under reduce motion) and
  // no growing height: revealed by height it blinked in whole for a frame, then grew with its line cut
  // (Prism F2-9). Below everything, at the end: the list glides to it.
  if (fade)
    return shown ? (
      <Reanimated.View entering={motion.entering()} exiting={motion.exiting()} style={{ paddingTop: gap ?? m.gap }}>
        {children}
      </Reanimated.View>
    ) : null;
  return (
    // appear={false}: a block grows in only when it shows after mounting hidden. What is already on screen
    // when an answer turns live (a follow-up on a restored one) or when the list remounts a row stays put.
    <Reveal shown={shown} appear={false} spaceBefore={gap ?? m.gap}>
      {children}
    </Reveal>
  );
}

export const AssistantMessage = memo(function AssistantMessage(props: AssistantMessageProps) {
  const { answer, active: running, stopping, interrupted, feedback, locale, onOpenSource } = props;
  // Prism CX-12: the answer reads as running until its text has finished showing (the steady reveal drains
  // after the model is done): the receipt, the actions and the send button wait for the last words.
  const [draining, setDraining] = useState<Record<string, boolean>>({});
  const onDraining = useCallback(
    (key: string, d: boolean) => setDraining((prev) => (!!prev[key] === d ? prev : { ...prev, [key]: d })),
    []
  );
  const active = answerStillShowing(!!running, draining);
  const revealing = active && !running;
  const onRevealing = props.onRevealing;
  useEffect(() => {
    onRevealing?.(revealing);
    // Unmounted mid-reveal (the list recycles rows): never leave the screen waiting on it.
    return () => onRevealing?.(false);
  }, [onRevealing, revealing]);
  const t = useTokens();
  const blockMotion = useMotion();
  const { t: tr } = useTranslation();
  const phase = answerPhase(answer);
  // No strong source: no [n] citations, even if weak passages came back (weak-sources spec rule 4).
  // Nothing on the topic ("weak") or found but not cited ("uncited"): no sources card, a note instead.
  const sourceless = noSourceKind(answer);
  // No strong source: no [n] citations (weak-sources spec rule 4).
  // Memoized on the sources: a new list every token would re-render every Markdown block (TierBody memo).
  const sourceTitles = useMemo(() => (sourceless === "weak" ? [] : answer.sources.map((s) => s.title)), [sourceless, answer.sources]);
  // The excerpt as a tier, kept while it doesn't change, so its TierBody memo holds too (audit #1 leftover).
  const extractOutcome = answer.instantDone?.outcome;
  const extractTier = useMemo<TierState | null>(
    () => (answer.extract ? { text: answer.extract, stage: null, outcome: extractOutcome } : null),
    [answer.extract, extractOutcome]
  );

  const placesOnly = !!answer.places && !answer.fast;
  const largeText = useWindowDimensions().fontScale >= LARGE_TEXT_SCALE;
  const footnoteLine = useOpticalLine("footnote");
  const note = noSourceNote(answer, placesOnly);
  const split = answerSourceSplit(answer);
  const cardMode = sourcesCardMode(!!active, split);
  const extractiveOnly = !!answer.instantDone && !answer.fast && !answer.places;
  const instantOnly = !!answer.instantDone && !answer.fast;
  const lastTier = answer.deep ?? answer.fast;
  // A decline has no answer of its own to rate, share or copy: its card is the whole message.
  const hasText =
    showsAnswerBody(answer) && !!(answer.fast?.text || answer.deep?.text || answer.instant || answer.extract || answer.places?.places.length);
  const done = !active && (lastTier?.outcome || instantOnly);
  const steps = running && !stopping ? generatingSteps(answer, tr) : null;
  // D3: the live research card stays until the answer's own text starts, then folds into its pill.
  const stepsShown = stepsCardShown(answer, steps);
  // The answer's own tier: the fast one, or the deep one for a question the router sent straight to the deep
  // tier (no fast pass). Only a Deepen (deep after fast) is a section of its own, titled "Deeper answer".
  const isDeepen = deepSectionLabeled(answer);
  const deepOnly = !!answer.deep && !answer.fast;
  const firstTier = deepOnly ? answer.deep : answer.fast;
  const fastSteps = !isDeepen && stepsShown && !props.waitingLibrary;
  const deepLive = isDeepen && stepsShown;
  // LIVE_RESEARCH (direction A): each pass's research as a timeline, folded from what the answer shows on
  // each render while it runs, then kept for the pill. Not for places (its sources are places).
  const rPhase = researchPhase(phase);
  const noArticle = !!answer.weakSources && answer.sources.length === 0;
  // The attempt each timeline folds: the first answer() id (a Retry replaces it), and the last one for a Deepen.
  const firstAttempt = answer.answerIds[0] ?? "";
  const lastAttempt = answer.answerIds[answer.answerIds.length - 1] ?? "";
  const fastTl = useTimeline(
    firstAttempt,
    running && !stopping && !isDeepen && !answer.places && !props.waitingLibrary && !firstTier?.outcome,
    answer.sources,
    0,
    firstTier?.detail,
    rPhase === "searching"
  );
  // A Deepen's timeline lists what its own search appends (the first answer's articles are in its sources card).
  const deepFrom = useRef<{ key: string; from: number | null } | null>(null);
  deepFrom.current = deepFromFor(deepFrom.current, lastAttempt, isDeepen && !answer.deep?.outcome, answer.sources.length);
  const deepTl = useTimeline(lastAttempt, running && !stopping && isDeepen && !answer.deep?.outcome, answer.sources, deepFrom.current.from ?? 0, answer.deep?.detail, rPhase === "searching");
  // The pill keeps its last step until it becomes the receipt (one swap, not two). It follows the card: a Deep
  // Research part past its search reads "Reading…", not "Searching…" for the whole research (iPhone, r4to).
  const tlStep = timelinePillStep(isDeepen ? deepTl : fastTl, rPhase);
  const currentStep = tlStep && steps ? tr(tlStep) : steps?.find((x) => x.status === "active")?.short;
  // Per attempt too: a retry's pill never starts from the previous attempt's last step.
  const lastStep = useRef<{ key: string; step?: string }>({ key: lastAttempt });
  if (lastStep.current.key !== lastAttempt) lastStep.current = { key: lastAttempt };
  if (currentStep) lastStep.current.step = currentStep;
  const shownStep = pillStep(currentStep, lastStep.current.step);
  // The pill only where a model answered: an extractive-only answer shows its passage, not "1 article".
  const fastPill = !!fastTl && !fastSteps && !!firstTier && summaryItems(fastTl) != null;
  const deepPill = !!deepTl && !deepLive && summaryItems(deepTl) != null;
  // The research card or its pill carries the articles while the answer runs: no count card below as well.
  const researchCarries = fastSteps || fastPill || deepLive || deepPill;
  const fastStreaming = running && !isDeepen && !firstTier?.outcome;
  const deepStreaming = running && isDeepen && !answer.deep?.outcome;
  const bodyEnter = useMemo(() => blockMotion.entering(), [blockMotion]);

  // A question routed straight to the deep tier: its receipt is the answer's, by the name.
  const topReceipt = answer.fast?.receipt ?? (deepOnly ? answer.deep?.receipt : instantOnly ? answer.instantDone?.receipt : undefined);
  const receipt = useReceipt(topReceipt, locale, receiptTagKey(answer), !!answer.weakDeclined);
  const locating = isLocating(answer);
  const waitingForCity = answer.places?.coverage === "needs_place" || locating;
  const fresh = !!props.fresh;
  const motion = useMemo(() => ({ appear: fresh, gap: t.space.md }), [fresh, t.space.md]);
  const sourcesShown = answer.sources.length > 0 && !placesOnly && !sourceless && !(cardMode === "found" && researchCarries);
  const instantBanner = !!instantOnly && !!answer.instantDone && answer.instantDone.outcome !== "success";
  const emergency = answerShowsEmergencyNote(answer, props.question ?? "", placesOnly);
  // The body's block opens with its first words, not before (an empty block would grow a bare gap).
  const fastBody = !!firstTier?.text && showsAnswerBody(answer);
  // Waiting for the user to pick a city: no clock, no receipt (nothing was answered yet).
  const deepenNow = !active && phase === "done" && canDeepen(answer);
  const deepenEst = answer.deepAvailable?.estSeconds ? formatSeconds(answer.deepAvailable.estSeconds * 1000, locale) : null;
  // A decline has its receipt too, time only (Prism CX-9, NOVO NORTE P2).
  // A deep-tier-only answer has no first receipt to show meanwhile: its clock runs like a fast answer's.
  const pill = waitingForCity ? null : active && !isDeepen ? "elapsed" : receipt ? "receipt" : null;
  return (
    <BlockMotion.Provider value={motion}>
      {/* The row's first mount (a new answer is empty then; a recycled or reopened one is whole) enters nothing:
          only blocks that come in later do. */}
      <LayoutAnimationConfig skipEntering>
        <View style={{ alignSelf: "stretch" }}>
          <View>
            <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm }}>
              <Mascot size="avatarSm" />
              {/* The mockup's name in ember (Boar: the artifact wins over "one accent per screen"). */}
              {/* Never pushed or cut by the receipt beside it (Prism CX-11): the receipt truncates instead. */}
              <Text variant="headline" color="accent" style={{ flexShrink: 0 }}>
                {tr("chat.assistantName")}
              </Text>
              {/* The running pill crossfades into the receipt in the same place (SEND-MOTION). */}
              {pill && (
                // Shrinks (the receipt's one line truncates) rather than pushing past the row at large text.
                <Swap swapKey={pill} style={{ marginLeft: "auto", flexShrink: 1, minWidth: 0 }}>
                  {pill === "elapsed" ? (
                    <Elapsed
                      locale={locale}
                      // Waiting for the library, nothing is searched yet: the pill says so (Prism HX-2).
                      step={props.waitingLibrary ? tr("chat.stepShort.prepare") : shownStep}
                    />
                  ) : (
                    receipt && <ReceiptToggle r={receipt} hidden={active} />
                  )}
                </Swap>
              )}
            </View>
            {/* The receipt's details open and close in height (SEND-MOTION S10). */}
            <Reveal shown={!!receipt?.open && !waitingForCity} spaceBefore={t.space.sm}>
              {receipt && <ReceiptDetails r={receipt} onCopy={props.onCopyReceipt} />}
            </Reveal>
          </View>

          <Block shown={!!answer.streamsFromStorage}>
            {answer.streamsFromStorage && <Banner tone="info" icon="hard-drive" message={tr("chat.notice.streamsFromStorage")} />}
          </Block>

          <Block shown={locating}>{locating && <LocatingPrompt onCity={props.onCity} />}</Block>

          <Block shown={!!answer.places}>
            {answer.places && (
              <PlacesCard
                answer={answer}
                locale={locale}
                onOpenSource={onOpenSource}
                onCity={props.onCity}
                onUseLocation={props.onUseLocation}
                onGetMap={props.onGetMap}
                focusCity={!!props.fresh}
              />
            )}
          </Block>

          <Block shown={showsInstantSnippet(answer)}>
            {showsInstantSnippet(answer) && <InstantSnippet answer={answer} isFinal={extractiveOnly} onOpenSource={onOpenSource} />}
          </Block>
          {/* NB-1: health/safety answers are the source's literal excerpt, with its [n], no model. */}
          <Block shown={!!extractTier}>
            {extractTier ? (
              <TierBody
                tier={extractTier}
                streaming={!answer.instantDone}
                sourceTitles={sourceTitles}
                onOpenSource={onOpenSource}
                drainKey="extract"
                onDraining={onDraining}
              />
            ) : null}
          </Block>

          {/* First boot: the question waits for the library to be indexed, instead of searching an empty one. */}
          <Block shown={!!props.waitingLibrary}>
            {props.waitingLibrary ? (
              <Card padding="compact" radius="card" style={{ gap: t.space.xs }}>
                {/* The spinner on the first line when the text wraps, as the steps card (Prism CH-15). */}
                <View style={{ flexDirection: "row", alignItems: "flex-start", gap: icon.gap }}>
                  <LineSlot line={footnoteLine}>
                    <StepSpinner />
                  </LineSlot>
                  <Text variant="footnote" weight="semibold" style={{ flex: 1 }}>
                    {tr("chat.stage.waitingLibrary")}
                  </Text>
                </View>
                <Text variant="caption" color="secondary">
                  {props.waitingLibrary}
                </Text>
              </Card>
            ) : null}
          </Block>
          {/* The research card, folding into its pill above the text at the first words (LIVE_RESEARCH, D3). */}
          <Block shown={fastSteps || fastPill || fastBody}>
            {fastTl && (fastSteps || fastPill) ? <ResearchPanel timeline={fastTl} phase={fastSteps ? rPhase : null} live={fastSteps} none={noArticle} /> : null}
            {fastBody && firstTier ? (
              <Reanimated.View entering={bodyEnter}>
                <TierBody tier={firstTier} streaming={fastStreaming} sourceTitles={sourceTitles} onOpenSource={onOpenSource} drainKey="fast" onDraining={onDraining} />
              </Reanimated.View>
            ) : null}
          </Block>
          <Block shown={noticeShown(firstTier, interrupted && !isDeepen)}>
            <Notice tier={firstTier} snippetShown={!!answer.instant} interrupted={interrupted && !isDeepen} onRetry={props.onRetry} />
          </Block>

          {/* A Deepen only: a question routed to the deep tier is the answer itself, above (iPhone, r4to). */}
          <Block shown={isDeepen}>
            {isDeepen && answer.deep && (
              <View style={{ paddingTop: t.space.md, borderTopWidth: t.size.hairline, borderTopColor: t.color.line.hairline }}>
                {/* A section overline in secondary, like the others: ember isn't decoration (Prism CH-22). */}
                <Text variant="label" color="secondary" header>
                  {tr("chat.deep.title")}
                </Text>
                <View style={{ paddingTop: t.space.sm }}>
                  {deepTl && (deepLive || deepPill) ? <ResearchPanel timeline={deepTl} phase={deepLive ? rPhase : null} live={deepLive} /> : null}
                  <TierBody tier={answer.deep} streaming={deepStreaming} sourceTitles={sourceTitles} onOpenSource={onOpenSource} drainKey="deep" onDraining={onDraining} />
                </View>
                <Block shown={noticeShown(answer.deep, interrupted)} gap={t.space.sm}>
                  <Notice tier={answer.deep} snippetShown={false} interrupted={interrupted} onRetry={props.onRetry} />
                </Block>
                <Block shown={!!answer.deep.receipt} gap={t.space.sm}>
                  {answer.deep.receipt && (
                    <Receipt receipt={answer.deep.receipt} locale={locale} hidden={active} onCopy={props.onCopyReceipt} />
                  )}
                </Block>
              </View>
            )}
          </Block>

          <Block shown={instantBanner}>
            {instantBanner && answer.instantDone && (
              <Banner
                tone={answer.instantDone.outcome === "error" ? "danger" : "info"}
                message={
                  answer.instantDone.outcome === "error"
                    ? tr(`chat.error.${answer.instantDone.error?.code ?? "generic"}`)
                    : tr(`chat.notice.${answer.instantDone.outcome}`)
                }
                actionLabel={answer.instantDone.outcome === "stopped" ? undefined : tr("chat.actions.retry")}
                onAction={props.onRetry}
              />
            )}
          </Block>

          {/* Right under the text, before the sources (Iris, Prism NB-1): on a risky answer it weighs more than the list. */}
          <Block shown={emergency}>{emergency && <EmergencyNote />}</Block>

          <Block shown={sourcesShown}>
            {sourcesShown && (
              // CT-2: once the engine says which [n] stayed, the card lists only those; nothing cited = no card.
              // While it writes, only the count (Prism), and not even that while the research card or its pill
              // carries the articles (LIVE_RESEARCH): the list comes in once the engine says which [n] stayed.
              // The count crossfades into the list while the block's height follows (SEND-MOTION S7).
              <Swap swapKey={cardMode === "found" || cardMode === "related" ? cardMode : "list"}>
                {cardMode === "found" ? (
                  <Card radius="card" padding="compact">
                    <IconText icon="book-open" variant="footnote" color="secondary" iconColor={t.color.text.secondary}>
                      {tr("chat.sources.found", { count: answer.sources.length })}
                    </IconText>
                  </Card>
                ) : cardMode === "related" && split ? (
                  <Card radius="card" padding="compact">
                    <RelatedSources answer={answer} indexes={split.related} />
                  </Card>
                ) : (
                  <SourceList answer={answer} onOpenSource={onOpenSource} only={split?.cited} related={split?.related} />
                )}
              </Swap>
            )}
          </Block>
          <Block shown={!!answer.weakDeclined && !active} fade>
            {answer.weakDeclined && !active &&
              (declineAfterSnippet(answer) ? (
                <HeldAfterSnippet onAnswerAnyway={props.onAnswerAnyway} />
              ) : (
                <DeclinedNoSource answer={answer} onAnswerAnyway={props.onAnswerAnyway} incomplete={props.libraryIncomplete} />
              ))}
          </Block>
          <Block shown={!!note && !!done} fade>
            {note && done && <WeakSourceNote answer={answer} incomplete={props.libraryIncomplete} uncited={note === "uncited"} />}
          </Block>

          <Block shown={!!done && hasText}>
            {done && hasText && (
              // Prism AX-1: the row wraps; at large text "Copy answer" takes a line of its own, label kept.
              <View style={{ flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: t.space.sm }}>
                <IconButton
                  icon="thumbs-up"
                  variant="surface"
                  size="sm"
                  label={tr("chat.actions.helpful")}
                  selected={feedback === "up"}
                  accessibilityState={{ selected: feedback === "up" }}
                  onPress={() => props.onRate("up")}
                />
                <IconButton
                  icon="thumbs-down"
                  variant="surface"
                  size="sm"
                  label={tr("chat.actions.unhelpful")}
                  selected={feedback === "down"}
                  accessibilityState={{ selected: feedback === "down" }}
                  onPress={() => props.onRate("down")}
                />
                <IconButton icon="share-2" variant="surface" size="sm" label={tr("chat.actions.share")} onPress={props.onShare} />
                {/* With a deeper pass on offer, it takes the pill (the most useful next step, Prism CX-10) and
                    copying joins the icons: three pills don't fit a 360-390 pt row. */}
                {deepenNow && (
                  <IconButton icon="copy" variant="surface" size="sm" label={tr("chat.actions.copyAnswer")} onPress={props.onCopy} />
                )}
                <View style={largeText ? { flexBasis: "100%", height: 0 } : { flex: 1 }} />
                {deepenNow ? (
                  <ActionPill
                    icon="layers"
                    label={tr("chat.actions.deepen")}
                    accessibilityLabel={deepenEst ? tr("chat.actions.deepenEstSpoken", { time: deepenEst }) : undefined}
                    meta={deepenEst ? tr("chat.actions.deepenEst", { time: deepenEst }) : undefined}
                    largeText={largeText}
                    onPress={props.onDeepen}
                  />
                ) : (
                  <ActionPill icon="copy" label={tr("chat.actions.copyAnswer")} largeText={largeText} onPress={props.onCopy} />
                )}
              </View>
            )}
          </Block>

          <Block shown={!active && offersAskModel(answer)}>
            {!active && offersAskModel(answer) && (
              <Button label={tr("chat.actions.askModel")} variant="secondary" icon="cpu" onPress={props.onAskModel} style={{ alignSelf: "flex-start" }} />
            )}
          </Block>
          {/* Deepen lives in the actions row; alone only if an answer offers it without that row. */}
          <Block shown={deepenNow && !(done && hasText)}>
            {deepenNow && !(done && hasText) && (
              // Not in the mockup: a quiet text link under the actions, so it doesn't compete with copying (Iris);
              // TextAction brings the touch minimum (Prism CH-7). The estimate is a support fact beside the
              // action, not part of its label (Prism CH-9); readers hear both in the action's name.
              <View style={{ flexDirection: "row", alignItems: "center", gap: icon.gap }}>
                <TextAction
                  leadingIcon="layers"
                  onPress={props.onDeepen}
                  label={tr("chat.actions.deepen")}
                  accessibilityLabel={
                    answer.deepAvailable?.estSeconds
                      ? tr("chat.actions.deepenEstSpoken", { time: formatSeconds(answer.deepAvailable.estSeconds * 1000, locale) })
                      : undefined
                  }
                />
                {answer.deepAvailable?.estSeconds ? (
                  <View importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
                    <MetaLine items={[tr("chat.actions.deepenEst", { time: formatSeconds(answer.deepAvailable.estSeconds * 1000, locale) })]} variant="caption" />
                  </View>
                ) : null}
              </View>
            )}
          </Block>
        </View>
      </LayoutAnimationConfig>
    </BlockMotion.Provider>
  );
});
