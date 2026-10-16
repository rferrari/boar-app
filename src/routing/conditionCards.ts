/**
 * Per-condition first-aid cards v0.1 (Compass, rnd/research/compass/cards/first-aid-condition-cards.md; FROZEN
 * exp/fa-cards/FROZEN.v0.1.sha256 combined e0730060…a56b, which supersedes v0 before QUEUE #14 ran): where the engine
 * would show the generic no-source health card (noHealthSourceAnswer, from the health path or from A1's danger
 * detector), a question whose terms name exactly one condition gets that condition's card: the must-do line, the
 * key don'ts, the facts' sources, then the emergency line.
 *
 * Trigger v0.1 (trigger.v0.1.md): exactly one condition by the term regexes (terms.py v0.1, ported) → that card;
 * several (MULTI) or none → the generic card. v0's embedding-centroid fallback was removed before the run (an
 * out-of-sample check on 400 FirstAidQA questions found it wrong on 3 of 4 firings). Snakebite maps to the existing
 * WHO card (firstAidCards.ts). Texts: conditionCards.v0.json = cards.v0.json byte for byte (sha256 5de2015a…).
 * NOT clinically reviewed yet: an eval arm, not a shipping text.
 * Pure. The regexes are Python's (Unicode \w and \b), ported with lookarounds on [\p{L}\p{N}_].
 */
import cards from "./conditionCards.v1.json";
import { ACTION_INTENT, emergencyLine, SHOULD_INTENT } from "./context";
import { emergencyNumbersLine } from "./emergencyNumbers";
import { snakebiteFirstAidCard } from "./firstAidCards";
import { isCoExposure, isCoExposureAcrossTurns } from "./coHazard";

