# ✅ چکلیست پیاده‌سازی (Implementation Checklist)

## 📌 معلومات پروژه

- **نام پروژه:** Persian AI Assistant Online
- **فیچر:** سیستم ضبط صدای ترکیبی (Hybrid Voice Recording System)
- **تاریخ شروع:** 2024
- **وضعیت:** در حال پیاده‌سازی

---

## 🎯 مرحله 1: مقدماتی (Prerequisites)

- [x] فایل‌های جدید ایجاد شده
  - [x] HybridVoiceRecorder.kt
  - [x] VoiceRecordingService.kt
  - [x] VoiceRecordingHelper.kt
  - [x] VoiceRecorderViewNew.kt

- [x] فایل‌های موجود آپدیت شده
  - [x] AndroidManifest.xml (سرویس و permissions)
  - [x] FullScreenAlarmActivity.kt (documentation)
  - [x] VoiceRecorderView.kt (fixed)
  - [x] VoiceNavigationAssistantActivity.kt (fixed)

- [x] داکومنتیشن ایجاد شده
  - [x] VOICE_RECORDING_ARCHITECTURE.md
  - [x] INTEGRATION_GUIDE.md
  - [x] TESTING_DEBUGGING_GUIDE.md
  - [x] SOLUTION_SUMMARY.md
  - [x] README_HYBRID_VOICE_SYSTEM.md

---

## 🔧 مرحله 2: اتصال (Integration)

### 2.1 MainActivity.kt

**کار:**
- [ ] Copy کنید: `INTEGRATION_GUIDE.md` - MainActivity.kt section
- [ ] اضافه کنید: Import statements
- [ ] اضافه کنید: `VoiceRecordingHelper voiceHelper;`
- [ ] اضافه کنید: `voiceHelper` initialization in `onCreate()`
- [ ] اضافه کنید: RecordingListener implementation
- [ ] اضافه کنید: Mic button click handler
- [ ] اضافه کنید: Permission check
- [ ] اضافه کنید: Cleanup in `onDestroy()`

**Expected time:** 15 minutes  
**File:** `app/src/main/java/.../activities/MainActivity.kt`

```kotlin
// TODO: Add these lines
private lateinit var voiceHelper: VoiceRecordingHelper

override fun onCreate(savedInstanceState: Bundle?) {
    // ... existing code ...
    
    voiceHelper = VoiceRecordingHelper(this)
    voiceHelper.setListener(/* listener */)
    
    findViewById<ImageButton>(R.id.micButton).setOnClickListener {
        // Check permission
        // Start recording
    }
}

override fun onDestroy() {
    super.onDestroy()
    voiceHelper.cancelRecording()
}
```

---

### 2.2 BaseChatActivity.kt

**کار:**
- [ ] Copy کنید: `INTEGRATION_GUIDE.md` - BaseChatActivity.kt section
- [ ] اضافه کنید: `VoiceRecordingHelper`
- [ ] اضافه کنید: `setupVoiceRecording()` method
- [ ] اضافه کنید: Abstract methods for overriding
- [ ] Override کنید: `onVoiceRecordingStarted()`, etc.

**Expected time:** 15 minutes  
**File:** `app/src/main/java/.../activities/BaseChatActivity.kt`

```kotlin
// TODO: Add setup method
protected fun setupVoiceRecording() {
    voiceHelper = VoiceRecordingHelper(this)
    voiceHelper.setListener(/* listener */)
}

// TODO: Add abstract methods
protected open fun onVoiceRecordingStarted() { }
protected open fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) { }
protected open fun onVoiceRecordingCancelled() { }
protected open fun onVoiceRecordingError(error: String) { }
```

---

### 2.3 AIChatActivity.kt (extends BaseChatActivity)

**کار:**
- [ ] Copy کنید: `INTEGRATION_GUIDE.md` - AIChatActivity.kt section
- [ ] Override کنید: Voice recording methods
- [ ] اضافه کنید: UI update logic
- [ ] اضافه کنید: Audio processing logic

