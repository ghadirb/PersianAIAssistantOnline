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
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.*

class PersianCalendarWidgetSmall : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.persianai.assistant.WIDGET_SMALL_REFRESH"
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
            val thisWidget = ComponentName(context, PersianCalendarWidgetSmall::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            
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
        val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar_small)
        
        // تاریخ فارسی فقط روز و ماه
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val shortDate = "${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month).take(3)}"
        views.setTextViewText(R.id.widgetPersianDateSmall, shortDate)
        
        // آب و هوا مختصر
        updateWeather(context, views)
        
        // کلیک برای باز کردن برنامه
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetClockSmall, pendingIntent)
        
        // دکمه refresh
        val refreshIntent = Intent(context, PersianCalendarWidgetSmall::class.java)
        refreshIntent.action = ACTION_REFRESH
        val refreshPendingIntent = PendingIntent.getBroadcast(context, 1, refreshIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widgetRefreshButtonSmall, refreshPendingIntent)
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun updateWeather(context: Context, views: RemoteViews) {
        val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
        val city = prefs.getString("selected_city", "تهران") ?: "تهران"
        
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val weather = OpenWeatherAPI.getCurrentWeather(city)
                if (weather != null) {
                    val emoji = OpenWeatherAPI.getWeatherEmoji(weather.icon)
                    val text = "$emoji ${weather.temp.toInt()}°"
                    
                    withContext(Dispatchers.Main) {
                        views.setTextViewText(R.id.widgetWeatherSmall, text)
                        
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisWidget = ComponentName(context, PersianCalendarWidgetSmall::class.java)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                        appWidgetIds.forEach { id ->
                            appWidgetManager.updateAppWidget(id, views)
                        }
                    }
                } else {
                    val mockData = OpenWeatherAPI.getMockWeatherData(city)
                    val emoji = OpenWeatherAPI.getWeatherEmoji(mockData.icon)
                    val text = "$emoji ${mockData.temp.toInt()}°"
                    views.setTextViewText(R.id.widgetWeatherSmall, text)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersianWidgetSmall", "Error updating weather", e)
                val mockData = OpenWeatherAPI.getMockWeatherData(city)
                val emoji = OpenWeatherAPI.getWeatherEmoji(mockData.icon)
                views.setTextViewText(R.id.widgetWeatherSmall, "$emoji ${mockData.temp.toInt()}°")
            }
        }
    }
}
