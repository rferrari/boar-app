# Using BOAR

How to use BOAR 2.0 (the native Android app): the tour, answer styles, Deep Research,
sources, Boar Labs, the PIN, your own documents, more models, and recovering from a
bad model load.

## The tour

Setup downloads the answer model, the search model and, when there is room, the
Wikipedia Vital Articles pack. While it downloads, a "While you wait" card opens a
short BOAR tour: themes, answer styles, Deep Research, sources, your library and
documents, models, the PIN, Boar Labs and adaptive routing. Each page says where the
feature lives. See it again any time in Settings → System Recovery & Danger Zone → See
the tour.

## How BOAR answers

Pick a style with the style button next to where you type, or in Settings → How BOAR
answers:

- **Thorough & Detailed**, the default: full explanations.
- **Succinct & Direct**: quick replies.
- **Personal Files**: answers only from your own files (notes, PDFs, spreadsheets), the
  exact fact first, quoted as the file writes it, then a few sentences. If your files don't
  hold the answer, it says "I couldn't find this in your Personal Files." instead of
  answering from memory. Deep Research, adaptive routing and Boar Labs tools step aside in
  this style; the safety cards still answer.

Under every answer from the library, BOAR lists the articles it used. Tap one to read
the passage. The receipt shows the model, the speed and the time.

## Deep Research

Deep Research is for big questions. Turn it on in Settings → How BOAR answers, or from
the style button in the chat. When it is on, the chat glows and the 🔬 lights up.

- BOAR breaks the question into at most 3 smaller questions, searches the library for
  each, then writes one answer. These steps run one after another on the same model, so
  it is slower than a normal reply.
- It works with any style. The style sets the tone: Deep Research with Succinct & Direct
  still gives a short answer.
- With Deep Research on, the quick-answer tools of Boar Labs (Calculator, Facts,
  Distance and others) step aside and the question goes to Deep Research. Only the safety
  tools (`safety-*`, for example Boar Cohazard) still answer at once.
- Small talk ("hi", "thanks") never starts a Deep Research.

## Ask BOAR instead

When a Boar Labs tool answers a question without the model, the answer shows "Ask BOAR
instead". Tap it to ask the same question again with the tools skipped, so the model
answers. It applies to that question only. A safety answer (Boar Cohazard) has no such
button.

## Boar Labs

Boar Labs is a set of experiments, in builds made with the experimental flag (the 2.0.0
releases are). Find it in Settings → Experimental → Boar Labs, and in the menu.

- Boar Labs starts on. If one lab misbehaves, switch it off there. If you can, open an
  issue on GitHub.
- On from the start, labelled beta: Calculator, Facts, Cohazard, Distance and Polyglot.
  They answer some questions at once, from fixed formulas and tables, without the model.
  Facts holds driving side, calling codes, mains frequency and emergency numbers for 195
  countries (table r9.1).
- Boar Vitals is live and on from the start.
- Off from the start, labelled alpha: Guard, Press (answers into a PDF), Designer (draws
  a picture from an answer), Big Boar, Nomad and Lunar. Boar Wisp is in review.
- Big Boar runs Qwen3.6 35B (12.3 GB) from storage. Download it in the app, or copy it
  over USB. It is slow.
- Labs that load a model (Designer, Big Boar, Wisp) use extra memory while they run.

How a lab is made, and the order in which BOAR asks tools, checks and models:
[PLUGINS.md](PLUGINS.md).

## Adaptive routing

Settings → How BOAR answers → Adaptive routing. It is off by default and experimental.
When on, BOAR picks which of your installed models answers each question. It helps only
when you have more than one model. It does not check answers against the sources.

## PIN lock

Settings → PIN lock. A PIN seals your chats: they can't be read until the PIN is
entered. It also blanks BOAR's screen in recent apps and screenshots. Imported documents
are not encrypted yet. Without the PIN, the sealed chats can't be opened: "Forgot the
PIN?" erases them and removes the PIN, and keeps models, packs and documents.

## Your own documents

