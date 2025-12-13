package com.persianai.assistant.utils

import android.net.Uri

/**
 * پارس لینک‌های اشتراک‌گذاری‌شده از نقشه (Google Maps، نِشان، geo:)
 */
object LocationShareParser {

    data class ParsedLocation(
        val latitude: Double,
        val longitude: Double,
        val label: String?
    )

    fun parseFromIntentText(text: String?): ParsedLocation? {
        if (text.isNullOrBlank()) return null
        return parseFromString(text)
    }

    fun parseFromUri(uri: Uri?): ParsedLocation? {
        if (uri == null) return null
        return parseFromString(uri.toString())
    }

    /**
        پشتیبانی از:
        - geo:35.7,51.4?q=label
        - https://maps.google.com/...@35.7,51.4,...
        - https://maps.app.goo.gl/... (lat,lng inside params)
        - https://neshan.org/maps/@35.7,51.4,...
        - متون ساده "35.7, 51.4"
     */
    fun parseFromString(input: String): ParsedLocation? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        // geo: scheme
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        if (uri != null && uri.scheme == "geo") {
            val latLng = uri.schemeSpecificPart.substringBefore('?')
            val parts = latLng.split(',')
            if (parts.size >= 2) {
                val lat = parts[0].toDoubleOrNull()
                val lng = parts[1].toDoubleOrNull()
                if (lat != null && lng != null && isValidLatLng(lat, lng)) {
                    val label = uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }
                    return ParsedLocation(lat, lng, label)
                }
            }
        }

        // Try to extract two doubles (lat,lng) from the text / URL
        val regex = Regex("(-?\\d+(?:\\.\\d+)?)[,\\s]+(-?\\d+(?:\\.\\d+)?)")
        val match = regex.find(trimmed)
        if (match != null) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && isValidLatLng(lat, lng)) {
                val label = extractLabel(uri, trimmed)
                return ParsedLocation(lat, lng, label)
            }
        }

        return null
    }

    private fun extractLabel(uri: Uri?, raw: String): String? {
        // Prefer query param q (common in Google Maps shared links)
        val qParam = uri?.getQueryParameter("q")?.takeIf { it.isNotBlank() }
        if (!qParam.isNullOrBlank()) return qParam

        // If has place_id name or textual prefix, keep first line as label
        val firstLine = raw.lineSequence().firstOrNull()?.takeIf { it.length in 3..80 }
        return firstLine
    }

    private fun isValidLatLng(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }
}
