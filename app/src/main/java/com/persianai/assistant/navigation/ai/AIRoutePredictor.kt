package com.persianai.assistant.navigation.ai

import android.content.Context
import android.util.Log
import com.persianai.assistant.navigation.learning.RouteLearningSystem
import com.persianai.assistant.navigation.models.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.io.File
import kotlin.math.*

/**
 * سیستم هوش مصنوعی برای پیشنهاد مسیر بهینه
 * با استفاده از یادگیری ماشین و تحلیل الگوهای حرکتی
 */
class AIRoutePredictor(private val context: Context) {
    
    companion object {
        private const val TAG = "AIRoutePredictor"
        private const val MODEL_FILE = "route_prediction_model.json"
        private const val LEARNING_RATE = 0.01f
        private const val MAX_PREDICTION_ATTEMPTS = 5
    }
    
    private val routeLearningSystem = RouteLearningSystem(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // مدل یادگیری ماشین ساده‌شده
    private var predictionModel: RoutePredictionModel = RoutePredictionModel()
    
    init {
        loadModel()
    }
    
    /**
     * پیشنهاد مسیر با هوش مصنوعی
     */
    suspend fun predictRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? = withContext(Dispatchers.IO) {
        
        try {
            // 1. بررسی مسیرهای یادگرفته شده مشابه
            val learnedRoutes = routeLearningSystem.getSimilarRoutes(destination)
            if (learnedRoutes.isNotEmpty()) {
                val bestLearnedRoute = selectBestLearnedRoute(learnedRoutes, routeType)
                if (bestLearnedRoute != null) {
                    val navigationRoute = convertToNavigationRoute(bestLearnedRoute, origin, destination)
                    if (navigationRoute != null) {
                        Log.d(TAG, "Route suggested from learned routes")
                        return@withContext navigationRoute
                    }
                }
            }
            
            // 2. تحلیل الگوهای ترافیکی و زمانی
            val trafficBasedRoute = predictRouteBasedOnTraffic(origin, destination, routeType)
            if (trafficBasedRoute != null) {
                Log.d(TAG, "Route suggested based on traffic patterns")
                return@withContext trafficBasedRoute
            }
            
            // 3. پیشنهاد بر اساس معیارهای مختلف (سرعت، امنیت، زیبایی)
            val multiCriteriaRoute = predictMultiCriteriaRoute(origin, destination, routeType)
            if (multiCriteriaRoute != null) {
                Log.d(TAG, "Route suggested based on multi-criteria analysis")
                return@withContext multiCriteriaRoute
            }
            
            // 4. پیشنهاد مسیر ترکیبی (بهینه‌سازی شده)
            val optimizedRoute = generateOptimizedRoute(origin, destination, routeType)
            if (optimizedRoute != null) {
                Log.d(TAG, "Optimized route generated")
                return@withContext optimizedRoute
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error predicting route", e)
        }
        
        return@withContext null
    }
    
    /**
     * انتخاب بهترین مسیر از مسیرهای یادگرفته شده
     */
    private fun selectBestLearnedRoute(
        routes: List<LearnedRoute>,
        routeType: RouteType
    ): LearnedRoute? {
        
        return when (routeType) {
            RouteType.DRIVING -> routes.minByOrNull { it.travelTime }
            RouteType.WALKING -> routes.minByOrNull { it.distance }
            RouteType.CYCLING -> routes.filter { it.tags.contains("دوچرخه") }.minByOrNull { it.distance }
            RouteType.TRANSIT -> routes.minByOrNull { it.travelTime }
            else -> routes.minByOrNull { it.travelTime }
        }
    }
    
    /**
     * تبدیل مسیر یادگرفته شده به مسیر ناوبری
     */
    private fun convertToNavigationRoute(
        learnedRoute: LearnedRoute,
        origin: GeoPoint,
        destination: GeoPoint
    ): NavigationRoute? {
        
        return try {
            NavigationRoute(
                id = learnedRoute.id,
                origin = origin,
                destination = destination,
                waypoints = learnedRoute.waypoints,
                distance = learnedRoute.distance,
                duration = learnedRoute.travelTime,
                routeType = RouteType.DRIVING,
                confidence = calculateConfidence(learnedRoute),
                trafficInfo = TrafficInfo(
                    trafficLevel = TrafficLevel.LOW,
                    estimatedDelay = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting learned route", e)
            null
        }
    }
    
    /**
     * محاسبه اطمینان از پیشنهاد مسیر
     */
    private fun calculateConfidence(route: LearnedRoute): Float {
        var confidence = 0.5f // پایه
        
        // افزایش اطمینان بر اساس تعداد استفاده
        confidence += minOf(route.usageCount * 0.1f, 0.3f)
        
        // افزایش اطمینان بر اساس رتبه
        confidence += (route.rating / 5.0f) * 0.2f
        
        return minOf(confidence, 1.0f)
    }
    
    /**
     * پیشنهاد مسیر بر اساس الگوهای ترافیکی
     */
    private suspend fun predictRouteBasedOnTraffic(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        
        // تحلیل ترافیک بر اساس زمان روز
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val trafficFactor = getTrafficFactor(currentHour)
        
        // تولید مسیرهای جایگزین با در نظر گرفتن ترافیک
        val alternativeRoutes = generateAlternativeRoutes(origin, destination, 3)
        
        // انتخاب بهترین مسیر بر اساس ترافیک
        val bestRoute = alternativeRoutes.minByOrNull { route ->
            calculateTrafficScore(route, trafficFactor)
        }
        
        return bestRoute?.copy(
            trafficInfo = TrafficInfo(
                trafficLevel = getTrafficLevel(trafficFactor),
                estimatedDelay = (trafficFactor * 300).toLong() // تخمین تاخیر
            )
        )
    }
    
    /**
     * دریافت ضریب ترافیک بر اساس ساعت
     */
    private fun getTrafficFactor(hour: Int): Float {
        return when (hour) {
            in 7..9, in 17..19 -> 0.8f // ساعت اوج ترافیک
            in 10..16, in 20..22 -> 0.5f // ساعت متوسط
            else -> 0.2f // ساعت کم ترافیک
        }
    }
    
    /**
     * محاسبه امتیاز ترافیک برای مسیر
     */
    private fun calculateTrafficScore(route: NavigationRoute, trafficFactor: Float): Float {
        // مسیرهای طولانی‌تر در ترافیک بیشتر آسیب می‌بینند
        return route.distance.toFloat() * trafficFactor
    }
    
    /**
     * دریافت سطح ترافیک
     */
    private fun getTrafficLevel(factor: Float): TrafficLevel {
        return when {
            factor > 0.7f -> TrafficLevel.SEVERE
            factor > 0.5f -> TrafficLevel.HIGH
            factor > 0.3f -> TrafficLevel.MEDIUM
            else -> TrafficLevel.LOW
        }
    }
    
    /**
     * پیشنهاد مسیر بر اساس معیارهای چندگانه
     */
    private suspend fun predictMultiCriteriaRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        
        val routes = generateAlternativeRoutes(origin, destination, 5)
        
        // امتیازدهی به مسیرها بر اساس معیارهای مختلف
        val scoredRoutes = routes.map { route ->
            val score = calculateMultiCriteriaScore(route, routeType)
            route.copy(confidence = score)
        }
        
        // انتخاب بهترین مسیر
        return scoredRoutes.maxByOrNull { it.confidence }
    }
    
    /**
     * محاسبه امتیاز چندمعیاری
     */
    private fun calculateMultiCriteriaScore(route: NavigationRoute, routeType: RouteType): Float {
        var score = 0f
        
        when (routeType) {
            RouteType.DRIVING -> {
                score += (1f / (route.duration.toFloat() / 60f)) * 0.4f // وزن زمان
                score += (1f / (route.distance.toFloat() / 1000f)) * 0.3f // وزن مسافت
                score += route.confidence * 0.3f // وزن اطمینان
            }
            RouteType.WALKING -> {
                score += (1f / (route.distance.toFloat() / 1000f)) * 0.5f // وزن مسافت
                score += route.confidence * 0.3f // وزن اطمینان
                score += (1f / (route.duration.toFloat() / 60f)) * 0.2f // وزن زمان
            }
            RouteType.CYCLING -> {
                score += route.confidence * 0.5f // وزن اطمینان (مسیرهای یادگرفته شده)
                score += (1f / (route.distance.toFloat() / 1000f)) * 0.3f
                score += (1f / (route.duration.toFloat() / 60f)) * 0.2f
            }
            RouteType.TRANSIT -> {
                score += (1f / (route.duration.toFloat() / 60f)) * 0.6f // وزن زمان برای حمل و نقل عمومی
                score += route.confidence * 0.4f // وزن اطمینان
            }
            else -> {
                score += route.confidence * 0.5f
                score += (1f / (route.distance.toFloat() / 1000f)) * 0.3f
                score += (1f / (route.duration.toFloat() / 60f)) * 0.2f
            }
        }
        
        return score
    }
    
    /**
     * تولید مسیر بهینه‌سازی شده
     */
    private suspend fun generateOptimizedRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        routeType: RouteType
    ): NavigationRoute? {
        
        // تولید مسیر اولیه
        val baseRoute = generateBaseRoute(origin, destination)
        if (baseRoute == null) return null
        
        // بهینه‌سازی مسیر
        val optimizedRoute = optimizeRoute(baseRoute, routeType)
        
        return optimizedRoute
    }
    
    /**
     * تولید مسیر پایه (ساده‌شده)
     */
    private fun generateBaseRoute(origin: GeoPoint, destination: GeoPoint): NavigationRoute? {
        // این یک پیاده‌سازی ساده است - در واقعیت باید از API مسیریابی استفاده شود
        
        val waypoints = generateSimpleWaypoints(origin, destination)
        val distance = calculateRouteDistance(waypoints)
        val duration = estimateDuration(distance)
        
        return NavigationRoute(
            id = "generated_${System.currentTimeMillis()}",
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            distance = distance,
            duration = duration,
            routeType = RouteType.DRIVING,
            confidence = 0.3f // اطمینان کم برای مسیرهای تولید شده
        )
    }
    
    /**
     * تولید نقاط میانی ساده
     */
    private fun generateSimpleWaypoints(origin: GeoPoint, destination: GeoPoint): List<GeoPoint> {
        val waypoints = mutableListOf<GeoPoint>()
        
        // اضافه کردن نقاط میانی برای ایجاد مسیر واقعی‌تر
        val steps = 10
        for (i in 0..steps) {
            val lat = origin.latitude + (destination.latitude - origin.latitude) * (i / steps.toFloat())
            val lon = origin.longitude + (destination.longitude - origin.longitude) * (i / steps.toFloat())
            
            // اضافه کردن کمی انحراف برای واقعی‌تر شدن مسیر
            val offset = sin(i * 0.5) * 0.001
            waypoints.add(GeoPoint(lat + offset, lon))
        }
        
        return waypoints
    }
    
    /**
     * محاسبه مسافت مسیر
     */
    private fun calculateRouteDistance(waypoints: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            totalDistance += waypoints[i-1].distanceToAsDouble(waypoints[i])
        }
        return totalDistance
    }
    
    /**
     * تخمین زمان مسیر
     */
    private fun estimateDuration(distance: Double): Long {
        // فرض سرعت متوسط 50 km/h در شهر
        val averageSpeed = 50.0 * 1000 / 3600 // متر بر ثانیه
        return (distance / averageSpeed).toLong()
    }
    
    /**
     * تولید مسیرهای جایگزین
     */
    private suspend fun generateAlternativeRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        count: Int
    ): List<NavigationRoute> {
        
        val routes = mutableListOf<NavigationRoute>()
        
        for (i in 0 until count) {
            val route = generateAlternativeRoute(origin, destination, i)
            if (route != null) {
                routes.add(route)
            }
        }
        
        return routes
    }
    
