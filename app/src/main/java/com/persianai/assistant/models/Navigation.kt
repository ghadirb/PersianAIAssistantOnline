package com.persianai.assistant.models

/**
 * مدل‌های مربوط به مسیریابی حرفه‌ای
 */

// مکان
data class Place(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val type: PlaceType,
    val rating: Float = 0f,
    val phoneNumber: String? = null,
    val website: String? = null,
    val openingHours: String? = null
)

enum class PlaceType {
    RESTAURANT,
    GAS_STATION,
    HOSPITAL,
    BANK,
    SHOPPING_MALL,
    PARK,
    SCHOOL,
    PHARMACY,
    HOTEL,
    ATTRACTION,
    UNKNOWN
}

// پیشنهاد مکان برای جستجو
data class PlaceSuggestion(
    val placeId: String,
    val description: String,
    val primaryText: String,
    val secondaryText: String
)

// مسیر ناوبری
data class NavigationRoute(
    val id: String,
    val distance: Double, // متر
    val duration: Long, // ثانیه
    val trafficLevel: Int, // 0: روان, 1: متوسط, 2: سنگین
    val points: List<LatLng>,
    val steps: List<NavigationStep>,
    val bounds: LatLngBounds,
    val overviewPolyline: String
)

// مرحله ناوبری
data class NavigationStep(
    val instruction: String,
    val distance: Double, // متر
    val duration: Long, // ثانیه
    val startLocation: LatLng,
    val endLocation: LatLng,
    val maneuver: Maneuver,
    val polyline: String
)

// نوع مانور
enum class Maneuver {
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    STRAIGHT,
    U_TURN,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    DEPART,
    ARRIVE
}

// مختصات جغرافیایی
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

// محدوده جغرافیایی
data class LatLngBounds(
    val northeast: LatLng,
    val southwest: LatLng
)

// اطلاعات ترافیک
data class TrafficInfo(
    val level: TrafficLevel,
    val description: String,
    val delayInSeconds: Long,
    val color: String
)

enum class TrafficLevel {
    LOW,      // ترافیک روان - سبز
    MODERATE, // ترافیک متوسط - زرد
    HEAVY,    // ترافیک سنگین - نارنجی
    SEVERE    // ترافیک بسیار سنگین - قرمز
}

// اطلاعات سرعت‌گیر
data class SpeedLimit(
    val speed: Int, // کیلومتر بر ساعت
    val unit: String = "km/h",
    val isStrict: Boolean = false
)

// منطقه جغرافیایی
data class GeoFence(
    val id: String,
    val name: String,
    val center: LatLng,
    val radius: Double, // متر
    val isActive: Boolean = true
)

// مورد دلخواه (Favorite)
data class FavoritePlace(
    val id: String,
    val name: String,
    val address: String,
    val location: LatLng,
    val category: FavoriteCategory,
    val icon: String,
    val addedDate: Long
)

enum class FavoriteCategory {
    HOME,
    WORK,
    SCHOOL,
    RESTAURANT,
    SHOPPING,
    ENTERTAINMENT,
    OTHER
}

// تاریخچه مسیرها
data class RouteHistory(
    val id: String,
    val startAddress: String,
    val endAddress: String,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val distance: Double,
    val duration: Long,
    val date: Long,
    val routeType: RouteType
)

enum class RouteType {
    DRIVING,
    WALKING,
    CYCLING,
    TRANSIT
}

// اطلاعات پارکینگ
data class ParkingInfo(
    val id: String,
    val name: String,
    val location: LatLng,
    val availableSpots: Int,
    val totalSpots: Int,
    val pricePerHour: Double,
    val isOpen: Boolean,
    val hasDisabledParking: Boolean
)

// اطلاعات بنزین‌زایی
data class GasStationInfo(
    val id: String,
    val name: String,
    val location: LatLng,
    val hasGasoline: Boolean,
    val hasDiesel: Boolean,
    val hasCNG: Boolean,
    val priceGasoline: Double,
    val priceDiesel: Double,
    val priceCNG: Double,
    val isOpen: Boolean,
    val queueLength: Int // 0: خالی, 1: کم, 2: متوسط, 3: زیاد
)

// اطلاعات آب و هوا برای مسیریابی
data class WeatherInfo(
    val temperature: Double,
    val condition: WeatherCondition,
    val humidity: Int,
    val windSpeed: Double,
    val visibility: Double
)

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAINY,
    SNOWY,
    FOGGY,
    STORMY
}

// رویداد مسیر
data class RouteEvent(
    val id: String,
    val type: EventType,
    val location: LatLng,
    val description: String,
    val severity: EventSeverity,
    val startTime: Long,
    val endTime: Long?,
    val affectedLanes: List<String>
)

enum class EventType {
    ACCIDENT,
    CONSTRUCTION,
    ROAD_CLOSURE,
    TRAFFIC_JAM,
    WEATHER,
    SPECIAL_EVENT
}

enum class EventSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

// تنظیمات مسیریابی
data class NavigationSettings(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val avoidUnpavedRoads: Boolean = true,
    val preferredRouteType: RouteType = RouteType.DRIVING,
    val voiceNavigation: Boolean = true,
    val autoReroute: Boolean = true,
    val showTraffic: Boolean = true,
    val speedLimitAlerts: Boolean = true,
    val speedCameraAlerts: Boolean = true
)

// آمار مسیریابی
data class NavigationStats(
    val totalDistance: Double,
    val totalTime: Long,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val fuelConsumed: Double,
    val co2Emissions: Double,
    val tollsPaid: Double,
    val stopsCount: Int
)

// تنظیمات مسیریابی
data class NavigationSettings(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val voiceEnabled: Boolean = true,
    val speedAlerts: Boolean = true
)

// اطلاعات پارکینگ
data class ParkingInfo(
    val name: String,
    val location: LatLng,
    val capacity: Int,
    val availableSpots: Int,
    val pricePerHour: Double
)

// اطلاعات پمپ بنزین
data class GasStationInfo(
    val name: String,
    val location: LatLng,
    val fuelTypes: List<String>,
    val isOpen: Boolean,
    val pricePerLiter: Double
)

// رویداد مسیر
data class RouteEvent(
    val type: String,
    val timestamp: Long,
    val location: LatLng,
    val description: String
)
