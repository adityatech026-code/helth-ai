package com.swasthsaathi.voice.core

import com.swasthsaathi.voice.core.KnowledgeBase.toStrings

/** On-device port of backend/src/nlu.js. Keep behaviour identical. */
object SymptomExtractor {
    private val digitBases = intArrayOf(0x0966, 0x09E6, 0x0BE6, 0x0C66, 0x0AE6)
    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "ten" to 10,
        "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "छह" to 6, "सात" to 7, "दस" to 10,
        "এক" to 1, "দুই" to 2, "তিন" to 3, "চার" to 4, "পাঁচ" to 5,
        "ஒரு" to 1, "இரண்டு" to 2, "மூன்று" to 3, "நான்கு" to 4, "ஐந்து" to 5,
        "दोन" to 2, "ఒక" to 1, "రెండు" to 2, "మూడు" to 3, "నాలుగు" to 4, "ఐదు" to 5,
        "એક" to 1, "બે" to 2, "ત્રણ" to 3, "પાંચ" to 5
    )
    private val unitDays = mapOf("hour" to 1.0 / 24, "day" to 1.0, "week" to 7.0, "month" to 30.0)

    fun normalize(text: String): String {
        val sb = StringBuilder()
        text.lowercase().codePoints().forEach { cp ->
            val base = digitBases.firstOrNull { cp >= it && cp <= it + 9 }
            if (base != null) sb.append(cp - base) else sb.appendCodePoint(cp)
        }
        return java.text.Normalizer.normalize(sb, java.text.Normalizer.Form.NFC)
    }

    fun extract(transcript: String, lang: String): Extraction {
        val kb = KnowledgeBase.root
        val text = normalize(transcript)
        val symptoms = mutableListOf<ExtractedSymptom>()
        val defs = KnowledgeBase.symptoms
        for (id in defs.keys()) {
            val def = defs.getJSONObject(id)
            val hit = KnowledgeBase.aliases(def.getJSONObject("aliases"))
                .mapNotNull { a -> text.indexOf(a.lowercase()).takeIf { it >= 0 }?.let { a to it } }
                .minByOrNull { it.second } ?: continue
            val dur = duration(text)
            symptoms += ExtractedSymptom(id, hit.first, def.getString("icd10"), severity(text, hit.second), dur?.first, dur?.second)
        }
        val meds = kb.getJSONArray("medications").toStrings().filter { text.contains(it.lowercase()) }
        val habitsObj = kb.getJSONObject("habits")
        val habits = habitsObj.keys().asSequence().filter { h ->
            KnowledgeBase.aliases(habitsObj.getJSONObject(h).getJSONObject("aliases")).any { text.contains(it.lowercase()) }
        }.toList()
        val flags = KnowledgeBase.aliases(kb.getJSONObject("redFlags").getJSONObject("emergency"))
            .filter { text.contains(it.lowercase()) }
        return Extraction(lang, symptoms, meds, habits, flags)
    }

    private fun severity(text: String, idx: Int): String? {
        val window = text.substring(maxOf(0, idx - 40), minOf(text.length, idx + 60))
        val sev = KnowledgeBase.root.getJSONObject("severityWords")
        for (level in listOf("severe", "moderate", "mild")) {
            if (KnowledgeBase.aliases(sev.getJSONObject(level)).any { window.contains(it.lowercase()) }) return level
        }
        return null
    }

    /** returns (days, rawMatch) */
    private fun duration(text: String): Pair<Double, String>? {
        val units = KnowledgeBase.root.getJSONObject("durationUnits")
        val numAlt = (listOf("\\d+(?:\\.\\d+)?") + numberWords.keys.map { Regex.escape(it) }).joinToString("|")
        for (unit in units.keys()) {
            for (w in units.getJSONArray(unit).toStrings()) {
                val m = Regex("($numAlt)\\s*${Regex.escape(w.lowercase())}").find(text) ?: continue
                val raw = m.groupValues[1]
                val n = raw.toDoubleOrNull() ?: (numberWords[raw] ?: 1).toDouble()
                return (n * unitDays.getValue(unit)) to m.value
            }
        }
        return null
    }
}
