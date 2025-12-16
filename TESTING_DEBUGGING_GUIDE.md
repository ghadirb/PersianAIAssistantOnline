# 🧪 راهنمای تست و رفع عیب (Testing & Debugging Guide)

## 📋 فهرست

1. [Unit Tests](#unit-tests)
2. [Integration Tests](#integration-tests)
3. [Device Tests](#device-tests)
4. [Debugging Guide](#debugging-guide)
5. [Performance Analysis](#performance-analysis)
6. [Error Scenarios](#error-scenarios)

---

## 🧪 Unit Tests

### 1. HybridVoiceRecorder Tests

```kotlin
// File: app/src/test/java/com/persianai/assistant/services/HybridVoiceRecorderTest.kt

package com.persianai.assistant.services

import android.content.Context
import android.media.MediaRecorder
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HybridVoiceRecorderTest {
    
    private lateinit var context: Context
    private lateinit var recorder: HybridVoiceRecorder
    private lateinit var testListener: TestRecorderListener
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        recorder = HybridVoiceRecorder(context)
        testListener = TestRecorderListener()
        recorder.setListener(testListener)
    }
    
    @Test
    fun testRecorderInitialization() {
        assertNotNull(recorder)
        assertNull(testListener.lastError)
    }
    
    @Test
    fun testStartRecording() {
        recorder.startRecording()
        
        assertTrue(testListener.startedCalled)
        assertFalse(testListener.completedCalled)
    }
    
    @Test
    fun testStopRecording() {
        recorder.startRecording()
        Thread.sleep(100)
        recorder.stopRecording()
        
        assertTrue(testListener.startedCalled)
        assertTrue(testListener.completedCalled)
        assertNotNull(testListener.lastAudioFile)
    }
    
    @Test
    fun testCancelRecording() {
        recorder.startRecording()
        Thread.sleep(100)
        recorder.cancelRecording()
        
        assertTrue(testListener.cancelledCalled)
        assertTrue(testListener.lastAudioFile?.exists() == false)
    }
    
    @Test
    fun testAudioFileCreation() {
        recorder.startRecording()
        Thread.sleep(100)
        recorder.stopRecording()
        
        val audioFile = testListener.lastAudioFile
        assertNotNull(audioFile)
        assertTrue(audioFile?.exists() ?: false)
        assertTrue(audioFile?.length() ?: 0 > 0)
    }
    
    @Test
    fun testRecordingDuration() {
        recorder.startRecording()
        Thread.sleep(2000) // Record for 2 seconds
        recorder.stopRecording()
        
        val duration = testListener.lastDuration
        // Duration should be between 1500-2500ms
        assertTrue(duration in 1500..2500)
    }
    
    @Test
    fun testAmplitudeMonitoring() {
        var amplitudeCallbacks = 0
        
        recorder.setListener(object : HybridVoiceRecorder.RecorderListener {
            override fun onRecordingStarted() {}
            override fun onRecordingCompleted(file: File, durationMs: Long) {}
            override fun onRecordingCancelled() {}
            override fun onRecordingError(error: String) {}
            override fun onAmplitudeChanged(amplitude: Int) {
                amplitudeCallbacks++
            }
        })
        
        recorder.startRecording()
        Thread.sleep(1000) // Should get ~10 amplitude updates
        recorder.stopRecording()
        
        assertTrue(amplitudeCallbacks > 5)
    }
    
    @Test
    fun testExceptionHandling() {
        // Mock MediaRecorder to throw exception
        try {
            recorder.startRecording()
            recorder.startRecording() // Try to start again
            recorder.stopRecording()
            
            // Should have error callback
            assertTrue(testListener.errorCalled || !testListener.errorCalled)
            // No crash!
        } catch (e: Exception) {
            fail("Recording should not crash on double start: ${e.message}")
        }
    }
    
    @Test
    fun testResourceCleanup() {
        recorder.startRecording()
        recorder.stopRecording()
        recorder.cleanup()
        
        // Try to use recorder after cleanup - should not crash
        try {
            recorder.startRecording() // May fail, but shouldn't crash
        } catch (e: Exception) {
            // Expected
        }
    }
    
    @Test
    fun testCacheFileLocation() {
        recorder.startRecording()
        Thread.sleep(100)
        recorder.stopRecording()
        
        val audioFile = testListener.lastAudioFile
        assertTrue(audioFile?.absolutePath?.contains("cache") ?: false)
    }
    
    @Test
    fun testAudioFileEncoding() {
        recorder.startRecording()
        Thread.sleep(100)
        recorder.stopRecording()
        
        val audioFile = testListener.lastAudioFile
        assertTrue(audioFile?.name?.endsWith(".m4a") ?: false)
    }
}

// Test listener class
private class TestRecorderListener : HybridVoiceRecorder.RecorderListener {
    var startedCalled = false
    var completedCalled = false
    var cancelledCalled = false
    var errorCalled = false
    var lastAudioFile: File? = null
    var lastDuration: Long = 0
    var lastError: String? = null
    
    override fun onRecordingStarted() {
        startedCalled = true
    }
    
    override fun onRecordingCompleted(file: File, durationMs: Long) {
        completedCalled = true
        lastAudioFile = file
        lastDuration = durationMs
    }
    
    override fun onRecordingCancelled() {
        cancelledCalled = true
    }
    
    override fun onRecordingError(error: String) {
        errorCalled = true
        lastError = error
    }
    
    override fun onAmplitudeChanged(amplitude: Int) {
        // Track amplitude changes
    }
}
```

---

### 2. VoiceRecordingHelper Tests

```kotlin
// File: app/src/test/java/com/persianai/assistant/services/VoiceRecordingHelperTest.kt

package com.persianai.assistant.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VoiceRecordingHelperTest {
    
    private lateinit var context: Context
    private lateinit var helper: VoiceRecordingHelper
    private lateinit var testListener: TestRecordingListener
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        helper = VoiceRecordingHelper(context)
        testListener = TestRecordingListener()
        helper.setListener(testListener)
    }
    
    @Test
    fun testHelperInitialization() {
        assertNotNull(helper)
    }
    
    @Test
    fun testStartRecording() {
        helper.startRecording()
        
        assertTrue(testListener.startedCalled)
    }
    
    @Test
    fun testStopRecording() {
        helper.startRecording()
        Thread.sleep(100)
        helper.stopRecording()
        
        assertTrue(testListener.completedCalled)
    }
    
    @Test
    fun testCancelRecording() {
        helper.startRecording()
        helper.cancelRecording()
        
        assertTrue(testListener.cancelledCalled)
    }
    
    @Test
    fun testListenerCallback() {
        helper.setListener(testListener)
        helper.startRecording()
        Thread.sleep(100)
        helper.stopRecording()
        
        assertTrue(testListener.startedCalled)
        assertTrue(testListener.completedCalled)
    }
}

private class TestRecordingListener : VoiceRecordingHelper.RecordingListener {
    var startedCalled = false
    var completedCalled = false
    var cancelledCalled = false
    var errorCalled = false
    var lastFile: File? = null
    var lastDuration: Long = 0
    var lastError: String? = null
    
    override fun onRecordingStarted() {
        startedCalled = true
    }
    
    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
        completedCalled = true
        lastFile = audioFile
        lastDuration = durationMs
    }
    
    override fun onRecordingCancelled() {
        cancelledCalled = true
    }
    
    override fun onRecordingError(error: String) {
        errorCalled = true
        lastError = error
    }
}
```

---

## 🔧 Integration Tests

### 1. VoiceRecordingService Tests

```kotlin
// File: app/src/androidTest/java/com/persianai/assistant/services/VoiceRecordingServiceTest.kt

package com.persianai.assistant.services

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ServiceLifecycleController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceRecordingServiceTest {
    
    private lateinit var context: Context
    private lateinit var service: VoiceRecordingService
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testServiceCreation() {
        val intent = Intent(context, VoiceRecordingService::class.java)
        context.startService(intent)
        
        Thread.sleep(500)
        
        // Service should be created
        assertTrue(VoiceRecordingService.isRunning())
    }
    
    @Test
    fun testStartRecordingIntent() {
        val intent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "START_RECORDING"
        }
        
        context.startService(intent)
        Thread.sleep(500)
        
        assertTrue(VoiceRecordingService.isRecording())
    }
    
    @Test
    fun testStopRecordingIntent() {
        val startIntent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "START_RECORDING"
        }
        context.startService(startIntent)
        Thread.sleep(500)
        
        val stopIntent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "STOP_RECORDING"
        }
        context.startService(stopIntent)
        Thread.sleep(500)
        
        assertFalse(VoiceRecordingService.isRecording())
    }
    
    @Test
    fun testRecordingDuration() {
        val startIntent = Intent(context, VoiceRecordingService::class.java).apply {
            action = "START_RECORDING"
        }
        context.startService(startIntent)
        
        Thread.sleep(2000) // Record for 2 seconds
        
        val duration = VoiceRecordingService.getRecordingDuration()
        assertTrue(duration in 1500..2500)
    }
}
```

---

## 📱 Device Tests

### 1. Manual Testing Checklist

```
🎙️ MICROPHONE TESTS
□ Click mic button - does NOT crash
□ Record for 1 second - completes successfully
□ Record for 5 seconds - no memory leaks
□ Cancel recording mid-way - no crash
□ Multiple records in sequence - all work
□ Record with low battery - no crash
□ Record with WiFi off - uses offline processing
□ Record with WiFi on - hybrid processing works

🔊 AUDIO QUALITY
□ Recorded audio is audible
□ No distortion in audio
□ Waveform displays correctly
□ Amplitude monitoring works
□ Duration display is accurate

🚨 FULL-SCREEN ALERTS
□ Alert shows full-screen at 100% brightness
□ Sound plays on alert
□ Vibration works
□ Swipe gesture dismisses alert
□ Button gestures work (snooze, done)
□ Alert shows in background (screen off)
□ WakeLock holds device awake

🔄 BACKGROUND BEHAVIOR
□ Recording works with screen off
□ Recording works when app is backgrounded
□ Foreground service notification shows
□ Recording stops when requested from background
□ No system crashes during background recording
□ Memory doesn't leak after recording
```

### 2. Test Scenarios

```kotlin
// Scenario 1: Rapid Fire Recording
fun testRapidFireRecording() {
    repeat(5) {
        helper.startRecording()
        Thread.sleep(500)
        helper.stopRecording()
        Thread.sleep(200)
    }
    // Should not crash after 5 rapid recordings
}

// Scenario 2: Extended Recording
fun testExtendedRecording() {
    helper.startRecording()
    Thread.sleep(60000) // Record for 1 minute
    helper.stopRecording()
    
    val file = listener.lastAudioFile
    assertTrue(file?.length() ?: 0 > 1_000_000) // > 1MB
}

// Scenario 3: Memory Pressure
fun testMemoryPressure() {
    // Fill memory with data
    val tempList = mutableListOf<ByteArray>()
    repeat(100) {
        tempList.add(ByteArray(1_000_000))
    }
    
    // Try recording in memory-pressure situation
    helper.startRecording()
    Thread.sleep(1000)
    helper.stopRecording()
    
    // Should still work or fail gracefully
    assertTrue(true)
}
```

---

## 🔍 Debugging Guide

### 1. Log Filtering

```bash
# Filter HybridVoiceRecorder logs
adb logcat HybridVoiceRecorder:V *:S

# Filter VoiceRecordingService logs
adb logcat VoiceRecordingService:V *:S

# Filter Full-Screen Alarm logs
adb logcat FullScreenAlarm:V *:S

# All voice-related logs
adb logcat | grep -E "HybridVoiceRecorder|VoiceRecordingService|FullScreenAlarm"
```

### 2. Debug Points to Add

```kotlin
// In HybridVoiceRecorder.kt
override fun startRecording() {
    Log.d("HybridVoiceRecorder", "=== START RECORDING ===")
    Log.d("HybridVoiceRecorder", "Audio file: ${audioFile?.absolutePath}")
    Log.d("HybridVoiceRecorder", "Cache dir: ${context.cacheDir.absolutePath}")
    
    try {
        mediaRecorder?.apply {
            Log.d("HybridVoiceRecorder", "Initializing MediaRecorder...")
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioChannels(1)
            setAudioEncodingBitRate(128000)
            
            Log.d("HybridVoiceRecorder", "Preparing MediaRecorder...")
            prepare()
            
            Log.d("HybridVoiceRecorder", "Starting MediaRecorder...")
            start()
            
            Log.d("HybridVoiceRecorder", "✓ Recording started successfully")
        }
    } catch (e: Exception) {
        Log.e("HybridVoiceRecorder", "✗ Recording start failed", e)
        listener?.onRecordingError("Start failed: ${e.message}")
    }
}
```

### 3. Breakpoints

```kotlin
// Set breakpoints at:
1. HybridVoiceRecorder.startRecording() - line 50
2. HybridVoiceRecorder.stopRecording() - line 80
3. HybridVoiceRecorder.cleanup() - line 120
4. VoiceRecordingHelper.startRecording() - line 25
5. VoiceRecordingService.onStartCommand() - line 40
```

### 4. Profiling

```
Android Studio > View > Tool Windows > Profiler

1. Memory Profiler
   - Watch heap size during recording
   - Check for memory leaks after stopping
   - Verify GC cleanup

2. CPU Profiler
   - Check CPU usage during recording
   - Verify amplitude monitoring efficiency

3. Energy Profiler
   - Check battery impact
   - Verify WakeLock usage

4. Network Profiler
   - Check API calls to aimlapi
   - Verify data sent/received
```

---

## 📊 Performance Analysis

### Metrics to Monitor

```
MEMORY USAGE:
- Idle: ~50 MB
- Recording: +5-10 MB
- After stop: Should return to idle

CPU USAGE:
- Recording: ~15-20%
- Amplitude: <1%
- API calls: 10-30%

LATENCY:
- Start recording: <200ms
- Stop recording: <100ms
- Offline analysis: 300-500ms
- Online analysis: 2-5s

BATTERY:
- Recording (per minute): ~2-3%
- WakeLock (per minute): ~5-10%
```

### Performance Testing Code

```kotlin
@Test
fun testPerformance() {
    val startMemory = Runtime.getRuntime().totalMemory()
    
    helper.startRecording()
    Thread.sleep(5000)
    helper.stopRecording()
    
    val endMemory = Runtime.getRuntime().totalMemory()
    val memoryUsed = endMemory - startMemory
    
    // Memory usage should be reasonable
    assertTrue(memoryUsed < 20_000_000) // < 20MB
}
```

---

## 🚨 Error Scenarios

### 1. Common Errors and Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| `java.lang.RuntimeException: MediaRecorder error (-1, -4)` | Mic not available | Check permissions, restart app |
| `CoroutineScope job is not active` | Coroutine cancelled | Add null checks, handle in try-catch |
| `java.io.IOException: ENOMEM` | No disk space | Clean cache, check free space |
| `java.lang.SecurityException: Permission denied` | No RECORD_AUDIO | Request permission at runtime |
| `java.lang.IllegalStateException: prepareAudioRecording() called in state 4` | Wrong lifecycle | Ensure stop before start |

### 2. Crash Recovery

```kotlin
// In Application class
class AIAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("CRASH", "Uncaught exception in ${thread.name}", exception)
            
            // Clean up recording resources
            try {
                VoiceRecordingService.stopRecording()
            } catch (e: Exception) {
                Log.e("CRASH", "Failed to stop recording during crash recovery", e)
            }
            
            // Log crash for debugging
            logCrashToFile(exception)
        }
    }
    
    private fun logCrashToFile(exception: Exception) {
        // TODO: Send to logging service
    }
}
```

---

## 📈 Test Coverage Goals

```
Unit Tests:      80%+ coverage
Integration:     70%+ coverage
UI Tests:        50%+ coverage
Overall:         75%+ coverage

Critical Paths:
✓ Recording start/stop
✓ Error handling
✓ Resource cleanup
✓ Background operation
```

---

**نسخه:** 1.0  
**آپدیت:** 2024
