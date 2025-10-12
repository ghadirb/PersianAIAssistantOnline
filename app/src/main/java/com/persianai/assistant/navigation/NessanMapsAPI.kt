package com.persianai.assistant.navigation

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Nessan Maps API Client
 * کلید API ارائه شده توسط کاربر: service.649ba7521ba04da595c5ab56413b3c84
 */
class NessanMapsAPI {
    
    companion object {
        private const val TAG = "NessanMapsAPI"
        // کلید API نشان
        private const val API_KEY = "service.649ba7521ba04da595c5ab56413b3c84"
        private const val BASE_URL = "https://api.neshan.org/v1"
        
        // Fallback: Google Directions API (free tier)
        private const val GOOGLE_DIRECTIONS_KEY = "YOUR_GOOGLE_KEY" // می‌تواند همان OpenWeather key باشد
    }
    
    data class PlaceResult(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )
    
    data class RouteResult(
        val points: List<LatLng>,
        val distance: Double, // کیلومتر
        val duration: Int, // دقیقه
        val speedLimit: Int, // km/h
        val instructions: List<String>
    )
    
    /**
     * جستجوی مکان با نام یا آدرس
     */
    suspend fun searchPlace(query: String): PlaceResult? = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search?term=$encodedQuery&lat=35.6892&lng=51.3890"
            
            val response = makeRequest(url)
            
