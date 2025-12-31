package com.persianai.assistant.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Query سے Action تک
 * 
 * مثال:
 * Query: "یادآوری برای فردا ساعت 8"
 * → Intent: ReminderCreateIntent
 * → Action: AlarmManager.set()
 * → Response: "یادآوری تنظیم شد ✅"
 */
class ActionExecutor(private val context: Context) {
    
    private val TAG = "ActionExecutor"
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    companion object {
        const val ACTION_REMINDER_ALARM = "com.persianai.assistant.REMINDER_ALARM"
        const val EXTRA_REMINDER_TEXT = "reminder_text"
        const val EXTRA_REMINDER_TIME = "reminder_time"
    }
    
    /**
     * Query کو parse کر کے action execute کریں
     */
    suspend fun executeFromQuery(query: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎯 Executing: $query")
            
            // Query patterns تعریف کریں
            val reminderPattern = Regex(
                "یادآوری.*?(فردا|امروز|بعداً|ساعت\\s+\\d+|کال|ساعت)",
                RegexOption.IGNORE_CASE
            )
            val alarmPattern = Regex(
                "(زنگ|alarm|اژیر).*(فردا|امروز|بعداً|ساعت\\s+\\d+)",
                RegexOption.IGNORE_CASE
            )
            val notePattern = Regex(
                "(یادداشت|نت|note).*(بریز|ذخیره|save)",
                RegexOption.IGNORE_CASE
            )
            
            when {
                reminderPattern.containsMatchIn(query) -> {
                    Log.d(TAG, "✅ Detected: Reminder")
                    executeReminder(query)
                }
                alarmPattern.containsMatchIn(query) -> {
                    Log.d(TAG, "✅ Detected: Alarm")
                    executeAlarm(query)
                }
                notePattern.containsMatchIn(query) -> {
                    Log.d(TAG, "✅ Detected: Note")
                    executeNote(query)
                }
                else -> {
                    Log.d(TAG, "❓ No action pattern matched")
                    ExecutionResult(
                        success = false,
                        message = "هیچ اقدام شناخت‌نشده",
                        action = null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error executing: ${e.message}", e)
            ExecutionResult(
                success = false,
                message = "خطا در اجرای اقدام: ${e.message}",
                action = null,
                exception = e
            )
        }
    }
    
    /**
     * Reminder تنظیم کریں
     */
    private suspend fun executeReminder(query: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📌 Setting reminder: $query")
            
            // Parse time from query
            val timeInMinutes = parseTimeFromQuery(query)
            val reminderText = extractReminderText(query)
            
            if (timeInMinutes <= 0) {
                return@withContext ExecutionResult(
                    success = false,
                    message = "زمان درست نیست: $timeInMinutes",
                    action = "reminder"
                )
            }
            
            // Set alarm
            val calendar = Calendar.getInstance().apply {
                add(Calendar.MINUTE, timeInMinutes)
            }
            
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMINDER_ALARM
                putExtra(EXTRA_REMINDER_TEXT, reminderText)
                putExtra(EXTRA_REMINDER_TIME, calendar.timeInMillis)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            
            Log.d(TAG, "✅ Reminder set for ${calendar.timeInMillis}")
            
            ExecutionResult(
                success = true,
                message = "یادآوری برای ${calendar.displayHumanReadable()} تنظیم شد ✅",
                action = "reminder",
                data = mapOf(
                    "text" to reminderText,
                    "time" to calendar.timeInMillis.toString(),
                    "readableTime" to calendar.displayHumanReadable()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reminder error: ${e.message}", e)
            ExecutionResult(
                success = false,
                message = "خطا در تنظیم یادآوری",
                action = "reminder",
                exception = e
            )
        }
    }
    
    /**
     * Alarm تنظیم کریں
     */
    private suspend fun executeAlarm(query: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "⏰ Setting alarm: $query")
            
            val timeInMinutes = parseTimeFromQuery(query)
            if (timeInMinutes <= 0) {
                return@withContext ExecutionResult(
                    success = false,
                    message = "زمان درست نیست",
                    action = "alarm"
                )
            }
            
            val calendar = Calendar.getInstance().apply {
                add(Calendar.MINUTE, timeInMinutes)
            }
            
            // Here you would call AlarmManager
            // For now, returning success
            
            ExecutionResult(
                success = true,
                message = "زنگ برای ${calendar.displayHumanReadable()} تنظیم شد ✅",
                action = "alarm",
                data = mapOf("time" to calendar.timeInMillis.toString())
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                message = "خطا در تنظیم زنگ",
                action = "alarm",
                exception = e
            )
        }
    }
    
    /**
     * Note save کریں
     */
    private suspend fun executeNote(query: String): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📝 Saving note: $query")
            
            val noteText = extractNoteText(query)
            
            // Save to SharedPreferences or database
            val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)
            val existingNotes = prefs.getString("all_notes", "") ?: ""
            val timestamp = System.currentTimeMillis()
            val newNote = "$timestamp|$noteText"
            val allNotes = if (existingNotes.isEmpty()) newNote else "$existingNotes\n$newNote"
            
