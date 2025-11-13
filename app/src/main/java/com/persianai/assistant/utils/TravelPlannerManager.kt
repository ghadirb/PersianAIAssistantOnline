package com.persianai.assistant.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import java.util.*

/**
 * مدیر هوشمند برنامه‌ریزی سفر
 */
class TravelPlannerManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("travel_planner", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TRIPS_KEY = "trips"
        private const val DESTINATIONS_KEY = "destinations"
    }
    
    @Serializable
    data class TravelTrip(
        val id: String,
        val title: String,
        val destination: String,
        val startDate: Long,
        val endDate: Long,
        val budget: Double,
        val transportType: TransportType,
        val accommodationType: AccommodationType,
        val activities: List<String>,
        val notes: String = "",
        val isCompleted: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    @Serializable
    data class Destination(
        val id: String,
        val name: String,
        val country: String,
        val description: String,
        val attractions: List<String>,
        val bestTimeToVisit: String,
        val averageCost: String,
        val imageUrl: String = "",
        val rating: Float = 0.0f
    )
    
    @Serializable
    enum class TransportType {
        CAR, // ماشین شخصی
        BUS, // اتوبوس
        TRAIN, // قطار
        PLANE, // هواپیما
        SHIP // کشتی
    }
    
    @Serializable
    enum class AccommodationType {
        HOTEL, // هتل
        APARTMENT, // آپارتمان
        HOSTEL, // مهمانپذیر
        COTTAGE, // ویلای جنگلی
        CAMPING // کمپینگ
    }
    
    /**
     * افزودن سفر جدید
     */
    fun addTrip(trip: TravelTrip) {
        try {
            val trips = getTrips().toMutableList()
            trips.add(trip)
            saveTrips(trips)
            
            Log.i("TravelPlannerManager", "✅ سفر جدید اضافه شد: ${trip.title}")
            
            // تنظیم یادآور برای سفر
            scheduleTripReminders(trip)
            
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در افزودن سفر: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام سفرها
     */
    fun getTrips(): List<TravelTrip> {
        return try {
            val tripsJson = prefs.getString(TRIPS_KEY, null)
            if (tripsJson != null) {
                json.decodeFromString<List<TravelTrip>>(tripsJson)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در دریافت سفرها: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * دریافت سفرهای آینده
     */
    fun getUpcomingTrips(): List<TravelTrip> {
        val now = System.currentTimeMillis()
        return getTrips().filter { !it.isCompleted && it.startDate > now }
            .sortedBy { it.startDate }
    }
    
    /**
     * دریافت سفرهای فعال (در حال انجام)
     */
    fun getActiveTrips(): List<TravelTrip> {
        val now = System.currentTimeMillis()
        return getTrips().filter { 
            !it.isCompleted && it.startDate <= now && it.endDate >= now 
        }
    }
    
    /**
     * دریافت سفرهای گذشته
     */
    fun getCompletedTrips(): List<TravelTrip> {
        return getTrips().filter { it.isCompleted }
            .sortedByDescending { it.startDate }
    }
    
    /**
     * ویرایش سفر
     */
    fun updateTrip(trip: TravelTrip) {
        try {
            val trips = getTrips().toMutableList()
            val index = trips.indexOfFirst { it.id == trip.id }
            if (index != -1) {
                trips[index] = trip
                saveTrips(trips)
                Log.i("TravelPlannerManager", "✅ سفر ویرایش شد: ${trip.title}")
            }
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در ویرایش سفر: ${e.message}")
        }
    }
    
    /**
     * حذف سفر
     */
    fun deleteTrip(tripId: String) {
        try {
            val trips = getTrips().toMutableList()
            trips.removeAll { it.id == tripId }
            saveTrips(trips)
            Log.i("TravelPlannerManager", "✅ سفر حذف شد: $tripId")
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در حذف سفر: ${e.message}")
        }
    }
    
    /**
     * تکمیل سفر
     */
    fun completeTrip(tripId: String) {
        try {
            val trips = getTrips().toMutableList()
            val index = trips.indexOfFirst { it.id == tripId }
            if (index != -1) {
                trips[index] = trips[index].copy(isCompleted = true)
                saveTrips(trips)
                Log.i("TravelPlannerManager", "✅ سفر تکمیل شد: $tripId")
            }
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در تکمیل سفر: ${e.message}")
        }
    }
    
    /**
     * افزودن مقصد جدید
     */
    fun addDestination(destination: Destination) {
        try {
            val destinations = getDestinations().toMutableList()
            destinations.add(destination)
            saveDestinations(destinations)
            Log.i("TravelPlannerManager", "✅ مقصد جدید اضافه شد: ${destination.name}")
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در افزودن مقصد: ${e.message}")
        }
    }
    
    /**
     * دریافت تمام مقاصد
     */
    fun getDestinations(): List<Destination> {
        return try {
            val destinationsJson = prefs.getString(DESTINATIONS_KEY, null)
            if (destinationsJson != null) {
                json.decodeFromString<List<Destination>>(destinationsJson)
            } else {
                createDefaultDestinations()
            }
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در دریافت مقاصد: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * جستجوی مقاصد
     */
    fun searchDestinations(query: String): List<Destination> {
        val destinations = getDestinations()
        return destinations.filter { destination ->
            destination.name.contains(query, ignoreCase = true) ||
            destination.country.contains(query, ignoreCase = true) ||
            destination.description.contains(query, ignoreCase = true)
        }
    }
    
    /**
     * دریافت توصیه‌های سفر
     */
    fun getTravelRecommendations(trip: TravelTrip): List<String> {
        val recommendations = mutableListOf<String>()
        
        // توصیه بر اساس نوع حمل و نقل
        when (trip.transportType) {
            TransportType.CAR -> {
                recommendations.add("بیمه ماشین خود را بررسی کنید")
                recommendations.add("وضعیت فنی خودرو را کنترل نمایید")
                recommendations.add("مسیرهای جایگزین را در نقشه ذخیره کنید")
            }
            TransportType.BUS -> {
                recommendations.add("بلیط را زودتر رزرو کنید")
                recommendations.add("از وسایل شخصی برای راحتی استفاده کنید")
            }
            TransportType.TRAIN -> {
                recommendations.add("ایستگاه‌های مسیر را بررسی کنید")
                recommendations.add("وسایل ضروری در سفر قطار را آماده کنید")
            }
            TransportType.PLANE -> {
                recommendations.add("قوانین بار هوایی را بررسی کنید")
                recommendations.add("زودتر در فرودگاه حاضر شوید")
                recommendations.add("مدارک لازم را آماده کنید")
            }
            TransportType.SHIP -> {
                recommendations.add("وضعیت آب و هوا را بررسی کنید")
                recommendations.add("دارای دریازدگی دارو همراه داشته باشید")
            }
        }
        
        // توصیه بر اساس نوع اقامت
        when (trip.accommodationType) {
            AccommodationType.HOTEL -> {
                recommendations.add("امکانات هتل را از قبل بررسی کنید")
                recommendations.add("ساعت تحویل اتاق را هماهنگ کنید")
            }
            AccommodationType.APARTMENT -> {
                recommendations.add("وسایل آشپزخانه ضروری را بررسی کنید")
                recommendations.add("قوانین آپارتمان را مطالعه کنید")
            }
            AccommodationType.COTTAGE -> {
                recommendations.add("وسایل گرمایشی و سرمایشی را بررسی کنید")
                recommendations.add("موقعیت دقیق ویلای جنگلی را بررسی کنید")
            }
            AccommodationType.CAMPING -> {
                recommendations.add("وسایل کمپینگ را کامل بررسی کنید")
                recommendations.add("موقعیت آب و هوایی را بررسی کنید")
            }
            else -> {}
        }
        
        // توصیه‌های عمومی
        recommendations.add("مدارک شناسایی و پاسپورت را بررسی کنید")
        recommendations.add("داروهای ضروری را همراه داشته باشید")
        recommendations.add("شامل شارژر موبایل و پاور بانک")
        recommendations.add("نقشه آفلاین مقصد را دانلود کنید")
        
        return recommendations
    }
    
    /**
     * محاسبه هزینه سفر
     */
    fun calculateTripCost(trip: TravelTrip): TripCostBreakdown {
        val days = ((trip.endDate - trip.startDate) / (1000 * 60 * 60 * 24)).toInt() + 1
        
        val accommodationCost = when (trip.accommodationType) {
            AccommodationType.HOTEL -> trip.budget * 0.4
            AccommodationType.APARTMENT -> trip.budget * 0.3
            AccommodationType.HOSTEL -> trip.budget * 0.2
            AccommodationType.COTTAGE -> trip.budget * 0.35
            AccommodationType.CAMPING -> trip.budget * 0.1
        }
        
        val transportCost = when (trip.transportType) {
            TransportType.CAR -> trip.budget * 0.2
            TransportType.BUS -> trip.budget * 0.15
            TransportType.TRAIN -> trip.budget * 0.25
            TransportType.PLANE -> trip.budget * 0.3
            TransportType.SHIP -> trip.budget * 0.35
        }
        
        val foodCost = trip.budget * 0.25
        val activitiesCost = trip.budget * 0.15
        val emergencyCost = trip.budget * 0.05
        
        return TripCostBreakdown(
            totalBudget = trip.budget,
            accommodation = accommodationCost,
            transport = transportCost,
            food = foodCost,
            activities = activitiesCost,
            emergency = emergencyCost,
            dailyAverage = trip.budget / days
        )
    }
    
    @Serializable
    data class TripCostBreakdown(
        val totalBudget: Double,
        val accommodation: Double,
        val transport: Double,
        val food: Double,
        val activities: Double,
        val emergency: Double,
        val dailyAverage: Double
    )
    
    /**
     * تنظیم یادآورهای سفر
     */
    private fun scheduleTripReminders(trip: TravelTrip) {
        try {
            // یادآور ۱ هفته قبل از سفر
            val oneWeekBefore = trip.startDate - (7 * 24 * 60 * 60 * 1000)
            scheduleReminder(oneWeekBefore, "یادآور سفر", "سفر شما به ${trip.destination} در کمتر از یک هفته شروع می‌شود")
            
            // یادآور ۱ روز قبل از سفر
            val oneDayBefore = trip.startDate - (24 * 60 * 60 * 1000)
            scheduleReminder(oneDayBefore, "آماده باش سفر", "فردا سفر به ${trip.destination} شروع می‌شود")
            
            // یادآور روز شروع سفر
            scheduleReminder(trip.startDate, "شروع سفر", "سفر به ${trip.destination} امروز شروع می‌شود")
            
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در تنظیم یادآورهای سفر: ${e.message}")
        }
    }
    
    /**
     * تنظیم یادآور
     */
    private fun scheduleReminder(time: Long, title: String, message: String) {
        try {
            // استفاده از NotificationHelper برای تنظیم یادآور
            val notificationHelper = NotificationHelper(context)
            scope.launch {
                notificationHelper.scheduleNotification(
                    title = title,
                    message = message,
                    time = time,
                    channelId = "travel_reminders"
                )
            }
            
            Log.i("TravelPlannerManager", "✅ یادآور تنظیم شد: $title")
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در تنظیم یادآور: ${e.message}")
        }
    }
    
    /**
     * ایجاد مقاصد پیش‌فرض
     */
    private fun createDefaultDestinations(): List<Destination> {
        val defaultDestinations = listOf(
            Destination(
                id = "tehran",
                name = "تهران",
                country = "ایران",
                description = "پایتخت ایران با جاذبه‌های تاریخی و مدرن",
                attractions = listOf("برج میلاد", "کاخ گلستان", "بازار بزرگ تهران", "موزه ملی ایران"),
                bestTimeToVisit = "بهار و پاییز",
                averageCost = "متوسط",
                rating = 4.2f
            ),
            Destination(
                id = "isfahan",
                name = "اصفهان",
                country = "ایران",
                description = "نصف جهان با معماری اسلامی بی‌نظیر",
                attractions = listOf("میدان نقش جهان", "سی و سه پل", "کاخ عالی قاپو", "مسجد شیخ لطف‌الله"),
                bestTimeToVisit = "بهار و پاییز",
                averageCost = "متوسط",
                rating = 4.5f
            ),
            Destination(
                id = "shiraz",
                name = "شیراز",
                country = "ایران",
                description = "شهر شعر و ادب و باغ‌های زیبا",
                attractions = listOf("تخت جمشید", "باغ ارم", "حافظیه", "سعدیه"),
                bestTimeToVisit = "بهار",
                averageCost = "متوسط",
                rating = 4.6f
            ),
            Destination(
                id = "mashhad",
                name = "مشهد",
                country = "ایران",
                description = "پایتخت معنوی ایران با حرم امام رضا",
                attractions = listOf("حرم امام رضا", "طوس", "آرامگاه نادرشاه", "باغ ملک"),
                bestTimeToVisit = "بهار و پاییز",
                averageCost = "پایین",
                rating = 4.7f
            )
        )
        
        saveDestinations(defaultDestinations)
        return defaultDestinations
    }
    
    /**
     * ذخیره سفرها
     */
    private fun saveTrips(trips: List<TravelTrip>) {
        try {
            val tripsJson = json.encodeToString(trips)
            prefs.edit()
                .putString(TRIPS_KEY, tripsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در ذخیره سفرها: ${e.message}")
        }
    }
    
    /**
     * ذخیره مقاصد
     */
    private fun saveDestinations(destinations: List<Destination>) {
        try {
            val destinationsJson = json.encodeToString(destinations)
            prefs.edit()
                .putString(DESTINATIONS_KEY, destinationsJson)
                .apply()
        } catch (e: Exception) {
            Log.e("TravelPlannerManager", "❌ خطا در ذخیره مقاصد: ${e.message}")
        }
    }
    
    /**
     * پاک‌سازی منابع
     */
    fun cleanup() {
        scope.cancel()
        Log.i("TravelPlannerManager", "🧹 منابع TravelPlannerManager پاک‌سازی شد")
    }
}