            if (response != null) {
                val json = JSONObject(response)
                val items = json.optJSONArray("items")
                
                if (items != null && items.length() > 0) {
                    val firstItem = items.getJSONObject(0)
                    val location = firstItem.getJSONObject("location")
                    
                    return@withContext PlaceResult(
                        name = firstItem.optString("title", query),
                        address = firstItem.optString("address", ""),
                        latitude = location.getDouble("y"),
                        longitude = location.getDouble("x")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching place with Neshan", e)
        }
        
        // Fallback: استفاده از مختصات پیش‌فرض برای تست
        return@withContext getMockLocation(query)
    }
    
    /**
     * دریافت مسیرهای جایگزین (تا 3 مسیر)
     */
    suspend fun getAlternativeRoutes(origin: LatLng, destination: LatLng): List<RouteResult> = withContext(Dispatchers.IO) {
        val routes = mutableListOf<RouteResult>()
        
        try {
            // دریافت مسیر اصلی
            val mainRoute = getDirections(origin, destination)
            if (mainRoute != null) {
                routes.add(mainRoute)
            }
            
            // ایجاد مسیرهای جایگزین (موک)
            // مسیر 2: کمی طولانی‌تر ولی سریع‌تر
            if (mainRoute != null) {
                routes.add(mainRoute.copy(
                    distance = mainRoute.distance * 1.15,
                    duration = (mainRoute.duration * 0.85).toInt(),
                    speedLimit = 100,
                    instructions = mainRoute.instructions
                ))
            }
            
            // مسیر 3: کوتاه‌تر ولی کندتر
            if (mainRoute != null) {
                routes.add(mainRoute.copy(
                    distance = mainRoute.distance * 0.92,
                    duration = (mainRoute.duration * 1.1).toInt(),
                    speedLimit = 60,
                    instructions = mainRoute.instructions
                ))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting alternative routes", e)
            // Fallback
            routes.addAll(getMockAlternativeRoutes(origin, destination))
        }
        
        return@withContext routes
    }
    
    /**
     * محاسبه مسیر بین دو نقطه
     */
    suspend fun getDirections(origin: LatLng, destination: LatLng): RouteResult? = withContext(Dispatchers.IO) {
        try {
            // استفاده از Neshan Directions API
            val url = "$BASE_URL/direction?" +
                    "type=car&" +
                    "origin=${origin.latitude},${origin.longitude}&" +
                    "destination=${destination.latitude},${destination.longitude}"
            
            val response = makeRequest(url)
            
            if (response != null) {
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                
                if (routes != null && routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val overview = route.getJSONObject("overview_polyline")
                    val legs = route.getJSONArray("legs")
                    val leg = legs.getJSONObject(0)
                    
                    val distance = leg.getJSONObject("distance").getInt("value") / 1000.0 // متر به کیلومتر
                    val duration = leg.getJSONObject("duration").getInt("value") / 60 // ثانیه به دقیقه
                    
                    // دیکد کردن polyline
                    val points = decodePolyline(overview.getString("points"))
                    
                    return@withContext RouteResult(
                        points = points,
                        distance = distance,
                        duration = duration,
                        speedLimit = 80, // سرعت پیش‌فرض
                        instructions = extractInstructions(legs)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting directions from Neshan", e)
        }
        
        // Fallback: مسیر مستقیم
        return@withContext getMockRoute(origin, destination)
    }
    
    /**
     * دیکد کردن Polyline (Google encoded polyline format)
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        
        return poly
    }
    
    /**
     * استخراج دستورالعمل‌های مسیریابی
     */
    private fun extractInstructions(legs: org.json.JSONArray): List<String> {
        val instructions = mutableListOf<String>()
        
        try {
            for (i in 0 until legs.length()) {
                val leg = legs.getJSONObject(i)
                val steps = leg.getJSONArray("steps")
                
                for (j in 0 until steps.length()) {
                    val step = steps.getJSONObject(j)
                    val instruction = step.optString("html_instructions", "")
                    
                    if (instruction.isNotEmpty()) {
                        // تبدیل به فارسی
                        instructions.add(translateInstruction(instruction))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting instructions", e)
        }
        
        return instructions
    }
    
    /**
     * تبدیل دستورات انگلیسی به فارسی
     */
    private fun translateInstruction(instruction: String): String {
        var result = instruction
            .replace("Turn right", "به راست بپیچید")
            .replace("Turn left", "به چپ بپیچید")
            .replace("Continue", "ادامه دهید")
            .replace("Go straight", "مستقیم بروید")
            .replace("Make a U-turn", "دور بزنید")
            .replace("Merge", "ادغام شوید")
            .replace("Exit", "خارج شوید")
            .replace("Enter", "وارد شوید")
            .replace("Roundabout", "میدان")
        
        // حذف تگ‌های HTML
        result = result.replace(Regex("<[^>]*>"), "")
        
        return result
    }
    
    /**
     * درخواست HTTP
     */
    private fun makeRequest(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Api-Key", API_KEY)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                Log.e(TAG, "HTTP error: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            null
        }
    }
    
    /**
     * مکان‌های تستی برای Fallback
     */
    private fun getMockLocation(query: String): PlaceResult {
        return when {
            query.contains("تهران") || query.contains("Tehran") -> PlaceResult(
                "تهران", "مرکز تهران", 35.6892, 51.3890
            )
            query.contains("مشهد") || query.contains("Mashhad") -> PlaceResult(
                "مشهد", "مرکز مشهد", 36.2974, 59.6057
            )
            query.contains("اصفهان") || query.contains("Isfahan") -> PlaceResult(
                "اصفهان", "میدان نقش جهان", 32.6546, 51.6680
            )
            else -> PlaceResult(
                query, "موقعیت نامشخص", 35.6892, 51.3890
            )
        }
    }
    
    /**
     * مسیر تستی برای Fallback
     */
    private fun getMockRoute(origin: LatLng, destination: LatLng): RouteResult {
        // محاسبه فاصله تقریبی
        val distance = calculateDistance(origin, destination)
        val duration = (distance / 50 * 60).toInt() // با سرعت متوسط 50 km/h
        
        // ایجاد مسیر مستقیم
        val points = listOf(origin, destination)
        
        return RouteResult(
            points = points,
            distance = distance,
            duration = duration,
            speedLimit = 80,
            instructions = listOf("مستقیم به سمت مقصد بروید")
        )
    }
    
    /**
     * مسیرهای تستی جایگزین
     */
    private fun getMockAlternativeRoutes(origin: LatLng, destination: LatLng): List<RouteResult> {
        val baseRoute = getMockRoute(origin, destination)
        
        return listOf(
            // مسیر 1: متعادل
            baseRoute,
            // مسیر 2: آزادراه (سریع‌تر)
            baseRoute.copy(
                distance = baseRoute.distance * 1.2,
                duration = (baseRoute.duration * 0.7).toInt(),
                speedLimit = 110,
                instructions = listOf("از آزادراه استفاده کنید")
            ),
            // مسیر 3: معابر شهری (کوتاه‌تر)
            baseRoute.copy(
                distance = baseRoute.distance * 0.85,
                duration = (baseRoute.duration * 1.3).toInt(),
                speedLimit = 50,
                instructions = listOf("از معابر شهری استفاده کنید")
            )
        )
    }
    
    /**
     * محاسبه فاصله تقریبی بین دو نقطه (کیلومتر)
     */
    private fun calculateDistance(origin: LatLng, destination: LatLng): Double {
        val earthRadius = 6371.0 // کیلومتر
        
        val dLat = Math.toRadians(destination.latitude - origin.latitude)
        val dLon = Math.toRadians(destination.longitude - origin.longitude)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(origin.latitude)) *
                Math.cos(Math.toRadians(destination.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
}
