import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useTranslation } from "react-i18next";
import { Badge, Button, Card, EmptyState, ListRow, MetaLine, OptionCard, Screen, Section, SegmentedControl, Skeleton, Stat, Text, TextField, useToast } from "./components";
import { getDeviceTotalRamBytes } from "ram-monitor";
import { availableRamFrom } from "../inference/memoryFit";
import { passesSizeFilter, type SizeFilter } from "./flows/sizeFilter";
import { useTokens } from "./theme";
import { useMotion } from "./theme/motion";
import { screenRhythm } from "./flows/rhythm";
import { catalogLabel } from "./flows/catalogLabel";
import { ScreenTitle } from "./flows/ScreenTitle";
import { CatalogModel, MODEL_CATALOG } from "../models/manifest";
import { addDiscoveredModel } from "../models/discoveredModels";
import { HFGgufFile, HFModelSummary, listGgufFiles, searchModels, toCatalogModel } from "../services/modelBrowser";
import { CatalogRow } from "./flows/CatalogRow";
import { CatalogList } from "./flows/CatalogList";
import { DEEP_AUTO_MIN_TOK_PER_SEC as MIN_DEEP_TOK_PER_SEC, deepAutoEligible, MIN_SPEED_SAMPLES, ModelSpeed, modelSpeedStats, resolveDeepModel } from "../routing/depth";
import { AnswerSettings, getAnswerSettings, setAnswerSettings } from "../models/settings";
import { listRecentExecutions } from "../services/executionTelemetry";
import { ImportList } from "./flows/ImportList";
import { networkAllowed } from "../config/variant";
import { useCatalog } from "./flows/useCatalog";
import { formatBytes, formatBytesParts, formatCount, formatRate } from "./flows/format";
import { tokPerSecBand } from "./flows/perfBands";
import type { RootStackParamList } from "./navigation/types";
import { userErrorKey } from "./flows/userError";

type Nav = NativeStackNavigationProp<RootStackParamList>;


