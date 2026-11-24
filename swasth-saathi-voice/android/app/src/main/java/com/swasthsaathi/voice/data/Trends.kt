package com.swasthsaathi.voice.data

import java.util.concurrent.TimeUnit

data class Trend(val symptom: String, val countLast30d: Int, val countPrev30d: Int, val weeklyCounts: List<Int>, val message: String)

/** Progress view logic: recurring-symptom detection over the notebook. */
object Trends {
    fun compute(entries: List<Entry>, now: Long = System.currentTimeMillis()): List<Trend> {
        val day = TimeUnit.DAYS.toMillis(1)
        val symptomEntries = entries.filter { it.kind == "symptom" }
        return symptomEntries.flatMap { e -> e.symptoms.map { it to e.timestamp } }
            .groupBy({ it.first }, { it.second })
            .map { (sym, times) ->
                val recent = times.count { now - it <= 30 * day }
                val prev = times.count { now - it in (30 * day + 1)..(60 * day) }
                val weekly = (0 until 4).map { w -> times.count { now - it in (w * 7 * day)until((w + 1) * 7 * day) } }.reversed()
                val label = sym.replace('_', ' ')
                val msg = when {
                    recent >= 4 -> "You mentioned $label $recent times in 30 days. Recurring symptoms are worth showing a doctor."
                    recent > prev && prev > 0 -> "$label is more frequent than last month ($recent vs $prev)."
                    recent < prev -> "$label is improving ($recent vs $prev last month)."
                    else -> "$label: $recent time(s) in the last 30 days."
                }
                Trend(sym, recent, prev, weekly, msg)
            }.sortedByDescending { it.countLast30d }
    }
}
