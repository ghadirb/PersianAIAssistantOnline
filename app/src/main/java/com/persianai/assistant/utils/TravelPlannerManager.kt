package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.api.WorldWeatherAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * دستیار هوشمند برنامه‌ریزی سفر
 * ترکیب آب‌وهوا، تقویم، و حمل‌ونقل برای پیشنهاد بهترین زمان سفر
 */
class TravelPlannerManager(private val context: Context) {
    
    private val weatherAPI = WorldWeatherAPI(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    companion object {
        private const val TAG = "TravelPlanner"
    }
    
    /**
     * اطلاعات سفر
     */
    data class TripPlan(
        val destination: String,
        val departureDate: Long,
        val returnDate: Long?,
        val travelers: Int,
        val transportType: TransportType,
        val accommodation: String = "",
        val budget: Long = 0,
        val notes: String = ""
    )
    
    /**
     * نوع وسیله حمل‌ونقل
     */
    enum class TransportType(val displayName: String) {
        CAR("خودرو شخصی"),
        BUS("اتوبوس"),
        TRAIN("قطار"),
        PLANE("هواپیما"),
        OTHER("سایر")
    }
    
    /**
     * توصیه‌های سفر
     */
    data class TravelRecommendations(
        val destination: String,
        val bestDepartureTime: String,
        val weatherForecast: WeatherInfo,
        val packingList: List<String>,
        val warnings: List<String>,
        val tips: List<String>,
        val estimatedDuration: String
    )
    
    /**
     * اطلاعات آب‌وهوا
     */
    data class WeatherInfo(
        val temperature: String,
        val condition: String,
        val humidity: String,
        val windSpeed: String,
        val aqi: String,
        val uvIndex: String
    )
    
    /**
     * برنامه‌ریزی سفر با توصیه‌های هوشمند
     */
    suspend fun planTrip(
        destination: String,
        departureDate: Long,
        returnDate: Long?,
        transportType: TransportType
    ): TravelRecommendations = withContext(Dispatchers.IO) {
        
        Log.i(TAG, "🗺️ برنامه‌ریزی سفر به $destination")
        
        // دریافت پیش‌بینی آب‌وهوا
        val weatherInfo = getWeatherForecast(destination, departureDate)
        
        // بهترین زمان حرکت
        val bestTime = calculateBestDepartureTime(destination, departureDate, weatherInfo, transportType)
        
        // لیست وسایل
        val packingList = generatePackingList(weatherInfo, transportType)
        
        // هشدارها
        val warnings = generateWarnings(weatherInfo, transportType, departureDate)
        
        // نکات مفید
        val tips = generateTravelTips(destination, weatherInfo, transportType)
        
        // مدت زمان تقریبی
        val duration = estimateTravelDuration(destination, transportType)
        
        TravelRecommendations(
            destination = destination,
            bestDepartureTime = bestTime,
            weatherForecast = weatherInfo,
            packingList = packingList,
            warnings = warnings,
            tips = tips,
            estimatedDuration = duration
        )
    }
    
    /**
     * دریافت پیش‌بینی آب‌وهوا
     */
    private suspend fun getWeatherForecast(destination: String, date: Long): WeatherInfo {
        return try {
            val weather = weatherAPI.getWeatherByCity(destination)
            
            WeatherInfo(
                temperature = "${weather.main.temp}°C",
                condition = weather.weather.firstOrNull()?.description ?: "نامشخص",
                humidity = "${weather.main.humidity}%",
                windSpeed = "${weather.wind.speed} m/s",
                aqi = weather.aqi?.toString() ?: "نامشخص",
                uvIndex = weather.uvi?.toString() ?: "نامشخص"
            )
        } catch (e: Exception) {
            Log.e(TAG, "خطا در دریافت آب‌وهوا", e)
            WeatherInfo(
                temperature = "نامشخص",
                condition = "نامشخص",
                humidity = "نامشخص",
                windSpeed = "نامشخص",
                aqi = "نامشخص",
                uvIndex = "نامشخص"
            )
        }
    }
    
    /**
     * محاسبه بهترین زمان حرکت
     */
    private fun calculateBestDepartureTime(
        destination: String,
        departureDate: Long,
        weather: WeatherInfo,
        transportType: TransportType
    ): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = departureDate
        
        // بر اساس نوع وسیله
        val recommendedHour = when (transportType) {
            TransportType.CAR -> {
                // برای خودرو: صبح زود یا بعدازظهر (جلوگیری از ترافیک)
                if (calendar.get(Calendar.DAY_OF_WEEK) in Calendar.SATURDAY..Calendar.THURSDAY) {
                    6 // صبح زود در روزهای کاری
                } else {
                    8 // روزهای تعطیل
                }
            }
            TransportType.BUS -> 7 // اتوبوس‌ها معمولاً صبح حرکت می‌کنند
            TransportType.TRAIN -> 8
            TransportType.PLANE -> {
                // برای پرواز: ساعت‌های اول صبح کمتر تاخیر دارند
                6
            }
            TransportType.OTHER -> 8
        }
        
        calendar.set(Calendar.HOUR_OF_DAY, recommendedHour)
        calendar.set(Calendar.MINUTE, 0)
        
        val timeFormat = SimpleDateFormat("EEEE، d MMMM yyyy - ساعت HH:mm", Locale("fa", "IR"))
        val bestTime = timeFormat.format(calendar.time)
        
        // بررسی شرایط آب‌وهوایی
        val weatherNote = when {
            weather.condition.contains("باران", ignoreCase = true) -> "\n⚠️ توجه: احتمال بارش وجود دارد. زودتر حرکت کنید."
            weather.condition.contains("برف", ignoreCase = true) -> "\n❄️ توجه: احتمال برف‌بارش. حرکت را به تعویق بیندازید."
            weather.temperature.contains("-") -> "\n🥶 توجه: هوا سرد است. زودتر حرکت کنید."
            else -> ""
        }
        
        return bestTime + weatherNote
    }
    
