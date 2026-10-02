import { describe, expect, it } from "vitest";
import { citySummary, PoiRegion, suggestRegion, topInstalledCities } from "./poi";

function region(id: string, bbox: PoiRegion["bbox"], timeZones: string[], poiCount: number, cities: string[] = []): PoiRegion {
  return {
    id,
    name: { en: id, pt: id },
    sizeBytes: 1,
    sha256: "",
    sourceUrl: "",
    filename: `poi/${id}.sqlite`,
    poiCount,
    veganCount: 0,
    vegetarianCount: 0,
    cities: cities.map((name) => ({ name, lat: 0, lon: 0, pois: 0 })),
    bbox,
    timeZones,
    osmDate: "",
    builtAt: "",
  };
}

const brazil = region("brazil", [-34, -74, 6, -34], ["America/Sao_Paulo", "America/Manaus"], 500_000);
const saoPaulo = region("sao-paulo", [-24.1, -47.2, -23.3, -46.3], ["America/Sao_Paulo"], 40_000, ["São Paulo", "Guarulhos", "Osasco", "Santo André", "Diadema"]);
const berlin = region("berlin", [52.3, 13.0, 52.7, 13.8], ["Europe/Berlin"], 20_000);
const regions = [brazil, saoPaulo, berlin];

describe("suggestRegion", () => {
  it("prefers a measured position and says so", () => {
    expect(suggestRegion(regions, { timeZone: "Europe/Berlin", point: { lat: -23.55, lon: -46.63 } })).toEqual({
      region: saoPaulo,
      reason: "location",
    });
  });

  it("falls back to the time zone, choosing the region with the most places", () => {
    expect(suggestRegion(regions, { timeZone: "America/Sao_Paulo" })).toEqual({ region: brazil, reason: "timezone" });
  });

  it("uses the time zone when the position is outside every region", () => {
    expect(suggestRegion(regions, { timeZone: "Europe/Berlin", point: { lat: 35.68, lon: 139.69 } })?.reason).toBe("timezone");
  });

  it("suggests nothing when no region matches", () => {
    expect(suggestRegion(regions, { timeZone: "Asia/Tokyo" })).toBeNull();
    expect(suggestRegion([], { timeZone: "America/Sao_Paulo" })).toBeNull();
  });
});

describe("citySummary", () => {
  it("shows the first places and counts the rest of the list", () => {
    expect(citySummary(saoPaulo)).toEqual({ names: ["São Paulo", "Guarulhos", "Osasco"], more: 2 });
    expect(citySummary(berlin)).toEqual({ names: [], more: 0 });
  });
});

describe("topInstalledCities", () => {
  // Coordinates as in the packs: Berlin's districts sit within a few km of Berlin.
  const berlin = {
    id: "berlin",
    cities: [
      { name: "Berlin", lat: 52.52, lon: 13.41, pois: 5993 },
      { name: "Neukölln", lat: 52.48, lon: 13.44, pois: 4365 },
      { name: "Kreuzberg", lat: 52.5, lon: 13.4, pois: 5867 },
      { name: "Potsdam", lat: 52.4, lon: 13.07, pois: 900 },
    ],
  };
  const qujing = { id: "qujing", cities: [{ name: "Qujing", lat: 25.49, lon: 103.8, pois: 0 }] };
  const sp = {
    id: "sao-paulo",
    cities: [
      { name: "São Paulo", lat: -23.55, lon: -46.63, pois: 2785 },
      { name: "Guarulhos", lat: -23.46, lon: -46.53, pois: 222 },
    ],
  };
  const all = [berlin, qujing, sp];

  it("every installed region's main city before any second one, even with no food places (Prism L-4)", () => {
    expect(topInstalledCities(all, new Set(["berlin", "qujing", "sao-paulo"]), 3).map((c) => c.name)).toEqual(["Berlin", "São Paulo", "Qujing"]);
  });

  it("leaves districts out: places within 10 km of a bigger one in the same pack", () => {
    expect(topInstalledCities([berlin], new Set(["berlin"])).map((c) => c.name)).toEqual(["Berlin", "Potsdam"]);
  });

  it("fills the next rounds in region order and keeps each region's biggest-first order", () => {
    expect(topInstalledCities(all, new Set(["berlin", "sao-paulo"])).map((c) => c.name)).toEqual(["Berlin", "São Paulo", "Potsdam", "Guarulhos"]);
  });

  it("only installed packs, and nothing when none is installed", () => {
    expect(topInstalledCities(all, new Set(["qujing"]))).toEqual([{ name: "Qujing", region: "qujing" }]);
    expect(topInstalledCities(all, new Set())).toEqual([]);
  });

  it("lists a city name once even if two packs cover it", () => {
    const tiles = { id: "berlin-tiles", cities: [{ name: "Berlin", lat: 52.52, lon: 13.41, pois: 10 }] };
    expect(topInstalledCities([berlin, tiles], new Set(["berlin", "berlin-tiles"])).filter((c) => c.name === "Berlin")).toHaveLength(1);
  });
});
