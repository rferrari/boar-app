import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Text } from "../components";

/** Loading lines are swapped this often, like a game's loading screen. */
const LOADING_LINE_MS = 4500;
/** flows.onboarding.loadingLines.l1 … l12 */
const LOADING_LINES = 12;

/**
 * One light line under a progress bar while BOAR works ("The boar stopped to sniff a truffle…"), changing
 * every few seconds so a long wait doesn't feel stuck. Decorative: hidden from screen readers, which hear
 * the progress itself.
 */
export function LoadingLine() {
  const { t } = useTranslation();
  const [i, setI] = useState(() => Math.floor(Math.random() * LOADING_LINES));
  useEffect(() => {
    const id = setInterval(() => setI((n) => (n + 1) % LOADING_LINES), LOADING_LINE_MS);
    return () => clearInterval(id);
  }, []);
  return (
    <Text variant="footnote" color="secondary" accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      {t(`flows.onboarding.loadingLines.l${i + 1}`)}
    </Text>
  );
}
