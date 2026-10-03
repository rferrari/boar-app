#!/usr/bin/env node
// OpenStreetMap gold for the places tile cases (Boar v1.1, 2026-09-27), fetched from Overpass, independent of the
// Boar tile files: places-004 = vegan venues in Rome (admin area Q220), places-005 = kosher venues within 25 km of
// Velletri (none expected: the t-N41E012 tile covers Velletri, so the honest answer is "no match", not "no data"),
// places-006 = kosher venues in Rome (some exist, none serves ramen: the no-match must name both conditions),
// places-007 = vegan venues within 25 km of Qujing (tile t-N25E103 has 10 places, none vegan: nothing may be invented),
// places-008 = vegan venues within 25 km of Cape Town (tile t-S34E018, WORLD_PLACES final @0d75f30: a southern-hemisphere tile).
// Same query shape as build-v2.mjs osmFood. Writes gold/places/<id>.json; --refetch to overwrite.
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const GOLD_DIR = join(dirname(fileURLToPath(import.meta.url)), "gold", "places");
const OVERPASS = process.env.OVERPASS_URL ?? "https://overpass-api.de/api/interpreter";
const UA = "boar-eval/1.0 (offline assistant evaluation; github.com/r4topunk)";
const refetch = process.argv.includes("--refetch");

const CASES = [
  { id: "places-004", city: "Rome", diet: "vegan", scope: 'area["wikidata"="Q220"]->.a;nwr(area.a)', scopeNote: "admin area Q220" },
  { id: "places-005", city: "Velletri", diet: "kosher", center: { lat: 41.68573, lon: 12.77753 }, radiusM: 25_000 },
  { id: "places-006", city: "Rome", diet: "kosher", scope: 'area["wikidata"="Q220"]->.a;nwr(area.a)', scopeNote: "admin area Q220" },
  { id: "places-007", city: "Qujing", diet: "vegan", center: { lat: 25.48333, lon: 103.78333 }, radiusM: 25_000 },
  { id: "places-008", city: "Cape Town", diet: "vegan", center: { lat: -33.92584, lon: 18.42322 }, radiusM: 25_000 },
];

async function overpass(scope, diet) {
  const query = `[out:json][timeout:120];${scope}[amenity~"^(restaurant|cafe|fast_food)$"]["diet:${diet}"~"^(only|yes)$"];out center tags;`;
  for (let attempt = 1; attempt <= 3; attempt++) {
    const res = await fetch(OVERPASS, { method: "POST", headers: { "User-Agent": UA }, body: new URLSearchParams({ data: query }) });
    if (res.ok) return res.json();
    if (res.status === 429 || res.status >= 500) { await new Promise((r) => setTimeout(r, 10_000 * attempt)); continue; }
    throw new Error(`${res.status} overpass`);
  }
  throw new Error("giving up on overpass");
}

mkdirSync(GOLD_DIR, { recursive: true });
for (const c of CASES) {
  const file = join(GOLD_DIR, `${c.id}.json`);
  if (existsSync(file) && !refetch) { console.log(`${c.id}: kept ${file}`); continue; }
  const scope = c.scope ?? `nwr(around:${c.radiusM},${c.center.lat},${c.center.lon})`;
  const j = await overpass(scope, c.diet);
  const venues = j.elements.filter((e) => e.tags?.name).map((e) => ({
    osm: `${e.type}/${e.id}`, name: e.tags.name, nameEn: e.tags["name:en"], amenity: e.tags.amenity, cuisine: e.tags.cuisine,
    vegan: e.tags["diet:vegan"], vegetarian: e.tags["diet:vegetarian"], [c.diet]: e.tags[`diet:${c.diet}`],
    lat: e.lat ?? e.center?.lat, lon: e.lon ?? e.center?.lon,
  }));
  const gold = {
    id: c.id, city: c.city, diet: c.diet, fetchedAt: new Date().toISOString(),
    osm: { snapshot: j.osm3s?.timestamp_osm_base, scope: c.scopeNote ?? `${c.radiusM / 1000} km radius around ${c.center.lat},${c.center.lon}`, license: "ODbL 1.0, © OpenStreetMap contributors", dietVenues: venues.length, venues },
  };
  writeFileSync(file, JSON.stringify(gold, null, 1) + "\n");
  console.log(`${c.id} ${c.city} ${c.diet}: ${venues.length} venues [${gold.osm.scope}] snapshot ${gold.osm.snapshot}`);
  await new Promise((r) => setTimeout(r, 3000));
}
