package com.persianai.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.MainActivity

/**
 * دریافت‌کننده هشدار یادآوری
 */
class ReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "MARK_AS_DONE" -> {
                val message = intent.getStringExtra("message") ?: ""
                val reminderId = intent.getIntExtra("reminder_id", 0)
                markAsDone(context, message, reminderId)
            }
            "SNOOZE_REMINDER" -> {
                val message = intent.getStringExtra("message") ?: ""
                val reminderId = intent.getIntExtra("reminder_id", 0)
                snoozeReminder(context, message)
            }
            else -> {
                val message = intent.getStringExtra("message") ?: "یادآوری"
                val reminderId = intent.getIntExtra("reminder_id", 0)
                val useAlarm = intent.getBooleanExtra("use_alarm", false)
                
                if (useAlarm) {
                    // نمایش آلارم تمام صفحه
                    showFullScreenAlarm(context, message, reminderId)
                } else {
                    // نوتیفیکیشن معمولی
                    showNotification(context, message, reminderId)
                }
            }
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
    
    private fun snoozeReminder(context: Context, message: String) {
        // لغو نوتیفیکیشن
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        
        // تنظیم یادآوری برای 5 دقیقه بعد
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MINUTE, 5)
        
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        
        com.persianai.assistant.utils.SystemIntegrationHelper.setReminder(
            context, 
            message, 
            hour, 
            minute
        )
        
        android.widget.Toast.makeText(context, "⏰ 5 دقیقه بعد یادآوری می‌شود", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun showFullScreenAlarm(context: Context, message: String, reminderId: Int) {
        val intent = Intent(context, com.persianai.assistant.activities.AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("message", message)
            putExtra("reminder_id", reminderId)
        }
        context.startActivity(intent)
    }
    
    private fun showNotification(context: Context, message: String, reminderId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // ایجاد کانال (برای اندروید 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "یادآوری‌ها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "هشدارهای یادآوری"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent برای علامت‌گذاری به عنوان "انجام شد"
        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "MARK_AS_DONE"
            putExtra("message", message)
            putExtra("reminder_id", reminderId)
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
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, 
            reminderId + 1000, 
            snoozeIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // صدای پیش‌فرض
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        // ساخت نوتیفیکیشن با Action Buttons
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏰ یادآوری")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(donePendingIntent)
            .addAction(0, "✅ انجام شد", donePendingIntent)
            .addAction(0, "⏰ بعداً", snoozePendingIntent)
            .build()
        
        notificationManager.notify(reminderId, notification)
    }
    
    companion object {
        private const val CHANNEL_ID = "reminder_channel"
    }
}
