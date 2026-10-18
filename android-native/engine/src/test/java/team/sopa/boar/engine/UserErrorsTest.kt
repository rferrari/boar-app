package team.sopa.boar.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import team.sopa.boar.engine.models.AssetStore.DownloadException

class UserErrorsTest {
    @Test fun `no model gets its own text, anything else the generic one`() {
        assertEquals("native.chat.errorNoModel", UserErrors.chatKey(UserErrors.NO_MODEL))
        assertEquals("native.chat.errorGeneric", UserErrors.chatKey("java.lang.IllegalStateException: model unloaded"))
    }

    @Test fun `download failures map by cause`() {
        assertEquals("native.download.errorStorage", UserErrors.downloadKey(DownloadException.Storage("needs 2 GB")))
        assertEquals("native.download.errorOffline", UserErrors.downloadKey(DownloadException.OfflineBuild()))
        assertEquals("native.download.errorNetwork", UserErrors.downloadKey(java.net.UnknownHostException("huggingface.co")))
        assertEquals("native.download.errorNetwork", UserErrors.downloadKey(java.net.SocketTimeoutException()))
        assertEquals("native.download.errorGeneric", UserErrors.downloadKey(IllegalStateException("boom")))
    }
}
