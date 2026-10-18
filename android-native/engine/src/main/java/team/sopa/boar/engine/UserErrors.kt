package team.sopa.boar.engine

import team.sopa.boar.engine.models.AssetStore.DownloadException
import team.sopa.boar.engine.models.StorageBudget
import team.sopa.boar.engine.models.Units
import java.util.Locale

/** Which localized text a failure shows (native.* keys): users never see raw exception text. */
object UserErrors {
    /** What ChatViewModel stores in ChatState.error when no answer model could be loaded. */
    const val NO_MODEL = "no-model"

    /** The line shown in place of a failed answer. */
    fun chatKey(error: String): String = if (error == NO_MODEL) "native.chat.errorNoModel" else "native.chat.errorGeneric"

    /** A failed download, by cause: the engine's messages are English and name the file, the row already does. */
    fun downloadKey(e: Throwable): String = when (e) {
        is DownloadException.NoSource -> "native.download.errorNoSource"
        is DownloadException.OfflineBuild -> "native.download.errorOffline"
        // BOAR's own limit and a full phone read differently: the phone may have plenty of room when the limit is hit.
        is DownloadException.Storage -> e.refused.let { r ->
            when {
                r == null || r.reason != StorageBudget.Reason.BUDGET -> "native.download.errorStorage"
                r.downloadingBytes > 0 -> "native.download.errorBudgetDownloading"
                else -> "native.download.errorBudget"
            }
        }
        is DownloadException.SizeChanged -> "native.download.errorSizeChanged"
        is DownloadException.Paused -> "native.download.errorPaused"
        is DownloadException.Checksum -> "native.download.errorChecksum"
        is DownloadException.Http -> "native.download.errorServer"
        // No route, DNS, a dropped or timed-out connection.
        is java.io.IOException -> "native.download.errorNetwork"
        else -> "native.download.errorGeneric"
    }

    /** The numbers [downloadKey]'s storage texts fill in ({{budget}}, {{used}}, {{downloading}}, {{needed}}, {{free}}). */
    fun downloadArgs(e: Throwable, locale: Locale = Locale.getDefault()): Array<Pair<String, Any?>> {
        val r = (e as? DownloadException.Storage)?.refused ?: return emptyArray()
        fun f(b: Long) = Units.formatBytes(b, locale)
        return arrayOf(
            "budget" to f(r.budgetBytes), "used" to f(r.usedBytes), "downloading" to f(r.downloadingBytes),
            "needed" to f(r.neededBytes), "free" to (r.freeDiskBytes?.let(::f) ?: "?"),
        )
    }
}
