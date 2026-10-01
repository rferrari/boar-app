/**
 * The fixed evaluation set run by the in-app harness (src/eval/evalHarness.ts)
 * and documented in docs/EVAL_QUERIES.md. Bump EVAL_SET_VERSION whenever a
 * query is added, removed or reworded, so result files from different
 * versions aren't compared as if they were the same set.
 */

export const EVAL_SET_VERSION = "1";

export type EvalCategory =
  | "greeting"
  | "factual"
  | "explanation"
  | "comparison"
  | "synthesis"
  | "reasoning"
  | "retrieval-grounded"
  | "no-kb-content"
  /** VITALIK_SET only: never part of the standard run. */
  | "vitalik";

export interface EvalQuery {
  id: string;
  category: EvalCategory;
  query: string;
  /** Corpus article titles a correct retrieval should surface. Empty when the bundled corpus has no relevant article. */
  expectedKbTitles: string[];
  /** What a good answer contains, for manual grading. */
  gradingNotes: string;
}

export const EVAL_SET: EvalQuery[] = [
  {
    id: "greeting-1",
    category: "greeting",
    query: "hey, what's up?",
    expectedKbTitles: [],
    gradingNotes: "One or two friendly sentences. No retrieval, no invented actions or follow-up questions.",
  },
  {
    id: "factual-1",
    category: "factual",
    query: "What is the capital of Australia?",
    expectedKbTitles: [],
    gradingNotes: "Canberra (not Sydney).",
  },
  {
    id: "factual-2",
    category: "factual",
    query: "Who proposed the theory of evolution by natural selection?",
    expectedKbTitles: ["Evolution"],
    gradingNotes: "Charles Darwin; mentioning Alfred Russel Wallace is a plus.",
  },
  {
    id: "explanation-1",
    category: "explanation",
    query: "How do vaccines work?",
    expectedKbTitles: ["Vaccine"],
    gradingNotes: "Exposes the immune system to a harmless form/part of a pathogen; memory cells enable a faster response later.",
  },
  {
    id: "explanation-2",
    category: "explanation",
    query: "Explain photosynthesis in simple terms.",
    expectedKbTitles: ["Photosynthesis"],
    gradingNotes: "Light energy + CO2 + water -> glucose + oxygen, in chloroplasts via chlorophyll.",
  },
  {
    id: "comparison-1",
    category: "comparison",
    query: "Compare the French Revolution and the Industrial Revolution.",
    expectedKbTitles: ["French Revolution", "Industrial Revolution"],
    gradingNotes: "Political upheaval (1789, France) vs economic/technological transformation (Britain, ~1760-1840); covers both sides.",
  },
  {
    id: "comparison-2",
    category: "comparison",
    query:
      "Contrast how supply and demand explains price changes with how behavioral economics complicates that picture — where does the simple model break down?",
    expectedKbTitles: ["Supply and demand", "Behavioral economics"],
    gradingNotes: "Equilibrium price from supply/demand curves vs bounded rationality, biases, heuristics; concrete breakdown examples.",
  },
  {
    id: "synthesis-1",
    category: "synthesis",
    query:
      "What do the Agricultural Revolution and the Industrial Revolution have in common as turning points in human history, and how did they differ in how quickly they changed daily life?",
    expectedKbTitles: ["Agricultural revolution", "Industrial Revolution"],
    gradingNotes: "Both reshaped production, population and society; agricultural change took millennia, industrial change decades.",
  },
  {
    id: "synthesis-2",
    category: "synthesis",
    query: "How does the immune system's response to a pathogen relate to how a vaccine works — walk through the mechanism.",
    expectedKbTitles: ["Immune system", "Vaccine"],
    gradingNotes: "Innate then adaptive response, antibodies, memory B/T cells; a vaccine triggers the adaptive response without the disease.",
  },
  {
    id: "synthesis-3",
    category: "synthesis",
    query:
      "Why is the Amazon rainforest considered important for global climate, and what does biodiversity loss there actually threaten beyond the obvious loss of species?",
    expectedKbTitles: ["Amazon rainforest"],
    gradingNotes: "Carbon sink, rainfall/water cycle, tipping point toward savanna; ecosystem services, medicines, regional climate.",
  },
  {
    id: "reasoning-1",
    category: "reasoning",
    query: "A train leaves at 3:40 pm and the trip takes 2 hours and 35 minutes. What time does it arrive?",
    expectedKbTitles: [],
    gradingNotes: "6:15 pm.",
  },
  {
    id: "reasoning-2",
    category: "reasoning",
    query:
      "A device has a 12GB RAM budget. The OS and app overhead take 2GB, the embedding model needs 200MB, and the LLM's KV cache needs 1.5GB. How much is left for the LLM's weights, and would a 9GB model fit if loaded fully into RAM?",
    expectedKbTitles: [],
    gradingNotes: "12 - 2 - 0.2 - 1.5 = 8.3GB left; a 9GB model does not fit fully in RAM.",
  },
  {
    id: "reasoning-3",
    category: "reasoning",
    query:
      "If a Mixture-of-Experts model has 100B total parameters but only activates 8B per token, and each parameter needs 1 byte at Q8, what's the minimum disk footprint, and why doesn't RAM usage scale with the 100B figure?",
    expectedKbTitles: [],
    gradingNotes: "~100GB on disk; only the active experts' weights (plus shared layers/KV cache) must be in memory per token.",
  },
  {
    id: "grounded-1",
    category: "retrieval-grounded",
    query: "Why did the Western Roman Empire fall?",
    expectedKbTitles: ["Fall of the Western Roman Empire"],
    gradingNotes: "Multiple causes: political instability, economic troubles, military pressure/invasions, division of the empire; 476 CE.",
  },
  {
    id: "grounded-2",
    category: "retrieval-grounded",
    query: "What is a black hole and how does one form?",
    expectedKbTitles: ["Black hole"],
    gradingNotes: "Region where gravity prevents light escaping; event horizon; forms from collapse of a massive star.",
  },
  {
    id: "no-kb-1",
    category: "no-kb-content",
    query: "Who was Napoleon Bonaparte?",
    expectedKbTitles: [],
    gradingNotes: "French military leader and emperor (1804-1814/15). Must not cite the unrelated 'Randy Napoleon' article.",
  },
  {
    id: "no-kb-2",
    category: "no-kb-content",
    query: "How do antibiotics work, and why does antibiotic resistance develop?",
    expectedKbTitles: [],
    gradingNotes: "Kill/inhibit bacteria (cell wall, protein synthesis); resistance via selection/mutation, overuse. No fabricated citations.",
  },
];

