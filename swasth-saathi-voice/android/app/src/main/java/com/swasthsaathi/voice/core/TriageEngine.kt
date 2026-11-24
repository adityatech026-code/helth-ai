package com.swasthsaathi.voice.core

import com.swasthsaathi.voice.core.KnowledgeBase.toStrings

/** On-device port of backend/src/triage.js: the core triage works with zero connectivity. */
object TriageEngine {
    private val persistenceLimitDays = mapOf("fever" to 3.0, "headache" to 3.0, "muscle_pain" to 7.0, "indigestion" to 7.0)

    private fun i18n(section: String, key: String?, lang: String): String {
        val obj = KnowledgeBase.i18n.getJSONObject(section)
        val node = if (key != null) obj.getJSONObject(key) else obj
        return node.optString(lang, node.getString("en"))
    }

    fun triage(ex: Extraction, profile: UserProfile = UserProfile()): TriageResult {
        val lang = ex.lang.takeIf { it in KnowledgeBase.LANGS } ?: "en"
        val disclaimer = i18n("disclaimer", null, lang)
        if (ex.symptoms.isEmpty() && ex.emergencyFlags.isEmpty()) {
            return TriageResult("unknown", "I did not catch a symptom. Please tell me where it hurts or how you feel.",
                listOf("no_symptom_detected"), emptyList(), emptyList(), emptyList(), emptyList(), null, disclaimer)
        }
        val reasons = mutableListOf<String>()
        var level = "self_care"
        if (ex.emergencyFlags.isNotEmpty()) { level = "emergency"; reasons += "red_flag:" + ex.emergencyFlags.joinToString(",") }

        for (s in ex.symptoms) {
            val def = KnowledgeBase.symptoms.getJSONObject(s.id)
            when (def.getString("tier")) {
                "serious" -> {
                    if (level != "emergency") level = if (def.optBoolean("emergency") && s.severity == "severe") "emergency" else "see_doctor"
                    reasons += "serious_symptom:${s.id}"
                }
                "ambiguous" -> { if (level == "self_care") level = "see_doctor"; reasons += "ambiguous_symptom:${s.id}" }
            }
            if (s.severity == "severe" && level == "self_care") { level = "see_doctor"; reasons += "severe:${s.id}" }
            val limit = persistenceLimitDays[s.id]
            if (limit != null && s.durationDays != null && s.durationDays >= limit && level == "self_care") {
                level = "see_doctor"; reasons += "persistent:${s.id}"
            }
        }
        if (ex.symptoms.size >= 3 && level == "self_care") { level = "see_doctor"; reasons += "multiple_symptoms" }
        val age = profile.age
        val highRisk = profile.pregnant || (age != null && (age < 12 || age >= 65))
        if (highRisk && level == "self_care") { level = "see_doctor"; reasons += "high_risk_group" }

        val ids = ex.symptoms.map { it.id }
        val headlineKey = when (level) { "emergency" -> "emergency"; "see_doctor" -> "see_doctor"; else -> "self_care" }
        val conditions = mapConditions(ids)

        if (level == "self_care") {
            val care = ex.symptoms.map {
                val d = KnowledgeBase.symptoms.getJSONObject(it.id)
                val otc = d.getJSONObject("otc"); val adv = d.getJSONObject("advice")
                SelfCareAdvice(it.id, otc.getString("drug"), otc.getString("dose"), otc.getString("max"), otc.getString("avoid"),
                    adv.getString("hydration"), adv.getString("rest"), adv.getString("diet"))
            }
            return TriageResult(level, i18n("headline", headlineKey, lang), reasons, conditions, care, emptyList(), emptyList(), null, disclaimer)
        }
        val tests = linkedSetOf<String>(); val specs = linkedSetOf<String>()
        ids.forEach { id ->
            val d = KnowledgeBase.symptoms.getJSONObject(id)
            tests += d.getJSONArray("tests").toStrings(); specs += d.getString("specialty")
        }
        val urgency = if (level == "emergency") "now" else if (reasons.any { it.startsWith("serious") }) "within_24h" else "within_3_days"
        return TriageResult(level, i18n("headline", headlineKey, lang), reasons, conditions, emptyList(), tests.take(6), specs.toList(), urgency, disclaimer)
    }

    fun mapConditions(ids: List<String>): List<Condition> {
        data class Acc(val name: String, val icd: String, var score: Double)
        val acc = linkedMapOf<String, Acc>()
        for (id in ids) {
            val arr = KnowledgeBase.symptoms.getJSONObject(id).getJSONArray("conditions")
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                val key = c.getString("icd10") + "|" + c.getString("name")
                acc.getOrPut(key) { Acc(c.getString("name"), c.getString("icd10"), 0.0) }.score += c.getDouble("weight")
            }
        }
        val total = acc.values.sumOf { it.score }.takeIf { it > 0 } ?: 1.0
        return acc.values.sortedByDescending { it.score }.take(5).map { Condition(it.name, it.icd, Math.round(it.score / total * 100) / 100.0) }
    }
}
