package com.persianai.assistant.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * WorldWeatherOnline API Client - نسخه اصلاح شده
 * API Key: db4236ef33c64dab8ce194001251110
 * 
 * این نسخه مشکلات زیر را حل می‌کند:
 * 1. دمای نادرست (25 سپس 32)
 * 2. عدم همخوانی با API واقعی
 * 3. بهبود کش و عملکرد
 */
class WorldWeatherAPI {
    
    companion object {
        private const val TAG = "WorldWeatherAPI"
        // توکن واقعی World Weather Online
        private const val API_KEY = "db4236ef33c64dab8ce194001251110"
        private const val BASE_URL = "https://api.worldweatheronline.com/premium/v1"
        // زمان کش: 30 دقیقه برای دقت بیشتر
        private const val CACHE_DURATION = 30 * 60 * 1000L
        
        // کش برای هر شهر
        private val weatherCache = mutableMapOf<String, Pair<WeatherData, Long>>()
        
        data class WeatherData(
            val temp: Double,           // دمای واقعی
            val feelsLike: Double,      // دمای حسی
            val humidity: Int,          // رطوبت
            val windSpeed: Double,      // سرعت باد
            val description: String,    // توضیحات فارسی
            val icon: String,
            val cityName: String,
            val uvIndex: Int,
            val visibility: Int,
            val pressure: Int,
            val dewPoint: Double,       // نقطه شبنم
            val cloudCover: Int         // پوشش ابری
        )
        
        data class ForecastDay(
            val date: String,
            val maxTemp: Double,
            val minTemp: Double,
            val avgTemp: Double,
            val description: String,
            val icon: String,
            val hourly: List<HourlyForecast>
        )
        
        data class HourlyForecast(
            val time: String,
            val temp: Double,
            val feelsLike: Double,
            val description: String,
            val icon: String,
            val chanceOfRain: Int,
            val humidity: Int
        )
        
        /**
         * دریافت آب و هوای فعلی با دقت بالا
         */
        suspend fun getCurrentWeather(cityName: String): WeatherData? = withContext(Dispatchers.IO) {
            // بررسی کش
            val cached = weatherCache[cityName]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION) {
                Log.d(TAG, "Using cached weather for $cityName")
                return@withContext cached.first
            }
            
            try {
                val encodedCity = URLEncoder.encode(cityName, "UTF-8")
                val url = "$BASE_URL/weather.ashx?key=$API_KEY&q=$encodedCity&format=json&lang=fa&includelocation=yes"
                
                Log.d(TAG, "Fetching weather from: $url")
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    
                    // بررسی وضعیت پاسخ
                    if (json.has("data")) {
                        val data = json.getJSONObject("data")
                        val currentCondition = data.getJSONArray("current_condition").getJSONObject(0)
                        
                        // استخراج دمای واقعی
                        val actualTemp = currentCondition.getDouble("temp_C")
                        
                        Log.d(TAG, "Real temperature for $cityName: $actualTemp°C")
                        
                        val weatherData = WeatherData(
                            temp = actualTemp,
                            feelsLike = currentCondition.getDouble("FeelsLikeC"),
                            humidity = currentCondition.getInt("humidity"),
                            windSpeed = currentCondition.getDouble("windspeedKmph"),
                            description = getDescription(currentCondition),
                            icon = getWeatherIconCode(currentCondition.getString("weatherCode")),
                            cityName = cityName,
                            uvIndex = currentCondition.optInt("uvIndex", 0),
                            visibility = currentCondition.optInt("visibility", 10),
                            pressure = currentCondition.optInt("pressure", 1013),
                            dewPoint = currentCondition.optDouble("DewPointC", 0.0),
                            cloudCover = currentCondition.optInt("cloudcover", 0)
                        )
                        
                        // ذخیره در کش
                        weatherCache[cityName] = Pair(weatherData, System.currentTimeMillis())
                        Log.d(TAG, "Weather cached: ${weatherData.temp}°C for $cityName")
                        
                        return@withContext weatherData
                    } else {
                        Log.e(TAG, "Invalid API response: no data field")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting weather for $cityName", e)
            }
            
            return@withContext null
        }
        
        /**
         * دریافت توضیحات فارسی
         */
        private fun getDescription(currentCondition: JSONObject): String {
            return try {
                val langFa = currentCondition.getJSONArray("lang_fa")
                if (langFa.length() > 0) {
                    langFa.getJSONObject(0).getString("value")
                } else {
                    val weatherDesc = currentCondition.getJSONArray("weatherDesc")
                    weatherDesc.getJSONObject(0).getString("value")
                }
            } catch (e: Exception) {
                "نامشخص"
            }
        }
        
        /**
         * دریافت پیش‌بینی 7 روزه
         */
        suspend fun getForecast(cityName: String, numDays: Int = 7): List<ForecastDay> = withContext(Dispatchers.IO) {
            val forecasts = mutableListOf<ForecastDay>()
            
            try {
                val encodedCity = URLEncoder.encode(cityName, "UTF-8")
                val url = "$BASE_URL/weather.ashx?key=$API_KEY&q=$encodedCity&format=json&num_of_days=$numDays&tp=3&lang=fa"
                
                Log.d(TAG, "Fetching forecast from: $url")
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val data = json.getJSONObject("data")
                    val weather = data.getJSONArray("weather")
                    
                    for (i in 0 until weather.length()) {
                        val day = weather.getJSONObject(i)
                        val hourlyArray = day.getJSONArray("hourly")
                        
                        val hourlyForecasts = mutableListOf<HourlyForecast>()
                        for (j in 0 until hourlyArray.length()) {
                            val hour = hourlyArray.getJSONObject(j)
                            
                            hourlyForecasts.add(HourlyForecast(
                                time = hour.getString("time"),
                                temp = hour.getDouble("tempC"),
                                feelsLike = hour.getDouble("FeelsLikeC"),
                                description = getDescription(hour),
                                icon = getWeatherIconCode(hour.getString("weatherCode")),
                                chanceOfRain = hour.optInt("chanceofrain", 0),
                                humidity = hour.optInt("humidity", 50)
                            ))
                        }
                        
                        forecasts.add(ForecastDay(
                            date = day.getString("date"),
                            maxTemp = day.getDouble("maxtempC"),
                            minTemp = day.getDouble("mintempC"),
                            avgTemp = day.getDouble("avgtempC"),
                            description = hourlyForecasts.firstOrNull()?.description ?: "",
                            icon = hourlyForecasts.firstOrNull()?.icon ?: "01d",
                            hourly = hourlyForecasts
                        ))
                    }
                    
                    Log.d(TAG, "Forecast loaded: ${forecasts.size} days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting forecast", e)
            }
            
            return@withContext forecasts
        }
        
        /**
         * تبدیل کد آب و هوا به آیکون
         */
        private fun getWeatherIconCode(weatherCode: String): String {
            return when (weatherCode) {
                "113" -> "01d" // آفتابی / صاف
                "116" -> "02d" // قسمتی ابری
                "119", "122" -> "03d" // ابری
                "143", "248", "260" -> "50d" // مه
                "176", "263", "266", "293", "296" -> "10d" // باران خفیف
                "185", "281", "284", "311", "314", "317", "350", "362", "365" -> "13d" // برف خفیف
                "179", "227", "230", "320", "323", "326", "368", "371" -> "13d" // برف
                "182", "185", "281", "284", "311", "314", "317", "350", "377" -> "13d" // بارش مخلوط
                "200", "386", "389", "392", "395" -> "11d" // رعد و برق
                "299", "302", "305", "308", "356", "359" -> "09d" // باران شدید
                else -> "01d"
            }
        }
        
        /**
         * تبدیل به ایموجی
         */
        fun getWeatherEmoji(weatherCode: String): String {
            return when (weatherCode) {
                "113" -> "☀️"
                "116" -> "⛅"
                "119", "122" -> "☁️"
                "143", "248", "260" -> "🌫️"
                "176", "263", "266", "293", "296" -> "🌦️"
                "299", "302", "305", "308", "356", "359" -> "🌧️"
                "179", "227", "230", "320", "323", "326", "368", "371" -> "❄️"
                "182", "185", "281", "284", "311", "314", "317", "350", "377" -> "🌨️"
                "200", "386", "389", "392", "395" -> "⛈️"
                else -> "🌤️"
            }
        }
        
        /**
         * درخواست HTTP با مدیریت خطا
         */
        private fun makeRequest(urlString: String): String? {
            var connection: HttpURLConnection? = null
            return try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000 // 15 ثانیه
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "PersianAssistant/1.0")
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    Log.e(TAG, "HTTP error: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request failed", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
        
        /**
         * پاک کردن کش
         */
        fun clearCache() {
            weatherCache.clear()
            Log.d(TAG, "Weather cache cleared")
        }
        
        /**
         * بررسی اعتبار کش برای یک شهر
         */
        fun isCacheValid(cityName: String): Boolean {
            val cached = weatherCache[cityName]
            return cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION
        }
        
        /**
         * دریافت آب و هوای کش شده
         */
        fun getCachedWeather(cityName: String): WeatherData? {
            val cached = weatherCache[cityName]
            return if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION) {
                cached.first
            } else {
                null
            }
        }
    }
}
