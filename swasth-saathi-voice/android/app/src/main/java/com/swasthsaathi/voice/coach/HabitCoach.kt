package com.swasthsaathi.voice.coach

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swasthsaathi.voice.core.KnowledgeBase
import com.swasthsaathi.voice.core.KnowledgeBase.toStrings
import java.util.Locale
import java.util.concurrent.TimeUnit

object HabitCoach {
    fun tips(habit: String): List<String> =
        KnowledgeBase.root.getJSONObject("habits").optJSONObject(habit)?.getJSONArray("tips").toStrings()

    /** Seven-day culturally relevant meal plan (vegetarian default; customise by region/diet). */
    fun weeklyMealPlan(): Map<String, String> {
        val plan = KnowledgeBase.root.getJSONObject("mealPlans").getJSONObject("default")
        return plan.keys().asSequence().associateWith { plan.getString(it) }
    }

    /** Schedules a gentle spoken reminder roughly once a day. */
    fun scheduleDaily(ctx: Context, habit: String, langTag: String) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf("habit" to habit, "lang" to langTag)).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("habit-$habit", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}

class ReminderWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        KnowledgeBase.load(ctx)
        val habit = inputData.getString("habit") ?: return Result.failure()
        val tip = HabitCoach.tips(habit).randomOrNull() ?: return Result.success()
        var tts: TextToSpeech? = null
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag(inputData.getString("lang") ?: "en-IN")
                tts?.speak(tip, TextToSpeech.QUEUE_FLUSH, null, "habit")
            }
        }
        // TODO: also post a notification (NotificationCompat) so the reminder is visible when the phone is silent.
        return Result.success()
    }
}
