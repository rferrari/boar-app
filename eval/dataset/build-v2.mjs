#!/usr/bin/env node
// Builds dataset v2 ("Vitalik style"): literal local-food questions with gold from OpenStreetMap (Overpass) and
// Wikivoyage "Eat" listings, plus specialist crypto questions and practical travel questions.
// Gold for food items is a dated snapshot written to dataset/gold/v2/<id>.json (ODbL / CC BY-SA).
// Usage (from eval/): node dataset/build-v2.mjs [--skip-fetch]   (network: Overpass, Wikipedia, Wikivoyage; no LLM)
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DIR = dirname(fileURLToPath(import.meta.url));
const GOLD_DIR = join(DIR, "gold", "v2");
const UA = "boar-eval/0.2 (offline research app evaluation; https://github.com/rferrari/boar-app)";
const OVERPASS = process.env.OVERPASS_URL ?? "https://overpass-api.de/api/interpreter";
const skipFetch = process.argv.includes("--skip-fetch");
// Re-fetch only these ids (comma list after --refetch); default: fetch items without a gold file.
const VOYAGE_ONLY = new Set((process.argv.includes("--voyage-only") ? process.argv[process.argv.indexOf("--voyage-only") + 1] : "").split(",").filter(Boolean));
const REFETCH = new Set((process.argv.includes("--refetch") ? process.argv[process.argv.indexOf("--refetch") + 1] : "").split(",").filter(Boolean));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---------------------------------------------------------------- food: literal phrasing from the post
// diet: which OSM tag set is the gold. located: the question depends on the device location (context.lat/lon).
const FOOD = [
  { city: "Berlin", wiki: "Berlin", voy: "Berlin", diet: "vegan", q: "Tell me the best vegan restaurants in Berlin" },
  { city: "Lisbon", wiki: "Lisbon", voy: "Lisbon", diet: "vegan", q: "Tell me the best vegan restaurants in Lisbon" },
  { city: "Buenos Aires", wiki: "Buenos Aires", voy: "Buenos Aires", diet: "vegan", q: "Tell me the best vegan restaurants in Buenos Aires" },
  { city: "Singapore", wiki: "Singapore", voy: "Singapore", diet: "vegan", q: "Tell me the best vegan restaurants in Singapore" },
  { city: "Chiang Mai", wiki: "Chiang Mai", voy: "Chiang Mai", diet: "vegan", q: "Tell me the best vegan restaurants in Chiang Mai" },
  { city: "Tokyo", wiki: "Tokyo", voy: "Tokyo", diet: "vegan", q: "Tell me the best vegan restaurants in Tokyo" },
  { city: "Bangkok", wiki: "Bangkok", voy: "Bangkok", diet: "vegan", q: "Tell me the best vegan restaurants in Bangkok" },
  { city: "Istanbul", wiki: "Istanbul", voy: "Istanbul", diet: "vegan", q: "Tell me the best vegan restaurants in Istanbul" },
  { city: "Mexico City", wiki: "Mexico City", voy: "Mexico City", diet: "vegan", q: "Tell me the best vegan restaurants in Mexico City" },
  { city: "Taipei", wiki: "Taipei", voy: "Taipei", diet: "vegan", q: "Tell me the best vegan restaurants in Taipei" },
  { city: "Seoul", wiki: "Seoul", voy: "Seoul", diet: "vegan", q: "Tell me the best vegan restaurants in Seoul" },
  { city: "Prague", wiki: "Prague", voy: "Prague", diet: "vegan", q: "Tell me the best vegan restaurants in Prague" },
  { city: "Tbilisi", wiki: "Tbilisi", voy: "Tbilisi", diet: "vegetarian", q: "Where can I eat vegetarian food in Tbilisi?" },
  { city: "Kyoto", wiki: "Kyoto", voy: "Kyoto", diet: "vegetarian", q: "What are good vegetarian restaurants in Kyoto?" },
  { city: "Medellín", wiki: "Medellín", voy: "Medellín", diet: "vegetarian", q: "Recommend some vegetarian restaurants in Medellín" },
  { city: "Cape Town", wiki: "Cape Town", voy: "Cape Town", diet: "vegan", q: "Tell me the best vegan restaurants in Cape Town" },
  { city: "São Paulo", wiki: "São Paulo", voy: "São Paulo", diet: "vegan", lang: "pt-BR", q: "Quais são os melhores restaurantes veganos em São Paulo?" },
  // Location-dependent: the literal "[city I am currently in]" form; the runner must use context.lat/lon (P1).
  { city: "Barcelona", wiki: "Barcelona", voy: "Barcelona", diet: "vegan", located: { lat: 41.3874, lon: 2.1686 }, q: "Tell me the best vegan restaurants in the city I am currently in" },
  { city: "Hong Kong", wiki: "Hong Kong", voy: "Hong Kong", diet: "vegan", located: { lat: 22.2819, lon: 114.1582 }, q: "Tell me the best vegan restaurants in the city I am currently in" },
  { city: "Denver", wiki: "Denver", voy: "Denver", diet: "vegan", located: { lat: 39.7392, lon: -104.9903 }, q: "Tell me the best vegan restaurants near me" },
];

