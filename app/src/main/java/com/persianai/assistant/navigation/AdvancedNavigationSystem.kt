package com.persianai.assistant.navigation

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import com.persianai.assistant.navigation.detectors.SpeedCameraDetector
import com.persianai.assistant.navigation.analyzers.TrafficAnalyzer
import com.persianai.assistant.navigation.analyzers.RoadConditionAnalyzer
import com.persianai.assistant.navigation.ai.AIRoutePredictor
import com.persianai.assistant.navigation.learning.RouteLearningSystem
import com.persianai.assistant.navigation.models.*
import com.persianai.assistant.navigation.models.NavigationRoute
import com.persianai.assistant.navigation.models.RouteType
import com.persianai.assistant.navigation.utils.RouteCache
import com.persianai.assistant.navigation.sync.GoogleDriveSync
import java.io.File
import java.util.*
import kotlin.math.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * سیستم مسیریاب پیشرفته با هوش مصنوعی و یادگیری خودکار
 * شبیه به نشان با قابلیت‌های هشدار سرعت‌گیر، دوربین، ترافیک و...
 */
class AdvancedNavigationSystem(private val context: Context) {
    
    companion object {
        private const val TAG = "AdvancedNavigationSystem"
        private const val PREFS_NAME = "navigation_prefs"
        private const val KEY_NESHAN_API = "neshan_api_key"
        private const val KEY_NAVIGATION_ENABLED = "navigation_enabled"
        private const val KEY_SPEED_ALERTS_ENABLED = "speed_alerts_enabled"
        private const val KEY_CAMERA_ALERTS_ENABLED = "camera_alerts_enabled"
        private const val KEY_TRAFFIC_ALERTS_ENABLED = "traffic_alerts_enabled"
        private const val KEY_ROAD_CONDITION_ALERTS = "road_condition_alerts"
        
        // محدودیت برای کلید نشان (1000 درخواست در روز)
        private const val DAILY_API_LIMIT = 1000
        private const val KEY_API_USAGE_COUNT = "api_usage_count"
        private const val KEY_API_USAGE_DATE = "api_usage_date"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient()
    
    // مدیریت کش و یادگیری
    private val routeCache = RouteCache(context)
    private val routeLearningSystem = RouteLearningSystem(context)
    private val speedCameraDetector = SpeedCameraDetector(context)
    private val trafficAnalyzer = TrafficAnalyzer(context)
    private val roadConditionAnalyzer = RoadConditionAnalyzer(context)
    private val aiRoutePredictor = AIRoutePredictor(context)
    
    // وضعیت سرویس‌ها (public برای دسترسی از خارج)
    var isNavigationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NAVIGATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NAVIGATION_ENABLED, value).apply()
    
