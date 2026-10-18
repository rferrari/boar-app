package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from src/routing/verdictGuard.test.ts in r4to's PR #85 (BOAR R&D, MIT), case for case. The answers are the 4B's own. */
class VerdictGuardTest {
    private fun vg(q: String, a: String) = VerdictGuard.check(q, a)
    private fun startsWith(prefix: String, q: String, a: String) {
        val t = vg(q, a)?.text
        assertTrue("$q -> $t", t != null && t.startsWith(prefix))
    }

    // ---- flags a verdict its own facts contradict (VG, cut 3 dev) ----

    @Test fun `(a) date threshold, No before 1970 plus first in 1969 gives Yes, with the facts kept`() {
        val r = vg(
            "Did astronauts walk on the Moon before 1970?",
            "No, astronauts did not walk on the Moon before 1970. The first human moonwalk occurred on July 20, 1969, during NASA's Apollo 11 mission. No lunar surface walks took place before that date.",
        )
        assertEquals("date", r?.kind)
        assertEquals(
            "Yes, astronauts did walk on the Moon before 1970. The first human moonwalk occurred on July 20, 1969, during NASA's Apollo 11 mission. No lunar surface walks took place before that date.",
            r?.text,
        )
    }

    @Test fun `(a) drops later restatements of the old verdict that name the threshold`() {
        val r = vg(
            "Did astronauts walk on the Moon before 1970?",
            "No, astronauts did not walk on the Moon before 1970.\n\nThe first human beings to walk on the Moon were Neil Armstrong and Buzz Aldrin on July 20, 1969.\n\nTherefore, there were no lunar surface visits by astronauts before 1970.",
        )
        assertFalse(r!!.text.contains("Therefore, there were no"))
        assertTrue(r.text.startsWith("Yes, astronauts did walk"))
    }

    @Test fun `(a) a consistent answer and the after case it can't decide are left alone`() {
        assertNull(vg("Did astronauts walk on the Moon before 1970?", "Yes, astronauts walked on the Moon before 1970 [2]. The first lunar walk occurred during Apollo 11 in July 1969."))
        assertEquals("date", vg("Did astronauts walk on the Moon after 1960?", "No. The first moonwalk was in 1969.")?.kind)
        assertNull(vg("Did people use the telephone after 1960?", "No. The first telephone call was in 1876."))
    }

    @Test fun `(a) Yes when the first time was after the threshold gives No`() {
        assertEquals(
            "No. The first ARPANET message was sent in 1969.",
            vg("Was the internet used before 1960?", "Yes, the internet was used before 1960. The first ARPANET message was sent in 1969.")?.text,
        )
    }

    @Test fun `(b) which-form comparison, the verdict names the smaller number, rewritten with the difference and ratio`() {
        val r = vg(
            "Which is more densely populated, Japan or Germany, and by how much?",
            "Germany is more densely populated than Japan. As of 2023, Germany has a population density of about 230 people per square kilometer, while Japan's is approximately 339 people per square kilometer. Japan has a higher population density due to its small land area and high population concentration in urban centers like Tokyo and Osaka. \n\n(Note: This response contains a factual error—Japan actually has a higher population density than Germany. Correcting: Japan’s density is about 339/km², Germany’s is about 230/km². So Japan is more densely populated by about 109 people per km².)\n\nCorrected: Japan is more densely populated than Germany, with a density of about 339 people per square kilometer compared to Germany’s 230 people per square kilometer—about 109 more per km².",
        )
        assertEquals("compare", r?.kind)
        assertEquals(
            "Japan is more densely populated than Germany, by about 109 people per km² (339 versus 230, about 1.5 times as much). As of 2023, Germany has a population density of about 230 people per square kilometer, while Japan's is approximately 339 people per square kilometer. Japan has a higher population density due to its small land area and high population concentration in urban centers like Tokyo and Osaka.",
            r?.text,
        )
    }

    @Test fun `(b) yes-no comparison with the wrong Yes or No, flipped, facts kept`() {
        assertEquals(
            "Yes. Polar bears average 1,000 to 1,600 pounds. Grizzly bears average 400 to 600 pounds.",
            vg("Is a polar bear bigger than a grizzly bear?", "No, a polar bear is not bigger than a grizzly bear. Polar bears average 1,000 to 1,600 pounds. Grizzly bears average 400 to 600 pounds.")?.text,
        )
    }

