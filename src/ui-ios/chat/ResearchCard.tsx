import React, { memo, useEffect, useMemo, useRef, useState } from "react";
import { Animated, Easing, Pressable, View } from "react-native";
import Reanimated from "react-native-reanimated";
import { useTranslation } from "react-i18next";
import { Card, Icon, LineSlot, Text, useOpticalLine } from "../components";
import { META_SEPARATOR } from "../components/metaItems";
import { toneColors, useTheme, useTokens } from "../theme";
import { useMotion } from "../theme/motion";
import { Reveal } from "./Reveal";
import {
  MAX_ARTICLES,
  SPLIT_PLACEHOLDERS,
  researchView,
  rowGrows,
  stackArticles,
  summaryItems,
  type PartView,
  type ResearchArticle,
  type ResearchPhase,
  type Timeline,
} from "./liveResearch";

/**
 * The live research card, direction A "timeline" (docs/design/LIVE_RESEARCH.md): a status line with a
 * text shimmer, the article counter and a thin progress bar; the parts of the question as a vertical
 * timeline (node + connector), the articles under the part that found them. When the answer's text
 * starts, the card folds into a summary pill ("3 parts · 4 articles ›", badges stacked) that opens the
 * timeline again, read-only.
 *
 * Motion: DS roles only (Reveal for heights, `entering({ pop })` for rows, `layoutProps` for the bar,
 * `colorTransition` for the nodes). One ambient loop (`useAmbient`) drives both the shimmer and the
 * active node's pulse; it runs only while the live card is on screen, never under reduce motion.
 */

/** Shimmer band and pulse, as fractions of the loop (no timing here: the period is tokens.motion.loop). */
const PULSE_FROM = 0.5;
const PULSE_SCALE = 1.7;

/** The loop behind the shimmer and the pulse: 0 → 1 over the DS sweep period, on the native driver. */
function useAmbient(on: boolean): Animated.Value {
  const t = useTokens();
  const phase = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!on) {
      phase.setValue(0);
      return;
    }
    const loop = Animated.loop(Animated.timing(phase, { toValue: 1, duration: t.motion.loop.sweep, easing: Easing.linear, useNativeDriver: true }));
    loop.start();
    return () => loop.stop();
  }, [on, phase, t.motion]);
  return phase;
}

/**
 * The status line: a brighter copy of the text seen through a window that sweeps across it (no mask
 * library: two texts, the window's transform and the copy's opposite one). Static text when `phase` is null.
 */
function ShimmerText({ text, phase }: { text: string; phase: Animated.Value | null }) {
  const t = useTokens();
  const [w, setW] = useState(0);
  const band = t.space.xxl;
  const x = useMemo(() => phase?.interpolate({ inputRange: [0, 1], outputRange: [-band, w] }), [phase, band, w]);
  const back = useMemo(() => phase?.interpolate({ inputRange: [0, 1], outputRange: [band, -w] }), [phase, band, w]);
  return (
    <View style={{ flex: 1 }} onLayout={(e) => setW(e.nativeEvent.layout.width)}>
      <Text variant="caption" weight="semibold" color="secondary" numberOfLines={1}>
        {text}
      </Text>
      {phase && x && back && w > 0 ? (
        <Animated.View pointerEvents="none" style={{ position: "absolute", top: 0, bottom: 0, left: 0, width: band, overflow: "hidden", transform: [{ translateX: x }] }}>
          <Animated.View style={{ position: "absolute", top: 0, left: 0, width: w, transform: [{ translateX: back }] }}>
            <Text variant="caption" weight="semibold" color="primary" numberOfLines={1}>
              {text}
            </Text>
          </Animated.View>
        </Animated.View>
      ) : null}
    </View>
  );
}

/** The thin bar under the status line: ember fill, its width moving with the DS layout role. */
function ProgressBar({ value }: { value: number }) {
  const t = useTokens();
  const m = useMotion();
  const [w, setW] = useState(0);
  const height = t.space.xxs + t.size.border;
  return (
    <View
      onLayout={(e) => setW(e.nativeEvent.layout.width)}
      style={{ height, borderRadius: t.radius.full, backgroundColor: t.color.line.hairline, overflow: "hidden" }}
    >
      <Reanimated.View style={{ height, width: w * Math.max(0, Math.min(1, value)), borderRadius: t.radius.full, backgroundColor: t.color.accent.solid, ...m.layoutProps(["width"]) }} />
    </View>
  );
}

