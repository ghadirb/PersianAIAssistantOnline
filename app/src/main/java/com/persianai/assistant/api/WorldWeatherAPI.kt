package com.persianai.assistant.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * WorldWeatherOnline API Client
 * API Key: db4236ef33c64dab8ce194001251110
 */
class WorldWeatherAPI {
    
    companion object {
        private const val TAG = "WorldWeatherAPI"
        private const val API_KEY = "db4236ef33c64dab8ce194001251110"
        private const val BASE_URL = "https://api.worldweatheronline.com/premium/v1"
        private const val CACHE_DURATION = 10 * 60 * 1000L // 10 دقیقه
        
        private val weatherCache = mutableMapOf<String, Pair<WeatherData, Long>>()
        
        data class WeatherData(
            val temp: Double,
            val feelsLike: Double,
            val humidity: Int,
            val windSpeed: Double,
            val description: String,
            val icon: String,
            val cityName: String,
            val uvIndex: Int,
            val visibility: Int
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
            val chanceOfRain: Int
        )
        
        /**
         * دریافت آب و هوای فعلی
         */
        suspend fun getCurrentWeather(cityName: String): WeatherData? = withContext(Dispatchers.IO) {
            // بررسی کش
            val cached = weatherCache[cityName]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION) {
                Log.d(TAG, "Returning cached weather for $cityName")
                return@withContext cached.first
            }
            
            try {
                val encodedCity = URLEncoder.encode(cityName, "UTF-8")
                val url = "$BASE_URL/weather.ashx?key=$API_KEY&q=$encodedCity&format=json&lang=fa"
                val response = makeRequest(url)
                
                if (response != null) {
                    val json = JSONObject(response)
                    val data = json.getJSONObject("data")
                    val currentCondition = data.getJSONArray("current_condition").getJSONObject(0)
                    
                    val weatherData = WeatherData(
                        temp = currentCondition.getDouble("temp_C"),
                        feelsLike = currentCondition.getDouble("FeelsLikeC"),
                        humidity = currentCondition.getInt("humidity"),
                        windSpeed = currentCondition.getDouble("windspeedKmph"),
                        description = currentCondition.getJSONArray("lang_fa").getJSONObject(0).getString("value"),
                        icon = getWeatherIconCode(currentCondition.getString("weatherCode")),
                        cityName = cityName,
                        uvIndex = currentCondition.optInt("uvIndex", 0),
                        visibility = currentCondition.optInt("visibility", 10)
                    )
                    
                    weatherCache[cityName] = Pair(weatherData, System.currentTimeMillis())
                    Log.d(TAG, "Weather updated: ${weatherData.temp}°C for $cityName")
                    
                    return@withContext weatherData
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting weather for $cityName", e)
            }
            
            return@withContext null
        }
        
        /**
         * دریافت پیش‌بینی 7 روزه
         */
        suspend fun getForecast(cityName: String, numDays: Int = 7): List<ForecastDay> = withContext(Dispatchers.IO) {
            val forecasts = mutableListOf<ForecastDay>()
            
            try {
                val encodedCity = URLEncoder.encode(cityName, "UTF-8")
                val url = "$BASE_URL/weather.ashx?key=$API_KEY&q=$encodedCity&format=json&num_of_days=$numDays&tp=3&lang=fa"
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
                                description = hour.getJSONArray("lang_fa").getJSONObject(0).getString("value"),
                                icon = getWeatherIconCode(hour.getString("weatherCode")),
                                chanceOfRain = hour.optInt("chanceofrain", 0)
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
                "113" -> "01d" // Clear/Sunny
                "116" -> "02d" // Partly cloudy
                "119", "122" -> "03d" // Cloudy
                "143", "248", "260" -> "50d" // Mist/Fog
                "176", "263", "266", "293", "296" -> "10d" // Light rain
                "185", "281", "284", "311", "314", "317", "350", "362", "365" -> "13d" // Light snow
                "179", "227", "230", "320", "323", "326", "368", "371" -> "13d" // Snow
                "182", "185", "281", "284", "311", "314", "317", "350", "377" -> "13d" // Sleet
                "200", "386", "389", "392", "395" -> "11d" // Thunder
                "299", "302", "305", "308", "356", "359" -> "09d" // Heavy rain
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
         * درخواست HTTP
         */
        private fun makeRequest(urlString: String): String? {
            return try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
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
        
        fun clearCache() {
            weatherCache.clear()
            Log.d(TAG, "Weather cache cleared")
        }
    }
}
