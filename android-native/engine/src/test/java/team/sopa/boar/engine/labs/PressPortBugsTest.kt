package team.sopa.boar.engine.labs

import org.junit.Assert.assertEquals
import org.junit.Test
import team.sopa.boar.engine.labs.PressDoc.Block

/** BOAR-79, from Biel's iOS port: an empty block beside a photo, and the short list after opening a project. */
class PressPortBugsTest {
    private val photo = Block.Picture("ph", PressDoc.Kind.PHOTO, "/p.jpg", place = PressDoc.Place.LEFT)

    @Test fun `an empty paragraph, list or note after a side photo goes below it, where it can be tapped`() {
        for (empty in listOf(Block.Paragraph("e", " "), Block.Bullets("e", ""), Block.Note("e", ""))) {
            val plan = PressLayout.plan(listOf(photo, empty))
            assertEquals(listOf(PressLayout.Item.Beside(photo, null, true), PressLayout.Item.Single(empty)), plan)
        }
    }

    @Test fun `a paragraph with text still flows beside the photo`() {
        val text = Block.Paragraph("t", "Beside the photo.")
        assertEquals(listOf(PressLayout.Item.Beside(photo, text, true)), PressLayout.plan(listOf(photo, text)))
    }

    @Test fun `a project seen with no new answers moves up the short list`() {
        var lib = PressDoc.emptyLibrary("a", "A", 10)
        lib = lib.newProject("b", "B", 20).newProject("c", "C", 30)
        assertEquals(listOf("c", "b", "a"), lib.shortList().map { it.id })
        // Open A, the oldest, then leave it for C: A was seen last, so it comes right after the open one.
        lib = lib.switchTo("a").markSeen(40, "a").switchTo("c")
        assertEquals(listOf("c", "a", "b"), lib.shortList().map { it.id })
        // Seen earlier than it already was: nothing goes back.
        assertEquals(40L, lib.markSeen(35, "a").projects.first { it.id == "a" }.seenAt)
    }
}
