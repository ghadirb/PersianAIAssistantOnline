package com.persianai.assistant.ml

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class LocationHistoryManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("location_history", Context.MODE_PRIVATE)
    private val MAX_RECORDS = 1000
    
    data class LocationRecord(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val dayOfWeek: Int,
        val hourOfDay: Int,
        val speed: Float = 0f
    )
    
    fun recordLocation(location: Location) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        
        val record = LocationRecord(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis(),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            speed = location.speed
        )
        
        val records = getAllRecords().toMutableList()
        records.add(record)
        
        if (records.size > MAX_RECORDS) {
            records.removeAt(0)
        }
        
        saveRecords(records)
    }
    
    fun getAllRecords(): List<LocationRecord> {
        val json = prefs.getString("records", "[]") ?: "[]"
        val records = mutableListOf<LocationRecord>()
        
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                records.add(LocationRecord(
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng"),
                    timestamp = obj.getLong("time"),
                    dayOfWeek = obj.getInt("dow"),
                    hourOfDay = obj.getInt("hour"),
                    speed = obj.optDouble("speed", 0.0).toFloat()
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationHistory", "Error loading", e)
        }
        
        return records
    }
    
    private fun saveRecords(records: List<LocationRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            val obj = JSONObject()
            obj.put("lat", record.latitude)
            obj.put("lng", record.longitude)
            obj.put("time", record.timestamp)
            obj.put("dow", record.dayOfWeek)
            obj.put("hour", record.hourOfDay)
            obj.put("speed", record.speed)
            array.put(obj)
        }
        prefs.edit().putString("records", array.toString()).apply()
    }
    
    fun getFrequentLocations(): List<Pair<Double, Double>> {
        val records = getAllRecords()
        val clusters = mutableMapOf<Pair<Int, Int>, Int>()
        
        records.forEach { record ->
            val key = Pair((record.latitude * 100).toInt(), (record.longitude * 100).toInt())
            clusters[key] = (clusters[key] ?: 0) + 1
        }
        
        return clusters.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { Pair(it.key.first / 100.0, it.key.second / 100.0) }
    }
}
