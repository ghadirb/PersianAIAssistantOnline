# 🚀 راهنمای اتصال سیستم ضبط صدای ترکیبی (Integration Guide)

## 📝 خلاصه

این راهنما نحوه ادغام سیستم ضبط صدای ترکیبی جدید را در Activities موجود نشان می‌دهد.

---

## ✅ پیش‌نیازها

- ✅ HybridVoiceRecorder.kt ایجاد شده
- ✅ VoiceRecorderViewNew.kt ایجاد شده
- ✅ VoiceRecordingService.kt ایجاد شده
- ✅ VoiceRecordingHelper.kt ایجاد شده
- ✅ AndroidManifest.xml بروزرسانی شده
- ✅ VOICE_RECORDING_ARCHITECTURE.md ایجاد شده

---

## 🔧 مرحله 1: بررسی Activities موجود

### لیست Activities با Voice Recording:

1. **MainActivity.kt** - صفحه اصلی چت
2. **BaseChatActivity.kt** - کلاس پایه برای Chat Activities
3. **VoiceNavigationAssistantActivity.kt** - دستیار صوتی
4. **AIChatActivity.kt** - Chat تعاملی

---

## 📋 مرحله 2: Activity های نیاز به تغییر

### MainActivity.kt

**مراحل:**
1. Import های لازم اضافه کنید
2. VoiceRecordingHelper تعریف کنید
3. onClick handler را بروزرسانی کنید
4. Layout XML را آپدیت کنید

**کد نمونه:**

```kotlin
package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.persianai.assistant.services.HybridVoiceRecorder
import com.persianai.assistant.services.VoiceRecordingHelper

class MainActivity : AppCompatActivity() {
    
    private lateinit var voiceHelper: VoiceRecordingHelper
    private var isRecording = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Voice Recording Helper
        voiceHelper = VoiceRecordingHelper(this)
        
        // Set up listener for recording callbacks
        voiceHelper.setListener(object : VoiceRecordingHelper.RecordingListener {
            override fun onRecordingStarted() {
                Log.d("MainActivity", "Recording started")
                isRecording = true
                updateMicButtonUI(true)
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                Log.d("MainActivity", "Recording completed: ${audioFile.absolutePath}, Duration: ${durationMs}ms")
                isRecording = false
                updateMicButtonUI(false)
                
                // Process the audio file (send to AI, etc.)
                processAudioFile(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                Log.d("MainActivity", "Recording cancelled")
                isRecording = false
                updateMicButtonUI(false)
            }
            
            override fun onRecordingError(error: String) {
                Log.e("MainActivity", "Recording error: $error")
                isRecording = false
                updateMicButtonUI(false)
                showError(error)
            }
        })
        
        // Set up Mic Button Click
        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            onMicButtonClick()
        }
    }
    
    private fun onMicButtonClick() {
        // Check microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permission
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_CODE
            )
            return
        }
        
        // Start recording
        if (!isRecording) {
            voiceHelper.startRecording()
        }
    }
    
    private fun updateMicButtonUI(recording: Boolean) {
        val micButton = findViewById<ImageButton>(R.id.micButton)
        micButton.setImageResource(
            if (recording) R.drawable.ic_mic_recording else R.drawable.ic_mic_default
        )
    }
    
    private fun processAudioFile(audioFile: File, durationMs: Long) {
        // TODO: Send audio to AI for analysis
        Log.d("MainActivity", "Processing audio file: ${audioFile.absolutePath}")
    }
    
    private fun showError(error: String) {
        Toast.makeText(this, "Recording Error: $error", Toast.LENGTH_SHORT).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            MICROPHONE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onMicButtonClick()
                } else {
                    Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup
        voiceHelper.cancelRecording()
    }
    
    companion object {
        private const val MICROPHONE_PERMISSION_CODE = 101
    }
}
```

---

### BaseChatActivity.kt

**مراحل:**
1. HybridVoiceRecorder تعریف کنید
2. Voice recording lifecycle setup
3. onTouchEvent handler بروزرسانی کنید

**کد نمونه:**

