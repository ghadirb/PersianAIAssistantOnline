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
    
    // مدیریت کش و یادگیری
    private val routeCache = RouteCache(context)
    private val routeLearningSystem = RouteLearningSystem(context)
    private val speedCameraDetector = SpeedCameraDetector(context)
    private val trafficAnalyzer = TrafficAnalyzer(context)
    private val roadConditionAnalyzer = RoadConditionAnalyzer(context)
    private val aiRoutePredictor = AIRoutePredictor(context)
    
    // وضعیت سرویس‌ها
    private var isNavigationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NAVIGATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NAVIGATION_ENABLED, value).apply()
    
    private var areSpeedAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPEED_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SPEED_ALERTS_ENABLED, value).apply()
    
    private var areCameraAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_CAMERA_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CAMERA_ALERTS_ENABLED, value).apply()
    
    private var areTrafficAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRAFFIC_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TRAFFIC_ALERTS_ENABLED, value).apply()
    
    private var areRoadConditionAlertsEnabled: Boolean
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
        if (canUseApi() && getNeshanApiKey() != null) {
            try {
                val apiRoute = getRouteFromNeshanAPI(origin, destination, routeType)
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
    private suspend fun getRouteFromNeshanAPI(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        // پیاده‌سازی API نشان
        return null // TODO: پیاده‌سازی API نشان
    }
    
    /**
     * دریافت مسیر از OSM (آفلاین)
     */
    private suspend fun getOSMRoute(
        origin: GeoPoint,
        destination: GeoPoint
    ): NavigationRoute? {
        // پیاده‌سازی مسیریابی OSM
        return null // TODO: پیاده‌سازی OSM routing
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
     */
    fun setNavigationEnabled(enabled: Boolean) {
        isNavigationEnabled = enabled
    }
    
    fun setSpeedAlertsEnabled(enabled: Boolean) {
        areSpeedAlertsEnabled = enabled
    }
    
    fun setCameraAlertsEnabled(enabled: Boolean) {
        areCameraAlertsEnabled = enabled
    }
    
    fun setTrafficAlertsEnabled(enabled: Boolean) {
        areTrafficAlertsEnabled = enabled
    }
    
    fun setRoadConditionAlertsEnabled(enabled: Boolean) {
        areRoadConditionAlertsEnabled = enabled
    }
    
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
