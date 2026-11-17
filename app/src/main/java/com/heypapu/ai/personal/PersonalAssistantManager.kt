package com.heypapu.ai.personal

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.heypapu.ai.HeyPapuApplication
import com.heypapu.ai.calls.CallAnnouncementAndControlManager
import com.heypapu.ai.jobs.JobPortalRepository
import com.heypapu.ai.security.SecurityVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

data class PersonalItem(
    val id: String,
    val type: String,
    val title: String,
    val dateTimeLabel: String,
    val triggerAtMillis: Long,
    val isCompleted: Boolean = false
)

class PersonalAssistantManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("papu_personal_items", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    fun getItems(): List<PersonalItem> {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        val list = mutableListOf<PersonalItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                PersonalItem(
                    id = o.optString("id"),
                    type = o.optString("type"),
                    title = o.optString("title"),
                    dateTimeLabel = o.optString("dateTimeLabel"),
                    triggerAtMillis = o.optLong("triggerAtMillis"),
                    isCompleted = o.optBoolean("isCompleted")
                )
            )
        }
        return list
    }

    fun addItemAndScheduleAlarm(item: PersonalItem) {
        val list = getItems().toMutableList()
        list.add(0, item)
        saveItems(list)
        if (item.triggerAtMillis > System.currentTimeMillis()) {
            scheduleExactVoiceAlarm(item)
        }
    }

    fun toggleTaskCompleted(id: String) {
        val updated = getItems().map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveItems(updated)
    }

    private fun saveItems(items: List<PersonalItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("type", it.type)
                put("title", it.title)
                put("dateTimeLabel", it.dateTimeLabel)
                put("triggerAtMillis", it.triggerAtMillis)
                put("isCompleted", it.isCompleted)
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    @SuppressLint("MissingPermission")
    private fun scheduleExactVoiceAlarm(item: PersonalItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra("title", item.title)
            putExtra("type", item.type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, item.triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    item.triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, item.triggerAtMillis, pendingIntent)
        }
    }

    fun insertIntoAndroidCalendar(title: String, startMillis: Long) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, startMillis + 3600_000L)
                put(CalendarContract.Events.TITLE, "Hey Papu AI: $title")
                put(CalendarContract.Events.CALENDAR_ID, 1L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (_: Exception) {
        }
    }

    fun openGmailInbox() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    suspend fun fetchLiveWeather(city: String = "Chennai, Tamil Nadu"): String =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=13.0827&longitude=80.2707&current=temperature_2m,relative_humidity_2m,wind_speed_10m"
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext "$city: Weather feed temporarily unavailable."
                    val current = JSONObject(resp.body?.string().orEmpty()).getJSONObject("current")
                    val temp = current.optDouble("temperature_2m")
                    val humidity = current.optInt("relative_humidity_2m")
                    val wind = current.optDouble("wind_speed_10m")
                    "$city • ${temp}°C | Humidity: $humidity% | Wind: $wind km/h"
                }
            } catch (_: Exception) {
                "$city • Check internet connection for live weather."
            }
        }
}

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Scheduled Reminder"
        val type = intent.getStringExtra("type") ?: "Reminder"

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, HeyPapuApplication.CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hey Papu AI • $type")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)

        CallAnnouncementAndControlManager.voiceEngineRef?.speak(
            "Sir, reminder alert for $type: $title"
        )
    }
}

class DailyJobAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val vault = SecurityVault(applicationContext)
        val repo = JobPortalRepository(applicationContext)
        val jobs = repo.searchWorldwideJobs("React WordPress AI Developer", vault.skills)
        val topJob = jobs.firstOrNull() ?: return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(applicationContext, HeyPapuApplication.CHANNEL_JOBS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hey Papu AI: ${jobs.size} New Developer Jobs")
            .setContentText("Top Match (${topJob.matchScore}%): ${topJob.title} at ${topJob.company}")
            .setAutoCancel(true)
            .build()
        manager.notify(7001, notif)
        return Result.success()
    }
}
