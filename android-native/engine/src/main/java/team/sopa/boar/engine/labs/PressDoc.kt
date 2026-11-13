package team.sopa.boar.engine.labs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Boar Press's projects (adam's mock-up, Nov 13): several projects, each its own document, made of blocks in order.
 * Pure data and the changes on it; the app keeps it in one file (files/labs/press/projects.json, written whole through an
 * AtomicFile) and draws it as the paper.
 *
 * Ready for the next phases without another migration:
 * - phase 2 (photos, captions, placement): [Block.Picture] already has [Block.Picture.kind] PHOTO, a [Block.Picture.caption]
 *   and a [Block.Picture.place] (center, left, right); a photo's [Block.Picture.file] is a name inside the project's own
 *   folder (files/labs/press/<project id>/), which goes with the project ([Library.deleteProject] returns its id).
 * - phase 3 (Ask BOAR to rewrite): a rewrite only replaces a block's text ([Library.editText]); nothing new is stored.
 * - anything newer: a block type this version doesn't know is kept as it is ([Block.Unknown]) and written back unchanged;
 *   [VERSION] lets a later version migrate if it ever must.
 */
object PressDoc {
    const val VERSION = 1
    /** How many projects the menu lists before "All projects". */
    const val SHORT_LIST = 4

    data class Source(val title: String, val source: String? = null)

    /** How a Designer drawing was made, read from its recipe; every field may be missing (older drawings). */
    data class Recipe(val subject: String? = null, val style: String? = null, val size: String? = null, val seed: Long? = null)

    enum class Kind { DRAWING, PHOTO }

    /** Where a picture sits: centered at the full width (phase 1), or left / right with the text beside it (phase 2). */
    enum class Place { CENTER, LEFT, RIGHT;
        companion object { fun of(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: CENTER }
    }

    sealed interface Block {
        val id: String

        data class Heading(override val id: String, val text: String) : Block
        /** An aside: italic and muted, with a bar at its side; a quote in the .md. Typed in the input bar. */
        data class Note(override val id: String, val text: String) : Block
        /** Body text the user writes, as an answer's text looks (adam, Nov 13: a paragraph is not a note). */
        data class Paragraph(override val id: String, val text: String) : Block
        /** A list, one item per line of [text] ([items]); a "- " or "• " the user typed is not part of the item. */
        data class Bullets(override val id: String, val text: String) : Block {
            val items: List<String> get() = PressDoc.items(text)
        }
        /** A thin line across the page, between parts. */
        data class Divider(override val id: String) : Block

        /** An answer from a chat; [id] is the chat message's id, so the same answer is in a project only once. */
        data class Answer(
            override val id: String,
            val chatTitle: String,
            val question: String,
            val text: String,
            val sources: List<Source> = emptyList(),
            val addedAt: Long = 0,
            val model: String? = null,
            val tokPerSec: Double? = null,
            /** The heading the user typed; blank means the question stands as the heading. */
            val heading: String = "",
            /**
             * Its text was changed (typed, or a rewrite the user used): only the sources it still cites by [n] print, so a
             * rewrite that cites nothing has no Sources line (validator G-5). An answer as it came from the chat keeps all.
             */
            val edited: Boolean = false,
        ) : Block {
            /** The heading on the paper: the user's, or the question, or the chat's title. */
            val shownHeading: String get() = heading.ifBlank { question.ifBlank { chatTitle } }
            /** The question in italics under a heading the user changed; null when the heading is the question. */
            val shownQuestion: String? get() = question.takeIf { heading.isNotBlank() && it.isNotBlank() && it.trim() != heading.trim() }
        }

        /**
         * A picture: a Designer drawing ([file] is its path in Designer's folder, [answerId] the answer it illustrates, if
         * any) or, from phase 2, a photo ([file] is a name in the project's folder). [width] and [height] are its pixels, for
         * its shape before the file is read.
         */
        data class Picture(
            override val id: String,
            val kind: Kind,
            val file: String,
            val caption: String = "",
            val place: Place = Place.CENTER,
            val answerId: String? = null,
            val recipe: Recipe? = null,
            val width: Int = 0,
            val height: Int = 0,
        ) : Block

