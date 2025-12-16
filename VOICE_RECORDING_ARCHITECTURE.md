# 🎙️ معماری سیستم ضبط صدای ترکیبی (Hybrid Voice Recording System)

## 📋 خلاصه اجرایی

سیستم جدید برای **حل مشکل کرش میکروفن** و **فعال‌کردن هشدارهای تمام‌صفحه در پس‌زمینه** ایجاد شده است.

**مشکلات قبلی:**
- ❌ کلیک بر روی میکروفن باعث کرش برنامه می‌شود
- ❌ HybridVoiceRecorder استثناهای نامدیریت‌شده دارد
- ❌ MediaRecorder بدون تمیز‌کاری منابع
- ❌ هشدارهای یادآوری در پس‌زمینه کار نمی‌کنند

**راه‌حل:**
- ✅ HybridVoiceRecorder با مدیریت استثناء شامل
- ✅ VoiceRecorderViewNew - کامپوننت UI جدید و امن
- ✅ VoiceRecordingService - سرویس پیش‌زمینه قابل اعتماد
- ✅ تمام منابع تمیز‌شده شوند (cleanup)
- ✅ پشتیبانی از آفلاین/آنلاین

---

## 🏗️ نمودار معماری

```
┌─────────────────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER (لایه نمایش)               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ Activities / Fragments                                   │  │
│  │ (MainActivity, BaseChatActivity, VoiceNavigationAct...)  │  │
│  │                                                          │  │
│  │ • Touch Event Handlers                                  │  │
│  │ • UI Updates                                            │  │
│  │ • Listener Callbacks                                    │  │
│  └────────────────────┬─────────────────────────────────────┘  │
│                       │                                         │
│  ┌────────────────────▼──────────────────────────────────────┐  │
│  │ UI Components                                            │  │
│  │ • VoiceRecorderView (Old - Compatible)                  │  │
│  │ • VoiceRecorderViewNew (New - Recommended)              │  │
│  │ • Waveform Display                                      │  │
│  │ • Animation Controllers                                 │  │
│  └────────────────────┬──────────────────────────────────────┘  │
│                       │                                         │
└───────────────────────┼─────────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────────┐
│                DOMAIN LAYER (لایه حوزه)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ VoiceRecordingHelper                                     │  │
│  │ (ساده‌ترین API برای استفاده در Activities)             │  │
│  │                                                          │  │
│  │ PUBLIC: startRecording()                                │  │
│  │ PUBLIC: stopRecording()                                 │  │
│  │ PUBLIC: cancelRecording()                               │  │
│  │ PUBLIC: setListener(RecordingListener)                  │  │
│  └────────────────────┬──────────────────────────────────────┘  │
│                       │                                         │
│  ┌────────────────────▼──────────────────────────────────────┐  │
│  │ HybridVoiceRecorder (Core Recording Service)            │  │
│  │                                                          │  │
│  │ PRIVATE: mediaRecorder: MediaRecorder                   │  │
│  │ PRIVATE: coroutineScope: CoroutineScope                 │  │
│  │ PRIVATE: audioFile: File                                │  │
│  │                                                          │  │
│  │ PUBLIC: startRecording()                                │  │
│  │ PUBLIC: stopRecording()                                 │  │
│  │ PUBLIC: cancelRecording()                               │  │
│  │ PUBLIC: analyzeHybrid() - Offline + Online              │  │
│  │ PUBLIC: analyzeOffline() - Haaniye Model                │  │
│  │ PUBLIC: analyzeOnline() - Qwen/aimlapi                  │  │
│  │ PRIVATE: startAmplitudeMonitoring()                     │  │
│  │ PRIVATE: cleanup()                                      │  │
│  │                                                          │  │
│  │ LISTENER: RecorderListener                              │  │
│  │   • onRecordingStarted()                                │  │
│  │   • onRecordingCompleted(file, duration)                │  │
│  │   • onRecordingCancelled()                              │  │
│  │   • onRecordingError(error)                             │  │
│  │   • onAmplitudeChanged(amplitude)                       │  │
│  └────────────────────┬──────────────────────────────────────┘  │
│                       │                                         │
└───────────────────────┼─────────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────────┐
│                DATA & SERVICES LAYER (لایه خدمات)              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ VoiceRecordingService (LifecycleService)                │  │
│  │ (سرویس پیش‌زمینه برای ضبط صدای موثر)                   │  │
│  │                                                          │  │
│  │ INTENT ACTIONS:                                         │  │
│  │ • START_RECORDING - شروع ضبط                          │  │
│  │ • STOP_RECORDING - توقف ضبط                           │  │
│  │ • CANCEL_RECORDING - لغو ضبط                           │  │
│  │                                                          │  │
│  │ BINDER: VoiceRecordingBinder                            │  │
│  │ • getRecordingDuration()                                │  │
│  │ • isRecording()                                         │  │
│  │                                                          │  │
│  │ FOREGROUND: microphone                                  │  │
│  │ LIFECYCLE: LifecycleService (بهتر از Service)           │  │
│  └────────────────────┬──────────────────────────────────────┘  │
│                       │                                         │
│  ┌────────────────────▼──────────────────────────────────────┐  │
│  │ MediaRecorder + Audio Processing                        │  │
│  │                                                          │  │
│  │ • Recording State Management                            │  │
│  │ • Audio Encoding                                        │  │
│  │ • File Output                                           │  │
│  │ • Exception Handling                                    │  │
│  └────────────────────┬──────────────────────────────────────┘  │
│                       │                                         │
│  ┌────────────────────▼──────────────────────────────────────┐  │
│  │ AI Models Layer                                         │  │
│  │                                                          │  │
│  │ ┌────────────────────┐  ┌──────────────────────────┐    │  │
│  │ │ Offline: Haaniye   │  │ Online: Qwen + aimlapi   │    │  │
│  │ │                    │  │                          │    │  │
│  │ │ • STT Processing   │  │ • Network Requests       │    │  │
│  │ │ • Fast (~500ms)    │  │ • Complex Analysis       │    │  │
│  │ │ • No Internet      │  │ • Slower (2-5s)          │    │  │
│  │ │ • ~50MB Storage    │  │ • Requires Internet      │    │  │
│  │ │                    │  │ • API Keys Needed        │    │  │
│  │ └────────────────────┘  └──────────────────────────┘    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 جریان کار کامل (Complete Workflow)

### 1️⃣ شروع ضبط صدا

```
┌─ User Clicks Mic Button ─┐
│                         │
├─ Activity Touch Event  ──┤
│                         │
├─ VoiceRecorderView     ──┤
│   .onTouchEvent()       │
│                         │
├─ ACTION_DOWN            ──┤
│   startRecording()       │
│                         │
├─ HybridVoiceRecorder    ──┤
│   .startRecording()      │
│                         │
├─ Try:                   ──┤
│   • MediaRecorder Init  │
│   • Encoding Setup      │
│   • Start Recording     │
│                         │
├─ Catch Exception:       ──┤
│   • Log Error            │
│   • onRecordingError()   │
│                         │
├─ Finally:               ──┤
│   • startAmplitude       │
│     Monitoring()         │
│                         │
└─ Call Listener          ─┘
   onRecordingStarted()
