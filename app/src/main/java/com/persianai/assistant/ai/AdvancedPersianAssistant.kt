package com.persianai.assistant.ai

import android.content.Context
import com.persianai.assistant.utils.SmartReminderManager

class AdvancedPersianAssistant(context: Context) {

    private val reminderManager = SmartReminderManager(context)

    // This is a simplified placeholder for the original complex logic.
    // It demonstrates how to use the new SmartReminderManager.
    fun handleIntent(intent: String, data: Map<String, Any>): String {
        return when (intent) {
            "add_reminder" -> handleAddReminder(data)
            "list_reminders" -> handleListReminders()
            else -> "متوجه نشدم. لطفا دوباره تلاش کنید."
        }
    }

    private fun handleAddReminder(data: Map<String, Any>): String {
        val title = data["message"] as? String ?: return "لطفا متن یادآوری را مشخص کنید."
        val triggerTime = data["triggerTime"] as? Long ?: System.currentTimeMillis()
        val repeatPatternRaw = data["repeat"] as? String ?: "once"

        val repeatPattern = when (repeatPatternRaw) {
            "daily" -> SmartReminderManager.RepeatPattern.DAILY
            "weekly" -> SmartReminderManager.RepeatPattern.WEEKLY
            "monthly" -> SmartReminderManager.RepeatPattern.MONTHLY
            else -> SmartReminderManager.RepeatPattern.ONCE
        }

        if (repeatPattern != SmartReminderManager.RepeatPattern.ONCE) {
            reminderManager.createRecurringReminder(title, triggerTime, repeatPattern)
        } else {
            reminderManager.createSimpleReminder(title, triggerTime)
        }

        return "✅ یادآوری «$title» با موفقیت تنظیم شد."
    }

    private fun handleListReminders(): String {
        val activeReminders = reminderManager.getActiveReminders()
        if (activeReminders.isEmpty()) {
            return "شما هیچ یادآوری فعالی ندارید."
        }
        val remindersText = activeReminders.joinToString("\n") { "- ${it.title}" }
        return "یادآوری‌های فعال شما:\n$remindersText"
    }
}
