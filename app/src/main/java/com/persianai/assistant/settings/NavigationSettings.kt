package com.persianai.assistant.settings

import android.content.Context

class NavigationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nav_prefs", Context.MODE_PRIVATE)
    
    var useGoogleMaps: Boolean
        get() = prefs.getBoolean("use_google", false)
        set(v) = prefs.edit().putBoolean("use_google", v).apply()
    
    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice", true)
        set(v) = prefs.edit().putBoolean("voice", v).apply()
}
