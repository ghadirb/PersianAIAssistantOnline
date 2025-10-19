package com.persianai.assistant.utils

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.persianai.assistant.navigation.models.NavigationRoute
import com.persianai.assistant.navigation.models.NavigationStep
import kotlinx.coroutines.*
import java.util.*

/**
 * دستیار صوتی برای ناوبری فارسی
 */
class VoiceNavigationHelper(private val context: Context) : TextToSpeech.OnInitListener {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var isMuted = false
    private var navigationJob: Job? = null
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("fa", "IR"))
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // اگر فارسی پشتیبانی نشود، از انگلیسی استفاده می‌کنیم
                textToSpeech?.language = Locale.US
            }
            
            isInitialized = true
            
            // تنظیم listener برای مدیریت وضعیت پخش
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
            })
        }
    }
    
    /**
     * شروع ناوبری صوتی
     */
    fun startNavigation(route: NavigationRoute) {
        if (!isInitialized) {
            return
        }
        
        cancelNavigation()
        
        navigationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // خوشامدگویی اولیه
                speak("ناوبری شروع شد. به سمت مقصد حرکت کنید.")
                delay(2000)
                
                // TODO: NavigationRoute doesn't have steps property
                // خواندن دستورالعمل‌ها
                // route.steps.forEachIndexed { index, step ->
                //     if (!isMuted) {
                //         speakNavigationInstruction(step, index == 0)
                //         
                //         // انتظار برای رسیدن به مرحله بعد
                //         delay(step.duration * 1000 / route.steps.size)
                //     }
                // }
                
                // اعلام رسیدن به مقصد
                if (!isMuted) {
                    delay(route.duration * 1000)
                    speak("شما به مقصد رسیدید.")
                }
                
            } catch (e: Exception) {
                // مدیریت خطا
            }
        }
    }
    
    /**
     * خواندن دستورالعمل ناوبری
     */
    fun speakInstruction(step: NavigationStep) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val instruction = formatInstruction(step)
        speak(instruction)
    }
    
    /**
     * خواندن دستورالعمل ناوبری با فاصله
     */
    private fun speakNavigationInstruction(step: NavigationStep, isFirst: Boolean) {
        val instruction = when {
            isFirst -> "حرکت کنید " + formatInstruction(step)
            step.distance < 100 -> formatInstruction(step) // برای مسافت‌های کم، فقط دستورالعمل اصلی
            else -> "بعد از ${String.format("%.0f", step.distance)} متر، ${formatInstruction(step)}"
        }
        
        speak(instruction)
    }
    
    /**
     * فرمت کردن دستورالعمل به زبان فارسی
     */
    private fun formatInstruction(step: NavigationStep): String {
        val baseInstruction = step.instruction.lowercase()
        
        return when {
            baseInstruction.contains("right") || baseInstruction.contains("راست") -> {
                "به راست بپیچید"
            }
            baseInstruction.contains("left") || baseInstruction.contains("چپ") -> {
                "به چپ بپیچید"
            }
            baseInstruction.contains("straight") || baseInstruction.contains("مستقیم") -> {
                "مستقیم ادامه دهید"
            }
            baseInstruction.contains("uturn") || baseInstruction.contains("دور زدن") -> {
                "دور بزنید"
            }
            baseInstruction.contains("roundabout") || baseInstruction.contains("میدان") -> {
                if (baseInstruction.contains("first") || baseInstruction.contains("اول")) {
                    "در میدان از خروجی اول خارج شوید"
                } else if (baseInstruction.contains("second") || baseInstruction.contains("دوم")) {
                    "در میدان از خروجی دوم خارج شوید"
                } else if (baseInstruction.contains("third") || baseInstruction.contains("سوم")) {
                    "در میدان از خروجی سوم خارج شوید"
                } else {
                    "در میدان از خروجی مناسب خارج شوید"
                }
            }
            baseInstruction.contains("highway") || baseInstruction.contains("اتوبان") -> {
                "به اتوبان وارد شوید"
            }
            baseInstruction.contains("exit") || baseInstruction.contains("خروجی") -> {
                "از خروجی خارج شوید"
            }
            baseInstruction.contains("merge") || baseInstruction.contains("ادغام") -> {
                "در مسیر ادغام شوید"
            }
            baseInstruction.contains("fork") || baseInstruction.contains("انشعاب") -> {
                if (baseInstruction.contains("left") || baseInstruction.contains("چپ")) {
                    "در انشعاب به چپ بروید"
                } else {
                    "در انشعاب به راست بروید"
                }
            }
            baseInstruction.contains("arrive") || baseInstruction.contains("رسیدن") -> {
                "به مقصد رسیدید"
            }
            else -> {
                // اگر دستورالعمل شناسایی نشد، متن اصلی را با کمی اصلاح می‌خوانیم
                baseInstruction.replace("turn", "بپیچید")
                    .replace("continue", "ادامه دهید")
                    .replace("keep", "ادامه دهید")
            }
        }
    }
    
    /**
     * خواندن اطلاعات ترافیک
     */
    fun speakTrafficInfo(trafficLevel: String, delayMinutes: Int) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val message = when {
            delayMinutes > 10 -> "توجه: ترافیک سنگین است. حدود $delayMinutes دقیقه تأخیر دارید."
            delayMinutes > 5 -> "ترافیک متوسط است. حدود $delayMinutes دقیقه تأخیر دارید."
            else -> "ترافیک روان است."
        }
        
        speak(message)
    }
    
    /**
     * خواندن اطلاعات سرعت
     */
    fun speakSpeedLimit(speedLimit: Int, currentSpeed: Int) {
        if (!isInitialized || isMuted) {
            return
        }
        
        if (currentSpeed > speedLimit + 10) {
            speak("توجه: سرعت شما از محدودیت مجاز بیشتر است. محدودیت سرعت $speedLimit کیلومتر بر ساعت است.")
        }
    }
    
    /**
     * خواندن اطلاعات دوربین‌های سرعت
     */
    fun speakSpeedCameraAlert(distance: Int) {
        if (!isInitialized || isMuted) {
            return
        }
        
        speak("توجه: در ${String.format("%.0f", distance)} متر دیگر دوربین کنترل سرعت وجود دارد.")
    }
    
    /**
     * خواندن اطلاعات پارکینگ
     */
    fun speakParkingInfo(parkingName: String, availableSpots: Int, distance: Int) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val message = when {
            availableSpots > 20 -> "در ${String.format("%.0f", distance)} متر دیگر، پارکینگ $parkingName با $availableSpots جای خالی وجود دارد."
            availableSpots > 5 -> "در ${String.format("%.0f", distance)} متر دیگر، پارکینگ $parkingName با $availableSpots جای خالی وجود دارد."
            availableSpots > 0 -> "در ${String.format("%.0f", distance)} متر دیگر، پارکینگ $parkingName فقط $availableSpots جای خالی دارد."
            else -> "در ${String.format("%.0f", distance)} متر دیگر، پارکینگ $parkingName پر است."
        }
        
        speak(message)
    }
    
    /**
     * خواندن اطلاعات بنزین‌زایی
     */
    fun speakGasStationInfo(stationName: String, isOpen: Boolean, queueLevel: Int, distance: Int) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val status = if (isOpen) "باز است" else "بسته است"
        val queue = when (queueLevel) {
            0 -> "بدون صف"
            1 -> "صف کوتاه"
            2 -> "صف متوسط"
            3 -> "صف طولانی"
            else -> "اطلاعاتی موجود نیست"
        }
        
        val message = "در ${String.format("%.0f", distance)} متر دیگر، پمپ بنزین $stationName $status و $queue دارد."
        speak(message)
    }
    
    /**
     * خواندن هشدارهای ایمنی
     */
    fun speakSafetyAlert(alertType: String, message: String) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val alertMessage = when (alertType) {
            "accident" -> "هشدار: تصادف در مسیر. احتیاط کنید."
            "construction" -> "توجه: کارهای ساختمانی در جریان است."
            "road_closure" -> "هشدار: جاده بسته است. در حال پیدا کردن مسیر جایگزین..."
            "weather" -> "توجه: شرایط جوی نامساعد است. با احتیاط رانندگی کنید."
            else -> message
        }
        
        speak(alertMessage)
    }
    
    /**
     * خواندن زمان تخمینی رسیدن
     */
    fun speakETA(remainingMinutes: Int, remainingDistance: Double) {
        if (!isInitialized || isMuted) {
            return
        }
        
        val timeText = when {
            remainingMinutes < 60 -> "$remainingMinutes دقیقه"
            else -> "${remainingMinutes / 60} ساعت و ${remainingMinutes % 60} دقیقه"
        }
        
        val distanceText = if (remainingDistance < 1000) {
            "${String.format("%.0f", remainingDistance)} متر"
        } else {
            "${String.format("%.1f", remainingDistance / 1000)} کیلومتر"
        }
        
        speak("زمان تخمینی رسیدن: $timeText. مسافت باقی‌مانده: $distanceText.")
    }
    
    /**
     * توقف ناوبری صوتی
     */
    fun stopNavigation() {
        cancelNavigation()
        if (!isMuted) {
            speak("ناوبری متوقف شد.")
        }
    }
    
    /**
     * لغو job ناوبری
     */
    private fun cancelNavigation() {
        navigationJob?.cancel()
        navigationJob = null
    }
    
    /**
     * قطع/وصل کردن صدا
     */
    fun toggleMute() {
        isMuted = !isMuted
        
        if (isMuted) {
            textToSpeech?.stop()
            speak("صدای ناوبری قطع شد.")
        } else {
            speak("صدای ناوبری وصل شد.")
        }
    }
    
    /**
     * بررسی وضعیت mute
     */
    fun isMuted(): Boolean = isMuted
    
    /**
     * تنظیم حجم صدا
     */
    fun setVolume(volume: Float) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (volume * maxVolume).toInt()
        
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }
    
    /**
     * خواندن متن
     */
    private fun speak(text: String) {
        if (!isInitialized || isMuted) {
            return
        }
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }
    
    /**
     * پاک کردن منابع
     */
    fun cleanup() {
        cancelNavigation()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
    
    /**
     * بررسی وضعیت اولیه‌سازی
     */
    fun isReady(): Boolean = isInitialized
}
