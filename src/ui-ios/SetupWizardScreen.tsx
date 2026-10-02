/**
 * First-run setup in three steps: welcome → capacity package → install and
 * index. Every number shown is measured on the device or computed from the
 * manifest (flows-spec §4.1); nothing is typed in by hand.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AccessibilityInfo, AppState, BackHandler, findNodeHandle, Linking, Pressable, Text as RNText, useWindowDimensions, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Badge, Button, Card, EmptyState, IconName, LARGE_TEXT_SCALE, IconSlot, IconText, ListRow, Mascot, MetaLine, OptionCard, Progress, Screen, Section, Sheet, Stat, Stepper, Switch, Text, TextAction, useAnnounce, useOpticalLine } from "./components";
import type { TextColor } from "./components/Text";
import { icon as iconTokens, useTokens } from "./theme";
import { useMotion } from "./theme/motion";
import Animated, { LayoutAnimationConfig } from "react-native-reanimated";
import { impact, ImpactFeedbackStyle, notification, NotificationFeedbackType } from "../services/haptics";
import { useLanguage } from "../i18n/LanguageContext";
import { getSetupProgress, LanguageId, setActiveModelId, setSetupProgress } from "../models/settings";
import { CatalogModel, MODEL_CATALOG, TIERS } from "../models/manifest";
import { findAsset } from "../models/assetRegistry";
import { restartDownload } from "../services/downloadManager";
import { onSeedProgress, seedKnowledgeBaseIfEmpty, SeedProgress } from "../rag/seedCorpus";
import { LoadingLine } from "./flows/LoadingLine";
import { embeddingEngine } from "../rag/embed";
import { useCatalog } from "./flows/useCatalog";
import type { MemoryFit } from "../inference/memoryFit";
import { canAutoRetry, RowState } from "./flows/modelRowState";
import {
  PackageId,
  PACKAGES,
  packageAssets,
  planPackage,
  REFERENCE_BYTES_PER_SEC,
  storageShortfall,
  transferSeconds,
} from "./flows/packages";
import { failureLines, formatBytes, formatCount, formatRam, minutesAbout, minutesLeft } from "./flows/format";
import { answerModelChoices, AnswerTier, shownRecommendation } from "./flows/packages";
import { COMPACT_ONLY_MAX_RAM_BYTES, pickDefaultAnswerModel } from "../routing/defaultModel";
import { placesInstall, poiRegions } from "./flows/adapters";
import { canDownload } from "./flows/useCatalog";
import { CitySearch } from "./flows/CitySearch";
import { citySummary, deviceTimeZone, PoiRegion, suggestRegion } from "./flows/poi";
import { locateForUser } from "../services/location";
import { networkAllowed } from "../config/variant";
import { ImportList } from "./flows/ImportList";
import { RadioRow } from "./flows/RadioRow";
import * as Clipboard from "expo-clipboard";
import { OFFLINE_INSTALL_URL } from "./flows/links";
import { InstallCategory, installCategories } from "./flows/installGroups";
import { likelyTarget } from "./flows/fileImport";
import { importHeroFraction, withVerifiedImport } from "./flows/importProgress";
import { IndexPhase, installStage, stageSwapAnimates } from "./flows/installStage";
import { catalogLabel } from "./flows/catalogLabel";
import { userErrorKey } from "./flows/userError";

interface Props {
  onReady: () => void;
  /** Present when setup was reopened from Settings: lets the user leave without finishing. */
  onSkip?: () => void;
}

type Step = 1 | 2 | 3 | 4;

/** The mockup's setup rhythm (FIDELITY): 14 pt between blocks, content right under the stepper. */
function setupRhythm(t: ReturnType<typeof useTokens>) {
  return { gap: t.space.md + t.space.xxs, paddingTop: t.space.xs };
}

/** Narrowest screen (pt) where the language cards keep their EN/PT monogram (393 yes, 360 no). */
const MONOGRAM_MIN_WIDTH = 380;

const ANSWER_FIT_TONE = { resident: "success", streaming: "warning", thrashing: "warning", insufficient: "danger" } as const;

/** Above this OS font scale the two language cards stack instead of sitting side by side. */
const LARGE_TEXT = 1.15;
const LANGUAGES: { id: LanguageId; name: string }[] = [
  { id: "en", name: "English" },
  { id: "pt", name: "Português" },
];

/** No progress for this long shows the "restart downloads" escape hatch. */
const STALL_MS = 60_000;