        /** A block written by a newer BOAR: not shown, kept as it is. */
        data class Unknown(override val id: String, val raw: JsonObject) : Block
    }

    data class Project(
        val id: String,
        val name: String,
        val createdAt: Long,
        val blocks: List<Block> = emptyList(),
        /** When the user last saw this project: answers added after it make the "new answers" banner. */
        val seenAt: Long = 0,
    ) {
        /** Answers that came in from a chat since the user last looked. */
        val unseen: Int get() = blocks.count { it is Block.Answer && it.addedAt > seenAt }
        val answers: List<Block.Answer> get() = blocks.filterIsInstance<Block.Answer>()
    }

    data class Library(val open: String, val projects: List<Project>) {
        val current: Project get() = projects.firstOrNull { it.id == open } ?: projects.first()

        private fun mapProject(id: String, f: (Project) -> Project) = copy(projects = projects.map { if (it.id == id) f(it) else it })
        private fun mapOpen(f: (Project) -> Project) = mapProject(current.id, f)
        private fun mapBlock(blockId: String, f: (Block) -> Block) = mapOpen { p -> p.copy(blocks = p.blocks.map { if (it.id == blockId) f(it) else it }) }

        fun switchTo(id: String) = if (projects.any { it.id == id }) copy(open = id) else this

        /**
         * The menu's short list (adam, Nov 12: ten projects made the menu long): the open project first, then the ones seen or
         * made most recently, [n] in all. The rest are behind "All projects", with a search.
         */
        fun shortList(n: Int = SHORT_LIST): List<Project> =
            (listOf(current) + projects.filter { it.id != current.id }.sortedByDescending { maxOf(it.seenAt, it.createdAt) }).take(n)

        /** Every project whose name has [query] (case and accents aside), the open one first, then the most recent. */
        fun search(query: String): List<Project> {
            fun norm(x: String) = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            val q = norm(query.trim())
            return shortList(Int.MAX_VALUE).filter { q.isEmpty() || norm(it.name).contains(q) }
        }

        /** A new empty project, opened; it goes first in the list. */
        fun newProject(id: String, name: String, now: Long) =
            copy(open = id, projects = listOf(Project(id, name.trim().ifEmpty { "-" }, now, seenAt = now)) + projects)

        fun rename(id: String, name: String) = name.trim().takeIf { it.isNotEmpty() }?.let { n -> mapProject(id) { it.copy(name = n) } } ?: this

        /** Deletes a project (its folder goes too, phase 2's photos with it); the last one can't go. */
        fun deleteProject(id: String): Library {
            if (projects.size <= 1 || projects.none { it.id == id }) return this
            val rest = projects.filter { it.id != id }
            return Library(if (open == id) rest.first().id else open, rest)
        }

        /** An answer from a chat goes at the end of the open project, once; [after] (its drawing) follows it. */
        fun addAnswer(a: Block.Answer, after: List<Block> = emptyList()) = mapOpen { p ->
            if (p.blocks.any { it.id == a.id }) p else p.copy(blocks = p.blocks + a + after.filter { b -> p.blocks.none { it.id == b.id } })
        }

        fun has(answerId: String) = current.blocks.any { it is Block.Answer && it.id == answerId }

        /** Takes an answer out of the open project, and the drawing that came with it. */
        fun removeAnswer(answerId: String) = mapOpen { p -> p.copy(blocks = p.blocks.filterNot { it.id == answerId || (it is Block.Picture && it.answerId == answerId) }) }

        /** Puts [b] at [at] in a project (the open one by default; clamped): a new block, or a removed one back where it was (Undo). */
        fun insert(b: Block, at: Int, projectId: String = current.id) = mapProject(projectId) { p ->
            if (p.blocks.any { it.id == b.id }) p else p.copy(blocks = p.blocks.toMutableList().apply { add(at.coerceIn(0, size), b) })
        }

        fun remove(blockId: String) = mapOpen { p -> p.copy(blocks = p.blocks.filter { it.id != blockId }) }

