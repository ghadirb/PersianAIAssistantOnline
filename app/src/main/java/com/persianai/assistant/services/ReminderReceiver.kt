package com.persianai.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.FullScreenAlarmActivity
import com.persianai.assistant.utils.SmartReminderManager

/**
 * بهتر شده ReminderReceiver برای پردازش broadcast events
 */
class ReminderReceiver : BroadcastReceiver() {
    
    private val TAG = "ReminderReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 onReceive called with action: ${intent.action}")
        
        // دریافت WakeLock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "PersianAssistant::ReminderWakeLock"
        )
        
        try {
            wakeLock.acquire(10 * 60 * 1000L) // 10 دقیقه
            Log.d(TAG, "⚡ WakeLock acquired")
            
            // اگر BOOT_COMPLETED است
            if (intent.action == "android.intent.action.BOOT_COMPLETED") {
                Log.d(TAG, "📱 BOOT_COMPLETED - reschedule reminders")
                rescheduleAllReminders(context)
                return
            }

            val reminderId = intent.getIntExtra("reminder_id", 0)
            val smartReminderId = intent.getStringExtra("smart_reminder_id")
            val message = intent.getStringExtra("message") ?: "یادآوری"

            Log.d(TAG, "📝 Processing: ID=$reminderId, SmartID=$smartReminderId, Message=$message")
            
            when (intent.action) {
                "MARK_AS_DONE" -> {
                    Log.d(TAG, "✅ Mark as done: $message")
                    handleMarkAsDone(context, smartReminderId, reminderId)
                }
                
                "SNOOZE_REMINDER" -> {
                    Log.d(TAG, "⏰ Snooze reminder: $message")
                    handleSnoozeReminder(context, smartReminderId, reminderId)
                }
                
                else -> {
                    // Reminder alarm
                    Log.d(TAG, "🔔 Default action - showing reminder")
                    handleReminderAlarm(context, smartReminderId, message, reminderId, intent)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onReceive", e)
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wakelock", e)
            }
        }
    }
    
    /**
     * پردازش علامت‌گذاری یادآوری به عنوان انجام شده
     */
    private fun handleMarkAsDone(context: Context, smartReminderId: String?, reminderId: Int) {
        if (!smartReminderId.isNullOrEmpty()) {
            try {
                val mgr = SmartReminderManager(context)
                mgr.completeReminder(smartReminderId)
                Log.d(TAG, "✅ Reminder completed: $smartReminderId")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing reminder", e)
            }
        }
        
        // حذف notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId)
    }
    
    /**
     * پردازش تعویق یادآوری
     */
    private fun handleSnoozeReminder(context: Context, smartReminderId: String?, reminderId: Int) {
        if (!smartReminderId.isNullOrEmpty()) {
            try {
                val mgr = SmartReminderManager(context)
                mgr.snoozeReminder(smartReminderId, 5)
                Log.d(TAG, "⏰ Reminder snoozed: $smartReminderId")
            } catch (e: Exception) {
                Log.e(TAG, "Error snoozing reminder", e)
            }
        }
        
        // حذف notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId)
    }
    
    /**
     * پردازش alarm یادآوری
     */
    private fun handleReminderAlarm(
        context: Context,
        smartReminderId: String?,
        message: String,
        reminderId: Int,
        intent: Intent
    ) {
        var useFullScreen = false
        
        // 1️⃣ ابتدا از Intent بررسی کن
        val alertTypeFromIntent = intent.getStringExtra("alert_type")
        Log.d(TAG, "📦 Intent alert_type: $alertTypeFromIntent")
        
        if (alertTypeFromIntent == "FULL_SCREEN") {
            useFullScreen = true
            Log.d(TAG, "✅ Alert type from Intent: FULL_SCREEN")
        } else if (alertTypeFromIntent != null) {
            useFullScreen = false
            Log.d(TAG, "✅ Alert type from Intent: NOTIFICATION")
        } else {
            // 2️⃣ اگر Intent خالی بود، از DB بررسی کن
            if (!smartReminderId.isNullOrEmpty()) {
                try {
                    val mgr = SmartReminderManager(context)
                    val reminder = mgr.getAllReminders().find { it.id == smartReminderId }
                    if (reminder != null) {
                        useFullScreen = reminder.alertType == SmartReminderManager.AlertType.FULL_SCREEN
                        Log.d(TAG, "🔎 Found reminder in DB - alertType: ${reminder.alertType}, useFullScreen: $useFullScreen")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking reminder type from DB", e)
                }
            }
        }
        
        Log.d(TAG, "🔔 Final decision - useFullScreen: $useFullScreen")
        
        if (useFullScreen) {
            // نمایش تمام‌صفحه
            Handler(Looper.getMainLooper()).postDelayed({
                showFullScreenAlarm(context, message, reminderId, smartReminderId)
            }, 300)
        } else {
            // نمایش notification
            showNotification(context, message, reminderId, smartReminderId)
        }
    }
    
    /**
     * نمایش تمام‌صفحه
     */
    private fun showFullScreenAlarm(
        context: Context,
        message: String,
        reminderId: Int,
        smartReminderId: String?
    ) {
        try {
            val intent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_FROM_BACKGROUND
                putExtra("title", message)
                putExtra("description", "")
                putExtra("reminder_id", reminderId)
                putExtra("smart_reminder_id", smartReminderId)
            }
            
            Log.d(TAG, "🎬 Starting full-screen activity: $message")
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing full-screen", e)
            showNotification(context, message, reminderId, smartReminderId)
        }
    }
    
    /**
     * نمایش notification
     */
    private fun showNotification(
        context: Context,
        message: String,
        reminderId: Int,
        smartReminderId: String?
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // ایجاد channel
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
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
            
            // دریافت صدا
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            // ایجاد pending intent برای دکمه انجام شد
            val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "MARK_AS_DONE"
                putExtra("message", message)
                putExtra("reminder_id", reminderId)
                putExtra("smart_reminder_id", smartReminderId)
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                context, 
                reminderId, 
                doneIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // ایجاد pending intent برای دکمه تعویق
            val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = "SNOOZE_REMINDER"
                putExtra("message", message)
                putExtra("reminder_id", reminderId)
                putExtra("smart_reminder_id", smartReminderId)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context, 
                reminderId + 1000, 
                snoozeIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // ایجاد notification
            val notification = NotificationCompat.Builder(context, "reminder_alerts")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ یادآوری")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSound(sound)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(donePendingIntent)
                .addAction(0, "✅ انجام شد", donePendingIntent)
                .addAction(0, "⏰ 5 دقیقه بعد", snoozePendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(donePendingIntent, true)
                .build()
            
            nm.notify(reminderId, notification)
            Log.d(TAG, "✅ Notification shown: $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing notification", e)
        }
    }
    
    /**
     * reschedule تمام یادآوری‌ها پس از بوت
     */
    private fun rescheduleAllReminders(context: Context) {
        try {
            val mgr = SmartReminderManager(context)
            val reminders = mgr.getActiveReminders()
            
            Log.d(TAG, "📋 Rescheduling ${reminders.size} reminders...")
            
            for (reminder in reminders) {
                mgr.scheduleReminder(reminder)
            }
            
            Log.d(TAG, "✅ All reminders rescheduled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling reminders", e)
        }
    }
}
