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

/**
 * سرویس پس‌زمینه برای اجرای دستیار هوش مصنوعی
 */
class AIAssistantService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ai_assistant_service"
        private const val CHANNEL_NAME = "سرویس دستیار هوش مصنوعی"
    }

    // جلوگیری از crash: یک بار startForeground کافی است
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        ensureForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ok = ensureForeground()
        return if (ok) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("دستیار هوش مصنوعی فعال است")
            .setContentText("برای مخفی ماندن در پس‌زمینه اجرا می‌شود")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setSilent(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun ensureForeground(): Boolean {
        if (startedForeground) return true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            return try {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, createNotification())
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
