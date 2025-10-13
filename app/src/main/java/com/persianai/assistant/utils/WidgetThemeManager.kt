package com.persianai.assistant.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import com.persianai.assistant.R

/**
 * مدیریت تم ویجت‌ها بر اساس Dark Mode و تنظیمات کاربر
 */
class WidgetThemeManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    
    enum class WidgetTheme {
        LIGHT, DARK, AUTO
    }
    
    /**
     * دریافت تم فعلی ویجت
     */
    fun getCurrentTheme(): WidgetTheme {
        val themeString = prefs.getString("widget_theme", "auto") ?: "auto"
        return when (themeString) {
            "light" -> WidgetTheme.LIGHT
            "dark" -> WidgetTheme.DARK
            else -> WidgetTheme.AUTO
        }
    }
    
    /**
     * آیا در حالت Dark Mode هستیم؟
     */
    fun isDarkMode(): Boolean {
        val theme = getCurrentTheme()
        return when (theme) {
            WidgetTheme.LIGHT -> false
            WidgetTheme.DARK -> true
            WidgetTheme.AUTO -> {
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
    
    /**
     * دریافت رنگ متن بر اساس تم
     */
    fun getTextColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#FFFFFF")
        } else {
            Color.parseColor("#212121")
        }
    }
    
    /**
     * دریافت رنگ متن ثانویه
     */
    fun getSecondaryTextColor(): Int {
        return if (isDarkMode()) {
            Color.parseColor("#B0B0B0")
        } else {
            Color.parseColor("#666666")
        }
    }
    
    /**
     * دریافت background resource بر اساس تم و سایز
     */
    fun getBackgroundResource(widgetSize: String): Int {
        val transparency = prefs.getInt("widget_transparency", 60)
        
        return if (isDarkMode()) {
            when (widgetSize) {
                "small" -> R.drawable.widget_background_small_dark
                "large" -> R.drawable.widget_background_large_dark
                else -> R.drawable.widget_background_dark
            }
        } else {
            when (widgetSize) {
                "small" -> R.drawable.widget_background_small
                "large" -> R.drawable.widget_background_large
                else -> R.drawable.widget_background
            }
        }
    }
    
    /**
     * اعمال تم به RemoteViews
     */
    fun applyThemeToWidget(views: RemoteViews, widgetSize: String) {
        // تنظیم پس‌زمینه
        val backgroundRes = getBackgroundResource(widgetSize)
        views.setInt(android.R.id.content, "setBackgroundResource", backgroundRes)
        
        // تنظیم رنگ متن‌ها بر اساس ID های مختلف
        val textColor = getTextColor()
        val secondaryTextColor = getSecondaryTextColor()
        
        // این ID ها باید با ID های واقعی در layout مطابقت داشته باشند
        applyTextColors(views, textColor, secondaryTextColor, widgetSize)
    }
    
    /**
     * اعمال رنگ‌های متن به ویجت
     */
    private fun applyTextColors(views: RemoteViews, primaryColor: Int, secondaryColor: Int, widgetSize: String) {
        when (widgetSize) {
            "small" -> {
                views.setTextColor(R.id.widgetClockSmall, primaryColor)
                views.setTextColor(R.id.widgetPersianDateSmall, secondaryColor)
                views.setTextColor(R.id.widgetWeatherSmall, secondaryColor)
            }
            "large" -> {
                views.setTextColor(R.id.widgetClockLarge, primaryColor)
                views.setTextColor(R.id.widgetPersianDateLarge, primaryColor)
                views.setTextColor(R.id.widgetGregorianDateLarge, secondaryColor)
                views.setTextColor(R.id.widgetWeatherLarge, secondaryColor)
                views.setTextColor(R.id.widgetWeatherDescLarge, secondaryColor)
                views.setTextColor(R.id.widgetAppName, secondaryColor)
            }
            else -> {  // medium
                views.setTextColor(R.id.widgetClock, primaryColor)
                views.setTextColor(R.id.widgetPersianDate, secondaryColor)
                views.setTextColor(R.id.widgetWeather, secondaryColor)
            }
        }
    }
    
    /**
     * دریافت alpha برای transparency
     */
    fun getTransparencyAlpha(): Int {
        val transparency = prefs.getInt("widget_transparency", 60)
        return (transparency * 255 / 100)
    }
    
    /**
     * ایجاد رنگ با transparency
     */
    fun createColorWithAlpha(baseColor: Int): Int {
        val alpha = getTransparencyAlpha()
        return Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
    }
}
