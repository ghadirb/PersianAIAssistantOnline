package com.persianai.assistant.navigation.analyzers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.persianai.assistant.navigation.models.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.*

/**
 * تحلیلگر وضعیت جاده - شرایط جاده را تحلیل کرده و هشدارهای مربوطه را صادر می‌کند
 */
class RoadConditionAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "RoadConditionAnalyzer"
        private const val ROAD_CONDITIONS_FILE = "road_conditions.json"
        private const val ANALYSIS_INTERVAL = 2 * 60 * 1000L // 2 دقیقه
        private const val WARNING_DISTANCE = 500 // متر
    }
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val conditionsFile = File(context.filesDir, ROAD_CONDITIONS_FILE)
    
    private var currentRoute: NavigationRoute? = null
    private var isAnalyzing = false
    private var roadConditions: MutableList<RoadCondition> = mutableListOf()
    
    init {
        loadRoadConditions()
        loadDefaultConditions()
    }
    
    /**
     * شروع تحلیل وضعیت جاده برای مسیر
     */
    fun startAnalyzing(route: NavigationRoute) {
        currentRoute = route
        isAnalyzing = true
        
        // فیلتر کردن شرایط نزدیک به مسیر
        filterConditionsForRoute(route)
        
        scope.launch {
            while (isAnalyzing) {
                analyzeRoadConditions(route)
                delay(ANALYSIS_INTERVAL)
            }
        }
        
        Log.d(TAG, "Started road condition analysis for route: ${route.id}")
    }
    
    /**
     * توقف تحلیل وضعیت جاده
     */
    fun stopAnalyzing() {
        isAnalyzing = false
        currentRoute = null
        Log.d(TAG, "Stopped road condition analysis")
    }
    
    /**
     * بررسی موقعیت برای تحلیل وضعیت جاده
     */
    fun checkLocation(location: GeoPoint) {
        scope.launch {
            try {
                checkRoadCondition(location)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking location", e)
            }
        }
    }
    
    /**
     * بررسی هشدار وضعیت جاده برای موقعیت فعلی
     */
    suspend fun checkRoadCondition(location: GeoPoint): RoadConditionAlert? = withContext(Dispatchers.IO) {
        if (!isAnalyzing) return@withContext null
        
        try {
            val nearbyCondition = findNearbyCondition(location)
            if (nearbyCondition != null) {
                val distance = location.distanceToAsDouble(nearbyCondition.location)
                
                return@withContext RoadConditionAlert(
                    condition = nearbyCondition.condition,
                    severity = nearbyCondition.severity,
                    distance = distance.toInt(),
                    message = generateRoadConditionMessage(nearbyCondition, distance),
                    alertType = getRoadConditionAlertType(nearbyCondition.severity)
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking road condition", e)
        }
        
        return@withContext null
    }
    
    /**
     * پیدا کردن شرایط جاده نزدیک
     */
    private fun findNearbyCondition(location: GeoPoint): RoadCondition? {
        return roadConditions.firstOrNull { condition ->
            val distance = location.distanceToAsDouble(condition.location)
            distance <= WARNING_DISTANCE
        }
    }
    
    /**
     * تحلیل شرایط جاده برای مسیر
     */
    private suspend fun analyzeRoadConditions(route: NavigationRoute) {
        try {
            // تحلیل بر اساس نقاط کلیدی مسیر
            val keyPoints = route.waypoints.takeLast(15)
            
            for (point in keyPoints) {
                val condition = analyzeRoadConditionAtPoint(point)
                if (condition != null) {
                    // اضافه کردن شرایط جدید (اگر وجود ندارد)
                    if (!roadConditions.any { 
                        it.location.distanceToAsDouble(condition.location) < 50 
                    }) {
                        roadConditions.add(condition)
                        saveRoadConditions()
                    }
                }
            }
            
            Log.d(TAG, "Road condition analysis completed for route: ${route.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing road conditions", e)
        }
    }
    
    /**
     * تحلیل وضعیت جاده در یک نقطه خاص
     */
    private fun analyzeRoadConditionAtPoint(location: GeoPoint): RoadCondition? {
        // این یک تحلیل ساده است - در واقعیت باید از داده‌های مختلف استفاده شود
        
        // شبیه‌سازی تشخیص شرایط مختلف بر اساس موقعیت
        val random = Random(location.hashCode().toLong())
        
        return when (random.nextInt(20)) {
            0 -> RoadCondition(
                location = location,
                condition = ConditionType.CONSTRUCTION,
                severity = SeverityLevel.MEDIUM,
                length = 100.0,
                description = "عملیات ساخت‌وساز در جاده"
            )
            1 -> RoadCondition(
                location = location,
                condition = ConditionType.POTHOLE,
                severity = SeverityLevel.LOW,
                length = 20.0,
                description = "وجود دست‌انداز در جاده"
            )
            2 -> RoadCondition(
                location = location,
                condition = ConditionType.NARROW_ROAD,
                severity = SeverityLevel.MEDIUM,
                length = 200.0,
                description = "جاده باریک در این منطقه"
            )
            3 -> RoadCondition(
                location = location,
                condition = ConditionType.FLOODING,
                severity = SeverityLevel.HIGH,
                length = 50.0,
                description = "آبگرفتگی در جاده"
            )
            else -> null
        }
    }
    
    /**
     * تولید پیام هشدار وضعیت جاده
     */
    private fun generateRoadConditionMessage(condition: RoadCondition, distance: Double): String {
        val conditionText = when (condition.condition) {
            ConditionType.CONSTRUCTION -> "عملیات ساخت‌وساز"
            ConditionType.POTHOLE -> "دست‌انداز"
            ConditionType.FLOODING -> "آبگرفتگی"
            ConditionType.ICE -> "یخ‌زدگی"
            ConditionType.DEBRIS -> "مانع در جاده"
            ConditionType.NARROW_ROAD -> "جاده باریک"
            ConditionType.BRIDGE_WORK -> "کار پل"
            ConditionType.LANDSLIDE -> "رانش زمین"
        }
        
        val severityText = when (condition.severity) {
            SeverityLevel.LOW -> "خفیف"
            SeverityLevel.MEDIUM -> "متوسط"
            SeverityLevel.HIGH -> "شدید"
            SeverityLevel.CRITICAL -> "بحرانی"
        }
        
        return when {
            distance < 100 -> {
                "⚠️ $conditionText $severityText در当前位置! ${condition.description}"
            }
            distance < 300 -> {
                "🚧 $conditionText $severityText در ${distance.toInt()} متری. ${condition.description}"
            }
            else -> {
                "🛣️ $conditionText در مسیر شما (${distance.toInt()} متری). ${condition.description}"
            }
        }
    }
    
    /**
     * دریافت نوع هشدار وضعیت جاده
     */
    private fun getRoadConditionAlertType(severity: SeverityLevel): AlertType {
        return when (severity) {
            SeverityLevel.CRITICAL -> AlertType.CRITICAL
            SeverityLevel.HIGH -> AlertType.WARNING
            SeverityLevel.MEDIUM -> AlertType.INFO
            SeverityLevel.LOW -> AlertType.INFO
        }
    }
    
    /**
     * فیلتر کردن شرایط برای مسیر فعلی
     */
    private fun filterConditionsForRoute(route: NavigationRoute) {
        val boundingBox = calculateBoundingBox(route.waypoints)
        
        roadConditions = roadConditions.filter { condition ->
            isPointInBoundingBox(condition.location, boundingBox)
        }.toMutableList()
        
        Log.d(TAG, "Filtered to ${roadConditions.size} road conditions for route")
    }
    
    /**
     * محاسبه کادر محاطی مسیر
     */
    private fun calculateBoundingBox(waypoints: List<GeoPoint>): BoundingBox {
        if (waypoints.isEmpty()) {
            return BoundingBox(0.0, 0.0, 0.0, 0.0)
        }
        
        var minLat = waypoints.first().latitude
        var maxLat = waypoints.first().latitude
        var minLon = waypoints.first().longitude
        var maxLon = waypoints.first().longitude
        
        for (point in waypoints) {
            minLat = minOf(minLat, point.latitude)
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
        }
        
        // اضافه کردن حاشیه
        val margin = 0.02 // حدود 2 کیلومتر
        return BoundingBox(
            minLat - margin,
            minLon - margin,
            maxLat + margin,
            maxLon + margin
        )
    }
    
    /**
     * بررسی اینکه نقطه در کادر محاطی است یا نه
     */
    private fun isPointInBoundingBox(point: GeoPoint, box: BoundingBox): Boolean {
        return point.latitude >= box.minLat && point.latitude <= box.maxLat &&
               point.longitude >= box.minLon && point.longitude <= box.maxLon
    }
    
    /**
     * بارگذاری شرایط جاده از فایل
     */
    private fun loadRoadConditions() {
        try {
            if (conditionsFile.exists()) {
                val json = conditionsFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<MutableList<RoadCondition>>() {}.type
                roadConditions = gson.fromJson(json, type) ?: mutableListOf()
            }
            
            Log.d(TAG, "Loaded ${roadConditions.size} road conditions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading road conditions", e)
            roadConditions = mutableListOf()
        }
    }
    
    /**
     * بارگذاری شرایط پیش‌فرض
     */
    private fun loadDefaultConditions() {
        if (roadConditions.isEmpty()) {
            // اضافه کردن شرایط نمونه در تهران
            roadConditions.addAll(listOf(
                RoadCondition(
                    location = GeoPoint(35.6961, 51.4231),
                    condition = ConditionType.CONSTRUCTION,
                    severity = SeverityLevel.MEDIUM,
                    length = 150.0,
                    description = "عملیات زیرسازی خیابان"
                ),
                RoadCondition(
                    location = GeoPoint(35.6892, 51.3890),
                    condition = ConditionType.POTHOLE,
                    severity = SeverityLevel.LOW,
                    length = 30.0,
                    description = "تعمیر دست‌انداز"
                ),
                RoadCondition(
                    location = GeoPoint(35.7158, 51.4065),
                    condition = ConditionType.NARROW_ROAD,
                    severity = SeverityLevel.MEDIUM,
                    length = 200.0,
                    description = "محدودیت عرض جاده"
                ),
                RoadCondition(
                    location = GeoPoint(35.7021, 51.4115),
                    condition = ConditionType.FLOODING,
                    severity = SeverityLevel.HIGH,
                    length = 80.0,
                    description = "آبگرفتگی پس از بارندگی"
                )
            ))
            
            saveRoadConditions()
        }
    }
    
    /**
     * ذخیره شرایط جاده در فایل
     */
    private fun saveRoadConditions() {
        try {
            val json = gson.toJson(roadConditions)
            conditionsFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving road conditions", e)
        }
    }
    
    /**
     * اضافه کردن شرایط جاده جدید
     */
    fun addRoadCondition(condition: RoadCondition) {
        roadConditions.add(condition)
        saveRoadConditions()
        Log.d(TAG, "Added new road condition: ${condition.condition}")
    }
    
    /**
     * دریافت تمام شرایط جاده
     */
    fun getAllRoadConditions(): List<RoadCondition> {
        return roadConditions.toList()
    }
    
    /**
     * دریافت شرایط جاده برای یک منطقه خاص
     */
    fun getRoadConditionsInArea(center: GeoPoint, radius: Double): List<RoadCondition> {
        return roadConditions.filter { condition ->
            center.distanceToAsDouble(condition.location) <= radius
        }
    }
    
    /**
     * حذف شرایط جاده
     */
    fun removeRoadCondition(conditionId: String) {
        roadConditions.removeAll { it.location.toString().contains(conditionId) }
        saveRoadConditions()
    }
    
    /**
     * به‌روزرسانی شرایط جاده بر اساس بازخورد کاربر
     */
    fun updateRoadCondition(
        location: GeoPoint,
        condition: ConditionType,
        severity: SeverityLevel,
        description: String
    ) {
        // پیدا کردن شرایط مشابه و به‌روزرسانی آن
        val existingCondition = roadConditions.find { 
            it.location.distanceToAsDouble(location) < 50 
        }
        
        if (existingCondition != null) {
            val index = roadConditions.indexOf(existingCondition)
            roadConditions[index] = existingCondition.copy(
                condition = condition,
                severity = severity,
                description = description
            )
        } else {
            roadConditions.add(RoadCondition(
                location = location,
                condition = condition,
                severity = severity,
                length = 50.0,
                description = description
            ))
        }
        
        saveRoadConditions()
        Log.d(TAG, "Updated road condition at: $location")
    }
    
    /**
     * دریافت پیش‌بینی شرایط جاده برای مسیر
     */
    fun getRoadConditionPrediction(route: NavigationRoute): List<RoadCondition> {
        return roadConditions.filter { condition ->
            route.waypoints.any { waypoint ->
                waypoint.distanceToAsDouble(condition.location) <= 100 // 100 متر از مسیر
            }
        }.sortedBy { condition ->
            // مرتب‌سازی بر اساس فاصله از شروع مسیر
            route.waypoints.minOf { it.distanceToAsDouble(condition.location) }
        }
    }
    
    /**
     * پاک کردن تمام داده‌ها
     */
    fun clearAllData() {
        roadConditions.clear()
        saveRoadConditions()
        Log.d(TAG, "All road condition data cleared")
    }
}

/**
 * کادر محاطی
 */
data class BoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
)
