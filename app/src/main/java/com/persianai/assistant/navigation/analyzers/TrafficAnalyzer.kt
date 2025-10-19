package com.persianai.assistant.navigation.analyzers

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.persianai.assistant.navigation.models.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.*

/**
 * تحلیلگر ترافیک - وضعیت ترافیک را بر اساس الگوهای زمانی و مکانی تحلیل می‌کند
 */
class TrafficAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "TrafficAnalyzer"
        private const val TRAFFIC_DATA_FILE = "traffic_data.json"
        private const val TRAFFIC_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 دقیقه
    }
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val trafficDataFile = File(context.filesDir, TRAFFIC_DATA_FILE)
    
    private var currentRoute: NavigationRoute? = null
    private var isAnalyzing = false
    private var trafficData: MutableMap<String, TrafficPattern> = mutableMapOf()
    
    init {
        loadTrafficData()
    }
    
    /**
     * شروع تحلیل ترافیک برای مسیر
     */
    fun startAnalyzing(route: NavigationRoute) {
        currentRoute = route
        isAnalyzing = true
        
        scope.launch {
            while (isAnalyzing) {
                analyzeTrafficForRoute(route)
                delay(TRAFFIC_UPDATE_INTERVAL)
            }
        }
        
        Log.d(TAG, "Started traffic analysis for route: ${route.id}")
    }
    
    /**
     * توقف تحلیل ترافیک
     */
    fun stopAnalyzing() {
        isAnalyzing = false
        currentRoute = null
        Log.d(TAG, "Stopped traffic analysis")
    }
    
    /**
     * بررسی موقعیت برای تحلیل ترافیک
     */
    fun checkLocation(location: GeoPoint) {
        scope.launch {
            try {
                checkTrafficAlert(location)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking location", e)
            }
        }
    }
    
    /**
     * بررسی هشدار ترافیک برای موقعیت فعلی
     */
    suspend fun checkTrafficAlert(location: GeoPoint): TrafficAlert? = withContext(Dispatchers.IO) {
        if (!isAnalyzing) return@withContext null
        
        try {
            val trafficLevel = analyzeTrafficAtLocation(location)
            val distanceToNextJam = findDistanceToNextTrafficJam(location)
            
            if (trafficLevel != TrafficLevel.LOW || distanceToNextJam < 1000) {
                val estimatedDelay = estimateDelay(trafficLevel, distanceToNextJam)
                
                return@withContext TrafficAlert(
                    trafficLevel = trafficLevel,
                    distance = distanceToNextJam.toInt(),
                    estimatedDelay = estimatedDelay,
                    message = generateTrafficMessage(trafficLevel, distanceToNextJam, estimatedDelay),
                    alertType = getTrafficAlertType(trafficLevel)
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking traffic alert", e)
        }
        
        return@withContext null
    }
    
    /**
     * تحلیل ترافیک در موقعیت مشخص
     */
    private fun analyzeTrafficAtLocation(location: GeoPoint): TrafficLevel {
        val areaKey = generateAreaKey(location)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        
        // دریافت الگوی ترافیک برای این منطقه
        val pattern = trafficData[areaKey]
        if (pattern != null) {
            return pattern.getTrafficLevel(currentHour, dayOfWeek)
        }
        
        // تحلیل بر اساس ساعت روز (پیش‌فرض)
        return getDefaultTrafficLevel(currentHour)
    }
    
    /**
     * تحلیل ترافیک برای کل مسیر
     */
    private suspend fun analyzeTrafficForRoute(route: NavigationRoute) {
        try {
            val trafficLevels = mutableListOf<TrafficLevel>()
            
            // تحلیل ترافیک در نقاط کلیدی مسیر
            val keyPoints = route.waypoints.takeLast(20) // 20 نقطه آخر
            
            for (point in keyPoints) {
                val level = analyzeTrafficAtLocation(point)
                trafficLevels.add(level)
            }
            
            // محاسبه میانگین ترافیک
            val averageLevel = calculateAverageTrafficLevel(trafficLevels)
            
            // به‌روزرسانی اطلاعات ترافیک مسیر
            val updatedTrafficInfo = route.trafficInfo?.copy(
                trafficLevel = averageLevel,
                estimatedDelay = estimateDelay(averageLevel, 0.0)
            ) ?: TrafficInfo(
                trafficLevel = averageLevel,
                estimatedDelay = estimateDelay(averageLevel, 0.0)
            )
            
            Log.d(TAG, "Traffic analysis completed for route: ${route.id}, Level: $averageLevel")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing traffic for route", e)
        }
    }
    
    /**
     * پیدا کردن فاصله تا گره ترافیکی بعدی
     */
    private fun findDistanceToNextTrafficJam(location: GeoPoint): Double {
        // جستجو در محدوده 5 کیلومتری
        val searchRadius = 5000.0
        var minDistance = Double.MAX_VALUE
        
        for ((areaKey, pattern) in trafficData) {
            val areaLocation = parseAreaKey(areaKey)
            val distance = location.distanceTo(areaLocation)
            
            if (distance <= searchRadius) {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                val trafficLevel = pattern.getTrafficLevel(currentHour, dayOfWeek)
                
                if (trafficLevel == TrafficLevel.HIGH || trafficLevel == TrafficLevel.SEVERE) {
                    minDistance = minOf(minDistance, distance)
                }
            }
        }
        
        return if (minDistance == Double.MAX_VALUE) 2000.0 else minDistance
    }
    
    /**
     * دریافت سطح ترافیک پیش‌فرض بر اساس ساعت
     */
    private fun getDefaultTrafficLevel(hour: Int): TrafficLevel {
        return when (hour) {
            in 6..9, in 16..19 -> TrafficLevel.HIGH     // ساعت‌های اوج ترافیک
            in 10..11, in 14..15, in 20..21 -> TrafficLevel.MEDIUM  // ترافیک متوسط
            else -> TrafficLevel.LOW      // ترافیک روان
        }
    }
    
    /**
     * تخمین تاخیر ترافیک
     */
    private fun estimateDelay(trafficLevel: TrafficLevel, distance: Double): Long {
        val baseDelay = when (trafficLevel) {
            TrafficLevel.LOW -> 0L
            TrafficLevel.MEDIUM -> 300L // 5 دقیقه
            TrafficLevel.HIGH -> 900L // 15 دقیقه
            TrafficLevel.SEVERE -> 1800L // 30 دقیقه
        }
        
        // اضافه کردن تاخیر بر اساس مسافت
        val distanceDelay = (distance / 1000.0 * 60).toLong() // 1 دقیقه به ازای هر کیلومتر
        
        return baseDelay + distanceDelay
    }
    
    /**
     * تولید پیام هشدار ترافیک
     */
    private fun generateTrafficMessage(
        trafficLevel: TrafficLevel,
        distance: Double,
        estimatedDelay: Long
    ): String {
        val trafficText = when (trafficLevel) {
            TrafficLevel.LOW -> "ترافیک روان"
            TrafficLevel.MEDIUM -> "ترافیک متوسط"
            TrafficLevel.HIGH -> "ترافیک سنگین"
            TrafficLevel.SEVERE -> "ترافیک بسیار سنگین"
        }
        
        val delayText = if (estimatedDelay > 0) {
            "تاخیر تخمینی: ${estimatedDelay / 60} دقیقه"
        } else {
            ""
        }
        
        return when {
            distance < 500 -> {
                "⚠️ $trafficText در当前位置! $delayText"
            }
            distance < 2000 -> {
                "🚗 $trafficText در ${distance.toInt()} متری. $delayText"
            }
            else -> {
                "🛣️ $trafficText در مسیر شما. $delayText"
            }
        }
    }
    
    /**
     * دریافت نوع هشدار ترافیک
     */
    private fun getTrafficAlertType(trafficLevel: TrafficLevel): AlertType {
        return when (trafficLevel) {
            TrafficLevel.SEVERE -> AlertType.CRITICAL
            TrafficLevel.HIGH -> AlertType.WARNING
            TrafficLevel.MEDIUM -> AlertType.INFO
            TrafficLevel.LOW -> AlertType.INFO
        }
    }
    
    private fun calculateAverageTrafficLevel(levels: List<TrafficLevel>): TrafficLevel {
        if (levels.isEmpty()) return TrafficLevel.LOW
        
        val average = levels.map { 
            when (it) {
                TrafficLevel.LOW -> 0
                TrafficLevel.MEDIUM -> 1
                TrafficLevel.HIGH -> 2
                TrafficLevel.SEVERE -> 3
            }
        }.average()
        
        return when {
            average < 0.5 -> TrafficLevel.LOW
            average < 1.5 -> TrafficLevel.MEDIUM
            average < 2.5 -> TrafficLevel.HIGH
            else -> TrafficLevel.SEVERE
        }
    }
    
    /**
     * تولید کلید منطقه
     */
    private fun generateAreaKey(location: GeoPoint): String {
        // گرد کردن مختصات برای ایجاد مناطق حدود 500x500 متر
        val lat = (location.latitude * 200).toInt() / 200.0
        val lon = (location.longitude * 200).toInt() / 200.0
        return "${lat}_${lon}"
    }
    
    /**
     * تجزیه کلید منطقه
     */
    private fun parseAreaKey(areaKey: String): GeoPoint {
        val parts = areaKey.split("_")
        return GeoPoint(parts[0].toDouble(), parts[1].toDouble())
    }
    
    /**
     * بارگذاری داده‌های ترافیک
     */
    private fun loadTrafficData() {
        try {
            if (trafficDataFile.exists()) {
                val json = trafficDataFile.readText()
                val type = object : com.google.gson.reflect.TypeToken<MutableMap<String, TrafficPattern>>() {}.type
                trafficData = gson.fromJson(json, type) ?: mutableMapOf()
            }
            
            // بارگذاری داده‌های پیش‌فرض
            loadDefaultTrafficData()
            
            Log.d(TAG, "Loaded traffic data for ${trafficData.size} areas")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading traffic data", e)
            trafficData = mutableMapOf()
            loadDefaultTrafficData()
        }
    }
    
    /**
     * بارگذاری داده‌های ترافیک پیش‌فرض
     */
    private fun loadDefaultTrafficData() {
        // اضافه کردن الگوهای ترافیک برای مناطق اصلی تهران
        val tehranCenter = generateAreaKey(GeoPoint(35.6892, 51.3890))
        trafficData[tehranCenter] = TrafficPattern(
            areaKey = tehranCenter,
            hourlyPatterns = mutableMapOf(
                0 to TrafficLevel.LOW, 1 to TrafficLevel.LOW, 2 to TrafficLevel.LOW,
                3 to TrafficLevel.LOW, 4 to TrafficLevel.LOW, 5 to TrafficLevel.LOW,
                6 to TrafficLevel.MEDIUM, 7 to TrafficLevel.HIGH, 8 to TrafficLevel.HIGH,
                9 to TrafficLevel.MEDIUM, 10 to TrafficLevel.MEDIUM, 11 to TrafficLevel.MEDIUM,
                12 to TrafficLevel.MEDIUM, 13 to TrafficLevel.MEDIUM, 14 to TrafficLevel.MEDIUM,
                15 to TrafficLevel.MEDIUM, 16 to TrafficLevel.HIGH, 17 to TrafficLevel.HIGH,
                18 to TrafficLevel.HIGH, 19 to TrafficLevel.MEDIUM, 20 to TrafficLevel.MEDIUM,
                21 to TrafficLevel.LOW, 22 to TrafficLevel.LOW, 23 to TrafficLevel.LOW
            )
        )
        
        saveTrafficData()
    }
    
    /**
     * ذخیره داده‌های ترافیک
     */
    private fun saveTrafficData() {
        try {
            val json = gson.toJson(trafficData)
            trafficDataFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving traffic data", e)
        }
    }
    
    /**
     * به‌روزرسانی داده‌های ترافیک بر اساس بازخورد کاربر
     */
    fun updateTrafficData(location: GeoPoint, trafficLevel: TrafficLevel) {
        val areaKey = generateAreaKey(location)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        val pattern = trafficData.getOrPut(areaKey) {
            TrafficPattern(areaKey, mutableMapOf())
        }
        
        pattern.updateHourlyPattern(currentHour, trafficLevel)
        saveTrafficData()
        
        Log.d(TAG, "Updated traffic data for area: $areaKey")
    }
    
    /**
     * دریافت پیش‌بینی ترافیک برای مسیر آینده
     */
    fun getTrafficPrediction(route: NavigationRoute, departureTime: Long): TrafficInfo {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = departureTime
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val trafficLevels = route.waypoints.takeLast(10).map { point ->
            val areaKey = generateAreaKey(point)
            val pattern = trafficData[areaKey]
            pattern?.getTrafficLevel(hour) ?: getDefaultTrafficLevel(hour)
        }
        
        val averageLevel = calculateAverageTrafficLevel(trafficLevels)
        val estimatedDelay = estimateDelay(averageLevel, 0.0)
        
        return TrafficInfo(
            trafficLevel = averageLevel,
            estimatedDelay = estimatedDelay
        )
    }
}

/**
 * الگوی ترافیک برای یک منطقه
 */
data class TrafficPattern(
    val areaKey: String,
    val hourlyPatterns: MutableMap<Int, TrafficLevel>
) {
    fun getTrafficLevel(hour: Int, dayOfWeek: Int = Calendar.MONDAY): TrafficLevel {
        // در این نسخه ساده، روز هفته را در نظر نمی‌گیریم
        return hourlyPatterns[hour] ?: TrafficLevel.LOW
    }
    
    fun updateHourlyPattern(hour: Int, trafficLevel: TrafficLevel) {
        hourlyPatterns[hour] = trafficLevel
    }
}
