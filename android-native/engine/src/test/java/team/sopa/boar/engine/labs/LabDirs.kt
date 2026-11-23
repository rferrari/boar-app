package team.sopa.boar.engine.labs

import java.io.File

/** A lab's folder under app/src/labs/plugins by its id: the folder may differ from the id (designer holds id=image). */
object LabDirs {
    private val root = File("../app/src/labs/plugins")
    private val byId: Map<String, File> by lazy {
        root.listFiles().orEmpty().filter { File(it, "plugin.properties").isFile }.associateBy(::idOf)
    }

    /** The id in [dir]'s plugin.properties. */
    fun idOf(dir: File): String = File(dir, "plugin.properties").readLines().first { it.startsWith("id=") }.removePrefix("id=").trim()

    fun of(id: String): File = byId[id] ?: File(root, id)
    fun path(id: String): String = of(id).path
}
