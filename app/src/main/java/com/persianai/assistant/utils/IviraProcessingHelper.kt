package com.persianai.assistant.utils

import android.content.Context
import android.util.Log
import com.persianai.assistant.models.ChatMessage
import com.persianai.assistant.integration.IviraIntegrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File

object IviraProcessingHelper {

    private const val TAG = "IviraProcessingHelper"
    private val httpClient = OkHttpClient()

    private const val GAPGPT_BASE_URL = "https://api.gapgpt.app/v1"
    private const val GAPGPT_TTS_URL = "$GAPGPT_BASE_URL/audio/speech"

    suspend fun processWithIviraPriority(
        context: Context,
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing message with Ivira priority")
            
            val iviraManager = IviraIntegrationManager(context)
            if (hasValidTokens(context)) {
                var result: String? = null
                var succeeded = false
                iviraManager.sendMessageViaIvira(
                    message = userMessage,
                    onSuccess = { response ->
                        result = response
                        succeeded = true
                    },
                    onError = { error ->
                        Log.w(TAG, "Ivira error: $error")
                        succeeded = false
                    }
                )
                if (succeeded && !result.isNullOrBlank()) {
                    return@withContext result
                }
            }
            
            val fallback = processFallbackLocal(userMessage)
            return@withContext fallback
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            return@withContext processFallbackLocal(userMessage)
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
            Log.d(TAG, "Processing TTS (GAPGPT → Ivira → local)")

            val prefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
            val gapgptKey = prefs.getString("gapgpt_api_key", null)

            // 1) تلاش با GAPGPT gpt-4o-mini-tts
            if (!gapgptKey.isNullOrNullOrEmpty()) {
                try {
                    val audioBytes = requestGapgptTts(
                        apiKey = gapgptKey,
                        text = text
                    )
                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                        Log.d(TAG, "✅ TTS via GAPGPT gpt-4o-mini-tts")
                        onResult(audioBytes)
                        return@withContext audioBytes
                    } else {
                        Log.w(TAG, "GAPGPT TTS returned empty audio")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GAPGPT TTS failed: ${e.message}", e)
                }
            }

            // 2) تلاش با Ivira (منطق قبلی)
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
                    Log.d(TAG, "✅ TTS via Ivira")
                    return@withContext result
                }
            }

            // 3) اگر اینجا رسیدیم یعنی هیچ‌کدام موفق نشدند
            onError("TTS not available (GAPGPT/Ivira)")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error in processTTSWithIviraPriority", e)
            onError(e.message ?: "Error")
            return@withContext null
        }
    }

    private fun processFallbackLocal(userMessage: String): String {
        return when {
            userMessage.contains("سلام") -> "سلام!"
            else -> "متأسفانه نمی‌تونم الان پاسخ بدم"
        }
    }

    private fun requestGapgptTts(
        apiKey: String,
        text: String
    ): ByteArray? {
        // بدنه JSON مطابق OpenAI /audio/speech
        val json = JSONObject().apply {
            put("model", "gpt-4o-mini-tts")
            put("input", text)
            // می‌توانی اگر GAPGPT از voice پشتیبانی می‌کند، اضافه کنی:
            // put("voice", "alloy")
        }

        val mediaType = "application/json".toMediaType()
        val body = RequestBody.create(mediaType, json.toString())

        val request = Request.Builder()
            .url(GAPGPT_TTS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "audio/mpeg")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return response.body?.bytes()
            } else {
                Log.w(
                    TAG,
                    "GAPGPT TTS HTTP error: code=${response.code}, msg=${response.message}"
                )
            }
        }
        return null
    }

    private fun String?.isNullOrNullOrEmpty(): Boolean = this == null || this.isEmpty()

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