// ---------------------------------------------------------------- specialist crypto / Ethereum / cryptography
const CRYPTO = [
  { q: "Which signature algorithms are quantum resistant?", notes: "Exact prompt from the post. NIST PQC signatures: ML-DSA (CRYSTALS-Dilithium, FIPS 204), SLH-DSA (SPHINCS+, FIPS 205), FN-DSA (Falcon, FIPS 206); stateful hash-based XMSS and LMS (NIST SP 800-208). RSA, DSA, ECDSA and EdDSA are NOT quantum resistant (Shor's algorithm). Citing RSA/factoring as the answer is wrong.", src: "https://csrc.nist.gov/projects/post-quantum-cryptography", wiki: "Post-quantum cryptography" },
  { q: "What is the difference between ML-DSA and SLH-DSA?", notes: "ML-DSA: lattice-based (Module-LWE/SIS), small fast signatures (~2.4-4.6 KB). SLH-DSA: stateless hash-based, security relies only on hash functions, much larger/slower signatures (~8-50 KB). Both NIST standards (FIPS 204/205, Aug 2024).", src: "https://csrc.nist.gov/pubs/fips/205/final", wiki: "SPHINCS+" },
  { q: "Why is RSA broken by quantum computers but SHA-256 is not?", notes: "Shor's algorithm solves factoring/discrete log in polynomial time, breaking RSA/ECC. For hashes only Grover gives a quadratic speedup (~128-bit preimage security for SHA-256), so they stay secure with adequate output size.", src: "https://en.wikipedia.org/wiki/Shor%27s_algorithm", wiki: "Shor's algorithm" },
  { q: "What is EIP-4844 and what are blobs in Ethereum?", notes: "Proto-danksharding (Dencun upgrade, March 2024): blob-carrying transactions with ~128 KB blobs, separate blob gas market, KZG commitments, data pruned after ~18 days; cuts rollup data costs.", src: "https://eips.ethereum.org/EIPS/eip-4844", wiki: "Ethereum" },
  { q: "How does finality work in Ethereum proof of stake?", notes: "Casper FFG + LMD-GHOST (Gasper). Checkpoints at epoch boundaries (32 slots x 12 s); justified with 2/3 of staked ETH attesting, finalized after the next checkpoint is justified: ~2 epochs (~12.8-15 min). Reverting needs slashing of >=1/3 of stake.", src: "https://ethereum.org/en/developers/docs/consensus-mechanisms/pos/", wiki: "Proof of stake" },
  { q: "What is the difference between optimistic rollups and ZK rollups?", notes: "Optimistic: assume valid, fraud proofs, ~7-day challenge window for withdrawals (Arbitrum, Optimism/Base). ZK: validity proofs (SNARK/STARK) verified on L1, fast finality, heavier proving (zkSync, Starknet, Scroll, Linea).", src: "https://ethereum.org/en/developers/docs/scaling/", wiki: "Rollup (blockchain)" },
  { q: "What is account abstraction in Ethereum (ERC-4337)?", notes: "Smart-contract wallets without protocol change: UserOperations, alt mempool, bundlers, EntryPoint contract, paymasters (gas sponsorship), custom signature schemes. EIP-7702 (Pectra, 2025) lets EOAs set contract code.", src: "https://eips.ethereum.org/EIPS/eip-4337", wiki: "" },
  { q: "What are BLS signatures and why does Ethereum's consensus layer use them?", notes: "Pairing-based signatures on BLS12-381; aggregation of many validators' signatures into one, making attestations from hundreds of thousands of validators practical. Not quantum resistant.", src: "https://eth2book.info/capella/part2/building_blocks/signatures/", wiki: "BLS digital signature" },
  { q: "What is the difference between a zk-SNARK and a zk-STARK?", notes: "SNARK: succinct, small proofs, often trusted setup (Groth16) or universal setup (PLONK/KZG), pairing-based so not post-quantum. STARK: transparent (no trusted setup), hash-based so plausibly post-quantum, larger proofs.", src: "https://en.wikipedia.org/wiki/Non-interactive_zero-knowledge_proof", wiki: "Non-interactive zero-knowledge proof" },
  { q: "What is MEV and what is proposer-builder separation?", notes: "Maximal extractable value: profit from ordering/including/excluding transactions (arbitrage, sandwiching, liquidations). PBS splits block building from proposing; today via MEV-Boost relays (out of protocol); enshrined PBS (ePBS) is a research/upgrade topic.", src: "https://ethereum.org/en/developers/docs/mev/", wiki: "" },
  // v1.1 item (3): 10 more, notes checked against the primary source on 2026-09-26.
  { q: "What does EIP-1559 change about Ethereum transaction fees, and what happens to the base fee?", notes: "EIP-1559: a protocol base fee per block that is always burned (destroyed), plus a priority fee (tip) that goes to the block producer. The base fee moves by at most 1/8 (12.5%) per block toward a gas target of half the gas limit (elasticity multiplier 2).", src: "https://eips.ethereum.org/EIPS/eip-1559", wiki: "" },
  { q: "What is EIP-155 and what attack does it prevent?", notes: "Replay protection: the chain ID is included in the signed transaction data (nine RLP elements incl. chainid, 0, 0) and v = {0,1} + CHAIN_ID*2 + 35, so a transaction signed for one chain (e.g. mainnet, ID 1) cannot be replayed on another (e.g. Ethereum Classic).", src: "https://eips.ethereum.org/EIPS/eip-155", wiki: "" },
  { q: "What does EIP-7702 let a regular Ethereum account (EOA) do?", notes: "Set code for EOAs: new transaction type 0x04 with an authorization list of signed tuples [chain_id, address, nonce, y_parity, r, s]; writes a persistent delegation indicator (0xef0100 || address) to the EOA's code so calls execute the delegate contract's code (batching, sponsorship, smart-wallet features). Final; shipped in Pectra.", src: "https://eips.ethereum.org/EIPS/eip-7702", wiki: "" },
  { q: "What is the maximum effective balance of an Ethereum validator after EIP-7251?", notes: "EIP-7251 raises MAX_EFFECTIVE_BALANCE from 32 to 2048 ETH while MIN_ACTIVATION_BALANCE stays 32 ETH, so operators can consolidate validators and solo stakers can stake e.g. 40 ETH in one validator; reduces validator count, P2P and aggregation load.", src: "https://eips.ethereum.org/EIPS/eip-7251", wiki: "" },
  { q: "How do staking withdrawals reach the Ethereum execution layer (EIP-4895)?", notes: "Withdrawals are a system-level operation pushed from the beacon chain, not transactions: applied after all user transactions in a block, they increase the recipient balance (Gwei converted to wei), cannot fail and consume no gas; their number per block is bounded by the consensus layer. Enabled in Shanghai/Capella (2023).", src: "https://eips.ethereum.org/EIPS/eip-4895", wiki: "" },
  { q: "What is the difference between ERC-20 and ERC-721 tokens?", notes: "ERC-20: fungible tokens, every unit identical (balances). ERC-721: non-fungible tokens, each tokenId unique and tracked individually (ownerOf, transfers, approvals, optional metadata).", src: "https://eips.ethereum.org/EIPS/eip-721", wiki: "ERC-721" },
  { q: "What is ERC-4626 used for?", notes: "Tokenized vault standard: a vault holds an underlying ERC-20 asset and issues shares (also ERC-20) representing a fraction of its holdings; standard deposit/mint/withdraw/redeem and asset<->share conversion functions for yield-bearing vaults.", src: "https://eips.ethereum.org/EIPS/eip-4626", wiki: "" },
  { q: "Is ML-KEM a signature algorithm? What is it for?", notes: "No. FIPS 203 (Aug 13, 2024) ML-KEM is a key-encapsulation mechanism to establish a shared secret key over a public channel (then used for symmetric encryption/authentication); security from Module-LWE; parameter sets ML-KEM-512/768/1024; derived from CRYSTALS-Kyber. Signatures are ML-DSA / SLH-DSA.", src: "https://csrc.nist.gov/pubs/fips/203/final", wiki: "Kyber" },
  { q: "What are stateful hash-based signatures like XMSS and LMS, and why does the 'state' matter?", notes: "NIST SP 800-208 approves LMS and XMSS and their multi-tree variants HSS and XMSS^MT. They are built from one-time signature keys in a Merkle tree; the signer must track which one-time keys were used and never reuse one (reuse breaks security), hence 'stateful'. Quantum resistant; suited to e.g. firmware signing.", src: "https://csrc.nist.gov/pubs/sp/800/208/final", wiki: "Hash-based cryptography" },
  { q: "What changed in Ethereum at the Merge (EIP-3675)?", notes: "Consensus moved from proof-of-work to proof-of-stake driven by the beacon chain; triggered by terminal total difficulty (mainnet 58750000000000000000000); block rewards and ommer rewards removed, difficulty set to 0; fork choice from heaviest PoW chain to the beacon chain's LMD-GHOST (Sept 2022).", src: "https://eips.ethereum.org/EIPS/eip-3675", wiki: "The Merge (Ethereum)" },
];