```

### 2️⃣ نظارت بر ضبط صدا

```
┌─ Every 100ms ───────────────┐
│                            │
├─ Amplitude Check          ──┤
│   mediaRecorder            │
│   .getMaxAmplitude()       │
│                            │
├─ onAmplitudeChanged()     ──┤
│   (Listener Callback)      │
│                            │
├─ UI Update                ──┤
│   • Waveform Drawing      │
│   • Duration Update       │
│   • Animation             │
│                            │
└─ Keep Recording ──────────┘
   (Until User Releases)
```

### 3️⃣ توقف ضبط صدا

```
┌─ ACTION_UP or CANCEL ──────┐
│                           │
├─ HybridVoiceRecorder      ──┤
│   .stopRecording()         │
│                           │
├─ Try:                     ──┤
│   • Stop Monitoring       │
│   • MediaRecorder.stop()  │
│   • MediaRecorder.release()
│                           │
├─ Call Listener            ──┤
│   onRecordingCompleted()   │
│   (file, duration)         │
│                           │
├─ Start Analysis           ──┤
│   analyzeHybrid()          │
│   • Offline (Fast)        │
│   • Online (Async)        │
│                           │
└─ Result Delivery ─────────┘
   (Send to AI or UI)
```

### 4️⃣ تحلیل ترکیبی (Hybrid Analysis)

```
┌─ analyzeHybrid(audioFile) ─┐
│                            │
├─ Launch Coroutines        ──┤
│   (withContext(IO))        │
│                            │
├─ Parallel Processing:     ──┤
│   ├─ analyzeOffline()     │
│   │   • Load Haaniye      │
│   │   • STT Processing    │
│   │   • Result: Text      │
│   │                        │
│   └─ analyzeOnline()      │
│       • Send to API        │
│       • Parse Response     │
│       • Result: Entity     │
│                            │
├─ Combine Results          ──┤
│   • Merge Findings        │
│   • Select Best Option    │
│   • Confidence Scoring    │
│                            │
├─ Back to Main Thread      ──┤
│   (Dispatchers.Main)       │
│                            │
└─ Deliver Result ──────────┘
   onAnalysisComplete()
