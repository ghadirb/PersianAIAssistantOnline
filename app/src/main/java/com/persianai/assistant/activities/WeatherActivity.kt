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
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.utils.SharedDataManager
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {
    
    // استفاده از findViewById به جای binding
    private var currentCity = "تهران"
    private val popularCities = listOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم", 
        "کرمان", "ارومیه", "رشت", "زاهدان", "همدان", "کرمانشاه", "یزد", "اردبیل", "بندرعباس", 
        "اراک", "زنجان", "قزوین", "سنندج", "گرگان", "نیشابور", "خرم‌آباد", "ساری", "کاشان")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // استفاده از layout نهایی یکسان با داشبورد
        setContentView(R.layout.activity_weather_final)
        
        // تنظیم Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"

        // بارگذاری شهر ذخیره شده
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        currentCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        findViewById<android.widget.TextView>(R.id.cityNameText).text = currentCity
        
        setupSearchBar()
        setupQuickCities()
        
        // دکمه پیش‌بینی 7 روزه
        findViewById<com.google.android.material.button.MaterialButton>(R.id.forecastButton)?.setOnClickListener {
            try {
                val intent = Intent(this, WeatherForecastActivity::class.java)
                intent.putExtra("city", currentCity)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "خطا در باز کردن پیش‌بینی", Toast.LENGTH_SHORT).show()
            }
        }
        
        // دکمه پیش‌بینی ساعتی
        findViewById<com.google.android.material.button.MaterialButton>(R.id.hourlyButton)?.setOnClickListener {
            val hourlyCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.hourlyCard)
            hourlyCard.visibility = if (hourlyCard.visibility == android.view.View.VISIBLE) {
                android.view.View.GONE
            } else {
                loadHourlyForecast()
                android.view.View.VISIBLE
            }
        }
        
        loadWeather()
    }
    
    private fun setupSearchBar() {
        // دکمه جستجو - MaterialButton
        findViewById<com.google.android.material.button.MaterialButton>(R.id.searchCityButton)?.setOnClickListener {
            showCitySearchDialog()
        }
    }
    
    private fun showCitySearchDialog() {
        val allCities = popularCities + listOf(
            "آمل", "بوشهر", "بیرجند", "چالوس", "دزفول", "رامسر", "سبزوار", "سمنان",
            "شهرکرد", "قزوین", "کاشان", "گرگان", "مشهد", "یاسوج"
        ).distinct().sorted()
        
        val input = android.widget.EditText(this).apply {
            hint = "🔍 جستجو در ${allCities.size} شهر..."
            setPadding(32, 24, 32, 24)
            textSize = 16f
        }
        
        val listView = android.widget.ListView(this)
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, allCities)
        listView.adapter = adapter
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(input)
            addView(listView)
        }
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🌍 انتخاب شهر")
            .setView(layout)
            .setNegativeButton("بستن", null)
            .create()
        
        // Filter cities as user types
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Handle city selection
        listView.setOnItemClickListener { _, _, position, _ ->
            try {
                val city = adapter.getItem(position) ?: return@setOnItemClickListener
                currentCity = city
                findViewById<android.widget.TextView>(R.id.cityNameText)?.text = city
                
                val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                prefs.edit().putString("selected_city", city).apply()
                
                dialog.dismiss()
                loadWeather(forceFresh = true)
                Toast.makeText(this, "🌍 $city", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Error selecting city from dialog", e)
                Toast.makeText(this, "خطا در انتخاب شهر", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }
    
    private fun setupQuickCities() {
        val quickCitiesLayout = findViewById<com.google.android.material.chip.ChipGroup>(R.id.quickCitiesLayout)
        popularCities.take(10).forEach { city ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = city
                isClickable = true
                setOnClickListener {
                    try {
                        currentCity = city
                        findViewById<android.widget.TextView>(R.id.cityNameText)?.text = city
                        
                        // ذخیره شهر
                        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                        prefs.edit().putString("selected_city", city).apply()
                        
                        // بارگذاری مجدد با try-catch
                        loadWeather(forceFresh = true)
                        Toast.makeText(this@WeatherActivity, "🌍 $city", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("WeatherActivity", "Error selecting city", e)
                        Toast.makeText(this@WeatherActivity, "خطا در انتخاب شهر", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            quickCitiesLayout?.addView(chip)
        }
    }
    private fun loadWeather(forceFresh: Boolean = false) {
        // پاک کردن کش در صورت نیاز
        if (forceFresh) {
            WorldWeatherAPI.clearCache()
        }
        loadCurrentWeather()
    }
    
    private fun loadCurrentWeather() {
        lifecycleScope.launch {
            try {
                // دریافت دمای واقعی از WorldWeatherOnline API
                val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                
                val weatherData = WorldWeatherAPI.getCurrentWeather(currentCity)
                
                if (weatherData != null) {
                    android.util.Log.d("WeatherActivity", "Live weather from WorldWeather: ${weatherData.temp}°C")
                    
                    // نمایش اطلاعات
                    findViewById<android.widget.TextView>(R.id.tempText)?.text = "${weatherData.temp.roundToInt()}°"
                    findViewById<android.widget.TextView>(R.id.weatherIcon)?.text = getWeatherEmoji(weatherData.temp)
                    findViewById<android.widget.TextView>(R.id.weatherDescText)?.text = weatherData.description
                    findViewById<android.widget.TextView>(R.id.humidityText)?.text = "${weatherData.humidity}%"
                    findViewById<android.widget.TextView>(R.id.windSpeedText)?.text = "${weatherData.windSpeed.roundToInt()} km/h"
                    findViewById<android.widget.TextView>(R.id.feelsLikeText)?.text = "حس ${weatherData.feelsLike.roundToInt()}°"
                    
                    // ذخیره دما
                    prefs.edit().putFloat("current_temp_$currentCity", weatherData.temp.toFloat()).apply()
                    prefs.edit().putString("weather_desc_$currentCity", weatherData.description).apply()
                    prefs.edit().putInt("weather_humidity_$currentCity", weatherData.humidity).apply()
                    prefs.edit().putFloat("weather_wind_$currentCity", weatherData.windSpeed.toFloat()).apply()
                    
                    // Sync با SharedDataManager
                    SharedDataManager.saveWeatherData(
                        this@WeatherActivity,
                        currentCity,
                        weatherData.temp.toFloat(),
                        weatherData.description,
                        getWeatherEmoji(weatherData.temp)
                    )
                    android.util.Log.d("WeatherActivity", "💾 Synced to SharedDataManager: $currentCity - ${weatherData.temp}°C")
                } else {
                    // استفاده از داده‌های ذخیره شده
                    val savedTemp = prefs.getFloat("current_temp_$currentCity", 25f)
                    val savedDesc = prefs.getString("weather_desc_$currentCity", "آفتابی")
                    val savedHumidity = prefs.getInt("weather_humidity_$currentCity", 45)
                    val savedWind = prefs.getFloat("weather_wind_$currentCity", 12f)
                    
                    findViewById<android.widget.TextView>(R.id.tempText)?.text = "${savedTemp.roundToInt()}°"
                    findViewById<android.widget.TextView>(R.id.weatherIcon)?.text = getWeatherEmoji(savedTemp.toDouble())
                    findViewById<android.widget.TextView>(R.id.weatherDescText)?.text = savedDesc
                    findViewById<android.widget.TextView>(R.id.humidityText)?.text = "$savedHumidity%"
                    findViewById<android.widget.TextView>(R.id.windSpeedText)?.text = "${savedWind.roundToInt()} km/h"
                    findViewById<android.widget.TextView>(R.id.feelsLikeText)?.text = "حس ${(savedTemp + 2).roundToInt()}°"
                }
                
                // بارگذاری پیش‌بینی ساعتی
                loadHourlyForecast()
                
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Error loading weather", e)
                Toast.makeText(this@WeatherActivity, "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getWeatherEmoji(temp: Double): String {
        return when {
            temp < 0 -> "❄️"
            temp < 10 -> "🌨️"
            temp < 20 -> "⛅"
            temp < 30 -> "☀️"
            else -> "🔥"
        }
    }
    
    private fun loadHourlyForecast() {
        // ایجاد Mock Data برای پیش‌بینی ساعتی
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hourlyLayout = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.hourlyRecyclerView)
        
        // اگر RecyclerView وجود نداره، از روش ساده استفاده کن
        if (hourlyLayout == null) {
            // شاید باید Layout Manager اضافه کنیم
            return
        }
        
        // ایجاد داده‌های ساعتی (12 ساعت آینده)
        val hourlyData = mutableListOf<HourlyWeatherData>()
        for (i in 0..11) {
            val hour = (currentHour + i) % 24
            val temp = 25 + (Math.random() * 10 - 5).toInt() // دمای تصادفی بین 20-30
            val icon = when {
                hour in 6..10 -> "☀️"
                hour in 11..15 -> "⛅"
                hour in 16..18 -> "☁️"
                hour in 19..21 -> "🌙"
                else -> "⭐"
            }
            
            hourlyData.add(HourlyWeatherData(
                time = String.format("%02d:00", hour),
                temp = temp,
                icon = icon
            ))
        }
        
        // نمایش در RecyclerView
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
            this, 
            androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, 
            false
        )
        hourlyLayout.layoutManager = layoutManager
        hourlyLayout.adapter = HourlyWeatherAdapter(hourlyData)
    }
    
    // Data class برای هر ساعت
    data class HourlyWeatherData(
        val time: String,
        val temp: Int,
        val icon: String
    )
    
    // Adapter برای RecyclerView
    inner class HourlyWeatherAdapter(
        private val items: List<HourlyWeatherData>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: android.view.View) : 
            androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            val timeText: android.widget.TextView = itemView.findViewById(android.R.id.text1)
            val iconText: android.widget.TextView = itemView.findViewById(android.R.id.text2)
            val tempText: android.widget.TextView = itemView.findViewById(android.R.id.hint)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            // ایجاد یک View ساده
            val linearLayout = android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(24, 16, 24, 16)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // اضافه کردن TextView ها
            val timeText = android.widget.TextView(parent.context).apply {
                id = android.R.id.text1
                textSize = 12f
                gravity = android.view.Gravity.CENTER
            }
            
            val iconText = android.widget.TextView(parent.context).apply {
                id = android.R.id.text2
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }
            
            val tempText = android.widget.TextView(parent.context).apply {
                id = android.R.id.hint
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            linearLayout.addView(timeText)
            linearLayout.addView(iconText)
            linearLayout.addView(tempText)
            
            return ViewHolder(linearLayout)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.timeText.text = item.time
            holder.iconText.text = item.icon
            holder.tempText.text = "${item.temp}°"
        }
        
        override fun getItemCount() = items.size
    }
    
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
