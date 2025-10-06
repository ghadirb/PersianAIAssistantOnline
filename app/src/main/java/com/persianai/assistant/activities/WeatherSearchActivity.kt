package com.persianai.assistant.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityWeatherSearchBinding
import com.persianai.assistant.utils.WeatherAPI
import com.persianai.assistant.R

class WeatherSearchActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherSearchBinding
    private var currentCity = "تهران"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        loadSavedCity()
        setupSearchButton()
        updateWeatherDisplay()
    }
    
    private fun loadSavedCity() {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        currentCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        binding.searchCityEdit.setText(currentCity)
    }
    
    private fun setupSearchButton() {
        binding.searchBtn.setOnClickListener {
            val searchText = binding.searchCityEdit.text.toString().trim()
            if (searchText.isNotEmpty()) {
                currentCity = searchText
                saveCity(currentCity)
                updateWeatherDisplay()
            }
        }
        
        binding.searchCityEdit.setOnEditorActionListener { _, _, _ ->
            binding.searchBtn.performClick()
            true
        }
    }
    
    private fun saveCity(city: String) {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_city", city).apply()
    }
    
    private fun updateWeatherDisplay() {
        // Update current weather
        binding.cityNameText.text = currentCity
        binding.currentTempText.text = "${WeatherAPI.getTemperature()}"
        binding.weatherDescText.text = WeatherAPI.getDescription()
        binding.minTempText.text = "حداقل: ${WeatherAPI.getMinTemp()}°"
        binding.maxTempText.text = "حداکثر: ${WeatherAPI.getMaxTemp()}°"
        
        // Update hourly forecast
        updateHourlyForecast()
        
        // Update daily forecast
        updateDailyForecast()
    }
    
    private fun updateHourlyForecast() {
        binding.hourlyForecastContainer.removeAllViews()
        
        val hours = listOf("12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00")
        val temps = listOf("25°", "26°", "27°", "28°", "28°", "27°", "26°", "24°")
        
        for (i in hours.indices) {
            val itemView = LayoutInflater.from(this).inflate(
                android.R.layout.simple_list_item_2, 
                binding.hourlyForecastContainer, 
                false
            )
            
            val hourView = itemView.findViewById<TextView>(android.R.id.text1)
            val tempView = itemView.findViewById<TextView>(android.R.id.text2)
            
            hourView?.text = hours[i]
            tempView?.text = temps[i]
            
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 8, 16, 8)
                addView(TextView(context).apply {
                    text = hours[i]
                    textSize = 14f
                })
                addView(TextView(context).apply {
                    text = temps[i]
                    textSize = 18f
                })
            }
            
            binding.hourlyForecastContainer.addView(container)
        }
    }
    
    private fun updateDailyForecast() {
        binding.dailyForecastContainer.removeAllViews()
        
        val days = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
        val maxTemps = listOf("28°", "27°", "26°", "25°", "26°", "27°", "28°")
        val minTemps = listOf("18°", "17°", "16°", "15°", "16°", "17°", "18°")
        
        for (i in days.indices) {
            val dayView = TextView(this).apply {
                text = "${days[i]}: ${minTemps[i]} - ${maxTemps[i]}"
                textSize = 16f
                setPadding(0, 8, 0, 8)
            }
            binding.dailyForecastContainer.addView(dayView)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
