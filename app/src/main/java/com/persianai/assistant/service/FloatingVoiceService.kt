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
 * - هشدارهای صوتی آفلاین
 */
class FloatingVoiceService : Service() {
    
    private lateinit var notificationManager: NotificationManager
    private var isVoiceAlertsEnabled = true
    private var isSpeedLimitEnabled = true
    
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
        const val CHANNEL_ID = "floating_voice_channel"
        const val NOTIFICATION_ID = 100
        const val ACTION_START = "START_FLOATING_VOICE"
        const val ACTION_STOP = "STOP_FLOATING_VOICE"
        const val ACTION_TOGGLE_VOICE = "TOGGLE_VOICE_ALERTS"
        const val ACTION_TOGGLE_SPEED = "TOGGLE_SPEED_LIMIT"
        const val ACTION_TEST = "TEST_VOICE"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("FloatingVoice", "🎤 Service created")
        
        createNotificationChannel()
        initTTS()
        initLocationTracking()
        showNavigationNotification()
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
            updateNotification()
        }
        
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
    
    private fun showNavigationNotification() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = buildInteractiveNotification()
        notificationManager?.notify(NOTIFICATION_ID, notification)
        
        Log.d("FloatingVoice", "✅ Interactive notification shown")
    }
    
    private fun buildInteractiveNotification(): Notification {
        // PendingIntents for buttons
        val voicePI = PendingIntent.getService(this, 1,
            Intent(this, FloatingVoiceService::class.java).apply { action = ACTION_TOGGLE_VOICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val speedPI = PendingIntent.getService(this, 2,
            Intent(this, FloatingVoiceService::class.java).apply { action = ACTION_TOGGLE_SPEED },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val testPI = PendingIntent.getService(this, 3,
            Intent(this, FloatingVoiceService::class.java).apply { action = ACTION_TEST },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val stopPI = PendingIntent.getService(this, 4,
            Intent(this, FloatingVoiceService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        
        val status = if (isNavigating) "🚗 ${currentSpeed.toInt()} km/h" else "آماده"
        val voiceTxt = if (isVoiceAlertsEnabled) "هشدار ON" else "هشدار OFF"
        val speedTxt = if (isSpeedLimitEnabled) "سرعت ON" else "سرعت OFF"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🧭 دستیار مسیریابی فارسی")
            .setContentText("$status • هشدارهای فارسی فعال")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_lock_silent_mode_off, voiceTxt, voicePI)
            .addAction(android.R.drawable.ic_menu_sort_by_size, speedTxt, speedPI)
            .addAction(android.R.drawable.ic_btn_speak_now, "تست", testPI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "بستن", stopPI)
            .build()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildInteractiveNotification())
                speak("شروع به سفر کنید. دستیار فارسی فعال است")
            }
            ACTION_TOGGLE_VOICE -> {
                isVoiceAlertsEnabled = !isVoiceAlertsEnabled
                updateNotification()
                if (isVoiceAlertsEnabled) speak("هشدار فعال")
            }
            ACTION_TOGGLE_SPEED -> {
                isSpeedLimitEnabled = !isSpeedLimitEnabled
                updateNotification()
            }
            ACTION_TEST -> speak("تست صدا. دستیار فارسی فعال است")
            ACTION_STOP -> stopFloatingVoice()
        }
        return START_STICKY
    }
    
    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildInteractiveNotification())
    }
    
    private fun stopFloatingVoice() {
        try {
            speak("دستیار مسیریابی متوقف شد")
            notificationManager?.cancel(NOTIFICATION_ID)
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }
            tts?.shutdown()
            stopForeground(true)
            Log.d("FloatingVoice", "🛑 Navigation service stopped")
        } catch (e: Exception) {
            Log.e("FloatingVoice", "Error stopping", e)
        }
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
