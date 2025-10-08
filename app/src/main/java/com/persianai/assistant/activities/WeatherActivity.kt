package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.R
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.databinding.ActivityWeatherBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherBinding
    
    private val cities = listOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        setupCitySpinner()
        
        // دکمه پیش‌بینی 7 روزه
        binding.forecastButton.setOnClickListener {
            try {
                val intent = Intent(this, WeatherForecastActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "خطا در باز کردن پیش‌بینی: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
        
        // دکمه بروزرسانی
        binding.refreshButton.setOnClickListener {
            loadWeather(forceFresh = true)
        }
        
        loadWeather()
    }
    
    private fun setupCitySpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cities)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.citySpinner.adapter = adapter
        
        // تنظیم شهر ذخیره شده
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val savedCity = prefs.getString("selected_city", "تهران")
        val position = cities.indexOf(savedCity)
        if (position >= 0) {
            binding.citySpinner.setSelection(position)
        }
        
        binding.citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCity = cities[position]
                prefs.edit().putString("selected_city", selectedCity).apply()
                loadWeather()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
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