export function SetupWizardScreen({ onReady, onSkip }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const announce = useAnnounce();
  const { languageId, setLanguage } = useLanguage();
  // The install step shows the total and ETA outside the rows: it follows every progress event (5 Hz).
  const catalog = useCatalog({ liveProgress: true });
  const lang = i18n.language;
  const [step, setStep] = useState<Step>(1);
  // A step change is a content swap (DS §6): the old step leaves in 90 ms, the new one enters after
  // it, travelling in the reading direction when going forward and against it when going back (TR-3).
  const motion = useMotion();
  const lastStep = useRef(step);
  const swap = useMemo(() => motion.crossfade(step > lastStep.current ? "forward" : step < lastStep.current ? "back" : "none"), [motion, step]);
  useEffect(() => {
    lastStep.current = step;
  }, [step]);
  const [packageId, setPackageId] = useState<PackageId>("essential");
  const [backOpen, setBackOpen] = useState(false);
  const [travel, setTravel] = useState<PoiRegion | null>(null);
  const [trip, setTrip] = useState<{ label: string; assets: CatalogModel[] } | null>(null);
  const choices = useMemo(() => answerModelChoices(MODEL_CATALOG), []);
  const [answerTier, setAnswerTier] = useState<AnswerTier>("default");
  // Set once the user picked (or progress was restored): automatic recommendations never override it.
  const [packageChosen, setPackageChosen] = useState(false);
  const [answerChosen, setAnswerChosen] = useState(false);
  const answerModel = (answerTier === "compact" && choices.compact) || choices.default;
  const [restored, setRestored] = useState(false);

  // Resume where setup was: a font-size change recreates the Android Activity,
  // and the process can be killed during a long download.
  useEffect(() => {
    getSetupProgress().then((p) => {
      if (p) {
        if (PACKAGES.some((x) => x.id === p.packageId)) setPackageId(p.packageId as PackageId);
        if (p.answerTier) setAnswerTier(p.answerTier);
        // Only a real pick is kept; an automatic recommendation is recomputed (Prism S2-3).
        setPackageChosen(!!p.packageChosen);
        setAnswerChosen(!!p.answerChosen);
        const region = p.travelRegionId ? poiRegions().find((r) => r.id === p.travelRegionId) : undefined;
        if (region) setTravel(region);
        // The trip's items back from their ids; one that no longer exists drops the trip rather than half of it.
        const tripAssets = p.trip?.assetIds.map((id) => findAsset(id));
        if (p.trip && tripAssets?.every(Boolean)) setTrip({ label: p.trip.label, assets: tripAssets as CatalogModel[] });
        // Saved before the model got its own step (flow 1: 1 welcome, 2 choose, 3 install).
        setStep(!p.flow && p.step === 3 ? 4 : !p.flow && p.step === 2 ? 2 : (p.step as Step));
      }
      setRestored(true);
    });
  }, []);
  useEffect(() => {
    if (restored)
      setSetupProgress({
        flow: 2,
        step,
        packageId,
        travelRegionId: travel?.id,
        trip: trip ? { label: trip.label, assetIds: trip.assets.map((a) => a.id) } : undefined,
        answerTier,
        packageChosen,
        answerChosen,
      });
  }, [restored, step, packageId, travel, trip, answerTier, packageChosen, answerChosen]);
  const titleRef = useRef<RNText>(null);

  // Focus and announce the title on every step change (Prism F7).
  useEffect(() => {
    const node = titleRef.current && findNodeHandle(titleRef.current);
    if (node) AccessibilityInfo.setAccessibilityFocus(node);
    // Same count the Stepper speaks: step 2 is stage 2 (Choose), step 3 starts at stage 3 (Install).
    if (step > 1) announce(t("flows.onboarding.stageOf", { n: step, total: STAGES.length, name: t(`flows.onboarding.stage.${STAGES[step - 1]}`) }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step]);

  const tier = TIERS.find((x) => x.id === PACKAGES.find((p) => p.id === packageId)!.tier)!;
  const assets = useMemo(() => {
    const all = [...packageAssets(tier, MODEL_CATALOG, answerModel), ...(travel ? placesInstall(travel) : []), ...(trip?.assets ?? [])];
    // The gazetteer can come from both the region and the trip: install it once.
    return all.filter((a, i) => all.findIndex((b) => b.id === a.id) === i);
  }, [tier, travel, trip, answerModel]);
  const present = useMemo(
    () => Object.fromEntries(Object.values(catalog.statuses).map((s) => [s.asset.id, s.present])),
    [catalog.statuses]
  );
  const allPresent = catalog.loaded && assets.every((a) => present[a.id]);

  // Leaving a setup reopened from Settings forgets its progress, or the next launch would resume it.
  const skip = useMemo(
    () =>
      onSkip &&
      (() => {
        setSetupProgress(null);
        onSkip();
      }),
    [onSkip]
  );
  // While the search index is being built, back does nothing: the on-screen back link is hidden then too,
  // and leaving the step would hide the progress while indexing carries on.
  const indexingRef = useRef(false);
  const goBack = useCallback(() => {
    if (step === 4 && indexingRef.current) return true;
    if (step === 4 && !allPresent) setBackOpen(true);
    else if (step > 1) setStep((s) => (s - 1) as Step);
    else if (skip) skip();
    else return false;
    return true;
  }, [step, allPresent, skip]);

  useEffect(() => {
    const sub = BackHandler.addEventListener("hardwareBackPress", goBack);
    return () => sub.remove();
  }, [goBack]);

  // Restored into step 3 (process killed mid-download): start what is still missing, once.
  const resumed = useRef(false);
  useEffect(() => {
    if (!restored || resumed.current || step !== 4 || !catalog.loaded || !networkAllowed()) return;
    resumed.current = true;
    for (const a of assets) {
      const kind = catalog.view(a).state.kind;
      if (!present[a.id] && canDownload(a) && kind !== "downloading" && kind !== "verifying") catalog.download(a);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restored, step, catalog.loaded]);

  const startInstall = () => {
    resumed.current = true;
    impact(ImpactFeedbackStyle.Medium);
    setStep(4);
    // The offline build has no network: step 4 imports files instead.
    for (const a of assets) if (!present[a.id] && canDownload(a)) catalog.download(a);
  };

  return (
    <>
      {/* The first step appears with the route's own fade; only later changes animate here. */}
      <LayoutAnimationConfig skipEntering>
        <Animated.View key={step} style={{ flex: 1 }} entering={swap.entering} exiting={swap.exiting}>
          {step === 1 && (
            <Welcome
              titleRef={titleRef}
              languageId={languageId}
              setLanguage={setLanguage}
              deviceRamBytes={catalog.deviceRamBytes}
              freeBytes={catalog.freeBytes}
              lang={lang}
              onNext={() => setStep(2)}
              onSkip={skip}
            />
          )}
          {step === 2 && (
            <ModelStep
              titleRef={titleRef}
              choices={choices}
              answerTier={answerTier}
              onAnswerTier={setAnswerTier}
              answerChosen={answerChosen}
              onUserAnswer={(tierId) => {
                setAnswerChosen(true);
                setAnswerTier(tierId);
              }}
              fit={catalog.fit}
              deviceRamBytes={catalog.deviceRamBytes}
              loaded={catalog.loaded}
              restored={restored}
              lang={lang}
              onBack={() => setStep(1)}
              onNext={() => setStep(3)}
            />
          )}
          {step === 3 && (
            <PackageStep
              titleRef={titleRef}
              selected={packageId}
              onSelect={setPackageId}
              present={present}
              freeBytes={catalog.freeBytes}
              fit={catalog.fit}
              loaded={catalog.loaded}
              lang={lang}
              travel={travel}
              onTravel={setTravel}
              trip={trip}
              onTrip={setTrip}
              catalog={catalog}
              choices={choices}
              answerTier={answerTier}
              packageChosen={packageChosen}
              onUserPackage={(id) => {
                setPackageChosen(true);
                setPackageId(id);
              }}
              restored={restored}
              onBack={() => setStep(2)}
              onInstall={startInstall}
            />
          )}
          {step === 4 && (
            <InstallStep
              titleRef={titleRef}
              assets={assets}
              catalog={catalog}
              allPresent={allPresent}
              lang={lang}
              onBack={() => setBackOpen(true)}
              onIndexingChange={(v) => {
                indexingRef.current = v;
              }}
              onChoosePackage={() => setStep(3)}
              answerModel={answerModel}
              placesLabel={trip?.label ?? (travel ? (lang.startsWith("pt") ? travel.name.pt : travel.name.en) : undefined)}
              onReady={async () => {
                // The chosen answer model (default or compact) writes the answers from now on.
                if (answerModel) await setActiveModelId("llm", answerModel.id);
                setSetupProgress(null);
                onReady();
              }}
            />
          )}
        </Animated.View>
      </LayoutAnimationConfig>
      <Sheet
        visible={backOpen}
        onClose={() => setBackOpen(false)}
        title={t("flows.onboarding.backTitle")}
        description={t("flows.onboarding.backBody")}
        footer={
          <>
            <Button label={t("flows.onboarding.stay")} variant="secondary" fullWidth onPress={() => setBackOpen(false)} />
            <Button
              label={t("flows.onboarding.goBack")}
              variant="primary"
              fullWidth
              onPress={() => {
                setBackOpen(false);
                setStep(3);
              }}
            />
          </>
        }
      />
    </>
  );
}

/** The four stages the user sees: step 3 covers both install and index. */
const STAGES = ["start", "model", "knowledge", "download", "index"] as const;

/** The labelled stepper (the mockup's HARDWARE → MODEL TIER → INSTALL → INDEXING). */
function SetupStepper({ stage }: { stage: number }) {
  const { t } = useTranslation();
  const names = STAGES.map((s) => t(`flows.onboarding.stage.${s}`));
  return (
    <Stepper
      steps={names}
      current={stage}
      accessibilityLabel={t("flows.onboarding.stageOf", { n: stage + 1, total: STAGES.length, name: names[stage] })}
    />
  );
}

function StepHeader({ titleRef, stage, title, subtitle }: { titleRef: React.RefObject<RNText | null>; stage: number; title: string; subtitle?: string }) {
  const tokens = useTokens();
  return (
    // 18 pt from the stepper's labels to the title, as measured on the mockup (FIDELITY).
    <View style={{ gap: tokens.space.base + tokens.space.xxs }}>
      <SetupStepper stage={stage} />
      <View style={{ gap: tokens.space.xs }}>
        {/* 26 pt like the mockup's step titles (FIDELITY). */}
        <Text ref={titleRef} variant="title1" header>
          {title}
        </Text>
        {subtitle && (
          <Text variant="footnote" color="secondary">
            {subtitle}
          </Text>
        )}
      </View>
    </View>
  );
}

function Welcome({
  titleRef,
  languageId,
  setLanguage,
  deviceRamBytes,
  freeBytes,
  lang,
  onNext,
  onSkip,
}: {
  titleRef: React.RefObject<RNText | null>;
  languageId: LanguageId;
  setLanguage: (id: LanguageId) => Promise<void>;
  deviceRamBytes: number;
  freeBytes: number;
  lang: string;
  onNext: () => void;
  onSkip?: () => void;
}) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const announce = useAnnounce();
  const { fontScale } = useWindowDimensions();
  const { width } = useWindowDimensions();
  // The EN/PT monogram fits two cards side by side on a 393 pt screen, not on a 360 dp one (Iris).
  const monogram = width >= MONOGRAM_MIN_WIDTH && fontScale <= LARGE_TEXT;
  const motion = useMotion();
  // Measured on this phone; a value the OS would not give is left out, never guessed.
  const phone = [
    { key: "phoneMemory", value: deviceRamBytes > 0 ? formatRam(deviceRamBytes, lang) : null },
    { key: "phoneFree", value: freeBytes > 0 ? formatBytes(freeBytes, lang) : null },
    { key: "phoneEngine", value: t("flows.onboarding.phoneEngineValue") },
  ].filter((r): r is { key: string; value: string } => !!r.value);
  return (
    <Screen
      contentStyle={setupRhythm(tokens)}
      ambient
      edges={["top", "bottom", "left", "right"]}
      footer={
        <>
          <Button size="lg" label={t("flows.onboarding.start")} icon="arrow-right" iconPosition="end" fullWidth onPress={onNext} />
          {/* The neutral back link of steps 2 and 3, not a second ember under the CTA (Prism FL-24). */}
          {onSkip && <BackLink label={t("flows.onboarding.backToApp")} onPress={onSkip} />}
        </>
      }
    >
      <SetupStepper stage={0} />
      {/* The mockup's brand row: disc 48, gap 10, wordmark 28 (title1 26), tagline 12 in a2, 3 pt apart. */}
      <View style={{ flexDirection: "row", alignItems: "center", gap: tokens.space.md - tokens.space.xxs }}>
        <Mascot size="brand" />
        <View style={{ flex: 1, gap: tokens.space.xxs }}>
          {/* The identity's wordmark is lowercase (chat header, splash). */}
          <Text ref={titleRef} variant="title1" header>
            boar
          </Text>
          <Text variant="caption" color="field">
            {t("flows.onboarding.brandSub")}
          </Text>
        </View>
      </View>
      {/* One overline and one paragraph of 13.5, like the mockup (Iris, Prism F1-3). */}
      <Card padding="compact" style={{ gap: tokens.space.xs + tokens.space.xxs }}>
        <Text variant="label" color="field">
          {t("flows.onboarding.introLabel")}
        </Text>
        <Text variant="footnote">{t("flows.onboarding.introBody")}</Text>
      </Card>
      <View style={{ gap: tokens.space.sm }}>
        <Text variant="label" color="secondary">
          {t("flows.settings.language")}
        </Text>
        {/* Side by side like the mockup; stacked when large text would break "Português". */}
        <View accessibilityRole="radiogroup" style={{ flexDirection: fontScale > LARGE_TEXT ? "column" : "row", gap: tokens.space.sm }}>
          {LANGUAGES.map((l) => {
            const selected = languageId === l.id;
            return (
              <View key={l.id} style={fontScale > LARGE_TEXT ? undefined : { flex: 1 }}>
                <OptionCard
                  title={l.name}
                  // With the monogram, the ember disc + border + raised surface carry the selection; a check
                  // beside "Português" did not fit and shrank it (Prism S1-2). Without it, the check does.
                  indicator={monogram ? "none" : "check"}
                  selected={selected}
                  leading={
                    monogram ? (
                      <Animated.View
                        style={{
                          width: tokens.size.controlSm,
                          height: tokens.size.controlSm,
                          borderRadius: tokens.radius.full,
                          alignItems: "center",
                          justifyContent: "center",
                          backgroundColor: selected ? tokens.color.accent.solid : tokens.color.bg.raised,
                          ...motion.colorTransition(["backgroundColor"]),
                        }}
                        importantForAccessibility="no-hide-descendants"
                        accessibilityElementsHidden
                      >
                        <Text variant="caption" weight="semibold" color={selected ? "onAccent" : "primary"}>
                          {l.id.toUpperCase()}
                        </Text>
                      </Animated.View>
                    ) : undefined
                  }
                  onPress={async () => {
                    await setLanguage(l.id);
                    announce(i18n.getFixedT(l.id)("flows.onboarding.languageAnnounce"));
                  }}
                />
              </View>
            );
          })}
        </View>
      </View>
      {/* The mockup's hardware card, with measured values only: no "Verified" column (Prism F1-5/F1-6). */}
      <Card style={{ paddingTop: tokens.space.md, paddingHorizontal: tokens.space.md + tokens.space.xxs, paddingBottom: tokens.space.xs + tokens.space.xxs }}>
        <Text variant="label" color="secondary">
          {t("flows.onboarding.phoneLabel")}
        </Text>
        {phone.map((r, i) => (
          <View
            key={r.key}
            accessible
            // Large text: the value goes under its label, like ListRow (Prism FL-3: 378 pt in a 307 pt card at 2.0).
            style={{
              ...(fontScale >= LARGE_TEXT_SCALE
                ? { flexDirection: "column", alignItems: "flex-start", gap: tokens.space.xxs }
                : { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: tokens.space.sm }),
              paddingVertical: tokens.space.xs + tokens.space.xxs,
              borderTopWidth: i > 0 ? tokens.size.hairline : 0,
              borderTopColor: tokens.color.line.row,
            }}
          >
            <Text variant="footnote" color="secondary">
              {t(`flows.onboarding.${r.key}`)}
            </Text>
            <Text variant="caption" numeric>
              {r.value}
            </Text>
          </View>
        ))}
      </Card>
    </Screen>
  );
}

/**
 * Step 2: the model that writes the answers, chosen from the suggested ones (the standard and the
 * compact answer model) with their real name, size and how they fit this phone. Tusk's rule
 * (src/routing/defaultModel.ts) pre-selects one until the user picks.
 */
function ModelStep({
  titleRef,
  choices,
  answerTier,
  onAnswerTier,
  answerChosen,
  onUserAnswer,
  fit,
  deviceRamBytes,
  loaded,
  restored,
  lang,
  onBack,
  onNext,
}: {
  titleRef: React.RefObject<RNText | null>;
  choices: Partial<Record<AnswerTier, CatalogModel>>;
  answerTier: AnswerTier;
  onAnswerTier: (tier: AnswerTier) => void;
  answerChosen: boolean;
  onUserAnswer: (tier: AnswerTier) => void;
  fit: (model: CatalogModel) => MemoryFit | undefined;
  deviceRamBytes: number;
  loaded: boolean;
  restored: boolean;
  lang: string;
  onBack: () => void;
  onNext: () => void;
}) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const tiers = (["default", "compact"] as const).filter((tierId) => !!choices[tierId]);
  const pick = pickDefaultAnswerModel(
    tiers.map((tierId) => choices[tierId]!).map((m) => ({ id: m.id, answerTier: m.answerTier, fit: fit(m)?.verdict })),
    deviceRamBytes
  );
  const compactSuggested = !!choices.compact && pick?.id === choices.compact.id;
  const recommendedTier: AnswerTier | undefined = pick ? (pick.id === choices.compact?.id ? "compact" : "default") : undefined;
  // Recommended = pre-selected: follow the routing rule until the user picks.
  useEffect(() => {
    if (restored && loaded && !answerChosen && recommendedTier) onAnswerTier(recommendedTier);
  }, [restored, loaded, answerChosen, recommendedTier, onAnswerTier]);
  const why = compactSuggested
    ? pick?.reason === "compact-low-ram"
      ? t("flows.onboarding.compactLowRam", { ram: formatRam(COMPACT_ONLY_MAX_RAM_BYTES, lang) })
      : t("flows.onboarding.compactWhy")
    : undefined;

  return (
    <Screen
      contentStyle={setupRhythm(tokens)}
      edges={["top", "bottom", "left", "right"]}
      footer={
        <>
          <Button size="lg" icon="arrow-right" iconPosition="end" label={t("flows.onboarding.continue")} fullWidth disabled={!loaded} onPress={onNext} />
          <BackLink label={t("flows.onboarding.back")} onPress={onBack} />
        </>
      }
    >
      <StepHeader titleRef={titleRef} stage={1} title={t("flows.onboarding.modelTitle")} subtitle={t("flows.onboarding.modelSub")} />
      <View accessibilityRole="radiogroup" style={{ gap: tokens.space.sm }}>
        {tiers.map((tierId) => {
          const m = choices[tierId]!;
          const f = fit(m);
          return (
            <OptionCard
              key={tierId}
              title={catalogLabel(m, t, { technical: true })}
              description={`${catalogLabel(m, t)} · ${t(`flows.assistant.sub.${tierId}`)}`}
              selected={answerTier === tierId}
              onPress={() => onUserAnswer(tierId)}
              badge={tierId === recommendedTier ? <Badge label={t("flows.onboarding.recommended")} tone="accent" emphasis="solid" /> : undefined}
              trailing={formatBytes(m.sizeBytes, lang)}
              meta={[
                f && t(`flows.row.fitShort.${f.verdict}`),
                f && t("flows.onboarding.workingMemory", { size: formatRam(f.anonBytes + (f.expertFraction === 0 ? f.fileBytes : 0), lang) }),
              ]}
            />
          );
        })}
      </View>
      {why && (
        <Text variant="footnote" color="secondary">
          {why}
        </Text>
      )}
    </Screen>
  );
}

function PackageStep({
  titleRef,
  selected,
  onSelect,
  present,
  freeBytes,
  fit,
  loaded,
  lang,
  travel,
  onTravel,
  trip,
  onTrip,
  catalog,
  choices,
  answerTier,
  packageChosen,
  onUserPackage,
  restored,
  onBack,
  onInstall,
}: {
  titleRef: React.RefObject<RNText | null>;
  selected: PackageId;
  onSelect: (id: PackageId) => void;
  present: Record<string, boolean>;
  freeBytes: number;
  /** catalog.fit: one RAM snapshot for every model on the step (perf audit #3). */
  fit: (model: CatalogModel) => MemoryFit | undefined;
  loaded: boolean;
  lang: string;
  travel: PoiRegion | null;
  onTravel: (region: PoiRegion | null) => void;
  trip: { label: string; assets: CatalogModel[] } | null;
  onTrip: (trip: { label: string; assets: CatalogModel[] } | null) => void;
  catalog: ReturnType<typeof useCatalog>;
  choices: Partial<Record<AnswerTier, CatalogModel>>;
  answerTier: AnswerTier;
  packageChosen: boolean;
  onUserPackage: (id: PackageId) => void;
  restored: boolean;
  onBack: () => void;
  onInstall: () => void;
}) {
  const answerModel = (answerTier === "compact" && choices.compact) || choices.default;
  const { t } = useTranslation();
  const tokens = useTokens();
  const offline = !networkAllowed();
  const plans = PACKAGES.map((p) => {
    const tier = TIERS.find((x) => x.id === p.tier)!;
    const all = [...packageAssets(tier, MODEL_CATALOG, answerModel), ...(travel ? placesInstall(travel) : []), ...(trip?.assets ?? [])];
    const plan = planPackage(all.filter((a, i) => all.findIndex((b) => b.id === a.id) === i), present);
    const largestFit = plan.largestLlm ? fit(plan.largestLlm)?.verdict : undefined;
    const shortfall = storageShortfall(plan.downloadBytes, freeBytes);
    const seconds = transferSeconds(plan.downloadBytes, REFERENCE_BYTES_PER_SEC);
    return { ...p, plan, fit: largestFit, shortfall, seconds };
  });
  const chosen = plans.find((p) => p.id === selected)!;
  const recommended = shownRecommendation(plans, loaded);
  // Recommended = pre-selected, once free space is known, until the user picks.
  useEffect(() => {
    if (restored && recommended && !packageChosen && selected !== recommended) onSelect(recommended);
  }, [restored, loaded, packageChosen, recommended, selected, onSelect]);

  return (
    <Screen
      contentStyle={setupRhythm(tokens)}
      edges={["top", "bottom", "left", "right"]}
      footer={
        <>
          <Button
            size="lg"
            // The mockup's CTA carries an arrow (Prism S2F-2).
            icon="arrow-right"
            iconPosition="end"
            label={
              chosen.plan.downloadBytes > 0 && !offline
                ? t("flows.onboarding.install", { size: formatBytes(chosen.plan.downloadBytes, lang) })
                : t("flows.onboarding.continue")
            }
            fullWidth
            disabled={!loaded || chosen.shortfall > 0}
            accessibilityHint={chosen.shortfall > 0 ? t("flows.onboarding.noSpace", { size: formatBytes(chosen.shortfall, lang) }) : undefined}
            onPress={onInstall}
          />
          {chosen.shortfall > 0 && (
            <Text variant="footnote" color="danger" align="center">
              {t("flows.onboarding.noSpace", { size: formatBytes(chosen.shortfall, lang) })}
            </Text>
          )}
          <BackLink label={t("flows.onboarding.back")} onPress={onBack} />
        </>
      }
    >
      <StepHeader titleRef={titleRef} stage={2} title={t("flows.onboarding.knowledgeTitle")} subtitle={t(offline ? "flows.onboarding.step2SubOffline" : "flows.onboarding.step2Sub")} />
      <View accessibilityRole="radiogroup" style={{ gap: tokens.space.sm }}>
        {plans.map((p) => {
          const warning =
            p.shortfall > 0
              ? t("flows.onboarding.noSpace", { size: formatBytes(p.shortfall, lang) })
              : p.fit === "insufficient" || p.fit === "thrashing" || p.fit === "streaming"
                ? t(`flows.row.fit.${p.fit}`)
                : null;
          return (
            <OptionCard
              key={p.id}
              title={t(`flows.onboarding.package.${p.id}.name`)}
              selected={p.id === selected}
              onPress={() => onUserPackage(p.id)}
              badge={p.id === recommended ? <Badge label={t("flows.onboarding.recommended")} tone="accent" emphasis="solid" /> : undefined}
              // The one number that decides: what this choice downloads (or imports) now.
              trailing={p.plan.downloadBytes > 0 ? formatBytes(p.plan.downloadBytes, lang) : undefined}
              description={t(`flows.onboarding.package.${p.id}.body`)}
              meta={[
                p.plan.downloadBytes === 0 && t("flows.onboarding.alreadyDownloaded"),
                // Only when it differs from the figure on the right (import, part already downloaded).
                formatBytes(p.plan.installedBytes, lang) !== formatBytes(p.plan.downloadBytes, lang) &&
                  t("flows.onboarding.meta.onDisk", { size: formatBytes(p.plan.installedBytes, lang) }),
                !offline &&
                  p.seconds != null &&
                  p.plan.downloadBytes > 0 &&
                  t("flows.onboarding.meta.time", { minutes: minutesAbout(p.seconds), speed: formatBytes(REFERENCE_BYTES_PER_SEC, lang) }),
              ]}
            >
              {warning && (
                <Text variant="footnote" color={p.shortfall > 0 || p.fit === "insufficient" ? "danger" : "warning"}>
                  {warning}
                </Text>
              )}
            </OptionCard>
          );
        })}
      </View>
      <TravelCard selected={travel} onChange={onTravel} lang={lang} trip={trip} onTrip={onTrip} catalog={catalog} />
    </Screen>
  );
}

/** Optional offline places for the user's region (P1). Hidden when the build has no region packs. */
function TravelCard({
  selected,
  onChange,
  lang,
  trip,
  onTrip,
  catalog,
}: {
  selected: PoiRegion | null;
  onChange: (r: PoiRegion | null) => void;
  lang: string;
  trip: { label: string; assets: CatalogModel[] } | null;
  onTrip: (trip: { label: string; assets: CatalogModel[] } | null) => void;
  catalog: ReturnType<typeof useCatalog>;
}) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const regions = useMemo(() => poiRegions(), []);
  const [point, setPoint] = useState<{ lat: number; lon: number } | undefined>();
  const [locating, setLocating] = useState(false);
  const [locationNote, setLocationNote] = useState<string | null>(null);
  const [explainOpen, setExplainOpen] = useState(false);
  const [declined, setDeclined] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [manual, setManual] = useState<PoiRegion | null>(null);
  const [tripOpen, setTripOpen] = useState(false);
  const tripRef = useRef<View>(null);
  const announce = useAnnounce();
  const explainAnswer = useRef<((ok: boolean) => void) | null>(null);
  const locateRef = useRef<View>(null);
  const otherRef = useRef<View>(null);

  if (regions.length === 0) return null;
  const auto = suggestRegion(regions, { timeZone: deviceTimeZone(), point });
  const suggestion: { region: PoiRegion; reason: "location" | "timezone" | "manual" } | null = manual
    ? { region: manual, reason: "manual" }
    : auto;

  const explain = () =>
    new Promise<boolean>((resolve) => {
      explainAnswer.current = resolve;
      setExplainOpen(true);
    });
  const answerExplain = (ok: boolean) => {
    setExplainOpen(false);
    explainAnswer.current?.(ok);
    explainAnswer.current = null;
  };

  const useLocation = async () => {
    setLocating(true);
    setLocationNote(null);
    const result = await locateForUser(explain);
    setLocating(false);
    if (result.status === "ok") {
      setPoint({ lat: result.lat, lon: result.lon });
      setManual(null);
      setDeclined(false);
      // A different region now wins: don't keep including the old one silently.
      if (selected) onChange(null);
    } else {
      const note = t(result.status === "declined" ? "flows.places.locationDeclined" : "flows.places.locationUnavailable");
      setLocationNote(note);
      setDeclined(result.status === "declined");
      announce(note);
    }
  };

  const region = suggestion?.region;
  const name = region ? (lang.startsWith("pt") ? region.name.pt : region.name.en) : "";
  const cities = region ? citySummary(region) : null;

  return (
    <Section title={t("flows.places.travelTitle")} footer={t("flows.places.footer")}>
      {region && cities ? (
        <>
          {/* In the rhythm of the mockup's cards (Iris): title 16, one metadata line, the reason in a caption. */}
          <View style={{ paddingVertical: tokens.space.md - tokens.space.xxs, paddingHorizontal: tokens.space.inset, gap: tokens.space.xs }}>
            <View style={{ flexDirection: "row", alignItems: "center", gap: tokens.space.md }}>
              {/* centerOnBox: iOS draws Baloo up to 4 pt high in its box, so the switch would sit low (icon-align). */}
              <IconText variant="cardTitle" centerOnBox style={{ flex: 1 }}>
                {t("flows.places.placesFor", { region: name })}
              </IconText>
              <Switch
                label={t("flows.places.include", { region: name })}
                value={selected?.id === region.id}
                onValueChange={(v) => onChange(v ? region : null)}
              />
            </View>
            <MetaLine
              variant="caption"
              items={[
                t("flows.places.meta", { places: formatCount(region.poiCount, lang), size: formatBytes(region.sizeBytes, lang) }),
                t("flows.places.cityCount", { count: cities.names.length + cities.more, value: formatCount(cities.names.length + cities.more, lang) }),
              ]}
            />
            <Text variant="caption" color="secondary">
              {t(`flows.places.reason.${suggestion!.reason}`)}
            </Text>
          </View>
        </>
      ) : (
        <ListRow title={t("flows.places.noRegionHere")} />
      )}
      {suggestion?.reason !== "location" && (
        // One left edge in the card: 14, like the trip ListRow and the Section title (Prism FL-2).
        <View style={{ paddingHorizontal: tokens.space.inset, paddingVertical: tokens.space.base, gap: tokens.space.sm }}>
          <Button ref={locateRef} size="sm" variant="secondary" icon="map-pin" label={t("flows.places.useLocation")} loading={locating} onPress={useLocation} />
          {locationNote && (
            <Text variant="footnote" color="secondary">
              {locationNote}
            </Text>
          )}
          {declined && <Button size="sm" variant="secondary" label={t("flows.places.openSettings")} onPress={() => Linking.openSettings()} />}
        </View>
      )}
      {regions.length > 1 && (
        <View style={{ paddingHorizontal: tokens.space.inset, paddingBottom: tokens.space.base }}>
          <Button ref={otherRef} size="sm" variant="secondary" icon="map" label={t("flows.places.otherRegion")} onPress={() => setPickerOpen(true)} />
        </View>
      )}
      {trip ? (
        <ListRow
          icon="navigation"
          title={trip.label}
          // The size goes under the name: a row with a switch has no room for a value (the switch says "include").
          subtitle={formatBytes(trip.assets.reduce((n, a) => n + a.sizeBytes, 0), lang)}
          switch={{ value: true, onValueChange: (v) => !v && onTrip(null) }}
        />
      ) : (
        <View style={{ paddingHorizontal: tokens.space.inset, paddingBottom: tokens.space.base, gap: tokens.space.xs }}>
          <Button ref={tripRef} size="sm" variant="secondary" icon="navigation" label={t("flows.travel.goingTo")} onPress={() => setTripOpen(true)} />
          <Text variant="footnote" color="secondary">
            {t("flows.travel.goingToHint")}
          </Text>
        </View>
      )}
      <Sheet visible={tripOpen} onClose={() => setTripOpen(false)} title={t("flows.travel.goingToTitle")} returnFocusRef={tripRef}>
        <CitySearch
          catalog={catalog}
          onChoose={(choice) => {
            onTrip(choice);
            setTripOpen(false);
          }}
        />
      </Sheet>
      <Sheet visible={pickerOpen} onClose={() => setPickerOpen(false)} title={t("flows.places.otherRegion")} returnFocusRef={otherRef}>
        <View accessibilityRole="radiogroup">
          {regions.map((r) => (
            <RadioRow
              key={r.id}
              title={lang.startsWith("pt") ? r.name.pt : r.name.en}
              subtitle={t("flows.places.meta", { places: formatCount(r.poiCount, lang), size: formatBytes(r.sizeBytes, lang) })}
              selected={suggestion?.region.id === r.id}
              onPress={() => {
                // Picking a region means wanting it: include it right away.
                setManual(r);
                onChange(r);
                setPickerOpen(false);
              }}
            />
          ))}
        </View>
      </Sheet>
      <Sheet
        visible={explainOpen}
        returnFocusRef={locateRef}
        onClose={() => answerExplain(false)}
        title={t("flows.places.rationaleTitle")}
        description={t("flows.places.rationaleBody")}
        footer={
          <>
            <Button label={t("flows.places.notNow")} variant="secondary" fullWidth onPress={() => answerExplain(false)} />
            <Button label={t("flows.places.continue")} fullWidth onPress={() => answerExplain(true)} />
          </>
        }
      />
    </Section>
  );
}


