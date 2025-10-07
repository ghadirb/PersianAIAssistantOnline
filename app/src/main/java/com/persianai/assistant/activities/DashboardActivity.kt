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
import com.persianai.assistant.databinding.ActivityMainDashboardBinding
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var prefs: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        
        setupDate()
        setupClickListeners()
        loadWeather()
        animateCards()
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
        // تقویم
        binding.calendarCard.setOnClickListener {
            startActivityWithAnimation(CalendarActivity::class.java)
        }
        
        // آب و هوا
        binding.weatherCard.setOnClickListener {
            startActivityWithAnimation(WeatherSearchActivity::class.java)
        }
        
        // چت AI
        binding.aiChatCard.setOnClickListener {
            startActivityWithAnimation(MainActivity::class.java)
        }
        
        // درباره برنامه
        binding.aboutCard.setOnClickListener {
            showAboutDialog()
        }
    }
    
    private fun startActivityWithAnimation(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
    
    private fun loadWeather() {
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        lifecycleScope.launch {
            try {
                // استفاده از Mock Data فعلاً
                val weatherData = OpenWeatherAPI.getMockWeatherData(city)
                
                binding.weatherTempText.text = "${weatherData.temp.toInt()}°C"
                binding.weatherIcon.text = OpenWeatherAPI.getWeatherEmoji(weatherData.icon)
                
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error loading weather", e)
                binding.weatherTempText.text = "25°C"
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
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        
        binding.calendarCard.startAnimation(fadeIn)
        binding.weatherCard.startAnimation(fadeIn)
        binding.aiChatCard.startAnimation(fadeIn)
        binding.aboutCard.startAnimation(fadeIn)
    }
}
