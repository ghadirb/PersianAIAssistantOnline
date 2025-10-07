package com.persianai.assistant.widgets

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.RemoteViews
import com.persianai.assistant.R
import com.persianai.assistant.api.OpenWeatherAPI
import com.persianai.assistant.utils.PersianDateConverter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * سرویس بروزرسانی خودکار ویجت‌ها
 */
class WidgetUpdateService : Service() {
    
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        startUpdating()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateAllWidgets()
        return START_STICKY
    }
    
    private fun startUpdating() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateAllWidgets()
                // بروزرسانی هر 30 ثانیه برای نمایش زمان دقیق
                updateHandler.postDelayed(this, 30000)
            }
        }
        updateHandler.post(updateRunnable!!)
    }
    
    private fun updateAllWidgets() {
        val context = this
        
        // بروزرسانی ویجت متوسط
        updateMediumWidgets(context)
        
        // بروزرسانی ویجت کوچک
        updateSmallWidgets(context)
        
        // بروزرسانی ویجت بزرگ
        updateLargeWidgets(context)
    }
    
    private fun updateMediumWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, PersianCalendarWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar)
            
            // بروزرسانی زمان
            val timeFormat = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
            views.setTextViewText(R.id.widgetClock, timeFormat.format(Date()))
            
            // بروزرسانی تاریخ
            val persianDate = PersianDateConverter.getCurrentPersianDate()
            val dayOfWeek = getDayOfWeekPersian()
            val dateText = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
            views.setTextViewText(R.id.widgetPersianDate, dateText)
            
            // بروزرسانی آب و هوا
            updateWeatherAsync(context, views, appWidgetManager, appWidgetId)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun updateSmallWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, PersianCalendarWidgetSmall::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar_small)
            
            // بروزرسانی زمان
            val timeFormat = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
            views.setTextViewText(R.id.widgetClockSmall, timeFormat.format(Date()))
            
            // بروزرسانی تاریخ (فقط روز و ماه)
            val persianDate = PersianDateConverter.getCurrentPersianDate()
            val shortDate = "${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month).substring(0, 3)}"
            views.setTextViewText(R.id.widgetPersianDateSmall, shortDate)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun updateLargeWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, PersianCalendarWidgetLarge::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_persian_calendar_large)
            
            // بروزرسانی زمان با ثانیه
            val timeFormat = SimpleDateFormat("HH:mm", Locale("fa", "IR"))
            val secondsFormat = SimpleDateFormat("ss", Locale("fa", "IR"))
            views.setTextViewText(R.id.widgetClockLarge, timeFormat.format(Date()))
            views.setTextViewText(R.id.widgetSecondsLarge, secondsFormat.format(Date()))
            
            // بروزرسانی تاریخ کامل
            val persianDate = PersianDateConverter.getCurrentPersianDate()
            val dayOfWeek = getDayOfWeekPersian()
            val fullDate = "$dayOfWeek، ${persianDate.day} ${PersianDateConverter.getMonthName(persianDate.month)} ${persianDate.year}"
            views.setTextViewText(R.id.widgetPersianDateLarge, fullDate)
            
            // تاریخ میلادی
            val gregorianFormat = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH)
            views.setTextViewText(R.id.widgetGregorianDateLarge, gregorianFormat.format(Date()))
            
            // بروزرسانی آب و هوا
            updateWeatherAsync(context, views, appWidgetManager, appWidgetId)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
    
    private fun updateWeatherAsync(
        context: Context, 
        views: RemoteViews, 
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        serviceScope.launch {
            try {
                val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
                val city = prefs.getString("selected_city", "تهران") ?: "تهران"
                
                val weather = OpenWeatherAPI.getCurrentWeather(city)
                if (weather != null) {
                    val emoji = OpenWeatherAPI.getWeatherEmoji(weather.icon)
                    val text = "$emoji ${weather.temp.toInt()}° $city"
                    
                    withContext(Dispatchers.Main) {
                        // بروزرسانی بر اساس نوع ویجت
                        when {
                            views.getLayoutId() == R.layout.widget_persian_calendar -> {
                                views.setTextViewText(R.id.widgetWeather, text)
                            }
                            views.getLayoutId() == R.layout.widget_persian_calendar_small -> {
                                views.setTextViewText(R.id.widgetWeatherSmall, "$emoji ${weather.temp.toInt()}°")
                            }
                            views.getLayoutId() == R.layout.widget_persian_calendar_large -> {
                                views.setTextViewText(R.id.widgetWeatherLarge, text)
                                views.setTextViewText(R.id.widgetWeatherDescLarge, weather.description)
                            }
                        }
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun getDayOfWeekPersian(): String {
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
    
    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
        serviceScope.cancel()
    }
}
