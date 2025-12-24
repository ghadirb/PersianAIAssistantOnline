package com.persianai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.MainActivity
import com.persianai.assistant.activities.RemindersActivity
import com.persianai.assistant.activities.VoiceCallActivity
import com.persianai.assistant.utils.PreferencesManager

/**
 * سرویس پس‌زمینه برای اجرای دستیار هوش مصنوعی
 */
class AIAssistantService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ai_assistant_service"
        private const val CHANNEL_NAME = "سرویس دستیار هوش مصنوعی"

        const val EXTRA_START_VOICE = "extra_start_voice"
        const val EXTRA_QUICK_REMINDER = "extra_quick_reminder"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
    }

    // جلوگیری از crash: یک بار startForeground کافی است
    private var startedForeground = false

    private var lastNotifyUpdateMs: Long = 0L
    private var lastNotifyText: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureForeground(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = ensureForeground(intent)
        return if (ok) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun defaultStatusText(prefs: PreferencesManager): String {
        return when (prefs.getWorkingMode()) {
            PreferencesManager.WorkingMode.OFFLINE -> "آفلاین"
            PreferencesManager.WorkingMode.HYBRID -> "ترکیبی"
            PreferencesManager.WorkingMode.ONLINE -> "آنلاین"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN  // کمینه برای پنهان بودن
            ).apply {
                description = ""
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusTextOverride: String? = null): Notification {
        val prefs = PreferencesManager(this)
        val status = statusTextOverride?.takeIf { it.isNotBlank() } ?: defaultStatusText(prefs)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("دستیار آماده است")
            .setContentText("وضعیت: $status")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setCategory(Notification.CATEGORY_SERVICE)

        if (prefs.isPersistentNotificationActionsEnabled()) {
            val voiceIntent = Intent(this, VoiceCommandService::class.java).apply {
                action = VoiceCommandService.ACTION_RECORD_COMMAND
            }
            val voicePendingIntent = PendingIntent.getService(
                this,
                1,
                voiceIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_btn_speak_now, "ضبط فرمان", voicePendingIntent)

            val callIntent = Intent(this, VoiceCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val callPendingIntent = PendingIntent.getActivity(
                this,
                3,
                callIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.sym_action_call, "تماس", callPendingIntent)

            val reminderIntent = Intent(this, RemindersActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_QUICK_REMINDER, true)
            }
            val reminderPendingIntent = PendingIntent.getActivity(
                this,
                2,
                reminderIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_my_calendar, "یادآوری سریع", reminderPendingIntent)
        }

        return builder.build()
    }

    private fun maybeNotify(statusTextOverride: String? = null) {
        val now = System.currentTimeMillis()
        val text = statusTextOverride?.takeIf { it.isNotBlank() }
        val shouldThrottle = (now - lastNotifyUpdateMs) < 2000L
        if (shouldThrottle && text == lastNotifyText) return

        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createNotification(text))
            lastNotifyUpdateMs = now
            lastNotifyText = text
        } catch (_: Exception) {
        }
    }

    private fun ensureForeground(intent: Intent?): Boolean {
        val prefs = PreferencesManager(this)
        val shouldRun = prefs.isServiceEnabled() && prefs.isPersistentStatusNotificationEnabled()
        if (!shouldRun) {
            stopSelf()
            return false
        }

        val statusOverride = intent?.getStringExtra(EXTRA_STATUS_TEXT)

        if (startedForeground) {
            maybeNotify(statusOverride)
            return true
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return try {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification(statusOverride))
                startedForeground = true
                true
            } catch (e: Exception) {
                android.util.Log.e("AIAssistantService", "startForeground failed: ${e.message}", e)
                stopSelf()
                false
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