**Expected time:** 10 minutes  
**File:** `app/src/main/java/.../activities/AIChatActivity.kt`

```kotlin
// TODO: Override methods
override fun onVoiceRecordingStarted() {
    super.onVoiceRecordingStarted()
    // Update UI
}

override fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) {
    super.onVoiceRecordingCompleted(audioFile, durationMs)
    // Process audio
}
```

---

### 2.4 VoiceNavigationAssistantActivity.kt

**کار:**
- [ ] Copy کنید: `INTEGRATION_GUIDE.md` - VoiceNavigationAssistantActivity.kt section
- [ ] اضافه کنید: `VoiceRecordingHelper`
- [ ] اضافه کنید: Navigation-specific handling

**Expected time:** 10 minutes  
**File:** `app/src/main/java/.../activities/VoiceNavigationAssistantActivity.kt`

```kotlin
// TODO: Similar to MainActivity
private lateinit var voiceHelper: VoiceRecordingHelper

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setupVoiceRecording()
}

private fun setupVoiceRecording() {
    voiceHelper = VoiceRecordingHelper(this)
    voiceHelper.setListener(/* navigation-specific listener */)
}
```

---

### 2.5 Layout XML Files

**کار:**
- [ ] یافتید: تمام layout files با voice recording
  - [ ] `activity_main.xml`
  - [ ] `activity_ai_chat.xml`
  - [ ] `activity_voice_navigation.xml`
  - [ ] دیگر layout files

- [ ] بروزرسانی: mic button references
- [ ] اضافه کنید: `android:contentDescription` if missing
- [ ] اضافه کنید: Proper icon references

**Expected time:** 20 minutes

```xml
<!-- Example: Update mic button -->
<ImageButton
    android:id="@+id/micButton"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@drawable/ic_mic_default"
    android:contentDescription="@string/record_voice"
    android:background="?attr/selectableItemBackgroundBorderless" />
```

---

## 🧪 مرحله 3: تست (Testing)

### 3.1 Compilation

- [ ] `./gradlew clean` - پاک‌سازی
- [ ] `./gradlew build` - کامپایل
- [ ] بررسی: No errors
- [ ] بررسی: No warnings (optional)

**Expected time:** 5 minutes

```bash
# Clean and build
./gradlew clean build

# Expected output
# BUILD SUCCESSFUL in XXXms
```

---

### 3.2 Unit Tests

- [ ] `./gradlew test` - اجرای unit tests
- [ ] بررسی: All tests pass
- [ ] بررسی: Coverage 80%+

**Expected time:** 5 minutes

```bash
./gradlew test

# Expected output:
# > Task :app:testDebugUnitTest
# > HybridVoiceRecorderTest > testRecorderInitialization PASSED
# > ... more tests ...
# BUILD SUCCESSFUL
```

---

### 3.3 Device Tests

- [ ] `./gradlew connectedAndroidTest` - اجرای device tests
- [ ] بررسی: All tests pass

**Expected time:** 10 minutes

```bash
./gradlew connectedAndroidTest

# Expected: All tests pass on device
```

---

### 3.4 Manual Testing

**Microphone Tests:**
- [ ] Click mic button → No crash
- [ ] Record 2 seconds → Completes successfully
- [ ] Record 10 seconds → No memory issues
- [ ] Cancel recording → No crash
- [ ] Multiple records → All work

**Full-Screen Alert Tests:**
- [ ] Reminder triggers → Full-screen shows
- [ ] Screen off → Alert still shows
- [ ] Sound plays → Audible
- [ ] Swipe gesture → Dismisses alert
- [ ] Background → All features work

**Audio Quality:**
- [ ] Recording is clear → No distortion
- [ ] Waveform displays → Animates smoothly
- [ ] Duration accurate → Matches actual time

**Expected time:** 30 minutes

---

## 🔧 مرحله 4: پیاده‌سازی (Implementation)

### 4.1 Offline Model (Haaniye)

