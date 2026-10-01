/**
 * The Knowledge Sanctuary preview's mock catalog: four sessions and their sample packs. Nothing
 * here is real or downloadable; titles and descriptions are i18n keys (sanctuary.sessions.*,
 * sanctuary.packs.*), so the preview reads in English and Portuguese.
 */
export const SESSIONS = ["combat", "music", "code", "survive"] as const;
export type SessionId = (typeof SESSIONS)[number];

export type Price = { kind: "free" } | { kind: "once"; boar: number } | { kind: "subscription"; boarPerMonth: number };

export interface MockPack {
  id: string;
  session: SessionId;
  /** Mock creator handle, shown as @handle. */
  creator: string;
  sizeMb: number;
  /** Mock community rating, 0-5. */
  rating: number;
  price: Price;
}

export const PACKS: MockPack[] = [
  { id: "self-defense-basics", session: "combat", creator: "dojo_notes", sizeMb: 18, rating: 4.6, price: { kind: "free" } },
  { id: "martial-arts-history", session: "combat", creator: "budo_archive", sizeMb: 42, rating: 4.3, price: { kind: "once", boar: 120 } },
  { id: "synth-design-101", session: "music", creator: "oscillator", sizeMb: 25, rating: 4.7, price: { kind: "free" } },
  { id: "chords-and-harmony", session: "music", creator: "circle_of_fifths", sizeMb: 12, rating: 4.4, price: { kind: "subscription", boarPerMonth: 15 } },
  { id: "llms-on-a-phone", session: "code", creator: "edge_inference", sizeMb: 30, rating: 4.8, price: { kind: "free" } },
  { id: "local-search-explained", session: "code", creator: "ranked_retrieval", sizeMb: 20, rating: 4.5, price: { kind: "once", boar: 80 } },
  { id: "wilderness-first-aid", session: "survive", creator: "trail_medic", sizeMb: 36, rating: 4.9, price: { kind: "free" } },
  { id: "solar-off-grid", session: "survive", creator: "sun_and_stone", sizeMb: 28, rating: 4.6, price: { kind: "subscription", boarPerMonth: 10 } },
];

export const packsOf = (session: SessionId): MockPack[] => PACKS.filter((p) => p.session === session);
