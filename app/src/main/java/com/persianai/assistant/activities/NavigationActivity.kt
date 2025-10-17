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
import com.persianai.assistant.navigation.SavedLocationsManager
import com.google.android.gms.maps.model.LatLng

class NavigationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityNavigationBinding
    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var savedLocationsManager: SavedLocationsManager
    private var currentLocation: Location? = null
    private var selectedDestination: LatLng? = null
    
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
        savedLocationsManager = SavedLocationsManager(this)
        
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
            showSavedLocations()
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
            currentLocation?.let { loc ->
                showSaveLocationDialog(LatLng(loc.latitude, loc.longitude))
            } ?: Toast.makeText(this, "⚠️ در حال دریافت موقعیت...", Toast.LENGTH_SHORT).show()
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
                showSaveLocationDialog(LatLng(lat, lng))
            }
        }
    }
    
    private fun showSavedLocations() {
        val locations = savedLocationsManager.getAllLocations()
        if (locations.isEmpty()) {
            Toast.makeText(this, "💾 هیچ مکانی ذخیره نشده", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = locations.map { "${getCategoryEmoji(it.category)} ${it.name}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("💾 مکان‌های ذخیره شده")
            .setItems(items) { _, which ->
                val location = locations[which]
                selectedDestination = LatLng(location.latitude, location.longitude)
                webView.evaluateJavascript("addMarker(${location.latitude}, ${location.longitude}, '${location.name}');", null)
                Toast.makeText(this, "📍 ${location.name}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("مدیریت") { _, _ ->
                showManageLocationsDialog()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showManageLocationsDialog() {
        val locations = savedLocationsManager.getAllLocations()
        val items = locations.map { "${getCategoryEmoji(it.category)} ${it.name}" }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ مدیریت مکان‌ها")
            .setItems(items) { _, which ->
                val location = locations[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle("حذف ${location.name}؟")
                    .setMessage("آیا مطمئن هستید؟")
                    .setPositiveButton("حذف") { _, _ ->
                        savedLocationsManager.deleteLocation(location.id)
                        Toast.makeText(this, "✅ حذف شد", Toast.LENGTH_SHORT).show()
                        showManageLocationsDialog()
                    }
                    .setNegativeButton("لغو", null)
                    .show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showSaveLocationDialog(latLng: LatLng) {
        val input = EditText(this)
        input.hint = "نام مکان"
        
        val categories = arrayOf("🏠 خانه", "💼 محل کار", "⭐ علاقه‌مندی")
        var selectedCategory = "favorite"
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⭐ ذخیره مکان")
            .setMessage("📍 ${String.format("%.6f", latLng.latitude)}, ${String.format("%.6f", latLng.longitude)}")
            .setView(input)
            .setSingleChoiceItems(categories, 2) { _, which ->
                selectedCategory = when (which) {
                    0 -> "home"
                    1 -> "work"
                    else -> "favorite"
                }
            }
            .setPositiveButton("ذخیره") { _, _ ->
                val name = input.text.toString().ifEmpty { "مکان ${System.currentTimeMillis()}" }
                val address = "${String.format("%.6f", latLng.latitude)}, ${String.format("%.6f", latLng.longitude)}"
                
                if (savedLocationsManager.saveLocation(name, address, latLng, selectedCategory)) {
                    Toast.makeText(this, "✅ ذخیره شد: $name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ خطا در ذخیره", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun getCategoryEmoji(category: String): String {
        return when (category) {
            "home" -> "🏠"
            "work" -> "💼"
            else -> "⭐"
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
