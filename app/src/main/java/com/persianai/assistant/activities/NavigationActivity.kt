package com.persianai.assistant.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.databinding.ActivityNavigationBinding
import com.persianai.assistant.navigation.models.NavigationRoute
import com.persianai.assistant.navigation.SavedLocationsManager
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import com.google.android.gms.maps.model.LatLng
import com.persianai.assistant.ml.LocationHistoryManager
import com.persianai.assistant.ml.RoutePredictor
import com.persianai.assistant.ml.RouteLearningSys
import com.persianai.assistant.utils.NeshanSearchAPI
import com.persianai.assistant.ai.ContextualAIAssistant
import com.persianai.assistant.navigation.AdvancedNavigationSystem
import com.persianai.assistant.navigation.models.*
import com.persianai.assistant.navigation.sync.GoogleDriveSync
import com.persianai.assistant.navigation.learning.RouteLearningSystem
import com.persianai.assistant.navigation.detectors.SpeedCameraDetector
import com.persianai.assistant.navigation.analyzers.TrafficAnalyzer
import com.persianai.assistant.navigation.analyzers.RoadConditionAnalyzer
import com.persianai.assistant.navigation.ai.AIRoutePredictor
import com.persianai.assistant.navigation.ai.AIRoadLimitDetector
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import com.persianai.assistant.navigation.RouteSheetHelper

class NavigationActivity : AppCompatActivity() {
    
    companion object {
        var instance: NavigationActivity? = null
    }
    
    private lateinit var binding: ActivityNavigationBinding
    private lateinit var routeSheetHelper: RouteSheetHelper
    lateinit var webView: WebView  // public for RouteSheetHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var savedLocationsManager: SavedLocationsManager
    private lateinit var locationHistoryManager: LocationHistoryManager
    private lateinit var routePredictor: RoutePredictor
    private lateinit var routeLearningSys: RouteLearningSys
    private lateinit var searchAPI: NeshanSearchAPI
    private lateinit var aiAssistant: ContextualAIAssistant
    
    // سیستم مسیریاب پیشرفته
    private lateinit var navigationSystem: AdvancedNavigationSystem
    private lateinit var googleDriveSync: GoogleDriveSync
    private lateinit var routeLearningSystem: RouteLearningSystem
    private lateinit var speedCameraDetector: SpeedCameraDetector
    private lateinit var trafficAnalyzer: TrafficAnalyzer
    private lateinit var roadConditionAnalyzer: RoadConditionAnalyzer
    private lateinit var aiRoutePredictor: AIRoutePredictor
    private lateinit var aiRoadLimitDetector: AIRoadLimitDetector
    private lateinit var voiceGuide: com.persianai.assistant.voice.NavigationVoiceGuide
    
    var currentLocation: Location? = null
    private var selectedDestination: LatLng? = null
    private var currentNavigationRoute: NavigationRoute? = null
    private val routeWaypoints = mutableListOf<LatLng>()
    private var routeStartTime: Long = 0
    private var isTrafficEnabled = false
    private var currentMapLayer = "normal"
    private var isNavigationActive = false
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                currentLocation = loc
                webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
                webView.evaluateJavascript("disableAutoCenter();", null)
                binding.currentSpeedText.text = "${(loc.speed * 3.6f).toInt()} km/h"
                
                // ثبت مکان برای یادگیری
                locationHistoryManager.recordLocation(loc)
                
