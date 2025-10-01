package com.persianai.assistant.utils

import android.content.Context
import android.location.Location
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * سیستم مسیریابی صوتی فارسی
 */
class PersianNavigationHelper(private val context: Context) {
    
    private var tts: TextToSpeech? = null
    private var isTTSReady = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("fa", "IR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // اگر فارسی پشتیبانی نشد، از انگلیسی استفاده کن
                    tts?.setLanguage(Locale.US)
                    Toast.makeText(context, "زبان فارسی پشتیبانی نمی‌شود. از انگلیسی استفاده می‌شود.", Toast.LENGTH_LONG).show()
                }
                isTTSReady = true
            }
        }
        
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {}
        })
    }
    
    /**
     * خواندن متن با صوت فارسی
     */
    fun speak(text: String) {
        if (isTTSReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
        }
    }
    
    /**
     * دریافت مسیر از Google Directions API
     */
    suspend fun getDirections(origin: String, destination: String, apiKey: String): NavigationResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${origin.replace(" ", "+")}" +
                    "&destination=${destination.replace(" ", "+")}" +
                    "&mode=driving" +
                    "&language=fa" +
                    "&key=$apiKey"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext NavigationResult.Error("خطا در دریافت مسیر: ${response.code}")
                }
                
                val body = response.body?.string() ?: return@withContext NavigationResult.Error("پاسخ خالی")
                val json = JSONObject(body)
                
                if (json.getString("status") != "OK") {
                    return@withContext NavigationResult.Error("مسیر یافت نشد")
                }
                
                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) {
                    return@withContext NavigationResult.Error("هیچ مسیری یافت نشد")
                }
                
                val route = routes.getJSONObject(0)
                val legs = route.getJSONArray("legs")
                val leg = legs.getJSONObject(0)
                
                val distance = leg.getJSONObject("distance").getString("text")
                val duration = leg.getJSONObject("duration").getString("text")
                val startAddress = leg.getString("start_address")
                val endAddress = leg.getString("end_address")
                
                val steps = mutableListOf<NavigationStep>()
                val stepsArray = leg.getJSONArray("steps")
                
                for (i in 0 until stepsArray.length()) {
                    val step = stepsArray.getJSONObject(i)
                    val instruction = step.getString("html_instructions")
                        .replace("<[^>]*>".toRegex(), "") // حذف HTML tags
                        .replace("&nbsp;", " ")
                    val stepDistance = step.getJSONObject("distance").getString("text")
                    val stepDuration = step.getJSONObject("duration").getString("text")
                    
                    steps.add(NavigationStep(
                        instruction = instruction,
                        distance = stepDistance,
                        duration = stepDuration
                    ))
                }
                
                NavigationResult.Success(
                    distance = distance,
                    duration = duration,
                    startAddress = startAddress,
                    endAddress = endAddress,
                    steps = steps
                )
            }
        } catch (e: Exception) {
            NavigationResult.Error("خطا: ${e.message}")
        }
    }
    
    /**
     * شروع مسیریابی صوتی فارسی
     */
    suspend fun startPersianNavigation(origin: String, destination: String): String {
        // توجه: برای استفاده از Directions API نیاز به Google API Key دارید
        // برای تست، بدون API فقط راهنمایی کلی می‌دهیم
        
        return withContext(Dispatchers.Main) {
            val introText = "مسیریابی از $origin به $destination"
            speak(introText)
            
            // برای استفاده واقعی باید API Key داشته باشید
            """
            مسیریابی صوتی فارسی راه‌اندازی شد!
            
            از $origin به $destination
            
            برای استفاده کامل، نیاز به Google Directions API Key است.
            
            در حال حاضر، Google Maps با مقصد شما باز می‌شود.
            
            قابلیت‌های آینده:
            • دستورات صوتی فارسی گام به گام
            • پیگیری GPS و اطلاع‌رسانی در زمان واقعی
            • هشدار ترافیک به فارسی
            • مسیرهای جایگزین با توضیح فارسی
            """.trimIndent()
        }
    }
    
    /**
     * تفسیر دستورات مسیریابی به فارسی
     */
    fun translateNavigationInstruction(instruction: String): String {
        return instruction
            .replace("Turn right", "بپیچید به راست")
            .replace("Turn left", "بپیچید به چپ")
            .replace("Continue", "ادامه دهید")
            .replace("Head", "حرکت کنید")
            .replace("north", "شمال")
            .replace("south", "جنوب")
            .replace("east", "شرق")
            .replace("west", "غرب")
            .replace("straight", "مستقیم")
            .replace("toward", "به سمت")
            .replace("onto", "به")
            .replace("at", "در")
            .replace("roundabout", "میدان")
            .replace("exit", "خروج")
            .replace("merge", "ادغام شوید")
            .replace("slight right", "کمی به راست")
            .replace("slight left", "کمی به چپ")
            .replace("sharp right", "به شدت به راست")
            .replace("sharp left", "به شدت به چپ")
            .replace("destination", "مقصد")
            .replace("will be on", "خواهد بود در")
    }
    
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}

/**
 * نتیجه مسیریابی
 */
sealed class NavigationResult {
    data class Success(
        val distance: String,
        val duration: String,
        val startAddress: String,
        val endAddress: String,
        val steps: List<NavigationStep>
    ) : NavigationResult()
    
    data class Error(val message: String) : NavigationResult()
}

/**
 * یک گام از مسیریابی
 */
data class NavigationStep(
    val instruction: String,
    val distance: String,
    val duration: String
)