/** The part's node: pending hairline ring, active ember ring with a soft pulse, done filled ember with a check. */
function Node({ status, phase }: { status: PartView["status"]; phase: Animated.Value | null }) {
  const t = useTokens();
  const m = useMotion();
  const side = t.size.iconSm + t.space.xxs;
  const pulse = useMemo(
    () =>
      phase && {
        opacity: phase.interpolate({ inputRange: [0, 1], outputRange: [PULSE_FROM, 0] }),
        transform: [{ scale: phase.interpolate({ inputRange: [0, 1], outputRange: [1, PULSE_SCALE] }) }],
      },
    [phase]
  );
  const ring = status === "pending" ? t.color.line.hairline : t.color.accent.solid;
  return (
    <View style={{ width: side, height: side }}>
      {status === "active" && pulse ? (
        <Animated.View
          pointerEvents="none"
          style={{ position: "absolute", width: side, height: side, borderRadius: t.radius.full, borderWidth: t.size.focusRing, borderColor: t.color.accent.solid, ...pulse }}
        />
      ) : null}
      <Reanimated.View
        style={{
          width: side,
          height: side,
          borderRadius: t.radius.full,
          borderWidth: t.size.focusRing,
          borderColor: ring,
          backgroundColor: status === "done" ? t.color.accent.solid : t.color.bg.canvas,
          alignItems: "center",
          justifyContent: "center",
          ...m.colorTransition(["borderColor", "backgroundColor"]),
        }}
      >
        {status === "done" ? <Icon name="check" size={side - t.space.sm} color={t.color.accent.on} /> : null}
      </Reanimated.View>
    </View>
  );
}

/** An article's initial on its stable tone (a DS soft tone and its text colour). */
export function ArticleBadge({ article, side, ring }: { article: ResearchArticle; side: number; ring?: string }) {
  const t = useTokens();
  const tone = toneColors(t.color, article.tone);
  return (
    <View
      style={{
        width: side,
        height: side,
        borderRadius: t.radius.xs + t.space.xxs,
        backgroundColor: tone.bg,
        borderWidth: ring ? t.size.focusRing : 0,
        borderColor: ring,
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
      }}
    >
      <Text variant="caption" weight="bold" maxFontSizeMultiplier={1.2} style={{ color: tone.fg }}>
        {article.initial}
      </Text>
    </View>
  );
}

/** One article under its part: badge, title (semibold, 1 line), one line of the passage. Pops in when new. */
function ArticleRow({ article, grows, first }: { article: ResearchArticle; grows: boolean; first: boolean }) {
  const t = useTokens();
  const m = useMotion();
  const entering = useRef(grows ? m.entering({ pop: true }) : undefined).current;
  return (
    <Reveal appear={grows} spaceBefore={first ? t.space.xs + t.space.xxs : t.space.xs}>
      <Reanimated.View
        entering={entering}
        style={{
          flexDirection: "row",
          alignItems: "flex-start",
          gap: t.space.sm,
          paddingVertical: t.space.xs + t.space.xxs,
          paddingHorizontal: t.space.sm,
          borderRadius: t.radius.sm + t.space.xxs,
          backgroundColor: t.color.bg.raised,
        }}
      >
        <ArticleBadge article={article} side={t.size.iconLg - t.space.xxs} />
        <View style={{ flex: 1 }}>
          <Text variant="footnote" weight="semibold" numberOfLines={1}>
            {article.title}
          </Text>
          {article.passage ? (
            <Text variant="caption" color="secondary" numberOfLines={1}>
              {article.passage}
            </Text>
          ) : null}
        </View>
      </Reanimated.View>
    </Reveal>
  );
}

