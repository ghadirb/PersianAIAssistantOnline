package com.persianai.assistant.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.persianai.assistant.R
import com.persianai.assistant.activities.DashboardActivity
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.utils.PersianDateConverter
import com.persianai.assistant.utils.WidgetThemeManager
import kotlinx.coroutines.*
import java.util.*

class PersianCalendarWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.persianai.assistant.WIDGET_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, PersianCalendarWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
            // آپدیت همه ویجت‌ها
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar)
            
            // خواندن تنظیمات
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val showWeather = prefs.getBoolean("show_weather", true)
            
            // شروع سرویس آپدیت
            try {
                val serviceIntent = Intent(context, WidgetUpdateService::class.java)
                context.startService(serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("Widget", "Service error: ${e.message}")
            }
            
            // تاریخ فارسی
            val persianDate = PersianDateConverter.getCurrentPersianDate()
            val dayOfWeek = getDayOfWeek()
            val dateText = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
            
            views.setTextViewText(R.id.widgetPersianDate, dateText)
        
        // آب و هوا
        if (showWeather) {
            views.setViewVisibility(R.id.widgetWeather, android.view.View.VISIBLE)
            updateWeather(context, views)
        } else {
            views.setViewVisibility(R.id.widgetWeather, android.view.View.GONE)
        }
        
        // کلیک بر روی ساعت - باز کردن برنامه
        val intent = Intent(context, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetClock, pendingIntent)
        
        // دکمه refresh
        val refreshIntent = Intent(context, PersianCalendarWidget::class.java)
        refreshIntent.action = ACTION_REFRESH
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            android.util.Log.e("PersianCalendarWidget", "Error updating widget", e)
        }
    }
    
    private fun updateWeather(context: Context, views: RemoteViews) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        // آپدیت آب و هوا در background
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val weather = WorldWeatherAPI.getCurrentWeather(city)
                if (weather != null) {
                    val emoji = getWeatherEmoji(weather.temp)
                    val text = "$emoji ${weather.temp.toInt()}° $city"
                    
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widgetWeather, text)
                        
                        // آپدیت مجدد ویجت
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, PersianCalendarWidget::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        appWidgetIds.forEach { id ->
                            appWidgetManager.updateAppWidget(id, views)
                        }
                    }
                } else {
                    // استفاده از داده ذخیره شده
                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                    val emoji = getWeatherEmoji(savedTemp.toDouble())
                    val text = "$emoji ${savedTemp.toInt()}° $city"
                    views.setTextViewText(R.id.widgetWeather, text)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersianCalendarWidget", "Error updating weather: ${e.message}", e)
                // استفاده از داده ذخیره شده
                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                val emoji = getWeatherEmoji(savedTemp.toDouble())
                val text = "$emoji ${savedTemp.toInt()}° $city"
                views.setTextViewText(R.id.widgetWeather, text)
            }
        }
    }
    
    private fun getDayOfWeek(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
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
}
