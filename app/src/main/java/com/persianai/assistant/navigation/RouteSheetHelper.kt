package com.persianai.assistant.navigation

import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.activities.NavigationActivity
import com.persianai.assistant.utils.NeshanDirectionAPI
import kotlinx.coroutines.launch

class RouteSheetHelper(private val activity: NavigationActivity) {
    
    private val directionAPI = NeshanDirectionAPI()
    private var selectedRoute: NeshanDirectionAPI.RouteInfo? = null
    private val settings = com.persianai.assistant.settings.NavigationSettings(activity)
    
    fun showLocationSheet(lat: Double, lng: Double) {
        android.util.Log.d("RouteSheetHelper", "🟢 Showing bottom sheet for: $lat, $lng")
        Toast.makeText(activity, "🗺️ نمایش گزینه‌ها...", Toast.LENGTH_SHORT).show()
        
        val sheet = BottomSheetDialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_location_options, null)
        
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveLocationButton)?.setOnClickListener {
            activity.showSaveLocationDialog(com.google.android.gms.maps.model.LatLng(lat, lng))
            sheet.dismiss()
        }
        
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.showRoutesButton)?.setOnClickListener {
            sheet.dismiss()
            showRoutes(lat, lng)
        }
        
        sheet.setContentView(view)
        sheet.show()
    }
    
    private fun showRoutes(lat: Double, lng: Double) {
        val currentLoc = activity.currentLocation
        if (currentLoc == null) {
            Toast.makeText(activity, "⚠️ مکان فعلی شما در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(activity, "🔄 در حال محاسبه مسیر واقعی...", Toast.LENGTH_SHORT).show()
        
        activity.lifecycleScope.launch {
            try {
                // دریافت مسیرهای واقعی از Neshan
                val routes = directionAPI.getDirection(
                    currentLoc.latitude, currentLoc.longitude, lat, lng
                )
                
                activity.runOnUiThread {
                    if (routes.isEmpty()) {
                        Toast.makeText(activity, "❌ مسیری یافت نشد", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    
                    // تبدیل به آیتم‌های قابل نمایش
                    val routeItems = routes.mapIndexed { index, route ->
                        val icon = when(index) {
                            0 -> "🚗 سریع‌ترین"
                            1 -> "🛣️ کوتاه‌ترین"
                            else -> "🌳 آرام‌ترین"
                        }
                        "$icon: ${route.duration} دقیقه، ${String.format("%.1f", route.distance)} کم"
                    }.toTypedArray()
                    
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("🗺️ ${routes.size} مسیر واقعی")
                        .setItems(routeItems) { _, which ->
                            selectedRoute = routes[which]
                            showStartButton(lat, lng, routes[which])
                        }
                        .setNegativeButton("بستن", null)
                        .show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "❌ خطا در محاسبه مسیر", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showStartButton(lat: Double, lng: Double, route: NeshanDirectionAPI.RouteInfo) {
        // کشیدن مسیر واقعی روی نقشه با polyline
        val polyline = route.polyline.replace("'", "\\'")  // escape quotes
        activity.webView.evaluateJavascript("drawRealRoute('$polyline');", null)
        
        Toast.makeText(activity, "✅ مسیر ${route.duration} دقیقه‌ای انتخاب شد", Toast.LENGTH_SHORT).show()
        
        // نمایش دیالوگ بزن بریم
        val options = arrayOf(
            "🚗 نشان (هشدارهای صوتی فارسی)",
            "🗺️ Google Maps + هشدارهای فارسی"
        )
        
        MaterialAlertDialogBuilder(activity)
            .setTitle("انتخاب موتور مسیریابی")
            .setItems(options) { _, which ->
                if (which == 0) {
                    startNavigation(lat, lng, route)
                } else {
                    startGoogleMapsNavigation(lat, lng)
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }
    
    private fun startNavigation(lat: Double, lng: Double, route: NeshanDirectionAPI.RouteInfo) {
        Toast.makeText(activity, "🚗 ناوبری شروع شد! مسافت: ${String.format("%.1f", route.distance)} کم", Toast.LENGTH_LONG).show()
        
        // نمایش اطلاعات مسیر در لاگ برای debug
        android.util.Log.d("Navigation", "Route: ${route.summary}")
        android.util.Log.d("Navigation", "Distance: ${route.distance} km")
        android.util.Log.d("Navigation", "Duration: ${route.duration} min")
        
        activity.startNavigationTo(lat, lng)
    }
    
    private fun startGoogleMapsNavigation(lat: Double, lng: Double) {
        try {
            com.persianai.assistant.maps.GoogleMapsHelper.openGoogleMaps(activity, lat, lng)
            Toast.makeText(activity, "🗺️ باز شد در Google Maps با هشدارهای فارسی", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "❌ Google Maps نصب نیست", Toast.LENGTH_SHORT).show()
        }
    }
}
