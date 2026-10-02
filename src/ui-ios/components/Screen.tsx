import React from "react";
import { Platform, StyleProp, View, ViewStyle } from "react-native";
import { KeyboardAwareScrollView } from "react-native-keyboard-controller";
import { Edge, SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import { useTokens } from "../theme";
import { Ambient } from "./Ambient";

export interface ScreenProps {
  children: React.ReactNode;
  /** Scrollable content (keyboard-aware). Default true. Use false for screens that own a FlatList. */
  scroll?: boolean;
  /**
   * Safe-area edges this screen pads. Screens under the native stack header
   * don't need "top" (the header handles it); the default is bottom + sides.
   */
  edges?: Edge[];
  /** Horizontal content padding. Default true. */
  padded?: boolean;
  contentStyle?: StyleProp<ViewStyle>;
  /** Sticky content at the bottom (primary action), above the home indicator. */
  footer?: React.ReactNode;
  /** Draw the identity's light pattern (ember / moon) behind the content. Hero screens: chat, onboarding. */
  ambient?: boolean;
  /** Center the content vertically when it is shorter than the screen (welcome, errors). Scrolls when taller. */
  center?: boolean;
  /** The scroll view, to bring something that just appeared at the end into view (scrollToEnd). */
  scrollRef?: React.RefObject<ScreenScroll | null>;
}

/** What a screen can ask of its scroll view. */
export interface ScreenScroll {
  scrollToEnd: (options?: { animated?: boolean }) => void;
}

/** Screen scaffold: canvas background, safe area, keyboard handling, content rhythm. */
/**
 * Space under a footer action. iOS: the mockup ends the action 30 pt above the edge, 4 pt into the
 * home-indicator area, which floats over content. Android: the navigation bar (3-button or gesture)
 * is a real bar, so the action sits fully above it (Prism AN-1: 4 pt of the CTA were under it).
 */
export function footerBottom(inset: number, xs: number, sm: number): number {
  return Platform.OS === "ios" ? Math.max(inset - xs, sm) : inset + sm;
}

export function Screen({ children, scroll = true, edges = ["bottom", "left", "right"], padded = true, contentStyle, footer, ambient, center, scrollRef }: ScreenProps) {
  const t = useTokens();
  const insets = useSafeAreaInsets();
  // With a footer, the footer owns the bottom inset: the mockup ends the action 30 pt above the
  // screen edge, i.e. 4 pt into the home-indicator area (34), not a full inset + padding below it.
  const padsBottom = edges.includes("bottom");
  const safeEdges = footer && padsBottom ? edges.filter((e) => e !== "bottom") : edges;
  const inner: ViewStyle = {
    paddingHorizontal: padded ? t.space.gutter : 0,
    paddingVertical: t.space.base,
    gap: t.space.xl,
    ...(center ? { flexGrow: 1, justifyContent: "center" } : null),
  };
  // Under a native large-title header, the scroll view must be the screen's first child for iOS to
  // show and collapse the title (wrapped in SafeAreaView, the title area stayed empty until scrolling,
  // Loom 99eba00). A plain scrolling screen is therefore the scroll view itself, with the safe-area
  // insets it needs applied to its content.
  if (scroll && !footer && !ambient) {
    // iOS: "automatic" already adds the header and safe-area insets to the content; add them by hand only on Android.
    const edge = (e: Edge, v: number) => (Platform.OS === "android" && edges.includes(e) ? v : 0);
    return (
      <KeyboardAwareScrollView
        ref={scrollRef as React.Ref<never>}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="interactive"
        contentInsetAdjustmentBehavior="automatic"
        bottomOffset={t.space.base}
        style={{ flex: 1, backgroundColor: t.color.bg.canvas }}
        contentContainerStyle={[
          inner,
          {
            paddingTop: t.space.base + edge("top", insets.top),
            paddingBottom: t.space.base + edge("bottom", insets.bottom),
            paddingLeft: (padded ? t.space.gutter : 0) + edge("left", insets.left),
            paddingRight: (padded ? t.space.gutter : 0) + edge("right", insets.right),
          },
          contentStyle,
        ]}
      >
        {children}
      </KeyboardAwareScrollView>
    );
  }
  return (
    <SafeAreaView edges={safeEdges} style={{ flex: 1, backgroundColor: t.color.bg.canvas }}>
      {ambient && <Ambient />}
      {scroll ? (
        <KeyboardAwareScrollView
          ref={scrollRef as React.Ref<never>}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentInsetAdjustmentBehavior="automatic"
          bottomOffset={t.space.base}
          contentContainerStyle={[inner, contentStyle]}
        >
          {children}
        </KeyboardAwareScrollView>
      ) : (
        <View style={[{ flex: 1 }, center && { justifyContent: "center" }, contentStyle]}>{children}</View>
      )}
      {footer && (
        <View
          style={{
            paddingHorizontal: t.space.gutter,
            paddingTop: t.space.md,
            paddingBottom: padsBottom ? footerBottom(insets.bottom, t.space.xs, t.space.sm) : t.space.sm,
            gap: t.space.sm,
            borderTopWidth: ambient ? 0 : t.size.hairline,
            borderTopColor: t.color.line.hairline,
            backgroundColor: ambient ? "transparent" : t.color.bg.canvas,
          }}
        >
          {footer}
        </View>
      )}
    </SafeAreaView>
  );
}

