package com.persianai.assistant.utils

import android.content.Context
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.persianai.assistant.models.LatLng
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.model.DirectionsResult
import com.google.maps.model.TravelMode
import com.persianai.assistant.navigation.models.*
import com.persianai.assistant.navigation.models.RouteType
import com.persianai.assistant.models.*
import kotlinx.coroutines.Dispatchers
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import kotlinx.coroutines.withContext
import java.util.*

/**
 * مدیر مسیریابی حرفه‌ای با قابلیت‌های کامل
 */
class NavigationManager(private val context: Context) {
    
    private val geoApiContext: GeoApiContext
    private val navigationSettings: NavigationSettings
    
    init {
        geoApiContext = GeoApiContext.Builder()
            .apiKey("YOUR_GOOGLE_MAPS_API_KEY")
            .build()
            
        navigationSettings = loadNavigationSettings()
    }
    
    /**
     * جستجوی مکان با نام یا آدرس
     */
    suspend fun searchPlace(query: String): Place? = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی جستجوی مکان با Places API
            // فعلاً یک مکان نمونه برمی‌گردانیم
            Place(
                id = "sample_place",
                name = query,
                address = "تهران، ایران",
                latitude = 35.6892,
                longitude = 51.3890,
                type = PlaceType.UNKNOWN
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * محاسبه مسیر بین دو نقطه
     */
    suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        routeType: RouteType = RouteType.DRIVING
    ): com.persianai.assistant.navigation.models.NavigationRoute = withContext(Dispatchers.IO) {
        // TODO: Implement Google Directions API integration
        // For now, return a simple default route with a fake step
        val base = createDefaultRoute(startLat, startLng, endLat, endLng)
        val step = com.persianai.assistant.navigation.models.NavigationStep(
            instruction = "به مقصد حرکت کنید",
            distance = base.distance,
            duration = base.duration,
            startLocation = com.persianai.assistant.navigation.models.GeoPoint(startLat, startLng),
            endLocation = com.persianai.assistant.navigation.models.GeoPoint(endLat, endLng),
            maneuver = "straight",
            polyline = "${startLat},${startLng};${endLat},${endLng}"
        )
        base.copy(steps = listOf(step))
    }
    
    /**
     * دریافت مسیرهای جایگزین
     */
    suspend fun getAlternativeRoutes(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): List<com.persianai.assistant.navigation.models.NavigationRoute> = withContext(Dispatchers.IO) {
        // TODO: Implement alternative routes
        emptyList()
    }
    
    /**
     * دریافت مرحله فعلی مسیر
     */
    // TODO: Implement when NavigationStep is added to NavigationRoute
    // fun getCurrentStep(route: com.persianai.assistant.navigation.models.NavigationRoute, currentLocation: android.location.Location): com.persianai.assistant.navigation.models.NavigationStep? {
    //     val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
    //     
    //     return route.steps.minByOrNull { step ->
    //         distanceBetween(currentLatLng, step.startLocation)
    //     }
    // }
    
    /**
     * بررسی رسیدن به مقصد
     */
    fun hasReachedDestination(route: com.persianai.assistant.navigation.models.NavigationRoute, currentLocation: android.location.Location): Boolean {
        val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        val destination = route.waypoints.last()
        
        return distanceBetween(currentLatLng, LatLng(destination.latitude, destination.longitude)) < 50 // ۵۰ متر به مقصد
    }
    
