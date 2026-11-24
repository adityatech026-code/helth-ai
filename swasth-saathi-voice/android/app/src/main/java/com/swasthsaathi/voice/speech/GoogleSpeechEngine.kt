package com.swasthsaathi.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.swasthsaathi.voice.core.Lang

/** Uses Android's built-in SpeechRecognizer (Google). Must be called on the main thread. */
class GoogleSpeechEngine(private val ctx: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null

    override fun start(lang: Lang, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { onError("Speech recognition not available on this device"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(b: Bundle) { first(b)?.let(onPartial) }
            override fun onResults(b: Bundle) { first(b)?.let(onFinal) ?: onError("No speech heard") }
            override fun onError(code: Int) = onError("Speech error $code")
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            private fun first(b: Bundle) = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // uses on-device pack if downloaded
        })
    }

    override fun stop() { recognizer?.stopListening() }
    override fun release() { recognizer?.destroy(); recognizer = null }
}