        /** Moves a block up (-1) or down (+1). */
        fun move(blockId: String, by: Int) = mapOpen { p ->
            val i = p.blocks.indexOfFirst { it.id == blockId }
            val j = i + by
            if (i < 0 || j !in p.blocks.indices) p else p.copy(blocks = p.blocks.toMutableList().apply { add(j, removeAt(i)) })
        }

        /** Edits a text block in place: a heading's or note's text, an answer's heading or text. */
        fun editText(blockId: String, field: Field, value: String) = mapBlock(blockId) { b ->
            when {
                b is Block.Heading -> b.copy(text = value)
                b is Block.Note -> b.copy(text = value)
                b is Block.Paragraph -> b.copy(text = value)
                b is Block.Bullets -> b.copy(text = value)
                b is Block.Answer && field == Field.HEADING -> b.copy(heading = if (value.trim() == b.question.trim()) "" else value)
                b is Block.Answer -> if (value == b.text) b else b.copy(text = value, edited = true)
                b is Block.Picture && field == Field.CAPTION -> b.copy(caption = value)
                else -> b
            }
        }

        /** A picture's place: centered at the full width, or left / right with the next text beside it (phase 2). */
        fun place(blockId: String, place: Place) = mapBlock(blockId) { b -> if (b is Block.Picture) b.copy(place = place) else b }

        /** The user saw [projectId]'s new answers (Show, or left the page); nothing changes when there were none. */
        fun markSeen(now: Long, projectId: String = current.id) =
            if (projects.firstOrNull { it.id == projectId }?.unseen ?: 0 == 0) this else mapProject(projectId) { it.copy(seenAt = now) }

        /** A deleted chat's answers leave every project, with the drawings that came with them (adam chose (a), Nov 12). */
        fun eraseAnswers(ids: Collection<String>): Library {
            val gone = ids.toSet()
            if (gone.isEmpty()) return this
            return copy(projects = projects.map { p ->
                p.copy(blocks = p.blocks.filterNot { (it is Block.Answer && it.id in gone) || (it is Block.Picture && it.answerId in gone) })
            })
        }

        /** How many of [ids] are in any project (the chat's delete row says so). */
        fun countOf(ids: Collection<String>): Int {
            val set = ids.toSet()
            return projects.sumOf { p -> p.blocks.count { it is Block.Answer && it.id in set } }
        }
    }

    enum class Field { TEXT, HEADING, CAPTION }

    /** A list's items: one per non-blank line, a leading "-", "*" or "•" taken off. */
    fun items(text: String): List<String> = text.lines().map { it.trim().replace(Regex("^[-*•]\\s*"), "").trim() }.filter { it.isNotEmpty() }

    /** A list as the paper and the PDF show it: "• " before each item. */
    fun bulleted(text: String): String = items(text).joinToString("\n") { "•  $it" }

    fun emptyLibrary(id: String, name: String, now: Long) = Library(id, listOf(Project(id, name, now, seenAt = now)))

    // ---- JSON ----

    private val json = Json { ignoreUnknownKeys = true }

    fun toJson(lib: Library): String = JsonObject(linkedMapOf(
        "version" to JsonPrimitive(VERSION),
        "open" to JsonPrimitive(lib.open),
        "projects" to JsonArray(lib.projects.map { p ->
            JsonObject(linkedMapOf(
                "id" to JsonPrimitive(p.id), "name" to JsonPrimitive(p.name), "createdAt" to JsonPrimitive(p.createdAt), "seenAt" to JsonPrimitive(p.seenAt),
                "blocks" to JsonArray(p.blocks.map(::blockJson)),
            ))
        }),
    )).toString()

    private fun str(s: String?): JsonElement = s?.let { JsonPrimitive(it) } ?: JsonNull

