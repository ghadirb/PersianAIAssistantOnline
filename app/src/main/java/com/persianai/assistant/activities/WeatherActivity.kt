package com.persianai.assistant.activities

import android.content.Intent
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
        
        // دکمه پیش‌بینی 7 روزه
        binding.forecastButton.setOnClickListener {
            val intent = Intent(this, WeatherForecastActivity::class.java)
            startActivity(intent)
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
                    binding.cityNameText.text = city
                    binding.tempText.text = "${weather.temp.roundToInt()}°"
                    binding.descText.text = weather.description
                    binding.humidityText.text = "${weather.humidity}%"
                    binding.windText.text = "${weather.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "${weather.feelsLike.roundToInt()}°"
                } else {
                    // در صورت عدم دسترسی به API، از Mock Data استفاده می‌کنیم
                    val mockWeather = OpenWeatherAPI.getMockWeatherData(city)
                    binding.cityNameText.text = city
                    binding.tempText.text = "${mockWeather.temp.roundToInt()}°"
                    binding.descText.text = mockWeather.description
                    binding.humidityText.text = "${mockWeather.humidity}%"
                    binding.windText.text = "${mockWeather.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "${mockWeather.feelsLike.roundToInt()}°"
                    
                    Toast.makeText(this@WeatherActivity, "⚠️ استفاده از داده‌های آفلاین", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@WeatherActivity, "خطا در دریافت اطلاعات آب و هوا", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
