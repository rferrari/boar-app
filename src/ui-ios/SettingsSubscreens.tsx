/** Settings subscreens: answer tone, answer length, conversation history. */
import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { Button, ListRow, Screen, Section, Sheet, TextField, useToast } from "./components";
import { useTokens } from "./theme";
import { screenRhythm } from "./flows/rhythm";
import { ScreenTitle } from "./flows/ScreenTitle";
import { MAX_TOKENS_OPTIONS, PERSONALITIES, PersonalityId } from "../constants/personalities";
import {
  getCustomSystemPrompt,
  getMaxTokens,
  getMemorySettings,
  getPersonalityId,
  MemorySettings,
  setCustomSystemPrompt,
  setMaxTokens,
  setMemorySettings,
  setPersonalityId,
} from "../models/settings";
import { clearAllHistory } from "../services/chatHistory";
import { getChatBridge } from "./navigation/chatBridge";
import { RadioRow } from "./flows/RadioRow";
import { toWords } from "./flows/format";

export function SettingsToneScreen() {
  const tokens = useTokens();
  const { t } = useTranslation();
  const [selected, setSelected] = useState<PersonalityId | null>(null);
  const [custom, setCustom] = useState("");

  useEffect(() => {
    (async () => {
      setSelected(await getPersonalityId());
      setCustom(await getCustomSystemPrompt());
    })();
  }, []);

  if (!selected) return <Screen contentStyle={screenRhythm(tokens)}><ScreenTitle>{t("flows.settings.tone")}</ScreenTitle></Screen>;
  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.settings.tone")}</ScreenTitle>
      <Section footer={t("flows.tone.footer")}>
        <View accessibilityRole="radiogroup">
          {PERSONALITIES.map((p) => (
            <RadioRow
              key={p.id}
              title={t(`personalities.${p.id}.label`)}
              subtitle={t(`personalities.${p.id}.description`)}
              selected={selected === p.id}
              onPress={() => {
                setSelected(p.id);
                setPersonalityId(p.id);
              }}
            />
          ))}
        </View>
      </Section>
      {selected === "custom" && (
        <TextField
          label={t("flows.tone.customLabel")}
          helper={t("flows.tone.customHelper")}
          value={custom}
          onChangeText={(text) => {
            setCustom(text);
            setCustomSystemPrompt(text);
          }}
          autoGrow
          maxRows={8}
          multiline
        />
      )}
    </Screen>
  );
}

export function SettingsLengthScreen() {
  const tokens = useTokens();
  const { t } = useTranslation();
  const [selected, setSelected] = useState<number | null>(null);
  useEffect(() => {
    getMaxTokens().then(setSelected);
  }, []);

  if (selected == null) return <Screen contentStyle={screenRhythm(tokens)}><ScreenTitle>{t("flows.settings.length")}</ScreenTitle></Screen>;
  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.settings.length")}</ScreenTitle>
      <Section footer={t("flows.length.footer")}>
        <View accessibilityRole="radiogroup">
          {MAX_TOKENS_OPTIONS.map((n) => (
            <RadioRow
              key={n}
              title={t(`flows.length.hint${n}`)}
              subtitle={t("flows.length.words", { count: n, words: Math.round(toWords(n)) })}
              selected={selected === n}
              onPress={() => {
                setSelected(n);
                setMaxTokens(n);
              }}
            />
          ))}
        </View>
      </Section>
    </Screen>
  );
}

const TURN_OPTIONS = [4, 6, 8, 10] as const;
const SESSION_OPTIONS = [10, 25, 50, 0] as const;

export function SettingsHistoryScreen() {
  const { t } = useTranslation();
  const tokens = useTokens();
  const toast = useToast();
  const [memory, setMemory] = useState<MemorySettings | null>(null);
  const [clearOpen, setClearOpen] = useState(false);

  useEffect(() => {
    getMemorySettings().then(setMemory);
  }, []);

  const update = (patch: Partial<MemorySettings>) => {
    setMemory((prev) => (prev ? { ...prev, ...patch } : prev));
    setMemorySettings(patch);
  };

  if (!memory) return <Screen contentStyle={screenRhythm(tokens)}><ScreenTitle>{t("flows.settings.history")}</ScreenTitle></Screen>;
  return (
    <Screen contentStyle={screenRhythm(tokens)}>
      <ScreenTitle>{t("flows.settings.history")}</ScreenTitle>
      <Section>
        <ListRow
          title={t("flows.history.autoTitles")}
          subtitle={t("flows.history.autoTitlesHint")} switch={{ value: memory.autoGenerateTitles, onValueChange: (v) => update({ autoGenerateTitles: v }) }}
        />
        <ListRow
          title={t("flows.history.autoSummarize")}
          subtitle={t("flows.history.autoSummarizeHint")} switch={{ value: memory.autoSummarize, onValueChange: (v) => update({ autoSummarize: v }) }}
        />
      </Section>

      {memory.autoSummarize && (
        <Section title={t("flows.history.summarizeAfter")}>
          <View accessibilityRole="radiogroup">
            {TURN_OPTIONS.map((n) => (
              <RadioRow
                key={n}
                title={t("flows.history.turns", { count: n })}
                selected={memory.historyTurnThreshold === n}
                onPress={() => update({ historyTurnThreshold: n })}
              />
            ))}
          </View>
        </Section>
      )}

      <Section title={t("flows.history.keep")} footer={t("flows.history.keepFooter")}>
        <View accessibilityRole="radiogroup">
          {SESSION_OPTIONS.map((n) => (
            <RadioRow
              key={n}
              title={n > 0 ? t("flows.settings.keepLast", { count: n }) : t("flows.settings.keepAll")}
              selected={memory.maxSavedSessions === n}
              onPress={() => update({ maxSavedSessions: n })}
            />
          ))}
        </View>
      </Section>

      {/* The entry is ghost + danger; the red fill is only on the confirm in the Sheet (Prism FL-18). */}
      <Button label={t("flows.history.clear")} variant="ghost" tone="danger" icon="trash-2" onPress={() => setClearOpen(true)} />

      <Sheet
        visible={clearOpen}
        onClose={() => setClearOpen(false)}
        title={t("flows.history.clearTitle")}
        description={t("flows.history.clearBody")}
        footer={
          <>
            <Button label={t("common.cancel")} variant="secondary" onPress={() => setClearOpen(false)} fullWidth />
            <Button
              label={t("flows.history.clear")}
              variant="destructive"
              fullWidth
              onPress={async () => {
                await clearAllHistory();
                getChatBridge().refreshSessions();
                setClearOpen(false);
                toast({ message: t("flows.history.cleared"), tone: "success" });
              }}
            />
          </>
        }
      />
    </Screen>
  );
}