    private fun blockJson(b: Block): JsonObject = when (b) {
        is Block.Heading -> JsonObject(linkedMapOf("type" to JsonPrimitive("heading"), "id" to JsonPrimitive(b.id), "text" to JsonPrimitive(b.text)))
        is Block.Note -> JsonObject(linkedMapOf("type" to JsonPrimitive("note"), "id" to JsonPrimitive(b.id), "text" to JsonPrimitive(b.text)))
        is Block.Paragraph -> JsonObject(linkedMapOf("type" to JsonPrimitive("paragraph"), "id" to JsonPrimitive(b.id), "text" to JsonPrimitive(b.text)))
        is Block.Bullets -> JsonObject(linkedMapOf("type" to JsonPrimitive("list"), "id" to JsonPrimitive(b.id), "text" to JsonPrimitive(b.text)))
        is Block.Divider -> JsonObject(linkedMapOf("type" to JsonPrimitive("divider"), "id" to JsonPrimitive(b.id)))
        is Block.Answer -> JsonObject(linkedMapOf(
            "type" to JsonPrimitive("answer"), "id" to JsonPrimitive(b.id), "chatTitle" to JsonPrimitive(b.chatTitle), "question" to JsonPrimitive(b.question),
            "heading" to JsonPrimitive(b.heading), "text" to JsonPrimitive(b.text), "addedAt" to JsonPrimitive(b.addedAt), "model" to str(b.model),
            "tokPerSec" to (b.tokPerSec?.let { JsonPrimitive(it) } ?: JsonNull), "edited" to JsonPrimitive(b.edited),
            "sources" to JsonArray(b.sources.map { JsonObject(linkedMapOf("title" to JsonPrimitive(it.title), "source" to str(it.source))) }),
        ))
        is Block.Picture -> JsonObject(linkedMapOf(
            "type" to JsonPrimitive("picture"), "id" to JsonPrimitive(b.id), "kind" to JsonPrimitive(b.kind.name.lowercase()), "file" to JsonPrimitive(b.file),
            "caption" to JsonPrimitive(b.caption), "place" to JsonPrimitive(b.place.name.lowercase()), "answerId" to str(b.answerId),
            "width" to JsonPrimitive(b.width), "height" to JsonPrimitive(b.height),
            "recipe" to (b.recipe?.let { r -> JsonObject(linkedMapOf("subject" to str(r.subject), "style" to str(r.style), "size" to str(r.size),
                "seed" to (r.seed?.let { JsonPrimitive(it) } ?: JsonNull))) } ?: JsonNull),
        ))
        is Block.Unknown -> b.raw
    }