**کار:**
- [ ] بررسی: Model file exists
  - `app/src/main/assets/tts/haaniye/`
- [ ] اضافه کنید: Model loader in `HybridVoiceRecorder.analyzeOffline()`
- [ ] اضافه کنید: STT processing logic
- [ ] اضافه کنید: Result parsing
- [ ] تست: Model works offline

**Expected time:** 2-4 hours  
**Priority:** HIGH

```kotlin
// TODO: In HybridVoiceRecorder.kt
suspend fun analyzeOffline(audioFile: File): OfflineAnalysisResult {
    return withContext(Dispatchers.Default) {
        try {
            // 1. Load Haaniye model from assets
            val model = loadHaaniyeModel()
            
            // 2. Process audio file
            val result = model.processAudio(audioFile)
            
            // 3. Return result
            return@withContext OfflineAnalysisResult(
                text = result.recognizedText,
                confidence = result.confidence,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("HybridVoiceRecorder", "Offline analysis failed", e)
            return@withContext OfflineAnalysisResult(error = e.message)
        }
    }
}
```

---

### 4.2 Online Model (Qwen + aimlapi)

**کار:**
- [ ] اضافه کنید: API client in `HybridVoiceRecorder.analyzeOnline()`
- [ ] اضافه کنید: Audio file upload logic
- [ ] اضافه کنید: API call to aimlapi/Qwen2.5
- [ ] اضافه کنید: Response parsing
- [ ] تست: API works with valid key

**Expected time:** 2-4 hours  
**Priority:** HIGH

```kotlin
// TODO: In HybridVoiceRecorder.kt
suspend fun analyzeOnline(audioFile: File): OnlineAnalysisResult {
    return withContext(Dispatchers.IO) {
        try {
            // 1. Create multipart request
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", audioFile.name,
                    RequestBody.create(MediaType.parse("audio/m4a"), audioFile))
                .addFormDataPart("model", "Qwen2.5-1.5B")
                .build()
            
            // 2. Send to aimlapi
            val request = Request.Builder()
                .url("https://api.aimlapi.com/v1/audio/transcribe")
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(body)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            // 3. Parse response
            val result = parseAimlApiResponse(response.body?.string())
            
            return@withContext result
        } catch (e: Exception) {
            Log.e("HybridVoiceRecorder", "Online analysis failed", e)
            return@withContext OnlineAnalysisResult(error = e.message)
        }
    }
}
```

---

### 4.3 Hybrid Analysis

**کار:**
- [ ] اضافه کنید: `analyzeHybrid()` implementation
- [ ] اضافه کنید: Parallel offline + online calls
- [ ] اضافه کنید: Result merging logic
- [ ] اضافه کنید: Fallback mechanism
- [ ] تست: Both work together

**Expected time:** 1-2 hours  
**Priority:** MEDIUM

```kotlin
// TODO: In HybridVoiceRecorder.kt
suspend fun analyzeHybrid(audioFile: File): HybridAnalysisResult {
    return withContext(Dispatchers.Main) {
        try {
            // Run offline and online in parallel
            val offlineDeferred = async(Dispatchers.Default) { analyzeOffline(audioFile) }
            val onlineDeferred = async(Dispatchers.IO) { analyzeOnline(audioFile) }
            
            val offlineResult = offlineDeferred.await()
            val onlineResult = onlineDeferred.await()
            
            // Merge results
            return@withContext HybridAnalysisResult(
                offlineResult = offlineResult,
                onlineResult = onlineResult,
                combinedText = mergeResults(offlineResult, onlineResult),
                confidence = calculateConfidence(offlineResult, onlineResult)
            )
        } catch (e: Exception) {
            Log.e("HybridVoiceRecorder", "Hybrid analysis failed", e)
            return@withContext HybridAnalysisResult(error = e.message)
        }
    }
}
```

---

## 📊 مرحله 5: تکمیل (Finalization)

