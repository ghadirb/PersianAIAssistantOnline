package com.persianai.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask

class ReminderService : Service() {
    private lateinit var smartReminderManager: SmartReminderManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var checkTimer: Timer? = null
    private val checkedReminders = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        smartReminderManager = SmartReminderManager(this)
        startForegroundService()
        startReminderCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("reminder_service", "یادآوری‌ها", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "reminder_service")
            .setContentTitle("🔔 یادآوری‌های هوشمند")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        startForeground(999, notification)
    }

    private fun startReminderCheck() {
        checkTimer = Timer()
        checkTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkAndTriggerReminders()
            }
        }, 0, 60000)
    }

    private fun checkAndTriggerReminders() {
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                val reminders = smartReminderManager.getActiveReminders()
                for (reminder in reminders) {
                    if (reminder.triggerTime <= now && !checkedReminders.contains(reminder.id)) {
                        checkedReminders.add(reminder.id)
                        showNotification(reminder)
                        if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                            val next = smartReminderManager.calculateNextTriggerTime(reminder, now)
                            smartReminderManager.updateReminder(reminder.copy(triggerTime = next))
                            checkedReminders.remove(reminder.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ReminderService", "Error", e)
            }
        }
    }

    private fun showNotification(reminder: SmartReminderManager.SmartReminder) {
        val useFullScreen = reminder.tags.any { it.startsWith("use_alarm:true") }
        if (useFullScreen) {
            val intent = Intent(this, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("title", reminder.title)
                putExtra("description", reminder.description)
                putExtra("smart_reminder_id", reminder.id)
            }
            startActivity(intent)
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("alerts", "هشدارها", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            action = "MARK_AS_DONE"
            putExtra("smart_reminder_id", reminder.id)
        }
        val pi = PendingIntent.getBroadcast(this, reminder.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, "alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ یادآوری")
            .setContentText(reminder.title)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .addAction(0, "✅ انجام شد", pi)
            .build()
        nm.notify(reminder.id.hashCode(), notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        checkTimer?.cancel()
    }
}
