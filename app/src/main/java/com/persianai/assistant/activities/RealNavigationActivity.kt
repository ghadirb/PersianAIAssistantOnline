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
        webView = findViewById(R.id.navigationMapWebView)
        tvNextTurn = findViewById(R.id.tvNextTurn)
        tvDistance = findViewById(R.id.tvDistance)
        tvTime = findViewById(R.id.tvTime)
        tvSpeed = findViewById(R.id.tvSpeed)
        
        // نمایش اطلاعات مسیر
        tvDistance.text = "مسافت: ${String.format("%.1f", routeDistance)} کم"
        tvTime.text = "زمان: $routeDuration دقیقه"
        tvNextTurn.text = "🚗 شروع به حرکت کنید"
        
        // تنظیم WebView
        setupWebView()
        
        // مقداردهی
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        voiceAlerts = PersianVoiceAlerts(this)
        
        // شروع ناوبری با هشدارهای صوتی فارسی - مثل نشان
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voiceAlerts.speak("شروع به حرکت کنید")
            tvNextTurn.text = "🚗 در حال حرکت به سمت مقصد"
        }, 1500)
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            voiceAlerts.speak("مسافت ${String.format("%.0f", routeDistance)} کیلومتر، زمان تقریبی $routeDuration دقیقه")
        }, 4500)
    }
    
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        
        webView.webViewClient = android.webkit.WebViewClient()
        
        // بارگذاری نقشه نشان
        webView.loadUrl("file:///android_asset/neshan_map.html")
        
        // بعد از بارگذاری، نمایش مسیر
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                // نمایش مسیر روی نقشه
                val polyline = intent.getStringExtra("POLYLINE") ?: ""
                if (polyline.isNotEmpty()) {
                    webView.evaluateJavascript(
                        "drawClickableRoute(0, '${polyline.replace("'", "\\'")}', '#4285F4');",
                        null
                    )
                }
                
                // مرکز نقشه روی مسیر
                webView.evaluateJavascript("map.setView([$destLat, $destLng], 13);", null)
            }
        }
    }
    
    override fun onBackPressed() {
        // دیالوگ خروج مثل نشان
        val options = arrayOf(
            "❌ پایان مسیریابی",
            "🔙 خروج (ادامه مسیریابی)"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("مسیریابی")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // پایان مسیریابی - قطع کامل
                        voiceAlerts.speak("مسیریابی پایان یافت")
                        finish()
                    }
                    1 -> {
                        // خروج ولی ادامه مسیریابی در پس‌زمینه
                        voiceAlerts.speak("مسیریابی در پس زمینه ادامه دارد")
                        // TODO: شروع service برای ادامه navigation
                        finish()
                    }
                }
            }
            .setNegativeButton("بازگشت", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        voiceAlerts.shutdown()
    }
}
