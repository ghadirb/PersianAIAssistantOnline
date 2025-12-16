# 🎤 سیستم ضبط صدای ترکیبی (Hybrid Voice Recording System)

## نمای کلی

سیستم جدید ضبط صدا با قابلیت‌های:
- ✅ **Offline Processing**: استفاده از مدل Haaniye برای تحلیل فوری بدون اینترنت
- ✅ **Online Processing**: آپلود به Qwen2.5, aimlapi برای تحلیل پیشرفته
- ✅ **Hybrid Mode**: ترکیب هر دو برای بهترین نتیجه
- ✅ **خالی از Crash**: مدیریت منابع صحیح و Exception Handling
- ✅ **Full-Screen Alerts in Background**: اعلانات تمام‌صفحه حتی وقتی برنامه بسته است

---

## 📦 اجزای سیستم

### 1. **HybridVoiceRecorder.kt**
سیستم اصلی ضبط صدا با:
- مدیریت صحیح MediaRecorder
- Amplitude monitoring
- پردازش Offline/Online

```kotlin
// استفاده:
val recorder = HybridVoiceRecorder(context, coroutineScope)
recorder.setListener(object : HybridVoiceRecorder.RecorderListener {
    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
        // تحلیل ترکیبی
        launch {
            val result = recorder.analyzeHybrid(audioFile)
        }
    }
})
recorder.startRecording()
```

### 2. **VoiceRecorderViewNew.kt**
UI Component برای نمایش:
- انیمیشن‌های Pulse
- Waveform drawing
- Swipe to cancel
- بدون crash

### 3. **VoiceRecordingService.kt**
Foreground Service برای ضبط در Background:
- LifecycleService برای مدیریت بهتر
- Async Recording
- Foreground notification

### 4. **VoiceRecordingHelper.kt**
Helper class برای استفاده آسان:
```kotlin
val helper = VoiceRecordingHelper(context, this) // this = Activity/Fragment
helper.startRecording()
helper.stopRecording()
```

---

## 🚀 نحوه استفاده در Activities

### گزینه 1: استفاده مستقیم (ساده)
```kotlin
class MyActivity : AppCompatActivity() {
    private var recorder: HybridVoiceRecorder? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        recorder = HybridVoiceRecorder(this, lifecycleScope)
        
        binding.micButton.setOnClickListener {
            if (recorder?.isRecordingInProgress() == true) {
                recorder?.stopRecording()
            } else {
                recorder?.startRecording()
            }
        }
    }
}
```

### گزینه 2: استفاده از Service (برای Background)
```kotlin
val intent = Intent(context, VoiceRecordingService::class.java).apply {
    action = "START_RECORDING"
}
startService(intent)

// توقف:
intent.action = "STOP_RECORDING"
startService(intent)
```

---

## 🔧 مقابله با مشکلات قبلی

### مشکل 1: Crash هنگام کلیک میکروفن
**حل**: 
- ✅ مدیریت Exception صحیح
- ✅ Null-safety برای MediaRecorder
- ✅ cleanup() در finalize

### مشکل 2: Full-Screen Alert در Background
**حل**:
- ✅ استفاده از FullScreenIntent
- ✅ WakeLock مدیریت شده
- ✅ SYSTEM_ALERT_WINDOW permission
- ✅ ReminderReceiver بهبود شده

### مشکل 3: مصرف منابع زیاد
**حل**:
- ✅ coroutineScope.cancel() در onDestroy
- ✅ Handler.removeCallbacks() برای amplitude
- ✅ mediaRecorder.release() فوری

---

## 🎯 Offline Model (Haaniye)

مسیر مدل:
```
app/src/main/assets/tts/haaniye/
```

استفاده برای:
- تشخیص فوری گفتار
- تحلیل بدون تأخیر
- کار در مناطق بدون اینترنت

```kotlin
// در HybridVoiceRecorder.kt - analyzeOffline()
suspend fun analyzeOffline(audioFile: File): String? = withContext(Dispatchers.IO) {
    // بارگذاری مدل Haaniye
    // تحلیل فایل صوتی
    // برگرداندن نتیجه
}
```

