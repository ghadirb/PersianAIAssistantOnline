package com.persianai.assistant.activities

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.persianai.assistant.adapters.ChatAdapter
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.AIProvider
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.ui.VoiceRecorderView
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.TTSHelper
import com.persianai.assistant.utils.PreferencesManager.ProviderPreference
import com.persianai.assistant.services.VoiceRecordingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

abstract class BaseChatActivity : AppCompatActivity() {

    protected lateinit var binding: ViewBinding
    protected lateinit var chatAdapter: ChatAdapter
    protected lateinit var prefsManager: PreferencesManager
    protected lateinit var ttsHelper: TTSHelper
    protected var aiClient: AIClient? = null
    protected var currentModel: AIModel = AIModel.TINY_LLAMA_OFFLINE
    protected val messages = mutableListOf<ChatMessage>()
    private lateinit var speechRecognizer: SpeechRecognizer
    private var voiceRecorderView: VoiceRecorderView? = null
    protected lateinit var voiceHelper: VoiceRecordingHelper
    private val httpClient = OkHttpClient()
    private val hfApiKey: String by lazy {
        getSharedPreferences("api_keys", MODE_PRIVATE)
            .getString("hf_api_key", null)
            ?.takeIf { it.isNotBlank() }
            ?: DefaultApiKeys.getHuggingFaceKey()
            ?: ""
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    private fun chooseBestModel(apiKeys: List<APIKey>, pref: ProviderPreference): AIModel {
        // اولویت آنلاین: AIML → OpenRouter (Qwen سبک) → OpenAI → در نهایت آفلاین
        val activeProviders = apiKeys.filter { it.isActive }.map { it.provider }.toSet()
        return when {
            activeProviders.contains(com.persianai.assistant.models.AIProvider.AIML) -> AIModel.AIML_GPT_35
            activeProviders.contains(com.persianai.assistant.models.AIProvider.OPENROUTER) -> AIModel.QWEN_2_5_1B5
            activeProviders.contains(com.persianai.assistant.models.AIProvider.OPENAI) -> AIModel.GPT_4O_MINI
            else -> AIModel.TINY_LLAMA_OFFLINE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        ttsHelper = TTSHelper(this)
        ttsHelper.initialize()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        
        // Setup Voice Recording Helper
        voiceHelper = VoiceRecordingHelper(this)
        setupVoiceRecording()
    }

    protected abstract fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView
    protected abstract fun getMessageInput(): com.google.android.material.textfield.TextInputEditText
    protected abstract fun getSendButton(): View
    protected abstract fun getVoiceButton(): View

    protected open fun setupChatUI() {
        setupRecyclerView()
        setupListeners()
        setupAIClient()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(messages)
        getRecyclerView().apply {
            layoutManager = LinearLayoutManager(this@BaseChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private suspend fun transcribeWithHuggingFace(audioFile: File): String? = withContext(Dispatchers.IO) {
        if (hfApiKey.isBlank()) {
            Log.w("HF-STT", "HuggingFace key not set; skipping HF transcription")
            return@withContext null
        }
        return@withContext try {
            val bytes = audioFile.readBytes()
            val body = bytes.toRequestBody("audio/m4a".toMediaType())
            val request = Request.Builder()
                .url("https://api-inference.huggingface.co/models/openai/whisper-large-v3")
                .addHeader("Authorization", "Bearer $hfApiKey")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.e("HF-STT", "Failed: ${resp.code} ${resp.message}")
                    return@use null
                }
                val text = resp.body?.string()?.trim() ?: return@use null
                if (text.startsWith("{")) {
                    return@use try {
                        val json = org.json.JSONObject(text)
                        json.optString("text").ifBlank { json.optString("generated_text") }
                    } catch (_: Exception) {
                        text
                    }
                }
                text
            }
        } catch (e: Exception) {
            android.util.Log.e("HF-STT", "error: ${e.message}", e)
            null
        }
    }

    private fun setupAIClient() {
        val apiKeys = prefsManager.getAPIKeys()
        if (apiKeys.isNotEmpty()) {
            aiClient = AIClient(apiKeys)
            val resolved = chooseBestModel(apiKeys, prefsManager.getProviderPreference())
            currentModel = resolved
            prefsManager.saveSelectedModel(currentModel)
        } else {
            Toast.makeText(this, "کلید API آنلاین یافت نشد (حالت آفلاین فعال است).", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        getSendButton().setOnClickListener {
            sendMessage()
        }
        
        // تنظیم VoiceRecorderView یا VoiceActionButton
        try {
            voiceRecorderView = getVoiceButton() as? VoiceRecorderView
            if (voiceRecorderView != null) {
                voiceRecorderView!!.setListener(object : VoiceRecorderView.VoiceRecorderListener {
                    override fun onRecordingStarted() {
                        checkAudioPermissionAndStartRecording()
                    }
                    
                    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                        transcribeAudio(audioFile)
                    }
                    
                    override fun onRecordingCancelled() {
                        Toast.makeText(this@BaseChatActivity, "❌ ضبط لغو شد", Toast.LENGTH_SHORT).show()
                    }
                    
                    override fun onAmplitudeChanged(amplitude: Int) {
                        // نمایش شدت صدا
                    }
                })
                android.util.Log.d("BaseChatActivity", "VoiceRecorderView initialized successfully")
            } else {
                android.util.Log.w("BaseChatActivity", "VoiceRecorderView not found, voice recording disabled")
            }
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Error initializing VoiceRecorderView", e)
        }

        // If a unified VoiceActionButton exists in the layout, wire it up so
        // chat activities automatically benefit from the unified recorder.
        try {
            val vab = findViewById<com.persianai.assistant.ui.VoiceActionButton?>(
                resources.getIdentifier("voiceActionButton", "id", packageName)
            )
            if (vab != null) {
                vab.setListener(object : com.persianai.assistant.ui.VoiceActionButton.Listener {
                    override fun onRecordingStarted() {
                        onVoiceRecordingStarted()
                    }

                    override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                        transcribeAudio(audioFile)
                    }

                    override fun onTranscript(text: String) {
                        try {
                            getMessageInput().setText(text)
                            sendMessage()
                        } catch (_: Exception) { }
                    }

                    override fun onRecordingError(error: String) {
                        onVoiceRecordingError(error)
                    }
                })
                android.util.Log.d("BaseChatActivity", "VoiceActionButton wired")
            }
        } catch (e: Exception) {
            android.util.Log.w("BaseChatActivity", "VoiceActionButton not present or wiring failed", e)
        }
    }

    protected fun sendMessage() {
        val text = getMessageInput().text.toString().trim()
        if (text.isEmpty()) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = text, timestamp = System.currentTimeMillis())
        addMessage(userMessage)
        getMessageInput().text?.clear()

        getSendButton().isEnabled = false

        lifecycleScope.launch {
            try {
                val response = handleRequest(text)
                val aiMessage = ChatMessage(role = MessageRole.ASSISTANT, content = response, timestamp = System.currentTimeMillis())
                addMessage(aiMessage)
            } catch (e: Exception) {
                val errorMessage = ChatMessage(role = MessageRole.ASSISTANT, content = "❌ خطا: ${e.message}", timestamp = System.currentTimeMillis(), isError = true)
                addMessage(errorMessage)
            } finally {
                getSendButton().isEnabled = true
            }
        }
    }

    protected open suspend fun handleRequest(text: String): String = withContext(Dispatchers.IO) {
        val workingMode = prefsManager.getWorkingMode()
        val onlinePreferred = shouldUseOnlinePriority()

        // حالت پیش‌فرض: آفلاین TinyLlama
        if (!onlinePreferred || workingMode == PreferencesManager.WorkingMode.OFFLINE) {
            return@withContext offlineRespond(text)
        }

        if (aiClient == null) {
            return@withContext offlineRespond(text)
        }

        val model = chooseBestModel(prefsManager.getAPIKeys(), prefsManager.getProviderPreference())
        return@withContext try {
            currentModel = model
            val response = aiClient!!.sendMessage(
                model,
                messages,
                getSystemPrompt() + "\n\nپیام کاربر: " + text
            )
            response.content
        } catch (e: Exception) {
            android.util.Log.w("BaseChatActivity", "Online analysis failed: ${e.message}")
            offlineRespond(text)
        }
    }

    private fun offlineRespond(text: String): String {
        // تلاش برای استنتاج واقعی از مدل آفلاین (اگر موجود باشد)
        val modelPath = findOfflineModelPath()
        if (modelPath != null) {
            val prompt = buildString {
                append("شما یک دستیار فارسی هستید. پاسخ کوتاه و مستقیم بده.\n")
                append("کاربر: ").append(text).append("\nدستیار:")
            }
            try {
                val generated = com.persianai.assistant.offline.LocalLlamaRunner.infer(modelPath, prompt, maxTokens = 96)
                if (!generated.isNullOrBlank()) {
                    return "🟢 پاسخ آفلاین (TinyLlama):\n$generated"
                }
            } catch (e: Exception) {
                android.util.Log.w("BaseChatActivity", "Local inference failed: ${e.message}")
            }
        }

        // در صورت نبود مدل یا خطا، خلاصه ساده
        val summary = if (text.length > 140) text.take(140) + "…" else text
        return "🟢 پاسخ آفلاین (placeholder):\n$summary"
    }

    /**
     * یافتن مسیر مدل tinyllama دانلود‌شده (دستی یا از طریق OfflineModelManager)
     */
    private fun findOfflineModelPath(): String? {
        return try {
            val manager = com.persianai.assistant.models.OfflineModelManager(this)
            val list = manager.getDownloadedModels()
            // اول TinyLlama
            list.firstOrNull { it.first.name.contains("TinyLlama", ignoreCase = true) }?.second
                ?: list.firstOrNull()?.second
        } catch (e: Exception) {
            android.util.Log.w("BaseChatActivity", "findOfflineModelPath failed: ${e.message}")
            null
        }
    }

    protected open fun shouldUseOnlinePriority(): Boolean = false

    protected open fun getSystemPrompt(): String {
        return "شما یک دستیار هوشمند فارسی هستید."
    }

    protected fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        getRecyclerView().smoothScrollToPosition(messages.size - 1)
        if (message.role == MessageRole.ASSISTANT && !message.isError) {
            ttsHelper.speak(message.content)
        }
    }

    private fun checkAudioPermissionAndStartRecording() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_RECORD_AUDIO)
        }
    }

    protected fun transcribeAudio(audioFile: File) {
        lifecycleScope.launch {
            try {
                // در حالت جدید: بدون پنجره گوگل، فقط تلاش آنلاین (در صورت فعال بودن) وگرنه پیام آفلاین
                val transcribedText = aiClient?.transcribeAudio(audioFile.absolutePath)
                    ?.takeIf { !it.isNullOrBlank() }
                    ?: transcribeWithHuggingFace(audioFile)
                
                if (!transcribedText.isNullOrEmpty()) {
                    getMessageInput().setText(transcribedText)
                    Toast.makeText(this@BaseChatActivity, "✅ صوت به متن تبدیل شد", Toast.LENGTH_SHORT).show()
                    sendMessage()
                    return@launch
                }
                
                Toast.makeText(this@BaseChatActivity, "🎙️ فایل ضبط‌شده ذخیره شد (آفلاین). متن در دسترس نیست.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Transcription failed: ${e.message}", e)
                Toast.makeText(this@BaseChatActivity, "🎙️ ضبط آفلاین انجام شد (تبدیل ناموفق)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "⚠️ مجوز ضبط صوت لازم است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
        speechRecognizer.destroy()
        voiceHelper.cancelRecording()
    }
    
    // ===== Voice Recording Setup =====
    
    protected open fun setupVoiceRecording() {
        voiceHelper.setListener(object : VoiceRecordingHelper.RecordingListener {
            override fun onRecordingStarted() {
                Log.d("BaseChatActivity", "Recording started")
                onVoiceRecordingStarted()
            }
            
            override fun onRecordingCompleted(audioFile: File, durationMs: Long) {
                Log.d("BaseChatActivity", "Recording completed: ${audioFile.absolutePath}, Duration: ${durationMs}ms")
                onVoiceRecordingCompleted(audioFile, durationMs)
            }
            
            override fun onRecordingCancelled() {
                Log.d("BaseChatActivity", "Recording cancelled")
                onVoiceRecordingCancelled()
            }
            
            override fun onRecordingError(error: String) {
                Log.e("BaseChatActivity", "Recording error: $error")
                onVoiceRecordingError(error)
            }
        })
    }
    
    protected open fun onVoiceRecordingStarted() {
        Log.d("BaseChatActivity", "Voice recording started")
    }
    
    protected open fun onVoiceRecordingCompleted(audioFile: File, durationMs: Long) {
        Log.d("BaseChatActivity", "Voice recording completed")
        // پس از اتمام ضبط: تبدیل گفتار به متن و ارسال به چت
        transcribeAudio(audioFile)
    }
    
    protected open fun onVoiceRecordingCancelled() {
        Log.d("BaseChatActivity", "Voice recording cancelled")
    }
    
    protected open fun onVoiceRecordingError(error: String) {
        Log.e("BaseChatActivity", "Voice recording error: $error")
        Toast.makeText(this, "خطا: $error", Toast.LENGTH_SHORT).show()
    }
    
    protected fun startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            voiceHelper.startRecording()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }
    
    protected fun stopVoiceRecording() {
        voiceHelper.stopRecording()
    }
    
    protected fun cancelVoiceRecording() {
        voiceHelper.cancelRecording()
    }
}
