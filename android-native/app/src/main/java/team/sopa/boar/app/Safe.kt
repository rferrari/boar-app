package team.sopa.boar.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/**
 * The first layer of the error handler: a background job (an answer, a save, a download, a title) that throws something
 * nobody expected tells the user in a toast and logs it, instead of closing the app. The app keeps running; the job that
 * failed is over. A crash in native code (llama.cpp) can't be caught here: the process is already broken.
 */
object Safe {
    fun handler(app: Application, onError: ((Throwable) -> Unit)? = null) = CoroutineExceptionHandler { _, e ->
        // The stack only: no question or answer text goes to the log.
        Log.e("BOAR", "background job failed: ${e.javaClass.simpleName}", e)
        runCatching { onError?.invoke(e) }
        Handler(Looper.getMainLooper()).post {
            runCatching { Toast.makeText(app, team.sopa.boar.ui.I18n.t(app, "native.common.somethingWrong"), Toast.LENGTH_SHORT).show() }
        }
    }
}

/** [viewModelScope] with [Safe.handler]: what every ViewModel launches its background work on. */
val AndroidViewModel.safeScope: CoroutineScope get() = viewModelScope + Safe.handler(getApplication())
