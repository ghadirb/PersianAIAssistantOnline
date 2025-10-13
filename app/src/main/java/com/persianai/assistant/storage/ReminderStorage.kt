package com.persianai.assistant.storage

import android.content.Context
import com.persianai.assistant.models.Reminder
import com.persianai.assistant.models.RepeatType
import org.json.JSONArray
import org.json.JSONObject

class ReminderStorage(context: Context) {
    
    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    
    fun saveReminder(reminder: Reminder) {
        val reminders = getAllReminders().toMutableList()
        reminders.add(reminder)
        saveAll(reminders)
    }
    
    fun getAllReminders(): List<Reminder> {
        val json = prefs.getString("all_reminders", "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val reminders = mutableListOf<Reminder>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            reminders.add(
                Reminder(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    persianDate = obj.getString("persianDate"),
                    time = obj.getString("time"),
                    repeatType = RepeatType.valueOf(obj.getString("repeatType")),
                    isActive = obj.getBoolean("isActive")
                )
            )
        }
        
        return reminders
    }
    
    fun deleteReminder(id: Long) {
        val reminders = getAllReminders().filter { it.id != id }
        saveAll(reminders)
    }
    
    fun updateReminder(reminder: Reminder) {
        val reminders = getAllReminders().map {
            if (it.id == reminder.id) reminder else it
        }
        saveAll(reminders)
    }
    
    private fun saveAll(reminders: List<Reminder>) {
        val jsonArray = JSONArray()
        reminders.forEach { reminder ->
            val obj = JSONObject().apply {
                put("id", reminder.id)
                put("title", reminder.title)
                put("persianDate", reminder.persianDate)
                put("time", reminder.time)
                put("repeatType", reminder.repeatType.name)
                put("isActive", reminder.isActive)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("all_reminders", jsonArray.toString()).apply()
    }
}