---

## 🌐 Online Models

### Qwen 2.5 1.5B
```kotlin
val model = AIModel.QWEN_2_5_1B5
val result = aiClient.analyze(audioFile, model)
```

### aimlapi
```kotlin
// آپلود و تحلیل
val result = uploadToAimlApi(audioFile)
```

---

## 📝 اصلاحات انجام شده

### ✅ VoiceRecorderView.kt
قبل: استفاده مستقیم MediaRecorder
بعد: استفاده از HybridVoiceRecorder

### ✅ AndroidManifest.xml
اضافه شد:
- `VoiceRecordingService` declaration
- `foregroundServiceType="microphone"`
- `SYSTEM_ALERT_WINDOW` permission

### ✅ FullScreenAlarmActivity.kt
اضافه شد:
- Display over other apps support
- Background handling بهتر
- تبصره‌های کد

---

## 🧪 تست

### تست ضبط صدا:
```kotlin
val recorder = HybridVoiceRecorder(context, lifecycleScope)
recorder.setListener(object : HybridVoiceRecorder.RecorderListener {
    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
        Log.d("Test", "✅ Recording complete: ${audioFile.absolutePath}")
        // تحلیل ترکیبی
    }
})
recorder.startRecording()
// بعد از 5 ثانیه:
recorder.stopRecording()
```

### تست Full-Screen Alert:
```kotlin
val reminder = SmartReminder(
    title = "تست هشدار",
    alertType = AlertType.FULL_SCREEN,
    triggerTime = System.currentTimeMillis() + 2000
)
smartReminderManager.addReminder(reminder)
```

---

## ⚠️ نکات مهم

1. **Permission**: `RECORD_AUDIO` را تایید کنید
2. **WakeLock**: در Background، دستگاه بیدار باشد
3. **Cleanup**: همیشه در onDestroy منابع را آزاد کنید
4. **Logging**: برای debugging `Log.d(TAG, "message")` استفاده کنید

---

## 📚 منابع

- [Android MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder)
- [Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [Full Screen Intent](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setFullScreenIntent(androidx.core.app.PendingIntent,%20boolean))
- [WakeLock Best Practices](https://developer.android.com/training/scheduling/wakelock)

---

## 🐛 دیباگینگ

### لاگ‌های مهم:
```bash
# مشاهده لاگ‌های ضبط صدا
adb logcat | grep HybridVoiceRecorder

# مشاهده لاگ‌های Service
adb logcat | grep VoiceRecordingService

# مشاهده لاگ‌های هشدار
adb logcat | grep FullScreenAlarm
```

---

## 🎓 مثال کامل

```kotlin
class MainActivity : AppCompatActivity() {
    private var recorder: HybridVoiceRecorder? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // آماده‌سازی Recorder
        recorder = HybridVoiceRecorder(this, lifecycleScope)
        recorder?.setListener(object : HybridVoiceRecorder.RecorderListener {
            override fun onRecordingStarted() {
                Log.d("Test", "✅ Recording started")
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                Log.d("Test", "✅ Recording complete: $durationMs ms")
                
                lifecycleScope.launch {
                    // تحلیل ترکیبی
                    val result = recorder?.analyzeHybrid(audioFile)
                    Log.d("Test", "📊 Analysis result: $result")
                }
            }
            
            override fun onRecordingCancelled() {
                Log.d("Test", "❌ Recording cancelled")
            }
            
            override fun onRecordingError(error: String) {
                Log.e("Test", "❌ Recording error: $error")
            }
            
            override fun onAmplitudeChanged(amplitude: Int) {
                // نمایش شدت صدا
            }
        })
        
        // دکمه میکروفن
        findViewById<View>(R.id.micButton).apply {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        recorder?.startRecording()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        recorder?.stopRecording()
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // پاکسازی
    }
}
```

---

## ✨ نتیجهٔ نهایی

سیستم جدید:
- ✅ بدون crash
- ✅ Full-screen alerts در background
- ✅ تحلیل offline + online
- ✅ مدیریت منابع صحیح
- ✅ تجربهٔ کاربر بهتر
