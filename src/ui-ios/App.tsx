import { useCallback, useEffect, useRef, useState } from "react";
import { InteractionManager, StyleSheet, View } from "react-native";
import * as SplashScreen from "expo-splash-screen";
import { useFonts } from "expo-font";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { KeyboardProvider } from "react-native-keyboard-controller";
import { initialWindowMetrics, SafeAreaProvider } from "react-native-safe-area-context";
import "./i18n";
import { LanguageProvider } from "../i18n/LanguageContext";
import { ModelManager } from "../models/ModelManager";
import { ThemeProvider, useTheme, useTokens } from "./theme";
import { FONT_FILES } from "./theme/fontFiles";
import { AnnouncerProvider, ToastProvider } from "./components";
import { RootNavigator } from "./navigation/RootNavigator";
import { initHaptics } from "../services/haptics";
import { initialRoute as bootRoute } from "./flows/boot";
import { afterFirstFrame } from "./flows/afterFirstFrame";
import { registerGeoProviders } from "../routing/answerService";
import { geoProvidersFrom } from "../routing/geoWiring";
import { getCurrentPoint, getLocationFix } from "../services/location";
import { installedPoiPacks, loadTileCatalog, resolvePlace, searchPois } from "../rag/pois";
import { POI_REGIONS } from "../rag/poiRegions";

// Offline places: without this, every places question answers "places pack not installed".
registerGeoProviders(
  geoProvidersFrom({
    installedPoiPacks,
    getCurrentPoint,
    getLocationFix,
    resolvePlace,
    searchPois,
    cities: () => POI_REGIONS.flatMap((r) => r.cities),
  })
);

const modelManager = new ModelManager();

// Keep the native splash (same canvas, mascot and wordmark) up until the first real screen can
// draw, instead of flashing a bare spinner between the two (iOS cd50fdc splash sequence).
SplashScreen.preventAutoHideAsync().catch(() => {});
// Cut, don't fade: the native splash faded over the first screen and its big boar crossed the loading
// chat's smaller one for ~4 frames (Prism, Android 8dc234e). On iOS the art under it is the same image.
SplashScreen.setOptions({ fade: false });
// Boot timing (splash decision): how long the native splash covers the JS start. Read in logcat / Xcode.
const BOOT_T0 = Date.now();
console.info(`[boot] js-start t=${BOOT_T0}`);
// The JS context survives an Activity recreation (font scale change), so later mounts of the tree are
// not boots: they are timed from their own mount and tagged via=recreate (Piston PERF-4).
let bootMounts = 0;

function AppContent() {
  const t = useTokens();
  // Saved theme, text size and reduce motion first: the first screen draws in them, not in the defaults (TR-13).
  const { loaded: themeLoaded } = useTheme();
  const [initialRoute, setInitialRoute] = useState<"Main" | "Setup" | null>(null);
  // Brand fonts are bundled; this resolves from local assets. On error, fall
  // back to system fonts rather than blocking the app.
  const [fontsLoaded, fontError] = useFonts(FONT_FILES);

  useEffect(() => {
    initHaptics();
    (async () => {
      // Setup unless the chat has an answer model it can load (and saves that one as active).
      // A failed disk read also lands in setup, never on a spinner or a chat that cannot answer.
      setInitialRoute(await bootRoute(modelManager).catch(() => "Setup" as const));
    })();
  }, []);

  const ready = !!initialRoute && (fontsLoaded || !!fontError) && themeLoaded;
  // The native splash stays until the first screen can draw, then cuts straight to it. BootSplash (the
  // same art + tagline + bar) was measured on screen for 0-90 ms on iOS and Android (Harbor, Piston,
  // 1c09215): too short to read, it only flashed the tagline. Kept in src/ui/flows for a slower boot.
  const nativeHidden = useRef(false);
  const mount = useRef<{ t0: number; recreate: boolean } | null>(null);
  if (!mount.current) {
    const recreate = bootMounts++ > 0;
    mount.current = { t0: recreate ? Date.now() : BOOT_T0, recreate };
  }
  // `via` says which path released the native splash: "image" (BootSplash drew) or "ready" (the app was
  // ready first, so BootSplash never showed). Ready minus hide = how long BootSplash was on screen.
  const hideNative = useCallback((via: "image" | "ready") => {
    if (nativeHidden.current) return;
    nativeHidden.current = true;
    // Boot timing for Tusk's measurements (the native splash is released here).
    const m = mount.current!;
    console.info(`[boot] hide after=${Date.now() - m.t0}ms via=${m.recreate ? "recreate" : via}`);
    SplashScreen.hideAsync().catch(() => {});
  }, []);
  useEffect(() => {
    if (!ready) return;
    const m = mount.current!;
    console.info(`[boot] ready after=${Date.now() - m.t0}ms${m.recreate ? " via=recreate" : ""}`);
    hideNative("ready");
  }, [ready, hideNative]);
  // The tile index lives in the gazetteer: without it a tile is neither downloadable nor importable. It
  // registers after the first screen is up (8,374 tiles in the world gazetteer), still before anything the
  // person can import; installing the gazetteer reloads it itself (rag/pois onAssetInstalled).
  useEffect(() => {
    if (!ready) return;
    return afterFirstFrame(
      () => void loadTileCatalog().catch((e) => console.warn("[pois] tile index:", e?.message ?? e)),
      {
        nextFrame: requestAnimationFrame,
        cancelFrame: cancelAnimationFrame,
        afterInteractions: (task) => InteractionManager.runAfterInteractions(task),
      }
    );
  }, [ready]);

  if (!ready) {
    // Under the native splash: the same canvas colour, in case the OS reveals this frame.
    return <View style={[styles.centered, { backgroundColor: t.color.bg.canvas }]} />;
  }
  return <RootNavigator initialRoute={initialRoute!} />;
}

export default function App() {
  return (
    <GestureHandlerRootView style={styles.root}>
      {/* Real insets on the first frame, so the shell does not jump as the splash fades (Prism SA-1). */}
      <SafeAreaProvider initialMetrics={initialWindowMetrics}>
        <KeyboardProvider>
          <LanguageProvider>
            <ThemeProvider>
              <AnnouncerProvider>
                <ToastProvider>
                  <AppContent />
                </ToastProvider>
              </AnnouncerProvider>
            </ThemeProvider>
          </LanguageProvider>
        </KeyboardProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  centered: { flex: 1, alignItems: "center", justifyContent: "center" },
});
