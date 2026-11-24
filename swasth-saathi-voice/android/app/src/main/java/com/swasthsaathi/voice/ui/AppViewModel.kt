package com.swasthsaathi.voice.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swasthsaathi.voice.coach.HabitCoach
import com.swasthsaathi.voice.core.*
import com.swasthsaathi.voice.data.*
import com.swasthsaathi.voice.net.BackendClient
import com.swasthsaathi.voice.speech.SpeechEngine
import com.swasthsaathi.voice.speech.SpeechEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMsg(val fromUser: Boolean, val text: String, val result: TriageResult? = null, val care: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NotebookStore(app)
    private var engine: SpeechEngine? = null

    var lang by mutableStateOf(Lang.HI)
    var listening by mutableStateOf(false)
    var partial by mutableStateOf("")
    var messages by mutableStateOf(listOf(ChatMsg(false, "Namaste! Tell me what you are feeling.")))
    var entries by mutableStateOf(emptyList<Entry>())
    var trends by mutableStateOf(emptyList<Trend>())
    var profile by mutableStateOf(UserProfile(city = "Lucknow"))
    private var lastResult: TriageResult? = null

    init { KnowledgeBase.load(app); refresh() }

    fun refresh() { entries = store.all(); trends = Trends.compute(entries) }

    fun toggleMic() {
        if (listening) { engine?.stop(); return }
        engine?.release()
        engine = SpeechEngineFactory.create(getApplication(), lang)
        listening = true; partial = ""
        engine!!.start(lang, { partial = it }, { listening = false; partial = ""; handle(it) },
            { listening = false; messages = messages + ChatMsg(false, "Sorry, I could not hear that ($it). You can also type.") })
    }

    fun handle(utterance: String) {
        messages = messages + ChatMsg(true, utterance)
        val intent = Dialogue.detect(utterance)
        if (intent != Dialogue.Intent.NONE) {
            messages = messages + ChatMsg(false, Dialogue.reply(intent, entries, lastResult)); return
        }
        val ex = SymptomExtractor.extract(utterance, lang.code)
        val result = TriageEngine.triage(ex, profile)   // offline, always available
        lastResult = result
        messages = messages + ChatMsg(false, result.headline, result)
        store.add(Entry(System.currentTimeMillis(), "symptom", lang.code, utterance, ex.symptoms.map { it.id },
            ex.symptoms.mapNotNull { s -> s.severity?.let { s.id to it } }.toMap(), ex.medications, result.level, summarize(result)))
        refresh()
        // Habits -> coaching tips
        ex.habits.firstOrNull()?.let { h -> HabitCoach.tips(h).firstOrNull()?.let { messages = messages + ChatMsg(false, it) } }
        // Online enrichment: nearby labs + doctors for non-mild results
        if (result.level == "see_doctor" || result.level == "emergency") viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                BackendClient.triageWithCare(utterance, lang.code, profile.city, profile.insurance, profile.age, profile.pregnant)
            } ?: return@launch
            val care = r.optJSONObject("care") ?: return@launch
            val docs = care.optJSONArray("doctors"); val labs = care.optJSONArray("labs")
            val sb = StringBuilder()
            if (labs != null) for (i in 0 until minOf(2, labs.length())) labs.getJSONObject(i).let { sb.append("Lab: ${it.getString("name")} (${it.getJSONArray("accreditation").join("/")}) ${it.getString("phone")}\n") }
            if (docs != null) for (i in 0 until minOf(3, docs.length())) docs.getJSONObject(i).let {
                sb.append("Dr: ${it.getString("name")}, ${it.getString("specialty")}, ${it.getDouble("rating")}★ ${it.getString("phone")}\n") }
            if (sb.isNotEmpty()) messages = messages + ChatMsg(false, "Nearby options", care = sb.toString().trim())
        }
    }

    fun addLabResult(text: String) { store.add(Entry(System.currentTimeMillis(), "lab", lang.code, text, emptyList(), emptyMap(), emptyList(), "info", "")); refresh() }

    fun exportPdf(): java.io.File = PdfExporter.export(getApplication(), entries, trends, null)

    fun backupBlob(passphrase: CharArray) = BackupCrypto.seal(store.exportJson(), passphrase)
    fun restore(blob: String, passphrase: CharArray) { store.importJson(BackupCrypto.open(blob, passphrase)); refresh() }

    private fun summarize(r: TriageResult) = when (r.level) {
        "self_care" -> r.selfCare.joinToString("; ") { "${it.symptom.replace('_', ' ')}: ${it.drug}, ${it.dose}" }
        else -> "${r.level}: tests ${r.tests.joinToString()}; see ${r.specialties.joinToString()}"
    }

    override fun onCleared() { engine?.release() }
}
