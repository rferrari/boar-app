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
  /** Mock community counts before the user's own votes, report and tips. */
  community: { up: number; down: number; reports: number; tippedBoar: number };
  /** A sample of the pack's text, Markdown (the preview's "look inside"). English for now. */
  preview: string;
}

export const PACKS: MockPack[] = [
  { id: "self-defense-basics", session: "combat", creator: "dojo_notes", sizeMb: 18, rating: 4.6, price: { kind: "free" }, community: { up: 214, down: 6, reports: 1, tippedBoar: 380 }, preview: "# Self-Defense Basics\n\n## Awareness first\nMost self-defense is **avoiding** the fight: notice exits, keep distance, trust a bad feeling and leave early.\n\n## If you can't leave\n- Keep your hands up and open, and speak calmly and loudly\n- Create space, then move toward people and light\n- Your goal is to get away, not to win\n\n## After\nGet somewhere safe, then call for help and write down what happened." },
  { id: "martial-arts-history", session: "combat", creator: "budo_archive", sizeMb: 42, rating: 4.3, price: { kind: "once", boar: 120 }, community: { up: 97, down: 4, reports: 0, tippedBoar: 120 }, preview: "# History of Martial Arts\n\n## Many roots\nFighting systems grew up independently around the world: wrestling in ancient Greece and Mongolia, boxing in Rome, kalaripayattu in southern India.\n\n## East Asia\n- **Judo** was founded by Kano Jigoro in Japan in 1882\n- **Karate** developed in Okinawa\n- **Taekwondo** took its modern form in Korea in the 1950s\n\n## Today\nMany became Olympic sports; others are practised mainly for fitness and discipline." },
  { id: "synth-design-101", session: "music", creator: "oscillator", sizeMb: 25, rating: 4.7, price: { kind: "free" }, community: { up: 305, down: 9, reports: 2, tippedBoar: 610 }, preview: "# Synth Design 101\n\n## The signal path\n1. An **oscillator** makes the raw tone (sine, saw, square)\n2. A **filter** shapes it, usually cutting the highs\n3. An **amplifier** sets its loudness over time\n\n## Envelopes\nADSR: *attack, decay, sustain, release*. A short attack sounds plucked; a slow one sounds like a pad.\n\n## Try it\nSaw wave, low-pass filter, slow attack: you have a warm pad." },
  { id: "chords-and-harmony", session: "music", creator: "circle_of_fifths", sizeMb: 12, rating: 4.4, price: { kind: "subscription", boarPerMonth: 15 }, community: { up: 142, down: 3, reports: 0, tippedBoar: 95 }, preview: "# Chords & Harmony\n\n## Triads\nA major triad stacks a major third and a minor third (C, E, G). A minor triad swaps them (A, C, E).\n\n## Progressions\n- **I – V – vi – IV** powers countless pop songs\n- **ii – V – I** is the backbone of jazz\n\n## Tip\nPlay the same progression in a new key to hear what changes and what stays." },
  { id: "llms-on-a-phone", session: "code", creator: "edge_inference", sizeMb: 30, rating: 4.8, price: { kind: "free" }, community: { up: 488, down: 12, reports: 3, tippedBoar: 1240 }, preview: "# Running LLMs on a Phone\n\n## What limits you\n- **RAM**: the model's weights must fit in memory\n- **Prompt length**: the phone reads the prompt before it writes the first word\n\n## Quantization\nA Q4 model stores each weight in about 4 bits, so a 1.5B model needs roughly 1 GB.\n\n## Keep it fast\nShort prompts, one model loaded at a time, and only the passages that answer." },
  { id: "local-search-explained", session: "code", creator: "ranked_retrieval", sizeMb: 20, rating: 4.5, price: { kind: "once", boar: 80 }, community: { up: 176, down: 5, reports: 1, tippedBoar: 260 }, preview: "# Local Search Explained\n\n## Two kinds of search\n- **Keyword** search (BM25) finds the exact words\n- **Meaning** search compares embeddings, numbers that capture what a passage is about\n\n## Together\nKeywords catch names and numbers; meaning catches paraphrases. Ranking both and merging them beats either alone.\n\n## On a phone\nThe library is indexed before you download it, so nothing heavy runs on the device." },
  { id: "wilderness-first-aid", session: "survive", creator: "trail_medic", sizeMb: 36, rating: 4.9, price: { kind: "free" }, community: { up: 533, down: 7, reports: 0, tippedBoar: 1480 }, preview: "# Wilderness First Aid\n\n## First, check the scene\nMake sure you won't become a second casualty. Then check the person: responsive? breathing?\n\n## Bleeding\nApply **firm, direct pressure** with a clean cloth and keep it there.\n\n## When to get help\n- Trouble breathing, chest pain, confusion\n- Bleeding that won't stop\n\nCall your local emergency number as soon as you can. This preview is not medical training." },
  { id: "solar-off-grid", session: "survive", creator: "sun_and_stone", sizeMb: 28, rating: 4.6, price: { kind: "subscription", boarPerMonth: 10 }, community: { up: 201, down: 8, reports: 1, tippedBoar: 330 }, preview: "# Solar Off-Grid Guide\n\n## The four parts\n1. **Panels** turn sunlight into DC power\n2. A **charge controller** protects the battery\n3. The **battery** stores energy for the night\n4. An **inverter** makes AC for normal plugs\n\n## Sizing\nAdd up your daily watt-hours, then size the battery for at least one cloudy day." },
];

export const packsOf = (session: SessionId): MockPack[] => PACKS.filter((p) => p.session === session);
