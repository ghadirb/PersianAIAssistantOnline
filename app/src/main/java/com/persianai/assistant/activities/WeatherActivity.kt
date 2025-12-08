package com.persianai.assistant.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.databinding.ActivityWeatherFinalBinding
import com.persianai.assistant.databinding.ItemHourlyWeatherBinding
import com.persianai.assistant.utils.SharedDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherFinalBinding
    private var currentCity = "تهران"
    private val popularCities = listOf(
        "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "اهواز", "قم",
        "کرمان", "ارومیه", "رشت", "زاهدان", "همدان", "کرمانشاه", "یزد",
        "اردبیل", "بندرعباس", "اراک", "زنجان", "قزوین", "سنندج", "گرگان",
        "نیشابور", "خرم‌آباد", "ساری", "کاشان"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWeatherFinalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🌤️ آب و هوا"

        val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        currentCity = prefs.getString("selected_city", "تهران") ?: "تهران"
        binding.cityNameText.text = currentCity

        setupSearchBar()
        setupQuickCities()
        setupButtons()

        loadWeather()
    }

    private fun setupButtons() {
        binding.forecastButton.setOnClickListener {
            startActivity(android.content.Intent(this, WeatherForecastActivity::class.java).apply {
                putExtra("city", currentCity)
            })
        }

        binding.hourlyButton.setOnClickListener {
            binding.hourlyCard.visibility =
                if (binding.hourlyCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (binding.hourlyCard.visibility == View.VISIBLE) {
                loadHourlyForecast()
            }
        }
    }

    private fun setupSearchBar() {
        binding.searchCityButton.setOnClickListener { showCitySearchDialog() }
    }

    private fun showCitySearchDialog() {
        val allCities = (popularCities + listOf(
            "آمل", "بوشهر", "بیرجند", "چالوس", "دزفول", "رامسر", "سبزوار", "سمنان",
            "شهرکرد", "قزوین", "کاشان", "گرگان", "مشهد", "یاسوج"
        )).distinct().sorted()

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, allCities)
        val input = android.widget.EditText(this).apply {
            hint = "🔍 جستجو در ${allCities.size} شهر..."
            setPadding(32, 24, 32, 24)
            textSize = 16f
        }
        val listView = android.widget.ListView(this).apply { this.adapter = adapter }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            addView(input)
            addView(listView)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("🌍 انتخاب شهر")
            .setView(layout)
            .setNegativeButton("بستن", null)
            .create()

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter.filter(s)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val city = adapter.getItem(position) ?: return@setOnItemClickListener
            selectCity(city)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupQuickCities() {
        binding.quickCitiesLayout.removeAllViews()
        popularCities.take(10).forEach { city ->
            val chip = Chip(this).apply {
                text = city
                isClickable = true
                setOnClickListener { selectCity(city) }
            }
            binding.quickCitiesLayout.addView(chip)
        }
    }

    private fun selectCity(city: String) {
        try {
            currentCity = city
            binding.cityNameText.text = city
            val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
            prefs.edit().putString("selected_city", city).apply()
            loadWeather(forceFresh = true)
            Toast.makeText(this, "🌍 $city", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Error selecting city", e)
            Toast.makeText(this, "خطا در انتخاب شهر", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadWeather(forceFresh: Boolean = false) {
        if (forceFresh) WorldWeatherAPI.clearCache()
        loadCurrentWeather()
    }

    private fun loadCurrentWeather() {
        lifecycleScope.launch {
            try {
                val prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                val weatherData = WorldWeatherAPI.getCurrentWeather(currentCity)

                if (weatherData != null) {
                    binding.tempText.text = "${weatherData.temp.roundToInt()}°"
                    loadIcon(binding.weatherIcon, weatherData.iconUrl, weatherData.icon)
                    binding.weatherDescText.text = weatherData.description
                    binding.humidityText.text = "${weatherData.humidity}%"
                    binding.windSpeedText.text = "${weatherData.windSpeed.roundToInt()} km/h"
                    binding.feelsLikeText.text = "حس ${weatherData.feelsLike.roundToInt()}°"

                    prefs.edit().putFloat("current_temp_$currentCity", weatherData.temp.toFloat()).apply()
                    prefs.edit().putString("weather_icon_$currentCity", weatherData.icon).apply()
                    prefs.edit().putString("weather_desc_$currentCity", weatherData.description).apply()
                    prefs.edit().putInt("weather_humidity_$currentCity", weatherData.humidity).apply()
                    prefs.edit().putFloat("weather_wind_$currentCity", weatherData.windSpeed.toFloat()).apply()

                    SharedDataManager.saveWeatherData(
                        this@WeatherActivity,
                        currentCity,
                        weatherData.temp.toFloat(),
                        weatherData.description,
                        WorldWeatherAPI.getWeatherEmoji(weatherData.icon)
                    )
                } else {
                    val savedTemp = prefs.getFloat("current_temp_$currentCity", 25f)
                    val savedIcon = prefs.getString("weather_icon_$currentCity", "113") ?: "113"
                    val savedDesc = prefs.getString("weather_desc_$currentCity", "آفتابی") ?: "آفتابی"
                    val savedHumidity = prefs.getInt("weather_humidity_$currentCity", 45)
                    val savedWind = prefs.getFloat("weather_wind_$currentCity", 12f)

                    binding.tempText.text = "${savedTemp.roundToInt()}°"
                    loadIcon(binding.weatherIcon, null, savedIcon)
                    binding.weatherDescText.text = savedDesc
                    binding.humidityText.text = "$savedHumidity%"
                    binding.windSpeedText.text = "${savedWind.roundToInt()} km/h"
                    binding.feelsLikeText.text = "حس ${(savedTemp + 2).roundToInt()}°"
                }

                loadHourlyForecast()
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Error loading weather", e)
                Toast.makeText(this@WeatherActivity, "خطا در دریافت اطلاعات", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHourlyForecast() {
        lifecycleScope.launch {
            try {
                val forecasts = WorldWeatherAPI.getForecast(currentCity, 1)
                val hourlyData = if (forecasts.isNotEmpty() && forecasts[0].hourly.isNotEmpty()) {
                    forecasts[0].hourly.take(12).map { hourly ->
                        val timeStr = hourly.time.padStart(4, '0')
                        val formattedTime = "${timeStr.substring(0, 2)}:${timeStr.substring(2, 4)}"
                        HourlyWeatherData(
                            time = formattedTime,
                            temp = hourly.temp.roundToInt(),
                            icon = hourly.icon,
                            chanceOfRain = hourly.chanceOfRain
                        )
                    }
                } else emptyList()

                withContext(Dispatchers.Main) {
                    binding.hourlyRecyclerView.layoutManager = LinearLayoutManager(
                        this@WeatherActivity,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    binding.hourlyRecyclerView.adapter = HourlyWeatherAdapter(hourlyData)
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Error loading hourly forecast", e)
            }
        }
    }

    data class HourlyWeatherData(
        val time: String,
        val temp: Int,
        val icon: String,
        val chanceOfRain: Int
    )

    inner class HourlyWeatherAdapter(
        private val items: List<HourlyWeatherData>
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<HourlyWeatherAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemHourlyWeatherBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemHourlyWeatherBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                timeText.text = item.time
                iconText.text = WorldWeatherAPI.getWeatherEmoji(item.icon)
                tempText.text = "${item.temp}°"
                rainChanceText.text = "${item.chanceOfRain}%"
                rainChanceText.visibility = if (item.chanceOfRain > 0) View.VISIBLE else View.GONE
            }
        }

        override fun getItemCount() = items.size
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadIcon(textView: android.widget.TextView, code: String) {
        textView.text = WorldWeatherAPI.getWeatherEmoji(code)
    }
}
