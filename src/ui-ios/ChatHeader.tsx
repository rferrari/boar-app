import React, { memo, useState } from "react";
import { Pressable, useWindowDimensions, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Icon, IconButton, IconText, Mascot, OfflineSeal, Sheet, Text, useOpticalLine } from "./components";
import { useTokens } from "./theme";
import { headerFit, sealCopy } from "./chat/headerLayout";
import { lineSlop } from "./chat/touch";

// Which build this is (see docs/BUILD_VARIANTS.md on feat/trust-offline). Read the
// same inlined variable here until src/config/variant.ts is on main.
const OFFLINE_BUILD = process.env.EXPO_PUBLIC_BOAR_VARIANT?.trim().toLowerCase() === "offline";

interface Props {
  activeModelLabel?: string;
  /**
   * The model that will actually answer is not the one saved (CR-1: a large model on a low-RAM
   * phone without confirmation, or one that closed the app). The header names the real one and
   * the model line opens Models.
   */
  downgradedFrom?: { label: string; reason: "low-ram" | "load-crashed" };
  onOpenModels?: () => void;
  voiceEnabled: boolean;
  onOpenDrawer: () => void;
}

/** The model line under "boar" grows with the text up to here (Prism AX-5: at AX-XXL it covered the answer). */
const HEADER_META_MAX_SCALE = 1.5;

/**
 * Chat top bar, as the mockup: menu, avatar, name + model, and the offline
 * badge (tap for what "offline" means in this build). New chat lives in the
 * drawer (first item), per Boar's fidelity decision. Tone lives in Settings > Personality (the mockup
 * has no tone button, and it cost the seal its text on 393-412pt phones). Name and model always stay readable:
 * when the width gets tight (see headerFit) the seal keeps only its icon,
 * then the avatar goes, so large text never wraps or swallows the title.
 * Memoized: the chat screen re-renders on every streamed frame, the header only when its props change.
 */
export const ChatHeader = memo(function ChatHeader({ activeModelLabel, downgradedFrom, onOpenModels, voiceEnabled, onOpenDrawer }: Props) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const { width, fontScale } = useWindowDimensions();
  const [offlineOpen, setOfflineOpen] = useState(false);
  // "OFFLINE" like the mockup only where it is literally true (the build without INTERNET); the downloader
  // build says "ON DEVICE" (Boar: the answer is computed on the phone), same pill. Readers hear the long form.
  const seal = sealCopy(OFFLINE_BUILD);
  // The downgraded-model line is a caps caption: its touch area comes up to the minimum (Prism CH-5).
  const metaLine = useOpticalLine("capsMeta", undefined, HEADER_META_MAX_SCALE);
  const sealLabel = tr(seal.pill);
  const sealSpoken = tr(seal.pillSpoken);
  // The mockup's header: padding 4/16/10, 10 between items, 42 pt discs (touch comes from hitSlop).
  const itemGap = t.space.sm + t.space.xxs;
  const fit = headerFit({
    width,
    fontScale,
    touch: t.size.headerDisc,
    buttons: 1,
    // Row padding + the gaps menu|title and title|seal; the avatar brings its own gap.
    chrome: t.space.gutterChat * 2 + itemGap * 2,
    avatar: t.size.avatar + itemGap,
    sealChars: sealLabel.length,
  });

  return (
    <View
      // Top-aligned: with large text the title block grows downward, never above the menu (Prism AX-1).
      style={{
        flexDirection: "row",
        alignItems: "flex-start",
        gap: itemGap,
        paddingHorizontal: t.space.gutterChat,
        paddingTop: t.space.xs,
        paddingBottom: itemGap,
      }}
    >
      <IconButton icon="menu" variant="surface" size="header" label={tr("chat.header.menu")} onPress={onOpenDrawer} />
      <View style={{ flex: 1, flexDirection: "row", alignItems: "flex-start", gap: itemGap }}>
        {fit.avatar && <Mascot size="avatar" />}
        <View style={{ flexShrink: 1, flexGrow: 1, minHeight: t.size.headerDisc, justifyContent: "center", gap: t.space.xxs }}>
          <Text variant="title2" header numberOfLines={1}>
            boar
          </Text>
          {activeModelLabel && !downgradedFrom && (
            // The mockup's model line: caps, secondary, raised from 9.5 px to the 12 pt floor.
            // Prism AX-5: capped at 1.5x, so at AX-XXL the model line stays inside the header.
            <Text variant="capsMeta" color="secondary" numberOfLines={1} maxFontSizeMultiplier={HEADER_META_MAX_SCALE}>
              {activeModelLabel}
            </Text>
          )}
          {activeModelLabel && downgradedFrom && (
            <Pressable
              onPress={onOpenModels}
              accessibilityRole="button"
              accessibilityLabel={tr(`chat.header.downgraded.${downgradedFrom.reason}`, { model: activeModelLabel, from: downgradedFrom.label })}
              accessibilityHint={tr("chat.header.downgraded.hint")}
              hitSlop={lineSlop(t.size.touch, metaLine.lineHeight, t.space.sm)}
            >
              {/* Icon-align (Iris): gap 8, seal-sized info icon on the caps line. */}
              <IconText
                icon="info"
                iconPosition="end"
                variant="capsMeta"
                color="secondary"
                iconColor={t.color.text.secondary}
                iconRole="seal"
                numberOfLines={1}
                maxFontSizeMultiplier={HEADER_META_MAX_SCALE}
              >
                {activeModelLabel}
              </IconText>
            </Pressable>
          )}
        </View>
        <Pressable
          onPress={() => setOfflineOpen(true)}
          accessibilityRole="button"
          accessibilityLabel={tr(seal.button)}
          style={{ minHeight: t.size.touch, minWidth: t.size.touch, alignItems: "center", justifyContent: "center" }}
        >
          {fit.seal === "text" ? (
            <OfflineSeal label={sealLabel} accessibilityLabel={sealSpoken} />
          ) : (
            <View style={{ padding: t.space.sm, borderRadius: t.radius.full, backgroundColor: t.color.field.soft }}>
              <Icon name={seal.icon} size="sm" color={t.color.field.text} />
            </View>
          )}
        </Pressable>
      </View>

      <Sheet visible={offlineOpen} onClose={() => setOfflineOpen(false)} title={tr(seal.title)}>
        <View style={{ gap: t.space.md }}>
          <Text color="secondary">{tr(seal.body)}</Text>
          {activeModelLabel && (
            <Text variant="footnote" color="secondary">
              {tr("chat.header.modelLoaded", { label: activeModelLabel })}
            </Text>
          )}
          {voiceEnabled && (
            <Text variant="footnote" color="secondary">
              {tr("chat.header.voiceCaveat")}
            </Text>
          )}
        </View>
      </Sheet>
    </View>
  );
});
