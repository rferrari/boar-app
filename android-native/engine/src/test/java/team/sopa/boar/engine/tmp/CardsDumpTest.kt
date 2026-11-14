package team.sopa.boar.engine.tmp
import kotlinx.serialization.json.*
import org.junit.Test
import java.io.File
import team.sopa.boar.engine.canvas.CardRules
class CardsDumpTest {
    @Test fun dump() {
        val dir = File("build/kg-in"); if (!dir.isDirectory) return
        val out = StringBuilder("| id | model | style | chars | lists in the text | cards found (kind: count) |\n|---|---|---|---|---|---|\n")
        val full = StringBuilder()
        for (f in dir.listFiles()!!.sorted()) for (line in f.readLines()) {
            if (line.isBlank()) continue
            val o = Json.parseToJsonElement(line).jsonObject
            val q = o["query"]!!.jsonPrimitive.content; val a = o["answer"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val cards = CardRules.find(q, a)
            val desc = cards.joinToString("; ") { c -> when (c) {
                is CardRules.Card.Steps -> "steps: ${c.items.size}"; is CardRules.Card.Table -> "table: ${c.rows.size} rows"
                is CardRules.Card.Facts -> "facts: ${c.pairs.size}"; is CardRules.Card.Compare -> "compare: ${c.sides.size}"
                is CardRules.Card.Chart -> "chart: ${c.points.size} (${c.points.joinToString(", ") { it.label + " " + it.written }})" } }.ifEmpty { "none" }
            val listy = a.lines().count { Regex("""^\s*([-*•]|\d{1,2}[.)])\s""").containsMatchIn(it) }
            val model = o["modelId"]?.jsonPrimitive?.content.orEmpty().substringBefore("-q4")
            out.append("| ${o["queryId"]?.jsonPrimitive?.content} | $model | ${o["style"]?.jsonPrimitive?.content} | ${a.length} | $listy | $desc |\n")
            full.append("=== ${o["queryId"]?.jsonPrimitive?.content} / $model / ${o["style"]?.jsonPrimitive?.content}\nQ: $q\n$a\n-> $desc\n\n")
        }
        File("build/cards-table.md").writeText(out.toString()); File("build/cards-answers.txt").writeText(full.toString())
    }
}
