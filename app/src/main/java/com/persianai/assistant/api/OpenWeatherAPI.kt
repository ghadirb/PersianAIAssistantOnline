package com.persianai.assistant.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * OpenWeatherMap API Client
 * برای دریافت آب و هوای واقعی
 */
class OpenWeatherAPI {
    
    companion object {
        private const val TAG = "OpenWeatherAPI"
        // کلید API رایگان OpenWeatherMap
        private const val API_KEY = "f8366599ed1ede5949ccd3be8959b718"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5"
        private const val CACHE_DURATION = 10 * 60 * 1000L // 10 دقیقه کش
        
        // کش برای جلوگیری از درخواست‌های مکرر
        private val weatherCache = mutableMapOf<String, Pair<WeatherData, Long>>()
        
        data class WeatherData(
            val temp: Double,
            val feelsLike: Double,
            val tempMin: Double,
            val tempMax: Double,
            val humidity: Int,
            val pressure: Int,
            val windSpeed: Double,
            val description: String,
            val icon: String,
            val cityName: String
        )
        
        data class ForecastData(
            val dateTime: Long,
            val temp: Double,
            val tempMin: Double,
            val tempMax: Double,
            val description: String,
            val icon: String
        )
        
        /**
         * دریافت آب و هوای فعلی شهر
         */
        suspend fun getCurrentWeather(cityName: String): WeatherData? = withContext(Dispatchers.IO) {
            // بررسی کش
            val cached = weatherCache[cityName]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION) {
                Log.d(TAG, "Returning cached weather for $cityName")
                return@withContext cached.first
            }
            
            try {
                val url = "$BASE_URL/weather?q=$cityName&appid=$API_KEY&units=metric&lang=fa"
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val main = json.getJSONObject("main")
                    val weather = json.getJSONArray("weather").getJSONObject(0)
                    val wind = json.getJSONObject("wind")
                    
                    val weatherData = WeatherData(
                        temp = main.getDouble("temp"),
                        feelsLike = main.getDouble("feels_like"),
                        tempMin = main.getDouble("temp_min"),
                        tempMax = main.getDouble("temp_max"),
                        humidity = main.getInt("humidity"),
                        pressure = main.getInt("pressure"),
                        windSpeed = wind.getDouble("speed"),
                        description = weather.getString("description"),
                        icon = weather.getString("icon"),
                        cityName = json.getString("name")
                    )
                    
                    // ذخیره در کش
                    weatherCache[cityName] = Pair(weatherData, System.currentTimeMillis())
                    Log.d(TAG, "Weather updated: ${weatherData.temp}°C for ${weatherData.cityName}")
                    
                    return@withContext weatherData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting weather", e)
            }
            return@withContext null
        }
        
