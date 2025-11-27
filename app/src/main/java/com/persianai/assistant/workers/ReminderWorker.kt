package com.persianai.assistant.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.persianai.assistant.R
import com.persianai.assistant.activities.FullScreenAlarmActivity
import com.persianai.assistant.services.ReminderReceiver
import com.persianai.assistant.utils.SmartReminderManager

/**
 * Worker برای بررسی و نمایش یادآوری‌های پس‌زمینه
 * این Worker هر دقیقه اجرا می‌شود و یادآوری‌های سر‌رسیده را نمایش می‌دهد
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    private val smartReminderManager = SmartReminderManager(context)
    
    override fun doWork(): Result {
        return try {
            Log.d("ReminderWorker", "Checking reminders...")
            checkAndTriggerReminders()
            Result.success()
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Error checking reminders", e)
            Result.retry()
        }
    }
    
    private fun checkAndTriggerReminders() {
        val now = System.currentTimeMillis()
        val reminders = smartReminderManager.getActiveReminders()
        
        for (reminder in reminders) {
            // اگر زمان یادآوری رسیده باشد
            if (reminder.triggerTime <= now) {
                Log.d("ReminderWorker", "Triggering reminder: ${reminder.title}")
                
                // بررسی کن آیا باید تمام‌صفحه نمایش داده شود
                val useFullScreen = reminder.tags.any { it.startsWith("use_alarm:true") }
                
                if (useFullScreen) {
                    showFullScreenAlarm(reminder)
                } else {
                    showNotification(reminder)
                }
                
                // برای یادآوری‌های تکراری، دوباره برنامه‌ریزی کن
                if (reminder.repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
                    val nextTriggerTime = smartReminderManager.calculateNextTriggerTime(reminder, now)
                    smartReminderManager.updateReminder(reminder.copy(triggerTime = nextTriggerTime))
                    Log.d("ReminderWorker", "Rescheduled recurring reminder: ${reminder.title}")
                }
            }
        }
    }
    
    private fun showFullScreenAlarm(reminder: SmartReminderManager.SmartReminder) {
        try {
            // نمایش تمام‌صفحه
            val intent = Intent(applicationContext, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("title", reminder.title)
                putExtra("description", reminder.description)
                putExtra("smart_reminder_id", reminder.id)
            }
            applicationContext.startActivity(intent)
            Log.d("ReminderWorker", "Full-screen alarm shown for: ${reminder.title}")
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Error showing full-screen alarm", e)
            // اگر فعالیت وجود نداشت، نوتیفیکیشن نمایش بده
            showNotification(reminder)
        }
    }
    
    private fun showNotification(reminder: SmartReminderManager.SmartReminder) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reminder_alerts",
                "یادآوری‌های هشدار",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }
        
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val intent = Intent(applicationContext, ReminderReceiver::class.java).apply {
            action = "MARK_AS_DONE"
            putExtra("smart_reminder_id", reminder.id)
        }
        val pi = PendingIntent.getBroadcast(
            applicationContext,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(applicationContext, "reminder_alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ یادآوری")
            .setContentText(reminder.title)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .addAction(0, "✅ انجام شد", pi)
            .setAutoCancel(true)
            .build()
        
        nm.notify(reminder.id.hashCode(), notification)
        Log.d("ReminderWorker", "Notification shown for: ${reminder.title}")
    }
}
