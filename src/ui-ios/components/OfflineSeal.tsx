import React from "react";
import { View } from "react-native";
import { useTokens } from "../theme";
import { Icon } from "./Icon";
import { IconText } from "./IconText";
import { icon as iconTokens } from "../theme";
import { Text } from "./Text";

export interface OfflineSealProps {
  /** e.g. t("ui.offline"). */
  label: string;
  /** Second line for the `card` style, e.g. "Local model". */
  sublabel?: string;
  /** `pill` = ember pill with crossed wifi (header). `moon` = pill with a moon disc. `card` = two lines, below the header. */
  variant?: "pill" | "moon" | "card";
  /** Longer spoken form when the visible label is terse (pill "OFFLINE" → "Answers are offline"). Defaults to the label. */
  accessibilityLabel?: string;
}

/**
 * The OFFLINE seal from the identity. Show it only when it is true: the build
 * or the current state guarantees no network use (see the trust/offline
 * variant). It is a statement, not decoration.
 */
export function OfflineSeal({ label, sublabel, variant = "pill", accessibilityLabel }: OfflineSealProps) {
  const t = useTokens();
  const moonDisc = (
    <View
      style={{
        width: variant === "card" ? 32 : 24,
        height: variant === "card" ? 32 : 24,
        borderRadius: 999,
        backgroundColor: variant === "card" ? t.color.field.soft : t.color.accent.on,
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Icon name="moon" size="sm" color={variant === "card" ? t.color.field.text : t.color.accent.solid} />
    </View>
  );
  if (variant === "card") {
    return (
      <View
        accessible
        accessibilityLabel={accessibilityLabel ?? [label, sublabel].filter(Boolean).join(", ")}
        style={{
          flexDirection: "row",
          alignItems: "center",
          alignSelf: "flex-start",
          gap: t.space.md,
          paddingVertical: t.space.sm,
          paddingLeft: t.space.sm,
          paddingRight: t.space.base,
          borderRadius: t.radius.md,
          borderWidth: t.size.border,
          borderColor: t.color.field.text,
          backgroundColor: t.color.bg.surface,
        }}
      >
        {moonDisc}
        <View>
          <Text variant="button" color="field" style={{ letterSpacing: 0.6, textTransform: "uppercase" }}>
            {label}
          </Text>
          {sublabel && (
            <Text variant="caption" color="secondary" numeric>
              {sublabel}
            </Text>
          )}
        </View>
      </View>
    );
  }
  return (
    <View
      accessible
      accessibilityLabel={accessibilityLabel ?? label}
      style={{
        flexDirection: "row",
        alignItems: "center",
        alignSelf: "flex-start",
        gap: iconTokens.gapTight,
        paddingVertical: variant === "moon" ? 4 : 6,
        paddingLeft: variant === "moon" ? 4 : t.space.sm + 2,
        paddingRight: t.space.md,
        borderRadius: t.radius.full,
        backgroundColor: t.color.accent.solid,
        ...(t.elevation.glow as object),
      }}
    >
      {variant === "moon" && moonDisc}
      {/* iOS draws the Baloo caps ~2 pt high in their box; centerOnBox puts them on the seal's middle. */}
      <IconText icon={variant === "moon" ? undefined : "wifi-off"} variant="seal" gap="tight" tint={t.color.accent.on} maxFontSizeMultiplier={1.5} centerOnBox>
        {label}
      </IconText>
    </View>
  );
}