// ---------------------------------------------------------------- practical travel (Wikivoyage-backed)
const TRAVEL = [
  { q: "What emergency numbers should I know in Thailand?", notes: "191 police, 1669 medical emergency/ambulance, 199 fire, 1155 tourist police (English).", voy: "Thailand" },
  { q: "What plug type and voltage does Brazil use?", notes: "Type N (IEC 60906-1) and older Type C; voltage varies by state/city: 127 V or 220 V, 60 Hz. Check locally.", voy: "Brazil" },
  { q: "Is tap water safe to drink in Mexico City?", notes: "Generally not recommended; use bottled/filtered (garrafón) water. Ice in established restaurants is usually purified.", voy: "Mexico City" },
  { q: "How do I get from Lisbon airport to the city center?", notes: "Metro red line (Aeroporto station) to the center with a transfer; also buses and taxis/ride-hailing; ~7 km. UNKNOWN-risk: bus lines change.", voy: "Lisbon" },
  { q: "What currency is used in Georgia (the country), and can I pay by card in Tbilisi?", notes: "Georgian lari (GEL). Cards widely accepted in Tbilisi; carry cash for markets, small shops and rural areas.", voy: "Georgia (country)" },
  { q: "Which side of the road do they drive on in Thailand, and can tourists drive there?", notes: "Left. An International Driving Permit (with home licence) is required for most visitors; scooter rentals without proper licence void insurance.", voy: "Thailand" },
  { q: "How do I say thank you in Thai, and does it change if I'm a man or a woman?", notes: "khop khun + polite particle: khrap (male speaker) / kha (female speaker).", voy: "Thai phrasebook" },
  { q: "When is the best time to visit Chiang Mai, and when is the smoky season?", notes: "Cool dry season Nov-Feb is best. Burning/smoky haze season roughly Feb/Mar-Apr (poor air quality). Rainy season ~May-Oct.", voy: "Chiang Mai" },
  { q: "Is tipping expected in restaurants in Portugal?", notes: "Not mandatory; service usually not added; rounding up or ~5-10% for good service in sit-down restaurants is common.", voy: "Portugal" },
  { q: "What should I know about using ride-hailing apps in Bangkok?", notes: "Grab is the main app (also Bolt, local taxis via apps); cars and motorbike taxis; agree via app price; metered taxis are an alternative. Time-sensitive: availability can change.", voy: "Bangkok" },
];

