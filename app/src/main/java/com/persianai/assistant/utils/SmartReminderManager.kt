package com.persianai.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Reminder
import java.util.UUID

// Simplified SmartReminder model to match the project's current state
data class SmartReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val triggerTime: Long,
    var isCompleted: Boolean = false
)

class SmartReminderManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_reminders_v2", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val reminderListType = object : TypeToken<List<SmartReminder>>() {}.type

    fun createSimpleReminder(title: String, description: String = "", triggerTime: Long): SmartReminder {
        val reminder = SmartReminder(title = title, description = description, triggerTime = triggerTime)
        addReminder(reminder)
        // Schedule the reminder using the new scheduler
        ReminderScheduler.scheduleReminder(context, reminder.toSimpleReminder())
        return reminder
    }

    fun addReminder(reminder: SmartReminder) {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
    }

    fun getAllReminders(): List<SmartReminder> {
        val json = prefs.getString("all_reminders", null)
        return if (json != null) {
            gson.fromJson(json, reminderListType)
        } else {
            emptyList()
        }
    }

    private fun saveReminders(reminders: List<SmartReminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString("all_reminders", json).apply()
    }

    fun updateReminder(updatedReminder: SmartReminder) {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == updatedReminder.id }
        if (index != -1) {
            reminders[index] = updatedReminder
            saveReminders(reminders)
            // Reschedule the reminder
            ReminderScheduler.cancelReminder(context, reminders[index].toSimpleReminder())
            ReminderScheduler.scheduleReminder(context, updatedReminder.toSimpleReminder())
        }
    }

    fun deleteReminder(id: String) {
        val reminders = getAllReminders().toMutableList()
        val reminderToRemove = reminders.firstOrNull { it.id == id }
        if (reminderToRemove != null) {
            reminders.remove(reminderToRemove)
            saveReminders(reminders)
            // Cancel the reminder
            ReminderScheduler.cancelReminder(context, reminderToRemove.toSimpleReminder())
        }
    }
}

// Extension function to convert SmartReminder to the simpler Reminder model used by the scheduler
fun SmartReminder.toSimpleReminder(): Reminder {
    return Reminder(
        id = this.id,
        message = this.title,
        timestamp = this.triggerTime,
        isCompleted = this.isCompleted
    )
}
