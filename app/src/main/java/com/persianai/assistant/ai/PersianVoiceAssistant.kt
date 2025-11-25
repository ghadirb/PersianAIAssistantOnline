package com.persianai.assistant.ai

import android.content.Context
import com.persianai.assistant.utils.SmartReminderManager

class PersianVoiceAssistant(context: Context) {

    private val reminderManager = SmartReminderManager(context)

    // This is a simplified placeholder for the original complex logic.
    fun processCommand(command: String): String {
        val normalizedCommand = command.lowercase()
        return when {
            normalizedCommand.contains("یادآوری") && normalizedCommand.contains("لیست") -> {
                listActiveReminders()
            }
            // Add other command processing logic here
            else -> {
                "متاسفم، متوجه دستور شما نشدم."
            }
        }
    }

    private fun listActiveReminders(): String {
        val activeReminders = reminderManager.getActiveReminders()
        return if (activeReminders.isEmpty()) {
            "شما هیچ یادآوری فعالی ندارید."
        } else {
            val remindersText = activeReminders.joinToString("\n") { "- ${it.title}" }
            "یادآوری‌های فعال شما:\n$remindersText"
        }
    }
}
