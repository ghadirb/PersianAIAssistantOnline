package com.persianai.assistant.navigation

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

/**
 * پارسر لینک‌های اشتراکی نشان / گوگل‌مپ / لینک‌های دارای مختصات
 */
object LocationShareParser {

    data class ParsedLocation(
        val latLng: LatLng,
        val nameHint: String? = null,
        val raw: String
    )

    /**
     * دریافت مختصات از متن اشتراک‌گذاری شده
     */
    fun parse(text: String?): ParsedLocation? {
        if (text.isNullOrBlank()) return null

        // 1) تلاش برای پیدا کردن اولین زوج lat,lng در متن
        val coordRegex = Regex("([-+]?\\d{1,3}\\.\\d+)[ ,]+([-+]?\\d{1,3}\\.\\d+)")
        coordRegex.find(text)?.let { match ->
            val lat = match.groupValues.getOrNull(1)?.toDoubleOrNull()
            val lng = match.groupValues.getOrNull(2)?.toDoubleOrNull()
            if (lat != null && lng != null) {
                return ParsedLocation(LatLng(lat, lng), null, text)
            }
        }

        // 2) تلاش برای پارس پارامترهای URL
        runCatching {
            val uri = Uri.parse(text)
            // Google Maps: ?q=lat,lng یا ?ll=lat,lng
            uri.getQueryParameter("q")?.let { q ->
                coordRegex.find(q)?.let { m ->
                    val lat = m.groupValues[1].toDoubleOrNull()
                    val lng = m.groupValues[2].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return ParsedLocation(LatLng(lat, lng), null, text)
                    }
                }
            }
            uri.getQueryParameter("ll")?.let { ll ->
                coordRegex.find(ll)?.let { m ->
                    val lat = m.groupValues[1].toDoubleOrNull()
                    val lng = m.groupValues[2].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return ParsedLocation(LatLng(lat, lng), null, text)
                    }
                }
            }
            // نشن: ?lat=&lng= یا @lat,lng
            val latParam = uri.getQueryParameter("lat")?.toDoubleOrNull()
            val lngParam = uri.getQueryParameter("lng")?.toDoubleOrNull()
            if (latParam != null && lngParam != null) {
                return ParsedLocation(LatLng(latParam, lngParam), null, text)
            }
            if (uri.path?.contains("@") == true) {
                val atPart = uri.path?.substringAfter("@")
                coordRegex.find(atPart ?: "")?.let { m ->
                    val lat = m.groupValues[1].toDoubleOrNull()
                    val lng = m.groupValues[2].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        return ParsedLocation(LatLng(lat, lng), null, text)
                    }
                }
            }
        }

        return null
    }
}
