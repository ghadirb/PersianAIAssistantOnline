# 📑 فهرست کامل سیستم ضبط صدای ترکیبی

## 🎯 شروع سریع

اگر تازه شروع کردی‌د، از اینجا بخوانید:

**👉 [README_HYBRID_VOICE_SYSTEM.md](README_HYBRID_VOICE_SYSTEM.md)** - نمای کلی 5 دقیقه‌ای

---

## 📚 داکومنتیشن کامل

### 1️⃣ معماری و طراحی

**📖 [VOICE_RECORDING_ARCHITECTURE.md](VOICE_RECORDING_ARCHITECTURE.md)**
- معماری کامل سیستم
- نمودارهای جریان کار
- بخش‌های مختلف سیستم
- مدل threading
- مدیریت منابع
- تنظیمات

**موضوعات:**
- [ ] نمودار لایه‌ای (Layered Architecture)
- [ ] جریان کار ضبط صدا (Recording Workflow)
- [ ] جریان کار تجزیه و تحلیل (Analysis Workflow)
- [ ] جریان کار هشدار (Alert Workflow)
- [ ] Data Flow
- [ ] API Interfaces
- [ ] Threading Model
- [ ] Configuration Details
- [ ] Performance Metrics
- [ ] Permissions

---

### 2️⃣ راهنمای اتصال (Integration)

**📖 [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md)**
- نحوه اتصال در Activities
- Code examples برای هر Activity
- Layout XML updates
- Permission handling
- Test procedures

**Activities شامل:**
- [ ] MainActivity.kt - صفحه اصلی چت
- [ ] BaseChatActivity.kt - کلاس پایه
- [ ] AIChatActivity.kt - Chat AI
- [ ] VoiceNavigationAssistantActivity.kt - دستیار صوتی

**موضوعات:**
- [ ] Code samples
- [ ] Layout XML examples
- [ ] Permission checks
- [ ] Error handling
- [ ] Testing guide

---

### 3️⃣ تست و رفع عیب

**📖 [TESTING_DEBUGGING_GUIDE.md](TESTING_DEBUGGING_GUIDE.md)**
- Unit tests کامل
- Integration tests
- Device tests
- Debugging strategies
- Performance analysis
- Error scenarios

**موضوعات:**
- [ ] Unit Tests for HybridVoiceRecorder
- [ ] Unit Tests for VoiceRecordingHelper
- [ ] Integration Tests for Service
- [ ] Device Test Scenarios
- [ ] Manual Testing Checklist
- [ ] Log Filtering Commands
- [ ] Debug Points
- [ ] Profiling Guide
- [ ] Performance Metrics
- [ ] Common Errors & Solutions

---

### 4️⃣ خلاصه حل

**📖 [SOLUTION_SUMMARY.md](SOLUTION_SUMMARY.md)**
- خلاصه مشکلات و حل‌ها
- فایل‌های ایجاد‌شده
- مراحل اجرا
- Checklist
- Performance metrics
- Next steps

---

### 5️⃣ راهنمای سریع

**📖 [README_HYBRID_VOICE_SYSTEM.md](README_HYBRID_VOICE_SYSTEM.md)**
- نمای کلی (5 دقیقه)
- Quick start
- Code examples
- Troubleshooting
- Testing checklist

---

### 6️⃣ چکلیست پیاده‌سازی

**📖 [IMPLEMENTATION_CHECKLIST.md](IMPLEMENTATION_CHECKLIST.md)**
- Checklist مفصل
- مراحل پیاده‌سازی
- Time estimates
- Success criteria

---

## 🗂️ فایل‌های کد

### سرویس‌ها (Services)

#### 1. HybridVoiceRecorder.kt
**📍 Location:** `app/src/main/java/.../services/HybridVoiceRecorder.kt`

**مسئولیت:** ضبط صدا با مدیریت استثناء و تحلیل ترکیبی

**Key Methods:**
```kotlin
fun startRecording()
fun stopRecording()
fun cancelRecording()
suspend fun analyzeOffline(audioFile: File): OfflineAnalysisResult
suspend fun analyzeOnline(audioFile: File): OnlineAnalysisResult
suspend fun analyzeHybrid(audioFile: File): HybridAnalysisResult
```

**Key Features:**
- ✅ Exception handling
- ✅ Resource cleanup
- ✅ Amplitude monitoring
- ✅ Offline/Online analysis

---

#### 2. VoiceRecordingService.kt
**📍 Location:** `app/src/main/java/.../services/VoiceRecordingService.kt`

**مسئولیت:** سرویس پیش‌زمینه برای ضبط صدا

