import { useEffect, useState } from "react";
import { StatusBar } from "expo-status-bar";
import { useFonts } from "expo-font";
import { StyleSheet, ActivityIndicator, View } from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import "../i18n";
import { LanguageProvider } from "../i18n/LanguageContext";
import { ChatScreen } from "./ChatScreen";
import { ModelSetupScreen } from "./ModelSetupScreen";
import { ModelManager } from "../models/ModelManager";
import { ThemeProvider, useTheme } from "./theme";
import { initHaptics } from "../services/haptics";
import { FONT_FILES } from "./theme/fontFiles";
import { registerGeoProviders } from "../routing/answerService";
import { geoProvidersFrom } from "../routing/geoWiring";
import { getCurrentPoint, getLocationFix } from "../services/location";
import { installedPoiPacks, resolvePlace, searchPois } from "../rag/pois";
import { POI_REGIONS } from "../rag/poiRegions";

// Offline places for the chat's answer() (and the in-app evaluation): without this every places question
// answers "places pack not installed", even with a pack installed. Same wiring as BOAR Bench's runner, but with
// the app's location service, which never asks for permission: the Android UI has no permission flow yet, so
// until the user grants location elsewhere, "near me" gets the engine's no-location answer and a named city
// ("vegan food in Lisbon") works from the installed pack without GPS.
registerGeoProviders(
  geoProvidersFrom({
    installedPoiPacks,
    getCurrentPoint,
    getLocationFix,
    resolvePlace,
    searchPois,
    cities: () => POI_REGIONS.flatMap((r) => r.cities ?? []),
  })
);

const modelManager = new ModelManager();

type Screen = "checking" | "required-setup" | "chat";

function AppContent() {
  const [screen, setScreen] = useState<Screen>("checking");
  const { colors } = useTheme();
  // Bundled files, so this is quick; on an error the text falls back to the system font.
  const [fontsLoaded, fontError] = useFonts(FONT_FILES);
  const fontsReady = fontsLoaded || fontError !== null;

  useEffect(() => {
    initHaptics();
  }, []);

  useEffect(() => {
    (async () => {
      const ready = await modelManager.requiredModelsPresent();
      setScreen(ready ? "chat" : "required-setup");
    })();
  }, []);

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.bg.terminal }]} edges={["top", "bottom"]}>
      <StatusBar style="light" />
      {(screen === "checking" || !fontsReady) && (
        <View style={styles.centered}>
          <ActivityIndicator color={colors.emerald[400]} size="large" />
        </View>
      )}
      {fontsReady && screen === "required-setup" && (
        <ModelSetupScreen mode="required" onReady={() => setScreen("chat")} />
      )}
      {fontsReady && screen === "chat" && (
        <ChatScreen onRelaunchWizard={() => setScreen("required-setup")} />
      )}
    </SafeAreaView>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <LanguageProvider>
        <ThemeProvider>
          <AppContent />
        </ThemeProvider>
      </LanguageProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
