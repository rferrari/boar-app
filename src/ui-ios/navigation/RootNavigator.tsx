import React, { useEffect, useMemo, useState } from "react";
import { Dimensions, Platform } from "react-native";
import { DarkTheme, DefaultTheme, NavigationContainer, NavigationState, Theme, useNavigation } from "@react-navigation/native";
import { saveNavState, savedNavState } from "./navState";
import { createNativeStackNavigator, NativeStackNavigationProp } from "@react-navigation/native-stack";
import { createDrawerNavigator } from "@react-navigation/drawer";
import { SafeAreaView } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { useTranslation } from "react-i18next";
import { Tokens, useTokens } from "../theme";
import { ChatScreen } from "../ChatScreen";
import { SetupWizardScreen } from "../SetupWizardScreen";
import { SettingsScreen } from "../SettingsScreen";
import { SettingsHistoryScreen, SettingsLengthScreen, SettingsToneScreen } from "../SettingsSubscreens";
import { SettingsAssistantScreen } from "../SettingsAssistantScreen";
import { ModelsScreen, ModelSearchScreen } from "../ModelsScreen";
import { KnowledgeScreen } from "../KnowledgeScreen";
import { PerformanceScreen, PerformanceLogsScreen } from "../PerformanceScreen";
import { SystemDetailsScreen } from "../SystemDetailsScreen";
import { EvaluationScreen } from "../EvaluationScreen";
import { AboutScreen } from "../AboutScreen";
import { ComponentCatalogScreen } from "../dev/ComponentCatalogScreen";
import { AppDrawerContent } from "./AppDrawerContent";
import { useChatBridge } from "./chatBridge";
import type { DrawerParamList, RootStackParamList } from "./types";

const Stack = createNativeStackNavigator<RootStackParamList>();
const Drawer = createDrawerNavigator<DrawerParamList>();

type RootNav = NativeStackNavigationProp<RootStackParamList>;

function navigationTheme(t: Tokens): Theme {
  const base = t.scheme === "dark" ? DarkTheme : DefaultTheme;
  return {
    ...base,
    colors: {
      ...base.colors,
      primary: t.color.accent.solid,
      background: t.color.bg.canvas,
      card: t.color.bg.canvas,
      text: t.color.text.primary,
      border: t.color.line.hairline,
      notification: t.color.accent.solid,
    },
  };
}

/**
 * Screens that still draw their own header and close button (owned by Loom,
 * migrating to <Screen> + the native header). They get the system back
 * gesture and Android back from the stack; the safe area is padded here
 * until they move to <Screen>.
 */
function Legacy({ children }: { children: React.ReactNode }) {
  const t = useTokens();
  return (
    <SafeAreaView edges={["top", "bottom"]} style={{ flex: 1, backgroundColor: t.color.bg.canvas }}>
      {children}
    </SafeAreaView>
  );
}

function ChatRoute() {
  const navigation = useNavigation<RootNav>();
  return (
    // ChatScreen pads its own safe area (<Screen edges=all>, Quill's feat/ui-chat).
    <ChatScreen onRelaunchWizard={() => navigation.navigate("Setup")} />
  );
}

function SetupRoute() {
  const navigation = useNavigation<RootNav>();
  return (
    <SetupWizardScreen
      onReady={() => navigation.reset({ index: 0, routes: [{ name: "Main" }] })}
      onSkip={navigation.canGoBack() ? () => navigation.goBack() : undefined}
    />
  );
}

function EvaluationRoute() {
  const { generating } = useChatBridge();
  return <EvaluationScreen chatBusy={generating} />;
}

/**
 * Native header for the flow screens (Loom): the back button only. The screen title is drawn in the
 * content (ScreenTitle, title1 Baloo, like the setup steps) because iOS 26 left the native large title
 * empty whatever we tried (Prism/Harbor LT-1, tests 1-5). The bar draws no title at all, so the name is
 * read once, from the content title.
 * `large` is kept for the call sites; it no longer turns on a large title.
 */
function flowHeader(t: Tokens, title: string, _large = true) {
  return {
    headerShown: true,
    title,
    headerLargeTitle: false,
    headerShadowVisible: false,
    headerTintColor: t.color.accent.text,
    // No title in the bar on either platform: a transparent title still showed on Android and was read
    // twice by TalkBack (Prism DUP-2). The screen's name is the content title (first in reading order);
    // `title` stays on the route for the next screen's back button.
    // An empty string, not a null component: on Android `() => null` also dropped the back button
    // (Prism BK-1). With "" the bar keeps the back button and has no title node to read.
    headerTitle: "",
    headerBackVisible: true,
    headerStyle: { backgroundColor: t.color.bg.canvas },
    headerBackButtonDisplayMode: "minimal" as const,
  };
}

