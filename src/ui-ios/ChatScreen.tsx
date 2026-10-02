import React, { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppState, FlatList, LayoutChangeEvent, NativeScrollEvent, NativeSyntheticEvent, Platform, ScrollView, Share, TextInput, View } from "react-native";
import { KeyboardAvoidingView, KeyboardController } from "react-native-keyboard-controller";
import Animated from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as Clipboard from "expo-clipboard";
import { useTranslation } from "react-i18next";
import { DrawerActions, useFocusEffect, useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import { impact, ImpactFeedbackStyle } from "../services/haptics";
import { llamaEngine } from "../inference/LlamaEngine";
import { embeddingEngine } from "../rag/embed";
import { onSeedProgress, seedKnowledgeBaseIfEmpty } from "../rag/seedCorpus";
import { makeGate } from "./chat/gate";
import { MODEL_CATALOG, CORPUS_CATALOG, REQUIRED_MODELS, CatalogModel } from "../models/manifest";
import { listDiscoveredModels } from "../models/discoveredModels";
import { subscribeDownloads, listDownloadStates } from "../services/downloadManager";
import {
  getActiveModelId,
  getHidePromptIdeas,
  setHidePromptIdeas,
  getPersonalityId,
  getCustomSystemPrompt,
  getMaxTokens,
  getMemorySettings,
  MemorySettings as MemorySettingsType,
  DEFAULT_MEMORY_SETTINGS,
  getVoiceInputEnabled,
} from "../models/settings";
import { getPersonality } from "../constants/personalities";
import {
  createSession,
  addMessage as persistMessage,
  getMessages as getSessionMessages,
  listSessions,
  deleteSession,
  setSessionSummary,
  pruneSessions,
  setMessageFeedback,
  setMessageMeta,
  upsertMessage,
  ChatSession,
} from "../services/chatHistory";
import { summarizeConversation } from "../services/summarize";
import { titleFromQuestion } from "./chat/sessionTitle";
import { chatModelName, chatModelNameById } from "./chat/modelName";
import { startAppMemoryTracking } from "../services/telemetry";
import { stripThinking } from "../services/thinking";
import { cleanCitations } from "../services/citations";
import type { RootStackParamList } from "./navigation/types";
import { publishChatBridge } from "./navigation/chatBridge";
import { EvaluationScreen } from "./EvaluationScreen";
import { takePendingEvalRequest } from "../eval/deviceEvalRequest";
import { DEVICE_EVAL_ON } from "../eval/deviceEvalGate";
import type { EvalRequest } from "../eval/deviceEvalRequest.pure";
import { ChatHeader } from "./ChatHeader";
import { Banner, Button, IconButton, Progress, Screen, Sheet, Text, useAnnounce, useToast } from "./components";
import { footerBottom } from "./components/Screen";
import { useTheme, useTokens } from "./theme";
import { answer as runAnswer, deepen as runDeepen, effectiveAnswerModel, type AnswerContext } from "./chat/answerApi";
import type { AnswerEvent, AnswerHandle, AnswerRequest, AnswerResult } from "./chat/answerEvents";
import { answerPhase, answerReducer, asInterrupted, attachAnswer, initialAnswer, type AnswerState } from "./chat/answerReducer";
import { answerTextForHistory, toStoredAnswer } from "./chat/answerRecord";
import { historyTurns, itemsFromRecords, sessionToResume, updateAnswer, type ChatItem } from "./chat/chatItems";
import { loadCrashMessage, phaseAnnouncement, withDisplayNames } from "./chat/presentation";
import { consumeLoadCrash } from "./chat/loadCrashApi";
import { formatForCopy, formatForShare, type ShareLabels } from "./chat/shareFormat";
import { answerSourceSplit } from "./chat/sourceLabel";
import { AssistantMessage } from "./chat/AssistantMessage";
import { ModelLoadError } from "../inference/loadError";
import type { ModelErrorKind } from "./chat/modelError";
import { ChatEmptyState, ChatModelError, ChatModelLoading, SourceSheet, UserMessage } from "./chat/ChatPieces";
import { Composer } from "./chat/Composer";
import { modelStatus } from "./chat/composerState";
import { placesForCopy, sourceName } from "./chat/placesFormat";
import { locate } from "./chat/locationApi";
import { suggestionsFor } from "./chat/suggestions";
import { installedKnowledgeIds } from "./chat/knowledgeApi";
import { batchAnimatesLayout, flushDelay } from "./chat/streamBatch";
import { createBottomPin, FOLLOW_SLACK, heightChanged, jumpToLatestShown, keepEndOnResize } from "./chat/listPin";
import { EnterOnce } from "./chat/EnterOnce";
import { Swap } from "./chat/Swap";
import { Reveal } from "./chat/Reveal";
import { loadStripKey, loadStripShown } from "./chat/loadStrip";
import { headerVeil } from "./chat/listEdge";
import { useMotion } from "./theme/motion";
import { shouldWarmPtLexicon, warmPtLexicon } from "./chat/lexiconWarmup";

const VERBATIM_MESSAGE_COUNT = 6;

/** A model's catalog entry by id (built-in catalog or models picked from the Hugging Face browser). */
async function catalogModelById(id: string, kind: "llm" | "embedding"): Promise<CatalogModel | undefined> {
  const candidates = [...MODEL_CATALOG, ...(await listDiscoveredModels())];
  return candidates.find((m) => m.id === id && m.kind === kind);
}

async function resolveActiveModel(kind: "llm" | "embedding"): Promise<CatalogModel> {
  const activeId = await getActiveModelId(kind);
  const fallback = REQUIRED_MODELS.find((m) => m.kind === kind)!;
  if (!activeId) return fallback;
  // Models picked from the Hugging Face browser live in discoveredModels, not MODEL_CATALOG.
  const candidates = [...MODEL_CATALOG, ...(await listDiscoveredModels())];
  const found = candidates.find((m) => m.id === activeId && m.kind === kind);
  if (!found) console.warn(`[ChatScreen] active ${kind} model "${activeId}" not found, using ${fallback.id}`);
  return found ?? fallback;
}

/** The answer text to copy or share: the deepest pass, cleaned of reasoning and invented citations. */
function answerForCopy(a: AnswerState): string {
  return cleanCitations(stripThinking(answerTextForHistory(a)), a.sources.length);
}

interface ActiveAnswer {
  messageId: string;
  handle: AnswerHandle | null;
}

// Module scope, so it survives a remount of this screen (FS-1: the navigation remounts when the system
// font changes). A new chat screen waits for the answer the old one was saving before it reopens the session.
let pendingWrite: Promise<void> = Promise.resolve();
let draftInput = "";

export function ChatScreen({ onRelaunchWizard }: { onRelaunchWizard?: () => void }) {
  const tk = useTokens();
  const { reduceMotion } = useTheme();
  const insets = useSafeAreaInsets();
  const motion = useMotion();
  // For callbacks that must stay stable (the event flush, finish).
  const motionRef = useRef(motion);
  motionRef.current = motion;
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const announce = useAnnounce();
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const locale = i18n.language?.startsWith("pt") ? "pt-BR" : "en-US";

  const [items, setItems] = useState<ChatItem[]>([]);
  const itemsRef = useRef<ChatItem[]>([]);
  // Answers asked in this run (not restored from history): only these may take focus (the city prompt).
  const askedIds = useRef<Set<string>>(new Set());
  // Rows sent in this run that haven't entered yet (SEND-MOTION S1): each enters once.
  const enterIds = useRef<Map<string, "now" | "afterSwap">>(new Map());
  // Which conversation the list shows (TR-10): changes on switching or starting one, not when the first
  // question creates the session (that would crossfade the list away mid-send).
  const [conversationKey, setConversationKey] = useState("initial");
  itemsRef.current = items;
  // The unsent question survives a remount too (FS-1).
  const [input, setInput] = useState<string>(draftInput);
  useEffect(() => {
    draftInput = input;
  }, [input]);
  const [ready, setReady] = useState(false);
  // The model is loaded and the offline library is being indexed (first run): sending waits (IX-1).
  const [indexing, setIndexing] = useState(false);
  // The model loads have been started (Tusk, boot P1): answer() waits for a load in progress, so asking
  // doesn't wait for ready. Before this (resolving which model) and after a load error, it does.
  const [modelsRequested, setModelsRequested] = useState(false);
  const [loadStatus, setLoadStatus] = useState<{ label: string; progress?: number }>({ label: t("chatScreen.initializingCore") });
  const [loadError, setLoadError] = useState<string | null>(null);
  const canAsk = modelsRequested && !loadError;
  // The offline library is ready (seeded, or nothing to seed). A question asked before that waits for it: on a
  // first boot the index is filled only after the model loads, and a search then would claim "not in this
  // phone's library" for what is about to be there (Prism HX-1, Harbor 5f7d9ca).
  const libraryReady = useRef(makeGate(true));
  const [waitingLibrary, setWaitingLibrary] = useState<string | null>(null);
  // The seed failed part-way: answers may miss what wasn't indexed, and must say so (Prism HX-1 residual).
  const [libraryIncomplete, setLibraryIncomplete] = useState(false);
  // The engine's own cause for a failed load (Tusk 9ec677e: ModelLoadError.kind), when it gives one.
  const [loadErrorKind, setLoadErrorKind] = useState<ModelErrorKind | undefined>(undefined);
  const [activeModel, setActiveModel] = useState<CatalogModel | null>(null);
  const [voiceInputEnabled, setVoiceInputEnabledState] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [deviceEvalRequest, setDeviceEvalRequest] = useState<EvalRequest | null>(null);
  const [openSource, setOpenSource] = useState<{ messageId: string; index: number } | null>(null);

  // The running answer. `generating` mirrors it for rendering; the ref is the
  // synchronous guard that closes the double-send race (set before any await).
  const activeRef = useRef<ActiveAnswer | null>(null);
  const [active, setActive] = useState<ActiveAnswer | null>(null);
  // Answers whose model is done but whose text is still showing its last words (Prism CX-12): send and
  // stop swap only once the text has finished, with the receipt.
  const [revealing, setRevealing] = useState<ReadonlySet<string>>(new Set());
  const generating = active !== null || revealing.size > 0;
  const [stopping, setStopping] = useState(false);

  const memorySettingsRef = useRef<MemorySettingsType>(DEFAULT_MEMORY_SETTINGS);
  const sessionSummaryRef = useRef<string | null>(null);
  const backgroundTaskRef = useRef<Promise<void> | null>(null);
  const showSettingsRef = useRef(false);
  const inputRef = useRef<TextInput>(null);
  const listRef = useRef<FlatList<ChatItem>>(null);

  // ---- list scrolling: follow the newest text unless the user scrolled up ----
  const followBottom = useRef(true);
  // Whether "Jump to latest" shows while an answer writes and otherwise (Prism CX-6, jumpToLatestShown).
  const [jump, setJump] = useState({ writing: false, idle: false });
  const [composerHeight, setComposerHeight] = useState(0);
  const onComposerLayout = useCallback((e: LayoutChangeEvent) => setComposerHeight(e.nativeEvent.layout.height), []);
  // One anchor for every move to the end (SEND-MOTION D4): snaps for small growth, one native glide for
  // content appearing at once, and no snap ever cutting a glide (the send's jumps).
  const reduceMotionRef = useRef(reduceMotion);
  reduceMotionRef.current = reduceMotion;
  // FlatList.scrollToEnd does nothing without items (VirtualizedList returns early): the empty state is the
  // list's ListEmptyComponent, so there the scroll view's own scrollToEnd (iPhone v9: the becc519 re-pin
  // never moved the empty state, and the last suggestion stayed cut by a 3-line composer).
  const scrollListToEnd = useCallback((animated: boolean) => {
    const list = listRef.current;
    if (!list) return;
    if (itemsRef.current.length > 0) list.scrollToEnd({ animated });
    else (list.getNativeScrollRef() as unknown as ScrollView | null)?.scrollToEnd({ animated });
  }, []);
  const bottomPin = useMemo(
    () =>
      createBottomPin({
        // Content: deferred a frame (on Android the reported content size can lag one layout behind).
        // The list's own layout: in the same frame, the content didn't change (audit #8/#10).
        scrollToEnd: (animated, sameFrame) =>
          sameFrame ? scrollListToEnd(animated) : requestAnimationFrame(() => scrollListToEnd(animated)),
      }),
    []
  );
  const glideToBottom = useCallback(() => bottomPin.glide(!reduceMotionRef.current), [bottomPin]);
  useEffect(() => () => bottomPin.cancel(), [bottomPin]);
  // Content growing while following the end (a new line, a block entering, a restored session rendering)
  // glides, chained by the pin's guard so no glide is restarted or cut (audit #13, Prism F2-5); sending
  // arms one that starts once the question is measured. Reduce motion snaps.
  const onListContentSize = useCallback(() => {
    if (followBottom.current) bottomPin.pin(!reduceMotionRef.current);
  }, [bottomPin]);
  // The keyboard changes the list's height on every frame: pin the last message then, and skip layouts that
  // leave the height as it was (audit #8/#10). During a glide (the keyboard closing on send) it's held.
  const listHeight = useRef<number | null>(null);
  const onListLayout = useCallback((e: LayoutChangeEvent) => {
    const h = e.nativeEvent.layout.height;
    if (!heightChanged(listHeight.current, h)) return;
    const shrank = listHeight.current != null && h < listHeight.current;
    listHeight.current = h;
    // The empty state too: its last suggestion stays whole as the keyboard or the composer takes room.
    if (keepEndOnResize({ following: followBottom.current, empty: itemsRef.current.length === 0, shrank })) {
      bottomPin.pin(!reduceMotionRef.current, "layout");
    }
  }, [bottomPin]);
  const onListScroll = useCallback((e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const { contentOffset, contentSize, layoutMeasurement } = e.nativeEvent;
    // Our own glide passes through "far from the end" on its way there: it still follows.
    if (bottomPin.gliding()) return;
    const distance = Math.max(0, contentSize.height - layoutMeasurement.height - contentOffset.y);
    followBottom.current = distance < FOLLOW_SLACK;
    const at = { distance, viewport: layoutMeasurement.height };
    const next = { writing: jumpToLatestShown({ ...at, generating: true }), idle: jumpToLatestShown({ ...at, generating: false }) };
    // A render only when the button would change.
    setJump((j) => (j.writing === next.writing && j.idle === next.idle ? j : next));
  }, [bottomPin]);
  // A drag is the user's: a glide in flight or armed stops moving the list.
  const onListDrag = useCallback(() => bottomPin.cancel(), [bottomPin]);

  // ---- events: tokens batched (STREAM_FLUSH_MS) into one state update; everything else shows at once ----
  const pendingEvents = useRef<{ messageId: string; event: AnswerEvent }[]>([]);
  // Answers declined in this run (their warning flushed), until a follow-up runs them again.
  const declinedIds = useRef<Set<string>>(new Set());
  const flushTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const flushEvents = useCallback(() => {
    if (flushTimer.current) clearTimeout(flushTimer.current);
    flushTimer.current = null;
    const batch = pendingEvents.current;
    if (batch.length === 0) return;
    pendingEvents.current = [];
    // The end of a normal answer folds the snippet to its preview: one DS layout animation for that commit
    // (Prism F2-2). Not for a decline or with its warning: the text leaves through its Reveal there, and the
    // native animation on the same view made it vanish in one frame (iPhone v9).
    // The decline's warning and the done come in separate flushes of the same tick, before the items (and
    // itemsRef) show the decline: remember it here (probe cddf2a8: the done flush still got the animation).
    for (const { messageId, event } of batch) {
      if (event.type === "warning" && event.code === "weak_sources" && event.declined) declinedIds.current.add(messageId);
    }
    const declined = batch.some(({ messageId }) => declinedIds.current.has(messageId));
    if (batchAnimatesLayout(batch.map((b) => b.event), declined)) motionRef.current.animateNextLayout();
    setItems((prev) => {
      let next = prev;
      for (const { messageId, event } of batch) next = updateAnswer(next, messageId, (a) => answerReducer(a, event));
      return next;
    });
  }, []);
  const queueEvent = useCallback(
    (messageId: string, event: AnswerEvent) => {
      pendingEvents.current.push({ messageId, event });
      const delay = flushDelay(event);
      // A stage, the sources or done: flush now, with the tokens queued before it (order kept).
      if (delay === 0) flushEvents();
      else if (!flushTimer.current) flushTimer.current = setTimeout(flushEvents, delay);
    },
    [flushEvents]
  );

  // ---- screen reader: announce stage transitions of the running answer, never tokens ----
  const activeItem = active ? items.find((m) => m.id === active.messageId) : undefined;
  const activePhase = activeItem?.kind === "assistant" ? answerPhase(activeItem.answer) : null;
  const lastAnnounced = useRef<string | null>(null);
  useEffect(() => {
    if (!activePhase || activeItem?.kind !== "assistant") return;
    const key = `${activeItem.id}:${activePhase}`;
    if (lastAnnounced.current === key) return;
    lastAnnounced.current = key;
    // Stopped only to search a typed city: say nothing, the city search is announced next.
    if (activePhase === "stopped" && pendingCity.current?.id === activeItem.id) return;
    const a = phaseAnnouncement(activePhase, activeItem.answer, t, libraryIncomplete);
    if (a) announce(a.message, { assertive: a.assertive });
  }, [activePhase, activeItem, announce, t]);

  // ---- settings, sessions, models ----
  const refreshSessions = useCallback(async () => setSessions(await listSessions()), []);

  useEffect(() => {
    (async () => {
      await pendingWrite;
      setShowSuggestions(!(await getHidePromptIdeas()));
      memorySettingsRef.current = await getMemorySettings();
      setVoiceInputEnabledState(await getVoiceInputEnabled());
      const list = await listSessions();
      setSessions(list);
      // Coming back soon after leaving (or after the system killed the app) continues the conversation.
      const resume = sessionToResume(list, Date.now());
      if (resume && itemsRef.current.length === 0 && !activeRef.current) {
        const records = await getSessionMessages(resume.id);
        if (itemsRef.current.length > 0 || activeRef.current) return;
        setItems(itemsFromRecords(records));
        setActiveSessionId(resume.id);
        sessionSummaryRef.current = resume.summary;
      }
    })();
  }, []);

  // initModels reads t and locale through refs (audit #36): with them as deps, a language change in Settings
  // re-ran the whole init under the pushed screen (setReady(false), loads, seeding). One init at a time:
  // coming back from a screen mid-load no longer starts a second, overlapping one.
  const tRef = useRef(t);
  tRef.current = t;
  const localeRef = useRef(locale);
  localeRef.current = locale;
  const initInFlight = useRef(false);
  const initModels = useCallback(async () => {
    if (initInFlight.current) return;
    initInFlight.current = true;
    const t = tRef.current;
    const locale = localeRef.current;
    // A new gate for this init; opened once the library is seeded (or on failure, so nothing hangs).
    if (libraryReady.current.done) libraryReady.current = makeGate(false);
    const gate = libraryReady.current;
    try {
      setLoadError(null);
      setReady(false);
      setModelsRequested(false);
      // Preload the model answer() will really use (Tusk, CR-1/CR-2): on a low-RAM phone the saved large model
      // may be skipped, or one that crashed the app; loading the saved one would risk the very OOM it avoids.
      const effective = await effectiveAnswerModel();
      // Not found in the catalog: no preload rather than the saved model (it may be the one CR-1 skips);
      // answer() loads the right one on demand.
      const llm = effective ? (await catalogModelById(effective.id, "llm")) ?? null : null;
      const emb = await resolveActiveModel("embedding");
      if (llm) setActiveModel(llm);
      const loadingLabel = llm ? t("chat.model.loading", { label: chatModelName(llm, t) }) : t("chatScreen.initializingCore");
      setLoadStatus({ label: loadingLabel });
      // Both loads start here, before any question: the search's query vector waits in the embedder's queue
      // behind its own load, and answer() waits for the model's (Tusk). No model can run (null): no preload;
      // answer() says no_model.
      const loads = Promise.all([
        llm
          ? llamaEngine.load(llm.filename, {
              // The real load progress (Tusk 70141cf): reading the weights. At 1 the first boot still compiles
              // Metal on iOS (~22 s), so the bar turns indeterminate with "Preparing the model…" until done.
              onProgress: (f) =>
                setLoadStatus(f >= 1 ? { label: t("chat.model.preparing") } : { label: loadingLabel, progress: f }),
            })
          : Promise.resolve(),
        embeddingEngine.load(emb.filename),
      ]);
      setModelsRequested(true);
      await loads;
      startAppMemoryTracking();
      // Portuguese app: build the PT lexicon now, off the send path (audit #5: the first Portuguese question froze
      // the JS thread for its 6.5 MB and 173k titles). After the loads, not during: no extra heap next to the GGUF.
      if (shouldWarmPtLexicon(locale)) setTimeout(() => warmPtLexicon(), 0);
      const stopProgress = onSeedProgress((p) =>
        setLoadStatus({
          label: t("chat.model.indexing", { done: p.done.toLocaleString(locale), total: p.total.toLocaleString(locale) }),
          progress: p.total > 0 ? p.done / p.total : undefined,
        })
      );
      setIndexing(true);
      // A failed seed is not a model failure: the model is loaded, the library is incomplete (own try).
      try {
        await seedKnowledgeBaseIfEmpty();
        setLibraryIncomplete(false);
      } catch (e: any) {
        console.warn("[ChatScreen] seeding the offline library failed:", e?.message ?? e);
        setLibraryIncomplete(true);
      } finally {
        stopProgress();
        setIndexing(false);
        gate.open();
      }
      setReady(true);
    } catch (e: any) {
      gate.open();
      // The native message is the one worth showing (the RAM estimate is only in the log now).
      setLoadErrorKind(e instanceof ModelLoadError ? e.kind : undefined);
      setLoadError(e instanceof ModelLoadError ? e.native || e.message : e?.message ?? String(e));
    } finally {
      initInFlight.current = false;
    }
  }, []);

  useEffect(() => {
    initModels();
  }, [initModels]);

  // A download started elsewhere (Settings) keeps running; toast its completion here,
  // only for a transition this screen watched and not while Settings is open.
  const seenDownloadingRef = useRef<Set<string>>(new Set());
  useEffect(() => {
    return subscribeDownloads(() => {
      for (const { assetId, state: dl } of listDownloadStates()) {
        if (dl.downloading) {
          seenDownloadingRef.current.add(assetId);
        } else if (seenDownloadingRef.current.has(assetId)) {
          seenDownloadingRef.current.delete(assetId);
          if (dl.error || showSettingsRef.current) continue;
          (async () => {
            const known = [...MODEL_CATALOG, ...CORPUS_CATALOG, ...(await listDiscoveredModels())].find((m) => m.id === assetId);
            toast({ message: t("chatScreen.modelDownloadComplete", { label: known ? chatModelName(known, t) : assetId }), tone: "success" });
          })();
        }
      }
    });
  }, [t, toast]);

  // An evaluation request written into the app's private storage by a development machine
  // (scripts/eval-iphone.mjs over devicectl, adb run-as on Android): development builds and builds made
  // with EXPO_PUBLIC_DEVICE_EVAL=1 only (src/eval/deviceEvalGate.ts). A shipped build never polls.
  useEffect(() => {
    if (!DEVICE_EVAL_ON || !ready || deviceEvalRequest) return;
    let cancelled = false;
    const check = async () => {
      if (cancelled || activeRef.current) return;
      try {
        const request = await takePendingEvalRequest();
        if (request && !cancelled) setDeviceEvalRequest(request);
      } catch (e: any) {
        console.warn("[EVAL] could not read device request:", e?.message ?? e);
      }
    };
    check();
    const id = setInterval(check, 3000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [ready, deviceEvalRequest]);

  // ---- running answers ----
  const cancelBackgroundTask = useCallback(async () => {
    if (!backgroundTaskRef.current) return;
    await llamaEngine.stop();
    await backgroundTaskRef.current.catch(() => {});
    backgroundTaskRef.current = null;
  }, []);

  const stopActive = useCallback(async () => {
    const current = activeRef.current;
    if (!current?.handle) return;
    setStopping(true);
    await current.handle.stop();
  }, []);

  /**
   * Runs one answer() into message `messageId` and persists the result. The
   * caller has already claimed activeRef synchronously.
   */
  const runInto = useCallback(
    async (messageId: string, start: (onEvent: (e: AnswerEvent) => void, ctx: AnswerContext) => AnswerHandle) => {
      const [maxTokens, activePersonalityId, customPrompt] = await Promise.all([
        getMaxTokens(),
        getPersonalityId(),
        getCustomSystemPrompt(),
      ]);
      const personality = getPersonality(activePersonalityId);
      const priorItems = itemsRef.current.filter((m) => m.id !== messageId);
      const ctx: AnswerContext = {
        systemPrompt: activePersonalityId === "custom" ? customPrompt ?? undefined : personality.systemPrompt,
        styleReminder: activePersonalityId === "custom" ? undefined : personality.styleReminder,
        history: { summary: sessionSummaryRef.current, turns: historyTurns(priorItems, VERBATIM_MESSAGE_COUNT) },
        maxTokens,
      };
      // A local copy of the state, so what gets persisted doesn't depend on render timing.
      let local = (itemsRef.current.find((m) => m.id === messageId) as Extract<ChatItem, { kind: "assistant" }>).answer;
      let attached = false;
      const handle = start((event) => {
        // An instant answer (calculator, small talk, emergency numbers, first-aid card) sends its text and
        // its done during start(), before handle.answerId is attached below: the reducer drops events of an
        // unknown answer, so the saved copy came out empty and replaced the text that had flashed on screen.
        // This run's first event carries its id (a Deepen's message already holds the earlier answer's).
        if (!attached) {
          attached = true;
          local = attachAnswer(local, event.answerId);
        }
        local = answerReducer(local, event);
        queueEvent(messageId, event);
      }, ctx);
      if (!local.answerIds.includes(handle.answerId)) local = attachAnswer(local, handle.answerId);
      setItems((prev) => updateAnswer(prev, messageId, (a) => attachAnswer(a, handle.answerId)));
      activeRef.current = { messageId, handle };
      setActive(activeRef.current);
      const result: AnswerResult = await handle.done;
      flushEvents();
      return { result, final: local };
    },
    [queueEvent, flushEvents]
  );

  const finish = useCallback(() => {
    // No layout animation here (was F2-1): since CX-12 an answer with text ends on screen when its reveal
    // drains, not in this commit; a decline's note mounts in it, and the native layout animation over a
    // view mounting in a Reveal made it blink in whole for a frame (Prism F2-9).
    activeRef.current = null;
    setActive(null);
    setStopping(false);
  }, []);

  const ask = useCallback(
    async (rawQuery: string) => {
      const query = rawQuery.trim();
      if (!query || activeRef.current || !canAsk) return;
      // Claim the answer slot before the first await.
      const assistantId = `${Date.now()}-a`;
      activeRef.current = { messageId: assistantId, handle: null };
      askedIds.current.add(assistantId);
      let written!: () => void;
      pendingWrite = new Promise<void>((r) => (written = r));
      setActive(activeRef.current);
      setInput("");
      // Close the keyboard through keyboard-controller so it tracks the close: when the keyboard
      // went away while the list swapped (Prism K-2: composer left floating with the keyboard shut),
      // its progress stayed at 1. The answer is read next anyway.
      KeyboardController.dismiss();
      // And leave the field unfocused, as the mockup (with a hardware keyboard, dismiss() keeps the caret).
      inputRef.current?.blur();
      // No scroll yet: the question isn't in the list. The content change it makes glides, once (D4).
      followBottom.current = true;
      bottomPin.armGlide();

      const userItem: ChatItem = { kind: "user", id: `${Date.now()}-u`, text: query };
      // The first question replaces the empty state: it enters after that state has left (F2-4b).
      const enterMode = itemsRef.current.length === 0 ? "afterSwap" : "now";
      enterIds.current.set(userItem.id, enterMode);
      enterIds.current.set(assistantId, enterMode);
      const assistantItem: ChatItem = {
        kind: "assistant",
        id: assistantId,
        question: query,
        answer: { answerIds: [], sources: [] },
        feedback: null,
      };
      setItems((prev) => [...prev, userItem, assistantItem]);
      itemsRef.current = [...itemsRef.current, userItem, assistantItem];

      let sessionId = activeSessionId;
      const isNewSession = !sessionId;
      try {
        await cancelBackgroundTask();
        if (!sessionId) {
          // TT-1: titled by the question itself, at once (the setting's hint: "Uses the first question as the title").
          sessionId = (await createSession(memorySettingsRef.current.autoGenerateTitles ? titleFromQuestion(query) : undefined)).id;
          setActiveSessionId(sessionId);
          void refreshSessions();
        }
        await persistMessage(sessionId, "user", query, userItem.id);

        const request: AnswerRequest = { query };
        if (!libraryReady.current.done) {
          setWaitingLibrary(assistantId);
          await libraryReady.current.promise;
          setWaitingLibrary(null);
        }
        const { result, final } = await runInto(assistantId, (onEvent, ctx) => runAnswer(request, onEvent, ctx));

        await persistMessage(sessionId, "assistant", answerTextForHistory(final), assistantId);
        await setMessageMeta(assistantId, stored(final));
        impact(result.outcome === "success" ? ImpactFeedbackStyle.Light : ImpactFeedbackStyle.Medium);

        const settings = memorySettingsRef.current;
        const sid = sessionId;
        if (!isNewSession && settings.autoSummarize && result.outcome === "success") {
          const all = itemsRef.current;
          if (Math.floor(all.length / 2) > settings.historyTurnThreshold) {
            const older = historyTurns(all.slice(0, -VERBATIM_MESSAGE_COUNT), Number.MAX_SAFE_INTEGER);
            if (older.length > 0) {
              const previousSummary = sessionSummaryRef.current;
              backgroundTaskRef.current = summarizeConversation(older, previousSummary)
                .then(async (summary) => {
                  sessionSummaryRef.current = summary;
                  await setSessionSummary(sid, summary);
                })
                .catch(() => {})
                .finally(() => {
                  backgroundTaskRef.current = null;
                });
            }
          }
        }
        await pruneSessions(settings.maxSavedSessions);
      } catch (e: any) {
        // Session or storage failures: show them on the answer instead of leaving it spinning.
        const message = e?.message ?? String(e);
        setItems((prev) =>
          updateAnswer(prev, assistantId, (a) => ({
            ...a,
            fast: { text: a.fast?.text ?? "", stage: null, outcome: "error", error: { code: "unknown", message } },
          }))
        );
      } finally {
        written();
        finish();
      }
    },
    // canAsk, not ready: asking works while the model loads (a stale closure here swallowed the first tap, Harbor).
    [activeSessionId, canAsk, cancelBackgroundTask, runInto, refreshSessions, bottomPin, finish]
  );

  /**
   * A follow-up pass on an existing answer: Deepen, the model after an
   * extractive answer, or the same question again for a typed city / after
   * the location permission (that one replaces the answer in place).
   */
  const followUp = useCallback(
    async (messageId: string, kind: "deep" | "fast" | { place?: string; answerAnyway?: boolean }) => {
      const item = itemsRef.current.find((m) => m.id === messageId);
      if (!item || item.kind !== "assistant" || activeRef.current || !canAsk) return;
      const sessionId = activeSessionId;
      // A follow-up makes the answer live again, restored or not: its blocks move (iPhone v9, F2-2: on a
      // reopened conversation "Answer with AI" ran with plain views, and the declined text vanished in a frame).
      askedIds.current.add(messageId);
      declinedIds.current.delete(messageId);
      activeRef.current = { messageId, handle: null };
      setActive(activeRef.current);
      let written!: () => void;
      pendingWrite = new Promise<void>((r) => (written = r));
      if (typeof kind === "object") {
        const fresh = (items: ChatItem[]) =>
          updateAnswer(items, messageId, () => ({ answerIds: [], sources: [] })).map((m) =>
            m.kind === "assistant" && m.id === messageId ? { ...m, interrupted: false } : m
          );
        itemsRef.current = fresh(itemsRef.current);
        setItems(fresh);
      }
      try {
        const { final } = await runInto(messageId, (onEvent, ctx) =>
          kind === "deep"
            ? runDeepen(item.question, item.answer.sources, onEvent, ctx)
            : kind === "fast"
              ? runAnswer({ query: item.question, tier: "fast" }, onEvent, ctx)
              : runAnswer({ query: item.question, place: kind.place, answerAnyway: kind.answerAnyway }, onEvent, ctx)
        );
        // A redo replaces the answer: keep the saved text in step (Deepen only adds to it).
        if (typeof kind === "object" && sessionId) await upsertMessage(sessionId, "assistant", answerTextForHistory(final), messageId);
        await setMessageMeta(messageId, stored(final));
      } catch (e: any) {
        console.warn("[ChatScreen] follow-up failed:", e?.message ?? e);
      } finally {
        written();
        finish();
      }
    },
    [canAsk, runInto, finish, activeSessionId]
  );

  // Retry only the library indexing after a failed seed.
  const reseed = useCallback(async () => {
    const stopProgress = onSeedProgress((p) =>
      setLoadStatus({
        label: t("chat.model.indexing", { done: p.done.toLocaleString(locale), total: p.total.toLocaleString(locale) }),
        progress: p.total > 0 ? p.done / p.total : undefined,
      })
    );
    setIndexing(true);
    try {
      await seedKnowledgeBaseIfEmpty();
      setLibraryIncomplete(false);
    } catch (e: any) {
      console.warn("[ChatScreen] seeding the offline library failed again:", e?.message ?? e);
    } finally {
      stopProgress();
      setIndexing(false);
    }
  }, [t, locale]);

  // A model error below a conversation is its last item: bring it into view.
  useEffect(() => {
    if (!loadError || itemsRef.current.length === 0) return;
    followBottom.current = true;
    glideToBottom();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadError]);

  // The model that will really answer (CR-1): the engine's own choice, so header and answer() agree.
  const [effective, setEffective] = useState<Awaited<ReturnType<typeof effectiveAnswerModel>>>(null);
  useEffect(() => {
    effectiveAnswerModel()
      .then(setEffective)
      .catch(() => undefined);
  }, [ready, activeModel?.id]);

  // Installed knowledge, so the empty chat only suggests questions with an on-topic source here (RT-1).
  // Re-read when the model state changes (setup and the Knowledge screen run before the chat is ready).
  const [knowledge, setKnowledge] = useState<string[]>([]);
  useEffect(() => {
    installedKnowledgeIds()
      .then(setKnowledge)
      .catch(() => undefined);
  }, [ready]);

  // Once, when the chat opens after a model load killed the app (Boar CR-2): say what happened and where to go.
  const [loadCrash, setLoadCrash] = useState<string | null>(null);
  useEffect(() => {
    // CR-3: the marker may name the models by file path; show the catalog's names.
    Promise.all([consumeLoadCrash(), listDiscoveredModels().catch(() => [])])
      .then(([c, discovered]) => setLoadCrash(loadCrashMessage(withDisplayNames(c, [...MODEL_CATALOG, ...discovered], t), t)))
      .catch(() => undefined);
    // Once per chat screen: consume() clears the mark, so it never shows twice.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // A city typed during "Finding your location…" runs as soon as the stopped answer has finished.
  const pendingCity = useRef<{ id: string; city: string } | null>(null);
  useEffect(() => {
    if (active || !pendingCity.current) return;
    const { id, city } = pendingCity.current;
    pendingCity.current = null;
    followUp(id, { place: city });
  }, [active, followUp]);

  // "Use my location": explain in context first (only while the permission is undetermined).
  const [locationExplain, setLocationExplain] = useState<((ok: boolean) => void) | null>(null);
  const locateAndAsk = useCallback(
    async (messageId: string) => {
      const result = await locate(() => new Promise<boolean>((resolve) => setLocationExplain(() => resolve)));
      setLocationExplain(null);
      if (result.status === "ok") followUp(messageId, {});
    },
    [followUp]
  );

  // Backgrounding stops the answer (same path as Stop) and marks it interrupted.
  useEffect(() => {
    const sub = AppState.addEventListener("change", (next) => {
      if (next !== "background" || !activeRef.current) return;
      const id = activeRef.current.messageId;
      setItems((prev) => prev.map((m) => (m.kind === "assistant" && m.id === id ? { ...m, interrupted: true } : m)));
      activeRef.current.handle?.stop();
    });
    return () => sub.remove();
  }, []);

  // The screen going away mid-answer (remount on a font change, FS-1): stop the answer, and save it as
  // interrupted so the reopened conversation offers Try again instead of a question with no answer.
  const unmounted = useRef(false);
  useEffect(
    () => () => {
      unmounted.current = true;
      activeRef.current?.handle?.stop();
    },
    []
  );
  const stored = (a: AnswerState) => toStoredAnswer(unmounted.current ? asInterrupted(a) : a);

  /** Stops whatever runs and waits for it, before swapping the conversation out. */
  const stopAndWait = useCallback(async () => {
    await activeRef.current?.handle?.stop();
    await cancelBackgroundTask();
  }, [cancelBackgroundTask]);

  const resetToNewChat = useCallback(async () => {
    await stopAndWait();
    setConversationKey(`new-${Date.now()}`);
    setItems([]);
    setActiveSessionId(null);
    sessionSummaryRef.current = null;
    // CR-5: the crash banner belongs to the conversation it opened in; once, not in every new chat.
    setLoadCrash(null);
    impact(ImpactFeedbackStyle.Medium);
  }, [stopAndWait]);

  const selectSession = useCallback(
    async (id: string) => {
      await stopAndWait();
      const records = await getSessionMessages(id);
      followBottom.current = true;
      setConversationKey(id);
      setItems(itemsFromRecords(records));
      setActiveSessionId(id);
      setLoadCrash(null);
      sessionSummaryRef.current = sessions.find((s) => s.id === id)?.summary ?? null;
    },
    [stopAndWait, sessions]
  );

  const removeSession = useCallback(
    async (id: string) => {
      // Stop first: a finishing answer would otherwise be saved into the deleted session.
      if (id === activeSessionId) await resetToNewChat();
      await deleteSession(id);
      await refreshSessions();
    },
    [activeSessionId, refreshSessions, resetToNewChat]
  );

  const openSettings = useCallback(() => navigation.navigate("Settings"), [navigation]);
  // Stable props for the memoized header and composer (the screen re-renders on every streamed frame).
  const openModels = useCallback(() => navigation.navigate("Models"), [navigation]);
  const openDrawer = useCallback(() => {
    refreshSessions();
    navigation.dispatch(DrawerActions.openDrawer());
  }, [navigation, refreshSessions]);
  const send = useCallback(() => ask(input), [ask, input]);
  // Stable props for the empty state (memo): typing re-renders this screen on every key (audit #7).
  const suggestions = useMemo(
    () => (showSuggestions ? suggestionsFor(activeModel?.id, i18n.language, knowledge) : []),
    [showSuggestions, activeModel?.id, i18n.language, knowledge]
  );
  const openKnowledge = useCallback(() => navigation.navigate("Knowledge"), [navigation]);
  const fillQuestion = useCallback((q: string) => {
    setInput(q);
    inputRef.current?.focus();
  }, []);
  const veil = useMemo(() => headerVeil(tk.color.bg.canvas), [tk.color.bg.canvas]);
  const listContentStyle = useMemo(
    // paddingTop = the header veil's height: at the top the veil covers only this empty space (Prism CX-3).
    () => ({ paddingHorizontal: tk.space.gutterChat, paddingTop: tk.space.base, paddingBottom: tk.space.base, gap: tk.space.cardGap, flexGrow: 1 }),
    [tk]
  );

  // Set while another screen (Settings, Models, ...) is pushed on top of the chat;
  // also keeps download toasts meant for that screen out of the chat.
  useEffect(
    () =>
      navigation.addListener("blur", () => {
        showSettingsRef.current = true;
      }),
    [navigation]
  );

  // Returning from any pushed screen (drawer, error card, back gesture): pick up what may have changed there.
  useFocusEffect(
    useCallback(() => {
      if (!showSettingsRef.current) return;
      showSettingsRef.current = false;
      (async () => {
        getVoiceInputEnabled().then(setVoiceInputEnabledState);
        getHidePromptIdeas().then((hide) => setShowSuggestions(!hide));
        installedKnowledgeIds().then(setKnowledge).catch(() => undefined);
        effectiveAnswerModel().then(setEffective).catch(() => undefined);
        // Settings loads a newly chosen LLM itself; only re-run the full init if what's loaded isn't the model
        // answer() will use (the effective one, CR-1: may differ from the saved one on a low-RAM phone).
        const effective = await effectiveAnswerModel();
        const llm = effective ? await catalogModelById(effective.id, "llm") : undefined;
        if (ready && llm && llamaEngine.getModelInfo()?.filename === llm.filename) setActiveModel(llm);
        else initModels();
      })();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ready, initModels])
  );

  useEffect(() => {
    publishChatBridge({
      sessions,
      activeSessionId,
      generating,
      newChat: resetToNewChat,
      selectSession,
      deleteSession: removeSession,
      refreshSessions,
      // Suggestions live in the empty state of a new chat; opening them from the menu also undoes a
      // "don't show again" (as the original menu's Prompt Ideas did).
      openPromptIdeas: () => {
        setHidePromptIdeas(false).catch(() => {});
        setShowSuggestions(true);
        resetToNewChat();
      },
    });
  }, [sessions, activeSessionId, generating, resetToNewChat, selectSession, removeSession, refreshSessions]);

  // ---- per-message actions ----
  const shareLabels: ShareLabels = useMemo(
    () => ({
      sources: t("chat.share.sources"),
      answeredOffline: t("chat.share.answeredOffline"),
      sourcePassage: t("chat.share.sourcePassage"),
      myDocuments: t("chat.share.myDocuments"),
      noOfflineSource: t("chat.receipt.noOfflineSource"),
      modelName: (id: string, label: string) => chatModelNameById(id, label, t),
      calculator: t("chat.receipt.calculator"),
    }),
    [t]
  );

  const rate = useCallback(async (id: string, rating: "up" | "down") => {
    const current = itemsRef.current.find((m) => m.id === id);
    if (current?.kind !== "assistant") return;
    const next = current.feedback === rating ? null : rating;
    setItems((prev) => prev.map((m) => (m.kind === "assistant" && m.id === id ? { ...m, feedback: next } : m)));
    await setMessageFeedback(id, next);
  }, []);

  const copyText = useCallback(
    async (text: string, message: string) => {
      await Clipboard.setStringAsync(text);
      toast({ message, icon: "check" });
    },
    [toast]
  );

  // Row actions go through a ref so every row gets the same handler object:
  // memoized rows then re-render only when their own item changes, not on
  // every streamed frame of another answer.
  const rowActions = useRef<RowActions>(null as unknown as RowActions);
  rowActions.current = {
    openSource: (id, index) => setOpenSource({ messageId: id, index }),
    deepen: (id) => followUp(id, "deep"),
    askModel: (id) => followUp(id, "fast"),
    // Retry redoes the answer in place; without a saved session (it failed before one existed) it asks again.
    retry: (id, question) => (activeSessionId ? followUp(id, {}) : ask(question)),
    rate: (id, r) => rate(id, r),
    copy: (item) => copyText(copyBody(item) ?? formatForCopy(answerForCopy(item.answer), item.answer.sources, shareLabels, answerSourceSplit(item.answer)?.cited), t("chat.actions.copied")),
    share: (item) => {
      const a = item.answer;
      const receipt = a.deep?.receipt ?? a.fast?.receipt ?? a.instantDone?.receipt;
      const places = copyBody(item);
      Share.share({
        message: places
          ? formatForShare(item.question, places, [], receipt, shareLabels, locale)
          : formatForShare(item.question, answerForCopy(a), a.sources, receipt, shareLabels, locale, answerSourceSplit(a)?.cited),
      });
    },
    answerAnyway: (id) => followUp(id, { answerAnyway: true }),
    city: (id, city) => {
      // Typed while the answer still waits for the GPS: stop it, then search the city once it has ended.
      if (activeRef.current?.messageId === id) {
        pendingCity.current = { id, city };
        void stopActive();
        return;
      }
      followUp(id, { place: city });
    },
    useLocation: (id) => locateAndAsk(id),
    // The offline maps live in Knowledge › Places (the button said "Get the map" and opened Settings).
    getMap: () => navigation.navigate("Knowledge"),
    copyReceipt: (text) => copyText(text, t("chat.receipt.copied")),
    revealing: (id, on) =>
      setRevealing((prev) => {
        if (prev.has(id) === on) return prev;
        const next = new Set(prev);
        if (on) next.add(id);
        else next.delete(id);
        return next;
      }),
    copyQuestion: (text) => copyText(text, t("chat.actions.questionCopied")),
    editQuestion: (text) => {
      setInput(text);
      inputRef.current?.focus();
    },
  };
  function copyBody(item: Extract<ChatItem, { kind: "assistant" }>): string | null {
    const a = item.answer;
    if (!a.places?.places.length) return null;
    return [
      placesForCopy(a.places.places, a.places.area.kind === "near" ? new Date() : null, locale, t),
      a.places.attribution.map((x) => `${sourceName(x.source, t)} (${x.license})`).join(" · "),
    ].join("\n\n");
  }
  const actions = useMemo<RowActions>(
    () => ({
      openSource: (id, i) => rowActions.current.openSource(id, i),
      deepen: (id) => rowActions.current.deepen(id),
      askModel: (id) => rowActions.current.askModel(id),
      retry: (id, q) => rowActions.current.retry(id, q),
      rate: (id, r) => rowActions.current.rate(id, r),
      copy: (item) => rowActions.current.copy(item),
      share: (item) => rowActions.current.share(item),
      answerAnyway: (id) => rowActions.current.answerAnyway(id),
      city: (id, c) => rowActions.current.city(id, c),
      useLocation: (id) => rowActions.current.useLocation?.(id),
      getMap: () => rowActions.current.getMap(),
      copyReceipt: (text) => rowActions.current.copyReceipt(text),
      revealing: (id, on) => rowActions.current.revealing(id, on),
      copyQuestion: (text) => rowActions.current.copyQuestion(text),
      editQuestion: (text) => rowActions.current.editQuestion(text),
    }),
    []
  );

  const activeId = active?.messageId ?? null;
  const renderItem = useCallback(
    ({ item }: { item: ChatItem }) => (
      <EnterOnce enter={enterIds.current.get(item.id) ?? false} onEntered={() => enterIds.current.delete(item.id)}>
        {item.kind === "user" ? (
          <UserRow text={item.text} actions={actions} />
        ) : (
          <AssistantRow
            item={item}
            active={activeId === item.id}
            waitingLibrary={waitingLibrary === item.id ? loadStatus.label : undefined}
            libraryIncomplete={libraryIncomplete}
            fresh={askedIds.current.has(item.id)}
            stopping={activeId === item.id && stopping}
            locale={locale}
            actions={actions}
          />
        )}
      </EnterOnce>
    ),
    [actions, activeId, stopping, locale, waitingLibrary, loadStatus.label, libraryIncomplete]
  );

  const downgradedFrom = useMemo(
    () => effective?.downgradedFrom && { ...effective.downgradedFrom, label: chatModelNameById(effective.downgradedFrom.id, effective.downgradedFrom.label, t) },
    [effective, t]
  );

  if (deviceEvalRequest) {
    return <EvaluationScreen deviceRequest={deviceEvalRequest} onClose={() => setDeviceEvalRequest(null)} />;
  }

  const sourceItem = openSource ? items.find((m) => m.id === openSource.messageId) : undefined;
  const source = sourceItem?.kind === "assistant" ? sourceItem.answer.sources[openSource!.index] ?? null : null;

  return (
    <Screen scroll={false} padded={false} ambient edges={["top", "left", "right"]}>
      <ChatHeader
        // Tier names (r4to via Boar): "Fast" / "More accurate"; the technical name only in Settings › Assistant › Details.
        activeModelLabel={effective ? chatModelNameById(effective.id, effective.label, t) : activeModel ? chatModelName(activeModel, t) : undefined}
        downgradedFrom={downgradedFrom}
        onOpenModels={openModels}
        voiceEnabled={voiceInputEnabled}
        onOpenDrawer={openDrawer}
      />
      {/* automaticOffset: the view's onLayout y is relative to its parent (below the safe area and the
          header), so without it the padding came out short and the composer sat behind the keyboard
          (Prism K-1, Android offline 06f508b). The native window position fixes any offset above. */}
      {/* The composer keeps its bottom gap (home indicator, navigation bar) with the keyboard up; the offset takes
          it back so the composer ends sm above the keyboard, at every frame of the keyboard's move (Composer). */}
      <KeyboardAvoidingView
        behavior="padding"
        automaticOffset
        keyboardVerticalOffset={tk.space.sm - footerBottom(insets.bottom, tk.space.xs, tk.space.sm)}
        style={{ flex: 1 }}
      >
        {loadCrash && (
          <View style={{ paddingHorizontal: tk.space.gutterChat, paddingTop: tk.space.sm }}>
            <Banner
              tone="warning"
              icon="alert-triangle"
              message={loadCrash}
              actionLabel={t("chat.loadCrash.seeModels")}
              onAction={() => {
                setLoadCrash(null);
                navigation.navigate("Models");
              }}
              onDismiss={() => setLoadCrash(null)}
              dismissLabel={t("chat.loadCrash.dismiss")}
            />
          </View>
        )}
        {/* With a conversation on screen, the model state sits above it; an empty chat shows it centred instead. */}
        {libraryIncomplete && !indexing && (
          <View style={{ paddingHorizontal: tk.space.gutterChat, paddingTop: tk.space.sm }}>
            <Banner tone="warning" icon="book-open" message={t("chat.library.incomplete")} actionLabel={t("chat.actions.retry")} onAction={reseed} />
          </View>
        )}
        {/* While the model loads or the library indexes, the status sits on top, above the chat or the empty state.
            It leaves in height (DS layout + exit; reduce motion: a short fade) instead of vanishing and
            lifting the chat 46 dp in one frame; a new status crossfades, a counter updates in place (Prism L3-1). */}
        <Reveal shown={loadStripShown({ loadError: !!loadError, ready, itemCount: items.length, modelsRequested })} appear={false}>
          <View style={{ paddingHorizontal: tk.space.gutterChat, paddingVertical: tk.space.sm, gap: tk.space.sm }}>
            <Swap swapKey={loadStripKey(loadStatus.label)}>
              {/* Seen, not read: the progress bar below speaks the same label (Prism CH-32). */}
              <Text variant="footnote" color="secondary" importantForAccessibility="no" accessibilityElementsHidden>
                {loadStatus.label}
              </Text>
            </Swap>
            <Progress label={loadStatus.label} value={loadStatus.progress} tone="accent" height={tk.space.xs} />
          </View>
        </Reveal>

        {/* TR-10: switching conversations crossfades the list (DS crossfade, keyed by the conversation). */}
        <Swap swapKey={conversationKey} style={{ flex: 1 }}>
          <View style={{ flex: 1 }}>
            <FlatList
              ref={listRef}
              style={{ flex: 1 }}
              data={items}
              keyExtractor={(m) => m.id}
              renderItem={renderItem}
              extraData={renderItem}
              contentContainerStyle={listContentStyle}
              // "interactive" is iOS only: on Android the list ignored it and a drag never closed the keyboard.
              keyboardDismissMode={Platform.OS === "ios" ? "interactive" : "on-drag"}
              keyboardShouldPersistTaps="handled"
              // With a conversation on screen, a model error comes in as the next message, above the composer: it
              // never covers an earlier answer or reads as that answer failing (Iris/Prism ER-1).
              ListFooterComponent={
                items.length > 0 && loadError ? (
                  <ChatModelError compact error={loadError} kind={loadErrorKind} onOpenSettings={openSettings} onRelaunchWizard={onRelaunchWizard} onRetry={initModels} />
                ) : null
              }
              ListEmptyComponent={
                loadError ? (
                  <ChatModelError error={loadError} kind={loadErrorKind} modelLabel={activeModel ? chatModelName(activeModel, t) : undefined} onOpenSettings={openSettings} onRelaunchWizard={onRelaunchWizard} onRetry={initModels} />
                ) : !modelsRequested ? (
                  <ChatModelLoading label={loadStatus.label} progress={loadStatus.progress} />
                ) : (
                  // Leaves with the DS exit when the first question comes in, instead of vanishing (SEND-MOTION S1).
                  // The swap half of the DS crossfade: the conversation enters right after it (F2-4b).
                  <Animated.View exiting={motion.exiting({ swap: true })} style={{ flexGrow: 1 }}>
                    <ChatEmptyState
                      suggestions={suggestions}
                      onAddKnowledge={showSuggestions ? openKnowledge : undefined}
                      onAsk={ask}
                      onFill={fillQuestion}
                    />
                  </Animated.View>
                )
              }
              onContentSizeChange={onListContentSize}
              onLayout={onListLayout}
              onScroll={onListScroll}
              onScrollBeginDrag={onListDrag}
              scrollEventThrottle={100}
            />
            {/* Text scrolling under the header fades out instead of being cut through the letters (Prism CX-3). */}
            <View
              pointerEvents="none"
              importantForAccessibility="no-hide-descendants"
              accessibilityElementsHidden
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                right: 0,
                height: tk.space.base,
                experimental_backgroundImage: veil,
              }}
            />
          </View>
        </Swap>

        {(generating ? jump.writing : jump.idle) && (
          // Above the composer as tall as it is now (it grows with lines and large text; Prism CH-28); it comes
          // and goes with the DS enter/exit (Prism CX-6).
          <Animated.View
            entering={motion.entering({ from: "below" })}
            exiting={motion.exiting({ to: "below" })}
            style={{ position: "absolute", right: tk.space.base, bottom: composerHeight + tk.space.sm }}
          >
            <IconButton
              icon="arrow-down"
              variant="tonal"
              label={t("chat.jumpToLatest")}
              onPress={() => {
                followBottom.current = true;
                setJump({ writing: false, idle: false });
                glideToBottom();
              }}
            />
          </Animated.View>
        )}

        <View onLayout={onComposerLayout}>
          <Composer
            ref={inputRef}
            value={input}
            onChange={setInput}
            onSend={send}
            onStop={stopActive}
            status={modelStatus(ready, loadError, indexing)}
            canSend={canAsk}
            generating={generating}
            stopping={stopping}
            voiceEnabled={voiceInputEnabled}
          />
        </View>
      </KeyboardAvoidingView>

      <Sheet
        visible={!!locationExplain}
        onClose={() => locationExplain?.(false)}
        title={t("chat.places.locationWhyTitle")}
        footer={
          <View style={{ gap: tk.space.sm }}>
            <Button label={t("chat.places.locationAllow")} fullWidth onPress={() => locationExplain?.(true)} />
            <Button label={t("chat.places.typeCity")} variant="secondary" fullWidth onPress={() => locationExplain?.(false)} />
          </View>
        }
      >
        <Text color="secondary">{t("chat.places.locationWhyBody")}</Text>
      </Sheet>

      <SourceSheet source={source} index={openSource?.index ?? 0} onClose={() => setOpenSource(null)} />
    </Screen>
  );
}

interface RowActions {
  openSource: (id: string, index: number) => void;
  deepen: (id: string) => void;
  askModel: (id: string) => void;
  retry: (id: string, question: string) => void;
  rate: (id: string, rating: "up" | "down") => void;
  copy: (item: Extract<ChatItem, { kind: "assistant" }>) => void;
  share: (item: Extract<ChatItem, { kind: "assistant" }>) => void;
  answerAnyway: (id: string) => void;
  city: (id: string, city: string) => void;
  useLocation?: (id: string) => void;
  getMap: () => void;
  copyReceipt: (text: string) => void;
  revealing: (id: string, on: boolean) => void;
  copyQuestion: (text: string) => void;
  editQuestion: (text: string) => void;
}

const UserRow = memo(function UserRow({ text, actions }: { text: string; actions: RowActions }) {
  const onCopy = useCallback(() => actions.copyQuestion(text), [actions, text]);
  const onEdit = useCallback(() => actions.editQuestion(text), [actions, text]);
  return <UserMessage text={text} onCopy={onCopy} onEdit={onEdit} />;
});

/** One answer row; its callbacks are stable per item so AssistantMessage's memo holds while others stream. */
const AssistantRow = memo(function AssistantRow({
  item,
  active,
  waitingLibrary,
  libraryIncomplete,
  fresh,
  stopping,
  locale,
  actions,
}: {
  item: Extract<ChatItem, { kind: "assistant" }>;
  active: boolean;
  /** The question waits for the offline library to be ready; the current status ("Indexing knowledge 15 / 300"). */
  waitingLibrary?: string;
  libraryIncomplete: boolean;
  fresh: boolean;
  stopping: boolean;
  locale: string;
  actions: RowActions;
}) {
  const itemRef = useRef(item);
  itemRef.current = item;
  const id = item.id;
  const cb = useMemo(
    () => ({
      onOpenSource: (i: number) => actions.openSource(id, i),
      onDeepen: () => actions.deepen(id),
      onAskModel: () => actions.askModel(id),
      onRetry: () => actions.retry(id, itemRef.current.question),
      onRate: (r: "up" | "down") => actions.rate(id, r),
      onCopy: () => actions.copy(itemRef.current),
      onShare: () => actions.share(itemRef.current),
      onCity: (city: string) => actions.city(id, city),
      onAnswerAnyway: () => actions.answerAnyway(id),
      onUseLocation: actions.useLocation ? () => actions.useLocation!(id) : undefined,
      onGetMap: actions.getMap,
      onCopyReceipt: actions.copyReceipt,
      onRevealing: (on: boolean) => actions.revealing(id, on),
    }),
    [actions, id]
  );
  return (
    <AssistantMessage
      answer={item.answer}
      question={item.question}
      waitingLibrary={waitingLibrary}
      libraryIncomplete={libraryIncomplete}
      active={active}
      fresh={fresh}
      stopping={stopping}
      interrupted={item.interrupted}
      feedback={item.feedback}
      locale={locale}
      {...cb}
    />
  );
});
