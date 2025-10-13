package com.persianai.assistant.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.persianai.assistant.R
import com.persianai.assistant.activities.MainActivity
import com.persianai.assistant.activities.CalendarActivity
import com.persianai.assistant.activities.WeatherActivity
import com.persianai.assistant.api.WorldWeatherAPI
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class PersianCalendarWidgetLarge : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.persianai.assistant.WIDGET_LARGE_REFRESH"
        const val ACTION_OPEN_CHAT = "com.persianai.assistant.WIDGET_OPEN_CHAT"
        const val ACTION_OPEN_CALENDAR = "com.persianai.assistant.WIDGET_OPEN_CALENDAR"
        const val ACTION_OPEN_WEATHER = "com.persianai.assistant.WIDGET_OPEN_WEATHER"
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
        
        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, PersianCalendarWidgetLarge::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
            ACTION_OPEN_CHAT -> {
                val chatIntent = Intent(context, MainActivity::class.java)
                chatIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(chatIntent)
            }
            ACTION_OPEN_CALENDAR -> {
                val calendarIntent = Intent(context, CalendarActivity::class.java)
                calendarIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(calendarIntent)
            }
            ACTION_OPEN_WEATHER -> {
                val weatherIntent = Intent(context, WeatherActivity::class.java)
                weatherIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(weatherIntent)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar_large)
        
        // تاریخ فارسی کامل
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val dayOfWeek = getDayOfWeek()
        val persianDateText = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
        views.setTextViewText(R.id.widgetPersianDateLarge, persianDateText)
        
        // تاریخ میلادی
        val gregorianFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
        val gregorianDate = gregorianFormat.format(Date())
        views.setTextViewText(R.id.widgetGregorianDateLarge, gregorianDate)
        
        // آب و هوا
        updateWeather(context, views)
        
        // کلیک روی ساعت - باز کردن برنامه
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetClockLarge, pendingIntent)
        
        // دکمه refresh
        val refreshIntent = Intent(context, PersianCalendarWidgetLarge::class.java)
        refreshIntent.action = ACTION_REFRESH
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetRefreshButtonLarge, refreshPendingIntent)
        
        // Quick Actions
        // چت
        val chatIntent = Intent(context, PersianCalendarWidgetLarge::class.java)
        chatIntent.action = ACTION_OPEN_CHAT
        val chatPendingIntent = PendingIntent.getBroadcast(context, 2, chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetQuickChat, chatPendingIntent)
        
        // تقویم
        val calendarIntent = Intent(context, PersianCalendarWidgetLarge::class.java)
        calendarIntent.action = ACTION_OPEN_CALENDAR
        val calendarPendingIntent = PendingIntent.getBroadcast(context, 3, calendarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetQuickCalendar, calendarPendingIntent)
        
        // آب و هوا
        val weatherIntent = Intent(context, PersianCalendarWidgetLarge::class.java)
        weatherIntent.action = ACTION_OPEN_WEATHER
        val weatherPendingIntent = PendingIntent.getBroadcast(context, 4, weatherIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetQuickWeather, weatherPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun updateWeather(context: Context, views: RemoteViews) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val weather = WorldWeatherAPI.getCurrentWeather(city)
                if (weather != null) {
                    val emoji = getWeatherEmoji(weather.temp)
                    val tempText = "$emoji ${weather.temp.toInt()}° $city"
                    val descText = weather.description
                    
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widgetWeatherLarge, tempText)
                        views.setTextViewText(R.id.widgetWeatherDescLarge, descText)
                        
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, PersianCalendarWidgetLarge::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        appWidgetIds.forEach { id ->
                            appWidgetManager.updateAppWidget(id, views)
                        }
                    }
                } else {
                    val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                    val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                    val savedDesc = prefs.getString("weather_desc_$city", "آفتابی")
                    val emoji = getWeatherEmoji(savedTemp.toDouble())
                    views.setTextViewText(R.id.widgetWeatherLarge, "$emoji ${savedTemp.toInt()}° $city")
                    views.setTextViewText(R.id.widgetWeatherDescLarge, savedDesc)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersianWidgetLarge", "Error updating weather", e)
                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                val savedTemp = prefs.getFloat("current_temp_$city", 25f)
                val savedDesc = prefs.getString("weather_desc_$city", "آفتابی")
                val emoji = getWeatherEmoji(savedTemp.toDouble())
                views.setTextViewText(R.id.widgetWeatherLarge, "$emoji ${savedTemp.toInt()}° $city")
                views.setTextViewText(R.id.widgetWeatherDescLarge, savedDesc)
            }
        }
    }
    
    private fun getDayOfWeek(): String {
        val days = arrayOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")
        val calendar = Calendar.getInstance()
        val dayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
        return days[dayIndex]
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
