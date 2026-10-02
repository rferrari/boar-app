import React, { createContext, useCallback, useContext, useRef, useState } from "react";
import { AccessibilityInfo, Platform, StyleSheet, Text, View } from "react-native";

type Announce = (message: string, options?: { assertive?: boolean }) => void;

const AnnounceContext = createContext<Announce>(() => {});

/**
 * Screen-reader announcements. iOS uses announceForAccessibility; Android uses
 * an off-screen live-region node, since announceForAccessibility is deprecated
 * on Android 16. Mounted once in the app shell.
 */
export function AnnouncerProvider({ children }: { children: React.ReactNode }) {
  const [polite, setPolite] = useState("");
  const [assertive, setAssertive] = useState("");
  const toggle = useRef(false);
  const clearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const announce = useCallback<Announce>((message, options) => {
    if (!message) return;
    if (Platform.OS === "ios") {
      AccessibilityInfo.announceForAccessibilityWithOptions(message, { queue: !options?.assertive });
      return;
    }
    // The announce API still works below Android 16 (API 36); the live region covers 36+.
    if (typeof Platform.Version === "number" && Platform.Version < 36) {
      AccessibilityInfo.announceForAccessibility(message);
      return;
    }
    // Alternate a zero-width suffix so repeating the same message still changes the node.
    toggle.current = !toggle.current;
    const text = toggle.current ? message : `${message}​`;
    // Note: below API 36 `assertive` is not honored (announceForAccessibility has no priority).
    const set = options?.assertive ? setAssertive : setPolite;
    set(text);
    // Clear it so linear navigation doesn't land on a stale invisible node.
    if (clearTimer.current) clearTimeout(clearTimer.current);
    clearTimer.current = setTimeout(() => set(""), 2000);
  }, []);

  return (
    <AnnounceContext.Provider value={announce}>
      {children}
      {Platform.OS === "android" && (
        <View style={styles.hidden} pointerEvents="none" importantForAccessibility="yes">
          <Text accessibilityLiveRegion="polite">{polite}</Text>
          <Text accessibilityLiveRegion="assertive">{assertive}</Text>
        </View>
      )}
    </AnnounceContext.Provider>
  );
}

/** Returns `announce(message, { assertive })`. Announce state changes once, never per streamed token. */
export function useAnnounce(): Announce {
  return useContext(AnnounceContext);
}

const styles = StyleSheet.create({
  // Inside the viewport and not fully transparent: TalkBack may skip nodes it considers invisible.
  hidden: { position: "absolute", top: 0, left: 0, width: 1, height: 1, overflow: "hidden", opacity: 0.01 },
});

