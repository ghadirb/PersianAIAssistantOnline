package com.persianai.assistant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object NeshanAPI {
    private val KEY = com.persianai.assistant.utils.NeshanKeyProvider.getApiKey()
    
    data class Route(val distance: Int, val duration: Int, val polyline: String)
    data class SearchResult(val name: String, val latitude: Double, val longitude: Double)
    data class SpeedCameraData(val latitude: Double, val longitude: Double, val speedLimit: Int, val type: String)
    data class TrafficData(val level: String, val delay: Int, val description: String)
    
    suspend fun getRoute(oLat: Double, oLng: Double, dLat: Double, dLng: Double): Route? = 
        withContext(Dispatchers.IO) {
        try {
            val url = "https://api.neshan.org/v4/direction?type=car&origin=$oLat,$oLng&destination=$dLat,$dLng"
            val conn = URL(url).openConnection()
            conn.setRequestProperty("Api-Key", KEY)
            val json = JSONObject(conn.getInputStream().bufferedReader().readText())
            val leg = json.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0)
            Route(
                leg.getJSONObject("distance").getInt("value"),
                leg.getJSONObject("duration").getInt("value"),
                json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points")
            )
        } catch (e: Exception) { null }
    }
    
    suspend fun searchLocation(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.neshan.org/v1/search?term=$query&lat=35.7&lng=51.4"
            val conn = URL(url).openConnection()
            conn.setRequestProperty("Api-Key", KEY)
            val json = JSONObject(conn.getInputStream().bufferedReader().readText())
            val results = mutableListOf<SearchResult>()
            val items = json.optJSONArray("items") ?: return@withContext results
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val loc = item.getJSONObject("location")
                results.add(SearchResult(
                    item.optString("title", query),
                    loc.getDouble("latitude"),
                    loc.getDouble("longitude")
                ))
            }
            results
        } catch (e: Exception) { emptyList() }
    }
    
    /**
     * دریافت دوربین‌های سرعت در مسیر
     * Note: Neshan API may not have direct speed camera endpoint, using mock data
     */
    suspend fun getSpeedCameras(lat: Double, lng: Double, radius: Int = 1000): List<SpeedCameraData> = 
        withContext(Dispatchers.IO) {
        try {
            // TODO: Replace with actual Neshan API endpoint when available
            // For now, return mock data based on known camera locations in Tehran
            val cameras = mutableListOf<SpeedCameraData>()
            
            // شناسایی دوربین‌های شناخته شده در تهران
            if (lat in 35.6..35.8 && lng in 51.3..51.5) {
                cameras.add(SpeedCameraData(35.699, 51.338, 80, "fixed"))
                cameras.add(SpeedCameraData(35.715, 51.404, 100, "fixed"))
            }
            
            cameras
        } catch (e: Exception) { emptyList() }
    }
    
    /**
     * دریافت اطلاعات ترافیک مسیر
     */
    suspend fun getTrafficInfo(oLat: Double, oLng: Double, dLat: Double, dLng: Double): TrafficData? = 
        withContext(Dispatchers.IO) {
        try {
            // استفاده از API مسیریابی نشان برای دریافت اطلاعات ترافیک
            val route = getRoute(oLat, oLng, dLat, dLng)
            route?.let {
                // تخمین سطح ترافیک بر اساس مدت زمان
                val avgSpeed = (it.distance.toDouble() / it.duration) * 3.6 // km/h
                val (level, delay, desc) = when {
                    avgSpeed > 60 -> Triple("روان", 0, "ترافیک روان است")
                    avgSpeed > 40 -> Triple("نیمه‌سنگین", it.duration / 6, "ترافیک نیمه‌سنگین")
                    avgSpeed > 20 -> Triple("سنگین", it.duration / 3, "ترافیک سنگین")
                    else -> Triple("بسیار سنگین", it.duration / 2, "ترافیک بسیار سنگین")
                }
                TrafficData(level, delay, desc)
            }
        } catch (e: Exception) { null }
    }
    
    /**
     * دریافت محدودیت سرعت جاده
     */
    suspend fun getSpeedLimit(lat: Double, lng: Double): Int? = withContext(Dispatchers.IO) {
        try {
            // TODO: Implement with actual Neshan API endpoint
            // For now, estimate based on location (city vs highway)
            when {
                lat in 35.6..35.8 && lng in 51.3..51.5 -> 60 // تهران - شهری
                else -> 120 // برون‌شهری
            }
        } catch (e: Exception) { null }
    }
}
