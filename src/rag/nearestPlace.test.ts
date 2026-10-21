import { describe, expect, it } from "vitest";
import { nearestPlaceIn } from "./poiPack";
import { formatPlacesAnswer } from "../routing/geo";

const rows = [
  { name: "Tokyo", country: "Japan", lat: 35.6895, lon: 139.69171, population: 9733276 },
  { name: "Shibuya", country: "Japan", lat: 35.664, lon: 139.698, population: 224533 },
  { name: "Yokohama", country: "Japan", lat: 35.43333, lon: 139.65, population: 3777491 },
  { name: "Hamlet", country: "Japan", lat: 36.5, lon: 139.0, population: 300 },
];
// A fake gazetteer: the bounding-box query, as SQLite would answer it.
const db = {
  getAllAsync: async (_sql: string, [la0, la1, lo0, lo1]: number[]) => rows.filter((r) => r.lat >= la0 && r.lat <= la1 && r.lon >= lo0 && r.lon <= lo1),
  getFirstAsync: async () => null,
  execAsync: async () => {},
  runAsync: async () => ({}),
} as any;

describe("nearestPlaceIn (GPS venue header names the city)", () => {
  it("a fix in Shibuya names Tokyo (most populous 100k+ within 25 km), not the ward", async () => {
    expect(await nearestPlaceIn(db, { lat: 35.659, lon: 139.7 })).toEqual({ name: "Tokyo", country: "Japan" });
  });
  it("in the countryside: the nearest place within 15 km, else null", async () => {
    expect(await nearestPlaceIn(db, { lat: 36.51, lon: 139.0 })).toEqual({ name: "Hamlet", country: "Japan" });
    expect(await nearestPlaceIn(db, { lat: 37.5, lon: 139.0 })).toBeNull();
  });
});

describe("formatPlacesAnswer header", () => {
  const intent = { lang: "en", diet: ["vegan"], near: { kind: "device" } } as any;
  it("near you in <city>, <country> when known; plain 'near you' otherwise", () => {
    expect(formatPlacesAnswer({ intent, places: [], areaLabel: "near you", byDistance: true, nearCity: { name: "Tokyo", country: "Japan" } })).toMatch(/^Vegan places near you in Tokyo, Japan, /);
    expect(formatPlacesAnswer({ intent, places: [], areaLabel: "near you", byDistance: true })).toMatch(/^Vegan places near you, /);
  });
});
