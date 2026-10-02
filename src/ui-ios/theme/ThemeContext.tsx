import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AccessibilityInfo, useColorScheme } from "react-native";
import * as SystemUI from "expo-system-ui";
import { selection } from "../../services/haptics";
import {
  Appearance,
  FontScale,
  PaletteChoice,
  getPaletteChoice,
  setPaletteChoice as persistPalette,
  ThemeId,
  getAppearance,
  getFontScale,
  getThemeId,
  setAppearance as persistAppearance,
  setFontScale as persistFontScale,
  setThemeId as persistThemeId,
} from "../../models/settings";
import { Colors, legacyColorsFromTokens } from "./colors";
import { DEFAULT_APPEARANCE, resolveScheme } from "./scheme";
import { buildTokens, ColorScheme, Tokens } from "./tokens";
import { getTypography, Typography } from "./typography";

interface ThemeContextType {
  /** Design tokens for the resolved scheme. Use these in new code. */
  tokens: Tokens;
  /** Resolved color scheme after applying `appearance` to the OS setting. */
  scheme: ColorScheme;
  appearance: Appearance;
  setAppearance: (appearance: Appearance) => Promise<void>;
  fontScale: FontScale;
  setFontScale: (scale: FontScale) => Promise<void>;
  /** Fogueira (default) or Luar. */
  palette: PaletteChoice;
  setPalette: (palette: PaletteChoice) => Promise<void>;
  /** OS "reduce motion" preference. Skip non-essential animation when true. */
  reduceMotion: boolean;
  /**
   * The saved appearance, text size and palette, and the OS reduce-motion flag, are read. The app
   * waits for it before its first screen, so a light-theme or large-text user never sees one frame
   * in the defaults (Iris TR-13).
   */
  loaded: boolean;

  /** @deprecated Legacy color shape bridged from `tokens`. Migrate to `tokens.color`. */
  colors: Colors;
  /** @deprecated Legacy type ramp. Migrate to `<Text variant>` / `tokens.type`. */
  typography: Typography;
  /** @deprecated The three dark themes were replaced by `appearance`. Kept so old settings screens compile. */
  themeId: ThemeId;
  /** @deprecated See `themeId`. */
  setTheme: (theme: ThemeId) => Promise<void>;
}

const defaultTokens = buildTokens("dark");

const ThemeContext = createContext<ThemeContextType>({
  tokens: defaultTokens,
  scheme: "dark",
  appearance: DEFAULT_APPEARANCE,
  setAppearance: async () => {},
  fontScale: "standard",
  setFontScale: async () => {},
  palette: "fogueira",
  setPalette: async () => {},
  reduceMotion: false,
  loaded: true,
  colors: legacyColorsFromTokens(defaultTokens.color),
  typography: getTypography("standard"),
  themeId: "midnight",
  setTheme: async () => {},
});

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const system = useColorScheme();
  const [appearance, setAppearanceState] = useState<Appearance>(DEFAULT_APPEARANCE);
  const [fontScale, setFontScaleState] = useState<FontScale>("standard");
  const [palette, setPaletteState] = useState<PaletteChoice>("fogueira");
  const [themeId, setThemeIdState] = useState<ThemeId>("midnight");
  const [reduceMotion, setReduceMotion] = useState(false);
  const [prefsRead, setPrefsRead] = useState(false);
  const [motionRead, setMotionRead] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [savedAppearance, savedScale, savedTheme, savedPalette] = await Promise.all([
          getAppearance(),
          getFontScale(),
          getThemeId(),
          getPaletteChoice(),
        ]);
        setPaletteState(savedPalette);
        setAppearanceState(savedAppearance);
        setFontScaleState(savedScale);
        setThemeIdState(savedTheme);
      } finally {
        // A failed read keeps the defaults; it must never hold the app behind the splash.
        setPrefsRead(true);
      }
    })();
  }, []);

  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled()
      .then(setReduceMotion)
      .catch(() => {})
      .finally(() => setMotionRead(true));
    const sub = AccessibilityInfo.addEventListener("reduceMotionChanged", setReduceMotion);
    return () => sub.remove();
  }, []);

  const scheme = resolveScheme(appearance, system);
  const tokens = useMemo(() => buildTokens(scheme, fontScale, palette), [scheme, fontScale, palette]);

  // Root view color shows during screen transitions and behind the keyboard.
  useEffect(() => {
    SystemUI.setBackgroundColorAsync(tokens.color.bg.canvas).catch(() => {});
  }, [tokens.color.bg.canvas]);

  const setAppearance = useCallback(async (next: Appearance) => {
    setAppearanceState(next);
    selection();
    await persistAppearance(next);
  }, []);

  const setFontScale = useCallback(async (next: FontScale) => {
    setFontScaleState(next);
    selection();
    await persistFontScale(next);
  }, []);

  const setPalette = useCallback(async (next: PaletteChoice) => {
    setPaletteState(next);
    selection();
    await persistPalette(next);
  }, []);

  const setTheme = useCallback(async (next: ThemeId) => {
    setThemeIdState(next);
    await persistThemeId(next);
  }, []);

  const colors = useMemo(() => legacyColorsFromTokens(tokens.color), [tokens.color]);
  const typography = useMemo(() => getTypography(fontScale), [fontScale]);

  const value = useMemo<ThemeContextType>(
    () => ({
      tokens,
      scheme,
      appearance,
      setAppearance,
      fontScale,
      setFontScale,
      palette,
      setPalette,
      reduceMotion,
      loaded: prefsRead && motionRead,
      colors,
      typography,
      themeId,
      setTheme,
    }),
    [tokens, scheme, appearance, setAppearance, fontScale, setFontScale, palette, setPalette, reduceMotion, prefsRead, motionRead, colors, typography, themeId, setTheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextType {
  return useContext(ThemeContext);
}

/** Shorthand for components that only need tokens. */
export function useTokens(): Tokens {
  return useContext(ThemeContext).tokens;
}
