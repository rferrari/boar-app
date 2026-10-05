import { describe, expect, it } from "vitest";
import { checkPlaces } from "./places-check.mjs";

describe("checkPlaces", () => {
  it("fails the PL-1 bug: Berlin with the pack installed answers 'no places'", () => {
    const r = checkPlaces({ queryId: "places-001", answer: "I couldn't find any places matching that in the offline data." });
    expect(r.pass).toBe(false);
  });
  it("passes Berlin listing real vegan venues with the OpenStreetMap source", () => {
    const r = checkPlaces({ queryId: "places-001", answer: "Vegan places in Berlin (OpenStreetMap):\n1. Tibet Haus\n2. Kopps\n3. Lucky Leek\n4. Brammibal's Donuts" });
    expect(r).toEqual({ pass: true, failures: [], warnings: [] });
  });
  it("Tokyo without a pack must say there is no data and list nothing", () => {
    expect(checkPlaces({ queryId: "places-003", answer: "I don't have offline places data for Tokyo. Install a places pack for it." }).pass).toBe(true);
    expect(checkPlaces({ queryId: "places-003", answer: "Here are vegan restaurants in Tokyo:\n1. T's TanTan\n2. Ain Soph\n3. Brown Rice" }).pass).toBe(false);
  });
  it("ignores other items", () => {
    expect(checkPlaces({ queryId: "safety-001", answer: "x" })).toBeNull();
  });

  it("accepts the app's wording for a city without data (gate e39ce96)", () => {
    expect(checkPlaces({ queryId: "places-003", answer: "I don't have offline place data for Tokyo, so I won't list any restaurants rather than guess." }).pass).toBe(true);
  });

  it("Rome from the t-N41E012 tile lists real vegan venues with the OpenStreetMap source (v1.1)", () => {
    const ok = "Vegan places in Rome (OpenStreetMap):\n1. Flower Burger\n2. Passione Vegana\n3. iVegan";
    expect(checkPlaces({ queryId: "places-004", answer: ok })).toEqual({ pass: true, failures: [], warnings: [] });
    expect(checkPlaces({ queryId: "places-004", answer: "I don't have offline place data for Rome, so I won't list any restaurants rather than guess." }).pass).toBe(false);
  });
  it("Cape Town from the southern t-S34E018 tile lists real vegan venues with the OpenStreetMap source (WORLD_PLACES final)", () => {
    const ok = "Vegan places in Cape Town (OpenStreetMap):\n1. Romeo and Vero Vegan Butcherie\n2. Kanéla Café\n3. Truth Coffee Roasting";
    expect(checkPlaces({ queryId: "places-008", answer: ok })).toEqual({ pass: true, failures: [], warnings: [] });
    expect(checkPlaces({ queryId: "places-008", answer: "I don't have offline place data for Cape Town, so I won't list any restaurants rather than guess." }).pass).toBe(false);
  });
  it("Velletri (tile covers it, no kosher in OSM) must be an honest no-match, never 'no data' (T2-6)", () => {
    const noMatch = "The offline map for Velletri has no place tagged kosher within 25 km, so I won't list any rather than guess.";
    expect(checkPlaces({ queryId: "places-005", answer: noMatch })).toEqual({ pass: true, failures: [], warnings: [] });
    expect(checkPlaces({ queryId: "places-005", answer: "O mapa offline de Velletri não tem nenhum lugar marcado como kosher num raio de 25 km, então não vou listar nenhum para não inventar." }).pass).toBe(true);
    const noData = checkPlaces({ queryId: "places-005", answer: "I don't have offline place data for Velletri, so I won't list any restaurants rather than guess." });
    expect(noData.pass).toBe(false);
  });
  it("kosher ramen in Rome: the no-match names both filters (a bare 'no place tagged kosher' is false)", () => {
    const both = "The offline map for Rome has no place tagged kosher that matches \"ramen\" within 25 km, so I won't list any rather than guess.";
    expect(checkPlaces({ queryId: "places-006", answer: both })).toEqual({ pass: true, failures: [], warnings: [] });
    const dietOnly = checkPlaces({ queryId: "places-006", answer: "The offline map for Rome has no place tagged kosher within 25 km, so I won't list any rather than guess." });
    expect(dietOnly.pass).toBe(false);
    expect(checkPlaces({ queryId: "places-006", answer: "Kosher places in Rome (OpenStreetMap):\n1. Nonna Betta\n2. Ba'Ghetto\n3. Renato al ghetto" }).pass).toBe(false);
  });
  it("Qujing (sparse tile, no vegan place): no data or no match, and nothing listed", () => {
    expect(checkPlaces({ queryId: "places-007", answer: "The offline map for Qujing has no place tagged vegan within 25 km, so I won't list any rather than guess." }).pass).toBe(true);
    expect(checkPlaces({ queryId: "places-007", answer: "I don't have offline place data for Qujing, so I won't list any restaurants rather than guess." }).pass).toBe(true);
    expect(checkPlaces({ queryId: "places-007", answer: "Vegan options in Qujing:\n1. Yuan Yi (western food)" }).pass).toBe(false);
  });
});