Settings → Knowledge → Personal Files (BOAR Knowledge, the packs, is the section below it;
tap a section's title to fold it). Import your own files into the same local search the
app uses everywhere, and ask about them with the Personal Files style:

- **Formats:** `.txt`, `.md`, `.csv` (a plain comma split, no quoted fields), `.json`
  (a list of `{title, source, body}` documents, or any other JSON imported as text) and
  `.pdf` (selectable text only, read on the phone with PDFBox: no OCR, so a scanned PDF
  gives no text, and password-protected PDFs are refused).
- A `.md` file is cut at its `#` and `##` headings, one part per section (a section longer
  than a part is sliced as below). Files imported before this keep their old parts.
- Each other import is cut into chunks of about 500 tokens with a 50-token overlap, embedded on
  the phone with the search model, and saved as a named collection. Turn a collection
  off without deleting it, or delete it.
- **Export** writes a collection as `{title, source, body}` JSON and hands it to the
  Android share sheet. It is not a `.sqlite` file, because the stored vectors only match
  this phone's search model. The recipient imports the JSON and embeds it again.
- Import and search happen on the phone. Nothing is uploaded.

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

Settings → Reasoning Models → Open the model catalog lists the curated models
([MODELS.md](MODELS.md)). From the catalog, "Discover GGUF models" searches Hugging Face
for other GGUF models. `android-native/app/src/main/java/team/sopa/boar/models/HfBrowser.kt`
calls Hugging Face's public API only when you search. Tapping a result's file adds it to
the model list, where you download it like any catalog model.

Nobody has checked these models on a phone the way the curated catalog was checked. Look
at a model's Hugging Face page yourself (size, license, quantization) before
downloading. The download is pinned to the commit that was listed, and checked against
Hugging Face's SHA-256 when it gives one; the result says when there is no checksum.

## Recovering from a bad model load, or starting over

If a model fails to load (a damaged download, a missing file), the chat shows a readable
message instead of a raw error. If loading a model closed the app, BOAR doesn't load it
again on its own: the chat says so and offers to try again with less memory.

Settings → System Recovery & Danger Zone has:

- **Relaunch Setup Wizard**: back into first-run setup to change the model or the
  knowledge package, without losing anything else.
- **See the tour**: the BOAR tour again.
- **Purge All Data & Reset App**: clears all of BOAR's data on the phone (models, the
  knowledge base, your documents, chats and settings), the same as Android's "Clear
  storage", then setup starts again.

## The offline APK

For a phone that never goes online, not even once, there are offline APKs. They carry the
answer model, the search model, the Wikipedia Vital Articles pack and Boar Designer's drawing
model inside. First launch unpacks and checks them (it needs a few GB of free space for that),
with no download:

- `boar-v…-offline-arm64.apk` (about 2 GB), with Qwen3 1.7B: on the GitHub release and on
  [Hugging Face](https://huggingface.co/BOAR-App/boar-offline).
- `boar-v…-offline-qwen3-4b-arm64.apk` (about 3.4 GB), with Qwen3 4B, for 12 GB phones: on
  Hugging Face only, since GitHub takes files up to 2 GiB.

Each is signed with the release key, so it installs over the normal BOAR and the normal BOAR
over it. Other models and packs can still be added later from the Model Catalog or a Hugging
Face search, which needs internet.

### Build your own

To bundle the other supported answer model, build the offline APK from source. The script is on the
`feat/offline-bundle` branch:

```bash
BOAR_OFFLINE_MODEL=qwen3-4b-instruct-2507-q4km BOAR_VERSION_NAME=2.0.0-rc2 \
  scripts/build-offline-apk.sh release
```

It expects, and checks against the catalog's SHA-256: the model GGUF in
`build/offline-bundle/models/`, `assets/models/embedding.gguf`,
`build/knowledge-pack/wiki-vital5.sqlite` (`npm run pack:build`) and Designer's
`build/offline-bundle/labs/sdxs-512-tinySDdistilled_Q8_0.gguf`. `BOAR_OFFLINE_MODEL` takes
`qwen3-1.7b-q4km` (the default) or `qwen3-4b-instruct-2507-q4km`. A self-built APK is signed
with your own key (or debug-signed), so it can't install over an official BOAR: switching
means uninstalling, and that deletes the downloaded models.
