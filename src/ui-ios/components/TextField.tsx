import React, { forwardRef, useState } from "react";
import { TextInput, TextInputProps, View } from "react-native";
import { useTokens } from "../theme";
import { Text } from "./Text";

export interface TextFieldProps extends Omit<TextInputProps, "style" | "placeholderTextColor"> {
  /** Visible label; also the accessible name (placeholder is never the label). */
  label?: string;
  /** Accessible name when the design has no visible label (e.g. the chat composer). */
  accessibilityLabel?: string;
  helper?: string;
  error?: string;
  /** Multiline that grows with content up to `maxRows`, then scrolls. */
  autoGrow?: boolean;
  maxRows?: number;
  leading?: React.ReactNode;
  trailing?: React.ReactNode;
}

export const TextField = forwardRef<TextInput, TextFieldProps>(function TextField(
  { label, accessibilityLabel, helper, error, autoGrow, maxRows = 6, leading, trailing, onFocus, onBlur, editable = true, ...rest },
  ref
) {
  const t = useTokens();
  const [focused, setFocused] = useState(false);
  const body = t.type.body;
  const lineHeight = body.lineHeight ?? 24;
  const borderColor = error ? t.color.status.danger.solid : focused ? t.color.line.focus : t.color.line.strong;
  const { maxFontSizeMultiplier: _ignored, ...inputType } = body;
  return (
    <View style={{ gap: t.space.xs }}>
      {label && (
        <Text variant="subhead" color="secondary" accessible={false} importantForAccessibility="no">
          {label}
        </Text>
      )}
      <View
        style={{
          flexDirection: "row",
          alignItems: autoGrow ? "flex-end" : "center",
          gap: t.space.sm,
          minHeight: t.size.touch,
          paddingHorizontal: t.space.base,
          // Pill like the mockup's question field; a softer radius once it can grow past one line.
          borderRadius: autoGrow || rest.multiline ? t.space.xl : t.radius.full,
          borderWidth: focused || error ? t.size.focusRing : t.size.border,
          borderColor,
          backgroundColor: t.color.bg.surface,
          opacity: editable ? 1 : 0.6,
        }}
      >
        {leading}
        <TextInput
          ref={ref}
          accessibilityLabel={accessibilityLabel ?? label}
          accessibilityHint={error ?? helper}
          accessibilityState={{ disabled: !editable }}
          editable={editable}
          multiline={autoGrow || rest.multiline}
          placeholderTextColor={t.color.text.tertiary}
          selectionColor={t.color.accent.solid}
          cursorColor={t.color.accent.solid}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          style={[
            inputType,
            {
              flex: 1,
              color: t.color.text.primary,
              paddingVertical: (t.size.touch - lineHeight) / 2 - 1,
              textAlignVertical: autoGrow ? "top" : "center",
            },
            autoGrow && { maxHeight: lineHeight * maxRows + t.space.base },
          ]}
          {...rest}
        />
        {trailing}
      </View>
      {(error || helper) && (
        <Text variant="footnote" color={error ? "danger" : "tertiary"} accessibilityLiveRegion={error ? "polite" : "none"}>
          {error ?? helper}
        </Text>
      )}
    </View>
  );
});
