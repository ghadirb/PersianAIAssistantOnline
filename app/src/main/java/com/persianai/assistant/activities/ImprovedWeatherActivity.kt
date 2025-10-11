package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.api.AqicnWeatherAPI
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.databinding.ActivityImprovedWeatherBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * آب و هوا با API واقعی و UI کامل
 */
class ImprovedWeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityImprovedWeatherBinding
    private var currentCity = "تهران"
    
    private val popularCities = listOf(
        "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم",
        "کرمان", "ارومیه", "رشت", "زاهدان", "همدان", "کرمانشاه", "یزد", "اردبیل",
        "بندرعباس", "اراک", "زنجان", "قزوین", "سنندج", "گرگان", "نیشابور",
        "خرم‌آباد", "ساری", "کاشان", "آمل", "بوشهر", "بیرجند", "چالوس"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImprovedWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        // بارگذاری شهر ذخیره شده
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        currentCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        setupUI()
        loadCurrentWeather()
    }
    
    private fun setupUI() {
        // دکمه جستجوی شهر
        binding.searchCityButton.setOnClickListener {
            showCitySearchDialog()
        }
        
        // چیپ‌های دسترسی سریع
        setupQuickCities()
        
        // دکمه پیش‌بینی 7 روزه
        binding.forecast7DayButton.setOnClickListener {
            val intent = Intent(this, WeatherForecastActivity::class.java)
            intent.putExtra("city", currentCity)
            intent.putExtra("days", 7)
            startActivity(intent)
        }
        
        // حذف دکمه 30 روزه (نادرست بود)
        binding.forecast30DayButton.visibility = View.GONE
    }
    
    private fun setupQuickCities() {
        binding.quickCitiesLayout.removeAllViews()
        
        popularCities.take(10).forEach { city ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = city
                isClickable = true
                setOnClickListener {
                    selectCity(city)
                }
            }
            binding.quickCitiesLayout.addView(chip)
        }
    }
    
    private fun showCitySearchDialog() {
        val allCities = popularCities.sorted()
        
        val items = allCities.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🌍 انتخاب شهر")
            .setItems(items) { dialog, which ->
                selectCity(items[which])
                dialog.dismiss()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun selectCity(city: String) {
        currentCity = city
        binding.cityNameText.text = city
        
        // ذخیره شهر
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_city", city).apply()
        
        // بارگذاری مجدد
        loadCurrentWeather()
        
        Toast.makeText(this, "🌍 $city", Toast.LENGTH_SHORT).show()
    }
    
    private fun loadCurrentWeather() {
        binding.cityNameText.text = currentCity
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // تلاش برای AQICN API (دمای واقعی لحظه‌ای)
                val aqicnData = AqicnWeatherAPI.getWeatherByCity(currentCity)
                
                if (aqicnData != null) {
                    // دمای واقعی دریافت شد
                    updateUIWithRealData(aqicnData)
                    
                    // ذخیره برای استفاده در داشبورد
                    val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                    prefs.edit()
                        .putFloat("current_temp_$currentCity", aqicnData.temp.toFloat())
                        .putLong("temp_timestamp_$currentCity", System.currentTimeMillis())
                        .apply()
                    
                    android.util.Log.d("ImprovedWeather", "✅ Live temp for $currentCity: ${aqicnData.temp}°C")
                } else {
                    // داده‌های تخمینی
                    val estimatedData = AqicnWeatherAPI.getEstimatedWeatherForCity(currentCity)
                    updateUIWithEstimatedData(estimatedData)
                    
                    android.util.Log.w("ImprovedWeather", "⚠️ Using estimated data for $currentCity")
                }
                
                binding.progressBar.visibility = View.GONE
                
            } catch (e: Exception) {
                android.util.Log.e("ImprovedWeather", "Error loading weather", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ImprovedWeatherActivity, 
                    "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateUIWithRealData(data: AqicnWeatherAPI.WeatherData) {
        // دمای فعلی
        binding.currentTempText.text = "${data.temp.roundToInt()}°"
        binding.weatherIcon.text = AqicnWeatherAPI.getWeatherEmoji(data.temp)
        binding.weatherDescText.text = getWeatherDescription(data.temp)
        
        // جزئیات
        binding.humidityText.text = "رطوبت: ${data.humidity}%"
        binding.windSpeedText.text = "باد: ${data.windSpeed.roundToInt()} km/h"
        binding.feelsLikeText.text = "حس می‌شود: ${(data.temp + 2).roundToInt()}°"
        
        // AQI
        binding.aqiText.text = "کیفیت هوا: ${data.aqi}"
        binding.aqiProgressBar.progress = data.aqi.coerceIn(0, 500)
        
        // رنگ AQI
        val aqiColor = when {
            data.aqi <= 50 -> android.graphics.Color.GREEN
            data.aqi <= 100 -> android.graphics.Color.YELLOW
            data.aqi <= 150 -> android.graphics.Color.parseColor("#FFA500") // Orange
            data.aqi <= 200 -> android.graphics.Color.RED
            else -> android.graphics.Color.parseColor("#800080") // Purple
        }
        binding.aqiProgressBar.progressTintList = android.content.res.ColorStateList.valueOf(aqiColor)
        
        // زمان به‌روزرسانی
        val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale("fa", "IR"))
            .format(java.util.Date())
        binding.updateTimeText.text = "به‌روزرسانی: $now"
    }
    
    private fun updateUIWithEstimatedData(data: AqicnWeatherAPI.WeatherData) {
        binding.currentTempText.text = "${data.temp.roundToInt()}°"
        binding.weatherIcon.text = AqicnWeatherAPI.getWeatherEmoji(data.temp)
        binding.weatherDescText.text = getWeatherDescription(data.temp) + " (تخمینی)"
        
        binding.humidityText.text = "رطوبت: ${data.humidity}%"
        binding.windSpeedText.text = "باد: ${data.windSpeed.roundToInt()} km/h"
        binding.feelsLikeText.text = "حس می‌شود: ${(data.temp + 2).roundToInt()}°"
        
        binding.aqiText.text = "کیفیت هوا: نامشخص"
        binding.aqiProgressBar.visibility = View.GONE
        
        binding.updateTimeText.text = "داده‌های تخمینی"
    }
    
    private fun getWeatherDescription(temp: Double): String {
        return when {
            temp < 0 -> "❄️ سرد و یخبندان"
            temp < 10 -> "🌡️ سرد"
            temp < 20 -> "🍃 خنک"
            temp < 30 -> "☀️ معتدل"
            else -> "🔥 گرم"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
