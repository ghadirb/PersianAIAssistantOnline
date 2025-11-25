package com.persianai.assistant.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.models.Reminder

class ReminderStorage(context: Context) {

    private val prefs = context.getSharedPreferences("reminders_v2", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val reminderListType = object : TypeToken<List<Reminder>>() {}.type

    fun addReminder(reminder: Reminder): Reminder {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        return reminder
    }

    fun getAllReminders(): List<Reminder> {
        val json = prefs.getString("all_reminders", null)
        return if (json != null) {
            gson.fromJson(json, reminderListType)
        } else {
            emptyList()
        }
    }

    fun saveReminders(reminders: List<Reminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString("all_reminders", json).apply()
    }

    fun deleteReminder(id: String) {
        val reminders = getAllReminders().toMutableList()
        reminders.removeAll { it.id == id }
        saveReminders(reminders)
    }
    
    fun getReminder(id: String): Reminder? {
        return getAllReminders().firstOrNull { it.id == id }
    }
    
    fun updateReminder(updatedReminder: Reminder) {
        val reminders = getAllReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == updatedReminder.id }
        if (index != -1) {
            reminders[index] = updatedReminder
            saveReminders(reminders)
        }
    }
}
