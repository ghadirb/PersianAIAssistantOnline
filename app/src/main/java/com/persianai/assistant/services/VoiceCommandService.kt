package com.persianai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.ai.AdvancedPersianAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VoiceCommandService : Service() {

    companion object {
        const val ACTION_RECORD_COMMAND = "com.persianai.assistant.action.RECORD_COMMAND"
        const val ACTION_RECORD_REMINDER = "com.persianai.assistant.action.RECORD_REMINDER"

        private const val CHANNEL_ID = "voice_command_service"
        private const val NOTIFICATION_ID = 1210

        const val EXTRA_HINT = "extra_hint"
        const val EXTRA_MODE = "extra_mode"
        const val MODE_GENERAL = "general"
        const val MODE_REMINDER = "reminder"
    }

    private val tag = "VoiceCommandService"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECORD_COMMAND || intent?.action == ACTION_RECORD_REMINDER) {
            if (!started) {
                started = true
                ensureChannel()
                startForeground(NOTIFICATION_ID, buildNotification("🎤 آماده ضبط...", ""))
            }
            scope.launch {
                val mode = intent.getStringExtra(EXTRA_MODE)?.takeIf { it.isNotBlank() }
                    ?: if (intent.action == ACTION_RECORD_REMINDER) MODE_REMINDER else MODE_GENERAL
                runOneShotCommand(intent.getStringExtra(EXTRA_HINT), mode)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        stopSelf()
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Commands",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice command processing"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun notifyUpdate(title: String, text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(title, text))
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.ifBlank { " " })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { " " }))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private suspend fun runOneShotCommand(hint: String?, mode: String) {
        val engine = UnifiedVoiceEngine(this)

        try {
            if (!engine.hasRequiredPermissions()) {
                notifyUpdate("❌ مجوز لازم است", "برای اجرای فرمان صوتی، مجوز میکروفن را به برنامه بدهید.")
                return
            }

            val title = if (mode == MODE_REMINDER) "🎤 ضبط یادآوری..." else "🎤 ضبط فرمان..."
            notifyUpdate(title, hint.orEmpty())
            val recording = recordWithVad(engine)
            if (recording == null) {
                notifyUpdate("⚠️ چیزی شنیده نشد", "دوباره تلاش کنید.")
                return
            }

            notifyUpdate("📝 تبدیل گفتار به متن...", "")
            val analysis = engine.analyzeHybrid(recording.file)
            val text = analysis.getOrNull()?.primaryText?.trim().orEmpty()

            // Clean up audio file immediately
            try { recording.file.delete() } catch (_: Exception) { }

            if (text.isBlank()) {
                notifyUpdate("⚠️ متن تشخیص داده نشد", "دوباره تلاش کنید.")
                return
            }

            val normalizedText = if (mode == MODE_REMINDER) {
                val t = text.trim()
                val lower = t.lowercase()
                val looksLikeReminder =
                    lower.contains("یادم بنداز") ||
                    lower.contains("یادآوری") ||
                    lower.contains("یادآور") ||
                    lower.contains("آلارم") ||
                    lower.contains("هشدار")

                if (looksLikeReminder) t else "یادم بنداز $t"
            } else {
                text.trim()
            }

            notifyUpdate("✅ فرمان دریافت شد", normalizedText)

            // Offline execution (no history saving)
            val assistant = AdvancedPersianAssistant(this)
            val resp = try {
                val result = assistant.processRequest(normalizedText)
                // Some actions should be applied immediately (e.g., reminders) even from service
                when (result.actionType) {
                    AdvancedPersianAssistant.ActionType.ADD_REMINDER,
                    AdvancedPersianAssistant.ActionType.OPEN_REMINDERS -> {
                        // AdvancedPersianAssistant already creates reminders internally; show final text
                        result.text
                    }
                    else -> result.text
                }
            } catch (e: Exception) {
                "❌ خطا در اجرای فرمان: ${e.message}"
            }

            notifyUpdate("🤖 نتیجه", resp)
            delay(2500)
        } catch (e: Exception) {
            Log.e(tag, "runOneShotCommand failed", e)
            notifyUpdate("❌ خطا", e.message ?: "خطای نامشخص")
        }
    }

    private suspend fun recordWithVad(engine: UnifiedVoiceEngine): com.persianai.assistant.services.RecordingResult? {
        return try {
            val start = engine.startRecording()
            if (start.isFailure) return null

            val startTime = System.currentTimeMillis()
            var hasSpeech = false
            var lastSpeechTime = 0L
            val maxTotalMs = 8_000L
            val maxWaitForSpeechMs = 3_500L
            val silenceStopMs = 1_000L
            val threshold = 900

            while (engine.isRecordingInProgress()) {
                val now = System.currentTimeMillis()
                val amp = engine.getCurrentAmplitude()
                if (amp > threshold) {
                    hasSpeech = true
                    lastSpeechTime = now
                }

                val total = now - startTime
                if (!hasSpeech && total > maxWaitForSpeechMs) break
                if (hasSpeech && (now - lastSpeechTime) > silenceStopMs) break
                if (total > maxTotalMs) break

                delay(120)
            }

            val stop = engine.stopRecording()
            stop.getOrNull()
        } catch (e: Exception) {
            try { engine.cancelRecording() } catch (_: Exception) {}
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { (scope.coroutineContext[Job] as? Job)?.cancel() } catch (_: Exception) {}
    }
}