```

### 5️⃣ هشدار تمام‌صفحه در پس‌زمینه

```
┌─ ReminderService ──────────┐
│ (Background Check)         │
│                            │
├─ Timer Task               ──┤
│   Check Reminders         │
│                            │
├─ Reminder Time Reached    ──┤
│   Send Broadcast          │
│                            │
├─ ReminderReceiver         ──┤
│   onReceive()             │
│                            │
├─ Create Intent            ──┤
│   FullScreenAlarmActivity │
│   + fullScreenIntent      │
│                            │
├─ Create Notification      ──┤
│   • Channel: max priority │
│   • Heads-up              │
│   • Full-screen intent    │
│                            │
├─ Acquire WakeLock        ──┤
│   FULL_WAKE_LOCK          │
│   (Keep device awake)     │
│                            │
├─ Launch Activity          ──┤
│   FullScreenAlarmActivity │
│                            │
├─ User Action              ──┤
│   • Swipe: Dismiss        │
│   • Button: Snooze        │
│   • Tap: View Details     │
│                            │
└─ Release Resources ───────┘
   (WakeLock, Notification)
```

---

## 📂 ساختار فایل‌ها

```
app/src/main/java/com/persianai/assistant/

├── services/
│   ├── VoiceRecordingService.kt ................... [NEW] سرویس پیش‌زمینه
│   ├── HybridVoiceRecorder.kt ..................... [NEW] ضبط‌کننده ترکیبی
│   ├── VoiceRecordingHelper.kt .................... [NEW] کمک‌کننده ساده
│   ├── ReminderService.kt ........................ [EXISTING] یادآوری
│   ├── ReminderReceiver.kt ........................ [EXISTING] دریافت‌کننده
│   └── FullScreenAlarmActivity.kt ................ [MODIFIED] دسترسی تمام‌صفحه
│
├── views/
│   ├── VoiceRecorderView.kt ....................... [FIXED] نسخه کهن (سازگاری)
│   └── VoiceRecorderViewNew.kt .................... [NEW] نسخه جدید (توصیه‌شده)
│
├── activities/
│   ├── MainActivity.kt ............................ [NEEDS UPDATE]
│   ├── BaseChatActivity.kt ........................ [NEEDS UPDATE]
│   ├── VoiceNavigationAssistantActivity.kt ........ [FIXED]
│   └── AIChatActivity.kt .......................... [NEEDS UPDATE]
│
└── utils/
    └── AIClient.kt ............................... [EXISTING] API