// ---------------------------------------------------------------- danger situations (community: "show what to do")
// Notes quote the cited source, checked on 2026-09-26.
const DANGER = [
  { q: "I just got bitten by a snake while hiking, two hours from the nearest road. What do I do right now?", notes: "CDC/NIOSH: keep calm; get emergency medical care ASAP (antivenom); sit/lie with the limb in a neutral position; remove rings/watches before swelling; wash with soap and water, cover with clean dry dressing; mark the edge of swelling with the time; photo of the snake from a distance. Do NOT: tourniquet, cut the wound, suck out venom, apply ice, drink alcohol, take aspirin/ibuprofen/naproxen, or handle the snake.", src: "https://www.cdc.gov/niosh/outdoor-workers/about/venomous-snakes.html" },
  { q: "My hiking partner is shivering, confused and slurring words in the cold. What should I do?", notes: "Likely hypothermia (CDC signs: shivering, exhaustion, confusion, fumbling hands, memory loss, slurred speech, drowsiness). Get to shelter, remove wet clothing, warm the CENTER of the body first (chest, neck, head, groin), skin-to-skin under loose dry layers, warm non-alcoholic drinks only if alert; call emergency help; handle gently; CPR if unresponsive and not breathing.", src: "https://www.cdc.gov/winter-weather/prevention/index.html" },
  { q: "My child spilled boiling water on their arm. What do I do?", notes: "NHS: cool under cool running water for 20 minutes (within 3 hours); remove clothing/jewellery near the burn unless stuck; cover loosely with cling film (don't wrap around); no creams, oils, butter, ice or sticky plasters; for children get medical advice (NHS 111/emergency), A&E if large, deep, or on face/hands/genitals.", src: "https://www.nhs.uk/conditions/burns-and-scalds/" },
  { q: "An earthquake starts while I'm inside a hotel room. What should I do, and what about after it stops?", notes: "Ready.gov: stay inside, don't run outside, avoid doorways; Drop, Cover (head and neck, under sturdy table if possible), Hold On until shaking stops. After: expect aftershocks (Drop/Cover/Hold On again), leave damaged buildings, follow official alerts; outside: move to open area away from buildings/power lines; in a car: pull over, parking brake.", src: "https://www.ready.gov/earthquakes" },
  { q: "After a flood the tap water might be contaminated. How do I make water safe to drink?", notes: "CDC: use bottled water if possible; boil clear water at a rolling boil for 1 minute (3 minutes above 6,500 ft / ~2,000 m); or disinfect with unscented household bleach (5-9%): 2 drops per liter, 8 drops per gallon, stand at least 30 minutes; filter/settle cloudy water first.", src: "https://www.cdc.gov/healthywater/emergency/making-water-safe.html" },
];

