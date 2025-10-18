package com.persianai.assistant.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import com.persianai.assistant.ml.LocationHistoryManager
import com.persianai.assistant.ml.RoutePredictor
import com.persianai.assistant.ml.RouteLearningSys
import com.persianai.assistant.utils.NeshanSearchAPI
import com.persianai.assistant.ai.ContextualAIAssistant
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NavigationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityNavigationBinding
    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var savedLocationsManager: SavedLocationsManager
    private lateinit var locationHistoryManager: LocationHistoryManager
    private lateinit var routePredictor: RoutePredictor
    private lateinit var routeLearningSys: RouteLearningSys
    private lateinit var searchAPI: NeshanSearchAPI
    private lateinit var aiAssistant: ContextualAIAssistant
    private var currentLocation: Location? = null
    private var selectedDestination: LatLng? = null
    private val routeWaypoints = mutableListOf<LatLng>()
    private var routeStartTime: Long = 0
    private var isTrafficEnabled = false
    private var currentMapLayer = "normal"
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                currentLocation = loc
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
                binding.currentSpeedText.text = "${(loc.speed * 3.6f).toInt()} km/h"
                
                // ثبت مکان برای یادگیری
                locationHistoryManager.recordLocation(loc)
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
        
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            savedLocationsManager = SavedLocationsManager(this)
            locationHistoryManager = LocationHistoryManager(this)
            routePredictor = RoutePredictor(this)
            routeLearningSys = RouteLearningSys(this)
            searchAPI = NeshanSearchAPI(this)
            aiAssistant = ContextualAIAssistant(this)
            
            webView = binding.mapWebView
            webView.settings.javaScriptEnabled = true
            webView.addJavascriptInterface(MapInterface(), "Android")
            webView.loadUrl("file:///android_asset/neshan_map.html")
            
            checkPermissions()
            setupButtons()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("NavigationActivity", "Error", e)
        }
    }
    
    private fun setupButtons() {
        binding.myLocationButton.setOnClickListener {
            currentLocation?.let { loc ->
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
            }
        }
        
        binding.searchDestinationButton.setOnClickListener {
            val intent = Intent(this, SearchDestinationActivity::class.java)
            startActivityForResult(intent, 1001)
        }
        
        binding.savedLocationsButton.setOnClickListener {
            showSavedLocations()
        }
        
        binding.poiButton.setOnClickListener {
            showPOIDialog()
        }
        
        binding.saveCurrentLocationButton.setOnClickListener {
            currentLocation?.let { loc ->
                showSaveLocationDialog(LatLng(loc.latitude, loc.longitude))
            } ?: Toast.makeText(this, "⚠️ در حال دریافت موقعیت...", Toast.LENGTH_SHORT).show()
        }
        
        binding.startNavigationButton.setOnClickListener {
            startNavigation()
        }
        
        binding.stopNavigationButton.setOnClickListener {
            stopNavigation()
        }
        
        binding.addWaypointButton.setOnClickListener {
            Toast.makeText(this, "📍 مقصد میانی", Toast.LENGTH_SHORT).show()
        }
        
        binding.aiChatFab.setOnClickListener {
            showAIChat()
        }
        
        // دکمه ترافیک
        binding.trafficButton?.setOnClickListener {
            toggleTraffic()
        }
        
        // دکمه لایه‌های نقشه
        binding.layersButton?.setOnClickListener {
            showMapLayersDialog()
        }
    }
    
    private fun toggleTraffic() {
        isTrafficEnabled = !isTrafficEnabled
        if (isTrafficEnabled) {
            webView.evaluateJavascript("enableTraffic();", null)
            Toast.makeText(this, "🚦 ترافیک فعال شد", Toast.LENGTH_SHORT).show()
        } else {
            webView.evaluateJavascript("disableTraffic();", null)
            Toast.makeText(this, "✅ ترافیک غیرفعال شد", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showMapLayersDialog() {
        val layers = arrayOf("🗺️ نقشه عادی", "🛰️ ماهواره", "🌍 ترکیبی")
        MaterialAlertDialogBuilder(this)
            .setTitle("لایه نقشه")
            .setItems(layers) { _, which ->
                currentMapLayer = when (which) {
                    0 -> "normal"
                    1 -> "satellite"
                    else -> "hybrid"
                }
                webView.evaluateJavascript("setMapLayer('$currentMapLayer');", null)
                Toast.makeText(this, layers[which], Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showPOIDialog() {
        val poiTypes = arrayOf(
            "⛽ پمپ بنزین",
            "🍽️ رستوران",
            "🏥 بیمارستان",
            "🏧 ATM",
            "🅿️ پارکینگ",
            "☕ کافه",
            "🏨 هتل",
            "🏪 فروشگاه",
            "💊 داروخانه",
            "🏦 بانک"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📍 مکان‌های نزدیک")
            .setItems(poiTypes) { _, which ->
                val poiType = when (which) {
                    0 -> "gas_station"
                    1 -> "restaurant"
                    2 -> "hospital"
                    3 -> "atm"
                    4 -> "parking"
                    5 -> "cafe"
                    6 -> "hotel"
                    7 -> "store"
                    8 -> "pharmacy"
                    else -> "bank"
                }
                searchNearbyPOI(poiType, poiTypes[which])
            }
            .show()
    }
    
    private fun searchNearbyPOI(type: String, name: String) {
        currentLocation?.let { loc ->
            Toast.makeText(this, "🔍 جستجوی $name ...", Toast.LENGTH_SHORT).show()
            webView.evaluateJavascript("searchNearby(${loc.latitude}, ${loc.longitude}, '$type');", null)
        } ?: Toast.makeText(this, "⚠️ مکان شما در دسترس نیست", Toast.LENGTH_SHORT).show()
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
    
    private fun showAdvancedSearchDialog() {
        val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)
        val searchInput = EditText(this).apply {
            hint = "جستجوی مقصد..."
            setPadding(32, 32, 32, 16)
        }
        
        val cityInput = EditText(this).apply {
            hint = "شهر (پیش‌فرض: تهران)"
            setPadding(32, 16, 32, 32)
        }
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(searchInput)
            addView(cityInput)
        }
        
        // پیشنهادات ML
        currentLocation?.let { loc ->
            val predictions = routePredictor.predictNextDestination(loc)
            if (predictions.isNotEmpty()) {
                val suggestionsText = android.widget.TextView(this).apply {
                    text = "💡 پیشنهادات هوشمند:"
                    setPadding(32, 16, 32, 8)
                    setTextColor(0xFF9C27B0.toInt())
                    textSize = 14f
                }
                layout.addView(suggestionsText)
                
                predictions.take(2).forEach { prediction ->
                    val btn = com.google.android.material.button.MaterialButton(this).apply {
                        text = prediction.reason
                        setOnClickListener {
                            selectedDestination = prediction.location
                            webView.evaluateJavascript("addMarker(${prediction.location.latitude}, ${prediction.location.longitude}, 'پیشنهاد ML');", null)
                            Toast.makeText(this@NavigationActivity, "📍 مقصد انتخاب شد", Toast.LENGTH_SHORT).show()
                        }
                    }
                    layout.addView(btn)
                }
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🔍 جستجوی مقصد")
            .setView(layout)
            .setPositiveButton("جستجو") { _, _ ->
                val query = searchInput.text.toString()
                val city = cityInput.text.toString().ifEmpty { "تهران" }
                
                if (query.isNotEmpty()) {
                    performSearch(query, city)
                } else {
                    Toast.makeText(this, "⚠️ لطفاً مقصد را وارد کنید", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun performSearch(query: String, city: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val results = searchAPI.search(query, city)
                
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    
                    if (results.isEmpty()) {
                        Toast.makeText(this@NavigationActivity, "❌ نتیجه‌ای یافت نشد", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    
                    showSearchResults(results)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@NavigationActivity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showSearchResults(results: List<NeshanSearchAPI.SearchResult>) {
        val items = results.map { "📍 ${it.title}\n${it.address}" }.toTypedArray()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🔍 نتایج جستجو (${results.size})")
            .setItems(items) { _, which ->
                val result = results[which]
                selectedDestination = LatLng(result.latitude, result.longitude)
                webView.evaluateJavascript("addMarker(${result.latitude}, ${result.longitude}, '${result.title}');", null)
                Toast.makeText(this, "✅ ${result.title}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showAIChat() {
        val input = EditText(this).apply {
            hint = "دستور خود را بنویسید..."
            setPadding(32, 32, 32, 32)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("🤖 دستیار مسیریابی")
            .setView(input)
            .setPositiveButton("اجرا") { _, _ ->
                val userMessage = input.text.toString()
                if (userMessage.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val response = aiAssistant.processNavigationCommand(userMessage)
                            runOnUiThread {
                                MaterialAlertDialogBuilder(this@NavigationActivity)
                                    .setTitle(if (response.success) "✅ انجام شد" else "⚠️ پاسخ")
                                    .setMessage(response.message)
                                    .setPositiveButton("باشه", null)
                                    .show()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@NavigationActivity, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
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
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("latitude", 0.0)
            val lng = data.getDoubleExtra("longitude", 0.0)
            val title = data.getStringExtra("title") ?: "مقصد"
            
            if (lat != 0.0 && lng != 0.0) {
                selectedDestination = LatLng(lat, lng)
                webView.evaluateJavascript("addMarker($lat, $lng, '$title');", null)
                Toast.makeText(this, "✅ $title", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startNavigation() {
        routeStartTime = System.currentTimeMillis()
        binding.speedCard.visibility = View.VISIBLE
        binding.routeInfoCard.visibility = View.VISIBLE
    }
    
    private fun stopNavigation() {
        binding.speedCard.visibility = View.GONE
        binding.routeInfoCard.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
