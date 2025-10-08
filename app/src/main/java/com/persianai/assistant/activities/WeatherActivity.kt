package com.persianai.assistant.activities

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.persianai.assistant.R
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.databinding.ActivityWeatherBinding
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherBinding
    private var currentCity = "تهران"
    private val popularCities = listOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم", 
        "کرمان", "ارومیه", "رشت", "زاهدان", "همدان", "کرمانشاه", "یزد", "اردبیل", "بندرعباس", 
        "اراک", "زنجان", "قزوین", "سنندج", "گرگان", "نیشابور", "خرم‌آباد", "ساری", "کاشان")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"

        // بارگذاری شهر ذخیره شده
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        currentCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        binding.cityNameText.text = currentCity
        
        setupSearchBar()
        setupQuickCities()
        
        // دکمه پیش‌بینی 7 روزه
        binding.forecastButton.setOnClickListener {
            try {
                val intent = Intent(this, WeatherForecastActivity::class.java)
                intent.putExtra("city", currentCity)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "خطا در باز کردن پیش‌بینی", Toast.LENGTH_SHORT).show()
            }
        }
        
        // دکمه refresh
        binding.refreshButton.setOnClickListener {
            loadWeather(forceFresh = true)
        }
        
        loadWeather()
    }
    
    private fun setupSearchBar() {
        // دکمه جستجو
        binding.searchButton.setOnClickListener {
            searchCity()
        }
        
        // جستجو با Enter
        binding.citySearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchCity()
                true
            } else {
                false
            }
        }
    }
    
    private fun searchCity() {
        val city = binding.citySearchInput.text?.toString()?.trim()
        if (!city.isNullOrEmpty()) {
            currentCity = city
            binding.cityNameText.text = city
            
            // ذخیره شهر جدید
            val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
            prefs.edit().putString("selected_city", city).apply()
            
            // پاک کردن input
            binding.citySearchInput.setText("")
            binding.citySearchInput.clearFocus()
            
            // مخفی کردن کیبورد
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.citySearchInput.windowToken, 0)
            
            // بارگذاری آب و هوا
            loadWeather(forceFresh = true)
        }
    }
    
    private fun setupQuickCities() {
        popularCities.take(10).forEach { city ->
            val button = Button(this).apply {
                text = city
                setBackgroundResource(android.R.drawable.btn_default)
                setPadding(32, 16, 32, 16)
                setOnClickListener {
                    currentCity = city
                    binding.cityNameText.text = city
                    
                    // ذخیره شهر
                    val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                    prefs.edit().putString("selected_city", city).apply()
                    
                    loadWeather()
                }
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            button.layoutParams = params
            
            binding.quickCitiesLayout.addView(button)
        }
    }
    
    private fun loadWeather(forceFresh: Boolean = false) {
        // پاک کردن کش در صورت نیاز
        if (forceFresh) {
            OpenWeatherAPI.clearCache()
        }
        
        lifecycleScope.launch {
            try {
                val weather = OpenWeatherAPI.getCurrentWeather(currentCity)
                
                if (weather != null) {
                    // نمایش داده‌های واقعی
                    binding.tempText.text = "${weather.temp.roundToInt()}°"
                    binding.descText.text = weather.description
                    binding.humidityText.text = "${weather.humidity}%"
                    binding.windText.text = "${weather.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "${weather.feelsLike.roundToInt()}°"
                } else {
                    // استفاده از Mock Data
                    val mockWeather = OpenWeatherAPI.getMockWeatherData(currentCity)
                    binding.tempText.text = "${mockWeather.temp.roundToInt()}°"
                    binding.descText.text = mockWeather.description
                    binding.humidityText.text = "${mockWeather.humidity}%"
                    binding.windText.text = "${mockWeather.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "${mockWeather.feelsLike.roundToInt()}°"
                    
                    Toast.makeText(this, "⚠️ استفاده از داده‌های آفلاین", Toast.LENGTH_SHORT).show()
                }
                
                // بارگذاری پیش‌بینی ساعتی
                loadHourlyForecast()
                
            } catch (e: Exception) {
                Toast.makeText(this, "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadHourlyForecast() {
        lifecycleScope.launch {
            try {
                val hourlyData = OpenWeatherAPI.getHourlyForecast(currentCity)
                // نمایش در RecyclerView (باید اضافه شود)
                // binding.hourlyRecyclerView.adapter = HourlyAdapter(hourlyData)
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Error loading hourly forecast", e)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
