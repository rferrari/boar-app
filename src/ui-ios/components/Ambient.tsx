import React from "react";
import { StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useTheme } from "../theme";
import { AMBIENT_LIGHT_STRENGTH, emberGradient, MOON_ALPHA } from "../theme/ambient";

const MOON_SIZE = 110;

/**
 * The identity's "pattern of light", drawn behind a screen's content:
 * Fogueira = ember glow rising from the bottom; Luar = a faint full moon at
 * the top right. Decorative (hidden from screen readers), static, so it
 * needs no reduce-motion handling. Uses RN's CSS radial-gradient
 * (`experimental_backgroundImage`); where unsupported it renders nothing.
 */
export function Ambient() {
  const { tokens: t, palette } = useTheme();
  const g = t.color.glow;
  const strength = t.scheme === "dark" ? 1 : AMBIENT_LIGHT_STRENGTH;
  const insets = useSafeAreaInsets();
  // The moon sits below the top bar (safe area + one touch-height row), so it never cuts the header's
  // controls in half (Prism LU-2); it peeks in from the right edge as in the mockup.
  const moonTop = insets.top + t.size.touch + t.space.md;
  return (
    <View
      pointerEvents="none"
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
      style={StyleSheet.absoluteFill}
    >
      {palette === "luar" ? (
        <View
          style={{
            position: "absolute",
            // 110 pt (the mockup's moon behind the hero), tucked into the right edge under the header: a
            // 220 pt disc reached into the chat's hero and read as a blob (Piston, Android Luar 35ffb87).
            right: -(t.space.xxxl),
            top: moonTop,
            width: MOON_SIZE,
            height: MOON_SIZE,
            borderRadius: 999,
            backgroundColor: t.color.moon,
            opacity: MOON_ALPHA,
          }}
        />
      ) : (
        <View
          style={{
            position: "absolute",
            left: -60,
            right: -60,
            bottom: -170,
            height: 440,
            opacity: strength,
            experimental_backgroundImage: emberGradient(g),
          }}
        />
      )}
    </View>
  );
}
