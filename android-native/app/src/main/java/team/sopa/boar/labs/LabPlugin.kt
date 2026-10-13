package team.sopa.boar.labs

import androidx.compose.runtime.Composable
import team.sopa.boar.BuildConfig
import team.sopa.boar.labs.image.ImageGenLab
import team.sopa.boar.labs.pulse.DevicePulseLab

/**
 * Boar Labs plugins. Each lab is one object in its own package (labs/<id>/) and one line in [LabRegistry.ALL]:
 * - add: write a [LabPlugin], add it to ALL, add native.labs.<id>.title / .body to strings_native.xml (en and pt);
 * - remove: delete its package and its line;
 * - hide without removing: boar.labs.hide=<id>,<id> (gradle.properties, -P, or BOAR_LABS_HIDE in .env);
 * - all of Labs: boar.labs=true|false.
 * A CLASSIFIED lab shows its tile locked and never opens: a teaser for what's in testing.
 */
enum class LabStatus { LIVE, BETA, CLASSIFIED }

interface LabPlugin {
    /** Stable id: the route (lab:<id>), the string keys (native.labs.<id>.*) and the hide flag. */
    val id: String
    /** Stands in for the tile's art until there is a picture. */
    val emoji: String
    val status: LabStatus

    /** The experiment, below the shared header (LabScaffold). Back and Done return to the grid. */
    @Composable
    fun Content() {}
}

/** A tile for something in testing: no screen. */
class ClassifiedLab(override val id: String, override val emoji: String) : LabPlugin {
    override val status = LabStatus.CLASSIFIED
}

object LabRegistry {
    /** Grid order, two per row. */
    val ALL: List<LabPlugin> = listOf(
        ImageGenLab,
        DevicePulseLab,
        ClassifiedLab("printer", "🖨️"),
        ClassifiedLab("truffleHunt", "🍄"),
        ClassifiedLab("bigBrain", "🧠"),
        ClassifiedLab("signboard", "📷"),
    )

    private val hidden: Set<String> = BuildConfig.LABS_HIDE.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val visible: List<LabPlugin> get() = ALL.filter { it.id !in hidden }

    fun byId(id: String): LabPlugin? = visible.firstOrNull { it.id == id && it.status != LabStatus.CLASSIFIED }
}
