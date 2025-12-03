package com.persianai.assistant.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.persianai.assistant.services.ReminderReceiver
import com.persianai.assistant.utils.SmartReminderManager

/**
 * Worker برای بررسی و نمایش یادآوری‌های پس‌زمینه
 * بهبود شده برای استفاده صحیح از AlarmManager
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    private val smartReminderManager = SmartReminderManager(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val TAG = "ReminderWorker"
    
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "🔍 Checking reminders...")
            checkAndTriggerReminders()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking reminders", e)
            e.printStackTrace()
            Result.retry()
        }
    }
    
    private fun checkAndTriggerReminders() {
        val now = System.currentTimeMillis()
        
        try {
            val reminders = smartReminderManager.getActiveReminders()
            Log.d(TAG, "📋 Found ${reminders.size} active reminders")
            
            for (reminder in reminders) {
                // اگر زمان یادآوری رسیده باشد
                if (reminder.triggerTime <= now) {
                    Log.d(TAG, "⏰ Triggering reminder: ${reminder.title}")
                    
                    // بررسی نوع هشدار
                    val useFullScreen = reminder.alertType == SmartReminderManager.AlertType.FULL_SCREEN ||
                                       reminder.tags.any { it.startsWith("use_alarm:true") }
                    
                    Log.d(TAG, "🔔 Alert Type: ${if (useFullScreen) "FULL_SCREEN" else "NOTIFICATION"}")
                    
                    // نمایش فوری یا برنامه‌ریزی
                    triggerReminder(
                        reminder.id,
                        reminder.title,
                        reminder.description,
                        useFullScreen,
                        reminder.priority.ordinal
                    )
                    
                    // برای یادآوری‌های تکراری، دوباره برنامه‌ریزی کن
                    if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                        val nextTriggerTime = smartReminderManager.calculateNextTriggerTime(reminder, now)
                        smartReminderManager.updateReminder(reminder.copy(triggerTime = nextTriggerTime))
                        Log.d(TAG, "🔄 Rescheduled recurring reminder: ${reminder.title}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in checkAndTriggerReminders", e)
            throw e
        }
    }
    
    private fun triggerReminder(
        reminderId: String,
        title: String,
        description: String,
        useFullScreen: Boolean,
        priority: Int
    ) {
        try {
            val intent = Intent(applicationContext, ReminderReceiver::class.java).apply {
                action = "REMINDER_ALERT"
                putExtra("smart_reminder_id", reminderId)
                putExtra("message", title)
                putExtra("description", description)
                putExtra("alert_type", if (useFullScreen) "FULL_SCREEN" else "NOTIFICATION")
                putExtra("priority", priority)
                putExtra("reminder_id", reminderId.hashCode())
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // فوری trigger کن
            Log.d(TAG, "🎯 Triggering reminder immediately: $title")
            applicationContext.sendBroadcast(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error triggering reminder", e)
            throw e
        }
    }
}