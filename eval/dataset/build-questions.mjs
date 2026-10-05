#!/usr/bin/env node
// Source of truth for questions.v1.jsonl. Edit here, then run
// `node eval/dataset/build-questions.mjs` to regenerate the JSONL.
// Once v1 is frozen (first reference answers generated), any change goes to a
// new VERSION instead of editing v1.
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const VERSION = 1;
const W = "enwiki";
const V = "enwikivoyage";

// [id, category, lang, query, gold[[source, title]], notes]
const Q = [
  // explain — mechanism of a single phenomenon
  ["exp-001", "explain", "en", "How does a refrigerator move heat out of its interior?", [[W, "Vapor-compression refrigeration"]], "Refrigerant evaporates inside (absorbs heat), compressor, condenser outside releases heat, expansion valve."],
  ["exp-002", "explain", "en", "Why does the Moon always show the same face to Earth?", [[W, "Tidal locking"]], "Synchronous rotation: rotation period equals orbital period, caused by tidal torque over time. Libration lets us see ~59%."],
  ["exp-003", "explain", "en", "What is the placebo effect, and why do clinical trials use blinding?", [[W, "Placebo"], [W, "Blinded experiment"]], "Improvement from expectation, not treatment; blinding prevents expectation/observer bias."],
  ["exp-004", "explain", "en", "How does a GPS receiver work out its position?", [[W, "Global Positioning System"]], "Timed signals from >=4 satellites, trilateration of pseudoranges, 4th satellite solves receiver clock error."],
  ["exp-005", "explain", "en", "What causes the northern lights?", [[W, "Aurora"]], "Charged solar wind particles guided by Earth's magnetic field excite oxygen/nitrogen in upper atmosphere; near polar regions."],
  ["exp-006", "explain", "en", "How do noise-cancelling headphones work?", [[W, "Active noise control"]], "Microphones sample ambient sound, electronics emit inverted (anti-phase) wave, destructive interference; best on low steady frequencies."],
  ["exp-007", "explain", "en", "Why does bread dough rise?", [[W, "Leavening agent"]], "Yeast ferments sugars into CO2 (and ethanol); gluten network traps gas; heat expands it in the oven."],
  ["exp-008", "explain", "en", "What is compound interest, and why does it grow faster than simple interest?", [[W, "Compound interest"]], "Interest earned on accumulated interest; exponential vs linear growth; compounding frequency matters."],
  ["exp-009", "explain", "en", "How does a lithium-ion battery store and release energy?", [[W, "Lithium-ion battery"]], "Li ions shuttle between anode (graphite) and cathode (metal oxide) through electrolyte; electrons through external circuit."],
  ["exp-010", "explain", "pt-BR", "Como funciona o sistema de freios ABS de um carro?", [[W, "Anti-lock braking system"]], "Sensores de velocidade das rodas; modulação da pressão para evitar travamento; mantém dirigibilidade."],
  ["exp-011", "explain", "en", "What is the tragedy of the commons?", [[W, "Tragedy of the commons"]], "Individuals acting in self-interest overuse a shared resource; Hardin 1968 (Lloyd 1833); solutions: regulation, property rights, Ostrom's community governance."],
  ["exp-012", "explain", "en", "Why is the sky blue during the day but red at sunset?", [[W, "Rayleigh scattering"]], "Short wavelengths scatter more; at sunset light crosses more atmosphere so blue is scattered away, leaving red/orange."],

  // compare — two or more things side by side
  ["cmp-001", "compare", "en", "How do hurricanes and tornadoes differ in how they form, their size, and how long they last?", [[W, "Tropical cyclone"], [W, "Tornado"]], "Hurricanes: warm ocean, hundreds of km, days-weeks. Tornadoes: thunderstorms over land, tens-hundreds of m, minutes."],
  ["cmp-002", "compare", "en", "What are the main differences between viruses and bacteria?", [[W, "Virus"], [W, "Bacteria"]], "Bacteria are living cells that reproduce alone and are treatable with antibiotics; viruses need host cells, much smaller, antibiotics don't work."],
  ["cmp-003", "compare", "en", "TCP versus UDP: what's the difference and when would you choose each?", [[W, "Transmission Control Protocol"], [W, "User Datagram Protocol"]], "TCP connection-oriented, reliable, ordered; UDP connectionless, low overhead. Web/email vs streaming/games/DNS."],
  ["cmp-004", "compare", "en", "How can you tell an alligator from a crocodile?", [[W, "Alligator"], [W, "Crocodile"]], "Snout shape (U vs V), crocodile's lower teeth visible when mouth closed, habitat (salt tolerance), range."],
  ["cmp-005", "compare", "en", "Compare parliamentary and presidential systems of government.", [[W, "Parliamentary system"], [W, "Presidential system"]], "Executive drawn from/accountable to legislature vs separately elected; fusion vs separation of powers; confidence votes."],
  ["cmp-006", "compare", "en", "What is the difference between the Julian and Gregorian calendars, and why did countries switch?", [[W, "Julian calendar"], [W, "Gregorian calendar"]], "Leap year rule (century years); Julian drifts ~1 day/128 years; 1582 reform; 10+ days skipped."],
  ["cmp-007", "compare", "en", "What is the difference between type 1 and type 2 diabetes?", [[W, "Type 1 diabetes"], [W, "Type 2 diabetes"]], "Autoimmune destruction of beta cells/insulin dependence vs insulin resistance; onset age; lifestyle factors; treatment."],
  ["cmp-008", "compare", "en", "AC versus DC electricity: how do they differ, and why do power grids mostly use AC?", [[W, "Alternating current"], [W, "Direct current"]], "Direction reversal; transformers make voltage conversion easy for long-distance transmission; HVDC exists for some links."],
  ["cmp-009", "compare", "en", "How did government in the Roman Republic differ from the Roman Empire?", [[W, "Roman Republic"], [W, "Roman Empire"]], "Elected consuls/Senate/assemblies vs emperor with concentrated power; transition under Augustus 27 BC."],
  ["cmp-010", "compare", "pt-BR", "Quais são as diferenças entre o café arábica e o robusta?", [[W, "Coffea arabica"], [W, "Coffea canephora"]], "Arábica: altitude, sabor mais suave, menos cafeína; robusta: mais resistente, mais cafeína, mais amargo, mais barato."],
  ["cmp-011", "compare", "en", "Compare mitosis and meiosis.", [[W, "Mitosis"], [W, "Meiosis"]], "Mitosis: 2 identical diploid cells, growth/repair. Meiosis: 4 genetically distinct haploid gametes, crossing over, two divisions."],
  ["cmp-012", "compare", "en", "What distinguishes igneous, sedimentary, and metamorphic rocks?", [[W, "Igneous rock"], [W, "Sedimentary rock"], [W, "Metamorphic rock"]], "Cooled magma/lava; deposited and lithified sediment; transformed by heat/pressure. Examples: granite, sandstone, marble."],

  // synthesis — pull several causes/effects together
  ["syn-001", "synthesis", "en", "What factors are thought to have caused the Late Bronze Age collapse around 1200 BC?", [[W, "Late Bronze Age collapse"]], "Multiple: drought/climate, Sea Peoples, earthquakes, systems collapse of trade networks, internal revolts; no single consensus."],
  ["syn-002", "synthesis", "en", "How did Gutenberg's printing press change European society?", [[W, "Printing press"]], "Cheap books, literacy, spread of Reformation and scientific ideas, standardized vernaculars, loss of church/scribe monopoly."],
  ["syn-003", "synthesis", "en", "What were the main causes and consequences of the Great Famine in Ireland?", [[W, "Great Famine (Ireland)"]], "Potato blight, dependence on potato, land system/British policy; ~1M deaths, ~1M+ emigrated, lasting population decline."],
  ["syn-004", "synthesis", "en", "How did the Black Death change Europe's economy and society?", [[W, "Black Death"]], "30-60% died; labor shortage raised wages, weakened serfdom, persecution of Jews, religious upheaval."],
  ["syn-005", "synthesis", "en", "What are the main arguments for and against daylight saving time?", [[W, "Daylight saving time"]], "For: evening daylight, retail/recreation, small energy claims. Against: health/sleep disruption, weak energy savings, coordination costs."],
  ["syn-006", "synthesis", "en", "How did shipping containers transform global trade?", [[W, "Containerization"]], "Malcom McLean 1950s; standardized ISO boxes cut loading costs/time drastically, intermodal transport, enabled globalized supply chains, port changes."],
  ["syn-007", "synthesis", "en", "What are the leading explanations for the extinction of the non-avian dinosaurs?", [[W, "Cretaceous–Paleogene extinction event"]], "Chicxulub asteroid impact (main), Deccan Traps volcanism as contributing factor; ~66 million years ago."],
  ["syn-008", "synthesis", "en", "Why did the Aral Sea shrink, and what were the consequences?", [[W, "Aral Sea"]], "Soviet irrigation diverted Amu Darya/Syr Darya for cotton; fisheries collapse, salinity, toxic dust storms, health problems, climate change locally; North Aral partial recovery (Kok-Aral dam)."],
  ["syn-009", "synthesis", "en", "What was the Suez Crisis of 1956 and why did it matter geopolitically?", [[W, "Suez Crisis"]], "Nasser nationalized canal; Israel/UK/France invasion; US/USSR pressure forced withdrawal; decline of British/French imperial power."],
  ["syn-010", "synthesis", "en", "How did the Meiji Restoration modernize Japan?", [[W, "Meiji Restoration"]], "1868 restoration of imperial rule; end of feudal domains/samurai class, industrialization, conscription, Western-style education and constitution."],
  ["syn-011", "synthesis", "pt-BR", "Quais foram as causas e as consequências do ciclo da borracha na Amazônia?", [[W, "Amazon rubber cycle"]], "Demanda industrial (vulcanização, pneus); Manaus e Belém enriquecem; exploração de seringueiros e indígenas; colapso após plantações asiáticas (sementes levadas por Wickham)."],
  ["syn-012", "synthesis", "en", "What is known about the causes of colony collapse disorder in honey bees?", [[W, "Colony collapse disorder"]], "Multiple factors: Varroa mites and viruses, neonicotinoid pesticides, poor nutrition, stress from migratory beekeeping; no single cause."],

  // multistep — needs two or more facts plus a calculation or chain
  ["mst-001", "multistep", "en", "If it is 3 PM in Tokyo, what time is it in São Paulo?", [[W, "Japan Standard Time"], [W, "Time in Brazil"]], "UTC+9 vs UTC-3 (Brazil has no DST since 2019): 12 hours behind, 3 AM the same day."],
  ["mst-002", "multistep", "en", "How many years passed between the fall of Constantinople and the first crewed Moon landing?", [[W, "Fall of Constantinople"], [W, "Apollo 11"]], "1453 to 1969 = 516 years."],
  ["mst-003", "multistep", "en", "Is Mount Everest or Mauna Kea taller, and does the answer depend on how you measure?", [[W, "Mount Everest"], [W, "Mauna Kea"]], "Everest highest above sea level (8,849 m); Mauna Kea tallest from base on ocean floor (~10,200 m)."],
  ["mst-004", "multistep", "en", "Of the countries that border both France and Germany, which has the largest population?", [[W, "Belgium"], [W, "Switzerland"], [W, "Luxembourg"]], "Belgium (~11.8M) vs Switzerland (~9M) vs Luxembourg (~0.67M): Belgium."],
  ["mst-005", "multistep", "en", "Who was US president when the Berlin Wall fell, and how old was he at the time?", [[W, "Berlin Wall"], [W, "George H. W. Bush"]], "George H. W. Bush, born 12 June 1924; wall fell 9 November 1989: age 65."],
  ["mst-006", "multistep", "en", "Roughly what temperature does water boil at on the summit of Mont Blanc?", [[W, "Mont Blanc"], [W, "Boiling point"]], "Summit ~4,806 m; pressure ~55-57 kPa; water boils around 84-85 °C."],
  ["mst-007", "multistep", "en", "About how long does sunlight take to reach Mars, compared with Earth?", [[W, "Speed of light"], [W, "Mars"]], "Earth ~8.3 min; Mars ~1.52 AU average: ~12.7 min (varies ~11-14 min with orbit)."],
  ["mst-008", "multistep", "en", "Which chemical element has an atomic number equal to the number of US states?", [[W, "Tin"], [W, "U.S. state"]], "50 states: tin (Sn)."],
  ["mst-009", "multistep", "en", "In what year did the author of One Hundred Years of Solitude win the Nobel Prize, and who was the previous Latin American winner of the literature prize?", [[W, "Gabriel García Márquez"], [W, "Pablo Neruda"]], "García Márquez 1982; previous Latin American laureate Pablo Neruda (1971)."],
  ["mst-010", "multistep", "pt-BR", "Qual rio atravessa a capital do país onde fica Machu Picchu?", [[W, "Machu Picchu"], [W, "Lima"], [W, "Rímac River"]], "Machu Picchu fica no Peru; capital Lima; rio Rímac."],
  ["mst-011", "multistep", "en", "How many times wider is Jupiter than Earth, and roughly how many Earths would fit inside it by volume?", [[W, "Jupiter"]], "Diameter ~11 times; volume ~1,300 Earths (1,321)."],
  ["mst-012", "multistep", "en", "Walking at 5 km/h, about how long would it take to walk the full length of the Golden Gate Bridge?", [[W, "Golden Gate Bridge"]], "Total length ~2.7 km (2,737 m): about 33 minutes."],

  // traveler-firstaid — safety-critical, offline traveler
  ["fa-001", "traveler-firstaid", "en", "What should I do right away if someone is stung by a jellyfish at the beach?", [[W, "Jellyfish"]], "Get out of water, rinse with seawater (not fresh water), vinegar for box jellyfish in some regions, remove tentacles carefully, hot water immersion; seek help for severe reactions. No urine."],
  ["fa-002", "traveler-firstaid", "en", "How do I recognize heat stroke, and what should I do while waiting for help?", [[W, "Heat stroke"]], "High body temp (>40 °C), confusion, hot skin; emergency: call for help, cool rapidly (cold water immersion, ice packs, fanning), move to shade."],
  ["fa-003", "traveler-firstaid", "en", "An adult is choking and can't speak or cough. What are the steps?", [[W, "Choking"], [W, "Heimlich maneuver"]], "Call emergency; back blows and abdominal thrusts (Heimlich) alternating per guideline; if unconscious start CPR."],
  ["fa-004", "traveler-firstaid", "en", "What are the symptoms of altitude sickness, and how can I prevent it when trekking?", [[W, "Altitude sickness"]], "Headache, nausea, fatigue, insomnia; gradual ascent (~300-500 m/day sleeping altitude above 3,000 m), hydration, acetazolamide; descend if severe (HAPE/HACE)."],
  ["fa-005", "traveler-firstaid", "en", "How should a snakebite be handled in the field, and what should you avoid doing?", [[W, "Snakebite"]], "Keep calm and still, immobilize limb, remove rings, get to hospital for antivenom. Avoid cutting, sucking, tourniquets, ice."],
  ["fa-006", "traveler-firstaid", "en", "How do I treat a minor burn from a hot pan?", [[W, "Burn"]], "Cool under cool running water ~20 min, remove jewelry, cover loosely with clean dressing; no ice, butter; seek care for large/deep/face burns."],
  ["fa-007", "traveler-firstaid", "en", "What are the signs of hypothermia, and what should I do if a hiking partner has it?", [[W, "Hypothermia"]], "Shivering, confusion, slurred speech, clumsiness; get out of wind/wet, remove wet clothing, insulate, warm gradually, warm drinks if alert; handle gently."],
  ["fa-008", "traveler-firstaid", "en", "How can I make stream water safe to drink when hiking if I don't have a filter?", [[W, "Portable water purification"]], "Boil (1 min rolling boil, 3 min at high altitude), chemical treatment (chlorine, iodine), SODIS (UV in clear bottles)."],
  ["fa-009", "traveler-firstaid", "en", "How can I prevent and treat traveler's diarrhea?", [[W, "Travelers' diarrhea"], [W, "Oral rehydration therapy"]], "Food/water hygiene (boil it, cook it, peel it); treat with oral rehydration solution, loperamide for mild cases, antibiotics if severe/bloody fever."],
  ["fa-010", "traveler-firstaid", "pt-BR", "Como fazer RCP (reanimação cardiopulmonar) em um adulto?", [[W, "Cardiopulmonary resuscitation"]], "Verificar resposta, chamar ajuda/DEA; compressões no centro do peito, 100-120/min, 5-6 cm de profundidade; 30:2 se treinado ou só compressões."],
  ["fa-011", "traveler-firstaid", "en", "How can I quickly tell if someone is having a stroke?", [[W, "Stroke"]], "FAST: Face drooping, Arm weakness, Speech difficulty, Time to call emergency; note time of onset."],
  ["fa-012", "traveler-firstaid", "en", "What should I do for a sprained ankle while traveling?", [[W, "Sprained ankle"]], "Rest, ice, compression, elevation initially; gradual weight bearing; seek imaging if unable to bear weight (Ottawa rules)."],

  // traveler-geo — practical geography and logistics
  ["geo-001", "traveler-geo", "en", "What currency is used in Vietnam, and can I pay with US dollars there?", [[W, "Vietnamese đồng"], [V, "Vietnam"]], "Vietnamese đồng (VND); USD accepted in some tourist places but đồng expected for most everyday transactions."],
  ["geo-002", "traveler-geo", "en", "When is the best time of year to see the northern lights in Iceland?", [[V, "Iceland"], [W, "Aurora"]], "Roughly September to mid-April, dark clear nights; not in summer (midnight sun)."],
  ["geo-003", "traveler-geo", "en", "Which side of the road do people drive on in Japan?", [[W, "Left- and right-hand traffic"]], "Left."],
  ["geo-004", "traveler-geo", "en", "What voltage and plug type are used in the United Kingdom?", [[W, "Mains electricity by country"]], "230 V, 50 Hz, Type G (BS 1363) three rectangular pins."],
  ["geo-005", "traveler-geo", "en", "What is the capital of Australia, and why isn't it Sydney?", [[W, "Canberra"]], "Canberra; purpose-built compromise between Sydney and Melbourne rivalry, chosen 1908, parliament since 1927."],
  ["geo-006", "traveler-geo", "en", "Which languages are spoken in Switzerland, and in which regions?", [[W, "Languages of Switzerland"]], "German (north/centre, majority), French (west, Romandy), Italian (Ticino, south), Romansh (Graubünden)."],
  ["geo-007", "traveler-geo", "en", "What are the main ways to get from Narita Airport to central Tokyo?", [[V, "Narita"], [W, "Narita International Airport"]], "Narita Express (JR), Keisei Skyliner to Ueno/Nippori, limousine/airport buses, cheaper Keisei/Access trains; ~1 hour."],
  ["geo-008", "traveler-geo", "en", "How do tipping customs in the United States compare with Japan?", [[W, "Gratuity"]], "US: expected, ~15-20% at restaurants. Japan: generally not practiced and can be seen as rude/confusing."],
  ["geo-009", "traveler-geo", "en", "When is the rainy season in Thailand, and does it differ between regions?", [[W, "Geography of Thailand"]], "Southwest monsoon ~May-October for most of country; east coast of peninsula (Koh Samui) wettest ~October-December."],
  ["geo-010", "traveler-geo", "pt-BR", "Brasileiros precisam de visto para visitar países do espaço Schengen como turistas?", [[W, "Visa policy of the Schengen Area"]], "Não para estadias de até 90 dias em 180; ETIAS previsto (checar status). Passaporte válido, possível exigência de comprovantes."],
  ["geo-011", "traveler-geo", "en", "What is the highest mountain in Africa, and can people without climbing experience reach the summit?", [[W, "Mount Kilimanjaro"]], "Kilimanjaro, 5,895 m, Tanzania; trekking routes, no technical climbing required, but altitude sickness risk; guides mandatory."],
  ["geo-012", "traveler-geo", "en", "Which countries does the Danube flow through?", [[W, "Danube"]], "Germany, Austria, Slovakia, Hungary, Croatia, Serbia, Romania, Bulgaria, Moldova, Ukraine (10 countries); ends in Black Sea."],

  // traveler-lang — phrases and usage
  ["lng-001", "traveler-lang", "en", "How do I greet someone politely in Korean?", [[V, "Korean phrasebook"]], "Annyeonghaseyo (formal/polite); annyeong (casual); bow slightly."],
  ["lng-002", "traveler-lang", "en", "What are some basic Spanish phrases for asking directions?", [[V, "Spanish phrasebook"]], "¿Dónde está...?, ¿Cómo llego a...?, a la izquierda/derecha, todo recto, ¿Está lejos?"],
  ["lng-003", "traveler-lang", "en", "What does 'obrigado' mean in Portuguese, and why do some people say 'obrigada' instead?", [[V, "Portuguese phrasebook"]], "'Thank you'; agrees with speaker's gender: men say obrigado, women obrigada."],
  ["lng-004", "traveler-lang", "en", "How do you count from one to five in Mandarin Chinese?", [[V, "Chinese phrasebook"], [W, "Chinese numerals"]], "yī, èr, sān, sì, wǔ (一二三四五)."],
  ["lng-005", "traveler-lang", "en", "In German, when should I use 'Sie' instead of 'du'?", [[W, "T–V distinction"]], "Sie: formal, strangers, adults, workplace; du: friends, family, children, informal settings; wait to be offered du."],
  ["lng-006", "traveler-lang", "en", "What is the difference between 'tu' and 'vous' in French?", [[W, "T–V distinction"], [V, "French phrasebook"]], "Tu informal singular; vous formal singular or any plural; use vous with strangers."],
  ["lng-007", "traveler-lang", "en", "How do I ask 'how much does this cost?' in Italian?", [[V, "Italian phrasebook"]], "Quanto costa? (Quanto costa questo?)"],
  ["lng-008", "traveler-lang", "en", "What does 'sawasdee' mean in Thai, and why do people add 'krap' or 'ka'?", [[V, "Thai phrasebook"]], "Hello/goodbye; polite particles by speaker gender: khrap (male), kha/ka (female)."],
  ["lng-009", "traveler-lang", "en", "How do I say 'I am allergic to peanuts' in French?", [[V, "French phrasebook"]], "Je suis allergique aux cacahuètes (arachides)."],
  ["lng-010", "traveler-lang", "en", "Which direction is Arabic written in, and how do you say hello in Arabic?", [[W, "Arabic script"], [V, "Arabic phrasebook"]], "Right to left; as-salamu alaykum (reply wa alaykum as-salam), marhaba."],
  ["lng-011", "traveler-lang", "pt-BR", "Como peço a conta num restaurante em inglês e em espanhol?", [[V, "Spanish phrasebook"]], "Inglês: 'Could I have the check/bill, please?'; espanhol: 'La cuenta, por favor.'"],
  ["lng-012", "traveler-lang", "en", "What does 'sumimasen' mean in Japanese, and when is it used?", [[V, "Japanese phrasebook"]], "Excuse me / sorry / thank you for trouble; getting attention (waiter), apologizing lightly."],

  // hard-for-1b — false premises, myths, negation, precise facts
  ["h1b-001", "hard-for-1b", "en", "Why is the Great Wall of China visible from the Moon with the naked eye?", [[W, "Great Wall of China"]], "False premise: it is not visible from the Moon, and hardly from low Earth orbit with the naked eye. Must correct the premise."],
  ["h1b-002", "hard-for-1b", "en", "Did Albert Einstein fail mathematics at school?", [[W, "Albert Einstein"]], "No, myth; he excelled at math (mastered calculus by ~15). Myth possibly from Swiss grading scale confusion."],
  ["h1b-003", "hard-for-1b", "en", "Who was the second person to walk on the Moon?", [[W, "Apollo 11"]], "Buzz Aldrin (Apollo 11, after Neil Armstrong)."],
  ["h1b-004", "hard-for-1b", "en", "Which is larger by area: Brazil or the contiguous United States (the lower 48)?", [[W, "Brazil"], [W, "Contiguous United States"]], "Brazil (~8.5M km²) > contiguous US (~8.1M km²). Whole US including Alaska is larger than Brazil."],
  ["h1b-005", "hard-for-1b", "en", "Is a tomato a fruit or a vegetable?", [[W, "Tomato"]], "Botanically a fruit (berry); culinarily treated as vegetable; Nix v. Hedden (1893) US tariff ruling."],
  ["h1b-006", "hard-for-1b", "en", "Did the 20th century begin on January 1, 1900 or January 1, 1901?", [[W, "20th century"]], "Strictly 1 January 1901 (no year zero); popularly 1900."],
  ["h1b-007", "hard-for-1b", "en", "Name a South American country that does not share a border with Brazil.", [[W, "Brazil"]], "Chile or Ecuador (only two)."],
  ["h1b-008", "hard-for-1b", "en", "Do goldfish really have a three-second memory?", [[W, "Goldfish"]], "No, myth; goldfish remember for months, can be trained."],
  ["h1b-009", "hard-for-1b", "en", "Which came first: the founding of Harvard University or Isaac Newton's work on calculus?", [[W, "Harvard University"], [W, "Isaac Newton"]], "Harvard 1636; Newton developed calculus ~1665-1666: Harvard first."],
  ["h1b-010", "hard-for-1b", "pt-BR", "Em quantos continentes o território do Egito está?", [[W, "Egypt"]], "Dois: África e Ásia (península do Sinai)."],
  ["h1b-011", "hard-for-1b", "en", "Is it true that lightning never strikes the same place twice?", [[W, "Lightning"]], "False; tall structures are struck repeatedly (Empire State Building ~20-25 times/year)."],
  ["h1b-012", "hard-for-1b", "en", "What is the smallest country in South America by area?", [[W, "Suriname"]], "Suriname (~164,000 km²) is the smallest sovereign state; French Guiana is smaller but is a French region, not a country."],
];

