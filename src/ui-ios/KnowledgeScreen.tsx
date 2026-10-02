import React, { useCallback, useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import type { DocumentPickerAsset } from "expo-document-picker";
import { useTranslation } from "react-i18next";
import { Button, Card, EmptyState, ListRow, MetaLine, Progress, Reveal, Screen, ScreenScroll, Section, Sheet, Skeleton, Stat, Text, TextField, useAnnounce, useLateLoad, useToast } from "./components";
import { useTokens } from "./theme";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { CatalogModel, CORPUS_CATALOG } from "../models/manifest";
import {
  deleteCustomCollection,
  exportCollection,
  importDocuments,
  ImportCancelledError,
  ImportProgress,
  listCustomCollections,
  pickDocuments,
  setCustomCollectionActive,
} from "../services/documentImporter";
import type { CustomCollection } from "../rag/db";
import { onSeedProgress, seedKnowledgeBaseIfEmpty, SeedProgress } from "../rag/seedCorpus";
import { LoadingLine } from "./flows/LoadingLine";
import { CollectionIndexStatus, getCollectionIndexStatus, onCollectionIndexStatus } from "../rag/indexStatus";
import { CatalogRow } from "./flows/CatalogRow";
import { CatalogList } from "./flows/CatalogList";
import { ImportList } from "./flows/ImportList";
import { networkAllowed } from "../config/variant";
import { useCatalog } from "./flows/useCatalog";
import { formatBytes, formatCount } from "./flows/format";
import { packName, placesInstall, poiCatalogEntry, poiRegions, topicPacks } from "./flows/adapters";
import { CitySearch } from "./flows/CitySearch";
import { PlaceAreaRows } from "./flows/PlaceAreaRows";
import { canDownload } from "./flows/useCatalog";
import { citySummary } from "./flows/poi";
import { userErrorKey } from "./flows/userError";

function importPercent(p: ImportProgress): number | undefined {
  if (p.stage !== "embedding" || !p.chunkCount) return undefined;
  return ((p.chunkIndex ?? 0) + 1) / p.chunkCount;
}

export function KnowledgeScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const announce = useAnnounce();
  const catalog = useCatalog();
  const lang = i18n.language;
  const regions = poiRegions();
  const packs = topicPacks();
  const { refresh } = catalog;
  const [collections, setCollections] = useState<CustomCollection[] | null>(null);
  const [seed, setSeed] = useState<SeedProgress | null>(null);
  const [picked, setPicked] = useState<DocumentPickerAsset[] | null>(null);
  const [name, setName] = useState("");
  const [importing, setImporting] = useState<ImportProgress | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  // The import error sits at the top of the screen, with adding documents; no scroll needed to see it.
  const scrollRef = useRef<ScreenScroll>(null);
  const [toRemove, setToRemove] = useState<CustomCollection | null>(null);
  const [exportingId, setExportingId] = useState<string | null>(null);
  const [indexStatus, setIndexStatus] = useState<Record<string, CollectionIndexStatus>>(getCollectionIndexStatus);
  const abortRef = useRef<AbortController | null>(null);
  const [importingName, setImportingName] = useState("");

  const load = useCallback(async () => {
    await refresh();
    setCollections(await listCustomCollections());
  }, [refresh]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );
  useEffect(() => onSeedProgress((p) => setSeed(p.done >= p.total ? null : p)), []);
  useEffect(() => onCollectionIndexStatus(setIndexStatus), []);
  const lateLoad = useLateLoad(catalog.loaded && collections !== null);

  /** One line for a collection's index state; undefined when there is nothing to say. */
  const statusLine = (id: string): string | undefined => {
    const st = indexStatus[id];
    if (!st) return undefined;
    if (st.state === "indexing") return t("flows.knowledge.indexing", { done: formatCount(st.done, lang), total: formatCount(st.total, lang) });
    if (st.state === "error") return t("flows.knowledge.indexError", { error: t(userErrorKey(st.error ?? "")) });
    return t("flows.knowledge.indexed");
  };

  const downloadPack = async (pack: CatalogModel) => {
    await catalog.download(pack);
    if (pack.format !== "sqlite-pack") {
      await seedKnowledgeBaseIfEmpty();
      setSeed(null);
    }
    toast({ message: t("flows.knowledge.packReady", { name: pack.label }), tone: "success" });
  };

  const pick = async () => {
    setImportError(null);
    const files = await pickDocuments();
    // Cancelled picker: back to where we were, no message.
    if (!files || files.length === 0) return;
    setPicked(files);
    setName(files[0].name.replace(/\.[^.]+$/, ""));
  };

  const runImport = async () => {
    if (!picked) return;
    const files = picked;
    const collectionName = name.trim() || files[0].name;
    setPicked(null);
    setImporting({ stage: "reading" });
    setImportingName(collectionName);
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      await importDocuments(files, collectionName, setImporting, controller.signal);
      announce(t("flows.knowledge.imported", { name: collectionName }));
      toast({ message: t("flows.knowledge.imported", { name: collectionName }), tone: "success" });
    } catch (e: any) {
      // Cancelled by the user: back to where we were, no error.
      if (e instanceof ImportCancelledError || e?.name === "AbortError") {
        announce(t("flows.knowledge.importCancelled"));
      } else {
        setImportError(e?.message ?? String(e));
        announce(t("flows.knowledge.importFailed"), { assertive: true });
      }
    } finally {
      abortRef.current = null;
      setImporting(null);
      setCollections(await listCustomCollections());
    }
  };

  const toggle = async (c: CustomCollection, active: boolean) => {
    setCollections((prev) => prev?.map((x) => (x.id === c.id ? { ...x, active } : x)) ?? prev);
    await setCustomCollectionActive(c.id, active);
  };

  const exportOne = async (c: CustomCollection) => {
    setExportingId(c.id);
    try {
      await exportCollection(c);
    } catch (e: any) {
      toast({ message: t("flows.knowledge.exportFailed", { error: t(userErrorKey(e)) }), tone: "danger" });
    } finally {
      setExportingId(null);
    }
  };

  if (!catalog.loaded || collections === null) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("nav.knowledge")}</ScreenTitle>
        <View accessible accessibilityLabel={t("flows.common.loading")} style={{ gap: tokens.space.md }}>
          <Skeleton height={tokens.space.lg} width="50%" />
          <Skeleton height={tokens.size.control * 2} />
          <Skeleton height={tokens.size.control * 2} />
        </View>
      </Screen>
    );
  }

  const importValue = importing ? importPercent(importing) : undefined;

  return (
    <Screen contentStyle={screenRhythm(tokens)} scrollRef={scrollRef}>
      <ScreenTitle>{t("nav.knowledge")}</ScreenTitle>
      {/* Replaces the skeleton: fades in when the data came after the screen (TR-5). */}
      <Reveal animate={lateLoad} style={screenRhythm(tokens)}>
        <Text variant="footnote" color="secondary">
          {t("flows.knowledge.intro")}
        </Text>

        {seed && (
          <Card style={{ gap: tokens.space.md }}>
            <Stat size="lg" label={t("flows.knowledge.indexingLabel")} value={String(Math.floor((seed.done / seed.total) * 100))} unit="%" />
            <Progress
              label={t("flows.knowledge.indexingLabel")}
              value={seed.done / seed.total}
              valueText={t("flows.knowledge.indexing", { done: formatCount(seed.done, lang), total: formatCount(seed.total, lang) })}
            />
            <MetaLine items={[t("flows.knowledge.indexing", { done: formatCount(seed.done, lang), total: formatCount(seed.total, lang) })]} />
            {seed.title ? (
              <Text variant="footnote" numberOfLines={1} ellipsizeMode="tail">
                {t("flows.onboarding.indexReading", { title: seed.title })}
              </Text>
            ) : null}
            <LoadingLine />
          </Card>
        )}

        {/* Adding documents comes first; the collections are listed at the end of the screen. */}
        {importError && (
            <EmptyState
              tone="error"
              title={t("flows.knowledge.importFailed")}
              body={t(userErrorKey(importError))}
              detail={importError}
              actionLabel={t("flows.knowledge.pickAgain")}
              onAction={pick}
            />
          )}
        {collections.length === 0 && !importing && !importError && (
          <EmptyState icon="file-plus" title={t("flows.knowledge.emptyTitle")} body={t("flows.knowledge.emptyBody")} actionLabel={t("flows.knowledge.add")} onAction={pick} />
        )}
        {importing && (
          <Card style={{ gap: tokens.space.md }}>
            <View style={{ flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", gap: tokens.space.md }}>
              <View style={{ flex: 1, gap: tokens.space.xxs }}>
                <Text variant="label" color="field">
                  {t("flows.knowledge.importingLabel")}
                </Text>
                <Text variant="headline" numberOfLines={2}>
                  {importingName}
                </Text>
              </View>
              {importValue != null && <Stat size="md" align="right" value={String(Math.round(importValue * 100))} unit="%" />}
            </View>
            <Progress label={t("flows.knowledge.importingLabel")} value={importValue} valueText={importValue != null ? `${Math.round(importValue * 100)}%` : undefined} />
            <MetaLine items={[t(`flows.knowledge.stage.${importing.stage}`, { current: (importing.chunkIndex ?? 0) + 1, total: importing.chunkCount ?? 0 })]} />
            <Button
              size="sm"
              variant="secondary"
              label={t("common.cancel")}
              accessibilityLabel={t("flows.knowledge.cancelImportA11y", { name: importingName })}
              onPress={() => abortRef.current?.abort()}
            />
          </Card>
        )}
        {collections.length > 0 && !importing && !importError && (
          <Button label={t("flows.knowledge.add")} icon="file-plus" onPress={pick} />
        )}


        {packs.length > 0 && (
          <Section title={t("flows.knowledge.topicPacksTitle")} footer={t("flows.knowledge.topicPacksFooter")}>
            <CatalogList>
            {packs.map((pack) => (
              <CatalogRow
                showKind={false}
                key={pack.entry.id}
                model={pack.entry}
                title={packName(pack, lang)}
                meta={t("flows.knowledge.docs", { count: pack.docCount, value: formatCount(pack.docCount, lang) })}
                details={[t("flows.knowledge.sourcesLine", { sources: pack.sources.map((s) => s.name).join(", ") })]}
                view={catalog.view(pack.entry)}
                fileImport={catalog.importFor(pack.entry.id)}
                onDownload={() => catalog.install([pack.entry])}
                onRemove={() => catalog.remove(pack.entry)}
              />
            ))}
            </CatalogList>
          </Section>
        )}

        <Section title={t("flows.knowledge.appCollections")} footer={t("flows.knowledge.appFooter")}>
          <CatalogList>
          <ListRow title={t("flows.knowledge.builtin")} subtitle={[t("flows.knowledge.builtinSub"), statusLine("builtin")].filter(Boolean).join("\n")} />
          {CORPUS_CATALOG.map((pack) => (
            <CatalogRow
                showKind={false}
              key={pack.id}
              model={pack}
              details={[statusLine(pack.id)].filter((x): x is string => !!x)}
              view={catalog.view(pack)}
              fileImport={catalog.importFor(pack.id)}
              onDownload={() => (canDownload(pack) ? downloadPack(pack) : catalog.install([pack]))}
              onRemove={() => catalog.remove(pack)}
            />
          ))}
          </CatalogList>
        </Section>

        {(catalog.imports.length > 0 || regions.some((r) => !canDownload(poiCatalogEntry(r)) && !catalog.statuses[poiCatalogEntry(r).id]?.present)) && (
          <Section title={t("flows.import.title")} footer={t("flows.import.footer")}>
            <View style={{ padding: tokens.space.base }}>
              <ImportList imports={catalog.imports} onPick={catalog.importFiles} onCancel={catalog.cancelImports} />
            </View>
          </Section>
        )}

        <Section title={t("flows.places.title")} footer={regions.length > 0 ? t("flows.places.footer") : undefined}>
          <CatalogList>
          <View style={{ padding: tokens.space.base }}>
            <CitySearch catalog={catalog} />
          </View>
          <PlaceAreaRows catalog={catalog} />
          {regions.length === 0 ? (
            <View style={{ padding: tokens.space.base }}>
              <Text variant="callout" color="secondary">
                {t("flows.places.none")}
              </Text>
            </View>
          ) : (
            regions.map((r) => {
              const entry = poiCatalogEntry(r);
              const cities = citySummary(r);
              const name = lang.startsWith("pt") ? r.name.pt : r.name.en;
              return (
                <CatalogRow
                showKind={false}
                  key={r.id}
                  model={entry}
                  title={name}
                  meta={t("flows.places.count", { places: formatCount(r.poiCount, lang) })}
                  details={[
                    t("flows.places.vegan", { vegan: formatCount(r.veganCount, lang), vegetarian: formatCount(r.vegetarianCount, lang) }),
                    cities.more > 0
                      ? t("flows.places.citiesMore", { cities: cities.names.join(", "), count: cities.more })
                      : cities.names.join(", "),
                  ].filter(Boolean)}
                  view={catalog.view(entry)}
                  fileImport={catalog.importFor(entry.id)}
                  onDownload={() => catalog.install(placesInstall(r))}
                  onRemove={() => catalog.remove(entry)}
                />
              );
            })
          )}
          </CatalogList>
        </Section>




        {collections.length > 0 && (
          <Section title={t("flows.knowledge.yourCollections")}>
            {collections.map((c) => (
              <View key={c.id}>
                <ListRow
                  title={c.name}
                  subtitle={t("flows.knowledge.collectionMeta", {
                    docs: t("flows.knowledge.docs", { count: c.docCount, value: formatCount(c.docCount, lang) }),
                    chunks: t("flows.knowledge.chunks", { count: c.chunkCount }),
                    size: formatBytes(c.sizeBytes, i18n.language),
                  })}
                  accessibilityLabel={t("flows.knowledge.useInAnswers", { name: c.name })}
                  switch={{ value: c.active, onValueChange: (v) => toggle(c, v) }}
                />
                <View style={{ flexDirection: "row", gap: tokens.space.sm, paddingHorizontal: tokens.space.inset, paddingBottom: tokens.space.md }}>
                  <Button
                    size="sm"
                    variant="secondary"
                    icon="share"
                    label={t("flows.knowledge.export")}
                    accessibilityLabel={t("flows.knowledge.exportA11y", { name: c.name })}
                    loading={exportingId === c.id}
                    onPress={() => exportOne(c)}
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    tone="danger"
                    label={t("flows.row.remove")}
                    accessibilityLabel={t("flows.knowledge.removeA11y", { name: c.name })}
                    onPress={() => setToRemove(c)}
                  />
                </View>
              </View>
            ))}
          </Section>
        )}

        <Sheet
          visible={picked !== null}
          onClose={() => setPicked(null)}
          title={t("flows.knowledge.nameTitle")}
          description={t("flows.knowledge.filesPicked", { count: picked?.length ?? 0 })}
          footer={
            <>
              <Button label={t("common.cancel")} variant="secondary" fullWidth onPress={() => setPicked(null)} />
              <Button label={t("flows.knowledge.import")} variant="primary" fullWidth onPress={runImport} />
            </>
          }
        >
          <TextField label={t("flows.knowledge.nameLabel")} value={name} onChangeText={setName} returnKeyType="done" onSubmitEditing={runImport} />
        </Sheet>

        <Sheet
          visible={toRemove !== null}
          onClose={() => setToRemove(null)}
          title={t("flows.knowledge.removeTitle", { name: toRemove?.name ?? "" })}
          description={t("flows.knowledge.removeBody", { count: toRemove?.chunkCount ?? 0 })}
          footer={
            <>
              <Button label={t("common.cancel")} variant="secondary" fullWidth onPress={() => setToRemove(null)} />
              <Button
                label={t("flows.row.remove")}
                variant="destructive"
                fullWidth
                onPress={async () => {
                  const c = toRemove!;
                  setToRemove(null);
                  await deleteCustomCollection(c.id);
                  setCollections(await listCustomCollections());
                  toast({ message: t("flows.row.removed", { name: c.name }), tone: "success" });
                }}
              />
            </>
          }
        />
      </Reveal>
    </Screen>
  );
}
