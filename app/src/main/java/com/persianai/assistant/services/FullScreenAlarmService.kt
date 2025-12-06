package com.persianai.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R
import com.persianai.assistant.activities.FullScreenAlarmActivity

/**
 * Foreground Service برای نمایش FullScreenAlarm
 * این Service تمام وقت اجرا می‌شود و یادآوری‌ها را بررسی می‌کند
 */
class FullScreenAlarmService : Service() {
    
    private val TAG = "FullScreenAlarmService"
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "fullscreen_alarm_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📲 onStartCommand - action: ${intent?.action}")
        
        if (intent == null) {
            Log.e(TAG, "❌ Intent is null")
            return START_STICKY
        }
        
        val action = intent.action ?: return START_STICKY
        
        when (action) {
            "SHOW_FULL_SCREEN_ALARM" -> {
                handleShowFullScreenAlarm(intent)
            }
            "START_MONITORING" -> {
                startMonitoring()
            }
            else -> {
                Log.d(TAG, "Unknown action: $action")
            }
        }
        
        return START_STICKY
    }
    
    /**
     * شروع Foreground Notification
     */
    private fun startMonitoring() {
        try {
            val notification = createMonitoringNotification()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            Log.d(TAG, "✅ Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting foreground service", e)
        }
    }
    
    /**
     * نمایش Full Screen Alarm
     */
    private fun handleShowFullScreenAlarm(intent: Intent) {
        try {
            // WakeLock بگیری
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "PersianAssistant::FullScreenAlarm"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 دقیقه
            }
            Log.d(TAG, "⚡ WakeLock acquired")
            
            val title = intent.getStringExtra("title") ?: "⏰ یادآوری"
            val description = intent.getStringExtra("description") ?: ""
            val reminderId = intent.getStringExtra("smart_reminder_id") ?: "unknown"
            
            Log.d(TAG, "📝 Showing alarm: $title | ID: $reminderId")
            
            // نمایش Activity با delay کوچک
            Handler(Looper.getMainLooper()).postDelayed({
                showFullScreenActivity(title, description, reminderId)
            }, 300)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in handleShowFullScreenAlarm", e)
        }
    }
    
    /**
     * شروع Activity تمام صفحه
     */
    private fun showFullScreenActivity(title: String, description: String, reminderId: String) {
        try {
            val alarmIntent = Intent(this, FullScreenAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra("title", title)
                putExtra("description", description)
                putExtra("smart_reminder_id", reminderId)
            }
            
            Log.d(TAG, "🎬 Starting FullScreenAlarmActivity - $title")
            startActivity(alarmIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting activity", e)
        }
    }
    
    /**
     * ساخت Notification برای Monitoring
     */
    private fun createMonitoringNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("یادآوری فعال است")
            .setContentText("سیستم یادآوری در حال نظارت است...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
    
    /**
     * ایجاد Notification Channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Full Screen Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Service برای نمایش یادآوری‌های تمام صفحه"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Service destroyed")
        
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "⚡ WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakelock", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}