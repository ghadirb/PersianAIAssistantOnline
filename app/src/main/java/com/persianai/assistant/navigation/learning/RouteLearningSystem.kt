package com.persianai.assistant.navigation.learning

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.navigation.models.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.*

/**
 * سیستم یادگیری مسیر - مسیرهای عبور شده کاربر را یاد می‌گیرد
 * و برای بهبود مسیریابی‌های آینده استفاده می‌کند
 */
class RouteLearningSystem(private val context: Context) {
    
    companion object {
        private const val TAG = "RouteLearningSystem"
        private const val LEARNED_ROUTES_FILE = "learned_routes.json"
        private const val ROUTE_POINTS_FILE = "route_points.json"
        private const val MIN_ROUTE_POINTS = 10 // حداقل نقاط برای یادگیری مسیر
        private const val SIMILARITY_THRESHOLD = 50.0 // متر
    }
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val learnedRoutesFile = File(context.filesDir, LEARNED_ROUTES_FILE)
    private val routePointsFile = File(context.filesDir, ROUTE_POINTS_FILE)
    
    private var currentRoutePoints: MutableList<GeoPoint> = mutableListOf()
    private var currentNavigationRoute: NavigationRoute? = null
    private var isLearning = false
    
    private var learnedRoutes: MutableList<LearnedRoute> = mutableListOf()
    
    init {
        loadLearnedRoutes()
    }
    
    /**
     * شروع یادگیری مسیر جدید
     */
    fun startLearningRoute(route: NavigationRoute) {
        currentNavigationRoute = route
        currentRoutePoints.clear()
        isLearning = true
        Log.d(TAG, "Started learning route: ${route.id}")
    }
    
    /**
     * به‌روزرسانی موقعیت کاربر در مسیر
     */
    fun updateLocation(location: GeoPoint) {
        if (!isLearning) return
        
        // اضافه کردن موقعیت جدید (با جلوگیری از تکرار)
        if (currentRoutePoints.isEmpty() || 
            location.distanceToAsDouble(currentRoutePoints.last()) > 10) {
            currentRoutePoints.add(location)
        }
    }
    