```

---

## 🔐 مدیریت منابع (Resource Management)

### اشتغال (Initialization)

```kotlin
class HybridVoiceRecorder {
    private val mediaRecorder = MediaRecorder(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var audioFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
}
```

### تمیز‌کاری (Cleanup)

```kotlin
private fun cleanup() {
    try {
        // 1. Stop Monitoring
        amplitudeHandler?.removeCallbacksAndMessages(null)
        amplitudeHandler = null
        
        // 2. Release MediaRecorder
        mediaRecorder?.apply {
            reset()
            release()
        }
        mediaRecorder = null
        
        // 3. Cancel Coroutines
        coroutineScope?.cancel()
        
        // 4. Delete Failed File
        if (audioFile?.exists() == true) {
            audioFile?.delete()
        }
    } catch (e: Exception) {
        Log.e("HybridVoiceRecorder", "Cleanup error", e)
    }
}
```

---

## 🧵 مدل Thread‌ها (Threading Model)

```
┌─ Main Thread ─────────────────┐
│ • UI Updates                  │
│ • View Rendering              │
│ • Listener Callbacks          │
└───────────────────────────────┘

┌─ Dispatcher.IO ───────────────┐
│ • File Operations             │
│ • Network Requests            │
│ • Heavy Processing            │
│ • Model Inference             │
└───────────────────────────────┘

┌─ Dispatcher.Default ──────────┐
│ • CPU-Intensive Tasks         │
│ • Data Processing             │
│ • Calculations                │
└───────────────────────────────┘
```

---

## ⚙️ تنظیمات (Configuration)

### Manifest Entries

```xml
<!-- Service Declaration -->
<service
    android:name=".services.VoiceRecordingService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone" />

<!-- Required Permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

### Audio Configuration

```kotlin
// Sample Rate: 44.1 kHz (Industry Standard)
mediaRecorder.setAudioSamplingRate(44100)

// Channels: Mono (1 channel for voice)
mediaRecorder.setAudioChannels(1)

// Encoding: AAC
mediaRecorder.setAudioEncoder(AudioFormat.ENCODING_AAC)

// Bitrate: 128 kbps
mediaRecorder.setAudioEncodingBitRate(128000)
```

---

## 📊 مقایسه عملکردی (Performance Comparison)

| بخش | زمان | حافظه | نکات |
|-----|------|-------|------|
| شروع ضبط | 100-200ms | 1-2MB | یک بار |
| نظارت دامنه | <10ms | ناچیز | هر 100ms |
| توقف ضبط | 50-100ms | 1MB | یک بار |
| تحلیل آفلاین | 300-500ms | 50MB | موازی |
| تحلیل آنلاین | 2-5s | 5-10MB | موازی |
| **کل** | **2-5s** | **~70MB** | **Hybrid** |

---

## 🔍 نقاط حرج (Critical Points)

1. **MediaRecorder Lifecycle**
   - ✅ دقیق: Initialize → Prepare → Start → Stop → Release
   - ❌ اشتباه: Multiple start() یا double release()

2. **Exception Handling**
   - ✅ تمام استثناها گیر خورده شده
   - ❌ Uncaught exceptions = کرش

3. **Resource Cleanup**
   - ✅ Finally block با cleanup()
   - ❌ Missing cleanup = نشت حافظه

4. **Coroutine Scope**
   - ✅ LifecycleScope binding
   - ❌ Orphaned coroutines = نشت حافظه

5. **Background Services**
   - ✅ LifecycleService بجای Service
   - ❌ Normal Service = می‌کشد

---

## 🧪 تست‌کردن (Testing)

### Unit Tests

```kotlin
@Test
fun testHybridVoiceRecorderStartStop() {
    val recorder = HybridVoiceRecorder(context)
    val listener = MockRecorderListener()
    
    recorder.setListener(listener)
    recorder.startRecording()
    
    // Wait for recording
    Thread.sleep(1000)
    
    recorder.stopRecording()
    
    // Assertions
    verify(listener).onRecordingStarted()
    verify(listener).onRecordingCompleted(any(), anyLong())
}
```

### Integration Tests

```kotlin
@Test
fun testAnalysisHybridMode() {
    // Test offline + online
    val file = createTestAudioFile()
    
    runBlocking {
        val result = recorder.analyzeHybrid(file)
        
        assertNotNull(result)
        assertTrue(result.hasOfflineResult)
        assertTrue(result.hasOnlineResult)
    }
}
```

---

## 📚 منابع (Resources)

- [Android MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [LifecycleService](https://developer.android.com/reference/androidx/lifecycle/LifecycleService)
- [Foreground Services](https://developer.android.com/develop/background-work/services/foreground-services)

---

**نسخه:** 1.0  
**آخرین بروزرسانی:** 2024  
**وضعیت:** در حال پیاده‌سازی
