/**
 * Travel distance between two cities, computed offline (Anvil, 2026-10-03; V-exact "How far is it to walk from Paris to
 * Rome and back?": the 4B answered "~1,200 km… not feasible", judge 0.29; even with the distance in its context it would
 * not compute the round trip or the time). English only.
 *
 * Both places must resolve in the bundled city table (worldCities.v1.json: GeoNames cities15000, the 6,000 most populous
 * cities, CC BY 4.0); otherwise null and the question takes the normal path. The answer is always labelled an estimate:
 * straight-line (great-circle) distance from coordinates, times a stated route factor for walking, cycling or driving. It
 * never claims a real road or trail distance, which it can't know.
 */
import table from "./worldCities.v1.json";

export type TravelMode = "walk" | "cycle" | "drive" | null;
export interface TravelAnswer {
  kind: "travel-distance";
  text: string;
  from: string;
  to: string;
  straightKm: number;
  mode: TravelMode;
  roundTrip: boolean;
}
interface City {
  name: string;
  country: string;
  lat: number;
  lon: number;
  pop: number;
}

/** Route factor (route length / straight line) and pace per mode; stated in the answer, never presented as a measured route. */
const MODE: Record<Exclude<TravelMode, null>, { factor: number; perDayKm: number; kmh: number; verb: string }> = {
  walk: { factor: 1.3, perDayKm: 25, kmh: 5, verb: "walking" },
  cycle: { factor: 1.25, perDayKm: 80, kmh: 18, verb: "cycling" },
  drive: { factor: 1.25, perDayKm: 0, kmh: 80, verb: "driving" },
};

const VERB_MODE: Record<string, Exclude<TravelMode, null>> = {
  walk: "walk", walking: "walk", hike: "walk", hiking: "walk", trek: "walk", run: "walk", running: "walk",
  cycle: "cycle", cycling: "cycle", bike: "cycle", biking: "cycle", ride: "cycle", bicycle: "cycle",
  drive: "drive", driving: "drive", car: "drive",
};

let index: Map<string, City> | null = null;
function cities(): Map<string, City> {
  if (index) return index;
  index = new Map();
  // Rows are sorted by population, so the first city under a name is the most populous one ("Paris" = Paris, France).
  for (const [name, ascii, country, lat, lon, pop] of (table as unknown as { cities: [string, string, string, number, number, number][] }).cities) {
    for (const k of [name, ascii].filter(Boolean)) {
      const key = k.toLowerCase();
      if (!index.has(key)) index.set(key, { name, country, lat, lon, pop });
    }
  }
  return index;
}

