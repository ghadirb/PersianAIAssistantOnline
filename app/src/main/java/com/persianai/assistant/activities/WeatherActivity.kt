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
import com.persianai.assistant.api.AqicnWeatherAPI
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
        // استفاده از layout جدید
        setContentView(R.layout.activity_weather_updated)
        binding = ActivityWeatherBinding.bind(findViewById(android.R.id.content))
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
    }
    
    private fun loadCurrentWeather() {
        lifecycleScope.launch {
            try {
                // ابتدا از AQICN API استفاده کن
                val aqicnData = AqicnWeatherAPI.getWeatherByCity(currentCity)
                
                if (aqicnData != null) {
                    android.util.Log.d("WeatherActivity", "AQICN data loaded: ${aqicnData.temp}°C")
                    updateUIWithAqicnData(aqicnData)
                } else {
                    // اگر AQICN جواب نداد، از OpenWeatherAPI استفاده کن
                    val weatherData = OpenWeatherAPI.getCurrentWeather(currentCity)
                    // استفاده از Mock Data
                    val mockWeather = OpenWeatherAPI.getMockWeatherData(currentCity)
                    binding.tempText.text = "${mockWeather.temp.roundToInt()}°"
                    binding.descText.text = mockWeather.description
                    binding.humidityText.text = "${mockWeather.humidity}%"
                    binding.windText.text = "${mockWeather.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "${mockWeather.feelsLike.roundToInt()}°"
                    
                    Toast.makeText(this@WeatherActivity, "⚠️ استفاده از داده‌های آفلاین", Toast.LENGTH_SHORT).show()
                }
                
                // بارگذاری پیش‌بینی ساعتی
                loadHourlyForecast()
                
            } catch (e: Exception) {
                Toast.makeText(this@WeatherActivity, "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadHourlyForecast() {
        // ایجاد Mock Data برای پیش‌بینی ساعتی
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val hourlyLayout = binding.hourlyForecastRecycler
        
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
    
    private fun updateUIWithAqicnData(data: AqicnWeatherAPI.WeatherData) {
        // آپدیت دما
        binding.tempText?.text = "${data.temp.roundToInt()}°"
        binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(data.temp)
        
        // آپدیت رطوبت
        binding.humidityText?.text = "${data.humidity}%"
        
        // آپدیت سرعت باد
        binding.windSpeedText?.text = "${data.windSpeed.roundToInt()} km/h"
        
        // آپدیت فشار هوا
        binding.feelsLikeText?.text = "حس ${(data.temp + 2).roundToInt()}°"
        
        // نمایش کیفیت هوا
        binding.aqiValueText?.text = "AQI: ${data.aqi}"
        binding.aqiStatusText?.text = AqicnWeatherAPI.getAqiText(data.aqi)
        binding.aqiProgressBar?.progress = data.aqi
        
        // رنگ بندی بر اساس کیفیت هوا
        val aqiColor = android.graphics.Color.parseColor(AqicnWeatherAPI.getAqiColor(data.aqi))
        binding.aqiProgressBar?.progressDrawable?.setColorFilter(
            aqiColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        
        // توضیحات آب و هوا
        binding.weatherDescText?.text = when {
            data.temp < 10 -> "سرد"
            data.temp < 20 -> "خنک"
            data.temp < 30 -> "معتدل"
            data.temp < 35 -> "گرم"
            else -> "بسیار گرم"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
