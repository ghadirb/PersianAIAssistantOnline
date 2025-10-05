package com.persianai.assistant.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.persianai.assistant.R
import com.persianai.assistant.utils.PersianDateConverter
import java.util.*

class PersianCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar)
        
        val persianDate = PersianDateConverter.getCurrentPersianDate()
        val dayOfWeek = getDayOfWeek()
        val dateText = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
        
        views.setTextViewText(R.id.widgetPersianDate, dateText)
        views.setTextViewText(R.id.widgetWeather, "🌤️ ${getWeatherText()}")
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
    
    private fun getDayOfWeek(): String {
        val days = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
        val calendar = Calendar.getInstance()
        val dayIndex = (calendar.get(Calendar.DAY_OF_WEEK) + 1) % 7
        return days[dayIndex]
    }
    
    private fun getWeatherText(): String {
        return "آفتابی" // TODO: integrate weather API
    }
}