        /**
         * دریافت آب و هوا با مختصات جغرافیایی
         */
        suspend fun getWeatherByLocation(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/weather?lat=$lat&lon=$lon&appid=$API_KEY&units=metric&lang=fa"
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val main = json.getJSONObject("main")
                    val weather = json.getJSONArray("weather").getJSONObject(0)
                    val wind = json.getJSONObject("wind")
                    
                    val weatherData = WeatherData(
                        temp = main.getDouble("temp"),
                        feelsLike = main.getDouble("feels_like"),
                        tempMin = main.getDouble("temp_min"),
                        tempMax = main.getDouble("temp_max"),
                        humidity = main.getInt("humidity"),
                        pressure = main.getInt("pressure"),
                        windSpeed = wind.getDouble("speed"),
                        description = weather.getString("description"),
                        icon = weather.getString("icon"),
                        cityName = json.getString("name")
                    )
                    
                    // ذخیره در کش
                    weatherCache[weatherData.cityName] = Pair(weatherData, System.currentTimeMillis())
                    Log.d(TAG, "Weather updated: ${weatherData.temp}°C for ${weatherData.cityName}")
                    
                    return@withContext weatherData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting weather by location", e)
            }
            return@withContext null
        }
        
        /**
         * دریافت پیش‌بینی 5 روزه
         */
        suspend fun getForecast(cityName: String): List<ForecastData> = withContext(Dispatchers.IO) {
            val forecasts = mutableListOf<ForecastData>()
            
            try {
                val url = "$BASE_URL/forecast?q=$cityName&appid=$API_KEY&units=metric&lang=fa"
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val list = json.getJSONArray("list")
                    
                    for (i in 0 until minOf(list.length(), 40)) { // حداکثر 40 آیتم (5 روز)
                        val item = list.getJSONObject(i)
                        val main = item.getJSONObject("main")
                        val weather = item.getJSONArray("weather").getJSONObject(0)
                        
                        forecasts.add(ForecastData(
                            dateTime = item.getLong("dt") * 1000, // تبدیل به میلی‌ثانیه
                            temp = main.getDouble("temp"),
                            tempMin = main.getDouble("temp_min"),
                            tempMax = main.getDouble("temp_max"),
                            description = weather.getString("description"),
                            icon = weather.getString("icon")
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting forecast", e)
            }
            
            return@withContext forecasts
        }
        
        /**
         * دریافت آیکون آب و هوا
         */
        fun getWeatherIconUrl(iconCode: String): String {
            return "https://openweathermap.org/img/wn/${iconCode}@2x.png"
        }
        
        /**
         * تبدیل آیکون به ایموجی
         */
        fun getWeatherEmoji(iconCode: String): String {
            return when (iconCode) {
                "01d" -> "☀️"  // آفتابی روز
                "01n" -> "🌙"  // صاف شب
                "02d", "02n" -> "⛅"  // کمی ابری
                "03d", "03n" -> "☁️"  // ابری
                "04d", "04n" -> "☁️"  // ابری کامل
                "09d", "09n" -> "🌧️"  // رگبار
                "10d", "10n" -> "🌦️"  // بارانی
                "11d", "11n" -> "⛈️"  // رعد و برق
                "13d", "13n" -> "❄️"  // برفی
                "50d", "50n" -> "🌫️"  // مه
                else -> "🌤️"
            }
        }
        
        /**
         * ارسال درخواست HTTP
         */
        private fun makeRequest(urlString: String): String? {
            return try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    Log.e(TAG, "HTTP error: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request failed", e)
                null
            }
        }
        
        /**
         * پاک کردن کش آب و هوا
         */
        fun clearCache() {
            weatherCache.clear()
            Log.d(TAG, "Weather cache cleared")
        }
        
        /**
         * تست API با داده‌های نمونه (برای زمانی که API key نیست)
         */
        fun getMockWeatherData(cityName: String): WeatherData {
            // استفاده از داده‌های واقعی‌تر بر اساس شهر و زمان
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val baseTemp = when(cityName) {
                "تهران" -> if (hour in 2..6) 15.0 else if (hour in 12..16) 28.0 else 23.0
                "مشهد" -> if (hour in 2..6) 12.0 else if (hour in 12..16) 25.0 else 20.0
                "اصفهان" -> if (hour in 2..6) 14.0 else if (hour in 12..16) 30.0 else 22.0
                "شیراز" -> if (hour in 2..6) 16.0 else if (hour in 12..16) 29.0 else 24.0
                "تبریز" -> if (hour in 2..6) 10.0 else if (hour in 12..16) 22.0 else 18.0
                else -> if (hour in 2..6) 15.0 else if (hour in 12..16) 27.0 else 22.0
            }
            
            return WeatherData(
                temp = baseTemp + (Math.random() * 3 - 1.5).roundToInt(),
                feelsLike = baseTemp - 2,
                tempMin = baseTemp - 5,
                tempMax = baseTemp + 5,
                humidity = 35 + (Math.random() * 30).toInt(),
                pressure = 1013 + (Math.random() * 10 - 5).toInt(),
                windSpeed = 2.0 + Math.random() * 5,
                description = when ((Math.random() * 4).toInt()) {
                    0 -> "آسمان صاف"
                    1 -> "کمی ابری"
                    2 -> "ابری"
                    else -> "آفتابی"
                },
                icon = when(hour) {
                    in 6..18 -> "01d"  // روز
                    else -> "01n"      // شب
                },
                cityName = cityName
            )
        }
    }
}
