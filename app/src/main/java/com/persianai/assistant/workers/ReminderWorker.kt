package com.persianai.assistant.workers

import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.content.Context
import com.persianai.assistant.services.ReminderReceiver
import com.persianai.assistant.utils.SmartReminderManager

/**
 * Worker برای بررسی یادآوری‌های پس‌زمینه
 * این Worker AlarmManager را مدیریت می‌کند
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    
    private val TAG = "ReminderWorker"
    private val smartReminderManager = SmartReminderManager(context)
    
    override fun doWork(): Result {
        return try {
            Log.d(TAG, "🔍 doWork started")
            
            // بررسی یادآوری‌ها
            val reminders = smartReminderManager.getActiveReminders()
            Log.d(TAG, "📋 Found ${reminders.size} active reminders")
            
            if (reminders.isEmpty()) {
                Log.d(TAG, "No reminders to check")
                return Result.success()
            }
            
            // این Worker فقط یادآوری‌ها را بررسی می‌کند
            // AlarmManager مسئول trigger کردن است
            Log.d(TAG, "✅ Worker check completed")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in doWork", e)
            e.printStackTrace()
            Result.retry()
        }
    }
}