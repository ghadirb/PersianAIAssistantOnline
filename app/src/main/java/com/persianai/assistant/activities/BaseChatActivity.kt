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
import com.persianai.assistant.models.APIKey
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.ui.VoiceRecorderView
import com.persianai.assistant.utils.DefaultApiKeys
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.TTSHelper
import com.persianai.assistant.utils.PreferencesManager.ProviderPreference
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
    protected var currentModel: AIModel = AIModel.LLAMA_3_3_70B
    protected val messages = mutableListOf<ChatMessage>()
    private lateinit var speechRecognizer: SpeechRecognizer
    private var voiceRecorderView: VoiceRecorderView? = null
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
        val activeProviders = apiKeys.filter { it.isActive }.map { it.provider }.toSet()
        val fullPriority = listOf(
            AIModel.LLAMA_3_3_70B,
            AIModel.DEEPSEEK_R1T2,
            AIModel.MIXTRAL_8X7B,
            AIModel.LLAMA_2_70B,
            AIModel.CLAUDE_SONNET,
            AIModel.CLAUDE_HAIKU,
            AIModel.GPT_4O,
            AIModel.GPT_4O_MINI
        )
        val filtered = when (pref) {
            ProviderPreference.OPENAI_ONLY -> fullPriority.filter { it.provider == com.persianai.assistant.models.AIProvider.OPENAI }
            ProviderPreference.SMART_ROUTE -> fullPriority.filter { it.provider != com.persianai.assistant.models.AIProvider.OPENAI } + fullPriority.filter { it.provider == com.persianai.assistant.models.AIProvider.OPENAI }
            ProviderPreference.AUTO -> fullPriority
        }
        return filtered.firstOrNull { activeProviders.contains(it.provider) } ?: AIModel.getDefaultModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        ttsHelper = TTSHelper(this)
        ttsHelper.initialize()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
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
            val preferred = prefsManager.getSelectedModel()
            val providerPref = prefsManager.getProviderPreference()
            val resolved = if (apiKeys.any { it.provider == preferred.provider && it.isActive }) {
                preferred
            } else {
                chooseBestModel(apiKeys, providerPref)
            }
            currentModel = resolved
            prefsManager.saveSelectedModel(currentModel)
        } else {
            Toast.makeText(this, "کلید API یافت نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        getSendButton().setOnClickListener {
            sendMessage()
        }
        
        // تنظیم VoiceRecorderView
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
        if (aiClient == null) return@withContext "سرویس آنلاین در دسترس نیست."
        // تلاش برای Puter.js (stub) در حالت AUTO یا SMART_ROUTE
        val providerPref = prefsManager.getProviderPreference()
        if (providerPref == ProviderPreference.AUTO || providerPref == ProviderPreference.SMART_ROUTE) {
            try {
                val puterReply = com.persianai.assistant.ai.PuterBridge.chat(text, messages)
                if (!puterReply.isNullOrBlank()) {
                    return@withContext puterReply
                }
            } catch (_: Exception) {
                // ساکت: مستقیماً fallback
            }
        }
        val response = aiClient!!.sendMessage(currentModel, messages, getSystemPrompt() + "\n\nپیام کاربر: " + text)
        return@withContext response.content
    }

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

    private fun transcribeAudio(audioFile: File) {
        lifecycleScope.launch {
            try {
                // تلاش اول: OpenAI/Whisper (اگر کلید موجود باشد)
                val transcribedText = aiClient?.transcribeAudio(audioFile.absolutePath)
                    ?.takeIf { !it.isNullOrBlank() }
                    ?: transcribeWithHuggingFace(audioFile)
                
                if (!transcribedText.isNullOrEmpty()) {
                    getMessageInput().setText(transcribedText)
                    Toast.makeText(this@BaseChatActivity, "✅ صوت به متن تبدیل شد", Toast.LENGTH_SHORT).show()
                    sendMessage()
                    return@launch
                }
                
                Toast.makeText(this@BaseChatActivity, "⚠️ متن خالی برگشت", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
                
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Transcription failed: ${e.message}", e)
                Toast.makeText(this@BaseChatActivity, "⚠️ تبدیل ناموفق", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
            }
        }
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "سرویس تشخیص گفتار در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "در حال شنیدن...")
        }
        startActivityForResult(intent, REQUEST_RECORD_AUDIO)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RECORD_AUDIO && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)
            if (!spokenText.isNullOrEmpty()) {
                getMessageInput().setText(spokenText)
                sendMessage()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // مجوز داده شد، VoiceRecorderView خودش ضبط را ادامه می‌دهد
            } else {
                Toast.makeText(this, "⚠️ مجوز ضبط صوت لازم است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
        speechRecognizer.destroy()
    }
}