/**
 * Questions the bounty's inspiration, Vitalik Buterin, asks offline research apps: the battery
 * and cases in his Field Atlas fork (github.com/vbuterin/fieldatlas, tools/e2e_battery_cases.json
 * and tools/e2e_cases.json), plus his own interests. Run on its own (`--queries vitalik`), never
 * as part of the standard set, and versioned separately so its rows aren't compared with it.
 * Expected titles are the right Wikipedia articles even when no installed pack has them yet,
 * so the found-article score measures coverage.
 */
export const VITALIK_SET_VERSION = "vitalik-1";

export const VITALIK_SET: EvalQuery[] = [
  {
    id: "vitalik-lions-tigers",
    category: "vitalik",
    query: "Are lions or tigers heavier?",
    expectedKbTitles: ["Tiger", "Lion"],
    gradingNotes: "Tigers are heavier. (Field Atlas battery)",
  },
  {
    id: "vitalik-elephant-rhino",
    category: "vitalik",
    query: "Which is heavier, an African elephant or a white rhinoceros?",
    expectedKbTitles: ["African elephant", "White rhinoceros"],
    gradingNotes: "The elephant. (Field Atlas battery)",
  },
  {
    id: "vitalik-whale-dinosaur",
    category: "vitalik",
    query: "Could a blue whale weigh more than the largest dinosaur?",
    expectedKbTitles: ["Blue whale", "Argentinosaurus"],
    gradingNotes: "Yes, the blue whale is the heaviest animal known. (Field Atlas battery)",
  },
  {
    id: "vitalik-tallest-animal",
    category: "vitalik",
    query: "What is the tallest animal in the world?",
    expectedKbTitles: ["Giraffe"],
    gradingNotes: "The giraffe (about 5-6 m). Any height must be realistic. (Field Atlas e2e)",
  },
  {
    id: "vitalik-populous-countries",
    category: "vitalik",
    query: "What are the most populated countries in the world?",
    expectedKbTitles: ["World population"],
    gradingNotes: "India and China. (Field Atlas battery)",
  },
  {
    id: "vitalik-japan-nigeria",
    category: "vitalik",
    query: "Is Japan's population larger than Nigeria's?",
    expectedKbTitles: ["Nigeria", "Japan"],
    gradingNotes: "No: Nigeria's is larger. (Field Atlas battery)",
  },
  {
    id: "vitalik-thirty-years-war",
    category: "vitalik",
    query: "When did the Thirty Years War take place?",
    expectedKbTitles: ["Thirty Years' War"],
    gradingNotes: "1618-1648. (Field Atlas battery)",
  },
  {
    id: "vitalik-wwii-dates",
    category: "vitalik",
    query: "When did World War II start and end?",
    expectedKbTitles: ["World War II"],
    gradingNotes: "1939-1945. (Field Atlas battery)",
  },
  {
    id: "vitalik-wwii-ussr",
    category: "vitalik",
    query: "Did Germany invade the Soviet Union in World War II?",
    expectedKbTitles: ["Operation Barbarossa"],
    gradingNotes: "Yes, Operation Barbarossa, June 1941. (Field Atlas e2e)",
  },
  {
    id: "vitalik-wwi-winner",
    category: "vitalik",
    query: "Who won the First World War?",
    expectedKbTitles: ["World War I"],
    gradingNotes: "The Allies/Entente; the Central Powers lost. (Field Atlas e2e)",
  },
  {
    id: "vitalik-long-life-diet",
    category: "vitalik",
    query: "What should I eat to live a long and healthy life?",
    expectedKbTitles: ["Mediterranean diet"],
    gradingNotes: "Mediterranean-style diet, calorie restriction evidence; hedged, no miracle claims. (Field Atlas battery)",
  },
  {
    id: "vitalik-meat-eggs-diet",
    category: "vitalik",
    query: "Is a diet of only meat and eggs healthy?",
    expectedKbTitles: [],
    gradingNotes: "Must hedge or decline; must not declare it healthy. (Field Atlas e2e)",
  },
  {
    id: "vitalik-highest-r0",
    category: "vitalik",
    query: "Which viruses have the highest R0?",
    expectedKbTitles: ["Measles", "Basic reproduction number"],
    gradingNotes: "Measles (R0 ~12-18). (Field Atlas battery)",
  },
  {
    id: "vitalik-world-cup-2018",
    category: "vitalik",
    query: "Who won the 2018 FIFA World Cup final?",
    expectedKbTitles: [],
    gradingNotes: "Honest refusal if no source; France only if clearly marked as not from the library. (Field Atlas battery)",
  },
  {
    id: "vitalik-about-taiwan",
    category: "vitalik",
    query: "Tell me about Taiwan.",
    expectedKbTitles: ["Taiwan"],
    gradingNotes: "From the Taiwan article: island in East Asia, ~23 million people, capital Taipei. (Field Atlas battery)",
  },
  {
    id: "vitalik-about-paris",
    category: "vitalik",
    query: "Tell me about Paris.",
    expectedKbTitles: ["Paris"],
    gradingNotes: "Capital of France, facts from the article lede. (Field Atlas battery)",
  },
  {
    id: "vitalik-about-everest",
    category: "vitalik",
    query: "Tell me about Mount Everest.",
    expectedKbTitles: ["Mount Everest"],
    gradingNotes: "Highest mountain above sea level, Himalayas, Nepal/China border. (Field Atlas battery)",
  },
  {
    id: "vitalik-about-argentina",
    category: "vitalik",
    query: "Tell me about Argentina.",
    expectedKbTitles: ["Argentina"],
    gradingNotes: "Capital Buenos Aires, Spanish, South America, Patagonia. (Field Atlas e2e)",
  },
  {
    id: "vitalik-about-x",
    category: "vitalik",
    query: "Tell me about X.com.",
    expectedKbTitles: ["X (social network)"],
    gradingNotes: "The social network formerly Twitter, owned by X Corp. (Elon Musk, 2022). The 1999 X.com bank is acceptable only as history. Must not invent.",
  },
  {
    id: "vitalik-zk-snark",
    category: "vitalik",
    query: "How does a zk-SNARK differ from a zk-STARK?",
    expectedKbTitles: ["Zero-knowledge proof", "Non-interactive zero-knowledge proof"],
    gradingNotes: "SNARK: succinct, usually trusted setup, pairing-based; STARK: no trusted setup, hash-based, post-quantum, larger proofs.",
  },
  {
    id: "vitalik-quadratic-funding",
    category: "vitalik",
    query: "What is quadratic funding and what are its weaknesses?",
    expectedKbTitles: ["Quadratic funding", "Quadratic voting"],
    gradingNotes: "Matching funds by the square of the sum of square roots of contributions; weak to collusion and Sybil attacks.",
  },
  {
    id: "vitalik-far-uvc",
    category: "vitalik",
    query: "Does far-UVC light at 222 nm harm human skin?",
    expectedKbTitles: ["Far-UVC", "Ultraviolet germicidal irradiation"],
    gradingNotes: "Studies so far show little penetration and low harm to skin and eyes at safe doses; inactivates airborne pathogens.",
  },
  {
    id: "vitalik-zuzalu",
    category: "vitalik",
    query: "What was Zuzalu?",
    expectedKbTitles: ["Zuzalu", "Network state"],
    gradingNotes: "A two-month pop-up city in Montenegro in 2023 (crypto, longevity, public goods), started by Vitalik Buterin.",
  },
  {
    id: "vitalik-vegan-buenos-aires",
    category: "vitalik",
    query: "Where can I find vegan food in Buenos Aires?",
    expectedKbTitles: ["Buenos Aires", "Veganism"],
    gradingNotes: "Needs a travel source (Wikivoyage Eat section); honest if not covered.",
  },
];

/** The version a query's rows are recorded with. */
export function setVersionOf(q: EvalQuery): string {
  return q.category === "vitalik" ? VITALIK_SET_VERSION : EVAL_SET_VERSION;
}
