package team.sopa.boar.labs.printer

import team.sopa.boar.engine.chat.ChatExport
import java.text.DateFormat
import java.util.Date

/**
 * The basket as a Markdown file: the same title, order, headings and switches as the PDF, as plain text (no drawings).
 * Handy to pull a long test session off the phone and read or diff it on a computer.
 */
object MarkdownCompiler {
    fun compile(title: String, items: List<Basket.Item>, footer: String, meta: PdfCompiler.Meta, labels: ChatExport.Labels): String = buildString {
        append("# ").append(title.trim().replace('\n', ' ')).append("\n\n")
        append('_').append((if (meta.date) DateFormat.getDateInstance(DateFormat.LONG).format(Date()) + " · " else "") + footer).append("_\n\n")
        append(items.joinToString("\n---\n\n") { i ->
            ChatExport.turn(ChatExport.Turn(i.question.ifBlank { i.chatTitle }, i.text, null,
                if (meta.sources) i.sources.map { it.title to it.source } else emptyList(),
                if (meta.model) PdfCompiler.modelLine(i) else null), labels)
        })
    }
}
