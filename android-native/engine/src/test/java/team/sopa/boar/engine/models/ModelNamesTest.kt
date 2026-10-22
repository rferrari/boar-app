package team.sopa.boar.engine.models

import org.junit.Assert.assertEquals
import org.junit.Test

/** The raw file names seen in Settings and the header on the X6 (Oct 13). */
class ModelNamesTest {
    @Test fun readableNames() {
        assertEquals("Meta Llama 3.1 8B Instruct · Q4_K_M", ModelNames.readable("Meta-Llama-3.1-8B-Instruct-Q4_K_M"))
        assertEquals("Mixtral 7Bx2 MoE 13B · IQ1_S", ModelNames.readable("Mixtral_7Bx2_MoE_13B.i1-IQ1_S"))
        assertEquals("Lfm2 2.6B uncensored x64 · Q4_K_M", ModelNames.readable("lfm2-2.6b-uncensored-x64-q4_k_m"))
        assertEquals("Qwen3 4B Instruct 2507 · Q4_K_M", ModelNames.readable("Qwen3-4B-Instruct-2507-Q4_K_M.gguf"))
        assertEquals("Phi 3.5 mini · F16", ModelNames.readable("phi-3.5-mini-f16"))
        assertEquals("Tiny", ModelNames.readable("tiny"))
    }
}
