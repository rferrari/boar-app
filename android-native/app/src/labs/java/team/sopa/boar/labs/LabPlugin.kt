package team.sopa.boar.labs

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import team.sopa.boar.BuildConfig
import team.sopa.boar.plugins.AnswerRef

/**
 * Boar Labs plugins (src/labs). Hidden until the user turns on Settings → Advanced tweaks → Boar Labs, at the very
 * bottom, which only builds with EXPERIMENTAL_FEATURES=true show (app/build.gradle.kts); off, no lab shows anywhere.
 *
 * Each lab is one folder, src/labs/plugins/<id>/ (plugin.properties, java/, res/), found by the build; see
 * docs/PLUGINS.md and the _template folder:
 * - add: copy _template, rename it; nothing outside the folder changes;
 * - remove: delete the folder;
 * - hide from a build: boar.labs.hide=<id>,<id> (gradle.properties or -P);
 * - on the phone: every lab has an Enabled switch on its page; a disabled lab fills no slot.
 *
 * Slots are where a plugin reaches into the app ([LabHooks]): an answer's 🧩 sheet and what shows under it, the chat header.
 * A CLASSIFIED lab shows its tile locked and never opens or fills a slot: a teaser for what's in testing.
 */
/**
 * REVIEW: works and stays open to try, but measured short of usable (too slow, unreliable). The tile shows
 * native.labs.<id>.reviewShort ("under 1 tok/s") and the lab's page opens with native.labs.<id>.review, the full reason.
 */
/** LIVE: simple and proven. BETA: tested on phones. ALPHA: works, less tested. REVIEW: frozen. CLASSIFIED: a teaser. */
enum class LabStatus { LIVE, BETA, ALPHA, REVIEW, CLASSIFIED }

/** The art a tile shows: a lab under review (frozen) shows the ice boar, whatever its own art. */
val LabPlugin.shownArt: Int? get() = if (status == LabStatus.REVIEW) team.sopa.boar.R.drawable.lab_frozen else art

interface LabPlugin {
    /** Stable id: the route (lab:<id>), the string keys (native.labs.<id>.*), the hide flag and the switch. */
    val id: String
    /** Stands in for the tile's art until there is a picture. */
    val emoji: String
    /** The tile's picture (a drawable), shown instead of [emoji] when set. */
    val art: Int? get() = null
    val status: LabStatus
    /** Switched on in a fresh install (or after Reset); every other lab starts off until the user turns it on. */
    val onByDefault: Boolean get() = false

    /** The experiment's page, below the shared header (LabScaffold). */
    @Composable fun Content() {}

    /** Rows in the 🧩 sheet a finished answer's footer opens: one button there however many labs add actions. */
    @Composable fun answerActions(a: AnswerRef): List<LabAction> = emptyList()

    /** Content under a finished answer, above its footer. */
    @Composable fun AnswerExtra(a: AnswerRef) {}

    /** A small badge in the chat header; [go] opens a route ("lab:<id>"). */
    @Composable fun HeaderBadge(go: (String) -> Unit) {}

    /** Rows on a model's card in the Model Catalog, under its memory badge (Big Boar's engine choice). */
    @Composable fun CatalogCardExtra(asset: team.sopa.boar.engine.models.CatalogAsset) {}

    /** The Hugging Face search offers its Mixture of experts filter while this lab is on. */
    val browseMoeFilter: Boolean get() = false

    /** A line under a Hugging Face result ([architecture] from its GGUF facts, when listed): a string key, or null. */
    fun browseHint(repoId: String, architecture: String?): String? = null

    /**
     * Erases what this lab made for the user (team.sopa.boar.plugins.UserData): from chats only ([UserData.Scope.CHATS]),
     * or everything. Runs even when the lab is switched off.
     */
    fun eraseUserData(context: Context, scope: team.sopa.boar.plugins.UserData.Scope) {}

    /** The search slot: a step after search for every answer (a reranker); see team.sopa.boar.plugins.SearchStage. */
    val searchStage: team.sopa.boar.plugins.SearchStage? get() = null

    /** The engine slot: models this lab runs on its own engine, listed with the app's (team.sopa.boar.plugins.EnginePlugins). */
    val engine: team.sopa.boar.plugins.EngineProvider? get() = null

    /** The tool slot: looks at the question before the model and answers it or adds context (team.sopa.boar.plugins.AnswerTools). */
    val answerTool: team.sopa.boar.plugins.AnswerTool? get() = null

    /** The check slot: reads a model's finished answer and may repair it (team.sopa.boar.plugins.AnswerChecks). */
    val answerCheck: team.sopa.boar.plugins.AnswerCheck? get() = null
}

/** One row in an answer's 🧩 sheet. [done]: already applied to this answer (checked); [enabled] false: in progress. */
class LabAction(val emoji: String, val label: String, val done: Boolean = false, val enabled: Boolean = true, val art: Int? = null, val onClick: () -> Unit)

