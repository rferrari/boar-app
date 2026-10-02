import { afterEach, describe, expect, it, vi } from "vitest";
import { getChatBridge, publishChatBridge, resetChatBridge } from "./chatBridge";

vi.mock("react", () => ({ useSyncExternalStore: vi.fn() }));

describe("chatBridge", () => {
  afterEach(() => resetChatBridge());

  it("merges published fields into the current state", () => {
    publishChatBridge({ generating: true });
    publishChatBridge({ activeSessionId: "s1" });
    expect(getChatBridge().generating).toBe(true);
    expect(getChatBridge().activeSessionId).toBe("s1");
  });

  it("keeps the same state object when nothing changed (no re-render loop)", () => {
    publishChatBridge({ generating: true });
    const before = getChatBridge();
    publishChatBridge({ generating: true });
    expect(getChatBridge()).toBe(before);
  });

  it("routes drawer actions to the handlers the chat registered", () => {
    const deleteSession = vi.fn();
    publishChatBridge({ deleteSession });
    getChatBridge().deleteSession("s2");
    expect(deleteSession).toHaveBeenCalledWith("s2");
  });
});