- [ ] تمام files compile شدند بدون error
- [ ] تمام tests pass شدند
- [ ] Device tests اجرا شدند و موفق
- [ ] Manual testing کامل شد
- [ ] Offline model کار می‌کند
- [ ] Online model کار می‌کند
- [ ] Hybrid analysis کار می‌کند
- [ ] Documentation بروزرسانی شد
- [ ] Code review کامل شد
- [ ] Team مطلع شد

---

## 📋 Summary Checklist

```
PHASE 1: Setup ✅
[x] Infrastructure files created
[x] Manifest updated
[x] Documentation prepared

PHASE 2: Integration 🔄 (IN PROGRESS)
[ ] MainActivity.kt updated
[ ] BaseChatActivity.kt updated
[ ] AIChatActivity.kt updated
[ ] VoiceNavigationAssistantActivity.kt updated
[ ] Layout XML files updated
[ ] Compilation successful

PHASE 3: Testing (NEXT)
[ ] Unit tests passing
[ ] Device tests passing
[ ] Manual testing complete
[ ] No crashes reported

PHASE 4: Implementation (AFTER TESTING)
[ ] Haaniye offline model working
[ ] Qwen/aimlapi online model working
[ ] Hybrid analysis working
[ ] Error handling complete

PHASE 5: Finalization (LAST)
[ ] Code review passed
[ ] Documentation complete
[ ] Team training done
[ ] Production ready
```

---

## ⏱️ Time Estimate

| Phase | Task | Time |
|-------|------|------|
| 1 | Setup | ✅ Done |
| 2 | MainActivity Integration | 15 min |
| 2 | BaseChatActivity Integration | 15 min |
| 2 | AIChatActivity Integration | 10 min |
| 2 | Other Activities Integration | 10 min |
| 2 | Layout XML Updates | 20 min |
| 2 | Compilation & Fix | 20 min |
| 3 | Unit Tests | 5 min |
| 3 | Device Tests | 10 min |
| 3 | Manual Testing | 30 min |
| 4 | Haaniye Implementation | 2-4 hrs |
| 4 | Qwen/aimlapi Implementation | 2-4 hrs |
| 4 | Hybrid Analysis | 1-2 hrs |
| 5 | Code Review & Fixes | 1-2 hrs |
| 5 | Documentation & Training | 1 hr |
| **TOTAL** | | **~16-24 hours** |

---

## 🎯 Resources

### Documentation
- `VOICE_RECORDING_ARCHITECTURE.md` - معماری کامل
- `INTEGRATION_GUIDE.md` - راهنمای اتصال
- `TESTING_DEBUGGING_GUIDE.md` - راهنمای تست
- `SOLUTION_SUMMARY.md` - خلاصه کامل

### Code Files
- `HybridVoiceRecorder.kt` - ابر سرویس
- `VoiceRecordingService.kt` - سرویس پس‌زمینه
- `VoiceRecordingHelper.kt` - کمک‌کننده API
- `VoiceRecorderViewNew.kt` - UI component

### External Resources
- [Android MediaRecorder Docs](https://developer.android.com/reference/android/media/MediaRecorder)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [aimlapi Documentation](https://aimlapi.com/docs)

---

## 📞 Contact & Support

**For questions about:**
- Architecture → See `VOICE_RECORDING_ARCHITECTURE.md`
- Integration → See `INTEGRATION_GUIDE.md`
- Testing → See `TESTING_DEBUGGING_GUIDE.md`
- Implementation → See code comments and documentation

---

## ✨ Success Criteria

✅ **When this checklist is complete:**

1. Microphone doesn't crash
2. Full-screen alerts work in background
3. Offline model works (Haaniye)
4. Online model works (Qwen/aimlapi)
5. Hybrid analysis gives best results
6. No memory leaks
7. All tests pass
8. Code reviewed
9. Team trained
10. Production ready

---

**🎉 Good Luck! You got this!**

---

**آخرین آپدیت:** 2024  
**نسخه:** 1.0  
**وضعیت:** آماده برای شروع
