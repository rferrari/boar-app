import { describe, expect, it } from "vitest";
import { tileEntry } from "../../rag/poiRegions";
import { groupPlaceAreas, tileCorner, tileIdOf, type InstalledTile } from "./placeTiles";

const tile = (id: string, bytes: number, pois?: number): InstalledTile => ({
  tileId: id,
  entry: tileEntry({ id, sizeBytes: bytes, sha256: "", pois: pois ?? 0, vegan: 0, osmDate: "" }),
  bytes,
  pois,
});

describe("placeTiles", () => {
  it("reads the tile id from a catalog entry, and nothing else", () => {
    expect(tileIdOf(tile("t-N41E012", 1).entry)).toBe("t-N41E012");
    expect(tileIdOf({ id: "poi-world-places" })).toBeNull();
    expect(tileIdOf({ id: "poi-europe-south" })).toBeNull();
  });

  it("names a tile by its corner, never by its id", () => {
    expect(tileCorner("t-N41E012")).toBe("41°N 12°E");
    expect(tileCorner("t-S34W059")).toBe("34°S 59°W");
  });

  it("groups tiles under the city they were downloaded for, summing size and places", () => {
    const areas = groupPlaceAreas([tile("t-N41E012", 100, 900), tile("t-N41E011", 50, 100), tile("t-N52E013", 70, 500)], {
      "t-N41E012": "Rome",
      "t-N41E011": "Rome",
      "t-N52E013": "Berlin",
    });
    expect(areas.map((a) => a.city)).toEqual(["Berlin", "Rome"]);
    expect(areas[1]).toMatchObject({ bytes: 150, pois: 1000 });
    expect(areas[1].tiles.map((t) => t.tileId)).toEqual(["t-N41E012", "t-N41E011"]);
  });

  it("lists an imported tile on its own after the cities, without a count it doesn't know", () => {
    const areas = groupPlaceAreas([tile("t-S34W059", 40), tile("t-N41E012", 100, 900)], { "t-N41E012": "Rome" });
    expect(areas.map((a) => a.city ?? a.corner)).toEqual(["Rome", "34°S 59°W"]);
    expect(areas[1].pois).toBeUndefined();
  });

  it("an area with one tile of unknown count has no total", () => {
    const [rome] = groupPlaceAreas([tile("t-N41E012", 100, 900), tile("t-N41E011", 50)], { "t-N41E012": "Rome", "t-N41E011": "Rome" });
    expect(rome.pois).toBeUndefined();
  });

  it("an area has an update when any of its tiles has one", () => {
    const [rome] = groupPlaceAreas([{ ...tile("t-N41E012", 100, 900), updateAvailable: true }, tile("t-N41E011", 50, 10)], { "t-N41E012": "Rome", "t-N41E011": "Rome" });
    expect(rome.updateAvailable).toBe(true);
    expect(groupPlaceAreas([tile("t-N41E012", 1, 1)], {})[0].updateAvailable).toBe(false);
  });
});
