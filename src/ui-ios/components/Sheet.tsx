import React, { useEffect, useMemo, useRef, useState } from "react";
import { AccessibilityInfo, Animated, findNodeHandle, Modal, Pressable, ScrollView, StyleSheet, useWindowDimensions, View } from "react-native";
import { KeyboardAvoidingView } from "react-native-keyboard-controller";
import { Gesture, GestureDetector, GestureHandlerRootView } from "react-native-gesture-handler";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useTranslation } from "react-i18next";
import { opacity, space, useTheme } from "../theme";
import { useMotion } from "../theme/motion";
import { IconButton } from "./IconButton";
import { Text } from "./Text";
import { sheetAnimates, sheetDragCloses, sheetTravel } from "./sheetMotion";

export interface SheetProps {
  visible: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children?: React.ReactNode;
  /** Sticky footer actions, listed safest first (Cancel before Delete); drawn bottom-up so the last one is on top. */
  footer?: React.ReactNode;
  /** Hide the close button, e.g. for a forced choice. Back/scrim still close unless `dismissible` is false. */
  showClose?: boolean;
  dismissible?: boolean;
  /** Element to give screen-reader focus back to when the sheet closes (usually the trigger). */
  returnFocusRef?: React.RefObject<View | null>;
}

/**
 * Bottom sheet for short tasks and confirmations (destructive actions, pickers).
 * Modal: traps focus, closes on Android back / scrim tap / close button, and
 * moves screen-reader focus to the title on open.
 */
