import { describe, expect, it } from "vitest";
import type { CatalogModel } from "../../models/manifest";
import type { PoiRegion } from "../../rag/poiRegions";
import { cityOptions } from "./travel";

function entry(id: string, sizeBytes: number): CatalogModel {
  return { id, kind: "corpus", format: "poi-pack", label: id, filename: `poi/${id}.sqlite`, sizeBytes, sha256: "x", sourceUrl: "", license: "", description: "", required: false };
}

const rome = { name: "Rome", country: "IT", lat: 41.9, lon: 12.5 };
const italy = { id: "italy", bbox: [36, 6, 47.1, 18.6] } as unknown as PoiRegion;
const gazetteer = entry("poi-world-places", 20);
const regionAssets = (r: PoiRegion) => [entry(`poi-${r.id}`, 900), gazetteer];

describe("cityOptions", () => {
  it("offers the city area and the whole region, smallest first, gazetteer included", () => {
    const options = cityOptions({ city: rome, regions: [italy], areaTiles: [entry("poi-t-N41E012", 179)], radiusKm: 15, regionAssets, gazetteer });
    expect(options.map((o) => [o.kind, o.bytes])).toEqual([
      ["area", 199],
      ["region", 920],
    ]);
    expect(options[0].assets.map((a) => a.id)).toEqual(["poi-t-N41E012", "poi-world-places"]);
  });

  it("offers only the region when tiles aren't available", () => {
    const options = cityOptions({ city: rome, regions: [italy], areaTiles: null, radiusKm: 15, regionAssets, gazetteer });
    expect(options.map((o) => o.kind)).toEqual(["region"]);
  });

  it("offers nothing when no pack covers the city", () => {
    expect(cityOptions({ city: { name: "Tokyo", lat: 35.7, lon: 139.7 }, regions: [italy], areaTiles: [], radiusKm: 15, regionAssets, gazetteer })).toEqual([]);
  });
});
