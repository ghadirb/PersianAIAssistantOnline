package com.persianai.assistant.navigation

import android.content.Context

/**
 * سیستم انتخاب نقشه
 * Google Maps / Neshan / OpenStreetMap
 */
enum class MapType {
    GOOGLE_MAP,
    NESHAN_MAP,
    OSM_MAP
}

object MapProvider {
    private const val PREFS_NAME = "map_settings"
    private const val KEY_MAP_TYPE = "selected_map_type"
    
    fun getSelectedMapType(context: Context): MapType {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_MAP_TYPE, MapType.NESHAN_MAP.name)
        return MapType.valueOf(type ?: MapType.NESHAN_MAP.name)
    }
    
    fun setMapType(context: Context, type: MapType) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP_TYPE, type.name)
            .apply()
    }
    
    fun getMapName(type: MapType): String = when(type) {
        MapType.GOOGLE_MAP -> "🌍 Google Maps"
        MapType.NESHAN_MAP -> "🗺️ نقشه نشان"
        MapType.OSM_MAP -> "🌐 OpenStreetMap"
    }
}
