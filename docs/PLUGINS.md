# Boar Labs plugins

Boar Labs is the native app's room for experiments. Each experiment ("lab") is a **plugin**: one folder that the build
finds by itself. Adding a lab is adding a folder; removing it is deleting the folder. Nothing outside the folder changes.

Labs only exist in builds made with `EXPERIMENTAL_FEATURES=true` (the repo's `.env`, the environment, or
`-Pboar.experimentalFeatures=true`). The 2.0.0 releases are built that way. In those builds the master switch
**Settings → Experimental → Boar Labs** starts on, and each lab has its own **Enabled** switch on its page. A lab with
`onByDefault = true` starts enabled: Calculator, Facts, Cohazard, Distance, Polyglot (beta) and Boar Vitals (live).
The others start off. A build without the flag has none of this.

## The rules every lab follows
- Runs on the phone, offline. No network calls except downloading the lab's own model when the user asks (Big Boar,
  Designer). No uploads, no share buttons: anything that leaves the phone goes through Android's Save-as, where the
  user picks the place.
- Never touches the chat's model while it's answering (`AnswerRef.chatBusy`).
- Erases what it made when the user erases their data (see [Erase](#erase)).
- English and Portuguese strings for everything it shows.

## How a question flows

The order in `ChatViewModel.send` (`android-native/app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt`) and
`AnswerPath` (`engine/…/routing/AnswerPath.kt`):

1. **The tool slot.** Skipped for a recap or summary of this chat, and once after "Ask BOAR instead". Otherwise the
   enabled labs' `answerTool`s run in order: Cohazard first, then Polyglot, then the rest in grid order. The first
   that answers wins. With Deep Research on, only a safety answer (`safety-*`) is kept.
   - `ToolResult.Instant`: shown as the answer. No model loads, and the rest is skipped.
   - `ToolResult.Context`: the model reads it after the system prompt (on the default and adaptive paths).
2. **The path.** Small talk ("hi", "thanks") takes the plain pipeline, even with Deep Research or adaptive routing on.
   Then Deep Research if it is on; then a recap of the chat; then adaptive routing if it is on (and the active model
   runs on BOAR's own engine); else the default path (the plain pipeline: search, then the model).
3. **The check slot.** After the model, on the default and adaptive paths only, the enabled labs' `answerCheck`s
   (Boar Guard) may repair the answer. Never after an instant answer, Deep Research, a recap, "Ask BOAR instead", or a
   stopped, timed-out, failed or empty answer.

**Personal Files** (the answer style `personal`, `engine/…/answer/PersonalFiles.kt`) changes all three: in the tool slot
only a safety answer (`safety-*`) is kept, as under Deep Research; Deep Research and adaptive routing are off, so a
question takes the default path (a recap of the chat still works); and the check slot is skipped. Search covers only
the user's own collections (`RetrievalParams.ownOnly`: no library, no packs), and the parts it finds go to the model
whole while they fit the context. When nothing is found, the chat writes "I couldn't find this in your Personal Files."
without calling the model. Search stages (`PipelineStages`) still run; they only see the user's parts.

## A lab's folder

```
android-native/app/src/labs/plugins/<id>/
  plugin.properties                    required
  java/team/sopa/boar/labs/<id>/…      the lab's code (a LabPlugin object)
  res/values/strings_lab_<id>.xml      English strings
  res/values-pt/strings_lab_<id>.xml   Portuguese strings
  jniLibs/arm64-v8a/lib*.so            optional executables; gitignored, built by a script
```

The build (`app/build.gradle.kts`) scans `src/labs/plugins/*/plugin.properties`, adds each folder's `java/`, `res/`
and, in experimental builds, `jniLibs/` to the app, and writes the registry the app reads (`LabRegistry.ALL`, in
`build/generated/labsRegistry`). **Folders whose name starts with `_` are skipped.** Use them for templates or for a lab
you're parking.

### plugin.properties

```properties
# Boar Labs plugin (docs/PLUGINS.md)
id=pulse                                         # must equal the folder name
object=team.sopa.boar.labs.pulse.DevicePulseLab  # the LabPlugin object…
order=20                                         # grid position (lower first; ties by id)
```

Set **either** `object=` (a working lab) **or** `classified=<emoji>` (a teaser: a locked tile, nothing runs):

```properties
id=signboard
classified=📷
order=90
```

The build stops with a clear error if `id` doesn't match the folder, or if both or neither of `object`/`classified` are set.

A lab under review (`LabStatus.REVIEW`) that runs its own engine also sets `review=true`. Its `jniLibs` then stay out of every APK. Google Play Protect blocks an APK that bundles executables it doesn't trust, so only the engines of labs in use ship. Example: colibri.

### Strings

String keys follow `native.labs.<id>.<name>`, written in XML as `native_labs_<id>_<name>`. Every lab needs:

| Key | Shown |
|---|---|
| `native_labs_<id>_title` | Tile and page title |
| `native_labs_<id>_body` | One line under the title |
| `native_labs_<id>_review`, `native_labs_<id>_reviewShort` | Only for status `REVIEW` (see below) |

Put them in `res/values/strings_lab_<id>.xml` and `res/values-pt/strings_lab_<id>.xml`, with `formatted="false"`, and
escape apostrophes (`\'`). Read them in code with `t("native.labs.<id>.<name>")`.

## The LabPlugin interface

`android-native/app/src/labs/java/team/sopa/boar/labs/LabPlugin.kt`. Everything except `id`, `emoji` and `status` has a
do-nothing default (`onByDefault` is false), so a lab only overrides what it uses.

```kotlin
object MyLab : LabPlugin {
    override val id = "mylab"
    override val emoji = "🧪"
    override val status = LabStatus.BETA

    @Composable override fun Content() { /* the lab's page, under the shared header */ }
}
```

| Member | Slot | What it does |
|---|---|---|
| `id`, `emoji`, `status` | — | Identity; `id` is the route (`lab:<id>`), the string prefix, the switch key |
| `Content()` | **Page** | The lab's screen inside Boar Labs, under the shared header (title, status, Enabled switch) |
| `answerActions(a: AnswerRef)` | **🧩 under an answer** | Rows in the one 🧩 sheet under each finished answer. Return `LabAction(emoji, label, done, enabled, onClick)` |
| `AnswerExtra(a: AnswerRef)` | **Under an answer** | Something shown under a finished answer, above its footer (e.g. an illustration) |
| `HeaderBadge(go)` | **Chat header** | A small badge at the top of the chat; `go("lab:<id>")` opens the lab |
| `engine` | **Engine** | An `EngineProvider`: models this lab runs on its own engine (see [Engines](#engines)) |
| `answerTool` | **Before the model** | An `AnswerTool` (`plugins/AnswerTools.kt`): sees the question and the chat's earlier turns first and returns `ToolResult.Instant` (the answer itself, shown and saved with no model call) or `ToolResult.Context` (text the model reads after the system prompt), or null to pass (example: `plugins/calc`). The chat and the device evaluation use it, not Deep Research, except safety tools (`safety-*`, e.g. Cohazard); never for a recap of the chat or after "Ask BOAR instead". The receipt keeps the tool's name. One that throws is skipped |
| `answerCheck` | **After the model** | An `AnswerCheck` (`plugins/AnswerChecks.kt`): reads a model's finished answer with its question and returns a `Repair` (the text that replaces it, and a name for the receipt) or null to leave it (example: `plugins/guard`). Only the chat's default and adaptive paths, before the answer is saved; never a tool's instant answer, Deep Research, or a stopped, timed-out or failed answer. One that throws is skipped |
| `searchStage` | **After search** | A `SearchStage` (`plugins/PipelineStages.kt`): reorders or filters the passages for every answer (a reranker). It gets twice the usual number to choose from and returns at most `topK`; one that throws is skipped |
| `eraseUserData(context, scope)` | **Erase** | Delete what the lab made (see [Erase](#erase)) |

A slot is only filled while Labs is on and the lab is enabled (`LabRegistry.active`). With Labs off every slot is
empty, and the app behaves as if Labs didn't exist.

### What a lab gets about an answer: AnswerRef

`android-native/app/src/main/java/team/sopa/boar/plugins/AnswerRef.kt`: `id`, `sessionId`, `question`, `text` (as
shown, citations cleaned), `sources` (title to source), `chatBusy` (the model is answering: don't start a generation of
your own now), `model` and `tokPerSec` (from the answer's receipt). The app knows the slots, never the plugins.

### Settings a lab keeps

`LabPrefs` (SharedPreferences "labs"): `enabled(id)` for the lab's switch, and `string(key)` / `putString(key, value)`
for small settings. Keys are prefixed with the lab id by convention (`image.style`). `putString` also writes a line to
the debug log (`setting labs.<key>`); measurements that aren't settings should use a key the logger skips, or their own
file.

`LabContext.app` is the application context, for code that runs outside a screen (engines, workers).

## Status

| `LabStatus` | Tile | Use when |
|---|---|---|
| `LIVE` | green | It works well |
| `BETA` | cyan | It works, still rough. Starts on, unless the lab sets `onByDefault = false` (Designer, Press) |
| `ALPHA` | amber | Early; starts off |
| `REVIEW` | amber, with `⚠ reviewShort` under the title | It works but was **measured** short of usable (too slow, unreliable). The page opens with an amber `⚠ review` warning, then the lab works as normal. Example: colibri, "under 1 tok/s", which also sets `review=true`, so its engine is left out and its page says so. |
| `CLASSIFIED` | dim, locked, description struck through | A teaser; set with `classified=` in plugin.properties |

The grid shows live, then beta, alpha, under review and classified labs, in plugin.properties order within each.

## Executables

Some labs run a program of their own (Boar Designer's `sd-cli`, Big Boar's `bmoe-cli`). Android only lets an app run
files it extracted from its native library folder, so they ship as `lib<name>.so` in the lab's
`jniLibs/arm64-v8a/`, which is gitignored and built by a script:

| Script | Builds |
|---|---|
| `scripts/build-sd-cli.sh` | Boar Designer's `libsdcli.so`, `libsdcli_i8mm.so` |
| `scripts/build-lab-engines.sh` | `libbmoe_cli.so` (Big Boar), `libcolibri_olmoe.so` (Boar Wisp) |

Rules:
- **Link statically,** or at least never ship a library named like the app's own (`libllama.so`, `libggml*.so`,
  `libc++_shared.so`). Two files with the same name can't both be packaged. `build-lab-engines.sh` checks this with
  `llvm-readelf`.
- Run it as its own process with `labs/engines/EngineProcess.kt` (start, stdin, stdout lines and binary frames,
  stop). If it crashes or runs out of memory, only the lab goes down.
- Executables ship only in experimental builds, unless `-Pboar.labs.executables=false` leaves them out
  (`boar.labs.imageEngine=false` is the old name).

## Engines

A lab can make the chat answer with another engine. Implement `EngineProvider`
(`app/src/main/java/team/sopa/boar/plugins/EnginePlugins.kt`) and return it from `engine`:

```kotlin
object MyEngineLab : LabPlugin, EngineProvider {
    override val id = "myengine"
    override val emoji = "🦣"
    override val status = LabStatus.BETA
    override val engine: EngineProvider get() = this
    override val engineLabel = "My Engine"          // Models screen: "My Engine engine · streams from storage"
    override val engineEmoji get() = emoji
    override fun models() = listOf(MODEL)            // CatalogAsset with engine = "myengine", externalPath = …
    override suspend fun load(asset: CatalogAsset, params: EngineParams): TextModel? = …
}
```

- `models()` returns catalog entries with `engine = <id>` and, for a file copied over USB, `externalPath`. The app
  lists them in Models next to its own models. It never downloads or deletes them. Adaptive routing skips them.
- `load()` returns a `TextModel` (`engine/…/Engine.kt`): `formatChat`, `generate` (a flow of pieces, then one `Done`
  with the measurements), `cancel`, `close`. The app's own `LlamaSession` implements the same interface, so the chat,
  Stop, Deep Research, sources and the debug log work unchanged.
- Buffer the output without limit (`.buffer(Channel.UNLIMITED)`), so no piece of an answer is dropped.
- After Stop, drain what the stopped request still prints before starting the next one (see `BmoeModel.drain`).
- Override `isGenerating` so memory release never cuts an answer. BOAR frees a Labs engine's process on critical
  memory pressure or after 5 minutes idle in the background (`BoarServices.onTrimMemory`/`onBackground`), never on a
  plain app switch; the next answer loads it again.
- Examples: `plugins/bmoe` (BigMoeOnEdge, JSON lines) and `plugins/colibri` (a framed text protocol).

## Erase

`eraseUserData(context, scope)` runs for **every** lab, switched on or not (`UserData.erase`), when:

| Scope | When | Delete |
|---|---|---|
| `CHATS` | Settings → Clear All Chat History | What came from chats (illustrations, the Press basket) |
| `EVERYTHING` | Forgot the PIN → Erase | Everything the lab made for the user (Designer's gallery too) |

Delete files even if they can't be read, and reset any state the lab keeps in memory. Downloaded models stay.

## Building and trying a lab

```bash
cd android-native
./gradlew :app:assembleRelease -Pboar.experimentalFeatures=true   # add -Pboar.allowDebugLog=true for the debug log
adb install -r app/build/outputs/apk/release/app-release.apk
```

On the phone, check that Settings → Experimental → Boar Labs is on, then open the menu → Boar Labs ⚛️ → your lab.

To leave a lab out of one build without deleting it, use `-Pboar.labs.hide=<id>,<id>`. To park it, rename its folder to
start with `_`.

## Shared pieces
- `LabsScreen.kt`: the grid, the console, `LabScaffold` (each lab's header and Enabled switch), `StatusChip`.
- `LabHooks.kt`: what the app calls (menu entry, routes, Settings row, the slots, the engine and erase hooks).
- `MatrixRain.kt`: green falling code, for "the phone is working on it" (Boar Designer uses it while drawing).
- Framework strings: `src/labs/res/values{,-pt}/strings_labs.xml`.