    var areSpeedAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEED_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEED_ALERTS_ENABLED, value).apply()
    
    var areCameraAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_ALERTS_ENABLED, value).apply()
    
    var areTrafficAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRAFFIC_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TRAFFIC_ALERTS_ENABLED, value).apply()
    
    var areRoadConditionAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROAD_CONDITION_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ROAD_CONDITION_ALERTS, value).apply()
    
    /**
     * تنظیم کلید API نشان
     */
    fun setNeshanApiKey(apiKey: String) {
        prefs.edit().putString(KEY_NESHAN_API, apiKey).apply()
        Log.d(TAG, "Neshan API key configured")
    }
    
    /**
     * دریافت کلید API نشان
     */
    fun getNeshanApiKey(): String? {
        return prefs.getString(KEY_NESHAN_API, null)
    }
    
    /**
     * بررسی محدودیت مصرف API
     */
    private fun canUseApi(): Boolean {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastUsageDate = prefs.getInt(KEY_API_USAGE_DATE, -1)
        val usageCount = prefs.getInt(KEY_API_USAGE_COUNT, 0)
        
        // اگر روز جدید است، شمارنده را ریست کن
        if (today != lastUsageDate) {
            prefs.edit()
                .putInt(KEY_API_USAGE_DATE, today)
                .putInt(KEY_API_USAGE_COUNT, 0)
                .apply()
            return true
        }
        
        return usageCount < DAILY_API_LIMIT
    }
    
    /**
     * افزایش شمارنده مصرف API
     */
    private fun incrementApiUsage() {
        val count = prefs.getInt(KEY_API_USAGE_COUNT, 0)
        prefs.edit().putInt(KEY_API_USAGE_COUNT, count + 1).apply()
    }

    /**
     * تلاش برای بارگذاری کلید نشان از فایل لایسنس و ذخیره در prefs
     */
    private fun ensureNeshanApiKeyLoaded(): String? {
        val existing = getNeshanApiKey()
        if (!existing.isNullOrBlank()) return existing
        val fromFile = loadNeshanKeyFromLicenseFile()
        if (!fromFile.isNullOrBlank()) {
            setNeshanApiKey(fromFile)
            return fromFile
        }
        return null
    }

    private fun loadNeshanKeyFromLicenseFile(): String? {
        return try {
            val file = File("C:\\\\Users\\\\Admin\\\\Downloads\\\\Telegram Desktop\\\\neshan.license")
            if (file.exists()) {
                file.readText().trim().ifBlank { null }
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read neshan.license", e)
            null
        }
    }
    
    /**
     * دریافت مسیر با هوش مصنوعی
     */
    suspend fun getRouteWithAI(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType = RouteType.DRIVING
    ): NavigationRoute? = withContext(Dispatchers.IO) {
        
        // اول از کش بررسی کن
        val cachedRoute = routeCache.getRoute(origin, destination, routeType)
        if (cachedRoute != null) {
            Log.d(TAG, "Route found in cache")
            return@withContext cachedRoute
        }
        
        // اگر API در دسترس است و آنلاین هستیم
        val apiKey = ensureNeshanApiKeyLoaded()
        if (canUseApi() && apiKey != null) {
            try {
                val apiRoute = getRouteFromNeshanAPI(origin, destination, routeType, apiKey)
                if (apiRoute != null) {
                    incrementApiUsage()
                    routeCache.saveRoute(apiRoute)
                    return@withContext apiRoute
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting route from API", e)
            }
        }
        
        // استفاده از هوش مصنوعی برای پیشنهاد مسیر
        val aiRoute = aiRoutePredictor.predictRoute(origin, destination, routeType)
        if (aiRoute != null) {
            routeCache.saveRoute(aiRoute)
            return@withContext aiRoute
        }
        
        // مسیر پایه از OSM
        val osmRoute = getOSMRoute(origin, destination)
        if (osmRoute != null) {
            routeCache.saveRoute(osmRoute)
            return@withContext osmRoute
        }
        
        return@withContext null
    }
    
    /**
     * دریافت مسیر از API نشان
     */
    private fun getRouteFromNeshanAPI(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType,
        apiKey: String
    ): NavigationRoute? {
        return try {
            val typeParam = when (routeType) {
                RouteType.WALKING -> "foot"
                RouteType.CYCLING -> "bicycle"
                else -> "car"
            }
            val url =
                "https://api.neshan.org/v4/direction?type=$typeParam&origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}"
            val request = Request.Builder()
                .url(url)
                .addHeader("Api-Key", apiKey)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Neshan API not successful: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                parseNeshanRoute(body, origin, destination, routeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Neshan API error", e)
            null
        }
    }
    
    /**
     * دریافت مسیر از OSM (آفلاین)
     */
    private suspend fun getOSMRoute(
        origin: GeoPoint,
        destination: GeoPoint
    ): NavigationRoute? {
        // مسیر خط مستقیم ساده به عنوان fallback آفلاین/OSM
        val waypoints = listOf(origin, destination)
        val distance = haversineMeters(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
        val duration = (distance / (40_000.0 / 3600.0)).roundToLong() // فرض سرعت ۴۰km/h
        val step = NavigationStep(
            instruction = "مسیر مستقیم تا مقصد",
            distance = distance,
            duration = duration,
            startLocation = com.persianai.assistant.navigation.models.GeoPoint(origin.latitude, origin.longitude),
            endLocation = com.persianai.assistant.navigation.models.GeoPoint(destination.latitude, destination.longitude),
            maneuver = "straight",
            polyline = "${origin.latitude},${origin.longitude};${destination.latitude},${destination.longitude}"
        )
        return NavigationRoute(
            id = UUID.randomUUID().toString(),
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            distance = distance,
            duration = duration,
            routeType = RouteType.DRIVING,
            steps = listOf(step)
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun parseNeshanRoute(
        jsonBody: String,
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        val json = JSONObject(jsonBody)
        val routesArr = json.optJSONArray("routes") ?: return null
        val routeObj = routesArr.optJSONObject(0) ?: return null
        val legs = routeObj.optJSONArray("legs") ?: return null

        val waypoints = mutableListOf<GeoPoint>()
        val steps = mutableListOf<NavigationStep>()
        var totalDistance = 0.0
        var totalDuration = 0.0

        for (i in 0 until legs.length()) {
            val leg = legs.optJSONObject(i) ?: continue
            val stepsArr = leg.optJSONArray("steps") ?: continue
            for (j in 0 until stepsArr.length()) {
                val stepObj = stepsArr.optJSONObject(j) ?: continue
                val dist = stepObj.optJSONObject("distance")?.optDouble("value") ?: 0.0
                val dur = stepObj.optJSONObject("duration")?.optDouble("value") ?: 0.0
                val startLoc = stepObj.optJSONObject("start_location")
                val endLoc = stepObj.optJSONObject("end_location")
                val startLat = startLoc?.optDouble("lat") ?: origin.latitude
                val startLng = startLoc?.optDouble("lng") ?: origin.longitude
                val endLat = endLoc?.optDouble("lat") ?: destination.latitude
                val endLng = endLoc?.optDouble("lng") ?: destination.longitude
                val startPoint = GeoPoint(startLat, startLng)
                val endPoint = GeoPoint(endLat, endLng)

                if (waypoints.isEmpty()) waypoints.add(startPoint)
                if (waypoints.lastOrNull() != endPoint) waypoints.add(endPoint)

                val rawInstruction = stepObj.optString("html_instructions",
                    stepObj.optString("instruction",
                        stepObj.optString("maneuver", "ادامه مسیر")))
                val cleanInstruction = rawInstruction.replace(Regex("<.*?>"), "").trim()
                val maneuver = stepObj.optString("maneuver", "straight")
                val polyline = stepObj.optString("polyline", "")

                steps.add(
                    NavigationStep(
                        instruction = cleanInstruction.ifBlank { "ادامه مسیر" },
                        distance = dist,
                        duration = dur,
                        startLocation = com.persianai.assistant.navigation.models.GeoPoint(startLat, startLng),
                        endLocation = com.persianai.assistant.navigation.models.GeoPoint(endLat, endLng),
                        maneuver = maneuver,
                        polyline = polyline
                    )
                )
                totalDistance += dist
                totalDuration += dur
            }
        }

        if (waypoints.isEmpty()) {
            waypoints.add(origin)
            waypoints.add(destination)
        }

        return NavigationRoute(
            id = UUID.randomUUID().toString(),
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            distance = if (totalDistance > 0) totalDistance else haversineMeters(origin.latitude, origin.longitude, destination.latitude, destination.longitude),
            duration = if (totalDuration > 0) totalDuration else (totalDistance / (40_000.0 / 3600.0)),
            routeType = routeType,
            steps = steps
        )
    }
    
    /**
     * شروع ناوبری و یادگیری مسیر
     */
    fun startNavigation(route: NavigationRoute) {
        if (!isNavigationEnabled) return
        
        scope.launch {
            // شروع یادگیری مسیر
            routeLearningSystem.startLearningRoute(route)
            
            // شروع تحلیل ترافیک
            if (areTrafficAlertsEnabled) {
                trafficAnalyzer.startAnalyzing(route)
            }
            
            // شروع تشخیص سرعت‌گیر و دوربین
            if (areSpeedAlertsEnabled || areCameraAlertsEnabled) {
                speedCameraDetector.startDetection(route)
            }
            
            // شروع تحلیل وضعیت جاده
            if (areRoadConditionAlertsEnabled) {
                roadConditionAnalyzer.startAnalyzing(route)
            }
        }
    }
    
    /**
     * به‌روزرسانی موقعیت کاربر و دریافت هشدارها
     */
    fun updateLocation(location: Location) {
        if (!isNavigationEnabled) return
        
        scope.launch {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            
            // یادگیری مسیر
            routeLearningSystem.updateLocation(geoPoint)
            
            // بررسی هشدارها
            if (areSpeedAlertsEnabled) {
                val speedAlert = speedCameraDetector.checkSpeedAlert(location)
                speedAlert?.let { notifySpeedAlert(it) }
            }
            
            if (areCameraAlertsEnabled) {
                val cameraAlert = speedCameraDetector.checkCameraAlert(location)
                cameraAlert?.let { notifyCameraAlert(it) }
            }
            
            if (areTrafficAlertsEnabled) {
                val trafficAlert = trafficAnalyzer.checkTrafficAlert(geoPoint)
                trafficAlert?.let { notifyTrafficAlert(it) }
            }
            
            if (areRoadConditionAlertsEnabled) {
                val roadAlert = roadConditionAnalyzer.checkRoadCondition(geoPoint)
                roadAlert?.let { notifyRoadConditionAlert(it) }
            }
        }
    }
    
    /**
     * پایان ناوبری و ذخیره مسیر یادگرفته شده
     */
    fun stopNavigation() {
        scope.launch {
            val learnedRoute = routeLearningSystem.finishLearning()
            learnedRoute?.let { route ->
                // ذخیره در Google Drive برای اشتراک‌گذاری
                GoogleDriveSync(context).uploadRoute(route)
                
                // به‌روزرسانی مدل هوش مصنوعی
                aiRoutePredictor.updateModel(route)
            }
            
            // توقف تمام تحلیل‌گرها
            trafficAnalyzer.stopAnalyzing()
            speedCameraDetector.stopDetection()
            roadConditionAnalyzer.stopAnalyzing()
        }
    }
    
    // توابع هشدار
    private fun notifySpeedAlert(alert: SpeedAlert) {
        // ارسال هشدار سرعت
        Log.d(TAG, "Speed alert: ${alert.message}")
    }
    
    private fun notifyCameraAlert(alert: CameraAlert) {
        // ارسال هشدار دوربین
        Log.d(TAG, "Camera alert: ${alert.message}")
    }
    
    private fun notifyTrafficAlert(alert: TrafficAlert) {
        // ارسال هشدار ترافیک
        Log.d(TAG, "Traffic alert: ${alert.message}")
    }
    
    private fun notifyRoadConditionAlert(alert: RoadConditionAlert) {
        // ارسال هشدار وضعیت جاده
        Log.d(TAG, "Road condition alert: ${alert.message}")
    }
    
    /**
     * تنظیمات مسیریاب
     * توجه: برای تنظیم مستقیماً از property ها استفاده کنید:
     * - isNavigationEnabled
     * - areSpeedAlertsEnabled
     * - areCameraAlertsEnabled
     * - areTrafficAlertsEnabled
     * - areRoadConditionAlertsEnabled
     */
    
    /**
     * دریافت وضعیت مصرف API
     */
    fun getApiUsageStatus(): ApiUsageStatus {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastUsageDate = prefs.getInt(KEY_API_USAGE_DATE, -1)
        val usageCount = prefs.getInt(KEY_API_USAGE_COUNT, 0)
        
        return if (today == lastUsageDate) {
            ApiUsageStatus(usageCount, DAILY_API_LIMIT, true)
        } else {
            ApiUsageStatus(0, DAILY_API_LIMIT, true)
        }
    }
}

/**
 * انواع مسیر
 */
enum class RouteType {
    FASTEST,    // سریع‌ترین
    SHORTEST,   // کوتاه‌ترین
    AVOID_TRAFFIC, // دور زدن ترافیک
    SCENIC      // جاده‌های زیبا
}

/**
 * وضعیت مصرف API
 */
data class ApiUsageStatus(
    val usedCount: Int,
    val maxCount: Int,
    val canUseApi: Boolean
) {
    val remainingCount: Int get() = maxCount - usedCount
    val usagePercentage: Float get() = (usedCount.toFloat() / maxCount) * 100
}