    /**
     * تولید لیست وسایل
     */
    private fun generatePackingList(weather: WeatherInfo, transportType: TransportType): List<String> {
        val list = mutableListOf<String>()
        
        // وسایل عمومی
        list.addAll(listOf(
            "📱 شارژر موبایل و پاوربانک",
            "💳 کارت شناسایی و کارت بانکی",
            "💊 داروهای شخصی",
            "🧴 لوازم بهداشتی",
            "🎒 کوله‌پشتی یا چمدان"
        ))
        
        // بر اساس آب‌وهوا
        val temp = weather.temperature.replace("°C", "").toDoubleOrNull() ?: 20.0
        
        when {
            temp < 10 -> {
                list.add("🧥 لباس گرم و کت ضخیم")
                list.add("🧣 شال و کلاه")
                list.add("🧤 دستکش")
            }
            temp > 30 -> {
                list.add("👕 لباس نازک و راحت")
                list.add("🕶️ عینک آفتابی")
                list.add("🧴 کرم ضد آفتاب")
                list.add("🧢 کلاه آفتابی")
            }
            else -> {
                list.add("👕 لباس مناسب فصل")
            }
        }
        
        if (weather.condition.contains("باران", ignoreCase = true)) {
            list.add("☔ چتر یا بارانی")
        }
        
        // بر اساس وسیله حمل‌ونقل
        when (transportType) {
            TransportType.CAR -> {
                list.addAll(listOf(
                    "🚗 مدارک خودرو",
                    "🔧 جعبه ابزار و یدک",
                    "⛽ کارت سوخت",
                    "🗺️ نقشه یا GPS"
                ))
            }
            TransportType.PLANE -> {
                list.addAll(listOf(
                    "✈️ بلیط و پاسپورت",
                    "🎧 هندزفری",
                    "😷 ماسک"
                ))
            }
            else -> {}
        }
        
        return list
    }
    
    /**
     * تولید هشدارها
     */
    private fun generateWarnings(weather: WeatherInfo, transportType: TransportType, departureDate: Long): List<String> {
        val warnings = mutableListOf<String>()
        
        // هشدارهای آب‌وهوایی
        val temp = weather.temperature.replace("°C", "").toDoubleOrNull() ?: 20.0
        
        if (temp < 0) {
            warnings.add("🥶 هشدار یخبندان: جاده‌ها ممکن است لغزنده باشند")
        }
        
        if (weather.condition.contains("باران شدید", ignoreCase = true)) {
            warnings.add("🌧️ هشدار باران شدید: احتمال آبگرفتگی جاده‌ها")
        }
        
        if (weather.condition.contains("برف", ignoreCase = true) && transportType == TransportType.CAR) {
            warnings.add("❄️ برف‌بارش: حتماً زنجیر چرخ همراه داشته باشید")
        }
        
        val aqi = weather.aqi.toIntOrNull() ?: 0
        if (aqi > 150) {
            warnings.add("😷 هشدار آلودگی هوا: برای افراد حساس خطرناک است")
        }
        
        // هشدار تعطیلات
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = departureDate
        if (PersianEvents.isHoliday(calendar)) {
            warnings.add("📅 روز تعطیل: ترافیک سنگین و جاده‌های شلوغ")
        }
        
        return warnings
    }
    
