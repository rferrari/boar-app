package team.sopa.boar.engine.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The merged visual: the contract, grounding of every number and label, and what the app computes. */
class VisualTest {
    private val peaks = "Mount Everest is 8,849 m high, K2 is 8,611 m and Kangchenjunga 8,586 m. Everest was first climbed on 29 May 1953."
    private val pt = "A população do Brasil é de 203,1 milhões; a Rússia tem 146.4 milhões e uma área de 17.098.246 km²."

    private fun ok(raw: String, answer: String, user: String = ""): Visual =
        (Visual.parse(raw, answer, user) as? Visual.Parsed.Ok)?.visual ?: error("rejected: ${Visual.parse(raw, answer, user)}")
    private fun reason(raw: String, answer: String, user: String = "") = (Visual.parse(raw, answer, user) as? Visual.Parsed.Rejected)?.reason

    // ── The contract ──

    @Test fun `bars, line and share read as one series, pie as share`() {
        val items = """[{"label":"Everest","value":8849},{"label":"K2","value":8611},{"label":"Kangchenjunga","value":8586}]"""
        for ((type, kind) in listOf("bars" to Visual.SeriesKind.BARS, "line" to Visual.SeriesKind.LINE, "share" to Visual.SeriesKind.SHARE, "pie" to Visual.SeriesKind.SHARE)) {
            val v = ok("""{"type":"$type","title":"Highest peaks","unit":"m","items":$items}""", peaks) as Visual.Series
            assertEquals(type, kind, v.kind)
            assertEquals(listOf(8849.0, 8611.0, 8586.0), v.items.map { it.second })
            assertEquals("m", v.unit)
        }
    }

    @Test fun `a card saved before reads the same (CardSpec shapes)`() {
        val old = listOf(
            """{"type":"table","title":"Peaks","columns":["Peak","Height"],"rows":[["Everest","8,849 m"],["K2","8,611 m"]]}""",
            """{"type":"bars","title":"Peaks","unit":"m","items":[{"label":"Everest","value":8849},{"label":"K2","value":"8,611"}]}""",
            """{"type":"steps","title":"Climb","steps":["Everest was climbed","in May 1953"]}""",
            """{"type":"compare","title":"Two peaks","items":[{"name":"Everest","points":["8,849 m high"]},{"name":"K2","points":["8,611 m"]}]}""",
            """{"type":"facts","title":"Everest","facts":[{"label":"Height","value":"8,849 m"},{"label":"First climbed","value":"29 May 1953"}]}""",
        )
        for (raw in old) {
            assertTrue(raw, CardSpec.parse(raw, peaks) is CardSpec.Parsed.Ok)
            assertTrue(raw, Visual.parse(raw, peaks) is Visual.Parsed.Ok)
        }
    }

    @Test fun `the reply may come in a fence, after reasoning, or with a brace in a string`() {
        val raw = "<think>{\"type\":\"bars\"} maybe</think>Here it is:\n```json\n{\"type\":\"facts\",\"title\":\"Everest {tallest}\",\"facts\":[{\"label\":\"Height\",\"value\":\"8,849 m\"}]}\n```"
        assertEquals("Everest {tallest}", ok(raw, peaks).title)
    }

    @Test fun `none, reserved kinds, unknown types, broken JSON and empty data are refused`() {
        assertEquals(Visual.Reason.NOTHING, reason("""{"type":"none"}""", peaks))
        for (k in listOf("timeline", "slider", "quiz", "converter")) assertEquals(k, Visual.Reason.RESERVED, reason("""{"type":"$k","items":[]}""", peaks))
        assertEquals(Visual.Reason.UNKNOWN_TYPE, reason("""{"type":"map"}""", peaks))
        assertEquals(Visual.Reason.NO_JSON, reason("I think a table would help.", peaks))
        assertEquals(Visual.Reason.NO_JSON, reason("""{"type":"bars","items":[""", peaks))
        assertEquals(Visual.Reason.EMPTY, reason("""{"type":"bars","items":[{"label":"Everest","value":8849}]}""", peaks))
        assertEquals(Visual.Reason.EMPTY, reason("""{"type":"steps","steps":["only one"]}""", peaks))
    }

