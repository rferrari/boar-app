import { describe, it, expect } from "vitest";
import type { Place } from "./answerEvents";
import en from "../locales/en.json";
import pt from "../locales/pt.json";
import {
  attributionCredit,
  attributionSpoken,
  coordinatesText,
  areaCityName,
  cuisineLabels,
  dietLabels,
  formatDistance,
  geoUri,
  openState,
  parseOpeningHours,
  placeA11yLabel,
  placeDetailLine,
  spokenDistance,
  deviceClockApplies,
  showUseLocation,
  formatDataDate,
  placesEmptyTitle,
  citySuggestions,
  lastKnownOf,
  agoText,
} from "./placesFormat";

const t = (key: string, opts?: Record<string, unknown>) => (opts ? `${key}${JSON.stringify(opts)}` : key);
// 2026-09-28 is a Monday.
const at = (hh: number, mm = 0, dayOffset = 0) => new Date(2026, 8, 28 + dayOffset, hh, mm);

describe("formatDistance", () => {
  const cases: [number, string, string][] = [
    [0, "0 m", "0 m"],
    [9, "10 m", "10 m"],
    [349, "350 m", "350 m"],
    [351, "350 m", "350 m"],
    [999, "1.0 km", "1,0 km"],
    [1000, "1.0 km", "1,0 km"],
    [1049, "1.0 km", "1,0 km"],
    [1250, "1.3 km", "1,3 km"],
    [9950, "10 km", "10 km"],
    [12400, "12 km", "12 km"],
  ];
  it.each(cases)("%i m → %s (EN) / %s (PT)", (m, en, pt) => {
    expect(formatDistance(m, "en-US")).toBe(en);
    expect(formatDistance(m, "pt-BR")).toBe(pt);
  });

  it("spells the unit for screen readers", () => {
    expect(spokenDistance(350, "pt-BR", t)).toBe('chat.places.m{"value":"350"}');
    expect(spokenDistance(1250, "pt-BR", t)).toBe('chat.places.km{"value":"1,3"}');
  });
});

describe("dietLabels", () => {
  it("tells a vegan place from one with vegan options, and skips 'no'", () => {
    expect(dietLabels({ vegan: "only" }, t)).toEqual(["chat.places.diet.vegan.only"]);
    expect(dietLabels({ vegetarian: "only", vegan: "yes" }, t)).toEqual([
      "chat.places.diet.vegan.yes",
      "chat.places.diet.vegetarian.only",
    ]);
    expect(dietLabels({ vegan: "no" }, t)).toEqual([]);
  });

  it("doesn't repeat 'Vegetarian' on a vegan-only place (Prism P-2)", () => {
    expect(dietLabels({ vegan: "only", vegetarian: "only" }, t)).toEqual(["chat.places.diet.vegan.only"]);
    expect(dietLabels({ vegan: "only", vegetarian: "yes", gluten_free: "yes" }, t)).toEqual([
      "chat.places.diet.vegan.only",
      "chat.places.diet.gluten_free.yes",
    ]);
    expect(dietLabels(undefined, t)).toEqual([]);
  });
});

describe("dietFlag", () => {
  it("replaces a doubtful tag with a note, and softens an uncertain 'only'", () => {
    expect(dietLabels({ vegan: "only" }, t, "verify")).toEqual(["chat.places.dietToVerify"]);
    expect(dietLabels({ vegan: "only", vegetarian: "yes" }, t, "uncertain")).toEqual([
      "chat.places.diet.vegan.tagged",
      "chat.places.diet.vegetarian.yes",
    ]);
  });
});

describe("cuisineLabels", () => {
  it("formats OSM values and drops diet words", () => {
    const echo = (k: string) => k;
    expect(cuisineLabels(["vegan", "fine_dining", "indian", "thai"], echo)).toEqual(["Fine dining", "chat.places.cuisine.indian"]);
  });
  it("names the 20 common values in the app's language, and every one has EN and PT copy", () => {
    const echo = (k: string) => k;
    expect(cuisineLabels(["ice_cream", "burger", "coffee_shop"], echo, 3)).toEqual(["chat.places.cuisine.ice_cream", "chat.places.cuisine.burger", "chat.places.cuisine.coffee_shop"]);
    const all = ["pizza", "burger", "coffee_shop", "ice_cream", "italian", "chinese", "regional", "sandwich", "chicken", "mexican", "japanese", "kebab", "indian", "asian", "sushi", "thai", "french", "seafood", "greek", "american"];
    for (const lang of [en, pt]) for (const c of all) expect(typeof (lang as any).chat.places.cuisine[c]).toBe("string");
    expect((pt as any).chat.places.cuisine.ice_cream).toBe("Sorveteria");
  });
});

