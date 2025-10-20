# راهنمای پیاده‌سازی Navigation

## تغییرات لازم در NavigationActivity.kt:

### 1. تغییر Layout
```kotlin
// خط 44
binding = ActivityNavigationNewBinding.inflate(layoutInflater)
```

### 2. اضافه کردن Long Press
```kotlin
@JavascriptInterface
fun onMapLongPress(lat: Double, lng: Double) {
    runOnUiThread {
        selectedLocation = LatLng(lat, lng)
        binding.selectedMarker.visibility = View.VISIBLE
        showBottomSheet(lat, lng)
    }
}
```

### 3. جستجوی AI
```kotlin
binding.searchInput.setOnEditorActionListener { v, _, actionId ->
    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        lifecycleScope.launch {
            val result = aiAssistant.processNavigationQuery(v.text.toString())
            if (result.destination != null) {
                navigateToDestination(result.destination)
            }
        }
        true
    } else false
}
```

### 4. دکمه مکان من
```kotlin
binding.myLocationBtn.setOnClickListener {
    currentLocation?.let {
        webView.evaluateJavascript(
            "map.setView([${it.latitude}, ${it.longitude}], 15);", 
            null
        )
    }
}
```

### 5. Toggle ترافیک
```kotlin
binding.trafficToggleBtn.setOnClickListener {
    isTrafficEnabled = !isTrafficEnabled
    webView.evaluateJavascript(
        "toggleTraffic($isTrafficEnabled);", 
        null
    )
}
```

## فایل‌های آماده:
✅ activity_navigation_new.xml
✅ bottom_sheet_route_options.xml
✅ navigation_bottom_menu.xml

## قابلیت‌های فعال:
✅ Neshan API
✅ Persian Voice (TTS)
✅ AI Road Limit
✅ Google Drive Sync
✅ Route Learning
