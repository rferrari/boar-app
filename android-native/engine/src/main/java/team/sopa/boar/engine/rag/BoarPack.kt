package team.sopa.boar.engine.rag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * BOAR's knowledge about itself (adam, 16 Nov; t9 0225, 0233): the BOAR Guide (assets/boar-guide/<lang>.md, one passage
 * per section) and the BOAR FAQ written by Amandita (assets/boar-faq/faq.json), every language in one built-in pack.
 * Internal: not a knowledge pack the user sees, switches or deletes; never searched by Local Files. Each passage knows
 * its language ("boar-<lang>-…"); its id carries a hash of its text, so an update replaces exactly what changed.
 * Adding a language later is adding its guide and FAQ text: nothing else changes.
 */
object BoarPack {
    data class Passage(val id: String, val lang: String, val title: String, val source: String, val body: String)

    const val PREFIX = "boar-"
    /** A section longer than this is split on its paragraphs: the search model reads 512 tokens. */
    const val MAX_CHARS = 1400

    private val GUIDE_SOURCE = mapOf("en" to "BOAR Guide", "pt" to "Guia do BOAR")

    /** The guide in [lang]: its "## " sections, each a passage titled "BOAR Guide · <section>". */
    fun guide(lang: String, markdown: String): List<Passage> {
        val label = GUIDE_SOURCE[lang] ?: "BOAR Guide"
        return Regex("(?m)^## ").split(markdown).drop(1).flatMap { sec ->
            val heading = sec.substringBefore('\n').trim()
            val body = plain(sec.substringAfter('\n'))
            if (heading.isEmpty() || body.isEmpty()) emptyList()
            else parts(body).mapIndexed { i, b -> passage(lang, "guide", heading + if (i > 0) " (${i + 1})" else "", "$label · $heading", label, b) }
        }
    }

    /** The FAQ, every language it has: one passage per entry, the question as its title, the answer as its body. */
    fun faq(json: String): List<Passage> {
        val root = Json.parseToJsonElement(json).jsonObject
        val sources = root["source"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
        return root["entries"]!!.jsonArray.flatMap { e ->
            val o = e.jsonObject
            // Each language is an object {q, a} under its code; "id" and "checked" are not languages.
            o.keys.filter { it.length == 2 && o[it] is kotlinx.serialization.json.JsonObject }.mapNotNull { lang ->
                val qa = o[lang]!!.jsonObject
                val q = qa["q"]!!.jsonPrimitive.content.trim(); val a = qa["a"]!!.jsonPrimitive.content.trim()
                val name = if (lang == "pt") "FAQ do BOAR" else "BOAR FAQ"
                passage(lang, "faq", q, "$name · $q", sources[lang] ?: name, "$q\n$a")
            }
        }
    }

    fun passages(guides: Map<String, String>, faqJson: String?): List<Passage> =
        guides.flatMap { (lang, md) -> guide(lang, md) } + (faqJson?.let(::faq) ?: emptyList())

    private fun passage(lang: String, kind: String, key: String, title: String, source: String, body: String): Passage {
        val slug = SeedCorpus.slug(key).take(48)
        val hash = Integer.toHexString((title + "\n" + body).hashCode()).padStart(8, '0')
        return Passage("$PREFIX$lang-$kind-$slug-$hash", lang, title, source, body)
    }

    /** Markdown to the plain text a passage holds: no list marks, no emphasis, no links' brackets. */
    private fun plain(md: String): String = md.lines().joinToString("\n") { l ->
        l.replace(Regex("^\\s*[-*]\\s+"), "").replace("**", "").replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1").trimEnd()
    }.replace(Regex("\n{3,}"), "\n\n").trim()

    private fun parts(body: String): List<String> {
        if (body.length <= MAX_CHARS) return listOf(body)
        val out = ArrayList<String>(); val cur = StringBuilder()
        for (p in body.split(Regex("\n\\s*\n"))) {
            if (cur.isNotEmpty() && cur.length + p.length > MAX_CHARS) { out += cur.toString().trim(); cur.clear() }
            cur.append(p).append("\n\n")
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }

    /** A source label BoarPack writes ("BOAR Guide", "FAQ do BOAR, escrito pela Amandita"): shareable in a PDF. */
    fun isBoarSource(source: String): Boolean = Regex("^(?:BOAR Guide|Guia do BOAR|BOAR FAQ|FAQ do BOAR)(?:$|,)").containsMatchIn(source.trim())

    /** The passage's language, from its id. */
    fun langOf(chunkId: String): String? = chunkId.removePrefix(PREFIX).substringBefore('-').takeIf { chunkId.startsWith(PREFIX) }

    /**
     * A question about BOAR itself, EN or PT (t9 0225 item 3): BOAR named, the app itself, or a feature only BOAR has a
     * name for. Generic words alone ("models", "offline", "battery") don't count: "models of the atom" isn't about BOAR.
     */
    fun aboutBoar(query: String): Boolean = BOAR.containsMatchIn(query) || APP.containsMatchIn(query) || FEATURE.containsMatchIn(query) || PIN.containsMatchIn(query)

    private const val B = """(?<![\p{L}\p{N}_])"""
    private const val E = """(?![\p{L}\p{N}_])"""
    private val BOAR = Regex("""$B\$?boar$E""", RegexOption.IGNORE_CASE)
    private val APP = Regex("""$B(?:this app|the app|this application|o app|este app|esse app|o aplicativo|este aplicativo|esse aplicativo)$E""", RegexOption.IGNORE_CASE)
    private val FEATURE = Regex(
        """$B(?:local files|arquivos locais|personal files|deep research|pesquisa profunda|knowledge packs?|pacotes? de conhecimento|boar labs|answer styles?|estilos? de resposta|model catalog|cat[áa]logo de modelos|boarverse|boar avatars?|starter profile|perfil iniciante|succinct & direct|thorough & detailed|sucinto e direto|completo e detalhado|my data|meus dados|telemetry|telemetria)$E""",
        RegexOption.IGNORE_CASE,
    )
    /** The app's PIN, written as such (not "pin a message"). */
    private val PIN = Regex("""$B(?:PIN|pin lock|pin code|bloqueio por pin|c[óo]digo pin)$E""")

    /** The floor for a BOAR passage: the semantic search's, not the answer gate's 0.7 (a PT question reads PT text lower). */
    const val MIN_SIMILARITY = RagPure.MIN_SEMANTIC_SIMILARITY

    /**
     * BOAR's passages for a question in [lang] (t9 0233): the question's language first when it has a good one (at or
     * above [MIN_SIMILARITY]); otherwise the other language's, which the answer still uses in the question's language.
     */
    fun rank(hits: List<Chunk>, lang: String): List<Chunk> {
        val good = hits.filter { (it.similarity ?: 0.0) >= MIN_SIMILARITY }.sortedByDescending { it.similarity ?: 0.0 }
        val (own, other) = good.partition { langOf(it.chunkId) == lang }
        return if (own.isNotEmpty()) own + other else other
    }
}
