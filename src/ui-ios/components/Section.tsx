import React from "react";
import { View, ViewProps } from "react-native";
import { useTokens } from "../theme";
import { Text } from "./Text";
import { childKey } from "./childKey";

export interface SectionProps extends ViewProps {
  title?: string;
  footer?: string;
  /** Wrap children in an inset grouped surface (settings style). */
  inset?: boolean;
}

/** A titled group of rows. With `inset`, rows sit on one rounded surface separated by hairlines. */
export function Section({ title, footer, inset = true, children, style, ...rest }: SectionProps) {
  const t = useTokens();
  const items = React.Children.toArray(children).filter(Boolean);
  return (
    <View style={[{ gap: t.space.sm }, style]} {...rest}>
      {title && (
        <Text variant="label" color="secondary" header style={{ paddingHorizontal: t.space.md + t.space.xxs }}>
          {title}
        </Text>
      )}
      {inset ? (
        <View
          style={{
            backgroundColor: t.color.bg.surface,
            borderRadius: t.radius.lg,
            borderWidth: t.scheme === "light" ? t.size.hairline : 0,
            borderColor: t.color.line.hairline,
            overflow: "hidden",
          }}
        >
          {items.map((child, i) => (
            <View key={childKey(child, i)}>
              {/* The separator is inset; the row itself is not, so every row keeps the same left edge. */}
              {i > 0 && (
                <View
                  pointerEvents="none"
                  style={{
                    position: "absolute",
                    top: 0,
                    left: t.space.md + t.space.xxs,
                    right: 0,
                    height: t.size.hairline,
                    backgroundColor: t.color.line.hairline,
                  }}
                />
              )}
              {child}
            </View>
          ))}
        </View>
      ) : (
        items
      )}
      {footer && (
        <Text variant="footnote" color="secondary" style={{ paddingHorizontal: t.space.md + t.space.xxs }}>
          {footer}
        </Text>
      )}
    </View>
  );
}
