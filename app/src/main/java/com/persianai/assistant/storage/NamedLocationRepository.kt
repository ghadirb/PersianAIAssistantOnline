package com.persianai.assistant.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class NamedLocation(val name: String, val latitude: Double, val longitude: Double)

class NamedLocationRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("named_locations", Context.MODE_PRIVATE)
    private val KEY = "locations_json"

    fun list(): List<NamedLocation> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<NamedLocation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(NamedLocation(obj.getString("name"), obj.getDouble("lat"), obj.getDouble("lng")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(locations: List<NamedLocation>) {
        val arr = JSONArray()
        locations.forEach { l ->
            val obj = JSONObject()
            obj.put("name", l.name)
            obj.put("lat", l.latitude)
            obj.put("lng", l.longitude)
            arr.put(obj)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(location: NamedLocation) {
        val current = list().toMutableList()
        current.add(location)
        save(current)
    }

    fun remove(name: String) {
        val current = list().filterNot { it.name == name }
        save(current)
    }
}