**Key Methods:**
```kotlin
override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int
fun startRecording()
fun stopRecording()
fun cancelRecording()
fun isRecording(): Boolean
fun getRecordingDuration(): Long
```

**Key Features:**
- ✅ LifecycleService
- ✅ Foreground service
- ✅ Intent-based control
- ✅ Background support

---

#### 3. VoiceRecordingHelper.kt
**📍 Location:** `app/src/main/java/.../services/VoiceRecordingHelper.kt`

**مسئولیت:** API ساده برای استفاده در Activities

**Key Methods:**
```kotlin
fun startRecording()
fun stopRecording()
fun cancelRecording()
fun setListener(listener: RecordingListener)
```

**Key Features:**
- ✅ Simple API
- ✅ Callback listeners
- ✅ Permission checks
- ✅ Lifecycle-aware

---

### UI Components

#### 4. VoiceRecorderViewNew.kt
**📍 Location:** `app/src/main/java/.../views/VoiceRecorderViewNew.kt`

**مسئولیت:** Custom View برای ضبط صدا

**Key Methods:**
```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean
private fun startRecording()
private fun stopRecording()
private fun cancelRecording()
private fun updateWaveform(amplitude: Int)
```

**Key Features:**
- ✅ Custom View
- ✅ Touch handling
- ✅ Waveform animation
- ✅ HybridVoiceRecorder integration

---

## 📊 Dependency Map

```
Activities (MainActivity, etc.)
    ↓
VoiceRecordingHelper (Simple API)
    ↓
HybridVoiceRecorder (Core Engine)
    ├─ MediaRecorder (Android SDK)
    ├─ Coroutines (Kotlin)
    └─ File I/O (System)

VoiceRecordingService (Background)
    ├─ VoiceRecordingHelper
    └─ HybridVoiceRecorder

VoiceRecorderViewNew (UI)
    └─ HybridVoiceRecorder
```

---

## 🎯 قبل و بعد

### ❌ BEFORE
```
┌─ User clicks mic ─────────────┐
│                              │
├─ MediaRecorder init error    ├→ 💥 CRASH
│  (No error handling)         │
│                              │
├─ App closes                  │
│                              │
└─ Data lost ──────────────────┘
```

### ✅ AFTER
```
┌─ User clicks mic ──────────────────────┐
│                                       │
├─ VoiceRecordingHelper                 ├→ Safe
│                                       │
├─ HybridVoiceRecorder                  │
│  (Exception handling)                 │
│                                       │
├─ MediaRecorder starts                 │
│  (Protected with try-catch)           │
│                                       │
├─ Audio recorded                       │
│                                       │
├─ Analysis:                            │
│  ├─ Offline (Haaniye) - Fast         │
│  └─ Online (Qwen/aimlapi) - Async    │
│                                       │
└─ Result delivered ────────────────────┘
```

---

## 🚀 سناریوهای استفاده

### Scenario 1: Simple Recording

```kotlin
// In Activity
val helper = VoiceRecordingHelper(this)

helper.setListener(object : VoiceRecordingHelper.RecordingListener {
    override fun onRecordingCompleted(file: File, duration: Long) {
        // Handle audio
    }
})

findViewById<Button>(R.id.recordButton).setOnClickListener {
    helper.startRecording()
}
```

---

### Scenario 2: Background Recording

```kotlin
// Start service
val intent = Intent(context, VoiceRecordingService::class.java).apply {
    action = "START_RECORDING"
}
context.startForegroundService(intent)

// Stop service
val stopIntent = Intent(context, VoiceRecordingService::class.java).apply {
    action = "STOP_RECORDING"
}
context.startService(stopIntent)
```

---

### Scenario 3: Hybrid Analysis

```kotlin
val recorder = HybridVoiceRecorder(context)

recorder.setListener(object : HybridVoiceRecorder.RecorderListener {
    override fun onRecordingCompleted(file: File, duration: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            val result = recorder.analyzeHybrid(file)
            
            // Use both offline and online results
            println("Offline: ${result.offlineResult.text}")
            println("Online: ${result.onlineResult.analysis}")
            println("Combined: ${result.combinedText}")
        }
    }
})

recorder.startRecording()
```

---

## 📋 Workflow Examples

### Complete Recording & Analysis Flow

