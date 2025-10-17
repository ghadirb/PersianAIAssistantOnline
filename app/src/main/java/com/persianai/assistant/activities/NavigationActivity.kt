package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.databinding.ActivityNavigationBinding

class NavigationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityNavigationBinding
    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                currentLocation = loc
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
                binding.currentSpeedText.text = "${(loc.speed * 3.6f).toInt()} km/h"
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🗺️ مسیریاب"
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        webView = binding.mapWebView
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(MapInterface(), "Android")
        webView.loadUrl("file:///android_asset/neshan_map.html")
        
        checkPermissions()
        
        setupButtons()
    }
    
    private fun setupButtons() {
        binding.myLocationButton.setOnClickListener {
            currentLocation?.let { loc ->
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
            }
        }
        
        binding.searchDestinationButton.setOnClickListener {
            val input = EditText(this)
            input.hint = "نام مقصد"
            MaterialAlertDialogBuilder(this)
                .setTitle("🔍 جستجوی مقصد")
                .setView(input)
                .setPositiveButton("جستجو") { _, _ ->
                    Toast.makeText(this, "جستجو: ${input.text}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("لغو", null)
                .show()
        }
        
        binding.savedLocationsButton.setOnClickListener {
            Toast.makeText(this, "💾 مکان‌های ذخیره شده", Toast.LENGTH_SHORT).show()
        }
        
        binding.poiButton.setOnClickListener {
            val items = arrayOf("⛽ پمپ بنزین", "🍽️ رستوران", "🏥 بیمارستان", "🏧 ATM")
            MaterialAlertDialogBuilder(this)
                .setTitle("📏 مکان‌های نزدیک")
                .setItems(items) { _, which ->
                    Toast.makeText(this, "انتخاب: ${items[which]}", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
        
        binding.saveCurrentLocationButton.setOnClickListener {
            currentLocation?.let {
                Toast.makeText(this, "⭐ مکان ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.startNavigationButton.setOnClickListener {
            binding.speedCard.visibility = View.VISIBLE
            binding.routeInfoCard.visibility = View.VISIBLE
            Toast.makeText(this, "▶️ مسیریابی شروع شد", Toast.LENGTH_SHORT).show()
        }
        
        binding.stopNavigationButton.setOnClickListener {
            binding.speedCard.visibility = View.GONE
            binding.routeInfoCard.visibility = View.GONE
            Toast.makeText(this, "⏹️ مسیریابی متوقف شد", Toast.LENGTH_SHORT).show()
        }
        
        binding.addWaypointButton.setOnClickListener {
            Toast.makeText(this, "📍 مقصد میانی", Toast.LENGTH_SHORT).show()
        }
        
        binding.aiChatFab.setOnClickListener {
            Toast.makeText(this, "💬 چت AI", Toast.LENGTH_SHORT).show()
        }
    }
    
    inner class MapInterface {
        @JavascriptInterface
        fun onMapClick(lat: Double, lng: Double) {
            runOnUiThread {
                Toast.makeText(this@NavigationActivity, "📍 $lat, $lng", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            startLocationUpdates()
        }
    }
    
    private fun startLocationUpdates() {
        val request = LocationRequest.create().apply {
            interval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