    /**
     * پایان یادگیری و ذخیره مسیر
     */
    suspend fun finishLearning(): LearnedRoute? = withContext(Dispatchers.IO) {
        if (!isLearning || currentRoutePoints.size < MIN_ROUTE_POINTS) {
            Log.d(TAG, "Route learning incomplete - insufficient points")
            return@withContext null
        }
        
        try {
            // ایجاد مسیر یادگرفته شده
            val learnedRoute = createLearnedRoute()
            if (learnedRoute != null) {
                // ذخیره مسیر
                saveLearnedRoute(learnedRoute)
                
                // تحلیل و بهبود مسیرهای موجود
                analyzeAndImproveRoutes()
                
                Log.d(TAG, "Route learned successfully: ${learnedRoute.id}")
                return@withContext learnedRoute
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing route learning", e)
        } finally {
            isLearning = false
            currentRoutePoints.clear()
            currentNavigationRoute = null
        }
        
        return@withContext null
    }
    
    /**
     * ایجاد مسیر یادگرفته شده از نقاط جمع‌آوری شده
     */
    private fun createLearnedRoute(): LearnedRoute? {
        val route = currentNavigationRoute ?: return null
        val points = currentRoutePoints.toList()
        
        // محاسبه اطلاعات مسیر
        val distance = calculateTotalDistance(points)
        val duration = System.currentTimeMillis() - (route.duration * 1000) // تخمین
        val averageSpeed = if (duration > 0) (distance / duration) * 3.6 else 0.0 // km/h
        
        // تولید نام برای مسیر
        val routeName = generateRouteName(points)
        
        return LearnedRoute(
            id = UUID.randomUUID().toString(),
            name = routeName,
            waypoints = points,
            averageSpeed = averageSpeed,
            travelTime = duration / 1000,
            distance = distance,
            usageCount = 1,
            rating = 4.0f,
            tags = extractTags(points),
            createdAt = System.currentTimeMillis(),
            lastUsed = System.currentTimeMillis()
        )
    }
    
    /**
     * محاسبه مسافت کل مسیر
     */
    private fun calculateTotalDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += points[i-1].distanceToAsDouble(points[i])
        }
        return totalDistance
    }
    
    /**
     * تولید نام برای مسیر بر اساس نقاط کلیدی
     */
    private fun generateRouteName(points: List<GeoPoint>): String {
        if (points.isEmpty()) return "مسیر ناشناس"
        
        val startPoint = points.first()
        val endPoint = points.last()
        
        // تلاش برای پیدا کردن نام خیابان‌ها (این بخش نیاز به API دارد)
        val startName = "مبدأ"
        val endName = "مقصد"
        
        return "مسیر $startName به $endName"
    }
    
    /**
     * استخراج تگ‌های مسیر (برای جستجو و دسته‌بندی)
     */
    private fun extractTags(points: List<GeoPoint>): List<String> {
        val tags = mutableListOf<String>()
        
        // تحلیل نوع مسیر بر اساس سرعت و مسافت
        val distance = calculateTotalDistance(points)
        when {
            distance > 10000 -> tags.add("مسیر طولانی")
            distance > 5000 -> tags.add("مسیر متوسط")
            else -> tags.add("مسیر کوتاه")
        }
        
        // تحلیل بر اساس مناطق شهری/شهری
        tags.add("شهری") // TODO: تحلیل دقیق‌تر
        
        return tags
    }
    
    /**
     * ذخیره مسیر یادگرفته شده
     */
    private fun saveLearnedRoute(route: LearnedRoute) {
        learnedRoutes.add(route)
        saveLearnedRoutesToFile()
    }
    
    /**
     * تحلیل و بهبود مسیرهای موجود
     */
    private fun analyzeAndImproveRoutes() {
        // پیدا کردن مسیرهای مشابه و ادغام آن‌ها
        val similarRoutes = findSimilarRoutes(currentRoutePoints)
        
        similarRoutes.forEach { similarRoute ->
            // ادغام مسیرها برای بهبود دقت
            mergeRoutes(similarRoute)
        }
    }
    
    /**
     * پیدا کردن مسیرهای مشابه
     */
    private fun findSimilarRoutes(points: List<GeoPoint>): List<LearnedRoute> {
        return learnedRoutes.filter { route ->
            areRoutesSimilar(points, route.waypoints)
        }
    }
    
    /**
     * بررسی شباهت دو مسیر
     */
    private fun areRoutesSimilar(route1: List<GeoPoint>, route2: List<GeoPoint>): Boolean {
        if (route1.isEmpty() || route2.isEmpty()) return false
        
        // بررسی شباهت نقاط شروع و پایان
        val startSimilar = route1.first().distanceToAsDouble(route2.first()) < SIMILARITY_THRESHOLD
        val endSimilar = route1.last().distanceToAsDouble(route2.last()) < SIMILARITY_THRESHOLD
        
        return startSimilar && endSimilar
    }
    
    /**
     * ادغام دو مسیر برای بهبود
     */
    private fun mergeRoutes(existingRoute: LearnedRoute) {
        // افزایش تعداد استفاده
        val index = learnedRoutes.indexOf(existingRoute)
        if (index != -1) {
            learnedRoutes[index] = existingRoute.copy(
                usageCount = existingRoute.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            )
            saveLearnedRoutesToFile()
        }
    }
    
    /**
     * دریافت مسیرهای یادگرفته شده
     */
    fun getLearnedRoutes(): List<LearnedRoute> {
        return learnedRoutes.toList()
    }
    
    /**
     * دریافت مسیرهای مشابه برای یک مقصد
     */
    fun getSimilarRoutes(destination: GeoPoint): List<LearnedRoute> {
        return learnedRoutes.filter { route ->
            route.waypoints.last().distanceToAsDouble(destination) < 1000 // 1km
        }.sortedByDescending { it.usageCount }
    }
    
    /**
     * دریافت مسیرهای محبوب
     */
    fun getPopularRoutes(limit: Int = 10): List<LearnedRoute> {
        return learnedRoutes
            .sortedByDescending { it.usageCount * it.rating }
            .take(limit)
    }
    
    /**
     * حذف مسیر یادگرفته شده
     */
    fun deleteRoute(routeId: String) {
        learnedRoutes.removeAll { it.id == routeId }
        saveLearnedRoutesToFile()
    }
    
    /**
     * ارزیابی مسیر (توسط کاربر)
     */
    fun rateRoute(routeId: String, rating: Float) {
        val index = learnedRoutes.indexOfFirst { it.id == routeId }
        if (index != -1) {
            val route = learnedRoutes[index]
            learnedRoutes[index] = route.copy(rating = rating)
            saveLearnedRoutesToFile()
        }
    }
    
    /**
     * بارگذاری مسیرهای یادگرفته شده از فایل
     */
    private fun loadLearnedRoutes() {
        try {
            if (learnedRoutesFile.exists()) {
                val json = learnedRoutesFile.readText()
                val type = object : TypeToken<MutableList<LearnedRoute>>() {}.type
                learnedRoutes = gson.fromJson(json, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading learned routes", e)
            learnedRoutes = mutableListOf()
        }
    }
    
    /**
     * ذخیره مسیرها در فایل
     */
    private fun saveLearnedRoutesToFile() {
        try {
            val json = gson.toJson(learnedRoutes)
            learnedRoutesFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving learned routes", e)
        }
    }
    
    /**
     * خالی کردن حافظه کش
     */
    fun clearCache() {
        learnedRoutes.clear()
        currentRoutePoints.clear()
        isLearning = false
        
        // حذف فایل‌ها
        try {
            learnedRoutesFile.delete()
            routePointsFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }
    
    /**
     * دریافت آمار یادگیری
     */
    fun getLearningStats(): LearningStats {
        return LearningStats(
            totalRoutes = learnedRoutes.size,
            totalDistance = learnedRoutes.sumOf { it.distance },
            averageRating = if (learnedRoutes.isNotEmpty()) 
                learnedRoutes.map { it.rating }.average().toFloat() else 0f,
            mostUsedRoute = learnedRoutes.maxByOrNull { it.usageCount },
            recentlyAdded = learnedRoutes.sortedByDescending { it.createdAt }.take(5)
        )
    }
}

/**
 * آمار یادگیری مسیر
 */
data class LearningStats(
    val totalRoutes: Int,
    val totalDistance: Double,
    val averageRating: Float,
    val mostUsedRoute: LearnedRoute?,
    val recentlyAdded: List<LearnedRoute>
)
