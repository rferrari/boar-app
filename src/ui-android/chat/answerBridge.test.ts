import { describe, expect, it, vi } from "vitest";
import type { AnswerEvent, AnswerHandle, AnswerReceipt, AnswerResult, SourceChunk } from "../../routing/events";
import type { AnswerContext } from "../../routing/answer";
import { AnswerFn, buildChatHistory, ChatAnswerSink, queryStatsFromAnswer, runChatAnswer, taskTypeOf, VERBATIM_MESSAGE_COUNT } from "./answerBridge";

const ID = "ans-1";
const receipt = (over: Partial<AnswerReceipt> = {}): AnswerReceipt => ({
  modelId: "qwen3-4b",
  modelLabel: "Qwen3 4B",
  tokens: 3,
  tokPerSec: 12.5,
  ttftMs: 900,
  totalMs: 1500,
  loadMs: 0,
  reasonCodes: ["task:lookup"],
  ...over,
});
const src = (n: number): SourceChunk =>
  ({ chunkId: `c${n}`, docId: `d${n}`, title: `Title ${n}`, text: `text ${n}`, score: 1, similarity: 0.5 }) as unknown as SourceChunk;

type Ev = AnswerEvent extends infer E ? (E extends { answerId: string } ? Omit<E, "answerId"> : never) : never;

/** A fake answer(): emits the script in order (await between events), then resolves done with `result`. */
function fakeAnswer(script: Ev[], result: Partial<AnswerResult> = {}, opts: { onStop?: () => void } = {}) {
  const calls: { query: string; ctx: AnswerContext }[] = [];
  const stop = vi.fn(async () => opts.onStop?.());
  const fn: AnswerFn = (req, onEvent, ctx) => {
    calls.push({ query: req.query, ctx });
    const done = (async (): Promise<AnswerResult> => {
      for (const e of script) {
        await Promise.resolve();
        onEvent({ ...e, answerId: ID } as AnswerEvent);
      }
      const d = script.find((e) => e.type === "done") as Extract<AnswerEvent, { type: "done" }> | undefined;
      return {
        answerId: ID,
        tier: d?.tier ?? "fast",
        outcome: d?.outcome ?? "success",
        text: "",
        sources: [],
        receipt: d?.receipt ?? receipt(),
        ...result,
      };
    })();
    const handle: AnswerHandle = { answerId: ID, stop, done };
    return handle;
  };
  return { fn, calls, stop };
}

function recorder() {
  const log: string[] = [];
  let text = "";
  const sink: ChatAnswerSink = {
    setStatus: (s, l) => log.push(`status:${s}${l ? `:${l.kind}${l.kind === "research" ? `${l.index}/${l.count}` : ""}` : ""}`),
    setText: (t) => {
      text = t;
      log.push(`set:${t}`);
    },
    appendText: (p) => {
      text += p;
      log.push(`+${p}`);
    },
    setLiveTokPerSec: (v) => log.push(`tps:${v.toFixed(1)}`),
  };
  return { sink, log, text: () => text };
}

const ctx: AnswerContext = { systemPrompt: "SYS", styleReminder: "STYLE", history: { summary: null, turns: [] }, maxTokens: 512 };
const clock = () => {
  let t = 0;
  return () => (t += 500);
};