    @Test fun `a share needs parts that can make a whole`() {
        assertEquals(Visual.Reason.BAD_SHARE, reason("""{"type":"share","items":[{"label":"Everest","value":-8849},{"label":"K2","value":8611}]}""", "Everest -8849 K2 8611"))
        assertEquals(Visual.Reason.BAD_SHARE, reason("""{"type":"share","items":[{"label":"Everest","value":0},{"label":"K2","value":0}]}""", "Everest 0 K2 0"))
        val nine = (1..9).joinToString(",") { """{"label":"Everest","value":$it}""" }
        // More than 8 parts is refused, not cut and drawn as the whole (V-7).
        assertEquals(Visual.Reason.BAD_SHARE, reason("""{"type":"share","items":[$nine]}""", "Everest 1 2 3 4 5 6 7 8 9"))
    }

    // ── Grounding: numbers ──

    @Test fun `a number the answer doesn't write is refused, with the number`() {
        val r = Visual.parse("""{"type":"bars","items":[{"label":"Everest","value":8849},{"label":"K2","value":8612}]}""", peaks)
        assertEquals(Visual.Parsed.Rejected(Visual.Reason.INVENTED_NUMBERS, "8612"), r)
    }

    @Test fun `numbers inside text are checked too`() {
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"facts","facts":[{"label":"Height","value":"8,850 m"}]}""", peaks))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"table","columns":["Peak","Height"],"rows":[["Everest","9,000 m"],["K2","8,611 m"]]}""", peaks))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"steps","steps":["Everest climbed in 1952","then K2"]}""", peaks))
    }

    @Test fun `a computed total or percentage is refused`() {
        // 8849 + 8611 = 17460: the model added; the app computes totals, not the model.
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"facts","facts":[{"label":"Everest and K2","value":"17,460 m"}]}""", peaks))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"facts","facts":[{"label":"Everest share","value":"51%"}]}""", peaks))
    }

    @Test fun `numbers written in any usual way are grounded`() {
        val ns = VisualCheck.numbersIn(pt)
        assertTrue(203.1 in ns); assertTrue(203_100_000.0 in ns)
        assertTrue(146.4 in ns); assertTrue(146_400_000.0 in ns)
        assertTrue(17_098_246.0 in ns)
        // A plain space doesn't join numbers ("K2 8611", validator V-1); a no-break space does.
        assertFalse(1234.0 in VisualCheck.numbersIn("1 234 people"))
        assertTrue(1234.0 in VisualCheck.numbersIn("1\u00a0234 people"))
        assertTrue(1234.0 in VisualCheck.numbersIn("1,234 people"))
        assertTrue(3.5 in VisualCheck.numbersIn("3,5 graus"))
        assertTrue(12.0 in VisualCheck.numbersIn("12% of it"))
        assertTrue(1_500_000.0 in VisualCheck.numbersIn("1.5 million people"))
        assertTrue(9.5 in VisualCheck.numbersIn("magnitude M9.5 quake"))
        // A list stays separate numbers, and a date's dashes aren't minus signs.
        assertEquals(setOf(1913.0, 1914.0), VisualCheck.numbersIn("in 1913, 1914").filter { it > 1000 }.toSet())
        val date = VisualCheck.numbersIn("on 1913-03-12")
        assertTrue(3.0 in date && 12.0 in date && -3.0 !in date)
        assertTrue(-4.0 in VisualCheck.numbersIn("it fell to -4 degrees"))
    }

    @Test fun `PT answers ground their own numbers`() {
        val v = ok("""{"type":"bars","title":"População","unit":"milhões","items":[{"label":"Brasil","value":203.1},{"label":"Rússia","value":146.4}]}""", pt)
        assertEquals(2, (v as Visual.Series).items.size)
    }

    @Test fun `a year is grounded by a full date, and a full date needs its day`() {
        assertNull(VisualCheck.check(Visual.Facts("", listOf("Everest first climbed" to "1953")), peaks))
        assertNull(VisualCheck.check(Visual.Facts("", listOf("Everest first climbed" to "29 May 1953")), peaks))
        assertNull(VisualCheck.check(Visual.Facts("", listOf("Everest first climbed" to "1953")), "Everest was climbed on 1953-05-29."))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, VisualCheck.check(Visual.Facts("", listOf("Everest first climbed" to "28 May 1953")), peaks)?.first)
    }

    @Test fun `the user's own numbers count`() {
        val v = ok("""{"type":"bars","items":[{"label":"Monday","value":5},{"label":"Tuesday","value":8}]}""", "Here is the chart.", "Monday:5, Tuesday:8")
        assertEquals(2, (v as Visual.Series).items.size)
    }

    // ── Grounding: labels ──

    @Test fun `a label with no word from the answer is refused`() {
        val r = Visual.parse("""{"type":"bars","items":[{"label":"Everest","value":8849},{"label":"Aconcagua","value":8611}]}""", peaks)
        assertEquals(Visual.Parsed.Rejected(Visual.Reason.UNGROUNDED_LABELS, "Aconcagua"), r)
    }

    @Test fun `labels match across plurals, accents and case`() {
        assertNull(VisualCheck.check(Visual.Steps("", listOf("Volcanoes erupt", "Magma rises")), "A volcano forms where magma rises."))
        assertNull(VisualCheck.check(Visual.Facts("", listOf("POPULACAO" to "203,1 milhões")), pt))
        assertNull(VisualCheck.check(Visual.Facts("", listOf("Rússia" to "146.4 milhões")), pt))
    }

    @Test fun `headings are the model's own words and aren't checked, their numbers are`() {
        assertNull(VisualCheck.check(Visual.Facts("A wholly new heading", listOf("Height" to "8,849 m")), peaks))
        assertNull(VisualCheck.check(Visual.Table("", listOf("Peak", "Elevation"), listOf(listOf("Everest", "8,849 m"))), peaks))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, VisualCheck.check(Visual.Facts("", listOf("Top 3 peaks" to "Everest")), peaks)?.first)
        assertEquals(Visual.Reason.UNGROUNDED_LABELS, VisualCheck.check(Visual.Facts("", listOf("Height" to "Aconcagua")), peaks)?.first)
    }

    // ── What the app computes ──

    @Test fun `shares add up to exactly 100`() {
        assertEquals(listOf(34, 33, 33), VisualScale.percents(listOf(1.0, 1.0, 1.0)))
        assertEquals(100, VisualScale.percents(listOf(8849.0, 8611.0, 8586.0)).sum())
        assertEquals(listOf(13, 21, 26, 40), VisualScale.percents(listOf(5.0, 8.0, 10.0, 15.5)))
        assertEquals(listOf(0, 0), VisualScale.percents(listOf(0.0, 0.0)))
    }

    @Test fun `axes start at zero for bars and fit the data for a line, at round steps`() {
        val bars = VisualScale.axis(listOf(8586.0, 8611.0, 8849.0), fromZero = true)
        assertEquals(0.0, bars.min, 0.0); assertEquals(10000.0, bars.max, 0.0); assertEquals(2500.0, bars.step, 0.0)
        val line = VisualScale.axis(listOf(8586.0, 8611.0, 8849.0), fromZero = false)
        assertTrue(line.min <= 8586 && line.min > 8000); assertTrue(line.max >= 8849)
        assertTrue(listOf(1.0, 2.0, 2.5, 5.0).any { kotlin.math.abs(line.step / Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(line.step))) - it) < 1e-9 })
        assertEquals(listOf(0.0, 2500.0, 5000.0, 7500.0, 10000.0), bars.ticks)
        val flat = VisualScale.axis(listOf(5.0, 5.0), fromZero = false)
        assertTrue(flat.max > flat.min)
    }

    @Test fun `the same data switches view with no new model call`() {
        val s = Visual.Series("Peaks", Visual.SeriesKind.BARS, "m", listOf("Everest" to 8849.0, "K2" to 8611.0, "Kangchenjunga" to 8586.0))
        // A pie only for parts of a whole the model named (V-6): heights aren't shares.
        assertEquals(listOf("bars", "line", "table"), VisualScale.kinds(s))
        assertEquals(listOf("bars", "line", "share", "table"), VisualScale.kinds(s.copy(kind = Visual.SeriesKind.SHARE)))
        assertEquals(listOf("bars", "share", "table"), VisualScale.kinds(s.copy(kind = Visual.SeriesKind.SHARE, items = s.items.take(2))))
        assertEquals(listOf("bars", "line", "table"), VisualScale.kinds(s.copy(items = listOf("a" to -1.0, "b" to 2.0, "c" to 3.0))))
        assertEquals(Visual.SeriesKind.LINE, VisualScale.asKind(s, Visual.SeriesKind.LINE)?.kind)
        assertNull(VisualScale.asKind(s.copy(items = listOf("a" to -1.0, "b" to 2.0)), Visual.SeriesKind.SHARE))
        assertEquals(listOf(listOf("Everest", "8849"), listOf("K2", "8611"), listOf("Kangchenjunga", "8586")), VisualScale.asTable(s).rows)
    }

    @Test fun `copy gives plain Markdown, shares with the app's percents`() {
        val share = Visual.Series("Stock", Visual.SeriesKind.SHARE, null, listOf("Monday" to 5.0, "Tuesday" to 8.0, "Wednesday" to 10.0, "Thursday" to 15.5))
        assertEquals("**Stock**\n\n- Monday: 5 (13%)\n- Tuesday: 8 (21%)\n- Wednesday: 10 (26%)\n- Thursday: 15.5 (40%)", VisualScale.markdown(share))
        val table = Visual.Table("", listOf("Peak", "Height"), listOf(listOf("Everest", "8,849 m")))
        assertEquals("| Peak | Height |\n| --- | --- |\n| Everest | 8,849 m |", VisualScale.markdown(table))
        assertEquals("1. a\n2. b", VisualScale.markdown(Visual.Steps("", listOf("a", "b"))))
    }

    @Test fun `numbers print without trailing zeros or exponents`() {
        assertEquals("1500", VisualScale.format(1500.0))
        assertEquals("3.25", VisualScale.format(3.25))
        assertEquals("203100000", VisualScale.format(2.031e8))
        assertEquals("-4", VisualScale.format(-4.0))
    }

    /** The X6's Brazil and Russia answer (Nov 10) and the table Qwen3-1.7B wrote from it, most of it from memory. */
    private val x6Answer = "Brazil, on the other hand, is one of the ten chief industrial states in the world according to the International " +
        "Labour Organization. It has a population of approximately 210 million, making it the second-most populous country in the world. " +
        "Brazil's economy is more diversified. The country's nominal GDP was $2.642 trillion in 2024, and its GDP per capita was $12,313. " +
        "Brazil's economy is less dependent on a single sector compared to Russia's. Russia's economy is more volatile due to its reliance on oil and gas."
    private val x6Table = """{"type":"table","title":"Brazil and Russia compared: population, area and economy",
        "columns":["Country","Population (millions)","Area (km²)","Economy (Nominal GDP, ${'$'}trillion)","Economy (GDP per capita, ${'$'})","Economy (Oil/Gas % of Budget)"],
        "rows":[["Brazil","210","8.5","2.642","12,313","30"],["Russia","140","11.5","2.642","11,633","50"]]}"""

    @Test fun `the X6 table keeps what the answer backs and says what it left out`() {
        val r = Visual.parse(x6Table, x6Answer) as Visual.Parsed.Ok
        val t = r.visual as Visual.Table
        assertEquals(listOf("Country", "Population (millions)", "Economy (Nominal GDP, ${'$'}trillion)", "Economy (GDP per capita, ${'$'})"), t.columns)
        assertEquals(listOf(listOf("Brazil", "210", "2.642", "12,313")), t.rows)
        assertTrue(r.leftOut.containsAll(listOf("140", "11.5", "11,633", "50", "8.5", "30")))
    }

    @Test fun `a number in the sources is backed too, so it stays`() {
        val sources = "Brazil covers an area of 8.5 million square kilometres."
        val r = Visual.parse(x6Table, x6Answer + "\n" + sources) as Visual.Parsed.Ok
        assertTrue((r.visual as Visual.Table).columns.contains("Area (km²)"))
        assertFalse(r.leftOut.contains("8.5"))
    }

    @Test fun `empty cells come out with their line, not left blank`() {
        val t = """{"type":"table","title":"","columns":["Country","Population","Area"],"rows":[["Brazil","210 million",""],["Russia","146 million",""]]}"""
        val v = (Visual.parse(t, "Brazil has 210 million people and Russia 146 million.") as Visual.Parsed.Ok).visual as Visual.Table
        assertEquals(listOf("Country", "Population"), v.columns)
        assertEquals(2, v.rows.size)
    }

    @Test fun `a chart drops the item not in the texts and keeps the rest`() {
        val r = Visual.parse("""{"type":"bars","unit":"m","items":[{"label":"Everest","value":8849},{"label":"K2","value":8611},{"label":"Kangchenjunga","value":8600}]}""", peaks) as Visual.Parsed.Ok
        assertEquals(listOf("Everest", "K2"), (r.visual as Visual.Series).items.map { it.first })
        assertEquals(listOf("8600"), r.leftOut)
    }

    @Test fun `too little left is still refused, with what failed first`() {
        assertEquals(Visual.Parsed.Rejected(Visual.Reason.INVENTED_NUMBERS, "8612"),
            Visual.parse("""{"type":"bars","items":[{"label":"Everest","value":8849},{"label":"K2","value":8612}]}""", peaks))
        val all = """{"type":"table","columns":["Country","Area"],"rows":[["Brazil","8.5"],["Russia","11.5"]]}"""
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason(all, x6Answer))
    }

    @Test fun `a unit must come from the answer too`() {
        val answer = "Brazil's GDP was $2.642 trillion and Russia's $2.2 trillion. Brazil covers 8,515,767 km²."
        val gdp = """{"type":"bars","title":"GDP","unit":"trillion US$","items":[{"label":"Brazil","value":2.642},{"label":"Russia","value":2.2}]}"""
        assertTrue(Visual.parse(gdp.replace("trillion US$", "trillion $"), answer) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNGROUNDED_LABELS, reason(gdp.replace("trillion US$", "billion $"), answer))
        assertEquals(Visual.Reason.UNGROUNDED_LABELS, reason(gdp.replace("trillion US$", "trillion €"), answer))
        assertTrue(VisualCheck.unitOk("km²", answer))
        assertTrue(VisualCheck.unitOk("trillion", answer))
        assertFalse(VisualCheck.unitOk("million km²", answer))
        assertTrue(VisualCheck.unitOk("milhões", "A população é de 203,1 milhões."))
        assertTrue(VisualCheck.unitOk("m", "Mount Everest is 8,849 m high."))
    }

    @Test fun `the prompt puts the rules first, units included, and lets a missing value stay empty`() {
        assertTrue(Visual.PROMPT.indexOf("STRICT RULES") < Visual.PROMPT.indexOf("\"type\":\"bars\""))
        assertTrue(Visual.PROMPT.contains("empty string"))
        assertTrue(Visual.PROMPT.contains("at most 3 columns"))
        assertTrue(Visual.PROMPT.contains("Never convert units"))
        assertFalse(Visual.PROMPT.contains("another country"))
    }

    @Test fun `the prompt asks for data, never for computed numbers`() {
        assertTrue(Visual.PROMPT.contains("\"type\":\"line\""))
        assertTrue(Visual.PROMPT.contains("\"type\":\"share\""))
        assertTrue(Visual.PROMPT.contains("no percentages"))
        assertTrue(Visual.PROMPT.contains("{\"type\":\"none\"}"))
        assertFalse(Visual.PROMPT.contains("timeline"))
    }

    // ── Validator gate, Nov 10 ──

    @Test fun `V-1 a space doesn't join two numbers`() {
        val ns = VisualCheck.numbersIn("K2 8611 m and Apollo 11 1969")
        assertTrue(8611.0 in ns && 2.0 in ns && 11.0 in ns && 1969.0 in ns)
        assertFalse(2861.0 in ns)
        assertTrue(1234.0 in VisualCheck.numbersIn("1\u00a0234 people"))
        assertTrue(1234.0 in VisualCheck.numbersIn("1\u202f234 people"))
        // The old saved K2 card reads again.
        assertTrue(Visual.parse("""{"type":"bars","unit":"m","items":[{"label":"Everest","value":8849},{"label":"K2","value":8611}]}""",
            "Everest is 8,849 m and K2 8611 m.") is Visual.Parsed.Ok)
    }

    @Test fun `V-2 a number is read the way it's written`() {
        assertEquals(listOf(3.5), VisualCheck.readings("3,5"))
        assertEquals(listOf(1234.0), VisualCheck.readings("1,234"))
        assertEquals(listOf(1.5), VisualCheck.readings("1.5"))
        assertEquals(listOf(1.234, 1234.0), VisualCheck.readings("1.234"))
        assertEquals(listOf(1234567.0), VisualCheck.readings("1.234.567"))
        assertEquals(listOf(1234.5), VisualCheck.readings("1.234,5"))
        assertFalse(35.0 in VisualCheck.numbersIn("3,5 graus"))
        assertFalse(15.0 in VisualCheck.numbersIn("1,5 milhão"))
        assertTrue(1_500_000.0 in VisualCheck.numbersIn("1,5 milhão"))
        assertFalse(15_000_000.0 in VisualCheck.numbersIn("1.5 million"))
        // A value written as text in the reply is read the same way.
        val v = (Visual.parse("""{"type":"bars","unit":"graus","items":[{"label":"Lisboa","value":"3,5"},{"label":"Porto","value":"2,5"}]}""", "Lisboa 3,5 graus, Porto 2,5 graus.") as Visual.Parsed.Ok).visual as Visual.Series
        assertEquals(listOf(3.5, 2.5), v.items.map { it.second })
    }

    @Test fun `V-3 a number must stand by its own label`() {
        val answer = "Paris has 2.1 million people, Lyon 0.5 million."
        val swapped = """{"type":"bars","unit":"million","items":[{"label":"Paris","value":0.5},{"label":"Lyon","value":2.1}]}"""
        assertEquals(Visual.Reason.UNPAIRED, reason(swapped, answer))
        val right = """{"type":"bars","unit":"million","items":[{"label":"Paris","value":2.1},{"label":"Lyon","value":0.5}]}"""
        assertTrue(Visual.parse(right, answer) is Visual.Parsed.Ok)
        // The X6 table still keeps Brazil's row: "It has 210 million" names Brazil a sentence before.
        assertTrue(((Visual.parse(x6Table, x6Answer) as Visual.Parsed.Ok).visual as Visual.Table).rows.single().first() == "Brazil")
        // A table row swapped with another's number is left out.
        val t = """{"type":"table","columns":["City","People"],"rows":[["Paris","0.5 million"],["Lyon","2.1 million"],["Marseille","0.9 million"]]}"""
        assertEquals(Visual.Reason.UNPAIRED, reason(t, answer + " Marseille 0.9 million."))
    }

    // Validator, Nov 11: correct data written number-first was refused, and so were labels that are prefixes of others.
    @Test fun `V-3 the number before its name is paired with that name, and a swap is still refused`() {
        fun bars(a: Double, b: Double, la: String = "Paris", lb: String = "Lyon", unit: String = "million") =
            """{"type":"bars","unit":"$unit","items":[{"label":"$la","value":$a},{"label":"$lb","value":$b}]}"""
        for (answer in listOf(
            "2.1 million people live in Paris and 0.5 million in Lyon.",
            "2.1 million (Paris), 0.5 million (Lyon).",
        )) {
            assertTrue(answer, Visual.parse(bars(2.1, 0.5), answer) is Visual.Parsed.Ok)
            assertEquals(answer, Visual.Reason.UNPAIRED, reason(bars(0.5, 2.1), answer))
        }
        val pt = "2,1 milhões de pessoas vivem em Paris e 0,5 milhão em Lyon."
        assertTrue(Visual.parse(bars(2.1, 0.5, unit = "milhões"), pt) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason(bars(0.5, 2.1, unit = "milhões"), pt))
        // Name first still works both ways.
        val nameFirst = "Paris has 2.1 million people, Lyon 0.5 million."
        assertTrue(Visual.parse(bars(2.1, 0.5), nameFirst) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason(bars(0.5, 2.1), nameFirst))
    }

    // Validator Q-A3 (t9 0115): one sentence per item, each starting with its number, refused the right card 3 of 3.
    @Test fun `Q-A3 a sentence that starts with its number is its own, and a swap is still refused`() {
        fun bars(vararg p: Pair<String, Double>) = """{"type":"bars","unit":"million","items":[${p.joinToString(",") { """{"label":"${it.first}","value":${it.second}}""" }}]}"""
        val three = "2.1 million people live in Paris. 0.5 million people live in Lyon. 0.9 million people live in Marseille."
        assertTrue(Visual.parse(bars("Paris" to 2.1, "Lyon" to 0.5, "Marseille" to 0.9), three) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason(bars("Paris" to 0.5, "Lyon" to 2.1, "Marseille" to 0.9), three))
        assertEquals(Visual.Reason.UNPAIRED, reason(bars("Paris" to 2.1, "Lyon" to 0.9, "Marseille" to 0.5), three))
        // Mixed orders, each sentence its own (CodeRabbit on all73).
        val mixed = "Paris has 2.1 million people. 0.5 million people live in Lyon. Marseille has 0.9 million people."
        val kept = (Visual.parse(bars("Paris" to 2.1, "Lyon" to 0.5, "Marseille" to 0.9), mixed) as Visual.Parsed.Ok).visual as Visual.Series
        assertEquals(3, kept.items.size)
        assertEquals(Visual.Reason.UNPAIRED, reason(bars("Paris" to 0.5, "Lyon" to 2.1, "Marseille" to 0.9), mixed))
        val two = "2.1 million people live in Paris. 0.5 million live in Lyon."
        assertTrue(Visual.parse(bars("Paris" to 2.1, "Lyon" to 0.5), two) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason(bars("Paris" to 0.5, "Lyon" to 2.1), two))
    }

    @Test fun `V-3 a label inside a longer one isn't confused with it`() {
        val sudan = "South Sudan has 11 million people and Sudan has 48 million."
        assertTrue(Visual.parse("""{"type":"bars","unit":"million","items":[{"label":"Sudan","value":48},{"label":"South Sudan","value":11}]}""", sudan) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason("""{"type":"bars","unit":"million","items":[{"label":"Sudan","value":11},{"label":"South Sudan","value":48}]}""", sudan))
        val parts = "Part10 weighs 40 kg and Part1 weighs 7 kg."
        assertTrue(Visual.parse("""{"type":"bars","unit":"kg","items":[{"label":"Part1","value":7},{"label":"Part10","value":40}]}""", parts) is Visual.Parsed.Ok)
        assertEquals(Visual.Reason.UNPAIRED, reason("""{"type":"bars","unit":"kg","items":[{"label":"Part1","value":40},{"label":"Part10","value":7}]}""", parts))
    }

    @Test fun `V-4 a number in the title or the unit must be backed too`() {
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"bars","title":"Peaks in 2099","unit":"m","items":[{"label":"Everest","value":8849},{"label":"K2","value":8611}]}""", peaks))
        assertEquals(Visual.Reason.INVENTED_NUMBERS, reason("""{"type":"bars","unit":"per 2099 m","items":[{"label":"Everest","value":8849},{"label":"K2","value":8611}]}""", peaks))
        assertTrue(Visual.parse("""{"type":"bars","title":"Climbed since 1953","unit":"m","items":[{"label":"Everest","value":8849},{"label":"K2","value":8611}]}""", peaks) is Visual.Parsed.Ok)
    }
}