describe("areaCityName (Piston ecb83d3: 'Em Rome' for 'Roma')", () => {
  it("shows the city as the question wrote it, else the gazetteer's name", () => {
    expect(areaCityName({ kind: "city", label: "Rome", place: { name: "Rome", asked: "Roma" } })).toBe("Roma");
    expect(areaCityName({ kind: "city", label: "Rome", place: { name: "Rome", asked: "rome" } })).toBe("Rome");
    expect(areaCityName({ kind: "city", label: "São Paulo", place: { name: "Sao Paulo", asked: "são paulo" } })).toBe("São Paulo");
    expect(areaCityName({ kind: "city", place: { name: "Rio de Janeiro", asked: "rio de janeiro" } })).toBe("Rio de Janeiro");
    expect(areaCityName({ kind: "city", place: { name: "Munich", asked: "munique" } })).toBe("Munique");
    expect(areaCityName({ kind: "city", label: "Rome", place: { name: "Rome" } })).toBe("Rome");
    expect(areaCityName({ kind: "near", label: "near you" })).toBe("");
  });
});

describe("opening hours", () => {
  it("reads common forms and says open until / closed", () => {
    expect(openState("Mo-Fr 11:00-22:00; Sa 12:00-23:00", at(12))).toEqual({ open: true, closesAt: "22:00" });
    expect(openState("Mo-Fr 11:00-22:00", at(9))).toEqual({ open: false, opensAt: "11:00" });
    expect(openState("Mo-Fr 11:00-22:00", at(23))).toEqual({ open: false, opensAt: undefined });
    expect(openState("Mo-Fr 11:00-22:00; Sa,Su off", at(12, 0, 5))).toEqual({ open: false, opensAt: undefined });
    expect(openState("24/7", at(3))).toEqual({ open: true });
  });

  it("handles split hours and times past midnight", () => {
    expect(openState("Mo-Su 12:00-15:00,19:00-23:00", at(16))).toEqual({ open: false, opensAt: "19:00" });
    // Sunday 18:00-02:00, checked at Monday 01:00.
    expect(openState("Su 18:00-02:00", at(1))).toEqual({ open: true, closesAt: "02:00" });
  });

  it("gives up on anything it doesn't understand rather than guess", () => {
    for (const raw of ["Mo-Fr 10:00-18:00; PH off", "sunrise-sunset", "Jan-Mar Mo 10:00-12:00", "Mo 10-12", ""]) {
      expect(parseOpeningHours(raw)).toBeNull();
    }
    expect(openState(undefined, at(12))).toBeNull();
  });
});

describe("rows", () => {
  const place: Place = {
    id: "osm:node/1",
    name: "Mão Verde",
    lat: -23.5505199,
    lon: -46.6333094,
    diet: { vegan: "only" },
    cuisine: ["brazilian"],
    distanceM: 351,
    address: "Rua Augusta 100",
    openingHours: "Mo-Su 11:00-22:00",
    source: "osm",
  };

  it("reads a row as one sentence with units spelled out", () => {
    expect(placeA11yLabel(place, at(12), "pt-BR", t)).toBe(
      'Mão Verde, chat.places.m{"value":"350"}, chat.places.diet.vegan.only, Brazilian, chat.places.openUntil{"time":"22:00"}, Rua Augusta 100, chat.places.fromSource{"source":"chat.places.sourceOsm"}'
    );
  });

  it("shows no open/closed without the device's local time (another city)", () => {
    expect(placeDetailLine(place, null, t)).toEqual({ text: "chat.places.diet.vegan.only · Brazilian", closed: false });
    expect(placeA11yLabel(place, null, "en-US", t)).not.toContain("openUntil");
  });

  it("marks the detail line closed only with parsed hours", () => {
    expect(placeDetailLine(place, at(23), t).closed).toBe(true);
    expect(placeDetailLine({ ...place, openingHours: "by appointment" }, at(23), t)).toEqual({
      text: "chat.places.diet.vegan.only · Brazilian",
      closed: false,
    });
  });

  it("gives coordinates and a geo: URI for offline maps apps", () => {
    expect(coordinatesText(place)).toBe("-23.55052, -46.63331");
    expect(geoUri(place)).toBe("geo:-23.550520,-46.633309?q=-23.550520,-46.633309(M%C3%A3o%20Verde)");
  });
});

describe("deviceClockApplies", () => {
  it("uses the phone's clock near me, and in a named city only when the phone is inside it", () => {
    expect(deviceClockApplies({ kind: "near" })).toBe(true);
    expect(deviceClockApplies({ kind: "city", deviceInside: true })).toBe(true);
    expect(deviceClockApplies({ kind: "city", deviceInside: false })).toBe(false);
    expect(deviceClockApplies({ kind: "city" })).toBe(false);
  });
});

