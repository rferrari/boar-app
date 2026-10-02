import React, { useEffect, useRef, useState } from "react";
import { BackHandler, Pressable, View } from "react-native";
import { useTranslation } from "react-i18next";
import { impact, ImpactFeedbackStyle } from "../services/haptics";
import { llamaEngine } from "../inference/LlamaEngine";
import { getRoutingPreset } from "../models/settings";
import type { CatalogModel } from "../models/manifest";
import { EVAL_SET, EVAL_SET_VERSION } from "../eval/evalSet";
import { EvalConfig, evalConfigId, EvalResultRow } from "../eval/evalHarness.pure";
import {
  discardRun,
  exportEvalResults,
  keepFinishedRows,
  listInstalledEvalModels,
  loadLatestRun,
  markRunShared,
  runEvaluation,
  EvalProgress,
  EvaluationRun,
  SavedRun,
} from "../eval/evalHarness";
import { runDeviceEvalRequest } from "../eval/deviceEvalRequest";
import type { EvalRequest } from "../eval/deviceEvalRequest.pure";
import { resultsSharingAvailable, shareDevice, shareEvalRun } from "../eval/shareResults";
import {
  SHARE_MIN_MS,
  shareSucceeded,
  STEP_MIN_MS,
  type ShareDevice,
  type ShareOutcome,
  type ShareStep,
  type ShareStepInfo,
} from "../eval/shareResults.pure";
import { SharePreview } from "./SharePreview";
import { ShareProgress, type ShareLogLine } from "./ShareProgress";
import appConfig from "../../app.json";
import { Button, IconSlot, Progress, Screen, Section, Skeleton, Text, useOpticalLine, useToast } from "./components";
import { ScreenTitle } from "./flows/ScreenTitle";
import { screenRhythm } from "./flows/rhythm";
import type { TextColor } from "./components";
import { icon, useTokens } from "./theme";
import { useMotion } from "./theme/motion";
import { userErrorKey } from "./flows/userError";

interface Props {
  /** Shown as a Done button when the screen is opened outside the navigation stack (device requests). */
  onClose?: () => void;
  /** A chat reply is still generating — running an evaluation now would fight it for the model. */
  chatBusy?: boolean;
  /** Sent from a development machine (scripts/eval-device.mjs): runs immediately with its own selection. */
  deviceRequest?: EvalRequest;
}

function formatMs(ms: number | undefined): string {
  if (ms == null) return "—";
  return ms < 1000 ? `${ms.toFixed(0)}ms` : `${(ms / 1000).toFixed(1)}s`;
}

function outcomeColor(outcome: EvalResultRow["outcome"]): TextColor {
  if (outcome === "failure") return "danger";
  if (outcome === "cancelled") return "warning";
  return "success";
}

/**
 * Runs the fixed evaluation set (src/eval/evalSet.ts) against selected
 * configurations and exports the structured results. See
 * docs/EVAL_QUERIES.md for the workflow.
 */
