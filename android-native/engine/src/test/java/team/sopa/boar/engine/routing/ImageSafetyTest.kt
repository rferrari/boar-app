package team.sopa.boar.engine.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSafetyTest {
    @Test fun `blocks explicit and violent subjects in English and Portuguese`() {
        for (s in listOf("a nude woman on a beach", "NSFW art", "sexy lingerie model", "pornographic scene", "a bloody corpse",
            "beheading", "a man shooting a rifle", "weapons on a table", "mulher pelada", "cena de sexo", "cadáver sangrento",
            "uma arma de fogo", "metralhadora", "uma pessoa sem roupa"))
            assertTrue(s, ImageSafety.blocked(s))
    }

    @Test fun `catches spaced-out and dotted letters`() {
        for (s in listOf("n u d e", "n.u.d.e", "g-o-r-e", "w e a p o n s", "P O R N"))
            assertTrue(s, ImageSafety.blocked(s))
    }

    @Test fun `lets harmless subjects through`() {
        for (s in listOf("breast cancer awareness ribbon", "grilled chicken breast", "a gun dog retriever", "a blood orange on a table",
            "red blood cells under a microscope", "a volcano erupting over the ocean at dusk", "a charcoal grill outdoors far from a tent",
            "heavy monsoon rain over green rice fields", "peito de frango grelhado", "a hot glue gun", "a naturalist sketching birds",
            "Sussex countryside", "an armadillo", "armadura medieval de cavaleiro", "a great tit on a branch",
            "a shooting star over the desert", "rodovia Transamazônica", "a bath bomb fizzing"))
            assertFalse(s, ImageSafety.blocked(s))
    }

    @Test fun `a blocked pick from the model becomes a calm landscape`() {
        assertEquals(ImageSafety.SAFE_SUBJECT, ImageSafety.safeSubject("a naked person"))
        assertEquals("a red fox in snow", ImageSafety.safeSubject("a red fox in snow"))
    }
}
