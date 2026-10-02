/**
 * The JS continuation of the native splash (FIDELITY, Iris): the same image,
 * at the same place, so the hand-over does not move, plus what the native
 * splash can't show: the tagline, an indeterminate bar and an honest status
 * while the boot reads the disk. Rendered by App.tsx only while the initial
 * route is unknown. onFirstLayout (name kept for App.tsx) fires once the splash image is decoded, and App.tsx hides the native splash then.
 */
import React, { useEffect, useMemo, useRef, useState } from "react";
import { Animated, Dimensions, Image, PixelRatio, Platform, useWindowDimensions, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Progress, Text } from "../components";
import { buildTokens, useTheme } from "../theme";

// The full art (Iris 227c6c6): one 360×366 pt image, centred in the window. It is the iOS native splash;
// on Android 12+ the native splash is the icon below, and this art arrives by a fade.
const SPLASH_W = 360;
const SPLASH_H = 366;
// Android 12+ splash icon box (app.json android.imageWidth 240): the boar alone, inside the safe circle.
const ANDROID_ICON = 240;
// The mockup's splash (393×852): tagline at y571, i.e. 145 pt below the window's centre;
// bar 140×4 at y762 and status at y776, i.e. 90 and 76 pt from the bottom.
const TAGLINE_FROM_CENTRE = 145;
// Image edges on whole device pixels: a half-pixel edge drew a 1 px line at x≈360 pt (Prism, 8863ef3).
const px = (v: number) => PixelRatio.roundToNearestPixel(v);
const BAR_W = 140;
const BAR_FROM_BOTTOM = 90;
const STATUS_FROM_BOTTOM = 76;

export function BootSplash({ onFirstLayout, textReady = true }: { onFirstLayout?: () => void; textReady?: boolean }) {
  const { t } = useTranslation();
  const { reduceMotion } = useTheme();
  // Always the native splash's colours (dark Fogueira, #17110D), whatever theme the user picked:
  // the opaque image is composed on that canvas, so any other background would show a seam.
  const tokens = useMemo(() => buildTokens("dark", "standard", "fogueira"), []);
  // Android 12+ draws its splash on a full-screen window (behind the system bars) and centres the icon there;
  // with edge-to-edge, the window size can come back without the navigation bar, so Android centres on the
  // screen (Iris). iOS centres on the window, like its storyboard.
  const win = useWindowDimensions();
  const screen = Dimensions.get("screen");
  const { width, height } = Platform.OS === "android" ? screen : win;
  // Android 12+ shows only the boar in the system's 240 dp icon box (Iris 794efe9): this starts as that same
  // image, then fades to the full art with the tagline and bar. iOS starts on the full art, like its native splash.
  const startsAsIcon = Platform.OS === "android";
  const art = useRef(new Animated.Value(startsAsIcon ? 0 : 1)).current;
  // Built once per value (and width), not on every render: a new interpolation rewires the native animated graph (perf audit #11).
  const iconFade = useMemo(() => art.interpolate({ inputRange: [0, 1], outputRange: [1, 0] }), [art]);
  const [iconShown, setIconShown] = useState(!startsAsIcon);
  const [artLoaded, setArtLoaded] = useState(false);
  useEffect(() => {
    if (!startsAsIcon || !iconShown || !artLoaded) return;
    Animated.timing(art, {
      toValue: 1,
      duration: reduceMotion ? 0 : tokens.motion.duration.slow,
      easing: tokens.motion.easing.standard,
      useNativeDriver: true,
    }).start();
  }, [startsAsIcon, iconShown, artLoaded, reduceMotion, art, tokens]);
  const handOver = (onFirstLayout ?? (() => {})) as () => void;
  return (
    <View
      accessible
      accessibilityLabel={t("flows.onboarding.bootChecking")}
      style={{ flex: 1, backgroundColor: tokens.color.bg.canvas }}
    >
      {startsAsIcon && (
        // The system's icon: same image, same 240 dp box, centred in the window. The native splash hides once
        // it is decoded, so the hand-over does not move; then it fades out under the full art.
        <Animated.Image
          source={require("../../../assets/splash-icon-android.png")}
          resizeMode="contain"
          accessible={false}
          fadeDuration={0}
          onLoad={() => {
            setIconShown(true);
            handOver();
          }}
          onError={() => {
            setIconShown(true);
            handOver();
          }}
          style={{
            position: "absolute",
            width: ANDROID_ICON,
            height: ANDROID_ICON,
            left: px((width - ANDROID_ICON) / 2),
            top: px((height - ANDROID_ICON) / 2),
            opacity: iconFade,
          }}
        />
      )}
      <Animated.View pointerEvents="none" style={{ position: "absolute", left: 0, right: 0, top: 0, bottom: 0, opacity: art }}>
        {/* Same image, same size and place as the iOS native splash, so the hand-over does not move there.
            The native splash hides only once an image is decoded: hiding on the first layout showed a frame with
            the text and bar but no boar (Prism/Harbor, 37a935e). An error still hands over. */}
        {/* splash-bootsplash.png: the native art on the original #17110D base. The native file's base is tuned for
            the iOS image path and would sit 1-3 levels off this canvas through React Native (Iris 09dec4c, Prism). */}
        <Image
          source={require("../../../assets/splash-bootsplash.png")}
          resizeMode="contain"
          accessible={false}
          fadeDuration={0}
          onLoad={() => {
            setArtLoaded(true);
            if (!startsAsIcon) handOver();
          }}
          onError={() => {
            setArtLoaded(true);
            if (!startsAsIcon) handOver();
          }}
          style={{ position: "absolute", width: SPLASH_W, height: SPLASH_H, left: px((width - SPLASH_W) / 2), top: px((height - SPLASH_H) / 2) }}
        />
        {/* Text waits for the brand fonts, so it never swaps face on screen. */}
        {textReady && (
          <Text
            variant="subhead"
            align="center"
            style={{ position: "absolute", left: 0, right: 0, top: height / 2 + TAGLINE_FROM_CENTRE, color: tokens.color.field.text }}
          >
            {t("flows.onboarding.brandSub")}
          </Text>
        )}
        <View style={{ position: "absolute", width: BAR_W, left: (width - BAR_W) / 2, top: height - BAR_FROM_BOTTOM }}>
          {/* The splash colours, not the user theme, so the track never turns light (Iris 2342441). */}
          <Progress label={t("flows.onboarding.bootChecking")} height={tokens.space.xs} tokens={tokens} />
        </View>
        {textReady && (
          <Text
            variant="footnote"
            align="center"
            style={{ position: "absolute", left: 0, right: 0, top: height - STATUS_FROM_BOTTOM, color: tokens.color.text.secondary }}
          >
            {t("flows.onboarding.bootChecking")}
          </Text>
        )}
      </Animated.View>
    </View>
  );
}