// ---------------------------------------------------------------- reasoning with numbers (gold computed here)
const r2 = (x) => Math.round(x * 100) / 100;
const MATH = [
  (() => { const lp100 = 7.5; const mpg = 235.2146 / lp100; return { q: "My rental car uses 7.5 liters per 100 km. What is that in miles per US gallon?", answer: r2(mpg), notes: `mpg = 235.21 / (L/100km) = ${r2(mpg)} (about 31 mpg). 1 US gal = 3.78541 L, 1 mi = 1.609344 km.` }; })(),
  (() => { const km = 18, climb = 1200; const h = km / 5 + climb / 600; return { q: "A hike is 18 km with 1,200 m of total climbing. Using Naismith's rule, about how long will it take?", answer: h, notes: `Naismith: 1 h per 5 km + 1 h per 600 m of ascent = ${km / 5} + ${climb / 600} = ${h} h (5 h 36 min), before breaks.` }; })(),
  (() => { const thb = 2450, rate = 36.5; const usd = thb / rate; return { q: "My dinner bill is 2,450 Thai baht and 1 US dollar is 36.5 baht. How much is that in dollars, and what is the total with a 10% tip?", answer: r2(usd * 1.1), notes: `${thb} / ${rate} = ${r2(usd)} USD; +10% tip (${r2(usd * 0.1)} USD, 245 THB) = ${r2(usd * 1.1)} USD (2,695 THB).` }; })(),
  (() => { const l = 3 * 2 * 3; return { q: "Three of us are hiking for two days and each person needs 3 liters of water per day. How much water do we need and how much does it weigh?", answer: l, notes: `3 people x 2 days x 3 L = ${l} L, which weighs about ${l} kg (1 L of water ≈ 1 kg).` }; })(),
  (() => { const wh = 5000 / 1000 * 3.85; return { q: "My power bank is 5,000 mAh at 3.85 V. How many watt-hours is that, and can I take it on a plane?", answer: r2(wh), notes: `Wh = Ah x V = 5 x 3.85 = ${r2(wh)} Wh. Under 100 Wh: allowed in carry-on without airline approval; spare lithium batteries must NOT go in checked baggage (FAA/IATA).`, src: "https://www.faa.gov/hazmat/packsafe/lithium-batteries" }; })(),
  (() => { const f = 104; const c = (f - 32) * 5 / 9; return { q: "It's 104°F outside. What is that in Celsius, and is it dangerous for a long walk?", answer: r2(c), notes: `C = (F - 32) x 5/9 = ${r2(c)} °C. Yes: high heat-illness risk; avoid midday exertion, drink water, shade, rest; know heat exhaustion/heat stroke signs.` }; })(),
];