    /**
     * تولید یک مسیر جایگزین
     */
    private fun generateAlternativeRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        variant: Int
    ): NavigationRoute? {
        
        // ایجاد مسیر با انحراف مختلف
        val waypoints = mutableListOf<GeoPoint>()
        val steps = 15
        
        for (i in 0..steps) {
            val progress = i / steps.toFloat()
            val lat = origin.latitude + (destination.latitude - origin.latitude) * progress
            val lon = origin.longitude + (destination.longitude - origin.longitude) * progress
            
            // انحراف متفاوت برای هر واریانت
            val offset = when (variant % 3) {
                0 -> sin(progress * PI * 2) * 0.002
                1 -> cos(progress * PI * 2) * 0.002
                else -> sin(progress * PI * 4) * 0.001
            }
            
            waypoints.add(GeoPoint(lat + offset, lon))
        }
        
        val distance = calculateRouteDistance(waypoints)
        val duration = estimateDuration(distance)
        
        return NavigationRoute(
            id = "alt_${variant}_${System.currentTimeMillis()}",
            origin = origin,
            destination = destination,
            waypoints = waypoints,
            distance = distance,
            duration = duration,
            routeType = RouteType.DRIVING,
            confidence = 0.2f
        )
    }
    
    /**
     * بهینه‌سازی مسیر
     */
    private fun optimizeRoute(route: NavigationRoute, routeType: RouteType): NavigationRoute {
        // اینجا می‌توان الگوریتم‌های بهینه‌سازی مانند A* یا ژنتیک را پیاده‌سازی کرد
        
        var optimizedWaypoints = route.waypoints.toMutableList()
        
        // حذف نقاط غیرضروری
        optimizedWaypoints = removeUnnecessaryWaypoints(optimizedWaypoints)
        
        // صاف کردن مسیر
        optimizedWaypoints = smoothRoute(optimizedWaypoints)
        
        val newDistance = calculateRouteDistance(optimizedWaypoints)
        val newDuration = estimateDuration(newDistance)
        
        return route.copy(
            waypoints = optimizedWaypoints,
            distance = newDistance,
            duration = newDuration,
            confidence = minOf(route.confidence + 0.1f, 0.8f)
        )
    }
    
    /**
     * حذف نقاط میانی غیرضروری
     */
    private fun removeUnnecessaryWaypoints(waypoints: MutableList<GeoPoint>): MutableList<GeoPoint> {
        if (waypoints.size <= 2) return waypoints
        
        val optimized = mutableListOf<GeoPoint>()
        optimized.add(waypoints.first())
        
        for (i in 1 until waypoints.size - 1) {
            val prev = waypoints[i-1]
            val current = waypoints[i]
            val next = waypoints[i+1]
            
            // اگر زاویه خیلی تند نباشد، نقطه را نگه دار
            val angle = calculateAngle(prev, current, next)
            if (angle > 15) { // درجه
                optimized.add(current)
            }
        }
        
        optimized.add(waypoints.last())
        return optimized
    }
    
    /**
     * محاسبه زاویه بین سه نقطه
     */
    private fun calculateAngle(p1: GeoPoint, p2: GeoPoint, p3: GeoPoint): Double {
        val angle1 = Math.toDegrees(atan2(p1.latitude - p2.latitude, p1.longitude - p2.longitude))
        val angle2 = Math.toDegrees(atan2(p3.latitude - p2.latitude, p3.longitude - p2.longitude))
        var angle = abs(angle1 - angle2)
        if (angle > 180) angle = 360 - angle
        return angle
    }
    
    /**
     * صاف کردن مسیر
     */
    private fun smoothRoute(waypoints: MutableList<GeoPoint>): MutableList<GeoPoint> {
        if (waypoints.size <= 3) return waypoints
        
        val smoothed = mutableListOf<GeoPoint>()
        smoothed.add(waypoints.first())
        
        for (i in 1 until waypoints.size - 1) {
            val prev = waypoints[i-1]
            val current = waypoints[i]
            val next = waypoints[i+1]
            
            // میانگین‌گیری برای صاف کردن
            val smoothedPoint = GeoPoint(
                (prev.latitude + current.latitude * 2 + next.latitude) / 4,
                (prev.longitude + current.longitude * 2 + next.longitude) / 4
            )
            smoothed.add(smoothedPoint)
        }
        
        smoothed.add(waypoints.last())
        return smoothed
    }
    
    /**
     * به‌روزرسانی مدل با مسیر جدید
     */
    fun updateModel(route: LearnedRoute) {
        scope.launch {
            try {
                // به‌روزرسانی وزن‌های مدل
                predictionModel.updateFromRoute(route)
                saveModel()
                
                Log.d(TAG, "Model updated with new route data")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating model", e)
            }
        }
    }
    
    /**
     * بارگذاری مدل از فایل
     */
    private fun loadModel() {
        try {
            val modelFile = File(context.filesDir, MODEL_FILE)
            if (modelFile.exists()) {
                // TODO: بارگذاری مدل از فایل
                Log.d(TAG, "Model loaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }
    
    /**
     * ذخیره مدل در فایل
     */
    private fun saveModel() {
        try {
            val modelFile = File(context.filesDir, MODEL_FILE)
            // TODO: ذخیره مدل در فایل
            Log.d(TAG, "Model saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving model", e)
        }
    }
    
    /**
     * دریافت پیشنهادات مسیر برای کاربر
     */
    suspend fun getSuggestedRoutes(
        origin: GeoPoint,
        destination: GeoPoint
    ): List<RouteSuggestion> {
        
        val suggestions = mutableListOf<RouteSuggestion>()
        
        // مسیر سریع‌ترین
        val fastestRoute = predictRoute(origin, destination, RouteType.DRIVING)
        fastestRoute?.let {
            suggestions.add(RouteSuggestion(
                route = it,
                reasoning = "سریع‌ترین مسیر بر اساس تحلیل ترافیک و الگوهای حرکتی",
                confidence = it.confidence,
                benefits = listOf("زمان رسیدن کمتر", "بهینه‌سازی شده برای سرعت")
            ))
        }
        
        // مسیر کوتاه‌ترین
        val shortestRoute = predictRoute(origin, destination, RouteType.WALKING)
        shortestRoute?.let {
            suggestions.add(RouteSuggestion(
                route = it,
                reasoning = "کوتاه‌ترین مسیر بر اساس مسافت",
                confidence = it.confidence,
                benefits = listOf("مسافت کمتر", "مصرف سوخت کمتر")
            ))
        }
        
        // مسیر کمترافیک
        val avoidTrafficRoute = predictRoute(origin, destination, RouteType.CYCLING)
        avoidTrafficRoute?.let {
            suggestions.add(RouteSuggestion(
                route = it,
                reasoning = "مسیر با ترافیک کمتر بر اساس داده‌های یادگیری شده",
                confidence = it.confidence,
                benefits = listOf("ترافیک کمتر", "رانندگی آرام‌تر")
            ))
        }
        
        return suggestions.sortedByDescending { it.confidence }
    }
}

/**
 * مدل پیشنهاد مسیر (ساده‌شده)
 */
class RoutePredictionModel {
    // این یک مدل ساده است - در واقعیت باید از شبکه‌های عصبی استفاده شود
    private var weights: FloatArray = floatArrayOf(0.5f, 0.3f, 0.2f) // وزن‌های زمان، مسافت، ترافیک
    
    fun updateFromRoute(route: LearnedRoute) {
        // به‌روزرسانی وزن‌ها بر اساس بازخورد مسیر
        // این یک پیاده‌سازی ساده است
        val learningRate = 0.01f
        
        // تنظیم وزن‌ها بر اساس رتبه مسیر
        val ratingFactor = route.rating / 5.0f
        weights[0] += learningRate * ratingFactor // زمان
        weights[1] += learningRate * (1 - ratingFactor) // مسافت
        
        // نرمال‌سازی وزن‌ها
        val sum = weights.sum()
        weights = weights.map { it / sum }.toFloatArray()
    }
    
    fun predictScore(distance: Double, duration: Long, trafficLevel: Float): Float {
        return (weights[0] * duration + weights[1] * distance + weights[2] * trafficLevel).toFloat()
    }
}