    /** Reads the projects file; throws on a file that isn't one (the app then writes nothing over it). */
    fun parse(text: String): Library {
        val o = json.parseToJsonElement(text) as JsonObject
        val projects = (o["projects"] as JsonArray).map { e ->
            val p = e as JsonObject
            Project(p.s("id")!!, p.s("name") ?: "", p.l("createdAt") ?: 0, (p["blocks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseBlock) }, p.l("seenAt") ?: 0)
        }
        require(projects.isNotEmpty()) { "no projects" }
        return Library(o.s("open")?.takeIf { id -> projects.any { it.id == id } } ?: projects.first().id, projects)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

    private fun parseBlock(o: JsonObject): Block? {
        val id = o.s("id") ?: return null
        return when (o.s("type")) {
            "heading" -> Block.Heading(id, o.s("text").orEmpty())
            "note" -> Block.Note(id, o.s("text").orEmpty())
            "paragraph" -> Block.Paragraph(id, o.s("text").orEmpty())
            "list" -> Block.Bullets(id, o.s("text").orEmpty())
            "divider" -> Block.Divider(id)
            "answer" -> Block.Answer(id, o.s("chatTitle").orEmpty(), o.s("question").orEmpty(), o.s("text").orEmpty(), sources(o["sources"]), o.l("addedAt") ?: 0,
                o.s("model"), o.d("tokPerSec"), o.s("heading").orEmpty(), o.b("edited") ?: false)
            "picture" -> Block.Picture(id, if (o.s("kind") == "photo") Kind.PHOTO else Kind.DRAWING, o.s("file") ?: return null, o.s("caption").orEmpty(),
                Place.of(o.s("place")), o.s("answerId"), (o["recipe"] as? JsonObject)?.let { r -> Recipe(r.s("subject"), r.s("style"), r.s("size"), r.l("seed")) },
                (o.l("width") ?: 0).toInt(), (o.l("height") ?: 0).toInt())
            else -> Block.Unknown(id, o)
        }
    }

    private fun sources(e: JsonElement?): List<Source> = (e as? JsonArray).orEmpty().mapNotNull { s ->
        (s as? JsonObject)?.let { o -> o.s("title")?.let { Source(it, o.s("source")?.takeIf { v -> v.isNotEmpty() && v != "null" }) } }
    }

    // ---- Migration from the single basket (files/labs/basket.json, Press until Nov 13) ----

    /**
     * Today's basket becomes the first project, [name] ("Field notes"), in the same order, nothing left out: each answer
     * with its question, edited heading and text, sources, model and speed; the drawing it had (the one picked, else its
     * own illustration from [illustrations], answer id to file, unless it was switched off) follows it as a picture.
     * Throws on a basket that can't be read: the app then leaves the file where it is.
     */
    fun fromBasket(basketJson: String, projectId: String, name: String, now: Long, illustrations: Map<String, String> = emptyMap()): Library {
        val items = json.parseToJsonElement(basketJson) as JsonArray
        val blocks = mutableListOf<Block>()
        items.forEach { e ->
            val o = e as JsonObject
            val id = o.s("id") ?: error("an item without id")
            val text = o.s("text") ?: error("an item without text")
            // The old basket kept an edited heading in "question" itself: it stays the question, shown as the heading.
            blocks += Block.Answer(id, o.s("chatTitle").orEmpty(), o.s("question").orEmpty(), text, sources(o["sources"]), o.l("addedAt") ?: 0,
                o.s("model")?.takeIf { it.isNotEmpty() && it != "null" }, o.d("tokPerSec"))
            val drawing = if (o.b("imageOff") == true) null else o.s("image")?.takeIf { it.isNotEmpty() && it != "null" } ?: illustrations[id]
            if (drawing != null) blocks += Block.Picture("pic-$id", Kind.DRAWING, drawing, answerId = id)
        }
        // Seen now: the migrated answers aren't "new" in the banner.
        return Library(projectId, listOf(Project(projectId, name, now, blocks, seenAt = now)))
    }

    /** What loading found, for the app to act on. */
    sealed interface Loaded {
        /** The projects file, read. */
        data class Read(val library: Library) : Loaded
        /** No projects file: the basket became the first project; write it, then the basket file can go. */
        data class Migrated(val library: Library) : Loaded
        /** Nothing saved yet (or a basket that can't be read, which stays where it is): a first empty project. */
        data class Fresh(val library: Library, val basketUnreadable: Boolean) : Loaded
        /** The projects file can't be read: nothing is written over it. */
        data class Unreadable(val error: Throwable) : Loaded
    }

    /**
     * Loads the projects ([projects] is the file's text, or null when there's none). The basket is migrated once: only when
     * there is no projects file yet, so after the first write it never runs again.
     */
    /**
     * The old basket that couldn't be read the first time, read now (validator G-3): its answers become one more project,
     * after the ones there are. Throws if it still can't be read; an empty basket changes nothing.
     */
    fun mergeBasket(lib: Library, basket: String, newId: String, name: String, now: Long, illustrations: Map<String, String> = emptyMap()): Library {
        val old = fromBasket(basket, newId, name, now, illustrations)
        val adds = old.projects.filter { it.blocks.isNotEmpty() && lib.projects.none { p -> p.id == it.id } }
        return if (adds.isEmpty()) lib else lib.copy(projects = lib.projects + adds)
    }

    fun load(projects: String?, basket: String?, newId: String, name: String, now: Long, illustrations: Map<String, String> = emptyMap()): Loaded {
        if (projects != null) return runCatching { Loaded.Read(parse(projects)) }.getOrElse { Loaded.Unreadable(it) }
        if (basket != null) return runCatching { Loaded.Migrated(fromBasket(basket, newId, name, now, illustrations)) }
            .getOrElse { Loaded.Fresh(emptyLibrary(newId, name, now), basketUnreadable = true) }
        return Loaded.Fresh(emptyLibrary(newId, name, now), basketUnreadable = false)
    }
}
