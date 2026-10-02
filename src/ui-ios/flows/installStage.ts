/** The install step's two stages (the Stepper's 3 and 4). No native imports. */
export type IndexPhase = "waiting" | "building" | "ready" | "error";

/**
 * Files arriving (download or import), then the search index. The step crossfades when the stage
 * changes (Prism L3-2); building <-> error stays one stage (a retry is not a new screen). "ready" is the
 * done screen, drawn apart.
 */
export function installStage(phase: IndexPhase): "transfer" | "index" {
  return phase === "waiting" ? "transfer" : "index";
}

/** The stage swap animates only if the step showed the transfer stage first. */
export function stageSwapAnimates(openedWithEverythingIn: boolean): boolean {
  return !openedWithEverythingIn;
}
