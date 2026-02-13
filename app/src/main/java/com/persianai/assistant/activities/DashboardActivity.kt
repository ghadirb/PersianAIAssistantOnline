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
import com.persianai.assistant.utils.AutoProvisioningManager
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.config.RemoteAIConfigManager
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
        
        // Load/refresh remote AI config after key provisioning
        lifecycleScope.launch {
            try {
                val remoteConfigManager = RemoteAIConfigManager.getInstance(this@DashboardActivity)
                val config = remoteConfigManager.refreshAndCache()
                if (config != null) {
                    android.util.Log.i("DashboardActivity", "Remote AI config refreshed: ${config.models?.size ?: 0} models")
                    // Show welcome/global announcement messages once per app start
                    config.messages?.let { msgs ->
                        val message = listOfNotNull(msgs.welcome, msgs.global_announcement).joinToString("\n\n")
                        if (message.isNotBlank()) {
                            runOnUiThread {
                                androidx.appcompat.app.AlertDialog.Builder(this@DashboardActivity)
                                    .setTitle("📢 اطلاعیه")
                                    .setMessage(message)
                                    .setPositiveButton("باشه", null)
                                    .show()
                            }
                        }
                    }
                } else {
                    android.util.Log.w("DashboardActivity", "Failed to refresh remote AI config, using cached if available")
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error refreshing remote AI config", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_ai_chat -> {
                showDisabledMessage("مکالمه با مدل")
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
                showDisabledMessage("دستیار مسیریابی صوتی")
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
                showDisabledMessage("مکالمه با مدل")
            }, 150)
        }
        
        binding.psychologyCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showDisabledMessage("مشاور آرامش")
            }, 120)
        }
        
        binding.careerCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showDisabledMessage("مشاور مسیر")
            }, 120)
        }
        
        binding.crmCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, CRMChatActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 120)
        }
        
        binding.docsCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                val intent = Intent(this, DocumentChatActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }, 120)
        }
        
        binding.cultureCard?.setOnClickListener {
            AnimationHelper.clickAnimation(it)
            it.postDelayed({
                showDisabledMessage("فرهنگ")
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

        // Requested: hide CRM and Document modules from dashboard for now
        binding.crmCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        binding.docsCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }

        // Reduce token usage: disable assistant-heavy modules for now
        binding.aiChatCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        binding.voiceNavigationAssistantCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        binding.psychologyCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        binding.careerCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
        binding.cultureCard?.let { card ->
            card.visibility = View.GONE
            (card.parent as? android.view.ViewGroup)?.removeView(card)
        }
    }

    private fun showDisabledMessage(featureName: String) {
        Toast.makeText(this, "$featureName به‌زودی فعال می‌شود. $disabledFeatureMessage", Toast.LENGTH_LONG).show()
    }
    
    private fun showApiKeysStatus() {
        try {
            val keys = prefsManager.getAPIKeys()
            val activeKeys = keys.filter { it.isActive }
            val mode = prefsManager.getWorkingMode()
            
            android.util.Log.d("DashboardActivity", "📊 API Keys Status:")
            keys.forEach { key ->
                android.util.Log.d("DashboardActivity", "  - ${key.provider.name}: ${if (key.isActive) "✅ ACTIVE" else "❌ INACTIVE"}")
            }
            
            val orKey = activeKeys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.OPENROUTER }
            val liaraKey = activeKeys.firstOrNull { it.provider == com.persianai.assistant.models.AIProvider.LIARA }
            
            val status = when {
                liaraKey != null -> "✅ Liara فعال | حالت ${mode.name}"
                orKey != null -> "✅ OpenRouter فعال | حالت ${mode.name}"
                else -> "❌ هیچ کلید فعالی نیست | حالت ${mode.name}"
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
     * دانلود/رمزگشایی مجدد کلیدها از Google Drive با رمز 12345
     * فقط کلید Liara فعال می‌شود، بقیه غیرفعال
     */
    private fun refreshKeysFromDrive() {
        lifecycleScope.launch {
            Snackbar.make(binding.root, "در حال دانلود کلیدها...", Snackbar.LENGTH_SHORT).show()
            try {
                val encrypted = withContext(Dispatchers.IO) { DriveHelper.downloadEncryptedKeys() }
                val decrypted = withContext(Dispatchers.IO) { EncryptionHelper.decrypt(encrypted, "12345") }
                val parsed = parseAPIKeys(decrypted)
                if (parsed.isEmpty()) throw Exception("هیچ کلیدی پیدا نشد")

                // اولویت: فقط Liara فعال، بقیه غیرفعال
                val liaraKeys = parsed.filter { it.provider == com.persianai.assistant.models.AIProvider.LIARA }
                    .map { it.copy(isActive = true) }
                val otherKeys = parsed.filter { it.provider != com.persianai.assistant.models.AIProvider.LIARA }
                    .map { it.copy(isActive = false) }
                val processedKeys = liaraKeys + otherKeys

                prefsManager.saveAPIKeys(processedKeys)
                prefsManager.setWorkingMode(PreferencesManager.WorkingMode.HYBRID)
                syncApiPrefs(prefsManager)
                showApiKeysStatus()
                
                android.util.Log.d("DashboardActivity", "✅ کلیدها دانلود و پردازش شدند:")
                processedKeys.forEach { k ->
                    android.util.Log.d("DashboardActivity", "  - ${k.provider.name}: ${if (k.isActive) "✔ ACTIVE" else "✕ INACTIVE"}")
                }
                
                Snackbar.make(binding.root, "✅ کلیدها به‌روزرسانی شدند (Liara فعال)", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.util.Log.e("DashboardActivity", "Error refreshing keys", e)
                Snackbar.make(binding.root, "❌ خطا: ${e.message}", Snackbar.LENGTH_LONG).show()
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
        editor.remove("liara_api_key")
        editor.remove("openrouter_api_key")
        editor.remove("claude_api_key")
        editor.remove("aiml_api_key")
        editor.remove("avalai_api_key")
        editor.remove("gladia_api_key")

        prefsManager.getAPIKeys().forEach { key ->
            when (key.provider) {
                AIProvider.OPENAI -> editor.putString("openai_api_key", key.key)
                AIProvider.LIARA -> editor.putString("liara_api_key", key.key)
                AIProvider.ANTHROPIC -> editor.putString("claude_api_key", key.key)
                AIProvider.OPENROUTER -> editor.putString("openrouter_api_key", key.key)
                AIProvider.AIML -> editor.putString("aiml_api_key", key.key)
                AIProvider.GLADIA -> editor.putString("gladia_api_key", key.key)
                AIProvider.AVALAI -> editor.putString("avalai_api_key", key.key)
                AIProvider.LOCAL -> {
                    // مدل آفلاین کلید ندارد
                }
                AIProvider.IVIRA -> {
                    // Ivira uses token manager, not string keys
                }
                AIProvider.GAPGPT -> {
                    // فعلاً نیازی به سینک مستقیم در SharedPreferences قدیمی نیست
                }
            }
        }

        val hfToApply = existingHfKey ?: apiPrefs.getString("hf_api_key", null) ?: DefaultApiKeys.getHuggingFaceKey()
        hfToApply?.takeIf { it.isNotBlank() }?.let { editor.putString("hf_api_key", it) }

        editor.apply()
    }

    /**
     * پارس کلیدها - baseUrl صحیح برای Liara
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
                    "openai" -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.OPENAI, parts[1].trim(), isActive = false))
                    "anthropic", "claude" -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.ANTHROPIC, parts[1].trim(), isActive = false))
                    "openrouter" -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.OPENROUTER, parts[1].trim(), isActive = false))
                    "aiml", "aimlapi", "aimlapi.com" -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.AIML, parts[1].trim(), isActive = false))
                    "avalai" -> keys.add(
                        APIKey(
                            provider = com.persianai.assistant.models.AIProvider.AVALAI,
                            key = parts[1].trim(),
                            baseUrl = "https://avalai.ir/api/v1",
                            isActive = false
                        )
                    )
                    "liara" -> keys.add(
                        APIKey(
                            provider = com.persianai.assistant.models.AIProvider.LIARA,
                            key = parts[1].trim(),
                            baseUrl = "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1",
                            isActive = false
                        )
                    )
                    "huggingface", "hf" -> huggingFaceKey = parts[1].trim()
                }
            } else if (parts.size == 1) {
                val token = trimmed
                when {
                    token.startsWith("sk-or-", ignoreCase = true) -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.OPENROUTER, token, isActive = false))
                    token.startsWith("sk-", ignoreCase = true) -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.OPENAI, token, isActive = false))
                    token.startsWith("hf_", ignoreCase = true) -> huggingFaceKey = token
                    token.contains("aiml", ignoreCase = true) || token.contains("aimlapi", ignoreCase = true) -> keys.add(APIKey(com.persianai.assistant.models.AIProvider.AIML, token, isActive = false))
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