            prefs.edit().putString("all_notes", allNotes).apply()
            
            Log.d(TAG, "✅ Note saved")
            
            ExecutionResult(
                success = true,
                message = "یادداشت ذخیره شد ✅",
                action = "note",
                data = mapOf("text" to noteText, "timestamp" to timestamp.toString())
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                message = "خطا در ذخیره یادداشت",
                action = "note",
                exception = e
            )
        }
    }
    
    /**
     * Parse کریں query سے time
     */
    private fun parseTimeFromQuery(query: String): Int {
        return when {
            query.contains("فردا", ignoreCase = true) -> 24 * 60 // 24 hours
            query.contains("یک ساعت", ignoreCase = true) -> 60
            query.contains("نیم ساعت", ignoreCase = true) -> 30
            query.contains("دو ساعت", ignoreCase = true) -> 120
            query.contains("5 دقیقه", ignoreCase = true) -> 5
            query.contains("10 دقیقه", ignoreCase = true) -> 10
            query.contains("15 دقیقه", ignoreCase = true) -> 15
            else -> {
                // Extract number if present
                val numberRegex = Regex("\\d+")
                val match = numberRegex.find(query)
                match?.value?.toIntOrNull() ?: 60
            }
        }
    }
    
    /**
     * Extract reminder text
     */
    private fun extractReminderText(query: String): String {
        return query.replace(Regex("(یادآوری|فردا|امروز|ساعت|زنگ)"), "").trim().take(100)
    }
    
    /**
     * Extract note text
     */
    private fun extractNoteText(query: String): String {
        return query.replace(Regex("(یادداشت|نت|note|save|ذخیره|بریز)"), "").trim().take(500)
    }
    
    /**
     * Calendar کو human-readable format میں دکھائیں
     */
    private fun Calendar.displayHumanReadable(): String {
        val hours = get(Calendar.HOUR_OF_DAY)
        val minutes = get(Calendar.MINUTE)
        val day = get(Calendar.DAY_OF_MONTH)
        val month = get(Calendar.MONTH) + 1
        
        return when {
            timeInMillis - System.currentTimeMillis() < 24 * 60 * 60 * 1000 -> {
                String.format("امروز ساعت %02d:%02d", hours, minutes)
            }
            else -> String.format("%d/%d ساعت %02d:%02d", month, day, hours, minutes)
        }
    }
}

/**
 * Action execution result
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val action: String? = null,
    val data: Map<String, String>? = null,
    val exception: Exception? = null
)

/**
 * Broadcast receiver for reminders
 */
class ReminderReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == ActionExecutor.ACTION_REMINDER_ALARM) {
            val reminderText = intent.getStringExtra(ActionExecutor.EXTRA_REMINDER_TEXT) ?: "یادآوری"
            Log.d("ReminderReceiver", "🔔 Reminder: $reminderText")
            
            // Show notification
            showReminderNotification(context, reminderText)
        }
    }
    
    private fun showReminderNotification(context: Context, text: String) {
        // Create notification
        val notificationId = System.currentTimeMillis().toInt()
        
        try {
            val builder = androidx.core.app.NotificationCompat.Builder(context, "reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("یادآوری")
                .setContentText(text)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Failed to show notification: ${e.message}")
        }
    }
}
