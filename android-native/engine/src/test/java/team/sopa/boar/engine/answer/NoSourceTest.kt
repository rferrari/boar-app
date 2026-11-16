package team.sopa.boar.engine.answer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** t9 0226 point 4: a model's answer with nothing in the library behind it says so. */
class NoSourceTest {
    @Test fun `the search found nothing and the answer cites nothing - the line shows`() {
        assertTrue(NoSource.shows(searched = true, sources = 0, instant = false, question = "How do I change a flat bicycle tyre?", answer = "Remove the wheel, then…"))
    }

    @Test fun `not with sources, a citation, a plugin's answer, small talk, no search or an empty answer`() {
        assertFalse(NoSource.shows(true, 2, false, "Who was Marie Curie?", "A physicist [1]."))
        // Golden change (validator R-C12): with 0 sources a [1] is made up and no longer hides the line.
        assertTrue(NoSource.shows(true, 0, false, "Who was Marie Curie?", "A physicist [1]."))
        assertTrue(NoSource.shows(true, 0, false, "Write a function", "return items[1]"))
        assertFalse(NoSource.shows(true, 0, true, "27000 mAh 3.7 V on a plane", "99.9 Wh."))
        assertFalse(NoSource.shows(true, 0, false, "hey, what's up?", "Hey! How can I help?"))
        assertFalse(NoSource.shows(false, 0, false, "How do I change a flat bicycle tyre?", "Remove the wheel."))
        assertFalse(NoSource.shows(true, 0, false, "How do I change a flat bicycle tyre?", ""))
    }

    @Test fun `the chat shows it under the separator, in EN and PT`() {
        val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()
        assertTrue(chat.contains("NoSource.shows(m.receipt?.retrievalMs != null, m.sources.size, m.receipt?.tool != null, ref.question, m.text, m.receipt?.style)"))
        val en = java.io.File("../app/src/main/res/values/strings_native.xml").readText()
        val pt = java.io.File("../app/src/main/res/values-pt/strings_native.xml").readText()
        assertTrue(en.contains("No source in this phone\\'s library covers this; the answer is from the model alone."))
        assertTrue(pt.contains("Nenhuma fonte da biblioteca deste celular cobre isto; a resposta vem só do modelo."))
    }

    @Test fun `not under Personal Files' own not-found line, which no model wrote (validator R-C11)`() {
        val notFound = "I couldn't find this in your Local Files.\n\nTry asking about something your files say."
        assertFalse(NoSource.shows(true, 0, false, "what did my landlord say?", notFound, style = "personal"))
        assertTrue(NoSource.shows(true, 0, false, "what did my landlord say?", "He said the rent goes up.", style = "detailed"))
    }
}

