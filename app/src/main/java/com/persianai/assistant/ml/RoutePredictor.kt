package com.persianai.assistant.ml

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import java.util.*
import kotlin.math.*

class RoutePredictor(context: Context) {
    
    private val historyManager = LocationHistoryManager(context)
    
    data class PredictedDestination(
        val location: LatLng,
        val confidence: Float,
        val reason: String
    )
    
    fun predictNextDestination(currentLocation: Location): List<PredictedDestination> {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val records = historyManager.getAllRecords()
        val predictions = mutableListOf<PredictedDestination>()
        
        // الگوی زمانی مشابه
        val similarTimeRecords = records.filter { record ->
            record.dayOfWeek == currentDayOfWeek && 
            abs(record.hourOfDay - currentHour) <= 1
        }
        
        if (similarTimeRecords.isNotEmpty()) {
            val clusters = clusterLocations(similarTimeRecords)
            clusters.take(3).forEach { (location, count) ->
                val confidence = count.toFloat() / similarTimeRecords.size
                predictions.add(PredictedDestination(
                    location = location,
                    confidence = confidence,
                    reason = "معمولاً ${getDayName(currentDayOfWeek)} ساعت $currentHour به اینجا می‌روید"
                ))
            }
        }
        
        // مکان‌های پرتکرار
        val frequentLocations = historyManager.getFrequentLocations()
        frequentLocations.take(2).forEach { (lat, lng) ->
            if (predictions.none { distance(it.location, LatLng(lat, lng)) < 0.01 }) {
                predictions.add(PredictedDestination(
                    location = LatLng(lat, lng),
                    confidence = 0.5f,
                    reason = "یکی از مکان‌های پرتکرار شما"
                ))
            }
        }
        
        return predictions.sortedByDescending { it.confidence }
    }
    
    private fun clusterLocations(records: List<LocationHistoryManager.LocationRecord>): List<Pair<LatLng, Int>> {
        val clusters = mutableMapOf<Pair<Int, Int>, Int>()
        
        records.forEach { record ->
            val key = Pair((record.latitude * 100).toInt(), (record.longitude * 100).toInt())
            clusters[key] = (clusters[key] ?: 0) + 1
        }
        
        return clusters.entries
            .sortedByDescending { it.value }
            .map { Pair(LatLng(it.key.first / 100.0, it.key.second / 100.0), it.value) }
    }
    
    private fun distance(loc1: LatLng, loc2: LatLng): Double {
        val lat1 = loc1.latitude
        val lon1 = loc1.longitude
        val lat2 = loc2.latitude
        val lon2 = loc2.longitude
        
        return sqrt((lat1 - lat2).pow(2) + (lon1 - lon2).pow(2))
    }
    
    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنج‌شنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> "امروز"
        }
    }
}
