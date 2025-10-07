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
        private const val API_KEY = "YOUR_API_KEY_HERE" // کاربر باید کلید خودش رو بذاره
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5"
        
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
            try {
                val url = "$BASE_URL/weather?q=$cityName&appid=$API_KEY&units=metric&lang=fa"
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val main = json.getJSONObject("main")
                    val weather = json.getJSONArray("weather").getJSONObject(0)
                    val wind = json.getJSONObject("wind")
                    
                    return@withContext WeatherData(
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
                    
                    return@withContext WeatherData(
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
         * تست API با داده‌های نمونه (برای زمانی که API key نیست)
         */
        fun getMockWeatherData(cityName: String): WeatherData {
            return WeatherData(
                temp = 25.0 + (Math.random() * 10 - 5).roundToInt(),
                feelsLike = 23.0,
                tempMin = 18.0,
                tempMax = 28.0,
                humidity = 45,
                pressure = 1013,
                windSpeed = 3.5,
                description = when ((Math.random() * 4).toInt()) {
                    0 -> "آسمان صاف"
                    1 -> "کمی ابری"
                    2 -> "ابری"
                    else -> "آفتابی"
                },
                icon = "01d",
                cityName = cityName
            )
        }
    }
}