```kotlin
package com.persianai.assistant.activities

import com.persianai.assistant.services.VoiceRecordingHelper

abstract class BaseChatActivity : AppCompatActivity() {
    
    protected lateinit var voiceHelper: VoiceRecordingHelper
    protected var isRecording = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Voice Helper
        setupVoiceRecording()
    }
    
    private fun setupVoiceRecording() {
        voiceHelper = VoiceRecordingHelper(this)
        
        voiceHelper.setListener(object : VoiceRecordingHelper.RecordingListener {
            override fun onRecordingStarted() {
                isRecording = true
                onVoiceRecordingStarted()
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                isRecording = false
                onVoiceRecordingCompleted(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                isRecording = false
                onVoiceRecordingCancelled()
            }
            
            override fun onRecordingError(error: String) {
                isRecording = false
                onVoiceRecordingError(error)
            }
        })
    }
    
    // Abstract methods for subclasses to override
    protected open fun onVoiceRecordingStarted() {
        Log.d("BaseChatActivity", "Voice recording started")
    }
    
    protected open fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) {
        Log.d("BaseChatActivity", "Voice recording completed")
    }
    
    protected open fun onVoiceRecordingCancelled() {
        Log.d("BaseChatActivity", "Voice recording cancelled")
    }
    
    protected open fun onVoiceRecordingError(error: String) {
        Log.e("BaseChatActivity", "Voice recording error: $error")
    }
    
    // Method to start recording from subclasses
    protected fun startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            voiceHelper.startRecording()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MICROPHONE_PERMISSION_CODE
            )
        }
    }
    
    protected fun stopVoiceRecording() {
        voiceHelper.stopRecording()
    }
    
    protected fun cancelVoiceRecording() {
        voiceHelper.cancelRecording()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.cancelRecording()
    }
    
    companion object {
        protected const val MICROPHONE_PERMISSION_CODE = 101
    }
}
```

---

### AIChatActivity.kt (extends BaseChatActivity)

**مراحل:**
1. parent class methods override کنید
2. UI updates اضافه کنید

**کد نمونه:**

```kotlin
package com.persianai.assistant.activities

class AIChatActivity : BaseChatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)
        
        // Set up mic button
        findViewById<ImageButton>(R.id.micButton).setOnClickListener {
            if (!isRecording) {
                startVoiceRecording()
            }
        }
    }
    
    override fun onVoiceRecordingStarted() {
        super.onVoiceRecordingStarted()
        
        // Update UI
        findViewById<ImageButton>(R.id.micButton).setImageResource(R.drawable.ic_mic_recording)
        Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
    }
    
    override fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) {
        super.onVoiceRecordingCompleted(audioFile, durationMs)
        
        // Update UI
        findViewById<ImageButton>(R.id.micButton).setImageResource(R.drawable.ic_mic_default)
        
        // Process audio
        sendAudioToAI(audioFile, durationMs)
    }
    
    override fun onVoiceRecordingCancelled() {
        super.onVoiceRecordingCancelled()
        
        // Update UI
        findViewById<ImageButton>(R.id.micButton).setImageResource(R.drawable.ic_mic_default)
        Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onVoiceRecordingError(error: String) {
        super.onVoiceRecordingError(error)
        
        // Update UI
        findViewById<ImageButton>(R.id.micButton).setImageResource(R.drawable.ic_mic_default)
        Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
    }
    
    private fun sendAudioToAI(audioFile: File, durationMs: Long) {
        // Send to AI for analysis
        Log.d("AIChatActivity", "Sending audio to AI: ${audioFile.absolutePath}")
        
        // TODO: Implement AI integration
    }
}
```

---

### VoiceNavigationAssistantActivity.kt

**مراحل:**
1. VoiceRecordingHelper تعریف کنید
2. Navigation specific handling

**کد نمونه:**

```kotlin
package com.persianai.assistant.activities

class VoiceNavigationAssistantActivity : AppCompatActivity() {
    
    private lateinit var voiceHelper: VoiceRecordingHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_navigation)
        
        setupVoiceRecording()
    }
    
    private fun setupVoiceRecording() {
        voiceHelper = VoiceRecordingHelper(this)
        
        voiceHelper.setListener(object : VoiceRecordingHelper.RecordingListener {
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                // Handle navigation commands
                processNavigationVoiceCommand(audioFile)
            }
            
            override fun onRecordingError(error: String) {
                showNavigationError(error)
            }
        })
        
        // Listen for voice button
        findViewById<ImageButton>(R.id.navigationMicButton).setOnClickListener {
            voiceHelper.startRecording()
        }
    }
    
    private fun processNavigationVoiceCommand(audioFile: File) {
        // Process navigation-specific commands
        Log.d("VoiceNavigation", "Processing voice command: ${audioFile.absolutePath}")
    }
    
    private fun showNavigationError(error: String) {
        Toast.makeText(this, "Navigation Error: $error", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.cancelRecording()
    }
}
```

---

