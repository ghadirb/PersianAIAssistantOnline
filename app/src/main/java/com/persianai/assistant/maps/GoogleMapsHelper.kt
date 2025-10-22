package com.persianai.assistant.maps

import android.content.Context
import android.content.Intent
import android.net.Uri

class GoogleMapsHelper {
    
    companion object {
        fun openGoogleMaps(context: Context, lat: Double, lng: Double, label: String = "مقصد") {
            val uri = "google.navigation:q=$lat,$lng&mode=d"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.setPackage("com.google.android.apps.maps")
            context.startActivity(intent)
        }
    }
}
