import React, { useEffect, useRef, useState } from "react";
import { Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from "react-native";
import { DrawerContentComponentProps, useDrawerStatus } from "@react-navigation/drawer";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { getMemorySettings, getShowLiveStats } from "../../models/settings";
import { impact, ImpactFeedbackStyle } from "../../services/haptics";
import { Button, IconButton, IconName, LARGE_TEXT_SCALE, ListRow, Mascot, Sheet, Text, useToast } from "../components";
import { useTokens } from "../theme";
import { useChatBridge } from "./chatBridge";
import { ActivityCard } from "../flows/ActivityCard";
import { useActivity } from "../flows/useActivity";
import { LiveStats } from "../flows/LiveStats";
import type { RootStackParamList } from "./types";

function relativeTime(ms: number, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const diffMin = (Date.now() - ms) / 60000;
  if (diffMin < 1) return t("time.justNow");
  if (diffMin < 60) return t("time.minutesAgo", { count: Math.floor(diffMin) });
  const diffHr = diffMin / 60;
  if (diffHr < 24) return t("time.hoursAgo", { count: Math.floor(diffHr) });
  return t("time.daysAgo", { count: Math.floor(diffHr / 24) });
}

type Destination = { route: keyof RootStackParamList; icon: IconName; label: string };

export function AppDrawerContent({ navigation }: DrawerContentComponentProps) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const insets = useSafeAreaInsets();
  // Large text: a pinned footer of three tall rows covered the end of the list (Prism AX-4), so the
  // destinations scroll with it instead.
  const { fontScale } = useWindowDimensions();
  const pinFooter = fontScale < LARGE_TEXT_SCALE;
  const toast = useToast();
  const status = useDrawerStatus();
  const chat = useChatBridge();
  const activity = useActivity();
  const [pendingDelete, setPendingDelete] = useState<{ id: string; title: string } | null>(null);
  const [maxSessions, setMaxSessions] = useState<number | null>(null);
  const [liveStats, setLiveStats] = useState(false);
  const trashRefs = useRef(new Map<string, View | null>());
  const returnFocusRef = useRef<View | null>(null);
  const newChatRef = useRef<View | null>(null);

  useEffect(() => {
    if (status !== "open") return;
    chat.refreshSessions();
    getMemorySettings()
      .then((m) => setMaxSessions(m.maxSavedSessions))
      .catch(() => {});
    getShowLiveStats()
      .then(setLiveStats)
      .catch(() => {});
    // Refresh each time the drawer opens, not on every bridge update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  const destinations: Destination[] = [
    { route: "Knowledge", icon: "book-open", label: tr("nav.knowledge") },
    { route: "Settings", icon: "sliders", label: tr("nav.settings") },
    { route: "Performance", icon: "activity", label: tr("nav.performance") },
  ];
  // The component catalog (route "Catalog") stays reachable in code, but not from the menu.

  const go = (fn: () => void) => {
    navigation.closeDrawer();
    fn();
  };

  const footer = (
    <View
      style={{
        borderTopWidth: t.size.hairline,
        borderTopColor: t.color.line.hairline,
        paddingBottom: pinFooter ? insets.bottom : 0,
        marginTop: pinFooter ? 0 : t.space.base,
      }}
    >
      {/* As in the original menu: the prompt ideas come back even after they were hidden. */}
      <ListRow title={tr("nav.promptIdeas")} icon="zap" chevron={false} onPress={() => go(chat.openPromptIdeas)} />
      {destinations.map((d) => (
        <ListRow
          key={d.route}
          title={d.label}
          icon={d.icon}
          chevron={false}
          onPress={() => go(() => navigation.getParent()?.navigate(d.route))}
        />
      ))}
      {liveStats && <LiveStats active={status === "open"} />}
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: t.color.bg.surface, paddingTop: insets.top }}>
      <View style={[styles.brand, { paddingHorizontal: t.space.base, paddingVertical: t.space.md, gap: t.space.md }]}>
        <Mascot size="brand" />
        <View style={{ flex: 1 }} accessible accessibilityRole="header">
          {/* The name as the chat header says it (a caps "BOAR" may be spelled out; Prism CH-33). */}
          <Text variant="title2" accessibilityLabel={tr("chat.assistantName")}>
            boar
          </Text>
          <Text variant="footnote" color="field">
            {tr("nav.subtitle")}
          </Text>
        </View>
      </View>

      <View style={{ paddingHorizontal: t.space.base, paddingBottom: t.space.md }}>
        <Button ref={newChatRef} label={tr("nav.newChat")} icon="edit-3" variant="secondary" fullWidth onPress={() => go(chat.newChat)} />
      </View>

      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: pinFooter ? t.space.base : insets.bottom }}>
        {/* Downloads and indexing keep going outside the setup; the menu shows them (and nothing when idle). */}
        <View style={{ paddingHorizontal: t.space.base }}>
          <ActivityCard
            activity={activity}
            onOpenDownloads={() => go(() => navigation.getParent()?.navigate("Models"))}
            onOpenIndex={() => go(() => navigation.getParent()?.navigate("Knowledge"))}
          />
        </View>
        <Text variant="label" color="tertiary" header style={{ paddingHorizontal: t.space.base, paddingVertical: t.space.sm }}>
          {tr("nav.recent")}
        </Text>
        {chat.sessions.length === 0 ? (
          <Text variant="footnote" color="tertiary" style={{ paddingHorizontal: t.space.base }}>
            {tr("nav.noHistory")}
          </Text>
        ) : (
          chat.sessions.map((s) => {
            const active = s.id === chat.activeSessionId;
            return (
              <View key={s.id} style={[styles.sessionRow, { paddingLeft: t.space.sm, paddingRight: t.space.xs }]}>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`${s.title}, ${relativeTime(s.updatedAt, tr)}`}
                  accessibilityState={{ selected: active }}
                  onPress={() => {
                    impact(ImpactFeedbackStyle.Light);
                    go(() => chat.selectSession(s.id));
                  }}
                  style={({ pressed }) => [
                    styles.sessionMain,
                    {
                      minHeight: t.size.touch,
                      paddingHorizontal: t.space.sm,
                      borderRadius: t.radius.sm,
                      backgroundColor: active ? t.color.accent.soft : pressed ? t.color.bg.sunken : "transparent",
                    },
                  ]}
                >
                  {/* Large text: two lines, so the title isn't only complete for screen readers (Prism CH-33). */}
                  <Text variant="callout" numberOfLines={pinFooter ? 1 : 2} weight={active ? "semibold" : "regular"}>
                    {s.title}
                  </Text>
                  <Text variant="caption" color="tertiary">
                    {relativeTime(s.updatedAt, tr)}
                  </Text>
                </Pressable>
                <IconButton
                  ref={(node) => {
                    trashRefs.current.set(s.id, node);
                  }}
                  icon="trash-2"
                  size="sm"
                  label={tr("nav.deleteChatA11y", { title: s.title })}
                  onPress={() => {
                    returnFocusRef.current = trashRefs.current.get(s.id) ?? null;
                    setPendingDelete({ id: s.id, title: s.title });
                  }}
                />
              </View>
            );
          })
        )}
        {maxSessions !== null && chat.sessions.length > 0 && (
          <Text variant="caption" color="tertiary" style={{ paddingHorizontal: t.space.base, paddingTop: t.space.sm }}>
            {tr("nav.keepingLast", { count: maxSessions })}
          </Text>
        )}
        {!pinFooter && footer}
      </ScrollView>

      {pinFooter && footer}

      <Sheet
        visible={pendingDelete !== null}
        onClose={() => setPendingDelete(null)}
        returnFocusRef={returnFocusRef}
        title={tr("nav.deleteChatTitle")}
        description={pendingDelete ? tr("nav.deleteChatBody", { title: pendingDelete.title }) : undefined}
        footer={
          <>
            <Button label={tr("ui.cancel")} variant="ghost" fullWidth onPress={() => setPendingDelete(null)} />
            <Button
              label={tr("nav.deleteChatConfirm")}
              variant="destructive"
              icon="trash-2"
              fullWidth
              onPress={() => {
                if (pendingDelete) chat.deleteSession(pendingDelete.id);
                // The trash button goes away with the row; land focus on "New chat" instead.
                returnFocusRef.current = newChatRef.current;
                setPendingDelete(null);
                toast({ message: tr("nav.chatDeleted"), icon: "trash-2" });
              }}
            />
          </>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  brand: { flexDirection: "row", alignItems: "center" },
  sessionRow: { flexDirection: "row", alignItems: "center", gap: 2 },
  sessionMain: { flex: 1, justifyContent: "center", paddingVertical: 6 },
});

