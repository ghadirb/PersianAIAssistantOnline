# تغییرات لازم در NavigationActivity.kt

## 1. اضافه کردن به setupButtons():

```kotlin
private fun setupButtons() {
    // جستجوی AI
    binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            val query = v.text.toString()
            lifecycleScope.launch {
                try {
                    val result = aiAssistant.processNavigationQuery(query)
                    if (result.destination != null) {
                        selectedDestination = result.destination
                        webView.evaluateJavascript(
                            "addMarker(${result.destination.latitude}, ${result.destination.longitude});",
                            null
                        )
                        Toast.makeText(
                            this@NavigationActivity,
                            "مقصد پیدا شد: ${result.locationName}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@NavigationActivity,
                        "خطا در جستجو: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            true
        } else false
    }

    // دکمه مکان من (FAB)
    binding.myLocationFab.setOnClickListener {
        currentLocation?.let { loc ->
            webView.evaluateJavascript(
                "map.setView([${loc.latitude}, ${loc.longitude}], 15);",
                null
            )
        }
    }

    // Toggle ترافیک
    binding.trafficToggleFab.setOnClickListener {
        isTrafficEnabled = !isTrafficEnabled
        webView.evaluateJavascript(
            "toggleTraffic($isTrafficEnabled);",
            null
        )
        val color = if (isTrafficEnabled) "#4CAF50" else "#9E9E9E"
        binding.trafficToggleFab.backgroundTintList = 
            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
        Toast.makeText(
            this,
            if (isTrafficEnabled) "ترافیک فعال شد" else "ترافیک غیرفعال شد",
            Toast.LENGTH_SHORT
        ).show()
    }

    // مکان‌های ذخیره شده
    binding.savedLocationsFab.setOnClickListener {
        showSavedLocations()
    }

    // تب‌های پایین
    binding.bottomNavigation.setOnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_map -> {
                // نقشه (پیش‌فرض)
                true
            }
            R.id.nav_chat -> {
                showAIChat()
                true
            }
            R.id.nav_accounting -> {
                // TODO: باز کردن صفحه حسابداری
                Toast.makeText(this, "حسابداری", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.nav_reminders -> {
                // TODO: باز کردن صفحه یادآورها
                Toast.makeText(this, "یادآورها", Toast.LENGTH_SHORT).show()
                true
            }
            else -> false
        }
    }

    // دکمه‌های قدیمی (اگر هنوز موجودند)
    binding.myLocationButton?.setOnClickListener {
        currentLocation?.let { loc ->
            webView.evaluateJavascript("setUserLocation(${loc.latitude}, ${loc.longitude});", null)
        }
    }
    
    binding.searchDestinationButton?.setOnClickListener {
        val intent = Intent(this, SearchDestinationActivity::class.java)
        startActivityForResult(intent, 1001)
    }
    
    binding.savedLocationsButton?.setOnClickListener {
        showSavedLocations()
    }
    
    binding.saveCurrentLocationButton?.setOnClickListener {
        currentLocation?.let { loc ->
            showSaveLocationDialog(LatLng(loc.latitude, loc.longitude))
        }
    }
    
    binding.startNavigationButton?.setOnClickListener {
        if (selectedDestination != null && currentLocation != null) {
            startNavigation()
        } else {
            Toast.makeText(this, "لطفاً مقصد را انتخاب کنید", Toast.LENGTH_SHORT).show()
        }
    }
    
    binding.stopNavigationButton?.setOnClickListener {
        stopNavigation()
    }
}
```

## 2. اضافه کردن MapInterface برای Long Press:

```kotlin
inner class MapInterface {
    @JavascriptInterface
    fun onMapLongPress(lat: Double, lng: Double) {
        runOnUiThread {
            selectedDestination = LatLng(lat, lng)
            binding.selectedMarker.visibility = View.VISIBLE
            
            // نمایش آدرس
            lifecycleScope.launch {
                try {
                    val geocoder = Geocoder(this@NavigationActivity)
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "مکان انتخاب شده"
                    
                    runOnUiThread {
                        showLocationOptionsDialog(LatLng(lat, lng), address)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showLocationOptionsDialog(LatLng(lat, lng), "مکان انتخاب شده")
                    }
                }
            }
        }
    }
}
```

## 3. اضافه کردن Dialog برای انتخاب مکان:

```kotlin
private fun showLocationOptionsDialog(location: LatLng, address: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle("مکان انتخاب شده")
        .setMessage(address)
        .setPositiveButton("مسیریابی") { _, _ ->
            selectedDestination = location
            startNavigation()
        }
        .setNeutralButton("ذخیره") { _, _ ->
            showSaveLocationDialog(location)
        }
        .setNegativeButton("لغو", null)
        .show()
}
```

## 4. به‌روزرسانی neshan_map.html:

```javascript
// اضافه کردن Long Press
map.on('contextmenu', function(e) {
    const latlng = e.latlng;
    Android.onMapLongPress(latlng.lat, latlng.lng);
});

// اضافه کردن Toggle Traffic
function toggleTraffic(enabled) {
    if (enabled) {
        // نمایش لایه ترافیک
        if (trafficLayer) {
            map.addLayer(trafficLayer);
        }
    } else {
        // حذف لایه ترافیک
        if (trafficLayer) {
            map.removeLayer(trafficLayer);
        }
    }
}
```

## 5. Import های لازم:

```kotlin
import android.view.inputmethod.EditorInfo
import com.google.android.material.bottomnavigation.BottomNavigationView
```

## خلاصه تغییرات:

✅ جستجوی AI با searchInput
✅ دکمه مکان من (myLocationFab)
✅ Toggle ترافیک (trafficToggleFab)
✅ مکان‌های ذخیره (savedLocationsFab)
✅ تب‌های پایین (bottomNavigation)
✅ Long press روی نقشه
✅ Dialog انتخاب مکان

## تست:

1. Build بگیر
2. جستجو کن: "مسیریابی تا میدان آزادی"
3. روی نقشه نگه دار
4. دکمه ترافیک رو بزن
5. تب‌ها رو امتحان کن