describe("runChatAnswer", () => {
  it("streams a fast answer: status, tokens, sources shown when cited, receipt kept", async () => {
    const sources = [src(1), src(2)];
    const { fn, calls } = fakeAnswer([
      { type: "stage", stage: "retrieving", tier: "fast", at: 0 },
      { type: "sources", tier: "fast", sources },
      { type: "stage", stage: "prefill", tier: "fast", at: 1 },
      { type: "token", tier: "fast", text: "Paris" },
      { type: "token", tier: "fast", text: " is" },
      { type: "token", tier: "fast", text: " the capital [1]." },
      { type: "done", tier: "fast", outcome: "success", receipt: receipt(), cited: [1] },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "capital of France?", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(calls).toEqual([{ query: "capital of France?", ctx }]);
    expect(r.log.filter((l) => l.startsWith("status"))).toEqual(["status:retrieving", "status:thinking", "status:generating"]);
    expect(r.text()).toBe("Paris is the capital [1].");
    expect(r.log.some((l) => l.startsWith("set:"))).toBe(false); // the streamed text stays, no replacement
    expect(r.log.filter((l) => l.startsWith("tps:")).length).toBe(2);
    expect(out.text).toBe("Paris is the capital [1].");
    expect(out.citations).toEqual(sources);
    expect(out.cited).toEqual([1]);
    expect(out.outcome).toBe("success");
    expect(out.stopped).toBe(false);
    expect(out.streamedTokens).toBe(3);
    expect(out.receipt?.modelId).toBe("qwen3-4b");
  });

  it("no source card when the final text cites nothing; finalText replaces the streamed text", async () => {
    const { fn } = fakeAnswer([
      { type: "sources", tier: "fast", sources: [src(1)] },
      { type: "token", tier: "fast", text: "Answer [7]." },
      { type: "warning", code: "weak_sources", message: "No offline source backs this answer." },
      { type: "done", tier: "fast", outcome: "success", receipt: receipt(), cited: [], finalText: "Answer." },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(r.text()).toBe("Answer.");
    expect(out.citations).toEqual([]);
    expect(out.sources.length).toBe(1);
    expect(out.warnings).toEqual(["weak_sources"]);
    expect(out.declined).toBe(false);
  });

  it("declined (weak sources, compact model): the decline message is the answer", async () => {
    const msg = "I didn't find this in this phone's library.";
    const { fn } = fakeAnswer([
      { type: "stage", stage: "retrieving", tier: "fast", at: 0 },
      { type: "warning", code: "weak_sources", declined: true, message: msg },
      { type: "done", tier: "fast", outcome: "success", receipt: receipt({ tokens: 0 }), cited: [], finalText: msg },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(out.declined).toBe(true);
    expect(out.text).toBe(msg);
    expect(r.text()).toBe(msg);
    expect(out.citations).toEqual([]);
  });

  it("an instant snippet is shown first, then replaced by the model's tokens", async () => {
    const { fn } = fakeAnswer([
      { type: "sources", tier: "instant", sources: [src(1)] },
      { type: "instant", snippet: { text: "Snippet.", sourceIndex: 0 }, confidence: 0.5 },
      { type: "token", tier: "fast", text: "Model" },
      { type: "token", tier: "fast", text: " text [1]." },
      { type: "done", tier: "fast", outcome: "success", receipt: receipt(), cited: [1] },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(r.log.slice(0, 4)).toEqual(["set:Snippet.", "status:generating", "set:", "status:generating"]);
    expect(r.text()).toBe("Model text [1].");
    expect(out.text).toBe("Model text [1].");
  });

  it("an instant-final answer keeps the snippet as the text", async () => {
    const { fn } = fakeAnswer(
      [
        { type: "sources", tier: "instant", sources: [src(1)] },
        { type: "instant", snippet: { text: "Snippet.", sourceIndex: 0 }, confidence: 0.9 },
        { type: "done", tier: "instant", outcome: "success", receipt: receipt({ modelId: "extractive", tokens: 0 }), cited: [1] },
      ],
      { text: "Snippet." }
    );
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(out.text).toBe("Snippet.");
    expect(out.tier).toBe("instant");
    expect(out.citations.length).toBe(1);
    expect(r.log.filter((l) => l.startsWith("set:"))).toEqual(["set:Snippet."]);
  });

  it("an error with no text shows 'Error: <message>' like the legacy path", async () => {
    const { fn } = fakeAnswer([
      { type: "stage", stage: "loading_model", tier: "fast", at: 0 },
      { type: "done", tier: "fast", outcome: "error", receipt: receipt({ tokens: 0 }), error: { code: "no_model", message: "No language model is installed." } },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(r.log[0]).toBe("status:thinking:loading_model");
    expect(out.outcome).toBe("error");
    expect(out.text).toBe("Error: No language model is installed.");
    expect(r.text()).toBe(out.text);
    expect(out.error?.code).toBe("no_model");
  });

  it("stop: a stop requested before the handle exists is applied at once; outcome stopped keeps the partial text", async () => {
    const { fn, stop } = fakeAnswer([
      { type: "token", tier: "fast", text: "Partial" },
      { type: "done", tier: "fast", outcome: "stopped", receipt: receipt({ tokens: 1 }), cited: [] },
    ]);
    const r = recorder();
    let handleSeen: AnswerHandle | null = null;
    const out = await runChatAnswer({
      answer: fn,
      query: "q",
      ctx,
      sink: r.sink,
      now: clock(),
      isStopRequested: () => true,
      onHandle: (h) => (handleSeen = h),
    });
    expect(handleSeen).not.toBeNull();
    expect(stop).toHaveBeenCalledTimes(1);
    expect(out.stopped).toBe(true);
    expect(out.text).toBe("Partial");
    expect(r.text()).toBe("Partial");
  });

  it("stop through the handle while streaming (the chat's Stop button)", async () => {
    let stopped = false;
    const { fn, stop } = fakeAnswer(
      [
        { type: "token", tier: "fast", text: "A" },
        { type: "done", tier: "fast", outcome: "stopped", receipt: receipt(), cited: [] },
      ],
      {},
      { onStop: () => void (stopped = true) }
    );
    const r = recorder();
    let handle: AnswerHandle | null = null;
    // The Stop button: pressed after the first streamed token (the chat calls handle.stop()).
    const sink: ChatAnswerSink = {
      ...r.sink,
      appendText: (p) => {
        r.sink.appendText(p);
        void handle?.stop();
      },
    };
    const out = await runChatAnswer({
      answer: fn,
      query: "q",
      ctx,
      sink,
      now: clock(),
      isStopRequested: () => stopped,
      onHandle: (h) => void (handle = h),
    });
    expect(stop).toHaveBeenCalledTimes(1);
    expect(r.text()).toBe("A");
    expect(out.stopped).toBe(true);
  });

  it("timeout, multi-pass stage labels, safety and deep_available are reported", async () => {
    const { fn } = fakeAnswer([
      { type: "stage", stage: "retrieving", tier: "deep", at: 0, detail: { index: 1, count: 3 } },
      { type: "stage", stage: "synthesizing", tier: "deep", at: 1 },
      { type: "token", tier: "deep", text: "x" },
      { type: "done", tier: "deep", outcome: "timeout", receipt: receipt(), cited: [], safety: true },
      { type: "deep_available", reason: "multipass" },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(r.log.slice(0, 2)).toEqual(["status:retrieving:research2/3", "status:thinking:synthesizing"]);
    expect(out.timedOut).toBe(true);
    expect(out.safety).toBe(true);
    expect(out.deepAvailable).toBe(true);
  });

  it("places (registered providers): the engine's places text is kept as is, with its records as sources", async () => {
    const place = { id: "osm:node/1", name: "Green Cafe", lat: 0, lon: 0, address: "Main St 1", cuisine: ["vegan"], source: "osm" as const, sourceIndex: 0 };
    const placeSources = [src(1)];
    const engineText = "Vegan places in Lisbon:\n1. Green Cafe — Main St 1 [1]";
    const { fn } = fakeAnswer(
      [
        { type: "stage", stage: "retrieving", tier: "instant", at: 0 },
        { type: "places", tier: "instant", places: [place], area: { kind: "city", place: { name: "Lisbon" } }, filters: ["vegan"], criterion: "diet_match", coverage: "ok", attribution: [{ source: "osm", license: "ODbL" }] },
        { type: "done", tier: "instant", outcome: "success", receipt: receipt({ modelId: "places", modelLabel: "Offline places", tokens: 0, reasonCodes: ["task:places", "near:city", "diet:vegan", "places:1"] }) },
      ],
      { text: engineText, sources: placeSources }
    );
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "vegan food in Lisbon", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(r.log[0]).toBe("status:retrieving");
    expect(out.text).toBe(engineText); // not listed twice
    expect(r.text()).toBe(engineText);
    expect(out.citations).toEqual(placeSources);
    expect(out.tier).toBe("instant");
  });

  it("places with no engine text: the places are listed", async () => {
    const place = { id: "osm:node/1", name: "Green Cafe", lat: 0, lon: 0, address: "Main St 1", cuisine: ["vegan"], source: "osm" as const };
    const { fn } = fakeAnswer([
      { type: "places", tier: "instant", places: [place], area: { kind: "city" }, criterion: "diet_match", coverage: "ok", attribution: [] },
      { type: "done", tier: "instant", outcome: "success", receipt: receipt({ modelId: "places", tokens: 0 }) },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "vegan food in Rome", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(out.text).toBe("- Green Cafe (Main St 1) · vegan");
    expect(r.text()).toBe(out.text);
  });

  it("an error after some streamed tokens is marked, not shown as a finished answer", async () => {
    const { fn } = fakeAnswer([
      { type: "sources", tier: "fast", sources: [src(1)] },
      { type: "token", tier: "fast", text: "Partial answer" },
      { type: "token", tier: "fast", text: " so far" },
      { type: "done", tier: "fast", outcome: "error", receipt: receipt(), cited: [], error: { code: "generation_failed", message: "context overflow" } },
    ]);
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(out.outcome).toBe("error");
    expect(out.text).toBe("Partial answer so far\n\nError: context overflow");
    expect(r.text()).toBe(out.text);
    expect(out.stopped).toBe(false);
    expect(out.citations).toEqual([]);
  });

  it("passes the rest of the request through (answerAnyway, deeper answer, place)", async () => {
    const { fn, calls } = fakeAnswer([{ type: "done", tier: "deep", outcome: "success", receipt: receipt(), cited: [] }]);
    const reqs: unknown[] = [];
    const spy: AnswerFn = (req, onEvent, c) => {
      reqs.push(req);
      return fn(req, onEvent, c);
    };
    const reuse = [src(1)];
    await runChatAnswer({ answer: spy, query: "q", request: { answerAnyway: true }, ctx, sink: recorder().sink, now: clock(), isStopRequested: () => false });
    await runChatAnswer({ answer: spy, query: "q", request: { tier: "deep", reuseSources: reuse }, ctx, sink: recorder().sink, now: clock(), isStopRequested: () => false });
    await runChatAnswer({ answer: spy, query: "q", request: { place: "Lisbon" }, ctx, sink: recorder().sink, now: clock(), isStopRequested: () => false });
    await runChatAnswer({ answer: spy, query: "q", ctx, sink: recorder().sink, now: clock(), isStopRequested: () => false });
    expect(reqs).toEqual([
      { answerAnyway: true, query: "q" },
      { tier: "deep", reuseSources: reuse, query: "q" },
      { place: "Lisbon", query: "q" },
      { query: "q" },
    ]);
    expect(calls.length).toBe(4);
  });

  it("drops events of another answer id and events after done", async () => {
    const onEventRef: { cb?: (e: AnswerEvent) => void } = {};
    const fn: AnswerFn = (_req, onEvent) => {
      onEventRef.cb = onEvent;
      const done = (async (): Promise<AnswerResult> => {
        onEvent({ type: "token", answerId: ID, tier: "fast", text: "ok" });
        onEvent({ type: "token", answerId: "other", tier: "fast", text: "LATE" });
        onEvent({ type: "done", answerId: ID, tier: "fast", outcome: "success", receipt: receipt(), cited: [] });
        onEvent({ type: "token", answerId: ID, tier: "fast", text: "AFTER" });
        return { answerId: ID, tier: "fast", outcome: "success", text: "ok", sources: [], receipt: receipt() };
      })();
      return { answerId: ID, stop: async () => {}, done };
    };
    const r = recorder();
    const out = await runChatAnswer({ answer: fn, query: "q", ctx, sink: r.sink, now: clock(), isStopRequested: () => false });
    expect(out.text).toBe("ok");
    expect(r.text()).toBe("ok");
  });
});

describe("buildChatHistory", () => {
  it("keeps the summary and the last VERBATIM_MESSAGE_COUNT non-empty messages, reasoning stripped", () => {
    const prior = [
      { role: "user" as const, text: "q0" },
      { role: "assistant" as const, text: "a0" },
      { role: "user" as const, text: "q1" },
      { role: "assistant" as const, text: "" },
      { role: "user" as const, text: "q2" },
      { role: "assistant" as const, text: "<think>hmm</think>a2" },
      { role: "user" as const, text: "q3" },
      { role: "assistant" as const, text: "a3" },
    ];
    const { history, priorMessages } = buildChatHistory(prior, "older summary");
    expect(VERBATIM_MESSAGE_COUNT).toBe(6);
    expect(priorMessages.length).toBe(7);
    expect(history.summary).toBe("older summary");
    expect(history.turns).toEqual([
      { role: "assistant", text: "a0" },
      { role: "user", text: "q1" },
      { role: "user", text: "q2" },
      { role: "assistant", text: "a2" },
      { role: "user", text: "q3" },
      { role: "assistant", text: "a3" },
    ]);
  });
});

describe("queryStatsFromAnswer", () => {
  it("maps the receipt to the usage-stats fields", () => {
    const s = queryStatsFromAnswer(
      {
        text: "t",
        citations: [],
        sources: [src(1)],
        cited: [],
        outcome: "stopped",
        tier: "fast",
        stopped: true,
        timedOut: false,
        receipt: receipt({ loadMs: 1200 }),
        warnings: [],
        declined: false,
        safety: false,
        deepAvailable: false,
        streamedTokens: 3,
      },
      { durationMs: 2000, peakRssBytes: 5, timestamp: 7, fallbackModelId: "loaded" }
    );
    expect(s).toMatchObject({
      tokensGenerated: 3,
      durationMs: 2000,
      ttftMs: 900,
      tokPerSec: 12.5,
      totalLatencyMs: 1500,
      modelId: "qwen3-4b",
      modelLoadMs: 1200,
      retrievalUsed: true,
      taskType: "lookup",
      outcome: "cancelled",
      reasonCodes: ["task:lookup"],
    });
    expect(s.adaptiveRoutingUsed).toBeUndefined();
  });

  it("falls back to the loaded model id when the receipt names none", () => {
    const s = queryStatsFromAnswer(
      {
        text: "",
        citations: [],
        sources: [],
        cited: [],
        outcome: "error",
        tier: "fast",
        stopped: false,
        timedOut: false,
        receipt: receipt({ modelId: "none", reasonCodes: ["error:unexpected"] }),
        warnings: [],
        declined: false,
        safety: false,
        deepAvailable: false,
        streamedTokens: 0,
      },
      { durationMs: 1, peakRssBytes: 0, timestamp: 0, fallbackModelId: "loaded" }
    );
    expect(s.modelId).toBe("loaded");
    expect(s.taskType).toBeUndefined();
    expect(s.outcome).toBe("failure");
  });
});

describe("taskTypeOf", () => {
  it("reads the engine's task:<type> code; places and unknown codes give none", () => {
    expect(taskTypeOf(["task:research", "gate:x"])).toBe("research");
    expect(taskTypeOf(["task:places", "near:city"])).toBeUndefined();
    expect(taskTypeOf(["error:unexpected"])).toBeUndefined();
    expect(taskTypeOf(undefined)).toBeUndefined();
  });
});
