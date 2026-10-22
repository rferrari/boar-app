package team.sopa.boar.engine.models

/**
 * A readable name for a model added from Hugging Face, from its file name: "Meta-Llama-3.1-8B-Instruct-Q4_K_M" reads
 * "Meta Llama 3.1 8B Instruct · Q4_K_M", "lfm2-2.6b-uncensored-x64-q4_k_m" reads "Lfm2 2.6B uncensored x64 · Q4_K_M".
 */
object ModelNames {
    private val QUANT = Regex("(?i)[._-](?:i1[._-])?((?:I?Q\\d\\w*)|BF16|F16|F32)$")
    private val SIZE = Regex("(?i)\\b(\\d+(?:\\.\\d+)?)b\\b")

    fun readable(fileLabel: String): String {
        val base = fileLabel.removeSuffix(".gguf").removeSuffix(".GGUF")
        val quant = QUANT.find(base)
        val name = (if (quant != null) base.substring(0, quant.range.first) else base)
            .removeSuffix(".i1").removeSuffix("-i1")
            // A dot between digits is a version ("3.1", "2.6b"); any other separator is a space.
            .replace(Regex("(?<!\\d)\\.|\\.(?!\\d)|[_-]+"), " ")
            .replace(SIZE) { "${it.groupValues[1]}B" }
            .replace(Regex("\\s+"), " ").trim()
            .replaceFirstChar { it.uppercaseChar() }
        if (name.isEmpty()) return base
        return if (quant != null) "$name · ${quant.groupValues[1].uppercase()}" else name
    }
}
