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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.FullScreenAlarmActivity
import com.persianai.assistant.utils.SmartReminderManager

/**
 * دریافت‌کننده هشدار یادآوری
 */
class ReminderReceiver : BroadcastReceiver() {
    
    private val TAG = "ReminderReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "PersianAssistant::ReminderWakeLock"
        )
        
        try {
            wakeLock.acquire(30 * 1000L)
            
            // اگر BOOT_COMPLETED است، تمام یادآوری‌های فعال را دوباره برنامه‌ریزی کن
            if (intent.action == "android.intent.action.BOOT_COMPLETED") {
                Log.d(TAG, "BOOT_COMPLETED received, rescheduling all reminders")
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

            Log.d(TAG, "Processing reminder: ID=$reminderId, SmartID=$smartReminderId, Message=$message")

            when (intent.action) {
                "MARK_AS_DONE" -> {
                    Log.d(TAG, "Mark as done: $message")
                    if (!smartReminderId.isNullOrEmpty()) {
                        SmartReminderManager(context).completeReminder(smartReminderId)
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(reminderId)
                    } else {
                        markAsDone(context, message, reminderId)
                    }
                }
                "SNOOZE_REMINDER" -> {
                    Log.d(TAG, "Snooze reminder: $message")
                    if (!smartReminderId.isNullOrEmpty()) {
                        SmartReminderManager(context).snoozeReminder(smartReminderId, 10)
                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(reminderId)
                    }
                }
                else -> {
                    var useAlarm = intent.getBooleanExtra("use_alarm", false)
                    Log.d(TAG, "Intent useAlarm: $useAlarm, smartReminderId: $smartReminderId")
                    
                    // اگر smartReminderId موجود است، از alertType بررسی کن
                    if (!smartReminderId.isNullOrEmpty()) {
                        try {
                            val mgr = SmartReminderManager(context)
                            val reminder = mgr.getAllReminders().find { it.id == smartReminderId }
                            if (reminder != null) {
                                useAlarm = reminder.alertType == SmartReminderManager.AlertType.FULL_SCREEN ||
                                          reminder.tags.any { it.startsWith("use_alarm:true") }
                                Log.d(TAG, "Found reminder: ${reminder.title}, alertType: ${reminder.alertType}, tags: ${reminder.tags}, useAlarm: $useAlarm")
                            } else {
                                Log.w(TAG, "Reminder not found: $smartReminderId")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error checking reminder alertType", e)
                        }
                    }
                    
                    Log.d(TAG, "Final decision - Triggering reminder: $message (useAlarm: $useAlarm)")

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
                                mgr.addReminder(reminder)
                                Log.d(TAG, "Recurring reminder rescheduled: $message")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error rescheduling recurring reminder", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive", e)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    private fun markAsDone(context: Context, message: String, reminderId: Int) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(reminderId)
            
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
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as done", e)
        }
    }
    
    private fun showFullScreenAlarm(context: Context, message: String, reminderId: Int, smartReminderId: String?) {
        Log.d(TAG, "showFullScreenAlarm called for: $message, smartReminderId: $smartReminderId")
        
        try {
            val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra("title", message)
                putExtra("description", "")
                putExtra("reminder_id", reminderId)
                putExtra("smart_reminder_id", smartReminderId)
            }
            
            Log.d(TAG, "Starting FullScreenAlarmActivity with flags: ${fullScreenIntent.flags}")
            context.startActivity(fullScreenIntent)
            Log.d(TAG, "FullScreenAlarmActivity started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing full screen alarm: ${e.message}", e)
            e.printStackTrace()
            showNotification(context, message, reminderId, smartReminderId)
        }
    }
    
    private fun showNotification(context: Context, message: String, reminderId: Int, smartReminderId: String?) {
        Log.d(TAG, "showNotification called for: $message")
        
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
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
                .build()
            
            notificationManager.notify(reminderId, notification)
            Log.d(TAG, "Notification posted with ID: $reminderId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "reminder_channel"
    }
}
