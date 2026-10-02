import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { Animated, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { notification, NotificationFeedbackType } from "../../services/haptics";
import { icon as iconTokens, toneColors, useTheme } from "../theme";
import { useAnnounce } from "./Announcer";
import { Button } from "./Button";
import { Icon, IconName } from "./Icon";
import { useOpticalLine } from "./IconText";
import { Text } from "./Text";
import { useMotion } from "../theme/motion";
import { queueToast, ToastQueue, toastLeft } from "./toastQueue";

export interface ToastOptions {
  message: string;
  tone?: "neutral" | "success" | "danger";
  icon?: IconName;
  /** e.g. "Undo". The toast stays at least 6s when it has an action. */
  actionLabel?: string;
  onAction?: () => void;
  /** Override the reading-time based duration (ms). */
  duration?: number;
}

type Show = (options: ToastOptions) => void;
const ToastContext = createContext<Show>(() => {});

/** Minimum on-screen time: 5s base + ~60ms per character; 6s floor with an action. */
export function toastDuration(message: string, hasAction: boolean): number {
  return Math.max(hasAction ? 6000 : 5000, 5000 + message.length * 60);
}

/** Mounted once in the shell. `const toast = useToast(); toast({ message, actionLabel: t("ui.undo"), onAction })`. */
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [queue, setQueue] = useState<ToastQueue<ToastOptions & { id: number }>>({ current: null, pending: null });
  const idRef = useRef(0);
  const show = useCallback<Show>((options) => {
    idRef.current += 1;
    const next = { ...options, id: idRef.current };
    setQueue((q) => queueToast(q, next));
  }, []);
  const onDone = useCallback(() => setQueue(toastLeft), []);
  const current = queue.current;
  return (
    <ToastContext.Provider value={show}>
      {children}
      {current && <ToastView key={current.id} toast={current} replaced={queue.pending !== null} onDone={onDone} />}
    </ToastContext.Provider>
  );
}

export function useToast(): Show {
  return useContext(ToastContext);
}

function ToastView({ toast, replaced, onDone }: { toast: ToastOptions; replaced: boolean; onDone: () => void }) {
  const { tokens: t } = useTheme();
  const m = useMotion();
  const insets = useSafeAreaInsets();
  const announce = useAnnounce();
  const anim = useRef(new Animated.Value(0)).current;
  // Built once per value (and width), not on every render: a new interpolation rewires the native animated graph (perf audit #11).
  const travel = m.spec("enter").travel;
  const riseY = useMemo(() => anim.interpolate({ inputRange: [0, 1], outputRange: [travel, 0] }), [anim, travel]);
  const tone = toast.tone ?? "neutral";
  const tc = toneColors(t.color, tone === "neutral" ? "neutral" : tone);
  const line = useOpticalLine("callout");

  // Leaves with `exit` (150 ms, accelerate, back down); replaced by a newer toast, the short swap exit (90 ms).
  const leaving = useRef(false);
  const hide = useCallback(
    (swap = false) => {
      if (leaving.current) return;
      leaving.current = true;
      Animated.timing(anim, { toValue: 0, ...m.timing("exit", { swap }), useNativeDriver: true }).start(() => onDone());
    },
    [anim, onDone, m]
  );
  useEffect(() => {
    if (replaced) hide(true);
  }, [replaced, hide]);

  useEffect(() => {
    announce(toast.actionLabel ? `${toast.message}. ${toast.actionLabel}` : toast.message, { assertive: tone === "danger" });
    if (tone === "danger") notification(NotificationFeedbackType.Error);
    Animated.timing(anim, { toValue: 1, ...m.timing("enter"), useNativeDriver: true }).start();
    const timer = setTimeout(() => hide(), toast.duration ?? toastDuration(toast.message, !!toast.actionLabel));
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View pointerEvents="box-none" style={[StyleSheet.absoluteFill, { justifyContent: "flex-end" }]}>
      <Animated.View
        style={{
          marginHorizontal: t.space.base,
          marginBottom: insets.bottom + 88,
          flexDirection: "row",
          alignItems: "center",
          gap: iconTokens.gap,
          paddingLeft: t.space.md,
          paddingRight: toast.actionLabel ? t.space.xs : t.space.base,
          paddingVertical: t.space.xs,
          minHeight: t.size.row,
          borderRadius: t.radius.md,
          backgroundColor: t.color.bg.raised,
          borderWidth: t.scheme === "light" ? t.size.hairline : 0,
          borderColor: t.color.line.hairline,
          opacity: anim,
          transform: [{ translateY: riseY }],
          ...(t.elevation[3] as object),
        }}
      >
        {(toast.icon || tone !== "neutral") && (
          // A wrapped message keeps the disc beside its first line (icon-align rule 1): a slot as tall
          // as that line plus the text's padding, nudged onto the line's optical centre.
          <View style={{ alignSelf: "flex-start", height: line.lineHeight + 2 * t.space.sm, justifyContent: "center", transform: [{ translateY: line.offset }] }}>
            <View style={{ width: 32, height: 32, borderRadius: 16, alignItems: "center", justifyContent: "center", backgroundColor: tone === "neutral" ? t.color.accent.soft : tc.bg }}>
              <Icon name={toast.icon ?? (tone === "danger" ? "x" : "check")} size={iconTokens.sizeBody} color={tone === "neutral" ? t.color.accent.text : tc.fg} />
            </View>
          </View>
        )}
        <Text variant="callout" style={{ flex: 1, paddingVertical: t.space.sm }}>
          {toast.message}
        </Text>
        {toast.actionLabel && toast.onAction && (
          <Button
            label={toast.actionLabel}
            variant="ghost"
            size="sm"
            onPress={() => {
              toast.onAction?.();
              hide();
            }}
          />
        )}
      </Animated.View>
    </View>
  );
}
