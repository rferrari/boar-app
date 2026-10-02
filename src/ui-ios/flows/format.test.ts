import { describe, expect, it } from "vitest";
import { formatBytes, formatBytesParts, formatRam, formatSeconds, failureLines, minutesAbout, minutesLeft, readableErrorDetail, toWords } from "./format";

describe("formatBytes", () => {
  it("uses the locale's decimal separator", () => {
    expect(formatBytes(1_250_000_000, "en")).toBe("1.3 GB");
    expect(formatBytes(1_250_000_000, "pt")).toBe("1,3 GB");
  });

  it("uses decimal units, like the system file picker and Settings (Prism N-14)", () => {
    // boar-crypto.sqlite: 35,962,880 bytes, shown as 35.96 MB by Android.
    expect(formatBytes(35_962_880, "en")).toBe("36 MB");
    expect(formatBytes(19_554_304, "en")).toBe("19.6 MB");
  });

  it("picks the unit by size", () => {
    expect(formatBytes(986_000_000, "en")).toBe("986 MB");
    // Never four digits of the smaller unit (iOS shot: "1,000 MB on disk").
    expect(formatBytes(999_600_000, "en")).toBe("1 GB");
    expect(formatBytes(999_000_000, "en")).toBe("999 MB");
    expect(formatBytes(999_700, "en")).toBe("1 MB");
    expect(formatBytesParts(1_250_000_000, "pt")).toEqual({ value: "1,3", unit: "GB" });
    expect(formatBytes(2_500_000, "en")).toBe("2.5 MB");
    expect(formatBytes(600_000, "en")).toBe("600 kB");
  });
});

describe("formatRam", () => {
  it("uses binary units, as phones are sold: 16 GiB reads 16 GB", () => {
    expect(formatRam(16 * 1024 ** 3, "en")).toBe("16 GB");
    expect(formatRam(3.8 * 1024 ** 3, "pt")).toBe("3,8 GB");
    expect(formatRam(512 * 1024 ** 2, "en")).toBe("512 MB");
  });
});

describe("formatSeconds", () => {
  it("keeps a decimal under ten seconds", () => {
    expect(formatSeconds(1800, "pt")).toBe("1,8 s");
    expect(formatSeconds(12_400, "en")).toBe("12 s");
  });
});

describe("minutesLeft", () => {
  it("rounds up and never says zero", () => {
    expect(minutesLeft(10)).toBe(1);
    expect(minutesLeft(61)).toBe(2);
  });
});

describe("minutesAbout", () => {
  it("rounds an estimate to the nearest minute, so 1.0 and 1.2 GB at 5 MB/s differ (Prism)", () => {
    expect(minutesAbout(200)).toBe(3); // 1.0 GB
    expect(minutesAbout(240)).toBe(4); // 1.2 GB
    expect(minutesAbout(10)).toBe(1);
  });
});

describe("readableErrorDetail (Prism MD-1)", () => {
  it("formats byte counts and drops the exception text", () => {
    expect(
      readableErrorDetail(
        "Download of Qwen3-4B was interrupted at 782790059 of 2497281120 bytes (Software caused connection abort). Retry to continue from there.",
        "en"
      )
    ).toBe("Download of Qwen3-4B was interrupted at 783 MB of 2.5 GB. Retry to continue from there.");
  });

  it("formats a lone byte count and an expected size", () => {
    expect(readableErrorDetail("failed verification — got 1200000 bytes, expected 2497281120.", "en")).toBe(
      "failed verification — got 1.2 MB, expected 2.5 GB."
    );
  });

  it("leaves a message without sizes as it is", () => {
    expect(readableErrorDetail("The file is not in the catalog.", "en")).toBe("The file is not in the catalog.");
  });
});

describe("failureLines (download errors translated from Ledger's codes, MD-4)", () => {
  const t = (key: string, o?: Record<string, unknown>) => `${key}${o ? " " + JSON.stringify(o) : ""}`;

  it("interrupted: the cause, then where it stopped, sizes formatted", () => {
    const r = failureLines(
      { errorKind: "network", message: "Download of X was interrupted …", detail: { code: "interrupted", bytesDone: 782_790_059, bytesTotal: 2_497_281_120 } },
      t,
      "pt"
    );
    expect(r.cause).toBe("flows.row.error.network");
    expect(r.detail).toBe('flows.row.errorDetail.interrupted {"done":"783 MB","total":"2,5 GB","seconds":0}');
  });

  it("paused is a stall, not a lost connection", () => {
    const r = failureLines({ errorKind: "network", message: "", detail: { code: "paused", bytesDone: 1, bytesTotal: 2, stallS: 60 } }, t, "en");
    expect(r.cause).toBe("flows.row.error.stalled");
    expect(r.detail).toContain('"seconds":60');
  });

  it("without a code, the readable English detail", () => {
    const r = failureLines({ errorKind: "storage", message: "needs 3000000000 bytes (ENOSPC)" }, t, "en");
    expect(r).toEqual({ cause: "flows.row.error.storage", detail: "needs 3 GB" });
  });
});

describe("toWords", () => {
  it("shows a token count as about three quarters as many words", () => {
    expect(toWords(1024)).toBe(768);
    expect(Math.round(toWords(5))).toBe(4);
  });
});
