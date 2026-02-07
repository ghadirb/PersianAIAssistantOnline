package com.persianai.assistant.integration

import android.content.Context
import android.util.Log
import com.persianai.assistant.api.IviraAPIClient
import com.persianai.assistant.utils.IviraTokenManager
import com.persianai.assistant.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IviraIntegrationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "IviraIntegration"
    }
    
    private val tokenManager = IviraTokenManager(context)
    private val apiClient = IviraAPIClient(context)
    private val prefsManager = PreferencesManager(context)
    
    suspend fun initializeIviraTokens(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing Ivira tokens...")
            val storedTokens = getIviraTokens()
            if (storedTokens.isNotEmpty()) {
                Log.d(TAG, "Found tokens")
                return@withContext true
            }
            Log.w(TAG, "No Ivira tokens found")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Ivira tokens", e)
            false
        }
    }

    fun getIviraTokens(): Map<String, String> {
        return try {
            prefsManager.getIviraTokens()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Ivira tokens")
            emptyMap()
        }
    }

    fun setIviraToken(key: String, value: String) {
        try {
            prefsManager.setIviraToken(key, value)
            Log.d(TAG, "Token set")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Ivira token", e)
        }
    }

    fun isIviraEnabled(): Boolean {
        return try {
            prefsManager.isIviraEnabled()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reloadTokensManually(): Result<Map<String, String>> {
        return tokenManager.fetchEncryptedTokensFromUrl()
    }

    fun getTokenStatusForSettings(): String = getTokensStatus()
    
    fun getTokensStatus(): String {
        val hasTokens = tokenManager.hasTokens()
        val available = apiClient.getAvailableTokensInfo()
        val activeCount = available.count { it.value }
        
        return when {
            hasTokens && activeCount > 0 -> "SUCCESS"
            hasTokens -> "PARTIAL"
            else -> "UNAVAILABLE"
        }
    }
    
    suspend fun sendMessageViaIvira(
        message: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("Token missing")
                return@withContext false
            }
            
            var resultReceived = false
            apiClient.sendMessage(
                message = message,
                onResponse = { response ->
                    onSuccess(response)
                    resultReceived = true
                },
                onError = { error ->
                    onError(error)
                    resultReceived = false
                }
            )
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            onError("Error")
            false
        }
    }
    
    suspend fun textToSpeechViaIvira(
        text: String,
        onSuccess: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("Token missing")
                return@withContext false
            }
            
            var resultReceived = false
            apiClient.textToSpeech(
                text = text,
                onSuccess = { audioBytes ->
                    onSuccess(audioBytes)
                    resultReceived = true
                },
                onError = { error ->
                    onError(error)
                    resultReceived = false
                }
            )
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            onError("Error")
            false
        }
    }
    
    suspend fun speechToTextViaIvira(
        audioFile: java.io.File,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ): Boolean = withContext(Dispatchers.Default) {
        try {
            if (!tokenManager.hasTokens()) {
                onError("Token missing")
                return@withContext false
            }
            
            var resultReceived = false
            apiClient.speechToText(
                audioFile = audioFile,
                onSuccess = { text ->
                    onSuccess(text)
                    resultReceived = true
                },
                onError = { error ->
                    onError(error)
                    resultReceived = false
                }
            )
            resultReceived
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
            onError("Error")
            false
        }
    }
    
    fun isIviraReady(): Boolean {
        return tokenManager.hasTokens() && apiClient.hasTokens()
    }
    
    fun getAvailableTokensInfo(): Map<String, Boolean> {
        return apiClient.getAvailableTokensInfo()
    }
    
    fun shutdown() {
        try {
            Log.d(TAG, "Shutting down")
        } catch (e: Exception) {
            Log.e(TAG, "Error", e)
        }
    }
}