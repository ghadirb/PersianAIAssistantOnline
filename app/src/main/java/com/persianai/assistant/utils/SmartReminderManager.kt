package com.persianai.assistant.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Reminder
import java.util.Calendar
import java.util.UUID

class SmartReminderManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_reminders_v3", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val reminderListType = object : TypeToken<List<SmartReminder>>() {}.type

    // Enums for different reminder properties
    enum class ReminderType { SIMPLE, RECURRING, BIRTHDAY, BILL_PAYMENT }
    enum class RepeatPattern { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY }

    data class SmartReminder(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val description: String = "",
        val type: ReminderType = ReminderType.SIMPLE,
        var triggerTime: Long,
        val repeatPattern: RepeatPattern = RepeatPattern.ONCE,
        var isCompleted: Boolean = false,
        var snoozeCount: Int = 0,
        var lastSnoozed: Long = 0
    )

    fun createSimpleReminder(title: String, triggerTime: Long): SmartReminder {
        val reminder = SmartReminder(title = title, triggerTime = triggerTime)
        return addReminder(reminder)
    }

    fun createRecurringReminder(title: String, firstTriggerTime: Long, repeatPattern: RepeatPattern): SmartReminder {
        val reminder = SmartReminder(title = title, triggerTime = firstTriggerTime, type = ReminderType.RECURRING, repeatPattern = repeatPattern)
        return addReminder(reminder)
    }
    
    fun createBirthdayReminder(personName: String, birthdayDate: Long): SmartReminder {
        val reminder = SmartReminder(
            title = "تولد $personName",
            triggerTime = birthdayDate,
            type = ReminderType.BIRTHDAY,
            repeatPattern = RepeatPattern.YEARLY
        )
        return addReminder(reminder)
    }

    private fun addReminder(reminder: SmartReminder): SmartReminder {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        ReminderScheduler.scheduleReminder(context, reminder.toSimpleReminder())
        return reminder
    }

    fun completeReminder(id: String): Boolean {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == id }
        if (index != -1) {
            val reminder = reminders[index]
            if (reminder.repeatPattern == RepeatPattern.ONCE) {
                reminders[index] = reminder.copy(isCompleted = true)
                ReminderScheduler.cancelReminder(context, reminder.toSimpleReminder())
            } else {
                val nextTime = calculateNextTriggerTime(reminder)
                reminders[index] = reminder.copy(triggerTime = nextTime)
                ReminderScheduler.cancelReminder(context, reminder.toSimpleReminder())
                ReminderScheduler.scheduleReminder(context, reminders[index].toSimpleReminder())
            }
            saveReminders(reminders)
            return true
        }
        return false
    }

    fun snoozeReminder(id: String, minutes: Int): Boolean {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == id }
        if (index != -1) {
            val reminder = reminders[index]
            val newTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            reminders[index] = reminder.copy(
                triggerTime = newTime,
                snoozeCount = reminder.snoozeCount + 1,
                lastSnoozed = System.currentTimeMillis()
            )
            saveReminders(reminders)
            ReminderScheduler.cancelReminder(context, reminder.toSimpleReminder())
            ReminderScheduler.scheduleReminder(context, reminders[index].toSimpleReminder())
            return true
        }
        return false
    }

    fun getAllReminders(): List<SmartReminder> {
        val json = prefs.getString("all_reminders", null)
        return if (json != null) gson.fromJson(json, reminderListType) else emptyList()
    }

    fun getActiveReminders(): List<SmartReminder> {
        return getAllReminders().filter { !it.isCompleted }
    }

    private fun saveReminders(reminders: List<SmartReminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString("all_reminders", json).apply()
    }

    private fun calculateNextTriggerTime(reminder: SmartReminder): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }
        when (reminder.repeatPattern) {
            RepeatPattern.DAILY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
            RepeatPattern.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatPattern.MONTHLY -> calendar.add(Calendar.MONTH, 1)
            RepeatPattern.YEARLY -> calendar.add(Calendar.YEAR, 1)
            RepeatPattern.ONCE -> { /* Do nothing */ }
        }
        return calendar.timeInMillis
    }

    private fun SmartReminder.toSimpleReminder(): Reminder {
        return Reminder(
            id = this.id,
            message = this.title,
            timestamp = this.triggerTime,
            isRepeating = this.repeatPattern != RepeatPattern.ONCE,
            isCompleted = this.isCompleted
        )
    }
}
