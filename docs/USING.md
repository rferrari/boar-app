# Using BOAR

TL;DR: import your own documents, add models from Hugging Face, and recover from a bad model load or reset the app.

## Custom knowledge base


Settings > Knowledge Base has an "Import" card alongside the built-in
downloadable corpus packs. It lets you index your own notes into the same
local FTS5 + vector search used everywhere else in the app:

- **Supported formats:** `.txt`, `.md`, `.csv` (naive comma-split, no quoted-field
  escaping), `.json` (either the app's own `{title, source, body}[]` corpus-pack
  shape, or any other JSON — imported as raw text otherwise), and `.pdf`
  (embedded/selectable text only, via Apache PDFBox-Android on the phone, no
  network, no OCR — so a scanned/image-only
  PDF extracts to empty text, and password-protected PDFs are rejected with a
  clear error rather than attempted).
- Each import is chunked (~500 tokens, 50-token overlap, heuristic
  char-based split) and embedded on-device with the same embedding model used
  for the rest of the knowledge base, then saved as a named, toggleable
  collection — turn one off without deleting it, or delete it outright.
- **Export** re-serializes a collection as `{title, source, body}[]` JSON (the
  same shape as the bundled corpus packs) and hands it to the Android share
  sheet — send it over Bluetooth, Nearby Share, a file manager, whatever the
  recipient's device offers. This is deliberately *not* a raw `.sqlite`
  export: that would bake in this device's specific embedding vectors, which
  are meaningless (or the wrong dimension) on a phone running a different
  embedding model. A recipient re-embeds the JSON locally by importing it the
  same way.
- Everything happens on-device; nothing is uploaded anywhere.

## The ONLINE / OFFLINE pill

The pill next to BOAR in the chat header says whether the phone has working internet right now.

- **OFFLINE** (moon-blue outline): no working internet. This is BOAR's normal state: answers, sources and your documents all work.
- **ONLINE** (filled gold): the phone has working internet. BOAR still answers on the phone; the network is only used for downloads you start yourself (models, knowledge packs) and for searching Hugging Face for other models.

How it decides:

- The pill turns ONLINE only when Android has checked that the connection really reaches the internet ("validated"). After you turn Wi-Fi or mobile data on, that check usually takes a few seconds, more on a slow network, so the pill changes a moment after the Wi-Fi icon does.
- A connection without real internet stays OFFLINE: a hotel or airport login page, a hotspot with no data, a router with no uplink.
- Losing the network turns the pill OFFLINE at once, because Android reports a lost network immediately.

When the header is tight (large text), the pill shows only its dot, in the same colors.

## Finding more models


Settings > Tone & Model has a "Find more models" search box (below the
curated model list) that searches Hugging Face for other GGUF models —
`android-native/app/src/main/java/team/sopa/boar/models/HfBrowser.kt` calls Hugging Face's public API, this is the
app's only other network access besides the model-setup downloads, and only
happens when you explicitly search. Tapping a result's file adds it to the
model list above, where you download it through the normal flow (same
progress tracking and post-download size check as every built-in model).

This is deliberately separate from the curated catalog in
`android-native/app/src/main/assets/boar-data/catalog.json`: nobody has run these models to
confirm they fit typical phone RAM or work cleanly with BOAR's llama.cpp build, so check a
model's Hugging Face page yourself (size, license, quantization) before downloading. The
download is pinned to the commit that was listed and checked against Hugging Face's git-lfs
sha256 when it gives one; the result shows when there is no checksum.

## Recovering from a bad model load, or starting over


If a model fails to load (corrupted/truncated download, the file went
missing, etc.) the chat screen shows a readable diagnosis instead of a raw
error, with shortcuts to Settings or straight back into the setup wizard.

Settings > App also has:

- **Re-run Setup Wizard** — jump back into first-run setup any time to
  switch model tiers or re-download the defaults, without losing anything
  else.
- **Danger Zone > Clear All Data & Reset App** — a double-confirmed full
  wipe: deletes every downloaded model, the
  whole local knowledge base (bundled + downloaded corpus packs + your own
  imported collections), and all chat history/settings, then sends you back
  to the setup wizard.

