import React, { useCallback, useState } from "react";
import { View } from "react-native";
import { useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { MAX_TOKENS_OPTIONS } from "../constants/personalities";
import { voiceInBuild } from "../config/variant";
import { Button, IconText, ListRow, Screen, Section, SegmentedControl, Sheet, Text, useAnnounce, useToast } from "./components";
import { useTheme, useTokens } from "./theme";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { catalogLabel } from "./flows/catalogLabel";
import { formatCount, toWords } from "./flows/format";
import { MODEL_CATALOG } from "../models/manifest";
import { useLanguage } from "../i18n/LanguageContext";
import {
  Appearance,
  getAnswerSettings,
  setAnswerSettings,
  FontScale,
  getHapticsEnabled,
  getShowLiveStats,
  getMaxTokens,
  getMemorySettings,
  getPersonalityId,
  getVoiceInputEnabled,
  LanguageId,
  PaletteChoice,
  setHapticsEnabled,
  setShowLiveStats,
  setVoiceInputEnabled,
  getActiveModelId,
} from "../models/settings";
import { setHapticsEnabledCache } from "../services/haptics";
import { resetAllAppData } from "../services/appReset";
import type { RootStackParamList } from "./navigation/types";
import { userErrorKey } from "./flows/userError";

type Nav = NativeStackNavigationProp<RootStackParamList>;

interface Values {
  personality: string;
  maxTokens: number;
  quickFirst: boolean;
  alwaysComplete: boolean;
  maxSavedSessions: number;
  haptics: boolean;
  voice: boolean;
  liveStats: boolean;
}

/** The length as plain words ("A page"); a nonstandard stored value falls back to its approximate word count. */
function lengthLabel(t: TFunction, maxTokens: number, locale: string): string {
  return (MAX_TOKENS_OPTIONS as readonly number[]).includes(maxTokens)
    ? t(`flows.length.hint${maxTokens}`)
    : t("flows.settings.words", { words: formatCount(Math.round(toWords(maxTokens)), locale) });
}

/** Which of the four answer modes the two toggles select (see flows-spec §3.1). */
function answerModeKey(quickFirst: boolean, alwaysComplete: boolean): string {
  return `flows.settings.answerMode.${quickFirst ? "quick" : "direct"}${alwaysComplete ? "Complete" : "Model"}`;
}

export function SettingsScreen() {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const navigation = useNavigation<Nav>();
  const toast = useToast();
  const announce = useAnnounce();
  const { appearance, setAppearance, fontScale, setFontScale, palette, setPalette } = useTheme();
  const { languageId, setLanguage } = useLanguage();
  const [values, setValues] = useState<Values | null>(null);
  const [resetOpen, setResetOpen] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [assistant, setAssistant] = useState<string>();

  useFocusEffect(
    useCallback(() => {
      getActiveModelId("llm").then((id) => {
        const m = id ? MODEL_CATALOG.find((x) => x.id === id) : undefined;
        setAssistant(m ? catalogLabel(m, t) : undefined);
      });
    }, [t])
  );

  useFocusEffect(
    useCallback(() => {
      (async () => {
        const [personality, maxTokens, quickFirst, alwaysComplete, memory, haptics, voice, liveStats] = await Promise.all([
          getPersonalityId(),
          getMaxTokens(),
          getAnswerSettings().then((a) => a.quickFirst),
          getAnswerSettings().then((a) => a.alwaysComplete),
          getMemorySettings(),
          getHapticsEnabled(),
          getVoiceInputEnabled(),
          getShowLiveStats(),
        ]);
        setValues({ personality, maxTokens, quickFirst, alwaysComplete, maxSavedSessions: memory.maxSavedSessions, haptics, voice, liveStats });
      })();
    }, [])
  );

  const update = async <K extends keyof Values>(key: K, value: Values[K], persist: (v: Values[K]) => Promise<void>) => {
    setValues((prev) => (prev ? { ...prev, [key]: value } : prev));
    try {
      await persist(value);
    } catch {
      toast({ message: t("flows.settings.saveFailed"), tone: "danger" });
    }
  };

  const reset = async () => {
    setResetting(true);
    try {
      await resetAllAppData();
      setResetOpen(false);
      navigation.reset({ index: 0, routes: [{ name: "Setup" }] });
    } catch (e: any) {
      setResetting(false);
      toast({ message: t("flows.settings.resetFailed", { error: t(userErrorKey(e)) }), tone: "danger" });
    }
  };

  if (!values) return <Screen contentStyle={screenRhythm(tokens)}><ScreenTitle>{t("nav.settings")}</ScreenTitle></Screen>;

  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("nav.settings")}</ScreenTitle>
      <Section title={t("flows.settings.answers")} footer={t(answerModeKey(values.quickFirst, values.alwaysComplete))}>
        <ListRow icon="user" title={t("flows.assistant.title")} value={assistant} onPress={() => navigation.navigate("SettingsAssistant")} />
        <ListRow
          icon="message-circle"
          title={t("flows.settings.tone")}
          value={t(`personalities.${values.personality}.label`)}
          onPress={() => navigation.navigate("SettingsTone")}
        />
        <ListRow
          icon="align-left"
          title={t("flows.settings.length")}
          value={lengthLabel(t, values.maxTokens, i18n.language)}
          onPress={() => navigation.navigate("SettingsLength")}
        />
        <ListRow
          icon="zap"
          title={t("flows.settings.quickFirst")}
          subtitle={t("flows.settings.quickFirstHint")}
          switch={{ value: values.quickFirst, onValueChange: (v) => {
              update("quickFirst", v, (quickFirst) => setAnswerSettings({ quickFirst }));
              announce(t(answerModeKey(v, values.alwaysComplete)));
            } }}
        />
        <ListRow
          icon="layers"
          title={t("flows.settings.alwaysComplete")}
          subtitle={t("flows.settings.alwaysCompleteHint")}
          switch={{ value: values.alwaysComplete, onValueChange: (v) => {
              update("alwaysComplete", v, (alwaysComplete) => setAnswerSettings({ alwaysComplete }));
              announce(t(answerModeKey(values.quickFirst, v)));
            } }}
        />
      </Section>

      <Section title={t("flows.settings.library")}>
        <ListRow icon="cpu" title={t("flows.settings.models")} onPress={() => navigation.navigate("Models")} />
        <ListRow icon="book-open" title={t("flows.settings.knowledge")} onPress={() => navigation.navigate("Knowledge")} />
        <ListRow
          icon="clock"
          title={t("flows.settings.history")}
          value={
            values.maxSavedSessions > 0
              ? t("flows.settings.keepLast", { count: values.maxSavedSessions })
              : t("flows.settings.keepAll")
          }
          onPress={() => navigation.navigate("SettingsHistory")}
        />
      </Section>

      <Section title={t("flows.settings.appearance")}>
        <View style={{ paddingHorizontal: tokens.space.inset, paddingVertical: tokens.space.base, gap: tokens.space.base }}>
          {/* Every control in the block has its overline, the theme too (Prism FL-26). */}
          <Text variant="label" color="secondary">
            {t("flows.settings.theme")}
          </Text>
          <SegmentedControl<Appearance>
            size="compact"
            label={t("flows.settings.theme")}
            value={appearance}
            onChange={setAppearance}
            options={[
              // Dark (Fogueira) is the default, so it comes first.
              { value: "dark", label: t("flows.settings.themeDark") },
              { value: "light", label: t("flows.settings.themeLight") },
              { value: "system", label: t("flows.settings.themeSystem") },
            ]}
          />
          <Text variant="label" color="secondary">
            {t("flows.settings.palette")}
          </Text>
          <SegmentedControl<PaletteChoice>
            size="compact"
            label={t("flows.settings.palette")}
            value={palette}
            onChange={setPalette}
            options={[
              { value: "fogueira", label: t("flows.settings.paletteFogueira") },
              { value: "luar", label: t("flows.settings.paletteLuar") },
            ]}
          />
          <Text variant="label" color="secondary">
            {t("flows.settings.textSize")}
          </Text>
          <SegmentedControl<FontScale>
            size="compact"
            label={t("flows.settings.textSize")}
            value={fontScale}
            onChange={setFontScale}
            options={[
              { value: "compact", label: t("flows.settings.textCompact") },
              { value: "standard", label: t("flows.settings.textStandard") },
              { value: "large", label: t("flows.settings.textLarge") },
            ]}
          />
          <Text variant="label" color="secondary">
            {t("flows.settings.language")}
          </Text>
          <SegmentedControl<LanguageId>
            size="compact"
            label={t("flows.settings.language")}
            value={languageId}
            onChange={setLanguage}
            options={[
              { value: "en", label: "English" },
              { value: "pt", label: "Português" },
            ]}
          />
        </View>
        <ListRow
          title={t("flows.settings.haptics")} switch={{ value: values.haptics, onValueChange: (v) => {
            setHapticsEnabledCache(v);
            update("haptics", v, setHapticsEnabled);
          } }}
        />
        <ListRow
          title={t("flows.settings.liveStats")}
          subtitle={t("flows.settings.liveStatsHint")}
          switch={{ value: values.liveStats, onValueChange: (v) => update("liveStats", v, setShowLiveStats) }}
        />
      </Section>

      {/* The offline build has no microphone permission: say so instead of offering a switch that contradicts the manifest (Prism P4-1). */}
      {voiceInBuild() ? (
        <Section title={t("flows.settings.input")} footer={t("flows.settings.voiceNote")}>
          <ListRow icon="mic" title={t("flows.settings.voice")} switch={{ value: values.voice, onValueChange: (v) => update("voice", v, setVoiceInputEnabled) }} />
        </Section>
      ) : (
        <Section title={t("flows.settings.input")}>
          <ListRow icon="mic-off" title={t("flows.settings.voice")} subtitle={t("flows.settings.voiceNotInBuild")} />
        </Section>
      )}

      <Section>
        <ListRow icon="activity" title={t("flows.settings.performance")} onPress={() => navigation.navigate("Performance")} />
        <ListRow icon="info" title={t("flows.settings.about")} onPress={() => navigation.navigate("About")} />
      </Section>

      <Section title={t("flows.settings.recovery")}>
        <ListRow icon="refresh-cw" title={t("flows.settings.rerunSetup")} onPress={() => navigation.navigate("Setup")} />
        <ListRow icon="trash-2" title={t("flows.settings.eraseAll")} destructive onPress={() => setResetOpen(true)} />
      </Section>

      <Sheet
        visible={resetOpen}
        onClose={() => !resetting && setResetOpen(false)}
        dismissible={!resetting}
        title={t("flows.settings.eraseTitle")}
        description={t("flows.settings.eraseBody")}
        footer={
          <>
            <Button label={t("common.cancel")} variant="secondary" onPress={() => setResetOpen(false)} disabled={resetting} fullWidth />
            <Button label={t("flows.settings.eraseConfirm")} variant="destructive" onPress={reset} loading={resetting} fullWidth />
          </>
        }
      >
        <View style={{ gap: tokens.space.xs }}>
          {(["eraseModels", "eraseKnowledge", "eraseHistory", "eraseSettings"] as const).map((k) => (
            // A list mark from the icon set, not a "•" typed into the text (Prism FL-34).
            <IconText key={k} icon="minus" variant="callout" color="secondary">
              {t(`flows.settings.${k}`)}
            </IconText>
          ))}
        </View>
      </Sheet>
    </Screen>
  );
}