    @Test fun `(b) guard items stay untouched, consistent verdicts, overlapping ranges, no figures`() {
        val keep = listOf(
            "Does Brazil have more people than Russia?" to "Yes, Brazil has more people than Russia. As of recent estimates, Brazil's population is approximately 214 million, while Russia's is around 141 million. Therefore, Brazil has a larger population than Russia.",
            "Is a polar bear bigger than a grizzly bear?" to "Yes, polar bears are generally larger than grizzly bears [1]. On average, polar bears have a greater body mass and length, especially in males.",
            "Which weighs more on average, an adult male moose or an adult male American bison?" to "An adult male moose averages about 1,500 lb (700 kg), while an adult male American bison typically weighs around 1,500 to 2,000 lb (680–900 kg). Therefore, the American bison weighs more on average than the adult male moose [1].",
            "Which grows bigger, the saltwater crocodile or the Nile crocodile?" to "Saltwater crocodiles grow bigger than Nile crocodiles. On average, saltwater crocodiles can reach lengths of 5 to 6 meters (16 to 20 feet), while Nile crocodiles typically max out at 4 to 5 meters (13 to 16 feet).",
            "If you stood the Burj Khalifa on the deepest point of Lake Baikal, would its top stick out of the water?" to "The Burj Khalifa is about 828 meters tall, and Lake Baikal's deepest point is about 1,642 meters below sea level. So, no, its top would not stick out.",
            "How big is Iceland in square kilometres?" to "Iceland has a total area of approximately 103,000 square kilometres.",
        )
        for ((q, a) in keep) assertNull(q, vg(q, a))
    }

    @Test fun `(b) different units are never compared`() {
        assertNull(vg("Which is longer, the Nile or the Amazon?", "The Amazon is longer than the Nile. The Nile is about 6,650 km long, while the Amazon is about 4,000 miles long."))
    }

    @Test fun `(c) rank, No country is bigger next to seventh-largest gives six, with the list when the answer gives it`() {
        assertEquals(
            VerdictGuard.Result("rank", "Six countries are bigger than India by area. India is the seventh-largest country in the world by land area."),
            vg("Which countries are bigger than India by area?", "No country is bigger than India by area [1]. India is the seventh-largest country in the world by land area."),
        )
        assertEquals(
            "Six countries are bigger than India by area: Russia, Canada, China, the United States, Brazil and Australia. India is the seventh-largest country in the world by land area, after Russia, Canada, China, the United States, Brazil, and Australia.",
            vg(
                "Which countries are bigger than India by area?",
                "No country is bigger than India by area [1]. India is the seventh-largest country in the world by land area, after Russia, Canada, China, the United States, Brazil, and Australia.",
            )?.text,
        )
    }

    @Test fun `(c) a correct list answer is left alone`() {
        assertNull(vg("Which countries are bigger than India by area?", "Countries larger than India by area include Russia, Canada, and China. India ranks as the seventh-largest country by land area."))
    }

    @Test fun `never fires on questions it doesn't parse, or on empty text`() {
        assertNull(vg("Explain Mozi's views on universal love.", "No, Mozi did not… The first Mohists lived in 400 BC."))
        assertNull(vg("", "No."))
        assertNull(vg("Did X happen before 1970?", ""))
    }

    // ---- v2: the compared quantity, list consistency ----

    @Test fun `(b) a population figure next to a density comparison is not the density, the density figures decide`() {
        // Populations alone (18 M vs 216 M) would wrongly favour Brazil; only the per-km² figures are compared.
        assertNull(vg(
            "Which is more densely populated, the Netherlands or Brazil?",
            "The Netherlands is more densely populated than Brazil. The Netherlands has about 18 million people and a density of about 520 people per km², while Brazil has about 216 million people and about 25 people per km².",
        ))
        // Populations only, no density figure: a quantity is missing, so no flag.
        assertNull(vg(
            "Which is more densely populated, the Netherlands or Brazil?",
            "The Netherlands is more densely populated than Brazil. The Netherlands has about 18 million people, while Brazil has about 216 million people.",
        ))
        // The density figure after a population figure still decides a real contradiction.
        startsWith(
            "Japan is more densely populated than Germany.",
            "Which is more densely populated, Japan or Germany?",
            "Germany is more densely populated than Japan. Germany has 84 million people and 235 people per km², while Japan has 124 million people and 330 people per km².",
        )
    }

