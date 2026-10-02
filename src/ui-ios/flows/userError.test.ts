import { describe, expect, it } from "vitest";
import { AssetIntegrityError } from "../../models/integrity";
import { rawErrorText, userErrorKey } from "./userError";
import en from "../locales/en.json";

describe("userErrorKey (Prism FL-11: no raw exception text on screen)", () => {
  it("names the cause of common failures", () => {
    expect(userErrorKey(new Error("ENOSPC: no space left on device"))).toBe("flows.row.error.storage");
    expect(userErrorKey(new Error("Network request failed"))).toBe("flows.row.error.network");
    expect(userErrorKey("The request timed out.")).toBe("flows.row.error.network");
    expect(userErrorKey(new Error("EACCES: permission denied"))).toBe("flows.row.error.unreadable-file");
    // Prism NA-3: the Knowledge import print showed this in English under a PT screen.
    expect(userErrorKey(new Error("Couldn't read the PDF: Unexpected End-of-File"))).toBe("flows.row.error.corrupt");
    expect(userErrorKey("Invalid PDF structure")).toBe("flows.row.error.corrupt");
  });

  it("keeps an integrity error's own kind, and falls back to 'Something went wrong'", () => {
    expect(userErrorKey(new AssetIntegrityError("hash-mismatch", "x", false))).toBe("flows.row.error.hash-mismatch");
    expect(userErrorKey(new Error("TypeError: undefined is not a function"))).toBe("flows.row.error.unknown");
    expect(userErrorKey(undefined)).toBe("flows.row.error.unknown");
  });

  it("every key it returns has a sentence", () => {
    const row = (en as any).flows.row.error;
    for (const e of [new Error("ENOSPC"), new Error("offline"), new Error("EPERM"), new Error("?")]) {
      expect(row[userErrorKey(e).split(".").pop()!]).toBeTruthy();
    }
  });

  it("rawErrorText keeps the detail for the log line", () => {
    expect(rawErrorText(new Error("boom"))).toBe("boom");
    expect(rawErrorText("plain")).toBe("plain");
  });
});
