package team.sopa.boar.engine

import team.sopa.boar.engine.models.AssetStore.DownloadException

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
        is DownloadException.Storage -> "native.download.errorStorage"
        is DownloadException.SizeChanged -> "native.download.errorSizeChanged"
        is DownloadException.Paused -> "native.download.errorPaused"
        is DownloadException.Checksum -> "native.download.errorChecksum"
        is DownloadException.Http -> "native.download.errorServer"
        // No route, DNS, a dropped or timed-out connection.
        is java.io.IOException -> "native.download.errorNetwork"
        else -> "native.download.errorGeneric"
    }
}
