import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { View } from "react-native";
import { toneColors, useTokens } from "../theme";
import { Button } from "./Button";
import { Icon, IconName } from "./Icon";
import { Text } from "./Text";
import { TextAction } from "./TextAction";

export interface EmptyStateProps {
  title: string;
  body?: string;
  /** The raw technical text of an error, folded behind "Details" and selectable when open (never the body, Prism FL-11/NA-3). */
  detail?: string;
  icon?: IconName;
  /** `error` turns this into the ErrorState (danger icon well). */
  tone?: "neutral" | "error";
  actionLabel?: string;
  onAction?: () => void;
  /** "secondary" where the screen's one accent belongs to another control (the chat's send). Default primary. */
  actionVariant?: "primary" | "secondary";
  secondaryLabel?: string;
  onSecondary?: () => void;
}

/** Empty and error states: what happened, why, and the one action that moves forward. */
export function EmptyState({
  title,
  body,
  detail,
  icon,
  tone = "neutral",
  actionLabel,
  onAction,
  actionVariant = "primary",
  secondaryLabel,
  onSecondary,
}: EmptyStateProps) {
  const t = useTokens();
  const { t: tr } = useTranslation();
  const [detailOpen, setDetailOpen] = useState(false);
  const tc = toneColors(t.color, tone === "error" ? "danger" : "neutral");
  return (
    <View style={{ alignItems: "center", paddingHorizontal: t.space.xl, paddingVertical: t.space.xxl, gap: t.space.md }}>
      <View
        style={{
          width: t.size.emptyWell,
          height: t.size.emptyWell,
          borderRadius: t.radius.full,
          backgroundColor: tc.bg,
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Icon name={icon ?? (tone === "error" ? "alert-triangle" : "inbox")} size="lg" color={tc.fg} />
      </View>
      <Text variant="title3" align="center" header>
        {title}
      </Text>
      {body && (
        <Text variant="callout" color="secondary" align="center">
          {body}
        </Text>
      )}
      {detail ? (
        <View style={{ alignItems: "center", gap: t.space.xs }}>
          <TextAction
            label={tr(detailOpen ? "ui.hideDetails" : "ui.details")}
            icon={detailOpen ? "chevron-up" : "chevron-down"}
            expanded={detailOpen}
            onPress={() => setDetailOpen((v) => !v)}
          />
          {detailOpen && (
            <Text variant="caption" color="secondary" align="center" selectable>
              {detail}
            </Text>
          )}
        </View>
      ) : null}
      {(actionLabel || secondaryLabel) && (
        <View style={{ gap: t.space.sm, marginTop: t.space.sm, alignSelf: "stretch", alignItems: "center" }}>
          {actionLabel && onAction && <Button label={actionLabel} variant={actionVariant} onPress={onAction} />}
          {secondaryLabel && onSecondary && <Button label={secondaryLabel} variant="ghost" onPress={onSecondary} />}
        </View>
      )}
    </View>
  );
}
