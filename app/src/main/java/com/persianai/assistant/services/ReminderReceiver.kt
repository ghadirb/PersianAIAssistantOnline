package com.persianai.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.utils.SmartReminderManager

/**
 * دریافت‌کننده هشدار یادآوری
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PersianAssistant::ReminderWakeLock"
        )
        wakeLock.acquire(10 * 1000L /* 10 seconds timeout */)

        try {
            android.util.Log.d("ReminderReceiver", "onReceive called with action: ${intent.action}")

            // اگر BOOT_COMPLETED است، تمام یادآوری‌های فعال را دوباره برنامه‌ریزی کن
            if (intent.action == "android.intent.action.BOOT_COMPLETED") {
                android.util.Log.d("ReminderReceiver", "BOOT_COMPLETED received, rescheduling all reminders")
                val mgr = SmartReminderManager(context)
                val reminders = mgr.getActiveReminders()
                for (reminder in reminders) {
                    mgr.scheduleReminder(reminder)
                }
                return
            }

            val reminderId = intent.getIntExtra("reminder_id", 0)
            val smartReminderId = intent.getStringExtra("smart_reminder_id")
            val message = intent.getStringExtra("message") ?: "یادآوری"

            when (intent.action) {
                "MARK_AS_DONE" -> {
                    android.util.Log.d("ReminderReceiver", "Mark as done: $message")
                    if (!smartReminderId.isNullOrEmpty()) {
                        com.persianai.assistant.utils.SmartReminderManager(context)
                            .completeReminder(smartReminderId)
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(reminderId)
                    } else {
                        markAsDone(context, message, reminderId)
                    }
                }
                "SNOOZE_REMINDER" -> {
                    android.util.Log.d("ReminderReceiver", "Snooze reminder: $message")
                    if (!smartReminderId.isNullOrEmpty()) {
                        com.persianai.assistant.utils.SmartReminderManager(context)
                            .snoozeReminder(smartReminderId, 10)
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(reminderId)
                    }
                    // اگر smartReminderId خالی باشد، فعلاً snooze انجام نمی‌دهیم تا با منطق قدیمی تداخل نداشته باشد
                }
                else -> {
                    val useAlarm = intent.getBooleanExtra("use_alarm", false)
                    android.util.Log.d(
                        "ReminderReceiver",
                        "Reminder triggered: $message (useAlarm: $useAlarm)"
                    )

                    if (useAlarm) {
                        showFullScreenAlarm(context, message, reminderId, smartReminderId)
                    } else {
                        showNotification(context, message, reminderId, smartReminderId)
                    }
                    
                    // برای یادآوری‌های تکراری، دوباره برنامه‌ریزی کن
                    if (!smartReminderId.isNullOrEmpty()) {
                        try {
                            val mgr = SmartReminderManager(context)
                            val reminder = mgr.getAllReminders().find { it.id == smartReminderId }
                            if (reminder != null && reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                                // یادآوری را دوباره برنامه‌ریزی کن
                                mgr.addReminder(reminder)
                                android.util.Log.d("ReminderReceiver", "Recurring reminder rescheduled: $message")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ReminderReceiver", "Error rescheduling recurring reminder", e)
                        }
                    }
                }
            }
        } finally {
            wakeLock.release()
        }
    }
    
    private fun markAsDone(context: Context, message: String, reminderId: Int) {
        // لغو نوتیفیکیشن
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId)
        
        // علامت‌گذاری در لیست یادآوری‌ها
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        
        for (i in 0 until count) {
            val savedMessage = prefs.getString("message_$i", "")
            if (savedMessage == message) {
                prefs.edit().putBoolean("completed_$i", true).apply()
                break
            }
        }
        
        android.widget.Toast.makeText(context, "✅ انجام شد", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    // This legacy method is no longer needed as snooze is handled by SmartReminderManager
    /* private fun snoozeReminder(context: Context, message: String) {
        ...
    }*/
    
    private fun showFullScreenAlarm(context: Context, message: String, reminderId: Int, smartReminderId: String?) {
        val intent = Intent(context, com.persianai.assistant.activities.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("message", message)
            putExtra("reminder_id", reminderId)
            putExtra("smart_reminder_id", smartReminderId)
        }
        context.startActivity(intent)
    }
    
    private fun showNotification(context: Context, message: String, reminderId: Int, smartReminderId: String?) {
        android.util.Log.d("ReminderReceiver", "showNotification called for: $message")
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // صدای پیش‌فرض
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // ایجاد کانال (برای اندروید 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .build()
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "یادآوری‌ها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "هشدارهای یادآوری"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setSound(defaultSoundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent برای علامت‌گذاری به عنوان "انجام شد"
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
        
        // Intent برای snooze (5 دقیقه بعد)
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
        
        // ساخت نوتیفیکیشن با Action Buttons
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ یادآوری")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(donePendingIntent)
            .addAction(0, "✅ انجام شد", donePendingIntent)
            .addAction(0, "⏰ بعداً", snoozePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(donePendingIntent, true)
            .build()
        
        notificationManager.notify(reminderId, notification)
        android.util.Log.d("ReminderReceiver", "Notification posted with ID: $reminderId")
    }
    
    companion object {
        private const val CHANNEL_ID = "reminder_channel"
    }
}
