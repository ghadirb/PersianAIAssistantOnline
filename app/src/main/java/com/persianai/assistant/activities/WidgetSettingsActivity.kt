package com.persianai.assistant.activities

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.persianai.assistant.R
import com.persianai.assistant.widgets.*
import kotlinx.coroutines.*

class WidgetSettingsActivity : AppCompatActivity() {
    
    private lateinit var prefs: android.content.SharedPreferences
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_settings)
        
        prefs = getSharedPreferences("widget_prefs", MODE_PRIVATE)
        
        setupViews()
        loadCurrentSettings()
    }
    
    private fun setupViews() {
        // Back button
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            finish()
        }
        
        // Widget Transparency Slider
        val transparencySlider = findViewById<SeekBar>(R.id.seekBarTransparency)
        val transparencyValue = findViewById<TextView>(R.id.textTransparencyValue)
        
        transparencySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                transparencyValue.text = "$progress%"
                if (fromUser) {
                    prefs.edit().putInt("widget_transparency", progress).apply()
                    updateWidgetPreview()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                refreshAllWidgets()
            }
        })
        
        // City Selection for Weather
        val citySpinner = findViewById<Spinner>(R.id.spinnerCity)
        val cities = arrayOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "قم", "اهواز")
        citySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cities)
        
        citySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedCity = cities[position]
                val weatherPrefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
                weatherPrefs.edit().putString("selected_city", selectedCity).apply()
                refreshAllWidgets()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Widget Update Interval
        val updateIntervalSpinner = findViewById<Spinner>(R.id.spinnerUpdateInterval)
        val intervals = arrayOf("15 دقیقه", "30 دقیقه", "1 ساعت", "3 ساعت", "6 ساعت")
        val intervalValues = arrayOf(15, 30, 60, 180, 360)
        updateIntervalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)
        
        updateIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("update_interval_minutes", intervalValues[position]).apply()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        // Widget Theme
        findViewById<RadioGroup>(R.id.radioGroupTheme).setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.radioThemeLight -> "light"
                R.id.radioThemeDark -> "dark"
                R.id.radioThemeAuto -> "auto"
                else -> "auto"
            }
            prefs.edit().putString("widget_theme", theme).apply()
            refreshAllWidgets()
        }
        
        // Show Seconds Toggle
        findViewById<SwitchCompat>(R.id.switchShowSeconds).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_seconds", isChecked).apply()
            refreshAllWidgets()
        }
        
        // Show Weather Toggle
        findViewById<SwitchCompat>(R.id.switchShowWeather).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_weather", isChecked).apply()
            refreshAllWidgets()
        }
        
        // Show Gregorian Date Toggle
        findViewById<SwitchCompat>(R.id.switchShowGregorian).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_gregorian", isChecked).apply()
            refreshAllWidgets()
        }
        
        // Quick Actions Toggle
        findViewById<SwitchCompat>(R.id.switchQuickActions).setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_quick_actions", isChecked).apply()
            refreshAllWidgets()
        }
        
        // Refresh All Widgets Button
        findViewById<Button>(R.id.buttonRefreshWidgets).setOnClickListener {
            refreshAllWidgets()
            Toast.makeText(this, "ویجت‌ها بروزرسانی شدند", Toast.LENGTH_SHORT).show()
        }
        
        // Reset to Default Button
        findViewById<Button>(R.id.buttonResetDefaults).setOnClickListener {
            resetToDefaults()
        }
    }
    
    private fun loadCurrentSettings() {
        // Load transparency
        val transparency = prefs.getInt("widget_transparency", 60)
        findViewById<SeekBar>(R.id.seekBarTransparency).progress = transparency
        findViewById<TextView>(R.id.textTransparencyValue).text = "$transparency%"
        
        // Load city
        val weatherPrefs = getSharedPreferences("weather_prefs", MODE_PRIVATE)
        val selectedCity = weatherPrefs.getString("selected_city", "تهران")
        val cities = arrayOf("تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "قم", "اهواز")
        val cityIndex = cities.indexOf(selectedCity)
        if (cityIndex >= 0) {
            findViewById<Spinner>(R.id.spinnerCity).setSelection(cityIndex)
        }
        
        // Load update interval
        val updateInterval = prefs.getInt("update_interval_minutes", 30)
        val intervalValues = arrayOf(15, 30, 60, 180, 360)
        val intervalIndex = intervalValues.indexOf(updateInterval)
        if (intervalIndex >= 0) {
            findViewById<Spinner>(R.id.spinnerUpdateInterval).setSelection(intervalIndex)
        }
        
        // Load theme
        val theme = prefs.getString("widget_theme", "auto")
        val radioId = when (theme) {
            "light" -> R.id.radioThemeLight
            "dark" -> R.id.radioThemeDark
            else -> R.id.radioThemeAuto
        }
        findViewById<RadioGroup>(R.id.radioGroupTheme).check(radioId)
        
        // Load toggles
        findViewById<SwitchCompat>(R.id.switchShowSeconds).isChecked = 
            prefs.getBoolean("show_seconds", false)
        findViewById<SwitchCompat>(R.id.switchShowWeather).isChecked = 
            prefs.getBoolean("show_weather", true)
        findViewById<SwitchCompat>(R.id.switchShowGregorian).isChecked = 
            prefs.getBoolean("show_gregorian", false)
        findViewById<SwitchCompat>(R.id.switchQuickActions).isChecked = 
            prefs.getBoolean("show_quick_actions", true)
    }
    
    private fun updateWidgetPreview() {
        // Update widget preview based on current settings
        scope.launch {
            delay(100) // Small delay to ensure settings are saved
            // Update preview if needed
        }
    }
    
    private fun refreshAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Refresh Medium Widget
        val mediumWidget = ComponentName(this, PersianCalendarWidget::class.java)
        val mediumIds = appWidgetManager.getAppWidgetIds(mediumWidget)
        if (mediumIds.isNotEmpty()) {
            val intent = Intent(this, PersianCalendarWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mediumIds)
            }
            sendBroadcast(intent)
        }
        
        // Refresh Small Widget
        val smallWidget = ComponentName(this, PersianCalendarWidgetSmall::class.java)
        val smallIds = appWidgetManager.getAppWidgetIds(smallWidget)
        if (smallIds.isNotEmpty()) {
            val intent = Intent(this, PersianCalendarWidgetSmall::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, smallIds)
            }
            sendBroadcast(intent)
        }
        
        // Refresh Large Widget
        val largeWidget = ComponentName(this, PersianCalendarWidgetLarge::class.java)
        val largeIds = appWidgetManager.getAppWidgetIds(largeWidget)
        if (largeIds.isNotEmpty()) {
            val intent = Intent(this, PersianCalendarWidgetLarge::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, largeIds)
            }
            sendBroadcast(intent)
        }
    }
    
    private fun resetToDefaults() {
        prefs.edit().apply {
            putInt("widget_transparency", 60)
            putInt("update_interval_minutes", 30)
            putString("widget_theme", "auto")
            putBoolean("show_seconds", false)
            putBoolean("show_weather", true)
            putBoolean("show_gregorian", false)
            putBoolean("show_quick_actions", true)
            apply()
        }
        
        getSharedPreferences("weather_prefs", MODE_PRIVATE).edit()
            .putString("selected_city", "تهران")
            .apply()
        
        loadCurrentSettings()
        refreshAllWidgets()
        
        Toast.makeText(this, "تنظیمات به حالت پیش‌فرض بازگشت", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
