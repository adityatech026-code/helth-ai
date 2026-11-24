package com.swasthsaathi.voice.speech

import android.content.Context
import com.swasthsaathi.voice.core.Lang
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Fully offline recognition with VOSK.
 *
 * Models are NOT in the repo (size). Download the ones you need from
 * https://alphacephei.com/vosk/models and place the unzipped folders under
 * app/src/main/assets/model-<lang>/ (e.g. model-hi, model-en, model-gu, model-te).
 * Each folder needs a `uuid` file for StorageService; add one with any unique text.
 *
 * Not every Indian language has an official VOSK model; see MODEL_DIRS and README.
 * Languages without a bundled model fall back to the Google engine or text input.
 */
class VoskSpeechEngine(private val ctx: Context) : SpeechEngine {
    private var service: SpeechService? = null
    private var model: Model? = null

    override fun start(lang: Lang, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        val dir = MODEL_DIRS[lang] ?: return onError("No offline model for ${lang.label}")
        StorageService.unpack(ctx, dir, "vosk/$dir", { m ->
            model = m
            try {
                val rec = Recognizer(m, 16000f)
                service = SpeechService(rec, 16000f).also {
                    it.startListening(object : RecognitionListener {
                        override fun onPartialResult(h: String?) { text(h, "partial")?.let(onPartial) }
                        override fun onResult(h: String?) { text(h, "text")?.takeIf { it.isNotBlank() }?.let(onFinal) }
                        override fun onFinalResult(h: String?) { text(h, "text")?.takeIf { it.isNotBlank() }?.let(onFinal) }
                        override fun onError(e: Exception?) = onError(e?.message ?: "VOSK error")
                        override fun onTimeout() = onError("Timed out")
                    })
                }
            } catch (e: Exception) { onError(e.message ?: "VOSK init failed") }
        }, { e -> onError("Model load failed: ${e.message}") })
    }

    private fun text(json: String?, key: String) = json?.let { JSONObject(it).optString(key) }

    override fun stop() { service?.stop() }
    override fun release() { service?.shutdown(); service = null; model?.close(); model = null }

    companion object {
        /** Asset folder per language. Remove/add entries to match the models you actually ship. */
        val MODEL_DIRS = mapOf(
            Lang.EN to "model-en", Lang.HI to "model-hi", Lang.GU to "model-gu", Lang.TE to "model-te"
        )
        fun hasModel(lang: Lang) = MODEL_DIRS.containsKey(lang)
    }
}