/** A tile for something in testing: no screen, no slots. Its boar shows as a silhouette until it launches. */
class ClassifiedLab(override val id: String, override val emoji: String) : LabPlugin {
    override val status = LabStatus.CLASSIFIED
    override val art: Int? get() = CLASSIFIED_ART[id]

    companion object {
        /** Each classified lab's boar, shown dark like a locked truffle in the vault; a different one per tile. */
        private val CLASSIFIED_ART = mapOf(
            "truffleHunt" to team.sopa.boar.R.drawable.lab_truffle_hunt,
            "signboard" to team.sopa.boar.R.drawable.lab_signboard,
            "sanctuary" to team.sopa.boar.R.drawable.lab_sanctuary,
        )
    }
}

object LabRegistry {
    /** Every plugin folder under src/labs/plugins, in their plugin.properties order (generated by the build). */
    val ALL: List<LabPlugin> = GENERATED_LABS

    private val hidden: Set<String> = BuildConfig.LABS_HIDE.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** In the grid's order: live, then beta, then under review, then classified; plugin.properties order within each. */
    val visible: List<LabPlugin> get() = ALL.filter { it.id !in hidden }.sortedBy {
        when (it.status) { LabStatus.LIVE -> 0; LabStatus.BETA -> 1; LabStatus.ALPHA -> 2; LabStatus.REVIEW -> 3; LabStatus.CLASSIFIED -> 4 }
    }

    fun byId(id: String): LabPlugin? = visible.firstOrNull { it.id == id && it.status != LabStatus.CLASSIFIED }

    /** The labs that fill slots: built, not classified, and switched on. */
    val active: List<LabPlugin> get() = if (!LabPrefs.labsOn) emptyList() else visible.filter { it.status != LabStatus.CLASSIFIED && LabPrefs.enabled(it.id) }
}

/** Per-lab switches and small settings, in SharedPreferences "labs". The switches are Compose state. */
object LabPrefs {
    private var prefs: android.content.SharedPreferences? = null
    private val switches = mutableStateMapOf<String, Boolean>()
    private val on = androidx.compose.runtime.mutableStateOf(false)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("labs", Context.MODE_PRIVATE).also { prefs = it }
        // Boar Designer and Boar Press start on (LIVE, Oct 17); every other lab starts off until the user switches it on.
        LabRegistry.ALL.forEach { switches[it.id] = p.getBoolean("enabled.${it.id}", it.onByDefault) }
        on.value = p.getBoolean("labs.on", true)  // the Labs build opens with Boar Labs on
        restart.value = p.getBoolean("autoRestart", true)
    }

    /** The master switch at the bottom of Advanced tweaks: on by default in the Labs build; off, no lab shows anywhere. */
    var labsOn: Boolean
        get() = BuildConfig.EXPERIMENTAL_FEATURES && on.value
        set(v) { team.sopa.boar.app.DebugLog.settingChanged("labs.on", on.value, v); on.value = v; prefs?.edit()?.putBoolean("labs.on", v)?.apply() }

    /** Open BOAR again after it crashes (CrashRestart); on by default. */
    var autoRestart: Boolean
        get() = restart.value
        set(v) { team.sopa.boar.app.DebugLog.settingChanged("labs.autoRestart", restart.value, v); restart.value = v; prefs?.edit()?.putBoolean("autoRestart", v)?.apply() }
    private val restart = androidx.compose.runtime.mutableStateOf(true)

    fun enabled(id: String) = switches[id] ?: false

    /** Classified labs the user declassified with ten taps (the Labs easter egg); kept across launches. */
    private val reveals = mutableStateMapOf<String, Boolean>()
    // A leak lasts while Boar Labs is open: leaving it covers every tile again (Oct 19), and ten taps reveal it again.
    fun revealed(id: String) = reveals[id] ?: false
    fun setRevealed(id: String) { reveals[id] = true }
    fun coverAll() = reveals.clear()

    fun setEnabled(id: String, on: Boolean) {
        team.sopa.boar.app.DebugLog.settingChanged("labs.enabled.$id", enabled(id), on)
        switches[id] = on
        prefs?.edit()?.putBoolean("enabled.$id", on)?.apply()
    }

    fun string(key: String): String? = prefs?.getString(key, null)

    fun putString(key: String, value: String) {
        // Measurements the labs keep (image.times.*) are not settings.
        if (!key.startsWith("image.times.")) team.sopa.boar.app.DebugLog.settingChanged("labs.$key", string(key)?.take(80), value.take(80))
        prefs?.edit()?.putString(key, value)?.apply()
    }
}

/** The application context, for plugins that start processes or read files outside a screen (engines). */
object LabContext {
    @Volatile var app: Context? = null
}
