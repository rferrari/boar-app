import { describe, expect, it } from "vitest";
import { queueToast, toastLeft } from "./toastQueue";

describe("toast queue (TR-11)", () => {
  it("shows a toast at once when none is up", () => {
    expect(queueToast({ current: null, pending: null }, "a")).toEqual({ current: "a", pending: null });
  });
  it("a new toast waits for the current one to leave; the latest wins", () => {
    let q = queueToast({ current: "a", pending: null }, "b");
    q = queueToast(q, "c");
    expect(q).toEqual({ current: "a", pending: "c" });
    expect(toastLeft(q)).toEqual({ current: "c", pending: null });
  });
  it("leaving with nothing pending clears the slot", () => {
    expect(toastLeft({ current: "a", pending: null })).toEqual({ current: null, pending: null });
  });
});