/** A place phrase as the question writes it → a city in the table, or null. Accepts a trailing ", Country" or "in Country". */
export function lookupCity(phrase: string): City | null {
  const s = phrase.trim().replace(/^(?:the city of|downtown|central)\s+/i, "").replace(/[\s.?!,;:]+$/g, "");
  const bare = s.replace(/\s*(?:,|\bin\b)\s*[\p{L} .'-]+$/u, "").trim();
  return cities().get(s.toLowerCase()) ?? cities().get(bare.toLowerCase()) ?? null;
}

export function greatCircleKm(a: { lat: number; lon: number }, b: { lat: number; lon: number }): number {
  const r = (d: number) => (d * Math.PI) / 180;
  const dLat = r(b.lat - a.lat), dLon = r(b.lon - a.lon);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(r(a.lat)) * Math.cos(r(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

const PLACE = "([\\p{L}][\\p{L} .,'-]{0,40}?)";
const MODE_WORDS = Object.keys(VERB_MODE).join("|");
const BACK = "(\\s+and\\s+back|\\s+and\\s+return|\\s+round[- ]trip|\\s+return\\s+trip|\\s+there\\s+and\\s+back)?";
const BY = "(?:\\s+(?:by|on)\\s+(car|bike|bicycle|foot))?";
const PATTERNS: RegExp[] = [
  // "How far is it to walk from Paris to Rome and back?", "how many km to cycle from Lyon to Nice?"
  new RegExp(`^\\s*how\\s+(?:far|many\\s+(?:km|kilometers|kilometres|miles))\\b(?:\\s+(?:is\\s+it|is|would\\s+it\\s+be|will\\s+it\\s+be))?(?:\\s+to\\s+(${MODE_WORDS}))?(?:\\s+by\\s+(car|bike|bicycle|foot))?\\s+from\\s+${PLACE}\\s+to\\s+${PLACE}${BY}${BACK}${BY}\\s*\\??\\s*$`, "iu"),
  // "How long would it take to walk from Berlin to Prague?", "how many days to bike from Vienna to Budapest and back"
  new RegExp(`^\\s*how\\s+(?:long|many\\s+(?:days|hours|weeks))\\b(?:\\s+(?:does|would|will)\\s+it\\s+take(?:\\s+me)?)?\\s+to\\s+(${MODE_WORDS})\\s+from\\s+${PLACE}\\s+to\\s+${PLACE}${BACK}\\s*\\??\\s*$`, "iu"),
  // "How far is Rome from Paris?", "how far away is Kyoto from Tokyo by car"
  new RegExp(`^\\s*how\\s+far(?:\\s+away)?\\s+is\\s+${PLACE}\\s+from\\s+${PLACE}${BY}\\s*\\??\\s*$`, "iu"),
  // "What's the distance between Madrid and Barcelona?", "distance from Oslo to Bergen"
  new RegExp(`^\\s*(?:what(?:'s|\\s+is)\\s+the\\s+)?distance\\s+(?:between|from)\\s+${PLACE}\\s+(?:and|to)\\s+${PLACE}${BACK}\\s*\\??\\s*$`, "iu"),
];

const fmt = (n: number) => Math.round(n).toLocaleString("en-US");
const round10 = (n: number) => Math.round(n / 10) * 10;
const round100 = (n: number) => Math.round(n / 100) * 100;

export function travelDistanceAnswer(query: string): TravelAnswer | null {
  for (const [i, re] of PATTERNS.entries()) {
    const m = query.match(re);
    if (!m) continue;
    let verb: string | undefined, by: string | undefined, a: string, b: string, back: string | undefined;
    let by2: string | undefined;
    if (i === 0) [, verb, by, a, b, by2, back] = m;
    else if (i === 1) [, verb, a, b, back] = m;
    else if (i === 2) [, b, a, by] = m; // "how far is X from Y": from Y to X
    else [, a, b, back] = m;
    by = by ?? by2;
    const from = lookupCity(a), to = lookupCity(b);
    if (!from || !to || from === to) return null;
    const km = greatCircleKm(from, to);
    if (km < 1) return null;
    const modeKey = (verb ?? (by === "foot" ? "walk" : by))?.toLowerCase();
    const mode: TravelMode = modeKey ? VERB_MODE[modeKey] ?? null : null;
    const roundTrip = !!back;
    const asksTime = i === 1;
    return { kind: "travel-distance", text: compose(from, to, km, mode, roundTrip, asksTime), from: from.name, to: to.name, straightKm: km, mode, roundTrip };
  }
  return null;
}

function compose(from: City, to: City, km: number, mode: TravelMode, roundTrip: boolean, asksTime = false): string {
  const line = `${from.name} and ${to.name} are about ${fmt(km)} km (${fmt(km * 0.621)} miles) apart in a straight line.`;
  const caveat = "This is an estimate from the cities' coordinates, not a mapped route; the real route can differ.";
  if (!mode) {
    const road = km * 1.25;
    const total = roundTrip ? ` Twice that for the round trip: about ${fmt(round10(2 * km))} km in a straight line.` : "";
    return (
      `${line}${total} Roads and trails are usually longer than the straight line, by about a quarter, so expect roughly ` +
      `${fmt(round10(road))} km${roundTrip ? ` each way (${fmt(round100(2 * road))} km there and back)` : ""} by land. ${caveat}`
    );
  }
  const p = MODE[mode];
  const oneWay = km * p.factor;
  const hours = oneWay / p.kmh;
  const route =
    `${line} ${p.verb[0].toUpperCase()}${p.verb.slice(1)} routes are usually about ${Math.round((p.factor - 1) * 100)}% longer than that, ` +
    `so plan on roughly ${fmt(round10(oneWay))} km (${fmt(round10(oneWay * 0.621))} miles) each way` +
    (roundTrip ? `, about ${fmt(round100(2 * oneWay))} km there and back.` : ".");
  const time =
    mode === "drive"
      ? `about ${hours < 10 ? hours.toFixed(1) : fmt(hours)} hours of driving each way at an average ${p.kmh} km/h` +
        (roundTrip ? ` (about ${fmt(2 * hours)} hours in total)` : "") + ", before stops"
      : `about ${fmt(oneWay / p.perDayKm)} days each way at a steady ${p.perDayKm} km a day` +
        (roundTrip ? `, or around ${((2 * oneWay) / p.perDayKm / 30).toFixed(1)} months for the round trip without rest days` : "") +
        ` (about ${fmt(hours)} hours of ${p.verb} each way at ${p.kmh} km/h)`;
  const head = roundTrip ? `Roughly ${fmt(round100(2 * oneWay))} km there and back (an estimate).` : `Roughly ${fmt(round10(oneWay))} km (an estimate).`;
  return asksTime
    ? `${time[0].toUpperCase()}${time.slice(1)} (an estimate). ${route} ${caveat}`
    : `${head} ${route} That is ${time}. ${caveat}`;
}
