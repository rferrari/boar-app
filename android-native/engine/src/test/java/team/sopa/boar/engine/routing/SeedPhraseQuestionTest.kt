package team.sopa.boar.engine.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seed-phrase and wallet-backup questions answer in Succinct even when Detailed is picked (prompt test plan, Oct 18). */
class SeedPhraseQuestionTest {
    @Test fun everyTriggerRoutes() {
        listOf(
            "What is a seed phrase in a crypto wallet?",
            "Where should I keep my seedphrase?",
            "How do I store a recovery phrase safely?",
            "Is a mnemonic phrase the same as a password?",
            "What happens if I lose the mnemonic for my wallet?",
            "Should I write down the 12 words of my wallet?",
            "Is it safe to photograph my 24-word bitcoin backup?",
            "Can someone steal my coins with my private key?",
            "What is the best way to do a wallet backup?",
            "How do I back up my wallet?",
            "How do I backup my hardware wallet?",
            "O que é uma frase semente?",
            "Onde guardar a frase de recuperação?",
            "Onde guardar a frase de recuperacao?",
            "Perdi as palavras de recuperação, e agora?",
            "Posso tirar foto das 12 palavras da minha carteira?",
            "As 24 palavras do bitcoin ficam no celular?",
            "O que é uma chave privada?",
            "Como fazer backup da carteira?",
        ).forEach { assertTrue(it, QueryContext.isSeedPhraseQuestion(it)) }
    }

    @Test fun walletsWithoutABackupOrSecretDoNot() {
        listOf(
            "What's a wallet?",
            "wallet for coins",
            "best leather wallet",
            "What is a hardware wallet?",
            // 12/24 words and "mnemonic" need a wallet or a coin nearby.
            "Write a poem in 12 words",
            "A mnemonic for the planets",
            "Resuma em 24 palavras",
            "Why do we have seasons?",
        ).forEach { assertFalse(it, QueryContext.isSeedPhraseQuestion(it)) }
    }
}
