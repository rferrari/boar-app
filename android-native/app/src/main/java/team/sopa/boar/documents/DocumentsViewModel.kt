package team.sopa.boar.documents

import team.sopa.boar.app.safeScope

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import team.sopa.boar.app.BoarServices
import team.sopa.boar.engine.rag.DocumentImport
import team.sopa.boar.engine.rag.KnowledgeBase

data class DocumentsState(
    val collections: List<KnowledgeBase.Collection> = emptyList(),
    /** null, "reading", "chunking" or "embedding". */
    val stage: String? = null,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    /** The collection just imported, for "Ask about them" (Personal Files); null once dismissed or on the next import. */
    val imported: String? = null,
    /** The collection being added now, and those waiting (adam, Nov 12): name and file count. */
    val running: String? = null,
    val waiting: List<Triple<Long, String, Int>> = emptyList(),
)

/** My Documents (port of PersonalDocumentsManager.tsx): import files into a collection, switch it on or off, remove it. */
class DocumentsViewModel(app: Application) : AndroidViewModel(app) {
    private val services = BoarServices.get(app)
    private val reader = DocumentReader(app)
    private val _state = MutableStateFlow(DocumentsState())
    val state: StateFlow<DocumentsState> = _state

    init { refresh() }

    fun dismissImported() = _state.update { it.copy(imported = null) }

    fun refresh() = _state.update { it.copy(collections = services.knowledgeBase.collections()) }

    private val queue = team.sopa.boar.engine.rag.ImportQueue<List<Uri>>()

    /** While a collection is being added, another waits its turn (up to five); otherwise it starts now. */
    fun import(uris: List<Uri>, name: String) {
        if (uris.isEmpty()) return
        if (_state.value.stage != null) {
            if (!queue.add(name, uris.size, uris)) _state.update { it.copy(error = team.sopa.boar.ui.I18n.t(getApplication(), "native.docs.queueFull")) }
            publishQueue()
            return
        }
        start(uris, name)
    }

    fun removeWaiting(id: Long) { queue.remove(id); publishQueue() }

    private fun publishQueue() = _state.update { it.copy(waiting = queue.waiting.map { w -> Triple(w.id, w.name, w.files) }) }

    private fun runNextImport() {
        val next = queue.next() ?: return
        publishQueue()
        start(next.payload, next.name)
    }

    private fun start(uris: List<Uri>, name: String) {
        _state.update { it.copy(stage = "reading", error = null, done = 0, total = 0, imported = null, running = name) }
        safeScope.launch(Dispatchers.IO) {
            try {
                val files = uris.map { reader.read(it) }
                _state.update { it.copy(stage = "chunking") }
                DocumentImport.import(services.knowledgeBase, services::embed, files, name, onProgress = { d, t ->
                    _state.update { it.copy(stage = "embedding", done = d, total = t) }
                })
                services.vault.record { f -> f.copy(documents = f.documents + files.size) }
                _state.update { it.copy(imported = name) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "$name: " + reader.messageFor(e)) }
            } finally {
                _state.update { it.copy(stage = null, running = null) }
                refresh()
                // Worked or failed, the next waiting collection starts (on the main thread, which owns the queue).
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { runNextImport() }
            }
        }
    }

    fun setActive(id: String, on: Boolean) {
        services.knowledgeBase.setCollectionActive(id, on)
        refresh()
    }

    /** exportCollection: the collection's passages as JSON, handed to the share sheet. */
    suspend fun exportIntent(col: KnowledgeBase.Collection): android.content.Intent = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        // A folder per export: the file keeps the collection's name, and a later export can't overwrite one still being shared.
        val dir = java.io.File(app.cacheDir, "exports/${System.currentTimeMillis()}").apply { mkdirs() }
        val file = java.io.File(dir, DocumentImport.slug(col.name).ifEmpty { "collection" } + ".json")
        file.writeText(DocumentImport.exportJson(services.knowledgeBase.collectionDocs(col.id)))
        services.vault.record { f -> f.copy(exports = f.exports + 1) }
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, team.sopa.boar.ui.I18n.t(app, "native.documents.shareTitle", "name" to col.name))
    }

    fun remove(id: String) {
        safeScope.launch(Dispatchers.IO) { services.knowledgeBase.deleteCollection(id); refresh() }
    }
}