export function Sheet({
  visible,
  onClose,
  title,
  description,
  children,
  footer,
  showClose = true,
  dismissible = true,
  returnFocusRef,
}: SheetProps) {
  const { tokens: t, reduceMotion } = useTheme();
  const { t: tr } = useTranslation();
  const insets = useSafeAreaInsets();
  const [mounted, setMounted] = useState(visible);
  const progress = useRef(new Animated.Value(0)).current;
  const titleRef = useRef<View>(null);
  const motion = useMotion();
  const windowHeight = useWindowDimensions().height;
  const [height, setHeight] = useState(0);
  const travel = sheetTravel(height, windowHeight, reduceMotion);
  // Built once per value (and travel), not on every render: a new interpolation rewires the native animated graph (perf audit #11).
  const slideY = useMemo(() => progress.interpolate({ inputRange: [0, 1], outputRange: [travel, 0] }), [progress, travel]);
  // The grabber and header drag the sheet down (TR-12); dragY adds to the open/close slide.
  const dragY = useRef(new Animated.Value(0)).current;
  const offsetY = useMemo(() => Animated.add(slideY, dragY), [slideY, dragY]);
  const drag = useMemo(
    () =>
      Gesture.Pan()
        .runOnJS(true)
        .enabled(dismissible)
        .activeOffsetY(8)
        .failOffsetX([-24, 24])
        .onUpdate((e) => dragY.setValue(Math.max(0, e.translationY)))
        .onEnd((e) => {
          if (sheetDragCloses(e.translationY, e.velocityY, height)) onClose();
          else Animated.spring(dragY, { toValue: 0, ...t.motion.spring, useNativeDriver: true }).start();
        }),
    [dismissible, dragY, height, onClose, t.motion.spring]
  );

  useEffect(() => {
    // Mounted closed (a row's confirm sheet, the setup pickers): nothing on screen to animate out,
    // and no focus to hand back. Without this, every closed Sheet ran a native animation on mount
    // and then moved screen-reader focus to its trigger (perf audit #12).
    if (!sheetAnimates(visible, mounted)) return;
    if (visible) {
      setMounted(true);
      dragY.setValue(0);
    }
    // enter 220 decelerate / exit 150 accelerate; reduce motion: a 90 ms fade, no slide (DS §6).
    Animated.timing(progress, {
      toValue: visible ? 1 : 0,
      ...motion.timing(visible ? "enter" : "exit"),
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (finished && !visible) {
        setMounted(false);
        const trigger = returnFocusRef?.current && findNodeHandle(returnFocusRef.current);
        if (trigger) setTimeout(() => AccessibilityInfo.setAccessibilityFocus(trigger), 50);
      }
      if (finished && visible) {
        const node = titleRef.current && findNodeHandle(titleRef.current);
        if (node) AccessibilityInfo.setAccessibilityFocus(node);
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, progress, motion]);

  if (!mounted) return null;
  const close = () => dismissible && onClose();

  return (
    <Modal transparent visible statusBarTranslucent navigationBarTranslucent animationType="none" onRequestClose={close}>
      {/* Android draws a Modal in its own window: gestures need their own root there. */}
      <GestureHandlerRootView style={StyleSheet.absoluteFill}>
        <Animated.View style={[StyleSheet.absoluteFill, { backgroundColor: t.color.bg.scrim, opacity: progress }]}>
          <Pressable
            style={StyleSheet.absoluteFill}
            onPress={close}
            accessibilityRole="button"
            accessibilityLabel={tr("ui.close")}
            importantForAccessibility="no"
            accessibilityElementsHidden
          />
        </Animated.View>
        <KeyboardAvoidingView behavior="padding" style={styles.anchor} pointerEvents="box-none">
          <Animated.View
            accessibilityViewIsModal
            onLayout={(e) => setHeight(e.nativeEvent.layout.height)}
            style={{
              maxHeight: "90%",
              backgroundColor: t.color.bg.raised,
              borderTopLeftRadius: t.radius.xl,
              borderTopRightRadius: t.radius.xl,
              borderWidth: t.size.hairline,
              borderColor: t.color.line.hairline,
              paddingBottom: Math.max(insets.bottom, t.space.base),
              transform: [{ translateY: offsetY }],
              // Under reduce motion the sheet does not slide; it fades with the scrim.
              opacity: reduceMotion ? progress : 1,
              ...(t.elevation[3] as object),
            }}
          >
            <GestureDetector gesture={drag}>
              {/* Grabber + header: drag down to close (TR-12). */}
              <View>
                <View style={[styles.grabber, { backgroundColor: t.color.line.strong }]} />
                <View style={[styles.header, { paddingHorizontal: t.space.base, gap: t.space.sm }]}>
                  <View ref={titleRef} accessible style={{ flex: 1, gap: t.space.xs, paddingTop: t.space.sm }}>
                    <Text variant="title3" header>
                      {title}
                    </Text>
                    {description && (
                      <Text variant="callout" color="secondary">
                        {description}
                      </Text>
                    )}
                  </View>
                  {showClose && dismissible && <IconButton icon="x" label={tr("ui.close")} onPress={onClose} />}
                </View>
              </View>
            </GestureDetector>
            {children && (
              <ScrollView
                style={{ flexGrow: 0 }}
                contentContainerStyle={{ paddingHorizontal: t.space.base, paddingTop: t.space.md, gap: t.space.md }}
                keyboardShouldPersistTaps="handled"
              >
                {children}
              </ScrollView>
            )}
            {footer && (
              // Footer children are given safest-first (Cancel, then the action) for focus order,
              // and drawn in reverse so the main action sits on top.
              <View style={{ flexDirection: "column-reverse", paddingHorizontal: t.space.base, paddingTop: t.space.base, gap: t.space.sm }}>
                {footer}
              </View>
            )}
          </Animated.View>
        </KeyboardAvoidingView>
      </GestureHandlerRootView>
    </Modal>
  );
}

/** The grabber bar: the platform sheets' 36 pt handle. */
const GRABBER_WIDTH = 36;

const styles = StyleSheet.create({
  anchor: { flex: 1, justifyContent: "flex-end" },
  grabber: { alignSelf: "center", width: GRABBER_WIDTH, height: space.xs, borderRadius: space.xxs, marginTop: space.sm, opacity: opacity.pressed },
  header: { flexDirection: "row", alignItems: "flex-start" },
});
