import React, { useEffect, useRef, useState } from "react";
import { AccessibilityInfo, findNodeHandle, useWindowDimensions, View } from "react-native";
import { useTranslation } from "react-i18next";
import { Button, IconSlot, LARGE_TEXT_SCALE, Progress, Text, useAnnounce, useOpticalLine } from "../components";
import { icon, useTokens } from "../theme";
import { MODEL_CATALOG } from "../../models/manifest";
import { poiCatalogEntries } from "../../rag/poiRegions";
import { placeTileNames, worldPlacesEntry } from "./adapters";
import { tileCorner, tileIdOf } from "./placeTiles";
import { catalogLabel } from "./catalogLabel";
import { formatBytes, readableErrorDetail } from "./format";
import type { TFunction } from "i18next";

function labelFor(assetId: string | undefined, t: TFunction, tileNames: Record<string, string> = {}): string | undefined {
  if (!assetId) return undefined;
  // A places tile ("poi-t-N41E012") by the name Knowledge gives it, never the raw id (Piston ecb83d3).
  const tile = tileIdOf({ id: assetId });
  if (tile) return tileNames[tile] ? t("flows.travel.areaOf", { city: tileNames[tile] }) : t("flows.places.cornerArea", { corner: tileCorner(tile) });
  const item = [...MODEL_CATALOG, ...poiCatalogEntries(), worldPlacesEntry()].find((m) => m.id === assetId);
  return item && catalogLabel(item, t);
}
import type { FileImport } from "./useCatalog";

/** Kinds whose message says it all (Ledger's empty-file, unreadable-file): no raw detail under them. */
const SELF_EXPLAINED = new Set<string>(["empty-file", "unreadable-file"]);

interface Props {
  imports: FileImport[];
  onPick: () => void;
  /** Stops the import in progress. */
  onCancel?: () => void;
  /** Label of the pick button; "Choose files" by default. */
  pickLabel?: string;
  /** The pick is the screen's main action (offline setup): primary, full width. */
  primary?: boolean;
  /** The pick button lives elsewhere (the setup's footer CTA); this lists the files only. */
  hidePick?: boolean;
  /** The file being checked is shown elsewhere (the setup's hero): list only finished and refused ones. */
  hideActive?: boolean;
  /** Verified files are shown elsewhere (the setup's category rows): list only the refused ones. */
  hideVerified?: boolean;
}

/**
 * Files chosen for import and what happened to each: hashing progress,
 * verified as a catalog item, or why it was refused. Refusals stay on
 * screen with the file name until the next pick (Prism F8).
 */
