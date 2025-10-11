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
import kotlinx.coroutines.launch
import java.util.*

/**
 * مسیریابی فارسی با نقشه و هشدارهای صوتی
 * استفاده از Google Maps + Nessan Maps API + Persian TTS
 */
class NavigationActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var binding: ActivityNavigationBinding
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var aiPoweredTTS: AIPoweredTTS
    private lateinit var speedCameraManager: SpeedCameraManager
    private lateinit var nessanMapsAPI: NessanMapsAPI
    
    private var currentLocation: Location? = null
    private var currentRoute: List<LatLng>? = null
    private var currentSpeed: Float = 0f // km/h
    private var speedLimit: Int = 0
    
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
        
        // نمایش وضعیت TTS
        android.util.Log.d("Navigation", "TTS Status: ${aiPoweredTTS.getStatus()}")
        
        // Setup map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
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
                    
                    // دریافت مسیر
                    currentLocation?.let { location ->
                        getRoute(
                            LatLng(location.latitude, location.longitude),
                            destination
                        )
                    }
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
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
