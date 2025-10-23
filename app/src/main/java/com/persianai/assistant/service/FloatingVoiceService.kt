package com.persianai.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.persianai.assistant.R

/**
 * سرویس شناور برای هشدارهای صوتی فارسی در Google Maps
 * - شناور و سبک
 * - در پس‌زمینه اجرا
 * - هشدارهای صوتی آفلاین
 */
class FloatingVoiceService : Service() {
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var isFloatingViewActive = false
    
    companion object {
        const val CHANNEL_ID = "FloatingVoiceChannel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "START_FLOATING_VOICE"
        const val ACTION_STOP = "STOP_FLOATING_VOICE"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("FloatingVoice", "🎤 Service created")
        
        createNotificationChannel()
        showFloatingButton()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d("FloatingVoice", "✅ Floating voice started")
            }
            ACTION_STOP -> {
                stopFloatingVoice()
            }
        }
        return START_STICKY
    }
    
    private fun showFloatingButton() {
        if (isFloatingViewActive) return
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Inflate floating view
        floatingView = LayoutInflater.from(this).inflate(
            R.layout.floating_voice_button,
            null
        )
        
        // Параметры для floating window
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100
        
        // Add view to window
        windowManager.addView(floatingView, params)
        isFloatingViewActive = true
        
        // Setup drag and click
        setupFloatingViewActions(params)
        
        Log.d("FloatingVoice", "✅ Floating button shown")
    }
    
    private fun setupFloatingViewActions(params: WindowManager.LayoutParams) {
        val button = floatingView?.findViewById<ImageView>(R.id.floatingButton)
        val statusText = floatingView?.findViewById<TextView>(R.id.statusText)
        
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        button?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    
                    // اگه حرکت کوچک بود، کلیک محسوب میشه
                    if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                        onFloatingButtonClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun onFloatingButtonClick() {
        Log.d("FloatingVoice", "🔘 Floating button clicked")
        // TODO: تست صدا
        // voiceEngine.speak("هشدار صوتی فعال است")
    }
    
    private fun stopFloatingVoice() {
        if (floatingView != null && isFloatingViewActive) {
            windowManager.removeView(floatingView)
            floatingView = null
            isFloatingViewActive = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d("FloatingVoice", "⏹️ Service stopped")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "هشدارهای صوتی مسیریابی",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "هشدارهای صوتی فارسی در Google Maps"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, FloatingVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎤 هشدار صوتی فعال")
            .setContentText("در حال ارائه هشدارهای مسیریابی فارسی")
            .setSmallIcon(R.drawable.ic_navigation_voice)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "بستن", stopPendingIntent)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopFloatingVoice()
        Log.d("FloatingVoice", "🗑️ Service destroyed")
    }
}