const BASE_TERMS: Record<string, RegExp> = {
  co: /(?<![\p{L}\p{N}_])(carbon monoxide|(?<![\p{L}\p{N}_])co(?![\p{L}\p{N}_]) poison|mon[óo]xido de carbono|gas (heater|leak)|vazamento de g[áa]s|aquecedor a g[áa]s|generator (inside|indoors))/iu,
  burn: /(?<![\p{L}\p{N}_])(burn(ed|t)? (my|his|her|their)|scald|boiling (water|oil)|hot (pan|oil|water|stove)|queim(ei|ou|ado|ada|adura)|[áa]gua fervendo|[óo]leo quente|panela quente)/iu,
  heatstroke: /(?<![\p{L}\p{N}_])(heat ?stroke|stopped sweating|collapsed in the heat|very hot and confused|insola[çc][ãa]o|golpe de calor|desmai[\p{L}\p{N}_]* no calor|desabou no calor)/iu,
  hypothermia: /(?<![\p{L}\p{N}_])(hypotherm|hipoterm|(shiver[\p{L}\p{N}_]*|slurr[\p{L}\p{N}_]+|stumbl[\p{L}\p{N}_]+|confused)[^.?!]{0,80}(?<![\p{L}\p{N}_])(cold|snow|winter|freezing|icy|lake)(?![\p{L}\p{N}_])|(?<![\p{L}\p{N}_])(cold|snow|winter|freezing)(?![\p{L}\p{N}_])[^.?!]{0,80}(shiver[\p{L}\p{N}_]*|slurr[\p{L}\p{N}_]+|stumbl[\p{L}\p{N}_]+|confus[\p{L}\p{N}_]+)|(tremend[\p{L}\p{N}_]*|fala enrolada|enrolando a fala|confus[\p{L}\p{N}_]+)[^.?!]{0,80}(?<![\p{L}\p{N}_])(frio|neve|inverno|gelad[\p{L}\p{N}_]*)(?![\p{L}\p{N}_])|(?<![\p{L}\p{N}_])(frio|neve|inverno|gelad[\p{L}\p{N}_]*)(?![\p{L}\p{N}_])[^.?!]{0,80}(tremend[\p{L}\p{N}_]*|enrolad[\p{L}\p{N}_]*|confus[\p{L}\p{N}_]+))/iu,
  frostbite: /(?<![\p{L}\p{N}_])(frostbit|frostnip|frozen (toes|fingers|feet)|white,? (waxy|hard)|congelad|queimadura de frio)/iu,
  choking: /(?<![\p{L}\p{N}_])(chok(e|ing)|can'?t (breathe|speak) .{0,20}(food|stuck)|engasg)/iu,
  bleeding: /(?<![\p{L}\p{N}_])(bleeding (a lot|heavily|badly)|won'?t stop bleeding|blood (is )?(pouring|spurting)|sangrando muito|sangramento forte|n[ãa]o para de sangrar)/iu,
  heartattack: /(?<![\p{L}\p{N}_])(chest pain|pressure in (his|her|my) chest|heart attack|dor (forte )?no peito|aperto (forte )?no peito|infarto|ataque card)/iu,
  stroke: /(?<![\p{L}\p{N}_])(face (droop|drooping|drooped)|crooked (smile|face)|stroke(?![\p{L}\p{N}_])|one side (of (his|her) body )?(is )?weak|boca torta|rosto (ca[íi]do|torto)|derrame|(?<![\p{L}\p{N}_])avc(?![\p{L}\p{N}_]))/iu,
  seizure: /(?<![\p{L}\p{N}_])(seizure|convuls|fit(?![\p{L}\p{N}_])|jerking|shaking on the (floor|ground)|se debatendo|ataque epil)/iu,
  anaphylaxis: /(?<![\p{L}\p{N}_])(anaphyla|throat (is )?(swelling|closing)|lips? (are |is )?swelling|garganta (inchando|fechando)|choque anafil)/iu,
  battery: /(?<![\p{L}\p{N}_])(swallow[\p{L}\p{N}_]* (a |an )?(button |coin|watch |small )?batter|engoli[\p{L}\p{N}_]* (uma )?(bateria|pilha))/iu,
  eyechem: /(?<![\p{L}\p{N}_])((bleach|chemical|cleaning product|acid|alkali)[\p{L}\p{N}_]* .{0,25}(eye)|(eye).{0,25}(bleach|chemical)|(sanit[áa]ria|produto qu[íi]mico|produto de limpeza).{0,25}olho)/iu,
  electric: /(?<![\p{L}\p{N}_])(electric(al)? shock|electrocut|live (wire|cable)|got a shock|tomou choque|levou um choque|fio desencapado)/iu,
  jellyfish: /(?<![\p{L}\p{N}_])(jellyfish|man.o.war|blue ?bottle|[áa]gua.viva|caravela)/iu,
  nosebleed: /(?<![\p{L}\p{N}_])(nose ?bleed|nose (is |started )?bleeding|bleeding from (my|the|his|her) nose|sangr[\p{L}\p{N}_]* (pelo|no|do) nariz|nariz (sangrando|sangra))/iu,
  earthquake: /(?<![\p{L}\p{N}_])(earthquake|ground is shaking|building is shaking|terremoto|tremor de terra|pr[ée]dio (est[áa] )?tremendo)/iu,
  drowning: /(?<![\p{L}\p{N}_])(drown|pulled .{0,20}out of the (water|pool|sea)|afog|tiramos .{0,20}(da|do) (piscina|mar|[áa]gua))/iu,
  snakebite: /(?<![\p{L}\p{N}_])(snake ?bit|bitten by a (snake|viper|rattlesnake)|snake (just )?bit|cobra (me )?(picou|mordeu)|picad[\p{L}\p{N}_]* (por|de) (uma )?(cobra|jararaca|cascavel)|mordid[oa] por (una )?(serpiente|culebra|v[íi]bora|cascabel)|(serpiente|culebra|v[íi]bora|cascabel) (me |le |lo |la |nos )?(mordi[óo]|pic[óo])|mordedura de (serpiente|culebra|v[íi]bora|cascabel)|mordi[óo] una (serpiente|culebra|v[íi]bora|cascabel))/iu,
  // v1 long tail (Compass terms_v1.py NEW, FROZEN.v1 e407c676…), ported mechanically by Sonar exp-29 (parity-tested).
  hypoglycemia: /^(?!.*(?<![\p{L}\p{N}_])(high blood sugar|hyperglyc|a[çc][úu]car alto|hiperglic))(?:.*(low (blood )?sugar|hypoglyc|hipoglic|glicose baixa|a[çc][úu]car (no sangue )?baixo)|(?=.*(diabet|diab[ée]tic|insulin))(?=.*(?<![\p{L}\p{N}_])(shak[\p{L}\p{N}_]*|sweat[\p{L}\p{N}_]*|confus[\p{L}\p{N}_]*|faint[\p{L}\p{N}_]*|dizzy|pale|drows[\p{L}\p{N}_]*|tremend[\p{L}\p{N}_]*|suad[\p{L}\p{N}_]*|suando|confus[\p{L}\p{N}_]*|desmai[\p{L}\p{N}_]*|tont[\p{L}\p{N}_]*)))/ius,
  // health13: no case-insensitive AMS/HACE/HAPE (ES "hace" = "it does / ago": heatstroke, overdose and chest-pain chats got
  // the altitude card; acronyms are matched case-sensitively in regexCondition); "at/a N m" only from 2,000 m (a rip
  // current "a 100 metros" is a distance, not an altitude).
  altitude: /(?<![\p{L}\p{N}_])(altitude sickness|mountain sickness|high altitude|soroche|mal de altitude|doen[çc]a da altitude)/iu,
  beesting: /(?<![\p{L}\p{N}_])((?<![\p{L}\p{N}_])(bee|wasp|hornet|yellow ?jacket)s?(?![\p{L}\p{N}_]).{0,40}(?<![\p{L}\p{N}_])(sting|stung|stinger)|(?<![\p{L}\p{N}_])stung by an? (bee|wasp|hornet)|(abelha|vespa|marimbondo).{0,40}(picad|picou|ferr[ãa]o)|picad[\p{L}\p{N}_]* (por|de) (uma )?(abelha|vespa|marimbondo))/iu,
  scorpion: /(?<![\p{L}\p{N}_])(scorpion|escorpi[ãa]o)/iu,
  marinepuncture: /(?<![\p{L}\p{N}_])(sting ?ray|sea urchin|stonefish|weever|lionfish|arraia|ouri[çc]o.do.mar|peixe.pedra)/iu,
  tick: /(?<![\p{L}\p{N}_])((?<![\p{L}\p{N}_])ticks?(?![\p{L}\p{N}_])|carrapato)/iu,
  animalbite: /(?<![\p{L}\p{N}_])((?<![\p{L}\p{N}_])(dog|cat|monkey|bat|fox|raccoon|macaque)s?(?![\p{L}\p{N}_]).{0,30}(?<![\p{L}\p{N}_])(bit|bite|bitten|scratch[\p{L}\p{N}_]*)|(?<![\p{L}\p{N}_])bitten by an? (dog|cat|monkey|bat|fox|raccoon)|rabies|raiva|(cachorro|c[ãa]o|gato|macaco|morcego).{0,30}(mordeu|mordid|arranh)|mordid[\p{L}\p{N}_]* (por|de) (um )?(cachorro|c[ãa]o|gato|macaco|morcego))/iu,
  heatexhaustion: /(?<![\p{L}\p{N}_])(heat exhaustion|heat cramps?|cramps?.{0,40}(?<![\p{L}\p{N}_])(heat|sun|hot)(?![\p{L}\p{N}_])|exaust[ãa]o (pelo|por) calor|c[ãa]imbras?.{0,40}(?<![\p{L}\p{N}_])(sol|calor)(?![\p{L}\p{N}_]))/iu,
};

// Compass first-aid-cards draft (2026-10-03, rnd/research/compass/exp/first-aid-cards/triggers.proposed.mjs, round 4, sha256
// see SHA256SUMS; self-test 36/36): replaces the terms of the 10 drafted conditions (ES terms, drowning "pulled … out of the lake", choking split
// into child vs infant, coins with batteries); the other conditions keep the v0.1 / v1 terms.
const B = String.raw`(?<![\p{L}\p{N}_])`, E = String.raw`(?![\p{L}\p{N}_])`;
const INFANT = String.raw`(?:baby|infant|newborn|beb[êeé]|rec[ée]m.nascid\w*|lactante|[0-9]{1,2} (?:months?|meses) old|(?<!(?:gr[áa]vida|gestante|embarazada|encinta|estou|estoy|t[ôo]|estamos|est[áa])\s{1,3})de [0-9]{1,2} meses|[0-9]{1,2}.?(?:month|m[eê]s).?old)`;
const W = String.raw`[\p{L}\p{N}_]`;
const u = (src: string) => src.replaceAll("\\w", W);
// Compass round 5 (2026-10-03, triggers.proposed.mjs 4373e81e…, cards bfdaeec4…): rip current vs drowning; child vs adult scorpion.
const RIP = String.raw`(?:rip ?currents?|rip ?tide|riptide|undertow|caught in a rip|(?:swept|pulled|dragged|carried|sucked) (?:\w+ )?(?:out|away)[^.?!]{0,40}(?:sea|ocean|beach|shore|waves|current|surf)|current[^.?!]{0,30}(?:pull|drag|sweep|carr)\w*[^.?!]{0,30}(?:out|away)|corrente de retorno|correnteza|repuxo|(?:puxad|arrastad|levad)\w* (?:mar adentro|para o alto.mar|pela corrente)|corriente de (?:resaca|retorno)|resaca[^.?!]{0,40}(?:mar|playa|nad|arrastr|agua)|(?:mar|playa|nad|agua)[^.?!]{0,40}resaca|(?:arrastrad|jalad|llevad)\w* mar adentro|corriente[^.?!]{0,25}(?:arrastr|jal|llev)\w*|corrente(?:za)?[^.?!]{0,25}(?:pux|lev|arrast)\w*|(?:pux|lev|arrast)\w*[^.?!]{0,25}pela corrente(?:za)?)`;
const POST = String.raw`(?:out of the (?:water|sea|ocean|surf)|not breathing|isn'?t breathing|stopped breathing|unconscious|unresponsive|${B}cpr${E}|tir(?:amos|ou|aram|ei)\w*[^.?!]{0,30}(?:da|do) (?:[áa]gua|mar)|n[ãa]o (?:est[áa] )?respira|parou de respirar|desacordad|inconsciente|sac(?:amos|aron|[óo]|aste)\w*[^.?!]{0,30}del (?:agua|mar)|no respira|dej[óo] de respirar|${B}rcp${E})`;
const SCORP = String.raw`${B}(?:scorpions?${E}|escorpi[ãa]o|escorpi[óo]n|alacr[áa]n)`;
const NOT_SCORP = String.raw`(?:scorpion ?(?:pose|fish)|yoga|zodiac|zod[íi]aco|horoscop|hor[óo]scopo|astrolog|ascendente|mortal kombat|signo|${B}sou de escorpi|soy (?:de )?escorpi|pez escorpi|peixe.escorpi)`;
const CHILD_W = String.raw`${B}(?:child|children|kids?|son|daughter|baby|toddler|boy|girl|grandson|granddaughter|nephew|niece|[0-9]{1,2}.?(?:year|yr).?old|crian[çc]as?|filh[oa]s?|menin[oa]s?|beb[êeé]s?|net[oa]s?|sobrinh[oa]s?|ni[ñn][oa]s?|hij[oa]s?|nene|nieto|nieta|chamac\w*|peque[ñn][oa]|[0-9]{1,2} (?:anos|años))${E}`;
const STING = String.raw`(?:sting|stung|${B}bit(?:e|ten)?${E}|picad|picou|pica${E}|me pic|le pic|pic[óo]|ferroad|ferroou|ferr[ãa]o|aguij)`;
const DROWN_CORE = String.raw`${B}(drown|(?:pulled|taken|dragged|fished|got|rescued|pulled him|pulled her)[^.?!]{0,30}(?:out of|from) (?:the )?(?:water|pool|sea|lake|river|ocean|pond|surf)|afog|(?:tir|pux|resgat)\w*[^.?!]{0,30}(?:da|do) (?:piscina|mar|[áa]gua|lago|rio|represa|lagoa)|ahogad|se ahog\w*[^.?!]{0,30}(?:agua|piscina|alberca|mar|lago|r[íi]o)|(?:sac|rescat)\w*[^.?!]{0,30}(?:del|de la) (?:agua|piscina|alberca|mar|lago|r[íi]o))`;
const PROPOSED = {
  // + lake/river/pond/ocean, rescued/taken/dragged, "not breathing" after any water; PT resgat/puxamos; ES ahogad/sacamos.
  drowning: new RegExp(u(`^(?:(?=.*${DROWN_CORE})(?:(?!.*${RIP})|(?=.*${POST}))|(?=.*${RIP})(?=.*${POST}))`), "isu"),
  jellyfish: new RegExp(u(`${B}(jellyfish|man.o.war|blue ?bottle|[áa]gua.viva|caravela|medusa|aguamala|agua mala|carabela)`), "iu"),
  seizure: new RegExp(u(`${B}(seizure|convuls|fit${E}|jerking|shaking on the (?:floor|ground)|se debatendo|ataque epil|crisis epil)`), "iu"),
  co: new RegExp(u(`${B}(carbon monoxide|${B}co${E} poison|mon[óo]xido de carbono|gas (?:heater|leak)|vazamento de g[áa]s|aquecedor a g[áa]s|generator (?:inside|indoors)|fuga de gas|calentador de gas|intoxicaci[óo]n por (?:mon[óo]xido|gas))`), "iu"),
  scorpion: new RegExp(u(`^(?!.*${NOT_SCORP})(?=.*${CHILD_W})(?=.*${SCORP})`), "isu"),
  beesting: new RegExp(u(`${B}((?:bee|wasp|hornet|yellow ?jacket)s?${E}.{0,40}${B}(?:sting|stung|stinger)|stung by an? (?:bee|wasp|hornet)|(?:abelha|vespa|marimbondo).{0,40}(?:picad|picou|ferr[ãa]o)|picad\\w* (?:por|de) (?:uma )?(?:abelha|vespa|marimbondo)|(?:abeja|avispa).{0,40}(?:pic|aguij)|picad\\w* (?:por|de) (?:una )?(?:abeja|avispa)|pic[óo][^.?!]{0,15}(?:una )?(?:abeja|avispa))`), "iu"),
  // battery card also covers coins (same card: button battery = emergency; coin = when to go).
  battery: new RegExp(u(`${B}(swallow\\w* (?:a |an )?(?:button |coin|watch |small )?(?:batter|coin)|engoli\\w* (?:uma )?(?:bateria|pilha|moeda)|(?:se )?trag[óo] (?:una )?(?:pila|bater[íi]a|moneda))`), "iu"),
  // choking: adult/child only; an infant mention goes to chokinginfant (else MULTI → generic card).
  choking: new RegExp(u(`^(?!.*${INFANT})(?=.*${B}(?:chok(?:e|ing)|can'?t (?:breathe|speak) .{0,20}(?:food|stuck)|engasg|atragant))`), "isu"),
  chokinginfant: new RegExp(u(`^(?=.*${INFANT})(?=.*${B}(?:chok(?:e|ing)|engasg|atragant|can'?t breathe))`), "isu"),
  hypoglycemia: new RegExp(u(`^(?!.*${B}(?:high blood sugar|hyperglyc|a[çc][úu]car alto|hiperglic|az[úu]car alta))(?:.*(?:low (?:blood )?sugar|hypoglyc|hipoglic|hipogluc|glicose baixa|glucosa baja|a[çc][úu]car (?:no sangue )?baixo|az[úu]car baja)|(?=.*(?:diabet|diab[ée]tic|insulin))(?=.*${B}(?:shak\\w*|sweat\\w*|confus\\w*|faint\\w*|dizzy|pale|drows\\w*|tremend\\w*|suad\\w*|suando|desmai\\w*|tont\\w*|tembl\\w*|sud(?:ando|ado|oroso|a)|mare\\w*|desmay\\w*|confund\\w*)))`), "isu"),
  // round 2 (2026-10-03)
  heartattack: new RegExp(u(`^(?!.*(?:saudade|cora[çc][ãa]o partido|heartbreak|broken heart|de amor|desamor|por (?:ela|ele|ti|vos)))(?=.*${B}(?:chest pain|pressure in (?:his|her|my) chest|heart attack|dor (?:forte )?no peito|aperto (?:forte )?no peito|infarto|ataque card|dolor (?:fuerte )?(?:en el|de) pecho|opresi[óo]n en el pecho|ataque al coraz[óo]n))`), "isu"),
  // v0.1 alternations restored (cold word BEFORE the symptom in PT/ES was dropped in round 2: vit-loc1-009 "molhado na neve, está tremendo muito e meio confuso"), + ES; a diabetes cue → not hypothermia (hypoglycemia card), as in Sonar's health6 bridge.
  hypothermia: new RegExp(u(`^(?!.*(?:diabet|diab[ée]tic|insulin|a[çc][úu]car|glicose|glucosa|hypoglyc|hipoglic|hipogluc|fever|febre|fiebre|sunburn|torrei|torrou|queimad\\w* (?:de|do|pelo) sol|quemad\\w* (?:de|del|por el) sol|insola|golpe de calor|heat ?stroke))(?=.*${B}(?:hypotherm|hipoterm|(?:shiver\\w*|slurr\\w+|stumbl\\w+|confused)[^.?!]{0,80}${B}(?:cold|snow|winter|freezing|icy|lake)${E}|${B}(?:cold|snow|winter|freezing|icy)${E}[^.?!]{0,80}(?:shiver\\w*|slurr\\w+|stumbl\\w+|confus\\w+)|(?:tremend\\w*|fala enrolada|enrolando a fala|confus\\w+|tiritand\\w*|temblando|arrastrando las palabras)[^.?!]{0,80}${B}(?:frio|frío|neve|nieve|inverno|invierno|gelad\\w*|helad\\w*)${E}|${B}(?:frio|frío|neve|nieve|inverno|invierno|gelad\\w*|helad\\w*)${E}[^.?!]{0,80}(?:tremend\\w*|enrolad\\w*|confus\\w+|tiritand\\w*|temblando|confundid\\w*)))`), "isu"),
  headinjury: new RegExp(u(`${B}(head injur|hit (?:his|her|my|their) head|bumped (?:his|her|my|their) head|fell on (?:his|her|my|their) head|concussion|bateu a cabe[çc]a|batida na cabe[çc]a|pancada na cabe[çc]a|caiu de cabe[çc]a|traumatismo craniano|concuss[ãa]o|golpe(?:ó)? (?:en|la) (?:la )?cabeza|se golpe[óo] la cabeza|traumatismo craneal|conmoci[óo]n cerebral)`), "iu"),
  // coastal earthquake → tsunami card (Sonar's earthquake card keeps inland quakes; this needs earthquake + coast/sea/beach words)
  tsunami: new RegExp(u(`${B}(tsunami|maremoto|(?:earthquake|quake|terremoto|sismo|temblor|tremor de terra)[^.?!]{0,120}${B}(?:beach|coast|shore|sea|ocean|praia|litoral|mar|playa|costa|orilla)${E}|${B}(?:beach|coast|shore|praia|litoral|playa|costa)${E}[^.?!]{0,120}(?:earthquake|quake|terremoto|sismo|temblor))`), "iu"),
  // round 3 (2026-10-03): Sonar's EN/PT terms (instcards v0.1/v1) + ES; earthquake = inland only (coastal → tsunami).
  burn: new RegExp(u(`${B}(burn(?:ed|t)? (?:my|his|her|their)|scald|boiling (?:water|oil)|hot (?:pan|oil|water|stove)|queim(?:ei|ou|ado|ada|adura)|[áa]gua fervendo|[óo]leo quente|panela quente|quem(?:é|ó|ado|ada|adura)|agua hirviendo|aceite caliente|escald\\w*)`), "iu"),
  frostbite: new RegExp(u(`${B}(frostbit|frostnip|frozen (?:toes|fingers|feet)|congelad|queimadura de frio|congelaci[óo]n|dedos congelados)`), "iu"),
  heatstroke: new RegExp(u(`${B}(heat ?stroke|stopped sweating|collapsed in the heat|very hot and confused|insola[çc][ãa]o|golpe de calor|desmai\\w* no calor|desabou no calor|dej[óo] de sudar|se desmay[óo] por el calor|(?:no|ya no) (?:est[áa] )?sud(?:a|ando)|n[ãa]o (?:est[áa] )?su(?:a|ando)|parou de suar)`), "iu"),
  nosebleed: new RegExp(u(`${B}(nose ?bleed|nose (?:is |started )?bleeding|bleeding from (?:my|the|his|her) nose|sangr\\w*[^.?!]{0,15}nariz|nariz[^.?!]{0,20}sangr|hemorragia nasal|epistaxis)`), "iu"),
  earthquake: new RegExp(u(`^(?!.*${B}(?:beach|coast|shore|sea|ocean|praia|litoral|mar|playa|costa|orilla|tsunami|maremoto)${E})(?=.*${B}(?:earthquake|ground is shaking|building is shaking|terremoto|tremor de terra|pr[ée]dio (?:est[áa] )?tremendo|sismo|temblor|est[áa] temblando el edificio))`), "isu"),
  animalbite: new RegExp(u(`${B}((?:dog|cat|monkey|bat|fox|raccoon|macaque)s?${E}.{0,30}${B}(?:bit|bite|bitten|scratch\\w*)|bitten by an? (?:dog|cat|monkey|bat|fox|raccoon)|rabies|(?:raiva|rabia)(?=[^.?!]{0,40}(?:cachorro|c[ãa]o|gato|macaco|morcego|perro|mono|murci[ée]lago|animal|mordid|mordedura|vacina|vacuna|soro|suero))|(?:cachorro|c[ãa]o|gato|macaco|morcego|perro|mono|murci[ée]lago|animal|mordid|mordedura|vacina|vacuna)[^.?!]{0,40}(?:raiva|rabia)|(?:cachorro|c[ãa]o|gato|macaco|morcego).{0,30}(?:mordeu|mordid|arranh)|mordid\\w* (?:por|de) (?:um )?(?:cachorro|c[ãa]o|gato|macaco|morcego)|(?:perro|gato|mono|murci[ée]lago|zorro).{0,30}(?:mordi|mordid|ara[ñn])|mordedura de (?:perro|gato|mono|murci[ée]lago)|me mordi[óo] un (?:perro|gato|mono|murci[ée]lago))`), "iu"),
  // round 4 (2026-10-03): Sonar's EN/PT terms (instcards v0.1/v1) + ES.
  bleeding: new RegExp(u(`${B}(bleeding (?:a lot|heavily|badly)|won'?t stop bleeding|blood (?:is )?(?:pouring|spurting)|sangrando muito|sangramento forte|n[ãa]o para de sangrar|sangra mucho|no (?:deja|para) de sangrar|sangrado (?:abundante|fuerte)|hemorragia(?! nasal))`), "iu"),
  stroke: new RegExp(u(`(?<!heat ?)${B}(face (?:is |was )?droop\\w*|crooked (?:smile|face)|stroke(?![\\p{L}\\p{N}_])|one side (?:of (?:his|her) body )?(?:is )?weak|boca torta|rosto (?:ca[íi]do|torto)|derrame|${B}avc${E}|${B}ictus${E}|cara ca[íi]da|accidente cerebrovascular|habla arrastrada)`), "iu"),
  anaphylaxis: new RegExp(u(`${B}(anaphyla|anafila|throat (?:is )?(?:swelling|closing)|lips? (?:are |is )?swelling|garganta (?:inchando|fechando)|choque anafil|se le (?:cierra|hincha) la garganta|reacci[óo]n al[ée]rgica grave|shock anafil)`), "iu"),
  eyechem: new RegExp(u(`${B}((?:bleach|chemical|cleaning product|acid|alkali)\\w* .{0,25}(?:eye)|(?:eye).{0,25}(?:bleach|chemical)|(?:sanit[áa]ria|produto qu[íi]mico|produto de limpeza).{0,25}olho|(?:lej[íi]a|producto qu[íi]mico|cloro).{0,25}ojo|ojo.{0,25}(?:lej[íi]a|qu[íi]mico))`), "iu"),
  electric: new RegExp(u(`${B}(electric(?:al)? shock|electrocut|live (?:wire|cable)|got a shock|tomou choque|levou um choque|fio desencapado|descarga el[ée]ctrica|electrocutad\\w*|cable pelado|le dio la corriente)`), "iu"),
  altitude: new RegExp(u(`${B}(altitude sickness|mountain sickness|high altitude|soroche|mal de altitude|doen[çc]a da altitude|mal da montanha|mal de altura|mal de monta[ñn]a|apunamiento)`), "iu"),
  marinepuncture: new RegExp(u(`${B}(sting ?ray|sea urchin|stonefish|weever|lionfish|arraia|ouri[çc]o.do.mar|peixe.pedra|mantarraya|pastinaca|erizo de mar|pez piedra|pez ara[ñn]a|pez escorpi[óo]n|raya me pic)`), "iu"),
  tick: new RegExp(u(`${B}(ticks?${E}|carrapato|garrapata)`), "iu"),
  heatexhaustion: new RegExp(u(`${B}(heat exhaustion|heat cramps?|cramps?.{0,40}${B}(?:heat|sun|hot)${E}|exaust[ãa]o (?:pelo|por) calor|c[ãa]imbras?.{0,40}${B}(?:sol|calor)${E}|agotamiento por calor|calambres?.{0,40}${B}(?:sol|calor)${E})`), "iu"),
  // round 5 (Compass)
  ripcurrent: new RegExp(u(`^(?!.*${POST})(?=.*${RIP})`), "isu"),
  scorpionadult: new RegExp(u(`^(?!.*${NOT_SCORP})(?!.*${CHILD_W})(?=.*${SCORP})(?=.*${STING})`), "isu"),
};
export const CONDITION_TERMS: Record<string, RegExp> = { ...BASE_TERMS, ...PROPOSED };

/** terms.py regex_condition: the one matching condition, "MULTI" for several, null for none. */
const ALTITUDE_ACRONYM = /(?<![\p{L}\p{N}_])(AMS|HACE|HAPE)(?![\p{L}\p{N}_])/u; // case-sensitive (health13)
/** "at 3,400 m" / "a 3.400 m": an altitude only from 2,000 m, and only as a CURRENT-turn cue (a trip turn in the history
 *  is context, not a condition; "a 100 metros da praia" is a distance). health13. */
const ALTITUDE_METERS = /(?<![\p{L}\p{N}_])(?:at|a|em|en) (?:[2-9][.,]?[0-9]{3}|[1-9][0-9][.,]?[0-9]{3}) ?m(?:eters|etres|etros)?(?![\p{L}\p{N}_])/iu;
export function regexCondition(query: string, contextCues = true): string | null {
  let hits = Object.keys(CONDITION_TERMS).filter((c) => CONDITION_TERMS[c].test(query));
  if (!hits.includes("altitude") && (ALTITUDE_ACRONYM.test(query) || (contextCues && ALTITUDE_METERS.test(query)))) hits.push("altitude");
  // A quake at the coast is a tsunami question: the tsunami card, not the inland earthquake card (Compass round 2).
  if (hits.includes("tsunami")) hits = hits.filter((c) => c !== "earthquake");
  // CO exposure (EN): a fuel device in an enclosed space plus symptoms or an alarm is the CO card, even without the words
  // "carbon monoxide". Its symptoms (chest pain, confusion in the cold) are CO's, so they don't make it MULTI. The current
  // turn only (contextCues): joined-history matches gave wrong cards in health13 (Sonar).
  if (contextCues && isCoExposure(query)) hits = [...new Set([...hits.filter((c) => c !== "heartattack" && c !== "hypothermia"), "co"])];
  return hits.length === 1 ? hits[0] : hits.length ? "MULTI" : null;
}

/**
 * The condition for a conversation: the current question's, else (it names none) the user's turns together, oldest
 * first ("my son was stung by a scorpion" … "what do I do now?"). A condition named in the current question wins.
 */
/**
 * Card-content guards (health13, Compass card-mismatch check): the drowning card is post-rescue CPR ("pulled from the
 * water and not breathing normally") — never while the person is still in the water (rip current, being swept out);
 * the scorpion card is child-only ("Scorpion sting in a child…") — never for an adult. A guarded condition → generic line.
 */
const OUT_OF_WATER = /(?<![\p{L}\p{N}_])(pulled|took|taken|got|dragged|fished|rescued|carried) (?:him|her|them|it|my \w+|the \w+ )?\s?(?:out|from)|out of the (?:water|pool|sea|lake|river|ocean)|(?:tir|sac|pux|resgat)\w*[^.?!]{0,30}(?:da|do|del|de la) (?:piscina|mar|[áa]gua|lago|rio|r[íi]o|praia|playa)|n[ãa]o (?:est[áa] )?respira|not breathing|no respira|(?:on|na|en) (?:the )?(?:beach|sand|areia|arena|playa|praia)/iu;
const STILL_IN_WATER = /(?<![\p{L}\p{N}_])(rip ?(?:current|tide)|riptide|being (?:pulled|swept|dragged|carried)|swept (?:out|away)|still in the water|can'?t get back|corrente(?:za)? (?:de retorno|est[áa] puxando)|correnteza|repuxo|ainda (?:est[áa] )?na [áa]gua|corriente|resaca|todav[íi]a (?:est[áa] )?en el agua|mar adentro|arrastrando|puxando (?:ele|ela|pra fora))/iu;
const CHILD = /(?<![\p{L}\p{N}_])(child|children|kid|kids|son|daughter|baby|toddler|boy|girl|[0-9]{1,2}.?(?:year|yr)s?.?old|crian[çc]a|filh[oa]|beb[êeé]|menin[oa]|nen[êe]m|ni[ñn][oa]|hij[oa]|nen[ea]|chamac[oa]|guagua|peque[ñn][oa]|[0-9]{1,2} anos|[0-9]{1,2} a[ñn]os)/iu;
// Compass v0.8.8 wrong-card review (dev): the choking card's abdominal thrusts are wrong in pregnancy; the burn card is about
// cooling a burn, not a building fire or smoke; the CO card is not natural-gas-leak conduct (round-5 cards pending).
const PREGNANT = /(?<![\p{L}\p{N}_])(pregnant|gr[áa]vida|gestante|embarazada|encinta|weeks? pregnant|semanas de (?:gesta|embara))/iu;
const FIRE_SMOKE = /(?<![\p{L}\p{N}_])(smoke|fumo|fuma[çc]a|humo|on fire|house fire|building fire|pegou fogo|pegando fogo|inc[êe]ndio|incendio|fuego|cheira a (?:pl[áa]stico )?queimado|huele a (?:pl[áa]stico )?quemado|smells? (?:of|like) burning)/iu;
const OWN_BURN = /(?<![\p{L}\p{N}_])(burn(?:ed|t)? (?:my|his|her|their)|queimei|queimou (?:a|o|meu|minha)|me quem[ée]|se quem[óo]|scald|bolha|blister|ampolla)/iu;
const GAS_SMELL = /(?<![\p{L}\p{N}_])(gas leak|smell(?:s|ing)? (?:of )?gas|vazamento de g[áa]s|cheiro (?:forte )?de g[áa]s|fuga de gas|olor a gas|huele a gas)/iu;
const CO_CUE = /(?<![\p{L}\p{N}_])(carbon monoxide|mon[óo]xido|co alarm|alarme de co|detector de co|heater|aquecedor|calentador|generator|gerador|generador|brasero|braseiro|charcoal|carv[ãa]o|carb[óo]n)/iu;
function guarded(c: string | null, text: string): string | null {
  if (c === "drowning" && STILL_IN_WATER.test(text) && !OUT_OF_WATER.test(text)) return null;
  if ((c === "choking" || c === "chokinginfant") && PREGNANT.test(text)) return null;
  if (c === "burn" && FIRE_SMOKE.test(text) && !OWN_BURN.test(text)) return null;
  if (c === "co" && GAS_SMELL.test(text) && !CO_CUE.test(text)) return null;
  return c;
}

export function conversationCondition(query: string, userTurns: string[] = []): { condition: string | null; fromHistory: boolean } {
  const all = [...userTurns, query].join(" / ");
  const now = regexCondition(query);
  if (now !== null || !userTurns.length) return { condition: now === "MULTI" ? now : guarded(now, all), fromHistory: false };
  // CO across two turns (Sonar exp-37): the device or a CO alarm in the immediately previous user turn, the symptoms now,
  // and no other condition in the current turn.
  if (isCoExposureAcrossTurns(userTurns[userTurns.length - 1], query)) return { condition: guarded("co", all), fromHistory: true };
  // health13 (blind hand read: wrong cards from history): a card from history only when the NEWEST earlier user turn that
  // names a condition is itself a health turn naming exactly that one condition. Never the turns joined together (a trip
  // turn's "a 3.000 m" + "my dad has chest pain now" gave the altitude card). Otherwise the generic emergency line.
  // The newest earlier turn that names a condition by its own words (not a context cue like an altitude in metres) is the
  // health turn about it. Only if no single turn names one: the user turns together, still without context cues
  // ("sou diabético" // "glicemia 48, tremendo"; "cloro na piscina" // "voou no olho"). A coast named in another turn
  // turns an earthquake into the tsunami card.
  let one: string | null = null;
  for (let i = userTurns.length - 1; i >= 0 && one === null; i--) one = regexCondition(userTurns[i], false);
  const joined = regexCondition(all, false);
  const c = one === null ? joined : one === "earthquake" && joined === "tsunami" ? "tsunami" : one;
  return { condition: c !== null && c !== "MULTI" ? guarded(c, all) : null, fromHistory: true };
}

/** trigger.v0.1 step 2: the regex's unique condition; MULTI or none → null (the generic card). */
export function pickCondition(query: string): string | null {
  const r = regexCondition(query);
  return r === "MULTI" ? null : r;
}

type CardText = { en: string; pt: string; es?: string; sources: string; status?: string };
export type CardLang = "en" | "pt" | "es";
const ES_EMERGENCY = "En una emergencia, llama al número de emergencias local (911 en gran parte de América, 112 en Europa).";
const CARDS = (cards as { cards: Record<string, CardText> }).cards;

/** The condition's card text in the question's language (the emergency line appended, as for the snakebite card); null
 * for an unknown condition or a language the card has no text for (the caller shows the generic card). */
export function conditionCardText(condition: string, query: string, lang: CardLang | boolean): string | null {
  const l: CardLang = typeof lang === "boolean" ? (lang ? "pt" : "en") : lang;
  // health13 (reviewer): the ES WHO card (health12) on the health route too, not only on the early WHO path.
  if (condition === "snakebite") return snakebiteFirstAidCard(query, l === "pt", l === "es");
  const c = CARDS[condition];
  const body = c?.[l];
  if (!c || !body) return null;
  const emergency = l === "es" ? ES_EMERGENCY : emergencyNumbersLine(query, l === "pt") ?? emergencyLine(l === "pt");
  return `${body}\n\n${l === "pt" ? "Fonte" : l === "es" ? "Fuente" : "Source"}: ${c.sources}.\n\n${emergency}`;
}

/**
 * A question the lexical health detector misses, but that names exactly one first-aid condition, asks what to do, and
 * describes a symptom ("I'm at 4,000 m in the Andes with a headache and nausea. What should I do?"; Sonar exp-29). The
 * symptom keeps out "How much longer should I boil pasta at high altitude?"; SHOULD_INTENT's planning exclusion keeps out
 * "earthquakes in Wellington? Should I worry?".
 */
const SYMPTOM = /(^|[^\p{L}])(headache|nause\p{L}*|vomit\p{L}*|dizz\p{L}*|faint\p{L}*|pain|hurts?|ache|swell\p{L}*|rash|fever|short of breath|can'?t breathe|breathless|confus\p{L}*|drows\p{L}*|sweat\p{L}*|shiver\p{L}*|numb|bleed\p{L}*|unconscious|sick|pale|dor|d[óo]i|n[áa]usea|enjoo|enjoad\p{L}*|v[ôo]mit\p{L}*|vomitando|tontura|tont\p{L}*|desmai\p{L}*|inchad\p{L}*|incha\p{L}*|febre|falta de ar|confus\p{L}*|sonolent\p{L}*|suand\p{L}*|suar|suor|tremend\p{L}*|sangr\p{L}*|p[áa]lid\p{L}*|dolor|duele|mareo|mareado|v[óo]mito\p{L}*|vomitando|fiebre|hinchad\p{L}*|sudor|sudando|temblando|desmay\p{L}*|p[áa]lido)(?![\p{L}])/iu;
export function isCardEmergency(query: string): boolean {
  const c = regexCondition(query);
  return !!c && c !== "MULTI" && (ACTION_INTENT.test(query) || SHOULD_INTENT.test(query)) && SYMPTOM.test(query);
}
