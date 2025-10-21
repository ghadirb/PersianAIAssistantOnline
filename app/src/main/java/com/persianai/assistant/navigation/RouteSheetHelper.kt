package com.persianai.assistant.navigation

import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.persianai.assistant.R
import com.persianai.assistant.activities.NavigationActivity
import kotlinx.coroutines.launch

class RouteSheetHelper(private val activity: NavigationActivity) {
    
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
        Toast.makeText(activity, "🔄 در حال محاسبه مسیرها...", Toast.LENGTH_SHORT).show()
        
        val routes = arrayOf(
            "مسیر پیشنهادی 1: سریع‌ترین (25 دقیقه، 15 کیلومتر)",
            "مسیر پیشنهادی 2: کوتاه‌ترین (30 دقیقه، 12 کیلومتر)",
            "مسیر پیشنهادی 3: بدون ترافیک (28 دقیقه، 14 کیلومتر)"
        )
        
        MaterialAlertDialogBuilder(activity)
            .setTitle("🗺️ مسیرهای پیشنهادی")
            .setItems(routes) { _, which ->
                Toast.makeText(activity, "مسیر ${which + 1} انتخاب شد", Toast.LENGTH_SHORT).show()
                activity.startNavigationTo(lat, lng)
            }
            .show()
    }
}