    /**
     * دریافت اطلاعات ترافیک مسیر
     */
    suspend fun getTrafficInfo(route: com.persianai.assistant.navigation.models.NavigationRoute): com.persianai.assistant.navigation.models.TrafficInfo = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی دریافت اطلاعات ترافیک واقعی
            com.persianai.assistant.navigation.models.TrafficInfo(
                trafficLevel = com.persianai.assistant.navigation.models.TrafficLevel.MEDIUM,
                estimatedDelay = 300
            )
        } catch (e: Exception) {
            com.persianai.assistant.navigation.models.TrafficInfo(
                trafficLevel = com.persianai.assistant.navigation.models.TrafficLevel.LOW,
                estimatedDelay = 0
            )
        }
    }
    
    /**
     * دریافت محدودیت سرعت
     */
    suspend fun getSpeedLimit(location: LatLng): SpeedLimit? = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی دریافت محدودیت سرعت
            SpeedLimit(
                speed = 50,
                unit = "km/h",
                isStrict = false
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * جستجوی پارکینگ‌های نزدیک
     */
    suspend fun findNearbyParking(location: LatLng, radius: Double = 1000.0): List<ParkingInfo> = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی جستجوی پارکینگ
            listOf(
                ParkingInfo(
                    id = "parking_1",
                    name = "پارکینگ عمومی",
                    location = LatLng(location.latitude + 0.001, location.longitude + 0.001),
                    availableSpots = 25,
                    totalSpots = 100,
                    pricePerHour = 5000.0,
                    isOpen = true,
                    hasDisabledParking = true
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * جستجوی بنزین‌زایی‌های نزدیک
     */
    suspend fun findNearbyGasStations(location: LatLng, radius: Double = 2000.0): List<GasStationInfo> = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی جستجوی بنزین‌زایی
            listOf(
                GasStationInfo(
                    id = "gas_1",
                    name = "پمپ بنزین ولیعصر",
                    location = LatLng(location.latitude + 0.002, location.longitude + 0.002),
                    hasGasoline = true,
                    hasDiesel = true,
                    hasCNG = false,
                    priceGasoline = 15000.0,
                    priceDiesel = 14000.0,
                    priceCNG = 8000.0,
                    isOpen = true,
                    queueLength = 1
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * دریافت رویدادهای مسیر (تصادف، ساخت‌وساز و...)
     */
    // TODO: Fix NavigationRoute ambiguity
    suspend fun getRouteEvents(route: com.persianai.assistant.navigation.models.NavigationRoute): List<RouteEvent> = withContext(Dispatchers.IO) {
        try {
            // TODO: پیاده‌سازی دریافت رویدادهای مسیر
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * محاسبه آمار سفر
     */
    fun calculateTripStats(route: com.persianai.assistant.navigation.models.NavigationRoute, actualTime: Long): NavigationStats {
        val averageSpeed = if (actualTime > 0) (route.distance / actualTime) * 3.6 else 0.0 // km/h
        val fuelConsumed = estimateFuelConsumption(route.distance)
        val co2Emissions = fuelConsumed * 2.31 // kg CO2 per liter gasoline
        
        return NavigationStats(
            totalDistance = route.distance,
            totalTime = actualTime,
            averageSpeed = averageSpeed,
            maxSpeed = estimateMaxSpeed(route),
            fuelConsumed = fuelConsumed,
            co2Emissions = co2Emissions,
            tollsPaid = estimateTolls(route),
            stopsCount = 0
        )
    }
    
    /**
     * افزودن مکان به موارد دلخواه
     */
    suspend fun addToFavorite(place: Place, category: FavoriteCategory): Boolean = withContext(Dispatchers.IO) {
        try {
            val favorite = FavoritePlace(
                id = UUID.randomUUID().toString(),
                name = place.name,
                address = place.address,
                location = LatLng(place.latitude, place.longitude),
                category = category,
                icon = getIconForCategory(category),
                addedDate = System.currentTimeMillis()
            )
            
            val favorites = getFavorites().toMutableList()
            favorites.add(favorite)
            saveFavorites(favorites)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * دریافت موارد دلخواه
     */
    fun getFavorites(): List<FavoritePlace> {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("favorite_places", null)
        
        return if (json != null) {
            com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<FavoritePlace>>() {}.type)
                ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * ذخیره تاریخچه مسیرها
     */
    suspend fun saveRouteHistory(route: com.persianai.assistant.navigation.models.NavigationRoute, startAddress: String, endAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val history = RouteHistory(
                id = UUID.randomUUID().toString(),
                startAddress = startAddress,
                endAddress = endAddress,
                startLocation = com.persianai.assistant.models.LatLng(route.origin.latitude, route.origin.longitude),
                endLocation = com.persianai.assistant.models.LatLng(route.destination.latitude, route.destination.longitude),
                distance = route.distance,
                duration = route.duration,
                date = System.currentTimeMillis(),
                routeType = com.persianai.assistant.models.RouteType.DRIVING
            )
            
            val histories = getRouteHistory().toMutableList()
            histories.add(0, history) // اضافه به ابتدا
            
            // نگه داشتن فقط ۵۰ آخری
            if (histories.size > 50) {
                histories.removeAt(histories.size - 1)
            }
            
            saveRouteHistory(histories)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * دریافت تاریخچه مسیرها
     */
    fun getRouteHistory(): List<RouteHistory> {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("route_history", null)
        
        return if (json != null) {
            com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<RouteHistory>>() {}.type)
                ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    // توابع کمکی
    
    private fun toTravelMode(routeType: com.persianai.assistant.navigation.models.RouteType): TravelMode {
        return when (routeType) {
            com.persianai.assistant.navigation.models.RouteType.DRIVING -> TravelMode.DRIVING
            com.persianai.assistant.navigation.models.RouteType.WALKING -> TravelMode.WALKING
            com.persianai.assistant.navigation.models.RouteType.CYCLING -> TravelMode.BICYCLING
            com.persianai.assistant.navigation.models.RouteType.TRANSIT -> TravelMode.TRANSIT
        }
    }
    
    private fun convertToNavigationRoute(result: DirectionsResult, routeType: com.persianai.assistant.navigation.models.RouteType): com.persianai.assistant.navigation.models.NavigationRoute {
        val route = result.routes[0]
        val leg = route.legs[0]
        
        val points = route.overviewPolyline.decodePath().map { point ->
            LatLng(point.lat, point.lng)
        }
        
        // TODO: Fix NavigationStep if needed
        val steps = emptyList<com.persianai.assistant.navigation.models.NavigationStep>()
        
        val bounds = LatLngBounds(
            northeast = LatLng(route.bounds.northeast.lat, route.bounds.northeast.lng),
            southwest = LatLng(route.bounds.southwest.lat, route.bounds.southwest.lng)
        )
        
        return com.persianai.assistant.navigation.models.NavigationRoute(
            id = UUID.randomUUID().toString(),
            origin = OsmGeoPoint(leg.startLocation.lat, leg.startLocation.lng),
            destination = OsmGeoPoint(leg.endLocation.lat, leg.endLocation.lng),
            waypoints = points.map { OsmGeoPoint(it.latitude, it.longitude) },
            distance = leg.distance.inMeters.toDouble(),
            duration = leg.duration.inSeconds,
            routeType = routeType,
            trafficInfo = com.persianai.assistant.navigation.models.TrafficInfo(
                trafficLevel = com.persianai.assistant.navigation.models.TrafficLevel.values()[estimateTrafficLevel(leg.duration.inSeconds.toInt(), leg.distance.inMeters.toInt())],
                estimatedDelay = 0
            )
        )
    }
    
    // TODO: Implement when Google Maps SDK is properly configured
    // private fun convertToNavigationRoute(route: com.google.maps.model.Route, routeType: com.persianai.assistant.navigation.models.RouteType): com.persianai.assistant.navigation.models.NavigationRoute {
    //     return createDefaultRoute(0.0, 0.0, 0.0, 0.0)
    // }
    
    private fun convertManeuver(maneuver: String?): String {
        return when (maneuver) {
            "turn-left" -> "چپ گرد"
            "turn-right" -> "راست گرد"
            "straight" -> "مستقیم"
            "slight-left" -> "کم چپ"
            "slight-right" -> "کم راست"
            "sharp-left" -> "تند چپ"
            "sharp-right" -> "تند راست"
            "uturn-left" -> "گردش به عقب چپ"
            "uturn-right" -> "گردش به عقب راست"
            else -> "مستقیم"
        }
    }
    
    private fun estimateTrafficLevel(duration: Int, distance: Int): Int {
        val averageSpeed = (distance / duration) * 3.6 // km/h
        
        return when {
            averageSpeed > 60 -> 0 // روان
            averageSpeed > 40 -> 1 // متوسط
            averageSpeed > 20 -> 2 // سنگین
            else -> 2 // بسیار سنگین
        }
    }
    
    private fun createDefaultRoute(startLat: Double, startLng: Double, endLat: Double, endLng: Double): com.persianai.assistant.navigation.models.NavigationRoute {
        val points = listOf(
            LatLng(startLat, startLng),
            LatLng(endLat, endLng)
        )
        
        val distance = distanceBetween(points[0], points[1])
        val duration = (distance / 50 * 3600).toLong() // فرض سرعت ۵۰ کیلومتر بر ساعت
        
        return com.persianai.assistant.navigation.models.NavigationRoute(
            id = UUID.randomUUID().toString(),
            origin = OsmGeoPoint(startLat, startLng),
            destination = OsmGeoPoint(endLat, endLng),
            waypoints = points.map { OsmGeoPoint(it.latitude, it.longitude) },
            distance = distance,
            duration = duration,
            routeType = com.persianai.assistant.navigation.models.RouteType.DRIVING,
            trafficInfo = com.persianai.assistant.navigation.models.TrafficInfo(
                trafficLevel = com.persianai.assistant.navigation.models.TrafficLevel.LOW,
                estimatedDelay = 0
            )
        )
    }
    
    private fun distanceBetween(point1: LatLng, point2: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0].toDouble()
    }
    
    private fun estimateFuelConsumption(distance: Double): Double {
        // فرض مصرف ۸ لیتر در هر ۱۰۰ کیلومتر
        return (distance / 1000) * 0.08
    }
    
    private fun estimateMaxSpeed(route: com.persianai.assistant.navigation.models.NavigationRoute): Double {
        // تخمین حداکثر سرعت بر اساس نوع مسیر
        return 80.0 // km/h
    }
    
    private fun estimateTolls(route: com.persianai.assistant.navigation.models.NavigationRoute): Double {
        // تخمین هزینه عوارض
        return 0.0
    }
    
    private fun getIconForCategory(category: FavoriteCategory): String {
        return when (category) {
            FavoriteCategory.HOME -> "home"
            FavoriteCategory.WORK -> "work"
            FavoriteCategory.SCHOOL -> "school"
            FavoriteCategory.RESTAURANT -> "restaurant"
            FavoriteCategory.SHOPPING -> "shopping"
            FavoriteCategory.ENTERTAINMENT -> "entertainment"
            FavoriteCategory.OTHER -> "place"
        }
    }
    
    private fun loadNavigationSettings(): NavigationSettings {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        return NavigationSettings(
            avoidTolls = prefs.getBoolean("avoid_tolls", false),
            avoidHighways = prefs.getBoolean("avoid_highways", false),
            avoidFerries = prefs.getBoolean("avoid_ferries", false),
            avoidUnpavedRoads = prefs.getBoolean("avoid_unpaved", true),
            preferredRouteType = com.persianai.assistant.models.RouteType.valueOf(prefs.getString("preferred_type", "DRIVING") ?: "DRIVING"),
            voiceNavigation = prefs.getBoolean("voice_navigation", true),
            autoReroute = prefs.getBoolean("auto_reroute", true),
            showTraffic = prefs.getBoolean("show_traffic", true),
            speedLimitAlerts = prefs.getBoolean("speed_limit_alerts", true),
            speedCameraAlerts = prefs.getBoolean("speed_camera_alerts", true)
        )
    }
    
    private fun saveFavorites(favorites: List<FavoritePlace>) {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(favorites)
        prefs.edit().putString("favorite_places", json).apply()
    }
    
    private fun saveRouteHistory(histories: List<RouteHistory>) {
        val prefs = context.getSharedPreferences("navigation_prefs", Context.MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(histories)
        prefs.edit().putString("route_history", json).apply()
    }
    
    private fun String.removeHtmlTags(): String {
        return this.replace("<[^>]*>".toRegex(), "")
    }
}
