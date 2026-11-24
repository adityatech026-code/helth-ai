package com.swasthsaathi.voice.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Loads shared/knowledge/*.json (bundled as assets). Works fully offline. */
object KnowledgeBase {
    val LANGS = listOf("en", "hi", "bn", "ta", "mr", "te", "gu")
    lateinit var symptoms: JSONObject; private set
    lateinit var root: JSONObject; private set
    lateinit var i18n: JSONObject; private set

    fun load(ctx: Context) {
        if (::root.isInitialized) return
        fun read(name: String) = JSONObject(ctx.assets.open(name).bufferedReader().use { it.readText() })
        root = read("symptoms.json")
        symptoms = root.getJSONObject("symptoms")
        i18n = read("i18n.json")
    }

    /** All aliases across every language for an object shaped {"en":[...], "hi":[...]} */
    fun aliases(map: JSONObject): List<String> =
        LANGS.flatMap { l -> map.optJSONArray(l).toStrings() }

    fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