const PREFIX = {
  explain: "exp", compare: "cmp", synthesis: "syn", multistep: "mst",
  "traveler-firstaid": "fa", "traveler-geo": "geo", "traveler-lang": "lng", "hard-for-1b": "h1b",
};

const urlFor = (source, title) =>
  `https://${source === V ? "en.wikivoyage.org" : "en.wikipedia.org"}/wiki/${encodeURIComponent(title.replace(/ /g, "_"))}`;

const items = Q.map(([id, category, lang, query, gold, notes]) => {
  if (!id.startsWith(`${PREFIX[category]}-`)) throw new Error(`id ${id} does not match category ${category}`);
  return {
    id,
    category,
    query,
    lang,
    gold: gold.map(([source, title]) => ({ source, title })),
    // Questions are original to this eval (MIT, like the repo). Gold articles are CC BY-SA 4.0.
    license: "MIT (question); CC BY-SA 4.0 (gold articles)",
    source_url: gold.length ? urlFor(gold[0][0], gold[0][1]) : "",
    notes,
  };
});

const ids = new Set(items.map((i) => i.id));
if (ids.size !== items.length) throw new Error("duplicate ids");

const out = join(dirname(fileURLToPath(import.meta.url)), `questions.v${VERSION}.jsonl`);
writeFileSync(out, items.map((i) => JSON.stringify(i)).join("\n") + "\n");
console.log(`wrote ${items.length} items to ${out}`);
