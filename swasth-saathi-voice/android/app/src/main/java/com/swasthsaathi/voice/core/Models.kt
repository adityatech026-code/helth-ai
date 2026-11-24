package com.swasthsaathi.voice.core

enum class Lang(val code: String, val tag: String, val label: String) {
    EN("en", "en-IN", "English"),
    HI("hi", "hi-IN", "हिन्दी"),
    BN("bn", "bn-IN", "বাংলা"),
    TA("ta", "ta-IN", "தமிழ்"),
    MR("mr", "mr-IN", "मराठी"),
    TE("te", "te-IN", "తెలుగు"),
    GU("gu", "gu-IN", "ગુજરાતી");

    companion object { fun from(code: String) = entries.firstOrNull { it.code == code } ?: EN }
}

data class ExtractedSymptom(
    val id: String,
    val matchedText: String,
    val icd10: String,
    val severity: String?,
    val durationDays: Double?,
    val durationRaw: String?
)

data class Extraction(
    val lang: String,
    val symptoms: List<ExtractedSymptom>,
    val medications: List<String>,
    val habits: List<String>,
    val emergencyFlags: List<String>
)

data class Condition(val name: String, val icd10: String, val likelihood: Double)

data class SelfCareAdvice(
    val symptom: String, val drug: String, val dose: String, val max: String, val avoid: String,
    val hydration: String, val rest: String, val dietTip: String
)

data class TriageResult(
    val level: String,                // emergency | see_doctor | self_care | unknown
    val headline: String,
    val reasons: List<String>,
    val conditions: List<Condition>,
    val selfCare: List<SelfCareAdvice>,
    val tests: List<String>,
    val specialties: List<String>,
    val urgency: String?,
    val disclaimer: String
)

data class UserProfile(val age: Int? = null, val pregnant: Boolean = false, val city: String? = null, val insurance: String? = null)
