package com.persianai.assistant.utils

import android.content.Context
import org.json.JSONObject

data class PersianEvent(
    val month: Int,
    val day: Int,
    val title: String,
    val holiday: Boolean,
    val type: String
)

object PersianEvents {
    
    private var events: List<PersianEvent>? = null
    
    fun loadEvents(context: Context) {
        if (events != null) return
        
        try {
            val json = context.assets.open("events.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val array = obj.getJSONArray("Persian Calendar")
            
            val list = mutableListOf<PersianEvent>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(PersianEvent(
                    month = item.getInt("month"),
                    day = item.getInt("day"),
                    title = item.getString("title"),
                    holiday = item.getBoolean("holiday"),
                    type = item.getString("type")
                ))
            }
            events = list
        } catch (e: Exception) {
            events = emptyList()
        }
    }
    
    fun getEventsForDate(month: Int, day: Int): List<PersianEvent> {
        return events?.filter { it.month == month && it.day == day } ?: emptyList()
    }
    
    fun getAllHolidays(): List<PersianEvent> {
        return events?.filter { it.holiday } ?: emptyList()
    }
}
