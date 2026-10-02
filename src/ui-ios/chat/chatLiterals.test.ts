import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, it, expect } from "vitest";

const read = (rel: string) => readFileSync(join(__dirname, rel), "utf8");
const chat = ["AssistantMessage.tsx", "PlacesCard.tsx", "ChatPieces.tsx", "../ChatScreen.tsx"].map(read).join("\n");

describe("chat visual minors (Prism CH-22..CH-28)", () => {
  it("CH-28: no literal sizes next to tokens", () => {
    expect(chat).not.toMatch(/bottom: 96\b/);
    expect(chat).not.toMatch(/borderLeftWidth: 2\b/);
    expect(chat).not.toMatch(/size\.touch \+ 8\b/);
    expect(chat).not.toMatch(/fontScale >= \d/);
  });
  it("CH-22/CH-23/CH-24: ember and solid amber only where they mean something", () => {
    const am = read("AssistantMessage.tsx");
    expect(am).not.toMatch(/<Text variant="label" color="accent" header>\s*\{tr\("chat\.deep\.title"\)\}/);
    expect(am).not.toMatch(/tone="field" emphasis="solid"/);
    expect(am).not.toMatch(/band === "low" \? "secondary" : "field"/);
  });
  it("CH-28/CH-29: the composer uses the opacity tokens and the DS Stop", () => {
    const composer = read("Composer.tsx");
    expect(composer).not.toMatch(/opacity: [^,}]*\b0\.\d/);
    expect(composer).toMatch(/variant="stop"/);
    expect(composer).not.toMatch(/<Pressable\b/);
  });
  it("Stop gives one haptic, the IconButton's (not a second one from stopActive)", () => {
    const stop = read("../ChatScreen.tsx").match(/const stopActive = useCallback\([\s\S]*?\n  \}, \[\]\);/)![0];
    expect(stop).not.toMatch(/impact\(/);
  });
  it("CH-27: the finished receipt sits at the row's end, as the running pill", () => {
    // The pill and the receipt share one Swap at the row's end (SEND-MOTION crossfade).
    const src = read("AssistantMessage.tsx");
    expect(src).toMatch(/<Swap swapKey=\{pill\} style=\{\{ marginLeft: "auto", flexShrink: 1, minWidth: 0 \}\}>/);
    expect(src).toMatch(/pill === "elapsed" \? \([\s\S]*?<Elapsed[\s\S]*?<ReceiptToggle r=\{receipt\} hidden=\{active\} \/>/);
  });
  it("CX-8: 'May be wrong.' comes before 'Answer anyway', seen and heard once", () => {
    const decline = read("AssistantMessage.tsx").match(/function DeclinedNoSource[\s\S]*?\n}\n/)![0];
    const warning = decline.indexOf('tr("chat.weak.answerAnywayHint")');
    const button = decline.indexOf('<Button label={tr("chat.weak.answerAnyway")}');
    expect(warning).toBeGreaterThan(-1);
    expect(button).toBeGreaterThan(warning);
    // Read by screen readers (not hidden) and not repeated as the button's hint.
    expect(decline).not.toMatch(/accessibilityElementsHidden>\s*\{tr\("chat\.weak\.answerAnywayHint"\)/);
    expect(decline.match(/answerAnywayHint/g)).toHaveLength(1);
  });
  it("CX-7: the no-strong-source marker is a status (no card, no caps), read as text", () => {
    const note = read("AssistantMessage.tsx").match(/function WeakSourceNote[\s\S]*?\n}\n/)![0];
    expect(note).not.toMatch(/<Card\b/);
    expect(note).not.toMatch(/variant="(label|badge)"[^>]*>\s*\{title\}/);
    expect(note).toMatch(/accessibilityRole="text"/);
  });
  it("CX-10: Deepen is the actions row's pill when offered; copying joins the icons then", () => {
    const src = read("AssistantMessage.tsx");
    expect(src).toMatch(/\{deepenNow \? \(\s*<ActionPill\s+icon="layers"/);
    expect(src).toMatch(/\{deepenNow && \(\s*<IconButton icon="copy"/);
    expect(src).toMatch(/:\s*\(\s*<ActionPill icon="copy"/);
  });
  it("CX-11: the long receipt truncates; the name beside it never shrinks", () => {
    const src = read("AssistantMessage.tsx");
    expect(src).toMatch(/<Text variant="headline" color="accent" style=\{\{ flexShrink: 0 \}\}>\s*\{tr\("chat\.assistantName"\)\}/);
    expect(src).toMatch(/<Swap swapKey=\{pill\} style=\{\{ marginLeft: "auto", flexShrink: 1, minWidth: 0 \}\}>/);
    expect(src).toMatch(/<MetaLine items=\{r\.short\} variant="caption" numberOfLines=\{1\} \/>/);
  });
  it("CX-12: send/stop and the receipt wait until the answer's text has finished showing", () => {
    const screen = read("../ChatScreen.tsx");
    expect(screen).toMatch(/const generating = active !== null \|\| revealing\.size > 0;/);
    const msg = read("AssistantMessage.tsx");
    expect(msg).toMatch(/const active = answerStillShowing\(!!running, draining\);/);
    // The model's own state still drives the streaming and the steps.
    expect(msg).toMatch(/const steps = running && !stopping/);
    expect(msg).toMatch(/const fastStreaming = running && /);
    for (const key of ["fast", "deep", "extract"]) expect(msg).toContain(`drainKey="${key}"`);
  });
  it("F2-9: the end notes fade in whole (no height reveal), with no layout animation on their commit", () => {
    const msg = read("AssistantMessage.tsx");
    expect(msg).toMatch(/<Block shown=\{!!answer\.weakDeclined && !active\} fade>/);
    expect(msg).toMatch(/<Block shown=\{!!note && !!done\} fade>/);
    const finish = read("../ChatScreen.tsx").match(/const finish = useCallback\(\(\) => \{[\s\S]*?\n  \}, \[\]\);/)![0];
    expect(finish).not.toMatch(/animateNextLayout\(\)/);
  });
  it("iPhone v9: the empty state is scrolled by the scroll view itself (FlatList.scrollToEnd needs items)", () => {
    const screen = read("../ChatScreen.tsx");
    expect(screen).toMatch(/if \(itemsRef\.current\.length > 0\) list\.scrollToEnd\(\{ animated \}\);\s*else \(list\.getNativeScrollRef\(\)/);
    expect(screen).toMatch(/sameFrame \? scrollListToEnd\(animated\) : requestAnimationFrame\(\(\) => scrollListToEnd\(animated\)\)/);
  });
  it("iPhone v9 F2-2: a follow-up makes a restored answer live; its blocks don't regrow what is on screen", () => {
    const followUp = read("../ChatScreen.tsx").match(/const followUp = useCallback\([\s\S]*?setActive\(activeRef\.current\);/)![0];
    expect(followUp).toMatch(/askedIds\.current\.add\(messageId\);/);
    const msg = read("AssistantMessage.tsx");
    expect(msg).toMatch(/<Reveal shown=\{shown\} appear=\{false\} spaceBefore=\{gap \?\? m\.gap\}>/);
    expect(msg).toMatch(/<LayoutAnimationConfig skipEntering>\s*<View style=\{\{ alignSelf: "stretch" \}\}>/);
  });
  it("F2-2 (probe cddf2a8): every Reanimated timing in the chat opts out of the OS skip; the DS reduces motion itself", () => {
    // Reanimated's default ReduceMotion.System ends an animation at once when the OS setting is on: every
    // Reveal move ended within a frame on the iPhone, the DS's 90 ms fade included.
    for (const file of ["Reveal.tsx", "Composer.tsx"]) {
      const src = read(file);
      const timings = src.match(/with(Timing|Spring|Delay|Sequence|Repeat|Decay)\(/g) ?? [];
      expect(timings.length).toBeGreaterThan(0);
      expect(src.match(/reduceMotion: ReduceMotion\.Never/g)?.length).toBe(timings.length);
    }
  });
  it("F2-2 plan B: a leaving block only fades (no bound moves), then its space closes", () => {
    const hide = read("Reveal.tsx").match(/\/\/ Leaving \(hideSteps\)[\s\S]*?arm\(steps\.deadlineMs, id\);/)![0];
    expect(hide).toMatch(/opacity\.value = withTiming\(0,/);
    expect(hide).not.toMatch(/maxHeight\.value|minHeight\.value/);
  });
  it("probe cddf2a8: a decline is remembered across the warning and done flushes (no layout animation there)", () => {
    const screen = read("../ChatScreen.tsx");
    expect(screen).toMatch(/event\.type === "warning" && event\.code === "weak_sources" && event\.declined\) declinedIds\.current\.add\(messageId\)/);
    expect(screen).toMatch(/const declined = batch\.some\(\(\{ messageId \}\) => declinedIds\.current\.has\(messageId\)\);/);
  });
});
