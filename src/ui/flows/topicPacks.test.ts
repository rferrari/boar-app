import { describe, expect, it } from "vitest";
import { packName, topicPacks } from "./topicPacks";

describe("topicPacks", () => {
  it("offers the English Wikivoyage travel guides, attributed to Wikivoyage", () => {
    const pack = topicPacks().find((p) => p.entry.id === "boar-wikivoyage-en");
    expect(pack).toBeDefined();
    expect(pack!.entry.filename).toBe("corpus/boar-wikivoyage-en.sqlite");
    expect(pack!.entry.format).toBe("sqlite-pack");
    expect(pack!.docCount).toBe(34002);
    expect(packName(pack!, "pt-BR")).toBe("Guias de viagem (Wikivoyage)");
    expect(pack!.sources).toEqual([{ name: "Wikivoyage", license: "CC BY-SA 4.0", url: "https://en.wikivoyage.org" }]);
  });

  it("lists each pack once", () => {
    const ids = topicPacks().map((p) => p.entry.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});
