package com.swasthsaathi.voice.speech

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.swasthsaathi.voice.core.Lang

interface SpeechEngine {
    /** Starts listening. [onPartial] streams interim text; [onFinal] fires once with the full utterance. */
    fun start(lang: Lang, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release()
}

/**
 * Chooses the engine: Google SpeechRecognizer when online (best accuracy for all 7 languages),
 * VOSK when offline. Rural users with weak data therefore still get core triage by voice.
 */
object SpeechEngineFactory {
    fun create(ctx: Context, lang: Lang): SpeechEngine =
        if (isOnline(ctx) || !VoskSpeechEngine.hasModel(lang)) GoogleSpeechEngine(ctx) else VoskSpeechEngine(ctx)

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
