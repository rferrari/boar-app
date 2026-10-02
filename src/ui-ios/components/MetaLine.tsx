import React from "react";
import { Text } from "./Text";
import type { TextColor } from "./Text";
import { META_SEPARATOR, metaItems } from "./metaItems";

export interface MetaLineProps {
  /** Facts in reading order. Falsy entries are dropped, so callers can write `ready && "cached"`. */
  items: (string | false | null | undefined)[];
  /** `mono` (default): tabular readouts. `caption`: prose metadata. */
  variant?: "mono" | "caption";
  color?: TextColor;
  numberOfLines?: number;
}

/**
 * One line of secondary facts, "978 MB · ~4 min · 5 MB/s". Replaces stacks of
 * "Label: value" rows of equal weight. Screen readers hear commas, not dots.
 */
export function MetaLine({ items, variant = "mono", color = "secondary", numberOfLines }: MetaLineProps) {
  const facts = metaItems(items);
  if (facts.length === 0) return null;
  return (
    <Text
      variant={variant}
      color={color}
      numeric
      numberOfLines={numberOfLines}
      accessibilityLabel={facts.join(", ")}
    >
      {facts.join(META_SEPARATOR)}
    </Text>
  );
}
