import type { CatalogModel } from "../../models/manifest";
import { PREPAREDNESS_PACK, PREPAREDNESS_SOURCES, preparednessEntry as bramblePreparednessEntry } from "../../rag/preparedness";
import { CRYPTO_PACK, CRYPTO_SOURCES, cryptoEntry } from "../../rag/cryptoPack";
import { WIKIVOYAGE_EN, wikivoyageEnEntry } from "../../rag/wikiEnPacks";

export interface PackSource {
  name: string;
  license: string;
  url?: string;
}

/** A topic pack (Bramble's src/rag/*Pack.ts): its catalog entry, localized name, size in documents and sources to attribute. */
export interface TopicPack {
  entry: CatalogModel;
  name: { en: string; pt: string };
  docCount: number;
  sources: PackSource[];
}

/** Every topic pack in the app, for the Knowledge list and the About attributions. A new pack is one line here. */
export function topicPacks(): TopicPack[] {
  return [
    {
      entry: bramblePreparednessEntry(),
      name: PREPAREDNESS_PACK.name,
      docCount: PREPAREDNESS_PACK.docCount,
      sources: PREPAREDNESS_SOURCES.map(({ name, license, url }) => ({ name, license, url })),
    },
    {
      entry: cryptoEntry(),
      name: CRYPTO_PACK.name,
      docCount: CRYPTO_PACK.docCount,
      sources: CRYPTO_SOURCES.map(({ name, license, url }) => ({ name, license, url })),
    },
    {
      entry: wikivoyageEnEntry(),
      name: { en: "Travel guides (Wikivoyage)", pt: "Guias de viagem (Wikivoyage)" },
      docCount: WIKIVOYAGE_EN.guides,
      sources: [{ name: "Wikivoyage", license: "CC BY-SA 4.0", url: "https://en.wikivoyage.org" }],
    },
  ];
}

/** The pack's name in the UI language (English for anything but Portuguese). */
export function packName(pack: TopicPack, lang: string): string {
  return lang.startsWith("pt") ? pack.name.pt : pack.name.en;
}