describe("showUseLocation", () => {
  it("offers the GPS when permission was never asked (skipped in setup), was granted, or had no fix", () => {
    for (const status of ["prompt", undefined, "granted", "unavailable", "stale"]) expect(showUseLocation(true, status)).toBe(true);
  });

  it("hides it when permission is denied or the app can't locate", () => {
    expect(showUseLocation(true, "denied")).toBe(false);
    expect(showUseLocation(false, "prompt")).toBe(false);
  });
});

describe("formatDataDate", () => {
  it("turns the pack's ISO timestamp into a date in the reader's language (Prism P-1, Iris)", () => {
    expect(formatDataDate("2026-09-26T20:27:59Z", "en-GB")).toMatch(/^26 Sept? 2026$/); // ICU versions differ on "Sep"/"Sept"
    expect(formatDataDate("2026-09-26T20:27:59Z", "en-US")).toBe("Sep 26, 2026");
    expect(formatDataDate("2026-09-26T20:27:59Z", "pt-BR")).toMatch(/^26 de set\.? de 2026$/);
  });

  it("drops what it can't read", () => {
    expect(formatDataDate(undefined, "en-US")).toBeNull();
    expect(formatDataDate("soon", "en-US")).toBeNull();
  });
});

describe("placesEmptyTitle filter wording", () => {
  it("puts the diet filter in lower case inside the sentence (Prism P-4)", () => {
    const tt = (key: string, opts?: Record<string, unknown>) =>
      key === "chat.places.filter.vegan" ? "Vegan" : opts ? `${key}${JSON.stringify(opts)}` : key;
    const r = { coverage: "none" as const, places: [], filters: ["vegan"], area: { place: { name: "Tokyo" } } };
    expect(placesEmptyTitle(r, tt)).toBe('chat.places.noneInCityFiltered{"city":"Tokyo","filter":"vegan"}');
  });
});

describe("citySuggestions", () => {
  const cities = [{ name: "Berlin" }, { name: "Hamburg" }, { name: "berlin" }, { name: "Munich" }, { name: " " }];
  it("keeps order, drops repeats and blanks (Prism L-2)", () => {
    expect(citySuggestions(cities)).toEqual(["Berlin", "Hamburg", "Munich"]);
  });

  it("leaves out the city just asked, whatever its case (P-5)", () => {
    expect(citySuggestions(cities, "BERLIN ")).toEqual(["Hamburg", "Munich"]);
  });

  it("caps the list and gives nothing without packs", () => {
    expect(citySuggestions(cities, undefined, 2)).toEqual(["Berlin", "Hamburg"]);
    expect(citySuggestions([])).toEqual([]);
  });
});

describe("lastKnownOf / agoText (stale location, Boar)", () => {
  it("reads a well-formed lastKnown and rejects the rest", () => {
    expect(lastKnownOf({ lastKnown: { city: " Berlin ", country: "DE", ageS: 1380 } })).toEqual({ city: "Berlin", country: "DE", ageS: 1380 });
    expect(lastKnownOf({})).toBeNull();
    expect(lastKnownOf({ lastKnown: { city: "", ageS: 10 } })).toBeNull();
    // Defensive at runtime, even though the type requires ageS.
    expect(lastKnownOf({ lastKnown: { city: "Berlin" } as never })).toBeNull();
  });

  it("says how long ago the way people say it", () => {
    expect(agoText(1380, t)).toBe('chat.places.ago.min{"count":23}');
    expect(agoText(20, t)).toBe('chat.places.ago.min{"count":1}');
    expect(agoText(2 * 3600 + 100, t)).toBe('chat.places.ago.h{"count":2}');
    expect(agoText(3 * 86400, t)).toBe('chat.places.ago.d{"count":3}');
  });
});

describe("placesEmptyTitle near me", () => {
  it("uses the 'near you' sentence, not the city one, for a near-me area (Piston)", () => {
    const r = { coverage: "none" as const, places: [], filters: ["vegan"], area: { kind: "near" as const, label: "near you" } };
    expect(placesEmptyTitle(r, t)).toBe('chat.places.noneNearFiltered{"filter":"vegan"}');
  });
});

describe("attribution credit (Prism CH-25: the spoken name contains the visible credit)", () => {
  const tr = (d: typeof en) => (key: string, opts?: Record<string, unknown>) => {
    const v = key.split(".").reduce<any>((o, k) => o?.[k], d) as string;
    return opts ? v.replace(/\{\{(\w+)\}\}/g, (_m, k) => String(opts[k])) : v;
  };
  it.each([
    ["en", en, "en-US"],
    ["pt", pt, "pt-BR"],
  ] as const)("%s", (_l, d, locale) => {
    const credit = attributionCredit([{ source: "osm", date: "2026-08" }], locale, tr(d));
    expect(credit).toContain("OpenStreetMap");
    const spoken = attributionSpoken(credit, tr(d));
    expect(spoken.startsWith(credit)).toBe(true);
    expect(spoken.length).toBeGreaterThan(credit.length);
  });
});
