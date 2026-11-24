package com.swasthsaathi.voice.net

import com.swasthsaathi.voice.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Thin client for the Node backend. All calls are optional: the app works offline without them. */
object BackendClient {
    private fun post(path: String, body: JSONObject): JSONObject {
        val c = (URL(BuildConfig.BACKEND_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 4000; readTimeout = 6000
            setRequestProperty("Content-Type", "application/json")
            // setRequestProperty("x-api-key", ...)  // inject from secure config, never hard-code
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    /** Care options (labs + doctors) for a see_doctor/emergency result. Returns null when offline or on error. */
    fun triageWithCare(transcript: String, lang: String, city: String?, insurance: String?, age: Int?, pregnant: Boolean): JSONObject? =
        runCatching {
            post("/v1/triage", JSONObject().put("transcript", transcript).put("lang", lang).put("city", city).put("insurance", insurance)
                .put("profile", JSONObject().put("age", age).put("pregnant", pregnant)))
        }.getOrNull()

    fun bookAppointment(provider: String, doctorId: String, name: String, phone: String, slot: String): JSONObject? =
        runCatching {
            post("/v1/integrations/appointments", JSONObject().put("provider", provider).put("doctorId", doctorId)
                .put("patientName", name).put("phone", phone).put("slot", slot))
        }.getOrNull()

    fun orderLabTest(tests: List<String>, address: String, name: String, phone: String): JSONObject? =
        runCatching {
            post("/v1/integrations/lab-orders", JSONObject().put("tests", org.json.JSONArray(tests)).put("address", address)
                .put("patientName", name).put("phone", phone))
        }.getOrNull()

    fun scheduleReminder(channel: String, to: String, template: String, sendAtIso: String): JSONObject? =
        runCatching {
            post("/v1/integrations/reminders", JSONObject().put("channel", channel).put("to", to).put("template", template).put("sendAt", sendAtIso))
        }.getOrNull()
}
