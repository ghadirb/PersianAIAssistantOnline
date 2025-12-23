package com.persianai.assistant.activities

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.persianai.assistant.R
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.databinding.ActivityMainDashboardBinding
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.utils.AnimationHelper
import com.persianai.assistant.utils.AppRatingHelper
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.DriveHelper
import com.persianai.assistant.utils.EncryptionHelper
import com.persianai.assistant.utils.NotificationHelper
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.SharedDataManager
import com.persianai.assistant.workers.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class DashboardActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainDashboardBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var prefs: SharedPreferences
    private val disabledFeatureMessage = "⛔ این بخش به‌صورت موقت غیرفعال شده است تا روی قابلیت‌های حیاتی تمرکز کنیم"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarDashboard)
        
        prefsManager = PreferencesManager(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        
        // ایجاد کانال‌های نوتیفیکیشن
        NotificationHelper.createNotificationChannels(this)
        
        // راه‌اندازی WorkManager برای بررسی یادآوری‌های پس‌زمینه
        scheduleReminderWorker()
        
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
        
        // نمایش سریع وضعیت کلیدها پس از ورود به داشبورد
        showApiKeysStatus()

        // درخواست مجوز اعلان برای heads-up/full-screen روی Android 13+
        requestNotificationPermissionIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ai_chat -> {
                startActivity(Intent(this, AIChatActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                true
            }
            R.id.action_refresh_keys -> {
                refreshKeysFromDrive()
                true
            }
            R.id.action_chat_history -> {
                startActivity(Intent(this, ConversationsActivity::class.java))
                true
            }
            R.id.action_saved_locations -> {
                startActivity(Intent(this, com.persianai.assistant.ui.NamedLocationsActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_offline_models -> {
                startActivity(Intent(this, OfflineModelsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun hideAllCards() {
        binding.calendarCard?.alpha = 0f
        binding.navigationCard?.alpha = 0f
        binding.voiceNavigationAssistantCard?.alpha = 0f
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

        binding.voiceNavigationAssistantCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                try {
                    val intent = Intent(this, VoiceNavigationAssistantActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                } catch (e: Exception) {
                    android.util.Log.e("DashboardActivity", "Error opening voice navigation assistant", e)
                    Toast.makeText(this, "خطا در باز کردن دستیار مسیریابی صوتی", Toast.LENGTH_SHORT).show()
                }
            }, 150)
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
                val intent = Intent(this, AIChatActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 150)
        }
        
        binding.psychologyCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showCounselingDisclaimer(
                    "مشاور آرامش",
                    "این بخش تنها نقش همراه و شنونده دارد و جایگزین درمانگر یا روان‌شناس نیست. در شرایط اضطرار با متخصص تماس بگیرید."
                ) {
                    val intent = Intent(this, AIChatActivity::class.java).apply {
                        putExtra(
                            "presetMessage",
                            "به عنوان مشاور آرامش و خودشناسی، یک گفت‌وگوی کوتاه برای مدیریت استرس و تنظیم احساسات با من شروع کن."
                        )
                        putExtra("forceOnlineAnalysis", true)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }, 120)
        }
        
        binding.careerCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showCounselingDisclaimer(
                    "مشاور مسیر",
                    "این راهنما پیشنهادهای کلی می‌دهد و مسئولیت تصمیم‌های شغلی یا تحصیلی با خود شماست. برای تصمیم نهایی با یک مشاور انسانی مشورت کنید."
                ) {
                    val intent = Intent(this, AIChatActivity::class.java).apply {
                        putExtra(
                            "presetMessage",
                            "می‌خواهم یک مسیر شغلی/تحصیلی مناسب پیدا کنم. با سوال‌های کوتاه کمکم کن تا مهارت‌ها و علایقم را مشخص کنم."
                        )
                        putExtra("forceOnlineAnalysis", true)
                    }
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }, 120)
        }
        
        binding.crmCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, GenericInfoActivity::class.java).apply {
                    putExtra(GenericInfoActivity.EXTRA_TITLE, "دفتر مشتریان")
                    putExtra(GenericInfoActivity.EXTRA_DESC, "مشتریان، یادداشت‌ها و پیگیری‌ها را در یک جا ثبت کنید. برای افزودن قالب پیام یا پیگیری، گفت‌وگو با دستیار را شروع کنید.")
                    putExtra(GenericInfoActivity.EXTRA_PRESET, "یک جدول ساده CRM برای پیگیری مشتریان با ستون‌های نام، شماره، آخرین تماس، اقدام بعدی بساز.")
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 120)
        }
        
        binding.docsCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, GenericInfoActivity::class.java).apply {
                    putExtra(GenericInfoActivity.EXTRA_TITLE, "بانک اسناد")
                    putExtra(GenericInfoActivity.EXTRA_DESC, "مدیریت و جستجوی قرارداد، فاکتور و فایل‌های مهم. برای ساخت چک‌لیست برچسب‌گذاری یا خلاصه‌سازی، گفت‌وگو را شروع کنید.")
                    putExtra(GenericInfoActivity.EXTRA_PRESET, "یک چک‌لیست برچسب‌گذاری و نام‌گذاری برای بایگانی قراردادها و فاکتورها پیشنهاد بده.")
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 120)
        }
        
        binding.cultureCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, GenericInfoActivity::class.java).apply {
                    putExtra(GenericInfoActivity.EXTRA_TITLE, "پیشنهاد فرهنگی")
                    putExtra(GenericInfoActivity.EXTRA_DESC, "کتاب، فیلم و دوره آموزشی متناسب با علایق شما. با دستیار گفتگو کنید تا لیست شخصی دریافت کنید.")
                    putExtra(GenericInfoActivity.EXTRA_PRESET, "بر اساس علایق من در توسعه فردی و تکنولوژی، ۳ کتاب و ۳ فیلم الهام‌بخش پیشنهاد بده.")
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 120)
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
    
    private fun showCounselingDisclaimer(title: String, message: String, onConfirmed: (() -> Unit)? = null) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("متوجه شدم") { _, _ -> onConfirmed?.invoke() }
            .show()
    }
    
    private fun showComingSoon(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("⏳ به‌زودی:\n\n$message")
            .setPositiveButton("باشه", null)
            .show()
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
            binding.voiceNavigationAssistantCard,
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
    
    private fun showApiKeysStatus() {
        try {
            val keys = prefsManager.getAPIKeys()
            val openAI = keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.OPENAI && it.isActive }
            val openRouter = keys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.OPENROUTER && it.isActive }
            val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
            val huggingFace = apiPrefs.getString("hf_api_key", null)
            
            val status = buildString {
                append("کلیدها: ")
                append(if (openAI != null) "OpenAI ✅  " else "OpenAI ⛔  ")
                append(if (openRouter != null) "OpenRouter ✅  " else "OpenRouter ⛔  ")
                append(if (!huggingFace.isNullOrBlank()) "HF ✅" else "HF ⛔")
            }
            
            Snackbar.make(binding.root, status, Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("DashboardActivity", "Error showing API key status", e)
        }
    }

    /**
     * درخواست runtime مجوز اعلان برای heads-up/full-screen در Android 13+
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    /**
     * دانلود/رمزگشایی مجدد کلیدها از Google Drive با رمز 12345 و همگام‌سازی SharedPreferences
     */
    private fun refreshKeysFromDrive() {
        lifecycleScope.launch {
            Snackbar.make(binding.root, "در حال دانلود کلیدها...", Snackbar.LENGTH_SHORT).show()
            try {
                val encrypted = withContext(Dispatchers.IO) { DriveHelper.downloadEncryptedKeys() }
                val decrypted = withContext(Dispatchers.IO) { EncryptionHelper.decrypt(encrypted, "12345") }
                val parsed = parseAPIKeys(decrypted)
                if (parsed.isEmpty()) throw Exception("هیچ کلیدی پیدا نشد")

                prefsManager.saveAPIKeys(parsed)
                syncApiPrefs(prefsManager)
                showApiKeysStatus()
                Snackbar.make(binding.root, "کلیدها به‌روزرسانی شدند (${parsed.size})", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error refreshing keys", e)
                Snackbar.make(binding.root, "خطا در به‌روزرسانی کلیدها: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * همگام‌سازی کلیدها به SharedPreferences (مشابه SplashActivity)
     */
    private fun syncApiPrefs(prefsManager: PreferencesManager) {
        val apiPrefs = getSharedPreferences("api_keys", MODE_PRIVATE)
        val editor = apiPrefs.edit()

        val existingHfKey = apiPrefs.getString("hf_api_key", null)

        editor.remove("openai_api_key")
        editor.remove("openrouter_api_key")
        editor.remove("claude_api_key")
        editor.remove("aiml_api_key")

        prefsManager.getAPIKeys().forEach { key ->
            when (key.provider) {
                AIProvider.OPENAI -> editor.putString("openai_api_key", key.key)
                AIProvider.ANTHROPIC -> editor.putString("claude_api_key", key.key)
                AIProvider.OPENROUTER -> editor.putString("openrouter_api_key", key.key)
                AIProvider.AIML -> editor.putString("aiml_api_key", key.key)
                AIProvider.LOCAL -> {
                    // مدل آفلاین کلید نیاز ندارد
                }
            }
        }

        val hfToApply = existingHfKey ?: apiPrefs.getString("hf_api_key", null) ?: DefaultApiKeys.getHuggingFaceKey()
        hfToApply?.takeIf { it.isNotBlank() }?.let { editor.putString("hf_api_key", it) }

        editor.apply()
    }

    /**
     * پارس کلیدها مشابه SplashActivity
     */
    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        var huggingFaceKey: String? = null

        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            val parts = trimmed.split(":", limit = 2)
            if (parts.size == 2) {
                when (parts[0].lowercase()) {
                    "openai" -> keys.add(APIKey(AIProvider.OPENAI, parts[1].trim(), true))
                    "anthropic", "claude" -> keys.add(APIKey(AIProvider.ANTHROPIC, parts[1].trim(), true))
                    "openrouter" -> keys.add(APIKey(AIProvider.OPENROUTER, parts[1].trim(), true))
                    "aiml", "aimlapi", "aimlapi.com" -> keys.add(APIKey(AIProvider.AIML, parts[1].trim(), true))
                    "huggingface", "hf" -> huggingFaceKey = parts[1].trim()
                }
            } else if (parts.size == 1) {
                val token = trimmed
                when {
                    token.startsWith("sk-or-", ignoreCase = true) -> keys.add(APIKey(AIProvider.OPENROUTER, token, true))
                    token.startsWith("sk-", ignoreCase = true) -> keys.add(APIKey(AIProvider.OPENAI, token, true))
                    token.startsWith("hf_", ignoreCase = true) -> huggingFaceKey = token
                    token.contains("aiml", ignoreCase = true) || token.contains("aimlapi", ignoreCase = true) -> keys.add(APIKey(AIProvider.AIML, token, true))
                }
            }
        }

        huggingFaceKey?.let {
            getSharedPreferences("api_keys", MODE_PRIVATE)
                .edit()
                .putString("hf_api_key", it)
                .apply()
        }

        return keys
    }
    
    private fun scheduleReminderWorker() {
        try {
            val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.MINUTES  // هر دقیقه بررسی کن
            ).build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "reminder_work",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val MUSIC_DISABLED = true
        private const val NAVIGATION_DISABLED = true
        private const val WEATHER_DISABLED = true
    }
}
