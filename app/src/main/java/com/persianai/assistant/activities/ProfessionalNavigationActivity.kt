package com.persianai.assistant.activities

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.tabs.TabLayout
import com.persianai.assistant.R
import com.persianai.assistant.databinding.ActivityProfessionalNavigationBinding
import com.persianai.assistant.models.NavigationRoute
import com.persianai.assistant.models.PlaceSuggestion
import com.persianai.assistant.utils.NavigationManager
import com.persianai.assistant.utils.VoiceNavigationHelper
import kotlinx.coroutines.launch
import java.util.*

/**
 * اکتیویتی مسیریاب حرفه‌ای با قابلیت‌های کامل مانند نشان
 */
class ProfessionalNavigationActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var binding: ActivityProfessionalNavigationBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var placesClient: PlacesClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var navigationManager: NavigationManager
    private lateinit var voiceNavigation: VoiceNavigationHelper
    
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var destinationLocation: LatLng? = null
    private var currentRoute: NavigationRoute? = null
    
    private val locationPermissionRequestCode = 1001
    private val sessionToken = AutocompleteSessionToken.newInstance()
    
    // آداپتور پیشنهاد مکان‌ها
    private lateinit var suggestionsAdapter: PlacesAutoCompleteAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityProfessionalNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize Places API
        Places.initialize(applicationContext, "YOUR_API_KEY")
        placesClient = Places.createClient(this)
        
        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Initialize navigation components
        navigationManager = NavigationManager(this)
        voiceNavigation = VoiceNavigationHelper(this)
        
        setupMap()
        setupUI()
        setupLocationUpdates()
        checkLocationPermission()
    }
    
    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    
    private fun setupUI() {
        setupToolbar()
        setupTabs()
        setupSearch()
        setupBottomSheet()
        setupClickListeners()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "مسیریاب حرفه‌ای"
        }
    }
    
    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showMapMode()
                    1 -> showListMode()
                    2 -> showSatelliteMode()
                    3 -> showTerrainMode()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun setupSearch() {
        suggestionsAdapter = PlacesAutoCompleteAdapter { place ->
            searchDestination(place.description)
        }
        
        binding.searchRecyclerView.adapter = suggestionsAdapter
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(this)
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    searchPlaces(s.toString())
                } else {
                    suggestionsAdapter.submitList(emptyList())
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        binding.currentLocationButton.setOnClickListener {
            getCurrentLocationAndZoom()
        }
    }
    
    private fun setupBottomSheet() {
        binding.routeInfoButton.setOnClickListener {
            toggleRouteInfo()
        }
        
        binding.startNavigationButton.setOnClickListener {
            startNavigation()
        }
        
        binding.stopNavigationButton.setOnClickListener {
            stopNavigation()
        }
        
        binding.voiceNavigationButton.setOnClickListener {
            toggleVoiceNavigation()
        }
    }
    
    private fun setupClickListeners() {
        binding.favoriteButton.setOnClickListener {
            addToFavorites()
        }
        
        binding.shareButton.setOnClickListener {
            shareLocation()
        }
        
        binding.reportButton.setOnClickListener {
            reportIssue()
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // تنظیمات نقشه
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
        }
        
        // نوع نقشه پیش‌فرض
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        
        // فعال کردن موقعیت مکانی
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            getCurrentLocationAndZoom()
        }
        
        // listener برای کلیک روی نقشه
        googleMap.setOnMapClickListener { latLng ->
            showLocationOptions(latLng)
        }
        
        // listener برای کلیک روی مارکر
        googleMap.setOnMarkerClickListener { marker ->
            showMarkerInfo(marker)
            true
        }
    }
    
    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateCurrentLocationMarker(location)
                    
                    // به‌روزرسانی مسیریابی اگر در حال ناوبری هستیم
                    if (currentRoute != null && isNavigating) {
                        updateNavigation(location)
                    }
                }
            }
        }
    }
    
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            startLocationUpdates()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
                if (::googleMap.isInitialized) {
                    googleMap.isMyLocationEnabled = true
                    getCurrentLocationAndZoom()
                }
            } else {
                Toast.makeText(this, "دسترسی به موقعیت مکانی لازم است", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 10000 // 10 ثانیه
                fastestInterval = 5000 // 5 ثانیه
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }
    
    private fun getCurrentLocationAndZoom() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    val latLng = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    updateCurrentLocationMarker(it)
                }
            }
        }
    }
    
    private fun searchPlaces(query: String) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setTypeFilter(TypeFilter.ADDRESS)
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()
        
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val suggestions = response.map { prediction ->
                    PlaceSuggestion(
                        placeId = prediction.placeId,
                        description = prediction.getFullText(null).toString(),
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString()
                    )
                }
                suggestionsAdapter.submitList(suggestions)
            }
            .addOnFailureListener { exception ->
                // Handle error
            }
    }
    
    private fun searchDestination(placeDescription: String) {
        // جستجوی مکان مقصد و نمایش مسیر
        lifecycleScope.launch {
            try {
                val destination = navigationManager.searchPlace(placeDescription)
                destination?.let {
                    destinationLocation = LatLng(it.latitude, it.longitude)
                    showRouteToDestination(it)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ProfessionalNavigationActivity, "خطا در جستجوی مکان", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showRouteToDestination(destination: com.persianai.assistant.models.Place) {
        currentLocation?.let { current ->
            lifecycleScope.launch {
                try {
                    val route = navigationManager.calculateRoute(
                        current.latitude,
                        current.longitude,
                        destination.latitude,
                        destination.longitude
                    )
                    
                    currentRoute = route
                    displayRoute(route)
                    showRouteInfo(route)
                    
                } catch (e: Exception) {
                    Toast.makeText(this@ProfessionalNavigationActivity, "خطا در محاسبه مسیر", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun displayRoute(route: NavigationRoute) {
        // پاک کردن مسیرهای قبلی
        googleMap.clear()
        
        // نمایش مسیر
        val polylineOptions = PolylineOptions()
            .addAll(route.points.map { LatLng(it.latitude, it.longitude) })
            .color(ContextCompat.getColor(this, R.color.primary_blue))
            .width(12f)
            .geodesic(true)
        
        googleMap.addPolyline(polylineOptions)
        
        // افزودن مارکر مقصد
        destinationLocation?.let { dest ->
            val markerOptions = MarkerOptions()
                .position(dest)
                .title("مقصد")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            googleMap.addMarker(markerOptions)
        }
        
        // حرکت دوربین برای نمایش کل مسیر
        val boundsBuilder = LatLngBounds.builder()
        route.points.forEach { point ->
            boundsBuilder.include(LatLng(point.latitude, point.longitude))
        }
        val bounds = boundsBuilder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }
    
    private fun showRouteInfo(route: NavigationRoute) {
        binding.routeInfoLayout.visibility = View.VISIBLE
        
        binding.distanceText.text = "${String.format("%.1f", route.distance / 1000)} کیلومتر"
        binding.durationText.text = "${route.duration / 60} دقیقه"
        binding.trafficText.text = getTrafficStatus(route.trafficLevel)
        
        binding.startNavigationButton.visibility = View.VISIBLE
        binding.stopNavigationButton.visibility = View.GONE
    }
    
    private fun getTrafficStatus(trafficLevel: Int): String {
        return when (trafficLevel) {
            0 -> "ترافیک روان"
            1 -> "ترافیک متوسط"
            2 -> "ترافیک سنگین"
            else -> "اطلاعاتی موجود نیست"
        }
    }
    
    private fun startNavigation() {
        currentRoute?.let { route ->
            isNavigating = true
            binding.startNavigationButton.visibility = View.GONE
            binding.stopNavigationButton.visibility = View.VISIBLE
            
            // شروع ناوبری صوتی
            voiceNavigation.startNavigation(route)
            
            // نمایش دستورالعمل اول
            if (route.steps.isNotEmpty()) {
                showNextInstruction(route.steps[0])
            }
        }
    }
    
    private fun stopNavigation() {
        isNavigating = false
        currentRoute = null
        
        binding.startNavigationButton.visibility = View.VISIBLE
        binding.stopNavigationButton.visibility = View.GONE
        binding.routeInfoLayout.visibility = View.GONE
        
        // توقف ناوبری صوتی
        voiceNavigation.stopNavigation()
        
        // پاک کردن مسیر
        googleMap.clear()
    }
    
    private fun toggleVoiceNavigation() {
        voiceNavigation.toggleMute()
        val icon = if (voiceNavigation.isMuted()) R.drawable.ic_volume_off else R.drawable.ic_volume_on
        binding.voiceNavigationButton.setImageResource(icon)
    }
    
    private fun updateNavigation(location: Location) {
        currentRoute?.let { route ->
            // به‌روزرسانی موقعیت روی مسیر
            val currentStep = navigationManager.getCurrentStep(route, location)
            currentStep?.let { step ->
                showNextInstruction(step)
            }
            
            // بررسی رسیدن به مقصد
            if (navigationManager.hasReachedDestination(route, location)) {
                Toast.makeText(this, "به مقصد رسیدید!", Toast.LENGTH_LONG).show()
                stopNavigation()
            }
        }
    }
    
    private fun showNextInstruction(step: NavigationStep) {
        binding.instructionText.text = step.instruction
        binding.instructionDistance.text = "${String.format("%.0f", step.distance)} متر"
        
        // به‌روزرسانی مارکر موقعیت فعلی
        updateCurrentLocationMarker(location)
    }
    
    private fun updateCurrentLocationMarker(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        // TODO: افزودن مارکر سفارشی برای موقعیت فعلی
    }
    
    private fun showLocationOptions(latLng: LatLng) {
        // نمایش گزینه‌ها برای مکان انتخاب شده
        val options = arrayOf("مسیریابی به این مکان", "افزودن به موارد دلخواه", "اشتراک‌گذاری مکان")
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("گزینه‌های مکان")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // مسیریابی به مکان انتخاب شده
                        destinationLocation = latLng
                        // TODO: محاسبه مسیر
                    }
                    1 -> {
                        // افزودن به موارد دلخواه
                        addToFavorites(latLng)
                    }
                    2 -> {
                        // اشتراک‌گذاری مکان
                        shareLocation(latLng)
                    }
                }
            }
            .show()
    }
    
    private fun showMarkerInfo(marker: Marker) {
        // نمایش اطلاعات مارکر
    }
    
    private fun showMapMode() {
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
    }
    
    private fun showListMode() {
        // نمایش مسیر به صورت لیست
    }
    
    private fun showSatelliteMode() {
        googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
    }
    
    private fun showTerrainMode() {
        googleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
    }
    
    private fun toggleRouteInfo() {
        if (binding.routeDetailsLayout.visibility == View.VISIBLE) {
            binding.routeDetailsLayout.visibility = View.GONE
        } else {
            binding.routeDetailsLayout.visibility = View.VISIBLE
        }
    }
    
    private fun addToFavorites() {
        destinationLocation?.let { latLng ->
            addToFavorites(latLng)
        }
    }
    
    private fun addToFavorites(latLng: LatLng) {
        // TODO: پیاده‌سازی افزودن به موارد دلخواه
        Toast.makeText(this, "به موارد دلخواه اضافه شد", Toast.LENGTH_SHORT).show()
    }
    
    private fun shareLocation() {
        currentLocation?.let { location ->
            shareLocation(LatLng(location.latitude, location.longitude))
        }
    }
    
    private fun shareLocation(latLng: LatLng) {
        val shareText = "موقعیت من: ${latLng.latitude}, ${latLng.longitude}"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری موقعیت"))
    }
    
    private fun reportIssue() {
        // TODO: پیاده‌سازی گزارش مشکل
        Toast.makeText(this, "گزارش مشکل", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        voiceNavigation.cleanup()
    }
    
    companion object {
        private var isNavigating = false
    }
}

/**
 * آداپتور برای پیشنهاد مکان‌ها
 */
class PlacesAutoCompleteAdapter(
    private val onPlaceClick: (PlaceSuggestion) -> Unit
) : RecyclerView.Adapter<PlacesAutoCompleteAdapter.ViewHolder>() {
    
    private var suggestions = listOf<PlaceSuggestion>()
    
    fun submitList(newSuggestions: List<PlaceSuggestion>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }
    
    override fun getItemCount(): Int = suggestions.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)
        
        fun bind(suggestion: PlaceSuggestion) {
            text1.text = suggestion.primaryText
            text2.text = suggestion.secondaryText
            
            itemView.setOnClickListener {
                onPlaceClick(suggestion)
            }
        }
    }
}