## 🎨 مرحله 3: بروزرسانی Layout XML فایل‌ها

### activity_main.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">
    
    <!-- Messages RecyclerView -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/messagesRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    
    <!-- Input Container -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp">
        
        <EditText
            android:id="@+id/messageInput"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:hint="Enter message..."
            android:paddingStart="16dp"
            android:paddingEnd="56dp"
            android:background="@drawable/edit_text_background"
            android:inputType="text" />
        
        <!-- Mic Button -->
        <ImageButton
            android:id="@+id/micButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_gravity="end|center_vertical"
            android:src="@drawable/ic_mic_default"
            android:contentDescription="Record Voice"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:scaleType="centerInside" />
    </FrameLayout>
</LinearLayout>
```

### activity_ai_chat.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    
    <!-- Chat Messages -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/chatRecyclerView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    
    <!-- Input Area -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp">
        
        <EditText
            android:id="@+id/chatInput"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:hint="Type message..."
            android:paddingStart="16dp"
            android:background="@drawable/edit_text_background" />
        
        <!-- Mic Button -->
        <ImageButton
            android:id="@+id/micButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:layout_marginStart="8dp"
            android:src="@drawable/ic_mic_default"
            android:contentDescription="Record Voice"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:scaleType="centerInside" />
    </LinearLayout>
</LinearLayout>
```

---

## 🧪 مرحله 4: تست کردن

### Test Case 1: شروع و توقف ضبط

```kotlin
@Test
fun testVoiceRecordingStartStop() {
    val helper = VoiceRecordingHelper(context)
    val testListener = TestRecordingListener()
    
    helper.setListener(testListener)
    helper.startRecording()
    
    // Wait for recording
    Thread.sleep(2000)
    
    helper.stopRecording()
    
    // Assertions
    assertTrue(testListener.startedCalled)
    assertTrue(testListener.completedCalled)
}
```

### Test Case 2: کرش نشودن میکروفن

```kotlin
@Test
fun testMicrophoneClickDoesNotCrash() {
    try {
        val helper = VoiceRecordingHelper(context)
        helper.startRecording()
        Thread.sleep(1000)
        helper.stopRecording()
        Thread.sleep(1000)
        
        // Should not crash
        assertTrue(true)
    } catch (e: Exception) {
        fail("Microphone recording crashed: ${e.message}")
    }
}
```

### Test Case 3: هشدار تمام‌صفحه

```kotlin
@Test
fun testFullScreenAlarmInBackground() {
    // Create reminder
    val reminder = Reminder(
        title = "Test",
        time = "14:00",
        fullScreen = true
    )
    
    // Store reminder
    ReminderService.addReminder(reminder)
    
    // Wait for trigger
    Thread.sleep(5000)
    
    // Verify full-screen was shown
    assertTrue(FullScreenAlarmActivity.wasShown)
}
```

---

## 📊 چکلیست اتصال

- [ ] HybridVoiceRecorder.kt در `services/` قرار گرفت
- [ ] VoiceRecorderViewNew.kt در `views/` قرار گرفت
- [ ] VoiceRecordingService.kt در `services/` قرار گرفت
- [ ] VoiceRecordingHelper.kt در `services/` قرار گرفت
- [ ] AndroidManifest.xml آپدیت شد
- [ ] MainActivity.kt بروزرسانی شد
- [ ] BaseChatActivity.kt بروزرسانی شد
- [ ] AIChatActivity.kt بروزرسانی شد
- [ ] VoiceNavigationAssistantActivity.kt بروزرسانی شد
- [ ] Layout XML فایل‌ها آپدیت شدند
- [ ] تست‌ها اجرا شدند
- [ ] داکومنتیشن کامل شد

---

## 🐛 Troubleshooting

### مشکل: "java.lang.RuntimeException: MediaRecorder error (-1, -4)"

**حل:**
```kotlin
// Make sure microphone permission is granted
if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
    != PackageManager.PERMISSION_GRANTED) {
    // Request permission first
}
```

### مشکل: "CoroutineScope job is not active"

**حل:**
```kotlin
// Make sure cleanup() is called in try-finally
override fun onDestroy() {
    super.onDestroy()
    voiceHelper.cancelRecording()  // Properly cancels recording
}
```

### مشکل: "Full-screen alarm not showing in background"

**حل:**
```kotlin
// Ensure these permissions are in Manifest:
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />

// And proper notification setup in ReminderService
```

---

**نسخه:** 1.0  
**وضعیت:** آماده برای اجرا
