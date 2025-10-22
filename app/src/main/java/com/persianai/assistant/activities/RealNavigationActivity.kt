package com.persianai.assistant.activities

import android.location.Location
import android.os.Bundle
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.persianai.assistant.R
import com.persianai.assistant.voice.PersianVoiceAlerts

class RealNavigationActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private lateinit var voiceAlerts: PersianVoiceAlerts
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private lateinit var tvNextTurn: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvSpeed: TextView
    
    private var destLat: Double = 0.0
    private var destLng: Double = 0.0
    private var routeDistance: Double = 0.0
    private var routeDuration: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_real_navigation)
        
        // دریافت اطلاعات مسیر
        destLat = intent.getDoubleExtra("DEST_LAT", 0.0)
        destLng = intent.getDoubleExtra("DEST_LNG", 0.0)
        routeDistance = intent.getDoubleExtra("DISTANCE", 0.0)
        routeDuration = intent.getIntExtra("DURATION", 0)
        
        // مقداردهی Views
        tvNextTurn = findViewById(R.id.tvNextTurn)
        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        tvSpeed = findViewById(R.id.tvSpeed)
        
        // نمایش اطلاعات مسیر
        tvDistance.text = "مسافت: ${String.format("%.1f", routeDistance)} کم"
        tvTime.text = "زمان: $routeDuration دقیقه"
        tvNextTurn.text = "🚗 در حال مسیریابی به مقصد..."
        
        // مقداردهی
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        voiceAlerts = PersianVoiceAlerts(this)
        
        // شروع ناوبری با هشدارهای صوتی فارسی - مثل نشان
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voiceAlerts.speak("شروع به حرکت کنید")
        }, 1000)
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voiceAlerts.speak("مسافت ${String.format("%.0f", routeDistance)} کیلومتر، زمان تقریبی $routeDuration دقیقه")
        }, 4000)
        
        // TODO: Load map in WebView
        // TODO: Update location real-time
        // TODO: Voice turn-by-turn guidance
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceAlerts.shutdown()
    }
}
