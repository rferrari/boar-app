/**
 * Settings › Assistant: which of the two answer models writes the answers, by
 * what they are for (Fast, More accurate), with In use on the active one. The
 * technical names sit folded under Details (r4to, decision 5C).
 */
import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { useTranslation } from "react-i18next";
import { ListRow, Screen, Section, Skeleton, Text, useToast } from "./components";
import { useTokens } from "./theme";
import { useMotion } from "./theme/motion";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { CatalogList } from "./flows/CatalogList";
import { CatalogRow } from "./flows/CatalogRow";
import { useCatalog } from "./flows/useCatalog";
import { catalogLabel, isAdvancedModel, technicalModelName } from "./flows/catalogLabel";
import { ANSWER_MODELS, MODEL_CATALOG, type CatalogModel } from "../models/manifest";

export function SettingsAssistantScreen() {
  const { t } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const catalog = useCatalog();
  const { refresh } = catalog;
  const [detailsOpen, setDetailsOpen] = useState(false);
  const motion = useMotion();
  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  if (!catalog.loaded) {
    return (
      <Screen contentStyle={screenRhythm(tokens)}>
        <ScreenTitle>{t("flows.assistant.title")}</ScreenTitle>
        <View accessible accessibilityLabel={t("flows.common.loading")} style={{ gap: tokens.space.md }}>
          <Skeleton height={tokens.size.control * 2} />
          <Skeleton height={tokens.size.control * 2} />
        </View>
      </Screen>
    );
  }

  const use = async (m: CatalogModel) => {
    const ok = await catalog.use(m);
    if (ok) toast({ message: t("flows.models.nowAnswering", { name: catalogLabel(m, t) }), tone: "success" });
  };
  const active = catalog.activeLlmId ? [...MODEL_CATALOG, ...catalog.discovered].find((m) => m.id === catalog.activeLlmId) : undefined;
  const search = MODEL_CATALOG.find((m) => m.kind === "embedding");
  // Details: the two answer models, the search model, and an advanced model if one is in use.
  const detailed = [...ANSWER_MODELS, ...(search ? [search] : []), ...(active && isAdvancedModel(active) ? [active] : [])];

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.assistant.title")}</ScreenTitle>
      <Section footer={t("flows.assistant.footer")}>
        <CatalogList>
          {ANSWER_MODELS.map((m) => (
            <CatalogRow
              key={m.id}
              model={m}
              view={catalog.view(m)}
              fileImport={catalog.importFor(m.id)}
              fit={catalog.fit(m)}
              busy={catalog.loadingId !== null}
              showKind={false}
              showRoles={false}
              meta={t(`flows.assistant.sub.${m.answerTier}`)}
              onDownload={() => catalog.install([m])}
              onUse={() => use(m)}
              onRemove={() => catalog.remove(m)}
            />
          ))}
        </CatalogList>
      </Section>
      {active && isAdvancedModel(active) && (
        <Text variant="footnote" color="secondary" style={{ paddingHorizontal: tokens.space.md + tokens.space.xxs }}>
          {t("flows.assistant.otherInUse")}
        </Text>
      )}
      <Section>
        <ListRow
          title={t("flows.assistant.details")}
          expanded={detailsOpen}
          accessibilityLabel={t("flows.assistant.detailsA11y")}
          onPress={() => {
            // The rows below unfold instead of appearing at once (TR-6).
            motion.animateNextLayout();
            setDetailsOpen((v) => !v);
          }}
        />
        {detailsOpen &&
          detailed.map((m) => <ListRow key={m.id} title={catalogLabel(m, t)} subtitle={technicalModelName(m)} />)}
      </Section>
    </Screen>
  );
}
