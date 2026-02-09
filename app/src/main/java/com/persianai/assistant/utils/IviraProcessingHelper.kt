package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.integration.IviraIntegrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object IviraProcessingHelper {

    private const val TAG = "IviraProcessingHelper"

    suspend fun processWithIviraPriority(
        context: Context,
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing message")
            
            val iviraManager = IviraIntegrationManager(context)
            if (hasValidTokens(context)) {
                var success = false
                iviraManager.sendMessageViaIvira(
                    message = userMessage,
                    onSuccess = { response ->
                        onResult(response)
                        success = true
                    },
                    onError = { error ->
                        onError(error)
                        success = false
                    }
                )
                if (success) return@withContext true
            }
            
            val fallback = processFallbackLocal(userMessage)
            onResult(fallback)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            onResult(processFallbackLocal(userMessage))
            return@withContext false
        }
    }

    suspend fun processVoiceWithIviraPriority(
        context: Context,
        audioPath: String,
        onError: (String) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(audioPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                onError("Audio file missing")
                return@withContext null
            }

            val iviraManager = IviraIntegrationManager(context)
            if (!hasValidTokens(context)) {
                onError("Ivira tokens unavailable")
                return@withContext null
            }

            var resultText: String? = null
            var succeeded = false
            iviraManager.speechToTextViaIvira(
                audioFile = audioFile,
                onSuccess = { text ->
                    resultText = text
                    succeeded = true
                },
                onError = { err ->
                    onError(err)
                    succeeded = false
                }
            )

            if (succeeded) resultText else null
        } catch (e: Exception) {
            Log.e(TAG, "Error processing voice", e)
            onError(e.message ?: "Error")
            null
        }
    }

    suspend fun processTTSWithIviraPriority(
        context: Context,
        text: String,
        onResult: (ByteArray) -> Unit = {},
        onError: (String) -> Unit = {}
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing TTS")
            
            val iviraManager = IviraIntegrationManager(context)
            if (hasValidTokens(context)) {
                var result: ByteArray? = null
                var success = false
                
                iviraManager.textToSpeechViaIvira(
                    text = text,
                    onSuccess = { audioBytes ->
                        result = audioBytes
                        success = true
                        onResult(audioBytes)
                    },
                    onError = { error ->
                        onError(error)
                        success = false
                    }
                )
                
                if (success && result != null) {
                    return@withContext result
                }
            }
            
            onError("TTS not available")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            return@withContext null
        }
    }

    private fun processFallbackLocal(userMessage: String): String {
        return when {
            userMessage.contains("سلام") -> "سلام!"
            else -> "متأسفانه نمی‌تونم الان پاسخ بدم"
        }
    }

    private fun hasValidTokens(context: Context): Boolean {
        return try {
            val iviraManager = IviraIntegrationManager(context)
            iviraManager.getIviraTokens().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun isIviraAvailable(context: Context): Boolean {
        return try {
            IviraIntegrationManager(context).isIviraReady()
        } catch (e: Exception) {
            false
        }
    }

    fun getIviraStatus(context: Context): String {
        return try {
            IviraIntegrationManager(context).getTokensStatus()
        } catch (e: Exception) {
            "Error"
        }
    }
}
