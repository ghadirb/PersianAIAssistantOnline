package com.persianai.assistant.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.persianai.assistant.R
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.databinding.ActivityWeatherForecastBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class WeatherForecastActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherForecastBinding
    private lateinit var forecastAdapter: ForecastAdapter
    private var forecastType = "daily" // daily or monthly
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherForecastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📅 پیش‌بینی آب و هوا"
        
        setupTabs()
        setupRecyclerView()
        loadForecast()
    }
    
    private fun setupTabs() {
        // فقط 7 روزه - 30 روزه نادرست بود
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("📅 پیش‌بینی 7 روزه"))
        binding.tabLayout.visibility = View.GONE // فقط یک تب است
        forecastType = "daily"
    }
    
    private fun setupRecyclerView() {
        forecastAdapter = ForecastAdapter()
        binding.forecastRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WeatherForecastActivity)
            adapter = forecastAdapter
        }
    }
    
    private fun loadForecast() {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        binding.progressBar.visibility = View.VISIBLE
        binding.forecastRecyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                when (forecastType) {
                    "daily" -> loadDailyForecast(city)
                    "monthly" -> loadMonthlyForecast(city)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@WeatherForecastActivity, 
                    "خطا در دریافت پیش‌بینی", 
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.forecastRecyclerView.visibility = View.VISIBLE
            }
        }
    }
    
    private suspend fun loadDailyForecast(city: String) {
        val forecasts = WorldWeatherAPI.getForecast(city)
        
        if (forecasts.isNotEmpty()) {
            // گروه‌بندی بر اساس روز و گرفتن پیش‌بینی ظهر هر روز
            val dailyForecasts = mutableListOf<DailyForecast>()
            val today = Calendar.getInstance()
            
            // تبدیل به DailyForecast - بدون فیلتر چون WorldWeatherAPI فقط آینده بر میگرداند
            forecasts.take(7).forEach { forecast ->
                // پارس تاریخ از رشته (format: "2025-10-13")
                val dateParts = forecast.date.split("-")
                val cal = Calendar.getInstance()
                if (dateParts.size == 3) {
                    cal.set(dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt())
                }
                
                dailyForecasts.add(
                    DailyForecast(
                        date = cal.time,
                        tempMin = forecast.minTemp,
                        tempMax = forecast.maxTemp,
                        description = forecast.description,
                        icon = forecast.icon
                    )
                )
            }
            
            forecastAdapter.updateData(dailyForecasts)
        } else {
            // Mock data اگر API کار نکرد
            val mockForecasts = generateMockDailyForecast(city)
            forecastAdapter.updateData(mockForecasts)
        }
    }
    
    private suspend fun loadMonthlyForecast(city: String) {
        // برای 30 روز آینده mock data تولید می‌کنیم
        // API رایگان OpenWeather فقط 5 روز می‌دهد
        val monthlyForecasts = generateMockMonthlyForecast(city)
        forecastAdapter.updateData(monthlyForecasts)
    }
    
    private fun generateMockDailyForecast(city: String): List<DailyForecast> {
        val forecasts = mutableListOf<DailyForecast>()
        val calendar = Calendar.getInstance()
        
        for (i in 0..6) {
            val baseTemp = when (city) {
                "تهران" -> 25.0
                "مشهد" -> 22.0
                "اصفهان" -> 27.0
                "شیراز" -> 26.0
                "تبریز" -> 20.0
                else -> 24.0
            }
            
            val variation = (Math.random() * 10 - 5).roundToInt()
            forecasts.add(
                DailyForecast(
                    date = calendar.time,
                    tempMin = baseTemp + variation - 5,
                    tempMax = baseTemp + variation + 5,
                    description = listOf("آفتابی", "ابری", "نیمه ابری", "بارانی").random(),
                    icon = listOf("01d", "02d", "03d", "10d").random()
                )
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return forecasts
    }
    
    private fun generateMockMonthlyForecast(city: String): List<DailyForecast> {
        val forecasts = mutableListOf<DailyForecast>()
        val calendar = Calendar.getInstance()
        
        for (i in 0..29) {
            val month = calendar.get(Calendar.MONTH)
            val baseTemp = when (month) {
                Calendar.JANUARY, Calendar.FEBRUARY, Calendar.DECEMBER -> 10.0
                Calendar.MARCH, Calendar.APRIL, Calendar.OCTOBER, Calendar.NOVEMBER -> 18.0
                Calendar.MAY, Calendar.SEPTEMBER -> 25.0
                else -> 35.0 // تابستان
            }
            
            val variation = (Math.random() * 8 - 4).roundToInt()
            forecasts.add(
                DailyForecast(
                    date = calendar.time,
                    tempMin = baseTemp + variation - 6,
                    tempMax = baseTemp + variation + 6,
                    description = listOf("آفتابی", "ابری", "نیمه ابری", "بارانی", "برفی").random(),
                    icon = listOf("01d", "02d", "03d", "10d", "13d").random()
                )
            )
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return forecasts
    }
    
    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance()
        cal1.time = date1
        val cal2 = Calendar.getInstance()
        cal2.time = date2
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    // Data Classes
    data class DailyForecast(
        val date: Date,
        val tempMin: Double,
        val tempMax: Double,
        val description: String,
        val icon: String
    )
    
    // Adapter
    inner class ForecastAdapter : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {
        
        private var forecasts = listOf<DailyForecast>()
        
        fun updateData(newForecasts: List<DailyForecast>) {
            forecasts = newForecasts
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_forecast, parent, false)
            return ForecastViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
            holder.bind(forecasts[position])
        }
        
        override fun getItemCount() = forecasts.size
        
        inner class ForecastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val dateText: TextView = itemView.findViewById(R.id.forecastDate)
            private val iconText: TextView = itemView.findViewById(R.id.forecastIcon)
            private val tempText: TextView = itemView.findViewById(R.id.forecastTemp)
            private val descText: TextView = itemView.findViewById(R.id.forecastDescription)
            
            fun bind(forecast: DailyForecast) {
                try {
                    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("fa"))
                    dateText.text = dateFormat.format(forecast.date)
                } catch (e: Exception) {
                    // fallback to simple format
                    val cal = Calendar.getInstance()
                    cal.time = forecast.date
                    val dayNames = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")
                    val monthNames = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
                    dateText.text = "${dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${monthNames[cal.get(Calendar.MONTH)]}"
                }
                
                iconText.text = getWeatherEmoji(forecast.tempMax)
                
                tempText.text = "${forecast.tempMin.roundToInt()}° - ${forecast.tempMax.roundToInt()}°"
                
                descText.text = forecast.description
                
                // رنگ پس‌زمینه برای امروز
                if (isSameDay(forecast.date, Date())) {
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.holo_blue_light))
                }
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
}
