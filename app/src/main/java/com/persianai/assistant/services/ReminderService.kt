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
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.DashboardActivity
import com.persianai.assistant.utils.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * بهتر شده ReminderService با بررسی مستمر و بیدار نگه داشتن
 */
class ReminderService : Service() {
    
    private lateinit var smartReminderManager: SmartReminderManager
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val triggeredReminders = ConcurrentHashMap<String, Long>()
    private var isRunning = false
    
    companion object {
        private const val TAG = "ReminderService"
        private const val FOREGROUND_ID = 999
        private const val CHECK_INTERVAL = 10000L // هر 10 ثانیه بررسی کن (نه هر دقیقه)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 ReminderService created")
        
        smartReminderManager = SmartReminderManager(this)
        startForegroundNotification()
        startContinuousReminderCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reminder_service", 
                "یادآوری‌های هوشمند", 
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
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
        
        startForeground(FOREGROUND_ID, notification)
        Log.d(TAG, "✅ Foreground service started")
    }

    private fun startContinuousReminderCheck() {
        isRunning = true
        
        serviceScope.launch {
            while (isRunning) {
                try {
                    checkAndTriggerReminders()
                    delay(CHECK_INTERVAL) // هر 10 ثانیه بررسی کن
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in check loop", e)
                    delay(CHECK_INTERVAL)
                }
            }
        }
        
        Log.d(TAG, "✅ Continuous reminder check started")
    }

    private fun checkAndTriggerReminders() {
        try {
            val now = System.currentTimeMillis()
            val reminders = smartReminderManager.getActiveReminders()
            
            Log.d(TAG, "🔍 Checking ${reminders.size} active reminders...")
            
            for (reminder in reminders) {
                // بررسی کن آیا زمان رسیده و هنوز trigger نشده
                if (reminder.triggerTime <= now && !triggeredReminders.containsKey(reminder.id)) {
                    Log.d(TAG, "⏰ Triggering reminder: ${reminder.title}")
                    
                    triggeredReminders[reminder.id] = now
                    triggerReminder(reminder)
                    
                    // برای یادآوری‌های تکراری، زمان بعدی را محاسبه کن
                    if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                        val nextTime = smartReminderManager.calculateNextTriggerTime(reminder, now)
                        smartReminderManager.updateReminder(reminder.copy(triggerTime = nextTime))
                        triggeredReminders.remove(reminder.id)
                        Log.d(TAG, "🔄 Rescheduled: ${reminder.title}")
                    } else {
                        // برای یادآوری‌های یکبار، بعد از 30 دقیقه حذف کن
                        serviceScope.launch {
                            delay(30 * 60 * 1000)
                            triggeredReminders.remove(reminder.id)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking reminders", e)
        }
    }

    private fun triggerReminder(reminder: SmartReminderManager.SmartReminder) {
        try {
            // بیدار کن دستگاه
            wakeupDevice()
            
            // بررسی نوع آلارم
            val useFullScreen = reminder.alertType == SmartReminderManager.AlertType.FULL_SCREEN ||
                               reminder.tags.any { it.startsWith("use_alarm:true") }
            
            Log.d(TAG, "🔔 Triggering with useFullScreen=$useFullScreen")
            
            if (useFullScreen) {
                showFullScreenAlarm(reminder)
            } else {
                showNotification(reminder)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error triggering reminder", e)
        }
    }

    private fun wakeupDevice() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "PersianAssistant::ReminderWakeLock"
            )
            
            wakeLock.acquire(5 * 60 * 1000L) // 5 دقیقه
            Log.d(TAG, "⚡ Device woken up")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error waking device", e)
        }
    }

    private fun showFullScreenAlarm(reminder: SmartReminderManager.SmartReminder) {
        try {
            val intent = Intent(this, com.persianai.assistant.activities.FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_FROM_BACKGROUND
                putExtra("title", reminder.title)
                putExtra("description", reminder.description)
                putExtra("smart_reminder_id", reminder.id)
            }
            
            Log.d(TAG, "🎬 Starting full-screen activity: ${reminder.title}")
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing full-screen alarm", e)
            showNotification(reminder)
        }
    }

    private fun showNotification(reminder: SmartReminderManager.SmartReminder) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "reminder_alerts",
                    "یادآوری‌های هشدار",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "هشدارهای یادآوری فوری"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    enableLights(true)
                    lightColor = android.graphics.Color.RED
                }
                nm.createNotificationChannel(channel)
            }
            
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            // Action intent for "Mark as Done" button
            val actionIntent = Intent(this, ReminderReceiver::class.java).apply {
                action = "MARK_AS_DONE"
                putExtra("smart_reminder_id", reminder.id)
            }
            val actionPi = PendingIntent.getBroadcast(
                this, 
                reminder.id.hashCode(), 
                actionIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Tap intent to open DashboardActivity
            val tapIntent = Intent(this, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("smart_reminder_id", reminder.id)
                putExtra("open_screen", "reminder")
            }
            val tapPi = PendingIntent.getActivity(
                this,
                reminder.id.hashCode() + 1,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, "reminder_alerts")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ یادآوری")
                .setContentText(reminder.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.description))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(sound)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(tapPi)
                .addAction(0, "✅ انجام شد", actionPi)
                .setAutoCancel(true)
                .build()
            
            nm.notify(reminder.id.hashCode(), notification)
            Log.d(TAG, "✅ Notification shown: ${reminder.title}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 ReminderService destroyed")
        isRunning = false
    }
}