                // اگر در حال مسیریابی هستیم، هشدارها را بررسی کن
                if (isNavigationActive) {
                    checkAlerts(loc)
                }
            }
        }
    }
    
    private fun checkAlerts(location: Location) {
        lifecycleScope.launch {
            try {
                // بررسی هشدار سرعت‌گیرها و دوربین‌ها
                speedCameraDetector.checkLocation(OsmGeoPoint(location.latitude, location.longitude))
                
                // بررسی هشدار ترافیک
                trafficAnalyzer.checkLocation(OsmGeoPoint(location.latitude, location.longitude))
                
                // بررسی وضعیت جاده
                roadConditionAnalyzer.checkLocation(OsmGeoPoint(location.latitude, location.longitude))
                
                // تشخیص هوشمند محدودیت سرعت جاده با AI
                val currentSpeed = (location.speed * 3.6).toDouble() // تبدیل m/s به km/h
                val geoPoint = com.persianai.assistant.navigation.models.GeoPoint(
                    location.latitude, 
                    location.longitude
                )
                val result = aiRoadLimitDetector.detectSpeedLimit(geoPoint, currentSpeed)
                
                // نمایش محدودیت سرعت در UI
                runOnUiThread {
                    binding.speedLimitText?.text = "${result.speedLimit} km/h"
                }
                
            } catch (e: Exception) {
                Log.e("NavigationActivity", "Error checking alerts", e)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // نمایش نسخه جدید - برای تست
        Toast.makeText(this, "✅ v3.5 - Neshan + Google Maps!", Toast.LENGTH_LONG).show()

        webView = binding.mapWebView
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            savedLocationsManager = SavedLocationsManager(this)
            locationHistoryManager = LocationHistoryManager(this)
            routePredictor = RoutePredictor(this)
            routeLearningSys = RouteLearningSys(this)
            searchAPI = NeshanSearchAPI(this)
            aiAssistant = ContextualAIAssistant(this)
            routeSheetHelper = RouteSheetHelper(this)
            
            // مقداردهی سیستم مسیریاب پیشرفته
            navigationSystem = AdvancedNavigationSystem(this)
            googleDriveSync = GoogleDriveSync(this)
            routeLearningSystem = RouteLearningSystem(this)
            speedCameraDetector = SpeedCameraDetector(this)
            trafficAnalyzer = TrafficAnalyzer(this)
            roadConditionAnalyzer = RoadConditionAnalyzer(this)
            aiRoutePredictor = AIRoutePredictor(this)
            aiRoadLimitDetector = AIRoadLimitDetector(this)
            voiceGuide = com.persianai.assistant.voice.NavigationVoiceGuide(this)
            
            // تنظیم کلید API نشان
            val neshanApiKey = "service.649ba7521ba04da595c5ab56413b3c84"
            navigationSystem.setNeshanApiKey(neshanApiKey)
            
            // تنظیم لینک Google Drive برای اشتراک‌گذاری مسیرها
            val driveUrl = "https://drive.google.com/drive/folders/1bp1Ay9kmK_bjWq_PznRfkPvhhjdhSye1?usp=drive_link"
            googleDriveSync.setDriveUrl(driveUrl)
            
            webView = binding.mapWebView
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.addJavascriptInterface(MapInterface(), "Android")
            
            // Enable console.log debugging
            webView.webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    message?.let {
                        Log.d("WebView", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                    }
                    return true
                }
            }
            
            webView.loadUrl("file:///android_asset/neshan_map.html")
            
            checkPermissions()
            setupButtons()
            
            // شروع همگام‌سازی با Google Drive
            lifecycleScope.launch {
                try {
                    val syncResult = googleDriveSync.syncRoutes()
                    runOnUiThread {
                        Toast.makeText(
                            this@NavigationActivity,
                            "همگام‌سازی: ${syncResult.uploadedCount} آپلود، ${syncResult.downloadedCount} دانلود",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("NavigationActivity", "Sync error", e)
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("NavigationActivity", "Error", e)
        }
    }
    
    private fun setupButtons() {
        // جستجوی AI - TODO: پیاده‌سازی کامل
        binding.searchInput?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                Toast.makeText(this, "جستجو: ${v.text}", Toast.LENGTH_SHORT).show()
                true
            } else false
        }

        // دکمه مکان من (FAB)
        binding.myLocationFab?.setOnClickListener {
            if (currentLocation != null) {
                webView.evaluateJavascript("map.setView([${currentLocation!!.latitude}, ${currentLocation!!.longitude}], 16);", null)
                Toast.makeText(this, "✅ مکان فعلی", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ در حال دریافت مکان...", Toast.LENGTH_SHORT).show()
            }
        }

        // دکمه تست Long Press (موقت - برای debug)
        binding.searchInput?.setOnLongClickListener {
            currentLocation?.let { loc ->
                Toast.makeText(this, "🧪 تست Bottom Sheet", Toast.LENGTH_SHORT).show()
                routeSheetHelper.showLocationSheet(loc.latitude, loc.longitude)
            }
            true
        }
        
        // Toggle ترافیک
        binding.trafficToggleFab?.setOnClickListener {
            isTrafficEnabled = !isTrafficEnabled
            webView.evaluateJavascript(
                "toggleTraffic($isTrafficEnabled);",
                null
            )
            Toast.makeText(
                this,
                if (isTrafficEnabled) "ترافیک فعال شد" else "ترافیک غیرفعال شد",
                Toast.LENGTH_SHORT
            ).show()
        }

        // تب‌های پایین - از XML (موقتاً غیرفعال به دلیل R.id)
        // setupBottomTabsFromXml()
        
        // استفاده از روش دیگه برای تب‌ها
        setupBottomTabsWithoutRId()

        // دکمه‌ها فعال هستن
        binding.myLocationButton?.setOnClickListener {
            currentLocation?.let { loc ->
                webView.evaluateJavascript("enableAutoCenter();", null)
                webView.evaluateJavascript("map.setView([${loc.latitude}, ${loc.longitude}], 15);", null)
                Toast.makeText(this, "📍 برگشت به مکان فعلی", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.searchDestinationButton?.setOnClickListener {
            val intent = Intent(this, SearchDestinationActivity::class.java)
            startActivityForResult(intent, 1001)
        }
        
        binding.savedLocationsButton?.setOnClickListener {
            showSavedLocations()
        }
        
        binding.poiButton?.setOnClickListener {
            showPOIDialog()
        }
        
        binding.saveCurrentLocationButton?.setOnClickListener {
            currentLocation?.let { loc ->
                showSaveLocationDialog(LatLng(loc.latitude, loc.longitude))
            } ?: Toast.makeText(this, "⚠️ در حال دریافت موقعیت...", Toast.LENGTH_SHORT).show()
        }
        
        binding.startNavigationButton?.setOnClickListener {
            if (selectedDestination != null && currentLocation != null) {
                startNavigation()
            } else {
                Toast.makeText(this, "لطفاً ابتدا مقصد را انتخاب کنید", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.stopNavigationButton?.setOnClickListener {
            stopNavigation()
        }
        
        binding.addWaypointButton?.setOnClickListener {
            Toast.makeText(this, "📍 مقصد میانی", Toast.LENGTH_SHORT).show()
        }
        
        // دکمه تنظیمات هشدارها
        // TODO: Add alertSettingsButton to layout
        // binding.alertSettingsButton?.setOnClickListener {
        //     showAlertSettingsDialog()
        // }
        
        // دکمه همگام‌سازی دستی
        // TODO: Add syncButton to layout
        // binding.syncButton?.setOnClickListener {
        //     lifecycleScope.launch {
        //         try {
        //             binding.syncButton?.isEnabled = false
        //             val syncResult = googleDriveSync.syncRoutes()
        //             runOnUiThread {
        //                 Toast.makeText(
        //                     this@NavigationActivity,
        //                     "همگام‌سازی انجام شد: ${syncResult.uploadedCount} آپلود، ${syncResult.downloadedCount} دانلود",
        //                     Toast.LENGTH_SHORT
        //                 ).show()
        //                 binding.syncButton?.isEnabled = true
        //             }
        //         } catch (e: Exception) {
        //             runOnUiThread {
        //                 Toast.makeText(
        //                     this@NavigationActivity,
        //                     "خطا در همگام‌سازی: ${e.message}",
        //                     Toast.LENGTH_LONG
        //                 ).show()
        //                 binding.syncButton?.isEnabled = true
        //             }
        //         }
        //     }
        // }
    }
    
    private fun startNavigation() {
        selectedDestination?.let { dest ->
            currentLocation?.let { start ->
                lifecycleScope.launch {
                    try {
                        // استفاده از سیستم مسیریاب پیشرفته برای پیدا کردن مسیر
                        val route = navigationSystem.getRouteWithAI(
                            GeoPoint(start.latitude, start.longitude),
                            GeoPoint(dest.latitude, dest.longitude)
                        )
                        
                        route?.let { validRoute ->
                            currentNavigationRoute = validRoute
                            routeStartTime = System.currentTimeMillis()
                            isNavigationActive = true
                            
                            // نمایش مسیر روی نقشه
                            val routePoints = validRoute.waypoints.joinToString(",") { 
                                "new L.LatLng(${it.latitude}, ${it.longitude})"
                            }
                            webView.evaluateJavascript("showRoute([$routePoints]);", null)
                            
                            // شروع یادگیری مسیر
                            routeLearningSystem.startLearningRoute(validRoute)
                            
                            // فعال کردن هشدارها
                            enableAlerts()
                            
                            // نمایش کارت‌های سرعت و اطلاعات مسیر
                            binding.speedCard.visibility = View.VISIBLE
                            binding.routeInfoCard.visibility = View.VISIBLE
                            
                            runOnUiThread {
                                Toast.makeText(
                                    this@NavigationActivity,
                                    "🧭 مسیریابی شروع شد (طول: ${String.format("%.1f", validRoute.distance / 1000)} کیلومتر)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@NavigationActivity,
                                "خطا در مسیریابی: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun stopNavigation() {
        isNavigationActive = false
        currentNavigationRoute?.let { route ->
            // پایان یادگیری مسیر
            lifecycleScope.launch {
                try {
                    routeLearningSystem.finishLearning()
                    
                    // همگام‌سازی با Google Drive
                    googleDriveSync.syncRoutes()
                } catch (e: Exception) {
                    Log.e("NavigationActivity", "Error finishing route learning", e)
                }
            }
        }
        
        webView.evaluateJavascript("clearRoute();", null)
        currentNavigationRoute = null
        disableAlerts()
        
        // مخفی کردن کارت‌های سرعت و اطلاعات مسیر
        binding.speedCard.visibility = View.GONE
        binding.routeInfoCard.visibility = View.GONE
        
        Toast.makeText(this, "✅ مسیریابی متوقف شد", Toast.LENGTH_SHORT).show()
    }
    
    private fun enableAlerts() {
        // فعال کردن هشدار سرعت‌گیرها و دوربین‌ها
        speedCameraDetector.enable()
        
        // فعال کردن تحلیلگر ترافیک
        trafficAnalyzer.enable()
        
        // فعال کردن تحلیلگر وضعیت جاده
        roadConditionAnalyzer.enable()
        
        // فعال کردن تشخیص هوشمند محدودیت سرعت با AI
        aiRoadLimitDetector.enable()
    }
    
    private fun disableAlerts() {
        speedCameraDetector.disable()
        trafficAnalyzer.disable()
        roadConditionAnalyzer.disable()
        aiRoadLimitDetector.disable()
    }
    
    private fun showAlertSettingsDialog() {
        val alertTypes = arrayOf(
            "هشدار سرعت‌گیرها",
            "هشدار دوربین‌های کنترل سرعت",
            "هشدار ترافیک",
            "هشدار وضعیت جاده",
            "تشخیص هوشمند محدودیت جاده با AI",
            "هشدارهای صوتی"
        )
        val checkedItems = booleanArrayOf(true, true, true, true, true, true)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("تنظیمات هشدارها")
            .setMultiChoiceItems(alertTypes, checkedItems) { _, which, isChecked ->
                // ذخیره تنظیمات
                when (which) {
                    0 -> speedCameraDetector.setSpeedBumpAlertsEnabled(isChecked)
                    1 -> speedCameraDetector.setCameraAlertsEnabled(isChecked)
                    2 -> trafficAnalyzer.setEnabled(isChecked)
                    3 -> roadConditionAnalyzer.setEnabled(isChecked)
                    4 -> if (isChecked) aiRoadLimitDetector.enable() else aiRoadLimitDetector.disable()
                    5 -> {
                        speedCameraDetector.setVoiceAlertsEnabled(isChecked)
                        trafficAnalyzer.setVoiceAlertsEnabled(isChecked)
                        roadConditionAnalyzer.setVoiceAlertsEnabled(isChecked)
                    }
                }
            }
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("لغو", null)
            .show()
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
    
    private fun setupBottomTabsWithoutRId() {
        // پیدا کردن bottomNavBar از XML بدون استفاده از R.id
        val rootView = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bottomNavBar = findViewByTag(rootView, "bottomNavBar")
        
        if (bottomNavBar is android.widget.LinearLayout) {
            // bottomNavBar پیدا شد، حالا تب‌ها رو پیدا می‌کنیم
            if (bottomNavBar.childCount >= 4) {
                // تب 0: نقشه
                bottomNavBar.getChildAt(0)?.setOnClickListener {
                    Toast.makeText(this, "🗺️ نقشه", Toast.LENGTH_SHORT).show()
                }
                
                // تب 1: جستجو
                bottomNavBar.getChildAt(1)?.setOnClickListener {
                    val intent = Intent(this, SearchDestinationActivity::class.java)
                    startActivityForResult(intent, 1001)
                }
                
                // تب 2: ذخیره
                bottomNavBar.getChildAt(2)?.setOnClickListener {
                    showSavedLocations()
                }
                
                // تب 3: سایر
                bottomNavBar.getChildAt(3)?.setOnClickListener {
                    showMoreOptions()
                }
            }
        }
    }
    
    private fun findViewByTag(parent: android.view.View, tag: String): android.view.View? {
        if (parent.tag == tag) return parent
        if (parent is android.view.ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val found = findViewByTag(child, tag)
                if (found != null) return found
            }
        }
        return null
    }
    
    private fun showMoreOptions() {
        val options = arrayOf(
            "🔍 مکان‌های نزدیک",
            "⚙️ تنظیمات",
            "💬 چت AI"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("سایر")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPOIDialog()
                    1 -> showSettingsDialog()
                    2 -> showAIChat()
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showSettingsDialog() {
        val settings = arrayOf(
            "🔊 هشدارهای صوتی",
            "📢 هشدار محدودیت سرعت",
            "📷 هشدار دوربین",
            "🚦 هشدار سرعت‌گیر"
        )
        
        val prefs = getSharedPreferences("NavigationSettings", MODE_PRIVATE)
        val checkedItems = booleanArrayOf(
            prefs.getBoolean("voice_alerts", true),
            prefs.getBoolean("speed_limit_alert", true),
            prefs.getBoolean("camera_alert", true),
            prefs.getBoolean("speed_camera_alert", true)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚙️ تنظیمات")
            .setMultiChoiceItems(settings, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("ذخیره") { _, _ ->
                prefs.edit().apply {
                    putBoolean("voice_alerts", checkedItems[0])
                    putBoolean("speed_limit_alert", checkedItems[1])
                    putBoolean("camera_alert", checkedItems[2])
                    putBoolean("speed_camera_alert", checkedItems[3])
                    apply()
                }
                
                // اعمال تنظیمات
                applyVoiceSettings(checkedItems)
                Toast.makeText(this, "✅ تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("لغو", null)
            .show()
    }
    
    private fun applyVoiceSettings(settings: BooleanArray) {
        // فعال/غیرفعال کردن هشدارهای صوتی
        if (settings[0]) {
            // فعال کردن هشدارهای صوتی
            Toast.makeText(this, "🔊 هشدارهای صوتی فعال شد", Toast.LENGTH_SHORT).show()
        }
        
        if (settings[1]) {
            aiRoadLimitDetector.enable()
        } else {
            aiRoadLimitDetector.disable()
        }
        
        if (settings[2] || settings[3]) {
            speedCameraDetector.enable()
        } else {
            speedCameraDetector.disable()
        }
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
                // غیرفعال کردن auto-center
                webView.evaluateJavascript("disableAutoCenter();", null)
                
                // نمایش Bottom Sheet با یک کلیک ساده
                selectedDestination = LatLng(lat, lng)
                webView.evaluateJavascript("showDestinationMarker($lat, $lng);", null)
                routeSheetHelper.showLocationSheet(lat, lng)
                Toast.makeText(this@NavigationActivity, "📍 مقصد انتخاب شد", Toast.LENGTH_SHORT).show()
            }
        }
        
        @JavascriptInterface
        fun onLocationLongPress(lat: Double, lng: Double) {
            Log.d("NavigationActivity", "🔴 Long Press detected: $lat, $lng")
            runOnUiThread {
                Toast.makeText(this@NavigationActivity, "📍 Long Press: ${String.format("%.4f, %.4f", lat, lng)}", Toast.LENGTH_SHORT).show()
                selectedDestination = LatLng(lat, lng)
                webView.evaluateJavascript("showDestinationMarker($lat, $lng);", null)
                routeSheetHelper.showLocationSheet(lat, lng)
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
    
    private fun showLocationOptionsBottomSheet(lat: Double, lng: Double) {
        val options = arrayOf("💾 ذخیره مکان", "🛣️ مسیرهای پیشنهادی", "🚗 بزن بریم")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("📍 مکان انتخاب شده")
            .setMessage("${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSaveLocationDialog(LatLng(lat, lng))
                    1 -> showSuggestedRoutes(LatLng(lat, lng))
                    2 -> {
                        if (currentLocation != null) {
                            selectedDestination = LatLng(lat, lng)
                            startNavigation()
                        } else {
                            Toast.makeText(this, "⚠️ در حال دریافت موقعیت...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showSuggestedRoutes(destination: LatLng) {
        currentLocation?.let { loc ->
            lifecycleScope.launch {
                try {
                    Toast.makeText(this@NavigationActivity, "🤖 در حال محاسبه مسیرها...", Toast.LENGTH_SHORT).show()
                    
                    val origin = OsmGeoPoint(loc.latitude, loc.longitude)
                    val dest = OsmGeoPoint(destination.latitude, destination.longitude)
                    
                    // محاسبه 3 نوع مسیر
                    val routes = listOf(
                        Pair("سریع‌ترین مسیر", calculateRouteDistance(origin, dest, 1.0)),
                        Pair("کوتاه‌ترین مسیر", calculateRouteDistance(origin, dest, 0.85)),
                        Pair("مسیر توصیه شده", calculateRouteDistance(origin, dest, 0.95))
                    )
                    
                    val routeNames = routes.map { pair ->
                        val name = pair.first
                        val distance = pair.second
                        val time = (distance / 50 * 60).toInt() // فرض: 50 کیلومتر در ساعت
                        "$name\n📍 ${String.format("%.1f", distance)} کیلومتر - ⏱️ $time دقیقه"
                    }.toTypedArray()
                    
                    MaterialAlertDialogBuilder(this@NavigationActivity)
                        .setTitle("🛣️ مسیرهای پیشنهادی")
                        .setItems(routeNames) { dialog, which: Int ->
                            selectedDestination = destination
                            Toast.makeText(
                                this@NavigationActivity,
                                "✅ ${routes[which].first} انتخاب شد",
                                Toast.LENGTH_SHORT
                            ).show()
                            startNavigation()
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                        
                } catch (e: Exception) {
                    Log.e("NavigationActivity", "Route error", e)
                    selectedDestination = destination
                    startNavigation()
                }
            }
        } ?: Toast.makeText(this, "⚠️ لطفاً منتظر بمانید...", Toast.LENGTH_SHORT).show()
    }
    
    private fun calculateRouteDistance(origin: OsmGeoPoint, dest: OsmGeoPoint, factor: Double): Double {
        // محاسبه فاصله با فرمول Haversine
        val r = 6371 // شعاع زمین به کیلومتر
        val dLat = Math.toRadians(dest.latitude - origin.latitude)
        val dLon = Math.toRadians(dest.longitude - origin.longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(origin.latitude)) * Math.cos(Math.toRadians(dest.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c * factor
    }
    
    fun startNavigationTo(destLat: Double, destLng: Double) {
        selectedDestination = LatLng(destLat, destLng)
        startNavigation()
    }
    
    fun showSaveLocationDialog(latLng: LatLng) {
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
                
                // نمایش marker و حرکت به مکان
                webView.evaluateJavascript("showDestinationMarker(${result.latitude}, ${result.longitude});", null)
                webView.evaluateJavascript("map.setView([${result.latitude}, ${result.longitude}], 15);", null)
                
                // نمایش Bottom Sheet با گزینه‌های مسیریابی
                routeSheetHelper.showLocationSheet(result.latitude, result.longitude)
                Toast.makeText(this, "✅ ${result.title}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun showAIChat() {
        val intent = Intent(this, AIChatActivity::class.java)
        startActivity(intent)
    }
    
    fun searchAndNavigateTo(locationName: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar?.visibility = View.VISIBLE
                val results = searchAPI.searchGlobal(locationName)
                
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    if (results.isNotEmpty()) {
                        val first = results[0]
                        selectedDestination = LatLng(first.latitude, first.longitude)
                        webView.evaluateJavascript("showDestinationMarker(${first.latitude}, ${first.longitude});", null)
                        webView.evaluateJavascript("map.setView([${first.latitude}, ${first.longitude}], 15);", null)
                        
                        routeSheetHelper.showLocationSheet(first.latitude, first.longitude)
                        Toast.makeText(this@NavigationActivity, "✅ ${first.title}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@NavigationActivity, "❌ مکان '$locationName' پیدا نشد", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(this@NavigationActivity, "❌ خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    fun performDirectSearch(query: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar?.visibility = View.VISIBLE
                val results = searchAPI.searchGlobal(query)
                
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    if (results.isNotEmpty()) {
                        showSearchResults(results)
                    } else {
                        Toast.makeText(this@NavigationActivity, "❌ نتیجه‌ای برای '$query' پیدا نشد", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar?.visibility = View.GONE
                    Toast.makeText(this@NavigationActivity, "❌ خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
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
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
