package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.canvas.CardSpec.Parsed
import team.sopa.boar.engine.canvas.CardSpec.Reason

class CanvasTest {
    private val answer = "Mount Everest is 8,849 m tall, K2 is 8,611 m and Kangchenjunga 8,586 m. The climb takes about 2 months."

    private fun ok(raw: String, a: String = answer) = (CardSpec.parse(raw, a) as Parsed.Ok).card
    private fun rejected(raw: String, a: String = answer) = (CardSpec.parse(raw, a) as Parsed.Rejected).reason

    @Test fun `a bar chart from the answer's own numbers, inside a fence and a sentence`() {
        val c = ok("Here is the card:\n```json\n{\"type\":\"bars\",\"title\":\"Highest peaks\",\"unit\":\"m\",\"items\":[{\"label\":\"Everest\",\"value\":8849},{\"label\":\"K2\",\"value\":\"8,611\"},{\"label\":\"Kangchenjunga\",\"value\":8586}]}\n```")
        assertEquals(Card.Bars("Highest peaks", "m", listOf("Everest" to 8849.0, "K2" to 8611.0, "Kangchenjunga" to 8586.0)), c)
    }

    @Test fun `a number the answer never wrote kills the chart`() {
        assertEquals(Reason.INVENTED_NUMBERS, rejected("""{"type":"bars","title":"Peaks","items":[{"label":"Everest","value":8849},{"label":"Lhotse","value":8516}]}"""))
    }

    @Test fun `numbers as people write them`() {
        assertEquals(listOf(1200.0, 3.5, -4.0, 12.0), CardSpec.numbersIn("1,200 people, 3.5 km, −4 °C and 12%"))
    }

    @Test fun `a table pads short rows and drops empty ones`() {
        val c = ok("""{"type":"table","title":"Peaks","columns":["Peak","Height"],"rows":[["Everest","8,849 m"],["K2"],["",""]]}""") as Card.Table
        assertEquals(listOf(listOf("Everest", "8,849 m"), listOf("K2", "")), c.rows)
    }

    @Test fun `steps, compare and facts`() {
        assertEquals(Card.Steps("Climb", listOf("Acclimatize", "Summit")), ok("""{"type":"steps","title":"Climb","steps":["Acclimatize","Summit"]}"""))
        val cmp = ok("""{"type":"compare","title":"Routes","items":[{"name":"South","points":["Nepal"]},{"name":"North","points":["Tibet","colder"]}]}""") as Card.Compare
        assertEquals(listOf("South", "North"), cmp.items.map { it.first })
        assertEquals(Card.Facts("Everest", listOf("Height" to "8,849 m")), ok("""{"type":"facts","title":"Everest","facts":[{"label":"Height","value":"8,849 m"}]}"""))
    }

    @Test fun `what isn't a card`() {
        assertEquals(Reason.NO_JSON, rejected("I can't make a card for this."))
        assertEquals(Reason.NOTHING, rejected("""{"type":"none"}"""))
        assertEquals(Reason.UNKNOWN_TYPE, rejected("""{"type":"map","title":"x"}"""))
        assertEquals(Reason.EMPTY, rejected("""{"type":"steps","title":"x","steps":["only one"]}"""))
        assertEquals(Reason.EMPTY, rejected("""{"type":"bars","title":"x","items":[{"label":"Everest","value":8849}]}"""))
    }

    @Test fun `the thinking before the card is skipped`() {
        assertTrue(ok("<think>{\"type\":\"none\"}</think>{\"type\":\"facts\",\"title\":\"t\",\"facts\":[{\"label\":\"a\",\"value\":\"b\"}]}") is Card.Facts)
    }

    private val theme = WidgetHtml.Theme("#000000", "#ffffff", "#888888", "#10b981", "#111111", dark = true)

    @Test fun `a widget page comes out of a fence or bare text`() {
        assertEquals("<html><body><button>Go</button></body></html>", WidgetHtml.extract("Sure!\n```html\n<html><body><button>Go</button></body></html>\n```\nEnjoy."))
        assertEquals("<div id=\"a\">x</div>", WidgetHtml.extract("<div id=\"a\">x</div>"))
        assertNull(WidgetHtml.extract("No page here."))
        assertNull(WidgetHtml.extract("<div>" + "x".repeat(WidgetHtml.MAX_CHARS) + "</div>"))
    }

    @Test fun `the policy comes first and nothing may load`() {
        val doc = WidgetHtml.wrap("<!DOCTYPE html><html><head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src *\"></head><body>hi</body></html>", theme)
        val ours = doc.indexOf(WidgetHtml.POLICY)
        assertTrue(ours in 0 until doc.indexOf("default-src *"))
        assertTrue(WidgetHtml.POLICY.startsWith("default-src 'none'"))
        assertTrue("connect-src 'none'" in WidgetHtml.POLICY)
        assertTrue(doc.startsWith("<!doctype html><html><head>"))
        assertTrue("--accent:#10b981" in doc)
        assertEquals(1, Regex("<html", RegexOption.IGNORE_CASE).findAll(doc).count())
    }
}