function MainDrawer() {
  const t = useTokens();
  return (
    <Drawer.Navigator
      drawerContent={(props) => <AppDrawerContent {...props} />}
      screenOptions={{
        headerShown: false,
        drawerType: "front",
        swipeEdgeWidth: 32,
        overlayColor: t.color.bg.scrim,
        drawerStyle: { width: 312, backgroundColor: t.color.bg.surface },
      }}
    >
      <Drawer.Screen name="Chat" component={ChatRoute} />
    </Drawer.Navigator>
  );
}

export function RootNavigator({ initialRoute }: { initialRoute: "Main" | "Setup" }) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const theme = useMemo(() => navigationTheme(t), [t]);
  // iOS: a live Dynamic Type change keeps the old text measurements (Prism FS-3, Harbor 51f9863:
  // clipped descenders, cut phrases; a cold launch at the same size is clean). Remount the tree on a
  // font-scale change; navState below puts the user back on the same screen. Android recreates the
  // Activity instead (FS-1 plan B), so this is iOS-only.
  const [fontEpoch, setFontEpoch] = useState(0);
  useEffect(() => {
    if (Platform.OS !== "ios") return;
    let scale = Dimensions.get("window").fontScale;
    const sub = Dimensions.addEventListener("change", ({ window }) => {
      if (Math.abs(window.fontScale - scale) < 0.01) return;
      scale = window.fontScale;
      setFontEpoch((e) => e + 1);
    });
    return () => sub.remove();
  }, []);
  return (
    // Restores the last screen when the tree mounts again, e.g. after Android recreates the
    // Activity on a system font change (FS-1): the JS context survives, the React tree does not.
    <NavigationContainer
      key={fontEpoch}
      theme={theme}
      initialState={savedNavState<NavigationState>()}
      onStateChange={saveNavState}
    >
      <StatusBar style={t.scheme === "dark" ? "light" : "dark"} />
      <Stack.Navigator
        initialRouteName={initialRoute}
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: t.color.bg.canvas },
          animation: "default",
        }}
      >
        {/* Main only ever arrives as the root (reset at the end of setup): it fades in, instead of the
            push slide that read as "forward" with nowhere to go back to (Iris TR-7). */}
        <Stack.Screen name="Main" component={MainDrawer} options={{ animation: "fade" }} />
        <Stack.Screen name="Setup" component={SetupRoute} options={{ gestureEnabled: false, animation: "fade" }} />
        <Stack.Screen name="Settings" component={SettingsScreen} options={flowHeader(t, tr("nav.settings"))} />
        <Stack.Screen name="SettingsTone" component={SettingsToneScreen} options={flowHeader(t, tr("flows.settings.tone"), false)} />
        <Stack.Screen name="SettingsLength" component={SettingsLengthScreen} options={flowHeader(t, tr("flows.settings.length"), false)} />
        <Stack.Screen name="SettingsHistory" component={SettingsHistoryScreen} options={flowHeader(t, tr("flows.settings.history"), false)} />
        <Stack.Screen name="SettingsAssistant" component={SettingsAssistantScreen} options={flowHeader(t, tr("flows.assistant.title"), false)} />
        <Stack.Screen name="Models" component={ModelsScreen} options={flowHeader(t, tr("flows.settings.models"))} />
        <Stack.Screen name="ModelSearch" component={ModelSearchScreen} options={flowHeader(t, tr("flows.models.searchTitle"), false)} />
        <Stack.Screen name="Knowledge" component={KnowledgeScreen} options={flowHeader(t, tr("nav.knowledge"))} />
        <Stack.Screen name="Performance" component={PerformanceScreen} options={flowHeader(t, tr("nav.performance"))} />
        <Stack.Screen name="PerformanceLogs" component={PerformanceLogsScreen} options={flowHeader(t, tr("flows.performance.logsTitle"), false)} />
        <Stack.Screen name="Evaluation" component={EvaluationRoute} options={flowHeader(t, tr("flows.performance.evaluationTitle"), false)} />
        <Stack.Screen name="SystemDetails" component={SystemDetailsScreen} options={flowHeader(t, tr("flows.system.title"), false)} />
        <Stack.Screen name="About" component={AboutScreen} options={flowHeader(t, tr("nav.about"))} />
        <Stack.Screen
          name="Catalog"
          component={ComponentCatalogScreen}
          options={{
            headerShown: true,
            title: tr("nav.catalog"),
            headerLargeTitle: true,
            headerShadowVisible: false,
            headerTintColor: t.color.accent.text,
            headerTitleStyle: { color: t.color.text.primary },
            headerStyle: { backgroundColor: t.color.bg.canvas },
          }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
