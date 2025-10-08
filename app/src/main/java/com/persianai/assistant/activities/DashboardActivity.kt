package com.persianai.assistant.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.api.AqicnWeatherAPI
import com.persianai.assistant.databinding.ActivityMainDashboardBinding
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.AnimationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        
        // Hide all cards initially
        hideAllCards()
        
        setupDate()
        setupClickListeners()
        loadWeather()
        animateCards()
    }
    
    private fun hideAllCards() {
        binding.calendarCard?.alpha = 0f
        binding.weatherCard?.alpha = 0f
        binding.aiChatCard?.alpha = 0f
        binding.musicCard?.alpha = 0f
        binding.aboutCard?.alpha = 0f
    }
    
    private fun setupDate() {
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val dayOfWeek = getDayOfWeek()
        
        // تاریخ فارسی
        binding.persianDateText.text = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
        
        // تاریخ میلادی  
        val gregorianDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date())
        binding.gregorianDateText?.text = gregorianDate
    }
    
    private fun getDayOfWeek(): String {
        val days = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")
        val calendar = Calendar.getInstance()
        val dayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
        return days[dayIndex]
    }
    
    private fun setupClickListeners() {
        binding.calendarCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, CalendarActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.weatherCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, WeatherActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.aiChatCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.musicCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, MusicActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.aboutCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showAboutDialog()
            }, 150)
        }
    }
    
    private fun loadWeather() {
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        // نمایش نام شهر
        binding.weatherCityName?.text = city
        
        lifecycleScope.launch {
            try {
                // ابتدا از AQICN API استفاده کن
                val aqicnData = AqicnWeatherAPI.getCurrentWeather()
                
                if (aqicnData != null) {
                    android.util.Log.d("DashboardActivity", "AQICN weather loaded: ${aqicnData.temp}°C")
                    binding.weatherTempText?.text = "${aqicnData.temp.roundToInt()}°"
                    binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(aqicnData.temp)
                    
                    // نمایش کیفیت هوا اگر موجود است
                    val aqiText = AqicnWeatherAPI.getAqiText(aqicnData.aqi)
                    // نمایش AQI در لاگ (فعلاً TextView مربوطه در layout وجود ندارد)
                    android.util.Log.d("DashboardActivity", "AQI: ${aqicnData.aqi} - $aqiText")
                } else {
                    // اگر AQICN جواب نداد، از OpenWeatherAPI استفاده کن
                    val weatherData = OpenWeatherAPI.getCurrentWeather(city)
                    
                    if (weatherData != null) {
                        binding.weatherTempText?.text = "${weatherData.temp.roundToInt()}°"
                        binding.weatherIcon?.text = OpenWeatherAPI.getWeatherEmoji(weatherData.icon)
                    } else {
                        // در نهایت از داده‌های تخمینی AQICN استفاده کن
                        val estimatedData = AqicnWeatherAPI.getEstimatedWeatherForCity(city)
                        binding.weatherTempText?.text = "${estimatedData.temp.roundToInt()}°"
                        binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(estimatedData.temp)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error loading weather", e)
                // استفاده از داده‌های تخمینی واقعی‌تر
                val estimatedData = AqicnWeatherAPI.getEstimatedWeatherForCity(city)
                binding.weatherTempText?.text = "${estimatedData.temp.roundToInt()}°"
                binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(estimatedData.temp)
            }
        }
        
        // دکمه‌های پیش‌بینی
        binding.hourlyBtn?.setOnClickListener {
            android.util.Log.d("DashboardActivity", "Hourly button clicked")
            try {
                val intent = Intent(this, WeatherActivity::class.java)
                intent.putExtra("SHOW_HOURLY", true)
                intent.putExtra("city", city)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error opening hourly weather", e)
                Toast.makeText(this, "خطا در نمایش پیش‌بینی ساعتی", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.weeklyBtn?.setOnClickListener {
            android.util.Log.d("DashboardActivity", "Weekly button clicked")
            try {
                val intent = Intent(this, WeatherForecastActivity::class.java)
                intent.putExtra("city", city)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error opening weekly forecast", e)
                Toast.makeText(this, "خطا در نمایش پیش‌بینی هفتگی", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("درباره برنامه")
            .setMessage("""
                دستیار هوشمند فارسی
                نسخه 1.0.0
                
                ویژگی‌ها:
                ✨ تقویم فارسی با مناسبت‌ها
                🌤️ پیش‌بینی آب و هوا  
                🤖 چت با هوش مصنوعی
                🕌 اوقات شرعی
                📅 یادآوری و رویدادها
                
                توسعه‌دهنده: تیم دستیار فارسی
            """.trimIndent())
            .setPositiveButton("بستن", null)
            .setNeutralButton("راهنما") { _, _ ->
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.putExtra("SHOW_HELP", true)
                startActivity(intent)
            }
            .show()
    }
    
    private fun animateCards() {
        // Staggered fade in animation for cards
        val cards = listOfNotNull(
            binding.calendarCard,
            binding.weatherCard,
            binding.aiChatCard,
            binding.musicCard,
            binding.aboutCard
        )
        
        AnimationHelper.animateListItems(cards, delayBetween = 100)
        
        // Add pulse animation to weather card to draw attention
        binding.weatherCard?.postDelayed({
            AnimationHelper.pulseAnimation(binding.weatherCard!!, scaleFactor = 1.05f, duration = 2000)
        }, 1000)
    }
}
