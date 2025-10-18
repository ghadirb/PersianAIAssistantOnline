package com.persianai.assistant.ml

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.persianai.assistant.utils.NeshanAPIManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

class RouteOptimizer(private val context: Context) {
    
    private val neshanAPI = NeshanAPIManager(context)
    
    data class Route(
        val id: Int,
        val name: String,
        val distance: Double,
        val duration: Int,
        val trafficLevel: String,
        val speedCameras: Int,
        val points: List<LatLng>,
        val score: Float
    )
    
    suspend fun suggestRoutes(start: LatLng, end: LatLng): List<Route> = coroutineScope {
        val routes = mutableListOf<Route>()
        
        // مسیر 1: کوتاه‌ترین (خط مستقیم)
        val route1 = async {
            calculateDirectRoute(start, end, 1, "مسیر سریع")
        }
        
        // مسیر 2: با کمترین ترافیک
        val route2 = async {
            calculateLowTrafficRoute(start, end, 2, "کم‌ترافیک")
        }
        
        // مسیر 3: با کمترین دوربین
        val route3 = async {
            calculateLowCameraRoute(start, end, 3, "کم‌دوربین")
        }
        
        routes.add(route1.await())
        routes.add(route2.await())
        routes.add(route3.await())
        
        routes.sortedByDescending { it.score }
    }
    
    private suspend fun calculateDirectRoute(start: LatLng, end: LatLng, id: Int, name: String): Route {
        val distance = calculateDistance(start, end)
        val duration = (distance / 50 * 60).toInt() // فرض: 50 km/h
        
        val midLat = (start.latitude + end.latitude) / 2
        val midLng = (start.longitude + end.longitude) / 2
        
        val traffic = neshanAPI.getTrafficData(midLat, midLng)
        val cameras = neshanAPI.getSpeedCameras(midLat, midLng, 5000)
        
        val score = calculateScore(distance, duration, traffic?.level ?: "normal", cameras.size)
        
        return Route(
            id = id,
            name = name,
            distance = distance,
            duration = duration,
            trafficLevel = traffic?.level ?: "normal",
            speedCameras = cameras.size,
            points = listOf(start, end),
            score = score
        )
    }
    
    private suspend fun calculateLowTrafficRoute(start: LatLng, end: LatLng, id: Int, name: String): Route {
        val distance = calculateDistance(start, end) * 1.15 // 15% طولانی‌تر
        val duration = (distance / 60 * 60).toInt() // سرعت بیشتر
        
        return Route(
            id = id,
            name = name,
            distance = distance,
            duration = duration,
            trafficLevel = "light",
            speedCameras = 2,
            points = listOf(start, end),
            score = calculateScore(distance, duration, "light", 2)
        )
    }
    
    private suspend fun calculateLowCameraRoute(start: LatLng, end: LatLng, id: Int, name: String): Route {
        val distance = calculateDistance(start, end) * 1.1
        val duration = (distance / 55 * 60).toInt()
        
        return Route(
            id = id,
            name = name,
            distance = distance,
            duration = duration,
            trafficLevel = "moderate",
            speedCameras = 0,
            points = listOf(start, end),
            score = calculateScore(distance, duration, "moderate", 0)
        )
    }
    
    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val R = 6371.0 // شعاع زمین به کیلومتر
        val lat1 = start.latitude * PI / 180
        val lat2 = end.latitude * PI / 180
        val dLat = (end.latitude - start.latitude) * PI / 180
        val dLng = (end.longitude - start.longitude) * PI / 180
        
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }
    
    private fun calculateScore(distance: Double, duration: Int, traffic: String, cameras: Int): Float {
        var score = 100f
        
        // کاهش امتیاز بر اساس مسافت
        score -= (distance / 10).toFloat()
        
        // کاهش امتیاز بر اساس زمان
        score -= (duration / 10).toFloat()
        
        // کاهش امتیاز بر اساس ترافیک
        score -= when (traffic) {
            "heavy" -> 30f
            "moderate" -> 15f
            "light" -> 5f
            else -> 10f
        }
        
        // کاهش امتیاز بر اساس دوربین‌ها
        score -= cameras * 5f
        
        return score.coerceAtLeast(0f)
    }
}
