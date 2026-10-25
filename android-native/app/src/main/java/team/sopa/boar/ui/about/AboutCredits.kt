package team.sopa.boar.ui.about

/**
 * The open work BOAR is built on, for About's credits: the libraries in the app, by name and license (android-native
 * NOTICE has the same list with links). Models and packs come from the catalog, each with its own license. A build
 * with more components (Boar Labs and its engines) adds them to [extra].
 */
object AboutCredits {
    val libraries: List<Pair<String, String>> = listOf(
        "llama.cpp / ggml" to "MIT",
        "SQLite" to "public domain",
        "Zstandard" to "BSD",
        "PdfBox-Android" to "Apache-2.0",
        "AndroidLM (knowledge-pack search design)" to "Apache-2.0",
        "Baloo 2 and Lexend fonts" to "SIL OFL 1.1",
    )

    /**
     * What BOAR reads and where its models come from (owner, Oct 24), credited next to the code it runs on: string keys
     * for the name (it's translated) and the license; Hugging Face hosts, it isn't a license, so "—".
     */
    val sources: List<Pair<String, String>> = listOf(
        "native.about.creditWikipedia" to "CC BY-SA 4.0",
        "native.about.creditHuggingFace" to "—",
    )

    /** Set at start by a build that ships more (Boar Labs: its engines and models). */
    @Volatile var extra: () -> List<Pair<String, String>> = { emptyList() }
}
