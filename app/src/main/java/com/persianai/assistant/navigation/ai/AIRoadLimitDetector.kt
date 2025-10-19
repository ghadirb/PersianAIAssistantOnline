package com.persianai.assistant.navigation.ai

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.persianai.assistant.ai.AIModelManager
import com.persianai.assistant.navigation.models.GeoPoint
import com.persianai.assistant.navigation.voice.PersianVoiceAlertSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * سیستم هوشمند تشخیص محدودیت سرعت جاده با AI
 */
class AIRoadLimitDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "AIRoadLimitDetector"
        
        // استانداردهای محدودیت سرعت ایران
        object IranSpeedLimits {
            const val HIGHWAY = 120 // بزرگراه
            const val FREEWAY = 110 // آزادراه
            const val MAIN_ROAD_CITY = 60 // خیابان اصلی شهر
            const val SIDE_STREET = 40 // کوچه
            const val SCHOOL_ZONE = 25 // محدوده مدرسه
            const val RESIDENTIAL = 50 // مسکونی
            const val RURAL_ROAD = 95 // جاده روستایی
            const val MOUNTAIN_ROAD = 70 // جاده کوهستانی
        }
    }
    
    private val aiModel = AIModelManager(context)
    private val voiceAlert = PersianVoiceAlertSystem(context)
    private val geocoder = Geocoder(context, Locale("fa", "IR"))
    
    private var lastDetectedLimit: Int = 0
    private var lastAlertTime: Long = 0
    private val alertCooldown = 30000L // 30 ثانیه
    
    /**
     * تشخیص هوشمند محدودیت سرعت با AI
     */
    suspend fun detectSpeedLimit(location: GeoPoint, currentSpeed: Double): SpeedLimitResult = 
        withContext(Dispatchers.IO) {
        try {
            val address = getAddressInfo(location)
            val roadType = analyzeRoadTypeWithAI(location, address, currentSpeed)
            val speedLimit = calculateSpeedLimit(roadType, address)
            
            val isOverSpeed = currentSpeed > speedLimit
            val speedDifference = currentSpeed - speedLimit
            
            if (shouldAlert(speedLimit, isOverSpeed)) {
                alertSpeedLimit(speedLimit, isOverSpeed, speedDifference, roadType)
            }
            
            Log.d(TAG, "Type: $roadType, Limit: $speedLimit, Speed: $currentSpeed")
            
            SpeedLimitResult(
                speedLimit = speedLimit,
                roadType = roadType,
                isOverSpeed = isOverSpeed,
                speedDifference = speedDifference,
                confidence = 0.85,
                source = "AI Model"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting speed limit", e)
            SpeedLimitResult(60, RoadType.MAIN_ROAD, false, 0.0, 0.3, "Default")
        }
    }
    
    /**
     * تحلیل نوع جاده با AI
     */
    private suspend fun analyzeRoadTypeWithAI(
        location: GeoPoint, 
        address: Address?, 
        currentSpeed: Double
    ): RoadType = withContext(Dispatchers.IO) {
        try {
            val prompt = buildRoadAnalysisPrompt(location, address, currentSpeed)
            
            if (aiModel.hasApiKey()) {
                val aiResponse = aiModel.generateText(prompt)
                parseRoadTypeFromAI(aiResponse)
            } else {
                analyzeRoadTypeRuleBased(address, currentSpeed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI analysis failed, using rule-based", e)
            analyzeRoadTypeRuleBased(address, currentSpeed)
        }
    }
    
    /**
     * ساخت prompt برای AI
     */
    private fun buildRoadAnalysisPrompt(location: GeoPoint, address: Address?, speed: Double): String {
        val cityName = address?.locality ?: "نامشخص"
        val streetName = address?.thoroughfare ?: "نامشخص"
        val featureName = address?.featureName ?: ""
        
        return """
تحلیل نوع جاده و تعیین محدودیت سرعت در ایران:

موقعیت: ${location.latitude}, ${location.longitude}
شهر: $cityName
خیابان: $streetName
ویژگی: $featureName
سرعت فعلی: ${String.format("%.0f", speed)} km/h

انواع جاده و محدودیت سرعت استاندارد ایران:
- HIGHWAY (بزرگراه - اتوبان): 120 km/h
- FREEWAY (آزادراه): 110 km/h
- MAIN_ROAD (خیابان اصلی): 60 km/h
- SIDE_STREET (کوچه فرعی): 40 km/h
- SCHOOL_ZONE (محدوده مدرسه): 25 km/h
- RESIDENTIAL (مسکونی): 50 km/h
- RURAL_ROAD (جاده روستایی): 95 km/h
- MOUNTAIN_ROAD (جاده کوهستانی): 70 km/h

فقط نام نوع جاده را بنویس.
        """.trimIndent()
    }
    
    /**
     * تجزیه پاسخ AI
     */
    private fun parseRoadTypeFromAI(aiResponse: String): RoadType {
        val response = aiResponse.uppercase().trim()
        return when {
            response.contains("HIGHWAY") -> RoadType.HIGHWAY
            response.contains("FREEWAY") -> RoadType.FREEWAY
            response.contains("MAIN_ROAD") -> RoadType.MAIN_ROAD
            response.contains("SIDE_STREET") -> RoadType.SIDE_STREET
            response.contains("SCHOOL") -> RoadType.SCHOOL_ZONE
            response.contains("RESIDENTIAL") -> RoadType.RESIDENTIAL
            response.contains("RURAL") -> RoadType.RURAL_ROAD
            response.contains("MOUNTAIN") -> RoadType.MOUNTAIN_ROAD
            else -> RoadType.MAIN_ROAD
        }
    }
    
    /**
     * تحلیل rule-based بدون AI
     */
    private fun analyzeRoadTypeRuleBased(address: Address?, currentSpeed: Double): RoadType {
        val streetName = address?.thoroughfare?.lowercase() ?: ""
        val featureName = address?.featureName?.lowercase() ?: ""
        val cityName = address?.locality?.lowercase() ?: ""
        
        return when {
            streetName.contains("بزرگراه") || streetName.contains("اتوبان") ||
            currentSpeed > 100 -> RoadType.HIGHWAY
            
            streetName.contains("آزادراه") -> RoadType.FREEWAY
            
            featureName.contains("مدرسه") || streetName.contains("مدرسه") -> RoadType.SCHOOL_ZONE
            
            streetName.contains("کوچه") || streetName.contains("بن‌بست") -> RoadType.SIDE_STREET
            
            featureName.contains("کوه") -> RoadType.MOUNTAIN_ROAD
            
            cityName.isEmpty() -> RoadType.RURAL_ROAD
            
            streetName.contains("خیابان") || streetName.contains("بلوار") -> RoadType.MAIN_ROAD
            
            else -> RoadType.RESIDENTIAL
        }
    }
    
    /**
     * محاسبه محدودیت سرعت
     */
    private fun calculateSpeedLimit(roadType: RoadType, address: Address?): Int {
        return when (roadType) {
            RoadType.HIGHWAY -> IranSpeedLimits.HIGHWAY
            RoadType.FREEWAY -> IranSpeedLimits.FREEWAY
            RoadType.MAIN_ROAD -> IranSpeedLimits.MAIN_ROAD_CITY
            RoadType.SIDE_STREET -> IranSpeedLimits.SIDE_STREET
            RoadType.SCHOOL_ZONE -> IranSpeedLimits.SCHOOL_ZONE
            RoadType.RESIDENTIAL -> IranSpeedLimits.RESIDENTIAL
            RoadType.RURAL_ROAD -> IranSpeedLimits.RURAL_ROAD
            RoadType.MOUNTAIN_ROAD -> IranSpeedLimits.MOUNTAIN_ROAD
        }
    }
    
    /**
     * دریافت اطلاعات آدرس
     */
    private fun getAddressInfo(location: GeoPoint): Address? {
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed", e)
            null
        }
    }
    
    /**
     * بررسی نیاز به هشدار
     */
    private fun shouldAlert(newLimit: Int, isOverSpeed: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val limitChanged = newLimit != lastDetectedLimit
        val cooldownPassed = (now - lastAlertTime) > alertCooldown
        
        return (limitChanged || isOverSpeed) && cooldownPassed
    }
    
    /**
     * صدور هشدار صوتی
     */
    private fun alertSpeedLimit(limit: Int, isOverSpeed: Boolean, diff: Double, roadType: RoadType) {
        lastDetectedLimit = limit
        lastAlertTime = System.currentTimeMillis()
        
        val roadTypeFa = getRoadTypePersian(roadType)
        
        if (isOverSpeed) {
            val message = "توجه! سرعت شما ${String.format("%.0f", diff)} کیلومتر بیشتر از حد مجاز است. " +
                         "محدودیت سرعت $roadTypeFa: $limit کیلومتر"
            voiceAlert.speak(message)
        } else {
            val message = "محدودیت سرعت $roadTypeFa: $limit کیلومتر بر ساعت"
            voiceAlert.speak(message)
        }
        
        Log.d(TAG, "Alert: $roadTypeFa - $limit km/h - OverSpeed: $isOverSpeed")
    }
    
    /**
     * ترجمه نوع جاده به فارسی
     */
    private fun getRoadTypePersian(roadType: RoadType): String {
        return when (roadType) {
            RoadType.HIGHWAY -> "بزرگراه"
            RoadType.FREEWAY -> "آزادراه"
            RoadType.MAIN_ROAD -> "خیابان اصلی"
            RoadType.SIDE_STREET -> "کوچه"
            RoadType.SCHOOL_ZONE -> "محدوده مدرسه"
            RoadType.RESIDENTIAL -> "منطقه مسکونی"
            RoadType.RURAL_ROAD -> "جاده روستایی"
            RoadType.MOUNTAIN_ROAD -> "جاده کوهستانی"
        }
    }
    
    /**
     * فعال/غیرفعال کردن
     */
    private var isEnabled = true
    
    fun enable() {
        isEnabled = true
        Log.d(TAG, "AI Road Limit Detector enabled")
    }
    
    fun disable() {
        isEnabled = false
        Log.d(TAG, "AI Road Limit Detector disabled")
    }
    
    fun cleanup() {
        voiceAlert.cleanup()
    }
}

/**
 * انواع جاده
 */
enum class RoadType {
    HIGHWAY,        // بزرگراه
    FREEWAY,        // آزادراه
    MAIN_ROAD,      // خیابان اصلی
    SIDE_STREET,    // کوچه
    SCHOOL_ZONE,    // محدوده مدرسه
    RESIDENTIAL,    // مسکونی
    RURAL_ROAD,     // جاده روستایی
    MOUNTAIN_ROAD   // جاده کوهستانی
}

/**
 * نتیجه تشخیص محدودیت سرعت
 */
data class SpeedLimitResult(
    val speedLimit: Int,
    val roadType: RoadType,
    val isOverSpeed: Boolean,
    val speedDifference: Double,
    val confidence: Double,
    val source: String
)