    /**
     * تولید نکات مفید
     */
    private fun generateTravelTips(destination: String, weather: WeatherInfo, transportType: TransportType): List<String> {
        val tips = mutableListOf<String>()
        
        // نکات عمومی
        tips.addAll(listOf(
            "💡 قبل از حرکت، خودرو را سرویس کنید",
            "📱 موقعیت مکانی را با خانواده به اشتراک بگذارید",
            "⛽ قبل از حرکت، باک را پر کنید"
        ))
        
        // نکات مخصوص وسیله
        when (transportType) {
            TransportType.CAR -> {
                tips.addAll(listOf(
                    "🚗 فشار باد لاستیک‌ها را چک کنید",
                    "🔋 باتری خودرو را بررسی کنید",
                    "🛣️ از برنامه‌های ترافیکی استفاده کنید"
                ))
            }
            TransportType.BUS -> {
                tips.add("🚌 2 ساعت قبل به ترمینال برسید")
            }
            TransportType.TRAIN -> {
                tips.add("🚆 1 ساعت قبل به ایستگاه برسید")
            }
            TransportType.PLANE -> {
                tips.add("✈️ 3 ساعت قبل به فرودگاه برسید")
            }
            else -> {}
        }
        
        // نکات مربوط به آب‌وهوا
        val temp = weather.temperature.replace("°C", "").toDoubleOrNull() ?: 20.0
        if (temp < 5) {
            tips.add("🥶 در سرما، خودرو را 5-10 دقیقه گرم کنید")
        }
        
        return tips
    }
    
    /**
     * تخمین مدت زمان سفر
     */
    private fun estimateTravelDuration(destination: String, transportType: TransportType): String {
        // این یک تخمین ساده است - در واقعیت باید از API مسیریابی استفاده کرد
        
        val baseDistance = when {
            destination.contains("تهران", ignoreCase = true) -> 0
            destination.contains("مشهد", ignoreCase = true) -> 900
            destination.contains("اصفهان", ignoreCase = true) -> 450
            destination.contains("شیراز", ignoreCase = true) -> 900
            destination.contains("تبریز", ignoreCase = true) -> 600
            destination.contains("کرمان", ignoreCase = true) -> 1000
            else -> 500 // پیش‌فرض
        }
        
        val hours = when (transportType) {
            TransportType.CAR -> baseDistance / 80 // میانگین 80 km/h
            TransportType.BUS -> baseDistance / 70
            TransportType.TRAIN -> baseDistance / 100
            TransportType.PLANE -> baseDistance / 600 // +2 ساعت برای فرآیندهای فرودگاه
            else -> baseDistance / 60
        }
        
        return if (hours < 1) {
            "کمتر از 1 ساعت"
        } else {
            "$hours ساعت (تقریبی)"
        }
    }
    
    /**
     * چک کردن شرایط مسیر در زمان واقعی
     */
    suspend fun checkRouteConditions(destination: String): RouteConditions {
        return withContext(Dispatchers.IO) {
            val weather = getWeatherForecast(destination, System.currentTimeMillis())
            
            val status = when {
                weather.condition.contains("برف", ignoreCase = true) -> RouteStatus.DANGEROUS
                weather.condition.contains("باران شدید", ignoreCase = true) -> RouteStatus.RISKY
                weather.aqi.toIntOrNull()?.let { it > 150 } == true -> RouteStatus.CAUTION
                else -> RouteStatus.CLEAR
            }
            
            RouteConditions(
                status = status,
                weather = weather,
                recommendation = when (status) {
                    RouteStatus.DANGEROUS -> "⛔ توصیه می‌شود سفر را به تعویق بیندازید"
                    RouteStatus.RISKY -> "⚠️ با احتیاط بیشتری رانندگی کنید"
                    RouteStatus.CAUTION -> "💡 از ماسک استفاده کنید"
                    RouteStatus.CLEAR -> "✅ شرایط مسیر مناسب است"
                }
            )
        }
    }
    
    enum class RouteStatus {
        CLEAR,      // مسیر باز
        CAUTION,    // احتیاط
        RISKY,      // پرخطر
        DANGEROUS   // خطرناک
    }
    
    data class RouteConditions(
        val status: RouteStatus,
        val weather: WeatherInfo,
        val recommendation: String
    )
}
