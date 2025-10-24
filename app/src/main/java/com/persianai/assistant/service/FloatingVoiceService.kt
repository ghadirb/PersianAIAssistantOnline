package com.persianai.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
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
    
    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var currentSpeed: Float = 0f
    private var isNavigating = false
    
    // TTS Engine (Hybrid: Google + Offline)
    private var tts: com.persianai.assistant.tts.HybridTTS? = null
    private var isTTSReady = false
    
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
        initTTS()
        initLocationTracking()
        showFloatingButton()
    }
    
    private fun initTTS() {
        try {
            tts = com.persianai.assistant.tts.HybridTTS(this)
            // صبر کمی برای آماده شدن
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isTTSReady = tts?.isReady == true
                if (isTTSReady) {
                    Log.d("FloatingVoice", "✅ Hybrid TTS Ready")
                    speak("دستیار صوتی فعال شد")
                }
            }, 1000)
        } catch (e: Exception) {
            Log.e("FloatingVoice", "❌ TTS init failed", e)
        }
    }
    
    private fun initLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationUpdate(location)
                }
            }
        }
        
        startLocationUpdates()
    }
    
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("FloatingVoice", "❌ No location permission")
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // 5 seconds
        ).build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        
        Log.d("FloatingVoice", "📍 Location tracking started")
    }
    
    private fun onLocationUpdate(location: Location) {
        lastLocation = location
        currentSpeed = location.speed * 3.6f // m/s to km/h
        
        // تشخیص مسیریابی: اگه سرعت > 5 km/h
        val wasNavigating = isNavigating
        isNavigating = currentSpeed > 5f
        
        // اگه مسیریابی شروع شد
        if (isNavigating && !wasNavigating) {
            onNavigationStarted()
        }
        
        // اگه مسیریابی تموم شد
        if (!isNavigating && wasNavigating) {
            onNavigationStopped()
        }
        
        // Update UI
        updateFloatingButton()
        
        Log.d("FloatingVoice", "📍 Speed: ${currentSpeed.toInt()} km/h, Navigating: $isNavigating")
    }
    
    private fun onNavigationStarted() {
        Log.d("FloatingVoice", "🚗 Navigation started!")
        speak("مسیریابی شروع شد. با احتیاط رانندگی کنید")
    }
    
    private fun onNavigationStopped() {
        Log.d("FloatingVoice", "⏹️ Navigation stopped")
    }
    
    private fun speak(text: String) {
        if (isTTSReady) {
            tts?.speak(text)
            Log.d("FloatingVoice", "🔊 Speaking: $text")
        } else {
            Log.w("FloatingVoice", "⚠️ TTS not ready")
        }
    }
    
    private fun updateFloatingButton() {
        val statusText = floatingView?.findViewById<TextView>(R.id.statusText)
        statusText?.text = if (isNavigating) {
            "${currentSpeed.toInt()} km/h"
        } else {
            "آماده"
        }
        statusText?.visibility = if (isNavigating) View.VISIBLE else View.GONE
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
        // Stop location tracking
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        
        // Stop TTS
        tts?.shutdown()
        
        // Remove floating view
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
