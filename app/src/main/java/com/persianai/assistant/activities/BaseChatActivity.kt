package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.EditText
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
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.TTSHelper
import com.persianai.assistant.utils.PreferencesManager.ProviderPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

abstract class BaseChatActivity : AppCompatActivity() {

    protected lateinit var binding: ViewBinding
    protected lateinit var chatAdapter: ChatAdapter
    protected lateinit var prefsManager: PreferencesManager
    protected lateinit var ttsHelper: TTSHelper
    protected var aiClient: AIClient? = null
    protected var currentModel: AIModel = AIModel.getDefaultModel()
    protected var conversationId: Long? = null
    protected var conversations: MutableList<Conversation> = mutableListOf()
    private var voiceRecorderView: VoiceRecorderView? = null
    private var directAudioAnalysisEnabled: Boolean = false
    private lateinit var conversationStorage: com.persianai.assistant.storage.ConversationStorage
    protected var currentConversation: com.persianai.assistant.models.Conversation? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }

    private fun chooseBestModel(apiKeys: List<APIKey>, pref: ProviderPreference): AIModel {
        val activeProviders = apiKeys.filter { it.isActive }.map { it.provider }.toSet()
        val fullPriority = listOf(
            AIModel.QWEN_2_5_1_5B,
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
        conversationStorage = com.persianai.assistant.storage.ConversationStorage(this)
    }

    protected abstract fun getRecyclerView(): androidx.recyclerview.widget.RecyclerView
    protected abstract fun getMessageInput(): com.google.android.material.textfield.TextInputEditText
    protected abstract fun getSendButton(): View
    protected abstract fun getVoiceButton(): View

    protected open fun setupChatUI() {
        loadPersistedMessages()
        setupRecyclerView()
        setupListeners()
        setupAIClient()
    }

    private fun loadPersistedMessages() {
        try {
            val ns = getNamespace()
            val currentId = conversationStorage.getCurrentConversationId()
            val current = currentId?.let { conversationStorage.getConversationSync(it) }
            val latestInNamespace = conversationStorage.getLatestConversation(ns)
            currentConversation = when {
                current != null && current.namespace == ns -> current
                latestInNamespace != null -> latestInNamespace
                else -> conversationStorage.createConversation(namespace = ns, title = "چت جدید")
            }
            messages.clear()
            messages.addAll(currentConversation?.messages ?: emptyList())
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Failed to load messages", e)
        }
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
                        if (directAudioAnalysisEnabled) {
                            analyzeAudio(audioFile)
                        } else {
                            transcribeAudio(audioFile)
                        }
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

        // نگه داشتن دکمه میکروفن برای سوییچ حالت «تحلیل مستقیم صوت»
        getVoiceButton().setOnLongClickListener {
            directAudioAnalysisEnabled = !directAudioAnalysisEnabled
            val msg = if (directAudioAnalysisEnabled) {
                "🎧 حالت تحلیل مستقیم صوت (HF Qwen-Audio) فعال شد"
            } else {
                "📝 حالت تبدیل صوت به متن فعال شد"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            true
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

    /**
     * نام فضای چت برای تفکیک بخش‌ها (دستیار، مشاور آرامش، مشاور مسیر، مسیریابی و ...)
     */
    protected open fun getNamespace(): String = "assistant"

    /**
     * ایجاد چت جدید در namespace فعلی
     */
    protected fun startNewConversation(defaultTitle: String = "چت جدید") {
        val conv = conversationStorage.createConversation(getNamespace(), defaultTitle)
        currentConversation = conv
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        scrollToBottom()
    }

    /**
     * نمایش لیست چت‌ها در namespace فعلی و امکان انتخاب/ایجاد/تغییر نام/حذف
     */
    protected fun showConversationManager() {
        val ns = getNamespace()
        val conversations = conversationStorage.getConversationsByNamespace(ns)
        val items = mutableListOf("➕ چت جدید").apply {
            addAll(conversations.map { it.title })
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("چت‌های بخش ${namespaceLabel(ns)}")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    startNewConversation()
                } else {
                    val conv = conversations[which - 1]
                    loadConversation(conv)
                }
            }
            .setNegativeButton("تغییر نام فعلی") { _, _ -> promptRenameCurrent() }
            .setNeutralButton("حذف چت فعلی") { _, _ -> deleteCurrentConversation() }
        builder.show()
    }

    private fun namespaceLabel(ns: String): String = when (ns) {
        "counseling" -> "مشاور آرامش"
        "career" -> "مشاور مسیر"
        "navigation" -> "مسیریابی"
        else -> "دستیار"
    }

    private fun loadConversation(conv: com.persianai.assistant.models.Conversation) {
        currentConversation = conv
        conversationStorage.setCurrentConversationId(conv.id)
        messages.clear()
        messages.addAll(conv.messages)
        chatAdapter.notifyDataSetChanged()
        scrollToBottom()
    }

    private fun promptRenameCurrent() {
        val conv = currentConversation ?: return
        val input = EditText(this).apply {
            setText(conv.title)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("تغییر نام چت")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val newTitle = input.text.toString().ifBlank { "چت جدید" }
                conversationStorage.updateConversationTitleSync(conv.id, newTitle)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun deleteCurrentConversation() {
        val conv = currentConversation ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("حذف چت")
            .setMessage("آیا مطمئن هستید؟")
            .setPositiveButton("حذف") { _, _ ->
                conversationStorage.deleteConversationSync(conv.id)
                currentConversation = conversationStorage.getLatestConversation(getNamespace())
                messages.clear()
                messages.addAll(currentConversation?.messages ?: emptyList())
                chatAdapter.notifyDataSetChanged()
                scrollToBottom()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun persistCurrentConversation() {
        val conv = currentConversation ?: conversationStorage.createConversation(getNamespace(), "چت جدید").also {
            currentConversation = it
        }
        conv.messages.clear()
        conv.messages.addAll(messages)
        conversationStorage.saveConversationSync(conv)
        conversationStorage.setCurrentConversationId(conv.id)
    }

    private fun scrollToBottom() {
        getRecyclerView().post {
            getRecyclerView().smoothScrollToPosition(messages.size.coerceAtLeast(0))
        }
    }

    private fun addMessage(message: ChatMessage) {
        messages.add(message)
        chatAdapter.notifyItemInserted(messages.size - 1)
        getRecyclerView().smoothScrollToPosition(messages.size - 1)
        try {
            persistCurrentConversation()
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Failed to persist messages", e)
        }
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
                val transcribedText = aiClient?.transcribeAudio(audioFile.absolutePath)
                
                if (!transcribedText.isNullOrEmpty()) {
                    getMessageInput().setText(transcribedText)
                    Toast.makeText(this@BaseChatActivity, "✅ صوت به متن تبدیل شد", Toast.LENGTH_SHORT).show()
                    sendMessage()
                    return@launch
                }
                
                Toast.makeText(this@BaseChatActivity, "⚠️ متن خالی برگشت (بررسی میکروفن/اینترنت/کلید)", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
                
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Transcription failed: ${e.message}", e)
                Toast.makeText(this@BaseChatActivity, "⚠️ تبدیل ناموفق (کلید یا اینترنت را چک کن)", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
            }
        }
    }

    /**
     * تحلیل مستقیم صوت با HF Qwen-Audio (بدون STT)
     */
    private fun analyzeAudio(audioFile: File) {
        lifecycleScope.launch {
            try {
                val result = aiClient?.analyzeAudio(audioFile.absolutePath)
                if (!result.isNullOrBlank()) {
                    getMessageInput().setText(result)
                    Toast.makeText(this@BaseChatActivity, "🎧 تحلیل صوتی HF آماده است", Toast.LENGTH_SHORT).show()
                    sendMessage()
                    return@launch
                }
                Toast.makeText(this@BaseChatActivity, "⚠️ خروجی خالی از تحلیل صوتی؛ تلاش با STT", Toast.LENGTH_SHORT).show()
                transcribeAudio(audioFile)
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Audio analysis failed: ${e.message}", e)
                Toast.makeText(this@BaseChatActivity, "⚠️ تحلیل صوتی ناموفق (کلید یا اینترنت را چک کن)", Toast.LENGTH_SHORT).show()
                transcribeAudio(audioFile)
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
