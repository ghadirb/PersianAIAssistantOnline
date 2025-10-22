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
    
    private val cachedRoutes = mutableListOf<NeshanDirectionAPI.RouteInfo>()
    
    private fun showRoutes(lat: Double, lng: Double) {
        val currentLoc = activity.currentLocation
        if (currentLoc == null) {
            Toast.makeText(activity, "⚠️ مکان فعلی شما در دسترس نیست", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(activity, "🔄 محاسبه و کشیدن مسیرها...", Toast.LENGTH_LONG).show()
        
        activity.lifecycleScope.launch {
            try {
                val routes = directionAPI.getDirection(
                    currentLoc.latitude, currentLoc.longitude, lat, lng
                )
                
                activity.runOnUiThread {
                    if (routes.isEmpty()) {
                        Toast.makeText(activity, "❌ مسیری یافت نشد", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    
                    cachedRoutes.clear()
                    cachedRoutes.addAll(routes)
                    
                    // کشیدن همه مسیرها روی نقشه
                    val colors = listOf("#4285F4", "#34A853", "#FBBC04")
                    routes.forEachIndexed { index, route ->
                        val color = colors.getOrNull(index) ?: "#999999"
                        val poly = route.polyline.replace("'", "\\'")
                        activity.webView.evaluateJavascript(
                            "drawClickableRoute($index, '$poly', '$color');",
                            null
                        )
                    }
                    
                    // نمایش پیام راهنما
                    android.util.Log.d("RouteSheet", "✅ ${routes.size} routes drawn on map")
                    Toast.makeText(activity, "🎯 روی خط مسیر دلخواه کلیک کنید\n🔵آبی 🟢سبز 🟠نارنجی", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "❌ خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    fun onRouteClicked(routeIndex: Int, lat: Double, lng: Double) {
        if (routeIndex < cachedRoutes.size) {
            selectedRoute = cachedRoutes[routeIndex]
            showNavigationChoice(lat, lng, cachedRoutes[routeIndex])
        }
    }
    
    private fun showNavigationChoice(lat: Double, lng: Double, route: NeshanDirectionAPI.RouteInfo) {
        Toast.makeText(activity, "✅ مسیر ${route.duration} دقیقه انتخاب شد", Toast.LENGTH_SHORT).show()
        
        val options = arrayOf(
            "🚗 بزن بریم",
            "🗺️ Google Maps"
        )
        
        MaterialAlertDialogBuilder(activity)
            .setTitle("مسافت: ${String.format("%.1f", route.distance)} کم، زمان: ${route.duration} دقیقه")
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
        android.util.Log.d("Navigation", "🚗 Starting real navigation to: $lat, $lng")
        
        // باز کردن Activity ناوبری واقعی
        val intent = android.content.Intent(activity, com.persianai.assistant.activities.RealNavigationActivity::class.java)
        intent.putExtra("DEST_LAT", lat)
        intent.putExtra("DEST_LNG", lng)
        intent.putExtra("DISTANCE", route.distance)
        intent.putExtra("DURATION", route.duration)
        intent.putExtra("POLYLINE", route.polyline)
        
        activity.startActivity(intent)
        Toast.makeText(activity, "🚗 ناوبری با هشدارهای فارسی", Toast.LENGTH_SHORT).show()
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
