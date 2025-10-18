package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NeshanAPIManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("neshan_cache", Context.MODE_PRIVATE)
    private val apiKey = "service.649ba7521ba04da595c5ab56413b3c84"
    
    // کش دوربین‌ها و سرعت‌گیرها: 7 روز
    private val CAMERA_CACHE_DURATION = 7 * 24 * 60 * 60 * 1000L
    
    // کش ترافیک: 5 دقیقه
    private val TRAFFIC_CACHE_DURATION = 5 * 60 * 1000L
    
    suspend fun getSpeedCameras(lat: Double, lng: Double, radius: Int = 5000): List<SpeedCamera> {
        val cacheKey = "cameras_${lat}_${lng}"
        val cachedData = prefs.getString(cacheKey, null)
        val cacheTime = prefs.getLong("${cacheKey}_time", 0)
        
        if (cachedData != null && System.currentTimeMillis() - cacheTime < CAMERA_CACHE_DURATION) {
            return parseSpeedCameras(cachedData)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.neshan.org/v1/pois/nearby?lat=$lat&lng=$lng&radius=$radius&category=speed_camera")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Api-Key", apiKey)
                connection.requestMethod = "GET"
                
                val response = connection.inputStream.bufferedReader().readText()
                prefs.edit()
                    .putString(cacheKey, response)
                    .putLong("${cacheKey}_time", System.currentTimeMillis())
                    .apply()
                
                parseSpeedCameras(response)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    suspend fun getTrafficData(lat: Double, lng: Double): TrafficInfo? {
        val cacheKey = "traffic_${lat}_${lng}"
        val cachedData = prefs.getString(cacheKey, null)
        val cacheTime = prefs.getLong("${cacheKey}_time", 0)
        
        if (cachedData != null && System.currentTimeMillis() - cacheTime < TRAFFIC_CACHE_DURATION) {
            return parseTrafficInfo(cachedData)
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.neshan.org/v1/traffic?lat=$lat&lng=$lng")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Api-Key", apiKey)
                connection.requestMethod = "GET"
                
                val response = connection.inputStream.bufferedReader().readText()
                prefs.edit()
                    .putString(cacheKey, response)
                    .putLong("${cacheKey}_time", System.currentTimeMillis())
                    .apply()
                
                parseTrafficInfo(response)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun parseSpeedCameras(json: String): List<SpeedCamera> {
        return try {
            val jsonObj = JSONObject(json)
            val items = jsonObj.optJSONArray("items") ?: return emptyList()
            
            (0 until items.length()).mapNotNull { i ->
                val item = items.getJSONObject(i)
                SpeedCamera(
                    lat = item.getDouble("latitude"),
                    lng = item.getDouble("longitude"),
                    speedLimit = item.optInt("speed_limit", 60)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseTrafficInfo(json: String): TrafficInfo? {
        return try {
            val jsonObj = JSONObject(json)
            TrafficInfo(
                level = jsonObj.optString("level", "normal"),
                speed = jsonObj.optInt("speed", 50)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class SpeedCamera(
        val lat: Double,
        val lng: Double,
        val speedLimit: Int
    )
    
    data class TrafficInfo(
        val level: String,
        val speed: Int
    )
}
