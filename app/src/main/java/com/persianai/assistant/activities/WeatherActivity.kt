package com.persianai.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.databinding.ActivityWeatherBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        // دکمه رفرش
        binding.refreshButton?.setOnClickListener {
            loadWeather(forceFresh = true)
        }
        
        loadWeather()
    }
    
    private fun loadWeather(forceFresh: Boolean = false) {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        // پاک کردن کش در صورت نیاز
        if (forceFresh) {
            OpenWeatherAPI.clearCache()
        }
        
        lifecycleScope.launch {
            try {
                // ابتدا سعی می‌کنیم داده واقعی بگیریم
                val weather = OpenWeatherAPI.getCurrentWeather(city)
                
                if (weather != null) {
                    // نمایش داده‌های واقعی
                    binding.tempText.text = "${weather.temp.roundToInt()}°C"
                    binding.descText.text = "$city - ${weather.description}"
                    binding.aqiText.text = "رطوبت: ${weather.humidity}% | باد: ${weather.windSpeed} m/s"
                    // Update additional fields if they exist
                    binding.feelsLikeText?.text = "احساسی: ${weather.feelsLike.roundToInt()}°"
                    binding.minMaxText?.text = "${weather.tempMin.roundToInt()}° / ${weather.tempMax.roundToInt()}°"
                } else {
                    // در صورت عدم دسترسی به API، از Mock Data استفاده می‌کنیم
                    val mockWeather = OpenWeatherAPI.getMockWeatherData(city)
                    binding.tempText.text = "${mockWeather.temp.roundToInt()}°C"
                    binding.descText.text = "$city - ${mockWeather.description}"
                    binding.aqiText.text = "رطوبت: ${mockWeather.humidity}% | باد: ${mockWeather.windSpeed.roundToInt()} m/s"
                    // Update additional fields if they exist
                    binding.feelsLikeText?.text = "احساسی: ${mockWeather.feelsLike.roundToInt()}°"
                    binding.minMaxText?.text = "${mockWeather.tempMin.roundToInt()}° / ${mockWeather.tempMax.roundToInt()}°"
                    
                    Toast.makeText(this@WeatherActivity, "⚠️ استفاده از داده‌های آفلاین", Toast.LENGTH_SHORT).show()
                }
                
                // پیش‌بینی 7 روزه
                
            } catch (e: Exception) {
                Toast.makeText(this@WeatherActivity, "خطا در دریافت اطلاعات آب و هوا", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun loadForecast(city: String) {
        try {
            val forecasts = OpenWeatherAPI.getForecast(city)
            if (forecasts.isNotEmpty()) {
                // نمایش پیش‌بینی در RecyclerView یا ScrollView
                // TODO: اضافه کردن RecyclerView برای نمایش پیش‌بینی
            }
        } catch (e: Exception) {
            // در صورت خطا، پیش‌بینی نمایش نمی‌دهیم
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