function PartRow({
  part,
  last,
  phase,
  atMount,
  none,
}: {
  part: PartView;
  last: boolean;
  phase: Animated.Value | null;
  atMount: ReadonlySet<string>;
  none: boolean;
}) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const line = useOpticalLine("footnote");
  const side = t.size.iconSm + t.space.xxs;
  const label = part.question ?? tr(part.labelKey, part.labelOpts);
  return (
    <View style={{ flexDirection: "row", gap: t.space.sm + t.space.xxs }}>
      {/* The node on the label's first line; the connector runs from it to the next node. */}
      <View style={{ width: side, alignItems: "center" }}>
        <LineSlot line={line}>
          {/* Pulses while its part searches; calm (the ring alone) while its sub-answer is written. */}
          <Node status={part.status} phase={part.status === "active" && !part.reading ? phase : null} />
        </LineSlot>
        {!last && <View style={{ flex: 1, width: t.size.focusRing, backgroundColor: t.color.line.hairline }} />}
      </View>
      <View style={{ flex: 1, paddingBottom: last ? 0 : t.space.sm + t.space.xxs }}>
        {/* Up to 2 lines: a sub-question is read, not cut to one line. */}
        <Text variant="footnote" color={part.status === "pending" ? "secondary" : "primary"} numberOfLines={2}>
          {label}
        </Text>
        {part.shown.map((a, i) => (
          <ArticleRow key={a.key} article={a} grows={rowGrows(a.key, atMount)} first={i === 0} />
        ))}
        <Reveal shown={part.more > 0} appear={!atMount.has(`more:${part.index}`)} spaceBefore={t.space.xs}>
          <Text variant="caption" color="secondary" numeric>
            {tr("chat.research.more", { count: part.more })}
          </Text>
        </Reveal>
        {/* One quiet line, not a row card: a part done with nothing new, or a search with nothing at all. */}
        <Reveal shown={part.empty || (none && part.status === "done" && part.shown.length === 0)} appear={!atMount.has(`empty:${part.index}`)} spaceBefore={t.space.xs}>
          <Text variant="caption" color="secondary">
            {tr(part.empty ? "chat.research.noneNew" : "chat.research.none")}
          </Text>
        </Reveal>
      </View>
    </View>
  );
}

/** Widths of the placeholder lines while the question is split (fractions of the text column). */
const PLACEHOLDER_WIDTHS = ["82%", "64%", "74%"] as const;

/**
 * A part not known yet (splitting the question): a pending node and a still bar where its text will be,
 * with the connector, so the card grows into the parts instead of changing shape. Static: no second loop.
 */
function PlaceholderRow({ index, last }: { index: number; last: boolean }) {
  const t = useTokens();
  const line = useOpticalLine("footnote");
  const side = t.size.iconSm + t.space.xxs;
  return (
    <View style={{ flexDirection: "row", gap: t.space.sm + t.space.xxs }}>
      <View style={{ width: side, alignItems: "center" }}>
        <LineSlot line={line}>
          <Node status="pending" phase={null} />
        </LineSlot>
        {!last && <View style={{ flex: 1, width: t.size.focusRing, backgroundColor: t.color.line.hairline }} />}
      </View>
      <View style={{ flex: 1, paddingBottom: last ? 0 : t.space.sm + t.space.xxs }}>
        <LineSlot line={line}>
          <View style={{ width: PLACEHOLDER_WIDTHS[index % PLACEHOLDER_WIDTHS.length], height: t.space.sm, borderRadius: t.radius.full, backgroundColor: t.color.bg.sunken }} />
        </LineSlot>
      </View>
    </View>
  );
}

/** Keys on screen when a card mounts: those rows show in place, later ones pop in. */
function mountKeys(tl: Timeline): ReadonlySet<string> {
  const keys = new Set<string>(tl.order);
  tl.parts.forEach((p) => {
    if (p.articles.length > MAX_ARTICLES) keys.add(`more:${p.index}`);
    if (tl.multi && p.articles.length === 0) keys.add(`empty:${p.index}`);
  });
  return keys;
}

/**
 * The timeline card. `phase` null: finished (read-only, behind the pill): no status line, no bar, every
 * part done. Live, the card is visual only (readers hear one announcement per answer, screen announcer).
 */
