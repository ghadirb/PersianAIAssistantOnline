package com.persianai.assistant.navigation

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.persianai.assistant.models.AIModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

/**
 * TTS با کیفیت بالا با استفاده از مدل‌های AI
 * حالت 1: OpenAI TTS (اینترنت) - کیفیت عالی
 * حالت 2: Offline Model (بدون اینترنت) - کیفیت خوب
 * حالت 3: Android TTS (fallback) - کیفیت متوسط
 */
class AIPoweredTTS(private val context: Context) {
    
    private var aiModelManager: AIModelManager? = null
    private var androidTTS: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isReady = false
    
    companion object {
        private const val TAG = "AIPoweredTTS"
        
        // OpenAI TTS voices
        const val VOICE_ALLOY = "alloy"
        const val VOICE_ECHO = "echo"
        const val VOICE_FABLE = "fable"
        const val VOICE_ONYX = "onyx"
        const val VOICE_NOVA = "nova"
        const val VOICE_SHIMMER = "shimmer"
    }
    
    enum class TTSMode {
        ONLINE_AI,      // OpenAI TTS - بهترین کیفیت
        OFFLINE_MODEL,  // مدل محلی - کیفیت خوب
        ANDROID_TTS     // Android TTS - کیفیت متوسط
    }
    
    private var currentMode: TTSMode = TTSMode.ANDROID_TTS
    
    init {
        // تلاش برای initialize کردن AI Model
        try {
            aiModelManager = AIModelManager(context)
            
            // چک کردن دسترسی به OpenAI
            val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val openaiKey = prefs.getString("openai_api_key", null)
            
            if (!openaiKey.isNullOrEmpty()) {
                currentMode = TTSMode.ONLINE_AI
                Log.d(TAG, "Using OpenAI TTS (Best Quality)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI Model not available", e)
        }
        
        // Initialize Android TTS as fallback
        initializeAndroidTTS()
    }
    
    private fun initializeAndroidTTS() {
        androidTTS = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = androidTTS?.setLanguage(Locale("fa", "IR"))
                
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Persian language not supported, using default")
                    androidTTS?.setLanguage(Locale.getDefault())
                }
                
                androidTTS?.setSpeechRate(0.85f)
                androidTTS?.setPitch(1.0f)
                
                isReady = true
                if (currentMode == TTSMode.ANDROID_TTS) {
                    Log.d(TAG, "Using Android TTS (Medium Quality)")
                }
            }
        }
    }
    
    /**
     * خواندن متن با بهترین کیفیت موجود
     */
    suspend fun speak(text: String, urgent: Boolean = false) {
        val persianText = convertNumbersToPersian(text)
        
        when (currentMode) {
            TTSMode.ONLINE_AI -> {
                if (speakWithOpenAI(persianText)) {
                    return
                }
                // اگر نشد، به حالت بعدی برو
                currentMode = TTSMode.OFFLINE_MODEL
                speak(text, urgent)
            }
            
            TTSMode.OFFLINE_MODEL -> {
                if (speakWithOfflineModel(persianText)) {
                    return
                }
                // اگر نشد، از Android TTS استفاده کن
                currentMode = TTSMode.ANDROID_TTS
                speak(text, urgent)
            }
            
            TTSMode.ANDROID_TTS -> {
                speakWithAndroidTTS(persianText, urgent)
            }
        }
    }
    
    /**
     * استفاده از OpenAI TTS (بهترین کیفیت)
     */
    private suspend fun speakWithOpenAI(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Trying OpenAI TTS...")
            
            // دریافت API key
            val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("openai_api_key", null)
            
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "OpenAI API key not found")
                return@withContext false
            }
            
            // ساخت request برای OpenAI TTS
            val requestBody = """
                {
                    "model": "tts-1-hd",
                    "voice": "$VOICE_ALLOY",
                    "input": "$text",
                    "speed": 0.9
                }
            """.trimIndent()
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json"),
                    requestBody
                ))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                // ذخیره فایل صوتی
                val audioFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
                response.body()?.byteStream()?.use { input ->
                    audioFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // پخش فایل
                withContext(Dispatchers.Main) {
                    playAudioFile(audioFile)
                }
                
                Log.d(TAG, "OpenAI TTS successful!")
                return@withContext true
            } else {
                Log.e(TAG, "OpenAI TTS failed: ${response.code()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error with OpenAI TTS", e)
            return@withContext false
        }
    }
    
    /**
     * استفاده از مدل آفلاین (کیفیت خوب)
     */
    private suspend fun speakWithOfflineModel(text: String): Boolean {
        try {
            Log.d(TAG, "Trying Offline Model TTS...")
            
            // TODO: پیاده‌سازی TTS با مدل محلی
            // در نسخه آینده می‌توان از مدل‌های TTS محلی استفاده کرد
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error with Offline Model TTS", e)
            return false
        }
    }
    
    /**
     * استفاده از Android TTS (fallback)
     */
    private fun speakWithAndroidTTS(text: String, urgent: Boolean) {
        try {
            val queueMode = if (urgent) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            
            androidTTS?.speak(text, queueMode, null, UUID.randomUUID().toString())
            Log.d(TAG, "Android TTS: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error with Android TTS", e)
        }
    }
    
    /**
     * پخش فایل صوتی
     */
    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                
                setOnCompletionListener {
                    it.release()
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio file", e)
        }
    }
    
    /**
     * تبدیل اعداد به فارسی
     */
    private fun convertNumbersToPersian(text: String): String {
        var result = text
        
        val numbers = mapOf(
            "0" to "صفر", "1" to "یک", "2" to "دو", "3" to "سه", "4" to "چهار",
            "5" to "پنج", "6" to "شش", "7" to "هفت", "8" to "هشت", "9" to "نه",
            "10" to "ده", "20" to "بیست", "30" to "سی", "40" to "چهل", "50" to "پنجاه",
            "60" to "شصت", "70" to "هفتاد", "80" to "هشتاد", "90" to "نود",
            "100" to "صد", "200" to "دویست", "300" to "سیصد", "400" to "چهارصد",
            "500" to "پانصد", "1000" to "هزار"
        )
        
        numbers.forEach { (english, persian) ->
            result = result.replace("\\b$english\\b".toRegex(), persian)
        }
        
        return result
    }
    
    /**
     * دریافت وضعیت TTS
     */
    fun getStatus(): String {
        return when (currentMode) {
            TTSMode.ONLINE_AI -> "🌟 OpenAI TTS (کیفیت عالی)"
            TTSMode.OFFLINE_MODEL -> "📱 مدل محلی (کیفیت خوب)"
            TTSMode.ANDROID_TTS -> "🔊 Android TTS (کیفیت متوسط)"
        }
    }
    
    /**
     * تغییر حالت TTS
     */
    fun setMode(mode: TTSMode) {
        currentMode = mode
        Log.d(TAG, "TTS mode changed to: ${getStatus()}")
    }
    
    /**
     * متوقف کردن پخش
     */
    fun stop() {
        try {
            androidTTS?.stop()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * بستن TTS
     */
    fun shutdown() {
        try {
            stop()
            androidTTS?.shutdown()
            androidTTS = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}
