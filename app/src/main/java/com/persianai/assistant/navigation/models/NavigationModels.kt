package com.persianai.assistant.navigation.models

import org.osmdroid.util.GeoPoint

/**
 * مدل‌های داده برای سیستم مسیریاب
 */

/**
 * مسیر ناوبری
 */
data class NavigationRoute(
    val id: String,
    val origin: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint>,
    val distance: Double, // متر
    val duration: Long, // ثانیه
    val routeType: RouteType,
    val trafficInfo: TrafficInfo? = null,
    val speedCameras: List<SpeedCamera> = emptyList(),
    val speedBumps: List<SpeedBump> = emptyList(),
    val roadConditions: List<RoadCondition> = emptyList(),
    val confidence: Float = 1.0f // اطمینان مدل هوش مصنوعی
)

/**
 * اطلاعات ترافیک
 */
data class TrafficInfo(
    val trafficLevel: TrafficLevel,
    val estimatedDelay: Long, // ثانیه
    val alternativeRoutes: List<NavigationRoute> = emptyList()
)

enum class TrafficLevel {
    LOW, MEDIUM, HIGH, SEVERE
}

/**
 * دوربین سرعت
 */
data class SpeedCamera(
    val location: GeoPoint,
    val type: CameraType,
    val speedLimit: Int, // کیلومتر بر ساعت
    val direction: String? = null,
    val isActive: Boolean = true
)

enum class CameraType {
    FIXED,      // ثابت
    MOBILE,     // متحرک
    TRAFFIC,    // ترافیکی
    RED_LIGHT   // چراغ قرمز
}

/**
 * سرعت‌گیر
 */
data class SpeedBump(
    val location: GeoPoint,
    val severity: BumpSeverity,
    val length: Double, // متر
    val warningDistance: Int = 50 // متر
)

enum class BumpSeverity {
    LOW, MEDIUM, HIGH
}

/**
 * وضعیت جاده
 */
data class RoadCondition(
    val location: GeoPoint,
    val condition: ConditionType,
    val severity: SeverityLevel,
    val length: Double, // متر
    val description: String
)

enum class ConditionType {
    CONSTRUCTION,    // ساخت‌وساز
    POTHOLE,        // دست‌انداز
    FLOODING,       // آبگرفتگی
    ICE,            // یخ‌زدگی
    DEBRIS,         // مانع
    NARROW_ROAD,    // جاده باریک
    BRIDGE_WORK,    // کار پل
    LANDSLIDE       // رانش زمین
}

enum class SeverityLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * هشدار سرعت
 */
data class SpeedAlert(
    val currentSpeed: Int,
    val speedLimit: Int,
    val distanceToCamera: Int,
    val message: String,
    val alertType: AlertType
)

/**
 * هشدار دوربین
 */
data class CameraAlert(
    val cameraType: CameraType,
    val distance: Int,
    val message: String,
    val alertType: AlertType
)

/**
 * هشدار ترافیک
 */
data class TrafficAlert(
    val trafficLevel: TrafficLevel,
    val distance: Int,
    val estimatedDelay: Long,
    val message: String,
    val alertType: AlertType
)

/**
 * هشدار وضعیت جاده
 */
data class RoadConditionAlert(
    val condition: ConditionType,
    val severity: SeverityLevel,
    val distance: Int,
    val message: String,
    val alertType: AlertType
)

enum class AlertType {
    INFO, WARNING, DANGER, CRITICAL
}

/**
 * مسیر یادگرفته شده
 */
data class LearnedRoute(
    val id: String,
    val name: String,
    val waypoints: List<GeoPoint>,
    val averageSpeed: Double,
    val travelTime: Long,
    val distance: Double,
    val usageCount: Int,
    val rating: Float,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val lastUsed: Long
)

/**
 * نقطه علاقه (POI)
 */
data class PointOfInterest(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val category: POICategory,
    val description: String? = null,
    val rating: Float? = null,
    val isUserAdded: Boolean = false
)

enum class POICategory {
    GAS_STATION,
    RESTAURANT,
    HOTEL,
    HOSPITAL,
    PARKING,
    SHOPPING,
    TOURIST_ATTRACTION,
    BANK,
    PHARMACY,
    POLICE,
    CUSTOM
}

/**
 * پیشنهاد مسیر هوش مصنوعی
 */
data class RouteSuggestion(
    val route: NavigationRoute,
    val reasoning: String,
    val confidence: Float,
    val benefits: List<String>,
    val drawbacks: List<String> = emptyList()
)

/**
 * اطلاعات جاده
 */
data class RoadInfo(
    val name: String,
    val type: RoadType,
    val speedLimit: Int,
    val lanes: Int,
    val surface: SurfaceType,
    val isToll: Boolean = false
)

enum class RoadType {
    HIGHWAY, PRIMARY, SECONDARY, RESIDENTIAL, SERVICE
}

enum class SurfaceType {
    ASPHALT, CONCRETE, GRAVEL, DIRT, COBBLESTONE
}

/**
 * مرحله ناوبری
 */
data class NavigationStep(
    val instruction: String,
    val distance: Double, // متر
    val duration: Long, // ثانیه
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val maneuver: String,
    val polyline: String
)

/**
 * نقطه جغرافیایی
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * نوع مسیر
 */
enum class RouteType {
    DRIVING,
    WALKING,
    CYCLING,
    TRANSIT
}

/**
 * کادر محاطی برای محدوده جغرافیایی
 */
data class BoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
)
