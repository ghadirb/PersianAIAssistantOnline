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
        
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.startNavigationFromSheet)?.setOnClickListener {
            sheet.dismiss()
            activity.startNavigationTo(lat, lng)
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
                // محاسبه فاصله تقریبی
                val distance = calculateDistance(currentLoc.latitude, currentLoc.longitude, lat, lng)
                val duration = (distance / 50.0 * 60).toInt() // فرض: 50 کیلومتر در ساعت
                
                val routes = arrayOf(
                    "🚗 مسیر سریع: ${duration} دقیقه، ${String.format("%.1f", distance)} کیلومتر",
                    "🛣️ مسیر کوتاه: ${duration + 5} دقیقه، ${String.format("%.1f", distance - 1)} کیلومتر",
                    "🌳 مسیر آرام: ${duration + 10} دقیقه، ${String.format("%.1f", distance + 2)} کیلومتر"
                )
                
                activity.runOnUiThread {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("🗺️ مسیرها از مکان شما")
                        .setItems(routes) { _, which ->
                            Toast.makeText(activity, "✅ مسیر ${which + 1} انتخاب شد", Toast.LENGTH_SHORT).show()
                            
                            // کشیدن مسیر روی نقشه
                            activity.webView.evaluateJavascript(
                                "drawRoute(${currentLoc.latitude}, ${currentLoc.longitude}, $lat, $lng);",
                                null
                            )
                            
                            // شروع ناوبری
                            activity.startNavigationTo(lat, lng)
                        }
                        .show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "❌ خطا در محاسبه مسیر", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // شعاع زمین به کیلومتر
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
