package com.persianai.assistant.navigation

import android.content.Context
import android.location.Location
import android.util.Log

/**
 * مدیریت دوربین‌های سرعت و سرعت‌گیرها
 * شامل دوربین‌های ثابت، سیار، و سرعت‌گیرهای جاده‌ای
 */
class SpeedCameraManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeedCameraManager"
    }
    
    data class SpeedCamera(
        val id: Int,
        val latitude: Double,
        val longitude: Double,
        val speedLimit: Int, // km/h
        val type: CameraType,
        val address: String
    )
    
    enum class CameraType {
        FIXED,          // دوربین ثابت
        MOBILE,         // دوربین سیار
        AVERAGE_SPEED,  // کنترل سرعت متوسط
        SPEED_BUMP      // سرعت‌گیر
    }
    
    // دیتابیس محلی دوربین‌های سرعت (نمونه برای شهرهای اصلی)
    private val speedCameras = listOf(
        // تهران
        SpeedCamera(1, 35.6892, 51.3890, 80, CameraType.FIXED, "تهران - میدان آزادی"),
        SpeedCamera(2, 35.7219, 51.3347, 70, CameraType.FIXED, "تهران - اتوبان همت"),
        SpeedCamera(3, 35.7011, 51.4185, 90, CameraType.AVERAGE_SPEED, "تهران - بزرگراه مدرس"),
        SpeedCamera(4, 35.7589, 51.4190, 60, CameraType.SPEED_BUMP, "تهران - خیابان ولیعصر"),
        SpeedCamera(5, 35.6908, 51.4213, 80, CameraType.FIXED, "تهران - اتوبان رسالت"),
        
        // مشهد
        SpeedCamera(10, 36.2974, 59.6057, 70, CameraType.FIXED, "مشهد - بلوار وکیل‌آباد"),
        SpeedCamera(11, 36.3134, 59.5986, 80, CameraType.FIXED, "مشهد - بزرگراه شهید کلانتری"),
        SpeedCamera(12, 36.2688, 59.5744, 60, CameraType.SPEED_BUMP, "مشهد - خیابان امام رضا"),
        
        // اصفهان
        SpeedCamera(20, 32.6546, 51.6680, 70, CameraType.FIXED, "اصفهان - میدان نقش جهان"),
        SpeedCamera(21, 32.6710, 51.6819, 80, CameraType.FIXED, "اصفهان - بزرگراه شهید چمران"),
        SpeedCamera(22, 32.6383, 51.6458, 60, CameraType.SPEED_BUMP, "اصفهان - خیابان چهارباغ"),
        
        // شیراز
        SpeedCamera(30, 29.5926, 52.5836, 70, CameraType.FIXED, "شیراز - بلوار چمران"),
        SpeedCamera(31, 29.6104, 52.5316, 80, CameraType.FIXED, "شیراز - بزرگراه آیت‌الله دستغیب"),
        
        // تبریز
        SpeedCamera(40, 38.0802, 46.2919, 70, CameraType.FIXED, "تبریز - بزرگراه آزادی"),
        SpeedCamera(41, 38.0735, 46.3196, 60, CameraType.SPEED_BUMP, "تبریز - خیابان ولیعصر"),
        
        // جاده‌های بین‌شهری
        SpeedCamera(50, 35.5000, 51.5000, 110, CameraType.FIXED, "جاده تهران-قم"),
        SpeedCamera(51, 35.8000, 50.9000, 120, CameraType.AVERAGE_SPEED, "آزادراه تهران-قزوین"),
        SpeedCamera(52, 36.0000, 59.0000, 110, CameraType.FIXED, "جاده تهران-مشهد"),
        SpeedCamera(53, 33.5000, 52.0000, 110, CameraType.FIXED, "جاده تهران-اصفهان"),
        SpeedCamera(54, 34.0000, 49.7000, 120, CameraType.AVERAGE_SPEED, "آزادراه تهران-ساوه")
    )
    
    /**
     * دریافت دوربین‌های نزدیک
     */
    suspend fun getNearbyCameras(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<SpeedCamera> {
        val nearbyCameras = mutableListOf<SpeedCamera>()
        
        speedCameras.forEach { camera ->
            val distance = calculateDistance(
                latitude, longitude,
                camera.latitude, camera.longitude
            )
            
            if (distance <= radiusMeters) {
                nearbyCameras.add(camera)
                Log.d(TAG, "Found camera at ${camera.address}, distance: ${distance.toInt()}m")
            }
        }
        
        return nearbyCameras.sortedBy { camera ->
            calculateDistance(latitude, longitude, camera.latitude, camera.longitude)
        }
    }
    
    /**
     * دریافت محدودیت سرعت جاده فعلی
     */
    fun getSpeedLimit(latitude: Double, longitude: Double): Int {
        // جستجوی نزدیک‌ترین دوربین برای تشخیص محدودیت سرعت جاده
        val nearestCamera = speedCameras.minByOrNull { camera ->
            calculateDistance(latitude, longitude, camera.latitude, camera.longitude)
        }
        
        return nearestCamera?.speedLimit ?: 80 // پیش‌فرض: 80 km/h
    }
    
    /**
     * محاسبه فاصله بین دو نقطه (متر)
     */
    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }
    
    /**
     * تشخیص نوع دوربین
     */
    fun getCameraTypeDescription(type: CameraType): String {
        return when (type) {
            CameraType.FIXED -> "دوربین ثابت"
            CameraType.MOBILE -> "دوربین سیار"
            CameraType.AVERAGE_SPEED -> "کنترل سرعت متوسط"
            CameraType.SPEED_BUMP -> "سرعت‌گیر"
        }
    }
    
    /**
     * افزودن دوربین جدید (برای آپدیت کردن دیتابیس)
     */
    fun addCamera(camera: SpeedCamera) {
        // در نسخه کامل، این اطلاعات باید در دیتابیس ذخیره شود
        Log.d(TAG, "Camera added: ${camera.address}")
    }
    
    /**
     * گزارش دوربین توسط کاربر
     */
    fun reportCamera(latitude: Double, longitude: Double, type: CameraType) {
        Log.d(TAG, "User reported camera at: $latitude, $longitude, type: $type")
        // در نسخه کامل، این گزارش به سرور ارسال می‌شود
    }
}