    @Test fun `(b) mixed units (km² vs sq mi) are never compared`() {
        assertNull(vg(
            "Which is more densely populated, Japan or Germany?",
            "Germany is more densely populated than Japan. Germany has about 600 people per square mile, while Japan has about 330 people per km².",
        ))
    }

    @Test fun `(b) older needs ages, not years, ranges give no difference or ratio`() {
        assertNull(vg("Which is older, Oxford or Cambridge?", "Cambridge is older than Oxford. Oxford dates from 1096, while Cambridge was founded in 1209."))
        val r = vg(
            "Which is heavier, a polar bear or a grizzly bear, and by how much?",
            "A grizzly bear is heavier than a polar bear. Polar bears weigh 350 to 700 kg, while grizzly bears weigh 180 to 300 kg.",
        )
        assertTrue(r?.text, r!!.text.startsWith("A polar bear is heavier than a grizzly bear. "))
        assertFalse(Regex("times as much|by about").containsMatchIn(r.text))
    }

    @Test fun `(c) a rank answer whose own list disagrees with the new count is left alone`() {
        assertNull(vg(
            "Which countries are bigger than India by area?",
            "No country is larger in area than India. India is the seventh-largest country in the world by area.\n\nCountries larger than India include:\n- Russia\n- Canada\n- United States\n- China\n- Brazil\n- Australia\n- Kazakhstan",
        ))
        assertNull(vg(
            "Which countries are bigger than India by area?",
            "No country is bigger than India by area. India is the seventh-largest country, after Russia, Canada, China and the United States.",
        ))
        // A rank about another subject doesn't count.
        assertNull(vg("Which countries are bigger than India by area?", "No country is bigger than India. China is the third-largest country."))
    }

    // ---- v3: abstain on ambiguous attribution, rank scope or subject, multi-year facts ----

    @Test fun `no flag, P1 sub-region figure, P6b another attribute, P11 regional rank, P12 substring subject, P13 two years`() {
        val cases = listOf(
            Triple("P1", "Which is more densely populated, Japan or Germany?", "Japan is more densely populated than Germany. Japan has about 339 people per km², while in Germany the city-state of Berlin reaches about 4,100 people per km²."),
            Triple("P6b", "Which is bigger, Jupiter or Saturn?", "Jupiter is bigger than Saturn. Jupiter is about 139,820 km wide, while Saturn's rings are about 282,000 km wide."),
            Triple("P11", "Which countries are bigger than Sudan?", "No country is bigger than Sudan. Sudan is the third-largest country in Africa."),
            Triple("P12", "Which countries are bigger than Sudan by area?", "No country is bigger than Sudan by area. South Sudan is the 42nd-largest country."),
            Triple("P13", "Was the Channel Tunnel opened before 1990?", "No, it was not. The first plans for a tunnel date from 1802, but it opened in 1994."),
        )
        for ((id, q, a) in cases) assertNull(id, vg(q, a))
    }

    @Test fun `positive, P2 density chosen over population, P9 elevation, P17 smaller polarity`() {
        startsWith(
            "The Netherlands is more densely populated than Brazil.",
            "Which is more densely populated, the Netherlands or Brazil?",
            "Brazil is more densely populated than the Netherlands. The Netherlands has about 18 million people and about 520 people per km², while Brazil has about 216 million people and about 25 people per km².",
        )
        startsWith("La Paz is higher than Quito. Quito sits at about 2,850 m", "Which is higher, Quito or La Paz?", "Quito is higher than La Paz. Quito sits at about 2,850 m, while La Paz is at about 3,640 m.")
        startsWith(
            "Belgium is smaller than the Netherlands.",
            "Which is smaller, Belgium or the Netherlands?",
            "The Netherlands is smaller than Belgium. Belgium covers about 30,689 km², while the Netherlands covers about 41,850 km².",
        )
    }
}