function TimelineCard({ timeline, phase, none }: { timeline: Timeline; phase: ResearchPhase | null; none: boolean }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const { reduceMotion } = useTheme();
  const live = phase != null;
  const ambient = useAmbient(live && !reduceMotion);
  const view = researchView(timeline, phase);
  const atMount = useRef(mountKeys(timeline)).current;
  const header = tr(view.header.key, view.header.opts);
  return (
    <Card
      padding="compact"
      radius="card"
      style={{ gap: t.space.sm }}
      importantForAccessibility={live ? "no-hide-descendants" : "auto"}
      accessibilityElementsHidden={live}
    >
      {live && (
        <>
          <View style={{ flexDirection: "row", alignItems: "center", gap: t.space.sm }}>
            <ShimmerText text={header} phase={reduceMotion ? null : ambient} />
            {view.total > 0 && (
              <Text variant="caption" weight="semibold" color="secondary" numeric>
                {tr("chat.research.articles", { count: view.total })}
              </Text>
            )}
          </View>
          <ProgressBar value={view.progress} />
        </>
      )}
      <View>
        {view.splitting &&
          Array.from({ length: SPLIT_PLACEHOLDERS }, (_, i) => <PlaceholderRow key={i} index={i} last={i === SPLIT_PLACEHOLDERS - 1} />)}
        {view.parts.map((p, i) => (
          <PartRow key={p.index} part={p} last={i === view.parts.length - 1} phase={reduceMotion ? null : ambient} atMount={atMount} none={none} />
        ))}
      </View>
    </Card>
  );
}

/** The folded card: stacked badges and "3 parts · 4 articles ›"; opens the read-only timeline. */
function SummaryPill({ timeline, open, onToggle }: { timeline: Timeline; open: boolean; onToggle: () => void }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const items = summaryItems(timeline) ?? [];
  const text = items.map((i) => tr(i.key, i.opts)).join(META_SEPARATOR);
  const badge = t.size.iconSm + t.space.xs;
  const overlap = t.space.xs + t.space.xxs;
  const stack = stackArticles(timeline);
  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="button"
      accessibilityLabel={tr("chat.research.summarySpoken", { summary: text })}
      accessibilityHint={tr("chat.research.summaryHint")}
      accessibilityState={{ expanded: open }}
      hitSlop={{ top: (t.size.touch - t.size.controlSm) / 2, bottom: (t.size.touch - t.size.controlSm) / 2 }}
      style={({ pressed }) => ({
        alignSelf: "flex-start",
        flexDirection: "row",
        alignItems: "center",
        gap: t.space.sm,
        minHeight: t.size.controlSm,
        paddingLeft: t.space.sm,
        paddingRight: t.space.md,
        borderRadius: t.radius.full,
        backgroundColor: pressed ? t.color.bg.raised : t.color.bg.surface,
      })}
    >
      {stack.length > 0 && (
        <View style={{ flexDirection: "row" }} importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
          {stack.map((a, i) => (
            <View key={a.key} style={{ marginLeft: i === 0 ? 0 : -overlap }}>
              <ArticleBadge article={a} side={badge} ring={t.color.bg.surface} />
            </View>
          ))}
        </View>
      )}
      <Text variant="caption" weight="semibold" color="secondary" numberOfLines={1} style={{ flexShrink: 1 }}>
        {text}
      </Text>
      <Icon name={open ? "chevron-down" : "chevron-right"} size="sm" color={t.color.text.secondary} />
    </Pressable>
  );
}

/**
 * The research of one tier (fast, or a Deepen): the live card while it searches and reads, then the
 * summary pill (and, opened, the read-only timeline) above the answer's text. Heights move with Reveal:
 * the card fades with its height kept, then its space closes under the DS layout animation, while the
 * pill grows in above it, so the text below slides instead of jumping.
 */
export const ResearchPanel = memo(function ResearchPanel({
  timeline,
  phase,
  live,
  none = false,
}: {
  timeline: Timeline;
  /** The tier's phase while it runs; null once it's over. */
  phase: ResearchPhase | null;
  /** The live card shows (no text yet); false: the pill. */
  live: boolean;
  /** Searched and nothing on the topic: one quiet line under the step. */
  none?: boolean;
}) {
  const t = useTokens();
  const m = useMotion();
  const [open, setOpen] = useState(false);
  const pill = !live && summaryItems(timeline) != null;
  return (
    <View>
      <Reveal shown={pill} appear>
        <View style={{ paddingBottom: t.space.sm }}>
          <SummaryPill
            timeline={timeline}
            open={open}
            onToggle={() => {
              m.animateNextLayout();
              setOpen((o) => !o);
            }}
          />
          <Reveal shown={open} spaceBefore={t.space.sm}>
            {open && <TimelineCard timeline={timeline} phase={null} none={none} />}
          </Reveal>
        </View>
      </Reveal>
      <Reveal shown={live} appear>
        <TimelineCard timeline={timeline} phase={live ? phase ?? "searching" : null} none={none} />
      </Reveal>
    </View>
  );
});
