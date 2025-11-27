package com.persianai.assistant.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.utils.TTSHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Timer
import java.util.TimerTask

abstract class BaseChatActivity : AppCompatActivity() {

    protected lateinit var binding: ViewBinding
    protected lateinit var chatAdapter: ChatAdapter
    protected lateinit var prefsManager: PreferencesManager
    protected lateinit var ttsHelper: TTSHelper
    protected var aiClient: AIClient? = null
    protected var currentModel: AIModel = AIModel.GPT_4O_MINI
    protected val messages = mutableListOf<ChatMessage>()
    private lateinit var speechRecognizer: SpeechRecognizer
    
    // متغیرهای ضبط صوت
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFilePath = ""
    private var recordingTimer: Timer? = null
    private var permissionRequested = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1002
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

    private fun setupAIClient() {
        val apiKeys = prefsManager.getAPIKeys()
        if (apiKeys.isNotEmpty()) {
            aiClient = AIClient(apiKeys)
            currentModel = prefsManager.getSelectedModel()
        } else {
            Toast.makeText(this, "کلید API یافت نشد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupListeners() {
        getSendButton().setOnClickListener {
            sendMessage()
        }
        getVoiceButton().setOnClickListener {
            if (isRecording) {
                stopRecordingAndTranscribe()
            } else {
                checkAudioPermissionAndStartRecording()
            }
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
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            if (!permissionRequested) {
                permissionRequested = true
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_RECORD_AUDIO)
            } else {
                Toast.makeText(this, "❌ مجوز ضبط صوت لازم است. لطفاً در تنظیمات برنامه فعال کنید.", Toast.LENGTH_LONG).show()
            }
        } else {
            permissionRequested = false
            startVoiceRecordingWithFallback()
        }
    }

    private fun startVoiceRecordingWithFallback() {
        // ابتدا سعی کن ضبط صوت را شروع کن
        try {
            startVoiceRecording()
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Voice recording failed, falling back to SpeechRecognizer", e)
            // اگر ضبط صوت ناموفق بود، به SpeechRecognizer fallback کن
            startSpeechRecognition()
        }
    }

    private fun startVoiceRecording() {
        try {
            val audioDir = File(getExternalFilesDir(null), "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            audioFilePath = File(audioDir, "recording_${System.currentTimeMillis()}.m4a").absolutePath
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            
            isRecording = true
            Toast.makeText(this, "🎤 ضبط صوت شروع شد", Toast.LENGTH_SHORT).show()
            
            // خودکار متوقف کن بعد از ۳۰ ثانیه
            recordingTimer = Timer()
            recordingTimer?.schedule(object : TimerTask() {
                override fun run() {
                    if (isRecording) {
                        stopRecordingAndTranscribe()
                    }
                }
            }, 30000)
            
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Error starting voice recording", e)
            Toast.makeText(this, "❌ خطا در شروع ضبط: ${e.message}", Toast.LENGTH_SHORT).show()
            throw e
        }
    }

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        
        try {
            recordingTimer?.cancel()
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            Toast.makeText(this, "🎤 در حال تبدیل صوت به متن...", Toast.LENGTH_LONG).show()
            transcribeAudioWithFallback()
            
        } catch (e: Exception) {
            android.util.Log.e("BaseChatActivity", "Error stopping recording", e)
            Toast.makeText(this, "❌ خطا در پایان ضبط: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun transcribeAudioWithFallback() {
        lifecycleScope.launch {
            try {
                // ابتدا سعی کن Whisper API را استفاده کن
                val transcribedText = aiClient?.transcribeAudio(audioFilePath)
                
                if (!transcribedText.isNullOrEmpty()) {
                    getMessageInput().setText(transcribedText)
                    Toast.makeText(this@BaseChatActivity, "✅ صوت به متن تبدیل شد", Toast.LENGTH_SHORT).show()
                    sendMessage()
                    return@launch
                }
                
                // اگر Whisper متن خالی برگرداند
                android.util.Log.w("BaseChatActivity", "Whisper returned empty text, falling back to SpeechRecognizer")
                Toast.makeText(this@BaseChatActivity, "⚠️ Whisper متن خالی برگرداند، از تشخیص صوت استفاده می‌شود...", Toast.LENGTH_SHORT).show()
                startSpeechRecognition()
                
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Whisper transcription failed: ${e.message}", e)
                Toast.makeText(this@BaseChatActivity, "⚠️ Whisper ناموفق، از تشخیص صوت استفاده می‌شود...", Toast.LENGTH_SHORT).show()
                // Fallback به SpeechRecognizer
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
                startVoiceRecordingWithFallback()
            } else {
                Toast.makeText(this, "⚠️ مجوز ضبط صوت لازم است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper.shutdown()
        speechRecognizer.destroy()
        recordingTimer?.cancel()
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                android.util.Log.e("BaseChatActivity", "Error releasing MediaRecorder", e)
            }
        }
    }
}
