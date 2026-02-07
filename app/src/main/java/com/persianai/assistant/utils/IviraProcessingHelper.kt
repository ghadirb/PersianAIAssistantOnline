package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.models.MessageRole
import com.persianai.assistant.integration.IviraIntegrationManager
import com.persianai.assistant.core.QueryRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper utility for Ivira API processing with fallback support
 * Used by BaseChatActivity, HybridVoiceRecorder, and HybridTTS
 */
object IviraProcessingHelper {

    private const val TAG = "IviraProcessingHelper"

    /**
     * Process message with Ivira priority
     * Tries Ivira first, falls back to local model if needed
     *
     * @param context Android context
     * @param userMessage User input message
     * @param conversationHistory Previous messages for context
     * @return Response string or null if failed
     */
    suspend fun processWithIviraPriority(
        context: Context,
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing message with Ivira priority")
            
            // Try Ivira first
            val iviraManager = IviraIntegrationManager(context)
            if (iviraManager.hasValidTokens()) {
                Log.d(TAG, "Ivira tokens available, attempting...")
                
                val result = iviraManager.sendMessage(
                    userMessage = userMessage,
                    conversationHistory = conversationHistory
                )
                
                if (result.isNotEmpty()) {
                    Log.d(TAG, "✅ Ivira response received: ${result.take(50)}...")
                    return@withContext result
                }
            }
            
            // Fallback to local model
            Log.w(TAG, "⚠️ Ivira unavailable or empty response, falling back to local")
            return@withContext processFallbackLocal(userMessage, conversationHistory)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Ivira processing", e)
            return@withContext processFallbackLocal(userMessage, conversationHistory)
        }
    }

    /**
     * Process voice input with Ivira priority
     *
     * @param context Android context
     * @param audioPath Path to audio file
     * @return Transcribed text or null if failed
     */
    suspend fun processVoiceWithIviraPriority(
        context: Context,
        audioPath: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing voice with Ivira priority")
            
            val iviraManager = IviraIntegrationManager(context)
            if (iviraManager.hasValidTokens()) {
                Log.d(TAG, "Attempting Ivira STT...")
                
                val result = iviraManager.speechToText(audioPath)
                if (result.isNotEmpty()) {
                    Log.d(TAG, "✅ Ivira STT result: ${result.take(50)}...")
                    return@withContext result
                }
            }
            
            Log.w(TAG, "⚠️ Ivira STT unavailable, using fallback")
            return@withContext null  // Would use fallback STT in calling code
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in voice processing", e)
            return@withContext null
        }
    }

    /**
     * Process text-to-speech with Ivira priority
     *
     * @param context Android context
     * @param text Text to convert
     * @return Audio bytes or null if failed
     */
    suspend fun processTTSWithIviraPriority(
        context: Context,
        text: String
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing TTS with Ivira priority")
            
            val iviraManager = IviraIntegrationManager(context)
            if (iviraManager.hasValidTokens()) {
                Log.d(TAG, "Attempting Ivira TTS...")
                
                val result = iviraManager.textToSpeech(text)
                if (result != null && result.isNotEmpty()) {
                    Log.d(TAG, "✅ Ivira TTS result: ${result.size} bytes")
                    return@withContext result
                }
            }
            
            Log.w(TAG, "⚠️ Ivira TTS unavailable, using fallback")
            return@withContext null  // Would use fallback TTS in calling code
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in TTS processing", e)
            return@withContext null
        }
    }

    /**
     * Fallback local model processing
     */
    private fun processFallbackLocal(
        userMessage: String,
        conversationHistory: List<ChatMessage>
    ): String {
        Log.d(TAG, "Using offline fallback response")
        
        // Simple offline response strategy
        return when {
            userMessage.contains("سلام", ignoreCase = true) -> "سلام! چطور می‌تونم کمکتون کنم؟"
            userMessage.contains("نام", ignoreCase = true) -> "من دستیار هوشمند فارسی هستم."
            else -> "متأسفانه نمی‌تونم الان پاسخ کاملی بدم. لطفاً بعداً دوباره تلاش کنید."
        }
    }

    /**
     * Check if Ivira is available and properly configured
     */
    fun isIviraAvailable(context: Context): Boolean {
        return try {
            val iviraManager = IviraIntegrationManager(context)
            iviraManager.hasValidTokens()
        } catch (e: Exception) {
            Log.w(TAG, "Ivira availability check failed", e)
            false
        }
    }

    /**
     * Get Ivira status for logging/debugging
     */
    fun getIviraStatus(context: Context): String {
        return try {
            val iviraManager = IviraIntegrationManager(context)
            iviraManager.getTokensStatus()
        } catch (e: Exception) {
            "Error checking Ivira status: ${e.message}"
        }
    }
}
