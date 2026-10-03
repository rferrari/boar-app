import { describe, expect, it } from "vitest";
import { checkScreen } from "./screen-check.mjs";

describe("CIT-2 on the chat screen", () => {
  it("fails a passage ending with [n] above its [n] button, and a doubled [n] in the text", () => {
    expect(checkScreen({ screen: { snippet: { text: "…Intertropical Convergence Zone. [1]", button: "[1]" } } }).pass).toBe(false);
    expect(checkScreen({ screen: { fast: "Greenhouse gases trap heat [1]. [1]" } }).where).toBe("fast");
  });
  it("passes the 394bf31 screens and rows without a screen are not checked", () => {
    expect(checkScreen({ screen: { snippet: { text: "A monsoon is … the equator.", button: "[1]" } } }).pass).toBe(true);
    expect(checkScreen({ screen: { fast: "…from the Earth's surface [1]. These gases allow sunlight … [1]." } }).pass).toBe(true);
    expect(checkScreen({ answer: "x" })).toBeNull();
  });
});
