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
import com.persianai.assistant.utils.PreferencesManager
import com.persianai.assistant.ai.AdvancedPersianAssistant
import com.persianai.assistant.core.AIIntentController
import com.persianai.assistant.core.AIIntentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VoiceCommandService - Fixed
 * 
 * ✓ Uses SimplifiedSTTEngine for better fallback
 * ✓ Better error messages
 * ✓ Proper voice recording with VAD
 * ✓ Integrates with AIIntentController
 */
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
        val controller = AIIntentController(this)

        try {
            // Step 1: Check permissions
            if (!engine.hasRequiredPermissions()) {
                notifyUpdate(
                    "❌ مجوز لازم است",
                    "برای اجرای فرمان صوتی، مجوز میکروفن را به برنامه بدهید."
                )
                Log.e(tag, "Missing RECORD_AUDIO permission")
                return
            }

            // Step 2: Record audio with VAD
            val title = if (mode == MODE_REMINDER) "🎤 ضبط یادآوری..." else "🎤 ضبط فرمان..."
            notifyUpdate(title, hint.orEmpty())
            
            val recording = recordWithVad(engine)
            if (recording == null) {
                notifyUpdate(
                    "⚠️ صدای تشخیص داده نشد",
                    "لطفاً دوباره سعی کنید و واضح‌تر صحبت کنید."
                )
                Log.w(tag, "No speech detected (VAD threshold not exceeded)")
                return
            }

            Log.d(tag, "✅ Recording completed: ${recording.file.absolutePath}")

            // Step 3: Transcribe audio
            notifyUpdate("📝 تبدیل گفتار به متن...", "لطفاً صبر کنید...")
            
            val sttResult = SimplifiedSTTEngine.transcribe(this, recording.file)
            val transcribedText = sttResult.getOrNull()?.trim().orEmpty()

            // Cleanup audio file
            try { recording.file.delete() } catch (_: Exception) {}

            if (transcribedText.isBlank()) {
                notifyUpdate(
                    "⚠️ متن تشخیص داده نشد",
                    "سرویس STT دستیابی ندارد یا صدا واضح نبود."
                )
                Log.w(tag, "STT result was empty/blank")
                return
            }

            Log.d(tag, "✅ STT Result: $transcribedText")

            // Step 4: Normalize text based on mode
            val normalizedText = if (mode == MODE_REMINDER) {
                val t = transcribedText.trim()
                val lower = t.lowercase()
                val looksLikeReminder =
                    lower.contains("یادم بنداز") ||
                    lower.contains("یادآوری") ||
                    lower.contains("یادآور") ||
                    lower.contains("آلارم") ||
                    lower.contains("هشدار")

                if (looksLikeReminder) t else "یادم بنداز $t"
            } else {
                transcribedText.trim()
            }

            notifyUpdate("✅ فرمان دریافت شد", normalizedText)

            // Step 5: Process as Intent
            val resp = try {
                val intent = controller.detectIntentFromText(normalizedText, mode)
                val result = controller.handle(
                    AIIntentRequest(
                        intent = intent,
                        source = AIIntentRequest.Source.NOTIFICATION,
                        workingModeName = PreferencesManager(this).getWorkingMode().name
                    )
                )
                Log.d(tag, "✅ Intent processed: ${intent.name} -> ${result.text.take(50)}")
                result.text
            } catch (e: Exception) {
                Log.e(tag, "Error processing intent", e)
                // Fallback to old behavior
                val assistant = AdvancedPersianAssistant(this)
                try {
                    assistant.processRequest(normalizedText).text
                } catch (ex: Exception) {
                    "❌ خطا در اجرای فرمان: ${ex.message}"
                }
            }

            notifyUpdate("🤖 نتیجه", resp)
            delay(2500)
            
        } catch (e: Exception) {
            Log.e(tag, "runOneShotCommand failed", e)
            notifyUpdate("❌ خطا", e.message ?: "خطای نامشخص")
        }
    }

    /**
     * Record audio with Voice Activity Detection
     */
    private suspend fun recordWithVad(
        engine: UnifiedVoiceEngine
    ): com.persianai.assistant.services.RecordingResult? {
        return try {
            val start = engine.startRecording()
            if (start.isFailure) {
                Log.e(tag, "Failed to start recording: ${start.exceptionOrNull()?.message}")
                return null
            }

            val startTime = System.currentTimeMillis()
            var hasSpeech = false
            var lastSpeechTime = 0L
            val maxTotalMs = 10_000L      // 10 sec max
            val maxWaitForSpeechMs = 3_500L  // 3.5 sec to start talking
            val silenceStopMs = 1_200L    // 1.2 sec silence = stop
            val threshold = 800            // amplitude threshold

            Log.d(tag, "Recording with VAD: timeout=$maxTotalMs, silence=$silenceStopMs")

            while (engine.isRecordingInProgress()) {
                val now = System.currentTimeMillis()
                val amp = engine.getCurrentAmplitude()
                
                if (amp > threshold) {
                    hasSpeech = true
                    lastSpeechTime = now
                }

                val total = now - startTime
                
                // Stop if no speech detected in time
                if (!hasSpeech && total > maxWaitForSpeechMs) {
                    Log.d(tag, "VAD: Timeout waiting for speech")
                    break
                }
                
                // Stop if silence detected
                if (hasSpeech && (now - lastSpeechTime) > silenceStopMs) {
                    Log.d(tag, "VAD: Silence detected, stopping")
                    break
                }
                
                // Stop if max duration exceeded
                if (total > maxTotalMs) {
                    Log.d(tag, "VAD: Max duration exceeded")
                    break
                }

                delay(100) // Check every 100ms
            }

            val stop = engine.stopRecording()
            val result = stop.getOrNull()
            
            if (result != null) {
                Log.d(tag, "Recording stopped successfully: ${result.duration}ms")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(tag, "Error during VAD recording", e)
            try { engine.cancelRecording() } catch (_: Exception) {}
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { (scope.coroutineContext[Job] as? Job)?.cancel() } catch (_: Exception) {}
    }
}