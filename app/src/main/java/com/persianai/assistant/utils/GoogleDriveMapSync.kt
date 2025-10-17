package com.persianai.assistant.utils

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class GoogleDriveMapSync(private val context: Context) {
    
    // لینک عمومی Google Drive (فقط خواندن)
    private val DRIVE_FILE_ID = "YOUR_GOOGLE_DRIVE_FILE_ID"
    private val DRIVE_DOWNLOAD_URL = "https://drive.google.com/uc?export=download&id=$DRIVE_FILE_ID"
    
    private val prefs = context.getSharedPreferences("map_corrections", Context.MODE_PRIVATE)
    
    data class MapCorrection(
        val id: String,
        val type: String, // "new_road", "missing_road", "wrong_direction"
        val lat: Double,
        val lng: Double,
        val description: String,
        val timestamp: Long,
        val userId: String
    )
    
    // تشخیص مسیر جدید
    fun detectNewRoute(locations: List<Location>): List<MapCorrection> {
        val corrections = mutableListOf<MapCorrection>()
        
        for (i in 1 until locations.size) {
            val prev = locations[i - 1]
            val curr = locations[i]
            val distance = prev.distanceTo(curr)
            
            // اگر فاصله بیش از 100 متر باشد
            if (distance > 100) {
                val correction = MapCorrection(
                    id = UUID.randomUUID().toString(),
                    type = "new_road",
                    lat = curr.latitude,
                    lng = curr.longitude,
                    description = "مسیر جدید شناسایی شده",
                    timestamp = System.currentTimeMillis(),
                    userId = getDeviceId()
                )
                corrections.add(correction)
            }
        }
        
        if (corrections.isNotEmpty()) {
            saveLocalCorrections(corrections)
        }
        
        return corrections
    }
    
    // ذخیره محلی
    private fun saveLocalCorrections(corrections: List<MapCorrection>) {
        val existing = getLocalCorrections().toMutableList()
        existing.addAll(corrections)
        
        val jsonArray = JSONArray()
        existing.forEach { correction ->
            jsonArray.put(JSONObject().apply {
                put("id", correction.id)
                put("type", correction.type)
                put("lat", correction.lat)
                put("lng", correction.lng)
                put("description", correction.description)
                put("timestamp", correction.timestamp)
                put("userId", correction.userId)
            })
        }
        
        prefs.edit().putString("corrections", jsonArray.toString()).apply()
    }
    
    // دانلود از Google Drive
    suspend fun downloadCorrectionsFromDrive(): List<MapCorrection> = withContext(Dispatchers.IO) {
        try {
            val url = URL(DRIVE_DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val response = connection.inputStream.bufferedReader().readText()
            parseCorrections(response)
        } catch (e: Exception) {
            android.util.Log.e("GoogleDriveMapSync", "Download error: ${e.message}")
            emptyList()
        }
    }
    
    // خواندن اصلاحات محلی
    private fun getLocalCorrections(): List<MapCorrection> {
        val json = prefs.getString("corrections", "[]") ?: "[]"
        return parseCorrections(json)
    }
    
    private fun parseCorrections(json: String): List<MapCorrection> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                MapCorrection(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    lat = obj.getDouble("lat"),
                    lng = obj.getDouble("lng"),
                    description = obj.getString("description"),
                    timestamp = obj.getLong("timestamp"),
                    userId = obj.getString("userId")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // تولید شناسه دستگاه
    private fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }
    
    // ادغام اصلاحات
    suspend fun syncCorrections() {
        val remoteCorrections = downloadCorrectionsFromDrive()
        val localCorrections = getLocalCorrections()
        
        // ادغام بدون تکرار
        val allCorrections = (remoteCorrections + localCorrections)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
        
        // ذخیره
        val jsonArray = JSONArray()
        allCorrections.forEach { correction ->
            jsonArray.put(JSONObject().apply {
                put("id", correction.id)
                put("type", correction.type)
                put("lat", correction.lat)
                put("lng", correction.lng)
                put("description", correction.description)
                put("timestamp", correction.timestamp)
                put("userId", correction.userId)
            })
        }
        
        prefs.edit().putString("corrections", jsonArray.toString()).apply()
        android.util.Log.d("GoogleDriveMapSync", "Synced ${allCorrections.size} corrections")
    }
    
    // صادر کردن برای آپلود دستی
    fun exportCorrectionsForUpload(): String {
        val corrections = getLocalCorrections()
        val jsonArray = JSONArray()
        
        corrections.forEach { correction ->
            jsonArray.put(JSONObject().apply {
                put("id", correction.id)
                put("type", correction.type)
                put("lat", correction.lat)
                put("lng", correction.lng)
                put("description", correction.description)
                put("timestamp", correction.timestamp)
                put("userId", correction.userId)
                put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(correction.timestamp)))
            })
        }
        
        return jsonArray.toString(2) // Pretty print
    }
}