export function ModelsScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const navigation = useNavigation<Nav>();
  const toast = useToast();
  const catalog = useCatalog();
  const { refresh } = catalog;
  const offline = !networkAllowed();

  const [answer, setAnswer] = useState<AnswerSettings | null>(null);
  const [speeds, setSpeeds] = useState<Record<string, ModelSpeed>>({});
  const [showLarger, setShowLarger] = useState(false);
  useFocusEffect(
    useCallback(() => {
      refresh();
      getAnswerSettings().then(setAnswer);
      listRecentExecutions(200)
        .then((records) => setSpeeds(Object.fromEntries(modelSpeedStats(records))))
        .catch(() => setSpeeds({}));
    }, [refresh])
  );
  const chooseDeep = async (id: string | null | undefined) => {
    setAnswer((prev) => (prev ? { ...prev, deepModelId: id } : prev));
    await setAnswerSettings({ deepModelId: id });
  };
  if (!catalog.loaded) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("flows.settings.models")}</ScreenTitle>
        <View accessible accessibilityLabel={t("flows.common.loading")} style={{ gap: tokens.space.md }}>
          <Skeleton height={tokens.space.lg} width="60%" />
          <Skeleton height={tokens.size.control * 2} />
          <Skeleton height={tokens.size.control * 2} />
        </View>
      </Screen>
    );
  }

  const models: CatalogModel[] = [
    ...MODEL_CATALOG.filter((m) => m.kind === "llm" || m.kind === "embedding"),
    ...catalog.discovered.filter((d) => !MODEL_CATALOG.some((c) => c.filename === d.filename)),
  ];
  // Two jobs, two sections: choose among the models on the phone, then get more (search, import,
  // download). Every installed model is listed with the others, found on Hugging Face or not.
  const groups = { yours: [] as CatalogModel[], available: [] as CatalogModel[], larger: [] as CatalogModel[] };
  for (const m of models) {
    const kind = catalog.view(m).state.kind;
    const installed = kind === "in-use" || kind === "installed" || kind === "loading" || (kind === "failed" && !!catalog.statuses[m.id]?.present);
    if (installed) groups.yours.push(m);
    // Can't open on this phone: out of the list, in a folded section that says why (CR-1).
    else if (catalog.view(m).wontFit) groups.larger.push(m);
    else groups.available.push(m);
  }
  // The model in use first.
  groups.yours.sort((x, y) => Number(catalog.view(y).state.kind === "in-use") - Number(catalog.view(x).state.kind === "in-use"));

  /** The model's real name ("Qwen2.5-1.5B-Instruct"); its role and license go under it. */
  const nameOf = (m: CatalogModel) => catalogLabel(m, t, { technical: true });
  const roleAndLicense = (m: CatalogModel) => {
    const role = catalogLabel(m, t);
    return [role !== nameOf(m) ? role : null, m.license].filter(Boolean).join(" · ");
  };

  const use = async (m: CatalogModel) => {
    const ok = await catalog.use(m);
    if (ok) toast({ message: t("flows.models.nowAnswering", { name: nameOf(m) }), tone: "success" });
  };

  const renderGroup = (list: CatalogModel[]) => (
    <CatalogList>
      {list.map((m) => (
        <CatalogRow
          key={m.id}
          model={m}
          title={nameOf(m)}
          meta={roleAndLicense(m)}
          view={catalog.view(m)}
          fileImport={catalog.importFor(m.id)}
          fit={catalog.fit(m)}
          busy={catalog.loadingId !== null}
          onDownload={() => catalog.install([m])}
          onUse={() => use(m)}
          onRemove={() => catalog.remove(m)}
        />
      ))}
    </CatalogList>
  );

  // What "Automatic" would pick right now, with the same rule the chat uses (routing/depth.ts).
  const installedLlms = models.filter((m) => m.kind === "llm" && catalog.statuses[m.id]?.present);
  const activeLlm = installedLlms.find((m) => m.id === catalog.activeLlmId);
  const autoDeep = resolveDeepModel(
    installedLlms.map((m) => ({
      id: m.id,
      label: nameOf(m),
      sizeBytes: m.sizeBytes,
      roles: m.capabilities?.roles ?? [],
      fit: catalog.fit(m)?.verdict,
      tokPerSec: speeds[m.id]?.medianTokPerSec,
    })),
    catalog.activeLlmId,
    undefined
  );

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.settings.models")}</ScreenTitle>
      {/* One figure per card (phase 2 rule): what BOAR uses; the free space is the metadata under it. */}
      <Card padding="compact" style={{ gap: tokens.space.xs }}>
        <Stat label={t("flows.models.usedLabel")} {...formatBytesParts(catalog.usedBytes, i18n.language)} />
        {catalog.freeBytes > 0 && (
          <MetaLine items={[t("flows.models.freeMeta", { size: formatBytes(catalog.freeBytes, i18n.language) })]} />
        )}
      </Card>

      <Section title={t("flows.models.yours")} footer={t("flows.models.yoursFooter")}>
        {renderGroup(groups.yours)}
      </Section>

      {answer && (
        <View style={{ gap: tokens.space.md }}>
          {/* Inset 14 like the Section titles above and below (the shot had it on the gutter). */}
          <Text variant="label" color="secondary" style={{ paddingHorizontal: tokens.space.md + tokens.space.xxs }}>
            {t("flows.models.deepTitle")}
          </Text>
          <View accessibilityRole="radiogroup" style={{ gap: tokens.space.md }}>
            <OptionCard
              title={t("flows.models.deepAuto")}
              description={
                autoDeep
                  ? t("flows.models.deepAutoPicks", { name: autoDeep.label })
                  : t("flows.models.deepAutoNone", { min: MIN_DEEP_TOK_PER_SEC, name: activeLlm ? nameOf(activeLlm) : "—" })
              }
              selected={answer.deepModelId === undefined}
              onPress={() => chooseDeep(undefined)}
            />
            <OptionCard
              title={t("flows.models.deepNone")}
              description={t("flows.models.deepNoneNamed", { name: activeLlm ? nameOf(activeLlm) : "—" })}
              selected={answer.deepModelId === null}
              onPress={() => chooseDeep(null)}
            />
            {installedLlms
              .filter((m) => m.id !== catalog.activeLlmId)
              .map((m) => {
                const sp = speeds[m.id];
                return (
                  <OptionCard
                    key={m.id}
                    title={nameOf(m)}
                    // Same risk as Use for answers on a low-RAM phone (CR-1).
                    badge={catalog.view(m).mayCloseApp ? <Badge label={t(catalog.view(m).didNotOpen ? "flows.row.didNotOpen" : "flows.row.mayClose")} tone="danger" dot caps={false} /> : undefined}
                    // The measured speed decides; nothing is shown that was not measured here.
                    trailing={sp ? t(`flows.performance.band.${tokPerSecBand(sp.medianTokPerSec)}`) : undefined}
                    description={
                      // Too big for this phone first: no number of answers will make it automatic here (Prism CR-2).
                      catalog.view(m).mayCloseApp
                        ? t("flows.models.lowRamNotAuto")
                        : deepAutoEligible(sp, { model: m, totalRamBytes: catalog.deviceRamBytes })
                        ? undefined
                        : deepAutoEligible(sp)
                          ? // Fast enough, but above the compact size on a low-RAM phone (Tusk, CR-1).
                            t("flows.models.lowRamNotAuto")
                          : (sp?.samples ?? 0) < MIN_SPEED_SAMPLES
                          ? t("flows.models.notEnoughSamples", { min: MIN_SPEED_SAMPLES })
                          : t("flows.models.tooSlow")
                    }
                    meta={[
                      sp && t("flows.models.tokRate", { rate: formatRate(sp.medianTokPerSec, i18n.language) }),
                      sp
                        ? t("flows.models.samples", { count: sp.samples, date: sp.lastAt ? new Date(sp.lastAt).toLocaleDateString(i18n.language) : "—" })
                        : t("flows.models.notMeasured"),
                    ]}
                    selected={answer.deepModelId === m.id}
                    onPress={() => chooseDeep(m.id)}
                  />
                );
              })}
            {installedLlms.length < 2 && (
              <Text variant="footnote" color="secondary" style={{ paddingHorizontal: tokens.space.md + tokens.space.xxs }}>
                {t("flows.models.deepOnlyOne")}
              </Text>
            )}
          </View>
          <Text variant="footnote" color="secondary" style={{ paddingHorizontal: tokens.space.md + tokens.space.xxs }}>
            {t("flows.models.deepFooter", { min: formatCount(MIN_DEEP_TOK_PER_SEC, i18n.language) })}
          </Text>
        </View>
      )}

      <Section title={t("flows.models.getMore")} footer={t("flows.models.availableFooter")}>
        {/* Search first: it reaches every GGUF on Hugging Face, the list below is the curated catalog. */}
        {!offline && <ListRow icon="search" title={t("flows.models.searchTitle")} subtitle={t("flows.models.searchFooter")} onPress={() => navigation.navigate("ModelSearch")} />}
        {groups.available.length > 0 ? (
          renderGroup(groups.available)
        ) : (
          <View style={{ padding: tokens.space.base }}>
            <Text variant="callout" color="secondary">
              {t("flows.models.allInstalled")}
            </Text>
          </View>
        )}
      </Section>

      {(offline || catalog.imports.length > 0) && (
        <Section title={t("flows.import.title")} footer={t("flows.import.footer")}>
          <View style={{ padding: tokens.space.base }}>
            <ImportList imports={catalog.imports} onPick={catalog.importFiles} onCancel={catalog.cancelImports} />
          </View>
        </Section>
      )}

      {groups.larger.length > 0 && (
        <Section title={t("flows.models.larger")} footer={t("flows.models.largerFooter")}>
          <ListRow
            title={t("flows.models.showLarger", { count: groups.larger.length })}
            expanded={showLarger}
            onPress={() => setShowLarger((v) => !v)}
          />
          {showLarger && renderGroup(groups.larger)}
        </Section>
      )}
    </Screen>
  );
}

