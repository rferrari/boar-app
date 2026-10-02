/**
 * "I'm travelling to X": what can be installed for a city. The area option
 * comes from Bramble's map tiles covering the city; the region option from
 * the region pack whose box contains it. Sizes are the catalog's, summed.
 */
import type { CatalogModel } from "../../models/manifest";
import { PoiRegion, regionForPoint } from "../../rag/poiRegions";

export interface City {
  name: string;
  country?: string;
  lat: number;
  lon: number;
  population?: number;
}

export type CityOption =
  | { kind: "area"; radiusKm: number; assets: CatalogModel[]; bytes: number }
  | { kind: "region"; region: PoiRegion; assets: CatalogModel[]; bytes: number };

/**
 * Options for a city, smallest first. `areaTiles` is null when the tile
 * catalog isn't available. The gazetteer goes along with either option.
 */
export function cityOptions(input: {
  city: City;
  regions: PoiRegion[];
  areaTiles: CatalogModel[] | null;
  radiusKm: number;
  regionAssets: (region: PoiRegion) => CatalogModel[];
  gazetteer: CatalogModel;
}): CityOption[] {
  const sum = (assets: CatalogModel[]) => assets.reduce((n, a) => n + a.sizeBytes, 0);
  const options: CityOption[] = [];
  if (input.areaTiles && input.areaTiles.length > 0) {
    const assets = [...input.areaTiles, input.gazetteer];
    options.push({ kind: "area", radiusKm: input.radiusKm, assets, bytes: sum(assets) });
  }
  const region = regionForPoint(input.city.lat, input.city.lon, input.regions);
  if (region) {
    const assets = input.regionAssets(region);
    options.push({ kind: "region", region, assets, bytes: sum(assets) });
  }
  return options.sort((a, b) => a.bytes - b.bytes);
}
