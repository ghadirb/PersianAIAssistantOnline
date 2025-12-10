package com.persianai.assistant.navigation

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

/**
 * مدیریت مکان‌های ذخیره شده کاربر
 */
class SavedLocationsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("saved_locations", Context.MODE_PRIVATE)
    
    data class SavedLocation(
        val id: String,
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val category: String, // home, work, favorite
        val timestamp: Long,
        val source: String = "manual" // manual | shared | neshan | gmaps
    )
    
    /**
     * ذخیره مکان جدید
     */
    fun saveLocation(name: String, address: String, latLng: LatLng, category: String, source: String = "manual"): Boolean {
        return try {
            val locations = getAllLocations().toMutableList()
            val newLocation = SavedLocation(
                id = System.currentTimeMillis().toString(),
                name = name,
                address = address,
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                category = category,
                timestamp = System.currentTimeMillis(),
                source = source
            )
            locations.add(newLocation)
            saveAllLocations(locations)
            true
        } catch (e: Exception) {
            android.util.Log.e("SavedLocations", "Error saving location", e)
            false
        }
    }
    
    /**
     * دریافت تمام مکان‌های ذخیره شده
     */
    fun getAllLocations(): List<SavedLocation> {
        val locationsJson = prefs.getString("locations", "[]") ?: "[]"
        val locations = mutableListOf<SavedLocation>()
        
        try {
            val jsonArray = JSONArray(locationsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                locations.add(
                    SavedLocation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        address = obj.optString("address", ""),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        category = obj.optString("category", "favorite"),
                        timestamp = obj.optLong("timestamp", 0),
                        source = obj.optString("source", "manual")
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SavedLocations", "Error loading locations", e)
        }
        
        return locations
    }

    /**
     * نام مستعار برای استفاده در دستیار مکالمه‌ای
     */
    fun getSavedLocations(): List<SavedLocation> = getAllLocations()
    
    /**
     * حذف مکان
     */
    fun deleteLocation(id: String): Boolean {
        return try {
            val locations = getAllLocations().filter { it.id != id }
            saveAllLocations(locations)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * دریافت مکان‌ها بر اساس دسته
     */
    fun getLocationsByCategory(category: String): List<SavedLocation> {
        return getAllLocations().filter { it.category == category }
    }
    
    /**
     * آیا این مکان قبلا ذخیره شده؟
     */
    fun isLocationSaved(latLng: LatLng): Boolean {
        return getAllLocations().any { 
            Math.abs(it.latitude - latLng.latitude) < 0.0001 && 
            Math.abs(it.longitude - latLng.longitude) < 0.0001
        }
    }
    
    /**
     * جست‌وجوی مکان ذخیره شده بر اساس نام (تقریبی)
     */
    fun findByName(name: String): SavedLocation? {
        val query = name.trim().lowercase()
        if (query.isEmpty()) return null
        return getAllLocations().firstOrNull { it.name.lowercase().contains(query) }
    }
    
    /**
     * ذخیره یا به‌روزرسانی مکان بر اساس مختصات اگر قبلا ثبت شده باشد
     */
    fun upsertLocation(name: String, address: String, latLng: LatLng, category: String = "favorite", source: String = "shared"): Boolean {
        return try {
            val existing = getAllLocations().firstOrNull { 
                Math.abs(it.latitude - latLng.latitude) < 0.0001 && 
                Math.abs(it.longitude - latLng.longitude) < 0.0001
            }
            val locations = getAllLocations().toMutableList().filter {
                !(Math.abs(it.latitude - latLng.latitude) < 0.0001 && Math.abs(it.longitude - latLng.longitude) < 0.0001)
            }.toMutableList()
            val newItem = SavedLocation(
                id = existing?.id ?: System.currentTimeMillis().toString(),
                name = name,
                address = address,
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                category = category,
                timestamp = System.currentTimeMillis(),
                source = source
            )
            locations.add(newItem)
            saveAllLocations(locations)
            true
        } catch (e: Exception) {
            android.util.Log.e("SavedLocations", "Error upserting location", e)
            false
        }
    }
    
    private fun saveAllLocations(locations: List<SavedLocation>) {
        val jsonArray = JSONArray()
        locations.forEach { location ->
            val obj = JSONObject()
            obj.put("id", location.id)
            obj.put("name", location.name)
            obj.put("address", location.address)
            obj.put("latitude", location.latitude)
            obj.put("longitude", location.longitude)
            obj.put("category", location.category)
            obj.put("timestamp", location.timestamp)
            obj.put("source", location.source)
            jsonArray.put(obj)
        }
        prefs.edit().putString("locations", jsonArray.toString()).apply()
    }
    
    /**
     * تنظیم خانه
     */
    fun setHome(latLng: LatLng, address: String) {
        // حذف خانه قبلی
        val locations = getAllLocations().filter { it.category != "home" }.toMutableList()
        locations.add(SavedLocation(
            id = "home",
            name = "🏠 خانه",
            address = address,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            category = "home",
            timestamp = System.currentTimeMillis(),
            source = "manual"
        ))
        saveAllLocations(locations)
    }
    
    /**
     * تنظیم محل کار
     */
    fun setWork(latLng: LatLng, address: String) {
        // حذف محل کار قبلی
        val locations = getAllLocations().filter { it.category != "work" }.toMutableList()
        locations.add(SavedLocation(
            id = "work",
            name = "💼 محل کار",
            address = address,
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            category = "work",
            timestamp = System.currentTimeMillis(),
            source = "manual"
        ))
        saveAllLocations(locations)
    }
    
    /**
     * دریافت خانه
     */
    fun getHome(): SavedLocation? {
        return getAllLocations().find { it.category == "home" }
    }
    
    /**
     * دریافت محل کار
     */
    fun getWork(): SavedLocation? {
        return getAllLocations().find { it.category == "work" }
    }
}
