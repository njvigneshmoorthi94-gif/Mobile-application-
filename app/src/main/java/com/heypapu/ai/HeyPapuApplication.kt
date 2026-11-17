package com.heypapu.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.heypapu.ai.personal.DailyJobAlertWorker
import java.util.concurrent.TimeUnit

class HeyPapuApplication : Application() {

    companion object {
        const val CHANNEL_CALLS = "papu_calls_channel"
        const val CHANNEL_JOBS = "papu_jobs_channel"
        const val CHANNEL_REMINDERS = "papu_reminders_channel"
        const val CHANNEL_SOCIAL = "papu_social_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleDailyJobAlerts()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CHANNEL_CALLS, "AI Call Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming call announcements and approval prompts"
            },
            NotificationChannel(CHANNEL_JOBS, "Daily Job Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily WordPress, React, Web & AI job matches"
            },
            NotificationChannel(CHANNEL_REMINDERS, "Personal & Voice Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Birthday, event, schedule and voice alarms"
            },
            NotificationChannel(CHANNEL_SOCIAL, "Social & Message Drafts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "WhatsApp, Instagram & Facebook notifications"
            }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    private fun scheduleDailyJobAlerts() {
        val workRequest = PeriodicWorkRequestBuilder<DailyJobAlertWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyJobAlertWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
