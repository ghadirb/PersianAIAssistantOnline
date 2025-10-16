package com.persianai.assistant.navigation

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * سیستم یادگیری و اصلاح خودکار نقشه
 * ذخیره مسیرهای کاربر و گزارش مشکلات برای بروزرسانی OSM
 */
class MapLearningSystem(private val context: Context) {
    
    private val learningFile = File(context.filesDir, "map_learning.json")
    private val routeHistory = mutableListOf<RouteData>()
    private val mapIssues = mutableListOf<MapIssue>()
    
    data class RouteData(
        val points: List<Location>,
        val timestamp: Long,
        val avgSpeed: Float,
        val distance: Float
    )
    
    data class MapIssue(
        val lat: Double,
        val lng: Double,
        val type: IssueType,
        val description: String,
        val timestamp: Long,
        val verified: Boolean = false
    )
    
    enum class IssueType {
        SPEED_CAMERA,      // دوربین سرعت
        ROAD_CLOSED,       // مسیر بسته
        NEW_ROAD,          // مسیر جدید
        WRONG_DIRECTION,   // جهت اشتباه
        POI_MISSING,       // POI گم شده
        POI_WRONG_LOCATION // مکان اشتباه POI
    }
    
    init {
        loadData()
    }
    
    /**
     * ذخیره مسیر کاربر
     */
    fun recordRoute(locations: List<Location>) {
        if (locations.size < 2) return
        
        val distance = calculateDistance(locations)
        val avgSpeed = locations.map { it.speed }.average().toFloat()
        
        val route = RouteData(
            points = locations,
            timestamp = System.currentTimeMillis(),
            avgSpeed = avgSpeed,
            distance = distance
        )
        
        routeHistory.add(route)
        saveData()
        
        // تحلیل خودکار برای یافتن مشکلات
        analyzeRoute(route)
    }
    
    /**
     * گزارش مشکل نقشه
     */
    fun reportIssue(lat: Double, lng: Double, type: IssueType, description: String) {
        val issue = MapIssue(
            lat = lat,
            lng = lng,
            type = type,
            description = description,
            timestamp = System.currentTimeMillis()
        )
        
        mapIssues.add(issue)
        saveData()
        
        // ارسال به سرور OSM (در آینده)
        // submitToOSM(issue)
    }
    
    /**
     * تحلیل خودکار مسیر
     */
    private fun analyzeRoute(route: RouteData) {
        // شناسایی توقف‌های ناگهانی (احتمال سرعتگیر)
        for (i in 1 until route.points.size) {
            val prev = route.points[i - 1]
            val curr = route.points[i]
            
            if (prev.speed > 40 && curr.speed < 10) {
                // توقف ناگهانی - احتمال سرعتگیر
                reportIssue(
                    curr.latitude,
                    curr.longitude,
                    IssueType.SPEED_CAMERA,
                    "توقف ناگهانی شناسایی شد"
                )
            }
        }
        
        // شناسایی مسیرهای جدید
        // TODO: مقایسه با نقشه موجود
    }
    
    /**
     * دریافت مشکلات تایید نشده
     */
    fun getPendingIssues(): List<MapIssue> {
        return mapIssues.filter { !it.verified }
    }
    
    /**
     * تایید مشکل
     */
    fun verifyIssue(issue: MapIssue) {
        val index = mapIssues.indexOf(issue)
        if (index >= 0) {
            mapIssues[index] = issue.copy(verified = true)
            saveData()
        }
    }
    
    /**
     * محاسبه فاصله
     */
    private fun calculateDistance(locations: List<Location>): Float {
        var distance = 0f
        for (i in 1 until locations.size) {
            distance += locations[i - 1].distanceTo(locations[i])
        }
        return distance
    }
    
    /**
     * ذخیره داده‌ها
     */
    private fun saveData() {
        try {
            val json = JSONObject()
            
            // ذخیره مسیرها (فقط 100 مسیر اخیر)
            val routesArray = JSONArray()
            routeHistory.takeLast(100).forEach { route ->
                val routeObj = JSONObject()
                routeObj.put("timestamp", route.timestamp)
                routeObj.put("avgSpeed", route.avgSpeed)
                routeObj.put("distance", route.distance)
                routesArray.put(routeObj)
            }
            json.put("routes", routesArray)
            
            // ذخیره مشکلات
            val issuesArray = JSONArray()
            mapIssues.forEach { issue ->
                val issueObj = JSONObject()
                issueObj.put("lat", issue.lat)
                issueObj.put("lng", issue.lng)
                issueObj.put("type", issue.type.name)
                issueObj.put("description", issue.description)
                issueObj.put("timestamp", issue.timestamp)
                issueObj.put("verified", issue.verified)
                issuesArray.put(issueObj)
            }
            json.put("issues", issuesArray)
            
            learningFile.writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("MapLearning", "Error saving data", e)
        }
    }
    
    /**
     * بارگذاری داده‌ها
     */
    private fun loadData() {
        try {
            if (!learningFile.exists()) return
            
            val json = JSONObject(learningFile.readText())
            
            // بارگذاری مشکلات
            val issuesArray = json.optJSONArray("issues")
            if (issuesArray != null) {
                for (i in 0 until issuesArray.length()) {
                    val issueObj = issuesArray.getJSONObject(i)
                    val issue = MapIssue(
                        lat = issueObj.getDouble("lat"),
                        lng = issueObj.getDouble("lng"),
                        type = IssueType.valueOf(issueObj.getString("type")),
                        description = issueObj.getString("description"),
                        timestamp = issueObj.getLong("timestamp"),
                        verified = issueObj.getBoolean("verified")
                    )
                    mapIssues.add(issue)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MapLearning", "Error loading data", e)
        }
    }
}
