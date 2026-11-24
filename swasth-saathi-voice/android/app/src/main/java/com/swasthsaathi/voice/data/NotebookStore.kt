package com.swasthsaathi.voice.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Entry(
    val timestamp: Long,
    val kind: String,              // "symptom" | "lab" | "note"
    val lang: String,
    val transcript: String,
    val symptoms: List<String>,
    val severity: Map<String, String>,
    val medications: List<String>,
    val level: String,
    val recommendation: String
) {
    fun toJson() = JSONObject().put("t", timestamp).put("k", kind).put("l", lang).put("tr", transcript)
        .put("s", JSONArray(symptoms)).put("sev", JSONObject(severity)).put("m", JSONArray(medications))
        .put("lv", level).put("r", recommendation)

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            o.getLong("t"), o.optString("k", "symptom"), o.optString("l"), o.optString("tr"),
            o.getJSONArray("s").let { a -> (0 until a.length()).map { a.getString(it) } },
            o.getJSONObject("sev").let { s -> s.keys().asSequence().associateWith { s.getString(it) } },
            o.getJSONArray("m").let { a -> (0 until a.length()).map { a.getString(it) } },
            o.optString("lv"), o.optString("r")
        )
    }
}

/**
 * Local-only, encrypted health notebook.
 * Each entry is one line: base64(iv || AES-256-GCM ciphertext). The key lives in the Android
 * Keystore (hardware-backed where available) and never leaves the device.
 */
class NotebookStore(ctx: Context) {
    private val file = File(ctx.filesDir, "notebook.enc")

    @Synchronized fun add(e: Entry) {
        file.appendText(encrypt(e.toJson().toString()) + "\n")
    }

    @Synchronized fun all(): List<Entry> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.mapNotNull {
            runCatching { Entry.fromJson(JSONObject(decrypt(it))) }.getOrNull()
        }.sortedByDescending { it.timestamp }
    }

    @Synchronized fun wipe() { file.delete() }

    /** Plain JSON of every entry, for BackupCrypto (which re-encrypts with a user passphrase). */
    fun exportJson(): String = JSONArray().also { arr -> all().forEach { arr.put(it.toJson()) } }.toString()

    fun importJson(json: String) {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) add(Entry.fromJson(arr.getJSONObject(i)))
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(b64: String): String {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        }
        return String(c.doFinal(raw, 12, raw.size - 12))
    }

    private companion object { const val ALIAS = "swasth_notebook_key" }
}
