/**
 * Which knowledge the phone has, as catalog ids ("corpus-standard", "wiki-vital5",
 * "boar-preparedness"…), for the empty chat's suggestions (RT-1). Same rule as the
 * flows' installedPoiCities: a pack is installed when its catalog file is on disk.
 * The builtin base is implied by suggestionsFor.
 */
import * as FileSystem from "expo-file-system/legacy";
import { CORPUS_CATALOG } from "../../models/manifest";
import { topicPacks } from "../flows/adapters";

export async function installedKnowledgeIds(): Promise<string[]> {
  const entries = [...CORPUS_CATALOG, ...topicPacks().map((p) => p.entry)];
  const found = await Promise.all(
    entries.map(async (e) => {
      const info = await FileSystem.getInfoAsync(`${FileSystem.documentDirectory}${e.filename}`).catch(() => null);
      return info?.exists ? e.id : null;
    })
  );
  return found.filter((id): id is string => !!id);
}
