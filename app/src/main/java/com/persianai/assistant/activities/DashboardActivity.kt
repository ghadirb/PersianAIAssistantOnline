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
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.weather.WeatherAPI
import com.persianai.assistant.databinding.ActivityMainDashboardBinding
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.AnimationHelper
import com.persianai.assistant.utils.SharedDataManager
import com.persianai.assistant.utils.NotificationHelper
import com.persianai.assistant.utils.AppRatingHelper
import android.view.View
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var prefs: SharedPreferences
    private val disabledFeatureMessage = "⛔ این بخش به‌صورت موقت غیرفعال شده است تا روی قابلیت‌های حیاتی تمرکز کنیم"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        
        // ایجاد کانال‌های نوتیفیکیشن
        NotificationHelper.createNotificationChannels(this)
        
        // بررسی و نمایش دیالوگ امتیازدهی
        AppRatingHelper.checkAndShowRatingDialog(this)
        
        // Hide all cards initially
        hideAllCards()
        
        setupDate()
        setupClickListeners()
        disableExperimentalModules()
        loadWeather()
        loadWeatherButtons()
        loadSharedData()
        animateCards()
    }
    
    private fun hideAllCards() {
        binding.calendarCard?.alpha = 0f
        binding.navigationCard?.alpha = 0f
        binding.aiChatCard?.alpha = 0f
        binding.musicCard?.alpha = 0f
        binding.expensesCard?.alpha = 0f
        binding.remindersCard?.alpha = 0f
        binding.aboutCard?.alpha = 0f
        binding.weatherCard?.alpha = 0f
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
            if (NAVIGATION_DISABLED) {
                showDisabledMessage("مسیریابی")
                return@setOnClickListener
            }
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
            if (WEATHER_DISABLED) {
                showDisabledMessage("آب‌وهوا")
                return@setOnClickListener
            }
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
            if (MUSIC_DISABLED) {
                showDisabledMessage("پخش موزیک")
                return@setOnClickListener
            }
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, ImprovedMusicActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.expensesCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, AccountingAdvancedActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.remindersCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, AdvancedRemindersActivity::class.java)
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
        if (WEATHER_DISABLED) {
            binding.weatherCard?.alpha = 0.4f
            binding.weatherTempText?.text = "--"
            binding.weatherIcon?.text = "🚧"
            return
        }
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        // نمایش فوری cache برای جلوگیری از چشمک زدن
        val savedTemp = prefs.getFloat("current_temp_$city", -999f)
        val savedIcon = prefs.getString("weather_icon_$city", null)
        if (savedTemp != -999f && !savedIcon.isNullOrEmpty()) {
            binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
            binding.weatherIcon?.text = WorldWeatherAPI.getWeatherEmoji(savedIcon)
        }
        
        lifecycleScope.launch {
            try {
                // دریافت دمای واقعی از WorldWeatherOnline API
                val weatherData = WorldWeatherAPI.getCurrentWeather(city)
                
                if (weatherData != null) {
                    android.util.Log.d("DashboardActivity", "Live weather from WorldWeather: ${weatherData.temp}°C for $city")
                    binding.weatherTempText?.text = "${weatherData.temp.roundToInt()}°"
                    binding.weatherIcon?.text = WorldWeatherAPI.getWeatherEmoji(weatherData.icon)
                    
                    // ذخیره دما برای استفاده در WeatherActivity
                    prefs.edit().putFloat("current_temp_$city", weatherData.temp.toFloat()).apply()
                    prefs.edit().putString("weather_icon_$city", weatherData.icon).apply()
                    prefs.edit().putString("weather_desc_$city", weatherData.description).apply()
                    prefs.edit().putInt("weather_humidity_$city", weatherData.humidity).apply()
                    prefs.edit().putFloat("weather_wind_$city", weatherData.windSpeed.toFloat()).apply()
                } else {
                    // استفاده از داده‌های ذخیره شده
                    val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                    val savedIcon = prefs.getString("weather_icon_$city", "113")
                    binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
                    binding.weatherIcon?.text = WorldWeatherAPI.getWeatherEmoji(savedIcon ?: "113")
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error loading weather", e)
                // استفاده از داده ذخیره شده
                val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                val savedIcon = prefs.getString("weather_icon_$city", "113")
                binding.weatherTempText?.text = "${savedTemp.roundToInt()}°"
                binding.weatherIcon?.text = WorldWeatherAPI.getWeatherEmoji(savedIcon ?: "113")
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
    
    private fun loadWeatherButtons() {
        if (WEATHER_DISABLED) return
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        // TODO: Add hourlyBtn to layout
        // // دکمه پیش‌بینی ساعتی - با جلوگیری از کرش
        // binding.hourlyBtn?.setOnClickListener {
        //     android.util.Log.d("DashboardActivity", "Hourly button clicked")
        //     it.postDelayed({
        //         try {
        //             val intent = Intent(this, WeatherActivity::class.java)
        //             intent.putExtra("SHOW_HOURLY", true)
        //             intent.putExtra("city", city)
        //             startActivity(intent)
        //             overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        //         } catch (e: Exception) {
        //             android.util.Log.e("DashboardActivity", "Error opening hourly weather", e)
        //             Toast.makeText(this, "خطا در نمایش پیش‌بینی ساعتی", Toast.LENGTH_SHORT).show()
        //         }
        //     }, 100)
        // }
        
        // TODO: Add weeklyBtn to layout
        // // دکمه پیش‌بینی هفتگی - با جلوگیری از کرش
        // binding.weeklyBtn?.setOnClickListener {
        //     android.util.Log.d("DashboardActivity", "Weekly button clicked")
        //     it.postDelayed({
        //         try {
        //             val intent = Intent(this, WeatherForecastActivity::class.java)
        //             intent.putExtra("city", city)
        //             startActivity(intent)
        //             overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        //         } catch (e: Exception) {
        //             android.util.Log.e("DashboardActivity", "Error opening weekly forecast", e)
        //             Toast.makeText(this, "خطا در نمایش پیش‌بینی هفتگی", Toast.LENGTH_SHORT).show()
        //         }
        //     }, 100)
        // }
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
    
    private fun loadSharedData() {
        // بارگذاری داده‌های یکپارچه
        lifecycleScope.launch {
            try {
                // یادآوری‌ها
                val reminders = SharedDataManager.getUpcomingReminders(this@DashboardActivity, 3)
                val reminderCount = reminders.size
                if (reminderCount > 0) {
                    binding.remindersCard?.alpha = 1f
                    // می‌توانید تعداد را نمایش دهید
                    android.util.Log.d("DashboardActivity", "🔔 $reminderCount یادآوری فعال")
                }
                
                // حسابداری
                val balance = SharedDataManager.getTotalBalance(this@DashboardActivity)
                val monthlyExpenses = SharedDataManager.getMonthlyExpenses(this@DashboardActivity)
                
                if (balance != 0.0 || monthlyExpenses != 0.0) {
                    android.util.Log.d("DashboardActivity", "💰 موجودی: ${balance.toLong()} - هزینه: ${monthlyExpenses.toLong()}")
                }
                
                // ذخیره دما در SharedDataManager
                val city = prefs.getString("selected_city", "تهران") ?: "تهران"
                val temp = prefs.getFloat("current_temp_$city", 25f)
                val desc = prefs.getString("weather_desc_$city", "آفتابی") ?: "آفتابی"
                val icon = prefs.getString("weather_icon_$city", "113") ?: "113"
                SharedDataManager.saveWeatherData(this@DashboardActivity, city, temp, desc, WorldWeatherAPI.getWeatherEmoji(icon))
                
                android.util.Log.d("DashboardActivity", "✅ داده‌ها به SharedDataManager ذخیره شدند")
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error loading shared data", e)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // بروزرسانی داده‌ها هنگام بازگشت
        loadWeather()
        loadSharedData()
    }
    
    private fun animateCards() {
        // Staggered fade in animation for cards
        val cards = listOfNotNull(
            binding.calendarCard,
            binding.weatherCard,
            binding.navigationCard,
            binding.aiChatCard,
            binding.musicCard,
            binding.expensesCard,
            binding.remindersCard,
            binding.aboutCard
        ).filter { it.visibility == View.VISIBLE }
        
        AnimationHelper.animateListItems(cards, delayBetween = 100)
        
        // Add pulse animation to navigation card to draw attention (only if visible)
        if (!NAVIGATION_DISABLED) {
            binding.navigationCard?.postDelayed({
                binding.navigationCard?.let {
                    AnimationHelper.pulseAnimation(it, scaleFactor = 1.05f, duration = 2000)
                }
            }, 1000)
        }
    }
    
    private fun disableExperimentalModules() {
        if (MUSIC_DISABLED) {
            binding.musicCard?.let { card ->
                card.visibility = View.GONE
                (card.parent as? android.view.ViewGroup)?.removeView(card)
            }
        }
        if (NAVIGATION_DISABLED) {
            binding.navigationCard?.let { card ->
                card.visibility = View.GONE
                (card.parent as? android.view.ViewGroup)?.removeView(card)
            }
        }
        if (WEATHER_DISABLED) {
            binding.weatherCard?.let { card ->
                card.visibility = View.GONE
                (card.parent as? android.view.ViewGroup)?.removeView(card)
            }
        }
    }

    private fun showDisabledMessage(featureName: String) {
        Toast.makeText(this, "$featureName به‌زودی فعال می‌شود. $disabledFeatureMessage", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MUSIC_DISABLED = true
        private const val NAVIGATION_DISABLED = true
        private const val WEATHER_DISABLED = true
    }
}