type SearchState =
  | { kind: "idle" }
  | { kind: "searching" }
  | { kind: "error"; message: string }
  | { kind: "results"; items: HFModelSummary[] };

export function ModelSearchScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState<SearchState>({ kind: "idle" });
  const [open, setOpen] = useState<string | null>(null);
  const motion = useMotion();
  const [files, setFiles] = useState<Record<string, HFGgufFile[] | "loading" | "error">>({});
  const [adding, setAdding] = useState<string | null>(null);
  const [sizeFilter, setSizeFilter] = useState<SizeFilter>("fits");
  const [budget] = useState(() => {
    const total = getDeviceTotalRamBytes();
    return total > 0 ? availableRamFrom({ totalBytes: total, rssBytes: 0 }) : 0;
  });

  const run = async () => {
    const q = query.trim();
    if (!q) return;
    setSearch({ kind: "searching" });
    setOpen(null);
    try {
      setSearch({ kind: "results", items: await searchModels(q) });
    } catch (e: any) {
      setSearch({ kind: "error", message: e?.message ?? String(e) });
    }
  };

  const toggle = async (repoId: string) => {
    // Opening or closing a repo's files slides the results below (TR-6).
    motion.animateNextLayout();
    if (open === repoId) return setOpen(null);
    setOpen(repoId);
    if (Array.isArray(files[repoId])) return;
    setFiles((f) => ({ ...f, [repoId]: "loading" }));
    try {
      const list = await listGgufFiles(repoId);
      setFiles((f) => ({ ...f, [repoId]: list }));
    } catch {
      setFiles((f) => ({ ...f, [repoId]: "error" }));
    }
  };

  const add = async (repoId: string, file: HFGgufFile) => {
    const key = `${repoId}/${file.filename}`;
    setAdding(key);
    try {
      const model = toCatalogModel(repoId, file);
      await addDiscoveredModel(model);
      toast({ message: t("flows.models.added", { name: catalogLabel(model, t) }), tone: "success" });
    } catch (e: any) {
      toast({ message: t("flows.models.addFailed", { error: t(userErrorKey(e)) }), tone: "danger" });
    } finally {
      setAdding(null);
    }
  };

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.models.searchTitle")}</ScreenTitle>
      <Text variant="callout" color="secondary">
        {t("flows.models.searchIntro")}
      </Text>
      <TextField
        accessibilityLabel={t("flows.models.searchTitle")}
        placeholder={t("flows.models.searchPlaceholder")}
        value={query}
        onChangeText={setQuery}
        onSubmitEditing={run}
        returnKeyType="search"
        autoCapitalize="none"
        autoCorrect={false}
      />
      <Button label={t("flows.models.searchButton")} icon="search" onPress={run} loading={search.kind === "searching"} disabled={!query.trim()} />
      <SegmentedControl<SizeFilter>
        label={t("flows.models.sizeFilter")}
        size="compact"
        value={sizeFilter}
        onChange={setSizeFilter}
        options={[
          { value: "fits", label: t("flows.models.sizeFits") },
          { value: "2", label: t("flows.models.sizeUpTo", { gb: 2 }) },
          { value: "4", label: t("flows.models.sizeUpTo", { gb: 4 }) },
          { value: "any", label: t("flows.models.sizeAny") },
        ]}
      />

      {search.kind === "error" && (
        <EmptyState
          tone="error"
          icon="wifi-off"
          title={t("flows.models.searchFailed")}
          body={t(userErrorKey(search.message))}
          detail={search.message}
          actionLabel={t("flows.row.retry")}
          onAction={run}
        />
      )}
      {search.kind === "results" && search.items.length === 0 && (
        <EmptyState icon="search" title={t("flows.models.noResults")} body={t("flows.models.noResultsBody")} />
      )}
      {search.kind === "results" && search.items.length > 0 && (
        <Section>
          {search.items.map((item) => {
            const list = files[item.id];
            return (
              <View key={item.id}>
                <ListRow
                  title={item.id}
                  subtitle={t("flows.models.repoMeta", {
                    downloads: formatCount(item.downloads ?? 0, i18n.language),
                    likes: formatCount(item.likes ?? 0, i18n.language),
                  })}
                  // It folds its files in place: a disclosure, not navigation (Prism FL-14).
                  expanded={open === item.id}
                  onPress={() => toggle(item.id)}
                  accessibilityHint={t("flows.models.repoHint")}
                />
                {open === item.id && (
                  <View style={{ paddingHorizontal: tokens.space.base, paddingBottom: tokens.space.md, gap: tokens.space.sm }}>
                    {list === "loading" && <Skeleton height={tokens.size.row} />}
                    {list === "error" && (
                      <Text variant="footnote" color="danger">
                        {t("flows.models.filesFailed")}
                      </Text>
                    )}
                    {Array.isArray(list) && list.length > 0 && !list.some((f) => passesSizeFilter(f.sizeBytes, sizeFilter, budget)) && (
                      <Text variant="footnote" color="secondary">
                        {t("flows.models.noneInSize")}
                      </Text>
                    )}
                    {Array.isArray(list) && list.length === 0 && (
                      <Text variant="footnote" color="secondary">
                        {t("flows.models.noGguf")}
                      </Text>
                    )}
                    {Array.isArray(list) &&
                      list.filter((f) => passesSizeFilter(f.sizeBytes, sizeFilter, budget)).map((file) => {
                        const key = `${item.id}/${file.filename}`;
                        return (
                          <View key={key} style={{ gap: tokens.space.xs }}>
                            <Text variant="callout" numberOfLines={2} ellipsizeMode="middle" accessibilityLabel={file.filename}>
                              {file.filename}
                            </Text>
                            <Text variant="footnote" color={file.sha256 ? "secondary" : "warning"}>
                              {formatBytes(file.sizeBytes, i18n.language)}
                              {file.sha256 ? "" : ` · ${t("flows.models.noChecksum")}`}
                            </Text>
                            <Button size="sm" variant="secondary" label={t("flows.models.addToList")} loading={adding === key} onPress={() => add(item.id, file)} />
                          </View>
                        );
                      })}
                  </View>
                )}
              </View>
            );
          })}
        </Section>
      )}
    </Screen>
  );
}
