package team.sopa.boar.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dictation with Android's on-device speech recognizer only (Android 12+; modules/voice-input's "on-device" mode):
 * the audio stays on the phone. Phones without one (no recognition service, as on GrapheneOS) get no mic button; the
 * system recognizer, which may send audio to a server, isn't used.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val available: Boolean get() = Companion.available(context)

    fun start(onPartial: (String) -> Unit, onResult: (String) -> Unit, onEnd: (error: Int?) -> Unit) {
        stop()
        if (!available) { onEnd(SpeechRecognizer.ERROR_CLIENT); return }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            // Stopped first, and destroyed after this callback returns, so a session onEnd starts isn't the one stopped.
            override fun onError(error: Int) { finish(r); onEnd(error) }
            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onResult)
                finish(r); onEnd(null)
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun finish(r: SpeechRecognizer) {
        if (recognizer === r) recognizer = null
        main.post { runCatching { r.destroy() } }
    }

    fun stop() {
        recognizer?.runCatching { cancel(); destroy() }
        recognizer = null
    }

    companion object {
        fun available(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