export function ImportList({ imports, onPick, onCancel, pickLabel, primary, hidePick, hideActive, hideVerified }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const line = useOpticalLine("subhead");
  const largeText = useWindowDimensions().fontScale >= LARGE_TEXT_SCALE;
  const announce = useAnnounce();
  const pickRef = useRef<View>(null);
  const busy = imports.some((f) => f.status === "importing");
  // The city a tile was downloaded for, if any; a tile that came in as a file is named by its corner.
  const [tileNames, setTileNames] = useState<Record<string, string>>({});
  const verifiedCount = imports.filter((f) => f.status === "verified").length;
  useEffect(() => {
    let live = true;
    placeTileNames()
      .then((names) => live && setTileNames(names))
      .catch(() => {});
    return () => {
      live = false;
    };
  }, [verifiedCount]);

  // Same contract as downloads (Prism F4/F5): polite on start, every quarter
  // and when verified; assertive on a refusal, with focus on the pick button.
  const spoken = useRef<Record<string, string>>({});
  useEffect(() => {
    let refused = false;
    for (const f of imports) {
      const key = f.status === "importing" ? `q${Math.floor(f.progress * 4)}` : f.status;
      if (spoken.current[f.name] === key) continue;
      spoken.current[f.name] = key;
      if (f.status === "importing") {
        const pct = Math.floor(f.progress * 4) * 25;
        announce(pct === 0 ? t("flows.import.checking", { name: f.name }) : t("flows.import.checkingAnnounce", { name: f.name, pct }));
      } else if (f.status === "verified") {
        announce(t("flows.import.verified", { item: labelFor(f.assetId, t, tileNames) ?? f.assetId ?? f.name }));
      } else {
        announce(`${f.name}: ${t(`flows.row.error.${f.errorKind ?? "unknown"}`)}`, { assertive: true });
        refused = true;
      }
    }
    if (refused) {
      setTimeout(() => {
        const node = pickRef.current && findNodeHandle(pickRef.current);
        if (node) AccessibilityInfo.setAccessibilityFocus(node);
      }, 300);
    }
    // Forget files that left the list, so a re-pick announces again.
    for (const name of Object.keys(spoken.current)) if (!imports.some((f) => f.name === name)) delete spoken.current[name];
  }, [imports, announce, t, tileNames]);
  return (
    <View style={{ gap: tokens.space.md }}>
      {imports.filter((f) => !(hideActive && f.status === "importing") && !(hideVerified && f.status === "verified")).map((f) => {
        const label = labelFor(f.assetId, t, tileNames);
        return (
          <View key={f.name} style={{ gap: tokens.space.xs }}>
            <View style={{ flexDirection: "row", gap: icon.gap, alignItems: "flex-start" }}>
              <View importantForAccessibility="no-hide-descendants" accessibilityElementsHidden>
                <IconSlot
                  line={line}
                  name={f.status === "verified" ? "check-circle" : f.status === "failed" ? "alert-octagon" : "file"}
                  color={
                    f.status === "verified"
                      ? tokens.color.status.success.solid
                      : f.status === "failed"
                        ? tokens.color.status.danger.solid
                        : tokens.color.text.secondary
                  }
                />
              </View>
              <Text variant="subhead" numberOfLines={largeText ? 2 : 1} ellipsizeMode="middle" accessibilityLabel={f.name} style={{ flex: 1 }}>
                {f.name}
              </Text>
            </View>
            {f.status === "importing" && (
              <Progress
                label={t("flows.import.checking", { name: f.name })}
                value={f.progress}
                valueText={t("flows.import.checkingValue", { pct: Math.round(f.progress * 100) })}
              />
            )}
            {f.status === "verified" && (
              <Text variant="footnote" color="success">
                {t("flows.import.verified", { item: label ?? f.assetId ?? "" })}
              </Text>
            )}
            {f.status === "verified" &&
              f.missing?.map((m) => (
                // Honest about what still won't work, and what does (Ledger PL-1, Boar).
                <Text key={m.label} variant="footnote" color="warning">
                  {t("flows.import.missingCities", { name: m.label, size: formatBytes(m.sizeBytes, i18n.language) })}
                </Text>
              ))}
            {f.status === "failed" && (
              <>
                {/* The error kind says it (Ledger 6f28763/7f57dad): empty-file, unreadable-file, unknown-file… */}
                <Text variant="footnote" color="danger">
                  {t(`flows.row.error.${f.errorKind ?? "unknown"}`)}
                </Text>
                {f.errorKind === "unknown-file" ? (
                  // What to do on the phone, not a path in the repository (Prism IM-3).
                  <Text variant="caption" color="secondary">
                    {t("flows.import.unknownHint")}
                  </Text>
                ) : (
                  f.message &&
                  !SELF_EXPLAINED.has(f.errorKind ?? "") && (
                    <Text variant="caption" color="secondary" selectable>
                      {readableErrorDetail(f.message, i18n.language)}
                    </Text>
                  )
                )}
              </>
            )}
          </View>
        );
      })}
      {busy && onCancel && !hideActive && (
        <Button
          variant="outline"
          label={t("common.cancel")}
          accessibilityLabel={t("flows.import.cancelA11y", { name: imports.find((f) => f.status === "importing")?.name ?? "" })}
          onPress={onCancel}
        />
      )}
      {!hidePick && (
      <Button
        ref={pickRef}
        label={pickLabel ?? t("flows.import.pick")}
        icon="file-plus"
        variant={primary ? "primary" : "secondary"}
        fullWidth={primary}
        onPress={onPick}
        loading={busy}
      />
      )}
    </View>
  );
}
