package com.persianai.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.persianai.assistant.ai.PuterBridge
import com.persianai.assistant.services.UnifiedVoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AIAssistantApplication : Application() {

    companion object {
        const val CHANNEL_ID = "ai_assistant_channel"
        const val CHANNEL_NAME = "دستیار هوش مصنوعی"
        lateinit var instance: AIAssistantApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        PuterBridge.setContext(this)

        // Dev-only: if a host path is provided via env var, copy Haaniye model into app files
        try {
            if (BuildConfig.DEBUG) {
                val hostPath = System.getenv("HAANIYE_HOST_PATH")
                    ?: "${projectDirPlaceholder()}" // placeholder if needed
                if (!hostPath.isNullOrBlank()) {
                    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
                    scope.launch {
                        try {
                            UnifiedVoiceEngine(this@AIAssistantApplication).copyHaaniyeFromHost(hostPath)
                        } catch (_: Exception) { /* ignore dev helper failures */ }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // projectDir is not available at runtime; this helper returns an impossible path placeholder
    private fun projectDirPlaceholder(): String = "C:/github/PersianAIAssistantOnline/app/build/intermediates/assets/debug/tts/haaniye"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "کانال اعلان‌های دستیار هوش مصنوعی"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
