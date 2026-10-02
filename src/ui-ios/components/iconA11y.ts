/**
 * Accessibility props for an icon. Pure (no RN import) so it can be tested.
 *
 * An icon font glyph is a Text node holding a private-use character, which
 * screen readers expose as an empty or garbled text element. So:
 * - decorative (no label): the wrapper and everything inside leave the tree
 *   on both platforms (Android importantForAccessibility, iOS
 *   accessibilityElementsHidden), and the glyph itself is not accessible;
 * - labeled: the wrapper is one element with role "image" and the label, and
 *   the glyph under it is still hidden so it can't be read twice.
 */
export interface IconWrapperA11y {
  accessible: boolean;
  accessibilityRole?: "image";
  accessibilityLabel?: string;
  accessibilityElementsHidden: boolean;
  importantForAccessibility: "yes" | "no-hide-descendants";
}

export interface IconGlyphA11y {
  accessible: false;
  accessibilityElementsHidden: true;
  importantForAccessibility: "no-hide-descendants";
}

export function iconA11yProps(label?: string): { wrapper: IconWrapperA11y; glyph: IconGlyphA11y } {
  const labeled = !!label && label.trim().length > 0;
  return {
    wrapper: labeled
      ? {
          accessible: true,
          accessibilityRole: "image",
          accessibilityLabel: label!.trim(),
          accessibilityElementsHidden: false,
          importantForAccessibility: "yes",
        }
      : {
          accessible: false,
          accessibilityElementsHidden: true,
          importantForAccessibility: "no-hide-descendants",
        },
    glyph: { accessible: false, accessibilityElementsHidden: true, importantForAccessibility: "no-hide-descendants" },
  };
}