```
1. User clicks Mic
   └─ VoiceRecordingHelper.startRecording()
      └─ HybridVoiceRecorder.startRecording()
         └─ MediaRecorder.start()
            └─ Amplitude monitoring started

2. Recording in progress
   └─ Every 100ms: Amplitude update
      └─ UI: Waveform animated

3. User releases Mic
   └─ VoiceRecordingHelper.stopRecording()
      └─ HybridVoiceRecorder.stopRecording()
         └─ MediaRecorder.stop()
            └─ onRecordingCompleted callback

4. Analysis starts
   ├─ Parallel:
   │  ├─ analyzeOffline() (300-500ms)
   │  │  └─ Load Haaniye model
   │  │  └─ Process audio
   │  │  └─ STT result
   │  │
   │  └─ analyzeOnline() (2-5s)
   │     └─ Upload to aimlapi
   │     └─ Qwen 2.5 1.5B processing
   │     └─ NER result
   │
   └─ Results merged
      └─ onAnalysisComplete()
         └─ UI updated
```

---

## 🔧 Common Tasks

### Task 1: Add Voice Recording to Activity

**Steps:**
1. Copy code from `INTEGRATION_GUIDE.md`
2. Add VoiceRecordingHelper
3. Set up listener
4. Add mic button click handler
5. Test

**Time:** 15 minutes

---

### Task 2: Debug Recording Issue

**Steps:**
1. Check logs: `adb logcat HybridVoiceRecorder:V`
2. Check permissions in Manifest
3. Check device mic availability
4. Add breakpoints in `TESTING_DEBUGGING_GUIDE.md`
5. Use profiler if needed

**Time:** 30 minutes

---

### Task 3: Implement Offline Model

**Steps:**
1. Get Haaniye model from `assets/tts/haaniye/`
2. Implement `analyzeOffline()` method
3. Load model and process audio
4. Parse results
5. Test offline

**Time:** 2-4 hours

---

### Task 4: Implement Online Model

**Steps:**
1. Get aimlapi credentials
2. Implement `analyzeOnline()` method
3. Upload audio file
4. Parse response
5. Test online

**Time:** 2-4 hours

---

## 📊 نمودار وابستگی‌ها

```
Core Components:
├── HybridVoiceRecorder (Media Recording)
│   ├── MediaRecorder (Android SDK)
│   ├── Kotlin Coroutines
│   ├── File I/O
│   └── Handler (Amplitude monitoring)
│
├── VoiceRecordingService (Background)
│   ├── LifecycleService
│   ├── Notification
│   └── WakeLock
│
├── VoiceRecordingHelper (API)
│   └── VoiceRecordingHelper.RecordingListener
│
└── VoiceRecorderViewNew (UI)
    ├── Canvas (Drawing)
    ├── ValueAnimator (Animation)
    └── MotionEvent (Touch)

External:
├── Haaniye Model (Offline STT)
├── aimlapi (Online API)
└── Qwen 2.5 1.5B (Online LLM)
```

---

## ⚡ Quick References

### Import Statements

```kotlin
import android.media.MediaRecorder
import android.content.Intent
import androidx.lifecycle.LifecycleService
import kotlin.coroutines.*
import kotlinx.coroutines.*

import com.persianai.assistant.services.HybridVoiceRecorder
import com.persianai.assistant.services.VoiceRecordingService
import com.persianai.assistant.services.VoiceRecordingHelper
import com.persianai.assistant.views.VoiceRecorderViewNew
```

---

### Manifest Additions

```xml
<!-- Permissions (already added) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Service -->
<service
    android:name=".services.VoiceRecordingService"
    android:foregroundServiceType="microphone" />
```

---

### Permission Checks

```kotlin
if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED) {
    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
}
```

---

## 📞 بخش پشتیبانی

### کجا برای هر پرسش؟

| سوال | فایل |
|-----|------|
| چگونه معماری کار می‌کند؟ | VOICE_RECORDING_ARCHITECTURE.md |
| چگونه اتصال کنم؟ | INTEGRATION_GUIDE.md |
| چگونه تست کنم؟ | TESTING_DEBUGGING_GUIDE.md |
| خطای X دریافت کردم | TESTING_DEBUGGING_GUIDE.md - Troubleshooting |
| سریع شروع کن | README_HYBRID_VOICE_SYSTEM.md |
| Checklist دنبال کن | IMPLEMENTATION_CHECKLIST.md |

---

## ✅ نتیجه‌گیری

**با این راهنما:**

1. ✅ می‌فهمی معماری چگونه کار می‌کند
2. ✅ می‌تونی اتصال کنی در Activities
3. ✅ می‌تونی تست کنی درست
4. ✅ می‌تونی debug کنی اگر مشکل داشت
5. ✅ می‌تونی implement کنی آفلاین/آنلاین
6. ✅ می‌تونی بسازی production-ready solution

---

**🎉 Happy Coding!**

---

**نسخه:** 1.0  
**آپدیت:** 2024  
**توسط:** AI Assistant
