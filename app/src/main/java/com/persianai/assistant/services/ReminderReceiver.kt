package com.persianai.assistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.persianai.assistant.activities.FullScreenAlarmActivity
import com.persianai.assistant.utils.SmartReminderManager

/**
 * BroadcastReceiver برای دریافت الارم یادآوری
 * این Receiver زمانی فعال می‌شود که AlarmManager الارم را trigger کند
 */
class ReminderReceiver : BroadcastReceiver() {
    
    private val TAG = "ReminderReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🔔 onReceive - action: ${intent.action}")
        
        // WakeLock بگیری - خیلی مهم!
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "PersianAssistant::ReminderReceiver"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 دقیقه
        }
        
        try {
            Log.d(TAG, "⚡ WakeLock acquired")
            
            when (intent.action) {
                // الارم یادآوری
                "com.persianai.assistant.REMINDER_ALARM" -> {
                    handleReminderAlarm(context, intent)
                }
                // علامت‌گذاری به عنوان انجام شده
                "MARK_AS_DONE" -> {
                    handleMarkAsDone(context, intent)
                }
                // تعویق یادآوری
                "SNOOZE_REMINDER" -> {
                    handleSnoozeReminder(context, intent)
                }
                // بوت تمام
                Intent.ACTION_BOOT_COMPLETED -> {
                    Log.d(TAG, "📱 BOOT_COMPLETED - reschedule reminders")
                    rescheduleAllReminders(context)
                }
                else -> {
                    Log.d(TAG, "Unknown action: ${intent.action}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onReceive", e)
            e.printStackTrace()
        } finally {
            try {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                    Log.d(TAG, "⚡ WakeLock released")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wakelock", e)
            }
        }
    }
    
    /**
     * پردازش Reminder Alarm
     */
    private fun handleReminderAlarm(context: Context, intent: Intent) {
        try {
            val reminderId = intent.getStringExtra("smart_reminder_id")
            val title = intent.getStringExtra("reminder_title") ?: "⏰ یادآوری"
            val description = intent.getStringExtra("reminder_description") ?: ""
            val alertType = intent.getStringExtra("alert_type") ?: "NOTIFICATION"
            
            Log.d(TAG, "📝 Reminder received: title=$title, alertType=$alertType, id=$reminderId")
            
            val useFullScreen = alertType == "FULL_SCREEN"
            
            if (useFullScreen) {
                showFullScreenAlarm(context, title, description, reminderId)
            } else {
                Log.d(TAG, "Notification mode - skipped")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling alarm", e)
        }
    }
    
    /**
     * نمایش Full Screen Alarm - مستقیم
     */
    private fun showFullScreenAlarm(
        context: Context,
        title: String,
        description: String,
        reminderId: String?
    ) {
        try {
            Log.d(TAG, "🎬 Starting FullScreenAlarmActivity directly")
            
            val alarmIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("title", title)
                putExtra("description", description)
                putExtra("smart_reminder_id", reminderId)
            }
            
            context.startActivity(alarmIntent)
            Log.d(TAG, "✅ Activity started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting activity", e)
        }
    }
    
    /**
     * علامت‌گذاری به عنوان انجام شده
     */
    private fun handleMarkAsDone(context: Context, intent: Intent) {
        try {
            val smartReminderId = intent.getStringExtra("smart_reminder_id")
            
            if (!smartReminderId.isNullOrEmpty()) {
                val mgr = SmartReminderManager(context)
                mgr.completeReminder(smartReminderId)
                Log.d(TAG, "✅ Reminder completed: $smartReminderId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error marking as done", e)
        }
    }
    
    /**
     * تعویق یادآوری
     */
    private fun handleSnoozeReminder(context: Context, intent: Intent) {
        try {
            val smartReminderId = intent.getStringExtra("smart_reminder_id")
            
            if (!smartReminderId.isNullOrEmpty()) {
                val mgr = SmartReminderManager(context)
                mgr.snoozeReminder(smartReminderId, 5)
                Log.d(TAG, "⏰ Reminder snoozed: $smartReminderId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error snoozing reminder", e)
        }
    }
    
    /**
     * reschedule تمام یادآوری‌ها
     */
    private fun rescheduleAllReminders(context: Context) {
        try {
            Handler(Looper.getMainLooper()).post {
                val mgr = SmartReminderManager(context)
                val reminders = mgr.getActiveReminders()
                
                Log.d(TAG, "📋 Rescheduling ${reminders.size} reminders...")
                
                for (reminder in reminders) {
                    try {
                        mgr.scheduleReminder(reminder)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scheduling reminder: ${reminder.id}", e)
                    }
                }
                
                Log.d(TAG, "✅ All reminders rescheduled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error rescheduling reminders", e)
        }
    }
}