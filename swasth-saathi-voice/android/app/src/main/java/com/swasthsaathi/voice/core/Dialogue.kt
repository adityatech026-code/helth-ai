package com.swasthsaathi.voice.core

import com.swasthsaathi.voice.data.Entry
import com.swasthsaathi.voice.data.Trends

/**
 * Follow-up handling: "What should I do next?", "Show my last blood test results", "How am I doing?".
 * Keyword intents across the 7 languages keep it offline and predictable; swap in an LLM later if wanted.
 */
object Dialogue {
    enum class Intent { NEXT_STEP, LAST_RESULTS, PROGRESS, REPEAT, BOOK, NONE }

    private val words = mapOf(
        Intent.NEXT_STEP to listOf("what should i do", "what next", "next step", "अब क्या करूं", "अब क्या करें", "आगे क्या", "এরপর কী", "এখন কী করব", "அடுத்து என்ன", "இப்போது என்ன செய்ய", "पुढे काय", "आता काय करू", "ఇప్పుడు ఏం చేయాలి", "తర్వాత ఏమిటి", "હવે શું કરું", "આગળ શું"),
        Intent.LAST_RESULTS to listOf("last blood", "blood test result", "my results", "last report", "मेरी रिपोर्ट", "ब्लड टेस्ट", "रक्त जांच", "রক্ত পরীক্ষা", "রিপোর্ট", "இரத்த பரிசோதனை", "அறிக்கை", "रक्त तपासणी", "रिपोर्ट", "రక్త పరీక్ష", "రిపోర్ట్", "રક્ત તપાસ", "રિપોર્ટ"),
        Intent.PROGRESS to listOf("progress", "how am i doing", "trend", "कैसा चल रहा", "प्रगति", "অগ্রগতি", "முன்னேற்றம்", "प्रगती", "పురోగతి", "પ્રગતિ"),
        Intent.REPEAT to listOf("repeat", "say again", "फिर से बोलो", "दोबारा बोलो", "আবার বলুন", "மீண்டும் சொல்", "पुन्हा सांगा", "మళ్లీ చెప్పు", "ફરી કહો"),
        Intent.BOOK to listOf("book", "appointment", "बुक", "अपॉइंटमेंट", "অ্যাপয়েন্টমেন্ট", "அப்பாயின்ட்மென்ட்", "అపాయింట్‌మెంట్", "એપોઇન્ટમેન્ટ")
    )

    fun detect(utterance: String): Intent {
        val t = SymptomExtractor.normalize(utterance)
        return words.entries.firstOrNull { (_, ws) -> ws.any { t.contains(it.lowercase()) } }?.key ?: Intent.NONE
    }

    /** Short, spoken-style replies (1-3 sentences). */
    fun reply(intent: Intent, entries: List<Entry>, last: TriageResult?): String = when (intent) {
        Intent.NEXT_STEP -> when (last?.level) {
            "emergency" -> "Please call 112 or 108 now, or have someone take you to the nearest hospital."
            "see_doctor" -> "Get ${last.tests.take(2).joinToString(" and ").ifBlank { "a check-up" }} done ${if (last.urgency == "within_24h") "today or tomorrow" else "in the next few days"}, then see a doctor. Want me to show nearby labs?"
            "self_care" -> "Rest, drink fluids and follow the dose I gave. If it is not better in 2 days, tell me and we will arrange tests."
            else -> "Tell me what you are feeling and I will guide you."
        }
        Intent.LAST_RESULTS -> entries.firstOrNull { it.kind == "lab" }
            ?.let { "Your last report: ${it.transcript}. Saved on ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))}." }
            ?: "I do not have a lab report saved yet. You can add one from the Notebook screen."
        Intent.PROGRESS -> Trends.compute(entries).firstOrNull()?.message ?: "Nothing recurring so far. Keep logging how you feel."
        Intent.REPEAT -> last?.headline ?: "I have nothing to repeat yet."
        Intent.BOOK -> "Opening doctors near you. Please confirm a time slot."
        Intent.NONE -> ""
    }
}
