package com.persianai.assistant.ml

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RouteLearningSys(private val context: Context) {
    
    private val routesFile = File(context.filesDir, "learned_routes.json")
    
    data class LearnedRoute(
        val id: String,
        val origin: LatLng,
        val destination: LatLng,
        val waypoints: List<LatLng>,
        val usageCount: Int,
        val avgDuration: Long,
        val avgSpeed: Float,
        val successRate: Float,
        val timestamp: Long
    )
    
    fun saveRoute(origin: LatLng, destination: LatLng, waypoints: List<LatLng>, duration: Long) {
        val routes = loadRoutes().toMutableList()
        
        val existing = routes.find { 
            isNearby(it.origin, origin) && isNearby(it.destination, destination)
        }
        
        if (existing != null) {
            routes.remove(existing)
            routes.add(existing.copy(
                usageCount = existing.usageCount + 1,
                avgDuration = (existing.avgDuration + duration) / 2,
                successRate = (existing.successRate + 1.0f) / 2
            ))
        } else {
            routes.add(LearnedRoute(
                id = System.currentTimeMillis().toString(),
                origin = origin,
                destination = destination,
                waypoints = waypoints,
                usageCount = 1,
                avgDuration = duration,
                avgSpeed = calculateSpeed(waypoints, duration),
                successRate = 1.0f,
                timestamp = System.currentTimeMillis()
            ))
        }
        
        saveRoutes(routes)
    }
    
    fun getSuggestedRoute(origin: LatLng, destination: LatLng): LearnedRoute? {
        return loadRoutes()
            .filter { isNearby(it.origin, origin) && isNearby(it.destination, destination) }
            .maxByOrNull { it.usageCount * it.successRate }
    }
    
    private fun isNearby(a: LatLng, b: LatLng): Boolean {
        val distance = FloatArray(1)
        android.location.Location.distanceBetween(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            distance
        )
        return distance[0] < 500 // 500 متر
    }
    
    private fun calculateSpeed(waypoints: List<LatLng>, duration: Long): Float {
        if (waypoints.size < 2 || duration == 0L) return 0f
        
        var totalDistance = 0f
        for (i in 1 until waypoints.size) {
            val distance = FloatArray(1)
            android.location.Location.distanceBetween(
                waypoints[i-1].latitude, waypoints[i-1].longitude,
                waypoints[i].latitude, waypoints[i].longitude,
                distance
            )
            totalDistance += distance[0]
        }
        
        return (totalDistance / duration) * 3.6f // km/h
    }
    
    private fun loadRoutes(): List<LearnedRoute> {
        if (!routesFile.exists()) return emptyList()
        
        return try {
            val json = JSONArray(routesFile.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                LearnedRoute(
                    id = obj.getString("id"),
                    origin = LatLng(obj.getDouble("originLat"), obj.getDouble("originLng")),
                    destination = LatLng(obj.getDouble("destLat"), obj.getDouble("destLng")),
                    waypoints = parseWaypoints(obj.getJSONArray("waypoints")),
                    usageCount = obj.getInt("usageCount"),
                    avgDuration = obj.getLong("avgDuration"),
                    avgSpeed = obj.getDouble("avgSpeed").toFloat(),
                    successRate = obj.getDouble("successRate").toFloat(),
                    timestamp = obj.getLong("timestamp")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveRoutes(routes: List<LearnedRoute>) {
        val json = JSONArray()
        routes.forEach { route ->
            json.put(JSONObject().apply {
                put("id", route.id)
                put("originLat", route.origin.latitude)
                put("originLng", route.origin.longitude)
                put("destLat", route.destination.latitude)
                put("destLng", route.destination.longitude)
                put("waypoints", waypointsToJson(route.waypoints))
                put("usageCount", route.usageCount)
                put("avgDuration", route.avgDuration)
                put("avgSpeed", route.avgSpeed)
                put("successRate", route.successRate)
                put("timestamp", route.timestamp)
            })
        }
        routesFile.writeText(json.toString())
    }
    
    private fun parseWaypoints(json: JSONArray): List<LatLng> {
        return (0 until json.length()).map { i ->
            val obj = json.getJSONObject(i)
            LatLng(obj.getDouble("lat"), obj.getDouble("lng"))
        }
    }
    
    private fun waypointsToJson(waypoints: List<LatLng>): JSONArray {
        val json = JSONArray()
        waypoints.forEach { point ->
            json.put(JSONObject().apply {
                put("lat", point.latitude)
                put("lng", point.longitude)
            })
        }
        return json
    }
}
