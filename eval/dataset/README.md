# Eval dataset

TL;DR: research questions used to compare BOAR against frontier models with web search. One JSONL line per question, versioned by filename.

## Files

- `questions.v1.jsonl` — current version. A change to any item ships as a new version (`questions.v2.jsonl`); existing files are never edited in place.

## Item schema

| Field | Type | Notes |
|---|---|---|
| `id` | string | Stable, `<category-prefix>-<nnn>` (e.g. `cmp-012`). Never reused. |
| `category` | enum | `explain` \| `compare` \| `synthesis` \| `multistep` \| `traveler-firstaid` \| `traveler-geo` \| `traveler-lang` \| `hard-for-1b` |
| `query` | string | The question as the user would type it. |
| `lang` | string | BCP 47 (`en`, `pt-BR`). |
| `gold` | array | Optional. `[{ source: "enwiki" \| "enwikivoyage", title, page_id?, revid? }]`. Empty when no single article answers the question. |
| `license` | string | License of the question's source (e.g. `CC BY-SA 4.0`), or `original` when written from scratch. |
| `source_url` | string | Where the question or its facts came from. |
| `notes` | string | Optional. |

## Gold titles (shared with `eval/retrieval/`)

Gold titles are resolved against the knowledge pack built by the knowledge workstream:

- Source: FineWiki EN (`HuggingFaceFW/finewiki`, HF revision `8bd13e72e6a0`), from the August 2025 Enterprise HTML dumps, without redirect or disambiguation pages. Wikivoyage: EN only (`enwikivoyage-latest`).
- Title form: canonical Wikipedia title, spaces instead of underscores, first letter uppercase as in MediaWiki, rest case-sensitive. The title shown in the en.wikipedia URL (with `_` replaced by spaces) is acceptable; the resolver also follows redirects and matches case-insensitively.
- Matching is by title, and by `page_id` when present. `revid` is optional because FineWiki does not carry a reliable one.

## Exclusions

- No question may target the corpus bundled with the app (`assets/corpus/corpus.json`) or reuse a title from the in-app `EVAL_SET` (`src/eval/evalSet.ts`); those titles were picked to match the old eval set.

## v2: "Vitalik style" (`questions.v2.jsonl`, built by `build-v2.mjs`)

TL;DR: the literal test from Vitalik's post (2026-09-26) plus his domain. 40 items, separate from v1.

| Category | n | Gold |
|---|---|---|
| `local-food` | 17 | OpenStreetMap snapshot (Overpass, `diet:vegan`/`diet:vegetarian` = only/yes, amenity restaurant/cafe/fast_food) in `gold/v2/<id>.json`, plus Wikivoyage "Eat" listings mentioning vegan/vegetarian |
| `local-food-located` | 3 | Same, within 3 km of `context.lat/lon`; the query does not name the city ("the city I am currently in", "near me") |
| `crypto-expert` | 10 | Author notes + primary sources (NIST FIPS 204/205, EIPs, ethereum.org). Includes the exact prompt "Which signature algorithms are quantum resistant?" |
| `travel-practical` | 10 | Wikivoyage country/city article + author notes |

Food grading is objective (`scripts/lib/venues.mjs`): count distinct venues named in the answer that exist in the OSM gold for that city and diet; an item passes with ≥ 3. A venue missing from OSM is unverified, not necessarily wrong. Large cities keep "Eat" in district articles on Wikivoyage, so the Wikivoyage lists are sparse; OSM is the primary gold. `node dataset/build-v2.mjs --skip-fetch` rebuilds the JSONL from the committed gold files; `--refetch <ids>` / `--voyage-only <ids>` refresh snapshots (set `OVERPASS_URL` to use a mirror when the main instance rate-limits).

## cryptopack: v1.1 "20 crypto questions" (`questions.cryptopack.jsonl`)

TL;DR: answer quality of the default model with the Ethereum and cryptography pack installed (`boar-crypto.sqlite`, HF `r4topunk/boar-packs@b309ba9`). 20 items copied verbatim (id, category, query, gold) from the knowledge workstream's retrieval set (`source_url` names the branch and commit), so retrieval recall and answer quality use the same questions. `crypto-named-001` is the exact prompt from Vitalik's post.

- `provenance` keeps the original authoring note; `notes` is empty on purpose: the judge sees no author key points, only the two blinded answers (the reference is the key).
- `gold` sources include `eips` and `bips`; the runner's KB-hit column only checks `enwiki` titles.

## safety: fixed first-aid regression (`questions.safety.jsonl`)

TL;DR: the 5 danger items of v2 (`from` keeps the v2 id; notes from CDC/NIOSH, NHS and Ready.gov) plus "How do I stop a nosebleed?" in EN and PT (NHS, https://www.nhs.uk/conditions/nosebleed/), added after Prism's device finding E-1 (2026-09-26). Checked by `scripts/lib/firstaid-check.mjs` through `npm --prefix eval run regress`; any wrong first-aid instruction is a release blocker.

## PT-BR versions (Prism PT-1): `questions.v2-pt.jsonl`, `questions.safety-pt.jsonl`

TL;DR: every v2 item and the safety items in natural PT-BR (`pt-translations.v1.json`, written by hand, not machine-translated word for word), ids `<source>-pt`, same gold and notes as the source, `from` = source id. Rebuild with `node eval/dataset/build-pt.mjs`. The empty-chat suggestions already carry PT in the app's i18n. The first-aid checker applies the source item's rules to `-pt` ids.
