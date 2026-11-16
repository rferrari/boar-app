package team.sopa.boar.engine.answer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import team.sopa.boar.engine.answer.LabRanges.Mark

/** t9 2330 b and a: adam's fictional blood test, marked in code; the summary instruction. */
class LabRangesTest {
    private val blood = "Blood test results — March 2026\n\nPatient: Anna Reed (fictional document, for BOAR tests). Vida Clinical Lab.\n\n" +
        "| Test | Result | Reference |\n|---|---|---|\n| Fasting glucose | 98 mg/dL | 70 to 99 mg/dL |\n| Total cholesterol | 231 mg/dL | below 190 mg/dL |\n" +
        "| LDL | 162 mg/dL | below 130 mg/dL |\n| HDL | 48 mg/dL | above 40 mg/dL |\n| Triglycerides | 140 mg/dL | below 150 mg/dL |\n| Vitamin D | 24 ng/mL | 30 to 100 ng/mL |\n\n" +
        "Note from Dr. Helen Price: LDL above target; repeat in 6 months, with diet changes and daily walks."

    @Test fun `adam's blood test - each result marked as the lab would`() {
        assertEquals(listOf("Fasting glucose" to Mark.WITHIN, "Total cholesterol" to Mark.ABOVE, "LDL" to Mark.ABOVE, "HDL" to Mark.WITHIN,
            "Triglycerides" to Mark.WITHIN, "Vitamin D" to Mark.BELOW), LabRanges.rows(blood).map { it.name to it.mark })
        // Values and ranges as written, units kept.
        assertEquals("231 mg/dL" to "below 190 mg/dL", LabRanges.rows(blood)[1].let { it.value to it.range })
    }

    @Test fun `the same in Portuguese, as lines too`() {
        val pt = "Exame de sangue\n| Exame | Resultado | Referência |\n|---|---|---|\n| Glicose | 98 mg/dL | de 70 a 99 mg/dL |\n" +
            "| Colesterol total | 231 mg/dL | abaixo de 190 mg/dL |\n| HDL | 48 mg/dL | acima de 40 mg/dL |\n| Vitamina D | 24 ng/mL | 30 a 100 ng/mL |"
        assertEquals(listOf(Mark.WITHIN, Mark.ABOVE, Mark.WITHIN, Mark.BELOW), LabRanges.rows(pt).map { it.mark })
        val lines = "- LDL: 162 mg/dL (below 130 mg/dL)\n- HDL: 48 mg/dL (above 40 mg/dL)"
        assertEquals(listOf(Mark.ABOVE, Mark.WITHIN), LabRanges.rows(lines).map { it.mark })
        assertTrue(LabRanges.note(pt, pt = true)!!.contains("Colesterol total: 231 mg/dL, ACIMA da faixa (abaixo de 190 mg/dL)"))
    }

    @Test fun `a row the code can't read is left unmarked, never guessed`() {
        assertNull(LabRanges.mark("231 mg/dL", "see your doctor"))
        assertNull(LabRanges.mark("6.0 mmol/L", "below 190 mg/dL"))
        assertNull(LabRanges.mark("positive", "negative"))
        assertNull(LabRanges.note("No results here, just a note.", pt = false))
        // The block says it is BOAR's, not the file's.
        assertTrue(LabRanges.note(blood, pt = false)!!.startsWith("Computed by BOAR from this file's own numbers (not written in the file)"))
    }

    @Test fun `a summary asks for the model's words and the code's marks (a)`() {
        for (q in listOf("hows health blood test? summarize", "summarize my blood test", "resuma meu exame de sangue", "me dá um resumo do exame"))
            assertTrue(q, PersonalFiles.asksSummary(q))
        assertTrue(!PersonalFiles.asksSummary("what is my LDL?"))
        assertTrue(PersonalFiles.SUMMARY_EN.contains("never copy the file") && PersonalFiles.SUMMARY_EN.contains("say that mark exactly"))
        val vm = java.io.File("../app/src/main/java/team/sopa/boar/chat/ChatViewModel.kt").readText()
        assertTrue(vm.contains("if (personal && PersonalFiles.asksSummary(q))") && vm.contains("team.sopa.boar.engine.answer.LabRanges.note(c.body, ptQ)"))
    }
}