export function EvaluationScreen({ onClose, chatBusy, deviceRequest }: Props) {
  const { t, i18n } = useTranslation();
  const tokens = useTokens();
  const bodyLine = useOpticalLine("body");
  const toast = useToast();
  const [models, setModels] = useState<CatalogModel[] | null>(null);
  const [preset, setPreset] = useState<string>("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [running, setRunning] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [progress, setProgress] = useState<EvalProgress | null>(null);
  const [rows, setRows] = useState<EvalResultRow[]>([]);
  const [run, setRun] = useState<EvaluationRun | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);
  const motion = useMotion();
  const [sharing, setSharing] = useState(false);
  const [shared, setShared] = useState<string | null>(null);
  const [previewDevice, setPreviewDevice] = useState<ShareDevice | null>(null);
  const stopRef = useRef(false);
  // A run the app was closed during (a big model can run the phone out of memory): continue it
  // instead of starting over. evalResume.pure.ts.
  const [unfinished, setUnfinished] = useState<SavedRun | null>(null);

  useEffect(() => {
    (async () => {
      const [installed, p, saved] = await Promise.all([
        listInstalledEvalModels(),
        getRoutingPreset(),
        deviceRequest ? Promise.resolve(null) : loadLatestRun().catch(() => null),
      ]);
      setModels(installed);
      setPreset(p);
      setSelected(new Set([...installed.map((m) => `model:${m.id}`), "adaptive"]));
      if (!saved || (saved.rows.length === 0 && !saved.manifest.inFlight)) return;
      if (!saved.manifest.endedAt) {
        setUnfinished(saved);
      } else if (!saved.manifest.sharedAt) {
        // The last run, not shared yet: it can still be shared or exported after a restart.
        setRows(saved.rows);
        setRun({ runId: saved.manifest.runId, rows: saved.rows, savedPath: saved.savedPath, stopped: !!saved.manifest.stopped });
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const configs: EvalConfig[] = [
    // The model's real name, not its role ("Fast"): it is saved with every answer and shared with other phones.
    ...(models ?? []).map((m): EvalConfig => ({ kind: "model", modelId: m.id, label: m.label })),
    { kind: "adaptive", label: t("evaluation.adaptiveConfig", { preset }) },
  ];
  const chosen = configs.filter((c) => selected.has(evalConfigId(c)));

  const toggle = (id: string) => {
    if (running) return;
    impact(ImpactFeedbackStyle.Light);
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleRun = async (resume?: { saved: SavedRun; skipKilled: boolean }) => {
    impact(ImpactFeedbackStyle.Medium);
    stopRef.current = false;
    setRunning(true);
    setStopping(false);
    setRows(resume ? resume.saved.rows : []);
    setRun(null);
    setUnfinished(null);
    const callbacks = {
      onProgress: setProgress,
      onRow: (row: EvalResultRow) => setRows((prev) => [...prev, row]),
      shouldStop: () => stopRef.current,
    };
    try {
      const result = deviceRequest
        ? await runDeviceEvalRequest(deviceRequest, (p) => t("evaluation.adaptiveConfig", { preset: p }), callbacks)
        : await runEvaluation({
            configs: chosen,
            ...callbacks,
            ...(resume && { resume: { manifest: resume.saved.manifest, rows: resume.saved.rows, skipKilled: resume.skipKilled } }),
          });
      setRun(result);
    } catch (e: any) {
      toast({ message: `${t("evaluation.runFailedTitle")}: ${t(userErrorKey(e))}`, tone: "danger" });
    } finally {
      setRunning(false);
      setStopping(false);
      setProgress(null);
    }
  };

  const handleKeepFinished = async () => {
    if (!unfinished) return;
    impact(ImpactFeedbackStyle.Light);
    try {
      const kept = await keepFinishedRows(unfinished);
      setUnfinished(null);
      setRows(kept.rows);
      setRun(kept);
    } catch (e: any) {
      toast({ message: `${t("evaluation.exportFailedTitle")}: ${t(userErrorKey(e))}`, tone: "danger" });
    }
  };

  const handleDiscard = async () => {
    if (!unfinished) return;
    impact(ImpactFeedbackStyle.Light);
    await discardRun(unfinished.manifest.runId).catch(() => {});
    setUnfinished(null);
  };

  const handleStop = async () => {
    impact(ImpactFeedbackStyle.Medium);
    stopRef.current = true;
    setStopping(true);
    await llamaEngine.stop();
  };

  const handleExport = async (format: "jsonl" | "csv") => {
    if (!run) return;
    impact(ImpactFeedbackStyle.Light);
    try {
      await exportEvalResults(run, format);
    } catch (e: any) {
      toast({ message: `${t("evaluation.exportFailedTitle")}: ${t(userErrorKey(e))}`, tone: "danger" });
    }
  };

  // Nothing leaves the phone until the user has seen everything that will be sent (SharePreview)
  // and pressed Share there.
  const handleShare = () => {
    if (!run || run.rows.length === 0) return;
    impact(ImpactFeedbackStyle.Light);
    setPreviewDevice(shareDevice());
  };

  // A second tap before the button re-renders disabled must not send the run again.
  const sendingRef = useRef(false);
  const [shareUi, setShareUi] = useState<{ step: ShareStep | null; log: ShareLogLine[]; outcome: ShareOutcome | null } | null>(null);
  const logLine = (step: ShareStep, info?: ShareStepInfo): string => {
    if (step === "challenge" && info?.challenge) return t("evaluation.shareProgress.log.challengeGot", { code: info.challenge });
    if (step === "key" && info?.key) return t(`evaluation.shareProgress.log.key-${info.key}`);
    if (step === "send") return t("evaluation.shareProgress.log.send", { size: `${Math.max(1, Math.round((info?.bytes ?? 0) / 1024))} KB`, count: info?.rows ?? 0 });
    return t(`evaluation.shareProgress.log.${step}`, { count: info?.rows ?? 0 });
  };
  const confirmShare = async () => {
    if (!run || sendingRef.current) return;
    sendingRef.current = true;
    setSharing(true);
    impact(ImpactFeedbackStyle.Light);
    const started = Date.now();
    setShareUi({ step: "prepare", log: [], outcome: null });
    // Steps reach the screen one at a time, each for at least STEP_MIN_MS.
    let shown = Promise.resolve();
    const onStep = (step: ShareStep, info?: ShareStepInfo) => {
      shown = shown.then(async () => {
        setShareUi((s) => s && { ...s, step, log: [...s.log, { key: `${s.log.length}`, text: logLine(step, info) }] });
        await new Promise((r) => setTimeout(r, STEP_MIN_MS));
      });
    };
    let outcome: ShareOutcome = { result: "failed" };
    try {
      outcome = await shareEvalRun(run.rows, onStep);
    } catch (e) {
      outcome = { result: "failed", detail: String((e as Error)?.message ?? e) };
    }
    await shown;
    const wait = SHARE_MIN_MS - (Date.now() - started);
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
    sendingRef.current = false;
    setSharing(false);
    if (shareSucceeded(outcome.result)) {
      setShared(run.runId);
      markRunShared(run.runId).catch(() => {});
    }
    const status = outcome.status ? t("evaluation.shareProgress.log.status", { status: outcome.status }) : null;
    setShareUi((s) => s && { step: null, outcome, log: status ? [...s.log, { key: `${s.log.length}`, text: status }] : s.log });
    impact(shareSucceeded(outcome.result) ? ImpactFeedbackStyle.Medium : ImpactFeedbackStyle.Light);
  };
  const closeShare = () => {
    setShareUi(null);
    setPreviewDevice(null);
  };
  // "You can share again at 14:05" (or "Tue 09:30" when that is another day).
  const retryAtText = (() => {
    const at = shareUi?.outcome?.retryAt ? new Date(shareUi.outcome.retryAt) : null;
    return at
      ? at.toLocaleString(i18n.language, {
          ...(at.toDateString() === new Date().toDateString() ? {} : { weekday: "short" }),
          hour: "2-digit",
          minute: "2-digit",
        })
      : null;
  })();

  const canRun = !running && !chatBusy && chosen.length > 0 && models !== null;
  const killedLabel = unfinished?.manifest.inFlight
    ? unfinished.manifest.configs.find((c) => evalConfigId(c) === unfinished.manifest.inFlight!.configId)?.label
    : undefined;
  // A run saved with another question set can't be continued with this one: keep or discard it.
  const canContinue = unfinished?.manifest.evalSetVersion === EVAL_SET_VERSION;
  const unfinishedTotal = unfinished ? unfinished.manifest.configs.length * unfinished.manifest.queryIds.length : 0;

  // Opened standalone (a device request): Android back is the Done button, and does nothing during a
  // run, so a stray press can't close the app in the middle of a benchmark.
  useEffect(() => {
    if (!onClose) return;
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      if (!running) onClose();
      return true;
    });
    return () => sub.remove();
  }, [onClose, running]);

  const autoStarted = useRef(false);
  useEffect(() => {
    if (deviceRequest && models !== null && !autoStarted.current) {
      autoStarted.current = true;
      handleRun();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceRequest, models]);

  return (
    // The flow screens' 14 pt rhythm, like its siblings (Prism FL-29).
    // Opened outside the navigator (a device request), no header sits above it: it owns the top inset
    // too. Screen adds it by hand on Android only; iOS's automatic inset already covers it.
    <Screen edges={onClose ? ["top", "bottom", "left", "right"] : undefined} contentStyle={screenRhythm(tokens)}>
      {!onClose && <ScreenTitle>{t("flows.performance.evaluationTitle")}</ScreenTitle>}
      <View style={{ gap: tokens.space.xs }}>
        {onClose && !running && (
          <Button size="sm" variant="ghost" label={t("common.done")} onPress={onClose} style={{ alignSelf: "flex-end" }} />
        )}
        <Text variant="callout" color="secondary">
          {t("evaluation.subtitle", { version: EVAL_SET_VERSION, count: EVAL_SET.length })}
        </Text>
        {deviceRequest && (
          <Text variant="footnote" color="secondary">
            {t("evaluation.deviceRequest", { id: deviceRequest.requestId })}
          </Text>
        )}
      </View>

      {unfinished && !running && (
        <Section title={t("evaluation.unfinished.title")}>
          <View style={{ padding: tokens.space.base, gap: tokens.space.sm }}>
            <Text variant="callout">
              {t("evaluation.unfinished.saved", { done: unfinished.rows.length, total: unfinishedTotal })}
              {killedLabel ? ` ${t("evaluation.unfinished.killed", { model: killedLabel })}` : ""}
            </Text>
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: tokens.space.sm }}>
              {canContinue && (
                <Button
                  size="sm"
                  icon="play"
                  label={killedLabel ? t("evaluation.unfinished.continueSkipping") : t("evaluation.unfinished.continue")}
                  onPress={() => handleRun({ saved: unfinished, skipKilled: true })}
                  disabled={!!chatBusy || models === null}
                />
              )}
              {canContinue && killedLabel && (
                <Button
                  size="sm"
                  variant="secondary"
                  icon="rotate-ccw"
                  label={t("evaluation.unfinished.retry")}
                  onPress={() => handleRun({ saved: unfinished, skipKilled: false })}
                  disabled={!!chatBusy || models === null}
                />
              )}
              <Button size="sm" variant="secondary" icon="check" label={t("evaluation.unfinished.keep")} onPress={handleKeepFinished} />
              <Button size="sm" variant="ghost" icon="trash-2" label={t("evaluation.unfinished.discard")} onPress={handleDiscard} />
            </View>
          </View>
        </Section>
      )}

      {!deviceRequest && (
        <Section title={t("evaluation.configsTitle")}>
          {models === null ? (
            <View style={{ padding: tokens.space.base }}>
              <Skeleton height={tokens.size.row} />
            </View>
          ) : (
            <>
              {models.length === 0 && (
                <View style={{ padding: tokens.space.base }}>
                  <Text variant="callout" color="secondary">
                    {t("evaluation.noModels")}
                  </Text>
                </View>
              )}
              {configs.map((c) => {
                const id = evalConfigId(c);
                const on = selected.has(id);
                return (
                  <Pressable
                    key={id}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: on, disabled: running }}
                    accessibilityLabel={c.label}
                    onPress={() => toggle(id)}
                    style={{ minHeight: tokens.size.row, paddingHorizontal: tokens.space.base, paddingVertical: tokens.space.md, flexDirection: "row", alignItems: "flex-start", gap: icon.gap }}
                  >
                    <IconSlot name={on ? "check-square" : "square"} line={bodyLine} color={on ? tokens.color.accent.text : tokens.color.text.secondary} />
                    <Text variant="body" style={{ flex: 1 }}>
                      {c.label}
                    </Text>
                  </Pressable>
                );
              })}
            </>
          )}
        </Section>
      )}

      <View style={{ gap: tokens.space.sm }}>
        {!deviceRequest && (
          <Text variant="footnote" color="secondary">
            {t("evaluation.summary", { queries: EVAL_SET.length, configs: chosen.length, total: EVAL_SET.length * chosen.length })}
          </Text>
        )}
        <Text variant="footnote" color={chatBusy ? "warning" : "secondary"}>
          {chatBusy ? t("evaluation.chatBusy") : t("evaluation.keepScreenOn")}
        </Text>
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: tokens.space.sm }}>
          {running ? (
            <Button variant="destructive" icon="square" label={stopping ? t("evaluation.stopping") : t("evaluation.stop")} onPress={handleStop} disabled={stopping} />
          ) : (
            <Button label={t("evaluation.run")} icon="play" onPress={() => handleRun()} disabled={!canRun} />
          )}
          {run && !running && (
            <>
              <Button size="sm" variant="secondary" icon="share" label={t("evaluation.exportJsonl")} onPress={() => handleExport("jsonl")} />
              <Button size="sm" variant="secondary" icon="share" label={t("evaluation.exportCsv")} onPress={() => handleExport("csv")} />
              {resultsSharingAvailable && (
                <Button
                  size="sm"
                  icon="upload"
                  label={sharing ? t("evaluation.sharing") : shared === run.runId ? t("evaluation.shared") : t("evaluation.share")}
                  onPress={handleShare}
                  disabled={sharing || shared === run.runId}
                />
              )}
            </>
          )}
        </View>
      </View>

      {progress && (
        <View style={{ gap: tokens.space.xs }}>
          <Progress
            label={t("evaluation.title")}
            value={(progress.configIndex * progress.queryCount + progress.queryIndex) / (progress.configCount * progress.queryCount)}
            valueText={t("evaluation.progress", {
              config: progress.configIndex + 1,
              configs: progress.configCount,
              query: progress.queryIndex + 1,
              queries: progress.queryCount,
            })}
          />
          <Text variant="footnote" color="secondary">
            {t("evaluation.progress", {
              config: progress.configIndex + 1,
              configs: progress.configCount,
              query: progress.queryIndex + 1,
              queries: progress.queryCount,
            })}
          </Text>
          <Text variant="footnote" color="secondary" numberOfLines={2}>
            {progress.config.label} — {progress.query.query}
          </Text>
        </View>
      )}

      {run && (
        <Text variant="footnote" color="secondary" selectable>
          {run.stopped ? `${t("evaluation.stoppedEarly")} ` : ""}
          {t("evaluation.savedTo", { path: run.savedPath })}
        </Text>
      )}

      {rows.length > 0 && (
        <Section>
          {rows.map((r) => {
            const key = `${r.configId}/${r.queryId}`;
            const open = expanded === key;
            return (
              <Pressable
                key={key}
                accessibilityRole="button"
                accessibilityState={{ expanded: open }}
                accessibilityLabel={`${r.queryId}, ${r.configLabel}, ${r.outcome ?? ""}`}
                onPress={() => {
                  motion.animateNextLayout();
                  setExpanded(open ? null : key);
                }}
                style={{ padding: tokens.space.base, gap: tokens.space.xxs }}
              >
                <View style={{ flexDirection: "row", justifyContent: "space-between", gap: tokens.space.sm }}>
                  <Text variant="subhead" numberOfLines={1} style={{ flex: 1 }}>
                    {r.queryId} · {r.configLabel}
                  </Text>
                  <Text variant="caption" color={outcomeColor(r.outcome)} weight="semibold">
                    {(r.outcome ?? "—").toUpperCase()}
                  </Text>
                </View>
                <Text variant="caption" color="secondary">
                  {r.modelId ?? "—"} · {r.taskType ?? "—"} · {r.modelResidency ?? "—"} ·{" "}
                  {r.retrievalUsed ? r.retrievedTitles.slice(0, 2).join(", ") : t("evaluation.noRetrieval")}
                </Text>
                <Text variant="caption" color="secondary" numeric>
                  {r.tokPerSec ? `${r.tokPerSec.toFixed(1)} t/s` : "—"} · load {formatMs(r.modelLoadMs)} · TTFT {formatMs(r.ttftMs)} · total{" "}
                  {formatMs(r.totalLatencyMs)}
                </Text>
                <Text variant="footnote" numberOfLines={open ? undefined : 3}>
                  {r.errorMessage && r.outcome === "failure" ? r.errorMessage : r.answer}
                </Text>
              </Pressable>
            );
          })}
        </Section>
      )}
      {run && previewDevice && (
        <SharePreview
          visible
          rows={run.rows}
          device={previewDevice}
          appVersion={appConfig.expo.version}
          sending={sharing}
          onCancel={closeShare}
          onShare={confirmShare}
          progress={
            shareUi ? (
              <ShareProgress
                step={shareUi.step}
                log={shareUi.log}
                outcome={shareUi.outcome}
                retryAtText={retryAtText}
                onDone={closeShare}
                onRetry={confirmShare}
                onExport={handleExport}
              />
            ) : null
          }
        />
      )}
    </Screen>
  );
}