// ---------------------------------------------------------------- fetch helpers
async function getJson(url, init = {}) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    const res = await fetch(url, { ...init, headers: { "User-Agent": UA, ...(init.headers ?? {}) } });
    if (res.ok) return res.json();
    if (res.status === 429 || res.status >= 500) { await sleep(10_000 * attempt); continue; }
    throw new Error(`${res.status} ${url}`);
  }
  throw new Error(`giving up on ${url}`);
}

async function wikidataId(title) {
  const j = await getJson(`https://en.wikipedia.org/w/api.php?action=query&format=json&prop=pageprops&redirects=1&titles=${encodeURIComponent(title)}`);
  const page = Object.values(j.query.pages)[0];
  return page?.pageprops?.wikibase_item ?? null;
}

async function cityCenter(title) {
  const j = await getJson(`https://en.wikipedia.org/w/api.php?action=query&format=json&prop=coordinates&redirects=1&titles=${encodeURIComponent(title)}`);
  const c = Object.values(j.query.pages)[0]?.coordinates?.[0];
  return c ? { lat: c.lat, lon: c.lon } : null;
}

async function osmFood(qid, located, radiusM = 3000) {
  const scope = located ? `nwr(around:${radiusM},${located.lat},${located.lon})` : `area["wikidata"="${qid}"]->.a;nwr(area.a)`;
  const query = `[out:json][timeout:120];${scope}[amenity~"^(restaurant|cafe|fast_food)$"][~"^diet:(vegan|vegetarian)$"~"^(only|yes)$"];out center tags;`;
  const j = await getJson(OVERPASS, { method: "POST", body: new URLSearchParams({ data: query }) });
  return {
    snapshot: j.osm3s?.timestamp_osm_base,
    venues: j.elements.filter((e) => e.tags?.name).map((e) => ({
      osm: `${e.type}/${e.id}`,
      name: e.tags.name,
      nameEn: e.tags["name:en"],
      amenity: e.tags.amenity,
      cuisine: e.tags.cuisine,
      vegan: e.tags["diet:vegan"],
      vegetarian: e.tags["diet:vegetarian"],
      lat: e.lat ?? e.center?.lat,
      lon: e.lon ?? e.center?.lon,
      street: e.tags["addr:street"],
      hours: e.tags.opening_hours,
    })),
  };
}

