import React, { useCallback } from "react";
import { View } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import * as Clipboard from "expo-clipboard";
import { REPO_URL } from "./flows/links";
import { useTranslation } from "react-i18next";
import { Button, ListRow, Mascot, Screen, Section, Text, useToast } from "./components";
import { catalogLabel } from "./flows/catalogLabel";
import { useTokens } from "./theme";
import { screenRhythm } from "./flows/rhythm";
import { useCatalog } from "./flows/useCatalog";
import { packName, topicPacks } from "./flows/adapters";
import { formatBytes } from "./flows/format";
import { MODEL_CATALOG } from "../models/manifest";
import appConfig from "../../app.json";


/** Fonts bundled in the app (src/ui/theme/fontFiles.ts); licenses from each package's LICENSE_FONT. */
const BUNDLED_FONTS = [
  { name: "Baloo 2", license: "SIL Open Font License 1.1" },
  { name: "Lexend", license: "SIL Open Font License 1.1" },
];

export function AboutScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const catalog = useCatalog();
  const { refresh } = catalog;
  const packs = topicPacks();

  useFocusEffect(
    useCallback(() => {
      refresh();
    }, [refresh])
  );

  const installed = [...MODEL_CATALOG, ...catalog.discovered].filter((m) => catalog.statuses[m.id]?.present);
  const build = __DEV__ ? t("flows.about.buildDev") : t("flows.about.buildRelease");

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <View style={{ alignItems: "center", gap: tokens.space.sm }}>
        <Mascot size="brand" />
        <Text variant="title1" align="center">
          boar
        </Text>
        <Text variant="callout" color="secondary" align="center">
          {t("flows.about.tagline")}
        </Text>
        <Text variant="footnote" color="secondary" align="center" numeric>
          {t("flows.about.version", { version: appConfig.expo.version, build })}
        </Text>
      </View>

      <Section title={t("flows.about.howTitle")}>
        <View style={{ padding: tokens.space.base, gap: tokens.space.md }}>
          {(["how1", "how2", "how3"] as const).map((k) => (
            <Text key={k} variant="callout">
              {t(`flows.about.${k}`)}
            </Text>
          ))}
        </View>
      </Section>

      <Section title={t("flows.about.installedTitle")} footer={t("flows.about.installedFooter")}>
        {installed.length === 0 ? (
          <ListRow title={t("flows.about.nothingInstalled")} />
        ) : (
          installed.map((m) => (
            <ListRow key={m.id} title={catalogLabel(m, t, { technical: true })} value={formatBytes(m.sizeBytes, i18n.language)} subtitle={m.license} />
          ))
        )}
      </Section>

      {packs.map((pack) => (
        <Section key={pack.entry.id} title={t("flows.about.packSources", { name: packName(pack, i18n.language) })} footer={t("flows.about.packFooter")}>
          {pack.sources.map((s) => (
            <ListRow key={s.name} title={s.name} subtitle={s.license} />
          ))}
        </Section>
      ))}

      <Section title={t("flows.about.fontsTitle")}>
        {BUNDLED_FONTS.map((f) => (
          <ListRow key={f.name} title={f.name} subtitle={f.license} />
        ))}
      </Section>

      <Section title={t("flows.about.sourceTitle")} footer={t("flows.about.sourceFooter")}>
        <View style={{ padding: tokens.space.base, gap: tokens.space.md }}>
          <Text variant="footnote" selectable>
            {REPO_URL}
          </Text>
          <Button
            size="sm"
            variant="secondary"
            icon="copy"
            label={t("flows.about.copy")}
            accessibilityHint={t("flows.about.copyHint")}
            onPress={async () => {
              await Clipboard.setStringAsync(`https://${REPO_URL}`);
              toast({ message: t("flows.about.copied"), tone: "success" });
            }}
          />
        </View>
      </Section>
    </Screen>
  );
}
