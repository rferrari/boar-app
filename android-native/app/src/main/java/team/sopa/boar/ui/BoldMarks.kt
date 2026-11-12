package team.sopa.boar.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** A string's **marked** words in bold (adam, Nov 12), for short texts in dialogs and notes; the marks are dropped. */
fun boldMarks(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in Regex("\\*\\*(.+?)\\*\\*").findAll(text)) {
        append(text.substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