/** Wikivoyage listings under "Eat" sections whose text mentions vegan/vegetarian. */
async function voyageEat(title) {
  const j = await getJson(`https://en.wikivoyage.org/w/api.php?action=parse&format=json&prop=wikitext&redirects=1&page=${encodeURIComponent(title)}`);
  const text = j.parse?.wikitext?.["*"] ?? "";
  // Level-2 "Eat" section up to the next level-2 heading (keeps the ===Budget=== style subsections).
  const start = text.search(/\n==\s*Eat\s*==\s*\n/i);
  const rest = start < 0 ? "" : text.slice(start + 1);
  const next = rest.slice(2).search(/\n==[^=]/);
  const eat = start < 0 ? "" : next < 0 ? rest : rest.slice(0, next + 2);
  const listings = [...eat.matchAll(/\{\{\s*(?:eat|listing)\b([\s\S]*?)\}\}/gi)].map((m) => m[1]);
  return {
    revid: j.parse?.revid,
    hasEat: eat.length > 0,
    vegListings: listings.filter((l) => /vegan|vegetarian/i.test(l)).map((l) => (l.match(/\|\s*name\s*=\s*([^|\n]+)/) ?? [])[1]?.trim()).filter(Boolean),
    districtsOnly: !eat.length && /\{\{\s*Regionlist|==\s*Districts/i.test(text),
  };
}

// ---------------------------------------------------------------- build
mkdirSync(GOLD_DIR, { recursive: true });
const rows = [];
let n = 0;
for (const f of FOOD) {
  const id = `food-${String(++n).padStart(3, "0")}`;
  const goldFile = join(GOLD_DIR, `${id}.json`);
  let gold;
  if (VOYAGE_ONLY.has(id) && existsSync(goldFile)) {
    // Refresh only the Wikivoyage part of an existing gold file (Overpass untouched).
    gold = JSON.parse(readFileSync(goldFile, "utf8"));
    const voy = await voyageEat(f.voy);
    await sleep(1000);
    gold.wikivoyage = { title: f.voy, revid: voy.revid, license: "CC BY-SA 4.0", hasEatSection: voy.hasEat, districtsOnly: voy.districtsOnly, vegListings: voy.vegListings };
    writeFileSync(goldFile, JSON.stringify(gold, null, 1) + "\n");
    console.log(`${id} ${f.city}: wikivoyage refreshed, veg listings ${voy.vegListings.length}`);
  } else if ((skipFetch || !REFETCH.has(id)) && existsSync(goldFile)) gold = JSON.parse(readFileSync(goldFile, "utf8"));
  else {
    const qid = await wikidataId(f.wiki);
    let osm = await osmFood(qid, f.located);
    let scope = f.located ? "3 km radius around context location" : `admin area ${qid}`;
    await sleep(3000);
    if (!osm.venues.length && !f.located) {
      // Some cities' OSM boundary relations carry a different (or no) wikidata tag: fall back to a radius.
      const center = await cityCenter(f.wiki);
      osm = await osmFood(qid, center, 10_000);
      scope = `10 km radius around ${center.lat},${center.lon} (admin area ${qid} not found in OSM)`;
      await sleep(3000);
    }
    const voy = await voyageEat(f.voy);
    await sleep(1000);
    const vegan = osm.venues.filter((v) => v.vegan === "only" || v.vegan === "yes");
    gold = {
      id, city: f.city, wikidata: qid, diet: f.diet, located: f.located ?? null, fetchedAt: new Date().toISOString(),
      osm: { snapshot: osm.snapshot, scope, license: "ODbL 1.0, © OpenStreetMap contributors", veganOnly: osm.venues.filter((v) => v.vegan === "only").length, veganYes: vegan.length, vegetarianOrVegan: osm.venues.length, venues: osm.venues },
      wikivoyage: { title: f.voy, revid: voy.revid, license: "CC BY-SA 4.0", hasEatSection: voy.hasEat, districtsOnly: voy.districtsOnly, vegListings: voy.vegListings },
    };
    writeFileSync(goldFile, JSON.stringify(gold, null, 1) + "\n");
    console.log(`${id} ${f.city}: vegan-only ${gold.osm.veganOnly}, vegan ${gold.osm.veganYes}, veg-or-vegan ${gold.osm.vegetarianOrVegan} [${scope}]; voyage eat=${voy.hasEat} veg listings ${voy.vegListings.length}${voy.districtsOnly ? " (district articles)" : ""}`);
  }
  const pool = gold.diet === "vegan" ? gold.osm.venues.filter((v) => v.vegan === "only" || v.vegan === "yes") : gold.osm.venues;
  const examples = [...pool].sort((a, b) => (b.vegan === "only") - (a.vegan === "only")).slice(0, 8).map((v) => v.name);
  rows.push({
    id, category: f.located ? "local-food-located" : "local-food", query: f.q, lang: f.lang ?? "en",
    ...(f.located ? { context: { lat: f.located.lat, lon: f.located.lon, note: "device GPS; the city is not named in the query" } } : {}),
    gold: [{ source: "osm", file: `gold/v2/${id}.json`, snapshot: gold.osm.snapshot }, { source: "enwikivoyage", title: gold.wikivoyage.title, revid: gold.wikivoyage.revid }],
    grading: { type: "venue-list", city: gold.city, diet: gold.diet, minRealVenues: 3, matchAgainst: "gold file venues (name or name:en, fuzzy); a venue not in OSM is unverified, not automatically wrong" },
    license: "original (question); ODbL (OSM gold); CC BY-SA 4.0 (Wikivoyage gold)",
    source_url: "https://x.com/VitalikButerin/status/2103762130554204651",
    notes: `A good answer names several real ${gold.diet} places in ${gold.city} (ideally with area/address) and says data may be outdated. OSM ${gold.osm.snapshot}: ${gold.osm.veganOnly} fully vegan, ${gold.osm.veganYes} vegan-friendly, ${gold.osm.vegetarianOrVegan} vegetarian-or-vegan venues${f.located ? " within 3 km of the device" : ""}. Examples: ${examples.join("; ")}.`,
  });
}
CRYPTO.forEach((c, i) => rows.push({
  id: `cry-${String(i + 1).padStart(3, "0")}`, category: "crypto-expert", query: c.q, lang: "en",
  gold: c.wiki ? [{ source: "enwiki", title: c.wiki }] : [], license: "original", source_url: c.src, notes: c.notes,
}));
TRAVEL.forEach((t, i) => rows.push({
  id: `trv-${String(i + 1).padStart(3, "0")}`, category: "travel-practical", query: t.q, lang: "en",
  gold: [{ source: "enwikivoyage", title: t.voy }], license: "original (question); CC BY-SA 4.0 (Wikivoyage gold)", source_url: `https://en.wikivoyage.org/wiki/${encodeURIComponent(t.voy.replace(/ /g, "_"))}`, notes: t.notes,
}));
DANGER.forEach((d, i) => rows.push({
  id: `dng-${String(i + 1).padStart(3, "0")}`, category: "danger-situation", query: d.q, lang: "en",
  gold: [], license: "original (question); notes quote public-health guidance", source_url: d.src, notes: d.notes,
}));
MATH.forEach((m, i) => rows.push({
  id: `mth-${String(i + 1).padStart(3, "0")}`, category: "math-reasoning", query: m.q, lang: "en",
  gold: [], answer: m.answer, license: "original", source_url: m.src ?? "", notes: m.notes,
}));
writeFileSync(join(DIR, "questions.v2.jsonl"), rows.map((r) => JSON.stringify(r)).join("\n") + "\n");
console.log(`wrote questions.v2.jsonl (${rows.length} items)`);
