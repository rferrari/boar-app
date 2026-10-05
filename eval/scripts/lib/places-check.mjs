// Gate item "places" (Boar PL-1, 2026-09-27): with the Berlin places pack from the catalog installed, Berlin by name and
// "near me" from a point in Berlin must list real vegan venues (matched against the OpenStreetMap snapshot in the v2
// gold) with an OpenStreetMap source; Tokyo, with no pack, must say there is no offline data and list no venues.
// Tile cases (Boar v1.1): Rome from the 1x1 tile t-N41E012 alone lists real vegan venues with an OpenStreetMap source
// (places-004); Velletri, covered by that tile but with no kosher venue in OSM within 25 km (gold/places/places-005),
// must say the map has no match, never "no data" (Tusk T2-6), and list nothing (places-005). "Kosher ramen in Rome"
// (kosher venues exist, none serves ramen) must be a no-match naming both conditions (places-006). Qujing, whose sparse
// tile t-N25E103 has no vegan place, may say no data or no match but must list nothing (places-007). Cape Town from the
// southern-hemisphere tile t-S34E018 alone lists real vegan venues with an OpenStreetMap source (places-008, WORLD_PLACES final).
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { scoreFoodAnswer } from "./venues.mjs";

const DATASET_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "dataset");
const GOLD = {
  "places-001": "gold/v2/food-001.json", "places-002": "gold/v2/food-001.json", "places-003": "gold/v2/food-006.json",
  "places-004": "gold/places/places-004.json", "places-005": "gold/places/places-005.json",
  "places-006": "gold/places/places-006.json", "places-007": "gold/places/places-007.json",
  "places-008": "gold/places/places-008.json",
};
const HAS_DATA = new Set(["places-001", "places-002", "places-004", "places-008"]);
const NO_MATCH_IDS = new Set(["places-005", "places-006"]);
// Both filters named in the no-match (Tusk next16): the diet and the dish.
const BOTH_TERMS = { "places-006": [/\bkosher\b/i, /\bramen\b/i] };
const SPARSE_IDS = new Set(["places-007"]);
// noMatchAnswer (src/routing/geo.ts): "The offline map for X has no place tagged kosher within 25 km".
const NO_MATCH = /offline map .{0,40}has no place|has no place (tagged|matching)|mapa offline .{0,40}n[ãa]o tem nenhum lugar/i;
const OSM = /openstreetmap|\bosm\b|© ?openstreetmap/i;
const NO_DATA = /no (offline )?(places|place) (data|pack)|not installed|no data (for|about|on)|don't have (offline )?(places?|data)( data)?|isn't (covered|available)|not (covered|available) offline|n[ãa]o (h[áa]|tenho) dados|sem dados|pacote de lugares/i;
const listLines = (t) => (t.match(/^\s*(?:[-*•]|\d+[.)])\s+\S/gm) ?? []).length;

/** @returns {{ pass: boolean, failures: string[], warnings: string[] } | null} */
export function checkPlaces(row) {
  const id = row.queryId?.replace(/-pt$/, "");
  if (!GOLD[id]) return null;
  const gold = JSON.parse(readFileSync(join(DATASET_DIR, GOLD[id]), "utf8"));
  const answer = row.answer ?? "";
  const score = scoreFoodAnswer(answer, gold);
  const failures = [], warnings = [];
  const sources = `${answer}\n${(row.retrievedTitles ?? []).join("\n")}`;
  if (HAS_DATA.has(id)) {
    if (!score.pass) failures.push(`fewer than 3 real vegan venues from OpenStreetMap (${score.verifiedDietVenues} matched${score.matched.length ? `: ${score.matched.slice(0, 5).join(", ")}` : ""})`);
    if (!OSM.test(sources)) failures.push("no OpenStreetMap source shown");
  } else if (SPARSE_IDS.has(id)) {
    if (!NO_DATA.test(answer) && !NO_MATCH.test(answer)) failures.push("does not say there is no matching place in the offline data");
    if (listLines(answer) >= 1) failures.push(`lists venues although the offline data has none (${listLines(answer)} list lines)`);
    if (gold.osm.venues.length) warnings.push(`OSM now has ${gold.osm.venues.length} ${gold.diet} venues near ${gold.city} (tile older than OSM?)`);
  } else if (NO_MATCH_IDS.has(id)) {
    const terms = BOTH_TERMS[id];
    if (terms) {
      const dish = /ramen/i;
      if (!gold.osm.venues.length || gold.osm.venues.some((v) => dish.test(`${v.name} ${v.cuisine ?? ""}`))) failures.push("gold no longer fits (needs diet venues and none with the dish): refetch");
      const missing = terms.filter((t) => !t.test(answer));
      if (missing.length) failures.push(`no-match does not name every filter (missing ${missing.map(String).join(", ")})`);
    } else if (gold.osm.venues.length) failures.push(`gold has ${gold.osm.venues.length} ${gold.diet} venues: the case is no longer a no-match (refetch the gold)`);
    if (!NO_MATCH.test(answer)) failures.push("does not say the offline map has no matching place");
    if (NO_DATA.test(answer)) failures.push("says there is no data although a map covers the area (no_match expected)");
    if (listLines(answer) >= 3) failures.push(`lists venues (${listLines(answer)} list lines)`);
  } else {
    if (!NO_DATA.test(answer)) failures.push("does not say there is no offline places data for this city");
    if (score.verifiedVenues > 0 || listLines(answer) >= 3) failures.push(`lists venues without data (${score.verifiedVenues} real names matched, ${listLines(answer)} list lines)`);
  }
  if (score.matched.length && !HAS_DATA.has(id)) warnings.push(`named: ${score.matched.slice(0, 5).join(", ")}`);
  return { pass: failures.length === 0, failures, warnings };
}
