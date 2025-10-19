package com.persianai.assistant.navigation.detectors

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.persianai.assistant.navigation.models.*
import kotlinx.coroutines.*
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.*

/**
 * سیستم تشخیص سرعت‌گیر و دوربین کنترل سرعت
 * با استفاده از GPS و داده‌های ذخیره شده
 */
class SpeedCameraDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeedCameraDetector"
        private const val CAMERAS_FILE = "speed_cameras.json"
        private const val SPEED_BUMPS_FILE = "speed_bumps.json"
        private const val WARNING_DISTANCE = 200 // متر
        private const val CRITICAL_DISTANCE = 50 // متر
        private const val UPDATE_DISTANCE = 10 // متر برای به‌روزرسانی موقعیت
    }
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val camerasFile = File(context.filesDir, CAMERAS_FILE)
    private val speedBumpsFile = File(context.filesDir, SPEED_BUMPS_FILE)
    
    private var speedCameras: MutableList<SpeedCamera> = mutableListOf()
    private var speedBumps: MutableList<SpeedBump> = mutableListOf()
    private var currentRoute: NavigationRoute? = null
    private var isDetecting = false
    private var lastLocation: Location? = null
    
    init {
        loadData()
        loadDefaultData()
    }
    
    /**
     * شروع تشخیص برای مسیر مشخص
     */
    fun startDetection(route: NavigationRoute) {
        currentRoute = route
        isDetecting = true
        lastLocation = null
        
        // فیلتر کردن دوربین‌ها و سرعت‌گیرهای نزدیک به مسیر
        filterNearbyDevices(route)
        
        Log.d(TAG, "Started detection for route: ${route.id}")
    }
    
    /**
     * توقف تشخیص
     */
    fun stopDetection() {
        isDetecting = false
        currentRoute = null
        lastLocation = null
        Log.d(TAG, "Detection stopped")
    }
    
    /**
     * بررسی هشدار سرعت
     */
    suspend fun checkSpeedAlert(location: Location): SpeedAlert? = withContext(Dispatchers.IO) {
        if (!isDetecting) return@withContext null
        
        try {
            // جستجو برای دوربین‌های نزدیک
            val nearbyCamera = findNearbyCamera(location)
            if (nearbyCamera != null) {
                val distance = location.distanceTo(
                    Location("").apply {
                        latitude = nearbyCamera.location.latitude
                        longitude = nearbyCamera.location.longitude
                    }
                ).toInt()
                
                val currentSpeed = (location.speed * 3.6).toInt() // تبدیل به km/h
                
                return@withContext SpeedAlert(
                    currentSpeed = currentSpeed,
                    speedLimit = nearbyCamera.speedLimit,
                    distanceToCamera = distance,
                    message = generateSpeedMessage(currentSpeed, nearbyCamera.speedLimit, distance),
                    alertType = getAlertType(distance, currentSpeed, nearbyCamera.speedLimit)
                )
            }
            
            // جستجو برای سرعت‌گیرهای نزدیک
            val nearbyBump = findNearbySpeedBump(location)
            if (nearbyBump != null) {
                val distance = location.distanceTo(
                    Location("").apply {
                        latitude = nearbyBump.location.latitude
                        longitude = nearbyBump.location.longitude
                    }
                ).toInt()
                
                val currentSpeed = (location.speed * 3.6).toInt()
                val recommendedSpeed = getRecommendedSpeed(nearbyBump.severity)
                
                return@withContext SpeedAlert(
                    currentSpeed = currentSpeed,
                    speedLimit = recommendedSpeed,
                    distanceToCamera = distance,
                    message = generateBumpMessage(currentSpeed, recommendedSpeed, distance, nearbyBump.severity),
                    alertType = getAlertType(distance, currentSpeed, recommendedSpeed)
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking speed alert", e)
        }
        
        return@withContext null
    }
    
    /**
     * بررسی هشدار دوربین
     */
    suspend fun checkCameraAlert(location: Location): CameraAlert? = withContext(Dispatchers.IO) {
        if (!isDetecting) return@withContext null
        
        try {
            val nearbyCamera = findNearbyCamera(location)
            if (nearbyCamera != null) {
                val distance = location.distanceTo(
                    Location("").apply {
                        latitude = nearbyCamera.location.latitude
                        longitude = nearbyCamera.location.longitude
                    }
                ).toInt()
                
                return@withContext CameraAlert(
                    cameraType = nearbyCamera.type,
                    distance = distance,
                    message = generateCameraMessage(nearbyCamera, distance),
                    alertType = getCameraAlertType(distance, nearbyCamera.type)
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera alert", e)
        }
        
        return@withContext null
    }
    
    /**
     * پیدا کردن دوربین نزدیک
     */
    private fun findNearbyCamera(location: Location): SpeedCamera? {
        val userPoint = GeoPoint(location.latitude, location.longitude)
        
        return speedCameras.firstOrNull { camera ->
            if (!camera.isActive) return@firstOrNull false
            
            val distance = userPoint.distanceToAsDouble(camera.location)
            distance <= WARNING_DISTANCE
        }
    }
    
    /**
     * پیدا کردن سرعت‌گیر نزدیک
     */
    private fun findNearbySpeedBump(location: Location): SpeedBump? {
        val userPoint = GeoPoint(location.latitude, location.longitude)
        
        return speedBumps.firstOrNull { bump ->
            val distance = userPoint.distanceToAsDouble(bump.location)
            distance <= bump.warningDistance
        }
    }
    
    /**
     * بررسی موقعیت برای هشدار سرعت‌گیر و دوربین
     */
    fun checkLocation(location: GeoPoint) {
        val androidLocation = Location("").apply {
            latitude = location.latitude
            longitude = location.longitude
        }
        
        scope.launch {
            try {
                checkSpeedAlert(androidLocation)
                checkCameraAlert(androidLocation)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking location", e)
            }
        }
    }
    
    /**
     * تولید پیام هشدار سرعت
     */
    private fun generateSpeedMessage(currentSpeed: Int, speedLimit: Int, distance: Int): String {
        return when {
            distance <= CRITICAL_DISTANCE -> {
                if (currentSpeed > speedLimit) {
                    "⚠️ خطر! سرعت شما $currentSpeed کیلومتر است. محدودیت سرعت $speedLimit کیلومتر است. دوربین در $distance متری!"
                } else {
                    "📹 دوربین کنترل سرعت در $distance متری. سرعت مجاز: $speedLimit کیلومتر"
                }
            }
            distance <= WARNING_DISTANCE -> {
                "📍 دوربین کنترل سرعت در $distance متری. سرعت مجاز: $speedLimit کیلومتر"
            }
            else -> {
                "🛣️ دوربین کنترل سرعت در مسیر شما. سرعت مجاز: $speedLimit کیلومتر"
            }
        }
    }
    
    /**
     * تولید پیام هشدار سرعت‌گیر
     */
    private fun generateBumpMessage(currentSpeed: Int, recommendedSpeed: Int, distance: Int, severity: BumpSeverity): String {
        val severityText = when (severity) {
            BumpSeverity.LOW -> "سبک"
            BumpSeverity.MEDIUM -> "متوسط"
            BumpSeverity.HIGH -> "خطرناک"
        }
        
        return when {
            distance <= CRITICAL_DISTANCE -> {
                if (currentSpeed > recommendedSpeed) {
                    "⚠️ خطر! سرعت‌گیر $severityText در $distance متری! سرعت خود را کم کنید."
                } else {
                    "🔻 سرعت‌گیر $severityText در $distance متری. سرعت پیشنهادی: $recommendedSpeed کیلومتر"
                }
            }
            distance <= WARNING_DISTANCE -> {
                "📍 سرعت‌گیر $severityText در $distance متری"
            }
            else -> {
                "🛣️ سرعت‌گیر در مسیر شما"
            }
        }
    }
    
    /**
     * تولید پیام هشدار دوربین
     */
    private fun generateCameraMessage(camera: SpeedCamera, distance: Int): String {
        val cameraTypeText = when (camera.type) {
            CameraType.FIXED -> "ثابت"
            CameraType.MOBILE -> "متحرک"
            CameraType.TRAFFIC -> "ترافیکی"
            CameraType.RED_LIGHT -> "چراغ قرمز"
        }
        
        return when {
            distance <= CRITICAL_DISTANCE -> {
                "📹 دوربین $cameraTypeText در $distance متری! سرعت مجاز: ${camera.speedLimit} کیلومتر"
            }
            distance <= WARNING_DISTANCE -> {
                "📍 دوربین $cameraTypeText در $distance متری"
            }
            else -> {
                "🛣️ دوربین $cameraTypeText در مسیر شما"
            }
        }
    }
    
    /**
     * دریافت نوع هشدار
     */
    private fun getAlertType(distance: Int, currentSpeed: Int, speedLimit: Int): AlertType {
        return when {
            distance <= CRITICAL_DISTANCE && currentSpeed > speedLimit -> AlertType.CRITICAL
            distance <= CRITICAL_DISTANCE -> AlertType.WARNING
            distance <= WARNING_DISTANCE -> AlertType.INFO
            else -> AlertType.INFO
        }
    }
    
    /**
     * دریافت نوع هشدار دوربین
     */
    private fun getCameraAlertType(distance: Int, cameraType: CameraType): AlertType {
        return when {
            distance <= CRITICAL_DISTANCE -> AlertType.WARNING
            distance <= WARNING_DISTANCE -> AlertType.INFO
            else -> AlertType.INFO
        }
    }
    
    /**
     * دریافت سرعت پیشنهادی برای سرعت‌گیر
     */
    private fun getRecommendedSpeed(severity: BumpSeverity): Int {
        return when (severity) {
            BumpSeverity.LOW -> 30
            BumpSeverity.MEDIUM -> 20
            BumpSeverity.HIGH -> 10
        }
    }
    
    /**
     * فیلتر کردن دستگاه‌های نزدیک به مسیر
     */
    private fun filterNearbyDevices(route: NavigationRoute) {
        val routeBoundingBox = calculateBoundingBox(route.waypoints)
        
        // فیلتر دوربین‌ها
        speedCameras = speedCameras.filter { camera ->
            isPointInBoundingBox(camera.location, routeBoundingBox)
        }.toMutableList()
        
        // فیلتر سرعت‌گیرها
        speedBumps = speedBumps.filter { bump ->
            isPointInBoundingBox(bump.location, routeBoundingBox)
        }.toMutableList()
        
        Log.d(TAG, "Filtered to ${speedCameras.size} cameras and ${speedBumps.size} speed bumps")
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
        val margin = 0.01 // حدود 1 کیلومتر
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
     * بارگذاری داده‌ها از فایل
     */
    private fun loadData() {
        try {
            // بارگذاری دوربین‌ها
            if (camerasFile.exists()) {
                val json = camerasFile.readText()
                val type = object : TypeToken<MutableList<SpeedCamera>>() {}.type
                speedCameras = gson.fromJson(json, type) ?: mutableListOf()
            }
            
            // بارگذاری سرعت‌گیرها
            if (speedBumpsFile.exists()) {
                val json = speedBumpsFile.readText()
                val type = object : TypeToken<MutableList<SpeedBump>>() {}.type
                speedBumps = gson.fromJson(json, type) ?: mutableListOf()
            }
            
            Log.d(TAG, "Loaded ${speedCameras.size} cameras and ${speedBumps.size} speed bumps")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading data", e)
            speedCameras = mutableListOf()
            speedBumps = mutableListOf()
        }
    }
    
    /**
     * بارگذاری داده‌های پیش‌فرض (نمونه)
     */
    private fun loadDefaultData() {
        if (speedCameras.isEmpty()) {
            // اضافه کردن دوربین‌های نمونه در تهران
            speedCameras.addAll(listOf(
                SpeedCamera(
                    location = GeoPoint(35.6961, 51.4231),
                    type = CameraType.FIXED,
                    speedLimit = 50,
                    isActive = true
                ),
                SpeedCamera(
                    location = GeoPoint(35.6892, 51.3890),
                    type = CameraType.MOBILE,
                    speedLimit = 60,
                    isActive = true
                ),
                SpeedCamera(
                    location = GeoPoint(35.7158, 51.4065),
                    type = CameraType.TRAFFIC,
                    speedLimit = 40,
                    isActive = true
                )
            ))
        }
        
        if (speedBumps.isEmpty()) {
            // اضافه کردن سرعت‌گیرهای نمونه
            speedBumps.addAll(listOf(
                SpeedBump(
                    location = GeoPoint(35.6965, 51.4235),
                    severity = BumpSeverity.MEDIUM,
                    length = 3.0,
                    warningDistance = 50
                ),
                SpeedBump(
                    location = GeoPoint(35.6895, 51.3895),
                    severity = BumpSeverity.LOW,
                    length = 2.0,
                    warningDistance = 30
                ),
                SpeedBump(
                    location = GeoPoint(35.7160, 51.4060),
                    severity = BumpSeverity.HIGH,
                    length = 5.0,
                    warningDistance = 70
                )
            ))
        }
        
        saveData()
    }
    
    /**
     * ذخیره داده‌ها در فایل
     */
    private fun saveData() {
        try {
            // ذخیره دوربین‌ها
            val camerasJson = gson.toJson(speedCameras)
            camerasFile.writeText(camerasJson)
            
            // ذخیره سرعت‌گیرها
            val bumpsJson = gson.toJson(speedBumps)
            speedBumpsFile.writeText(bumpsJson)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving data", e)
        }
    }
    
    /**
     * اضافه کردن دوربین جدید
     */
    fun addCamera(camera: SpeedCamera) {
        speedCameras.add(camera)
        saveData()
        Log.d(TAG, "Added new camera: ${camera.type}")
    }
    
    /**
     * اضافه کردن سرعت‌گیر جدید
     */
    fun addSpeedBump(bump: SpeedBump) {
        speedBumps.add(bump)
        saveData()
        Log.d(TAG, "Added new speed bump")
    }
    
    /**
     * دریافت تمام دوربین‌ها
     */
    fun getAllCameras(): List<SpeedCamera> {
        return speedCameras.toList()
    }
    
    /**
     * دریافت تمام سرعت‌گیرها
     */
    fun getAllSpeedBumps(): List<SpeedBump> {
        return speedBumps.toList()
    }
    
    /**
     * حذف دوربین
     */
    fun removeCamera(cameraId: String) {
        speedCameras.removeAll { it.location.toString().contains(cameraId) }
        saveData()
    }
    
    /**
     * حذف سرعت‌گیر
     */
    fun removeSpeedBump(bumpId: String) {
        speedBumps.removeAll { it.location.toString().contains(bumpId) }
        saveData()
    }
    
    /**
     * پاک کردن تمام داده‌ها
     */
    fun clearAllData() {
        speedCameras.clear()
        speedBumps.clear()
        saveData()
        Log.d(TAG, "All data cleared")
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
