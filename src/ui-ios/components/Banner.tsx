import React from "react";
import { View } from "react-native";
import { useTranslation } from "react-i18next";
import { icon as iconTokens, toneColors, useTokens } from "../theme";
import { Button } from "./Button";
import type { IconName } from "./Icon";
import { IconButton } from "./IconButton";
import { IconSlot, useOpticalLine } from "./IconText";
import { Text } from "./Text";

export type BannerTone = "info" | "success" | "warning" | "danger" | "field";

export interface BannerProps {
  tone?: BannerTone;
  title?: string;
  message: string;
  icon?: IconName;
  actionLabel?: string;
  onAction?: () => void;
  onDismiss?: () => void;
  dismissLabel?: string;
}

const DEFAULT_ICON: Record<BannerTone, IconName> = {
  info: "info",
  success: "check-circle",
  warning: "alert-triangle",
  danger: "alert-octagon",
  field: "shield",
};

/**
 * Inline, persistent notice inside the content flow (not floating). Errors
 * are announced assertively, everything else politely. The tone lives in the
 * soft fill and the icon (mockup "KEEP BOAR OPEN"); only danger adds a frame,
 * so an info note inside a card doesn't draw a coloured box.
 */
/** The dismiss disc is pulled this far into the corner, so its x sits near the padding edge, not 6 pt inside it. */
const DISMISS_PULL = 6;

export function Banner({ tone = "info", title, message, icon, actionLabel, onAction, onDismiss, dismissLabel }: BannerProps) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const tc = toneColors(t.color, tone);
  // The icon sits on the first line: the title's when there is one, else the message's.
  // Its role follows the line: 20 beside a title, 16 beside a callout message (DS §7, Prism FD-6).
  const line = useOpticalLine(title ? "headline" : "callout");
  return (
    <View
      accessibilityLiveRegion={tone === "danger" ? "assertive" : "polite"}
      style={{
        flexDirection: "row",
        gap: iconTokens.gap,
        padding: t.space.md,
        borderRadius: t.radius.lg,
        backgroundColor: tc.bg,
        borderWidth: tone === "danger" ? t.size.border : 0,
        borderColor: tc.fg,
      }}
    >
      <IconSlot name={icon ?? DEFAULT_ICON[tone]} line={line} color={tc.fg} />
      <View style={{ flex: 1, gap: t.space.xs }}>
        {title && (
          <Text variant="headline" style={{ color: tc.fg }}>
            {title}
          </Text>
        )}
        <Text variant="callout" color="primary">
          {message}
        </Text>
        {actionLabel && onAction && (
          <View style={{ alignSelf: "flex-start", marginLeft: -t.space.md }}>
            <Button label={actionLabel} variant="ghost" size="sm" onPress={onAction} />
          </View>
        )}
      </View>
      {onDismiss && (
        <IconButton icon="x" size="sm" label={dismissLabel ?? tr("ui.dismiss")} onPress={onDismiss} style={{ marginTop: -DISMISS_PULL, marginRight: -DISMISS_PULL }} />
      )}
    </View>
  );
}
