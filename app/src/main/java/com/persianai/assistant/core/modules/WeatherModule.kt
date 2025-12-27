package com.persianai.assistant.core.modules

import android.content.Context
import android.content.Intent
import android.util.Log
import com.persianai.assistant.activities.WeatherActivity
import com.persianai.assistant.core.AIIntentRequest
import com.persianai.assistant.core.AIIntentResult
import com.persianai.assistant.core.intent.AIIntent
import com.persianai.assistant.core.intent.WeatherCheckIntent

class WeatherModule(private val context: Context) : BaseModule(context) {
    override val moduleName: String = "Weather"

    override suspend fun canHandle(intent: AIIntent): Boolean {
        return intent is WeatherCheckIntent
    }

    override suspend fun execute(request: AIIntentRequest, intent: AIIntent): AIIntentResult {
        return when (intent) {
            is WeatherCheckIntent -> handleWeatherCheck(request, intent)
            else -> createResult("نوع Intent نشناخته‌شده", intent.name, false)
        }
    }

    private suspend fun handleWeatherCheck(request: AIIntentRequest, intent: WeatherCheckIntent): AIIntentResult {
        val location = intent.location ?: "موقعیت فعلی"
        
        logAction("CHECK_WEATHER", "location=$location")
        
        return try {
            // باز کردن Weather Activity
            val weatherIntent = Intent(context, WeatherActivity::class.java).apply {
                if (intent.location != null) {
                    putExtra("location", intent.location)
                }
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(weatherIntent)
            
            createResult(
                text = "🌤️ در حال دریافت آب‌وهوا برای $location...",
                intentName = intent.name,
                actionType = "check_weather",
                actionData = location
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking weather", e)
            createResult(
                text = "❌ خطا در دریافت اطلاعات آب‌وهوا",
                intentName = intent.name,
                success = false
            )
        }
    }
}