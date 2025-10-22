package com.persianai.assistant.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NeshanDirectionAPI {
    
    private val apiKey = "service.d81b1f9424414d4ea848931499e60dac"
    
    data class RouteInfo(
        val distance: Double,        // کیلومتر
        val duration: Int,            // دقیقه
        val polyline: String,         // مسیر برای کشیدن روی نقشه
        val steps: List<String>,      // مراحل مسیر
        val summary: String           // خلاصه مسیر
    )
    
    suspend fun getDirection(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): List<RouteInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://api.neshan.org/v4/direction?" +
                    "type=car&" +
                    "origin=$originLat,$originLng&" +
                    "destination=$destLat,$destLng&" +
                    "alternative=true"
                
                Log.d("NeshanDirection", "Requesting routes: $urlString")
                
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Api-Key", apiKey)
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                val responseCode = connection.responseCode
                Log.d("NeshanDirection", "Response code: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d("NeshanDirection", "Response: ${response.take(300)}")
                    parseRoutes(response)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    Log.e("NeshanDirection", "Error: $error")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("NeshanDirection", "Exception: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    private fun parseRoutes(json: String): List<RouteInfo> {
        return try {
            val jsonObj = JSONObject(json)
            val routes = jsonObj.optJSONArray("routes") ?: return emptyList()
            
            (0 until routes.length()).mapNotNull { i ->
                val route = routes.getJSONObject(i)
                val legs = route.optJSONArray("legs")?.getJSONObject(0)
                
                val distance = legs?.optJSONObject("distance")?.optDouble("value", 0.0) ?: 0.0
                val duration = legs?.optJSONObject("duration")?.optInt("value", 0) ?: 0
                val overviewPolyline = route.optJSONObject("overview_polyline")?.optString("points", "") ?: ""
                
                // استخراج مراحل مسیر
                val steps = legs?.optJSONArray("steps")?.let { stepsArray ->
                    (0 until stepsArray.length()).map { j ->
                        stepsArray.getJSONObject(j).optString("instruction", "")
                    }
                } ?: emptyList()
                
                val summary = route.optString("summary", "مسیر پیشنهادی")
                
                RouteInfo(
                    distance = distance / 1000.0,  // متر به کیلومتر
                    duration = duration / 60,      // ثانیه به دقیقه
                    polyline = overviewPolyline,
                    steps = steps,
                    summary = summary
                )
            }
        } catch (e: Exception) {
            Log.e("NeshanDirection", "Parse error: ${e.message}", e)
            emptyList()
        }
    }
}
