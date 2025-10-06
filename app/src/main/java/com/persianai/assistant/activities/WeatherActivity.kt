package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.persianai.assistant.databinding.ActivityWeatherBinding
import com.persianai.assistant.utils.CityManager

class WeatherActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWeatherBinding
    private var selectedCity = "تهران"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"
        
        setupCitySpinner()
        loadSelectedCity()
        updateWeather()
    }
    
    private fun setupCitySpinner() {
        val cityNames = CityManager.cities.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cityNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.citySpinner.adapter = adapter
        
        binding.citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCity = cityNames[position]
                saveSelectedCity()
                updateWeather()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun loadSelectedCity() {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        selectedCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        val position = CityManager.cities.indexOfFirst { it.name == selectedCity }
        if (position >= 0) {
            binding.citySpinner.setSelection(position)
        }
    }
    
    private fun saveSelectedCity() {
        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        prefs.edit().putString("selected_city", selectedCity).apply()
    }
    
    private fun updateWeather() {
        // نمایش موقت
        binding.tempText.text = "${com.persianai.assistant.utils.WeatherAPI.getTemperature()}\n$selectedCity"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
