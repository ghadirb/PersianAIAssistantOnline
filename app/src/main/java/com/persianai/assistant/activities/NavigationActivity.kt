package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityNavigationBinding
import com.persianai.assistant.navigation.NessanMapsAPI
import com.persianai.assistant.navigation.PersianNavigationTTS
import com.persianai.assistant.navigation.AIPoweredTTS
import com.persianai.assistant.navigation.SpeedCameraManager
import com.persianai.assistant.navigation.SavedLocationsManager
import kotlinx.coroutines.launch
import java.util.*

/**
 * مسیریابی فارسی با نقشه و هشدارهای صوتی
 * استفاده از Google Maps + Nessan Maps API + Persian TTS
 */
class NavigationActivity : AppCompatActivity(), OnMapReadyCallback {
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val DEFAULT_ZOOM = 15f
        const val NESHAN_API_KEY = "service.649ba7521ba04da595c5ab56413b3c84"
    }
    
    private lateinit var binding: ActivityNavigationBinding
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var aiPoweredTTS: AIPoweredTTS
    private lateinit var speedCameraManager: SpeedCameraManager
    private lateinit var nessanMapsAPI: NessanMapsAPI
    private lateinit var savedLocationsManager: SavedLocationsManager
    
    private var currentLocation: Location? = null
    private var currentRoute: List<LatLng>? = null
    private var currentSpeed: Float = 0f // km/h
    private var speedLimit: Int = 0
    private var alternativeRoutes: List<NessanMapsAPI.RouteResult> = emptyList()
    private var selectedRouteIndex: Int = 0
    private var routePolylines: MutableList<Polyline> = mutableListOf()
    private var isNavigating = false
    private var destinationMarker: Marker? = null
    private var routePolyline: Polyline? = null
    
    // POI Types
    private val poiTypes = mapOf(
        "gas" to "⛽ پمپ بنزین",
        "food" to "🍴 رستوران",
        "hospital" to "🏥 بیمارستان",
        "atm" to "💳 عابر بانک",
        "parking" to "🅿️ پارکینگ"
    )
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentLocation = location
                currentSpeed = location.speed * 3.6f // m/s به km/h
                
                updateLocationOnMap(location)
                checkSpeedWarnings(location)
                checkSpeedCameras(location)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🗺️ مسیریابی فارسی"
        
        // Initialize services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        aiPoweredTTS = AIPoweredTTS(this)
        speedCameraManager = SpeedCameraManager(this)
        nessanMapsAPI = NessanMapsAPI()
        savedLocationsManager = SavedLocationsManager(this)
        
        // نمایش وضعیت TTS
        android.util.Log.d("Navigation", "TTS Status: ${aiPoweredTTS.getStatus()}")
        
        // Setup map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        // سرعت‌سنج ابتدا مخفی است
        binding.speedCard.visibility = android.view.View.GONE
        
        // دکمه جستجوی مقصد
        binding.searchDestinationButton.setOnClickListener {
            showDestinationSearchDialog()
        }
        
        // دکمه مکان فعلی
        binding.myLocationButton.setOnClickListener {
            currentLocation?.let { location ->
                googleMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.latitude, location.longitude),
                        17f
                    )
                )
                Toast.makeText(this, "📍 مکان فعلی", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "مکان شما هنوز آماده نیست", Toast.LENGTH_SHORT).show()
            }
        }
        
        // دکمه شروع مسیریابی
        binding.startNavigationButton.setOnClickListener {
            if (currentRoute != null) {
                startNavigation()
                // نمایش سرعت‌سنج
                binding.speedCard.visibility = android.view.View.VISIBLE
            } else {
                Toast.makeText(this, "لطفاً ابتدا مقصد را انتخاب کنید", Toast.LENGTH_SHORT).show()
            }
        }
        
        // دکمه توقف
        binding.stopNavigationButton.setOnClickListener {
            stopNavigation()
            // مخفی کردن سرعت‌سنج
            binding.speedCard.visibility = android.view.View.GONE
        }
        
        // دکمه توقف مسیریابی
        binding.stopNavigationButton.setOnClickListener {
            stopNavigation()
        }
        
        // دکمه مکان‌های ذخیره شده
        binding.savedLocationsButton?.setOnClickListener {
            showSavedLocationsDialog()
        }
        
        // دکمه نمایش POI
        binding.poiButton?.setOnClickListener {
            showPOIDialog()
        }
        
        // دکمه ذخیره مکان فعلی
        binding.saveCurrentLocationButton?.setOnClickListener {
            saveCurrentLocation()
        }
        
        // دکمه چت AI
        binding.aiChatFab?.setOnClickListener {
            showNavigationAIChat()
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = false
            
            startLocationUpdates()
        }
    }
    
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, 1001)
        } else {
            startLocationUpdates()
        }
    }
    
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000 // هر 2 ثانیه
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
        }
    }
    
    private fun updateLocationOnMap(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        // بروزرسانی سرعت
        binding.currentSpeedText.text = "سرعت: ${currentSpeed.toInt()} km/h"
        
        // تغییر رنگ سرعت اگر بیشتر از حد مجاز باشد
        if (speedLimit > 0 && currentSpeed > speedLimit) {
            binding.currentSpeedText.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            binding.currentSpeedText.setTextColor(getColor(android.R.color.white))
        }
    }
    
    private fun checkSpeedWarnings(location: Location) {
        if (speedLimit > 0 && currentSpeed > speedLimit + 5) {
            // هشدار تخطی از سرعت مجاز (فوری)
            lifecycleScope.launch {
                val warning = "توجه! سرعت شما ${currentSpeed.toInt()} کیلومتر است. محدودیت سرعت $speedLimit کیلومتر می‌باشد"
                aiPoweredTTS.speak(warning, urgent = true)
            }
        }
    }
    
    private fun checkSpeedCameras(location: Location) {
        lifecycleScope.launch {
            val nearbyCameras = speedCameraManager.getNearbyCameras(
                location.latitude,
                location.longitude,
                500.0 // 500 متر
            )
            
            nearbyCameras.forEach { camera ->
                val distance = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    camera.latitude,
                    camera.longitude,
                    distance
                )
                
                if (distance[0] < 500) {
                    warnSpeedCamera(distance[0].toInt(), camera.speedLimit)
                }
            }
        }
    }
    
    private suspend fun warnSpeedCamera(distanceInMeters: Int, cameraSpeedLimit: Int) {
        val warning = when {
            distanceInMeters < 100 -> "دوربین سرعت! محدودیت $cameraSpeedLimit کیلومتر"
            distanceInMeters < 300 -> "توجه! دوربین سرعت در $distanceInMeters متری. محدودیت $cameraSpeedLimit کیلومتر"
            else -> "دوربین سرعت در $distanceInMeters متری"
        }
        
        aiPoweredTTS.speak(warning, urgent = true)
        
        // نمایش آیکون دوربین روی نقشه
        googleMap?.addMarker(
            MarkerOptions()
                .position(LatLng(currentLocation!!.latitude, currentLocation!!.longitude))
                .title("دوربین سرعت")
                .snippet("محدودیت: $cameraSpeedLimit km/h")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }
    
    private fun showPOIsOnMap(poiType: String) {
        currentLocation?.let { location ->
            // نمایش POI های اطراف
            Toast.makeText(this, "در حال جستجوی ${poiTypes[poiType]}...", Toast.LENGTH_SHORT).show()
            
            // TODO: API call to get POIs
            // برای الان موقعیت‌های نمونه
            val samplePOIs = when(poiType) {
                "gas" -> listOf(
                    LatLng(location.latitude + 0.01, location.longitude + 0.01),
                    LatLng(location.latitude - 0.01, location.longitude + 0.02)
                )
                "food" -> listOf(
                    LatLng(location.latitude + 0.02, location.longitude - 0.01),
                    LatLng(location.latitude - 0.02, location.longitude - 0.02)
                )
                else -> emptyList()
            }
            
            samplePOIs.forEach { poi ->
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(poi)
                        .title(poiTypes[poiType])
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                )
            }
        }
    }
    
    private fun showDestinationSearchDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("جستجوی مقصد")
        
        val input = android.widget.EditText(this)
        input.hint = "آدرس مقصد را وارد کنید"
        builder.setView(input)
        
        builder.setPositiveButton("جستجو") { _, _ ->
            val destination = input.text.toString()
            if (destination.isNotEmpty()) {
                searchDestination(destination)
            }
        }
        
        builder.setNegativeButton("لغو") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
    
    private fun searchDestination(query: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = android.view.View.VISIBLE
                
                // استفاده از Nessan Maps API برای جستجو
                val result = nessanMapsAPI.searchPlace(query)
                
                if (result != null) {
                    val destination = LatLng(result.latitude, result.longitude)
                    
                    // نمایش مارکر مقصد
                    googleMap?.addMarker(
                        MarkerOptions()
                            .position(destination)
                            .title(result.name)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                    )
                    
                    // حرکت دوربین به مقصد
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destination, 13f))
                    
                    // TODO: دریافت مسیرهای جایگزین در آینده
                    Toast.makeText(this@NavigationActivity, "مسیریابی فعال شد", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@NavigationActivity, "مقصد یافت نشد", Toast.LENGTH_SHORT).show()
                }
                
                binding.progressBar.visibility = android.view.View.GONE
            } catch (e: Exception) {
                android.util.Log.e("Navigation", "Error searching destination", e)
                Toast.makeText(this@NavigationActivity, "خطا در جستجو", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private suspend fun getRoute(origin: LatLng, destination: LatLng) {
        try {
            val route = nessanMapsAPI.getDirections(origin, destination)
            
            if (route != null) {
                currentRoute = route.points
                speedLimit = route.speedLimit
                
                // رسم مسیر روی نقشه
                val polylineOptions = PolylineOptions()
                    .addAll(route.points)
                    .color(getColor(R.color.primaryColor))
                    .width(10f)
                
                googleMap?.addPolyline(polylineOptions)
                
                // نمایش اطلاعات مسیر
                binding.routeInfoCard.visibility = android.view.View.VISIBLE
                binding.routeDistanceText.text = "مسافت: ${route.distance} کیلومتر"
                binding.routeDurationText.text = "زمان تقریبی: ${route.duration} دقیقه"
                binding.speedLimitText.text = "سرعت مجاز: ${route.speedLimit} km/h"
                
                // شروع راهنمایی صوتی
                lifecycleScope.launch {
                    aiPoweredTTS.speak("مسیر محاسبه شد. مسافت ${route.distance} کیلومتر. زمان تقریبی ${route.duration} دقیقه")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Navigation", "Error getting route", e)
            Toast.makeText(this, "خطا در محاسبه مسیر", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startNavigation() {
        if (currentRoute != null) {
            binding.startNavigationButton.visibility = android.view.View.GONE
            binding.stopNavigationButton.visibility = android.view.View.VISIBLE
            
            lifecycleScope.launch {
                aiPoweredTTS.speak("مسیریابی شروع شد. لطفاً به دستورات توجه کنید")
            }
            
            Toast.makeText(this, "مسیریابی شروع شد", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "ابتدا مقصد را انتخاب کنید", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopNavigation() {
        binding.startNavigationButton.visibility = android.view.View.VISIBLE
        binding.stopNavigationButton.visibility = android.view.View.GONE
        
        currentRoute = null
        googleMap?.clear()
        
        Toast.makeText(this, "مسیریابی متوقف شد", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        aiPoweredTTS.shutdown()
    }
    
    private fun handleAIIntent() {
        val aiDestination = intent.getStringExtra("AI_DESTINATION")
        val aiVoice = intent.getBooleanExtra("AI_VOICE", false)
        
        if (aiDestination != null) {
            // جستجوی خودکار مقصد
            lifecycleScope.launch {
                try {
                    val results = com.persianai.assistant.api.NeshanAPI.searchLocation(aiDestination)
                    if (results.isNotEmpty()) {
                        val dest = results[0]
                        Toast.makeText(
                            this@NavigationActivity,
                            "🗺️ مسیریابی به ${dest.name}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        if (aiVoice) {
                            aiPoweredTTS.speak("مسیریابی به ${dest.name} شروع شد")
                        }
                        
                        // شروع مسیریابی (فقط اگر قبلا مسیر محاسبه شده)
                        // TODO: باید از nessanMapsAPI.getRoute استفاده کنیم
                        currentLocation?.let {
                            Toast.makeText(
                                this@NavigationActivity,
                                "📍 موقعیت شما: ${it.latitude}, ${it.longitude}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@NavigationActivity,
                            "❌ مقصد '‎$aiDestination' پیدا نشد",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NavigationActivity", "AI Intent error", e)
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun showSavedLocationsDialog() {
        val locations = savedLocationsManager.getAllLocations()
        
        if (locations.isEmpty()) {
            Toast.makeText(this, "📍 مکانی ذخیره نشده است", Toast.LENGTH_SHORT).show()
            return
        }
        
        val locationNames = locations.map { "${getCategoryEmoji(it.category)} ${it.name}" }.toTypedArray()
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("📍 مکان‌های ذخیره شده")
        builder.setItems(locationNames) { _, which ->
            val selectedLocation = locations[which]
            val destination = LatLng(selectedLocation.latitude, selectedLocation.longitude)
            
            // نمایش مارکر
            googleMap?.addMarker(
                MarkerOptions()
                    .position(destination)
                    .title(selectedLocation.name)
                    .snippet(selectedLocation.address)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            
            // حرکت دوربین
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f))
            
            // محاسبه مسیر
            currentLocation?.let { origin ->
                lifecycleScope.launch {
                    getRoute(LatLng(origin.latitude, origin.longitude), destination)
                }
            }
            
            Toast.makeText(this, "📍 ${selectedLocation.name}", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNeutralButton("مدیریت") { _, _ ->
            showManageLocationsDialog()
        }
        
        builder.setNegativeButton("بستن", null)
        builder.show()
    }
    
    private fun showManageLocationsDialog() {
        val locations = savedLocationsManager.getAllLocations()
        val locationNames = locations.map { "${getCategoryEmoji(it.category)} ${it.name}" }.toTypedArray()
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("🗑️ مدیریت مکان‌ها")
        builder.setItems(locationNames) { _, which ->
            val location = locations[which]
            
            val confirmBuilder = androidx.appcompat.app.AlertDialog.Builder(this)
            confirmBuilder.setTitle("حذف ${location.name}")
            confirmBuilder.setMessage("آیا مطمئن هستید؟")
            confirmBuilder.setPositiveButton("حذف") { _, _ ->
                if (savedLocationsManager.deleteLocation(location.id)) {
                    Toast.makeText(this, "✅ حذف شد", Toast.LENGTH_SHORT).show()
                }
            }
            confirmBuilder.setNegativeButton("لغو", null)
            confirmBuilder.show()
        }
        builder.setNegativeButton("بستن", null)
        builder.show()
    }
    
    private fun showPOIDialog() {
        val poiList = arrayOf(
            "⛽ پمپ بنزین",
            "🍴 رستوران",
            "🏥 بیمارستان",
            "🏧 عابر بانک",
            "🅿️ پارکینگ",
            "☕ کافه",
            "🏨 هتل"
        )
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("📏 نمایش مکان‌های نزدیک")
        builder.setItems(poiList) { _, which ->
            val poiType = when(which) {
                0 -> "gas"
                1 -> "food"
                2 -> "hospital"
                3 -> "atm"
                4 -> "parking"
                5 -> "cafe"
                6 -> "hotel"
                else -> "gas"
            }
            showPOIsOnMap(poiType)
        }
        builder.setNegativeButton("بستن", null)
        builder.show()
    }
    
    private fun saveCurrentLocation() {
        currentLocation?.let { location ->
            val latLng = LatLng(location.latitude, location.longitude)
            
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setTitle("💾 ذخیره مکان")
            
            val input = android.widget.EditText(this)
            input.hint = "نام مکان"
            builder.setView(input)
            
            val categories = arrayOf("🏠 خانه", "💼 محل کار", "⭐ علاقه‌مندی")
            var selectedCategory = "favorite"
            
            builder.setSingleChoiceItems(categories, 2) { _, which ->
                selectedCategory = when(which) {
                    0 -> "home"
                    1 -> "work"
                    else -> "favorite"
                }
            }
            
            builder.setPositiveButton("ذخیره") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    if (savedLocationsManager.saveLocation(name, "", latLng, selectedCategory)) {
                        Toast.makeText(this, "✅ ذخیره شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            builder.setNegativeButton("لغو", null)
            builder.show()
        } ?: run {
            Toast.makeText(this, "مکان فعلی در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getCategoryEmoji(category: String): String {
        return when(category) {
            "home" -> "🏠"
            "work" -> "💼"
            else -> "⭐"
        }
    }
    
    private fun showNavigationAIChat() {
        val input = EditText(this)
        input.hint = "مثلا: منو به نزدیکترین پمپ بنزین ببر یا مکان فعلی رو ذخیره کن"
        input.setPadding(20, 20, 20, 20)
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("🤖 دستیار مسیریاب")
        builder.setView(input)
        builder.setPositiveButton("ارسال") { _, _ ->
            val command = input.text.toString()
            if (command.isNotEmpty()) {
                processNavigationCommand(command)
            }
        }
        builder.setNegativeButton("لغو", null)
        builder.show()
    }
    
    private fun processNavigationCommand(command: String) {
        Toast.makeText(this, "🔄 در حال پردازش...", Toast.LENGTH_SHORT).show()
        
        when {
            command.contains("پمپ") || command.contains("بنزین") -> {
                showPOIsOnMap("gas")
                Toast.makeText(this, "⛽ نمایش پمپ بنزین‌ها", Toast.LENGTH_SHORT).show()
            }
            command.contains("رستوران") || command.contains("غذا") -> {
                showPOIsOnMap("food")
                Toast.makeText(this, "🍴 نمایش رستوران‌ها", Toast.LENGTH_SHORT).show()
            }
            command.contains("بیمارستان") -> {
                showPOIsOnMap("hospital")
                Toast.makeText(this, "🏥 نمایش بیمارستان‌ها", Toast.LENGTH_SHORT).show()
            }
            command.contains("ذخیره") && command.contains("مکان") -> {
                saveCurrentLocation()
            }
            command.contains("خانه") -> {
                // جستجوی خانه در مکان‌های ذخیره شده
                val homeLocation = savedLocationsManager.getAllLocations().find { it.category == "home" }
                if (homeLocation != null) {
                    val destination = LatLng(homeLocation.latitude, homeLocation.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(destination, 15f))
                    currentLocation?.let { origin ->
                        lifecycleScope.launch {
                            getRoute(LatLng(origin.latitude, origin.longitude), destination)
                        }
                    }
                    Toast.makeText(this, "🏠 مسیر به خانه", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ مکان خانه ذخیره نشده", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "💬 من دستیار مسیریاب هستم. می‌تونید بگید: پمپ بنزین، رستوران، مسیر خانه، ذخیره مکان", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && 
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
}
