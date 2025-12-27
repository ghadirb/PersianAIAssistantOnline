package com.persianai.assistant.core.modules

import android.content.Context
import android.util.Log
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.ReminderCreateIntent
import com.persianai.assistant.core.intent.ReminderDeleteIntent
import com.persianai.assistant.core.intent.ReminderListIntent
import com.persianai.assistant.core.intent.ReminderUpdateIntent
import com.persianai.assistant.utils.SmartReminderManager

class ReminderModule(context: Context) : BaseModule(context) {
    override val moduleName: String = "Reminder"
    
    private val assistant = AdvancedPersianAssistant(context)
    private val reminderManager = SmartReminderManager(context)

    override suspend fun canHandle(intent: com.persianai.assistant.core.intent.AIIntent): Boolean {
        return intent is ReminderCreateIntent ||
                intent is ReminderListIntent ||
                intent is ReminderDeleteIntent ||
                intent is ReminderUpdateIntent
    }

    override suspend fun execute(
        request: AIIntentRequest,
        intent: com.persianai.assistant.core.intent.AIIntent
    ): AIIntentResult {
        return when (intent) {
            is ReminderCreateIntent -> handleCreate(request, intent)
            is ReminderListIntent -> handleList(request, intent)
            is ReminderDeleteIntent -> handleDelete(request, intent)
            is ReminderUpdateIntent -> handleUpdate(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleCreate(request: AIIntentRequest, intent: ReminderCreateIntent): AIIntentResult {
        logAction("CREATE", "text=${intent.rawText.take(50)}")
        
        return try {
            val result = assistant.processRequest(intent.rawText)
            
            return createResult(
                text = "✅ یادآوری تنظیم شد\n${result.text}",
                intentName = intent.name,
                actionType = "reminder_created"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reminder", e)
            createResult(
                text = "❌ خطا در ایجاد یادآوری: ${e.message}",
                intentName = intent.name,
                success = false
            )
        }
    }

    private suspend fun handleList(request: AIIntentRequest, intent: ReminderListIntent): AIIntentResult {
        logAction("LIST", "category=${intent.category}")
        
        return try {
            val reminders = reminderManager.getActiveReminders()
            
            if (reminders.isEmpty()) {
                return createResult(
                    text = "📭 هیچ یادآوری فعالی وجود ندارد",
                    intentName = intent.name
                )
            }
            
            val formatted = reminders.map { reminder ->
                "⏰ ${reminder.title}\n" +
                "   🕐 ${formatTime(reminder.triggerTime)}\n"
            }.joinToString("\n")
            
            createResult(
                text = "📋 یادآوری‌های فعال:\n\n$formatted",
                intentName = intent.name,
                actionType = "reminder_list"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error listing reminders", e)
            createResult(
                text = "❌ خطا در دریافت لیست یادآوری‌ها",
                intentName = intent.name,
                success = false
            )
        }
    }

    private suspend fun handleDelete(request: AIIntentRequest, intent: ReminderDeleteIntent): AIIntentResult {
        logAction("DELETE", "reminderId=${intent.reminderId}")
        
        return try {
            if (intent.reminderId != null) {
                reminderManager.deleteReminder(intent.reminderId.toString())
                createResult(
                    text = "✅ یادآوری حذف شد",
                    intentName = intent.name,
                    actionType = "reminder_deleted"
                )
            } else {
                createResult(
                    text = "⚠️ شناسه یادآوری مشخص نیست",
                    intentName = intent.name,
                    success = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting reminder", e)
            createResult(
                text = "❌ خطا در حذف یادآوری",
                intentName = intent.name,
                success = false
            )
        }
    }

    private suspend fun handleUpdate(request: AIIntentRequest, intent: ReminderUpdateIntent): AIIntentResult {
        logAction("UPDATE", "reminderId=${intent.reminderId}")
        
        return createResult(
            text = "ℹ️ بروزرسانی یادآوری هنوز پیاده‌سازی نشده",
            intentName = intent.name,
            success = false
        )
    }

    private fun formatTime(millis: Long): String {
        val formatter = java.text.SimpleDateFormat("HH:mm - EEEE", java.util.Locale("fa"))
        return formatter.format(java.util.Date(millis))
    }
}