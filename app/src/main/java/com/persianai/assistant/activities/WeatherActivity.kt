package com.persianai.assistant.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityWeatherBinding

class WeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        binding.tempText.text = com.persianai.assistant.utils.WeatherAPI.getTemperature()
        binding.descText.text = com.persianai.assistant.utils.WeatherAPI.getDescription()
        binding.aqiText.text = "کیفیت هوا: ${com.persianai.assistant.utils.WeatherAPI.getAQI()}"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
