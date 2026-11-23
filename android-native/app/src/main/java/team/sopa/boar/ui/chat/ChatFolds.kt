package team.sopa.boar.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf

/**
 * What the user opened or folded in an answer (a source's passage, Staged's quotes), kept while BOAR runs: Read the
 * whole article leaves the chat screen, and Done brings it back as it was left (adam, Nov 10). Main thread only.
 */
internal object ChatFolds {
    private const val MAX = 500
    private val states = object : LinkedHashMap<String, Any?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>) = size > MAX
    }

    /** Like `remember { mutableStateOf(initial) }`, but a [key] (the message id and the part) keeps the value. */
    @Composable
    fun <T> remember(key: String?, initial: T): MutableState<T> {
        @Suppress("UNCHECKED_CAST")
        val state = androidx.compose.runtime.remember(key) { mutableStateOf(if (key != null && states.containsKey(key)) states[key] as T else initial) }
        SideEffect { if (key != null) states[key] = state.value }
        return state
    }
}
