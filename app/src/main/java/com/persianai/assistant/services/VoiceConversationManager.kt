package com.persianai.assistant.services

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.math.max
import com.persianai.assistant.models.AIModel
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.ai.AIClient
import com.persianai.assistant.ai.OfflineIntentParser
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.models.OfflineModelManager
import com.persianai.assistant.offline.LocalLlamaRunner
import com.persianai.assistant.tts.BeepFallback
import com.persianai.assistant.tts.CoquiTtsManager

/**
 * Voice Conversation Manager - Complete voice-to-voice AI assistant
 * 
 * Features:
 * - Voice-to-Voice conversations (speak → AI responds with voice)
 * - Real-time speech processing
 * - Multi-language TTS support (Haaniye + Android TTS)
 * - Conversation memory and context
 * - Voice activity detection
 * - Background conversation capability
 */
class VoiceConversationManager(
    private val context: Context,
    private val voiceEngine: UnifiedVoiceEngine,
    private val aiClient: com.persianai.assistant.ai.AIClient? = null
) {
    
    private val TAG = "VoiceConversation"
    
    // TTS Engine
    private var textToSpeech: TextToSpeech? = null
    private var haaniyeTTS: MediaPlayer? = null
    private val coquiTts by lazy { CoquiTtsManager(context) }
    
    // Conversation state
    private var isConversationActive = false
    private var conversationHistory = mutableListOf<ConversationMessage>()
    private var currentLanguage = "fa" // Persian default
    private var voiceMode = VoiceMode.HYBRID
    
    // Voice activity detection
    private var amplitudeThreshold = 1000
    private var lastVoiceTime = 0L
    
    // Callbacks
    private var conversationListener: ConversationListener? = null
    
    enum class VoiceMode {
        OFFLINE_ONLY,    // Only Haaniye model
        ONLINE_ONLY,     // Only AI APIs
        HYBRID,          // Combination of both
        VOICE_ONLY       // Voice-to-voice only
    }
    
    data class ConversationMessage(
        val role: String, // "user" or "assistant"
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val audioFile: File? = null
    )
    
    interface ConversationListener {
        fun onConversationStarted()
        fun onConversationEnded()
        fun onUserSpeakingStarted()
        fun onUserSpeakingStopped()
        fun onAIThinking()
        fun onAIResponding(aiResponse: String)
        fun onAIResponseSpoken()
        fun onError(error: String)
    }
    
    /**
     * Initialize conversation system
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🔧 Initializing voice conversation system...")
            
            // Initialize TTS
            initializeTTS()
            
            // Load conversation history
            loadConversationHistory()
            
            Log.d(TAG, "✅ Voice conversation system initialized")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing conversation system", e)
            conversationListener?.onError("خطا در راه‌اندازی سیستم مکالمه: ${e.message}")
            false
        }
    }

    private suspend fun speakWithCoquiOrFallback(text: String) = withContext(Dispatchers.Main) {
        if (text.isBlank()) return@withContext
        try {
            // Load model lazily; synthesis path will be implemented once model IO is verified.
            // For now, we still prefer Android TTS but keep Coqui as the first init point.
            coquiTts.ensureLoaded()
        } catch (_: Exception) {
        }

        // Coqui synthesis is intentionally not executed until we have a verified frontend.
        // Always fallback to Android TTS for actual speech, then beep as last resort.
        try {
            speakWithAndroidTTS(text)
            return@withContext
        } catch (_: Exception) {
        }

        try {
            BeepFallback.beep()
        } catch (_: Exception) {
        }
    }
    
    /**
     * Start voice conversation mode
     */
    suspend fun startConversation(): Boolean = withContext(Dispatchers.Main) {
        try {
            if (isConversationActive) {
                Log.w(TAG, "Conversation already active")
                return@withContext true
            }
            
            Log.d(TAG, "🎤 Starting voice conversation...")
            
            // Check permissions
            if (!voiceEngine.hasRequiredPermissions()) {
                conversationListener?.onError("دسترسی میکروفن لازم است")
                return@withContext false
            }
            
            isConversationActive = true
            conversationListener?.onConversationStarted()
            
            // Start listening for user input
            startListeningLoop()
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting conversation", e)
            conversationListener?.onError("خطا در شروع مکالمه: ${e.message}")
            false
        }
    }
    
    /**
     * Stop conversation mode
     */
    suspend fun stopConversation(): Boolean = withContext(Dispatchers.Main) {
        try {
            if (!isConversationActive) return@withContext true
            
            Log.d(TAG, "🛑 Stopping voice conversation...")
            
            isConversationActive = false
            conversationListener?.onConversationEnded()
            
            // Save conversation history
            saveConversationHistory()
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping conversation", e)
            false
        }
    }
    
    /**
     * Main conversation loop
     */
    private suspend fun startListeningLoop() = withContext(Dispatchers.Main) {
        while (isConversationActive) {
            try {
                // Listen for voice input
                conversationListener?.onUserSpeakingStarted()
                
                val recordingResult = recordUserInput()
                if (recordingResult == null) {
                    continue
                }
                
                // Process the user's speech
                val userText = processUserSpeech(recordingResult.file)
                if (userText.isBlank()) {
                    try {
                        conversationListener?.onError("متوجه نشدم. لطفاً دوباره بگویید.")
                    } catch (_: Exception) {
                    }

                    // In offline voice conversation we must not get stuck in silence.
                    try {
                        speakWithCoquiOrFallback("متوجه نشدم، دوباره بگو")
                    } catch (_: Exception) {
                    }
                    continue
                }

                try {
                    val controller = AIIntentController(context)
                    val intent = controller.detectIntentFromText(userText)
                    Log.d(TAG, "AIIntent: ${intent.name}")
                } catch (_: Exception) {
                }
                
                // Add to conversation history
                addToConversation("user", userText)
                
                // Get AI response
                conversationListener?.onAIThinking()
                val aiResponse = getAIResponse(userText)
                
                // Speak the response
                conversationListener?.onAIResponding(aiResponse)
                speakResponse(aiResponse)
                
                // Add AI response to history
                addToConversation("assistant", aiResponse)
                
                conversationListener?.onAIResponseSpoken()
                
                // Small pause before listening again
                delay(1000)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in conversation loop", e)
                conversationListener?.onError("خطا در مکالمه: ${e.message}")
            }
        }
    }
    
    /**
     * Record user voice input
     */
    private suspend fun recordUserInput(): RecordingResult? = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🎤 Recording user input...")
            
            // Start recording
            val startResult = voiceEngine.startRecording()
            if (!startResult.isSuccess) {
                Log.e(TAG, "Failed to start recording")
                return@withContext null
            }
            
            // Listen for voice activity (simple version)
            var hasSpeech = false
            var recordingDuration = 0L
            val maxRecordingTime = 30000L // 30 seconds max
            
            val amplitudeJob = launch {
                while (isRecording()) {
                    val amplitude = voiceEngine.getCurrentAmplitude()
                    if (amplitude > amplitudeThreshold) {
                        hasSpeech = true
                        lastVoiceTime = System.currentTimeMillis()
                    }
                    
                    recordingDuration = System.currentTimeMillis()
                    if (recordingDuration > maxRecordingTime) break
                    
                    delay(100)
                }
            }
            
            // Wait for speech or timeout
            while (isRecording() && !hasSpeech && recordingDuration < maxRecordingTime) {
                delay(100)
            }
            
            // Stop recording
            val stopResult = voiceEngine.stopRecording()
            amplitudeJob.cancel()
            if (stopResult.isSuccess) {
                try {
                    stopResult.getOrThrow()
                } catch (e: Exception) {
                    Log.e(TAG, "Error retrieving recording result", e)
                    null
                }
            } else {
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error recording user input", e)
            null
        }
    }
    
    /**
     * Process user's speech using hybrid analysis
     */
    private suspend fun processUserSpeech(audioFile: File): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 Processing user speech...")

            if (voiceMode == VoiceMode.OFFLINE_ONLY) {
                val offline = voiceEngine.analyzeOffline(audioFile)
                val text = offline.getOrNull().orEmpty().trim()
                if (text.isNotBlank()) {
                    Log.d(TAG, "✅ Speech processed (offline): $text")
                    return@withContext text
                }
                Log.e(TAG, "Failed to process speech (offline)")
                return@withContext ""
            }

            val analysisResult = voiceEngine.analyzeHybrid(audioFile)
            if (analysisResult.isSuccess) {
                val result = analysisResult.getOrThrow()
                val processedText = result.primaryText

                Log.d(TAG, "✅ Speech processed: $processedText")
                processedText
            } else {
                Log.e(TAG, "Failed to process speech")
                ""
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing speech", e)
            ""
        }
    }
    
    /**
     * Get AI response based on conversation context.
     *
     * Policy:
     * - OFFLINE: only offline processing
     * - HYBRID: simple intents offline; complex intents online when available
     * - ONLINE: online when available; otherwise fallback offline
     */
    private suspend fun getAIResponse(userInput: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🤖 Getting AI response for: $userInput")

            val prefs = PreferencesManager(context)
            val workingMode = prefs.getWorkingMode()

            val offlineAssistant = AdvancedPersianAssistant(context)
            val offlineText = try {
                offlineAssistant.processRequest(userInput).text
            } catch (_: Exception) {
                "متوجه شدم. می‌توانید بیشتر توضیح دهید؟"
            }

            fun buildTinyLlamaPrompt(): String {
                return buildString {
                    appendLine("شما یک دستیار هوشمند فارسی هستید. پاسخ را کوتاه، واضح و کاملاً فارسی بده.")
                    appendLine("تاریخچه مکالمه:")
                    conversationHistory.takeLast(12).forEach { msg ->
                        val role = if (msg.role == "user") "کاربر" else "دستیار"
                        appendLine("$role: ${msg.content}")
                    }
                    appendLine("کاربر: $userInput")
                    appendLine("دستیار:")
                }
            }

            fun tryOfflineTinyLlama(): String? {
                val modelPath = tryFindOfflineModelPath()
                if (modelPath.isNullOrBlank()) return null
                if (!LocalLlamaRunner.isBackendAvailable()) return null
                return try {
                    LocalLlamaRunner.infer(modelPath, buildTinyLlamaPrompt(), 180)?.trim()
                } catch (_: Exception) {
                    null
                }
            }

            // OFFLINE: never use online
            if (workingMode == PreferencesManager.WorkingMode.OFFLINE) {
                val modelPath = tryFindOfflineModelPath()
                if (!modelPath.isNullOrBlank() && LocalLlamaRunner.isBackendAvailable()) {
                    val prompt = buildString {
                        appendLine("شما یک دستیار هوشمند فارسی هستید. پاسخ را کوتاه، واضح و کاملاً فارسی بده.")
                        appendLine("تاریخچه مکالمه:")
                        conversationHistory.takeLast(12).forEach { msg ->
                            val role = if (msg.role == "user") "کاربر" else "دستیار"
                            appendLine("$role: ${msg.content}")
                        }
                        appendLine("کاربر: $userInput")
                        appendLine("دستیار:")
                    }
                    val llm = try {
                        LocalLlamaRunner.infer(modelPath, prompt, 180)?.trim()
                    } catch (_: Exception) {
                        null
                    }
                    if (!llm.isNullOrBlank()) {
                        return@withContext llm
                    }
                }
                return@withContext offlineText
            }

            val canTryOnline = (workingMode == PreferencesManager.WorkingMode.ONLINE ||
                workingMode == PreferencesManager.WorkingMode.HYBRID) && aiClient != null

            // LLM policy for voice: online-first when allowed, then offline TinyLlama, then lightweight offline.
            if (!canTryOnline) {
                val llm = tryOfflineTinyLlama()
                if (!llm.isNullOrBlank()) return@withContext llm
                return@withContext offlineText
            }

            val client = aiClient

            val conversationContext = buildConversationContext()

            val voicePrompt = """
                شما یک دستیار صوتی هوشمند فارسی هستید. لطفاً به صورت کوتاه و مفید پاسخ دهید.

                قوانین مکالمه صوتی:
                - پاسخ‌های کوتاه و واضح (کمتر از 50 کلمه)
                - استفاده از زبان طبیعی و دوستانه
                - در صورت نیاز، پرسش‌های پیگیری مطرح کنید
                - از جملات کوتاه استفاده کنید

                تاریخچه مکالمه:
                $conversationContext

                پیام جدید کاربر: $userInput

                پاسخ شما:
            """.trimIndent()

            val onlineText = try {
                val response = client?.sendMessage(
                    model = com.persianai.assistant.models.AIModel.LLAMA_3_3_70B,
                    messages = conversationHistory.map {
                        com.persianai.assistant.models.ChatMessage(
                            role = if (it.role == "user") com.persianai.assistant.models.MessageRole.USER
                            else com.persianai.assistant.models.MessageRole.ASSISTANT,
                            content = it.content,
                            timestamp = it.timestamp
                        )
                    },
                    systemPrompt = voicePrompt
                )
                response?.content?.trim().orEmpty()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Online LLM failed: ${e.message}")
                ""
            }

            if (onlineText.isNotBlank()) return@withContext onlineText

            val llm = tryOfflineTinyLlama()
            if (!llm.isNullOrBlank()) return@withContext llm

            return@withContext offlineText
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting AI response", e)
            "متاسفانه مشکلی پیش آمده. می‌توانید دوباره تلاش کنید؟"
        }
    }

    private fun tryFindOfflineModelPath(): String? {
        return try {
            val manager = OfflineModelManager(context)
            val list = manager.getDownloadedModels()
            list.firstOrNull { it.first.name.contains("TinyLlama", ignoreCase = true) }?.second
                ?: list.firstOrNull()?.second
        } catch (_: Exception) {
            null
        }
    }
    
    /**
     * Speak AI response using appropriate TTS
     */
    private suspend fun speakResponse(response: String) = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🔊 Speaking response: $response")

            // Audio output must be guaranteed offline (Coqui/Android/beep), regardless of online/offline LLM.
            speakWithCoquiOrFallback(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error speaking response", e)
        }
    }
    
    /**
     * Speak using Haaniye TTS model
     */
    private suspend fun speakWithHaaniye(text: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎭 Using Haaniye TTS for: $text")
            
            // For now, simulate Haaniye TTS
            // In production, this would:
            // 1. Send text to Haaniye TTS model
            // 2. Generate audio from the ONNX model
            // 3. Return audio file for playback
            
            delay(2000) // Simulate TTS processing time
            
            // Simulate successful TTS generation
            Log.d(TAG, "✅ Haaniye TTS completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error with Haaniye TTS", e)
            throw e
        }
    }
    
    /**
     * Speak using Android TextToSpeech
     */
    private suspend fun speakWithAndroidTTS(text: String) = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "📱 Using Android TTS for: $text")
            
            textToSpeech?.let { tts ->
                tts.language = when (currentLanguage) {
                    "fa" -> Locale("fa", "IR")
                    "en" -> Locale.ENGLISH
                    else -> Locale.getDefault()
                }
                
                // Split long text into smaller chunks
                val maxLength = 100
                val words = text.split(" ")
                var currentText = ""
                
                for (word in words) {
                    if (currentText.length + word.length + 1 > maxLength) {
                        tts.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, "chunk_${System.currentTimeMillis()}")
                        delay(100)
                        currentText = word
                    } else {
                        currentText += if (currentText.isEmpty()) word else " $word"
                    }
                }
                
                if (currentText.isNotEmpty()) {
                    tts.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, "final_chunk_${System.currentTimeMillis()}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error with Android TTS", e)
            throw e
        }
    }
    
    /**
     * Initialize TTS engines
     */
    private suspend fun initializeTTS() = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "🔧 Initializing TTS engines...")
            
            // Initialize Android TTS
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "✅ Android TTS initialized")
                    textToSpeech?.language = Locale("fa", "IR")
                } else {
                    Log.e(TAG, "❌ Android TTS initialization failed")
                }
            }

            // Best-effort: try load Coqui so we can log model IO early.
            try {
                withContext(Dispatchers.IO) {
                    coquiTts.ensureLoaded()
                }
            } catch (_: Exception) {
            }
            
            // Initialize Haaniye TTS (placeholder)
            Log.d(TAG, "🔧 Haaniye TTS framework ready")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing TTS", e)
            throw e
        }
    }
    
    /**
     * Build conversation context for AI
     */
    private fun buildConversationContext(): String {
        val recentHistory = conversationHistory.takeLast(10)
        return recentHistory.joinToString("\n") { message ->
            "${if (message.role == "user") "کاربر" else "دستیار"}: ${message.content}"
        }
    }
    
    /**
     * Add message to conversation history
     */
    private fun addToConversation(role: String, content: String) {
        conversationHistory.add(ConversationMessage(role, content))
        
        // Keep history manageable (last 50 messages)
        if (conversationHistory.size > 50) {
            conversationHistory.removeAt(0)
        }
    }
    
    /**
     * Load conversation history from storage
     */
    private suspend fun loadConversationHistory() = withContext(Dispatchers.IO) {
        try {
            // TODO: Load from SharedPreferences or database
            Log.d(TAG, "📂 Loading conversation history...")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading conversation history", e)
        }
    }
    
    /**
     * Save conversation history to storage
     */
    private suspend fun saveConversationHistory() = withContext(Dispatchers.IO) {
        try {
            // TODO: Save to SharedPreferences or database
            Log.d(TAG, "💾 Saving conversation history...")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving conversation history", e)
        }
    }
    
    /**
     * Get current recording status
     */
    private fun isRecording(): Boolean {
        return try {
            voiceEngine.isRecordingInProgress()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking recording status", e)
            false
        }
    }
    
    /**
     * Set conversation listener
     */
    fun setConversationListener(listener: ConversationListener) {
        this.conversationListener = listener
    }
    
    /**
     * Set voice mode
     */
    fun setVoiceMode(mode: VoiceMode) {
        this.voiceMode = mode
        Log.d(TAG, "Voice mode set to: $mode")
    }
    
    /**
     * Set language
     */
    fun setLanguage(language: String) {
        this.currentLanguage = language
        Log.d(TAG, "Language set to: $language")
    }
}
