package team.sopa.boar.engine.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.chat.KeepGoing.Answer
import team.sopa.boar.engine.chat.KeepGoing.Step

/** t9 0235 (adam: "send answers next"): what "Keep going" offers, one test per row of the table. */
class KeepGoingTest {
    private val two = listOf("Tell me more about Salt", "What is Dead Sea?")

    @Test fun `from the model in Succinct - Go deeper, then up to two follow-ups`() {
        val n = KeepGoing.of(Answer(style = "succinct", hasSources = true), two + "A third")
        assertEquals(Step.DEEPER, n.step); assertEquals(two, n.followUps)
    }

    @Test fun `from the model in Detailed - Research this`() =
        assertEquals(Step.RESEARCH, KeepGoing.of(Answer(style = "detailed", hasSources = true), two).step)

    @Test fun `Deep Research - the ladder has ended, follow-ups only`() {
        val n = KeepGoing.of(Answer(style = "detailed", deepResearch = true, hasSources = true), two)
        assertNull(n.step); assertEquals(two, n.followUps)
    }

    @Test fun `a plugin's instant answer - Ask BOAR and nothing else, the CO answer - nothing`() {
        val n = KeepGoing.of(Answer(instant = true, canBypass = true, hasSources = true), two)
        assertEquals(Step.ASK_BOAR, n.step); assertTrue(n.followUps.isEmpty())
        val co = KeepGoing.of(Answer(instant = true, canBypass = false), two)
        assertNull(co.step); assertTrue(co.followUps.isEmpty())
    }

    @Test fun `cut at the limit - no step, the repair is in the card`() {
        val n = KeepGoing.of(Answer(style = "succinct", cut = true, hasSources = true), two)
        assertNull(n.step); assertEquals(two, n.followUps)
    }

    @Test fun `failed, stopped or timed out - nothing`() {
        val n = KeepGoing.of(Answer(broken = true, style = "succinct", hasSources = true), two)
        assertNull(n.step); assertTrue(n.followUps.isEmpty())
    }

    @Test fun `kept short on purpose, Personal Files and Custom - no step`() {
        assertNull(KeepGoing.of(Answer(style = "succinct", keptShort = true), emptyList()).step)
        assertNull(KeepGoing.of(Answer(style = "personal"), emptyList()).step)
        assertNull(KeepGoing.of(Answer(style = "custom"), emptyList()).step)
    }

    @Test fun `no follow-up without sources, three bubbles at most`() {
        assertTrue(KeepGoing.of(Answer(style = "succinct", hasSources = false), two).followUps.isEmpty())
        val n = KeepGoing.of(Answer(style = "succinct", hasSources = true), listOf("a", "b", "c", "d"))
        assertTrue((if (n.step != null) 1 else 0) + n.followUps.size <= KeepGoing.MAX)
    }
}

class FollowUpsTest {
    @Test fun `another source's title first, then a named thing from a cited passage, never the question's topic`() {
        val sources = listOf(
            FollowUps.Source("Seawater", "Seawater is water from a sea. Its salinity comes from the Dead Sea basins and rivers.", true),
            FollowUps.Source("Salinity (water)", "Salinity is the saltiness of a body of water.", true),
            FollowUps.Source("Seawater", "duplicate", false),
        )
        val got = FollowUps.pick("Why is seawater salty?", sources, pt = false)
        assertEquals(listOf("Tell me more about Salinity", "What is Dead Sea?"), got)
    }

    @Test fun `Portuguese, two at most, no duplicates`() {
        val sources = listOf(FollowUps.Source("Marie Curie", "Marie Curie trabalhou com Pierre Curie em Paris.", true), FollowUps.Source("Polônio", "O polônio foi descoberto em 1898.", true))
        val got = FollowUps.pick("Quem foi Marie Curie?", sources, pt = true)
        assertEquals("Fale mais sobre Polônio", got.first())
        assertTrue(got.size <= 2 && got.toSet().size == got.size && got.none { it.contains("Marie Curie") })
    }

    @Test fun `titles lose a trailing parenthesis and a pack section`() {
        assertEquals("Mercury", FollowUps.cleanTitle("Mercury (planet)"))
        assertEquals("Salt", FollowUps.cleanTitle("Salt — Uses"))
    }

    @Test fun `no sources, no follow-ups`() = assertTrue(FollowUps.pick("Why is the sky blue?", emptyList(), pt = false).isEmpty())
}

/** The app side, read from the source: the bubble shows the verb, everything else reads the real question. */
class KeepGoingWiringTest {
    private val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
    private val chat = java.io.File("../app/src/main/java/team/sopa/boar/ui/chat/ChatScreen.kt").readText()

    @Test fun `a step is for that answer only - the settings are not written`() {
        val fn = vm.substringAfter("fun keepGoing(").substringBefore("\n    }\n")
        assertTrue(fn.contains("styleOnce = \"detailed\"") && fn.contains("deepOnce = true") && fn.contains("askModel(answerId)"))
        assertTrue(!fn.contains("settings.") && !fn.contains("setDeepResearch") && !fn.contains("personalityId ="))
        assertTrue(vm.contains("val chosenStyle = styleNow ?: settings.personalityId") && vm.contains("val deep = (deepNow ?: _state.value.deepResearch) && !personal"))
    }

    @Test fun `the user message keeps the real question as its text, the step in its meta`() {
        assertTrue(vm.contains("chats.addMessage(it, \"user\", q, userId); if (stepNow != null) chats.setMessageMeta(userId, stepMeta(stepNow))"))
        assertTrue(vm.contains("UiMessage(userId, \"user\", q, step = stepNow)") && vm.contains("step = if (r.role == \"user\") stepFromMeta(r.meta) else null"))
    }

    @Test fun `the bubble shows the verb, hold and TalkBack still have the question`() {
        val bubble = chat.substringAfter("private fun userBubbleText(").substringBefore("\n}\n")
        assertTrue(bubble.contains("t(\"native.chat.kgDeeper\")") && bubble.contains("t(\"native.chat.kgResearch\")") && bubble.contains("t(\"native.chat.kgAskQ\", \"q\" to m.text)"))
        assertTrue(chat.contains("combinedClickable(onClick = {}, onLongClick = { onReuse(m.text) })"))
        assertTrue(chat.contains("contentDescription = shownText + \". \" + m.text"))
    }

    @Test fun `under the latest answer only, not while answering or queued, gone with text in the input`() {
        assertTrue(chat.contains("last.role == \"assistant\" && last.text.isNotBlank() && state.answeringId == null && state.queued.isEmpty() && input.isBlank()"))
        assertTrue(!chat.contains("ActionButton(null, t(\"native.chat.askBoarInstead\")"))
        assertTrue(chat.contains("VerbLine(t(\"native.chat.tryAgain\"))") && chat.contains("VerbLine(t(\"native.chat.allowMore\""))
        for (d in listOf("values", "values-pt")) {
            val x = java.io.File("../app/src/main/res/$d/strings_native.xml").readText()
            for (k in listOf("keepGoing", "kgGroup", "kgDeeper", "kgResearch", "kgAsk", "kgAskQ", "kgQuick", "kgDetailed", "kgResearchRung", "kgDeeperSpoken", "kgResearchSpoken", "kgAskSpoken", "kgFollowSpoken"))
                assertTrue("$d $k", x.contains("name=\"native_chat_$k\""))
        }
        assertTrue(java.io.File("../app/src/main/res/values/strings_native.xml").readText().contains(">Keep going<"))
        assertTrue(java.io.File("../app/src/main/res/values-pt/strings_native.xml").readText().contains(">Continue<"))
    }
}
