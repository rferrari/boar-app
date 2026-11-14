package team.sopa.boar.engine.tmp
import kotlinx.serialization.json.*
import org.junit.Test
import java.io.File
class FollowUpDumpTest {
    @Test fun dump() {
        val dir = File("build/kg-in"); if (!dir.isDirectory) return
        val out = StringBuilder("| question | model | style | sources | follow-ups |\n|---|---|---|---|---|\n")
        for (f in dir.listFiles()!!.sorted()) for (line in f.readLines()) {
            if (line.isBlank()) continue
            val o = Json.parseToJsonElement(line).jsonObject
            val q = o["query"]!!.jsonPrimitive.content; val a = o["answer"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val titles = o["retrievedTitles"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val pt = team.sopa.boar.engine.routing.QueryContext.isPortugueseQuestion(q)
            val picks = team.sopa.boar.engine.chat.FollowUps.pick(q, titles.mapIndexed { i, t -> team.sopa.boar.engine.chat.FollowUps.Source(t, "", a.contains("[${i + 1}]")) }, pt)
            val model = o["modelId"]?.jsonPrimitive?.content.orEmpty().substringBefore("-q4")
            out.append("| $q | $model | ${o["style"]?.jsonPrimitive?.content} | ${titles.joinToString("; ")} | ${picks.joinToString("; ").ifEmpty { "(none)" }} |\n")
        }
        File("build/kg-table.md").writeText(out.toString())
    }
}