/** The one-word status at the right of an install row; the full line is what a screen reader hears. */
function shortStatus(state: RowState, model: CatalogModel, t: ReturnType<typeof useTranslation>["t"]): string {
  switch (state.kind) {
    case "not-installed":
      return t(canDownload(model) ? "flows.onboarding.queued" : "flows.onboarding.toImport");
    case "downloading":
      return state.progress > 0 ? `${Math.round(state.progress * 100)}%` : t("flows.onboarding.queued");
    case "verifying":
      return t("flows.onboarding.checking");
    case "failed":
      return t("flows.onboarding.failedShort");
    case "loading":
      return t("flows.row.loading");
    default:
      return t("flows.onboarding.ready");
  }
}

function InstallStep({
  titleRef,
  assets,
  catalog,
  allPresent,
  lang,
  onBack,
  onIndexingChange,
  onChoosePackage,
  onReady,
  answerModel,
  placesLabel,
}: {
  titleRef: React.RefObject<RNText | null>;
  assets: CatalogModel[];
  catalog: ReturnType<typeof useCatalog>;
  allPresent: boolean;
  lang: string;
  onBack: () => void;
  /** Tells the wizard when the index is being built, so the hardware back button can stay put. */
  onIndexingChange?: (indexing: boolean) => void;
  onChoosePackage: () => void;
  onReady: () => void;
  /** For the closing summary: what will answer, and the places chosen (region or trip). */
  answerModel?: CatalogModel;
  placesLabel?: string;
}) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const subheadLine = useOpticalLine("subhead");
  const headlineLine = useOpticalLine("headline");
  const announce = useAnnounce();
  const [indexPhase, setIndexPhase] = useState<IndexPhase>("waiting");
  const [indexError, setIndexError] = useState<string | null>(null);
  const [seed, setSeed] = useState<SeedProgress | null>(null);
  const seedStart = useRef<{ at: number; done: number } | null>(null);
  const [now, setNow] = useState(Date.now());
  const offline = !networkAllowed();
  // Offline build, or items with no published URL yet (places packs): those are imported.
  const needsImport = offline || assets.some((a) => !canDownload(a) && !catalog.statuses[a.id]?.present);

  // A file verified in the running pick is in, before the catalog refreshes at the end of the pick (Prism L3-3).
  const states = assets.map((a) => ({ asset: a, state: withVerifiedImport(catalog.view(a).state, a.id, catalog.imports) }));
  const downloading = states.some((s) => s.state.kind === "downloading" || s.state.kind === "verifying");
  const failed = states.filter((s) => s.state.kind === "failed");
  const noSpaceFailure = failed.some((f) => f.state.kind === "failed" && f.state.errorKind === "storage");

  // A new failure is announced right away and focus moves to the retry button (Prism F5).
  const retryRef = useRef<View>(null);
  const failedKey = failed.map((f) => f.asset.id).join(",");
  const lastFailedKey = useRef("");
  useEffect(() => {
    if (failedKey && failedKey !== lastFailedKey.current) {
      const first = failed[0];
      const reason = first.state.kind === "failed" ? failureLines(first.state, t, lang).cause : "";
      announce(`${t("flows.onboarding.downloadFailed")}. ${catalogLabel(first.asset, t)}: ${reason}`, { assertive: true });
      setTimeout(() => {
        const node = retryRef.current && findNodeHandle(retryRef.current);
        if (node) AccessibilityInfo.setAccessibilityFocus(node);
      }, 300);
    }
    lastFailedKey.current = failedKey;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [failedKey]);

  // Announce the switch to verification once per asset.
  const verifyingKey = states.filter((s) => s.state.kind === "verifying").map((s) => s.asset.id).join(",");
  useEffect(() => {
    if (verifyingKey) announce(t("flows.row.verifying"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [verifyingKey]);

  // Aggregate progress, and when it last moved, for the stall hint.
  const totalBytes = assets.reduce((sum, a) => sum + a.sizeBytes, 0);
  const doneBytes = states.reduce((sum, { asset, state }) => {
    if (state.kind === "downloading") return sum + asset.sizeBytes * state.progress;
    if (state.kind === "installed" || state.kind === "in-use") return sum + asset.sizeBytes;
    return sum;
  }, 0);
  const lastMove = useRef({ bytes: doneBytes, at: Date.now() });
  if (doneBytes !== lastMove.current.bytes) lastMove.current = { bytes: doneBytes, at: Date.now() };
  useEffect(() => {
    if (!downloading) return;
    const id = setInterval(() => setNow(Date.now()), 5000);
    return () => clearInterval(id);
  }, [downloading]);
  const stalled = downloading && now - lastMove.current.at > STALL_MS;
  // The file being checked right now (offline import): the hero shows it live (Prism/Iris S3C-1).
  const activeImport = catalog.imports.find((f) => f.status === "importing");
  const transferring = downloading || !!activeImport;
  // Measured download speed since this screen started receiving bytes: the time left is shown only once measured.
  const rateStart = useRef<{ bytes: number; at: number } | null>(null);
  if (downloading && !rateStart.current && doneBytes > 0) rateStart.current = { bytes: doneBytes, at: Date.now() };
  const etaS = (() => {
    const r = rateStart.current;
    if (!r || !downloading) return undefined;
    const elapsedS = (now - r.at) / 1000;
    const bps = (doneBytes - r.bytes) / Math.max(elapsedS, 1);
    return elapsedS >= 5 && bps > 0 ? (totalBytes - doneBytes) / bps : undefined;
  })();

  // Announce every quarter of the download, never per tick (Prism F4).
  const quarter = totalBytes > 0 ? Math.floor((doneBytes / totalBytes) * 4) : 0;
  const lastQuarter = useRef(quarter);
  useEffect(() => {
    if (quarter > lastQuarter.current && quarter < 4) announce(t(offline ? "flows.onboarding.percentImportedAnnounce" : "flows.onboarding.percentAnnounce", { pct: quarter * 25 }));
    lastQuarter.current = quarter;
  }, [quarter, announce, t]);

  // Coming back to the app: retry only failures that retrying can fix.
  const retryable = useRef<CatalogModel[]>([]);
  retryable.current = states.filter((s) => canAutoRetry(s.state)).map((s) => s.asset);
  useEffect(() => {
    const sub = AppState.addEventListener("change", (next) => {
      if (next === "active") for (const a of retryable.current) catalog.download(a);
    });
    return () => sub.remove();
    // catalog.download is stable (useCallback on refresh).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(
    () =>
      onSeedProgress((p) => {
        seedStart.current ??= { at: Date.now(), done: p.done };
        setSeed(p);
      }),
    []
  );

  const buildIndex = useCallback(async () => {
    setIndexPhase("building");
    setIndexError(null);
    try {
      const emb = MODEL_CATALOG.find((m) => m.kind === "embedding" && m.required)!;
      await embeddingEngine.load(emb.filename);
      await seedKnowledgeBaseIfEmpty();
      setIndexPhase("ready");
      notification(NotificationFeedbackType.Success);
      announce(t("flows.onboarding.doneAnnounce"));
    } catch (e: any) {
      setIndexError(e?.message ?? String(e));
      setIndexPhase("error");
      announce(t("flows.onboarding.indexFailed"), { assertive: true });
    }
  }, [announce, t]);

  useEffect(() => {
    if (allPresent && indexPhase === "waiting") buildIndex();
  }, [allPresent, indexPhase, buildIndex]);

  const seedEta = (() => {
    const s = seedStart.current;
    if (!seed || !s) return undefined;
    const elapsed = (Date.now() - s.at) / 1000;
    const indexed = seed.done - s.done;
    if (elapsed < 3 || indexed < 5) return undefined;
    return ((seed.total - seed.done) * elapsed) / indexed;
  })();

  const ready = indexPhase === "ready";
  const indexing = indexPhase === "building" || indexPhase === "error";
  useEffect(() => {
    onIndexingChange?.(indexPhase === "building");
  }, [indexPhase, onIndexingChange]);
  const { fontScale } = useWindowDimensions();
  const indexCounter = seed ? t("flows.onboarding.indexCounter", { done: formatCount(seed.done, lang), total: formatCount(seed.total, lang) }) : "";
  // The item whose bytes are arriving now, "Item 2 of 5 — name" under the bar (the mockup's "Model 1 of 3 — …").
  const currentIdx = states.findIndex((x) => moving(x.state));
  const current = currentIdx >= 0 ? { n: currentIdx + 1, label: catalogLabel(states[currentIdx].asset, t) } : undefined;
  const presentCount = states.filter((s) => s.state.kind === "installed" || s.state.kind === "in-use").length;
  // The offline build imports: its hero counts files and only appears once one is in; before that the list says it all (Iris, Prism N-5/N-6).
  const hero = !allPresent
    ? offline && activeImport
      ? {
          label: t("flows.onboarding.importingLabel"),
          // The whole setup, not this file: the bar fell from 100% to 0% at every file (Prism L3-4).
          fraction: importHeroFraction(doneBytes, totalBytes, activeImport),
          figure: undefined,
          meta: activeImport.sizeBytes
            ? [t("flows.onboarding.totalValue", { done: formatBytes(activeImport.sizeBytes * activeImport.progress, lang), total: formatBytes(activeImport.sizeBytes, lang) })]
            : [],
        }
      : offline
      ? {
          label: t("flows.onboarding.importedLabel"),
          fraction: assets.length > 0 ? presentCount / assets.length : 0,
          figure: { value: formatCount(presentCount, lang), unit: t("flows.onboarding.ofFiles", { count: assets.length, total: formatCount(assets.length, lang) }) },
          meta: [t("flows.onboarding.totalValue", { done: formatBytes(doneBytes, lang), total: formatBytes(totalBytes, lang) })],
        }
      : {
          label: t("flows.onboarding.totalLabel"),
          fraction: totalBytes > 0 ? doneBytes / totalBytes : 0,
          figure: undefined,
          meta: [t("flows.onboarding.totalValue", { done: formatBytes(doneBytes, lang), total: formatBytes(totalBytes, lang) })],
        }
    : {
        figure: undefined,
        label: t("flows.onboarding.indexRow"),
        fraction: seed && seed.total > 0 ? seed.done / seed.total : 0,
        meta: [indexCounter, seedEta != null ? t("flows.onboarding.minutesLeft", { count: minutesLeft(seedEta) }) : null].filter((x): x is string => !!x),
      };
  // One row per category, like the mockup (Iris, Prism): aggregated honestly, files one tap away.
  // The file being copied belongs to an item only once verified; its likely item (same size, or the same
  // name without case/punctuation) lets that category read "Importing" in ember meanwhile (the mockup's STREAMING row).
  // Only items not in yet: a file verified earlier in the pick is no longer a candidate (L3-3).
  const copyTarget = activeImport
    ? likelyTarget(activeImport, states.filter((s) => s.state.kind === "not-installed" || s.state.kind === "failed").map((s) => s.asset))
    : undefined;
  const importingItem = (asset: CatalogModel) => !!copyTarget && copyTarget.id === asset.id;
  const categories = installCategories(
    states.map(({ asset, state }) => ({
      id: asset.id,
      kind: asset.kind,
      sizeBytes: asset.sizeBytes,
      state: importingItem(asset) ? ({ kind: "downloading", phase: "copying", progress: activeImport!.progress } as const) : state,
      importOnly: !canDownload(asset),
    }))
  );
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  // Verified files that are not part of this setup (an optional pack picked with the others) are listed
  // in the list card, never as a loose line under it (Prism A3-2).
  const extras = catalog.imports
    .filter((f) => f.status === "verified" && f.assetId && !assets.some((a) => a.id === f.assetId))
    .map((f) => { const a = findAsset(f.assetId!); return a ? catalogLabel(a, t) : f.name; });
  // Install -> done happens under the user's eyes: the done screen fades in (`enter`). When the step
  // opens already done (restored), the step's own crossfade is enough.
  const motion = useMotion();
  const openedReady = useRef(ready).current;
  // Import/download -> index is a stage change inside this step (Stepper 3 -> 4): the same crossfade as a
  // step change, not a cut (Prism L3-2). A step that opens with everything in starts at the index: no swap.
  const openedAllPresent = useRef(allPresent).current;
  const stage = installStage(indexPhase);
  const stageSwap = useMemo(() => (stageSwapAnimates(openedAllPresent) ? motion.crossfade("forward") : undefined), [openedAllPresent, motion]);
  // The last screen before the chat: centred, one figure-free summary of what is now on the phone (Prism N-13).
  if (ready) {
    const collections = assets.filter((a) => a.kind === "corpus" && !a.id.startsWith("poi-")).length;
    const summary = [
      answerModel && { key: "doneAnswer", value: catalogLabel(answerModel, t) },
      collections > 0 && { key: "doneKnowledge", value: t("flows.onboarding.doneCollections", { count: collections, value: formatCount(collections, lang) }) },
      placesLabel && { key: "donePlaces", value: placesLabel },
      seed && seed.total > 0 && { key: "doneIndex", value: t("flows.onboarding.doneArticles", { count: seed.total, value: formatCount(seed.total, lang) }) },
    ].filter((r): r is { key: string; value: string } => !!r);
    return (
      <Animated.View style={{ flex: 1 }} entering={openedReady ? undefined : motion.entering()}>
        {/* ambient: the same ember light as the Welcome, so both ends of setup rhyme (Iris). */}
        <Screen center ambient edges={["top", "bottom", "left", "right"]} footer={<Button size="lg" label={t("flows.onboarding.open")} fullWidth onPress={onReady} />}>
          <View style={{ alignItems: "center", gap: tokens.space.md }}>
            <Mascot size="hero" glow />
            <Text ref={titleRef} variant="title1" align="center" header>
              {t("flows.onboarding.doneTitle")}
            </Text>
            <Text variant="footnote" color="secondary" align="center">
              {t("flows.onboarding.doneBody")}
            </Text>
          </View>
          {summary.length > 0 && (
            <Card>
              <Text variant="label" color="secondary">
                {t("flows.onboarding.doneSummary")}
              </Text>
              {summary.map((r, i) => (
                <View
                  key={r.key}
                  accessible
                  style={{
                    flexDirection: "row",
                    alignItems: "flex-start",
                    justifyContent: "space-between",
                    gap: tokens.space.md,
                    paddingTop: tokens.space.md,
                    paddingBottom: i < summary.length - 1 ? tokens.space.md : 0,
                    borderBottomWidth: i < summary.length - 1 ? tokens.size.hairline : 0,
                    borderBottomColor: tokens.color.line.hairline,
                  }}
                >
                  <Text variant="footnote" color="secondary">
                    {t(`flows.onboarding.${r.key}`)}
                  </Text>
                  <Text variant="footnote" align="right" style={{ flex: 1 }}>
                    {r.value}
                  </Text>
                </View>
              ))}
            </Card>
          )}
        </Screen>
      </Animated.View>
    );
  }

  return (
    <Animated.View key={stage} style={{ flex: 1 }} entering={stageSwap?.entering} exiting={stageSwap?.exiting}>
      <Screen
        contentStyle={setupRhythm(tokens)}
        edges={["top", "bottom", "left", "right"]}
        footer={
          <>
            {offline && !allPresent && !activeImport ? (
              // Offline, the step's action is choosing the files: it takes the mockup's CTA place (primary, the one accent).
              <Button
                size="lg"
                icon="file-plus"
                label={t("flows.import.pick")}
                fullWidth
                onPress={catalog.importFiles}
              />
            ) : (
              // The mockup's CTA: large, disabled until everything is on the phone, and it says why (Iris §3, Prism).
              <Button size="lg" label={t("flows.onboarding.open")} fullWidth disabled accessibilityHint={t("flows.onboarding.openWhenReady")} onPress={onReady} />
            )}
            {/* No Back while bytes move (download or import), as in the mockup; it returns when nothing is
                transferring (nothing imported yet, or a failure), and the CTA moves up with it (Iris). */}
            {!transferring && indexPhase !== "building" && <BackLink label={t("flows.onboarding.back")} onPress={onBack} />}
          </>
        }
      >
        <StepHeader
          titleRef={titleRef}
          stage={indexPhase === "waiting" ? 3 : 4}
          // Once the files are in, the header describes the offline indexing, not the download (Harbor, iOS shot 04).
          title={t(`flows.onboarding.${ready ? "doneTitle" : indexing ? "indexTitle" : offline ? "importTitle" : "step3Title"}`)}
          subtitle={t(`flows.onboarding.${ready ? "doneBody" : indexing ? "indexSub" : offline ? "importSub" : "step3Sub"}`)}
        />


        {/* One hero: the download while files arrive, then the search index (the mockup's big figure). */}
        {((!allPresent && (!offline || presentCount > 0 || !!activeImport)) || (indexing && seed)) && (
          // The hero boar's glow is wider than the boar: the card clips it, as in the mockup (Prism N-11).
          // The mockup's hero (FIDELITY): radius 22, gap 10, the boar at right -6 / top -4 with its ember glow;
          // the bar runs full width under its feet. The glow is clipped by the card (Prism N-11).
          <Card style={{ gap: tokens.space.md - tokens.space.xxs, borderRadius: tokens.radius.hero }}>
            {fontScale > LARGE_TEXT ? (
              // The brand disc at large text is a framed avatar: it keeps the card's padding (Prism H-1).
              <View style={{ position: "absolute", top: tokens.space.base, right: tokens.space.base }}>
                <Mascot size="brand" />
              </View>
            ) : (
              // The mockup's boar overflows the card (top -4, right -6), unclipped (Iris §3).
              <View style={{ position: "absolute", top: -tokens.space.xs, right: -(tokens.space.xs + tokens.space.xxs) }}>
                <Mascot size="md" />
              </View>
            )}
            <View style={{ paddingRight: fontScale > LARGE_TEXT ? tokens.size.mascotSm + tokens.space.sm : tokens.size.mascotMd - tokens.space.xl }}>
              {/* xl only for the download, the one figure of the setup; the index and the file count stay lg (Iris). */}
              {hero.figure ? (
                <Stat size="lg" label={hero.label} value={hero.figure.value} unit={hero.figure.unit} />
              ) : (
                // "62%" is one run in the mockup; Stat draws unit="%" in the number's body (Iris 23a271b, Prism S3C-1).
                <Stat size={allPresent ? "lg" : "xl"} label={hero.label} value={String(Math.floor(hero.fraction * 100))} unit="%" />
              )}
            </View>
            <Progress label={hero.label} value={hero.fraction} valueText={hero.meta.join(", ")} height={tokens.space.sm + tokens.space.xxs} />
            {allPresent && indexPhase === "building" && seed?.title ? (
              <Text variant="footnote" numberOfLines={1} ellipsizeMode="tail">
                {t("flows.onboarding.indexReading", { title: seed.title })}
              </Text>
            ) : null}
            {(transferring || indexPhase === "building") && <LoadingLine />}
            {!allPresent && offline && activeImport && (
              // Large text gets a second line before the middle ellipsis; the reader always hears the whole name (Prism FL-33).
              <Text
                variant="footnote"
                numberOfLines={fontScale >= LARGE_TEXT_SCALE ? 2 : 1}
                ellipsizeMode="middle"
                accessibilityLabel={t("flows.onboarding.fileOf", { n: Math.min(presentCount + 1, assets.length), total: assets.length, name: activeImport.name })}
              >
                {t("flows.onboarding.fileOf", { n: Math.min(presentCount + 1, assets.length), total: assets.length, name: activeImport.name })}
              </Text>
            )}
            {!allPresent && !offline && current && (
              <Text variant="footnote" numberOfLines={2}>
                {t("flows.onboarding.currentItem", { n: current.n, total: states.length, name: current.label })}
              </Text>
            )}
            <View style={{ flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: tokens.space.md }}>
              <MetaLine items={hero.meta} />
              {!allPresent && etaS != null && <MetaLine items={[t("flows.onboarding.minutesLeft", { count: minutesLeft(etaS) })]} />}
              {offline && activeImport && (
                // The copy's Cancel sits in the mockup's ETA slot, next to the progress it stops; neutral, not ember,
                // so it doesn't compete with the bar; 44 pt touch from hitSlop (Iris, Prism).
                <TextAction
                  label={t("common.cancel")}
                  accessibilityLabel={t("flows.import.cancelA11y", { name: activeImport.name })}
                  onPress={catalog.cancelImports}
                />
              )}
            </View>
          </Card>
        )}

        {/* The mockup's list card: 4/14 padding, rows 9 pt tall padding, status in small caps (FIDELITY). */}
        <Card padding="none" style={{ paddingHorizontal: tokens.space.md + tokens.space.xxs, paddingVertical: tokens.space.xs }}>
          {categories.map((c, i) => (
            <CategoryRow
              key={c.category}
              row={c}
              first={i === 0}
              assets={assets}
              expanded={!!expanded[c.category]}
              onToggle={() => {
                motion.animateNextLayout();
                setExpanded((e) => ({ ...e, [c.category]: !e[c.category] }));
              }}
              onRetry={(asset) => catalog.download(asset)}
              lang={lang}
              importing={offline}
            />
          ))}
          {extras.length > 0 && (
            <View
              accessible
              accessibilityLabel={`${t("flows.onboarding.category.extras")}: ${extras.join(", ")}`}
              style={{ flexDirection: "row", alignItems: "flex-start", gap: iconTokens.gap, paddingVertical: tokens.space.sm, borderTopWidth: tokens.size.hairline, borderTopColor: tokens.color.line.row }}
            >
              <IconSlot name="plus-circle" line={subheadLine} color={tokens.color.status.success.solid} />
              <View style={{ flex: 1 }}>
                <Text variant="subhead">{t("flows.onboarding.category.extras")}</Text>
                <Text variant="caption" color="secondary" numberOfLines={fontScale >= LARGE_TEXT_SCALE ? undefined : 2}>
                  {extras.join(", ")}
                </Text>
              </View>
              <View style={{ height: subheadLine.lineHeight, justifyContent: "center" }}>
                <Text variant="label" color="secondary">
                  {t("flows.onboarding.categoryStatus.ready")}
                </Text>
              </View>
            </View>
          )}
          {[
            {
              key: "index",
              icon: (
                <IconSlot
                  name={ready ? "check-circle" : indexPhase === "error" ? "alert-octagon" : "clock"}
                  line={subheadLine}
                  color={ready ? tokens.color.status.success.solid : indexPhase === "error" ? tokens.color.status.danger.solid : tokens.color.text.secondary}
                />
              ),
              title: t("flows.onboarding.indexRow"),
              status:
                indexPhase === "building" && seed
                  ? indexCounter
                  : indexPhase === "waiting"
                    ? t("flows.onboarding.waitingDownloads")
                    : indexPhase === "building"
                      ? t("flows.onboarding.indexStarting")
                      : t("flows.onboarding.indexFailed"),
              spoken: undefined,
              // The hero carries the index progress in accent; the row stays secondary (R-IDX-2).
              tone: (indexPhase === "error" ? "danger" : "secondary") as TextColor,
            },
          ]
            // While the hero shows the index, its row would repeat it (Prism N-10).
            .filter((row) => !(row.key === "index" && indexing && seed))
            .map((row, i, rows) => (
            <React.Fragment key={row.key}>
            <View
              accessible
              accessibilityLabel={`${row.title}, ${row.spoken ?? row.status}`}
              style={{
                gap: tokens.space.xs,
                paddingVertical: tokens.space.sm,
                borderTopWidth: categories.length > 0 || i > 0 ? tokens.size.hairline : 0,
                borderTopColor: tokens.color.line.row,
              }}
            >
              <View style={{ flexDirection: "row", gap: iconTokens.gap, alignItems: "flex-start" }}>
                {row.icon}
                <Text variant="subhead" style={{ flex: 1 }} numberOfLines={2}>
                  {row.title}
                </Text>
                {row.key !== "index" && (
                  // Small caps like the mockup's STREAMING / QUEUED / PENDING.
                  <Text variant="label" color={row.tone} numeric>
                    {row.status}
                  </Text>
                )}
              </View>
              {row.key === "index" && (
                <Text variant="footnote" color={row.tone} numeric>
                  {row.status}
                </Text>
              )}
            </View>
            </React.Fragment>
          ))}
        </Card>


        {/* Mockup order: hero, list, then this; with one row per category it stays on the first screen (Iris). */}
        {(transferring || indexPhase === "building") && (
          // The mockup's warning card: warm wash, radius 18, 12/14 padding, body in the primary ink (FIDELITY).
          <Card level={0} radius="card" padding="compact" style={{ gap: tokens.space.xs, backgroundColor: tokens.color.status.warning.soft }}>
            <IconText icon="info" variant="label" color="warning" iconColor={tokens.color.status.warning.solid}>
              {t(indexPhase === "building" && !transferring ? "flows.onboarding.canLeaveTitle" : "flows.onboarding.keepOpenTitle")}
            </IconText>
            <Text variant="footnote">
              {/* Both resume: downloads from the partial file (ModelManager), indexing by skipping the
                  articles already in (seedCorpus). Leaving only pauses them. */}
              {t(indexPhase === "building" && !transferring ? "flows.onboarding.keepOpenIndex" : offline ? "flows.onboarding.keepOpenImport" : "flows.onboarding.keepOpen")}
            </Text>
          </Card>
        )}

        {/* The files being imported come after "Keep BOAR open", so that card stays on the first screen (Harbor, 02e72b5). */}
        {needsImport && !allPresent && (
          <View style={{ gap: tokens.space.sm }}>
            {!offline && (
              <Text variant="footnote" color="secondary">
                {t("flows.onboarding.importPlacesNote")}
              </Text>
            )}
            {/* Offline, choosing files is the footer's CTA (the mockup's place for the step's action); this lists them. */}
            <ImportList imports={catalog.imports} onPick={catalog.importFiles} onCancel={catalog.cancelImports} hidePick={offline} hideActive={offline} hideVerified />
            {!activeImport && (
              // Where the files come from, with an address someone can type on a computer (Prism IM-5).
              <View style={{ gap: tokens.space.xs }}>
                <Text variant="footnote" color="secondary">
                  {t("flows.onboarding.importHow")}
                </Text>
                <Text variant="footnote" selectable>
                  {OFFLINE_INSTALL_URL}
                </Text>
                <View style={{ alignSelf: "flex-start" }}>
                  <TextAction
                    label={t("flows.onboarding.copyLink")}
                    leadingIcon="copy"
                    onPress={async () => {
                      await Clipboard.setStringAsync(`https://${OFFLINE_INSTALL_URL}`);
                      announce(t("flows.onboarding.linkCopied"));
                    }}
                  />
                </View>
              </View>
            )}
          </View>
        )}

        {failed.length > 0 && (
          <View
            style={{
              gap: tokens.space.sm,
              padding: tokens.space.base,
              borderRadius: tokens.radius.md,
              backgroundColor: tokens.color.status.danger.soft,
            }}
          >
            <View style={{ flexDirection: "row", gap: iconTokens.gap, alignItems: "flex-start" }}>
              <IconSlot name="alert-octagon" line={headlineLine} color={tokens.color.status.danger.solid} />
              <Text variant="headline" color="danger" header style={{ flexShrink: 1 }}>
                {t("flows.onboarding.downloadFailed")}
              </Text>
            </View>
            {failed.map((f) => {
              if (f.state.kind !== "failed") return null;
              const lines = failureLines(f.state, t, lang);
              return (
                <View key={f.asset.id} style={{ gap: tokens.space.xxs }}>
                  <Text variant="callout">
                    {catalogLabel(f.asset, t)}: {lines.cause}
                  </Text>
                  <Text variant="caption" color="secondary" selectable>
                    {lines.detail}
                  </Text>
                </View>
              );
            })}
            <Button ref={retryRef} label={t("flows.row.retry")} icon="refresh-cw" onPress={() => failed.forEach((f) => catalog.download(f.asset))} />
            {noSpaceFailure && <Button label={t("flows.onboarding.smallerPackage")} variant="secondary" onPress={onChoosePackage} />}
          </View>
        )}

        {indexPhase === "error" && (
          <EmptyState tone="error" title={t("flows.onboarding.indexFailed")} body={indexError ? t(userErrorKey(indexError)) : undefined} detail={indexError ?? undefined} actionLabel={t("flows.row.retry")} onAction={buildIndex} />
        )}

        {stalled && !offline && (
          <View style={{ gap: tokens.space.xs }}>
          <Text variant="footnote" color="secondary">
            {t("flows.onboarding.restartHint")}
          </Text>
          <Button
            variant="secondary"
            icon="refresh-cw"
            label={t("flows.onboarding.restart")}
            onPress={() => {
              for (const { asset, state } of states) if (state.kind !== "installed" && state.kind !== "in-use") restartDownload(asset).finally(() => catalog.refresh());
            }}
          />
          </View>
        )}

      </Screen>
    </Animated.View>
  );
}

/** Bytes are arriving or being checked. A download that has not started yet reads as waiting: one accent per screen (Prism S3-2). */
function moving(state: RowState): boolean {
  return (state.kind === "downloading" && state.progress > 0) || state.kind === "verifying";
}

/** The mockup's back link: 13.5 text in mu with an arrow, not an accent button; touch >= 44 (Prism). */
function BackLink({ label, onPress }: { label: string; onPress: () => void }) {
  const tokens = useTokens();
  return (
    // 10 pt below the CTA like the mockup (the footer's gap is 8).
    <View style={{ alignSelf: "center", marginTop: tokens.space.xxs }}>
      <TextAction label={label} leadingIcon="arrow-left" onPress={onPress} />
    </View>
  );
}

const CATEGORY_ICON: Record<InstallCategory, IconName> = { answer: "cpu", search: "database", knowledge: "book-open", places: "map-pin" };

/**
 * One install category (the mockup's "Reasoning Models ... STREAMING"). Reads as one sentence; a tap shows
 * each file with its status and, for a failed one, Try again (Prism: honest status, files reachable).
 */
function CategoryRow({
  row,
  first,
  assets,
  expanded,
  onToggle,
  onRetry,
  lang,
  importing,
}: {
  row: ReturnType<typeof installCategories>[number];
  first: boolean;
  assets: CatalogModel[];
  expanded: boolean;
  onToggle: () => void;
  onRetry: (asset: CatalogModel) => void;
  lang: string;
  /** Offline build: bytes arrive by import, so a moving row reads "Importing". */
  importing?: boolean;
}) {
  const { t } = useTranslation();
  const tokens = useTokens();
  const line = useOpticalLine("subhead");
  const stacked = useWindowDimensions().fontScale >= LARGE_TEXT_SCALE;
  const name = t(`flows.onboarding.category.${row.category}`);
  const failedItem = row.items.find((i) => i.state.kind === "failed");
  const reason = failedItem && failedItem.state.kind === "failed" ? failureLines(failedItem.state, t, lang).cause : undefined;
  const status = t(`flows.onboarding.categoryStatus.${row.status === "moving" && importing ? "importing" : row.status}`);
  const pct = Math.floor(row.fraction * 100);
  const icon: IconName = row.status === "failed" ? "alert-octagon" : row.status === "moving" ? "download" : row.status === "ready" ? "check-circle" : CATEGORY_ICON[row.category];
  const iconColor =
    row.status === "failed"
      ? tokens.color.status.danger.solid
      : row.status === "moving"
        ? tokens.color.accent.text
        : row.status === "ready"
          ? tokens.color.status.success.solid
          : tokens.color.text.secondary;
  const tone: TextColor = row.status === "failed" ? "danger" : row.status === "moving" ? "accent" : "secondary";
  return (
    <View style={{ borderTopWidth: first ? 0 : tokens.size.hairline, borderTopColor: tokens.color.line.row }}>
      <Pressable
        accessibilityRole="button"
        accessibilityState={{ expanded }}
        accessibilityHint={t("flows.onboarding.categoryHint")}
        accessibilityLabel={[
          t("flows.onboarding.categoryA11y", { name, done: row.done, total: row.total, pct }),
          row.status === "failed" ? `${status}: ${reason ?? ""}` : status,
        ].join(", ")}
        onPress={onToggle}
        style={{ flexDirection: "row", alignItems: "flex-start", gap: iconTokens.gap, paddingVertical: tokens.space.sm, minHeight: tokens.size.touch }}
      >
        <IconSlot name={icon} line={line} color={iconColor} />
        <View style={{ flex: 1 }}>
          <Text variant="subhead">{name}</Text>
          {/* Large text: the status goes under the name, which keeps the width ('Conhecime/nto' at 2.0 PT, Prism NA-1). */}
          {stacked && (
            <Text variant="label" color={tone}>
              {status}
            </Text>
          )}
          {reason && (
            <Text variant="caption" color="danger">
              {reason}
            </Text>
          )}
        </View>
        {!stacked && (
          <View style={{ height: line.lineHeight, justifyContent: "center" }}>
            <Text variant="label" color={tone}>
              {status}
            </Text>
          </View>
        )}
        {/* A disclosure shows it opens: the chevron of ListRow's expanded rows, on line 1 at the edge (Prism FL-15). */}
        <IconSlot name={expanded ? "chevron-up" : "chevron-down"} line={line} color={tokens.color.text.secondary} edge="end" />
      </Pressable>
      {expanded &&
        row.items.map((it) => {
          const asset = assets.find((a) => a.id === it.id)!;
          return (
            <View
              key={it.id}
              // Items start at the category name's x: icon plus gap, at any text size (icon-align rule 4).
              style={{ flexDirection: "row", alignItems: "center", gap: tokens.space.sm, paddingLeft: line.iconSize + iconTokens.gap, paddingBottom: tokens.space.sm }}
            >
              <Text variant="footnote" color="secondary" style={{ flex: 1 }} numberOfLines={2}>
                {catalogLabel(asset, t)}
              </Text>
              {it.state.kind === "failed" ? (
                <Button size="sm" variant="secondary" label={t("flows.row.retry")} accessibilityLabel={`${t("flows.row.retry")}: ${catalogLabel(asset, t)}`} onPress={() => onRetry(asset)} />
              ) : (
                <Text variant="caption" color={moving(it.state) ? "accent" : "secondary"} numeric>
                  {shortStatus(it.state, asset, t)}
                </Text>
              )}
            </View>
          );
        })}
    </View>
  );
}

