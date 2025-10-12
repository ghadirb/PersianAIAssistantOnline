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
        binding.expensesCard?.alpha = 0f
        binding.remindersCard?.alpha = 0f
        binding.navigationCard?.alpha = 0f
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
            try {
                AnimationHelper.clickAnimation(it)
                it.postDelayed({
                    try {
                        val intent = Intent(this, CalendarActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardActivity", "Error opening calendar", e)
                        Toast.makeText(this, "خطا در باز کردن تقویم", Toast.LENGTH_SHORT).show()
                    }
                }, 150)
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Click error", e)
            }
        }
        
        binding.navigationCard?.setOnClickListener {
            try {
                AnimationHelper.clickAnimation(it)
                it.postDelayed({
                    try {
                        val intent = Intent(this, NavigationActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardActivity", "Error opening navigation", e)
                        Toast.makeText(this, "خطا در باز کردن مسیریابی", Toast.LENGTH_SHORT).show()
                    }
                }, 150)
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Click error", e)
            }
        }
        
        binding.weatherCard?.setOnClickListener {
            try {
                AnimationHelper.clickAnimation(it)
                it.postDelayed({
                    try {
                        val intent = Intent(this, WeatherActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    } catch (e: Exception) {
                        android.util.Log.e("DashboardActivity", "Error opening weather", e)
                        Toast.makeText(this, "خطا در باز کردن آب و هوا", Toast.LENGTH_SHORT).show()
                    }
                }, 150)
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Click error", e)
            }
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
        
        binding.expensesCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, AccountingActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.remindersCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, RemindersActivity::class.java)
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
        
        // نمایش فوری cache برای جلوگیری از چشمک زدن
        val savedTemp = prefs.getFloat("current_temp_$city", -999f)
        if (savedTemp != -999f) {
            binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
            binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(savedTemp.toDouble())
        }
        
        lifecycleScope.launch {
            try {
                // دریافت دمای واقعی لحظه‌ای از AQICN
                val aqicnData = AqicnWeatherAPI.getWeatherByCity(city)
                
                if (aqicnData != null) {
                    android.util.Log.d("DashboardActivity", "Live weather: ${aqicnData.temp}°C for $city")
                    binding.weatherTempText?.text = "${aqicnData.temp.roundToInt()}°"
                    binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(aqicnData.temp)
                    
                    // ذخیره دما برای استفاده در WeatherActivity
                    prefs.edit().putFloat("current_temp_$city", aqicnData.temp.toFloat()).apply()
                } else {
                    // استفاده از داده‌های ذخیره شده یا تخمینی
                    val savedTemp = prefs.getFloat("current_temp_$city", -999f)
                    if (savedTemp != -999f) {
                        binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
                        binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(savedTemp.toDouble())
                    } else {
                        val estimatedData = AqicnWeatherAPI.getEstimatedWeatherForCity(city)
                        binding.weatherTempText?.text = "${estimatedData.temp.roundToInt()}°"
                        binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(estimatedData.temp)
                        prefs.edit().putFloat("current_temp_$city", estimatedData.temp.toFloat()).apply()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error loading weather", e)
                // استفاده از داده ذخیره شده یا تخمینی
                val savedTemp = prefs.getFloat("current_temp_$city", -999f)
                if (savedTemp != -999f) {
                    binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
                    binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(savedTemp.toDouble())
                } else {
                    val estimatedData = AqicnWeatherAPI.getEstimatedWeatherForCity(city)
                    binding.weatherTempText?.text = "${estimatedData.temp.roundToInt()}°"
                    binding.weatherIcon?.text = AqicnWeatherAPI.getWeatherEmoji(estimatedData.temp)
                }
            }
        }
        
        // دکمه پیش‌بینی ساعتی - با جلوگیری از کرش
        binding.hourlyBtn?.setOnClickListener {
            android.util.Log.d("DashboardActivity", "Hourly button clicked")
            it.postDelayed({
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
            }, 100)
        }
        
        // دکمه پیش‌بینی هفتگی - با جلوگیری از کرش
        binding.weeklyBtn?.setOnClickListener {
            android.util.Log.d("DashboardActivity", "Weekly button clicked")
            it.postDelayed({
                try {
                    val intent = Intent(this, WeatherForecastActivity::class.java)
                    intent.putExtra("city", city)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error opening weekly forecast", e)
                    Toast.makeText(this, "خطا در نمایش پیش‌بینی هفتگی", Toast.LENGTH_SHORT).show()
                }
            }, 100)
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
            binding.expensesCard,
            binding.remindersCard,
            binding.navigationCard,
            binding.aboutCard
        )
        
        AnimationHelper.animateListItems(cards, delayBetween = 100)
        
        // Add pulse animation to weather card to draw attention
        binding.weatherCard?.postDelayed({
            AnimationHelper.pulseAnimation(binding.weatherCard!!, scaleFactor = 1.05f, duration = 2000)
        }, 1000)
    }
}
